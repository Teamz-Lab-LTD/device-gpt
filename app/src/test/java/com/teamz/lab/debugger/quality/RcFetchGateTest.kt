package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.RcFetchGate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Emulator 2026-10-09: on a fresh install the score appeared about 1 s before the first RC fetch
 * activated, so the A/B arm was never assigned (server said B). The score screen now waits for
 * that fetch, bounded so an offline first launch is not held up for long.
 */
class RcFetchGateTest {
    @Before fun reset() = RcFetchGate.resetForTest()

    @Test fun `wait ends early once the first fetch has completed`() = runBlocking {
        RcFetchGate.markDone()
        val t0 = System.nanoTime()
        assertTrue(RcFetchGate.await(5_000L))
        assertTrue("must not sit out the timeout", System.nanoTime() - t0 < 1_000_000_000L)
    }

    @Test fun `wait is bounded when the fetch never completes`() = runBlocking {
        assertFalse(RcFetchGate.await(50L))
    }

    @Test fun `marking done twice is harmless`() = runBlocking {
        RcFetchGate.markDone(); RcFetchGate.markDone()
        assertTrue(RcFetchGate.await(10L))
    }
}
