package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirstScreenChooserWiringTest {
    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app/src/main/java/com/teamz/lab/debugger/$rel").readText()
    }

    @Test fun `score screen shows the chooser only in arm B, with the approved copy`() {
        val s = src("ui/FirstScanGateScreen.kt")
        assertTrue(s.contains("onChooseTest: ((String) -> Unit)? = null"))
        assertTrue(s.contains("FirstScreenExperiment.isB(context)"))
        for (copy in listOf("What do you want to test?", "\"Camera\"", "\"Mic\"", "\"Screen\"", "See full health report"))
            assertTrue(copy, s.contains(copy))
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
