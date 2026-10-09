# Bangla backlog: what is still English

Written 2026-10-09, after task 3 of the easy-Bangla work
(`docs/superpowers/specs/2026-10-09-easy-bangla-design.md`). Task 3 moved the buy/sell path to
string resources: first screen, done card, tab bar and top bar, camera check, screen tests,
health tab, drawer, and the score share text. Everything below is still typed into Kotlin or
XML in English.

Order: how often a person checking a phone before buying or selling it would meet the text.
Counts are rough (a grep for `Text("…")`, `text = "…"`, `title = "…"`, toasts and
`appendLine("…")`), good enough to size the work, not to bill it.

Rules for whoever picks this up: style guide and fixed vocabulary in the spec above; every new
Bangla string must pass `BanglaStringsGuardTest`; numbers go through `%s`, not `%d` (see item 3).

## 1. English inside screens that are otherwise Bangla now

These are the loudest, because the person is already on the buy/sell path when they see them.

| Where it shows | What is English | Comes from | Rough count |
|---|---|---|---|
| Health tab, battery card | `🔋 Battery Status: Not Charging`, `Charge Type`, `Battery Temp` rows | `utils/health_score_utils.kt`, `utils/device_utils.kt` | 10 + 25 |
| Health tab, "what to do today" | task titles and bodies (`Free up storage space`, `Restart your phone` …) | `utils/` task builders | ~20 |
| Health tab, "what would make it better" | every tip line (`🌟 Great job! Your phone is in excellent shape` …) | `HealthScoreUtils.getImprovementSuggestions` | ~40 |
| Health tab, privacy card | threat names (`USB debugging enabled (security risk)`) | `utils/` privacy checks | ~15 |
| Health tab, memory / storage / battery cards | result line after tapping the button, and `Error: …` | results returned by `utils/` cleaners, `"Error: ${e.message}"` in `ui/health_section.kt` | ~12 |
| Health tab, timeline card | row labels (`Device Score 97`, `Daily snapshot 9/10`), date header `Fri, 9 Oct` | stored event labels; `SimpleDateFormat(…, Locale.getDefault())` (default locale is pinned to English on purpose) | ~10 |
| Health tab, last-score card | the 💡 insight line | `utils/InsightEngine` | ~10 |
| Camera tab, "খুঁটিনাটি" rows | values only: `LIMITED`, `FULL`, `OFF, ON, ON_AUTO_FLASH`, `1/20000s - 1/2s` | `utils/camera_health_utils.kt` | 38 in file |
| Mic test | units `dBFS`, `dB` beside Bangla labels | `ui/MicTestCard.kt` | 5 |
| Memory / storage cards | `1790 MB / 3920 MB (45%)`, `8 GB / 9 GB` | utils | units only, allowed by rule 1 |
| Drawer, premium card | `DeviceGPT Premium`, `Lifetime Access`, `✓ No ads • …`, `Get DeviceGPT Premium`, premium-active dialog | `ui/drawer.kt` lines ~360-640 (paywall code, left alone in task 3) | 9 |
| Drawer and ads | the `Ad` badge, `Sponsored` | `AdBadge()` in `ui/drawer.kt`, native ad layout | 3 |
| Bottom buttons | `PRO`, `Remove Ads - Premium Lifetime` | premium FAB in `ui/adaptive/DeviceGptNavExperience.kt` (paywall code) | 5 |
| Verified-report gate | `Generate 1 Verified Report`, `Watch ad`, `Go Premium` | same file, lines ~1820-1855 (ads / paywall code) | 4 |
| AI dialog title | `এআই বুঝিয়ে দেবে: Camera Health Test` — the part after the colon | item titles passed to `onItemAIClick(…)` / `handler(…)`; they also build a file name, so they cannot simply be translated | ~25 call sites |
| Android's own dialogs | camera / mic / notification permission prompts, Google consent sheet | the phone and Google; they follow the phone's language, not the app's | not ours |

Hardcoded values beside translated labels (flagged by task 2): `"yes"`, `"no"`, `"unknown"`,
`"not reported"` appear in about 13 places in `ui/*.kt` outside the files done in task 3, and in
most `utils/*` row builders. Camera rows now use `camera_yes`, `camera_no`, `camera_not_reported`;
reuse those.

## 2. Tabs a buyer or seller opens next

