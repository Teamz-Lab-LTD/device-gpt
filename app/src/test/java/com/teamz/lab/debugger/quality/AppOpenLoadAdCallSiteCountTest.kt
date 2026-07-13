package com.teamz.lab.debugger.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * App-open ad request/show pipeline — source-text regression guard.
 *
 * ## History, including the mistake this file used to enforce
 *
 * The 2026-06-21 audit found five call sites invoking `AppOpenAdManager.loadAd()` with
 * no request-side throttle: **5148 AdMob requests/week for 18 displays (0.43% show
 * rate)**. v3.1.11 (b971c61) "fixed" this by deleting call sites — including the
 * `null-check-during-show -> load with activity` path, which was the ONLY thing that
 * let a freshly-loaded ad actually be shown.
 *
 * That was the wrong side of the ratio. The audit had already written down a 0.43%
 * show rate; the fix attacked the numerator (requests) and left the denominator
 * collapse untouched. **This test file then pinned the broken behaviour in place** by
 * asserting `Application.onCreate` must preload and that the show-path load must stay
 * deleted — making the revenue bug un-fixable without failing CI.
 *
 * AdMob, 30 days to 2026-07-13, proves the outcome:
 *
 *     app_open      18,124 requests -> 15,607 FILLED -> 59 shown   (0.38%)
 *     native         2,664 requests ->  2,620 FILLED -> 163 shown  (6.2%)
 *     interstitial     483 requests ->    459 FILLED -> 152 shown  (33%)
 *
 * Fill was never the problem (86-98% match rate). ~18,600 ads were filled and 374 were
 * shown. Requests did not even drop as intended (~4.2k/wk) because the true source was
 * never addressed: `Application.onCreate` runs for EVERY headless process wake
 * (WorkManager, widgets, receivers), where no Activity exists and an ad can never be
 * displayed. Real user sessions numbered ~573 against 18,124 requests.
 *
 * ## The invariants that actually matter (what this file now guards)
 *
 *  1. **No headless requests.** `Application.onCreate` must NOT call `loadAd()`. This is
 *     both the revenue leak and the AdMob invalid-traffic risk (a high request count with
 *     near-zero impressions endangers the whole publisher account, not just this app).
 *  2. **The show path exists.** When no ad is cached, `showAdIfAvailable()` must load
 *     WITH the Activity, so the ad can present itself on arrival. Without this, a filled
 *     ad is simply discarded — and with 7% D1 retention there is no "next session" to
 *     show it in.
 *  3. **Request volume stays bounded.** The throttles that made the v3.1.11 audit
 *     worthwhile must survive: no preload from branches where the ad cannot be shown
 *     anyway (cooldown / short-background), plus the interval + per-session caps.
 *
 * Behavioural coverage of the auto-show decision lives in [AppOpenAdPipelineTest].
 */
class AppOpenLoadAdCallSiteCountTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private fun read(path: String): String {
        val f = File(projectRoot(), path)
        if (!f.exists()) error("file not found: ${f.absolutePath}")
        return f.readText()
    }

    private fun countOccurrences(src: String, needle: Regex): Int = needle.findAll(src).count()

    private val managerPath = "app/src/main/java/com/teamz/lab/debugger/utils/app_open_manager.kt"
    private val applicationPath = "app/src/main/java/com/teamz/lab/debugger/Application.kt"

    // ------------------------------------------------ invariant 1: no headless requests

    @Test
    fun `no production code outside the manager may request an app-open ad`() {
        // Every request must originate from a real Activity via showAdIfAvailable().
        // Application.onCreate is explicitly included in this ban: it runs for headless
        // process wakes (WorkManager/widgets/receivers) with no Activity, and an ad
        // requested there can never be shown. That produced 18,124 requests / 59 shows.
        val externalCallRegex = Regex("""\bAppOpenAdManager\.loadAd\s*\(""")
        val violators = mutableListOf<String>()
        File(projectRoot(), "app/src/main/java").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .filter { !it.absolutePath.endsWith("app_open_manager.kt") }
            .forEach { f ->
                if (externalCallRegex.containsMatchIn(f.readText())) {
                    violators.add(f.absolutePath.substringAfter("app/src/main/java/"))
                }
            }
        assertTrue(
            "No production file may call AppOpenAdManager.loadAd() directly — requests must come " +
                "from a real Activity through showAdIfAvailable(). Application.onCreate especially: " +
                "it fires on every headless process wake, and an ad requested with no Activity can " +
                "never be displayed (18,124 requests -> 59 impressions). Violators: $violators",
            violators.isEmpty()
        )
    }

    @Test
    fun `Application onCreate still resets the per-session load cap`() {
        // The Application object is a process singleton, so the per-session counters would
        // otherwise leak across user sessions. Still required even though onCreate no
        // longer requests an ad itself.
        val src = read(applicationPath)
        assertTrue(
            "Application.onCreate must call AppOpenAdManager.resetSessionCounters() so the " +
                "per-session load cap starts honest on each cold start.",
            src.contains("AppOpenAdManager.resetSessionCounters()")
        )
    }

    // -------------------------------------------------- invariant 2: the show path lives

    @Test
    fun `showAdIfAvailable loads WITH the activity when nothing is cached`() {
        val src = read(managerPath)
        val guardIdx = src.indexOf("if (appOpenAd == null)")
        assertTrue("the 'no ad cached' branch is missing entirely", guardIdx >= 0)
        val branch = src.substring(guardIdx, minOf(guardIdx + 600, src.length))
        assertTrue(
            "When no ad is cached, showAdIfAvailable() MUST call loadAd(activity, activity, ...). " +
                "Returning early here is the v3.1.11 bug: the ad is requested, filled and billed to " +
                "AdMob's auction, then discarded because nothing can show it. Do not 'optimise' this " +
                "line away to reduce request volume — bound requests with the throttles instead.",
            Regex("""loadAd\s*\(\s*activity\s*,\s*activity""").containsMatchIn(branch)
        )
    }

    @Test
    fun `post-dismissal self-reload is preserved`() {
        // Fires only AFTER an ad was actually shown, so the next launch has one cached.
        val src = read(managerPath)
        val dismissedIdx = src.indexOf("override fun onAdDismissedFullScreenContent()")
        assertTrue("onAdDismissedFullScreenContent must exist", dismissedIdx > 0)
        val nextOverride = src.indexOf("override fun", dismissedIdx + 1)
        val body = src.substring(dismissedIdx, if (nextOverride > 0) nextOverride else dismissedIdx + 1000)
        assertTrue(
            "Post-dismissal callback must still call loadAd(activity) — the legitimate preload " +
                "that arms the next launch after a successful show.",
            body.contains("loadAd(activity)")
        )
    }

    // ------------------------------------------- invariant 3: request volume stays bounded

    @Test
    fun `no preload from branches where the ad could not be shown anyway`() {
        // These two deletions from the 2026-06-21 audit were correct and must stand: both
        // fired loadAd() inside guards that had ALREADY decided not to show an ad, so the
        // cached result could never be used within the window. Restoring them re-opens the
        // request fan-out without buying a single impression.
        val src = read(managerPath)
        listOf(
            "DELETED redundant preload here",         // cooldown branch
            "DELETED redundant preload here for the", // short-background branch
        ).forEach { marker ->
            assertTrue(
                "Deletion-landmark comment missing: '$marker'. The comment is the only record of " +
                    "why that loadAd() call is gone — if you removed it, verify the call was not " +
                    "re-added.",
                src.contains(marker)
            )
        }
    }

    @Test
    fun `request-side throttles survive the show-path restoration`() {
        val src = read(managerPath)
        assertTrue(
            "MIN_LOAD_INTERVAL_MS must remain — hard floor between loadAd() invocations.",
            src.contains("MIN_LOAD_INTERVAL_MS")
        )
        assertTrue(
            "The per-session load cap must remain (getAppOpenMaxLoadsPerSession).",
            src.contains("getAppOpenMaxLoadsPerSession")
        )
    }

    @Test
    fun `internal loadAd self-calls are exactly the two legitimate activity-scoped paths`() {
        // 1. showAdIfAvailable  -> loadAd(activity, activity, isColdStart)  [show on arrival]
        // 2. onAdDismissed      -> loadAd(activity)                         [arm next launch]
        // Both require an Activity, so neither can fire from a headless process.
        val src = read(managerPath)
        val selfCallRegex = Regex("""(?<!fun )\bloadAd\s*\(\s*activity""")
        val n = countOccurrences(src, selfCallRegex)
        assertEquals(
            "Expected exactly 2 activity-scoped internal loadAd() calls (show-on-arrival + " +
                "post-dismissal rearm). Found $n. More than 2 means a new trigger path appeared — " +
                "check it cannot fire without an Activity.",
            2, n
        )
    }
}
