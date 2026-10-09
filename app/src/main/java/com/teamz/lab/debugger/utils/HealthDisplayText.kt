package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.teamz.lab.debugger.R
import java.util.Locale

/**
 * Turns English text that other code compares, stores or sends to an AI into the text a person
 * reads, at the moment it is shown.
 *
 * The builders behind these lines (`getBatteryChargingInfo`, `clearRam`, `optimizeBattery`,
 * `privacyThreatsFromSignals`, `HealthScoreUtils.getTemperatureTrend`, the item titles passed to
 * the AI dialog) return English on purpose: `"Battery Full"` and `"Overheat"` are matched with
 * `contains`, `"Not enough data"` with `!=`, and the item titles build a file name. So the value
 * stays English and only the display goes through here.
 *
 * Every function returns its input unchanged when the app language is English, or when the
 * text is not one it knows. A line that was already translated at its source passes through.
 */
object HealthDisplayText {

    private const val LANGUAGE_ENGLISH = "en"

    private fun isEnglish(context: Context): Boolean =
        context.resources.configuration.locales[0].language == LANGUAGE_ENGLISH

    /**
     * A context that resolves string resources in English, for text that is handed to an AI app
     * or another program rather than read on the screen.
     */
    fun englishContext(context: Context): Context =
        context.createConfigurationContext(Configuration().apply { setLocale(Locale.ENGLISH) })

    /**
     * Returns [english] when the app language is English, and the string [fallback] otherwise.
     * For messages built far from the screen (a sign-in error, an exception text) that a Bangla
     * reader cannot read: English keeps the exact message, Bangla gets a plain sentence.
     */
    fun englishOr(context: Context, english: String, @StringRes fallback: Int): String =
        if (isEnglish(context)) english else context.string(fallback)

    /** The battery card's rows, as returned by `getBatteryChargingInfo`. */
    fun batteryInfo(context: Context, raw: String): String {
        if (isEnglish(context)) return raw
        if (raw.trim() == "Battery Info Unavailable") return context.string(R.string.mx_battery_info_unavailable)
        return raw.lines().joinToString("\n") { batteryRow(context, it) }
    }

    private fun batteryRow(context: Context, line: String): String {
        val text = line.trim()
        Regex("""^🔋 Estimated Cycles \(Approximation\): ~(\d+) cycles$""").find(text)?.let {
            return context.string(R.string.mx_battery_row_cycles, it.groupValues[1])
        }
        val value = text.substringAfter(": ", missingDelimiterValue = "").trim()
        return when {
            text.startsWith("🔋 Battery Status:") ->
                context.string(R.string.mx_battery_row_status, batteryStatus(context, value))
            text.startsWith("🔌 Charge Type:") ->
                context.string(R.string.mx_battery_row_charge_type, chargerType(context, value))
            text.startsWith("🔥 Battery Temp:") -> context.string(R.string.mx_battery_row_temp, value)
            text.startsWith("📊 Battery Health:") ->
                context.string(R.string.mx_battery_row_health, batteryHealth(context, value))
            text.startsWith("🔬 Battery Tech:") ->
                context.string(R.string.mx_battery_row_tech, unknownOr(context, value))
            text.startsWith("⏳ Estimated Full Charge:") ->
                context.string(R.string.mx_battery_row_full_in, timeToFull(context, value))
            text.startsWith("📅 Predicted Battery Life Remaining:") ->
                context.string(R.string.mx_battery_row_life, predictedLife(context, value))
            else -> line
        }
    }

    private fun batteryStatus(context: Context, value: String): String = when {
        value.startsWith("Charging at ") -> powerLine(
            context, value.removePrefix("Charging at "),
            R.string.mx_battery_status_charging, R.string.mx_battery_status_charging_plain,
        )
        value.startsWith("Discharging at ") -> powerLine(
            context, value.removePrefix("Discharging at "),
            R.string.mx_battery_status_discharging, R.string.mx_battery_status_discharging_plain,
        )
        value.startsWith("Battery Full") -> context.string(R.string.mx_battery_status_full)
        value == "Not Charging" -> context.string(R.string.mx_battery_status_not_charging)
        value == "Unknown Status" -> context.string(R.string.mx_battery_status_unknown)
        else -> value
    }

