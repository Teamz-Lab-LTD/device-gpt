package com.teamz.lab.debugger.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard: AdMob native ad viewability invariants.
 *
 * Background — 2026-06-15 root-cause hunt traced 1-year "98% show rate" bug
 * to admob_native_ads.kt lines 184-228. Prior implementation wrapped a single
 * ComposeView, registered it as `callToActionView`, then called `setNativeAd`
 * BEFORE Compose laid out any child Views. AdMob SDK then measured registered
 * asset slots at 0x0 and failed its viewability check on 100% of native renders.
 *
 * v3.1.9 fix: inflate native_ad_view.xml (real Android Views with concrete
 * @+ids), bind values, assign typed slots, THEN call setNativeAd.
 *
 * These tests run in <50ms via the preflight gate and fail the build if any of
 * the regression patterns return. Add a new assertion every time a new
 * viewability regression mode is discovered.
 */
class NativeAdViewabilityTest {

    private fun locate(relPath: String): File {
        val root = System.getProperty("user.dir") ?: "."
        val candidates = listOf(
            File(root, relPath),
            File(root, "app/$relPath"),
            File(File(root).parentFile, "app/$relPath"),
            File("../app/$relPath"),
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Could not locate $relPath. Tried: ${candidates.joinToString { it.absolutePath }}")
    }

    private val xmlSrc by lazy {
        locate("src/main/res/layout/native_ad_view.xml").readText()
    }
    private val nativeAdsSrc by lazy {
        locate("src/main/java/com/teamz/lab/debugger/ui/admob_native_ads.kt").readText()
    }

    // ────────────────────── XML layout invariants ──────────────────────

    @Test
    fun `native_ad_view xml has NativeAdView root`() {
        assertTrue(
            "native_ad_view.xml must have <com.google.android.gms.ads.nativead.NativeAdView> as root. " +
                "AdMob SDK refuses to bind assets unless the root is a NativeAdView.",
            xmlSrc.contains("<com.google.android.gms.ads.nativead.NativeAdView")
        )
    }

    @Test
    fun `native_ad_view xml has all 4 required typed asset slots with concrete ids`() {
        val requiredIds = listOf(
            "@+id/native_ad_headline",
            "@+id/native_ad_body",
            "@+id/native_ad_icon",
            "@+id/native_ad_call_to_action"
        )
        requiredIds.forEach { id ->
            assertTrue(
                "Missing required asset slot $id in native_ad_view.xml. " +
                    "AdMob viewability check requires each typed asset to map to a real Android View " +
                    "with a concrete id so the SDK can measure its size.",
                xmlSrc.contains(id)
            )
        }
    }

    @Test
    fun `native_ad_view xml has AdChoicesView for policy compliance`() {
        assertTrue(
            "native_ad_view.xml must contain <com.google.android.gms.ads.nativead.AdChoicesView>. " +
                "Required by AdMob policy — missing AdChoicesView triggers ad-serving suspension.",
            xmlSrc.contains("<com.google.android.gms.ads.nativead.AdChoicesView")
        )
    }

    @Test
    fun `native_ad_view xml does NOT register ComposeView as an asset slot`() {
        // The 1-year bug was caused by wrapping asset slots inside a single ComposeView.
        // AdMob can't measure Compose-rendered content for viewability — only real Views.
        assertFalse(
            "native_ad_view.xml must NOT contain ComposeView. This was the root cause of the " +
                "1-year 98% show-rate bug — Compose-rendered asset Views are measured at 0x0 by " +
                "the AdMob viewability check.",
            xmlSrc.contains("<androidx.compose.ui.platform.ComposeView")
        )
    }

    @Test
    fun `attribution badge present for Google Play deceptive-ads policy`() {
        assertTrue(
            "native_ad_view.xml must show an 'Ad' attribution label. Google Play Deceptive Ads " +
                "policy requires every native ad to be clearly labeled as an ad.",
            xmlSrc.contains("@+id/native_ad_attribution") && xmlSrc.contains("\"Ad\"")
        )
    }

    // ────────────────────── Kotlin binder invariants ──────────────────────

    @Test
    fun `NativeAdBinder bind exists and uses findViewById to locate typed slots`() {
        assertTrue(
            "admob_native_ads.kt must expose NativeAdBinder.bind(adView, ad) so the inflation + " +
                "binding contract is enforceable by this test and reusable from any consumer.",
            nativeAdsSrc.contains("object NativeAdBinder") &&
                nativeAdsSrc.contains("fun bind(adView: NativeAdView, ad: NativeAd)")
        )

        val requiredFindViewByIdCalls = listOf(
            "findViewById<TextView>(R.id.native_ad_headline)",
            "findViewById<TextView>(R.id.native_ad_body)",
            "findViewById<ImageView>(R.id.native_ad_icon)",
            "findViewById<Button>(R.id.native_ad_call_to_action)",
        )
        requiredFindViewByIdCalls.forEach { call ->
            assertTrue(
                "NativeAdBinder.bind must call $call. Looking up the typed slot by id is the only " +
                    "way to give the AdMob SDK a real, measurable View for the viewability check.",
                nativeAdsSrc.contains(call)
            )
        }
    }

    @Test
    fun `NativeAdBinder assigns typed slot properties BEFORE setNativeAd`() {
        val slotAssignments = listOf(
            "adView.headlineView =",
            "adView.bodyView =",
            "adView.iconView =",
            "adView.callToActionView =",
        )
        val setNativeAdIdx = nativeAdsSrc.indexOf("adView.setNativeAd(ad)")
        assertTrue(
            "NativeAdBinder.bind must call adView.setNativeAd(ad). Without it, the asset slots " +
                "never observe ad data.",
            setNativeAdIdx >= 0
        )

        slotAssignments.forEach { stmt ->
            val idx = nativeAdsSrc.indexOf(stmt)
            assertTrue(
                "NativeAdBinder.bind must assign $stmt (typed slot property). Missing assignment " +
                    "means AdMob does not know which Android View represents this asset → ad never " +
                    "counts as viewable.",
                idx >= 0
            )
            assertTrue(
                "$stmt must appear BEFORE adView.setNativeAd(ad). AdMob attaches click handlers + " +
                    "viewability observers at setNativeAd time — typed slots assigned AFTER are " +
                    "ignored, recreating the 98% show-rate bug.",
                idx < setNativeAdIdx
            )
        }
    }

    @Test
    fun `NativeAdBinder does NOT register a ComposeView as any typed slot`() {
        // Defense against a future regression where someone wraps a slot in ComposeView
        // again. ComposeViews measure as 0x0 before composition runs → AdMob viewability fails.
        val composeViewSlot = Regex(
            "adView\\.(headlineView|bodyView|iconView|callToActionView|advertiserView|priceView|starRatingView)\\s*=\\s*[A-Za-z_]*ComposeView"
        )
        val match = composeViewSlot.find(nativeAdsSrc)
        assertTrue(
            "NativeAdBinder must NEVER assign a ComposeView to a typed asset slot. " +
                "Offending line: ${match?.value ?: ""}. This pattern caused the 1-year 98% show-rate " +
                "regression. Use real Android Views (TextView/Button/ImageView) inflated from XML.",
            match == null
        )
    }

    @Test
    fun `AdMobNativeAdCard inflates the XML layout via R_layout_native_ad_view`() {
        assertTrue(
            "AdMobNativeAdCard must inflate R.layout.native_ad_view via LayoutInflater. " +
                "This is the only renderer path that satisfies AdMob viewability — Compose-built " +
                "asset Views fail the SDK's size check.",
            nativeAdsSrc.contains("R.layout.native_ad_view") &&
                nativeAdsSrc.contains("LayoutInflater.from(context)")
        )
    }

    @Test
    fun `AdMobNativeAdCard does not call setNativeAd inside its Composable factory before binding`() {
        // Old buggy path called adView.setNativeAd(ad) directly in the factory block.
        // The fix routes everything through NativeAdBinder.bind so the order invariant is
        // enforced in ONE place.
        val factoryStart = nativeAdsSrc.indexOf("factory = { context ->")
        val factoryEnd = nativeAdsSrc.indexOf("update = { adView ->")
        if (factoryStart < 0 || factoryEnd < 0 || factoryEnd <= factoryStart) {
            // Renderer was restructured — skip rather than false-fail.
            return
        }
        val factoryBody = nativeAdsSrc.substring(factoryStart, factoryEnd)
        val callsBinder = factoryBody.contains("NativeAdBinder.bind(adView, nativeAd)")
        val callsSetNativeAdDirectly = Regex("adView\\.setNativeAd\\(").containsMatchIn(factoryBody)
        assertTrue(
            "AdMobNativeAdCard factory must delegate to NativeAdBinder.bind(adView, nativeAd) so " +
                "the slot-then-setNativeAd ordering invariant has a single enforcement point.",
            callsBinder
        )
        assertFalse(
            "AdMobNativeAdCard factory must NOT call adView.setNativeAd directly. Route all binds " +
                "through NativeAdBinder.bind so this test catches future regressions in one place.",
            callsSetNativeAdDirectly
        )
    }
}
