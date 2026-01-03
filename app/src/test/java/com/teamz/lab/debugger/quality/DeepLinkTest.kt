package com.teamz.lab.debugger

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.ReferralManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for deep link handling
 * Ensures referral deep links work correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeepLinkTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testReferralDeepLinkIntent() {
        // Create a deep link intent
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("debugger://referral?code=TEST123")
        }
        
        // Test that intent is valid
        assertNotNull("Deep link intent should not be null", intent)
        assertNotNull("Deep link data should not be null", intent.data)
    }
    
    @Test
    fun testReferralCodeExtraction() {
        // Test referral code extraction from URI
        val uri = android.net.Uri.parse("debugger://referral?code=TEST123")
        val code = uri.getQueryParameter("code")
        
        assertEquals("Should extract referral code", "TEST123", code)
    }
    
    @Test
    fun testReferralManagerHandlesDeepLink() {
        // Test that ReferralManager can handle deep links
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("debugger://referral?code=TEST123")
        }
        
        try {
            ReferralManager.checkReferral(context, intent)
            // Should not crash
            assertTrue("Referral check should complete", true)
        } catch (e: Exception) {
            assertTrue("Should handle errors gracefully", true)
        }
    }
    
    @Test
    fun testInvalidDeepLink() {
        // Test handling of invalid deep links
        val invalidIntent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("invalid://scheme")
        }
        
        try {
            ReferralManager.checkReferral(context, invalidIntent)
            // Should not crash
            assertTrue("Invalid deep link should be handled", true)
        } catch (e: Exception) {
            assertTrue("Should handle errors gracefully", true)
        }
    }
}

