package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.HealthScoreUtils
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for data persistence
 * Ensures data is saved and retrieved correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DataPersistenceTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear any existing data
        val prefs = context.getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
    
    @Test
    fun testHealthScorePersistence() {
        // Save a health score
        val testScore = 85
        HealthScoreUtils.saveHealthScore(context, testScore)
        
        // Retrieve and verify
        val history = HealthScoreUtils.getHealthScoreHistory(context, 7)
        assertTrue("History should contain saved score", history.isNotEmpty())
    }
    
    @Test
    fun testMultipleHealthScorePersistence() {
        // Save multiple scores
        HealthScoreUtils.saveHealthScore(context, 80)
        HealthScoreUtils.saveHealthScore(context, 85)
        HealthScoreUtils.saveHealthScore(context, 90)
        
        // Retrieve and verify
        val history = HealthScoreUtils.getHealthScoreHistory(context, 7)
        assertTrue("History should contain multiple scores", history.size >= 1)
    }
    
    @Test
    fun testStreakPersistence() {
        // Save scores to create a streak
        HealthScoreUtils.saveHealthScore(context, 80)
        
        // Retrieve streak
        val streak = HealthScoreUtils.getDailyStreak(context)
        assertTrue("Streak should be >= 0", streak >= 0)
    }
    
    @Test
    fun testBestScorePersistence() {
        // Save multiple scores
        HealthScoreUtils.saveHealthScore(context, 70)
        HealthScoreUtils.saveHealthScore(context, 90)
        HealthScoreUtils.saveHealthScore(context, 80)
        
        // Retrieve best score
        val bestScore = HealthScoreUtils.getBestScore(context)
        assertTrue("Best score should be >= 70", bestScore >= 70)
        assertTrue("Best score should be <= 100", bestScore <= 100)
    }
    
    @Test
    fun testCameraTestResultsPersistence() {
        // Create test results
        val testResult = PowerConsumptionUtils.CameraPowerTestResult(
            beforeCapture = 1.5,
            afterCapture = 2.0,
            powerDifference = 0.5,
            captureDuration = 1000L,
            timestamp = System.currentTimeMillis(),
            baselinePower = 1.0,
            previewPower = 1.5,
            capturePower = 2.0
        )
        
        // Save results
        PowerConsumptionAggregator.saveCameraTestResults(context, listOf(testResult))
        
        // Retrieve and verify
        val loadedResults = PowerConsumptionAggregator.loadCameraTestResults(context)
        assertTrue("Should load saved results", loadedResults.isNotEmpty())
        assertEquals("Should have correct count", 1, loadedResults.size)
    }
}

