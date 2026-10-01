#!/usr/bin/env python3
"""
Sunforged texture generator: items, weapons, blocks, effects and armor layers for the Sunlands expansion.
Reuses the drawing helpers of gen_textures.py / gen_weapons.py. Needs the vanilla item textures for the
Sunsteel tool silhouettes: --vanilla <.../assets/minecraft/textures/item>.
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

SUN = ["#2a1206", "#5e2a0a", "#8e420e", "#c2641a", "#e88a24", "#ffb444", "#ffd884", "#fff6d0"]
OUT = "#2a1206"
GOLD = ["#5e3f0e", "#a8761f", "#f0c45a", "#fff0b0"]
FIRE = ["#7a1608", "#c8300c", "#ff6a10", "#ffa830", "#ffe080", "#fffbe0"]
CRIMSON = ["#2a0a0a", "#5a1414", "#8a2020"]


def ramp(colors, t):
    return W.ramp(colors, t)


def new():
    return W.new()


def outline(img, color=OUT):
    return W.outline(img, color)


def item(img, name):
    save(img, f"item/{name}.png")


# ----------------------------------------------------------------------------------------------------------------
# Sunsteel tools: vanilla diamond silhouettes, recoloured gold-orange.
# ----------------------------------------------------------------------------------------------------------------

def sunsteel_tools(vanilla):
    for tool in ("sword", "pickaxe", "axe", "shovel", "hoe"):
        im = Image.open(os.path.join(vanilla, f"diamond_{tool}.png")).convert("RGBA")
        px = im.load()
        head = sorted({px[x, y] for y in range(16) for x in range(16) if px[x, y][3] and not W.is_wood(px[x, y])}, key=W.lum)
        tool_ramp = ["#3a1806", "#9a4a10", "#c8681c", "#e07e22", "#f09a30", "#ffbc50", "#ffd884", "#fff6d0"]
        mapping = {c: hexc(tool_ramp[round(i * (len(tool_ramp) - 1) / max(1, len(head) - 1))]) for i, c in enumerate(head)}
        for y in range(16):
            for x in range(16):
                c = px[x, y]
                if c[3] and c in mapping:
                    px[x, y] = mapping[c]
                elif c[3] and W.is_wood(c):
                    # Charcoal grip instead of wood.
                    px[x, y] = lerp(hexc("#1c1412"), hexc("#5a3a2a"), W.lum(c) / 140.0)
        item(im, f"sunsteel_{tool}")


# ----------------------------------------------------------------------------------------------------------------
# Legendary weapons
# ----------------------------------------------------------------------------------------------------------------

def solar_lance():
    img, px = new()
    # Long shaft, bottom-left to the spearhead.
    W.line(px, (0, 15), (9, 6), "#5a1414")
    W.line(px, (1, 15), (10, 6), "#2a0a0a")
    for (x, y) in ((2, 13), (5, 10)):
        px[x, y] = hexc(GOLD[2])
        px[x + 1, y] = hexc(GOLD[1])
    # Wing guard.
    for (x, y, c) in ((8, 9, GOLD[2]), (9, 10, GOLD[1]), (7, 8, GOLD[2]), (10, 11, GOLD[1]), (6, 7, GOLD[3])):
        px[x, y] = hexc(c)
    # Leaf-shaped spearhead along the diagonal.
    for y in range(16):
        for x in range(16):
            along = ((x - 9.5) - (y - 6.5)) / math.sqrt(2)  # towards top-right
            across = ((x - 9.5) + (y - 6.5)) / math.sqrt(2)
            if 0 <= along <= 7.2:
                width = 1.9 * math.sin(min(1.0, along / 7.2) * math.pi) ** 0.8 + 0.3
                if abs(across) <= width:
                    t = abs(across) / max(width, 0.01)
                    px[x, y] = ramp(FIRE[2:], 1.0 - t * 0.9) if along < 6 else hexc(FIRE[5])
    item(outline(img), "solar_lance")


def flare_greatsword():
    img, px = new()
    # Broad 3-wide blade with a molten core.
    for y in range(0, 11):
        for x in range(16):
            d = x + y - 15
            if 5 <= x <= 15 and -2 <= d <= 1 and not (y == 0 and x < 14):
                if d == -2:
                    c = SUN[7] if y < 5 else SUN[6]
                elif d == -1:
                    c = SUN[5]
                elif d == 0:
                    c = FIRE[3] if (x + y) % 4 else FIRE[4]
                else:
                    c = SUN[3]
                px[x, y] = hexc(c)
    px[15, 0] = hexc("#ffffff")
    # Sun-disc crossguard.
    for (x, y) in ((2, 8), (3, 9), (4, 10), (7, 13), (8, 14), (3, 8), (8, 13)):
        px[x, y] = hexc(GOLD[2])
    for (x, y) in ((4, 11), (5, 10), (5, 11), (6, 11), (6, 12), (5, 12), (4, 12)):
        px[x, y] = hexc(FIRE[3])
    px[5, 11] = hexc(FIRE[5])
    # Grip and pommel.
    for (x, y) in ((3, 12), (2, 13)):
        px[x, y] = hexc(CRIMSON[2])
    for (x, y) in ((4, 13), (3, 14)):
        px[x, y] = hexc(CRIMSON[1])
    px[1, 14] = hexc(GOLD[2])
    px[0, 15] = hexc(FIRE[4])
    item(outline(img), "flare_greatsword")


def phoenix_bow():
    def bow(pull):
        img, px = new()
        for (t, x, y, nx, ny) in W.bow_points():
            width = 1.0 + 1.6 * math.sin(t * math.pi)
            tip = t < 0.12 or t > 0.88
            for k in range(0, int(width * 4) + 1):
                o = k / 4
                X, Y = int(x + nx * o), int(y + ny * o)
                if 0 <= X < 16 and 0 <= Y < 16:
                    px[X, Y] = ramp(FIRE[2:], 0.9 - o / 3) if tip else ramp([CRIMSON[1], "#b8241a", "#e8501a", "#ff9a3a"], 0.95 - o / max(width, 1) * 0.8)
        # Feather fins along the back of the limbs.
        for (t, x, y, nx, ny) in W.bow_points():
            if any(abs(t - f) < 0.004 for f in (0.2, 0.32, 0.68, 0.8)):
                X, Y = int(x + nx * 3.4), int(y + ny * 3.4)
                if 0 <= X < 16 and 0 <= Y < 16:
                    px[X, Y] = hexc(FIRE[4])
        for (x, y), c in {(4, 4): "#ffffff", (5, 4): FIRE[4], (4, 5): FIRE[4], (5, 5): FIRE[2]}.items():
            px[x, y] = hexc(c)
        img = outline(img)
        px = img.load()
        a, b = (13, 3), (3, 13)
        if pull == 0:
            W.line(px, a, b, "#ffe6a0")
        else:
            back = {1: (10, 10), 2: (11, 11), 3: (12, 12)}[pull]
            W.line(px, a, back, "#ffe6a0")
            W.line(px, back, b, "#ffe6a0")
            W.line(px, back, (6, 6), FIRE[3])
            px[6, 6] = hexc("#ffffff")
            px[7, 7] = hexc(FIRE[4])
            px[back[0], back[1]] = hexc(FIRE[4])
        return img
    item(bow(0), "phoenix_bow")
    for i in range(3):
        item(bow(i + 1), f"phoenix_bow_pulling_{i}")


def helios_scepter():
    img, px = new()
    W.handle(px, (1, 14), (8, 7), GOLD[2], GOLD[0])
    for (x, y) in ((3, 12), (6, 9)):
        px[x, y] = hexc(CRIMSON[2])
        px[x + 1, y] = hexc(CRIMSON[1])
    px[1, 14] = hexc(FIRE[4])
    cx, cy = 11.0, 4.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - (cx + 0.5), y + 0.5 - (cy + 0.5)
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            ray = abs(math.cos(ang * 4)) > 0.82
            if r <= 2.6:
                px[x, y] = ramp(FIRE[3:], 1.0 - r / 2.6)
            elif ray and r <= 4.6:
                px[x, y] = hexc(GOLD[2] if r < 3.8 else GOLD[1])
    px[11, 4] = hexc("#ffffff")
    item(outline(img), "helios_scepter")


def cinder_chakram():
    img, px = new()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            spike = (math.cos(ang * 8) + 1) / 2
            outer = 5.0 + spike * 2.4
            if 2.6 <= r <= outer:
                if r > 5.0:
                    px[x, y] = ramp(FIRE[1:5], 0.3 + (1 - (r - 5.0) / 2.4) * 0.7)
                else:
                    t = abs(r - 3.8) / 1.2
                    px[x, y] = ramp([SUN[2], SUN[5], SUN[7]], 1.0 - t)
    item(outline(img), "cinder_chakram")


def sunburst_flask():
    img, px = new()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 10
            r = math.hypot(dx, dy)
            if r <= 5.2:
                if y < 8:
                    px[x, y] = hexc("#ffe6a0") if r < 4.5 else hexc("#b88a40")
                else:
                    px[x, y] = ramp(FIRE[3:], 1.0 - r / 5.2)
    for y in range(2, 6):
        for x in range(6, 10):
            px[x, y] = hexc("#d8c8a0" if x in (6, 9) else "#f0e6c8")
    for x in range(6, 10):
        px[x, 1] = hexc("#8a5a2a")
        px[x, 2] = hexc("#6a3e1a")
    px[6, 9] = hexc("#ffffff")
    px[5, 10] = hexc("#fff6d0")
    item(outline(img), "sunburst_flask")


def solar_key():
    img, px = new()
    # Sun-shaped bow (top-left), long shaft to the bit (bottom-right).
    cx, cy = 4.5, 4.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - (cx + 0.5), y + 0.5 - (cy + 0.5)
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            if r <= 2.0:
                px[x, y] = ramp(FIRE[3:], 1.0 - r / 2.0)
            elif 2.0 < r <= 3.2:
                px[x, y] = hexc(GOLD[2])
            elif r <= 4.6 and abs(math.cos(ang * 4)) > 0.85:
                px[x, y] = hexc(GOLD[1])
    W.line(px, (7, 7), (13, 13), GOLD[2])
    W.line(px, (8, 7), (14, 13), GOLD[1])
    for (x, y) in ((12, 14), (13, 15), (11, 13), (10, 14)):
        px[x, y] = hexc(GOLD[2])
    px[4, 4] = hexc("#ffffff")
    item(outline(img), "solar_key")


def phoenix_egg():
    img, px = new()
    rng = random.Random(3)
    for y in range(16):
        for x in range(16):
            dy = (y - 9.0) / (6.0 if y > 9 else 7.5)
            d = math.hypot((x - 7.5) / 5.2, dy)
            if d < 1.0:
                t = (y - 2) / 13
                c = ramp([FIRE[4], FIRE[2], CRIMSON[2]], t)
                if (x + int(t * 9)) % 5 == 0 and rng.random() < 0.6:
                    c = hexc(FIRE[4])
                px[x, y] = c
    for (x, y) in ((6, 7), (7, 8), (8, 9), (8, 10), (9, 11)):
        px[x, y] = hexc("#fff6c0")
    px[5, 4] = hexc("#fffbe0")
    item(outline(img, "#2a0806"), "phoenix_egg")


def sunfire_sigil():
    img, px = new()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            if r <= 3.0:
                px[x, y] = ramp(FIRE[3:], 1.0 - r / 3.0)
            elif r <= 5.0:
                px[x, y] = hexc(GOLD[2] if r < 4.3 else GOLD[1])
            elif r <= 7.4 and abs(math.cos(ang * 6)) > 0.88:
                px[x, y] = hexc(FIRE[3] if r < 6.4 else FIRE[2])
    px[8, 8] = px[7, 8] = hexc("#ffffff")
    item(outline(img), "sunfire_sigil")


def sun_heart():
    img, px = new()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            rays = 4.6 + 2.0 * max(0, math.cos(ang * 8)) ** 6
            if r <= 4.6:
                px[x, y] = ramp(["#ffffff", FIRE[4], FIRE[3], FIRE[2]], r / 4.6)
            elif r <= rays:
                px[x, y] = hexc(FIRE[3])
    item(outline(img, "#3a1206"), "sun_heart")


def materials():
    rng = random.Random(9)
    # Raw sunsteel: lumpy nugget.
    img, px = new()
    for (cx, cy, r) in ((6, 9, 3.6), (10, 7, 3.0), (9, 11, 2.8)):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d < r:
                    px[x, y] = ramp([SUN[6], SUN[4], SUN[2]], d / r * 0.8 + rng.random() * 0.2)
    for (x, y) in ((5, 8), (9, 6), (9, 10)):
        px[x, y] = hexc(SUN[7])
    item(outline(img), "raw_sunsteel")

    # Sunsteel ingot (vanilla-style trapezoid ingot).
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
    item(W.grid(rows, {"#": OUT, "w": SUN[7], "y": SUN[5], "Y": SUN[4], "o": SUN[3]}), "sunsteel_ingot")

    # Ember shard.
    img, px = new()
    for y in range(16):
        for x in range(16):
            along = ((x - 4) + (12 - y)) / math.sqrt(2)
            across = ((x - 4) - (12 - y)) / math.sqrt(2)
            if 0 <= along <= 12:
                w = 2.4 * (1 - abs(along - 5) / 7.5)
                if abs(across) <= w:
                    px[x, y] = ramp(FIRE[2:], 1.0 - abs(across) / max(w, 0.01) * 0.8 - along / 40)
    item(outline(img, "#3a0e04"), "ember_shard")

    # Solar essence: a swirling wisp.
    img, px = new()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 9
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx) + r * 0.6
            if r <= 5.5 and (math.sin(ang * 2) > -0.3 or r < 2.2):
                px[x, y] = ramp(["#fffbe0", FIRE[4], FIRE[3], FIRE[2]], r / 5.5)
    for (x, y) in ((8, 2), (8, 3), (9, 4), (7, 3)):
        px[x, y] = hexc(FIRE[4])
    item(outline(img, "#3a1206"), "solar_essence")

    # Phoenix feather.
    img, px = new()
    W.line(px, (3, 14), (12, 2), "#f0e0c0")
    for s in range(1, 11):
        t = s / 11
        bx, by = 3 + 9 * t, 14 - 12 * t
        width = 2.8 * math.sin(t * math.pi) + 0.6
        for k in range(1, int(width) + 2):
            for side in (-1, 1):
                x, y = int(round(bx + side * k * 0.7)), int(round(by + side * k * 0.7))
                if 0 <= x < 16 and 0 <= y < 16 and k <= width:
                    px[x, y] = ramp([FIRE[4], FIRE[2], CRIMSON[2]], k / max(width, 1) * 0.6 + t * 0.4)
    item(outline(img, "#2a0806"), "phoenix_feather")

    # Projectile visual.
    flare = T.radial(16, (8, 8), 7.0, hexc("#fffbe0"), hexc("#ff6a10"), None)
    item(outline(flare, "#7a1608"), "solar_flare")


def armor_icons():
    pal = {"#": OUT, "a": SUN[7], "b": SUN[5], "c": SUN[3], "d": SUN[2], "h": CRIMSON[2], "H": CRIMSON[1], "g": FIRE[4],
           "y": GOLD[2], "Y": GOLD[1], "w": "#ffffff"}
    for name, rows in (("helmet", T.HELMET), ("chestplate", T.CHESTPLATE), ("leggings", T.LEGGINGS), ("boots", T.BOOTS)):
        item(T.sprite(rows, pal), f"sunsteel_{name}")
    # Phoenix Mantle: crimson chest piece with flame feathers.
    img = T.sprite(T.CHESTPLATE, dict(pal, a=FIRE[4], b=FIRE[3], c="#c8300c", d=CRIMSON[2]))
    px = img.load()
    for (x, y) in ((4, 9), (11, 9), (5, 11), (10, 11), (7, 12), (8, 12)):
        if px[x, y][3]:
            px[x, y] = hexc(FIRE[5])
    item(img, "phoenix_mantle")
    # Magma Treads: basalt boots with lava cracks.
    img = T.sprite(T.BOOTS, dict(pal, a="#5a504a", b="#3a3430", c="#2a2420", d="#1a1614"))
    px = img.load()
    for (x, y) in ((3, 9), (4, 10), (11, 9), (12, 10), (4, 12), (11, 12), (2, 12), (13, 12)):
        if px[x, y][3]:
            px[x, y] = hexc(FIRE[3])
    item(img, "magma_treads")
    # Solar Crown.
    crown = T.sprite([
        "................",
        "................",
        "................",
        ".......#........",
        "......#g#.......",
        "..#..#ggg#..#...",
        "..#y#.#g#.#y#...",
        "..#yy#y#y#yy#...",
        "..#yyyyyyyyyy#..",
        "..#ygyyrryygy#..",
        "..#YYYYYYYYYY#..",
        "...##########...",
        "................",
        "................",
        "................",
        "................",
    ], {"#": OUT, "y": GOLD[2], "Y": GOLD[1], "g": FIRE[4], "r": FIRE[2]})
    item(crown, "solar_crown")


def spawn_eggs():
    eggs = {
        "cinder_imp": ("#3a1612", "#ff7a1a"),
        "magma_crawler": ("#2c2622", "#ff6a10"),
        "ember_hound": ("#24201e", "#ffb030"),
        "ashen_knight": ("#5e5a56", "#ff8a20"),
        "solar_phoenix": ("#e0441a", "#ffe080"),
        "sun_warden": ("#c89a3a", "#fff2b0"),
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
        item(T.outline(img, (24, 10, 6, 255)), f"{name}_spawn_egg")


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


def scorch(seed):
    rng = random.Random(seed)
    img = T.noise_tex(seed, "#9a4e26", "#6e3018", 1.0)
    px = img.load()
    for _ in range(3):
        x, y = rng.randint(0, 15), rng.randint(0, 15)
        for _ in range(6):
            px[x % 16, y % 16] = hexc("#4a1e0e")
            x += rng.choice((-1, 0, 1))
            y += rng.choice((0, 1))
    for _ in range(10):
        px[rng.randint(0, 15), rng.randint(0, 15)] = hexc("#b8643a")
    return img


def bricks(seed, cracked=False, light="#e0aa62", dark="#b8803e", mortar="#6a4420"):
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
                    c = lerp(c, hexc("#f4d08a"), 0.3)
            px[x, y] = c
    if cracked:
        x, y = 4, 0
        for _ in range(18):
            px[x % 16, y % 16] = hexc("#4a2c12")
            x += r.choice((0, 1))
            y += 1
    return img


def blocks():
    rng = random.Random(77)
    block(scorch(1), "scorchstone")

    ore = scorch(1)
    px = ore.load()
    for cluster in ((4, 4), (11, 5), (5, 11), (12, 12)):
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1), (2, 1), (1, 2)):
            x = min(15, max(0, cluster[0] + dx))
            y = min(15, max(0, cluster[1] + dy))
            px[x, y] = ramp([SUN[7], SUN[5], SUN[3]], (dx + dy + 1) / 4)
        px[cluster] = hexc("#ffffff")
    block(ore, "sunstone_ore")

    block(T.noise_tex(5, "#f2c25a", "#d8962e", 1.0), "sunsand")

    top = T.noise_tex(6, "#8a8480", "#5e5854", 1.0)
    px = top.load()
    for _ in range(9):
        px[rng.randint(0, 15), rng.randint(0, 15)] = hexc(FIRE[3] if rng.random() < 0.5 else FIRE[2])
    block(top, "ashen_soil_top")
    side = T.noise_tex(7, "#4a2e1e", "#2e1c12", 1.0)
    px = side.load()
    tpx = top.load()
    for x in range(16):
        depth = 3 + (1 if rng.random() < 0.4 else 0)
        for y in range(depth):
            px[x, y] = tpx[x, y]
    for _ in range(4):
        px[rng.randint(0, 15), rng.randint(5, 15)] = hexc(FIRE[2])
    block(side, "ashen_soil_side")
    block(T.noise_tex(7, "#4a2e1e", "#2e1c12", 1.0), "ashen_soil_bottom")

    # Ember crystal cluster (cross).
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for (cx, h, w) in ((4, 9, 2), (8, 14, 3), (12, 8, 2), (6, 6, 1), (10, 11, 2)):
        for y in range(16 - h, 16):
            for x in range(cx - w, cx + w + 1):
                if 0 <= x < 16:
                    t = (y - (16 - h)) / h
                    if abs(x - cx) <= w * min(1.0, t * 2.2 + 0.2):
                        px[x, y] = ramp(["#fffbe0", FIRE[4], FIRE[2], "#8a1e08"], t * 0.85 + (0.12 if x > cx else 0))
    block(img, "ember_crystal_cluster")

    # Sunbloom (cross flower).
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(8, 16):
        px[8, y] = hexc("#4a6a1e" if y % 3 else "#5e8a26")
    px[7, 12] = px[6, 11] = hexc("#5e8a26")
    px[9, 13] = px[10, 12] = hexc("#5e8a26")
    for y in range(1, 9):
        for x in range(4, 13):
            d = math.hypot(x + 0.5 - 8.5, y + 0.5 - 5)
            ang = math.atan2(y + 0.5 - 5, x + 0.5 - 8.5)
            petal = d < 3.4 + 0.9 * math.cos(ang * 6)
            if d < 1.4:
                px[x, y] = hexc("#fff6c0")
            elif petal:
                px[x, y] = ramp([SUN[6], SUN[5], SUN[4]], d / 4.2)
    block(img, "sunbloom")

    # Sunsteel block: brushed gold-orange with a sun emblem.
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            c = ramp([SUN[6], SUN[4]], (y / 15) * 0.7 + rng.random() * 0.15)
            if x in (0, 15) or y in (0, 15):
                c = hexc(SUN[2])
            px[x, y] = c
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            ang = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
            if d < 2.2:
                px[x, y] = hexc(FIRE[4])
            elif d < 5.0 and abs(math.cos(ang * 4)) > 0.92:
                px[x, y] = hexc(FIRE[3])
    block(img, "sunsteel_block")

    block(bricks(3), "sunbaked_bricks")
    block(bricks(3, cracked=True), "cracked_sunbaked_bricks")

    img = bricks(5)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = hexc("#6a4420")
            elif x in (1, 14) or y in (1, 14):
                px[x, y] = hexc(GOLD[2])
            else:
                px[x, y] = hexc("#b8803e")
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            ang = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
            if d < 2.0:
                px[x, y] = hexc("#fff2a0")
            elif d < 2.8:
                px[x, y] = hexc(FIRE[3])
            elif d < 5.6 and abs(math.cos(ang * 4)) > 0.9:
                px[x, y] = hexc(FIRE[2])
    block(img, "chiseled_sunbaked_bricks")

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = hexc(GOLD[1], 255)
            else:
                px[x, y] = hexc("#ff9a30", 105)
    for (x, y) in ((3, 4), (11, 3), (7, 9), (12, 12), (4, 12)):
        px[x, y] = hexc("#fff6d0", 220)
    px[2, 2] = px[3, 2] = px[2, 3] = hexc("#ffe0a0", 170)
    block(img, "solar_glass")

    # Sun lantern (animated).
    frames = []
    for f in range(4):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                ang = math.atan2(y + 0.5 - 8, x + 0.5 - 8) + f * 0.2
                twinkle = 0.85 + 0.15 * math.sin(f * math.pi / 2 + d)
                c = ramp(["#fffbe0", FIRE[4], FIRE[3]], min(1, d / 7))
                if d > 4 and abs(math.cos(ang * 4)) > 0.9:
                    c = hexc("#fff2a0")
                c = tuple(int(min(255, v * twinkle)) for v in c[:3]) + (255,)
                if x in (0, 15) or y in (0, 15):
                    c = hexc("#6a4420")
                px[x, y] = c
        frames.append(img)
    animated(frames, "sun_lantern", 6)

    # Solar brazier: gold bowl sides, coal top (unlit) / fire top (lit, animated).
    side = Image.new("RGBA", (16, 16))
    px = side.load()
    for y in range(16):
        for x in range(16):
            c = ramp([GOLD[3], GOLD[2], GOLD[1]], y / 15 + rng.random() * 0.1)
            if y in (0, 3, 12, 15):
                c = hexc(GOLD[0])
            if 5 <= y <= 9 and x % 4 == 1:
                c = hexc(FIRE[2])
            px[x, y] = c
    block(side, "solar_brazier_side")
    coal = T.noise_tex(12, "#2a2420", "#121010", 1.0)
    cpx = coal.load()
    for x in range(16):
        cpx[x, 0] = cpx[x, 15] = cpx[0, x] = cpx[15, x] = hexc(GOLD[1])
    block(coal, "solar_brazier_top")
    frames = []
    for f in range(4):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                v = (math.sin(x * 0.9 + f * 1.6) + math.sin(y * 0.8 - f * 1.3) + 2) / 4
                c = ramp(["#fffbe0", FIRE[4], FIRE[3], FIRE[2]], min(1, d / 8 + v * 0.3))
                if x in (0, 15) or y in (0, 15):
                    c = hexc(GOLD[1])
                px[x, y] = c
        frames.append(img)
    animated(frames, "solar_brazier_top_lit", 3)

    # Sun seal (animated woven sunlight).
    frames = []
    for f in range(4):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                v = (math.sin((x + f * 2) * 0.8) + math.sin((y - f * 2) * 0.8) + math.sin((x + y + f * 3) * 0.5)) / 3
                c = ramp([FIRE[2], FIRE[3], "#fff2a0"], (v + 1) / 2)
                if (x - y + f) % 8 == 0:
                    c = hexc("#ffffff")
                if x in (0, 15) or y in (0, 15):
                    c = hexc(GOLD[1])
                px[x, y] = c
        frames.append(img)
    animated(frames, "sun_seal", 4)

    # Sunfire vent: iron grate over a sunfire well (dormant / hissing / erupting).
    def vent(heat):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                grate = x % 4 == 0 or y % 4 == 0
                if x in (0, 15) or y in (0, 15):
                    c = hexc("#6a4420")
                elif grate:
                    c = hexc("#3a2e28")
                else:
                    d = math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8
                    c = ramp(["#fffbe0", FIRE[4], FIRE[2], "#3a0e04"], min(1.0, d * (1.6 - heat) + (1 - heat) * 0.5))
                px[x, y] = c
        return img
    block(vent(0.0), "sunfire_vent")
    block(vent(0.6), "sunfire_vent_warning")
    block(vent(1.0), "sunfire_vent_active")

    # Sun altar.
    side = bricks(9)
    spx = side.load()
    for x in range(16):
        for y in (0, 1, 14, 15):
            spx[x, y] = hexc(GOLD[2]) if y in (1, 14) else hexc(GOLD[1])
    for (x, y) in ((7, 6), (8, 6), (7, 7), (8, 7), (6, 8), (9, 8)):
        spx[x, y] = hexc(FIRE[3])
    block(side, "sun_altar_side")
    top = Image.new("RGBA", (16, 16))
    tpx = top.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            ang = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
            c = hexc("#8a5a2a")
            if 6.0 < d < 7.2:
                c = hexc(GOLD[2])
            elif d < 2.2:
                c = hexc("#fff6c0")
            elif d < 5.5 and abs(math.cos(ang * 4)) > 0.85:
                c = hexc(FIRE[3])
            if x in (0, 15) or y in (0, 15):
                c = hexc(GOLD[1])
            tpx[x, y] = c
    block(top, "sun_altar_top")
    block(bricks(9), "sun_altar_bottom")

    # Solar gateway: liquid sunlight (animated).
    frames = []
    for f in range(8):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                v = (math.sin(x * 0.7 + f * 0.8) + math.sin(y * 0.6 + f * 0.6) + math.sin((x - y) * 0.4 - f)) / 3
                c = ramp(["#ffffff", "#fff2a0", FIRE[4], FIRE[3]], (v + 1) / 2)
                px[x, y] = c[:3] + (235,)
        frames.append(img)
    animated(frames, "solar_gateway", 3)


# ----------------------------------------------------------------------------------------------------------------
# Effects & armor layers
# ----------------------------------------------------------------------------------------------------------------

def effects():
    img = Image.new("RGBA", (16, 64), (0, 0, 0, 0))
    px = img.load()
    for y in range(64):
        for x in range(16):
            d = abs(x - 7.5) / 7.5
            streak = 0.8 + 0.2 * math.sin(y * 0.5 + x * 0.7)
            a = max(0.0, 1.0 - d ** 1.4) * streak
            px[x, y] = (255, 255, 255, int(255 * a))
    save(img, "entity/solar_beam.png")

    flare = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = flare.load()
    for y in range(64):
        for x in range(64):
            dx, dy = x + 0.5 - 32, y + 0.5 - 32
            d = math.hypot(dx, dy) / 32
            ang = math.atan2(dy, dx)
            rays = 0.25 * max(0, math.cos(ang * 6)) ** 8
            v = max(0.0, 1.0 - d / (0.55 + rays)) if d < 1 else 0
            if v > 0:
                c = lerp((255, 250, 230, 255), (255, 150, 40, 255), min(1, d * 1.4))
                px[x, y] = c[:3] + (int(255 * min(1, v ** 0.7)),)
    save(flare, "entity/solar_flare.png")

    orb = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = orb.load()
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16
            if d < 0.75:
                c = lerp((255, 255, 240, 255), (255, 170, 40, 255), d / 0.75)
                px[x, y] = c
            elif d < 1.0:
                px[x, y] = (255, 200, 80, int(255 * (1 - (d - 0.75) / 0.25)))
    save(orb, "entity/helios_orb.png")


def armor_layers():
    rng = random.Random(31)
    main, dark, light, glow = hexc(SUN[5]), hexc(SUN[1]), hexc(SUN[7]), hexc(FIRE[3])
    paint = T.armor_paint(main, dark, light, glow, rng)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    T.fill_box(px, 0, 0, 8, 8, 8, paint)
    for x in range(9, 15):
        px[x, 11] = glow
    T.fill_box(px, 16, 16, 8, 12, 4, paint)
    px[19, 22] = px[20, 22] = px[19, 23] = px[20, 23] = hexc(FIRE[4])
    T.fill_box(px, 40, 16, 4, 12, 4, paint)
    T.fill_box(px, 0, 16, 4, 12, 4, lambda f, x, y, w, h: paint(f, x, y, w, h) if y >= h - 5 or f in ("top", "bottom") else None)
    save(img, "entity/equipment/humanoid/sunsteel.png")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    T.fill_box(px, 16, 16, 8, 12, 4, lambda f, x, y, w, h: paint(f, x, y, w, h) if y >= h - 5 else None)
    T.fill_box(px, 0, 16, 4, 12, 4, lambda f, x, y, w, h: paint(f, x, y, w, h) if y < h - 3 else None)
    save(img, "entity/equipment/humanoid_leggings/sunsteel.png")

    # Phoenix Mantle chest + flame wings.
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()

    def plumage(face, x, y, w, h):
        c = ramp([FIRE[3], FIRE[2], CRIMSON[2]], y / max(1, h - 1))
        if x % 2 == 1:
            c = lerp(c, hexc(CRIMSON[1]), 0.2)
        if x == 0 or y == 0 or x == w - 1 or y == h - 1:
            c = hexc(CRIMSON[0])
        return c
    T.fill_box(px, 16, 16, 8, 12, 4, plumage)
    T.fill_box(px, 40, 16, 4, 12, 4, plumage)
    save(img, "entity/equipment/humanoid/phoenix.png")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()

    def wing(face, x, y, w, h):
        t = y / max(1, h - 1)
        c = ramp(["#fff2a0", FIRE[4], FIRE[3], FIRE[2], CRIMSON[2]], t)
        if x % 3 == 0:
            c = lerp(c, hexc(CRIMSON[1]), 0.25)
        if rng.random() < 0.04:
            c = hexc("#fffbe0")
        return c
    T.fill_box(px, 22, 0, 10, 20, 2, wing)
    save(img, "entity/equipment/wings/phoenix.png")

    # Magma treads (boots).
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()

    def basalt(face, x, y, w, h):
        if not (y >= h - 5 or face in ("bottom",)):
            return None
        c = lerp(hexc("#3a3430"), hexc("#1a1614"), rng.random() * 0.8)
        if (x * 3 + y * 5) % 7 == 0:
            c = hexc(FIRE[3])
        return c
    T.fill_box(px, 0, 16, 4, 12, 4, basalt)
    save(img, "entity/equipment/humanoid/magma.png")

    # Solar crown (helmet layer: a gold circlet with sun rays).
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()

    def crown(face, x, y, w, h):
        if face in ("north", "south", "east", "west"):
            if y < 3:
                return hexc(FIRE[4]) if x % 3 == 1 else None
            if y < 6:
                if face == "north" and x in (3, 4) and y == 4:
                    return hexc("#ffffff")
                return ramp([GOLD[3], GOLD[2]], (y - 3) / 3)
        return None
    T.fill_box(px, 32, 0, 8, 8, 8, crown)
    save(img, "entity/equipment/humanoid/solar.png")


def main():
    vanilla = os.environ.get("VANILLA_ITEMS", "")
    if "--vanilla" in sys.argv:
        vanilla = sys.argv[sys.argv.index("--vanilla") + 1]
    if not os.path.isdir(vanilla):
        sys.exit("Pass the vanilla item texture folder with --vanilla")
    sunsteel_tools(vanilla)
    solar_lance()
    flare_greatsword()
    phoenix_bow()
    helios_scepter()
    cinder_chakram()
    sunburst_flask()
    solar_key()
    phoenix_egg()
    sunfire_sigil()
    sun_heart()
    materials()
    armor_icons()
    spawn_eggs()
    blocks()
    effects()
    armor_layers()
    print("sun textures written")


if __name__ == "__main__":
    main()
