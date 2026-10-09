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
        // The copy moved to string resources (Bangla + English, 2026-10-09). The sheet must use
        // these keys, and their English values are still the approved copy.
        val strings = File(f.path.substringBefore("/java/com/teamz/"), "res/values/strings.xml").readText()
        val approved = mapOf(
            "done_title" to "Done",
            "done_body" to "Want to keep an eye on your phone\\'s health?",
            "done_add_widget" to "Add widget",
            "done_weekly_checkup" to "Weekly check-up",
            "not_now" to "Not now",
        )
        for ((key, copy) in approved) {
            assertTrue("sheet must use R.string.$key", s.contains("R.string.$key"))
            assertTrue("$key must read \"$copy\"", strings.contains("<string name=\"$key\">$copy</string>"))
        }
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
