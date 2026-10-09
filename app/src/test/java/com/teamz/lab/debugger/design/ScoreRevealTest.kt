package com.teamz.lab.debugger.design

import com.teamz.lab.debugger.ui.components.SCORE_FAIR_FROM
import com.teamz.lab.debugger.ui.components.SCORE_GOOD_FROM
import com.teamz.lab.debugger.ui.components.ScoreRingSession
import com.teamz.lab.debugger.ui.components.ScoreTone
import com.teamz.lab.debugger.ui.components.scoreTone
import com.teamz.lab.debugger.ui.components.shakeOffsetFraction
import com.teamz.lab.debugger.ui.theme.DgMotion
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The score reveal: which colour a score gets, that it plays once, and that the screen keeps its contract. */
class ScoreRevealTest {

    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app/src/main/java/com/teamz/lab/debugger/$rel").readText()
    }

    @Test fun `ring colour follows the verdict cut-offs of the score screen`() {
        assertEquals(75, SCORE_GOOD_FROM)
        assertEquals(40, SCORE_FAIR_FROM)
        assertEquals(ScoreTone.Good, scoreTone(100))
        assertEquals(ScoreTone.Good, scoreTone(75))
        assertEquals(ScoreTone.Fair, scoreTone(74))
        assertEquals(ScoreTone.Fair, scoreTone(40))
        assertEquals(ScoreTone.Poor, scoreTone(39))
        assertEquals(ScoreTone.Poor, scoreTone(0))
        // A score out of ten is read on the same scale.
        assertEquals(ScoreTone.Good, scoreTone(8, 10))
        assertEquals(ScoreTone.Fair, scoreTone(4, 10))
        assertEquals(ScoreTone.Poor, scoreTone(3, 10))
        assertEquals(ScoreTone.Poor, scoreTone(5, 0))
    }

    @Test fun `a ring plays its reveal once per session`() {
        ScoreRingSession.reset()
        assertTrue(ScoreRingSession.firstTime("card"))
        assertFalse(ScoreRingSession.firstTime("card"))
        assertTrue(ScoreRingSession.firstTime("other"))
        ScoreRingSession.reset()
    }

    @Test fun `the shake swings both ways, never past its distance, and ends at rest`() {
        assertEquals(0f, shakeOffsetFraction(0f), 0.0001f)
        assertEquals(0f, shakeOffsetFraction(1f), 0.0001f)
        val samples = (0..100).map { shakeOffsetFraction(it / 100f) }
        assertTrue(samples.all { abs(it) <= 1f })
        assertTrue(samples.any { it > 0.3f } && samples.any { it < -0.3f })
        val turns = samples.zipWithNext().count { (a, b) -> (a < 0f) != (b < 0f) && b != 0f }
        assertTrue("2 to 3 swings, got $turns sign changes", turns in 3..6)
        assertEquals(300, DgMotion.shake)
    }

    @Test fun `the score screen reveals with the shared ring and ends inside 1600 ms`() {
        val s = src("ui/FirstScanGateScreen.kt")
        assertTrue(s.contains("ScoreRing("))
        assertTrue("actions are faded, not removed, so they can be tapped at once", s.contains(".fadeInWhen(settled"))
        // ring, then the last thing to start (the actions), then its own duration
        val end = DgMotion.reveal + DgMotion.stagger * 8 + DgMotion.standard
        assertTrue("reveal ends at $end ms", end <= 1600)
        // The experiment lines stay as they were.
        assertTrue(s.contains("remember { com.teamz.lab.debugger.utils.FirstScreenExperiment.arm(context) } == \"B\""))
        assertTrue(s.contains("com.teamz.lab.debugger.utils.RcFetchGate.await(2_500L)"))
    }
}
