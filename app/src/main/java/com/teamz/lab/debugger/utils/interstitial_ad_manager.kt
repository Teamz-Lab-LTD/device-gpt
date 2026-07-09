package com.teamz.lab.debugger.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.teamz.lab.debugger.utils.AppLog
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd

/**
 * Unified Interstitial Ad Manager
 * 
 * This is the central manager for all interstitial (full-screen) ads in the app.
 * It consolidates functionality from both InterstitialAdManager and FullScreenVideoAdManager.
 * 
 * Features:
 * - Retry logic with exponential backoff (via ImprovedAdManager)
 * - Preloading for better UX
 * - Action callbacks (showAdBeforeAction pattern)
 * - Simple callbacks (showAdIfAvailable pattern)
 * - AdMob policy compliance
 * - Revenue tracking and analytics
 * - Proper lifecycle handling
 * 
 * Usage Examples:
 * 
 * 1. Show ad before executing an action:
 * ```kotlin
 * InterstitialAdManager.showAdBeforeAction(activity, "action_name") {
 *     // Your action code here
 * }
 * ```
 * 
 * 2. Show ad with simple callback:
 * ```kotlin
 * InterstitialAdManager.showAdIfAvailable(activity) {
 *     // Code to run after ad closes
 * }
 * ```
 * 
 * 3. Preload ad:
 * ```kotlin
 * InterstitialAdManager.preloadAd(context)
 * ```
 */
object InterstitialAdManager {
    private const val TAG = "PowerStateDebug"
    
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var pendingAction: (() -> Unit)? = null // Store pending action to survive recomposition
    
    // Global cooldown/throttling for full-screen ads (prevents ad spam)
    // Tracks the last time an ad was shown to enforce minimum interval
    private var lastAdShownTime: Long = 0L
    
    private val adUnitId: String = AdConfig.getInterstitialAdUnitId()
    
    /**
     * Get minimum interval between ads (in milliseconds)
     * Uses the single global RemoteConfig variable: interstitial_ad_interval
     * Default: 60 seconds if not configured
     */
    private fun getMinAdIntervalMs(): Long {
        return try {
            val intervalSeconds = RemoteConfigUtils.getInterstitialAdInterval()
            (intervalSeconds * 1000)
        } catch (e: Exception) {
            60_000L // Default: 60 seconds
        }
    }
    
    /**
     * Check if enough time has passed since last ad to show another one
     */
    private fun canShowAd(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastAdShownTime
        val minInterval = getMinAdIntervalMs()
        
        return timeSinceLastAd >= minInterval
    }
    
    /**
     * Update the last ad shown timestamp
     */
    private fun updateLastAdShownTime() {
        lastAdShownTime = System.currentTimeMillis()
    }

    /**
     * Preload ad in background for better performance
     * Call this when app starts or when user enters relevant screens
     */
    fun preloadAd(context: Context) {
        if (isLoading || interstitialAd != null) return
        
        if (RemoteConfigUtils.shouldShowInterstitialAds()) {
            loadAd(context)
        }
    }

    /**
     * Load ad with retry logic (uses ImprovedAdManager)
     */
    fun loadAd(context: Context, onLoaded: (() -> Unit)? = null) {
        if (isLoading) return
        isLoading = true
        
        if (RemoteConfigUtils.shouldShowInterstitialAds()) {
            ImprovedAdManager.loadInterstitialAdWithRetry(
                context,
                adUnitId,
                onSuccess = { ad ->
                    interstitialAd = ad
                    isLoading = false
                    
                    // Set revenue tracking listener
                    ad.setOnPaidEventListener(
                        AdRevenueOptimizer.createRevenueListener(
                            context,
                            adUnitId,
                            "interstitial"
                        )
                    )
                    
                    onLoaded?.invoke()
                },
                onFailure = { error ->
                    interstitialAd = null
                    isLoading = false
                    onLoaded?.invoke()
                }
            )
        } else {
            isLoading = false
            onLoaded?.invoke()
        }
    }

