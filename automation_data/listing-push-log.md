# Play listing pushes — what actually reached the store

Kept because `.last_refresh.android_rewrite` gates the 56-day keyword-rewrite floor and must
NOT be stamped by an edit that changed no keywords. Recording pushes here instead means the
next session can tell "no edit happened" from "an edit happened that does not consume the
rewrite budget".

## 2026-09-09 — claims correction, 69 locales, descriptions only

Removed two capability claims the code could not support (`keylogger detection`,
`on-device malware scan`) and rewrote the en-US/GB/CA/AU App Privacy bullet to name the
mechanisms the code now detects. See commit `14db43b`.

- titles and short descriptions: **unchanged in every locale** — this does not consume the
  Android rewrite floor and does not reset title-based ranking
- backup of all 79 live locales before the push: `play-listing-BACKUP-20260909.json`
- live state after the push: `play-listing-AFTER-20260909.json`
- verified against the STORE, not the local files: 0 of 79 locales carry the claim,
  positive control (locales containing "DeviceGPT") found 79

### The locale-code trap this uncovered

Play serves Norwegian as **`no-NO`**. The fastlane folder was **`nb-NO`**. So `no-NO` had no
local counterpart: it was invisible to a sweep over `fastlane/metadata/android/*`, and pushing
`nb-NO` would have created a second Norwegian listing while the live one kept the false claim
indefinitely. Renamed to `no-NO`.

The lesson generalises: a sweep over local metadata cannot prove anything about the store.
The 79 local folders and the 79 live locales were not the same 79. **Verify claims by pulling
`edits().listings().list()` and grepping that**, with a positive control.

## 2026-10-04 — /aso-refresh devicegpt (SIGNAL) + claims correction prepared, NOT pushed

- `.last_refresh.android_rewrite` corrected 2026-07-13 → 2026-09-08. The 09-09 pre-push backup
  already carried the new title ("Camera Test & Mic: DeviceGPT", commit 1db820b, 2026-09-08), so
  the title floor runs from 09-08. The old stamp would have let a title rewrite through today.
- Drift check: 79/79 live locales byte-identical to fastlane. Backup:
  `play-listing-BACKUP-20261004.json`.
- Prepared: "deep packet inspection" removed from the Network Trust line in 69 locales
  (commit b8fb787). One line per locale; titles and short descriptions unchanged. A Play edit
  with all 69 updates passed `edits.validate`, then was deleted. **Not committed to the store.**
- Gates: 4e PASS (Planner API live). 4f PASS after recording one skip, "internet speed test",
  in keyword-coverage-skip.txt (not winnable: Meteor has 134K reviews).
- **PUSHED 2026-10-04** with the owner's approval. Play edit `18092022244266185335`.
  Verified against the STORE (`play-listing-AFTER-20261004.json`):
  - 0 of 79 live locales carry the DPI claim in any of its 16 language forms;
  - positive control: 69 locales still have the Network Trust (MITM) line;
  - title and short description changed in 0 locales; full description changed in exactly 69.
  - `.last_refresh.android_rewrite` was NOT stamped (no keyword or title change).

## 2026-10-04 — vc50 to PRODUCTION + 77-locale claims removal (one edit `07771553956477489674`)
- Removed "live mic use" / "mic/camera abuse" (69), "bootloader" (69), "ISP tracking" (69), and
  mic/camera "access history" / "Nutzung" (8). Titles and short descriptions are unchanged.
- Verified on the store: 0 of 79 locales carry any of 12 claim phrases. Positive controls: the
  MITM line is present in 69 locales, the root line in 76. Snapshot:
  `play-listing-AFTER-20261004-vc50.json`.

## 2026-10-09 — /aso-refresh devicegpt (SIGNAL, Bangladesh) — bn-BD description prepared, NOT pushed
- Scope: `bn-BD` only. Drift 79/79 identical. Backup `play-listing-BACKUP-20261009.json`.
- Data and reasoning: `automation_data/bd-20261009/REPORT.md`.
- Proposal: `automation_data/bd-20261009/bn-BD-full_description.PROPOSED.txt` (full description
  only; title and short description unchanged). `edits.validate` passed, edit deleted.
- `.last_refresh.android_rewrite` not stamped.
- **PUSHED 2026-10-09** with the owner's go ("whatever best to get Bangladeshi users, do it"). Verified against
  the STORE (`play-listing-AFTER-20261009.json`): exactly 1 of 79 locales changed (`bn-BD`), full description
  only; title and short description unchanged in all 79; `en-US` byte-identical to the backup.
  `.last_refresh.android_rewrite` NOT stamped.
