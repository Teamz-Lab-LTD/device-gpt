package com.teamz.lab.debugger.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.AIIcon
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.MicTestUtils
import com.teamz.lab.debugger.utils.PermissionManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.teamz.lab.debugger.ui.icons.DgText
import com.teamz.lab.debugger.ui.theme.DgMotion
import com.teamz.lab.debugger.ui.theme.motionTween
import com.teamz.lab.debugger.ui.components.pressScale
import com.teamz.lab.debugger.ui.components.popOnce
import com.teamz.lab.debugger.ui.components.shakeOnce
import com.teamz.lab.debugger.ui.components.riseInOnAppear
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.teamz.lab.debugger.ui.components.touchTarget
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize

/**
 * Microphone test — the feature the store listing has been promising.
 *
 * The listing says "Mic test — input level, noise floor, instant playback" and the
 * app title is "Battery, Mic Test: DeviceGPT", but until 2026-08-26 no AudioRecord
 * existed anywhere in this codebase. This card makes those three claims true, in that
 * order: measure the room, measure the voice, play it back.
 *
 * The verdict is the USER'S. The app reports dBFS and headroom, then asks "did you
 * hear yourself?" — because whether a microphone sounds correct is not decidable
 * without a calibrated reference, and inventing a score is the same impossible-claim
 * shape that got this app rejected before.
 */
