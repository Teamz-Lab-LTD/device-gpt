# DeviceGPT churn diagnosis — 2026-10-07

Research only. No app code, store listing, Remote Config or Firebase setting was changed.

**The short answer:** most of the people who uninstall DeviceGPT do it **about 4 minutes after
installing it**. They have already done what they came for: scan the phone, test the camera, look
at the score. The people who keep it installed do almost the same things and do not come back
either. The main cause is not one broken screen. DeviceGPT is a one-time tool sold under one-time
search terms ("camera test", "mic test", "dead pixel test"), and nothing gives anyone a reason to
keep it. Two defects in the live build make this worse, and both can be fixed cheaply:
full-screen ads start within the first hour, and a background monitor uses mobile data.

The fleet verdict (51 installs vs 52 uninstalls, 28 days to 2026-09-25) is correct about the
direction. It is wrong about what it counts (see §A.5).

---

## A. The numbers, with sources

### A.1 Play installs vs uninstalls (bulk reports, `gs://pubsite_prod_<dev>/stats/installs/`)

Pulled 2026-10-07. Play data runs to **2026-09-25**, so none of it covers vc50.

| window | user installs | user uninstalls | active devices |
|---|---|---|---|
| 2026-07-01 .. 09-25 (87 d) | 168 | **222** | 452 → **388** |
| 2026-08-01 .. 08-28 | 55 | 80 | 416 → 384 |
| 2026-08-29 .. 09-25 (fleet window) | 49 | 52 | 389 → 388 |

- **By country, 87 days (installs / uninstalls / active at end):** BD 23/42/35, **IR 10/32/149**,
  US 13/20/53, IN 13/15/5, DZ 10/12/2, BR 12/7/3, RU 8/8/5. Iran makes up 38% of the active base
  and is mostly dormant old builds: vc14 has 55 active devices and vc9 has 33.
- **By Android version, 87 days:** API 36 55/77, API 35 27/36, API 33 22/26, API 34 15/25. No
  version stands out against its share of installs.
- **By device:** too spread out to read (largest named device: 5 installs).
- **Rating:** total average 4.08 (bulk `stats/ratings`). Recent window: 5 ratings, average 3.0
  (fleet facts).

### A.2 When uninstalls happen (GA4 property 481224245, `app_remove` crossed with `firstSessionDate`)

Robots excluded: device `(not set)`, `sdk_gphone*`, `OnePlus8Pro` (pre-launch), and the cities
Calpella, Reston and Frankfurt am Main.

| window | human `app_remove` | same day as install | day 1–7 | day 8–30 | >30 days |
|---|---|---|---|---|---|
| fleet window 08-29 .. 09-25 | 70 | **41 (59%)** | 11 (16%) | 4 (6%) | 14 (20%) |
| 07-01 .. 09-25 | 265 | **142 (54%)** | 35 (13%) | 14 (5%) | 74 (28%) |
| 09-26 .. 10-06 | 37 | **23 (62%)** | 4 | 3 | 7 |

**About 60% of uninstalls happen on the day of install. About 20–28% are long-term users leaving
a mostly dormant base:** 388 active devices, but GA4 shows about 10–20 active users a day.

### A.3 What happens in the first session: per-device timelines, 2026-09-24 .. 10-06

Rebuilt from GA4 events (`deviceModel` × `dateHourMinute` × `eventName`, raw output in the session
scratchpad). There were 43 human devices with a `first_open`.

- **24 of 43 (56%) uninstalled. The median time from `first_open` to `app_remove` is 4 minutes.**
  14 of the 24 uninstalled within 6 minutes; several within 0–2 minutes, right after the scan.
- **The 19 "keepers" did not come back either.** Only 4 of 19 had activity on a second day. "Kept"
  mostly means "has not uninstalled yet".
- **Nothing in session 1 tells the uninstallers apart from the keepers:**

| reached in session 1 | uninstallers (24) | keepers (19) |
|---|---|---|
| first scan completed | 18 | 14 |
| camera tab | 13 | 10 |
| Ask-AI FAB | 11 | 12 |
| leaderboard | 9 | 9 |
| premium paywall shown | 8 | 6 |
| referral screen shown | 8 | 6 |
| real-time monitor started | 10 | 7 |
| review requested | 5 | 2 |
| mic test started | 1 | 1 |

