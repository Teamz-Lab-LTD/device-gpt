package com.teamz.lab.debugger.quality

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Same contract AiBridgeStringResourceGuardTest enforces, applied to the website
 * probe card: every `R.string.probe_*` it references must exist in `values` AND
 * `values-bn`. A missing key is a ResourceNotFoundException crash for a Bangla-locale
 * user, not a soft fallback to English.
 *
 * Also pins the privacy contract: the domain the user typed must never reach an
 * analytics payload. That is not a style preference — a user-supplied string
 * transmitted off device is precisely the shape that got vc37 and vc39 rejected
 * under Play's Data safety policy.
 */
class CustomDomainProbeGuardTest {

    private fun locate(rel: String): File {
        val root = System.getProperty("user.dir") ?: "."
        return listOf(File(root, rel), File(root, "app/$rel"), File("../app/$rel"))
            .firstOrNull { it.exists() } ?: File(root, rel)
    }

    private val cardSrc: String
        get() = locate("src/main/java/com/teamz/lab/debugger/ui/CustomDomainProbeCard.kt")
            .also { assertTrue("CustomDomainProbeCard.kt missing", it.exists()) }
            .readText()

    @Test
    fun `every R_string_probe_ reference exists in en AND bn`() {
        val keys = Regex("R\\.string\\.(probe_[a-z0-9_]+)").findAll(cardSrc)
            .map { it.groupValues[1] }.toSortedSet()
        assertTrue("no R.string.probe_* references found — regex or file drifted", keys.isNotEmpty())

        for (locale in listOf("values", "values-bn")) {
            val xml = locate("src/main/res/$locale/strings.xml")
            assertTrue("$locale/strings.xml missing", xml.exists())
            val body = xml.readText()
            for (key in keys) {
                assertTrue(
                    "$locale/strings.xml missing R.string.$key — " +
                        "Bangla users would crash with ResourceNotFoundException",
                    body.contains("name=\"$key\"") || body.contains("name='$key'")
                )
            }
        }
    }

    @Test
    fun `every app_doctor and tab string in the tab section exists in en AND bn`() {
        val src = locate("src/main/java/com/teamz/lab/debugger/ui/AppDoctorTabSection.kt")
        assertTrue("AppDoctorTabSection.kt missing", src.exists())
        val keys = Regex("R\\.string\\.((?:app_doctor|tab_app_doctor)[a-z0-9_]*)")
            .findAll(src.readText()).map { it.groupValues[1] }.toSortedSet()
        assertTrue("no app doctor string refs found — regex or file drifted", keys.isNotEmpty())
        for (locale in listOf("values", "values-bn")) {
            val body = locate("src/main/res/$locale/strings.xml").readText()
            for (key in keys) {
                assertTrue(
                    "$locale/strings.xml missing R.string.$key — Bangla users would crash",
                    body.contains("name=\"$key\"") || body.contains("name='$key'")
                )
            }
        }
        // The tab label itself is referenced from the nav experience, not the section.
        for (locale in listOf("values", "values-bn")) {
            val body = locate("src/main/res/$locale/strings.xml").readText()
            assertTrue(
                "$locale/strings.xml missing R.string.tab_app_doctor — the tab would crash on select",
                body.contains("name=\"tab_app_doctor\"")
            )
        }
    }

    @Test
    fun `app doctor is never a required tab`() {
        // A tab listed in requiredTabs makes TabOrderManager reject the WHOLE RC tab_order
        // when a config omits it, silently collapsing every user back to the default order.
        val body = locate("src/main/java/com/teamz/lab/debugger/utils/TabOrderManager.kt").readText()
        val required = Regex("val requiredTabs = setOf\\(([^)]*)\\)")
            .find(body)?.groupValues?.get(1).orEmpty()
        assertTrue("requiredTabs block not found — regex drifted", required.isNotBlank())
        assertFalse(
            "APP_DOCTOR must not be in requiredTabs",
            required.contains("APP_DOCTOR")
        )
    }

    @Test
    fun `the probe card has exactly one home`() {
        // It previously lived on Network Info as well; two copies double-fire the
        // custom_domain_probe_* events and give one feature two places to look for it.
        val roots = listOf(
            "src/main/java/com/teamz/lab/debugger/ui/network_ui.kt",
            "src/main/java/com/teamz/lab/debugger/ui/AppDoctorTabSection.kt"
        )
        val callers = roots.filter { locate(it).exists() && locate(it).readText().contains("CustomDomainProbeCard()") }
        assertEquals("CustomDomainProbeCard() must be mounted exactly once", 1, callers.size)
    }

    @Test
    fun `the typed domain is never attached to an analytics event`() {
        // Find each logEvent(...) payload and assert none mentions the domain vars.
        val payloads = Regex("logEvent\\(([\\s\\S]{0,600}?)\\)\\s*\\n", RegexOption.MULTILINE)
            .findAll(cardSrc).map { it.groupValues[1] }.toList()
        assertTrue("no logEvent call sites found — regex drifted", payloads.isNotEmpty())
        for (p in payloads) {
            assertFalse(
                "an analytics payload references the user-typed domain:\n$p",
                Regex("\"domain\"|to\\s+domain\\b|\\binput\\b").containsMatchIn(p)
            )
        }
    }

    @Test
    fun `probe runs more than once so intermittent faults cannot hide`() {
        val tester = locate("src/main/java/com/teamz/lab/debugger/utils/NetworkReachabilityTester.kt")
        val body = tester.readText()
        assertTrue(
            "DEFAULT_PROBE_ATTEMPTS must stay above 1 — a single probe is what made the " +
                "2026-08-25 incident invisible",
            Regex("DEFAULT_PROBE_ATTEMPTS\\s*=\\s*([2-9]|10)").containsMatchIn(body)
        )
        assertTrue(
            "attempts must be spaced; parallel/no-gap attempts reuse the connection pool " +
                "and measure the pool instead of the network",
            body.contains("ATTEMPT_GAP_MS")
        )
    }

    @Test
    fun `probe list is remote-config driven, not a hardcoded val`() {
        val body = locate(
            "src/main/java/com/teamz/lab/debugger/utils/NetworkReachabilityTester.kt"
        ).readText()
        assertFalse(
            "TEST_DOMAINS came back as a hardcoded list — a live incident must be a " +
                "console change, not a Play release",
            Regex("private val TEST_DOMAINS\\s*=\\s*listOf").containsMatchIn(body)
        )
        assertTrue(
            "probe list must be read through a function, not snapshotted in a val at " +
                "class-init (which happens before the first RC fetch on a cold start)",
            body.contains("private fun testDomains()")
        )
    }
}
