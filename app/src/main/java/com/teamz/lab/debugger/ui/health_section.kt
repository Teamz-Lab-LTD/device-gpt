package com.teamz.lab.debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.ui.rememberAdLoader
import com.teamz.lab.debugger.utils.HealthScoreUtils
import com.teamz.lab.debugger.utils.handleError
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.ReviewPromptManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.teamz.lab.debugger.utils.calculatePrivacyScore
import com.teamz.lab.debugger.utils.getPrivacyThreatsToday
import com.teamz.lab.debugger.utils.getRecentCameraMicUsageLog
import com.teamz.lab.debugger.utils.AIIcon
import com.teamz.lab.debugger.utils.string
import com.teamz.lab.debugger.R
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import androidx.lifecycle.viewmodel.compose.viewModel


@Composable
fun HealthSection(
    onShareClick: (String) -> Unit = {},
    onAIClick: (() -> Unit)? = null,
    onItemAIClick: ((String, String) -> Unit)? = null,
    onScanComplete: (() -> Unit)? = null,
    onGenerateReportClick: (() -> Unit)? = null,
    onVerifyReportClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val coroutineScope = rememberCoroutineScope()
    
    // Use ViewModel to persist scroll state across activity recreation (survives ad show/close)
    val viewModel: HealthSectionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    
    // Preserve LazyColumn scroll state across activity recreation
    // Get saved scroll position from ViewModel and restore it
    val savedFirstVisibleItemIndex by viewModel.lazyListFirstVisibleItemIndex.collectAsState()
    val savedFirstVisibleItemScrollOffset by viewModel.lazyListFirstVisibleItemScrollOffset.collectAsState()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = savedFirstVisibleItemScrollOffset
    )
    
    // Restore scroll position after activity recreation
    LaunchedEffect(Unit) {
        if (savedFirstVisibleItemIndex > 0 || savedFirstVisibleItemScrollOffset > 0) {
            if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                // Only restore if we have a saved position and current position is 0 (activity was recreated)
                kotlinx.coroutines.delay(100) // Wait for layout
                try {
                    listState.animateScrollToItem(savedFirstVisibleItemIndex, savedFirstVisibleItemScrollOffset)
                } catch (e: Exception) {
                    // Ignore scroll errors (item might not exist yet)
                }
            }
        }
    }
    
    // Save scroll position to ViewModel when it changes (throttled to prevent ANR)
    // Use snapshotFlow with debounce to prevent LaunchedEffect from restarting on every scroll event
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { 
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset 
        }
            .distinctUntilChanged()
            .debounce(500) // Only process scroll changes every 500ms
            .collect { (index, offset) ->
                if (index > 0 || offset > 0) {
                    viewModel.saveLazyListScrollPosition(index, offset)
                }
            }
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Track pending actions that should execute after permission is granted
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingBatteryAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCacheAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    // Lifecycle observer to detect when user returns from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Check if permissions were granted and execute pending actions
                coroutineScope.launch {
                    kotlinx.coroutines.delay(500) // Small delay to ensure settings are closed
                    
                    // Check storage permission and execute pending action
                    pendingStorageAction?.let { action ->
                        // Execute the pending storage cleanup
                        // Note: We removed MANAGE_EXTERNAL_STORAGE permission check
                        // The app will clear its own cache and open settings for other apps
                        pendingStorageAction = null
                        action() // Execute the pending storage cleanup
                    }
                    
                    // Check battery optimization and execute pending action
                    pendingBatteryAction?.let { action ->
                        pendingBatteryAction = null
                        action() // Execute the pending battery optimization
                    }
                    
                    // Check cache permission and execute pending action
                    pendingCacheAction?.let { action ->
                        // Execute the pending cache cleanup
                        // Note: We removed MANAGE_EXTERNAL_STORAGE permission check
                        // The app will clear its own cache and open settings for other apps
                        pendingCacheAction = null
                        action() // Execute the pending cache cleanup
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // State management for loading and data refresh
    var isScanning by remember { mutableStateOf(false) }
    var scanCompleted by remember { mutableStateOf(false) }
    var currentHealthScore by remember { mutableIntStateOf(0) }
    
    // Calculate health score asynchronously since it's a suspend function
    LaunchedEffect(Unit) {
        currentHealthScore = HealthScoreUtils.calculateDailyHealthScore(context)
    }

    // AdMob interstitial ad tracking
    // Note: InterstitialAdManager handles all checks centrally:
    // - RemoteConfig enable/disable flag
    // - Global time-based throttling
    // - Ad loading and showing

    // Native ad state - stabilize to prevent scroll position jumps
    // Cache the value to prevent recomposition during scroll
    val shouldShowNativeAds = RemoteConfigUtils.shouldShowNativeAdsReactive()
    
    // Initialize ad loader to ensure ads are loaded on first view (fixes fresh install issue)
    // This ensures native ads are available immediately, not just after visiting other tabs
    val adLoader = activity?.let { rememberAdLoader(it) }
    
    // Cache native ads - update only when scroll stops to prevent scroll jumps.
    // Also re-snapshot whenever cacheGeneration changes (an ad was added/evicted/expired)
    // so a destroyed NativeAd never renders just because the user happens to be scrolling.
    var nativeAdsCache by remember { mutableStateOf(NativeAdManager.nativeAds.toList()) }
    val healthAdCacheGen = NativeAdManager.cacheGeneration.intValue

    // Update ads when scroll stops (very short delay - 50ms - to balance UX and revenue)
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            kotlinx.coroutines.delay(50)
            if (!listState.isScrollInProgress) {
                nativeAdsCache = NativeAdManager.nativeAds.toList()
            }
        }
    }

    // Safety net for evictions that happen mid-scroll: refresh cache on cacheGen bump.
    LaunchedEffect(healthAdCacheGen) {
        nativeAdsCache = NativeAdManager.nativeAds.toList()
    }

    // Always use cached ads - they're updated when scroll stops, but displayed immediately
    // This prevents scroll jumps while ensuring ads are always visible (revenue-safe)
    val nativeAds = nativeAdsCache
    
    var currentNativeAd by remember {
        mutableStateOf<com.google.android.gms.ads.nativead.NativeAd?>(
            null
        )
    }

    // Animation for loading spinner
    val infiniteTransition = rememberInfiniteTransition(label = "scan_loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Function to perform the actual scan
    val performScan: () -> Unit = {
        if (!isScanning) {
            AnalyticsUtils.logEvent(AnalyticsEvent.HealthScanStarted)
            coroutineScope.launch {
                try {
                    isScanning = true
                    scanCompleted = false

                    // Simulate scanning time for better UX
                    kotlinx.coroutines.delay(2000)

                    // Calculate new health score
                    val newHealthScore = HealthScoreUtils.calculateDailyHealthScore(context)
                    HealthScoreUtils.saveHealthScore(context, newHealthScore)

                    // Update UI state
                    currentHealthScore = newHealthScore
                    scanCompleted = true
                    isScanning = false
                    
                    // Log scan completion
                    AnalyticsUtils.logEvent(AnalyticsEvent.HealthScanCompleted, mapOf(
                        "health_score" to newHealthScore,
                        "streak" to HealthScoreUtils.getDailyStreak(context),
                        "best_score" to HealthScoreUtils.getBestScore(context)
                    ))
                    com.teamz.lab.debugger.utils.EngagementTracker.trackSignificantAction(
                        context,
                        com.teamz.lab.debugger.utils.SignificantAction.SCAN_COMPLETED,
                        mapOf("health_score" to newHealthScore)
                    )
                    
                    // Track meaningful interaction for review prompt (after positive experience)
                    if (context is android.app.Activity) {
                        ReviewPromptManager.trackMeaningfulInteraction(context, "health_scan_completed")
                    }

                    // Trigger paywall after scan (user has seen value)
                    onScanComplete?.invoke()

                    // Upload to leaderboard after scan
                    com.teamz.lab.debugger.utils.LeaderboardDataUpload.uploadAfterHealthScan(context)
                    
                    // Generate and share health data text
                    val healthShareText = generateHealthShareText(context, newHealthScore)
                    onShareClick(healthShareText)

                    // Reset completion state after a delay
                    kotlinx.coroutines.delay(3000)
                    scanCompleted = false

                } catch (e: Exception) {
                    handleError(e)
                    isScanning = false
                }
            }
        }
    }

    // Generate share text on initial load
    LaunchedEffect(currentHealthScore) {
        val healthShareText = generateHealthShareText(context, currentHealthScore)
        onShareClick(healthShareText)
    }
    
    // Function to handle scan button click with AdMob integration
    // InterstitialAdManager handles everything centrally:
    // - Checks if ads are enabled (RemoteConfig)
    // - Enforces global throttling (time-based)
    // - Shows ad if allowed, otherwise proceeds immediately
    val handleScanClick: () -> Unit = {
        InterstitialAdManager.showAdIfAvailable(context as android.app.Activity) {
            // Ad closed or skipped - proceed with scan
            performScan()
        }
    }


    // Pre-compute TextStyle outside LazyColumn to prevent ANR from TextStyle.merge
    // TextStyle.merge is expensive and can block if called repeatedly during recomposition
    // This is computed once and reused for all items, preventing repeated merges
    val materialTypography = MaterialTheme.typography
    val cardTitleTextStyle = remember(materialTypography) {
        materialTypography.titleMedium.copy(
            fontWeight = FontWeight.Bold
        )
    }
    val cardBodyTextStyle = remember(materialTypography) {
        materialTypography.bodyMedium
    }
    val cardSmallTextStyle = remember(materialTypography) {
        materialTypography.bodySmall
    }
    
    
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 120.dp // Extra space for FABs at bottom (Cert, AI, Send buttons)
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // v3.1.12 — Last Device Score card (only renders if user has completed FirstScanGate)
        if (FirstScanGate.hasCompletedScan(context)) {
            item(key = "last_device_score") {
                LastScoreCard(onShareClick = { score ->
                    val shareText = "My phone scored $score/100 on DeviceGPT. Run yours: https://play.google.com/store/apps/details?id=com.teamz.lab.debugger"
                    onShareClick(shareText)
                })
            }
        }

        // Health Score Card
        item(key = "health_score") {
            // Memoize the onScoreClick callback to prevent unnecessary recomposition
            val onScoreClickCallback = remember(coroutineScope, currentHealthScore, listState) {
                {
                    AnalyticsUtils.logEvent(AnalyticsEvent.HealthScoreClicked, mapOf(
                        "current_score" to currentHealthScore
                    ))
                    // Scroll to recommendations section at the bottom
                    coroutineScope.launch {
                        try {
                            // Find recommendations item by key - it's at the bottom now
                            // First, try to find it in visible items
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            val visibleItem = visibleItems.find { it.key == "recommendations" }
                            
                            if (visibleItem != null) {
                                // Item is visible, scroll to it
                                listState.animateScrollToItem(visibleItem.index)
                            } else {
                                // Item not visible, scroll to end and then find it
                                val totalItems = listState.layoutInfo.totalItemsCount
                                if (totalItems > 0) {
                                    // Scroll near the end first
                                    listState.animateScrollToItem(maxOf(0, totalItems - 5))
                                    // Wait a bit for layout to update
                                    kotlinx.coroutines.delay(100)
                                    // Now try to find it again
                                    val updatedVisibleItems = listState.layoutInfo.visibleItemsInfo
                                    val foundItem = updatedVisibleItems.find { it.key == "recommendations" }
                                    foundItem?.let {
                                        listState.animateScrollToItem(it.index)
                                    } ?: run {
                                        // Last resort: scroll to very end
                                        listState.animateScrollToItem(totalItems - 1)
                                    }
                                }
                            }
                            AnalyticsUtils.logEvent(AnalyticsEvent.HealthRecommendationsViewed, mapOf(
                                "source" to "health_score_clicked"
                            ))
                        } catch (e: Exception) {
                            // If all else fails, scroll to end
                            try {
                                val totalItems = listState.layoutInfo.totalItemsCount
                                if (totalItems > 0) {
                                    listState.animateScrollToItem(totalItems - 1)
                                }
                            } catch (e2: Exception) {
                                // Ignore scroll errors
                            }
                        }
                    }
                    Unit // Explicitly return Unit to match expected type
                }
            }
            
            HealthScoreCard(
                context = context,
                onScanClick = handleScanClick,
                isScanning = isScanning,
                scanCompleted = scanCompleted,
                rotation = rotation,
                onScoreClick = onScoreClickCallback,
                onAIClick = onAIClick
            )
        }

        // Verified Report teaser - shown after scan completes
        if (currentHealthScore > 0 && onGenerateReportClick != null) {
            item(key = "verified_report_teaser") {
                androidx.compose.material3.OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onGenerateReportClick() },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        com.teamz.lab.debugger.ui.theme.DesignSystemColors.NeonGreen.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋",
                            fontSize = 28.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Get Device Health Report",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Shareable proof that your phone is working properly — great for resale or warranty",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = com.teamz.lab.debugger.ui.theme.DesignSystemColors.NeonGreen
                        )
                    }
                }
            }
        }

        // Verify a Report card - always visible (free feature, drives viral growth)
        if (onVerifyReportClick != null) {
            item(key = "verify_report_teaser") {
                androidx.compose.material3.OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onVerifyReportClick() },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔍",
                            fontSize = 28.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Verify Someone's Report",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Got a report code? Check if the phone's health report is real",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Native Ad (just above Smart Recommendations for better revenue)
        // Policy: Single native ad per screen, adequate spacing, clearly labeled
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item(key = "native_ad_top") {
                // Use position-specific ad to ensure proper rotation
                val nativeAd = remember(healthAdCacheGen, "health_section_top") { NativeAdManager.getAdForPosition("health_section_top") }
                if (nativeAd != null) {
                    // Logging reduced - only log once
                    LaunchedEffect(nativeAd.hashCode()) {
                        android.util.Log.d("AdDisplay", "📺 Health section ad - " +
                                "Ad hash: ${nativeAd.hashCode()}, Total ads: ${NativeAdManager.nativeAds.filterNotNull().size}")
                    }
                    Spacer(modifier = Modifier.height(8.dp)) // Spacing before ad
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp)) // Spacing after ad
                }
            }
        }

        // v3.1.11 W2 user-behavior insight — Health History promoted from
        // item position #9 (below 8 other cards) to position #2 (right after
        // health_score). GA4 28d data: 4.2 views/user but only 46% of
        // Health-tab visitors discovered it. Surfacing above the fold should
        // grow reach from 19% (58/302) toward Health-tab reach 42% (127/302).
        item(key = "health_history") {
            LaunchedEffect(Unit) {
                AnalyticsUtils.logEvent(AnalyticsEvent.HealthHistoryViewed)
            }
            HealthHistoryCard(
                context = context,
                onAIClick = onItemAIClick?.let { handler ->
                    {
                        AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                            "source" to "health_history",
                            "item_title" to "7-Day Health History"
                        ))
                        val history = HealthScoreUtils.getHealthScoreHistory(context, 7)
                        val historyText = if (history.isNotEmpty()) {
                            history.joinToString("\n") { (date, score) -> "$date: $score/10" }
                        } else {
                            "No health history yet. Start scanning to build your history!"
                        }
                        val content = """
7-Day Health History:
$historyText

Current Score: $currentHealthScore/10
Daily Streak: ${HealthScoreUtils.getDailyStreak(context)} days
Best Score: ${HealthScoreUtils.getBestScore(context)}/10
Total Scans: ${HealthScoreUtils.getTotalScans(context)}
                        """.trimIndent()
                        handler("7-Day Health History", content)
                    }
                }
            )
        }

        // Quick Actions Section Header
        item(key = "quick_actions_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Native Ad (after Quick Actions header)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item(key = "native_ad_quick_actions") {
                val nativeAd = remember(healthAdCacheGen, "health_section_quick_actions_header") { NativeAdManager.getAdForPosition("health_section_quick_actions_header") }
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // RAM Optimization Card (Quick Action)
        item(key = "ram_optimization") {
            var ramUsage by remember { mutableStateOf("--") }
            var ramPercent by remember { mutableIntStateOf(0) }
            var isClearing by remember { mutableStateOf(false) }
            var lastClearResult by remember { mutableStateOf<String?>(null) }
            var lastClearTime by remember { mutableStateOf(0L) } // Debounce rapid clicks
            
            // Pre-compile Regex outside LaunchedEffect to prevent ANR (Regex compilation blocks main thread)
            val ramPercentRegex = remember { Regex("\\((\\d+)%\\)") }
            
            LaunchedEffect(Unit) {
                val ramInfo = withContext(Dispatchers.Default) {
                    com.teamz.lab.debugger.utils.getRamUsage(context)
                }
                ramUsage = ramInfo
                // Extract percentage using pre-compiled regex
                val percentMatch = ramPercentRegex.find(ramInfo)
                ramPercent = percentMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }
            
            // Update RAM usage periodically - pause when scrolling for better performance
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(5000) // Update every 5 seconds
                    // Skip update if user is scrolling to prevent lag
                    if (!listState.isScrollInProgress) {
                        // Run on background thread to prevent blocking
                        val ramInfo = withContext(Dispatchers.Default) {
                            com.teamz.lab.debugger.utils.getRamUsage(context)
                        }
                        ramUsage = ramInfo
                        val percentMatch = ramPercentRegex.find(ramInfo)
                        ramPercent = percentMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    }
                }
            }
            
            RamOptimizationCard(
                ramUsage = ramUsage,
                ramPercent = ramPercent,
                isClearing = isClearing,
                lastClearResult = lastClearResult,
                onClearRam = {
                    // Debounce: Prevent rapid clicks (minimum 2 seconds between clicks)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClearTime < 2000) {
                        return@RamOptimizationCard // Ignore rapid clicks
                    }
                    lastClearTime = currentTime
                    
                    // Prevent multiple simultaneous operations
                    if (isClearing) {
                        return@RamOptimizationCard
                    }
                    
                    // Set loading state immediately so user sees feedback right away
                    isClearing = true
                    
                    // Log button click analytics
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.WidgetTapped,
                        mapOf(
                            "source" to "health_section",
                            "action" to "free_ram_button_clicked",
                            "ram_percent_before" to ramPercent
                        )
                    )
                    
                    // Show interstitial ad before clearing RAM
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        InterstitialAdManager.showAdBeforeAction(
                            activity = activity,
                            actionName = "clear_ram"
                        ) {
                            // Action to execute after ad is dismissed (or if ad fails)
                            // Run RAM clearing on background thread to prevent UI blocking
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                try {
                                    val ramPercentBefore = ramPercent
                                    // Run clearRam on background thread
                                    val (success, message) = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        com.teamz.lab.debugger.utils.clearRam(context)
                                    }
                                    
                                    // Update UI on main thread
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        lastClearResult = message
                                        isClearing = false
                                    }
                                    
                                    // Update RAM usage after clearing (on background thread)
                                    kotlinx.coroutines.delay(1000)
                                    val ramInfo = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        com.teamz.lab.debugger.utils.getRamUsage(context)
                                    }
                                    
                                    // Update UI on main thread
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        ramUsage = ramInfo
                                        val percentMatch = ramPercentRegex.find(ramInfo)
                                        ramPercent = percentMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                    }
                                    
                                    // Log RAM clear completion analytics
                                    AnalyticsUtils.logEvent(
                                        AnalyticsEvent.WidgetTapped,
                                        mapOf(
                                            "source" to "health_section",
                                            "action" to "clear_ram_completed",
                                            "success" to success,
                                            "ram_percent_before" to ramPercentBefore,
                                            "ram_percent_after" to ramPercent,
                                            "freed_mb" to (ramPercentBefore - ramPercent)
                                        )
                                    )
                                } catch (e: Exception) {
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        lastClearResult = "Error: ${e.message}"
                                        isClearing = false
                                    }
                                    com.teamz.lab.debugger.utils.handleError(e)
                                    
                                    // Log error analytics
                                    AnalyticsUtils.logEvent(
                                        AnalyticsEvent.WidgetTapped,
                                        mapOf(
                                            "source" to "health_section",
                                            "action" to "clear_ram_error",
                                            "error" to (e.message ?: "Unknown error")
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        // Fallback if no activity context - run on background thread
                        isClearing = true
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                            try {
                                val ramPercentBefore = ramPercent
                                // Run clearRam on background thread
                                val (success, message) = com.teamz.lab.debugger.utils.clearRam(context)
                                
                                // Update UI on main thread
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    lastClearResult = message
                                    isClearing = false
                                }
                                
                                // Update RAM usage after clearing
                                kotlinx.coroutines.delay(1000)
                                val ramInfo = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    com.teamz.lab.debugger.utils.getRamUsage(context)
                                }
                                
                                // Update UI on main thread
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    ramUsage = ramInfo
                                    val percentMatch = ramPercentRegex.find(ramInfo)
                                    ramPercent = percentMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                }
                                
                                // Log completion analytics
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.WidgetTapped,
                                    mapOf(
                                        "source" to "health_section",
                                        "action" to "clear_ram_completed",
                                        "success" to success,
                                        "ram_percent_before" to ramPercentBefore,
                                        "ram_percent_after" to ramPercent
                                    )
                                )
                            } catch (e: Exception) {
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    lastClearResult = "Error: ${e.message}"
                                    isClearing = false
                                }
                                com.teamz.lab.debugger.utils.handleError(e)
                            }
                        }
                    }
                },
                onAIClick = onItemAIClick?.let { handler ->
                    {
                        val content = """
RAM Usage: $ramUsage
Current Usage: $ramPercent%

Android manages RAM automatically. This card shows current memory usage; refreshing re-reads the memory stats.
                        """.trimIndent()
                        handler("Memory Usage", content)
                    }
                }
            )
        }

        // Native Ad (after RAM Optimization)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item(key = "native_ad_ram") {
                val nativeAd = remember(healthAdCacheGen, "health_section_ram") { NativeAdManager.getAdForPosition("health_section_ram") }
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Storage Cleanup Card (Quick Action)
        item(key = "storage_cleanup") {
            var storageUsage by remember { mutableStateOf("--") }
            var storagePercent by remember { mutableIntStateOf(0) }
            var isClearing by remember { mutableStateOf(false) }
            var lastClearResult by remember { mutableStateOf<String?>(null) }
            var showStorageGuideDialog by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                val storageInfo = com.teamz.lab.debugger.utils.getAvailableStorage()
                storageUsage = storageInfo
                storagePercent = com.teamz.lab.debugger.utils.getStorageUsagePercent(context)
            }
            
            // Update storage usage periodically - pause when scrolling for better performance
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(10000) // Update every 10 seconds
                    // Skip update if user is scrolling to prevent lag
                    if (!listState.isScrollInProgress) {
                        // Run on background thread to prevent blocking
                        val storageInfo = withContext(Dispatchers.Default) {
                            com.teamz.lab.debugger.utils.getAvailableStorage()
                        }
                        val storagePercentValue = withContext(Dispatchers.Default) {
                            com.teamz.lab.debugger.utils.getStorageUsagePercent(context)
                        }
                        storageUsage = storageInfo
                        storagePercent = storagePercentValue
                    }
                }
            }
            
            // Storage Guide Dialog
            if (showStorageGuideDialog) {
                AlertDialog(
                    onDismissRequest = { showStorageGuideDialog = false },
                    title = {
                        Text(
                            text = "How to Clear Storage",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "To clear app caches and free up storage:",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                Text(
                                    text = "Option 1: Grant 'All files access' permission",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Text(
                                    text = "• Enable 'Allow access to manage all files' in the settings screen",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                                )
                                Text(
                                    text = "• This allows the app to clear more cache automatically",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = "Option 2: Clear cache manually",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Text(
                                text = "1. Go to Settings > Apps",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "2. Select an app",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "3. Tap 'Storage' > 'Clear Cache'",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Or use Settings > Storage to see overall storage usage and recommendations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showStorageGuideDialog = false
                                // Try to open settings again
                                val opened = com.teamz.lab.debugger.utils.openStorageSettings(context)
                                if (!opened) {
                                    // If still can't open, show toast
                                    android.widget.Toast.makeText(
                                        context,
                                        "Please go to Settings > Storage manually",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        ) {
                            Text("Open Settings")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStorageGuideDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
            
            StorageCleanupCard(
                storageUsage = storageUsage,
                storagePercent = storagePercent,
                isClearing = isClearing,
                lastClearResult = lastClearResult,
                onCleanStorage = {
                    isClearing = true
                    
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.WidgetTapped,
                        mapOf(
                            "source" to "health_section",
                            "action" to "clean_storage_button_clicked",
                            "storage_percent_before" to storagePercent
                        )
                    )
                    
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        InterstitialAdManager.showAdBeforeAction(
                            activity = activity,
                            actionName = "clean_storage"
                        ) {
                            coroutineScope.launch {
                                try {
                                    val storagePercentBefore = storagePercent
                                    val (success, message, shouldOpenSettings) = com.teamz.lab.debugger.utils.clearStorageCache(context)
                                    isClearing = false
                                    
                                    if (success) {
                                        // Cache was cleared successfully
                                        lastClearResult = message
                                        kotlinx.coroutines.delay(1000)
                                        val storageInfo = com.teamz.lab.debugger.utils.getAvailableStorage()
                                        storageUsage = storageInfo
                                        storagePercent = com.teamz.lab.debugger.utils.getStorageUsagePercent(context)
                                        
                                        AnalyticsUtils.logEvent(
                                            AnalyticsEvent.WidgetTapped,
                                            mapOf(
                                                "source" to "health_section",
                                                "action" to "clean_storage_completed",
                                                "success" to true,
                                                "storage_percent_before" to storagePercentBefore,
                                                "storage_percent_after" to storagePercent
                                            )
                                        )
                                    } else if (shouldOpenSettings) {
                                        // Need to open settings - set pending action to retry after permission is granted
                                        lastClearResult = message
                                        
                                        // Set pending action to retry storage cleanup when user returns
                                        pendingStorageAction = {
                                            coroutineScope.launch {
                                                try {
                                                    isClearing = true
                                                    val storagePercentBefore = storagePercent
                                                    val (retrySuccess, retryMessage, retryShouldOpenSettings) = com.teamz.lab.debugger.utils.clearStorageCache(context)
                                                    isClearing = false
                                                    
                                                    if (retrySuccess) {
                                                        lastClearResult = retryMessage
                                                        kotlinx.coroutines.delay(1000)
                                                        val storageInfo = com.teamz.lab.debugger.utils.getAvailableStorage()
                                                        storageUsage = storageInfo
                                                        storagePercent = com.teamz.lab.debugger.utils.getStorageUsagePercent(context)
                                                        
                                                        AnalyticsUtils.logEvent(
                                                            AnalyticsEvent.WidgetTapped,
                                                            mapOf(
                                                                "source" to "health_section",
                                                                "action" to "clean_storage_completed_after_permission",
                                                                "success" to true,
                                                                "storage_percent_before" to storagePercentBefore,
                                                                "storage_percent_after" to storagePercent
                                                            )
                                                        )
                                                    } else {
                                                        lastClearResult = retryMessage
                                                    }
                                                } catch (e: Exception) {
                                                    lastClearResult = "Error: ${e.message}"
                                                    isClearing = false
                                                    com.teamz.lab.debugger.utils.handleError(e)
                                                }
                                            }
                                        }
                                        
                                        kotlinx.coroutines.delay(500)
                                        val settingsOpened = com.teamz.lab.debugger.utils.openStorageSettings(context)
                                        if (!settingsOpened) {
                                            // Settings couldn't be opened - show guide dialog
                                            showStorageGuideDialog = true
                                            pendingStorageAction = null // Clear pending if settings can't be opened
                                        }
                                        
                                        AnalyticsUtils.logEvent(
                                            AnalyticsEvent.WidgetTapped,
                                            mapOf(
                                                "source" to "health_section",
                                                "action" to "clean_storage_opened_settings",
                                                "settings_opened" to settingsOpened
                                            )
                                        )
                                    } else {
                                        lastClearResult = message
                                    }
                                } catch (e: Exception) {
                                    lastClearResult = "Error: ${e.message}"
                                    isClearing = false
                                    com.teamz.lab.debugger.utils.handleError(e)
                                    AnalyticsUtils.logEvent(
                                        AnalyticsEvent.WidgetTapped,
                                        mapOf(
                                            "source" to "health_section",
                                            "action" to "clean_storage_error",
                                            "error" to (e.message ?: "Unknown error")
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        isClearing = true
                        coroutineScope.launch {
                            try {
                                val (success, message, shouldOpenSettings) = com.teamz.lab.debugger.utils.clearStorageCache(context)
                                isClearing = false
                                
                                if (success) {
                                    lastClearResult = message
                                    kotlinx.coroutines.delay(1000)
                                    val storageInfo = com.teamz.lab.debugger.utils.getAvailableStorage()
                                    storageUsage = storageInfo
                                    storagePercent = com.teamz.lab.debugger.utils.getStorageUsagePercent(context)
                                } else if (shouldOpenSettings) {
                                    lastClearResult = message
                                    kotlinx.coroutines.delay(500)
                                    val settingsOpened = com.teamz.lab.debugger.utils.openStorageSettings(context)
                                    if (!settingsOpened) {
                                        showStorageGuideDialog = true
                                    }
                                } else {
                                    lastClearResult = message
                                }
                            } catch (e: Exception) {
                                lastClearResult = "Error: ${e.message}"
                                isClearing = false
                                com.teamz.lab.debugger.utils.handleError(e)
                            }
                        }
                    }
                },
                onAIClick = onItemAIClick?.let { handler ->
                    {
                        val content = """
Storage Usage: $storageUsage
Current Usage: $storagePercent%

Storage cleanup clears app caches and temporary files to free up space.
                        """.trimIndent()
                        handler("Storage Cleanup", content)
                    }
                }
            )
        }

        // Native Ad (after Storage Cleanup)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item(key = "native_ad_storage") {
                val nativeAd = remember(healthAdCacheGen, "health_section_storage") { NativeAdManager.getAdForPosition("health_section_storage") }
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Battery Optimization Card (Quick Action)
        item(key = "battery_optimization") {
            // Show loading state immediately (like Device Info tab) to prevent blocking during scroll
            var isLoadingBattery by remember { mutableStateOf(true) }
            var batteryHealth by remember { mutableIntStateOf(70) }
            var batteryInfo by remember { mutableStateOf(context.string(R.string.loading)) }
            var isOptimizing by remember { mutableStateOf(false) }
            var lastOptimizeResult by remember { mutableStateOf<String?>(null) }
            var showBatteryGuideDialog by remember { mutableStateOf(false) }
            
            // Load battery data on background thread to prevent scrolling lag
            // Similar to Device Info tab's approach - show loading immediately, then update
            LaunchedEffect(Unit) {
                // Run on background thread to prevent blocking main thread during scroll
                val batteryHealthValue = withContext(Dispatchers.Default) {
                    com.teamz.lab.debugger.utils.getBatteryHealthPercent(context)
                }
                val batteryInfoValue = withContext(Dispatchers.Default) {
                    com.teamz.lab.debugger.utils.getBatteryChargingInfo(context)
                }
                // Update state after data is loaded (non-blocking)
                batteryHealth = batteryHealthValue
                batteryInfo = batteryInfoValue
                isLoadingBattery = false
            }
            
            // Update battery info periodically - pause when scrolling for better performance
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(15000) // Update every 15 seconds
                    // Skip update if user is scrolling to prevent lag
                    if (!listState.isScrollInProgress) {
                        // Run on background thread to prevent blocking
                        val batteryHealthValue = withContext(Dispatchers.Default) {
                            com.teamz.lab.debugger.utils.getBatteryHealthPercent(context)
                        }
                        val batteryInfoValue = withContext(Dispatchers.Default) {
                            com.teamz.lab.debugger.utils.getBatteryChargingInfo(context)
                        }
                        batteryHealth = batteryHealthValue
                        batteryInfo = batteryInfoValue
                    }
                }
            }
            
            // Battery Guide Dialog
            if (showBatteryGuideDialog) {
                AlertDialog(
                    onDismissRequest = { showBatteryGuideDialog = false },
                    title = {
                        Text(
                            text = "Battery Tips",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "To optimize battery life and health:",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "1. Enable Battery Saver Mode",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "• Go to Settings > Battery > Battery Saver",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "• Enable 'Turn on automatically' at 15% or 5%",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "2. Optimize App Battery Usage",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "• Go to Settings > Battery > Battery optimization",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "• Set apps to 'Optimized' or 'Restricted'",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "3. General Tips",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "• Lower screen brightness",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "• Close unused apps",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "• Don't keep phone at 100% charge",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showBatteryGuideDialog = false
                                val opened = com.teamz.lab.debugger.utils.openBatterySettings(context)
                                if (!opened) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Please go to Settings > Battery manually",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        ) {
                            Text("Open Settings")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBatteryGuideDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
            
            BatteryOptimizationCard(
                batteryHealth = batteryHealth,
                batteryInfo = batteryInfo,
                isLoading = isLoadingBattery,
                isOptimizing = isOptimizing,
                lastOptimizeResult = lastOptimizeResult,
                onOptimizeBattery = {
                    isOptimizing = true
                    
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.WidgetTapped,
                        mapOf(
                            "source" to "health_section",
                            "action" to "optimize_battery_button_clicked",
                            "battery_health_before" to batteryHealth
                        )
                    )
                    
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        InterstitialAdManager.showAdBeforeAction(
                            activity = activity,
                            actionName = "optimize_battery"
                        ) {
                            coroutineScope.launch {
                                try {
                                    val batteryHealthBefore = batteryHealth
                                    val (success, message, shouldOpenSettings) = com.teamz.lab.debugger.utils.optimizeBattery(context)
                                    isOptimizing = false
                                    
                                    if (success) {
                                        lastOptimizeResult = message
                                        kotlinx.coroutines.delay(1000)
                                        batteryHealth = com.teamz.lab.debugger.utils.getBatteryHealthPercent(context)
                                        batteryInfo = com.teamz.lab.debugger.utils.getBatteryChargingInfo(context)
                                        
                                        AnalyticsUtils.logEvent(
                                            AnalyticsEvent.WidgetTapped,
                                            mapOf(
                                                "source" to "health_section",
                                                "action" to "optimize_battery_completed",
                                                "success" to true,
                                                "battery_health_before" to batteryHealthBefore,
                                                "battery_health_after" to batteryHealth
                                            )
                                        )
                                    } else if (shouldOpenSettings) {
                                        lastOptimizeResult = message
                                        
                                        // Set pending action to retry battery optimization when user returns
                                        pendingBatteryAction = {
                                            coroutineScope.launch {
                                                try {
                                                    isOptimizing = true
                                                    val batteryHealthBefore = batteryHealth
                                                    val (retrySuccess, retryMessage, retryShouldOpenSettings) = com.teamz.lab.debugger.utils.optimizeBattery(context)
                                                    isOptimizing = false
                                                    
                                                    if (retrySuccess) {
                                                        lastOptimizeResult = retryMessage
                                                        kotlinx.coroutines.delay(1000)
                                                        batteryHealth = com.teamz.lab.debugger.utils.getBatteryHealthPercent(context)
                                                        batteryInfo = com.teamz.lab.debugger.utils.getBatteryChargingInfo(context)
                                                        
                                                        AnalyticsUtils.logEvent(
                                                            AnalyticsEvent.WidgetTapped,
                                                            mapOf(
                                                                "source" to "health_section",
                                                                "action" to "optimize_battery_completed_after_settings",
                                                                "success" to true,
                                                                "battery_health_before" to batteryHealthBefore,
                                                                "battery_health_after" to batteryHealth
                                                            )
                                                        )
                                                    } else {
                                                        lastOptimizeResult = retryMessage
                                                    }
                                                } catch (e: Exception) {
                                                    lastOptimizeResult = "Error: ${e.message}"
                                                    isOptimizing = false
                                                    com.teamz.lab.debugger.utils.handleError(e)
                                                }
                                            }
                                        }
                                        
                                        kotlinx.coroutines.delay(500)
                                        val settingsOpened = com.teamz.lab.debugger.utils.openBatterySettings(context)
                                        if (!settingsOpened) {
                                            showBatteryGuideDialog = true
                                            pendingBatteryAction = null // Clear pending if settings can't be opened
                                        }
                                        
                                        AnalyticsUtils.logEvent(
                                            AnalyticsEvent.WidgetTapped,
                                            mapOf(
                                                "source" to "health_section",
                                                "action" to "optimize_battery_opened_settings",
                                                "settings_opened" to settingsOpened
                                            )
                                        )
                                    } else {
                                        lastOptimizeResult = message
                                    }
                                } catch (e: Exception) {
                                    lastOptimizeResult = "Error: ${e.message}"
                                    isOptimizing = false
                                    com.teamz.lab.debugger.utils.handleError(e)
                                    AnalyticsUtils.logEvent(
                                        AnalyticsEvent.WidgetTapped,
                                        mapOf(
                                            "source" to "health_section",
                                            "action" to "optimize_battery_error",
                                            "error" to (e.message ?: "Unknown error")
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        isOptimizing = true
                        coroutineScope.launch {
                            try {
                                val (success, message, shouldOpenSettings) = com.teamz.lab.debugger.utils.optimizeBattery(context)
                                isOptimizing = false
                                
                                if (success) {
                                    lastOptimizeResult = message
                                } else if (shouldOpenSettings) {
                                    lastOptimizeResult = message
                                    kotlinx.coroutines.delay(500)
                                    val settingsOpened = com.teamz.lab.debugger.utils.openBatterySettings(context)
                                    if (!settingsOpened) {
                                        showBatteryGuideDialog = true
                                    }
                                } else {
                                    lastOptimizeResult = message
                                }
                            } catch (e: Exception) {
                                lastOptimizeResult = "Error: ${e.message}"
                                isOptimizing = false
                                com.teamz.lab.debugger.utils.handleError(e)
                            }
                        }
                    }
                },
                onAIClick = onItemAIClick?.let { handler ->
                    {
                        val content = """
Battery Health: $batteryHealth%
Battery Status: $batteryInfo

Android manages battery automatically. This card surfaces diagnostic tips (charging, temperature, saver mode) and deep-links to Android's battery settings.
                        """.trimIndent()
                        handler("Battery Health", content)
                    }
                }
            )
        }

        // Native Ad (after Battery Optimization)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item(key = "native_ad_battery") {
                val nativeAd = remember(healthAdCacheGen, "health_section_battery") { NativeAdManager.getAdForPosition("health_section_battery") }
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // App Cache Cleaner Card removed - redundant with Storage Cleanup
        // Both features do the same thing (clear app cache), so keeping only Storage Cleanup
        // which is more comprehensive and shows overall storage usage

        // Privacy Dashboard Card
        item(key = "privacy_dashboard") {
            // Show loading state immediately (like Device Info tab) to prevent blocking during scroll
            var isLoadingPrivacy by remember { mutableStateOf(true) }
            var privacyScore by remember { mutableIntStateOf(0) }
            var threats by remember { mutableStateOf<List<String>>(emptyList()) }
            var recentUsage by remember { mutableStateOf("") }
            
            // Load privacy data on background thread to prevent NetworkOnMainThreadException
            // Similar to Device Info tab's approach - show loading immediately, then update
            LaunchedEffect(Unit) {
                // Run all network operations on background thread
                val privacyScoreValue = withContext(Dispatchers.IO) {
                    calculatePrivacyScore(context)
                }
                val threatsValue = withContext(Dispatchers.IO) {
                    getPrivacyThreatsToday(context)
                }
                val recentUsageValue = withContext(Dispatchers.Default) {
                    getRecentCameraMicUsageLog()
                }
                // Update state after data is loaded (non-blocking)
                privacyScore = privacyScoreValue
                threats = threatsValue
                recentUsage = recentUsageValue
                isLoadingPrivacy = false
            }
            
            PrivacyDashboardCard(
                context = context,
                privacyScore = privacyScore,
                threats = threats,
                recentUsage = recentUsage,
                isLoading = isLoadingPrivacy,
                onAIClick = onItemAIClick?.let { handler ->
                    {
                        val content = """
Privacy Score: $privacyScore/100
Threats Today: ${if (threats.isEmpty()) "None" else threats.joinToString(", ")}
Recent Mic/Camera Usage:
$recentUsage
                        """.trimIndent()
                        handler("Privacy Dashboard", content)
                    }
                }
            )
        }

        // Native Ad (after Privacy Dashboard)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item(key = "native_ad_privacy") {
                val nativeAd = remember(healthAdCacheGen, "health_section_privacy") { NativeAdManager.getAdForPosition("health_section_privacy") }
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Today's Tasks Card
        item(key = "daily_tasks") {
            var dailyTasks by remember { mutableStateOf<List<HealthScoreUtils.DailyTask>>(emptyList()) }
            var completedCount by remember { mutableIntStateOf(0) }
            var taskCompletionMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
            
            // Load tasks and completion status on background thread
            LaunchedEffect(currentHealthScore) {
                withContext(Dispatchers.Default) {
                    val tasks = HealthScoreUtils.getDailyTasks(context, currentHealthScore)
                    val count = HealthScoreUtils.getCompletedTasksCount(context)
                    val completionMap = tasks.associate { it.id to HealthScoreUtils.isTaskCompleted(context, it.id) }
                    
                    dailyTasks = tasks
                    completedCount = count
                    taskCompletionMap = completionMap
                }
            }
            
            // Memoize the onTaskComplete callback
            val onTaskCompleteCallback = remember {
                { taskId: String ->
                    HealthScoreUtils.completeTask(context, taskId)
                    completedCount = HealthScoreUtils.getCompletedTasksCount(context)
                    // Update completion map
                    taskCompletionMap = taskCompletionMap + (taskId to true)
                    AnalyticsUtils.logEvent(AnalyticsEvent.AchievementUnlocked, mapOf(
                        "task_id" to taskId,
                        "tasks_completed" to completedCount
                    ))
                }
            }
            
            // Memoize the onAIClick callback
            val onAIClickCallback = remember(dailyTasks, completedCount, currentHealthScore, taskCompletionMap, onItemAIClick) {
                onItemAIClick?.let { handler ->
                    {
                        val tasksText = dailyTasks.joinToString("\n") { 
                            "${if (taskCompletionMap[it.id] == true) "✅" else "⬜"} ${it.title}"
                        }
                        val content = """
Today's Tasks (${completedCount}/${dailyTasks.size} completed):
$tasksText

Health Score: $currentHealthScore/10
                        """.trimIndent()
                        handler("Today's Tasks", content)
                    }
                }
            }
            
            if (dailyTasks.isNotEmpty()) {
                DailyTasksCard(
                    tasks = dailyTasks,
                    completedCount = completedCount,
                    taskCompletionMap = taskCompletionMap,
                    onTaskComplete = onTaskCompleteCallback,
                    onAIClick = onAIClickCallback
                )
            }
        }

        // Native Ad (after Today's Tasks)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item(key = "native_ad_tasks") {
                val nativeAd = remember(healthAdCacheGen, "health_section_tasks") { NativeAdManager.getAdForPosition("health_section_tasks") }
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Removed Device Analysis - redundant with Device Info tab

        // Temperature History Card
        item(key = "temperature_history") {
            // Memoize the onAIClick callback to avoid expensive operations on every recomposition
            val onAIClickCallback = remember(onItemAIClick, coroutineScope) {
                onItemAIClick?.let { handler ->
                    {
                        // Load data on background thread when callback is invoked
                        coroutineScope.launch(Dispatchers.Default) {
                            val history = HealthScoreUtils.getTemperatureHistory(context, 7)
                            val peakTemp = HealthScoreUtils.getPeakTemperature(context)
                            val trend = HealthScoreUtils.getTemperatureTrend(context)
                            val historyText = if (history.isNotEmpty()) {
                                history.joinToString("\n") { 
                                    "${it.date}: Max ${it.maxTemp.toInt()}°C, Avg ${it.avgTemp.toInt()}°C"
                                }
                            } else {
                                "No temperature history yet."
                            }
                            val content = """
7-Day Temperature History:
$historyText

Peak Temperature: ${peakTemp?.toInt() ?: "N/A"}°C
Trend: $trend
                            """.trimIndent()
                            withContext(Dispatchers.Main) {
                                handler("Temperature History", content)
                            }
                        }
                        Unit // Explicitly return Unit
                    }
                }
            }
            
            TemperatureHistoryCard(
                context = context,
                onAIClick = onAIClickCallback
            )
        }

        // Native Ad (after Temperature History)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item(key = "native_ad_temperature") {
                val nativeAd = remember(healthAdCacheGen, "health_section_temperature") { NativeAdManager.getAdForPosition("health_section_temperature") }
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // v3.1.11 W2 user-behavior insight — Health History block MOVED to position #2
        // (right after health_score) for above-the-fold visibility. Old position here
        // produced 19% reach despite 4.2 views/user repeat engagement.

        // Native Ad (after Temperature History — was after Health History before W2 move)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item(key = "native_ad_history") {
                val nativeAd = remember(healthAdCacheGen, "health_section_history") { NativeAdManager.getAdForPosition("health_section_history") }
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Intelligent Improvement Suggestions - At the bottom
        item(key = "recommendations") {
            val suggestions =
                HealthScoreUtils.getImprovementSuggestions(context, currentHealthScore)
            if (suggestions.isNotEmpty()) {
                ImprovementSuggestionsCard(
                    suggestions = suggestions, 
                    currentScore = currentHealthScore,
                    onAIClick = onItemAIClick?.let { handler ->
                        {
                            AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                                "source" to "health_recommendations",
                                "item_title" to "Smart Recommendations"
                            ))
                            val suggestionsText = suggestions.joinToString("\n") { "• $it" }
                            val content = """
Health Score: $currentHealthScore/10
Improvement Suggestions:
$suggestionsText
                            """.trimIndent()
                            handler("Smart Recommendations", content)
                        }
                    }
                )
            }
        }

        // Native Ad (after Recommendations)
        if (shouldShowNativeAds && nativeAds.isNotEmpty()) {
            item(key = "native_ad_recommendations") {
                val nativeAd = remember(healthAdCacheGen, "health_section_recommendations") { NativeAdManager.getAdForPosition("health_section_recommendations") }
                if (nativeAd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdMobNativeAdCard(nativeAd = nativeAd)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(80.dp)) // Extra space to prevent FAB overlap
        }

    }
}

@Composable
private fun QuickStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier,
        color = DesignSystemColors.NeonGreen,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = DesignSystemColors.Dark,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = DesignSystemColors.Dark,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DesignSystemColors.DarkII,
            )
        }
    }
}

@Composable
private fun PerformanceInsightsCard(
    insights: String,
    motivationalMessage: String
) {
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
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Insights",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Your Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = insights,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = motivationalMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ImprovementSuggestionsCard(
    suggestions: List<String>,
    currentScore: Int,
    onAIClick: (() -> Unit)? = null
) {
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
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "Suggestions",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart Recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                    if (currentScore < 8) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Based on your device's actual health data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = "Get AI insights about recommendations",
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Tip",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun DailyTasksCard(
    tasks: List<HealthScoreUtils.DailyTask>,
    completedCount: Int,
    taskCompletionMap: Map<String, Boolean>,
    onTaskComplete: (String) -> Unit,
    onAIClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Tasks",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Today's Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$completedCount/${tasks.size} completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = com.teamz.lab.debugger.utils.AIIcon.icon,
                            contentDescription = "Get AI insights about tasks",
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress bar
            LinearProgressIndicator(
                progress = { completedCount.toFloat() / tasks.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            tasks.forEach { task ->
                val isCompleted = taskCompletionMap[task.id] ?: false
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = isCompleted,
                        onCheckedChange = { checked ->
                            if (checked && !isCompleted) {
                                onTaskComplete(task.id)
                            }
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = task.icon,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (task.priority == HealthScoreUtils.TaskPriority.HIGH) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCompleted) 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                else 
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!isCompleted) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            if (completedCount == tasks.size && tasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🎉 All tasks completed! Great job!",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureHistoryCard(
    context: android.content.Context,
    onAIClick: (() -> Unit)? = null
) {
    // Load temperature data on background thread to avoid blocking UI
    var history by remember { mutableStateOf<List<HealthScoreUtils.TemperatureDataPoint>>(emptyList()) }
    var peakTemp by remember { mutableStateOf<Float?>(null) }
    var trend by remember { mutableStateOf<String>("") }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            val loadedHistory = HealthScoreUtils.getTemperatureHistory(context, 7)
            val loadedPeakTemp = HealthScoreUtils.getPeakTemperature(context)
            val loadedTrend = HealthScoreUtils.getTemperatureTrend(context)
            
            history = loadedHistory
            peakTemp = loadedPeakTemp
            trend = loadedTrend
            isLoading = false
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
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = "Temperature",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Temperature History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
                            contentDescription = "Get AI insights about temperature",
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                }
            } else if (history.isNotEmpty()) {
                // Peak temperature alert
                peakTemp?.let { peak ->
                    if (peak > 40f) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Peak: ${peak.toInt()}°C this week",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // Trend indicator
                if (trend.isNotEmpty() && trend != "Not enough data") {
                    Text(
                        text = trend,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Simple temperature graph (text-based bars)
                history.forEach { dataPoint ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dataPoint.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Temperature bar visualization
                        val barWidth = ((dataPoint.avgTemp / 50f) * 100f).coerceIn(0f, 100f).toInt()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            dataPoint.avgTemp > 40f -> MaterialTheme.colorScheme.error
                                            dataPoint.avgTemp > 35f -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.primaryContainer
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${dataPoint.avgTemp.toInt()}°C",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Text(
                    text = "No temperature history yet. Temperature will be tracked automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun HealthHistoryCard(
    context: android.content.Context,
    onAIClick: (() -> Unit)? = null
) {
    val history = remember {
        HealthScoreUtils.getHealthScoreHistory(context, 7)
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
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "7-Day History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
                            contentDescription = "Get AI insights about health history",
                            tint = com.teamz.lab.debugger.utils.AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (history.isNotEmpty()) {
                history.forEach { (date, score) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = getScoreColor(score).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$score/10",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = getScoreColor(score),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Text(
                    text = "No health history yet. Start scanning to build your history!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun getScoreColor(score: Int): Color {
    return when {
        score >= 9 -> MaterialTheme.colorScheme.primary
        score >= 7 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        score >= 5 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
}

// Device Analysis Card removed - redundant with Device Info tab 

/**
 * Generate health share text for AI and sharing
 */
private fun generateHealthShareText(context: android.content.Context, healthScore: Int): String {
    val streak = HealthScoreUtils.getDailyStreak(context)
    val bestScore = HealthScoreUtils.getBestScore(context)
    val totalScans = HealthScoreUtils.getTotalScans(context)
    val history = HealthScoreUtils.getHealthScoreHistory(context, 7)
    val suggestions = HealthScoreUtils.getImprovementSuggestions(context, healthScore)
    
    return buildString {
        appendLine("📊 DEVICE HEALTH REPORT")
        appendLine("========================")
        appendLine()
        appendLine("🏆 Current Health Score: $healthScore/100")
        appendLine()
        appendLine("📈 Health Stats:")
        appendLine("  • Daily Streak: $streak days")
        appendLine("  • Best Score: $bestScore/100")
        appendLine("  • Total Scans: $totalScans")
        appendLine()
        
        if (history.isNotEmpty()) {
            appendLine("📅 Recent History (Last 7 Days):")
            history.take(7).forEach { (date, score) ->
                appendLine("  • $date: $score/100")
            }
            appendLine()
        }
        
        if (suggestions.isNotEmpty()) {
            appendLine("💡 Improvement Suggestions:")
            suggestions.forEach { suggestion ->
                appendLine("  • $suggestion")
            }
            appendLine()
        }
        
        appendLine("Score Rating: ${getScoreRating(healthScore)}")
    }
}

private fun getScoreRating(score: Int): String {
    return when {
        score >= 90 -> "Excellent! Your device is in top condition."
        score >= 75 -> "Good! Minor improvements possible."
        score >= 60 -> "Fair. Some attention needed."
        score >= 40 -> "Needs work. Several issues detected."
        else -> "Critical. Immediate attention recommended."
    }
}

@Composable
private fun PrivacyDashboardCard(
    context: android.content.Context,
    privacyScore: Int,
    threats: List<String>,
    recentUsage: String,
    isLoading: Boolean = false,
    onAIClick: (() -> Unit)? = null
) {
    val hasRecentUsage = recentUsage.contains("Recent usage detected")
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            when {
                privacyScore < 50 -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                privacyScore < 70 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Privacy",
                    tint = when {
                        privacyScore < 50 -> MaterialTheme.colorScheme.error
                        privacyScore < 70 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy Dashboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = AIIcon.icon,
                            contentDescription = "Get AI insights about privacy",
                            tint = AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Privacy Score
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Privacy Score",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = context.string(R.string.loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                    Surface(
                        color = when {
                            privacyScore >= 80 -> DesignSystemColors.NeonGreen
                            privacyScore >= 60 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$privacyScore/100",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                privacyScore >= 80 -> DesignSystemColors.Dark
                                privacyScore >= 60 -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Progress bar - show loading state
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                } else {
                LinearProgressIndicator(
                    progress = { privacyScore / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        privacyScore >= 80 -> DesignSystemColors.NeonGreen
                        privacyScore >= 60 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Threats Today
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading threats...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else if (threats.isNotEmpty()) {
                Text(
                    text = "Threats Today:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                threats.take(3).forEach { threat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Threat",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = threat,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Safe",
                        tint = DesignSystemColors.NeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No threats detected today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
            
            // Recent Mic/Camera Usage
            if (hasRecentUsage) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Recent usage",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recent mic/camera access detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Info text
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Monitors device security and privacy threats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun RamOptimizationCard(
    ramUsage: String,
    ramPercent: Int,
    isClearing: Boolean,
    lastClearResult: String?,
    onClearRam: () -> Unit,
    onAIClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            when {
                ramPercent > 85 -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                ramPercent > 70 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "RAM",
                    tint = when {
                        ramPercent > 85 -> MaterialTheme.colorScheme.error
                        ramPercent > 70 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Memory Usage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ramUsage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = AIIcon.icon,
                            contentDescription = "Get AI insights about RAM",
                            tint = AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // RAM Usage Progress
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Memory Usage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = when {
                            ramPercent > 85 -> MaterialTheme.colorScheme.errorContainer
                            ramPercent > 70 -> DesignSystemColors.NeonGreen
                            else -> DesignSystemColors.NeonGreen
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$ramPercent%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            // Always use black text on neon green background for visibility
                            color = when {
                                ramPercent > 85 -> MaterialTheme.colorScheme.onErrorContainer
                                else -> DesignSystemColors.Dark // Black text on neon green
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ramPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        ramPercent > 85 -> MaterialTheme.colorScheme.error
                        ramPercent > 70 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Free RAM Button
            Button(
                onClick = onClearRam,
                enabled = !isClearing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        ramPercent > 85 -> MaterialTheme.colorScheme.error
                        ramPercent > 70 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                if (isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refreshing…")
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Refresh",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (lastClearResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = DesignSystemColors.NeonGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = lastClearResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Shows current memory usage. Android manages RAM automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun StorageCleanupCard(
    storageUsage: String,
    storagePercent: Int,
    isClearing: Boolean,
    lastClearResult: String?,
    onCleanStorage: () -> Unit,
    onAIClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            when {
                storagePercent > 90 -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                storagePercent > 80 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Storage",
                    tint = when {
                        storagePercent > 90 -> MaterialTheme.colorScheme.error
                        storagePercent > 80 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Storage Cleanup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = storageUsage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = AIIcon.icon,
                            contentDescription = "Get AI insights about storage",
                            tint = AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Storage Usage Progress
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Storage Usage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = when {
                            storagePercent > 90 -> MaterialTheme.colorScheme.errorContainer
                            storagePercent > 80 -> DesignSystemColors.NeonGreen
                            else -> DesignSystemColors.NeonGreen
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$storagePercent%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                storagePercent > 90 -> MaterialTheme.colorScheme.onErrorContainer
                                else -> DesignSystemColors.Dark
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { storagePercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        storagePercent > 90 -> MaterialTheme.colorScheme.error
                        storagePercent > 80 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Clean Storage Button
            Button(
                onClick = onCleanStorage,
                enabled = !isClearing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        storagePercent > 90 -> MaterialTheme.colorScheme.error
                        storagePercent > 80 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                if (isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cleaning...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clean Storage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Show result message
            if (lastClearResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = DesignSystemColors.NeonGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = lastClearResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            
            // Info text
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Clears app caches and temporary files to free up storage space",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun BatteryOptimizationCard(
    batteryHealth: Int,
    batteryInfo: String,
    isLoading: Boolean = false,
    isOptimizing: Boolean,
    lastOptimizeResult: String?,
    onOptimizeBattery: () -> Unit,
    onAIClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            when {
                batteryHealth < 50 -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                batteryHealth < 70 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryStd,
                    contentDescription = "Battery",
                    tint = when {
                        batteryHealth < 50 -> MaterialTheme.colorScheme.error
                        batteryHealth < 70 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Battery Health",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = batteryInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    } else {
                    Text(
                        text = batteryInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Visible
                    )
                    }
                }
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = AIIcon.icon,
                            contentDescription = "Get AI insights about battery",
                            tint = AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Battery Health Progress
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Battery Health",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = when {
                            batteryHealth < 50 -> MaterialTheme.colorScheme.errorContainer
                            batteryHealth < 70 -> DesignSystemColors.NeonGreen
                            else -> DesignSystemColors.NeonGreen
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$batteryHealth%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                batteryHealth < 50 -> MaterialTheme.colorScheme.onErrorContainer
                                else -> DesignSystemColors.Dark
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { batteryHealth / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        batteryHealth < 50 -> MaterialTheme.colorScheme.error
                        batteryHealth < 70 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Optimize Battery Button
            Button(
                onClick = onOptimizeBattery,
                enabled = !isOptimizing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        batteryHealth < 50 -> MaterialTheme.colorScheme.error
                        batteryHealth < 70 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                if (isOptimizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Battery Tips",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (lastOptimizeResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = DesignSystemColors.NeonGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = lastOptimizeResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Shows battery diagnostics + opens Android's battery settings. Android manages battery automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun AppCacheCleanerCard(
    cacheSize: String,
    appCount: Int,
    isClearing: Boolean,
    lastClearResult: String?,
    onClearCache: () -> Unit,
    onAIClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Cache",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Cache Cleaner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$cacheSize in $appCount apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (onAIClick != null) {
                    IconButton(
                        onClick = onAIClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = AIIcon.icon,
                            contentDescription = "Get AI insights about cache",
                            tint = AIIcon.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Cache Info
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Cache",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = DesignSystemColors.NeonGreen,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = cacheSize,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DesignSystemColors.Dark,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$appCount apps have cache data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Clear Cache Button
            Button(
                onClick = onClearCache,
                enabled = !isClearing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clearing...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear App Cache",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Show result message
            if (lastClearResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = DesignSystemColors.NeonGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = lastClearResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            
            // Info text
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Clears temporary files from apps to free up storage space",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}