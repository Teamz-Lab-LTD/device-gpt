package com.teamz.lab.debugger.ui

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.AdConfig
import com.teamz.lab.debugger.utils.LeaderboardManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.res.stringResource
import com.teamz.lab.debugger.utils.string

/**
 * Data Retention Reminder Dialog
 * 
 * Shows reminder to link Gmail account after 25 days
 * Explains that data will be removed after 30 days but kept after linking
 */
@Composable
fun DataRetentionReminderDialog(
    activity: Activity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Google Sign-In launcher (using standard API for now)
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                
                if (idToken != null) {
                    val success = LeaderboardManager.linkGmailAccount(context, idToken)
                    if (success) {
                        LeaderboardManager.setDataRetentionReminderShown(context)
                        onDismiss()
                    } else {
                        isLinking = false
                        android.widget.Toast.makeText(
                            context,
                            context.string(R.string.account_link_failed),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    isLinking = false
                }
            } catch (e: ApiException) {
                isLinking = false
                android.widget.Toast.makeText(
                    context,
                    context.string(R.string.account_sign_in_failed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                isLinking = false
                android.widget.Toast.makeText(
                    context,
                    context.string(R.string.lb_error_try_again),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.lb_dr_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                val daysUntilRemoval = LeaderboardManager.getDaysUntilDataRemoval(context)
                if (daysUntilRemoval > 0) {
                    Text(
                        text = stringResource(R.string.lb_dr_remove_in_days, daysUntilRemoval.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.lb_dr_kept),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.lb_dr_dont_worry),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.lb_dr_free),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isLinking = true
                    linkGmailAccount(activity, googleSignInLauncher) { success ->
                        if (!success) {
                            isLinking = false
                        }
                    }
                },
                enabled = !isLinking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignSystemColors.NeonGreen,
                    contentColor = DesignSystemColors.Dark
                )
            ) {
                if (isLinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = DesignSystemColors.Dark
                    )
                } else {
                    Text(stringResource(R.string.account_link_gmail))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                LeaderboardManager.setDataRetentionReminderShown(context)
                onDismiss()
            }) {
                Text(stringResource(R.string.lb_dr_later))
            }
        }
    )
}

/**
 * Link Gmail account using Google Sign-In
 */
private fun linkGmailAccount(
    activity: Activity,
    launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
    onResult: (Boolean) -> Unit
) {
    try {
        var webClientId = AdConfig.getOAuthClientId()
        if (webClientId.isEmpty()) {
            // Fallback to strings.xml if AdConfig returns empty
            webClientId = activity.getString(R.string.default_web_client_id)
        }
        if (webClientId.isNotEmpty() && webClientId != "YOUR_OAUTH_CLIENT_ID") {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            
            val googleSignInClient = GoogleSignIn.getClient(activity, gso)
            val signInIntent = googleSignInClient.signInIntent
            launcher.launch(signInIntent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            activity,
            activity.string(R.string.account_sign_in_start_failed),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        onResult(false)
    }
}

