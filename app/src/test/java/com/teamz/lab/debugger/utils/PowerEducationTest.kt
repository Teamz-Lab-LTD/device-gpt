package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.PowerEducation
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for PowerEducation
 * Ensures power education content is available
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PowerEducationTest {

    // The text now comes from string resources, so the lookups need a context.
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testGetEducationForComponent() {
        val education = PowerEducation.getEducationForComponent(context, "CPU")
        
        // Should return education content
        assertNotNull("Education should not be null", education)
        assertTrue("Education should have title", education!!.title.isNotEmpty())
        assertTrue("Education should have content", education.content.isNotEmpty())
    }
    
    @Test
    fun testGetQuickTip() {
        val tip = PowerEducation.getQuickTip(context, "CPU")
        
        // Should return a quick tip
        assertNotNull("Quick tip should not be null", tip)
        assertTrue("Quick tip should not be empty", tip!!.isNotEmpty())
    }
    
    @Test
    fun testGetEducationForAllComponents() {
        val components = listOf("CPU", "Display", "Camera", "Network", "Battery")
        
        components.forEach { component ->
            val education = PowerEducation.getEducationForComponent(context, component)
            assertNotNull("Education for $component should not be null", education)
        }
    }
    
    @Test
    fun testGetBasicsEducation() {
        val education = PowerEducation.getBasicsEducation(context)
        
        assertNotNull("Basics education should not be null", education)
        assertTrue("Should have title", education.title.isNotEmpty())
        assertTrue("Should have content", education.content.isNotEmpty())
    }
}

