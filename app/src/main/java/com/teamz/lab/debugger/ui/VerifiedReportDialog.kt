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
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.VerificationResult
import com.teamz.lab.debugger.utils.VerifiedReport
import com.teamz.lab.debugger.utils.VerifiedReportManager
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Generating Verified Report",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isGenerating) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Collecting device data and signing...", style = MaterialTheme.typography.bodyMedium)
                } else if (isUploading) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Uploading for verification...", style = MaterialTheme.typography.bodyMedium)
                } else if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
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
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Report Ready",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

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
                        Text("Verification Code", style = MaterialTheme.typography.labelMedium)
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

                // Copy button
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
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Code")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Report summary
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Report Summary", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        SummaryRow("Device", report.reportData.deviceModel)
                        SummaryRow("Android", report.reportData.androidVersion)
                        SummaryRow("Health Score", "${report.reportData.healthScore}/10")
                        SummaryRow("Privacy Grade", report.reportData.privacyGrade)
                        SummaryRow("Download", report.reportData.downloadSpeed)
                        SummaryRow("Upload", report.reportData.uploadSpeed)
                        SummaryRow("Signed At", report.signedAt)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This report is tamper-evident. Anyone with DeviceGPT can verify it was not modified.",
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
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
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
    var code by remember { mutableStateOf(initialCode) }
    var isVerifying by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<VerificationResult?>(null) }
    var reportInfo by remember { mutableStateOf<Map<String, Any>?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
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
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Verification Code") },
                    placeholder = { Text("DG-XXXXXXXX") },
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
                    shape = RoundedCornerShape(10.dp)
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
                                    Text("VERIFIED", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                    Text(
                                        "This report was generated by DeviceGPT and has not been modified.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                    reportInfo?.let { info ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Signed: ${info["signedAt"]}", style = MaterialTheme.typography.labelSmall)
                                        Text("App version: ${info["appVersion"]}", style = MaterialTheme.typography.labelSmall)
                                        // Parse and show report data
                                        val json = info["reportDataJson"] as? String
                                        if (json != null) {
                                            val data = VerifiedReportManager.parseReportData(json)
                                            if (data != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                SummaryRow("Device", data.deviceModel)
                                                SummaryRow("Health", "${data.healthScore}/10")
                                                SummaryRow("Privacy", data.privacyGrade)
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
                                    Text("VERIFICATION FAILED", fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                                    Text(
                                        "This report may have been modified. The data cannot be trusted.",
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
                                    Text("NOT FOUND", fontWeight = FontWeight.Bold)
                                    Text(
                                        "No report found with this code. Check the code and try again.",
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
