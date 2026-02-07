package com.teamz.lab.debugger.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

// --- Data models ---

enum class TrustCheckStatus { PASS, WARNING, FAIL, ERROR }

data class TrustCheckResult(
    val name: String,
    val displayName: String,
    val status: TrustCheckStatus,
    val detail: String,
    val recommendation: String? = null
)

data class TrustSection(
    val name: String,
    val displayName: String,
    val icon: String,
    val checks: List<TrustCheckResult>,
    val score: Int,
    val grade: String
)

data class ZeroTrustReport(
    val sections: List<TrustSection>,
    val compositeScore: Int,
    val compositeGrade: String,
    val riskLevel: String,
    val timestamp: Long
)

/**
 * Zero Trust Scorer -- aggregates App Privacy Risk, Network Trust,
 * and Device Integrity into a composite trust score.
 *
 * Reuses existing check functions from device_utils.kt,
 * NetworkPrivacyScorer.kt, and the new DeviceIntegrityChecker.kt.
 */
object ZeroTrustScorer {

    /**
     * Run the full zero-trust audit across all three sections.
     */
    suspend fun runZeroTrustAudit(context: Context): ZeroTrustReport = coroutineScope {
        val appSection = async(Dispatchers.IO) { evaluateAppPrivacy(context) }
        val networkSection = async(Dispatchers.IO) { evaluateNetworkTrust(context) }
        val deviceSection = async(Dispatchers.IO) { evaluateDeviceIntegrity(context) }

        val sections = listOf(
            appSection.await(),
            networkSection.await(),
            deviceSection.await()
        )

        // Weighted composite: App 35%, Network 35%, Device 30%
        val composite = (
            sections[0].score * 0.35 +
            sections[1].score * 0.35 +
            sections[2].score * 0.30
        ).toInt().coerceIn(0, 100)

        ZeroTrustReport(
            sections = sections,
            compositeScore = composite,
            compositeGrade = scoreToGrade(composite),
            riskLevel = when {
                composite >= 80 -> "Low"
                composite >= 50 -> "Moderate"
                else -> "High"
            },
            timestamp = System.currentTimeMillis()
        )
    }

    // ==================== Section 1: App Privacy Risk ====================

