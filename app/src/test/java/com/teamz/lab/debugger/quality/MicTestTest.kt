package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.MicTestUtils
import com.teamz.lab.debugger.utils.MicTestUtils.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test
import kotlin.math.abs

/**
 * Microphone test: the level maths, the classification, and a guard that the
 * advertised capability is actually backed by code.
 *
 * The guard is the important one. The Play title is "Battery, Mic Test: DeviceGPT"
 * and the listing promises "Mic test — input level, noise floor, instant playback",
 * yet until 2026-08-26 this codebase contained no AudioRecord at all. That is a
 * Deceptive Behavior exposure on an app that already carries one strike for the same
 * pattern. If someone deletes the capture code again, this test fails before the
 * store listing gets the chance to lie.
 */
class MicTestTest {

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        return listOf(File(root, rel), File(root, "app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() } ?: File(root, rel)
    }

    // ── the anti-deception guard ─────────────────────────────────────────────

    @Test
    fun `the advertised mic test is backed by real capture code`() {
        val utils = locate("src/main/java/com/teamz/lab/debugger/utils/MicTestUtils.kt")
        assertTrue("MicTestUtils.kt is missing — the listing claims a mic test", utils.exists())
        val src = utils.readText()
        assertTrue(
            "no AudioRecord — 'mic test' in the title would be an unbacked claim again",
            src.contains("AudioRecord(")
        )
        assertTrue(
            "no playback — the listing promises 'instant playback'",
            src.contains("AudioTrack")
        )
        assertTrue(
            "no level measurement — the listing promises 'input level'",
            src.contains("fun rms(") && src.contains("fun toDbfs(")
        )
    }

    @Test
    fun `the mic test is actually mounted in the UI, not just written`() {
        // Dead code satisfies a grep but not a reviewer opening the app.
        val tab = locate("src/main/java/com/teamz/lab/debugger/ui/screen_test_card.kt").readText()
        assertTrue("MicTestCard is never mounted", tab.contains("MicTestCard("))
    }

    @Test
    fun `mic strings exist in en AND bn`() {
        val keys = Regex("R\\.string\\.(mic_test_[a-z0-9_]+)")
            .findAll(locate("src/main/java/com/teamz/lab/debugger/ui/MicTestCard.kt").readText())
            .map { it.groupValues[1] }.toSortedSet()
        assertTrue("no mic_test_* strings referenced", keys.isNotEmpty())
        val dq = '"'
        for (locale in listOf("values", "values-bn")) {
            val body = locate("src/main/res/$locale/strings.xml").readText()
            for (k in keys) {
                assertTrue("$locale/strings.xml missing R.string.$k",
                    body.contains("name=$dq$k$dq"))
            }
        }
    }

    // ── level maths ──────────────────────────────────────────────────────────

    @Test
    fun `rms of digital silence is zero and maps to the floor, not negative infinity`() {
        val silence = ShortArray(512)
        assertEquals(0.0, MicTestUtils.rms(silence), 0.0001)
        // -inf cannot be rendered or averaged; the clamp keeps every downstream
        // calculation finite.
        assertEquals(MicTestUtils.MIN_DBFS, MicTestUtils.toDbfs(0.0), 0.0001)
    }

    @Test
    fun `rms of an empty buffer is zero rather than NaN`() {
        assertEquals(0.0, MicTestUtils.rms(ShortArray(0)), 0.0001)
        assertFalse(MicTestUtils.rms(ShortArray(0)).isNaN())
    }

    @Test
    fun `full scale reads as zero dBFS`() {
        val full = ShortArray(256) { Short.MAX_VALUE }
        val db = MicTestUtils.toDbfs(MicTestUtils.rms(full))
        assertTrue("full scale should sit at ~0 dBFS, got $db", abs(db) < 0.5)
    }

    @Test
    fun `halving amplitude drops the level by about six dB`() {
        val loud = ShortArray(256) { 10_000 }
        val half = ShortArray(256) { 5_000 }
        val delta = MicTestUtils.toDbfs(MicTestUtils.rms(loud)) -
            MicTestUtils.toDbfs(MicTestUtils.rms(half))
        assertTrue("expected ~6dB for a halving, got $delta", abs(delta - 6.02) < 0.2)
    }

    @Test
    fun `clipping is detected only at the rails`() {
        assertTrue(MicTestUtils.isClipping(ShortArray(8) { Short.MAX_VALUE }))
        assertTrue(MicTestUtils.isClipping(shortArrayOf(0, 0, Short.MIN_VALUE, 0)))
        assertFalse(MicTestUtils.isClipping(ShortArray(8) { 20_000 }))
        assertFalse(MicTestUtils.isClipping(ShortArray(0)))
    }

    @Test
    fun `only the length prefix is inspected`() {
        // The capture loop reads fewer samples than the buffer holds; stale tail data
        // from a previous frame must not be measured or it fabricates a level.
        val buf = ShortArray(64)
        buf[40] = Short.MAX_VALUE
        assertFalse("must ignore samples beyond `length`", MicTestUtils.isClipping(buf, 20))
        assertEquals(0.0, MicTestUtils.rms(buf, 20), 0.0001)
    }

    // ── classification ───────────────────────────────────────────────────────

    @Test
    fun `a dead mic in a loud room is SILENT despite a high absolute level`() {
        // THE case that makes headroom the deciding number. A high peak alone would
        // call a broken microphone healthy.
        assertEquals(
            Verdict.SILENT,
            MicTestUtils.classify(peakDbfs = -12.0, headroomDb = 1.0, clipped = false)
        )
    }

    @Test
    fun `clipping outranks every other reading`() {
        assertEquals(
            Verdict.CLIPPING,
            MicTestUtils.classify(peakDbfs = -1.0, headroomDb = 40.0, clipped = true)
        )
    }

    @Test
    fun `normal speech over a quiet room classifies OK`() {
        assertEquals(
            Verdict.OK,
            MicTestUtils.classify(peakDbfs = -18.0, headroomDb = 30.0, clipped = false)
        )
    }

    @Test
    fun `a faint rise above the room is VERY_QUIET, not OK`() {
        assertEquals(
            Verdict.VERY_QUIET,
            MicTestUtils.classify(peakDbfs = -30.0, headroomDb = 6.0, clipped = false)
        )
    }

    @Test
    fun `a hot but unclipped signal is LOUD`() {
        assertEquals(
            Verdict.LOUD,
            MicTestUtils.classify(peakDbfs = -1.0, headroomDb = 40.0, clipped = false)
        )
    }

    // ── result shape ─────────────────────────────────────────────────────────

    @Test
    fun `headroom is peak minus the room, and the user verdict starts unanswered`() {
        val r = MicTestUtils.MicTestResult(
            noiseFloorDbfs = -55.0, peakDbfs = -12.0, averageDbfs = -22.0,
            clipped = false, sampleCount = 132_300
        )
        assertEquals(43.0, r.headroomDb, 0.0001)
        assertEquals(null, r.userHeardPlayback)
    }
}
