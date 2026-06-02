package com.teamz.lab.debugger.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.TrustCheckResult
import com.teamz.lab.debugger.utils.TrustCheckStatus
import com.teamz.lab.debugger.utils.TrustSection
import com.teamz.lab.debugger.utils.ZeroTrustReport
import com.teamz.lab.debugger.ui.theme.AppTheme
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.ui.theme.useThemeManager
import com.teamz.lab.debugger.utils.ZeroTrustScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zero Trust Dashboard -- a prominent card showing an aggregated trust
 * score with three expandable sections (App Privacy, Network Trust,
 * Device Integrity). Designed to sit in the Device Info or Health tab.
 */
@Composable
fun ZeroTrustDashboard(
    onAIClick: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = useThemeManager().getEffectiveTheme() == AppTheme.DESIGN_SYSTEM_DARK

    var report by remember { mutableStateOf<ZeroTrustReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isExpanded by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var trustShareText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val result = withContext(Dispatchers.IO) {
                ZeroTrustScorer.runZeroTrustAudit(context)
            }
            report = result
            AnalyticsUtils.logEvent(
                AnalyticsEvent.ZeroTrustScanCompleted,
                mapOf("score" to result.compositeScore, "grade" to result.compositeGrade)
            )
            com.teamz.lab.debugger.utils.EngagementTracker.trackSignificantAction(
                context,
                com.teamz.lab.debugger.utils.SignificantAction.SCAN_COMPLETED,
                mapOf("scan_type" to "zero_trust", "score" to result.compositeScore)
            )
        } catch (_: Exception) {
            // Graceful degradation
        } finally {
            isLoading = false
        }
    }

    if (showShareDialog) {
        ViralShareDialog(
            onDismiss = { showShareDialog = false },
            context = context,
            shareText = trustShareText,
            showReferralCode = true
        )
    }

    AppCard(bottomPadding = 12) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isExpanded = !isExpanded
                        if (isExpanded) {
                            AnalyticsUtils.logEvent(AnalyticsEvent.ZeroTrustScanStarted)
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left shield icon — design system: neon green in dark mode when score good
                val headerGoodScore = (report?.compositeScore ?: 0) >= 80
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isLoading -> MaterialTheme.colorScheme.surfaceVariant
                        headerGoodScore && isDark -> DesignSystemColors.NeonGreen.copy(alpha = 0.12f)
                        headerGoodScore -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                        (report?.compositeScore ?: 0) >= 50 -> Color(0xFFFF9800).copy(alpha = 0.12f)
                        else -> Color(0xFFF44336).copy(alpha = 0.12f)
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Trust",
                            modifier = Modifier.size(24.dp),
                            tint = when {
                                isLoading -> MaterialTheme.colorScheme.onSurfaceVariant
                                headerGoodScore && isDark -> DesignSystemColors.NeonGreen
                                headerGoodScore -> Color(0xFF4CAF50)
                                (report?.compositeScore ?: 0) >= 50 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Security Dashboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            isLoading -> "Checking how safe your phone is..."
                            report != null -> "One simple score for your phone and connection: ${report!!.compositeScore}/100"
                            else -> "Tap to see details"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isLoading && report != null) {
                    val gradeGood = report!!.compositeScore >= 80
                    val gradeOk = report!!.compositeScore >= 50
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            gradeGood && isDark -> DesignSystemColors.NeonGreen
                            gradeGood && !isDark -> MaterialTheme.colorScheme.primaryContainer
                            gradeOk -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = report!!.compositeGrade,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    gradeGood && isDark -> DesignSystemColors.Dark
                                    gradeGood && !isDark -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> Color.White
                                },
                                fontSize = if (report!!.compositeGrade.length > 1) 12.sp else 16.sp
                            )
                        }
                    }
                } else if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }

                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded && !isLoading && report != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                report?.let { r ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        // Score arc (design system: neon green in dark mode for good score)
                        TrustScoreArc(score = r.compositeScore, isDark = isDark)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Sections
                        r.sections.forEach { section ->
                            TrustSectionCard(
                                section = section,
                                onAIClick = onAIClick,
                                isDark = isDark
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action buttons (design system: no neon in light mode)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    trustShareText = ZeroTrustScorer.generateShareText(r, context)
                                    showShareDialog = true
                                    AnalyticsUtils.logEvent(AnalyticsEvent.ZeroTrustShared)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                colors = if (isDark) ButtonDefaults.buttonColors(
                                    containerColor = DesignSystemColors.NeonGreen,
                                    contentColor = DesignSystemColors.Dark
                                ) else ButtonDefaults.buttonColors()
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", style = MaterialTheme.typography.labelLarge)
                            }

                            if (onAIClick != null) {
                                OutlinedButton(
                                    onClick = {
                                        val aiPrompt = ZeroTrustScorer.generateAIPromptText(r)
                                        onAIClick("Security Dashboard", aiPrompt)
                                        AnalyticsUtils.logEvent(AnalyticsEvent.ZeroTrustAIClicked)
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
                                    Text("Ask AI", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustScoreArc(score: Int, isDark: Boolean) {
    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 1000),
        label = "trust_score"
    )
    val arcColor = when {
        score >= 80 && isDark -> DesignSystemColors.NeonGreen
        score >= 80 -> Color(0xFF4CAF50)
        score >= 50 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    // Score number sits on dark background — use neon (not Dark) so it's readable
    val scoreTextColor = when {
        score >= 80 && isDark -> DesignSystemColors.NeonGreen
        else -> arcColor
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            val strokeWidth = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset((size.width - radius * 2) / 2, (size.height - radius * 2) / 2)
            val arcSize = Size(radius * 2, radius * 2)

            drawArc(trackColor, 135f, 270f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            drawArc(arcColor, 135f, 270f * animatedProgress, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = scoreTextColor)
            Text("/ 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrustSectionCard(
    section: TrustSection,
    onAIClick: ((String, String) -> Unit)? = null,
    isDark: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val sectionColor = when {
        section.score >= 80 && isDark -> DesignSystemColors.NeonGreen
        section.score >= 80 -> Color(0xFF4CAF50)
        section.score >= 50 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    // Section score sits on dark card background — use neon (not Dark) so it's readable
    val sectionScoreTextColor = when {
        section.score >= 80 && isDark -> DesignSystemColors.NeonGreen
        else -> sectionColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .animateContentSize()
    ) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    expanded = !expanded
                    if (expanded) {
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.ZeroTrustSectionExpanded,
                            mapOf("section" to section.name)
                        )
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = section.icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "${section.score}/100",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = sectionScoreTextColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section checks
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                section.checks.forEach { check ->
                    TrustCheckRow(check = check)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun TrustCheckRow(check: TrustCheckResult) {
    var showDetail by remember { mutableStateOf(false) }
    val statusColor = when (check.status) {
        TrustCheckStatus.PASS -> Color(0xFF4CAF50)
        TrustCheckStatus.WARNING -> Color(0xFFFF9800)
        TrustCheckStatus.FAIL -> Color(0xFFF44336)
        TrustCheckStatus.ERROR -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusIcon = when (check.status) {
        TrustCheckStatus.PASS -> Icons.Default.CheckCircle
        TrustCheckStatus.WARNING -> Icons.Default.Warning
        TrustCheckStatus.FAIL -> Icons.Default.Cancel
        TrustCheckStatus.ERROR -> Icons.Default.Help
    }
    val statusLabel = when (check.status) {
        TrustCheckStatus.PASS -> "OK"
        TrustCheckStatus.WARNING -> "Careful"
        TrustCheckStatus.FAIL -> "Issue"
        TrustCheckStatus.ERROR -> "?"
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
            Icon(statusIcon, contentDescription = statusLabel, modifier = Modifier.size(20.dp), tint = statusColor)
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
