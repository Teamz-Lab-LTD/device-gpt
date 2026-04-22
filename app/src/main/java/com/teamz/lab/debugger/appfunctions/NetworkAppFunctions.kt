package com.teamz.lab.debugger.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import com.teamz.lab.debugger.utils.NetworkPrivacyScorer
import com.teamz.lab.debugger.utils.NetworkReachabilityTester

class NetworkAppFunctions {

    /**
     * Runs DeviceGPT's full network probe: DNS/HTTPS reachability against popular services,
     * QUIC hint, captive portal / private-DNS / VPN detection, and a network-privacy audit
     * (SSL cert sanity, DPI hints, ISP tracking heuristics, captive portal).
     *
     * Returns a composite report with an openness grade AND a privacy grade.
     *
     * Use this when the user asks: "is my wifi working properly?",
     * "is my network private?", "is my ISP tracking me?",
     * "why is my internet slow / blocked?".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun runNetworkHealthScan(
        appFunctionContext: AppFunctionContext,
    ): NetworkReport {
        val context = appFunctionContext.context
        val reach = NetworkReachabilityTester.runReachabilityTest(context)
        val privacy = NetworkPrivacyScorer.runPrivacyAudit(context)

        val summary = buildString {
            append("Network openness: ${reach.restrictionLevel} (${reach.opennessScore}/100). ")
            append("Privacy: ${privacy.grade} (${privacy.score}/100, ${privacy.threatLevel}).")
        }

        return networkReport(
            reachabilityGrade = reach.restrictionLevel,
            privacyGrade = privacy.grade,
            summary = summary,
            reachabilityDetails = buildMap {
                put("opennessScore", reach.opennessScore.toString())
                put("restrictionLevel", reach.restrictionLevel)
                put("captivePortalDetected", reach.captivePortalDetected.toString())
                put("privateDnsEnabled", reach.privateDnsEnabled.toString())
                put("vpnActive", reach.vpnActive.toString())
                put("dnsServers", reach.dnsServers)
                put("probesCount", reach.probes.size.toString())
                reach.quicHint?.let { put("quicHost", it.domain) }
            }.toDetailEntries(),
            privacyDetails = buildMap {
                put("score", privacy.score.toString())
                put("grade", privacy.grade)
                put("threatLevel", privacy.threatLevel)
                if (privacy.ispName.isNotBlank()) put("isp", privacy.ispName)
                if (privacy.country.isNotBlank()) put("country", privacy.country)
                privacy.checks.forEach { check ->
                    put("check_${check::class.simpleName}_${check.hashCode()}", check.toString())
                }
            }.toDetailEntries(),
        )
    }

    /**
     * Runs only the reachability probe (faster than the full network health scan):
     * checks if popular services are DNS-resolvable and HTTPS-reachable, and hints
     * whether the current network is restricted.
     *
     * Use this for: "is my internet working?", "can I reach Google/YouTube/etc.?".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun runNetworkReachability(
        appFunctionContext: AppFunctionContext,
    ): ScanResult {
        val context = appFunctionContext.context
        val report = NetworkReachabilityTester.runReachabilityTest(context)
        val summary = NetworkReachabilityTester.generateShareText(report, context)
        return scanResult(
            title = "Network Reachability",
            summary = summary,
            score = report.opennessScore,
            grade = report.restrictionLevel,
            details = mapOf(
                "probes" to report.probes.size.toString(),
                "captivePortalDetected" to report.captivePortalDetected.toString(),
                "privateDnsEnabled" to report.privateDnsEnabled.toString(),
                "vpnActive" to report.vpnActive.toString(),
                "dnsServers" to report.dnsServers,
            ).toDetailEntries(),
        )
    }

    /**
     * Runs only the network-privacy audit: DNS integrity, SSL cert sanity, transparent-proxy
     * detection, DPI detection, ISP tracking heuristics, captive portal detection.
     *
     * Use this for: "is my network private?", "is anyone tracking my traffic?".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun runNetworkPrivacyAudit(
        appFunctionContext: AppFunctionContext,
    ): ScanResult {
        val context = appFunctionContext.context
        val report = NetworkPrivacyScorer.runPrivacyAudit(context)
        return scanResult(
            title = "Network Privacy",
            summary = "Privacy grade ${report.grade} (${report.score}/100) · ${report.threatLevel}.",
            score = report.score,
            grade = report.grade,
            details = buildMap {
                put("threatLevel", report.threatLevel)
                if (report.ispName.isNotBlank()) put("isp", report.ispName)
                if (report.country.isNotBlank()) put("country", report.country)
                put("checksRun", report.checks.size.toString())
            }.toDetailEntries(),
        )
    }
}
