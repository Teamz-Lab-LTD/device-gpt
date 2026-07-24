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
 * The prompt is the ONLY thing a snapshot-mode user hands to their AI, so:
 *   - Every documented section must be present (a section that silently disappears
 *     after a refactor leaves the AI with less context than the user thinks).
 *   - Camera-quality banned phrases (same list as [CameraHealthPolicyGuardTest])
 *     must NOT appear in the generated text — the AI would happily repeat any
 *     banned score back to the user and re-open the Play policy issue.
 *   - The Bridge live-access block appears IFF a url+pin are passed. If a user
 *     shares the snapshot with Bridge OFF, the URL/PIN must not leak.
 *   - Character length stays under the 2000-char goal so it fits in every AI's
 *     first-message input box.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AiShareTextGeneratorTest {

    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ─────────────────────── shape ───────────────────────

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
            prompt.contains("=== Optional: live access via MCP ==="),
        )
        assertFalse(prompt.contains("URL:"))
        assertFalse(prompt.contains("PIN:"))
    }

    @Test fun `live-mode prompt includes the Bridge URL and PIN verbatim`() {
        val prompt = AiShareTextGenerator.buildPrompt(
            context,
            bridgeUrl = "http://192.168.0.127:8787",
            bridgePin = "552946",
        )
        assertTrue(prompt.contains("=== Optional: live access via MCP ==="))
        assertTrue("URL not embedded verbatim", prompt.contains("http://192.168.0.127:8787"))
        assertTrue("PIN not embedded verbatim", prompt.contains("552946"))
    }

    @Test fun `live-mode block is opt-in — passing only one of url or pin does not emit the block`() {
        val onlyUrl = AiShareTextGenerator.buildPrompt(context, bridgeUrl = "http://x", bridgePin = null)
        val onlyPin = AiShareTextGenerator.buildPrompt(context, bridgeUrl = null, bridgePin = "111111")
        assertFalse(onlyUrl.contains("=== Optional: live access via MCP ==="))
        assertFalse(onlyPin.contains("=== Optional: live access via MCP ==="))
        // and the raw values must not appear
        assertFalse(onlyUrl.contains("http://x"))
        assertFalse(onlyPin.contains("111111"))
    }

    // ─────────────────────── policy (Deceptive Behavior) ───────────────────────

    @Test fun `no banned camera-quality phrase appears in the generated prompt`() {
        val prompt = AiShareTextGenerator.buildPrompt(
            context,
            bridgeUrl = "http://192.168.1.1:8787",
            bridgePin = "123456",
        ).lowercase()
        // Same list as CameraHealthPolicyGuardTest — kept in sync deliberately.
        for (banned in listOf(
            "colour accuracy", "color accuracy",
            "sharpness score", "sharpness rating",
            "sensor health",
            "lens quality",
            "camera health score", "camera quality score",
            "detect lens damage", "detects lens damage",
            "hot pixel", "hotpixel",
            "stuck pixel", "stuckpixel",
            "dark frame", "darkframe",
        )) {
            assertFalse(
                "generated prompt contains banned policy phrase: '$banned' — same class as the a44b84b rejection",
                prompt.contains(banned),
            )
        }
    }

    @Test fun `prompt explicitly forbids the AI from rating camera quality`() {
        // Positive guard: not only is the prompt free of the banned phrases, it
        // ALSO tells the receiving AI never to invent a camera-quality claim.
        // Without this, the AI could still hallucinate one from the fact sheet.
        val prompt = AiShareTextGenerator.buildPrompt(context)
        assertTrue(
            "prompt must instruct the AI not to rate camera quality",
            prompt.contains("Do NOT rate the camera quality"),
        )
    }

    @Test fun `prompt tells the AI to relay user_message verbatim on tool failures`() {
        // This mirrors the same rule in AI_ONBOARDING_PROMPT.md. If the AI paraphrases
        // instead of relaying, users get a made-up recovery step for a real hardware
        // failure — the exact opposite of the tool contract.
        val prompt = AiShareTextGenerator.buildPrompt(context)
        assertTrue(
            "prompt must instruct the AI to relay user_message verbatim",
            prompt.contains("relay it VERBATIM"),
        )
    }

    @Test fun `prompt includes the camera-diagnosis playbook (both modes)`() {
        val prompt = AiShareTextGenerator.buildPrompt(context)
        // Numbered steps 1..8 must all appear inside the playbook block.
        val block = prompt.substringAfter("=== If I say my camera is broken ===")
            .substringBefore("=== General ask ===")
        for (n in 1..8) {
            assertTrue("camera playbook missing step $n. — user's wife-phone scenario relies on this", block.contains("  $n."))
        }
    }

    // ─────────────────────── length + robustness ───────────────────────

    @Test fun `prompt stays under 4000 characters (soft ceiling)`() {
        // The design target is <2000 chars to fit a single AI input, but Robolectric
        // may include long emulated hardware strings. 4000 is a soft ceiling — if
        // we sail over this, the copy/paste UX starts to break on ChatGPT mobile.
        val prompt = AiShareTextGenerator.buildPrompt(context)
        assertTrue(
            "prompt is ${prompt.length} chars — over the 4000-char UX ceiling. Trim a section.",
            prompt.length < 4000,
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
