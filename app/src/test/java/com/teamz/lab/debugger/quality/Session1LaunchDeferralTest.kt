package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The last two modals that fire before a new user has seen anything.
 *
 * vc43 removed three of the interruptions traced in session 1 and Remote Config
 * handled three more. Two survived, both in MainActivity.onCreate, both
 * unconditional:
 *
 *   T+0.4s  UmpConsentManager.ensureConsent(...)
 *   T+0.5s  checkForAppUpdate(...)          -> Play's FLEXIBLE update sheet
 *
 * Neither showed up in the 2026-09-08 device run, and that run proved nothing
 * about them: UMP renders a sheet only in regulated geos (EU / BR / CH / SE / SG)
 * and is a no-op in Bangladesh, while the update sheet needs a newer build to
 * exist on Play, which a locally-installed APK never has. Silence, not a pass.
 *
 * It matters because Brazil is this app's #4 market by installs (7 of 111 in the
 * Play bulk window, ahead of the US at 7 and behind only BD 25 / IN 8 / IR 8),
 * and UMP fires there.
 *
 * UMP cannot simply be deleted — it is legally required before ad requests in
 * those geos. It is DEFERRED instead, to the same moment the review prompt now
 * waits for: the first scan completing. That is safe because ads are already
 * blocked in sessions 1-2 by `ads_grace_sessions = 2`, so no ad request can
 * outrun consent in the deferral window.
 *
 * Guards strip comments before matching — a guard in this repo once matched the
 * very comment explaining why it existed.
 */
class Session1LaunchDeferralTest {

    private val main: String = File("src/main/java/com/teamz/lab/debugger/MainActivity.kt")
        .readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    @Test
    fun `UMP consent is gated on the first scan, not fired at launch`() {
        assertTrue(
            "MainActivity must still call UmpConsentManager.ensureConsent somewhere — " +
                "it is legally required before ad requests in EU/BR/CH/SE/SG.",
            main.contains("UmpConsentManager.ensureConsent"),
        )
        val idx = main.indexOf("UmpConsentManager.ensureConsent")
        val preceding = main.substring(maxOf(0, idx - 600), idx)
        assertTrue(
            "ensureConsent must be behind FirstScanGate.hasCompletedScan(). Unconditional, " +
                "it opens a consent sheet at T+0.4s in regulated geos — Brazil is the #4 " +
                "market — before the user has seen a single screen. Found no " +
                "hasCompletedScan guard in the 600 chars before the call.",
            preceding.contains("hasCompletedScan"),
        )
    }

    @Test
    fun `the Play in-app-update sheet is gated on the first scan`() {
        val idx = main.indexOf("checkForAppUpdate(this@MainActivity)")
        assertTrue(
            "MainActivity must still call checkForAppUpdate — deferring it is the fix, " +
                "deleting it is not.",
            idx >= 0,
        )
        val preceding = main.substring(maxOf(0, idx - 600), idx)
        assertTrue(
            "checkForAppUpdate must be behind FirstScanGate.hasCompletedScan(). A FLEXIBLE " +
                "update sheet at T+0.5s interrupts a user who has not reached the product " +
                "yet, and the update is not urgent — it can wait for session 2.",
            preceding.contains("hasCompletedScan"),
        )
    }

    @Test
    fun `the deferral reads the same signal the review prompt waits for`() {
        assertTrue(
            "Both deferrals must key off FirstScanGate.hasCompletedScan so session 1 has ONE " +
                "definition of 'the app has delivered something'. A second, parallel notion of " +
                "readiness is how six independent timers appeared in the first place.",
            main.contains("FirstScanGate.hasCompletedScan"),
        )
    }
}
