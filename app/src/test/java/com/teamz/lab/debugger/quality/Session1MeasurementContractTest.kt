package com.teamz.lab.debugger.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the 2026-09-08 measurement package.
 *
 * Why it exists: on 2026-09-08 three of the owner's four questions were unanswerable
 * because the data was never recorded, not because it was never analysed.
 *
 *   unifiedScreenName   100% "(not set)" property-wide — the app is single-Activity,
 *                       so Firebase auto-collection sees one screen forever
 *   work_state          d1_overnight_drain_post_mortem fired 11x; the payload naming
 *                       WHY the D1 job did not run was unregistered and unqueryable
 *   result              widget_pin_prompt_result fired 24x; the shown/unsupported/
 *                       already_added split was unqueryable
 *   session_count       no way to prove "no ads in sessions 1-2" actually holds
 *   share completion    first_scan_share_tapped 49 -> no completion event exists
 *
 * The July 2026 growth plan made "measurement cleanup BEFORE product change" its Day 0
 * item (insight #9). It was skipped, twice. These guards make skipping it fail the build.
 */
class Session1MeasurementContractTest {

    /** Comments stripped: a guard once matched the very comment explaining it. */
    private fun src(path: String) = File(path).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    private val nav = src("src/main/java/com/teamz/lab/debugger/ui/adaptive/DeviceGptNavExperience.kt")
    private val tabOrder = src("src/main/java/com/teamz/lab/debugger/utils/TabOrderManager.kt")
    private val analytics = src("src/main/java/com/teamz/lab/debugger/utils/analytics_utils.kt")
    private val pinPrompt = src("src/main/java/com/teamz/lab/debugger/utils/WidgetPinPrompt.kt")
    private val mainActivity = src("src/main/java/com/teamz/lab/debugger/MainActivity.kt")

    @Test
    fun `every tab logs a screen_name, so unifiedScreenName stops being (not set)`() {
        // Verified on a Pixel 8a 2026-09-08: logMainTabSelectionAnalytics is reachable ONLY
        // from Tab.onClick, so putting screen_view there missed the tab the user LANDS on —
        // the single most important screen for "where do they leave". It must be driven by
        // the selected tab instead, so the landing tab and every change are both covered.
        assertTrue(
            "screen_view must be logged from a LaunchedEffect keyed on the selected tab, " +
                "not from the click handler — the landing tab is never clicked",
            Regex("LaunchedEffect\\(selectedTab[^)]*\\)").containsMatchIn(nav)
        )
        val effect = nav.substringAfter("LaunchedEffect(selectedTab").substringBefore("tabOrder.forEachIndexed")
        assertTrue("the effect must log ScreenViewed", effect.contains("ScreenViewed"))
        assertTrue("the screen view must carry a screen_name param", effect.contains("screen_name"))

        // The branches moved out of logMainTabSelectionAnalytics into tabViewedEvent on
        // 2026-09-09, because the widget-navigation path had its own hardcoded copy that always
        // logged TabHealthViewed. One mapping, one place to add a tab. The requirement this
        // guard encodes is unchanged: no TabType may be missing from it.
        val fn = nav.substringAfter("fun tabViewedEvent")
            .substringBefore("private fun logMainTabSelectionAnalytics")

        // Every TabType must be reachable in that when — a new tab added without a branch
        // would silently have no screen name.
        val tabTypes = Regex("^\\s{4}([A-Z_]+),", RegexOption.MULTILINE)
            .findAll(tabOrder.substringAfter("enum class TabType").substringBefore("}"))
            .map { it.groupValues[1] }.toList()
        assertTrue("expected the TabType enum to parse", tabTypes.size >= 8)
        for (t in tabTypes) {
            assertTrue("TabType.$t has no branch in logMainTabSelectionAnalytics", fn.contains("TabType.$t"))
        }
    }

    @Test
    fun `session_count rides on the events that prove the session-1 gates hold`() {
        assertTrue(
            "AnalyticsUtils must expose a helper that stamps session_count, otherwise every " +
                "call site re-implements it and one of them will forget",
            analytics.contains("fun logEventWithSession")
        )
        assertTrue(
            "session_count is the dimension that proves 'no ads in sessions 1-2' — it must be " +
                "the literal param name registered in GA4",
            analytics.contains("\"session_count\"")
        )
    }

    @Test
    fun `the widget pin prompt can report success at all`() {
        val fn = pinPrompt.substringAfter("fun maybePrompt")
        assertTrue(
            "requestPinAppWidget's third argument is the success callback. Passing null — as this " +
                "did until 2026-09-08 — makes widget_add_to_home_screen_success structurally " +
                "impossible to fire, so '18 prompts, 0 successes' measured nothing.",
            !fn.contains("requestPinAppWidget(component, null, null)")
        )
        assertTrue(
            "a PendingIntent must be supplied so the OS can tell us the widget was actually pinned",
            fn.contains("PendingIntent")
        )
    }

    @Test
    fun `the first-scan share reports completion, not just intent`() {
        assertTrue(
            "FirstScanGate logs first_scan_share_tapped and leaves the dialog to the caller. " +
                "49 taps produced no measurable outcome. The caller must log completion.",
            analytics.contains("FirstScanShareCompleted")
        )
        assertTrue(
            "the completion event belongs at the share call site in MainActivity",
            mainActivity.contains("FirstScanShareCompleted")
        )
    }
}
