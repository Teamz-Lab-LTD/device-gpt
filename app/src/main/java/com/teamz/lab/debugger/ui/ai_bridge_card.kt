package com.teamz.lab.debugger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.services.BridgeService
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.BridgeQrCodeGenerator
import kotlinx.coroutines.delay

/**
 * The AI Bridge tab.
 *
 * Design rules applied (from `ui-ux-pro-max`, adapted for Compose):
 *  - Verdict first: current state renders BEFORE any technical detail. No jargon in the top line.
 *  - Never colour-alone: every state pairs an icon + colour + text.
 *  - One primary action per state: Turn on / Turn off. Nothing else uses primary colour.
 *  - Touch targets ≥ 48dp: every clickable is a full Button.
 *  - Progressive disclosure: setup guide lives behind a bottom sheet, not on the main tab.
 *  - Contrast: PIN uses monospace + tabular numbers so it never shifts as digits change.
 *  - QR renders black-on-white regardless of theme (scanner compatibility, not aesthetics).
 *
 * The tab reuses the existing tab-section signature so [DeviceGptNavExperience]'s Crossfade
 * dispatch does not need special-casing. `onShareClick` is a no-op: this tab has no shareable
 * plain-text report and the parent hides the Send FAB when this tab is selected (see
 * [DeviceGptNavExperience]'s FAB visibility rule).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiBridgeTabSection(
    activity: ComponentActivity,
    @Suppress("UNUSED_PARAMETER") onShareClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val state by BridgeService.state.collectAsState()
    var showGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AnalyticsUtils.logEvent(AnalyticsEvent.TabAiBridgeViewed)
    }

    // Cleanly stop the bridge if the user backgrounds the app for a long time AND the
    // idle-timer is close to firing anyway. Deliberately NOT auto-stopping on backgrounding —
    // the whole point is that the AI on the laptop can reach the phone even when the app
    // is not in the foreground; that's the honest expectation the toggle sets.

    Column(
        modifier = Modifier
            .fillMaxSize()
            // OnCard's QR + address + PIN + countdown + Turn off button + privacy note
            // exceeds a compact-phone screen height. Without verticalScroll the PIN and Turn off
            // button sit behind the system nav bar and become unreachable on short screens.
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val s = state) {
            is BridgeService.BridgeState.Off -> OffCard(onTurnOn = {
                BridgeService.start(context)
            }, onOpenGuide = {
                AnalyticsUtils.logEvent(AnalyticsEvent.AiBridgeGuideOpened, mapOf("from" to "off_state"))
                showGuide = true
            })

            is BridgeService.BridgeState.Starting -> StartingCard()

            is BridgeService.BridgeState.On -> OnCard(
                state = s,
                onTurnOff = { BridgeService.stop(context) },
                onOpenGuide = {
                    AnalyticsUtils.logEvent(AnalyticsEvent.AiBridgeGuideOpened, mapOf("from" to "on_state"))
                    showGuide = true
                },
                onCopy = { label, value ->
                    copyToClipboard(context, label, value)
                    Toast.makeText(context, context.getString(R.string.ai_bridge_copied), Toast.LENGTH_SHORT).show()
                },
            )

            is BridgeService.BridgeState.Error -> ErrorCard(reason = s.reason, onRetry = {
                BridgeService.start(context)
            })
        }

        Text(
            text = stringResource(id = R.string.ai_bridge_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showGuide) {
        SetupGuideSheet(onDismiss = { showGuide = false })
    }
}

// ────────────────────────────── state cards ──────────────────────────────

@Composable
private fun OffCard(onTurnOn: () -> Unit, onOpenGuide: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.ai_bridge_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.ai_bridge_subtitle_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onTurnOn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_bridge_turn_on), fontSize = 16.sp)
            }
            TextButton(
                onClick = onOpenGuide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ai_bridge_open_guide))
            }
        }
    }
}

@Composable
private fun StartingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.ai_bridge_starting),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun OnCard(
    state: BridgeService.BridgeState.On,
    onTurnOff: () -> Unit,
    onOpenGuide: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    val qrPayload = remember(state.urlDisplay, state.pin) {
        BridgeQrCodeGenerator.buildPayload(state.urlDisplay, state.pin)
    }
    val qrBitmap: Bitmap? = remember(qrPayload) { BridgeQrCodeGenerator.generateBitmap(qrPayload) }

    // Live countdown for the auto-shutdown label. Recomputed each second; cheap.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.startedAtMs, state.lastRequestAtMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val lastActivityMs = state.lastRequestAtMs ?: state.startedAtMs
    val remainingMs = (lastActivityMs + BridgeService.IDLE_TIMEOUT_MS - nowMs).coerceAtLeast(0L)
    val remainingLabel = formatMmSs(remainingMs)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32), // deliberate: green paired with the ✓ icon, not colour alone
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.ai_bridge_state_on),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.ai_bridge_add_this_on_laptop),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Pairing QR code",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            LabeledValueRow(
                label = stringResource(R.string.ai_bridge_address),
                value = state.urlDisplay,
                onCopy = { onCopy("bridge_address", state.urlDisplay) },
            )
            LabeledValueRow(
                label = stringResource(R.string.ai_bridge_pin),
                value = state.pin,
                monospace = true,
                onCopy = { onCopy("bridge_pin", state.pin) },
            )
            Text(
                text = "${stringResource(R.string.ai_bridge_auto_off_prefix)} $remainingLabel ${stringResource(R.string.ai_bridge_auto_off_suffix)}".trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onTurnOff,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Default.HighlightOff, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_bridge_turn_off), fontSize = 16.sp)
            }
            TextButton(
                onClick = onOpenGuide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ai_bridge_open_guide))
            }
        }
    }
}

@Composable
private fun ErrorCard(reason: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.ai_bridge_turn_on))
            }
        }
    }
}

// ────────────────────────────── setup guide sheet ──────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupGuideSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_bridge_setup_guide),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            GuideStep(
                title = stringResource(R.string.ai_bridge_guide_step1_title),
                body = stringResource(R.string.ai_bridge_guide_step1_body),
            )
            GuideStep(
                title = stringResource(R.string.ai_bridge_guide_step2_title),
                body = stringResource(R.string.ai_bridge_guide_step2_body),
            )
            GuideStep(
                title = stringResource(R.string.ai_bridge_guide_step3_title),
                body = stringResource(R.string.ai_bridge_guide_step3_body),
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.ai_bridge_guide_close))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GuideStep(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ────────────────────────────── small helpers ──────────────────────────────

@Composable
private fun LabeledValueRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    onCopy: () -> Unit,
) {
    val copyLabel = stringResource(R.string.ai_bridge_copy)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // `weight(1f)` on the label/value column reserves the copy button its own slot; without
            // it, a long value (like `http://192.168.1.42:8787`) pushed the button off-screen and
            // truncated "Copy" to "C op" in the OnCard header row (spotted live on emulator).
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // IconButton over TextButton: guaranteed 48dp square touch target (rule
            // `touch-target-size`), icon-only so it never gets truncated by long values, and the
            // clipboard icon is universally understood — no text label needed.
            IconButton(
                onClick = onCopy,
                modifier = Modifier.semantics { contentDescription = copyLabel },
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun formatMmSs(ms: Long): String {
    val totalSeconds = (ms / 1000L).toInt()
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
