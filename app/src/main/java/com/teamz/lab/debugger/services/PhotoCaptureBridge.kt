package com.teamz.lab.debugger.services

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Size
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Coordinates a single one-shot camera capture between the AI Bridge HTTP thread
 * (which is blocked waiting for a JPEG) and the [PhotoCaptureConsentActivity] (which
 * shows the user the consent dialog and, if allowed, runs the actual Camera2 capture).
 *
 * Flow:
 *   1. HTTP thread calls [request], gets a fresh [PendingCapture] with a latch.
 *   2. HTTP thread launches [PhotoCaptureConsentActivity] and blocks on the latch.
 *   3. Activity shows a modal dialog: Allow / Deny.
 *   4. On Allow → Activity calls [capture] which does the Camera2 dance.
 *   5. Activity calls [complete] with the result → latch releases → HTTP thread
 *      reads the result and returns JSON.
 *
 * Only one capture at a time — a second /capture_photo while one is in flight
 * returns immediately with error_code="already_in_flight".
 */
object PhotoCaptureBridge {

    /** What the HTTP endpoint receives back. Exactly one of jpegBytes / errorCode is non-null. */
    data class Result(
        val jpegBytes: ByteArray?,
        val width: Int,
        val height: Int,
        val cameraId: String?,
        val errorCode: String?,
        val userMessage: String?,
    )

    // Opaque handle to a capture in flight. Callers get one from [request] and pass it back
    // to [awaitResult] — they can't do anything else with it. Visibility is `internal` so the
    // Kotlin visibility check doesn't complain about a `private` type leaking out of `public`
    // function signatures.
    class PendingCapture internal constructor(
        internal val cameraId: String?,
        internal val latch: CountDownLatch,
        internal val result: AtomicReference<Result?>,
    )

    private val current = AtomicReference<PendingCapture?>(null)

    /**
     * HTTP thread entrypoint. Creates a new pending capture, returns it. Caller then
     * launches the consent activity + calls [awaitResult].
     */
    fun request(cameraId: String?): PendingCapture? {
        val pending = PendingCapture(cameraId, CountDownLatch(1), AtomicReference(null))
        // compareAndSet to serialise — one capture in flight at a time
        return if (current.compareAndSet(null, pending)) pending else null
    }

    /** HTTP thread blocks here for up to [timeoutMs]. */
    fun awaitResult(pending: PendingCapture, timeoutMs: Long): Result {
        val ok = pending.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        val r = pending.result.get()
        current.compareAndSet(pending, null)
        return r ?: Result(
            jpegBytes = null,
            width = 0,
            height = 0,
            cameraId = pending.cameraId,
            errorCode = if (!ok) "timeout" else "no_result",
            userMessage = if (!ok) {
                "The capture request timed out (either you did not tap Allow in time, or " +
                "the camera was slow to respond). Ask me to retry."
            } else {
                "The capture completed without a result. This is a bug — please report."
            },
        )
    }

    /** Activity-thread entrypoint. Fires the actual capture on the given cameraId. */
    fun capture(context: Context, cameraId: String, onDone: (Result) -> Unit) {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val thread = HandlerThread("bridge-capture").apply { start() }
        val handler = Handler(thread.looper)

        val cleanup = { thread.quitSafely() }

        try {
            val chars = cm.getCameraCharacteristics(cameraId)
            val jpegSize = pickCaptureSize(chars)
            val reader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 1)

            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    onDone(Result(
                        jpegBytes = bytes,
                        width = image.width,
                        height = image.height,
                        cameraId = cameraId,
                        errorCode = null,
                        userMessage = null,
                    ))
                } finally {
                    image.close()
                    reader.close()
                    cleanup()
                }
            }, handler)

            @Suppress("MissingPermission")
            cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    createSessionAndCapture(camera, reader.surface, handler, onDone) { camera.close(); reader.close(); cleanup() }
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close(); reader.close(); cleanup()
                    onDone(Result(null, 0, 0, cameraId, "camera_disconnected",
                        "Camera $cameraId disconnected before the photo could be taken. Try again."))
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); reader.close(); cleanup()
                    onDone(Result(null, 0, 0, cameraId, "camera_error_$error",
                        "Camera $cameraId reported error $error and could not open."))
                }
            }, handler)
        } catch (e: SecurityException) {
            cleanup()
            onDone(Result(null, 0, 0, cameraId, "security_exception",
                "Android refused the camera-open request. Check DeviceGPT has Camera permission."))
        } catch (e: Exception) {
            cleanup()
            onDone(Result(null, 0, 0, cameraId, "capture_setup_failed",
                "Could not set up the capture: ${e.message ?: e.javaClass.simpleName}."))
        }
    }

    /** Delivers the final result + releases the HTTP thread. */
    fun complete(result: Result) {
        val p = current.get() ?: return
        p.result.set(result)
        p.latch.countDown()
    }

    /** Convert JPEG bytes to base64 (no newlines, safe for JSON transport). */
    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    // ─────────────────────── private helpers ───────────────────────

    private fun createSessionAndCapture(
        camera: CameraDevice,
        surface: Surface,
        handler: Handler,
        onDone: (Result) -> Unit,
        cleanup: () -> Unit,
    ) {
        try {
            @Suppress("DEPRECATION")
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(surface)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                        set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                    }.build()
                    session.capture(req, null, handler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    camera.close(); cleanup()
                    onDone(Result(null, 0, 0, null, "capture_session_failed",
                        "The camera capture session could not be configured. Try restarting the phone."))
                }
            }, handler)
        } catch (e: Exception) {
            camera.close(); cleanup()
            onDone(Result(null, 0, 0, null, "capture_request_failed",
                "Could not send the capture request: ${e.message ?: e.javaClass.simpleName}."))
        }
    }

    /**
     * Pick a reasonable JPEG size. Prefer ≤ 1920x1080 so base64 payload stays under ~500KB;
     * the MCP transport is stdio and any client that has to relay this to a cloud endpoint
     * will thank us for not shipping a 12 MP frame.
     */
    private fun pickCaptureSize(chars: CameraCharacteristics): Size {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return Size(1920, 1080)
        val jpegSizes = map.getOutputSizes(ImageFormat.JPEG) ?: return Size(1920, 1080)
        return jpegSizes
            .filter { it.width <= 1920 && it.height <= 1920 }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: jpegSizes.first()
    }
}
