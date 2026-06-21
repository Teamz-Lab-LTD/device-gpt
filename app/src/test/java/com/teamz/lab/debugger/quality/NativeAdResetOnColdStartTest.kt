package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.1.11 W1 ad-pipeline fix — native ad counter reset regression guard.
 *
 * NativeAdManager is a Kotlin object (process singleton) so its totalRequests,
 * successfulLoads, failedLoads, retryAttempts counters and positionUsageMap
 * survive across user sessions. Without an explicit cold-start reset, the
 * per-session budget cap (max_requests_per_session) is meaningless — a user
 * with a long-running process burns the cap once and never recovers.
 *
 * Application.onCreate must call NativeAdManager.resetStats() AND
 * AppOpenAdManager.resetSessionCounters() before MobileAds.initialize, so the
 * very first ad request after cold-start starts from a clean budget.
 *
 * Without these resets, the converged 2026-06-21 fix is incomplete: even with
 * loadAd() throttled, a leaked counter from the prior session could short-
 * circuit any new attempt.
 */
class NativeAdResetOnColdStartTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private val appSrc: String by lazy {
        File(projectRoot(), "app/src/main/java/com/teamz/lab/debugger/Application.kt").readText()
    }

    @Test
    fun `Application onCreate calls NativeAdManager resetStats`() {
        assertTrue(
            "Application.onCreate must call NativeAdManager.resetStats() so the per-session " +
                "budget cap is honest. Without this, totalRequests leaks across user sessions " +
                "and a long-running process never gets fresh budget.",
            appSrc.contains("NativeAdManager.resetStats()")
        )
    }

    @Test
    fun `Both ad manager resets run BEFORE MobileAds initialize`() {
        val nativeResetIdx = appSrc.indexOf("NativeAdManager.resetStats()")
        val appOpenResetIdx = appSrc.indexOf("AppOpenAdManager.resetSessionCounters()")
        val mobileAdsInitIdx = appSrc.indexOf("MobileAds.initialize(")
        assertTrue("NativeAdManager.resetStats() must exist", nativeResetIdx > 0)
        assertTrue("AppOpenAdManager.resetSessionCounters() must exist", appOpenResetIdx > 0)
        assertTrue("MobileAds.initialize() must exist", mobileAdsInitIdx > 0)
        assertTrue(
            "AppOpenAdManager.resetSessionCounters() must run BEFORE MobileAds.initialize() so " +
                "the cold-start preload sees a clean counter.",
            appOpenResetIdx < mobileAdsInitIdx
        )
        assertTrue(
            "NativeAdManager.resetStats() must run BEFORE MobileAds.initialize() so the first " +
                "native ad request after init sees a clean budget.",
            nativeResetIdx < mobileAdsInitIdx
        )
    }

    @Test
    fun `NativeAdManager resetStats clears totalRequests counter`() {
        val nativeSrc = File(projectRoot(),
            "app/src/main/java/com/teamz/lab/debugger/ui/admob_native_ads.kt").readText()
        val resetIdx = nativeSrc.indexOf("fun resetStats()")
        assertTrue("resetStats() must be declared in NativeAdManager", resetIdx > 0)
        val nextFun = nativeSrc.indexOf("\n    fun ", resetIdx + 1)
        val body = nativeSrc.substring(resetIdx, if (nextFun > 0) nextFun else resetIdx + 600)
        assertTrue(
            "resetStats() must zero totalRequests — the counter checked by canMakeRequest() against the per-session cap.",
            body.contains("totalRequests = 0")
        )
        assertTrue(
            "resetStats() must zero successfulLoads + failedLoads for consistent telemetry.",
            body.contains("successfulLoads = 0") && body.contains("failedLoads = 0")
        )
        assertTrue(
            "resetStats() must clear positionUsageMap to release the leaked process-wide state.",
            body.contains("positionUsageMap.clear()")
        )
    }
}
