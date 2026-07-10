package com.teamz.lab.debugger.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Lock
import com.teamz.lab.debugger.utils.AIIcon
import com.teamz.lab.debugger.ai.ondevice.OnDeviceAiAvailability
import com.teamz.lab.debugger.ai.ondevice.PrivateAiExplainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.string
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.firebase.crashlytics.FirebaseCrashlytics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantDialog(
    onDismiss: () -> Unit,
    onShareWithApp: (AIApp, PromptMode) -> Unit,
    context: Context,
    title: String = "AI, Is My Phone OK?",
    subtitle: String = "Get simple, friendly answers about your phone’s health—no tech skills needed.",
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

    val aiApps = remember {
        listOf(
            AIApp(
                "ChatGPT",
                "com.openai.chatgpt",
                "https://play.google.com/store/apps/details?id=com.openai.chatgpt"
            ),
            AIApp(
                "Gemini (formerly Bard)",
                "com.google.android.apps.bard",
                "https://play.google.com/store/apps/details?id=com.google.android.apps.bard"
            ),
            AIApp(
                "DeepSeek",
                "com.deepseek.chat",
                "https://play.google.com/store/apps/details?id=com.deepseek.chat"
            ),
            AIApp(
                "Microsoft Copilot (Bing AI)",
                "com.microsoft.bing",
                "https://play.google.com/store/apps/details?id=com.microsoft.bing"
            ),
            AIApp(
                "Grok - AI Assistant",
                "ai.x.grok",
                "https://play.google.com/store/apps/details?id=ai.x.grok"
            ),
            AIApp(
                "You.com AI Chat",
                "com.you.browser",
                "https://play.google.com/store/apps/details?id=com.you.browser"
            ),
            AIApp(
                "Replika AI Companion",
                "ai.replika.app",
                "https://play.google.com/store/apps/details?id=ai.replika.app"
            ),
            AIApp(
                "Claude",
                "com.anthropic.claude",
                "https://play.google.com/store/apps/details?id=com.anthropic.claude"
            ),
            AIApp(
                "Perplexity",
                "ai.perplexity.app.android",
                "https://play.google.com/store/apps/details?id=ai.perplexity.app.android"
            ),
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

    AlertDialog(onDismissRequest = onDismiss, title = {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                title, style = MaterialTheme.typography.titleLarge
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }
    }, text = {
        Column(
            modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start
        ) {

            // Show the Simple/Advanced toggle once we know there is something to send to:
            // a cloud app, or on-device AI. Not while the package scan is still running.
            val hasAnyTarget = installedApps?.isNotEmpty() == true || onDeviceAvailable
            if (installedApps != null && hasAnyTarget && showExplanationModeToggle) {
                // Toggle for explanation mode
                Text(
                    "AI Explanation Mode:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = promptMode == PromptMode.Simple,
                            onClick = { promptMode = PromptMode.Simple })
                        Text(context.string(R.string.simple), style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = promptMode == PromptMode.Advanced,
                            onClick = { promptMode = PromptMode.Advanced })
                        Text(context.string(R.string.advanced), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            val resolved = installedApps
            if (resolved == null) {
                // Package scan still running on IO. Showing the empty state here would flash
                // "No AI apps found" and hide the Private AI row on every open.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else if (resolved.isEmpty() && !onDeviceAvailable) {
                Text(
                    "No AI apps found. Please install one of these apps:",
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                LazyColumn {
                    items(aiApps) { app ->
                        ListItem(headlineContent = {
                            Text(
                                app.name, style = MaterialTheme.typography.titleMedium
                            )
                        }, leadingContent = {
                            Icon(
                                AIIcon.icon,
                                contentDescription = null,
                                tint = AIIcon.color()
                            )
                        }, trailingContent = {
                            Icon(
                                Icons.Default.InstallMobile,
                                contentDescription = "Install",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }, modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, app.playStoreUrl.toUri())
                            context.startActivity(intent)
                        })
                    }
                }

            } else {
                LazyColumn {
                    if (onDeviceAvailable) {
                        item {
                            val onDeviceAiApp = AIApp(
                                name = PrivateAiExplainer.DISPLAY_NAME,
                                packageName = PrivateAiExplainer.SYNTHETIC_PACKAGE,
                                playStoreUrl = "",
                            )
                            val subtitle = when (onDeviceStatus) {
                                OnDeviceAiAvailability.Status.READY ->
                                    PrivateAiExplainer.TAGLINE
                                OnDeviceAiAvailability.Status.DOWNLOADABLE ->
                                    "One-time ~300MB download · then fully offline"
                                else -> PrivateAiExplainer.TAGLINE
                            }
                            ListItem(
                                headlineContent = {
                                    Text(
                                        onDeviceAiApp.name,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        subtitle,
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
                                    if (handler != null) {
                                        handler(promptMode)
                                    } else {
                                        onShareWithApp(onDeviceAiApp, promptMode)
                                    }
                                },
                            )
                        }
                    }
                    items(resolved) { app ->
                        ListItem(headlineContent = {
                            Text(
                                app.name, style = MaterialTheme.typography.titleMedium
                            )
                        }, leadingContent = {
                            Icon(
                                AIIcon.icon,
                                contentDescription = null,
                                tint = AIIcon.color()
                            )
                        }, modifier = Modifier.clickable {
                            onShareWithApp(app, promptMode)
                        })
                    }
                    // On-device AI works with no cloud app installed. Still offer the installs.
                    if (resolved.isEmpty()) {
                        items(aiApps) { app ->
                            ListItem(headlineContent = {
                                Text(app.name, style = MaterialTheme.typography.titleMedium)
                            }, leadingContent = {
                                Icon(AIIcon.icon, contentDescription = null, tint = AIIcon.color())
                            }, trailingContent = {
                                Icon(
                                    Icons.Default.InstallMobile,
                                    contentDescription = "Install",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }, modifier = Modifier.clickable {
                                val intent = Intent(Intent.ACTION_VIEW, app.playStoreUrl.toUri())
                                context.startActivity(intent)
                            })
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = onDismiss) {
            Text(context.string(R.string.cancel))
        }
    })
}

data class AIApp(
    val name: String, val packageName: String, val playStoreUrl: String
)

enum class PromptMode {
    Simple, Advanced
}


