package com.teamz.lab.debugger.utils

import android.content.Context
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator.PowerStats
import com.teamz.lab.debugger.utils.PowerConsumptionUtils.ComponentPowerData
import com.teamz.lab.debugger.utils.PowerConsumptionUtils.PowerConsumptionSummary

/**
 * Power Consumption Recommendations Engine
 * Based on research papers from latest_power_consumption_research.md
 * 
 * Provides actionable recommendations to optimize power consumption
 */
object PowerRecommendations {
    
    data class PowerRecommendation(
        val title: String,
        val description: String,
        val priority: Priority,
        val category: Category,
        val researchSource: String? = null
    )
    
    enum class Priority {
        HIGH, MEDIUM, LOW
    }
    
    enum class Category {
        DISPLAY, CPU, NETWORK, CAMERA, BATTERY, GENERAL
    }
    
    /**
     * Generate recommendations based on current power consumption data
     */
    fun generateRecommendations(
        context: Context,
        powerData: PowerConsumptionSummary?,
        aggregatedStats: PowerStats?
    ): List<PowerRecommendation> {
        val recommendations = mutableListOf<PowerRecommendation>()
        
        if (powerData == null) return recommendations

        @Suppress("NAME_SHADOWING")
        val context = LocaleManager.localizedContext(context)
        
        // Display brightness recommendations (from LCD vs AMOLED research)
        recommendations.addAll(getDisplayRecommendations(context, powerData, aggregatedStats))
        
        // CPU frequency scaling advice (from frequency-independent research)
        recommendations.addAll(getCpuRecommendations(context, powerData, aggregatedStats))
        
        // Network power optimization (from RSSI research)
        recommendations.addAll(getNetworkRecommendations(context, powerData, aggregatedStats))
        
        // Camera usage optimization (from per-photo energy research)
        recommendations.addAll(getCameraRecommendations(context, powerData, aggregatedStats))
        
        // Battery health recommendations
        recommendations.addAll(getBatteryRecommendations(context, powerData, aggregatedStats))
        
        // General power optimization tips
        recommendations.addAll(getGeneralRecommendations(context, powerData, aggregatedStats))
        
        // Sort by priority
        return recommendations.sortedBy { 
            when (it.priority) {
                Priority.HIGH -> 0
                Priority.MEDIUM -> 1
                Priority.LOW -> 2
            }
        }
    }
    
    private fun getDisplayRecommendations(
        context: Context,
        powerData: PowerConsumptionSummary,
        stats: PowerStats?
    ): List<PowerRecommendation> {
        val recommendations = mutableListOf<PowerRecommendation>()
        
        val displayComponent = powerData.components.find { it.component == "Display" }
        val displayPower = displayComponent?.powerConsumption ?: 0.0
        
        // High display power consumption
        if (displayPower > 2000.0) { // > 2W
            recommendations.add(
                PowerRecommendation(
                    title = context.getString(R.string.pw_rec_brightness_title),
                    description = context.getString(R.string.pw_rec_brightness_desc, String.format("%.1f", displayPower / 1000)),
                    priority = Priority.HIGH,
                    category = Category.DISPLAY,
                    researchSource = "LCD vs AMOLED Power Consumption Research"
                )
            )
        }
        
        // Display trend analysis
        stats?.let {
            if (it.powerTrend == PowerConsumptionAggregator.PowerTrend.INCREASING && displayPower > 1500.0) {
                recommendations.add(
                    PowerRecommendation(
                        title = context.getString(R.string.pw_rec_display_trend_title),
                        description = context.getString(R.string.pw_rec_display_trend_desc),
                        priority = Priority.MEDIUM,
                        category = Category.DISPLAY
                    )
                )
            }
        }
        
        return recommendations
    }
    
    private fun getCpuRecommendations(
        context: Context,
        powerData: PowerConsumptionSummary,
        stats: PowerStats?
    ): List<PowerRecommendation> {
        val recommendations = mutableListOf<PowerRecommendation>()
        
        val cpuComponent = powerData.components.find { it.component == "CPU" }
        val cpuPower = cpuComponent?.powerConsumption ?: 0.0
        
        // High CPU power consumption
        if (cpuPower > 3000.0) { // > 3W
            recommendations.add(
                PowerRecommendation(
                    title = context.getString(R.string.pw_rec_cpu_title),
                    description = context.getString(R.string.pw_rec_cpu_desc, String.format("%.1f", cpuPower / 1000)),
                    priority = Priority.HIGH,
                    category = Category.CPU,
                    researchSource = "CPU Frequency-Independent Power Consumption Research"
                )
            )
        }
        
        // CPU usage details
        cpuComponent?.details?.let { details ->
            if (details.contains("%") && details.contains("80")) {
                recommendations.add(
                    PowerRecommendation(
                        title = context.getString(R.string.pw_rec_cpu_high_title),
                        description = context.getString(R.string.pw_rec_cpu_high_desc),
                        priority = Priority.MEDIUM,
                        category = Category.CPU
                    )
                )
            }
        }
        
        return recommendations
    }
    
