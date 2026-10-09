package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.teamz.lab.debugger.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Power consumption data aggregator for real-time monitoring and historical tracking
 */
object PowerConsumptionAggregator {
    
    private const val PREFS_NAME = "power_consumption_prefs"
    private const val KEY_HISTORY = "power_history"
    private const val KEY_AVERAGE_POWER = "average_power"
    private const val KEY_PEAK_POWER = "peak_power"
    private const val KEY_TOTAL_SAMPLES = "total_samples"
    private const val KEY_LAST_UPDATE = "last_update"
    
    private val _powerHistoryFlow = MutableStateFlow<List<PowerDataPoint>>(emptyList())
    val powerHistoryFlow: StateFlow<List<PowerDataPoint>> = _powerHistoryFlow.asStateFlow()
    
    private val _currentPowerFlow = MutableStateFlow<PowerConsumptionUtils.PowerConsumptionSummary?>(null)
    val currentPowerFlow: StateFlow<PowerConsumptionUtils.PowerConsumptionSummary?> = _currentPowerFlow.asStateFlow()
    
    private val _aggregatedStatsFlow = MutableStateFlow<PowerStats?>(null)
    val aggregatedStatsFlow: StateFlow<PowerStats?> = _aggregatedStatsFlow.asStateFlow()
    
    data class PowerDataPoint(
        val timestamp: Long,
        val totalPower: Double,
        val componentBreakdown: Map<String, Double>
    )
    
    data class PowerStats(
        val averagePower: Double,
        val peakPower: Double,
        val minPower: Double,
        val totalSamples: Int,
        val lastUpdate: Long,
        val powerTrend: PowerTrend,
        val topConsumers: List<ComponentPowerStats>
    )
    
    data class ComponentPowerStats(
        val component: String,
        val averagePower: Double,
        val peakPower: Double,
        val usagePercentage: Double
    )
    
    enum class PowerTrend {
        INCREASING, DECREASING, STABLE, UNKNOWN
    }
    
    /**
     * Update power consumption data and aggregate statistics
     */
    fun updatePowerData(context: Context, powerData: PowerConsumptionUtils.PowerConsumptionSummary) {
        val currentTime = System.currentTimeMillis()
        
        // Update current power flow
        _currentPowerFlow.value = powerData
        
        // Create data point
        val dataPoint = PowerDataPoint(
            timestamp = currentTime,
            totalPower = powerData.totalPower,
            componentBreakdown = powerData.components.associate { it.component to it.powerConsumption }
        )
        
        // Add to history
        val currentHistory = _powerHistoryFlow.value.toMutableList()
        currentHistory.add(dataPoint)
        
        // Keep only last 100 data points (5 minutes at 3-second intervals)
        if (currentHistory.size > 100) {
            currentHistory.removeAt(0)
        }
        
        _powerHistoryFlow.value = currentHistory
        
        // Update aggregated statistics
        updateAggregatedStats(context, currentHistory)
        
        // Save to preferences
        saveToPreferences(context, dataPoint)
    }
    
    /**
     * Calculate aggregated statistics from power history
     */
    private fun updateAggregatedStats(context: Context, history: List<PowerDataPoint>) {
        if (history.isEmpty()) return
        
        val totalPowers = history.map { it.totalPower }
        val averagePower = totalPowers.average()
        val peakPower = totalPowers.maxOrNull() ?: 0.0
        val minPower = totalPowers.minOrNull() ?: 0.0
        
        // Calculate power trend
        val powerTrend = calculatePowerTrend(history)
        
        // Calculate component statistics
        val componentStats = calculateComponentStats(history)
        
        val stats = PowerStats(
            averagePower = averagePower,
            peakPower = peakPower,
            minPower = minPower,
            totalSamples = history.size,
            lastUpdate = System.currentTimeMillis(),
            powerTrend = powerTrend,
            topConsumers = componentStats
        )
        
        _aggregatedStatsFlow.value = stats
        
        // Save aggregated stats to preferences
        saveAggregatedStats(context, stats)
    }
    
