package com.teamz.lab.debugger.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 2026-10-04, release build on a clean emulator: Health → Privacy Dashboard showed
 * "Privacy Score 60/100 · Threats Today: ⚠ Screen recording or suspicious apps detected".
 *   - monitoring: matched contains("Screen Recording") / ("Suspicious") against text whose own
 *     section HEADERS contain those words → −20 and the threat, for every user
 *   - "DPI": flagged contains("packet loss") on `ping -c 1` output, whose summary always says
 *     "0% packet loss" → "⚠️ Your ISP might be blocking VPNs or inspecting traffic!" and −10
 *     for every user who can ping (and Network Trust: "a VPN can help")
 *   - SSL: "❌ Unable to check (no internet)" counted as a certificate threat
 *   - mic/camera: `logcat -d` only returns DeviceGPT's own log, so the app's own mic/camera
 *     test could raise "Recent microphone or camera access detected"
 *   - spoofing: a real detection returns "🚫 …", which the ⚠️/❌ test never matched
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyFalseAlarmTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun signals(
        screenCapture: Boolean = false, usb: Boolean = false, rooted: Boolean = false,
        ssl: Boolean = false, spoof: Boolean = false,
    ) = PrivacySignals(
        screenCaptureExposed = screenCapture, usbDebugging = usb, rooted = rooted,
        suspiciousSslCertificate = ssl, spoofingAppInstalled = spoof,
    )

    @Test
    fun `a clean device scores 100 with no threats`() {
        assertEquals(100, privacyScoreFromSignals(signals()))
        assertTrue(privacyThreatsFromSignals(signals()).isEmpty())
    }

    @Test
    fun `each real finding still counts`() {
        assertEquals(80, privacyScoreFromSignals(signals(screenCapture = true)))
        assertEquals(90, privacyScoreFromSignals(signals(usb = true)))
        assertEquals(85, privacyScoreFromSignals(signals(rooted = true)))
        assertEquals(90, privacyScoreFromSignals(signals(ssl = true)))
        assertEquals(90, privacyScoreFromSignals(signals(spoof = true)))
        assertEquals(5, privacyThreatsFromSignals(
            signals(screenCapture = true, usb = true, rooted = true, ssl = true, spoof = true)).size)
    }

    @Test
    fun `ping summary is not a DPI finding`() {
        val ok = "1 packets transmitted, 1 received, 0% packet loss, time 0ms"
        val blocked = "1 packets transmitted, 0 received, 100% packet loss, time 0ms"
        assertTrue(dpiLineFromPing(ok).startsWith("✅"))
        for (out in listOf(blocked, "", "ping: unknown host")) {
            val line = dpiLineFromPing(out)
            assertFalse(line, line.contains("⚠️"))
            assertFalse(line, line.contains("ISP"))
        }
    }

    @Test
    fun `an SSL check that could not run is not a suspicious certificate`() {
        assertFalse(isSuspiciousSslResult("❌ Unable to Check SSL Certificates (No internet connection)"))
        assertTrue(isSuspiciousSslResult("⚠️ Suspicious SSL Certificate Detected! Possible MITM Attack!"))
        assertFalse(isSuspiciousSslResult("✅ SSL Certificates Appear Normal"))
    }

    @Test
    fun `mic and camera text does not claim to see other apps`() {
        val s = getRecentCameraMicUsageLog()
        assertFalse(s, s.contains("✅"))
        assertFalse(s, s.contains("Recent usage detected"))
    }

    @Test
    fun `the live dashboard on a clean device raises no false threat`() {
        val threats = getPrivacyThreatsToday(context)
        assertFalse(threats.toString(), threats.any { it.contains("Screen recording") || it.contains("SSL") || it.contains("microphone") })
    }

    @Test
    fun `no consumer matches section headers or the mic log`() {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        val s = File(dir, "app/src/main/java/com/teamz/lab/debugger/utils/device_utils.kt").readText()
        assertFalse(s.contains("monitoringStatus.contains(\"Screen Recording\")"))
        assertFalse(s.contains("recentUsage.contains(\"Recent usage detected\")"))
    }
}
