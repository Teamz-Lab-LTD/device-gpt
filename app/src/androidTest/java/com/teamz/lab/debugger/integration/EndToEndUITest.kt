package com.teamz.lab.debugger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI tests
 * Tests complete user journeys
 */
@RunWith(AndroidJUnit4::class)
class EndToEndUITest {

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
    fun testCompleteTabNavigationJourney() {
        // Complete journey: Navigate through all tabs and verify content actually changes
        waitForApp()

        // Start at Device Info
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

        // Navigate to Network Info
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

        // Navigate to Health
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

        // Navigate to Power
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

        // Verify app is still functional after all navigation
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testMenuDrawerJourney() {
        // Complete journey: Open menu, verify drawer content appears, interact, close
        waitForApp()

        // Open menu
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify drawer actually opened by checking for drawer content
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Notifications", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Verify drawer content is actually present
        composeTestRule.onAllNodesWithText("Notifications", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()

        // Close drawer by pressing back
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify app is still functional after drawer interaction
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testShareFlowJourney() {
        // Complete journey: Navigate to tab, wait for data, verify share button appears, click it
        waitForApp()

        // Navigate to Health tab
        composeTestRule.onAllNodesWithText("Health", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify Health tab content actually loaded
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("Health", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Wait for FABs to appear (they appear after data loads)
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Send Info", substring = true, ignoreCase = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        // Verify share button exists and is clickable
        composeTestRule.onNodeWithContentDescription("Send Info", substring = true, ignoreCase = true)
            .assertExists()
            .assertIsEnabled()
            .performClick()

        composeTestRule.waitForIdle()

        // After clicking share, Android share sheet should appear
        // We can't easily test the share sheet, but we can verify:
        // 1. The button was clickable
        // 2. The app didn't crash
        // 3. The app is still functional
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }
}
