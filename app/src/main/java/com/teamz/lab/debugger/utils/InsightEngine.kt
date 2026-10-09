package com.teamz.lab.debugger.utils

import android.content.Context
import androidx.core.content.edit
import com.teamz.lab.debugger.R
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

    /** Language of the cached line, so a language switch inside one session does not show the old one. */
    private const val KEY_CACHED_LANGUAGE = "cached_language"
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
        val language = context.resources.configuration.locales[0].language
        if (p.getInt(KEY_SESSION_ID, -1) == session && p.getString(KEY_CACHED_LANGUAGE, null) == language) {
            val line = p.getString(KEY_CACHED_LINE, null)
            val tpl = p.getString(KEY_CACHED_TEMPLATE, null)
            if (line != null && tpl != null) return Insight(tpl, line)
        }

        val insight = computeFresh(context) ?: return null
        p.edit {
            putInt(KEY_SESSION_ID, session)
            putString(KEY_CACHED_LINE, insight.line)
            putString(KEY_CACHED_TEMPLATE, insight.templateId)
            putString(KEY_CACHED_LANGUAGE, language)
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
                    Insight("score_up", context.string(R.string.mx_insight_score_up, "$diff", "$prior", "$newest"))
                } else {
                    Insight("score_down", context.string(R.string.mx_insight_score_down, "${-diff}", "$prior", "$newest"))
                }
            }
            if (newest != null && prior != null) {
                return Insight("score_flat", context.string(R.string.mx_insight_score_flat, "$newest"))
            }
        }

        // T2: charge sessions this week vs the one before
        try {
            val week = DeviceEventsRepository.chargeCountSince(context, now - 7 * DAY_MS)
            val prevWeek = DeviceEventsRepository.chargeCountBetween(context, now - 14 * DAY_MS, now - 7 * DAY_MS)
            if (week > 0 && prevWeek > 0 && week != prevWeek) {
                return if (week > prevWeek) {
                    Insight("charge_more", context.string(R.string.mx_insight_charge_more, "$week", "$prevWeek"))
                } else {
                    Insight("charge_less", context.string(R.string.mx_insight_charge_less, "$week", "$prevWeek"))
                }
            }
            if (week > 0) {
                val id = if (week == 1) R.string.mx_insight_charge_count_one else R.string.mx_insight_charge_count_many
                return Insight("charge_count", context.string(id, "$week"))
            }
        } catch (_: Throwable) { }

        // T3: new apps in the last 7 days
        try {
            val newApps = DeviceEventsRepository.installCountSince(context, now - 7 * DAY_MS)
            if (newApps > 0) {
                val id = if (newApps == 1) R.string.mx_insight_new_apps_one else R.string.mx_insight_new_apps_many
                return Insight("new_apps", context.string(id, "$newApps"))
            }
        } catch (_: Throwable) { }

        // T4: days of history collected (investment reminder — factual)
        try {
            val count = DeviceEventsRepository.eventCount(context)
            if (count >= 5) {
                return Insight("history_size", context.string(R.string.mx_insight_history_size, "$count"))
            }
        } catch (_: Throwable) { }

        // T5: sub-score callout from the last scan (worst readable subsystem)
        val subs = listOf(
            R.string.first_scan_battery to FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_BATTERY),
            R.string.first_scan_memory to FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_MEMORY),
            R.string.first_scan_storage to FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_STORAGE),
            R.string.first_scan_network to FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_NETWORK),
        ).filter { it.second in 0..100 }
        val worst = subs.minByOrNull { it.second }
        if (worst != null && worst.second < 70) {
            return Insight("worst_sub", context.string(R.string.mx_insight_worst_sub, context.string(worst.first), "${worst.second}"))
        }

        return null // no data supports any template — render nothing, never invent
    }
}
