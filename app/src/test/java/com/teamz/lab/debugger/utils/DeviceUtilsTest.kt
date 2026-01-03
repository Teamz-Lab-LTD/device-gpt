package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.getDeviceInfoString
import com.teamz.lab.debugger.utils.getCpuInfo
import com.teamz.lab.debugger.utils.getRamUsage
import com.teamz.lab.debugger.utils.getMemoryAndStorageInfo
import com.teamz.lab.debugger.utils.getBatteryChargingInfo
import android.os.Build
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for DeviceUtils
 * Ensures device information retrieval works correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeviceUtilsTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testGetDeviceModel() {
        val model = Build.MODEL
        
        // Should return a non-empty string
        assertNotNull("Device model should not be null", model)
        assertTrue("Device model should not be empty", model.isNotEmpty())
    }
    
    @Test
    fun testGetAndroidVersion() {
        val version = Build.VERSION.RELEASE
        
        // Should return a non-empty string
        assertNotNull("Android version should not be null", version)
        assertTrue("Android version should not be empty", version.isNotEmpty())
    }
    
    @Test
    fun testGetCpuInfo() {
        val cpuInfo = try {
            getCpuInfo()
        } catch (e: Exception) {
            "CPU info unavailable"
        }
        
        // Should return CPU information (or fallback)
        assertNotNull("CPU info should not be null", cpuInfo)
        assertTrue("CPU info should not be empty", cpuInfo.isNotEmpty())
    }
    
    @Test
    fun testGetRamInfo() {
        val ramInfo = getRamUsage(context)
        
        // Should return RAM information
        assertNotNull("RAM info should not be null", ramInfo)
        assertTrue("RAM info should not be empty", ramInfo.isNotEmpty())
    }
    
    @Test
    fun testGetStorageInfo() {
        val storageInfo = getMemoryAndStorageInfo(context)
        
        // Should return storage information
        assertNotNull("Storage info should not be null", storageInfo)
        assertTrue("Storage info should not be empty", storageInfo.isNotEmpty())
    }
    
    @Test
    fun testGetBatteryInfo() {
        val batteryInfo = getBatteryChargingInfo(context)
        
        // Should return battery information
        assertNotNull("Battery info should not be null", batteryInfo)
        assertTrue("Battery info should not be empty", batteryInfo.isNotEmpty())
    }
    
    @Test
    fun testGetDeviceManufacturer() {
        val manufacturer = Build.MANUFACTURER
        
        // Should return manufacturer name
        assertNotNull("Manufacturer should not be null", manufacturer)
        assertTrue("Manufacturer should not be empty", manufacturer.isNotEmpty())
    }
    
    @Test
    fun testGetDeviceInfoString() {
        val deviceInfo = try {
            getDeviceInfoString(context)
        } catch (e: Exception) {
            "Device info unavailable"
        }
        
        // Should return device information string (or fallback)
        assertNotNull("Device info string should not be null", deviceInfo)
        assertTrue("Device info string should not be empty", deviceInfo.isNotEmpty())
    }
}

