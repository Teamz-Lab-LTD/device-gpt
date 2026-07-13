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

    // 2026-07-13 revenue fix. AdMob 30d: 18,124 app-open requests, 15,607 FILLED,
    // 59 shown (0.38%). Fill was never the problem — the app discarded 15,548 paid
    // ads. Two causes, both fixed here and in Application.onCreate:
    //
    //  (1) Application.onCreate called loadAd(applicationContext) unconditionally.
    //      Android runs onCreate for ANY component wake (WorkManager, widgets,
    //      the D1 worker), so most requests came from headless processes with no
    //      Activity — an ad that literally cannot be displayed. Removed there.
    //
    //  (2) On a genuine launch the ad arrived AFTER the show attempt: onCreate
    //      preloaded with NO activity, MainActivity.onStart called
    //      showAdIfAvailable() while the ad was still downloading (appOpenAd ==
    //      null -> return), and the "show it when it lands" path was unreachable
    //      because pendingActivityRef was never set. The cached ad then sat unshown
    //      until it expired — and with 7% D1 retention, "next session" never came.
    //      v3.1.11 (b971c61) DELETED the load-with-activity path to cut request
    //      volume; that killed the shows and did not even cut requests (still
    //      ~4.2k/wk) because cause (1) was the real source.
    //
    // The load-with-activity path is restored, but ONLY from a real Activity, and
    // the auto-show is bounded: if the ad lands after the user has already been
    // looking at content for AUTO_SHOW_WINDOW_MS, we cache it for the next launch
    // instead of slamming a fullscreen ad over their session.
    private const val AUTO_SHOW_WINDOW_MS = 6_000L
    @Volatile private var pendingShowRequestedAtMs: Long = 0L
    @Volatile private var pendingIsColdStart: Boolean = false

    /**
     * Pure decision for "the ad just finished loading — is it still OK to show it?".
     * Extracted so it is unit-testable without the AdMob SDK, an Activity, or a device.
     *
     * @param requestedAtMs when the user's launch asked for an ad (0 = nobody is waiting)
     * @param loadedAtMs    when the ad actually arrived
     */
    @JvmStatic
    @androidx.annotation.VisibleForTesting
    fun shouldAutoShowOnLoad(
        requestedAtMs: Long,
        loadedAtMs: Long,
        windowMs: Long = AUTO_SHOW_WINDOW_MS,
    ): Boolean {
        if (requestedAtMs <= 0L) return false          // no launch is waiting on this ad
        val waited = loadedAtMs - requestedAtMs
        if (waited < 0L) return false                  // clock went backwards
        return waited <= windowMs                      // too late = don't interrupt the user
    }

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

    fun loadAd(context: Context, activity: Activity? = null, isColdStart: Boolean = false) {
        android.util.Log.d(TAG, "loadAd() called - isLoading: $isLoading, appOpenAd: ${appOpenAd != null}, attempts: $loadAttemptsThisSession")

        // An Activity means a real launch is waiting on this ad — record when, so the
        // onSuccess handler can decide whether it arrived in time to be shown.
        if (activity != null) {
            pendingShowRequestedAtMs = System.currentTimeMillis()
            pendingIsColdStart = isColdStart
        }

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
                    
                    // A launch is waiting on this ad — show it, provided it arrived
                    // fast enough that the user hasn't already settled into the app.
                    val pendingActivity = pendingActivityRef?.get()
                    val inTime = shouldAutoShowOnLoad(pendingShowRequestedAtMs, System.currentTimeMillis())
                    val coldStart = pendingIsColdStart
                    if (pendingActivity != null && !pendingActivity.isFinishing && !pendingActivity.isDestroyed && inTime) {
                        android.util.Log.d(TAG, "loadAd() - Auto-showing ad for pending activity: ${pendingActivity.javaClass.simpleName}")
                        pendingActivityRef = null
                        pendingShowRequestedAtMs = 0L
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            showAdIfAvailable(pendingActivity, isColdStart = coldStart)
                        }
                    } else {
                        // Cached, not discarded: the next launch's showAdIfAvailable()
                        // finds it already in memory and shows it immediately.
                        android.util.Log.d(TAG, "loadAd() - Not auto-showing (inTime=$inTime, activity=${pendingActivity != null}); ad cached for next launch")
                        pendingActivityRef = null
                        pendingShowRequestedAtMs = 0L
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

        // 2026-07-13 revenue fix — RESTORED the "no ad cached -> load with activity"
        // path that v3.1.11 (b971c61) deleted.
        //
        // Deleting it assumed "the next session's cold-start will preload one fresh".
        // That assumption is false: D1 retention is 7%, so for 93% of users there IS
        // no next session. The ad was requested, filled, billed to AdMob's auction —
        // and never shown. 15,607 filled / 59 shown.
        //
        // Passing `activity` sets pendingActivityRef, so the onSuccess handler above
        // shows the ad the moment it lands (bounded by AUTO_SHOW_WINDOW_MS). The
        // request-side throttles (MIN_LOAD_INTERVAL_MS + per-session cap) still apply,
        // and this path is only reachable from a real Activity — never from a headless
        // background wake.
        if (appOpenAd == null) {
            android.util.Log.d(TAG, "showAdIfAvailable() - No ad cached; loading with activity so it can auto-show on arrival")
            loadAd(activity, activity, isColdStart)
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
