package com.teamz.lab.debugger.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 2026-10-04 code review of the vc49 retention branch. Same defect class the branch fixed —
 * a check that cannot see, reported as a finding — still present in three checks:
 *   - isCameraOrMicActive read DeviceGPT's OWN app-ops (myUid): "🎤🎥 Mic/Camera Active!" and a
 *     Zero Trust warning for everyone who allowed the mic or camera test
 *   - checkDNSManipulation compared only the FIRST address of dns.google / one.one.one.one to
 *     one exact IPv4, but both names also answer 8.8.4.4 / 1.0.0.1 and IPv6 — "⚠️ Your ISP or
 *     Government might be hijacking DNS" on healthy networks
 *   - checkISPTracking warned "your ISP might be tracking" when both probes simply failed (offline)
 * plus the first scan waiting on an unused security-cache warm-up with no timeout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewFixesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app/src/main/java/com/teamz/lab/debugger/$rel").readText()
    }

    @Test
    fun `camera and mic line does not report DeviceGPT's own permission as activity`() {
        val s = isCameraOrMicActive(context)
        assertFalse(s, s.contains("Active"))
        assertFalse(s, s.contains("✅"))
        assertFalse(s, s.contains("🎤"))
    }

    @Test
    fun `zero trust no longer scores camera and mic activity it cannot see`() {
        val s = src("utils/ZeroTrustScorer.kt")
        assertFalse(s.contains("name = \"camera_mic_active\""))
        val sec = s.substring(s.indexOf("private suspend fun evaluateAppPrivacy"), s.indexOf("// ==================== Section 2"))
        assertTrue("app-privacy section must score over the checks that ran", sec.contains("normalizeToRun = true"))
    }

    @Test
    fun `DNS check accepts every published address, warns only on foreign answers`() {
        assertTrue(dnsLineFromAnswers(listOf("8.8.8.8"), listOf("1.1.1.1")).startsWith("✅"))
        assertTrue(dnsLineFromAnswers(listOf("8.8.4.4", "2001:4860:4860::8888"), listOf("2606:4700:4700::1001", "1.0.0.1")).startsWith("✅"))
        val bad = dnsLineFromAnswers(listOf("10.10.34.35"), listOf("1.1.1.1"))
        assertTrue(bad, bad.startsWith("⚠️"))
        assertFalse(bad, bad.contains("Government") || bad.contains("hijack"))
        assertTrue(dnsLineFromAnswers(emptyList(), emptyList()).startsWith("❌"))
    }

    @Test
    fun `ISP check - no response at all is not a finding`() {
        assertTrue(ispLineFromCodes(null, null).startsWith("❌"))
        assertTrue(ispLineFromCodes(204, null).startsWith("✅"))
        assertTrue(ispLineFromCodes(null, 200).startsWith("✅"))
        val redirected = ispLineFromCodes(302, 302)
        assertTrue(redirected, redirected.startsWith("⚠️"))
        assertFalse(redirected, redirected.contains("tracking"))
    }

    @Test
    fun `first scan does not wait on the security cache, and its wait is bounded`() {
        val h = src("utils/health_score_utils.kt")
        val fn = h.substring(h.indexOf("suspend fun calculateDailyHealthScore"), h.indexOf("internal fun scoreFromSignals"))
        assertFalse("score must not run the full security scan it does not use", fn.contains("SecurityInfoCache.refresh"))
        val g = src("ui/FirstScanGateScreen.kt")
        assertTrue(g.contains("withTimeoutOrNull("))
        val gate = src("ui/FirstScanGate.kt")
        assertTrue("recordDailyScan must not swallow cancellation", gate.contains("CancellationException) { throw"))
    }

    @Test
    fun `AI prompts and agent-facing titles do not claim spyware or other apps' mic logs`() {
        val banned = listOf(
            "which apps used these features", "check which apps are accessing my camera",
            "Spyware Detection", "Spyware & Keylogger Check", "Any malicious monitoring detected",
        )
        for (f in listOf("utils/ai_prompt_generator.kt", "appfunctions/PrivacyAppFunctions.kt")) {
            val visible = src(f).lines().filter { line ->
                val t = line.trim()
                f.startsWith("appfunctions") || !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
            }.joinToString("\n")
            for (b in banned) assertFalse("$f still shows \"$b\"", visible.contains(b))
        }
    }

    @Test
    fun `paywall delay default holds before Remote Config defaults land`() {
        val rc = src("utils/RemoteConfigUtils.kt")
        val fn = rc.substring(rc.indexOf("fun isPaywallDelayEnabled"), rc.indexOf("fun getPaywallMinSessions"))
        assertTrue(fn, fn.contains("defaultsApplied"))
    }
}
