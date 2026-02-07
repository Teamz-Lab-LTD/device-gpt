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

    // Fixed test list -- safe, non-political, well-known services
    private val TEST_DOMAINS = listOf(
        "Search" to "www.google.com",
        "Video" to "www.youtube.com",
        "Messaging" to "web.whatsapp.com",
        "Messaging" to "telegram.org",
        "Social" to "www.instagram.com",
        "Developer" to "github.com",
        "Cloud" to "drive.google.com",
        "DNS" to "dns.google",
        "DNS" to "one.one.one.one",
        "CDN" to "speed.cloudflare.com"
    )

    private const val DNS_TIMEOUT_MS = 5000L
    private const val HTTPS_CONNECT_TIMEOUT_MS = 5000
    private const val HTTPS_READ_TIMEOUT_MS = 5000

    /**
     * Run all probes in parallel and produce a complete report.
     */
    suspend fun runReachabilityTest(context: Context): ReachabilityReport = coroutineScope {
        // Run domain probes in parallel
        val probeJobs = TEST_DOMAINS.map { (category, domain) ->
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
