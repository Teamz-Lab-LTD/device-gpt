package com.teamz.lab.debugger.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the 2026-08-28 paywall frequency work.
 *
 * Measured before the change (GA4 481224245, 28d to 2026-08-26):
 *   premium_paywall_shown       717  over 418 sessions = 1.72 per session
 *   premium_purchase_completed    0
 *   dismissals 165: no_response 68 + sheet_dismissed 61 = 78% never engaged,
 *                   too_expensive 6, no_value_seen 2
 *   installs 116, app_remove 85 = 73% uninstall, D1 retention 8.6%
 *
 * The paywall was costing retention and returning nothing.
 */
class PaywallSessionCapTest {

    private fun src(path: String) = File(path).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    private val policy = src("src/main/java/com/teamz/lab/debugger/utils/PaywallPolicy.kt")
    private val rc = src("src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt")

    @Test
    fun `both trigger gates consult the per-session cap`() {
        val cold = policy.substringAfter("fun coldTriggerAllowed").substringBefore("fun delightTriggerAllowed")
        assertTrue(
            "coldTriggerAllowed must check the session cap BEFORE the delay-flag early return, " +
                "or the cap does nothing while paywall_delay_enabled is false — which is its " +
                "value on the live template",
            cold.indexOf("sessionCapClear(context)") in 0 until cold.indexOf("isPaywallDelayEnabled")
        )
        val delight = policy.substringAfter("fun delightTriggerAllowed").substringBefore("private fun sessionCapClear")
        assertTrue(
            "delightTriggerAllowed must check it too, or delight triggers bypass the cap",
            delight.contains("sessionCapClear(context)")
        )
    }

    @Test
    fun `the counter resets on a new session by construction, not by a lifecycle hook`() {
        val fn = policy.substringAfter("private fun shownThisSession")
        assertTrue(
            "must key the counter on the session number so it self-resets — a hook or timer " +
                "can be missed or survive process death with a stale count",
            fn.contains("EngagementTracker.getSessionCount(context)") &&
                fn.contains("KEY_SESSION_STAMP")
        )
    }

    @Test
    fun `zero is a working kill switch`() {
        val fn = policy.substringAfter("private fun sessionCapClear")
        assertTrue(
            "max <= 0 must block, so paywall_max_per_session = 0 turns the paywall off " +
                "with no release",
            fn.contains("if (max <= 0L) return false")
        )
    }

    @Test
    fun `recordShown is wired to every site that logs the funnel event`() {
        val showSites = listOf(
            "src/main/java/com/teamz/lab/debugger/ui/PaywallWithReferralFallback.kt",
            "src/main/java/com/teamz/lab/debugger/ui/RevenueCatPaywall.kt",
        )
        var logged = 0
        var recorded = 0
        for (f in showSites) {
            val t = File(f).readText()
            logged += Regex("AnalyticsEvent\\.PremiumPaywallShown").findAll(t).count()
            recorded += Regex("PaywallPolicy\\.recordShown\\(").findAll(t).count()
        }
        assertTrue("expected at least one show site", logged > 0)
        assertEquals(
            "every premium_paywall_shown must also increment the cap. If these drift, the cap " +
                "silently under-counts and the 1.72-per-session problem comes back.",
            logged, recorded
        )
    }

    @Test
    fun `a dismissal that shows no engagement backs off`() {
        val route = policy.substringAfter("fun routeForReason").substringBefore("fun onDismissReason")
        for (reason in listOf("no_response", "sheet_dismissed")) {
            assertTrue(
                "\"$reason\" is one of the two largest dismissal buckets (68 and 61 of 165). " +
                    "Leaving it on LOG_ONLY is what let the app keep re-showing to people who " +
                    "were ignoring the sheet.",
                route.contains("\"$reason\"")
            )
        }
        assertTrue(
            "no branch may fall through to LOG_ONLY — that was the bug",
            !route.contains("RouteAction.LOG_ONLY")
        )
    }

    @Test
    fun `the cap has a bundled default so it applies before Remote Config fetches`() {
        assertTrue(
            "a server-only flag would leave the very first session — the one that matters " +
                "most for D1 — uncapped",
            rc.contains("\"paywall_max_per_session\" to 1L") ||
                rc.contains("\"paywall_max_per_session\" to DEFAULT_PAYWALL_MAX_PER_SESSION")
        )
        assertTrue(
            "when the map names a constant, that constant must still be 1",
            !rc.contains("\"paywall_max_per_session\" to DEFAULT_PAYWALL_MAX_PER_SESSION") ||
                rc.contains("DEFAULT_PAYWALL_MAX_PER_SESSION = 1L")
        )
        val getter = rc.substringAfter("fun getPaywallMaxPerSession")
        assertTrue(
            "a negative value must not disable the cap by accident",
            getter.contains("if (value < 0L) 1L else value") ||
                getter.contains("if (value < 0L) DEFAULT_PAYWALL_MAX_PER_SESSION else value")
        )
        assertTrue(
            "the accessor must answer from the bundled default before setDefaultsAsync lands — " +
                "pre-defaults getLong returns 0, which sessionCapClear reads as a deliberate " +
                "kill switch, so the cap silently suppresses every paywall on a fresh install",
            getter.contains("defaultsApplied")
        )
    }
}
