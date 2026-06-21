package com.teamz.lab.debugger.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.teamz.lab.debugger.BuildConfig
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Improved Ad Manager with retry logic and exponential backoff
 * Reduces ad_failed rate significantly
 */
object ImprovedAdManager {
    // v3.1.11 W1 ad-pipeline fix — was hardcoded 1, now RC-tunable.
    // 2026-06-21 audit: hardcoded retry was hidden 2x request multiplier on transient
    // failures. New default: 0 (no retry). Tunable via RC app_open_ad_max_retries.
    private val MAX_RETRIES: Int
        get() = RemoteConfigUtils.getAppOpenAdMaxRetries()
    private const val INITIAL_RETRY_DELAY_MS = 2000L // 2 seconds
    private const val MAX_RETRY_DELAY_MS = 30000L // 30 seconds
    
    private val retryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * Load ad with retry logic and exponential backoff
     * Note: AppOpenAd.load() must be called on the main thread
     */
    fun loadAdWithRetry(
        context: Context,
        adUnitId: String,
        onSuccess: (AppOpenAd) -> Unit,
        onFailure: (LoadAdError) -> Unit,
        retryCount: Int = 0
    ) {
        Log.d("ImprovedAdManager", "loadAdWithRetry() - ENTERED - retryCount: $retryCount, adUnitId: $adUnitId")
        
        // Allow initial attempt plus MAX_RETRIES follow-up attempts.
        if (retryCount > MAX_RETRIES) {
            Log.w("ImprovedAdManager", "❌ Retry budget exhausted for ad: $adUnitId")
            return
        }
        
        Log.d("ImprovedAdManager", "loadAdWithRetry() - Launching coroutine on main thread...")
        // Ensure AppOpenAd.load() is called on the main thread
        // This is required by Google Mobile Ads SDK
        mainScope.launch {
            Log.d("ImprovedAdManager", "loadAdWithRetry() - ✅ Inside coroutine, creating AdRequest...")
            val request = AdRequest.Builder().build()
            Log.d("ImprovedAdManager", "loadAdWithRetry() - ✅ AdRequest created, calling AppOpenAd.load()...")
            
            try {
                AppOpenAd.load(
                    context,
                    adUnitId,
                    request,
                    object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        Log.d("ImprovedAdManager", "✅ Ad loaded successfully: $adUnitId (attempt ${retryCount + 1})")
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.AdLoaded,
                            mapOf("retry_count" to retryCount, "ad_type" to "app_open")
                        )
                        onSuccess(ad)
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        val errorCode = error.code
                        val errorMessage = error.message ?: ""
                        
                        // Check for "Unable to obtain a JavascriptEngine" error first
                        // This is a known issue when WebView is not available on the device
                        val errorMessageLower = errorMessage.lowercase()
                        val isJavascriptEngineError = errorMessageLower.contains("javascriptengine") || 
                                                      errorMessageLower.contains("javascript engine") ||
                                                      errorMessageLower.contains("unable to obtain")
                        
                        if (isJavascriptEngineError) {
                            Log.w("ImprovedAdManager", "⚠️ WebView/JavascriptEngine not available (code: $errorCode) - this is expected on some devices. Skipping retry and analytics.")
                            // Don't log analytics or retry for this known non-fatal issue
                            onFailure(error)
                            return
                        }
                        
                        Log.w("ImprovedAdManager", "❌ Ad failed to load (attempt ${retryCount + 1}/$MAX_RETRIES): $errorMessage (code: $errorCode)")
                        
                        // Track failure with context
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.AdFailed,
                            mapOf(
                                "error_code" to errorCode,
                                "error_message" to errorMessage,
                                "retry_count" to retryCount,
                                "ad_type" to "app_open"
                            )
                        )
                        
                        // Don't retry for certain error codes (0=INVALID_REQUEST, 2=INVALID_AD_SIZE, 8=INVALID_APP_ID)
                        val nonRetryableErrors = listOf(0, 2, 8)
                        
                        if (errorCode in nonRetryableErrors) {
                            Log.e("ImprovedAdManager", "Non-retryable error: $errorCode")
                            onFailure(error)
                            return
                        }
                        
                        if (retryCount >= MAX_RETRIES) {
                            Log.w("ImprovedAdManager", "❌ Max retries reached for ad: $adUnitId, forwarding terminal failure")
                            onFailure(error)
                            return
                        }

                        // Calculate exponential backoff delay
                        val delayMs = minOf(
                            INITIAL_RETRY_DELAY_MS * (1 shl retryCount),
                            MAX_RETRY_DELAY_MS
                        )
                        
                        // Retry with exponential backoff (delay on IO, then load on main)
                        retryScope.launch {
                            delay(delayMs)
                            loadAdWithRetry(context, adUnitId, onSuccess, onFailure, retryCount + 1)
                        }
                    }
                }
                )
                Log.d("ImprovedAdManager", "loadAdWithRetry() - ✅ AppOpenAd.load() called successfully")
            } catch (e: Exception) {
                Log.e("ImprovedAdManager", "loadAdWithRetry() - ❌ Exception calling AppOpenAd.load(): ${e.message}", e)
                // Exception occurred - the error will be handled by the callback if possible
                // If not, we'll retry on next attempt
            }
        }
        Log.d("ImprovedAdManager", "loadAdWithRetry() - Coroutine launched, returning...")
    }
    
    /**
     * Load interstitial ad with retry logic
     * Note: InterstitialAd.load() must be called on the main thread
     */
    fun loadInterstitialAdWithRetry(
        context: Context,
        adUnitId: String,
        onSuccess: (InterstitialAd) -> Unit,
        onFailure: (LoadAdError) -> Unit,
        retryCount: Int = 0
    ) {
        if (retryCount > MAX_RETRIES) {
            Log.w("ImprovedAdManager", "Retry budget exhausted for interstitial ad: $adUnitId")
            return
        }
        
        // Ensure InterstitialAd.load() is called on the main thread
        // This is required by Google Mobile Ads SDK
        mainScope.launch {
            val request = AdRequest.Builder().build()
            
            InterstitialAd.load(
                context,
                adUnitId,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        Log.d("ImprovedAdManager", "Interstitial ad loaded successfully: $adUnitId")
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.AdLoaded,
                            mapOf("retry_count" to retryCount, "ad_type" to "interstitial")
                        )
                        onSuccess(ad)
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        val errorCode = error.code
                        val errorMessage = error.message ?: ""
                        
                        // Check for "Unable to obtain a JavascriptEngine" error first
                        // This is a known issue when WebView is not available on the device
                        val errorMessageLower = errorMessage.lowercase()
                        val isJavascriptEngineError = errorMessageLower.contains("javascriptengine") || 
                                                      errorMessageLower.contains("javascript engine") ||
                                                      errorMessageLower.contains("unable to obtain")
                        
                        if (isJavascriptEngineError) {
                            Log.w("ImprovedAdManager", "⚠️ WebView/JavascriptEngine not available (code: $errorCode) - this is expected on some devices. Skipping retry and analytics.")
                            // Don't log analytics or retry for this known non-fatal issue
                            onFailure(error)
                            return
                        }
                        
                        Log.w("ImprovedAdManager", "Interstitial ad failed (attempt ${retryCount + 1}/$MAX_RETRIES): $errorMessage (code: $errorCode)")
                        
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.AdFailed,
                            mapOf(
                                "error_code" to errorCode,
                                "error_message" to errorMessage,
                                "retry_count" to retryCount,
                                "ad_type" to "interstitial"
                            )
                        )
                        
                        // Don't retry for certain error codes (0=INVALID_REQUEST, 2=INVALID_AD_SIZE, 8=INVALID_APP_ID)
                        val nonRetryableErrors = listOf(0, 2, 8)
                        
                        if (errorCode in nonRetryableErrors) {
                            onFailure(error)
                            return
                        }
                        
                        if (retryCount >= MAX_RETRIES) {
                            Log.w("ImprovedAdManager", "Max retries reached for interstitial ad: $adUnitId, forwarding terminal failure")
                            onFailure(error)
                            return
                        }

                        // Exponential backoff
                        val delayMs = minOf(
                            INITIAL_RETRY_DELAY_MS * (1 shl retryCount),
                            MAX_RETRY_DELAY_MS
                        )
                        
                        // Retry with exponential backoff (delay on IO, then load on main)
                        retryScope.launch {
                            delay(delayMs)
                            loadInterstitialAdWithRetry(context, adUnitId, onSuccess, onFailure, retryCount + 1)
                        }
                    }
                }
            )
        }
    }
    
    /**
     * Cleanup coroutine scopes
     */
    fun cleanup() {
        retryScope.cancel()
        mainScope.cancel()
    }
}

