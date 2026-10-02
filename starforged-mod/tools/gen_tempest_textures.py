#!/usr/bin/env python3
"""
Tempestforged texture generator: items, weapons, blocks, effects and armor layers for the Stormreach expansion.
Reuses the helpers of gen_textures.py / gen_weapons.py. Needs the vanilla item textures for the Aetherium tool
silhouettes: --vanilla <.../assets/minecraft/textures/item> (the sibling block/ folder supplies the lantern layout).
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

SLATE = ["#10121c", "#1e2232", "#2e3448", "#444c66", "#5e6884", "#7c88a6", "#a2acc6", "#ccd4e6"]
OUT = "#0c0e18"
VOLT = ["#1c3c8c", "#2f62d0", "#4f8cff", "#7ab4ff", "#b4dcff", "#eef8ff"]
AETH = ["#123a48", "#1e5a6c", "#2e8296", "#4cb0c0", "#7ad8e0", "#c0f4f6", "#ffffff"]
GOLD = ["#6a4a14", "#a87a24", "#e0b040", "#ffe08a", "#fff6d0"]
GALE = ["#3a7a74", "#5cb0a4", "#8ad8cc", "#c8f4ec", "#ffffff"]


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


def bolt(px, points, color, glow=None):
    """A jagged lightning line through the given points."""
    for a, b in zip(points, points[1:]):
        W.line(px, a, b, color)
    if glow:
        for (x, y) in points:
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = hexc(glow)


# ----------------------------------------------------------------------------------------------------------------
# Aetherium tools: vanilla netherite silhouettes, recoloured sea-glass teal with storm-slate grips.
# ----------------------------------------------------------------------------------------------------------------

def aetherium_tools(vanilla):
    tool_ramp = ["#0e2a34", "#184654", "#246c7c", "#3a96a6", "#5cbcc8", "#8ee0e6", "#c8f6f8", "#ffffff"]
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
                    px[x, y] = lerp(hexc("#141826"), hexc("#3c4460"), W.lum(c) / 140.0)
        # A crackle of charge along the edge.
        lit = [(x, y) for y in range(16) for x in range(16) if px[x, y][3] and px[x, y] == hexc(tool_ramp[-2])]
        for (x, y) in lit[:2]:
            px[x, y] = hexc(VOLT[4])
        item(im, f"aetherium_{tool}")


# ----------------------------------------------------------------------------------------------------------------
# Legendary weapons
# ----------------------------------------------------------------------------------------------------------------

def skybreaker_halberd():
    img, px = new()
    W.line(px, (0, 15), (11, 4), SLATE[3])
    W.line(px, (1, 15), (12, 4), SLATE[1])
    for (x, y) in ((2, 13), (5, 10), (8, 7)):
        px[x, y] = hexc(GOLD[2])
    # Axe blade (one side) and back spike.
    for y in range(1, 9):
        for x in range(8, 16):
            u, v = x - 11.5, y - 4.5
            if -0.5 < u + v < 4.5 and abs(u - v) < 3.6 and u > -1.5:
                px[x, y] = ramp(AETH[2:], min(1.0, (u - v + 4) / 8))
    for (x, y) in ((14, 1), (15, 0), (13, 2)):
        px[x, y] = hexc(AETH[5])
    for (x, y) in ((9, 3), (8, 2)):
        px[x, y] = hexc(SLATE[5])
    bolt(px, [(10, 7), (12, 5), (11, 4), (13, 2)], VOLT[4], VOLT[5])
    item(outline(img), "skybreaker_halberd")


def tempest_javelin():
    img, px = new()
    W.line(px, (1, 14), (11, 4), SLATE[4])
    W.line(px, (2, 14), (12, 4), SLATE[2])
    for (x, y) in ((3, 12), (5, 10), (7, 8)):
        px[x, y] = hexc(VOLT[3])
    # Leaf-shaped head.
    for (x, y, c) in ((12, 3, AETH[4]), (13, 2, AETH[5]), (14, 1, AETH[6]), (12, 2, AETH[3]), (13, 3, AETH[3]), (11, 3, AETH[2]),
                      (13, 1, AETH[4]), (14, 2, AETH[4]), (12, 4, AETH[2])):
        px[x, y] = hexc(c)
    # Fletching of storm feathers.
    for (x, y) in ((0, 13), (1, 15), (0, 14), (2, 15)):
        px[x, y] = hexc(VOLT[2])
    item(outline(img), "tempest_javelin")


def gale_blades():
    img, px = new()

    def blade(x0, y0, flip):
        for i in range(9):
            x = x0 + (i if not flip else -i)
            y = y0 - i
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = ramp(GALE[1:], i / 9)
                xx = x + (1 if not flip else -1)
                if 0 <= xx < 16:
                    px[xx, y] = ramp(GALE[:3], i / 9)
        hx, hy = x0 - (2 if not flip else -2), y0 + 2
        W.line(px, (x0, y0), (hx, hy), SLATE[3])
    blade(4, 12, False)
    blade(11, 12, True)
    for (x, y) in ((8, 3), (7, 4), (9, 5), (6, 2)):
        px[x, y] = hexc(GALE[4])
    item(outline(img), "gale_blades")


def stormhook():
    img, px = new()
    W.line(px, (1, 14), (8, 7), SLATE[3])
    W.line(px, (2, 14), (9, 7), SLATE[1])
    px[2, 12] = px[4, 10] = hexc(GOLD[2])
    # Hook head: a curved prong.
    for a in range(-40, 200, 12):
        r = 3.3
        x = round(11 + math.cos(math.radians(a)) * r)
        y = round(5 - math.sin(math.radians(a)) * r)
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = ramp(AETH[3:], (a + 40) / 240)
    px[14, 7] = hexc(AETH[6])
    # Coiled wire.
    for i, (x, y) in enumerate(((6, 13), (7, 14), (8, 13), (9, 14), (10, 13))):
        px[x, y] = hexc(VOLT[3] if i % 2 else VOLT[4])
    item(outline(img), "stormhook")


def arc_cannon():
    img, px = new()
    # Barrel body.
    for y in range(5, 11):
        for x in range(2, 13):
            px[x, y] = ramp(SLATE[2:], 0.2 + (y - 5) / 8 + ((x + y) % 3 == 0) * 0.1)
    for x in range(2, 13):
        px[x, 5] = hexc(SLATE[6])
    # Muzzle coils.
    for x in (10, 12):
        for y in range(4, 12):
            px[x, y] = hexc(GOLD[1] if y in (4, 11) else GOLD[2])
    for y in range(6, 10):
        px[14, y] = hexc(VOLT[4])
        px[13, y] = hexc(VOLT[2])
    px[15, 7] = px[15, 8] = hexc(VOLT[5])
    # Grip and stock.
    for y in range(10, 15):
        px[4, y] = hexc(SLATE[1])
        px[5, y] = hexc(SLATE[3])
    for x in range(0, 3):
        for y in range(6, 10):
            px[x, y] = hexc(SLATE[3] if y > 6 else SLATE[5])
    # Charge cell.
    for x in range(5, 9):
        px[x, 7] = hexc(VOLT[3])
        px[x, 8] = hexc(VOLT[2])
    px[6, 7] = hexc(VOLT[5])
    item(outline(img), "arc_cannon")


def skycleaver():
    img, px = new()
    W.line(px, (1, 14), (5, 10), SLATE[3])
    W.line(px, (2, 15), (6, 11), SLATE[1])
    # Guard: a pair of swept wings.
    for (x, y) in ((3, 9), (4, 9), (5, 9), (7, 12), (7, 13), (7, 11), (2, 8), (8, 14)):
        px[x, y] = hexc(GOLD[2])
    # Broad blade of storm-glass with a bolt down its fuller.
    for i in range(10):
        cx, cy = 6 + i, 9 - i
        for w in range(-1, 2):
            x, y = cx + (w if w > 0 else 0), cy + (-w if w < 0 else 0)
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = ramp(VOLT[1:] if w else AETH[3:], i / 10)
        if 0 <= cx + 1 < 16 and 0 <= cy + 1 < 16:
            px[cx + 1, cy + 1] = ramp([VOLT[1], VOLT[2]], i / 10)
    bolt(px, [(7, 8), (9, 7), (10, 5), (12, 4), (13, 2)], VOLT[5])
    px[15, 0] = hexc("#ffffff")
    item(outline(img), "skycleaver")


def skybreaker_core():
    img, px = new()
    disc(px, 8, 8, 6.4, [VOLT[5], VOLT[3], VOLT[1], SLATE[2]])
    for a in range(0, 360, 60):
        x = round(8 + math.cos(math.radians(a)) * 6.5)
        y = round(8 + math.sin(math.radians(a)) * 6.5)
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = hexc(GOLD[2])
    bolt(px, [(6, 3), (9, 7), (7, 8), (10, 13)], "#ffffff")
    item(outline(img), "skybreaker_core")


def tempest_sigil():
    img, px = new()
    disc(px, 8, 8, 7, [GOLD[1], GOLD[2], GOLD[3]], shade=False)
    disc(px, 8, 8, 5.4, [SLATE[1], SLATE[2], SLATE[3]], shade=False)
    bolt(px, [(9, 3), (6, 8), (9, 8), (6, 13)], VOLT[4], None)
    px[9, 3] = px[6, 13] = hexc("#ffffff")
    for a in range(30, 360, 60):
        x = round(8 + math.cos(math.radians(a)) * 6.4)
        y = round(8 + math.sin(math.radians(a)) * 6.4)
        px[x, y] = hexc(GOLD[4])
    item(outline(img, "#2a1e08"), "tempest_sigil")


def stormheart():
    img, px = new()
    heart = [
        "................",
        "................",
        "...aa.....aa....",
        "..abba...abba...",
        ".abccba.abccba..",
        ".abcddcbbcdcba..",
        ".abcdeedddcba...",
        "..abcdeeedcba...",
        "...abcdeedcba...",
        "....abcddcba....",
        ".....abccba.....",
        "......abba......",
        ".......aa.......",
        "................",
        "................",
        "................",
    ]
    pal = {"a": VOLT[0], "b": VOLT[1], "c": VOLT[2], "d": VOLT[3], "e": VOLT[5]}
    for y, row in enumerate(heart):
        for x, ch in enumerate(row):
            if ch in pal:
                px[x, y] = hexc(pal[ch])
    bolt(px, [(8, 3), (7, 6), (9, 7), (7, 10)], "#ffffff")
    item(outline(img), "stormheart")


def materials():
    rng = random.Random(404)

    # Raw aetherium: a lumpy teal nugget.
    img, px = new()
    for y in range(4, 13):
        for x in range(3, 13):
            d = math.hypot((x - 7.5) / 4.6, (y - 8.5) / 4.0) + rng.random() * 0.15
            if d < 1.0:
                px[x, y] = ramp(AETH[1:6], 1 - d + (x - y) * 0.02)
    px[6, 6] = px[9, 8] = hexc(AETH[6])
    item(outline(img), "raw_aetherium")

    # Ingots.
    for name, colors, spark in (("aetherium_ingot", AETH[1:6], None), ("charged_aetherium_ingot", [VOLT[1], AETH[3], VOLT[3], AETH[5], VOLT[5]], VOLT[5])):
        img, px = new()
        for y in range(6, 12):
            for x in range(2, 14):
                if x - 2 >= (11 - y) * 0.5 and 13 - x >= (y - 6) * 0.3:
                    px[x, y] = ramp(colors, 0.9 - (y - 6) / 7 + (x % 4 == 0) * 0.08)
        for x in range(4, 12):
            px[x, 6] = hexc(colors[-1])
        if spark:
            bolt(px, [(3, 4), (6, 5), (8, 3), (11, 5), (13, 3)], spark)
        item(outline(img), name)

    # Thunder shard: a forked crystal.
    img, px = new()
    for i in range(10):
        for w in (-1, 0, 1):
            x, y = 5 + i // 2 + w, 13 - i
            if 0 <= x < 16:
                px[x, y] = ramp(VOLT[2:], i / 10 + abs(w) * 0.1)
    for i in range(5):
        px[10 + i // 2, 8 - i] = hexc(VOLT[4])
    px[10, 3] = px[12, 4] = hexc("#ffffff")
    item(outline(img), "thunder_shard")

    # Static mote: a crackling spark.
    img, px = new()
    disc(px, 8, 8, 3.2, [VOLT[5], VOLT[3], VOLT[2]], shade=False)
    for (dx, dy) in ((0, -5), (5, 0), (0, 5), (-5, 0), (3, -3), (-3, 3)):
        W.line(px, (8, 8), (8 + dx, 8 + dy), VOLT[3])
    px[8, 8] = hexc("#ffffff")
    item(img, "static_mote")

    # Charged aether dust.
    img, px = new()
    for y in range(9, 15):
        half = (y - 8) * 0.9
        for x in range(round(8 - half), round(8 + half)):
            if 0 <= x < 16:
                px[x, y] = ramp([AETH[5], AETH[3], VOLT[2]], rng.random() * 0.9)
    for (x, y) in ((5, 6), (10, 5), (8, 3), (7, 10), (9, 12)):
        px[x, y] = hexc(VOLT[5])
    item(outline(img), "charged_aether_dust")

    # Shardwing crystal: a long blue blade-crystal.
    img, px = new()
    for i in range(12):
        for w in (0, 1):
            x, y = 2 + i + w, 13 - i
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = ramp([VOLT[1], VOLT[3], VOLT[4], VOLT[5]], (i / 12) * 0.8 + w * 0.2)
    px[14, 1] = hexc("#ffffff")
    item(outline(img), "shardwing_crystal")

    # Storm feather.
    img, px = new()
    W.line(px, (3, 13), (12, 2), SLATE[6])
    for i in range(1, 9):
        x, y = 3 + i, 13 - round(i * 1.2)
        for k in range(1, 3):
            for (ox, oy) in ((-k, -k + 1), (k, k - 1)):
                xx, yy = x + ox, y + oy
                if 0 <= xx < 16 and 0 <= yy < 16:
                    px[xx, yy] = ramp([SLATE[3], SLATE[5], VOLT[3]], i / 9 + k * 0.1)
    px[12, 2] = hexc(VOLT[4])
    item(outline(img), "storm_feather")

    # Stormbound plate: a dented pauldron with a glowing rune.
    img, px = new()
    for y in range(3, 13):
        for x in range(2, 14):
            d = math.hypot((x - 7.5) / 6, (y - 9) / 6)
            if d < 1.0 and y >= 3:
                px[x, y] = ramp(SLATE[3:], 1 - d + (rng.random() * 0.1))
    for (x, y) in ((7, 6), (8, 7), (7, 8), (8, 9)):
        px[x, y] = hexc(VOLT[4])
    item(outline(img), "stormbound_plate")

    # Charged scrap: bent metal bits with sparks.
    img, px = new()
    for (x0, y0, x1, y1, c) in ((3, 11, 8, 8, SLATE[5]), (7, 12, 12, 10, SLATE[4]), (5, 5, 9, 7, SLATE[6]), (10, 4, 12, 7, SLATE[3])):
        W.line(px, (x0, y0), (x1, y1), c)
        W.line(px, (x0, y0 + 1), (x1, y1 + 1), SLATE[2])
    for (x, y) in ((4, 8), (11, 8), (8, 4)):
        px[x, y] = hexc(VOLT[5])
    item(outline(img), "charged_scrap")

    # Horns.
    for name, colors, tip in (("thunderjaw_horn", ["#8a8676", "#c0bcaa", "#f4f0e2"], VOLT[4]),
                              ("alpha_conductor_horn", [GOLD[0], GOLD[2], GOLD[4]], VOLT[5])):
        img, px = new()
        for i in range(12):
            r = max(0.5, 2.6 - i * 0.2)
            cx, cy = 3 + i * 0.9, 13 - i * 0.6 - (i * i) * 0.04
            for y in range(16):
                for x in range(16):
                    if math.hypot(x + 0.5 - cx, y + 0.5 - cy) < r:
                        px[x, y] = ramp(colors, i / 12)
        px[13, 2] = px[13, 3] = hexc(tip)
        if name == "alpha_conductor_horn":
            bolt(px, [(4, 12), (6, 10), (8, 10), (10, 7)], VOLT[4])
        item(outline(img), name)

    # Stormhide: a folded hide with blue veins.
    img, px = new()
    for y in range(3, 14):
        for x in range(2, 14):
            if (x - 2) + (13 - y) > 1 and (13 - x) + (y - 3) > 1:
                px[x, y] = ramp(["#2a2a36", "#3e3e4e", "#585a6c"], rng.random() * 0.6 + (y - 3) / 30)
    for (x, y) in ((4, 6), (5, 7), (6, 7), (7, 8), (8, 8), (9, 9), (10, 10)):
        px[x, y] = hexc(VOLT[3])
    item(outline(img), "stormhide")

    # Breeze shard: a swirl of wind.
    img, px = new()
    for a in range(0, 540, 15):
        r = 1.0 + a / 540 * 5.5
        x = round(8 + math.cos(math.radians(a)) * r)
        y = round(8 + math.sin(math.radians(a)) * r)
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = ramp(GALE[::-1], a / 540)
    px[8, 8] = hexc("#ffffff")
    item(img, "breeze_shard")


def armor_icons():
    pal = {"#": OUT, "a": AETH[6], "b": AETH[4], "c": AETH[3], "d": AETH[2], "h": VOLT[3], "H": VOLT[2], "g": VOLT[4],
           "y": AETH[5], "Y": AETH[3], "w": "#ffffff"}
    for name, rows in (("helmet", T.HELMET), ("chestplate", T.CHESTPLATE), ("leggings", T.LEGGINGS), ("boots", T.BOOTS)):
        item(T.sprite(rows, pal), f"aetherium_{name}")
    crown = T.sprite([
        "................",
        "................",
        ".......#........",
        "..#...#g#...#...",
        ".#g#.#ggg#.#g#..",
        ".#gg##gwg##gg#..",
        "..#yyyyyyyyyy#..",
        "..#yYYyhhyYYy#..",
        "..#yyyyhhyyyy#..",
        "..#HHHHHHHHHH#..",
        "...##########...",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], {"#": OUT, "y": GOLD[3], "Y": GOLD[2], "w": "#ffffff", "g": VOLT[4], "h": VOLT[3], "H": GOLD[1]})
    item(crown, "tempest_crown")


def spawn_eggs():
    eggs = {
        "static_wisp": ("#cfe8ff", "#3a8cff"),
        "shardwing": ("#3c4660", "#8fd8ff"),
        "stormbound": ("#4e5870", "#9cd8ff"),
        "thunderjaw": ("#3a3a48", "#5ab0ff"),
        "zephyr_sprite": ("#d8f0f4", "#7ad0c4"),
        "storm_roc": ("#4a5468", "#e0e6f0"),
        "thunderjaw_alpha": ("#24242e", "#ffe066"),
        "veyr": ("#3a4258", "#e0c070"),
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
        item(T.outline(img, (12, 14, 24, 255)), f"{name}_spawn_egg")


# ----------------------------------------------------------------------------------------------------------------
# Blocks
# ----------------------------------------------------------------------------------------------------------------

def block(img, name):
    save(img, f"block/{name}.png")


def animated(frames, name, frametime, interpolate=True):
    sheet = Image.new("RGBA", (16, 16 * len(frames)))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, i * 16))
    block(sheet, name)
    with open(os.path.join(T.ASSETS, f"block/{name}.png.mcmeta"), "w") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": %s}}\n' % (frametime, "true" if interpolate else "false"))


def stone(seed, light, dark, speck, streak=None):
    rng = random.Random(seed)
    img = T.noise_tex(seed, light, dark, 1.0)
    px = img.load()
    for _ in range(14):
        px[rng.randint(0, 15), rng.randint(0, 15)] = hexc(speck)
    if streak:
        y = rng.randint(3, 12)
        for x in range(16):
            y = max(0, min(15, y + rng.choice((-1, 0, 0, 1))))
            px[x, y] = lerp(px[x, y], hexc(streak), 0.55)
    return img


def bricks(seed, light="#7a86a2", dark="#5a6480", mortar="#2e3448", top="#9aa6c0"):
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
                    c = lerp(c, hexc(top), 0.35)
            px[x, y] = c
    return img


def machine(seed, accent=None):
    """A riveted storm-slate machine casing."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            c = ramp(SLATE[2:6], 0.45 + rng.random() * 0.15 - (x + y) / 80)
            if x in (0, 15) or y in (0, 15):
                c = hexc(SLATE[1])
            elif x in (1, 14) or y in (1, 14):
                c = hexc(SLATE[5])
            px[x, y] = c
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = hexc(SLATE[7])
    if accent:
        for x in range(4, 12):
            px[x, 3] = hexc(accent)
    return img


