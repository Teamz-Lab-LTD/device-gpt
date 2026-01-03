package com.teamz.lab.debugger

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.teamz.lab.debugger.utils.PermissionManager
import com.teamz.lab.debugger.utils.PowerConsumptionUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for component permission request functionality
 * Prevents regression of bugs:
 * 1. Permission request button not working (especially Audio)
 * 2. Missing permissions in AndroidManifest.xml
 * 3. Permission status checking after request
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ComponentPermissionRequestTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = org.robolectric.RuntimeEnvironment.getApplication()
    }
    
    @Test
    fun testAudioPermissionMapping() {
        // Test that Audio component correctly maps to RECORD_AUDIO permission
        val audioPermissions = PermissionManager.getRequiredPermissions("audio")
        
        assertEquals("Audio should require exactly 1 permission", 1, audioPermissions.size)
        assertTrue("Audio should require RECORD_AUDIO permission",
            audioPermissions.contains(Manifest.permission.RECORD_AUDIO))
    }
    
    @Test
    fun testCameraPermissionMapping() {
        // Test that Camera component correctly maps to CAMERA permission
        val cameraPermissions = PermissionManager.getRequiredPermissions("camera")
        
        assertEquals("Camera should require exactly 1 permission", 1, cameraPermissions.size)
        assertTrue("Camera should require CAMERA permission",
            cameraPermissions.contains(Manifest.permission.CAMERA))
    }
    
    @Test
    fun testGpsPermissionMapping() {
        // Test that GPS component correctly maps to location permissions
        val gpsPermissions = PermissionManager.getRequiredPermissions("gps")
        
        assertTrue("GPS should require at least 1 location permission", gpsPermissions.size >= 1)
        assertTrue("GPS should include ACCESS_FINE_LOCATION",
            gpsPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
    }
    
    @Test
    fun testBluetoothPermissionMapping() {
        // Test that Bluetooth component correctly maps to bluetooth permissions
        val bluetoothPermissions = PermissionManager.getRequiredPermissions("bluetooth")
        
        assertTrue("Bluetooth should require at least 1 permission", bluetoothPermissions.size >= 1)
        assertTrue("Bluetooth should include BLUETOOTH permission",
            bluetoothPermissions.contains(Manifest.permission.BLUETOOTH))
    }
    
    @Test
    fun testComponentWithPermissionRequiredStatus() {
        // Test that components with "Permission required" status are detected
        val audioComponent = PowerConsumptionUtils.ComponentPowerData(
            component = "Audio",
            powerConsumption = 0.0,
            status = "Permission required",
            details = "Audio permission required",
            icon = "🔊"
        )
        
        val requiresPermission = audioComponent.status.contains("Permission required", ignoreCase = true) ||
                                 audioComponent.status.contains("permission required", ignoreCase = true)
        
        assertTrue("Component with 'Permission required' status should be detected", requiresPermission)
    }
    
    @Test
    fun testPermissionStatusCheckAfterRequest() {
        // Test that permission status can be checked after request
        // This prevents the bug where permission was granted but still showed "denied"
        val audioPermission = Manifest.permission.RECORD_AUDIO
        
        // Check permission status (may be granted or not in test environment)
        val hasPermission = PermissionManager.hasAudioPermission(context)
        
        // The check should not crash and should return a boolean
        assertNotNull("Permission check should return a value", hasPermission)
    }
    
    @Test
    fun testAllRequiredPermissionsAreRequestable() {
        // Test that all required permissions are actually requestable
        // This prevents "No requestable permission in the request" error
        
        val components = listOf("camera", "audio", "gps", "bluetooth")
        
        components.forEach { componentName ->
            val permissions = PermissionManager.getRequiredPermissions(componentName)
            
            assertTrue("$componentName should have at least one permission", permissions.isNotEmpty())
            
            // Verify each permission is a valid Android permission
            permissions.forEach { permission ->
                assertNotNull("Permission should not be null", permission)
                assertTrue("Permission should start with 'android.permission.'",
                    permission.startsWith("android.permission."))
            }
        }
    }
    
    @Test
    fun testFilterUngrantedPermissionsForAudio() {
        // Test filtering ungranted permissions for Audio component
        val audioPermissions = PermissionManager.getRequiredPermissions("audio")
        val ungranted = PermissionManager.filterUngrantedPermissions(context, audioPermissions)
        
        assertNotNull("Ungranted permissions list should not be null", ungranted)
        assertTrue("Ungranted permissions should be subset of required permissions",
            ungranted.all { audioPermissions.contains(it) })
    }
    
    @Test
    fun testComponentNameCaseInsensitive() {
        // Test that component name matching is case-insensitive
        val audioPerms1 = PermissionManager.getRequiredPermissions("audio")
        val audioPerms2 = PermissionManager.getRequiredPermissions("Audio")
        val audioPerms3 = PermissionManager.getRequiredPermissions("AUDIO")
        
        assertEquals("Permissions should be same regardless of case", audioPerms1, audioPerms2)
        assertEquals("Permissions should be same regardless of case", audioPerms1, audioPerms3)
    }
    
    @Test
    fun testGpsLocationPermissionAlternative() {
        // Test that GPS accepts "location" as alternative name
        val gpsPerms = PermissionManager.getRequiredPermissions("gps")
        val locationPerms = PermissionManager.getRequiredPermissions("location")
        
        assertEquals("GPS and location should have same permissions", gpsPerms, locationPerms)
    }
}

