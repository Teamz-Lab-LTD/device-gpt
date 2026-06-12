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

    fun canRequestAds(context: Context): Boolean {
        return try {
            UserMessagingPlatform.getConsentInformation(context).canRequestAds()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "canRequestAds() threw — treating as true: ${e.message}")
            true
        }
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
                android.util.Log.w(TAG, "UMP timeout after ${UMP_TIMEOUT_MS}ms — proceeding without consent")
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
