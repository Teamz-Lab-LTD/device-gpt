package com.teamz.lab.debugger.quality

import com.teamz.lab.debugger.ui.buildMcpJsonBlock
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the JSON block the user copy-pastes into their AI client's config.
 *
 * If this block is malformed, the user's Claude Desktop / Cursor / ChatGPT
 * silently ignores the MCP server on startup and the user thinks "the bridge
 * is broken" — with no way to know it was actually the config file the app
 * generated for them.
 *
 * Also guards against injection: URL/PIN come from BridgeService state, but a
 * future change that reads them from user input MUST NOT let a malicious value
 * escape the JSON string context. Test with a quote-heavy PIN just in case.
 *
 * RobolectricTestRunner is required only for org.json.JSONObject — buildMcpJsonBlock
 * itself is a pure string function, but we parse its output through Android's
 * JSON stack to catch malformed output the same way an MCP client would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BuildMcpJsonBlockTest {

    @Test fun `output parses as valid JSON`() {
        val out = buildMcpJsonBlock("http://192.168.0.127:8787", "552946")
        // If this throws, the string is not valid JSON and every AI client will
        // reject the config without an error message the user can act on.
        val parsed = JSONObject(out)
        assertTrue("missing mcpServers root object", parsed.has("mcpServers"))
    }

    @Test fun `output has the exact devicegpt-bridge server key MCP clients expect`() {
        val json = JSONObject(buildMcpJsonBlock("http://x", "111111"))
        val servers = json.getJSONObject("mcpServers")
        assertTrue("server key must be 'devicegpt-bridge' — clients index by this name", servers.has("devicegpt-bridge"))

        val bridge = servers.getJSONObject("devicegpt-bridge")
        assertTrue(bridge.has("command"))
        assertTrue(bridge.has("args"))
        assertTrue(bridge.has("env"))
    }

    @Test fun `URL and PIN are embedded in the env object under the expected keys`() {
        val url = "http://10.0.0.42:8787"
        val pin = "998877"
        val json = JSONObject(buildMcpJsonBlock(url, pin))
        val env = json.getJSONObject("mcpServers").getJSONObject("devicegpt-bridge").getJSONObject("env")
        // These two env keys are what docs/ai-bridge-mcp-wrapper/server.py reads at startup.
        // Renaming either half of this pair silently breaks every user's setup.
        assertEquals(url, env.getString("DEVICEGPT_BRIDGE_URL"))
        assertEquals(pin, env.getString("DEVICEGPT_BRIDGE_PIN"))
    }

    @Test fun `python interpreter default is python3, overridable`() {
        val defaulted = JSONObject(buildMcpJsonBlock("http://x", "111111"))
        val overridden = JSONObject(buildMcpJsonBlock("http://x", "111111", python = "python3.12"))
        assertEquals("python3", defaulted.getJSONObject("mcpServers").getJSONObject("devicegpt-bridge").getString("command"))
        assertEquals("python3.12", overridden.getJSONObject("mcpServers").getJSONObject("devicegpt-bridge").getString("command"))
    }

    @Test fun `args points to a placeholder path the user is expected to replace`() {
        val json = JSONObject(buildMcpJsonBlock("http://x", "111111"))
        val args = json.getJSONObject("mcpServers").getJSONObject("devicegpt-bridge").getJSONArray("args")
        assertEquals(1, args.length())
        val path = args.getString(0)
        // Must not accidentally point at a dev-only absolute path — the placeholder
        // is what tells the user "you have to change this."
        assertTrue("args must contain 'server.py': got '$path'", path.contains("server.py"))
        assertTrue("args should carry a 'replace me' signal — currently '/absolute/path/'", path.contains("/absolute/path/") || path.contains("<"))
    }

    @Test fun `edge case — special chars in PIN do not corrupt the JSON`() {
        // Not currently possible (PIN is always [0-9]{6}), but a future refactor
        // that starts using an alphanumeric PIN or a user-typed password would
        // regress this. Just prove the output is still parseable JSON even under
        // adversarial input — no injection outbreak.
        val hostile = "\"broken\\injection"
        // Should throw at parse time if the string is not properly escaped.
        val json = JSONObject(buildMcpJsonBlock("http://x", hostile))
        val readBack = json.getJSONObject("mcpServers").getJSONObject("devicegpt-bridge").getJSONObject("env").getString("DEVICEGPT_BRIDGE_PIN")
        assertEquals(
            "PIN was corrupted through JSON round-trip — an attacker-controlled PIN could break the config file",
            hostile,
            readBack,
        )
    }
}