    /**
     * Calculate power trend from recent history
     */
    private fun calculatePowerTrend(history: List<PowerDataPoint>): PowerTrend {
        if (history.size < 3) return PowerTrend.UNKNOWN
        
        val recent = history.takeLast(5) // Last 5 data points
        val older = history.dropLast(5).takeLast(5) // Previous 5 data points
        
        if (older.isEmpty()) return PowerTrend.UNKNOWN
        
        val recentAvg = recent.map { it.totalPower }.average()
        val olderAvg = older.map { it.totalPower }.average()
        
        val difference = recentAvg - olderAvg
        val threshold = 0.1 // 0.1W threshold for trend detection
        
        return when {
            difference > threshold -> PowerTrend.INCREASING
            difference < -threshold -> PowerTrend.DECREASING
            else -> PowerTrend.STABLE
        }
    }
    
    /**
     * Calculate component-level statistics
     */
    private fun calculateComponentStats(history: List<PowerDataPoint>): List<ComponentPowerStats> {
        val componentMap = mutableMapOf<String, MutableList<Double>>()
        
        // Collect all component power values
        history.forEach { dataPoint ->
            dataPoint.componentBreakdown.forEach { (component, power) ->
                componentMap.getOrPut(component) { mutableListOf() }.add(power)
            }
        }
        
        val totalAveragePower = history.map { it.totalPower }.average()
        
        return componentMap.map { (component, powers) ->
            val averagePower = powers.average()
            val peakPower = powers.maxOrNull() ?: 0.0
            val usagePercentage = if (totalAveragePower > 0) (averagePower / totalAveragePower) * 100 else 0.0
            
            ComponentPowerStats(
                component = component,
                averagePower = averagePower,
                peakPower = peakPower,
                usagePercentage = usagePercentage
            )
        }.sortedByDescending { it.averagePower }.take(5) // Top 5 consumers
    }
    
    /**
     * Get power consumption efficiency rating
     */
    fun getEfficiencyRating(averagePower: Double): String {
        return when {
            averagePower < 2.0 -> "Excellent ⭐⭐⭐⭐⭐"
            averagePower < 4.0 -> "Good ⭐⭐⭐⭐"
            averagePower < 6.0 -> "Fair ⭐⭐⭐"
            averagePower < 8.0 -> "Poor ⭐⭐"
            else -> "Very Poor ⭐"
        }
    }
    
    /**
     * Get power consumption recommendations
     */
    fun getPowerRecommendations(stats: PowerStats): List<String> {
        val recommendations = mutableListOf<String>()
        
        when (stats.powerTrend) {
            PowerTrend.INCREASING -> {
                recommendations.add("📈 Power consumption is increasing. Close unused apps.")
                recommendations.add("🔋 Consider enabling battery saver mode.")
            }
            PowerTrend.DECREASING -> {
                recommendations.add("📉 Power consumption is decreasing. Good optimization!")
            }
            PowerTrend.STABLE -> {
                recommendations.add("📊 Power consumption is stable.")
            }
            PowerTrend.UNKNOWN -> {
                recommendations.add("📊 Collecting more data for analysis...")
            }
        }
        
        // Component-specific recommendations
        stats.topConsumers.take(3).forEach { component ->
            when {
                component.averagePower > 1.5 -> {
                    recommendations.add("⚠️ ${component.component} is using ${"%.1f".format(component.averagePower)}W - consider optimizing")
                }
                component.usagePercentage > 30 -> {
                    recommendations.add("📊 ${component.component} accounts for ${"%.1f".format(component.usagePercentage)}% of total power")
                }
            }
        }
        
        // General recommendations based on average power
        when {
            stats.averagePower > 6.0 -> {
                recommendations.add("🔋 High power consumption detected. Enable battery optimization.")
                recommendations.add("📱 Reduce screen brightness and close background apps.")
            }
            stats.averagePower < 2.0 -> {
                recommendations.add("✅ Excellent power efficiency! Keep up the good work.")
            }
        }
        
        return recommendations.take(5) // Return top 5 recommendations
    }
    
    /**
     * Save data point to preferences
     */
    private fun saveToPreferences(context: Context, dataPoint: PowerDataPoint) {
        val prefs = getPrefs(context)
        val historyJson = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val history = try {
            // Simple JSON-like storage (in a real app, use proper JSON serialization)
            val currentHistory = historyJson.split("|").filter { it.isNotEmpty() }
            val newEntry = "${dataPoint.timestamp},${dataPoint.totalPower}"
            val updatedHistory = (currentHistory + newEntry).takeLast(50) // Keep last 50 entries
            updatedHistory.joinToString("|")
        } catch (e: Exception) {
            handleError(e)
            "${dataPoint.timestamp},${dataPoint.totalPower}"
        }
        
        prefs.edit {
            putString(KEY_HISTORY, history)
            putLong(KEY_LAST_UPDATE, dataPoint.timestamp)
        }
    }
    
