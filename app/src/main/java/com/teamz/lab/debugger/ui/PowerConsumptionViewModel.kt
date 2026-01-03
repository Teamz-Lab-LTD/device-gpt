package com.teamz.lab.debugger.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import com.teamz.lab.debugger.utils.AppPowerUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import com.teamz.lab.debugger.utils.ErrorHandler
import com.teamz.lab.debugger.utils.LeaderboardManager

/**
 * ViewModel for Power Consumption data
 * Persists across activity lifecycle changes (like when ads show/close)
 */
class PowerConsumptionViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "PowerStateDebug"
    }
    
    private val _powerData = MutableStateFlow<PowerConsumptionUtils.PowerConsumptionSummary?>(null)
    val powerData: StateFlow<PowerConsumptionUtils.PowerConsumptionSummary?> = _powerData.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _lastUpdateTime = MutableStateFlow(System.currentTimeMillis())
    val lastUpdateTime: StateFlow<Long> = _lastUpdateTime.asStateFlow()
    
    // Camera test state - persists across activity recreation
    private val _isCameraTestRunning = MutableStateFlow(false)
    val isCameraTestRunning: StateFlow<Boolean> = _isCameraTestRunning.asStateFlow()
    
    private val _cameraTestResult = MutableStateFlow<PowerConsumptionUtils.CameraPowerTestResult?>(null)
    val cameraTestResult: StateFlow<PowerConsumptionUtils.CameraPowerTestResult?> = _cameraTestResult.asStateFlow()
    
    private val _allCameraTestResults = MutableStateFlow<List<PowerConsumptionUtils.CameraPowerTestResult>>(emptyList())
    val allCameraTestResults: StateFlow<List<PowerConsumptionUtils.CameraPowerTestResult>> = _allCameraTestResults.asStateFlow()
    
    private val _currentTestNumber = MutableStateFlow(0)
    val currentTestNumber: StateFlow<Int> = _currentTestNumber.asStateFlow()
    
    private val _totalTests = MutableStateFlow(0)
    val totalTests: StateFlow<Int> = _totalTests.asStateFlow()
    
    // Display test state - persists across activity recreation
    private val _isDisplayTestRunning = MutableStateFlow(false)
    val isDisplayTestRunning: StateFlow<Boolean> = _isDisplayTestRunning.asStateFlow()
    
    private val _displayTestResults = MutableStateFlow<List<PowerConsumptionUtils.DisplayPowerPoint>?>(null)
    val displayTestResults: StateFlow<List<PowerConsumptionUtils.DisplayPowerPoint>?> = _displayTestResults.asStateFlow()
    
    // CPU test state - persists across activity recreation
    private val _isCpuTestRunning = MutableStateFlow(false)
    val isCpuTestRunning: StateFlow<Boolean> = _isCpuTestRunning.asStateFlow()
    
    private val _cpuTestResults = MutableStateFlow<List<PowerConsumptionUtils.CpuBenchPoint>?>(null)
    val cpuTestResults: StateFlow<List<PowerConsumptionUtils.CpuBenchPoint>?> = _cpuTestResults.asStateFlow()
    
    // Network test state - persists across activity recreation
    private val _isNetworkTestRunning = MutableStateFlow(false)
    val isNetworkTestRunning: StateFlow<Boolean> = _isNetworkTestRunning.asStateFlow()
    
    private val _networkTestResults = MutableStateFlow<List<PowerConsumptionUtils.NetworkSamplePoint>?>(null)
    val networkTestResults: StateFlow<List<PowerConsumptionUtils.NetworkSamplePoint>?> = _networkTestResults.asStateFlow()
    
    private val _networkCountdown = MutableStateFlow(0)
    val networkCountdown: StateFlow<Int> = _networkCountdown.asStateFlow()
    
    // CSV Dialog states - persist across activity recreation
    private val _showCameraCsvDialog = MutableStateFlow(false)
    val showCameraCsvDialog: StateFlow<Boolean> = _showCameraCsvDialog.asStateFlow()
    
    private val _showDisplayCsvDialog = MutableStateFlow(false)
    val showDisplayCsvDialog: StateFlow<Boolean> = _showDisplayCsvDialog.asStateFlow()
    
    private val _showCpuCsvDialog = MutableStateFlow(false)
    val showCpuCsvDialog: StateFlow<Boolean> = _showCpuCsvDialog.asStateFlow()
    
    private val _showNetworkCsvDialog = MutableStateFlow(false)
    val showNetworkCsvDialog: StateFlow<Boolean> = _showNetworkCsvDialog.asStateFlow()
    
    // App power monitoring state
    private val _isAppPowerMonitoring = MutableStateFlow(false)
    val isAppPowerMonitoring: StateFlow<Boolean> = _isAppPowerMonitoring.asStateFlow()
    
    private val _appPowerSnapshot = MutableStateFlow<PowerConsumptionUtils.AppPowerSnapshot?>(null)
    val appPowerSnapshot: StateFlow<PowerConsumptionUtils.AppPowerSnapshot?> = _appPowerSnapshot.asStateFlow()
    
    private val _appPowerHistory = MutableStateFlow<List<PowerConsumptionUtils.AppPowerSnapshot>>(emptyList())
    val appPowerHistory: StateFlow<List<PowerConsumptionUtils.AppPowerSnapshot>> = _appPowerHistory.asStateFlow()
    
    private val _showAppPowerCsvDialog = MutableStateFlow(false)
    val showAppPowerCsvDialog: StateFlow<Boolean> = _showAppPowerCsvDialog.asStateFlow()
    
    private var appPowerMonitoringJob: kotlinx.coroutines.Job? = null
    
    // Scroll positions - persist across activity recreation
    private val _mainScrollPosition = MutableStateFlow(0)
    val mainScrollPosition: StateFlow<Int> = _mainScrollPosition.asStateFlow()
    
    private val _sectionScrollPosition = MutableStateFlow(0)
    val sectionScrollPosition: StateFlow<Int> = _sectionScrollPosition.asStateFlow()
    
    private val _lazyListFirstVisibleItemIndex = MutableStateFlow(0)
    val lazyListFirstVisibleItemIndex: StateFlow<Int> = _lazyListFirstVisibleItemIndex.asStateFlow()
    
    private val _lazyListFirstVisibleItemScrollOffset = MutableStateFlow(0)
    val lazyListFirstVisibleItemScrollOffset: StateFlow<Int> = _lazyListFirstVisibleItemScrollOffset.asStateFlow()
    
    private var isRefreshing = false
    
    init {
        android.util.Log.d(TAG, "ViewModel INIT - Creating new ViewModel instance (hashCode: ${hashCode()})")
        
        // Load saved test results from disk
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Load camera test results
                val savedCameraResults = PowerConsumptionAggregator.loadCameraTestResults(getApplication())
                _allCameraTestResults.value = savedCameraResults
                android.util.Log.d(TAG, "ViewModel INIT - Loaded ${savedCameraResults.size} saved camera test results")
                
                // Load display test results
                val savedDisplayResults = PowerConsumptionAggregator.loadDisplayTestResults(getApplication())
                _displayTestResults.value = savedDisplayResults
                android.util.Log.d(TAG, "ViewModel INIT - Loaded ${savedDisplayResults?.size ?: 0} saved display test results")
                
                // Load CPU test results
                val savedCpuResults = PowerConsumptionAggregator.loadCpuTestResults(getApplication())
                _cpuTestResults.value = savedCpuResults
                android.util.Log.d(TAG, "ViewModel INIT - Loaded ${savedCpuResults?.size ?: 0} saved CPU test results")
                
                // Load network test results
                val savedNetworkResults = PowerConsumptionAggregator.loadNetworkTestResults(getApplication())
                _networkTestResults.value = savedNetworkResults
                android.util.Log.d(TAG, "ViewModel INIT - Loaded ${savedNetworkResults?.size ?: 0} saved network test results")
                
                // Load app power history
                val savedAppPowerHistory = PowerConsumptionAggregator.loadAppPowerSnapshots(getApplication())
                _appPowerHistory.value = savedAppPowerHistory
                android.util.Log.d(TAG, "ViewModel INIT - Loaded ${savedAppPowerHistory.size} saved app power snapshots")
            }
        }
        
        // Load initial data if not already loaded
        if (_powerData.value == null) {
            loadInitialData()
        }
        // Start periodic refresh
        startPeriodicRefresh()
        
        android.util.Log.d(TAG, "ViewModel INIT - Initial state: isTestRunning=${_isCameraTestRunning.value}, testCount=${_allCameraTestResults.value.size}")
    }
    
    fun setCameraTestRunning(isRunning: Boolean) {
        val oldValue = _isCameraTestRunning.value
        _isCameraTestRunning.value = isRunning
        android.util.Log.d(TAG, "ViewModel setCameraTestRunning - Changed: $oldValue -> $isRunning (hashCode: ${hashCode()})")
    }
    
    fun setCameraTestResult(result: PowerConsumptionUtils.CameraPowerTestResult?) {
        _cameraTestResult.value = result
        android.util.Log.d(TAG, "ViewModel setCameraTestResult - Result set: ${result != null} (hashCode: ${hashCode()})")
    }
    
    fun addCameraTestResult(result: PowerConsumptionUtils.CameraPowerTestResult) {
        val oldCount = _allCameraTestResults.value.size
        val updated = _allCameraTestResults.value + result
        _allCameraTestResults.value = updated
        android.util.Log.d(TAG, "ViewModel addCameraTestResult - Count: $oldCount -> ${updated.size} (hashCode: ${hashCode()})")
        // Save immediately to prevent data loss
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                PowerConsumptionAggregator.saveCameraTestResults(getApplication(), updated)
                android.util.Log.d(TAG, "ViewModel addCameraTestResult - Saved to disk: ${updated.size} results")
            }
        }
    }
    
    fun setTestProgress(current: Int, total: Int) {
        _currentTestNumber.value = current
        _totalTests.value = total
        android.util.Log.d(TAG, "ViewModel setTestProgress - Progress: $current/$total (hashCode: ${hashCode()})")
    }
    
    fun setDisplayTestRunning(isRunning: Boolean) {
        _isDisplayTestRunning.value = isRunning
        android.util.Log.d(TAG, "ViewModel setDisplayTestRunning - Changed to: $isRunning (hashCode: ${hashCode()})")
    }
    
    fun setDisplayTestResults(results: List<PowerConsumptionUtils.DisplayPowerPoint>?) {
        _displayTestResults.value = results
        android.util.Log.d(TAG, "ViewModel setDisplayTestResults - Results set: ${results?.size ?: 0} points (hashCode: ${hashCode()})")
        // Save immediately to prevent data loss
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                PowerConsumptionAggregator.saveDisplayTestResults(getApplication(), results)
                android.util.Log.d(TAG, "ViewModel setDisplayTestResults - Saved to disk: ${results?.size ?: 0} results")
            }
            // Upload to leaderboard after display test completes (if results exist)
            if (results != null && results.isNotEmpty()) {
                com.teamz.lab.debugger.utils.LeaderboardDataUpload.uploadAfterDisplayTest(getApplication())
            }
        }
    }
    
    fun setCpuTestRunning(isRunning: Boolean) {
        _isCpuTestRunning.value = isRunning
        android.util.Log.d(TAG, "ViewModel setCpuTestRunning - Changed to: $isRunning (hashCode: ${hashCode()})")
    }
    
    fun setCpuTestResults(results: List<PowerConsumptionUtils.CpuBenchPoint>?) {
        _cpuTestResults.value = results
        android.util.Log.d(TAG, "ViewModel setCpuTestResults - Results set: ${results?.size ?: 0} points (hashCode: ${hashCode()})")
        // Save immediately to prevent data loss
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                PowerConsumptionAggregator.saveCpuTestResults(getApplication(), results)
                android.util.Log.d(TAG, "ViewModel setCpuTestResults - Saved to disk: ${results?.size ?: 0} results")
            }
            // Upload to leaderboard after CPU test completes (if results exist)
            if (results != null && results.isNotEmpty()) {
                com.teamz.lab.debugger.utils.LeaderboardDataUpload.uploadAfterCpuTest(getApplication())
            }
        }
    }
    
    fun setNetworkTestRunning(isRunning: Boolean) {
        _isNetworkTestRunning.value = isRunning
        android.util.Log.d(TAG, "ViewModel setNetworkTestRunning - Changed to: $isRunning (hashCode: ${hashCode()})")
    }
    
    fun setNetworkTestResults(results: List<PowerConsumptionUtils.NetworkSamplePoint>?) {
        _networkTestResults.value = results
        android.util.Log.d(TAG, "ViewModel setNetworkTestResults - Results set: ${results?.size ?: 0} points (hashCode: ${hashCode()})")
        // Save immediately to prevent data loss
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                PowerConsumptionAggregator.saveNetworkTestResults(getApplication(), results)
                android.util.Log.d(TAG, "ViewModel setNetworkTestResults - Saved to disk: ${results?.size ?: 0} results")
            }
        }
    }
    
    fun setNetworkCountdown(countdown: Int) {
        _networkCountdown.value = countdown
    }
    
    // CSV Dialog state management - persists across activity recreation
    fun setCameraCsvDialogVisible(visible: Boolean) {
        _showCameraCsvDialog.value = visible
        android.util.Log.d(TAG, "ViewModel setCameraCsvDialogVisible - Changed to: $visible (hashCode: ${hashCode()})")
    }
    
    fun setDisplayCsvDialogVisible(visible: Boolean) {
        _showDisplayCsvDialog.value = visible
        android.util.Log.d(TAG, "ViewModel setDisplayCsvDialogVisible - Changed to: $visible (hashCode: ${hashCode()})")
    }
    
    fun setCpuCsvDialogVisible(visible: Boolean) {
        _showCpuCsvDialog.value = visible
        android.util.Log.d(TAG, "ViewModel setCpuCsvDialogVisible - Changed to: $visible (hashCode: ${hashCode()})")
    }
    
    fun setNetworkCsvDialogVisible(visible: Boolean) {
        _showNetworkCsvDialog.value = visible
        android.util.Log.d(TAG, "ViewModel setNetworkCsvDialogVisible - Changed to: $visible (hashCode: ${hashCode()})")
    }
    
    fun startAppPowerMonitoring(context: Context) {
        if (_isAppPowerMonitoring.value) {
            return // Already monitoring
        }
        
        android.util.Log.d(TAG, "ViewModel startAppPowerMonitoring - Starting app power monitoring")
        _isAppPowerMonitoring.value = true
        
        // Track last upload time to throttle uploads (upload every 5 minutes max)
        var lastUploadTime = 0L
        val uploadIntervalMs = 5 * 60 * 1000L // 5 minutes
        
        appPowerMonitoringJob = viewModelScope.launch {
            try {
                AppPowerUtils.startAppPowerMonitoring(context, intervalMs = 5000).collect { snapshot ->
                    _appPowerSnapshot.value = snapshot
                    
                    // Save snapshot
                    withContext(Dispatchers.IO) {
                        PowerConsumptionAggregator.saveAppPowerSnapshot(context, snapshot)
                    }
                    
                    // Update history
                    val currentHistory = _appPowerHistory.value.toMutableList()
                    currentHistory.add(snapshot)
                    // Keep only last 100
                    _appPowerHistory.value = currentHistory.takeLast(100)
                    
                    // Upload app power data to leaderboard (throttled)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUploadTime >= uploadIntervalMs) {
                        withContext(Dispatchers.IO) {
                            try {
                                // Get own package name to filter it out
                                val ownPackageName = context.packageName
                                
                                // Upload top power-consuming apps (top 10), excluding our own app
                                val topApps = snapshot.apps
                                    .filter { it.packageName != ownPackageName } // Filter out our own app
                                    .sortedByDescending { it.powerConsumption }
                                    .take(10)
                                
                                var uploadedCount = 0
                                topApps.forEach { appData ->
                                    if (appData.powerConsumption > 0.0) {
                                        val success = LeaderboardManager.uploadAppPowerEntry(context, appData)
                                        if (success) uploadedCount++
                                    }
                                }
                                lastUploadTime = currentTime
                                android.util.Log.d(TAG, "Uploaded ${topApps.size} app power entries to leaderboard (excluding own app)")
                                // Log analytics
                                if (uploadedCount > 0) {
                                    com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                                        com.teamz.lab.debugger.utils.AnalyticsEvent.AppPowerDataUploaded,
                                        mapOf("uploaded_count" to uploadedCount, "source" to "auto_upload")
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(TAG, "Failed to upload app power data", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "ViewModel startAppPowerMonitoring - Error: ${e.message}", e)
                ErrorHandler.handleError(e, context = "PowerConsumptionViewModel.startAppPowerMonitoring")
                _isAppPowerMonitoring.value = false
            }
        }
    }
    
    fun stopAppPowerMonitoring() {
        android.util.Log.d(TAG, "ViewModel stopAppPowerMonitoring - Stopping app power monitoring")
        
        // Upload final batch before stopping
        val snapshot = _appPowerSnapshot.value
        if (snapshot != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val context = getApplication<android.app.Application>()
                    val ownPackageName = context.packageName
                    
                    // Filter out our own app before uploading
                    val topApps = snapshot.apps
                        .filter { it.packageName != ownPackageName } // Filter out our own app
                        .sortedByDescending { it.powerConsumption }
                        .take(10)
                    
                    var uploadedCount = 0
                    topApps.forEach { appData ->
                        if (appData.powerConsumption > 0.0) {
                            val success = LeaderboardManager.uploadAppPowerEntry(context, appData)
                            if (success) uploadedCount++
                        }
                    }
                    android.util.Log.d(TAG, "Uploaded final batch: ${topApps.size} app power entries (excluding own app)")
                    // Log analytics
                    if (uploadedCount > 0) {
                        com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                            com.teamz.lab.debugger.utils.AnalyticsEvent.AppPowerDataUploaded,
                            mapOf("uploaded_count" to uploadedCount, "source" to "stop_monitoring")
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to upload final batch", e)
                }
            }
        }
        
        appPowerMonitoringJob?.cancel()
        appPowerMonitoringJob = null
        _isAppPowerMonitoring.value = false
    }
    
    /**
     * Manually upload app power data to leaderboard
     */
    fun uploadAppPowerDataToLeaderboard(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = _appPowerSnapshot.value
                if (snapshot == null) {
                    android.util.Log.w(TAG, "No app power data to upload")
                    return@launch
                }
                
                val ownPackageName = context.packageName
                
                // Filter out our own app before uploading
                val topApps = snapshot.apps
                    .filter { it.packageName != ownPackageName } // Filter out our own app
                    .sortedByDescending { it.powerConsumption }
                    .take(10)
                
                var uploadedCount = 0
                topApps.forEach { appData ->
                    if (appData.powerConsumption > 0.0) {
                        val success = LeaderboardManager.uploadAppPowerEntry(context, appData)
                        if (success) uploadedCount++
                    }
                }
                android.util.Log.d(TAG, "Manually uploaded $uploadedCount app power entries to leaderboard (excluding own app)")
                // Log analytics
                if (uploadedCount > 0) {
                    com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                        com.teamz.lab.debugger.utils.AnalyticsEvent.AppPowerDataUploaded,
                        mapOf("uploaded_count" to uploadedCount, "source" to "manual_upload")
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to manually upload app power data", e)
            }
        }
    }
    
    fun setAppPowerCsvDialogVisible(visible: Boolean) {
        _showAppPowerCsvDialog.value = visible
        android.util.Log.d(TAG, "ViewModel setAppPowerCsvDialogVisible - Changed to: $visible (hashCode: ${hashCode()})")
    }
    
    fun loadAppPowerHistory(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val history = PowerConsumptionAggregator.loadAppPowerSnapshots(context)
                _appPowerHistory.value = history
                android.util.Log.d(TAG, "ViewModel loadAppPowerHistory - Loaded ${history.size} snapshots")
            }
        }
    }
    
    fun saveMainScrollPosition(position: Int) {
        _mainScrollPosition.value = position
    }
    
    fun saveSectionScrollPosition(position: Int) {
        _sectionScrollPosition.value = position
    }
    
    fun saveLazyListScrollPosition(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        _lazyListFirstVisibleItemIndex.value = firstVisibleItemIndex
        _lazyListFirstVisibleItemScrollOffset.value = firstVisibleItemScrollOffset
    }
    
    /**
     * Start a single camera test - runs in viewModelScope which persists across activity recreation
     */
    fun startSingleTest(
        context: android.content.Context,
        previewSurface: android.view.Surface?,
        onComplete: (PowerConsumptionUtils.CameraPowerTestResult) -> Unit
    ) {
        android.util.Log.d(TAG, "ViewModel startSingleTest - Starting test (hashCode: ${hashCode()})")
        viewModelScope.launch {
            try {
                android.util.Log.d(TAG, "ViewModel startSingleTest - COROUTINE STARTED in viewModelScope")
                android.util.Log.d(TAG, "ViewModel startSingleTest - Setting isTestRunning to true")
                _isCameraTestRunning.value = true
                
                android.util.Log.d(TAG, "ViewModel startSingleTest - Logging analytics event")
                com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                    com.teamz.lab.debugger.utils.AnalyticsEvent.PowerExperimentStarted,
                    mapOf("experiment_type" to "camera", "test_type" to "single")
                )
                
                android.util.Log.d(TAG, "ViewModel startSingleTest - About to measure power consumption, previewSurface: ${previewSurface != null}")
                android.util.Log.d(TAG, "ViewModel startSingleTest - Context type: ${context::class.java.simpleName}, isActivity: ${context is android.app.Activity}")
                
                val result = try {
                    android.util.Log.d(TAG, "ViewModel startSingleTest - Entering withContext(Dispatchers.IO)")
                    val measuredResult = withContext(Dispatchers.IO) {
                        android.util.Log.d(TAG, "ViewModel startSingleTest - Inside Dispatchers.IO, calling measureSinglePhotoPowerConsumption")
                        try {
                            val res = PowerConsumptionUtils.measureSinglePhotoPowerConsumption(
                                context,
                                previewSurface
                            )
                            android.util.Log.d(TAG, "ViewModel startSingleTest - measureSinglePhotoPowerConsumption returned, result: ${res != null}")
                            res
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "ViewModel startSingleTest - Exception INSIDE measureSinglePhotoPowerConsumption: ${e.message}", e)
                            android.util.Log.e(TAG, "ViewModel startSingleTest - Exception stack trace:", e)
                            throw e
                        }
                    }
                    android.util.Log.d(TAG, "ViewModel startSingleTest - Exited withContext(Dispatchers.IO), result: ${measuredResult != null}")
                    measuredResult
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "ViewModel startSingleTest - ERROR in measureSinglePhotoPowerConsumption (outer catch): ${e.message}", e)
                    android.util.Log.e(TAG, "ViewModel startSingleTest - Exception type: ${e::class.java.name}", e)
                    ErrorHandler.handleError(e, context = "PowerConsumptionViewModel.startSingleTest-outer")
                    throw e
                }
                
                android.util.Log.d(TAG, "ViewModel startSingleTest - Test completed, result: ${true}, powerDifference: ${result.powerDifference}")
                _cameraTestResult.value = result
                
                // Add result to ViewModel (which saves automatically)
                android.util.Log.d(TAG, "ViewModel startSingleTest - Adding result to ViewModel")
                val updated = _allCameraTestResults.value + result
                _allCameraTestResults.value = updated
                android.util.Log.d(TAG, "ViewModel startSingleTest - Test result added, count: ${updated.size}")
                
                // Save immediately to prevent data loss
                withContext(Dispatchers.IO) {
                    PowerConsumptionAggregator.saveCameraTestResults(getApplication(), updated)
                    android.util.Log.d(TAG, "ViewModel startSingleTest - Saved to disk: ${updated.size} results")
                }
                
                // Upload to leaderboard after camera test completes
                com.teamz.lab.debugger.utils.LeaderboardDataUpload.uploadAfterCameraTest(getApplication())
                
                android.util.Log.d(TAG, "ViewModel startSingleTest - Setting isTestRunning to false")
                _isCameraTestRunning.value = false
                
                android.util.Log.d(TAG, "ViewModel startSingleTest - Calling onComplete callback on main thread")
                withContext(Dispatchers.Main) {
                    onComplete(result)
                }
                
                android.util.Log.d(TAG, "ViewModel startSingleTest - Single test completed successfully")
                com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                    com.teamz.lab.debugger.utils.AnalyticsEvent.PowerExperimentCompleted,
                    mapOf("experiment_type" to "camera", "test_type" to "single")
                )
                com.teamz.lab.debugger.utils.PowerAchievements.recordExperimentCompletion(context, "camera")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "ViewModel startSingleTest - ERROR in coroutine: ${e.message}", e)
                android.util.Log.e(TAG, "ViewModel startSingleTest - Exception type: ${e::class.java.name}", e)
                android.util.Log.e(TAG, "ViewModel startSingleTest - Exception cause: ${e.cause?.message}", e.cause)
                
                // Use global error handler
                ErrorHandler.handleError(e, context = "PowerConsumptionViewModel.startSingleTest")
                
                _isCameraTestRunning.value = false
                // Don't rethrow - just log and reset state to prevent hanging
                android.util.Log.e(TAG, "ViewModel startSingleTest - Test failed, state reset to false")
            }
        }
        android.util.Log.d(TAG, "ViewModel startSingleTest - viewModelScope.launch() called")
    }
    
    /**
     * Generic method to execute any test action in viewModelScope
     * This ensures all test actions persist across activity recreation
     */
    fun executeTestAction(
        actionName: String,
        action: suspend () -> Unit,
        onComplete: (() -> Unit)? = null
    ) {
        android.util.Log.d(TAG, "ViewModel executeTestAction - Starting: $actionName (hashCode: ${hashCode()})")
        viewModelScope.launch {
            try {
                android.util.Log.d(TAG, "ViewModel executeTestAction - COROUTINE STARTED in viewModelScope: $actionName")
                action()
                android.util.Log.d(TAG, "ViewModel executeTestAction - Action completed: $actionName")
                onComplete?.let {
                    withContext(Dispatchers.Main) {
                        it()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "ViewModel executeTestAction - ERROR in coroutine: $actionName - ${e.message}", e)
                
                // Use global error handler
                ErrorHandler.handleError(e, context = "PowerConsumptionViewModel.executeTestAction-$actionName")
            }
        }
        android.util.Log.d(TAG, "ViewModel executeTestAction - viewModelScope.launch() called: $actionName")
    }
    
    private fun loadInitialData() {
        android.util.Log.d(TAG, "ViewModel loadInitialData - Starting (hashCode: ${hashCode()})")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val data = withContext(Dispatchers.IO) {
                    PowerConsumptionUtils.getPowerConsumptionData(getApplication())
                }
                _powerData.value = data
                PowerConsumptionAggregator.updatePowerData(getApplication(), data)
                _lastUpdateTime.value = System.currentTimeMillis()
                android.util.Log.d(TAG, "ViewModel loadInitialData - Completed (hashCode: ${hashCode()})")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "ViewModel loadInitialData - Error: ${e.message}", e)
                ErrorHandler.handleError(e, context = "PowerConsumptionViewModel.loadInitialData")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(5000) // 5-second intervals
                if (!isRefreshing) {
                    refreshData()
                }
            }
        }
    }
    
    fun refreshData() {
        if (isRefreshing) return
        isRefreshing = true
        
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    PowerConsumptionUtils.getPowerConsumptionData(getApplication())
                }
                _powerData.value = data
                PowerConsumptionAggregator.updatePowerData(getApplication(), data)
                _lastUpdateTime.value = System.currentTimeMillis()
            } catch (e: Exception) {
                android.util.Log.e("PowerConsumptionViewModel", "Error refreshing data", e)
                ErrorHandler.handleError(e, context = "PowerConsumptionViewModel.refreshData")
            } finally {
                isRefreshing = false
            }
        }
    }
    
    // Get aggregated stats from aggregator
    val aggregatedStats = PowerConsumptionAggregator.aggregatedStatsFlow
    val powerHistory = PowerConsumptionAggregator.powerHistoryFlow
}

