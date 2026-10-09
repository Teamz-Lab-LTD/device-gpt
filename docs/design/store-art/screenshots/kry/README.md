# KRY store screenshots

Built with Pillow and rsvg-convert/Pango using Kohinoor Bangla. All headlines are the exact supplied strings, shaped as complete lines. Screens and focus cards come directly from original capture crops with Lanczos scaling; no screen painting, generated UI, colour correction, or retouching is used.

Crop/focus rectangles use original capture coordinates `(left, top, right, bottom)` with exclusive right/bottom bounds. Device rectangles are output `(x, y, width, height)`. Focus magnification is relative to the same content as displayed in the phone, not relative to the raw capture.

| Shot | File | Composition | Screen crop | Focus crop | Magnification | Device rectangle |
|---|---|---|---|---|---|---|
| 1 | 01-imei-check.png | hero | (0, 120, 1344, 2902) | (80, 1550, 1260, 1880) | 1.2× | (221, 520, 638, 1291) |
| 2 | 02-display-test.png | left | (0, 0, 1344, 2902) | (55, 65, 1310, 308) | 1.2× | (60, 540, 618, 1302) |
| 3 | 03-mic-test.png | right | (0, 120, 1344, 2902) | (125, 805, 1230, 1044) | 1.3× | (402, 540, 618, 1249) |
| 4 | 04-phone-check.png | left | (0, 120, 1344, 2902) | (401, 790, 951, 1335) | 2.0× | (60, 540, 618, 1249) |
| 5 | 05-camera-test.png | right | (0, 120, 1344, 2902) | (40, 1390, 934, 1760) | 1.6× | (402, 540, 618, 1249) |
| 6 | 06-touch-test.png | left | (0, 0, 1344, 2902) | (310, 620, 1150, 1230) | 1.7× | (60, 540, 618, 1302) |
| 7 | 07-battery.png | right | (0, 120, 1344, 2902) | (94, 702, 1250, 1500) | 1.3× | (402, 540, 618, 1249) |
| 8 | 08-report.png | left | (0, 120, 1344, 2902) | (90, 771, 1250, 1236) | 1.3× | (60, 540, 618, 1249) |

## Rebuild

Replace the corresponding file in `../../raw/` with a new capture, retaining its filename. From the repository root run:

```sh
python3 docs/design/store-art/tools/compose_screenshots.py
python3 docs/design/store-art/verify_assets.py
```

The script resolves paths relative to itself. It scales authored crop coordinates when replacement dimensions change; if the UI layout changes, update the corresponding `SHOTS` crop/focus tuple. The raw file mapping and exact headlines are in `SHOTS`. Required dependencies: Python 3, Pillow, `/opt/homebrew/bin/rsvg-convert`, and macOS Kohinoor Bangla.

See `../../CODEX-NOTES.md` for the conflicting composition instructions, full-screen capture exceptions, and emulator limitations. All eight PNGs and the contact sheet were visually reviewed after rendering.
