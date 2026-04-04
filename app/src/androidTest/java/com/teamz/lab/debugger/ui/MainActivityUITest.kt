package com.teamz.lab.debugger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for MainActivity
 * Tests actual UI components, interactions, and user flows
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

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
    fun testAppLaunches() {
        waitForApp()

        // Verify app launches successfully - check if any UI element exists
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testTopBarExists() {
        waitForApp()

        // Verify top bar is displayed by checking menu button exists
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testMenuButtonExists() {
        waitForApp()

        // Verify menu button is displayed
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testTabsAreDisplayed() {
        waitForApp()

        // Verify tabs are displayed (use first() to handle multiple matches)
        composeTestRule.onAllNodesWithText("Device Info", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()

        composeTestRule.onAllNodesWithText("Network Info", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()

        composeTestRule.onAllNodesWithText("Health", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()

        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testTabSwitching() {
        waitForApp()

        // Test switching between tabs and verify content actually changes
        // Start with Device Info
        composeTestRule.onAllNodesWithText("Device Info", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify Device Info content appears
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Device Specifications", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Switch to Network Info
        composeTestRule.onAllNodesWithText("Network Info", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify Network Info content appears (content should have changed)
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Network Usage", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Switch to Health
        composeTestRule.onAllNodesWithText("Health", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify Health content appears (content should have changed again)
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Health", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Switch to Power
        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify Power content appears (content should have changed again)
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Component Breakdown", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    @Test
    fun testFloatingActionButtonsExist() {
        waitForApp()

        // Wait for FABs to potentially appear
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Send Info", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Verify FABs are present
        // Share button is "Send Info"
        composeTestRule.onNodeWithContentDescription("Send Info", substring = true, ignoreCase = true)
            .assertExists()

        // AI button is "AI Assistant"
        composeTestRule.onNodeWithContentDescription("AI Assistant", substring = true, ignoreCase = true)
            .assertExists()
    }
}
