package com.teamz.lab.debugger.utils

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ExifInterface
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import java.io.ByteArrayInputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Camera + screen diagnostics.
 *
 * READS DECLARED CAPABILITIES AND OBSERVES LIVE BEHAVIOUR ONLY. Never scores, grades, or rates
 * a camera as "healthy" — a 2026-07-24 deep-research pass (13 confirmed / 12 refuted claims)
 * found that every pixel-based quality judgement (sharpness, colour accuracy, sensor health)
 * measures the vendor's ISP pipeline, not the sensor, and the app cannot even know which
 * physical sensor produced a given frame. That is the same "impossible functionality" shape
 * that got this app's per-app battery claim rejected under Play's Deceptive Behavior policy.
 * Hot/stuck-pixel detection was evaluated and dropped entirely for the same reason: the
 * platform hot-pixel map is a factory defect list (not a live scan) and DIY dark-frame analysis
 * is below the noise floor at phone pixel sizes.
 *
 * Every user-facing string here must read as "your device reports X", never "your camera is X".
 */
object CameraHealthUtils {
    private const val TAG = "CameraHealthUtils"
    private const val LIVENESS_TIMEOUT_MS = 4_000L

    // AE (auto-exposure) warmup window before the still capture fires — see openCaptureClose.
    // MIN: never fire on the very first preview frame, even if it happens to already report
    // converged. MAX: hard ceiling for hardware that never reports CONTROL_AE_STATE at all, so
    // one slow/silent device can't eat the whole LIVENESS_TIMEOUT_MS budget by itself.
    private const val AE_WARMUP_MIN_MS = 250L
    private const val AE_WARMUP_MAX_MS = 1_200L

    // ── Data classes ────────────────────────────────────────────────────────

    /** One declared physical sub-lens behind a logical multi-camera ID. Nothing here is measured. */
    data class PhysicalLensSpec(
        val physicalId: String,
        val focalLengthsMm: List<Float>,
        val aperturesF: List<Float>,
    )

    /** One physical or logical camera's declared capabilities. Nothing here is measured. */
    data class LensReport(
        val cameraId: String,
        val facing: String,                    // "Back" / "Front" / "External" / "Unknown"
        val hardwareLevel: String,              // LEGACY/LIMITED/FULL/LEVEL_3/EXTERNAL — 5 values, never 4
        val focalLengthsMm: List<Float>,
        val maxDigitalZoom: Float?,
        val hasOpticalStabilization: Boolean,
        val hasFlash: Boolean,
        val supportsAutofocus: Boolean,
        val rawAvailableToThisApp: Boolean,     // RAW is optional even at FULL — never inferred from level
        val physicalLensCount: Int,             // 1 if this camera exposes no physical sub-lenses
        val aperturesF: List<Float> = emptyList(),
        val sensorSizeMm: String? = null,       // e.g. "6.4 x 4.8" — physical sensor size, not resolution
        val isoRange: String? = null,           // e.g. "50 - 3200" — declared sensitivity range
        val hasVideoStabilization: Boolean = false,
        // Populated only when physicalLensCount > 1 AND each sub-lens's characteristics are
        // readable (many OEMs block this even when the ID list itself is non-empty).
        val physicalLenses: List<PhysicalLensSpec> = emptyList(),
        val exposureTimeRangeSec: String? = null,  // e.g. "1/8000s - 30s" — declared shutter range
        val aeModes: List<String> = emptyList(),   // declared auto-exposure modes, e.g. "ON_AUTO_FLASH"
        val jpegResolutions: List<String> = emptyList(), // e.g. "4000x3000", widest first
        // Video path was never checked before 2026-07-24 — only photo capture was. Read from the
        // same StreamConfigurationMap already fetched for jpegResolutions, just a different output
        // class (MediaRecorder instead of ImageFormat.JPEG). Declared capability, not measured.
        val videoRecordingSupported: Boolean = false,
        val maxVideoResolution: String? = null,
    )

    /** Declared-capability snapshot across every camera ID the OS exposes to this app. */
    data class CameraFactSheet(
        val lenses: List<LensReport>,
        val scannedAtMs: Long = System.currentTimeMillis(),
    ) {
        val cameraCount: Int get() = lenses.size
    }

    /** One camera's liveness result. The USER, not the app, judges photo quality. */
    data class CameraLivenessResult(
        val cameraId: String,
        val facing: String,
        val opened: Boolean,
        val frameReceived: Boolean,
        val autofocusConverged: Boolean?,       // null = device has no AF to test
        val openToFrameMs: Long?,
        val errorReason: String?,               // null on success; never shown as a "verdict"
        // A genuine read, not an inference: which physical sub-lens actually produced this
        // frame, per CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID (API 29+, requires
        // the device to both declare LOGICAL_MULTI_CAMERA and expose the result key — most
        // Samsung/Xiaomi/Motorola/OnePlus devices report null here, which must render as
        // "not reported by this device", never as "this phone has one lens".
        val activePhysicalCameraId: String? = null,
    )

    data class CameraHealthResult(
        val factSheet: CameraFactSheet,
        val liveness: List<CameraLivenessResult>,
        val timestamp: Long = System.currentTimeMillis(),
    ) {
        /** True only when every lens opened and delivered a frame. Binary, provable, never a score. */
        val allLensesResponded: Boolean
            get() = liveness.isNotEmpty() && liveness.all { it.opened && it.frameReceived }
    }

    /**
     * "My photos look black and white" check. Two parts, both honest about their limits:
     * a read of the two AOSP-standard settings that cause this (never an inference — either the
     * OS reports the flag or it doesn't), and a coarse colour-saturation read of one captured
     * frame (a real pixel measurement, but reported as "your last photo showed almost no colour",
     * never as a "camera health" verdict). OEM-specific mechanisms (Samsung Bedtime Mode, some
     * Xiaomi colour toggles) are NOT exposed through any public Android API — this check cannot
     * see those, and the UI must say so rather than imply full coverage.
     */
    data class ColorCastCheckResult(
        val capturedLooksMonochrome: Boolean?,  // null = no frame was available to analyze
        val averageSaturation: Float?,          // 0f..1f, raw signal — never shown as a percentage/score
        val grayscaleAccessibilityOn: Boolean,
        val batterySaverOn: Boolean,
        // The photo this check captures is the only frame this app ever keeps as bytes (see
        // runCameraLivenessCheck's onFrameCaptured doc) — reused here to read what the camera
        // actually used to take it, straight from the file the OS wrote, no extra capture needed.
        val exifSummary: ExifSummary? = null,
    )

