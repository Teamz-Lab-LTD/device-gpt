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

## Fix 2 (next): the first scan does not count as a scan, and the scan button is hidden

- `FirstScanGate.markCompleted` never calls `HealthScoreUtils.saveHealthScore`. So seconds after
  scanning, the card says "not yet · 0 scans · Run a scan".
- The four FABs (PRO / Cert / AI / Send) sit on top of the card's "Scan Device Health" button.
- Right after "See details", a notification-permission pre-prompt opens before the user has
  seen the app. Its "Don't ask me again" row is clipped to 16px.

## Fix 3 (next): false ❌ / ⚠️ lines on the Security tab

The Security tab shows these lines to every user. They are the same false findings that fix 1
removed from the score:
- "❌ No admin set — features may be limited"
- "❌ Security shield is off" (when SELinux cannot be read)
- "⚠️ Modified system files found: /etc/hosts"

Fix 1 took them out of the score but not out of the display. They need copy that tells the
truth, and the owner has to approve that copy, because these lines are the policy surface.

## Measurement changes the owner should approve

1. **Register `score` / `sub_*` as GA4 event dimensions** (first_scan_completed). Today the score
   a user saw cannot be queried, so "did a low score predict uninstall" is unanswerable.
2. **Exclude robots from the fleet D1:** `mobileDeviceModel != (not set)` and city not in
   Calpella / Reston / Frankfurt am Main.

## Re-check

The day-0 `app_remove` share for humans in the cohort that installs the build carrying fix 1.
Today it is 49%. Read it once 50 or more humans have installed that build, about 3 weeks after
release.
