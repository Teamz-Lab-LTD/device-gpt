# First screen test chooser + "test done" retention card — design

Approved by the owner on 2026-10-09. Evidence: `docs/retention/2026-10-09-user-research.md` and
`docs/CHURN-DIAGNOSIS-2026-10-07.md`.

## Problem
- People find DeviceGPT through "camera test / mic test / dead pixel test" searches. The app
  opens on a health score.
- The median uninstall comes 4 minutes after install, and 56% uninstall on day 0.
- Only 2 of 43 new users started the mic test.
- Users who keep the app don't come back: 4 of 19 had a second day.
- The day-1 push reached 2 of 99 users. The only notification-permission request sits inside the
  monitor prompt, which is held back for 24 h.
- Revenue goal: each return visit means an ad view and a chance at the paywall (after the 72 h
  quiet period).

## Experiment
- Remote Config boolean `first_screen_test_chooser`, bundled default `false`.
  - Rollout: a Firebase RC percentage condition, 50% of users. "Users", not "installs":
    Firebase RC splits by app instance, so a reinstall can switch groups. That's accepted.
- The arm is read once, at the first score reveal, and stored in prefs as `fs_arm` = `A` or `B`.
  A later RC change does not move a user to the other arm.
- GA4 user property `fs_arm` is stamped once, so every metric can be split by arm.
- Group A (control): the current flow, unchanged.

## Group B flow
1. **Score reveal (FirstScanGateScreen, score phase).** The score stays. Under it:
   - Heading "What do you want to test?".
   - Three buttons, all the same size: **Camera**, **Mic**, **Screen**.
     - Camera → `navigate_to_tab=camera`.
     - Mic and Screen → `navigate_to_tab=screen_test` (MicTestCard lives in ScreenTestSection).
   - "Share my score" stays.
   - "See details" becomes a smaller text button, "See full health report" → `health`.
   - The widget pin prompt is NOT fired here in group B; it moves to step 2.
   - Event: `fs_test_chosen` with param `test` = camera / mic / screen / report.
2. **"Test done" card.** Shown once per install, the first time any of
   `camera_health_check_completed`, `mic_test_completed` or `screen_pixel_test_completed` fires
   in group B. It's a bottom sheet, dismissable:
   - "Done ✓ Want to keep an eye on your phone's health?"
   - **Add widget** → the existing `WidgetPinPrompt` path. The once-per-install flag is honoured.
   - **Weekly check-up** → on Android 13+, request POST_NOTIFICATIONS. If granted, make sure the
     existing weekly report work is scheduled. No new notification types, and the existing
     1/day cap applies.
   - **Not now** → close.
   - Events: `fs_done_card_shown`, `fs_done_card_action` with param `action` = widget / weekly /
     dismiss, and `fs_notif_permission_result` with param `granted`.

## Units (each one testable on its own)
- `FirstScreenExperiment` (utils): `arm(context)`, which reads RC once, persists the result and
  stamps GA4. A pure `chooseArm(rcFlag, stored)` decides it.
- `TestDoneCard` policy (utils): `shouldShow(arm, alreadyShown, event)`, pure.
- UI: changes to the score phase in `FirstScanGateScreen` (group B branch), plus the sheet
  composable.
- Wiring: the three completion call sites notify `TestDoneCard`; MainActivity routes the
  chooser taps.

## Not affected
- QuietPeriod (no full-screen ads or unasked-for paywalls in the first 72 h).
- Group A.
- Paywall triggers.
- Ads, including the native slot on the first screen.

## Measurement (GA4, robots excluded, event-level device matching)
- Per arm: day-0 uninstall share (baseline 56%), 7-day keep rate, sessions per user over 14 days,
  ad impressions per user, purchases.
- Check after about 4 weeks, when there are about 50 human installs per arm.
- Stop early if group B's day-0 uninstall share is clearly worse after 25 installs per arm.

## Testing
- Unit:
  - `chooseArm` is stable once stored.
  - `shouldShow` only in group B, only once, only on the three completion events.
  - In group B the chooser routes camera → `camera` and mic/screen → `screen_test`.
  - In group B the widget prompt is not fired from the score phase.
- Device: emulator and Pixel, fresh installs with the flag forced on and off. Check each tab
  route, the card appearing once after a mic test, the permission prompt, and no paywall or
  full-screen ad.
