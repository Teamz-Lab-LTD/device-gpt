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

    @Test fun `all three completion sites notify the card`() {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        val base = "app/src/main/java/com/teamz/lab/debugger/ui/"
        for ((file, event) in listOf(
            "MicTestCard.kt" to "AnalyticsEvent.MicTestCompleted",
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
