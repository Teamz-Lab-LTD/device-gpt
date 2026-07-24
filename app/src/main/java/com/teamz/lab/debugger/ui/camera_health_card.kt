package com.teamz.lab.debugger.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.AIIcon
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.CameraHealthUtils
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.PermissionManager
import com.teamz.lab.debugger.utils.RevenueCatManager

/**
 * Camera tab: fact sheet + per-lens liveness check. Screen tests moved to their own tab
 * ([ScreenTestTabSection] in screen_test_card.kt) on 2026-07-24 — the screen is not camera
 * hardware, and a worried owner searching "camera not working" and one searching "screen dead
 * pixel" are two different intents that deserve two different front doors.
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
    val capturedThumbnails by viewModel.capturedThumbnails.collectAsState()

    var showDetail by remember { mutableStateOf(false) }

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
            val colorCastResultForReport by viewModel.colorCastResult.collectAsState()
            CameraProblemReportCard(
                context = context,
                activity = activity,
                factSheet = factSheet,
                latestResult = latestResult,
                colorCastResult = colorCastResultForReport,
                onItemAIClick = onItemAIClick,
            )

            Spacer(Modifier.size(20.dp))
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.size(20.dp))

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
                if (capturedThumbnails.isNotEmpty()) {
                    Spacer(Modifier.size(12.dp))
                    CapturedPhotosRow(result.liveness, capturedThumbnails)
                }
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
                            sheet.lenses.forEach { lens ->
                                val activeId = latestResult?.liveness
                                    ?.firstOrNull { it.cameraId == lens.cameraId }
                                    ?.activePhysicalCameraId
                                LensDetailRow(lens, activeId)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.size(20.dp))
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.size(20.dp))

            ColorCastCheckCard(context = context, activity = activity, viewModel = viewModel, onItemAIClick = onItemAIClick)

            // The Scaffold's floating action button row (PRO/Cert/AI/Send) is an overlay, not
            // reserved layout space — Scaffold's paddingValues does NOT account for FAB height,
            // so without this the last scrolled line sits underneath those icons (reported
            // 2026-07-24). Trailing spacer, not outer padding, so it only affects scroll extent.
            Spacer(Modifier.size(96.dp))
        }
    }
}

/**
 * "Report a Camera Problem" — broad AI hand-off for symptoms this app cannot detect or diagnose
 * itself (blurry, black screen, wrong colours, lines, crashes, etc.). A 2026-07-24 deep-research
 * pass (104 sub-agents) found real, verifiable evidence for only two camera-relevant causes a
 * no-root app can read (overheating, another app holding the camera) — everything else in the
 * symptom list below is a self-report LABEL, not a detection claim: this app never asserts what
 * caused it. It gathers every fact it can honestly confirm (hardware, live check, storage/RAM/
 * thermal) plus the user's own words, and hands the whole thing to a general-purpose AI to reason
 * about — the same "structure the facts, then let the model reason" pattern the research found
 * outperforms raw-data dumps.
 */
