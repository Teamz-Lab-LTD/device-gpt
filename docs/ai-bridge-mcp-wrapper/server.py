#!/usr/bin/env python3
"""
DeviceGPT AI Bridge — MCP wrapper.

Runs on the user's laptop. Talks HTTP to the phone-side Bridge (see the DeviceGPT
Android app's "AI Bridge" tab), and exposes each endpoint as an MCP tool so any
MCP-compatible client (Claude Desktop, ChatGPT Desktop, Cursor, etc.) can drive it.

Usage:
    python3 server.py --url http://192.168.1.42:8787 --pin 483920

Or configure via env vars:
    DEVICEGPT_BRIDGE_URL=http://192.168.1.42:8787
    DEVICEGPT_BRIDGE_PIN=483920

MCP-client integration example (Claude Desktop `claude_desktop_config.json`):

    {
      "mcpServers": {
        "devicegpt-bridge": {
          "command": "python3",
          "args": ["/absolute/path/to/server.py"],
          "env": {
            "DEVICEGPT_BRIDGE_URL": "http://192.168.1.42:8787",
            "DEVICEGPT_BRIDGE_PIN": "483920"
          }
        }
      }
    }

Dependencies (install once):
    pip install mcp httpx
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
from typing import Any

try:
    import httpx
    from mcp.server import Server
    from mcp.server.stdio import stdio_server
    from mcp.types import ImageContent, TextContent, Tool
except ImportError as exc:  # pragma: no cover
    print(
        "Missing dependency. Install with: pip install mcp httpx\n"
        f"  Original error: {exc}",
        file=sys.stderr,
    )
    sys.exit(1)


BRIDGE_URL_ENV = "DEVICEGPT_BRIDGE_URL"
BRIDGE_PIN_ENV = "DEVICEGPT_BRIDGE_PIN"
DEFAULT_TIMEOUT_SECONDS = 10.0


def resolve_config() -> tuple[str, str]:
    parser = argparse.ArgumentParser(description="DeviceGPT AI Bridge — MCP wrapper")
    parser.add_argument("--url", default=os.environ.get(BRIDGE_URL_ENV, ""))
    parser.add_argument("--pin", default=os.environ.get(BRIDGE_PIN_ENV, ""))
    args = parser.parse_args()
    if not args.url or not args.pin:
        parser.error(
            "Bridge URL and PIN required. Pass --url + --pin, or set "
            f"{BRIDGE_URL_ENV} + {BRIDGE_PIN_ENV} env vars."
        )
    return args.url.rstrip("/"), args.pin


BASE_URL, PIN = resolve_config()


def _headers() -> dict[str, str]:
    return {"X-Bridge-Pin": PIN, "Content-Type": "application/json"}


async def _get(path: str) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=DEFAULT_TIMEOUT_SECONDS) as client:
        resp = await client.get(f"{BASE_URL}{path}", headers=_headers())
    resp.raise_for_status()
    return resp.json()


async def _post(path: str, body: dict[str, Any]) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=DEFAULT_TIMEOUT_SECONDS) as client:
        resp = await client.post(f"{BASE_URL}{path}", headers=_headers(), json=body)
    resp.raise_for_status()
    return resp.json()


TOOLS: list[Tool] = [
    Tool(
        name="devicegpt_device_info",
        description="Read this phone's model, manufacturer, Android version, and hardware identifiers.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_battery",
        description="Read this phone's live battery status: percent, charging state, temperature, voltage, health.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_storage",
        description="Read this phone's internal-storage total and free byte counts.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_network",
        description="Read this phone's LAN IPv4, WiFi/cellular state, and whether it has a validated internet connection.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_cameras",
        description="List this phone's cameras with facing, focal lengths, and flash availability. Does NOT rate camera quality — that is not measurable via the OS.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_sensors",
        description="List this phone's hardware sensors with vendor, type, and typical power draw.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_apps",
        description="List launchable apps installed on this phone (package name + display label).",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_flashlight",
        description="Turn this phone's back-camera flashlight on or off.",
        inputSchema={
            "type": "object",
            "properties": {"on": {"type": "boolean", "description": "True to turn on, false to turn off"}},
            "required": ["on"],
        },
    ),
    Tool(
        name="devicegpt_launch_app",
        description="Launch an app on this phone by package name (e.g. com.google.android.calculator).",
        inputSchema={
            "type": "object",
            "properties": {"package": {"type": "string", "description": "Android package name of the app to launch"}},
            "required": ["package"],
        },
    ),

    # ─────────────── Phase A — safe info reads ───────────────
    Tool(
        name="devicegpt_sensors_snapshot",
        description="One-shot current readings of the 6 canonical sensors (accelerometer, gyroscope, magnetometer, ambient light, proximity, pressure). Values are the raw sensor units; missing sensors report null.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_wifi_scan",
        description="List nearby WiFi networks (cached scan, does not trigger a new scan): SSID, BSSID, signal strength, frequency, capabilities. Also reports the currently connected SSID and link speed. Requires location permission on the phone.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_permissions_status",
        description="List all Android permissions the DeviceGPT app declares, with their current runtime grant state (granted/denied). Useful for diagnosing why a scan or action returns 'permission required'.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_cpu_info",
        description="Report CPU core count and per-core current/max/min frequency in kHz. Some cores may report null if cpufreq files are permission-denied by the OEM kernel.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_thermal_state",
        description="Report the device thermal throttling status (none/light/moderate/severe/critical/emergency/shutdown), power-save mode, and doze/idle state. Requires Android 10+ for full data.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_memory_info",
        description="Report total RAM, currently available RAM, low-memory threshold, and whether the OS considers the device in low-memory state.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_uptime",
        description="Report how long since the phone was last rebooted (seconds since boot) and the build timestamp and fingerprint.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_screen_info",
        description="Report screen resolution, DPI, density scale, refresh rate, and current rotation.",
        inputSchema={"type": "object", "properties": {}},
    ),

    # ─────────────── Phase B — safe actions ───────────────
    Tool(
        name="devicegpt_notify",
        description="Post a local notification on this phone from the AI. Uses a separate notification channel so the user can mute AI-triggered notifications independently.",
        inputSchema={
            "type": "object",
            "properties": {
                "title": {"type": "string", "description": "Notification title"},
                "body": {"type": "string", "description": "Notification body text"},
            },
            "required": ["title"],
        },
    ),
    Tool(
        name="devicegpt_copy_to_clipboard",
        description="Copy a string to the phone's clipboard so the user can paste it into any app.",
        inputSchema={
            "type": "object",
            "properties": {"text": {"type": "string", "description": "Text to copy"}},
            "required": ["text"],
        },
    ),
    Tool(
        name="devicegpt_open_url",
        description="Open an http/https URL in the phone's default browser. Refuses non-http(s) schemes to prevent Intent-based deep-link abuse.",
        inputSchema={
            "type": "object",
            "properties": {"url": {"type": "string", "description": "Must start with http:// or https://"}},
            "required": ["url"],
        },
    ),
    Tool(
        name="devicegpt_share_text",
        description="Open the phone's system share chooser pre-filled with the given text.",
        inputSchema={
            "type": "object",
            "properties": {"text": {"type": "string", "description": "Text to share"}},
            "required": ["text"],
        },
    ),
    Tool(
        name="devicegpt_dial",
        description="Open the phone dialer pre-filled with a number. The user must still tap the call button — this does NOT auto-dial.",
        inputSchema={
            "type": "object",
            "properties": {"number": {"type": "string", "description": "Phone number, e.g. +14155551234"}},
            "required": ["number"],
        },
    ),

    # ─────────────── Phase C — auto-runnable tests ───────────────
    Tool(
        name="devicegpt_test_storage_write",
        description="Measure sustained sequential-write throughput to internal storage. Writes N MB (default 10, max 100) of random bytes, reports MB/s, deletes the file.",
        inputSchema={
            "type": "object",
            "properties": {"size_mb": {"type": "integer", "description": "Bytes to write, in MB (1-100)", "minimum": 1, "maximum": 100}},
        },
    ),
    Tool(
        name="devicegpt_test_storage_read_latency",
        description="Measure random-access read latency to internal storage. Writes a 4 MB probe file, then does N random 4 KB reads (default 200), reports median / p95 / max microseconds.",
        inputSchema={
            "type": "object",
            "properties": {"samples": {"type": "integer", "description": "Number of random reads (10-2000)", "minimum": 10, "maximum": 2000}},
        },
    ),
    Tool(
        name="devicegpt_test_dns_latency",
        description="Sequentially cold-resolve 5 well-known domains (google.com, cloudflare.com, wikipedia.org, github.com, apple.com), report per-domain elapsed ms and whether the resolve succeeded.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_test_network_speed",
        description="HTTP HEAD probe to Google's connectivity-check endpoint. Reports round-trip ms and HTTP status. Reachability + latency probe, NOT a bandwidth benchmark.",
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_test_camera_open",
        description=(
            "Open each camera ID and measure first-open time. Detects a physically dead / "
            "kernel-blocked camera. Does NOT capture a frame — a lens can open and still fail "
            "to stream (that check needs the user's eye).\n\n"
            "RESPONSE HANDLING RULES (follow these — the phone side already wrote the exact "
            "user-facing sentences, do NOT paraphrase or invent your own):\n"
            "  1. Top-level shape when the whole call is refused: "
            "{ok: false, error_code: string, user_message: string}. "
            "Relay `user_message` to the user VERBATIM, then stop. Do not retry.\n"
            "  2. Success shape: {results: [{camera_id, opened, open_ms, error_code, user_message}, ...]}.\n"
            "  3. For each result where `opened=true`: tell the user 'Camera <id> opened in <open_ms> ms'. "
            "That's it — no interpretation, no invented quality score.\n"
            "  4. For each result where `opened=false`: tell the user the value of `user_message` "
            "VERBATIM. It already contains the recovery step. Do not add your own recovery advice.\n"
            "  5. If `error_code` starts with `camera_disabled` — a policy blocked the open. "
            "The user_message names the specific fix (Quick Settings toggle, foreground the app, "
            "close another camera app). Trust it, don't guess."
        ),
        inputSchema={"type": "object", "properties": {}},
    ),
    Tool(
        name="devicegpt_capture_photo",
        description=(
            "Take ONE photo with the phone's camera and return the JPEG to you inline. "
            "The user must tap Allow on a consent dialog that pops up on the phone — nothing "
            "captures until they do. If they tap Deny (or take longer than 30 seconds), "
            "the call returns an error with a plain-language user_message.\n\n"
            "USE THIS WHEN the user asks you to see something, read something written down, "
            "identify an object in front of them, etc. — where you actually need to see the "
            "picture. Do NOT use it for random surveillance or repeated snapshots. Ask before "
            "you call it (the user tapping Allow is not consent that you can call it again).\n\n"
            "RESPONSE HANDLING RULES:\n"
            "  - On success you receive the actual JPEG inline; describe what you see the "
            "same way you would describe an uploaded image.\n"
            "  - On failure: relay `user_message` VERBATIM. Do not paraphrase, do not invent "
            "your own recovery step, do not retry silently. If error_code is `user_denied`, "
            "the user tapped Deny — accept it and stop.\n"
            "  - Default camera is the back one. Only pass `camera_id` if the user asks for "
            "the selfie / front camera (typically \"1\") or names a specific ID from a prior "
            "`devicegpt_cameras` call."
        ),
        inputSchema={
            "type": "object",
            "properties": {
                "camera_id": {
                    "type": "string",
                    "description": "Optional. Camera ID from devicegpt_cameras. Defaults to the first back-facing lens.",
                }
            },
        },
    ),
    Tool(
        name="devicegpt_test_battery_drain_rate",
        description="Sample battery current draw over 2 seconds. Reports raw microamps at t=0 and t=+2s plus the mean absolute mA. Sign convention (positive vs negative for discharge) is OEM-defined and NOT normalised.",
        inputSchema={"type": "object", "properties": {}},
    ),
]


async def dispatch(tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
    if tool_name == "devicegpt_device_info":
        return await _get("/device")
    if tool_name == "devicegpt_battery":
        return await _get("/battery")
    if tool_name == "devicegpt_storage":
        return await _get("/storage")
    if tool_name == "devicegpt_network":
        return await _get("/network")
    if tool_name == "devicegpt_cameras":
        return await _get("/camera")
    if tool_name == "devicegpt_sensors":
        return await _get("/sensors")
    if tool_name == "devicegpt_apps":
        return await _get("/apps")
    if tool_name == "devicegpt_flashlight":
        return await _post("/flashlight", {"on": bool(arguments.get("on", False))})
    if tool_name == "devicegpt_launch_app":
        return await _post("/launch_app", {"package": arguments.get("package", "")})

    # Phase A — safe info reads
    if tool_name == "devicegpt_sensors_snapshot":
        return await _get("/sensors_snapshot")
    if tool_name == "devicegpt_wifi_scan":
        return await _get("/wifi_scan")
    if tool_name == "devicegpt_permissions_status":
        return await _get("/permissions_status")
    if tool_name == "devicegpt_cpu_info":
        return await _get("/cpu_info")
    if tool_name == "devicegpt_thermal_state":
        return await _get("/thermal_state")
    if tool_name == "devicegpt_memory_info":
        return await _get("/memory_info")
    if tool_name == "devicegpt_uptime":
        return await _get("/uptime")
    if tool_name == "devicegpt_screen_info":
        return await _get("/screen_info")

    # Phase B — safe actions
    if tool_name == "devicegpt_notify":
        return await _post("/notify", {"title": arguments.get("title", ""), "body": arguments.get("body", "")})
    if tool_name == "devicegpt_copy_to_clipboard":
        return await _post("/copy_to_clipboard", {"text": arguments.get("text", "")})
    if tool_name == "devicegpt_open_url":
        return await _post("/open_url", {"url": arguments.get("url", "")})
    if tool_name == "devicegpt_share_text":
        return await _post("/share_text", {"text": arguments.get("text", "")})
    if tool_name == "devicegpt_dial":
        return await _post("/dial", {"number": arguments.get("number", "")})

    # Phase C — auto-runnable tests
    if tool_name == "devicegpt_test_storage_write":
        body = {"size_mb": int(arguments["size_mb"])} if "size_mb" in arguments else {}
        return await _post("/test_storage_write", body)
    if tool_name == "devicegpt_test_storage_read_latency":
        body = {"samples": int(arguments["samples"])} if "samples" in arguments else {}
        return await _post("/test_storage_read_latency", body)
    if tool_name == "devicegpt_test_dns_latency":
        return await _post("/test_dns_latency", {})
    if tool_name == "devicegpt_test_network_speed":
        return await _post("/test_network_speed", {})
    if tool_name == "devicegpt_test_camera_open":
        return await _post("/test_camera_open", {})
    if tool_name == "devicegpt_test_battery_drain_rate":
        return await _post("/test_battery_drain_rate", {})

    # Phase D — consent-gated photo capture
    if tool_name == "devicegpt_capture_photo":
        body = {}
        if "camera_id" in arguments and arguments["camera_id"]:
            body["camera_id"] = str(arguments["camera_id"])
        # Longer timeout — the phone side waits up to 30s for the user's Allow tap.
        async with httpx.AsyncClient(timeout=45.0) as client:
            resp = await client.post(f"{BASE_URL}/capture_photo", headers=_headers(), json=body)
        resp.raise_for_status()
        return resp.json()

    return {"ok": False, "error": f"unknown tool: {tool_name}"}


async def main() -> None:
    server: Server = Server("devicegpt-bridge")

    @server.list_tools()
    async def _list_tools() -> list[Tool]:
        return TOOLS

    @server.call_tool()
    async def _call_tool(name: str, arguments: dict[str, Any] | None) -> list[Any]:
        try:
            result = await dispatch(name, arguments or {})
        except httpx.HTTPStatusError as exc:
            result = {"ok": False, "error": f"HTTP {exc.response.status_code}", "body": exc.response.text}
        except Exception as exc:  # pragma: no cover — surface every failure to the client
            result = {"ok": False, "error": str(exc)}

        # Special case: /capture_photo returns a JPEG the AI needs to actually SEE.
        # Peel `jpeg_base64` out of the JSON and ship it as ImageContent so Claude
        # renders it inline instead of getting a wall of base64 text.
        if name == "devicegpt_capture_photo" and isinstance(result, dict) and result.get("ok") and result.get("jpeg_base64"):
            meta = {k: v for k, v in result.items() if k != "jpeg_base64"}
            return [
                ImageContent(type="image", data=result["jpeg_base64"], mimeType="image/jpeg"),
                TextContent(type="text", text=json.dumps(meta, indent=2)),
            ]

        return [TextContent(type="text", text=json.dumps(result, indent=2))]

    async with stdio_server() as (read_stream, write_stream):
        await server.run(read_stream, write_stream, server.create_initialization_options())


if __name__ == "__main__":
    asyncio.run(main())
