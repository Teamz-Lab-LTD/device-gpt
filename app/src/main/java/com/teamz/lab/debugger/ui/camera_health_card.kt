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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.AIIcon
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.CameraHealthUtils
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.PermissionManager

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
    onShareClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: CameraHealthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    CameraHealthSection(
        context = context,
        viewModel = viewModel,
        activity = activity,
        onItemAIClick = onItemAIClick,
        onShareClick = onShareClick,
    )
}

@Composable
fun CameraHealthSection(
    context: Context,
    viewModel: CameraHealthViewModel,
    activity: Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null,
    onShareClick: (String) -> Unit = {},
) {
    val factSheet by viewModel.factSheet.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    val isRunning by viewModel.isCheckRunning.collectAsState()
    val capturedThumbnails by viewModel.capturedThumbnails.collectAsState()

    // BUG FIX (2026-07-24, reported by user: "why are they always loading?"): the bottom FAB
    // row (AI / Certificate / Share) only leaves its loading state once `shareText` at the nav
    // host stops being the literal "Loading…" placeholder. Every other tab wires onShareClick;
    // this tab never did, so those three FABs spun forever whenever Camera was open. Push a
    // real string up as soon as we have anything to show, and again once a check completes.
    androidx.compose.runtime.LaunchedEffect(factSheet, latestResult) {
        val text = latestResult?.let { CameraHealthUtils.buildCameraAiContext(context, it) }
            ?: "Camera tab ready — ${factSheet?.cameraCount ?: 0} camera(s) detected on this device. " +
                "Run \"Check My Camera\" for full details."
        onShareClick(text)
    }

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
                    stringResource(R.string.camera_title),
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
                        Icon(AIIcon.icon, contentDescription = stringResource(R.string.ask_ai), tint = AIIcon.color())
                    }
                }
            }

            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.camera_intro),
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
                    Text(stringResource(R.string.camera_checking))
                } else {
                    Text(
                        stringResource(
                            if (latestResult == null) R.string.camera_check_button
                            else R.string.camera_check_again
                        )
                    )
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
                            stringResource(
                                if (showDetail) R.string.camera_hide_detail else R.string.camera_show_detail
                            ),
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
    // The English text is the symptom's identity: it goes to analytics and into the report the
    // AI reads, so it stays English. Only the chip label is shown in the app language.
    val symptomOptions = remember {
        listOf(
            "Black screen" to R.string.camera_symptom_black_screen,
            "Won't open" to R.string.camera_symptom_wont_open,
            "Blurry / won't focus" to R.string.camera_symptom_blurry,
            "Flash not working" to R.string.camera_symptom_flash,
            "Colours look wrong" to R.string.camera_symptom_colours,
            "Crashes or freezes" to R.string.camera_symptom_crashes,
            "Says another app is using it" to R.string.camera_symptom_in_use,
            "Slow / laggy" to R.string.camera_symptom_slow,
            "Lines or spots in photos" to R.string.camera_symptom_lines,
            "Overheating warning" to R.string.camera_symptom_overheating,
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
            stringResource(R.string.camera_help_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.size(8.dp))
    Text(
        stringResource(R.string.camera_help_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(12.dp))

    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        symptomOptions.forEach { (symptom, symptomLabel) ->
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
                label = { Text(stringResource(symptomLabel), style = MaterialTheme.typography.bodySmall) },
            )
        }
    }

    Spacer(Modifier.size(12.dp))
    androidx.compose.material3.OutlinedTextField(
        value = otherText,
        onValueChange = { otherText = it },
        label = { Text(stringResource(R.string.camera_help_other)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )

    Spacer(Modifier.size(12.dp))
    Text(
        stringResource(R.string.camera_help_privacy),
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
                    context = context,
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
        Text(stringResource(R.string.camera_help_button))
    }

    // Go Pro upsell removed 2026-07-25 after the button appeared to do nothing on tap.
    // ORIGINAL THEORY (WRONG, retracted 2026-07-25): a RC/Play product-type mismatch on
    // `lifetime_premium`. Checked directly via the RC v2 API + Play Developer API the same
    // day: RC has it as `one_time` (non-consumable), Play has it as an active one-time
    // product available in 173 regions incl. BD, and the offering → package → product chain
    // (device-gpt-offering → DeviceGPT Lifetime Access → lifetime_premium) is wired correctly.
    // No mismatch exists. The real cause of the empty/broken tap is UNCONFIRMED — restore
    // this button only after reproducing the failure with live logcat during an actual tap.
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
            stringResource(R.string.camera_bw_title),
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
                Icon(AIIcon.icon, contentDescription = stringResource(R.string.ask_ai), tint = AIIcon.color())
            }
        }
    }

    Spacer(Modifier.size(8.dp))
    Text(
        stringResource(R.string.camera_bw_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.size(12.dp))

    val preview by viewModel.colorCastPreview.collectAsState()
    result?.let { r -> ColorCastResultCard(context, r, preview) }

    if (result != null) Spacer(Modifier.size(12.dp))

    Button(
        onClick = {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.CameraColorCastCheckStarted,
                mapOf("is_recheck" to (result != null)),
            )
            if (!permissionGranted) {
                permissionLauncher.launch(android.Manifest.permission.CAMERA)
            } else {
                // No interstitial here: the ad activity pauses MainActivity, which on
                // resume tears down and rebuilds this tab's scroll state — so tapping
                // "Check Again" jumped the viewport to the top of the page. Diagnostics
                // are also the wrong place for a mid-flow interstitial; ads belong at
                // natural breaks, not between "run" and "see result".
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
            Text(stringResource(R.string.checking))
        } else {
            Text(
                stringResource(
                    if (result == null) R.string.camera_bw_check_button else R.string.camera_check_again
                )
            )
        }
    }
}

@Composable
private fun ColorCastResultCard(
    context: Context,
    result: CameraHealthUtils.ColorCastCheckResult,
    preview: android.graphics.Bitmap? = null,
) {
    val (containerColor, contentColor, icon, headline, subtext, settingsAction) = when {
        result.grayscaleAccessibilityOn -> ColorCastVerdict(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Warning,
            stringResource(R.string.camera_bw_grayscale_title),
            stringResource(R.string.camera_bw_grayscale_body),
            Settings.ACTION_ACCESSIBILITY_SETTINGS to stringResource(R.string.camera_bw_open_accessibility),
        )
        result.batterySaverOn -> ColorCastVerdict(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Warning,
            stringResource(R.string.camera_bw_saver_title),
            stringResource(R.string.camera_bw_saver_body),
            Settings.ACTION_BATTERY_SAVER_SETTINGS to stringResource(R.string.camera_bw_open_battery),
        )
        result.capturedLooksMonochrome == true -> ColorCastVerdict(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Warning,
            stringResource(R.string.camera_bw_mono_title),
            stringResource(R.string.camera_bw_mono_body),
            null,
        )
        result.capturedLooksMonochrome == false -> ColorCastVerdict(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.CheckCircle,
            stringResource(R.string.camera_bw_ok_title),
            stringResource(R.string.camera_bw_ok_body),
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
            // Show what the app actually saw before the verdict — the user's own eye
            // is the tie-breaker on "does this look grayscale to me?", and hiding the
            // photo forces them to trust an opaque score.
            if (preview != null) {
                androidx.compose.foundation.Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = stringResource(R.string.camera_bw_photo_cd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Spacer(Modifier.size(10.dp))
            }
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
            stringResource(R.string.camera_verdict_ok),
        )
        someOpened -> Quad(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Warning,
            stringResource(R.string.camera_verdict_partial),
        )
        else -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Warning,
            stringResource(R.string.camera_verdict_failed),
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
                val name = cameraName(lens.facing)
                val line = when {
                    !lens.opened -> stringResource(R.string.camera_line_not_opened, name)
                    !lens.frameReceived -> stringResource(R.string.camera_line_no_frame, name)
                    lens.autofocusConverged == false -> stringResource(R.string.camera_line_focus, name)
                    else -> stringResource(R.string.camera_line_ok, name)
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = contentColor)
            }
        }
    }
}