    /**
     * Show ad before executing an action (FullScreenVideoAdManager pattern)
     * 
     * Global throttling: Enforces minimum interval between ads to prevent ad spam
     * and improve user experience while maintaining revenue (AdMob policy compliance).
     * 
     * Policy Compliance:
     * - Only shows ad if user explicitly initiated the action
     * - Ad is optional - action proceeds even if ad fails to load/show
     * - Proper error handling - never blocks user action
     * - Preloads next ad for better UX
     * - Global cooldown prevents excessive ad frequency (AdMob policy compliance)
     * 
     * @param activity The activity context
     * @param actionName Optional name for analytics tracking
     * @param action The action to execute after ad is dismissed (or if ad fails)
     */
    fun showAdBeforeAction(
        activity: Activity,
        actionName: String = "unknown",
        action: () -> Unit
    ) {
        AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - actionName: $actionName, adLoaded: ${interstitialAd != null}, isLoading: $isLoading")
        AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - Activity state: isFinishing=${activity.isFinishing}, isDestroyed=${activity.isDestroyed}")

        // v3.2.0 ad-grace: no interstitials during the first N sessions
        // (RC ads_grace_sessions, default 2). Bounce-reduction play — 26% of
        // installs bounce before any feature; a fullscreen ad in session 1-2
        // is the cheapest churn source to remove.
        val sessionCount = EngagementTracker.getSessionCount(activity)
        if (sessionCount in 1..RemoteConfigUtils.getAdsGraceSessions()) {
            AppLog.d(TAG, "Ad-grace window (session $sessionCount) — skipping interstitial, executing action: $actionName")
            Handler(Looper.getMainLooper()).post {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    try { action() } catch (e: Exception) {
                        ErrorHandler.handleError(e, context = "InterstitialAdManager.grace-$actionName")
                    }
                }
            }
            return
        }

        // REVENUE OPTIMIZATION: Always try to load ad if not loaded (even if throttled)
        // This ensures ads are ready when throttling expires
        if (interstitialAd == null && !isLoading && RemoteConfigUtils.shouldShowInterstitialAds()) {
            loadAd(activity)
        }

