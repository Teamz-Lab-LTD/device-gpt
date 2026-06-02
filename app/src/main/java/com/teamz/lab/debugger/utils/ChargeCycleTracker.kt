package com.teamz.lab.debugger.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.teamz.lab.debugger.R

/**
 * Charge-cycle ritual hook (per 2026-06-02 synthesized habit-loop plan).
 *
 * Listens for ACTION_POWER_CONNECTED + ACTION_POWER_DISCONNECTED (these implicit
 * broadcasts are exempt from Android 8+ background-restriction list, so a manifest
 * receiver works even when the app is killed).
 *
 * On connect → snapshot battery % + timestamp to SharedPreferences.
 * On disconnect → compute delta + duration, log to EngagementTracker as
 * CHARGE_CYCLE_COMPLETED, post a low-importance notification with a plain-English
 * summary ("Charge complete: 38% → 100% in 2h 14m. 0 anomalies overnight.").
 *
 * Notification tap opens the app with a "from=charge_summary" Intent extra so
 * MainActivity can log CHARGE_SUMMARY_OPENED + jump straight to the health tab.
 */
object ChargeCycleTracker {

    private const val PREFS = "charge_cycle_tracker"
    private const val KEY_START_BATTERY = "start_battery_pct"
    private const val KEY_START_TIME = "start_time_ms"
    private const val KEY_LAST_SUMMARY_TIME = "last_summary_ms"

    private const val CHANNEL_ID = "charge_cycle_summary"
    private const val CHANNEL_NAME = "Charge cycle summary"
    private const val NOTIFICATION_ID = 11_001

    private const val TAG = "ChargeCycleTracker"

    /** Minimum charge delta to bother surfacing a summary. Skips trivial top-ups. */
    private const val MIN_CHARGE_DELTA_PCT = 5

    /** Minimum gap between summaries so a flaky cable doesn't spam notifications. */
    private const val MIN_GAP_BETWEEN_SUMMARIES_MS = 30L * 60L * 1000L // 30 min

    fun onPowerConnected(context: Context) {
        val battery = currentBatteryPercent(context) ?: return
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putInt(KEY_START_BATTERY, battery)
            .putLong(KEY_START_TIME, now)
            .apply()
        android.util.Log.d(TAG, "🔌 Power connected at $battery%")
    }

    fun onPowerDisconnected(context: Context) {
        val p = prefs(context)
        val startBattery = p.getInt(KEY_START_BATTERY, -1)
        val startTime = p.getLong(KEY_START_TIME, 0L)
        val endBattery = currentBatteryPercent(context) ?: return
        val now = System.currentTimeMillis()

        // Always clear the cached start state — a partial cycle still ends the previous one.
        p.edit().remove(KEY_START_BATTERY).remove(KEY_START_TIME).apply()

        if (startBattery < 0 || startTime <= 0L) {
            android.util.Log.d(TAG, "🔌 Power disconnected but no start state — skipping summary")
            return
        }

        val deltaPct = endBattery - startBattery
        val durationMs = now - startTime
        if (deltaPct < MIN_CHARGE_DELTA_PCT) {
            android.util.Log.d(TAG, "🔌 Charge cycle too small ($deltaPct%) — skipping summary")
            return
        }

        // Suppress back-to-back summaries.
        val lastSummary = p.getLong(KEY_LAST_SUMMARY_TIME, 0L)
        if (now - lastSummary < MIN_GAP_BETWEEN_SUMMARIES_MS) {
            android.util.Log.d(TAG, "🔌 Within suppression window — skipping summary")
            return
        }
        p.edit().putLong(KEY_LAST_SUMMARY_TIME, now).apply()

        // Log to EngagementTracker for habit-streak math.
        EngagementTracker.trackSignificantAction(
            context,
            SignificantAction.CHARGE_CYCLE_COMPLETED,
            mapOf(
                "start_pct" to startBattery,
                "end_pct" to endBattery,
                "delta_pct" to deltaPct,
                "duration_ms" to durationMs
            )
        )
        // Also log to Firebase Analytics so it shows up in GA4 dashboard alongside
        // monitor_notification_opened + charge_summary_opened — the full habit funnel.
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.ChargeCycleCompleted,
                mapOf(
                    "start_pct" to startBattery,
                    "end_pct" to endBattery,
                    "delta_pct" to deltaPct,
                    "duration_min" to (durationMs / 60_000L).toInt(),
                )
            )
        } catch (_: Exception) { /* analytics never blocks core flow */ }

        postSummaryNotification(context, startBattery, endBattery, durationMs)
    }

    private fun currentBatteryPercent(context: Context): Int? {
        val bm = context.getSystemService<BatteryManager>() ?: return null
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (pct in 0..100) pct else null
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun postSummaryNotification(
        context: Context,
        startPct: Int,
        endPct: Int,
        durationMs: Long
    ) {
        val nm = context.getSystemService<NotificationManager>() ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Plain-English summary after every charge cycle."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        // Pre-Android 13: notification posts without permission. 13+: silently no-ops if not granted.
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("from", "charge_summary")
            }
        val pi = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val hours = durationMs / (60L * 60L * 1000L)
        val mins = (durationMs / (60L * 1000L)) % 60L
        val durationText = when {
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }

        val body = "Charge complete: $startPct% → $endPct% in $durationText. " +
                "Tap to scan for overnight anomalies."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DeviceGPT charge summary")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
        if (pi != null) builder.setContentIntent(pi)
        val notification = builder.build()

        try {
            nm.notify(NOTIFICATION_ID, notification)
            android.util.Log.i(TAG, "📣 Posted charge summary: $startPct→$endPct ($durationText)")
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+ opt-in). Silently swallow.
            android.util.Log.d(TAG, "📣 Charge summary suppressed (no notification permission)")
        }
    }
}

