#!/usr/bin/env python3
"""
Moonforged texture generator: items, weapons, blocks, effects and armor layers for the Pale Reach expansion.
Reuses the helpers of gen_textures.py / gen_weapons.py. Needs the vanilla item textures for the Moonsilver tool
silhouettes: --vanilla <.../assets/minecraft/textures/item>.
"""
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_textures as T  # noqa: E402
import gen_weapons as W  # noqa: E402

hexc, lerp, save = T.hexc, T.lerp, T.save

MOON = ["#161a28", "#2a3248", "#465272", "#6a7a9e", "#94a6c8", "#bccce6", "#dfe9f8", "#ffffff"]
OUT = "#12141f"
GLOW = ["#2c5ea8", "#4f8ce0", "#7ab4ff", "#a8d4ff", "#e0f2ff"]
PEARL = ["#b8a8c8", "#e6dcef", "#fff8ff"]
SLATE = ["#1a1c26", "#2c3040", "#444a60"]


def ramp(colors, t):
    return W.ramp(colors, t)


def new():
    return W.new()


def outline(img, color=OUT):
    return W.outline(img, color)


def item(img, name):
    save(img, f"item/{name}.png")


def disc(px, cx, cy, r, colors, shade=True):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d < r:
                t = d / r
                if shade:
                    t = min(1.0, t * 0.7 + max(0.0, (x - cx + y - cy) / (2 * r)) * 0.5)
                px[x, y] = ramp(colors, t)


# ----------------------------------------------------------------------------------------------------------------
# Moonsilver tools: vanilla diamond silhouettes, recoloured silver-blue with slate grips.
# ----------------------------------------------------------------------------------------------------------------

def moonsilver_tools(vanilla):
    tool_ramp = ["#202638", "#3e4a68", "#5e6e94", "#8090b8", "#a6b6d8", "#c8d6ee", "#e4ecfa", "#ffffff"]
    for tool in ("sword", "pickaxe", "axe", "shovel", "hoe"):
        im = Image.open(os.path.join(vanilla, f"diamond_{tool}.png")).convert("RGBA")
        px = im.load()
        head = sorted({px[x, y] for y in range(16) for x in range(16) if px[x, y][3] and not W.is_wood(px[x, y])}, key=W.lum)
        mapping = {c: hexc(tool_ramp[round(i * (len(tool_ramp) - 1) / max(1, len(head) - 1))]) for i, c in enumerate(head)}
        for y in range(16):
            for x in range(16):
                c = px[x, y]
                if c[3] and c in mapping:
                    px[x, y] = mapping[c]
                elif c[3] and W.is_wood(c):
                    px[x, y] = lerp(hexc("#161824"), hexc("#3e4660"), W.lum(c) / 140.0)
        item(im, f"moonsilver_{tool}")


# ----------------------------------------------------------------------------------------------------------------
# Legendary weapons
# ----------------------------------------------------------------------------------------------------------------

def crescent(px, cx, cy, r_out, off, colors, edge=None):
    """A crescent: inside a circle, outside a shifted circle."""
    for y in range(16):
        for x in range(16):
            d1 = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            d2 = math.hypot(x + 0.5 - (cx + off[0]), y + 0.5 - (cy + off[1]))
            if d1 < r_out and d2 > r_out * 0.82:
                px[x, y] = ramp(colors, min(1.0, (r_out - d1) / r_out * 1.6))
                if edge and d1 > r_out - 1.0:
                    px[x, y] = hexc(edge)


def crescent_glaive():
    img, px = new()
    W.line(px, (0, 15), (9, 6), SLATE[2])
    W.line(px, (1, 15), (10, 6), SLATE[0])
    for (x, y) in ((3, 12), (6, 9)):
        px[x, y] = hexc(MOON[5])
    crescent(px, 11.5, 4.5, 4.6, (-2.2, 2.2), MOON[4:], edge=MOON[7])
    px[9, 6] = hexc(GLOW[3])
    px[10, 5] = hexc(GLOW[2])
    item(outline(img), "crescent_glaive")


