package com.teamz.lab.debugger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.VerificationResult
import com.teamz.lab.debugger.utils.ReportData
import com.teamz.lab.debugger.utils.VerifiedReport
import com.teamz.lab.debugger.utils.LeaderboardManager
import com.teamz.lab.debugger.utils.VerifiedReportManager
import com.teamz.lab.debugger.ui.theme.AppTheme
import kotlinx.coroutines.delay
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.ui.theme.useThemeManager
import kotlinx.coroutines.launch

/**
 * Dialog for generating a verified device report.
 * Shows progress, then the verification code and report summary.
 */
@Composable
fun GenerateReportDialog(
    onDismiss: () -> Unit,
    onReportReady: (VerifiedReport) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isGenerating by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<VerifiedReport?>(null) }

    LaunchedEffect(Unit) {
        try {
            isGenerating = true
            // Ensure Firebase Auth user exists (required by Firestore rules for upload)
            LeaderboardManager.ensureUserExists(context)
            delay(1500)
            val generated = VerifiedReportManager.generateReport(context)
            report = generated
            isGenerating = false

            isUploading = true
            val uploaded = VerifiedReportManager.uploadReport(generated)
            isUploading = false

            if (uploaded) {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.VerifiedReportUploaded,
                    mapOf("report_id" to generated.reportId)
                )
                com.teamz.lab.debugger.utils.EngagementTracker.trackSignificantAction(
                    context,
                    com.teamz.lab.debugger.utils.SignificantAction.REPORT_GENERATED,
                    mapOf("report_id" to generated.reportId)
                )
                onReportReady(generated)
            } else {
                error = "Report generated but upload failed. You can still share the code."
                onReportReady(generated)
            }
        } catch (e: Exception) {
            isGenerating = false
            isUploading = false
            error = "Failed to generate report: ${e.message}"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Generating Verified Report",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "We're creating a snapshot of your phone's health and safety that you can share with others.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isGenerating) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Collecting device data and signing...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                } else if (isUploading) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Uploading for verification...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                } else if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Dialog showing the generated report with verification code.
 */
