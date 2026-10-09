package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.FirstScreenExperiment
import com.teamz.lab.debugger.utils.TestDoneCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // Review 2026-10-09 I1: vc51 upgraders and failed scans never saw the score screen. A finished
    // test must not put them in the experiment or show them the card.
    @Test fun `a user with no arm gets no card and no arm`() {
        TestDoneCard.onTestCompleted(context)
        assertFalse(TestDoneCard.visible.value)
        assertNull(context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .getString(FirstScreenExperiment.KEY_ARM, null))
    }

    // Review 2026-10-09 M1: the mic test fires an interstitial right after completion; it must
    // know whether the card just opened so the ad does not land on top of it.
    @Test fun `onTestCompleted reports whether it opened the card`() {
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .edit().putString(FirstScreenExperiment.KEY_ARM, "B").commit()
        assertTrue(TestDoneCard.onTestCompleted(context))
        TestDoneCard.dismiss()
        assertFalse(TestDoneCard.onTestCompleted(context))
    }

    @Test fun `the mic interstitial is skipped when the card just opened`() {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        val s = File(dir, "app/src/main/java/com/teamz/lab/debugger/ui/MicTestCard.kt").readText()
        assertTrue(Regex("""val cardShown = com\.teamz\.lab\.debugger\.utils\.TestDoneCard\.onTestCompleted\(context\)""").containsMatchIn(s))
        assertTrue(Regex("""if \(!cardShown\) activity\?\.let""").containsMatchIn(s))
    }

    // Re-review minor 5: the sheet's LaunchedEffect re-runs on rotation; one showing = one event.
    @Test fun `the shown event is counted once per showing`() {
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .edit().putString(FirstScreenExperiment.KEY_ARM, "B").commit()
        TestDoneCard.onTestCompleted(context)
        assertTrue(TestDoneCard.claimShownLog())
        assertFalse("rotation must not count again", TestDoneCard.claimShownLog())
    }

    @Test fun `excluded users never see the card`() {
        context.getSharedPreferences(FirstScreenExperiment.PREFS, Context.MODE_PRIVATE)
            .edit().putString(FirstScreenExperiment.KEY_ARM, "X").commit()
        assertFalse(TestDoneCard.onTestCompleted(context))
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
