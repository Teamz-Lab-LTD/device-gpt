package com.teamz.lab.debugger.utils

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * Centralized Data Collection Manager
 * 
 * REUSES existing methods - NO new data collection
 * Only aggregates and formats data from existing sources
 */
object DataCollectionManager {
    
    /**
     * Collect ALL unique data by REUSING existing methods
     * NO new data collection - only aggregation and formatting
     */
    suspend fun collectAllUniqueData(context: Context): LeaderboardEntry {
        // Get existing data from app (REUSE - don't create new)
        val powerData = PowerConsumptionAggregator.currentPowerFlow.value
        val aggregatedStats = PowerConsumptionAggregator.aggregatedStatsFlow.value
        
        // REUSE existing methods to collect data
        val powerEfficiency = collectPowerEfficiencyFromExisting(context, powerData, aggregatedStats)
        val cpuPerformance = collectCpuPerformanceFromExisting(context)
        val cameraEfficiency = collectCameraEfficiencyFromExisting(context)
        val displayEfficiency = collectDisplayEfficiencyFromExisting(context)
        val healthScore = collectHealthScoreFromExisting(context)
        val powerTrend = collectPowerTrendFromExisting(aggregatedStats)
        val componentOptimization = collectComponentOptimizationFromExisting(powerData)
        val thermalEfficiency = collectThermalEfficiencyFromExisting(context)
        val performanceConsistency = collectPerformanceConsistencyFromExisting(context)
        val userEngagement = collectUserEngagementFromExisting(context)
        
        // Calculate scores (reuse existing data structures)
        val scores = LeaderboardScoreCalculator.calculateAllScores(
            powerEfficiency, cpuPerformance, cameraEfficiency, 
            displayEfficiency, healthScore, powerTrend, 
            componentOptimization, thermalEfficiency, 
            performanceConsistency, userEngagement
        )
        
        // Normalize device name
        val normalizedDevice = DeviceNameNormalizer.normalizeDeviceName()
        
        // Calculate data quality (trust indicator)
        val dataQuality = calculateDataQuality(
            powerEfficiency, cpuPerformance, cameraEfficiency, 
            displayEfficiency, healthScore
        )
        
        return LeaderboardEntry(
            userId = "", // Will be set by LeaderboardManager
            normalizedDeviceId = normalizedDevice.normalizedId,
            hardwareId = normalizedDevice.hardwareId,
            timestamp = System.currentTimeMillis(),
            normalizedBrand = normalizedDevice.normalizedBrand,
            normalizedModel = normalizedDevice.normalizedModel,
            displayName = normalizedDevice.displayName,
            androidVersion = Build.VERSION.RELEASE,
            scores = scores,
            dataQuality = dataQuality,
            measurementCount = countMeasurements(
                cpuPerformance, cameraEfficiency, displayEfficiency
            ),
            lastMeasurementDate = getLastMeasurementDate(
                cpuPerformance, cameraEfficiency, displayEfficiency
            )
        )
    }
    
    // REUSE existing PowerConsumptionAggregator
    private fun collectPowerEfficiencyFromExisting(
        context: Context,
        powerData: PowerConsumptionUtils.PowerConsumptionSummary?,
        aggregatedStats: PowerConsumptionAggregator.PowerStats?
    ): PowerEfficiencyData {
        if (powerData == null || aggregatedStats == null) {
            return PowerEfficiencyData.empty()
        }
        
        // REUSE existing aggregated stats
        val avgPower = aggregatedStats.averagePower
        val powerTrend = aggregatedStats.powerTrend.name
        val peakPower = aggregatedStats.peakPower
        val minPower = aggregatedStats.minPower
        
        // REUSE existing component breakdown
        val componentBreakdown = powerData.components.associate { 
            it.component to it.powerConsumption 
        }
        
        // Calculate component balance
        val componentBalance = LeaderboardScoreCalculator.calculateComponentBalance(componentBreakdown)
        
        return PowerEfficiencyData(
            avgPowerConsumption = avgPower,
            powerTrend = powerTrend,
            peakPower = peakPower,
            minPower = minPower,
            componentBreakdown = componentBreakdown,
            efficiencyRating = PowerConsumptionAggregator.getEfficiencyRating(avgPower),
            score = LeaderboardScoreCalculator.calculatePowerEfficiencyScore(
                avgPower, powerTrend, componentBalance
            )
        )
    }
    
