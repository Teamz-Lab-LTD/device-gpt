package com.teamz.lab.debugger.utils

/**
 * Optimized data models for leaderboard
 * Minimal fields only - for cost reduction
 */

/**
 * Leaderboard entry - minimal fields only
 */
data class LeaderboardEntry(
    // Identification (required)
    val userId: String = "",
    val normalizedDeviceId: String = "", // For grouping
    val hardwareId: String = "", // Unique device ID (cannot be changed by user)
    val timestamp: Long = 0L,
    
    // Device info (minimal)
    val normalizedBrand: String = "",
    val normalizedModel: String = "",
    val displayName: String = "", // User-friendly name
    val androidVersion: String = "", // For filtering
    
    // Scores only (no raw data)
    val scores: Map<String, Double> = emptyMap(), // category -> score
    
    // Trust indicators
    val dataQuality: Int = 0, // 1-5 based on data completeness
    val measurementCount: Int = 0, // How many measurements contributed
    val lastMeasurementDate: Long = 0L
)

/**
 * Aggregated device insights (pre-calculated)
 */
data class DeviceInsight(
    val normalizedDeviceId: String = "",
    val displayName: String = "",
    val normalizedBrand: String = "",
    val scores: Map<String, Double> = emptyMap(), // category -> avg score
    val userCount: Int = 0,
    val dataQuality: Int = 0,
    val trustLevel: String = "Low", // "Verified", "High", "Medium", "Low"
    val lastUpdated: Long = 0L
)

/**
 * Best device entry for aggregation
 */
data class BestDeviceEntry(
    val normalizedDeviceId: String,
    val displayName: String,
    val normalizedBrand: String,
    val avgScore: Double,
    val userCount: Int,
    val topScore: Double,
    val dataQuality: Int
)

/**
 * Internal data structures for score calculation (not stored in Firestore)
 */
data class PowerEfficiencyData(
    val avgPowerConsumption: Double,
    val powerTrend: String, // INCREASING/DECREASING/STABLE
    val peakPower: Double,
    val minPower: Double,
    val componentBreakdown: Map<String, Double>,
    val efficiencyRating: String,
    val score: Double
) {
    companion object {
        fun empty() = PowerEfficiencyData(
            avgPowerConsumption = 0.0,
            powerTrend = "UNKNOWN",
            peakPower = 0.0,
            minPower = 0.0,
            componentBreakdown = emptyMap(),
            efficiencyRating = "Unknown",
            score = 0.0
        )
    }
}

data class CpuPerformanceData(
    val microbenchResults: List<PowerConsumptionUtils.CpuBenchPoint>,
    val avgDeltaPower: Double,
    val cpuEfficiency: Double,
    val realTimeFrequencies: Map<Int, Int>,
    val activeCores: Int,
    val idleCores: Int,
    val usagePercent: Int,
    val score: Double
)

data class CameraEfficiencyData(
    val energyPerPhoto: Double, // Joules
    val avgPowerConsumption: Double,
    val baselinePower: Double,
    val previewPower: Double,
    val capturePower: Double,
    val testCount: Int,
    val lastTestDate: Long,
    val score: Double
) {
    companion object {
        fun empty() = CameraEfficiencyData(
            energyPerPhoto = 0.0,
            avgPowerConsumption = 0.0,
            baselinePower = 0.0,
            previewPower = 0.0,
            capturePower = 0.0,
            testCount = 0,
            lastTestDate = 0L,
            score = 0.0
        )
    }
}

data class DisplayEfficiencyData(
    val powerSweepResults: List<PowerConsumptionUtils.DisplayPowerPoint>,
    val avgPowerPerBrightness: Double,
    val optimalBrightness: Int,
    val displayEfficiency: Double,
    val score: Double
) {
    companion object {
        fun empty() = DisplayEfficiencyData(
            powerSweepResults = emptyList(),
            avgPowerPerBrightness = 0.0,
            optimalBrightness = 50,
            displayEfficiency = 0.0,
            score = 0.0
        )
    }
}

data class HealthScoreData(
    val currentScore: Int,
    val bestScore: Int,
    val streak: Int,
    val totalScans: Int,
    val trend: String,
    val score: Double
)

data class PowerTrendData(
    val trend: String,
    val trendStrength: Double,
    val daysTracked: Int,
    val improvementPercent: Double,
    val score: Double
)

data class ComponentOptimizationData(
    val topConsumers: List<PowerConsumptionAggregator.ComponentPowerStats>,
    val maxComponentPower: Double,
    val balanceScore: Double,
    val componentBreakdown: Map<String, Double>,
    val score: Double
)

