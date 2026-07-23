package com.teamz.lab.debugger.ui

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.teamz.lab.debugger.utils.AIIcon
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.CameraHealthUtils
import com.teamz.lab.debugger.utils.PermissionManager
import com.teamz.lab.debugger.ui.theme.DesignSystemColors

/**
 * Camera tab: fact sheet + per-lens liveness check.
 *
 * Design constraints from the 2026-07-24 plan (do not relax without re-reading it):
 *  - Verdict first, plain words. No specs before the plain-language line.
 *  - Colour is never the only signal — every state pairs an icon + text with the colour.
 *  - Every background here comes from MaterialTheme.colorScheme, whose on* pairs are already
 *    defined in Theme.kt — never introduce a raw hex colour without its paired foreground.
 *  - No score, grade, or "health %". See camera_health_utils.kt header for why.
 */

/**
 * Tab-level entry point — owns the ViewModel, matching [PowerConsumptionSection]'s convention
 * of instantiating its ViewModel internally via [androidx.lifecycle.viewmodel.compose.viewModel]
 * rather than receiving it as a parameter from the nav host.
 */
@Composable
fun CameraTabSection(
    activity: Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val viewModel: CameraHealthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    CameraHealthSection(
        context = context,
        viewModel = viewModel,
        activity = activity,
        onItemAIClick = onItemAIClick,
    )
}

@Composable
fun CameraHealthSection(
    context: Context,
    viewModel: CameraHealthViewModel,
    activity: Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null,
) {
    val factSheet by viewModel.factSheet.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    val isRunning by viewModel.isCheckRunning.collectAsState()

    var showDetail by remember { mutableStateOf(false) }
    var showScreenTest by remember { mutableStateOf(false) }

    val hasCameraPermission = remember(context) { PermissionManager.hasCameraPermission(context) }
    var permissionGranted by remember { mutableStateOf(hasCameraPermission) }
    val permissionLauncher = PermissionManager.rememberPermissionLauncher { granted ->
        permissionGranted = granted
        if (granted) {
            AnalyticsUtils.logEvent(AnalyticsEvent.PermissionCameraRequested, mapOf("granted" to true))
            viewModel.runHealthCheck()
        } else {
            AnalyticsUtils.logEvent(AnalyticsEvent.PermissionCameraRequested, mapOf("granted" to false))
        }
    }

    val isDarkMode = MaterialTheme.colorScheme.background == DesignSystemColors.Dark
    val cardBackground = if (isDarkMode) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, DesignSystemColors.NeonGreen.copy(alpha = 0.3f)),
    ) {
        // BUG FIX (2026-07-24, found in device verification): the tab host does not wrap tab
        // content in a scrollable container, so the expanded "technical detail" lens list was
        // clipped behind the bottom navigation bar on real devices. Scroll is self-contained
        // here so this section is correct regardless of what the host does.
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Is Your Camera Working?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (latestResult != null && onItemAIClick != null) {
                    IconButton(
                        modifier = Modifier.size(32.dp),
                        onClick = {
                            val content = CameraHealthUtils.buildCameraAiContext(context, latestResult!!)
                            onItemAIClick("Camera Health Test", content)
                        },
                    ) {
                        Icon(AIIcon.icon, contentDescription = "Ask AI", tint = AIIcon.color())
                    }
                }
            }

            Spacer(Modifier.size(8.dp))
            Text(
                "We check each camera on your phone and tell you what your device reports — " +
                    "in plain words, not a score.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(12.dp))

            // Verdict — first, plain words, before any spec.
            latestResult?.let { result ->
                CameraVerdictCard(result)
                Spacer(Modifier.size(12.dp))
            }

            // Action button
            Button(
                onClick = {
                    AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf("source" to "camera_health"))
                    if (!permissionGranted) {
                        permissionLauncher.launch(android.Manifest.permission.CAMERA)
                    } else {
                        viewModel.runHealthCheck()
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Checking your camera…")
                } else {
                    Text(if (latestResult == null) "Check My Camera" else "Check Again")
                }
            }

            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = { showScreenTest = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Check My Screen for Bad Spots")
            }

            // Progressive disclosure — specs live here, never before the verdict.
            factSheet?.let { sheet ->
                if (sheet.cameraCount > 0) {
                    Spacer(Modifier.size(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDetail = !showDetail },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (showDetail) "Hide technical detail" else "Show technical detail",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            if (showDetail) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    AnimatedVisibility(visible = showDetail) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            sheet.lenses.forEach { lens -> LensDetailRow(lens) }
                        }
                    }
                }
            }
        }
    }

    if (showScreenTest) {
        ScreenPixelTestDialog(
            onDismiss = { showScreenTest = false },
            onResult = { issue, color -> viewModel.recordScreenPixelResult(issue, color) },
        )
    }
}

