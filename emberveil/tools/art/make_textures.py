#!/usr/bin/env python3
"""Generate the Emberveil Core textures.

* item/almanac.png, gui/almanac_filler.png, emberveil_logo.png, the Deeds background: original pixel art
  drawn here from palette maps / procedural shapes.
* gui/almanac_book.png: the Patchouli book GUI sheet. It must keep Patchouli's UV layout, so it is an
  adaptation of Patchouli's book_brown.png (Vazkii, CC BY-NC-SA 3.0): the frame, paper, spine, ribbon and
  icon colours are re-painted procedurally. That output file is therefore distributed under CC BY-NC-SA 3.0
  (see docs/ATTRIBUTION.md). Pass the Patchouli jar path as argv[1].
"""
import sys, zipfile, io, math, random, pathlib
from PIL import Image, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "companion-mod/src/main/resources"
TEX = RES / "assets/emberveil/textures"
(TEX / "item").mkdir(parents=True, exist_ok=True)
(TEX / "gui").mkdir(parents=True, exist_ok=True)

def hexc(h, a=255):
    h = h.lstrip("#"); return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

# ---------------------------------------------------------------- item icon (16x16, original)
ALMANAC = [
    "................",
    "...kkkkkkkkkk...",
    "..kLLLLLLLLLLkp.",
    "..kLcLLLLLLcLkp.",
    "..kLLLLLLLLLLkp.",
    "..kLLLLooLLLLkp.",
    "..kLLLoyyoLLLkp.",
    "..kLLoyWyyoLLkp.",
    "..kLLoyyWyoLLkp.",
    "..kLLLoyyoLLLkp.",
    "..kLLLLooLLLLkp.",
    "..kLLLLLLLLLLkp.",
    "..kLcLLLLLLcLkp.",
    "..kLLLLLLLrLLkp.",
    "...kkkkkkkrkkk..",
    "..........r.....",
]
PAL = {".": (0, 0, 0, 0), "k": hexc("1c120d"), "L": hexc("5a3222"), "c": hexc("c07a3a"),
       "o": hexc("d4541b"), "y": hexc("ffb347"), "W": hexc("fff1c7"), "p": hexc("efe2c4"), "r": hexc("a3231a")}
img = Image.new("RGBA", (16, 16))
for y, row in enumerate(ALMANAC):
    for x, ch in enumerate(row):
        c = PAL[ch]
        if ch == "L":  # subtle leather grain / shading, deterministic
            shade = ((x * 7 + y * 13) % 5) - 2 - (1 if x >= 11 else 0) * 3 + (2 if y <= 3 else 0)
            c = tuple(max(0, min(255, v + shade * 4)) for v in c[:3]) + (255,)
        img.putpixel((x, y), c)
img.save(TEX / "item/almanac.png")

# ---------------------------------------------------------------- page filler (128x128, original)
random.seed(7)
fill = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
d = ImageDraw.Draw(fill)
ink = hexc("7a4a2a", 150)
cx, cy = 64, 64
# a lantern emblem framed by a thin veil ring
for r in range(40, 44):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=hexc("8a5a36", 60 + (r - 40) * 10))
d.rectangle((cx - 10, cy - 22, cx + 10, cy + 18), outline=ink, width=2)
d.line((cx - 10, cy - 12, cx + 10, cy - 12), fill=ink, width=1)
d.line((cx - 10, cy + 8, cx + 10, cy + 8), fill=ink, width=1)
d.arc((cx - 7, cy - 34, cx + 7, cy - 20), 180, 360, fill=ink, width=2)
flame = [(cx, cy - 8), (cx + 6, cy + 2), (cx + 3, cy + 6), (cx - 3, cy + 6), (cx - 6, cy + 2)]
d.polygon(flame, fill=hexc("d4541b", 170))
d.polygon([(cx, cy - 3), (cx + 3, cy + 3), (cx - 3, cy + 3)], fill=hexc("ffb347", 190))
for i in range(12):  # drifting embers
    a = random.uniform(0, math.tau); rr = random.uniform(20, 36)
    x, y = cx + math.cos(a) * rr, cy + math.sin(a) * rr - 6
    d.rectangle((x, y, x + 1, y + 1), fill=hexc("d4541b", random.randint(70, 140)))
