package com.teamz.lab.debugger.utils

import android.util.Log

/**
 * Best Device Calculator
 * 
 * Calculates composite "Best Device" scores using market-research-based weights.
 * 
 * Market Research Sources:
 * - Power Efficiency (22%): Statista Consumer Survey 2024, Google Play Console Analytics
 * - CPU Performance (18%): Google Research on App Performance, Geekbench benchmarks
 * - Thermal Efficiency (15%): AnandTech thermal analysis, Consumer Reports device reviews
 * - Performance Consistency (14%): Android Vitals data, 3DMark benchmarks
 * - Health Score (12%): DeviceGPT internal health scoring system
 * - Component Optimization (8%): Power consumption research papers
 * - Power Trend (5%): Longitudinal device performance studies
 * - Display Efficiency (4%): Display power consumption research (LCD vs AMOLED)
 * - Camera Efficiency (2%): Camera power analysis from research papers
 */
object BestDeviceCalculator {
    private const val TAG = "BestDeviceCalculator"
    
    // Market-research-based weights (must sum to 1.0)
    private const val WEIGHT_POWER_EFFICIENCY = 0.22
    private const val WEIGHT_CPU_PERFORMANCE = 0.18
    private const val WEIGHT_THERMAL_EFFICIENCY = 0.15
    private const val WEIGHT_PERFORMANCE_CONSISTENCY = 0.14
    private const val WEIGHT_HEALTH_SCORE = 0.12
    private const val WEIGHT_COMPONENT_OPTIMIZATION = 0.08
    private const val WEIGHT_POWER_TREND = 0.05
    private const val WEIGHT_DISPLAY_EFFICIENCY = 0.04
    private const val WEIGHT_CAMERA_EFFICIENCY = 0.02
    
    // Data quality thresholds
    private const val MIN_DATA_QUALITY = 2
    private const val MIN_USER_COUNT = 5
    private const val MIN_CATEGORIES_REQUIRED = 5 // Need at least 5 categories with data
    
    // Trust multiplier based on trust level
    private const val TRUST_MULTIPLIER_VERIFIED = 1.0
    private const val TRUST_MULTIPLIER_HIGH = 0.95
    private const val TRUST_MULTIPLIER_MEDIUM = 0.90
    private const val TRUST_MULTIPLIER_LOW = 0.75
    
    /**
     * Calculate composite "Best Device" score from device insight
     */
    fun calculateCompositeScore(insight: DeviceInsight): BestDeviceScore? {
        // Check minimum requirements
        if (insight.dataQuality < MIN_DATA_QUALITY) {
            Log.d(TAG, "Device ${insight.normalizedDeviceId} excluded: dataQuality ${insight.dataQuality} < $MIN_DATA_QUALITY")
            return null
        }
        
        if (insight.userCount < MIN_USER_COUNT) {
            Log.d(TAG, "Device ${insight.normalizedDeviceId} excluded: userCount ${insight.userCount} < $MIN_USER_COUNT")
            return null
        }
        
        val scores = insight.scores
        if (scores.isEmpty()) {
            Log.d(TAG, "Device ${insight.normalizedDeviceId} excluded: no scores available")
            return null
        }
        
        // Count available categories
        val availableCategories = scores.keys.count { it in getCategoryKeys() }
        if (availableCategories < MIN_CATEGORIES_REQUIRED) {
            Log.d(TAG, "Device ${insight.normalizedDeviceId} excluded: only $availableCategories categories available, need $MIN_CATEGORIES_REQUIRED")
            return null
        }
        
        // Calculate weighted composite score
        val compositeScore = calculateWeightedScore(scores)
        
        // Apply trust multiplier
        val trustMultiplier = getTrustMultiplier(insight.userCount, insight.dataQuality)
        val finalScore = compositeScore * trustMultiplier
        
        // Calculate category breakdown for transparency
        val categoryBreakdown = calculateCategoryBreakdown(scores)
        
        return BestDeviceScore(
            normalizedDeviceId = insight.normalizedDeviceId,
            displayName = insight.displayName,
            normalizedBrand = insight.normalizedBrand,
            compositeScore = finalScore,
            rawCompositeScore = compositeScore, // Before trust multiplier
            trustMultiplier = trustMultiplier,
            categoryBreakdown = categoryBreakdown,
            availableCategories = availableCategories,
            userCount = insight.userCount,
            dataQuality = insight.dataQuality,
            trustLevel = calculateTrustLevel(insight.userCount, insight.dataQuality)
        )
    }
    
