package com.teamz.lab.debugger.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

// --- Data models ---

enum class ProbeStatus { SUCCESS, FAILED, ERROR, NOT_TESTED }

enum class ReachabilityStatus {
    REACHABLE,
    DNS_BLOCKED,
    TLS_BLOCKED,
    TCP_BLOCKED,
    NETWORK_ERROR,
    NOT_TESTED
}

data class DomainProbeResult(
    val domain: String,
    val category: String,
    val dnsStatus: ProbeStatus,
    val dnsResolvedIp: String?,
    val dnsLatencyMs: Long,
    val httpsStatus: ProbeStatus,
    val httpsResponseCode: Int?,
    val httpsLatencyMs: Long,
    val overallStatus: ReachabilityStatus,
    val errorDetail: String?
)

/**
 * Outcome of probing one domain several times.
 *
 * Exists because the faults worth finding are intermittent. A site that fails one
 * request in five looks perfectly healthy to a single probe — which is exactly how
 * the 2026-08-25 InterviewBoss incident stayed invisible while ~20% of users were
 * affected. Never collapse this to a green tick; show the pass count.
 */
data class RepeatedProbeResult(
    val domain: String,
    val category: String,
    val attempts: Int,
    val successCount: Int,
    /** Mean round-trip (DNS + HTTPS) across SUCCESSFUL attempts only. 0 if none passed. */
    val avgLatencyMs: Long,
    val minLatencyMs: Long,
    val maxLatencyMs: Long,
    val overallStatus: ReachabilityStatus,
    val perAttempt: List<DomainProbeResult>,
    val errorDetail: String?
) {
    /** Some passed, some failed — the case a single probe would have missed entirely. */
    val isIntermittent: Boolean get() = successCount in 1 until attempts

    val allFailed: Boolean get() = attempts > 0 && successCount == 0

    /** e.g. "3/4 OK, avg 240ms" — the honest headline, never a bare tick. */
    val summaryLine: String
        get() = when {
            attempts == 0 -> "Not tested"
            successCount == 0 -> "0/$attempts OK"
            else -> "$successCount/$attempts OK, avg ${avgLatencyMs}ms"
        }
}

data class QuicHintResult(
    val domain: String,
    val udpOpen: Boolean,
    val latencyMs: Long
)

data class ReachabilityReport(
    val probes: List<DomainProbeResult>,
    val quicHint: QuicHintResult?,
    val captivePortalDetected: Boolean,
    val privateDnsEnabled: Boolean,
    val vpnActive: Boolean,
    val dnsServers: String,
    val opennessScore: Int,
    val restrictionLevel: String,
    val timestamp: Long
)

/**
 * Network Reachability Tester -- runs DNS + HTTPS probes against popular
 * services to determine what the current network can reach.
 *
 * Framed as connectivity troubleshooting, not censorship detection.
 */
object NetworkReachabilityTester {

    /**
     * Probe list, now Remote-Config driven (2026-08-18). The previously-hardcoded set
     * is the bundled default in RemoteConfigUtils, so behaviour is unchanged until
     * someone edits the console. Curation rule is unchanged: safe, well-known,
     * non-political services only.
     *
     * Read through a function, not a `val`: a `val` would snapshot the list at class-init
     * (which on a cold start happens before the first RC fetch completes) and the app
     * would keep probing the stale list for the rest of the process lifetime.
     */
    private fun testDomains(): List<Pair<String, String>> =
        RemoteConfigUtils.getReachabilityTestDomains()

    /** Attempts per domain for the repeated/user-entered probe. See probeDomainRepeated. */
    const val DEFAULT_PROBE_ATTEMPTS = 4

    /** Gap between attempts. Long enough that a connection is not simply reused. */
    private const val ATTEMPT_GAP_MS = 350L

    private const val DNS_TIMEOUT_MS = 5000L
    private const val HTTPS_CONNECT_TIMEOUT_MS = 5000
    private const val HTTPS_READ_TIMEOUT_MS = 5000

