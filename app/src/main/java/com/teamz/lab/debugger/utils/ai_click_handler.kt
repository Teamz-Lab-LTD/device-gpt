package com.teamz.lab.debugger.utils

import android.app.Activity
import android.content.Context
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AnalyticsEvent

/**
 * Centralized AI click handler that shows fullscreen ads before opening AI dialogs
 *
 * Soft gate strategy (never blocks AI):
 * - First 3 AI uses: completely free, no interruption
 * - 4th use in a session: show paywall ONCE, then open AI anyway
 * - 5th+ uses in same session: AI opens directly (paywall already shown)
 * - Next session: show paywall once again on first AI use, then free for rest of session
 *
 * This ensures users ALWAYS get to use AI. The paywall is just a brief reminder.
 */
object AIClickHandler {

    private const val PREFS_NAME = "ai_usage_prefs"
    private const val KEY_AI_USE_COUNT = "ai_use_count"
    private const val KEY_PAYWALL_SHOWN_SESSION = "paywall_shown_this_session"
    private const val FREE_AI_USES_LIMIT = 3

    // In-memory flag: reset each app process (session)
    private var paywallShownThisSession = false

    /**
     * Handle AI icon click with soft premium gate.
     *
     * @param activity The activity context
     * @param source Source identifier for analytics
     * @param itemTitle Optional item title for item-specific AI clicks
     * @param onPaywallRequest Show paywall AND open AI (not either/or)
     * @param onAIClick Callback to open AI dialog
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

        // Increment total AI usage count (persists across sessions)
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val useCount = prefs.getInt(KEY_AI_USE_COUNT, 0) + 1
        prefs.edit().putInt(KEY_AI_USE_COUNT, useCount).apply()

        // Soft gate: show paywall ONCE per session after free limit
        // Then ALWAYS proceed to open AI — never block
        if (!RevenueCatManager.isPremium() &&
            useCount > FREE_AI_USES_LIMIT &&
            !paywallShownThisSession &&
            onPaywallRequest != null
        ) {
            paywallShownThisSession = true
            AnalyticsUtils.logEvent(AnalyticsEvent.PremiumPaywallShown, mapOf<String, Any?>(
                "source" to "ai_soft_gate",
                "ai_use_count" to useCount
            ))
            // Show paywall — but still open AI right after
            onPaywallRequest()
            // Don't return! Fall through to open AI below
        }

        // ALWAYS open AI — show ad first if available, then execute
        InterstitialAdManager.showAdIfAvailable(activity) {
            onAIClick()
        }
    }

    /** Get current AI usage count */
    fun getUsageCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_AI_USE_COUNT, 0)
    }
}
