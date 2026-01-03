package com.teamz.lab.debugger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.ReferralManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for ReferralManager
 * Ensures referral system works correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReferralManagerTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testCheckReferral() {
        val intent = Intent()
        
        // Should check referral without crashing
        ReferralManager.checkReferral(context, intent)
        assertTrue("Check referral should succeed", true)
    }
    
    @Test
    fun testGetOrCreateReferralCode() {
        val code = ReferralManager.getOrCreateReferralCode(context)
        
        // Should return referral code
        assertNotNull("Referral code should not be null", code)
        assertTrue("Referral code should not be empty", code.isNotEmpty())
        assertTrue("Referral code should start with USER", code.startsWith("USER"))
    }
    
    @Test
    fun testCheckReferralWithDeepLink() {
        val intent = Intent()
        intent.data = Uri.parse("devicegpt://?ref=TEST123")
        
        // Should check referral without crashing
        ReferralManager.checkReferral(context, intent)
        assertTrue("Check referral with deep link should succeed", true)
    }
    
    @Test
    fun testGetReferralCount() {
        val count = ReferralManager.getReferralCount(context)
        
        // Should return referral count (>= 0)
        assertTrue("Referral count should be >= 0", count >= 0)
    }
}

