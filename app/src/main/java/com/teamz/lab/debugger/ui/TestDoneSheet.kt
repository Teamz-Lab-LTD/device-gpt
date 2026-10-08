package com.teamz.lab.debugger.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
    fun action(a: String) = try {
        AnalyticsUtils.logEvent(AnalyticsEvent.FsDoneCardAction, mapOf("action" to a))
    } catch (_: Throwable) { }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        try { AnalyticsUtils.logEvent(AnalyticsEvent.FsNotifPermissionResult, mapOf("granted" to granted)) } catch (_: Throwable) { }
        if (granted) Toast.makeText(context, "Weekly check-up is on", Toast.LENGTH_SHORT).show()
        TestDoneCard.dismiss()
    }
    AlertDialog(
        onDismissRequest = { action("dismiss"); TestDoneCard.dismiss() },
        title = { Text("Done ✓") },
        text = { Text("Want to keep an eye on your phone's health?") },
        confirmButton = {
            TextButton(onClick = {
                action("widget")
                WidgetPinPrompt.requestNow(context)
                TestDoneCard.dismiss()
            }) { Text("Add widget") }
        },
        dismissButton = {
            TextButton(onClick = {
                action("weekly")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Toast.makeText(context, "Weekly check-up is on", Toast.LENGTH_SHORT).show()
                    TestDoneCard.dismiss()
                }
            }) { Text("Weekly check-up") }
            TextButton(onClick = { action("dismiss"); TestDoneCard.dismiss() }) { Text("Not now") }
        },
    )
}
