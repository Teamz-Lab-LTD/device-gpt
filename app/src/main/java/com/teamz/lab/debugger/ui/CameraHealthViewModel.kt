package com.teamz.lab.debugger.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.CameraHealthAggregator
import com.teamz.lab.debugger.utils.CameraHealthUtils
import com.teamz.lab.debugger.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Camera tab (fact sheet + liveness check only — screen tests moved to
 * [ScreenTestViewModel] when the Screen Test tab split out on 2026-07-24).
 * Persists across activity recreation, matching [PowerConsumptionViewModel]'s reason for
 * existing: an interstitial ad can recreate the Activity mid-test.
 */
class CameraHealthViewModel(application: Application) : AndroidViewModel(application) {

    private val _isCheckRunning = MutableStateFlow(false)
    val isCheckRunning: StateFlow<Boolean> = _isCheckRunning.asStateFlow()

    private val _factSheet = MutableStateFlow<CameraHealthUtils.CameraFactSheet?>(null)
    val factSheet: StateFlow<CameraHealthUtils.CameraFactSheet?> = _factSheet.asStateFlow()

    private val _latestResult = MutableStateFlow<CameraHealthUtils.CameraHealthResult?>(null)
    val latestResult: StateFlow<CameraHealthUtils.CameraHealthResult?> = _latestResult.asStateFlow()

    private val _history = MutableStateFlow<List<CameraHealthUtils.CameraHealthResult>>(emptyList())
    val history: StateFlow<List<CameraHealthUtils.CameraHealthResult>> = _history.asStateFlow()

    private val _showCsvDialog = MutableStateFlow(false)
    val showCsvDialog: StateFlow<Boolean> = _showCsvDialog.asStateFlow()

    // What each camera actually saw during the last check, keyed by camera ID — the user's own
    // eyes judging the photo, same as the screen test's philosophy. Deliberately NOT persisted:
    // this is a live preview of the run that just happened, not a historical record, so it never
    // touches CameraHealthAggregator or bloats SharedPreferences with image bytes.
    private val _capturedThumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val capturedThumbnails: StateFlow<Map<String, Bitmap>> = _capturedThumbnails.asStateFlow()

    private val _isColorCastCheckRunning = MutableStateFlow(false)
    val isColorCastCheckRunning: StateFlow<Boolean> = _isColorCastCheckRunning.asStateFlow()

    private val _colorCastResult = MutableStateFlow<CameraHealthUtils.ColorCastCheckResult?>(null)
    val colorCastResult: StateFlow<CameraHealthUtils.ColorCastCheckResult?> = _colorCastResult.asStateFlow()

    init {
        val context = getApplication<Application>()
        // Cheap, sync — a fact sheet is available immediately without a test run.
        _factSheet.value = CameraHealthUtils.readCameraFactSheet(context)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val loadedHistory = CameraHealthAggregator.loadCameraHealthHistory(context)
                _history.value = loadedHistory
                _latestResult.value = loadedHistory.lastOrNull()
            }
        }
    }

    fun setCsvDialogVisible(visible: Boolean) {
        _showCsvDialog.value = visible
    }

    /**
     * Runs the fact sheet + liveness check. In [viewModelScope], not the caller's
     * `rememberCoroutineScope` — an interstitial ad shown before this action can recreate the
     * hosting Activity mid-test, which would cancel a composition-scoped coroutine.
     */
    fun runHealthCheck(onComplete: (CameraHealthUtils.CameraHealthResult) -> Unit = {}) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _isCheckRunning.value = true
            AnalyticsUtils.logEvent(AnalyticsEvent.CameraHealthCheckStarted)
            try {
                val sheet = withContext(Dispatchers.IO) {
                    CameraHealthUtils.readCameraFactSheet(context)
                }
                _factSheet.value = sheet
                _capturedThumbnails.value = emptyMap()

                val liveness = CameraHealthUtils.runCameraLivenessCheck(context) { cameraId, jpegBytes ->
                    val bitmap = try {
                        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                    } catch (e: Exception) {
                        ErrorHandler.handleError(e, context = "CameraHealthViewModel.decodeThumbnail")
                        null
                    }
                    if (bitmap != null) {
                        _capturedThumbnails.value = _capturedThumbnails.value + (cameraId to bitmap)
                    }
                }
                val result = CameraHealthUtils.CameraHealthResult(factSheet = sheet, liveness = liveness)

                withContext(Dispatchers.IO) {
                    CameraHealthAggregator.saveCameraHealthResult(context, result)
                }
                CameraHealthUtils.logMultiCamTelemetry(context, sheet)

                _latestResult.value = result
                _history.value = _history.value + result

                AnalyticsUtils.logEvent(
                    AnalyticsEvent.CameraHealthCheckCompleted,
                    mapOf(
                        "camera_count" to sheet.cameraCount,
                        "all_responded" to result.allLensesResponded,
                    ),
                )
                onComplete(result)
            } catch (e: Exception) {
                ErrorHandler.handleError(e, context = "CameraHealthViewModel.runHealthCheck")
            } finally {
                _isCheckRunning.value = false
            }
        }
    }

    /**
     * "My photos look black and white" guided check. Reuses the same open/capture/close harness
     * as [runHealthCheck] — takes the first captured frame (any camera) rather than opening a
     * second camera session, since this check only needs one sample photo, not per-lens results.
     */
    fun runColorCastCheck() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _isColorCastCheckRunning.value = true
            try {
                var capturedJpeg: ByteArray? = null
                CameraHealthUtils.runCameraLivenessCheck(context) { _, jpegBytes ->
                    if (capturedJpeg == null) capturedJpeg = jpegBytes
                }
                val result = CameraHealthUtils.buildColorCastCheckResult(context, capturedJpeg)
                _colorCastResult.value = result
                AnalyticsUtils.logEvent(
                    AnalyticsEvent.CameraColorCastCheckRun,
                    mapOf(
                        "grayscale_accessibility_on" to result.grayscaleAccessibilityOn,
                        "battery_saver_on" to result.batterySaverOn,
                        "capture_looks_monochrome" to (result.capturedLooksMonochrome?.toString() ?: "unknown"),
                    ),
                )
            } catch (e: Exception) {
                ErrorHandler.handleError(e, context = "CameraHealthViewModel.runColorCastCheck")
            } finally {
                _isColorCastCheckRunning.value = false
            }
        }
    }
}
