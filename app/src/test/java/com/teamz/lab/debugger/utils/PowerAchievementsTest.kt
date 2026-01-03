package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PowerAchievements
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for PowerAchievements
 * Ensures achievements system works correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PowerAchievementsTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear achievements
        val prefs = context.getSharedPreferences("power_achievements_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
    
    @Test
    fun testGetAllAchievements() {
        val achievements = PowerAchievements.ALL_ACHIEVEMENTS
        
        // Should return list of achievements
        assertNotNull("Achievements should not be null", achievements)
        assertTrue("Should have achievements", achievements.isNotEmpty())
    }
    
    @Test
    fun testGetUnlockedAchievements() {
        val unlocked = PowerAchievements.getUnlockedAchievements(context)
        
        // Should return set of unlocked achievement IDs
        assertNotNull("Unlocked achievements should not be null", unlocked)
    }
    
    @Test
    fun testUnlockAchievement() {
        val achievementId = "first_power_check"
        val unlocked = PowerAchievements.unlockAchievement(context, achievementId)
        
        // Should unlock achievement
        assertTrue("Should unlock achievement", unlocked)
        
        // Verify it's unlocked
        val unlockedSet = PowerAchievements.getUnlockedAchievements(context)
        assertTrue("Achievement should be in unlocked set", unlockedSet.contains(achievementId))
    }
    
    @Test
    fun testGetMonitoringStreak() {
        val streak = PowerAchievements.getMonitoringStreak(context)
        
        // Should return streak (>= 0)
        assertTrue("Streak should be >= 0", streak >= 0)
    }
}

