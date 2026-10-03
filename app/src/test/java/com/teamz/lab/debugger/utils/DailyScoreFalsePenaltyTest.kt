package com.teamz.lab.debugger.utils

import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 2026-10-04: the Health tab showed a healthy phone "97 Excellent" (first scan) directly above
 * "5/10 Fair. Some improvements needed" (daily score). The daily score deducted points that no
 * fault caused:
 *   - battery: `contains("good")` against "Good ✅" — case mismatch, fell to `else -> 1`
 *   - storage: the text has no "good"/"available" in lowercase, fell to `else -> 1`
 *   - security: any ❌/⚠️ anywhere — "No admin set" (the normal state for a personal phone),
 *     SELinux unreadable by apps, "Unable to read mic/camera usage", and /etc/hosts flagged as
 *     a modified system file (it exists on every Android phone) — fell to -2
 * A healthy phone could not score above ~6. These tests pin the score to real faults only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyScoreFalsePenaltyTest {

    private fun score(
        batteryHealth: Int = BatteryManager.BATTERY_HEALTH_GOOD,
        storageFreePct: Int? = 40,
        thermal: String = "🔋 Battery: 31.0°C\n",
        ramUsage: String = "3000 MB / 8000 MB (37%)",
        storageUnencrypted: Boolean = false,
        rooted: Boolean = false,
        usbDebugging: Boolean = false,
    ) = HealthScoreUtils.scoreFromSignals(
        batteryHealth, storageFreePct, thermal, ramUsage, storageUnencrypted, rooted, usbDebugging,
    )

    @Test
    fun `a healthy phone scores 10`() {
        assertEquals(10, score())
    }

    @Test
    fun `unreadable battery and storage are unknowns, not faults`() {
        assertEquals(10, score(batteryHealth = -1, storageFreePct = null))
        assertEquals(10, score(batteryHealth = BatteryManager.BATTERY_HEALTH_UNKNOWN))
    }

    @Test
    fun `battery faults still deduct`() {
        assertEquals(9, score(batteryHealth = BatteryManager.BATTERY_HEALTH_COLD))
        assertEquals(8, score(batteryHealth = BatteryManager.BATTERY_HEALTH_DEAD))
        assertEquals(7, score(batteryHealth = BatteryManager.BATTERY_HEALTH_OVERHEAT))
    }

    @Test
    fun `low storage still deducts, on the same thresholds as the first scan`() {
        assertEquals(10, score(storageFreePct = 15))
        assertEquals(9, score(storageFreePct = 14))
        assertEquals(9, score(storageFreePct = 10))
        assertEquals(8, score(storageFreePct = 9))
    }

    @Test
    fun `real security faults still deduct`() {
        assertEquals(8, score(storageUnencrypted = true))
        assertEquals(8, score(rooted = true))
        assertEquals(9, score(usbDebugging = true))
    }

    @Test
    fun `thermal and RAM keep their existing checks`() {
        assertEquals(8, score(thermal = "hot"))
        assertEquals(9, score(ramUsage = "critical"))
    }

    @Test
    fun `everything wrong at once floors at 1`() {
        assertEquals(
            1,
            score(
                batteryHealth = BatteryManager.BATTERY_HEALTH_OVERHEAT,
                storageFreePct = 2,
                thermal = "overheating",
                ramUsage = "critical",
                storageUnencrypted = true,
                rooted = true,
                usbDebugging = true,
            ),
        )
    }

    @Test
    fun `the live calculation does not penalise a fault-free device`() {
        // Robolectric: battery GOOD sticky intent, nothing rooted, adb off. Before the fix the
        // text matching alone took this to 6 or lower.
        val context: Context = ApplicationProvider.getApplicationContext()
        val sticky = android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
            .putExtra(BatteryManager.EXTRA_TEMPERATURE, 310)
        context.sendStickyBroadcast(sticky)
        val live = runBlocking { HealthScoreUtils.calculateDailyHealthScore(context) }
        assertEquals(10, live)
    }
}