        // Check global cooldown/throttling - prevent showing ads too frequently (AdMob policy compliance)
        if (!canShowAd()) {
            val timeSinceLastAd = System.currentTimeMillis() - lastAdShownTime
            val minInterval = getMinAdIntervalMs()
            val remainingSeconds = ((minInterval - timeSinceLastAd) / 1000).toInt()
            AppLog.d(TAG, "Ad throttled: ${remainingSeconds}s remaining until next ad can be shown for action: $actionName")
            // Silently skip ad (better UX than showing too frequently)
            // But ad is loading in background for next opportunity
            // Policy: Always proceed with action, ad is optional
            Handler(Looper.getMainLooper()).post {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    try {
                        AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - EXECUTING ACTION (throttled): $actionName")
                        action()
                        AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - Action executed (throttled): $actionName")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "InterstitialAdManager showAdBeforeAction - Error executing action (throttled): ${e.message}", e)
                        ErrorHandler.handleError(
                            e,
                            context = "InterstitialAdManager.showAdBeforeAction-$actionName"
                        )
                    }
                } else {
                    AppLog.w(TAG, "InterstitialAdManager showAdBeforeAction - Activity finishing/destroyed, skipping action (throttled): $actionName")
                }
            }
            return
        }

        // Check if ads are enabled (RemoteConfig kill switch)
        if (!RemoteConfigUtils.shouldShowInterstitialAds()) {
            AppLog.d(TAG, "Ads disabled via RemoteConfig")
            // Policy: Always proceed with action, ad is optional
            Handler(Looper.getMainLooper()).post {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    try {
                        AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - EXECUTING ACTION (ads disabled): $actionName")
                        action()
                        AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - Action executed (ads disabled): $actionName")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "InterstitialAdManager showAdBeforeAction - Error executing action (ads disabled): ${e.message}", e)
                        ErrorHandler.handleError(
                            e,
                            context = "InterstitialAdManager.showAdBeforeAction-$actionName"
                        )
                    }
                } else {
                    AppLog.w(TAG, "InterstitialAdManager showAdBeforeAction - Activity finishing/destroyed, skipping action (ads disabled): $actionName")
                }
            }
            return
        }

        // Policy: Always proceed with action, ad is optional
        if (interstitialAd != null) {
            val ad = interstitialAd // Store reference to avoid null issues
            // Store action to survive activity lifecycle changes
            pendingAction = action
            AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - Stored pending action for: $actionName")
            
            ad?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    AppLog.d(TAG, "InterstitialAdManager onAdDismissedFullScreenContent - Ad dismissed for: $actionName")
                    AppLog.d(TAG, "InterstitialAdManager onAdDismissedFullScreenContent - Activity state: isFinishing=${activity.isFinishing}, isDestroyed=${activity.isDestroyed}")
                    
                    interstitialAd = null
                    val actionToExecute = pendingAction
                    pendingAction = null // Clear immediately to prevent double execution
                    AppLog.d(TAG, "InterstitialAdManager onAdDismissedFullScreenContent - Pending action: ${actionToExecute != null}")
                    
                    // Preload next ad immediately (on background)
                    loadAd(activity)
                    
                    // Execute action immediately on main thread with a small delay
                    // This delay allows the activity to fully resume and prevents
                    // interference with lifecycle observers that might trigger on ON_RESUME
                    Handler(Looper.getMainLooper()).postDelayed({
                        AppLog.d(TAG, "InterstitialAdManager onAdDismissedFullScreenContent - Executing action after delay: $actionName")
                        AppLog.d(TAG, "InterstitialAdManager onAdDismissedFullScreenContent - Activity state after delay: isFinishing=${activity.isFinishing}, isDestroyed=${activity.isDestroyed}")
                        
                        if (actionToExecute != null && !activity.isFinishing && !activity.isDestroyed) {
                            try {
                                AppLog.d(TAG, "InterstitialAdManager onAdDismissedFullScreenContent - EXECUTING ACTION: $actionName")
                                // Execute action - only once
                                actionToExecute()
                                AppLog.d(TAG, "InterstitialAdManager onAdDismissedFullScreenContent - Action executed successfully: $actionName")
                                
                                // Log analytics
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.AppFullScreenAdDismissed,
                                    mapOf("action_name" to actionName, "ad_type" to "interstitial")
                                )
                            } catch (e: Exception) {
                                AppLog.e(TAG, "InterstitialAdManager onAdDismissedFullScreenContent - Error executing action: ${e.message}", e)
                                ErrorHandler.handleError(
                                    e,
                                    context = "InterstitialAdManager.onAdDismissedFullScreenContent-$actionName"
                                )
                            }
                        } else {
                            AppLog.w(TAG, "InterstitialAdManager onAdDismissedFullScreenContent - Skipping action (activity finishing/destroyed or no action): $actionName")
                        }
                    }, 200) // Small delay to let activity resume complete and avoid lifecycle conflicts
                }

                override fun onAdClicked() {
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.AppFullScreenAdClicked,
                        mapOf("action_name" to actionName)
                    )
                    AdRevenueOptimizer.trackAdClick(activity, "interstitial")
                }

                override fun onAdShowedFullScreenContent() {
                    updateLastAdShownTime() // Update cooldown timestamp when ad is shown (AdMob policy compliance)
                    AppLog.d(TAG, "InterstitialAdManager onAdShowedFullScreenContent - Ad shown for: $actionName")
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.AppFullScreenAdShown,
                        mapOf("action_name" to actionName)
                    )
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    val actionToExecute = pendingAction
                    pendingAction = null
                    
                    // Preload next ad
                    loadAd(activity)
                    // Policy: Always proceed with action even if ad fails
                    // Execute on main thread
                    Handler(Looper.getMainLooper()).post {
                        if (actionToExecute != null && !activity.isFinishing && !activity.isDestroyed) {
                            try {
                                actionToExecute()
                            } catch (e: Exception) {
                                AppLog.e(
                                    "InterstitialAdManager",
                                    "Error executing action after ad failure: ${e.message}",
                                    e
                                )
                                ErrorHandler.handleError(
                                    e,
                                    context = "InterstitialAdManager.onAdFailedToShowFullScreenContent-$actionName"
                                )
                            }
                        }
                    }
                    android.util.Log.e(
                        "InterstitialAdManager",
                        "Failed to show ad: ${adError.message}"
                    )
                }
            }

            // Show ad
            AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - Showing ad for: $actionName")
            ad?.show(activity)
        } else {
            // No ad available - proceed with action immediately
            AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - No ad available, executing action immediately: $actionName")
            // This ensures user action is never blocked
            pendingAction = null // Clear any pending action
            Handler(Looper.getMainLooper()).post {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    try {
                        AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - EXECUTING ACTION (no ad): $actionName")
                        action()
                        AppLog.d(TAG, "InterstitialAdManager showAdBeforeAction - Action executed (no ad): $actionName")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "InterstitialAdManager showAdBeforeAction - Error executing action (no ad): ${e.message}", e)
                        ErrorHandler.handleError(
                            e,
                            context = "InterstitialAdManager.showAdBeforeAction-$actionName"
                        )
                    }
                } else {
                    AppLog.w(TAG, "InterstitialAdManager showAdBeforeAction - Activity finishing/destroyed, skipping action: $actionName")
                }
            }
            
            // Try to load ad for next time
            if (!isLoading) {
                loadAd(activity)
            }
        }
    }

    /**
     * Show ad if available with simple callback (original InterstitialAdManager pattern)
     * 
     * Global throttling: Enforces minimum interval between ads to prevent ad spam
     * and improve user experience while maintaining revenue.
     * 
     * Policy Compliance:
     * - Only shows ad if user explicitly initiated the action
     * - Ad is optional - callback proceeds even if ad fails to load/show
     * - Proper error handling - never blocks user action
     * - Preloads next ad for better UX
     * - Global cooldown prevents excessive ad frequency
     * 
     * @param activity The activity context
     * @param onAdClosed Callback to execute after ad is dismissed or if no ad is available
     */
    fun showAdIfAvailable(activity: Activity, onAdClosed: () -> Unit) {
        // REVENUE OPTIMIZATION: Always try to load ad if not loaded (even if throttled)
        // This ensures ads are ready when throttling expires
        if (interstitialAd == null && !isLoading && RemoteConfigUtils.shouldShowInterstitialAds()) {
            loadAd(activity)
        }
        
        // Check global cooldown/throttling - prevent showing ads too frequently
        if (!canShowAd()) {
            val timeSinceLastAd = System.currentTimeMillis() - lastAdShownTime
            val minInterval = getMinAdIntervalMs()
            val remainingSeconds = ((minInterval - timeSinceLastAd) / 1000).toInt()
            AppLog.d(TAG, "Ad throttled: ${remainingSeconds}s remaining until next ad can be shown")
            // Silently skip ad (better UX than showing too frequently)
            // But ad is loading in background for next opportunity
            onAdClosed()
            return
        }
        
        // Check if ads are enabled (RemoteConfig kill switch)
        if (!RemoteConfigUtils.shouldShowInterstitialAds()) {
            AppLog.d(TAG, "Ads disabled via RemoteConfig")
            onAdClosed()
            return
        }
        
        // Show ad if loaded and ready
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    updateLastAdShownTime() // Update cooldown timestamp
                    interstitialAd = null
                    loadAd(activity)
                    onAdClosed()
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.AppFullScreenAdDismissed,
                        mapOf("ad_type" to "interstitial")
                    )
                }

                override fun onAdClicked() {
                    AnalyticsUtils.logEvent(AnalyticsEvent.AppFullScreenAdClicked)
                    AdRevenueOptimizer.trackAdClick(activity, "interstitial")
                }

                override fun onAdShowedFullScreenContent() {
                    updateLastAdShownTime() // Update cooldown timestamp when ad is shown
                    AnalyticsUtils.logEvent(AnalyticsEvent.AppFullScreenAdShown)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    // Don't update timestamp on failure - allow retry sooner
                    interstitialAd = null
                    loadAd(activity)
                    onAdClosed()
                    android.util.Log.e(
                        "InterstitialAdManager",
                        "Failed to show ad: ${adError.message}"
                    )
                }
            }
                interstitialAd?.show(activity)
        } else {
            // No ad available yet - but we're loading it in background for next opportunity
            // Proceed with callback immediately (don't block user)
            // Next call will have ad ready (revenue optimized)
            AppLog.d(TAG, "Ad not loaded yet, but loading in background for next opportunity")
            onAdClosed()
        }
    }

    /**
     * Check if ad is loaded and ready to show
     */
    fun isAdLoaded(): Boolean = interstitialAd != null

    /**
     * Check if ad is currently loading
     */
    fun isLoading(): Boolean = isLoading

    /**
     * Force reload ad (useful for testing or after errors)
     */
    fun reloadAd(context: Context) {
        interstitialAd = null
        isLoading = false
        loadAd(context)
    }
    
    /**
     * Clear/dispose of loaded ad (useful when user purchases premium)
     * This ensures no ads are shown after premium purchase
     */
    fun clearAd() {
        AppLog.d(TAG, "clearAd() - Clearing interstitial ad")
        interstitialAd = null
        isLoading = false
        pendingAction = null
    }
}
