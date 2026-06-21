package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.1.11 W1 race-fix regression guard for FirstScanGate.
 *
 * THE BUG: On fresh install, MainActivity.onCreate called
 * `FirstScanGate.currentState(this)` ONCE inside `remember{}`. Firebase
 * Remote Config typically takes 1-5 seconds to fetch on warm cache and up
 * to 5 minutes on first install. During that window, currentState() reads
 * the BUNDLED default `first_scan_gate_enabled=false` and returns NOT_GATED.
 * Compose captures NOT_GATED into the remember{} and never re-evaluates,
 * so the gate is permanently skipped even though RC eventually returned true.
 *
 * THE FIX (2026-06-22): a LaunchedEffect that polls currentState() every
 * 500ms for up to 10 seconds. If the state transitions out of NOT_GATED
 * (because RC fetched and the flag is true), the Compose state is updated
 * and the UI re-renders the gate. After 10s the loop exits cleanly with
 * no further battery cost.
 *
 * THIS TEST PREVENTS:
 *   - The LaunchedEffect being removed (re-introducing the race silently)
 *   - The poll interval being lengthened beyond 1s (would feel laggy)
 *   - The max wait window being shortened below 5s (RC often takes that long)
 *   - The early-break optimization being removed (would keep polling
 *     even after gate is already triggered, wasting battery)
 *
 * Same bug class as the D1OvernightDrainWorker race fixed earlier in W1 —
 * keep the test pattern parallel for consistency.
 */
class FirstScanGateRaceFixContractTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private val mainSrc: String by lazy {
        File(projectRoot(),
            "app/src/main/java/com/teamz/lab/debugger/MainActivity.kt").readText()
    }

    @Test
    fun mainActivity_pollsFirstScanGateStateInLaunchedEffect() {
        assertTrue(
            "MainActivity must contain a LaunchedEffect that polls " +
                "FirstScanGate.currentState — the race fix for fresh-install RC fetch lag. " +
                "Without it, gate is skipped permanently when RC hasn't fetched at first " +
                "composition.",
            mainSrc.contains("FirstScanGate") &&
                mainSrc.contains("LaunchedEffect(Unit)") &&
                mainSrc.contains(".currentState(this@MainActivity)")
        )
    }

    @Test
    fun pollLoop_hasReasonableMaxWaitWindow() {
        // RC typical fetch: 1-5s on warm cache, up to 5min on cold install.
        // 10s captures the warm-cache case (which is the common case after first
        // launch ever) without burning battery. Asserting >=5_000L gives flex
        // room if a future PR tunes the constant up. <5s would miss most RC fetches.
        val pollBlock = mainSrc.substringAfter("LaunchedEffect(Unit) {").substringBefore("when (gateState.value) {")
        assertTrue(
            "Poll loop must run at least 5_000ms to give RC time to fetch on warm " +
                "cache. Found block: ${pollBlock.take(500)}",
            pollBlock.contains("10_000L") || pollBlock.contains("10000L") ||
                pollBlock.contains("5_000L") || pollBlock.contains("5000L") ||
                pollBlock.contains("15_000L")
        )
    }

    @Test
    fun pollLoop_breaksEarlyWhenStateChanges() {
        // After RC fetches and state flips out of NOT_GATED, the loop must EXIT —
        // continuing to poll would burn battery for no benefit.
        val pollBlock = mainSrc.substringAfter("LaunchedEffect(Unit) {").substringBefore("when (gateState.value) {")
        assertTrue(
            "Poll loop must contain an early-break or while-condition-exit when state " +
                "transitions out of NOT_GATED.",
            pollBlock.contains("break") || pollBlock.contains("return@LaunchedEffect") ||
                pollBlock.contains("== com.teamz.lab.debugger.ui.FirstScanGate.State.NOT_GATED") ||
                pollBlock.contains("== FirstScanGate.State.NOT_GATED")
        )
    }

    @Test
    fun pollLoop_pollIntervalIsReasonable() {
        // 500ms poll interval = 20 reads over 10s = negligible CPU/battery.
        // Faster than 100ms would be spammy; slower than 1s would feel laggy
        // when the user opens the app and waits ~2-3s for the gate to appear.
        val pollBlock = mainSrc.substringAfter("LaunchedEffect(Unit) {").substringBefore("when (gateState.value) {")
        val hasReasonableInterval = pollBlock.contains("500L") ||
            pollBlock.contains("250L") ||
            pollBlock.contains("1000L") ||
            pollBlock.contains("750L")
        assertTrue(
            "Poll interval should be in the 250-1000ms range for snappy UX without battery " +
                "cost. Block snippet: ${pollBlock.take(500)}",
            hasReasonableInterval
        )
    }
}