private data class Quad(val a: Color, val b: Color, val c: androidx.compose.ui.graphics.vector.ImageVector, val d: String)

/**
 * The name a person reads for a camera. [facing] is the English word camera_health_utils reports
 * ("Back" / "Front" / "External" / "Unknown"); it also goes into the AI report, so it stays
 * English there and is only turned into the app language here.
 */
@Composable
private fun cameraName(facing: String): String = when (facing) {
    "Back" -> stringResource(R.string.camera_name_back)
    "Front" -> stringResource(R.string.camera_name_front)
    "External" -> stringResource(R.string.camera_name_external)
    else -> stringResource(R.string.camera_name_unknown)
}

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
            stringResource(R.string.camera_saw_title),
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
                        contentDescription = stringResource(R.string.camera_photo_cd, cameraName(lens.facing)),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(140.dp)
                            .height(105.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        cameraName(lens.facing),
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
                stringResource(R.string.camera_lens_reports, cameraName(lens.facing)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val notReported = stringResource(R.string.camera_not_reported)
            val reported = stringResource(R.string.camera_reported)
            val zoom = lens.maxDigitalZoom?.let { stringResource(R.string.camera_lens_zoom, it.toString()) }
                ?: stringResource(R.string.camera_lens_zoom_none)
            val focal = if (lens.focalLengthsMm.isNotEmpty()) {
                stringResource(R.string.camera_lens_focal, lens.focalLengthsMm.joinToString(", "))
            } else {
                stringResource(R.string.camera_lens_focal_none)
            }
            val aperture = if (lens.aperturesF.isNotEmpty()) {
                stringResource(R.string.camera_lens_aperture, lens.aperturesF.joinToString(", f/"))
            } else {
                stringResource(R.string.camera_lens_aperture_none)
            }
            Text(
                stringResource(R.string.camera_lens_line_hardware, lens.hardwareLevel, focal, aperture, zoom),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.camera_lens_line_sensor,
                    lens.sensorSizeMm?.toString() ?: notReported,
                    lens.isoRange?.toString() ?: notReported,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.camera_lens_line_stabilization,
                    if (lens.hasOpticalStabilization) reported else notReported,
                    if (lens.hasVideoStabilization) reported else notReported,
                    if (lens.supportsAutofocus) reported else notReported,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.camera_lens_line_raw,
                    stringResource(if (lens.rawAvailableToThisApp) R.string.camera_yes else R.string.camera_no),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.camera_lens_line_shutter,
                    lens.exposureTimeRangeSec?.toString() ?: notReported,
                    lens.aeModes.joinToString(", ").ifEmpty { notReported },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lens.jpegResolutions.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.camera_lens_line_resolutions,
                        lens.jpegResolutions.size,
                        lens.jpegResolutions.first().toString(),
                        lens.jpegResolutions.last().toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (lens.videoRecordingSupported) {
                    lens.maxVideoResolution
                        ?.let { stringResource(R.string.camera_lens_video_supported_up_to, it.toString()) }
                        ?: stringResource(R.string.camera_lens_video_supported)
                } else {
                    stringResource(R.string.camera_lens_video_none)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The one differentiator the 2026-07-24 research confirmed is a genuine read (not an
            // inference): which physical sub-lens the device says it used, at the zoom level of
            // the last check. Most devices report nothing here — that must read as "not
            // reported", never as "this phone has only one lens".
            if (lens.physicalLenses.isNotEmpty()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    stringResource(R.string.camera_lens_physical_count, lens.physicalLenses.size),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lens.physicalLenses.forEach { phys ->
                    val isActive = phys.physicalId == activePhysicalCameraId
                    val physFocal = phys.focalLengthsMm.joinToString(", ").ifEmpty { notReported }
                    val physAperture = phys.aperturesF.joinToString(", f/").ifEmpty { notReported }
                    Text(
                        "  • " + stringResource(
                            if (isActive) R.string.camera_lens_physical_row_active
                            else R.string.camera_lens_physical_row,
                            phys.physicalId,
                            physFocal,
                            physAperture,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.camera_lens_active_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