def tidecaller_glaive():
    img, px = new()
    W.line(px, (0, 15), (8, 7), "#1c2a4a")
    W.line(px, (1, 15), (9, 7), "#0e1628")
    for (x, y) in ((2, 13), (4, 11), (6, 9)):
        px[x, y] = hexc(GLOW[2])
    crescent(px, 11.0, 5.0, 5.6, (-2.4, 2.6), [GLOW[0], GLOW[1], GLOW[3], GLOW[4]], edge="#ffffff")
    # Wave crest on the blade.
    for (x, y) in ((12, 2), (13, 3), (14, 4), (13, 1), (11, 2)):
        if px[x, y][3]:
            px[x, y] = hexc("#ffffff")
    disc(px, 8.5, 7.5, 1.6, PEARL[::-1], shade=False)
    item(outline(img), "tidecaller_glaive")


def orrery_staff():
    img, px = new()
    W.line(px, (1, 15), (8, 8), SLATE[2])
    W.line(px, (2, 15), (9, 8), SLATE[0])
    for a in range(0, 360, 12):
        x = round(10.5 + math.cos(math.radians(a)) * 4.0 - 0.5)
        y = round(5.5 + math.sin(math.radians(a)) * 4.0 - 0.5)
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = hexc(MOON[5] if a % 24 else MOON[3])
    disc(px, 10.5, 5.5, 2.0, [MOON[7], MOON[5], MOON[3]])
    for (x, y, c) in ((14, 4, GLOW[3]), (7, 3, GLOW[3]), (12, 9, GLOW[2])):
        px[x, y] = hexc(c)
        if x + 1 < 16:
            px[x + 1, y] = hexc(GLOW[1])
    item(outline(img), "orrery_staff")


def phase_daggers():
    # Two crossed moonsilver daggers, glowing guards at the crossing.
    rows = [
        "................",
        ".W............W.",
        ".Wa..........aW.",
        "..Wa........aW..",
        "...Wa......aW...",
        "....Wa....aW....",
        ".....Wa..aW.....",
        "......WaaW......",
        ".....gGaaGg.....",
        "....g.GhhG.g....",
        "......hGGh......",
        ".....h....h.....",
        "....h......h....",
        "...o........o...",
        "................",
        "................",
    ]
    img = T.sprite(rows, {"W": "#ffffff", "a": MOON[5], "g": GLOW[2], "G": GLOW[3], "h": SLATE[2], "o": GLOW[1]})
    item(outline(img), "phase_daggers")


def moonshot_crossbow(vanilla):
    # Vanilla crossbow silhouette in moonsilver and slate, loaded with a glowing bolt of moonlight.
    im = Image.open(os.path.join(vanilla, "crossbow_standby.png")).convert("RGBA")
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = px[x, y]
            if not c[3]:
                continue
            if W.is_wood(c):
                px[x, y] = lerp(hexc(MOON[1]), hexc(MOON[5]), min(1.0, W.lum(c) / 130.0))
            else:
                px[x, y] = ramp([MOON[2], MOON[4], MOON[6], "#ffffff"], min(1.0, W.lum(c) / 200.0))
    for (x, y, col) in ((6, 9, GLOW[1]), (7, 8, GLOW[2]), (8, 7, GLOW[3]), (9, 6, GLOW[4]), (10, 5, "#ffffff")):
        px[x, y] = hexc(col)
    item(im, "moonshot_crossbow")


def stasis_bell():
    rows = [
        "................",
        "......####......",
        ".....#gGGg#.....",
        "......####......",
        ".....#aaab#.....",
        "....#aaabbc#....",
        "....#aabbbc#....",
        "...#aabbbbcc#...",
        "...#abbbbccd#...",
        "..#abbbbcccdd#..",
        "..#bbbbccccdd#..",
        ".#ggggggggggggg#",
        "..#############.",
        ".......#e#......",
        "........#.......",
        "................",
    ]
    item(T.sprite(rows, {"#": OUT, "a": MOON[7], "b": MOON[5], "c": MOON[4], "d": MOON[3], "g": GLOW[2], "G": GLOW[4], "e": GLOW[3]}), "stasis_bell")


