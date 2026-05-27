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
