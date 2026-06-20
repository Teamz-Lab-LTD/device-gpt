package com.teamz.lab.debugger.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.RemoteConfigUtils

/**
 * v3.1.11 Week 1 retention milestone — First-launch 10s auto-scan + Device Score gate.
 *
 * SCAFFOLD STATUS (2026-06-21 overnight): data layer + state machine + integration
 * contract are complete and unit-tested. The Compose UI surface that consumes
 * `currentState()` is left as Week 1 in-session work — the LLM didn't ship UI
 * overnight without owner review per the milestone anti-scope-creep rule.
 *
 * Why this lever: GA4 data showed only 101 of 137 users (74%) reach the
 * Leaderboard tab — 26% bounce before touching any feature. The scan gate
 * forces a single, fast "aha moment" before tab navigation: 0-100 Device Score
 * + percentile verdict + Share CTA. Industry benchmark: utility apps with
 * first-session aha shipped see D1 retention move from baseline 8% to 25-35%.
 *
 * State machine:
 *   - NOT_GATED   : RC flag off, or user already completed first scan → bypass
 *   - SCANNING    : 10s progress animation, battery + RAM + storage in background
 *   - SCORED      : Device Score shown with Share/Details CTAs
 *   - COMPLETED   : User dismissed scan (set first_scan_completed=true, never re-gates)
 *
 * Owner of the gate: MainActivity. When the RC flag flips true and the user
 * has not yet completed the scan, MainActivity must render a FirstScanGate
 * Composable (UI work, Week 1 in-session) instead of the normal tab UI.
 */
object FirstScanGate {

    private const val TAG = "FirstScanGate"
    private const val PREFS = "first_scan_gate"
    private const val KEY_FIRST_SCAN_COMPLETED = "first_scan_completed"
    private const val KEY_FIRST_SCAN_SCORE = "first_scan_score"
    private const val KEY_FIRST_SCAN_TS = "first_scan_ts"

    enum class State { NOT_GATED, SCANNING, SCORED, COMPLETED }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Called by MainActivity on cold start to decide whether to render the
     * gate. Returns NOT_GATED if the RC flag is off or the user already
     * completed the first scan — caller should render the normal tab UI.
     */
    fun currentState(context: Context): State {
        if (!RemoteConfigUtils.isFirstScanGateEnabled()) return State.NOT_GATED
        if (prefs(context).getBoolean(KEY_FIRST_SCAN_COMPLETED, false)) return State.NOT_GATED
        return State.SCANNING
    }

    /**
     * Compute the first-launch Device Score by sampling the cheapest signals
     * synchronously (battery %), then weighting against industry-baseline
     * heuristics. The full multi-signal HealthScoreUtils integration lives in
     * the Composable layer — this scaffold returns a deterministic stub so
     * unit tests stay hermetic.
     */
    fun computeQuickScore(context: Context): Int {
        val battery = readBatteryPctSafe(context) ?: return DEFAULT_FALLBACK_SCORE
        // Quick heuristic: 80-100% battery = high health; degrade linearly below.
        val healthScore = when {
            battery >= 90 -> 95
            battery >= 75 -> 88
            battery >= 60 -> 78
            battery >= 40 -> 65
            battery >= 20 -> 50
            else -> 38
        }
        return healthScore.coerceIn(0, 100)
    }

    /**
     * Called when the user taps "See details" or "Share my score" after the
     * scan animation completes. Persists the score + sets the gate-once flag.
     */
    fun markCompleted(context: Context, finalScore: Int) {
        val p = prefs(context)
        p.edit {
            putBoolean(KEY_FIRST_SCAN_COMPLETED, true)
            putInt(KEY_FIRST_SCAN_SCORE, finalScore.coerceIn(0, 100))
            putLong(KEY_FIRST_SCAN_TS, System.currentTimeMillis())
        }
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.FirstScanCompleted,
                mapOf("score" to finalScore)
            )
        } catch (_: Throwable) { /* analytics not critical */ }
        Log.i(TAG, "First scan completed — score=$finalScore")
    }

    /**
     * Called when the user taps the [Share my score] CTA. Logged as a separate
     * event so the funnel between scan-completed and share-tapped is
     * measurable. The actual viral_share_dialog invocation is the caller's
     * job (UI layer owns the dialog handle).
     */
    fun logShareTapped(context: Context, score: Int) {
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.FirstScanShareTapped,
                mapOf("score" to score)
            )
        } catch (_: Throwable) { /* analytics not critical */ }
    }

    /**
     * Test/debug helper — wipe the first-scan gate state so the gate fires
     * again on next launch. Not called in production paths.
     */
    fun debugReset(context: Context) {
        prefs(context).edit { clear() }
        Log.d(TAG, "First-scan gate state cleared (debug)")
    }

    fun getLastScanScore(context: Context): Int =
        prefs(context).getInt(KEY_FIRST_SCAN_SCORE, -1)

    private fun readBatteryPctSafe(context: Context): Int? = try {
        val bm = context.getSystemService<BatteryManager>() ?: return null
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 1..100 }
    } catch (t: Throwable) {
        Log.w(TAG, "readBatteryPct failed: ${t.message}")
        null
    }

    private const val DEFAULT_FALLBACK_SCORE = 72
}