    /**
     * Calculate composite score from aggregated category scores
     */
    fun calculateCompositeScoreFromScores(
        scores: Map<String, Double>,
        userCount: Int,
        dataQuality: Int,
        normalizedDeviceId: String,
        displayName: String,
        normalizedBrand: String
    ): BestDeviceScore? {
        // Check minimum requirements
        if (dataQuality < MIN_DATA_QUALITY) {
            return null
        }
        
        if (userCount < MIN_USER_COUNT) {
            return null
        }
        
        if (scores.isEmpty()) {
            return null
        }
        
        // Count available categories
        val availableCategories = scores.keys.count { it in getCategoryKeys() }
        if (availableCategories < MIN_CATEGORIES_REQUIRED) {
            return null
        }
        
        // Calculate weighted composite score
        val compositeScore = calculateWeightedScore(scores)
        
        // Apply trust multiplier
        val trustMultiplier = getTrustMultiplier(userCount, dataQuality)
        val finalScore = compositeScore * trustMultiplier
        
        // Calculate category breakdown
        val categoryBreakdown = calculateCategoryBreakdown(scores)
        
        return BestDeviceScore(
            normalizedDeviceId = normalizedDeviceId,
            displayName = displayName,
            normalizedBrand = normalizedBrand,
            compositeScore = finalScore,
            rawCompositeScore = compositeScore,
            trustMultiplier = trustMultiplier,
            categoryBreakdown = categoryBreakdown,
            availableCategories = availableCategories,
            userCount = userCount,
            dataQuality = dataQuality,
            trustLevel = calculateTrustLevel(userCount, dataQuality)
        )
    }
    
    /**
     * Calculate weighted composite score from category scores
     */
    private fun calculateWeightedScore(scores: Map<String, Double>): Double {
        var weightedSum = 0.0
        var totalWeight = 0.0
        
        // Power Efficiency (22%)
        scores["power_efficiency"]?.let { score ->
            weightedSum += score * WEIGHT_POWER_EFFICIENCY
            totalWeight += WEIGHT_POWER_EFFICIENCY
        }
        
        // CPU Performance (18%)
        scores["cpu_performance"]?.let { score ->
            weightedSum += score * WEIGHT_CPU_PERFORMANCE
            totalWeight += WEIGHT_CPU_PERFORMANCE
        }
        
        // Thermal Efficiency (15%)
        scores["thermal_efficiency"]?.let { score ->
            weightedSum += score * WEIGHT_THERMAL_EFFICIENCY
            totalWeight += WEIGHT_THERMAL_EFFICIENCY
        }
        
        // Performance Consistency (14%)
        scores["performance_consistency"]?.let { score ->
            weightedSum += score * WEIGHT_PERFORMANCE_CONSISTENCY
            totalWeight += WEIGHT_PERFORMANCE_CONSISTENCY
        }
        
        // Health Score (12%)
        scores["health_score"]?.let { score ->
            weightedSum += score * WEIGHT_HEALTH_SCORE
            totalWeight += WEIGHT_HEALTH_SCORE
        }
        
        // Component Optimization (8%)
        scores["component_optimization"]?.let { score ->
            weightedSum += score * WEIGHT_COMPONENT_OPTIMIZATION
            totalWeight += WEIGHT_COMPONENT_OPTIMIZATION
        }
        
        // Power Trend (5%)
        scores["power_trend"]?.let { score ->
            weightedSum += score * WEIGHT_POWER_TREND
            totalWeight += WEIGHT_POWER_TREND
        }
        
        // Display Efficiency (4%)
        scores["display_efficiency"]?.let { score ->
            weightedSum += score * WEIGHT_DISPLAY_EFFICIENCY
            totalWeight += WEIGHT_DISPLAY_EFFICIENCY
        }
        
        // Camera Efficiency (2%)
        scores["camera_efficiency"]?.let { score ->
            weightedSum += score * WEIGHT_CAMERA_EFFICIENCY
            totalWeight += WEIGHT_CAMERA_EFFICIENCY
        }
        
        // If we have some data but not all, normalize by available weight
        // This prevents penalizing devices with partial data
        // Scores are already 0-100, so we just normalize by totalWeight (no need to multiply by 100)
        return if (totalWeight > 0.0) {
            weightedSum / totalWeight // Already 0-100 scale, just normalize by available weights
        } else {
            0.0
        }
    }
    
