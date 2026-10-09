package com.teamz.lab.debugger.utils

import android.content.Context
import androidx.annotation.StringRes
import com.teamz.lab.debugger.R

/**
 * Power Consumption Education Content
 * Based on research papers from latest_power_consumption_research.md
 *
 * Provides educational content about power consumption for users.
 * The text lives in string resources (strings_power.xml, keys `pw_edu_*`) so it follows the
 * app language. Each content block keeps one bullet per line starting with "•": the component
 * dialog picks its quick tips by that mark.
 */
object PowerEducation {

    data class EducationContent(
        val title: String,
        val content: String,
        val category: Category,
        val researchSource: String? = null
    )

    enum class Category {
        BASICS, DISPLAY, CPU, NETWORK, CAMERA, BATTERY, GENERAL
    }

    /**
     * Get education content for a specific component.
     * [component] is the English component name used as data everywhere ("CPU", "Display").
     */
    fun getEducationForComponent(context: Context, component: String): EducationContent? {
        return when (component.lowercase()) {
            "display", "screen" -> getDisplayEducation(context)
            "cpu", "processor" -> getCpuEducation(context)
            "network", "wifi", "cellular" -> getNetworkEducation(context)
            "camera" -> getCameraEducation(context)
            "battery" -> getBatteryEducation(context)
            else -> null
        }
    }

    private fun build(
        context: Context,
        @StringRes title: Int,
        @StringRes content: Int,
        category: Category,
        researchSource: String? = null
    ) = EducationContent(
        title = context.getString(title),
        content = context.getString(content),
        category = category,
        researchSource = researchSource
    )

    /**
     * Get general power consumption basics
     */
    fun getBasicsEducation(context: Context): EducationContent =
        build(context, R.string.pw_edu_basics_title, R.string.pw_edu_basics_content, Category.BASICS)

    private fun getDisplayEducation(context: Context): EducationContent = build(
        context, R.string.pw_edu_display_title, R.string.pw_edu_display_content, Category.DISPLAY,
        researchSource = "LCD vs AMOLED Power Consumption Research"
    )

    private fun getCpuEducation(context: Context): EducationContent = build(
        context, R.string.pw_edu_cpu_title, R.string.pw_edu_cpu_content, Category.CPU,
        researchSource = "CPU Frequency-Independent Power Consumption Research"
    )

    private fun getNetworkEducation(context: Context): EducationContent = build(
        context, R.string.pw_edu_network_title, R.string.pw_edu_network_content, Category.NETWORK,
        researchSource = "Network RSSI Power Consumption Research"
    )

    private fun getCameraEducation(context: Context): EducationContent = build(
        context, R.string.pw_edu_camera_title, R.string.pw_edu_camera_content, Category.CAMERA,
        researchSource = "Per-Photo Energy Consumption Research"
    )

    private fun getBatteryEducation(context: Context): EducationContent =
        build(context, R.string.pw_edu_battery_title, R.string.pw_edu_battery_content, Category.BATTERY)

    /**
     * Get quick tip for a component
     */
    fun getQuickTip(context: Context, component: String): String? {
        @StringRes val res = when (component.lowercase()) {
            "display", "screen" -> R.string.pw_edu_tip_display
            "cpu", "processor" -> R.string.pw_edu_tip_cpu
            "network", "wifi", "cellular" -> R.string.pw_edu_tip_network
            "camera" -> R.string.pw_edu_tip_camera
            "battery" -> R.string.pw_edu_tip_battery
            else -> return null
        }
        return context.getString(res)
    }
}
