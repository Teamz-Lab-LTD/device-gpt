package com.teamz.lab.debugger.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    /**
     * Set the moment either path posts (or deliberately skips) the push, so the
     * WorkManager job and the AlarmManager broadcast can both be armed without
     * any risk of the user seeing the notification twice.
     */
    private const val KEY_OUTCOME_RECORDED = "outcome_recorded"
    /** One-shot guard so the WorkInfo post-mortem is reported once per install. */
    private const val KEY_POSTMORTEM_SENT = "postmortem_sent"
    private const val ALARM_REQUEST_CODE = 2027
    const val ACTION_D1_ALARM = "com.teamz.lab.debugger.D1_OVERNIGHT_DRAIN"
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
            // No content constraints on purpose: a constraint can only make the
            // job LESS likely to run, and the measured problem is that it does not
            // run at all. Backoff is set so a doWork() that returns retry() (RC
            // fetch timed out) comes back instead of resolving to "flag false"
            // forever, which is how the July fix could look applied and still
            // never push.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        armAlarmFallback(context)

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
        cancelAlarmFallback(context)
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

    // ---------------------------------------------------------------------
    // Redundant delivery path.
    //
    // Measured 2026-08-27 on 3.1.20 (GA4 481224245, the only cohort both past the
    // 20h delay and able to log worker_fired):
    //
    //     scheduled 36 = cancelled 20 + worker_fired 5 + UNACCOUNTED 11
    //
    // The 20 cancellations are correct — those users came back on their own. Of the
    // 16 jobs that were still eligible to run, only 5 ran. AlarmManager is a
    // different subsystem from the JobScheduler that WorkManager sits on, so an OEM
    // battery policy that drops one may not drop the other. Both are armed; the
    // first to arrive posts, the other no-ops on KEY_OUTCOME_RECORDED.
    //
    // setAndAllowWhileIdle, not setExactAndAllowWhileIdle: exact alarms need
    // SCHEDULE_EXACT_ALARM on Android 12+, which Play scrutinises and this app does
    // not need — "roughly 20 hours later" tolerates a Doze maintenance window.
    //
    // Deliberately NOT persisted across reboot: that needs RECEIVE_BOOT_COMPLETED,
    // and adding a permission to an app with two live Data Safety rejections is a
    // bad trade. WorkManager already survives reboot, so the two paths cover
    // different failure modes rather than the same one.
    // ---------------------------------------------------------------------

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_D1_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun armAlarmFallback(context: Context) {
        try {
            val am = context.getSystemService<AlarmManager>() ?: return
            val triggerAt = System.currentTimeMillis() + INITIAL_DELAY_HOURS * 60 * 60 * 1000L
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent(context))
            Log.i(TAG, "Armed AlarmManager fallback for +${INITIAL_DELAY_HOURS}h")
        } catch (t: Throwable) {
            Log.w(TAG, "armAlarmFallback failed: ${t.message}")
        }
    }

    private fun cancelAlarmFallback(context: Context) {
        try {
            context.getSystemService<AlarmManager>()?.cancel(alarmIntent(context))
        } catch (t: Throwable) {
            Log.w(TAG, "cancelAlarmFallback failed: ${t.message}")
        }
    }

    /**
     * Claim the single delivery slot. Returns true exactly once per install; every
     * later caller — the other delivery path — gets false and must do nothing.
     */
    /** What `deliver` should do once it knows whether Remote Config actually fetched. */
    internal enum class DeliveryDecision {
        /** Fetch failed and this path can retry — hand it back to WorkManager. */
        RETRY,
        /**
         * Fetch failed on a path with no retry. It must NOT claim the outcome slot: the
         * decision would rest on the bundled default rather than on the real flag, and
         * claiming it would lock the retrying path out of ever delivering.
         */
        BAIL_WITHOUT_CLAIMING,
        /** Config is trustworthy — claim the slot and act on it. */
        PROCEED,
    }

    /** Pure, so the interaction between the two delivery paths is testable without a device. */
    internal fun decideDelivery(fetched: Boolean, canRetry: Boolean): DeliveryDecision = when {
        fetched -> DeliveryDecision.PROCEED
        canRetry -> DeliveryDecision.RETRY
        else -> DeliveryDecision.BAIL_WITHOUT_CLAIMING
    }

    private fun claimOutcome(context: Context): Boolean {
        val p = prefs(context)
        synchronized(this) {
            if (p.getBoolean(KEY_OUTCOME_RECORDED, false)) return false
            p.edit { putBoolean(KEY_OUTCOME_RECORDED, true) }
            return true
        }
    }

    /**
     * The whole decide-and-post sequence, shared by the WorkManager worker and the
     * AlarmManager receiver so the two paths cannot drift apart.
     *
     * @return true if it ran to completion, false if the RC fetch failed and the
     *         caller should retry (WorkManager only — the alarm has no retry).
     */
    internal suspend fun deliver(ctx: Context, source: String, canRetry: Boolean): Boolean {
        AnalyticsUtils.init(ctx)
        RemoteConfigUtils.init()

        val fetched = RemoteConfigUtils.awaitD1OvernightDrainFetched()
        val gateResult = RemoteConfigUtils.isD1OvernightDrainEnabled()
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.D1OvernightDrainWorkerFired,
                mapOf("gate_result" to gateResult, "source" to source, "rc_fetched" to fetched)
            )
        } catch (_: Throwable) { /* analytics not critical */ }

        // A failed fetch used to fall through to the bundled default (false) and
        // return success, so the push was lost for good on one flaky network moment.
        //
        // 2026-09-09: the redundant alarm path defeated the retry that shipped beside it
        // in 295c533. With canRetry=false and a failed fetch it skipped the branch below,
        // CLAIMED the single outcome slot, read the bundled false, and posted nothing —
        // after which the worker's later successful fetch found the slot taken and
        // no-opped. Two delivery paths, and the unreliable one silently won.
        when (decideDelivery(fetched, canRetry)) {
            DeliveryDecision.RETRY -> {
                Log.w(TAG, "RC fetch failed — asking WorkManager to retry rather than defaulting to false")
                return false
            }
            DeliveryDecision.BAIL_WITHOUT_CLAIMING -> {
                Log.w(TAG, "RC fetch failed on a path that cannot retry ($source) — leaving the " +
                    "outcome slot unclaimed so the retrying path can still deliver")
                return true
            }
            DeliveryDecision.PROCEED -> Unit
        }
        if (!claimOutcome(ctx)) {
            Log.d(TAG, "Outcome already recorded by the other path — skipping ($source)")
            return true
        }
        if (!gateResult) {
            Log.d(TAG, "D1 flag false at fire time (A/B off or RC disabled) — skipping")
            return true
        }

        val p = prefs(ctx)
        val baseline = p.getInt(KEY_BASELINE_BATTERY_PCT, -1)
        if (baseline < 0) {
            Log.w(TAG, "Baseline missing — skipping")
            return true
        }
        val current = readBatteryPctSafe(ctx) ?: return true
        val drainPct = (baseline - current).coerceAtLeast(0)
        // The alarm path hands in a receiver context and the worker path an application context;
        // neither is sure to be in the language the person chose in the app.
        val localized = LocaleManager.localizedContext(ctx)
        val text = if (drainPct >= 1) {
            localized.getString(R.string.mx_notif_d1_drain, drainPct.toString())
        } else {
            localized.getString(R.string.mx_notif_d1_steady)
        }
        postNotification(ctx, text)
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.D1OvernightDrainPushed,
                mapOf(
                    "baseline_pct" to baseline,
                    "current_pct" to current,
                    "drain_pct" to drainPct,
                    "source" to source
                )
            )
        } catch (_: Throwable) { /* analytics not critical */ }
        return true
    }

    /**
     * Reports what WorkManager thinks happened to the unique work, once per install,
     * on the next app open after the delay has elapsed. `worker_fired` can only tell
     * us the job DID run; nothing today explains the 11 installs that neither
     * cancelled nor fired. This names them — CANCELLED, ENQUEUED (still waiting),
     * FAILED, or absent entirely (OEM force-stop wiped the JobScheduler entry).
     */
    fun reportDeliveryPostMortem(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_POSTMORTEM_SENT, false)) return
        val baselineTs = p.getLong(KEY_BASELINE_TS, 0L)
        if (baselineTs <= 0L) return
        val ageMs = System.currentTimeMillis() - baselineTs
        if (ageMs < INITIAL_DELAY_HOURS * 60 * 60 * 1000L) return  // delay not elapsed yet

        CoroutineScope(Dispatchers.IO).launch {
            val state = try {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WORK_NAME).get()
                    .firstOrNull()?.state?.name ?: "ABSENT"
            } catch (t: Throwable) {
                "LOOKUP_FAILED"
            }
            try {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.D1OvernightDrainPostMortem,
                    mapOf(
                        "work_state" to state,
                        "outcome_recorded" to p.getBoolean(KEY_OUTCOME_RECORDED, false),
                        "age_hours" to (ageMs / 1000 / 60 / 60).toInt()
                    )
                )
            } catch (_: Throwable) { /* analytics not critical */ }
            p.edit { putBoolean(KEY_POSTMORTEM_SENT, true) }
            Log.i(TAG, "D1 post-mortem: workState=$state age=${ageMs / 1000 / 60 / 60}h")
        }
    }

    /**
     * AlarmManager arm of the redundant delivery path. Uses goAsync() because the
     * Remote Config fetch inside deliver() can take seconds and onReceive() would
     * otherwise be torn down at the end of its synchronous body.
     */
    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_D1_ALARM) return
            val pending = goAsync()
            val ctx = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    deliver(ctx, source = "alarm", canRetry = false)
                } catch (t: Throwable) {
                    Log.w(TAG, "AlarmReceiver deliver failed: ${t.message}")
                } finally {
                    pending.finish()
                }
            }
        }
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
        val localized = LocaleManager.localizedContext(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.mx_notif_d1_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = localized.getString(R.string.mx_notif_d1_channel_desc)
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
            // Everything lives in the shared deliver() so this path and the
            // AlarmManager path cannot drift. Returning retry() on a failed RC
            // fetch is the behaviour change: the old code fell through to the
            // bundled default (false) and returned success, losing the push
            // permanently on a single bad network moment.
            val ok = try {
                deliver(applicationContext, source = "worker", canRetry = runAttemptCount < 3)
            } catch (t: Throwable) {
                Log.w(TAG, "deliver failed: ${t.message}")
                true
            }
            return if (ok) Result.success() else Result.retry()
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
