package com.teamz.lab.debugger.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import com.teamz.lab.debugger.utils.ZeroTrustScorer
import com.teamz.lab.debugger.utils.detectHiddenApps
import com.teamz.lab.debugger.utils.detectKeylogger
import com.teamz.lab.debugger.utils.detectScreenRecordingApps
import com.teamz.lab.debugger.utils.isDeviceRooted
import com.teamz.lab.debugger.utils.isUsbDebuggingEnabled

class PrivacyAppFunctions {

    /**
     * Runs DeviceGPT's full Zero Trust Dashboard audit: App Privacy Risk, Network Trust,
     * and Device Integrity combined into a weighted composite score 0-100 with grade.
     *
     * Use this when the user asks: "is my phone secure?", "privacy check",
     * "am I being spied on?", "is my phone compromised?".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun runZeroTrustAudit(
        appFunctionContext: AppFunctionContext,
    ): PrivacyAuditReport {
        val context = appFunctionContext.context
        val report = ZeroTrustScorer.runZeroTrustAudit(context)

        val appSection = report.sections.getOrNull(0)
        val netSection = report.sections.getOrNull(1)
        val deviceSection = report.sections.getOrNull(2)

        val findings = report.sections.flatMap { section ->
            section.checks
                .filter { check ->
                    val text = check.toString().lowercase()
                    text.contains("suspicious") || text.contains("detected") || text.contains("fail")
                }
                .map { it.toString() }
        }.take(20)

        return privacyAuditReport(
            overallScore = report.compositeScore,
            grade = report.compositeGrade,
            appPrivacyGrade = appSection?.grade ?: "?",
            networkTrustGrade = netSection?.grade ?: "?",
            deviceIntegrityGrade = deviceSection?.grade ?: "?",
            suspiciousFindings = findings,
            details = mapOf(
                "riskLevel" to report.riskLevel,
                "appPrivacyScore" to (appSection?.score?.toString() ?: "?"),
                "networkTrustScore" to (netSection?.score?.toString() ?: "?"),
                "deviceIntegrityScore" to (deviceSection?.score?.toString() ?: "?"),
                "timestamp" to report.timestamp.toString(),
            ).toDetailEntries(),
        )
    }

    /**
     * Checks for known keylogger / spyware packages installed on the device.
     * DeviceGPT queries against a hardcoded list declared in AndroidManifest.
     *
     * Use this for: "check for spyware", "keylogger detector", "is there a spy app?".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun checkSpywareAndKeyloggers(
        appFunctionContext: AppFunctionContext,
    ): ScanResult {
        val context = appFunctionContext.context
        val keyloggerResult = runCatching { detectKeylogger(context) }.getOrDefault("")
        val screenRecResult = runCatching { detectScreenRecordingApps(context) }.getOrDefault("")
        val hiddenResult = runCatching { detectHiddenApps(context) }.getOrDefault("")

        val combinedFindings = listOf(
            "Keylogger scan" to keyloggerResult,
            "Screen recorders" to screenRecResult,
            "Hidden apps" to hiddenResult,
        ).filter { it.second.isNotBlank() }

        val summary = if (combinedFindings.isEmpty()) {
            "No known spyware packages detected."
        } else {
            combinedFindings.joinToString(" · ") { (label, r) -> "$label: $r" }
        }

        return scanResult(
            title = "Spyware & Keylogger Check",
            summary = summary,
            details = combinedFindings.toMap().toDetailEntries(),
        )
    }

    /**
     * Checks whether the device is rooted AND whether USB debugging is enabled —
     * two common security-posture signals.
     *
     * Use this for: "is my phone rooted?", "is USB debugging on?", "security posture".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDeviceIntegrityQuickCheck(
        appFunctionContext: AppFunctionContext,
    ): ScanResult {
        val context = appFunctionContext.context
        val rooted = runCatching { isDeviceRooted() }.getOrDefault("Unknown")
        val usbDebug = runCatching { isUsbDebuggingEnabled(context) }.getOrDefault("Unknown")
        return scanResult(
            title = "Device Integrity",
            summary = "Root status: $rooted. USB debugging: $usbDebug.",
            details = mapOf("rootStatus" to rooted, "usbDebugging" to usbDebug).toDetailEntries(),
        )
    }
}
