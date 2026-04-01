package com.teamz.lab.debugger.utils

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.teamz.lab.debugger.utils.AdRevenueOptimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

object RewardedAdManager {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private val adUnitId: String = AdConfig.getRewardedAdUnitId()
    private var consecutiveLoadFailures = 0
    private var nextEligibleLoadTimeMs = 0L
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val BASE_RETRY_DELAY_MS = 3000L
    private const val MAX_RETRY_DELAY_MS = 30000L

    fun loadAd(context: Context, onLoaded: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (isLoading || rewardedAd != null || now < nextEligibleLoadTimeMs) return
        isLoading = true

        if (RemoteConfigUtils.shouldShowRewardedAds()) {
            val request = AdRequest.Builder().build()
            
            RewardedAd.load(
                context,
                adUnitId,
                request,
                object : RewardedAdLoadCallback() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        rewardedAd = null
                        isLoading = false
                        consecutiveLoadFailures++
                        android.util.Log.e("RewardedAdManager", "Failed to load rewarded ad: ${adError.message}")
                        
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.AdFailed,
                            mapOf(
                                "error_code" to adError.code,
                                "error_message" to (adError.message ?: "unknown"),
                                "ad_type" to "rewarded"
                            )
                        )
                        
                        // Retry with exponential backoff for retryable errors
                        val nonRetryableErrors = listOf(0, 2, 8) // INVALID_REQUEST, INVALID_AD_SIZE, INVALID_APP_ID
                        if (adError.code !in nonRetryableErrors && consecutiveLoadFailures <= MAX_RETRY_ATTEMPTS) {
                            val retryDelayMs = minOf(
                                BASE_RETRY_DELAY_MS * (1 shl (consecutiveLoadFailures - 1)),
                                MAX_RETRY_DELAY_MS
                            )
                            nextEligibleLoadTimeMs = System.currentTimeMillis() + retryDelayMs
                            CoroutineScope(Dispatchers.IO).launch {
                                delay(retryDelayMs)
                                if (!((context as? Activity)?.isDestroyed ?: false)) {
                                    loadAd(context, onLoaded)
                                }
                            }
                        }
                        
                        onLoaded?.invoke()
                    }

                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isLoading = false
                        consecutiveLoadFailures = 0
                        nextEligibleLoadTimeMs = 0L
                        
                        // Set revenue tracking listener
                        ad.setOnPaidEventListener(
                            AdRevenueOptimizer.createRevenueListener(
                                context,
                                adUnitId,
                                "rewarded"
                            )
                        )
                        
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.AdLoaded,
                            mapOf("ad_type" to "rewarded")
                        )
                        onLoaded?.invoke()
                    }
                }
            )
        } else {
            isLoading = false
            onLoaded?.invoke()
        }
    }

    fun showAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdDismissed: () -> Unit = {},
        onAdFailed: () -> Unit = {}
    ) {
        // Preload next ad immediately for better revenue
        if (rewardedAd == null && !isLoading) {
            loadAd(activity)
        }

        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadAd(activity) // Preload next ad
                    onAdDismissed()
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.AppFullScreenAdDismissed,
                        mapOf("ad_type" to "rewarded")
                    )
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    loadAd(activity)
                    onAdFailed()
                    android.util.Log.e("RewardedAdManager", "Failed to show rewarded ad: ${adError.message}")
                }

                override fun onAdShowedFullScreenContent() {
                    AnalyticsUtils.logEvent(AnalyticsEvent.AppFullScreenAdShown)
                }

                override fun onAdClicked() {
                    AnalyticsUtils.logEvent(AnalyticsEvent.AppFullScreenAdClicked)
                    AdRevenueOptimizer.trackAdClick(activity, "rewarded")
                }
            }

            if (RemoteConfigUtils.shouldShowRewardedAds()) {
                ad.show(
                    activity,
                    OnUserEarnedRewardListener { rewardItem ->
                        // User earned reward
                        val rewardAmount = rewardItem.amount
                        val rewardType = rewardItem.type
                        android.util.Log.d("RewardedAdManager", "User earned reward: $rewardAmount $rewardType")
                        
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.AdRewardEarned,
                            mapOf(
                                "reward_amount" to rewardAmount,
                                "reward_type" to rewardType,
                                "ad_type" to "rewarded"
                            )
                        )
                        
                        onRewardEarned()
                    }
                )
            } else {
                onAdFailed()
            }
        } else {
            // Ad not loaded, try to load and show later
            loadAd(activity) {
                if (rewardedAd != null) {
                    showAd(activity, onRewardEarned, onAdDismissed, onAdFailed)
                } else {
                    onAdFailed()
                }
            }
        }
    }

    fun isAdLoaded(): Boolean = rewardedAd != null
    fun isLoading(): Boolean = isLoading
}