    /**
     * Run all probes in parallel and produce a complete report.
     */
    suspend fun runReachabilityTest(context: Context): ReachabilityReport = coroutineScope {
        // Run domain probes in parallel
        val probeJobs = testDomains().map { (category, domain) ->
            async(Dispatchers.IO) { probeDomain(domain, category) }
        }

        // Run context checks in parallel
        val captivePortalJob = async(Dispatchers.IO) { checkCaptivePortal() }
        val quicJob = async(Dispatchers.IO) { QuicProber.probeQuicReachability("www.google.com", 443, 3000) }

        val probes = probeJobs.map { it.await() }
        val captivePortal = captivePortalJob.await()
        val quicHint = try { quicJob.await() } catch (_: Exception) { null }

        // Gather network context
        val privateDns = isPrivateDnsEnabled(context)
        val vpnActive = isVpnActive(context)
        val dnsServers = try { getDnsServers(context) } catch (_: Exception) { "Unknown" }

        // Calculate score
        val reachableCount = probes.count { it.overallStatus == ReachabilityStatus.REACHABLE }
        val opennessScore = if (probes.isNotEmpty()) {
            ((reachableCount.toFloat() / probes.size) * 100).toInt().coerceIn(0, 100)
        } else 0

        val restrictionLevel = when {
            opennessScore >= 90 -> "Open Network"
            opennessScore >= 70 -> "Minor Restrictions"
            opennessScore >= 40 -> "Moderate Restrictions"
            else -> "Heavy Restrictions"
        }

        ReachabilityReport(
            probes = probes,
            quicHint = quicHint,
            captivePortalDetected = captivePortal,
            privateDnsEnabled = privateDns,
            vpnActive = vpnActive,
            dnsServers = dnsServers,
            opennessScore = opennessScore,
            restrictionLevel = restrictionLevel,
            timestamp = System.currentTimeMillis()
        )
    }

    // --- Repeated / user-entered probe ---

    /**
     * Normalise whatever the user typed into a bare hostname, or null if it cannot be
     * one. Accepts "https://example.com/path", "example.com:8443", " Example.COM ".
     *
     * Pure and defensive on purpose: this value is about to be sent off the device as a
     * DNS query and a TLS SNI, so it must be a hostname and nothing else. It is never
     * logged, never sent to analytics, and never persisted by the probe path.
     */
    fun normaliseUserDomain(input: String): String? {
        var s = input.trim().lowercase()
        if (s.isEmpty()) return null
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.substringBefore('/')          // drop path
        s = s.substringBefore('?')          // drop query
        s = s.substringBefore('#')          // drop fragment
        s = s.substringBefore(':')          // drop port
        s = s.substringAfter('@')           // drop any userinfo
        s = s.trim().trimEnd('.')           // tolerate a trailing root dot
        if (s.isEmpty() || s.length > 253) return null
        if (!s.matches(USER_HOSTNAME_REGEX)) return null
        return s
    }

    private val USER_HOSTNAME_REGEX =
        Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /**
     * Probe one domain [attempts] times and aggregate.
     *
     * Deliberately SEQUENTIAL with a gap, not parallel: parallel attempts share the DNS
     * cache and the HTTP connection pool, so attempts 2..n measure the pool rather than
     * the network and an intermittent fault reads as uniformly healthy. Sequential is
     * slower and is the entire point.
     */
    suspend fun probeDomainRepeated(
        domain: String,
        category: String = "Custom",
        attempts: Int = DEFAULT_PROBE_ATTEMPTS
    ): RepeatedProbeResult = withContext(Dispatchers.IO) {
        val safeAttempts = attempts.coerceIn(1, 10)
        val results = ArrayList<DomainProbeResult>(safeAttempts)
        repeat(safeAttempts) { i ->
            results.add(probeDomain(domain, category))
            if (i < safeAttempts - 1) kotlinx.coroutines.delay(ATTEMPT_GAP_MS)
        }
        aggregateAttempts(domain, category, results)
    }

    /**
     * Pure aggregation, split out so the 4-attempt maths is unit-testable without a
     * network. Visible for testing.
     */
    fun aggregateAttempts(
        domain: String,
        category: String,
        results: List<DomainProbeResult>
    ): RepeatedProbeResult {
        val successes = results.filter { it.overallStatus == ReachabilityStatus.REACHABLE }
        val latencies = successes.map { it.dnsLatencyMs + it.httpsLatencyMs }

        // Representative status: if nothing passed, report the most common failure mode
        // rather than an arbitrary first-one-wins, so a single odd error does not
        // mislabel a consistently blocked domain.
        val status = when {
            results.isEmpty() -> ReachabilityStatus.NOT_TESTED
            successes.isNotEmpty() -> ReachabilityStatus.REACHABLE
            else -> results.groupingBy { it.overallStatus }.eachCount()
                .maxByOrNull { it.value }?.key ?: ReachabilityStatus.NETWORK_ERROR
        }

        // Surface an error string whenever ANY attempt failed — including the
        // partial-success case, where the failure is the interesting part.
        val errorDetail = results.firstOrNull {
            it.overallStatus != ReachabilityStatus.REACHABLE && it.errorDetail != null
        }?.errorDetail

        return RepeatedProbeResult(
            domain = domain,
            category = category,
            attempts = results.size,
            successCount = successes.size,
            avgLatencyMs = if (latencies.isEmpty()) 0L else latencies.sum() / latencies.size,
            minLatencyMs = latencies.minOrNull() ?: 0L,
            maxLatencyMs = latencies.maxOrNull() ?: 0L,
            overallStatus = status,
            perAttempt = results,
            errorDetail = errorDetail
        )
    }

