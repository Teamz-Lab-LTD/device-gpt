package com.teamz.lab.debugger.ui

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.*
import com.teamz.lab.debugger.utils.InterstitialAdManager
import kotlinx.coroutines.launch
import android.util.Log
import com.teamz.lab.debugger.BuildConfig
import com.google.android.gms.ads.nativead.NativeAd

/**
 * Leaderboard Section - Child-friendly UI
 */
@Composable
fun LeaderboardSection(activity: Activity) {
    val context = activity
    val scope = rememberCoroutineScope()
    
    // RemoteConfig settings
    val adFrequency = remember { RemoteConfigUtils.getLeaderboardAdFrequency() }
    val shouldShowInterstitial = remember { RemoteConfigUtils.shouldShowLeaderboardInterstitialAds() }
    
    // State - Default to most desired category (Cool Phone)
    var selectedCategory by remember { mutableStateOf(LeaderboardCategory.THERMAL_EFFICIENCY) }
    var leaderboardEntries by remember { mutableStateOf<List<CategoryLeaderboardEntry>>(emptyList()) }
    var appPowerEntries by remember { mutableStateOf<List<AppPowerLeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) } // Start with loading state
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var viewCount by remember { mutableIntStateOf(0) }
    var userRank by remember { mutableIntStateOf(-1) }
    var retryCount by remember { mutableIntStateOf(0) }
    
    // Dialog state - using separate state to ensure proper updates
    var showDeviceInsights by remember { mutableStateOf(false) }
    var showBestDevices by remember { mutableStateOf(false) }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) } // Track which device to show insights for
    var showCategoryInfoDialog by remember { mutableStateOf(false) }
    var selectedCategoryForInfo by remember { mutableStateOf<LeaderboardCategory?>(null) }
    
    // Trigger flags for ad callbacks - these will be set by ad callbacks and watched by LaunchedEffect
    var triggerDeviceInsights by remember { mutableStateOf(0) }
    var triggerBestDevices by remember { mutableStateOf(0) }
    
    // Centralized function to show full-screen ad after user action (AdMob policy compliant)
    // This ensures ads are shown consistently and properly across all dialogs
    val showFullScreenAdAfterAction: () -> Unit = {
        InterstitialAdManager.showAdIfAvailable(activity) {
            // Ad shown and dismissed - callback can be used for analytics or cleanup if needed
        }
    }
    
    // Get normalized device ID for user rank
    val normalizedDevice = remember { DeviceNameNormalizer.normalizeDeviceName() }
    
    // Data retention reminder
    var showDataRetentionReminder by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        showDataRetentionReminder = LeaderboardManager.shouldShowDataRetentionReminder(context)
    }
    
    // Show interstitial ad on first view (if enabled and not debug mode)
    // Using centralized ad showing function with global throttling
    // Note: Global cooldown in InterstitialAdManager prevents excessive ads
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG && shouldShowInterstitial && viewCount == 0) {
            // Show ad on first view only (throttling handled globally)
            showFullScreenAdAfterAction()
        }
        viewCount++
        // Log analytics for leaderboard tab view
        AnalyticsUtils.logEvent(AnalyticsEvent.TabLeaderboardViewed, mapOf(
            "view_count" to viewCount,
            "category" to selectedCategory.id
        ))
    }
    
    // Watch for trigger flags and update dialog state
    LaunchedEffect(triggerDeviceInsights) {
        if (triggerDeviceInsights > 0) {
            showDeviceInsights = true
        }
    }
    
    LaunchedEffect(triggerBestDevices) {
        if (triggerBestDevices > 0) {
            showBestDevices = true
        }
    }
    
    // Load leaderboard data with retry logic
    LaunchedEffect(selectedCategory, retryCount) {
        // Loading state is already set in onCategorySelected callback
        // But ensure it's set here too in case of retry
        isLoading = true
        hasError = false
        errorMessage = null
        scope.launch {
            // Debug function removed - was causing performance issues
            // To debug, call manually: scope.launch { LeaderboardManager.debugLeaderboardStructure() }
            try {
                // Check if this is app power monitoring category
                if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) {
                    // Load app power leaderboard
                    val entries = LeaderboardManager.getAppPowerLeaderboardEntries(100)
                    appPowerEntries = entries
                    leaderboardEntries = emptyList() // Clear device entries
                    userRank = -1 // No user rank for app power
                } else {
                    // Load device leaderboard
                    val entries = LeaderboardManager.getLeaderboardEntries(selectedCategory.id, 100)
                    if (entries.isEmpty() && retryCount == 0) {
                        // First attempt with empty result - might be permission issue or no data yet
                        // Force upload immediately to ensure data is available
                        com.teamz.lab.debugger.utils.LeaderboardDataUpload.forceUpload(context)
                        // Wait a bit for upload to complete and retry
                        kotlinx.coroutines.delay(3000)
                        val retryEntries = LeaderboardManager.getLeaderboardEntries(selectedCategory.id, 100)
                        if (retryEntries.isEmpty()) {
                            // No data yet - this is normal for new leaderboard
                            // Don't show error, just show empty state
                            leaderboardEntries = emptyList()
                            userRank = -1
                        } else {
                            leaderboardEntries = retryEntries
                            userRank = LeaderboardManager.getUserRank(selectedCategory.id, normalizedDevice.normalizedId)
                        }
                    } else {
                        leaderboardEntries = entries
                        userRank = LeaderboardManager.getUserRank(selectedCategory.id, normalizedDevice.normalizedId)
                    }
                    appPowerEntries = emptyList() // Clear app power entries
                }
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                hasError = true
                when (e.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        errorMessage = "Leaderboard access requires authentication. Please wait a moment..."
                        // Try to ensure anonymous auth
                        LeaderboardManager.initialize(context)
                        kotlinx.coroutines.delay(2000)
                        retryCount++
                    }
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> {
                        errorMessage = "Leaderboard is temporarily unavailable. Please try again later."
                    }
                    else -> {
                        errorMessage = "Unable to load leaderboard. Please check your connection."
                    }
                }
            } catch (e: Exception) {
                hasError = true
                errorMessage = "Something went wrong. Please try again."
                Log.e("LeaderboardSection", "Error loading leaderboard", e)
            } finally {
                isLoading = false
            }
        }
    }
    
    // Trigger automatic data upload when leaderboard is viewed (throttled to avoid excessive costs)
    // Only upload if enough time has passed since last upload (handled by uploadOnAppStart throttling)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000) // Wait a bit after view for Firebase Auth
        scope.launch {
            // Use regular upload (throttled) instead of force upload to avoid excessive Firebase costs
            // This respects the 1-hour minimum interval between uploads
            com.teamz.lab.debugger.utils.LeaderboardDataUpload.uploadOnAppStart(context)
        }
    }
    
    // Collapsible trust explanation
    var isTrustExpanded by remember { mutableStateOf(false) }
    
    // Native Banner Ad at top (AdMob recommended placement) - stable across recompositions
    // AdMob Best Practice: Use different ad for top banner vs list ads to maximize revenue
    // Only change when ad pool changes, not on every recomposition
    val topBannerAd = remember(NativeAdManager.nativeAds.size) {
        // Use position-specific ad assignment to ensure different ads in different places
        NativeAdManager.getAdForPosition("leaderboard_top_banner")
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Fixed header section (trust explanation and category selector)
        Column(
            modifier = Modifier.padding(top = 16.dp)
        ) {
            // Collapsible trust explanation header
            TrustExplanationHeader(
                isExpanded = isTrustExpanded,
                onToggle = { 
                    isTrustExpanded = !isTrustExpanded
                    AnalyticsUtils.logEvent(AnalyticsEvent.InfoExpanded, mapOf(
                        "item" to "trust_explanation",
                        "expanded" to isTrustExpanded
                    ))
                }
            )
            
            // Animated expandable trust card with swipe to close
            AnimatedVisibility(
                visible = isTrustExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                var totalDrag by remember { mutableStateOf(0f) }
                val threshold = 100f // pixels
                val onClose: () -> Unit = { isTrustExpanded = false }
                
                TrustExplanationCard(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (totalDrag < -threshold) {
                                        // Swiped up enough - close
                                        onClose()
                                    }
                                    totalDrag = 0f
                                }
                            ) { change, dragAmount ->
                                if (dragAmount < 0) { // Only allow upward swipes
                                    totalDrag += dragAmount
                                }
                            }
                        }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Category selector - set loading state immediately when category changes
            CategorySelector(
                selectedCategory = selectedCategory,
                onCategorySelected = { newCategory ->
                    AnalyticsUtils.logEvent(AnalyticsEvent.TabLeaderboardViewed, mapOf(
                        "category" to newCategory.id,
                        "category_name" to newCategory.displayName
                    ))
                    // Set loading state and clear entries immediately to prevent empty view flash
                    isLoading = true
                    hasError = false
                    errorMessage = null
                    leaderboardEntries = emptyList()
                    appPowerEntries = emptyList()
                    userRank = -1
                    selectedCategory = newCategory
                },
                onCategoryInfoClick = { category ->
                    AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                        "source" to "leaderboard_category_info",
                        "category" to category.id
                    ))
                    selectedCategoryForInfo = category
                    showCategoryInfoDialog = true
                }
            )
        }
        
        // Track native ads list for stable ad assignments
        // Use derivedStateOf to reactively track ad pool changes
        val nativeAdsList = remember { NativeAdManager.nativeAds }
        val validAdsSize = remember { derivedStateOf { nativeAdsList.filterNotNull().size } }.value
        
        // Pre-calculate ad assignments to prevent frequent changes
        // AdMob Best Practice: Assign DIFFERENT ads to different positions to maximize revenue
        // This ensures each ad position gets a stable, unique ad that doesn't change on recomposition
        // Only recalculate when entries, category, or ad pool size actually change
        val adAssignments = remember(
            if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) appPowerEntries.size else leaderboardEntries.size,
            selectedCategory.id,
            validAdsSize
        ) {
            val validAds = NativeAdManager.nativeAds.filterNotNull()
            if (validAds.isEmpty()) {
                emptyMap<Int, NativeAd>()
            } else {
                val assignments: MutableMap<Int, NativeAd> = mutableMapOf()
                val entriesSize = if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) {
                    appPowerEntries.size
                } else {
                    leaderboardEntries.size
                }
                (0 until entriesSize).forEach { index ->
                    if ((index + 1) % adFrequency == 0) {
                        // Use position-specific ad assignment to ensure different ads in different positions
                        // This maximizes revenue by preventing ad fatigue (AdMob best practice)
                        val positionId = "leaderboard_list_${selectedCategory.id}_$index"
                        val ad = NativeAdManager.getAdForPosition(positionId)
                        if (ad != null) {
                            assignments[index] = ad
                        }
                    }
                }
                assignments
            }
        }
        
        // Scrollable content (ad, user rank, and list)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Native Banner Ad at top (AdMob recommended placement)
            topBannerAd?.let {
                item {
                    AdMobNativeAdCard(nativeAd = it, bottomPadding = 8)
                }
            }
            
            // User rank display with navigation to insights (only for device categories)
            if (userRank > 0 && selectedCategory != LeaderboardCategory.APP_POWER_MONITORING) {
                item {
                        UserRankCard(
                        rank = userRank, 
                        category = selectedCategory,
                        totalEntries = leaderboardEntries.size,
                        onViewInsights = { 
                            AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                                "source" to "leaderboard_view_insights",
                                "category" to selectedCategory.id,
                                "user_rank" to userRank
                            ))
                            // Open dialog immediately - no ad before opening
                            triggerDeviceInsights++
                        },
                        onViewBestDevices = { 
                            AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                                "source" to "leaderboard_view_best_devices",
                                "category" to selectedCategory.id,
                                "user_rank" to userRank
                            ))
                            // Open dialog immediately - no ad before opening
                            triggerBestDevices++
                        }
                    )
                }
            }
            
            // Content based on state
            if (isLoading) {
                items(5) {
                    ShimmerLeaderboardCard()
                }
            } else if (hasError) {
                item {
                    ErrorStateCard(
                        message = errorMessage ?: "Unable to load leaderboard",
                        onRetry = { retryCount++ }
                    )
                }
            } else if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) {
                // App Power Leaderboard
                if (appPowerEntries.isEmpty()) {
                    item {
                        EmptyAppPowerLeaderboardCard(
                            onInfoClick = {
                                // Show info about app power monitoring
                            }
                        )
                    }
                } else {
                    // Render app power entries
                    appPowerEntries.forEachIndexed { index, entry ->
                        item(key = "app_entry_$index") {
                            AppPowerLeaderboardEntryCard(
                                rank = index + 1,
                                entry = entry
                            )
                        }
                        
                        // Show native ad every N entries
                        adAssignments[index]?.let { nativeAd ->
                            item(key = "app_ad_$index") {
                                AdMobNativeAdCard(nativeAd = nativeAd, bottomPadding = 16)
                            }
                        }
                    }
                }
            } else if (leaderboardEntries.isEmpty()) {
                item {
                    EmptyLeaderboardCard(
                        category = selectedCategory,
                        onUploadClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    // Force upload data immediately
                                    com.teamz.lab.debugger.utils.LeaderboardDataUpload.forceUpload(context)
                                    // Wait a bit for upload to complete
                                    kotlinx.coroutines.delay(2000)
                                    // Reload leaderboard
                                    retryCount++
                                } catch (e: Exception) {
                                    hasError = true
                                    errorMessage = "Failed to upload data. Please try again."
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )
                }
            } else {
                // Render leaderboard entries with ads interspersed
                // Use pre-calculated assignments to prevent frequent changes
                leaderboardEntries.forEachIndexed { index, entry ->
                    // Leaderboard entry
                    item(key = "entry_$index") {
                        LeaderboardEntryCard(
                            rank = index + 1,
                            entry = entry,
                            category = selectedCategory,
                            onClick = {
                                AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                                    "source" to "leaderboard_entry",
                                    "category" to selectedCategory.id,
                                    "rank" to (index + 1),
                                    "device_id" to entry.normalizedDeviceId
                                ))
                                // Set selected device and show insights
                                selectedDeviceId = entry.normalizedDeviceId
                                showDeviceInsights = true
                            }
                        )
                    }
                    
                    // Show native ad every N entries (AdMob policy compliant)
                    // Use pre-calculated assignment to prevent frequent changes
                    adAssignments[index]?.let { nativeAd ->
                        item(key = "ad_$index") {
                            AdMobNativeAdCard(nativeAd = nativeAd, bottomPadding = 16)
                        }
                    }
                }
            }
        }
    }
    
    // Device Insights - Show as large dialog with better UX
    if (showDeviceInsights) {
        AlertDialog(
            onDismissRequest = { 
                showDeviceInsights = false
                selectedDeviceId = null // Reset selected device
                // Show full-screen ad after user dismisses dialog (centralized)
                showFullScreenAdAfterAction()
            },
            title = { 
                Text(
                    text = "Device Insights",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                // Large modal with proper scrolling - increased height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(650.dp)
                ) {
                    DeviceInsightsScreen(
                        activity = activity,
                        normalizedDeviceId = selectedDeviceId ?: normalizedDevice.normalizedId
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    // Close dialog first
                    showDeviceInsights = false
                    selectedDeviceId = null // Reset selected device
                    // Show full-screen ad after user action (centralized)
                    showFullScreenAdAfterAction()
                }) {
                    Text(
                        "Close",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
    
    // Best Devices - Show as dialog with proper scrolling
    if (showBestDevices) {
        AlertDialog(
            onDismissRequest = { 
                showBestDevices = false
                // Show full-screen ad after user dismisses dialog (centralized)
                showFullScreenAdAfterAction()
            },
            title = { 
                Text(
                    text = "Best Devices",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                // Larger modal with compact UI
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(700.dp)
                ) {
                    BestDevicesScreen(
                        activity = activity,
                        category = selectedCategory
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    // Close dialog first
                    showBestDevices = false
                    // Show full-screen ad after user action (centralized)
                    showFullScreenAdAfterAction()
                }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Data retention reminder dialog
    if (showDataRetentionReminder) {
        DataRetentionReminderDialog(
            activity = activity,
            onDismiss = { showDataRetentionReminder = false }
        )
    }
    
    // Category info dialog
    selectedCategoryForInfo?.let { category ->
        if (showCategoryInfoDialog) {
            CategoryInfoDialog(
                category = category,
                onDismiss = {
                    showCategoryInfoDialog = false
                    selectedCategoryForInfo = null
                }
            )
        }
    }
}

@Composable
fun CategoryInfoDialog(
    category: LeaderboardCategory,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(category.icon, fontSize = 24.sp)
                Text(
                    text = "What is ${category.displayName}?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Simple explanation for non-tech users
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📱 Simple Explanation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = category.childFriendlyExplanation,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // Technical explanation for tech users
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⚙️ Technical Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = category.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "📊 How It's Measured:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when (category) {
                                LeaderboardCategory.POWER_EFFICIENCY -> "Based on total power consumption (Watts) measured using Android BatteryManager API. Lower power usage = better rank."
                                LeaderboardCategory.CPU_PERFORMANCE -> "Based on CPU benchmark scores and processing speed. Higher performance = better rank."
                                LeaderboardCategory.CAMERA_EFFICIENCY -> "Based on power consumed per photo taken. Lower power per photo = better rank."
                                LeaderboardCategory.DISPLAY_EFFICIENCY -> "Based on screen brightness vs power consumption ratio. Higher brightness with lower power = better rank."
                                LeaderboardCategory.HEALTH_SCORE -> "Based on overall device health metrics including battery health, storage, and system performance."
                                LeaderboardCategory.POWER_TREND -> "Based on power consumption trends over time. Devices showing improvement = better rank."
                                LeaderboardCategory.COMPONENT_OPTIMIZATION -> "Based on balanced power distribution across CPU, GPU, Display, and Network components."
                                LeaderboardCategory.THERMAL_EFFICIENCY -> "Based on device temperature during heavy usage. Cooler devices = better rank."
                                LeaderboardCategory.PERFORMANCE_CONSISTENCY -> "Based on frame rate stability and absence of lag. Smoother performance = better rank."
                                LeaderboardCategory.APP_POWER_MONITORING -> "Based on per-app battery impact (%/hour). Shows which apps consume the most power."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it!")
            }
        }
    )
}

@Composable
fun CategorySelector(
    selectedCategory: LeaderboardCategory,
    onCategorySelected: (LeaderboardCategory) -> Unit,
    onCategoryInfoClick: ((LeaderboardCategory) -> Unit)? = null
) {
    // Custom ordered list: Most desired categories first, then others
    val orderedCategories = remember {
        listOf(
            // Top 4 most desired categories (in order of preference)
            LeaderboardCategory.THERMAL_EFFICIENCY,      // 1. Cool Phone
            LeaderboardCategory.PERFORMANCE_CONSISTENCY, // 2. Smooth Runner
            LeaderboardCategory.APP_POWER_MONITORING,    // 3. App Power Ranking
            LeaderboardCategory.HEALTH_SCORE,            // 4. Healthy Phone
            // All other categories after the top 4
            LeaderboardCategory.POWER_EFFICIENCY,
            LeaderboardCategory.CPU_PERFORMANCE,
            LeaderboardCategory.CAMERA_EFFICIENCY,
            LeaderboardCategory.DISPLAY_EFFICIENCY,
            LeaderboardCategory.POWER_TREND,
            LeaderboardCategory.COMPONENT_OPTIMIZATION
        )
    }
    
    ScrollableTabRow(
        selectedTabIndex = orderedCategories.indexOf(selectedCategory),
        modifier = Modifier.fillMaxWidth()
    ) {
        orderedCategories.forEach { category ->
            Tab(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(category.icon, fontSize = 16.sp)
                        Text(
                            category.displayName,
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (onCategoryInfoClick != null) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable(
                                        onClick = {
                                            onCategoryInfoClick(category)
                                        },
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "What is ${category.displayName}?",
                                    modifier = Modifier.fillMaxSize(),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun LeaderboardEntryCard(
    rank: Int,
    entry: CategoryLeaderboardEntry,
    category: LeaderboardCategory,
    onClick: () -> Unit = {}
) {
    val trustBadge = calculateTrustBadge(entry.userCount, entry.dataQuality)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick), // Make card clickable
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Device name
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                // Score with explanation - show "No data" for zero scores
                val scoreText = if (entry.avgScore == 0.0) {
                    "Score: No data"
                } else {
                    "Score: ${entry.avgScore.toInt()}/100"
                }
                Text(
                    text = scoreText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (entry.avgScore == 0.0) 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else 
                        MaterialTheme.colorScheme.primary
                )
                
                // Trust indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrustBadgeIcon(badge = trustBadge)
                    Text(
                        text = if (entry.userCount == 1) 
                            "Verified by 1 user" 
                        else 
                            "Verified by ${entry.userCount} users",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun TrustExplanationHeader(
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Trust info",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Why you can trust this data",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun TrustExplanationCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) {
                DesignSystemColors.DarkII
            } else {
                DesignSystemColors.White
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Grid layout for trust points (2 columns)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TrustPointCard(
                        icon = "📱",
                        title = "Real system measurements",
                        description = "Uses Android BatteryManager API (P = V × I) for actual power consumption, not estimates",
                        modifier = Modifier.weight(1f)
                    )
                    TrustPointCard(
                        icon = "🌐",
                        title = "Real network tests",
                        description = "Actually downloads/uploads data to measure speed - not estimated from signal strength",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TrustPointCard(
                        icon = "👥",
                        title = "From real users",
                        description = "Data comes from actual device measurements by users like you",
                        modifier = Modifier.weight(1f)
                    )
                    TrustPointCard(
                        icon = "🛡️",
                        title = "Privacy protected",
                        description = "Your data is anonymous - we don't know who you are",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun TrustPointCard(
    icon: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background == DesignSystemColors.Dark) {
                DesignSystemColors.DarkII
            } else {
                DesignSystemColors.White
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = icon,
                fontSize = 28.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun TrustPoint(
    icon: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TrustBadgeIcon(badge: TrustBadge) {
    val (icon, color, text) = when(badge) {
        TrustBadge.VERIFIED -> Triple("✅", DesignSystemColors.NeonGreen, "Verified")
        TrustBadge.HIGH -> Triple("⭐", MaterialTheme.colorScheme.primary, "High Trust")
        TrustBadge.MEDIUM -> Triple("📊", MaterialTheme.colorScheme.secondary, "Medium Trust")
        TrustBadge.LOW -> Triple("📝", MaterialTheme.colorScheme.onSurfaceVariant, "Low Trust")
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp
        )
    }
}

fun calculateTrustBadge(userCount: Int, dataQuality: Int): TrustBadge {
    return when {
        userCount >= 100 && dataQuality >= 4 -> TrustBadge.VERIFIED
        userCount >= 50 && dataQuality >= 3 -> TrustBadge.HIGH
        userCount >= 10 && dataQuality >= 2 -> TrustBadge.MEDIUM
        else -> TrustBadge.LOW
    }
}

@Composable
fun UserRankCard(
    rank: Int, 
    category: LeaderboardCategory,
    totalEntries: Int = 0,
    onViewInsights: (() -> Unit)? = null,
    onViewBestDevices: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DesignSystemColors.NeonGreen.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏆",
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your Rank: #$rank",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (totalEntries > 0) {
                            val percentile = calculateTopPercent(rank, totalEntries)
                            "You're in the top $percentile% for ${category.displayName}!"
                        } else {
                            "Ranked #$rank for ${category.displayName}!"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Action buttons - always show if callbacks are provided
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onViewInsights != null) {
                    Button(
                        onClick = onViewInsights,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("View Insights", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onViewBestDevices != null) {
                    Button(
                        onClick = onViewBestDevices,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesignSystemColors.NeonGreen,
                            contentColor = DesignSystemColors.Dark
                        )
                    ) {
                        Text("Best Devices", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * Calculate accurate percentile based on rank and total entries
 * @param rank The user's rank (1-based, where 1 is the best)
 * @param totalEntries Total number of entries in the leaderboard
 * @return Percentile as integer (0-100), where higher means better (top X%)
 */
fun calculateTopPercent(rank: Int, totalEntries: Int): Int {
    if (totalEntries == 0 || rank < 1) return 100
    if (rank > totalEntries) return 0
    
    // Calculate percentile: ((totalEntries - rank + 1) / totalEntries) * 100
    // This gives: rank 1 out of 100 = top 100%, rank 50 = top 51%, rank 100 = top 1%
    val percentile = ((totalEntries - rank + 1).toDouble() / totalEntries.toDouble()) * 100.0
    return percentile.toInt().coerceIn(0, 100)
}

@Composable
fun EmptyLeaderboardCard(
    category: LeaderboardCategory,
    onUploadClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📊",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Be the first to share your ${category.displayName} score!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onUploadClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignSystemColors.NeonGreen,
                    contentColor = DesignSystemColors.Dark
                )
            ) {
                Text("Upload My Data Now")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "💡 Your data will be uploaded automatically in the background",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Shimmer modifier for loading animation
 */
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    
    var size by remember { mutableStateOf(Size.Zero) }
    
    return this
        .onGloballyPositioned { coordinates ->
            size = Size(
                coordinates.size.width.toFloat(),
                coordinates.size.height.toFloat()
            )
        }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translateAnimation.value - 300f, translateAnimation.value - 300f),
                    end = Offset(translateAnimation.value, translateAnimation.value)
                ),
                alpha = 0.9f
            )
        }
}

@Composable
fun ShimmerLeaderboardCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shimmer rank badge
            Box(
                modifier = Modifier
                    .size(48.dp, 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Shimmer device name
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(10.dp))
                // Shimmer score
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Shimmer trust badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shimmerEffect()
                )
            }
        }
    }
}

@Composable
fun ErrorStateCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Oops! Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignSystemColors.NeonGreen,
                    contentColor = DesignSystemColors.Dark
                )
            ) {
                Text("Try Again")
            }
        }
    }
}

@Composable
fun AppPowerLeaderboardEntryCard(
    rank: Int,
    entry: AppPowerLeaderboardEntry
) {
    val trustBadge = calculateTrustBadge(entry.userCount, entry.dataQuality)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // App name
                Text(
                    text = entry.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                // Power consumption
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Power: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f W".format(entry.avgPowerConsumption),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Battery impact
                if (entry.avgBatteryImpact > 0) {
                    Text(
                        text = "Battery drain: ${"%.1f".format(entry.avgBatteryImpact)}% per hour",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                // Trust indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrustBadgeIcon(badge = trustBadge)
                    Text(
                        text = if (entry.userCount == 1) 
                            "Reported by 1 user" 
                        else 
                            "Reported by ${entry.userCount} users",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyAppPowerLeaderboardCard(
    onInfoClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📱",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No app power data yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start monitoring app power consumption in the Power Consumption tab to see which apps rank highest!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "💡 Enable app power monitoring to contribute data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

