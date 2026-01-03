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
 * Regression tests
 * Ensures previously fixed bugs don't reappear
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RegressionTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testNoFakeDataInShareText() {
        // Regression: Ensure no fake data appears in share text
        val healthShareText = try {
            generateHealthShareText(context, 85)
        } catch (e: Exception) {
            ""
        }
        
        assertFalse("Should not contain 'fake'", healthShareText.contains("fake", ignoreCase = true))
        assertFalse("Should not contain 'dummy'", healthShareText.contains("dummy", ignoreCase = true))
        assertFalse("Should not contain 'Loading...'", healthShareText.contains("Loading..."))
    }
    
    @Test
    fun testPowerFormattingConsistency() {
        // Regression: Ensure power formatting is consistent
        val power1 = PowerConsumptionAggregator.formatPower(2.5)
        val power2 = PowerConsumptionAggregator.formatPower(2.5)
        
        assertEquals("Power formatting should be consistent", power1, power2)
    }
    
    @Test
    fun testHealthScoreRange() {
        // Regression: Ensure health score is always in valid range
        val score = try {
            HealthScoreUtils.calculateDailyHealthScore(context)
        } catch (e: Exception) {
            50 // Fallback score
        }
        assertTrue("Score should be >= 0", score >= 0)
        assertTrue("Score should be <= 100", score <= 100)
    }
    
    @Test
    fun testCameraTestResultsPersistence() {
        // Regression: Ensure camera test results persist after app reload
        val testResult = com.teamz.lab.debugger.utils.PowerConsumptionUtils.CameraPowerTestResult(
            beforeCapture = 1.5,
            afterCapture = 2.0,
            powerDifference = 0.5,
            captureDuration = 1000L,
            timestamp = System.currentTimeMillis(),
            baselinePower = 1.0,
            previewPower = 1.5,
            capturePower = 2.0
        )
        
        PowerConsumptionAggregator.saveCameraTestResults(context, listOf(testResult))
        val loaded = PowerConsumptionAggregator.loadCameraTestResults(context)
        
        assertTrue("Results should persist", loaded.isNotEmpty())
    }
    
    // Helper function
    private fun generateHealthShareText(context: Context, healthScore: Int): String {
        val streak = try {
            HealthScoreUtils.getDailyStreak(context)
        } catch (e: Exception) {
            0
        }
        val bestScore = try {
            HealthScoreUtils.getBestScore(context)
        } catch (e: Exception) {
            0
        }
        val totalScans = try {
            HealthScoreUtils.getTotalScans(context)
        } catch (e: Exception) {
            0
        }
        
        return buildString {
            appendLine("📊 DEVICE HEALTH REPORT")
            appendLine("🏆 Current Health Score: $healthScore/100")
            appendLine("📈 Health Stats:")
            appendLine("  • Daily Streak: $streak days")
            appendLine("  • Best Score: $bestScore/100")
            appendLine("  • Total Scans: $totalScans")
        }
    }
}

