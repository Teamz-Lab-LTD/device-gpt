package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.1.11 W2 — Verified Report drawer promotion regression guard.
 *
 * GA4 28d stored data: verified_report_generated / verified_report_shared /
 * report_verification_attempted all <22 users (<7% reach) despite the feature
 * being the app's strongest viral asset (ECDSA-signed score badge for social
 * proof). Root cause: buttons sat at drawer position ~16, BELOW the AdBadge +
 * More Apps + Review prompt — virtually nobody scrolled that far.
 *
 * Fix shipped 2026-06-22 (per /ui-ux-pro-max audit):
 *   - New section "Verified Reports — Pro Feature" placed RIGHT AFTER Invite
 *     Friends and BEFORE App Permissions (top third of drawer)
 *   - Removed the buried duplicate at the bottom
 *   - "Pro Feature" label sets expectation since both actions trigger paywall
 *
 * This regression guard prevents:
 *   - Section header being renamed (breaks GA4 dashboard slicing)
 *   - Buttons being moved back below App Permissions (re-buries the feature)
 *   - The 2 buttons being duplicated (drawer ends up with 4 instead of 2)
 */
class VerifiedReportPromotionTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private val src: String by lazy {
        File(projectRoot(),
            "app/src/main/java/com/teamz/lab/debugger/ui/drawer.kt").readText()
    }

    @Test
    fun drawer_verifiedReportsSectionHeader_exists() {
        assertTrue(
            "Drawer must contain 'Verified Reports — Pro Feature' section header. " +
                "Renaming silently invalidates GA4 dashboard queries that filter by section.",
            src.contains("text = \"Verified Reports — Pro Feature\"")
        )
    }

    @Test
    fun drawer_generateVerifiedReportButtonLabel_isLockedExact() {
        assertTrue(
            "Drawer must contain literal label 'Generate Verified Report' — drives " +
                "DrawerItemClicked event with item=generate_verified_report.",
            src.contains("label = \"Generate Verified Report\"")
        )
    }

    @Test
    fun drawer_verifyAReportButtonLabel_isLockedExact() {
        assertTrue(
            "Drawer must contain literal label 'Verify a Report' — drives " +
                "DrawerItemClicked event with item=verify_report.",
            src.contains("label = \"Verify a Report\"")
        )
    }

    @Test
    fun drawer_verifiedReportsSection_appearsAboveAppPermissions() {
        val verifiedIdx = src.indexOf("text = \"Verified Reports — Pro Feature\"")
        val permsIdx = src.indexOf("text = \"App Permissions\",")
        assertTrue("'Verified Reports' section must exist", verifiedIdx > 0)
        assertTrue("'App Permissions' section must exist", permsIdx > 0)
        assertTrue(
            "'Verified Reports' section ($verifiedIdx) must appear BEFORE 'App Permissions' " +
                "($permsIdx) in drawer.kt. Below = back to <7% reach (the original bug).",
            verifiedIdx < permsIdx
        )
    }

    @Test
    fun drawer_verifiedReportsSection_appearsAfterInviteFriends() {
        // Position constraint: Pro features should follow the engagement-loop sections
        // (Widget + Invite Friends). Putting it ABOVE Widget would distract from the
        // app's #1 surface. Putting it AFTER Invite Friends keeps the natural flow:
        // engagement → growth → pro features → settings.
        val inviteIdx = src.indexOf("text = \"Invite Friends — Earn ad-free\"")
        val verifiedIdx = src.indexOf("text = \"Verified Reports — Pro Feature\"")
        assertTrue("'Invite Friends' must exist", inviteIdx > 0)
        assertTrue("'Verified Reports' must exist", verifiedIdx > 0)
        assertTrue(
            "'Verified Reports' ($verifiedIdx) must come AFTER 'Invite Friends' ($inviteIdx) " +
                "so engagement→growth→pro flow is preserved.",
            verifiedIdx > inviteIdx
        )
    }

    @Test
    fun drawer_generateVerifiedReportButton_existsExactlyOnce() {
        // After the promotion, the OLD buried copy must be deleted — otherwise the
        // drawer has 4 buttons (2 in new section + 2 buried) and the move accomplished
        // nothing.
        val count = Regex("""label = "Generate Verified Report"""").findAll(src).count()
        assertTrue(
            "Exactly 1 'Generate Verified Report' button must exist. Found $count. " +
                "More than 1 means the old buried copy was not removed — duplicates dilute " +
                "discovery + double-fire GA4 events.",
            count == 1
        )
    }

    @Test
    fun drawer_verifyAReportButton_existsExactlyOnce() {
        val count = Regex("""label = "Verify a Report"""").findAll(src).count()
        assertTrue(
            "Exactly 1 'Verify a Report' button must exist. Found $count. " +
                "More than 1 means the old buried copy was not removed.",
            count == 1
        )
    }
}
