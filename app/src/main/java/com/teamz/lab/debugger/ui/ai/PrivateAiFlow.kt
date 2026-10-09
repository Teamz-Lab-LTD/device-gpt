package com.teamz.lab.debugger.ui.ai

import androidx.compose.ui.res.stringResource
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.HealthDisplayText
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.teamz.lab.debugger.ai.ondevice.OnDeviceAiAvailability
import com.teamz.lab.debugger.ai.ondevice.PrivateAiExplainer
import com.teamz.lab.debugger.ui.PromptMode
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import kotlinx.coroutines.launch
import com.teamz.lab.debugger.ui.icons.DgText

/**
 * Encapsulates the on-device Private AI flow that AI-chooser call-sites plug in
 * to handle `onPrivateAiSelected`. Keeps each call-site to three lines:
 *   1. `val privateAi = rememberPrivateAiFlow(...) { mode -> buildPrompt(mode) }`
 *   2. Pass `onPrivateAiSelected = privateAi.onSelect` to `AIAssistantDialog(...)`.
 *   3. Invoke `privateAi.Render()` once inside the Composable tree.
 */
class PrivateAiFlowState internal constructor(
    val onSelect: (PromptMode) -> Unit,
    val render: @Composable () -> Unit,
) {
    /** Convenience so callers can write `privateAi.Render()` instead of `privateAi.render()`. */
    @Composable
    fun Render() {
        render()
    }
}

/**
 * Remembers a [PrivateAiFlowState] that routes the "Private AI (on-device)" chooser
 * row to ML Kit GenAI (Gemini Nano) when available.
 *
 * @param subject short label rendered in the dialog title (e.g. "Battery", "Network").
 *   Defaults to "DeviceGPT scan".
 * @param buildPrompt converts the selected [PromptMode] into the plain-text prompt
 *   that will be fed to Summariser. Callers usually delegate to
 *   `AIPromptGenerator.generateMainPrompt(...)`.
 */
@Composable
fun rememberPrivateAiFlow(
    subject: String = "DeviceGPT scan",
    buildPrompt: (PromptMode) -> String,
): PrivateAiFlowState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var active by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lastPrompt by remember { mutableStateOf("") }

    val onSelect: (PromptMode) -> Unit = { mode ->
        active = true
        loading = true
        result = null
        errorMsg = null
        lastPrompt = buildPrompt(mode)
        AnalyticsUtils.logEvent(
            AnalyticsEvent.PrivateAiSelected,
            mapOf("subject" to subject, "mode" to mode.name),
        )
        val startedAt = System.currentTimeMillis()
        scope.launch {
            // Best-effort: if the model still needs to download, kick it off and
            // fall through — user sees a "downloading" message via the empty result path.
            val ready = runCatching {
                OnDeviceAiAvailability.refreshStatus(context)
            }.getOrDefault(OnDeviceAiAvailability.Status.UNKNOWN)
            AnalyticsUtils.logEvent(
                AnalyticsEvent.PrivateAiModelStatus,
                mapOf("subject" to subject, "status" to ready.name),
            )

            if (ready == OnDeviceAiAvailability.Status.DOWNLOADABLE) {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.PrivateAiDownloadStarted,
                    mapOf("subject" to subject),
                )
                runCatching { PrivateAiExplainer.ensureModelReady(context) }
            }
            val explanation = PrivateAiExplainer.explain(
                context = context,
                scanSubject = subject,
                scanText = lastPrompt,
            )
            loading = false
            val latencyMs = System.currentTimeMillis() - startedAt
            explanation
                .onSuccess {
                    result = it
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.PrivateAiResultShown,
                        mapOf("subject" to subject, "latency_ms" to latencyMs, "chars" to it.length),
                    )
                }
                .onFailure {
                    errorMsg = it.message ?: context.getString(R.string.mx_private_ai_not_ready)
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.PrivateAiFailed,
                        mapOf(
                            "subject" to subject,
                            "status" to ready.name,
                            "latency_ms" to latencyMs,
                            "reason" to (it.message?.take(100) ?: "unknown"),
                        ),
                    )
                }
        }
    }

    val render: @Composable () -> Unit = {
        if (active) {
            PrivateAiResultDialog(
                subject = subject,
                loading = loading,
                result = result,
                errorMsg = errorMsg,
                onCopy = {
                    result?.let {
                        copyToClipboard(context, "DeviceGPT · $subject", it)
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.PrivateAiResultCopied,
                            mapOf("subject" to subject, "chars" to it.length),
                        )
                    }
                },
                onDismiss = {
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.PrivateAiDismissed,
                        mapOf("subject" to subject, "had_result" to (result != null)),
                    )
                    active = false
                    loading = false
                    result = null
                    errorMsg = null
                },
            )
        }
    }

    // Auto-dismiss nothing; caller controls lifecycle.
    LaunchedEffect(Unit) {}

    return remember(onSelect, render) { PrivateAiFlowState(onSelect, render) }
}

@Composable
private fun PrivateAiResultDialog(
    subject: String,
    loading: Boolean,
    result: String?,
    errorMsg: String?,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = !loading),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.padding(horizontal = 4.dp))
                DgText(
                    // `subject` stays English for analytics and the clipboard label.
                    stringResource(
                        R.string.mx_private_ai_title,
                        HealthDisplayText.aiItemTitle(LocalContext.current, subject),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.padding(horizontal = 8.dp))
                            Text(
                                stringResource(R.string.mx_private_ai_running),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    errorMsg != null -> {
                        Text(
                            stringResource(R.string.mx_private_ai_failed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        DgText(
                            errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    result != null -> {
                        DgText(result, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.mx_private_ai_powered),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.mx_private_ai_preparing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = {
            if (result != null) {
                TextButton(onClick = onCopy) {
                    Text(stringResource(R.string.mx_private_ai_copy))
                }
            }
        },
    )
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}
