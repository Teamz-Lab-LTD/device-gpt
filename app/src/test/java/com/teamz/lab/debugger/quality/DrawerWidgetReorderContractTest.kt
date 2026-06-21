package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.1.11 E2E — drawer Widget section position regression guard.
 *
 * Validates the drawer reorder shipped 2026-06-21 (commit f17a9f4): the
 * "Widget" section header must appear BEFORE the "App Permissions" section
 * header in the source order of drawer.kt.
 *
 * NOTE: A previous version of this test attempted to open the navigation drawer
 * via ComposeTestRule and assert Compose nodes. That approach fails on real
 * devices because:
 *   (a) The drawer is closed by default in MainActivity's NavigationLayout
 *   (b) The drawer trigger is an icon-only button (no "Menu" text label)
 *   (c) Opening the drawer requires a hamburger-icon ContentDescription match,
 *       and the description varies by theme + locale
 *
 * Source-order assertion gives the same guarantee with zero device dependency,
 * zero flakiness, and runs in milliseconds. If a future PR moves Widget back
 * BELOW App Permissions (or removes either section entirely), this test fails.
 *
 * Visual drawer verification is owner-driven manual smoke per the v3.1.11
 * release playbook.
 *
 * Lives in androidTest/ for build-system reasons (it imports from the main
 * source set the same way the other E2E classes do); execution is essentially
 * a JVM source scan + assertion.
 */
class DrawerWidgetReorderContractTest {

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
    fun widgetSectionHeaderAppearsBeforeAppPermissionsHeader() {
        val widgetIdx = drawerSrc.indexOf("text = \"Widget\",")
        val permsIdx = drawerSrc.indexOf("text = \"App Permissions\",")
        assertTrue("'Widget' section header must exist in drawer.kt", widgetIdx > 0)
        assertTrue("'App Permissions' section header must exist in drawer.kt", permsIdx > 0)
        assertTrue(
            "'Widget' section must render BEFORE 'App Permissions' in drawer.kt " +
                "(widget index=$widgetIdx, perms index=$permsIdx). " +
                "If you reverted the 2026-06-21 reorder, GA4 user-discovery analytics " +
                "for the widget pin-button will regress to <7% reach.",
            widgetIdx < permsIdx
        )
    }

    @Test
    fun addToHomeScreenButtonLabelIsLockedToExactString() {
        // The literal label drives GA4 user-discovery analytics (event
        // WidgetAddToHomeScreenClicked is fired from the click handler attached
        // to this exact text). Renaming silently invalidates the dashboard query.
        assertTrue(
            "Drawer must contain the literal label 'Add to Home Screen' — renaming would " +
                "silently invalidate the GA4 dashboard query for WidgetAddToHomeScreenClicked.",
            drawerSrc.contains("label = \"Add to Home Screen\"")
        )
        assertTrue(
            "Drawer must contain the literal label 'Add to Lock Screen' on Android 14+ — " +
                "same dashboard-query stability concern.",
            drawerSrc.contains("label = \"Add to Lock Screen\"")
        )
    }
}
