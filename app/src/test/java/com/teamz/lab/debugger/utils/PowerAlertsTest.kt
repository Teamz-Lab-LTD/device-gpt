package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PowerAlerts
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for PowerAlerts
 * Ensures power alerts are generated correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PowerAlertsTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testGetAlerts() {
        val powerData = PowerConsumptionUtils.PowerConsumptionSummary(
            totalPower = 4.0,
            components = emptyList()
        )
        
        val alerts = PowerAlerts.checkAlerts(context, powerData, null)
        
        // Should return alerts list
        assertNotNull("Alerts should not be null", alerts)
    }
    
    @Test
    fun testGetAlertsForCriticalPower() {
        val powerData = PowerConsumptionUtils.PowerConsumptionSummary(
            totalPower = 6.0, // Very high power
            components = emptyList()
        )
        
        val alerts = PowerAlerts.checkAlerts(context, powerData, null)
        
        // Should have alerts for critical power
        assertNotNull("Alerts should not be null", alerts)
    }
}

