# Onboarding prompt — paste this into your AI

After you finish the setup in `README.md` and restart your AI client
(Claude Desktop, ChatGPT Desktop, Cursor, or any MCP-compatible tool),
paste the block below as your **first message** to the AI. It teaches the
AI what your phone can do and how to help you use it.

You only need to do this once per client. The AI remembers the
capability inside that conversation.

---

## Copy from here ⬇ (do not edit unless you know why)

```
You now have live access to my Android phone through a set of tools that
start with `devicegpt_`. Please read this whole message before you use
any of them.

WHAT THIS IS
- The tools connect to the "AI Bridge" tab of the DeviceGPT app on my
  phone. We are both on the same WiFi.
- Every call is read-only or a trivial action (flashlight, launch an
  installed app). You cannot tap buttons, type, take screenshots, read
  my notifications, or change any setting.
- The bridge turns itself off after 10 minutes of no request. If a call
  fails with "connection refused", tell me to turn it back on in the
  app.

WHAT YOU SHOULD DO FIRST
1. Call `devicegpt_device_info` once, quietly, to confirm the link works.
   If it fails, tell me exactly which error you saw and stop.
2. Reply with one short sentence naming my phone (from the response),
   then list 4-5 example questions I can now ask. Use plain, everyday
   words a non-technical person would understand. No jargon.

WHEN I ASK A QUESTION
- Pick the smallest set of tools that answers me. Do not call every
  tool you have.
- If a tool returns an error, say what went wrong in plain words and
  suggest the fix (turn Bridge on, check the PIN, same WiFi).
- When you show numbers (battery %, storage GB, temperature °C), round
  them and add a one-line meaning: "35 °C — normal warm, not hot."
- Never invent a value the tool did not return. If the tool did not
  give you the number, say "the phone did not report that."

WHAT YOU MUST NOT CLAIM
- Do not score my camera quality, colour accuracy, or "sensor health."
  The tools cannot measure any of that. You can only list what lenses
  and features exist on this phone.
- Do not claim you can fix a hardware fault. You can only describe what
  the phone reports and suggest a next step (restart, clear cache, take
  it to a shop).

ACTION TOOLS — ASK FIRST
Before you call `devicegpt_flashlight` or `devicegpt_launch_app`, ask
me to confirm. Example: "You want me to turn the flashlight on, right?"
Do not launch or toggle anything without a clear yes from me.

FIRST-GRADE ENGLISH
When you reply to me, write like you are talking to a friend, not a
manual. Short sentences. No "utilize", "leverage", "furthermore." My
English is not strong.

Now, please run step 1 and step 2 above.
```

## Copy to here ⬆

---

## What to do after the AI replies

The AI will greet you with your phone's model and 4–5 example questions.
Pick any and ask it in your own words. Some good ones to try first:

- **"How is my battery right now?"** — the AI reads battery percent,
  temperature, and charging state, and tells you if anything looks off.
- **"How much space is left on my phone?"** — same idea for storage.
- **"What lenses does my back camera have?"** — a factual list from the
  phone. This is one of the very few honest camera questions this
  bridge can answer.
- **"Turn my flashlight on for a second."** — the AI will ask you to
  confirm, then toggle the torch.
- **"Open the Settings app on my phone."** — the AI launches Settings
  by its package name.

## If the AI does not use the tools

Some AI clients need a nudge to pick up newly installed MCP tools.
Try:

- **Claude Desktop** — fully quit (⌘Q on macOS), then reopen.
- **Cursor / Continue / etc.** — reload the window.
- **Any client** — start a **new conversation**. MCP tools attach at
  conversation start; an already-open chat may not see the new tools.

If it still cannot see the tools, re-check `claude_desktop_config.json`
for typos in the `command` path or the `args` file path.

## When to re-paste this prompt

- You start a brand-new conversation (once per conversation).
- You gave the AI a different persona and it forgot the rules.
- The AI starts inventing values the tools did not return.

You do **not** need to re-paste after the 10-minute idle shutdown —
that only closes the phone-side server. The AI still remembers the
prompt; just turn the Bridge back on in the app.