@Composable
fun ReportReadyDialog(
    report: VerifiedReport,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = useThemeManager().getEffectiveTheme() == AppTheme.DESIGN_SYSTEM_DARK

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Report Ready",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Your device report was created. Share the code below so someone else can check it — like a ticket number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "When to use this:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "• Sending to support so they can see your phone's status\n• Selling your phone — show the buyer it's healthy\n• Proving to work or school that your device is safe",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Verification code
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Your shareable code", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = report.verificationCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Copy button (design system: no neon in light mode)
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Verification Code", report.verificationCode))
                        Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.VerifiedReportShared,
                            mapOf("method" to "copy")
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = if (isDark) ButtonDefaults.buttonColors(
                        containerColor = DesignSystemColors.NeonGreen,
                        contentColor = DesignSystemColors.Dark
                    ) else ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Code")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Report summary — device, OS, speeds, health: proof for resale, support, or device state
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("What was recorded (100% full device insight)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "All fields below were saved from your phone — device, OS, speeds, network, battery, storage, RAM. Good for resale proof, tech support, or showing your device is in good shape.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ReportSummaryWithDividers(reportData = report.reportData, signedAt = report.signedAt)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Nobody can change this report. Anyone with this app can enter your code and see the same details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun ReportSummaryWithDividers(reportData: ReportData, signedAt: String) {
    // Device & identity
    SummaryRow("Device", reportData.deviceModel)
    SummaryRow("Android", reportData.androidVersion)
    SummaryRow("API level", if (reportData.apiLevel > 0) "API ${reportData.apiLevel}" else "N/A")
    SummaryRow("Security patch", reportData.securityPatchLevel)
    SummaryRow("Device integrity", reportData.rootStatus)
    SummaryRow("CPU model", reportData.cpuModel)
    SummaryRow("Screen resolution", reportData.screenResolution)
    SummaryRow("Google Play certified", reportData.playCertified)
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    // Health & privacy
    SummaryRow("Health Score", "${reportData.healthScore}/10")
    SummaryRow("Privacy Grade", reportData.privacyGrade)
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    // Network
    SummaryRow("Download", reportData.downloadSpeed)
    SummaryRow("Upload", reportData.uploadSpeed)
    SummaryRow("Latency", reportData.latency)
    SummaryRow("Jitter", reportData.jitter)
    SummaryRow("Packet loss", reportData.packetLoss)
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    // Hardware
    SummaryRow("Battery", reportData.batteryHealth)
    SummaryRow("Storage", reportData.storageInfo)
    SummaryRow("RAM", reportData.ramInfo)
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    // Meta
    SummaryRow("Signed At", signedAt)
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Dialog for verifying a report by entering a verification code.
 */
@Composable
fun VerifyReportDialog(
    onDismiss: () -> Unit,
    initialCode: String = ""
) {
    val coroutineScope = rememberCoroutineScope()
    val isDark = useThemeManager().getEffectiveTheme() == AppTheme.DESIGN_SYSTEM_DARK
    var code by remember { mutableStateOf(initialCode) }
    var isVerifying by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<VerificationResult?>(null) }
    var reportInfo by remember { mutableStateOf<Map<String, Any>?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Verify a Report",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Someone sent you a code? Enter it here to see their device report and confirm it's real.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "When to use this:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "• Support asked you to send a code\n• You're buying a used phone and want to check the seller's report\n• Someone shared their report code with you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Paste the code they gave you") },
                    placeholder = { Text("e.g. DG-A1B2C3D4") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (code.isNotBlank()) {
                            coroutineScope.launch {
                                isVerifying = true
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.ReportVerificationAttempted,
                                    mapOf("method" to "code")
                                )
                                val (vResult, info) = VerifiedReportManager.verifyReport(code.trim())
                                result = vResult
                                reportInfo = info
                                isVerifying = false

                                val eventName = when (vResult) {
                                    VerificationResult.VERIFIED -> AnalyticsEvent.ReportVerificationSuccess
                                    else -> AnalyticsEvent.ReportVerificationFailed
                                }
                                AnalyticsUtils.logEvent(eventName, mapOf("code" to code))
                            }
                        }
                    },
                    enabled = code.isNotBlank() && !isVerifying,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = if (isDark) ButtonDefaults.buttonColors(
                        containerColor = DesignSystemColors.NeonGreen,
                        contentColor = DesignSystemColors.Dark
                    ) else ButtonDefaults.buttonColors()
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Verify")
                    }
                }

                // Result
                if (result != null && !isVerifying) {
                    Spacer(modifier = Modifier.height(16.dp))
                    when (result) {
                        VerificationResult.VERIFIED -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.12f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Verified, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Verified", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                    Text(
                                        "This report is real and wasn't changed. The details below are what was recorded at the time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                    reportInfo?.let { info ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Signed: ${info["signedAt"]}", style = MaterialTheme.typography.labelSmall)
                                        Text("App version: ${info["appVersion"]}", style = MaterialTheme.typography.labelSmall)
                                        val json = info["reportDataJson"] as? String
                                        if (json != null) {
                                            val data = VerifiedReportManager.parseReportData(json)
                                            if (data != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    "Full device insight (100% of what was recorded)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                val isOldReport = data.apiLevel == 0 && data.securityPatchLevel == "N/A" && data.cpuModel == "N/A"
                                                if (isOldReport) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        "Reports created with an older app version may show N/A for some fields. New reports include more details.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                                val speedFailed = data.downloadSpeed.contains("Speed Test Failed", ignoreCase = true) || data.uploadSpeed.contains("Speed Test Failed", ignoreCase = true)
                                                if (speedFailed) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        "Download/upload shows \"Speed Test Failed\" when the test didn't complete at the time the report was created (e.g. no internet or timeout).",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                ReportSummaryWithDividers(reportData = data, signedAt = data.timestamp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        VerificationResult.TAMPERED -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF44336).copy(alpha = 0.12f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.GppBad, null, tint = Color(0xFFF44336), modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Not verified", fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                                    Text(
                                        "This report may have been altered. Don't trust it.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        VerificationResult.NOT_FOUND -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No report found", fontWeight = FontWeight.Bold)
                                    Text(
                                        "We couldn't find a report for this code. Check the code and try again, or ask the person to send it again.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        else -> {
                            Text("An error occurred. Please try again.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}