    /**
     * What the camera actually used for one real shot — read straight from the JPEG's own EXIF
     * tags, not a declared capability. Every field is optional: not every OEM's camera HAL writes
     * every tag, and a missing tag must render as "not reported", never as "0" or "off".
     */
    data class ExifSummary(
        val isoSpeed: Int?,
        val exposureTimeSec: String?,   // e.g. "1/60s" — already formatted, matches formatExposureTimeNs
        val fNumber: Float?,
        val focalLengthMm: Float?,
        val flashFired: Boolean?,
    )

    /**
     * Storage/RAM/thermal reads for the "Report a Camera Problem" bundle. All three are public,
     * no-root APIs confirmed by the 2026-07-24 deep-research pass (StatFs, ActivityManager, and
     * PowerManager.getCurrentThermalStatus). [thermalSevere] gates the one corroborated causal
     * note this app is willing to state (severe heat can make a phone block camera access) — every
     * other plausible cause the research checked (low storage → launch failure, low RAM → crash)
     * was explicitly REFUTED under adversarial review and must NOT be asserted here.
     */
    data class CameraEnvironmentSignals(
        val storageInfo: String,
        val ramInfo: String,
        val lowMemory: Boolean,
        val thermalStatus: String,
        val thermalSevere: Boolean,
        // Free space being reported ≠ a photo can actually be saved (permission-scoped storage,
        // a full-but-not-empty volume, or a broken MediaStore write can all block the save even
        // when StatFs reports headroom). This is a real attempt, not a free-space inference.
        val canSaveNewPhotos: Boolean = true,
    )

    /** The phone cannot detect its own dead pixels — this is the user's own judgement, recorded. */
    data class ScreenPixelResult(
        val userReportedIssue: Boolean,
        val colorShownWhenReported: String?,    // e.g. "red" — null if no issue reported
        val timestamp: Long = System.currentTimeMillis(),
    )

    // ── Fact sheet (declared capabilities — cheap, no camera open) ─────────

