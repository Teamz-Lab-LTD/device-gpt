fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android preflight

```sh
[bundle exec] fastlane android preflight
```

Pre-release safety gate — runs unit tests + ad-pipeline safety guards. Blocks any release lane if a known regression pattern is detected (test ad ID typos, missing geo suppression, broken Compose render, throttle-too-tight, native ad viewability bug, etc).

### android build

```sh
[bundle exec] fastlane android build
```

Build release AAB

### android internal

```sh
[bundle exec] fastlane android internal
```

Upload AAB to internal testing track (draft)

### android beta

```sh
[bundle exec] fastlane android beta
```

Upload AAB to closed beta track (draft)

### android promote_to_beta

```sh
[bundle exec] fastlane android promote_to_beta
```

Promote internal build to beta (no rebuild)

### android promote_to_production

```sh
[bundle exec] fastlane android promote_to_production
```

Promote existing internal release to production at 10% staged rollout (sends for Play review). No rebuild — reuses the internal AAB. Staged rollout is safer than 100% — Play accepts even when new AAB has narrower device coverage than current production.

### android promote_to_production_draft

```sh
[bundle exec] fastlane android promote_to_production_draft
```

Same as promote_to_production but uploads as DRAFT — appears in Play Console UI for manual review/release with warnings (use when staged rollout is also rejected).

### android release_full

```sh
[bundle exec] fastlane android release_full
```

ONE-SHOT release: build → upload to internal → promote to production (sends for Play review). Same AAB on both tracks.

### android production

```sh
[bundle exec] fastlane android production
```

Upload AAB to production (draft, 10% rollout)

### android production_full

```sh
[bundle exec] fastlane android production_full
```

Full production release — AAB + all 17 locales' metadata + screenshots + featureGraphic + changelogs. Submits to Production track as draft (no rollout) for manual review/release in Play Console.

### android production_release

```sh
[bundle exec] fastlane android production_release
```

Full production release — same as production_full but submits as COMPLETED (sends for Play review immediately). Use when ready to actually ship.

### android validate

```sh
[bundle exec] fastlane android validate
```

Validate Play Store credentials

### android fetch_store

```sh
[bundle exec] fastlane android fetch_store
```

Download current store listing + screenshots from Play Console

### android enhance_screenshots

```sh
[bundle exec] fastlane android enhance_screenshots
```

Enhance raw Play screenshots via Gemini Nano Banana (free tier). Usage: fastlane enhance_screenshots locale:en-US

### android push_screenshots

```sh
[bundle exec] fastlane android push_screenshots
```

Copy enhanced screenshots into Fastlane structure and upload to Play Console

### android push_metadata

```sh
[bundle exec] fastlane android push_metadata
```

Upload text metadata only (title, short/long description) — no screenshots, no AAB

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