def tether_hook():
    img, px = new()
    # Chain down to the bottom-left.
    for i in range(7):
        x, y = 1 + i, 14 - i
        px[x, y] = hexc(MOON[4] if i % 2 else MOON[2])
    # Shaft and three prongs.
    W.line(px, (8, 7), (11, 4), MOON[5])
    for (a, b) in (((11, 4), (14, 1)), ((11, 4), (11, 0)), ((11, 4), (15, 4))):
        W.line(px, a, b, MOON[6])
    for (x, y) in ((13, 0), (10, 1), (15, 3), (14, 5)):
        px[x, y] = hexc(MOON[4])
    px[11, 4] = hexc(GLOW[3])
    item(outline(img), "tether_hook")


def lunar_key():
    img, px = new()
    crescent(px, 5.0, 5.0, 4.4, (1.8, -1.8), [MOON[7], MOON[5], GLOW[2]])
    W.line(px, (7, 8), (13, 14), MOON[5])
    W.line(px, (8, 8), (14, 14), MOON[3])
    for (x, y) in ((11, 13), (12, 12), (13, 15), (14, 14)):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = hexc(MOON[6])
    px[4, 4] = hexc(GLOW[4])
    item(outline(img), "lunar_key")


def tidal_sigil():
    img, px = new()
    disc(px, 8, 8, 6.6, [GLOW[1], GLOW[0], "#14284e"])
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 5.6 < d < 6.6:
                px[x, y] = hexc(MOON[6])
    for x in range(3, 13):
        y = round(9 + math.sin(x * 0.9) * 1.2)
        px[x, y] = hexc(GLOW[4])
        px[x, y + 2] = hexc(GLOW[3])
    crescent(px, 8, 6.0, 2.6, (1.0, -0.8), [MOON[7], MOON[6]])
    item(outline(img), "tidal_sigil")


def moon_heart():
    img, px = new()
    disc(px, 8, 8, 6.0, [MOON[7], MOON[6], MOON[5], MOON[3]])
    rng = random.Random(4)
    for _ in range(5):
        cx, cy = rng.uniform(4, 12), rng.uniform(4, 12)
        for y in range(16):
            for x in range(16):
                if px[x, y][3] and math.hypot(x + 0.5 - cx, y + 0.5 - cy) < 1.2:
                    px[x, y] = lerp(px[x, y], hexc(MOON[3]), 0.5)
    for y in range(16):
        for x in range(16):
            if px[x, y][3] and math.hypot(x + 0.5 - 10.5, y + 0.5 - 6.5) < 5.0:
                px[x, y] = lerp(px[x, y], hexc("#1a2c58"), 0.55)
    px[5, 5] = hexc("#ffffff")
    item(outline(img, "#0e1430"), "moon_heart")


def materials():
    rng = random.Random(12)
    img, px = new()
    for (cx, cy, r) in ((6, 9, 3.6), (10, 7, 3.0), (9, 11, 2.8)):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d < r:
                    px[x, y] = ramp([MOON[6], MOON[4], MOON[2]], d / r * 0.8 + rng.random() * 0.2)
    for (x, y) in ((5, 8), (9, 6), (9, 10)):
        px[x, y] = hexc(GLOW[4])
    item(outline(img), "raw_moonsilver")

    rows = [
        "................",
        "................",
        "................",
        "................",
        "......#######...",
        ".....#wwyyyyy#..",
        "....#wyyyyyyyY#.",
        "...#wyyyyyyyYY#.",
        "..#yyyyyyyyYYo#.",
        ".#ooooooooooo#..",
        ".#YYYYYYYYYYo#..",
        ".#ooooooooooo#..",
        "..###########...",
        "................",
        "................",
        "................",
    ]
    item(T.sprite(rows, {"#": OUT, "w": MOON[7], "y": MOON[5], "Y": MOON[4], "o": MOON[3]}), "moonsilver_ingot")

    img, px = new()
    for i in range(10):
        x0, y0 = 4 + i * 0.6, 13 - i * 1.1
        for w in range(-1, 2):
            x, y = round(x0 + w * 0.8), round(y0 + w * 0.4)
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = ramp([MOON[7], MOON[5], GLOW[2]], abs(w) * 0.6 + i * 0.03)
    px[9, 3] = hexc("#ffffff")
    item(outline(img), "selenite_shard")

    img, px = new()
    for y in range(9, 15):
        half = (y - 8) * 0.9
        for x in range(round(8 - half), round(8 + half)):
            if 0 <= x < 16:
                px[x, y] = ramp([MOON[6], MOON[4], MOON[3]], rng.random() * 0.8)
    for _ in range(6):
        px[rng.randint(4, 11), rng.randint(9, 13)] = hexc(GLOW[4])
    for (x, y) in ((5, 6), (10, 5), (8, 3)):
        px[x, y] = hexc(GLOW[3])
    item(outline(img), "lunar_dust")

    img, px = new()
    disc(px, 8, 8.5, 4.5, PEARL[::-1])
    px[6, 6] = hexc("#ffffff")
    px[7, 6] = hexc("#ffffff")
    item(outline(img, "#3a3048"), "lunar_pearl")

    img, px = new()
    W.line(px, (3, 12), (12, 3), MOON[6])
    px[13, 2] = hexc(GLOW[4])
    px[12, 2] = hexc(GLOW[3])
    px[13, 3] = hexc(GLOW[3])
    item(outline(img), "moonshot_bolt")


