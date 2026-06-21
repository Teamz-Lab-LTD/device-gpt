package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.ui.FirstScanGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric runtime tests for v3.1.11 FirstScanGate.
 *
 * Source-text tests pin code shape. These tests pin BEHAVIOR:
 *   - currentState transitions correctly across the gate conditions
 *   - markCompleted persists the gate-once flag + score
 *   - computeQuickScore returns clamped 0..100 from BatteryManager
 *   - debugReset wipes state cleanly
 *   - getLastScanScore reads back what markCompleted wrote
 *
 * Hermetic — Robolectric BatteryManager returns BATTERY_PROPERTY_CAPACITY=100
 * by default so computeQuickScore returns its 95 (>= 90% bucket).
 *
 * NOTE: currentState() reads Firebase Remote Config which Robolectric can't
 * fully boot without the Firebase initializer. We test the gate-once
 * persistence + score logic which DON'T require RC. RC gating is
 * source-text-asserted in FirstScanGateTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstScanGateRuntimeTest {

    private lateinit var context: Context
    private val prefsName = "first_scan_gate"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `markCompleted persists first_scan_completed flag + score together`() {
        FirstScanGate.markCompleted(context, 87)

        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        assertTrue(
            "first_scan_completed must be true so the gate never re-prompts on next launch.",
            prefs.getBoolean("first_scan_completed", false)
        )
        assertEquals(
            "first_scan_score must be exactly the value passed to markCompleted.",
            87, prefs.getInt("first_scan_score", -1)
        )
        assertTrue(
            "first_scan_ts must be a real epoch-ms timestamp post-2024.",
            prefs.getLong("first_scan_ts", 0L) > 1_700_000_000_000L
        )
    }

    @Test
    fun `markCompleted clamps out-of-range scores to 0_100`() {
        FirstScanGate.markCompleted(context, 250)  // way out of range
        val high = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getInt("first_scan_score", -1)
        assertEquals("Score above 100 must be clamped to 100.", 100, high)

        // Wipe + try negative
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        FirstScanGate.markCompleted(context, -42)
        val low = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getInt("first_scan_score", -1)
        assertEquals("Negative score must be clamped to 0.", 0, low)
    }

    @Test
    fun `getLastScanScore reads back what markCompleted wrote`() {
        assertEquals(
            "Before any scan, getLastScanScore must return -1 (sentinel).",
            -1, FirstScanGate.getLastScanScore(context)
        )

        FirstScanGate.markCompleted(context, 73)
        assertEquals(
            "After markCompleted(73), getLastScanScore must return 73.",
            73, FirstScanGate.getLastScanScore(context)
        )
    }

    @Test
    fun `computeQuickScore returns a value in 0_100 range`() {
        val score = FirstScanGate.computeQuickScore(context)
        assertTrue(
            "computeQuickScore must return a value in 0..100 (got $score). " +
                "Robolectric BatteryManager returns 100% by default so we expect 95.",
            score in 0..100
        )
    }

    @Test
    fun `debugReset clears persisted score + gate-once flag`() {
        FirstScanGate.markCompleted(context, 91)
        // Sanity: flag is set
        assertTrue(
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getBoolean("first_scan_completed", false)
        )

        FirstScanGate.debugReset(context)

        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        assertEquals(
            "After debugReset, first_scan_completed must be false (default).",
            false, prefs.getBoolean("first_scan_completed", false)
        )
        assertEquals(
            "After debugReset, getLastScanScore must return -1.",
            -1, FirstScanGate.getLastScanScore(context)
        )
    }

    @Test
    fun `markCompleted is idempotent on repeated calls (last write wins)`() {
        FirstScanGate.markCompleted(context, 50)
        assertEquals(50, FirstScanGate.getLastScanScore(context))

        // Second scan with different score (would happen after debugReset in practice,
        // but markCompleted itself should be safe to call again as last-write-wins).
        FirstScanGate.markCompleted(context, 88)
        assertEquals(
            "Last markCompleted call must win on score.",
            88, FirstScanGate.getLastScanScore(context)
        )
    }
}
