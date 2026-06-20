package com.teamz.lab.debugger.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.ktx.Firebase
import com.teamz.lab.debugger.BuildConfig
import com.teamz.lab.debugger.utils.AppLog

object RemoteConfigUtils {
    private val remoteConfig: FirebaseRemoteConfig
        get() = FirebaseRemoteConfig.getInstance()
    
    /**
     * TEST FLAG: Automatically set to BuildConfig.DEBUG for safe testing
     * - Debug builds: FORCE_SHOW_ADS_IN_DEBUG = true (ads enabled for testing)
     * - Release builds: FORCE_SHOW_ADS_IN_DEBUG = false (ads follow RemoteConfig, safe for production)
     * 
     * This allows testing ads in debug mode while ensuring production safety.
     * In release builds, ads are always controlled by RemoteConfig.
     */
    private val FORCE_SHOW_ADS_IN_DEBUG = BuildConfig.DEBUG

    /**
     * ISO country code captured on app init. Cached because Locale.getDefault().country
     * can change mid-session (rare but possible) and we want a stable signal.
     * Used by isCountrySuppressed() to gate ad requests in zero-fill geos.
     */
    @Volatile private var cachedCountryCode: String = ""

    /** Returns true if the device's country is in the RC-driven suppression list. */
    fun isCountrySuppressed(): Boolean {
        val code = cachedCountryCode
        if (code.isBlank()) return false
        return code in getAdSuppressedCountryCodes()
    }

