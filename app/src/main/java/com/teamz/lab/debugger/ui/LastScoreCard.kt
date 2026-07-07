package com.teamz.lab.debugger.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils

/**
 * v3.1.12 — closes the "one-shot FirstScanGate" gap.
 *
 * Previously: FirstScanGate fired ONCE on first launch (behind 50% RC gate).
 * User saw the 10s scan + 0-100 Device Score once, then it was gone forever.
 * The score + timestamp were saved in SharedPreferences but not surfaced
 * anywhere in the UI. Users who valued the score had no way to re-run it —
 * frustrating for them, wasted data for us.
 *
 * This card renders on the Health tab (top of hero area) when a user has
 * completed at least one first-scan. Shows:
 *   - Their last score with color-coded verdict
 *   - How long ago they scanned
 *   - "Run again" button → clears gate state → Activity.recreate() → gate re-fires
 *   - "Share" button → same viral share as first gate
 *
 * If [FirstScanGate.hasCompletedScan] is false this composable renders NOTHING —
 * the caller wraps it in a conditional check so the LazyColumn item is empty.
 * That preserves the pre-v3.1.12 Health tab layout for the RC control cohort
 * (users who never saw the gate).
 */
@Composable
fun LastScoreCard(
    onShareClick: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val score = remember { FirstScanGate.getLastScanScore(context) }
    val timestamp = remember { FirstScanGate.getLastScanTimestamp(context) }

    if (score < 0 || timestamp <= 0L) return

    LaunchedEffect(Unit) {
        try {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.DeviceScoreCardViewed,
                mapOf("score" to score)
            )
        } catch (_: Throwable) { /* analytics not critical */ }
    }

    val verdict = verdictFor(score)
    val verdictColor = verdictColor(score)
    val agoLabel = timeAgoLabel(timestamp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Your Device Score",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = score.toString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = verdictColor
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = verdict,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = verdictColor
                    )
                    Text(
                        text = agoLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        FirstScanGate.clearForReplay(context)
                        (context as? Activity)?.recreate()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignSystemColors.NeonGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Run again", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = {
                        try {
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.DeviceScoreCardShared,
                                mapOf("score" to score)
                            )
                        } catch (_: Throwable) { /* analytics not critical */ }
                        onShareClick(score)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Share")
                }
            }
        }
    }
}

internal fun verdictFor(score: Int): String = when {
    score >= 90 -> "Excellent"
    score >= 75 -> "Good"
    score >= 60 -> "Fair"
    score >= 40 -> "Poor"
    else -> "Critical"
}

internal fun verdictColor(score: Int): Color = when {
    score >= 90 -> Color(0xFF2E7D32)
    score >= 75 -> Color(0xFF66BB6A)
    score >= 60 -> Color(0xFFF9A825)
    score >= 40 -> Color(0xFFEF6C00)
    else -> Color(0xFFC62828)
}

internal fun timeAgoLabel(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diffMs = (nowMs - timestampMs).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        mins < 1L -> "just now"
        mins < 60L -> "${mins}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        days < 30L -> "${days / 7L}w ago"
        else -> "${days / 30L}mo ago"
    }
}
