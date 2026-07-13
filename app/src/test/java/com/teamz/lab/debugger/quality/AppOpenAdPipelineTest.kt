package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.AppOpenAdManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the app-open ad pipeline against the two bugs that destroyed ~98% of ad
 * revenue and put the AdMob publisher account at risk.
 *
 * AdMob, 30 days to 2026-07-13:
 *     app_open   18,124 requests -> 15,607 FILLED -> 59 shown   (0.38% show rate)
 *     native      2,664 requests ->  2,620 FILLED -> 163 shown  (6.2%)
 *     interstitial  483 requests ->    459 FILLED -> 152 shown  (33%)
 *
 * Fill was never the problem (86-98% match rate). The app requested ads it then
 * threw away. Two independent causes:
 *
 *  (1) HEADLESS REQUESTS. Application.onCreate called loadAd(applicationContext)
 *      unconditionally. Android runs onCreate for any component wake (WorkManager,
 *      widgets, receivers) — no Activity, so the ad could never be shown. Real user
 *      sessions were ~573; requests were 18,124.
 *
 *  (2) THE AD ARRIVED AFTER THE SHOW ATTEMPT. onCreate preloaded with NO activity,
 *      so pendingActivityRef was never set. MainActivity.onStart then called
 *      showAdIfAvailable() while the ad was still downloading -> appOpenAd == null
 *      -> return. When the ad landed there was no pending activity and no retry, so
 *      it sat in cache until it expired. v3.1.11 (b971c61) had DELETED the
 *      load-with-activity path on the theory that "the next session's cold-start
 *      will preload one fresh" — but D1 retention is 7%, so for 93% of users there
 *      is no next session.
 */