    // --- Individual probe ---

    private suspend fun probeDomain(domain: String, category: String): DomainProbeResult {
        // Stage 1: DNS resolution
        var dnsStatus = ProbeStatus.NOT_TESTED
        var dnsIp: String? = null
        var dnsLatency = 0L
        var errorDetail: String? = null

        try {
            val dnsStart = System.currentTimeMillis()
            withTimeout(DNS_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val addr = InetAddress.getByName(domain)
                    dnsIp = addr.hostAddress
                }
            }
            dnsLatency = System.currentTimeMillis() - dnsStart
            dnsStatus = ProbeStatus.SUCCESS
        } catch (_: UnknownHostException) {
            dnsStatus = ProbeStatus.FAILED
            errorDetail = "DNS resolution failed"
        } catch (_: Exception) {
            dnsStatus = ProbeStatus.ERROR
            errorDetail = "DNS lookup error"
        }

        // If DNS failed, skip HTTPS
        if (dnsStatus != ProbeStatus.SUCCESS) {
            return DomainProbeResult(
                domain = domain,
                category = category,
                dnsStatus = dnsStatus,
                dnsResolvedIp = dnsIp,
                dnsLatencyMs = dnsLatency,
                httpsStatus = ProbeStatus.NOT_TESTED,
                httpsResponseCode = null,
                httpsLatencyMs = 0,
                overallStatus = ReachabilityStatus.DNS_BLOCKED,
                errorDetail = errorDetail
            )
        }

        // Stage 2: HTTPS connect (HEAD request -- minimal data transfer)
        var httpsStatus = ProbeStatus.NOT_TESTED
        var responseCode: Int? = null
        var httpsLatency = 0L

