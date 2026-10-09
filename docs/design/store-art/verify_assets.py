#!/usr/bin/env python3
"""Checks the files asked for in docs/design/codex-store-art-prompts.md.

Run from the repository root: python3 docs/design/store-art/verify_assets.py
Prints one line per check and ends with ALL CHECKS PASSED, or exits 1 listing what failed.
It checks presence, pixel size, alpha, file size, the adaptive-icon safe zone and that the
closed palettes were respected in the icons. It cannot judge whether an icon is good.
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
SLUGS = {1: "pulse-tick", 2: "ring", 3: "found-it"}
GLOBAL = [(0x12, 0x15, 0x1A), (0x1D, 0x1F, 0x25), (0xD9, 0xFE, 0x06), (0xFF, 0xFF, 0xFF), (0x34, 0xD3, 0x99)]
KRY = [(0x2E, 0x32, 0xD4), (0x00, 0x42, 0xC6), (0x00, 0xC2, 0xCC), (0xC0, 0xCD, 0xD8), (0xFF, 0xFF, 0xFF)]
fails = []


def check(ok, msg):
    print(("ok   " if ok else "FAIL ") + msg)
    if not ok:
        fails.append(msg)


def img(rel):
    p = ROOT / rel
    if not p.exists():
        check(False, f"{rel} exists")
        return None
    return Image.open(p)


def has_alpha(im):
    return im.mode in ("RGBA", "LA") and im.getchannel("A").getextrema()[0] < 255


def off_palette_share(im, palette, tol=40):
    """Share of pixels further than `tol` (sum of channel distances) from every palette colour.
    Anti-aliased edges sit between two palette colours, so a few percent is normal."""
    small = im.convert("RGB").resize((128, 128))
    px = list(small.getdata()) if not hasattr(small, "get_flattened_data") else list(small.get_flattened_data())
    bad = sum(1 for p in px if min(sum(abs(p[i] - c[i]) for i in range(3)) for c in palette) > tol)
    return bad / len(px)


for n, slug in SLUGS.items():
    for theme, palette in (("global", GLOBAL), ("kry", KRY)):
        base = f"icons/{theme}-{n}-{slug}"
        check((ROOT / f"{base}.svg").exists(), f"{base}.svg exists")
        im = img(f"{base}.png")
        if im:
            check(im.size == (1024, 1024), f"{base}.png is 1024x1024 (is {im.size})")
            check(not has_alpha(im), f"{base}.png has no transparency")
            share = off_palette_share(im, palette)
            check(share < 0.12, f"{base}.png stays in the {theme} palette ({share:.0%} of pixels off-palette)")
        im = img(f"{base}-512.png")
        if im:
            check(im.size == (512, 512), f"{base}-512.png is 512x512 (is {im.size})")
            check(not has_alpha(im), f"{base}-512.png has no transparency")
            kb = (ROOT / f"{base}-512.png").stat().st_size / 1024
            check(kb <= 1024, f"{base}-512.png is under 1 MB ({kb:.0f} KB)")
    for kind in ("foreground", "monochrome"):
        base = f"icons/global-{n}-{slug}-{kind}"
        check((ROOT / f"{base}.svg").exists(), f"{base}.svg exists")
        im = img(f"{base}.png")
        if im:
            check(im.size == (1024, 1024), f"{base}.png is 1024x1024 (is {im.size})")
            check(has_alpha(im), f"{base}.png has a transparent background")
            if im.mode == "RGBA":
                bb = im.getchannel("A").point(lambda v: 255 if v > 16 else 0).getbbox()
                lo, hi = 1024 * 0.17 - 2, 1024 * 0.83 + 2
                inside = bb is not None and bb[0] >= lo and bb[1] >= lo and bb[2] <= hi and bb[3] <= hi
                check(inside, f"{base}.png art is inside the central 66% safe zone (bbox {bb})")
                if kind == "monochrome" and bb:
                    cols = {c[:3] for _, c in (im.crop(bb).resize((64, 64)).getcolors(4096) or []) if c[3] > 200}
                    check(len(cols) <= 3, f"{base}.png is one solid colour (found {len(cols)} opaque colours)")

for rel in ("icons/icon-sizes.png", "icons/NOTES.md"):
    check((ROOT / rel).exists(), f"{rel} exists")

for name in ("kry-feature", "global-feature"):
    check((ROOT / f"feature/{name}.svg").exists(), f"feature/{name}.svg exists")
    im = img(f"feature/{name}.png")
    if im:
        check(im.size == (1024, 500), f"feature/{name}.png is 1024x500 (is {im.size})")
        check(not has_alpha(im), f"feature/{name}.png has no transparency")

SHOTS = ["01-imei-check", "02-display-test", "03-mic-test", "04-phone-check", "05-camera-test", "06-touch-test",
         "07-battery", "08-report"]
BLUE = (0x2E, 0x32, 0xD4)
for s in SHOTS:
    im = img(f"screenshots/kry/{s}.png")
    if im:
        check(im.size == (1080, 1920), f"screenshots/kry/{s}.png is 1080x1920 (is {im.size})")
        check(not has_alpha(im), f"screenshots/kry/{s}.png has no transparency")
        rgb = im.convert("RGB")
        # the whole phone must be inside the canvas: the bottom 24 rows are background, not device
        row = [rgb.getpixel((x, 1908)) for x in range(20, 1080, 40)]
        dark = sum(1 for p in row if sum(p) < 150)
        check(dark <= 2, f"screenshots/kry/{s}.png phone does not run off the bottom edge ({dark} dark samples)")
for rel in ("screenshots/kry/contact-sheet.png", "screenshots/kry/README.md", "tools/compose_screenshots.py"):
    check((ROOT / rel).exists(), f"{rel} exists")

print()
if fails:
    print(f"{len(fails)} CHECK(S) FAILED:")
    for f in fails:
        print("  - " + f)
    sys.exit(1)
print("ALL CHECKS PASSED")
