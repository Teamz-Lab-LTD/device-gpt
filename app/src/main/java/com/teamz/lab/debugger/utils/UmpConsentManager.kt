package com.teamz.lab.debugger.utils

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Google User Messaging Platform (UMP) consent flow for GDPR + PDPA + LGPD compliance.
 *
 * Why this exists:
 *   - EU/EEA users (GDPR), Singapore (PDPA), Brazil (LGPD), Switzerland (FADP),
 *     Sweden + other strict regions require explicit consent before serving ads.
 *   - Without UMP, ad networks (especially mediated partners) REFUSE to bid → all
 *     requests in those geos return NO_FILL (code 3) → ad_failed event storm.
 *   - 2026-06-03 GA4 audit: Sweden 0% fill, Singapore 0% fill, Netherlands 1.2% fill —
 *     these correlate with missing UMP integration.
 *
 * What this does (production path):
 *   1. On app start, request consent info update from UMP.
 *   2. If a consent form is required and available, load + show it.
 *   3. After consent (granted, denied, or N/A for non-regulated geos), call the
 *      onConsentReady callback so MobileAds.initialize() can proceed.
 *   4. Once the user has provided consent (or doesn't need to), `canRequestAds`
 *      returns true. Until then, do NOT request ads.
 *
 * Failure modes (intentional — never block the app):
 *   - If UMP itself fails (network error, SDK error), log + proceed AS IF consent
 *     not required. Better to attempt ad load than block forever.
 *   - If user denies consent, we still proceed — Google will serve non-personalized
 *     ads automatically based on the stored consent state.
 */
object UmpConsentManager {
    private const val TAG = "UmpConsentManager"

    @Volatile private var initialized = false

    /**
     * Returns true if UMP says we can request ads (consent obtained or not required
     * for this geo). False until the consent flow completes.
     */
    fun canRequestAds(context: Context): Boolean {
        return try {
            UserMessagingPlatform.getConsentInformation(context).canRequestAds()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "canRequestAds() threw — treating as true: ${e.message}")
            true
        }
    }

    /**
     * Call from MainActivity.onCreate (needs Activity for the form dialog). Runs the
     * full consent flow + invokes onConsentReady once it's safe to call
     * MobileAds.initialize / load ads.
     *
     * If consent flow fails for any reason, onConsentReady still fires so the app
     * is never blocked from serving ads.
     */
    fun ensureConsent(activity: Activity, onConsentReady: () -> Unit) {
        if (initialized) {
            // Already ran in this process — fire callback immediately so caller can
            // proceed to ad init.
            onConsentReady()
            return
        }
        initialized = true

        val consentInfo = try {
            UserMessagingPlatform.getConsentInformation(activity)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "getConsentInformation failed — bypassing UMP: ${e.message}")
            onConsentReady()
            return
        }

        val params = ConsentRequestParameters.Builder()
            // Production: set tagForUnderAgeOfConsent based on your TOS. Default false.
            .setTagForUnderAgeOfConsent(false)
            .apply {
                // Debug builds only: force EEA geography so the consent form fires
                // regardless of device IP. Lets us QA the dialog from any country.
                // Production builds never override geography — UMP uses real IP geo.
                if (com.teamz.lab.debugger.BuildConfig.DEBUG) {
                    val debugSettings = ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        // Add this device as a "test device" so debug settings apply.
                        // The hashed ID is logged by UMP itself the first time we call
                        // requestConsentInfoUpdate — check logcat for "ConsentInformation"
                        // and add the hash here. For now we tag all debug builds.
                        // Pixel 8a (test device) — captured 2026-06-03 from UMP logcat.
                        // To add a different test device: install debug build, grep logcat
                        // for "UserMessagingPlatform: Use new ConsentDebugSettings.Builder()
                        // .addTestDeviceHashedId" — Google logs the hash there.
                        .addTestDeviceHashedId("47B870AD1E1B7428ECA11EA24696D6DB")
                        .build()
                    setConsentDebugSettings(debugSettings)
                }
            }
            .build()

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent info updated — load and show form if required.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        android.util.Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    // Fire callback regardless of form result — Google handles the
                    // personalized-vs-non-personalized decision via stored consent state.
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.UmpConsentResolved,
                        mapOf(
                            "can_request_ads" to consentInfo.canRequestAds().toString(),
                            "status" to consentInfo.consentStatus.toString()
                        )
                    )
                    onConsentReady()
                }
            },
            { requestError ->
                android.util.Log.w(TAG, "requestConsentInfoUpdate failed: ${requestError.message}")
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.UmpConsentFailed,
                    mapOf("error" to (requestError.message ?: "unknown"))
                )
                // Don't block — proceed to ad init.
                onConsentReady()
            }
        )
    }

    /**
     * Force-reset UMP state (debug helper). NEVER call in production paths —
     * Google's policy requires user-initiated re-consent.
     */
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
