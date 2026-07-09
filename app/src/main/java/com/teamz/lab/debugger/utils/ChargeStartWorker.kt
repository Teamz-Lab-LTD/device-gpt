package com.teamz.lab.debugger.utils

import android.content.Context
import android.os.BatteryManager
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * v3.2.0 R2 delivery path 2 — charge-start detection when the app process is dead.
 *
 * ACTION_POWER_CONNECTED manifest receivers don't fire on API 26+, so WorkManager
 * with setRequiresCharging(true) is the only OS-sanctioned wake on charge. The OS
 * runs this worker when the charging constraint becomes satisfied; the worker
 * snapshots the charge start (if the runtime receiver hasn't already). The
 * disconnect side is handled by the runtime receiver (process alive) or
 * reconcileOnAppOpen (process dead).
 *
 * No foreground service, no exact alarms — nothing on the Play policy radar.
 */
class ChargeStartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val bm = applicationContext.getSystemService<BatteryManager>()
            if (bm != null && bm.isCharging) {
                val p = applicationContext.getSharedPreferences("charge_cycle_tracker", Context.MODE_PRIVATE)
                // Only snapshot if the runtime receiver didn't already catch this session.
                if (p.getLong("start_time_ms", 0L) == 0L) {
                    ChargeCycleTracker.onPowerConnected(applicationContext)
                }
            }
            // R4 wake moment: piggyback the new-app diff on the charge wake.
            NewAppWatchdog.checkNow(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "charge_start_detect"

        /** Idempotent — call from Application.onCreate. */
        fun schedule(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<ChargeStartWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder().setRequiresCharging(true).build()
                    )
                    .addTag(WORK_NAME)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (t: Throwable) {
                android.util.Log.w("ChargeStartWorker", "schedule failed: ${t.message}")
            }
        }
    }
}
