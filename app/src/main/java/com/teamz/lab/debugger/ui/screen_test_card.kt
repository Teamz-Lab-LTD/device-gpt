package com.teamz.lab.debugger.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.res.stringResource
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.AIIcon
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.CameraHealthUtils
import kotlinx.coroutines.delay

/**
 * Screen Test tab: colour/dead-pixel check, grid/scratch check, touch check. Split out of the
 * Camera tab on 2026-07-24 — the screen is not camera hardware.
 *
 * Every check here is judged by the USER, never the app: the phone cannot see its own display,
 * so there is no honest way for it to grade what it cannot observe. The app's job is to show a
 * clean test pattern and record what the user reports — same "device REPORTS X" discipline as
 * the Camera tab. See camera_health_utils.kt header for the full policy reasoning.
 */

/**
 * Compose's [Dialog] defaults to `usePlatformDefaultWidth = true`, which caps dialog width to
 * the platform's dialog theme (not full-bleed) on many real devices/OEM skins — a colour or grid
 * test that doesn't cover every pixel is useless for a dead-pixel/scratch check. `decorFitsSystemWindows
 * = false` lets content draw under the status/nav bars; paired with [ImmersiveFullBleedEffect]
 * below, which hides those bars outright so the pixels they normally sit over are actually shown,
 * not just drawn-behind-a-translucent-bar. All three test dialogs use this.
 */
private val FULL_SCREEN_DIALOG_PROPERTIES = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)

/**
 * Hides the status bar and navigation bar for as long as this is in composition, restoring them
 * on dispose. A [Dialog] opens its own platform [android.view.Window] — MainActivity's own
 * edge-to-edge setting does not carry over to it, so this must be set again here. Without this,
 * the physical pixels underneath the status bar / nav bar are never actually shown during a
 * dead-pixel or scratch check, which is exactly the area most likely to hide a real defect.
 */
@Composable
private fun ImmersiveFullBleedEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowInsetsControllerCompat(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
}

@Composable
fun ScreenTestTabSection(
    activity: Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null,
    onShareClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: ScreenTestViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var micReport by remember { mutableStateOf<String?>(null) }

    // The tab now owns the scroll so the mic card can sit as a SIBLING above the
    // screen checks. ScreenTestSection keeps its own scroll for any other caller;
    // nesting two same-direction scrollables here would fight each other.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp)
            // Clear the floating action buttons; without this the last control sits
            // behind them and reads as truncated.
            .padding(bottom = 120.dp)
    ) {
        // Mic first. The app TITLE is "Battery, Mic Test: DeviceGPT" — the feature the
        // title leads with must not be the last thing on the screen.
        MicTestCard(
            activity = activity,
            onItemAIClick = onItemAIClick,
            onResultChanged = { r ->
                micReport = r
                onShareClick(r)
            },
        )

        ScreenTestSection(
            context = context,
            viewModel = viewModel,
            activity = activity,
            onItemAIClick = onItemAIClick,
            onShareClick = { screenInfo ->
                // Keep whichever report the user actually produced. The mic result
                // must not be wiped by the screen section's idle placeholder.
                onShareClick(micReport?.let { "$it\n\n$screenInfo" } ?: screenInfo)
            },
            ownScroll = false,
        )
    }
}