@Composable
fun MicTestCard(
    activity: android.app.Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null,
    onResultChanged: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(PermissionManager.hasAudioPermission(context))
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf<MicPhase>(MicPhase.IDLE) }
    var liveDb by remember { mutableDoubleStateOf(MicTestUtils.MIN_DBFS) }
    var noiseFloor by remember { mutableStateOf<Double?>(null) }
    var result by remember { mutableStateOf<MicTestUtils.MicTestResult?>(null) }
    var pcm by remember { mutableStateOf<ShortArray?>(null) }
    var micUnavailable by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<Boolean?>(null) }

    val permissionHost = activity ?: context as? android.app.Activity

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            // This used to be `permanentlyDenied = true` on the FIRST denial, which sent a
            // user who tapped Deny once to a Settings deep link when tapping Allow again
            // would have worked. Android's own signal is shouldShowRequestPermissionRationale:
            // still true after one denial, false once the user has denied twice.
            val canShowRationale = permissionHost?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it, Manifest.permission.RECORD_AUDIO
                )
            }
            permanentlyDenied = MicTestUtils.permissionNextStep(false, canShowRationale) ==
                MicTestUtils.PermissionNextStep.OPEN_SETTINGS
            AnalyticsUtils.logEvent(
                AnalyticsEvent.MicTestFailed,
                mapOf("reason" to if (permanentlyDenied) "permission_blocked" else "permission_denied")
            )
        }
    }

    // The Settings deep link below is this card's own recovery path, and a permission read
    // captured once by remember() cannot see the grant the user just made there — so the card
    // kept saying "open Settings" after they already had. Re-read on every resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = PermissionManager.hasAudioPermission(context)
                hasPermission = granted
                if (granted) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun buildReport(r: MicTestUtils.MicTestResult, userHeard: Boolean?): String = buildString {
        appendLine("MICROPHONE TEST")
        appendLine("Room noise floor: ${r.noiseFloorDbfs.roundToInt()} dBFS")
        appendLine("Loudest while speaking: ${r.peakDbfs.roundToInt()} dBFS")
        appendLine("Average while speaking: ${r.averageDbfs.roundToInt()} dBFS")
        appendLine("Rise above the room: ${r.headroomDb.roundToInt()} dB")
        appendLine("Input overdriven (clipping): ${if (r.clipped) "yes" else "no"}")
        appendLine("Samples captured: ${r.sampleCount}")
        appendLine(
            "Playback heard by user: " + when (userHeard) {
                true -> "yes"
                false -> "NO — user could not hear the recording"
                null -> "not answered yet"
            }
        )
        appendLine()
        appendLine(
            "Reading: 'rise above the room' is the number that matters. A dead mic in " +
                "a loud room can still show a high absolute level, so the absolute " +
                "figure alone cannot tell a working mic from a broken one."
        )
        appendLine(
            "Not a score: this reports what was measured and what the user heard. " +
                "Whether a microphone sounds correct cannot be decided without a " +
                "calibrated reference signal."
        )
    }

    fun runTest() {
        if (phase != MicPhase.IDLE) return
        scope.launch {
            micUnavailable = false
            heard = null
            result = null
            AnalyticsUtils.logEvent(AnalyticsEvent.MicTestStarted)

            // 1. Noise floor — measure the room while the user stays quiet.
            phase = MicPhase.NOISE_FLOOR
            val quiet = MicTestUtils.record(1500) { liveDb = it.dbfs }
            if (quiet == null) {
                micUnavailable = true
                phase = MicPhase.IDLE
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.MicTestFailed, mapOf("reason" to "record_unavailable")
                )
                return@launch
            }
            val floor = MicTestUtils.toDbfs(MicTestUtils.rms(quiet.first))
            noiseFloor = floor

            // 2. Voice — measure while the user speaks.
            phase = MicPhase.SPEAKING
            var peak = MicTestUtils.MIN_DBFS
            val spoken = MicTestUtils.record(3000) {
                liveDb = it.dbfs
                if (it.dbfs > peak) peak = it.dbfs
            }
            if (spoken == null) {
                micUnavailable = true
                phase = MicPhase.IDLE
                // The noise-floor failure above logs MicTestFailed and this one did not, so a
                // mic taken mid-test — incoming call, another app grabbing it — was invisible.
                // It is also the likelier of the two to fail: 3000ms of capture against 1500ms.
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.MicTestFailed,
                    mapOf("reason" to "record_unavailable_while_speaking")
                )
                return@launch
            }
            pcm = spoken.first
            val avg = MicTestUtils.toDbfs(MicTestUtils.rms(spoken.first))
            val r = MicTestUtils.MicTestResult(
                noiseFloorDbfs = floor,
                peakDbfs = peak,
                averageDbfs = avg,
                clipped = spoken.second,
                sampleCount = spoken.first.size
            )
            result = r
            onResultChanged(buildReport(r, null))

            // 3. Instant playback — the third claim in the listing.
            phase = MicPhase.PLAYBACK
            MicTestUtils.playback(spoken.first)
            phase = MicPhase.ASK_HEARD
            AnalyticsUtils.logEvent(
                AnalyticsEvent.MicTestCompleted,
                mapOf(
                    "headroom_db" to r.headroomDb.roundToInt(),
                    "clipped" to r.clipped,
                    "verdict" to MicTestUtils.classify(
                        r.peakDbfs, r.headroomDb, r.clipped
                    ).name
                )
            )
        }
    }

    AppCard(bottomPadding = 12) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.mic_test_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.mic_test_subtitle),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                result?.let { r ->
                    if (onItemAIClick != null) {
                        IconButton(
                            modifier = Modifier.touchTarget(40.dp),
                            onClick = { onItemAIClick("Mic Test", buildReport(r, heard)) }
                        ) {
                            Icon(AIIcon.icon, contentDescription = stringResource(R.string.ask_ai), tint = AIIcon.color())
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                !hasPermission -> {
                    DgText(
                        stringResource(
                            if (permanentlyDenied) R.string.mic_test_perm_blocked
                            else R.string.mic_test_perm_needed
                        ),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (permanentlyDenied) {
                                // The system dialog will not reappear; only Settings can grant now.
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) { /* nothing further is safe to do */ }
                            } else {
                                permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        DgText(
                            stringResource(
                                if (permanentlyDenied) R.string.mic_test_open_settings
                                else R.string.mic_test_grant
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                micUnavailable -> {
                    // Permission held but the platform still refused: mic held by a call,
                    // another app, or no input device. Say which, do not dead-end.
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.mic_test_unavailable),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { micUnavailable = false },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.mic_test_retry)) }
                }

                else -> {
                    MicPhaseBody(
                        phase = phase,
                        liveDb = liveDb,
                        noiseFloor = noiseFloor,
                        result = result,
                        heard = heard,
                        onStart = { runTest() },
                        onReplay = {
                            pcm?.let { data ->
                                scope.launch {
                                    phase = MicPhase.PLAYBACK
                                    MicTestUtils.playback(data)
                                    phase = MicPhase.ASK_HEARD
                                }
                            }
                        },
                        onHeard = { yes ->
                            heard = yes
                            phase = MicPhase.IDLE
                            result?.let { onResultChanged(buildReport(it, yes)) }
                            AnalyticsUtils.logEvent(
                                AnalyticsEvent.MicTestPlaybackAnswered,
                                mapOf("heard" to yes)
                            )
                            // The test is finished only once this is answered; earlier, the
                            // "Done" card covered the question (emulator, 2026-10-09).
                            val cardShown = com.teamz.lab.debugger.utils.TestDoneCard.onTestCompleted(context)
                            // AFTER the test, never before: an interstitial plays audio,
                            // and this test opens by asking the user to stay silent while
                            // the room's noise floor is measured. An ad first would bleed
                            // into that measurement and contradict the on-screen
                            // instruction. Completion is the honest break point.
                            // Not when the Done card just opened: the ad would cover it.
                            if (!cardShown) activity?.let { act ->
                                com.teamz.lab.debugger.utils.InterstitialAdManager
                                    .showAdBeforeAction(act, "mic_test_complete") { }
                            }
                        }
                    )
                }
            }
        }
    }
}