- **Full-screen ads on day 0** (screen class `AdActivity`, firstSessionDate = date):
  **3 human devices saw one, and all 3 uninstalled the same day.**
  - SM-A075M (BR, vc48) saw 3 ads in 3 minutes and uninstalled 23 minutes later.
  - SM-A245F (SO, vc48) saw 4 ads and uninstalled.
  - SM-A176B (IN) was on **vc50, the live build**. It was installed at 18:39, came back at 19:24,
    and saw full-screen ads at 19:27 and 19:30. **`app_remove` is logged in the same minute as the
    second ad.**
- An earlier cohort, where the GA4 funnel API still returns data (first_open 2026-08-11 .. 09-23,
  n=168 humans): **45% of users who saw an ad impression in their first 15 minutes uninstalled
  within a day (26/58). For all users the figure is 29% (49/168).**

### A.4 Ads, crashes, reviews

- **AdMob, 30 days** (`admob.py report --dimensions FORMAT`):

| format | requests | impressions | earnings |
|---|---|---|---|
| app_open | 142 | 12 | 0.03 |
| interstitial | 1,406 | 49 | 0.28 |
| native | 2,216 | 78 | 0.14 |

  **Total about £0.45 a month.** Turning off full-screen ads for new users costs pennies.
- **Live Remote Config** (template v16, read 2026-10-07): `ads_grace_sessions=2`,
  `app_open_ad_min_session=3`, `interstitial_ad_interval=45` s, `show_app_open_ads=true` on
  3.1.17+.
- **Crashes / ANR** (Play bulk `stats/crashes`): September had 14 crashes and 33 ANRs. Over Aug–Oct,
  64 of 89 ANRs came from 3 device models (OP5EBBL1 35, OP5745L1 16, a13ve 13). Those are a few
  long-term users. None of the 43 new-user timelines shows a crash. The Play Reporting API vitals
  return 0 rows because the app is below Play's threshold, so this is **unknown at the Play level,
  not zero**. Crashlytics (24 h) shows 1 ANR and no fatal crash.
- **Written reviews:** 3 negative reviews have text. **2 of the 3 are about ads.** "Completely
  unusable due to excessive ads" (2026-04). "cheio de Ads" (2026-08). The third is a paid-twice
  billing complaint (2026-04).

### A.5 Measurement problems (unknown ≠ zero)

1. **GA4 `first_open` humans (114) vs Play user installs (49) in the same 28 days: 2.3×**, even
   after removing robots. The mechanism is not established (reinstalls such as the SO device that
   installed 5 times, data clears, or other causes). Every D1 figure in GA4 inherits this.
2. **`AnalyticsUtils.logEvent` drops every custom event while Battery Saver, Do Not Disturb, Doze
   or airplane mode is on** (`analytics_utils.kt`). About 10 of 43 new devices show only automatic
   events (screen_view, first_open, app_remove), so their sessions cannot be seen.
3. **The GA4 funnel API (v1alpha) returned 2/84 uninstalls for 09-24 .. 10-06.** Event-level
   matching shows 24/43 for the same period. Do not use the funnel API for windows less than about
   2 weeks old.

### A.6 Store promise vs first screen (Play listing pulled 2026-10-07, all 79 locales)

- en-US title: **"Camera Test & Mic: DeviceGPT"**. Short description: "Mic test, camera test, dead
  pixel test, screen test." Other locales use "Battery, Mic Test" or "Mic Test & Camera".
- The first screen is a health scan, then the Device Score and the Health tab. The mic test sits
  inside the "Mic & Screen" tab. Only 2 of 43 new devices started it.
- The landing-page false claims fixed in landing commit 366d906 (spyware detection, motion alert,
  AI scan) **are not in any store locale.** One borderline line remains in about 45 locales:
  *"Asked 'is my phone hacked?' — the Zero Trust Dashboard gives you a scored, structured
  answer"*. It is not a churn driver, but it is a policy-surface line to review through
  `/aso-refresh`.

### A.7 Live build state

