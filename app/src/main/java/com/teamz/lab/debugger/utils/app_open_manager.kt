package com.teamz.lab.debugger.utils

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.teamz.lab.debugger.BuildConfig
import com.teamz.lab.debugger.utils.ImprovedAdManager
import com.teamz.lab.debugger.utils.AdRevenueOptimizer
import java.lang.ref.WeakReference

object AppOpenAdManager {
    private const val TAG = "AppOpenAdManager"
    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var isShowingAd = false
    private var pendingActivityRef: WeakReference<Activity>? = null // Use WeakReference to prevent memory leak

    // REVENUE-OPTIMIZED: Frequency capping and background time tracking
    // 30 minutes minimum between ads (maintains ~93% revenue while preventing spam)
    private const val MIN_AD_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
    // 5 minutes minimum background time (prevents ads on quick app switches)
    private const val MIN_BACKGROUND_TIME_MS = 5 * 60 * 1000L // 5 minutes

    // v3.1.11 W1 ad-pipeline fix — request-side throttle.
    // 2026-06-21 audit: app-open had ZERO request-side throttle (only the 30-min
    // SHOW cooldown). 5 separate trigger paths fired loadAd() → 5148 requests for
    // 18 displays (0.43% show rate). AdMob quality model penalized over-fire,
    // dragging match rate across the whole account.
    // MIN_LOAD_INTERVAL_MS: hard floor between loadAd() invocations (drops fan-out
    // within a single foreground burst). 60s ≪ MIN_AD_INTERVAL_MS so it never
    // limits show cadence — only request cadence.
    // MAX_LOADS_PER_SESSION: per-process cap (default 3 via RC); reset on cold-start
    // by resetSessionCounters(). Caps worst-case weekly request volume even if a
    // future code path adds new trigger sites.
    private const val MIN_LOAD_INTERVAL_MS = 60_000L
    @Volatile private var lastLoadAttemptMs: Long = 0L
    @Volatile private var loadAttemptsThisSession: Int = 0

    private var lastAdShownTime: Long = 0L
    private var appWentToBackgroundTime: Long = 0L

    /**
     * v3.1.11 W1 — call from Application.onCreate to reset the per-session load cap.
     * The object is a process singleton so counters survive across user sessions
     * without an explicit reset; this hook makes the per-session contract honest.
     */
    fun resetSessionCounters() {
        loadAttemptsThisSession = 0
        lastLoadAttemptMs = 0L
        android.util.Log.d(TAG, "resetSessionCounters() - per-session load cap reset")
    }

