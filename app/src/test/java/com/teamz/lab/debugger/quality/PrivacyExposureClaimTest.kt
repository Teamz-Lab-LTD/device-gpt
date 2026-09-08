package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.utils.AccessibilityFact
import com.teamz.lab.debugger.utils.AppPermissionFact
import com.teamz.lab.debugger.utils.ExposureLevel
import com.teamz.lab.debugger.utils.KeyboardFact
import com.teamz.lab.debugger.utils.assessAdTrackingExposure
import com.teamz.lab.debugger.utils.assessKeystrokeExposure
import com.teamz.lab.debugger.utils.assessScreenCaptureExposure
import com.teamz.lab.debugger.utils.assessSensitivePermissionExposure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store listing sells "keylogger detection, screen-recording apps, on-device malware scan,
 * ad tracker detection". Before 2026-09-09 none of those four could return a finding: each
 * compared installed packages against a hardcoded list of invented ids, and on Android 11+ the
 * set being searched (launcher-visible apps) excludes by construction anything that hid its
 * icon. A fifth check reported every app on the phone as a permission abuser because it asked
 * whether OUR app held the permission.
 *
 * These are behavioural tests over the pure decision layer, not greps over source text — the
 * D1DualDeliveryContractTest lesson. Two directions are tested, and both matter:
 *
 *   - a real mechanism must produce a finding that NAMES it (the old code could not)
 *   - an ordinary phone must not be accused (the old code accused every app it enumerated)
 *
 * The second half is the one that is easy to lose. A detector that flags everything is not
 * safer than one that flags nothing; it is the same defect pointed the other way, and it is
 * what a Play reviewer opening the app on a stock device would see.
 */
class PrivacyExposureClaimTest {

    private fun keyboard(pkg: String, system: Boolean) =
        KeyboardFact(packageName = pkg, label = pkg.substringAfterLast('.'), isSystem = system)

    private fun service(
        pkg: String,
        keys: Boolean = false,
        content: Boolean = false,
        shot: Boolean = false,
        system: Boolean = false,
    ) = AccessibilityFact(pkg, pkg.substringAfterLast('.'), keys, content, shot, system)

    private fun app(
        pkg: String,
        sensitive: List<String> = emptyList(),
        adId: Boolean = false,
        projection: Boolean = false,
    ) = AppPermissionFact(pkg, pkg.substringAfterLast('.'), sensitive, adId, projection)

    // ---------- keystrokes ----------

    @Test
    fun `a service that filters key events is reported and named`() {
        val f = assessKeystrokeExposure(
            keyboards = listOf(keyboard("com.google.android.inputmethod.latin", system = true)),
            accessibility = listOf(service("com.some.monitor", keys = true)),
        )
        assertEquals(
            "Key-event filtering is the API monitoring software uses to read keystrokes. " +
                "A hardcoded package list could never see it.",
            ExposureLevel.ELEVATED, f.level,
        )
        assertTrue(
            "An ELEVATED verdict that names nothing cannot be acted on: ${f.items}",
            f.items.any { it.contains("com.some.monitor") },
        )
    }

    @Test
    fun `a third-party keyboard is reported but not accused`() {
        val f = assessKeystrokeExposure(
            keyboards = listOf(keyboard("com.touchtype.swiftkey", system = false)),
            accessibility = emptyList(),
        )
        assertEquals(
            "SwiftKey is an ordinary install. Calling it a keylogger is the same kind of wrong " +
                "answer as the fictional package list, just louder.",
            ExposureLevel.INFORMATIONAL, f.level,
        )
        assertTrue(f.items.any { it.contains("com.touchtype.swiftkey") })
    }

    @Test
    fun `a stock phone comes back clean`() {
        val f = assessKeystrokeExposure(
            keyboards = listOf(keyboard("com.google.android.inputmethod.latin", system = true)),
            accessibility = listOf(service("com.google.android.marvin.talkback", content = true, system = true)),
        )
        assertEquals(
            "TalkBack is a screen reader. The check this replaces returned " +
                "\"Suspicious Services Found\" for it — telling a blind user their screen " +
                "reader was spyware.",
            ExposureLevel.NONE, f.level,
        )
        assertTrue(f.items.isEmpty())
    }

    @Test
    fun `a system service that filters key events is not treated as monitoring`() {
        // Switch Access ships with Android and requests key-event filtering as its whole
        // purpose. This case was missed on the first pass: a mutant that dropped the isSystem
        // check survived every other test in this class, which means the "do not accuse a stock
        // phone" guarantee was only being enforced for screen access, not for keystrokes.
        val f = assessKeystrokeExposure(
            keyboards = listOf(keyboard("com.google.android.inputmethod.latin", system = true)),
            accessibility = listOf(service("com.android.switchaccess", keys = true, system = true)),
        )
        assertEquals(
            "A built-in accessibility tool must never read as a keylogger.",
            ExposureLevel.NONE, f.level,
        )
    }

