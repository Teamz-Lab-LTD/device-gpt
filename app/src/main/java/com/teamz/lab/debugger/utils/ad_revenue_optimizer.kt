package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.OnPaidEventListener
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Ad Revenue Optimizer
 * Tracks ad performance and optimizes revenue through:
 * - Revenue tracking per ad unit
 * - eCPM monitoring
 * - Ad preloading optimization
 * - Smart ad timing
 * - Performance analytics
 */
object AdRevenueOptimizer {
    private const val PREFS_NAME = "ad_revenue_prefs"
    private const val KEY_TOTAL_REVENUE = "total_revenue_micros"
    private const val KEY_AD_IMPRESSIONS = "ad_impressions"
    private const val KEY_AD_CLICKS = "ad_clicks"
    private const val KEY_LAST_AD_TIME = "last_ad_time"
    private const val KEY_DAILY_AD_COUNT = "daily_ad_count"
    private const val KEY_LAST_DATE = "last_date"
    
    private val revenueScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    data class AdRevenueData(
        val adUnitId: String,
        val adType: String, // "app_open", "interstitial", "native"
        val revenueMicros: Long,
        val currency: String,
        val impressionTime: Long = System.currentTimeMillis()
    )
    
    /**
     * Track ad revenue when ad is shown
     */
    fun trackAdRevenue(
        context: Context,
        adUnitId: String,
        adType: String,
        adValue: AdValue
    ) {
        revenueScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
                // Update total revenue
                val currentRevenue = prefs.getLong(KEY_TOTAL_REVENUE, 0L)
                prefs.edit {
                    putLong(KEY_TOTAL_REVENUE, currentRevenue + adValue.valueMicros)
                }
                
                // Track impressions
                val impressions = prefs.getInt(KEY_AD_IMPRESSIONS, 0)
                prefs.edit {
                    putInt(KEY_AD_IMPRESSIONS, impressions + 1)
                }
                
                // Track daily ad count
                val lastDate = prefs.getString(KEY_LAST_DATE, "")
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val dailyCount = if (lastDate == currentDate) {
                    prefs.getInt(KEY_DAILY_AD_COUNT, 0)
                } else {
                    prefs.edit { putString(KEY_LAST_DATE, currentDate) }
                    0
                }
                prefs.edit {
                    putInt(KEY_DAILY_AD_COUNT, dailyCount + 1)
                }
                
                // Log revenue data
                val revenueData = AdRevenueData(
                    adUnitId = adUnitId,
                    adType = adType,
                    revenueMicros = adValue.valueMicros,
                    currency = adValue.currencyCode
                )
                
                // Track in analytics (custom event for internal tracking)
                val eCPM = calculateECPM(context)
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.AdPaid,
                    mapOf(
                        "ad_type" to adType,
                        "ad_unit_id" to adUnitId,
                        "revenue_micros" to adValue.valueMicros,
                        "currency" to adValue.currencyCode,
                        "ecpm" to eCPM,
                        "daily_count" to (dailyCount + 1)
                    )
                )

                // Log GA4 standard ad_impression event so revenue shows in GA4 reports
                AnalyticsUtils.logAdRevenue(
                    adType = adType,
                    adUnitId = adUnitId,
                    revenueMicros = adValue.valueMicros,
                    currencyCode = adValue.currencyCode
                )

                Log.d("AdRevenueOptimizer", "Ad revenue tracked: ${adValue.valueMicros} micros, eCPM: $eCPM")
                
            } catch (e: Exception) {
                Log.e("AdRevenueOptimizer", "Error tracking ad revenue", e)
            }
        }
    }
    
    /**
     * Track ad click
     */
    fun trackAdClick(context: Context, adType: String) {
        revenueScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val clicks = prefs.getInt(KEY_AD_CLICKS, 0)
                prefs.edit {
                    putInt(KEY_AD_CLICKS, clicks + 1)
                }
                
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.AdClicked,
                    mapOf("ad_type" to adType)
                )
            } catch (e: Exception) {
                Log.e("AdRevenueOptimizer", "Error tracking ad click", e)
            }
        }
    }
    
    /**
     * Calculate eCPM (effective cost per mille)
     */
    fun calculateECPM(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val totalRevenue = prefs.getLong(KEY_TOTAL_REVENUE, 0L)
        val impressions = prefs.getInt(KEY_AD_IMPRESSIONS, 0)
        
        return if (impressions > 0) {
            (totalRevenue.toDouble() / impressions) * 1000.0 // Convert to per 1000 impressions
        } else {
            0.0
        }
    }
    
    /**
     * Get click-through rate (CTR)
     */
    fun getCTR(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val clicks = prefs.getInt(KEY_AD_CLICKS, 0)
        val impressions = prefs.getInt(KEY_AD_IMPRESSIONS, 0)
        
        return if (impressions > 0) {
            (clicks.toDouble() / impressions) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * Get total revenue in dollars
     */
    fun getTotalRevenue(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val totalRevenueMicros = prefs.getLong(KEY_TOTAL_REVENUE, 0L)
        return totalRevenueMicros / 1_000_000.0
    }
    
    /**
     * Check if we should show ad based on timing optimization
     * Prevents ad fatigue by spacing ads appropriately
     */
    fun shouldShowAdBasedOnTiming(context: Context, minIntervalSeconds: Int = 30): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAdTime = prefs.getLong(KEY_LAST_AD_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = (currentTime - lastAdTime) / 1000
        
        if (timeSinceLastAd >= minIntervalSeconds) {
            prefs.edit {
                putLong(KEY_LAST_AD_TIME, currentTime)
            }
            return true
        }
        return false
    }
    
    /**
     * Get daily ad count
     */
    fun getDailyAdCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        return if (lastDate == currentDate) {
            prefs.getInt(KEY_DAILY_AD_COUNT, 0)
        } else {
            0
        }
    }
    
    /**
     * Create OnPaidEventListener for tracking revenue
     */
    fun createRevenueListener(
        context: Context,
        adUnitId: String,
        adType: String
    ): OnPaidEventListener {
        return OnPaidEventListener { adValue ->
            trackAdRevenue(context, adUnitId, adType, adValue)
        }
    }
    
    /**
     * Cleanup
     */
    fun cleanup() {
        revenueScope.cancel()
    }
}

