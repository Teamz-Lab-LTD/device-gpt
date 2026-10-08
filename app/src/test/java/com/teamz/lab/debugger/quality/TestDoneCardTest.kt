package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.FirstScreenExperiment
import com.teamz.lab.debugger.utils.TestDoneCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestDoneCardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clear() {
        for (p in listOf(TestDoneCard.PREFS, FirstScreenExperiment.PREFS))
            context.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit()
        TestDoneCard.dismiss()
    }

    @Test fun `only arm B, only once`() {
        assertTrue(TestDoneCard.shouldShow(isB = true, alreadyShown = false))
        assertFalse(TestDoneCard.shouldShow(isB = true, alreadyShown = true))
        assertFalse(TestDoneCard.shouldShow(isB = false, alreadyShown = false))
    }

    @Test fun `first completed test in arm B shows the card once`() {
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .edit().putString(FirstScreenExperiment.KEY_ARM, "B").commit()
        TestDoneCard.onTestCompleted(context)
        assertTrue(TestDoneCard.visible.value)
        TestDoneCard.dismiss()
        TestDoneCard.onTestCompleted(context)
        assertFalse("second test must not show it again", TestDoneCard.visible.value)
    }

    @Test fun `arm A never sees it`() {
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .edit().putString(FirstScreenExperiment.KEY_ARM, "A").commit()
        TestDoneCard.onTestCompleted(context)
        assertFalse(TestDoneCard.visible.value)
    }

    @Test fun `the mic card is not shown before the playback question is answered`() {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        val s = File(dir, "app/src/main/java/com/teamz/lab/debugger/ui/MicTestCard.kt").readText()
        val completed = s.indexOf("AnalyticsEvent.MicTestCompleted")
        val answered = s.indexOf("AnalyticsEvent.MicTestPlaybackAnswered")
        val between = s.substring(completed, answered)
        assertFalse("no card between 'completed' and the answer", between.contains("TestDoneCard.onTestCompleted("))
    }

    @Test fun `all three completion sites notify the card`() {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        val base = "app/src/main/java/com/teamz/lab/debugger/ui/"
        for ((file, event) in listOf(
            // Not MicTestCompleted: that fires before "Did you hear your own voice?", and the card
            // covered the question on the emulator (2026-10-09). The test is done when that is answered.
            "MicTestCard.kt" to "AnalyticsEvent.MicTestPlaybackAnswered",
            "CameraHealthViewModel.kt" to "AnalyticsEvent.CameraHealthCheckCompleted",
            "ScreenTestViewModel.kt" to "AnalyticsEvent.ScreenPixelTestCompleted",
        )) {
            val s = File(dir, base + file).readText()
            val at = s.indexOf(event)
            assertTrue("$file: $event", at >= 0)
            assertTrue("$file must call TestDoneCard.onTestCompleted after $event",
                s.indexOf("TestDoneCard.onTestCompleted(", at) in at until at + 800)
        }
    }
}
