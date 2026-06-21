package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.1.11 W2 user-behavior insight — Invite a Friend drawer CTA regression guard.
 *
 * Per GA4 28d data, the referral system is fully built (5 reward tiers, install
 * referrer API, fraud guards, share-link generator) but `referral_shared` event
 * never crossed 22 users (<7% reach). Root cause: the CTA only existed inside
 * PaywallWithReferralFallback — users who never hit the paywall never saw it.
 *
 * Fix shipped 2026-06-22: dedicated "Invite Friends — Earn ad-free" section in
 * the drawer, placed above App Permissions (visible in normal usage). Tapping
 * calls ReferralManager.shareReferralLink which fires the Android ACTION_SEND
 * chooser + logs the ReferralShared event.
 *
 * This regression guard prevents:
 *   - The section header text being renamed (breaks GA4 dashboard slicing)
 *   - The button label being renamed (same)
 *   - The call to ReferralManager.shareReferralLink being removed (breaks the
 *     entire feature path even though the section header still shows)
 */
class InviteFriendDrawerCtaTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }

    private val drawerSrc: String by lazy {
        File(projectRoot(),
            "app/src/main/java/com/teamz/lab/debugger/ui/drawer.kt").readText()
    }

    @Test
    fun drawer_invitesFriendsSectionHeader_exists() {
        assertTrue(
            "Drawer must contain the 'Invite Friends — Earn ad-free' section header. " +
                "Renaming silently invalidates any GA4 dashboard query that filters by " +
                "the section title.",
            drawerSrc.contains("text = \"Invite Friends — Earn ad-free\"")
        )
    }

    @Test
    fun drawer_inviteAFriendButton_existsWithExactLabel() {
        // The button label drives analytics — keep it stable.
        assertTrue(
            "Drawer must contain the literal label 'Invite a Friend' on the share button. " +
                "Without it, click attribution in any per-label dashboard breaks silently.",
            drawerSrc.contains("label = \"Invite a Friend\"")
        )
    }

    @Test
    fun drawer_inviteButton_callsReferralManagerShareReferralLink() {
        // The button click handler MUST call ReferralManager.shareReferralLink.
        // Without it, the button does nothing — the section is decorative and the
        // ReferralShared event never fires.
        assertTrue(
            "Drawer Invite-a-Friend button must call ReferralManager.shareReferralLink(context). " +
                "Without this call, the section is decorative — share chooser never opens + " +
                "ReferralShared event never fires + the entire referral funnel is broken at " +
                "the entry point.",
            drawerSrc.contains("ReferralManager.shareReferralLink(context)")
        )
    }

    @Test
    fun drawer_inviteSection_appearsAboveAppPermissions() {
        // Placement matters: the whole point of the fix is making the CTA visible
        // in normal drawer scrolling, not buried below other sections.
        val inviteIdx = drawerSrc.indexOf("text = \"Invite Friends — Earn ad-free\"")
        val permsIdx = drawerSrc.indexOf("text = \"App Permissions\",")
        assertTrue("'Invite Friends' section must exist", inviteIdx > 0)
        assertTrue("'App Permissions' section must exist", permsIdx > 0)
        assertTrue(
            "'Invite Friends' section ($inviteIdx) must appear BEFORE 'App Permissions' ($permsIdx) " +
                "in drawer.kt. Below the fold = back to <7% reach.",
            inviteIdx < permsIdx
        )
    }
}
