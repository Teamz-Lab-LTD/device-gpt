package com.teamz.lab.debugger.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive tests for ReferralManager reward system:
 * - Reward tier calculation
 * - Ad-free granting & expiry
 * - Install Referrer URL parsing
 * - Referral link format
 * - Self-referral prevention
 * - Cached ad-free flag
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReferralRewardTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear referral prefs before each test
        context.getSharedPreferences("referral_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── Reward Tier Calculation ─────────────────────────────────────

    @Test
    fun `tier NONE for 0 referrals`() {
        assertEquals(ReferralManager.RewardTier.NONE, ReferralManager.RewardTier.forCount(0))
    }

    @Test
    fun `tier BRONZE for 1 referral`() {
        assertEquals(ReferralManager.RewardTier.BRONZE, ReferralManager.RewardTier.forCount(1))
    }

    @Test
    fun `tier BRONZE for 2 referrals`() {
        assertEquals(ReferralManager.RewardTier.BRONZE, ReferralManager.RewardTier.forCount(2))
    }

    @Test
    fun `tier SILVER for 3 referrals`() {
        assertEquals(ReferralManager.RewardTier.SILVER, ReferralManager.RewardTier.forCount(3))
    }

    @Test
    fun `tier SILVER for 4 referrals`() {
        assertEquals(ReferralManager.RewardTier.SILVER, ReferralManager.RewardTier.forCount(4))
    }

    @Test
    fun `tier GOLD for 5 referrals`() {
        assertEquals(ReferralManager.RewardTier.GOLD, ReferralManager.RewardTier.forCount(5))
    }

    @Test
    fun `tier GOLD for 9 referrals`() {
        assertEquals(ReferralManager.RewardTier.GOLD, ReferralManager.RewardTier.forCount(9))
    }

    @Test
    fun `tier LEGEND for 10 referrals`() {
        assertEquals(ReferralManager.RewardTier.LEGEND, ReferralManager.RewardTier.forCount(10))
    }

    @Test
    fun `tier LEGEND for 100 referrals`() {
        assertEquals(ReferralManager.RewardTier.LEGEND, ReferralManager.RewardTier.forCount(100))
    }

    // ── Tier Properties ─────────────────────────────────────────────

    @Test
    fun `BRONZE gives 24h ad-free`() {
        assertEquals(24L, ReferralManager.RewardTier.BRONZE.adFreeHours)
    }

    @Test
    fun `SILVER gives 72h ad-free`() {
        assertEquals(72L, ReferralManager.RewardTier.SILVER.adFreeHours)
    }

    @Test
    fun `GOLD gives 168h ad-free (7 days)`() {
        assertEquals(168L, ReferralManager.RewardTier.GOLD.adFreeHours)
    }

    @Test
    fun `LEGEND gives 720h ad-free (30 days)`() {
        assertEquals(720L, ReferralManager.RewardTier.LEGEND.adFreeHours)
    }

    // ── Ad-Free Status ──────────────────────────────────────────────

    @Test
    fun `initially not ad-free`() {
        assertFalse(ReferralManager.isAdFreeFromReferrals(context))
    }

    @Test
    fun `ad-free after granting reward via incrementReferralCount`() {
        // Simulate 1 referral to trigger BRONZE reward
        ReferralManager.incrementReferralCount(context)
        assertTrue(
            "Should be ad-free after 1st referral (BRONZE tier)",
            ReferralManager.isAdFreeFromReferrals(context)
        )
    }

    @Test
    fun `ad-free remaining is positive after reward`() {
        ReferralManager.incrementReferralCount(context)
        val remaining = ReferralManager.getAdFreeRemainingMs(context)
        assertTrue("Ad-free remaining should be > 0", remaining > 0)
        // BRONZE = 24h = 86400000ms — should be close to that
        assertTrue("Should be close to 24h", remaining > 86000000)
    }

    @Test
    fun `ad-free remaining is 0 when no reward`() {
        assertEquals(0L, ReferralManager.getAdFreeRemainingMs(context))
    }

    @Test
    fun `expired ad-free returns false`() {
        // Manually set ad_free_until to the past
        context.getSharedPreferences("referral_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("ad_free_until", System.currentTimeMillis() - 1000)
            .commit()

        assertFalse(ReferralManager.isAdFreeFromReferrals(context))
    }

    // ── Cached Ad-Free Flag ─────────────────────────────────────────

    @Test
    fun `cached ad-free is false initially`() {
        // Call isAdFreeFromReferrals to set the cache
        ReferralManager.isAdFreeFromReferrals(context)
        assertFalse(ReferralManager.isAdFreeFromReferralsCached())
    }

    @Test
    fun `cached ad-free is true after reward`() {
        ReferralManager.incrementReferralCount(context)
        ReferralManager.isAdFreeFromReferrals(context) // updates cache
        assertTrue(ReferralManager.isAdFreeFromReferralsCached())
    }

    // ── Current & Next Tier ─────────────────────────────────────────

    @Test
    fun `getCurrentTier returns NONE for 0 referrals`() {
        assertEquals(ReferralManager.RewardTier.NONE, ReferralManager.getCurrentTier(context))
    }

    @Test
    fun `getNextTier returns BRONZE when at NONE`() {
        assertEquals(ReferralManager.RewardTier.BRONZE, ReferralManager.getNextTier(context))
    }

    @Test
    fun `getReferralsToNextTier returns 1 when at 0`() {
        assertEquals(1, ReferralManager.getReferralsToNextTier(context))
    }

    @Test
    fun `getReferralsToNextTier returns 2 after 1 referral`() {
        ReferralManager.incrementReferralCount(context)
        // At BRONZE (1), next is SILVER (3), so need 2 more
        assertEquals(2, ReferralManager.getReferralsToNextTier(context))
    }

    @Test
    fun `getNextTier returns null when at LEGEND`() {
        // Set count to 10
        val prefs = context.getSharedPreferences("referral_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("referral_count", 10).commit()
        assertNull(ReferralManager.getNextTier(context))
    }

    @Test
    fun `getReferralsToNextTier returns 0 when at max tier`() {
        val prefs = context.getSharedPreferences("referral_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("referral_count", 10).commit()
        assertEquals(0, ReferralManager.getReferralsToNextTier(context))
    }

    // ── Referral Code ───────────────────────────────────────────────

    @Test
    fun `referral code is stable`() {
        val code1 = ReferralManager.getOrCreateReferralCode(context)
        val code2 = ReferralManager.getOrCreateReferralCode(context)
        assertEquals("Code should be the same on repeat call", code1, code2)
    }

    @Test
    fun `referral code has correct format`() {
        val code = ReferralManager.getOrCreateReferralCode(context)
        assertTrue("Should start with USER", code.startsWith("USER"))
        assertEquals("Should be 10 chars (USER + 6 digits)", 10, code.length)
    }

    // ── Referral Link Format ────────────────────────────────────────

    @Test
    fun `referral link uses proper referrer param`() {
        val link = ReferralManager.getReferralLink(context)
        assertTrue("Should contain referrer param", link.contains("referrer="))
        assertTrue("Should contain utm_source=referral", link.contains("utm_source"))
        assertTrue("Should contain utm_campaign", link.contains("utm_campaign"))
        assertFalse("Should NOT use old ?ref= format", link.contains("&ref=USER"))
    }

    @Test
    fun `short referral link equals full referral link`() {
        val full = ReferralManager.getReferralLink(context)
        val short = ReferralManager.getShortReferralLink(context)
        assertEquals("Short link should be same as full link now", full, short)
    }

    // ── Self-Referral Prevention ────────────────────────────────────

    @Test
    fun `self-referral is prevented`() {
        val myCode = ReferralManager.getOrCreateReferralCode(context)

        // Try to refer yourself
        val intent = Intent().apply {
            data = Uri.parse("debugger://referral?ref=$myCode")
        }
        ReferralManager.checkReferral(context, intent)

        assertFalse("Should not be marked as referred", ReferralManager.wasReferred(context))
    }

    @Test
    fun `valid referral from another user works`() {
        // First create our own code (so it's different from OTHER123)
        ReferralManager.getOrCreateReferralCode(context)

        val intent = Intent().apply {
            data = Uri.parse("debugger://referral?ref=OTHER123")
        }
        ReferralManager.checkReferral(context, intent)

        assertTrue("Should be marked as referred", ReferralManager.wasReferred(context))
        assertEquals("OTHER123", ReferralManager.getReferredByCode(context))
    }

    @Test
    fun `referral is only counted once`() {
        ReferralManager.getOrCreateReferralCode(context)

        val intent1 = Intent().apply {
            data = Uri.parse("debugger://referral?ref=FIRST999")
        }
        ReferralManager.checkReferral(context, intent1)

        // Second referral attempt from different user
        val intent2 = Intent().apply {
            data = Uri.parse("debugger://referral?ref=SECOND88")
        }
        ReferralManager.checkReferral(context, intent2)

        // Should still show first referrer
        assertEquals("FIRST999", ReferralManager.getReferredByCode(context))
    }

    // ── Null / Empty Handling ───────────────────────────────────────

    @Test
    fun `checkReferral handles null intent`() {
        ReferralManager.checkReferral(context, null)
        assertFalse(ReferralManager.wasReferred(context))
    }

    @Test
    fun `checkReferral handles empty intent`() {
        ReferralManager.checkReferral(context, Intent())
        assertFalse(ReferralManager.wasReferred(context))
    }

    @Test
    fun `checkReferral handles intent with no ref param`() {
        val intent = Intent().apply {
            data = Uri.parse("debugger://referral")
        }
        ReferralManager.checkReferral(context, intent)
        assertFalse(ReferralManager.wasReferred(context))
    }

    // ── Reward Tier Upgrade ─────────────────────────────────────────

    @Test
    fun `upgrading tiers extends ad-free time`() {
        // Get to BRONZE (1 referral)
        ReferralManager.incrementReferralCount(context)
        val bronzeRemaining = ReferralManager.getAdFreeRemainingMs(context)

        // Get to SILVER (3 referrals) — need 2 more
        ReferralManager.incrementReferralCount(context)
        ReferralManager.incrementReferralCount(context)
        val silverRemaining = ReferralManager.getAdFreeRemainingMs(context)

        assertTrue(
            "SILVER (72h) should give more time than BRONZE (24h)",
            silverRemaining > bronzeRemaining
        )
    }

    // ── Referral Count ──────────────────────────────────────────────

    @Test
    fun `incrementReferralCount increments correctly`() {
        assertEquals(0, ReferralManager.getReferralCount(context))
        ReferralManager.incrementReferralCount(context)
        assertEquals(1, ReferralManager.getReferralCount(context))
        ReferralManager.incrementReferralCount(context)
        assertEquals(2, ReferralManager.getReferralCount(context))
    }
}
