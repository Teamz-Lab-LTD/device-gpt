package com.teamz.lab.debugger.features

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teamz.lab.debugger.utils.ReferralManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests that run on a REAL device to verify the referral reward system.
 * These tests call the actual ReferralManager code on the device.
 */
@RunWith(AndroidJUnit4::class)
class ReferralRewardDeviceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear referral prefs before each test
        context.getSharedPreferences("referral_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun referralCodeIsGenerated() {
        val code = ReferralManager.getOrCreateReferralCode(context)
        assertNotNull("Code should not be null", code)
        assertTrue("Code should start with USER", code.startsWith("USER"))
        assertEquals("Code should be 10 chars", 10, code.length)

        // Second call returns same code
        val code2 = ReferralManager.getOrCreateReferralCode(context)
        assertEquals("Code should be stable", code, code2)
    }

    @Test
    fun initialStateIsCorrect() {
        assertEquals("Count should be 0", 0, ReferralManager.getReferralCount(context))
        assertFalse("Should not be ad-free", ReferralManager.isAdFreeFromReferrals(context))
        assertEquals("Remaining should be 0", 0L, ReferralManager.getAdFreeRemainingMs(context))
        assertEquals("Tier should be NONE", ReferralManager.RewardTier.NONE, ReferralManager.getCurrentTier(context))
        assertEquals("Next tier should be BRONZE", ReferralManager.RewardTier.BRONZE, ReferralManager.getNextTier(context))
        assertEquals("Referrals to next should be 1", 1, ReferralManager.getReferralsToNextTier(context))
    }

    @Test
    fun firstReferralGrantsBronzeReward() {
        // Simulate 1st referral
        ReferralManager.incrementReferralCount(context)

        assertEquals("Count should be 1", 1, ReferralManager.getReferralCount(context))
        assertEquals("Tier should be BRONZE", ReferralManager.RewardTier.BRONZE, ReferralManager.getCurrentTier(context))
        assertTrue("Should be ad-free", ReferralManager.isAdFreeFromReferrals(context))
        assertTrue("Cache should be true", ReferralManager.isAdFreeFromReferralsCached())

        val remaining = ReferralManager.getAdFreeRemainingMs(context)
        assertTrue("Should have >23h remaining", remaining > 23 * 3600 * 1000L)
        assertTrue("Should have <25h remaining", remaining < 25 * 3600 * 1000L)
    }

    @Test
    fun threeReferralsGrantsSilverReward() {
        repeat(3) { ReferralManager.incrementReferralCount(context) }

        assertEquals("Count should be 3", 3, ReferralManager.getReferralCount(context))
        assertEquals("Tier should be SILVER", ReferralManager.RewardTier.SILVER, ReferralManager.getCurrentTier(context))
        assertTrue("Should be ad-free", ReferralManager.isAdFreeFromReferrals(context))

        val remaining = ReferralManager.getAdFreeRemainingMs(context)
        // SILVER = 72h
        assertTrue("Should have >71h remaining", remaining > 71 * 3600 * 1000L)
        assertTrue("Should have <73h remaining", remaining < 73 * 3600 * 1000L)
    }

    @Test
    fun fiveReferralsGrantsGoldReward() {
        repeat(5) { ReferralManager.incrementReferralCount(context) }

        assertEquals("Count should be 5", 5, ReferralManager.getReferralCount(context))
        assertEquals("Tier should be GOLD", ReferralManager.RewardTier.GOLD, ReferralManager.getCurrentTier(context))
        assertTrue("Should be ad-free", ReferralManager.isAdFreeFromReferrals(context))

        val remaining = ReferralManager.getAdFreeRemainingMs(context)
        // GOLD = 168h (7 days)
        assertTrue("Should have >167h remaining", remaining > 167 * 3600 * 1000L)
    }

    @Test
    fun tenReferralsGrantsLegendReward() {
        repeat(10) { ReferralManager.incrementReferralCount(context) }

        assertEquals("Count should be 10", 10, ReferralManager.getReferralCount(context))
        assertEquals("Tier should be LEGEND", ReferralManager.RewardTier.LEGEND, ReferralManager.getCurrentTier(context))
        assertTrue("Should be ad-free", ReferralManager.isAdFreeFromReferrals(context))
        assertNull("Next tier should be null (max reached)", ReferralManager.getNextTier(context))
        assertEquals("Referrals to next should be 0", 0, ReferralManager.getReferralsToNextTier(context))

        val remaining = ReferralManager.getAdFreeRemainingMs(context)
        // LEGEND = 720h (30 days)
        assertTrue("Should have >719h remaining", remaining > 719 * 3600 * 1000L)
    }

    @Test
    fun deepLinkReferralIsRecorded() {
        // First create our own code
        ReferralManager.getOrCreateReferralCode(context)

        // Simulate deep link from friend
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("debugger://referral?ref=FRIEND999")
        }
        ReferralManager.checkReferral(context, intent)

        assertTrue("Should be referred", ReferralManager.wasReferred(context))
        assertEquals("Referred by FRIEND999", "FRIEND999", ReferralManager.getReferredByCode(context))
    }

    @Test
    fun selfReferralIsPrevented() {
        val myCode = ReferralManager.getOrCreateReferralCode(context)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("debugger://referral?ref=$myCode")
        }
        ReferralManager.checkReferral(context, intent)

        assertFalse("Should NOT be referred by self", ReferralManager.wasReferred(context))
    }

    @Test
    fun referralLinkHasCorrectFormat() {
        val link = ReferralManager.getReferralLink(context)
        assertTrue("Should contain play.google.com", link.contains("play.google.com"))
        assertTrue("Should contain referrer param", link.contains("referrer="))
        assertTrue("Should contain utm_source=referral", link.contains("utm_source"))
        assertTrue("Should contain utm_campaign", link.contains("utm_campaign"))
        assertFalse("Should NOT use &ref= (old broken format)", link.contains("&ref=USER"))
    }

    @Test
    fun expiredAdFreeReturnsFalse() {
        // Manually set expired timestamp
        context.getSharedPreferences("referral_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("ad_free_until", System.currentTimeMillis() - 1000)
            .commit()

        assertFalse("Expired ad-free should return false", ReferralManager.isAdFreeFromReferrals(context))
        assertFalse("Cache should also be false", ReferralManager.isAdFreeFromReferralsCached())
    }

    @Test
    fun tierUpgradeExtendsAdFreeTime() {
        // BRONZE
        ReferralManager.incrementReferralCount(context)
        val bronzeRemaining = ReferralManager.getAdFreeRemainingMs(context)

        // SILVER (2 more = 3 total)
        ReferralManager.incrementReferralCount(context)
        ReferralManager.incrementReferralCount(context)
        val silverRemaining = ReferralManager.getAdFreeRemainingMs(context)

        assertTrue(
            "SILVER (72h) should give more time than BRONZE (24h). Bronze=$bronzeRemaining, Silver=$silverRemaining",
            silverRemaining > bronzeRemaining
        )
    }
}
