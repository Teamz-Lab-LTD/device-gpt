package com.teamz.lab.debugger.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.teamz.lab.debugger.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.teamz.lab.debugger.ui.theme.DesignSystemColors


@Composable
fun AdMobNativeAdCard(nativeAd: NativeAd, bottomPadding: Int = 16) {
    NativeAdView(ad = nativeAd, adContent = { ad, composeView ->
        AppCard(
            borderColor = DesignSystemColors.NeonGreen,
            bottomPadding = bottomPadding,
            colors = CardDefaults.cardColors(
                containerColor = DesignSystemColors.NeonGreen,
                contentColor = DesignSystemColors.NeonGreen,
            )
        ) {
            // Prominent "Ad" badge — required by Google Play Deceptive Ads policy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = DesignSystemColors.Dark,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = DesignSystemColors.Dark,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Ad",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = DesignSystemColors.White,
                    )
                }
                Text(
                    "Sponsored",
                    style = MaterialTheme.typography.labelSmall,
                    color = DesignSystemColors.Dark.copy(alpha = 0.7f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                ad.headline ?: "",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
                color = DesignSystemColors.Dark,
            )

            ad.body?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    it,
                    color = DesignSystemColors.Dark,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Light,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ad.icon?.let {
                    Image(
                        painter = rememberAsyncImagePainter(it.drawable),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }

                ad.callToAction?.let {
                    Button(
                        colors = ButtonColors(
                            containerColor = DesignSystemColors.Dark,
                            contentColor = DesignSystemColors.White,
                            disabledContainerColor = DesignSystemColors.Dark,
                            disabledContentColor = DesignSystemColors.White
                        ),
                        onClick = { composeView.performClick() }) {
                        Text(it, color = DesignSystemColors.White)
                    }
                }
            }
        }
    })
}

@Composable
fun NativeAdView(
    ad: NativeAd,
    adContent: @Composable (ad: NativeAd, contentView: View) -> Unit,
) {
    // Stable refs to the inflated AdView + its inner ComposeView so update{} can
    // mutate them without findViewById and DisposableEffect can destroy the view
    // (NOT the NativeAd — manager owns ad lifecycle).
    val adViewRef = remember { mutableStateOf<NativeAdView?>(null) }
    val contentViewRef = remember { mutableStateOf<ComposeView?>(null) }

    // Track impression when ad is displayed
    LaunchedEffect(ad.hashCode()) {
        android.util.Log.d("AdImpression", "📊 Ad impression recorded - Ad hash: ${ad.hashCode()}")
    }

    // View-side cleanup ONLY. Pair with NativeAdManager.consumePendingDestroy(ad)
    // so the manager-driven eviction destroys the NativeAd AFTER the view, per AdMob
    // SDK contract (destroy view before ad).
    DisposableEffect(ad) {
        onDispose {
            try {
                adViewRef.value?.destroy()
            } catch (e: Exception) {
                android.util.Log.w("NativeAdView", "adView.destroy() failed: ${e.message}")
            }
            if (NativeAdManager.consumePendingDestroy(ad)) {
                try {
                    ad.destroy()
                } catch (e: Exception) {
                    android.util.Log.w("NativeAdView", "ad.destroy() failed: ${e.message}")
                }
            }
            adViewRef.value = null
            contentViewRef.value = null
        }
    }

    AndroidView(
        factory = { context ->
            // SYNCHRONOUS construction — return the NativeAdView itself, not a FrameLayout
            // wrapper. No coroutineScope.launch, no container.post. adViewRef.value is
            // guaranteed non-null before factory returns so update{} cannot race.
            val adView = NativeAdView(context)
            val contentView = ComposeView(context)
            val adChoicesView = AdChoicesView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                )
            }

            adView.addView(contentView)
            adView.addView(adChoicesView)

            // Only register callToActionView — registering all assets to the same
            // ComposeView breaks click attribution and lowers match/fill rate.
            adView.adChoicesView = adChoicesView
            adView.callToActionView = contentView
            adView.setNativeAd(ad)

            // Tag with the bound ad hash so update{} can no-op on identity-stable recomposes.
            adView.setTag(R.id.native_ad_bound_hash, ad.hashCode())

            contentView.setContent { adContent(ad, adView) }

            adViewRef.value = adView
            contentViewRef.value = contentView
            adView
        },
        update = { adView ->
            // Idempotent — skip if this AdView already holds this exact ad.
            val boundHash = adView.getTag(R.id.native_ad_bound_hash) as? Int
            val newHash = ad.hashCode()
            if (boundHash == newHash) return@AndroidView

            val contentView = contentViewRef.value ?: return@AndroidView
            adView.setNativeAd(ad)
            adView.callToActionView = contentView
            adView.setTag(R.id.native_ad_bound_hash, newHash)
            contentView.setContent { adContent(ad, adView) }
        }
    )
}

