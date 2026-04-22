package com.teamz.lab.debugger.restore

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.teamz.lab.debugger.utils.ErrorHandler
import com.teamz.lab.debugger.utils.LeaderboardManager
import com.teamz.lab.debugger.utils.LocaleManager
import com.teamz.lab.debugger.utils.RevenueCatManager
import com.teamz.lab.debugger.ui.theme.ThemeManager
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.annotation.VisibleForTesting

data class RestoredState(
    val userId: String,
    val prefs: Map<String, String>,
    val restoredAt: Long,
)

/**
 * Android 14+ [Restore Credentials](https://developer.android.com/identity/sign-in/restore-credentials)
 * plus encrypted Auto Backup of a small prefs snapshot so language, theme, and IDs can return
 * after device migration. Sub-minSdk devices no-op safely.
 */
object RestoreCredentialManager {

    private const val PREFS = "devicegpt_app_restore"
    private const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"
    private const val KEY_RESTORE_PAYLOAD = "restore_enc_payload"
    private const val KEY_RESTORE_PAYLOAD_EXISTS = "restore_payload_exists"
    private const val KEY_RESTORE_APPLIED = "restore_credentials_applied"

    /** WebAuthn RP ID — must match Digital Asset Links for passkeys on this domain when enforced. */
    private const val WEBAUTHN_RP_ID = "teamzlab.com"
    private const val AES_GCM_TAG_BITS = 128

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun appPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markFirstRunCompleted(context: Context) {
        appPrefs(context).edit { putBoolean(KEY_FIRST_RUN_COMPLETED, true) }
    }

    fun isFirstRunCompleted(context: Context): Boolean =
        appPrefs(context).getBoolean(KEY_FIRST_RUN_COMPLETED, false)

    private fun isRestoreAlreadyApplied(context: Context): Boolean =
        appPrefs(context).getBoolean(KEY_RESTORE_APPLIED, false)

    private fun markRestoreApplied(context: Context) {
        appPrefs(context).edit { putBoolean(KEY_RESTORE_APPLIED, true) }
    }

    /**
     * Schedules a save on [Dispatchers.IO]. Safe from any thread; never blocks the UI thread.
     */
    fun scheduleSaveAfterStateChange(context: Context, firebaseUid: String?) {
        val uid = firebaseUid ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        ioScope.launch {
            runCatching { saveRestoreState(context.applicationContext, uid, collectPrefsSnapshot(context.applicationContext)) }
                .onFailure { e ->
                    ErrorHandler.logMessage("restore_credentials_save_failed: ${e.message}")
                }
        }
    }

    /**
     * Post-[ComponentActivity] launch: attempt migration restore once.
     * Call from `lifecycleScope.launch(Dispatchers.IO)` so the UI thread is never blocked.
     */
    suspend fun runPostLaunchRestore(context: Context) {
        runCatching { tryRestoreAndApply(context.applicationContext) }
            .onFailure { e ->
                ErrorHandler.logMessage("restore_credentials_launch_restore_error: ${e.message}")
            }
    }

    suspend fun saveRestoreState(context: Context, userId: String, prefs: Map<String, String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (!isFirstRunCompleted(context)) return
        val app = context.applicationContext
        val payload = RestoredState(
            userId = userId,
            prefs = prefs,
            restoredAt = System.currentTimeMillis(),
        )
        val enc = encryptRestoredState(app, payload)
        appPrefs(app).edit {
            putString(KEY_RESTORE_PAYLOAD, enc)
            putBoolean(KEY_RESTORE_PAYLOAD_EXISTS, true)
        }
        val cm = CredentialManager.create(app)
        val createJson = buildRestoreCreationJson(userId)
        try {
            withContext(Dispatchers.Main) {
                cm.createCredential(app, CreateRestoreCredentialRequest(createJson, isCloudBackupEnabled = true))
            }
        } catch (e: CreateCredentialException) {
            val retryWithoutCloud = e is E2eeUnavailableException ||
                e.cause is E2eeUnavailableException
            if (!retryWithoutCloud) {
                ErrorHandler.logMessage("restore_credentials_create_failed: ${e.message}")
                return
            }
            runCatching {
                withContext(Dispatchers.Main) {
                    cm.createCredential(app, CreateRestoreCredentialRequest(createJson, isCloudBackupEnabled = false))
                }
            }.onFailure { sub ->
                ErrorHandler.logMessage("restore_credentials_create_failed_no_cloud: ${sub.message}")
            }
        }
    }

