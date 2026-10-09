# Codex brief — DeviceGPT icon, feature graphics and Bangla store screenshots

You are working in an Android app repository. Read this whole file before writing anything.
You have no other context: everything you need is here or in the files it names.

- **Working directory:** `/Users/mdgolamkibriaemon/Projects/Teamz Lab Projects/teamz-projects/debugger`
- **You write only under:** `docs/design/store-art/` (create `icons/`, `feature/`, `screenshots/`, `tools/` inside it)
- **Verify before handing back:** `python3 docs/design/store-art/verify_assets.py` must print `ALL CHECKS PASSED`
- **Output:** complete files on disk. In your reply, list the files you wrote and paste the verify output. No prose between files.

## What the product is

**DeviceGPT** is an Android app that checks a phone: camera, screen (dead pixels, touch), microphone,
battery, network, and, for Bangladesh, whether the phone is officially registered (an IMEI check through
the regulator BTRC). Its one-line idea: **check a phone before you buy or sell it.** After a check it shows
one score in a ring that fills while the number counts up. That ring is the app's signature moment.

The current icon (`docs/design/store-art/reference/icon-current.png`) is a tilted phone with a Wi-Fi mark
and a gear. The owner's verdict: not good. It says "settings" or "repair tool", it shows a mechanism
instead of an outcome, and nothing about it says "trust this before you pay".

Who sees it: mostly people in Bangladesh, standing in a phone market, deciding in two seconds whether to
tap. Many read slowly. The icon and the first screenshot have to work without any text being read.

## What must not be touched

- Nothing outside `docs/design/store-art/`. Not `app/`, not `fastlane/`, not any Gradle file.
- Do not publish, upload or push anything.
- **Never draw, generate or retouch what is inside a phone screen.** Screens in the screenshots are real
  captures from `docs/design/store-art/raw/`. You may crop and scale them. You may not repaint them,
  invent UI, or fix what they show. A store screenshot that shows an interface the app does not have gets
  the app removed.
- Do not use KRY's logo as, or inside, an app icon. KRY's logo appears only in the "presented by" badge.
- No text inside any icon. No letters, no numbers, no "GPT".

## Two closed palettes

Use exactly these values. No other colours, no tints invented between them.

