package com.teamz.lab.debugger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v3.2.0 honest FirstScanGate UI.
 *
 * Play policy 2026-07-10 (Deceptive Behavior clearance): the previous version ran a
 * fixed 10s theater loop labeled "Battery • RAM • Storage • Network" while reading
 * only battery charge %, with a fabricated 72 fallback. This version:
 *   - runs [FirstScanGate.runQuickScan] — four REAL subsystem reads (~1-3s)
 *   - binds the progress bar to actual check completion, no padded duration
 *   - shows the scoring weights verbatim on the result card
 *   - renders an explicit error state when nothing was readable — never a made-up score
 *
 * UI states: SCANNING (real progress) / SCORED (count-up reveal + haptic) / FAILED.
 */
@Composable
fun FirstScanGateScreen(
    onShareScore: (Int) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    var phase by rememberSaveable { mutableStateOf(Phase.SCANNING.name) }
    var progress by remember { mutableFloatStateOf(0f) }
    var checkLabel by remember { mutableStateOf("Starting…") }
    var finalScore by rememberSaveable { mutableIntStateOf(-1) }
    var subBattery by rememberSaveable { mutableIntStateOf(-1) }
    var subMemory by rememberSaveable { mutableIntStateOf(-1) }
    var subStorage by rememberSaveable { mutableIntStateOf(-1) }
    var subNetwork by rememberSaveable { mutableIntStateOf(-1) }
    var scanResult by remember { mutableStateOf<FirstScanGate.QuickScanResult?>(null) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "first-scan-progress",
    )

    // Real scan — progress advances only when a check actually finishes.
    LaunchedEffect(Unit) {
        if (phase == Phase.SCANNING.name) {
            val result = FirstScanGate.runQuickScan(context) { completed, label ->
                progress = completed / 4f
                checkLabel = label
            }
            scanResult = result
            val total = result.total
            if (total == null) {
                FirstScanGate.markScanFailed(context)
                phase = Phase.FAILED.name
            } else {
                finalScore = total
                subBattery = result.battery ?: -1
                subMemory = result.memory ?: -1
                subStorage = result.storage ?: -1
                subNetwork = result.network ?: -1
                phase = Phase.SCORED.name
            }
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
                checkLabel = checkLabel,
            )
            Phase.SCORED -> ScoredUi(
                score = finalScore,
                subBattery = subBattery,
                subMemory = subMemory,
                subStorage = subStorage,
                subNetwork = subNetwork,
                onShare = {
                    FirstScanGate.markCompleted(context, finalScore, scanResult)
                    FirstScanGate.logShareTapped(context, finalScore)
                    onShareScore(finalScore)
                },
                onDetails = {
                    FirstScanGate.markCompleted(context, finalScore, scanResult)
                    onDismiss()
                },
            )
            Phase.FAILED -> FailedUi(onContinue = onDismiss)
        }
    }
}

@Composable
private fun ScanningUi(progress: Float, checkLabel: String) {
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
            text = "Checking your device…",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = checkLabel,
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
            text = "${(progress * 4).toInt()} of 4 checks done",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ScoredUi(
    score: Int,
    subBattery: Int,
    subMemory: Int,
    subStorage: Int,
    subNetwork: Int,
    onShare: () -> Unit,
    onDetails: () -> Unit,
) {
    val grade = when {
        score >= 90 -> Grade("Excellent", Color(0xFF2E7D32))
        score >= 75 -> Grade("Great", Color(0xFF388E3C))
        score >= 60 -> Grade("Good", Color(0xFFF9A825))
        score >= 40 -> Grade("Fair", Color(0xFFEF6C00))
        else -> Grade("Needs attention", Color(0xFFC62828))
    }

    // Score reveal micro-interaction: count up from 0 + one haptic tick on settle.
    val haptic = LocalHapticFeedback.current
    var target by remember { mutableIntStateOf(0) }
    val animatedScore by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 800),
        label = "score-count-up",
        finishedListener = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
    )
    LaunchedEffect(score) { target = score }

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
            text = "$animatedScore",
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
        Spacer(Modifier.height(16.dp))
        SubScoreRow("Battery", subBattery)
        SubScoreRow("Memory", subMemory)
        SubScoreRow("Storage", subStorage)
        SubScoreRow("Network", subNetwork)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Score = battery ${FirstScanGate.WEIGHT_BATTERY} · memory ${FirstScanGate.WEIGHT_MEMORY} · storage ${FirstScanGate.WEIGHT_STORAGE} · network ${FirstScanGate.WEIGHT_NETWORK}",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(32.dp))
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

@Composable
private fun SubScoreRow(label: String, value: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
        Text(
            text = if (value >= 0) "$value" else "not readable",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (value >= 0) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun FailedUi(onContinue: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Couldn't read device state",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "None of the four checks could be completed on this device. You can still use every tool in the app.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue to app", fontSize = 16.sp)
        }
    }
}

private enum class Phase { SCANNING, SCORED, FAILED }

private data class Grade(val label: String, val color: Color)
