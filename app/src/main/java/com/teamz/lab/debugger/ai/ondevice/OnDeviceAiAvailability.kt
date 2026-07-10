package com.teamz.lab.debugger.ai.ondevice

import android.content.Context
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime feature-detect for on-device Gemini Nano / Gemma 4 via ML Kit GenAI.
 *
 * Intentionally defensive: a device without AICore (older phones, emulators, low-RAM
 * devices) must gracefully fall back to the existing cloud AI chooser, never crash.
 */
object OnDeviceAiAvailability {

    enum class Status {
        /** Device + OS support on-device AI and the model is ready to use. */
        READY,

        /** Device supports on-device AI but the Gemini Nano model isn't downloaded yet. */
        DOWNLOADABLE,

        /** This device cannot run on-device AI (too old, wrong SoC, emulator, etc.). */
        UNSUPPORTED,

        /** The ML Kit GenAI library isn't linked or threw at startup. */
        LIBRARY_MISSING,

        /** We haven't checked yet. */
        UNKNOWN,
    }

    @Volatile
    private var cachedStatus: Status = Status.UNKNOWN

    /**
     * Test seam. A healthy AICore answers in milliseconds, so a test cannot otherwise
     * reproduce the slow probe that wedged the main thread. Instrumented tests install a
     * deliberately slow probe here to assert we never block Main. Never set in production.
     */
    @Volatile
    @androidx.annotation.VisibleForTesting
    internal var probeOverride: ((Context) -> Status)? = null

    /**
     * Cheap synchronous check — returns the last known status without any IPC.
     * Safe to call from the UI thread when deciding whether to show the Private AI option.
     */
    fun lastKnownStatus(): Status = cachedStatus

    /**
     * Asynchronous deep check. Call once from Application.onCreate() and optionally again
     * when the user opens the AI chooser. Caches the result.
     *
     * [computeStatus] blocks on AICore IPC, so this must never run on the caller's
     * dispatcher: two UI call sites reach it from `LaunchedEffect` / `rememberCoroutineScope`,
     * both of which are Dispatchers.Main. Pin it to IO here so every caller is safe.
     */
    suspend fun refreshStatus(context: Context): Status = withContext(Dispatchers.IO) {
        val s = computeStatus(context)
        cachedStatus = s
        s
    }

    private fun computeStatus(context: Context): Status {
        probeOverride?.let { return it(context) }
        // Minimum OS gate. ML Kit GenAI APIs target API 26+, but usable on-device AI
        // in practice requires Android 14+ AICore on supported chipsets.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return Status.UNSUPPORTED
        }
        return try {
            PrivateAiExplainer.probeAvailability(context)
        } catch (noClass: NoClassDefFoundError) {
            FirebaseCrashlytics.getInstance().log("ML Kit GenAI class missing: ${noClass.message}")
            Status.LIBRARY_MISSING
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(t)
            Status.UNSUPPORTED
        }
    }
}
