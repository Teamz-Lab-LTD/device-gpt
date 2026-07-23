package com.teamz.lab.debugger.ui

import android.app.Application
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
 * ViewModel for the Camera tab (fact sheet + liveness check + screen pixel test).
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

    private val _screenPixelHistory =
        MutableStateFlow<List<CameraHealthUtils.ScreenPixelResult>>(emptyList())
    val screenPixelHistory: StateFlow<List<CameraHealthUtils.ScreenPixelResult>> =
        _screenPixelHistory.asStateFlow()

    private val _showCsvDialog = MutableStateFlow(false)
    val showCsvDialog: StateFlow<Boolean> = _showCsvDialog.asStateFlow()

    init {
        val context = getApplication<Application>()
        // Cheap, sync — a fact sheet is available immediately without a test run.
        _factSheet.value = CameraHealthUtils.readCameraFactSheet(context)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val loadedHistory = CameraHealthAggregator.loadCameraHealthHistory(context)
                val loadedScreenHistory = CameraHealthAggregator.loadScreenPixelHistory(context)
                _history.value = loadedHistory
                _latestResult.value = loadedHistory.lastOrNull()
                _screenPixelHistory.value = loadedScreenHistory
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

                val liveness = CameraHealthUtils.runCameraLivenessCheck(context)
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

    fun recordScreenPixelResult(userReportedIssue: Boolean, colorShown: String?) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val result = CameraHealthUtils.ScreenPixelResult(
                userReportedIssue = userReportedIssue,
                colorShownWhenReported = colorShown,
            )
            withContext(Dispatchers.IO) {
                CameraHealthAggregator.saveScreenPixelResult(context, result)
            }
            _screenPixelHistory.value = _screenPixelHistory.value + result
            AnalyticsUtils.logEvent(
                AnalyticsEvent.ScreenPixelTestCompleted,
                mapOf("user_reported_issue" to userReportedIssue),
            )
        }
    }
}
