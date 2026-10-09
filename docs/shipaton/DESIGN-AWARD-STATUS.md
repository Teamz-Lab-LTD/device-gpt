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