object NativeAdManager {
    var nativeAds = mutableStateListOf<NativeAd?>()
    @Volatile private var isLoading = false
    @Volatile private var hasInitialized = false
    private var lastRequestTime = 0L
    private var currentRotationIndex = 0 // For ad rotation
    private val initializationLock = Any() // Lock for thread-safe initialization
    private val pipelineLock = Any()
    private val lifecycleLock = Any() // Guards nativeAds + loadedAtMs + positionAdCache during evict/add
    @Volatile private var loadPipelineActive = false

    // Tracking counters for monitoring
    @Volatile private var totalRequests = 0
    @Volatile private var successfulLoads = 0
    @Volatile private var failedLoads = 0
    @Volatile private var retryAttempts = 0
    private val positionUsageMap = mutableMapOf<String, Int>() // Track which ad is used where
    private val positionAdCache = mutableMapOf<String, NativeAd?>() // Cache ad assignments to reduce redundant calls
    private val loggedPositions = mutableSetOf<String>() // Track which positions we've logged to reduce spam

    // Per-ad load timestamp for TTL-based eviction.
    private val loadedAtMs = mutableMapOf<NativeAd, Long>()

    // Two-phase eviction: removeFromPool() drops the ad from data structures and
    // bumps cacheGeneration; registerForDestroy() schedules ad.destroy() — actually
    // invoked by NativeAdView's DisposableEffect.onDispose (after adView.destroy()),
    // or by a 500ms Handler fallback for ads that never reached a view.
    private val pendingDestroy = java.util.Collections.synchronizedSet(mutableSetOf<NativeAd>())
    private const val PENDING_DESTROY_FALLBACK_MS = 500L
    private const val LOAD_TIMEOUT_MS = 12_000L

    /**
     * Compose-readable counter. Bumped on every addAd/removeFromPool/clear/invalidateCache.
     * Used as a remember() key at every NativeAd consumer surface so stale refs are
     * invalidated when the pool changes.
     */
    val cacheGeneration: MutableIntState = mutableIntStateOf(0)

    private const val TAG = "NativeAdManager"

    /**
     * Register a freshly-loaded NativeAd. Replaces raw nativeAds.add() so loadedAtMs
     * is populated in lockstep + cacheGeneration is bumped.
     */
    fun addAd(ad: NativeAd) {
        synchronized(lifecycleLock) {
            nativeAds.add(ad)
            loadedAtMs[ad] = System.currentTimeMillis()
            positionAdCache.clear()
            loggedPositions.clear()
        }
        cacheGeneration.intValue++
    }

    /**
     * Drop an ad from the pool + scrub all references to it. Does NOT call ad.destroy()
     * — pair with registerForDestroy() so the view-side DisposableEffect can destroy in
     * the correct order (adView.destroy() first, then ad.destroy()).
     */
    fun removeFromPool(ad: NativeAd) {
        synchronized(lifecycleLock) {
            nativeAds.remove(ad)
            loadedAtMs.remove(ad)
            val stalePositions = positionAdCache.entries
                .filter { it.value === ad }
                .map { it.key }
            stalePositions.forEach { positionAdCache.remove(it) }
        }
        cacheGeneration.intValue++
    }

