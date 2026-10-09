package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator.PowerStats
import com.teamz.lab.debugger.utils.PowerConsumptionUtils.PowerConsumptionSummary

/**
 * Power Consumption Achievements System
 * Tracks user achievements related to power monitoring and optimization
 */
object PowerAchievements {
    
    private const val PREFS_NAME = "power_achievements_prefs"
    private const val KEY_ACHIEVEMENTS = "unlocked_achievements"
    private const val KEY_EXPERIMENTS_COMPLETED = "experiments_completed"
    private const val KEY_POWER_MONITORING_STREAK = "monitoring_streak"
    private const val KEY_LAST_MONITORING_DATE = "last_monitoring_date"
    private const val KEY_TOTAL_MONITORING_SESSIONS = "total_sessions"
    private const val KEY_OPTIMIZATION_ACTIONS = "optimization_actions"
    
    /**
     * [title] and [description] are the English text. They stay as data: they are logged and
     * other code may still read them. Show [localizedTitle] / [localizedDescription] to people.
     */
    data class Achievement(
        val id: String,
        val title: String,
        val description: String,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        val icon: String,
        val category: Category
    ) {
        /** Title in the app language. Pass a context that carries it (see LocaleManager). */
        fun localizedTitle(context: Context): String = context.getString(titleRes)

        /** Description in the app language. */
        fun localizedDescription(context: Context): String = context.getString(descriptionRes)
    }
    
    enum class Category {
        MONITORING, EXPERIMENTS, OPTIMIZATION, RESEARCH
    }
    
    // Define all achievements
    val ALL_ACHIEVEMENTS = listOf(
        Achievement(
            id = "first_power_check",
            title = "Power Explorer",
            description = "Completed your first power consumption check",
            titleRes = R.string.pw_ach_first_power_check_title,
            descriptionRes = R.string.pw_ach_first_power_check_desc,
            icon = "🔋",
            category = Category.MONITORING
        ),
        Achievement(
            id = "monitoring_streak_7",
            title = "Power Monitor",
            description = "Monitored power consumption for 7 consecutive days",
            titleRes = R.string.pw_ach_monitoring_streak_7_title,
            descriptionRes = R.string.pw_ach_monitoring_streak_7_desc,
            icon = "📊",
            category = Category.MONITORING
        ),
        Achievement(
            id = "monitoring_streak_30",
            title = "Power Analyst",
            description = "Monitored power consumption for 30 consecutive days",
            titleRes = R.string.pw_ach_monitoring_streak_30_title,
            descriptionRes = R.string.pw_ach_monitoring_streak_30_desc,
            icon = "📈",
            category = Category.MONITORING
        ),
        Achievement(
            id = "experiment_camera",
            title = "Camera Researcher",
            description = "Completed camera power experiment",
            titleRes = R.string.pw_ach_experiment_camera_title,
            descriptionRes = R.string.pw_ach_experiment_camera_desc,
            icon = "📷",
            category = Category.EXPERIMENTS
        ),
        Achievement(
            id = "experiment_display",
            title = "Display Researcher",
            description = "Completed display power experiment",
            titleRes = R.string.pw_ach_experiment_display_title,
            descriptionRes = R.string.pw_ach_experiment_display_desc,
            icon = "📱",
            category = Category.EXPERIMENTS
        ),
        Achievement(
            id = "experiment_cpu",
            title = "CPU Researcher",
            description = "Completed CPU power experiment",
            titleRes = R.string.pw_ach_experiment_cpu_title,
            descriptionRes = R.string.pw_ach_experiment_cpu_desc,
            icon = "🧠",
            category = Category.EXPERIMENTS
        ),
        Achievement(
            id = "experiment_network",
            title = "Network Researcher",
            description = "Completed network power experiment",
            titleRes = R.string.pw_ach_experiment_network_title,
            descriptionRes = R.string.pw_ach_experiment_network_desc,
            icon = "📶",
            category = Category.EXPERIMENTS
        ),
        Achievement(
            id = "all_experiments",
            title = "Power Research Master",
            description = "Completed all power consumption experiments",
            titleRes = R.string.pw_ach_all_experiments_title,
            descriptionRes = R.string.pw_ach_all_experiments_desc,
            icon = "🎓",
            category = Category.EXPERIMENTS
        ),
        Achievement(
            id = "power_optimized",
            title = "Power Optimizer",
            description = "Reduced power consumption by following recommendations",
            titleRes = R.string.pw_ach_power_optimized_title,
            descriptionRes = R.string.pw_ach_power_optimized_desc,
            icon = "⚡",
            category = Category.OPTIMIZATION
        ),
        Achievement(
            id = "low_power_achieved",
            title = "Efficiency Expert",
            description = "Achieved low power consumption (< 3W)",
            titleRes = R.string.pw_ach_low_power_achieved_title,
            descriptionRes = R.string.pw_ach_low_power_achieved_desc,
            icon = "💚",
            category = Category.OPTIMIZATION
        ),
        Achievement(
            id = "csv_exported",
            title = "Data Exporter",
            description = "Exported power consumption data to CSV",
            titleRes = R.string.pw_ach_csv_exported_title,
            descriptionRes = R.string.pw_ach_csv_exported_desc,
            icon = "📤",
            category = Category.RESEARCH
        ),
        Achievement(
            id = "insights_viewed_10",
            title = "Insight Seeker",
            description = "Viewed power insights 10 times",
            titleRes = R.string.pw_ach_insights_viewed_10_title,
            descriptionRes = R.string.pw_ach_insights_viewed_10_desc,
            icon = "💡",
            category = Category.MONITORING
        )
    )
    
