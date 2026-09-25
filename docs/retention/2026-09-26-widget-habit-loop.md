# DeviceGPT — making it a daily app: the widget habit loop

Written 2026-09-26. Branch `retention/widget-habit-loop`. Nothing here is released.

## The argument, from GA4 481224245 (last 30 days, all versions)

**People want to come back and look. Nothing brings them back.**

| surface | new users who used it (of 161) |
|---|---|
| Device Timeline | **102 (63%)**, 378 opens — while it had *no daily data* |
| Leaderboard tab | **100 (62%)**, 321 views |
| Insight card | 50 |

| re-entry trigger | result |
|---|---|
| D1 overnight push | **2 of 99** eligible users reached. Four rounds of fixes. WorkManager post-mortem: dominant state `ABSENT` — scheduled, then no record. |
| Home-screen widget | **23 pinned → 21 tapped it 107 times** (~5 each). 11,862 renders. |

The widget is the only trigger that works. It does not depend on `POST_NOTIFICATIONS`,
WorkManager, or the OEM battery killers common on the Xiaomi / Oppo / Vivo / Realme phones in
this app's BD / IN / BR user base — the most likely reason the push keeps vanishing.

So the plan is not "add a feature". It is: **make the one trigger that works show something
new every day, and get more people to have it.**

## The loop

```
widget shows what CHANGED  ->  tap  ->  Health tab: Timeline shows the change  ->  scan again
        ^                                                                              |
        '---------------------- tomorrow's snapshot is different -------------------'
```

Each piece already exists. None of it has ever run together:
- daily snapshots: first written 2026-09-24 (vc48). Before that, `baseline_snapshot_written`
  was 0 for every user on every version.
- "what changed" widget layout: `widget_v2_enabled` — **off** in production.
- Timeline: on, but empty until vc48. Third item on the Health tab, which is where a widget tap lands.

## Steps

| # | step | status | needs |
|---|---|---|---|
| 1 | Label each widget user with the layout served (`widget_v2_arm`) | **done** `dd5130b`, GA4 dim registered | — |
| 2 | Device run: widget v2 delta renders from real snapshots; Timeline visible on tap landing | blocked | **Pixel 8a plugged in** |
| 3 | Pin prompt: 73 saw the raw OS sheet, 10 pinned (**13.7%**). Add one in-app line of *why* before the sheet | not started | step 2 first — UI must be seen |
| 4 | Ship vc49 with 1–3 | waits | owner yes + Timeline directional read (~2026-10-02) |
| 5 | `widget_v2_enabled` → 50% on an **independent seed**, `percent('widget_v2') <= 50` | waits | vc49 live |
| 6 | Read widget taps and D1/D7 by `widget_v2_arm` | — | ~2026-10-20 |

### Why step 5 needs its own seed
`retention_ab_50pct` (`percent <= 50`) already splits `d1_overnight_drain_enabled`. Reusing it
puts the same half of users in both arms, and the two effects become inseparable.

### Why not ship now
vc48 went out 2026-09-24 carrying the Timeline fix — the first build where the app's only
"come back and see what changed" surface has data. Shipping widget changes this week would blend
the two effects, and neither could be read.

### Pin-prompt copy has to be true for both arms
Half the widget users will see the original layout. Any pre-prompt line must describe what the
widget does in *both* — e.g. the health score on the home screen, kept up to date — not
"see what changed", which only the v2 arm shows. The app carries a Deceptive Behavior strike.

## Stop doing

- **The D1 push.** Four rounds, 2% delivery. Leave the code; stop spending on it.
- **Quoting 23% for the pin prompt.** That divided all pins (23, of which 13 came from the drawer)
  by all prompt results (101, of which 28 were `(not set)`). The prompt's own rate is 10 / 73 = 13.7%.

## Found on the way, left alone

`CohortLabeler` stamps `ab_cohort_v3111` on every install since v3.1.11, but the GA4 property had
**no user-scoped custom dimensions**, so it was never queryable — and nothing in `app/src` reads the
cohort, so "treatment" is identical to "control". Harmless; removing it earns nothing.