    private suspend fun evaluateAppPrivacy(context: Context): TrustSection {
        val checks = mutableListOf<TrustCheckResult>()

        // 1. Keylogger detection
        val keylogger = withContext(Dispatchers.IO) { detectKeylogger(context) }
        checks.add(TrustCheckResult(
            name = "keylogger",
            displayName = "Keylogger Detection",
            status = if (keylogger.contains("No")) TrustCheckStatus.PASS else TrustCheckStatus.FAIL,
            detail = if (keylogger.contains("No")) "No keylogger apps detected"
            else "Potential keylogger app found",
            recommendation = if (!keylogger.contains("No")) "Check installed apps and remove suspicious ones" else null
        ))

        // 2. Screen recorder detection
        val screenRec = withContext(Dispatchers.IO) { detectScreenRecordingApps(context) }
        checks.add(TrustCheckResult(
            name = "screen_recorder",
            displayName = "Screen Recorder Apps",
            status = if (screenRec.contains("No")) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = if (screenRec.contains("No")) "No suspicious screen recorders found"
            else "Screen recording app detected",
            recommendation = if (!screenRec.contains("No")) "Review screen recording apps in Settings > Apps" else null
        ))

        // 3. Dangerous permissions
        val perms = withContext(Dispatchers.IO) { detectDangerousPermissions(context) }
        val hasDangerousPerms = perms.contains("⚠") || perms.contains("found")
        checks.add(TrustCheckResult(
            name = "dangerous_permissions",
            displayName = "App Permissions Abuse",
            status = if (!hasDangerousPerms) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = if (!hasDangerousPerms) "No concerning permission patterns detected"
            else "Some apps hold sensitive permissions",
            recommendation = if (hasDangerousPerms) "Review app permissions in Settings > Apps > Permissions" else null
        ))

        // 4. Camera/mic currently active
        val camMicActive = withContext(Dispatchers.IO) { isCameraOrMicActive(context) }
        val isActive = camMicActive.contains("Active") || camMicActive.contains("🎤") || camMicActive.contains("📷")
        checks.add(TrustCheckResult(
            name = "camera_mic_active",
            displayName = "Camera/Mic Activity",
            status = if (!isActive) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = if (!isActive) "Camera and microphone are not currently in use"
            else "Camera or microphone is currently active",
            recommendation = if (isActive) "Check which app is using your camera/microphone" else null
        ))

        // 5. Suspicious accessibility services
        val accessServices = withContext(Dispatchers.IO) { detectSuspiciousAccessibilityServices(context) }
        val hasSuspiciousAccess = accessServices.contains("Found") || accessServices.contains("Suspicious")
        checks.add(TrustCheckResult(
            name = "accessibility_services",
            displayName = "Accessibility Services",
            status = if (!hasSuspiciousAccess) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = if (!hasSuspiciousAccess) "No suspicious accessibility services enabled"
            else "Accessibility services are enabled -- these can monitor all screen content",
            recommendation = if (hasSuspiciousAccess) "Review: Settings > Accessibility > Installed services" else null
        ))

        // 6. Malware scan
        val malware = withContext(Dispatchers.IO) { detectOfflineMalware(context) }
        val hasMalware = malware.contains("Detected") || malware.contains("⚠") || malware.contains("🚨")
        checks.add(TrustCheckResult(
            name = "malware_scan",
            displayName = "Malware Signatures",
            status = if (!hasMalware) TrustCheckStatus.PASS else TrustCheckStatus.FAIL,
            detail = if (!hasMalware) "No known malware signatures found"
            else "Potential malware detected on device",
            recommendation = if (hasMalware) "Run Google Play Protect and remove suspicious apps" else null
        ))

        val score = calculateSectionScore(checks, mapOf(
            "keylogger" to 20, "screen_recorder" to 15, "dangerous_permissions" to 20,
            "camera_mic_active" to 15, "accessibility_services" to 20, "malware_scan" to 10
        ))

        return TrustSection(
            name = "app_privacy",
            displayName = "App Privacy Risk",
            icon = "📱",
            checks = checks,
            score = score,
            grade = scoreToGrade(score)
        )
    }

    // ==================== Section 2: Network Trust ====================

    private suspend fun evaluateNetworkTrust(context: Context): TrustSection {
        // Reuse the existing NetworkPrivacyScorer -- it already runs 7 checks
        val privacyReport = NetworkPrivacyScorer.runPrivacyAudit(context)

        // Convert PrivacyCheckResults to TrustCheckResults
        val checks = privacyReport.checks.map { pc ->
            TrustCheckResult(
                name = pc.name,
                displayName = pc.displayName,
                status = when (pc.status) {
                    PrivacyCheckStatus.PASS -> TrustCheckStatus.PASS
                    PrivacyCheckStatus.WARNING -> TrustCheckStatus.WARNING
                    PrivacyCheckStatus.FAIL -> TrustCheckStatus.FAIL
                    PrivacyCheckStatus.ERROR -> TrustCheckStatus.ERROR
                },
                detail = pc.detail,
                recommendation = pc.recommendation
            )
        }

        return TrustSection(
            name = "network_trust",
            displayName = "Network Trust",
            icon = "🌐",
            checks = checks,
            score = privacyReport.score,
            grade = privacyReport.grade
        )
    }

    // ==================== Section 3: Device Integrity ====================

