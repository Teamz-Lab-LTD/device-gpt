# DeviceGPT AI Bridge — laptop setup

Connect a laptop AI (Claude Desktop, Cursor, ChatGPT Desktop, or any
MCP-compatible tool) to the **AI Bridge** tab in the DeviceGPT Android
app. Same WiFi only. Read-only + a couple of trivial actions.

**You will end up with:** an AI on your laptop that can answer "what's
my battery?" / "which lenses does my back camera have?" / "turn on my
flashlight" by talking to your phone live.

**Time to first working call:** ~5 minutes on your first phone, ~1
minute after that.

---

## Before you start

You need:

| # | Thing | How to check |
|---|-------|--------------|
| 1 | Phone and laptop on the same WiFi | Same network name in WiFi settings on both |
| 2 | Python 3.10 or newer on the laptop | `python3 --version` in a terminal |
| 3 | The DeviceGPT app installed and updated | Open the app, look for the **AI Bridge** tab |
| 4 | An AI client that supports MCP | Claude Desktop, Cursor, or ChatGPT Desktop (paid) |

If (1) is on a public / café / hotel WiFi, this will not work — those
networks block phone-to-laptop traffic. Use home or office WiFi.

---

## Step 1 — Get the wrapper on your laptop

Open a terminal on the laptop and run:

```bash
pip install mcp httpx
```

Save `server.py` from this folder somewhere permanent, for example:

```bash
mkdir -p ~/devicegpt-bridge
curl -o ~/devicegpt-bridge/server.py https://raw.githubusercontent.com/YOUR_ORG/debugger/main/docs/ai-bridge-mcp-wrapper/server.py
```

Or just copy the `server.py` file next to this README into
`~/devicegpt-bridge/server.py` by hand. Remember the full path — you
paste it into your AI client's config next.

---

## Step 2 — Turn on the Bridge on your phone

1. Open **DeviceGPT** on your phone.
2. Go to the **AI Bridge** tab (icon: a small router / plug).
3. Tap **Turn on AI Bridge**.
4. The screen shows two things — **write them down**, you need both:
   - **Address**: something like `http://192.168.1.42:8787`
   - **PIN**: a fresh 6-digit code, e.g. `483920`

Both **change every time you turn the Bridge on**. The AI Bridge also
turns itself off after 10 minutes of no request — turn it back on if
the AI says "connection refused".

---

## Step 3 — Point your AI client at the wrapper

Pick your client below. Each section is self-contained — you only need
the one for your tool.

### Claude Desktop (macOS)

1. Open Finder → press **⇧⌘G** → paste this path:
   ```
   ~/Library/Application Support/Claude/
   ```
2. Open `claude_desktop_config.json` in any text editor.
   If the file does not exist, create it.
3. Paste this block. Replace the two `PASTE_` values with what the
   phone showed you in Step 2, and update the `args` path to where
   you saved `server.py`:

   ```json
   {
     "mcpServers": {
       "devicegpt-bridge": {
         "command": "python3",
         "args": ["/Users/YOU/devicegpt-bridge/server.py"],
         "env": {
           "DEVICEGPT_BRIDGE_URL": "PASTE_ADDRESS_HERE",
           "DEVICEGPT_BRIDGE_PIN": "PASTE_PIN_HERE"
         }
       }
     }
   }
   ```

4. **Fully quit Claude** (⌘Q — not just close the window) and reopen it.
5. Start a **new conversation**. In the "🔌" or "Tools" area you
   should now see `devicegpt-bridge` listed.

### Claude Desktop (Windows)

1. Open Explorer → paste in the address bar:
   ```
   %APPDATA%\Claude\
   ```
2. Open `claude_desktop_config.json` (create it if missing).
3. Paste — replace paths with your real ones, use **double
   backslashes** in Windows paths:

   ```json
   {
     "mcpServers": {
       "devicegpt-bridge": {
         "command": "python",
         "args": ["C:\\Users\\YOU\\devicegpt-bridge\\server.py"],
         "env": {
           "DEVICEGPT_BRIDGE_URL": "PASTE_ADDRESS_HERE",
           "DEVICEGPT_BRIDGE_PIN": "PASTE_PIN_HERE"
         }
       }
     }
   }
   ```

4. Right-click the Claude tray icon → **Quit**. Reopen Claude.
5. Start a **new conversation**.

### Claude Desktop (Linux)

Config lives at `~/.config/Claude/claude_desktop_config.json`. Format
is the same as macOS. Fully quit and reopen after editing.

### Cursor

1. In Cursor: **Cmd/Ctrl + Shift + P** → search "**Open MCP
   settings**", or open `~/.cursor/mcp.json` directly.
2. Add the same block as Claude Desktop — Cursor uses the same shape:

   ```json
   {
     "mcpServers": {
       "devicegpt-bridge": {
         "command": "python3",
         "args": ["/Users/YOU/devicegpt-bridge/server.py"],
         "env": {
           "DEVICEGPT_BRIDGE_URL": "PASTE_ADDRESS_HERE",
           "DEVICEGPT_BRIDGE_PIN": "PASTE_PIN_HERE"
         }
       }
     }
   }
   ```

