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
    fun `the first session never asks for a review`() {
        // This guard used to pin `if (ENABLE_FIRST_LAUNCH_REVIEW && scanDone ...)` — it
        // required the session-1 ask to WAIT for the scan. The ask has since been removed
        // from session 1 entirely, which satisfies that intent more strongly, so the guard
        // now pins the stronger contract instead of the weaker one it was written against.
        val firstLaunchBranch = review
            .substringAfter("if (isFirstLaunch) {")
            .substringBefore("\n        }")
        assertTrue(
            "the isFirstLaunch branch was not found; this guard would pass vacuously",
            firstLaunchBranch.isNotBlank() && firstLaunchBranch.contains("KEY_FIRST_LAUNCH_DATE")
        )
        assertTrue(
            "the first session must not reach showReviewPrompt(). It used to land ~15s into " +
                "session 1 among five other modal interruptions, before the app had shown the " +
                "user anything. Found: $firstLaunchBranch",
            !firstLaunchBranch.contains("showReviewPrompt(")
        )
    }

    @Test
    fun `the install-age gate is a comparison, not a coroutine that has to stay alive`() {
        // review_delay_first_launch_ms was raised 15000 -> 86400000 to push the ask out by a
        // day. It was being passed to delay() on a CoroutineScope(Dispatchers.Main) tied to
        // nothing, so with a 155-second average session the prompt could never fire at all:
        // the intent was to postpone the ask, the effect was to delete it. It must stay a
        // comparison against a stored timestamp, which process death cannot erase.
        assertTrue(
            "getReviewDelayFirstLaunchMs() is being slept on again instead of compared",
            !Regex("""delay\(\s*\w*[Ff]irstLaunch\w*\s*\)""").containsMatchIn(review)
        )
        assertTrue(
            "the install-age gate no longer reads the persisted first-launch timestamp",
            review.contains("KEY_FIRST_LAUNCH_DATE") && review.contains("minInstallAgeMs")
        )
    }

    @Test
    fun `the review decision stays a pure function the tests can drive`() {
        assertTrue(
            "shouldShowReviewPrompt() stopped delegating to reviewGate(). The Context-bound " +
                "version is what let an unreachable prompt survive: nothing could assert that " +
                "an ask was ever actually possible.",
            review.contains("reviewGate(") && review.contains("ReviewGate.SHOW")
        )
    }
}
