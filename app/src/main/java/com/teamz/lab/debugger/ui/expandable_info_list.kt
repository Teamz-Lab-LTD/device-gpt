package com.teamz.lab.debugger.ui

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import com.teamz.lab.debugger.utils.AIIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.teamz.lab.debugger.BuildConfig
import com.teamz.lab.debugger.utils.AnalyticsEvent
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.RemoteConfigUtils
import com.teamz.lab.debugger.utils.RevenueCatManager
import com.teamz.lab.debugger.utils.copyToClipboard
import com.teamz.lab.debugger.utils.AdRevenueOptimizer
import com.teamz.lab.debugger.ui.theme.DesignSystemColors
import com.teamz.lab.debugger.utils.AIPromptGenerator
import com.teamz.lab.debugger.utils.AdConfig

// Sealed class to represent list items (info or ad)
private sealed class ListItem {
    data class InfoItem(val index: Int, val key: String, val value: String) : ListItem()
    data class AdItem(val index: Int, val ad: NativeAd) : ListItem()
}

/**
 * Generates a context-specific AI prompt for a device info item
 * 
 * Delegates to AIPromptGenerator for centralized prompt management
 */
fun generateItemPrompt(itemTitle: String, itemContent: String, appName: String, promptMode: PromptMode): String {
    // Delegate to centralized prompt generator
    return AIPromptGenerator.generateItemPrompt(
        itemTitle = itemTitle,
        itemContent = itemContent,
        appName = appName,
        promptMode = promptMode
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandableInfoList(
    infoList: List<Pair<String, String>>,
    activity: Activity,
    onAIClick: (() -> Unit)? = null,
    onItemAIClick: ((String, String) -> Unit)? = null,
    headerContent: @Composable (() -> Unit)? = null,
    onPremiumGateClick: ((String) -> Unit)? = null
) {

    val adLoader = rememberAdLoader(activity)
    var searchQuery by remember { mutableStateOf("") }
    val shownAdHashes = remember {
        mutableStateMapOf<String, Int>()
    }
    val interactionSource = remember { MutableInteractionSource() }
    
    // Use derivedStateOf for filtering - only recalculates when searchQuery or infoList changes
    // Note: derivedStateOf must be created outside remember to track changes properly
    val filteredInfo = derivedStateOf {
        if (searchQuery.isEmpty()) {
            infoList
        } else {
            infoList.filter { (title, detail) ->
                title.contains(searchQuery, ignoreCase = true) || detail.contains(
                    searchQuery,
                    ignoreCase = true
                )
            }
        }
    }
    
    // Create index map once for O(1) lookup instead of O(n) indexOfFirst
    val originalIndexMap = remember(infoList) {
        infoList.mapIndexed { index, (key, _) -> key to index }.toMap()
    }
    
    // Subscribe to NativeAdManager.cacheGeneration so adPositionCache + flattenedList
    // re-run whenever an ad is added/evicted/expired — even if filteredInfo and
    // originalIndexMap are identity-stable.
    val adCacheGen = NativeAdManager.cacheGeneration.intValue

    // Create flattened list with info items and ads - optimized with index map
    // Cache ad positions to prevent repeated lookups during recomposition
    val adPositionCache = remember(filteredInfo.value, originalIndexMap, adCacheGen) {
        val cache = mutableMapOf<Int, NativeAd?>()
        val filtered = filteredInfo.value
        filtered.forEachIndexed { filteredIndex, (key, _) ->
            val actualIndex = originalIndexMap[key] ?: filteredIndex
            if (actualIndex % 5 == 0 && actualIndex != 0 && actualIndex < infoList.size) {
                val positionId = "device_info_list_$actualIndex"
                // Cache ad lookup to prevent blocking during composition
                cache[actualIndex] = try {
                    NativeAdManager.getAdForPosition(positionId)
                } catch (e: Exception) {
                    // If ad lookup fails, cache null to prevent ANR
                    null
                }
            }
        }
        cache
    }

    // Create flattened list using cached ads to prevent blocking during composition
    // This only recomputes when filteredInfo or adPositionCache changes
    val flattenedList = remember(filteredInfo.value, originalIndexMap, adPositionCache, adCacheGen) {
        val list = mutableListOf<ListItem>()
        val filtered = filteredInfo.value
        filtered.forEachIndexed { filteredIndex, (key, value) ->
            // Use O(1) map lookup instead of O(n) indexOfFirst
            val actualIndex = originalIndexMap[key] ?: filteredIndex
            
            // Add ad before item if it's a 5th item (except first)
            // Use cached ad to prevent blocking during composition
            val cachedAd = adPositionCache[actualIndex]
            cachedAd?.let {
                list.add(ListItem.AdItem(actualIndex, it))
            }
            
            // Add the info item
            list.add(ListItem.InfoItem(actualIndex, key, value))
        }
        list
    }
    
    // Single expanded state map instead of per-item state (reduces recompositions)
    val expandedItems = remember { mutableStateMapOf<Int, Boolean>() }
    val inlineAds = remember { mutableStateMapOf<Int, NativeAd?>() }
    
    // Pre-compute merged TextStyle outside the loop to prevent ANR from TextStyle.merge
    // TextStyle.merge is expensive and can block if called repeatedly during recomposition
    // This is computed once and reused for all items, preventing repeated merges
    // Access MaterialTheme outside remember block to avoid Composable context issues
    val materialTypography = MaterialTheme.typography
    val titleTextStyle = remember(materialTypography) {
        materialTypography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold
        )
    }
    
    val lazyListState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .navigationBarsPadding(),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Optional header content (e.g., Network Privacy Report Card)
        if (headerContent != null) {
            item(key = "header_content") {
                headerContent()
            }
        }

        // Search bar at top with optional AI button
        item(key = "search_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        AnalyticsUtils.logEvent(AnalyticsEvent.SearchUsed, mapOf("query" to it))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // List items with virtualization - only visible items are composed
        // Use stable keys to prevent unnecessary recompositions during scroll
        itemsIndexed(
            items = flattenedList,
            key = { index, item ->
                // Use stable keys based on item identity to prevent recomposition during scroll
                // Include hashCode to ensure keys are unique and stable
                when (item) {
                    is ListItem.AdItem -> "ad_${item.index}_${item.ad.hashCode()}"
                    is ListItem.InfoItem -> "info_${item.index}_${item.key.hashCode()}"
                }
            }
        ) { index, item ->
            when (item) {
                is ListItem.AdItem -> {
                    // Centralized reactive premium check - automatically hides when premium is purchased
                    val shouldShowAds = RemoteConfigUtils.shouldShowNativeAdsReactive()
                    if (shouldShowAds) {
                        // Render ad (logging reduced - only log once per position)
                        val logKey = "display_${item.index}"
                        if (!shownAdHashes.containsKey(logKey) || shownAdHashes[logKey] != item.ad.hashCode()) {
                            Log.d("AdDisplay", "📺 Displaying ad at position ${item.index}, " +
                                    "Ad hash: ${item.ad.hashCode()}, Total ads: ${NativeAdManager.nativeAds.filterNotNull().size}")
                            shownAdHashes[logKey] = item.ad.hashCode()
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        AdMobNativeAdCard(nativeAd = item.ad)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                is ListItem.InfoItem -> {
                    // Render info item
                    val actualIndex = item.index
                    val key = item.key
                    val value = item.value
                    val expanded = expandedItems[actualIndex] ?: false
                    val inlineAd = inlineAds[actualIndex]
                    
                    // 🔄 Dynamically load an ad when expanded, but only if a new one exists
                    // AdMob Best Practice: Use different ad for expanded view vs list ads
                    // Use key to prevent unnecessary LaunchedEffect restarts
                    // Reactive check for ads - updates when premium is purchased
                    val shouldShowAdsInline = RemoteConfigUtils.shouldShowNativeAdsReactive()
                    val expandedAdCacheGen = NativeAdManager.cacheGeneration.intValue
                    LaunchedEffect(expanded, actualIndex, shouldShowAdsInline, expandedAdCacheGen) {
                        // Refetch when the cached inlineAd has been evicted from the pool.
                        val needsFetch = inlineAd == null || inlineAd !in NativeAdManager.nativeAds
                        if (expanded && needsFetch && shouldShowAdsInline) {
                            // Use position-specific ad to ensure different ad in expanded view
                            val positionId = "device_info_expanded_$actualIndex"
                            val newAd = NativeAdManager.getAdForPosition(positionId)
                            // Verify the returned ad is actually in the pool (not just-evicted).
                            if (newAd != null && newAd in NativeAdManager.nativeAds) {
                                val logKey = "expanded_$actualIndex"
                                if (!shownAdHashes.containsKey(logKey) || shownAdHashes[logKey] != newAd.hashCode()) {
                                    Log.d("AdDisplay", "📺 Expanded view ad at index $actualIndex, " +
                                            "Ad hash: ${newAd.hashCode()}, Position: $positionId")
                                    shownAdHashes[logKey] = newAd.hashCode()
                                }
                                inlineAds[actualIndex] = newAd
                            } else {
                                inlineAds[actualIndex] = null
                            }
                        }
                    }

                    val showExpandedAd = expanded && inlineAd != null
                    AppCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable {
                                    expandedItems[actualIndex] = !expanded
                                    AnalyticsUtils.logEvent(
                                        if (!expanded) AnalyticsEvent.InfoExpanded else AnalyticsEvent.InfoCollapsed,
                                        mapOf("title" to key)
                                    )
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = key,
                                style = titleTextStyle,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (onItemAIClick != null) {
                                    IconButton(
                                        onClick = {
                                            onItemAIClick(key, value)
                                            AnalyticsUtils.logEvent(AnalyticsEvent.FabAIClicked, mapOf(
                                                "source" to "device_info_item",
                                                "item_title" to key
                                            ))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = AIIcon.icon,
                                            contentDescription = "Get AI insights about $key",
                                            tint = AIIcon.color(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (value.length > 50) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (expanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Check if this is a premium-gated section (contains teaser marker)
                        val isPremiumGated = value.contains("⭐ Unlock Premium to see")

                        if (expanded && isPremiumGated && onPremiumGateClick != null) {
                            // Show free lines + unlock button (not just plain text)
                            val parts = value.split("\n\n⭐")
                            val freeContent = parts.firstOrNull() ?: value
                            Text(
                                text = freeContent,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // Log analytics when user sees gated content
                            LaunchedEffect(key) {
                                AnalyticsUtils.logEvent(
                                    AnalyticsEvent.PremiumSectionTeased,
                                    mapOf("section" to key)
                                )
                            }
                            Button(
                                onClick = { onPremiumGateClick(key) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.teamz.lab.debugger.ui.theme.DesignSystemColors.NeonGreen,
                                    contentColor = com.teamz.lab.debugger.ui.theme.DesignSystemColors.Dark
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Unlock Full Report",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Normal content (free or premium user)
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (expanded) Int.MAX_VALUE else 1,
                                overflow = if (value.length > 50) TextOverflow.Ellipsis else TextOverflow.Clip,
                                modifier = Modifier
                                    .combinedClickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = {
                                            expandedItems[actualIndex] = !expanded
                                            AnalyticsUtils.logEvent(
                                                if (!expanded) AnalyticsEvent.InfoExpanded else AnalyticsEvent.InfoCollapsed,
                                                mapOf("title" to key)
                                            )
                                        },
                                        onLongClick = {
                                            copyToClipboard(
                                                context = activity,
                                                title = key,
                                                body = value
                                            )
                                        }
                                    )
                            )
                        }
                        // Centralized reactive premium check - automatically hides when premium is purchased
                        val shouldShowAds = RemoteConfigUtils.shouldShowNativeAdsReactive()
                        if (showExpandedAd && shouldShowAds) {
                            inlineAd?.let {
                                Spacer(modifier = Modifier.height(16.dp))
                                AnimatedVisibility(
                                    visible = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                ) {
                                    AdMobNativeAdCard(
                                        bottomPadding = 0,
                                        nativeAd = it
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bottomPadding: Int = 16,
    borderColor: Color=MaterialTheme.colorScheme.outline,
    colors: CardColors = CardDefaults.cardColors( containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(bottom = bottomPadding.dp),
        shape = RoundedCornerShape(12.dp),
        colors = colors,
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(), content = content
        )
    }
}

@Composable
fun rememberAdLoader(activity: Activity): AdLoader {
    val coroutineScope = rememberCoroutineScope()
    var adLoaderRef: AdLoader? by remember { mutableStateOf(null) }
    var retryCount by remember { mutableStateOf(0) }
    val TAG = "AdLoader"
    
    val adLoader = remember {
        val adUnitId = AdConfig.getNativeAdUnitId()
        AdLoader.Builder(activity, adUnitId)
            .forNativeAd { nativeAd ->
                if (!activity.isDestroyed) {
                    // I5: attach paid-event listener BEFORE the ad enters the pool.
                    // A composition could pick up the ad and fire an impression between
                    // addAd and the listener attach, costing us revenue tracking on the
                    // very first paid event.
                    nativeAd.setOnPaidEventListener { adValue ->
                        AdRevenueOptimizer.trackAdRevenue(
                            activity,
                            adUnitId,
                            "native",
                            adValue
                        )
                    }

                    // addAd() registers loadedAtMs, invalidates positionAdCache, and bumps
                    // cacheGeneration atomically so downstream remember() blocks re-fetch.
                    NativeAdManager.addAd(nativeAd)
                    NativeAdManager.recordSuccessfulLoad()

                    val currentCount = NativeAdManager.nativeAds.filterNotNull().size
                    val targetCount = NativeAdManager.getTargetAdCount()
                    Log.i(TAG, "✅ Native ad loaded! Total ads in cache: $currentCount/$targetCount")

                    AnalyticsUtils.logEvent(AnalyticsEvent.AdLoaded, mapOf(
                        "ad_type" to "native",
                        "total_ads_loaded" to currentCount,
                        "target_count" to targetCount
                    ))

                    // Reset retry count on successful load
                    retryCount = 0
                    NativeAdManager.resetRetryCount()

                    // If we still need more ads, trigger another load attempt
                    // Note: Removed isCurrentlyLoading() check to allow continuation
                    // Also check premium status - don't load more ads if user has premium
                    if (currentCount < targetCount && RemoteConfigUtils.shouldShowNativeAds()) {
                        Log.d(TAG, "🔄 New ad loaded, checking if we need more...")
                        coroutineScope.launch {
                            // Wait for throttle period before next request
                            delay(RemoteConfigUtils.getNativeAdRequestIntervalMs() + 1000L)
                            // Re-check premium status before loading (user might have purchased premium)
                            if (!activity.isDestroyed &&
                                RemoteConfigUtils.shouldShowNativeAds() &&
                                NativeAdManager.nativeAds.filterNotNull().size < targetCount &&
                                NativeAdManager.canMakeRequest()) {
                                Log.d(TAG, "📤 Loading additional ad...")
                                NativeAdManager.setLoading(true) // Set loading before request
                                NativeAdManager.recordRequest()
                                adLoaderRef?.loadAd(AdRequest.Builder().build())

                                // Reset loading flag after delay to allow next request
                                launch {
                                    delay(12000) // Wait for response (10s request + 2s buffer)
                                    NativeAdManager.setLoading(false)
                                }
                            } else if (!NativeAdManager.canMakeRequest()) {
                                Log.d(TAG, "⏸️ Cannot make request yet (throttled), will retry later")
                                // Reset loading flag so continuation can retry
                                NativeAdManager.setLoading(false)
                            }
                        }
                    }
                } else {
                    // Activity gone before we could register the ad. Route through
                    // registerForDestroy so loadedAtMs / positionAdCache stay consistent
                    // even if the ad was somehow added by a parallel callback.
                    NativeAdManager.registerForDestroy(nativeAd)
                }
            }.withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    val errorCode = adError.code
                    val errorMessage = adError.message ?: "unknown"
                    val currentCount = NativeAdManager.nativeAds.filterNotNull().size
                    val targetCount = NativeAdManager.getTargetAdCount()
                    
                    NativeAdManager.recordFailedLoad(errorCode, errorMessage)
                    
                    Log.w(TAG, "❌ Ad load failed - Code: $errorCode, " +
                            "Message: $errorMessage, Current ads: $currentCount/$targetCount, " +
                            "Retry count: $retryCount")
                    
                    AnalyticsUtils.logEvent(
                        AnalyticsEvent.AdFailed,
                        mapOf(
                            "error_code" to errorCode,
                            "error_message" to errorMessage,
                            "ad_type" to "native",
                            "current_ads_loaded" to currentCount,
                            "retry_count" to retryCount
                        )
                    )
                    
                    // Retry loading with exponential backoff (only for retryable errors)
                    // Error codes: 0=INVALID_REQUEST, 2=INVALID_AD_SIZE, 8=INVALID_APP_ID
                    val nonRetryableErrors = listOf(0, 2, 8)
                    
                    // Only retry if:
                    // 1. Error is retryable
                    // 2. We don't have enough ads
                    // 3. We haven't exceeded max retries
                    // 4. We can make a request (throttled)
                    // 5. User doesn't have premium (premium users don't see ads)
                    if (errorCode !in nonRetryableErrors && 
                        RemoteConfigUtils.shouldShowNativeAds() &&
                        currentCount < targetCount &&
                        NativeAdManager.canMakeRequest() &&
                        NativeAdManager.canRetry()) {
                        
                        val canRetry = NativeAdManager.recordRetryAttempt()
                        if (canRetry) {
                            retryCount++
                            val retryDelay = RemoteConfigUtils.getNativeAdRequestIntervalMs()
                            
                            Log.d(TAG, "🔄 Scheduling retry #$retryCount in ${retryDelay/1000} seconds...")
                            
                            adLoaderRef?.let { loader ->
                                coroutineScope.launch {
                                    delay(retryDelay)
                                    // Re-check premium status before retry (user might have purchased premium)
                                    if (!activity.isDestroyed && 
                                        RemoteConfigUtils.shouldShowNativeAds() &&
                                        NativeAdManager.nativeAds.filterNotNull().size < targetCount &&
                                        NativeAdManager.canMakeRequest()) {
                                        Log.d(TAG, "🔄 Executing retry #$retryCount...")
                                        NativeAdManager.recordRequest()
                                        loader.loadAd(AdRequest.Builder().build())
                                    } else if (!RemoteConfigUtils.shouldShowNativeAds()) {
                                        Log.d(TAG, "🚫 Premium user detected - cancelling retry")
                                    }
                                }
                            }
                        } else {
                            Log.w(TAG, "⚠️ Max retries reached. Stopping retry attempts.")
                        }
                    } else {
                        if (!NativeAdManager.canRetry()) {
                            Log.w(TAG, "⚠️ Max retries reached ($retryCount). Stopping retry attempts.")
                        } else if (errorCode in nonRetryableErrors) {
                            Log.w(TAG, "⚠️ Non-retryable error ($errorCode). Not retrying.")
                        }
                    }
                }

                override fun onAdClicked() {
                    Log.d(TAG, "👆 Ad clicked")
                    AnalyticsUtils.logEvent(AnalyticsEvent.AdClicked, mapOf("ad_type" to "native"))
                    AdRevenueOptimizer.trackAdClick(activity, "native")
                }
            }).withNativeAdOptions(NativeAdOptions.Builder().build()).build()
            .also { adLoaderRef = it }
    }

    // Check premium status reactively - don't load ads if user has premium
    val shouldShowAds = RemoteConfigUtils.shouldShowNativeAdsReactive()
    
    LaunchedEffect(Unit, shouldShowAds) {
        // Premium users never see ads - skip loading entirely
        if (!shouldShowAds) {
            Log.d(TAG, "🚫 Premium user detected - skipping native ad loading")
            return@LaunchedEffect
        }
        
        val targetAdCount = NativeAdManager.getTargetAdCount()
        val currentCount = NativeAdManager.nativeAds.filterNotNull().size
        
        Log.d(TAG, "🚀 Checking ad loader - Current: $currentCount, Target: $targetAdCount")
        
        // Mark as initialized (only first time), but continue loading if we don't have enough ads
        val isFirstInit = NativeAdManager.tryMarkInitialized()
        if (!NativeAdManager.tryStartLoadPipeline()) {
            Log.d(TAG, "⏳ Native load pipeline already active, skipping duplicate LaunchedEffect pipeline")
            return@LaunchedEffect
        }
        
        // Continue loading if we don't have enough ads, even if already initialized
        // Note: Allow continuation even if loading flag is set (it will be reset by individual requests)
        // Only check loading flag for first initialization to prevent duplicate initial loads
        if (currentCount < targetAdCount && NativeAdManager.canMakeRequest()) {
            if (isFirstInit && NativeAdManager.isCurrentlyLoading()) {
                Log.d(TAG, "⏳ Already loading ads (first init), skipping duplicate...")
                NativeAdManager.endLoadPipeline()
                return@LaunchedEffect
            }
            
            if (isFirstInit) {
                Log.d(TAG, "🆕 First initialization")
            } else {
                Log.d(TAG, "🔄 Continuing to load more ads (need ${targetAdCount - currentCount} more)")
            }
            
            NativeAdManager.setLoading(true)
            val adsToLoad = targetAdCount - currentCount
            val staggerMs = RemoteConfigUtils.getNativeAdRequestIntervalMs() + 1000L // interval + 1s buffer

            Log.i(TAG, "📥 Loading $adsToLoad more ads (staggered by ${staggerMs/1000}s each to respect throttling)...")

            repeat(adsToLoad) { index ->
                delay(index * staggerMs)
                // Re-check premium status before each ad load (user might have purchased premium)
                val shouldLoadAd = RemoteConfigUtils.shouldShowNativeAds()
                if (!activity.isDestroyed &&
                    shouldLoadAd &&
                    NativeAdManager.nativeAds.filterNotNull().size < targetAdCount) {

                    // Check if we can make request, if not, wait and retry
                    if (NativeAdManager.canMakeRequest()) {
                        Log.d(TAG, "📤 Requesting ad ${index + 1}/$adsToLoad...")
                        NativeAdManager.recordRequest()
                        adLoader.loadAd(AdRequest.Builder().build())
                    } else {
                        Log.d(TAG, "⏸️ Request ${index + 1}/$adsToLoad throttled, waiting...")
                        delay(RemoteConfigUtils.getNativeAdRequestIntervalMs())
                        val shouldLoadAdRetry = RemoteConfigUtils.shouldShowNativeAds()
                        if (NativeAdManager.canMakeRequest() &&
                            !activity.isDestroyed &&
                            shouldLoadAdRetry &&
                            NativeAdManager.nativeAds.filterNotNull().size < targetAdCount) {
                            Log.d(TAG, "📤 Retrying ad ${index + 1}/$adsToLoad...")
                            NativeAdManager.recordRequest()
                            adLoader.loadAd(AdRequest.Builder().build())
                        } else if (!shouldLoadAdRetry) {
                            Log.d(TAG, "🚫 Premium user detected - cancelling ad load")
                        }
                    }
                } else if (!shouldLoadAd) {
                    Log.d(TAG, "🚫 Premium user detected - skipping ad load ${index + 1}/$adsToLoad")
                }
            }

            // Reset loading flag after a delay to allow for async loading
            coroutineScope.launch {
                val timeout = if (adsToLoad > 0) {
                    (adsToLoad - 1) * staggerMs + 12000L
                } else {
                    12000L
                }
                delay(timeout)
                NativeAdManager.setLoading(false)
                val finalCount = NativeAdManager.nativeAds.filterNotNull().size
                Log.i(TAG, "📊 Load attempt completed - Current ads: $finalCount/$targetAdCount")
                if (finalCount < targetAdCount) {
                    NativeAdManager.logStats()
                    if (NativeAdManager.canMakeRequest() && !activity.isDestroyed) {
                        Log.d(TAG, "🔄 Continuing to load remaining ads...")
                        val remainingAds = targetAdCount - finalCount
                        repeat(remainingAds) { index ->
                            delay(index * staggerMs + 1000L)
                            if (!activity.isDestroyed &&
                                NativeAdManager.nativeAds.filterNotNull().size < targetAdCount &&
                                NativeAdManager.canMakeRequest()) {
                                Log.d(TAG, "📤 Requesting additional ad ${index + 1}/$remainingAds...")
                                NativeAdManager.setLoading(true)
                                NativeAdManager.recordRequest()
                                adLoader.loadAd(AdRequest.Builder().build())

                                launch {
                                    delay(12000)
                                    NativeAdManager.setLoading(false)
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "⏸️ Cannot continue loading - throttled or activity destroyed")
                    }
                } else {
                    Log.i(TAG, "✅ Successfully loaded all $finalCount ads!")
                }
                NativeAdManager.endLoadPipeline()
            }
        } else {
            if (currentCount >= targetAdCount) {
                Log.d(TAG, "✅ Already have enough ads ($currentCount >= $targetAdCount)")
            } else if (NativeAdManager.isCurrentlyLoading()) {
                Log.d(TAG, "⏳ Already loading ads...")
            } else {
                Log.d(TAG, "⏸️ Cannot make request (throttled)")
            }
            NativeAdManager.endLoadPipeline()
        }
    }

    // I2: refill wake-up. Fires whenever cacheGeneration bumps (i.e. an ad was
    // evicted/expired/added). If the pool drops below targetCount and we can make a
    // request, kick the loader. Without this, an evict-then-empty pool would never
    // repopulate because the LaunchedEffect above is keyed only on (Unit, shouldShowAds).
    LaunchedEffect(NativeAdManager.cacheGeneration.intValue, shouldShowAds) {
        if (!shouldShowAds || activity.isDestroyed) return@LaunchedEffect
        val targetCount = NativeAdManager.getTargetAdCount()
        val currentCount = NativeAdManager.nativeAds.filterNotNull().size
        if (currentCount >= targetCount) return@LaunchedEffect
        if (!NativeAdManager.canMakeRequest()) return@LaunchedEffect
        if (!NativeAdManager.tryStartLoadPipeline()) return@LaunchedEffect
        try {
            Log.d(TAG, "♻️ Refill wake-up: have $currentCount/$targetCount, requesting one ad (gen=${NativeAdManager.cacheGeneration.intValue})")
            NativeAdManager.setLoading(true)
            NativeAdManager.recordRequest()
            adLoaderRef?.loadAd(AdRequest.Builder().build())
            // Mirror the staggered LaunchedEffect's loadtime-clear coroutine: defer
            // endLoadPipeline + setLoading(false) so a parallel cacheGeneration bump
            // within the request window doesn't race a duplicate load.
            launch {
                delay(12000L)
                NativeAdManager.setLoading(false)
                NativeAdManager.endLoadPipeline()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Refill wake-up failed: ${e.message}", e)
            NativeAdManager.endLoadPipeline()
        }
    }

    return adLoader
}