        try {
            val httpsStart = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                val url = URL("https://$domain/")
                val conn = url.openConnection() as HttpsURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = HTTPS_CONNECT_TIMEOUT_MS
                conn.readTimeout = HTTPS_READ_TIMEOUT_MS
                conn.instanceFollowRedirects = true
                try {
                    conn.connect()
                    responseCode = conn.responseCode
                    httpsStatus = ProbeStatus.SUCCESS
                } finally {
                    conn.disconnect()
                }
            }
            httpsLatency = System.currentTimeMillis() - httpsStart
        } catch (_: SSLException) {
            httpsStatus = ProbeStatus.FAILED
            errorDetail = "TLS handshake failed"
        } catch (_: javax.net.ssl.SSLHandshakeException) {
            httpsStatus = ProbeStatus.FAILED
            errorDetail = "TLS handshake failed"
        } catch (_: java.net.ConnectException) {
            httpsStatus = ProbeStatus.FAILED
            errorDetail = "TCP connection refused"
        } catch (_: java.net.SocketTimeoutException) {
            httpsStatus = ProbeStatus.FAILED
            errorDetail = "Connection timeout"
        } catch (_: java.io.IOException) {
            httpsStatus = ProbeStatus.FAILED
            errorDetail = "Network I/O error"
        } catch (_: Exception) {
            httpsStatus = ProbeStatus.ERROR
            errorDetail = "Connection error"
        }

        val overallStatus = when {
            httpsStatus == ProbeStatus.SUCCESS -> ReachabilityStatus.REACHABLE
            errorDetail?.contains("TLS") == true -> ReachabilityStatus.TLS_BLOCKED
            errorDetail?.contains("TCP") == true || errorDetail?.contains("timeout") == true ->
                ReachabilityStatus.TCP_BLOCKED
            else -> ReachabilityStatus.NETWORK_ERROR
        }

        return DomainProbeResult(
            domain = domain,
            category = category,
            dnsStatus = dnsStatus,
            dnsResolvedIp = dnsIp,
            dnsLatencyMs = dnsLatency,
            httpsStatus = httpsStatus,
            httpsResponseCode = responseCode,
            httpsLatencyMs = httpsLatency,
            overallStatus = overallStatus,
            errorDetail = errorDetail
        )
    }

    // --- Context checks ---

    private fun checkCaptivePortal(): Boolean {
        return try {
            val url = URL("https://clients3.google.com/generate_204")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = false
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            code != 204 // If not 204, captive portal likely
        } catch (_: Exception) {
            false // Can't tell -- assume no portal
        }
    }

    private fun isPrivateDnsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lp = cm.getLinkProperties(cm.activeNetwork)
            lp?.isPrivateDnsActive == true
        } catch (_: Exception) {
            false
        }
    }

    private fun isVpnActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } catch (_: Exception) {
            false
        }
    }

    // --- Share text generation ---

    fun generateShareText(report: ReachabilityReport, context: Context): String {
        return buildString {
            appendLine("\uD83C\uDF10 My Network Reachability Report")
            appendLine()
            appendLine("\uD83D\uDCCA Openness Score: ${report.opennessScore}/100 (${report.restrictionLevel})")
            appendLine("\uD83D\uDCE1 DNS: ${report.dnsServers.take(60)}")
            appendLine("\uD83D\uDD12 Private DNS: ${if (report.privateDnsEnabled) "Enabled" else "Disabled"}")
            appendLine("\uD83D\uDEE1\uFE0F VPN: ${if (report.vpnActive) "Active" else "Not active"}")
            if (report.quicHint != null) {
                appendLine("\uD83D\uDD17 QUIC hint: ${if (report.quicHint.udpOpen) "UDP/443 open" else "UDP/443 blocked"}")
            }
            appendLine()
            appendLine("Service Results:")
            for (probe in report.probes) {
                val icon = if (probe.overallStatus == ReachabilityStatus.REACHABLE) "\u2705" else "\u274C"
                val detail = when (probe.overallStatus) {
                    ReachabilityStatus.REACHABLE -> "${probe.httpsLatencyMs}ms"
                    ReachabilityStatus.DNS_BLOCKED -> "DNS blocked"
                    ReachabilityStatus.TLS_BLOCKED -> "TLS blocked"
                    ReachabilityStatus.TCP_BLOCKED -> "TCP blocked"
                    else -> probe.errorDetail ?: "Error"
                }
                appendLine("$icon ${probe.domain} -- $detail")
            }
            if (report.captivePortalDetected) {
                appendLine()
                appendLine("\u26A0\uFE0F Captive portal detected -- login may be required")
            }
            appendLine()
            appendLine("\uD83D\uDCA1 Tip: If a service is blocked, try enabling Private DNS")
            appendLine("   (Settings > Network > Private DNS > dns.google)")
            appendLine()
            appendLine("\uD83D\uDCF1 Tested with DeviceGPT")
            appendLine("\uD83D\uDD17 https://play.google.com/store/apps/details?id=${context.packageName}")
        }
    }

    fun generateAIPromptText(report: ReachabilityReport): String {
        return buildString {
            appendLine("My phone just ran a network reachability test. Here are the results:")
            appendLine()
            appendLine("Openness Score: ${report.opennessScore}/100 (${report.restrictionLevel})")
            appendLine("DNS Servers: ${report.dnsServers.take(80)}")
            appendLine("Private DNS: ${if (report.privateDnsEnabled) "Enabled" else "Disabled"}")
            appendLine("VPN: ${if (report.vpnActive) "Active" else "Not active"}")
            if (report.quicHint != null) {
                appendLine("QUIC (UDP/443): ${if (report.quicHint.udpOpen) "Open" else "Blocked"}")
            }
            appendLine("Captive Portal: ${if (report.captivePortalDetected) "Detected" else "Not detected"}")
            appendLine()
            appendLine("Domain Results:")
            for (probe in report.probes) {
                val status = probe.overallStatus.name
                appendLine("- ${probe.domain} (${probe.category}): $status")
                appendLine("  DNS: ${probe.dnsResolvedIp ?: "failed"} (${probe.dnsLatencyMs}ms)")
                if (probe.httpsStatus != ProbeStatus.NOT_TESTED) {
                    appendLine("  HTTPS: ${probe.httpsResponseCode ?: probe.errorDetail ?: "failed"} (${probe.httpsLatencyMs}ms)")
                }
            }
            appendLine()
            appendLine("Please explain:")
            appendLine("1. What do these results mean? Are any services being blocked?")
            appendLine("2. For blocked services, what is the likely cause?")
            appendLine("3. What are 3 safe steps I can try to fix connectivity?")
            appendLine("4. Is my DNS configuration protecting my privacy?")
            appendLine()
            appendLine("Only suggest safe, legal troubleshooting steps. Explain simply.")
        }
    }
}
