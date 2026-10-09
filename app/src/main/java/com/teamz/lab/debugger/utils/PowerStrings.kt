package com.teamz.lab.debugger.utils

import android.content.Context
import androidx.annotation.StringRes
import com.teamz.lab.debugger.R

/**
 * Display text for values the power tab keeps in English as data.
 *
 * Component names ("CPU"), component statuses ("Permission required"), signal grades and the
 * count labels of the experiment footers are compared, stored, logged to analytics and handed
 * to the AI report in English. They must stay English there. These helpers turn them into the
 * chosen language only where a person reads them. An unknown value is shown as it is.
 */
object PowerStrings {

    private val components: Map<String, Int> = mapOf(
        "battery" to R.string.pw_comp_battery,
        "cpu" to R.string.pw_comp_cpu,
        "camera" to R.string.pw_comp_camera,
        "ram" to R.string.pw_comp_ram,
        "display" to R.string.pw_comp_display,
        "wifi" to R.string.pw_comp_wifi,
        "audio" to R.string.pw_comp_audio,
        "gps" to R.string.pw_comp_gps,
        "bluetooth" to R.string.pw_comp_bluetooth,
        "nfc" to R.string.pw_comp_nfc,
        "cellular" to R.string.pw_comp_cellular,
    )

    private val statuses: Map<String, Int> = mapOf(
        "Charging" to R.string.pw_status_charging,
        "Discharging" to R.string.pw_status_discharging,
        "Full" to R.string.pw_status_full,
        "Not Charging" to R.string.pw_status_not_charging,
        "Unknown" to R.string.pw_status_unknown,
        "Error" to R.string.pw_status_error,
        "On" to R.string.pw_status_on,
        "Off" to R.string.pw_status_off,
        "Connected" to R.string.pw_status_connected,
        "Enabled" to R.string.pw_status_enabled,
        "Disabled" to R.string.pw_status_disabled,
        "Permission required" to R.string.pw_status_perm,
        "Music Active" to R.string.pw_status_music,
        "Speakerphone" to R.string.pw_status_speakerphone,
        "Bluetooth Audio" to R.string.pw_status_bt_audio,
        "Idle" to R.string.pw_status_idle,
        "GPS + Network" to R.string.pw_status_gps_network,
        "GPS Only" to R.string.pw_status_gps_only,
        "Network Only" to R.string.pw_status_network_only,
        "Not supported" to R.string.pw_status_not_supported,
        "Not available" to R.string.pw_status_not_available,
        "Discovering" to R.string.pw_status_discovering,
        "Active (Root)" to R.string.pw_status_active_root,
        "Active (System)" to R.string.pw_status_active_system,
        "Active (Detected)" to R.string.pw_status_active_detected,
        "Estimated" to R.string.pw_status_estimated,
        "Unknown (Permission required)" to R.string.pw_status_unknown_perm,
    )

    private val signals: Map<String, Int> = mapOf(
        "Excellent" to R.string.pw_signal_excellent,
        "Very Good" to R.string.pw_signal_very_good,
        "Very good" to R.string.pw_signal_very_good_lc,
        "Good" to R.string.pw_signal_good,
        "Fair" to R.string.pw_signal_fair,
        "Weak" to R.string.pw_signal_weak,
        "No WiFi" to R.string.pw_signal_none,
    )

    private val coresActive = Regex("^(\\d+)/(\\d+) cores active$")
    private val percentUsed = Regex("^(\\d+)% used$")

    /** "CPU" -> the name a person reads. The English name stays the key everywhere else. */
    fun component(context: Context, name: String): String =
        components[name.lowercase()]?.let { context.getString(it) } ?: name

    /** A component status as built by `PowerConsumptionUtils`, in the chosen language. */
    fun status(context: Context, status: String): String {
        statuses[status]?.let { return context.getString(it) }
        coresActive.matchEntire(status)?.let {
            return context.getString(R.string.pw_status_cores, it.groupValues[1], it.groupValues[2])
        }
        percentUsed.matchEntire(status)?.let {
            return context.getString(R.string.pw_status_used, it.groupValues[1])
        }
        return status // network type names such as "4G LTE" are the same in every language
    }

    /** WiFi signal grade ("Excellent" … "Weak"); the English grade is what the code compares. */
    fun signal(context: Context, grade: String): String =
        signals[grade]?.let { context.getString(it) } ?: grade

    fun trend(context: Context, trend: PowerConsumptionAggregator.PowerTrend): String = context.getString(
        when (trend) {
            PowerConsumptionAggregator.PowerTrend.INCREASING -> R.string.pw_trend_name_increasing
            PowerConsumptionAggregator.PowerTrend.DECREASING -> R.string.pw_trend_name_decreasing
            PowerConsumptionAggregator.PowerTrend.STABLE -> R.string.pw_trend_name_stable
            PowerConsumptionAggregator.PowerTrend.UNKNOWN -> R.string.pw_trend_name_unknown
        }
    )

    /**
     * "3 tests" under an experiment's result list. [countLabel] stays English because it is also
     * an analytics value and part of an ad action name.
     */
    fun countLabel(context: Context, count: Int, countLabel: String): String {
        @StringRes val res = when (countLabel) {
            "tests" -> R.string.pw_n_tests
            "test" -> R.string.pw_n_test
            "measurements" -> R.string.pw_n_measurements
            "measurement" -> R.string.pw_n_measurement
            "levels tested" -> R.string.pw_n_levels_tested
            "level tested" -> R.string.pw_n_level_tested
            "samples" -> R.string.pw_n_samples
            "sample" -> R.string.pw_n_sample
            else -> return "$count $countLabel"
        }
        return context.getString(res, count.toString())
    }

    fun hours(context: Context, hours: Int): String =
        context.getString(if (hours > 1) R.string.pw_n_hours else R.string.pw_n_hour, hours.toString())

    fun minutes(context: Context, minutes: Int): String =
        context.getString(if (minutes > 1) R.string.pw_n_minutes else R.string.pw_n_minute, minutes.toString())

    /** "2 hours 5 minutes": two whole measures side by side, which reads naturally in Bangla too. */
    fun hoursMinutes(context: Context, hours: Int, minutes: Int): String =
        hours(context, hours) + " " + minutes(context, minutes)
}
