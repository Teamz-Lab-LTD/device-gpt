package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.ui.adaptive.tabViewedEvent
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.MicTestUtils
import com.teamz.lab.debugger.utils.MicTestUtils.PermissionNextStep
import com.teamz.lab.debugger.utils.NetworkReachabilityTester
import com.teamz.lab.debugger.utils.TabType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Four defects in the first-session funnel, fixed 2026-09-09. Two are behaviour and get real
 * tests; two live inside @Composable bodies that a JVM test cannot instantiate, and get
 * regression tripwires that are labelled as such rather than dressed up as behaviour tests.
 */
class FirstSessionFunnelFixesTest {

    // ---------- 1. mic permission: one Deny must not become a dead end ----------

    @Test
    fun `a first denial still offers the system dialog`() {
        assertEquals(
            "MicTestCard set permanentlyDenied on the FIRST denial, so one tap of Deny sent the " +
                "user to a Settings deep link when tapping Allow again would have worked — on " +
                "the feature the app title leads with, in session one.",
            PermissionNextStep.ASK_AGAIN,
            MicTestUtils.permissionNextStep(granted = false, canShowRationale = true),
        )
    }

    @Test
    fun `a second denial routes to Settings, because Android will not ask again`() {
        assertEquals(
            PermissionNextStep.OPEN_SETTINGS,
            MicTestUtils.permissionNextStep(granted = false, canShowRationale = false),
        )
    }

    @Test
    fun `an unknown host prefers Settings over a button that may do nothing`() {
        // Settings always works. A re-prompt Android silently refuses is a dead end, so when
        // there is no Activity to ask, the safe default is the one that cannot dead-end.
        assertEquals(
            PermissionNextStep.OPEN_SETTINGS,
            MicTestUtils.permissionNextStep(granted = false, canShowRationale = null),
        )
    }

    @Test
    fun `granted runs the test regardless of rationale state`() {
        for (r in listOf(true, false, null)) {
            assertEquals(
                PermissionNextStep.RUN_TEST,
                MicTestUtils.permissionNextStep(granted = true, canShowRationale = r),
            )
        }
    }

    // ---------- 2. tab analytics: no two tabs may share an event ----------

    @Test
    fun `every tab logs its own view event`() {
        val mapped = TabType.entries.associateWith { tabViewedEvent(it) }
        val collisions = mapped.entries
            .groupBy { it.value }
            .filterValues { it.size > 1 }
        assertTrue(
            "Two or more tabs log the same event: " +
                collisions.map { (e, tabs) -> "${e.name} <- ${tabs.map { it.key }}" } +
                ". The widget-navigation path hardcoded TabHealthViewed for whichever tab it " +
                "opened, so Health absorbed every other tab's views and looked like the most " +
                "used screen in the app.",
            collisions.isEmpty(),
        )
        assertEquals(
            "Every TabType needs an event; a missing one is a silently unmeasured tab.",
            TabType.entries.size, mapped.values.toSet().size,
        )
    }

    @Test
    fun `health maps to health and app doctor does not`() {
        assertEquals(AnalyticsEvent.TabHealthViewed, tabViewedEvent(TabType.HEALTH))
        assertEquals(AnalyticsEvent.TabAppDoctorViewed, tabViewedEvent(TabType.APP_DOCTOR))
    }

    // ---------- 3 & 4. tripwires, not behaviour tests ----------

    /**
     * Comments stripped. The first version of the probe tripwire below failed on its own
     * explanation: the comment naming `attempts = 1` as the defect matched the grep looking
     * for it. Same shape as the claims guard that matched its own reassurance text.
     */
    private fun source(path: String): String {
        val f = File("src/main/java/com/teamz/lab/debugger/$path")
        assertTrue("source not found: ${f.absolutePath} — this test cannot fail if the path " +
            "is wrong, so the path is asserted first", f.exists())
        return f.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    }

    @Test
    fun `the repeated probe is not driven one attempt at a time`() {
        // TRIPWIRE. probeDomainRepeated skips ATTEMPT_GAP_MS when attempts == 1, so calling it
        // in a loop with attempts = 1 — which CustomDomainProbeCard did, to drive a progress
        // indicator — ran all four attempts back to back against a warm DNS cache and a pooled
        // connection. That is the exact condition its KDoc says sequential probing exists to
        // avoid, and the store listing sells the 4 attempts as proof against a lucky result.
        val card = source("ui/CustomDomainProbeCard.kt")
        assertTrue(
            "CustomDomainProbeCard passes attempts = 1; use onAttempt for progress instead.",
            !card.contains("attempts = 1"),
        )
        assertTrue(
            "The card must ask for the full attempt count, not one at a time.",
            card.contains("attempts = attempts"),
        )
        assertEquals("The listing claims four attempts.", 4, NetworkReachabilityTester.DEFAULT_PROBE_ATTEMPTS)
    }

    @Test
    fun `one paywall journey charges the session cap exactly once`() {
        // TRIPWIRE. The RevenueCat paywall, the referral-fallback modal and the fallback's share
        // button each called PaywallPolicy.recordShown and each logged premium_paywall_shown.
        // The fallback is only reachable from handleRcDismiss, so all three belong to one
        // journey: the session's single allowance was spent three times and the funnel's
        // denominator was inflated threefold.
        val fallback = source("ui/PaywallWithReferralFallback.kt")
        assertEquals(
            "PaywallWithReferralFallback must not record a show; RevenueCatPaywall already did.",
            0, Regex("PaywallPolicy\\.recordShown").findAll(fallback).count(),
        )
        assertEquals(
            "premium_paywall_shown belongs to the paywall alone. The fallback has its own events.",
            0, Regex("AnalyticsEvent\\.PremiumPaywallShown").findAll(fallback).count(),
        )
        assertEquals(
            "RevenueCatPaywall stays the single place a show is counted.",
            1, Regex("PaywallPolicy\\.recordShown")
                .findAll(source("ui/RevenueCatPaywall.kt")).count(),
        )
    }

    @Test
    fun `a mic failure while speaking is measured`() {
        // TRIPWIRE. The noise-floor failure logged MicTestFailed and the speaking-phase failure
        // did not, so a mic taken mid-test — an incoming call, another app grabbing it — was
        // invisible. It is the likelier of the two: 3000ms of capture against 1500ms.
        val card = source("ui/MicTestCard.kt")
        assertTrue(
            "the speaking-phase failure must log a distinguishable reason",
            card.contains("record_unavailable_while_speaking"),
        )
        for (reason in listOf(
            "record_unavailable",                  // noise-floor phase
            "record_unavailable_while_speaking",   // the phase that was silent
            "permission_denied",                   // can still be asked again
            "permission_blocked",                  // only Settings can grant now
        )) {
            assertTrue("mic failure reason not reported: $reason", card.contains(reason))
        }
    }
}
