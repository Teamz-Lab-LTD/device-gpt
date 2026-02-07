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
import com.teamz.lab.debugger.utils.NetworkReachabilityTester
import com.teamz.lab.debugger.utils.ReachabilityReport
import com.teamz.lab.debugger.utils.ReachabilityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NetworkReachabilityCard(
    onAIClick: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var report by remember { mutableStateOf<ReachabilityReport?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareText by remember { mutableStateOf("") }
    var hasRun by remember { mutableStateOf(false) }

    fun runTest() {
        if (isLoading) return
        coroutineScope.launch {
            isLoading = true
            AnalyticsUtils.logEvent(AnalyticsEvent.ReachabilityTestStarted)
            try {
                val result = withContext(Dispatchers.IO) {
                    NetworkReachabilityTester.runReachabilityTest(context)
                }
                report = result
                hasRun = true
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.ReachabilityTestCompleted,
                    mapOf("score" to result.opennessScore, "level" to result.restrictionLevel)
                )
            } catch (_: Exception) {
                // Graceful fallback
            } finally {
                isLoading = false
            }
        }
    }

    if (showShareDialog && shareText.isNotEmpty()) {
        ViralShareDialog(
            onDismiss = { showShareDialog = false },
            context = context,
            shareText = shareText,
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
                        if (!hasRun && !isLoading) {
                            runTest()
                        }
                        isExpanded = !isExpanded
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        !hasRun -> MaterialTheme.colorScheme.surfaceVariant
                        (report?.opennessScore ?: 0) >= 80 -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                        (report?.opennessScore ?: 0) >= 50 -> Color(0xFFFF9800).copy(alpha = 0.12f)
                        else -> Color(0xFFF44336).copy(alpha = 0.12f)
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Reachability",
                            modifier = Modifier.size(24.dp),
                            tint = when {
                                !hasRun -> MaterialTheme.colorScheme.onSurfaceVariant
                                (report?.opennessScore ?: 0) >= 80 -> Color(0xFF4CAF50)
                                (report?.opennessScore ?: 0) >= 50 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Network Reachability",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            isLoading -> "Testing connectivity..."
                            hasRun && report != null -> "${report!!.opennessScore}/100 \u2022 ${report!!.restrictionLevel}"
                            else -> "Tap to test service connectivity"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                } else if (hasRun && report != null) {
                    // Score badge
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            report!!.opennessScore >= 80 -> Color(0xFF4CAF50)
                            report!!.opennessScore >= 50 -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${report!!.opennessScore}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
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
                visible = isExpanded && hasRun && report != null && !isLoading,
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
                        // Context info
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                ContextLine("\uD83D\uDCE1 DNS", r.dnsServers.take(30))
                                ContextLine("\uD83D\uDD12 Private DNS", if (r.privateDnsEnabled) "Enabled" else "Disabled")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                ContextLine("\uD83D\uDEE1\uFE0F VPN", if (r.vpnActive) "Active" else "Off")
                                r.quicHint?.let {
                                    ContextLine("\uD83D\uDD17 QUIC", if (it.udpOpen) "UDP open" else "UDP blocked")
                                }
                            }
                        }

                        if (r.captivePortalDetected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFF9800).copy(alpha = 0.12f)
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Captive portal detected -- login may be required", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Domain results
                        r.probes.forEach { probe ->
                            DomainResultRow(probe)
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    shareText = NetworkReachabilityTester.generateShareText(r, context)
                                    showShareDialog = true
                                    AnalyticsUtils.logEvent(AnalyticsEvent.ReachabilityResultShared)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", style = MaterialTheme.typography.labelMedium)
                            }

                            if (onAIClick != null) {
                                OutlinedButton(
                                    onClick = {
                                        val prompt = NetworkReachabilityTester.generateAIPromptText(r)
                                        onAIClick("Network Reachability", prompt)
                                        AnalyticsUtils.logEvent(AnalyticsEvent.ReachabilityAIClicked)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = com.teamz.lab.debugger.utils.AIIcon.color()
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ask AI", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            OutlinedButton(
                                onClick = { runTest() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retest", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

@Composable
private fun DomainResultRow(probe: com.teamz.lab.debugger.utils.DomainProbeResult) {
    val isReachable = probe.overallStatus == ReachabilityStatus.REACHABLE
    val statusColor = if (isReachable) Color(0xFF4CAF50) else Color(0xFFF44336)
    val statusIcon = if (isReachable) Icons.Default.CheckCircle else Icons.Default.Cancel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = statusColor
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = probe.domain,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = when (probe.overallStatus) {
                ReachabilityStatus.REACHABLE -> "${probe.httpsLatencyMs}ms"
                ReachabilityStatus.DNS_BLOCKED -> "DNS blocked"
                ReachabilityStatus.TLS_BLOCKED -> "TLS blocked"
                ReachabilityStatus.TCP_BLOCKED -> "TCP blocked"
                else -> probe.errorDetail ?: "Error"
            },
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
