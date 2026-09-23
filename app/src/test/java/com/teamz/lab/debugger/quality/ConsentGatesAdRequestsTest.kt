package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.UmpConsentManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AdMob raised "Consent requirement: No CMP" against this app on 2026-09-22 — ad requests
 * reaching Google from the EEA/UK/Switzerland with no TC string attached.
 *
 * The cause was not a missing integration. UMP 3.0.0 was wired in and collecting consent
 * correctly; UmpConsentManager.canRequestAds() simply had **no callers**. The only other
 * mention of it anywhere in the app was its own analytics log line. Consent was gathered and
 * then ignored, so every ad format requested regardless of the answer.
 *
 * The gate now lives beside isUserAdFree() and isCountrySuppressed() in RemoteConfigUtils, one
 * choke point for all three formats. These guards exist so it cannot quietly lose its callers
 * again — that is the exact failure being fixed, and it survived months of ad-pipeline work.
 */
class ConsentGatesAdRequestsTest {

    /** Comments stripped: a guard that matches its own explanation proves nothing. */
    private fun source(path: String): String {
        val f = File("src/main/java/com/teamz/lab/debugger/$path")
        assertTrue("source not found: ${f.absolutePath}", f.exists())
        return f.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    }

    @Test
    fun `ads are not permitted until UMP has actually answered`() {
        assertFalse(
            "The cached consent answer must start false. Starting true is the same failure as " +
                "the old `catch { true }`: it lets a cold start request ads before anyone knows " +
                "whether this user is in a consent geo.",
            UmpConsentManager.adsPermittedCached(),
        )
    }

    @Test
    fun `every ad format consults consent`() {
        val rc = source("utils/RemoteConfigUtils.kt")
        for (fn in listOf("shouldShowInterstitialAds", "shouldShowAppOpenAds", "shouldShowNativeAds")) {
            val body = rc.substringAfter("fun $fn(").substringBefore("\n    fun ")
            assertTrue(
                "$fn() does not call consentBlocksAds(). Every format must ask, or the one that " +
                    "forgets is the one that reintroduces the AdMob policy flag.",
                body.contains("consentBlocksAds()"),
            )
        }
    }

    @Test
    fun `the consent check fails closed, not open`() {
        val ump = source("utils/UmpConsentManager.kt")
        val body = ump.substringAfter("fun canRequestAds(").substringBefore("fun ensureConsent")
        assertTrue(
            "canRequestAds() must return false when the SDK throws. It used to return true, " +
                "turning any SDK error into an unconsented ad request.",
            body.contains("false"),
        )
        assertFalse(
            "no path in canRequestAds() may yield true on failure",
            Regex("catch[^}]*\\btrue\\b", RegexOption.DOT_MATCHES_ALL).containsMatchIn(body),
        )
    }

    @Test
    fun `the UMP timeout unblocks the UI without granting consent`() {
        val ump = source("utils/UmpConsentManager.kt")
        val watchdog = ump.substringAfter("postDelayed(").substringBefore("UMP_TIMEOUT_MS)")
        assertFalse(
            "The watchdog exists so a slow network does not freeze the first screen. If it also " +
                "sets adsPermitted, a timeout becomes indistinguishable from consent.",
            watchdog.contains("adsPermitted = true"),
        )
    }

    @Test
    fun `the bundled default and the accessor fallback cannot drift`() {
        // They HAD drifted: the defaults map bundled 60000ms / 7 requests while the accessors
        // fell back to 10000ms / 20. getLong() returns Firebase's static 0 until
        // setDefaultsAsync() lands, so the fallback is the value actually in force on every
        // cold start that loses that race — six times the request rate, on fresh installs.
        val rc = source("utils/RemoteConfigUtils.kt")
        for (name in listOf(
            "DEFAULT_NATIVE_AD_REQUEST_INTERVAL_MS",
            "DEFAULT_NATIVE_AD_MAX_REQUESTS_PER_SESSION",
        )) {
            assertEquals(
                "$name must be declared exactly once and referenced by both the defaults map " +
                    "and the accessor fallback — that is what makes drift impossible.",
                1, Regex("const val $name").findAll(rc).count(),
            )
            assertTrue(
                "$name is declared but never used by the defaults map or the fallback",
                Regex("\\b$name\\b").findAll(rc).count() >= 3,
            )
        }
        assertFalse(
            "the old loose fallbacks must be gone",
            rc.contains("if (value == 0L) 10000L else value") ||
                rc.contains("if (value == 0L) 20 else value.toInt()"),
        )
    }
}
