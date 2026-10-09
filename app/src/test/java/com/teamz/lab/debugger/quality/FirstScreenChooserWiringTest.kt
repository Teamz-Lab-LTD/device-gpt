package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirstScreenChooserWiringTest {
    private fun appDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app")
    }
    private fun src(rel: String): String =
        File(appDir(), "src/main/java/com/teamz/lab/debugger/$rel").readText()

    @Test fun `score screen shows the chooser only in arm B, with the approved copy`() {
        val s = src("ui/FirstScanGateScreen.kt")
        assertTrue(s.contains("onChooseTest: ((String) -> Unit)? = null"))
        // The score screen is the one place that assigns the arm (remembered once per screen).
        assertTrue(s.contains("remember { com.teamz.lab.debugger.utils.FirstScreenExperiment.arm(context) } == \"B\""))
        // The copy moved to string resources (Bangla + English, 2026-10-09). The screen must use
        // these keys, and their English values are still the approved experiment copy.
        val strings = File(appDir(), "src/main/res/values/strings.xml").readText()
        val approved = mapOf(
            "first_scan_chooser_title" to "What do you want to test?",
            "tab_camera" to "Camera",
            "first_scan_choice_mic" to "Mic",
            "first_scan_choice_screen" to "Screen",
            "first_scan_full_report" to "See full health report",
        )
        for ((key, copy) in approved) {
            assertTrue("screen must use R.string.$key", s.contains("R.string.$key"))
            assertTrue("$key must read \"$copy\"", strings.contains("<string name=\"$key\">$copy</string>"))
        }
        // The choice keys the router and analytics read are not translated.
        for (choice in listOf("to \"camera\"", "to \"mic\"", "to \"screen\"")) assertTrue(choice, s.contains(choice))
    }

    @Test fun `MainActivity routes the choice and skips the score-phase widget prompt in B`() {
        val m = src("MainActivity.kt")
        assertTrue(m.contains("onChooseTest = { choice ->"))
        assertTrue(m.contains("FirstScreenExperiment.tabFor(choice)"))
        assertTrue(m.contains("AnalyticsEvent.FsTestChosen"))
        val prompts = Regex("""if \(!com\.teamz\.lab\.debugger\.utils\.FirstScreenExperiment\.isB\(this@MainActivity\)\)\s*com\.teamz\.lab\.debugger\.utils\.WidgetPinPrompt\.maybePrompt""").findAll(m).count()
        assertTrue("both score-phase prompts must be guarded (found $prompts)", prompts >= 2)
    }
}
