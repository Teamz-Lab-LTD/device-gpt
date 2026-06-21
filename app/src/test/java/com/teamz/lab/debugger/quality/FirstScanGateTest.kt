package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard for v3.1.11 FirstScanGate scaffold.
 *
 * The data layer + state machine are scaffolded; the Compose UI surface
 * is owner-reviewed in-session (per milestone anti-scope-creep rule).
 * These tests enforce the contract that the in-session UI work will rely on,
 * so a refactor doesn't silently break the gate.
 */
class FirstScanGateTest {

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        val candidates = listOf(
            File(root, rel),
            File(root, "app/$rel"),
            File(File(root).parentFile, "app/$rel"),
            File("../app/$rel"),
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Could not locate $rel")
    }

    private val gateSrc by lazy {
        locate("src/main/java/com/teamz/lab/debugger/ui/FirstScanGate.kt").readText()
    }
    private val rcSrc by lazy {
        locate("src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt").readText()
    }
    private val analyticsSrc by lazy {
        locate("src/main/java/com/teamz/lab/debugger/utils/analytics_utils.kt").readText()
    }

    @Test
    fun `FirstScanGate singleton + state machine exists`() {
        assertTrue(
            "FirstScanGate must declare an `object` singleton — MainActivity needs a stable entry point.",
            gateSrc.contains("object FirstScanGate")
        )
        assertTrue(
            "State machine must expose enum class State { NOT_GATED, SCANNING, SCORED, COMPLETED }.",
            gateSrc.contains("enum class State") &&
                gateSrc.contains("NOT_GATED") &&
                gateSrc.contains("SCANNING") &&
                gateSrc.contains("SCORED") &&
                gateSrc.contains("COMPLETED")
        )
    }

    @Test
    fun `currentState gates on RC flag AND on first_scan_completed pref`() {
        assertTrue(
            "currentState(context) must short-circuit to NOT_GATED when the RC flag is OFF — " +
                "otherwise the gate would force itself on every user even after the A/B " +
                "experiment ends.",
            gateSrc.contains("isFirstScanGateEnabled()") &&
                gateSrc.contains("return State.NOT_GATED")
        )
        assertTrue(
            "currentState(context) must also short-circuit when first_scan_completed=true — " +
                "the gate must fire exactly ONCE per install, never re-prompt a returning user.",
            gateSrc.contains("KEY_FIRST_SCAN_COMPLETED") &&
                gateSrc.contains("getBoolean(KEY_FIRST_SCAN_COMPLETED, false)")
        )
    }

    @Test
    fun `markCompleted persists the gate-once flag and the score`() {
        assertTrue(
            "markCompleted(context, finalScore) must persist first_scan_completed=true so the " +
                "gate never re-prompts.",
            gateSrc.contains("putBoolean(KEY_FIRST_SCAN_COMPLETED, true)")
        )
        assertTrue(
            "markCompleted must persist the score under KEY_FIRST_SCAN_SCORE for downstream " +
                "GA4 user-property writes.",
            gateSrc.contains("putInt(KEY_FIRST_SCAN_SCORE")
        )
        assertTrue(
            "markCompleted must fire AnalyticsEvent.FirstScanCompleted — required for D1 " +
                "cohort retention reporting (slice cohort by first scan score).",
            gateSrc.contains("AnalyticsEvent.FirstScanCompleted")
        )
    }

    @Test
    fun `logShareTapped emits the funnel event for share CTA conversion`() {
        assertTrue(
            "Share CTA tap must emit AnalyticsEvent.FirstScanShareTapped — funnel between scan " +
                "completion and viral share is the main lever measuring whether the [Share my " +
                "score] design actually moves the needle.",
            gateSrc.contains("fun logShareTapped(context: Context, score: Int)") &&
                gateSrc.contains("AnalyticsEvent.FirstScanShareTapped")
        )
    }

    @Test
    fun `computeQuickScore returns a clamped 0-100 integer`() {
        assertTrue(
            "computeQuickScore must read battery percent and clamp the resulting score to 0..100.",
            gateSrc.contains("BatteryManager.BATTERY_PROPERTY_CAPACITY") &&
                gateSrc.contains(".coerceIn(0, 100)")
        )
    }

    @Test
    fun `RemoteConfig exposes isFirstScanGateEnabled flag`() {
        assertTrue(
            "RemoteConfigUtils must expose isFirstScanGateEnabled() — required by the gate state machine.",
            rcSrc.contains("fun isFirstScanGateEnabled(): Boolean") &&
                rcSrc.contains("\"first_scan_gate_enabled\"")
        )
    }

    @Test
    fun `AnalyticsEvent enum includes FirstScanCompleted + FirstScanShareTapped`() {
        assertTrue(
            "AnalyticsEvent.FirstScanCompleted must exist with event name 'first_scan_completed'.",
            analyticsSrc.contains("FirstScanCompleted(\"first_scan_completed\")")
        )
        assertTrue(
            "AnalyticsEvent.FirstScanShareTapped must exist with event name 'first_scan_share_tapped'.",
            analyticsSrc.contains("FirstScanShareTapped(\"first_scan_share_tapped\")")
        )
    }
}
