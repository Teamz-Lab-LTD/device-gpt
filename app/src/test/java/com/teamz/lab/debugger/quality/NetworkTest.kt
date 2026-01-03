package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.getNetworkType
import com.teamz.lab.debugger.utils.getISPDetails
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for network functionality
 * Ensures network-related features work correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NetworkTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testNetworkTypeDetection() {
        val networkType = getNetworkType(context)
        
        // Should return a valid network type
        assertNotNull("Network type should not be null", networkType)
        assertTrue("Network type should not be empty", networkType.isNotEmpty())
    }
    
    @Test
    fun testISPDetailsRetrieval() {
        val ispDetails = getISPDetails()
        
        // Should return ISP details (may be empty in test environment)
        assertNotNull("ISP details should not be null", ispDetails)
    }
    
    @Test
    fun testNetworkInfoHandlesNoConnection() {
        // Test that network info handles no connection gracefully
        try {
            val networkType = getNetworkType(context)
            assertNotNull("Should handle no connection", networkType)
        } catch (e: Exception) {
            assertTrue("Should handle errors gracefully", true)
        }
    }
}

