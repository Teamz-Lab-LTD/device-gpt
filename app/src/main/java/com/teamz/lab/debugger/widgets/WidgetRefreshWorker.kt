package com.teamz.lab.debugger.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * v3.2.0 R3 — widget freshness worker.
 *
 * The widget was previously refreshed only by SystemMonitorService (an FGS most
 * users never enable) — so for the majority the #1 surface showed stale data.
 * WorkManager 15-minute periodic is the PRIMARY freshness path; the "last
 * updated Xm ago" footer keeps staleness honest between runs.
 *
 * Cheap early-exit when no widget instances exist.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val awm = AppWidgetManager.getInstance(applicationContext)
            val ids = awm.getAppWidgetIds(
                ComponentName(applicationContext, LockScreenMonitorWidget::class.java)
            )
            if (ids.isNotEmpty()) {
                LockScreenMonitorWidget.updateWidget(applicationContext)
            }
            // R4 wake moment: piggyback the new-app diff on the periodic wake.
            // No-ops unless RC new_app_watchdog_enabled is true.
            com.teamz.lab.debugger.utils.NewAppWatchdog.checkNow(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "widget_refresh"

        /** Idempotent — call from Application.onCreate. */
        fun schedule(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                    .addTag(WORK_NAME)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (t: Throwable) {
                android.util.Log.w("WidgetRefreshWorker", "schedule failed: ${t.message}")
            }
        }
    }
}
