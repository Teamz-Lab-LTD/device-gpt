package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PaywallPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 2026-10-04, fresh install on an emulator: within four minutes a new user was shown the
 * paywall, "Not ready to pay? Invite friends", and "Quick — why did you close?" over and over;
 * answering the survey led straight back to the referral screen.
 *
 * 1. An offering that failed to load (offering_fetch_failed / _timeout / no packages / SDK not
 *    configured) reached the same onDismiss as a user closing the paywall, so the user got
 *    "Not ready to pay?" for a price they never saw and a survey about a paywall they never
 *    closed. Production vc48: offering_fetch_failed 14 dismissals, referral_fallback_offered
 *    28 dismissals for 9 new users.
 * 2. The survey followed every journey. New users barely answer it: of 22 day-one survey
 *    events on vc48, 20 were sheet_dismissed / no_response.
 * 3. paywall_delay_enabled shipped false in the bundled defaults, so a fresh install that had
 *    not fetched Remote Config yet got the cold paywall 20 s into session one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaywallLoopTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("paywall_trigger_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun src(path: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return File(dir, path).readText()
    }

    private val ui = "app/src/main/java/com/teamz/lab/debugger/ui/"

    // ---- 1. a paywall that never rendered is not a refusal -------------------------------

    @Test
    fun `every unavailable-offering path calls onUnavailable, not onDismiss`() {
        val s = src(ui + "RevenueCatPaywall.kt")
        assertTrue("RevenueCatPaywall needs an onUnavailable callback", s.contains("onUnavailable: () -> Unit"))
        for (reason in listOf("no_offering_available", "offering_has_no_packages", "offering_fetch_timeout")) {
            assertTrue(reason, s.contains("reportOfferingUnavailable(context, analyticsSource, \"$reason\", onUnavailable)"))
        }
        val onError = s.substring(s.indexOf("override fun onError"), s.indexOf("override fun onError") + 900)
        assertTrue("onError must call onUnavailable()", onError.contains("onUnavailable()"))
        assertFalse("onError must not call onDismiss()", onError.contains("onDismiss()"))
        val notConfigured = s.substring(s.indexOf("RevenueCat not configured"), s.indexOf("RevenueCat not configured") + 200)
        assertTrue(notConfigured.contains("onUnavailable()"))
    }

    @Test
    fun `an unavailable paywall closes the chain without referral screen or survey`() {
        val s = src(ui + "PaywallWithReferralFallback.kt")
        val i = s.indexOf("onUnavailable = {")
        assertTrue("PaywallWithReferralFallback must pass onUnavailable", i >= 0)
        val block = s.substring(i, s.indexOf("}", i))
        assertTrue(block, block.contains("onDismiss()"))
        assertFalse(block, block.contains("showReferralFallback"))
        assertFalse(block, block.contains("showDismissReasonSheet"))
    }

    // ---- 2. the survey runs once per install ---------------------------------------------

    @Test
    fun `dismiss survey is allowed once per install`() {
        assertTrue(PaywallPolicy.dismissSurveyAllowed(context))
        PaywallPolicy.recordDismissSurveyShown(context)
        assertFalse(PaywallPolicy.dismissSurveyAllowed(context))
    }

    @Test
    fun `the chain asks the policy before opening the survey`() {
        val s = src(ui + "PaywallWithReferralFallback.kt")
        val fn = s.substring(s.indexOf("val handleFinalDismiss"), s.indexOf("val handleRcDismiss"))
        val gate = fn.indexOf("PaywallPolicy.dismissSurveyAllowed(")
        val open = fn.indexOf("showDismissReasonSheet = true")
        assertTrue(fn, gate in 0 until open)
        val sheet = s.substring(s.indexOf("private fun PaywallDismissReasonSheet"))
        assertTrue("the sheet must record that it was shown", sheet.contains("PaywallPolicy.recordDismissSurveyShown("))
    }

    // ---- 3. session one is protected before Remote Config arrives ------------------------

    @Test
    fun `bundled default gates the cold paywall`() {
        val rc = src("app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt")
        assertTrue(rc.contains("\"paywall_delay_enabled\" to true"))
    }
}