- Play production: **3.1.31 (vc50), `RELEASE_LIFECYCLE_STATE_PUBLISHED`** (Publishing API
  `tracks/production/releases`). GA4 shows vc50 users from 2026-10-04.
- **vc51 (3.1.32) is built, reviewed and on `origin/main`, but not released.**

### A.8 Device check (Pixel 8a, 192.168.0.100:5555)

- The phone has a **sideloaded vc51 release build** (installerPackageName=null, not debuggable),
  installed 2026-10-05. That is not the Play build. Installing Play's vc50 would mean a downgrade,
  which needs an uninstall, so the build was not replaced and no fresh first-run was recorded
  today.
- Netstats for the app's uid: **59 MB down and 12 MB up in the first 2-hour bucket** of the
  2026-10-04 vc50 walk-through (18:00–20:00 UTC). This matches the 10 MB + 2 MB every 30 s monitor
  loop.
- The owner's Wi-Fi ("Emon Tplink") reports `metered=true`. vc51's monitor speed test only runs on
  unmetered networks, so on this phone it never runs. That is the safe direction, but it is worth
  knowing.

---

## B. Root causes, ranked

### 1. A one-time tool, one-time-intent traffic, and no reason to keep it — [Likely]
- Median 4 minutes from install to uninstall. Session-1 behaviour is the same for uninstallers and
  keepers (table in A.3). Keepers do not return (4/19 had a second day). D7 was 0% for 183 users
  in the 2026-09-25 report.
- The title and short description sell tasks people do once: test a camera, test a mic, check for
  dead pixels. The user does the task and uninstalls.
- This is not proven causal. Proving it would need an A/B on the first screen or a re-entry hook.
  But no in-app step explains the difference, and the timing fits "job done".

### 2. Full-screen ads within the first hour — [Likely]
- The "grace" period is counted in **`MainActivity.onCreate` calls at least 60 s apart**
  (`EngagementTracker.trackSession`), not in days. A user who comes back 45 minutes after installing
  is already in "session 3". Interstitials can then fire every 45 s.
- Evidence:
  - 3 of 3 humans who saw a day-0 full-screen ad uninstalled that day. On vc50, the uninstall came
    in the same minute as the second ad.
  - 45% vs 29% day-0 removal for ad-exposed users vs all users (n=58/168, earlier cohort).
  - 2 of 3 written negative reviews are about ads.
- Revenue at stake: about £0.45 a month.

### 3. The real-time monitor starts itself and uses data on the live build — [Likely as a defect; Guessing as a day-0 cause]
- `HandleSystemMonitorAutoStart` → `startService()` starts `SystemMonitorService` when
  `isUserFirstTime()` is true, as soon as notifications are allowed. On Android ≤12 no permission is
  needed.
- **85 of 176 new humans (48%) logged `realtime_monitor_started` (08-11 .. 10-06). Only 5 ever
  toggled it.**
- On vc50 the service downloads 10 MB from Cloudflare and uploads 2 MB to httpbin **every 30 s on
  any network, mobile data included.** It also shows a persistent notification.
- It does not explain the 4-minute uninstalls (10/24 vs 7/19). It is the most plausible cause of
  the day 1–7 uninstalls (16%), and of the BD/IN/IR mobile-data users who leave later.
- The throttle is already in vc51, which is unreleased. vc51 does not remove the automatic start
  on first launch.

### 4. Normal decay of an old, dormant base — [Certain that it exists; not fixable by first-session work]
- 20–28% of uninstalls are users more than 30 days after install.
- There are 388 active devices but about 15 daily users. Iran has 149 dormant devices on old builds.
- Expect a floor of roughly 10–15 base uninstalls a month whatever is fixed. The fleet ratio will
  not reach "installs ≫ uninstalls" from retention work alone.

### Ruled out or weak
- **Crashes / ANRs [Likely not a cause].** The ANRs are concentrated on 3 device models of
  long-term users, and no crash appears on any new-user timeline.
- **Paywall / referral loop [Guessing, weak].** It does not separate uninstallers from keepers
  (8/24 vs 6/19). The loop fix in vc50 is too new to judge.
