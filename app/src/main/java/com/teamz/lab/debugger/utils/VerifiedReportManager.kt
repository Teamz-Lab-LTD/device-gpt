package com.teamz.lab.debugger.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

// --- Data models ---

data class ReportData(
    val deviceModel: String,
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatchLevel: String,
    val rootStatus: String,
    val cpuModel: String,
    val screenResolution: String,
    val playCertified: String,
    val healthScore: Int,
    val privacyGrade: String,
    val downloadSpeed: String,
    val uploadSpeed: String,
    val latency: String,
    val jitter: String,
    val packetLoss: String,
    val batteryHealth: String,
    val storageInfo: String,
    val ramInfo: String,
    val timestamp: String,
    val reportId: String
)

data class VerifiedReport(
    val reportId: String,
    val reportData: ReportData,
    val reportDataJson: String,
    val signature: String,
    val publicKey: String,
    val verificationCode: String,
    val signedAt: String,
    val appVersion: String
)

enum class VerificationResult {
    VERIFIED,
    TAMPERED,
    NOT_FOUND,
    ERROR
}

/**
 * Verified Report Manager -- orchestrates collecting device data,
 * signing it with the Android KeyStore, uploading to Firestore,
 * and looking up reports for verification.
 *
 * All data is privacy-safe: no IMEI, serial, MAC, IP, email, or UID.
 */
object VerifiedReportManager {

    private const val TAG = "VerifiedReportManager"
    private const val COLLECTION_REPORTS = "verified_reports"
    private const val COLLECTION_CODES = "verification_codes"

    /**
     * Collect device data, sign it, and return a VerifiedReport.
     * Does NOT upload to Firestore -- call uploadReport() separately.
     */
    suspend fun generateReport(context: Context): VerifiedReport = withContext(Dispatchers.IO) {
        val reportData = collectReportData(context)
        val canonicalJson = serializeCanonical(reportData)

        // Sign the canonical JSON
        val signature = ReportSigner.signData(canonicalJson)
        val publicKey = ReportSigner.getOrCreatePublicKey()
        val verificationCode = ReportSigner.generateVerificationCode(signature)

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
        val signedAt = isoFormat.format(Date())

        VerifiedReport(
            reportId = reportData.reportId,
            reportData = reportData,
            reportDataJson = canonicalJson,
            signature = signature,
            publicKey = publicKey,
            verificationCode = verificationCode,
            signedAt = signedAt,
            appVersion = com.teamz.lab.debugger.BuildConfig.VERSION_NAME
        )
    }

    /**
     * Upload a verified report to Firestore for public verification.
     */
    suspend fun uploadReport(report: VerifiedReport): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = FirebaseFirestore.getInstance()

            // Hash the Firebase UID for creator tracking (never store raw UID)
            val creatorHash = FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                val digest = MessageDigest.getInstance("SHA-256")
                digest.digest(uid.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            } ?: "anonymous"

            // Upload the full report
            val reportDoc = hashMapOf(
                "reportDataJson" to report.reportDataJson,
                "signature" to report.signature,
                "publicKey" to report.publicKey,
                "verificationCode" to report.verificationCode,
                "signedAt" to report.signedAt,
                "appVersion" to report.appVersion,
                "creatorHash" to creatorHash
            )
            db.collection(COLLECTION_REPORTS)
                .document(report.reportId)
                .set(reportDoc)
                .await()

            // Upload the verification code index
            val codeDoc = hashMapOf(
                "reportId" to report.reportId,
                "createdAt" to report.signedAt
            )
            db.collection(COLLECTION_CODES)
                .document(report.verificationCode)
                .set(codeDoc)
                .await()

