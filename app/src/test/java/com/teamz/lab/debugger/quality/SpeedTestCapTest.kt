package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.readUntilDeadline
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream

/**
 * The download speed test must stop at its time cap even when the stream never ends: on a
 * 0.5 Mbps line the full 10 MB used to hold the Network tab for ~160 s. (2026-10-08: the earlier
 * source-text check survived deleting the cap; this one is behavioural.)
 */
class SpeedTestCapTest {

    /**
     * Effectively endless: each read returns a full 64 KB buffer and advances a fake clock 1 s.
     * Ends after 1,000 reads only so that a build with the cap removed FAILS this test instead
     * of hanging it.
     */
    private class EndlessStream(private val clock: LongArray) : InputStream() {
        private var reads = 0
        override fun read(): Int = 0
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (++reads > 1000) return -1
            clock[0] += 1_000_000_000L; return len
        }
    }

    @Test
    fun `stops at the deadline on a stream that never ends`() {
        val clock = longArrayOf(0L)
        val bytes = readUntilDeadline(EndlessStream(clock), maxNanos = 8_000_000_000L, now = { clock[0] })
        assertEquals("8 reads of 64 KB before the 8 s cap", 8L * 64 * 1024, bytes)
    }

    @Test
    fun `reads a short stream to the end`() {
        val bytes = readUntilDeadline("hello".byteInputStream(), maxNanos = 8_000_000_000L, now = { 0L })
        assertEquals(5L, bytes)
    }
}
