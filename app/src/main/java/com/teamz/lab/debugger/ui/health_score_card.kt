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
                    contentDescription = "Health Status",
                    tint = if (hasScannedToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasScannedToday) "Today's Health Score" else "Daily Health Check",
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
                            contentDescription = "Get AI insights about health score",
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (hasScannedToday && !isScanning && !scanCompleted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    Text(
                            text = "Scanned",
                        style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            maxLines = 1
                    )
                    }
                } else if (isScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scanning",
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(rotation),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Scanning...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (scanCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Scan Complete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Complete!",
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
                    .height(120.dp)
                    .clickable(onClick = onScoreClick),
                color = DesignSystemColors.NeonGreen,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
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
                        text = HealthScoreUtils.getHealthScoreMessage(healthScore),
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
                                contentDescription = "Scroll to improvements",
                                tint = DesignSystemColors.Dark.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "See improvements below",
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
                StatItem(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Streak",
                    value = "$dailyStreak days",
                    color = if (dailyStreak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                StatItem(
                    icon = Icons.Default.EmojiEvents,
                    label = "Best",
                    value = "$bestScore/10",
                    color = if (bestScore >= 8) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                StatItem(
                    icon = Icons.Default.Analytics,
                    label = "Total",
                    value = "$totalScans scans",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Streak Message (always shown)
            if (!hasScannedToday) {
                Text(
                    text = HealthScoreUtils.getStreakMessage(dailyStreak),
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
                        dailyStreak >= 7 -> "🔥 Amazing streak! You're a health champion!"
                        dailyStreak >= 3 -> "💪 Great consistency! Keep the momentum going!"
                        dailyStreak >= 1 -> "✅ Good start! Build your daily habit!"
                        else -> "🚀 Ready to start your health journey?"
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
                        contentDescription = "Scanning",
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotation)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.string(R.string.scanning_device))
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.string(R.string.scan_device_health))
                }
            }
        }
    }
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

