package com.teamz.lab.debugger.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.TestDoneCard
import com.teamz.lab.debugger.utils.WidgetPinPrompt

/**
 * One-time card after the first completed test (arm B). Spec 2026-10-09.
 * "Weekly check-up" is the first notification-permission ask with a reason in front of it: the
 * weekly report notification is already scheduled at app start, but on Android 13+ it reached
 * almost nobody (D1 push: 2 of 99) because permission was never asked well.
 */
@Composable
fun TestDoneSheet() {
    val visible by TestDoneCard.visible.collectAsState()
    if (!visible) return
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
            Toast.makeText(context, "Weekly check-up is on", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Turn on notifications to get the weekly check-up", Toast.LENGTH_LONG).show()
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
        else Toast.makeText(context, "The weekly check-up needs notifications. You can turn them on in Settings.", Toast.LENGTH_LONG).show()
        TestDoneCard.dismiss()
    }
    AlertDialog(
        onDismissRequest = { action("dismiss"); TestDoneCard.dismiss() },
        title = { Text("Done ✓") },
        text = { Text("Want to keep an eye on your phone's health?") },
        confirmButton = {
            TextButton(onClick = {
                // Some launchers cannot pin from inside an app; say how instead of doing nothing (M3).
                val asked = WidgetPinPrompt.requestNow(context)
                action("widget", mapOf("pin_supported" to asked))
                if (!asked) Toast.makeText(
                    context, "Long-press your home screen, tap Widgets, then pick DeviceGPT",
                    Toast.LENGTH_LONG
                ).show()
                TestDoneCard.dismiss()
            }) { Text("Add widget") }
        },
        dismissButton = {
            TextButton(onClick = {
                action("weekly")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    confirmWeekly()
                    TestDoneCard.dismiss()
                }
            }) { Text("Weekly check-up") }
            TextButton(onClick = { action("dismiss"); TestDoneCard.dismiss() }) { Text("Not now") }
        },
    )
}
