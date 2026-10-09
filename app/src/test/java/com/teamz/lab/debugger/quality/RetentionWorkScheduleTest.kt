package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.teamz.lab.debugger.utils.RetentionNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Review 2026-10-09 I3: initializeRetentionNotifications runs on every process start (app open,
 * widget update, any worker wake). It re-enqueued its periodic jobs with REPLACE, which restarts
 * them with no delay, so the "weekly" report and the 2-day tip could fire on every start (only the
 * 1/day cap held them back). A process restart must keep the existing schedule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RetentionWorkScheduleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val jobs = listOf("weekly_report_work", "achievement_work", "milestone_work", "personalized_tip_work")

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    private fun info(name: String) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get().single()

    @Test fun `a process restart keeps the existing periodic jobs instead of restarting them`() {
        RetentionNotificationManager.initializeRetentionNotifications(context)
        val before = jobs.associateWith { info(it).id }
        RetentionNotificationManager.initializeRetentionNotifications(context)
        for (j in jobs) assertEquals("$j was replaced (timer reset) on restart", before[j], info(j).id)
    }

    // With no initial delay a periodic job runs its first period immediately (nextScheduleTime alone
    // hides this: after that first run it already points a week ahead).
    @Test fun `time-driven pushes do not fire on the first start`() {
        RetentionNotificationManager.initializeRetentionNotifications(context)
        assertTrue("weekly report must wait about a week",
            info("weekly_report_work").initialDelayMillis >= TimeUnit.DAYS.toMillis(6))
        assertTrue("tips must wait their 2-day period",
            info("personalized_tip_work").initialDelayMillis >= TimeUnit.DAYS.toMillis(1))
    }
}
