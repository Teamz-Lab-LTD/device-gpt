# DeviceGPT — Play Store Screenshot Source Library

**Captured:** 2026-05-27 · **App build:** debug, Premium forced ON (no ads visible) · **Device:** Pixel 9 Pro XL AVD (1344x2992)

This folder is **source material** for the Play Store screenshot redesign. Raw shots, no headline overlays yet.

## Folder Structure

```
screenshots/
├── README.md                  ← this file (quick-scan)
├── metadata.json              ← full JSON: keywords, priorities, headlines per shot
└── by-feature/
    ├── 01-health/             ← 4 shots — phone health score, privacy, recommendations
    ├── 02-battery/            ← 4 shots — power consumption in watts, components
    ├── 03-hardware/           ← 3 shots — security dashboard, sensors, FPS
    ├── 04-network/            ← 1 shot  — network privacy report
    ├── 05-trust-resale/       ← 2 shots — Generate/Verify Report dialogs (MOAT)
    ├── 06-leaderboard/        ← 1 shot  — device rankings
    └── 07-drawer/             ← 1 shot  — widgets + share + open source
```

**Total kept:** 16 of 27 captured. 11 removed (redundant, all-Loading, wrong app, etc.) — see `metadata.json` → `removed_screenshots_reasons`.

## Top 8 for Play Store Slots (Priority Order)

| Slot | File | Headline overlay | Keyword anchor |
|------|------|------------------|----------------|
| 1 | `02-battery/01-power-consumption-7p9w-overview.png` OR `04-network/01-...` | "Your battery in WATTS, not percent" | battery health, wifi speed test |
| 2 | `01-health/01-todays-health-score-5of10.png` | "Your phone, scored 0-10 every day" | phone health check, ai phone |
| 3 | `02-battery/02-component-breakdown-watts-per-hour.png` | "Exactly which component drains your phone" | battery drain, app power |
| 4 | `03-hardware/03-temp-memory-fps-frame-drop-root.png` | "Live FPS + frame drop for gamers" | fps counter, gamer phone |
| 5 | `01-health/03-privacy-dashboard-60of100.png` | "Real-time privacy threats, scored daily" | is my phone hacked, privacy scan |
| 6 | `05-trust-resale/01-generating-verified-report-dialog.png` | "Sell your phone with cryptographic proof" | phone resale report |
| 7 | `07-drawer/01-widget-verified-report-share-github.png` | "Widgets + share + open source" | lock screen widget |
| 8 | `06-leaderboard/01-device-rankings-best-device-category.png` | "See how your phone ranks vs the world" | phone benchmark |

## Still Needed (Capture in Follow-up Session)

- **AI Assistant 9-platform chooser** — HIGH priority for AI moat shot
- **Network speed test with real numbers** — needs real device WiFi (emulator shows "Loading...")
- **Onboarding screens** — clear app data + relaunch
- **Paywall dialog** — disable forced-premium first

## Naming Convention (For LLM / Automation)

```
by-feature/{NN-category-slug}/{NN-descriptive-content-slug}.png
```

- `NN` = priority order within category (01 = most important)
- Slugs use kebab-case
- Numbers like `7p9w` mean `7.9W`, `60of100` means `60/100`
- File name alone tells you what's in the shot — no need to open

## How to Use This with an LLM

1. Show LLM `metadata.json` for full context (keywords, priorities, headlines)
2. Pick a shot by file path — LLM will know without opening the image
3. Or ask: *"Pick best 4 for 'battery health' keyword"* → LLM filters via `keyword_anchor` field

## Source State

- Source edit `RevenueCatManager.kt` (force premium) — **reverted** after capture
- No commits made — all 16 shots untracked. Add to git only after final crops chosen.
