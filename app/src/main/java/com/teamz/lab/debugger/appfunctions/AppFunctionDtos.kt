package com.teamz.lab.debugger.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * Key/value entry used everywhere we need structured extra details.
 * AppFunctions (alpha08) does not support Map directly, and it also refuses
 * default values on scalar properties. Every field below is non-optional;
 * callers must supply everything explicitly (use empty string / NaN / -1 as
 * sentinel when a value is missing).
 */
@AppFunctionSerializable
data class DetailEntry(
    val key: String,
    val value: String,
)

internal fun Map<String, String>.toDetailEntries(): List<DetailEntry> =
    map { (k, v) -> DetailEntry(k, v) }

fun Iterable<DetailEntry>.asMap(): Map<String, String> =
    associate { it.key to it.value }

@AppFunctionSerializable
data class ScanResult(
    val title: String,
    val summary: String,
    val score: Int,
    val grade: String,
    val details: List<DetailEntry>,
    val timestamp: Long,
)

@AppFunctionSerializable
data class BatteryHealthReport(
    val healthScore: Int,
    val summary: String,
    val temperatureCelsius: Float,
    val thermalState: String,
    val chargingInfo: String,
    val cycleEstimate: String,
    val details: List<DetailEntry>,
)

@AppFunctionSerializable
data class NetworkReport(
    val reachabilityGrade: String,
    val privacyGrade: String,
    val summary: String,
    val reachabilityDetails: List<DetailEntry>,
    val privacyDetails: List<DetailEntry>,
)

@AppFunctionSerializable
data class DeviceInfoReport(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val cpu: String,
    val ramUsage: String,
    val storage: String,
    val display: String,
    val gpu: String,
    val sensors: List<String>,
    val details: List<DetailEntry>,
)

@AppFunctionSerializable
data class PrivacyAuditReport(
    val overallScore: Int,
    val grade: String,
    val appPrivacyGrade: String,
    val networkTrustGrade: String,
    val deviceIntegrityGrade: String,
    val suspiciousFindings: List<String>,
    val details: List<DetailEntry>,
)

@AppFunctionSerializable
data class VerifiedDeviceReport(
    val verificationCode: String,
    val generatedAtEpochMs: Long,
    val shareUrl: String,
    val summary: String,
    val details: List<DetailEntry>,
)

// ---------------- Kotlin-side factory helpers (not annotated) ----------------
// KSP forbids default values on @AppFunctionSerializable fields. These helpers
// let call sites pass only the fields they care about and fill in sensible
// defaults for the rest.

internal fun scanResult(
    title: String,
    summary: String,
    score: Int = -1,
    grade: String = "",
    details: List<DetailEntry> = emptyList(),
    timestamp: Long = System.currentTimeMillis(),
): ScanResult = ScanResult(
    title = title,
    summary = summary,
    score = score,
    grade = grade,
    details = details,
    timestamp = timestamp,
)

internal fun batteryHealthReport(
    healthScore: Int,
    summary: String,
    temperatureCelsius: Float = Float.NaN,
    thermalState: String = "",
    chargingInfo: String = "",
    cycleEstimate: String = "",
    details: List<DetailEntry> = emptyList(),
): BatteryHealthReport = BatteryHealthReport(
    healthScore, summary, temperatureCelsius, thermalState, chargingInfo,
    cycleEstimate, details,
)

internal fun networkReport(
    reachabilityGrade: String,
    privacyGrade: String,
    summary: String,
    reachabilityDetails: List<DetailEntry> = emptyList(),
    privacyDetails: List<DetailEntry> = emptyList(),
): NetworkReport = NetworkReport(
    reachabilityGrade, privacyGrade, summary, reachabilityDetails, privacyDetails,
)

internal fun deviceInfoReport(
    manufacturer: String,
    model: String,
    androidVersion: String,
    cpu: String,
    ramUsage: String = "",
    storage: String = "",
    display: String = "",
    gpu: String = "",
    sensors: List<String> = emptyList(),
    details: List<DetailEntry> = emptyList(),
): DeviceInfoReport = DeviceInfoReport(
    manufacturer, model, androidVersion, cpu, ramUsage, storage, display,
    gpu, sensors, details,
)

internal fun privacyAuditReport(
    overallScore: Int,
    grade: String,
    appPrivacyGrade: String = "?",
    networkTrustGrade: String = "?",
    deviceIntegrityGrade: String = "?",
    suspiciousFindings: List<String> = emptyList(),
    details: List<DetailEntry> = emptyList(),
): PrivacyAuditReport = PrivacyAuditReport(
    overallScore, grade, appPrivacyGrade, networkTrustGrade, deviceIntegrityGrade,
    suspiciousFindings, details,
)

internal fun verifiedDeviceReport(
    verificationCode: String,
    generatedAtEpochMs: Long,
    summary: String,
    shareUrl: String = "",
    details: List<DetailEntry> = emptyList(),
): VerifiedDeviceReport = VerifiedDeviceReport(
    verificationCode, generatedAtEpochMs, shareUrl, summary, details,
)