def framed(img, color):
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = hexc(color)
    return img


def blocks(vanilla_blocks):
    rng = random.Random(77)
    block(stone(1, "#5a6280", "#3c4460", "#2a3046", "#7c88aa"), "stormstone")
    block(stone(2, "#9aa4b8", "#7a8498", "#5c6478"), "skyrock")
    block(T.noise_tex(3, "#5a5470", "#3e3a52", 1.0), "skysoil")

    top = T.noise_tex(4, "#5c8a9c", "#3e6878", 1.0)
    px = top.load()
    for _ in range(18):
        px[rng.randint(0, 15), rng.randint(0, 15)] = hexc("#8ac0cc")
    block(top, "stormgrass_top")
    side = T.noise_tex(3, "#5a5470", "#3e3a52", 1.0)
    px = side.load()
    tp = top.load()
    for x in range(16):
        depth = 3 + rng.randint(0, 2)
        for y in range(depth):
            px[x, y] = tp[x, y]
    block(side, "stormgrass_side")

    # Stormwood: grey-violet bark with pale lightning scars, rings on top.
    bark = Image.new("RGBA", (16, 16))
    px = bark.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = ramp(["#2a2838", "#3e3a52", "#585270"], ((x * 7) % 5) / 5 * 0.6 + rng.random() * 0.25)
    y = 0
    x = 6
    for y in range(16):
        x = max(1, min(14, x + rng.choice((-1, 0, 1))))
        px[x, y] = hexc("#b8d8f0")
    block(bark, "stormwood_log")
    rings = Image.new("RGBA", (16, 16))
    px = rings.load()
    for yy in range(16):
        for xx in range(16):
            d = math.hypot(xx - 7.5, yy - 7.5)
            px[xx, yy] = hexc("#3e3a52") if d > 6.8 else ramp(["#8a84a6", "#6a6488"], (int(d) % 2) * 0.6)
    block(rings, "stormwood_log_top")
    planks = Image.new("RGBA", (16, 16))
    px = planks.load()
    for yy in range(16):
        for xx in range(16):
            c = ramp(["#5a5474", "#6c6688", "#7e78a0"], rng.random() * 0.5 + (0.3 if (yy // 4) % 2 else 0))
            if yy % 4 == 3 or (xx + (yy // 4) * 5) % 16 == 0:
                c = hexc("#36324a")
            px[xx, yy] = c
    block(planks, "stormwood_planks")
    leaves = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = leaves.load()
    for yy in range(16):
        for xx in range(16):
            if rng.random() < 0.8:
                px[xx, yy] = ramp(["#2c5a6a", "#3e7a8c", "#64a6b6", "#a8e0ea"], rng.random() * 0.8)
    block(leaves, "stormleaves")

    seed = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = seed.load()
    for yy in range(9, 16):
        px[8, yy] = hexc("#4a7a70")
    for a in range(0, 360, 30):
        for r in (1, 2, 3):
            xx = round(8 + math.cos(math.radians(a + r * 25)) * r)
            yy = round(6 + math.sin(math.radians(a + r * 25)) * r)
            px[xx, yy] = ramp(GALE[::-1], r / 4)
    px[8, 6] = hexc("#ffffff")
    block(seed, "gale_seed")

    ore = stone(1, "#5a6280", "#3c4460", "#2a3046", "#7c88aa")
    px = ore.load()
    for cluster in ((4, 4), (11, 5), (5, 11), (12, 12)):
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1), (2, 1), (1, 2)):
            xx = min(15, max(0, cluster[0] + dx))
            yy = min(15, max(0, cluster[1] + dy))
            px[xx, yy] = ramp([AETH[6], AETH[4], AETH[2]], (dx + dy + 1) / 4)
        px[cluster] = hexc(VOLT[5])
    block(ore, "aetherium_ore")

    ab = Image.new("RGBA", (16, 16))
    px = ab.load()
    for yy in range(16):
        for xx in range(16):
            c = ramp(AETH[1:6], 0.9 - (xx + yy) / 40 - rng.random() * 0.1)
            if xx in (0, 15) or yy in (0, 15):
                c = hexc(AETH[1])
            px[xx, yy] = c
    block(ab, "aetherium_block")
    frames = []
    for f in range(6):
        img = ab.copy()
        px = img.load()
        r2 = random.Random(f * 13 + 1)
        xx, yy = r2.randint(2, 6), 0
        while yy < 16:
            px[xx, yy] = hexc(VOLT[5])
            if 0 <= xx + 1 < 16:
                px[xx + 1, yy] = hexc(VOLT[3])
            xx = max(1, min(14, xx + r2.choice((-1, 1, 1))))
            yy += 1
        frames.append(img)
    animated(frames, "charged_aetherium_block", 2, interpolate=False)

    cl = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = cl.load()
    for (bx, h, w) in ((3, 9, 2), (7, 14, 3), (11, 10, 2), (13, 6, 1)):
        for yy in range(16 - h, 16):
            for xx in range(bx, bx + w):
                t = (yy - (16 - h)) / h
                px[xx, yy] = ramp([VOLT[5], VOLT[3], VOLT[1]], t * 0.8 + (xx - bx) * 0.15)
        px[bx, 16 - h] = hexc("#ffffff")
    block(cl, "thunder_crystal_cluster")

    block(bricks(10), "tempest_bricks")
    ch = bricks(12)
    px = ch.load()
    bolt(px, [(9, 2), (6, 7), (9, 8), (6, 13)], VOLT[3])
    px[9, 2] = px[6, 13] = hexc(VOLT[5])
    for xx in range(16):
        px[xx, 0] = px[xx, 15] = hexc(GOLD[1])
    block(ch, "chiseled_tempest_bricks")

    glass = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = glass.load()
    for yy in range(16):
        for xx in range(16):
            if xx in (0, 15) or yy in (0, 15):
                px[xx, yy] = hexc(SLATE[5])
            elif (xx + yy) % 7 == 0 and xx > 2 and yy > 2:
                px[xx, yy] = hexc("#e0f4ff", 140)
            else:
                px[xx, yy] = hexc("#7ab4e0", 70)
    block(glass, "aetherglass")

    # Storm lantern: the vanilla lantern layout, recoloured slate with a blue arc inside (block and item sprites).
    def storm_lantern(src):
        lan = Image.open(src).convert("RGBA")
        px = lan.load()
        for yy in range(lan.size[1]):
            for xx in range(lan.size[0]):
                c = px[xx, yy]
                if not c[3]:
                    continue
                r, g, b = c[:3]
                if r > 200 and g > 150:  # flame / glow
                    px[xx, yy] = lerp(hexc(VOLT[2]), hexc(VOLT[5]), (r + g) / 510)
                else:
                    px[xx, yy] = lerp(hexc(SLATE[1]), hexc(SLATE[6]), W.lum(c) / 200.0)
        return lan.crop((0, 0, 16, 16))
    block(storm_lantern(os.path.join(vanilla_blocks, "lantern.png")), "storm_lantern")
    item(storm_lantern(os.path.join(vanilla_blocks, "../item/lantern.png")), "storm_lantern")

    chime = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = chime.load()
    for xx in range(4, 12):
        px[xx, 1] = hexc(SLATE[5])
    for i, xx in enumerate((5, 7, 9, 11)):
        for yy in range(2, 2 + 4 + (i % 2) * 3 + i):
            px[xx - 1 if xx > 10 else xx, yy] = hexc(AETH[4] if yy > 3 else SLATE[6])
        px[xx - 1 if xx > 10 else xx, 2 + 4 + (i % 2) * 3 + i] = hexc(AETH[6])
    for yy in range(0, 2):
        px[8, yy] = hexc(SLATE[4])
    block(chime, "wind_chime")

    # Machines.
    side = machine(20)
    block(side, "storm_machine_side")
    block(machine(21, GOLD[2]), "storm_machine_top")
    for on in (False, True):
        img = machine(22)
        px = img.load()
        disc(px, 8, 8, 5.2, [VOLT[5] if on else VOLT[3], VOLT[2], VOLT[0]] if on else [SLATE[6], SLATE[4], SLATE[2]], shade=False)
        for a in range(0, 360, 45):
            xx = round(8 + math.cos(math.radians(a)) * 4)
            yy = round(8 + math.sin(math.radians(a)) * 4)
            px[xx, yy] = hexc(GOLD[2])
        if on:
            bolt(px, [(9, 4), (7, 8), (9, 8), (7, 12)], "#ffffff")
        block(img, "storm_dynamo_front" + ("_on" if on else ""))

    # Conductors: an aetherium rod end with an arrow showing which way the pulse leaves.
    for name, base in (("aetherium_conductor", AETH), ("rotating_conductor", VOLT)):
        for on in (False, True):
            img = machine(30 if name[0] == "a" else 31)
            px = img.load()
            disc(px, 8, 8, 4.0, [base[5] if on else base[4], base[3], base[1]], shade=False)
            px[8, 8] = px[7, 7] = px[8, 7] = px[7, 8] = hexc("#ffffff" if on else base[5])
            block(img, f"{name}_front" + ("_on" if on else ""))
        img = machine(32)
        px = img.load()
        for yy in range(3, 13):
            px[7, yy] = px[8, yy] = hexc(base[3])
        for d in range(3):
            px[7 - d, 3 + d] = px[8 + d, 3 + d] = hexc(base[4])
        if name == "rotating_conductor":
            for a in range(30, 330, 20):
                xx = round(8 + math.cos(math.radians(a)) * 6)
                yy = round(8 + math.sin(math.radians(a)) * 6)
                px[xx, yy] = hexc(GOLD[2])
        block(img, f"{name}_side")

    img = machine(40)
    px = img.load()
    for xx in range(2, 14):
        px[xx, 8] = hexc(VOLT[3])
    for yy in range(4, 12):
        px[8, yy] = hexc(VOLT[3])
    px[2, 8] = px[13, 8] = hexc(VOLT[5])
    block(img, "splitter_relay")
    for on in (False, True):
        img = machine(41)
        px = img.load()
        disc(px, 8, 8, 4.5, [VOLT[5], VOLT[3], VOLT[1]] if on else [SLATE[6], SLATE[4], SLATE[3]], shade=False)
        for xx in range(1, 15):
            px[xx, 8] = hexc(VOLT[4] if on else SLATE[6])
        block(img, "storm_relay" + ("_on" if on else ""))
    img = machine(42)
    px = img.load()
    for yy in range(3, 13):
        for xx in range(3, 13):
            if (xx + yy) % 4 < 2:
                px[xx, yy] = hexc("#d84040")
            else:
                px[xx, yy] = hexc("#3a1a1a")
    disc(px, 8, 8, 2.4, ["#ffffff", "#ff8a6a", "#c02020"], shade=False)
    block(img, "overload_relay")
    for lvl in range(4):
        img = machine(43)
        px = img.load()
        for yy in range(3, 13):
            for xx in range(5, 11):
                filled = (12 - yy) < (lvl + 1) * 2.5 and lvl > 0
                px[xx, yy] = hexc(VOLT[3] if filled else SLATE[1])
                if xx in (5, 10):
                    px[xx, yy] = hexc(SLATE[6])
        if lvl == 3:
            px[7, 4] = px[8, 5] = hexc("#ffffff")
        block(img, f"storm_capacitor_{lvl}")
    for lit in (False, True):
        img = bricks(50, light="#6e7a96", dark="#4e5874")
        px = img.load()
        disc(px, 8, 8, 5.5, [VOLT[5], VOLT[3], VOLT[1], GOLD[1]] if lit else [SLATE[4], SLATE[2], SLATE[1], GOLD[0]], shade=False)
        if lit:
            bolt(px, [(9, 4), (7, 8), (9, 8), (7, 12)], "#ffffff")
        framed(img, GOLD[2] if lit else GOLD[0])
        block(img, "citadel_core" + ("_lit" if lit else ""))

    img = machine(60)
    px = img.load()
    disc(px, 8, 8, 5.0, [VOLT[5], VOLT[3], SLATE[2]], shade=False)
    for a in range(0, 360, 90):
        W.line(px, (8, 8), (round(8 + math.cos(math.radians(a)) * 7), round(8 + math.sin(math.radians(a)) * 7)), GOLD[2])
    block(img, "lightning_beacon_top")
    img = machine(61)
    px = img.load()
    bolt(px, [(9, 2), (6, 7), (9, 8), (6, 13)], VOLT[4])
    block(img, "lightning_beacon_side")

    img = machine(62)
    px = img.load()
    for a in range(0, 360, 6):
        xx = round(8 + math.cos(math.radians(a)) * 5)
        yy = round(8 + math.sin(math.radians(a)) * 5)
        px[xx, yy] = hexc(GOLD[2])
    for (xx, yy) in ((8, 5), (8, 6), (9, 7), (8, 8)):
        px[xx, yy] = hexc(VOLT[4])
    block(img, "weather_engine_top")
    img = machine(63)
    px = img.load()
    for xx in range(3, 13):
        px[xx, 6] = hexc("#e8f0ff")
        px[xx, 9] = hexc(VOLT[3])
    for (xx, yy) in ((4, 5), (6, 4), (9, 5), (11, 4)):
        px[xx, yy] = hexc("#e8f0ff")
    block(img, "weather_engine_side")

    # Wind vent grille (animated breeze) and its sides.
    frames = []
    for f in range(8):
        img = machine(70)
        px = img.load()
        for yy in range(2, 14):
            for xx in range(2, 14):
                if yy % 3 == 0:
                    px[xx, yy] = hexc(SLATE[1])
                else:
                    v = 0.5 + 0.5 * math.sin(xx * 0.8 + f * 0.785 + yy * 0.3)
                    px[xx, yy] = ramp([GALE[3], GALE[1], SLATE[3]], 1 - v * 0.7)
        frames.append(img)
    animated(frames, "wind_vent_top", 2)
    img = machine(71)
    px = img.load()
    for xx in range(3, 13):
        px[xx, 11] = hexc(GALE[2])
    block(img, "wind_vent_side")
    frames = []
    for f in range(8):
        img = machine(72)
        px = img.load()
        for yy in range(3, 13):
            for xx in range(2, 14):
                v = 0.5 + 0.5 * math.sin(xx * 0.7 - f * 0.785)
                px[xx, yy] = ramp([GALE[3], GALE[1], SLATE[2]], 1 - v * 0.8) if yy % 2 else hexc(SLATE[1])
        frames.append(img)
    animated(frames, "gale_vent_front", 2)
    for on in (False, True):
        img = machine(73)
        px = img.load()
        for yy in range(3, 13):
            for xx in range(3, 13):
                d = math.hypot(xx - 7.5, yy - 7.5)
                px[xx, yy] = ramp([VOLT[4], VOLT[2], SLATE[2]] if on else [GALE[2], SLATE[3], SLATE[1]], d / 6)
        block(img, "storm_lift_top" + ("_on" if on else ""))

    img = machine(80)
    px = img.load()
    for yy in range(2, 14):
        for xx in range(2, 14):
            px[xx, yy] = hexc(GOLD[1] if (xx + yy) % 2 else SLATE[3])
    bolt(px, [(4, 4), (8, 7), (7, 9), (11, 12)], VOLT[5])
    block(img, "shock_plate")

    img = machine(81)
    px = img.load()
    disc(px, 8, 8, 4.5, [AETH[6], AETH[4], AETH[2]], shade=False)
    for (xx, yy) in ((8, 2), (8, 13), (2, 8), (13, 8)):
        px[xx, yy] = hexc(GOLD[3])
    block(img, "sky_anchor_top")
    img = machine(82)
    px = img.load()
    for yy in range(3, 13):
        px[8, yy] = hexc(AETH[4])
    for xx in range(5, 12):
        px[xx, 11] = hexc(AETH[4])
    px[5, 10] = px[11, 10] = hexc(AETH[5])
    block(img, "sky_anchor_side")

    for name, mark in (("thunder_rune", VOLT), ("gale_rune", GALE)):
        for spent in (False, True):
            img = bricks(90 if name[0] == "t" else 91, light="#5e6884", dark="#444c66")
            px = img.load()
            col = mark[1] if spent else mark[-2]
            if name == "thunder_rune":
                bolt(px, [(9, 2), (6, 7), (9, 8), (6, 13)], col)
            else:
                for a in range(0, 450, 15):
                    r = 1.0 + a / 450 * 5.0
                    px[round(8 + math.cos(math.radians(a)) * r), round(8 + math.sin(math.radians(a)) * r)] = hexc(col)
            framed(img, mark[0] if spent else mark[2])
            block(img, name + ("_spent" if spent else ""))

    img = machine(95)
    px = img.load()
    for a in range(0, 540, 12):
        r = 1.0 + a / 540 * 5.5
        px[round(8 + math.cos(math.radians(a)) * r), round(8 + math.sin(math.radians(a)) * r)] = hexc(GALE[3])
    block(img, "cyclone_emitter_top")
    img = machine(96, GALE[2])
    block(img, "cyclone_emitter_side")

    frames = []
    for f in range(8):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for yy in range(16):
            for xx in range(16):
                v = 0.5 + 0.5 * math.sin((xx * 0.6 - yy * 0.4 + f * 0.8))
                c = ramp([VOLT[5], VOLT[3], VOLT[1]], v * 0.8)
                px[xx, yy] = c[:3] + (200,)
        r2 = random.Random(f)
        xx = r2.randint(3, 12)
        for yy in range(16):
            xx = max(0, min(15, xx + r2.choice((-1, 0, 1))))
            px[xx, yy] = hexc("#ffffff")
        frames.append(img)
    animated(frames, "tempest_seal", 2)

    for part, colors in (("side", ("#7a86a2", "#5a6480")), ("bottom", ("#5a6480", "#3c4460"))):
        img = T.noise_tex(100 if part == "side" else 101, *colors)
        px = img.load()
        if part == "side":
            for xx in range(16):
                px[xx, 2] = hexc(GOLD[2])
                px[xx, 12] = hexc(GOLD[2])
            bolt(px, [(9, 4), (7, 7), (9, 8), (7, 11)], VOLT[4])
        block(img, f"tempest_altar_{part}")
    topimg = T.noise_tex(102, "#7a86a2", "#5a6480")
    px = topimg.load()
    disc(px, 8, 8, 4.2, [VOLT[5], VOLT[3], VOLT[1]], shade=False)
    for a in range(0, 360, 30):
        xx = round(7.5 + math.cos(math.radians(a)) * 6.2)
        yy = round(7.5 + math.sin(math.radians(a)) * 6.2)
        px[xx, yy] = hexc(GOLD[3])
    block(topimg, "tempest_altar_top")

    frames = []
    for f in range(8):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for yy in range(16):
            for xx in range(16):
                v = 0.5 + 0.5 * math.sin(xx * 0.7 + f * 0.785) * math.cos(yy * 0.6 - f * 0.785)
                px[xx, yy] = ramp([VOLT[5], AETH[5], VOLT[3], SLATE[3]], v * 0.8)
        frames.append(img)
    animated(frames, "stormgate", 3)


# ----------------------------------------------------------------------------------------------------------------
# Effects & armor layers
# ----------------------------------------------------------------------------------------------------------------

def effects():
    # Cyclone ring: a soft swirl of streaks, transparent in the middle.
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = size / 2
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            r = math.hypot(dx, dy) / c
            if 0.35 < r < 1.0:
                a = math.atan2(dy, dx)
                streak = 0.5 + 0.5 * math.sin(a * 5 + r * 9)
                alpha = math.sin((r - 0.35) / 0.65 * math.pi) * (0.35 + 0.65 * streak)
                col = lerp(hexc("#9ab0c8"), hexc("#ffffff"), streak)
                px[x, y] = col[:3] + (int(255 * max(0.0, min(1.0, alpha))),)
    save(img, "entity/cyclone.png")

    # Eye of the Storm: a calm pale ring on the ground.
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            r = math.hypot(x + 0.5 - c, y + 0.5 - c) / c
            if r < 1.0:
                a = math.atan2(y - c, x - c)
                edge = max(0.0, 1.0 - abs(r - 0.92) / 0.08)
                ticks = 1.0 if (0.78 < r < 0.86 and int((a + math.pi) / (math.pi / 12)) % 2 == 0) else 0.0
                inner = 0.18 * (1 - r)
                alpha = max(edge, ticks * 0.8, inner)
                px[x, y] = lerp(hexc("#9cd8ff"), hexc("#ffffff"), edge)[:3] + (int(255 * min(1.0, alpha)),)
    save(img, "entity/storm_eye.png")


def armor_layers():
    rng = random.Random(41)
    main, dark, light, glow = hexc(AETH[3]), hexc(SLATE[2]), hexc(AETH[5]), hexc(VOLT[3])
    paint = T.armor_paint(main, dark, light, glow, rng)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    T.fill_box(px, 0, 0, 8, 8, 8, paint)
    for x in range(9, 15):
        px[x, 11] = hexc(VOLT[4])
    T.fill_box(px, 16, 16, 8, 12, 4, paint)
    px[19, 22] = px[20, 22] = px[19, 23] = px[20, 23] = hexc(VOLT[5])
    T.fill_box(px, 40, 16, 4, 12, 4, paint)
    T.fill_box(px, 0, 16, 4, 12, 4, lambda f, x, y, w, h: paint(f, x, y, w, h) if y >= h - 5 or f in ("top", "bottom") else None)
    save(img, "entity/equipment/humanoid/aetherium.png")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    T.fill_box(px, 16, 16, 8, 12, 4, lambda f, x, y, w, h: paint(f, x, y, w, h) if y >= h - 5 else None)
    T.fill_box(px, 0, 16, 4, 12, 4, lambda f, x, y, w, h: paint(f, x, y, w, h) if y < h - 3 else None)
    save(img, "entity/equipment/humanoid_leggings/aetherium.png")

    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()

    def crown(face, x, y, w, h):
        if face in ("north", "south", "east", "west"):
            if y < 3:
                if x in (0, 7):
                    return hexc(VOLT[4])
                if face == "north" and x in (3, 4):
                    return hexc(VOLT[5]) if y < 2 else hexc(GOLD[3])
                return None
            if y < 6:
                if face == "north" and x in (3, 4) and y == 4:
                    return hexc(VOLT[5])
                return ramp([GOLD[3], GOLD[1]], (y - 3) / 3) if (x + y) % 3 else hexc(GOLD[4])
        return None
    T.fill_box(px, 32, 0, 8, 8, 8, crown)
    save(img, "entity/equipment/humanoid/tempest.png")


def main():
    vanilla = os.environ.get("VANILLA_ITEMS", "")
    if "--vanilla" in sys.argv:
        vanilla = sys.argv[sys.argv.index("--vanilla") + 1]
    if not os.path.isdir(vanilla):
        sys.exit("Pass the vanilla item texture folder with --vanilla")
    vanilla_blocks = os.path.join(os.path.dirname(os.path.normpath(vanilla)), "block")
    aetherium_tools(vanilla)
    skybreaker_halberd()
    tempest_javelin()
    gale_blades()
    stormhook()
    arc_cannon()
    skycleaver()
    skybreaker_core()
    tempest_sigil()
    stormheart()
    materials()
    armor_icons()
    spawn_eggs()
    blocks(vanilla_blocks)
    effects()
    armor_layers()
    print("tempest textures written")


if __name__ == "__main__":
    main()
