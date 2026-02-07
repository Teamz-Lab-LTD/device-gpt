package com.teamz.lab.debugger.utils

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.MessageDigest

/**
 * Report Signer -- manages an ECDSA keypair in the Android KeyStore
 * and provides signing / verification for device reports.
 *
 * The private key never leaves the hardware-backed keystore.
 * The public key is exported and stored alongside the report for
 * verification by any other DeviceGPT instance.
 */
object ReportSigner {

    private const val TAG = "ReportSigner"
    private const val KEY_ALIAS = "devicegpt_report_signing_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    /**
     * Get or create the ECDSA signing key in Android KeyStore.
     * Returns the base64-encoded public key.
     */
    fun getOrCreatePublicKey(): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            createSigningKey()
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val publicKeyBytes = entry.certificate.publicKey.encoded
        return Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
    }

    private fun createSigningKey() {
        Log.d(TAG, "Creating new ECDSA signing key in AndroidKeyStore...")

        val paramBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))

        // Hardware-backed on supported devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paramBuilder.setIsStrongBoxBacked(false) // StrongBox may not be available
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER
        )
        keyPairGenerator.initialize(paramBuilder.build())
        keyPairGenerator.generateKeyPair()

        Log.d(TAG, "Signing key created successfully")
    }

    /**
     * Sign the given data string and return a base64-encoded signature.
     */
    fun signData(data: String): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            createSigningKey()
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(entry.privateKey)
        signature.update(data.toByteArray(Charsets.UTF_8))
        val signatureBytes = signature.sign()

        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    /**
     * Verify a signature against the given data and public key.
     *
     * @param data The original data string
     * @param signatureBase64 The base64-encoded signature
     * @param publicKeyBase64 The base64-encoded public key
     * @return true if the signature is valid
     */
    fun verifySignature(
        data: String,
        signatureBase64: String,
        publicKeyBase64: String
    ): Boolean {
        return try {
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val publicKey = keyFactory.generatePublic(
                java.security.spec.X509EncodedKeySpec(publicKeyBytes)
            )

            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)

            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(publicKey)
            sig.update(data.toByteArray(Charsets.UTF_8))
            sig.verify(signatureBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed: ${e.message}", e)
            false
        }
    }

    /**
     * Generate a short verification code from the signature.
     * Takes the first 8 hex characters of SHA-256(signature).
     * Format: "DG-XXXXXXXX"
     */
    fun generateVerificationCode(signatureBase64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(signatureBase64.toByteArray(Charsets.UTF_8))
        val hex = hash.joinToString("") { "%02X".format(it) }
        return "DG-${hex.take(8)}"
    }
}
