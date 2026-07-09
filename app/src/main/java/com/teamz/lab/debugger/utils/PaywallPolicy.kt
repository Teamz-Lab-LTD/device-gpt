package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.teamz.lab.debugger.ui.FirstScanGate

/**
 * v3.2.0 paywall policy layer (2026-07-10 growth synthesis, Phase 4).
 *
 * Two responsibilities:
 *
 * 1. TRIGGER GATES — cold-open paywall chains (review-chain + session fallback)
 *    must pass [coldTriggerAllowed]: session count >= RC `paywall_min_sessions`
 *    AND first scan completed. Rationale: 89% dismiss rate was measured with
 *    paywalls firing at app-open before the user saw any value. Gated behind RC
 *    `paywall_delay_enabled` so it can be reverted without a release.
 *
 * 2. DISMISS-REASON ROUTING — pre-registered decision table for the
 *    `paywall_dismiss_reason` event (shipped v3.1.14). Phase 1 is LOG-ONLY:
 *    every routing decision is logged as `paywall_rerouted`, but no UI change
 *    happens until RC `paywall_reason_routing_enabled` flips true AND the routed
 *    target exists (runtime guard — never route to vaporware).
 *
 *    Routing table (Phase 1 targets only):
 *      closed_by_mistake -> RESHOW_ONCE   (one immediate re-show)
 *      not_now           -> COOLDOWN_7D   (re-ask only at a delight moment)
 *      no_value_seen     -> SUPPRESS_30D  (expires — no permanent revenue hole)
 *      too_expensive     -> LOG_ONLY      (Phase 2: downsell to Tier-B/weekly)
 *      other/no_response -> LOG_ONLY
 */
object PaywallPolicy {

    private const val PREFS = "paywall_trigger_prefs" // same file as trigger chains
    private const val KEY_RESHOW_CREDIT = "policy_reshow_credit"
    private const val KEY_SUPPRESS_UNTIL = "policy_suppress_until"
    private const val KEY_COOLDOWN_UNTIL = "policy_cooldown_until"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    enum class RouteAction { RESHOW_ONCE, COOLDOWN_7D, SUPPRESS_30D, LOG_ONLY }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- 1. Trigger gates ----------------------------------------------------

    /**
     * Gate for COLD-OPEN paywall chains (after-review chain + session fallback).
     * Delight-moment triggers (post-scan high score etc.) use
     * [delightTriggerAllowed] instead — they carry their own value context.
     */
    fun coldTriggerAllowed(context: Context): Boolean {
        if (!RemoteConfigUtils.isPaywallDelayEnabled()) return suppressionClear(context)
        val sessions = EngagementTracker.getSessionCount(context)
        val scanned = FirstScanGate.hasCompletedScan(context)
        val allowed = sessions >= RemoteConfigUtils.getPaywallMinSessions() && scanned
        return allowed && suppressionClear(context)
    }

    /**
     * Gate for delight-moment triggers. Shares the 7-day cooldown written by the
     * existing chains (`last_paywall_shown_time`) + respects reason-routing
     * suppression windows.
     */
    fun delightTriggerAllowed(context: Context): Boolean {
        val p = prefs(context)
        val last = p.getLong("last_paywall_shown_time", 0L)
        val repeatMs = RemoteConfigUtils.getPaywallRepeatIntervalDays() * DAY_MS
        val cooldownOk = last == 0L || System.currentTimeMillis() - last >= repeatMs
        return cooldownOk && suppressionClear(context)
    }

    private fun suppressionClear(context: Context): Boolean {
        val until = prefs(context).getLong(KEY_SUPPRESS_UNTIL, 0L)
        return until == 0L || System.currentTimeMillis() >= until
    }

    // ---- 2. Dismiss-reason routing --------------------------------------------

    fun routeForReason(reason: String): RouteAction = when (reason) {
        "closed_by_mistake" -> RouteAction.RESHOW_ONCE
        "not_now" -> RouteAction.COOLDOWN_7D
        "no_value_seen" -> RouteAction.SUPPRESS_30D
        else -> RouteAction.LOG_ONLY // too_expensive (Phase 2), other, no_response
    }

    /**
     * Apply the routing table for a dismiss reason. ALWAYS logs the decision
     * (`paywall_rerouted`); applies state only when RC routing flag is on.
     * Returns true when the caller should immediately re-show the paywall
     * (RESHOW_ONCE, routing enabled).
     */
    fun onDismissReason(context: Context, reason: String, analyticsSource: String): Boolean {
        val action = routeForReason(reason)
        val routingEnabled = RemoteConfigUtils.isPaywallReasonRoutingEnabled()
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.PaywallRerouted,
                mapOf(
                    "reason" to reason,
                    "action" to action.name,
                    "applied" to routingEnabled,
                    "source" to analyticsSource,
                )
            )
        } catch (_: Throwable) { /* analytics not critical */ }

        if (!routingEnabled) return false

        val p = prefs(context)
        return when (action) {
            RouteAction.RESHOW_ONCE -> {
                // Single immediate re-show, once — never loop.
                val credit = p.getInt(KEY_RESHOW_CREDIT, 1)
                if (credit > 0) {
                    p.edit { putInt(KEY_RESHOW_CREDIT, 0) }
                    true
                } else false
            }
            RouteAction.COOLDOWN_7D -> {
                p.edit { putLong(KEY_COOLDOWN_UNTIL, System.currentTimeMillis() + 7 * DAY_MS) }
                false
            }
            RouteAction.SUPPRESS_30D -> {
                p.edit { putLong(KEY_SUPPRESS_UNTIL, System.currentTimeMillis() + 30 * DAY_MS) }
                false
            }
            RouteAction.LOG_ONLY -> false
        }
    }

    /** New install / debug reset helper. */
    fun debugReset(context: Context) {
        prefs(context).edit {
            remove(KEY_RESHOW_CREDIT); remove(KEY_SUPPRESS_UNTIL); remove(KEY_COOLDOWN_UNTIL)
        }
    }
}
