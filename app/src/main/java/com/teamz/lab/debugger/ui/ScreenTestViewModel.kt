package com.teamz.lab.debugger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.CameraHealthAggregator
import com.teamz.lab.debugger.utils.CameraHealthUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Screen Test tab (colour/dead-pixel test, grid/scratch test, touch test).
 * Split out of [CameraHealthViewModel] on 2026-07-24 — the screen is not camera hardware, and
 * every check here is judged by the USER's eye/finger, never measured by the app. See
 * camera_health_utils.kt header for why that distinction matters for Play policy.
 */
class ScreenTestViewModel(application: Application) : AndroidViewModel(application) {

    private val _screenPixelHistory =
        MutableStateFlow<List<CameraHealthUtils.ScreenPixelResult>>(emptyList())
    val screenPixelHistory: StateFlow<List<CameraHealthUtils.ScreenPixelResult>> =
        _screenPixelHistory.asStateFlow()

    private val _lastTouchPointCount = MutableStateFlow<Int?>(null)
    val lastTouchPointCount: StateFlow<Int?> = _lastTouchPointCount.asStateFlow()

    init {
        val context = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _screenPixelHistory.value = CameraHealthAggregator.loadScreenPixelHistory(context)
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
            com.teamz.lab.debugger.utils.TestDoneCard.onTestCompleted(context)
        }
    }

    /**
     * Records the highest simultaneous touch-point count seen during one touch-test session.
     * Not persisted — a touch test is a live, right-now check (a stale "you had 5 fingers work
     * last week" reading has no diagnostic value the way a dead-camera or dead-pixel finding
     * does), so this only needs to survive the current screen, which [MutableStateFlow] does.
     */
    fun recordTouchTestResult(maxSimultaneousTouches: Int) {
        _lastTouchPointCount.value = maxSimultaneousTouches
        AnalyticsUtils.logEvent(
            AnalyticsEvent.ScreenTouchTestCompleted,
            mapOf("max_touch_points" to maxSimultaneousTouches),
        )
    }
}
