package com.teamz.lab.debugger.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Result of a single privacy check
 */
data class PrivacyCheckResult(
    val name: String,
    val displayName: String,
    val status: PrivacyCheckStatus,
    val detail: String,
    val recommendation: String? = null
)

enum class PrivacyCheckStatus {
    PASS,
    WARNING,
    FAIL,
    ERROR
}

/**
 * Complete privacy audit report
 */
data class PrivacyReport(
    val checks: List<PrivacyCheckResult>,
    val score: Int,
    val grade: String,
    val threatLevel: String,
    val ispName: String = "",
    val country: String = ""
)

/**
 * Network Privacy Scorer - orchestrates all privacy checks,
 * computes a composite score, and generates shareable text.
 *
 * Reuses existing check functions from network_utils.kt and adds
 * Private DNS detection as a new check.
 */
object NetworkPrivacyScorer {

    /**
     * Run all privacy checks in parallel and compute the composite report.
     */
    suspend fun runPrivacyAudit(context: Context): PrivacyReport = coroutineScope {
        val dnsCheck = async(Dispatchers.IO) { evaluateDnsIntegrity() }
        val sslCheck = async(Dispatchers.IO) { evaluateSslCertificate() }
        val proxyCheck = async(Dispatchers.IO) { evaluateTransparentProxy() }
        val dpiCheck = async(Dispatchers.IO) { evaluateDpiDetection() }
        val ispTrackingCheck = async(Dispatchers.IO) { evaluateIspTracking() }
        val captivePortalCheck = async(Dispatchers.IO) { evaluateCaptivePortal(context) }
        val privateDnsCheck = async(Dispatchers.IO) { evaluatePrivateDns(context) }

        val checks = listOf(
            dnsCheck.await(),
            sslCheck.await(),
            proxyCheck.await(),
            dpiCheck.await(),
            ispTrackingCheck.await(),
            captivePortalCheck.await(),
            privateDnsCheck.await()
        )

        val score = calculatePrivacyScore(checks)
        val grade = scoreToGrade(score)
        val threatLevel = scoreToThreatLevel(score)

        // Get ISP info (best-effort, non-blocking for score)
        val ispInfo = try {
            withContext(Dispatchers.IO) { parseIspInfo() }
        } catch (_: Exception) {
            Pair("Unknown ISP", "")
        }

        PrivacyReport(
            checks = checks,
            score = score,
            grade = grade,
            threatLevel = threatLevel,
            ispName = ispInfo.first,
            country = ispInfo.second
        )
    }

    // --- Individual check evaluators ---

    private fun evaluateDnsIntegrity(): PrivacyCheckResult {
        val raw = checkDNSManipulation()
        val passed = raw.contains("✅")
        val isError = raw.contains("❌")
        return PrivacyCheckResult(
            name = "dns_integrity",
            displayName = "DNS Integrity",
            status = when {
                passed -> PrivacyCheckStatus.PASS
                isError -> PrivacyCheckStatus.ERROR
                else -> PrivacyCheckStatus.WARNING
            },
            detail = if (passed) "Your DNS requests are not being redirected"
            else if (isError) "Unable to verify DNS integrity"
            else "Your DNS requests may be redirected by your ISP",
            recommendation = if (!passed) "Consider using a trusted DNS like Google (8.8.8.8) or Cloudflare (1.1.1.1)" else null
        )
    }

    private fun evaluateSslCertificate(): PrivacyCheckResult {
        val raw = checkSSLCertificateHijack()
        val passed = raw.contains("✅")
        val isError = raw.contains("❌")
        return PrivacyCheckResult(
            name = "ssl_certificate",
            displayName = "SSL Certificate",
            status = when {
                passed -> PrivacyCheckStatus.PASS
                isError -> PrivacyCheckStatus.ERROR
                else -> PrivacyCheckStatus.WARNING
            },
            detail = if (passed) "No man-in-the-middle interception detected"
            else if (isError) "Unable to verify SSL certificate integrity"
            else "Suspicious SSL certificate detected -- possible traffic interception",
            recommendation = if (!passed && !isError) "Avoid sensitive activities on this network" else null
        )
    }

    private fun evaluateTransparentProxy(): PrivacyCheckResult {
        val raw = checkTransparentProxy()
        val passed = raw.contains("✅")
        val isError = raw.contains("❌")
        return PrivacyCheckResult(
            name = "transparent_proxy",
            displayName = "Proxy Detection",
            status = when {
                passed -> PrivacyCheckStatus.PASS
                isError -> PrivacyCheckStatus.ERROR
                else -> PrivacyCheckStatus.WARNING
            },
            detail = if (passed) "No transparent proxy detected in your connection"
            else if (isError) "Unable to check for proxy interception"
            else "A transparent proxy may be intercepting your traffic",
            recommendation = if (!passed && !isError) "Use HTTPS websites and consider a VPN for sensitive browsing" else null
        )
    }

