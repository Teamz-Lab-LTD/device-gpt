package com.teamz.lab.debugger.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import com.teamz.lab.debugger.utils.VerifiedReportManager

class ReportAppFunctions {

    /**
     * Generates a signed, tamper-evident DeviceGPT verified report for the current device.
     *
     * Contains a cryptographic hash of device state + a short human-readable verification
     * code the user can share with a buyer / employer / insurance / repair tech.
     *
     * Does NOT upload anywhere by itself — call shareVerifiedReport() to post to the
     * cloud verification registry.
     *
     * Use this when the user asks: "generate a resale report",
     * "certify this phone", "verified device report",
     * "proof my phone is not rooted / not spied on".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun generateVerifiedReport(
        appFunctionContext: AppFunctionContext,
    ): VerifiedDeviceReport {
        val context = appFunctionContext.context
        val report = VerifiedReportManager.generateReport(context)
        val epochMs = runCatching {
            java.time.Instant.parse(report.signedAt).toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())

        return verifiedDeviceReport(
            verificationCode = report.verificationCode,
            generatedAtEpochMs = epochMs,
            shareUrl = "",
            summary = "Verified report generated. Code: ${report.verificationCode}. " +
                "App version ${report.appVersion}. Keep this code to let others verify.",
            details = mapOf(
                "reportId" to report.reportId,
                "appVersion" to report.appVersion,
                "signedAt" to report.signedAt,
                "publicKey" to report.publicKey.take(64) + "…",
                "signaturePreview" to report.signature.take(32) + "…",
            ).toDetailEntries(),
        )
    }

    /**
     * Generates AND uploads a signed DeviceGPT verified report to the cloud registry,
     * returning a short verification code buyers / employers can type in to verify.
     *
     * Use for: "share my verified report", "upload my phone certification".
     */
    @RequiresApi(36)
    @AppFunction(isDescribedByKDoc = true)
    suspend fun shareVerifiedReport(
        appFunctionContext: AppFunctionContext,
    ): VerifiedDeviceReport {
        val context = appFunctionContext.context
        val report = VerifiedReportManager.generateReport(context)
        val uploaded = runCatching { VerifiedReportManager.uploadReport(report) }.getOrDefault(false)
        val epochMs = runCatching {
            java.time.Instant.parse(report.signedAt).toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())

        val summary = if (uploaded) {
            "Report uploaded. Verification code: ${report.verificationCode}."
        } else {
            "Report generated but upload failed. Code still valid offline: ${report.verificationCode}."
        }

        return verifiedDeviceReport(
            verificationCode = report.verificationCode,
            generatedAtEpochMs = epochMs,
            shareUrl = "",
            summary = summary,
            details = mapOf(
                "reportId" to report.reportId,
                "uploaded" to uploaded.toString(),
                "appVersion" to report.appVersion,
                "signedAt" to report.signedAt,
            ).toDetailEntries(),
        )
    }
}