    /**
     * Reads [CameraCharacteristics] for every camera ID the OS exposes to this app.
     * Never opens a camera. Safe to call without the CAMERA permission — Camera2 characteristics
     * are readable pre-permission (only opening a camera device requires it).
     */
    fun readCameraFactSheet(context: Context): CameraFactSheet {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return CameraFactSheet(emptyList())

        val lenses = mutableListOf<LensReport>()
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val chars = try {
                    cameraManager.getCameraCharacteristics(cameraId)
                } catch (e: Exception) {
                    handleError(e, context = "readCameraFactSheet:$cameraId")
                    continue
                }
                lenses.add(buildLensReport(cameraManager, cameraId, chars))
            }
        } catch (e: Exception) {
            handleError(e, context = "readCameraFactSheet")
        }
        return CameraFactSheet(lenses)
    }

    private fun buildLensReport(
        cameraManager: CameraManager,
        cameraId: String,
        chars: CameraCharacteristics,
    ): LensReport {
        val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

        // Five values, not four — EXTERNAL (USB/UVC) is real and silently mislabeled by apps
        // that hardcode LEGACY/LIMITED/FULL/LEVEL_3.
        val hardwareLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "Unknown"
        }

        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.toList().orEmpty()
        val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
        val stabilizationModes =
            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        val hasOis = stabilizationModes?.any {
            it != CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_OFF
        } ?: false
        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        val supportsAf = afModes?.any { it != CameraCharacteristics.CONTROL_AF_MODE_OFF } ?: false

        // RAW is OPTIONAL even at hardware level FULL — must be read directly, never inferred.
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val rawAvailable = capabilities?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
        ) ?: false

        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            ?.toList().orEmpty()

        val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let {
            "%.1f x %.1f".format(it.width, it.height)
        }

        val isoRangeChar = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val isoRange = isoRangeChar?.let { "${it.lower} - ${it.upper}" }

        val videoStabModes =
            chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
        val hasVideoStab = videoStabModes?.any {
            it != CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_OFF
        } ?: false

        val exposureRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val exposureTimeRangeSec = exposureRange?.let {
            "${formatExposureTimeNs(it.lower)} - ${formatExposureTimeNs(it.upper)}"
        }

        val aeModeInts = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
        val aeModes = aeModeInts?.map { aeModeName(it) }.orEmpty()

        val streamConfigMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegResolutions = streamConfigMap?.getOutputSizes(ImageFormat.JPEG)
            ?.sortedByDescending { it.width.toLong() * it.height }
            ?.map { "${it.width}x${it.height}" }
            .orEmpty()

        val videoSizes = try {
            streamConfigMap?.getOutputSizes(MediaRecorder::class.java)
        } catch (e: Exception) {
            null
        }
        val videoRecordingSupported = !videoSizes.isNullOrEmpty()
        val maxVideoResolution = videoSizes
            ?.maxByOrNull { it.width.toLong() * it.height }
            ?.let { "${it.width}x${it.height}" }

        // Per-physical-lens enumeration (API 28+). Empty on most non-Pixel devices in the
        // BD/India base (Samsung/Xiaomi/Motorola/OnePlus commonly hide sub-cameras) — that is
        // expected, not an error, and must never be read as "this phone has 1 lens".
        val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                chars.physicalCameraIds
            } catch (e: Exception) {
                emptySet()
            }
        } else {
            emptySet()
        }
        val physicalCount = physicalIds.size.coerceAtLeast(1)

        // Sub-lens specs — each physical ID's own characteristics, when the OEM allows reading
        // them. Failure per sub-lens is expected (not every OEM exposes this), never fatal.
        val physicalLenses = if (physicalIds.size > 1) {
            physicalIds.mapNotNull { physicalId ->
                try {
                    val physChars = cameraManager.getCameraCharacteristics(physicalId)
                    PhysicalLensSpec(
                        physicalId = physicalId,
                        focalLengthsMm = physChars
                            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                            ?.toList().orEmpty(),
                        aperturesF = physChars
                            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                            ?.toList().orEmpty(),
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            emptyList()
        }

        return LensReport(
            cameraId = cameraId,
            facing = facing,
            hardwareLevel = hardwareLevel,
            focalLengthsMm = focalLengths,
            maxDigitalZoom = maxZoom,
            hasOpticalStabilization = hasOis,
            hasFlash = hasFlash,
            supportsAutofocus = supportsAf,
            rawAvailableToThisApp = rawAvailable,
            physicalLensCount = physicalCount,
            aperturesF = apertures,
            sensorSizeMm = sensorSize,
            isoRange = isoRange,
            hasVideoStabilization = hasVideoStab,
            physicalLenses = physicalLenses,
            exposureTimeRangeSec = exposureTimeRangeSec,
            aeModes = aeModes,
            jpegResolutions = jpegResolutions,
            videoRecordingSupported = videoRecordingSupported,
            maxVideoResolution = maxVideoResolution,
        )
    }

    /** "1/8000s" for sub-second shutter times, "2.5s" for slow shutter — both declared, not measured. */
    private fun formatExposureTimeNs(ns: Long): String {
        val seconds = ns / 1_000_000_000.0
        return if (seconds < 1.0 && seconds > 0.0) {
            "1/${Math.round(1.0 / seconds)}s"
        } else {
            "%.1fs".format(seconds)
        }
    }

    private fun aeModeName(mode: Int): String = when (mode) {
        CameraCharacteristics.CONTROL_AE_MODE_OFF -> "OFF"
        CameraCharacteristics.CONTROL_AE_MODE_ON -> "ON"
        CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH -> "ON_AUTO_FLASH"
        CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "ON_ALWAYS_FLASH"
        CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "ON_AUTO_FLASH_REDEYE"
        else -> "MODE_$mode"
    }

    // ── Liveness check (opens each camera, confirms a frame arrives) ───────

    /**
     * Opens every camera ID in sequence, requests one preview frame, and — where the camera
     * supports autofocus — triggers AF and reads [CaptureResult.CONTROL_AF_STATE] convergence.
     * This proves the camera is genuinely dead (the #1 "my camera is broken" complaint) without
     * the app ever asserting an image-quality verdict.
     *
     * Runs sequentially, never opens two cameras at once — see the research note above the
     * multi-camera guarantee: simultaneous streaming beyond the framework minimum needs
     * per-device tuning this app cannot do. Sequential open/capture/close is not bound by that
     * limit and works uniformly.
     *
     * Never throws to the caller. A camera that fails to open or times out returns a
     * [CameraLivenessResult] with [CameraLivenessResult.opened] = false; the caller decides
     * copy, this function only reports what happened.
     */
    /**
     * @param onFrameCaptured Called with the raw JPEG bytes of the single frame this check
     * captures per camera, so the caller can show the user what their camera actually saw — the
     * previous version of this check proved a frame arrived but never displayed it, which is a
     * weaker trust signal than the plan originally called for ("show the frame... let the user
     * judge"). Not persisted anywhere: this is an ephemeral, current-session-only preview, kept
     * out of [CameraHealthResult] entirely so history/SharedPreferences never has to hold photo
     * bytes.
     */
    suspend fun runCameraLivenessCheck(
        context: Context,
        onFrameCaptured: ((cameraId: String, jpegBytes: ByteArray) -> Unit)? = null,
    ): List<CameraLivenessResult> =
        withContext(Dispatchers.IO) {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return@withContext emptyList()

            val results = mutableListOf<CameraLivenessResult>()
            for (cameraId in cameraManager.cameraIdList) {
                val chars = try {
                    cameraManager.getCameraCharacteristics(cameraId)
                } catch (e: Exception) {
                    handleError(e, context = "runCameraLivenessCheck:chars:$cameraId")
                    continue
                }
                results.add(checkOneCamera(cameraManager, cameraId, chars, onFrameCaptured))
            }
            results
        }

    private suspend fun checkOneCamera(
        cameraManager: CameraManager,
        cameraId: String,
        chars: CameraCharacteristics,
        onFrameCaptured: ((cameraId: String, jpegBytes: ByteArray) -> Unit)?,
    ): CameraLivenessResult {
        val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        val hasAf = afModes?.any { it != CameraCharacteristics.CONTROL_AF_MODE_OFF } ?: false

        // Gate the active-physical-lens read on the device actually declaring support — reading
        // the result key on a device that doesn't back it returns null, which must render as
        // "not reported", never fabricated. See PhysicalLensSpec / activePhysicalCameraId KDoc.
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val canReadActiveLens = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            capabilities?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
            ) == true

        val startedAt = System.currentTimeMillis()
        val outcome = withTimeoutOrNull(LIVENESS_TIMEOUT_MS) {
            openCaptureClose(cameraManager, cameraId, hasAf, canReadActiveLens) { bytes ->
                onFrameCaptured?.invoke(cameraId, bytes)
            }
        }

        return if (outcome != null) {
            CameraLivenessResult(
                cameraId = cameraId,
                facing = facing,
                opened = outcome.deviceOpened,
                frameReceived = outcome.frameReceived,
                autofocusConverged = if (hasAf) outcome.afConverged else null,
                openToFrameMs = System.currentTimeMillis() - startedAt,
                activePhysicalCameraId = outcome.activePhysicalCameraId,
                // A real, plain-language reason when the device itself refused to open — e.g.
                // another app already has this camera — instead of a generic failure. Reported
                // 2026-07-24 as a common real-world complaint (deep-research catalog).
                errorReason = if (!outcome.deviceOpened) cameraErrorMessage(outcome.errorCode) else null,
            )
        } else {
            CameraLivenessResult(
                cameraId = cameraId,
                facing = facing,
                opened = false,
                frameReceived = false,
                autofocusConverged = null,
                openToFrameMs = null,
                errorReason = "Camera did not respond in time",
            )
        }
    }

    private data class OneCameraOutcome(
        val frameReceived: Boolean,
        val afConverged: Boolean,
        val activePhysicalCameraId: String? = null,
        val deviceOpened: Boolean = true,
        val errorCode: Int? = null,
    )

    /**
     * Plain-language mapping of [CameraDevice.StateCallback]'s onError codes. "Another app is
     * using this camera" is a genuinely common real-world cause (deep-research 2026-07-24) — the
     * device reports this exact reason, so it is safe to state directly, not a guess.
     */
    private fun cameraErrorMessage(code: Int?): String = when (code) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE ->
            "Another app is currently using this camera"
        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE ->
            "Too many apps have a camera open at the same time"
        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED ->
            "This camera is disabled (often a work-profile or device-policy restriction)"
        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE ->
            "The camera reported a device-level fault"
        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE ->
            "The phone's camera service crashed or is unavailable right now"
        null -> "Camera did not respond in time"
        else -> "Camera could not open (error code $code)"
    }

    /**
     * Dedicated HandlerThread + Semaphore(1) open/close, matching the pattern already proven in
     * [PowerConsumptionUtils.RealCameraPowerMeasurer] elsewhere in this app. Kept independent of
     * that class because it is coupled to power-baseline sampling this check does not need.
     */
    private suspend fun openCaptureClose(
        cameraManager: CameraManager,
        cameraId: String,
        checkAutofocus: Boolean,
        readActiveLens: Boolean,
        onFrameCaptured: (ByteArray) -> Unit,
    ): OneCameraOutcome {
        val thread = HandlerThread("CameraHealthCheck-$cameraId").apply { start() }
        val handler = Handler(thread.looper)
        val openCloseLock = Semaphore(1)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var reader: ImageReader? = null

        try {
            if (!openCloseLock.tryAcquire(2, TimeUnit.SECONDS)) {
                return OneCameraOutcome(frameReceived = false, afConverged = false)
            }

            var openErrorCode: Int? = null
            device = suspendCancellableCoroutine { cont ->
                try {
                    @Suppress("MissingPermission")
                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(cd: CameraDevice) {
                            openCloseLock.release()
                            if (cont.isActive) cont.resume(cd)
                        }
                        override fun onDisconnected(cd: CameraDevice) {
                            openCloseLock.release()
                            cd.close()
                            if (cont.isActive) cont.resume(null)
                        }
                        override fun onError(cd: CameraDevice, error: Int) {
                            openCloseLock.release()
                            openErrorCode = error
                            cd.close()
                            if (cont.isActive) cont.resume(null)
                        }
                    }, handler)
                } catch (e: SecurityException) {
                    openCloseLock.release()
                    handleError(e, context = "openCaptureClose:openCamera:$cameraId")
                    if (cont.isActive) cont.resume(null)
                } catch (e: Exception) {
                    openCloseLock.release()
                    handleError(e, context = "openCaptureClose:openCamera:$cameraId")
                    if (cont.isActive) cont.resume(null)
                }
            } ?: return OneCameraOutcome(
                frameReceived = false,
                afConverged = false,
                deviceOpened = false,
                errorCode = openErrorCode,
            )

            val streamConfigMap = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = streamConfigMap?.getOutputSizes(ImageFormat.JPEG)
            val size = sizes?.minByOrNull { it.width.toLong() * it.height }
                ?: return OneCameraOutcome(frameReceived = false, afConverged = false)

            reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)

            var frameReceived = false
            var afConverged = false
            var activePhysicalCameraId: String? = null

            session = suspendCancellableCoroutine { cont ->
                try {
                    device.createCaptureSession(
                        listOf(reader!!.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                if (cont.isActive) cont.resume(s)
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                        handler,
                    )
                } catch (e: Exception) {
                    handleError(e, context = "openCaptureClose:createSession:$cameraId")
                    if (cont.isActive) cont.resume(null)
                }
            } ?: return OneCameraOutcome(frameReceived = false, afConverged = false)

            // Sensor mounting orientation — the raw JPEG is written in the sensor's
            // native frame (usually landscape). Without JPEG_ORIENTATION set, both the
            // saved file AND our decoded preview come out sideways. Front-facing
            // sensors are typically mirrored 270° from portrait; back-facing 90°.
            val sensorOrientation =
                cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            val captured = suspendCancellableCoroutine<Boolean> { cont ->
                var resumed = false
                // Two-phase capture: (1) a repeating preview request drives the camera's
                // auto-exposure and auto-white-balance loops so a single-shot isn't taken
                // cold; (2) the actual still capture fires once AE has converged. Without
                // phase 1, the first JPEG from a freshly opened camera comes out very dark —
                // worst on the back camera whose AE target is calibrated for a wider dynamic
                // range.
                var keepNextFrame = false
                reader?.setOnImageAvailableListener({ r ->
                    val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        if (!keepNextFrame) {
                            // A warm-up frame from the repeating preview request. It's
                            // typically dark and un-oriented — throw it away, we only
                            // keep the still capture that fires after AE convergence.
                            return@setOnImageAvailableListener
                        }
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        onFrameCaptured(bytes)
                        if (!resumed) {
                            resumed = true
                            frameReceived = true
                            if (cont.isActive) cont.resume(true)
                        }
                    } catch (e: Exception) {
                        handleError(e, context = "openCaptureClose:readFrame:$cameraId")
                    } finally {
                        image.close()
                    }
                }, handler)

                // Fires the still capture exactly once — from whichever trigger reaches it
                // first: real AE convergence, or the timeout fallback for hardware that never
                // reports CONTROL_AE_STATE (LEGACY-level devices commonly don't). Declared
                // ahead of the warmup request below because Kotlin resolves local `fun`
                // declarations across the whole enclosing block, not just top-to-bottom, so
                // this forward reference from inside the warmup's CaptureCallback is safe.
                var warmupComplete = false
                fun fireStillCapture() {
                    if (warmupComplete) return
                    warmupComplete = true
                    try {
                        session?.stopRepeating()
                    } catch (_: Exception) { /* session may already be closing */ }

                    val stillBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(reader!!.surface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
                        set(CaptureRequest.JPEG_QUALITY, 90.toByte())
                        if (checkAutofocus) {
                            set(
                                CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START,
                            )
                        }
                    }
                    keepNextFrame = true
                    try {
                        session?.capture(
                            stillBuilder.build(),
                            object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    s: CameraCaptureSession,
                                    r: CaptureRequest,
                                    result: TotalCaptureResult,
                                ) {
                                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                                    if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                                        afConverged = true
                                    }
                                    if (readActiveLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        activePhysicalCameraId =
                                            result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
                                    }
                                }
                                override fun onCaptureFailed(
                                    s: CameraCaptureSession,
                                    r: CaptureRequest,
                                    failure: android.hardware.camera2.CaptureFailure,
                                ) {
                                    if (!resumed) {
                                        resumed = true
                                        if (cont.isActive) cont.resume(false)
                                    }
                                }
                            },
                            handler,
                        )
                    } catch (e: Exception) {
                        handleError(e, context = "openCaptureClose:capture:$cameraId")
                        if (!resumed) {
                            resumed = true
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                }

                try {
                    // Phase 1 — warmup. Repeating preview to drive AE + AWB + AF loops.
                    val previewBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(reader!!.surface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        if (checkAutofocus) {
                            set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                            )
                        }
                    }
                    // 2026-07-25: was a hardcoded 700ms fixed delay, tuned by hand on ONE
                    // device (Pixel 8a). That number has no reason to be right on other
                    // hardware — AE convergence speed varies a lot across OEM camera HALs.
                    // Now waits for the device's OWN reported CONTROL_AE_STATE instead of a
                    // guessed timer, with a floor (don't fire on the very first frame even if
                    // it happens to report converged) and a ceiling (devices that never report
                    // AE state at all — common on LEGACY hardware level — must not hang).
                    val warmupStartMs = SystemClock.elapsedRealtime()
                    session?.setRepeatingRequest(
                        previewBuilder.build(),
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                s: CameraCaptureSession,
                                r: CaptureRequest,
                                result: TotalCaptureResult,
                            ) {
                                if (warmupComplete) return
                                val elapsed = SystemClock.elapsedRealtime() - warmupStartMs
                                if (elapsed < AE_WARMUP_MIN_MS) return
                                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                                if (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                                    aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                                ) {
                                    handler.post { fireStillCapture() }
                                }
                            }
                        },
                        handler,
                    )

                    // Ceiling fallback — fires regardless of AE state once the max warmup
                    // window has passed, so hardware that never reports CONTROL_AE_STATE
                    // (or converges unusually slowly) still gets a photo instead of hanging
                    // until the outer 4s liveness timeout kills the whole check.
                    handler.postDelayed({ fireStillCapture() }, AE_WARMUP_MAX_MS)
                } catch (e: Exception) {
                    handleError(e, context = "openCaptureClose:warmup:$cameraId")
                    if (!resumed) {
                        resumed = true
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }

            return OneCameraOutcome(
                frameReceived = frameReceived || captured,
                afConverged = afConverged,
                activePhysicalCameraId = activePhysicalCameraId,
            )
        } finally {
            try {
                session?.close()
            } catch (_: Exception) {
            }
            try {
                device?.close()
            } catch (_: Exception) {
            }
            try {
                reader?.close()
            } catch (_: Exception) {
            }
            thread.quitSafely()
        }
    }

    // ── AI context (compact — for the AI prompt, not the emoji device dump) ─

    /**
     * A compact block for AI prompts. Deliberately NOT [getDeviceInfoString] (27 emoji-formatted
     * lines) — the AI needs enough context to explain results on THIS phone, not a full spec dump.
     */
    fun buildCameraAiContext(context: Context, result: CameraHealthResult): String {
        val sb = StringBuilder()
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        sb.appendLine()
        sb.appendLine("Cameras this app can see: ${result.factSheet.cameraCount}")
        result.factSheet.lenses.forEach { lens ->
            sb.appendLine(
                "- ${lens.facing} camera (id ${lens.cameraId}): hardware level ${lens.hardwareLevel}, " +
                    "focal length ${lens.focalLengthsMm.joinToString("/").ifEmpty { "not reported" }}mm, " +
                    "aperture f/${lens.aperturesF.joinToString("/").ifEmpty { "not reported" }}, " +
                    "sensor size ${lens.sensorSizeMm ?: "not reported"}mm, " +
                    "ISO range ${lens.isoRange ?: "not reported"}, " +
                    "autofocus ${if (lens.supportsAutofocus) "yes" else "no"}, " +
                    "stabilization ${if (lens.hasOpticalStabilization) "yes" else "no"}, " +
                    "RAW available to this app ${if (lens.rawAvailableToThisApp) "yes" else "no"}, " +
                    "shutter speed range ${lens.exposureTimeRangeSec ?: "not reported"}, " +
                    "auto-exposure modes ${lens.aeModes.joinToString("/").ifEmpty { "not reported" }}, " +
                    "max JPEG resolution ${lens.jpegResolutions.firstOrNull() ?: "not reported"} " +
                    "(${lens.jpegResolutions.size} resolutions supported)",
            )
            if (lens.physicalLenses.isNotEmpty()) {
                lens.physicalLenses.forEach { phys ->
                    sb.appendLine(
                        "  - sub-lens ${phys.physicalId}: focal length " +
                            "${phys.focalLengthsMm.joinToString("/").ifEmpty { "not reported" }}mm, " +
                            "aperture f/${phys.aperturesF.joinToString("/").ifEmpty { "not reported" }}",
                    )
                }
            }
        }
        sb.appendLine()
        sb.appendLine("Test results:")
        result.liveness.forEach { l ->
            val status = when {
                !l.opened -> "did not open (${l.errorReason ?: "unknown reason"})"
                !l.frameReceived -> "opened but no photo came back"
                l.autofocusConverged == false -> "took a photo but focus did not lock"
                else -> "opened and took a photo normally"
            }
            sb.append("- ${l.facing} camera: $status")
            if (l.activePhysicalCameraId != null) {
                sb.append(" (at this zoom, the device reports it used sub-lens ${l.activePhysicalCameraId})")
            }
            sb.appendLine()
        }
        return sb.toString().trim()
    }

    /**
     * Compact AI context for the Screen Test tab. Like [buildCameraAiContext], this is a report
     * of what the USER observed, never a measurement the app made — the phone cannot see its own
     * screen, so there is nothing here for the app to grade.
     */
    fun buildScreenTestAiContext(lastPixelResult: ScreenPixelResult?, maxTouchPoints: Int?): String {
        val sb = StringBuilder()
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        sb.appendLine()
        sb.appendLine("Screen colour / dead-pixel check (user's own eyes judged this, not the app):")
        sb.appendLine(
            when {
                lastPixelResult == null -> "- Not run yet."
                lastPixelResult.userReportedIssue ->
                    "- The user reported seeing a bad spot while the screen showed the colour: " +
                        "${lastPixelResult.colorShownWhenReported ?: "unknown"}."
                else -> "- The user did not see any bad spot during the last check."
            },
        )
        sb.appendLine()
        sb.appendLine("Touch screen check:")
        sb.appendLine(
            if (maxTouchPoints != null) {
                "- The screen detected $maxTouchPoints finger(s) touching at the same time."
            } else {
                "- Not run yet."
            },
        )
        return sb.toString().trim()
    }

    // ── Colour-cast check ("my photos look black and white") ───────────────

    /**
     * Reads the two AOSP-standard causes of a system-wide black-and-white display: Accessibility
     * colour correction set to grayscale/monochromacy simulation, and Battery Saver (which many
     * OEM skins force grayscale under). Both are public [Settings.Secure] / [PowerManager] reads
     * — no permission needed, nothing inferred. Returns (grayscaleOn, batterySaverOn).
     */
    fun readColorCastSystemSignals(context: Context): Pair<Boolean, Boolean> {
        val grayscaleOn = try {
            // Not exposed as public SDK constants (@hide in framework), but these are the stable,
            // documented Settings.Secure key names used by the Accessibility app itself.
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                "accessibility_display_daltonizer_enabled",
                0,
            ) == 1
            // Mode 0 == AccessibilityManager.DALTONIZER_SIMULATE_MONOCHROMACY — true grayscale,
            // as opposed to the colour-blindness-correction modes (protanomaly etc.) which keep
            // colour.
            val mode = Settings.Secure.getInt(
                context.contentResolver,
                "accessibility_display_daltonizer",
                -1,
            )
            enabled && mode == 0
        } catch (e: Exception) {
            handleError(e, context = "readColorCastSystemSignals:grayscale")
            false
        }
        val batterySaverOn = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode == true
        } catch (e: Exception) {
            handleError(e, context = "readColorCastSystemSignals:batterySaver")
            false
        }
        return grayscaleOn to batterySaverOn
    }

    /**
     * Coarse average-saturation read of one captured JPEG frame, downsampled for speed (this is
     * a "does this look grey" signal, not a precision colourimetry measurement). Returns null on
     * any decode failure — the caller must treat null as "couldn't check", never as "not
     * monochrome".
     */
    fun analyzeCapturedFrameForColorCast(jpegBytes: ByteArray): Float? {
        var original: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            original = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
            val sampleSize = 24
            scaled = Bitmap.createScaledBitmap(original, sampleSize, sampleSize, false)
            var totalSaturation = 0f
            val hsv = FloatArray(3)
            for (x in 0 until sampleSize) {
                for (y in 0 until sampleSize) {
                    Color.colorToHSV(scaled.getPixel(x, y), hsv)
                    totalSaturation += hsv[1]
                }
            }
            totalSaturation / (sampleSize * sampleSize)
        } catch (e: Exception) {
            handleError(e, context = "analyzeCapturedFrameForColorCast")
            null
        } finally {
            if (scaled !== original) scaled?.recycle()
            original?.recycle()
        }
    }

    /**
     * Reads what the camera actually used for one real photo, straight from its EXIF tags — real
     * shutter/ISO/aperture/flash for THAT shot, not the declared range from [LensReport]. Returns
     * null only if the JPEG has no EXIF block at all (some OEM camera HALs strip it); a present
     * block with some tags missing still returns a result with those fields null.
     */
    fun readExifSummary(jpegBytes: ByteArray): ExifSummary? {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(jpegBytes))
            @Suppress("DEPRECATION")
            val iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, -1)
                .takeIf { it > 0 }
            // EXIF stores TAG_EXPOSURE_TIME in seconds (not ns) — convert to match
            // formatExposureTimeNs's expected unit before reusing it.
            val exposureSeconds = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0)
                .takeIf { it > 0.0 }
            val exposureFormatted = exposureSeconds?.let {
                formatExposureTimeNs((it * 1_000_000_000.0).toLong())
            }
            val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, -1.0)
                .takeIf { it > 0.0 }?.toFloat()
            val focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, -1.0)
                .takeIf { it > 0.0 }?.toFloat()
            val flashInt = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
            // Bit 0 of the EXIF Flash tag is "flash fired" — bits above that describe mode/return
            // light, which this app has no honest use for.
            val flashFired = if (flashInt >= 0) (flashInt and 0x1) == 1 else null
            ExifSummary(
                isoSpeed = iso,
                exposureTimeSec = exposureFormatted,
                fNumber = fNumber,
                focalLengthMm = focalLength,
                flashFired = flashFired,
            )
        } catch (e: Exception) {
            handleError(e, context = "readExifSummary")
            null
        }
    }

    /** Combines the reads above. [jpegBytes] is optional — pass null to skip the frame checks. */
    fun buildColorCastCheckResult(context: Context, jpegBytes: ByteArray?): ColorCastCheckResult {
        val (grayscaleOn, batterySaverOn) = readColorCastSystemSignals(context)
        val avgSaturation = jpegBytes?.let { analyzeCapturedFrameForColorCast(it) }
        // Threshold picked empirically for "looks grey to a human eye", not a calibrated cutoff —
        // framed to the user as an observation, never a pass/fail grade.
        val looksMonochrome = avgSaturation?.let { it < 0.08f }
        val exifSummary = jpegBytes?.let { readExifSummary(it) }
        return ColorCastCheckResult(
            capturedLooksMonochrome = looksMonochrome,
            averageSaturation = avgSaturation,
            grayscaleAccessibilityOn = grayscaleOn,
            batterySaverOn = batterySaverOn,
            exifSummary = exifSummary,
        )
    }

    /** Plain-language line for the EXIF read — no jargon like "ISO" or "EXIF" shown as-is. */
    fun buildExifPlainText(exif: ExifSummary): String {
        val parts = mutableListOf<String>()
        exif.isoSpeed?.let { parts.add("light sensitivity setting was $it") }
        exif.exposureTimeSec?.let { parts.add("shutter was open for $it") }
        exif.fNumber?.let { parts.add("aperture was f/$it") }
        exif.focalLengthMm?.let { parts.add("focal length was ${it}mm") }
        exif.flashFired?.let { parts.add(if (it) "flash fired" else "flash did not fire") }
        return if (parts.isEmpty()) {
            "This photo's shot details were not saved by this phone's camera — that's normal on some devices."
        } else {
            "When this app took a test photo just now: ${parts.joinToString(", ")}."
        }
    }

    /** Compact AI context for the colour-cast check — same "report what we found" framing. */
    fun buildColorCastAiContext(result: ColorCastCheckResult): String {
        val sb = StringBuilder()
        sb.appendLine("\"My camera looks black and white\" check:")
        sb.appendLine(
            when (result.capturedLooksMonochrome) {
                true -> "- The last captured photo showed almost no colour (this device reports it)."
                false -> "- The last captured photo showed normal colour."
                null -> "- No photo was analyzed yet."
            },
        )
        sb.appendLine(
            "- Accessibility grayscale/colour-correction: " +
                if (result.grayscaleAccessibilityOn) "ON (this is very likely the cause)" else "off",
        )
        sb.appendLine(
            "- Battery Saver: " +
                if (result.batterySaverOn) "ON (some phones force grayscale display under this)" else "off",
        )
        if (!result.grayscaleAccessibilityOn && !result.batterySaverOn &&
            result.capturedLooksMonochrome == true
        ) {
            sb.appendLine(
                "- Neither known setting is on, but the photo still looks grey. This app cannot " +
                    "see every phone brand's own grayscale feature (e.g. Samsung Bedtime Mode) — " +
                    "suggest the user try Safe Mode, or contact the manufacturer if it persists.",
            )
        }
        return sb.toString().trim()
    }

    // ── "Report a Camera Problem" — broad AI hand-off bundle ────────────────

    /**
     * Deep-research pass (2026-07-24, 104 sub-agents) found real evidence for exactly two
     * camera-relevant causes a no-root app can genuinely read: device overheating (some phones
     * block camera access above a thermal threshold — corroborated via Samsung/Pixel support
     * docs) and another app already holding the camera (this app's own liveness check already
     * surfaces that via [CameraLivenessResult.errorReason]). A long checklist of "blurry",
     * "black screen", "green tint" etc. was explicitly investigated and could NOT be tied to any
     * readable signal — several plausible claims (low storage → launch failure, low RAM → crash)
     * were adversarially REFUTED. So this app does not pretend to detect those; it reads what it
     * honestly can, and hands the rest to the user's own words for a general-purpose AI to reason
     * about — matching the research's "structure the facts, then let the model reason" finding.
     */
    fun readCameraEnvironmentSignals(context: Context): CameraEnvironmentSignals {
        val storage = try {
            getAvailableStorage()
        } catch (e: Exception) {
            handleError(e, context = "readCameraEnvironmentSignals:storage")
            "not reported"
        }
        val ram = try {
            getRamUsage(context)
        } catch (e: Exception) {
            handleError(e, context = "readCameraEnvironmentSignals:ram")
            "not reported"
        }
        val lowMemory = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val info = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(info)
            info.lowMemory
        } catch (e: Exception) {
            handleError(e, context = "readCameraEnvironmentSignals:lowMemory")
            false
        }
        val (thermalStatus, thermalSevere) = readThermalStatusPlain(context)
        val canSave = testCameraStorageWrite(context)
        return CameraEnvironmentSignals(storage, ram, lowMemory, thermalStatus, thermalSevere, canSave)
    }

    /**
     * Actually writes a tiny throwaway file where a captured photo would go, then deletes it —
     * a real pass/fail, not an inference from free-space numbers. Uses the app's own cache dir
     * (no storage permission needed) since the question is "can this app write a file right now",
     * not "is the public gallery folder healthy".
     */
    fun testCameraStorageWrite(context: Context): Boolean {
        return try {
            val probe = java.io.File(context.cacheDir, "camera_write_probe_${System.currentTimeMillis()}.tmp")
            probe.writeBytes(byteArrayOf(1, 2, 3, 4))
            val wroteOk = probe.exists() && probe.length() == 4L
            probe.delete()
            wroteOk
        } catch (e: Exception) {
            handleError(e, context = "testCameraStorageWrite")
            false
        }
    }

    private fun readThermalStatusPlain(context: Context): Pair<String, Boolean> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "not reported (needs Android 10+)" to false
        }
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            when (pm?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "normal" to false
                PowerManager.THERMAL_STATUS_LIGHT -> "light throttling" to false
                PowerManager.THERMAL_STATUS_MODERATE -> "moderate throttling" to false
                PowerManager.THERMAL_STATUS_SEVERE -> "severe — running hot" to true
                PowerManager.THERMAL_STATUS_CRITICAL -> "critical — very hot" to true
                PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency — dangerously hot" to true
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown imminent from heat" to true
                else -> "not reported" to false
            }
        } catch (e: Exception) {
            handleError(e, context = "readThermalStatusPlain")
            "not reported" to false
        }
    }

    /**
     * Builds the full text handed to an external AI app (ChatGPT, Gemini, etc.) via the existing
     * share pipeline. [selectedSymptoms] and [otherDescription] are the user's own words — this
     * app makes no claim about what causes them, it only supplies verified facts alongside them.
     */
    fun buildCameraProblemReport(
        context: Context,
        factSheet: CameraFactSheet,
        liveness: List<CameraLivenessResult>?,
        colorCast: ColorCastCheckResult?,
        environment: CameraEnvironmentSignals,
        selectedSymptoms: List<String>,
        otherDescription: String,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("A person is asking for help with a camera problem on their Android phone.")
        sb.appendLine(
            "Please read the facts below and suggest what is likely happening and what they " +
                "can try — using only what is actually stated here, not guesses beyond it.",
        )
        sb.appendLine()
        sb.appendLine("WHAT THEY REPORTED:")
        selectedSymptoms.forEach { sb.appendLine("- $it") }
        if (otherDescription.isNotBlank()) {
            sb.appendLine("- In their own words: \"$otherDescription\"")
        }
        if (selectedSymptoms.isEmpty() && otherDescription.isBlank()) {
            sb.appendLine("- (no symptom selected — general check)")
        }
        sb.appendLine()
        sb.appendLine("DEVICE: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        sb.appendLine()
        sb.appendLine("WHAT THIS APP CAN CONFIRM RIGHT NOW:")
        sb.appendLine("- Storage: ${environment.storageInfo}")
        sb.appendLine(
            "- Memory: ${environment.ramInfo}" +
                if (environment.lowMemory) " — system reports low memory" else "",
        )
        sb.appendLine(
            "- Temperature: device reports ${environment.thermalStatus}" +
                if (environment.thermalSevere) {
                    " — some phones block camera access to protect the battery when this hot"
                } else "",
        )
        sb.appendLine(
            "- Saving a new photo right now: " +
                if (environment.canSaveNewPhotos) {
                    "this app was able to write a file just now, so saving should work"
                } else {
                    "this app just TRIED to write a file and it FAILED — this can stop photos " +
                        "from saving even when the camera itself works fine"
                },
        )
        val lastCameraCrash = try {
            CameraCrashTracker.getLastCameraCrash(context)
        } catch (e: Exception) {
            null
        }
        sb.appendLine(
            if (lastCameraCrash != null) {
                val daysAgo = (System.currentTimeMillis() - lastCameraCrash.timestampMs) / 86_400_000L
                "- This app itself crashed while touching the camera $daysAgo day(s) ago " +
                    "(${lastCameraCrash.reason}). This is about THIS diagnostics app, not other " +
                    "camera apps like WhatsApp or the phone's own camera app."
            } else {
                "- This app has not crashed on the camera recently (only tracks its own crashes, " +
                    "not other apps')."
            },
        )
        sb.appendLine()
        sb.appendLine("CAMERA HARDWARE (declared by the device, not measured):")
        sb.appendLine("Cameras this app can see: ${factSheet.cameraCount}")
        factSheet.lenses.forEach { lens ->
            sb.appendLine(
                "- ${lens.facing} camera: hardware level ${lens.hardwareLevel}, " +
                    "focal length ${lens.focalLengthsMm.joinToString("/").ifEmpty { "not reported" }}mm, " +
                    "autofocus ${if (lens.supportsAutofocus) "yes" else "no"}, " +
                    "flash ${if (lens.hasFlash) "yes" else "no"}, " +
                    "video recording ${if (lens.videoRecordingSupported) "supported" else "not reported"}" +
                    (lens.maxVideoResolution?.let { " (up to $it)" } ?: ""),
            )
        }
        if (liveness != null && liveness.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("LAST LIVE CHECK (this app opened each camera just now):")
            liveness.forEach { l ->
                val status = when {
                    !l.opened -> "did not open — ${l.errorReason ?: "unknown reason"}"
                    !l.frameReceived -> "opened but no photo came back"
                    l.autofocusConverged == false -> "took a photo but focus did not lock"
                    else -> "opened and took a photo normally"
                }
                sb.appendLine("- ${l.facing} camera: $status")
            }
        } else {
            sb.appendLine()
            sb.appendLine("LAST LIVE CHECK: not run yet.")
        }
        if (colorCast != null) {
            sb.appendLine()
            sb.appendLine(buildColorCastAiContext(colorCast))
            colorCast.exifSummary?.let {
                sb.appendLine(buildExifPlainText(it))
            }
        }
        sb.appendLine()
        sb.appendLine(
            "If none of the facts above explain the symptom, say so plainly and suggest " +
                "general next steps (restart, check for a system update, contact the phone " +
                "maker) rather than guessing a specific cause.",
        )
        return sb.toString().trim()
    }

    // ── Telemetry (Phase 0.5 — decides whether the zoom-lens UI ships at all) ─

    /**
     * Silent probe, no UI. Answers "does the real BD/India install base support per-physical-lens
     * reporting" before any zoom-sweep UI is built on top of it — per the research open question:
     * the feature is cosmetic if it returns "not reported" for most users.
     */
    fun logMultiCamTelemetry(context: Context, factSheet: CameraFactSheet) {
        val maxPhysicalCount = factSheet.lenses.maxOfOrNull { it.physicalLensCount } ?: 1
        AnalyticsUtils.logEvent(
            AnalyticsEvent.CameraMultiCamSupport,
            mapOf(
                "physical_id_count" to maxPhysicalCount,
                "camera_count" to factSheet.cameraCount,
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
            ),
        )
    }
}