    private fun evaluateDpiDetection(): PrivacyCheckResult {
        val raw = checkDPIDetection()
        val passed = raw.contains("✅")
        val isError = raw.contains("❌")
        return PrivacyCheckResult(
            name = "dpi_detection",
            displayName = "Deep Packet Inspection",
            status = when {
                passed -> PrivacyCheckStatus.PASS
                isError -> PrivacyCheckStatus.ERROR
                else -> PrivacyCheckStatus.WARNING
            },
            detail = if (passed) "No deep packet inspection signatures detected"
            else if (isError) "Unable to check for network interference"
            else "Network interference indicators detected",
            recommendation = if (!passed && !isError) "Your ISP may be inspecting traffic -- a VPN can help" else null
        )
    }

    private fun evaluateIspTracking(): PrivacyCheckResult {
        val raw = checkISPTracking()
        val passed = raw.contains("✅")
        val isError = raw.contains("❌")
        return PrivacyCheckResult(
            name = "isp_tracking",
            displayName = "ISP Behavior",
            status = when {
                passed -> PrivacyCheckStatus.PASS
                isError -> PrivacyCheckStatus.ERROR
                else -> PrivacyCheckStatus.WARNING
            },
            detail = if (passed) "No tracking redirects detected from your ISP"
            else if (isError) "Unable to check ISP behavior"
            else "Your ISP may be tracking or redirecting your web traffic",
            recommendation = if (!passed && !isError) "Consider using encrypted DNS and HTTPS-only browsing" else null
        )
    }

    private fun evaluateCaptivePortal(context: Context): PrivacyCheckResult {
        val raw = getCaptivePortalStatus(context)
        val passed = raw.contains("✅")
        return PrivacyCheckResult(
            name = "captive_portal",
            displayName = "Captive Portal",
            status = if (passed) PrivacyCheckStatus.PASS else PrivacyCheckStatus.WARNING,
            detail = if (passed) "No login wall detected on this network"
            else "This network requires login -- your traffic may be monitored",
            recommendation = if (!passed) "Captive portals can see your browsing -- avoid sensitive tasks until authenticated" else null
        )
    }

    private fun evaluatePrivateDns(context: Context): PrivacyCheckResult {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(network)

            val isPrivateDnsActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                linkProperties?.isPrivateDnsActive == true
            } else {
                false // Private DNS not available before Android 9
            }

