package com.teamz.lab.debugger.quality

import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.teamz.lab.debugger.utils.D1OvernightDrainWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBatteryManager

/**
 * Robolectric runtime tests for v3.1.11 D1OvernightDrainWorker.
 *
 * Source-text tests (D1OvernightDrainWorkerTest, D1OvernightDrainContractTest)
 * pin code shape. These tests pin BEHAVIOR:
 *   - scheduleOnFirstInstall actually enqueues a WorkRequest in WorkManager
 *   - KEY_WORK_SCHEDULED guard prevents double-enqueue on repeated calls
 *   - cancelIfPendingOrganicReturn cancels within the 20-hour window
 *   - cancelIfPendingOrganicReturn no-ops after the 20-hour window
 *
 * Uses WorkManagerTestInitHelper for in-process WorkManager + Robolectric for
 * SharedPreferences. Hermetic — no network, no real BatteryManager (Robolectric
 * returns 100% for BATTERY_PROPERTY_CAPACITY by default which satisfies the
 * "battery readable" guard).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class D1OvernightDrainRuntimeTest {

    private lateinit var context: Context
    private val workName = "d1_overnight_drain"
    private val prefsName = "d1_overnight_drain"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        // Wipe state between tests so we always start from "first install"
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        // Cancel any leftover work from previous test
        WorkManager.getInstance(context).cancelUniqueWork(workName).result.get()
        // Robolectric BatteryManager.BATTERY_PROPERTY_CAPACITY returns 0 by default;
        // D1Worker's readBatteryPctSafe rejects values outside 1..100, so the schedule
        // would otherwise no-op. Set a realistic level so the worker actually schedules.
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val shadow = Shadows.shadowOf(bm)
        shadow.setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 45)
    }

    @Test
    fun `scheduleOnFirstInstall enqueues exactly one ENQUEUED WorkRequest`() {
        D1OvernightDrainWorker.scheduleOnFirstInstall(context)

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get()
        assertEquals("Exactly one WorkRequest should be enqueued", 1, infos.size)
        assertEquals(
            "WorkInfo state should be ENQUEUED after scheduleOnFirstInstall",
            WorkInfo.State.ENQUEUED, infos[0].state
        )
    }

    @Test
    fun `scheduleOnFirstInstall persists the SharedPreferences guard flag`() {
        D1OvernightDrainWorker.scheduleOnFirstInstall(context)

        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        assertTrue(
            "KEY_WORK_SCHEDULED must be true after scheduleOnFirstInstall — without it " +
                "the idempotency short-circuit on second call fails.",
            prefs.getBoolean("work_scheduled", false)
        )
        assertTrue(
            "baseline_battery_pct must be persisted in 1..100 range (Robolectric returns 100).",
            prefs.getInt("baseline_battery_pct", -1) in 1..100
        )
        assertTrue(
            "baseline_ts must be persisted as a real epoch-ms timestamp.",
            prefs.getLong("baseline_ts", 0L) > 1_700_000_000_000L
        )
    }

    @Test
    fun `scheduleOnFirstInstall is idempotent on repeated calls`() {
        D1OvernightDrainWorker.scheduleOnFirstInstall(context)
        val firstBaselineTs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getLong("baseline_ts", 0L)

        // Wait a tick so a re-schedule would produce a different baseline_ts
        Thread.sleep(10)

        D1OvernightDrainWorker.scheduleOnFirstInstall(context)
        val secondBaselineTs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getLong("baseline_ts", 0L)

        assertEquals(
            "Second scheduleOnFirstInstall must no-op — baseline_ts must not change.",
            firstBaselineTs, secondBaselineTs
        )

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get()
        assertEquals("Repeated call must NOT create a second WorkRequest", 1, infos.size)
    }

    @Test
    fun `cancelIfPendingOrganicReturn no-ops when no work was scheduled`() {
        // Don't schedule first — cancel should silently return.
        D1OvernightDrainWorker.cancelIfPendingOrganicReturn(context)

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get()
        assertEquals("No work was scheduled — cancel must produce zero WorkInfos", 0, infos.size)
    }

    @Test
    fun `cancelIfPendingOrganicReturn cancels work within the 20-hour window`() {
        D1OvernightDrainWorker.scheduleOnFirstInstall(context)
        // Immediately call cancel — well within the 20-hour window
        D1OvernightDrainWorker.cancelIfPendingOrganicReturn(context)

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get()
        // Cancelled work shows state CANCELLED in test WorkManager
        assertTrue(
            "After cancel within window, WorkInfo state should be CANCELLED.",
            infos.all { it.state == WorkInfo.State.CANCELLED }
        )

        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        assertFalse(
            "KEY_WORK_SCHEDULED must reset to false after cancellation so the next install " +
                "detection works correctly.",
            prefs.getBoolean("work_scheduled", true)
        )
    }

    @Test
    fun `cancelIfPendingOrganicReturn no-ops after the 20-hour window`() {
        D1OvernightDrainWorker.scheduleOnFirstInstall(context)
        // Force baseline_ts to look like it was set 25 hours ago (past the cancel window)
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val twentyFiveHoursAgo = System.currentTimeMillis() - (25L * 60 * 60 * 1000L)
        prefs.edit().putLong("baseline_ts", twentyFiveHoursAgo).commit()

        D1OvernightDrainWorker.cancelIfPendingOrganicReturn(context)

        // KEY_WORK_SCHEDULED must remain true — out-of-window cancel is a no-op
        assertTrue(
            "Out-of-window cancel must NOT clear KEY_WORK_SCHEDULED.",
            prefs.getBoolean("work_scheduled", false)
        )
    }
}
