package com.teamz.lab.debugger.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.teamz.lab.debugger.MainActivity
import com.teamz.lab.debugger.R
import java.util.concurrent.TimeUnit

/**
 * D1 Overnight Drain Worker — v3.1.11 retention lever.
 *
 * Background: prior `RetentionNotificationManager` waited 3-7 days before firing
 * its first notification. Industry data shows utility apps lose 70%+ of users
 * between D0 and D1; waiting until D3 is past the cliff entirely.
 *
 * This worker is a ONE-SHOT WorkRequest enqueued from EngagementTracker.init()
 * on first install only. It fires ~20 hours after first install with a
 * personalized push:
 *
 *     "Your battery used 4% in the last 20 hours — tap to see what drained it."
 *
 * The 20-hour delay sits in the D1 sweet spot (post-overnight, pre-cliff).
 * Body uses the user's REAL device delta — never generic feature-talk.
 *
 * Cancel triggers:
 *   - User opens app organically before push fires (EngagementTracker.trackSession
 *     calls cancelD1IfOpened — the user returned without being prompted, no point
 *     pushing).
 *
 * Gated by Remote Config flag `d1_overnight_drain_enabled` (default false until
 * A/B test starts). Set to true in Firebase console to enable on next install.
 */
object D1OvernightDrainWorker {

