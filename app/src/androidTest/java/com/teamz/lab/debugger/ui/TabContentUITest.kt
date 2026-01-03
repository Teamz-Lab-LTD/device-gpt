package com.teamz.lab.debugger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for tab content
 * Tests that each tab displays its content correctly
 */
@RunWith(AndroidJUnit4::class)
class TabContentUITest {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Test
    fun testDeviceInfoTabContent() {
        // Wait for app to load
        composeTestRule.waitForIdle()
        
        // Navigate to Device Info tab
        composeTestRule.onAllNodesWithText("Device Info", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()
        
        // Wait for content to load
        composeTestRule.waitForIdle()
        Thread.sleep(2000) // Give time for async data loading
        
        // Verify Device Info tab actually shows device-specific content
        // Look for unique content that only appears in Device Info tab
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                // Device Info tab should show "Device Specifications" or "Processor" or "Battery"
                composeTestRule.onNodeWithText("Device Specifications", substring = true, ignoreCase = true)
                    .assertExists()
                true
            } catch (e: Exception) {
                try {
                    // Fallback: Check for "Processor" or "Battery"
                    composeTestRule.onNodeWithText("Processor", substring = true, ignoreCase = true)
                        .assertExists()
                    true
                } catch (e2: Exception) {
                    try {
                        composeTestRule.onNodeWithText("Battery", substring = true, ignoreCase = true)
                            .assertExists()
                        true
                    } catch (e3: Exception) {
                        false
                    }
                }
            }
        }
        
        // Verify content is actually displayed (not just loading)
        composeTestRule.onNodeWithText("Device Specifications", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }
    
    @Test
    fun testNetworkInfoTabContent() {
        // Wait for app to load
        composeTestRule.waitForIdle()
        
        // Navigate to Network Info tab
        composeTestRule.onAllNodesWithText("Network Info", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()
        
        composeTestRule.waitForIdle()
        Thread.sleep(2000) // Give time for async data loading
        
        // Verify Network Info tab actually shows network-specific content
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                // Network Info tab should show "Network Usage" or "ISP Details" or "Download Speed"
                composeTestRule.onNodeWithText("Network Usage", substring = true, ignoreCase = true)
                    .assertExists()
                true
            } catch (e: Exception) {
                try {
                    composeTestRule.onNodeWithText("ISP Details", substring = true, ignoreCase = true)
                        .assertExists()
                    true
                } catch (e2: Exception) {
                    try {
                        composeTestRule.onNodeWithText("Download Speed", substring = true, ignoreCase = true)
                            .assertExists()
                        true
                    } catch (e3: Exception) {
                        false
                    }
                }
            }
        }
        
        // Verify content is actually displayed
        composeTestRule.onNodeWithText("Network Usage", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }
    
    @Test
    fun testHealthTabContent() {
        // Wait for app to load
        composeTestRule.waitForIdle()
        
        // Navigate to Health tab
        composeTestRule.onAllNodesWithText("Health", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()
        
        composeTestRule.waitForIdle()
        Thread.sleep(2000) // Give time for async data loading
        
        // Verify Health tab actually shows health-specific content
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                // Health tab should show "Health Score" or "Improvement Suggestions" or "Health History"
                composeTestRule.onNodeWithText("Health Score", substring = true, ignoreCase = true)
                    .assertExists()
                true
            } catch (e: Exception) {
                try {
                    composeTestRule.onNodeWithText("Improvement Suggestions", substring = true, ignoreCase = true)
                        .assertExists()
                    true
                } catch (e2: Exception) {
                    try {
                        composeTestRule.onNodeWithText("Health History", substring = true, ignoreCase = true)
                            .assertExists()
                        true
                    } catch (e3: Exception) {
                        false
                    }
                }
            }
        }
        
        // Verify content is actually displayed
        composeTestRule.onNodeWithText("Health Score", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }
    
    @Test
    fun testPowerTabContent() {
        // Wait for app to load
        composeTestRule.waitForIdle()
        
        // Navigate to Power tab
        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()
        
        composeTestRule.waitForIdle()
        Thread.sleep(2000) // Give time for async data loading
        
        // Verify Power tab actually shows power-specific content
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                // Power tab should show "Component Breakdown" or "Camera Power Test" or "Screen Power Calibrator"
                composeTestRule.onNodeWithText("Component Breakdown", substring = true, ignoreCase = true)
                    .assertExists()
                true
            } catch (e: Exception) {
                try {
                    composeTestRule.onNodeWithText("Camera Power Test", substring = true, ignoreCase = true)
                        .assertExists()
                    true
                } catch (e2: Exception) {
                    try {
                        composeTestRule.onNodeWithText("Screen Power Calibrator", substring = true, ignoreCase = true)
                            .assertExists()
                        true
                    } catch (e3: Exception) {
                        false
                    }
                }
            }
        }
        
        // Verify content is actually displayed
        composeTestRule.onNodeWithText("Component Breakdown", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }
}