@Composable
fun ScreenTestSection(
    context: Context,
    viewModel: ScreenTestViewModel,
    activity: Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null,
    onShareClick: (String) -> Unit = {},
    /** False when a parent already scrolls; two same-direction scrollables conflict. */
    ownScroll: Boolean = true,
) {
    val screenPixelHistory by viewModel.screenPixelHistory.collectAsState()
    val lastTouchPointCount by viewModel.lastTouchPointCount.collectAsState()
    val lastPixelResult = screenPixelHistory.lastOrNull()

    // Same fix as CameraHealthSection: unblock the nav host's AI/Cert/Share FABs, which stay
    // in FabLoading() forever until shareText moves off the "Loading…" placeholder.
    LaunchedEffect(lastPixelResult, lastTouchPointCount) {
        val text = if (lastPixelResult != null || lastTouchPointCount != null) {
            CameraHealthUtils.buildScreenTestAiContext(lastPixelResult, lastTouchPointCount)
        } else {
            "Screen Test tab ready — run a colour, grid, or touch check for details."
        }
        onShareClick(text)
    }

    var showColorTest by remember { mutableStateOf(false) }
    var showGridTest by remember { mutableStateOf(false) }
    var showTouchTest by remember { mutableStateOf(false) }

    val isDarkMode = MaterialTheme.colorScheme.background == DesignSystemColors.Dark
    val cardBackground = if (isDarkMode) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surface
    val hasAnyResult = lastPixelResult != null || lastTouchPointCount != null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, DesignSystemColors.NeonGreen.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .then(
                    if (ownScroll) Modifier.verticalScroll(rememberScrollState())
                    else Modifier
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.screen_test_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (hasAnyResult && onItemAIClick != null) {
                    IconButton(
                        modifier = Modifier.size(32.dp),
                        onClick = {
                            val content = CameraHealthUtils.buildScreenTestAiContext(
                                lastPixelResult,
                                lastTouchPointCount,
                            )
                            onItemAIClick("Screen Test", content)
                        },
                    ) {
                        Icon(
                            AIIcon.icon,
                            contentDescription = stringResource(R.string.ask_ai),
                            tint = AIIcon.color(),
                        )
                    }
                }
            }

            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.screen_test_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (hasAnyResult) {
                Spacer(Modifier.size(12.dp))
                ScreenTestSummaryCard(lastPixelResult, lastTouchPointCount)
            }

            Spacer(Modifier.size(12.dp))
            Button(
                onClick = {
                    AnalyticsUtils.logEvent(AnalyticsEvent.ScreenPixelTestStarted)
                    showColorTest = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.screen_test_dead_pixels_button))
            }

            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = {
                    AnalyticsUtils.logEvent(AnalyticsEvent.ScreenGridTestViewed)
                    showGridTest = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.screen_test_scratches_button))
            }

            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = { showTouchTest = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.screen_test_touch_button))
            }

            // Same FAB-overlay clearance as the Camera tab — see camera_health_card.kt.
            Spacer(Modifier.size(96.dp))
        }
    }

    if (showColorTest) {
        ScreenPixelTestDialog(
            onDismiss = { showColorTest = false },
            onResult = { issue, color -> viewModel.recordScreenPixelResult(issue, color) },
        )
    }
    if (showGridTest) {
        GridTestDialog(onDismiss = { showGridTest = false })
    }
    if (showTouchTest) {
        TouchTestDialog(
            onDismiss = { showTouchTest = false },
            onResult = { maxTouches -> viewModel.recordTouchTestResult(maxTouches) },
        )
    }
}

@Composable
private fun ScreenTestSummaryCard(
    lastPixelResult: CameraHealthUtils.ScreenPixelResult?,
    lastTouchPointCount: Int?,
) {
    val hasProblem = lastPixelResult?.userReportedIssue == true
    val (containerColor, contentColor, icon) = if (hasProblem) {
        Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Warning,
        )
    } else {
        Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.CheckCircle,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (lastPixelResult != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = contentColor)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (hasProblem) {
                            val colour = lastPixelResult.colorShownWhenReported
                            if (colour != null) {
                                stringResource(R.string.screen_test_summary_spot_on_colour, screenColorLabel(colour))
                            } else {
                                stringResource(R.string.screen_test_summary_spot)
                            }
                        } else {
                            stringResource(R.string.screen_test_summary_ok)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                }
            }
            if (lastTouchPointCount != null) {
                if (lastPixelResult != null) Spacer(Modifier.size(4.dp))
                Text(
                    stringResource(R.string.screen_test_summary_touch, lastTouchPointCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }
    }
}

// ── Colour / dead-pixel test — the phone cannot detect its own dead pixels; the eye can. ────

private val SCREEN_TEST_COLORS = listOf(
    "red" to Color.Red,
    "green" to Color.Green,
    "blue" to Color.Blue,
    "cyan" to Color.Cyan,
    "magenta" to Color.Magenta,
    "yellow" to Color.Yellow,
    "white" to Color.White,
    "black" to Color.Black,
    "grey" to Color.Gray,
)

/**
 * The colour name a person reads. [name] is the English word stored with a saved result and
 * sent to the AI report ("red", "grey", …), so it stays English everywhere but on screen.
 */
@Composable
private fun screenColorLabel(name: String): String = when (name) {
    "red" -> stringResource(R.string.screen_color_red)
    "green" -> stringResource(R.string.screen_color_green)
    "blue" -> stringResource(R.string.screen_color_blue)
    "cyan" -> stringResource(R.string.screen_color_cyan)
    "magenta" -> stringResource(R.string.screen_color_magenta)
    "yellow" -> stringResource(R.string.screen_color_yellow)
    "white" -> stringResource(R.string.screen_color_white)
    "black" -> stringResource(R.string.screen_color_black)
    "grey" -> stringResource(R.string.screen_color_grey)
    else -> name
}

