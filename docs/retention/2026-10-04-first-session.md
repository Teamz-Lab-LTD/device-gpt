# DeviceGPT first session: half of real users delete the app within 15 minutes

Written 2026-10-04. Branch `retention/daily-score-false-penalties`. **Not released.**

## The fleet verdict measures the wrong thing

The fleet verdict says "D1 6%, 52 uninstalls vs 50 installs". Two corrections change what to fix:

**1. About 35% of `first_open` comes from robots, not people.** On release days the extra installs
have device `(not set)`, or come from US Calpella / Reston and Frankfurt (Google test
datacenters). That is the Play pre-launch report, which runs on every upload. The owner's
Pixel 8a in Dhaka adds a few more. This explains the 2.42x gap the 2026-09-25 report could not
explain. With those removed:

| cohort (first_open) | humans | D1 | D3 | D7 |
|---|---|---|---|---|
| 2026-08-11 .. 09-23 (pre-vc46) | 118 | 10.2% (floor) to 13.6% | 1.7% | **0%** |
| 2026-09-24 .. 10-02 (vc48) | 47 | 10.6% (floor) to 14.9% | 5.4% | 0% (n=15) |

The "floor" puts every one of the 12 unfiltered returners over the human denominator. So real
D1 is around 10-15%, not 6%. **D7 is still zero.**

**2. The loss happens on day 0, not on day 1.**

| | pre-vc46 | vc48 |
|---|---|---|
| humans whose `app_remove` falls on day 0 | 58 / 118 (49%) | 23 / 47 (49%) |

Time from `first_open` to `app_remove`, Aug 11 to Sep 23, all traffic:
2 min: 12 · **5 min: 26** · **15 min: 40** · 1 h: 45 · 1 day: 52 · 7 days: 61.
That means 77% of same-day uninstalls happen within 15 minutes. 70% of those users finished
the first scan before they uninstalled.

## Ads, crashes, and the drop-off screen

- **Ads correlate with removal but do not explain it.** Before vc48, the share of users who
  uninstalled within 15 minutes was 54% among those who saw an app-open ad, 38% among those who
  saw any ad impression, and about 22% otherwise. On vc48, only 2 of 47 new humans saw an ad,
  and day-0 removal stayed at 49%.
- **Crashes do not explain it either.** Over 30 days, Crashlytics shows 2 users with a fatal
  crash (WorkManager `JobSchedulerExt`) and about 6 users with an ANR (3 × `J.N.V`, from Chromium
  native code). GA4 recorded zero `app_exception` events. Play vitals returns no rows because
  the app is below Play's user threshold.
- **The drop-off screen.** I confirmed this on a fresh install, using emulator 5560 because the
  Pixel was locked. About 5 seconds after the scan, tapping "See details" opens the Health tab.
  It says three contradictory things at once:
  1. "Your Device Score **97 Excellent**"
  2. "Daily Health Check **5/10 Fair. Some improvements needed**"
  3. "Scanned **not yet** · Best 0/10 · **0 scans** · Run a scan to see today's device state"

  A user who has just scanned reads this as a broken or fake app.

## Fix 1 (this branch): the daily score penalised phones with no faults

`HealthScoreUtils.calculateDailyHealthScore` scored by searching display text for words:
- Battery: it searched for `contains("good")` in the text `"Good ✅"`. The case does not match,
  so the check fell through to `else -> 1`, and every phone lost 1 point.
- Storage: the text never contains any of the words it searched for, so every phone lost 1 point.
- Security: any ❌ or ⚠️ anywhere in the Security text cost 2 points. Every phone hits one of
  these, because the text includes "❌ No admin set" (the normal state for a personal phone),
  SELinux (apps cannot read it), "❌ Unable to read mic/camera usage", and `/etc/hosts` flagged
  as a "modified system file" (that file exists on every Android phone).

So a phone with no faults scored **6/10 "Fair. Some improvements needed."** This is the
scareware pattern that Play's Deceptive Behavior policy targets, and the app already has one
strike.

The change: the score now reads structured values (`EXTRA_HEALTH`, `StatFs` free %, and
`storageEncryptionStatus` for security). A pure function, `scoreFromSignals`, holds all the
deductions. A value the app cannot read counts as unknown and costs nothing. Root, USB
debugging, thermal and RAM checks are unchanged.

- Tests: `DailyScoreFalsePenaltyTest`. It was red first: a device with no faults scored
  `expected:<10> but was:<6>`. It now passes, along with the full suite: **669 tests, 0 failed**.
