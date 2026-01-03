package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for PowerConsumptionAggregator
 * Ensures power data persistence and retrieval works correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PowerConsumptionAggregatorTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear any existing data
        PowerConsumptionAggregator.clearHistory(context)
        PowerConsumptionAggregator.clearCameraTestResults(context)
    }
    
    @Test
    fun testSaveAndLoadCameraTestResults() {
        // Create test results
        val testResults = listOf(
            PowerConsumptionUtils.CameraPowerTestResult(
                beforeCapture = 1.5,
                afterCapture = 2.0,
                powerDifference = 0.5,
                captureDuration = 1000L,
                timestamp = System.currentTimeMillis(),
                baselinePower = 1.5,
                previewPower = 1.7,
                capturePower = 2.0
            ),
            PowerConsumptionUtils.CameraPowerTestResult(
                beforeCapture = 1.6,
                afterCapture = 2.1,
                powerDifference = 0.5,
                captureDuration = 1100L,
                timestamp = System.currentTimeMillis() + 1000,
                baselinePower = 1.6,
                previewPower = 1.8,
                capturePower = 2.1
            )
        )
        
        // Save
        PowerConsumptionAggregator.saveCameraTestResults(context, testResults)
        
        // Load
        val loadedResults = PowerConsumptionAggregator.loadCameraTestResults(context)
        
        // Verify
        assertEquals("Should load 2 test results", 2, loadedResults.size)
        assertEquals("First result power difference should match", 0.5, loadedResults[0].powerDifference, 0.001)
        assertEquals("First result duration should match", 1000L, loadedResults[0].captureDuration)
        assertEquals("Second result power difference should match", 0.5, loadedResults[1].powerDifference, 0.001)
    }
    
    @Test
    fun testLoadEmptyCameraTestResults() {
        val loadedResults = PowerConsumptionAggregator.loadCameraTestResults(context)
        assertTrue("Should return empty list when no results saved", loadedResults.isEmpty())
    }
    
    @Test
    fun testFormatPower() {
        // Test different power values (based on actual implementation)
        assertEquals("Should format 2.5 W correctly", "2.5 W", PowerConsumptionAggregator.formatPower(2.5))
        assertEquals("Should format 0.5 W as mW", "500 mW", PowerConsumptionAggregator.formatPower(0.5))
        // 0.05 is < 0.1, so it goes to µW: 0.05 * 1000000 = 50000 µW
        assertEquals("Should format 0.05 W as µW", "50000 µW", PowerConsumptionAggregator.formatPower(0.05))
        assertEquals("Should format 0.005 W as µW", "5000 µW", PowerConsumptionAggregator.formatPower(0.005))
        // Test edge cases
        assertEquals("Should format 1.0 W correctly", "1.0 W", PowerConsumptionAggregator.formatPower(1.0))
        assertEquals("Should format 0.1 W as mW", "100 mW", PowerConsumptionAggregator.formatPower(0.1))
    }
    
    @Test
    fun testClearCameraTestResults() {
        // Save some results
        val testResults = listOf(
            PowerConsumptionUtils.CameraPowerTestResult(
                beforeCapture = 1.5,
                afterCapture = 2.0,
                powerDifference = 0.5,
                captureDuration = 1000L
            )
        )
        PowerConsumptionAggregator.saveCameraTestResults(context, testResults)
        
        // Clear
        PowerConsumptionAggregator.clearCameraTestResults(context)
        
        // Verify cleared
        val loadedResults = PowerConsumptionAggregator.loadCameraTestResults(context)
        assertTrue("Results should be cleared", loadedResults.isEmpty())
    }
}

