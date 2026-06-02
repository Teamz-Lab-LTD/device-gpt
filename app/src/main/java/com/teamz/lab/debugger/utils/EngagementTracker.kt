package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Kotlin port of team_mvp_kit (Flutter) RatingService engagement pattern.
 * Same SharedPreferences-backed counter + same analytics event names
 * (app_installed / user_engagement_session / user_engagement_significant_action)
 * so cross-app Firebase queries can compare DeviceGPT against the Flutter portfolio.
 *
 * Adds a streak field on top of the MVP kit pattern: consecutive days with at
 * least one session. Used by the habit-loop work — Home tab "🔥 7 days" card.
 */
object EngagementTracker {
    private const val TAG = "EngagementTracker"
    private const val PREFS_NAME = "engagement_prefs"

    private const val KEY_INSTALL_DATE = "install_date_iso"
    private const val KEY_SESSION_COUNT = "session_count"
    private const val KEY_SIGNIFICANT_ACTION_COUNT = "significant_action_count"
    private const val KEY_LAST_SESSION_YMD = "last_session_ymd"
    private const val KEY_CONSECUTIVE_DAYS = "consecutive_days"
    private const val KEY_LAST_TRACKED_SESSION_TS = "last_tracked_session_ts"

    private const val SESSION_DEDUP_WINDOW_MS = 60_000L  // don't double-count within 60s