            Log.d(TAG, "Report uploaded: ${report.reportId} / ${report.verificationCode}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload report: ${e.message}", e)
            false
        }
    }

    /**
     * Look up a report by verification code and verify its signature.
     */
    suspend fun verifyReport(verificationCode: String): Pair<VerificationResult, Map<String, Any>?> =
        withContext(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()

                // Step 1: Look up report ID from code
                val codeDoc = db.collection(COLLECTION_CODES)
                    .document(verificationCode.uppercase())
                    .get()
                    .await()

                if (!codeDoc.exists()) {
                    return@withContext Pair(VerificationResult.NOT_FOUND, null)
                }

                val reportId = codeDoc.getString("reportId")
                    ?: return@withContext Pair(VerificationResult.NOT_FOUND, null)

                // Step 2: Fetch the full report
                val reportDoc = db.collection(COLLECTION_REPORTS)
                    .document(reportId)
                    .get()
                    .await()

                if (!reportDoc.exists()) {
                    return@withContext Pair(VerificationResult.NOT_FOUND, null)
                }

                val reportDataJson = reportDoc.getString("reportDataJson") ?: ""
                val signature = reportDoc.getString("signature") ?: ""
                val publicKey = reportDoc.getString("publicKey") ?: ""
                val signedAt = reportDoc.getString("signedAt") ?: ""
                val appVersion = reportDoc.getString("appVersion") ?: ""

                // Step 3: Verify the signature
                val isValid = ReportSigner.verifySignature(reportDataJson, signature, publicKey)

                val reportInfo = mapOf(
                    "reportDataJson" to reportDataJson,
                    "signedAt" to signedAt,
                    "appVersion" to appVersion,
                    "verificationCode" to verificationCode
                )

                if (isValid) {
                    Pair(VerificationResult.VERIFIED, reportInfo)
                } else {
                    Pair(VerificationResult.TAMPERED, reportInfo)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Verification error: ${e.message}", e)
                Pair(VerificationResult.ERROR, null)
            }
        }

    // --- Data collection (privacy-safe) ---

    private suspend fun collectReportData(context: Context): ReportData {
        val reportId = UUID.randomUUID().toString()

        // Use existing functions from the codebase
        val healthScore = try {
            HealthScoreUtils.calculateDailyHealthScore(context)
        } catch (_: Exception) { 0 }

        val privacyReport = try {
            NetworkPrivacyScorer.runPrivacyAudit(context)
        } catch (_: Exception) { null }

        val downloadSpeed = try { getNetworkDownloadSpeed() } catch (_: Exception) { "N/A" }
        val uploadSpeed = try { getNetworkUploadSpeed() } catch (_: Exception) { "N/A" }
        val latency = try { getCompactLatency() } catch (_: Exception) { "N/A" }
        val jitter = try { getJitter() } catch (_: Exception) { "N/A" }
        val packetLoss = try { getPacketLoss() } catch (_: Exception) { "N/A" }
        val batteryInfo = try { getBatteryChargingInfo(context) } catch (_: Exception) { "N/A" }
        val storageInfo = try { getMemoryAndStorageInfo(context) } catch (_: Exception) { "N/A" }
        val ramInfo = try { getRamUsage(context) } catch (_: Exception) { "N/A" }
        val rootStatus = try {
            if (isDeviceRooted() == "Yes") "Rooted" else "Not rooted"
        } catch (_: Exception) { "N/A" }
        val screenResolution = try {
            getDisplayInfo(context)["📺 Screen Resolution"] ?: "N/A"
        } catch (_: Exception) { "N/A" }
        val cpuModel = if (Build.VERSION.SDK_INT >= 31) {
            listOfNotNull(Build.SOC_MANUFACTURER, Build.SOC_MODEL).joinToString(" ").take(50).ifEmpty { Build.HARDWARE.take(50) }
        } else {
            Build.HARDWARE.take(50)
        }
        val playCertified = try { isPlayStoreCertified(context) } catch (_: Exception) { "N/A" }

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")

        return ReportData(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            securityPatchLevel = Build.VERSION.SECURITY_PATCH,
            rootStatus = rootStatus,
            cpuModel = cpuModel,
            screenResolution = screenResolution,
            playCertified = playCertified,
            healthScore = healthScore,
            privacyGrade = privacyReport?.grade ?: "N/A",
            downloadSpeed = downloadSpeed.take(30),
            uploadSpeed = uploadSpeed.take(30),
            latency = latency.take(20),
            jitter = jitter.take(20),
            packetLoss = packetLoss.take(40),
            batteryHealth = batteryInfo.take(60),
            storageInfo = storageInfo.take(80),
            ramInfo = ramInfo.take(60),
            timestamp = isoFormat.format(Date()),
            reportId = reportId
        )
    }

    /**
     * Serialize report data to canonical JSON (sorted keys, no whitespace).
     */
    private fun serializeCanonical(data: ReportData): String {
        // Use sorted keys for deterministic serialization
        val json = JSONObject()
        json.put("androidVersion", data.androidVersion)
        json.put("apiLevel", data.apiLevel)
        json.put("batteryHealth", data.batteryHealth)
        json.put("cpuModel", data.cpuModel)
        json.put("deviceModel", data.deviceModel)
        json.put("playCertified", data.playCertified)
        json.put("screenResolution", data.screenResolution)
        json.put("downloadSpeed", data.downloadSpeed)
        json.put("healthScore", data.healthScore)
        json.put("jitter", data.jitter)
        json.put("latency", data.latency)
        json.put("packetLoss", data.packetLoss)
        json.put("privacyGrade", data.privacyGrade)
        json.put("ramInfo", data.ramInfo)
        json.put("reportId", data.reportId)
        json.put("rootStatus", data.rootStatus)
        json.put("securityPatchLevel", data.securityPatchLevel)
        json.put("storageInfo", data.storageInfo)
        json.put("timestamp", data.timestamp)
        json.put("uploadSpeed", data.uploadSpeed)
        return json.toString()
    }

    /**
     * Parse report data from canonical JSON.
     */
    fun parseReportData(json: String): ReportData? {
        return try {
            val obj = JSONObject(json)
            ReportData(
                deviceModel = obj.optString("deviceModel", "Unknown"),
                androidVersion = obj.optString("androidVersion", ""),
                apiLevel = obj.optInt("apiLevel", 0),
                securityPatchLevel = obj.optString("securityPatchLevel", "N/A"),
                rootStatus = obj.optString("rootStatus", "N/A"),
                cpuModel = obj.optString("cpuModel", "N/A"),
                screenResolution = obj.optString("screenResolution", "N/A"),
                playCertified = obj.optString("playCertified", "N/A"),
                healthScore = obj.optInt("healthScore", 0),
                privacyGrade = obj.optString("privacyGrade", "N/A"),
                downloadSpeed = obj.optString("downloadSpeed", "N/A"),
                uploadSpeed = obj.optString("uploadSpeed", "N/A"),
                latency = obj.optString("latency", "N/A"),
                jitter = obj.optString("jitter", "N/A"),
                packetLoss = obj.optString("packetLoss", "N/A"),
                batteryHealth = obj.optString("batteryHealth", "N/A"),
                storageInfo = obj.optString("storageInfo", "N/A"),
                ramInfo = obj.optString("ramInfo", "N/A"),
                timestamp = obj.optString("timestamp", ""),
                reportId = obj.optString("reportId", "")
            )
        } catch (_: Exception) {
            null
        }
    }
}
