package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestDoneSheetWiringTest {
    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app/src/main/java/com/teamz/lab/debugger/$rel").readText()
    }

    @Test fun `sheet has the approved copy and three actions`() {
        val f = File(File(System.getProperty("user.dir") ?: ".").let { d -> var x = d; while (!File(x, "settings.gradle.kts").exists() && x.parentFile != null) x = x.parentFile; x },
            "app/src/main/java/com/teamz/lab/debugger/ui/TestDoneSheet.kt")
        assertTrue("TestDoneSheet.kt must exist", f.exists())
        val s = f.readText()
        for (c in listOf("Done ✓", "Want to keep an eye on your phone's health?", "Add widget", "Weekly check-up", "Not now"))
            assertTrue(c, s.contains(c))
        assertTrue(s.contains("WidgetPinPrompt.requestNow("))
        assertTrue(s.contains("Manifest.permission.POST_NOTIFICATIONS"))
        assertTrue(s.contains("AnalyticsEvent.FsNotifPermissionResult"))
        assertTrue(s.contains("TestDoneCard.visible.collectAsState()"))
    }

    @Test fun `main screen renders the sheet`() {
        assertTrue(src("ui/adaptive/DeviceGptNavExperience.kt").contains("TestDoneSheet()"))
    }

    @Test fun `user-initiated widget pin does not depend on the prompt flags`() {
        val w = src("utils/WidgetPinPrompt.kt")
        val start = w.indexOf("fun requestNow(")
        assertTrue("requestNow must exist", start >= 0)
        val body = w.substring(start, w.indexOf("\n    }", start).let { if (it < 0) w.length else it })
        assertTrue(!body.contains("isWidgetPinPromptEnabled") && !body.contains("KEY_PROMPTED"))
    }
}