    private val utcYmd = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val utcIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Call once from MainActivity.onCreate (or Application). Sets install date
     * on first run, fires `app_installed`, then runs `trackSession` so the
     * first cold-start counts.
     */
    fun init(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_INSTALL_DATE)) {
            val nowIso = utcIso.format(Date())
            p.edit { putString(KEY_INSTALL_DATE, nowIso) }
            AnalyticsUtils.logEvent(
                AnalyticsEvent.AppInstalled,
                mapOf("timestamp" to nowIso)
            )
            Log.d(TAG, "First install recorded at $nowIso")
        }
        trackSession(context)
    }

    /**
     * Increments session count, updates streak. Safe to call multiple times
     * per app lifecycle — internally dedups within a 60-second window so
     * onResume re-fires don't inflate the count.
     *
     * Streak math: if last session was YESTERDAY (UTC), streak += 1.
     * If last session was TODAY, streak unchanged. Otherwise streak resets to 1.
     */
    fun trackSession(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val lastTracked = p.getLong(KEY_LAST_TRACKED_SESSION_TS, 0L)
        if (now - lastTracked < SESSION_DEDUP_WINDOW_MS) return

        val sessionCount = p.getInt(KEY_SESSION_COUNT, 0) + 1
        val todayYmd = utcYmd.format(Date(now))
        val lastYmd = p.getString(KEY_LAST_SESSION_YMD, null)
        val prevStreak = p.getInt(KEY_CONSECUTIVE_DAYS, 0)

        val streak = when (lastYmd) {
            null -> 1
            todayYmd -> if (prevStreak < 1) 1 else prevStreak
            yesterday(todayYmd) -> prevStreak + 1
            else -> 1
        }

        p.edit {
            putInt(KEY_SESSION_COUNT, sessionCount)
            putString(KEY_LAST_SESSION_YMD, todayYmd)
            putInt(KEY_CONSECUTIVE_DAYS, streak)
            putLong(KEY_LAST_TRACKED_SESSION_TS, now)
        }

        AnalyticsUtils.logEvent(
            AnalyticsEvent.UserEngagementSession,
            mapOf(
                "session_count" to sessionCount,
                "consecutive_days" to streak,
                "timestamp" to utcIso.format(Date(now))
            )
        )
        Log.d(TAG, "Session $sessionCount tracked, streak=$streak day(s)")
    }

    /**
     * Increment the meaningful-action counter and fire analytics. Call from
     * the moments that signal real value — scan completed, report generated,
     * spyware detected and dismissed, etc.
     *
     * Pairs with `shouldShowEngagementPrompt(threshold)` to gate review
     * prompts / paywalls / feature discovery cards by engagement depth.
     */
    fun trackSignificantAction(
        context: Context,
        actionType: String,
        extra: Map<String, Any?> = emptyMap()
    ) {
        val p = prefs(context)
        val count = p.getInt(KEY_SIGNIFICANT_ACTION_COUNT, 0) + 1
        p.edit { putInt(KEY_SIGNIFICANT_ACTION_COUNT, count) }

        AnalyticsUtils.logEvent(
            AnalyticsEvent.UserEngagementSignificantAction,
            mapOf(
                "action_count" to count,
                "action_type" to actionType,
                "timestamp" to utcIso.format(Date())
            ) + extra
        )
        Log.d(TAG, "Significant action #$count: $actionType")
    }

    // ── Read accessors (for Home tab card, paywall gating, review prompt) ───

    fun getSessionCount(context: Context): Int =
        prefs(context).getInt(KEY_SESSION_COUNT, 0)

    fun getSignificantActionCount(context: Context): Int =
        prefs(context).getInt(KEY_SIGNIFICANT_ACTION_COUNT, 0)

    fun getConsecutiveDays(context: Context): Int =
        prefs(context).getInt(KEY_CONSECUTIVE_DAYS, 0)

    fun getInstallDateIso(context: Context): String? =
        prefs(context).getString(KEY_INSTALL_DATE, null)

    fun getDaysSinceInstall(context: Context): Int {
        val iso = getInstallDateIso(context) ?: return 0
        return try {
            val installMs = utcIso.parse(iso)?.time ?: return 0
            ((System.currentTimeMillis() - installMs) / (1000L * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Engagement gate matching MVP kit's `shouldShowRatingPrompt` shape.
     * Use to decide if user is "deep enough" to receive a prompt without
     * looking like first-session spam.
     */
    fun shouldShowEngagementPrompt(
        context: Context,
        minSessions: Int = 3,
        minSignificantActions: Int = 1,
        minDaysSinceInstall: Int = 1
    ): Boolean {
        val sessions = getSessionCount(context)
        val actions = getSignificantActionCount(context)
        val days = getDaysSinceInstall(context)
        return sessions >= minSessions &&
            actions >= minSignificantActions &&
            days >= minDaysSinceInstall
    }

    private fun yesterday(todayYmd: String): String {
        return try {
            val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.time = utcYmd.parse(todayYmd) ?: return ""
            cal.add(java.util.Calendar.DATE, -1)
            utcYmd.format(cal.time)
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Convenience action type constants for trackSignificantAction. Keep flat
 * so it's easy to grep all engagement signals in one place.
 */
object SignificantAction {
    const val SCAN_COMPLETED = "scan_completed"
    const val SPYWARE_DETECTED = "spyware_detected"
    const val REPORT_GENERATED = "report_generated"
    const val SHARE_LINK_GENERATED = "share_link_generated"
    const val REFERRAL_INVITE_SENT = "referral_invite_sent"
    const val NETWORK_TEST_COMPLETED = "network_test_completed"
    const val BATTERY_SCAN_COMPLETED = "battery_scan_completed"
    const val PREMIUM_PURCHASED = "premium_purchased"
    const val PAYWALL_DISMISSED = "paywall_dismissed"
    const val ANOMALY_RESOLVED = "anomaly_resolved"

    // Event-driven habit triggers (per 2026-06-02 synthesized plan).
    const val CHARGE_CYCLE_COMPLETED = "charge_cycle_completed"
    const val CHARGE_SUMMARY_OPENED = "charge_summary_opened"
    const val NEW_APP_INSTALLED = "new_app_installed"
    const val NEW_APP_SCAN_PROMPTED = "new_app_scan_prompted"
    const val NEW_WIFI_DETECTED = "new_wifi_detected"
}
