# Ads loaded vs shown — 2026-10-10 (vc54 / 3.1.35)

## The claim we started from

The product-intel card (2026-10-10) said 101 of 2,203 matched ads were shown (5%). It put the
blame on the native pool of 3 per screen (`RemoteConfigUtils.kt:612`).

## What the trace showed

AdMob, 30 days to 2026-10-10, split by `APP_VERSION_NAME` (`scripts/admob.py report --dimensions APP,FORMAT,APP_VERSION_NAME`):

| format | traffic | requests | matched | shown | shown / matched |
|---|---|---|---|---|---|
| native | DeviceGPT 3.x builds | 91 | 89 | 59 | **66%** |
| native | versionName `1.12` / `1.8` / `1.9` / `2.0` | 1,689 | 1,689 | 0 | 0% |
| interstitial | DeviceGPT 3.x builds | 172 | 124 | 32 | 26% |
| interstitial | empty versionName, all from SA | 1,176 | 192 | 0 | 0% |
| app_open | DeviceGPT 3.x builds | 98 | 70 | 5 | 7% |

- **DeviceGPT never shipped versionName 1.12, 1.8, 1.9 or 2.0.** The history goes 1.0 → 1.0.0
  → 1.0.1 → 2.0.0 → 2.0.1 → 3.x. No other Teamz Lab repo contains the native unit
  `…/2139601263`. 1,274 of the 1,666 `1.12` requests came on 2026-09-19 and 09-20. That traffic
  is a clone, a spoofed ad unit or a bot. It is not this code, and no app change can reach it.
- GA4 only sees real installs. Its numbers agree: 222 `ad_loaded` against 79 `ad_impression`
  (36%), and AdMob's 3.x rows give 283 matched against 96 shown (34%).
- **Native on real builds is healthy (66%).** The pool fallback of 3 only applied before
  Remote Config defaults loaded (live RC is 2, bundled default 1). It is now 1. Lazy loading was
  not built: at most 30 fills in 30 days are at stake.
- **The real leak was full-screen ads loaded in sessions where they could not be shown.**
  - `Application.onStart` → `InterstitialAdManager.loadAd()` ran on every foreground. It ignored
    `ads_grace_sessions`, and both show paths honour that key (RC = 10 since v17).
  - `AppOpenAdManager.showAdIfAvailable()` started a load *before* checking
    `app_open_ad_min_session` (RC = 10). It only checked once an ad was already cached.

## The fix (no new placements, no RC value changed, rewarded untouched)

- `utils/AdShowGate.kt` holds one predicate per format. The load path and the show path both use it.
- `InterstitialAdManager.loadAd()` makes no request in grace sessions. It logs `ad_load_skipped_grace_session`.
- `AppOpenAdManager.showAdIfAvailable()` checks the session gate before the launch load.
- `getNativeAdTargetCount()` falls back to 1, the bundled default, instead of 3.
- New GA4 events: `ad_loaded_native|interstitial|app_open`, `ad_shown_native|interstitial|app_open`,
  `ad_show_skipped_grace_session|cooldown|not_loaded|disabled` and `ad_load_skipped_grace_session`.
  The skipped events carry `ad_type` and `session_count`. `ad_loaded` (with `ad_type`) is kept.

## Device check (release APK vc54)

- Pixel 8a: installed over the sideloaded vc51 and launched. This phone is **premium**
  (`is_premium=1`), so it shows no ads in any build. It cannot test ads.
- Emulator `sleep_switch_rooted`, read-only (changes discarded), fresh install, Firebase debug logging:
  - Session 1: no ad requested (UMP is deferred until the first scan finishes).
  - Clock +4 days, so the QuietPeriod is over. Session 2: `ad_load_skipped_grace_session`
    (interstitial, session_count=2) and `ad_load_skipped_grace_session` (app_open). No interstitial
    request. **Before the fix this exact foreground requested one.**
  - Native: `ad_loaded_native` (pool_size=1), then `ad_shown_native` and `ad_impression`. A test
    ad was visible on the Health tab.
  - Session 12 (clock stepped past grace): foreground → `ad_loaded_interstitial`. Cert FAB →
    `ad_shown_interstitial` (session_count=12), then `ad_impression`, then
    `full_screen_ad_dismissed`, then a reload.

## Next check

Next card: `python3 py/build-product-intel.py --app devicegpt`. Read the show rate **on 3.x
rows only**. Target: interstitial and app_open shown/matched rise from 26% and 7%. Their 3.x
request counts drop, because grace sessions no longer request. In GA4,
`ad_shown_interstitial / ad_loaded_interstitial` should be close to 1. The card's all-traffic
total will stay near 5% for as long as the `1.12` and SA traffic continues. That number
measures that traffic, not this app.
