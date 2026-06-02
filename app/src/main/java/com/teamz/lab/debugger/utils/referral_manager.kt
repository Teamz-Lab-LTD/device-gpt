package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import java.net.URLDecoder
import java.util.*

/**
 * ReferralManager - Handles referral tracking, deferred deep linking, and reward tiers
 *
 * Reward Tiers:
 *   1 referral  → 24h ad-free
 *   3 referrals → 3 days ad-free + "Supporter" badge
 *   5 referrals → 7 days ad-free + "Champion" badge
 *  10 referrals → 30 days ad-free + "Legend" badge
 */
object ReferralManager {
    private const val TAG = "ReferralManager"
    private const val PREFS_NAME = "referral_prefs"
    private const val KEY_REFERRAL_CODE = "user_referral_code"
    private const val KEY_REFERRED_BY = "referred_by_code"
    private const val KEY_REFERRAL_COUNT = "referral_count"
    private const val KEY_FIRST_OPEN_TIME = "first_open_time"
    private const val KEY_IS_REFERRER = "is_referrer"
    private const val KEY_INSTALL_REFERRER_CHECKED = "install_referrer_checked"
    // Fraud-guard keys
    private const val KEY_REFERRAL_TIMESTAMPS = "referral_timestamps"           // CSV of granted ms
    private const val KEY_REFEREE_OPEN_COUNT = "referee_open_count"             // app opens since install (this device)
    private const val KEY_REFEREE_VALIDATED = "referee_validated"               // friend confirmed: opens>=2 + use>=5min
    private const val KEY_DEVICE_FINGERPRINT = "device_fingerprint"             // hashed Android ID for self-ref check
    private const val KEY_INSTALL_REFERRER_DEVICE = "install_referrer_device"   // fingerprint of referrer device, if known
    // Fraud-guard limits
    private const val MAX_REFERRALS_PER_DAY = 5L
    private const val MAX_REFERRALS_PER_WEEK = 20L
    private const val MIN_MS_BETWEEN_REFERRALS = 90_000L                        // 90s — block instant-fire fakes
    private const val REFEREE_VALIDATION_MIN_OPENS = 2
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val WEEK_MS = 7L * DAY_MS
    // Reward keys
    private const val KEY_AD_FREE_UNTIL = "ad_free_until"
    private const val KEY_CURRENT_TIER = "current_tier"
    private const val KEY_LAST_REWARD_COUNT = "last_reward_count"

    // Reward tier thresholds
    enum class RewardTier(
        val requiredReferrals: Int,
        val adFreeHours: Long,
        val badge: String,
        val title: String,
        val description: String
    ) {
        NONE(0, 0, "", "Starter", "Share to start earning rewards!"),
        BRONZE(1, 24, "🥉", "First Share", "24h ad-free unlocked!"),
        SILVER(3, 72, "⭐", "Supporter", "3 days ad-free + Supporter badge!"),
        GOLD(5, 168, "🏆", "Champion", "7 days ad-free + Champion badge!"),
        LEGEND(10, 720, "👑", "Legend", "30 days ad-free + Legend badge!");

        companion object {
            fun forCount(count: Int): RewardTier = entries
                .sortedByDescending { it.requiredReferrals }
                .firstOrNull { count >= it.requiredReferrals }
                ?: NONE
        }
    }

    // Cached ad-free flag — set at app start, checked by RemoteConfigUtils without Context
    @Volatile
    private var cachedAdFree: Boolean = false

    /**
     * Check without Context (reads cached value set during init).
     * Safe to call from RemoteConfigUtils ad-gating methods.
     */
    fun isAdFreeFromReferralsCached(): Boolean = cachedAdFree

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Referral Code ──────────────────────────────────────────────

    fun getOrCreateReferralCode(context: Context): String {
        val prefs = getPrefs(context)
        var code = prefs.getString(KEY_REFERRAL_CODE, null)

        if (code == null) {
            code = "USER${Random().nextInt(900000) + 100000}"
            prefs.edit {
                putString(KEY_REFERRAL_CODE, code)
                putLong(KEY_FIRST_OPEN_TIME, System.currentTimeMillis())
            }
            AnalyticsUtils.logEvent(
                AnalyticsEvent.AppOpened,
                mapOf("is_first_open" to true, "referral_code" to code)
            )
        }
        return code
    }

    // ── Install Referrer API (deferred deep linking) ───────────────

