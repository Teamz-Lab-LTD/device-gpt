package com.teamz.lab.debugger.services

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamz.lab.debugger.utils.LocaleManager

/**
 * Transparent consent Activity for [PhotoCaptureBridge]. Launched from the AI Bridge
 * HTTP thread; shows an Allow / Deny dialog for the current one-shot camera capture,
 * runs the actual capture on Allow, then finishes.
 *
 * Deliberately does NOT show a preview — this is a modal consent, not a viewfinder.
 * Showing what the camera sees BEFORE the user says Allow would leak the frame.
 *
 * Intent extras:
 *   - "camera_id" (optional String): the camera ID to capture from. Defaults to
 *     the first back-facing lens.
 *
 * Result contract: writes into [PhotoCaptureBridge.complete] and finishes. The
 * calling HTTP thread reads the result off the shared latch — this Activity does
 * not use setResult / Activity Result API (the caller isn't another Activity).
 */
class PhotoCaptureConsentActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Wake + show over lockscreen so a phone-in-pocket user still gets the dialog.
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val requestedCameraId = intent?.getStringExtra("camera_id")
        setContent {
            MaterialTheme {
                ConsentDialog(
                    onAllow = {
                        val resolvedId = requestedCameraId ?: pickDefaultCameraId() ?: run {
                            PhotoCaptureBridge.complete(PhotoCaptureBridge.Result(
                                jpegBytes = null, width = 0, height = 0, cameraId = null,
                                errorCode = "no_back_camera",
                                userMessage = "No back-facing camera was found on this phone.",
                            ))
                            finish()
                            return@ConsentDialog
                        }
                        PhotoCaptureBridge.capture(this, resolvedId) { result ->
                            PhotoCaptureBridge.complete(result)
                            finish()
                        }
                    },
                    onDeny = {
                        PhotoCaptureBridge.complete(PhotoCaptureBridge.Result(
                            jpegBytes = null, width = 0, height = 0,
                            cameraId = requestedCameraId,
                            errorCode = "user_denied",
                            userMessage = "You tapped Deny. The AI did not take a photo.",
                        ))
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun pickDefaultCameraId(): String? {
        val cm = getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        return try {
            cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull()
        } catch (e: Exception) { null }
    }
}

@androidx.compose.runtime.Composable
private fun ConsentDialog(onAllow: () -> Unit, onDeny: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDeny,
        icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
        title = { Text("Take a photo?") },
        text = {
            Column {
                Text(
                    "The AI on your laptop wants to take one photo with your back camera through the AI Bridge.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The photo goes only to your laptop AI. It is not saved to your phone. Nothing is uploaded by DeviceGPT.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { busy = true; onAllow() },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(if (busy) "Taking…" else "Allow once")
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny, enabled = !busy) {
                Text("Deny")
            }
        },
    )
}
