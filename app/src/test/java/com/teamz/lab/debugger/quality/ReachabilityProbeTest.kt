package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.DomainProbeResult
import com.teamz.lab.debugger.utils.NetworkReachabilityTester
import com.teamz.lab.debugger.utils.ProbeStatus
import com.teamz.lab.debugger.utils.ReachabilityStatus
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the three things the App Doctor "Definition of done" calls out for
 * Release 1: RC fallback to bundled defaults, the 4-attempt aggregation, and the
 * degraded paths where input or network is not usable.
 *
 * All three targets are PURE functions on purpose — the parser, the aggregator and
 * the hostname normaliser were split out of the networked code specifically so this
 * file needs neither a device, a live RemoteConfig, nor a socket.
 */
class ReachabilityProbeTest {

    // ── RC fallback to bundled defaults ──────────────────────────────────────

    @Test
    fun `blank remote value parses to nothing so the caller falls back`() {
        assertTrue(RemoteConfigUtils.parseReachabilityDomains("").isEmpty())
        assertTrue(RemoteConfigUtils.parseReachabilityDomains("   ").isEmpty())
    }

    @Test
    fun `bundled default list parses to the ten curated domains`() {
        val parsed = RemoteConfigUtils.parseReachabilityDomains(
            "Search|www.google.com,Video|www.youtube.com," +
                "Messaging|web.whatsapp.com,Messaging|telegram.org," +
                "Social|www.instagram.com,Developer|github.com," +
                "Cloud|drive.google.com,DNS|dns.google," +
                "DNS|one.one.one.one,CDN|speed.cloudflare.com"
        )
        assertEquals(10, parsed.size)
        assertEquals("Search" to "www.google.com", parsed.first())
        assertEquals("CDN" to "speed.cloudflare.com", parsed.last())
    }

    @Test
    fun `garbage entries are skipped without killing the whole list`() {
        // One good entry surrounded by every malformed shape a console typo can take.
        val parsed = RemoteConfigUtils.parseReachabilityDomains(
            "no-pipe-here,|,Cat|,|domain.com,Good|example.com,Bad|not a host,Bad|http://x.com"
        )
        assertEquals(listOf("Good" to "example.com"), parsed)
    }

    @Test
    fun `a fully malformed remote value yields empty so the fallback can engage`() {
        // This is the case that matters: if this returned a partial or bogus list,
        // the reachability test would render a 0% openness score and tell the user
        // their entire network is blocked.
        assertTrue(RemoteConfigUtils.parseReachabilityDomains("!!!,???,,,").isEmpty())
    }

    @Test
    fun `parser normalises case and whitespace`() {
        val parsed = RemoteConfigUtils.parseReachabilityDomains("  Search | WWW.Google.COM  ")
        assertEquals(listOf("Search" to "www.google.com"), parsed)
    }

    // ── 4-attempt aggregation ────────────────────────────────────────────────

    private fun ok(dns: Long, https: Long) = DomainProbeResult(
        domain = "example.com", category = "Custom",
        dnsStatus = ProbeStatus.SUCCESS, dnsResolvedIp = "1.2.3.4", dnsLatencyMs = dns,
        httpsStatus = ProbeStatus.SUCCESS, httpsResponseCode = 200, httpsLatencyMs = https,
        overallStatus = ReachabilityStatus.REACHABLE, errorDetail = null
    )

    private fun fail(status: ReachabilityStatus, detail: String) = DomainProbeResult(
        domain = "example.com", category = "Custom",
        dnsStatus = ProbeStatus.FAILED, dnsResolvedIp = null, dnsLatencyMs = 0,
        httpsStatus = ProbeStatus.NOT_TESTED, httpsResponseCode = null, httpsLatencyMs = 0,
        overallStatus = status, errorDetail = detail
    )

