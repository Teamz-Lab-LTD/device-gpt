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
import androidx.compose.ui.res.stringResource
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.string
import com.teamz.lab.debugger.ui.icons.DgText

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
                error = context.string(R.string.lb_vr_upload_failed)
                onReportReady(generated)
            }
        } catch (e: Exception) {
            isGenerating = false
            isUploading = false
            error = context.string(R.string.lb_vr_generate_failed, e.message.toString())
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
                    text = stringResource(R.string.lb_vr_generating_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.lb_vr_generating_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isGenerating) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.lb_vr_collecting),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                } else if (isUploading) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.lb_vr_uploading),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                } else if (error != null) {
                    DgText(
                        error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
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
                    stringResource(R.string.lb_vr_ready_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.lb_vr_ready_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.lb_vr_when_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.lb_vr_ready_when),
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
                        Text(stringResource(R.string.lb_vr_code_label), style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        DgText(
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
                        Toast.makeText(context, context.string(R.string.lb_code_copied), Toast.LENGTH_SHORT).show()
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
                    Text(stringResource(R.string.lb_vr_copy_code))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Report summary — device, OS, speeds, health: proof for resale, support, or device state
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.lb_vr_recorded_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.lb_vr_recorded_body),
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
                    stringResource(R.string.lb_vr_cannot_change),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.lb_vr_done))
                }
            }
        }
    }
}

@Composable
private fun ReportSummaryWithDividers(reportData: ReportData, signedAt: String) {
    // Device & identity
    SummaryRow(stringResource(R.string.lb_vr_row_device), reportData.deviceModel)
    SummaryRow(stringResource(R.string.lb_vr_row_android), reportData.androidVersion)
    SummaryRow(
        stringResource(R.string.lb_vr_row_api),
        if (reportData.apiLevel > 0) {
            stringResource(R.string.lb_vr_api_value, reportData.apiLevel.toString())
        } else {
            stringResource(R.string.lb_vr_not_available)
        }
    )
    SummaryRow(stringResource(R.string.lb_vr_row_patch), reportData.securityPatchLevel)
    SummaryRow(stringResource(R.string.lb_vr_row_integrity), reportData.rootStatus)
    SummaryRow(stringResource(R.string.lb_vr_row_cpu), reportData.cpuModel)
    SummaryRow(stringResource(R.string.lb_vr_row_resolution), reportData.screenResolution)
    SummaryRow(stringResource(R.string.lb_vr_row_play), reportData.playCertified)
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    // Health & privacy
    SummaryRow(stringResource(R.string.lb_vr_row_health), "${reportData.healthScore}/10")
    SummaryRow(stringResource(R.string.lb_vr_row_privacy), reportData.privacyGrade)
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    // Network
    SummaryRow(stringResource(R.string.lb_vr_row_download), reportData.downloadSpeed)
    SummaryRow(stringResource(R.string.lb_vr_row_upload), reportData.uploadSpeed)
    SummaryRow(stringResource(R.string.lb_vr_row_latency), reportData.latency)
    SummaryRow(stringResource(R.string.lb_vr_row_jitter), reportData.jitter)
    SummaryRow(stringResource(R.string.lb_vr_row_packet_loss), reportData.packetLoss)
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    // Hardware
    SummaryRow(stringResource(R.string.lb_vr_row_battery), reportData.batteryHealth)
    SummaryRow(stringResource(R.string.lb_vr_row_storage), reportData.storageInfo)
    SummaryRow(stringResource(R.string.lb_vr_row_ram), reportData.ramInfo)
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    // Meta
    SummaryRow(stringResource(R.string.lb_vr_row_signed_at), signedAt)
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        DgText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        DgText(
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
                    stringResource(R.string.drawer_verified_verify),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.lb_vr_verify_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lb_vr_when_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.lb_vr_verify_when),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text(stringResource(R.string.lb_vr_paste_label)) },
                    placeholder = { DgText(stringResource(R.string.lb_vr_code_example, "DG-A1B2C3D4")) },
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
                        Text(stringResource(R.string.lb_vr_verify_button))
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
                                    Text(stringResource(R.string.lb_vr_verified), fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                    Text(
                                        stringResource(R.string.lb_vr_verified_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                    reportInfo?.let { info ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        DgText(
                                            stringResource(R.string.lb_vr_signed, info["signedAt"].toString()),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        DgText(
                                            stringResource(R.string.lb_vr_app_version, info["appVersion"].toString()),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        val json = info["reportDataJson"] as? String
                                        if (json != null) {
                                            val data = VerifiedReportManager.parseReportData(json)
                                            if (data != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    stringResource(R.string.lb_vr_full_insight),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                val isOldReport = data.apiLevel == 0 && data.securityPatchLevel == "N/A" && data.cpuModel == "N/A"
                                                if (isOldReport) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        stringResource(R.string.lb_vr_old_report),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                                val speedFailed = data.downloadSpeed.contains("Speed Test Failed", ignoreCase = true) || data.uploadSpeed.contains("Speed Test Failed", ignoreCase = true)
                                                if (speedFailed) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        stringResource(R.string.lb_vr_speed_failed_note),
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
                                    Text(stringResource(R.string.lb_vr_not_verified), fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                                    Text(
                                        stringResource(R.string.lb_vr_tampered_body),
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
                                    Text(stringResource(R.string.lb_vr_not_found), fontWeight = FontWeight.Bold)
                                    Text(
                                        stringResource(R.string.lb_vr_not_found_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(stringResource(R.string.lb_error_try_again), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}
