# DeviceGPT user research: why people don't stay, rate or buy (2026-10-09)

Method: behavioural analytics. There were no interviews. GA4 481224245, 90 days to 2026-10-08,
robots excluded: device `(not set)`, Calpella/Reston/Frankfurt, `sdk_gphone64_arm64`,
`OnePlus8Pro`, and the owner's Pixel 8a. Also used: AdMob API, RevenueCat, Play bulk reports, and
`docs/CHURN-DIAGNOSIS-2026-10-07.md`, whose retention findings this builds on rather than repeats.
240 new human users.

## Themes

**1. A one-time tool, sold with one-time search terms.** [Likely] (2026-10-07 diagnosis)
- The median uninstall comes 4 minutes after install; 56% uninstall on day 0.
- Session 1 looks the same for people who stay and people who leave, and the keepers don't come
  back (4/19 had a second day).
- The title sells "Camera Test & Mic", but the app opens on a health score. Only 2 of 43 started
  the mic test.
- Not fixed yet. This is the biggest lever: see "Next".

**2. Every "wait until the user settles in" rule fired within the first hour.** [Certain, code]
- A "session" is MainActivity.onCreate at least 60 s apart, not a day. Full-screen ads (grace N
  sessions), the cold paywall (≥3 sessions) and the review prompt all used it.
- 3/3 humans who saw a day-0 full-screen ad uninstalled that day.
- Fixed in vc52: QuietPeriod. For the first 72 h by install age there are no full-screen ads
  (none shown, none requested) and no paywall the user didn't ask for.

**3. Paywall saturation, mostly unasked-for, often where nobody can pay.** [Certain, GA4]
- 206/240 new users (86%) saw a paywall, 1,315 times, about 6.4 each.
- Top sources: `smart_session_trigger` 211; review→paywall chain 143 (off since RC v19);
  "not ready to pay? invite friends" follow-ups about 330; the first scan 66 (ungated on day 0).
- Intent sources (AI, Leaderboard, PRO, report, locked section) were the minority.
- Iran had 46 viewers and 470 shows. Google Play billing does not work there.
- Purchases: 31–60 showed intent, 6 started a purchase, 2 completed. Both completions were in
  Denmark and the US, and both started from intent.
- Dismiss reasons: mostly closed with no answer; "too expensive" was 11 users.
- Fixed in vc52: no unasked-for paywall in the quiet period or in IR/CU/KP/SY. The first-scan
  paywall is gated.

**4. Ratings: asked early, then chained into a paywall, with few people left to ask.** [Certain]
- 308 asks / 268 users. 151 of them were on install day, mostly on builds before the
  2026-09-24 gate fix.
- About 5 ratings arrived in Aug–Sep.
- The review flow opened a paywall straight after it, even when Play showed no dialog.
- Fixed: 24 h gate (since 3.1.27); review→paywall chain off (RC v19, 2026-10-08).
- What's left is theme 1: few users reach day 2.

**5. A background monitor nobody asked for.** [Certain, code + GA4]
- It started itself for 48% of new users; only 5 chose it.
- On vc50 it used about 1.4 GB/h.
- vc51 throttled it. vc52 starts it only on the user's opt-in.

**6. Ads earned £0.** [Certain, AdMob]
- After vc51, native ads loaded 61/day, 100% filled, with 0 impressions: the first slot was two
  screens down.
- vc52 moves it to the first screen.
- Full-screen ads had about 0 impressions even before RC v17.

## Impact × effort
| Fix | Impact | Effort | Status |
|---|---|---|---|
| Quiet first 72 h (ads + unsolicited paywalls) | day-0 churn, reviews | S | vc52 |
| Monitor only on opt-in | data/battery, day 1–7 churn | S | vc52 |
| No paywall where billing doesn't work | annoyance, review risk | S | vc52 |
| Native ad on the first screen | revenue (£3–5/mo at 20 DAU) | S | vc52 |
| First screen matches the store promise (Camera / Mic / Screen chooser), then the widget habit | the main churn cause | M | next: design first, run as an RC A/B |

## Measure (per build, robots excluded, event-level matching)
- Day-0 removal share (baseline 56%).
- `premium_paywall_shown` per new user in the first 72 h: target 0 unsolicited.
- `AdActivity` screen views at install age under 72 h: target 0.
- `realtime_monitor_started` with no toggle: target 0.
- Native impressions per day (AdMob): target above 0.
