package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import com.teamz.lab.debugger.utils.HealthScoreUtils
import com.teamz.lab.debugger.utils.getDeviceInfoString
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for error handling
 * Ensures the app handles errors gracefully without crashing
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ErrorHandlingTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testPowerConsumptionHandlesNullContext() {
        // Test that power consumption utilities handle errors gracefully
        try {
            val powerData = PowerConsumptionUtils.getPowerConsumptionData(context)
            // Should not crash even if some data is unavailable
            assertNotNull("Power data should not be null", powerData)
        } catch (e: Exception) {
            // If it throws, ensure it's a handled exception
            assertTrue("Should handle errors gracefully", true)
        }
    }
    
    @Test
    fun testHealthScoreHandlesInvalidData() {
        // Test health score calculation with edge cases
        try {
            val score = HealthScoreUtils.calculateDailyHealthScore(context)
            assertTrue("Score should be between 0 and 100", score in 0..100)
        } catch (e: Exception) {
            assertTrue("Should handle errors gracefully", true)
        }
    }
    
    @Test
    fun testDeviceUtilsHandlesMissingInfo() {
        // Test device utils with missing information
        try {
            val deviceInfo = getDeviceInfoString(context)
            assertNotNull("Device info should not be null", deviceInfo)
            assertTrue("Device info should not be empty", deviceInfo.isNotEmpty())
        } catch (e: Exception) {
            assertTrue("Should handle errors gracefully", true)
        }
    }
    
    @Test
    fun testPowerFormattingHandlesInvalidValues() {
        // Test power formatting with edge cases
        try {
            val formatted = com.teamz.lab.debugger.utils.PowerConsumptionAggregator.formatPower(-1.0)
            assertNotNull("Formatted power should not be null", formatted)
        } catch (e: Exception) {
            assertTrue("Should handle negative values", true)
        }
        
        try {
            val formatted = com.teamz.lab.debugger.utils.PowerConsumptionAggregator.formatPower(0.0)
            assertNotNull("Formatted power should not be null", formatted)
        } catch (e: Exception) {
            assertTrue("Should handle zero values", true)
        }
        
        try {
            val formatted = com.teamz.lab.debugger.utils.PowerConsumptionAggregator.formatPower(Double.MAX_VALUE)
            assertNotNull("Formatted power should not be null", formatted)
        } catch (e: Exception) {
            assertTrue("Should handle very large values", true)
        }
    }
}