@Composable
private fun CameraVerdictCard(result: CameraHealthUtils.CameraHealthResult) {
    val allOk = result.allLensesResponded
    val someOpened = result.liveness.any { it.opened && it.frameReceived }

    // Never colour-only: every state pairs a semantic colour with an icon AND a text line.
    val (containerColor, contentColor, icon, headline) = when {
        allOk -> Quad(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.CheckCircle,
            "Your camera looks OK",
        )
        someOpened -> Quad(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Warning,
            "One camera did not respond fully",
        )
        else -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Warning,
            "We could not turn on your camera",
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = contentColor)
                Spacer(Modifier.size(8.dp))
                Text(
                    headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = contentColor,
                )
            }
            Spacer(Modifier.size(6.dp))
            result.liveness.forEach { lens ->
                val line = when {
                    !lens.opened -> "${lens.facing} camera: did not turn on"
                    !lens.frameReceived -> "${lens.facing} camera: turned on but no photo came back"
                    lens.autofocusConverged == false ->
                        "${lens.facing} camera: took a photo, but focus did not lock. This can mean " +
                            "the lens needs cleaning, or the room was too dark to focus."
                    else -> "${lens.facing} camera: working normally"
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = contentColor)
            }
        }
    }
}

private data class Quad(val a: Color, val b: Color, val c: androidx.compose.ui.graphics.vector.ImageVector, val d: String)

@Composable
private fun LensDetailRow(lens: CameraHealthUtils.LensReport) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                "${lens.facing} camera reports:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val zoom = lens.maxDigitalZoom?.let { "${it}x digital zoom" } ?: "zoom not reported"
            val focal = if (lens.focalLengthsMm.isNotEmpty()) {
                "focal length ${lens.focalLengthsMm.joinToString(", ")}mm"
            } else {
                "focal length not reported"
            }
            Text(
                "Hardware level: ${lens.hardwareLevel} · $focal · $zoom",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Stabilization: ${if (lens.hasOpticalStabilization) "reported" else "not reported"} · " +
                    "Autofocus: ${if (lens.supportsAutofocus) "reported" else "not reported"} · " +
                    "RAW available to this app: ${if (lens.rawAvailableToThisApp) "yes" else "no"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Screen pixel test — the phone cannot detect its own dead pixels; the user's eye can. ────

private val SCREEN_TEST_COLORS = listOf(
    "red" to Color.Red,
    "green" to Color.Green,
    "blue" to Color.Blue,
    "white" to Color.White,
    "black" to Color.Black,
    "grey" to Color.Gray,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenPixelTestDialog(
    onDismiss: () -> Unit,
    onResult: (issueReported: Boolean, colorShown: String?) -> Unit,
) {
    var index by remember { mutableStateOf(0) }
    val (colorName, color) = SCREEN_TEST_COLORS[index]
    val isLight = color == Color.White || color == Color.Yellow
    val textColor = if (isLight) Color.Black else Color.White

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxSize(), color = color) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    "Look at the whole screen. Do you see any spot that looks wrong?",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Showing: $colorName (${index + 1} of ${SCREEN_TEST_COLORS.size})",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            AnalyticsUtils.logEvent(AnalyticsEvent.ScreenPixelTestStarted)
                            onResult(true, colorName)
                            onDismiss()
                        },
                    ) { Text("I see a bad spot") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (index < SCREEN_TEST_COLORS.lastIndex) {
                                index += 1
                            } else {
                                onResult(false, null)
                                onDismiss()
                            }
                        },
                    ) { Text(if (index < SCREEN_TEST_COLORS.lastIndex) "Looks fine, next colour" else "Looks fine, done") }
                }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = textColor)
                }
            }
        }
    }
}