    private const val TAG = "D1OvernightDrain"
    private const val PREFS = "d1_overnight_drain"
    private const val KEY_BASELINE_BATTERY_PCT = "baseline_battery_pct"
    private const val KEY_BASELINE_TS = "baseline_ts"
    private const val KEY_WORK_SCHEDULED = "work_scheduled"
    private const val WORK_NAME = "d1_overnight_drain"
    private const val CHANNEL_ID = "d1_overnight_drain"
    private const val NOTIFICATION_ID = 2026
    private const val INITIAL_DELAY_HOURS = 20L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Call ONCE from EngagementTracker.init() when first install is detected.
     * Captures the baseline battery % and enqueues the 20-hour worker.
     * Idempotent — repeated calls no-op after the first schedule.
     */
    fun scheduleOnFirstInstall(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_WORK_SCHEDULED, false)) {
            Log.d(TAG, "Already scheduled — skipping")
            return
        }
        // No schedule-time RC gate: bundled defaults read at first launch
        // (flag=false) BEFORE the network fetch completes (~5 min). A gate
        // here would lose the D1 lever for every user installing before RC
        // fetches. Worker re-checks the flag at fire time — if owner wants
        // to kill mid-flight, flip RC and worker exits silently when it runs.

        val baselinePct = readBatteryPctSafe(context)
        if (baselinePct == null) {
            Log.w(TAG, "Could not read baseline battery — skipping schedule")
            return
        }

        p.edit {
            putInt(KEY_BASELINE_BATTERY_PCT, baselinePct)
            putLong(KEY_BASELINE_TS, System.currentTimeMillis())
            putBoolean(KEY_WORK_SCHEDULED, true)
        }

        val request = OneTimeWorkRequestBuilder<Worker>()
            .setInitialDelay(INITIAL_DELAY_HOURS, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)

        Log.i(TAG, "Scheduled D1 overnight-drain push for +${INITIAL_DELAY_HOURS}h " +
            "(baseline=$baselinePct%; RC gate evaluated at fire time)")
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.D1OvernightDrainScheduled,
                mapOf("baseline_pct" to baselinePct, "delay_hours" to INITIAL_DELAY_HOURS.toInt())
            )
        } catch (_: Throwable) { /* analytics not critical */ }
    }

    /**
     * Call from EngagementTracker.trackSession when user opens app organically.
     * If the D1 push hasn't fired yet, cancel it — they came back on their own,
     * no need to prompt.
     */
    fun cancelIfPendingOrganicReturn(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_WORK_SCHEDULED, false)) return
        // Only cancel within the first 20-hour window; after that the worker has
        // either run already (no-op) or been auto-removed.
        val baselineTs = p.getLong(KEY_BASELINE_TS, 0L)
        val ageMs = System.currentTimeMillis() - baselineTs
        if (ageMs > INITIAL_DELAY_HOURS * 60 * 60 * 1000L) return
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        p.edit { putBoolean(KEY_WORK_SCHEDULED, false) }
        Log.i(TAG, "Cancelled D1 push — user returned organically at ${ageMs / 1000 / 60} min")
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.D1OvernightDrainCancelled,
                mapOf("cancel_at_min" to (ageMs / 1000 / 60).toInt())
            )
        } catch (_: Throwable) { /* analytics not critical */ }
    }

    /**
     * Call from MainActivity.onCreate / onNewIntent when launched via the D1
     * notification (Intent extra "from" == "d1_overnight_drain"). Emits the
     * push-opened event so the funnel between scheduled → pushed → opened is
     * complete. Without this we know how many users got the push but not how
     * many actually came back.
     */
    fun trackPushOpened(context: Context) {
        val p = prefs(context)
        val baselineTs = p.getLong(KEY_BASELINE_TS, 0L)
        val timeFromInstallMin = if (baselineTs > 0) {
            ((System.currentTimeMillis() - baselineTs) / 1000 / 60).toInt()
        } else -1
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.D1OvernightDrainOpened,
                mapOf("time_from_install_min" to timeFromInstallMin)
            )
        } catch (_: Throwable) { /* analytics not critical */ }
        Log.i(TAG, "D1 push OPENED at +${timeFromInstallMin}min from install")
    }

    private fun readBatteryPctSafe(context: Context): Int? = try {
        val bm = context.getSystemService<BatteryManager>() ?: return null
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 1..100 }
    } catch (t: Throwable) {
        Log.w(TAG, "readBatteryPct failed: ${t.message}")
        null
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Battery Overnight Drain",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "One-time notification ~20 hours after install showing your overnight battery drain."
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * WorkManager worker — fires ~20 hours after first install, reads current
     * battery %, computes delta vs baseline, posts the personalized push.
     */
    class Worker(appContext: Context, params: WorkerParameters) :
        CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            val ctx = applicationContext
            if (!RemoteConfigUtils.isD1OvernightDrainEnabled()) {
                Log.d(TAG, "RC flipped off between schedule and fire — skipping")
                return Result.success()
            }
            val p = prefs(ctx)
            val baseline = p.getInt(KEY_BASELINE_BATTERY_PCT, -1)
            if (baseline < 0) {
                Log.w(TAG, "Baseline missing — skipping")
                return Result.success()
            }
            val current = readBatteryPctSafe(ctx) ?: return Result.success()
            val drainPct = (baseline - current).coerceAtLeast(0)

            val text = if (drainPct >= 1) {
                "Your battery used $drainPct% in the last 20 hours — tap to see what drained it."
            } else {
                "Your battery is steady overnight — tap to see today's device health score."
            }
            postNotification(ctx, text)

            try {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.D1OvernightDrainPushed,
                    mapOf(
                        "baseline_pct" to baseline,
                        "current_pct" to current,
                        "drain_pct" to drainPct
                    )
                )
            } catch (_: Throwable) { /* analytics not critical */ }

            return Result.success()
        }

        private fun postNotification(ctx: Context, text: String) {
            ensureChannel(ctx)
            val openIntent = Intent(ctx, MainActivity::class.java).apply {
                putExtra("from", "d1_overnight_drain")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                ctx, NOTIFICATION_ID, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("DeviceGPT")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try {
                NotificationManagerCompatShim.notify(ctx, NOTIFICATION_ID, n)
            } catch (t: Throwable) {
                Log.w(TAG, "postNotification failed: ${t.message}")
            }
        }
    }
}

/**
 * Thin shim around NotificationManagerCompat.notify so the worker compiles
 * even on minSDK paths that don't yet check POST_NOTIFICATIONS permission.
 * Caller responsibility: prompt the user for permission before scheduling on
 * Android 13+.
 */
private object NotificationManagerCompatShim {
    fun notify(ctx: Context, id: Int, n: android.app.Notification) {
        androidx.core.app.NotificationManagerCompat.from(ctx).notify(id, n)
    }
}
