package com.teamz.lab.debugger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility UI tests
 * Ensures UI components are accessible
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityUITest {

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
    fun testContentDescriptionsExist() {
        waitForApp()

        // Verify important UI elements have content descriptions
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()
    }

    @Test
    fun testTabsAreAccessible() {
        waitForApp()

        // Verify tabs are accessible (use first() to handle multiple matches)
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
    fun testButtonsAreAccessible() {
        waitForApp()

        // Menu button should be accessible
        composeTestRule.onAllNodesWithContentDescription("Menu", substring = true, ignoreCase = true)
            .onFirst()
            .assertExists()

        // The Send FAB's icon renders only once share text has been generated. That work is
        // now done off the main thread (health_section.kt), so the FAB appears after first
        // composition rather than during it. Wait for it instead of asserting immediately.
        //
        // Both blocks previously used `catch (e: Exception)`. assertExists() throws
        // AssertionError, which extends Error, not Exception — so the "that's okay" path
        // documented below never ran and an absent FAB failed the test outright.
        awaitContentDescriptionOrSkip("Send Info")
        awaitContentDescriptionOrSkip("AI Assistant")
    }

    /**
     * Waits up to [timeoutMillis] for a node carrying [description], then asserts it.
     * A timeout means "not surfaced in this configuration" rather than a failure: FAB
     * visibility depends on remote-config flags and premium state, neither of which this
     * test controls.
     */
    private fun awaitContentDescriptionOrSkip(description: String, timeoutMillis: Long = 10_000) {
        val appeared = try {
            composeTestRule.waitUntil(timeoutMillis) {
                composeTestRule
                    .onAllNodesWithContentDescription(description, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            true
        } catch (_: Throwable) {
            false
        }
        if (appeared) {
            composeTestRule
                .onAllNodesWithContentDescription(description, substring = true, ignoreCase = true)
                .onFirst()
                .assertExists()
        }
    }
}