    fun loadAd(context: Context, activity: Activity? = null) {
        android.util.Log.d(TAG, "loadAd() called - isLoading: $isLoading, appOpenAd: ${appOpenAd != null}, attempts: $loadAttemptsThisSession")

        if (isLoading || appOpenAd != null) {
            android.util.Log.d(TAG, "loadAd() - Skipping: already loading or ad exists")
            return
        }

        // v3.1.11 W1 ad-pipeline fix — request-side throttle gate.
        // Both checks must pass before any AdMob request is issued.
        val now = System.currentTimeMillis()
        val sinceLast = now - lastLoadAttemptMs
        if (lastLoadAttemptMs > 0 && sinceLast < MIN_LOAD_INTERVAL_MS) {
            android.util.Log.d(TAG, "loadAd() - Throttled: ${sinceLast / 1000}s since last (min ${MIN_LOAD_INTERVAL_MS / 1000}s)")
            return
        }
        val sessionCap = RemoteConfigUtils.getAppOpenMaxLoadsPerSession()
        if (loadAttemptsThisSession >= sessionCap) {
            android.util.Log.d(TAG, "loadAd() - Session cap reached: $loadAttemptsThisSession/$sessionCap")
            return
        }
        lastLoadAttemptMs = now
        loadAttemptsThisSession += 1

        isLoading = true
        val adUnitId = AdConfig.getAppOpenAdUnitId()
        android.util.Log.d(TAG, "loadAd() - Using ad unit ID: $adUnitId (DEBUG=${BuildConfig.DEBUG})")
        
        val shouldShow = RemoteConfigUtils.shouldShowAppOpenAds()
        android.util.Log.d(TAG, "loadAd() - RemoteConfig shouldShowAppOpenAds: $shouldShow")
        
        if (shouldShow) {
            // Store activity reference if provided (to show ad when loaded) - use WeakReference
            if (activity != null) {
                pendingActivityRef = WeakReference(activity)
                android.util.Log.d(TAG, "loadAd() - Stored pending activity: ${activity.javaClass.simpleName}")
            }
            
            android.util.Log.d(TAG, "loadAd() - Starting ad load with adUnitId: $adUnitId")
            android.util.Log.d(TAG, "loadAd() - Calling ImprovedAdManager.loadAdWithRetry()...")
            
            // Use improved ad manager with retry logic
            ImprovedAdManager.loadAdWithRetry(
                context,
                adUnitId,
                onSuccess = { ad ->
                    android.util.Log.d(TAG, "loadAd() - ✅ Ad loaded successfully!")
                    appOpenAd = ad
                    isLoading = false
                    
                    // Set revenue tracking listener
                    ad.onPaidEventListener = AdRevenueOptimizer.createRevenueListener(
                        context,
                        adUnitId,
                        "app_open"
                    )
                    android.util.Log.d(TAG, "loadAd() - Revenue tracking listener set")
                    
                    // If we have a pending activity, show the ad automatically
                    val pendingActivity = pendingActivityRef?.get()
                    if (pendingActivity != null && !pendingActivity.isFinishing && !pendingActivity.isDestroyed) {
                        android.util.Log.d(TAG, "loadAd() - Auto-showing ad for pending activity: ${pendingActivity.javaClass.simpleName}")
                        // Clear pending activity first to avoid showing multiple times
                        pendingActivityRef = null
                        // Show ad on main thread
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            showAdIfAvailable(pendingActivity)
                        }
                    } else {
                        android.util.Log.d(TAG, "loadAd() - No valid pending activity, ad will be shown on next showAdIfAvailable() call")
                        pendingActivityRef = null
                    }
                },
                onFailure = { error ->
                    isLoading = false
                    pendingActivityRef = null // Clear pending activity on failure
                    
                    // Handle "Unable to obtain a JavascriptEngine" error gracefully
                    // This is a known issue with Google Mobile Ads SDK when WebView is not available
                    val errorMessage = error.message ?: ""
                    val isJavascriptEngineError = errorMessage.contains("JavascriptEngine", ignoreCase = true) || 
                                                  errorMessage.contains("javascript engine", ignoreCase = true) ||
                                                  errorMessage.contains("Unable to obtain", ignoreCase = true) ||
                                                  errorMessage.contains("unable to obtain", ignoreCase = true)
                    
                    if (isJavascriptEngineError) {
                        android.util.Log.w(TAG, "loadAd() - ⚠️ WebView/JavascriptEngine not available (code: ${error.code}) - this is expected on some devices. Skipping error logging.")
                        // Don't log this as an error - it's a known non-fatal issue
                        return@loadAdWithRetry
                    }
                    
                    // Expected/recoverable ad failures: log only, do not report to Crashlytics
                    val isExpectedAdFailure = errorMessage.contains("Network error", ignoreCase = true) ||
                        errorMessage.contains("No fill", ignoreCase = true) ||
                        errorMessage.contains("internal error", ignoreCase = true) ||
                        errorMessage.contains("no ad was returned", ignoreCase = true) ||
                        errorMessage.contains("timeout", ignoreCase = true) ||
                        errorMessage.contains("Unable to resolve host", ignoreCase = true) ||
                        errorMessage.contains("No address associated", ignoreCase = true) ||
                        errorMessage.contains("connecting to ad server", ignoreCase = true)
                    if (isExpectedAdFailure) {
                        android.util.Log.w(TAG, "loadAd() - ⚠️ Ad failed (expected): $errorMessage, code: ${error.code}")
                        return@loadAdWithRetry
                    }
                    
                    android.util.Log.e(TAG, "loadAd() - ❌ Ad failed to load: $errorMessage, code: ${error.code}")
                    handleError(Exception(error.message))
                }
            )
        } else {
            android.util.Log.w(TAG, "loadAd() - ⚠️ Skipping ad load: RemoteConfig disabled app open ads")
            isLoading = false
        }
    }

    fun showAdIfAvailable(activity: Activity, isColdStart: Boolean = false) {
        android.util.Log.d(TAG, "showAdIfAvailable() called - activity: ${activity.javaClass.simpleName}, isColdStart: $isColdStart, isShowingAd: $isShowingAd, appOpenAd: ${appOpenAd != null}, isLoading: $isLoading")
        
        // Check if ad is already showing
        if (isShowingAd) {
            android.util.Log.d(TAG, "showAdIfAvailable() - ⚠️ Skipping: Ad already showing")
            return
        }
        
        // REVENUE-OPTIMIZED: Frequency capping - 30 minutes minimum between ads
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastAdShownTime
        if (timeSinceLastAd < MIN_AD_INTERVAL_MS) {
            val remainingMinutes = (MIN_AD_INTERVAL_MS - timeSinceLastAd) / (60 * 1000)
            android.util.Log.d(TAG, "showAdIfAvailable() - ⚠️ Skipping: Ad shown ${timeSinceLastAd / 1000}s ago, need to wait ${remainingMinutes}m more (cooldown active)")
            // v3.1.11 W1 ad-pipeline fix — DELETED redundant preload here.
            // Old behaviour fired loadAd() during cooldown, wasting AdMob auction slots
            // because the cached ad would never be shown within the 30-min window.
            // Cold-start bootstrap + post-dismissal self-reload are the only legitimate
            // load triggers; everything else is dropped.
            return
        }

        // REVENUE-OPTIMIZED: Background time check - only show if app was in background 5+ minutes
        // This prevents ads on quick app switches but allows them on real returns
        if (!isColdStart) {
            val backgroundTime = currentTime - appWentToBackgroundTime
            if (backgroundTime < MIN_BACKGROUND_TIME_MS && appWentToBackgroundTime > 0) {
                val remainingMinutes = (MIN_BACKGROUND_TIME_MS - backgroundTime) / (60 * 1000)
                android.util.Log.d(TAG, "showAdIfAvailable() - ⚠️ Skipping: App returned too quickly (${backgroundTime / 1000}s), need ${remainingMinutes}m minimum background time")
                // v3.1.11 W1 ad-pipeline fix — DELETED redundant preload here for the
                // same reason as above. Same root cause: short background returns
                // shouldn't trigger fresh requests.
                return
            }
        }

        // v3.1.11 W1 ad-pipeline fix — DELETED the "no ad available -> preload with
        // activity" path. It was the third internal preload site contributing to the
        // 5148 weekly request volume. If no ad is cached, the user sees nothing this
        // session; the next session's cold-start will preload one fresh.
        if (appOpenAd == null) {
            android.util.Log.d(TAG, "showAdIfAvailable() - ⚠️ Skipping: No ad available to show (will load on next cold-start)")
            return
        }

        // Session-gated: sessions 1 and 2 are ad-free so new users see the product
        // before a launch interstitial. Driven by Remote Config app_open_ad_min_session
        // (default 3). Also blocks ads in suppressed geos (IR/BD/PK) inside the helper.
        val sessionCount = EngagementTracker.getSessionCount(activity)
        val shouldShow = RemoteConfigUtils.shouldShowAppOpenAdsForSession(sessionCount)
        android.util.Log.d(TAG, "showAdIfAvailable() - session=$sessionCount, shouldShow=$shouldShow")

        if (!shouldShow) {
            android.util.Log.w(TAG, "showAdIfAvailable() - ⚠️ Skipping: session-gated or RC disabled or country suppressed")
            return
        }

        android.util.Log.d(TAG, "showAdIfAvailable() - Setting up ad callbacks...")
        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                android.util.Log.d(TAG, "showAdIfAvailable() - ✅ Ad dismissed, preloading next ad...")
                appOpenAd = null
                isShowingAd = false
                lastAdShownTime = System.currentTimeMillis() // Track when ad was shown for cooldown
                pendingActivityRef = null // Clear any pending activity
                loadAd(activity) // Preload next ad (no activity, just preload)
                AnalyticsUtils.logEvent(AnalyticsEvent.AppOpenAdDismissed)
            }

            override fun onAdShowedFullScreenContent() {
                android.util.Log.d(TAG, "showAdIfAvailable() - ✅ Ad shown successfully!")
                isShowingAd = true
                lastAdShownTime = System.currentTimeMillis() // Track when ad was shown for cooldown
                AnalyticsUtils.logEvent(AnalyticsEvent.AppOpenAdShown)
            }

            override fun onAdClicked() {
                android.util.Log.d(TAG, "showAdIfAvailable() - ✅ Ad clicked")
                AnalyticsUtils.logEvent(AnalyticsEvent.AppOpenAdClicked)
                AdRevenueOptimizer.trackAdClick(activity, "app_open")
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                android.util.Log.e(TAG, "showAdIfAvailable() - ❌ Ad failed to show: ${p0.message}, code: ${p0.code}")
                appOpenAd = null
                isShowingAd = false
                pendingActivityRef = null // Clear pending activity on failure
                // v3.1.11 W1 ad-pipeline fix — DELETED preload-after-show-failure.
                // Was the fifth trigger site. If the show failed, the auction was
                // already consumed; firing another request immediately re-burns the
                // budget. Wait for next cold-start.
                
                // Handle expected/recoverable errors: log only, do not report to Crashlytics
                val errorMessage = p0.message ?: ""
                val isJavascriptEngine = errorMessage.contains("JavascriptEngine", ignoreCase = true) ||
                    errorMessage.contains("Unable to obtain", ignoreCase = true)
                val isExpectedAdFailure = errorMessage.contains("Network error", ignoreCase = true) ||
                    errorMessage.contains("No fill", ignoreCase = true) ||
                    errorMessage.contains("internal error", ignoreCase = true)
                if (isJavascriptEngine) {
                    android.util.Log.w(TAG, "showAdIfAvailable() - ⚠️ WebView/JavascriptEngine not available - this is expected on some devices. Skipping error logging.")
                    return
                }
                if (isExpectedAdFailure) {
                    android.util.Log.w(TAG, "showAdIfAvailable() - ⚠️ Ad failed to show (expected): $errorMessage")
                    return
                }
                
                handleError(Exception(p0.message))
            }
        }
        
        android.util.Log.d(TAG, "showAdIfAvailable() - Attempting to show ad...")
        try {
            appOpenAd?.show(activity)
            android.util.Log.d(TAG, "showAdIfAvailable() - ✅ show() called successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "showAdIfAvailable() - ❌ Exception showing ad: ${e.message}", e)
            
            // Handle expected/recoverable errors: log only, do not report to Crashlytics
            val errorMessage = e.message ?: ""
            val isJavascriptEngine = errorMessage.contains("JavascriptEngine", ignoreCase = true) ||
                errorMessage.contains("Unable to obtain", ignoreCase = true)
            val isExpectedAdFailure = errorMessage.contains("Network error", ignoreCase = true) ||
                errorMessage.contains("No fill", ignoreCase = true) ||
                errorMessage.contains("internal error", ignoreCase = true)
            if (isJavascriptEngine) {
                android.util.Log.w(TAG, "showAdIfAvailable() - ⚠️ WebView/JavascriptEngine not available - this is expected on some devices. Skipping error logging.")
                return
            }
            if (isExpectedAdFailure) {
                android.util.Log.w(TAG, "showAdIfAvailable() - ⚠️ Exception (expected): $errorMessage")
                return
            }
            
            handleError(e)
        }
    }
    
    /**
     * Track when app goes to background
     * Called by Application.onStop() to track background time
     */
    fun onAppWentToBackground() {
        appWentToBackgroundTime = System.currentTimeMillis()
        android.util.Log.d(TAG, "onAppWentToBackground() - Background time tracked: ${appWentToBackgroundTime}")
    }
    
    /**
     * Get current state for debugging
     * @suppress Unused - kept for debugging purposes
     */
    @Suppress("unused")
    fun getState(): String {
        val timeSinceLastAd = if (lastAdShownTime > 0) {
            (System.currentTimeMillis() - lastAdShownTime) / 1000
        } else {
            -1
        }
        val backgroundTime = if (appWentToBackgroundTime > 0) {
            (System.currentTimeMillis() - appWentToBackgroundTime) / 1000
        } else {
            -1
        }
        return "AppOpenAdManager State - appOpenAd: ${appOpenAd != null}, isLoading: $isLoading, isShowingAd: $isShowingAd, timeSinceLastAd: ${timeSinceLastAd}s, backgroundTime: ${backgroundTime}s"
    }
    
    /**
     * Clear/dispose of loaded ad (useful when user purchases premium)
     * This ensures no app open ads are shown after premium purchase
     */
    fun clearAd() {
        android.util.Log.d(TAG, "clearAd() - Clearing app open ad")
        appOpenAd = null
        isLoading = false
        isShowingAd = false
        pendingActivityRef = null
    }
}
