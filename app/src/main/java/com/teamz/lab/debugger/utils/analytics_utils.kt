package com.teamz.lab.debugger.utils

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.HandlerThread
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.teamz.lab.debugger.BuildConfig

object AnalyticsUtils {

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var appContext: Context? = null

    // Dedicated background thread for analytics work. v3.1.10 fix — Crashlytics
    // showed v3.1.9 ANR in LeaderboardSection.CategorySelector. Root cause was
    // logEvent() calling isDeviceInRestrictedMode() on the main thread, which
    // synchronously queries Settings.Global.AIRPLANE_MODE_ON via IPC to the
    // SettingsProvider. On slow devices that single IPC can stack with other
    // main-thread work and trip an ANR. Routing every logEvent body through
    // this single HandlerThread keeps the call site fire-and-forget without
    // spawning a new thread per event (which would be wasteful — every tap
    // logs an event).
    private val analyticsThread: HandlerThread by lazy {
        HandlerThread("teamzlab-analytics").apply { start() }
    }
    private val analyticsHandler: Handler by lazy { Handler(analyticsThread.looper) }

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
        // Snapshot the inputs synchronously (caller's thread may release the map
        // before the background handler runs), then defer all blocking work
        // (SettingsProvider IPC + Firebase) to the analytics HandlerThread.
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

        // Always log locally for debugging — Log.d itself is non-blocking.
        Log.d("AnalyticsUtils", "Event logged: ${event.eventName} with params: $params")

        if (BuildConfig.DEBUG) {
            Log.d("AnalyticsUtils", "Event NOT sent to Firebase (DEBUG mode): ${event.eventName}")
            return
        }

