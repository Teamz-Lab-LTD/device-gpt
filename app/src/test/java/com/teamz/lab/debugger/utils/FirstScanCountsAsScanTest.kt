package com.teamz.lab.debugger.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.ui.FirstScanGate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 2026-10-04: seconds after a new user's first scan, the Health tab said "Scanned: not yet ·
 * 0 scans · Run a scan to see today's device state". The first scan never reached
 * HealthScoreUtils, so the card that renders right after "See details" had no record of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstScanCountsAsScanTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `the first scan is recorded as today's daily scan`() {
        assertEquals(0, HealthScoreUtils.getTotalScans(context))

        val daily = runBlocking { FirstScanGate.recordDailyScan(context) }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        assertEquals(today, HealthScoreUtils.getLastScanDate(context))
        assertEquals(1, HealthScoreUtils.getTotalScans(context))
        assertTrue("daily score in 1..10, was $daily", daily != null && daily in 1..10)
        assertEquals(daily, HealthScoreUtils.getBestScore(context))
    }

    @Test
    fun `the gate screen records the daily scan before it shows the score`() {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        val src = File(dir, "app/src/main/java/com/teamz/lab/debugger/ui/FirstScanGateScreen.kt").readText()

        val record = src.indexOf("FirstScanGate.recordDailyScan(")
        val await = src.indexOf(".await()", record)
        val scored = src.indexOf("phase = Phase.SCORED.name")
        assertTrue("FirstScanGateScreen must call recordDailyScan", record >= 0)
        assertTrue("the daily scan must be awaited before the score is shown", await in (record + 1) until scored)
    }
}
