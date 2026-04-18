# Fastlane — DeviceGPT (Android)

Automates Play Store builds, metadata, and AI-enhanced screenshots.

## One-time setup

The Play Store service account JSON and Gemini API key are already configured system-wide at `~/.config/teamzlab/`. No extra work needed for those.

1. **Install fastlane** (already installed via Homebrew if you see `fastlane --version`). Otherwise:
   ```bash
   bundle install
   ```
2. **Validate credentials**:
   ```bash
   fastlane validate
   ```

## Build + upload lanes

| Command | What it does |
|---|---|
| `fastlane build` | Clean + build release AAB locally |
| `fastlane internal` | Build + upload to **Internal testing** (draft) |
| `fastlane beta` | Build + upload to **Closed beta** (draft) |
| `fastlane promote_to_beta` | Promote current internal → beta (no rebuild) |
| `fastlane production` | Build + upload to **Production** (draft, 10% rollout) |
| `fastlane validate` | Check Play Store credentials |

## Store listing + screenshot workflow

The flow is three explicit steps: **fetch → enhance → push**.

### Step 1 — Pull current store listing
```bash
fastlane fetch_store
```
Downloads text metadata + screenshots from Play Console into `fastlane/metadata/android/`.

### Step 2 — Enhance screenshots with Gemini Nano Banana (free tier)
```bash
fastlane enhance_screenshots                              # defaults: locale=en-US, kinds=phone
fastlane enhance_screenshots locale:en-US kinds:all       # phone + tablet7 + tablet10
fastlane enhance_screenshots locale:de-DE prompt:"..."    # custom prompt
```

Reads raw screenshots from `fastlane/metadata/android/{locale}/images/{kind}/`, runs them through `teamz-company-automation/py/aso/aso-gemini-edit.py`, and writes polished versions to `automation_data/play-screenshots/enhanced/{locale}/{kind}/`.

**Review the enhanced outputs** before pushing. The default prompt frames screenshots in a Pixel 8 Pro frame on a solid background with a short headline — edit in `fastlane/Fastfile` → `DEFAULT_ENHANCE_PROMPT` if you want a different look.

### Step 3 — Push enhanced screenshots to Play Console
```bash
fastlane push_screenshots                        # en-US
fastlane push_screenshots locale:en-US dry_run:true   # stage but don't upload
fastlane push_screenshots locale:de-DE
```

Copies enhanced PNGs from `automation_data/play-screenshots/enhanced/{locale}/` into the Fastlane supply structure and uploads. No AAB, no metadata touched.

### Push text metadata only
```bash
fastlane push_metadata
```
Pushes `title.txt`, `short_description.txt`, `full_description.txt` — no images, no AAB.

## Notes

- All production uploads are **draft + 10% staged rollout** by default. Roll out manually in Play Console.
- Native debug symbols are auto-embedded in the AAB via `debugSymbolLevel = "FULL"` in `app/build.gradle.kts`.
- Credentials live at `~/.config/teamzlab/play-console-service-account.json` (shared across all Teamz Lab projects).
- Gemini Nano Banana runs on the free tier — no per-screenshot cost. Key at `~/.config/teamzlab/gemini-api-key.txt`.
- The `.teamz-automation.env` file at project root holds the package name and data dir paths consumed by submodule scripts.
