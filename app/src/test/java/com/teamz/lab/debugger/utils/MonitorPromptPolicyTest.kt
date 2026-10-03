package com.teamz.lab.debugger.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-10-04: the "Allow Realtime Monitor" notification pre-prompt opened on every cold start
 * for Android 13+ users without the permission — including the first landing, seconds after
 * the first scan, on the screen where half of real users uninstall within 15 minutes. It now
 * waits until the install is a day old.
 */
class MonitorPromptPolicyTest {

    private val hour = 60 * 60 * 1000L

    @Test
    fun `not offered on the install day`() {
        assertFalse(MonitorPromptPolicy.shouldOffer(installAgeMs = 0L))
        assertFalse(MonitorPromptPolicy.shouldOffer(installAgeMs = 23 * hour))
    }

    @Test
    fun `offered from day two`() {
        assertTrue(MonitorPromptPolicy.shouldOffer(installAgeMs = 24 * hour))
        assertTrue(MonitorPromptPolicy.shouldOffer(installAgeMs = 400 * 24 * hour))
    }

    @Test
    fun `an unreadable install time does not block existing users`() {
        assertTrue(MonitorPromptPolicy.shouldOffer(installAgeMs = null))
    }

    private fun src(path: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, path).readText()
    }

    @Test
    fun `the auto-start path asks the policy before showing the dialog`() {
        val s = src("app/src/main/java/com/teamz/lab/debugger/ui/adaptive/DeviceGptNavExperience.kt")
        val fn = s.substring(s.indexOf("fun HandleSystemMonitorAutoStart"))
        val gate = fn.indexOf("MonitorPromptPolicy.shouldOffer(")
        val show = fn.indexOf("showDialog = true")
        assertTrue("policy must be checked before showDialog = true", gate in 0 until show)
    }

    @Test
    fun `the don't-ask-again checkbox is in the dialog body, not squeezed beside the buttons`() {
        val s = src("app/src/main/java/com/teamz/lab/debugger/ui/drawer.kt")
        val fn = s.substring(s.indexOf("fun NotificationPermissionDialog"), s.indexOf("fun NotificationToggle"))
        val body = fn.indexOf("text = {")
        val confirm = fn.indexOf("confirmButton = {")
        val checkbox = fn.indexOf("Don't ask me again")
        assertTrue("checkbox must render inside text = { }", checkbox in body until confirm)
    }
}
