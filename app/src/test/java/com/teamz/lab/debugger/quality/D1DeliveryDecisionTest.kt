package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.D1OvernightDrainWorker
import com.teamz.lab.debugger.utils.D1OvernightDrainWorker.DeliveryDecision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A behavioural test for how the two D1 delivery paths interact. The existing
 * D1DualDeliveryContractTest cases are source-text greps and none of them exercises
 * this combination, which is why the defect survived the commit that introduced it.
 *
 * 295c533 added two things at once: a retry, so a flaky RC fetch no longer loses the
 * push for good, and a redundant AlarmManager path, so an OEM that never runs the
 * worker still delivers. They fought each other.
 *
 * With canRetry=false and a failed fetch the alarm skipped the retry branch, claimed
 * the single outcome slot, read the bundled `false`, and posted nothing. The worker's
 * later successful fetch then found the slot already taken and no-opped. The
 * redundant path silently beat the reliable one, and the retry it shipped with could
 * never fire.
 *
 * Context worth carrying: this push's measured funnel is 3 sent, 0 opened — by anyone,
 * ever. Fixing this makes the number trustworthy; it does not by itself make the push
 * work.
 */
class D1DeliveryDecisionTest {

    @Test
    fun `the alarm must not claim the outcome slot on a failed fetch`() {
        assertEquals(
            "canRetry=false + fetch failed is the alarm path. Claiming the slot here decides " +
                "on the bundled default and locks the worker out of ever delivering.",
            DeliveryDecision.BAIL_WITHOUT_CLAIMING,
            D1OvernightDrainWorker.decideDelivery(fetched = false, canRetry = false),
        )
    }

    @Test
    fun `the worker retries on a failed fetch rather than defaulting to false`() {
        assertEquals(
            DeliveryDecision.RETRY,
            D1OvernightDrainWorker.decideDelivery(fetched = false, canRetry = true),
        )
    }

    @Test
    fun `a successful fetch proceeds on either path`() {
        assertEquals(
            DeliveryDecision.PROCEED,
            D1OvernightDrainWorker.decideDelivery(fetched = true, canRetry = true),
        )
        assertEquals(
            "The alarm is a real delivery path when config is trustworthy — bailing here " +
                "would remove the redundancy 295c533 added for OEMs that never run the worker.",
            DeliveryDecision.PROCEED,
            D1OvernightDrainWorker.decideDelivery(fetched = true, canRetry = false),
        )
    }

    @Test
    fun `no input combination silently swallows the push`() {
        // Every path either delivers, hands back for retry, or leaves the slot open.
        // None may both decline to act AND consume the single outcome slot.
        for (fetched in listOf(true, false)) {
            for (canRetry in listOf(true, false)) {
                val d = D1OvernightDrainWorker.decideDelivery(fetched, canRetry)
                val consumesSlot = d == DeliveryDecision.PROCEED
                val acts = d == DeliveryDecision.PROCEED || d == DeliveryDecision.RETRY
                assertEquals(
                    "fetched=$fetched canRetry=$canRetry decided $d — a decision that " +
                        "consumes the outcome slot without acting is the original bug.",
                    consumesSlot,
                    consumesSlot && acts,
                )
            }
        }
    }
}
