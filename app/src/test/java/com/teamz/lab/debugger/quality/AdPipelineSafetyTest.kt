package com.teamz.lab.debugger.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pre-release ad-pipeline safety guards.
 *
 * Background — 2026-06-02 AdMob audit revealed:
 *   - 2,080 ad requests/day → 28 impressions/day (74:1 waste)
 *   - "Ad exposure / session: 0.00%" (render bug)
 *   - Test app-open ad unit had typo (3940256099942555 instead of ...544)
 *
 * These tests run as part of `./gradlew test` and fail the build if ANY of the
 * known regression patterns return. They are pure-source-text checks so they
 * run in <50ms and have no flake risk.
 *
 * Add a new assertion every time we discover a new ad-revenue regression mode.
 */
class AdPipelineSafetyTest {

    // ────────────────────────── File locators ──────────────────────────

    private fun locate(relPath: String): File {
        val root = System.getProperty("user.dir") ?: "."
        val candidates = listOf(
            File(root, relPath),
            File(root, "app/$relPath"),
            File(File(root).parentFile, "app/$relPath"),
            File("../app/$relPath"),
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Could not locate $relPath. Tried: ${candidates.joinToString { it.absolutePath }}")
    }

    private val adConfigSrc by lazy { locate("src/main/java/com/teamz/lab/debugger/utils/AdConfig.kt").readText() }

/**
 * Inlines `const val NAME = <literal>` back into any `"key" to NAME` entry before the regexes
 * below run. The defaults map now references named constants so the map and the accessor
 * fallback cannot drift (they had: bundled 60000/7 against fallbacks of 10000/20). These guards
 * check the VALUE, so they resolve the constant rather than requiring a literal in the map.
 */
private fun inlineConsts(text: String): String {
    val consts = Regex("""const val ([A-Z_][A-Z0-9_]*) = (\d[\d_]*)L?""")
        .findAll(text).associate { it.groupValues[1] to it.groupValues[2].replace("_", "") }
    var out = text
    for ((name, value) in consts) {
        out = out.replace(Regex("""\bto $name(\.toLong\(\))?"""), "to ${value}L")
    }
    return out
}

    private val remoteConfigSrc by lazy { inlineConsts(locate("src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt").readText()) }
    private val nativeAdsSrc by lazy { locate("src/main/java/com/teamz/lab/debugger/ui/admob_native_ads.kt").readText() }
    private val manifestSrc by lazy { locate("src/main/AndroidManifest.xml").readText() }

    // ────────────────────── Google official test ad units ──────────────────────
    // From https://developers.google.com/admob/android/test-ads — verified 2026-06-03.

    @Test
    fun `debug app-open uses Google's official test ad unit`() {
        // Regression: 2026-06-03 found app-open test ID had typo (...555/) producing
        // "Publisher data not found, code 3" for months in debug. Fixed to ...544/.
        val ok = adConfigSrc.contains("ca-app-pub-3940256099942544/9257395921")
        assertTrue(
            "Debug app-open test ad unit must be ca-app-pub-3940256099942544/9257395921 " +
                "(Google's published Android test unit). Any typo causes NO_FILL on every debug session.",
            ok
        )
    }

    @Test
    fun `debug interstitial uses Google's official test ad unit`() {
        assertTrue(
            "Debug interstitial test unit must be ca-app-pub-3940256099942544/1033173712",
            adConfigSrc.contains("ca-app-pub-3940256099942544/1033173712")
        )
    }

    @Test
    fun `debug native uses Google's official test ad unit`() {
        assertTrue(
            "Debug native test unit must be ca-app-pub-3940256099942544/2247696110",
            adConfigSrc.contains("ca-app-pub-3940256099942544/2247696110")
        )
    }

    @Test
    fun `debug rewarded uses Google's official test ad unit`() {
        assertTrue(
            "Debug rewarded test unit must be ca-app-pub-3940256099942544/5224354917",
            adConfigSrc.contains("ca-app-pub-3940256099942544/5224354917")
        )
    }

    @Test
    fun `no stray 3940256099942555 publisher anywhere`() {
        // Wrong-by-one-digit publisher prefix. Catches future regressions.
        assertFalse(
            "Found 3940256099942555 in AdConfig — this is NOT a Google test publisher and " +
                "will return NO_FILL. Correct test publisher is 3940256099942544.",
            adConfigSrc.contains("3940256099942555")
        )
    }

    // ────────────────────── Render-pipeline render guards ──────────────────────

    @Test
    fun `NativeAdView has DisposableEffect for view-side cleanup`() {
        // Regression: 2026-06-02 audit found NativeAdView was being garbage-collected
        // by Compose recomposition before user saw it. Fix added DisposableEffect that
        // destroys the adView (NOT the NativeAd) on dispose.
        assertTrue(
            "NativeAdView composable MUST use DisposableEffect to destroy the AdView on " +
                "dispose, otherwise Compose recomposition leaks the view and impression count drops to 0.",
            nativeAdsSrc.contains("DisposableEffect")
        )
        assertTrue(
            "DisposableEffect must call adView.destroy() (the View) but NOT ad.destroy() — " +
                "ad lifecycle is owned by NativeAdManager via removeFromPool/registerForDestroy.",
            nativeAdsSrc.contains("adViewRef.value?.destroy()")
        )
    }

    @Test
    fun `NativeAdView has idempotent update block with setTag`() {
        // Regression: 2026-06-02 audit found update{} called setNativeAd on every
        // recomposition, racing with the async factory. Fix: setTag-based hash check.
        assertTrue(
            "NativeAdView update{} must be idempotent via setTag(R.id.native_ad_bound_hash) " +
                "+ hashCode compare, otherwise every Compose recomposition triggers redundant " +
                "setNativeAd calls (the root cause of the 74:1 request:impression ratio).",
            nativeAdsSrc.contains("native_ad_bound_hash") && nativeAdsSrc.contains("boundHash == newHash")
        )
    }

    @Test
    fun `NativeAdManager has cacheGeneration MutableIntState for UI invalidation`() {
        // Regression: UI-side remember() caches held stale NativeAd refs after eviction.
        assertTrue(
            "NativeAdManager.cacheGeneration MUST exist as MutableIntState so callers can " +
                "subscribe via remember(cacheGen) and re-fetch after evictions. Without it, " +
                "callsites in LeaderboardSection/health_section/power_consumption_card render " +
                "destroyed ads after eviction.",
            nativeAdsSrc.contains("val cacheGeneration: MutableIntState")
        )
    }

    @Test
    fun `NativeAdManager has two-phase eviction (removeFromPool plus registerForDestroy)`() {
        // Regression: 2026-06-02 audit caught violation of AdMob SDK contract
        // (destroy ad BEFORE destroying view). Fix: split eviction into two phases.
        assertTrue(
            "Eviction must use removeFromPool() (no ad.destroy() inside) — saw single-phase eviction.",
            nativeAdsSrc.contains("fun removeFromPool(")
        )
        assertTrue(
            "Eviction must use registerForDestroy() that defers ad.destroy() until DisposableEffect runs.",
            nativeAdsSrc.contains("fun registerForDestroy(") && nativeAdsSrc.contains("pendingDestroy")
        )
    }

    @Test
    fun `NativeAdManager isExpired reads RemoteConfig TTL (not hardcoded const)`() {
        // Mediation networks have shorter TTL than AdMob direct (Unity Ads ~30min,
        // Mintegral ~40min). Hardcoded TTL eventually serves stale ads.
        assertTrue(
            "NativeAdManager.isExpired must call RemoteConfigUtils.getNativeAdTtlMs() — " +
                "hardcoded TTL violates mediation network contracts.",
            nativeAdsSrc.contains("RemoteConfigUtils.getNativeAdTtlMs()")
        )
    }

    // ────────────────────── Session + geo gates ──────────────────────

    @Test
    fun `app-open ad has session-gate function`() {
        assertTrue(
            "RemoteConfigUtils.shouldShowAppOpenAdsForSession(sessionCount) MUST exist — " +
                "sessions 1-2 are ad-free per Tier 1 retention plan to reduce 37% first-session uninstall.",
            remoteConfigSrc.contains("fun shouldShowAppOpenAdsForSession(")
        )
    }

    @Test
    fun `geo suppression check exists in all four show-star gates`() {
        // ALL four ad-type gates must check isCountrySuppressed(). Skipping even one
        // (e.g. banner) means BD/IR users still see no-fill ad slots in that slot.
        val shouldShowInterstitial = remoteConfigSrc
            .substringAfter("fun shouldShowInterstitialAds(")
            .substringBefore("fun ")
        val shouldShowBanner = remoteConfigSrc
            .substringAfter("fun shouldShowBannerAds(")
            .substringBefore("fun ")
        val shouldShowAppOpen = remoteConfigSrc
            .substringAfter("fun shouldShowAppOpenAds(")
            .substringBefore("fun ")
        val shouldShowNative = remoteConfigSrc
            .substringAfter("fun shouldShowNativeAds(")
            .substringBefore("fun ")

        assertTrue("shouldShowInterstitialAds missing isCountrySuppressed check", shouldShowInterstitial.contains("isCountrySuppressed"))
        assertTrue("shouldShowBannerAds missing isCountrySuppressed check", shouldShowBanner.contains("isCountrySuppressed"))
        assertTrue("shouldShowAppOpenAds missing isCountrySuppressed check", shouldShowAppOpen.contains("isCountrySuppressed"))
        assertTrue("shouldShowNativeAds missing isCountrySuppressed check", shouldShowNative.contains("isCountrySuppressed"))
    }

    @Test
    fun `country detection uses TelephonyManager not just Locale`() {
        // Regression: 2026-06-03 found Locale.getDefault().country returned empty
        // on user's BD device (system locale was en-US). Geo suppression silently
        // skipped because country code was blank. Fix: TelephonyManager first.
        assertTrue(
            "captureCountryCode must use TelephonyManager.networkCountryIso/simCountryIso " +
                "before falling back to Locale — BD/IR users frequently set en-US system " +
                "locale, which would defeat the geo suppression.",
            remoteConfigSrc.contains("networkCountryIso") && remoteConfigSrc.contains("simCountryIso")
        )
    }

    // ────────────────────── Manifest + receiver wiring ──────────────────────

    @Test
    fun `ChargeEventReceiver is registered in manifest`() {
        // The habit-loop ritual hook depends on this receiver firing even when app
        // is killed. Manifest registration (not runtime) is required.
        assertTrue(
            "ChargeEventReceiver must be declared in AndroidManifest.xml under <receiver>.",
            manifestSrc.contains("ChargeEventReceiver")
        )
        assertTrue(
            "Manifest receiver must filter ACTION_POWER_CONNECTED.",
            manifestSrc.contains("android.intent.action.ACTION_POWER_CONNECTED")
        )
        assertTrue(
            "Manifest receiver must filter ACTION_POWER_DISCONNECTED.",
            manifestSrc.contains("android.intent.action.ACTION_POWER_DISCONNECTED")
        )
    }

    @Test
    fun `POST_NOTIFICATIONS permission declared`() {
        // ChargeCycleTracker + SystemMonitorService both need this on Android 13+.
        assertTrue(
            "AndroidManifest.xml must declare POST_NOTIFICATIONS permission for habit triggers.",
            manifestSrc.contains("android.permission.POST_NOTIFICATIONS")
        )
    }

    // ────────────────────── ImprovedAdManager throttle parity ──────────────────────

    @Test
    fun `request interval long enough to avoid AdMob throttling`() {
        // Regression: Mid-April push set interval=10s. Burned the AdMob match rate
        // ceiling and caused the original 0.29% show rate. Floor at 60s.
        val match = Regex("\"native_ad_request_interval_ms\" to (\\d+)L").find(remoteConfigSrc)
        assertNotNull("native_ad_request_interval_ms entry missing", match)
        val ms = match!!.groupValues[1].toLong()
        assertTrue(
            "native_ad_request_interval_ms must be >= 60000ms (1 min) — was $ms. " +
                "Tighter throttles cause AdMob to downgrade your match rate.",
            ms >= 60000L
        )
    }

    @Test
    fun `request budget realistic against TTL`() {
        // Cross-cutting sanity: max_requests * (interval + TTL) shouldn't allow
        // burning the entire session budget in under 10 minutes.
        val budgetMatch = Regex("\"native_ad_max_requests_per_session\" to (\\d+)L").find(remoteConfigSrc)
        val intervalMatch = Regex("\"native_ad_request_interval_ms\" to (\\d+)L").find(remoteConfigSrc)
        assertNotNull(budgetMatch)
        assertNotNull(intervalMatch)
        val budget = budgetMatch!!.groupValues[1].toLong()
        val intervalMs = intervalMatch!!.groupValues[1].toLong()
        val minBudgetDurationMs = budget * intervalMs
        assertTrue(
            "Budget × interval must be >= 6 min to prevent session-start burn. " +
                "budget=$budget × interval=${intervalMs}ms = ${minBudgetDurationMs / 60_000}min.",
            minBudgetDurationMs >= 6 * 60 * 1000L
        )
    }

    @Test
    fun `clearing the ad pool does not refund the session request budget`() {
        // TRIPWIRE. NativeAdManager.clear() called resetStats(), which zeroed totalRequests.
        // clear() runs from MainActivity.onDestroy AND three places in RevenueCatPaywall, and
        // the paywall was shown 269 times in 14 days — so the "7 requests per session" cap was
        // really "7 per paywall view". AdMob, 7 days to 2026-09-23: 1,647 native requests,
        // 1,647 matched, 7 impressions. A 0.42% show rate on 100% fill is the
        // requested-but-never-shown pattern that put this publisher account at risk in July.
        //
        // Not a behavioural test: canMakeRequest() reads RemoteConfigUtils, which needs a live
        // Firebase instance, so the reset path cannot be exercised on the JVM. Stated plainly
        // rather than dressed up as one.
        val src = nativeAdsSrc
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        val clearBody = src.substringAfter("fun clear(").substringBefore("fun invalidateCache")
        assertFalse(
            "clear() must not call resetStats() — emptying the pool is memory management, " +
                "spending the request budget is a fact about the session. Use " +
                "resetCacheBookkeeping().",
            clearBody.contains("resetStats()"),
        )
        assertTrue(
            "clear() should reset cache bookkeeping",
            clearBody.contains("resetCacheBookkeeping()"),
        )
        // resetStats() keeps the budget reset, and belongs only to a real session boundary.
        val callers = Regex("NativeAdManager\\.resetStats\\(\\)").findAll(
            java.io.File("src/main/java/com/teamz/lab/debugger/Application.kt").readText()
        ).count()
        assertEquals("Application.onCreate is the only legitimate caller of resetStats()", 1, callers)
    }
}
