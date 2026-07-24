package com.teamz.lab.debugger.quality

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the string-key contract for the AI Bridge UI.
 *
 * Every `stringResource(R.string.ai_*)` reference in [ui/ai_bridge_card.kt] must
 * exist in ALL packaged locales, not just `values/strings.xml`. A Bangla user
 * whose device locale is bn-BD hitting a missing R.string reference gets a
 * ResourceNotFoundException crash — verified by AndroidRuntime, not a soft
 * fallback. This has burned other tabs in this app before (untracked KDoc, but
 * see the taqwa-model-madrasha incident in memory).
 *
 * Also verifies the AI Bridge JSON contract: the env keys we tell users to
 * paste into their MCP config MUST match the env keys the MCP wrapper Python
 * script reads at startup. A silent rename of either half breaks every user's
 * setup with no visible error.
 */
class AiBridgeStringResourceGuardTest {

    private val requiredLocales = listOf("values", "values-bn")

    @Test fun `every R_string_ai_ reference in ai_bridge_card exists in en AND bn`() {
        val src = File("src/main/java/com/teamz/lab/debugger/ui/ai_bridge_card.kt")
        assertTrue("source file missing", src.exists())
        // Include digits — `ai_bridge_guide_step1_title` and friends would otherwise
        // truncate at the digit and match a non-existent stem.
        val keys = Regex("R\\.string\\.(ai_[a-z0-9_]+)").findAll(src.readText())
            .map { it.groupValues[1] }
            .toSortedSet()
        assertTrue("no R.string.ai_* references found — regex or file drifted", keys.isNotEmpty())

        for (locale in requiredLocales) {
            val stringsXml = File("src/main/res/$locale/strings.xml")
            assertTrue("$locale/strings.xml missing", stringsXml.exists())
            val body = stringsXml.readText()
            for (key in keys) {
                // A key defined as either `<string name="key">…</string>` or
                // `<string name='key'>…</string>` counts.
                val exists = body.contains("name=\"$key\"") || body.contains("name='$key'")
                assertTrue(
                    "$locale/strings.xml missing R.string.$key — Bangla users would crash with ResourceNotFoundException",
                    exists,
                )
            }
        }
    }

    @Test fun `every AiShareTextGenerator R_string reference exists in both locales`() {
        // AiShareTextGenerator does not currently use string resources (it builds
        // an English AI prompt only), but a future refactor that localises the
        // "How you should behave" section could easily miss a locale. Guard now.
        val src = File("src/main/java/com/teamz/lab/debugger/utils/AiShareTextGenerator.kt")
        if (!src.exists()) return   // no crash if the file has moved

        val keys = Regex("R\\.string\\.([a-z0-9_]+)").findAll(src.readText())
            .map { it.groupValues[1] }
            .toSortedSet()
        if (keys.isEmpty()) return  // legitimate: file may still be all-English

        for (locale in requiredLocales) {
            val body = File("src/main/res/$locale/strings.xml").readText()
            for (key in keys) {
                assertTrue(
                    "$locale/strings.xml missing R.string.$key referenced by AiShareTextGenerator",
                    body.contains("name=\"$key\"") || body.contains("name='$key'"),
                )
            }
        }
    }

    // ─────────────────────── env-key contract with the Python wrapper ───────────────────────

    @Test fun `MCP JSON block env keys match what the Python server reads`() {
        // The Python wrapper at docs/ai-bridge-mcp-wrapper/server.py reads these
        // env vars via os.environ["DEVICEGPT_BRIDGE_URL"] and ["DEVICEGPT_BRIDGE_PIN"].
        // If ai_bridge_card renames either side of this pair, every user's
        // config file silently stops working and no error surfaces on either end.
        val bridgeCard = File("src/main/java/com/teamz/lab/debugger/ui/ai_bridge_card.kt").readText()
        for (envKey in listOf("DEVICEGPT_BRIDGE_URL", "DEVICEGPT_BRIDGE_PIN")) {
            assertTrue(
                "ai_bridge_card.kt no longer emits $envKey — Python wrapper reads it, users will silently break",
                bridgeCard.contains(envKey),
            )
        }

        // If the MCP wrapper is checked into the repo, verify the Python side too.
        val serverPy = File("docs/ai-bridge-mcp-wrapper/server.py")
        if (serverPy.exists()) {
            val body = serverPy.readText()
            for (envKey in listOf("DEVICEGPT_BRIDGE_URL", "DEVICEGPT_BRIDGE_PIN")) {
                assertTrue(
                    "docs/ai-bridge-mcp-wrapper/server.py no longer reads $envKey — Android side emits it, so this is a silent break",
                    body.contains(envKey),
                )
            }
        }
    }

    @Test fun `the AI onboarding prompt names the same devicegpt_ tools the wrapper exposes`() {
        // Onboarding prompt tells the AI to look for tools named devicegpt_*.
        // If the wrapper renames a tool (e.g. devicegpt_capture_photo → devicegpt_photo),
        // the AI's "STEP 1 — WHICH MODE?" check silently falls through to snapshot mode
        // even in a Live setup.
        val prompt = File("docs/ai-bridge-mcp-wrapper/AI_ONBOARDING_PROMPT.md")
        val serverPy = File("docs/ai-bridge-mcp-wrapper/server.py")
        if (!prompt.exists() || !serverPy.exists()) return

        val promptText = prompt.readText()
        // Tools named explicitly in the prompt's ACTION TOOLS block:
        val explicitlyNamedInPrompt = Regex("devicegpt_[a-z_]+")
            .findAll(promptText).map { it.value }.toSet()

        val serverText = serverPy.readText()
        for (tool in explicitlyNamedInPrompt) {
            assertTrue(
                "AI_ONBOARDING_PROMPT.md names '$tool' but server.py does not register it — the AI will call a nonexistent tool",
                serverText.contains(tool),
            )
        }
    }
}