def armor_icons():
    pal = {"#": OUT, "a": MOON[7], "b": MOON[5], "c": MOON[4], "d": MOON[3], "h": GLOW[2], "H": GLOW[1], "g": GLOW[3],
           "y": MOON[6], "Y": MOON[4], "w": "#ffffff"}
    for name, rows in (("helmet", T.HELMET), ("chestplate", T.CHESTPLATE), ("leggings", T.LEGGINGS), ("boots", T.BOOTS)):
        item(T.sprite(rows, pal), f"moonsilver_{name}")
    crown = T.sprite([
        "................",
        "................",
        "...#........#...",
        "..#g#......#g#..",
        "..#gg#....#gg#..",
        "...#gg#..#gg#...",
        "..#.#yy##yy#.#..",
        "..#y#yyyyyy#y#..",
        "..#yyyyyyyyyy#..",
        "..#ywwyhhywwy#..",
        "..#HHHHHHHHHH#..",
        "...##########...",
        "................",
        "................",
        "................",
        "................",
    ], {"#": OUT, "y": MOON[6], "w": "#ffffff", "g": GLOW[3], "h": GLOW[2], "H": GLOW[1]})
    item(crown, "crown_of_tides")


def spawn_eggs():
    eggs = {
        "regolith_skimmer": ("#8e96a8", "#9cf0ff"),
        "lunar_moth": ("#3a3550", "#d8d4ea"),
        "selenite_sentinel": ("#7c8496", "#dfe8f4"),
        "umbral_lurker": ("#121218", "#e8e6f0"),
        "moonkit": ("#d8dce6", "#7ac0ff"),
        "moonleaper": ("#c8cce0", "#6e7290"),
        "pale_matriarch": ("#cfd8ea", "#4f8ce0"),
    }
    for name, (base, spot) in eggs.items():
        rng = random.Random(name)
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y in range(16):
            for x in range(16):
                dy = (y - 9.0) / (6.0 if y > 9 else 7.2)
                d = math.hypot((x - 7.5) / 5.0, dy)
                if d < 1.0:
                    px[x, y] = lerp(hexc(base), lerp(hexc(base), (0, 0, 0, 255), 0.5), max(0, (x - 7) / 10 + d * 0.3))
        for _ in range(7):
            x, y = rng.randint(4, 11), rng.randint(4, 13)
            if px[x, y][3]:
                px[x, y] = hexc(spot)
                if x + 1 < 16 and px[x + 1, y][3] and rng.random() < 0.5:
                    px[x + 1, y] = hexc(spot)
        px[5, 4] = lerp(hexc(base), (255, 255, 255, 255), 0.5)
        item(T.outline(img, (14, 16, 26, 255)), f"{name}_spawn_egg")


# ----------------------------------------------------------------------------------------------------------------
# Blocks
# ----------------------------------------------------------------------------------------------------------------

def block(img, name):
    save(img, f"block/{name}.png")


def animated(frames, name, frametime):
    sheet = Image.new("RGBA", (16, 16 * len(frames)))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, i * 16))
    block(sheet, name)
    with open(os.path.join(T.ASSETS, f"block/{name}.png.mcmeta"), "w") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}\n' % frametime)


