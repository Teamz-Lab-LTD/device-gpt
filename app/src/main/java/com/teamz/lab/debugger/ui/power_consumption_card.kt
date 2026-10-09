package com.teamz.lab.debugger.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.TextureView
import android.view.Surface
import android.graphics.SurfaceTexture
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import com.teamz.lab.debugger.utils.PowerConsumptionAggregator
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.PowerEducation
import com.teamz.lab.debugger.utils.PowerAchievements
import com.teamz.lab.debugger.utils.ReviewPromptManager
import com.teamz.lab.debugger.utils.DeviceSleepTracker
import com.teamz.lab.debugger.utils.PowerConsumptionUtils.AppPowerData
import com.teamz.lab.debugger.ui.AIAssistantDialog
import com.teamz.lab.debugger.ui.PromptMode
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import com.teamz.lab.debugger.utils.PermissionManager
import com.teamz.lab.debugger.utils.string
import androidx.compose.ui.res.stringResource
import com.teamz.lab.debugger.utils.PowerStrings
import com.teamz.lab.debugger.R
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.ui.PowerConsumptionViewModel
import com.teamz.lab.debugger.utils.ErrorHandler

@Composable
fun PowerConsumptionCard(
    onItemAIClick: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    android.util.Log.d("PowerStateDebug", "PowerConsumptionCard - Composable recomposed, context: ${context.hashCode()}, activity: ${activity?.hashCode()}")
    
    // Use ViewModel to persist state across activity lifecycle changes (survives ad show/close)
    val viewModel: PowerConsumptionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    android.util.Log.d("PowerStateDebug", "PowerConsumptionCard - Composable recomposed, ViewModel hashCode: ${viewModel.hashCode()}")
    
    // Collect data from ViewModel (persists across activity recreation)
    val powerData by viewModel.powerData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lastUpdateTime by viewModel.lastUpdateTime.collectAsState()
    val aggregatedStats by viewModel.aggregatedStats.collectAsState()
    val powerHistory by viewModel.powerHistory.collectAsState()
    
    // Collect test state from ViewModel
    val isTestRunning by viewModel.isCameraTestRunning.collectAsState()
    val testResultsCount by viewModel.allCameraTestResults.collectAsState()
    android.util.Log.d("PowerStateDebug", "PowerConsumptionCard - State collected: isTestRunning=$isTestRunning, testResultsCount=${testResultsCount.size}")
    
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Preserve scroll states across activity recreation (for when ads show/close)
    // Get saved scroll position from ViewModel and restore it
    val savedMainScrollPosition by viewModel.mainScrollPosition.collectAsState()
    val mainScrollState = rememberScrollState(initial = savedMainScrollPosition)
    
    // Restore scroll position after activity recreation
    LaunchedEffect(Unit) {
        if (savedMainScrollPosition > 0 && mainScrollState.value == 0) {
            // Only restore if we have a saved position and current position is 0 (activity was recreated)
            kotlinx.coroutines.delay(100) // Wait for layout
            mainScrollState.scrollTo(savedMainScrollPosition)
        }
    }
    
    // Save scroll position to ViewModel when it changes (throttled to prevent ANR)
    // Use snapshotFlow with debounce to prevent LaunchedEffect from restarting on every scroll event
    LaunchedEffect(mainScrollState) {
        snapshotFlow { mainScrollState.value }
            .debounce(500) // Only process scroll changes every 500ms
            .collect { scrollValue ->
                if (scrollValue > 0) {
                    viewModel.saveMainScrollPosition(scrollValue)
                }
            }
    }
    
    // Ad management
    val shouldShowNativeAds = RemoteConfigUtils.shouldShowNativeAds()
    val powerAdCacheGen = NativeAdManager.cacheGeneration.intValue
    val nativeAds = remember(powerAdCacheGen) { NativeAdManager.nativeAds }
    val adLoader = activity?.let { rememberAdLoader(it) }
    
    // Track tab view on first load - use a key to prevent retriggering on activity resume
    var hasTrackedTabView by remember { mutableStateOf(false) }
    LaunchedEffect(hasTrackedTabView) {
        if (!hasTrackedTabView) {
        AnalyticsUtils.logEvent(AnalyticsEvent.PowerTabViewed)
        PowerAchievements.checkAchievements(context, powerData, aggregatedStats)
            // Preload full-screen video ads for better UX
            if (activity != null) {
                InterstitialAdManager.preloadAd(activity)
            }
            hasTrackedTabView = true
        }
    }
    
    // ViewModel handles all data loading and refreshing
    // No need for LaunchedEffect - ViewModel persists across activity recreation
    // Check achievements when data updates
    LaunchedEffect(powerData, aggregatedStats) {
        if (powerData != null && aggregatedStats != null) {
            val newlyUnlocked = PowerAchievements.checkAchievements(context, powerData, aggregatedStats)
                if (newlyUnlocked.isNotEmpty()) {
                    android.util.Log.d("PowerAchievements", "Unlocked: ${newlyUnlocked.map { it.title }}")
                }
        }
    }
    
    // Manual refresh function - uses ViewModel
    val refreshData: () -> Unit = {
            isRefreshing = true
        viewModel.refreshData()
        // Reset refreshing flag after a short delay
        coroutineScope.launch {
            delay(1000)
            isRefreshing = false
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryStd,
                        contentDescription = context.string(R.string.cd_power_consumption),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = context.string(R.string.power_consumption),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(
                    onClick = refreshData,
                    enabled = !isRefreshing
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = context.string(R.string.refresh),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Total Power Summary
            powerData?.let { data ->
                TotalPowerSummary(
                    totalPower = data.totalPower,
                    timestamp = data.timestamp,
                    onAIClick = onItemAIClick?.let { handler ->
                        {
                            val practicalInfo = PowerConsumptionAggregator.calculateBatteryPercentPerHour(data.totalPower, context)
                            val content = """
Total Power Consumption: ${"%.1f".format(data.totalPower)} W
${practicalInfo?.let { "Battery Drain Rate: $it" } ?: ""}
Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(data.timestamp))}

Power Level: ${when {
    data.totalPower < 2.0 -> "Low Usage - Great!"
    data.totalPower < 5.0 -> "Medium Usage - Normal"
    data.totalPower < 8.0 -> "High Usage - Battery Draining Fast"
    else -> "Very High Usage - Battery Draining Very Fast"
}}
                            """.trimIndent()
                            handler("Total Power Consumption", content)
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // App Power Monitor Section (High Priority - moved to top)
                AppPowerMonitorSection(
                    context = context, 
                    viewModel = viewModel, 
                    activity = activity,
                    onItemAIClick = onItemAIClick
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Native Ad 1 - After App Power Monitor (Policy: Adequate spacing from content)
                if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
                    val nativeAd1 = remember(powerAdCacheGen, "power_consumption_ad1") { NativeAdManager.getAdForPosition("power_consumption_ad1") }
                    if (nativeAd1 != null) {
                        // Logging handled by getAdForPosition (reduced)
                        AdMobNativeAdCard(nativeAd = nativeAd1)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Aggregated Statistics
                aggregatedStats?.let { stats ->
                    AggregatedStatsSection(
                        stats = stats,
                        onAIClick = onItemAIClick?.let { handler ->
                            {
                                val content = """
Power Statistics:
- Average: ${"%.2f".format(stats.averagePower)} W
- Peak: ${"%.2f".format(stats.peakPower)} W
- Minimum: ${"%.2f".format(stats.minPower)} W
- Total Samples: ${stats.totalSamples}
- Power Trend: ${stats.powerTrend.name}
                                """.trimIndent()
                                handler("Power Statistics", content)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Component List
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = context.string(R.string.component_breakdown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (onItemAIClick != null) {
                        IconButton(
                            onClick = {
                                val componentsText = data.components.sortedByDescending { it.powerConsumption }
                                    .joinToString("\n") { comp ->
                                        "${comp.component}: ${"%.2f".format(comp.powerConsumption / 1000.0)} W - ${comp.status}"
                                    }
                                val content = """
Component Power Breakdown:
$componentsText

Total Power: ${"%.2f".format(data.totalPower)} W
                                """.trimIndent()
                                onItemAIClick("Component Breakdown", content)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                                contentDescription = stringResource(R.string.pw_cd_ai_breakdown),
                                tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                // Track open dialog by component name to fix popup bug
                var openDialogComponentName by remember { mutableStateOf<String?>(null) }
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    data.components.sortedByDescending { it.powerConsumption }.forEach { component ->
                        ComponentPowerItem(
                            component = component,
                            isDialogOpen = openDialogComponentName == component.component,
                            onDialogOpen = { 
                                AnalyticsUtils.logEvent(AnalyticsEvent.PowerComponentInfoOpened, mapOf(
                                    "component" to component.component
                                ))
                                openDialogComponentName = component.component 
                            },
                            onDialogDismiss = { openDialogComponentName = null },
                            onAIClick = onItemAIClick?.let { handler ->
                                {
                                    val practicalInfo = PowerConsumptionAggregator.getPracticalPowerInfo(
                                        component.powerConsumption / 1000.0,
                                        context,
                                        component.component
                                    )
                                    val content = """
Component: ${component.component}
Power Consumption: ${"%.2f".format(component.powerConsumption / 1000.0)} W
Status: ${component.status}
${practicalInfo?.let { "Practical Info: $it" } ?: ""}
                                    """.trimIndent()
                                    handler(component.component, content)
                                }
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Native Ad 2 - After Component Breakdown (Policy: Adequate spacing between ads)
                if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
                    val nativeAd2 = remember(powerAdCacheGen, "power_consumption_ad2") { NativeAdManager.getAdForPosition("power_consumption_ad2") }
                    if (nativeAd2 != null) {
                        // Logging handled by getAdForPosition (reduced)
                        AdMobNativeAdCard(nativeAd = nativeAd2)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Camera Power Test Section
                CameraPowerTestSection(
                    context = context, 
                    viewModel = viewModel, 
                    activity = activity,
                    onItemAIClick = onItemAIClick
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Display Power Sweep Section
                DisplayPowerSweepSection(
                    context = context, 
                    viewModel = viewModel, 
                    activity = activity,
                    onItemAIClick = onItemAIClick
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Native Ad 3 - Between Test Sections (Policy: Reduced ad density - only show if no rewarded ad shown)
                // Only show this ad if rewarded ad was not shown to avoid too many ads
                if (shouldShowNativeAds && nativeAds.isNotEmpty() && !RemoteConfigUtils.shouldShowRewardedAds()) {
                    val nativeAd3 = remember(powerAdCacheGen, "power_consumption_ad3") { NativeAdManager.getAdForPosition("power_consumption_ad3") }
                    if (nativeAd3 != null) {
                        // Logging handled by getAdForPosition (reduced)
                        AdMobNativeAdCard(nativeAd = nativeAd3)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // CPU Microbench Section
                CpuMicrobenchSection(
                    context = context, 
                    viewModel = viewModel, 
                    activity = activity,
                    onItemAIClick = onItemAIClick
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Network RSSI Sampling Section
                NetworkRssiSamplingSection(
                    context = context, 
                    viewModel = viewModel, 
                    activity = activity,
                    onItemAIClick = onItemAIClick
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Device Sleep Tracker Section
                DeviceSleepTrackerSection(
                    context = context,
                    onItemAIClick = onItemAIClick
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Last Update Time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.last_updated, SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdateTime))),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            } ?: run {
                // Loading state - only show if ViewModel is actually loading
                if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loading_power_data),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // No data and not loading - show empty state or retry
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.pw_no_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalPowerSummary(
    totalPower: Double,
    timestamp: Long,
    onAIClick: (() -> Unit)? = null
) {
    val animatedPower by animateFloatAsState(
        targetValue = totalPower.toFloat(),
        animationSpec = tween(1000, easing = EaseOutCubic),
        label = "power_animation"
    )
    
    // Detect dark mode
    val isDarkMode = MaterialTheme.colorScheme.background == DesignSystemColors.Dark
    val backgroundColor = if (isDarkMode) DesignSystemColors.DarkII else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isDarkMode) DesignSystemColors.White else MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryTextColor = if (isDarkMode) DesignSystemColors.White.copy(alpha = 0.7f) else DesignSystemColors.Dark.copy(alpha = 0.7f)
    val tertiaryTextColor = if (isDarkMode) DesignSystemColors.White.copy(alpha = 0.6f) else DesignSystemColors.Dark.copy(alpha = 0.6f)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.pw_now_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                if (onAIClick != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = stringResource(R.string.pw_cd_ai_power),
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.pw_now_sub),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "%.1f".format(animatedPower),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.watts),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Show both technical (watts) and practical info (battery % per hour)
            val context = LocalContext.current
            val practicalInfo = remember(totalPower) {
                PowerConsumptionAggregator.calculateBatteryPercentPerHour(totalPower, context)
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.pw_watts_explain),
                    style = MaterialTheme.typography.bodySmall,
                    color = tertiaryTextColor,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                
                // Show practical info if available
                practicalInfo?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.pw_drains, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Power level indicator
            PowerLevelIndicator(power = totalPower)
        }
    }
}

@Composable
private fun PowerLevelIndicator(power: Double) {
    val powerLevel = when {
        power < 2.0 -> stringResource(R.string.pw_level_low) to Color(0xFF4CAF50) // Green
        power < 5.0 -> stringResource(R.string.pw_level_medium) to Color(0xFFFF9800) // Orange
        power < 8.0 -> stringResource(R.string.pw_level_high) to Color(0xFFFF5722) // Red
        else -> stringResource(R.string.pw_level_very_high) to Color(0xFFD32F2F) // Dark Red
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = powerLevel.second,
                    shape = RoundedCornerShape(6.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = powerLevel.first,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else DesignSystemColors.Dark,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ComponentPowerItem(
    component: PowerConsumptionUtils.ComponentPowerData,
    isDialogOpen: Boolean,
    onDialogOpen: () -> Unit,
    onDialogDismiss: () -> Unit,
    onAIClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    val animatedPower by animateFloatAsState(
        targetValue = component.powerConsumption.toFloat(),
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "component_power_animation"
    )
    
    // Track component info view
    LaunchedEffect(isDialogOpen) {
        if (isDialogOpen) {
            AnalyticsUtils.logEvent(
                AnalyticsEvent.PowerComponentExpanded,
                mapOf("component" to component.component)
            )
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDialogOpen() },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Component Icon
            Text(
                text = component.icon,
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
            
            // Component Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = PowerStrings.component(context, component.component),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = PowerStrings.status(context, component.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Power Consumption with practical info
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "%.1f".format(animatedPower),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(R.string.watts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Show practical info if available
                    val practicalInfo = PowerConsumptionAggregator.getPracticalPowerInfo(
                        component.powerConsumption / 1000.0,
                        context,
                        component.component
                    )
                    practicalInfo?.let {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 9.sp
                        )
                    }
                }
            }
            
            // AI icon and Info icon
            Spacer(modifier = Modifier.width(8.dp))
            if (onAIClick != null) {
                IconButton(
                    onClick = onAIClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                        contentDescription = stringResource(R.string.pw_cd_ai_component, PowerStrings.component(context, component.component)),
                        tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.tap_for_more_info),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
    
    // Info Dialog with non-technical explanation - only show if this component's dialog is open
    if (isDialogOpen) {
        ComponentInfoDialog(
            component = component,
            onDismiss = onDialogDismiss
        )
    }
}

@Composable
private fun SummaryStatCard(
    icon: String,
    title: String,
    value: String,
    description: String,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Top row: Icon + Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Visible,
                    lineHeight = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Description - full width, can wrap
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Visible,
                lineHeight = 11.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Value - full width, smaller font
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Visible,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun TestResultsFooter(
    count: Int,
    countLabel: String, // e.g., "tests", "measurements", "samples", "levels"
    onViewCsv: () -> Unit,
    context: android.content.Context,
    activity: Activity? = null // Optional activity for showing ads
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = PowerStrings.countLabel(context, count, countLabel),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = {
                    AnalyticsUtils.logEvent(AnalyticsEvent.PowerCsvDialogOpened, mapOf(
                        "test_type" to countLabel
                    ))
                    
                    // Show ad before CSV view - InterstitialAdManager handles all checks centrally:
                    // - Global enable/disable flag
                    // - Global throttling
                    // - Ad loading and showing
                    if (activity != null) {
                        InterstitialAdManager.showAdBeforeAction(
                            activity = activity,
                            actionName = "csv_view_$countLabel",
                            action = {
                                onViewCsv()
                            }
                        )
                    } else {
                        // No activity - proceed directly
                        onViewCsv()
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.wrapContentWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = context.string(R.string.view_csv),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SampleDataCard(
    icon: String,
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon - smaller size
            Text(
                text = icon,
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 10.dp, top = 2.dp)
            )
            
            // Content - allow full text to show
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title on first line - allow wrapping to show full text
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Visible,
                    lineHeight = 16.sp
                )
                
                // Value on second line with proper wrapping - smaller and not bold
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Visible,
                    lineHeight = 16.sp
                )
                
                // Subtitle on third line if present
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Visible,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponentInfoDialog(
    component: PowerConsumptionUtils.ComponentPowerData,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val education = PowerEducation.getEducationForComponent(context, component.component)
    
    // Check if permission is required
    val requiresPermission = component.status.contains("Permission required", ignoreCase = true) ||
                             component.status.contains("permission required", ignoreCase = true)
    
    // Map component to required permission(s) - using PermissionManager
    val requiredPermissions = remember(component.component) {
        PermissionManager.getRequiredPermissions(component.component)
    }
    
    // Permission launchers - using PermissionManager
    val singlePermissionLauncher = PermissionManager.rememberPermissionLauncher { granted ->
        // Use coroutine to check permission status after a small delay
        // This ensures the system has updated the permission status
        coroutineScope.launch {
            delay(300) // Small delay to allow system to update permission status
            
            val componentName = component.component.lowercase()
            val hasPermissionNow = when (componentName) {
                "camera" -> PermissionManager.hasCameraPermission(context)
                "audio" -> PermissionManager.hasAudioPermission(context)
                "gps", "location" -> PermissionManager.hasLocationPermission(context)
                "bluetooth" -> PermissionManager.hasBluetoothPermission(context)
                else -> {
                    // Check if any of the required permissions are granted
                    requiredPermissions.any { PermissionManager.hasPermission(context, it) }
                }
            }
            
            if (hasPermissionNow) {
                // Show component-specific success message
                val successMessage = when (componentName) {
                    "camera" -> context.string(R.string.pw_perm_camera_granted)
                    "audio" -> context.string(R.string.pw_perm_audio_granted)
                    "gps", "location" -> context.string(R.string.pw_perm_location_granted)
                    "bluetooth" -> context.string(R.string.pw_perm_bluetooth_granted)
                    else -> context.string(R.string.pw_perm_granted)
                }
                android.widget.Toast.makeText(
                    context,
                    successMessage,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                // Show component-specific denial message
                val denialMessage = when (componentName) {
                    "camera" -> context.string(R.string.pw_perm_camera_denied)
                    "audio" -> context.string(R.string.pw_perm_audio_denied)
                    "gps", "location" -> context.string(R.string.pw_perm_location_denied)
                    "bluetooth" -> context.string(R.string.pw_perm_bluetooth_denied)
                    else -> context.string(R.string.pw_perm_denied)
                }
                android.widget.Toast.makeText(
                    context,
                    denialMessage,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    val multiplePermissionLauncher = PermissionManager.rememberMultiplePermissionsLauncher { permissions ->
        // Use coroutine to check permission status after a small delay
        // This ensures the system has updated the permission status
        coroutineScope.launch {
            delay(300) // Small delay to allow system to update permission status
            
            val componentName = component.component.lowercase()
            val hasAllRequiredPermissions = when (componentName) {
                "gps", "location" -> PermissionManager.hasLocationPermission(context)
                "bluetooth" -> PermissionManager.hasBluetoothPermission(context)
                else -> {
                    // Check if all required permissions are granted
                    requiredPermissions.all { PermissionManager.hasPermission(context, it) }
                }
            }
            
            val grantedCount = permissions.values.count { it }
            val totalCount = permissions.size
            
            if (hasAllRequiredPermissions) {
                // Show component-specific success message
                val successMessage = when (componentName) {
                    "gps", "location" -> context.string(R.string.pw_perm_location_granted)
                    "bluetooth" -> context.string(R.string.pw_perms_bluetooth_granted)
                    else -> context.string(R.string.pw_perms_all_granted)
                }
                android.widget.Toast.makeText(
                    context,
                    successMessage,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else if (grantedCount > 0) {
                // Partial permissions granted
                val partialMessage = when (componentName) {
                    "bluetooth" -> context.string(R.string.pw_perms_bluetooth_some)
                    else -> context.string(R.string.pw_perms_some)
                }
                android.widget.Toast.makeText(
                    context,
                    partialMessage,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                // All permissions denied
                val denialMessage = when (componentName) {
                    "gps", "location" -> context.string(R.string.pw_perm_location_denied)
                    "bluetooth" -> context.string(R.string.pw_perms_bluetooth_denied)
                    else -> context.string(R.string.pw_perms_denied)
                }
                android.widget.Toast.makeText(
                    context,
                    denialMessage,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    // Handle permission request - using PermissionManager
    fun requestPermission() {
        if (requiredPermissions.isEmpty()) return
        
        val componentName = component.component.lowercase()
        
        // Check if permissions are already granted
        val alreadyGranted = when (componentName) {
            "camera" -> PermissionManager.hasCameraPermission(context)
            "audio" -> PermissionManager.hasAudioPermission(context)
            "gps", "location" -> PermissionManager.hasLocationPermission(context)
            "bluetooth" -> PermissionManager.hasBluetoothPermission(context)
            else -> {
                // Check if all required permissions are granted
                requiredPermissions.all { PermissionManager.hasPermission(context, it) }
            }
        }
        
        if (alreadyGranted) {
            val message = when (componentName) {
                "camera" -> context.string(R.string.pw_perm_camera_already)
                "audio" -> context.string(R.string.pw_perm_audio_already)
                "gps", "location" -> context.string(R.string.pw_perm_location_already)
                "bluetooth" -> context.string(R.string.pw_perms_bluetooth_already)
                else -> context.string(R.string.pw_perm_already)
            }
            android.widget.Toast.makeText(
                context,
                message,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Special handling for GPS - only request FINE_LOCATION (it's sufficient and includes coarse)
        if (componentName in listOf("gps", "location") && requiredPermissions.size > 1) {
            // For GPS, prioritize ACCESS_FINE_LOCATION (it's sufficient)
            val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
            if (!PermissionManager.hasPermission(context, fineLocationPermission)) {
                // Request fine location first
                PermissionManager.requestPermission(context, activity, fineLocationPermission, singlePermissionLauncher)
            } else {
                // Already has fine location, that's sufficient
                android.widget.Toast.makeText(
                    context,
                    context.string(R.string.pw_perm_location_already),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } else if (requiredPermissions.size > 1) {
            // For components with multiple permissions (like Bluetooth on Android 12+)
            // Filter to only request ungranted permissions
            val ungrantedPermissions = requiredPermissions.filter { 
                !PermissionManager.hasPermission(context, it) 
            }
            
            if (ungrantedPermissions.isEmpty()) {
                android.widget.Toast.makeText(
                    context,
                    context.string(R.string.pw_perms_all_already),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                PermissionManager.requestPermissions(context, ungrantedPermissions, multiplePermissionLauncher)
            }
        } else {
            // Single permission request
            val permission = requiredPermissions.first()
            if (PermissionManager.hasPermission(context, permission)) {
                val message = when (componentName) {
                    "camera" -> context.string(R.string.pw_perm_camera_already)
                    "audio" -> context.string(R.string.pw_perm_audio_already)
                    else -> context.string(R.string.pw_perm_already)
                }
                android.widget.Toast.makeText(
                    context,
                    message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                PermissionManager.requestPermission(context, activity, permission, singlePermissionLauncher)
            }
        }
    }
    
    // Convert technical details to user-friendly language
    val userFriendlyInfo = remember(component) {
        buildString {
            appendLine("${component.icon} ${PowerStrings.component(context, component.component)}")
            appendLine()
            
            // Power consumption in simple terms
            val powerW = component.powerConsumption / 1000.0
            when {
                powerW < 0.5 -> appendLine(context.string(R.string.pw_info_low))
                powerW < 1.5 -> appendLine(context.string(R.string.pw_info_moderate))
                powerW < 3.0 -> appendLine(context.string(R.string.pw_info_high))
                else -> appendLine(context.string(R.string.pw_info_very_high))
            }
            appendLine(context.string(R.string.pw_info_currently, String.format("%.1f", powerW)))
            appendLine()
            
            // Status in simple terms
            appendLine(context.string(R.string.pw_info_status, PowerStrings.status(context, component.status)))
            appendLine()
            
            // Simple explanation based on component type
            when (component.component.lowercase()) {
                "display", "screen" -> {
                    appendLine(context.string(R.string.pw_info_means))
                    appendLine(context.string(R.string.pw_info_display))
                }
                "cpu", "processor" -> {
                    appendLine(context.string(R.string.pw_info_means))
                    appendLine(context.string(R.string.pw_info_cpu))
                }
                "network", "wifi", "cellular" -> {
                    appendLine(context.string(R.string.pw_info_means))
                    appendLine(context.string(R.string.pw_info_network))
                }
                "camera" -> {
                    appendLine(context.string(R.string.pw_info_means))
                    appendLine(context.string(R.string.pw_info_camera))
                }
                "battery" -> {
                    appendLine(context.string(R.string.pw_info_means))
                    appendLine(context.string(R.string.pw_info_battery))
                }
                else -> {
                    appendLine(context.string(R.string.pw_info_means))
                    appendLine(context.string(R.string.pw_info_other))
                }
            }
            
            // Add education content if available
            education?.let {
                appendLine()
                appendLine(context.string(R.string.pw_info_quick_tips))
                val tips = it.content.lines()
                    .filter { line -> line.trim().startsWith("•") || line.trim().startsWith("-") }
                    .take(3)
                if (tips.isEmpty()) {
                    appendLine(PowerEducation.getQuickTip(context, component.component) ?: context.string(R.string.pw_info_default_tip))
                } else {
                    tips.forEach { tip -> appendLine(tip.trim()) }
                }
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = component.icon,
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = PowerStrings.component(context, component.component),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = userFriendlyInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                
                // Show permission request button if permission is required
                if (requiresPermission && requiredPermissions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.permission_required),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.grant_permission_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { requestPermission() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(context.string(R.string.grant_permission))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(context.string(R.string.got_it))
            }
        }
    )
}

@Composable
private fun AggregatedStatsSection(
    stats: PowerConsumptionAggregator.PowerStats,
    onAIClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Track history viewing
    LaunchedEffect(Unit) {
        AnalyticsUtils.logEvent(
            AnalyticsEvent.PowerHistoryViewed,
            mapOf(
                "total_samples" to stats.totalSamples,
                "average_power" to stats.averagePower,
                "trend" to stats.powerTrend.name
            )
        )
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.power_statistics),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = stringResource(R.string.pw_cd_ai_stats),
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Statistics Grid - Beautiful card-based design (Responsive)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val screenWidth = maxWidth
                val cardsPerRow = when {
                    screenWidth < 360.dp -> 1 // Very small screens: 1 card per row
                    screenWidth < 600.dp -> 2 // Small/Medium screens: 2 cards per row
                    else -> 3 // Large screens: 3 cards per row
                }
                
                if (cardsPerRow == 3) {
                    // 3 cards per row (default layout for larger screens)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CompactStatCard(
                                icon = "📊",
                                label = stringResource(R.string.pw_stat_average),
                                value = PowerConsumptionAggregator.formatPower(stats.averagePower),
                                valueColor = MaterialTheme.colorScheme.primary,
                                powerWatts = stats.averagePower,
                                context = context
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CompactStatCard(
                                icon = "⚡",
                                label = stringResource(R.string.pw_stat_highest),
                                value = PowerConsumptionAggregator.formatPower(stats.peakPower),
                                valueColor = Color(0xFFFF5722),
                                powerWatts = stats.peakPower,
                                context = context
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CompactStatCard(
                                icon = "📈",
                                label = stringResource(R.string.pw_stat_points),
                                value = stats.totalSamples.toString(),
                                valueColor = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                } else if (cardsPerRow == 2) {
                    // 2 cards per row (responsive layout for medium screens)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // First row: Average and Highest
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CompactStatCard(
                                    icon = "📊",
                                    label = stringResource(R.string.pw_stat_average),
                                    value = PowerConsumptionAggregator.formatPower(stats.averagePower),
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    powerWatts = stats.averagePower,
                                    context = context
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                CompactStatCard(
                                    icon = "⚡",
                                    label = stringResource(R.string.pw_stat_highest),
                                    value = PowerConsumptionAggregator.formatPower(stats.peakPower),
                                    valueColor = Color(0xFFFF5722),
                                    powerWatts = stats.peakPower,
                                    context = context
                                )
                            }
                        }
                        // Second row: Data Points (full width or centered)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CompactStatCard(
                                    icon = "📈",
                                    label = stringResource(R.string.pw_stat_points),
                                    value = stats.totalSamples.toString(),
                                    valueColor = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            // Empty space to maintain alignment
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    // 1 card per row (very small screens)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactStatCard(
                            icon = "📊",
                            label = stringResource(R.string.pw_stat_average),
                            value = PowerConsumptionAggregator.formatPower(stats.averagePower),
                            valueColor = MaterialTheme.colorScheme.primary,
                            powerWatts = stats.averagePower,
                            context = context
                        )
                        CompactStatCard(
                            icon = "⚡",
                            label = stringResource(R.string.pw_stat_highest),
                            value = PowerConsumptionAggregator.formatPower(stats.peakPower),
                            valueColor = Color(0xFFFF5722),
                            powerWatts = stats.peakPower,
                            context = context
                        )
                        CompactStatCard(
                            icon = "📈",
                            label = stringResource(R.string.pw_stat_points),
                            value = stats.totalSamples.toString(),
                            valueColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Power Trend - Card style
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val trendData = when (stats.powerTrend) {
                        PowerConsumptionAggregator.PowerTrend.INCREASING -> 
                            Triple("📈", stringResource(R.string.pw_trend_up), Color(0xFFFF5722)) to 
                            stringResource(R.string.pw_trend_up_why)
                        PowerConsumptionAggregator.PowerTrend.DECREASING -> 
                            Triple("📉", stringResource(R.string.pw_trend_down), Color(0xFF4CAF50)) to 
                            stringResource(R.string.pw_trend_down_why)
                        PowerConsumptionAggregator.PowerTrend.STABLE -> 
                            Triple("📊", stringResource(R.string.pw_trend_stable), MaterialTheme.colorScheme.primary) to 
                            stringResource(R.string.pw_trend_stable_why)
                        PowerConsumptionAggregator.PowerTrend.UNKNOWN -> 
                            Triple("❓", stringResource(R.string.pw_trend_unknown), MaterialTheme.colorScheme.onSurfaceVariant) to 
                            stringResource(R.string.pw_trend_unknown_why)
                    }
                    val (trendIcon, trendText, trendColor) = trendData.first
                    val trendExplanation = trendData.second
                    
                    Text(
                        text = trendIcon,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trendText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = trendColor,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = trendExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            // Efficiency Rating - Card style
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ratingData = when {
                        stats.averagePower < 2.0 -> 
                            Triple(stringResource(R.string.pw_rating_excellent), Color(0xFF4CAF50), 
                            stringResource(R.string.pw_rating_excellent_why))
                        stats.averagePower < 4.0 -> 
                            Triple(stringResource(R.string.pw_rating_good), Color(0xFF8BC34A),
                            stringResource(R.string.pw_rating_good_why))
                        stats.averagePower < 6.0 -> 
                            Triple(stringResource(R.string.pw_rating_fair), Color(0xFFFF9800),
                            stringResource(R.string.pw_rating_fair_why))
                        stats.averagePower < 8.0 -> 
                            Triple(stringResource(R.string.pw_rating_high), Color(0xFFFF5722),
                            stringResource(R.string.pw_rating_high_why))
                        else -> 
                            Triple(stringResource(R.string.pw_rating_very_high), Color(0xFFD32F2F),
                            stringResource(R.string.pw_rating_very_high_why))
                    }
                    val (ratingText, ratingColor, ratingExplanation) = ratingData
                    
                    Text(
                        text = "🔋",
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ratingText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ratingColor,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = ratingExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStatCard(
    icon: String,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.primary,
    powerWatts: Double? = null, // Optional: power in watts for practical info
    context: android.content.Context? = null // Optional: context for battery percentage calculation
) {
    // Detect dark mode
    val isDarkMode = MaterialTheme.colorScheme.background == DesignSystemColors.Dark
    val backgroundColor = if (isDarkMode) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val labelTextColor = if (isDarkMode) DesignSystemColors.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    // Use consistent value color that works in both themes - use primary color for better UX
    val valueTextColor = if (isDarkMode) {
        // In dark mode, use white for better contrast, but allow valueColor override for special cases
        if (valueColor == MaterialTheme.colorScheme.primary) DesignSystemColors.White else valueColor
    } else {
        // In light mode, use the provided valueColor (usually primary)
        valueColor
    }
    val practicalInfoColor = if (isDarkMode) DesignSystemColors.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    
    // Calculate practical info if power and context provided
    val practicalInfo = remember(powerWatts, context) {
        if (powerWatts != null && powerWatts > 0 && context != null) {
            PowerConsumptionAggregator.calculateBatteryPercentPerHour(powerWatts, context)
        } else null
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = valueTextColor,
                fontSize = 14.sp
            )
            // Show practical info if available
            practicalInfo?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = practicalInfoColor,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = labelTextColor,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Centralized helper function to show ad before executing any test action
 * This ensures all test buttons work consistently and state persists across activity recreation
 * Note: This is NOT a @Composable function because it's called from onClick handlers
 */
private fun showAdBeforeTestAction(
    activity: Activity?,
    actionName: String,
    action: () -> Unit
) {
    if (activity != null) {
        InterstitialAdManager.showAdBeforeAction(
            activity = activity,
            actionName = actionName,
            action = action
        )
    } else {
        // Fallback: execute action immediately if no activity
        action()
    }
}

// Removed shouldShowAdForCsvView() - InterstitialAdManager handles all checks centrally

@Composable
private fun CameraPowerTestSection(
    context: android.content.Context,
    viewModel: PowerConsumptionViewModel,
    activity: Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null
) {
    android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - Composable recomposed")
    
    // Use ViewModel state - persists across activity recreation
    val isTestRunning by viewModel.isCameraTestRunning.collectAsState()
    val testResult by viewModel.cameraTestResult.collectAsState()
    val allTestResults by viewModel.allCameraTestResults.collectAsState()
    val currentTestNumber by viewModel.currentTestNumber.collectAsState()
    val totalTests by viewModel.totalTests.collectAsState()
    
    android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - State collected: isTestRunning=$isTestRunning, testResultsCount=${allTestResults.size}, currentTest=$currentTestNumber/$totalTests")
    
    // Preserve LazyColumn scroll state across activity recreation
    // Get saved scroll position from ViewModel and restore it
    val savedFirstVisibleItemIndex by viewModel.lazyListFirstVisibleItemIndex.collectAsState()
    val savedFirstVisibleItemScrollOffset by viewModel.lazyListFirstVisibleItemScrollOffset.collectAsState()
    val testResultsListState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = savedFirstVisibleItemScrollOffset
    )
    
    var multipleTestResults by remember { mutableStateOf<List<PowerConsumptionUtils.CameraPowerTestResult>?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showMultipleTestDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // Use ViewModel state for CSV dialog - persists across activity recreation
    val showCsvDialog by viewModel.showCameraCsvDialog.collectAsState()
    
    // Create a stable callback reference for CSV dialog that persists across recompositions
    val openCsvDialog = remember {
        {
            android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - openCsvDialog callback invoked")
            viewModel.setCameraCsvDialogVisible(true)
            android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - showCsvDialog set to true via ViewModel")
            Unit // Explicitly return Unit
        }
    }
    var previewSurface by remember { mutableStateOf<Surface?>(null) }
    // currentTestNumber and totalTests are now from ViewModel (above)
    val coroutineScope = rememberCoroutineScope()
    
    // Restore scroll position after activity recreation
    LaunchedEffect(Unit) {
        if (savedFirstVisibleItemIndex > 0 && testResultsListState.firstVisibleItemIndex == 0) {
            // Only restore if we have a saved position and current position is 0 (activity was recreated)
            kotlinx.coroutines.delay(100) // Wait for layout
            testResultsListState.animateScrollToItem(savedFirstVisibleItemIndex, savedFirstVisibleItemScrollOffset)
        }
    }
    
    // Save scroll position to ViewModel when it changes (throttled to prevent ANR)
    // Use snapshotFlow with debounce to prevent LaunchedEffect from restarting on every scroll event
    LaunchedEffect(testResultsListState) {
        snapshotFlow { 
            testResultsListState.firstVisibleItemIndex to testResultsListState.firstVisibleItemScrollOffset 
        }
            .debounce(500) // Only process scroll changes every 500ms
            .collect { (index, offset) ->
                if (index > 0 || offset > 0) {
                    viewModel.saveLazyListScrollPosition(index, offset)
                }
            }
    }
    
    // Check camera permission - using PermissionManager
    var hasCameraPermission by remember {
        mutableStateOf(PermissionManager.hasCameraPermission(context))
    }

    // Camera permission launcher
    // BUG FIX (2026-07-24): hasCameraPermission was `remember { ... }` with no key, so it never
    // recomputed after the user granted permission — the preview stayed hidden until the whole
    // composition was torn down and rebuilt. Update the state directly from the launcher result.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            // Permission granted, show success message
            showPermissionDialog = true
        } else {
            // Permission denied, show error message
            showPermissionDialog = true
        }
    }
    
    // Detect dark mode
    val isDarkMode = MaterialTheme.colorScheme.background == DesignSystemColors.Dark
    val cardBackgroundColor = if (isDarkMode) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surface
    val headerTextColor = if (isDarkMode) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardBackgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            DesignSystemColors.NeonGreen.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.camera_power_test),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pw_cam_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = headerTextColor,
                    modifier = Modifier.weight(1f)
                )
                if (onItemAIClick != null) {
                    IconButton(
                        onClick = {
                            val allResults = allTestResults
                            val resultsText = if (allResults.isNotEmpty()) {
                                allResults.takeLast(5).reversed().joinToString("\n") { result ->
                                    "Test ${allResults.size - allResults.takeLast(5).reversed().indexOf(result)}: ${PowerConsumptionAggregator.formatPower(result.powerDifference)} (${result.captureDuration}ms)"
                                }
                            } else {
                                "No test results yet. Run a test to see camera power consumption data."
                            }
                            val content = """
Camera Power Test Results:
$resultsText

Total Tests: ${allResults.size}
                            """.trimIndent()
                            onItemAIClick("Camera Power Test", content)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = stringResource(R.string.pw_cd_ai_camera),
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.pw_cam_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.pw_cam_tip),
                style = MaterialTheme.typography.bodySmall,
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Camera Preview (TextureView) - shown when test is running or has permission
            if (hasCameraPermission) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        DesignSystemColors.NeonGreen.copy(alpha = 0.5f)
                    )
                ) {
                    var textureView by remember { mutableStateOf<TextureView?>(null) }
                    
                    // Properly clean up TextureView and Surface to prevent ANR
                    DisposableEffect(Unit) {
                        onDispose {
                            // Clean up Surface asynchronously to prevent blocking hardware renderer destruction
                            val surfaceToRelease = previewSurface
                            previewSurface = null
                            
                            // Release Surface on background thread to prevent ANR
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    surfaceToRelease?.release()
                                } catch (e: Exception) {
                                    // Surface may already be released, ignore
                                }
                            }
                            
                            // Clean up TextureView asynchronously
                            textureView?.let { view ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    try {
                                        view.surfaceTextureListener = null
                                    } catch (e: Exception) {
                                        // Ignore cleanup errors
                                    }
                                }
                            }
                        }
                    }
                    
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                textureView = this
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        surface: SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) {
                                        previewSurface = Surface(surface)
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        surface: SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) {
                                        // Surface size changed
                                    }

                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        // Return false to handle cleanup ourselves asynchronously
                                        // This prevents blocking the hardware renderer destruction
                                        val surfaceToRelease = previewSurface
                                        previewSurface = null
                                        
                                        // Release Surface asynchronously to prevent ANR
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                surfaceToRelease?.release()
                                            } catch (e: Exception) {
                                                // Surface may already be released, ignore
                                            }
                                        }
                                        
                                        // Return false so we handle cleanup ourselves
                                        return false
                                    }

                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                        // Frame updated
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Overlay text when not running
                    if (!isTestRunning) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.camera_preview),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Camera Status Indicator (when test is running)
            if (isTestRunning) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DesignSystemColors.NeonGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        DesignSystemColors.NeonGreen.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (totalTests > 1) {
                                    stringResource(R.string.pw_cam_progress_n, currentTestNumber, totalTests)
                                } else {
                                    stringResource(R.string.pw_cam_progress)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (totalTests > 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.preview_live_feed),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Test Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Single Test Button
                Button(
                    onClick = {
                        if (!hasCameraPermission) {
                            // Request camera permission
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            val activity = context as? Activity
                            if (activity != null) {
                                android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - Single test button clicked, creating action")
                                android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - coroutineScope: ${coroutineScope.hashCode()}, context: ${context.hashCode()}")
                                
                                // Create the action that will execute after ad
                                // IMPORTANT: Use ViewModel's viewModelScope instead of composable's coroutineScope
                                // because viewModelScope persists across activity recreation
                                val actionToExecute: () -> Unit = {
                                    android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - actionToExecute lambda invoked")
                                    android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - Using ViewModel's viewModelScope (persists across activity recreation)")
                                    
                                    // Use ViewModel's viewModelScope which survives activity recreation
                                    viewModel.startSingleTest(
                                        context = context,
                                        previewSurface = previewSurface,
                                        onComplete = { result ->
                                            android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - Test completed callback, result: ${result != null}")
                                            showResultDialog = true
                                        }
                                    )
                                    android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - ViewModel.startSingleTest() called")
                                }
                                
                                android.util.Log.d("PowerStateDebug", "CameraPowerTestSection - Showing ad before single test")
                                AnalyticsUtils.logEvent(AnalyticsEvent.PowerTestSingleTestClicked)
                                // Show ad before test (with throttling)
                                InterstitialAdManager.showAdBeforeAction(
                                    activity = activity,
                                    actionName = "power_single_test"
                                ) {
                                    actionToExecute()
                                }
                            } else {
                                // Fallback if no activity - use ViewModel's startSingleTest
                                viewModel.startSingleTest(
                                    context = context,
                                    previewSurface = previewSurface,
                                    onComplete = { result ->
                            showResultDialog = true
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isTestRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = DesignSystemColors.Dark
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.string(R.string.testing), fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.string(R.string.single_test), fontSize = 12.sp)
                    }
                }
                
                // Multiple Test Button
                Button(
                    onClick = {
                        if (!hasCameraPermission) {
                            // Request camera permission
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        } else {
                            val activity = context as? Activity
                            if (activity != null) {
                                // Create the action that will execute after ad - use ViewModel's viewModelScope
                                val actionToExecute: () -> Unit = {
                                    viewModel.executeTestAction(
                                        actionName = "camera_five_tests",
                                        action = {
                                            viewModel.setCameraTestRunning(true)
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.PowerExperimentStarted,
                                    mapOf("experiment_type" to "camera", "test_type" to "multiple")
                                )
                                            viewModel.setTestProgress(0, 5)
                                
                                // Create a wrapper that updates progress
                                val results = withContext(Dispatchers.IO) {
                                    val resultsList = mutableListOf<PowerConsumptionUtils.CameraPowerTestResult>()
                                    repeat(5) { testNum ->
                                        // Update progress on main thread
                                        withContext(Dispatchers.Main) {
                                                        viewModel.setTestProgress(testNum + 1, 5)
                                        }
                                        
                                        val result = PowerConsumptionUtils.measureSinglePhotoPowerConsumption(
                                            context,
                                            previewSurface
                                        )
                                        resultsList.add(result)
                                        if (testNum < 4) {
                                            delay(5000) // 5 second delay between tests for system stabilization
                                        }
                                    }
                                    resultsList
                                }
                                multipleTestResults = results
                                            // Add all results to ViewModel (which saves automatically)
                                            results.forEach { result ->
                                                viewModel.addCameraTestResult(result)
                                            }
                                            
                                            viewModel.setCameraTestRunning(false)
                                            viewModel.setTestProgress(0, 0)
                                            AnalyticsUtils.logEvent(
                                                AnalyticsEvent.PowerExperimentCompleted,
                                                mapOf("experiment_type" to "camera", "test_type" to "multiple")
                                            )
                                            PowerAchievements.recordExperimentCompletion(context, "camera")
                                            // Track meaningful interaction for review prompt (after positive experiment experience)
                                            activity?.let {
                                                ReviewPromptManager.trackMeaningfulInteraction(it, "power_experiment_camera_completed")
                                            }
                                        },
                                        onComplete = {
                            showMultipleTestDialog = true
                                        }
                                    )
                                }
                                
                                AnalyticsUtils.logEvent(AnalyticsEvent.PowerTestMultipleTestsClicked)
                                // Show ad before test (with throttling)
                                InterstitialAdManager.showAdBeforeAction(
                                    activity = activity,
                                    actionName = "power_multiple_tests"
                                ) {
                                    actionToExecute()
                                }
                            } else {
                                // Fallback if no activity - use ViewModel's executeTestAction
                                viewModel.executeTestAction(
                                    actionName = "camera_five_tests",
                                    action = {
                                        viewModel.setCameraTestRunning(true)
                                        AnalyticsUtils.logEvent(
                                            AnalyticsEvent.PowerExperimentStarted,
                                            mapOf("experiment_type" to "camera", "test_type" to "multiple")
                                        )
                                        viewModel.setTestProgress(0, 5)
                                        val results = withContext(Dispatchers.IO) {
                                            val resultsList = mutableListOf<PowerConsumptionUtils.CameraPowerTestResult>()
                                            repeat(5) { testNum ->
                                                withContext(Dispatchers.Main) {
                                                    viewModel.setTestProgress(testNum + 1, 5)
                                                }
                                                val result = PowerConsumptionUtils.measureSinglePhotoPowerConsumption(
                                                    context,
                                                    previewSurface
                                                )
                                                resultsList.add(result)
                                                if (testNum < 4) {
                                                    delay(5000) // 5 second delay between tests
                                                }
                                            }
                                            resultsList
                                        }
                                        results.forEach { result ->
                                            viewModel.addCameraTestResult(result)
                                        }
                                        viewModel.setCameraTestRunning(false)
                                        viewModel.setTestProgress(0, 0)
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.PowerExperimentCompleted,
                                    mapOf("experiment_type" to "camera", "test_type" to "multiple")
                                )
                                PowerAchievements.recordExperimentCompletion(context, "camera")
                                // Track meaningful interaction for review prompt (after positive experiment experience)
                                activity?.let {
                                    ReviewPromptManager.trackMeaningfulInteraction(it, "power_experiment_camera_completed")
                                }
                                    },
                                    onComplete = {
                                        showMultipleTestDialog = true
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isTestRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.string(R.string.testing), fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.string(R.string.five_tests), fontSize = 12.sp)
                    }
                }
            }
            
            // Results Preview
            if (allTestResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.pw_cam_all_results, allTestResults.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Show last 5 results with beautiful cards
                        val recentResults = allTestResults.takeLast(5).reversed()
                        recentResults.forEachIndexed { index, result ->
                            val testNumber = allTestResults.size - recentResults.size + index + 1
                            val powerText = PowerConsumptionAggregator.formatPower(result.powerDifference)
                            val durationText = if (result.captureDuration < 1000) {
                                "${result.captureDuration}ms"
                            } else {
                                "${String.format("%.1f", result.captureDuration / 1000.0)}s"
                            }
                            
                            // Calculate energy in Joules and battery percentage
                            val energyJoules = result.powerDifference * result.captureDuration / 1000.0
                            val batteryPercent = PowerConsumptionAggregator.calculateBatteryPercentForAction(energyJoules, context)
                            
                            // Determine color based on power consumption (higher = more intense color)
                            val powerValue = result.powerDifference
                            val powerColor = when {
                                powerValue < 0.001 -> MaterialTheme.colorScheme.primary // Very low - greenish
                                powerValue < 0.01 -> Color(0xFFFFC107) // Medium - amber
                                else -> Color(0xFFFF5722) // High - red/orange
                            }
                            
                            // Build value text with both watts and battery percentage
                            val valueText = buildString {
                                append(powerText)
                                batteryPercent?.let {
                                    append(" • " + context.string(R.string.pw_cam_takes, it))
                                }
                            }
                            
                            // Create meaningful subtitle
                            val subtitleText = buildString {
                                append(context.string(R.string.pw_cam_capture_time, durationText))
                                batteryPercent?.let { percent ->
                                    val percentValue = percent.replace("%", "").toDoubleOrNull() ?: 0.0
                                    when {
                                        percentValue < 0.001 -> append(" • " + context.string(R.string.pw_cam_very_efficient))
                                        percentValue < 0.01 -> append(" • " + context.string(R.string.pw_cam_efficient))
                                        percentValue < 0.1 -> append(" • " + context.string(R.string.pw_cam_normal))
                                        else -> append(" • " + context.string(R.string.pw_cam_higher))
                                    }
                                } ?: run {
                                    append(" • " + context.string(R.string.pw_cam_minimal))
                                }
                            }
                            
                            SampleDataCard(
                                icon = "📷",
                                title = stringResource(R.string.pw_test_n, testNumber),
                                value = valueText,
                                subtitle = subtitleText,
                                valueColor = powerColor
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (allTestResults.size > 5) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreHoriz,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.pw_cam_more, allTestResults.size - 5),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Compact footer with summary and CSV button
                        TestResultsFooter(
                            count = allTestResults.size,
                            countLabel = if (allTestResults.size > 1) "tests" else "test",
                            onViewCsv = openCsvDialog,
                            context = context,
                            activity = activity
                        )
                }
            }
        }
            
        }
    }
    
    // CSV Dialog
    if (showCsvDialog && allTestResults.isNotEmpty()) {
        val allResults = allTestResults
        
        CsvPreviewDialog(
            title = stringResource(R.string.camera_power_test_data),
            headers = listOf(stringResource(R.string.pw_csv_timestamp), stringResource(R.string.pw_csv_cam_baseline), stringResource(R.string.pw_csv_cam_preview), stringResource(R.string.pw_csv_cam_capture), stringResource(R.string.pw_csv_cam_delta), stringResource(R.string.pw_csv_cam_duration), stringResource(R.string.pw_csv_cam_energy)),
            headerDescriptions = mapOf(
                stringResource(R.string.pw_csv_timestamp) to stringResource(R.string.pw_csv_cam_timestamp_d),
                stringResource(R.string.pw_csv_cam_baseline) to stringResource(R.string.pw_csv_cam_baseline_d),
                stringResource(R.string.pw_csv_cam_preview) to stringResource(R.string.pw_csv_cam_preview_d),
                stringResource(R.string.pw_csv_cam_capture) to stringResource(R.string.pw_csv_cam_capture_d),
                stringResource(R.string.pw_csv_cam_delta) to stringResource(R.string.pw_csv_cam_delta_d),
                stringResource(R.string.pw_csv_cam_duration) to stringResource(R.string.pw_csv_cam_duration_d),
                stringResource(R.string.pw_csv_cam_energy) to stringResource(R.string.pw_csv_cam_energy_d)
            ),
            rows = allResults.map { result ->
                listOf(
                    PowerConsumptionAggregator.formatTimestamp(result.timestamp),
                    PowerConsumptionAggregator.formatPower(result.baselinePower),
                    PowerConsumptionAggregator.formatPower(result.previewPower),
                    PowerConsumptionAggregator.formatPower(result.capturePower),
                    PowerConsumptionAggregator.formatPower(result.powerDifference),
                    result.captureDuration.toString(),
                    "%.3f".format(result.powerDifference * result.captureDuration / 1000.0)
                )
            },
            onDismiss = { viewModel.setCameraCsvDialogVisible(false) },
            onExport = {
                // Show ad before CSV export
                if (activity != null) {
                    InterstitialAdManager.showAdBeforeAction(
                        activity = activity,
                        actionName = "csv_export_camera"
                    ) {
                        AnalyticsUtils.logEvent(AnalyticsEvent.PowerCsvExported, mapOf<String, Any?>(
                            "test_type" to "camera",
                            "row_count" to allResults.size
                        ))
                        val csvHeaders = listOf(
                            "timestamp", "baseline_power_w", "preview_power_w", "capture_power_w",
                            "power_difference_w", "capture_duration_ms", "energy_j"
                        )
                        val csvRows = allResults.map { result ->
                            listOf(
                                result.timestamp.toString(),
                                result.baselinePower.toString(),
                                result.previewPower.toString(),
                                result.capturePower.toString(),
                                result.powerDifference.toString(),
                                result.captureDuration.toString(),
                                (result.powerDifference * result.captureDuration / 1000.0).toString()
                            )
                        }
                        
                        val uri = PowerConsumptionUtils.exportExperimentCSV(
                            context = context,
                            experimentName = "camera_power_tests",
                            headers = csvHeaders,
                            rows = csvRows
                        )
                        PowerAchievements.recordCsvExport(context)
                        
                        uri?.let {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, it)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.string(R.string.pw_share_camera_data)))
                        }
                    }
                } else {
                    // Fallback if no activity
                    AnalyticsUtils.logEvent(AnalyticsEvent.PowerCsvExported, mapOf<String, Any?>(
                        "test_type" to "camera",
                        "row_count" to allResults.size
                    ))
                    val csvHeaders = listOf(
                        "timestamp", "baseline_power_w", "preview_power_w", "capture_power_w",
                        "power_difference_w", "capture_duration_ms", "energy_j"
                    )
                    val csvRows = allResults.map { result ->
                        listOf(
                            result.timestamp.toString(),
                            result.baselinePower.toString(),
                            result.previewPower.toString(),
                            result.capturePower.toString(),
                            result.powerDifference.toString(),
                            result.captureDuration.toString(),
                            (result.powerDifference * result.captureDuration / 1000.0).toString()
                        )
                    }
                    
                    val uri = PowerConsumptionUtils.exportExperimentCSV(
                        context = context,
                        experimentName = "camera_power_tests",
                        headers = csvHeaders,
                        rows = csvRows
                    )
                    PowerAchievements.recordCsvExport(context)
                    
                    uri?.let {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, it)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.string(R.string.pw_share_camera_data)))
                    }
                }
            }
        )
    }
    
    // Single Test Result Dialog
    if (showResultDialog && testResult != null) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.single_photo_power_test),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = PowerConsumptionUtils.formatCameraPowerTestResult(context, testResult!!),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResultDialog = false
                        // Ad already shown before test - no need to show again after result
                    }
                ) {
                    Text(context.string(R.string.ok))
                }
            }
        )
    }
    
    // Multiple Test Results Dialog
    if (showMultipleTestDialog && multipleTestResults != null) {
        val results = multipleTestResults!!
        
        AlertDialog(
            onDismissRequest = { showMultipleTestDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.multiple_photo_power_tests),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.test_results),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        state = testResultsListState,
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(results.size) { index ->
                            val result = results[index]
                    Text(
                                text = stringResource(R.string.pw_cam_result_line, index + 1, PowerConsumptionAggregator.formatPower(result.powerDifference), result.captureDuration),
                        style = MaterialTheme.typography.bodyMedium
                    )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMultipleTestDialog = false
                        // Ad already shown before test - no need to show again after result
                    }
                ) {
                    Text(context.string(R.string.ok))
                }
            }
        )
    }
    
    // Camera Permission Dialog
    if (showPermissionDialog) {
        val currentPermission = PermissionManager.hasCameraPermission(context)
        
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                    Text(
                    text = if (currentPermission) stringResource(R.string.pw_cam_perm_granted_title) else stringResource(R.string.pw_cam_perm_required_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                    )
            },
            text = {
                    Text(
                    text = if (currentPermission) {
                        stringResource(R.string.pw_cam_perm_granted_body)
                    } else {
                        stringResource(R.string.pw_cam_perm_required_body)
                    },
                        style = MaterialTheme.typography.bodyMedium
                    )
            },
            confirmButton = {
                TextButton(
                    onClick = { showPermissionDialog = false }
                ) {
                    Text(context.string(R.string.ok))
                }
            }
        )
    }
}

@Composable
fun PowerConsumptionSection(
    onShareClick: (String) -> Unit = {},
    onAIClick: (() -> Unit)? = null,
    onItemAIClick: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Use ViewModel to persist scroll state across activity recreation
    val viewModel: PowerConsumptionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    
    // Preserve scroll state across activity recreation
    // Get saved scroll position from ViewModel and restore it
    val savedSectionScrollPosition by viewModel.sectionScrollPosition.collectAsState()
    val sectionScrollState = rememberScrollState(initial = savedSectionScrollPosition)
    
    // Restore scroll position after activity recreation
    LaunchedEffect(Unit) {
        if (savedSectionScrollPosition > 0 && sectionScrollState.value == 0) {
            // Only restore if we have a saved position and current position is 0 (activity was recreated)
            kotlinx.coroutines.delay(100) // Wait for layout
            sectionScrollState.scrollTo(savedSectionScrollPosition)
        }
    }
    
    // Save scroll position to ViewModel when it changes (throttled to prevent ANR)
    // Use snapshotFlow with debounce to prevent LaunchedEffect from restarting on every scroll event
    LaunchedEffect(sectionScrollState) {
        snapshotFlow { sectionScrollState.value }
            .debounce(500) // Only process scroll changes every 500ms
            .collect { scrollValue ->
                if (scrollValue > 0) {
                    viewModel.saveSectionScrollPosition(scrollValue)
                }
            }
    }
    
    // Show interstitial ad when Power tab is opened (using centralized throttling mechanism)
    // InterstitialAdManager.showAdIfAvailable handles all checks internally:
    // - Checks canShowAd() (60-second cooldown via RemoteConfig)
    // - Checks RemoteConfigUtils.shouldShowInterstitialAds() (includes debug mode check)
    // - Updates lastAdShownTime when ad is shown
    // All checks are centralized - no manual BuildConfig.DEBUG check needed
    LaunchedEffect(Unit) {
        if (activity != null) {
            // Use centralized throttling mechanism - will show ad if throttling allows
            // shouldShowInterstitialAds() already checks debug mode internally
            InterstitialAdManager.showAdIfAvailable(activity) {
                // Ad shown and dismissed (or skipped due to throttling/debug mode)
            }
        }
        // Log analytics for power tab view
        AnalyticsUtils.logEvent(AnalyticsEvent.TabPowerViewed)
    }
    
    // Collect power data for sharing
    val currentPowerData by PowerConsumptionAggregator.currentPowerFlow.collectAsState()
    val aggregatedStats by PowerConsumptionAggregator.aggregatedStatsFlow.collectAsState()
    
    // Generate share text whenever power data updates
    LaunchedEffect(currentPowerData, aggregatedStats) {
        val shareText = generatePowerShareText(context, currentPowerData, aggregatedStats)
        onShareClick(shareText)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(sectionScrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PowerConsumptionCard(onItemAIClick = onItemAIClick)
        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * Generate power share text for AI and sharing
 */
private fun generatePowerShareText(
    context: android.content.Context,
    powerData: PowerConsumptionUtils.PowerConsumptionSummary?,
    stats: PowerConsumptionAggregator.PowerStats?
): String {
    return buildString {
        appendLine(context.string(R.string.pw_share_title))
        appendLine("===========================")
        appendLine()
        
        if (powerData != null) {
            appendLine(context.string(R.string.pw_share_current))
            appendLine("  " + context.string(R.string.pw_share_total, PowerConsumptionAggregator.formatPower(powerData.totalPower)))
            appendLine()
            
            appendLine(context.string(R.string.pw_share_breakdown))
            powerData.components.sortedByDescending { it.powerConsumption }.forEach { component ->
                val percentage = if (powerData.totalPower > 0) {
                    (component.powerConsumption / powerData.totalPower * 100).toInt()
                } else 0
                appendLine("  • ${component.icon} ${PowerStrings.component(context, component.component)}: ${PowerConsumptionAggregator.formatPower(component.powerConsumption)} ($percentage%)")
            }
            appendLine()
        }
        
        if (stats != null) {
            appendLine(context.string(R.string.pw_share_stats))
            appendLine("  " + context.string(R.string.pw_share_avg, PowerConsumptionAggregator.formatPower(stats.averagePower)))
            appendLine("  " + context.string(R.string.pw_share_peak, PowerConsumptionAggregator.formatPower(stats.peakPower)))
            appendLine("  " + context.string(R.string.pw_share_min, PowerConsumptionAggregator.formatPower(stats.minPower)))
            appendLine("  " + context.string(R.string.pw_share_samples, stats.totalSamples))
            appendLine("  " + context.string(R.string.pw_share_trend, PowerStrings.trend(context, stats.powerTrend)))
            appendLine()
            
            if (stats.topConsumers.isNotEmpty()) {
                appendLine(context.string(R.string.pw_share_top))
                stats.topConsumers.take(5).forEach { consumer ->
                    appendLine("  " + context.string(R.string.pw_share_consumer, PowerStrings.component(context, consumer.component), PowerConsumptionAggregator.formatPower(consumer.averagePower), consumer.usagePercentage.toInt()))
                }
                appendLine()
            }
        }
        
        if (powerData == null && stats == null) {
            appendLine(context.string(R.string.pw_share_no_data))
        }
        
        appendLine(context.string(R.string.pw_share_last_updated, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())))
    }
}

// ===== NEW EXPERIMENT SECTIONS =====

@Composable
private fun DisplayPowerSweepSection(
    context: android.content.Context,
    viewModel: PowerConsumptionViewModel,
    activity: Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null
) {
    // Use ViewModel state - persists across activity recreation (survives ad show/close)
    val isTestRunning by viewModel.isDisplayTestRunning.collectAsState()
    val testResults by viewModel.displayTestResults.collectAsState()
    var showResultDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // Use ViewModel state for CSV dialog - persists across activity recreation
    val showCsvDialog by viewModel.showDisplayCsvDialog.collectAsState()
    
    // Create a stable callback reference for CSV dialog that persists across recompositions
    val openCsvDialog = remember {
        {
            android.util.Log.d("PowerStateDebug", "DisplayPowerSweepSection - openCsvDialog callback invoked")
            viewModel.setDisplayCsvDialogVisible(true)
            android.util.Log.d("PowerStateDebug", "DisplayPowerSweepSection - showCsvDialog set to true via ViewModel")
            Unit // Explicitly return Unit
        }
    }
    
    // Check WRITE_SETTINGS permission - make it reactive
    var hasWriteSettings by remember { mutableStateOf(android.provider.Settings.System.canWrite(context)) }
    
    // Re-check permission when composable becomes visible (user returns from settings)
    LaunchedEffect(Unit) {
        hasWriteSettings = android.provider.Settings.System.canWrite(context)
    }
    
    // Also re-check when showPermissionDialog changes (user might have returned from settings)
    LaunchedEffect(showPermissionDialog) {
        if (!showPermissionDialog) {
            // User dismissed dialog, might have enabled permission
            hasWriteSettings = android.provider.Settings.System.canWrite(context)
        }
    }
    
    // Detect dark mode
    val isDarkMode = MaterialTheme.colorScheme.background == DesignSystemColors.Dark
    val cardBackgroundColor = if (isDarkMode) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surface
    val headerTextColor = if (isDarkMode) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardBackgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            DesignSystemColors.NeonGreen.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.BrightnessHigh,
                    contentDescription = stringResource(R.string.pw_disp_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pw_disp_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = headerTextColor,
                    modifier = Modifier.weight(1f)
                )
                if (onItemAIClick != null) {
                    IconButton(
                        onClick = {
                            val displayResults = testResults
                            val resultsText = if (displayResults != null && displayResults.isNotEmpty()) {
                                displayResults.joinToString("\n") { result ->
                                    "Brightness ${result.brightnessLevel}%: ${PowerConsumptionAggregator.formatPower(result.powerW)}"
                                }
                            } else {
                                "No test results yet. Run a brightness test to see power consumption data."
                            }
                            val content = """
Display Brightness Power Test Results:
$resultsText

This shows how different brightness levels affect battery consumption.
                            """.trimIndent()
                            onItemAIClick("Display Brightness Power Test", content)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = stringResource(R.string.pw_cd_ai_display),
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.pw_disp_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.pw_disp_fast),
                style = MaterialTheme.typography.bodySmall,
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = stringResource(R.string.pw_disp_full),
                style = MaterialTheme.typography.bodySmall,
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Permission warning
            if (!hasWriteSettings) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.permission_required),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.pw_disp_perm_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                // Request WRITE_SETTINGS permission
                                // On Android 6.0+ (API 23+), this must be granted manually via system settings
                                try {
                                    // First, try to open the specific WRITE_SETTINGS permission page
                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    
                                    // Check if the activity exists
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                    } else {
                                        // If ACTION_MANAGE_WRITE_SETTINGS is not available, 
                                        // try opening app settings where user can find it
                                        val appSettingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(appSettingsIntent)
                                        
                                        // Show toast with instructions
                                        android.widget.Toast.makeText(
                                            context,
                                            context.string(R.string.pw_disp_perm_toast),
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    // Final fallback: Open general app settings
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e2: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.string(R.string.pw_disp_perm_fail_app, context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(context.packageName, 0))),
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.open_settings),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Test Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Sweep Button
                Button(
                    onClick = {
                        if (!hasWriteSettings) {
                            showPermissionDialog = true
                        } else {
                            val activity = context as? Activity
                            if (activity != null) {
                                // Set state in ViewModel before showing ad (persists across activity recreation)
                                viewModel.setDisplayTestRunning(true)
                                
                                AnalyticsUtils.logEvent(AnalyticsEvent.PowerTestQuickSweepClicked)
                                showAdBeforeTestAction(
                                    activity = activity,
                                    actionName = "display_quick_sweep",
                                    action = {
                                        viewModel.executeTestAction(
                                            actionName = "display_quick_sweep",
                                            action = {
                                                viewModel.setDisplayTestRunning(true)
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.PowerExperimentStarted,
                                    mapOf("experiment_type" to "display", "test_type" to "simple")
                                )
                                                val results = withContext(Dispatchers.IO) {
                                    PowerConsumptionUtils.runDisplayPowerSweep(
                                        context = context,
                                        steps = listOf(20, 40, 60, 80, 100),
                                        contentApl = 0.2f
                                    )
                                }
                                                viewModel.setDisplayTestResults(results)
                                                viewModel.setDisplayTestRunning(false)
                                                AnalyticsUtils.logEvent(
                                                    AnalyticsEvent.PowerExperimentCompleted,
                                                    mapOf("experiment_type" to "display", "test_type" to "simple")
                                                )
                                                PowerAchievements.recordExperimentCompletion(context, "display")
                                            },
                                            onComplete = {
                                showResultDialog = true
                                            }
                                        )
                                    }
                                )
                            } else {
                                // Fallback if no activity - use ViewModel's viewModelScope
                                viewModel.executeTestAction(
                                    actionName = "display_quick_sweep",
                                    action = {
                                        viewModel.setDisplayTestRunning(true)
                                        AnalyticsUtils.logEvent(
                                            AnalyticsEvent.PowerExperimentStarted,
                                            mapOf("experiment_type" to "display", "test_type" to "simple")
                                        )
                                        val results = withContext(Dispatchers.IO) {
                                            PowerConsumptionUtils.runDisplayPowerSweep(
                                                context = context,
                                                steps = listOf(20, 40, 60, 80, 100),
                                                contentApl = 0.2f
                                            )
                                        }
                                        viewModel.setDisplayTestResults(results)
                                        viewModel.setDisplayTestRunning(false)
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.PowerExperimentCompleted,
                                    mapOf("experiment_type" to "display", "test_type" to "simple")
                                )
                                PowerAchievements.recordExperimentCompletion(context, "display")
                                    },
                                    onComplete = {
                                        showResultDialog = true
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isTestRunning,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = DesignSystemColors.Dark
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.string(R.string.testing), fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.BrightnessHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.string(R.string.quick_sweep), fontSize = 12.sp)
                    }
                }
                
                // Full Sweep Button
                Button(
                    onClick = {
                        if (!hasWriteSettings) {
                            showPermissionDialog = true
                        } else {
                            val activity = context as? Activity
                            if (activity != null) {
                                // Set state in ViewModel before showing ad (persists across activity recreation)
                                viewModel.setDisplayTestRunning(true)
                                
                                AnalyticsUtils.logEvent(AnalyticsEvent.PowerTestFullSweepClicked)
                                showAdBeforeTestAction(
                                    activity = activity,
                                    actionName = "display_full_sweep",
                                    action = {
                                        viewModel.executeTestAction(
                                            actionName = "display_full_sweep",
                                            action = {
                                                viewModel.setDisplayTestRunning(true)
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.PowerExperimentStarted,
                                    mapOf("experiment_type" to "display", "test_type" to "advanced")
                                )
                                val lowAplResults = withContext(Dispatchers.IO) {
                                    PowerConsumptionUtils.runDisplayPowerSweep(
                                        context = context,
                                        steps = listOf(0, 20, 40, 60, 80, 100),
                                        contentApl = 0.2f
                                    )
                                }
                                val highAplResults = withContext(Dispatchers.IO) {
                                    PowerConsumptionUtils.runDisplayPowerSweep(
                                        context = context,
                                        steps = listOf(0, 20, 40, 60, 80, 100),
                                        contentApl = 0.8f
                                    )
                                }
                                                viewModel.setDisplayTestResults(lowAplResults + highAplResults)
                                                viewModel.setDisplayTestRunning(false)
                                                AnalyticsUtils.logEvent(
                                                    AnalyticsEvent.PowerExperimentCompleted,
                                                    mapOf("experiment_type" to "display", "test_type" to "advanced")
                                                )
                                                PowerAchievements.recordExperimentCompletion(context, "display")
                                            },
                                            onComplete = {
                                showResultDialog = true
                                            }
                                        )
                                    }
                                )
                            } else {
                                // Fallback if no activity - use ViewModel's viewModelScope
                                viewModel.executeTestAction(
                                    actionName = "display_full_sweep",
                                    action = {
                                        viewModel.setDisplayTestRunning(true)
                                        AnalyticsUtils.logEvent(
                                            AnalyticsEvent.PowerExperimentStarted,
                                            mapOf("experiment_type" to "display", "test_type" to "advanced")
                                        )
                                        val lowAplResults = withContext(Dispatchers.IO) {
                                            PowerConsumptionUtils.runDisplayPowerSweep(
                                                context = context,
                                                steps = listOf(0, 20, 40, 60, 80, 100),
                                                contentApl = 0.2f
                                            )
                                        }
                                        val highAplResults = withContext(Dispatchers.IO) {
                                            PowerConsumptionUtils.runDisplayPowerSweep(
                                                context = context,
                                                steps = listOf(0, 20, 40, 60, 80, 100),
                                                contentApl = 0.8f
                                            )
                                        }
                                        viewModel.setDisplayTestResults(lowAplResults + highAplResults)
                                        viewModel.setDisplayTestRunning(false)
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.PowerExperimentCompleted,
                                    mapOf("experiment_type" to "display", "test_type" to "advanced")
                                )
                                PowerAchievements.recordExperimentCompletion(context, "display")
                                    },
                                    onComplete = {
                                        showResultDialog = true
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isTestRunning,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.string(R.string.testing), fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.string(R.string.full_sweep), fontSize = 12.sp)
                    }
                }
            }
            
            // Results Preview
            testResults?.let { results ->
                    Spacer(modifier = Modifier.height(12.dp))
                    
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Beautiful header with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pw_last_results),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Show summary stats
                        val minPower = results.minOfOrNull { it.powerW } ?: 0.0
                        val maxPower = results.maxOfOrNull { it.powerW } ?: 0.0
                        val avgPower = results.map { it.powerW }.average()
                        
                        // Calculate battery percentage per hour for non-tech friendly data
                        val minBatteryPercent = PowerConsumptionAggregator.calculateBatteryPercentPerHour(minPower, context)
                        val maxBatteryPercent = PowerConsumptionAggregator.calculateBatteryPercentPerHour(maxPower, context)
                        val avgBatteryPercent = PowerConsumptionAggregator.calculateBatteryPercentPerHour(avgPower, context)
                        
                        // Beautiful summary header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.pw_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    
                    // Beautiful summary cards with proper spacing and non-tech friendly data
                    SummaryStatCard(
                        icon = "🔋",
                        title = stringResource(R.string.pw_disp_best),
                        value = buildString {
                            append(PowerConsumptionAggregator.formatPower(minPower))
                            minBatteryPercent?.let { append(" • " + context.string(R.string.pw_drains, it)) }
                        },
                        description = stringResource(R.string.pw_disp_best_d),
                        valueColor = Color(0xFF4CAF50) // Green for best
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SummaryStatCard(
                        icon = "⚡",
                        title = stringResource(R.string.pw_disp_worst),
                        value = buildString {
                            append(PowerConsumptionAggregator.formatPower(maxPower))
                            maxBatteryPercent?.let { append(" • " + context.string(R.string.pw_drains, it)) }
                        },
                        description = stringResource(R.string.pw_disp_worst_d),
                        valueColor = Color(0xFFFF5722) // Red/Orange for worst
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SummaryStatCard(
                        icon = "📊",
                        title = stringResource(R.string.pw_disp_avg),
                        value = buildString {
                            append(PowerConsumptionAggregator.formatPower(avgPower))
                            avgBatteryPercent?.let { append(" • " + context.string(R.string.pw_drains, it)) }
                        },
                        description = stringResource(R.string.pw_disp_avg_d),
                        valueColor = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Mini chart - only show if there's meaningful variation
                        val chartData = results.map { it.brightnessLevel.toFloat() to it.powerW.toFloat() }
                        val hasVariation = (chartData.maxOfOrNull { it.second } ?: 0f) - (chartData.minOfOrNull { it.second } ?: 0f) > 0.000001
                        if (hasVariation && chartData.isNotEmpty()) {
                            SimpleLineChart(
                                data = chartData,
                                xLabel = stringResource(R.string.pw_axis_brightness),
                                yLabel = stringResource(R.string.pw_axis_battery_used),
                                modifier = Modifier.height(180.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        // Beautiful header with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pw_sample_data),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        results.take(5).forEach { point ->
                            val brightnessDesc = when {
                                point.brightnessLevel <= 20 -> stringResource(R.string.pw_disp_very_dark)
                                point.brightnessLevel <= 40 -> stringResource(R.string.pw_disp_dark)
                                point.brightnessLevel <= 60 -> stringResource(R.string.pw_disp_medium)
                                point.brightnessLevel <= 80 -> stringResource(R.string.pw_disp_bright)
                                else -> stringResource(R.string.pw_disp_very_bright)
                            }
                            val powerValue = PowerConsumptionAggregator.formatPower(point.powerW)
                            
                            // Calculate battery percentage per hour for practical info
                            val batteryPercentPerHour = PowerConsumptionAggregator.calculateBatteryPercentPerHour(point.powerW, context)
                            
                            // Build value text with both watts and battery percentage
                            val valueText = buildString {
                                append(powerValue)
                                batteryPercentPerHour?.let {
                                    append(" • " + context.string(R.string.pw_drains, it))
                                }
                            }
                            
                            // Create meaningful subtitle
                            val subtitleText = buildString {
                                batteryPercentPerHour?.let {
                                    when {
                                        point.brightnessLevel <= 20 -> append(context.string(R.string.pw_disp_sub_20))
                                        point.brightnessLevel <= 40 -> append(context.string(R.string.pw_disp_sub_40))
                                        point.brightnessLevel <= 60 -> append(context.string(R.string.pw_disp_sub_60))
                                        point.brightnessLevel <= 80 -> append(context.string(R.string.pw_disp_sub_80))
                                        else -> append(context.string(R.string.pw_disp_sub_100))
                                    }
                                } ?: run {
                                    append(context.string(R.string.pw_disp_sub_none))
                                }
                            }
                            
                            SampleDataCard(
                                icon = "🔆",
                                title = stringResource(R.string.pw_disp_point_title, point.brightnessLevel, brightnessDesc),
                                value = valueText,
                                subtitle = subtitleText,
                                valueColor = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (results.size > 5) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                            Text(
                                    text = "  " + stringResource(R.string.pw_more_measurements, results.size - 5),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(12.dp)
                            )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Compact footer with summary and CSV button
                        TestResultsFooter(
                            count = results.size,
                            countLabel = if (results.size > 1) "measurements" else "measurement",
                            onViewCsv = openCsvDialog,
                            context = context,
                            activity = activity
                        )
                    }
                }
            }
        }
    }
    
    // Results Dialog
    if (showResultDialog && testResults != null) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pw_disp_result_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val results = testResults!!
                val minPower = results.minOfOrNull { it.powerW } ?: 0.0
                val maxPower = results.maxOfOrNull { it.powerW } ?: 0.0
                val avgPower = results.map { it.powerW }.average()
                
                Text(
                    text = buildString {
                        appendLine(stringResource(R.string.pw_disp_done, results.size))
                        appendLine()
                        appendLine(stringResource(R.string.pw_power_range))
                        appendLine(stringResource(R.string.pw_minimum, PowerConsumptionAggregator.formatPower(minPower)))
                        appendLine(stringResource(R.string.pw_maximum, PowerConsumptionAggregator.formatPower(maxPower)))
                        appendLine(stringResource(R.string.pw_average_line, PowerConsumptionAggregator.formatPower(avgPower)))
                        appendLine()
                        appendLine(stringResource(R.string.pw_disp_result_note))
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showResultDialog = false }
                ) {
                    Text(context.string(R.string.ok))
                }
            }
        )
    }
    
    // CSV Dialog
    if (showCsvDialog && testResults != null) {
        CsvPreviewDialog(
            title = stringResource(R.string.pw_disp_csv_title),
            headers = listOf(stringResource(R.string.pw_csv_timestamp), stringResource(R.string.pw_csv_brightness), stringResource(R.string.pw_csv_apl), stringResource(R.string.pw_csv_power)),
            headerDescriptions = mapOf(
                stringResource(R.string.pw_csv_timestamp) to stringResource(R.string.pw_csv_disp_timestamp_d),
                stringResource(R.string.pw_csv_brightness) to stringResource(R.string.pw_csv_brightness_d),
                stringResource(R.string.pw_csv_apl) to stringResource(R.string.pw_csv_apl_d),
                stringResource(R.string.pw_csv_power) to stringResource(R.string.pw_csv_disp_power_d)
            ),
            rows = testResults!!.map { point ->
                listOf(
                    PowerConsumptionAggregator.formatTimestamp(point.timestamp),
                    point.brightnessLevel.toString(),
                    "%.2f".format(point.apl),
                    PowerConsumptionAggregator.formatPower(point.powerW)
                )
            },
            onDismiss = { viewModel.setDisplayCsvDialogVisible(false) },
            onExport = {
                // Show ad before CSV export
                if (activity != null) {
                    InterstitialAdManager.showAdBeforeAction(
                        activity = activity,
                        actionName = "csv_export_display"
                    ) {
                        AnalyticsUtils.logEvent(AnalyticsEvent.PowerCsvExported, mapOf<String, Any?>(
                            "test_type" to "display",
                            "row_count" to testResults!!.size
                        ))
                        val csvHeaders = listOf("timestamp", "brightness_percent", "apl", "power_w")
                        val csvRows = testResults!!.map { point ->
                            listOf(
                                point.timestamp.toString(),
                                point.brightnessLevel.toString(),
                                point.apl.toString(),
                                point.powerW.toString()
                            )
                        }
                        
                        val uri = PowerConsumptionUtils.exportExperimentCSV(
                            context = context,
                            experimentName = "display_power_sweep",
                            headers = csvHeaders,
                            rows = csvRows
                        )
                        PowerAchievements.recordCsvExport(context)
                        
                        uri?.let {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, it)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.string(R.string.pw_share_display_data)))
                        }
                    }
                } else {
                    // Fallback if no activity
                    AnalyticsUtils.logEvent(AnalyticsEvent.PowerCsvExported, mapOf<String, Any?>(
                        "test_type" to "display",
                        "row_count" to testResults!!.size
                    ))
                    val csvHeaders = listOf("timestamp", "brightness_percent", "apl", "power_w")
                    val csvRows = testResults!!.map { point ->
                        listOf(
                            point.timestamp.toString(),
                            point.brightnessLevel.toString(),
                            point.apl.toString(),
                            point.powerW.toString()
                        )
                    }
                    
                    val uri = PowerConsumptionUtils.exportExperimentCSV(
                        context = context,
                        experimentName = "display_power_sweep",
                        headers = csvHeaders,
                        rows = csvRows
                    )
                    PowerAchievements.recordCsvExport(context)
                    
                    uri?.let {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, it)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.string(R.string.pw_share_display_data)))
                    }
                }
            }
        )
    }
    
    // Permission Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pw_perm_required_warn),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.pw_disp_perm_dialog),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.pw_disp_steps),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pw_disp_step1),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.pw_disp_step2),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.pw_disp_step3),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.pw_disp_step4),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pw_disp_gray),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pw_disp_gray1),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = stringResource(R.string.pw_disp_gray2),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = stringResource(R.string.pw_disp_gray3),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pw_disp_gray_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            // Request WRITE_SETTINGS permission
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    val appSettingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(appSettingsIntent)
                                    android.widget.Toast.makeText(
                                        context,
                                        context.string(R.string.pw_disp_perm_toast),
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.string(R.string.pw_disp_perm_fail),
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            showPermissionDialog = false
                        }
                    ) {
                        Text(context.string(R.string.open_settings))
                    }
                    TextButton(
                        onClick = { showPermissionDialog = false }
                    ) {
                        Text(context.string(R.string.cancel))
                    }
                }
            }
        )
    }
}

@Composable
private fun CpuMicrobenchSection(
    context: android.content.Context,
    viewModel: PowerConsumptionViewModel,
    activity: Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null
) {
    // Collect state from ViewModel - persists across activity recreation
    val isTestRunning by viewModel.isCpuTestRunning.collectAsState()
    val testResults by viewModel.cpuTestResults.collectAsState()
    var showResultDialog by remember { mutableStateOf(false) }
    
    // Use ViewModel state for CSV dialog - persists across activity recreation
    val showCsvDialog by viewModel.showCpuCsvDialog.collectAsState()
    
    // Create a stable callback reference for CSV dialog that persists across recompositions
    val openCsvDialog = remember {
        {
            android.util.Log.d("PowerStateDebug", "CpuMicrobenchSection - openCsvDialog callback invoked")
            viewModel.setCpuCsvDialogVisible(true)
            android.util.Log.d("PowerStateDebug", "CpuMicrobenchSection - showCsvDialog set to true via ViewModel")
            Unit // Explicitly return Unit
        }
    }
    
    // Detect dark mode
    val isDarkMode = MaterialTheme.colorScheme.background == DesignSystemColors.Dark
    val cardBackgroundColor = if (isDarkMode) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surface
    val headerTextColor = if (isDarkMode) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardBackgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            DesignSystemColors.NeonGreen.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = stringResource(R.string.pw_cpu_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pw_cpu_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = headerTextColor,
                    modifier = Modifier.weight(1f)
                )
                if (onItemAIClick != null) {
                    IconButton(
                        onClick = {
                            val cpuResults = testResults
                            val resultsText = if (cpuResults != null && cpuResults.isNotEmpty()) {
                                cpuResults.joinToString("\n") { result ->
                                    "CPU Utilization ${result.targetUtilPercent}%: ${PowerConsumptionAggregator.formatPower(result.deltaPowerW)} delta power (observed: ${result.observedUtilPercent}%)"
                                }
                            } else {
                                "No test results yet. Run a CPU test to see power consumption data."
                            }
                            val content = """
CPU Performance Power Test Results:
$resultsText

This shows how CPU processing speed affects battery consumption.
                            """.trimIndent()
                            onItemAIClick("CPU Performance Power Test", content)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = stringResource(R.string.pw_cd_ai_cpu),
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.pw_cpu_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Test Button
            Button(
                onClick = {
                    val activity = context as? Activity
                    // Set state in ViewModel immediately before showing ad
                    viewModel.setCpuTestRunning(true)
                    
                    AnalyticsUtils.logEvent(AnalyticsEvent.PowerTestRunLevelsClicked)
                    showAdBeforeTestAction(
                        activity = activity,
                        actionName = "cpu_run_levels",
                        action = {
                            viewModel.executeTestAction(
                                actionName = "cpu_run_levels",
                                action = {
                                    viewModel.setCpuTestRunning(true)
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.PowerExperimentStarted,
                            mapOf("experiment_type" to "cpu")
                        )
                                    val results = withContext(Dispatchers.Default) {
                            PowerConsumptionUtils.runCpuMicrobench(
                                context = context,
                                levels = listOf(20, 40, 60, 80, 100),
                                burstMs = 500
                            )
                        }
                                    viewModel.setCpuTestResults(results)
                                    viewModel.setCpuTestRunning(false)
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.PowerExperimentCompleted,
                            mapOf("experiment_type" to "cpu")
                        )
                        PowerAchievements.recordExperimentCompletion(context, "cpu")
                        // Track meaningful interaction for review prompt (after positive experiment experience)
                        activity?.let {
                            ReviewPromptManager.trackMeaningfulInteraction(it, "power_experiment_cpu_completed")
                        }
                                },
                                onComplete = {
                                    showResultDialog = true
                    }
                            )
                        }
                    )
                },
                enabled = !isTestRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isTestRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = DesignSystemColors.Dark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.string(R.string.running_cpu_tests))
                } else {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.string(R.string.run_levels))
                }
            }
            
            // Results Preview
            testResults?.let { results ->
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Beautiful header with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pw_last_results),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    
                        // Show summary stats - only for non-zero results
                        val nonZeroResults = results.filter { it.deltaPowerW > 0.000001 } // Filter out near-zero values
                        val minDelta = nonZeroResults.minOfOrNull { it.deltaPowerW } ?: 0.0
                        val maxDelta = results.maxOfOrNull { it.deltaPowerW } ?: 0.0
                        val avgDelta = nonZeroResults.takeIf { it.isNotEmpty() }?.map { it.deltaPowerW }?.average() ?: 0.0
                        
                        // Beautiful summary header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.pw_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (nonZeroResults.isNotEmpty()) {
                            // Calculate battery percentage per hour for summary cards
                            val minBatteryPercent = PowerConsumptionAggregator.calculateBatteryPercentPerHour(minDelta, context)
                            val maxBatteryPercent = PowerConsumptionAggregator.calculateBatteryPercentPerHour(maxDelta, context)
                            val avgBatteryPercent = PowerConsumptionAggregator.calculateBatteryPercentPerHour(avgDelta, context)
                            
                            // Beautiful summary cards with proper spacing
                            SummaryStatCard(
                                icon = "🔋",
                                title = stringResource(R.string.pw_cpu_lowest),
                                value = buildString {
                                    append(PowerConsumptionAggregator.formatPower(minDelta))
                                    minBatteryPercent?.let { 
                                        append(" • " + context.string(R.string.pw_drains, it))
                                    }
                                },
                                description = stringResource(R.string.pw_cpu_lowest_d),
                                valueColor = Color(0xFF4CAF50) // Green for best
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SummaryStatCard(
                                icon = "⚡",
                                title = stringResource(R.string.pw_cpu_highest),
                                value = buildString {
                                    append(PowerConsumptionAggregator.formatPower(maxDelta))
                                    maxBatteryPercent?.let { 
                                        append(" • " + context.string(R.string.pw_drains, it))
                                    }
                                },
                                description = stringResource(R.string.pw_cpu_highest_d),
                                valueColor = Color(0xFFFF5722) // Red/Orange for worst
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SummaryStatCard(
                                icon = "📊",
                                title = stringResource(R.string.pw_cpu_avg),
                                value = buildString {
                                    append(PowerConsumptionAggregator.formatPower(avgDelta))
                                    avgBatteryPercent?.let { 
                                        append(" • " + context.string(R.string.pw_drains, it))
                                    }
                                },
                                description = stringResource(R.string.pw_cpu_avg_d),
                                valueColor = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                        Text(
                                        text = "ℹ️",
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                                        text = stringResource(R.string.pw_cpu_no_increase),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Mini chart - only show if there's meaningful variation
                        val chartData = results.map { it.targetUtilPercent.toFloat() to it.deltaPowerW.toFloat() }
                        val hasVariation = chartData.any { it.second > 0.000001 } && 
                                          (chartData.maxOfOrNull { it.second } ?: 0f) - (chartData.minOfOrNull { it.second } ?: 0f) > 0.000001
                        if (hasVariation && chartData.isNotEmpty()) {
                            SimpleLineChart(
                                data = chartData,
                                xLabel = stringResource(R.string.pw_axis_cpu),
                                yLabel = stringResource(R.string.pw_extra_battery_used),
                                modifier = Modifier.height(180.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        // Show all data points with beautiful cards
                        // Beautiful header with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pw_test_results),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Add explanation about what the test does
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.pw_cpu_explain),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        results.take(5).forEach { point ->
                            val hasPowerChange = point.deltaPowerW > 0.000001
                            
                            // Calculate battery percentage per hour for practical info
                            val batteryPercentPerHour = if (hasPowerChange) {
                                PowerConsumptionAggregator.calculateBatteryPercentPerHour(point.deltaPowerW, context)
                            } else null
                            
                            // Build value text with both watts and battery percentage
                            val powerText = if (hasPowerChange) {
                                val powerValue = PowerConsumptionAggregator.formatPower(point.deltaPowerW)
                                buildString {
                                    append(context.string(R.string.pw_cpu_extra, powerValue))
                                    // "extra battery" is part of pw_cpu_extra
                                    batteryPercentPerHour?.let {
                                        append(" • " + context.string(R.string.pw_drains, it))
                                    }
                                }
                            } else {
                                context.string(R.string.pw_cpu_no_change)
                            }
                            
                            // Create meaningful subtitle based on workload level
                            val subtitle = if (!hasPowerChange) {
                                context.string(R.string.pw_cpu_already)
                            } else {
                                buildString {
                                    when {
                                        point.targetUtilPercent <= 20 -> append(context.string(R.string.pw_cpu_sub_20))
                                        point.targetUtilPercent <= 40 -> append(context.string(R.string.pw_cpu_sub_40))
                                        point.targetUtilPercent <= 60 -> append(context.string(R.string.pw_cpu_sub_60))
                                        point.targetUtilPercent <= 80 -> append(context.string(R.string.pw_cpu_sub_80))
                                        else -> append(context.string(R.string.pw_cpu_sub_100))
                                    }
                                }
                            }
                            
                            SampleDataCard(
                                icon = "⚙️",
                                title = stringResource(R.string.pw_cpu_point_title, point.targetUtilPercent),
                                value = powerText,
                                subtitle = subtitle,
                                valueColor = if (hasPowerChange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (results.size > 5) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                            Text(
                                    text = "  " + stringResource(R.string.pw_more_levels, results.size - 5),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(12.dp)
                            )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Compact footer with summary and CSV button
                        TestResultsFooter(
                            count = results.size,
                            countLabel = if (results.size > 1) "levels tested" else "level tested",
                            onViewCsv = openCsvDialog,
                            context = context,
                            activity = activity
                        )
                    }
                }
            }
        }
    }
    
    // Results Dialog
    if (showResultDialog && testResults != null) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pw_cpu_result_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val results = testResults!!
                val maxDelta = results.maxOfOrNull { it.deltaPowerW } ?: 0.0
                val avgDelta = results.map { it.deltaPowerW }.average()
                
                Column {
                Text(
                    text = buildString {
                            appendLine(stringResource(R.string.pw_cpu_done, results.size))
                        appendLine()
                            appendLine(stringResource(R.string.pw_cpu_impact))
                            appendLine(stringResource(R.string.pw_cpu_highest_line, PowerConsumptionAggregator.formatPower(maxDelta)))
                            appendLine(stringResource(R.string.pw_cpu_avg_line, PowerConsumptionAggregator.formatPower(avgDelta)))
                        appendLine()
                            appendLine(stringResource(R.string.pw_what_means))
                            appendLine(stringResource(R.string.pw_cpu_means))
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pw_cpu_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showResultDialog = false }
                ) {
                    Text(context.string(R.string.ok))
                }
            }
        )
    }
    
    // CSV Dialog
    if (showCsvDialog && testResults != null) {
        CsvPreviewDialog(
            title = stringResource(R.string.pw_cpu_csv_title),
            headers = listOf(stringResource(R.string.pw_time), stringResource(R.string.pw_csv_cpu_workload), stringResource(R.string.pw_extra_battery_used), stringResource(R.string.pw_csv_cpu_status)),
            headerDescriptions = mapOf(
                stringResource(R.string.pw_time) to stringResource(R.string.pw_csv_cpu_time_d),
                stringResource(R.string.pw_csv_cpu_workload) to stringResource(R.string.pw_csv_cpu_workload_d),
                stringResource(R.string.pw_extra_battery_used) to stringResource(R.string.pw_csv_cpu_extra_d),
                stringResource(R.string.pw_csv_cpu_status) to stringResource(R.string.pw_csv_cpu_status_d)
            ),
            rows = testResults!!.map { point ->
                // Show only what matters - the workload we tried and the battery impact
                // Removed "Actual Speed" as it's confusing and not very useful for users
                listOf(
                    PowerConsumptionAggregator.formatTimestamp(point.timestamp),
                    "${point.targetUtilPercent}%",
                    if (point.deltaPowerW > 0.000001) PowerConsumptionAggregator.formatPower(point.deltaPowerW) else stringResource(R.string.pw_cpu_no_change_detected),
                    point.freqSummary.replace("cores @", "cores at")
                )
            },
            onDismiss = { viewModel.setCpuCsvDialogVisible(false) },
            onExport = {
                // Show ad before CSV export
                if (activity != null) {
                    InterstitialAdManager.showAdBeforeAction(
                        activity = activity,
                        actionName = "csv_export_cpu"
                    ) {
                        AnalyticsUtils.logEvent(AnalyticsEvent.PowerCsvExported, mapOf<String, Any?>(
                            "test_type" to "cpu",
                            "row_count" to testResults!!.size
                        ))
                        val csvHeaders = listOf("timestamp", "workload_tried_percent", "extra_battery_used_watts", "processor_status", "observed_response_percent")
                        val csvRows = testResults!!.map { point ->
                            // Include observedUtilPercent in CSV for technical users, but label it clearly
                            listOf(
                                point.timestamp.toString(),
                                point.targetUtilPercent.toString(),
                                if (point.deltaPowerW > 0.000001) point.deltaPowerW.toString() else "0.0",
                                point.freqSummary.replace("cores @", "cores at"),
                                if (point.observedUtilPercent > 0) point.observedUtilPercent.toString() else "not_detected"
                            )
                        }
                        
                        val uri = PowerConsumptionUtils.exportExperimentCSV(
                            context = context,
                            experimentName = "cpu_microbench",
                            headers = csvHeaders,
                            rows = csvRows
                        )
                        
                        // Track CSV export
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.PowerCsvExported,
                            mapOf("experiment_type" to "cpu", "rows" to csvRows.size)
                        )
                        PowerAchievements.recordCsvExport(context)
                        
                        uri?.let {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, it)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.string(R.string.pw_share_cpu_data)))
                        }
                    }
                } else {
                    // Fallback if no activity
                    AnalyticsUtils.logEvent(AnalyticsEvent.PowerCsvExported, mapOf<String, Any?>(
                        "test_type" to "cpu",
                        "row_count" to testResults!!.size
                    ))
                    val csvHeaders = listOf("timestamp", "workload_tried_percent", "extra_battery_used_watts", "processor_status", "observed_response_percent")
                    val csvRows = testResults!!.map { point ->
                        listOf(
                            point.timestamp.toString(),
                            point.targetUtilPercent.toString(),
                            if (point.deltaPowerW > 0.000001) point.deltaPowerW.toString() else "0.0",
                            point.freqSummary.replace("cores @", "cores at"),
                            if (point.observedUtilPercent > 0) point.observedUtilPercent.toString() else "not_detected"
                        )
                    }
                    
                    val uri = PowerConsumptionUtils.exportExperimentCSV(
                        context = context,
                        experimentName = "cpu_microbench",
                        headers = csvHeaders,
                        rows = csvRows
                    )
                    
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.PowerCsvExported,
                        mapOf("experiment_type" to "cpu", "rows" to csvRows.size)
                    )
                    PowerAchievements.recordCsvExport(context)
                    
                    uri?.let {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, it)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.string(R.string.pw_share_cpu_data)))
                    }
                }
            }
        )
    }
}

@Composable
private fun NetworkRssiSamplingSection(
    context: android.content.Context,
    viewModel: PowerConsumptionViewModel,
    activity: Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null
) {
    // Collect state from ViewModel - persists across activity recreation
    val isTestRunning by viewModel.isNetworkTestRunning.collectAsState()
    val testResults by viewModel.networkTestResults.collectAsState()
    val countdown by viewModel.networkCountdown.collectAsState()
    var showResultDialog by remember { mutableStateOf(false) }
    
    // Use ViewModel state for CSV dialog - persists across activity recreation
    val showCsvDialog by viewModel.showNetworkCsvDialog.collectAsState()
    
    // Create a stable callback reference for CSV dialog that persists across recompositions
    val openCsvDialog = remember {
        {
            android.util.Log.d("PowerStateDebug", "NetworkRssiSamplingSection - openCsvDialog callback invoked")
            viewModel.setNetworkCsvDialogVisible(true)
            android.util.Log.d("PowerStateDebug", "NetworkRssiSamplingSection - showCsvDialog set to true via ViewModel")
            Unit // Explicitly return Unit
        }
    }
    
    // Detect dark mode
    val isDarkMode = MaterialTheme.colorScheme.background == DesignSystemColors.Dark
    val cardBackgroundColor = if (isDarkMode) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surface
    val headerTextColor = if (isDarkMode) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardBackgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            DesignSystemColors.NeonGreen.copy(alpha = 0.3f)
        )
    ) {
    Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.SignalWifi4Bar,
                    contentDescription = stringResource(R.string.pw_net_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pw_net_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = headerTextColor,
                    modifier = Modifier.weight(1f)
                )
                if (onItemAIClick != null) {
                    IconButton(
                        onClick = {
                            val testResults = testResults
                            val resultsText = if (testResults != null && testResults.isNotEmpty()) {
                                val wifiRssiValues = testResults.mapNotNull { it.wifiRssiDbm }
                                val cellRssiValues = testResults.mapNotNull { it.cellDbm }
                                val powerValues = testResults.map { it.powerW }
                                
                                val wifiStats = if (wifiRssiValues.isNotEmpty()) {
                                    val minRssi = wifiRssiValues.minOrNull() ?: 0
                                    val maxRssi = wifiRssiValues.maxOrNull() ?: 0
                                    val medianRssi = wifiRssiValues.sorted()[wifiRssiValues.size / 2]
                                    "WiFi Signal: ${minRssi} to ${maxRssi} dBm (median: ${medianRssi} dBm)"
                                } else "WiFi Signal: No data"
                                
                                val cellStats = if (cellRssiValues.isNotEmpty()) {
                                    val minRssi = cellRssiValues.minOrNull() ?: 0
                                    val maxRssi = cellRssiValues.maxOrNull() ?: 0
                                    val medianRssi = cellRssiValues.sorted()[cellRssiValues.size / 2]
                                    "Cellular Signal: ${minRssi} to ${maxRssi} dBm (median: ${medianRssi} dBm)"
                                } else "Cellular Signal: No data"
                                
                                val powerStats = if (powerValues.isNotEmpty()) {
                                    val minPower = powerValues.minOrNull() ?: 0.0
                                    val maxPower = powerValues.maxOrNull() ?: 0.0
                                    val avgPower = powerValues.average()
                                    "Power Range: ${PowerConsumptionAggregator.formatPower(minPower)} to ${PowerConsumptionAggregator.formatPower(maxPower)} (avg: ${PowerConsumptionAggregator.formatPower(avgPower)})"
                                } else "Power: No data"
                                
                                """
Network RSSI Sampling Test Results:
$wifiStats
$cellStats
$powerStats

Total Samples: ${testResults.size}
Duration: 60 seconds
                                """.trimIndent()
                            } else {
                                "No test results yet. Run a network signal test to see how signal strength affects battery consumption."
                            }
                            val content = """
Network Signal Strength & Power Consumption Test:
$resultsText

This shows how WiFi and cellular signal strength affects battery power consumption.
                            """.trimIndent()
                            onItemAIClick("Network Signal Strength Test", content)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = stringResource(R.string.pw_cd_ai_net),
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.pw_net_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Test Button
            Button(
                onClick = {
                    val activity = context as? Activity
                    // Set state in ViewModel immediately before showing ad
                    viewModel.setNetworkTestRunning(true)
                    viewModel.setNetworkCountdown(60)
                    
                    AnalyticsUtils.logEvent(AnalyticsEvent.PowerTestSamplingClicked)
                    showAdBeforeTestAction(
                        activity = activity,
                        actionName = "network_60s_sampling",
                        action = {
                            viewModel.executeTestAction(
                                actionName = "network_60s_sampling",
                                action = {
                                    viewModel.setNetworkTestRunning(true)
                                    viewModel.setNetworkCountdown(60)
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.PowerExperimentStarted,
                            mapOf("experiment_type" to "network")
                        )
                                    
                                    // Start countdown in viewModelScope
                                    var countdownJob: Job? = null
                                    coroutineScope {
                                        countdownJob = launch {
                                            var remaining = 60
                                            while (remaining > 0 && viewModel.isNetworkTestRunning.value) {
                                delay(1000)
                                                remaining--
                                                viewModel.setNetworkCountdown(remaining)
                            }
                        }
                        
                                        // Run the test in parallel with countdown
                                        val results = withContext(Dispatchers.IO) {
                            PowerConsumptionUtils.runNetworkRssiSampling(
                                context = context,
                                durationSec = 60,
                                periodMs = 2000
                            )
                        }
                        
                                        countdownJob?.cancel()
                                        
                                        viewModel.setNetworkTestResults(results)
                                    }
                                    viewModel.setNetworkTestRunning(false)
                                    viewModel.setNetworkCountdown(0)
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.PowerExperimentCompleted,
                            mapOf("experiment_type" to "network")
                        )
                        PowerAchievements.recordExperimentCompletion(context, "network")
                        // Track meaningful interaction for review prompt (after positive experiment experience)
                        activity?.let {
                            ReviewPromptManager.trackMeaningfulInteraction(it, "power_experiment_network_completed")
                        }
                                },
                                onComplete = {
                                    showResultDialog = true
                    }
                            )
                        }
                    )
                },
                enabled = !isTestRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isTestRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = DesignSystemColors.Dark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.string(R.string.sampling_countdown, countdown))
                } else {
                    Icon(
                        imageVector = Icons.Default.SignalWifi4Bar,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.string(R.string.start_60s_sampling))
                }
            }
            
            // Results Preview
            testResults?.let { results ->
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Beautiful header with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pw_net_last),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Show summary stats
                        val wifiRssiValues = results.mapNotNull { it.wifiRssiDbm }
                        val powerValues = results.map { it.powerW }
                        val minPower = powerValues.minOrNull() ?: 0.0
                        val maxPower = powerValues.maxOrNull() ?: 0.0
                        val avgPower = powerValues.average()
                        
                        // Beautiful summary header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.pw_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // WiFi Signal Card (if available)
                        if (wifiRssiValues.isNotEmpty()) {
                            val medianRssi = wifiRssiValues.sorted()[wifiRssiValues.size / 2]
                            val signalStrength = when {
                                medianRssi >= -50 -> "Excellent"
                                medianRssi >= -60 -> "Very Good"
                                medianRssi >= -70 -> "Good"
                                medianRssi >= -80 -> "Fair"
                                else -> "Weak"
                            }
                            val signalColor = when {
                                medianRssi >= -50 -> Color(0xFF4CAF50) // Green
                                medianRssi >= -60 -> Color(0xFF8BC34A) // Light Green
                                medianRssi >= -70 -> Color(0xFFFFC107) // Amber
                                medianRssi >= -80 -> Color(0xFFFF9800) // Orange
                                else -> Color(0xFFFF5722) // Red
                            }
                            SummaryStatCard(
                                icon = "📶",
                                title = stringResource(R.string.pw_net_wifi_signal),
                                value = PowerStrings.signal(context, signalStrength),
                                description = stringResource(R.string.pw_net_wifi_signal_d),
                                valueColor = signalColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        // Beautiful summary cards with proper spacing
                        val minBatteryPercent = PowerConsumptionAggregator.calculateBatteryPercentPerHour(minPower, context)
                        val maxBatteryPercent = PowerConsumptionAggregator.calculateBatteryPercentPerHour(maxPower, context)
                        val avgBatteryPercent = PowerConsumptionAggregator.calculateBatteryPercentPerHour(avgPower, context)
                        
                        SummaryStatCard(
                            icon = "🔋",
                            title = stringResource(R.string.pw_net_lowest),
                            value = buildString {
                                append(PowerConsumptionAggregator.formatPower(minPower))
                                minBatteryPercent?.let { append(" • " + context.string(R.string.pw_drains, it)) }
                            },
                            description = stringResource(R.string.pw_net_lowest_d),
                            valueColor = Color(0xFF4CAF50) // Green for best
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SummaryStatCard(
                            icon = "⚡",
                            title = stringResource(R.string.pw_net_highest),
                            value = buildString {
                                append(PowerConsumptionAggregator.formatPower(maxPower))
                                maxBatteryPercent?.let { append(" • " + context.string(R.string.pw_drains, it)) }
                            },
                            description = stringResource(R.string.pw_net_highest_d),
                            valueColor = Color(0xFFFF5722) // Red/Orange for worst
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SummaryStatCard(
                            icon = "📊",
                            title = stringResource(R.string.pw_net_avg),
                            value = buildString {
                                append(PowerConsumptionAggregator.formatPower(avgPower))
                                avgBatteryPercent?.let { append(" • " + context.string(R.string.pw_drains, it)) }
                            },
                            description = stringResource(R.string.pw_net_avg_d),
                            valueColor = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Mini chart - only show if there's meaningful variation
                        val chartData = results.map { it.timeSeconds.toFloat() to it.powerW.toFloat() }
                        val hasVariation = (chartData.maxOfOrNull { it.second } ?: 0f) - (chartData.minOfOrNull { it.second } ?: 0f) > 0.000001
                        if (hasVariation && chartData.isNotEmpty()) {
                            SimpleLineChart(
                                data = chartData,
                                xLabel = stringResource(R.string.pw_time),
                                yLabel = stringResource(R.string.pw_axis_battery_used),
                                modifier = Modifier.height(180.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        // Beautiful header with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pw_sample_data),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        results.take(5).forEach { point ->
                            val signalInfo = point.wifiRssiDbm?.let { 
                                val strength = when {
                                    it >= -50 -> "Excellent"
                                    it >= -60 -> "Very good"
                                    it >= -70 -> "Good"
                                    it >= -80 -> "Fair"
                                    else -> "Weak"
                                }
                                strength
                            } ?: "No WiFi"
                            val timeDesc = when {
                                point.timeSeconds < 10 -> stringResource(R.string.pw_net_start)
                                point.timeSeconds < 30 -> stringResource(R.string.pw_net_early)
                                point.timeSeconds < 50 -> stringResource(R.string.pw_net_mid)
                                else -> stringResource(R.string.pw_net_end)
                            }
                            
                            // Calculate battery percentage per hour
                            val batteryPercentPerHour = PowerConsumptionAggregator.calculateBatteryPercentPerHour(point.powerW, context)
                            
                            // Build value text with both watts and battery percentage
                            val powerValue = buildString {
                                append(PowerConsumptionAggregator.formatPower(point.powerW))
                                batteryPercentPerHour?.let {
                                    append(" • " + context.string(R.string.pw_drains, it))
                                }
                            }
                            
                            // Create meaningful subtitle based on signal strength
                            val subtitle = buildString {
                                append(context.string(R.string.pw_net_sub, PowerStrings.signal(context, signalInfo)))
                                when {
                                    signalInfo == "Excellent" || signalInfo == "Very good" -> append(" • " + context.string(R.string.pw_net_sub_strong))
                                    signalInfo == "Good" -> append(" • " + context.string(R.string.pw_net_sub_good))
                                    signalInfo == "Fair" -> append(" • " + context.string(R.string.pw_net_sub_fair))
                                    signalInfo == "Weak" -> append(" • " + context.string(R.string.pw_net_sub_weak))
                                    else -> append(" • " + context.string(R.string.pw_net_sub_none))
                                }
                            }
                            
                            val signalColor = point.wifiRssiDbm?.let {
                                when {
                                    it >= -50 -> Color(0xFF4CAF50) // Green
                                    it >= -60 -> Color(0xFF8BC34A) // Light Green
                                    it >= -70 -> Color(0xFFFFC107) // Amber
                                    it >= -80 -> Color(0xFFFF9800) // Orange
                                    else -> Color(0xFFFF5722) // Red
                                }
                            } ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            
                            SampleDataCard(
                                icon = "📶",
                                title = stringResource(R.string.pw_net_point_title, point.timeSeconds, timeDesc),
                                value = powerValue,
                                subtitle = subtitle,
                                valueColor = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (results.size > 5) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "  " + stringResource(R.string.pw_more_samples, results.size - 5),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Compact footer with summary and CSV button
                        TestResultsFooter(
                            count = results.size,
                            countLabel = if (results.size > 1) "samples" else "sample",
                            onViewCsv = openCsvDialog,
                            context = context,
                            activity = activity
                        )
                    }
                }
            }
        }
    }
    
    // Results Dialog
    if (showResultDialog && testResults != null) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pw_net_result_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val results = testResults!!
                val wifiRssiValues = results.mapNotNull { it.wifiRssiDbm }
                val powerValues = results.map { it.powerW }
                
                Text(
                    text = buildString {
                        appendLine(stringResource(R.string.pw_net_done, results.size))
                        appendLine()
                        if (wifiRssiValues.isNotEmpty()) {
                            val minRssi = wifiRssiValues.minOrNull()!!
                            val maxRssi = wifiRssiValues.maxOrNull()!!
                            val medianRssi = wifiRssiValues.sorted()[wifiRssiValues.size/2]
                            val minStrength = when {
                                minRssi >= -50 -> "Excellent"
                                minRssi >= -60 -> "Very Good"
                                minRssi >= -70 -> "Good"
                                minRssi >= -80 -> "Fair"
                                else -> "Weak"
                            }
                            val maxStrength = when {
                                maxRssi >= -50 -> "Excellent"
                                maxRssi >= -60 -> "Very Good"
                                maxRssi >= -70 -> "Good"
                                maxRssi >= -80 -> "Fair"
                                else -> "Weak"
                            }
                            appendLine(stringResource(R.string.pw_net_strength))
                            appendLine(stringResource(R.string.pw_net_weakest, PowerStrings.signal(context, minStrength), "$minRssi dBm"))
                            appendLine(stringResource(R.string.pw_net_strongest, PowerStrings.signal(context, maxStrength), "$maxRssi dBm"))
                            appendLine(stringResource(R.string.pw_average_line, "$medianRssi dBm"))
                            appendLine()
                        }
                        appendLine(stringResource(R.string.pw_power_range_battery))
                        appendLine(stringResource(R.string.pw_min_line, PowerConsumptionAggregator.formatPower(powerValues.minOrNull() ?: 0.0)))
                        appendLine(stringResource(R.string.pw_max_line, PowerConsumptionAggregator.formatPower(powerValues.maxOrNull() ?: 0.0)))
                        appendLine(stringResource(R.string.pw_median_line, PowerConsumptionAggregator.formatPower(powerValues.sorted()[powerValues.size/2])))
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showResultDialog = false }
                ) {
                    Text(context.string(R.string.ok))
                }
            }
        )
    }
    
    // CSV Dialog
    if (showCsvDialog && testResults != null) {
        CsvPreviewDialog(
            title = stringResource(R.string.pw_net_csv_title),
            headers = listOf(stringResource(R.string.pw_csv_timestamp), stringResource(R.string.pw_csv_net_time), stringResource(R.string.pw_csv_net_wifi), stringResource(R.string.pw_csv_net_cell), stringResource(R.string.pw_csv_power)),
            headerDescriptions = mapOf(
                stringResource(R.string.pw_csv_timestamp) to stringResource(R.string.pw_csv_net_timestamp_d),
                stringResource(R.string.pw_csv_net_time) to stringResource(R.string.pw_csv_net_time_d),
                stringResource(R.string.pw_csv_net_wifi) to stringResource(R.string.pw_csv_net_wifi_d),
                stringResource(R.string.pw_csv_net_cell) to stringResource(R.string.pw_csv_net_cell_d),
                stringResource(R.string.pw_csv_power) to stringResource(R.string.pw_csv_net_power_d)
            ),
            rows = testResults!!.map { point ->
                listOf(
                    PowerConsumptionAggregator.formatTimestamp(point.timestamp),
                    point.timeSeconds.toString(),
                    point.wifiRssiDbm?.toString() ?: stringResource(R.string.pw_na),
                    point.cellDbm?.toString() ?: stringResource(R.string.pw_na),
                    PowerConsumptionAggregator.formatPower(point.powerW)
                )
            },
            onDismiss = { viewModel.setNetworkCsvDialogVisible(false) },
            onExport = {
                // Show ad before CSV export
                if (activity != null) {
                    InterstitialAdManager.showAdBeforeAction(
                        activity = activity,
                        actionName = "csv_export_network"
                    ) {
                        AnalyticsUtils.logEvent(AnalyticsEvent.PowerCsvExported, mapOf<String, Any?>(
                            "test_type" to "network",
                            "row_count" to testResults!!.size
                        ))
                        val csvHeaders = listOf("timestamp", "time_seconds", "wifi_rssi_dbm", "cell_dbm", "power_w")
                        val csvRows = testResults!!.map { point ->
                            listOf(
                                point.timestamp.toString(),
                                point.timeSeconds.toString(),
                                point.wifiRssiDbm?.toString() ?: "",
                                point.cellDbm?.toString() ?: "",
                                point.powerW.toString()
                            )
                        }
                        
                        val uri = PowerConsumptionUtils.exportExperimentCSV(
                            context = context,
                            experimentName = "network_rssi_sampling",
                            headers = csvHeaders,
                            rows = csvRows
                        )
                        
                        // Track CSV export
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.PowerCsvExported,
                            mapOf("experiment_type" to "network", "rows" to csvRows.size)
                        )
                        PowerAchievements.recordCsvExport(context)
                        
                        uri?.let {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, it)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.string(R.string.pw_share_net_data)))
                        }
                    }
                } else {
                    // Fallback if no activity
                    AnalyticsUtils.logEvent(AnalyticsEvent.PowerCsvExported, mapOf<String, Any?>(
                        "test_type" to "network",
                        "row_count" to testResults!!.size
                    ))
                    val csvHeaders = listOf("timestamp", "time_seconds", "wifi_rssi_dbm", "cell_dbm", "power_w")
                    val csvRows = testResults!!.map { point ->
                        listOf(
                            point.timestamp.toString(),
                            point.timeSeconds.toString(),
                            point.wifiRssiDbm?.toString() ?: "",
                            point.cellDbm?.toString() ?: "",
                            point.powerW.toString()
                        )
                    }
                    
                    val uri = PowerConsumptionUtils.exportExperimentCSV(
                        context = context,
                        experimentName = "network_rssi_sampling",
                        headers = csvHeaders,
                        rows = csvRows
                    )
                    
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.PowerCsvExported,
                        mapOf("experiment_type" to "network", "rows" to csvRows.size)
                    )
                    PowerAchievements.recordCsvExport(context)
                    
                    uri?.let {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, it)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.string(R.string.pw_share_net_data)))
                    }
                }
            }
        )
    }
}

// ===== LIGHTWEIGHT CHART COMPOSABLES =====

@Composable
private fun SimpleLineChart(
    data: List<Pair<Float, Float>>,
    xLabel: String,
    yLabel: String,
    modifier: Modifier = Modifier
) {
    // Validate and filter data
    val validData = remember(data) {
        data.filter { 
            it.first.isFinite() && it.second.isFinite() && 
            !it.first.isNaN() && !it.second.isNaN()
        }
    }
    
    if (validData.isEmpty()) {
        // Show fallback message when no valid data
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(150.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pw_chart_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(R.string.pw_chart_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
        return
    }
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    
    // Calculate data bounds
    val minX = validData.minOfOrNull { it.first } ?: 0f
    val maxX = validData.maxOfOrNull { it.first } ?: 1f
    val minY = validData.minOfOrNull { it.second } ?: 0f
    val maxY = validData.maxOfOrNull { it.second } ?: 1f
    
    val rangeX = maxX - minX
    val rangeY = maxY - minY
    
    // Check if there's meaningful variation
    val hasVariation = rangeX > 0.000001f && rangeY > 0.000001f
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 16.dp)  // Reduced padding to prevent overflow
    ) {
        if (!hasVariation) {
            // Show message when all values are the same
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pw_chart_constant),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                // More padding on sides since labels are now at top/bottom
                val leftPadding = 20.dp.toPx()
                val rightPadding = 20.dp.toPx()
                val topPadding = 50.dp.toPx()  // Space for Y-axis label at top
                val bottomPadding = 50.dp.toPx()  // Space for X-axis labels
                
                val chartWidth = size.width - leftPadding - rightPadding
                val chartHeight = size.height - topPadding - bottomPadding
                
                if (chartWidth <= 0 || chartHeight <= 0) return@Canvas
                
                // Draw grid lines (horizontal) - fewer lines for cleaner look
                val gridLines = 3
                for (i in 0..gridLines) {
                    val yPos = topPadding + (chartHeight / gridLines) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(leftPadding, yPos),
                        end = Offset(size.width - rightPadding, yPos),
                        strokeWidth = 0.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(4f, 4f)
                        )
                    )
                }
                
                // Draw grid lines (vertical) - fewer lines for cleaner look
                for (i in 0..gridLines) {
                    val xPos = leftPadding + (chartWidth / gridLines) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(xPos, topPadding),
                        end = Offset(xPos, size.height - bottomPadding),
                        strokeWidth = 0.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(4f, 4f)
                        )
                    )
                }
                
                // Draw axes with better visibility
                drawLine(
                    color = axisColor,
                    start = Offset(leftPadding, topPadding),
                    end = Offset(leftPadding, size.height - bottomPadding),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = axisColor,
                    start = Offset(leftPadding, size.height - bottomPadding),
                    end = Offset(size.width - rightPadding, size.height - bottomPadding),
                    strokeWidth = 2.dp.toPx()
                )
                
                // Draw data points and lines
                val points = validData.map { (x, y) ->
                    val screenX = leftPadding + ((x - minX) / rangeX) * chartWidth
                    val screenY = size.height - bottomPadding - ((y - minY) / rangeY) * chartHeight
                    Offset(screenX, screenY)
                }
                
                // Draw line connecting points - smoother and more visible
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = primaryColor,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                
                // Draw data points with better visibility
                points.forEach { point ->
                    // Outer circle for better visibility
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.25f),
                        radius = 5.5.dp.toPx(),
                        center = point
                    )
                    // Inner circle
                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = point
                    )
                    // Center dot for clarity
                    drawCircle(
                        color = Color.White,
                        radius = 1.5.dp.toPx(),
                        center = point
                    )
                }
            }
            
            // Y-axis label moved to TOP CENTER - better visibility, no cropping
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 50.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = yLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasVariation) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = stringResource(R.string.pw_chart_max, String.format("%.2f", maxY)),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.pw_chart_min, String.format("%.2f", minY)),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
            }
            
            // X-axis label (bottom) - centered
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 50.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = xLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasVariation) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pw_chart_range, String.format("%.0f", minX), String.format("%.0f", maxX)),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CsvPreviewDialog(
    title: String,
    headers: List<String>,
    rows: List<List<String>>,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    headerDescriptions: Map<String, String> = emptyMap() // Map of header -> description
) {
    // Preserve scroll states across activity recreation (for when ads show/close)
    // rememberScrollState already uses rememberSaveable internally, so it persists automatically
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        ),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                // Info banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DesignSystemColors.NeonGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pw_csv_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = if (rows.size <= 20) {
                        stringResource(R.string.pw_csv_preview, rows.size)
                    } else {
                        stringResource(R.string.pw_csv_preview_scroll, rows.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    // Make the content both horizontally and vertically scrollable
                    Box(
        modifier = Modifier
            .fillMaxSize()
                            .verticalScroll(verticalScrollState)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            // Headers row with clickable info icons
                            var selectedHeaderInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
                            
                            // Calculate optimal column widths based on content (UX best practice: ensure headers and data align)
                            val columnWidths = remember(headers, rows, headerDescriptions) {
                                headers.mapIndexed { colIndex, header ->
                                    val headerText = header.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
                                    // Calculate max width needed for this column
                                    // Use character count as proxy (roughly 7-8dp per character for small text)
                                    val headerCharCount = headerText.length
                                    val maxDataCharCount = rows.maxOfOrNull { row ->
                                        if (colIndex < row.size) row[colIndex].length else 0
                                    } ?: 0
                                    // Add space for info icon if present (24dp icon + 4dp spacing)
                                    val iconSpace = if (headerDescriptions[header]?.isNotEmpty() == true) 28 else 0
                                    // Calculate width: max content * char width + padding + icon space
                                    val contentWidth = maxOf(headerCharCount, maxDataCharCount) * 8
                                    // Ensure minimum width for readability (140dp), max for screen fit (220dp)
                                    (contentWidth + iconSpace + 24).coerceIn(140, 220)
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                headers.forEachIndexed { index, header ->
                                    val headerText = header.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
                                    val description = headerDescriptions[header] ?: ""
                                    val columnWidth = columnWidths[index]
                                    
                                    Box(
                                        modifier = Modifier.width(columnWidth.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = headerText,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (description.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = { selectedHeaderInfo = Pair(headerText, description) },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = stringResource(R.string.pw_csv_cd_info, headerText),
                                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        // Vertical divider to separate columns
                                        if (index < headers.size - 1) {
                                            VerticalDivider(
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .height(24.dp),
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                thickness = 1.dp
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Info Dialog
                            selectedHeaderInfo?.let { (headerTitle, headerDesc) ->
                                AlertDialog(
                                    onDismissRequest = { selectedHeaderInfo = null },
                                    title = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = headerTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = headerDesc,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = { selectedHeaderInfo = null }
                                        ) {
                                            Text(stringResource(R.string.pw_got_it))
                                        }
                                    }
                                )
                            }
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                            
                            // Data rows with aligned columns
                            rows.forEachIndexed { rowIndex, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    headers.forEachIndexed { colIndex, _ ->
                                        val columnWidth = columnWidths[colIndex]
                                        val cellValue = if (colIndex < row.size) row[colIndex] else ""
                                        
                                        Box(
                                            modifier = Modifier.width(columnWidth.dp)
                                        ) {
                                            Text(
                                                text = cellValue,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 11.sp,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 14.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                            // Vertical divider to separate columns in data rows
                                            if (colIndex < headers.size - 1) {
                                                VerticalDivider(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterEnd)
                                                        .height(20.dp),
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                    thickness = 1.dp
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowIndex < rows.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
                                }
                            }
                            
                            // Show message about total rows
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (rows.size <= 20) {
                                    stringResource(if (rows.size == 1) R.string.pw_csv_all_row else R.string.pw_csv_all_rows, rows.size)
                                } else {
                                    stringResource(R.string.pw_csv_all_rows_scroll, rows.size)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Summary info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.pw_csv_total_rows, rows.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.pw_csv_columns, headers.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(LocalContext.current.string(R.string.close))
                }
                Button(
                    onClick = {
                        onExport()
                        // Don't dismiss immediately - let user see the share sheet
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(LocalContext.current.string(R.string.export_csv), fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = null
    )
}

/**
 * Format seconds into human-readable duration (e.g., "1h 21m", "2m 50s", "45s")
 * Shows hours and minutes for longer durations, minutes and seconds for shorter ones
 */
private fun formatDurationSeconds(seconds: Long, context: android.content.Context): String {
    if (seconds < 60) {
        return context.string(R.string.pw_dur_s, seconds)
    }
    
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return when {
        hours > 0 -> {
            // For hours: show hours and minutes (e.g., "1h 21m")
            if (minutes > 0) {
                context.string(R.string.pw_dur_h_m, hours, minutes)
            } else {
                context.string(R.string.pw_dur_h, hours)
            }
        }
        minutes > 0 -> {
            // For minutes: show minutes and seconds (e.g., "2m 50s")
            if (secs > 0) {
                context.string(R.string.pw_dur_m_s, minutes, secs)
            } else {
                context.string(R.string.pw_dur_m, minutes)
            }
        }
        else -> {
            context.string(R.string.pw_dur_s, secs)
        }
    }
}

/**
 * Device Sleep Tracker Section
 * Shows automatic device sleep/wake pattern tracking
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun DeviceSleepTrackerSection(
    context: android.content.Context,
    onItemAIClick: ((String, String) -> Unit)? = null
) {
    var sleepStats by remember { mutableStateOf<DeviceSleepTracker.SleepStats?>(null) }
    var isDeviceAwake by remember { mutableStateOf(true) }
    
    // Update stats periodically
    LaunchedEffect(Unit) {
        android.util.Log.d("DeviceGPT_UI", "DeviceSleepTrackerSection: Starting periodic updates")
        while (true) {
            try {
                sleepStats = DeviceSleepTracker.getTodaySleepStats(context)
                isDeviceAwake = DeviceSleepTracker.isDeviceAwake(context)
                android.util.Log.d("DeviceGPT_UI", "DeviceSleepTrackerSection: Updated - Awake: $isDeviceAwake, Stats: ${sleepStats?.let { "Sleep: ${it.formatSleepTime()}, Wake: ${it.formatWakeTime()}, Efficiency: ${it.sleepEfficiency}%" } ?: "null"}")
            } catch (e: Exception) {
                android.util.Log.e("DeviceGPT_UI", "DeviceSleepTrackerSection: Error updating stats", e)
            }
            delay(5000) // Update every 5 seconds
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.device_sleep_tracker),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (onItemAIClick != null) {
                        IconButton(
                            onClick = {
                                val stats = sleepStats
                                val content = if (stats != null) {
                                    """
Device Sleep Tracker Data:
- Current Status: ${if (isDeviceAwake) "Awake" else "Sleeping"}
- Total Sleep Time: ${stats.formatSleepTime()}
- Total Wake Time: ${stats.formatWakeTime()}
- Sleep Efficiency: ${stats.sleepEfficiency}%
- Sleep Sessions: ${stats.sleepSessions}
- Wake Sessions: ${stats.wakeSessions}
- Average Sleep Duration: ${formatDurationSeconds(stats.averageSleepDuration / 1000, context)}
- Average Wake Duration: ${formatDurationSeconds(stats.averageWakeDuration / 1000, context)}
                                    """.trimIndent()
                                } else {
                                    """
Device Sleep Tracker:
- Current Status: ${if (isDeviceAwake) "Awake" else "Sleeping"}
- No sleep statistics available yet. The tracker is monitoring your device's sleep patterns.
                                    """.trimIndent()
                                }
                                onItemAIClick("Device Sleep Tracker", content)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                                contentDescription = stringResource(R.string.pw_cd_ai_sleep),
                                tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Current state indicator - moved below title
                Surface(
                    color = if (isDeviceAwake) 
                        (if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.5f) else DesignSystemColors.NeonGreen.copy(alpha = 0.2f))
                    else 
                        (if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.DarkII.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = if (isDeviceAwake) stringResource(R.string.pw_sleep_awake) else stringResource(R.string.pw_sleep_sleeping),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isDeviceAwake) 
                            (if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.primary)
                        else 
                            (if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Description
            Text(
                text = stringResource(R.string.pw_sleep_intro),
                style = MaterialTheme.typography.bodySmall,
                color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats - Beautiful card-based design
            sleepStats?.let { stats ->
                // Total times - Responsive card layout
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val screenWidth = maxWidth
                    val cardsPerRow = when {
                        screenWidth < 360.dp -> 1 // Very small screens: 1 card per row
                        else -> 2 // All other screens: 2 cards per row
                    }
                    
                    if (cardsPerRow == 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CompactStatCard(
                                    icon = "🌙",
                                    label = stringResource(R.string.pw_sleep_total),
                                    value = stats.formatSleepTime(),
                                    valueColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                CompactStatCard(
                                    icon = "🟢",
                                    label = stringResource(R.string.pw_sleep_total_wake),
                                    value = stats.formatWakeTime(),
                                    valueColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        // Single column for very small screens
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactStatCard(
                                icon = "🌙",
                                label = stringResource(R.string.pw_sleep_total),
                                value = stats.formatSleepTime(),
                                valueColor = MaterialTheme.colorScheme.primary
                            )
                            CompactStatCard(
                                icon = "🟢",
                                label = stringResource(R.string.pw_sleep_total_wake),
                                value = stats.formatWakeTime(),
                                valueColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Efficiency and sessions - Responsive card layout
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val screenWidth = maxWidth
                    val cardsPerRow = when {
                        screenWidth < 400.dp -> 2 // Small screens: 2 cards per row
                        screenWidth < 600.dp -> 2 // Medium screens: 2 cards per row
                        else -> 3 // Large screens: 3 cards per row
                    }
                    
                    if (cardsPerRow == 3) {
                        // 3 cards per row (default layout)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CompactStatCard(
                                    icon = "⚡",
                                    label = stringResource(R.string.pw_sleep_efficiency),
                                    value = "${stats.sleepEfficiency}%",
                                    valueColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                CompactStatCard(
                                    icon = "🌙",
                                    label = stringResource(R.string.pw_sleep_sessions),
                                    value = "${stats.sleepSessions}",
                                    valueColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                CompactStatCard(
                                    icon = "🟢",
                                    label = stringResource(R.string.pw_sleep_wake_sessions),
                                    value = "${stats.wakeSessions}",
                                    valueColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        // 2 cards per row (responsive layout) - Proper alignment
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // First row: Efficiency and Sleep Sessions (side by side for better alignment)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CompactStatCard(
                                        icon = "⚡",
                                        label = stringResource(R.string.pw_sleep_efficiency),
                                        value = "${stats.sleepEfficiency}%",
                                        valueColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    CompactStatCard(
                                        icon = "🌙",
                                        label = stringResource(R.string.pw_sleep_sessions),
                                        value = "${stats.sleepSessions}",
                                        valueColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            // Second row: Wake Sessions (full width for better visual balance)
                            CompactStatCard(
                                icon = "🟢",
                                label = stringResource(R.string.pw_sleep_wake_sessions),
                                value = "${stats.wakeSessions}",
                                valueColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Average durations - Responsive card layout
                if (stats.sleepSessions > 0 || stats.wakeSessions > 0) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val screenWidth = maxWidth
                        val cardsPerRow = when {
                            screenWidth < 360.dp -> 1 // Very small screens: 1 card per row
                            else -> 2 // All other screens: 2 cards per row
                        }
                        
                        val hasSleep = stats.sleepSessions > 0
                        val hasWake = stats.wakeSessions > 0
                        val cardCount = (if (hasSleep) 1 else 0) + (if (hasWake) 1 else 0)
                        
                        if (cardsPerRow == 2 && cardCount == 2) {
                            // 2 cards side by side
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasSleep) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        CompactStatCard(
                                            icon = "⏱️",
                                            label = stringResource(R.string.pw_sleep_avg),
                                            value = formatDurationSeconds(stats.averageSleepDuration / 1000, context),
                                            valueColor = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (hasWake) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        CompactStatCard(
                                            icon = "⏱️",
                                            label = stringResource(R.string.pw_sleep_avg_wake),
                                            value = formatDurationSeconds(stats.averageWakeDuration / 1000, context),
                                            valueColor = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        } else {
                            // Single column layout
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasSleep) {
                                    CompactStatCard(
                                        icon = "⏱️",
                                        label = stringResource(R.string.pw_sleep_avg),
                                        value = formatDurationSeconds(stats.averageSleepDuration / 1000, context),
                                        valueColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (hasWake) {
                                    CompactStatCard(
                                        icon = "⏱️",
                                        label = stringResource(R.string.pw_sleep_avg_wake),
                                        value = formatDurationSeconds(stats.averageWakeDuration / 1000, context),
                                        valueColor = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } ?: run {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.pw_sleep_tracking),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) DesignSystemColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPowerMonitorSection(
    context: android.content.Context,
    viewModel: PowerConsumptionViewModel,
    activity: Activity? = null,
    onItemAIClick: ((String, String) -> Unit)? = null
) {
    val isMonitoring by viewModel.isAppPowerMonitoring.collectAsState()
    val appPowerSnapshot by viewModel.appPowerSnapshot.collectAsState()
    val showCsvDialog by viewModel.showAppPowerCsvDialog.collectAsState()
    
    val hasUsageStatsPermission = remember {
        PermissionManager.hasUsageStatsPermission(context)
    }
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // Native ads for this section
    val shouldShowNativeAds = RemoteConfigUtils.shouldShowNativeAds()
    val powerMonitoringAdCacheGen = NativeAdManager.cacheGeneration.intValue
    val nativeAds = remember(powerMonitoringAdCacheGen) { NativeAdManager.nativeAds }

    val usageStatsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Permission result handled by checking permission state
        if (!PermissionManager.hasUsageStatsPermission(context)) {
            showPermissionDialog = true
        }
    }
    
    val isDarkMode = MaterialTheme.colorScheme.background == DesignSystemColors.Dark
    val cardBackgroundColor = if (isDarkMode) DesignSystemColors.DarkII else MaterialTheme.colorScheme.surface
    val headerTextColor = if (isDarkMode) DesignSystemColors.White else MaterialTheme.colorScheme.onSurface

    remember {
        {
            viewModel.setAppPowerCsvDialogVisible(true)
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardBackgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = headerTextColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = context.string(R.string.app_power_monitor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = headerTextColor,
                    modifier = Modifier.weight(1f)
                )
                if (onItemAIClick != null) {
                    IconButton(
                        onClick = {
                            val apps = appPowerSnapshot?.apps ?: emptyList()
                            val appsText = if (apps.isNotEmpty()) {
                                apps.take(10).joinToString("\n") { app ->
                                    "${app.appName}: ${"%.0f".format(app.foregroundTime / 60000.0)} min on screen"
                                } + if (apps.size > 10) "\n... and ${apps.size - 10} more apps" else ""
                            } else {
                                "No app data available yet. Start monitoring to see app screen time."
                            }
                            val content = """
App Screen Time - Overall Summary:
$appsText

Total Apps Monitored: ${apps.size}

Note: Android does not report per-app battery use to third-party apps, so this is
screen time only — not each app's battery consumption.
                            """.trimIndent()
                            onItemAIClick("App Screen Time", content)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = stringResource(R.string.pw_cd_ai_apps),
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Description with benefits
            Column {
                Text(
                    text = stringResource(R.string.pw_apps_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Permission check
            if (!hasUsageStatsPermission) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = context.string(R.string.usage_stats_permission_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            AnalyticsUtils.logEvent(AnalyticsEvent.AppPowerPermissionRequested)
                            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            try {
                                usageStatsPermissionLauncher.launch(intent)
                            } catch (e: Exception) {
                                showPermissionDialog = true
                            }
                        }
                    ) {
                        Text(context.string(R.string.grant_permission))
                    }
                }
            } else {
                // Monitoring controls
                Button(
                    onClick = {
                        if (isMonitoring) {
                            viewModel.stopAppPowerMonitoring()
                            AnalyticsUtils.logEvent(AnalyticsEvent.AppPowerMonitorStopped)
                        } else {
                            viewModel.startAppPowerMonitoring(context)
                            AnalyticsUtils.logEvent(AnalyticsEvent.AppPowerMonitorStarted)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMonitoring) MaterialTheme.colorScheme.errorContainer else DesignSystemColors.NeonGreen,
                        contentColor = if (isMonitoring) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    if (isMonitoring) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = context.string(R.string.stop_monitoring),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = context.string(R.string.start_monitoring),
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Native Ad 1 - After monitoring button (Policy: Adequate spacing from content)
                if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
                    val nativeAd1 = remember(powerMonitoringAdCacheGen, "power_monitoring_ad1") { NativeAdManager.getAdForPosition("power_monitoring_ad1") }
                    if (nativeAd1 != null) {
                        // Logging handled by getAdForPosition (reduced)
                        AdMobNativeAdCard(nativeAd = nativeAd1)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // App list
                if (isMonitoring || appPowerSnapshot != null) {
                    val apps = appPowerSnapshot?.apps ?: emptyList()
                    
                    if (apps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (isMonitoring) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.pw_apps_monitoring),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                } else {
                                    Text(
                                        text = context.string(R.string.no_app_data),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } else {
                        // Use a large but bounded height to allow unlimited scrolling
                        // This prevents the infinite height constraint error while allowing many apps to be scrolled
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 800.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(apps.size) { index ->
                                val app = apps[index]
                                
                                // Native Ad - Show after 5 apps (Policy: Adequate spacing between ads)
                                if (index == 5 && shouldShowNativeAds && nativeAds.isNotEmpty()) {
                                    val nativeAd2 = remember(powerMonitoringAdCacheGen, "power_app_list_ad") { NativeAdManager.getAdForPosition("power_app_list_ad") }
                                    if (nativeAd2 != null) {
                                        // Logging handled by getAdForPosition (reduced)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        AdMobNativeAdCard(nativeAd = nativeAd2)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                                
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (isDarkMode) DesignSystemColors.DarkII.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        // App name with AI button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = headerTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (onItemAIClick != null) {
                                                IconButton(
                                                    onClick = {
                                                        val totalHours = app.totalUsageTime / (1000.0 * 60.0 * 60.0)
                                                        val fgHours = app.foregroundTime / (1000.0 * 60.0 * 60.0)
                                                        val bgHours = app.backgroundTime / (1000.0 * 60.0 * 60.0)
                                                        val bgRatio = if (app.totalUsageTime > 0) {
                                                            (app.backgroundTime.toDouble() / app.totalUsageTime) * 100.0
                                                        } else 0.0
                                                        
                                                        // Report only what UsageStats actually measures. The old
                                                        // prompt fed the model fabricated per-app watts, a
                                                        // "battery impact %/hour" and a "time to drain full
                                                        // battery" — all derived from screen-time share, none
                                                        // measured. Feeding those to an LLM produced confident
                                                        // nonsense on top of a false premise.
                                                        val usageShare = if (app.totalUsageTime > 0) {
                                                            ((app.foregroundTime + app.backgroundTime).toDouble() /
                                                                app.totalUsageTime) * 100.0
                                                        } else 0.0
                                                        val content = """
App: ${app.appName}
Package: ${app.packageName}

Screen time (from Android UsageStats):
- Foreground Time: ${"%.2f".format(fgHours)} hours
- Background Visible Time: ${"%.2f".format(bgHours)} hours
- Background Ratio: ${"%.1f".format(bgRatio)}%
- Share of total app usage: ${"%.1f".format(usageShare)}%

Note: Android does not expose real per-app battery draw to third-party apps,
so this is usage time only — not a measurement of this app's battery use.
Please answer about usage habits, not battery attribution.
                                                        """.trimIndent()
                                                        
                                                        AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                                                            "source" to "app_power_monitor",
                                                            "app_name" to app.appName
                                                        ))
                                                        onItemAIClick(app.appName, content)
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                                                        contentDescription = stringResource(R.string.pw_cd_ai_app_tips),
                                                        tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        // Screen-time share. Android does not expose real per-app
                                        // battery draw to third-party apps (BatteryStats is system-only),
                                        // so we report what UsageStats actually gives us: how long the app
                                        // was used, and its share of total app usage. Previously this row
                                        // rendered "Drains X% per hour", which Google rejected under the
                                        // Deceptive Behavior policy (2026-07-12) — that figure was only
                                        // screen-time share x system power / battery capacity, never a
                                        // measurement of the app's actual battery use.
                                        val appActiveMs = app.foregroundTime + app.backgroundTime
                                        val usageShare = if (app.totalUsageTime > 0) {
                                            (appActiveMs.toDouble() / app.totalUsageTime) * 100.0
                                        } else 0.0
                                        val usageSeverity = when {
                                            usageShare > 20.0 -> stringResource(R.string.pw_apps_heavy)
                                            usageShare > 5.0 -> stringResource(R.string.pw_apps_moderate)
                                            else -> stringResource(R.string.pw_apps_light)
                                        }
                                        val usageColor = when {
                                            usageShare > 20.0 -> MaterialTheme.colorScheme.error
                                            usageShare > 5.0 -> MaterialTheme.colorScheme.tertiary
                                            else -> headerTextColor
                                        }
                                        val fgMinutes = app.foregroundTime / (1000.0 * 60.0)
                                        val screenTimeLabel = if (fgMinutes >= 60) {
                                            stringResource(R.string.pw_apps_h_on_screen, "%.1f".format(fgMinutes / 60.0))
                                        } else {
                                            stringResource(R.string.pw_apps_min_on_screen, fgMinutes.toInt())
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = usageColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.pw_apps_share, screenTimeLabel, "%.1f".format(usageShare)),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = headerTextColor,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = usageSeverity,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = usageColor,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        // Information ordered by importance (most important first)
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // 1. App efficiency - Most important for optimization decisions
                                            val efficiencyScore = if (app.totalUsageTime > 0 && app.powerConsumption > 0) {
                                                val usageHours = app.totalUsageTime / (1000.0 * 60.0 * 60.0)
                                                val powerPerHour = app.powerConsumption / usageHours.coerceAtLeast(0.001)
                                                (100.0 - (powerPerHour * 10.0).coerceIn(0.0, 100.0))
                                            } else 0.0
                                            
                                            if (efficiencyScore > 0) {
                                                val efficiencyLabel = when {
                                                    efficiencyScore >= 70 -> stringResource(R.string.pw_apps_eff_high)
                                                    efficiencyScore >= 40 -> stringResource(R.string.pw_apps_eff_mid)
                                                    else -> stringResource(R.string.pw_apps_eff_low)
                                                }
                                                val efficiencyColor = when {
                                                    efficiencyScore >= 70 -> DesignSystemColors.Dark
                                                    efficiencyScore >= 40 -> MaterialTheme.colorScheme.tertiary
                                                    else -> MaterialTheme.colorScheme.error
                                                }
                                                
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Speed,
                                                        contentDescription = null,
                                                        tint = efficiencyColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = efficiencyLabel,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = efficiencyColor,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            
                                            // 2. Estimated time to drain - Important for battery planning
                                            val hoursToDrain = if (app.batteryImpact > 0) {
                                                100.0 / app.batteryImpact
                                            } else Double.MAX_VALUE
                                            
                                            if (hoursToDrain < 1000 && hoursToDrain > 0) {
                                                val daysToDrain = hoursToDrain / 24.0
                                                val timeText = if (daysToDrain >= 1) {
                                                    stringResource(R.string.pw_n_days, "%.1f".format(daysToDrain))
                                                } else {
                                                    stringResource(R.string.pw_n_hours, "%.1f".format(hoursToDrain))
                                                }
                                                
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Schedule,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.pw_apps_would_drain, timeText),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            
                                            // 3. Last used time - Helps identify unused apps
                                            if (app.lastTimeUsed > 0) {
                                                val now = System.currentTimeMillis()
                                                val timeSinceLastUsed = now - app.lastTimeUsed
                                                val hoursAgo = timeSinceLastUsed / (1000 * 60 * 60)
                                                val daysAgo = hoursAgo / 24
                                                
                                                val lastUsedText = when {
                                                    hoursAgo < 1 -> stringResource(R.string.pw_apps_just_now)
                                                    hoursAgo < 24 -> stringResource(if (hoursAgo > 1) R.string.pw_hours_ago else R.string.pw_hour_ago, hoursAgo)
                                                    daysAgo < 7 -> stringResource(if (daysAgo > 1) R.string.pw_days_ago else R.string.pw_day_ago, daysAgo)
                                                    else -> {
                                                        val dateFormat = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                                                        stringResource(R.string.pw_apps_last_used, dateFormat.format(java.util.Date(app.lastTimeUsed)))
                                                    }
                                                }
                                                
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.History,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = lastUsedText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            
                                            // 4. Foreground service indicator - Important for apps like music players, navigation
                                            if (app.foregroundServiceTime > 0) {
                                                val serviceHours = app.foregroundServiceTime / (1000.0 * 60.0 * 60.0)
                                                val serviceText = when {
                                                    serviceHours >= 1 -> stringResource(R.string.pw_n_hours, "%.1f".format(serviceHours))
                                                    else -> stringResource(R.string.pw_n_minutes, (app.foregroundServiceTime / (1000.0 * 60.0)).toInt())
                                                }
                                                
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.NotificationsActive,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.pw_apps_background, serviceText),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            
                                            // 5. Usage time - Shows actual usage duration
                                            val totalHours = app.totalUsageTime / (1000.0 * 60.0 * 60.0)
                                            if (totalHours > 0.1) {
                                                val bgRatio = if (app.totalUsageTime > 0) {
                                                    (app.backgroundTime.toDouble() / app.totalUsageTime) * 100.0
                                                } else 0.0
                                                
                                                // Format hours in a user-friendly way
                                                val timeText = when {
                                                    totalHours >= 1 -> {
                                                        val hours = totalHours.toInt()
                                                        val minutes = ((totalHours - hours) * 60).toInt()
                                                        when {
                                                            minutes > 0 -> PowerStrings.hoursMinutes(context, hours, minutes)
                                                            else -> PowerStrings.hours(context, hours)
                                                        }
                                                    }
                                                    else -> {
                                                        val minutes = (totalHours * 60).toInt()
                                                        PowerStrings.minutes(context, minutes)
                                                    }
                                                }
                                                
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AccessTime,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.pw_apps_active_for, timeText),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontSize = 11.sp
                                                    )
                                                    if (bgRatio > 20) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = stringResource(R.string.pw_apps_bg_ratio, "%.0f".format(bgRatio)),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                            fontSize = 10.sp,
                                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            // 6. Usage frequency indicator - Shows usage patterns
                                            if (app.firstTimeStamp > 0 && app.lastTimeStamp > app.firstTimeStamp) {
                                                val usagePeriod = app.lastTimeStamp - app.firstTimeStamp
                                                val periodDays = usagePeriod / (1000.0 * 60.0 * 60.0 * 24.0)
                                                
                                                if (periodDays > 0.1) {
                                                    val totalHours = app.totalUsageTime / (1000.0 * 60.0 * 60.0)
                                                    val sessionsPerDay = if (totalHours > 0 && periodDays > 0) {
                                                        // Estimate sessions based on usage pattern
                                                        val estimatedSessions = (totalHours * 60.0 / 15.0).coerceAtMost(20.0) // Assume avg 15 min per session
                                                        estimatedSessions / periodDays
                                                    } else 0.0
                                                    
                                                    val frequencyText = when {
                                                        sessionsPerDay >= 5 -> stringResource(R.string.pw_apps_freq_very)
                                                        sessionsPerDay >= 2 -> stringResource(R.string.pw_apps_freq_regular)
                                                        sessionsPerDay >= 0.5 -> stringResource(R.string.pw_apps_freq_sometimes)
                                                        else -> stringResource(R.string.pw_apps_freq_rare)
                                                    }
                                                    
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.TrendingUp,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = frequencyText,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Simple footer without CSV export
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isDarkMode) DesignSystemColors.DarkII.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(if (apps.size > 1) R.string.pw_n_apps else R.string.pw_n_app, apps.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Permission dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text(context.string(R.string.permission_required))
            },
            text = {
                Text(
                    text = stringResource(R.string.pw_apps_perm_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            ErrorHandler.handleError(e, context = "AppPowerMonitorSection")
                        }
                    }
                ) {
                    Text(context.string(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(context.string(R.string.cancel))
                }
            }
        )
    }
    
    // CSV Dialog
    if (showCsvDialog) {
        val snapshots = viewModel.appPowerHistory.value
        // Include current snapshot if available
        val allSnapshots = if (appPowerSnapshot != null && !snapshots.contains(appPowerSnapshot)) {
            snapshots + appPowerSnapshot!!
        } else {
            snapshots
        }
        // Get all apps from all snapshots (keep latest for each package)
        val allApps = allSnapshots.flatMap { it.apps }
            .groupBy { it.packageName }
            .mapValues { it.value.maxByOrNull { it.timestamp } }
            .values
            .filterNotNull()
            .toList()
        
        CsvPreviewDialog(
            title = stringResource(R.string.pw_apps_csv_title),
            headers = listOf(stringResource(R.string.pw_csv_timestamp), stringResource(R.string.pw_csv_app_package), stringResource(R.string.pw_csv_app_name), stringResource(R.string.pw_csv_app_share), stringResource(R.string.pw_csv_app_fg), stringResource(R.string.pw_csv_app_bg), stringResource(R.string.pw_csv_app_total)),
            headerDescriptions = mapOf(
                stringResource(R.string.pw_csv_timestamp) to stringResource(R.string.pw_csv_app_timestamp_d),
                stringResource(R.string.pw_csv_app_package) to stringResource(R.string.pw_csv_app_package_d),
                stringResource(R.string.pw_csv_app_name) to stringResource(R.string.pw_csv_app_name_d),
                stringResource(R.string.pw_csv_app_share) to stringResource(R.string.pw_csv_app_share_d),
                stringResource(R.string.pw_csv_app_fg) to stringResource(R.string.pw_csv_app_fg_d),
                stringResource(R.string.pw_csv_app_bg) to stringResource(R.string.pw_csv_app_bg_d),
                stringResource(R.string.pw_csv_app_total) to stringResource(R.string.pw_csv_app_total_d)
            ),
            rows = allApps.map { app ->
                val share = if (app.totalUsageTime > 0) {
                    ((app.foregroundTime + app.backgroundTime).toDouble() / app.totalUsageTime) * 100.0
                } else 0.0
                listOf(
                    PowerConsumptionAggregator.formatTimestamp(app.timestamp),
                    app.packageName,
                    app.appName,
                    "%.1f".format(share),
                    app.foregroundTime.toString(),
                    app.backgroundTime.toString(),
                    app.totalUsageTime.toString()
                )
            },
            onDismiss = { viewModel.setAppPowerCsvDialogVisible(false) },
            onExport = {
                // Show ad before CSV export
                if (activity != null) {
                    InterstitialAdManager.showAdBeforeAction(
                        activity = activity,
                        actionName = "csv_export_app_power"
                    ) {
                        AnalyticsUtils.logEvent(AnalyticsEvent.AppPowerCsvExported, mapOf<String, Any?>(
                            "app_count" to allApps.size
                        ))
                        
                        val csvHeaders = listOf("timestamp", "package_name", "app_name", "power_w", "foreground_time_ms", "background_time_ms", "total_usage_ms", "battery_impact_percent_per_hour")
                        val csvRows = allApps.map { app ->
                            listOf(
                                app.timestamp.toString(),
                                app.packageName,
                                app.appName,
                                app.powerConsumption.toString(),
                                app.foregroundTime.toString(),
                                app.backgroundTime.toString(),
                                app.totalUsageTime.toString(),
                                app.batteryImpact.toString()
                            )
                        }
                        
                        val uri = PowerConsumptionUtils.exportExperimentCSV(
                            context = context,
                            experimentName = "app_power_monitor",
                            headers = csvHeaders,
                            rows = csvRows
                        )
                        
                        uri?.let {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, it)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.string(R.string.pw_share_apps_data)))
                        }
                    }
                } else {
                    // Fallback if no activity
                    AnalyticsUtils.logEvent(AnalyticsEvent.AppPowerCsvExported, mapOf<String, Any?>(
                        "app_count" to allApps.size
                    ))
                    
                    val csvHeaders = listOf("timestamp", "package_name", "app_name", "power_w", "foreground_time_ms", "background_time_ms", "total_usage_ms", "battery_impact_percent_per_hour")
                    val csvRows = allApps.map { app ->
                        listOf(
                            app.timestamp.toString(),
                            app.packageName,
                            app.appName,
                            app.powerConsumption.toString(),
                            app.foregroundTime.toString(),
                            app.backgroundTime.toString(),
                            app.totalUsageTime.toString(),
                            app.batteryImpact.toString()
                        )
                    }
                    
                    val uri = PowerConsumptionUtils.exportExperimentCSV(
                        context = context,
                        experimentName = "app_power_monitor",
                        headers = csvHeaders,
                        rows = csvRows
                    )
                    
                    uri?.let {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, it)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.string(R.string.pw_share_apps_data)))
                    }
                }
            }
        )
    }
    
    // AI Dialog for app optimization
}

// 2026-07-25: removed `shareWithAIApp` (hand-rolled ACTION_SEND with EXTRA_STREAM only — no
// EXTRA_TEXT, no clipboard fallback, cacheDir instead of filesDir — bypassing
// com.teamz.lab.debugger.utils.shareWithAIAppRobust, the one hardened share path every other
// AI-share entry point in this app uses) and `generateAppOptimizationPrompt`, its only caller.
// Both had zero live callers anywhere in the project (verified via project-wide grep before and
// after removal) — deleted rather than fixed, since fixing dead code just leaves a second bad
// share pattern sitting in the file for someone to copy later.


