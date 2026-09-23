package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.ReviewGate
import com.teamz.lab.debugger.utils.reviewGate
import org.junit.Assert.assertEquals
import org.junit.Test

private const val DAY = 24L * 60L * 60L * 1000L
private const val MONTH = 30L * DAY

/**
 * The live Remote Config value for review_delay_first_launch_ms is 86400000. It was raised from
 * 15000 to stop the sheet landing 15 seconds into session one, with a note that the real gate
 * was a code fix. Until that fix, the value was slept on by an unscoped coroutine, so with an
 * average session of 155 seconds the prompt could never fire at all.
 *
 * The distinction these tests protect: the value is a minimum AGE measured from a stored
 * timestamp, not a duration anything waits out in memory.
 */
class ReviewGateTest {

    private fun gate(
        alreadyReviewed: Boolean = false,
        msSinceInstall: Long = 2 * DAY,
        minInstallAgeMs: Long = DAY,
        msSinceLastPrompt: Long = -1L,
        minMsBetweenPrompts: Long = MONTH,
        appOpenCount: Int = 2,
        meaningfulInteractions: Int = 0,
        minAppOpens: Int = 1,
        minInteractions: Int = 1,
    ) = reviewGate(
        alreadyReviewed, msSinceInstall, minInstallAgeMs, msSinceLastPrompt,
        minMsBetweenPrompts, appOpenCount, meaningfulInteractions, minAppOpens, minInteractions,
    )

    @Test
    fun `the first session never asks`() {
        // -1 is "no install timestamp yet", which only happens on the very first session.
        assertEquals(ReviewGate.TOO_SOON_AFTER_INSTALL, gate(msSinceInstall = -1L))
    }

    @Test
    fun `a 155 second session on install day does not ask`() {
        assertEquals(
            "the ask reached a user inside their first session again",
            ReviewGate.TOO_SOON_AFTER_INSTALL,
            gate(msSinceInstall = 155_000L, minInstallAgeMs = DAY),
        )
    }

    @Test
    fun `the live 24 hour setting becomes reachable on a later day`() {
        // The whole point. Under the old coroutine this case could not occur, because the
        // process died long before the delay elapsed. Now the app only has to be REOPENED.
        assertEquals(
            ReviewGate.SHOW,
            gate(msSinceInstall = DAY + 1_000L, minInstallAgeMs = 86_400_000L, appOpenCount = 2),
        )
    }

    @Test
    fun `one second short of the age gate still declines`() {
        assertEquals(
            ReviewGate.TOO_SOON_AFTER_INSTALL,
            gate(msSinceInstall = DAY - 1_000L, minInstallAgeMs = DAY),
        )
    }

    @Test
    fun `a user who has done nothing is not asked`() {
        assertEquals(
            ReviewGate.NOT_ENOUGH_USE,
            gate(appOpenCount = 0, meaningfulInteractions = 0),
        )
    }

    @Test
    fun `a meaningful interaction qualifies without a second open`() {
        assertEquals(
            ReviewGate.SHOW,
            gate(appOpenCount = 0, meaningfulInteractions = 1),
        )
    }

    @Test
    fun `asking twice inside the cooldown is refused`() {
        assertEquals(ReviewGate.ASKED_RECENTLY, gate(msSinceLastPrompt = 3 * DAY))
    }

    @Test
    fun `never prompted reads as eligible rather than as just prompted`() {
        // -1 must not compare as "0 ms since last prompt", which would block forever.
        assertEquals(ReviewGate.SHOW, gate(msSinceLastPrompt = -1L))
    }

    @Test
    fun `cooldown expires`() {
        assertEquals(ReviewGate.SHOW, gate(msSinceLastPrompt = MONTH + 1))
    }

    @Test
    fun `an existing reviewer is never asked again`() {
        assertEquals(ReviewGate.ALREADY_REVIEWED, gate(alreadyReviewed = true))
    }

    @Test
    fun `already reviewed outranks every other reason`() {
        assertEquals(
            ReviewGate.ALREADY_REVIEWED,
            gate(alreadyReviewed = true, msSinceInstall = -1L, appOpenCount = 0),
        )
    }

    @Test
    fun `a zero age gate asks on the second session`() {
        // Setting review_delay_first_launch_ms to 0 must not resurrect a session-one ask:
        // the first session has no install timestamp and is refused before this is read.
        assertEquals(ReviewGate.SHOW, gate(msSinceInstall = 1_000L, minInstallAgeMs = 0L))
        assertEquals(
            ReviewGate.TOO_SOON_AFTER_INSTALL,
            gate(msSinceInstall = -1L, minInstallAgeMs = 0L),
        )
    }
}
