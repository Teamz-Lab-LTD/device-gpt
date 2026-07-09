package com.teamz.lab.debugger.utils

import android.content.Context
import androidx.core.content.edit
import com.teamz.lab.debugger.db.DeviceEvent
import com.teamz.lab.debugger.db.DeviceEventsRepository
import com.teamz.lab.debugger.ui.FirstScanGate

/**
 * v3.2.0 R6 — one templated insight per open (2026-07-10 growth synthesis).
 *
 * INVERTED ARCHITECTURE (applied verifier modification): parameterized templates
 * are the PRIMARY path on ALL devices — numbers are injected programmatically
 * from device_events queries. No LLM required; a future Gemini Nano slice may
 * only REPHRASE a filled template (with a validator rejecting any output
 * containing a numeral not present in the input payload).
 *
 * Honesty rules:
 *   - Own-baseline comparisons only. NEVER invented cross-user percentiles.
 *   - Every number in the line comes from a real stored event.
 *   - Per-session cache — no reroll farming, the line changes between sessions
 *     because the underlying data changed, not because of randomness theater.
 *
 * RC-gated: `insight_per_open_enabled` (default OFF — dark-shipped).
 */
object InsightEngine {

    private const val PREFS = "insight_engine"
    private const val KEY_SESSION_ID = "cached_session_id"
    private const val KEY_CACHED_LINE = "cached_line"
    private const val KEY_CACHED_TEMPLATE = "cached_template_id"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Insight(val templateId: String, val line: String)

    /**
     * Compute (or return session-cached) insight. Suspend — runs DB queries.
     * Returns null when RC flag is off or no data supports any template.
     */
    suspend fun insightForThisOpen(context: Context): Insight? {
        if (!RemoteConfigUtils.isInsightPerOpenEnabled()) return null

        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val session = EngagementTracker.getSessionCount(context)
        if (p.getInt(KEY_SESSION_ID, -1) == session) {
            val line = p.getString(KEY_CACHED_LINE, null)
            val tpl = p.getString(KEY_CACHED_TEMPLATE, null)
            if (line != null && tpl != null) return Insight(tpl, line)
        }

        val insight = computeFresh(context) ?: return null
        p.edit {
            putInt(KEY_SESSION_ID, session)
            putString(KEY_CACHED_LINE, insight.line)
            putString(KEY_CACHED_TEMPLATE, insight.templateId)
        }
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.InsightShown,
                mapOf("template" to insight.templateId)
            )
        } catch (_: Throwable) { }
        return insight
    }

    /**
     * Template priority: most specific data first. Each template renders ONLY
     * when its inputs exist — first match wins.
     */
    private suspend fun computeFresh(context: Context): Insight? {
        val now = System.currentTimeMillis()

        // T1: score trend across last 2 scans
        val scans = try {
            DeviceEventsRepository.latestScansPair(context)
        } catch (_: Throwable) { null }
        if (scans != null && scans.size >= 2) {
            val newest = scans[0].score
            val prior = scans[1].score
            if (newest != null && prior != null && newest != prior) {
                val diff = newest - prior
                return if (diff > 0) {
                    Insight("score_up", "Score up $diff since your last scan ($prior → $newest).")
                } else {
                    Insight("score_down", "Score down ${-diff} since your last scan ($prior → $newest). Tap Run again to see today's picture.")
                }
            }
            if (newest != null && prior != null) {
                return Insight("score_flat", "Score steady at $newest across your last two scans.")
            }
        }

        // T2: charge sessions this week vs the one before
        try {
            val week = DeviceEventsRepository.chargeCountSince(context, now - 7 * DAY_MS)
            val prevWeek = DeviceEventsRepository.chargeCountBetween(context, now - 14 * DAY_MS, now - 7 * DAY_MS)
            if (week > 0 && prevWeek > 0 && week != prevWeek) {
                return if (week > prevWeek) {
                    Insight("charge_more", "$week charge sessions this week — up from $prevWeek last week.")
                } else {
                    Insight("charge_less", "$week charge sessions this week — down from $prevWeek last week.")
                }
            }
            if (week > 0) {
                return Insight("charge_count", "$week charge session${if (week == 1) "" else "s"} recorded this week.")
            }
        } catch (_: Throwable) { }

        // T3: new apps in the last 7 days
        try {
            val newApps = DeviceEventsRepository.installCountSince(context, now - 7 * DAY_MS)
            if (newApps > 0) {
                return Insight("new_apps", "$newApps new app${if (newApps == 1) "" else "s"} installed this week — review their permissions any time.")
            }
        } catch (_: Throwable) { }

        // T4: days of history collected (investment reminder — factual)
        try {
            val count = DeviceEventsRepository.eventCount(context)
            if (count >= 5) {
                return Insight("history_size", "$count events in this device's timeline so far.")
            }
        } catch (_: Throwable) { }

        // T5: sub-score callout from the last scan (worst readable subsystem)
        val subs = listOf(
            "battery" to FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_BATTERY),
            "memory" to FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_MEMORY),
            "storage" to FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_STORAGE),
            "network" to FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_NETWORK),
        ).filter { it.second in 0..100 }
        val worst = subs.minByOrNull { it.second }
        if (worst != null && worst.second < 70) {
            return Insight("worst_sub", "${worst.first.replaceFirstChar { it.uppercase() }} scored ${worst.second} on your last scan — the lowest of the four checks.")
        }

        return null // no data supports any template — render nothing, never invent
    }
}