    /**
     * Call this from MainActivity.onCreate() to capture the referral code
     * that was embedded in the Play Store install URL.
     * This is what makes referral tracking survive the install process.
     */
    fun checkInstallReferrer(context: Context) {
        val prefs = getPrefs(context)
        if (prefs.getBoolean(KEY_INSTALL_REFERRER_CHECKED, false)) return
        if (prefs.contains(KEY_REFERRED_BY)) return

        val referrerClient = InstallReferrerClient.newBuilder(context).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        try {
                            val details: ReferrerDetails = referrerClient.installReferrer
                            val referrerUrl = details.installReferrer ?: ""
                            Log.d(TAG, "Install referrer: $referrerUrl")

                            // Parse: utm_source=referral&utm_medium=share&utm_campaign=USER123456
                            val params = parseReferrerParams(referrerUrl)
                            val source = params["utm_source"]
                            val campaign = params["utm_campaign"]

                            if (source == "referral" && campaign != null && campaign.startsWith("USER")) {
                                processReferralCode(context, campaign, "install_referrer")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading install referrer", e)
                        }
                        prefs.edit { putBoolean(KEY_INSTALL_REFERRER_CHECKED, true) }
                        referrerClient.endConnection()
                    }
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                    InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                        Log.w(TAG, "Install referrer not available: $responseCode")
                        prefs.edit { putBoolean(KEY_INSTALL_REFERRER_CHECKED, true) }
                        referrerClient.endConnection()
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                Log.w(TAG, "Install referrer service disconnected")
            }
        })
    }

    private fun parseReferrerParams(referrer: String): Map<String, String> {
        return try {
            val decoded = URLDecoder.decode(referrer, "UTF-8")
            decoded.split("&").mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ── Deep Link / Intent Referral ────────────────────────────────

    fun checkReferral(context: Context, intent: Intent?) {
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_REFERRED_BY)) return

        val referralCode = intent?.data?.getQueryParameter("ref")
            ?: intent?.getStringExtra("referral_code")
            ?: intent?.getStringExtra("ref")

        if (!referralCode.isNullOrEmpty()) {
            processReferralCode(context, referralCode, intent?.data?.scheme ?: "unknown")
        }
    }

    private fun processReferralCode(context: Context, referralCode: String, source: String) {
        val prefs = getPrefs(context)
        val userCode = getOrCreateReferralCode(context)

        // Don't count self-referrals
        if (referralCode == userCode) return
        // Don't double-count
        if (prefs.contains(KEY_REFERRED_BY)) return

        prefs.edit { putString(KEY_REFERRED_BY, referralCode) }

        AnalyticsUtils.logEvent(
            AnalyticsEvent.ReferralInstalled,
            mapOf(
                "referral_code" to referralCode,
                "user_code" to userCode,
                "source" to source
            )
        )

        trackReferralForReferrer(context, referralCode)
    }

    private fun trackReferralForReferrer(context: Context, referrerCode: String) {
        AnalyticsUtils.logEvent(
            AnalyticsEvent.ReferralShared,
            mapOf(
                "referrer_code" to referrerCode,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    // ── Referral Links ─────────────────────────────────────────────

    /**
     * Primary share link — uses Play Store `referrer` param which is
     * picked up by the Install Referrer API after install.
     */
    fun getReferralLink(context: Context): String {
        val code = getOrCreateReferralCode(context)
        val packageName = context.packageName
        return "https://play.google.com/store/apps/details?id=$packageName&referrer=utm_source%3Dreferral%26utm_medium%3Dshare%26utm_campaign%3D$code"
    }

    /**
     * Same link — we no longer use a separate "short" link with ?ref= since
     * that param gets lost on new installs. Both methods now use the proper referrer param.
     */
    fun getShortReferralLink(context: Context): String = getReferralLink(context)

    // ── Referral Count & Rewards ───────────────────────────────────

    fun getReferralCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_REFERRAL_COUNT, 0)
    }

    /**
     * Validated entry point. Use this from server-side push OR install-referrer trusted source.
     * Returns: true = counted, false = rejected (fraud guard).
     * Rejection reasons logged to Firebase as referral_fraud_rejected.
     */
    fun incrementReferralCountGuarded(
        context: Context,
        refereeFingerprint: String? = null,
        source: String = "unknown"
    ): Boolean {
        val rejection = checkFraudGuards(context, refereeFingerprint)
        if (rejection != null) {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.ReferralInstalled,
                mapOf(
                    "fraud_rejected" to true,
                    "rejection_reason" to rejection,
                    "source" to source
                )
            )
            Log.w(TAG, "Referral rejected: $rejection (source=$source)")
            return false
        }
        recordReferralTimestamp(context)
        incrementReferralCount(context)
        return true
    }

    private fun checkFraudGuards(context: Context, refereeFingerprint: String?): String? {
        val now = System.currentTimeMillis()
        val timestamps = getReferralTimestamps(context)

        // Rule 1: anti-rapid-fire (last grant must be > MIN_MS_BETWEEN_REFERRALS ago)
        val lastTs = timestamps.maxOrNull() ?: 0L
        if (lastTs > 0 && (now - lastTs) < MIN_MS_BETWEEN_REFERRALS) {
            return "rapid_fire"
        }

        // Rule 2: max per day
        val perDay = timestamps.count { it > now - DAY_MS }
        if (perDay >= MAX_REFERRALS_PER_DAY) return "rate_limit_day"

        // Rule 3: max per week
        val perWeek = timestamps.count { it > now - WEEK_MS }
        if (perWeek >= MAX_REFERRALS_PER_WEEK) return "rate_limit_week"

        // Rule 4: self-device check (referee fingerprint must differ from referrer device)
        val myFingerprint = getOrCreateDeviceFingerprint(context)
        if (refereeFingerprint != null && refereeFingerprint == myFingerprint) {
            return "self_device"
        }

        return null
    }

    private fun getReferralTimestamps(context: Context): List<Long> {
        val raw = getPrefs(context).getString(KEY_REFERRAL_TIMESTAMPS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    private fun recordReferralTimestamp(context: Context) {
        val now = System.currentTimeMillis()
        val keep = (getReferralTimestamps(context) + now)
            .filter { it > now - WEEK_MS }   // garbage-collect older than 7d
            .takeLast(50)                    // hard cap
        getPrefs(context).edit {
            putString(KEY_REFERRAL_TIMESTAMPS, keep.joinToString(","))
        }
    }

    /**
     * Stable per-install device fingerprint. Uses Android ID (per-app-signing-key on Android 8+).
     * Resets only on factory reset or app uninstall+reinstall on Android <8.
     */
    fun getOrCreateDeviceFingerprint(context: Context): String {
        val prefs = getPrefs(context)
        val cached = prefs.getString(KEY_DEVICE_FINGERPRINT, null)
        if (cached != null) return cached

        val androidId = try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: ""
        } catch (e: Exception) {
            ""
        }
        val raw = "$androidId|${android.os.Build.MANUFACTURER}|${android.os.Build.MODEL}|${android.os.Build.FINGERPRINT}"
        val hash = try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            raw.hashCode().toString(16)
        }
        prefs.edit { putString(KEY_DEVICE_FINGERPRINT, hash) }
        return hash
    }

    // ── Referee-side behavioral validation ─────────────────────────────

    /**
     * Call on every cold app-start. Tracks opens on the referred-friend's side.
     * When opens reach REFEREE_VALIDATION_MIN_OPENS, mark referral as validated
     * (so a server-side listener can credit the referrer with confidence).
     */
    fun onReferredUserAppOpen(context: Context) {
        val prefs = getPrefs(context)
        // Only track if this device was referred
        if (!prefs.contains(KEY_REFERRED_BY)) return
        if (prefs.getBoolean(KEY_REFEREE_VALIDATED, false)) return

        val count = prefs.getInt(KEY_REFEREE_OPEN_COUNT, 0) + 1
        prefs.edit { putInt(KEY_REFEREE_OPEN_COUNT, count) }

        if (count >= REFEREE_VALIDATION_MIN_OPENS) {
            prefs.edit { putBoolean(KEY_REFEREE_VALIDATED, true) }
            val referrerCode = prefs.getString(KEY_REFERRED_BY, null) ?: return
            AnalyticsUtils.logEvent(
                AnalyticsEvent.ReferralInstalled,
                mapOf(
                    "stage" to "validated",
                    "referrer_code" to referrerCode,
                    "referee_fingerprint" to getOrCreateDeviceFingerprint(context),
                    "open_count" to count
                )
            )
            Log.d(TAG, "Referral validated after $count opens. Server should credit referrer=$referrerCode")
        }
    }

    fun isRefereeValidated(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_REFEREE_VALIDATED, false)

    fun incrementReferralCount(context: Context) {
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_REFERRAL_COUNT, 0) + 1
        prefs.edit { putInt(KEY_REFERRAL_COUNT, count) }

        // Check if user reached a new reward tier
        val previousTier = RewardTier.forCount(count - 1)
        val newTier = RewardTier.forCount(count)

        if (newTier != previousTier && newTier != RewardTier.NONE) {
            grantReward(context, newTier)
        }

        // Track milestone achievements
        when (count) {
            1 -> AnalyticsUtils.logEvent(
                AnalyticsEvent.AchievementUnlocked,
                mapOf("achievement" to "first_referral", "count" to count, "tier" to "BRONZE")
            )
            3 -> AnalyticsUtils.logEvent(
                AnalyticsEvent.AchievementUnlocked,
                mapOf("achievement" to "supporter", "count" to count, "tier" to "SILVER")
            )
            5 -> AnalyticsUtils.logEvent(
                AnalyticsEvent.AchievementUnlocked,
                mapOf("achievement" to "champion", "count" to count, "tier" to "GOLD")
            )
            10 -> AnalyticsUtils.logEvent(
                AnalyticsEvent.AchievementUnlocked,
                mapOf("achievement" to "legend", "count" to count, "tier" to "LEGEND")
            )
        }
    }

    private fun grantReward(context: Context, tier: RewardTier) {
        val prefs = getPrefs(context)
        val adFreeUntil = System.currentTimeMillis() + (tier.adFreeHours * 3600 * 1000)
        prefs.edit {
            putLong(KEY_AD_FREE_UNTIL, adFreeUntil)
            putString(KEY_CURRENT_TIER, tier.name)
            putInt(KEY_LAST_REWARD_COUNT, tier.requiredReferrals)
        }
        Log.d(TAG, "Granted reward: ${tier.title} — ad-free for ${tier.adFreeHours}h")
    }

    /**
     * Returns true if the user has earned ad-free time through referrals
     * and it hasn't expired yet.
     */
    fun isAdFreeFromReferrals(context: Context): Boolean {
        val adFreeUntil = getPrefs(context).getLong(KEY_AD_FREE_UNTIL, 0)
        val result = adFreeUntil > System.currentTimeMillis()
        cachedAdFree = result
        return result
    }

    /**
     * Milliseconds remaining of ad-free time. 0 if expired or not earned.
     */
    fun getAdFreeRemainingMs(context: Context): Long {
        val adFreeUntil = getPrefs(context).getLong(KEY_AD_FREE_UNTIL, 0)
        return maxOf(0, adFreeUntil - System.currentTimeMillis())
    }

    fun getCurrentTier(context: Context): RewardTier {
        return RewardTier.forCount(getReferralCount(context))
    }

    fun getNextTier(context: Context): RewardTier? {
        val current = getCurrentTier(context)
        return RewardTier.entries
            .sortedBy { it.requiredReferrals }
            .firstOrNull { it.requiredReferrals > current.requiredReferrals }
    }

    fun getReferralsToNextTier(context: Context): Int {
        val count = getReferralCount(context)
        val next = getNextTier(context) ?: return 0
        return next.requiredReferrals - count
    }

    // ── Referral Status ────────────────────────────────────────────

    fun wasReferred(context: Context): Boolean {
        return getPrefs(context).contains(KEY_REFERRED_BY)
    }

    fun getReferredByCode(context: Context): String? {
        return getPrefs(context).getString(KEY_REFERRED_BY, null)
    }

    // ── Sharing ────────────────────────────────────────────────────

    fun shareReferralLink(context: Context, shareText: String = "") {
        val referralLink = getReferralLink(context)
        val code = getOrCreateReferralCode(context)

        val defaultText = """
            🔍 Check out this amazing device health checker app!

            📱 Get detailed insights about your phone's performance, battery, storage, and security.

            Use my referral code: $code

            Download now: $referralLink

            #PhoneHealth #DeviceChecker #TechTools
        """.trimIndent()

        val finalText = if (shareText.isNotEmpty()) shareText else defaultText

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, finalText)
            putExtra(Intent.EXTRA_SUBJECT, "Check out this amazing device health app!")
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share via"))

        AnalyticsUtils.logEvent(
            AnalyticsEvent.ReferralShared,
            mapOf("referral_code" to code, "method" to "generic_share")
        )
    }
}
