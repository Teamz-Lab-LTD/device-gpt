package com.teamz.lab.debugger.utils

import android.content.Context
import com.teamz.lab.debugger.R

/**
 * Puts the rows of the always-on notification into the app language at the moment they are shown.
 *
 * The rows come from `getCompactBatteryStatus`, `getCompactCpuInfo`, `getCompactFpsAndDropRate`
 * and `getCompactPowerState` as English text, and the same text is stored for the widget, which
 * reads numbers back out of it (`FPS: 59`, `(72%)`). So the stored value stays English and only
 * the notification text goes through here.
 *
 * Every function returns its input unchanged when the app language is English or the text is
 * not in the shape it expects.
 */
object MonitorDisplayText {

    private fun isEnglish(context: Context): Boolean =
        context.resources.configuration.locales[0].language == "en"

    /** "Charging at 5.2W • ⏳ 12 min left • 🔥 31.0°C" */
    fun battery(context: Context, raw: String): String {
        if (isEnglish(context)) return raw
        if (raw == "Battery Info N/A") return context.string(R.string.mx_battery_info_unavailable)
        return raw.split(" • ").joinToString(" • ") { part -> batteryPart(context, part) }
    }

    private fun batteryPart(context: Context, part: String): String {
        MINUTES_LEFT.find(part)?.let {
            return context.string(R.string.mx_monitor_battery_minutes_left, it.groupValues[1])
        }
        return when {
            part.startsWith("Charging at ") -> withPower(
                context, part.removePrefix("Charging at "),
                R.string.mx_battery_status_charging, R.string.mx_battery_status_charging_plain,
            )
            part.startsWith("Discharging at ") -> withPower(
                context, part.removePrefix("Discharging at "),
                R.string.mx_battery_status_discharging, R.string.mx_battery_status_discharging_plain,
            )
            part == "Full" -> context.string(R.string.mx_monitor_battery_full)
            part == "Not Charging" -> context.string(R.string.mx_battery_status_not_charging)
            part == "Unknown Status" -> context.string(R.string.mx_battery_status_unknown)
            else -> part
        }
    }

    private fun withPower(context: Context, power: String, withPowerId: Int, plainId: Int): String =
        if (power == "N/A") context.string(plainId) else context.string(withPowerId, power)

    /** "CPU: 1.20 / 2.40 GHz (50%)• 8 active cores" */
    fun cpu(context: Context, raw: String): String {
        if (isEnglish(context)) return "⚙️ $raw"
        val match = CPU.find(raw) ?: return "⚙️ $raw"
        return context.string(R.string.mx_monitor_row_cpu, match.groupValues[2], match.groupValues[1])
    }

    /** "FPS: 59 • Drop Rate: 1.0%" */
    fun fps(context: Context, raw: String): String {
        if (isEnglish(context)) return raw
        if (raw == "Initializing...") return context.string(R.string.mx_monitor_fps_starting)
        val match = FPS.find(raw) ?: return raw
        return context.string(R.string.mx_monitor_fps_value, match.groupValues[1], match.groupValues[2])
    }

    /** One part of `getCompactPowerState`: the heat level, the battery-saver state or the doze state. */
    fun powerState(context: Context, raw: String): String {
        if (isEnglish(context)) return raw
        val id = when (raw) {
            "Normal" -> R.string.mx_monitor_thermal_normal
            "Warm" -> R.string.mx_monitor_thermal_warm
            "Hot" -> R.string.mx_monitor_thermal_hot
            "Critical" -> R.string.mx_monitor_thermal_critical
            "Overheat" -> R.string.mx_monitor_thermal_overheat
            "N/A" -> R.string.mx_battery_value_not_available
            "⚡Saver On" -> R.string.mx_monitor_saver_on
            "⚡Saver Off" -> R.string.mx_monitor_saver_off
            "😴Dozing" -> R.string.mx_monitor_dozing
            "⏱️Doze inactive" -> R.string.mx_monitor_doze_inactive
            else -> return raw
        }
        return context.string(id)
    }

    private val MINUTES_LEFT = Regex("""^⏳ (\d+) min left$""")
    private val CPU = Regex("""^CPU: ([\d.]+ / [\d.]+ GHz) \((\d+)%\)""")
    private val FPS = Regex("""^FPS: (\d+) • Drop Rate: ([\d.]+)%$""")
}
