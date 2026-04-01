package com.teamz.lab.debugger.utils

import android.app.Activity
import android.content.Context
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AnalyticsEvent

/**
 * Centralized AI click handler that shows fullscreen ads before opening AI dialogs
 *
 * This ensures all AI clicks throughout the app follow AdMob restrictions:
 * - Shows interstitial ad if available and allowed
 * - Respects cooldown/throttling
 * - Handles analytics tracking
 * - Executes callback after ad is dismissed or skipped
 * - Soft-gates AI after FREE_AI_USES_LIMIT uses (shows dismissable paywall, never blocks)
 *
 * Usage:
 * ```kotlin
 * AIClickHandler.handleAIClick(activity, source = "fab_ai",
 *     onPaywallRequest = { showRevenueCatPaywall = true }
 * ) {
 *     showAIDialog = true
 * }
 * ```
 */
object AIClickHandler {

    private const val PREFS_NAME = "ai_usage_prefs"
    private const val KEY_AI_USE_COUNT = "ai_use_count"
    private const val FREE_AI_USES_LIMIT = 3 // Show paywall after 3 free uses

    /**
     * Handle AI icon click with centralized ad logic and soft premium gate.
     * After FREE_AI_USES_LIMIT uses, shows paywall FIRST but user can always dismiss
     * and continue to AI. This is a soft gate, never a hard block.
     *
     * @param activity The activity context
     * @param source Source identifier for analytics
     * @param itemTitle Optional item title for item-specific AI clicks
     * @param onPaywallRequest Callback to show paywall (user can dismiss → AI still opens next tap)
     * @param onAIClick Callback to execute after ad is shown/dismissed or skipped
     */
    fun handleAIClick(
        activity: Activity,
        source: String,
        itemTitle: String? = null,
        onPaywallRequest: (() -> Unit)? = null,
        onAIClick: () -> Unit
    ) {
        // Log analytics
        val analyticsParams = if (itemTitle != null) {
            mapOf<String, Any?>(
                "source" to source,
                "item_title" to itemTitle
            )
        } else {
            mapOf<String, Any?>(
                "source" to source
            )
        }
        AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, analyticsParams)

        // Increment AI usage count
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val useCount = prefs.getInt(KEY_AI_USE_COUNT, 0) + 1
        prefs.edit().putInt(KEY_AI_USE_COUNT, useCount).apply()

        // Soft gate: after FREE_AI_USES_LIMIT, show paywall first (but user can dismiss)
        // Premium users skip this entirely
        if (!RevenueCatManager.isPremium() && useCount > FREE_AI_USES_LIMIT && onPaywallRequest != null) {
            AnalyticsUtils.logEvent(AnalyticsEvent.PremiumPaywallShown, mapOf<String, Any?>(
                "source" to "ai_soft_gate",
                "ai_use_count" to useCount
            ))
            // Show paywall - user can dismiss and tap AI again to proceed
            onPaywallRequest()
            return
        }

        // Show ad if available, then execute callback
        // InterstitialAdManager handles all checks centrally:
        // - RemoteConfig enable/disable flag
        // - Global time-based throttling
        // - Ad loading and showing
        InterstitialAdManager.showAdIfAvailable(activity) {
            // Ad closed or skipped - execute AI action
            onAIClick()
        }
    }

    /** Get current AI usage count (for UI display if needed) */
    fun getUsageCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_AI_USE_COUNT, 0)
    }
}
