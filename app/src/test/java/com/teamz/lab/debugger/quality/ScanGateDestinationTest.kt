package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins where a user lands after the first-scan score.
 *
 * The score screen's CTA is labelled "See details". Until 2026-09-08 it set the gate to
 * COMPLETED and fell through to DeviceGptNavExperience's initialTab, which resolves to
 * index 0 — whatever tab_order happens to start with. That was the Leaderboard, so:
 *
 *   the details of YOUR score           lived on the Health tab (health_section.kt)
 *   the daily insight                   rendered only inside LastScoreCard (Health)
 *   the Device Timeline                 rendered only under LastScoreCard (Health)
 *   "See details" landed you on         a native ad above a blurred list of strangers
 *
 * Remote Config now puts Health first, which hides the bug rather than fixing it. Naming
 * the destination keeps it correct if tab_order is ever reordered again — and tab_order
 * has been reordered by the owner before.
 */
class ScanGateDestinationTest {

    /** Comments stripped: a guard in this repo once matched its own explanatory comment. */
    private val main = File("src/main/java/com/teamz/lab/debugger/MainActivity.kt").readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    private val nav = File("src/main/java/com/teamz/lab/debugger/ui/adaptive/DeviceGptNavExperience.kt")
        .readText()

    @Test
    fun `both exits from the score screen name Health as the destination`() {
        // The gate has exactly two ways out: the share CTA and "See details".
        val gateBlock = main.substringAfter("FirstScanGateScreen(").substringBefore("State.NOT_GATED,")
        val completions = Regex("FirstScanGate\\.State\\.COMPLETED").findAll(gateBlock).count()
        assertTrue("expected both gate exits to be present, found $completions", completions >= 2)

        val destinations = Regex("navigate_to_tab").findAll(gateBlock).count()
        assertTrue(
            "each exit must name its destination. Falling through to initialTab means the " +
                "landing tab is whatever tab_order starts with — the reason 'See details' " +
                "showed no details. Found $destinations of $completions.",
            destinations >= completions
        )
        assertTrue(
            "the destination must be health — that is where the score card, the insight and " +
                "the timeline actually render",
            gateBlock.contains("\"health\"")
        )
    }

    @Test
    fun `the name-based route the fix depends on still exists`() {
        // If this navigation path is ever removed, the fix above silently becomes a no-op.
        assertTrue(
            "DeviceGptNavExperience must still resolve the navigate_to_tab extra",
            nav.contains("getStringExtra(\"navigate_to_tab\")")
        )
        assertTrue(
            "and must still map the literal \"health\" to the Health tab index",
            Regex("navigateToTab\\.equals\\(\"health\"").containsMatchIn(nav)
        )
    }
}
