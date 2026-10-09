package com.teamz.lab.debugger.ui

import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.ai.ondevice.OnDeviceAiAvailability
import com.teamz.lab.debugger.ai.ondevice.PrivateAiExplainer
import com.teamz.lab.debugger.services.BridgeService
import com.teamz.lab.debugger.utils.AIIcon
import com.teamz.lab.debugger.utils.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.teamz.lab.debugger.ui.icons.DgText

// Package name of the Claude Android app. Isolated so the "Live-ready" badge in
// the AI-app list stays in sync with the entry in [aiApps] below without a
// string typo becoming a silent visual regression.
private const val CLAUDE_PACKAGE = "com.anthropic.claude"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantDialog(
    onDismiss: () -> Unit,
    onShareWithApp: (AIApp, PromptMode) -> Unit,
    context: Context,
    title: String = stringResource(R.string.mx_ai_dialog_title),
    subtitle: String = stringResource(R.string.mx_ai_dialog_subtitle),
    showExplanationModeToggle: Boolean = true,
    // Optional: if non-null, tapping the "Private AI (on-device)" row invokes this
    // instead of onShareWithApp. Callers typically launch a coroutine that calls
    // PrivateAiExplainer.explain(...) and shows the result in a small follow-up dialog.
    // If null, the synthetic AIApp with packageName == PrivateAiExplainer.SYNTHETIC_PACKAGE
    // is passed through onShareWithApp so callers can branch there.
    onPrivateAiSelected: ((PromptMode) -> Unit)? = null,
) {
    var promptMode by remember { mutableStateOf(PromptMode.Simple) }

    // Probe on-device AI availability once when this dialog opens. Cached across opens.
    var onDeviceStatus by remember { mutableStateOf(OnDeviceAiAvailability.lastKnownStatus()) }
    LaunchedEffect(Unit) {
        onDeviceStatus = OnDeviceAiAvailability.refreshStatus(context)
    }
    val onDeviceAvailable = onDeviceStatus == OnDeviceAiAvailability.Status.READY ||
        onDeviceStatus == OnDeviceAiAvailability.Status.DOWNLOADABLE

    // Bridge state — surfaces MCP live-mode availability inline so the user
    // can decide "share static text" vs "let this AI read live data".
    val bridgeState by BridgeService.state.collectAsState()

    val aiApps = remember {
        listOf(
            AIApp("ChatGPT", "com.openai.chatgpt", "https://play.google.com/store/apps/details?id=com.openai.chatgpt"),
            AIApp("Gemini (formerly Bard)", "com.google.android.apps.bard", "https://play.google.com/store/apps/details?id=com.google.android.apps.bard"),
            AIApp("DeepSeek", "com.deepseek.chat", "https://play.google.com/store/apps/details?id=com.deepseek.chat"),
            AIApp("Microsoft Copilot (Bing AI)", "com.microsoft.bing", "https://play.google.com/store/apps/details?id=com.microsoft.bing"),
            AIApp("Grok - AI Assistant", "ai.x.grok", "https://play.google.com/store/apps/details?id=ai.x.grok"),
            AIApp("You.com AI Chat", "com.you.browser", "https://play.google.com/store/apps/details?id=com.you.browser"),
            AIApp("Replika AI Companion", "ai.replika.app", "https://play.google.com/store/apps/details?id=ai.replika.app"),
            AIApp("Claude", CLAUDE_PACKAGE, "https://play.google.com/store/apps/details?id=$CLAUDE_PACKAGE"),
            AIApp("Perplexity", "ai.perplexity.app.android", "https://play.google.com/store/apps/details?id=ai.perplexity.app.android"),
        )
    }

    // Nine getPackageInfo() calls are nine binder round-trips to system_server. Running them
    // inside remember{} put them on the main thread during composition, so a busy
    // system_server froze the dialog. Resolve off-thread.
    //
    // null == "not resolved yet". An empty list is a real answer ("no AI apps installed") and
    // must not be shown before the scan finishes, or the dialog flashes its empty state.
    var installedApps by remember(aiApps) { mutableStateOf<List<AIApp>?>(null) }
    LaunchedEffect(aiApps) {
        installedApps = withContext(Dispatchers.IO) {
            aiApps.filter { app ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo(
                            app.packageName,
                            PackageManager.PackageInfoFlags.of(0L)
                        )
                    } else {
                        context.packageManager.getPackageInfo(
                            app.packageName,
                            PackageManager.GET_ACTIVITIES
                        )
                    }
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                } catch (exception: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(exception)
                    false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DgText(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DgText(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        text = {
            // Cap the whole dialog body so a phone with 9 AI apps installed
            // does not push the Cancel button off-screen on a small device.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                horizontalAlignment = Alignment.Start,
            ) {

                BridgeStatusPill(bridgeState)

                val hasAnyTarget = installedApps?.isNotEmpty() == true || onDeviceAvailable
                if (installedApps != null && hasAnyTarget && showExplanationModeToggle) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.mx_ai_mode_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioRow(
                            selected = promptMode == PromptMode.Simple,
                            label = context.string(R.string.simple),
                            onClick = { promptMode = PromptMode.Simple },
                            modifier = Modifier.weight(1f),
                        )
                        RadioRow(
                            selected = promptMode == PromptMode.Advanced,
                            label = context.string(R.string.advanced),
                            onClick = { promptMode = PromptMode.Advanced },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                val resolved = installedApps
                if (resolved == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            stringResource(R.string.mx_ai_finding_apps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (resolved.isEmpty() && !onDeviceAvailable) {
                    Text(
                        stringResource(R.string.mx_ai_no_apps),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    )
                    AiInstallList(apps = aiApps, context = context)
                } else {
                    val bridgeOn = bridgeState is BridgeService.BridgeState.On
                    LazyColumn {
                        if (onDeviceAvailable) {
                            item {
                                val onDeviceAiApp = AIApp(
                                    name = PrivateAiExplainer.DISPLAY_NAME,
                                    packageName = PrivateAiExplainer.SYNTHETIC_PACKAGE,
                                    playStoreUrl = "",
                                )
                                val onDeviceSubtitle = when (onDeviceStatus) {
                                    OnDeviceAiAvailability.Status.DOWNLOADABLE ->
                                        stringResource(R.string.mx_ai_private_download, "300MB")
                                    else -> stringResource(R.string.mx_ai_private_tagline)
                                }
                                ListItem(
                                    headlineContent = {
                                        DgText(
                                            // The AIApp keeps the English name: it is logged.
                                            stringResource(R.string.mx_ai_private_name),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    },
                                    supportingContent = {
                                        DgText(
                                            onDeviceSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        val handler = onPrivateAiSelected
                                        if (handler != null) handler(promptMode)
                                        else onShareWithApp(onDeviceAiApp, promptMode)
                                    },
                                )
                            }
                        }
                        items(resolved) { app ->
                            val isMcpCapable = bridgeOn && app.packageName == CLAUDE_PACKAGE
                            ListItem(
                                headlineContent = {
                                    DgText(app.name, style = MaterialTheme.typography.titleMedium)
                                },
                                supportingContent = if (isMcpCapable) {
                                    {
                                        Text(
                                            stringResource(R.string.mx_ai_live_ready),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else null,
                                leadingContent = {
                                    Icon(AIIcon.icon, contentDescription = null, tint = AIIcon.color())
                                },
                                trailingContent = if (isMcpCapable) {
                                    {
                                        Text(
                                            stringResource(R.string.mx_ai_live_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                } else null,
                                colors = if (isMcpCapable) {
                                    ListItemDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    )
                                } else ListItemDefaults.colors(),
                                modifier = Modifier.clickable { onShareWithApp(app, promptMode) },
                            )
                        }
                        if (resolved.isEmpty()) {
                            items(aiApps) { app ->
                                ListItem(
                                    headlineContent = {
                                        DgText(app.name, style = MaterialTheme.typography.titleMedium)
                                    },
                                    leadingContent = {
                                        Icon(AIIcon.icon, contentDescription = null, tint = AIIcon.color())
                                    },
                                    trailingContent = {
                                        Icon(
                                            Icons.Default.InstallMobile,
                                            contentDescription = stringResource(R.string.mx_ai_install_cd),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, app.playStoreUrl.toUri()))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        // Cancel is a dismiss, not a confirm — put it in the dismiss slot so
        // screen readers, keyboard shortcuts, and Material3 styling treat it
        // as such.
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.string(R.string.cancel))
            }
        },
    )
}

/**
 * A clickable row wrapping a RadioButton so tapping the label (not just the
 * tiny circle) selects the option. Fixes the touch-target rule that a 44dp
 * hit region must cover the whole logical control.
 */
@Composable
private fun RadioRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .sizeIn(minHeight = 44.dp)
            .padding(end = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        DgText(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AiInstallList(apps: List<AIApp>, context: Context) {
    LazyColumn {
        items(apps) { app ->
            ListItem(
                headlineContent = { DgText(app.name, style = MaterialTheme.typography.titleMedium) },
                leadingContent = { Icon(AIIcon.icon, contentDescription = null, tint = AIIcon.color()) },
                trailingContent = {
                    Icon(
                        Icons.Default.InstallMobile,
                        contentDescription = stringResource(R.string.mx_ai_install_cd),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, app.playStoreUrl.toUri()))
                },
            )
        }
    }
}

/**
 * Inline chip that mirrors the AI Bridge tab state. When On, it shows the
 * request counter so the user can *see* MCP calls arriving in real time
 * without leaving this dialog. When Off, it hints at the feature — one
 * sentence, no marketing pitch. Error/Starting states never surprise the
 * user with a blank space.
 */
@Composable
private fun BridgeStatusPill(state: BridgeService.BridgeState) {
    when (state) {
        is BridgeService.BridgeState.On -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        DgText(
                            stringResource(
                                if (state.requestCount == 1) R.string.mx_ai_bridge_on_one else R.string.mx_ai_bridge_on_many,
                                state.requestCount.toString(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.size(4.dp))
                    Text(
                        stringResource(R.string.mx_ai_bridge_on_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        is BridgeService.BridgeState.Starting -> {
            SubtlePill(
                icon = { CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp) },
                text = stringResource(R.string.mx_ai_bridge_starting),
            )
        }
        is BridgeService.BridgeState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    DgText(
                        stringResource(R.string.mx_ai_bridge_error, state.reason.take(80)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        is BridgeService.BridgeState.Off -> {
            SubtlePill(
                icon = {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                },
                text = stringResource(R.string.mx_ai_bridge_tip),
            )
        }
    }
    // Consistent bottom gap regardless of state so downstream content does not shift.
    Spacer(Modifier.size(8.dp))
}

@Composable
private fun SubtlePill(icon: @Composable () -> Unit, text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.size(8.dp))
            DgText(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class AIApp(
    val name: String, val packageName: String, val playStoreUrl: String
)

enum class PromptMode {
    Simple, Advanced
}