    /**
     * Mark an ad for destruction. NativeAdView.DisposableEffect.onDispose will call
     * consumePendingDestroy(ad) and run ad.destroy() AFTER adView.destroy(). For ads
     * that never reached a view, a 500ms Handler fallback runs the destroy.
     */
    fun registerForDestroy(ad: NativeAd) {
        pendingDestroy.add(ad)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (pendingDestroy.remove(ad)) {
                try {
                    ad.destroy()
                    android.util.Log.d(TAG, "♻️ Handler fallback destroyed orphan ad ${ad.hashCode()}")
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Handler fallback destroy failed: ${e.message}")
                }
            }
        }, PENDING_DESTROY_FALLBACK_MS)
    }

    /**
     * Called by NativeAdView.DisposableEffect.onDispose. Returns true if this ad was
     * in pendingDestroy (and was removed); caller must then call ad.destroy() itself.
     */
    fun consumePendingDestroy(ad: NativeAd): Boolean = pendingDestroy.remove(ad)

    /** Returns true if ad is missing a timestamp or exceeds the Remote-Config TTL. */
    fun isExpired(ad: NativeAd): Boolean {
        val ts = synchronized(lifecycleLock) { loadedAtMs[ad] } ?: return true
        return (System.currentTimeMillis() - ts) > com.teamz.lab.debugger.utils.RemoteConfigUtils.getNativeAdTtlMs()
    }

    fun clear() {
        val snapshot: List<NativeAd>
        synchronized(lifecycleLock) {
            snapshot = nativeAds.filterNotNull().toList()
            nativeAds.clear()
            loadedAtMs.clear()
            positionAdCache.clear()
            positionUsageMap.clear()
            loggedPositions.clear()
        }
        // Route each ad through registerForDestroy so any live NativeAdView destroys
        // its adView first via DisposableEffect.onDispose.
        snapshot.forEach { registerForDestroy(it) }
        synchronized(this) {
            isLoading = false
            hasInitialized = false
            currentRotationIndex = 0
        }
        resetStats()
        cacheGeneration.intValue++
    }

    /**
     * Clear cache when ads are added/removed to ensure fresh assignments
     */
    fun invalidateCache() {
        synchronized(lifecycleLock) {
            positionAdCache.clear()
            loggedPositions.clear()
        }
        cacheGeneration.intValue++
        android.util.Log.d(TAG, "🔄 Ad cache invalidated (gen=${cacheGeneration.intValue})")
    }
    
    fun setLoading(loading: Boolean) {
        synchronized(this) {
            isLoading = loading
        }
    }

    fun tryStartLoadPipeline(): Boolean = synchronized(pipelineLock) {
        if (loadPipelineActive) return false
        loadPipelineActive = true
        true
    }

    fun endLoadPipeline() {
        synchronized(pipelineLock) {
            loadPipelineActive = false
        }
    }
    
    fun isCurrentlyLoading(): Boolean = synchronized(this) { isLoading }
    
    /**
     * Atomically check and mark as initialized
     * Returns true if this was the first call (should initialize)
     * Returns false if already initialized (should skip)
     */
    fun tryMarkInitialized(): Boolean {
        return synchronized(initializationLock) {
            if (!hasInitialized) {
                hasInitialized = true
                true // First to initialize
            } else {
                false // Already initialized
            }
        }
    }
    
    fun hasBeenInitialized(): Boolean = synchronized(this) { hasInitialized }
    
    /**
     * Get next ad in rotation for "every 5 items" display
     * This ensures different ads are shown, maximizing revenue
     * 
     * NOTE: This increments rotation index - use carefully to avoid frequent changes
     * For stable ad display, prefer pre-calculating ad assignments
     */
    fun getNextAdForRotation(): NativeAd? {
        val validAds = nativeAds.filterNotNull()
        if (validAds.isEmpty()) return null
        
        synchronized(this) {
            val ad = validAds[currentRotationIndex % validAds.size]
            currentRotationIndex++
            return ad
        }
    }
    
    /**
     * Get ad at specific index (for stable assignment)
     * This doesn't increment rotation index, so it's safe for recomposition
     */
    fun getAdAtIndex(index: Int): NativeAd? {
        val validAds = nativeAds.filterNotNull()
        if (validAds.isEmpty()) return null
        return validAds[index % validAds.size]
    }
    
    /**
     * Get a unique ad for a specific position (for different ads in different places)
     * AdMob Best Practice: Show different ads in different positions to maximize revenue
     * 
     * @param positionId Unique identifier for the position (e.g., "top_banner", "list_0", "list_5")
     * @return Different ad for each position, or fallback to same ad if not enough ads available
     */
    fun getAdForPosition(positionId: String): NativeAd? {
        // 1. Snapshot pool + identify expired ads. Eviction shrinks nativeAds.size so
        //    the refill LaunchedEffect at expandable_info_list wakes up and reloads.
        val expired = synchronized(lifecycleLock) {
            nativeAds.filterNotNull().filter { isExpired(it) }
        }
        expired.forEach { stale ->
            android.util.Log.i(TAG, "♻️ Expiring stale ad ${stale.hashCode()}")
            removeFromPool(stale)
            registerForDestroy(stale)
        }

        val cachedAd = positionAdCache[positionId]
        val validAds = nativeAds.filterNotNull()

        // 2. Cached ad still in pool AND not expired → reuse.
        if (cachedAd != null && validAds.contains(cachedAd) && !isExpired(cachedAd)) {
            return cachedAd
        }
        // 3. Cached ad gone/stale → scrub the cache entry.
        if (cachedAd != null) {
            synchronized(lifecycleLock) { positionAdCache.remove(positionId) }
        }

        if (validAds.isEmpty()) {
            if (!loggedPositions.contains("${positionId}_empty")) {
                android.util.Log.w(TAG, "⚠️ getAdForPosition($positionId): No ads available")
                loggedPositions.add("${positionId}_empty")
            }
            positionAdCache[positionId] = null
            return null
        }

        // Assign a stable sequential index to each position on first encounter.
        // hashCode() has poor distribution for sequential names like "list_5", "list_10" etc.
        // Once a position is seen, its index never changes — only the ad it maps to may shift
        // when the number of loaded ads changes (validAds.size).
        if (!positionUsageMap.containsKey(positionId)) {
            positionUsageMap[positionId] = positionUsageMap.size
        }
        val adIndex = positionUsageMap[positionId]!! % validAds.size
        val selectedAd = validAds[adIndex]

        positionAdCache[positionId] = selectedAd

        val usageCount = positionUsageMap.values.count { it % validAds.size == adIndex }
        val logKey = "${positionId}_${validAds.size}_${adIndex}"
        if (!loggedPositions.contains(logKey)) {
            android.util.Log.d(TAG, "📍 Ad assigned - Position: $positionId, AdIndex: $adIndex/${validAds.size}, " +
                    "TotalAds: ${validAds.size}, SameAdUsedIn: $usageCount positions, " +
                    "AdHash: ${selectedAd.hashCode()}, Gen: ${cacheGeneration.intValue}")
            loggedPositions.add(logKey)
        }

        return selectedAd
    }
    
    /**
     * Check if we can make a new ad request (throttling)
     */
    fun canMakeRequest(): Boolean {
        val maxRequests = com.teamz.lab.debugger.utils.RemoteConfigUtils.getNativeAdMaxRequestsPerSession()
        if (totalRequests >= maxRequests) {
            android.util.Log.w(TAG, "⛔ Native ad request budget reached ($totalRequests/$maxRequests)")
            return false
        }
        val now = System.currentTimeMillis()
        val minInterval = com.teamz.lab.debugger.utils.RemoteConfigUtils.getNativeAdRequestIntervalMs()
        return (now - lastRequestTime) >= minInterval
    }
    
    fun recordRequest() {
        lastRequestTime = System.currentTimeMillis()
        totalRequests++
        android.util.Log.d(TAG, "📤 Ad request #$totalRequests recorded at ${System.currentTimeMillis()}")
    }
    
    fun recordSuccessfulLoad() {
        successfulLoads++
        val validAds = nativeAds.filterNotNull().size
        val successRate = if (totalRequests > 0) {
            (successfulLoads * 100.0 / totalRequests)
        } else {
            0.0
        }
        android.util.Log.i(TAG, "✅ Ad loaded successfully! Total loaded: $validAds, " +
                "Success rate: ${String.format("%.2f", successRate)}% " +
                "($successfulLoads/$totalRequests)")
    }
    
    fun recordFailedLoad(errorCode: Int, errorMessage: String) {
        failedLoads++
        val failureRate = if (totalRequests > 0) {
            (failedLoads * 100.0 / totalRequests)
        } else {
            0.0
        }
        android.util.Log.w(TAG, "❌ Ad load failed #$failedLoads - Code: $errorCode, " +
                "Message: $errorMessage, Failure rate: ${String.format("%.2f", failureRate)}%")
    }
    
    fun recordRetryAttempt(): Boolean {
        retryAttempts++
        val maxRetries = com.teamz.lab.debugger.utils.RemoteConfigUtils.getNativeAdMaxRetries()
        val canRetry = retryAttempts < maxRetries
        android.util.Log.d(TAG, "🔄 Retry attempt #$retryAttempts (max: $maxRetries, canRetry: $canRetry)")
        return canRetry
    }

    fun canRetry(): Boolean {
        val maxRetries = com.teamz.lab.debugger.utils.RemoteConfigUtils.getNativeAdMaxRetries()
        return retryAttempts < maxRetries
    }
    
    fun resetRetryCount() {
        retryAttempts = 0
    }
    
    fun getStats(): String {
        val validAds = nativeAds.filterNotNull().size
        val successRate = if (totalRequests > 0) {
            (successfulLoads * 100.0 / totalRequests)
        } else {
            0.0
        }
        return """
            📊 Native Ad Stats:
            - Total Requests: $totalRequests
            - Successful Loads: $successfulLoads
            - Failed Loads: $failedLoads
            - Retry Attempts: $retryAttempts
            - Currently Loaded Ads: $validAds
            - Target Ad Count: ${getTargetAdCount()}
            - Success Rate: ${String.format("%.2f", successRate)}%
            - Position Usage: ${positionUsageMap.size} unique positions
        """.trimIndent()
    }
    
    fun logStats() {
        android.util.Log.i(TAG, getStats())
    }
    
    fun resetStats() {
        totalRequests = 0
        successfulLoads = 0
        failedLoads = 0
        retryAttempts = 0
        positionUsageMap.clear()
        android.util.Log.d(TAG, "🔄 Stats reset")
    }
    
    /**
     * Get target ad count based on usage
     * AdMob Best Practice: Load multiple ads to show different ads in different positions
     * This prevents ad fatigue and maximizes revenue
     * 
     * For leaderboard:
     * - 1 top banner ad
     * - Up to 10-20 list ads (every 5 entries = 2-4 ads visible)
     * - Total: 5-6 ads for good diversity
     */
    fun getTargetAdCount(): Int = com.teamz.lab.debugger.utils.RemoteConfigUtils.getNativeAdTargetCount()
}



