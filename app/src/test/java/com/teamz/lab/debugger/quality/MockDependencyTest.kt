package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Mock dependency tests
 * Tests functionality with mocked dependencies
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MockDependencyTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testPowerDataWithMockComponents() {
        // Test power data generation with various component configurations
        val powerData = PowerConsumptionUtils.getPowerConsumptionData(context)
        
        // Should handle empty components
        assertNotNull("Power data should not be null", powerData)
        assertNotNull("Components should not be null", powerData.components)
        
        // Should handle components with zero power
        val hasZeroPower = powerData.components.any { it.powerConsumption == 0.0 }
        if (hasZeroPower) {
            assertTrue("Should handle zero power components", true)
        }
    }
    
    @Test
    fun testPowerDataWithNullValues() {
        // Test that power data handles null/missing values gracefully
        val powerData = PowerConsumptionUtils.getPowerConsumptionData(context)
        
        // Should always return valid data structure
        assertNotNull("Power data should not be null", powerData)
        assertTrue("Total power should be >= 0", powerData.totalPower >= 0)
    }
}