        val context = appContext ?: return
        val eventName = event.eventName
        analyticsHandler.post {
            try {
                if (!isDeviceInRestrictedMode(context)) {
                    firebaseAnalytics?.logEvent(eventName, bundle)
                    Log.d("AnalyticsUtils", "Event sent to Firebase: $eventName")
                } else {
                    Log.d("AnalyticsUtils", "Event NOT sent to Firebase (device in restricted mode): $eventName")
                }
            } catch (t: Throwable) {
                Log.w("AnalyticsUtils", "Background logEvent failed for $eventName: ${t.message}")
            }
        }
    }

    /**
     * Log ad revenue using GA4 standard "ad_impression" event.
     * This is the ONLY format GA4 recognizes for ad revenue in reports.
     * Uses FirebaseAnalytics.Event.AD_IMPRESSION with VALUE + CURRENCY params.
     *
     * Without this, ad revenue shows as $0 in GA4 because custom events
     * like "ad_paid" are not counted in the Revenue reports.
     */
    fun logAdRevenue(
        adType: String,
        adUnitId: String,
        revenueMicros: Long,
        currencyCode: String
    ) {
        val revenueValue = revenueMicros / 1_000_000.0

        Log.d("AnalyticsUtils", "Ad revenue: $revenueValue $currencyCode (type=$adType, unit=$adUnitId)")

        if (BuildConfig.DEBUG) {
            Log.d("AnalyticsUtils", "Ad revenue NOT sent to Firebase (DEBUG mode)")
            return
        }

        val context = appContext ?: return
        analyticsHandler.post {
            try {
                if (!isDeviceInRestrictedMode(context)) {
                    val bundle = Bundle().apply {
                        putString(FirebaseAnalytics.Param.AD_PLATFORM, "admob")
                        putString(FirebaseAnalytics.Param.AD_FORMAT, adType)
                        putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
                        putString(FirebaseAnalytics.Param.CURRENCY, currencyCode)
                        putDouble(FirebaseAnalytics.Param.VALUE, revenueValue)
                    }
                    firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle)
                    Log.d("AnalyticsUtils", "Ad revenue sent to Firebase: $revenueValue $currencyCode")
                }
            } catch (t: Throwable) {
                Log.w("AnalyticsUtils", "Background logAdRevenue failed: ${t.message}")
            }
        }
    }

    /**
     * Set user ID - Only sets if:
     * - NOT in debug mode (BuildConfig.DEBUG = false)
     * - Device is NOT in restricted mode
     */
    fun setUserId(userId: String?) {
        if (BuildConfig.DEBUG) {
            Log.d("AnalyticsUtils", "User ID NOT set (DEBUG mode): $userId")
            return
        }
        val context = appContext ?: return
        analyticsHandler.post {
            try {
                if (!isDeviceInRestrictedMode(context)) {
                    firebaseAnalytics?.setUserId(userId)
                    Log.d("AnalyticsUtils", "User ID set: $userId")
                } else {
                    Log.d("AnalyticsUtils", "User ID NOT set (device in restricted mode): $userId")
                }
            } catch (t: Throwable) {
                Log.w("AnalyticsUtils", "Background setUserId failed: ${t.message}")
            }
        }
    }

    /**
     * Set user property - Only sets if:
     * - NOT in debug mode (BuildConfig.DEBUG = false)
     * - Device is NOT in restricted mode
     */
    fun setUserProperty(name: String, value: String) {
        if (BuildConfig.DEBUG) {
            Log.d("AnalyticsUtils", "User property NOT set (DEBUG mode): $name = $value")
            return
        }
        val context = appContext ?: return
        analyticsHandler.post {
            try {
                if (!isDeviceInRestrictedMode(context)) {
                    firebaseAnalytics?.setUserProperty(name, value)
                    Log.d("AnalyticsUtils", "User property set: $name = $value")
                } else {
                    Log.d("AnalyticsUtils", "User property NOT set (device in restricted mode): $name = $value")
                }
            } catch (t: Throwable) {
                Log.w("AnalyticsUtils", "Background setUserProperty failed: ${t.message}")
            }
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
    RealtimeMonitorAutoStarted("realtime_monitor_auto_started"),
    MonitorNotificationOpened("monitor_notification_opened"),
    ChargeCycleCompleted("charge_cycle_completed"),
    ChargeSummaryOpened("charge_summary_opened"),
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
    AppFullScreenAdDismissed("full_screen_ad_dismissed"),
    AdPaid("ad_paid"),
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
    // Engagement events — same names as team_mvp_kit (Flutter) for cross-app analytics
    AppInstalled("app_installed"),
    UserEngagementSession("user_engagement_session"),
    UserEngagementSignificantAction("user_engagement_significant_action"),
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
    LifetimeSubscriptionClicked("lifetime_subscription_clicked"),
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
    // Widget events
    WidgetAddToHomeScreenClicked("widget_add_to_home_screen_clicked"),
    WidgetAddToHomeScreenSuccess("widget_add_to_home_screen_success"),
    WidgetAddToHomeScreenFailed("widget_add_to_home_screen_failed"),
    WidgetAddToLockScreenClicked("widget_add_to_lock_screen_clicked"),
    WidgetInstructionsShown("widget_instructions_shown"),
    WidgetTapped("widget_tapped"),
    WidgetUpdated("widget_updated"),
    WidgetDisplayed("widget_displayed"),
    // Premium events
    PremiumPaywallShown("premium_paywall_shown"),
    PremiumPaywallDismissed("premium_paywall_dismissed"),
    PremiumPurchaseInitiated("premium_purchase_initiated"),
    PremiumPurchaseCompleted("premium_purchase_completed"),
    PremiumPurchaseFailed("premium_purchase_failed"),
    PremiumPurchaseCancelled("premium_purchase_cancelled"),
    BillingNotInitialized("billing_not_initialized"),
    BillingOfferingsFailed("billing_offerings_failed"),
    BillingNoProductFound("billing_no_product_found"),
    UmpConsentResolved("ump_consent_resolved"),
    UmpConsentFailed("ump_consent_failed"),
    PremiumRestoreInitiated("premium_restore_initiated"),
    PremiumRestoreCompleted("premium_restore_completed"),
    PremiumRestoreFailed("premium_restore_failed"),
    PremiumGateClicked("premium_gate_clicked"),
    PremiumFabClicked("premium_fab_clicked"),
    PremiumDrawerCardClicked("premium_drawer_card_clicked"),
    PremiumBadgeClicked("premium_badge_clicked"),
    PremiumUserRankCardViewed("premium_user_rank_card_viewed"),
    // Network Privacy Report Card events
    PrivacyReportViewed("privacy_report_viewed"),
    PrivacyReportShared("privacy_report_shared"),
    PrivacyReportAIClicked("privacy_report_ai_clicked"),
    PrivacyReportGrade("privacy_report_grade"),
    // Network Reachability events
    ReachabilityTestStarted("reachability_test_started"),
    ReachabilityTestCompleted("reachability_test_completed"),
    ReachabilityResultShared("reachability_result_shared"),
    ReachabilityAIClicked("reachability_ai_clicked"),
    // Zero Trust Dashboard events
    ZeroTrustScanStarted("zero_trust_scan_started"),
    ZeroTrustScanCompleted("zero_trust_scan_completed"),
    ZeroTrustSectionExpanded("zero_trust_section_expanded"),
    ZeroTrustShared("zero_trust_shared"),
    ZeroTrustAIClicked("zero_trust_ai_clicked"),
    // Passkey auth events
    PasskeySignInAttempted("passkey_sign_in_attempted"),
    PasskeySignInSuccess("passkey_sign_in_success"),
    PasskeySignInFailed("passkey_sign_in_failed"),
    PasskeySignOut("passkey_sign_out"),
    PasskeyAccountLinked("passkey_account_linked"),
    PasskeyAccountDeleted("passkey_account_deleted"),
    // Verified Report events
    VerifiedReportGenerated("verified_report_generated"),
    VerifiedReportShared("verified_report_shared"),
    VerifiedReportUploaded("verified_report_uploaded"),
    ReportVerificationAttempted("report_verification_attempted"),
    ReportVerificationSuccess("report_verification_success"),
    ReportVerificationFailed("report_verification_failed"),
    // Monetization funnel tracking (visible in Firebase Analytics after release)
    PaywallChainReviewCompleted("paywall_chain_review_completed"),
    PaywallChainTriggered("paywall_chain_triggered"),
    PaywallChainSkipped("paywall_chain_skipped"),
    PaywallFallbackTriggered("paywall_fallback_triggered"),
    PaywallFallbackSkipped("paywall_fallback_skipped"),
    PaywallHealthScanTriggered("paywall_health_scan_triggered"),
    PaywallHealthScanSkipped("paywall_health_scan_skipped"),
    PaywallVerifiedReportGated("paywall_verified_report_gated"),
    AiSoftGateTriggered("ai_soft_gate_triggered"),
    AiSoftGateSkipped("ai_soft_gate_skipped"),
    PremiumSectionTeased("premium_section_teased"),
}
