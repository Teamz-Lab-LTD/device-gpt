package com.teamz.lab.debugger.quality

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teamz.lab.debugger.utils.AiShareTextGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the "Ask any AI" prompt generator.
 *
 * Two shapes, discriminated by whether Bridge is on:
 *   - SNAPSHOT MODE (no bridgeUrl/bridgePin) — the AI has no live channel, so
 *     the prompt must dump every metric we can read (Battery, Storage, Memory,
 *     Network, Camera facts, CPU/Thermal, Uptime). This IS the AI's only data
 *     source.
 *   - LIVE MODE (bridge on) — the AI has the URL+PIN and can call `devicegpt_*`
 *     tools for fresh data. The prompt must NOT ship a static metric dump, or
 *     the AI reads a stale "100%" battery instead of calling the tool. Live
 *     mode is a short handoff: identity + live handle + rules + camera playbook.
 *
 * Policy invariants (both modes):
 *   - Camera-quality banned phrases (same list as [CameraHealthPolicyGuardTest])
 *     must NOT appear — the AI would happily repeat any banned score back to the
 *     user and re-open the Play policy issue.
 *   - The camera-broken playbook must appear (worried-owner "my camera is broken"
 *     is the flagship path).
 *   - The AI is explicitly told not to rate camera quality.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AiShareTextGeneratorTest {

    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ─────────────────────── snapshot-mode shape ───────────────────────

    @Test fun `every documented section header is present in snapshot mode`() {
        val prompt = AiShareTextGenerator.buildPrompt(context)
        for (header in listOf(
            "=== Device ===",
            "=== Battery ===",
            "=== Storage ===",
            "=== Memory ===",
            "=== Network + WiFi ===",
            "=== Cameras",
            "=== CPU + Thermal ===",
            "=== Uptime ===",
            "=== How you should behave ===",
            "=== If I say my camera is broken ===",
            "=== General ask ===",
        )) {
            assertTrue("missing section header: $header", prompt.contains(header))
        }
    }

    @Test fun `snapshot-mode prompt does NOT include the Bridge live-access block`() {
        val prompt = AiShareTextGenerator.buildPrompt(context)
        assertFalse(
            "URL/PIN block leaked into snapshot-mode prompt — user did not opt in to live access",
            prompt.contains("=== Live access via MCP ==="),
        )
        assertFalse(prompt.contains("URL:"))
        assertFalse(prompt.contains("PIN:"))
    }

    // ─────────────────────── live-mode shape ───────────────────────

    @Test fun `live-mode prompt includes the Bridge URL and PIN verbatim`() {
        val prompt = AiShareTextGenerator.buildPrompt(
            context,
            bridgeUrl = "http://192.168.0.127:8787",
            bridgePin = "552946",
        )
        assertTrue(prompt.contains("=== Live access via MCP ==="))
        assertTrue("URL not embedded verbatim", prompt.contains("http://192.168.0.127:8787"))
        assertTrue("PIN not embedded verbatim", prompt.contains("552946"))
    }

    @Test fun `live-mode prompt has NO static per-metric dump — tools are the truth source`() {
        // The whole point of the live-mode split: don't ship stale numbers the AI
        // might read INSTEAD of calling the fresh tool. If a Battery/Storage/Memory
        // section reappears in live mode, someone reverted the split and reopened
        // the stale-vs-live trap.
        val prompt = AiShareTextGenerator.buildPrompt(context, "http://x", "111111")
        for (bannedHeader in listOf(
            "=== Battery ===",
            "=== Storage ===",
            "=== Memory ===",
            "=== Network + WiFi ===",
            "=== Cameras",
            "=== CPU + Thermal ===",
            "=== Uptime ===",
        )) {
            assertFalse(
                "live-mode prompt regressed — it now ships '$bannedHeader'. That's the stale-data trap: the AI will read this instead of calling the fresh tool.",
                prompt.contains(bannedHeader),
            )
        }
    }

    @Test fun `live-mode prompt tells the AI to use devicegpt_ tools FIRST`() {
        val prompt = AiShareTextGenerator.buildPrompt(context, "http://x", "111111")
        // The AI must be told to prefer tools over any assumption. Without this,
        // some AIs default to answering from priors (their training data about
        // "typical Android phones") rather than calling the live tool.
        assertTrue(
            "live-mode prompt must direct the AI to use devicegpt_ tools",
            prompt.contains("devicegpt_"),
        )
        assertTrue(
            "live-mode prompt must tell the AI USE THEM (not just mention their existence)",
            prompt.contains("USE THEM"),
        )
    }

    @Test fun `live-mode prompt is materially shorter than snapshot-mode prompt`() {
        // Sanity check on the whole design goal. If live is not << snapshot, the
        // split is not doing its job.
        val snap = AiShareTextGenerator.buildPrompt(context)
        val live = AiShareTextGenerator.buildPrompt(context, "http://x", "111111")
        assertTrue(
            "live-mode (${live.length}) must be much shorter than snapshot-mode (${snap.length}) — else the split is worthless",
            live.length < snap.length - 500,
        )
    }

    @Test fun `live-mode block is opt-in — passing only one of url or pin does not emit the block`() {
        val onlyUrl = AiShareTextGenerator.buildPrompt(context, bridgeUrl = "http://x", bridgePin = null)
        val onlyPin = AiShareTextGenerator.buildPrompt(context, bridgeUrl = null, bridgePin = "111111")
        assertFalse(onlyUrl.contains("=== Live access via MCP ==="))
        assertFalse(onlyPin.contains("=== Live access via MCP ==="))
        // and the raw values must not appear
        assertFalse(onlyUrl.contains("http://x"))
        assertFalse(onlyPin.contains("111111"))
    }

    // ─────────────────────── policy (Deceptive Behavior), BOTH modes ───────────────────────

    @Test fun `no banned camera-quality phrase appears in either mode`() {
        val bannedList = listOf(
            "colour accuracy", "color accuracy",
            "sharpness score", "sharpness rating",
            "sensor health",
            "lens quality",
            "camera health score", "camera quality score",
            "detect lens damage", "detects lens damage",
            "hot pixel", "hotpixel",
            "stuck pixel", "stuckpixel",
            "dark frame", "darkframe",
        )
        for ((mode, prompt) in mapOf(
            "snapshot" to AiShareTextGenerator.buildPrompt(context),
            "live" to AiShareTextGenerator.buildPrompt(context, "http://192.168.1.1:8787", "123456"),
        )) {
            val lc = prompt.lowercase()
            for (banned in bannedList) {
                assertFalse(
                    "[$mode] generated prompt contains banned policy phrase: '$banned' — same class as the a44b84b rejection",
                    lc.contains(banned),
                )
            }
        }
    }

    @Test fun `both modes explicitly forbid the AI from rating camera quality`() {
        val snap = AiShareTextGenerator.buildPrompt(context)
        val live = AiShareTextGenerator.buildPrompt(context, "http://x", "111111")
        assertTrue("snapshot must forbid camera rating", snap.contains("Do NOT rate the camera quality"))
        assertTrue("live must forbid camera rating too", live.contains("Do NOT rate the camera quality"))
    }

    @Test fun `live-mode prompt tells the AI to relay user_message verbatim on tool failures`() {
        // Live mode is the only place tool responses exist. Without this rule, the
        // AI paraphrases a real hardware-failure user_message into a made-up recovery
        // step. Snapshot mode has no tools so the rule is irrelevant there.
        val live = AiShareTextGenerator.buildPrompt(context, "http://x", "111111")
        assertTrue(
            "live-mode prompt must instruct the AI to relay user_message verbatim",
            live.contains("relay it VERBATIM"),
        )
    }

    @Test fun `snapshot mode includes the full 8-step camera-diagnosis playbook`() {
        // Snapshot mode is the only place the AI needs a step-by-step spelled out —
        // it has no tools to explore and no way to discover devicegpt_* names on
        // its own. Live mode uses a short inline ordering inside the General ask
        // section (the AI can list its own tools).
        val prompt = AiShareTextGenerator.buildPrompt(context)
        val block = prompt.substringAfter("=== If I say my camera is broken ===")
            .substringBefore("=== General ask ===")
        for (n in 1..8) {
            assertTrue(
                "camera playbook missing step $n. — user's wife-phone scenario relies on this",
                block.contains("  $n."),
            )
        }
    }

    @Test fun `live mode still names the camera-diagnosis ORDER, even without the 8-step block`() {
        // We dropped the 8-step block from live mode to keep it under the ceiling,
        // but the AI still needs to know the ORDER (permission before hardware, hardware
        // before storage, actual-photo-inspection before repair verdict). Otherwise the
        // AI jumps to "capture a photo" as step 1 and skips the permission check that
        // would have solved most cases.
        val live = AiShareTextGenerator.buildPrompt(context, "http://x", "111111")
        val expectedOrderMarkers = listOf(
            "permission",
            "hardware",
            "thermal",
            "storage",
            "photo",
            "repair shop",
        )
        for (marker in expectedOrderMarkers) {
            assertTrue(
                "live-mode General ask lost the '$marker' step — camera-broken flow will misfire",
                live.contains(marker),
            )
        }
    }

    // ─────────────────────── length + robustness ───────────────────────

    @Test fun `snapshot-mode prompt stays under 4000 characters (soft ceiling)`() {
        // The design target is <2000 chars to fit a single AI input, but Robolectric
        // may include long emulated hardware strings. 4000 is a soft ceiling — if
        // we sail over this, the copy/paste UX starts to break on ChatGPT mobile.
        val prompt = AiShareTextGenerator.buildPrompt(context)
        assertTrue(
            "prompt is ${prompt.length} chars — over the 4000-char UX ceiling. Trim a section.",
            prompt.length < 4000,
        )
    }

    @Test fun `live-mode prompt stays under 2000 characters (tight ceiling)`() {
        // Live mode has no per-metric dump; there is no excuse for it to be big.
        // A live prompt over 2000 chars means someone added a paragraph of
        // marketing / hand-holding — cut it, the AI can ask.
        val prompt = AiShareTextGenerator.buildPrompt(context, "http://192.168.0.127:8787", "552946")
        assertTrue(
            "live-mode prompt is ${prompt.length} chars — over the 2000-char ceiling. Trim.",
            prompt.length < 2000,
        )
    }

    @Test fun `prompt is non-empty and non-null even on a minimal Robolectric context`() {
        // Guards a "hidden crash" regression — every section builder currently swallows
        // its own exception, but a NullPointerException in the section headers would
        // still throw. This makes sure the whole prompt survives Robolectric's spartan
        // service implementations.
        val prompt = AiShareTextGenerator.buildPrompt(context)
        assertNotNull(prompt)
        assertTrue("prompt is empty — a section builder threw and the exception was swallowed silently", prompt.length > 200)
    }

    @Test fun `prompt is deterministic on the header structure across two calls`() {
        // The section headers must not depend on any time-varying input. If two
        // consecutive calls produce different section layouts, someone accidentally
        // shuffled the sections via a `Set` or a nondeterministic map iteration.
        val a = AiShareTextGenerator.buildPrompt(context)
        val b = AiShareTextGenerator.buildPrompt(context)
        val headersA = a.lines().filter { it.startsWith("===") }
        val headersB = b.lines().filter { it.startsWith("===") }
        assertTrue("section header order changed between two calls", headersA == headersB)
    }
}
