package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator
import com.teamz.lab.debugger.utils.PowerStrings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The power tab keeps component names, statuses, signal grades and count labels in English as
 * data and translates them only on display, through [PowerStrings]. In English the displayed
 * text must be exactly the data value, so English users see what they saw before, and a value
 * the table does not know must be shown as it is rather than dropped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PowerStringsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `component names read the same in English`() {
        listOf("Battery", "CPU", "Camera", "RAM", "Display", "WiFi", "Audio", "GPS", "Bluetooth", "NFC", "Cellular")
            .forEach { assertEquals(it, PowerStrings.component(context, it)) }
    }

    @Test
    fun `statuses read the same in English, including the ones built from numbers`() {
        listOf(
            "Charging", "Discharging", "Full", "Not Charging", "Unknown", "Error", "On", "Off", "Connected",
            "Enabled", "Disabled", "Permission required", "Music Active", "Speakerphone", "Bluetooth Audio",
            "Idle", "GPS + Network", "GPS Only", "Network Only", "Not supported", "Not available",
            "Discovering", "Active (Root)", "Active (System)", "Active (Detected)", "Estimated",
            "Unknown (Permission required)", "3/8 cores active", "47% used",
        ).forEach { assertEquals(it, PowerStrings.status(context, it)) }
    }

    @Test
    fun `an unknown value is shown as it is`() {
        assertEquals("4G LTE", PowerStrings.status(context, "4G LTE"))
        assertEquals("Thermal", PowerStrings.component(context, "Thermal"))
        assertEquals("Superb", PowerStrings.signal(context, "Superb"))
        assertEquals("2 widgets", PowerStrings.countLabel(context, 2, "widgets"))
    }

    @Test
    fun `signal grades, trends and count labels read the same in English`() {
        listOf("Excellent", "Very Good", "Very good", "Good", "Fair", "Weak", "No WiFi")
            .forEach { assertEquals(it, PowerStrings.signal(context, it)) }
        PowerConsumptionAggregator.PowerTrend.values()
            .forEach { assertEquals(it.name, PowerStrings.trend(context, it)) }
        assertEquals("3 tests", PowerStrings.countLabel(context, 3, "tests"))
        assertEquals("1 test", PowerStrings.countLabel(context, 1, "test"))
        assertEquals("5 levels tested", PowerStrings.countLabel(context, 5, "levels tested"))
        assertEquals("1 sample", PowerStrings.countLabel(context, 1, "sample"))
    }

    @Test
    fun `durations keep singular and plural`() {
        assertEquals("1 hour", PowerStrings.hours(context, 1))
        assertEquals("2 hours 1 minute", PowerStrings.hoursMinutes(context, 2, 1))
        assertEquals("45 minutes", PowerStrings.minutes(context, 45))
    }
}
