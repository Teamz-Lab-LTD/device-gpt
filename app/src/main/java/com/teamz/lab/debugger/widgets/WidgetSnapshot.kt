package com.teamz.lab.debugger.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.teamz.lab.debugger.utils.HealthScoreUtils
import com.teamz.lab.debugger.utils.getAvailableStorage
import com.teamz.lab.debugger.utils.getRamUsage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * The widget's own data path.
 *
 * Until 2026-10-05 only SystemMonitorService wrote `lock_screen_widget_data`. That service is an
 * opt-in foreground service, and since vc49 it is not offered before day two — so a widget pinned
 * from the pin prompt (shown right after the first scan, ~30% accept) rendered "Health: 0/10",
 * "⚠️ Low Score", every metric "--" and "Initializing" forever, directly after the app had shown
 * the same user an Excellent score. Verified on a fresh vc50 install, emulator, 2026-10-05.
 *
 * This writes the readings any app can take cheaply (battery, battery temperature, RAM, storage,
 * the saved daily score). It never writes the service-only fields (speeds, CPU, FPS, power, alert
 * text), so when the service does run its richer data is untouched.
 */
object WidgetSnapshot {

    internal const val PREFS = "lock_screen_widget_data"
    internal const val KEY_HEALTH_SCORE = "health_score"
    /** yyyy-MM-dd the stored score was measured; the widget shows it only on that day. */
    internal const val KEY_HEALTH_SCORE_DAY = "health_score_day"

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "widget-snapshot").apply { isDaemon = true } }

    /** Last queued write — exposed so tests can wait for it. */
    @Volatile internal var pending: Future<*>? = null

    internal fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Off the caller's thread: saveHealthScore runs on Main in two of its three callers, and this
     * does a disk load, StatFs and four binder calls.
     */
    fun writeAsync(context: Context, healthScore: Int? = null) {
        val app = context.applicationContext
        pending = io.submit { write(app, healthScore) }
    }

    /**
     * @param healthScore the daily score just saved, or null to reuse today's saved score.
     */
    fun write(context: Context, healthScore: Int? = null) {
        try {
            val score = healthScore ?: todaysSavedScore(context)
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val percent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val tenthsC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                if (score != null) {
                    putInt(KEY_HEALTH_SCORE, score)
                    putString(KEY_HEALTH_SCORE_DAY, today())
                }
                if (percent in 0..100) putInt("battery_percent", percent)
                putBoolean("battery_charging", status == BatteryManager.BATTERY_STATUS_CHARGING)
                putBoolean("battery_full", status == BatteryManager.BATTERY_STATUS_FULL)
                putString("charging_type", when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> ""
                })
                batteryTempLabel(tenthsC)?.let { putString("temperature_value", it) }
                putString("ram", "🧠 RAM: " + getRamUsage(context))
                storageUsedTotal(getAvailableStorage())?.let { putString("storage_used_total", it) }
                putLong("last_update", System.currentTimeMillis())
                apply()
            }
            // Prefs are always kept current; the broadcast only when a widget exists, because an
            // explicit broadcast reaches the provider (and its analytics) with none pinned.
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, LockScreenMonitorWidget::class.java))
            if (ids.isNotEmpty()) LockScreenMonitorWidget.updateWidget(context)
        } catch (t: Throwable) {
            android.util.Log.w("WidgetSnapshot", "write failed: ${t.message}")
        }
    }

    /** Today's saved score only — an older one shown under a fresh timestamp reads as current. */
    private fun todaysSavedScore(context: Context): Int? =
        HealthScoreUtils.getHealthScoreHistory(context, 1).lastOrNull()?.second

    /** BatteryManager reports tenths of a degree; anything outside a plausible range is unknown. */
    internal fun batteryTempLabel(tenthsC: Int): String? {
        if (tenthsC == Int.MIN_VALUE) return null
        val c = tenthsC / 10f
        return if (c in 1f..80f) c.toString() else null
    }

    /** getAvailableStorage() returns "<used> GB / <total> GB" — USED first, not available. */
    internal fun storageUsedTotal(s: String): String? {
        val m = Regex("(\\d+)\\s*GB\\s*/\\s*(\\d+)\\s*GB").find(s) ?: return null
        return "${m.groupValues[1]}/${m.groupValues[2]} GB"
    }
}
