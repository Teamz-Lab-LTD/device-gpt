# Fastlane — DeviceGPT (Android)

Automates Play Store builds and uploads.

## One-time setup

1. **Install Ruby + Bundler** (macOS: `brew install ruby`, then `gem install bundler`).
2. **Install fastlane**:
   ```bash
   bundle install
   ```
3. **Create a Play Store service account** (one-time, ~5 min):
   - Go to [Play Console](https://play.google.com/console) → **Setup** → **API access**
   - Click **Create new service account** → follow the Google Cloud link
   - Grant the account the **Service Account User** role
   - Create a JSON key, download it
   - Back in Play Console, click **Grant access** on the new account and give it at minimum: **Release manager** role (or custom: manage releases + edit store listing)
4. **Save the JSON key**:
   ```bash
   mv ~/Downloads/play-store-key.json fastlane/play-store-key.json
   ```
   (The filename is gitignored. Never commit this file.)
5. **Validate credentials**:
   ```bash
   bundle exec fastlane validate
   ```

## Lanes

| Lane | What it does |
|---|---|
| `build` | Clean + build release AAB locally |
| `internal` | Build + upload to **Internal testing** (draft) |
| `beta` | Build + upload to **Closed beta** (draft) |
| `promote_to_beta` | Promote current **internal** build → **beta** (no new build) |
| `production` | Build + upload to **Production** as draft, 10% rollout |
| `metadata` | Upload store listing only (no AAB) |
| `fetch_metadata` | Download current store listing into `fastlane/metadata/` |
| `validate` | Check Play Store credentials |

## Usage

```bash
bundle exec fastlane internal          # safest — push to internal testing
bundle exec fastlane beta              # push to closed beta
bundle exec fastlane production        # push to production as draft
bundle exec fastlane promote_to_beta   # no rebuild, just promote
```

## Notes

- All production uploads are created as **draft** — you must review and roll them out manually in Play Console.
- The production lane defaults to **10% staged rollout**. Edit `rollout: "0.1"` in `Fastfile` to change.
- Native debug symbols are auto-embedded in the AAB via `debugSymbolLevel = "FULL"` in `app/build.gradle.kts`.
- Store listing metadata (title, description, screenshots) is **not uploaded** by default — use the `metadata` lane explicitly after running `fetch_metadata` once.

## First time pulling metadata

```bash
bundle exec fastlane fetch_metadata
```
This downloads current store listing into `fastlane/metadata/android/`. Edit files there, then:
```bash
bundle exec fastlane metadata
```
to push changes back.
