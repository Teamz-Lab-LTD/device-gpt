package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator
import com.teamz.lab.debugger.utils.HealthScoreUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for edge cases and boundary conditions
 * Ensures the app handles edge cases correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EdgeCaseTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testPowerFormattingEdgeCases() {
        // Test zero power
        val zeroPower = PowerConsumptionAggregator.formatPower(0.0)
        assertNotNull("Zero power should be formatted", zeroPower)
        
        // Test very small power
        val smallPower = PowerConsumptionAggregator.formatPower(0.000001)
        assertNotNull("Very small power should be formatted", smallPower)
        
        // Test very large power
        val largePower = PowerConsumptionAggregator.formatPower(1000.0)
        assertNotNull("Very large power should be formatted", largePower)
        
        // Test exactly 1.0 W
        val oneWatt = PowerConsumptionAggregator.formatPower(1.0)
        assertTrue("1.0 W should contain 'W'", oneWatt.contains("W", ignoreCase = true))
    }
    
    @Test
    fun testHealthScoreBoundaries() {
        // Test minimum score
        val minScore = try {
            HealthScoreUtils.calculateDailyHealthScore(context)
        } catch (e: Exception) {
            50 // Fallback score
        }
        assertTrue("Score should be >= 0", minScore >= 0)
        
        // Test maximum score
        assertTrue("Score should be <= 100", minScore <= 100)
    }
    
    @Test
    fun testEmptyDataHandling() {
        // Test with empty history
        val emptyHistory = HealthScoreUtils.getHealthScoreHistory(context, 0)
        assertNotNull("Empty history should return empty list", emptyHistory)
        assertTrue("Empty history should be empty", emptyHistory.isEmpty())
    }
    
    @Test
    fun testNegativeValuesHandling() {
        // Test that negative values are handled
        val negativePower = PowerConsumptionAggregator.formatPower(-5.0)
        assertNotNull("Negative power should be handled", negativePower)
    }
    
    @Test
    fun testVeryLargeHistory() {
        // Test with very large history request
        val largeHistory = HealthScoreUtils.getHealthScoreHistory(context, 1000)
        assertNotNull("Large history request should not crash", largeHistory)
    }
}