fill.save(TEX / "gui/almanac_filler.png")

# ---------------------------------------------------------------- mod logo (128x128, original)
logo = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
d = ImageDraw.Draw(logo)
for r in range(60, 0, -1):
    t = r / 60
    col = (int(40 + 200 * (1 - t) ** 2), int(20 + 110 * (1 - t) ** 3), int(18 + 40 * (1 - t) ** 4), int(255 * min(1, (1 - t) * 3 + 0.25)))
    d.ellipse((64 - r, 64 - r, 64 + r, 64 + r), fill=col)
big = ALMANAC_IMG = img.resize((80, 80), Image.NEAREST)
logo.alpha_composite(big, (24, 22))
logo.save(RES / "emberveil_logo.png")

# ---------------------------------------------------------------- Deeds advancement background (16x16 tile, original)
bg = Image.new("RGBA", (16, 16))
for y in range(16):
    for x in range(16):
        n = ((x * 31 + y * 17) ^ (x * y)) % 9
        bg.putpixel((x, y), (46 + n, 34 + n // 2, 30 + n // 3, 255))
bg.save(TEX / "gui/deeds_background.png")

# ---------------------------------------------------------------- GUI sheet (adapted from Patchouli)
if len(sys.argv) > 1:
    with zipfile.ZipFile(sys.argv[1]) as zf:
        base = Image.open(io.BytesIO(zf.read("assets/patchouli/textures/gui/book_brown.png"))).convert("RGBA")
    out = base.copy()
    px = out.load()
    W, H = out.size

    def lum(c): return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]

    def remap(c, dark, light):
        t = max(0.0, min(1.0, lum(c) / 255.0))
        return tuple(int(dark[i] + (light[i] - dark[i]) * t) for i in range(3)) + (c[3],)

    frame_dark, frame_light = hexc("120c0a"), hexc("7c4a2e")
    paper_dark, paper_light = hexc("c9ad86"), hexc("f7ecd6")
    for y in range(H):
        for x in range(W):
            c = px[x, y]
            if c[3] == 0:
                continue
            r, g, b, a = c
            in_book = x < 272 and y < 180
            is_paper = in_book and lum(c) > 170 and r > b + 8
            is_goldish = r > 200 and g > 150 and b < 90  # highlighted icon variants
            if in_book and is_paper:
                # parchment with gentle edge darkening for depth; stays light for readability
                ex = min(x - 12, 260 - x, y - 8, 172 - y) if 12 < x < 260 and 8 < y < 172 else 0
                edge = max(0.0, 1.0 - ex / 18.0) * 0.12
                nc = remap(c, paper_dark, paper_light)
                px[x, y] = tuple(int(v * (1 - edge)) for v in nc[:3]) + (a,)
            elif in_book or (y >= 180 and y < 215 and x < 140):
                px[x, y] = remap(c, frame_dark, frame_light)
            elif is_goldish:
                px[x, y] = remap(c, hexc("7a2e0e"), hexc("ffb347"))
    # copper trim just inside the cover edge, and ember studs at the four corners
    d = ImageDraw.Draw(out)
    d.rectangle((5, 4, 266, 175), outline=hexc("b8692f", 200))
    for (sx, sy) in ((9, 8), (262, 8), (9, 171), (262, 171)):
        d.rectangle((sx - 1, sy - 1, sx + 1, sy + 1), fill=hexc("ff8a3d"))
        d.point((sx, sy), fill=hexc("fff1c7"))
    # spine stitches -> copper thread
    for y in range(8, 176):
        for x in range(130, 142):
            c = px[x, y]
            if c[3] and lum(c) < 120:
                px[x, y] = remap(c, hexc("6b3217"), hexc("d08440"))
    # nameplate ribbon (0,180)-(140,210): ember cloth
    for y in range(180, 215):
        for x in range(0, 140):
            c = px[x, y]
            if c[3]:
                px[x, y] = remap(c, hexc("3a0f07"), hexc("b8401a"))
    out.save(TEX / "gui/almanac_book.png")
    print("gui sheet written")
print("textures written to", TEX)