- **Push spam [Likely not a cause].** The D1 push reached 2 of 99 users.
- **False store claims [Likely not a cause].** Absent from all 79 locales.

---

## C. The 3 fixes most likely to cut uninstalls

| # | fix | why | expected effect | effort |
|---|---|---|---|---|
| 1 | **No full-screen ads until the install is 72 h old.** Gate both interstitial and app-open on install age (`EngagementTracker` already stores `install_date`), not on the activity-creation count. Raise `interstitial_ad_interval` from 45 to 180. **Stop-gap with no code: set RC `ads_grace_sessions` and `app_open_ad_min_session` to 10 now.** | Cause 2. 3/3 day-0 ad viewers uninstalled. Ads earn £0.45 a month. | Removes the ad-driven part of day-0 uninstalls. Estimate: day-0 removal **56% → about 45–50%** [Guessing; n is small]. Also removes the most common complaint in written reviews. | RC change: 5 minutes, needs the owner's yes. Code change: about half a day plus tests; ship in vc52. |
| 2 | **Release vc51, and in vc52 stop the monitor starting itself.** `startService()` should start it only when the user turns it on (drop the `isUserFirstTime()` branch). | Cause 3. 48% of new users get a service using 12 MB every 30 s on vc50. | Cuts the day 1–7 uninstalls (16% of all) and removes a data-bill and battery-drain risk that is unacceptable in a phone-health app. Little effect on the 4-minute uninstalls. | vc51 is already built: 1 release. The auto-start change is about 1 hour plus a test. |
| 3 | **Match the first screen to the store promise, then give people a reason to keep the app.** After the first scan, land on a three-button choice: Camera test, Mic test, Screen test. Then offer the widget (`widget_v2` A/B, see `docs/retention/2026-09-26-widget-habit-loop.md`) as the reason to keep it: "your health score on the home screen, updated daily". | Cause 1. The title sells camera/mic tests; the app opens on a health score; people uninstall at a median of 4 minutes. | The only fix aimed at the main cause, and the least certain [Guessing]. Run it as an RC A/B (own seed) and judge it by day-0 removal and 7-day keep rate, not by D1 alone. | Medium: 2–3 days, plus a device run and an RC experiment. |

Not recommended: more paywall tuning, more push work, or more crash work. The data does not point
at any of them.

**Note:** if the owner accepts that DeviceGPT is a one-time tool, the right health metric is
**7-day keep-installed rate and ratings**, not D1/D7. Even then, fixes 1 and 2 are still worth
doing on their own merits.

---

## D. What to measure after each fix

Exclude robots every time: device `(not set)`, `sdk_gphone*`, `OnePlus8Pro`, and the cities
Calpella, Reston and Frankfurt am Main. Use **event-level device matching** (the method in A.3),
not the funnel API, for any window less than 2 weeks old.

1. **Day-0 removal share for humans, per build.** Today: 56% (24/43, 09-24 .. 10-06). Read it when
   ≥50 humans have installed the build with the fix, about 3 weeks after release.
2. **Full-screen ads shown at install age < 72 h** (`AdActivity` screen views where
   `firstSessionDate == date` or the day after). Target: **0**. Anything above 0 means the gate
   leaks.
3. **`realtime_monitor_started` without `realtime_monitor_toggled` in session 1.** Target: 0 after
   fix 2.
4. **Uninstalls by age at removal** (A.2 table). The d1–7 share should fall after fix 2.
5. **Play bulk user installs vs uninstalls**, 28 days, read once Play data covers the new build
   (it lags about 10 days). Expect a floor of about 10–15 base uninstalls a month from cause 4.
6. Before trusting any GA4 retention figure, fix the measurement holes in A.5. The most urgent is
   that custom events are dropped in Battery Saver, DND and Doze. That needs an owner decision,
   because it was added on purpose.

Sources:
- Play bulk CSVs for 2026-07 .. 2026-10.
- GA4 Data API v1beta (`runReport`) and v1alpha (`runFunnelReport`).
- AdMob API.
- Firebase Remote Config REST (template v16).
- Play Publishing API (`edits.listings`, `tracks/production/releases`).
- `git show 8c40390` (the vc50 source) and `origin/main` (vc51).
- adb on the Pixel 8a.
