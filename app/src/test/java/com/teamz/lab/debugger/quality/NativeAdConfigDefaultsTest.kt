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
    fun `session budget at most 5`() {
        val match = Regex("\"native_ad_max_requests_per_session\" to (\\d+)L").find(src)
        assertTrue("native_ad_max_requests_per_session entry missing", match != null)
        val budget = match!!.groupValues[1].toLong()
        assertTrue(
            "native_ad_max_requests_per_session must be ≤ 5. Was $budget.",
            budget <= 5L
        )
    }
}