            val privateDnsServer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                linkProperties?.privateDnsServerName
            } else {
                null
            }

            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> PrivacyCheckResult(
                    name = "private_dns",
                    displayName = "Private DNS",
                    status = PrivacyCheckStatus.WARNING,
                    detail = "Private DNS requires Android 9+ (your device: Android ${Build.VERSION.SDK_INT})",
                    recommendation = "Consider upgrading your device for better DNS privacy"
                )
                isPrivateDnsActive -> PrivacyCheckResult(
                    name = "private_dns",
                    displayName = "Private DNS",
                    status = PrivacyCheckStatus.PASS,
                    detail = if (privateDnsServer != null) "Private DNS active via $privateDnsServer"
                    else "Private DNS is enabled (automatic mode)",
                    recommendation = null
                )
                else -> PrivacyCheckResult(
                    name = "private_dns",
                    displayName = "Private DNS",
                    status = PrivacyCheckStatus.WARNING,
                    detail = "Private DNS is not enabled -- your DNS queries are unencrypted",
                    recommendation = "Enable Private DNS in Settings > Network > Private DNS (use dns.google or 1dot1dot1dot1.cloudflare-dns.com)"
                )
            }
        } catch (e: Exception) {
            PrivacyCheckResult(
                name = "private_dns",
                displayName = "Private DNS",
                status = PrivacyCheckStatus.ERROR,
                detail = "Unable to check Private DNS status",
                recommendation = null
            )
        }
    }

    // --- Scoring ---

    private fun calculatePrivacyScore(checks: List<PrivacyCheckResult>): Int {
        // Each check contributes to the total score.
        // PASS = full weight, WARNING = half weight, FAIL/ERROR = 0
        val weights = mapOf(
            "dns_integrity" to 20,
            "ssl_certificate" to 20,
            "transparent_proxy" to 15,
            "dpi_detection" to 10,
            "isp_tracking" to 15,
            "captive_portal" to 10,
            "private_dns" to 10
        )

        var total = 0
        for (check in checks) {
            val weight = weights[check.name] ?: 10
            total += when (check.status) {
                PrivacyCheckStatus.PASS -> weight
                PrivacyCheckStatus.WARNING -> weight / 2
                PrivacyCheckStatus.FAIL -> 0
                PrivacyCheckStatus.ERROR -> weight / 3 // Partial credit for errors (inconclusive)
            }
        }

        return total.coerceIn(0, 100)
    }

    private fun scoreToGrade(score: Int): String {
        return when {
            score >= 90 -> "A"
            score >= 80 -> "B+"
            score >= 70 -> "B"
            score >= 60 -> "C+"
            score >= 50 -> "C"
            score >= 40 -> "D"
            else -> "F"
        }
    }

    private fun scoreToThreatLevel(score: Int): String {
        return when {
            score >= 80 -> "Low"
            score >= 50 -> "Moderate"
            else -> "High"
        }
    }

    // --- ISP info parsing ---

    private fun parseIspInfo(): Pair<String, String> {
        return try {
            val url = java.net.URL("https://ipinfo.io/json")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(response)
            val isp = json.optString("org", "Unknown ISP")
            val country = json.optString("country", "")
            Pair(isp, country)
        } catch (_: Exception) {
            Pair("Unknown ISP", "")
        }
    }

    // --- Share text generation ---

    /**
     * Generate a short, shareable text summary of the privacy report.
     */
    fun generateShareText(report: PrivacyReport, context: Context): String {
        return buildString {
            appendLine("🔐 My Network Privacy Report Card")
            appendLine()
            appendLine("📊 Privacy Grade: ${report.grade} (${report.score}/100)")
            if (report.ispName.isNotEmpty()) {
                val countryPart = if (report.country.isNotEmpty()) ", ${report.country}" else ""
                appendLine("📍 ISP: ${report.ispName}$countryPart")
            }
            appendLine()

            for (check in report.checks) {
                val icon = when (check.status) {
                    PrivacyCheckStatus.PASS -> "✅"
                    PrivacyCheckStatus.WARNING -> "⚠️"
                    PrivacyCheckStatus.FAIL -> "❌"
                    PrivacyCheckStatus.ERROR -> "❓"
                }
                appendLine("$icon ${check.displayName}: ${check.detail}")
            }

            appendLine()

            // Add a tip based on the lowest-scoring check
            val firstWarning = report.checks.firstOrNull {
                it.status == PrivacyCheckStatus.WARNING || it.status == PrivacyCheckStatus.FAIL
            }
            if (firstWarning?.recommendation != null) {
                appendLine("💡 Tip: ${firstWarning.recommendation}")
                appendLine()
            }

            appendLine("📱 Scanned with DeviceGPT")
            appendLine("🔗 https://play.google.com/store/apps/details?id=${context.packageName}")
        }
    }

    /**
     * Generate a detailed prompt for AI assistant analysis.
     */
    fun generateAIPromptText(report: PrivacyReport): String {
        return buildString {
            appendLine("I just ran a network privacy audit on my phone. Here are the results:")
            appendLine()
            appendLine("Privacy Grade: ${report.grade} (${report.score}/100)")
            appendLine("Threat Level: ${report.threatLevel}")
            if (report.ispName.isNotEmpty()) {
                appendLine("ISP: ${report.ispName}")
            }
            if (report.country.isNotEmpty()) {
                appendLine("Country: ${report.country}")
            }
            appendLine()
            appendLine("Check Results:")
            for (check in report.checks) {
                val statusLabel = when (check.status) {
                    PrivacyCheckStatus.PASS -> "PASS"
                    PrivacyCheckStatus.WARNING -> "WARNING"
                    PrivacyCheckStatus.FAIL -> "FAIL"
                    PrivacyCheckStatus.ERROR -> "ERROR"
                }
                appendLine("- ${check.displayName}: $statusLabel -- ${check.detail}")
                if (check.recommendation != null) {
                    appendLine("  Recommendation: ${check.recommendation}")
                }
            }
            appendLine()
            appendLine("Questions:")
            appendLine("1. What do these results mean for my privacy and security?")
            appendLine("2. Which findings should I be most concerned about?")
            appendLine("3. What are the top 3 things I can do right now to improve my network privacy?")
            appendLine("4. Is my ISP doing anything unusual compared to other ISPs?")
            appendLine()
            appendLine("Please explain in simple, non-technical language.")
        }
    }
}
