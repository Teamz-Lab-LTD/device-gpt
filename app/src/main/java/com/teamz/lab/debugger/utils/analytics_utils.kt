package com.teamz.lab.debugger.utils

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.teamz.lab.debugger.BuildConfig

object AnalyticsUtils {

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            appContext = context.applicationContext
        }
    }

    /**
     * Check if device is in a restricted mode where analytics should NOT be sent
     * Analytics are always logged locally for debugging, but only sent when device is in normal mode
     */
    private fun isDeviceInRestrictedMode(context: Context): Boolean {
        return try {
            // Check Battery Saver Mode
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isBatterySaver = powerManager?.isPowerSaveMode ?: false
            
            // Check Do Not Disturb Mode (Android 6.0+)
            val isDoNotDisturb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                notificationManager?.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE
            } else {
                false
            }
            
            // Check Airplane Mode
            val isAirplaneMode = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0
            
            // Check if device is in Doze Mode (deep sleep)
            val isDozeMode = powerManager?.isDeviceIdleMode ?: false
            
            val isRestricted = isBatterySaver || isDoNotDisturb || isAirplaneMode || isDozeMode
            
            if (isRestricted) {
                Log.d("AnalyticsUtils", "Device in restricted mode - Analytics logged but NOT sent: " +
                    "BatterySaver=$isBatterySaver, DoNotDisturb=$isDoNotDisturb, " +
                    "AirplaneMode=$isAirplaneMode, DozeMode=$isDozeMode")
            }
            
            isRestricted
        } catch (e: Exception) {
            Log.e("AnalyticsUtils", "Error checking device mode", e)
            false // Default to allowing analytics if check fails
        }
    }

    /**
     * Log analytics event - Always logs locally, but only sends to Firebase when:
     * - NOT in debug mode (BuildConfig.DEBUG = false)
     * - Device is NOT in restricted mode
     * 
     * Restricted modes (analytics NOT sent):
     * - Debug builds (BuildConfig.DEBUG = true)
     * - Battery Saver Mode
     * - Do Not Disturb Mode
     * - Airplane Mode
     * - Doze Mode (deep sleep)
     */
    fun logEvent(event: AnalyticsEvent, params: Map<String, Any?> = emptyMap()) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putBoolean(key, value)
            }
        }
        
        // Always log locally for debugging
        Log.d("AnalyticsUtils", "Event logged: ${event.eventName} with params: $params")
        
        // Never send to Firebase in debug mode
        if (BuildConfig.DEBUG) {
            Log.d("AnalyticsUtils", "Event NOT sent to Firebase (DEBUG mode): ${event.eventName}")
            return
        }
        
        // Only send to Firebase if device is NOT in restricted mode
        val context = appContext
        if (context != null && !isDeviceInRestrictedMode(context)) {
            firebaseAnalytics?.logEvent(event.eventName, bundle)
            Log.d("AnalyticsUtils", "Event sent to Firebase: ${event.eventName}")
        } else {
            Log.d("AnalyticsUtils", "Event NOT sent to Firebase (device in restricted mode): ${event.eventName}")
        }
    }

    /**
     * Set user ID - Only sets if:
     * - NOT in debug mode (BuildConfig.DEBUG = false)
     * - Device is NOT in restricted mode
     */
    fun setUserId(userId: String?) {
        // Never set user ID in debug mode
        if (BuildConfig.DEBUG) {
            Log.d("AnalyticsUtils", "User ID NOT set (DEBUG mode): $userId")
            return
        }
        
        val context = appContext
        if (context != null && !isDeviceInRestrictedMode(context)) {
            firebaseAnalytics?.setUserId(userId)
            Log.d("AnalyticsUtils", "User ID set: $userId")
        } else {
            Log.d("AnalyticsUtils", "User ID NOT set (device in restricted mode): $userId")
        }
    }

    /**
     * Set user property - Only sets if:
     * - NOT in debug mode (BuildConfig.DEBUG = false)
     * - Device is NOT in restricted mode
     */
    fun setUserProperty(name: String, value: String) {
        // Never set user property in debug mode
        if (BuildConfig.DEBUG) {
            Log.d("AnalyticsUtils", "User property NOT set (DEBUG mode): $name = $value")
            return
        }
        
        val context = appContext
        if (context != null && !isDeviceInRestrictedMode(context)) {
            firebaseAnalytics?.setUserProperty(name, value)
            Log.d("AnalyticsUtils", "User property set: $name = $value")
        } else {
            Log.d("AnalyticsUtils", "User property NOT set (device in restricted mode): $name = $value")
        }
    }
}