| File | What | Rough count |
|---|---|---|
| `ui/power_consumption_card.kt` | whole "চার্জ খরচ" tab: experiments, results, explanations, and the Usage Stats dialog flagged by task 2 | 249 |
| `utils/power_consumption_utils.kt`, `power_recommendations.kt`, `power_achievements.kt`, `power_education.kt`, `power_alerts.kt`, `power_consumption_aggregator.kt`, `ui/PowerConsumptionViewModel.kt` | rows and advice shown on that tab | ~75 |
| `utils/device_utils.kt` and the device-info builders | every row of "ফোনের তথ্য" (ready-made English rows) | 25+ in this file alone; the tab is built almost entirely from them |
| `utils/network_utils.kt`, `NetworkReachabilityTester.kt`, `NetworkPrivacyScorer.kt`, `ZeroTrustScorer.kt`, `ui/NetworkReachabilityCard.kt`, `ui/NetworkPrivacyReportCard.kt`, `ui/ZeroTrustDashboard.kt` | "নেটের তথ্য" tab | ~135 |
| `ui/VerifiedReportDialog.kt` | the report a seller generates and a buyer checks. High value for the buy/sell habit; do this one first in this group | 18 |
| `ui/viral_share_dialog.kt`, `utils/ShareCardRenderer.kt` | share dialog, the picture card of the score (text is drawn into the image), and a second copy of the score share sentence | 27 |
| `ui/LeaderboardSection.kt`, `ui/device_leaderboard_card.kt`, `ui/BestDevicesScreen.kt`, `ui/DeviceInsightsScreen.kt`, `ui/DataRetentionReminderDialog.kt` | "সেরাদের তালিকা" tab | ~105 |
| `ui/ai_bridge_card.kt`, `ui/ai_assistant_dialog.kt`, `utils/robust_ai_sharing.kt` | leftovers in AI screens | ~19 |
| `ui/PaywallWithReferralFallback.kt`, `ui/PremiumPurchaseDialog.kt` | paywall | 17 |

## 3. Things that look wrong rather than merely English

- **Bangla digits appear where `%d` is used.** Under the Bangla locale `getString` formats `%d`
  with Bangla digits, while every computed number in the app is in Latin digits (rule 8). Task 3
  strings use `%s` to avoid it. These older keys still use `%d` and will print Bangla digits:
  `all_test_results`, `test_result_format`, `more_tests`, `total_tests`, `sampling_countdown`,
  `probe_checking`, `probe_verdict_intermittent_why`, `probe_verdict_ok_detail`. Change them to
  `%s` in `values` and in every `values-*` folder together (lint checks that they match).
- **The countdown reads oddly under one minute** (flagged by task 2): `ai_bridge_auto_off_prefix`
  + time + `ai_bridge_auto_off_suffix` is a sentence glued from two halves, and the Bangla suffix
  says "মিনিট" even when seconds are left. Needs one key per case with the number as a placeholder.
- **Two English verdict scales for the same number.** The first screen says Great / Good / Fair
  at 75 / 60 / 40, the last-score card says Good / Fair / Poor at the same cut-offs. The Bangla
  follows each English one, so the same score can read "ভালো" on one screen and "মোটামুটি" on the
  next. Pick one scale in English first.
- **"সার্টিফিকেট" under the bottom button is as wide as the button.** It is not cut on a 6.7-inch
  screen but has no room to spare. A shorter word or a smaller label would be safer.
- **Score message has two homes.** `ui/health_score_card.kt` shows it from resources;
  `HealthScoreUtils.getHealthScoreMessage` still returns English for the AI-facing app functions.
  The thresholds are written twice.

## 4. Outside the app window

| Where | What | Rough count |
|---|---|---|
| `res/layout/widget_lock_screen_monitor.xml` | 14 `android:text` values typed into the layout (flagged by task 2) | 14 |
| `widgets/LockScreenMonitorWidget.kt` | text set on the widget from code | 13 |
| `utils/RetentionNotificationManager.kt` | daily and weekly reminder titles and bodies (flagged by task 2). The done card now offers "সপ্তাহে একবার খবর" in Bangla and the notice that then arrives is English | 20 |
| `services/system_monitor_service.kt` | the always-on notification rows | 12 |
| other notification builders (`D1OvernightDrainWorker`, charge summary, FCM) | titles and bodies | not counted |
| `Application.kt`, `utils/interstitial_ad_manager.kt` | a few toasts | ~11 |

## 5. Not text on a screen, decide before translating

- **AI prompts and the reports handed to an AI** (`buildCameraAiContext`, `buildCameraProblemReport`,
  `buildScreenTestAiContext`, mic `buildReport`, health report in `ui/health_section.kt`). They are
  read by an AI, and English works best there. What may need Bangla is one instruction line:
  "answer in easy Bangla".
- **Text that code compares against.** Do not translate these values; translate only where they
  are shown: `utils/health_score_utils.kt` matches English such as `"Battery Full"`;
  `ui/health_section.kt` checks `"Not enough data"` and `"Recent usage detected"`; the nav shell
  checks the share text against the `loading` string; camera symptom names and `facing`
  (`"Back"` / `"Front"`) and screen-test colour names (`"red"` …) are stored and sent to analytics.
- **Android instrumentation tests** under `app/src/androidTest` look up English text
  (`"Daily Health Check"`, `"Scanning your device…"`). They run on an English emulator, and some
  were already stale before task 3. They were not run or changed.
- The 17 other `values-*` folders do not have the keys added in tasks 2 and 3. They are
  unreachable today (only বাংলা and English can be chosen), and Android falls back to English.