@Composable
private fun ScreenPixelTestDialog(
    onDismiss: () -> Unit,
    onResult: (issueReported: Boolean, colorShown: String?) -> Unit,
) {
    var index by remember { mutableStateOf(0) }
    val (colorName, color) = SCREEN_TEST_COLORS[index]
    val isLight = color == Color.White || color == Color.Yellow || color == Color.Cyan
    val textColor = if (isLight) Color.Black else Color.White

    // The instruction text and buttons themselves sit on top of the very pixels being tested —
    // a defect directly underneath them would be invisible for as long as they're shown. They
    // auto-hide after a few seconds so the FULL colour is visible most of the time; tapping
    // anywhere brings them back (reported 2026-07-24 — "text might cover a blind spot").
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, index) {
        if (controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = FULL_SCREEN_DIALOG_PROPERTIES) {
        ImmersiveFullBleedEffect()
        Surface(modifier = Modifier.fillMaxSize(), color = color) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { controlsVisible = true })
                    },
            ) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Text(
                            stringResource(R.string.screen_pixel_question),
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            stringResource(
                                R.string.screen_pixel_showing,
                                screenColorLabel(colorName),
                                index + 1,
                                SCREEN_TEST_COLORS.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onResult(true, colorName)
                                    onDismiss()
                                },
                            ) { Text(stringResource(R.string.screen_pixel_bad_spot)) }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                                onClick = {
                                    if (index < SCREEN_TEST_COLORS.lastIndex) {
                                        index += 1
                                        controlsVisible = true
                                    } else {
                                        onResult(false, null)
                                        onDismiss()
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (index < SCREEN_TEST_COLORS.lastIndex) R.string.screen_pixel_next
                                        else R.string.screen_pixel_done
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.close), color = textColor)
                        }
                    }
                }
            }
        }
    }
}

// ── Grid test — helps the user's eye spot scratches / pressure marks along straight lines. ──

@Composable
private fun GridTestDialog(onDismiss: () -> Unit) {
    var isWhiteBackground by remember { mutableStateOf(true) }
    val bgColor = if (isWhiteBackground) Color.White else Color.Black
    val lineColor = if (isWhiteBackground) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
    val textColor = if (isWhiteBackground) Color.Black else Color.White

    // Same reasoning as ScreenPixelTestDialog: the instructions/buttons sit over real pixels —
    // auto-hide after a few seconds, tap anywhere to bring back.
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, isWhiteBackground) {
        if (controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = FULL_SCREEN_DIALOG_PROPERTIES) {
        ImmersiveFullBleedEffect()
        Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { controlsVisible = true })
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val spacing = 40.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                        x += spacing
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
                        y += spacing
                    }
                }
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
                ) {
                    Text(
                        stringResource(R.string.screen_grid_instruction),
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor,
                    )
                }
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                ) {
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            isWhiteBackground = !isWhiteBackground
                            controlsVisible = true
                        }) {
                            Text(
                                stringResource(
                                    if (isWhiteBackground) R.string.screen_grid_switch_dark
                                    else R.string.screen_grid_switch_bright
                                )
                            )
                        }
                        Button(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    }
                }
            }
        }
    }
}

// ── Touch test — genuinely provable via MotionEvent pointer count, not a guess. ─────────────

private val TOUCH_POINT_COLORS = listOf(Color.Red, Color.Cyan, Color.Yellow, Color.Green, Color.Magenta)

@Composable
private fun TouchTestDialog(
    onDismiss: () -> Unit,
    onResult: (maxSimultaneousTouches: Int) -> Unit,
) {
    var maxTouches by remember { mutableStateOf(0) }
    var pointerPositions by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val finish = { onResult(maxTouches); onDismiss() }

    Dialog(onDismissRequest = finish, properties = FULL_SCREEN_DIALOG_PROPERTIES) {
        ImmersiveFullBleedEffect()
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                pointerPositions = pressed.map { it.position }
                                if (pressed.size > maxTouches) maxTouches = pressed.size
                            }
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    pointerPositions.forEachIndexed { i, pos ->
                        drawCircle(
                            color = TOUCH_POINT_COLORS[i % TOUCH_POINT_COLORS.size],
                            radius = 70f,
                            center = pos,
                        )
                    }
                }
                Column(modifier = Modifier.align(Alignment.TopCenter).padding(24.dp)) {
                    Text(
                        stringResource(R.string.screen_touch_instruction),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        stringResource(R.string.screen_touch_count, pointerPositions.size, maxTouches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
                TextButton(
                    onClick = finish,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                ) {
                    Text(stringResource(R.string.screen_touch_done), color = Color.White)
                }
            }
        }
    }
}