    // REUSE existing CPU test results
    private fun collectCpuPerformanceFromExisting(context: Context): CpuPerformanceData {
        // REUSE existing load function
        val microbenchResults = PowerConsumptionAggregator.loadCpuTestResults(context) ?: emptyList()
        
        // REUSE existing CPU info functions
        val coreCount = Runtime.getRuntime().availableProcessors()
        val frequencies = getCpuFrequenciesFromSysfs(coreCount)
        val cpuInfo = getCompactCpuInfo() // REUSE existing function
        
        // Calculate from existing data
        val avgDeltaPower = if (microbenchResults.isNotEmpty()) {
            microbenchResults.map { it.deltaPowerW }.average()
        } else 0.0
        
        val avgUtilization = if (microbenchResults.isNotEmpty()) {
            microbenchResults.map { it.observedUtilPercent.toDouble() }.average()
        } else 0.0
        
        val cpuEfficiency = if (avgDeltaPower > 0) avgUtilization / avgDeltaPower else 0.0
        
        return CpuPerformanceData(
            microbenchResults = microbenchResults, // REUSE existing data structure
            avgDeltaPower = avgDeltaPower,
            cpuEfficiency = cpuEfficiency,
            realTimeFrequencies = frequencies.mapIndexed { index, freq -> index to freq }.toMap(),
            activeCores = frequencies.count { it != -1 },
            idleCores = frequencies.count { it == -1 },
            usagePercent = calculateCpuUsagePercentFromFrequencies(frequencies),
            score = LeaderboardScoreCalculator.calculateCpuPerformanceScore(microbenchResults)
        )
    }
    
