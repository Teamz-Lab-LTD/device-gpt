package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.ui.timeAgoLabel
import com.teamz.lab.debugger.ui.verdictFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v3.1.12 — LastScoreCard pure-logic contract guard.
 *
 * The card itself is Compose (integration-tested elsewhere via androidTest).
 * These are the deterministic string builders it depends on. Regression risks:
 *  - Verdict tier boundaries move (breaks screenshot claim "95 = Excellent")
 *  - timeAgoLabel breaks at day/week/month boundaries (misleads users about freshness)
 */
class LastScoreCardContractTest {

    @Test
    fun verdictFor_excellent_at_90plus() {
        assertEquals("Excellent", verdictFor(100))
        assertEquals("Excellent", verdictFor(95))
        assertEquals("Excellent", verdictFor(90))
    }

    @Test
    fun verdictFor_good_at_75_to_89() {
        assertEquals("Good", verdictFor(89))
        assertEquals("Good", verdictFor(80))
        assertEquals("Good", verdictFor(75))
    }

    @Test
    fun verdictFor_fair_at_60_to_74() {
        assertEquals("Fair", verdictFor(74))
        assertEquals("Fair", verdictFor(65))
        assertEquals("Fair", verdictFor(60))
    }

    @Test
    fun verdictFor_poor_at_40_to_59() {
        assertEquals("Poor", verdictFor(59))
        assertEquals("Poor", verdictFor(50))
        assertEquals("Poor", verdictFor(40))
    }

    @Test
    fun verdictFor_critical_below_40() {
        assertEquals("Critical", verdictFor(39))
        assertEquals("Critical", verdictFor(20))
        assertEquals("Critical", verdictFor(0))
    }

    @Test
    fun timeAgoLabel_just_now_under_minute() {
        val now = 1_000_000_000L
        assertEquals("just now", timeAgoLabel(now, now))
        assertEquals("just now", timeAgoLabel(now - 30_000L, now))
    }

    @Test
    fun timeAgoLabel_minutes() {
        val now = 1_000_000_000L
        assertEquals("1m ago", timeAgoLabel(now - 60_000L, now))
        assertEquals("59m ago", timeAgoLabel(now - 59L * 60_000L, now))
    }

    @Test
    fun timeAgoLabel_hours() {
        val now = 1_000_000_000L
        assertEquals("1h ago", timeAgoLabel(now - 60L * 60_000L, now))
        assertEquals("23h ago", timeAgoLabel(now - 23L * 60L * 60_000L, now))
    }

    @Test
    fun timeAgoLabel_days() {
        val now = 1_000_000_000L
        assertEquals("1d ago", timeAgoLabel(now - 24L * 60L * 60_000L, now))
        assertEquals("6d ago", timeAgoLabel(now - 6L * 24L * 60L * 60_000L, now))
    }

    @Test
    fun timeAgoLabel_weeks_and_months() {
        val now = 1_000_000_000L
        assertEquals("1w ago", timeAgoLabel(now - 7L * 24L * 60L * 60_000L, now))
        assertEquals("1mo ago", timeAgoLabel(now - 30L * 24L * 60L * 60_000L, now))
    }

    @Test
    fun timeAgoLabel_defensive_future_timestamp_returns_just_now() {
        val now = 1_000_000_000L
        assertEquals("just now", timeAgoLabel(now + 10_000L, now))
    }
}
