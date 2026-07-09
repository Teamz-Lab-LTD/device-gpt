package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.db.DeviceEvent
import com.teamz.lab.debugger.db.DeviceEventsRepository
import com.teamz.lab.debugger.db.DeviceGptDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v3.2.0 R5 — Robolectric runtime tests for the device_events store.
 *
 * Behavior pinned:
 *   - insert + windowed read round-trip
 *   - 90-day pruning removes old rows, keeps fresh ones
 *   - daily snapshot dedup: second call same day is a no-op
 *   - snapshot prefs shift prev <- last (the widget-delta source)
 *   - type-scoped counters (insight engine inputs)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceEventsRuntimeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("device_events_meta", Context.MODE_PRIVATE)
            .edit().clear().commit()
        // The DB singleton persists across tests in one JVM — empty the table
        // instead of deleting the file out from under the open connection.
        runBlocking { dao().prune(Long.MAX_VALUE) }
    }

    private fun dao() = DeviceGptDatabase.get(context).deviceEventDao()

    @Test
    fun `insert and windowed read round-trip`() = runBlocking {
        val now = System.currentTimeMillis()
        dao().insert(DeviceEvent(type = DeviceEvent.TYPE_SCORE_SCAN, timestamp = now, score = 82, label = "Device Score 82"))
        dao().insert(DeviceEvent(type = DeviceEvent.TYPE_CHARGE_SESSION, timestamp = now - 1000, label = "Charged 60% → 90% in 40m"))

        val window = dao().eventsSince(now - 60_000)
        assertEquals("Both events must be inside the window", 2, window.size)
        assertEquals("Newest first ordering", DeviceEvent.TYPE_SCORE_SCAN, window[0].type)
        assertEquals(82, window[0].score)
    }

    @Test
    fun `prune removes only rows older than the cutoff`() = runBlocking {
        val now = System.currentTimeMillis()
        dao().insert(DeviceEvent(type = DeviceEvent.TYPE_SCORE_SCAN, timestamp = now - 100L * 24 * 60 * 60 * 1000, score = 50))
        dao().insert(DeviceEvent(type = DeviceEvent.TYPE_SCORE_SCAN, timestamp = now, score = 90))

        val pruned = dao().prune(now - 90L * 24 * 60 * 60 * 1000)
        assertEquals("Exactly the 100-day-old row must be pruned", 1, pruned)
        assertEquals("Fresh row must survive", 1, dao().countAll())
    }

    @Test
    fun `daily snapshot dedups within the same day and shifts prev-last prefs`() {
        DeviceEventsRepository.recordDailySnapshotIfDue(context, 7)
        // Same day, different score — must be a NO-OP (dedup by calendar day).
        DeviceEventsRepository.recordDailySnapshotIfDue(context, 9)

        val (prev, last) = DeviceEventsRepository.snapshotDeltaFromPrefs(context)
        assertEquals("First-ever snapshot has no prev", -1, prev)
        assertEquals("last must hold the FIRST write of the day (dedup)", 7, last)

        // The widget delta contract: prev stays -1 until a second DAY's snapshot,
        // so v2 renders no delta line rather than a fake one.
        assertTrue("No fabricated delta on day one", prev !in 0..10)
    }

    @Test
    fun `type-scoped counters feed the insight engine correctly`() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        dao().insert(DeviceEvent(type = DeviceEvent.TYPE_CHARGE_SESSION, timestamp = now - 2 * day))
        dao().insert(DeviceEvent(type = DeviceEvent.TYPE_CHARGE_SESSION, timestamp = now - 10 * day))
        dao().insert(DeviceEvent(type = DeviceEvent.TYPE_APP_INSTALLED, timestamp = now - day, payload = "com.example"))

        assertEquals(
            "This week has exactly 1 charge session",
            1, DeviceEventsRepository.chargeCountSince(context, now - 7 * day)
        )
        assertEquals(
            "Prior week has exactly 1 charge session",
            1, DeviceEventsRepository.chargeCountBetween(context, now - 14 * day, now - 7 * day)
        )
        assertEquals(
            "One new app this week",
            1, DeviceEventsRepository.installCountSince(context, now - 7 * day)
        )
    }
}
