package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.UmpConsentManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-10-08. From 3.1.27 (2026-09-24, the UMP consent gate) native ads loaded in almost no
 * session: AdMob native requests fell from 30-60/day to 1-5/day. Cold-start trace on the
 * emulator: the loader's LaunchedEffect ran at 1.8 s, read the consent flag (false — UMP answers
 * at ~3.1 s), skipped the load, and nothing re-ran it. The first ad came from a request the
 * stagger had scheduled 60 s later, or never with a target of 1.
 */
class NativeAdConsentRaceTest {

    private fun src(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, "app/src/main/java/com/teamz/lab/debugger/$rel").readText()
    }

    @Test
    fun `consent is observable, starting closed`() {
        assertEquals(false, UmpConsentManager.adsPermittedFlow.value)
        assertEquals(UmpConsentManager.adsPermittedCached(), UmpConsentManager.adsPermittedFlow.value)
    }

    @Test
    fun `native loader re-runs when consent arrives and waits for it before claiming the pipeline`() {
        val s = src("ui/expandable_info_list.kt")
        assertTrue(s.contains("UmpConsentManager.adsPermittedFlow.collectAsState()"))
        val effect = s.indexOf("LaunchedEffect(Unit, shouldShowAds, consentPermitsAds)")
        assertTrue("initial load must be keyed on consent", effect >= 0)
        val wait = s.indexOf("if (!consentPermitsAds)", effect)
        val markInit = s.indexOf("NativeAdManager.tryMarkInitialized()", effect)
        val pipeline = s.indexOf("NativeAdManager.tryStartLoadPipeline()", effect)
        assertTrue("must return before marking init, or the post-consent run is not a first init", wait in effect until markInit)
        assertTrue("must return before taking the pipeline lock", wait < pipeline)
        assertTrue("refill must also wake on consent",
            s.contains("LaunchedEffect(NativeAdManager.cacheGeneration.intValue, shouldShowAds, consentPermitsAds)"))
    }
}
