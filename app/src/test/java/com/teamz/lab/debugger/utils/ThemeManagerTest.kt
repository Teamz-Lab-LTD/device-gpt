package com.teamz.lab.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseAuth
import com.teamz.lab.debugger.ui.theme.ThemeManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.MockedStatic
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for ThemeManager
 * Ensures theme management works correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeManagerTest {

    private lateinit var context: Context
    private lateinit var firebaseAuthStatic: MockedStatic<FirebaseAuth>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // ThemeManager.setTheme/setDarkMode call FirebaseAuth.getInstance() internally (to key
        // a credential-restore save to the current user). Robolectric has no working FirebaseAuth
        // instance (it needs Play Services / native init that Robolectric doesn't provide) —
        // Firebase ships no official Robolectric shadow for Auth, so getInstance() throws or
        // returns null depending on FirebaseApp state. Mock the static call instead: the mocked
        // instance's currentUser defaults to null (standard Mockito behavior), which makes
        // RestoreCredentialManager.scheduleSaveAfterStateChange's own uid lookup also resolve to
        // null and return early — the exact same no-op a real signed-out user would hit.
        val mockAuth = Mockito.mock(FirebaseAuth::class.java)
        firebaseAuthStatic = Mockito.mockStatic(FirebaseAuth::class.java)
        firebaseAuthStatic.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }.thenReturn(mockAuth)
        ThemeManager.initialize(context)
    }

    @After
    fun tearDown() {
        firebaseAuthStatic.close()
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