class AppOpenAdPipelineTest {

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        return listOf(File(root, rel), File(root, "app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() && it.isFile }
            ?: error("Could not locate $rel")
    }

    private val managerSrc by lazy {
        locate("src/main/java/com/teamz/lab/debugger/utils/app_open_manager.kt").readText()
    }
    private val applicationSrc by lazy {
        locate("src/main/java/com/teamz/lab/debugger/Application.kt").readText()
    }

    // ---------------------------------------------------------------- cause (1)

    @Test
    fun `Application onCreate must NOT request an app-open ad`() {
        // onCreate runs in headless processes (WorkManager, widgets, receivers) where
        // no Activity exists and the ad can never be displayed. Requesting there is
        // both wasted money and the invalid-traffic pattern AdMob penalises — a risk
        // to the whole publisher account, not just this app.
        val onCreateStart = applicationSrc.indexOf("override fun onCreate()")
        assertTrue("Application.onCreate() not found", onCreateStart >= 0)
        val onCreateEnd = applicationSrc.indexOf("\n    override fun ", onCreateStart + 1)
            .let { if (it < 0) applicationSrc.length else it }
        val onCreateBody = applicationSrc.substring(onCreateStart, onCreateEnd)

        val requestsAd = Regex("AppOpenAdManager\\.loadAd\\s*\\(").containsMatchIn(onCreateBody)
        assertFalse(
            "Application.onCreate() must NOT call AppOpenAdManager.loadAd(). onCreate fires for " +
                "every headless process wake (WorkManager/widgets/receivers), which produced " +
                "18,124 requests against only ~573 real sessions and 59 impressions. Request the " +
                "app-open ad from MainActivity.onStart instead, where a screen actually exists.",
            requestsAd
        )
    }

    // ---------------------------------------------------------------- cause (2)

    @Test
    fun `showAdIfAvailable loads WITH the activity when no ad is cached`() {
        // Without the activity, pendingActivityRef is never set, so the ad cannot show
        // itself when it lands — and with 7% D1 retention there is no "next session"
        // to show it in. This is the line v3.1.11 deleted.
        val fnStart = managerSrc.indexOf("fun showAdIfAvailable(")
        assertTrue("showAdIfAvailable() not found", fnStart >= 0)
        val guardIdx = managerSrc.indexOf("if (appOpenAd == null)", fnStart)
        assertTrue("the 'no ad cached' guard is gone", guardIdx >= 0)
        val guardBlock = managerSrc.substring(guardIdx, minOf(guardIdx + 600, managerSrc.length))

        assertTrue(
            "When no ad is cached, showAdIfAvailable() must call loadAd(activity, activity, ...) " +
                "so pendingActivityRef is set and the ad auto-shows on arrival. Returning early " +
                "here (the v3.1.11 behaviour) means the ad is requested, filled, billed — and " +
                "never displayed.",
            Regex("loadAd\\s*\\(\\s*activity\\s*,\\s*activity").containsMatchIn(guardBlock)
        )
    }

    @Test
    fun `loadAd records that a launch is waiting when given an activity`() {
        assertTrue(
            "loadAd() must stamp pendingShowRequestedAtMs when an activity is passed — the " +
                "onSuccess handler needs it to decide whether the ad arrived in time to show.",
            Regex("pendingShowRequestedAtMs\\s*=\\s*System\\.currentTimeMillis\\(\\)")
                .containsMatchIn(managerSrc)
        )
    }

    @Test
    fun `onSuccess gates the auto-show through shouldAutoShowOnLoad`() {
        assertTrue(
            "The load-success handler must consult shouldAutoShowOnLoad() rather than showing " +
                "unconditionally, so a slow ad cannot slam a fullscreen interstitial over a user " +
                "who is already using the app.",
            managerSrc.contains("shouldAutoShowOnLoad(pendingShowRequestedAtMs")
        )
    }

    // ------------------------------------------------- the pure decision function

    @Test
    fun `auto-show when the ad arrives while the user is still waiting`() {
        // Typical cold start: ad lands ~1.5s after launch. Show it.
        assertTrue(AppOpenAdManager.shouldAutoShowOnLoad(requestedAtMs = 1_000L, loadedAtMs = 2_500L))
    }

    @Test
    fun `auto-show right at the window boundary`() {
        assertTrue(
            AppOpenAdManager.shouldAutoShowOnLoad(
                requestedAtMs = 1_000L, loadedAtMs = 7_000L, windowMs = 6_000L
            )
        )
    }

    @Test
    fun `do NOT auto-show when the ad arrives too late`() {
        // User has been reading the screen for 10s — a fullscreen ad now is hostile.
        // The ad stays cached and shows on the next launch instead.
        assertFalse(
            AppOpenAdManager.shouldAutoShowOnLoad(
                requestedAtMs = 1_000L, loadedAtMs = 11_000L, windowMs = 6_000L
            )
        )
    }

    @Test
    fun `do NOT auto-show when nobody is waiting (headless preload)`() {
        // requestedAtMs == 0 means no Activity asked for this ad. This is the guard
        // that keeps a background-process load from ever trying to present UI.
        assertFalse(AppOpenAdManager.shouldAutoShowOnLoad(requestedAtMs = 0L, loadedAtMs = 5_000L))
    }

    @Test
    fun `do NOT auto-show when the clock goes backwards`() {
        assertFalse(AppOpenAdManager.shouldAutoShowOnLoad(requestedAtMs = 9_000L, loadedAtMs = 1_000L))
    }

    // --------------------------------------------------------- request-side guards

    @Test
    fun `request-side throttles survive (they bound the blast radius)`() {
        // Restoring the show path must not re-open the request firehose. These caps are
        // what keep worst-case request volume bounded if a future path adds a trigger.
        assertTrue(
            "MIN_LOAD_INTERVAL_MS throttle must remain — it drops load fan-out inside one " +
                "foreground burst.",
            managerSrc.contains("MIN_LOAD_INTERVAL_MS")
        )
        assertTrue(
            "The per-session load cap must remain (getAppOpenMaxLoadsPerSession).",
            managerSrc.contains("getAppOpenMaxLoadsPerSession")
        )
    }
}
