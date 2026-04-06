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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val contentViewId by remember { mutableIntStateOf(View.generateViewId()) }
    val adViewId by remember { mutableIntStateOf(View.generateViewId()) }
    val coroutineScope = rememberCoroutineScope()
    
    // Track impression when ad is displayed
    LaunchedEffect(ad.hashCode()) {
        android.util.Log.d("AdImpression", "📊 Ad impression recorded - Ad hash: ${ad.hashCode()}")
    }
    
    AndroidView(
        factory = { context ->
            // Create a lightweight container first to avoid blocking composition
            // Generate container ID outside of async block to prevent JNI blocking during composition
            val containerId = View.generateViewId()
            val container = FrameLayout(context).apply {
                id = containerId
            }
            
            coroutineScope.launch(Dispatchers.Main) {
                try {
                    // Single yield to let the current composition frame complete before creating views
                    kotlinx.coroutines.yield()

                    val adView = NativeAdView(context).apply { id = adViewId }

                    val contentView = ComposeView(context).apply { id = contentViewId }

                    val adChoicesView = AdChoicesView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.END
                        )
                    }

                    adView.addView(contentView)
                    adView.addView(adChoicesView)

                    // Only register callToActionView so AdMob can track clicks correctly.
                    // Registering all assets to the same ComposeView breaks click attribution
                    // and causes AdMob to penalise the unit with lower match rate and fill rate.
                    adView.adChoicesView = adChoicesView
                    adView.callToActionView = contentView

                    adView.setNativeAd(ad)

                    contentView.setContent {
                        adContent(ad, adView)
                    }

                    // Single post to defer past the current layout pass
                    container.post {
                        try {
                            if (container.parent != null && adView.parent == null) {
                                container.addView(adView)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("NativeAdView", "Error adding ad view to container: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NativeAdView", "Error creating NativeAdView: ${e.message}", e)
                }
            }
            
            container
        },
        update = { view ->
            // Find the NativeAdView if it's been created
            val adView = view.findViewById<NativeAdView>(adViewId)
            if (adView != null) {
                val contentView = view.findViewById<ComposeView>(contentViewId)
                if (contentView != null) {
                    adView.setNativeAd(ad)
                    adView.callToActionView = contentView
                    contentView.setContent { adContent(ad, contentView) }
                }
            }
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
    @Volatile private var loadPipelineActive = false

    // Tracking counters for monitoring
    @Volatile private var totalRequests = 0
    @Volatile private var successfulLoads = 0
    @Volatile private var failedLoads = 0
    @Volatile private var retryAttempts = 0
    private val positionUsageMap = mutableMapOf<String, Int>() // Track which ad is used where
    private val positionAdCache = mutableMapOf<String, NativeAd?>() // Cache ad assignments to reduce redundant calls
    private val loggedPositions = mutableSetOf<String>() // Track which positions we've logged to reduce spam
    
    private const val TAG = "NativeAdManager"

    fun clear() {
        nativeAds.forEach { it?.destroy() }
        nativeAds.clear()
        synchronized(this) {
            isLoading = false
            hasInitialized = false
            currentRotationIndex = 0
        }
        positionAdCache.clear()
        loggedPositions.clear()
        resetStats()
    }
    
    /**
     * Clear cache when ads are added/removed to ensure fresh assignments
     */
    fun invalidateCache() {
        positionAdCache.clear()
        loggedPositions.clear()
        android.util.Log.d(TAG, "🔄 Ad cache invalidated")
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
        // Check cache first to avoid redundant calculations
        val cachedAd = positionAdCache[positionId]
        val validAds = nativeAds.filterNotNull()
        
        // If cached ad is still valid, return it
        if (cachedAd != null && validAds.contains(cachedAd)) {
            return cachedAd
        }
        
        // Cache is invalid or doesn't exist, calculate new assignment
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
        
        // Cache the assignment
        positionAdCache[positionId] = selectedAd
        
        // Track usage for logging (don't overwrite the stable index stored on first encounter)
        val usageCount = positionUsageMap.values.count { it % validAds.size == adIndex }
        
        // Only log once per position to reduce spam (unless ad count changes)
        val logKey = "${positionId}_${validAds.size}_${adIndex}"
        if (!loggedPositions.contains(logKey)) {
            android.util.Log.d(TAG, "📍 Ad assigned - Position: $positionId, AdIndex: $adIndex/${validAds.size}, " +
                    "TotalAds: ${validAds.size}, SameAdUsedIn: $usageCount positions, " +
                    "AdHash: ${selectedAd.hashCode()}")
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



