package com.teamz.lab.debugger.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.utils.HealthScoreUtils
import android.content.Context
import androidx.compose.ui.draw.rotate
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import java.text.SimpleDateFormat
import java.util.*
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.string
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.sp


@Composable
fun HealthScoreCard(
    context: Context,
    onScanClick: () -> Unit,
    isScanning: Boolean = false,
    scanCompleted: Boolean = false,
    rotation: Float = 0f,
    onScoreClick: () -> Unit = {},
    onAIClick: (() -> Unit)? = null
) {
    // Make UI reactive to data changes - use async state to prevent ANR
    var healthScore by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        healthScore = HealthScoreUtils.calculateDailyHealthScore(context)
    }
    val dailyStreak by remember { derivedStateOf { HealthScoreUtils.getDailyStreak(context) } }
    val bestScore by remember { derivedStateOf { HealthScoreUtils.getBestScore(context) } }
    val totalScans by remember { derivedStateOf { HealthScoreUtils.getTotalScans(context) } }
    val lastScanDate by remember { derivedStateOf { HealthScoreUtils.getLastScanDate(context) } }
    
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val hasScannedToday by remember { derivedStateOf { lastScanDate == today } }

    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (hasScannedToday) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    contentDescription = context.string(R.string.health_status_cd),
                    tint = if (hasScannedToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = context.string(
                        if (hasScannedToday) R.string.health_today_score else R.string.health_daily_check
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = context.string(R.string.health_ai_score_cd),
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (hasScannedToday && !isScanning && !scanCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = context.string(R.string.health_scanned_today_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (isScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = context.string(R.string.cd_scanning),
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(rotation),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = context.string(R.string.health_scanning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (scanCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = context.string(R.string.health_scan_complete_cd),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = context.string(R.string.health_complete),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Health Score Display
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScoreClick),
                color = DesignSystemColors.NeonGreen,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$healthScore/10",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DesignSystemColors.Dark
                    )
                    Text(
                        text = context.string(healthScoreMessageRes(healthScore)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DesignSystemColors.Dark,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    if (healthScore < 8) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .clickable(onClick = onScoreClick)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = context.string(R.string.health_scroll_improvements_cd),
                                tint = DesignSystemColors.Dark.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = context.string(R.string.health_see_improvements),
                                style = MaterialTheme.typography.bodySmall,
                                color = DesignSystemColors.Dark.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Play policy 2026-07-10: streak stat -> factual recency stat
                // (streak gamification killed per research insight #7).
                StatItem(
                    icon = Icons.Default.LocalFireDepartment,
                    label = context.string(R.string.health_stat_scanned),
                    value = when {
                        dailyStreak > 1 -> context.string(R.string.health_days_many, dailyStreak)
                        dailyStreak == 1 -> context.string(R.string.health_days_one, dailyStreak)
                        else -> context.string(R.string.health_not_yet)
                    },
                    color = if (dailyStreak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                StatItem(
                    icon = Icons.Default.EmojiEvents,
                    label = context.string(R.string.health_stat_best),
                    value = "$bestScore/10",
                    color = if (bestScore >= 8) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                StatItem(
                    icon = Icons.Default.Analytics,
                    label = context.string(R.string.health_stat_total),
                    value = context.string(
                        if (totalScans == 1) R.string.health_scans_one else R.string.health_scans_many,
                        totalScans,
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Streak Message (always shown)
            if (!hasScannedToday) {
                Text(
                    text = when {
                        dailyStreak > 1 -> context.string(R.string.health_streak_many, dailyStreak)
                        dailyStreak == 1 -> context.string(R.string.health_streak_one, dailyStreak)
                        else -> context.string(R.string.health_streak_none)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // Motivational message for users who already scanned today
                Text(
                    text = when {
                        dailyStreak >= 7 -> context.string(R.string.amazing_streak)
                        dailyStreak >= 3 -> context.string(R.string.great_consistency)
                        dailyStreak >= 1 -> context.string(R.string.good_start)
                        else -> context.string(R.string.ready_to_start)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Scan Button (always visible)
            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !isScanning
            ) {
                if (isScanning) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = context.string(R.string.cd_scanning),
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotation)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.string(R.string.scanning_device))
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = context.string(R.string.cd_scan),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.string(R.string.scan_device_health))
                }
            }
        }
    }
}

/**
 * The line under the score, as a string resource. Same thresholds as
 * [HealthScoreUtils.getHealthScoreMessage], which stays English because the AI-facing
 * app functions read it; change both together.
 */
@androidx.annotation.StringRes
private fun healthScoreMessageRes(score: Int): Int = when {
    score >= 9 -> R.string.health_msg_excellent
    score >= 7 -> R.string.health_msg_good
    score >= 5 -> R.string.health_msg_fair
    else -> R.string.health_msg_needs_attention
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

