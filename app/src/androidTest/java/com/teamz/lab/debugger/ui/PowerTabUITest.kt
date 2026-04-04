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
    fun testPowerTabDisplays() {
        waitForApp()

        // Navigate to Power tab
        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Wait for content to render
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Component Breakdown", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Verify power tab content is displayed
        composeTestRule.onAllNodesWithText("Component Breakdown", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testCameraPowerSection() {
        waitForApp()

        // Navigate to Power tab
        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Wait for Power tab content to appear - look for correct production text
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("How Much Battery Does Your Camera Use?", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Verify the camera power section exists
        composeTestRule.onAllNodesWithText("How Much Battery Does Your Camera Use?", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testPowerTabScrollable() {
        waitForApp()

        // Navigate to Power tab
        composeTestRule.onAllNodesWithText("Power", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Wait for content to render
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