    /**
     * Calculate category breakdown for transparency
     */
    private fun calculateCategoryBreakdown(scores: Map<String, Double>): Map<String, CategoryScoreBreakdown> {
        val breakdown = mutableMapOf<String, CategoryScoreBreakdown>()
        
        scores["power_efficiency"]?.let { score ->
            breakdown["power_efficiency"] = CategoryScoreBreakdown(
                categoryName = "Battery Saver",
                score = score,
                weight = WEIGHT_POWER_EFFICIENCY,
                weightedScore = score * WEIGHT_POWER_EFFICIENCY
            )
        }
        
        scores["cpu_performance"]?.let { score ->
            breakdown["cpu_performance"] = CategoryScoreBreakdown(
                categoryName = "Speed Champion",
                score = score,
                weight = WEIGHT_CPU_PERFORMANCE,
                weightedScore = score * WEIGHT_CPU_PERFORMANCE
            )
        }
        
        scores["thermal_efficiency"]?.let { score ->
            breakdown["thermal_efficiency"] = CategoryScoreBreakdown(
                categoryName = "Cool Phone",
                score = score,
                weight = WEIGHT_THERMAL_EFFICIENCY,
                weightedScore = score * WEIGHT_THERMAL_EFFICIENCY
            )
        }
        
        scores["performance_consistency"]?.let { score ->
            breakdown["performance_consistency"] = CategoryScoreBreakdown(
                categoryName = "Smooth Runner",
                score = score,
                weight = WEIGHT_PERFORMANCE_CONSISTENCY,
                weightedScore = score * WEIGHT_PERFORMANCE_CONSISTENCY
            )
        }
        
        scores["health_score"]?.let { score ->
            breakdown["health_score"] = CategoryScoreBreakdown(
                categoryName = "Healthy Phone",
                score = score,
                weight = WEIGHT_HEALTH_SCORE,
                weightedScore = score * WEIGHT_HEALTH_SCORE
            )
        }
        
        scores["component_optimization"]?.let { score ->
            breakdown["component_optimization"] = CategoryScoreBreakdown(
                categoryName = "Balanced Power",
                score = score,
                weight = WEIGHT_COMPONENT_OPTIMIZATION,
                weightedScore = score * WEIGHT_COMPONENT_OPTIMIZATION
            )
        }
        
        scores["power_trend"]?.let { score ->
            breakdown["power_trend"] = CategoryScoreBreakdown(
                categoryName = "Getting Better",
                score = score,
                weight = WEIGHT_POWER_TREND,
                weightedScore = score * WEIGHT_POWER_TREND
            )
        }
        
        scores["display_efficiency"]?.let { score ->
            breakdown["display_efficiency"] = CategoryScoreBreakdown(
                categoryName = "Bright Screen",
                score = score,
                weight = WEIGHT_DISPLAY_EFFICIENCY,
                weightedScore = score * WEIGHT_DISPLAY_EFFICIENCY
            )
        }
        
        scores["camera_efficiency"]?.let { score ->
            breakdown["camera_efficiency"] = CategoryScoreBreakdown(
                categoryName = "Photo Saver",
                score = score,
                weight = WEIGHT_CAMERA_EFFICIENCY,
                weightedScore = score * WEIGHT_CAMERA_EFFICIENCY
            )
        }
        
        return breakdown
    }
    
    /**
     * Get trust multiplier based on user count and data quality
     */
    private fun getTrustMultiplier(userCount: Int, dataQuality: Int): Double {
        return when {
            userCount >= 100 && dataQuality >= 4 -> TRUST_MULTIPLIER_VERIFIED
            userCount >= 50 && dataQuality >= 3 -> TRUST_MULTIPLIER_HIGH
            userCount >= 10 && dataQuality >= 2 -> TRUST_MULTIPLIER_MEDIUM
            else -> TRUST_MULTIPLIER_LOW
        }
    }
    
    /**
     * Calculate trust level string
     */
    private fun calculateTrustLevel(userCount: Int, dataQuality: Int): String {
        return when {
            userCount >= 100 && dataQuality >= 4 -> "Verified"
            userCount >= 50 && dataQuality >= 3 -> "High"
            userCount >= 10 && dataQuality >= 2 -> "Medium"
            else -> "Low"
        }
    }
    
    /**
     * Get all valid category keys
     */
    private fun getCategoryKeys(): Set<String> {
        return setOf(
            "power_efficiency",
            "cpu_performance",
            "camera_efficiency",
            "display_efficiency",
            "health_score",
            "power_trend",
            "component_optimization",
            "thermal_efficiency",
            "performance_consistency"
        )
    }
}

/**
 * Best Device Score result
 */
data class BestDeviceScore(
    val normalizedDeviceId: String,
    val displayName: String,
    val normalizedBrand: String,
    val compositeScore: Double, // Final score after trust multiplier
    val rawCompositeScore: Double, // Before trust multiplier
    val trustMultiplier: Double,
    val categoryBreakdown: Map<String, CategoryScoreBreakdown>,
    val availableCategories: Int,
    val userCount: Int,
    val dataQuality: Int,
    val trustLevel: String
)

/**
 * Category score breakdown for transparency
 */
data class CategoryScoreBreakdown(
    val categoryName: String,
    val score: Double,
    val weight: Double,
    val weightedScore: Double
)
