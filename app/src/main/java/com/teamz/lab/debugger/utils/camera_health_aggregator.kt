package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persistence for camera + screen diagnostic results. Manual delimited-string encoding,
 * matching the idiom already used by [PowerConsumptionAggregator] elsewhere in this app
 * (no Gson/Room dependency exists here).
 */
object CameraHealthAggregator {
    private const val PREFS_NAME = "camera_health_prefs"
    private const val KEY_HISTORY = "camera_health_history"
    private const val KEY_SCREEN_PIXEL_HISTORY = "screen_pixel_history"
    private const val MAX_HISTORY = 10

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Camera health results ───────────────────────────────────────────────

    fun saveCameraHealthResult(context: Context, result: CameraHealthUtils.CameraHealthResult) {
        val existing = loadCameraHealthHistory(context).toMutableList()
        existing.add(result)
        val trimmed = existing.takeLast(MAX_HISTORY)
        val encoded = trimmed.joinToString("~") { encodeResult(it) }
        getPrefs(context).edit { putString(KEY_HISTORY, encoded) }
    }

    fun loadCameraHealthHistory(context: Context): List<CameraHealthUtils.CameraHealthResult> {
        val raw = getPrefs(context).getString(KEY_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("~").mapNotNull { decodeResult(it) }
    }

    private fun encodePhysicalLenses(lenses: List<CameraHealthUtils.PhysicalLensSpec>): String =
        lenses.joinToString("^") { p ->
            listOf(p.physicalId, p.focalLengthsMm.joinToString("/"), p.aperturesF.joinToString("/"))
                .joinToString("&")
        }

    private fun decodePhysicalLenses(raw: String): List<CameraHealthUtils.PhysicalLensSpec> {
        if (raw.isEmpty()) return emptyList()
        return raw.split("^").map { entry ->
            val f = entry.split("&")
            CameraHealthUtils.PhysicalLensSpec(
                physicalId = f[0],
                focalLengthsMm = if (f[1].isEmpty()) emptyList() else f[1].split("/").map { it.toFloat() },
                aperturesF = if (f[2].isEmpty()) emptyList() else f[2].split("/").map { it.toFloat() },
            )
        }
    }

    private fun encodeResult(result: CameraHealthUtils.CameraHealthResult): String {
        val lensesEncoded = result.factSheet.lenses.joinToString(";") { lens ->
            listOf(
                lens.cameraId,
                lens.facing,
                lens.hardwareLevel,
                lens.focalLengthsMm.joinToString("/"),
                lens.maxDigitalZoom?.toString() ?: "",
                lens.hasOpticalStabilization,
                lens.hasFlash,
                lens.supportsAutofocus,
                lens.rawAvailableToThisApp,
                lens.physicalLensCount,
                lens.aperturesF.joinToString("/"),
                lens.sensorSizeMm ?: "",
                lens.isoRange ?: "",
                lens.hasVideoStabilization,
                encodePhysicalLenses(lens.physicalLenses),
                lens.exposureTimeRangeSec ?: "",
                lens.aeModes.joinToString("/"),
                lens.jpegResolutions.joinToString("/"),
                lens.videoRecordingSupported,
                lens.maxVideoResolution ?: "",
            ).joinToString(",")
        }
        val livenessEncoded = result.liveness.joinToString(";") { l ->
            listOf(
                l.cameraId,
                l.facing,
                l.opened,
                l.frameReceived,
                l.autofocusConverged?.toString() ?: "null",
                l.openToFrameMs?.toString() ?: "",
                (l.errorReason ?: "").replace(",", " "),
                l.activePhysicalCameraId ?: "",
            ).joinToString(",")
        }
        return "${result.timestamp}|$lensesEncoded|$livenessEncoded"
    }

    private fun decodeResult(raw: String): CameraHealthUtils.CameraHealthResult? {
        return try {
            val parts = raw.split("|", limit = 3)
            if (parts.size < 3) return null
            val timestamp = parts[0].toLong()

            val lenses = if (parts[1].isEmpty()) emptyList() else parts[1].split(";").map { row ->
                val f = row.split(",")
                CameraHealthUtils.LensReport(
                    cameraId = f[0],
                    facing = f[1],
                    hardwareLevel = f[2],
                    focalLengthsMm = if (f[3].isEmpty()) emptyList() else f[3].split("/").map { it.toFloat() },
                    maxDigitalZoom = f[4].takeIf { it.isNotEmpty() }?.toFloat(),
                    hasOpticalStabilization = f[5].toBoolean(),
                    hasFlash = f[6].toBoolean(),
                    supportsAutofocus = f[7].toBoolean(),
                    rawAvailableToThisApp = f[8].toBoolean(),
                    physicalLensCount = f[9].toInt(),
                    // Fields 10-14 were added 2026-07-24 (screen-test split + enrichment pass).
                    // Guard with bounds checks so history saved before this pass still decodes.
                    aperturesF = f.getOrNull(10)?.takeIf { it.isNotEmpty() }
                        ?.split("/")?.map { it.toFloat() } ?: emptyList(),
                    sensorSizeMm = f.getOrNull(11)?.takeIf { it.isNotEmpty() },
                    isoRange = f.getOrNull(12)?.takeIf { it.isNotEmpty() },
                    hasVideoStabilization = f.getOrNull(13)?.toBoolean() ?: false,
                    physicalLenses = f.getOrNull(14)?.let { decodePhysicalLenses(it) } ?: emptyList(),
                    // Fields 15-17 added 2026-07-24 (shutter speed / AE modes / resolutions).
                    exposureTimeRangeSec = f.getOrNull(15)?.takeIf { it.isNotEmpty() },
                    aeModes = f.getOrNull(16)?.takeIf { it.isNotEmpty() }?.split("/") ?: emptyList(),
                    jpegResolutions = f.getOrNull(17)?.takeIf { it.isNotEmpty() }?.split("/") ?: emptyList(),
                    // Fields 18-19 added 2026-07-24 (video recording capability).
                    videoRecordingSupported = f.getOrNull(18)?.toBoolean() ?: false,
                    maxVideoResolution = f.getOrNull(19)?.takeIf { it.isNotEmpty() },
                )
            }

            val liveness = if (parts[2].isEmpty()) emptyList() else parts[2].split(";").map { row ->
                val f = row.split(",")
                CameraHealthUtils.CameraLivenessResult(
                    cameraId = f[0],
                    facing = f[1],
                    opened = f[2].toBoolean(),
                    frameReceived = f[3].toBoolean(),
                    autofocusConverged = if (f[4] == "null") null else f[4].toBoolean(),
                    openToFrameMs = f[5].takeIf { it.isNotEmpty() }?.toLong(),
                    errorReason = f[6].takeIf { it.isNotEmpty() },
                    // Field 7 added 2026-07-24 — see the lens-row bounds-check note above.
                    activePhysicalCameraId = f.getOrNull(7)?.takeIf { it.isNotEmpty() },
                )
            }

            CameraHealthUtils.CameraHealthResult(
                factSheet = CameraHealthUtils.CameraFactSheet(lenses, timestamp),
                liveness = liveness,
                timestamp = timestamp,
            )
        } catch (e: Exception) {
            handleError(e, context = "CameraHealthAggregator.decodeResult")
            null
        }
    }

    // ── Screen pixel results ────────────────────────────────────────────────

    fun saveScreenPixelResult(context: Context, result: CameraHealthUtils.ScreenPixelResult) {
        val existing = loadScreenPixelHistory(context).toMutableList()
        existing.add(result)
        val trimmed = existing.takeLast(MAX_HISTORY)
        val encoded = trimmed.joinToString("~") {
            "${it.timestamp},${it.userReportedIssue},${it.colorShownWhenReported ?: ""}"
        }
        getPrefs(context).edit { putString(KEY_SCREEN_PIXEL_HISTORY, encoded) }
    }

    fun loadScreenPixelHistory(context: Context): List<CameraHealthUtils.ScreenPixelResult> {
        val raw = getPrefs(context).getString(KEY_SCREEN_PIXEL_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("~").mapNotNull { row ->
            try {
                val f = row.split(",")
                CameraHealthUtils.ScreenPixelResult(
                    userReportedIssue = f[1].toBoolean(),
                    colorShownWhenReported = f[2].takeIf { it.isNotEmpty() },
                    timestamp = f[0].toLong(),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
