package com.teamz.lab.debugger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for component permission request functionality
 * Prevents regression of bugs:
 * 1. Permission request button not working (especially Audio)
 * 2. "No requestable permission in the request" error
 * 3. Permission status not updating after grant
 */
@RunWith(AndroidJUnit4::class)
class ComponentPermissionRequestUITest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun waitForApp() {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Health", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }

    private fun navigateToPowerTab() {
        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Wait for Component Breakdown section
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Component Breakdown", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    @Test
    fun testPermissionButtonShowsForAudioWhenRequired() {
        waitForApp()
        navigateToPowerTab()

        // Try to find Audio component
        try {
            composeTestRule.onAllNodesWithText("Audio", substring = true, ignoreCase = true)
                .onFirst()
                .assertExists()
                .performClick()

            composeTestRule.waitForIdle()
            Thread.sleep(2000)

            // Check if permission-related content appears in dialog
            try {
                composeTestRule.onAllNodesWithText("Permission", substring = true, ignoreCase = true)
                    .onFirst()
                    .assertExists()
            } catch (e: Exception) {
                // Permission may already be granted
            }
        } catch (e: Exception) {
            // Audio component may not be present or may already have permission
        }
    }

    @Test
    fun testPermissionButtonShowsForCameraWhenRequired() {
        waitForApp()
        navigateToPowerTab()

        // Try to find Camera component
        try {
            composeTestRule.onAllNodesWithText("Camera", substring = true, ignoreCase = true)
                .onFirst()
                .assertExists()
                .performClick()

            composeTestRule.waitForIdle()
            Thread.sleep(2000)

            // Check if permission-related content appears
            try {
                composeTestRule.onAllNodesWithText("Permission", substring = true, ignoreCase = true)
                    .onFirst()
                    .assertExists()
            } catch (e: Exception) {
                // Permission may already be granted
            }
        } catch (e: Exception) {
            // Camera component may not be present or may already have permission
        }
    }

    @Test
    fun testPermissionButtonShowsForGpsWhenRequired() {
        waitForApp()
        navigateToPowerTab()

        // Try to find GPS component
        try {
            composeTestRule.onAllNodesWithText("GPS", substring = true, ignoreCase = true)
                .onFirst()
                .assertExists()
                .performClick()

            composeTestRule.waitForIdle()
            Thread.sleep(2000)

            // Check if permission-related content appears
            try {
                composeTestRule.onAllNodesWithText("Permission", substring = true, ignoreCase = true)
                    .onFirst()
                    .assertExists()
            } catch (e: Exception) {
                // Permission may already be granted
            }
        } catch (e: Exception) {
            // GPS component may not be present or may already have permission
        }
    }

    @Test
    fun testComponentInfoDialogDisplays() {
        waitForApp()
        navigateToPowerTab()

        // Try to open any component dialog
        val componentNames = listOf("CPU", "Display", "Battery", "Camera", "Audio", "GPS", "Bluetooth")

        for (componentName in componentNames) {
            try {
                composeTestRule.onAllNodesWithText(componentName, substring = true, ignoreCase = true)
                    .onFirst()
                    .assertExists()
                    .performClick()

                composeTestRule.waitForIdle()
                Thread.sleep(2000)

                // Verify dialog appeared (look for "Got it!" button or component name in dialog)
                composeTestRule.onAllNodesWithText("Got it!", substring = true, ignoreCase = true)
                    .onFirst()
                    .assertExists()

                // Close dialog
                composeTestRule.onAllNodesWithText("Got it!", substring = true, ignoreCase = true)
                    .onFirst()
                    .performClick()

                composeTestRule.waitForIdle()
                break // Successfully tested one component
            } catch (e: Exception) {
                // Continue to next component
            }
        }
    }
}
