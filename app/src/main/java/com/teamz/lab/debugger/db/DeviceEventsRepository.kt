package com.teamz.lab.debugger.db

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.teamz.lab.debugger.ui.FirstScanGate
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single write/read facade over the device_events table (v3.2.0 R5).
 * All writers are fire-and-forget on IO — a DB failure must never crash a
 * feature that only wanted to journal itself.
 */
object DeviceEventsRepository {

    private const val TAG = "DeviceEvents"
    private const val PREFS = "device_events_meta"
    private const val KEY_LAST_SNAPSHOT_DAY = "last_baseline_snapshot_day"
    private const val KEY_PREV_SNAPSHOT_SCORE = "prev_baseline_snapshot_score"
    private const val KEY_LAST_SNAPSHOT_SCORE = "last_baseline_snapshot_score"
    private const val KEY_BACKFILLED = "prefs_backfilled_v1"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val PRUNE_AFTER_DAYS = 90L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dao(context: Context): DeviceEventDao =
        DeviceGptDatabase.get(context).deviceEventDao()

    // ---- Writers ---------------------------------------------------------------

    fun recordScoreScan(
        context: Context,
        total: Int,
        battery: Int?,
        memory: Int?,
        storage: Int?,
        network: Int?,
    ) = fireAndForget(context) {
        dao(context).insert(
            DeviceEvent(
                type = DeviceEvent.TYPE_SCORE_SCAN,
                timestamp = System.currentTimeMillis(),
                score = total,
                subBattery = battery, subMemory = memory,
                subStorage = storage, subNetwork = network,
                label = "Device Score $total",
            )
        )
    }

    fun recordChargeSession(context: Context, label: String, payloadJson: String? = null) =
        fireAndForget(context) {
            dao(context).insert(
                DeviceEvent(
                    type = DeviceEvent.TYPE_CHARGE_SESSION,
                    timestamp = System.currentTimeMillis(),
                    label = label,
                    payload = payloadJson,
                )
            )
        }

    fun recordAppInstalled(context: Context, packageName: String, label: String) =
        fireAndForget(context) {
            dao(context).insert(
                DeviceEvent(
                    type = DeviceEvent.TYPE_APP_INSTALLED,
                    timestamp = System.currentTimeMillis(),
                    label = label,
                    payload = packageName,
                )
            )
        }

    /**
     * Daily baseline snapshot (R3 widget-delta source). At most one per calendar
     * day — callers can invoke on every app open / widget refresh; dedup is here.
     */
    fun recordDailySnapshotIfDue(context: Context, healthScore10: Int) {
        val today = (System.currentTimeMillis() / DAY_MS).toInt()
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getInt(KEY_LAST_SNAPSHOT_DAY, -1) == today) return
        p.edit {
            putInt(KEY_LAST_SNAPSHOT_DAY, today)
            // Shift last -> prev so the widget (sync context, no DB access) can
            // render "vs yesterday" deltas from prefs alone (R3 widget v2).
            putInt(KEY_PREV_SNAPSHOT_SCORE, p.getInt(KEY_LAST_SNAPSHOT_SCORE, -1))
            putInt(KEY_LAST_SNAPSHOT_SCORE, healthScore10)
        }
        fireAndForget(context) {
            dao(context).insert(
                DeviceEvent(
                    type = DeviceEvent.TYPE_BASELINE_SNAPSHOT,
                    timestamp = System.currentTimeMillis(),
                    score = healthScore10 * 10, // normalize 0-10 health to 0-100 scale
                    label = "Daily snapshot ${healthScore10}/10",
                )
            )
            try {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.BaselineSnapshotWritten,
                    mapOf("health_score" to healthScore10)
                )
            } catch (_: Throwable) { }
        }
    }

    /** Sync prefs read for the widget: Pair(yesterdayScore10, todayScore10); -1 = unknown. */
    fun snapshotDeltaFromPrefs(context: Context): Pair<Int, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Pair(p.getInt(KEY_PREV_SNAPSHOT_SCORE, -1), p.getInt(KEY_LAST_SNAPSHOT_SCORE, -1))
    }

    // ---- Readers ---------------------------------------------------------------

    suspend fun timelineWindow(context: Context, days: Long): List<DeviceEvent> =
        dao(context).eventsSince(System.currentTimeMillis() - days * DAY_MS)

    suspend fun latestSnapshots(context: Context, limit: Int): List<DeviceEvent> =
        dao(context).latestByType(DeviceEvent.TYPE_BASELINE_SNAPSHOT, limit)

    suspend fun latestChargeSessions(context: Context, limit: Int): List<DeviceEvent> =
        dao(context).latestByType(DeviceEvent.TYPE_CHARGE_SESSION, limit)

    suspend fun eventCount(context: Context): Int = dao(context).countAll()

    /** Last two score scans (newest first) — R6 trend template input. */
    suspend fun latestScansPair(context: Context): List<DeviceEvent> =
        dao(context).latestByType(DeviceEvent.TYPE_SCORE_SCAN, 2)

    suspend fun chargeCountSince(context: Context, sinceTs: Long): Int =
        dao(context).countByTypeSince(DeviceEvent.TYPE_CHARGE_SESSION, sinceTs)

    suspend fun chargeCountBetween(context: Context, fromTs: Long, toTs: Long): Int =
        dao(context).countByTypeBetween(DeviceEvent.TYPE_CHARGE_SESSION, fromTs, toTs)

    suspend fun installCountSince(context: Context, sinceTs: Long): Int =
        dao(context).countByTypeSince(DeviceEvent.TYPE_APP_INSTALLED, sinceTs)

    // ---- Maintenance -------------------------------------------------------------

    /**
     * Called once from Application.onCreate (off main thread):
     *   1. Backfill: seed the timeline from existing prefs (last scan score) so
     *      upgraded users don't open an empty timeline.
     *   2. Prune events older than 90 days.
     */
    fun initMaintenance(context: Context) = fireAndForget(context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean(KEY_BACKFILLED, false)) {
            p.edit { putBoolean(KEY_BACKFILLED, true) }
            val lastScore = FirstScanGate.getLastScanScore(context)
            val lastTs = FirstScanGate.getLastScanTimestamp(context)
            if (lastScore in 0..100 && lastTs > 0L) {
                dao(context).insert(
                    DeviceEvent(
                        type = DeviceEvent.TYPE_SCORE_SCAN,
                        timestamp = lastTs,
                        score = lastScore,
                        subBattery = FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_BATTERY).takeIf { it >= 0 },
                        subMemory = FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_MEMORY).takeIf { it >= 0 },
                        subStorage = FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_STORAGE).takeIf { it >= 0 },
                        subNetwork = FirstScanGate.getSubScore(context, FirstScanGate.KEY_SUB_NETWORK).takeIf { it >= 0 },
                        label = "Device Score $lastScore",
                    )
                )
                Log.i(TAG, "Backfilled timeline from prefs: score=$lastScore ts=$lastTs")
            }
        }
        val pruned = dao(context).prune(System.currentTimeMillis() - PRUNE_AFTER_DAYS * DAY_MS)
        if (pruned > 0) Log.i(TAG, "Pruned $pruned device_events older than $PRUNE_AFTER_DAYS days")
    }

    private fun fireAndForget(context: Context, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                Log.w(TAG, "device_events write failed: ${t.message}")
            }
        }
    }
}
