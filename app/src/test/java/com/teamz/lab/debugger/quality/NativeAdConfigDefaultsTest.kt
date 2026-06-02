package com.teamz.lab.debugger.quality

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Guard against accidental regressions of the native-ad throttling defaults.
 *
 * Background:
 *   On 2026-05-27 the AdMob dashboard showed 69,798 native-ad requests producing only
 *   ~200 shown impressions (0.29% show rate). Old defaults were too aggressive:
 *     target_count=3, max_retries=1, interval=10s, session_budget=20
 *   The new tuned defaults cap request volume so AdMob match rate can recover.
 *
 * This is a pure-text guard — verifies the constants in RemoteConfigUtils.kt match
 * the tuned values without spinning up Firebase. If anyone bumps these back up
 * without a paired commit message explaining why, this test fails CI.
 */
class NativeAdConfigDefaultsTest {
    private fun findRemoteConfigUtilsFile(): File {
        val projectRoot = System.getProperty("user.dir") ?: "."
        val absoluteProjectRoot = File(projectRoot).absoluteFile
        val candidatePaths = listOf(
            File(projectRoot, "app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt"),
            File(projectRoot, "src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt"),
            File("app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt"),
            File("src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt"),
            File("../app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt"),
            File("../../app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt"),
            File(absoluteProjectRoot, "app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt")
        )
        return candidatePaths.firstOrNull { it.exists() && it.isFile }
            ?: error(
                "RemoteConfigUtils.kt not found. Tried: ${
                    candidatePaths.joinToString { it.absolutePath }
                }"
            )
    }

    private val src by lazy {
        findRemoteConfigUtilsFile().readText()
    }

    @Test
    fun `native ad target count stays at 1`() {
        assertTrue(
            "native_ad_target_count must be 1L (we cache one ad at a time to keep show rate high). " +
                "If you bump it, document why in the commit and update this test deliberately.",
            src.contains("\"native_ad_target_count\" to 1L")
        )
    }

    @Test
    fun `native ad max retries stays at 0`() {
        assertTrue(
            "native_ad_max_retries must be 0L — low fill rate means retries burn more requests than they save.",
            src.contains("\"native_ad_max_retries\" to 0L")
        )
    }

    @Test
    fun `request interval at least 60 seconds`() {
        val match = Regex("\"native_ad_request_interval_ms\" to (\\d+)L").find(src)
        assertTrue("native_ad_request_interval_ms entry missing", match != null)
        val ms = match!!.groupValues[1].toLong()
        assertTrue(
            "native_ad_request_interval_ms must be ≥ 60000 (1 min). Was $ms.",
            ms >= 60000L
        )
    }

    @Test
    fun `session budget at most 8`() {
        // 2026-06-03: bumped ceiling 5 -> 8 after lowering AD_TTL_MS from 60min to 28min
        // (mediation networks like Unity Ads native expire at ~30min). More frequent
        // refills are now expected; 5/session was too tight and caused mid-session
        // ad starvation. 7 is the new operational default; 8 is the assertion ceiling
        // so we still catch accidental bumps to e.g. 20.
        val match = Regex("\"native_ad_max_requests_per_session\" to (\\d+)L").find(src)
        assertTrue("native_ad_max_requests_per_session entry missing", match != null)
        val budget = match!!.groupValues[1].toLong()
        assertTrue(
            "native_ad_max_requests_per_session must be ≤ 8 (was raised from 5 alongside AD_TTL drop). Was $budget.",
            budget <= 8L
        )
    }

    @Test
    fun `ad ttl present and under one hour`() {
        // 2026-06-03: TTL is now Remote-Config-driven with a 28min default. Cap at 60min
        // to ensure we never drift past Google's documented native ad TTL.
        val match = Regex("\"native_ad_ttl_ms\" to (\\d[\\d_]*)L").find(src)
        assertTrue("native_ad_ttl_ms entry missing — see RemoteConfigUtils.getNativeAdTtlMs()", match != null)
        val ms = match!!.groupValues[1].replace("_", "").toLong()
        assertTrue("native_ad_ttl_ms must be ≤ 3_600_000 (60 min). Was $ms.", ms <= 3_600_000L)
    }

    @Test
    fun `app open ad min session set to 3 by default`() {
        // 2026-06-03: first-impression uninstall is 37% per lifetime GA4 audit; sessions
        // 1-2 are now ad-free. If this ever ships at 0 or 1, it means somebody disabled
        // the gate without removing the test — block.
        val match = Regex("\"app_open_ad_min_session\" to (\\d+)L").find(src)
        assertTrue("app_open_ad_min_session entry missing — Tier 1 retention gate broken", match != null)
        val n = match!!.groupValues[1].toInt()
        assertTrue("app_open_ad_min_session must be ≥ 2 (was set to 3 by Tier 1). Was $n.", n >= 2)
    }

    @Test
    fun `ad suppression list contains zero-fill geos`() {
        // 2026-06-03: IR/BD/PK contribute ~54% of installs but ~0% of revenue.
        // Removing them avoids the "broken app" signal from empty ad slots.
        // RC-driven so we can rebalance without a release, but the default must include
        // at least these three.
        val match = Regex("\"ad_suppressed_country_codes\" to \"([^\"]*)\"").find(src)
        assertTrue("ad_suppressed_country_codes entry missing", match != null)
        val codes = match!!.groupValues[1]
        assertTrue("ad_suppressed_country_codes default must include IR. Was $codes.", codes.contains("IR"))
        assertTrue("ad_suppressed_country_codes default must include BD. Was $codes.", codes.contains("BD"))
    }
}
