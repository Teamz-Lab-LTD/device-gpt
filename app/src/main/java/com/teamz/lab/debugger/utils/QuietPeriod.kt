package com.teamz.lab.debugger.utils

import android.content.Context

/**
 * The first 72 hours after install are quiet: no full-screen ads (shown or requested) and no
 * paywall the user did not ask for. Measured by install age, not by "sessions": a session here is
 * MainActivity.onCreate at least 60 s apart, so "wait until session N" opened within the first
 * hour. 3/3 humans who saw a day-0 full-screen ad uninstalled that day; 86% of new users saw a
 * paywall, 6.4 times each (GA4, 90 days to 2026-10-08).
 *
 * Paywalls the user asks for (PRO button, locked feature) are not affected.
 */
object QuietPeriod {
    const val DURATION_MS = 72L * 3_600_000L

    /** Google Play billing is not available here; a paywall can only annoy. */
    private val BILLING_UNAVAILABLE = setOf("IR", "CU", "KP", "SY")

    @Volatile private var firstInstallMs: Long? = null

    fun init(context: Context) {
        firstInstallMs = try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (_: Exception) { null }
    }

    /** Unknown or negative age is not quiet: it must never switch monetisation off for good. */
    fun isActive(installAgeMs: Long?): Boolean = installAgeMs != null && installAgeMs in 0 until DURATION_MS

    fun activeNow(nowMs: Long = System.currentTimeMillis()): Boolean =
        firstInstallMs?.let { isActive(nowMs - it) } ?: false

    fun billingUnavailable(country: String): Boolean = country.uppercase() in BILLING_UNAVAILABLE

    fun unsolicitedPaywallAllowed(installAgeMs: Long?, country: String): Boolean =
        !isActive(installAgeMs) && !billingUnavailable(country)

    fun unsolicitedPaywallAllowed(): Boolean = unsolicitedPaywallAllowed(
        firstInstallMs?.let { System.currentTimeMillis() - it },
        RemoteConfigUtils.countryCode()
    )
}
