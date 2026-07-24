package com.teamz.lab.debugger.utils

import android.content.Context

/**
 * A correction, not a new idea: this app once claimed it could pull "this app's past camera
 * crashes" from Firebase Crashlytics for the camera problem report. That claim was wrong —
 * Crashlytics is write-only from on-device code; the SDK cannot read back a history of past
 * crash reasons at runtime (only `didCrashOnPreviousExecution()`, a bare boolean with no reason).
 * That data only exists in the Crashlytics *dashboard*, which this app has no access to.
 *
 * This is the honest version: a tiny local record, written by this app's own global exception
 * handler ([com.teamz.lab.debugger.MyApplication.setupGlobalExceptionHandler]) BEFORE the crash
 * reaches Crashlytics, kept only when the stack trace actually mentions a camera class. Covers
 * only THIS app's own crashes — it was never possible to see whether WhatsApp or the stock
 * Camera app crashed, and this does not pretend otherwise.
 */
object CameraCrashTracker {
    private const val PREFS = "camera_crash_tracker"
    private const val KEY_TIMESTAMP = "last_camera_crash_ts"
    private const val KEY_REASON = "last_camera_crash_reason"

    private val CAMERA_MARKERS = listOf(
        "camera2", "cameramanager", "cameradevice", "cameracapturesession",
        "camcorder", "imagereader", ".camera.", "cameraaccessexception",
    )

    data class CameraCrashInfo(val timestampMs: Long, val reason: String)

    /** Call from the global uncaught-exception handler, before it rethrows. Never throws itself. */
    fun recordIfCameraRelated(context: Context, throwable: Throwable) {
        try {
            val trace = android.util.Log.getStackTraceString(throwable)
            val isCameraRelated = CAMERA_MARKERS.any { trace.contains(it, ignoreCase = true) }
            if (!isCameraRelated) return
            val reason = "${throwable.javaClass.simpleName}: ${throwable.message?.take(120) ?: "no message"}"
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .putString(KEY_REASON, reason)
                .apply()
        } catch (e: Exception) {
            // Must never break the real crash-handling path this runs inside of.
            android.util.Log.w("CameraCrashTracker", "recordIfCameraRelated failed: ${e.message}")
        }
    }

    fun getLastCameraCrash(context: Context): CameraCrashInfo? {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val ts = prefs.getLong(KEY_TIMESTAMP, 0L)
            if (ts == 0L) return null
            val reason = prefs.getString(KEY_REASON, null) ?: return null
            CameraCrashInfo(ts, reason)
        } catch (e: Exception) {
            null
        }
    }
}