    private fun getNetworkRecommendations(
        context: Context,
        powerData: PowerConsumptionSummary,
        stats: PowerStats?
    ): List<PowerRecommendation> {
        val recommendations = mutableListOf<PowerRecommendation>()
        
        val networkComponent = powerData.components.find { it.component == "Network" }
        val networkPower = networkComponent?.powerConsumption ?: 0.0
        
        // High network power consumption
        if (networkPower > 1500.0) { // > 1.5W
            recommendations.add(
                PowerRecommendation(
                    title = context.getString(R.string.pw_rec_network_title),
                    description = context.getString(R.string.pw_rec_network_desc, String.format("%.1f", networkPower / 1000)),
                    priority = Priority.MEDIUM,
                    category = Category.NETWORK,
                    researchSource = "Network RSSI Power Consumption Research"
                )
            )
        }
        
        return recommendations
    }
    
    private fun getCameraRecommendations(
        context: Context,
        powerData: PowerConsumptionSummary,
        stats: PowerStats?
    ): List<PowerRecommendation> {
        val recommendations = mutableListOf<PowerRecommendation>()
        
        val cameraComponent = powerData.components.find { it.component == "Camera" }
        val cameraPower = cameraComponent?.powerConsumption ?: 0.0
        
        // Camera power consumption
        if (cameraPower > 2000.0) { // > 2W
            recommendations.add(
                PowerRecommendation(
                    title = context.getString(R.string.pw_rec_camera_title),
                    description = context.getString(R.string.pw_rec_camera_desc, String.format("%.1f", cameraPower / 1000)),
                    priority = Priority.MEDIUM,
                    category = Category.CAMERA,
                    researchSource = "Per-Photo Energy Consumption Research"
                )
            )
        }
        
        return recommendations
    }
    
    private fun getBatteryRecommendations(
        context: Context,
        powerData: PowerConsumptionSummary,
        stats: PowerStats?
    ): List<PowerRecommendation> {
        val recommendations = mutableListOf<PowerRecommendation>()
        
        val batteryComponent = powerData.components.find { it.component == "Battery" }
        val totalPower = powerData.totalPower
        
        // High total power consumption
        if (totalPower > 8000.0) { // > 8W
            recommendations.add(
                PowerRecommendation(
                    title = context.getString(R.string.pw_rec_overall_title),
                    description = context.getString(R.string.pw_rec_overall_desc, String.format("%.1f", totalPower / 1000)),
                    priority = Priority.HIGH,
                    category = Category.BATTERY
                )
            )
        }
        
        // Power trend analysis
        stats?.let {
            when (it.powerTrend) {
                PowerConsumptionAggregator.PowerTrend.INCREASING -> {
                    recommendations.add(
                        PowerRecommendation(
                            title = context.getString(R.string.pw_rec_increasing_title),
                            description = context.getString(R.string.pw_rec_increasing_desc),
                            priority = Priority.MEDIUM,
                            category = Category.BATTERY
                        )
                    )
                }
                PowerConsumptionAggregator.PowerTrend.DECREASING -> {
                    recommendations.add(
                        PowerRecommendation(
                            title = context.getString(R.string.pw_rec_improving_title),
                            description = context.getString(R.string.pw_rec_improving_desc),
                            priority = Priority.LOW,
                            category = Category.BATTERY
                        )
                    )
                }
                else -> {}
            }
        }
        
        return recommendations
    }
    
    private fun getGeneralRecommendations(
        context: Context,
        powerData: PowerConsumptionSummary,
        stats: PowerStats?
    ): List<PowerRecommendation> {
        val recommendations = mutableListOf<PowerRecommendation>()
        
        // Top consumers analysis
        val topConsumers = powerData.components
            .sortedByDescending { it.powerConsumption }
            .take(3)
        
        if (topConsumers.isNotEmpty() && topConsumers[0].powerConsumption > 1000.0) {
            val topConsumer = topConsumers[0]
            recommendations.add(
                PowerRecommendation(
                    title = context.getString(R.string.pw_rec_top_title, PowerStrings.component(context, topConsumer.component)),
                    description = context.getString(R.string.pw_rec_top_desc, PowerStrings.component(context, topConsumer.component), String.format("%.1f", topConsumer.powerConsumption / 1000)),
                    priority = Priority.MEDIUM,
                    category = Category.GENERAL
                )
            )
        }
        
        return recommendations
    }
}

