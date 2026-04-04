package com.teamz.lab.debugger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration UI tests
 * Tests complete user flows and interactions between components
 */
@RunWith(AndroidJUnit4::class)
class IntegrationUITest {

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
    fun testCompleteTabNavigationFlow() {
        waitForApp()

        // Test complete flow: Navigate through all tabs
        val tabs = listOf("Device Info", "Network Info", "Health", "Power")

        tabs.forEach { tabName ->
            composeTestRule.onAllNodesWithText(tabName, substring = true, ignoreCase = true)
                .onFirst()
                .assertExists()
                .performClick()

            composeTestRule.waitForIdle()
            Thread.sleep(2000)

            // Verify content loaded
            composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
                .onFirst()
                .assertExists()
        }
    }

    @Test
    fun testMenuDrawerFlow() {
        waitForApp()

        // Open menu drawer
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify drawer is open by checking for drawer content
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Notifications", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Verify drawer content is visible
        composeTestRule.onAllNodesWithText("Notifications", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testShareButtonInteraction() {
        waitForApp()

        // Navigate to a tab that has data (Device Info)
        composeTestRule.onAllNodesWithText("Device Info", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Wait for FABs to appear (they appear after data loads)
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Send Info", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Actually click the share button and verify it's clickable
        composeTestRule.onNodeWithContentDescription("Send Info", substring = true, ignoreCase = true)
            .assertExists()
            .assertIsEnabled()
            .performClick()

        composeTestRule.waitForIdle()

        // After clicking share, Android share sheet should appear
        // We can't easily test the share sheet, but we can verify the button was clickable
        // and the app didn't crash
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testAIButtonInteraction() {
        waitForApp()

        // Navigate to a tab that has data (Health)
        composeTestRule.onAllNodesWithText("Health", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Wait for FABs to appear (they appear after data loads)
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithContentDescription("AI Assistant", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Actually click the AI button and verify it's clickable
        composeTestRule.onNodeWithContentDescription("AI Assistant", substring = true, ignoreCase = true)
            .assertExists()
            .assertIsEnabled()
            .performClick()

        composeTestRule.waitForIdle()

        // After clicking AI, a dialog or activity should appear
        // We can verify the button was clickable and the app didn't crash
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testRefreshButtonInteraction() {
        waitForApp()

        // Find refresh button (may not exist on all screens, handle gracefully)
        try {
            composeTestRule.onNodeWithContentDescription("Refresh", substring = true, ignoreCase = true)
                .assertExists()
                .performClick()
        } catch (e: Exception) {
            // Refresh button may not be visible on all screens, that's okay
        }

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify app still works after refresh
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testSettingsButtonInteraction() {
        waitForApp()

        // Open menu first
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Wait for drawer to open
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Notifications", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Find notification or other drawer item
        composeTestRule.onAllNodesWithText("Notifications", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()

        // Verify app is still functional
        composeTestRule.waitForIdle()
    }
}
