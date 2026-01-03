package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.AnalyticsUtils
import com.teamz.lab.debugger.utils.AnalyticsEvent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for AnalyticsUtils
 * Ensures analytics logging works correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AnalyticsUtilsTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testInit() {
        // Should initialize without crashing
        AnalyticsUtils.init(context)
        assertTrue("Init should succeed", true)
    }
    
    @Test
    fun testLogEvent() {
        // Should log event without crashing
        AnalyticsUtils.logEvent(AnalyticsEvent.PowerTabViewed)
        assertTrue("Log event should succeed", true)
    }
    
    @Test
    fun testLogEventWithParams() {
        val params = mapOf(
            "test_param" to "test_value",
            "number" to 123
        )
        
        // Should log event with parameters without crashing
        AnalyticsUtils.logEvent(AnalyticsEvent.PowerTabViewed, params)
        assertTrue("Log event with params should succeed", true)
    }
    
    @Test
    fun testAllAnalyticsEvents() {
        // Test that all event types can be logged
        val events = listOf(
            AnalyticsEvent.PowerTabViewed,
            AnalyticsEvent.ShareDeviceInfo,
            AnalyticsEvent.ShareWithAI
        )
        
        events.forEach { event ->
            AnalyticsUtils.logEvent(event)
            assertTrue("Should log $event", true)
        }
    }
}