private enum class MicPhase { IDLE, NOISE_FLOOR, SPEAKING, PLAYBACK, ASK_HEARD }

@Composable
private fun MicPhaseBody(
    phase: MicPhase,
    liveDb: Double,
    noiseFloor: Double?,
    result: MicTestUtils.MicTestResult?,
    heard: Boolean?,
    onStart: () -> Unit,
    onReplay: () -> Unit,
    onHeard: (Boolean) -> Unit,
) {
    val instruction = when (phase) {
        MicPhase.NOISE_FLOOR -> stringResource(R.string.mic_test_step_quiet)
        MicPhase.SPEAKING -> stringResource(R.string.mic_test_step_speak)
        MicPhase.PLAYBACK -> stringResource(R.string.mic_test_step_playback)
        MicPhase.ASK_HEARD -> stringResource(R.string.mic_test_step_confirm)
        MicPhase.IDLE -> stringResource(R.string.mic_test_step_ready)
    }
    DgText(
        instruction,
        fontSize = 14.sp,
        fontWeight = if (phase == MicPhase.IDLE) FontWeight.Normal else FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )

    if (phase == MicPhase.NOISE_FLOOR || phase == MicPhase.SPEAKING) {
        Spacer(Modifier.height(10.dp))
        LevelMeter(liveDb)
    }

    result?.let { r ->
        Spacer(Modifier.height(12.dp))
        MeasureRow(stringResource(R.string.mic_test_room), "${r.noiseFloorDbfs.roundToInt()} dBFS")
        MeasureRow(stringResource(R.string.mic_test_voice), "${r.peakDbfs.roundToInt()} dBFS")
        MeasureRow(stringResource(R.string.mic_test_headroom), "${r.headroomDb.roundToInt()} dB")
        if (r.clipped) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.mic_test_clipping),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    } ?: noiseFloor?.let {
        Spacer(Modifier.height(8.dp))
        MeasureRow(stringResource(R.string.mic_test_room), "${it.roundToInt()} dBFS")
    }

    Spacer(Modifier.height(12.dp))

    when (phase) {
        MicPhase.ASK_HEARD -> {
            // The answer is needed now, so bring the two buttons up clear of the floating buttons: they
            // used to appear underneath them (emulator, 320dp, 2026-10-09).
            val answerRequester = remember { BringIntoViewRequester() }
            var answerSize by remember { mutableStateOf(IntSize.Zero) }
            val clearancePx = with(LocalDensity.current) {
                com.teamz.lab.debugger.ui.adaptive.FabClearance.toPx()
            }
            LaunchedEffect(answerSize) {
                if (answerSize != IntSize.Zero) {
                    answerRequester.bringIntoView(
                        Rect(0f, 0f, answerSize.width.toFloat(), answerSize.height + clearancePx)
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { answerSize = it }
                    .bringIntoViewRequester(answerRequester)
            ) {
                // Little side padding and centred labels, so the Bangla answers fit a 320dp phone.
                Button(
                    onClick = { onHeard(true) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.mic_test_heard_yes), textAlign = TextAlign.Center) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onHeard(false) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.mic_test_heard_no), textAlign = TextAlign.Center) }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onReplay, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.mic_test_replay))
            }
        }
        MicPhase.IDLE -> {
            heard?.let { yes ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(bottom = 8.dp).riseInOnAppear(),
                ) {
                    Icon(
                        if (yes) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = if (yes) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp).then(if (yes) Modifier.popOnce() else Modifier.shakeOnce())
                    )
                    Spacer(Modifier.width(8.dp))
                    DgText(
                        stringResource(
                            if (yes) R.string.mic_test_verdict_ok
                            else R.string.mic_test_verdict_not_heard
                        ),
                        fontSize = 13.sp,
                        color = if (yes) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error
                    )
                }
            }
            val startPress = remember { MutableInteractionSource() }
            Button(
                interactionSource = startPress,
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale(startPress)
            ) {
                DgText(
                    stringResource(
                        if (heard == null) R.string.mic_test_start else R.string.mic_test_again
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        else -> {
            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

/** Live input meter. Maps [MicTestUtils.MIN_DBFS]..0 dBFS onto 0..1. */
@Composable
private fun LevelMeter(db: Double) {
    val fraction = ((db - MicTestUtils.MIN_DBFS) / (0.0 - MicTestUtils.MIN_DBFS))
        .coerceIn(0.0, 1.0).toFloat()
    val animated by animateFloatAsState(fraction, motionTween(DgMotion.quick), label = "mic-level")
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .background(
                        if (db > -3.0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        DgText(
            "${db.roundToInt()} dBFS",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MeasureRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        DgText(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        DgText(
            value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