    // Helper to get CPU frequencies (reusing approach from PowerConsumptionUtils)
    private fun getCpuFrequenciesFromSysfs(coreCount: Int): List<Int> {
        val frequencies = mutableListOf<Int>()
        for (i in 0 until coreCount) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
            try {
                val freq = File(path).readText().trim().toIntOrNull()?.div(1000) ?: -1
                frequencies.add(freq)
            } catch (e: Exception) {
                frequencies.add(-1)
            }
        }
        return frequencies
    }
    
    private fun calculateCpuUsagePercentFromFrequencies(frequencies: List<Int>): Int {
        if (frequencies.isEmpty()) return 0
        val activeCores = frequencies.count { it > 0 }
        return (activeCores * 100) / frequencies.size
    }
    
    // REUSE existing camera test results
    private fun collectCameraEfficiencyFromExisting(context: Context): CameraEfficiencyData {
        // REUSE existing load function
        val cameraResults = PowerConsumptionAggregator.loadCameraTestResults(context)
        
        if (cameraResults.isEmpty()) {
            return CameraEfficiencyData.empty()
        }
        
        // Calculate from existing data
        val avgEnergyPerPhoto = cameraResults.map { result ->
            // REUSE existing energy calculation
            // Energy (Joules) = Power (W) × Time (s)
            val timeSeconds = result.captureDuration / 1000.0
            result.powerDifference * timeSeconds
        }.average()
        
        val avgPower = cameraResults.map { it.afterCapture }.average()
        val baselinePower = cameraResults.first().baselinePower
        val previewPower = cameraResults.first().previewPower
        val capturePower = cameraResults.map { it.afterCapture }.average()
        
        return CameraEfficiencyData(
            energyPerPhoto = avgEnergyPerPhoto,
            avgPowerConsumption = avgPower,
            baselinePower = baselinePower,
            previewPower = previewPower,
            capturePower = capturePower,
            testCount = cameraResults.size,
            lastTestDate = cameraResults.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis(),
            score = LeaderboardScoreCalculator.calculateCameraEfficiencyScore(avgEnergyPerPhoto)
        )
    }
    
    // REUSE existing display test results
    private fun collectDisplayEfficiencyFromExisting(context: Context): DisplayEfficiencyData {
        // REUSE existing load function
        val displayResults = PowerConsumptionAggregator.loadDisplayTestResults(context) ?: emptyList()
        
        if (displayResults.isEmpty()) {
            return DisplayEfficiencyData.empty()
        }
        
        // Calculate from existing data
        val avgBrightness = displayResults.map { it.brightnessLevel.toDouble() }.average()
        val avgPower = displayResults.map { it.powerW }.average()
        val displayEfficiency = if (avgPower > 0) avgBrightness / avgPower else 0.0
        val optimalBrightness = displayResults.minByOrNull { it.powerW }?.brightnessLevel ?: 50
        
        return DisplayEfficiencyData(
            powerSweepResults = displayResults, // REUSE existing data structure
            avgPowerPerBrightness = if (avgBrightness > 0) avgPower / avgBrightness else 0.0,
            optimalBrightness = optimalBrightness,
            displayEfficiency = displayEfficiency,
            score = LeaderboardScoreCalculator.calculateDisplayEfficiencyScore(displayResults)
        )
    }
    
    // REUSE existing HealthScoreUtils
    private suspend fun collectHealthScoreFromExisting(context: Context): HealthScoreData {
        // REUSE existing functions
        val currentScore = HealthScoreUtils.calculateDailyHealthScore(context)
        val bestScore = HealthScoreUtils.getBestScore(context)
        val streak = HealthScoreUtils.getDailyStreak(context)
        val totalScans = HealthScoreUtils.getTotalScans(context)
        
        // Calculate trend from existing history
        val history = HealthScoreUtils.getHealthScoreHistory(context, 7)
        val trend = calculateHealthTrend(history)
        
        return HealthScoreData(
            currentScore = currentScore,
            bestScore = bestScore,
            streak = streak,
            totalScans = totalScans,
            trend = trend,
            score = LeaderboardScoreCalculator.calculateHealthScore(currentScore, streak, totalScans)
        )
    }
    
    private fun calculateHealthTrend(history: List<Pair<String, Int>>): String {
        if (history.size < 2) return "UNKNOWN"
        val recent = history.takeLast(3).map { it.second }.average()
        val older = history.dropLast(3).takeLast(3).map { it.second }.average()
        return when {
            recent > older + 0.5 -> "IMPROVING"
            recent < older - 0.5 -> "DECLINING"
            else -> "STABLE"
        }
    }
    
    // REUSE existing power trend
    private fun collectPowerTrendFromExisting(
        aggregatedStats: PowerConsumptionAggregator.PowerStats?
    ): PowerTrendData {
        if (aggregatedStats == null) {
            return PowerTrendData(
                trend = "UNKNOWN",
                trendStrength = 0.0,
                daysTracked = 0,
                improvementPercent = 0.0,
                score = 0.0
            )
        }
        
        val trend = aggregatedStats.powerTrend.name
        val improvementPercent = when (trend) {
            "DECREASING" -> 5.0 // Estimate improvement
            "STABLE" -> 0.0
            else -> -5.0
        }
        
        return PowerTrendData(
            trend = trend,
            trendStrength = 1.0,
            daysTracked = 1, // Simplified
            improvementPercent = improvementPercent,
            score = LeaderboardScoreCalculator.calculatePowerTrendScore(trend, improvementPercent)
        )
    }
    
    // REUSE existing component optimization
    private fun collectComponentOptimizationFromExisting(
        powerData: PowerConsumptionUtils.PowerConsumptionSummary?
    ): ComponentOptimizationData {
        if (powerData == null) {
            return ComponentOptimizationData(
                topConsumers = emptyList(),
                maxComponentPower = 0.0,
                balanceScore = 0.0,
                componentBreakdown = emptyMap(),
                score = 0.0
            )
        }
        
        val componentBreakdown = powerData.components.associate { 
            it.component to it.powerConsumption 
        }
        val maxComponentPower = componentBreakdown.values.maxOrNull() ?: 0.0
        val balanceScore = LeaderboardScoreCalculator.calculateComponentBalance(componentBreakdown)
        
        // Convert to ComponentPowerStats
        val topConsumers = powerData.components.map { component ->
            PowerConsumptionAggregator.ComponentPowerStats(
                component = component.component,
                averagePower = component.powerConsumption,
                peakPower = component.powerConsumption,
                usagePercentage = if (powerData.totalPower > 0) {
                    (component.powerConsumption / powerData.totalPower) * 100.0
                } else 0.0
            )
        }
        
        return ComponentOptimizationData(
            topConsumers = topConsumers,
            maxComponentPower = maxComponentPower,
            balanceScore = balanceScore,
            componentBreakdown = componentBreakdown,
            score = LeaderboardScoreCalculator.calculateComponentOptimizationScore(
                maxComponentPower, balanceScore
            )
        )
    }
    
    // REUSE existing thermal functions with BatteryManager fallback
    private suspend fun collectThermalEfficiencyFromExisting(context: Context): ThermalEfficiencyData {
        return withContext(Dispatchers.IO) {
            // REUSE existing function - try thermal zones first, with BatteryManager fallback
            val thermalStatus = getThermalZoneTemperatures(context)
            
            // REUSE utility functions from device_utils
            var cpuTemp = extractTemperature(thermalStatus, "CPU")
            var batteryTemp = extractTemperature(thermalStatus, "Battery")
            var gpuTemp = extractTemperature(thermalStatus, "GPU")
            
            // FALLBACK: Use BatteryManager API if thermal zones are not accessible (works on all devices)
            if (batteryTemp == null) {
                batteryTemp = getBatteryTemperature(context)
                if (batteryTemp != null) {
                    android.util.Log.d("DataCollectionManager", "Using BatteryManager temperature: ${batteryTemp}°C")
                } else {
                    android.util.Log.w("DataCollectionManager", "⚠️ No battery temperature available from BatteryManager")
                }
            }
            
            // REUSE utility function for thermal state
            val thermalState = getThermalState(context)
            
            // Calculate average temperature - use battery temp as fallback if no thermal zone data
            val temperatures = listOfNotNull(cpuTemp, batteryTemp, gpuTemp)
            val avgTemperature = if (temperatures.isNotEmpty()) {
                temperatures.average()
            } else batteryTemp?.// Use battery temp as estimate if no other temps available
            toDouble() ?: 0.0
            
            // REUSE utility function to build thermal zone temperatures map
            val thermalZoneTemps = extractAllTemperatures(thermalStatus).toMutableMap()
            if (batteryTemp != null && !thermalZoneTemps.containsKey("Battery")) {
                thermalZoneTemps["Battery"] = batteryTemp
            }
            
            val thermalScore = LeaderboardScoreCalculator.calculateThermalEfficiencyScore(avgTemperature)
            
            // Debug logging for thermal data
            android.util.Log.d("DataCollectionManager", "🌡️ Thermal Data Collection:")
            android.util.Log.d("DataCollectionManager", "  - CPU Temp: ${cpuTemp ?: "N/A"}°C")
            android.util.Log.d("DataCollectionManager", "  - Battery Temp: ${batteryTemp ?: "N/A"}°C")
            android.util.Log.d("DataCollectionManager", "  - GPU Temp: ${gpuTemp ?: "N/A"}°C")
            android.util.Log.d("DataCollectionManager", "  - Avg Temperature: ${avgTemperature}°C")
            android.util.Log.d("DataCollectionManager", "  - Thermal State: $thermalState")
            android.util.Log.d("DataCollectionManager", "  - Thermal Score: $thermalScore/100")
            
            if (avgTemperature <= 0) {
                android.util.Log.w("DataCollectionManager", "⚠️ WARNING: avgTemperature is 0! Thermal score will be 0. Check battery temperature collection.")
            }
            
            ThermalEfficiencyData(
                cpuTemperature = cpuTemp,
                batteryTemperature = batteryTemp,
                gpuTemperature = gpuTemp,
                thermalState = thermalState,
                thermalZoneTemperatures = thermalZoneTemps,
                avgTemperature = avgTemperature,
                score = thermalScore
            )
        }
    }
    
    // REUSE existing FPS monitoring with caching support
    private fun collectPerformanceConsistencyFromExisting(context: Context): PerformanceConsistencyData {
        var fpsData: String? = null
        var fps = 0
        var frameDropRate = 0.0
        
        // Check if we're on main thread
        val isMainThread = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
        
        if (isMainThread) {
            // We're on main thread, can safely call getCompactFpsAndDropRate
            val latch = java.util.concurrent.CountDownLatch(1)
            var cleanup: (() -> Unit)? = null
            cleanup = getCompactFpsAndDropRate { data ->
                fpsData = data
                fps = extractFPS(data)
                frameDropRate = extractFrameDropRate(data)
                
                // CACHE the FPS data for future use (when on background thread)
                FpsDataCache.saveFpsData(context, fps, frameDropRate, data)
                
                latch.countDown()
            }
            
            try {
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: Exception) {
                // Timeout - try to use cached data
                android.util.Log.w("DataCollectionManager", "FPS collection timeout, trying cache...")
            } finally {
                // Always clean up the callback
                cleanup?.invoke()
            }
        } else {
            // We're on background thread - try to use cached FPS data
            android.util.Log.d("DataCollectionManager", "On background thread - using cached FPS data if available")
            val cachedData = FpsDataCache.getCachedFpsData(context)
            
            if (cachedData != null) {
                // Use cached data
                fps = cachedData.fps
                frameDropRate = cachedData.frameDropRate
                fpsData = cachedData.fpsDataString
                android.util.Log.d("DataCollectionManager", "✅ Using cached FPS data: FPS=$fps, DropRate=$frameDropRate%")
            } else {
                // No cached data available - will use default values (0)
                android.util.Log.w("DataCollectionManager", "⚠️ No cached FPS data available - using default values")
            }
        }
        
        // If we still don't have data, try cache one more time (in case main thread collection failed)
        if (fps == 0 && frameDropRate == 0.0) {
            val cachedData = FpsDataCache.getCachedFpsData(context)
            if (cachedData != null) {
                fps = cachedData.fps
                frameDropRate = cachedData.frameDropRate
                fpsData = cachedData.fpsDataString
                android.util.Log.d("DataCollectionManager", "✅ Using cached FPS data (fallback): FPS=$fps, DropRate=$frameDropRate%")
            }
        }
        
        return PerformanceConsistencyData(
            avgFPS = fps,
            frameDropRate = frameDropRate,
            consistencyScore = calculateConsistencyScore(fps, frameDropRate),
            score = LeaderboardScoreCalculator.calculatePerformanceConsistencyScore(fps, frameDropRate)
        )
    }
    
    private fun extractFPS(fpsData: String): Int {
        val pattern = Pattern.compile("(\\d+)\\s*FPS", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(fpsData)
        return if (matcher.find()) {
            matcher.group(1)?.toIntOrNull() ?: 0
        } else 0
    }
    
    private fun extractFrameDropRate(fpsData: String): Double {
        val pattern = Pattern.compile("([\\d.]+)%", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(fpsData)
        return if (matcher.find()) {
            matcher.group(1)?.toDoubleOrNull() ?: 0.0
        } else 0.0
    }
    
    private fun calculateConsistencyScore(avgFPS: Int, frameDropRate: Double): Double {
        val fpsScore = (avgFPS / 60.0) * 50.0
        val dropPenalty = frameDropRate * 0.5
        return (fpsScore - dropPenalty).coerceIn(0.0, 100.0)
    }
    
    // REUSE existing engagement data
    private fun collectUserEngagementFromExisting(context: Context): UserEngagementData {
        // REUSE existing functions
        val streak = HealthScoreUtils.getDailyStreak(context)
        val totalScans = HealthScoreUtils.getTotalScans(context)
        val lastScanDate = HealthScoreUtils.getLastScanDate(context)
        
        val daysSinceFirstScan = calculateDaysSinceFirstScan(context, lastScanDate)
        val avgScanFrequency = if (daysSinceFirstScan > 0) {
            totalScans.toDouble() / daysSinceFirstScan
        } else 0.0
        
        return UserEngagementData(
            dailyStreak = streak,
            totalScans = totalScans,
            avgScanFrequency = avgScanFrequency,
            score = LeaderboardScoreCalculator.calculateUserEngagementScore(streak, totalScans)
        )
    }
    
    private fun calculateDaysSinceFirstScan(context: Context, lastScanDate: String): Int {
        if (lastScanDate.isEmpty()) return 1
        try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val lastDate = format.parse(lastScanDate)
            val now = java.util.Date()
            val diff = now.time - (lastDate?.time ?: now.time)
            return (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
        } catch (e: Exception) {
            return 1
        }
    }
    
    // Helper functions
    private fun calculateDataQuality(
        powerEfficiency: PowerEfficiencyData,
        cpuPerformance: CpuPerformanceData,
        cameraEfficiency: CameraEfficiencyData,
        displayEfficiency: DisplayEfficiencyData,
        healthScore: HealthScoreData
    ): Int {
        var quality = 0
        
        // Check if we have real measurements (not empty)
        if (powerEfficiency.avgPowerConsumption > 0) quality++
        if (cpuPerformance.microbenchResults.isNotEmpty()) quality++
        if (cameraEfficiency.testCount > 0) quality++
        if (displayEfficiency.powerSweepResults.isNotEmpty()) quality++
        if (healthScore.currentScore > 0) quality++
        
        return quality // 0-5 scale
    }
    
    private fun countMeasurements(
        cpuPerformance: CpuPerformanceData,
        cameraEfficiency: CameraEfficiencyData,
        displayEfficiency: DisplayEfficiencyData
    ): Int {
        return cpuPerformance.microbenchResults.size + 
               cameraEfficiency.testCount + 
               displayEfficiency.powerSweepResults.size
    }
    
    private fun getLastMeasurementDate(
        cpuPerformance: CpuPerformanceData,
        cameraEfficiency: CameraEfficiencyData,
        displayEfficiency: DisplayEfficiencyData
    ): Long {
        val dates = listOfNotNull(
            cpuPerformance.microbenchResults.maxOfOrNull { it.timestamp },
            cameraEfficiency.lastTestDate,
            displayEfficiency.powerSweepResults.maxOfOrNull { it.timestamp }
        )
        return dates.maxOrNull() ?: System.currentTimeMillis()
    }
}

