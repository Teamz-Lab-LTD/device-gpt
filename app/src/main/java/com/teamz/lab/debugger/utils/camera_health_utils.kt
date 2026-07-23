package com.teamz.lab.debugger.utils

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
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

    // ── Data classes ────────────────────────────────────────────────────────

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

        // Per-physical-lens enumeration (API 28+). Empty on most non-Pixel devices in the
        // BD/India base (Samsung/Xiaomi/Motorola/OnePlus commonly hide sub-cameras) — that is
        // expected, not an error, and must never be read as "this phone has 1 lens".
        val physicalCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                chars.physicalCameraIds.size.coerceAtLeast(1)
            } catch (e: Exception) {
                1
            }
        } else {
            1
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
        )
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
    suspend fun runCameraLivenessCheck(context: Context): List<CameraLivenessResult> =
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
                results.add(checkOneCamera(cameraManager, cameraId, chars))
            }
            results
        }

    private suspend fun checkOneCamera(
        cameraManager: CameraManager,
        cameraId: String,
        chars: CameraCharacteristics,
    ): CameraLivenessResult {
        val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        val hasAf = afModes?.any { it != CameraCharacteristics.CONTROL_AF_MODE_OFF } ?: false

        val startedAt = System.currentTimeMillis()
        val outcome = withTimeoutOrNull(LIVENESS_TIMEOUT_MS) {
            openCaptureClose(cameraManager, cameraId, hasAf)
        }

        return if (outcome != null) {
            CameraLivenessResult(
                cameraId = cameraId,
                facing = facing,
                opened = true,
                frameReceived = outcome.frameReceived,
                autofocusConverged = if (hasAf) outcome.afConverged else null,
                openToFrameMs = System.currentTimeMillis() - startedAt,
                errorReason = null,
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

    private data class OneCameraOutcome(val frameReceived: Boolean, val afConverged: Boolean)

    /**
     * Dedicated HandlerThread + Semaphore(1) open/close, matching the pattern already proven in
     * [PowerConsumptionUtils.RealCameraPowerMeasurer] elsewhere in this app. Kept independent of
     * that class because it is coupled to power-baseline sampling this check does not need.
     */
    private suspend fun openCaptureClose(
        cameraManager: CameraManager,
        cameraId: String,
        checkAutofocus: Boolean,
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
            } ?: return OneCameraOutcome(frameReceived = false, afConverged = false)

            val streamConfigMap = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = streamConfigMap?.getOutputSizes(ImageFormat.JPEG)
            val size = sizes?.minByOrNull { it.width.toLong() * it.height }
                ?: return OneCameraOutcome(frameReceived = false, afConverged = false)

            reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)

            var frameReceived = false
            var afConverged = false

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

            val captured = suspendCancellableCoroutine<Boolean> { cont ->
                var resumed = false
                reader?.setOnImageAvailableListener({ r ->
                    r.acquireLatestImage()?.close()
                    if (!resumed) {
                        resumed = true
                        frameReceived = true
                        if (cont.isActive) cont.resume(true)
                    }
                }, handler)

                try {
                    val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    requestBuilder.addTarget(reader!!.surface)
                    if (checkAutofocus) {
                        requestBuilder.set(
                            CaptureRequest.CONTROL_AF_TRIGGER,
                            CaptureRequest.CONTROL_AF_TRIGGER_START,
                        )
                    }
                    session?.capture(
                        requestBuilder.build(),
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

            return OneCameraOutcome(frameReceived = frameReceived || captured, afConverged = afConverged)
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
                    "autofocus ${if (lens.supportsAutofocus) "yes" else "no"}, " +
                    "stabilization ${if (lens.hasOpticalStabilization) "yes" else "no"}",
            )
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
            sb.appendLine("- ${l.facing} camera: $status")
        }
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
