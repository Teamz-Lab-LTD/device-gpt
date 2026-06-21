package com.teamz.lab.debugger.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.1.11 W1 ad-pipeline fix — native ad reentrancy regression guard.
 *
 * Workflow wf_d944db5e (2026-06-21) identified 4 reentrant coroutines in
 * expandable_info_list.kt that ALL called `adLoader.loadAd()` independently,
 * racing through a 12-second time-based `loadPipelineActive` flag. This
 * architecture produced 8,463 weekly native ad requests for 29 impressions
 * (3.7% show rate) by mathematically guaranteeing fan-out.
 *
 * The converged fix DELETES three of the four reentrant paths and lets the
 * refill `LaunchedEffect` (keyed on `cacheGeneration`) be the SINGLE source
 * of "we need another ad." Both `addAd()` (success) and `invalidateCache()`
 * (failure) bump `cacheGeneration`, waking the refill — which checks budget,
 * count, and pipeline lock before firing ONE request.
 *
 * This test asserts the 3 deletion landmarks remain + the legitimate paths
 * are intact. Without it, a future PR could silently re-introduce the bug.
 */
class NativeAdReentrancyContractTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private val src: String by lazy {
        File(projectRoot(), "app/src/main/java/com/teamz/lab/debugger/ui/expandable_info_list.kt").readText()
    }

    @Test
    fun `onAdLoaded continuation deleted - was reentrant path 1 of 4`() {
        // Pre-fix: onAdLoaded body had a coroutineScope.launch block that scheduled
        // another loadAd if cached count < target. That re-entered the request loop
        // for every loaded ad. With targetCount=1 this fired exactly the same number
        // of requests as the staggered loop, doubling the volume.
        assertTrue(
            "Deletion-landmark comment missing for onAdLoaded continuation. If you removed " +
                "the comment, also verify NO `coroutineScope.launch` block remains inside " +
                "the onAdLoaded callback that calls adLoaderRef?.loadAd or adLoader.loadAd.",
            src.contains("DELETED the onAdLoaded continuation")
        )
        // Stronger guard: between `recordSuccessfulLoad()` (success path marker) and
        // `addAd(nativeAd)` (the post-load addition) there must NOT be a coroutineScope.launch.
        val recordSuccess = src.indexOf("NativeAdManager.resetRetryCount()")
        val elseClose = src.indexOf("} else {", recordSuccess)
        assertTrue("onAdLoaded body must exist", recordSuccess > 0 && elseClose > recordSuccess)
        val body = src.substring(recordSuccess, elseClose)
        assertFalse(
            "onAdLoaded body must NOT contain a coroutineScope.launch — reentrant path 1 has returned. body=$body",
            body.contains("coroutineScope.launch")
        )
    }

    @Test
    fun `onAdFailedToLoad retry coroutine deleted - was reentrant path 2 of 4`() {
        // Pre-fix: onAdFailedToLoad had a coroutineScope.launch{ delay(retryDelay); ... loadAd }
        // block that retried every failure, doubling auction slot consumption.
        // NEW: just bump cacheGeneration via invalidateCache + reset isLoading.
        assertTrue(
            "Deletion-landmark comment missing for onAdFailedToLoad retry coroutine.",
            src.contains("DELETED the onAdFailedToLoad")
        )
        // Stronger guard: onAdFailedToLoad body must contain invalidateCache() AND
        // must NOT contain coroutineScope.launch.
        val failedIdx = src.indexOf("override fun onAdFailedToLoad(adError: LoadAdError)")
        assertTrue("onAdFailedToLoad must exist", failedIdx > 0)
        val nextOverride = src.indexOf("override fun ", failedIdx + 1)
        val body = src.substring(failedIdx, if (nextOverride > 0) nextOverride else failedIdx + 4000)
        assertTrue(
            "onAdFailedToLoad must call NativeAdManager.invalidateCache() to bump cacheGeneration " +
                "and wake the refill LaunchedEffect — that is now the single retry path.",
            body.contains("NativeAdManager.invalidateCache()")
        )
        assertFalse(
            "onAdFailedToLoad must NOT contain coroutineScope.launch — reentrant path 2 has returned.",
            body.contains("coroutineScope.launch")
        )
    }

    @Test
    fun `inner remainingAds continuation deleted - was reentrant path 3 of 4`() {
        // Pre-fix: after the 12s timeout in the initial staggered loop, ANOTHER
        // repeat(remainingAds) block fired more loadAd() calls if finalCount < target.
        // That doubled the initial-pipeline fan-out per LaunchedEffect activation.
        // NEW: refill LaunchedEffect handles "still need more ads" via cacheGeneration.
        assertTrue(
            "Deletion-landmark comment missing for inner remainingAds continuation.",
            src.contains("safety-net pipeline release") &&
                src.contains("refill LaunchedEffect")
        )
        // Stronger guard: count occurrences of `repeat(adsToLoad)` vs `repeat(remainingAds)`.
        // The initial staggered loop uses `repeat(adsToLoad)` and is allowed. Any
        // `repeat(remainingAds)` would indicate the deleted continuation came back.
        // Match actual code (must be followed by `{`), not a backtick-quoted comment reference.
        val remainingAdsCount = Regex("""repeat\s*\(\s*remainingAds\s*\)\s*\{""").findAll(src).count()
        assertEquals(
            "repeat(remainingAds) must NOT exist anywhere in expandable_info_list.kt — that block " +
                "is the deleted inner continuation. Refill LaunchedEffect handles 'need more ads'.",
            0, remainingAdsCount
        )
    }

    @Test
    fun `refill LaunchedEffect is preserved - sole source of refill requests`() {
        // The refill LaunchedEffect (keyed on cacheGeneration) is now the SOLE source
        // of refill requests. Both successful loads (addAd bumps cacheGeneration) and
        // failures (invalidateCache bumps cacheGeneration) wake it. It checks budget,
        // count, premium, and pipeline lock before firing exactly ONE request.
        assertTrue(
            "Refill LaunchedEffect must exist — it is now the SINGLE refill source.",
            src.contains("LaunchedEffect(NativeAdManager.cacheGeneration.intValue, shouldShowAds)")
        )
        // The refill body must check ALL gates before firing.
        val refillStart = src.indexOf("LaunchedEffect(NativeAdManager.cacheGeneration.intValue, shouldShowAds)")
        val refillEnd = src.indexOf("\n    return adLoader", refillStart).let {
            if (it < 0) src.length else it
        }
        val refillBody = src.substring(refillStart, refillEnd)
        assertTrue("Refill must check cached count vs target", refillBody.contains("currentCount >= targetCount"))
        assertTrue("Refill must check budget via canMakeRequest", refillBody.contains("canMakeRequest()"))
        assertTrue("Refill must check pipeline lock via tryStartLoadPipeline", refillBody.contains("tryStartLoadPipeline()"))
        assertTrue("Refill must fire exactly one loadAd", refillBody.contains("adLoaderRef?.loadAd"))
    }

    @Test
    fun `initial staggered loop is preserved - kicks the first cold-start batch`() {
        // The initial staggered repeat(adsToLoad) loop in the LaunchedEffect(Unit, shouldShowAds)
        // block remains. It fires the first batch of ads on cold-start. After that,
        // the refill LaunchedEffect handles everything.
        val initialLoopRegex = Regex("""repeat\s*\(\s*adsToLoad\s*\)\s*\{""")
        assertTrue(
            "Initial staggered repeat(adsToLoad) loop must exist — it kicks the first cold-start batch.",
            initialLoopRegex.containsMatchIn(src)
        )
        assertTrue(
            "Initial pipeline must still call NativeAdManager.tryStartLoadPipeline() to gate concurrent runs.",
            src.contains("NativeAdManager.tryStartLoadPipeline()")
        )
        assertTrue(
            "Initial pipeline release must still happen via NativeAdManager.endLoadPipeline() after the 12s timeout safety net.",
            src.contains("NativeAdManager.endLoadPipeline()")
        )
    }

    @Test
    fun `no coroutineScope launch in onAdLoaded or onAdFailedToLoad bodies anywhere`() {
        // The strongest regression guard. ANY coroutineScope.launch inside ad callback
        // bodies is reentrancy. Count occurrences of coroutineScope.launch globally and
        // assert each one is OUTSIDE callback bodies (i.e. in the LaunchedEffect blocks).
        val callbackBodies = listOf(
            "override fun onAdLoaded" to "override fun ",
            "override fun onAdFailedToLoad" to "override fun ",
        )
        for ((start, endMarker) in callbackBodies) {
            val s = src.indexOf(start)
            if (s < 0) continue
            val e = src.indexOf(endMarker, s + 1)
            val body = src.substring(s, if (e > 0) e else s + 5000)
            assertFalse(
                "Callback body starting with '$start' must NOT contain coroutineScope.launch — reentrancy regression. body length=${body.length}",
                body.contains("coroutineScope.launch")
            )
        }
    }
}
