package com.teamz.lab.debugger.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.teamz.lab.debugger.R

/**
 * AdMob native ad renderer for DeviceGPT.
 *
 * v3.1.9 root-cause fix (2026-06-15):
 * Prior implementation used a Compose AndroidView factory that wrapped a single
 * ComposeView, registered it as the only callToActionView, then called
 * setNativeAd() BEFORE the Compose pass produced any laid-out child Views. The
 * AdMob SDK's viewability check then measured zero-sized asset slots and
 * suppressed 98% of impressions. See NativeAdViewabilityTest for the regression
 * guard that locks this contract.
 *
 * Current implementation: inflate native_ad_view.xml (real Android Views with
 * concrete @+ids), bind values to each typed slot, ASSIGN each slot to the
 * NativeAdView's typed property (headlineView, bodyView, iconView,
 * callToActionView), THEN call setNativeAd(). Order matters — typed slot
 * assignment MUST precede setNativeAd, and the slots MUST be real Views (not
 * ComposeViews) so AdMob can measure their size for the viewability check.
 */

@Composable
fun AdMobNativeAdCard(nativeAd: NativeAd, bottomPadding: Int = 16) {
    val adViewRef = remember { mutableStateOf<NativeAdView?>(null) }

    LaunchedEffect(nativeAd.hashCode()) {
        android.util.Log.d("AdImpression", "📊 Ad impression recorded - Ad hash: ${nativeAd.hashCode()}")
    }

    DisposableEffect(nativeAd) {
        onDispose {
            try {
                adViewRef.value?.destroy()
            } catch (e: Exception) {
                android.util.Log.w("NativeAdView", "adView.destroy() failed: ${e.message}")
            }
            if (NativeAdManager.consumePendingDestroy(nativeAd)) {
                try {
                    nativeAd.destroy()
                } catch (e: Exception) {
                    android.util.Log.w("NativeAdView", "ad.destroy() failed: ${e.message}")
                }
            }
            adViewRef.value = null
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding.dp),
        factory = { context ->
            val adView = LayoutInflater.from(context)
                .inflate(R.layout.native_ad_view, null, false) as NativeAdView
            NativeAdBinder.bind(adView, nativeAd)
            adView.setTag(R.id.native_ad_bound_hash, nativeAd.hashCode())
            adViewRef.value = adView
            adView
        },
        update = { adView ->
            val boundHash = adView.getTag(R.id.native_ad_bound_hash) as? Int
            val newHash = nativeAd.hashCode()
            if (boundHash == newHash) return@AndroidView
            NativeAdBinder.bind(adView, nativeAd)
            adView.setTag(R.id.native_ad_bound_hash, newHash)
        }
    )
}

/**
 * Single source of truth for the bind contract. Extracted as an object so
 * NativeAdViewabilityTest can drive it in a JVM/Robolectric environment without
 * invoking Compose.
 *
 * Contract (enforced by test):
 *   1. Each typed slot View MUST be found via findViewById and assigned to the
 *      NativeAdView's typed property (headlineView, bodyView, iconView,
 *      callToActionView) BEFORE setNativeAd is called.
 *   2. setNativeAd is called LAST, after all typed slot assignments.
 *   3. Slot Views must be real Android Views with concrete dimensions — not
 *      ComposeViews or empty wrappers.
 */
