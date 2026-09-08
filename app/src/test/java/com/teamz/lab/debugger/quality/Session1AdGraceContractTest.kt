package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the ad-grace window on BOTH interstitial entry points.
 *
 * The July 2026 growth plan's first-10-seconds spec says, verbatim: "No ads in sessions
 * 1-2." The mechanism to honour it exists — RC ads_grace_sessions, read by
 * showAdBeforeAction — but the other entry point never checked it:
 *
 *   showAdBeforeAction   checked ads_grace_sessions          (guarded)
 *   showAdIfAvailable    did not                             (unguarded)
 *
 * and showAdIfAvailable is the one ai_click_handler uses, so the very first thing a new
 * user got for tapping the app's headline feature was a fullscreen ad.
 *
 * "Completely unusable due to excessive ads" and "cheio de Ads" are the only complaint
 * repeated across DeviceGPT's 1-star reviews, in two languages, five months apart.
 */
class Session1AdGraceContractTest {

    /** Comments stripped: a guard in this repo once matched its own explanatory comment. */
    private fun src(p: String) = File(p).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    private val interstitial = src("src/main/java/com/teamz/lab/debugger/utils/interstitial_ad_manager.kt")
    private val rc = src("src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt")

    @Test
    fun `every interstitial entry point honours the grace window`() {
        for (fn in listOf("fun showAdBeforeAction", "fun showAdIfAvailable")) {
            val body = interstitial.substringAfter(fn).substringBefore("\n    fun ")
            assertTrue(
                "$fn must consult getAdsGraceSessions(). An unguarded entry point makes the " +
                    "grace window advisory — and the unguarded one was the path the AI button used.",
                body.contains("getAdsGraceSessions()")
            )
        }
    }

    @Test
    fun `the grace check runs before the ad is shown, not after`() {
        val body = interstitial.substringAfter("fun showAdIfAvailable").substringBefore("\n    fun ")
        val grace = body.indexOf("getAdsGraceSessions()")
        val show = body.indexOf("interstitialAd?.show")
        assertTrue("expected showAdIfAvailable to contain both the grace check and a show call",
            grace >= 0 && show >= 0)
        assertTrue(
            "the grace check must precede the show call, or it gates nothing",
            grace < show
        )
    }

    @Test
    fun `the grace window has a bundled default so it holds before Remote Config lands`() {
        // Widened, NOT weakened: the map now names a constant so it cannot drift from the
        // pre-defaults answer, so accept either spelling — and when it is the constant form,
        // additionally pin the constant's own value.
        val literal = Regex("\"ads_grace_sessions\"\\s+to\\s+(\\d+)L").find(rc)?.groupValues?.get(1)?.toInt()
        val viaConst = rc.contains("\"ads_grace_sessions\" to DEFAULT_ADS_GRACE_SESSIONS")
        assertTrue(
            "ads_grace_sessions must carry an explicit bundled default — a server-only value " +
                "leaves session 1 unprotected on exactly the fresh installs it exists for",
            literal != null || viaConst
        )
        val n = literal
            ?: Regex("DEFAULT_ADS_GRACE_SESSIONS\\s*=\\s*(\\d+)L").find(rc)?.groupValues?.get(1)?.toInt()
        assertTrue(
            "the bundled default must actually cover sessions 1-2 (>=2). It shipped as 0 in " +
                "04973e2 and was never lit, so the spec was documented but not enforced. Found $n.",
            n != null && n >= 2
        )
        assertTrue(
            "the accessor must answer from the bundled default until setDefaultsAsync lands. " +
                "Without the guard getLong returns Firebase's static 0 on a fresh install — " +
                "'interstitials from session 1', the exact thing this contract forbids.",
            rc.substringAfter("fun getAdsGraceSessions").contains("defaultsApplied")
        )
    }

    private val app = src("src/main/java/com/teamz/lab/debugger/Application.kt")
    private val review = src("src/main/java/com/teamz/lab/debugger/utils/ReviewPromptManager.kt")

    @Test
    fun `nothing asks for notification permission before the app has been seen`() {
        assertTrue(
            "Application.onCreate must not request POST_NOTIFICATIONS. It fired before a " +
                "single pixel was drawn, and it was the first of TWO asks for the same " +
                "permission — HandleSystemMonitorAutoStart asks again on first tab " +
                "composition. The contextual one is the one that survives.",
            !app.contains("Notifications.requestPermission")
        )
    }

    @Test
    fun `the review sheet waits for the value moment`() {
        // Pin the CONDITION, not the file. The first version of this guard only asserted
        // that "hasCompletedScan" appeared somewhere in the branch — which stayed true when
        // the term was deleted from the if-condition and left in the val above it. Mutation
        // testing caught a guard that could not fail.
        val condition = Regex("if \\(ENABLE_FIRST_LAUNCH_REVIEW[^)]*\\)").find(review)?.value
        assertTrue("the first-launch review condition was not found at all", condition != null)
        assertTrue(
            "the first-launch review must be gated on the completed scan IN THE CONDITION. " +
                "It used to land ~15s into session 1 among five other modal interruptions, " +
                "before the app had shown the user anything. Found: $condition",
            condition!!.contains("scanDone")
        )
        assertTrue(
            "and scanDone must actually be derived from FirstScanGate",
            review.contains("FirstScanGate.hasCompletedScan")
        )
    }
}
