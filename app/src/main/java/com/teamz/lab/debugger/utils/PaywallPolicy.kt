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
    /**
     * Per-session show counter. Measured 2026-08-27 (GA4 481224245, 28d): 717 paywall
     * impressions across 418 sessions = 1.72 per session, in an average new-user session
     * of 155 seconds — and 0 purchases. There was no per-session cap because there is no
     * single show site: three call sites in DeviceGptNavExperience each wrote
     * `last_paywall_shown_time` independently, so nothing could count them together.
     * recordShown() is that missing chokepoint.
     */
    private const val KEY_SESSION_SHOWN_COUNT = "policy_session_shown_count"
    private const val KEY_SESSION_STAMP = "policy_session_stamp"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val KEY_DISMISS_SURVEY_SHOWN = "policy_dismiss_survey_shown"

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
        if (!QuietPeriod.unsolicitedPaywallAllowed()) return false
        if (!sessionCapClear(context)) return false
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
        if (!QuietPeriod.unsolicitedPaywallAllowed()) return false
        val p = prefs(context)
        val last = p.getLong("last_paywall_shown_time", 0L)
        val repeatMs = RemoteConfigUtils.getPaywallRepeatIntervalDays() * DAY_MS
        val cooldownOk = last == 0L || System.currentTimeMillis() - last >= repeatMs
        return cooldownOk && suppressionClear(context) && sessionCapClear(context)
    }

    /**
     * True while this session is still under `paywall_max_per_session` (default 1).
     *
     * The counter is keyed on EngagementTracker's session number, so it resets by itself
     * when a new session starts — no lifecycle hook to forget to call, and no timer that
     * could leave a stale count behind after a process death.
     */
    private fun sessionCapClear(context: Context): Boolean {
        val max = RemoteConfigUtils.getPaywallMaxPerSession()
        if (max <= 0L) return false          // 0 = paywall fully off, a usable kill switch
        return shownThisSession(context) < max
    }

    private fun shownThisSession(context: Context): Int {
        val p = prefs(context)
        val current = EngagementTracker.getSessionCount(context)
        if (p.getInt(KEY_SESSION_STAMP, -1) != current) return 0
        return p.getInt(KEY_SESSION_SHOWN_COUNT, 0)
    }

    /**
     * Call at the moment the paywall actually reaches the screen — the same place
     * `AnalyticsEvent.PremiumPaywallShown` is logged, so the counter and the funnel can
     * never disagree about what a "show" is.
     */
    fun recordShown(context: Context) {
        val p = prefs(context)
        val current = EngagementTracker.getSessionCount(context)
        val n = if (p.getInt(KEY_SESSION_STAMP, -1) == current) {
            p.getInt(KEY_SESSION_SHOWN_COUNT, 0)
        } else 0
        p.edit()
            .putInt(KEY_SESSION_STAMP, current)
            .putInt(KEY_SESSION_SHOWN_COUNT, n + 1)
            .apply()
    }

    /**
     * True when no suppression window is currently in force.
     *
     * Reads BOTH windows. Until 2026-08-26 this consulted only KEY_SUPPRESS_UNTIL, so the
     * 7-day cooldown written by RouteAction.COOLDOWN_7D (line ~121) was a dead write —
     * nothing anywhere read that key except debugReset(), which deletes it. The practical
     * effect: a user who picked "not now" was telling the app to back off for a week, and
     * the app forgot immediately. "Not now" is the most likely choice on that sheet, so the
     * most common dismissal was also the one that did nothing.
     *
     * Note this only becomes observable once RC `paywall_reason_routing_enabled` is true;
     * while that flag is false, onDismissReason() returns before writing either key.
     */
    private fun suppressionClear(context: Context): Boolean {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val suppressUntil = p.getLong(KEY_SUPPRESS_UNTIL, 0L)
        val cooldownUntil = p.getLong(KEY_COOLDOWN_UNTIL, 0L)
        val blocked = (suppressUntil != 0L && now < suppressUntil) ||
            (cooldownUntil != 0L && now < cooldownUntil)
        return !blocked
    }

    // ---- 2. Dismiss-reason routing --------------------------------------------

    fun routeForReason(reason: String): RouteAction = when (reason) {
        "closed_by_mistake" -> RouteAction.RESHOW_ONCE
        "no_value_seen" -> RouteAction.SUPPRESS_30D
        // Everything else backs off for a week. Until 2026-08-28 only three reasons were
        // routed and the rest fell to LOG_ONLY — which covered the two LARGEST buckets:
        // of 165 dismissals, no_response 68 + sheet_dismissed 61 = 78%. Those users were
        // ignoring the sheet entirely and the app kept showing it. Price rejection is
        // rare by comparison (too_expensive 6, no_value_seen 2), so "did not engage" is
        // the signal worth acting on, not "said no to the price".
        "not_now", "no_response", "sheet_dismissed", "too_expensive", "other" ->
            RouteAction.COOLDOWN_7D
        else -> RouteAction.COOLDOWN_7D
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

    // ---- 3. Dismiss-reason survey frequency -------------------------------------------

    /**
     * The "Quick — why did you close?" sheet runs once per install. It used to follow every
     * paywall journey; on vc48, 20 of 22 day-one survey events were sheet_dismissed or
     * no_response, so repeating it collected nothing and added a screen each time.
     */
    fun dismissSurveyAllowed(context: Context): Boolean =
        !prefs(context).getBoolean(KEY_DISMISS_SURVEY_SHOWN, false)

    fun recordDismissSurveyShown(context: Context) {
        prefs(context).edit { putBoolean(KEY_DISMISS_SURVEY_SHOWN, true) }
    }

    /** New install / debug reset helper. */
    fun debugReset(context: Context) {
        prefs(context).edit {
            remove(KEY_RESHOW_CREDIT); remove(KEY_SUPPRESS_UNTIL); remove(KEY_COOLDOWN_UNTIL)
        }
    }
}
