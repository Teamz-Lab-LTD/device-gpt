package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.ui.theme.ThemeManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Tests for ThemeManager
 * Ensures theme management works correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeManagerTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ThemeManager.initialize(context)
    }
    
    @Test
    fun testInitialize() {
        // Should initialize without crashing
        ThemeManager.initialize(context)
        assertTrue("Initialize should succeed", true)
    }
    
    @Test
    fun testGetCurrentTheme() {
        val theme = ThemeManager.currentTheme
        
        // Should return a theme
        assertNotNull("Current theme should not be null", theme)
    }
    
    @Test
    fun testSetTheme() {
        val themes = listOf(
            com.teamz.lab.debugger.ui.theme.AppTheme.DESIGN_SYSTEM_LIGHT,
            com.teamz.lab.debugger.ui.theme.AppTheme.DESIGN_SYSTEM_DARK
        )
        
        themes.forEach { theme ->
            ThemeManager.setTheme(theme, context)
            val currentTheme = ThemeManager.currentTheme
            assertEquals("Theme should be set correctly", theme, currentTheme)
        }
    }
    
    @Test
    fun testSetDarkMode() {
        ThemeManager.setDarkMode(true, context)
        assertTrue("Dark mode should be set", ThemeManager.isDarkMode)
        
        ThemeManager.setDarkMode(false, context)
        assertFalse("Dark mode should be unset", ThemeManager.isDarkMode)
    }
    
    @Test
    fun testGetEffectiveTheme() {
        val effectiveTheme = ThemeManager.getEffectiveTheme()
        
        // Should return effective theme
        assertNotNull("Effective theme should not be null", effectiveTheme)
    }
}