3. Reload Cursor: **Cmd/Ctrl + Shift + P** → "**Developer: Reload
   Window**".

### ChatGPT Desktop (with MCP add-on)

ChatGPT Desktop supports MCP via the same `mcpServers` shape. Config
path varies by version — check ChatGPT's settings for "Model Context
Protocol" or "External tools". Paste the same JSON block. Restart.

### Any other MCP-compatible tool

The wrapper speaks standard MCP over stdio. Any client that lets you
declare a local MCP server with a `command`, `args`, and `env` will
work with the exact same block above.

---

## Step 4 — Verify it works

Once your AI client restarts and you start a new conversation, open
[`AI_ONBOARDING_PROMPT.md`](AI_ONBOARDING_PROMPT.md) and paste the
block inside as your **first message** to the AI.

That prompt does two useful things:

1. Tells the AI to quietly call `devicegpt_device_info` once — if
   that works, the setup is 100% correct.
2. Teaches the AI to reply in plain first-grade English, ask before
   any action, and never invent a value.

After that, you can just ask things like:

- *"How's my battery right now?"*
- *"How much free space is on my phone?"*
- *"Turn my flashlight on for a moment."*
- *"Open Settings on my phone."*

---

## Available tools

The wrapper exposes 9 tools. You don't need to remember these names —
the AI picks them from your question.

| Tool | What it reads / does | Type |
|---|---|---|
| `devicegpt_device_info` | Model, manufacturer, Android version, SoC | Read |
| `devicegpt_battery` | Percent, temperature, voltage, charging, health | Read |
| `devicegpt_storage` | Internal storage total / free bytes | Read |
| `devicegpt_network` | LAN IP, WiFi / cellular state, internet reachability | Read |
| `devicegpt_cameras` | Lens facing, focal lengths, flash availability *(does NOT rate quality)* | Read |
| `devicegpt_sensors` | Installed sensors with vendor + type | Read |
| `devicegpt_apps` | Launchable apps: package name + display label | Read |
| `devicegpt_flashlight` | Back-camera torch on / off | Trivial write |
| `devicegpt_launch_app` | Start an installed app by package name | Trivial write |

---

## What this bridge cannot do

Not a limitation — a policy line. `AccessibilityService`-gated
capabilities are not granted to phone-diagnostic apps on Google Play,
and pretending otherwise would get the app removed.

- ❌ Simulate taps, swipes, or typing on the phone
- ❌ Take screenshots or record the screen
- ❌ Read notifications, SMS, contacts, call log, or other user data
- ❌ Change any system setting (WiFi, brightness, do-not-disturb…)
- ❌ Score, grade, or "measure" camera quality — the OS does not
  expose values that would let anyone do that honestly

For anything on that list, ADB from your laptop is the honest
alternative — different mental model, different setup.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| AI says **"connection refused"** | Bridge is off, or wrong WiFi | Turn AI Bridge back on. Check both devices show the same WiFi name. |
| AI says **HTTP 401** | PIN mismatch — the PIN rotated | Read the new PIN from the phone. Update `DEVICEGPT_BRIDGE_PIN` in config. Restart the AI client. |
| AI says **HTTP 403** | Your address is outside the local network range | Hotspot with "AP isolation" blocks phone↔laptop. Switch to home / office WiFi. |
| AI doesn't see the tools at all | Client cached the old config | Fully quit + reopen (not just close window). Then start a **new conversation** — MCP attaches at conversation start. |
| Bridge silently turned off | 10-minute idle-shutdown fired | Expected. Turn it back on when you need it. |
| Address changed since yesterday | Phone got a new LAN IP from the router | Update `DEVICEGPT_BRIDGE_URL` in config with the new address, restart client. |
| `pip install mcp` failed | Python < 3.10, or system pip | Use `python3 -m pip install --user mcp httpx`, or install Python 3.11+ from python.org. |

If none of the above match: run the wrapper directly to see the raw
error:

```bash
python3 ~/devicegpt-bridge/server.py --url "PASTE_ADDRESS" --pin "PASTE_PIN"
```

It will either sit and wait (good — MCP servers do that), or print
the exact error.

---

## Security notes

- The Bridge binds to `0.0.0.0` on port `8787` — anyone on your LAN
  who guesses the PIN could read your device state. The PIN is 6
  random digits (1 in 900,000) and rotates every session, so casual
  guessing takes years. If you're on shared / dorm WiFi, still turn
  the Bridge off when you're done.
- The server rejects any source IP outside RFC1918 + loopback ranges.
  Public-internet reachability is not possible without deliberate
  NAT setup — this is by design.
- No data leaves your LAN unless *you* type it into an AI client
  that itself sends things to a cloud. Whether it does that is up to
  which client you chose.
