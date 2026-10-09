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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.R
import kotlinx.coroutines.async
import com.teamz.lab.debugger.ui.icons.DgText

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
    onChooseTest: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var phase by rememberSaveable { mutableStateOf(Phase.SCANNING.name) }
    var progress by remember { mutableFloatStateOf(0f) }
    var checksDone by remember { mutableIntStateOf(0) }
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
            // The daily 1-10 scan runs alongside the quick scan and is awaited before the score
            // shows, so the Health tab behind "See details" never says "0 scans" to someone who
            // just scanned. It is also the scan the user would otherwise be asked to run there.
            // Started in the LaunchedEffect's own scope, not a nested coroutineScope { }: that
            // would join this child even after a timeout, and cancelling blocking IO does not stop
            // it — the bound would be fiction. Here a slow record just lands after the score shows.
            val daily = async(kotlinx.coroutines.Dispatchers.IO) { FirstScanGate.recordDailyScan(context) }
            // The label FirstScanGate passes is English; the screen names the check itself.
            val result = FirstScanGate.runQuickScan(context) { completed, _ ->
                progress = completed / 4f
                checksDone = completed
            }
            // Bounded: wait at most 2 s for the daily record before showing the score.
            kotlinx.coroutines.withTimeoutOrNull(2_000L) { daily.await() }
            // The first-screen A/B arm is assigned from the server RC value only. On a fresh
            // install the score used to beat the first fetch by about a second (emulator,
            // 2026-10-09), leaving the user out of the experiment. Usually already done by now;
            // bounded so an offline first launch waits at most 2.5 s more.
            com.teamz.lab.debugger.utils.RcFetchGate.await(2_500L)
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
                checkLabel = stringResource(
                    when (checksDone) {
                        1 -> R.string.first_scan_battery
                        2 -> R.string.first_scan_memory
                        3 -> R.string.first_scan_storage
                        4 -> R.string.first_scan_network
                        else -> R.string.first_scan_starting
                    }
                ),
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
                // A/B (spec 2026-10-09): arm B gets Camera / Mic / Screen here — what the store
                // listing sells — instead of landing everyone on the health report.
                // arm() assigns once, from the server RC value only; remember{} so a fetch landing
                // mid-screen cannot swap the UI under the user.
                showChooser = onChooseTest != null &&
                    remember { com.teamz.lab.debugger.utils.FirstScreenExperiment.arm(context) } == "B",
                onChoose = { choice ->
                    FirstScanGate.markCompleted(context, finalScore, scanResult)
                    onChooseTest?.invoke(choice)
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
            text = stringResource(R.string.first_scan_checking),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        DgText(
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
        DgText(
            text = stringResource(R.string.first_scan_checks_done, (progress * 4).toInt(), 4),
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
    showChooser: Boolean = false,
    onChoose: (String) -> Unit = {},
) {
    val grade = when {
        score >= 90 -> Grade(stringResource(R.string.first_scan_grade_excellent), Color(0xFF2E7D32))
        score >= 75 -> Grade(stringResource(R.string.first_scan_grade_great), Color(0xFF388E3C))
        score >= 60 -> Grade(stringResource(R.string.first_scan_grade_good), Color(0xFFF9A825))
        score >= 40 -> Grade(stringResource(R.string.first_scan_grade_fair), Color(0xFFEF6C00))
        else -> Grade(stringResource(R.string.first_scan_grade_needs_attention), Color(0xFFC62828))
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
            text = stringResource(R.string.first_scan_title),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(12.dp))
        DgText(
            text = "$animatedScore",
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = grade.color,
        )
        Spacer(Modifier.height(4.dp))
        DgText(
            text = grade.label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = grade.color,
        )
        Spacer(Modifier.height(16.dp))
        SubScoreRow(stringResource(R.string.first_scan_battery), subBattery)
        SubScoreRow(stringResource(R.string.first_scan_memory), subMemory)
        SubScoreRow(stringResource(R.string.first_scan_storage), subStorage)
        SubScoreRow(stringResource(R.string.first_scan_network), subNetwork)
        Spacer(Modifier.height(12.dp))
        DgText(
            text = stringResource(
                R.string.first_scan_weights,
                FirstScanGate.WEIGHT_BATTERY,
                FirstScanGate.WEIGHT_MEMORY,
                FirstScanGate.WEIGHT_STORAGE,
                FirstScanGate.WEIGHT_NETWORK,
            ),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(24.dp))
        if (showChooser) {
            Text(
                text = stringResource(R.string.first_scan_chooser_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val choices = listOf(
                    R.string.tab_camera to "camera",
                    R.string.first_scan_choice_mic to "mic",
                    R.string.first_scan_choice_screen to "screen",
                )
                for ((labelRes, key) in choices) {
                    Button(
                        onClick = { onChoose(key) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        DgText(
                            stringResource(labelRes),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.first_scan_share_score), fontSize = 16.sp)
            }
            TextButton(onClick = { onChoose("report") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.first_scan_full_report), fontSize = 14.sp)
            }
        } else {
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    stringResource(R.string.first_scan_share_score),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDetails,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.first_scan_see_details), fontSize = 16.sp)
            }
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
        DgText(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
        DgText(
            text = if (value >= 0) "$value" else stringResource(R.string.first_scan_not_readable),
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
            text = stringResource(R.string.first_scan_failed_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.first_scan_failed_body),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.first_scan_continue_to_app), fontSize = 16.sp)
        }
    }
}

private enum class Phase { SCANNING, SCORED, FAILED }

private data class Grade(val label: String, val color: Color)
