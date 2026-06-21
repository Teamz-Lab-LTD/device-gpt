package com.teamz.lab.debugger.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.1.11 W1 ad-pipeline fix — source-text contract guards for the AppOpen throttle.
 *
 * Source-text tests pin the SHAPE of the throttle: the gate must be at the top of
 * loadAd(), it must consult MIN_LOAD_INTERVAL_MS + a RC session cap, and the
 * counter increment must come AFTER the gate check (otherwise the gate would
 * record itself on the very first failed attempt).
 *
 * Robolectric runtime behavior (counter ticks, gate blocks, reset works) is
 * covered by [AppOpenThrottleRuntimeTest]. This file is the fast text guard.
 */
class AppOpenThrottleContractTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private val src: String by lazy {
        File(projectRoot(), "app/src/main/java/com/teamz/lab/debugger/utils/app_open_manager.kt").readText()
    }

    @Test
    fun `MIN_LOAD_INTERVAL_MS constant is declared and is 60 seconds`() {
        assertTrue(
            "MIN_LOAD_INTERVAL_MS must be declared — without it the request-side throttle " +
                "has no floor and a single foreground burst can fire multiple requests.",
            src.contains("private const val MIN_LOAD_INTERVAL_MS = 60_000L")
        )
    }

    @Test
    fun `Volatile load-attempt counters declared at object scope`() {
        // @Volatile is required because resetSessionCounters() can race with loadAd()
        // on different threads (Application init thread vs Main thread). Without it
        // the cap could be silently bypassed by reordered reads.
        assertTrue(
            "lastLoadAttemptMs must be @Volatile — racy reset/load on different threads.",
            src.contains("@Volatile private var lastLoadAttemptMs: Long = 0L")
        )
        assertTrue(
            "loadAttemptsThisSession must be @Volatile — same race concern as lastLoadAttemptMs.",
            src.contains("@Volatile private var loadAttemptsThisSession: Int = 0")
        )
    }

    @Test
    fun `resetSessionCounters function exists and zeros both counters`() {
        val resetIdx = src.indexOf("fun resetSessionCounters()")
        assertTrue(
            "resetSessionCounters() must exist so Application.onCreate can honestly start a new session.",
            resetIdx > 0
        )
        val nextFun = src.indexOf("\n    fun ", resetIdx + 1)
        val body = src.substring(resetIdx, if (nextFun > 0) nextFun else resetIdx + 500)
        assertTrue(
            "resetSessionCounters must zero loadAttemptsThisSession.",
            body.contains("loadAttemptsThisSession = 0")
        )
        assertTrue(
            "resetSessionCounters must zero lastLoadAttemptMs so the first new-session load isn't blocked by a stale interval.",
            body.contains("lastLoadAttemptMs = 0L")
        )
    }

    @Test
    fun `loadAd gate runs BEFORE isLoading=true and BEFORE the counter increment`() {
        // The gate ordering is critical:
        //   1. Existing fast-path: isLoading || appOpenAd != null (early-return)
        //   2. NEW throttle gate: sinceLast < MIN || attempts >= cap (early-return)
        //   3. Record this attempt (lastLoadAttemptMs = now; attempts++)
        //   4. isLoading = true
        //   5. Issue AdMob request
        // If counter increment happened BEFORE the gate, every blocked request would
        // still increment, exhausting the cap from blocked requests alone.
        val loadAdStart = src.indexOf("fun loadAd(context: Context, activity: Activity? = null) {")
        val gateIdx = src.indexOf("val sinceLast = now - lastLoadAttemptMs", loadAdStart)
        val recordIdx = src.indexOf("lastLoadAttemptMs = now", loadAdStart)
        val isLoadingIdx = src.indexOf("isLoading = true", loadAdStart)
        assertTrue("gate must exist in loadAd body", gateIdx > loadAdStart)
        assertTrue("record-attempt must exist in loadAd body", recordIdx > loadAdStart)
        assertTrue("isLoading=true must exist in loadAd body", isLoadingIdx > loadAdStart)
        assertTrue(
            "Gate check must come BEFORE record (otherwise blocked requests still tick the counter, exhausting cap on blocked attempts alone).",
            gateIdx < recordIdx
        )
        assertTrue(
            "Counter increment must come BEFORE isLoading=true (so race observers see the attempt-count update atomically with the load start).",
            recordIdx < isLoadingIdx
        )
    }

    @Test
    fun `loadAd gate reads cap from RemoteConfigUtils not a hardcoded constant`() {
        assertTrue(
            "Session cap must be RC-tunable so we can loosen or tighten in production without a rebuild.",
            src.contains("RemoteConfigUtils.getAppOpenMaxLoadsPerSession()")
        )
        // Sanity: the cap MUST NOT be a hardcoded number in the gate check.
        val gateBlock = src.substringAfter("val sessionCap = ").substringBefore("if (loadAttempts")
        assertTrue(
            "sessionCap must be obtained from RC, not assigned a literal int.",
            gateBlock.contains("RemoteConfigUtils.getAppOpenMaxLoadsPerSession()")
        )
    }

    @Test
    fun `ImprovedAdManager MAX_RETRIES is no longer a const val (now RC-tunable)`() {
        // Before: `private const val MAX_RETRIES = 1`
        // After:  `private val MAX_RETRIES: Int  get() = RemoteConfigUtils.getAppOpenAdMaxRetries()`
        // The hardcoded value was a hidden 2x request multiplier on transient network
        // failures (every failure triggered an additional auction slot). Now: RC-tunable
        // default 0 — single attempt per load.
        val ims = File(projectRoot(), "app/src/main/java/com/teamz/lab/debugger/utils/improved_ad_manager.kt").readText()
        assertTrue(
            "MAX_RETRIES must read from RemoteConfigUtils.getAppOpenAdMaxRetries() — not be hardcoded.",
            ims.contains("get() = RemoteConfigUtils.getAppOpenAdMaxRetries()")
        )
        assertTrue(
            "Hardcoded `const val MAX_RETRIES = 1` must be removed — was the source of the hidden 2x multiplier.",
            !ims.contains("private const val MAX_RETRIES = 1")
        )
    }

    @Test
    fun `RC defaults bundle both new app_open throttle keys`() {
        val rc = File(projectRoot(), "app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt").readText()
        assertTrue(
            "Bundled defaults must include app_open_max_loads_per_session=3 (race-safety belt — without this, " +
                "the very first session before RC fetch lands would see no cap).",
            rc.contains("\"app_open_max_loads_per_session\" to 3L")
        )
        assertTrue(
            "Bundled defaults must include app_open_ad_max_retries=0 — replaces the deleted hardcoded MAX_RETRIES=1.",
            rc.contains("\"app_open_ad_max_retries\" to 0L")
        )
        // Getters must exist with the SAME contract (default values match the bundled defaults).
        assertTrue(
            "RemoteConfigUtils.getAppOpenMaxLoadsPerSession() must exist with a default of 3.",
            rc.contains("fun getAppOpenMaxLoadsPerSession(): Int") &&
                rc.contains("if (value <= 0L) 3 else value.toInt()")
        )
        assertTrue(
            "RemoteConfigUtils.getAppOpenAdMaxRetries() must exist with a default of 0.",
            rc.contains("fun getAppOpenAdMaxRetries(): Int") &&
                rc.contains("if (value < 0L) 0 else value.toInt()")
        )
    }
}
