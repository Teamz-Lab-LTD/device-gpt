package com.teamz.lab.debugger.utils

import android.content.Context

/**
 * When the "Allow Realtime Monitor" notification pre-prompt may open on its own.
 *
 * It used to open on every cold start for Android 13+ users without POST_NOTIFICATIONS —
 * including the first landing, seconds after the first scan. GA4 2026-08-11..09-23: 40 of 52
 * same-day uninstalls happened within 15 minutes of first_open, and users who got the monitor
 * in those 15 minutes uninstalled at 25% vs 22% overall, so asking early bought nothing.
 *
 * The drawer's monitor toggle still asks immediately; only the unprompted dialog waits.
 */
object MonitorPromptPolicy {

    private const val MIN_INSTALL_AGE_MS = 24 * 60 * 60 * 1000L

    /** [installAgeMs] null = install time unreadable; do not block, behave as before. */
    fun shouldOffer(installAgeMs: Long?): Boolean =
        installAgeMs == null || installAgeMs >= MIN_INSTALL_AGE_MS

    fun installAgeMs(context: Context): Long? = try {
        val first = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        System.currentTimeMillis() - first
    } catch (_: Exception) {
        null
    }
}
