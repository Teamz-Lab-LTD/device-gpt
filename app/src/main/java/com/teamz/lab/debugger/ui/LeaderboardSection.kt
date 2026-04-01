package com.teamz.lab.debugger.ui

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.ads.nativead.NativeAd
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AppPowerLeaderboardEntry
import com.teamz.lab.debugger.utils.BestDevicesAggregator
import com.teamz.lab.debugger.utils.CategoryLeaderboardEntry
import com.teamz.lab.debugger.utils.DeviceNameNormalizer
import com.teamz.lab.debugger.utils.InterstitialAdManager
import com.teamz.lab.debugger.utils.LeaderboardCategory
import com.teamz.lab.debugger.utils.LeaderboardManager
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import com.teamz.lab.debugger.utils.RevenueCatManager
import com.teamz.lab.debugger.utils.TrustBadge
import kotlinx.coroutines.launch

/**
 * Leaderboard Section - Child-friendly UI
 */
@Composable
fun LeaderboardSection(activity: Activity) {
    val context = activity
    val scope = rememberCoroutineScope()
    
    // Premium paywall state (declared at composable level for proper scope)
    var showPremiumPaywall by remember { mutableStateOf(false) }
    var showPremiumPaywallAppPower by remember { mutableStateOf(false) }
    val premiumStatus by RevenueCatManager.premiumStatusFlow.collectAsState()
    val currentPremiumStatus = premiumStatus
    val isPremium = currentPremiumStatus is RevenueCatManager.PremiumStatus.Premium && currentPremiumStatus.isActive
    
    // RemoteConfig settings
    val adFrequency = remember { RemoteConfigUtils.getLeaderboardAdFrequency() }
    val shouldShowInterstitial = remember { RemoteConfigUtils.shouldShowLeaderboardInterstitialAds() }
    
    // State - Default to Best Device category
    var selectedCategory by remember { mutableStateOf(LeaderboardCategory.BEST_DEVICE) }
    var leaderboardEntries by remember { mutableStateOf<List<CategoryLeaderboardEntry>>(emptyList()) }
    var appPowerEntries by remember { mutableStateOf<List<AppPowerLeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) } // Start with loading state
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var viewCount by remember { mutableIntStateOf(0) }
    var userRank by remember { mutableIntStateOf(-1) }
    var retryCount by remember { mutableIntStateOf(0) }
    
    // Search state with debouncing for performance
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    
    // Debounce search query to avoid excessive filtering
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(300) // 300ms debounce
        debouncedSearchQuery = searchQuery
    }
    
    // Filtered entries with original rank preserved
    // Use Pair<Entry, OriginalRank> to preserve ranking information
    val filteredLeaderboardEntriesWithRank = remember(leaderboardEntries, debouncedSearchQuery) {
        derivedStateOf {
            if (debouncedSearchQuery.isEmpty()) {
                // No search - return entries with their original ranks (index + 1)
                leaderboardEntries.mapIndexed { index, entry -> 
                    Pair(entry, index + 1)
                }
            } else {
                val query = debouncedSearchQuery.lowercase().trim()
                // Filter but preserve original rank
                leaderboardEntries.mapIndexedNotNull { originalIndex, entry ->
                    val matches = entry.displayName.lowercase().contains(query) ||
                        entry.normalizedBrand.lowercase().contains(query) ||
                        entry.normalizedModel.lowercase().contains(query) ||
                        entry.normalizedDeviceId.lowercase().contains(query)
                    if (matches) {
                        Pair(entry, originalIndex + 1) // Preserve original rank
                    } else {
                        null
                    }
                }
            }
        }
    }.value
    
    val filteredAppPowerEntriesWithRank = remember(appPowerEntries, debouncedSearchQuery) {
        derivedStateOf {
            if (debouncedSearchQuery.isEmpty()) {
                // No search - return entries with their original ranks (index + 1)
                appPowerEntries.mapIndexed { index, entry -> 
                    Pair(entry, index + 1)
                }
            } else {
                val query = debouncedSearchQuery.lowercase().trim()
                // Filter but preserve original rank
                appPowerEntries.mapIndexedNotNull { originalIndex, entry ->
                    val matches = entry.appName.lowercase().contains(query) ||
                        entry.packageName.lowercase().contains(query)
                    if (matches) {
                        Pair(entry, originalIndex + 1) // Preserve original rank
                    } else {
                        null
                    }
                }
            }
        }
    }.value
    
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
    
    // Track previous debounced query for analytics
    var previousDebouncedQuery by remember { mutableStateOf("") }
    
    // Log search analytics after filtering completes
    LaunchedEffect(debouncedSearchQuery, filteredLeaderboardEntriesWithRank.size, filteredAppPowerEntriesWithRank.size, selectedCategory.id) {
        if (debouncedSearchQuery.isNotEmpty() && debouncedSearchQuery != previousDebouncedQuery) {
            val hasResults = when {
                selectedCategory == LeaderboardCategory.APP_POWER_MONITORING -> filteredAppPowerEntriesWithRank.isNotEmpty()
                else -> filteredLeaderboardEntriesWithRank.isNotEmpty()
            }
            
            AnalyticsUtils.logEvent(AnalyticsEvent.SearchUsed, mapOf(
                "source" to "leaderboard",
                "category" to selectedCategory.id,
                "category_name" to selectedCategory.displayName,
                "query_length" to debouncedSearchQuery.length,
                "query" to debouncedSearchQuery.take(50), // Limit query length for analytics
                "has_results" to hasResults,
                "result_count" to when {
                    selectedCategory == LeaderboardCategory.APP_POWER_MONITORING -> filteredAppPowerEntriesWithRank.size
                    else -> filteredLeaderboardEntriesWithRank.size
                }
            ))
            previousDebouncedQuery = debouncedSearchQuery
        } else if (debouncedSearchQuery.isEmpty() && previousDebouncedQuery.isNotEmpty()) {
            // Search cleared - reset tracking
            previousDebouncedQuery = ""
        }
    }
    
    // Log analytics for leaderboard tab view (no automatic ad - ads shown on user actions)
    LaunchedEffect(Unit) {
        viewCount++
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
                } else if (selectedCategory == LeaderboardCategory.BEST_DEVICE) {
                    // Load best device overall (composite score)
                    val entries = BestDevicesAggregator.getBestDevicesOverallAsEntries(100)
                    leaderboardEntries = entries
                    appPowerEntries = emptyList() // Clear app power entries
                    userRank = -1 // No user rank for best device overall
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
    
    // Centralized reactive premium check - automatically updates when premium status changes
    val shouldShowNativeAds = RemoteConfigUtils.shouldShowNativeAdsReactive()
    
    // Initialize ad loader to ensure ads are loaded on first view (fixes fresh install issue)
    // This ensures native ads are available immediately, not just after visiting other tabs
    val adLoader = rememberAdLoader(activity)
    
    // Native Banner Ad at top (AdMob recommended placement) - stable across recompositions
    // AdMob Best Practice: Use different ad for top banner vs list ads to maximize revenue
    // Only change when ad pool changes, not on every recomposition
    // Only fetch ad if user is not premium and native ads are enabled (centralized reactive check)
    val topBannerAd = remember(NativeAdManager.nativeAds.size, shouldShowNativeAds) {
        // Don't fetch ad if user is premium or native ads are disabled
        if (!shouldShowNativeAds) {
            null
        } else {
        // Use position-specific ad assignment to ensure different ads in different places
        NativeAdManager.getAdForPosition("leaderboard_top_banner")
        }
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
                    searchQuery = "" // Clear search when category changes
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
            
            // Search bar - optimized with good UI/UX
            if (!isLoading && !hasError && (leaderboardEntries.isNotEmpty() || appPowerEntries.isNotEmpty())) {
                Spacer(modifier = Modifier.height(12.dp))
                Column {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = when (selectedCategory) {
                            LeaderboardCategory.APP_POWER_MONITORING -> "Search apps..."
                            else -> "Search devices..."
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Show result count when searching
                    if (debouncedSearchQuery.isNotEmpty()) {
                        val resultCount = if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) {
                            filteredAppPowerEntriesWithRank.size
                        } else {
                            filteredLeaderboardEntriesWithRank.size
                        }
                        val totalCount = if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) {
                            appPowerEntries.size
                        } else {
                            leaderboardEntries.size
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$resultCount of $totalCount ${if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) "apps" else "devices"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
        
        // Track native ads list for stable ad assignments
        // Use derivedStateOf to reactively track ad pool changes
        val nativeAdsList = remember { NativeAdManager.nativeAds }
        val validAdsSize = remember { derivedStateOf { nativeAdsList.filterNotNull().size } }.value
        
        // Create mapping from filtered indices to original indices for ad assignments
        val filteredToOriginalIndexMap = remember(
            if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) {
                appPowerEntries.size to filteredAppPowerEntriesWithRank.size
            } else {
                leaderboardEntries.size to filteredLeaderboardEntriesWithRank.size
            },
            debouncedSearchQuery,
            selectedCategory.id
        ) {
            if (debouncedSearchQuery.isEmpty()) {
                // No search - indices match 1:1
                emptyMap<Int, Int>()
            } else {
                if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) {
                    // Map for app power entries - use the rank from the pair (rank - 1 = index)
                    filteredAppPowerEntriesWithRank.mapIndexedNotNull { filteredIndex, (filteredEntry, originalRank) ->
                        val originalIndex = originalRank - 1 // Convert rank to index
                        if (originalIndex >= 0) filteredIndex to originalIndex else null
                    }.toMap()
                } else {
                    // Map for device entries - use the rank from the pair (rank - 1 = index)
                    filteredLeaderboardEntriesWithRank.mapIndexedNotNull { filteredIndex, (filteredEntry, originalRank) ->
                        val originalIndex = originalRank - 1 // Convert rank to index
                        if (originalIndex >= 0) filteredIndex to originalIndex else null
                    }.toMap()
                }
            }
        }
        
        // Pre-calculate ad assignments based on original entry indices
        // AdMob Best Practice: Assign DIFFERENT ads to different positions to maximize revenue
        // Only create ad assignments if user is not premium and native ads are enabled
        val adAssignments = remember(
            if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) appPowerEntries.size 
            else leaderboardEntries.size,
            selectedCategory.id,
            validAdsSize,
            shouldShowNativeAds
        ) {
            // Don't create ad assignments if user is premium or native ads are disabled (centralized reactive check)
            if (!shouldShowNativeAds) {
                emptyMap<Int, NativeAd>()
            } else {
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
                (0 until entriesSize).forEach { originalIndex ->
                    if ((originalIndex + 1) % adFrequency == 0) {
                        // Use position-specific ad assignment to ensure different ads in different positions
                        val positionId = "leaderboard_list_${selectedCategory.id}_$originalIndex"
                        val ad = NativeAdManager.getAdForPosition(positionId)
                        if (ad != null) {
                            assignments[originalIndex] = ad
                        }
                    }
                }
                assignments
                }
            }
        }
        
        // Map ad assignments from original indices to filtered indices
        val filteredAdAssignments = remember(adAssignments, filteredToOriginalIndexMap) {
            if (filteredToOriginalIndexMap.isEmpty()) {
                // No search - use ad assignments as-is, but map to filtered indices
                adAssignments.mapKeys { it.key }
            } else {
                // Map original indices to filtered indices
                val reverseMap = filteredToOriginalIndexMap.entries.associate { (filtered, original) -> original to filtered }
                adAssignments.mapNotNull { (originalIndex, ad) ->
                    reverseMap[originalIndex]?.let { filteredIndex -> filteredIndex to ad }
                }.toMap()
            }
        }
        
        // Note: showPremiumPaywall and showPremiumPaywallAppPower are declared at the top level
        // of LeaderboardSection composable (lines 61-62) to ensure proper scope
        
        // Scrollable content (ad, user rank, and list)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Native Banner Ad at top (AdMob recommended placement)
            // Centralized reactive check - automatically hides when premium is purchased
            if (shouldShowNativeAds) {
            topBannerAd?.let {
                item {
                    AdMobNativeAdCard(nativeAd = it, bottomPadding = 8)
                    }
                }
            }
            
            // Premium: Show Your Device Ranking (for premium users only) - using original UserRankCard design
            if (isPremium && userRank > 0) {
                item(key = "premium_user_rank") {
                    // Track premium user rank card viewed
                    LaunchedEffect(selectedCategory, userRank) {
                        AnalyticsUtils.logEvent(
                            AnalyticsEvent.PremiumUserRankCardViewed,
                            mapOf(
                                "category" to selectedCategory.id,
                                "rank" to userRank,
                                "total_entries" to (if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) {
                                    appPowerEntries.size
                                } else {
                                    leaderboardEntries.size
                                })
                            )
                        )
                    }
                    UserRankCard(
                        rank = userRank, 
                        category = selectedCategory,
                        totalEntries = if (selectedCategory == LeaderboardCategory.APP_POWER_MONITORING) {
                            appPowerEntries.size
                        } else {
                            leaderboardEntries.size
                        },
                        onViewInsights = { 
                            // Show ad before opening Device Insights (user-initiated action)
                            InterstitialAdManager.showAdBeforeAction(
                                activity = activity,
                                actionName = "view_device_insights"
                            ) {
                                selectedDeviceId = null // Will use current device
                                showDeviceInsights = true
                            }
                        },
                        onViewBestDevices = null // Removed - Best Devices is available via tabs
                    )
                }
            }
            
            
            // User rank display with premium gate for non-premium users
            if (!isPremium && userRank > 0 && selectedCategory != LeaderboardCategory.APP_POWER_MONITORING && selectedCategory != LeaderboardCategory.BEST_DEVICE) {
                item {
                    UserRankCardPremiumGate(
                        category = selectedCategory,
                        userRank = userRank,
                        totalEntries = leaderboardEntries.size,
                        onUnlockClick = {
                            AnalyticsUtils.logEvent(AnalyticsEvent.PremiumGateClicked, mapOf(
                                "source" to "user_rank_card",
                                "category" to selectedCategory.id
                            ))
                            showPremiumPaywall = true
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
                if (filteredAppPowerEntriesWithRank.isEmpty()) {
                    item {
                        if (debouncedSearchQuery.isNotEmpty()) {
                            // Show "no results" message when searching
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🔍",
                                        fontSize = 48.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = "No results found",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = "Try searching with a different term",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            EmptyAppPowerLeaderboardCard(
                                onInfoClick = {
                                    // Show info about app power monitoring
                                }
                            )
                        }
                    }
                } else {
                    // Render filtered app power entries with original ranks
                    filteredAppPowerEntriesWithRank.forEachIndexed { filteredIndex, (entry, originalRank) ->
                        item(key = "app_entry_$filteredIndex") {
                            // Blur first 3 items for non-premium users
                            val shouldBlur = !isPremium && filteredIndex < 3
                            
                            // Get price dynamically for premium prompt
                            var premiumPriceAppPower by remember { mutableStateOf("$2.99") }
                            LaunchedEffect(filteredIndex) {
                                if (shouldBlur && filteredIndex == 0) { // Only fetch once for first item
                                    RevenueCatManager.getLifetimeProductPrice(
                                        onSuccess = { price -> premiumPriceAppPower = price },
                                        onError = { /* Keep default $2.99 */ }
                                    )
                                }
                            }
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                // Apply blur directly to the card content for free users
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (shouldBlur) {
                                                Modifier.blur(radius = 20.dp)
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                            AppPowerLeaderboardEntryCard(
                                rank = originalRank, // Use original rank, not filtered index
                                entry = entry
                            )
                                }
                                
                                // Premium gate overlay for first 3 items (non-premium only)
                                // Shows rank clearly (#1, #2, #3) with premium prompt
                                if (shouldBlur) {
                                    // Semi-transparent overlay to further obscure the blurred content
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (isSystemInDarkTheme()) {
                                                    Color.Black.copy(alpha = 0.5f)
                                                } else {
                                                    Color.White.copy(alpha = 0.6f)
                                                }
                                            )
                                    )
                                    
                                    // Premium prompt overlay - positioned to show rank clearly
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable {
                                                AnalyticsUtils.logEvent(AnalyticsEvent.PremiumGateClicked, mapOf(
                                                    "source" to "leaderboard_app_power_blur",
                                                    "rank" to originalRank,
                                                    "category" to "app_power_monitoring"
                                                ))
                                                showPremiumPaywallAppPower = true
                                            }
                                    ) {
                                        // Show rank prominently on the left (unblurred)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Rank number - clear and prominent (background is reverse of text color)
                                            // Calculate if text is light or dark based on luminance
                                            val textColor = MaterialTheme.colorScheme.onSurface
                                            val textLuminance = (0.299 * textColor.red + 0.587 * textColor.green + 0.114 * textColor.blue)
                                            val rankBgColor = if (textLuminance > 0.5) {
                                                // Text is light (dark mode) → use dark background
                                                Color.Black.copy(alpha = 0.7f)
                                            } else {
                                                // Text is dark (light mode) → use white background
                                                Color.White.copy(alpha = 0.9f)
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .background(
                                                        color = rankBgColor,
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .border(
                                                        width = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(12.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "• $originalRank",
                                                    style = MaterialTheme.typography.headlineLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.width(16.dp))
                                            
                                            // Premium prompt on the right
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = if (isSystemInDarkTheme()) {
                                                            DesignSystemColors.NeonGreen
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Premium Required",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "See all app data & unlock full leaderboard access forever",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        AnalyticsUtils.logEvent(AnalyticsEvent.PremiumGateClicked, mapOf(
                                                            "source" to "leaderboard_app_power_button",
                                                            "rank" to originalRank,
                                                            "category" to "app_power_monitoring"
                                                        ))
                                                        showPremiumPaywallAppPower = true
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = DesignSystemColors.NeonGreen,
                                                        contentColor = DesignSystemColors.Dark
                                                    ),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            "Unlock Lifetime Access",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            "$premiumPriceAppPower • See Everything Forever",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Show native ad every N entries (using mapped indices)
                        // Centralized reactive check - automatically hides when premium is purchased
                        if (shouldShowNativeAds) {
                        filteredAdAssignments[filteredIndex]?.let { nativeAd ->
                            item(key = "app_ad_$filteredIndex") {
                                AdMobNativeAdCard(nativeAd = nativeAd, bottomPadding = 16)
                                }
                            }
                        }
                    }
                }
            } else if (filteredLeaderboardEntriesWithRank.isEmpty()) {
                item {
                    if (debouncedSearchQuery.isNotEmpty()) {
                        // Show "no results" message when searching
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🔍",
                                    fontSize = 48.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = "No results found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = "Try searching with a different term",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
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
                                    } catch (_: Exception) {
                                        hasError = true
                                        errorMessage = "Failed to upload data. Please try again."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                // Render filtered leaderboard entries with ads interspersed
                // Use pre-calculated assignments to prevent frequent changes
                filteredLeaderboardEntriesWithRank.forEachIndexed { filteredIndex, (entry, originalRank) ->
                    // Leaderboard entry
                    item(key = "entry_$filteredIndex") {
                        // Blur first 3 items for non-premium users
                        val shouldBlur = !isPremium && filteredIndex < 3
                        
                        // Get price dynamically for premium prompt
                        var premiumPrice by remember { mutableStateOf("$2.99") }
                        LaunchedEffect(filteredIndex) {
                            if (shouldBlur && filteredIndex == 0) { // Only fetch once for first item
                                RevenueCatManager.getLifetimeProductPrice(
                                    onSuccess = { price -> premiumPrice = price },
                                    onError = { /* Keep default $2.99 */ }
                                )
                            }
                        }
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Apply blur directly to the card content for free users
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (shouldBlur) {
                                            Modifier.blur(radius = 20.dp)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                        LeaderboardEntryCard(
                            rank = originalRank, // Use original rank, not filtered index
                            entry = entry,
                            category = selectedCategory,
                            onClick = {
                                        if (shouldBlur) {
                                            // Show paywall if user tries to click blurred item
                                            AnalyticsUtils.logEvent(AnalyticsEvent.PremiumGateClicked, mapOf(
                                                "source" to "leaderboard_blur",
                                                "rank" to originalRank,
                                                "category" to selectedCategory.id
                                            ))
                                            showPremiumPaywall = true
                                        } else {
                                            // Show ad before opening Device Insights (HIGH ENGAGEMENT ACTION)
                                            InterstitialAdManager.showAdBeforeAction(
                                                activity = activity,
                                                actionName = "view_leaderboard_entry_insights"
                                            ) {
                                                AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                                                    "source" to "leaderboard_entry",
                                                    "category" to selectedCategory.id,
                                                    "rank" to originalRank, // Use original rank for analytics
                                                    "device_id" to entry.normalizedDeviceId
                                                ))
                                                // Set selected device and show insights
                                                selectedDeviceId = entry.normalizedDeviceId
                                                showDeviceInsights = true
                                            }
                                        }
                                    }
                                )
                            }
                            
                            // Premium gate overlay for first 3 items (non-premium only)
                            // Shows rank clearly (#1, #2, #3) with premium prompt
                            if (shouldBlur) {
                                // Semi-transparent overlay to further obscure the blurred content
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isSystemInDarkTheme()) {
                                                Color.Black.copy(alpha = 0.5f)
                                            } else {
                                                Color.White.copy(alpha = 0.6f)
                                            }
                                        )
                                )
                                
                                // Premium prompt overlay - positioned to show rank clearly
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { showPremiumPaywall = true }
                                ) {
                                    // Show rank prominently on the left (unblurred)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Rank number - clear and prominent (background is reverse of text color)
                                        // Calculate if text is light or dark based on luminance
                                        val textColor = MaterialTheme.colorScheme.onSurface
                                        val textLuminance = (0.299 * textColor.red + 0.587 * textColor.green + 0.114 * textColor.blue)
                                        val rankBgColor = if (textLuminance > 0.5) {
                                            // Text is light (dark mode) → use dark background
                                            Color.Black.copy(alpha = 0.7f)
                                        } else {
                                            // Text is dark (light mode) → use white background
                                            Color.White.copy(alpha = 0.9f)
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .background(
                                                    color = rankBgColor,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(12.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "#$originalRank",
                                                style = MaterialTheme.typography.headlineLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(16.dp))
                                        
                                        // Premium prompt on the right
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                // Get score to tease without revealing the device name
                                                val score = entry.score.toInt().takeIf { it > 0 }
                                                Text(
                                                    text = if (score != null) "#$originalRank Device — Score: $score/100" else "#$originalRank Device",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            // Curiosity hook: tease the score, hide the device name
                                            val totalDevices = filteredLeaderboardEntriesWithRank.size
                                            Text(
                                                text = "Which device ranks #$originalRank out of $totalDevices? Unlock to find out.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    AnalyticsUtils.logEvent(AnalyticsEvent.PremiumGateClicked, mapOf(
                                                        "source" to "leaderboard_blur_button",
                                                        "rank" to originalRank,
                                                        "category" to selectedCategory.id
                                                    ))
                                                    showPremiumPaywall = true
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = DesignSystemColors.NeonGreen,
                                                    contentColor = DesignSystemColors.Dark
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        "Unlock Lifetime Access",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        "$premiumPrice • See Everything Forever",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Show native ad every N entries (AdMob policy compliant)
                    // Use mapped ad assignments for filtered indices
                    // Centralized reactive check - automatically hides when premium is purchased
                    if (shouldShowNativeAds) {
                    filteredAdAssignments[filteredIndex]?.let { nativeAd ->
                        item(key = "ad_$filteredIndex") {
                            AdMobNativeAdCard(nativeAd = nativeAd, bottomPadding = 16)
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Premium Paywall for leaderboard premium gate (outside LazyColumn)
    if (showPremiumPaywall) {
        RevenueCatPaywall(
            showPaywall = showPremiumPaywall,
            onDismiss = { showPremiumPaywall = false },
            analyticsSource = "leaderboard_premium_gate"
        )
    }
    
    if (showPremiumPaywallAppPower) {
        RevenueCatPaywall(
            showPaywall = showPremiumPaywallAppPower,
            onDismiss = { showPremiumPaywallAppPower = false },
            analyticsSource = "leaderboard_app_power_premium_gate"
        )
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
                                LeaderboardCategory.BEST_DEVICE -> "Based on composite score across all categories using market-research-based weights. Power Efficiency (22%), CPU Performance (18%), Thermal Efficiency (15%), Performance Consistency (14%), Health Score (12%), Component Optimization (8%), Power Trend (5%), Display Efficiency (4%), Camera Efficiency (2%)."
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
            // Top category: Best Device Overall
            LeaderboardCategory.BEST_DEVICE,            // 0. Best Device (composite score)
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
                text = "•$rank",
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
                
                // OS version info with scores per OS - only show if data is available
                if (entry.androidVersion.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "🤖",
                            fontSize = 14.sp
                        )
                        // Show OS version(s) with scores per OS
                        if (entry.osScores.isNotEmpty() && entry.osScores.size > 1) {
                            // Multiple versions: Show scores per OS
                            // Format: "Android 13 (85/100), Android 14 (88/100)"
                            val osScoresText = entry.osScores.entries
                                .sortedByDescending { it.value } // Sort by score (descending)
                                .joinToString(", ") { 
                                    val scoreInt = it.value.toInt()
                                    "Android ${it.key} ($scoreInt/100)"
                                }
                            Text(
                                text = osScoresText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        } else if (entry.osScores.isNotEmpty()) {
                            // Single version: Show "Android 13 (85/100)"
                            val score = entry.osScores[entry.androidVersion] ?: entry.avgScore
                            Text(
                                text = "Android ${entry.androidVersion} (${score.toInt()}/100)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        } else {
                            // Fallback: Show OS version without score data
                            if (entry.androidVersions.isNotEmpty() && entry.androidVersions.size > 1) {
                                val versionCounts = entry.androidVersions.entries
                                    .sortedByDescending { it.value }
                                    .joinToString(", ") { "Android ${it.key} (${it.value})" }
                                Text(
                                    text = versionCounts,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text(
                                    text = "Android ${entry.androidVersion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                
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
        TrustBadge.VERIFIED -> Triple("", DesignSystemColors.NeonGreen, "Verified")
        TrustBadge.HIGH -> Triple("", MaterialTheme.colorScheme.onSurfaceVariant, "High Trust")
        TrustBadge.MEDIUM -> Triple("", MaterialTheme.colorScheme.onSurfaceVariant, "Medium Trust")
        TrustBadge.LOW -> Triple("", MaterialTheme.colorScheme.onSurfaceVariant, "Low Trust")
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
 * User Rank Card with Premium Gate - Shows "---" for non-premium users
 */
@Composable
fun UserRankCardPremiumGate(
    category: LeaderboardCategory,
    userRank: Int = 0,
    totalEntries: Int = 0,
    onUnlockClick: () -> Unit
) {
    // Get price dynamically
    var premiumPrice by remember { mutableStateOf("$2.99") }
    LaunchedEffect(Unit) {
        RevenueCatManager.getLifetimeProductPrice(
            onSuccess = { price -> premiumPrice = price },
            onError = { /* Keep default $2.99 */ }
        )
    }

    // Calculate percentile (show real data to hook the user)
    val topPercent = if (totalEntries > 0 && userRank > 0) {
        ((userRank.toFloat() / totalEntries) * 100).toInt().coerceIn(1, 100)
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUnlockClick() },
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
                    if (userRank > 0) {
                        Text(
                            text = "Your device: #$userRank out of $totalEntries",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Top $topPercent% for ${category.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Your rank: ---",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "You're in the top --% for ${category.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (userRank > 0) "See what the #1 device has that yours doesn't" else "Unlock to see your full ranking details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = onUnlockClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignSystemColors.NeonGreen,
                    contentColor = DesignSystemColors.Dark
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        "Unlock Lifetime Access",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$premiumPrice • See Everything Forever",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * "Should You Update?" Card - Premium Feature
 * Shows OS update recommendation if newer OS version has better performance
 */
@Composable
fun ShouldYouUpdateCard(
    activity: Activity,
    normalizedDeviceId: String,
    isPremium: Boolean,
    onUnlockClick: () -> Unit,
    onViewOSComparison: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var osRecommendation by remember { mutableStateOf<Pair<String?, Double>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val currentOS = android.os.Build.VERSION.RELEASE
    
    LaunchedEffect(normalizedDeviceId, currentOS) {
        isLoading = true
        osRecommendation = null
        scope.launch {
            try {
                osRecommendation = LeaderboardManager.getRecommendedOSVersion(
                    normalizedDeviceId = normalizedDeviceId,
                    currentOS = currentOS
                )
            } catch (e: Exception) {
                // Handle error silently
            } finally {
                isLoading = false
            }
        }
    }
    
    // Always show the card, but with different content based on state
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
                    text = "🚀",
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    when {
                        isLoading -> {
                            Text(
                                text = "Checking OS updates...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Analyzing your device performance",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        osRecommendation != null -> {
                            val (recommendedOS, improvement) = osRecommendation!!
                            Text(
                                text = if (isPremium) {
                                    "Update to Android $recommendedOS"
                                } else {
                                    "Should you update?"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isPremium) {
                                Text(
                                    text = "Could improve performance by ${improvement.toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            } else {
                                Text(
                                    text = "See if updating OS will improve your device",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            // No recommendation available (no data or no better version)
                            Text(
                                text = if (isPremium) {
                                    "OS Comparison Available"
                                } else {
                                    "Should you update?"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPremium) {
                                    "Compare your device across different Android versions"
                                } else {
                                    "See if updating OS will improve your device"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isPremium) {
                // Premium users can view OS comparison
                Button(
                    onClick = onViewOSComparison,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignSystemColors.NeonGreen,
                        contentColor = DesignSystemColors.Dark
                    )
                ) {
                    Text(
                        "View OS Comparison",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Free users see unlock button
                Button(
                    onClick = onUnlockClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignSystemColors.NeonGreen,
                        contentColor = DesignSystemColors.Dark
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            "Unlock OS Comparison",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "See detailed comparison [Premium]",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium User Rank Card - Shows device ranking for premium users
 */
@Composable
fun PremiumUserRankCard(
    rank: Int,
    category: LeaderboardCategory,
    totalEntries: Int = 0,
    onViewInsights: (() -> Unit)? = null
) {
    // Yellow/gold color for premium styling (matching the previous nice widget)
    val premiumYellow = Color(0xFFFFD700) // Gold color
    val premiumYellowDark = Color(0xFFB8860B) // Dark goldenrod for borders
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = premiumYellow.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 2.dp,
            color = premiumYellow.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Premium badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = premiumYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Premium",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = premiumYellow
                    )
                }
                Text(
                    text = "Your Device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Rank display
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Rank number with premium yellow/gold styling
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = premiumYellow,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "•$rank",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your Ranking",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (totalEntries > 0) {
                        val percentile = calculateTopPercent(rank, totalEntries)
                        Text(
                            text = "Top $percentile% • ${category.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // View Insights button
            if (onViewInsights != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onViewInsights,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = premiumYellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "View Device Insights",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
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

/**
 * Optimized Search Bar with good UI/UX
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
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
                text = "•$rank",
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
                
                // OS version info with power/battery per OS - only show if data is available
                if (entry.androidVersion.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "🤖",
                            fontSize = 14.sp
                        )
                        // Show OS version(s) with power consumption per OS
                        if (entry.osPowerConsumption.isNotEmpty() && entry.osPowerConsumption.size > 1) {
                            // Multiple versions: Show power per OS
                            // Format: "Android 13 (2.5W), Android 14 (2.3W)"
                            val osPowerText = entry.osPowerConsumption.entries
                                .sortedByDescending { it.value } // Sort by power consumption (descending)
                                .joinToString(", ") { 
                                    val power = "%.2f".format(it.value)
                                    "Android ${it.key} ($power W)"
                                }
                            Text(
                                text = osPowerText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        } else if (entry.osPowerConsumption.isNotEmpty()) {
                            // Single version: Show "Android 13 (2.5W)"
                            val power = entry.osPowerConsumption[entry.androidVersion] ?: entry.avgPowerConsumption
                            Text(
                                text = "Android ${entry.androidVersion} (${"%.2f".format(power)} W)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        } else {
                            // Fallback: Show OS version without power data
                            if (entry.androidVersions.isNotEmpty() && entry.androidVersions.size > 1) {
                                val versionCounts = entry.androidVersions.entries
                                    .sortedByDescending { it.value }
                                    .joinToString(", ") { "Android ${it.key} (${it.value})" }
                                Text(
                                    text = versionCounts,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text(
                                    text = "Android ${entry.androidVersion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
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