def stone(seed, light="#b4bccc", dark="#8a92a6", speck="#6c748a"):
    rng = random.Random(seed)
    img = T.noise_tex(seed, light, dark, 1.0)
    px = img.load()
    for _ in range(14):
        px[rng.randint(0, 15), rng.randint(0, 15)] = hexc(speck)
    for _ in range(6):
        px[rng.randint(0, 15), rng.randint(0, 15)] = hexc("#d6dce8")
    return img


def craters(img, seed, n, dark, light):
    rng = random.Random(seed)
    px = img.load()
    for _ in range(n):
        cx, cy, r = rng.uniform(1, 15), rng.uniform(1, 15), rng.uniform(1.2, 2.6)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d < r:
                    px[x, y] = lerp(px[x, y], hexc(dark), 0.45 * (1 - d / r) + 0.15)
                elif d < r + 0.8 and (x - cx) + (y - cy) > 0:
                    px[x, y] = lerp(px[x, y], hexc(light), 0.4)
    return img


def bricks(seed, cracked=False, light="#c4ccdc", dark="#98a2b8", mortar="#5c6478"):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            off = 4 if (y // 4) % 2 else 0
            if y % 4 == 3 or (x + off) % 8 == 7:
                c = hexc(mortar)
            else:
                c = lerp(hexc(light), hexc(dark), r.random() * 0.7)
                if y % 4 == 0:
                    c = lerp(c, hexc("#e8eef8"), 0.3)
            px[x, y] = c
    if cracked:
        x, y = 4, 0
        for _ in range(18):
            px[x % 16, y % 16] = hexc("#3c4256")
            x += r.choice((0, 1))
            y += 1
    return img


def phase_disc(px, phase, cx=7.5, cy=7.5, r=4.6, lit=GLOW[4], dark="#1c2440"):
    """Draws a moon at phase 0=new, 1=waxing, 2=full, 3=waning."""
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d > r:
                continue
            lx = (x - cx) / r
            if phase == 0:
                on = False
            elif phase == 2:
                on = True
            elif phase == 1:
                on = lx > 0.15
            else:
                on = lx < -0.15
            px[x, y] = hexc(lit) if on else hexc(dark)
            if abs(d - r) < 0.7:
                px[x, y] = hexc(GLOW[2])


def blocks():
    rng = random.Random(55)
    block(stone(1), "moonstone")
    block(craters(T.noise_tex(2, "#d2d6de", "#a8aeba", 1.0), 3, 3, "#7c8292", "#eef0f4"), "regolith")
    block(craters(T.noise_tex(4, "#3a3c48", "#24262e", 1.0), 5, 3, "#121218", "#50525e"), "umbral_regolith")
    sand = T.noise_tex(6, "#eef2f8", "#c6ccd8", 1.0)
    px = sand.load()
    for _ in range(10):
        px[rng.randint(0, 15), rng.randint(0, 15)] = hexc("#ffffff")
    block(sand, "silver_sand")

    ore = stone(1)
    px = ore.load()
    for cluster in ((4, 4), (11, 5), (5, 11), (12, 12)):
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1), (2, 1), (1, 2)):
            x = min(15, max(0, cluster[0] + dx))
            y = min(15, max(0, cluster[1] + dy))
            px[x, y] = ramp([MOON[7], GLOW[3], GLOW[1]], (dx + dy + 1) / 4)
        px[cluster] = hexc("#ffffff")
    block(ore, "moonsilver_ore")

    # Selenite cluster (cross model): pale blades.
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for (bx, h, w) in ((3, 9, 2), (7, 14, 3), (11, 10, 2), (13, 6, 1)):
        for y in range(16 - h, 16):
            for x in range(bx, bx + w):
                t = (y - (16 - h)) / h
                px[x, y] = ramp([MOON[7], GLOW[4], GLOW[2]], t * 0.8 + (x - bx) * 0.15)
        px[bx, 16 - h] = hexc("#ffffff")
    block(img, "selenite_cluster")

    # Moonpetal (cross model).
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(8, 16):
        px[8, y] = hexc("#7a90a8")
    for (x, y) in ((6, 12), (10, 11), (5, 11), (11, 10)):
        px[x, y] = hexc("#8aa0b8")
    for a in range(0, 360, 45):
        for r in (1, 2, 3):
            x = round(8 + math.cos(math.radians(a)) * r)
            y = round(5 + math.sin(math.radians(a)) * r)
            px[x, y] = hexc(MOON[7] if r < 3 else MOON[5])
    px[8, 5] = hexc(GLOW[3])
    block(img, "moonpetal")

    # Tidal clam: ribbed shell outside, pearly inside.
    shell = Image.new("RGBA", (16, 16))
    px = shell.load()
    for y in range(16):
        for x in range(16):
            c = lerp(hexc("#d8dce8"), hexc("#9aa2b6"), ((x % 3) == 0) * 0.5 + rng.random() * 0.2)
            px[x, y] = c
    block(shell, "tidal_clam_shell")
    inner = Image.new("RGBA", (16, 16))
    px = inner.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = lerp(hexc("#f0dcec"), hexc("#c8a8c8"), rng.random() * 0.5)
    block(inner, "tidal_clam_inner")
    pearl = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    disc(pearl.load(), 8, 8, 7.5, PEARL[::-1])
    block(pearl, "tidal_clam_pearl")

    mb = Image.new("RGBA", (16, 16))
    px = mb.load()
    for y in range(16):
        for x in range(16):
            c = ramp(MOON[3:], 0.9 - (x + y) / 40 - rng.random() * 0.1)
            if x in (0, 15) or y in (0, 15):
                c = hexc(MOON[3])
            if (x, y) in ((3, 3), (12, 12), (4, 11)):
                c = hexc(GLOW[3])
            px[x, y] = c
    block(mb, "moonsilver_block")

    block(bricks(10), "lunar_bricks")
    block(bricks(11, cracked=True), "cracked_lunar_bricks")
    ch = bricks(12)
    px = ch.load()
    for y in range(3, 13):
        for x in range(3, 13):
            d1 = math.hypot(x - 7.5, y - 7.5)
            d2 = math.hypot(x - 9.0, y - 6.0)
            if 2.0 < d1 < 4.6 and d2 > 3.6:
                px[x, y] = hexc(GLOW[3])
    block(ch, "chiseled_lunar_bricks")

    glass = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = glass.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = hexc(MOON[5])
            elif (x + y) % 7 == 0 and x > 2 and y > 2:
                px[x, y] = hexc("#e0eeff", 140)
            else:
                px[x, y] = hexc("#a8c8f0", 60)
    block(glass, "moon_glass")

    frames = []
    for f in range(4):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5) / 8
                pulse = 0.85 + 0.15 * math.sin(f * math.pi / 2)
                c = ramp([MOON[7], GLOW[4], GLOW[2], MOON[3]], d * (1.1 - pulse * 0.2))
                if x in (0, 15) or y in (0, 15):
                    c = hexc(MOON[2])
                px[x, y] = c
        frames.append(img)
    animated(frames, "moon_lantern", 10)

    plate = bricks(13)
    px = plate.load()
    for a in range(0, 360, 10):
        for r in (5.5, 3.0):
            x = round(7.5 + math.cos(math.radians(a)) * r)
            y = round(7.5 + math.sin(math.radians(a)) * r)
            px[x, y] = hexc(GLOW[3])
    for (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)):
        px[x, y] = hexc("#ffffff")
    block(plate, "gravity_plate")

    side = Image.new("RGBA", (16, 16))
    px = side.load()
    for y in range(16):
        for x in range(16):
            c = ramp(MOON[2:7], (y % 8) / 8 * 0.6 + rng.random() * 0.15)
            if y in (0, 7, 8, 15):
                c = hexc(GLOW[2])
            px[x, y] = c
    block(side, "orrery_ring_side")
    for phase, name in enumerate(("new", "waxing", "full", "waning")):
        top = Image.new("RGBA", (16, 16))
        px = top.load()
        for y in range(16):
            for x in range(16):
                px[x, y] = hexc(MOON[2])
                d = math.hypot(x - 7.5, y - 7.5)
                if 6.2 < d < 7.6:
                    px[x, y] = hexc(MOON[5])
        phase_disc(px, phase)
        block(top, f"orrery_ring_{name}")
        mural = bricks(20 + phase, light="#a8b0c2", dark="#7e889e")
        px = mural.load()
        phase_disc(px, phase, r=5.2, lit="#e8f2ff", dark="#2a3450")
        for x in range(16):
            px[x, 0] = hexc(GLOW[2])
            px[x, 15] = hexc(GLOW[2])
        block(mural, f"lunar_mural_{name}")

    con = Image.new("RGBA", (16, 16))
    px = con.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = hexc(MOON[2])
            d = math.hypot(x - 7.5, y - 7.5)
            if abs(d - 6.5) < 0.6 or abs(d - 3.5) < 0.6:
                px[x, y] = hexc(MOON[6])
            if abs(x - 7.5) < 0.6 or abs(y - 7.5) < 0.6:
                if d < 6.5:
                    px[x, y] = hexc(MOON[4])
    for (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)):
        px[x, y] = hexc(GLOW[4])
    for a in (40, 160, 280):
        px[round(7.5 + math.cos(math.radians(a)) * 6.5), round(7.5 + math.sin(math.radians(a)) * 6.5)] = hexc(GLOW[3])
    block(con, "orrery_console_top")
    block(bricks(30, light="#8c96aa", dark="#6a7488"), "orrery_console_side")

    frames = []
    for f in range(8):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                v = 0.5 + 0.5 * math.sin((x * 0.6 + y * 0.35 + f * 0.8))
                c = ramp([MOON[7], GLOW[4], GLOW[3]], v * 0.7)
                px[x, y] = c[:3] + (200,)
        frames.append(img)
    animated(frames, "moon_seal", 3)

    for part, colors in (("side", ("#c4ccdc", "#8a94aa")), ("bottom", ("#8a94aa", "#6a7488"))):
        img = T.noise_tex(40 if part == "side" else 41, *colors)
        px = img.load()
        if part == "side":
            for x in range(16):
                px[x, 2] = hexc(GLOW[2])
                px[x, 12] = hexc(GLOW[2])
        block(img, f"moon_altar_{part}")
    top = T.noise_tex(42, "#c4ccdc", "#9aa4b8")
    px = top.load()
    phase_disc(px, 2, r=4.0)
    for a in range(0, 360, 30):
        x = round(7.5 + math.cos(math.radians(a)) * 6.2)
        y = round(7.5 + math.sin(math.radians(a)) * 6.2)
        px[x, y] = hexc(GLOW[3])
    block(top, "moon_altar_top")

    frames = []
    for f in range(8):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                v = 0.5 + 0.5 * math.sin(x * 0.7 + f * 0.785) * math.cos(y * 0.6 - f * 0.785)
                px[x, y] = ramp([MOON[7], MOON[6], GLOW[4], GLOW[2]], v * 0.8)
        frames.append(img)
    animated(frames, "lunar_gateway", 3)


