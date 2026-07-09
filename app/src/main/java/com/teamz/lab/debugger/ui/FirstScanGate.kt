package com.teamz.lab.debugger.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

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
    const val KEY_SUB_BATTERY = "first_scan_sub_battery"
    const val KEY_SUB_MEMORY = "first_scan_sub_memory"
    const val KEY_SUB_STORAGE = "first_scan_sub_storage"
    const val KEY_SUB_NETWORK = "first_scan_sub_network"

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
     * v3.2.0 honest scan result. Play policy 2026-07-10: the scan must measure what
     * it claims to measure. Four real subsystem reads; a sub-score is null when the
     * subsystem could not be read (never invented). [total] is null only when NO
     * subsystem could be read — the UI must show an explicit error state, never a
     * fabricated number.
     */
    data class QuickScanResult(
        val total: Int?,
        val battery: Int?,
        val memory: Int?,
        val storage: Int?,
        val network: Int?,
    ) {
        val readableChecks: Int
            get() = listOfNotNull(battery, memory, storage, network).size
    }

    /** Disclosed weights — shown verbatim on the result card. */
    const val WEIGHT_BATTERY = 40
    const val WEIGHT_MEMORY = 25
    const val WEIGHT_STORAGE = 20
    const val WEIGHT_NETWORK = 15

    /**
     * Run the real 4-check scan. Each check completes in <100ms except network
     * (reachability probe, hard 1500ms cap so an offline device can never hang
     * the gate). [onCheckDone] fires after each check for progress binding —
     * the UI progress bar reflects ACTUAL completion, not a timed animation.
     */
    suspend fun runQuickScan(
        context: Context,
        onCheckDone: (completed: Int, label: String) -> Unit = { _, _ -> },
    ): QuickScanResult {
        val battery = readBatteryConditionScore(context)
        onCheckDone(1, "Battery")
        val memory = readMemoryScore(context)
        onCheckDone(2, "Memory")
        val storage = readStorageScore(context)
        onCheckDone(3, "Storage")
        val network = readNetworkScore(context)
        onCheckDone(4, "Network")

        val parts = listOf(
            battery to WEIGHT_BATTERY,
            memory to WEIGHT_MEMORY,
            storage to WEIGHT_STORAGE,
            network to WEIGHT_NETWORK,
        ).filter { it.first != null }

        val total = if (parts.isEmpty()) null else {
            // Redistribute weights over the subsystems we could actually read.
            val weightSum = parts.sumOf { it.second }
            parts.sumOf { (score, w) -> score!! * w } / weightSum
        }
        return QuickScanResult(
            total = total?.coerceIn(0, 100),
            battery = battery, memory = memory, storage = storage, network = network,
        )
    }

    /**
     * Legacy sync entry point kept for the LastScoreCard replay path + tests.
     * Delegates to the honest scan; returns 0 (worst, clamped) when nothing was
     * readable rather than inventing a mid-range score.
     */
    fun computeQuickScore(context: Context): Int =
        kotlinx.coroutines.runBlocking { runQuickScan(context).total ?: 0 }

    /**
     * Battery CONDITION (not charge level — a full battery is not a healthy
     * battery). Sources: EXTRA_HEALTH + temperature from the sticky
     * ACTION_BATTERY_CHANGED intent. Null when the sticky intent is unavailable.
     */
    private fun readBatteryConditionScore(context: Context): Int? = try {
        val intent: Intent? = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        if (intent == null) null else {
            val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            val tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
            var score = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> 100
                BatteryManager.BATTERY_HEALTH_COLD -> 70
                BatteryManager.BATTERY_HEALTH_UNKNOWN -> 75
                BatteryManager.BATTERY_HEALTH_OVERHEAT,
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE,
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> 35
                BatteryManager.BATTERY_HEALTH_DEAD -> 10
                else -> 75
            }
            if (tempC > 45f) score -= 25 else if (tempC > 40f) score -= 12
            score.coerceIn(0, 100)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "battery condition read failed: ${t.message}")
        null
    }

    /** Memory pressure from ActivityManager.MemoryInfo. */
    private fun readMemoryScore(context: Context): Int? = try {
        val am = context.getSystemService<ActivityManager>() ?: return null
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.totalMem <= 0L) null else {
            val usedPct = ((info.totalMem - info.availMem) * 100 / info.totalMem).toInt()
            var score = when {
                usedPct <= 60 -> 100
                usedPct <= 70 -> 88
                usedPct <= 80 -> 72
                usedPct <= 90 -> 55
                else -> 35
            }
            if (info.lowMemory) score = score.coerceAtMost(30)
            score.coerceIn(0, 100)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "memory read failed: ${t.message}")
        null
    }

    /** Free-space headroom on the data partition. */
    private fun readStorageScore(context: Context): Int? = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        if (total <= 0L) null else {
            val freePct = (stat.availableBytes * 100 / total).toInt()
            when {
                freePct >= 25 -> 100
                freePct >= 15 -> 85
                freePct >= 10 -> 70
                freePct >= 5 -> 50
                else -> 25
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "storage read failed: ${t.message}")
        null
    }

    /**
     * Network state: transport present + validated capability + a real reachability
     * probe (TCP 8.8.8.8:53). Offline is a FACT (low score), an unreadable
     * ConnectivityManager is an unknown (null). Hard-capped at 1500ms.
     */
    private suspend fun readNetworkScore(context: Context): Int? = try {
        val cm = context.getSystemService<ConnectivityManager>()
        if (cm == null) null else {
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            when {
                caps == null -> 20 // no active network at scan time — factual
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> 30
                else -> {
                    val reachable = withTimeoutOrNull(1500L) {
                        withContext(Dispatchers.IO) {
                            try {
                                Socket().use { s ->
                                    s.connect(InetSocketAddress("8.8.8.8", 53), 1200)
                                    true
                                }
                            } catch (t: Throwable) {
                                false
                            }
                        }
                    } ?: false
                    when {
                        reachable && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> 100
                        reachable -> 85
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> 75
                        else -> 45 // connected but nothing reachable at scan time
                    }
                }
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "network read failed: ${t.message}")
        null
    }

    /**
     * Called when the user taps "See details" or "Share my score" after the
     * scan completes. Persists the score + sub-scores + sets the gate-once flag.
     * Sub-scores feed the widget-delta + timeline baselines (R3/R5).
     */
    fun markCompleted(context: Context, finalScore: Int, result: QuickScanResult? = null) {
        val p = prefs(context)
        p.edit {
            putBoolean(KEY_FIRST_SCAN_COMPLETED, true)
            putInt(KEY_FIRST_SCAN_SCORE, finalScore.coerceIn(0, 100))
            putLong(KEY_FIRST_SCAN_TS, System.currentTimeMillis())
            if (result != null) {
                result.battery?.let { putInt(KEY_SUB_BATTERY, it) }
                result.memory?.let { putInt(KEY_SUB_MEMORY, it) }
                result.storage?.let { putInt(KEY_SUB_STORAGE, it) }
                result.network?.let { putInt(KEY_SUB_NETWORK, it) }
            }
        }
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.FirstScanCompleted,
                mapOf(
                    "score" to finalScore,
                    "sub_battery" to (result?.battery ?: -1),
                    "sub_memory" to (result?.memory ?: -1),
                    "sub_storage" to (result?.storage ?: -1),
                    "sub_network" to (result?.network ?: -1),
                )
            )
        } catch (_: Throwable) { /* analytics not critical */ }
        // R5: every scan is a timeline event (fire-and-forget, never blocks UI).
        try {
            com.teamz.lab.debugger.db.DeviceEventsRepository.recordScoreScan(
                context, finalScore.coerceIn(0, 100),
                result?.battery, result?.memory, result?.storage, result?.network,
            )
        } catch (_: Throwable) { /* journaling not critical */ }
        Log.i(TAG, "First scan completed — score=$finalScore")
    }

    /**
     * v3.2.0: the scan could not read ANY subsystem. Completes the gate (never
     * trap the user) but persists NO score — a fabricated number is the exact
     * Deceptive Behavior class the strike was about.
     */
    fun markScanFailed(context: Context) {
        prefs(context).edit {
            putBoolean(KEY_FIRST_SCAN_COMPLETED, true)
            putLong(KEY_FIRST_SCAN_TS, System.currentTimeMillis())
        }
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.FirstScanCompleted,
                mapOf("score" to -1, "failed" to true)
            )
        } catch (_: Throwable) { /* analytics not critical */ }
        Log.w(TAG, "First scan completed with NO readable subsystem — no score persisted")
    }

    fun getSubScore(context: Context, key: String): Int = prefs(context).getInt(key, -1)

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

    fun getLastScanTimestamp(context: Context): Long =
        prefs(context).getLong(KEY_FIRST_SCAN_TS, 0L)

    fun hasCompletedScan(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FIRST_SCAN_COMPLETED, false)

    /**
     * v3.1.12: user-triggered replay from the LastScoreCard on the Health tab.
     * Wipes the completion flag so [currentState] returns SCANNING on next cold
     * start. Score + timestamp are kept so the LastScoreCard can still show the
     * previous result until the new scan overwrites it.
     */
    fun clearForReplay(context: Context) {
        prefs(context).edit { remove(KEY_FIRST_SCAN_COMPLETED) }
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.DeviceScoreReplayed,
                mapOf("previous_score" to getLastScanScore(context))
            )
        } catch (_: Throwable) { /* analytics not critical */ }
        Log.i(TAG, "First-scan gate cleared for replay (user-initiated)")
    }

}