    /** "5.20 W" is a reading and is shown; "Not Available" is not worth a line of its own. */
    private fun powerLine(context: Context, power: String, @StringRes withPower: Int, @StringRes plain: Int): String =
        if (power == "Not Available") context.string(plain) else context.string(withPower, power)

    private fun chargerType(context: Context, value: String): String = when {
        value.startsWith("USB") -> context.string(R.string.mx_battery_plug_usb)
        value.startsWith("Fast Charging") -> context.string(R.string.mx_battery_plug_ac)
        value.startsWith("Wireless") -> context.string(R.string.mx_battery_plug_wireless)
        value == "Not Charging" -> context.string(R.string.mx_battery_plug_none)
        else -> value
    }

    private fun batteryHealth(context: Context, value: String): String = when {
        value.startsWith("Good") -> context.string(R.string.mx_battery_health_good)
        value.startsWith("Overheat") -> context.string(R.string.mx_battery_health_overheat)
        value.startsWith("Dead") -> context.string(R.string.mx_battery_health_dead)
        value.startsWith("Overvoltage") -> context.string(R.string.mx_battery_health_overvoltage)
        value.startsWith("Failure") -> context.string(R.string.mx_battery_health_failure)
        value.startsWith("Cold") -> context.string(R.string.mx_battery_health_cold)
        else -> unknownOr(context, value)
    }

    private fun unknownOr(context: Context, value: String): String =
        if (value == "Unknown") context.string(R.string.mx_battery_value_unknown) else value

    private fun timeToFull(context: Context, value: String): String {
        Regex("""^(\d+) min left$""").find(value)?.let {
            return context.string(R.string.mx_battery_minutes_left, it.groupValues[1])
        }
        return if (value == "N/A") context.string(R.string.mx_battery_value_not_available) else value
    }

    private fun predictedLife(context: Context, value: String): String = when {
        value.startsWith("~1+ year") -> context.string(R.string.mx_battery_life_long)
        value.startsWith("~6–12 months") -> context.string(R.string.mx_battery_life_short)
        else -> value
    }

    /** One privacy risk name, as returned by `privacyThreatsFromSignals`. */
    fun privacyThreat(context: Context, raw: String): String {
        if (isEnglish(context)) return raw
        val id = when (raw) {
            "An installed app can capture or read your screen" -> R.string.mx_threat_screen_capture
            "USB debugging enabled (security risk)" -> R.string.mx_threat_usb_debugging
            "Device is rooted (security risk)" -> R.string.mx_threat_rooted
            "SSL certificate issues detected" -> R.string.mx_threat_ssl
            "A GPS spoofing app is installed" -> R.string.mx_threat_gps_spoofing
            else -> return raw
        }
        return context.string(id)
    }

    /**
     * The line shown under the memory, storage and battery buttons, as returned by `clearRam`,
     * `clearStorageCache`, `clearAppCacheWithPermission` and `optimizeBattery`.
     */
    fun actionResult(context: Context, raw: String): String {
        if (isEnglish(context)) return raw
        MEMORY_RESULT.find(raw)?.let { m ->
            val id = if (m.groupValues[1].startsWith("Memory pressure")) {
                R.string.mx_result_memory_pressure
            } else {
                R.string.mx_result_memory_current
            }
            return context.string(id, m.groupValues[2], m.groupValues[3], m.groupValues[4])
        }
        FREED_FROM_APPS.find(raw)?.let { m ->
            return context.string(R.string.mx_result_storage_freed_apps, m.groupValues[1], m.groupValues[2])
        }
        FREED.find(raw)?.let { m ->
            return context.string(R.string.mx_result_storage_freed, m.groupValues[1])
        }
        BATTERY_TIPS.find(raw)?.let { m ->
            val count = m.groupValues[1]
            val id = if (count == "1") R.string.mx_result_battery_tips_one else R.string.mx_result_battery_tips_many
            return context.string(id, count)
        }
        return when {
            raw.startsWith("Open storage settings to clear app caches manually") ->
                context.string(R.string.mx_result_storage_open_settings)
            raw.startsWith("No battery tips right now") -> context.string(R.string.mx_result_battery_no_tips)
            raw.startsWith("Unable to read memory info") -> context.string(R.string.mx_result_memory_unreadable)
            raw.startsWith("Unable to clear cache") || raw.startsWith("Unable to clear storage") ->
                context.string(R.string.mx_result_storage_failed)
            raw.startsWith("Unable to read battery info") -> context.string(R.string.mx_result_battery_unreadable)
            else -> raw
        }
    }

