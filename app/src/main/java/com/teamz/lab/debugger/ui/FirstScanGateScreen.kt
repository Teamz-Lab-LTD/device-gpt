package com.teamz.lab.debugger.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * v3.1.11 Week 1 retention milestone — FirstScanGate Composable UI.
 *
 * Renders a 10s animated scan + Device Score + Share/Details CTAs. Consumed
 * by [com.teamz.lab.debugger.MainActivity] cold-start path when
 * [FirstScanGate.currentState] returns SCANNING.
 *
 * UI states (internal to this Composable):
 *   - Phase.SCANNING : progress bar 0->100 over ~10s
 *   - Phase.SCORED   : big number + percentile + 2 CTAs
 *
 * On Share CTA: invokes [onShareScore] (caller passes the share-dialog handle).
 * On Details CTA: invokes [onDismiss] (caller renders the normal tab UI).
 * Both flows call [FirstScanGate.markCompleted] so the gate never re-fires.
 */
@Composable
fun FirstScanGateScreen(
    onShareScore: (Int) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    var phase by rememberSaveable { mutableStateOf(Phase.SCANNING.name) }
    var progress by remember { mutableFloatStateOf(0f) }
    var finalScore by rememberSaveable { mutableIntStateOf(-1) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "first-scan-progress",
    )

    // 10s scan with progress ticks every 100ms (100 ticks total)
    LaunchedEffect(Unit) {
        if (phase == Phase.SCANNING.name) {
            for (i in 1..100) {
                delay(100)
                progress = i / 100f
            }
            finalScore = FirstScanGate.computeQuickScore(context)
            phase = Phase.SCORED.name
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (Phase.valueOf(phase)) {
            Phase.SCANNING -> ScanningUi(
                progress = animatedProgress,
                percent = (animatedProgress * 100).toInt(),
            )
            Phase.SCORED -> ScoredUi(
                score = finalScore,
                onShare = {
                    FirstScanGate.markCompleted(context, finalScore)
                    FirstScanGate.logShareTapped(context, finalScore)
                    onShareScore(finalScore)
                },
                onDetails = {
                    FirstScanGate.markCompleted(context, finalScore)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun ScanningUi(progress: Float, percent: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Scanning your device…",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Battery • RAM • Storage • Network",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(32.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "$percent%",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ScoredUi(score: Int, onShare: () -> Unit, onDetails: () -> Unit) {
    val grade = when {
        score >= 90 -> Grade("Excellent", Color(0xFF2E7D32))
        score >= 75 -> Grade("Great", Color(0xFF388E3C))
        score >= 60 -> Grade("Good", Color(0xFFF9A825))
        score >= 40 -> Grade("Fair", Color(0xFFEF6C00))
        else -> Grade("Needs attention", Color(0xFFC62828))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Your Device Score",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "$score",
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = grade.color,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = grade.label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = grade.color,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Based on battery, RAM, storage and network health.",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Share my score", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDetails,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("See details", fontSize = 16.sp)
        }
    }
}

private enum class Phase { SCANNING, SCORED }

private data class Grade(val label: String, val color: Color)