    private suspend fun evaluateDeviceIntegrity(context: Context): TrustSection {
        val checks = mutableListOf<TrustCheckResult>()

        // 1. Root detection
        val rooted = withContext(Dispatchers.IO) { isDeviceRooted() }
        val isRooted = rooted.contains("Yes") || rooted.contains("Rooted")
        checks.add(TrustCheckResult(
            name = "root_status",
            displayName = "Root Status",
            status = if (!isRooted) TrustCheckStatus.PASS else TrustCheckStatus.FAIL,
            detail = if (!isRooted) "Device is not rooted" else "Device appears to be rooted",
            recommendation = if (isRooted) "Rooted devices are more vulnerable to malware and data theft" else null
        ))

        // 2. USB debugging
        val usb = withContext(Dispatchers.IO) { isUsbDebuggingEnabled(context) }
        val usbEnabled = usb.contains("Enabled")
        checks.add(TrustCheckResult(
            name = "usb_debugging",
            displayName = "USB Debugging",
            status = if (!usbEnabled) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = if (!usbEnabled) "USB debugging is disabled"
            else "USB debugging is enabled -- allows remote access when connected",
            recommendation = if (usbEnabled) "Disable in Settings > Developer options > USB debugging" else null
        ))

        // 3. Storage encryption
        val security = withContext(Dispatchers.IO) { getSecurityInfo(context) }
        val encrypted = security.contains("fully protected") || security.contains("securely protected")
        checks.add(TrustCheckResult(
            name = "encryption",
            displayName = "Storage Encryption",
            status = if (encrypted) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = if (encrypted) "Device storage is encrypted"
            else "Storage encryption status could not be confirmed",
            recommendation = if (!encrypted) "Check Settings > Security > Encryption & credentials" else null
        ))

        // 4. Overlay permissions (NEW)
        val overlayEnabled = DeviceIntegrityChecker.isOverlayPermissionEnabled(context)
        checks.add(TrustCheckResult(
            name = "overlay_permission",
            displayName = "Overlay Permission",
            status = if (!overlayEnabled) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = if (!overlayEnabled) "No apps can draw over other apps"
            else "Draw-over-other-apps is enabled -- apps can overlay your screen",
            recommendation = if (overlayEnabled) "Review: Settings > Apps > Special access > Display over other apps" else null
        ))

        // 5. Notification listeners (NEW)
        val listeners = DeviceIntegrityChecker.getEnabledNotificationListeners(context)
        val hasListeners = listeners.isNotEmpty()
        checks.add(TrustCheckResult(
            name = "notification_listeners",
            displayName = "Notification Listeners",
            status = if (!hasListeners) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = if (!hasListeners) "No apps are reading your notifications"
            else "${listeners.size} app(s) can read all your notifications",
            recommendation = if (hasListeners) "Review: Settings > Apps > Special access > Notification access" else null
        ))

        // 6. Unknown sources (NEW)
        val unknownSources = DeviceIntegrityChecker.isUnknownSourcesEnabled(context)
        checks.add(TrustCheckResult(
            name = "unknown_sources",
            displayName = "Unknown Sources",
            status = if (!unknownSources) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = if (!unknownSources) "Sideloading (install from unknown sources) is disabled"
            else "Unknown sources is enabled -- apps can be installed outside Play Store",
            recommendation = if (unknownSources) "Disable in Settings > Security > Install unknown apps" else null
        ))

        // 7. SELinux
        val selinuxEnforced = security.contains("system protection is active")
        checks.add(TrustCheckResult(
            name = "selinux",
            displayName = "System Protection (SELinux)",
            status = if (selinuxEnforced) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = if (selinuxEnforced) "SELinux is enforcing -- kernel-level protection active"
            else "SELinux may not be enforcing",
            recommendation = if (!selinuxEnforced) "This is unusual -- contact your device manufacturer" else null
        ))

        val score = calculateSectionScore(checks, mapOf(
            "root_status" to 20, "usb_debugging" to 15, "encryption" to 15,
            "overlay_permission" to 10, "notification_listeners" to 10,
            "unknown_sources" to 15, "selinux" to 15
        ))

        return TrustSection(
            name = "device_integrity",
            displayName = "Device Integrity",
            icon = "🔒",
            checks = checks,
            score = score,
            grade = scoreToGrade(score)
        )
    }