    // ---------- screen ----------

    @Test
    fun `a service that reads window content is elevated`() {
        val f = assessScreenCaptureExposure(
            accessibility = listOf(service("com.some.monitor", content = true)),
            apps = emptyList(),
        )
        assertEquals(ExposureLevel.ELEVATED, f.level)
        assertTrue(f.items.any { it.contains("com.some.monitor") })
    }

    @Test
    fun `a screen recorder app is informational, not a threat`() {
        val f = assessScreenCaptureExposure(
            accessibility = emptyList(),
            apps = listOf(app("com.nll.screenrecorder", projection = true)),
        )
        assertEquals(
            "Android shows a system consent dialog before any recording starts, so an app " +
                "merely holding the permission has captured nothing.",
            ExposureLevel.INFORMATIONAL, f.level,
        )
    }

    @Test
    fun `a system accessibility service does not trigger a screen warning`() {
        val f = assessScreenCaptureExposure(
            accessibility = listOf(service("com.android.switchaccess", content = true, shot = true, system = true)),
            apps = emptyList(),
        )
        assertEquals(ExposureLevel.NONE, f.level)
    }

    // ---------- ad tracking ----------

    @Test
    fun `an app holding the advertising id permission is counted`() {
        val f = assessAdTrackingExposure(listOf(
            app("com.game.one", adId = true),
            app("com.game.two", adId = true),
            app("com.offline.notes"),
        ))
        assertEquals(ExposureLevel.INFORMATIONAL, f.level)
        assertEquals("Only the two AD_ID holders count.", 2, f.items.size)
        assertFalse(
            "An app that does not hold AD_ID must not appear in a tracking list.",
            f.items.any { it.contains("com.offline.notes") },
        )
    }

    @Test
    fun `no ad id holders reports clean rather than an empty premium teaser`() {
        val f = assessAdTrackingExposure(listOf(app("com.offline.notes")))
        assertEquals(ExposureLevel.NONE, f.level)
        assertTrue(f.items.isEmpty())
    }

    // ---------- sensitive permissions: the anti-libel test ----------

    @Test
    fun `only apps that actually hold a permission are listed`() {
        val f = assessSensitivePermissionExposure(listOf(
            app("com.chat.app", sensitive = listOf("microphone", "camera")),
            app("com.wallpaper.app"),
            app("com.torch.app"),
        ))
        assertEquals(ExposureLevel.INFORMATIONAL, f.level)
        assertEquals(
            "checkSelfPermission(context, perm) takes no package argument, so the version this " +
                "replaces answered for DeviceGPT itself and returned EVERY enumerated app. " +
                "Exactly one app here holds anything.",
            1, f.items.size,
        )
        assertTrue(f.items.single().contains("com.chat.app"))
    }

    @Test
    fun `a phone where nothing holds a sensitive permission is not accused`() {
        val f = assessSensitivePermissionExposure(listOf(app("com.wallpaper.app"), app("com.torch.app")))
        assertEquals(ExposureLevel.NONE, f.level)
        assertTrue(f.items.isEmpty())
    }

    // ---------- cross-cutting contract ----------

    @Test
    fun `no verdict is ever elevated without naming what caused it`() {
        val findings = listOf(
            assessKeystrokeExposure(listOf(keyboard("com.k", false)), listOf(service("com.a", keys = true))),
            assessKeystrokeExposure(emptyList(), emptyList()),
            assessScreenCaptureExposure(listOf(service("com.a", content = true)), emptyList()),
            assessScreenCaptureExposure(emptyList(), emptyList()),
            assessAdTrackingExposure(listOf(app("com.a", adId = true))),
            assessSensitivePermissionExposure(listOf(app("com.a", sensitive = listOf("camera")))),
        )
        for (f in findings) {
            if (f.level == ExposureLevel.NONE) {
                assertTrue("A clean verdict must list nothing: $f", f.items.isEmpty())
            } else {
                assertTrue("A non-clean verdict with no items is unusable: $f", f.items.isNotEmpty())
                assertTrue("Every non-clean verdict needs a next step: $f", f.recommendation != null)
            }
            assertTrue("Every verdict needs a headline", f.headline.isNotBlank())
        }
    }

    @Test
    fun `render marks a clean result differently from a finding`() {
        val clean = assessAdTrackingExposure(emptyList()).render()
        val found = assessAdTrackingExposure(listOf(app("com.a", adId = true))).render()
        assertTrue("clean must read as clean: $clean", clean.startsWith("✅"))
        assertFalse("a real finding must not render as clean: $found", found.startsWith("✅"))
    }
}