# ----------------------------------------------------------------------------------------------------------------
# Effects & armor layers
# ----------------------------------------------------------------------------------------------------------------

def effects():
    rng = random.Random(77)
    for name, size in (("moonlet", 32), ("falling_moon", 64)):
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        px = img.load()
        c = size / 2
        crs = [(rng.uniform(0.2, 0.8) * size, rng.uniform(0.2, 0.8) * size, rng.uniform(0.05, 0.14) * size) for _ in range(9)]
        for y in range(size):
            for x in range(size):
                d = math.hypot(x + 0.5 - c, y + 0.5 - c) / c
                if d < 0.92:
                    col = lerp(hexc("#ffffff"), hexc("#b8c4dc"), d * 0.7 + max(0, (x - y) / size) * 0.3)
                    for (cx, cy, r) in crs:
                        dc = math.hypot(x - cx, y - cy)
                        if dc < r:
                            col = lerp(col, hexc("#8c96b0"), 0.5 * (1 - dc / r) + 0.2)
                    px[x, y] = col
                elif d < 1.0:
                    px[x, y] = hexc("#cfe4ff", int(255 * (1 - (d - 0.92) / 0.08)))
        save(img, f"entity/{name}.png")

    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    for y in range(32):
        for x in range(32):
            d1 = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            d2 = math.hypot(x + 0.5 - 20, y + 0.5 - 12)
            if d1 < 14 and d2 > 11.5:
                edge = min(1.0, (14 - d1) / 3.0, (d2 - 11.5) / 2.0)
                px[x, y] = lerp(hexc("#9cc8ff"), hexc("#ffffff"), edge)[:3] + (int(255 * min(1.0, edge * 1.5)),)
    save(img, "entity/crescent.png")

    # Tide wave: a curling crest with foam, soft left/right edges (the renderer overlaps several of these side by side)
    # and a body that fades out towards the ground.
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for x in range(size):
        u = x / (size - 1)
        side = math.sin(math.pi * u) ** 0.8
        crest = 0.22 + 0.10 * math.sin(u * math.pi * 2.0 + 0.6) + 0.05 * math.sin(u * math.pi * 6.0)
        for y in range(size):
            v = y / (size - 1)
            if v < crest:
                continue
            depth = (v - crest) / (1.0 - crest)
            foam = depth < 0.12 and ((x * 5 + y * 3) % 7 < 4 or depth < 0.05)
            col = hexc("#ffffff") if foam else lerp(hexc("#d8ecff"), hexc("#3f6eb4"), min(1.0, depth * 1.3))
            streak = 0.12 * math.sin(x * 0.9 + y * 0.35) if not foam else 0.0
            col = lerp(col, hexc("#ffffff"), max(0.0, streak))
            alpha = side * (1.0 - depth) ** 1.4 * (1.0 if foam else 0.85)
            px[x, y] = col[:3] + (int(255 * max(0.0, min(1.0, alpha))),)
    save(img, "entity/tide_wave.png")


