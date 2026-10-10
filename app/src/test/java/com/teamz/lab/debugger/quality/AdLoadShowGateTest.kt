package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.AdShowGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A full-screen ad must be requested only in a session where it is allowed to be shown.
 *
 * 2026-10-10 trace (AdMob 30 d, split by APP_VERSION_NAME, DeviceGPT 3.x builds only):
 *   interstitial 124 matched -> 32 shown, app_open 70 matched -> 5 shown, native 89 -> 59.
 * The show paths honoured the session gates (RC ads_grace_sessions = 10 and
 * app_open_ad_min_session = 10 since 2026-10-07), the load paths did not:
 *   - Application.onStart loaded an interstitial on every foreground, grace or not;
 *   - AppOpenAdManager.showAdIfAvailable loaded an app-open ad before it checked the session.
 * Each of those is a filled request that can never be shown. The gate itself is unchanged:
 * same Remote Config keys, same numbers, applied one step earlier.
 */
class AdLoadShowGateTest {

    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app/src/main/java/com/teamz/lab/debugger/$rel").readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    }

    private fun body(source: String, fn: String) = source.substringAfter(fn).substringBefore("\n    fun ")

    @Test
    fun `interstitial grace covers sessions 1 to N and nothing else`() {
        assertTrue(AdShowGate.interstitialInGrace(sessionCount = 1, graceSessions = 10))
        assertTrue(AdShowGate.interstitialInGrace(sessionCount = 10, graceSessions = 10))
        assertFalse(AdShowGate.interstitialInGrace(sessionCount = 11, graceSessions = 10))
        assertFalse("grace 0 means no grace", AdShowGate.interstitialInGrace(sessionCount = 1, graceSessions = 0))
        assertFalse(
            "session 0 (never tracked) was never in grace on the show path; the load path must agree",
            AdShowGate.interstitialInGrace(sessionCount = 0, graceSessions = 10)
        )
    }

    @Test
    fun `app-open gate blocks sessions below the minimum`() {
        assertTrue(AdShowGate.appOpenBelowMinSession(sessionCount = 9, minSession = 10))
        assertFalse(AdShowGate.appOpenBelowMinSession(sessionCount = 10, minSession = 10))
        assertFalse(AdShowGate.appOpenBelowMinSession(sessionCount = 1, minSession = 1))
    }

    @Test
    fun `the interstitial load path checks the grace window before requesting`() {
        val load = body(src("utils/interstitial_ad_manager.kt"), "fun loadAd(")
        val gate = load.indexOf("AdShowGate.interstitialInGrace(")
        val request = load.indexOf("loadInterstitialAdWithRetry(")
        assertTrue("loadAd must consult AdShowGate.interstitialInGrace", gate >= 0)
        assertTrue("loadAd must still request outside grace", request >= 0)
        assertTrue("the grace check must come before the request, or it saves nothing", gate < request)
        assertTrue(
            "the grace check must read the same Remote Config key as the show path",
            load.contains("getAdsGraceSessions()")
        )
    }

    @Test
    fun `both interstitial show paths use the same grace predicate as the load path`() {
        val mgr = src("utils/interstitial_ad_manager.kt")
        for (fn in listOf("fun showAdBeforeAction", "fun showAdIfAvailable")) {
            assertTrue("$fn must use AdShowGate.interstitialInGrace", body(mgr, fn).contains("AdShowGate.interstitialInGrace("))
        }
    }

    @Test
    fun `app-open checks the session gate before it loads for a waiting launch`() {
        val show = body(src("utils/app_open_manager.kt"), "fun showAdIfAvailable(")
        val gate = show.indexOf("shouldShowAppOpenAdsForSession(")
        val load = show.indexOf("loadAd(activity, activity")
        assertTrue("expected both the session gate and the launch load in showAdIfAvailable", gate >= 0 && load >= 0)
        assertTrue("the session gate must precede the load, or a gated session still buys an ad", gate < load)
    }

    @Test
    fun `native pool fallback matches the bundled default, not the old 3`() {
        val rc = src("utils/RemoteConfigUtils.kt")
        val fn = body(rc, "fun getNativeAdTargetCount()")
        assertFalse("the pre-defaults fallback must not be 3 — that is the pool the 2026-05-27 tune removed", fn.contains("3 else"))
        assertTrue(fn.contains("DEFAULT_NATIVE_AD_TARGET_COUNT"))
        // The bundled map keeps its literal (NativeAdConfigDefaultsTest pins it); both must say 1.
        assertTrue(rc.contains("\"native_ad_target_count\" to 1L"))
        assertTrue(Regex("DEFAULT_NATIVE_AD_TARGET_COUNT\\s*=\\s*1L").containsMatchIn(rc))
    }

    @Test
    fun `loaded, shown and skipped ad events exist with the exact GA4 names`() {
        val analytics = src("utils/analytics_utils.kt")
        val names = listOf(
            "ad_loaded_native", "ad_loaded_interstitial", "ad_loaded_app_open",
            "ad_shown_native", "ad_shown_interstitial", "ad_shown_app_open",
            "ad_show_skipped_grace_session", "ad_show_skipped_cooldown",
            "ad_show_skipped_not_loaded", "ad_show_skipped_disabled",
            "ad_load_skipped_grace_session"
        )
        for (n in names) assertTrue("missing event \"$n\"", analytics.contains("(\"$n\")"))
        // GA4: event names <= 40 chars.
        for (n in names) assertTrue("$n is longer than GA4's 40-char limit", n.length <= 40)
    }

    @Test
    fun `each format logs loaded and shown at the SDK callbacks`() {
        val native = src("ui/expandable_info_list.kt")
        assertTrue(native.contains("AnalyticsEvent.AdLoadedNative"))
        assertTrue("native shown = the SDK's onAdImpression", body(native, "fun rememberAdLoader(").let {
            it.substringAfter("override fun onAdImpression()").substringBefore("override fun").contains("AnalyticsEvent.AdShownNative")
        })
        val improved = src("utils/improved_ad_manager.kt")
        assertTrue(improved.contains("AnalyticsEvent.AdLoadedInterstitial"))
        assertTrue(improved.contains("AnalyticsEvent.AdLoadedAppOpen"))
        val inter = src("utils/interstitial_ad_manager.kt")
        assertTrue(
            "both interstitial show paths must log ad_shown_interstitial",
            Regex("AnalyticsEvent\\.AdShownInterstitial").findAll(inter).count() >= 2
        )
        assertTrue(src("utils/app_open_manager.kt").contains("AnalyticsEvent.AdShownAppOpen"))
    }
}
