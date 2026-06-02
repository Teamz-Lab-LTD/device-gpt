package com.teamz.lab.debugger.utils

import com.teamz.lab.debugger.BuildConfig

/**
 * Ad Configuration Utility
 * 
 * Reads AdMob IDs from BuildConfig, which are set from local_config.properties during build.
 * Falls back to test IDs if local_config.properties doesn't exist (for open source users).
 * 
 * Usage:
 * ```kotlin
 * val adUnitId = AdConfig.getInterstitialAdUnitId()
 * ```
 */
object AdConfig {
    /**
     * Get App Open Ad Unit ID
     * Returns production ID from local_config.properties in release builds,
     * or test ID in debug builds or if not configured
     */
    fun getAppOpenAdUnitId(): String {
        return if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/9257395921" // Google test ID (corrected: was ...555/, Google's test publisher is ...544/)
        } else {
            BuildConfig.APP_OPEN_AD_UNIT_ID
        }
    }
    
    /**
     * Get Interstitial Ad Unit ID
     * Returns production ID from local_config.properties in release builds,
     * or test ID in debug builds or if not configured
     */
    fun getInterstitialAdUnitId(): String {
        return if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/1033173712" // Google test ID
        } else {
            BuildConfig.INTERSTITIAL_AD_UNIT_ID
        }
    }
    
    /**
     * Get Native Ad Unit ID
     * Returns production ID from local_config.properties in release builds,
     * or test ID in debug builds or if not configured
     */
    fun getNativeAdUnitId(): String {
        return if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/2247696110" // Google test ID
        } else {
            BuildConfig.NATIVE_AD_UNIT_ID
        }
    }
    
    /**
     * Get Rewarded Ad Unit ID
     * Returns production ID from local_config.properties in release builds,
     * or test ID in debug builds or if not configured
     */
    fun getRewardedAdUnitId(): String {
        return if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/5224354917" // Google test ID
        } else {
            BuildConfig.REWARDED_AD_UNIT_ID
        }
    }
    
    /**
     * Get OAuth Client ID
     * Returns from local_config.properties, or empty string if not configured
     */
    fun getOAuthClientId(): String = BuildConfig.OAUTH_CLIENT_ID
    
    /**
     * Get OneSignal App ID
     * Returns from local_config.properties, or empty string if not configured
     */
    fun getOneSignalAppId(): String = BuildConfig.ONESIGNAL_APP_ID
}