    /**
     * Capture device country once on app init. Reads from multiple sources in
     * order of reliability:
     *   1. TelephonyManager.networkCountryIso (SIM's home network — most accurate)
     *   2. TelephonyManager.simCountryIso (fallback for users on roaming)
     *   3. Locale.getDefault().country (last resort — many users have en-US system
     *      locale even when physically abroad, so geo suppression would miss them)
     * Required because IR/BD/PK users frequently have en-US set as system locale.
     */
    fun captureCountryCode(context: android.content.Context) {
        val code = try {
            val tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE)
                    as? android.telephony.TelephonyManager
            val networkIso = tm?.networkCountryIso?.uppercase().orEmpty()
            val simIso = tm?.simCountryIso?.uppercase().orEmpty()
            val localeIso = java.util.Locale.getDefault().country.uppercase()
            when {
                networkIso.isNotBlank() -> networkIso
                simIso.isNotBlank() -> simIso
                localeIso.isNotBlank() -> localeIso
                else -> ""
            }
        } catch (_: Exception) { "" }
        cachedCountryCode = code
        AppLog.d("RemoteConfigUtils", "captureCountryCode() - country=$code, suppressed=${isCountrySuppressed()}")
    }

    fun init() {
        AppLog.d("RemoteConfigUtils", "init() - Initializing RemoteConfig...")
        
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // 1 hour
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(
            mapOf(
                "show_interstitial_ads" to true,
                "show_banner_ads" to true,
                "show_app_open_ads" to true,
                "show_native_ads" to true,
                "show_rewarded_ads" to true,
                "interstitial_ad_interval" to 60L, // Minimum seconds between full-screen ads (global throttling, applies to all)
                // Leaderboard configuration
                "enable_leaderboard" to true,
                "leaderboard_tab_position" to -1L, // -1 = last tab, 0 = first tab
                "leaderboard_ad_frequency" to 5L, // Native ad every 5 entries
                "show_leaderboard_interstitial_ads" to true,
                "leaderboard_data_retention_days" to -1L, // -1 = keep forever, 0+ = days before removal
                "leaderboard_data_retention_reminder_days" to 5L, // Days before removal to show reminder (only if retention > 0)
                // Tab order configuration (IAP-optimized default)
                // Format: comma-separated list like "leaderboard,health,power,device_info,network_info"
                // Valid tab names: leaderboard, health, power, device_info, network_info
                "tab_order" to "leaderboard,health,power,device_info,network_info",
                // Review & Paywall timing configuration ("Review First, Paywall After" strategy)
                // First launch: 15s lets user see app load, dismiss notification dialog, and browse the UI
                // before review appears during the "honeymoon phase" (impressed but before finding issues)
                "review_delay_first_launch_ms" to 15000L,   // Delay before showing review on first launch (ms)
                "review_delay_returning_ms" to 3000L,       // Delay before showing review on returning sessions (ms)
                "paywall_delay_after_review_ms" to 1500L,   // Delay between review completing and paywall showing (ms)
                "paywall_fallback_delay_ms" to 20000L,      // Fallback delay if review doesn't show (ms)
                "paywall_repeat_interval_days" to 7L,       // Days between paywall re-shows for non-premium users
                "enable_review_first_strategy" to true,      // Master toggle for review-first-then-paywall flow
                // Native ad loading configuration
                // Tuned 2026-05-27: prior defaults (3/1/10s/20) hit 69798 req / 200 shown = 0.29% show rate
                // which throttles AdMob match rate. Cut to one-ad-at-a-time + long throttle + tight budget.
                "native_ad_target_count" to 1L,              // Cache only 1 ad at a time (was 3)
                "native_ad_max_retries" to 0L,               // No retry on fail; low fill = retries burn more (was 1)
                "native_ad_request_interval_ms" to 60000L,   // 60s between requests (was 10s)
                "native_ad_max_requests_per_session" to 7L,  // Bumped 5 -> 7 alongside 28min TTL drop so refills don't exhaust budget
                "native_ad_ttl_ms" to 1_680_000L,            // 28 min — under typical mediation network TTLs (Unity 30min, Mintegral 40min)
                "app_open_ad_min_session" to 1L,             // Default 1 = NO session gate (ads from session 1). Set to 3+ via Remote Config when ready to trade short-term ad revenue for retention.
                // Verified zero-fill markets per 2026-06-03 lifetime GA4 audit (≥20 users, 0.0% fill rate):
                //   IR=Iran (1214 users, 3 imp / 19474 fail) — sanctioned
                //   RU=Russia (54 users, 0 imp / 543 fail) — sanctioned
                //   IQ=Iraq (26 users, 0 imp / 981 fail) — low-fill region
                //   SG=Singapore (23 users, 0 imp / 351 fail) — PDPA blocks until UMP integrated
                //   YE=Yemen (22 users, 0 imp / 570 fail)
                //   ET=Ethiopia (20 users, 0 imp / 710 fail)
                //   SE=Sweden (28 users, 0 imp / 77 fail) — GDPR-strict, UMP needed
                // NOT included: BD (1.3% fill, 167 paid), PK (12.2% fill, better than US).
                // Suppression saves zero revenue (these earn $0) but removes ~22,800 wasted requests
                // that hurt AdMob global match rate signal.
                "ad_suppressed_country_codes" to "IR,RU,IQ,SG,YE,ET,SE"
            )
        )
        
        AppLog.d("RemoteConfigUtils", "init() - Defaults set, fetching and activating...")
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                AppLog.d("RemoteConfigUtils", "init() - ✅ RemoteConfig activated successfully")
                AppLog.d("RemoteConfigUtils", "init() - show_app_open_ads: ${remoteConfig.getBoolean("show_app_open_ads")}")
            } else {
                AppLog.e("RemoteConfigUtils", "init() - ❌ RemoteConfig activation failed: ${task.exception?.message}")
            }
        }
    }

    /**
     * Returns true if user should see no ads — premium purchase OR referral reward.
     */
    private fun isUserAdFree(): Boolean {
        if (RevenueCatManager.isPremium()) return true
        val referralAdFree = ReferralManager.isAdFreeFromReferralsCached()
        if (referralAdFree) {
            AppLog.d("RemoteConfigUtils", "isUserAdFree() - Referral ad-free active, suppressing ads")
            return true
        }
        return false
    }

    fun shouldShowInterstitialAds(): Boolean {
        if (isUserAdFree()) {
            AppLog.d("RemoteConfigUtils", "shouldShowInterstitialAds() - User is ad-free (premium or referral), skipping ads")
            return false
        }
        if (isCountrySuppressed()) {
            AppLog.d("RemoteConfigUtils", "shouldShowInterstitialAds() - Country $cachedCountryCode in suppression list, skipping")
            return false
        }

        // In debug mode: Only show ads if FORCE_SHOW_ADS_IN_DEBUG is true (for testing)
        // In release mode: FORCE_SHOW_ADS_IN_DEBUG is false, so this check is skipped (safe for production)
        // Production safety: In release builds, BuildConfig.DEBUG is false, so this condition never blocks ads
        if (BuildConfig.DEBUG && !FORCE_SHOW_ADS_IN_DEBUG) {
            AppLog.d("RemoteConfigUtils", "shouldShowInterstitialAds() - Debug mode, ads disabled (FORCE_SHOW_ADS_IN_DEBUG=$FORCE_SHOW_ADS_IN_DEBUG)")
            return false
        }

        // Production safety: In release builds, ads are always controlled by RemoteConfig
        // Debug builds: Ads are enabled for testing (FORCE_SHOW_ADS_IN_DEBUG = true)
        return remoteConfig.getBoolean("show_interstitial_ads")
    }

    fun shouldShowBannerAds(): Boolean {
        if (isUserAdFree()) return false
        if (isCountrySuppressed()) return false
        return remoteConfig.getBoolean("show_banner_ads")
    }

    fun shouldShowAppOpenAds(): Boolean {
        if (isUserAdFree()) {
            AppLog.d("RemoteConfigUtils", "shouldShowAppOpenAds() - User is ad-free, skipping ads")
            return false
        }
        if (isCountrySuppressed()) {
            AppLog.d("RemoteConfigUtils", "shouldShowAppOpenAds() - Country $cachedCountryCode suppressed, skipping")
            return false
        }

        val shouldShow = remoteConfig.getBoolean("show_app_open_ads")
        AppLog.d("RemoteConfigUtils", "shouldShowAppOpenAds() - Returning: $shouldShow")
        return shouldShow
    }

    /**
     * Session-aware variant of shouldShowAppOpenAds. Returns false for the first
     * `app_open_ad_min_session` sessions (default 3) so new users see the product
     * before seeing a launch interstitial. Pass the EngagementTracker session count.
     */
    fun shouldShowAppOpenAdsForSession(sessionCount: Int): Boolean {
        if (!shouldShowAppOpenAds()) return false
        val minSession = getAppOpenAdMinSession()
        if (sessionCount < minSession) {
            AppLog.d("RemoteConfigUtils", "shouldShowAppOpenAdsForSession() - session=$sessionCount < min=$minSession, suppressing")
            return false
        }
        return true
    }

    fun shouldShowNativeAds(): Boolean {
        if (isUserAdFree()) return false
        if (isCountrySuppressed()) return false
        return remoteConfig.getBoolean("show_native_ads")
    }

    /**
     * Reactive composable to check if native ads should be shown
     * This automatically updates when premium status changes
     * Use this in Compose UI instead of shouldShowNativeAds() for reactive updates
     */
    @Composable
    fun shouldShowNativeAdsReactive(): Boolean {
        val premiumStatus by RevenueCatManager.premiumStatusFlow.collectAsState()
        val isPremium = premiumStatus is RevenueCatManager.PremiumStatus.Premium &&
            (premiumStatus as? RevenueCatManager.PremiumStatus.Premium)?.isActive == true
        // Premium users never see ads
        if (isPremium) {
            return false
        }
        return remoteConfig.getBoolean("show_native_ads")
    }
    
    fun shouldShowRewardedAds(): Boolean {
        if (isUserAdFree()) return false
        
        // Disable video ads (rewarded) in debug mode
        if (BuildConfig.DEBUG) {
            return false
        }
        return remoteConfig.getBoolean("show_rewarded_ads")
    }
    
    /**
     * Get minimum interval between interstitial ads (in seconds)
     * This is the ONLY variable that controls full-screen ad frequency globally
     * Applies to all full-screen ads across the entire app
     * Default: 60 seconds (prevents ad spam and improves UX)
     */
    fun getInterstitialAdInterval(): Long {
        val interval = remoteConfig.getLong("interstitial_ad_interval")
        return if (interval == 0L) 60L else interval // Default: 60 seconds
    }
    
    // CSV View Ad Controls - REMOVED
    // CSV view ads now use global show_interstitial_ads flag (handled by InterstitialAdManager)
    
    // Leaderboard configuration
    fun isLeaderboardEnabled(): Boolean = remoteConfig.getBoolean("enable_leaderboard")
    
    fun getLeaderboardTabPosition(): Int {
        // 0 = first tab, -1 = last tab (default)
        val position = remoteConfig.getLong("leaderboard_tab_position")
        return if (position == 0L) 0 else -1 // Default to last tab
    }
    
    fun getLeaderboardAdFrequency(): Int {
        val frequency = remoteConfig.getLong("leaderboard_ad_frequency")
        return if (frequency == 0L) 5 else frequency.toInt() // Default: every 5 entries
    }
    
    fun shouldShowLeaderboardInterstitialAds(): Boolean {
        if (isUserAdFree()) return false
        return remoteConfig.getBoolean("show_leaderboard_interstitial_ads")
    }
    
    fun getLeaderboardDataRetentionDays(): Long {
        val days = remoteConfig.getLong("leaderboard_data_retention_days")
        // -1 means keep forever, 0 means use default (365 days), positive number = days
        return when {
            days == -1L -> -1L // Keep forever
            days == 0L -> 365L // Default: 1 year if not specified
            else -> days
        }
    }
    
    fun getLeaderboardDataRetentionReminderDays(): Long {
        val days = remoteConfig.getLong("leaderboard_data_retention_reminder_days")
        return if (days == 0L) 5L else days // Default: 5 days before removal
    }
    
    /**
     * Get tab order configuration from RemoteConfig
     * Returns comma-separated string of tab names
     * Default: "leaderboard,health,power,device_info,network_info" (IAP-optimized)
     */
    fun getTabOrderConfig(): String {
        return remoteConfig.getString("tab_order")
    }

    // === Review & Paywall timing (configurable via Firebase console) ===

    /** Delay before showing review prompt on first launch (ms). Default: 15000 */
    fun getReviewDelayFirstLaunchMs(): Long {
        val value = remoteConfig.getLong("review_delay_first_launch_ms")
        return if (value == 0L) 15000L else value
    }

    /** Delay before showing review prompt on returning sessions (ms). Default: 3000 */
    fun getReviewDelayReturningMs(): Long {
        val value = remoteConfig.getLong("review_delay_returning_ms")
        return if (value == 0L) 3000L else value
    }

    /** Delay between review completing and paywall showing (ms). Default: 1500 */
    fun getPaywallDelayAfterReviewMs(): Long {
        val value = remoteConfig.getLong("paywall_delay_after_review_ms")
        return if (value == 0L) 1500L else value
    }

    /** Fallback delay for paywall if review doesn't show (ms). Default: 20000 */
    fun getPaywallFallbackDelayMs(): Long {
        val value = remoteConfig.getLong("paywall_fallback_delay_ms")
        return if (value == 0L) 20000L else value
    }

    /** Days between paywall re-shows for non-premium users. Default: 7 */
    fun getPaywallRepeatIntervalDays(): Long {
        val value = remoteConfig.getLong("paywall_repeat_interval_days")
        return if (value == 0L) 7L else value
    }

    /** Master toggle for the review-first-then-paywall strategy. Default: true */
    fun isReviewFirstStrategyEnabled(): Boolean {
        return remoteConfig.getBoolean("enable_review_first_strategy")
    }

    // === Native ad loading configuration (tunable without app update) ===

    /** How many native ads to load per session. Default: 3 */
    fun getNativeAdTargetCount(): Int {
        val value = remoteConfig.getLong("native_ad_target_count")
        return if (value == 0L) 3 else value.toInt()
    }

    /** Max retry attempts when a native ad fails to load. Default: 0 (no retries). */
    fun getNativeAdMaxRetries(): Int {
        val value = remoteConfig.getLong("native_ad_max_retries")
        // Keep 0 as a valid value so retries can be explicitly disabled.
        return value.toInt()
    }

    /** Minimum ms between native ad requests (throttling). Default: 10000 */
    fun getNativeAdRequestIntervalMs(): Long {
        val value = remoteConfig.getLong("native_ad_request_interval_ms")
        return if (value == 0L) 10000L else value
    }

    /** Total native ad request budget per session. Default: 20 */
    fun getNativeAdMaxRequestsPerSession(): Int {
        val value = remoteConfig.getLong("native_ad_max_requests_per_session")
        return if (value == 0L) 20 else value.toInt()
    }

    /**
     * TTL (ms) before a cached native ad is considered expired and evicted.
     * Default: 1_680_000 (28 min) — under typical mediation network TTLs.
     * 0L or negative values fall back to the default.
     */
    fun getNativeAdTtlMs(): Long {
        val value = remoteConfig.getLong("native_ad_ttl_ms")
        return if (value <= 0L) 1_680_000L else value
    }

    /**
     * Minimum session count before app-open interstitials are shown.
     * Default: 1 (no session gate, ads from session 1 — preserves existing revenue).
     * Set to 3+ via Remote Config when ready to trade short-term ad revenue for
     * lower first-session uninstall (lifetime data shows 37% same-session churn).
     */
    fun getAppOpenAdMinSession(): Int {
        val value = remoteConfig.getLong("app_open_ad_min_session")
        return if (value <= 0L) 1 else value.toInt()
    }

    /**
     * Comma-separated ISO country codes where ad requests are suppressed entirely.
     * Default: "" (empty — suppression OFF, all geos see ads).
     * Set via Remote Config (e.g. "IR" or "IR,BD,PK") only when telemetry justifies
     * cutting a specific market.
     */
    fun getAdSuppressedCountryCodes(): Set<String> {
        val raw = remoteConfig.getString("ad_suppressed_country_codes")
        if (raw.isBlank()) return emptySet()
        return raw.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
    }

    /**
     * v3.1.11 Week 1 retention milestone — D1 overnight-drain push.
     * Default false. Set true in Firebase console to enable on next-install A/B test.
     */
    fun isD1OvernightDrainEnabled(): Boolean {
        return remoteConfig.getBoolean("d1_overnight_drain_enabled")
    }

    /**
     * v3.1.11 Week 1 retention milestone — First-launch 10s auto-scan + Device Score gate.
     * Default false. Set true in Firebase console to enable on next-install A/B test.
     */
    fun isFirstScanGateEnabled(): Boolean {
        return remoteConfig.getBoolean("first_scan_gate_enabled")
    }
}