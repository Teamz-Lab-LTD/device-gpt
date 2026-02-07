package com.teamz.lab.debugger.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.NetworkPrivacyScorer
import com.teamz.lab.debugger.utils.PrivacyCheckResult
import com.teamz.lab.debugger.utils.PrivacyCheckStatus
import com.teamz.lab.debugger.utils.PrivacyReport
import com.teamz.lab.debugger.utils.ReferralManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Network Privacy Report Card - a prominent card that shows a privacy
 * grade and individual check results. Designed to sit at the top of
 * the Network Info tab.
 */
@Composable
fun NetworkPrivacyReportCard(
    onShareClick: (String) -> Unit,
    onAIClick: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var report by remember { mutableStateOf<PrivacyReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isExpanded by remember { mutableStateOf(false) }
    var hasLoggedView by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var privacyShareText by remember { mutableStateOf("") }

    // Run the audit on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val result = withContext(Dispatchers.IO) {
                NetworkPrivacyScorer.runPrivacyAudit(context)
            }
            report = result
        } catch (_: Exception) {
            // Graceful degradation -- card stays in loading/error state
        } finally {
            isLoading = false
        }
    }

    // Show ViralShareDialog when user taps Share Report
    if (showShareDialog) {
        ViralShareDialog(
            onDismiss = { showShareDialog = false },
            context = context,
            shareText = privacyShareText,
            showReferralCode = true
        )
    }

    AppCard(
        bottomPadding = 12
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            // --- Header row (always visible) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isExpanded = !isExpanded
                        if (isExpanded && !hasLoggedView) {
                            hasLoggedView = true
                            AnalyticsUtils.logEvent(AnalyticsEvent.PrivacyReportViewed)
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shield icon
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isLoading -> MaterialTheme.colorScheme.surfaceVariant
                        (report?.score ?: 0) >= 80 -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                        (report?.score ?: 0) >= 50 -> Color(0xFFFF9800).copy(alpha = 0.12f)
                        else -> Color(0xFFF44336).copy(alpha = 0.12f)
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Privacy",
                            modifier = Modifier.size(24.dp),
                            tint = when {
                                isLoading -> MaterialTheme.colorScheme.onSurfaceVariant
                                (report?.score ?: 0) >= 80 -> Color(0xFF4CAF50)
                                (report?.score ?: 0) >= 50 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Network Privacy Report",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isLoading) {
                        Text(
                            text = "Scanning your network...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (report != null) {
                        Text(
                            text = "Grade: ${report!!.grade} (${report!!.score}/100) \u2022 Threat: ${report!!.threatLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Grade badge (when loaded)
                if (!isLoading && report != null) {
                    GradeBadge(grade = report!!.grade, score = report!!.score)
                } else if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Expanded content ---
            AnimatedVisibility(
                visible = isExpanded && !isLoading && report != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                report?.let { privacyReport ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        // Score arc
                        PrivacyScoreArc(score = privacyReport.score)

                        Spacer(modifier = Modifier.height(16.dp))

                        // ISP info
                        if (privacyReport.ispName.isNotEmpty() && privacyReport.ispName != "Unknown ISP") {
                            val ispText = if (privacyReport.country.isNotEmpty()) {
                                "${privacyReport.ispName} \u2022 ${privacyReport.country}"
                            } else {
                                privacyReport.ispName
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = ispText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Divider
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Individual check rows
                        privacyReport.checks.forEach { check ->
                            PrivacyCheckRow(check = check)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Share button
                            Button(
                                onClick = {
                                    privacyShareText = NetworkPrivacyScorer.generateShareText(privacyReport, context)
                                    showShareDialog = true
                                    AnalyticsUtils.logEvent(
                                        AnalyticsEvent.PrivacyReportShared,
                                        mapOf("grade" to privacyReport.grade, "score" to privacyReport.score)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Share Report",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            // Ask AI button
                            if (onAIClick != null) {
                                OutlinedButton(
                                    onClick = {
                                        val aiPrompt = NetworkPrivacyScorer.generateAIPromptText(privacyReport)
                                        onAIClick("Network Privacy Report", aiPrompt)
                                        AnalyticsUtils.logEvent(
                                            AnalyticsEvent.PrivacyReportAIClicked,
                                            mapOf("grade" to privacyReport.grade, "score" to privacyReport.score)
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = com.teamz.lab.debugger.utils.AIIcon.color()
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Ask AI",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }

                        // Log the grade for analytics
                        LaunchedEffect(privacyReport.grade) {
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.PrivacyReportGrade,
                                mapOf("grade" to privacyReport.grade, "score" to privacyReport.score)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Grade badge ---

@Composable
private fun GradeBadge(grade: String, score: Int) {
    val bgColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 50 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Surface(
        modifier = Modifier
            .size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = grade,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = if (grade.length > 1) 12.sp else 16.sp
            )
        }
    }
}

// --- Score arc visualization ---

@Composable
private fun PrivacyScoreArc(score: Int) {
    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 1000),
        label = "score_progress"
    )

    val arcColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 50 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(100.dp)
        ) {
            val strokeWidth = 10.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(
                (size.width - radius * 2) / 2,
                (size.height - radius * 2) / 2
            )
            val arcSize = Size(radius * 2, radius * 2)

            // Background track (full circle)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Score arc
            drawArc(
                color = arcColor,
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = arcColor
            )
            Text(
                text = "/ 100",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Individual check row ---

@Composable
private fun PrivacyCheckRow(check: PrivacyCheckResult) {
    var showDetail by remember { mutableStateOf(false) }

    val statusIcon: ImageVector
    val statusColor: Color
    val statusLabel: String

    when (check.status) {
        PrivacyCheckStatus.PASS -> {
            statusIcon = Icons.Default.CheckCircle
            statusColor = Color(0xFF4CAF50)
            statusLabel = "Pass"
        }
        PrivacyCheckStatus.WARNING -> {
            statusIcon = Icons.Default.Warning
            statusColor = Color(0xFFFF9800)
            statusLabel = "Warning"
        }
        PrivacyCheckStatus.FAIL -> {
            statusIcon = Icons.Default.Cancel
            statusColor = Color(0xFFF44336)
            statusLabel = "Fail"
        }
        PrivacyCheckStatus.ERROR -> {
            statusIcon = Icons.Default.Help
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
            statusLabel = "Unknown"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable { showDetail = !showDetail }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = statusLabel,
                modifier = Modifier.size(20.dp),
                tint = statusColor
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = check.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (showDetail) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = showDetail) {
            Column(modifier = Modifier.padding(top = 8.dp, start = 30.dp)) {
                Text(
                    text = check.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (check.recommendation != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = check.recommendation,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
