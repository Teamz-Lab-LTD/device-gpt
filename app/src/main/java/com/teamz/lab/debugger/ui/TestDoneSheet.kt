package com.teamz.lab.debugger.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.teamz.lab.debugger.ui.theme.DgMotion
import com.teamz.lab.debugger.ui.theme.motionTween
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.TestDoneCard
import com.teamz.lab.debugger.utils.WidgetPinPrompt
import com.teamz.lab.debugger.utils.string
import com.teamz.lab.debugger.ui.icons.DgIconText
import com.teamz.lab.debugger.ui.icons.DgStock
import com.teamz.lab.debugger.ui.icons.IconTone

/**
 * One-time card after the first completed test (arm B). Spec 2026-10-09.
 * "Weekly check-up" is the first notification-permission ask with a reason in front of it: the
 * weekly report notification is already scheduled at app start, but on Android 13+ it reached
 * almost nobody (D1 push: 2 of 99) because permission was never asked well.
 */
@Composable
fun TestDoneSheet() {
    val visible by TestDoneCard.visible.collectAsState()
    // The card rises from the bottom with its scrim and leaves faster than it came. It stays composed until
    // the leaving move has finished, then goes away.
    val presence = remember { MutableTransitionState(false) }
    presence.targetState = visible
    if (!visible && presence.isIdle && !presence.currentState) return
    val context = LocalContext.current
    // Logged when the dialog is really on screen (review 2026-10-09 M2), once per showing.
    LaunchedEffect(Unit) {
        if (TestDoneCard.claimShownLog())
            try { AnalyticsUtils.logEvent(AnalyticsEvent.FsDoneCardShown) } catch (_: Throwable) { }
    }
    fun action(a: String, extra: Map<String, Any> = emptyMap()) = try {
        AnalyticsUtils.logEvent(AnalyticsEvent.FsDoneCardAction, mapOf("action" to a) + extra)
    } catch (_: Throwable) { }
    // Never say "on" unless notifications can actually arrive: below Android 13 there is no
    // permission prompt, and the user may have switched the app's notifications off (review I4).
    fun confirmWeekly() {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Toast.makeText(context, context.string(R.string.done_weekly_on), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context, context.string(R.string.done_weekly_turn_on_notifications), Toast.LENGTH_LONG
            ).show()
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) { }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        try { AnalyticsUtils.logEvent(AnalyticsEvent.FsNotifPermissionResult, mapOf("granted" to granted)) } catch (_: Throwable) { }
        // Denied (or blocked after two denials, when Android answers at once): say why nothing
        // happened instead of closing silently (re-review minor 4).
        if (granted) confirmWeekly()
        else Toast.makeText(
            context, context.string(R.string.done_weekly_needs_notifications), Toast.LENGTH_LONG
        ).show()
        TestDoneCard.dismiss()
    }
    val dismiss = { action("dismiss"); TestDoneCard.dismiss() }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The scrim and the card are drawn and moved here, so the window brings no dim or animation of its own.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visibleState = presence,
                enter = fadeIn(motionTween(DgMotion.slow, easing = DgMotion.Enter)),
                exit = fadeOut(motionTween(DgMotion.quick, easing = DgMotion.Exit)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = dismiss,
                        ),
                )
            }
            AnimatedVisibility(
                visibleState = presence,
                enter = slideInVertically(motionTween(DgMotion.slow, easing = DgMotion.Enter)) { it } +
                    fadeIn(motionTween(DgMotion.standard, easing = DgMotion.Enter)),
                exit = slideOutVertically(motionTween(DgMotion.quick, easing = DgMotion.Exit)) { it } +
                    fadeOut(motionTween(DgMotion.quick, easing = DgMotion.Exit)),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        // Taps on the card must not fall through to the scrim.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                    ) {
                        DgIconText(
                            icon = DgStock.CheckCircle,
                            tone = IconTone.Good,
                            text = stringResource(R.string.done_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.done_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                // Some launchers cannot pin from inside an app; say how instead of doing nothing (M3).
                                val asked = WidgetPinPrompt.requestNow(context)
                                action("widget", mapOf("pin_supported" to asked))
                                if (!asked) Toast.makeText(
                                    context, context.string(R.string.done_widget_how_to),
                                    Toast.LENGTH_LONG
                                ).show()
                                TestDoneCard.dismiss()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) { Text(stringResource(R.string.done_add_widget)) }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                action("weekly")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    confirmWeekly()
                                    TestDoneCard.dismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.done_weekly_checkup)) }
                        TextButton(
                            onClick = dismiss,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.not_now)) }
                    }
                }
            }
        }
    }
}
