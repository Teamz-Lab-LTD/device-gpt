package com.teamz.lab.debugger.utils

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.teamz.lab.debugger.BuildConfig
import kotlinx.coroutines.tasks.await

/**
 * Passkey / Credential Manager auth wrapper.
 *
 * Replaces the legacy GoogleSignIn API with the modern Credential Manager
 * approach. Uses the same Firebase Auth backend -- only the credential
 * acquisition changes.
 *
 * Dependencies already in build.gradle.kts:
 *   - androidx.credentials:credentials:1.3.0
 *   - androidx.credentials:credentials-play-services-auth:1.3.0
 *   - com.google.android.libraries.identity.googleid:googleid:1.1.0
 */
object PasskeyAuthManager {

    private const val TAG = "PasskeyAuthManager"

    sealed class AuthOutcome {
        data class Success(val result: AuthResult, val isNewUser: Boolean) : AuthOutcome()
        data class Cancelled(val message: String = "Sign in was cancelled") : AuthOutcome()
        data class Error(val message: String, val exception: Exception? = null) : AuthOutcome()
    }

    /**
     * Sign in with Google via Credential Manager.
     *
     * Returns an AuthOutcome indicating success, cancellation, or error.
     * On success, the Firebase user is either linked (if anonymous) or
     * signed in directly.
     */
    suspend fun signInWithGoogle(context: Context): AuthOutcome {
        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.OAUTH_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            Log.d(TAG, "Requesting credential from Credential Manager...")
            val response: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = context
            )

            handleSignInResponse(response)
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Sign-in cancelled by user")
            AuthOutcome.Cancelled()
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No credentials available: ${e.message}")
            AuthOutcome.Error("No Google accounts available on this device", e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error: ${e.message}", e)
            AuthOutcome.Error("Sign-in failed: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sign-in: ${e.message}", e)
            AuthOutcome.Error("An unexpected error occurred: ${e.message}", e)
        }
    }

    private suspend fun handleSignInResponse(response: GetCredentialResponse): AuthOutcome {
        val credential = response.credential

        return try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            Log.d(TAG, "Got Google ID token, authenticating with Firebase...")

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            val result = if (currentUser != null && currentUser.isAnonymous) {
                // Link anonymous account with Google credential
                Log.d(TAG, "Linking anonymous account with Google credential...")
                try {
                    currentUser.linkWithCredential(firebaseCredential).await()
                } catch (_: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                    // Account already exists -- sign in directly
                    Log.d(TAG, "Account collision, signing in directly...")
                    auth.signInWithCredential(firebaseCredential).await()
                }
            } else {
                // Sign in directly
                Log.d(TAG, "Signing in with Google credential...")
                auth.signInWithCredential(firebaseCredential).await()
            }

            val isNewUser = result.additionalUserInfo?.isNewUser == true
            Log.d(TAG, "Firebase auth success. New user: $isNewUser")

            AuthOutcome.Success(result, isNewUser)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase auth failed: ${e.message}", e)
            AuthOutcome.Error("Authentication failed: ${e.message}", e)
        }
    }

    /**
     * Sign out from Firebase and clear Credential Manager state.
     */
    suspend fun signOut(context: Context) {
        try {
            FirebaseAuth.getInstance().signOut()
            // Credential Manager doesn't have a direct "sign out" -- clearing is
            // handled by Google's account management. The next sign-in will show
            // the account picker again.
            Log.d(TAG, "Signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign-out: ${e.message}", e)
        }
    }
}
