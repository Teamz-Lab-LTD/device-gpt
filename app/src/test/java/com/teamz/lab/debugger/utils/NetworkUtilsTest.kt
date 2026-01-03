package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.getNetworkType
import com.teamz.lab.debugger.utils.getNetworkDownloadSpeed
import com.teamz.lab.debugger.utils.getNetworkUploadSpeed
import com.teamz.lab.debugger.utils.getCompactLatency
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for NetworkUtils
 * Ensures network information retrieval works correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NetworkUtilsTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testGetNetworkType() {
        val networkType = getNetworkType(context)
        
        // Should return a network type string
        assertNotNull("Network type should not be null", networkType)
        assertTrue("Network type should not be empty", networkType.isNotEmpty())
    }
    
    @Test
    fun testGetNetworkUploadSpeed() {
        val uploadSpeed = getNetworkUploadSpeed()
        
        // Should return upload speed info
        assertNotNull("Upload speed should not be null", uploadSpeed)
        assertTrue("Upload speed should not be empty", uploadSpeed.isNotEmpty())
    }
    
    @Test
    fun testGetLatency() {
        val latency = getCompactLatency()
        
        // Should return latency info (may be empty if ping fails)
        assertNotNull("Latency should not be null", latency)
    }
    
    @Test
    fun testGetNetworkSpeed() {
        val speed = getNetworkDownloadSpeed()
        
        // Should return speed info
        assertNotNull("Network speed should not be null", speed)
        assertTrue("Network speed should not be empty", speed.isNotEmpty())
    }
}

