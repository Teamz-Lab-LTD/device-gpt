package com.teamz.lab.debugger.ui

import androidx.annotation.StringRes
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.LeaderboardCategory

/**
 * Wording for a leaderboard category, in the language the person chose.
 *
 * [LeaderboardCategory.id] is a Firestore collection key and [LeaderboardCategory.displayName]
 * is sent to analytics, so both stay English. Screens show these resources instead.
 */
@StringRes
fun LeaderboardCategory.nameRes(): Int = when (this) {
    LeaderboardCategory.POWER_EFFICIENCY -> R.string.lb_cat_name_power_efficiency
    LeaderboardCategory.CPU_PERFORMANCE -> R.string.lb_cat_name_cpu_performance
    LeaderboardCategory.CAMERA_EFFICIENCY -> R.string.lb_cat_name_camera_efficiency
    LeaderboardCategory.DISPLAY_EFFICIENCY -> R.string.lb_cat_name_display_efficiency
    LeaderboardCategory.HEALTH_SCORE -> R.string.lb_cat_name_health_score
    LeaderboardCategory.POWER_TREND -> R.string.lb_cat_name_power_trend
    LeaderboardCategory.COMPONENT_OPTIMIZATION -> R.string.lb_cat_name_component_optimization
    LeaderboardCategory.THERMAL_EFFICIENCY -> R.string.lb_cat_name_thermal_efficiency
    LeaderboardCategory.PERFORMANCE_CONSISTENCY -> R.string.lb_cat_name_performance_consistency
    LeaderboardCategory.APP_POWER_MONITORING -> R.string.lb_cat_name_app_power_monitoring
    LeaderboardCategory.BEST_DEVICE -> R.string.lb_cat_name_best_device
}

/** The question the category answers ("Which phones stay cool?"). */
@StringRes
fun LeaderboardCategory.questionRes(): Int = when (this) {
    LeaderboardCategory.POWER_EFFICIENCY -> R.string.lb_cat_q_power_efficiency
    LeaderboardCategory.CPU_PERFORMANCE -> R.string.lb_cat_q_cpu_performance
    LeaderboardCategory.CAMERA_EFFICIENCY -> R.string.lb_cat_q_camera_efficiency
    LeaderboardCategory.DISPLAY_EFFICIENCY -> R.string.lb_cat_q_display_efficiency
    LeaderboardCategory.HEALTH_SCORE -> R.string.lb_cat_q_health_score
    LeaderboardCategory.POWER_TREND -> R.string.lb_cat_q_power_trend
    LeaderboardCategory.COMPONENT_OPTIMIZATION -> R.string.lb_cat_q_component_optimization
    LeaderboardCategory.THERMAL_EFFICIENCY -> R.string.lb_cat_q_thermal_efficiency
    LeaderboardCategory.PERFORMANCE_CONSISTENCY -> R.string.lb_cat_q_performance_consistency
    LeaderboardCategory.APP_POWER_MONITORING -> R.string.lb_cat_q_app_power_monitoring
    LeaderboardCategory.BEST_DEVICE -> R.string.lb_cat_q_best_device
}

/** The one-line everyday explanation. */
@StringRes
fun LeaderboardCategory.simpleExplanationRes(): Int = when (this) {
    LeaderboardCategory.POWER_EFFICIENCY -> R.string.lb_cat_simple_power_efficiency
    LeaderboardCategory.CPU_PERFORMANCE -> R.string.lb_cat_simple_cpu_performance
    LeaderboardCategory.CAMERA_EFFICIENCY -> R.string.lb_cat_simple_camera_efficiency
    LeaderboardCategory.DISPLAY_EFFICIENCY -> R.string.lb_cat_simple_display_efficiency
    LeaderboardCategory.HEALTH_SCORE -> R.string.lb_cat_simple_health_score
    LeaderboardCategory.POWER_TREND -> R.string.lb_cat_simple_power_trend
    LeaderboardCategory.COMPONENT_OPTIMIZATION -> R.string.lb_cat_simple_component_optimization
    LeaderboardCategory.THERMAL_EFFICIENCY -> R.string.lb_cat_simple_thermal_efficiency
    LeaderboardCategory.PERFORMANCE_CONSISTENCY -> R.string.lb_cat_simple_performance_consistency
    LeaderboardCategory.APP_POWER_MONITORING -> R.string.lb_cat_simple_app_power_monitoring
    LeaderboardCategory.BEST_DEVICE -> R.string.lb_cat_simple_best_device
}

/** How the ranking is worked out. */
@StringRes
fun LeaderboardCategory.howMeasuredRes(): Int = when (this) {
    LeaderboardCategory.POWER_EFFICIENCY -> R.string.lb_cat_how_power_efficiency
    LeaderboardCategory.CPU_PERFORMANCE -> R.string.lb_cat_how_cpu_performance
    LeaderboardCategory.CAMERA_EFFICIENCY -> R.string.lb_cat_how_camera_efficiency
    LeaderboardCategory.DISPLAY_EFFICIENCY -> R.string.lb_cat_how_display_efficiency
    LeaderboardCategory.HEALTH_SCORE -> R.string.lb_cat_how_health_score
    LeaderboardCategory.POWER_TREND -> R.string.lb_cat_how_power_trend
    LeaderboardCategory.COMPONENT_OPTIMIZATION -> R.string.lb_cat_how_component_optimization
    LeaderboardCategory.THERMAL_EFFICIENCY -> R.string.lb_cat_how_thermal_efficiency
    LeaderboardCategory.PERFORMANCE_CONSISTENCY -> R.string.lb_cat_how_performance_consistency
    LeaderboardCategory.APP_POWER_MONITORING -> R.string.lb_cat_how_app_power_monitoring
    LeaderboardCategory.BEST_DEVICE -> R.string.lb_cat_how_best_device
}