- Device: fresh install of the debug build on emulator-5560. Before the fix the card showed
  "5/10 Fair". After the fix it shows **"9/10 Excellent! Your device is in top condition!"** The
  remaining −1 is real: USB debugging is on because adb is connected. No crash in logcat.
- **Still to do:** a run on the Pixel 8a. It was locked (fingerprint) during this session.

## Fix 2 (this branch): the first scan did not count, and a permission dialog came first

- `7250aff`: the gate now runs the daily scan alongside the quick scan, and waits for it before
  showing the score. On the emulator, the card a new user lands on now reads "Today's Health
  Score 9/10 · Scanned 1 day · Best 9/10 · 1 scan". Before, it read "not yet · 0 scans · Run a
  scan". The score still appears about 2 seconds after launch.
- `d390d22`: "Allow Realtime Monitor" used to open on every cold start for Android 13+ users,
  including the first landing. It now waits until the install is 24 hours old. Waiting costs
  little: users who got the monitor in their first 15 minutes uninstalled within 15 minutes at
  25%, against 22% overall. The "Don't ask me again" checkbox was squeezed to 16px; it now sits
  in the dialog body. On device, ticking it and pressing Cancel keeps the dialog from returning.
- Tests: 7 new ones, each red before its fix. Full suite: **676 tests, 0 failed**.
- **Dropped: the FABs do not actually hide the scan button.** The list already has 120dp of
  bottom padding, so the button scrolls clear of the FABs. On first landing it just sits at the
  bottom edge. Four pulsing FABs on every tab is a design decision, not a bug.
- **Found, not fixed:** in session 1, tapping the drawer's "Realtime" toggle opens a
  full-screen "Not ready to pay? Invite friends" screen. The monitor sits behind the
  paywall/referral reroute. GA4 shows `referral_fallback_shown` for 13 of 47 vc48 humans.
  Whether that belongs in session 1 is a monetisation call.
- **Not verified on the Pixel 8a:** it was locked, then unplugged. Not verified on Android 12 or
  lower either; the dialog is only reached on Android 13+.

## Fix 3 (this branch, `242ec48`): the app told healthy phones they were at risk

Before the fix, an Android 15 emulator with no faults showed this under Health → Smart
Recommendations: "🛡️ Your phone's security system is turned off - this is unusual and risky ·
📞 Contact your phone's customer support". It sat directly above "🌟 Great job! Your phone is in
excellent shape". This is the pattern Play's Deceptive Behavior policy targets.

Each alarm came from a check that could not see, a normal state reported as a fault, or a
placeholder:

| line | why it was false | now |
|---|---|---|
| ❌ Security shield is off | apps get `getenforce: Permission denied` (confirmed with `run-as`) | ℹ️ Not checked. "Permissive" still ❌ |
| ❌ No admin set → advice: "go to Device admin apps to fix this" | no device admin is the normal state; the advice told users to grant admin access to an app | ✅ none / ℹ️ lists them by name |
| ⚠️ Modified system files: /etc/hosts | the file ships on every Android device | path removed |
| 👣 "Your phone moved while locked — possible snooping" | `/sys/class/input` exists on every phone; the code said "Simulated for illustration" | check, advice and AI-prompt claim removed |
| ✅ Clipboard "auto-clears (Android 11+)" | the protection is Android 10's background-read block; Android 11 does not auto-clear the clipboard | corrected |
| "Malware Scan (Offline Check)", "Spy Detection" | the app does no malware or spy detection | renamed |

Zero Trust used to give an unreadable SELinux status a WARNING, with "contact your device
manufacturer". It now leaves that check out and scores the section over the checks that ran.
When all 7 run, nothing changes, because their weights add up to 100.

The Suggestions `when` block shows only its first match. So these had to change together:
fixing one would have surfaced the next false branch.

- Tests: `SecurityFalseAlarmTest`. All 3 behaviour tests failed on the old code: `/etc/hosts`
  was flagged, "No admin set" was in the text, and the suggestions contained alarms. Full suite:
  **684 tests, 0 failed**.
- Device: the suggestions now open with "Great job!" and contain no alarm. The Security section
  reads ℹ️ Not checked / ✅ No apps have device admin access / ✅ System files look clean /
  📦 App Install Sources.
- **Not verified on device:** the Zero Trust per-check rows. The session-1 paywall loop (below)
  kept taking over the screen. The dashboard itself rendered (72/100) without a crash.
- **The owner should review the wording.** These lines are the policy surface.
- **Same class, not fixed:** `getRecentCameraMicUsageLog()` runs `logcat -d`. An app can only
  read its own log, so the result "✅ No recent mic or camera access" is false reassurance.
  "Recent usage detected" can fire from DeviceGPT's own mic test, and it feeds
  `calculatePrivacyScore` and `getPrivacyThreatsToday`.

