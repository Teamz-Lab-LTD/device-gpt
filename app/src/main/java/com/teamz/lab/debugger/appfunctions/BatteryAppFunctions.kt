package com.teamz.lab.debugger.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import com.teamz.lab.debugger.utils.HealthScoreUtils
import com.teamz.lab.debugger.utils.getBatteryChargingInfo
import com.teamz.lab.debugger.utils.getBatteryCycleEstimate
import com.teamz.lab.debugger.utils.getBatteryTemperature
import com.teamz.lab.debugger.utils.getCompactBatteryStatus
import com.teamz.lab.debugger.utils.getThermalState
import com.teamz.lab.debugger.utils.getThermalZoneTemperatures

class BatteryAppFunctions {

    /**
     * Returns the daily battery-and-device health score (0-100) plus a plain-English summary.
     *
     * The score is DeviceGPT's composite of battery temperature, charge behaviour, per-app
     * drain, thermal state, storage health, and privacy posture.
     *
     * Use this when the user asks Gemini: "is my phone / battery healthy?",
     * "what's my battery health?", "is my phone overheating?".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getBatteryHealth(
        appFunctionContext: AppFunctionContext,
    ): BatteryHealthReport {
        val context = appFunctionContext.context
        val score = HealthScoreUtils.calculateDailyHealthScore(context)
        val summary = HealthScoreUtils.getHealthScoreMessage(score)
        val tempC = getBatteryTemperature(context)
        val thermal = getThermalState(context)
        val charging = runCatching { getBatteryChargingInfo(context) }.getOrDefault("")
        val cycles = runCatching { getBatteryCycleEstimate(context) }.getOrDefault("")
        val compact = runCatching { getCompactBatteryStatus(context) }.getOrDefault("")

        return batteryHealthReport(
            healthScore = score,
            summary = summary,
            temperatureCelsius = tempC ?: Float.NaN,
            thermalState = thermal,
            chargingInfo = charging,
            cycleEstimate = cycles,
            details = buildMap {
                put("compactStatus", compact)
                put("streakDays", HealthScoreUtils.getDailyStreak(context).toString())
                put("bestScore", HealthScoreUtils.getBestScore(context).toString())
                put("totalScans", HealthScoreUtils.getTotalScans(context).toString())
                put("lastScanDate", HealthScoreUtils.getLastScanDate(context))
            }.toDetailEntries(),
        )
    }

    /**
     * Returns the current battery temperature in Celsius and a plain-English thermal state
     * ("Cool", "Warm", "Hot", "Critical").
     *
     * Use this when the user asks: "how hot is my phone?",
     * "is my phone overheating?", "phone temperature".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getBatteryTemperatureReading(
        appFunctionContext: AppFunctionContext,
    ): ScanResult {
        val context = appFunctionContext.context
        val tempC = getBatteryTemperature(context)
        val thermal = getThermalState(context)
        val zones = runCatching { getThermalZoneTemperatures(context) }.getOrDefault("")
        val summary = if (tempC != null) {
            "Battery is ${"%.1f".format(tempC)}°C ($thermal)."
        } else {
            "Could not read battery temperature."
        }
        return scanResult(
            title = "Battery Temperature",
            summary = summary,
            grade = thermal,
            details = buildMap {
                tempC?.let { put("temperatureCelsius", it.toString()) }
                put("thermalState", thermal)
                if (zones.isNotBlank()) put("thermalZones", zones)
            }.toDetailEntries(),
        )
    }

    /**
     * Returns the live DeviceGPT health streak info: current streak (days),
     * best score ever, total scans run.
     *
     * Use this when the user asks: "how many days have I tracked my phone?",
     * "what's my best phone health score?".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getHealthStreak(
        appFunctionContext: AppFunctionContext,
    ): ScanResult {
        val context = appFunctionContext.context
        val streak = HealthScoreUtils.getDailyStreak(context)
        val best = HealthScoreUtils.getBestScore(context)
        val total = HealthScoreUtils.getTotalScans(context)
        val streakMsg = HealthScoreUtils.getStreakMessage(streak)
        return scanResult(
            title = "Health Streak",
            summary = "$streakMsg · Best score $best / 100 · $total scans total.",
            score = best,
            details = mapOf(
                "currentStreakDays" to streak.toString(),
                "bestScore" to best.toString(),
                "totalScans" to total.toString(),
            ).toDetailEntries(),
        )
    }
}
