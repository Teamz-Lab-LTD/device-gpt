package com.teamz.lab.debugger.quality

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Remote Config can activate an EMPTY config and still report success — observed on an
 * API 35 emulator, which logged "RemoteConfig activated successfully" with zero entries.
 * Every flag then silently resolves to its bundled default.
 *
 * The store listing for 3.1.14 leads with the honest four-check Device Score. If
 * `first_scan_gate_enabled` defaults to false, those users see nothing and the listing is
 * false — the exact class of claim that caused the 2026-07-08 Deceptive Behavior strike.
 *
 * Safe because this build contains no fake scan: older builds carry their own defaults.
 */
class FirstScanGateDefaultTest {

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return dir
    }

    private fun read(path: String) = File(projectRoot(), path).readText()

    @Test
    fun `first scan gate defaults to true so an empty Remote Config cannot hide it`() {
        val src = read("app/src/main/java/com/teamz/lab/debugger/utils/RemoteConfigUtils.kt")
        assertTrue(
            "first_scan_gate_enabled must default to true — an empty RC fetch would otherwise " +
                "hide the honest scan that the store listing advertises",
            Regex("\"first_scan_gate_enabled\"\\s+to\\s+true").containsMatchIn(src)
        )
    }

    @Test
    fun `the fake scan this default used to protect against is gone`() {
        val gate = read("app/src/main/java/com/teamz/lab/debugger/ui/FirstScanGate.kt")
        val screen = read("app/src/main/java/com/teamz/lab/debugger/ui/FirstScanGateScreen.kt")
        assertFalse(
            "a fabricated fallback score must never return",
            gate.contains("DEFAULT_FALLBACK_SCORE") || screen.contains("DEFAULT_FALLBACK_SCORE")
        )
        assertFalse(
            "the fake 10-second progress loop must never return",
            Regex("for \\(i in 1\\.\\.100\\)").containsMatchIn(screen)
        )
    }
}
