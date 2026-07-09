package com.teamz.lab.debugger.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.db.DeviceEventsRepository

/**
 * v3.2.0 R4 — new-app permission-review watchdog (2026-07-10 growth synthesis).
 *
 * Detects newly installed LAUNCHABLE apps by diffing the launcher-intent package
 * list at natural wake moments (cold start, widget refresh, charge worker).
 * Deliberately NOT QUERY_ALL_PACKAGES — the manifest <queries> MAIN/LAUNCHER
 * filter covers every app a user can open, with zero Play declaration needed.
 *
 * Policy rules baked in (Deceptive Behavior clearance 2026-07-10):
 *   - Notification is a "permission review" invite — verifiable facts only.
 *     NEVER a spyware/threat verdict. No urgency vocabulary.
 *   - Copy is time-agnostic ("installed since your last check") — the diff
 *     window is unknown, claiming "2h ago" would be invented precision.
 *   - Batched: one notification regardless of install count; max 1/day.
 *
 * RC-gated: `new_app_watchdog_enabled` (default OFF — dark-shipped).
 */
object NewAppWatchdog {

    private const val TAG = "NewAppWatchdog"
    private const val PREFS = "new_app_watchdog"
    private const val KEY_KNOWN_PACKAGES = "known_packages"
    private const val KEY_LAST_NOTIF_DAY = "last_notif_day"
    private const val CHANNEL_ID = "new_app_review"
    private const val NOTIFICATION_ID = 11_002
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Diff + (maybe) notify. Cheap (<10ms typical); safe to call from any wake
     * moment. All failures swallowed — the watchdog must never break its caller.
     */
    fun checkNow(context: Context) {
        try {
            if (!RemoteConfigUtils.isNewAppWatchdogEnabled()) return

            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val current = pm.queryIntentActivities(launcherIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
            if (current.isEmpty()) return // query failed — don't wipe the baseline

            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val known = p.getStringSet(KEY_KNOWN_PACKAGES, null)

            // First run: baseline only, never alert on the pre-existing app list.
            if (known == null) {
                p.edit { putStringSet(KEY_KNOWN_PACKAGES, current) }
                return
            }

            val fresh = current - known - context.packageName
            p.edit { putStringSet(KEY_KNOWN_PACKAGES, current) }
            if (fresh.isEmpty()) return

            // Journal every install into the timeline (R5) regardless of notification cap.
            fresh.forEach { pkg ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Throwable) { pkg }
                DeviceEventsRepository.recordAppInstalled(
                    context, pkg, "New app installed: $label"
                )
            }

            // Max one notification per day, batched.
            val today = (System.currentTimeMillis() / DAY_MS).toInt()
            if (p.getInt(KEY_LAST_NOTIF_DAY, -1) == today) return
            p.edit { putInt(KEY_LAST_NOTIF_DAY, today) }

            postReviewNotification(context, fresh)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "checkNow failed: ${t.message}")
        }
    }

    private fun postReviewNotification(context: Context, packages: Set<String>) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "New app review", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Facts about newly installed apps — permissions, installer source."
                    setShowBadge(false)
                }
            )
        }

        val pm = context.packageManager
        val firstLabel = packages.firstOrNull()?.let { pkg ->
            try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Throwable) { pkg }
        } ?: return
        val extra = packages.size - 1
        val body = if (extra > 0) {
            "$firstLabel and $extra more installed since your last check. Review what they can access."
        } else {
            "$firstLabel installed since your last check. Review what it can access."
        }

        val launchIntent = pm.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("from", "new_app_review")
        } ?: return
        val pi = PendingIntent.getActivity(
            context, NOTIFICATION_ID, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("📦 New app on this device")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notification)
            AnalyticsUtils.logEvent(
                AnalyticsEvent.NewAppReviewNotified,
                mapOf("new_apps" to packages.size)
            )
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — timeline rows still written.
        }
    }
}
