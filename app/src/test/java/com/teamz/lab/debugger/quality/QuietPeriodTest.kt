package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.QuietPeriod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-10-09 user research (GA4, robots filtered, 90 days, 240 new users):
 * - "session" in this app = MainActivity.onCreate >=60 s apart, NOT a day, so every
 *   "wait until session N" gate (full-screen ads, cold paywall) opens within the first hour;
 *   3/3 humans who saw a day-0 full-screen ad uninstalled that day (CHURN-DIAGNOSIS-2026-10-07);
 * - 86% of new users saw a paywall, 6.4 times each; the first scan fired one on day 0 ungated;
 * - the largest paywall audience was Iran (46 users, 470 shows), where Play billing does not work;
 * - the Real-time Monitor started itself for 48% of new users; 5 ever chose it.
 * Rule: the first 72 hours after install are quiet — measured by install age, not sessions.
 */
class QuietPeriodTest {

    private val h = 3_600_000L

    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app/src/main/java/com/teamz/lab/debugger/$rel").readText()
    }

    @Test
    fun `quiet for the first 72 hours by install age`() {
        assertTrue(QuietPeriod.isActive(0L))
        assertTrue(QuietPeriod.isActive(45 * 60_000L))          // the "session 3 within an hour" case
        assertTrue(QuietPeriod.isActive(72 * h - 1))
        assertFalse(QuietPeriod.isActive(72 * h))
        assertFalse("unknown install time must not silence monetisation forever", QuietPeriod.isActive(null))
        assertFalse("clock moved backwards", QuietPeriod.isActive(-5 * h))
    }

    @Test
    fun `no unsolicited paywall where Play billing does not work`() {
        for (c in listOf("IR", "ir", "CU", "KP", "SY")) assertTrue(c, QuietPeriod.billingUnavailable(c))
        for (c in listOf("BD", "US", "IN", "")) assertFalse(c, QuietPeriod.billingUnavailable(c))
        assertFalse(QuietPeriod.unsolicitedPaywallAllowed(installAgeMs = 100 * h, country = "IR"))
        assertFalse(QuietPeriod.unsolicitedPaywallAllowed(installAgeMs = 1 * h, country = "US"))
        assertTrue(QuietPeriod.unsolicitedPaywallAllowed(installAgeMs = 100 * h, country = "US"))
    }

    @Test
    fun `full-screen ad gates check the quiet period, so they neither show nor request`() {
        val rc = src("utils/RemoteConfigUtils.kt")
        for (fn in listOf("fun shouldShowInterstitialAds(): Boolean {", "fun shouldShowAppOpenAds(): Boolean {")) {
            val start = rc.indexOf(fn); assertTrue(fn, start >= 0)
            val body = rc.substring(start, rc.indexOf("\n    }", start))
            assertTrue("$fn must consult QuietPeriod", body.contains("QuietPeriod.activeNow()"))
        }
    }

    @Test
    fun `unsolicited paywalls consult the quiet period`() {
        val pp = src("utils/PaywallPolicy.kt")
        for (fn in listOf("fun coldTriggerAllowed(", "fun delightTriggerAllowed(")) {
            val start = pp.indexOf(fn)
            val body = pp.substring(start, pp.indexOf("\n    }", start))
            assertTrue("$fn must consult QuietPeriod", body.contains("QuietPeriod.unsolicitedPaywallAllowed()"))
        }
        val nav = src("ui/adaptive/DeviceGptNavExperience.kt")
        val scan = nav.indexOf("onScanComplete = {")
        val trigger = nav.indexOf("paywallAnalyticsSource = \"health_scan_complete\"", scan)
        assertTrue("first-scan paywall must be gated", nav.substring(scan, trigger).contains("QuietPeriod.unsolicitedPaywallAllowed()"))
    }

    @Test
    fun `the monitor never starts unless the user turned it on`() {
        val nav = src("ui/adaptive/DeviceGptNavExperience.kt")
        val start = nav.indexOf("private fun startService(context: Context) {")
        val body = nav.substring(start, nav.indexOf("\n}", start))
        assertFalse("first launch must not start the monitor", body.contains("isUserFirstTime()"))
        assertTrue("the monitor starts only behind the user's opt-in",
            Regex("""if \(context\.isUserEnableMonitoringService\(\)\) \{\s*context\.startSystemMonitorService\(\)""").containsMatchIn(body))
        val auto = nav.indexOf("fun HandleSystemMonitorAutoStart()")
        val autoBody = nav.substring(auto, nav.indexOf("NotificationPermissionDialog(", auto))
        assertFalse("permission already granted must not mean 'start it'", autoBody.contains("} else {\n                startService(context)"))
    }
}
