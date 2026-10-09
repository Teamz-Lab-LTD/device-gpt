# DeviceGPT — design status

## 2026-10-09 — first run of `/design-gap devicegpt`

Rubric: `teamz-company-automation/claude-config/design-award-audit.md`. The rubric's scan is
written for Flutter; the same nine checks were run with Kotlin/Compose equivalents on `main` at
`c314950`. No earlier score exists for this app.

### Mechanical scan (commands run, numbers as printed)

| # | Check | Result | Verdict |
|---|---|---|---|
| 1 | Icon families (`grep -rhoE "Icons\.<Family>\."`) | `Icons.Default` 228, `Icons.Filled` 26, `AutoMirrored` 1, Outlined/Rounded/Sharp/TwoTone 0 | One family (Default is Filled). Pass |
| 2 | Fonts fetched at runtime | none; `res/font/` bundles Poppins (18) and JetBrains Mono (16) | Pass |
| 3 | Requested weights bundled | every weight in `ui/theme/Type.kt` has a file | Pass. Note: neither font has Bengali glyphs, so Bangla falls back to the system font |
| 4 | **Emoji standing in for icons** | **1,229 source lines, 154 distinct emoji.** Top: ❌128 📱127 ✅100 →98 📊64 ⚠64 ⚡40 ⭐40 🔋36 💡35 📶30 📷27. Worst files: `utils/device_utils.kt` 211, `utils/network_utils.kt` 186, `utils/power_consumption_utils.kt` 130, `utils/health_score_utils.kt` 91, `ui/power_consumption_card.kt` 85, `services/system_monitor_service.kt` 43, `widgets/LockScreenMonitorWidget.kt` 35 | **Fail. The rubric calls this an instant cap** |
| 5 | Stock framework colours | `Color.Red/Green/Blue/…` 45 uses; raw `Color(0x…)` 108 uses outside the theme | Fail (minor) |
| 6 | Raster art mixed with painted UI | 0 `painterResource` calls; 5 drawables | Pass (nothing to mix) |
| 7 | Signature moment, named in five words | Cannot be named. 47 animation call sites, all fades, pulses and progress | **Absent** |
| 8 | Reduced motion handled in one place | 0 matches for `ANIMATOR_DURATION_SCALE`, `reduceMotion`, `areAnimatorsEnabled` | **Not handled anywhere** |
| 9 | Screenshot / golden tests | none (`Paparazzi`, `Roborazzi`, `captureToImage`: 0) | No narrow-phone evidence exists |

Not verified: narrow-phone (320–360dp) overflow in Bangla, which needs a device run; the store
screenshots and video were not re-pulled for this run.

### Score

| Axis | Score | Evidence |
|---|---|---|
| Innovative idea | 1 | Phone check before buying or selling is a clear use, not yet a remembered one |
| Beautiful design | 0 | Capped by check 4: emoji are the icon system on most data screens |
| Animation | 0 | No signature moment; reduce-motion ignored |
| Craft made visible | 1 | One icon family, bundled fonts, 806 unit tests; no layout tests, stock colours |
| Screenshots | not scored | not re-verified this run |
| Video | not scored | not re-verified this run |

**Lowest axis is Beautiful design at 0/2. Blocking it: emoji used as icons on 1,229 lines.
Cost to fix: one icon set plus one emoji-to-icon mapping at the point of display, about a day.
With a score-reveal moment and reduce-motion handling it moves design and animation to 1–2 each.**

The lowest axes are craft axes, so there is buildable work. Plan:
`docs/superpowers/plans/2026-10-09-design-pass.md`.

## 2026-10-09 — second run, after the design pass

Same nine checks, Kotlin/Compose equivalents, on `main` at `86d9707` (not pushed). The first run above was
at `c314950`. Between the two: translation, the emoji-to-icon pass, and the motion and UX pass
(`9595bc5`, `2044124`, `7a86b6a`, `2ba10d4`, `86d9707`).

### Mechanical scan (commands run, numbers as printed)

