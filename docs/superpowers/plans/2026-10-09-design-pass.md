# DeviceGPT design pass — plan (2026-10-09)

Owner request: audit the app, remove every emoji used as an icon and replace it with real icons
(custom where it makes the app better), improve UI/UX and motion, then show a preview.

Source of findings: `docs/shipaton/DESIGN-AWARD-STATUS.md` (mechanical scan, same date).

**Order matters.** Four agents are moving the remaining English text into resources on
branches `i18n/leaderboard`, `i18n/power`, `i18n/info`, `i18n/misc`. They touch nearly every UI
file, and the emoji sit inside the strings they are moving. This plan starts only after those
four branches are merged into `main` and the suite is green.

## Global constraints

- Text only changes where an emoji is removed. No wording changes; Bangla stays as written.
- No change to ads, paywall, experiment or scoring logic.
- One icon voice: 24dp grid, 2dp stroke, round caps and joins, no fill except small solid dots.
  Material `Icons.Rounded`-like weight so custom and stock icons sit together.
- Colours come from `MaterialTheme.colorScheme` or a semantic token; every background is paired
  with its `on*` colour.
- All motion goes through one tokens file and one reduce-motion switch.
- Touch targets ≥ 48dp. Body text ≥ 12sp. Nothing clipped at 320dp wide in Bangla.
- Unit suite and `:app:assembleDebug` pass after every task. Commits on `main`, not pushed.

## Task 1 — Motion foundation (small, first, because Tasks 3 and 4 use it)

- `ui/theme/Motion.kt`: durations `quick 150`, `standard 250`, `slow 400`, `reveal 900`;
  easings `standard (0.2,0,0,1)`, `enter (0.05,0.7,0.1,1)`, `exit (0.3,0,1,1)`; one spring.
- `LocalReduceMotion`: true when the system animator duration scale is 0 or the accessibility
  "remove animations" setting is on. Provided once at the app root.
- Helpers `motionTween(...)` and `motionSpring(...)` that return a snap when reduce-motion is on.
- Replace the hand-written `tween(...)`/`spring(...)` at the 47 existing call sites with the
  helpers (mechanical; durations mapped to the nearest token).
- Tests: tokens are the only place a duration literal appears in `ui/` (source-reading guard);
  helper returns a snap spec under reduce-motion.

## Task 2 — Icon system and emoji removal (the cap)

- `ui/icons/DgIcons.kt`: custom `ImageVector`s for the app's own ideas, drawn to the one voice:
  phone-check, camera-check, mic-check, screen-check (dead pixel), touch-check, battery-health,
  charge-flow, network-check, speed, memory, storage, temperature, privacy-shield, report
  (certificate), leaderboard (podium), streak, AI-help, app-doctor, share-score, widget.
  Everything generic (close, check, warning, arrow, refresh, settings, search) uses the matching
  stock Material icon.
- `ui/icons/EmojiIcons.kt`: one table from each of the 154 emoji in the codebase to an icon and a
  semantic tint role (neutral / good / warn / bad / accent). `splitLeadingEmoji(text)` returns
  the icon and the text without the emoji.
- Point of display:
  - Label/value rows built in `utils/*` (device, network, power, health): the row renderer
    (`ui/expandable_info_list.kt` and the card row composables) calls `splitLeadingEmoji` and
    draws the icon in a fixed 24dp slot, so every row aligns. Builders are not rewritten.
  - Headings, buttons, chips, tabs, dialogs, toasts: emoji removed from the string resource
    (both languages) and the composable given an `Icon` where the emoji carried meaning; purely
    decorative emoji are dropped.
  - Status marks ❌ ✅ ⚠ → error / check-circle / warning icons with `error`, good and warn
    colours, never colour alone.
  - Arrows `→` in button text → trailing arrow icon.
  - Notifications and the widget (`RemoteViews`, no vectors in text): emoji removed from text;
    small icon set per notification type; widget rows get `ImageView` icons.
  - Share text sent to other apps and AI prompts: unchanged (they are not this app's UI).
- Guard test: no emoji code point in any `strings*.xml` (both languages) or in a `Text(...)`
  literal under `ui/`, apart from an allow-list for share text and AI prompt files.

## Task 3 — Signature moment: the score reveal

"Ring fills, number counts up."

First-scan score screen (`ui/FirstScanGateScreen.kt`, scored state) and the score card on the
health tab: a ring sweeps from 0 to the score over `reveal`, the number counts with it in
tabular figures, the ring colour settles on good / fair / poor, the verdict word pops in with a
small spring when the ring stops, the four sub-score rows slide up 40ms apart, then the chooser
buttons fade in. Under reduce-motion everything appears at its final value. The experiment
lines `remember { FirstScreenExperiment.arm(context) } == "B"` and `RcFetchGate.await(2_500L)`
stay byte-identical.

## Task 4 — UX fixes (ui-ux-pro-max checklist, priority 1–4 first)

- Touch targets: top-bar icons, chips, the banner close buttons and list rows to ≥ 48dp.
- Bottom action buttons: the four yellow buttons clip "সার্টিফিকেট" and leave "PRO" in Latin;
  give labels room (two lines or a wider pill) and equal widths.
- Colour tokens: replace the 45 stock `Color.Red/Green/…` uses with semantic good / warn / bad /
  info tokens defined in the theme, paired with on-colours; check contrast on the lime accent.
- States: test result success pop, problem shake (once, 300ms), loading skeleton where a card
  waits more than 300ms, pressed-scale on the big test buttons.
- Done card entrance from the bottom with the scrim, exit faster than enter.
- Camera result: the card title says the camera looks fine while both rows below say the photo
  was not clear. Make the title follow the worst row (wording exists already; this is which
  title is chosen), if the logic is display-only. If it touches scoring, leave it and report.
- Tab bar: nine scrolling tabs. Not restructured here (navigation change needs the owner);
  the active-tab indicator and label weight are made clearer.

## Task 5 — Proof

- Emulator at 320dp and at default width, Bangla and English: every tab, the three tests, the
  drawer, the done card. Screenshots checked for clipping.
- A screen recording of the score reveal.
- Before/after pairs for the leaderboard, phone info and health tabs.
- Re-run the mechanical scan and append the new numbers to `DESIGN-AWARD-STATUS.md`.

## Not in this plan

Navigation restructure, a Bengali-capable brand font, new illustrations, store screenshots and
video. Each needs an owner decision.