    /**
     * Get unlocked achievements for user
     */
    fun getUnlockedAchievements(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val achievementsJson = prefs.getString(KEY_ACHIEVEMENTS, "[]") ?: "[]"
        return try {
            // Simple JSON parsing for achievement IDs
            achievementsJson.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    /**
     * Unlock an achievement
     */
    fun unlockAchievement(context: Context, achievementId: String): Boolean {
        val unlocked = getUnlockedAchievements(context)
        if (unlocked.contains(achievementId)) {
            return false // Already unlocked
        }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newUnlocked = unlocked + achievementId
        prefs.edit {
            putString(KEY_ACHIEVEMENTS, newUnlocked.joinToString(",", "[", "]") { "\"$it\"" })
        }
        
        // Log achievement unlock
        AnalyticsUtils.logEvent(
            AnalyticsEvent.AchievementUnlocked,
            mapOf(
                "achievement_id" to achievementId,
                "category" to (ALL_ACHIEVEMENTS.find { it.id == achievementId }?.category?.name ?: "UNKNOWN")
            )
        )
        
        return true
    }
    
    /**
     * Check and unlock achievements based on current state
     */
    fun checkAchievements(
        context: Context,
        powerData: PowerConsumptionSummary?,
        aggregatedStats: PowerStats?
    ): List<Achievement> {
        val newlyUnlocked = mutableListOf<Achievement>()
        val unlocked = getUnlockedAchievements(context)
        
        // Check first power check
        if (powerData != null && !unlocked.contains("first_power_check")) {
            val achievement = ALL_ACHIEVEMENTS.find { it.id == "first_power_check" }
            if (achievement != null && unlockAchievement(context, achievement.id)) {
                newlyUnlocked.add(achievement)
            }
        }
        
        // Check low power achievement
        if (powerData != null && powerData.totalPower < 3000.0 && !unlocked.contains("low_power_achieved")) {
            val achievement = ALL_ACHIEVEMENTS.find { it.id == "low_power_achieved" }
            if (achievement != null && unlockAchievement(context, achievement.id)) {
                newlyUnlocked.add(achievement)
            }
        }
        
        // Check monitoring streak
        updateMonitoringStreak(context)
        val streak = getMonitoringStreak(context)
        if (streak >= 7 && !unlocked.contains("monitoring_streak_7")) {
            val achievement = ALL_ACHIEVEMENTS.find { it.id == "monitoring_streak_7" }
            if (achievement != null && unlockAchievement(context, achievement.id)) {
                newlyUnlocked.add(achievement)
            }
        }
        if (streak >= 30 && !unlocked.contains("monitoring_streak_30")) {
            val achievement = ALL_ACHIEVEMENTS.find { it.id == "monitoring_streak_30" }
            if (achievement != null && unlockAchievement(context, achievement.id)) {
                newlyUnlocked.add(achievement)
            }
        }
        
        return newlyUnlocked
    }
    
    /**
     * Record experiment completion
     */
    fun recordExperimentCompletion(context: Context, experimentType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val completed = getCompletedExperiments(context)
        val newCompleted = completed + experimentType
        
        prefs.edit {
            putStringSet(KEY_EXPERIMENTS_COMPLETED, newCompleted)
        }
        
        // Unlock experiment-specific achievements
        when (experimentType.lowercase()) {
            "camera" -> unlockAchievement(context, "experiment_camera")
            "display" -> unlockAchievement(context, "experiment_display")
            "cpu" -> unlockAchievement(context, "experiment_cpu")
            "network" -> unlockAchievement(context, "experiment_network")
        }
        
        // Check if all experiments completed
        if (newCompleted.size >= 4 && !getUnlockedAchievements(context).contains("all_experiments")) {
            unlockAchievement(context, "all_experiments")
        }
    }
    
    /**
     * Get completed experiments
     */
    fun getCompletedExperiments(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_EXPERIMENTS_COMPLETED, emptySet()) ?: emptySet()
    }
    
    /**
     * Update monitoring streak
     */
    private fun updateMonitoringStreak(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getLong(KEY_LAST_MONITORING_DATE, 0)
        val currentDate = System.currentTimeMillis()
        val currentDay = currentDate / (24 * 60 * 60 * 1000)
        val lastDay = if (lastDate > 0) lastDate / (24 * 60 * 60 * 1000) else -1
        
        if (currentDay > lastDay) {
            if (currentDay == lastDay + 1) {
                // Consecutive day
                val streak = prefs.getInt(KEY_POWER_MONITORING_STREAK, 0) + 1
                prefs.edit {
                    putInt(KEY_POWER_MONITORING_STREAK, streak)
                    putLong(KEY_LAST_MONITORING_DATE, currentDate)
                }
            } else {
                // Streak broken, reset
                prefs.edit {
                    putInt(KEY_POWER_MONITORING_STREAK, 1)
                    putLong(KEY_LAST_MONITORING_DATE, currentDate)
                }
            }
        }
    }
    
    /**
     * Get current monitoring streak
     */
    fun getMonitoringStreak(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_POWER_MONITORING_STREAK, 0)
    }
    
    /**
     * Record CSV export
     */
    fun recordCsvExport(context: Context) {
        unlockAchievement(context, "csv_exported")
    }
    
    /**
     * Record insight view
     */
    fun recordInsightView(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val views = prefs.getInt("insight_views", 0) + 1
        prefs.edit {
            putInt("insight_views", views)
        }
        
        if (views >= 10 && !getUnlockedAchievements(context).contains("insights_viewed_10")) {
            unlockAchievement(context, "insights_viewed_10")
        }
    }
}

