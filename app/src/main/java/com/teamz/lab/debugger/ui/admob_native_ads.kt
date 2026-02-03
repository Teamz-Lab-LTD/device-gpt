package com.teamz.lab.debugger.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
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
            Text(
                "Sponsored", style = MaterialTheme.typography.bodySmall,
                color = DesignSystemColors.Dark,
            )
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
        com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
            com.teamz.lab.debugger.utils.AnalyticsEvent.AdShownInline,
            mapOf(
                "ad_type" to "native",
                "ad_hash" to ad.hashCode(),
                "total_ads_loaded" to NativeAdManager.nativeAds.filterNotNull().size
            )
        )
    }
    
    AndroidView(
        factory = { context ->
            // Create a lightweight container first to avoid blocking composition
            // Generate container ID outside of async block to prevent JNI blocking during composition
            val containerId = View.generateViewId()
            val container = FrameLayout(context).apply {
                id = containerId
            }
            
            // Initialize NativeAdView asynchronously to prevent blocking the main thread
            // This allows the composition to complete while the ad view is being created
            // Defer all View creation to prevent JNI blocking during composition
            coroutineScope.launch(Dispatchers.Main) {
                try {
                    // Yield multiple times and add delay to allow other work to proceed
                    // This prevents ANR during WebView initialization which blocks on ConnectivityManager
                    // WebView.init() internally calls ConnectivityManager.getActiveNetworkInfo() which can block
                    kotlinx.coroutines.yield()
                    kotlinx.coroutines.delay(100) // Delay to let UI settle and other work complete
                    kotlinx.coroutines.yield()
                    kotlinx.coroutines.delay(100) // Additional delay to prevent ANR
                    kotlinx.coroutines.yield()
                    kotlinx.coroutines.delay(50) // Extra delay before JNI calls
                    kotlinx.coroutines.yield()
                    
                    // Create NativeAdView with timeout to prevent ANR
                    // WebView initialization can block on ConnectivityManager.getActiveNetworkInfo()
                    // Even with timeout, the blocking call happens synchronously, so we add delays before it
                    // Wrap in try-catch to handle any JNI exceptions gracefully
                    val adView = try {
                        kotlinx.coroutines.withTimeoutOrNull(2000) { // 2 second timeout
                            // Yield one more time before creating WebView
                            kotlinx.coroutines.yield()
                            // NativeAdView creation involves JNI calls that can block
                            // Add one more yield before the blocking call
                            kotlinx.coroutines.delay(50)
                            NativeAdView(context).apply {
                                id = adViewId
                            }
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        android.util.Log.w("NativeAdView", "NativeAdView creation timed out: ${e.message}")
                        null
                    } catch (e: Exception) {
                        android.util.Log.e("NativeAdView", "Error creating NativeAdView: ${e.message}", e)
                        null
                    }
                    
                    if (adView == null) {
                        // If timeout or error, skip ad display to prevent ANR
                        android.util.Log.w("NativeAdView", "NativeAdView creation failed, skipping ad display")
                        return@launch
                    }

                    // Yield before creating more views to prevent JNI blocking
                    kotlinx.coroutines.yield()
                    kotlinx.coroutines.delay(50)
                    
                    val contentView = try {
                        ComposeView(context).apply {
                            id = contentViewId
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NativeAdView", "Error creating ComposeView: ${e.message}", e)
                        return@launch
                    }

                    // AdChoices view required by AdMob - also involves JNI calls
                    // Yield before creating to prevent blocking
                    kotlinx.coroutines.yield()
                    val adChoicesView = try {
                        AdChoicesView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                Gravity.TOP or Gravity.END
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NativeAdView", "Error creating AdChoicesView: ${e.message}", e)
                        return@launch
                    }

                    adView.addView(contentView)
                    adView.addView(adChoicesView)

                    // Attach required views
                    adView.adChoicesView = adChoicesView
                    adView.headlineView = contentView
                    adView.bodyView = contentView
                    adView.iconView = contentView
                    adView.callToActionView = contentView

                    adView.setNativeAd(ad)

                    contentView.setContent {
                        adContent(ad, adView)
                    }
                    
                    // Defer adding the ad view to the container until after current layout pass completes
                    // This prevents ANR from Google Mobile Ads SDK's OnGlobalLayoutListener
                    // which calls PowerManager.isScreenOn() during layout callbacks
                    container.post {
                        // Post again to ensure we're after the layout pass
                        container.post {
                            try {
                                // Only add if container is still attached and adView is valid
                                if (container.parent != null && adView.parent == null) {
                                    container.addView(adView)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("NativeAdView", "Error adding ad view to container: ${e.message}", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Handle any errors during ad view creation
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
    private const val MIN_REQUEST_INTERVAL_MS = 10000L // 10 seconds between requests (increased from 5s)
    private var currentRotationIndex = 0 // For ad rotation
    private val initializationLock = Any() // Lock for thread-safe initialization
    
    // Tracking counters for monitoring
    @Volatile private var totalRequests = 0
    @Volatile private var successfulLoads = 0
    @Volatile private var failedLoads = 0
    @Volatile private var retryAttempts = 0
    private const val MAX_RETRIES = 3 // Limit retries to prevent infinite loops
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
        
        // Use position ID hash to assign different ads to different positions
        // This ensures same position always gets same ad (stable), but different positions get different ads
        val positionHash = positionId.hashCode()
        val adIndex = kotlin.math.abs(positionHash) % validAds.size
        val selectedAd = validAds[adIndex]
        
        // Cache the assignment
        positionAdCache[positionId] = selectedAd
        
        // Track which ad is being used for which position
        positionUsageMap[positionId] = adIndex
        val usageCount = positionUsageMap.values.count { it == adIndex }
        
        // Only log once per position to reduce spam (unless ad count changes)
        val logKey = "${positionId}_${validAds.size}_${adIndex}"
        if (!loggedPositions.contains(logKey)) {
            android.util.Log.d(TAG, "📍 Ad assigned - Position: $positionId, AdIndex: $adIndex/${validAds.size}, " +
                    "TotalAds: ${validAds.size}, SameAdUsedIn: $usageCount positions, " +
                    "AdHash: ${selectedAd.hashCode()}")
            loggedPositions.add(logKey)
            
            // Log analytics for ad rotation tracking (only once per position)
            com.teamz.lab.debugger.utils.AnalyticsUtils.logEvent(
                com.teamz.lab.debugger.utils.AnalyticsEvent.AdShownInList,
                mapOf(
                    "position_id" to positionId,
                    "ad_index" to adIndex,
                    "total_ads_loaded" to validAds.size,
                    "ad_rotation_working" to (validAds.size > 1)
                )
            )
        }
        
        return selectedAd
    }
    
    /**
     * Check if we can make a new ad request (throttling)
     */
    fun canMakeRequest(): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastRequestTime) >= MIN_REQUEST_INTERVAL_MS
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
        val canRetry = retryAttempts < MAX_RETRIES
        android.util.Log.d(TAG, "🔄 Retry attempt #$retryAttempts (max: $MAX_RETRIES, canRetry: $canRetry)")
        return canRetry
    }
    
    fun canRetry(): Boolean {
        return retryAttempts < MAX_RETRIES
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
    fun getTargetAdCount(): Int = 6 // Increased for better ad diversity
}