@Composable
private fun CameraProblemReportCard(
    context: Context,
    activity: Activity?,
    factSheet: CameraHealthUtils.CameraFactSheet?,
    latestResult: CameraHealthUtils.CameraHealthResult?,
    colorCastResult: CameraHealthUtils.ColorCastCheckResult?,
    onItemAIClick: ((String, String) -> Unit)?,
) {
    var showUpsellPaywall by remember { mutableStateOf(false) }
    PaywallWithReferralFallback(
        showPaywall = showUpsellPaywall,
        onDismiss = { showUpsellPaywall = false },
        analyticsSource = "camera_problem_report",
    )
    val symptomOptions = remember {
        listOf(
            "Black screen",
            "Won't open",
            "Blurry / won't focus",
            "Flash not working",
            "Colours look wrong",
            "Crashes or freezes",
            "Says another app is using it",
            "Slow / laggy",
            "Lines or spots in photos",
            "Overheating warning",
        )
    }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var otherText by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "Not Sure What's Wrong? Get AI Help",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.size(8.dp))
    Text(
        "Pick what you're seeing (choose any that apply), and we'll bundle it with your " +
            "camera's real specs and send it to an AI app of your choice to help figure out " +
            "what's going on.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(12.dp))

    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        symptomOptions.forEach { symptom ->
            androidx.compose.material3.FilterChip(
                selected = symptom in selected,
                onClick = {
                    val nowSelected = symptom !in selected
                    selected = if (nowSelected) selected + symptom else selected - symptom
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.CameraProblemSymptomToggled,
                        mapOf("symptom" to symptom, "selected" to nowSelected),
                    )
                },
                label = { Text(symptom, style = MaterialTheme.typography.bodySmall) },
            )
        }
    }

    Spacer(Modifier.size(12.dp))
    androidx.compose.material3.OutlinedTextField(
        value = otherText,
        onValueChange = { otherText = it },
        label = { Text("Something else? Describe it here") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )

    Spacer(Modifier.size(12.dp))
    Text(
        "This shares your camera and device details with whichever AI app you pick next — " +
            "nothing is sent until you choose one.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(8.dp))

    val canSend = selected.isNotEmpty() || otherText.isNotBlank()
    Button(
        onClick = {
            if (factSheet == null || onItemAIClick == null) return@Button
            AnalyticsUtils.logEvent(
                AnalyticsEvent.CameraProblemReportSent,
                mapOf(
                    "symptom_count" to selected.size,
                    "symptoms" to selected.sorted().joinToString(","),
                    "has_other_text" to otherText.isNotBlank(),
                ),
            )
            val sendAction: () -> Unit = {
                val environment = CameraHealthUtils.readCameraEnvironmentSignals(context)
                val report = CameraHealthUtils.buildCameraProblemReport(
                    factSheet = factSheet,
                    liveness = latestResult?.liveness,
                    colorCast = colorCastResult,
                    environment = environment,
                    selectedSymptoms = selected.toList(),
                    otherDescription = otherText,
                )
                onItemAIClick("Camera Problem Report", report)
            }
            if (activity != null) {
                InterstitialAdManager.showAdBeforeAction(activity, "camera_problem_report") { sendAction() }
            } else {
                sendAction()
            }
        },
        enabled = canSend && factSheet != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Get AI Help With This")
    }

    val premiumStatus by RevenueCatManager.premiumStatusFlow.collectAsState()
    val isPremium = (premiumStatus as? RevenueCatManager.PremiumStatus.Premium)?.isActive == true
    if (!isPremium) {
        Spacer(Modifier.size(8.dp))
        androidx.compose.material3.TextButton(
            onClick = {
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.CameraProblemUpsellClicked,
                    mapOf("source" to "camera_problem_report"),
                )
                showUpsellPaywall = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Go Pro — remove ads app-wide", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * "My photos look black and white" guided check — reported by a user 2026-07-24. Same house
 * rules as the rest of this file: verdict first in plain words, only report what was actually
 * read (system settings) or measured (frame saturation), and say plainly when this app cannot
 * see the cause (OEM-specific grayscale features aren't exposed through any public API).
 */
@Composable
private fun ColorCastCheckCard(
    context: Context,
    activity: Activity?,
    viewModel: CameraHealthViewModel,
    onItemAIClick: ((String, String) -> Unit)?,
) {
    val result by viewModel.colorCastResult.collectAsState()
    val isRunning by viewModel.isColorCastCheckRunning.collectAsState()

    val hasCameraPermission = remember(context) { PermissionManager.hasCameraPermission(context) }
    var permissionGranted by remember { mutableStateOf(hasCameraPermission) }
    val permissionLauncher = PermissionManager.rememberPermissionLauncher { granted ->
        permissionGranted = granted
        if (granted) viewModel.runColorCastCheck()
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.InvertColors, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.size(8.dp))
        Text(
            "My Photos Look Black & White?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (result != null && onItemAIClick != null) {
            IconButton(
                modifier = Modifier.size(32.dp),
                onClick = {
                    onItemAIClick(
                        "Camera Colour Cast Check",
                        CameraHealthUtils.buildColorCastAiContext(result!!),
                    )
                },
            ) {
                Icon(AIIcon.icon, contentDescription = "Ask AI", tint = AIIcon.color())
            }
        }
    }

    Spacer(Modifier.size(8.dp))
    Text(
        "We'll take one quick photo and check two common settings. Not every phone brand's " +
            "own grayscale feature can be detected this way.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.size(12.dp))

    result?.let { r -> ColorCastResultCard(context, r) }

    if (result != null) Spacer(Modifier.size(12.dp))

    Button(
        onClick = {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.CameraColorCastCheckStarted,
                mapOf("is_recheck" to (result != null)),
            )
            if (!permissionGranted) {
                permissionLauncher.launch(android.Manifest.permission.CAMERA)
            } else if (activity != null) {
                InterstitialAdManager.showAdBeforeAction(activity, "camera_color_cast_check") {
                    viewModel.runColorCastCheck()
                }
            } else {
                viewModel.runColorCastCheck()
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
            Text("Checking…")
        } else {
            Text(if (result == null) "Check My Photo" else "Check Again")
        }
    }
}

@Composable
private fun ColorCastResultCard(context: Context, result: CameraHealthUtils.ColorCastCheckResult) {
    val (containerColor, contentColor, icon, headline, subtext, settingsAction) = when {
        result.grayscaleAccessibilityOn -> ColorCastVerdict(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Warning,
            "Found it: Grayscale display mode is on",
            "Your phone's Accessibility settings have colour correction set to grayscale. " +
                "That makes everything look black and white, including the camera — turn it " +
                "off and colour should come back.",
            Settings.ACTION_ACCESSIBILITY_SETTINGS to "Open Accessibility Settings",
        )
        result.batterySaverOn -> ColorCastVerdict(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Warning,
            "Likely cause: Battery Saver is on",
            "Many phones turn the whole screen grayscale while Battery Saver is active to " +
                "save power. Turn it off (or wait until it charges) and check again.",
            Settings.ACTION_BATTERY_SAVER_SETTINGS to "Open Battery Settings",
        )
        result.capturedLooksMonochrome == true -> ColorCastVerdict(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Warning,
            "Your last photo showed almost no colour",
            "We checked the two most common settings and neither is on. This app can't see " +
                "every phone brand's own grayscale feature (for example, some Samsung and " +
                "Xiaomi phones have their own). Try restarting your phone in Safe Mode — if " +
                "colour comes back there, a recently installed app is the cause. If it " +
                "doesn't, this may need a service check.",
            null,
        )
        result.capturedLooksMonochrome == false -> ColorCastVerdict(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.CheckCircle,
            "Your last photo has normal colour",
            "We didn't find a grayscale cause, and the photo itself looks fine.",
            null,
        )
        else -> return
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
            Text(subtext, style = MaterialTheme.typography.bodySmall, color = contentColor)
            if (settingsAction != null) {
                Spacer(Modifier.size(10.dp))
                Button(
                    onClick = { context.startActivity(Intent(settingsAction.first)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(settingsAction.second)
                }
            }
        }
    }
}

private data class ColorCastVerdict(
    val containerColor: Color,
    val contentColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val headline: String,
    val subtext: String,
    val settingsAction: Pair<String, String>?,
)

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

/**
 * Shows the actual photo each camera captured during the last check — the user's own eyes judge
 * it, same philosophy as the screen test. This is the direct answer to "why take a photo and not
 * show it" (2026-07-24): the previous version proved a frame arrived but never displayed it.
 */
@Composable
private fun CapturedPhotosRow(
    liveness: List<CameraHealthUtils.CameraLivenessResult>,
    thumbnails: Map<String, Bitmap>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "What your camera saw just now:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(6.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            liveness.forEach { lens ->
                val bitmap = thumbnails[lens.cameraId] ?: return@forEach
                Column(modifier = Modifier.padding(end = 10.dp)) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Photo from the ${lens.facing} camera",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(140.dp)
                            .height(105.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        "${lens.facing} camera",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LensDetailRow(lens: CameraHealthUtils.LensReport, activePhysicalCameraId: String?) {
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
            val aperture = if (lens.aperturesF.isNotEmpty()) {
                "aperture f/${lens.aperturesF.joinToString(", f/")}"
            } else {
                "aperture not reported"
            }
            Text(
                "Hardware level: ${lens.hardwareLevel} · $focal · $aperture · $zoom",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Sensor size: ${lens.sensorSizeMm ?: "not reported"}mm · " +
                    "ISO range: ${lens.isoRange ?: "not reported"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Stabilization (photo): ${if (lens.hasOpticalStabilization) "reported" else "not reported"} · " +
                    "Stabilization (video): ${if (lens.hasVideoStabilization) "reported" else "not reported"} · " +
                    "Autofocus: ${if (lens.supportsAutofocus) "reported" else "not reported"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "RAW available to this app: ${if (lens.rawAvailableToThisApp) "yes" else "no"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Shutter speed range: ${lens.exposureTimeRangeSec ?: "not reported"} · " +
                    "Auto-exposure modes: ${lens.aeModes.joinToString(", ").ifEmpty { "not reported" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lens.jpegResolutions.isNotEmpty()) {
                Text(
                    "Photo resolutions (${lens.jpegResolutions.size}): " +
                        "${lens.jpegResolutions.first()} down to ${lens.jpegResolutions.last()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The one differentiator the 2026-07-24 research confirmed is a genuine read (not an
            // inference): which physical sub-lens the device says it used, at the zoom level of
            // the last check. Most devices report nothing here — that must read as "not
            // reported", never as "this phone has only one lens".
            if (lens.physicalLenses.isNotEmpty()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    "This camera has ${lens.physicalLenses.size} lenses behind it:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lens.physicalLenses.forEach { phys ->
                    val isActive = phys.physicalId == activePhysicalCameraId
                    val physFocal = phys.focalLengthsMm.joinToString(", ").ifEmpty { "not reported" }
                    val physAperture = phys.aperturesF.joinToString(", f/").ifEmpty { "not reported" }
                    Text(
                        "  • lens ${phys.physicalId}: ${physFocal}mm, f/$physAperture" +
                            if (isActive) "  ← used at last check's zoom" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Which exact lens is active at each zoom level: not reported by this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
