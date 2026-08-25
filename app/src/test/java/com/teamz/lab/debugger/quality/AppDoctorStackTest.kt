package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.AppDoctorContext
import com.teamz.lab.debugger.utils.AppDoctorContext.StackVerdict
import com.teamz.lab.debugger.utils.DomainProbeResult
import com.teamz.lab.debugger.utils.NetworkReachabilityTester
import com.teamz.lab.debugger.utils.ProbeStatus
import com.teamz.lab.debugger.utils.ReachabilityStatus
import com.teamz.lab.debugger.utils.WebViewStackProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Java stack and the Chromium stack can disagree, and the disagreement is the
 * whole diagnosis. These tests pin that the disagreement survives aggregation and
 * reaches the report — a green Java row must never be presented alone.
 *
 * Everything under test is a pure function: the aggregation, the verdict and the
 * report builder were split out of the networked/WebView code precisely so this file
 * needs no device, no socket and no Chromium.
 */
class AppDoctorStackTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun javaOk(dns: Long = 5, https: Long = 100) = DomainProbeResult(
        domain = "interviewboss.net", category = "Custom",
        dnsStatus = ProbeStatus.SUCCESS, dnsResolvedIp = "1.2.3.4", dnsLatencyMs = dns,
        httpsStatus = ProbeStatus.SUCCESS, httpsResponseCode = 200, httpsLatencyMs = https,
        overallStatus = ReachabilityStatus.REACHABLE, errorDetail = null
    )

    private fun javaFail() = DomainProbeResult(
        domain = "interviewboss.net", category = "Custom",
        dnsStatus = ProbeStatus.FAILED, dnsResolvedIp = null, dnsLatencyMs = 0,
        httpsStatus = ProbeStatus.NOT_TESTED, httpsResponseCode = null, httpsLatencyMs = 0,
        overallStatus = ReachabilityStatus.DNS_BLOCKED, errorDetail = "DNS resolution failed"
    )

    private fun webOk(ms: Long = 300) =
        WebViewStackProbe.Attempt(WebViewStackProbe.Outcome.OK, null, null, ms)

    private fun webErr(code: Int, desc: String) =
        WebViewStackProbe.Attempt(WebViewStackProbe.Outcome.ERROR, code, desc, 120)

    private fun webTimeout() = WebViewStackProbe.Attempt(
        WebViewStackProbe.Outcome.TIMEOUT, null, "No response within 12s", 12_000
    )

    // ── verdict logic ────────────────────────────────────────────────────────

    @Test
    fun `java passing while webview fails is the headline diagnostic case`() {
        assertEquals(
            StackVerdict.WEBVIEW_ONLY_FAILS,
            AppDoctorContext.compareStacks(javaSuccessCount = 4, webViewSuccessCount = 0)
        )
    }

    @Test
    fun `an intermittent webview pass still counts as reachable`() {
        // 1 of 4 proves the path exists. Intermittency is reported by the summary line,
        // not by pretending the path is dead.
        assertEquals(
            StackVerdict.BOTH_OK,
            AppDoctorContext.compareStacks(4, 1)
        )
    }

    @Test
    fun `remaining verdict combinations`() {
        assertEquals(StackVerdict.BOTH_OK, AppDoctorContext.compareStacks(1, 1))
        assertEquals(StackVerdict.BOTH_FAIL, AppDoctorContext.compareStacks(0, 0))
        assertEquals(StackVerdict.JAVA_ONLY_FAILS, AppDoctorContext.compareStacks(0, 3))
        assertEquals(StackVerdict.INCOMPLETE, AppDoctorContext.compareStacks(4, null))
        assertEquals(StackVerdict.INCOMPLETE, AppDoctorContext.compareStacks(null, 4))
    }

    // ── WebView aggregation, including the timeout path ──────────────────────

    @Test
    fun `webview timeout aggregates as failure and keeps its description`() {
        val agg = WebViewStackProbe.aggregate(
            "interviewboss.net", listOf(webTimeout(), webTimeout(), webTimeout(), webTimeout())
        )
        assertTrue(agg.allFailed)
        assertFalse(agg.isIntermittent)
        assertEquals(0L, agg.avgLatencyMs)
        assertEquals(WebViewStackProbe.Outcome.TIMEOUT, agg.firstFailure?.outcome)
        assertTrue(
            "the timeout reason must reach the summary line",
            agg.summaryLine.contains("0/4 OK") && agg.summaryLine.contains("No response")
        )
    }

    @Test
    fun `webview partial success is flagged intermittent and averages only passes`() {
        val agg = WebViewStackProbe.aggregate(
            "interviewboss.net",
            listOf(webOk(200), webErr(-2, "net::ERR_NAME_NOT_RESOLVED"), webOk(400), webOk(300))
        )
        assertEquals(3, agg.successCount)
        assertTrue(agg.isIntermittent)
        assertEquals(300L, agg.avgLatencyMs)
        assertEquals(-2, agg.firstFailure?.errorCode)
    }

    @Test
    fun `empty webview attempt list does not throw or claim failure`() {
        val agg = WebViewStackProbe.aggregate("interviewboss.net", emptyList())
        assertEquals(0, agg.attempts)
        assertFalse(agg.allFailed)
        assertEquals("Not tested", agg.summaryLine)
    }

    // ── the disagreement must reach the report ───────────────────────────────

    @Test
    fun `report surfaces a java-pass webview-fail disagreement in words`() {
        val java = NetworkReachabilityTester.aggregateAttempts(
            "interviewboss.net", "Custom", List(4) { javaOk() }
        )
        val web = WebViewStackProbe.aggregate(
            "interviewboss.net", List(4) { webErr(-2, "net::ERR_NAME_NOT_RESOLVED") }
        )
        val report = NetworkReachabilityTester.buildProbeReport(
            java, "8.8.8.8", false, false,
            webView = web,
            webViewPackage = "com.google.android.webview" to "121.0.6167.143",
            transportLabel = "Wi-Fi",
            captivePortal = false
        )
        assertTrue("both rows must appear", report.contains("Java stack"))
        assertTrue(report.contains("WebView stack"))
        assertTrue("verdict must be named", report.contains("WEBVIEW_ONLY_FAILS"))
        assertTrue("the chromium error must survive", report.contains("ERR_NAME_NOT_RESOLVED"))
        assertTrue("must explain why this matters", report.contains("Chromium does not use"))
        assertTrue("webview build is the key field for a shell app",
            report.contains("121.0.6167.143"))
        assertTrue(report.contains("Transport: Wi-Fi"))
    }

    @Test
    fun `report never hides a missing webview measurement`() {
        // Omitting the row would read as "fine". It must say it was not measured.
        val java = NetworkReachabilityTester.aggregateAttempts(
            "interviewboss.net", "Custom", List(4) { javaOk() }
        )
        val report = NetworkReachabilityTester.buildProbeReport(java, null, null, null)
        assertTrue(report.contains("WebView stack: not measured"))
        assertTrue(report.contains("System WebView: unknown"))
        assertTrue(report.contains("Transport: unknown"))
        assertTrue(report.contains("Captive portal (sign-in wall): unknown"))
    }

    @Test
    fun `both stacks failing is not mislabelled as the webview-only case`() {
        val java = NetworkReachabilityTester.aggregateAttempts(
            "interviewboss.net", "Custom", List(4) { javaFail() }
        )
        val web = WebViewStackProbe.aggregate("interviewboss.net", List(4) { webTimeout() })
        val report = NetworkReachabilityTester.buildProbeReport(
            java, "8.8.8.8", true, false, webView = web
        )
        assertTrue(report.contains("BOTH_FAIL"))
        assertFalse(
            "must not print the WebView-only explainer when both stacks are down",
            report.contains("Chromium does not use")
        )
    }

    // ── transport / carrier degraded paths ───────────────────────────────────

    @Test
    fun `transport label degrades to unknown rather than lying`() {
        // What readTransport returns when ConnectivityManager gave nothing back, e.g.
        // permission denied or no active network.
        val t = AppDoctorContext.Transport(
            wifi = false, cellular = false, vpn = false, carrier = null
        )
        assertEquals("unknown", t.label)
    }

    @Test
    fun `carrier absent still names the transport`() {
        // READ_PHONE_STATE denied at runtime, or a Wi-Fi-only tablet: the carrier is
        // null but "Mobile data" is still a true and useful statement.
        val t = AppDoctorContext.Transport(
            wifi = false, cellular = true, vpn = false, carrier = null
        )
        assertEquals("Mobile data", t.label)
    }

    @Test
    fun `carrier and vpn are both named when present`() {
        val t = AppDoctorContext.Transport(
            wifi = true, cellular = false, vpn = true, carrier = "Grameenphone"
        )
        assertEquals("Wi-Fi + VPN", t.label)

        val cell = AppDoctorContext.Transport(
            wifi = false, cellular = true, vpn = false, carrier = "Grameenphone"
        )
        assertEquals("Mobile data (Grameenphone)", cell.label)
    }

    @Test
    fun `blank carrier string is treated as absent, not printed as empty parens`() {
        val t = AppDoctorContext.Transport(
            wifi = false, cellular = true, vpn = false, carrier = ""
        )
        assertEquals("Mobile data", t.label)
    }
}
