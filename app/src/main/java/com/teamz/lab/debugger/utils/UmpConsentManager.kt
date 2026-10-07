package com.teamz.lab.debugger.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google User Messaging Platform (UMP) consent flow for GDPR + PDPA + LGPD compliance.
 *
 * v3.1.8 hotfix: UMP init moved off main thread + hard timeout watchdog. v3.1.7 shipped
 * with `requestConsentInfoUpdate` running on main thread → Crashlytics showed 13 ANRs
 * inside `com.google.android.gms.internal.consent_sdk.zzay.zzf` (SDK does sync I/O
 * before going async). Background dispatch + 5s timeout makes UMP non-blocking even
 * on slow networks / device.
 */
object UmpConsentManager {
    private const val TAG = "UmpConsentManager"
    private const val UMP_TIMEOUT_MS = 5000L

    @Volatile private var initialized = false

    /**
     * Cached answer to "may this app request an ad right now", readable without a Context.
     *
     * Mirrors ReferralManager.isAdFreeFromReferralsCached() so RemoteConfigUtils — which takes
     * no Context in its ad gates — can consult consent at the same choke point as premium and
     * country suppression.
     *
     * Starts false on purpose. Until UMP has actually answered, the honest state is "we do not
     * know whether this user is in a consent geo", and requesting an ad on a guess is what
     * produced the AdMob "Consent requirement: No CMP" flag on 2026-09-22.
     */
    private val _adsPermitted = kotlinx.coroutines.flow.MutableStateFlow(false)
    private var adsPermitted: Boolean
        get() = _adsPermitted.value
        set(value) { _adsPermitted.value = value }

    /**
     * The same answer as an observable value. Native ads load from composables that start
     * BEFORE UMP resolves (cold start: loader at ~1.8 s, consent at ~3.1 s on the emulator,
     * 2026-10-08). Reading the plain flag there skipped the load and nothing ever retried it,
     * so from 3.1.27 (2026-09-24) most sessions got no native ad at all. Collect this instead.
     */
    val adsPermittedFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _adsPermitted

    /** True only once UMP has confirmed consent is obtained or not required. */
    fun adsPermittedCached(): Boolean = adsPermitted

    /**
     * Whether ads may be requested.
     *
     * Was `true` on exception. That fails OPEN: any SDK error turned into "go ahead, request
     * ads", with no TC string attached, in exactly the geos where that is a regulatory problem.
     * It now fails CLOSED. The revenue exposure is bounded — canRequestAds() returns true for
     * NOT_REQUIRED (every non-EEA user, which is ~89% of this app's impressions) as soon as UMP
     * resolves, and resolution is served from cache for returning users.
     */
    fun canRequestAds(context: Context): Boolean {
        val allowed = try {
            UserMessagingPlatform.getConsentInformation(context).canRequestAds()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "canRequestAds() threw — failing closed: ${e.message}")
            false
        }
        adsPermitted = allowed
        return allowed
    }

    fun ensureConsent(activity: Activity, onConsentReady: () -> Unit) {
        if (initialized) {
            onConsentReady()
            return
        }
        initialized = true

        val mainHandler = Handler(Looper.getMainLooper())
        val callbackFired = AtomicBoolean(false)
        val fireOnce: () -> Unit = {
            if (callbackFired.compareAndSet(false, true)) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    onConsentReady()
                } else {
                    mainHandler.post { onConsentReady() }
                }
            }
        }

        // Watchdog: if UMP SDK doesn't resolve in UMP_TIMEOUT_MS, proceed anyway.
        mainHandler.postDelayed({
            if (!callbackFired.get()) {
                // Deliberately does NOT set adsPermitted. The callback exists so the UI is not
                // held hostage by a slow network; it is not evidence that consent was given.
                android.util.Log.w(TAG, "UMP timeout after ${UMP_TIMEOUT_MS}ms — UI proceeds, ads stay blocked")
                try {
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.UmpConsentFailed,
                        mapOf("error" to "timeout_${UMP_TIMEOUT_MS}ms")
                    )
                } catch (_: Exception) { /* analytics not critical */ }
                fireOnce()
            }
        }, UMP_TIMEOUT_MS)

        CoroutineScope(Dispatchers.IO).launch {
            val consentInfo = try {
                UserMessagingPlatform.getConsentInformation(activity)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "getConsentInformation failed — bypassing UMP: ${e.message}")
                fireOnce()
                return@launch
            }

            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .apply {
                    if (com.teamz.lab.debugger.BuildConfig.DEBUG) {
                        val debugSettings = ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .addTestDeviceHashedId("47B870AD1E1B7428ECA11EA24696D6DB")
                            .build()
                        setConsentDebugSettings(debugSettings)
                    }
                }
                .build()

            // requestConsentInfoUpdate callbacks fire on main thread; the call itself
            // must be made with a live Activity. Re-check liveness before invoking.
            withContext(Dispatchers.Main) {
                if (activity.isFinishing || activity.isDestroyed) {
                    fireOnce()
                    return@withContext
                }
                consentInfo.requestConsentInfoUpdate(
                    activity,
                    params,
                    {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            if (formError != null) {
                                android.util.Log.w(TAG, "Consent form error: ${formError.message}")
                            }
                            adsPermitted = try {
                                consentInfo.canRequestAds()
                            } catch (_: Exception) {
                                false
                            }
                            try {
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.UmpConsentResolved,
                                    mapOf(
                                        "can_request_ads" to consentInfo.canRequestAds().toString(),
                                        "status" to consentInfo.consentStatus.toString()
                                    )
                                )
                            } catch (_: Exception) { /* analytics not critical */ }
                            fireOnce()
                        }
                    },
                    { requestError ->
                        android.util.Log.w(TAG, "requestConsentInfoUpdate failed: ${requestError.message}")
                        try {
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.UmpConsentFailed,
                                mapOf("error" to (requestError.message ?: "unknown"))
                            )
                        } catch (_: Exception) { /* analytics not critical */ }
                        fireOnce()
                    }
                )
            }
        }
    }

    fun debugReset(activity: Activity) {
        if (!com.teamz.lab.debugger.BuildConfig.DEBUG) return
        try {
            UserMessagingPlatform.getConsentInformation(activity).reset()
            initialized = false
            android.util.Log.d(TAG, "Debug UMP state reset")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "debugReset failed: ${e.message}")
        }
    }
}
