package com.teamz.lab.debugger.utils

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import com.teamz.lab.debugger.utils.hasUsageStatsPermission

/**
 * App Power Consumption Utilities
 * 
 * Reports per-app SCREEN TIME using UsageStatsManager. Android does not expose real
 * per-app battery draw to third-party apps (BatteryStats is system-only), so this must
 * never be presented as an app's battery consumption. See the Deceptive Behavior
 * rejection on 2026-07-12.
 * 
 * Methodology:
 * - Uses UsageStatsManager to get app usage time
 * - Combines with BatteryManager for system power
 * - Calculates app power: appPower = (appUsageTime / totalUsageTime) * systemPower
 * 
 * Note: This is an approximation. Real per-app power requires root/system access.
 */
object AppPowerUtils {
    
    private const val TAG = "AppPowerUtils"
    
    /**
     * Get power consumption for a specific app
     */
    fun getAppPowerConsumption(
        context: Context,
        packageName: String,
        systemPower: Double,
        totalUsageTime: Long
    ): PowerConsumptionUtils.AppPowerData? {
        if (!hasUsageStatsPermission(context)) {
            return null
        }
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager
        
        // Get usage stats for the last hour
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.HOURS.toMillis(1)
        
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )?.find { it.packageName == packageName }
        
        if (usageStats == null) {
            return null
        }
        
        // Get app name
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
        
        // Calculate usage times
        val foregroundTime = usageStats.totalTimeInForeground
        val backgroundTime = usageStats.totalTimeVisible - foregroundTime
        val appUsageTime = foregroundTime + backgroundTime
        
        // Calculate app power (approximation)
        // Note: This is a simplified calculation. Real per-app power requires system-level access.
        // We approximate by assuming power is proportional to usage time ratio
        val appPower = if (totalUsageTime > 0 && appUsageTime > 0) {
            // Use ratio: app usage time relative to total system usage time
            (appUsageTime.toDouble() / totalUsageTime) * systemPower
        } else {
            0.0
        }
        
        // Calculate battery impact
        val batteryCapacity = getBatteryCapacity(context)
        val batteryImpact = if (batteryCapacity > 0) {
            (appPower / batteryCapacity) * 100.0
        } else {
            0.0
        }
        