    /**
     * Save aggregated statistics to preferences
     */
    private fun saveAggregatedStats(context: Context, stats: PowerStats) {
        val prefs = getPrefs(context)
        prefs.edit {
            putFloat(KEY_AVERAGE_POWER, stats.averagePower.toFloat())
            putFloat(KEY_PEAK_POWER, stats.peakPower.toFloat())
            putInt(KEY_TOTAL_SAMPLES, stats.totalSamples)
        }
    }
    
    /**
     * Load historical data from preferences
     */
    fun loadHistoricalData(context: Context): List<PowerDataPoint> {
        val prefs = getPrefs(context)
        val historyJson = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        
        return try {
            historyJson.split("|").filter { it.isNotEmpty() }.mapNotNull { entry ->
                val parts = entry.split(",")
                if (parts.size >= 2) {
                    PowerDataPoint(
                        timestamp = parts[0].toLongOrNull() ?: 0L,
                        totalPower = parts[1].toDoubleOrNull() ?: 0.0,
                        componentBreakdown = emptyMap() // Simplified for storage
                    )
                } else null
            }
        } catch (e: Exception) {
            handleError(e)
            emptyList()
        }
    }
    
    /**
     * Get saved aggregated statistics
     */
    fun getSavedStats(context: Context): PowerStats? {
        val prefs = getPrefs(context)
        val averagePower = prefs.getFloat(KEY_AVERAGE_POWER, 0f).toDouble()
        val peakPower = prefs.getFloat(KEY_PEAK_POWER, 0f).toDouble()
        val totalSamples = prefs.getInt(KEY_TOTAL_SAMPLES, 0)
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        
        if (totalSamples == 0) return null
        
        return PowerStats(
            averagePower = averagePower,
            peakPower = peakPower,
            minPower = 0.0, // Not stored for simplicity
            totalSamples = totalSamples,
            lastUpdate = lastUpdate,
            powerTrend = PowerTrend.UNKNOWN, // Would need more complex storage
            topConsumers = emptyList() // Would need more complex storage
        )
    }
    
    /**
     * Clear all historical data
     */
    fun clearHistory(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit {
            remove(KEY_HISTORY)
            remove(KEY_AVERAGE_POWER)
            remove(KEY_PEAK_POWER)
            remove(KEY_TOTAL_SAMPLES)
            remove(KEY_LAST_UPDATE)
        }
        _powerHistoryFlow.value = emptyList()
        _aggregatedStatsFlow.value = null
    }
    
    /**
     * Get preferences instance
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // Test results persistence keys
    private const val KEY_CAMERA_TEST_RESULTS = "camera_test_results"
    private const val KEY_DISPLAY_TEST_RESULTS = "display_test_results"
    private const val KEY_CPU_TEST_RESULTS = "cpu_test_results"
    private const val KEY_NETWORK_TEST_RESULTS = "network_test_results"
    private const val KEY_APP_POWER_SNAPSHOTS = "app_power_snapshots"
    
    /**
     * Save camera test results to preferences
     */
    fun saveCameraTestResults(context: Context, results: List<PowerConsumptionUtils.CameraPowerTestResult>) {
        val prefs = getPrefs(context)
        val json = results.joinToString("|") { result ->
            "${result.beforeCapture},${result.afterCapture},${result.powerDifference}," +
            "${result.captureDuration},${result.timestamp}," +
            "${result.baselinePower},${result.previewPower},${result.capturePower}"
        }
        prefs.edit {
            putString(KEY_CAMERA_TEST_RESULTS, json)
        }
    }
    
