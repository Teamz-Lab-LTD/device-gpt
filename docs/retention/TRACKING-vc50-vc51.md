# DeviceGPT tracking: vc50 + vc51 (baseline 2026-10-08)

Re-check on **2026-10-15** (about a week after vc51 goes live) and again on **2026-10-29**.
Compare every number below with this baseline. Change one thing at a time.

## Releases
| Build | What it changes | State |
|---|---|---|
| vc50 / 3.1.31 | honest results, paywall loop fixed, first scan counts | PUBLISHED (review cleared ~2026-10-07) |
| vc51 / 3.1.32 | widget has real data, speeds in Mbps, monitor saves mobile data, 4 false readings fixed, floating buttons hide on scroll, **native ads wait for consent**, 30/day native request cap, no rewarded requests to a placeholder unit | sent 2026-10-08, **PUBLISHED by 2026-10-09** (review took under a day) |

Check release state with the Publishing API, not `edits.tracks` (that shows `completed` while
a release is still in review):
`applications().tracks().releases().list(parent='applications/com.teamz.lab.debugger/tracks/production')`

## Ads (AdMob, account currency GBP)
Run: `python3 scripts/admob-guard.py` (healthy prints `ADS OK`). Use `--days 7` once vc51 is live.

| Metric | Baseline | Expect after vc51 | Why |
|---|---|---|---|
| Earnings, 30 days | £0.41 | small rise only | about 13 daily users; real money needs about 1k |
| Native requests per day | 1–5 (consent race, since 3.1.27) | 20–60 | loader now waits for consent instead of skipping |
| Native match rate (served / requested) | 98% | similar | already high |
| Native show rate (shown / served) | 4% (12% without the 09-19/20 loop) | higher | ads load early in the session now |
| Interstitial match rate | 26% (376/1432) | watch | |
| Interstitial clicks / impressions | 25% (12/48) | under 10% | above 10% means accidental clicks and AdMob risk |
| Full-screen ads | held until session 10 (RC v17, 2026-10-06) → **from session 4 (RC v20, 2026-10-09)** | interstitial/app-open impressions come back for returning users | v17 turned off about 70% of ad revenue; 2026-10-08 showed 263 requests and 0 impressions. Watch day-0 uninstalls: sessions 1–3 stay ad-free |

## Users
| Metric | Baseline | Source |
|---|---|---|
| Real daily active users | about 10–13 | GA4 481224245, robot filter below |
| New users per day | about 3.7 | GA4 |
| Day-0 uninstall share (humans) | 49–56% | GA4 app_remove; docs/CHURN-DIAGNOSIS-2026-10-07.md |
| Play installs vs uninstalls, 18 days | 33 vs 32 | Play bulk reports |
| Store listing visitors per day | about 9, 28% convert | Play bulk store_performance |

Robot filter (always apply): device model `(not set)`; city Calpella, Reston or Frankfurt am Main;
devices `Pixel 8a` (owner) and `sdk_gphone64_arm64`.

## Ratings
| Metric | Baseline |
|---|---|
| Review asks, real users, 90 days | 308 asks / 268 users (151 on install day, mostly builds before the 2026-09-24 gate fix) |
| Ratings received, Aug–Sep | about 5 (5, 5, 3, 1, 1 stars) |
| Asks on current builds (3.1.29+) | about 12 a month |
| Review → paywall chain | **turned off 2026-10-08** (RC v19 `enable_review_first_strategy=false`). Before: the paywall opened right after the review flow, even when Play showed no dialog. Watch `paywall_chain_review_completed` with `strategy_enabled=false`, and the 1★ share of new ratings. Roll back: set it to true, or restore `automation_data/rc-backup-v18-20261008.json` |

## Remote Config (sys-explorer-131ed, v20 since 2026-10-09)
`review_delay_first_launch_ms` = 86400000 (24 h); `ads_grace_sessions` = 3; `app_open_ad_min_session` = 4 (v20);
`native_ad_target_count` = 2; `native_ad_max_requests_per_session` = 10; `enable_review_first_strategy` = false (v19).
Writing needs the ETag: send `Accept-Encoding: gzip`, or the response has no ETag header.
Access: `gcloud auth print-access-token --account=teamz.lab.contact@gmail.com`.