## Fix 4 (this branch): the paywall loop

- `e429a2d`: when the RevenueCat offering failed to load, timed out, had no packages, or the SDK
  was not configured, `RevenueCatPaywall` called the same `onDismiss` as a user closing the
  paywall. So the user got "Not ready to pay?" for a price they never saw, then a survey about a
  paywall they never closed. That is the loop seen on the emulator. A new `onUnavailable` path
  now ends the chain after the "premium unavailable" toast.
- `e429a2d`: "Quick — why did you close?" now appears once per install, not after every journey.
  On vc48, 20 of 22 day-one survey events were `sheet_dismissed` or `no_response`.
- `f278e2f`: the bundled `paywall_delay_enabled` is now `true`. On a cold emulator start, RC took
  **31 seconds** to activate, which is longer than the 20-second fallback delay. With the old
  `false` default, a fresh install could get the cold paywall in session one.
- Tests: 4 of 4 contract tests failed on the old code. Full suite: **689 tests, 0 failed**.
- Device: session one is now blocked (`paywall_cold_gate_blocked {session_count=1}`). On the
  first journey, the survey appeared once; on the second, "Maybe later" ended the chain. The
  offline failure path could not be forced on the emulator, because RevenueCat served a cached
  offering. The contract test covers it.
- **Correction to the paragraph below:** `applied=false` came from a debug run before RC had
  fetched. With production RC loaded, `paywall_rerouted` logs `applied=true`, so production
  does apply the 7-day cooldown.

### Fixed and published 2026-10-04 21:24 UTC: the live RevenueCat paywall's own claims

The owner approved it. Paywall `pw24cbb6af3f704532` ("device-gpt", offering `device-gpt-offering`,
shared project `proj8d8322e7`) went from revision 93 to revision 96:
- removed the "4.8 stars · ★★★★★ · 60+ reviews" block, laurel images included;
- the headline now uses the store's `{{ product.price }}` instead of a hand-typed "$2.99";
- removed "Faster app performance".

I compared the draft with the published version line by line before publishing. Nothing else
changed: the button, "Restore purchases", the package binding and the other three benefits are
the same. On a fresh install on the emulator, the paywall now reads "Remove Ads Forever · BDT
420.00 • Lifetime Access", and the button reads "Get Premium - BDT 420.00". To undo, restore
revision 93 in the RevenueCat dashboard.

What it showed before:

The paywall is designed in the RevenueCat dashboard, not in this repo. It shows:
- **"4.8 stars · 60+ reviews"**, but Play has 5 ratings averaging 3.0.
- **"$2.99 • Lifetime Access"**, directly above a button that charges **BDT 420.00**.
- "Faster app performance" as a premium benefit.
- An image that loads as an empty white box when the network is cold.

These are misleading-claim risks on an app that already has a Deceptive Behavior strike. The
change goes to a live, customer-facing surface, so the owner has to approve it before anyone
edits it.

### Also seen in session one

After "See details", the widget pin sheet opens; it reached 21 of 47 new vc48 users. Its preview
says "🔥 -- days" (a streak) and "Tap to fix issues →".

## Before fix 4: the first-session paywall loop

On a fresh debug install, logcat shows `paywall_fallback_triggered {session_count=1,
fallback_delay_ms=20000}`, meaning a paywall fires 20 seconds into the first session. After
that, the paywall, "Quick — why did you close?", and "Not ready to pay? Invite friends" cycle
into each other. Answering the survey led straight back to the referral screen. Every
`paywall_rerouted` event carries `action=COOLDOWN_7D, applied=false`.

The debug build may not have production's Remote Config: `coldTriggerAllowed` depends on an RC
flag and a minimum session count. But production GA4 shows the same pattern on vc48: **12 of 47
real users saw a paywall on day 0**, and 13 saw the referral screen **38 times**.

This is the strongest remaining first-session suspect. It is a monetisation decision, so it is
the owner's call.

## Measurement changes the owner should approve

1. **Register `score` / `sub_*` as GA4 event dimensions** (first_scan_completed). Today the score
   a user saw cannot be queried, so "did a low score predict uninstall" is unanswerable.
2. **Exclude robots from the fleet D1:** `mobileDeviceModel != (not set)` and city not in
   Calpella / Reston / Frankfurt am Main.

## Re-check

The day-0 `app_remove` share for humans in the cohort that installs the build carrying fix 1.
Today it is 49%. Read it once 50 or more humans have installed that build, about 3 weeks after
release.