    /**
     * The line for a button that threw. A Bangla reader cannot read an exception message, so
     * Bangla gets a plain sentence; English keeps the message it has always shown.
     */
    fun errorLine(context: Context, message: String?): String =
        if (isEnglish(context)) {
            context.string(R.string.mx_result_error, message.toString())
        } else {
            context.string(R.string.mx_result_error_plain)
        }

    /** The temperature trend, as returned by `HealthScoreUtils.getTemperatureTrend`. */
    fun temperatureTrend(context: Context, raw: String): String {
        if (isEnglish(context)) return raw
        val id = when (raw) {
            "📈 Increasing" -> R.string.mx_trend_increasing
            "📉 Decreasing" -> R.string.mx_trend_decreasing
            "📊 Stable" -> R.string.mx_trend_stable
            else -> return raw
        }
        return context.string(id)
    }

    /**
     * The title shown in the AI dialog for one item. [identifier] is the English title the
     * card passes to `onItemAIClick`; it also names the shared file and is logged to analytics,
     * so it is never translated itself.
     */
    fun aiItemTitle(context: Context, identifier: String): String {
        if (isEnglish(context)) return identifier
        val id = AI_ITEM_TITLES[identifier] ?: return identifier
        return context.string(id)
    }

    private val MEMORY_RESULT = Regex(
        """^(Memory pressure detected|Current memory): (\d+ MB) used of (\d+ MB) \((\d+)%\)\.""",
    )
    private val FREED_FROM_APPS = Regex("""^Freed (\d+ MB) .*from (\d+) apps?\.""")
    private val FREED = Regex("""^Freed (\d+ MB) """)
    private val BATTERY_TIPS = Regex("""^Found (\d+) tips?\.""")

    private val AI_ITEM_TITLES: Map<String, Int> = mapOf(
        "Camera Health Test" to R.string.mx_ai_item_camera_health_test,
        "Camera Problem Report" to R.string.mx_ai_item_camera_problem_report,
        "Camera Colour Cast Check" to R.string.mx_ai_item_camera_colour_cast,
        "Screen Test" to R.string.mx_ai_item_screen_test,
        "Mic Test" to R.string.mx_ai_item_mic_test,
        "7-Day Health History" to R.string.mx_ai_item_health_history,
        "Memory Usage" to R.string.mx_ai_item_memory_usage,
        "Storage Cleanup" to R.string.mx_ai_item_storage_cleanup,
        "Battery Health" to R.string.mx_ai_item_battery_health,
        "Privacy Dashboard" to R.string.mx_ai_item_privacy_dashboard,
        "Today's Tasks" to R.string.mx_ai_item_todays_tasks,
        "Temperature History" to R.string.mx_ai_item_temperature_history,
        "Smart Recommendations" to R.string.mx_ai_item_recommendations,
        "Security Dashboard" to R.string.mx_ai_item_security_dashboard,
        "Network Privacy Report" to R.string.mx_ai_item_network_privacy_report,
        "Network Reachability" to R.string.mx_ai_item_network_reachability,
        "Website Check" to R.string.mx_ai_item_website_check,
        "Component Breakdown" to R.string.mx_ai_item_component_breakdown,
        "Camera Power Test" to R.string.mx_ai_item_camera_power_test,
        "Display Brightness Power Test" to R.string.mx_ai_item_display_power_test,
        "CPU Performance Power Test" to R.string.mx_ai_item_cpu_power_test,
        "Network Signal Strength Test" to R.string.mx_ai_item_network_signal_test,
        "Device Sleep Tracker" to R.string.mx_ai_item_sleep_tracker,
        "App Screen Time" to R.string.mx_ai_item_app_screen_time,
        "Device scan" to R.string.mx_ai_item_device_scan,
        "DeviceGPT scan" to R.string.mx_ai_item_devicegpt_scan,
        "Resale Report" to R.string.mx_ai_item_resale_report,
        "Item" to R.string.mx_ai_item_generic,
    )
}
