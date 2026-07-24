package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.BridgePinGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the AI Bridge pairing PIN generator.
 *
 * The PIN is the ONLY user-typed defence between a same-LAN device and every
 * bridge endpoint. Two properties matter for user-visible correctness:
 *
 *   1. Always exactly 6 digits — a leading-zero PIN like "049283" is trivially
 *      mistyped as "49283" and the user gets 401. Guarded here so a well-meaning
 *      refactor to `Random.nextInt(1_000_000)` does not silently start emitting
 *      shorter strings.
 *   2. Uniformly spread across [100000, 999999] — required for the SecureRandom
 *      contract to be worth anything. A tightened range (e.g. [100000, 200000])
 *      would still pass "6 digits" but would give a LAN attacker a 100k-guess
 *      instead of 900k-guess attack surface.
 */
class BridgePinGeneratorTest {

    @Test fun `PIN is always exactly 6 characters`() {
        repeat(2_000) {
            val pin = BridgePinGenerator.generate()
            assertEquals("PIN '$pin' must be 6 chars — leading-zero regression?", 6, pin.length)
        }
    }

    @Test fun `PIN is always numeric`() {
        val digits = "0123456789".toSet()
        repeat(2_000) {
            val pin = BridgePinGenerator.generate()
            for (c in pin) {
                assertTrue("PIN '$pin' has non-digit '$c'", c in digits)
            }
        }
    }

    @Test fun `PIN is always in the 6-digit range 100000-999999`() {
        repeat(2_000) {
            val n = BridgePinGenerator.generate().toInt()
            assertTrue("PIN $n below 100000 — leading-zero regression", n in 100_000..999_999)
        }
    }

    @Test fun `PIN generator does not repeat consecutive values (entropy smoke test)`() {
        // Not a real entropy check — that needs a full statistical test suite.
        // This is a smoke test: 100 draws must not all return the same value
        // (which would happen if someone replaced SecureRandom with `nextInt(1)`)
        // and 100 consecutive draws must not be strictly increasing (which would
        // happen if someone accidentally used a counter).
        val draws = (1..100).map { BridgePinGenerator.generate() }
        assertTrue("all 100 PINs identical — generator is broken", draws.toSet().size > 1)

        var strictlyIncreasing = true
        for (i in 1 until draws.size) {
            if (draws[i].toInt() <= draws[i - 1].toInt()) { strictlyIncreasing = false; break }
        }
        assertTrue("100 PINs strictly increasing — generator is a counter, not random", !strictlyIncreasing)
    }

    @Test fun `spread across the full range (loose statistical check)`() {
        // Draw 5000 PINs. Bucket into 9 bins (100k-199k, 200k-299k, ... 900k-999k).
        // Every bin must have at least ~200 samples (would need to have at least 1 for
        // the range to be considered "uniform enough"). This catches a tightened range
        // like `100_000 + nextInt(100_000)` which would put everything in the 100k-199k
        // bin and leave the other 8 empty.
        val bins = IntArray(9)
        repeat(5_000) {
            val n = BridgePinGenerator.generate().toInt()
            val bin = (n - 100_000) / 100_000
            bins[bin.coerceIn(0, 8)]++
        }
        for ((i, count) in bins.withIndex()) {
            assertTrue("bin $i (${(i + 1) * 100_000}k..) has only $count samples — range too tight", count >= 200)
        }
    }
}