data class ThermalEfficiencyData(
    val cpuTemperature: Float?,
    val batteryTemperature: Float?,
    val gpuTemperature: Float?,
    val thermalState: String,
    val thermalZoneTemperatures: Map<String, Float>,
    val avgTemperature: Double,
    val score: Double
)

data class PerformanceConsistencyData(
    val avgFPS: Int,
    val frameDropRate: Double,
    val consistencyScore: Double,
    val score: Double
)

data class UserEngagementData(
    val dailyStreak: Int,
    val totalScans: Int,
    val avgScanFrequency: Double,
    val score: Double
)

/**
 * Leaderboard category enum
 */
enum class LeaderboardCategory(
    val id: String,
    val displayName: String,
    val icon: String,
    val description: String,
    val childFriendlyExplanation: String
) {
    POWER_EFFICIENCY(
        "power_efficiency",
        "Battery Saver",
        "🔋",
        "Which phones use the least battery?",
        "Like a car that uses less gas - these phones save battery!"
    ),
    CPU_PERFORMANCE(
        "cpu_performance",
        "Speed Champion",
        "⚡",
        "Which phones are the fastest?",
        "Like a race car - these phones are super fast!"
    ),
    CAMERA_EFFICIENCY(
        "camera_efficiency",
        "Photo Saver",
        "📸",
        "Which phones take photos without using much battery?",
        "Like taking photos without draining your battery!"
    ),
    DISPLAY_EFFICIENCY(
        "display_efficiency",
        "Bright Screen",
        "💡",
        "Which phones have bright screens that don't use much battery?",
        "Like a bright light that doesn't use much electricity!"
    ),
    HEALTH_SCORE(
        "health_score",
        "Healthy Phone",
        "🏥",
        "Which phones are in the best health?",
        "Like a health checkup - these phones are doing great!"
    ),
    POWER_TREND(
        "power_trend",
        "Getting Better",
        "📈",
        "Which phones are using less battery over time?",
        "These phones are getting better at saving battery!"
    ),
    COMPONENT_OPTIMIZATION(
        "component_optimization",
        "Balanced Power",
        "⚖️",
        "Which phones use power evenly across all parts?",
        "Like a balanced meal - these phones use power wisely!"
    ),
    THERMAL_EFFICIENCY(
        "thermal_efficiency",
        "Cool Phone",
        "❄️",
        "Which phones stay cool?",
        "These phones don't get hot even when working hard!"
    ),
    PERFORMANCE_CONSISTENCY(
        "performance_consistency",
        "Smooth Runner",
        "🎮",
        "Which phones run smoothly without lag?",
        "These phones never slow down or stutter!"
    ),
    // Display copy only — the "app_power_monitoring" id is the Firestore collection
    // key and must NOT change or existing leaderboard entries orphan. Android does not
    // expose per-app battery draw to third-party apps, so this ranks screen time, not
    // battery. The old copy ("See which apps drain your battery the most!") was rejected
    // under Google Play's Deceptive Behavior policy on 2026-07-12.
    APP_POWER_MONITORING(
        "app_power_monitoring",
        "App Screen Time",
        "📱",
        "Which apps get the most screen time?",
        "See which apps you use the most!"
    ),
    BEST_DEVICE(
        "best_device",
        "Best Device",
        "🏆",
        "Which phones are the best overall?",
        "These phones are the best across all categories!"
    )
}

/**
 * App Power Leaderboard Entry (for app-level rankings)
 */
data class AppPowerLeaderboardEntry(
    val packageName: String = "", // Unique identifier for the app
    val appName: String = "", // Display name
    val avgPowerConsumption: Double = 0.0, // Average power in watts
    val peakPowerConsumption: Double = 0.0, // Peak power in watts
    val userCount: Int = 0, // Number of users who reported this app
    val avgBatteryImpact: Double = 0.0, // Average battery % per hour
    val totalUsageTime: Long = 0L, // Total usage time across all users (ms)
    val dataQuality: Int = 0, // 1-5 based on data completeness
    val lastUpdated: Long = 0L,
    val userId: String = "",
    val userIds: List<String> = emptyList(), // Track unique user IDs
    val measurementCount: Int = 0, // Total measurements
    val androidVersion: String = "", // Most common OS version for this app
    val androidVersions: Map<String, Int> = emptyMap(), // Version -> user count distribution
    val osPowerConsumption: Map<String, Double> = emptyMap(), // OS version -> average power consumption (watts)
    val osBatteryImpact: Map<String, Double> = emptyMap() // OS version -> average battery impact (%/hour)
)

/**
 * Trust badge enum
 */
enum class TrustBadge {
    VERIFIED, // 100+ users, high data quality
    HIGH, // 50-99 users, good data quality
    MEDIUM, // 10-49 users, moderate data quality
    LOW // <10 users, low data quality
}

