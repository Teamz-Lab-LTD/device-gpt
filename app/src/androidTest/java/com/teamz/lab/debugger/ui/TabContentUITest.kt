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

    private fun waitForApp() {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Health", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testDeviceInfoTabContent() {
        waitForApp()

        // Navigate to Device Info tab
        composeTestRule.onAllNodesWithText("Device Info", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        // Wait for content to load
        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify Device Info tab actually shows device-specific content
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Device Specifications", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Verify content exists (use assertExists for potentially off-screen content)
        composeTestRule.onAllNodesWithText("Device Specifications", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testNetworkInfoTabContent() {
        waitForApp()

        // Navigate to Network Info tab
        composeTestRule.onAllNodesWithText("Network Info", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify Network Info tab actually shows network-specific content
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Network Usage", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Verify content exists
        composeTestRule.onAllNodesWithText("Network Usage", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testHealthTabContent() {
        waitForApp()

        // Navigate to Health tab
        composeTestRule.onAllNodesWithText("Health", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify Health tab actually shows health-specific content
        // "Health" text appears in multiple places (tab + content), use onAllNodes
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Health", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .size > 1 // tab label + content
        }

        // Verify Health content exists (use onAllNodes since "Health" appears multiple times)
        composeTestRule.onAllNodesWithText("Health", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testPowerTabContent() {
        waitForApp()

        // Navigate to Power tab
        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify Power tab actually shows power-specific content
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Component Breakdown", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Verify content exists
        composeTestRule.onAllNodesWithText("Component Breakdown", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }
}
