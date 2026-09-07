package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the 2026-09-08 fix for the first-scan gate race.
 *
 * setDefaultsAsync() is ASYNC. Until it completes, remoteConfig.getBoolean() on an unset
 * key returns Firebase's static default — FALSE — not the bundled value. On a fresh
 * install with no cached config, MainActivity reads FirstScanGate.currentState() roughly
 * 0.1s after Application.onCreate starts the defaults call, and loses that race.
 *
 * Consequence, before this fix:
 *   currentState() -> NOT_GATED -> the full tab UI composes
 *   a 500ms poll in MainActivity then flips gateState up to 10s later
 *   -> the screen the user is already reading is replaced by the scan gate
 *
 * This exact race was found and fixed for app-open ads on the neighbouring function
 * (shouldShowAppOpenAds, guarded by `defaultsApplied`, with a comment describing the same
 * failure). isFirstScanGateEnabled never got the guard. One function fixed, its neighbour
 * left exposed — the same shape as the paywall cooldown that was written and never read.
 */
class FirstScanGateDefaultsRaceTest {

    /** Comments stripped: a guard in this repo once matched its own explanatory comment. */
    private val rc = File("src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt")
        .readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    @Test
    fun `the first-scan gate answers from the bundled default until defaults land`() {
        val fn = rc.substringAfter("fun isFirstScanGateEnabled()").substringBefore("\n    fun ")
        assertTrue(
            "isFirstScanGateEnabled must consult defaultsApplied. Without it, a fresh install " +
                "reads Firebase's static false, renders the tab UI, and then swaps the screen " +
                "out from under the user when the poll notices.",
            fn.contains("defaultsApplied")
        )
        assertTrue(
            "it must return the bundled constant while defaults are pending, not a literal",
            fn.contains("DEFAULT_FIRST_SCAN_GATE_ENABLED")
        )
    }

    @Test
    fun `the bundled constant and the defaults map cannot drift apart`() {
        val const = Regex("DEFAULT_FIRST_SCAN_GATE_ENABLED\\s*=\\s*(true|false)")
            .find(rc)?.groupValues?.get(1)
        assertTrue("DEFAULT_FIRST_SCAN_GATE_ENABLED must be declared", const != null)
        val mapValue = Regex("\"first_scan_gate_enabled\"\\s+to\\s+(DEFAULT_FIRST_SCAN_GATE_ENABLED|true|false)")
            .find(rc)?.groupValues?.get(1)
        assertTrue("first_scan_gate_enabled must be in the bundled defaults map", mapValue != null)
        assertTrue(
            "the defaults map must reference the constant, or the two can silently disagree " +
                "and the pre-defaults answer stops matching the post-defaults one",
            mapValue == "DEFAULT_FIRST_SCAN_GATE_ENABLED"
        )
    }

    @Test
    fun `every RC reader that decides what the FIRST screen is uses the guard`() {
        // The two flags that can change what a user sees before defaults land. Both must be
        // guarded; shouldShowAppOpenAds already was, which is how the race was identified.
        for (fnName in listOf("fun shouldShowAppOpenAds()", "fun isFirstScanGateEnabled()")) {
            val body = rc.substringAfter(fnName).substringBefore("\n    fun ")
            assertTrue("$fnName must check defaultsApplied", body.contains("defaultsApplied"))
        }
    }
}