def armor_layers():
    rng = random.Random(41)
    main, dark, light, glow = hexc(MOON[5]), hexc(MOON[2]), hexc(MOON[7]), hexc(GLOW[2])
    paint = T.armor_paint(main, dark, light, glow, rng)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    T.fill_box(px, 0, 0, 8, 8, 8, paint)
    for x in range(9, 15):
        px[x, 11] = glow
    T.fill_box(px, 16, 16, 8, 12, 4, paint)
    px[19, 22] = px[20, 22] = px[19, 23] = px[20, 23] = hexc(GLOW[4])
    T.fill_box(px, 40, 16, 4, 12, 4, paint)
    T.fill_box(px, 0, 16, 4, 12, 4, lambda f, x, y, w, h: paint(f, x, y, w, h) if y >= h - 5 or f in ("top", "bottom") else None)
    save(img, "entity/equipment/humanoid/moonsilver.png")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    T.fill_box(px, 16, 16, 8, 12, 4, lambda f, x, y, w, h: paint(f, x, y, w, h) if y >= h - 5 else None)
    T.fill_box(px, 0, 16, 4, 12, 4, lambda f, x, y, w, h: paint(f, x, y, w, h) if y < h - 3 else None)
    save(img, "entity/equipment/humanoid_leggings/moonsilver.png")

    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()

    def crown(face, x, y, w, h):
        if face in ("north", "south", "east", "west"):
            if y < 3:
                return hexc(GLOW[3]) if x in (0, 7) or (face == "north" and x in (3, 4)) else None
            if y < 6:
                if face == "north" and x in (3, 4) and y == 4:
                    return hexc("#ffffff")
                return ramp([MOON[7], MOON[5]], (y - 3) / 3) if (x + y) % 3 else hexc(GLOW[2])
        return None
    T.fill_box(px, 32, 0, 8, 8, 8, crown)
    save(img, "entity/equipment/humanoid/tides.png")


def main():
    vanilla = os.environ.get("VANILLA_ITEMS", "")
    if "--vanilla" in sys.argv:
        vanilla = sys.argv[sys.argv.index("--vanilla") + 1]
    if not os.path.isdir(vanilla):
        sys.exit("Pass the vanilla item texture folder with --vanilla")
    moonsilver_tools(vanilla)
    crescent_glaive()
    tidecaller_glaive()
    orrery_staff()
    phase_daggers()
    moonshot_crossbow(vanilla)
    stasis_bell()
    tether_hook()
    lunar_key()
    tidal_sigil()
    moon_heart()
    materials()
    armor_icons()
    spawn_eggs()
    blocks()
    effects()
    armor_layers()
    print("moon textures written")


if __name__ == "__main__":
    main()
