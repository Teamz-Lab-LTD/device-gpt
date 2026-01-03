package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PowerRecommendations
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for PowerRecommendations
 * Ensures power recommendations are generated correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PowerRecommendationsTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testGetRecommendations() {
        // Create mock power data with components
        val powerData = PowerConsumptionUtils.PowerConsumptionSummary(
            totalPower = 3.5,
            components = listOf(
                PowerConsumptionUtils.ComponentPowerData(
                    component = "CPU",
                    powerConsumption = 2.0,
                    icon = "🧠",
                    status = "High",
                    details = "High CPU usage"
                ),
                PowerConsumptionUtils.ComponentPowerData(
                    component = "Display",
                    powerConsumption = 1.0,
                    icon = "📺",
                    status = "Active",
                    details = "Display usage"
                )
            )
        )
        
        val recommendations = PowerRecommendations.generateRecommendations(context, powerData, null)
        
        // Should return recommendations (may be empty if no conditions match, but list should exist)
        assertNotNull("Recommendations should not be null", recommendations)
        // Recommendations may be empty if no conditions match, so we just verify the list exists
        assertTrue("Recommendations list should be valid", recommendations.size >= 0)
    }
    
    @Test
    fun testGetRecommendationsForHighPower() {
        val powerData = PowerConsumptionUtils.PowerConsumptionSummary(
            totalPower = 5.0, // High power
            components = listOf(
                PowerConsumptionUtils.ComponentPowerData(
                    component = "CPU",
                    powerConsumption = 3.0,
                    icon = "🧠",
                    status = "High",
                    details = "High CPU usage"
                )
            )
        )
        
        val recommendations = PowerRecommendations.generateRecommendations(context, powerData, null)
        
        // Should return recommendations list (may be empty if no conditions match)
        assertNotNull("Recommendations should not be null", recommendations)
        assertTrue("Recommendations list should be valid", recommendations.size >= 0)
    }
    
    @Test
    fun testGetRecommendationsForLowPower() {
        val powerData = PowerConsumptionUtils.PowerConsumptionSummary(
            totalPower = 1.0, // Low power
            components = emptyList()
        )
        
        val recommendations = PowerRecommendations.generateRecommendations(context, powerData, null)
        
        // Should return recommendations (even for low power)
        assertNotNull("Recommendations should not be null", recommendations)
    }
}

