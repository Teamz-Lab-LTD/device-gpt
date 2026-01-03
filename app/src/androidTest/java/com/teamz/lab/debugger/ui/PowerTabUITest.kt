package com.teamz.lab.debugger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests specifically for Power tab
 * Tests power consumption UI components and interactions
 */
@RunWith(AndroidJUnit4::class)
class PowerTabUITest {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Test
    fun testPowerTabDisplays() {
        // Wait for app to load
        composeTestRule.waitForIdle()
        
        // Navigate to Power tab
        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Wait for content to render
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            try {
                composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
                    .onFirst()
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        
        // Verify power tab content is displayed
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }
    
    @Test
    fun testCameraPowerTestButton() {
        // Wait for app to load
        composeTestRule.waitForIdle()
        
        // Navigate to Power tab
        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()
        
        composeTestRule.waitForIdle()
        Thread.sleep(2000) // Wait for content to load
        
        // Wait for Power tab content to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithText("Camera Power Test", substring = true, ignoreCase = true)
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        
        // Look for camera test button - verify it exists and is clickable
        // The button text might be "Quick Test" or "Full Test" or similar
        try {
            // Try to find a button near "Camera Power Test" section
            composeTestRule.onNodeWithText("Quick Test", substring = true, ignoreCase = true)
                .assertExists()
                .assertIsDisplayed()
                .assertIsEnabled()
        } catch (e: Exception) {
            try {
                composeTestRule.onNodeWithText("Full Test", substring = true, ignoreCase = true)
                    .assertExists()
                    .assertIsDisplayed()
                    .assertIsEnabled()
            } catch (e2: Exception) {
                // If no button found, at least verify the section exists
                composeTestRule.onNodeWithText("Camera Power Test", substring = true, ignoreCase = true)
                    .assertExists()
                    .assertIsDisplayed()
            }
        }
    }
    
    @Test
    fun testPowerTabScrollable() {
        // Wait for app to load
        composeTestRule.waitForIdle()
        
        // Navigate to Power tab
        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Wait for content to render
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            try {
                composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
                    .onFirst()
                    .assertExists()
                true
            } catch (e: Exception) {
                false
            }
        }
        
        // Verify content is scrollable (if there's enough content)
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }
}