    /**
     * Load camera test results from preferences
     */
    fun loadCameraTestResults(context: Context): List<PowerConsumptionUtils.CameraPowerTestResult> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_CAMERA_TEST_RESULTS, "") ?: ""
        if (json.isEmpty()) return emptyList()
        
        return try {
            json.split("|").filter { it.isNotEmpty() }.mapNotNull { entry ->
                val parts = entry.split(",")
                if (parts.size >= 8) {
                    PowerConsumptionUtils.CameraPowerTestResult(
                        beforeCapture = parts[0].toDoubleOrNull() ?: 0.0,
                        afterCapture = parts[1].toDoubleOrNull() ?: 0.0,
                        powerDifference = parts[2].toDoubleOrNull() ?: 0.0,
                        captureDuration = parts[3].toLongOrNull() ?: 0L,
                        timestamp = parts[4].toLongOrNull() ?: System.currentTimeMillis(),
                        baselinePower = parts[5].toDoubleOrNull() ?: parts[0].toDoubleOrNull() ?: 0.0,
                        previewPower = parts[6].toDoubleOrNull() ?: parts[0].toDoubleOrNull() ?: 0.0,
                        capturePower = parts[7].toDoubleOrNull() ?: parts[1].toDoubleOrNull() ?: 0.0
                    )
                } else null
            }
        } catch (e: Exception) {
            handleError(e)
            emptyList()
        }
    }
    
    /**
     * Clear camera test results
     */
    fun clearCameraTestResults(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit {
            remove(KEY_CAMERA_TEST_RESULTS)
        }
    }
    
    /**
     * Save display test results to preferences
     */
    fun saveDisplayTestResults(context: Context, results: List<PowerConsumptionUtils.DisplayPowerPoint>?) {
        val prefs = getPrefs(context)
        if (results == null || results.isEmpty()) {
            prefs.edit {
                remove(KEY_DISPLAY_TEST_RESULTS)
            }
            return
        }
        val json = results.joinToString("|") { point ->
            "${point.brightnessLevel},${point.apl},${point.powerW},${point.timestamp}"
        }
        prefs.edit {
            putString(KEY_DISPLAY_TEST_RESULTS, json)
        }
    }
    
    /**
     * Load display test results from preferences
     */
    fun loadDisplayTestResults(context: Context): List<PowerConsumptionUtils.DisplayPowerPoint>? {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_DISPLAY_TEST_RESULTS, null) ?: return null
        if (json.isEmpty()) return null
        
        return try {
            val results = json.split("|").filter { it.isNotEmpty() }.mapNotNull { entry ->
                val parts = entry.split(",")
                if (parts.size >= 4) {
                    PowerConsumptionUtils.DisplayPowerPoint(
                        brightnessLevel = parts[0].toIntOrNull() ?: 0,
                        apl = parts[1].toFloatOrNull() ?: 0f,
                        powerW = parts[2].toDoubleOrNull() ?: 0.0,
                        timestamp = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                    )
                } else null
            }
            if (results.isEmpty()) null else results
        } catch (e: Exception) {
            handleError(e)
            null
        }
    }
    
    /**
     * Save CPU test results to preferences
     */
    fun saveCpuTestResults(context: Context, results: List<PowerConsumptionUtils.CpuBenchPoint>?) {
        val prefs = getPrefs(context)
        if (results == null || results.isEmpty()) {
            prefs.edit {
                remove(KEY_CPU_TEST_RESULTS)
            }
            return
        }
        val json = results.joinToString("|") { point ->
            "${point.targetUtilPercent},${point.observedUtilPercent},${point.deltaPowerW},${point.freqSummary},${point.timestamp}"
        }
        prefs.edit {
            putString(KEY_CPU_TEST_RESULTS, json)
        }
    }
    
    /**
     * Load CPU test results from preferences
     */
    fun loadCpuTestResults(context: Context): List<PowerConsumptionUtils.CpuBenchPoint>? {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_CPU_TEST_RESULTS, null) ?: return null
        if (json.isEmpty()) return null
        
        return try {
            val results = json.split("|").filter { it.isNotEmpty() }.mapNotNull { entry ->
                val parts = entry.split(",")
                if (parts.size >= 5) {
                    PowerConsumptionUtils.CpuBenchPoint(
                        targetUtilPercent = parts[0].toIntOrNull() ?: 0,
                        observedUtilPercent = parts[1].toIntOrNull() ?: 0,
                        deltaPowerW = parts[2].toDoubleOrNull() ?: 0.0,
                        freqSummary = parts[3],
                        timestamp = parts[4].toLongOrNull() ?: System.currentTimeMillis()
                    )
                } else null
            }
            if (results.isEmpty()) null else results
        } catch (e: Exception) {
            handleError(e)
            null
        }
    }
    
    /**
     * Save network test results to preferences
     */
    fun saveNetworkTestResults(context: Context, results: List<PowerConsumptionUtils.NetworkSamplePoint>?) {
        val prefs = getPrefs(context)
        if (results == null || results.isEmpty()) {
            prefs.edit {
                remove(KEY_NETWORK_TEST_RESULTS)
            }
            return
        }
        val json = results.joinToString("|") { point ->
            "${point.timeSeconds},${point.wifiRssiDbm ?: ""},${point.cellDbm ?: ""},${point.powerW},${point.timestamp}"
        }
        prefs.edit {
            putString(KEY_NETWORK_TEST_RESULTS, json)
        }
    }
    
    /**
     * Load network test results from preferences
     */
    fun loadNetworkTestResults(context: Context): List<PowerConsumptionUtils.NetworkSamplePoint>? {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_NETWORK_TEST_RESULTS, null) ?: return null
        if (json.isEmpty()) return null
        
        return try {
            val results = json.split("|").filter { it.isNotEmpty() }.mapNotNull { entry ->
                val parts = entry.split(",")
                if (parts.size >= 5) {
                    PowerConsumptionUtils.NetworkSamplePoint(
                        timeSeconds = parts[0].toIntOrNull() ?: 0,
                        wifiRssiDbm = parts[1].toIntOrNull(),
                        cellDbm = parts[2].toIntOrNull(),
                        powerW = parts[3].toDoubleOrNull() ?: 0.0,
                        timestamp = parts[4].toLongOrNull() ?: System.currentTimeMillis()
                    )
                } else null
            }
            if (results.isEmpty()) null else results
        } catch (e: Exception) {
            handleError(e)
            null
        }
    }
    
    /**
     * Format power value for display
     */
    fun formatPower(power: Double): String {
        return when {
            power >= 1.0 -> "%.1f W".format(power)
            power >= 0.1 -> "%.0f mW".format(power * 1000)
            else -> "%.0f µW".format(power * 1000000)
        }
    }
    
    /**
     * Format timestamp for display
     */
    fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(date)
    }
    
    /**
     * Calculate battery percentage consumed per hour from power consumption
     * @param powerWatts Power consumption in watts
     * @param context Android context to get battery capacity
     * @return Percentage of battery consumed per hour (e.g., "2.5% per hour")
     */
    fun calculateBatteryPercentPerHour(powerWatts: Double, context: Context): String? {
        if (powerWatts <= 0) return null
        
        return try {
            val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val chargeCounter = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val voltage = getBatteryVoltage(context)
            
            if (chargeCounter > 0 && voltage > 0) {
                // Calculate battery capacity in Wh (Watt-hours)
                // chargeCounter is in microampere-hours (µAh)
                val capacityMah = chargeCounter / 1000.0 // Convert to mAh
                val capacityWh = (capacityMah * voltage / 1000.0) / 1000.0 // Convert to Wh
                
                // Calculate percentage per hour
                // If using powerWatts for 1 hour, percentage = (powerWatts / capacityWh) * 100
                val percentPerHour = (powerWatts / capacityWh) * 100.0
                
                if (percentPerHour > 0 && percentPerHour < 100) {
                    // Use more precision for very small values
                    when {
                        percentPerHour >= 0.1 -> context.getString(R.string.pw_per_hour, "%.1f".format(percentPerHour))
                        percentPerHour >= 0.01 -> context.getString(R.string.pw_per_hour, "%.2f".format(percentPerHour))
                        else -> context.getString(R.string.pw_per_hour, "%.3f".format(percentPerHour))
                    }
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculate battery percentage for a specific action (e.g., taking a photo)
     * @param energyJoules Energy consumed in Joules
     * @param context Android context to get battery capacity
     * @return Percentage of battery consumed (e.g., "0.2% of your charge")
     */
    fun calculateBatteryPercentForAction(energyJoules: Double, context: Context): String? {
        if (energyJoules <= 0) return null
        
        return try {
            val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val chargeCounter = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val voltage = getBatteryVoltage(context)
            
            if (chargeCounter > 0 && voltage > 0) {
                // Calculate battery capacity in Joules
                // chargeCounter is in microampere-hours (µAh)
                val capacityMah = chargeCounter / 1000.0 // Convert to mAh
                val capacityWh = (capacityMah * voltage / 1000.0) / 1000.0 // Convert to Wh
                val capacityJoules = capacityWh * 3600.0 // Convert to Joules (1 Wh = 3600 J)
                
                // Calculate percentage
                val percent = (energyJoules / capacityJoules) * 100.0
                
                if (percent > 0 && percent < 100) {
                    // Use more precision for very small values
                    when {
                        percent >= 0.1 -> "%.2f%%".format(percent)
                        percent >= 0.01 -> "%.3f%%".format(percent)
                        else -> "%.4f%%".format(percent)
                    }
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get battery voltage from BatteryManager
     */
    private fun getBatteryVoltage(context: Context): Int {
        return try {
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * Get concise practical explanation for power consumption
     * Returns minimal text like "~2% per hour" or "Uses ~0.5% per hour"
     */
    fun getPracticalPowerInfo(powerWatts: Double, context: Context, componentName: String = ""): String? {
        val percentPerHour = calculateBatteryPercentPerHour(powerWatts, context)
        return percentPerHour?.let {
            when {
                componentName.lowercase().contains("camera") -> context.getString(R.string.pw_per_photo, it)
                componentName.lowercase().contains("display") || componentName.lowercase().contains("screen") -> "~$it"
                componentName.lowercase().contains("cpu") -> "~$it"
                else -> "~$it"
            }
        }
    }
    
    /**
     * Save app power snapshot to preferences
     */
    fun saveAppPowerSnapshot(context: Context, snapshot: PowerConsumptionUtils.AppPowerSnapshot) {
        val prefs = getPrefs(context)
        val existingSnapshots = loadAppPowerSnapshots(context).toMutableList()
        existingSnapshots.add(snapshot)
        
        // Keep only last 100 snapshots
        val snapshotsToSave = existingSnapshots.takeLast(100)
        
        val json = snapshotsToSave.joinToString("||") { snap ->
            val appsJson = snap.apps.joinToString("|") { app ->
                "${app.packageName}::${app.appName}::${app.powerConsumption}::" +
                "${app.foregroundTime}::${app.backgroundTime}::${app.totalUsageTime}::" +
                "${app.batteryImpact}::${app.timestamp}"
            }
            "${snap.timestamp}::${snap.totalSystemPower}::$appsJson"
        }
        
        prefs.edit {
            putString(KEY_APP_POWER_SNAPSHOTS, json)
        }
    }
    
    /**
     * Load app power snapshots from preferences
     */
    fun loadAppPowerSnapshots(context: Context): List<PowerConsumptionUtils.AppPowerSnapshot> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_APP_POWER_SNAPSHOTS, "") ?: ""
        if (json.isEmpty()) return emptyList()
        
        return try {
            json.split("||").filter { it.isNotEmpty() }.mapNotNull { snapshotEntry ->
                val parts = snapshotEntry.split("::")
                if (parts.size >= 3) {
                    val timestamp = parts[0].toLongOrNull() ?: 0L
                    val totalSystemPower = parts[1].toDoubleOrNull() ?: 0.0
                    val appsJson = parts.drop(2).joinToString("::")
                    
                    val apps = if (appsJson.isNotEmpty()) {
                        appsJson.split("|").filter { it.isNotEmpty() }.mapNotNull { appEntry ->
                            val appParts = appEntry.split("::")
                            if (appParts.size >= 8) {
                                PowerConsumptionUtils.AppPowerData(
                                    packageName = appParts[0],
                                    appName = appParts[1],
                                    powerConsumption = appParts[2].toDoubleOrNull() ?: 0.0,
                                    foregroundTime = appParts[3].toLongOrNull() ?: 0L,
                                    backgroundTime = appParts[4].toLongOrNull() ?: 0L,
                                    totalUsageTime = appParts[5].toLongOrNull() ?: 0L,
                                    batteryImpact = appParts[6].toDoubleOrNull() ?: 0.0,
                                    timestamp = appParts[7].toLongOrNull() ?: System.currentTimeMillis()
                                )
                            } else null
                        }
                    } else emptyList()
                    
                    PowerConsumptionUtils.AppPowerSnapshot(
                        timestamp = timestamp,
                        totalSystemPower = totalSystemPower,
                        apps = apps
                    )
                } else null
            }
        } catch (e: Exception) {
            handleError(e)
            emptyList()
        }
    }
    
    /**
     * Clear app power history
     */
    fun clearAppPowerHistory(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit {
            remove(KEY_APP_POWER_SNAPSHOTS)
        }
    }
    
    /**
     * Get app power history for a specific package
     */
    fun getAppPowerHistory(context: Context, packageName: String): List<PowerConsumptionUtils.AppPowerData> {
        val snapshots = loadAppPowerSnapshots(context)
        return snapshots.flatMap { snapshot ->
            snapshot.apps.filter { it.packageName == packageName }
        }
    }
}

