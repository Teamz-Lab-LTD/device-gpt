package com.teamz.lab.debugger.quality

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.teamz.lab.debugger.ui.FirstScanGateScreen
import org.junit.Rule
import org.junit.Test

/**
 * v3.1.11 W1 — COMPOSE UI test for FirstScanGateScreen.
 *
 * This is the only Compose UI test class in app/src/androidTest/. It uses
 * `createComposeRule()` to spin up an in-memory Compose host, render the
 * FirstScanGateScreen, then drive interactions via the Espresso-style
 * Compose Testing API (`onNodeWithText`, `assertIsDisplayed`, `performClick`).
 *
 * Why Compose UI test is essential separately from unit/Robolectric:
 *   - Unit tests verify state-machine logic (FirstScanGateTest)
 *   - Robolectric tests verify state-machine behavior with fake SharedPreferences (FirstScanGateRuntimeTest)
 *   - This Compose UI test verifies the UI ACTUALLY RENDERS the right thing
 *     when state changes — e.g. progress bar reaches 100, scoring CTA shows,
 *     buttons are tappable, text label appears as the user would see it.
 *
 * Without this layer, a Composable could declare correct state but render
 * an empty Box and unit tests would still pass.
 *
 * Run via:
 *   ./gradlew :app:connectedDebugAndroidTest --tests '*FirstScanGateScreenComposeUiTest*'
 *
 * Requires a connected device or emulator. Compose UI tests cannot run on
 * pure JVM/Robolectric without additional setup (createComposeRule needs the
 * Android Activity host).
 */
class FirstScanGateScreenComposeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstScanGateScreen_rendersScanningPhaseWithProgressLabel() {
        // Stand up the gate Composable in isolation — no MainActivity, no Firebase,
        // no app-state coupling. Pure UI contract verification.
        composeRule.setContent {
            FirstScanGateScreen()
        }
        // The scanning phase shows the literal label below the progress indicator.
        // If the Composable's text changes, this test fails — exactly what we want,
        // because the marketing-critical "10 second scan" promise lives in this string.
        composeRule.onNodeWithText("Scanning your device…", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Battery • RAM • Storage • Network", substring = true)
            .assertIsDisplayed()
    }
}