    suspend fun restoreStateIfAny(context: Context): RestoredState? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val app = context.applicationContext
        var credentialOk = false
        try {
            val getJson = buildRestoreGetJson(randomChallengeB64Url())
            val cm = CredentialManager.create(app)
            val response = withContext(Dispatchers.Main) {
                cm.getCredential(
                    app,
                    GetCredentialRequest.Builder()
                        .addCredentialOption(GetRestoreCredentialOption(getJson))
                        .build(),
                )
            }
            if (response.credential.type == RestoreCredential.TYPE_RESTORE_CREDENTIAL) {
                credentialOk = true
            }
        } catch (_: NoCredentialException) {
            // Expected when no restore credential on device
        } catch (e: GetCredentialException) {
            ErrorHandler.logMessage("restore_credentials_get_failed: ${e.message}")
        }
        val blob = appPrefs(app).getString(KEY_RESTORE_PAYLOAD, null)
        val fromPrefs = if (!blob.isNullOrEmpty()) {
            runCatching { decryptRestoredState(app, blob) }.getOrNull()
        } else {
            null
        }
        return when {
            fromPrefs != null -> fromPrefs
            credentialOk -> null
            else -> null
        }
    }

    private suspend fun tryRestoreAndApply(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (!isFirstRunCompleted(context)) return
        if (isRestoreAlreadyApplied(context)) return
        // If Firebase already restored an auth session, skip (prefs usually match).
        if (FirebaseAuth.getInstance().currentUser != null) {
            return
        }
        val hasPayload = appPrefs(context).getBoolean(KEY_RESTORE_PAYLOAD_EXISTS, false) ||
            !appPrefs(context).getString(KEY_RESTORE_PAYLOAD, null).isNullOrEmpty()
        val state = restoreStateIfAny(context) ?: run {
            if (hasPayload) {
                ErrorHandler.logMessage("restore_credentials_fail: payload_present_but_decrypt_failed")
            }
            return
        }
        applyRestoredStateSuspend(context, state)
        markRestoreApplied(context)
        ErrorHandler.logMessage("restore_credentials_success uid=${state.userId}")
    }

    suspend fun applyRestoredStateSuspend(context: Context, state: RestoredState) {
        val app = context.applicationContext
        for ((compoundKey, value) in state.prefs) {
            val parts = compoundKey.split("|", limit = 2)
            if (parts.size != 2) continue
            val (file, key) = parts[0] to parts[1]
            val p = app.getSharedPreferences(file, Context.MODE_PRIVATE)
            when {
                key == "is_dark_mode" && file == "theme_preferences" ->
                    p.edit { putBoolean(key, value.equals("true", ignoreCase = true)) }
                key == "email_linked" && file == "leaderboard_prefs" ->
                    p.edit { putBoolean(key, value.equals("true", ignoreCase = true)) }
                key == "user_enable_monitor_service" && file == "monitor_service" ->
                    p.edit { putBoolean(key, value.equals("true", ignoreCase = true)) }
                else -> p.edit { putString(key, value) }
            }
        }
        LeaderboardManager.persistUserIdFromRestore(app, state.userId)
        val langCode = app.getSharedPreferences("locale_preferences", Context.MODE_PRIVATE)
            .getString("selected_language", null)
        LocaleManager.setLanguageFromCodeIfSupported(app, langCode)
        val themePrefs = app.getSharedPreferences("theme_preferences", Context.MODE_PRIVATE)
        val themeName = themePrefs.getString("selected_theme", null)
        if (!themeName.isNullOrEmpty()) {
            runCatching {
                val theme = com.teamz.lab.debugger.ui.theme.AppTheme.valueOf(themeName)
                ThemeManager.setTheme(theme, app)
            }
        }
        ThemeManager.setDarkMode(themePrefs.getBoolean("is_dark_mode", true), app)
        val idToken = state.prefs["firebase_google_id_token"]
        if (!idToken.isNullOrEmpty()) {
            try {
                val cred = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(cred).await()
            } catch (e: Exception) {
                ErrorHandler.logMessage("restore_credentials_firebase_silent_signin_failed: ${e.message}")
            }
        }
        val rcUid = FirebaseAuth.getInstance().currentUser?.uid ?: state.userId
        if (RevenueCatManager.isSdkConfigured()) {
            RevenueCatManager.setUserId(rcUid)
        }
    }

    private suspend fun collectPrefsSnapshot(context: Context): Map<String, String> {
        val app = context.applicationContext
        val out = linkedMapOf<String, String>()
        fun copyStringPref(file: String, key: String) {
            val v = app.getSharedPreferences(file, Context.MODE_PRIVATE).getString(key, null)
            if (v != null) out["$file|$key"] = v
        }
        copyStringPref("locale_preferences", "selected_language")
        copyStringPref("theme_preferences", "selected_theme")
        val dark = app.getSharedPreferences("theme_preferences", Context.MODE_PRIVATE)
            .getBoolean("is_dark_mode", true)
        out["theme_preferences|is_dark_mode"] = dark.toString()
        copyStringPref("leaderboard_prefs", "user_id")
        val emailLinked = app.getSharedPreferences("leaderboard_prefs", Context.MODE_PRIVATE)
            .getBoolean("email_linked", false)
        out["leaderboard_prefs|email_linked"] = emailLinked.toString()
        val monitorOn = app.getSharedPreferences("monitor_service", Context.MODE_PRIVATE)
            .getBoolean("user_enable_monitor_service", false)
        out["monitor_service|user_enable_monitor_service"] = monitorOn.toString()
        val token = try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (_: Exception) {
            null
        }
        if (!token.isNullOrEmpty()) {
            out["firebase_google_id_token"] = token
        }
        return out
    }

    private fun buildRestoreCreationJson(firebaseUid: String): String {
        val userIdBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val userIdB64 = base64UrlEncode(userIdBytes)
        val challengeB64 = randomChallengeB64Url()
        return JSONObject().apply {
            put("challenge", challengeB64)
            put(
                "rp",
                JSONObject().apply {
                    put("id", WEBAUTHN_RP_ID)
                    put("name", "DeviceGPT")
                },
            )
            put(
                "user",
                JSONObject().apply {
                    put("id", userIdB64)
                    put("name", firebaseUid)
                    put("displayName", "DeviceGPT")
                },
            )
            put(
                "pubKeyCredParams",
                org.json.JSONArray().apply {
                    put(JSONObject().put("type", "public-key").put("alg", -7))
                    put(JSONObject().put("type", "public-key").put("alg", -257))
                },
            )
            put(
                "authenticatorSelection",
                JSONObject().apply {
                    put("residentKey", "required")
                    put("userVerification", "preferred")
                },
            )
            put("timeout", 120000)
            put("attestation", "none")
        }.toString()
    }

    private fun buildRestoreGetJson(challengeB64: String): String =
        JSONObject().apply {
            put("challenge", challengeB64)
            put("timeout", 120000)
            put("rpId", WEBAUTHN_RP_ID)
            put("userVerification", "discouraged")
        }.toString()

    private fun randomChallengeB64Url(): String {
        val c = ByteArray(32)
        SecureRandom().nextBytes(c)
        return base64UrlEncode(c)
    }

    private fun base64UrlEncode(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)

    private fun signingCertDigest(context: Context): ByteArray {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val pkg = pm.getPackageInfo(context.packageName, flags)
        val signers = pkg.signingInfo?.apkContentsSigners ?: emptyArray()
        val cert = signers.firstOrNull()?.toByteArray() ?: ByteArray(0)
        return MessageDigest.getInstance("SHA-256").digest(cert)
    }

    private fun restoreAesKey(context: Context): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(context.packageName.toByteArray(Charsets.UTF_8))
        digest.update(0x7c)
        digest.update(signingCertDigest(context))
        return SecretKeySpec(digest.digest().copyOf(32), "AES")
    }

    private fun encryptRestoredState(context: Context, state: RestoredState): String {
        val prefsJson = JSONObject()
        state.prefs.forEach { (k, v) -> prefsJson.put(k, v) }
        val plain = JSONObject().apply {
            put("userId", state.userId)
            put("restoredAt", state.restoredAt)
            put("prefs", prefsJson)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, restoreAesKey(context), GCMParameterSpec(AES_GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        return base64UrlEncode(iv + ct)
    }

    private fun decryptRestoredState(context: Context, wrapped: String): RestoredState {
        val all = Base64.decode(wrapped, Base64.NO_WRAP or Base64.URL_SAFE)
        require(all.size > 12)
        val iv = all.copyOfRange(0, 12)
        val ct = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, restoreAesKey(context), GCMParameterSpec(AES_GCM_TAG_BITS, iv))
        val plain = String(cipher.doFinal(ct), Charsets.UTF_8)
        val root = JSONObject(plain)
        val prefsJson = root.getJSONObject("prefs")
        val map = buildMap {
            val it = prefsJson.keys()
            while (it.hasNext()) {
                val k = it.next()
                put(k, prefsJson.getString(k))
            }
        }
        return RestoredState(
            userId = root.getString("userId"),
            prefs = map,
            restoredAt = root.getLong("restoredAt"),
        )
    }

    @VisibleForTesting
    internal fun encryptRestoredStateForTest(context: Context, state: RestoredState): String =
        encryptRestoredState(context, state)

    @VisibleForTesting
    internal fun decryptRestoredStateForTest(context: Context, wrapped: String): RestoredState =
        decryptRestoredState(context, wrapped)
}
