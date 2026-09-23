package com.teamz.lab.debugger.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TYPE_BASELINE_SNAPSHOT is the row the Device Timeline renders as the daily health point,
 * and recordDailySnapshotIfDue() is its only writer.
 *
 * That writer's single call site used to sit inside `if (widgetV2)` in LockScreenMonitorWidget.
 * widget_v2_enabled is false in production while timeline_enabled is true, so a live feature
 * was reading a table nothing ever wrote to. The second-order effect was worse: flipping
 * widget_v2_enabled on could not show a "vs yesterday" delta for at least a day, because the
 * history it compares against only began accruing at the moment of the flip — an experiment
 * whose own measurement was gated behind the thing being measured.
 *
 * The rule: a writer whose data a DIFFERENT feature consumes must not sit behind that
 * writer's own feature flag.
 */
class SnapshotWriterNotFlagGatedTest {

    private fun src(p: String) = File(p).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines().joinToString("\n") { it.substringBefore("//") }

    private val widget =
        src("src/main/java/com/teamz/lab/debugger/widgets/LockScreenMonitorWidget.kt")
    private val scanGate = src("src/main/java/com/teamz/lab/debugger/ui/FirstScanGate.kt")

    @Test
    fun `positive control - both files loaded and survived comment stripping`() {
        assertTrue(widget.contains("isWidgetV2Enabled()"))
        assertTrue(scanGate.contains("recordScoreScan("))
    }

    @Test
    fun `negative control - comment stripping actually removes comments`() {
        assertEquals(
            "a phrase that exists only in a comment leaked through the stripper",
            0,
            Regex("an experiment whose own measurement").findAll(widget + scanGate).count(),
        )
    }

    @Test
    fun `the widget writes its snapshot outside the v2 branch`() {
        val callIndex = widget.indexOf("recordDailySnapshotIfDue")
        val branchIndex = widget.indexOf("if (widgetV2)")
        assertTrue("recordDailySnapshotIfDue call not found", callIndex >= 0)
        assertTrue("if (widgetV2) branch not found", branchIndex >= 0)
        assertTrue(
            "the snapshot write moved back inside `if (widgetV2)`. The Device Timeline " +
                "consumes these rows and timeline_enabled is ON in production, so gating the " +
                "writer on widget_v2_enabled empties a live feature.",
            callIndex < branchIndex,
        )
    }

    @Test
    fun `a scan records the day's snapshot even with no widget pinned`() {
        assertTrue(
            "FirstScanGate no longer records a daily snapshot. Without it, only users who " +
                "have pinned the widget can ever produce Timeline history.",
            scanGate.contains("recordDailySnapshotIfDue("),
        )
    }

    @Test
    fun `the scan converts its 0-100 score to the 0-10 scale the snapshot expects`() {
        // recordDailySnapshotIfDue takes healthScore10; recordScoreScan takes 0-100. Passing
        // finalScore straight through would store 95 where the widget renders "95/10".
        // Read a fixed window after the call rather than trying to balance parentheses: the
        // first version of this assertion used a regex that could not match the nested parens
        // in `(finalScore.coerceIn(0, 100)) / 10` and failed against correct code.
        val at = scanGate.indexOf("recordDailySnapshotIfDue(")
        assertTrue("could not locate the call to inspect its argument", at >= 0)
        val call = scanGate.substring(at, minOf(at + 160, scanGate.length))
        assertTrue(
            "the 0-100 scan score is not being scaled to 0-10. Found: $call",
            call.contains("/ 10"),
        )
    }
}