object NativeAdBinder {
    fun bind(adView: NativeAdView, ad: NativeAd) {
        val headline = adView.findViewById<TextView>(R.id.native_ad_headline)
        val body = adView.findViewById<TextView>(R.id.native_ad_body)
        val icon = adView.findViewById<ImageView>(R.id.native_ad_icon)
        val cta = adView.findViewById<Button>(R.id.native_ad_call_to_action)
        val adChoices = adView.findViewById<com.google.android.gms.ads.nativead.AdChoicesView>(
            R.id.native_ad_choices
        )

        headline.text = ad.headline ?: ""
        val bodyText = ad.body
        if (bodyText.isNullOrEmpty()) {
            body.visibility = android.view.View.GONE
        } else {
            body.visibility = android.view.View.VISIBLE
            body.text = bodyText
        }
        val iconAsset = ad.icon
        if (iconAsset == null) {
            icon.visibility = android.view.View.GONE
        } else {
            icon.visibility = android.view.View.VISIBLE
            icon.setImageDrawable(iconAsset.drawable)
        }
        val ctaText = ad.callToAction
        if (ctaText.isNullOrEmpty()) {
            cta.visibility = android.view.View.GONE
        } else {
            cta.visibility = android.view.View.VISIBLE
            cta.text = ctaText
        }

        // Typed slot registration MUST happen before setNativeAd. AdMob SDK
        // attaches click handlers + viewability observers at setNativeAd time.
        adView.headlineView = headline
        adView.bodyView = body
        adView.iconView = icon
        adView.callToActionView = cta
        adView.adChoicesView = adChoices

        adView.setNativeAd(ad)
    }
}

object NativeAdManager {
    var nativeAds = mutableStateListOf<NativeAd?>()
    @Volatile private var isLoading = false
    @Volatile private var hasInitialized = false
    private var lastRequestTime = 0L
    private var currentRotationIndex = 0
    private val initializationLock = Any()
    private val pipelineLock = Any()
    private val lifecycleLock = Any()
    @Volatile private var loadPipelineActive = false

    @Volatile private var totalRequests = 0
    @Volatile private var successfulLoads = 0
    @Volatile private var failedLoads = 0
    @Volatile private var retryAttempts = 0
    private val positionUsageMap = mutableMapOf<String, Int>()
    private val positionAdCache = mutableMapOf<String, NativeAd?>()
    private val loggedPositions = mutableSetOf<String>()

    private val loadedAtMs = mutableMapOf<NativeAd, Long>()

    private val pendingDestroy = java.util.Collections.synchronizedSet(mutableSetOf<NativeAd>())
    private const val PENDING_DESTROY_FALLBACK_MS = 500L
    private const val LOAD_TIMEOUT_MS = 12_000L

    val cacheGeneration: MutableIntState = mutableIntStateOf(0)

    private const val TAG = "NativeAdManager"

    fun addAd(ad: NativeAd) {
        synchronized(lifecycleLock) {
            nativeAds.add(ad)
            loadedAtMs[ad] = System.currentTimeMillis()
            positionAdCache.clear()
            loggedPositions.clear()
        }
        cacheGeneration.intValue++
    }

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

    fun consumePendingDestroy(ad: NativeAd): Boolean = pendingDestroy.remove(ad)

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
        snapshot.forEach { registerForDestroy(it) }
        synchronized(this) {
            isLoading = false
            hasInitialized = false
            currentRotationIndex = 0
        }
        resetStats()
        cacheGeneration.intValue++
    }

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

    fun tryMarkInitialized(): Boolean {
        return synchronized(initializationLock) {
            if (!hasInitialized) {
                hasInitialized = true
                true
            } else {
                false
            }
        }
    }

    fun hasBeenInitialized(): Boolean = synchronized(this) { hasInitialized }

    fun getNextAdForRotation(): NativeAd? {
        val validAds = nativeAds.filterNotNull()
        if (validAds.isEmpty()) return null

        synchronized(this) {
            val ad = validAds[currentRotationIndex % validAds.size]
            currentRotationIndex++
            return ad
        }
    }

    fun getAdAtIndex(index: Int): NativeAd? {
        val validAds = nativeAds.filterNotNull()
        if (validAds.isEmpty()) return null
        return validAds[index % validAds.size]
    }

    fun getAdForPosition(positionId: String): NativeAd? {
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

        if (cachedAd != null && validAds.contains(cachedAd) && !isExpired(cachedAd)) {
            return cachedAd
        }
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

    fun getTargetAdCount(): Int = com.teamz.lab.debugger.utils.RemoteConfigUtils.getNativeAdTargetCount()
}
