package com.teamz.lab.debugger.utils

import android.content.Context
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator.PowerStats
import com.teamz.lab.debugger.utils.PowerConsumptionUtils.PowerConsumptionSummary

/**
 * Power Consumption Alerts System
 * Monitors power consumption and triggers alerts for anomalies
 */
object PowerAlerts {
    
    data class PowerAlert(
        val type: AlertType,
        val title: String,
        val message: String,
        val severity: Severity,
        val component: String? = null,
        val powerValue: Double? = null
    )
    
    enum class AlertType {
        HIGH_POWER_CONSUMPTION,
        POWER_SPIKE,
        COMPONENT_ANOMALY,
        BATTERY_DRAIN,
        TREND_WARNING
    }
    
    enum class Severity {
        CRITICAL, WARNING, INFO
    }
    
    // Thresholds based on research and typical device power consumption
    private const val HIGH_TOTAL_POWER_THRESHOLD = 10000.0 // 10W
    private const val CRITICAL_TOTAL_POWER_THRESHOLD = 15000.0 // 15W
    private const val HIGH_COMPONENT_POWER_THRESHOLD = 3000.0 // 3W per component
    private const val POWER_SPIKE_THRESHOLD_MULTIPLIER = 2.0 // 2x average = spike
    
    /**
     * Check for power consumption alerts based on current data
     */
    fun checkAlerts(
        context: Context,
        powerData: PowerConsumptionSummary?,
        aggregatedStats: PowerStats?
    ): List<PowerAlert> {
        val alerts = mutableListOf<PowerAlert>()
        
        if (powerData == null) return alerts

        // The caller is often the monitor service, whose context may predate a language switch.
        val ctx = LocaleManager.localizedContext(context)
        
        // Check total power consumption
        alerts.addAll(checkTotalPowerAlerts(ctx, powerData, aggregatedStats))
        
        // Check component-specific alerts
        alerts.addAll(checkComponentAlerts(ctx, powerData))
        
        // Check power spikes
        alerts.addAll(checkPowerSpikes(ctx, powerData, aggregatedStats))
        
        // Check trend warnings
        alerts.addAll(checkTrendWarnings(ctx, aggregatedStats))
        
        return alerts
    }
    
    private fun checkTotalPowerAlerts(
        context: Context,
        powerData: PowerConsumptionSummary,
        stats: PowerStats?
    ): List<PowerAlert> {
        val alerts = mutableListOf<PowerAlert>()
        val totalPower = powerData.totalPower
        
        if (totalPower >= CRITICAL_TOTAL_POWER_THRESHOLD) {
            alerts.add(
                PowerAlert(
                    type = AlertType.HIGH_POWER_CONSUMPTION,
                    title = context.getString(R.string.pw_alert_critical_title),
                    message = context.getString(R.string.pw_alert_critical_msg, String.format("%.1f", totalPower / 1000)),
                    severity = Severity.CRITICAL,
                    powerValue = totalPower
                )
            )
        } else if (totalPower >= HIGH_TOTAL_POWER_THRESHOLD) {
            alerts.add(
                PowerAlert(
                    type = AlertType.HIGH_POWER_CONSUMPTION,
                    title = context.getString(R.string.pw_alert_high_title),
                    message = context.getString(R.string.pw_alert_high_msg, String.format("%.1f", totalPower / 1000)),
                    severity = Severity.WARNING,
                    powerValue = totalPower
                )
            )
        }
        
        return alerts
    }
    
    private fun checkComponentAlerts(
        context: Context,
        powerData: PowerConsumptionSummary
    ): List<PowerAlert> {
        val alerts = mutableListOf<PowerAlert>()
        
        powerData.components.forEach { component ->
            if (component.powerConsumption >= HIGH_COMPONENT_POWER_THRESHOLD) {
                alerts.add(
                    PowerAlert(
                        type = AlertType.COMPONENT_ANOMALY,
                        title = context.getString(R.string.pw_alert_component_title, PowerStrings.component(context, component.component)),
                        message = context.getString(R.string.pw_alert_component_msg,
                            PowerStrings.component(context, component.component),
                            String.format("%.1f", component.powerConsumption / 1000)
                        ),
                        severity = Severity.WARNING,
                        component = component.component,
                        powerValue = component.powerConsumption
                    )
                )
            }
        }
        
        return alerts
    }
    
    private fun checkPowerSpikes(
        context: Context,
        powerData: PowerConsumptionSummary,
        stats: PowerStats?
    ): List<PowerAlert> {
        val alerts = mutableListOf<PowerAlert>()
        
        stats?.let {
            val averagePower = it.averagePower
            val currentPower = powerData.totalPower
            
            // Check if current power is significantly higher than average
            if (averagePower > 0 && currentPower >= averagePower * POWER_SPIKE_THRESHOLD_MULTIPLIER) {
                alerts.add(
                    PowerAlert(
                        type = AlertType.POWER_SPIKE,
                        title = context.getString(R.string.pw_alert_spike_title),
                        message = context.getString(R.string.pw_alert_spike_msg,
                            String.format("%.1f", currentPower / 1000),
                            String.format("%.1f", (currentPower / averagePower))
                        ),
                        severity = Severity.WARNING,
                        powerValue = currentPower
                    )
                )
            }
        }
        
        return alerts
    }
    
    private fun checkTrendWarnings(
        context: Context,
        stats: PowerStats?
    ): List<PowerAlert> {
        val alerts = mutableListOf<PowerAlert>()
        
        stats?.let {
            when (it.powerTrend) {
                PowerConsumptionAggregator.PowerTrend.INCREASING -> {
                    if (it.averagePower > HIGH_TOTAL_POWER_THRESHOLD) {
                        alerts.add(
                            PowerAlert(
                                type = AlertType.TREND_WARNING,
                                title = context.getString(R.string.pw_alert_trend_title),
                                message = context.getString(R.string.pw_alert_trend_msg),
                                severity = Severity.WARNING
                            )
                        )
                    }
                }
                else -> {}
            }
        }
        
        return alerts
    }
    
    /**
     * Get battery drain warning based on power consumption
     */
    fun getBatteryDrainEstimate(
        context: Context,
        powerData: PowerConsumptionSummary?,
        batteryCapacityMah: Int = 4000 // Default 4000mAh
    ): String? {
        if (powerData == null) return null
        
        val totalPowerWatts = powerData.totalPower / 1000.0 // Convert mW to W
        if (totalPowerWatts <= 0) return null
        
        // Estimate battery voltage (typically 3.7V for Li-ion)
        val batteryVoltage = 3.7
        val currentAmps = totalPowerWatts / batteryVoltage
        val currentMah = currentAmps * 1000
        
        // Calculate hours until battery drain
        val hoursUntilDrain = batteryCapacityMah / currentMah
        
        if (hoursUntilDrain < 4) {
            return LocaleManager.localizedContext(context)
                .getString(R.string.pw_alert_drain_estimate, String.format("%.1f", hoursUntilDrain))
        }
        
        return null
    }
}