**DeviceGPT (global, and the app's own launcher icon)**

| Role | Hex |
|---|---|
| Night (background) | `#12151A` |
| Card (secondary surface) | `#1D1F25` |
| Lime (the one accent) | `#D9FE06` |
| White (objects, text) | `#FFFFFF` |
| Pass green (score ring only) | `#34D399` |

**KRY variant (Bangla store listing only; KRY International is a Bangladeshi phone shop that presents the
app there, with its owner's permission)**

| Role | Hex | Source |
|---|---|---|
| KRY blue (background) | `#2E32D4` | the KRY wordmark |
| Deep blue | `#0042C6` | outer ring of KRY's round logo |
| Cyan | `#00C2CC` | middle ring |
| Mist | `#C0CDD8` | inner ring |
| White | `#FFFFFF` | |

KRY's round logo (`docs/design/store-art/brand/kry-logo-round.png`) is a white disc inside three
concentric rings: deep blue, cyan, mist. **Those three rings are the KRY motif you may borrow.** The
wordmark itself (`brand/kry-logo-official.png`) is used unmodified, only in the badge.

Contrast rule for everything you make: every foreground sits on a background it was paired with here.
White or lime on Night; white on KRY blue or Deep blue; KRY blue on white. Never lime on white, never
cyan text on blue.

---

## Part A — the icon (two themes, three concepts each)

### The thinking you must carry into the drawing

An icon wins a tap for three reasons, in this order: it is one shape the eye resolves instantly; that
shape promises an **outcome** the person wants; and it looks like nothing next to it in the search
results. The neighbours here are flat phone outlines with colour bars, fingers touching screens, and
barcodes, almost all on white or green. A near-black tile with one lime mark already stands apart. Keep
that, and change what the mark says.

What this person wants is not "a tool". It is the feeling just after the check: **it's fine, I can pay.**
So the icon shows the verdict, not the test.

Draw all three concepts in full. Concept 1 is the default the owner will most likely ship; 2 and 3 exist
so the choice is made by looking, not by imagining.

**Concept 1 — "Pulse to tick" (default).** One upright phone outline, white, rounded corners. Across its
screen runs a single continuous lime line: it starts flat at the left, makes one heartbeat spike, and its
last stroke rises into a tick that breaks out past the right edge of the phone. One line tells the whole
story: we read its pulse, it passed. The line is the hero and is at least 9% of the canvas thick. At
48 px it must read as: a phone, and a lime tick-shaped pulse. If the flat lead-in hurts at 48 px,
shorten it; never thin the line.

**Concept 2 — "The ring".** The app's own signature moment as its icon. A lime ring, about three-quarters
closed, starting at 12 o'clock with round ends, stroke about 10% of the canvas. Inside it a small solid
white phone silhouette. Where the ring ends, a compact lime tick sits as its terminal, so ring and tick
are one stroke. It must not read as a battery gauge or a cleaner app: no percentage, no segments, no glow,
and the phone inside is clearly a phone, not a battery.

**Concept 3 — "Found it".** A white phone outline, and over its upper right a magnifying lens (white ring,
short handle). Inside the lens, on the phone's screen, exactly one solid lime dot, larger than you think
it should be. The lens finds the one thing the eye would miss: the dead pixel, the hidden fault. This is
the inspection-before-buying idea. At 48 px: phone, lens, lime dot.

Style for all three: flat vector, geometric, friendly. Round caps and joins. Outline strokes at least 6%
of the canvas. No gradients, no shadows, no bevels, no glass, no 3D, no texture. Optical centring, not
mathematical: nudge until it looks centred. All art inside the central 66% of the canvas, because Android
masks the icon to a circle, a squircle or a rounded square depending on the phone.

### A1 — global icons, DeviceGPT palette

Background Night `#12151A`, object White, the mark Lime `#D9FE06`. For each concept `N` in `1 2 3`, with
slug `pulse-tick`, `ring`, `found-it`:

- `icons/global-N-<slug>.svg` — 1024×1024 viewBox, full-bleed square, Night background included
- `icons/global-N-<slug>.png` — 1024×1024 export, RGB, no alpha
- `icons/global-N-<slug>-512.png` — 512×512, RGB, no alpha, under 1 MB (Google Play's icon format)
- `icons/global-N-<slug>-foreground.svg` and `.png` (1024×1024) — Android adaptive foreground: the art
  only, transparent background, everything inside the central 66%
- `icons/global-N-<slug>-monochrome.svg` and `.png` (1024×1024) — the same art in a single solid colour
  (`#FFFFFF`) on transparent, for Android 13 themed icons; it must still read with the lime removed

### A2 — KRY icons, KRY palette (Bangla store listing only)

The same three drawings, same geometry, recoloured, plus the KRY motif. Background KRY blue `#2E32D4`,
phone White, the mark White for the outline parts and **Cyan `#00C2CC`** where the global version uses
lime. Around the art, inside the canvas edge, the three concentric KRY rings (Deep blue, Cyan, Mist) as a
thin frame, the way they frame KRY's own round logo: this is what makes it read as "from KRY" with no
logo on it. If the rings crowd the art at 48 px, reduce them to one cyan ring; never shrink the mark.

- `icons/kry-N-<slug>.svg`, `icons/kry-N-<slug>.png` (1024, RGB, no alpha), `icons/kry-N-<slug>-512.png`

No foreground or monochrome files for KRY: this icon is only ever a store picture, never the launcher.

### A3 — the sheet that proves it

- `icons/icon-sizes.png` — one image: every one of the six icons at 512, 128 and 48 px in a row, global
  on a mid-grey strip and KRY on a mid-grey strip, each labelled underneath with its file name. Then a
  second block showing each of the three global icons masked to a circle, a squircle and a rounded square
  at 128 px. Look at it. If any icon is not instantly nameable at 48 px ("a phone with a tick", "a ring
  with a phone", "a phone under a lens"), fix the drawing and export again.
- `icons/NOTES.md` — for each concept, two sentences: what you changed after looking at 48 px, and which
  one you would ship and why.

---

## Part B — feature graphics (1024×500, the wide banner on the store page)

No screenshot inside these. They are brand surfaces: icon, words, motif. Text is real text, shaped
correctly, not drawn letters.

**Bangla text is the hard part.** Bengali has joined letters. Do not hand-draw them and do not trust an
image model with them. Render text with a real shaping engine: either SVG `<text>` rendered by
`rsvg-convert` (installed at `/opt/homebrew/bin/rsvg-convert`, it shapes Bengali through Pango), or Pillow
with Raqm (`from PIL import features; features.check("raqm")` must be True). Font: Kohinoor Bangla, a
macOS system font at `/System/Library/Fonts/KohinoorBangla.ttc` (Pillow face index 3 is Bold, 1 is
Semibold; in SVG use `font-family="Kohinoor Bangla"` with `font-weight="700"` or `600`). After rendering,
open the PNG and compare each Bangla word with the strings below, letter by letter. A detached vowel sign
or a box means it failed.

### B1 — KRY feature graphic (Bangla listing)

- Background KRY blue, with the three KRY rings as large arcs entering from the right edge, cropped by
  the canvas, low-key: they are atmosphere, not the subject.
- Left, top: a white pill badge holding `brand/kry-logo-official.png` (unmodified, with its tagline and
  ™, on white) followed by the text `সৌজন্যে: KRY International` in KRY blue.
- Left, middle, large and bold, white, two lines: `ফোন কেনা-বেচার আগে` / `একবার চেক`
- Left, below, smaller, white: `IMEI check · Display test · Mic test · Camera test`
- Right: the KRY icon, concept 1, large, on its own (no white tile behind it), with `DeviceGPT` in white
  bold under it.
- Keep 48 px clear on every side; Google crops feature graphics differently on different screens.

Files: `feature/kry-feature.svg`, `feature/kry-feature.png` (1024×500, RGB, no alpha).

### B2 — global feature graphic (English)

Night background, one large lime ring arc entering from the right (the score ring, not the KRY rings).
Left: `Check a phone before you buy or sell it` in white bold, two lines; below it
`Camera test · Mic test · Display test · Battery health` in white. Right: global icon concept 1 with
`DeviceGPT` under it. No KRY anywhere.

Files: `feature/global-feature.svg`, `feature/global-feature.png` (1024×500, RGB, no alpha).

---

## Part C — Bangla store screenshots, KRY variant

The current version is `reference/screenshot-v1-kry-imei.png`: a flat blue canvas, a badge, a two-line
headline, a straight phone. It is clean and it is dull, every one of the eight is the same picture, and
at thumbnail size the interface inside the phone is too small to read. Make a set that someone scrolling
in a phone market stops on.

Write `tools/compose_screenshots.py` (Python 3, Pillow; `rsvg-convert` allowed for SVG parts) that builds
every screenshot from the raw captures, so the set can be rebuilt when better captures arrive. Then run it.

### Layout system (same grammar, three compositions, so the set has rhythm instead of repetition)

Canvas 1080×1920, RGB, no alpha. Shared across all: KRY blue background with large soft KRY ring arcs
(Deep blue and Cyan at low contrast) placed differently on each shot; a white pill badge with the
official KRY logo and `সৌজন্যে: KRY International`; a two-line headline, line 1 bold and large, line 2
semibold and smaller, white; a short cyan rule between them.

- **Composition "hero"** (use for shots 1 and 2): headline top, phone centred below it, whole phone
  visible with its rounded bottom, plus **one focus card**.
- **Composition "left"** (shots 3 and 4): phone on the left third, slightly larger, running off the
  bottom is NOT allowed; headline and the focus card stacked on the right.
- **Composition "right"** (the rest, alternating): mirror of "left".

**The focus card is the main improvement.** It is a rounded white-bordered card that floats over the edge
of the phone and shows the single most important part of that screen **enlarged 1.6× to 2.2×**, cut from
the same real capture. Real pixels, only scaled: this is what makes the interface readable at thumbnail
size without inventing anything. A thin cyan line connects the card to the place on the phone it came
from. One card per screenshot.

The phone is a simple drawn device: a rounded rectangle, radius about 9% of its width, Night `#12151A`
body 14 px thick around the capture, soft shadow under it. No brand device frame, no notch art.

### The shots

Raw captures are 1344×2992 PNGs in `docs/design/store-art/raw/`. Crop out the status bar (top ~120 px)
and the gesture bar (bottom ~90 px). Headlines are exact; do not reword or translate them.

| # | Output file | Raw | Line 1 | Line 2 | Focus card shows |
|---|---|---|---|---|---|
| 1 | `01-imei-check.png` | `02-official.png` | `IMEI check` | `ফোনটা অফিসিয়াল তো?` | the IMEI field with the green "number is well-formed" line under it |
| 2 | `02-display-test.png` | `03-deadpixel-red.png` | `Display test` | `দাগ আর dead pixel দেখুন` | the instruction text at the top of the red screen |
| 3 | `03-mic-test.png` | `04-mic-speak.png` | `Mic test` | `কথা ঠিকমতো যায় তো?` | the level meter and the "speak now" line |
| 4 | `04-phone-check.png` | `01-score.png` | `ফোন কেনা-বেচার` | `আগে একবার চেক` | the score ring with 97 |
| 5 | `05-camera-test.png` | `05-camera.png` | `Camera test` | `ছবি পরিষ্কার আসে তো?` | the two camera thumbnails |
| 6 | `06-touch-test.png` | `06-alt-grid.png` | `Touch screen test` | `স্ক্রিনে আঁচড় আছে কি না দেখুন` | a section of the grid |
| 7 | `07-battery.png` | `07-power.png` | `Battery health` | `কোন অংশে চার্জ কত যায়` | the top three rows of the list |
| 8 | `08-report.png` | `08-report-cards.png` | `ফোনের অবস্থার` | `report দেখান` | the 9/10 score card |

Known weak captures, taken on an emulator: 5 shows a warning and a synthetic camera scene, 6 is a plain
grid, 8 shows the entry to a report and not a report. Compose them anyway, as well as they allow; they
will be replaced with real-phone captures later and your script must make that a one-file swap. Do not
"improve" them by painting.

Files: the eight PNGs in `screenshots/kry/`, plus `screenshots/kry/contact-sheet.png` (all eight side by
side, reduced) and `screenshots/kry/README.md` (which composition each shot uses, the crop and focus
rectangles as numbers, and how to swap a raw capture and rebuild).

Before you finish, open the contact sheet and every screenshot at full size and check: the whole phone is
inside the canvas in every shot; no headline or badge is clipped or closer than 40 px to an edge; every
Bangla word matches the table above letter for letter; the focus card is sharp (scaled from the 1344-px
capture, not from an already-reduced one); no two neighbouring shots have the same composition.

---

## Order of work

1. Part A, concept 1 in the global palette, through to the 48 px check. Get this one right first.
2. The other icons, then `icons/icon-sizes.png` and `icons/NOTES.md`.
3. Part B.
4. Part C.
5. `python3 docs/design/store-art/verify_assets.py`. Fix until it prints `ALL CHECKS PASSED`.

If something here cannot be done as written (a tool is missing, a string does not shape, a capture
cannot be composed the way the table asks), do not guess and do not skip silently: do the nearest thing
that is honest, and say exactly what and why in `docs/design/store-art/CODEX-NOTES.md`.
