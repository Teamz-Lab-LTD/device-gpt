package com.teamz.lab.debugger.utils

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 2026-10-04: the Security text and the Health suggestions built from it told phones with no
 * faults that something was wrong. Confirmed on an Android 15 emulator, a healthy device got
 * "Your phone's security system is turned off ... Contact your phone's customer support",
 * directly above "Great job! Your phone is in excellent shape". Every source was a check that
 * could not see, a normal state reported as a fault, or a placeholder:
 *   - SELinux: apps get "Permission denied" from getenforce; empty output read as "off"
 *   - device admin: none (the normal state) read as "❌ No admin set", with advice to go
 *     and grant device-admin access to fix it
 *   - tamper: /etc/hosts is on every Android device, listed as a modified system file
 *   - motion: "phone moved while locked" whenever /sys/class/input exists (always) —
 *     the code said "Simulated for illustration"
 *   - clipboard: claimed Android 11 auto-clears the clipboard (it does not)
 * The Suggestions `when` shows only its first match, so all of them go together, or the next
 * false branch takes the slot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityFalseAlarmTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `SELinux - unreadable is not off`() {
        assertTrue(selinuxStatusLine("Enforcing").startsWith("✅"))
        assertTrue(selinuxStatusLine("Permissive").contains("❌ Security shield is off"))
        for (unreadable in listOf("", null, "getenforce: Couldn't get enforcing status: Permission denied")) {
            val line = selinuxStatusLine(unreadable)
            assertFalse(line, line.contains("❌"))
            assertFalse(line, line.contains("Security shield is off"))
        }
    }

    @Test
    fun `device admin - none is the safe state, some are named not alarmed`() {
        val none = deviceAdminLine(emptyList())
        assertTrue(none, none.startsWith("✅"))
        val some = deviceAdminLine(listOf("Work Profile", "Find My Device"))
        assertTrue(some, some.contains("Work Profile") && some.contains("Find My Device"))
        for (line in listOf(none, some)) {
            assertFalse(line, line.contains("❌"))
            assertFalse(line, line.contains("owner"))
            // keep admin text neutral: it is shown inside the security summary
            assertFalse(line, line.contains("Yes") || line.contains("Enabled"))
        }
    }

    @Test
    fun `tamper check does not flag files every phone has`() {
        // /etc/hosts exists on the machine running this test, as it does on every Android phone
        assertTrue(File("/etc/hosts").exists())
        assertTrue(checkSystemTampering().startsWith("✅"))
    }

    @Test
    fun `clipboard copy matches what Android actually does`() {
        assertTrue(clipboardLine(Build.VERSION_CODES.Q).startsWith("✅"))
        assertTrue(clipboardLine(Build.VERSION_CODES.P).startsWith("⚠️"))
        for (sdk in listOf(Build.VERSION_CODES.P, Build.VERSION_CODES.Q, Build.VERSION_CODES.R)) {
            assertFalse(clipboardLine(sdk).contains("auto-clear"))
        }
    }

    @Test
    fun `security text on a fault-free device raises no alarm`() {
        val info = runBlocking { getSecurityInfo(context) }
        assertFalse(info, info.contains("moved while locked"))
        assertFalse(info, info.contains("Motion While Locked"))
        assertFalse(info, info.contains("No admin set"))
        assertFalse(info, info.contains("Security shield is off"))
        assertFalse(info, info.contains("Modified system files"))
        // headers must not name a detection the app does not perform
        assertFalse(info, info.contains("Malware Scan"))
        assertFalse(info, info.contains("Spy Detection"))
    }

    @Test
    fun `health suggestions on a fault-free device carry no false alarm`() {
        runBlocking { SecurityInfoCache.refresh(context) }
        val all = HealthScoreUtils.getImprovementSuggestions(context, 10).joinToString("\n")
        for (alarm in listOf(
            "security system is turned off", "customer support",
            "doesn't recognize you as the owner", "Device admin apps to fix this",
            "touching your phone", "system has been changed",
        )) assertFalse("suggestion contains '$alarm':\n$all", all.contains(alarm))
    }

    private fun src(path: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, path).readText()
    }

    @Test
    fun `no surface claims a motion-while-locked capability`() {
        val main = "app/src/main/java/com/teamz/lab/debugger/utils/"
        for (f in listOf("device_utils.kt", "health_score_utils.kt", "ai_prompt_generator.kt")) {
            val s = src(main + f)
            assertFalse(f, s.contains("moved while locked") || s.contains("move when it was locked"))
            assertFalse(f, s.contains("detectMotionWhileLocked"))
        }
        assertFalse(src(main + "health_score_utils.kt").contains("DANGER: Bad apps detected"))
    }

    @Test
    fun `zero trust does not warn about a check it could not run`() {
        val s = src("app/src/main/java/com/teamz/lab/debugger/utils/ZeroTrustScorer.kt")
        assertTrue("SELinux check must branch on an unreadable status", s.contains("Not checked"))
        val guard = s.indexOf("if (!selinuxNotChecked)")
        val verdict = s.indexOf("val selinuxEnforced")
        assertTrue("the SELinux verdict must sit inside the not-checked guard", guard in 0 until verdict)
    }
}
