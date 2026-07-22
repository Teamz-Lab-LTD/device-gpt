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

    /** Guards the one-non-fatal-per-process rule in [computeStatus]. */
    private val probeFailureReported = java.util.concurrent.atomic.AtomicBoolean(false)

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
     * Test seam. [cachedStatus] and [probeFailureReported] are process-wide, so a test that
     * asserts "the probe ran exactly once" would otherwise be order-dependent on whatever
     * test ran before it. Never called in production.
     */
    @androidx.annotation.VisibleForTesting
    internal fun resetForTest() {
        cachedStatus = Status.UNKNOWN
        probeFailureReported.set(false)
        probeOverride = null
    }

    /**
     * True for statuses that describe the device or the linked libraries rather than the
     * model download, so they cannot change inside one process lifetime.
     *
     * READY and DOWNLOADABLE deliberately are NOT terminal: the system-managed Gemini Nano
     * download flips DOWNLOADABLE -> READY while the app is running, and the chooser has to
     * see that.
     */
    private val Status.isTerminal: Boolean
        get() = this == Status.UNSUPPORTED || this == Status.LIBRARY_MISSING

    /**
     * Asynchronous deep check. Call once from Application.onCreate() and optionally again
     * when the user opens the AI chooser.
     *
     * Caching is real, not decorative. This used to recompute unconditionally — the doc said
     * "caches the result" while [cachedStatus] was written and never read — so every open of
     * the AI chooser re-ran the AICore IPC probe. On devices without AICore that probe throws,
     * and [computeStatus] reports the throw to Crashlytics, so a single user generated one
     * non-fatal per dialog open: 1,193 events from 9 users in one week (2026-07-20), which
     * buried the app's real crashes. A terminal status is now answered from cache.
     *
     * [computeStatus] blocks on AICore IPC, so this must never run on the caller's
     * dispatcher: two UI call sites reach it from `LaunchedEffect` / `rememberCoroutineScope`,
     * both of which are Dispatchers.Main. Pin it to IO here so every caller is safe.
     *
     * @param force re-probe even when a terminal status is cached. Only for tests and for an
     *   explicit user-triggered retry; normal UI call sites must leave this false.
     */
    suspend fun refreshStatus(context: Context, force: Boolean = false): Status =
        withContext(Dispatchers.IO) {
            val cached = cachedStatus
            if (!force && cached.isTerminal) return@withContext cached
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
            // Report the first failure per process only. The cache in refreshStatus already
            // stops the repeat probes that caused the flood, but `force = true` and any future
            // caller can still reach this path — one device's unsupported hardware is worth
            // exactly one non-fatal, never a stream of identical ones.
            if (probeFailureReported.compareAndSet(false, true)) {
                FirebaseCrashlytics.getInstance().recordException(t)
            } else {
                FirebaseCrashlytics.getInstance().log("AICore probe failed again: ${t.message}")
            }
            Status.UNSUPPORTED
        }
    }
}