enum class AnalyticsEvent(val eventName: String) {
    ShareDeviceInfo("share_device_info_clicked"),
    ShareWithAI("shareWithAI"),
    ShareNetworkInfo("share_network_info_clicked"),
    RealtimeMonitorToggled("realtime_monitor_toggled"),
    RealtimeMonitorStarted("realtime_monitor_started"),
    RealtimeMonitorStopped("realtime_monitor_stopped"),
    PermissionGranted("permission_granted"),
    PermissionDenied("permission_denied"),
    PermissionOpenedFromSettings("permission_opened_settings"),
    AppOpened("app_opened"),
    DrawerOpened("drawer_opened"),
    ReviewRequested("review_requested"),
    ReviewOpenedPlayStore("review_opened_play_store"),
    InfoExpanded("info_expanded"),
    InfoCollapsed("info_collapsed"),
    InfoCopied("info_copied"),
    AdShownInline("ad_shown_inline"),
    AdShownInList("ad_shown_list"),
    AdLoaded("ad_loaded"),
    AdFailed("ad_failed"),
    SearchUsed("search_used"),
    AdClicked("ad_clicked"),
    AppOpenAdShown("app_open_ad_shown"),
    AppOpenAdClicked("app_open_ad_clicked"),
    AppOpenAdDismissed("app_open_ad_dismissed"),
    AppFullScreenAdShown("full_screen_ad_shown"),
    AppFullScreenAdClicked("full_screen_ad_clicked"),
    AppFullScreenAdDismissed("full_screen_ad_clicked"),
    // Viral growth events
    ReferralShared("referral_shared"),
    ReferralLinkClicked("referral_link_clicked"),
    ReferralInstalled("referral_installed"),
    ShareToSocial("share_to_social"),
    ShareToWhatsApp("share_to_whatsapp"),
    ShareToTelegram("share_to_telegram"),
    ShareToEmail("share_to_email"),
    ShareToSMS("share_to_sms"),
    OnboardingCompleted("onboarding_completed"),
    OnboardingSkipped("onboarding_skipped"),
    FeatureDiscovered("feature_discovered"),
    AchievementUnlocked("achievement_unlocked"),
    DailyStreakMilestone("daily_streak_milestone"),
    SocialProofShown("social_proof_shown"),
    // Power consumption events
    PowerTabViewed("power_tab_viewed"),
    PowerExperimentStarted("power_experiment_started"),
    PowerExperimentCompleted("power_experiment_completed"),
    PowerInsightViewed("power_insight_viewed"),
    PowerDataShared("power_data_shared"),
    PowerRecommendationShown("power_recommendation_shown"),
    PowerAlertTriggered("power_alert_triggered"),
    PowerComponentExpanded("power_component_expanded"),
    PowerHistoryViewed("power_history_viewed"),
    AdRewardEarned("ad_reward_earned"),
    // Drawer events
    DrawerItemClicked("drawer_item_clicked"),
    DrawerPermissionToggled("drawer_permission_toggled"),
    DrawerSettingsOpened("drawer_settings_opened"),
    DrawerReviewClicked("drawer_review_clicked"),
    DrawerMoreAppsClicked("drawer_more_apps_clicked"),
    DrawerUpworkClicked("drawer_upwork_clicked"),
    DrawerUsageStatsDialogShown("drawer_usage_stats_dialog_shown"),
    DrawerNotificationDialogShown("drawer_notification_dialog_shown"),
    DrawerNotificationToggled("drawer_notification_toggled"),
    // Tab navigation events
    TabDeviceInfoViewed("tab_device_info_viewed"),
    TabNetworkInfoViewed("tab_network_info_viewed"),
    TabHealthViewed("tab_health_viewed"),
    TabPowerViewed("tab_power_viewed"),
    TabLeaderboardViewed("tab_leaderboard_viewed"),
    // Top bar actions
    TopBarRefreshClicked("top_bar_refresh_clicked"),
    TopBarSettingsClicked("top_bar_settings_clicked"),
    TopBarDevOptionsClicked("top_bar_dev_options_clicked"),
    TopBarMenuOpened("top_bar_menu_opened"),
    // FAB events
    FabCertificateClicked("fab_certificate_clicked"),
    FabAIClicked("fab_ai_clicked"),
    FabShareClicked("fab_share_clicked"),
    // Health tab events
    HealthScanStarted("health_scan_started"),
    HealthScanCompleted("health_scan_completed"),
    HealthScoreClicked("health_score_clicked"),
    HealthRecommendationsViewed("health_recommendations_viewed"),
    HealthHistoryViewed("health_history_viewed"),
    // Power tab test events
    PowerTestSingleTestClicked("power_test_single_test_clicked"),
    PowerTestMultipleTestsClicked("power_test_multiple_tests_clicked"),
    PowerTestQuickSweepClicked("power_test_quick_sweep_clicked"),
    PowerTestFullSweepClicked("power_test_full_sweep_clicked"),
    PowerTestRunLevelsClicked("power_test_run_levels_clicked"),
    PowerTestSamplingClicked("power_test_sampling_clicked"),
    PowerCsvDialogOpened("power_csv_dialog_opened"),
    PowerCsvExported("power_csv_exported"),
    PowerComponentInfoOpened("power_component_info_opened"),
    AppPowerMonitorStarted("app_power_monitor_started"),
    AppPowerMonitorStopped("app_power_monitor_stopped"),
    AppPowerCsvExported("app_power_csv_exported"),
    AppPowerPermissionRequested("app_power_permission_requested"),
    AppPowerDataUploaded("app_power_data_uploaded"),
    // Permission events (detailed)
    PermissionLocationRequested("permission_location_requested"),
    PermissionPhoneStateRequested("permission_phone_state_requested"),
    PermissionUsageStatsRequested("permission_usage_stats_requested"),
    PermissionNotificationRequested("permission_notification_requested"),
    PermissionBluetoothRequested("permission_bluetooth_requested"),
    PermissionCameraRequested("permission_camera_requested"),
    PermissionAudioRequested("permission_audio_requested"),
    // Settings navigation
    SettingsOpened("settings_opened"),
    SettingsDevOptionsOpened("settings_dev_options_opened"),
    SettingsUsageAccessOpened("settings_usage_access_opened"),
    SettingsAppDetailsOpened("settings_app_details_opened"),
}
