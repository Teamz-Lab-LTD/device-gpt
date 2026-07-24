# DeviceGPT AI Bridge — MCP wrapper (laptop side)

Lets an AI on your laptop (Claude Desktop, ChatGPT Desktop, Cursor, or any
MCP-compatible tool) read live info from the DeviceGPT Android app on your
phone. Same-WiFi only. Read-only + a couple of trivial writes (flashlight,
launch an app). Cannot tap, type, or take screenshots — those need permissions
Google Play does not grant to normal apps.

## Setup (one-time)

1. Install Python 3.10+ if you don't already have it.
2. Install the wrapper's dependencies:

   ```bash
   pip install mcp httpx
   ```

3. Open the DeviceGPT app on your phone, go to the **AI Bridge** tab, and
   tap **Turn on AI Bridge**. Note the address and PIN shown on the phone —
   they change every session.

4. Point your MCP-compatible AI client at `server.py`. Example for Claude
   Desktop (edit `~/Library/Application Support/Claude/claude_desktop_config.json`
   on macOS, or the platform equivalent):

   ```json
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
   ```

5. Restart your AI client. Ask something like:
   > "What is the battery like on my phone?"

   The client will call `devicegpt_battery` and read back the live percent,
   temperature, and charging state.

## Available tools

Read-only:

- `devicegpt_device_info` — model, manufacturer, Android version, hardware
- `devicegpt_battery` — percent, charging state, temperature, voltage, health
- `devicegpt_storage` — internal-storage total and free byte counts
- `devicegpt_network` — LAN IPv4, WiFi/cellular state, internet reachability
- `devicegpt_cameras` — lens facing, focal lengths, flash availability
- `devicegpt_sensors` — installed sensors with vendor + type
- `devicegpt_apps` — launchable apps (package name + display label)

Trivial writes:

- `devicegpt_flashlight { on: true|false }` — back-camera torch on/off
- `devicegpt_launch_app { package: "com.example" }` — start an installed app

## What this cannot do (by design)

- Simulate taps or typing on the phone
- Take screenshots or record the screen
- Read notifications, SMS, contacts, call log
- Change any system setting (WiFi, brightness, do-not-disturb, etc.)

These require `AccessibilityService` or system-level permissions Google Play
does not grant to a normal app. For that class of automation, the honest
alternative is ADB from your laptop — see the plan doc for details.

## Troubleshooting

- **"Connection refused"** — the AI Bridge tab is off, or you're on a
  different WiFi. Confirm both devices show the same WiFi name.
- **"HTTP 401"** — PIN mismatch. The PIN rotates every time the Bridge is
  turned on. Update the value in your MCP client config and restart it.
- **"HTTP 403"** — you're reaching the phone from an address outside the
  local network range. Usually a hotspot with client isolation. Switch to a
  home / office WiFi.
- **The Bridge turned itself off** — that's the 10-minute idle-shutdown.
  It's expected. Turn it back on when you need it.
