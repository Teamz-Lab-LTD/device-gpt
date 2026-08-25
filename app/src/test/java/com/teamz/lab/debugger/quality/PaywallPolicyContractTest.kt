package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.PaywallPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.2.0 Phase 4 — paywall policy guards.
 *
 * Pure-logic tests for the pre-registered dismiss-reason routing table +
 * source-text guards for the cold-open gate wiring. (Full RC-dependent paths
 * need Firebase boot — the routing table itself is pure and testable.)
 *
 * Routing table is PRE-REGISTERED (2026-07-10 synthesis §5): changing a route
 * is a strategy decision, not a refactor — this test makes that explicit.
 */
class PaywallPolicyContractTest {

    // ---- Suppression windows must both be READ, not just written ---------------

    @Test
    fun `the 7-day cooldown key is actually consulted, not just written`() {
        // The bug this pins: COOLDOWN_7D wrote policy_cooldown_until, and
        // suppressionClear() only ever read policy_suppress_until. Nothing anywhere
        // read the cooldown key except debugReset(), which deletes it. So a user who
        // chose "not now" — the most likely answer on that sheet — asked the app to
        // back off for a week and it forgot instantly. Source-text guard, because the
        // read path needs a Context and SharedPreferences to exercise directly.
        val src = File("src/main/java/com/teamz/lab/debugger/utils/PaywallPolicy.kt")
            .let { if (it.exists()) it else File("app/src/main/java/com/teamz/lab/debugger/utils/PaywallPolicy.kt") }
        assertTrue("PaywallPolicy.kt not found", src.exists())
        val body = src.readText()

        val fn = body.substringAfter("private fun suppressionClear").substringBefore("\n    }")
        assertTrue(
            "suppressionClear() must read KEY_SUPPRESS_UNTIL",
            fn.contains("KEY_SUPPRESS_UNTIL")
        )
        assertTrue(
            "suppressionClear() must ALSO read KEY_COOLDOWN_UNTIL — writing a cooldown " +
                "nothing reads means 'not now' silently does nothing",
            fn.contains("KEY_COOLDOWN_UNTIL")
        )
    }

    @Test
    fun `every suppression key that is written is also read somewhere`() {
        val src = File("src/main/java/com/teamz/lab/debugger/utils/PaywallPolicy.kt")
            .let { if (it.exists()) it else File("app/src/main/java/com/teamz/lab/debugger/utils/PaywallPolicy.kt") }
        val body = src.readText()
        for (key in listOf("KEY_SUPPRESS_UNTIL", "KEY_COOLDOWN_UNTIL")) {
            val written = body.contains("putLong($key")
            val read = body.contains("getLong($key")
            assertTrue("$key is written but never read — a dead write", !written || read)
        }
    }

    // ---- Pure routing table ----------------------------------------------------

    @Test
    fun `routing table matches the pre-registered decision table`() {
        assertEquals(PaywallPolicy.RouteAction.RESHOW_ONCE, PaywallPolicy.routeForReason("closed_by_mistake"))
        assertEquals(PaywallPolicy.RouteAction.COOLDOWN_7D, PaywallPolicy.routeForReason("not_now"))
        assertEquals(PaywallPolicy.RouteAction.SUPPRESS_30D, PaywallPolicy.routeForReason("no_value_seen"))
        // too_expensive stays LOG_ONLY until the Tier-B/weekly downsell target
        // exists (Phase 2) — never route to vaporware.
        assertEquals(PaywallPolicy.RouteAction.LOG_ONLY, PaywallPolicy.routeForReason("too_expensive"))
        assertEquals(PaywallPolicy.RouteAction.LOG_ONLY, PaywallPolicy.routeForReason("other"))
        assertEquals(PaywallPolicy.RouteAction.LOG_ONLY, PaywallPolicy.routeForReason("no_response"))
        assertEquals(PaywallPolicy.RouteAction.LOG_ONLY, PaywallPolicy.routeForReason("anything_unknown"))
    }

    // ---- Source-text wiring guards ------------------------------------------------

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    @Test
    fun `both cold-open paywall chains pass through the session-scan gate`() {
        val src = File(
            projectRoot(),
            "app/src/main/java/com/teamz/lab/debugger/ui/adaptive/DeviceGptNavExperience.kt"
        ).readText()
        val gateCalls = Regex("PaywallPolicy\\.coldTriggerAllowed").findAll(src).count()
        assertTrue(
            "BOTH cold-open chains (after-review + session fallback) must check " +
                "coldTriggerAllowed — found $gateCalls call(s), need >= 2. The 89% dismiss " +
                "rate was measured on ungated cold-open triggers.",
            gateCalls >= 2
        )
    }

    @Test
    fun `dismiss-reason sheet routes through PaywallPolicy`() {
        val src = File(
            projectRoot(),
            "app/src/main/java/com/teamz/lab/debugger/ui/PaywallWithReferralFallback.kt"
        ).readText()
        assertTrue(
            "PaywallDismissReasonSheet must call PaywallPolicy.onDismissReason so every " +
                "reason is routed (Phase 1 log-only) — otherwise the routing table is dead code.",
            src.contains("PaywallPolicy.onDismissReason")
        )
    }

    @Test
    fun `policy defaults to log-only via RC flag`() {
        val src = File(
            projectRoot(),
            "app/src/main/java/com/teamz/lab/debugger/utils/PaywallPolicy.kt"
        ).readText()
        assertTrue(
            "onDismissReason must ALWAYS log, and only apply state when RC routing " +
                "flag is on (runtime guard against routing to vaporware).",
            src.contains("if (!routingEnabled) return false")
        )
    }
}
