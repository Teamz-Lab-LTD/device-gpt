package com.teamz.lab.debugger.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The store listing advertises "hidden apps" detection in eleven languages. This pins that
 * the code behind it actually looks for hidden apps.
 *
 * It used to filter on `loadLabel().isNullOrEmpty()` — apps with no display NAME, a rare
 * packaging edge case. Stalkerware keeps its label and drops its launcher ICON, so the old
 * check reported "no hidden apps detected" on effectively every phone no matter what was
 * installed. Same shape as the per-app battery claim that drew a Play Deceptive Behavior
 * rejection in a44b84b: a weak proxy presented as the real measurement.
 *
 * Guards strip comments before matching — a guard in this repo once matched the very comment
 * explaining why it existed, and the comment above says "loadLabel" out loud.
 */
class HiddenAppsClaimTest {

    private val utils: String = File("src/main/java/com/teamz/lab/debugger/utils/device_utils.kt")
        .readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    private val fn: String = utils.substringAfter("fun detectHiddenApps(").substringBefore("\nfun ")

    @Test
    fun `hidden means no launcher icon, not a missing label`() {
        assertTrue(
            "detectHiddenApps must test getLaunchIntentForPackage — that is what 'hidden from " +
                "the app drawer' actually means. Without it the listing's hidden-apps claim is " +
                "unsupported.",
            fn.contains("getLaunchIntentForPackage"),
        )
        assertTrue(
            "the old loadLabel().isNullOrEmpty() proxy must not come back as the detection rule",
            !fn.contains("loadLabel(context.packageManager)\n            .isNullOrEmpty()") &&
                !fn.contains("isNullOrEmpty() }"),
        )
    }

    @Test
    fun `it does not call an iconless app malware`() {
        assertTrue(
            "Keyboards, device-admin agents and carrier services are legitimately iconless. " +
                "Claiming spyware from a missing icon would replace one unsupported claim with " +
                "another.",
            fn.contains("Not proof of spyware"),
        )
    }

    @Test
    fun `our own package is excluded`() {
        assertTrue(
            "DeviceGPT reporting itself would be an obvious false positive.",
            fn.contains("app.packageName == context.packageName"),
        )
    }
}
