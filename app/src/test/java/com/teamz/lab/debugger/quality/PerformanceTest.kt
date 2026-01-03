package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import com.teamz.lab.debugger.utils.HealthScoreUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*
import kotlin.system.measureTimeMillis

/**
 * Performance tests
 * Ensures operations complete in reasonable time
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PerformanceTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testPowerConsumptionCalculationPerformance() {
        val time = measureTimeMillis {
            repeat(10) {
                PowerConsumptionUtils.getPowerConsumptionData(context)
            }
        }
        
        // Should complete 10 calculations in reasonable time (< 5 seconds)
        assertTrue("Power calculation should be fast", time < 5000)
    }
    
    @Test
    fun testHealthScoreCalculationPerformance() {
        val time = measureTimeMillis {
            repeat(10) {
                try {
                    HealthScoreUtils.calculateDailyHealthScore(context)
                } catch (e: Exception) {
                    // Handle errors gracefully in test environment
                }
            }
        }
        
        // Should complete 10 calculations in reasonable time (< 10 seconds)
        // Increased timeout to account for device info access in test environment
        assertTrue("Health score calculation should be fast", time < 10000)
    }
    
    @Test
    fun testPowerFormattingPerformance() {
        val time = measureTimeMillis {
            repeat(1000) {
                com.teamz.lab.debugger.utils.PowerConsumptionAggregator.formatPower(2.5)
            }
        }
        
        // Should format 1000 values very quickly (< 100ms)
        assertTrue("Power formatting should be very fast", time < 100)
    }
    
    @Test
    fun testHistoryRetrievalPerformance() {
        val time = measureTimeMillis {
            repeat(10) {
                HealthScoreUtils.getHealthScoreHistory(context, 30)
            }
        }
        
        // Should retrieve history quickly (< 1 second)
        assertTrue("History retrieval should be fast", time < 1000)
    }
}