| # | Check | First run | Now | Verdict |
|---|---|---|---|---|
| 1 | Icon families (`grep -rhoE "Icons\.<Family>\."`) | Default 228, Filled 26, AutoMirrored 1 | Default 229, Filled 26, Outlined 75, Rounded 40, AutoMirrored 10; plus 176 uses of the app's own `DgIcons` / `DgStock` | **Worse on paper.** The icon pass brought in Outlined and Rounded beside Filled. Needs one family chosen |
| 2 | Fonts fetched at runtime | none | none; `res/font/` still 34 files | Pass |
| 3 | Requested weights bundled | pass | unchanged | Pass. Bangla still falls back to the system font |
| 4 | Emoji standing in for icons | 1,229 source lines drawn as icons | 0 drawn: `NoEmojiIconsGuardTest` passes (no emoji in a text literal under `ui/`, none in a displayed string resource, none through the row display path). Emoji remain in `utils/` builders as data, on purpose | **Pass. The cap is lifted** |
| 5 | Stock framework colours | 45 uses | 0 under `ui/` outside the allow-list; 21 on the allow-list (`screen_test_card.kt`: the dead-pixel, grid and touch tests paint pure colours on purpose; `icons/DgIcons.kt`: vector paths). `StockColorGuardTest` holds it. Raw `Color(0x…)` outside the theme: 108 → 86 | Pass for stock colours. Raw hex still open |
| 6 | Raster art mixed with painted UI | 0 `painterResource` | 0 | Pass |
| 7 | Signature moment, named in five words | could not be named; 47 hand-written specs | **"Ring fills, number counts up."** `ScoreRing` in 3 places (score screen, last-score card, health score card). 0 hand-written `tween`/`spring`/`keyframes`/`infiniteRepeatable` under `ui/` outside `ui/theme/Motion.kt`; 50 call sites of the helpers. `MotionGuardTest` holds it | **Present** |
| 8 | Reduced motion handled in one place | 0 matches | `LocalReduceMotion`, provided at 2 of 2 Compose roots; every helper returns a snap, every loop rests. Checked on the emulator with animator scale 0: scanning goes to the finished score screen in one frame and nothing moves after it | **Handled** |
| 9 | Screenshot / golden tests | none | none (0 for `Paparazzi`, `Roborazzi`, `captureToImage`) | Still no automated layout evidence |

Also counted this run: text under 12sp under `ui/` 79 → 3 (two lines on the premium card, left alone as
paywall surface, and one icon glyph in a badge); icon buttons under 48dp with no touch area 29 → 1 (the
paywall close button, left alone).

Narrow-phone evidence now exists, by hand: emulator at 720x1560, density 360 (320dp wide), Bangla, every
tab, the score screen, the three tests, the done card and the drawer were walked and fixed. Screenshots are
outside the repo (session scratchpad, `devicegpt/final/`).

### Score

| Axis | First | Now | Evidence |
|---|---|---|---|
| Innovative idea | 1 | 1 | Unchanged. Nothing in this pass changes what the app is |
| Beautiful design | 0 | 1 | The emoji cap is gone, colours are semantic and paired, one accent, the score looks the same in three places. Held at 1 by check 1 (three icon families side by side), 86 raw hex colours, and Bangla set in a fallback font next to Poppins |
| Animation | 0 | 2 | One named moment, 1.5 s, on the first screen a new user sees; one token file; pop for a pass, shake for a problem; sheet enters slow and leaves fast; all of it off with one system switch |
| Craft made visible | 1 | 1 | 885 unit tests, three source guards (emoji, motion, stock colour), 48dp targets, 12sp floor, 320dp walk. Held at 1 because nothing checks layout automatically (check 9) |
| Screenshots | not scored | not scored | store screenshots not re-pulled |
| Video | not scored | not scored | store video not re-pulled |

### What moved and why

- Design 0 → 1: check 4 was an instant cap and is now a pass with a test behind it.
- Animation 0 → 2: checks 7 and 8 both flipped. The reveal is measured from a recording: 1.53 s from the
  first ring frame to the last action fully shown.
- Check 1 went the wrong way. Before the icon pass there was one stock family; now Filled, Outlined and
  Rounded are all used. This pass did not touch it.

### Still open

1. **One icon family** (check 1): pick Rounded or Outlined for stock icons and move the 255 Default/Filled
   uses, or the reverse. Lowest axis is no longer capped, so this is now the cheapest point on Beautiful design.
2. **Screenshot tests** (check 9): Roborazzi over the score screen, the three test cards and the floating row
   at 320dp in Bangla would turn today's hand walk into a guard. Moves Craft to 2.
3. Raw hex colours: 86 outside the theme (AI-link purple, leaderboard gold, power chart colours).
4. Bengali-capable brand font: owner decision (in "Not in this plan").
5. Camera result title when focus fails on every camera reads "One camera did not respond fully": it is the
   closest existing wording; a line for "more than one" needs the owner.
6. Light theme: secondary text at 50–60 % alpha is under 4.5:1 on white in places. Dark is the default and passes.
7. Paywall surfaces were not touched: the 32dp close button, and two lines at 10–11sp on the drawer premium card.

### 2026-10-09 — icon family unified

Check 1 re-run after converting every stock icon to `Icons.Rounded` (and `Icons.AutoMirrored.Rounded`),
which sits closest to the 2dp round-cap `DgIcons`: `Default` 0, `Filled` 0, `Outlined` 0, `Rounded` 370,
`AutoMirrored` 10 (all Rounded). 29 files; no icon name was used in two families in one file, so no
on/off pair collapsed. 885 tests pass. Open item 1 above is closed; the design axis stays at 1 until the raw
hex colours (86) and the Bangla fallback font are addressed.
