package com.teamz.lab.debugger.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import com.teamz.lab.debugger.utils.createGpuInfoSurfaceView
import com.teamz.lab.debugger.utils.string
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.getFPS
import com.teamz.lab.debugger.utils.getFrameDropRate
import com.teamz.lab.debugger.utils.HealthScoreUtils
import com.teamz.lab.debugger.utils.InfoTextLocalizer
import com.teamz.lab.debugger.utils.handleError
import com.teamz.lab.debugger.utils.FpsDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AnalyticsEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp

/**
 * Show first line of content for free (the headline/score), then premium teaser.
 * ALWAYS gates regardless of content length — these are premium security sections.
 * Note: Analytics is NOT logged here (called inside remember = would spam on recomposition).
 * Analytics is logged in the UI when user expands the section.
 */
private fun truncateWithTeaser(content: String, premiumDetail: String): String {
    if (content.isBlank()) return content
    val lines = content.lines()
    // Always show just the first line (score/headline) — the rest is premium
    val freeLine = lines.first()
    return "$freeLine\n\n⭐ Unlock Premium to see $premiumDetail"
}

/** Check if content was truncated by our teaser */
private fun isTeasedContent(content: String): Boolean {
    return content.contains("⭐ Unlock Premium to see")
}

@Composable
fun DeviceInfoSection(
    activity: Activity,
    onShareClick: (String) -> Unit,
    onAIClick: (() -> Unit)? = null,
    onItemAIClick: ((String, String) -> Unit)? = null,
    onPremiumGateClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val loadingText = context.string(R.string.loading)
    
    // Use ViewModel scoped to activity (not composable) so it persists across Crossfade transitions
    // This ensures state updates are visible even when Crossfade recreates the composable
    val viewModel: DeviceInfoViewModel = if (activity is ComponentActivity) {
        viewModel(viewModelStoreOwner = activity)
    } else {
        viewModel() // Fallback to default scope
    }
    
    // Collect state - this will trigger recomposition when state changes
    val state by viewModel.state.collectAsState()
    
    // Track FPS for caching (must be outside LaunchedEffect)
    var lastFps by remember { mutableStateOf(0) }
    var showGpuSurface by remember { mutableStateOf(true) }
    
    // Throttle FPS updates to prevent recomposition loop (only update every 500ms)
    var lastFpsUpdateTime by remember { mutableStateOf(0L) }
    var lastFrameRate by remember { mutableStateOf("") }
    var lastFrameDropData by remember { mutableStateOf("") }
    
    // Pre-compile Regex outside callbacks to prevent ANR (Regex compilation blocks main thread)
    // Regex compilation involves native code that can block, so we compile once and reuse
    val dropRateRegex = remember { Regex("([\\d.]+)%") }

    // GPU info surface (required for OpenGL context)
    var gpuSurfaceView by remember { mutableStateOf<android.opengl.GLSurfaceView?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Properly clean up GLSurfaceView to prevent ANR during hardware renderer destruction
    DisposableEffect(Unit) {
        onDispose {
            // Clean up GLSurfaceView asynchronously to prevent blocking hardware renderer destruction
            gpuSurfaceView?.let { view ->
                coroutineScope.launch(Dispatchers.Main) {
                    try {
                        // Stop rendering and release resources
                        view.onPause()
                        view.queueEvent {
                            // Release OpenGL resources on render thread
                        }
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                }
            }
        }
    }
    
    if (showGpuSurface) {
        AndroidView(factory = { ctx ->
            createGpuInfoSurfaceView(ctx) { info ->
                viewModel.updateGpuDetails(info)
                showGpuSurface = false
            }.apply {
                gpuSurfaceView = this
                layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
                requestRender()
            }
        })
    }

    // Load device info using ViewModel (batched loading with progressive delays)
    LaunchedEffect(Unit) {
        viewModel.loadDeviceInfo(context)
    }

    // Handle FPS data (main thread callbacks) - throttled to prevent recomposition loop
    // Use DisposableEffect to properly clean up callbacks when composable is disposed
    DisposableEffect(Unit) {
        var isActive = true
        
        // Store cleanup functions for FPS and frame drop callbacks
        val cleanupFps = getFPS { fps ->
            // Check if composable is still active before updating state
            if (!isActive) return@getFPS
            
            val fpsText = "$fps FPS"
            lastFps = fps
            lastFrameRate = fpsText
            
            // Throttle updates: only update ViewModel every 500ms
            val now = System.currentTimeMillis()
            if (now - lastFpsUpdateTime > 500) {
                val currentState = viewModel.state.value
                // Use lastFrameDropData (cached) instead of currentState.frameDropData
                // This ensures we preserve frameDropData even if it was set before frameRate
                val frameDropToUse = if (lastFrameDropData.isNotEmpty()) lastFrameDropData else currentState.frameDropData
                // Only update if value changed
                if (fpsText != currentState.frameRate) {
                    // android.util.Log.d("DeviceInfoSection", "📊 getFPS callback: updating frameRate='$fpsText', frameDropData='${frameDropToUse.take(50)}...'") // Disabled: Too verbose
                    viewModel.updateFpsInfo(fpsText, frameDropToUse)
                }
                lastFpsUpdateTime = now
            }
            
            // Cache FPS immediately (drop rate will be added when available)
            FpsDataCache.saveFpsData(context, fps, 0.0, "FPS: $fps")
        }
        
        val cleanupFrameDrop = getFrameDropRate { frameDropData ->
            // Check if composable is still active before updating state
            if (!isActive) return@getFrameDropRate
            
            lastFrameDropData = frameDropData
            
            // Extract and cache frame drop rate along with FPS
            // Use pre-compiled Regex to prevent ANR (Regex compilation blocks main thread)
            val dropRateMatch = dropRateRegex.find(frameDropData)
            val dropRate = dropRateMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            
            // Throttle updates: only update ViewModel every 500ms
            val now = System.currentTimeMillis()
            if (now - lastFpsUpdateTime > 500) {
                val currentState = viewModel.state.value
                // Use lastFrameRate (cached) instead of currentState.frameRate
                // This ensures we preserve frameRate even if it was set before frameDropData
                val frameRateToUse = if (lastFrameRate.isNotEmpty()) lastFrameRate else currentState.frameRate
                // Only update if value changed
                if (frameDropData != currentState.frameDropData) {
                    // android.util.Log.d("DeviceInfoSection", "📊 getFrameDropRate callback: updating frameRate='$frameRateToUse', frameDropData='${frameDropData.take(50)}...'") // Disabled: Too verbose
                    viewModel.updateFpsInfo(frameRateToUse, frameDropData)
                }
                lastFpsUpdateTime = now
            }
            
            // Cache both FPS and drop rate for future leaderboard uploads
            FpsDataCache.saveFpsData(context, lastFps, dropRate, "FPS: $lastFps • Drop Rate: ${String.format(java.util.Locale.getDefault(), "%.1f", dropRate)}%")
        }
        
        onDispose {
            // Mark as inactive when composable is disposed to prevent state updates
            isActive = false
            // Unregister Choreographer callbacks to prevent LeftCompositionCancellationException
            cleanupFps()
            cleanupFrameDrop()
        }
    }


    // Build device info list - recompute whenever state changes
    // Using remember with state values as keys ensures recomputation when state actually changes
    // Since state is from collectAsState(), we recompose when it changes, so we can compute directly
    val deviceInfo = remember(
        state.isFullyLoaded,
        state.deviceDetails,
        state.cpuDetails,
        state.gpuDetails,
        state.batteryInfo,
        state.gpsInfo,
        state.telephonyInfo,
        state.displayInfo,
        state.fontInfo,
        state.sensorList,
        state.recentLogs,
        state.dateTimeInfo,
        state.cameraInfo,
        state.fileFormat,
        state.thermalStatus,
        state.memoryStorage,
        state.securityInfo,
        state.frameRate,
        state.frameDropData,
        state.rootStatus,
        state.usbDebugging,
        state.aiInferenceSupport,
        state.thermalZoneInfo,
        state.spoofingStatus,
        state.hiddenAppsStatus,
        state.voiceCloneRisk,
        state.hackability,
        state.faceUnlockTrust,
        state.adTracking,
        state.isDeviceBeingMonitored
    ) {
        val showLoading = !state.isFullyLoaded
        // android.util.Log.d("DeviceInfoSection", "🔄 Recomputing deviceInfo - isFullyLoaded: ${state.isFullyLoaded}, items with data: ${listOf(state.deviceDetails, state.cpuDetails, state.gpuDetails).count { it.isNotEmpty() }}") // Disabled: Too verbose
        listOf(
            Triple("Device Specifications", R.string.info_sec_device_specs, if (showLoading && state.deviceDetails.isEmpty()) loadingText else state.deviceDetails),
            Triple("Privacy Exposure Check", R.string.info_sec_privacy_exposure, if (showLoading && state.isDeviceBeingMonitored.isEmpty()) loadingText
                else if (!com.teamz.lab.debugger.utils.RevenueCatManager.isPremium()) truncateWithTeaser(state.isDeviceBeingMonitored, "full privacy exposure details & what to do")
                else state.isDeviceBeingMonitored),
            Triple("Processor & Performance", R.string.info_sec_cpu, if (showLoading && state.cpuDetails.isEmpty()) loadingText else state.cpuDetails),
            Triple("Graphics & GPU Information", R.string.info_sec_gpu, if (showLoading && state.gpuDetails.isEmpty()) loadingText else state.gpuDetails),
            Triple("Battery & Charging Info", R.string.info_sec_battery, if (showLoading && state.batteryInfo.isEmpty()) loadingText else state.batteryInfo),
            Triple("GPS, Location & Navigation", R.string.info_sec_gps, if (showLoading && state.gpsInfo.isEmpty()) loadingText else state.gpsInfo),
            Triple("SIM & Mobile Network Info", R.string.info_sec_sim, if (showLoading && state.telephonyInfo.isEmpty()) loadingText else state.telephonyInfo),
            Triple("Screen & Display Settings", R.string.info_sec_display, if (showLoading && state.displayInfo.isEmpty()) loadingText else state.displayInfo),
            Triple("Text & Font Settings", R.string.info_sec_font, if (showLoading && state.fontInfo.isEmpty()) loadingText else state.fontInfo),
            Triple("Sensors Available on Device", R.string.info_sec_sensors, if (showLoading && state.sensorList.isEmpty()) loadingText else state.sensorList),
            Triple("Recent System Logs (Last 10 Entries)", R.string.info_sec_logs, if (showLoading && state.recentLogs.isEmpty()) loadingText else state.recentLogs),
            Triple("Date, Time & Auto Sync", R.string.info_sec_datetime, if (showLoading && state.dateTimeInfo.isEmpty()) loadingText else state.dateTimeInfo),
            Triple("Camera, Mic, Speaker & Flashlight Status", R.string.info_sec_camera_audio, if (showLoading && state.cameraInfo.isEmpty()) loadingText else state.cameraInfo),
            Triple("Supported Media Formats", R.string.info_sec_media, if (showLoading && state.fileFormat.isEmpty()) loadingText else state.fileFormat),
            Triple("Temperature & Cooling Status", R.string.info_sec_temp, if (showLoading && state.thermalStatus.isEmpty()) loadingText else state.thermalStatus),
            Triple("Memory & Storage Details", R.string.info_sec_memory, if (showLoading && state.memoryStorage.isEmpty()) loadingText else state.memoryStorage),
            Triple("Security & Privacy Features", R.string.info_sec_security, if (showLoading && state.securityInfo.isEmpty()) loadingText else state.securityInfo),
            // FPS and Frame Drop update in real-time - show them immediately when available
            Triple("Real-time FPS (Frame Rate)", R.string.info_sec_fps, if (state.frameRate.isEmpty()) loadingText else state.frameRate),
            Triple("Graphics & Frame Drop Analysis", R.string.info_sec_frame_drop, if (state.frameDropData.isEmpty()) loadingText else state.frameDropData),
            Triple("Device Root & Superuser Status", R.string.info_sec_root, if (showLoading && state.rootStatus.isEmpty()) loadingText else state.rootStatus),
            Triple("Developer Options & USB Debugging", R.string.info_sec_usb_debug, if (showLoading && state.usbDebugging.isEmpty()) loadingText else state.usbDebugging),
            Triple("AI Inference & Neural Acceleration Support", R.string.info_sec_ai, if (showLoading && state.aiInferenceSupport.isEmpty()) loadingText else state.aiInferenceSupport),
            Triple("Heat Check: CPU, Battery, GPU Temps", R.string.info_sec_heat, if (showLoading && state.thermalZoneInfo.isEmpty()) loadingText else state.thermalZoneInfo),
            // Premium security sections: show first 3 lines free (real value), gate the rest
            Triple("Sensor Spoofing Detection", R.string.info_sec_spoof, if (showLoading && state.spoofingStatus.isEmpty()) loadingText
                else if (!com.teamz.lab.debugger.utils.RevenueCatManager.isPremium()) truncateWithTeaser(state.spoofingStatus, "full spoofing detection details & how to fix")
                else state.spoofingStatus),
            Triple("Hidden Apps & Services Check", R.string.info_sec_hidden, if (showLoading && state.hiddenAppsStatus.isEmpty()) loadingText
                else if (!com.teamz.lab.debugger.utils.RevenueCatManager.isPremium()) truncateWithTeaser(state.hiddenAppsStatus, "full hidden apps list & removal guide")
                else state.hiddenAppsStatus),
            // Was "AI Voice Clone Risk Check", with premium selling a "full voice clone
            // vulnerability analysis". The check read DeviceGPT's own mic permission; what is left
            // is one honest sentence, so there is nothing to put behind the paywall.
            Triple("Microphone Privacy", R.string.info_sec_mic_privacy, if (showLoading && state.voiceCloneRisk.isEmpty()) loadingText
                else state.voiceCloneRisk),
            Triple("How Hackable Is My Phone?", R.string.info_sec_hack, if (showLoading && state.hackability.isEmpty()) loadingText
                else state.hackability),
            Triple("Face Unlock Security Trust Level", R.string.info_sec_face, if (showLoading && state.faceUnlockTrust.isEmpty()) loadingText
                else state.faceUnlockTrust),
            Triple("Ad Tracking SDK Exposure", R.string.info_sec_adtrack, if (showLoading && state.adTracking.isEmpty()) loadingText
                else if (!com.teamz.lab.debugger.utils.RevenueCatManager.isPremium()) truncateWithTeaser(state.adTracking, "full list of SDKs tracking you & how to stop them")
                else state.adTracking),
        ).map { (id, titleRes, english) ->
            // The English title and text stay as they are for share, AI and analytics. Only what is
            // drawn goes through the app language. System log lines are the phone's own words.
            InfoRow(
                id = id,
                title = context.string(titleRes),
                content = InfoTextLocalizer.localize(context, english, exactOnly = titleRes == R.string.info_sec_logs),
                englishContent = english,
            )
        }
    }

    // Generate share content only when ALL data is fully loaded
    // FABs will be enabled only after isFullyLoaded is true
    val shareContent = if (state.isFullyLoaded) {
        // All data is loaded, generate share content with all items
        // English on purpose: this text is shared to other apps and handed to an AI.
        deviceInfo.joinToString("\n\n") { row ->
            "${row.id}\n${row.englishContent}"
        }
    } else {
        // Still loading - return loading text so FABs stay disabled
        loadingText
    }

    // Only call onShareClick when ALL data is fully loaded
    LaunchedEffect(state.isFullyLoaded, shareContent) {
        if (state.isFullyLoaded && shareContent != loadingText) {
            onShareClick(shareContent)
            
            // Save health score after scan (only once when all data is ready)
            try {
                val healthScore = HealthScoreUtils.calculateDailyHealthScore(context)
                HealthScoreUtils.saveHealthScore(context, healthScore)
                // Streak milestone notifications are now handled automatically by WorkManager
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    // Device info list - pass AI callbacks to show AI icons; Zero Trust Dashboard as header
    ExpandableInfoList(
        infoList = deviceInfo,
        activity = activity,
        onAIClick = if (state.isFullyLoaded && onAIClick != null) {
            {
                onAIClick()
                AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                    "source" to "device_info",
                    "section" to "device_infos"
                ))
            }
        } else null,
        onItemAIClick = if (state.isFullyLoaded) onItemAIClick else null,
        headerContent = {
            ZeroTrustDashboard(onAIClick = onItemAIClick)
        },
        onPremiumGateClick = onPremiumGateClick
    )
}



