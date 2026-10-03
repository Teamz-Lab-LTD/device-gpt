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

    /**
     * Section 1 rebuilt 2026-09-09. Five of the six checks here could not produce a true
     * finding: keylogger, screen-recorder and malware each compared installed packages against
     * a short list of invented ids, permission abuse asked whether OUR app held a permission
     * and so flagged every app on the device, and the accessibility check called TalkBack
     * suspicious. Worse, the scorer's own string matching disagreed with the strings it was
     * matching — `perms.contains("⚠") || perms.contains("found")` never matched
     * "🚨 Apps Using Sensitive Permissions:", so the FAIL case scored as a PASS.
     *
     * Two things changed. Checks now detect capabilities rather than package names (see
     * PrivacyExposureModel), and they hand back a typed ExposureFinding instead of a sentence
     * to grep, which removes the whole class of bug where a copy edit silently flips a verdict.
     *
     * Scoring rule, deliberate: INFORMATIONAL does not cost points. A third-party keyboard, an
     * app holding the advertising id, a sideloaded APK — these are the normal state of a normal
     * phone. Docking them would give every user a bad grade and make the score meaningless,
     * which is the same failure as a false accusation, just quieter. Only ELEVATED — a
     * capability that is rare outside monitoring software — fails a check.
     */
    private suspend fun evaluateAppPrivacy(context: Context): TrustSection {
        // Collect once. Each of these is a binder query; the previous version re-ran them per
        // check and still read the results wrong.
        val keyboards = withContext(Dispatchers.IO) {
            PrivacyExposureScanner.collectKeyboards(context)
        }
        val accessibility = withContext(Dispatchers.IO) {
            PrivacyExposureScanner.collectAccessibilityServices(context)
        }
        val appPerms = withContext(Dispatchers.IO) {
            PrivacyExposureScanner.collectAppPermissions(context)
        }

        val checks = mutableListOf<TrustCheckResult>()

        fun add(name: String, displayName: String, f: ExposureFinding) {
            checks.add(TrustCheckResult(
                name = name,
                displayName = displayName,
                status = when (f.level) {
                    ExposureLevel.NONE, ExposureLevel.INFORMATIONAL -> TrustCheckStatus.PASS
                    ExposureLevel.ELEVATED -> TrustCheckStatus.FAIL
                },
                detail = f.headline + if (f.items.isEmpty()) "" else
                    ": " + f.items.joinToString("; "),
                recommendation = f.recommendation.takeIf { f.level != ExposureLevel.NONE },
            ))
        }

        // 1. Keystroke exposure — enabled keyboards, plus accessibility key-event filtering
        add("keylogger", "Keystroke Exposure", assessKeystrokeExposure(keyboards, accessibility))

        // 2. Screen content access — accessibility window/screenshot capability, media projection
        add("screen_recorder", "Screen Content Access",
            assessScreenCaptureExposure(accessibility, appPerms))

        // 3. Sensitive permissions actually granted, per app
        add("dangerous_permissions", "Sensitive Permissions",
            assessSensitivePermissionExposure(appPerms))

        // 4. Camera/mic currently active — unchanged, this one was already real (AppOps)
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

        // 5. Accessibility services, named and described rather than blanket-flagged
        val thirdPartyAccess = accessibility.filter { !it.isSystem }
        checks.add(TrustCheckResult(
            name = "accessibility_services",
            displayName = "Accessibility Services",
            status = if (thirdPartyAccess.isEmpty()) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
            detail = when {
                accessibility.isEmpty() -> "No accessibility services are enabled"
                thirdPartyAccess.isEmpty() ->
                    "${accessibility.size} enabled, all built into the system"
                else -> "${thirdPartyAccess.size} service(s) that did not ship with your phone " +
                    "are enabled: " + thirdPartyAccess.joinToString("; ") {
                        if (it.label.isBlank()) it.packageName else it.label
                    }
            },
            recommendation = if (thirdPartyAccess.isEmpty()) null
            else "Review: Settings → Accessibility → Installed services"
        ))

        // 6. Install source — replaces the three-package "malware signature" list
        add("malware_scan", "App Install Source",
            withContext(Dispatchers.IO) { PrivacyExposureScanner.installSourceExposure(context) })

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

        // 7. SELinux. Three states: getenforce is "Permission denied" for apps on current Android,
        // and an unreadable status used to WARN every user to "contact your device manufacturer".
        // "Not checked" in the security text = we could not see it: add no check and score the
        // section over the checks that ran (normalizeToRun below).
        val selinuxNotChecked = security.contains("Not checked")
        if (!selinuxNotChecked) {
            val selinuxEnforced = security.contains("system protection is active")
            checks.add(TrustCheckResult(
                name = "selinux",
                displayName = "System Protection (SELinux)",
                status = if (selinuxEnforced) TrustCheckStatus.PASS else TrustCheckStatus.WARNING,
                detail = if (selinuxEnforced) "SELinux is enforcing -- kernel-level protection active"
                else "SELinux is not enforcing",
                recommendation = if (!selinuxEnforced) "This is unusual -- contact your device manufacturer" else null
            ))
        }

        val score = calculateSectionScore(checks, mapOf(
            "root_status" to 20, "usb_debugging" to 15, "encryption" to 15,
            "overlay_permission" to 10, "notification_listeners" to 10,
            "unknown_sources" to 15, "selinux" to 15
        ), normalizeToRun = true)

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

    /**
     * [normalizeToRun]: score out of the weights of the checks that actually ran, so a check the
     * app could not perform is neither a pass nor a penalty. Off by default — the privacy section
     * builds a variable number of checks against fixed weights and keeps its old arithmetic.
     */
    private fun calculateSectionScore(
        checks: List<TrustCheckResult>,
        weights: Map<String, Int>,
        normalizeToRun: Boolean = false,
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
        if (normalizeToRun) {
            val possible = checks.sumOf { weights[it.name] ?: 10 }
            if (possible > 0) total = total * 100 / possible
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