    // ==================== Scoring ====================

    private fun calculateSectionScore(
        checks: List<TrustCheckResult>,
        weights: Map<String, Int>
    ): Int {
        var total = 0
        for (check in checks) {
            val weight = weights[check.name] ?: 10
            total += when (check.status) {
                TrustCheckStatus.PASS -> weight
                TrustCheckStatus.WARNING -> weight / 2
                TrustCheckStatus.FAIL -> 0
                TrustCheckStatus.ERROR -> weight / 3
            }
        }
        return total.coerceIn(0, 100)
    }

    private fun scoreToGrade(score: Int): String = when {
        score >= 90 -> "A"
        score >= 80 -> "B+"
        score >= 70 -> "B"
        score >= 60 -> "C+"
        score >= 50 -> "C"
        score >= 40 -> "D"
        else -> "F"
    }

    // ==================== Share / AI text ====================

    fun generateShareText(report: ZeroTrustReport, context: Context): String {
        return buildString {
            appendLine("\uD83D\uDEE1\uFE0F My Zero Trust Report Card")
            appendLine()
            appendLine("\uD83D\uDCCA Trust Score: ${report.compositeScore}/100 (${report.compositeGrade})")
            appendLine("\uD83D\uDCCD Risk Level: ${report.riskLevel}")
            appendLine()
            for (section in report.sections) {
                appendLine("${section.icon} ${section.displayName}: ${section.score}/100 (${section.grade})")
            }
            appendLine()
            // Top findings
            val warnings = report.sections.flatMap { it.checks }
                .filter { it.status != TrustCheckStatus.PASS }
                .take(3)
            if (warnings.isNotEmpty()) {
                appendLine("Top findings:")
                for (w in warnings) {
                    val icon = if (w.status == TrustCheckStatus.FAIL) "\u274C" else "\u26A0\uFE0F"
                    appendLine("$icon ${w.displayName}: ${w.detail}")
                }
            }
            val passCount = report.sections.flatMap { it.checks }.count { it.status == TrustCheckStatus.PASS }
            appendLine("\u2705 $passCount checks passed")
            appendLine()
            val firstRec = report.sections.flatMap { it.checks }.firstOrNull { it.recommendation != null }
            if (firstRec?.recommendation != null) {
                appendLine("\uD83D\uDCA1 Tip: ${firstRec.recommendation}")
                appendLine()
            }
            appendLine("\uD83D\uDCF1 Scanned with DeviceGPT")
            appendLine("\uD83D\uDD17 https://play.google.com/store/apps/details?id=${context.packageName}")
        }
    }

    fun generateAIPromptText(report: ZeroTrustReport): String {
        return buildString {
            appendLine("My phone just ran a Zero Trust security audit. Here are the results:")
            appendLine()
            appendLine("Overall Trust Score: ${report.compositeScore}/100 (${report.compositeGrade})")
            appendLine("Risk Level: ${report.riskLevel}")
            appendLine()
            for (section in report.sections) {
                appendLine("=== ${section.displayName} (${section.score}/100) ===")
                for (check in section.checks) {
                    appendLine("- ${check.displayName}: ${check.status} -- ${check.detail}")
                    if (check.recommendation != null) {
                        appendLine("  Recommendation: ${check.recommendation}")
                    }
                }
                appendLine()
            }
            appendLine("Please explain:")
            appendLine("1. What do these results mean for my security and privacy?")
            appendLine("2. Which findings are most concerning?")
            appendLine("3. What are the top 3 things I should do right now?")
            appendLine("4. Are there any unusual patterns across these results?")
            appendLine()
            appendLine("Explain in simple, non-technical language.")
        }
    }
}