    @Test
    fun `three of four passing reports as intermittent, not as healthy`() {
        // The whole reason this feature exists. A single probe would have hit one of
        // the three good attempts and shown a green tick.
        val r = NetworkReachabilityTester.aggregateAttempts(
            "example.com", "Custom",
            listOf(
                ok(10, 190),
                fail(ReachabilityStatus.TCP_BLOCKED, "Connection timeout"),
                ok(10, 240),
                ok(10, 290)
            )
        )
        assertEquals(4, r.attempts)
        assertEquals(3, r.successCount)
        assertTrue("partial success must read as intermittent", r.isIntermittent)
        assertFalse(r.allFailed)
        assertEquals("3/4 OK, avg 250ms", r.summaryLine)
        assertNotNull("the failing attempt's error must survive aggregation", r.errorDetail)
    }

    @Test
    fun `latency averages over successful attempts only`() {
        // A failed attempt contributes 0ms; letting it into the mean would understate
        // real latency and hide a slow-but-working host.
        val r = NetworkReachabilityTester.aggregateAttempts(
            "example.com", "Custom",
            listOf(ok(0, 100), ok(0, 300), fail(ReachabilityStatus.DNS_BLOCKED, "DNS resolution failed"))
        )
        assertEquals(200L, r.avgLatencyMs)
        assertEquals(100L, r.minLatencyMs)
        assertEquals(300L, r.maxLatencyMs)
    }

    @Test
    fun `all passing is not flagged intermittent`() {
        val r = NetworkReachabilityTester.aggregateAttempts(
            "example.com", "Custom", List(4) { ok(5, 100) }
        )
        assertEquals(4, r.successCount)
        assertFalse(r.isIntermittent)
        assertEquals(ReachabilityStatus.REACHABLE, r.overallStatus)
        assertNull(r.errorDetail)
    }

    @Test
    fun `total failure reports the dominant failure mode, not the first one`() {
        val r = NetworkReachabilityTester.aggregateAttempts(
            "example.com", "Custom",
            listOf(
                fail(ReachabilityStatus.NETWORK_ERROR, "Connection error"),
                fail(ReachabilityStatus.DNS_BLOCKED, "DNS resolution failed"),
                fail(ReachabilityStatus.DNS_BLOCKED, "DNS resolution failed"),
                fail(ReachabilityStatus.DNS_BLOCKED, "DNS resolution failed")
            )
        )
        assertTrue(r.allFailed)
        assertFalse(r.isIntermittent)
        assertEquals(ReachabilityStatus.DNS_BLOCKED, r.overallStatus)
        assertEquals("0/4 OK", r.summaryLine)
        assertEquals(0L, r.avgLatencyMs)
    }

    @Test
    fun `empty attempt list degrades to NOT_TESTED rather than throwing`() {
        val r = NetworkReachabilityTester.aggregateAttempts("example.com", "Custom", emptyList())
        assertEquals(0, r.attempts)
        assertEquals(ReachabilityStatus.NOT_TESTED, r.overallStatus)
        assertFalse(r.isIntermittent)
        assertFalse("zero attempts is not a failure verdict", r.allFailed)
        assertEquals("Not tested", r.summaryLine)
    }

    // ── Degraded / unusable-input paths ──────────────────────────────────────

    @Test
    fun `user domain normaliser strips scheme path port and case`() {
        val n = NetworkReachabilityTester::normaliseUserDomain
        assertEquals("interviewboss.net", n("https://interviewboss.net/login?x=1"))
        assertEquals("interviewboss.net", n("  InterviewBoss.NET  "))
        assertEquals("example.com", n("http://example.com:8443/a/b#frag"))
        assertEquals("example.com", n("example.com."))
    }

    @Test
    fun `user domain normaliser rejects anything that is not a hostname`() {
        val n = NetworkReachabilityTester::normaliseUserDomain
        // Each of these would otherwise be sent off-device verbatim as a DNS query.
        assertNull(n(""))
        assertNull(n("   "))
        assertNull(n("localhost"))          // no dot — not a probeable public host
        assertNull(n("not a domain"))
        assertNull(n("-bad.example.com"))
        assertNull(n("bad-.example.com"))
        assertNull(n("exam_ple.com"))
        assertNull(n("https://"))
        assertNull(n("a".repeat(300) + ".com"))
    }
}
