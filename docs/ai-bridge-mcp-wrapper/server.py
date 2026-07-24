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
    from mcp.types import TextContent, Tool
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
    return {"ok": False, "error": f"unknown tool: {tool_name}"}


async def main() -> None:
    server: Server = Server("devicegpt-bridge")

    @server.list_tools()
    async def _list_tools() -> list[Tool]:
        return TOOLS

    @server.call_tool()
    async def _call_tool(name: str, arguments: dict[str, Any] | None) -> list[TextContent]:
        try:
            result = await dispatch(name, arguments or {})
        except httpx.HTTPStatusError as exc:
            result = {"ok": False, "error": f"HTTP {exc.response.status_code}", "body": exc.response.text}
        except Exception as exc:  # pragma: no cover — surface every failure to the client
            result = {"ok": False, "error": str(exc)}
        return [TextContent(type="text", text=json.dumps(result, indent=2))]

    async with stdio_server() as (read_stream, write_stream):
        await server.run(read_stream, write_stream, server.create_initialization_options())


if __name__ == "__main__":
    asyncio.run(main())