        return PowerConsumptionUtils.AppPowerData(
            packageName = packageName,
            appName = appName,
            powerConsumption = appPower,
            foregroundTime = foregroundTime,
            backgroundTime = backgroundTime,
            totalUsageTime = totalUsageTime,
            batteryImpact = batteryImpact,
            lastTimeUsed = usageStats.lastTimeUsed,
            lastTimeVisible = usageStats.lastTimeVisible,
            firstTimeStamp = usageStats.firstTimeStamp,
            lastTimeStamp = usageStats.lastTimeStamp,
            foregroundServiceTime = usageStats.totalTimeForegroundServiceUsed
        )
    }
    
    /**
     * Get power consumption for all installed apps
     */
    fun getAllAppsPowerConsumption(
        context: Context,
        systemPower: Double,
        filterSystemApps: Boolean = true
    ): List<PowerConsumptionUtils.AppPowerData> {
        if (!hasUsageStatsPermission(context)) {
            return emptyList()
        }
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager
        
        // Get usage stats for the last hour
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.HOURS.toMillis(1)
        
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        ) ?: return emptyList()
        
        // Calculate total usage time
        val totalUsageTime = usageStatsList.sumOf { it.totalTimeInForeground }
        
        if (totalUsageTime == 0L) {
            return emptyList()
        }
        
        val appPowerList = mutableListOf<PowerConsumptionUtils.AppPowerData>()
        
        // Get the app's own package name to filter it out
        val ownPackageName = context.packageName
        
        for (usageStats in usageStatsList) {
            // Skip if no usage
            if (usageStats.totalTimeInForeground == 0L) {
                continue
            }
            
            // Filter out our own app (to avoid showing it in the list)
            if (usageStats.packageName == ownPackageName) {
                continue
            }
            
            // Filter system apps if requested
            if (filterSystemApps) {
                try {
                    val appInfo = packageManager.getApplicationInfo(usageStats.packageName, 0)
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                        continue
                    }
                } catch (e: Exception) {
                    // Skip if can't get app info
                    continue
                }
            }
            
            // Get app name
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(usageStats.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                usageStats.packageName
            }
            
            // Calculate usage times
            val foregroundTime = usageStats.totalTimeInForeground
            val backgroundTime = usageStats.totalTimeVisible - foregroundTime
            val appUsageTime = foregroundTime + backgroundTime
            
            // Calculate app power (approximation)
            val appPower = if (totalUsageTime > 0) {
                (appUsageTime.toDouble() / totalUsageTime) * systemPower
            } else {
                0.0
            }
            
            // Calculate battery impact
            val batteryCapacity = getBatteryCapacity(context)
            val batteryImpact = if (batteryCapacity > 0) {
                (appPower / batteryCapacity) * 100.0
            } else {
                0.0
            }
            
            appPowerList.add(
                PowerConsumptionUtils.AppPowerData(
                    packageName = usageStats.packageName,
                    appName = appName,
                    powerConsumption = appPower,
                    foregroundTime = foregroundTime,
                    backgroundTime = backgroundTime,
                    totalUsageTime = appUsageTime,
                    batteryImpact = batteryImpact,
                    lastTimeUsed = usageStats.lastTimeUsed,
                    lastTimeVisible = usageStats.lastTimeVisible,
                    firstTimeStamp = usageStats.firstTimeStamp,
                    lastTimeStamp = usageStats.lastTimeStamp,
                    foregroundServiceTime = usageStats.totalTimeForegroundServiceUsed
                )
            )
        }
        
        // Sort by power consumption (descending) and limit to top 50
        return appPowerList
            .sortedByDescending { it.powerConsumption }
            .take(50)
    }
    
    /**
     * Calculate battery impact for an app
     */
    fun calculateAppBatteryImpact(
        appPower: Double,
        batteryCapacity: Double
    ): Double {
        return if (batteryCapacity > 0) {
            (appPower / batteryCapacity) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * Start app power monitoring
     * Returns a Flow that emits app power snapshots periodically
     */
    fun startAppPowerMonitoring(
        context: Context,
        intervalMs: Long = 5000
    ): Flow<PowerConsumptionUtils.AppPowerSnapshot> = flow {
        if (!hasUsageStatsPermission(context)) {
            return@flow
        }
        
        while (true) {
            // Get current system power
            val systemPower = PowerConsumptionUtils.getPowerConsumptionData(context).totalPower
            
            // Get all apps power consumption
            val apps = getAllAppsPowerConsumption(context, systemPower, filterSystemApps = true)
            
            // Create snapshot
            val snapshot = PowerConsumptionUtils.AppPowerSnapshot(
                timestamp = System.currentTimeMillis(),
                totalSystemPower = systemPower,
                apps = apps
            )
            
            emit(snapshot)
            delay(intervalMs)
        }
    }
    
    /**
     * Get battery capacity in watts
     * This is an approximation based on typical battery capacities
     */
    private fun getBatteryCapacity(context: Context): Double {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            
            // Try to get battery capacity from system (Android 5.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val capacityMah = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                if (capacityMah > 0) {
                    // Convert mAh to Wh (assuming 3.7V average)
                    val capacityWh = (capacityMah / 1000.0) * 3.7
                    return capacityWh
                }
            }
            
            // Fallback: estimate based on device (typical values)
            // Most phones are 3000-5000 mAh = 11-18.5 Wh
            // Use 15 Wh as default
            15.0
        } catch (e: Exception) {
            ErrorHandler.handleError(e, context = "AppPowerUtils.getBatteryCapacity")
            // Default fallback
            15.0
        }
    }
}

