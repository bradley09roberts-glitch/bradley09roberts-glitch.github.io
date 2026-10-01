#!/usr/bin/env python3
"""
Starforged texture generator: item sprites (hand-drawn pixel art + procedural), block textures, particles,
armor/equipment layers, effect textures and the mod logo.
"""
import math
import os
import random
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/starforged/textures")


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def save(img, rel):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def sprite(rows, palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        assert len(row) == 16, (row, len(row))
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = hexc(palette[ch]) if isinstance(palette[ch], str) else palette[ch]
    return img


# ----------------------------------------------------------------------------------------------------------------
# Palettes
# ----------------------------------------------------------------------------------------------------------------

STARMETAL = {"#": "#141a33", "a": "#f2fbff", "b": "#a9d2f0", "c": "#5c87bf", "d": "#3a5a8f",
             "h": "#5b3a86", "H": "#341f52", "g": "#7ff4ff", "y": "#f0c45a", "Y": "#a8761f", "w": "#ffffff"}

# ----------------------------------------------------------------------------------------------------------------
# Hand-drawn items
# ----------------------------------------------------------------------------------------------------------------

SWORD = [
    "..............##",
    ".............#a#",
    "............#ab#",
    "...........#abc#",
    "..........#abc#.",
    ".........#abc#..",
    "..##....#abc#...",
    "..#y#..#abc#....",
    "...#y##abc#.....",
    "....#yabc#......",
    ".....#yy#.......",
    "....#h##y#......",
    "...#h#..##......",
    "..#H#...........",
    ".#g#............",
    ".##.............",
]

PICKAXE = [
    "................",
    "....#######.....",
    "...#abbbbbb##...",
    "....##ccccbba#..",
    "......##h#cba#..",
    ".......#h#.#ca#.",
    "......#h#...#a#.",
    ".....#h#....#a#.",
    "....#h#......#..",
    "...#h#..........",
    "..#h#...........",
    ".#H#............",
    "#g#.............",
    "##..............",
    "................",
    "................",
]

AXE = [
    "................",
    ".......###......",
    "......#abb#.....",
    ".....#aabbc#....",
    ".....#abbc#h#...",
    "......#bc#h#....",
    ".......##h#.....",
    "........#h#.....",
    ".......#h#......",
    "......#h#.......",
    ".....#h#........",
    "....#h#.........",
    "...#H#..........",
    "..#g#...........",
    "..##............",
    "................",
]

SHOVEL = [
    "............###.",
    "...........#aab#",
    "..........#abbc#",
    "..........#bbc#.",
    ".........#h##...",
    "........#h#.....",
    ".......#h#......",
    "......#h#.......",
    ".....#h#........",
    "....#h#.........",
    "...#h#..........",
    "..#H#...........",
    ".#g#............",
    ".##.............",
    "................",
    "................",
]

HOE = [
    "................",
    "......#####.....",
    ".....#aabbc#....",
    "......###h##....",
    ".........#h#....",
    "........#h#.....",
    ".......#h#......",
    "......#h#.......",
    ".....#h#........",
    "....#h#.........",
    "...#h#..........",
    "..#H#...........",
    ".#g#............",
    ".##.............",
    "................",
    "................",
]

HELMET = [
    "................",
    "................",
    "................",
    "....########....",
    "...#aabbbbcc#...",
    "..#abbbbbbbcc#..",
    "..#ab#gggg#bc#..",
    "..#ab#....#bc#..",
    "..#b#......#c#..",
    "..###......###..",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

CHESTPLATE = [
    "................",
    "..###......###..",
    "..#ab#....#bc#..",
    ".#abbb####bbcc#.",
    ".#abbbbbbbbbcc#.",
    ".#ab#bbggbb#cc#.",
    "..##bbbggbbb##..",
    "...#bbbbbbbbc#..",
    "...#abbbbbbcc#..",
    "...#abbbbbbcc#..",
    "...#bbbbbbbbc#..",
    "...##########...",
    "................",
    "................",
    "................",
    "................",
]

LEGGINGS = [
    "................",
    "................",
    "...##########...",
    "...#abbbbbbcc#..",
    "...#ab#gg#bcc#..",
    "...#ab#..#bcc#..",
    "...#ab#..#bc#...",
    "...#ab#..#bc#...",
    "...#ab#..#bc#...",
    "...#ab#..#bc#...",
    "...####..####...",
    "................",
    "................",
    "................",
    "................",
    "................",
]

BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "...###....###...",
    "...#ab#..#bc#...",
    "...#ab#..#bc#...",
    "..#abb#..#bbc#..",
    "..#####..#####..",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

STAFF = [
    "...........w.g..",
    "..........#gwg#.",
    ".........#gwywg#",
    "..........#gwg#.",
    ".........#h#g...",
    "........#h#.....",
    ".......#h#......",
    "......#h#.......",
    ".....#y#........",
    "....#h#.........",
    "...#h#..........",
    "..#h#...........",
    ".#h#............",
    "#y#.............",
    "##..............",
    "................",
]

HAMMER = [
    "................",
    "......####......",
    ".....#mmom#.....",
    "....#moomlm#....",
    "...#mmolmmom#...",
    "...#molmmoom#...",
    "....#mmolm##....",
    ".....#mm#h#.....",
    "......##h#......",
    ".......#h#......",
    "......#h#.......",
    ".....#h#........",
    "....#h#.........",
    "...#y#..........",
    "..#h#...........",
    "..##............",
]

SCYTHE = [
    "....######......",
    "..##vvvvvv##....",
    ".#vVVVVVVvvv#...",
    "#vV####VVVvv#...",
    "#v#....###Vh#...",
    "##........#h#...",
    "..........#h#...",
    ".........#h#....",
    ".........#h#....",
    "........#y#.....",
    "........#h#.....",
    ".......#h#......",
    ".......#h#......",
    "......#h#.......",
    "......#g#.......",
    "......##........",
]

ECLIPSE_BLADE = [
    "..............##",
    ".............#w#",
    "............#wp#",
    "...........#wpk#",
    "..........#wpk#.",
    ".........#wpk#..",
    "..##....#wpk#...",
    "..#y#..#wpk#....",
    "...#y##wpk#.....",
    "....#ygpk#......",
    ".....#yy#.......",
    "....#k##y#......",
    "...#k#..##......",
    "..#K#...........",
    ".#g#............",
    ".##.............",
]

GAUNTLET = [
    "................",
    "....#.#.#.......",
    "...#a#a#a#......",
    "...#b#b#b##.....",
    "...#bbbbb#a#....",
    "...#bbbbbbb#....",
    "...#bbgggbb#....",
    "...#bbgwgbb#....",
    "...#bbgggbc#....",
    "....#bbbbc#.....",
    "....#yyyyy#.....",
    "....#hhhhh#.....",
    "....#hhhhh#.....",
    ".....#####......",
    "................",
    "................",
]


def draw_items():
    tools = {"starmetal_sword": SWORD, "starmetal_pickaxe": PICKAXE, "starmetal_axe": AXE, "starmetal_shovel": SHOVEL,
             "starmetal_hoe": HOE, "starmetal_helmet": HELMET, "starmetal_chestplate": CHESTPLATE,
             "starmetal_leggings": LEGGINGS, "starmetal_boots": BOOTS, "gravity_gauntlet": GAUNTLET}
    for name, rows in tools.items():
        save(sprite(rows, STARMETAL), f"item/{name}.png")

    comet = dict(STARMETAL, a="#fff2c2", b="#ffb347", c="#d9601e", g="#7ff4ff")
    save(sprite(BOOTS, comet), "item/comet_boots.png")
    img = sprite(BOOTS, comet)
    px = img.load()
    for (x, y, c) in [(1, 7, "#ffe08a"), (0, 8, "#ffb347"), (14, 7, "#9ff3ff"), (15, 8, "#5ad0ff"), (1, 9, "#ff7a3a"), (14, 9, "#5ad0ff")]:
        px[x, y] = hexc(c)
    save(img, "item/comet_boots.png")

    nebula = dict(STARMETAL, a="#c9b8ff", b="#6c4fd6", c="#3a2a8f", g="#fff4b0")
    img = sprite(CHESTPLATE, nebula)
    px = img.load()
    rng = random.Random(4)
    for _ in range(7):
        x, y = rng.randint(4, 11), rng.randint(3, 10)
        if px[x, y][3] and px[x, y][:3] != hexc("#141a33")[:3]:
            px[x, y] = hexc("#fff4b0")
    save(img, "item/nebula_cloak.png")

    crown = sprite([
        "................",
        "................",
        "................",
        "................",
        "..#...#..#...#..",
        "..#y#.#y##.#y#..",
        "..#yy#yppy#yy#..",
        "..#yyyyppyyyy#..",
        "..#ygyyyyyygy#..",
        "..#YYYYYYYYYY#..",
        "...##########...",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], dict(STARMETAL, p="#ff5ae0", g="#7ff4ff"))
    save(crown, "item/eclipse_crown.png")

    save(sprite(STAFF, STARMETAL), "item/starcaller_staff.png")
    save(sprite(HAMMER, dict(STARMETAL, m="#3a3340", o="#ff7a2a", l="#ffd27a")), "item/meteor_hammer.png")
    save(sprite(SCYTHE, dict(STARMETAL, v="#b47cff", V="#6a2fc4", h="#2a1d3d", g="#c58bff")), "item/void_scythe.png")
    save(sprite(ECLIPSE_BLADE, dict(STARMETAL, w="#ffe9ff", p="#b45cff", k="#24123d", K="#120820", g="#ff5ae0")), "item/eclipse_blade.png")

    draw_heavy_tools()
    draw_bow()
    draw_round_items()
    draw_compass()
    draw_spawn_eggs()


def handle_line(px, x0, y0, x1, y1, col, dark):
    steps = max(abs(x1 - x0), abs(y1 - y0))
    for s in range(steps + 1):
        x = round(x0 + (x1 - x0) * s / steps)
        y = round(y0 + (y1 - y0) * s / steps)
        px[x, y] = col
        if x + 1 < 16:
            px[x + 1, y] = dark


def draw_heavy_tools():
    rng = random.Random(31)
    # Meteor Hammer: a molten meteorite head on a starmetal haft.
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    handle_line(px, 1, 14, 8, 7, hexc("#5b3a86"), hexc("#341f52"))
    px[1, 14] = hexc("#7ff4ff")
    px[5, 10] = hexc("#f0c45a")
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 10.0) / 4.8, (y - 5.0) / 4.4) + rng.random() * 0.12
            if d < 1.0:
                c = lerp(hexc("#5a5262"), hexc("#221d28"), d)
                if rng.random() < 0.22 or (x + y) % 5 == 0 and d < 0.7:
                    c = lerp(hexc("#ffd27a"), hexc("#ff5a1a"), rng.random())
                px[x, y] = c
    save(outline(img), "item/meteor_hammer.png")

    # Starmetal Axe: broad crescent blade.
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    handle_line(px, 2, 14, 11, 3, hexc("#5b3a86"), hexc("#341f52"))
    px[2, 14] = hexc("#7ff4ff")
    for y in range(16):
        for x in range(16):
            dx, dy = x - 9.0, y - 4.5
            d = math.hypot(dx / 4.2, dy / 3.6)
            if d < 1.0 and (x + y) < 15.5 and not (x > 10 and y > 6):
                px[x, y] = lerp(hexc("#f2fbff"), hexc("#5c87bf"), min(1, d * 0.9 + (y / 20)))
    save(outline(img), "item/starmetal_axe.png")


def draw_bow():
    base = [
        ".........#####..",
        ".......##ccbb#s.",
        "......#cb####.s.",
        ".....#cb#....s..",
        "....#cb#....s...",
        "....#b#....s....",
        "...#cb#...s.....",
        "...#b#...s......",
        "...#b#..s.......",
        "...#cb#s........",
        "....#bs.........",
        "....#cs.........",
        ".....#sb#.......",
        "......s##.......",
        ".....s..........",
        "................",
    ]
    pal = dict(STARMETAL, b="#9fd8ff", c="#4f7fd6", s="#fff4b0")
    save(sprite(base, pal), "item/constellation_bow.png")
    # Pulling frames: string drawn back with a glowing star arrow.
    for i, pull in enumerate((1, 2, 3)):
        img = sprite(base, pal)
        px = img.load()
        for x in range(16):
            for y in range(16):
                if px[x, y][:3] == hexc("#fff4b0")[:3]:
                    px[x, y] = (0, 0, 0, 0)
        # Redraw string from top to bottom through a pulled-back point.
        top, bottom = (14, 1), (5, 14)
        mid = (6 - pull, 9 - pull + 2)
        for (a, b) in ((top, mid), (mid, bottom)):
            steps = 20
            for s in range(steps + 1):
                x = round(a[0] + (b[0] - a[0]) * s / steps)
                y = round(a[1] + (b[1] - a[1]) * s / steps)
                if 0 <= x < 16 and 0 <= y < 16 and px[x, y][3] == 0:
                    px[x, y] = hexc("#fff4b0")
        # Arrow of starlight.
        for s in range(9):
            x, y = mid[0] + s, mid[1] - s
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = hexc("#7ff4ff") if s < 7 else hexc("#ffffff")
        save(img, f"item/constellation_bow_pulling_{i}.png")


def radial(size, center, radius, inner, outer, edge=None):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - center[0], y + 0.5 - center[1]) / radius
            if d <= 1.0:
                c = lerp(inner, outer, d)
                if edge and d > 0.82:
                    c = edge
                px[x, y] = c
    return img


def outline(img, color=(20, 26, 51, 255)):
    px = img.load()
    w, h = img.size
    pts = []
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and px[nx, ny][3] > 0 and px[nx, ny] != color:
                        pts.append((x, y))
                        break
    for p in pts:
        px[p] = color
    return img


def sparkle(px, x, y, color=(255, 255, 255, 255), arm=1):
    px[x, y] = color
    for i in range(1, arm + 1):
        for dx, dy in ((i, 0), (-i, 0), (0, i), (0, -i)):
            if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                px[x + dx, y + dy] = lerp(color, (color[0], color[1], color[2], 0), 0.35 * i)


def draw_round_items():
    rng = random.Random(7)

    # Stardust: a glittering pile.
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(9, 15):
        half = (y - 8) * 1.2
        for x in range(16):
            if abs(x - 7.5) <= half and rng.random() < 0.9:
                px[x, y] = lerp(hexc("#ffe08a"), hexc("#b77cff"), rng.random() * 0.8)
    for (x, y) in [(4, 4), (11, 6), (7, 2), (13, 10), (2, 9)]:
        sparkle(px, x, y, hexc("#fff8d6"), 1)
    save(outline(img, hexc("#3b2a5e")), "item/stardust.png")

    # Raw starmetal: a lumpy nugget.
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 6.0, (y - 8.5) / 5.0) + rng.random() * 0.18
            if d < 1.0:
                c = lerp(hexc("#d9ecff"), hexc("#4f6f9f"), min(1, d + (y - 4) / 30))
                if rng.random() < 0.15:
                    c = hexc("#2b2f3d")
                px[x, y] = c
    save(outline(img), "item/raw_starmetal.png")

    # Ingot.
    ingot = [
        "................",
        "................",
        "................",
        "................",
        "................",
        "......######....",
        "....##aaaaab#...",
        "...#aaabbbbbc#..",
        "..#aabbbbbbcc#..",
        "..#bbbbbbbccd#..",
        "..#cccccccdd#...",
        "...#########....",
        "................",
        "................",
        "................",
        "................",
    ]
    img = sprite(ingot, STARMETAL)
    sparkle(img.load(), 6, 7, hexc("#ffffff"), 0)
    save(img, "item/starmetal_ingot.png")

    # Astral shard.
    shard = [
        "................",
        "..........##....",
        ".........#wa#...",
        "........#wab#...",
        ".......#wabc#...",
        "......#wabc#....",
        ".....#wabcc#....",
        "....#wabcc#.....",
        "...#wabcc#......",
        "...#abcc#.......",
        "..#abcc#........",
        "..#bcc#.........",
        "..#cc#..........",
        "...##...........",
        "................",
        "................",
    ]
    save(sprite(shard, {"#": "#2a1550", "w": "#ffffff", "a": "#c8f6ff", "b": "#9b7cff", "c": "#5a2fc4"}), "item/astral_shard.png")
    save(sprite(shard, {"#": "#0b3a52", "w": "#ffffff", "a": "#c8f6ff", "b": "#5ad0ff", "c": "#2a7fc4"}), "item/crystal_shard_bolt.png")

    # Void essence: swirling dark orb.
    img = radial(16, (8, 8), 6.5, hexc("#c58bff"), hexc("#14062a"))
    px = img.load()
    for t in range(40):
        a = t * 0.35
        r = 1 + t * 0.12
        x, y = int(8 + math.cos(a) * r), int(8 + math.sin(a) * r)
        if 0 <= x < 16 and 0 <= y < 16 and px[x, y][3]:
            px[x, y] = hexc("#e6c8ff")
    save(outline(img, hexc("#0a0314")), "item/void_essence.png")

    # Celestial core: glowing orb with an orbit ring.
    img = radial(16, (8, 8), 5.0, hexc("#ffffff"), hexc("#2fb6e0"))
    px = img.load()
    for t in range(64):
        a = t / 64 * math.pi * 2
        x, y = 8 + math.cos(a) * 7.0, 8 + math.sin(a) * 2.6
        xi, yi = int(x), int(y)
        if 0 <= xi < 16 and 0 <= yi < 16:
            px[xi, yi] = hexc("#f0c45a")
    sparkle(px, 6, 6, hexc("#ffffff"), 0)
    save(outline(img, hexc("#0e2b45")), "item/celestial_core.png")

    # Sovereign's heart.
    heart = [
        "................",
        "................",
        "...###....###...",
        "..#pPp#..#pPp#..",
        ".#pPkkp##pkkPp#.",
        ".#Pkkkkppkkkkp#.",
        ".#pkkkkkkwkkkp#.",
        ".#pkkkkkkkkkkp#.",
        "..#pkkkkkkkkp#..",
        "...#pkkkkkkp#...",
        "....#pkkkkp#....",
        ".....#pkkp#.....",
        "......#pp#......",
        ".......##.......",
        "................",
        "................",
    ]
    save(sprite(heart, {"#": "#12061f", "p": "#c06bff", "P": "#ffd0f6", "k": "#3a0f5c", "w": "#ff5ae0"}), "item/sovereign_heart.png")

    # Eclipse sigil: a black sun with a corona, rimmed in gold.
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 3.4:
                px[x, y] = hexc("#050208")
            elif d < 5.0:
                px[x, y] = lerp(hexc("#fff4d6"), hexc("#c27bff"), (d - 3.4) / 1.6)
            elif d < 6.4:
                px[x, y] = hexc("#e8b84a") if (x + y) % 3 else hexc("#a8761f")
            elif d < 7.4:
                px[x, y] = hexc("#2a1640")
    save(img, "item/eclipse_sigil.png")

    # Astral egg.
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            dy = (y - 9.0) / (6.5 if y > 9 else 7.5)
            d = math.hypot((x - 7.5) / 5.2, dy)
            if d < 1.0:
                px[x, y] = lerp(hexc("#4a3ab8"), hexc("#140f3a"), d)
    for (x, y) in [(6, 5), (9, 8), (5, 11), (10, 12), (7, 9)]:
        px[x, y] = hexc("#ffe08a")
    px[6, 4] = hexc("#b9b0ff")
    save(outline(img, hexc("#0a0820")), "item/astral_egg.png")

    # Star chart: a rolled parchment with a constellation.
    chart = [
        "................",
        "................",
        "..############..",
        ".#bppppppppppb#.",
        ".#bp.........b#.",
        ".#bp........pb#.",
        ".#bp........pb#.",
        ".#bp........pb#.",
        ".#bp........pb#.",
        ".#bppppppppppb#.",
        "..############..",
        "................",
        "................",
        "................",
        "................",
        "................",
    ]
    img = sprite(chart.copy(), {"#": "#3d2a18", "b": "#8a6a3a", "p": "#e9d9b0"})
    px = img.load()
    for y in range(4, 9):
        for x in range(4, 12):
            px[x, y] = hexc("#1c2350")
    stars = [(5, 5), (7, 4), (9, 6), (10, 8), (6, 7)]
    for i in range(len(stars) - 1):
        (x0, y0), (x1, y1) = stars[i], stars[i + 1]
        for s in range(6):
            x = round(x0 + (x1 - x0) * s / 5)
            y = round(y0 + (y1 - y0) * s / 5)
            px[x, y] = hexc("#5a6ab8")
    for (x, y) in stars:
        px[x, y] = hexc("#fff4b0")
    save(img, "item/star_chart.png")

    # Singularity grenade: a black hole in a glass shell.
    img = radial(16, (8, 8.5), 6.5, hexc("#8fd8ff", 140), hexc("#4a5aa8", 200))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8.5)
            if d < 2.6:
                px[x, y] = hexc("#000000")
            elif d < 3.6:
                px[x, y] = hexc("#c58bff")
    for x in range(6, 11):
        px[x, 1] = hexc("#9aa0a8")
    px[7, 0] = px[8, 0] = hexc("#585d66")
    px[5, 5] = hexc("#ffffff")
    save(outline(img), "item/singularity_grenade.png")

    # Rift pearl.
    img = radial(16, (8, 8), 6.0, hexc("#f4d8ff"), hexc("#5a1f9e"))
    px = img.load()
    for t in range(30):
        a = t * 0.42
        r = t * 0.17
        x, y = int(8 + math.cos(a) * r), int(8 + math.sin(a) * r)
        if 0 <= x < 16 and 0 <= y < 16 and px[x, y][3]:
            px[x, y] = hexc("#1a0630")
    px[5, 5] = hexc("#ffffff")
    save(outline(img, hexc("#1a0630")), "item/rift_pearl.png")

    # Bolts.
    def star_bolt(core, glow):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y in range(16):
            for x in range(16):
                dx, dy = abs(x + 0.5 - 8), abs(y + 0.5 - 8)
                v = max(0.0, 1.0 - (dx * dy) / 3.0 - (dx + dy) / 12.0)
                if v > 0.05:
                    px[x, y] = lerp(hexc(glow, 0), hexc(core), min(1.0, v * 1.3))
        return img
    save(star_bolt("#ffffff", "#ffd27a"), "item/starbolt.png")
    save(star_bolt("#f0d8ff", "#8a2fff"), "item/void_bolt.png")


def draw_compass():
    """The Astral Compass: a starry dial with a needle at 32 angles, like the vanilla compass."""
    for i in range(32):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                if d < 6.6:
                    px[x, y] = hexc("#1c2350") if d < 5.6 else hexc("#e8b84a")
                elif d < 7.4:
                    px[x, y] = hexc("#5c3a12")
        for (x, y) in [(5, 6), (10, 5), (11, 10), (6, 11)]:
            px[x, y] = hexc("#5a6ab8")
        angle = (i / 32.0) * math.pi * 2
        # 0 = needle pointing up (towards target when held straight).
        for s in range(10):
            t = (s - 4.5) / 4.5 * 4.8
            x = 8 + math.sin(angle) * t
            y = 8 - math.cos(angle) * t
            xi, yi = int(round(x - 0.5)), int(round(y - 0.5))
            if 0 <= xi < 16 and 0 <= yi < 16:
                px[xi, yi] = hexc("#7ff4ff") if s > 4 else hexc("#f2fbff")
        px[7, 7] = px[8, 8] = hexc("#fff4b0")
        save(img, f"item/astral_compass_{i:02d}.png")


def draw_spawn_eggs():
    eggs = {
        "star_mite": ("#3b2f63", "#69e6ff"),
        "void_stalker": ("#120f1a", "#9b4dff"),
        "astral_golem": ("#4a5a78", "#a88cff"),
        "mimic": ("#a0703c", "#d6455f"),
        "astral_wraith": ("#2a3266", "#8ff7ff"),
        "starling": ("#ffd95a", "#ffffff"),
        "nebula_ray": ("#2a2470", "#ff9af0"),
        "eclipse_sovereign": ("#1b1433", "#e8b84a"),
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
        save(outline(img, (14, 10, 24, 255)), f"item/{name}_spawn_egg.png")


# ----------------------------------------------------------------------------------------------------------------
# Blocks
# ----------------------------------------------------------------------------------------------------------------

def noise_tex(seed, c1, c2, amount=1.0):
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = lerp(hexc(c1), hexc(c2), rng.random() * amount)
    return img


def draw_blocks():
    rng = random.Random(11)
    # Meteorite rock with glowing cracks.
    img = noise_tex(1, "#2b2830", "#141218")
    px = img.load()
    for path in range(2):
        x, y = rng.randint(2, 13), 0
        for step in range(16):
            if rng.random() < 0.75:
                px[x % 16, y % 16] = hexc("#d9531e") if step % 4 else hexc("#ffb050")
            x += rng.choice((-1, 0, 1))
            y += 1 if rng.random() < 0.8 else 0
    for _ in range(14):
        px[rng.randint(0, 15), rng.randint(0, 15)] = hexc("#3d3845")
    save(img, "block/meteorite_rock.png")

    # Starmetal ore: meteorite with silver-blue veins.
    img = noise_tex(2, "#2b2830", "#141218")
    px = img.load()
    for cluster in [(4, 4), (11, 5), (5, 11), (12, 12)]:
        for dx, dy in [(0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1), (2, 1), (1, 2)]:
            x = min(15, max(0, cluster[0] + dx))
            y = min(15, max(0, cluster[1] + dy))
            px[x, y] = lerp(hexc("#e9f6ff"), hexc("#4f7fd6"), (dx + dy + 1) / 4)
        px[cluster] = hexc("#ffffff")
    save(img, "block/starmetal_ore.png")

    # Starmetal block: brushed metal with a star emblem.
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            c = lerp(hexc("#c8e2f8"), hexc("#6f97c8"), (y / 15) * 0.7 + rng.random() * 0.15)
            if x in (0, 15) or y in (0, 15):
                c = hexc("#3a5a8f")
            px[x, y] = c
    for (x, y) in [(8, 3), (8, 4), (7, 5), (8, 5), (9, 5), (4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7), (12, 7),
                   (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (7, 9), (8, 9), (9, 9), (6, 10), (7, 10), (9, 10), (10, 10), (5, 11), (6, 11),
                   (10, 11), (11, 11)]:
        px[x, y] = hexc("#f0c45a")
    save(img, "block/starmetal_block.png")

    # Astral bricks.
    def bricks(seed, cracked=False):
        r = random.Random(seed)
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                row = y // 4
                off = 4 if row % 2 else 0
                mortar = y % 4 == 3 or (x + off) % 8 == 7
                if mortar:
                    c = hexc("#141a33")
                else:
                    c = lerp(hexc("#34407a"), hexc("#222b57"), r.random() * 0.8)
                    if r.random() < 0.03:
                        c = hexc("#f0c45a")
                px[x, y] = c
        if cracked:
            x, y = 3, 0
            for s in range(18):
                px[x % 16, y % 16] = hexc("#0a0d1c")
                x += r.choice((0, 1))
                y += 1
        return img
    save(bricks(3), "block/astral_bricks.png")
    save(bricks(3, cracked=True), "block/cracked_astral_bricks.png")

    # Chiseled astral bricks: framed tile with an 8-point star.
    img = bricks(5)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = hexc("#141a33")
            elif x in (1, 14) or y in (1, 14):
                px[x, y] = hexc("#4a58a3")
            elif 2 <= x <= 13 and 2 <= y <= 13:
                px[x, y] = hexc("#1c2350")
    for i in range(-5, 6):
        for (x, y) in [(8 + i, 8), (8, 8 + i)]:
            if 0 <= x < 16 and 0 <= y < 16:
                px[x - (1 if i <= 0 and x == 8 + i else 0), y] = hexc("#f0c45a") if abs(i) < 5 else hexc("#a8761f")
    for i in range(-3, 4):
        px[8 + i, 8 + i] = hexc("#a8761f")
        px[8 + i, 8 - i] = hexc("#a8761f")
    px[8, 8] = px[7, 8] = px[8, 7] = px[7, 7] = hexc("#7ff4ff")
    save(img, "block/chiseled_astral_bricks.png")

    # Starglass.
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = hexc("#2a3466", 255)
            else:
                px[x, y] = hexc("#1a2a6a", 110)
    for (x, y) in [(3, 4), (11, 3), (7, 9), (12, 12), (4, 12), (9, 6)]:
        px[x, y] = hexc("#fff4d6", 230)
    px[2, 2] = px[3, 2] = px[2, 3] = hexc("#8fb0ff", 160)
    save(img, "block/starglass.png")

    # Star lantern (animated, 4 frames stacked vertically).
    frames = Image.new("RGBA", (16, 64))
    for f in range(4):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                twinkle = 0.85 + 0.15 * math.sin(f * math.pi / 2 + d)
                c = lerp(hexc("#fffbe6"), hexc("#ffb347"), min(1, d / 8))
                c = tuple(int(min(255, v * twinkle)) for v in c[:3]) + (255,)
                if x in (0, 15) or y in (0, 15) or ((x in (4, 11) or y in (4, 11)) and d > 5.6):
                    c = hexc("#5a3a18")
                px[x, y] = c
        sparkle(px, 8, 8, (255, 255, 255, 255), 2 + f % 2)
        frames.paste(img, (0, f * 16))
    save(frames, "block/star_lantern.png")
    with open(os.path.join(ASSETS, "block/star_lantern.png.mcmeta"), "w") as fh:
        fh.write('{"animation": {"frametime": 6, "interpolate": true}}\n')

    # Astral crystal cluster (cross model).
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for (cx, h, w) in [(4, 9, 2), (8, 14, 3), (12, 8, 2), (6, 6, 1), (10, 11, 2)]:
        for y in range(16 - h, 16):
            for x in range(cx - w, cx + w + 1):
                if 0 <= x < 16:
                    t = (y - (16 - h)) / h
                    taper = abs(x - cx) <= w * min(1.0, (t * 2.2) + 0.2)
                    if taper:
                        px[x, y] = lerp(hexc("#e6fbff"), hexc("#7a3fd6"), t * 0.8 + (0.2 if x > cx else 0))
    save(img, "block/astral_crystal_cluster.png")

    # Celestial altar.
    base = bricks(9)
    side = base.copy()
    spx = side.load()
    for x in range(16):
        for y in (0, 1, 14, 15):
            spx[x, y] = hexc("#e8b84a") if y in (1, 14) else hexc("#a8761f")
    for (x, y) in [(7, 6), (8, 6), (7, 7), (8, 7), (6, 8), (9, 8)]:
        spx[x, y] = hexc("#c58bff")
    save(side, "block/celestial_altar_side.png")
    top = Image.new("RGBA", (16, 16))
    tpx = top.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            c = hexc("#1c2350")
            if 6.0 < d < 7.2:
                c = hexc("#e8b84a")
            elif 3.0 < d < 4.0:
                c = hexc("#c58bff")
            elif d < 1.6:
                c = hexc("#ffffff")
            if x in (0, 15) or y in (0, 15):
                c = hexc("#a8761f")
            tpx[x, y] = c
    for i in range(8):
        a = i * math.pi / 4
        tpx[int(8 + math.cos(a) * 5.2), int(8 + math.sin(a) * 5.2)] = hexc("#7ff4ff")
    save(top, "block/celestial_altar_top.png")
    save(bricks(9), "block/celestial_altar_bottom.png")

    # Rune trap tiles.
    def rune(color, active, glyph):
        img = bricks(13)
        px = img.load()
        for y in range(1, 15):
            for x in range(1, 15):
                px[x, y] = lerp(px[x, y], hexc("#1c2350"), 0.6)
        col = hexc(color)
        dim = lerp(col, hexc("#1c2350"), 0.55)
        for (x, y) in glyph:
            px[x, y] = col if active else dim
        return img
    gravity_glyph = [(8, y) for y in range(3, 13)] + [(7, 4), (9, 4), (6, 5), (10, 5), (5, 6), (11, 6)] + \
                    [(5, 12), (6, 12), (7, 12), (9, 12), (10, 12), (11, 12)]
    fire_glyph = [(8, 3), (7, 4), (9, 4), (6, 6), (10, 6), (6, 7), (10, 7), (7, 9), (9, 9), (8, 8), (8, 10), (5, 12), (6, 11), (10, 11),
                  (11, 12), (7, 12), (8, 12), (9, 12), (4, 5), (12, 5)]
    save(rune("#7ff4ff", False, gravity_glyph), "block/gravity_rune.png")
    save(rune("#c8fbff", True, gravity_glyph), "block/gravity_rune_active.png")
    save(rune("#ff9a3a", False, fire_glyph), "block/starfire_rune.png")
    save(rune("#ffd27a", True, fire_glyph), "block/starfire_rune_active.png")

    # Vault seal: woven, shifting starlight (animated).
    frames = Image.new("RGBA", (16, 64))
    for f in range(4):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                v = (math.sin((x + f * 2) * 0.8) + math.sin((y - f * 2) * 0.8) + math.sin((x + y + f * 3) * 0.5)) / 3
                c = lerp(hexc("#3a0f6a"), hexc("#ff7af0"), (v + 1) / 2)
                if (x + y + f) % 8 == 0:
                    c = hexc("#fff4ff")
                if x in (0, 15) or y in (0, 15):
                    c = hexc("#e8b84a")
                px[x, y] = c
        frames.paste(img, (0, f * 16))
    save(frames, "block/vault_seal.png")
    with open(os.path.join(ASSETS, "block/vault_seal.png.mcmeta"), "w") as fh:
        fh.write('{"animation": {"frametime": 4, "interpolate": true}}\n')


# ----------------------------------------------------------------------------------------------------------------
# Particles
# ----------------------------------------------------------------------------------------------------------------

def draw_particles():
    def star(size, arm):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        for y in range(8):
            for x in range(8):
                dx, dy = abs(x - 3.5), abs(y - 3.5)
                v = max(0.0, 1.0 - (dx * dy) / (0.6 * arm) - (dx + dy) / (size * 2.0))
                if v > 0.05:
                    px[x, y] = (255, 255, 255, int(255 * min(1.0, v * 1.4)))
        return img

    def blob(r, soft):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        for y in range(8):
            for x in range(8):
                d = math.hypot(x - 3.5, y - 3.5) / r
                if d < 1:
                    px[x, y] = (255, 255, 255, int(255 * (1 - d ** soft)))
        return img

    def diamond(r):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        for y in range(8):
            for x in range(8):
                d = (abs(x - 3.5) + abs(y - 3.5)) / r
                if d < 1:
                    px[x, y] = (255, 255, 255, int(255 * (1 - d * 0.6)))
        return img

    for i, (s, a) in enumerate([(4.0, 1.4), (3.3, 1.1), (2.6, 0.8), (1.8, 0.5)]):
        save(star(s, a), f"particle/star_{i}.png")
        save(diamond(4.2 - i * 0.8), f"particle/glint_{i}.png")
        save(blob(4.0 - i * 0.6, 1.6), f"particle/mote_{i}.png")
        save(blob(3.4 - i * 0.6, 3.0), f"particle/ember_{i}.png")
    flare = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = flare.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            if d < 1:
                px[x, y] = (255, 255, 255, int(255 * (1 - d) ** 1.6))
    save(flare, "particle/flare_0.png")
    ring = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = ring.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            if d < 1:
                px[x, y] = (255, 255, 255, int(255 * max(0, 1 - abs(d - 0.7) * 4)))
    save(ring, "particle/flare_1.png")


# ----------------------------------------------------------------------------------------------------------------
# Effect textures for entity renderers
# ----------------------------------------------------------------------------------------------------------------

def draw_effects():
    # Beam: bright core fading to transparent edges, with flowing streaks.
    img = Image.new("RGBA", (16, 64), (0, 0, 0, 0))
    px = img.load()
    for y in range(64):
        for x in range(16):
            d = abs(x - 7.5) / 7.5
            streak = 0.75 + 0.25 * math.sin(y * 0.6 + x * 0.9)
            a = max(0.0, 1.0 - d ** 1.5) * streak
            px[x, y] = (255, 255, 255, int(255 * a))
    save(img, "entity/eclipse_beam.png")

    save(radial(32, (16, 16), 16, (255, 220, 160, 220), (255, 120, 40, 0)), "entity/meteor_glow.png")

    core = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = core.load()
    for y in range(64):
        for x in range(64):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32
            if d < 0.45:
                px[x, y] = (0, 0, 0, 255)
            elif d < 1.0:
                t = (d - 0.45) / 0.55
                c = lerp((255, 240, 255), (140, 60, 255), min(1, t * 1.4))
                px[x, y] = c[:3] + (int(255 * (1 - t) ** 1.3),)
    save(core, "entity/singularity_core.png")

    disk = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = disk.load()
    rng = random.Random(5)
    for y in range(64):
        for x in range(64):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32
            ang = math.atan2(y - 32, x - 32)
            if 0.35 < d < 1.0:
                band = 0.6 + 0.4 * math.sin(ang * 6 + d * 18)
                t = (d - 0.35) / 0.65
                c = lerp((255, 220, 150), (160, 70, 255), t)
                a = (1 - abs(t - 0.35) * 1.4) * band
                if rng.random() < 0.02:
                    c = (255, 255, 255)
                px[x, y] = c[:3] + (int(255 * max(0, min(1, a))),)
    save(disk, "entity/singularity_disk.png")

    wave = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = wave.load()
    for y in range(64):
        for x in range(64):
            d1 = math.hypot(x + 0.5 - 32, y + 0.5 - 40) / 30
            d2 = math.hypot(x + 0.5 - 32, y + 0.5 - 50) / 30
            if d1 < 1.0 and d2 > 0.95 and y < 48:
                edge = min(1.0, (d2 - 0.95) * 6)
                c = lerp((255, 245, 255), (150, 60, 255), min(1, (1 - d1) * 2))
                px[x, y] = c[:3] + (int(255 * edge * min(1, (1 - d1) * 5)),)
    save(wave, "entity/eclipse_wave.png")


# ----------------------------------------------------------------------------------------------------------------
# Armor / equipment textures (humanoid 64x32 layout)
# ----------------------------------------------------------------------------------------------------------------

def box_faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "west": (u, v + d, d, h),
            "north": (u + d, v + d, w, h), "east": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}


def fill_box(px, u, v, w, h, d, paint):
    for face, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
        for yy in range(fh):
            for xx in range(fw):
                c = paint(face, xx, yy, fw, fh)
                if c:
                    px[fx + xx, fy + yy] = c


def armor_paint(main, dark, light, accent, rng):
    def paint(face, x, y, w, h):
        if x == 0 or y == 0 or x == w - 1 or y == h - 1:
            return dark
        c = lerp(light, main, y / max(1, h - 1) * 0.8 + rng.random() * 0.15)
        if face == "north" and (x + y) % 7 == 0:
            return accent
        return c
    return paint


def draw_armor():
    rng = random.Random(21)
    main, dark, light, glow = hexc("#9fc6e8"), hexc("#26355e"), hexc("#eaf6ff"), hexc("#7ff4ff")

    # Starmetal: helmet, chestplate, boots in "humanoid"; leggings in "humanoid_leggings".
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    fill_box(px, 0, 0, 8, 8, 8, armor_paint(main, dark, light, glow, rng))
    for x in range(9, 15):
        px[x, 11] = glow
    fill_box(px, 16, 16, 8, 12, 4, armor_paint(main, dark, light, glow, rng))
    px[19, 23] = px[20, 23] = px[19, 22] = px[20, 24] = glow
    fill_box(px, 40, 16, 4, 12, 4, armor_paint(main, dark, light, glow, rng))
    fill_box(px, 0, 16, 4, 12, 4, lambda f, x, y, w, h: armor_paint(main, dark, light, glow, rng)(f, x, y, w, h) if y >= h - 5 or f in ("top", "bottom") else None)
    save(img, "entity/equipment/humanoid/starmetal.png")

    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    fill_box(px, 16, 16, 8, 12, 4, lambda f, x, y, w, h: armor_paint(main, dark, light, glow, rng)(f, x, y, w, h) if y >= h - 5 else None)
    fill_box(px, 0, 16, 4, 12, 4, lambda f, x, y, w, h: armor_paint(main, dark, light, glow, rng)(f, x, y, w, h) if y < h - 3 else None)
    save(img, "entity/equipment/humanoid_leggings/starmetal.png")

    # Comet boots.
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    fire = armor_paint(hexc("#ffb347"), hexc("#5a2a10"), hexc("#fff2c2"), hexc("#7ff4ff"), rng)
    fill_box(px, 0, 16, 4, 12, 4, lambda f, x, y, w, h: fire(f, x, y, w, h) if y >= h - 5 or f in ("bottom",) else None)
    save(img, "entity/equipment/humanoid/comet.png")

    # Nebula cloak chest piece.
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()

    def nebula(face, x, y, w, h):
        v = (math.sin(x * 0.7 + y * 0.3) + math.sin(y * 0.5) + 2) / 4
        c = lerp(hexc("#1b1552"), hexc("#7a3fbf"), v)
        if rng.random() < 0.06:
            c = hexc("#fff4b0")
        if x == 0 or y == 0 or x == w - 1 or y == h - 1:
            c = hexc("#0d0a2a")
        return c
    fill_box(px, 16, 16, 8, 12, 4, nebula)
    fill_box(px, 40, 16, 4, 12, 4, nebula)
    save(img, "entity/equipment/humanoid/nebula.png")

    # Nebula wings (elytra layout: wing box at (22,0) size 10x20x2).
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()

    def wing(face, x, y, w, h):
        v = (math.sin(x * 0.5 + y * 0.25) + math.sin(y * 0.35 - x * 0.2) + 2) / 4
        c = lerp(hexc("#120f3a"), hexc("#8a4fd6"), v)
        if rng.random() < 0.07:
            c = hexc("#fff4d6")
        if y > h - 3:
            c = lerp(c, hexc("#ff9af0"), 0.5)
        return c
    fill_box(px, 22, 0, 10, 20, 2, wing)
    save(img, "entity/equipment/wings/nebula.png")

    # Eclipse crown (helmet).
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    gold, gdark, glight, gem = hexc("#e8b84a"), hexc("#7a5212"), hexc("#fff0b8"), hexc("#ff5ae0")

    def crown(face, x, y, w, h):
        if face in ("north", "south", "east", "west"):
            if y < 3:
                return gold if (x % 3 == 1) else None
            if y < 6:
                c = lerp(glight, gold, (y - 3) / 3)
                if face == "north" and x in (3, 4) and y == 4:
                    return gem
                return c
            return None
        if face == "top":
            return None
        return None
    fill_box(px, 32, 0, 8, 8, 8, crown)
    save(img, "entity/equipment/humanoid/eclipse.png")


# ----------------------------------------------------------------------------------------------------------------
# Logo
# ----------------------------------------------------------------------------------------------------------------

def draw_logo():
    w, h = 400, 200
    img = Image.new("RGBA", (w, h))
    px = img.load()
    rng = random.Random(99)
    for y in range(h):
        for x in range(w):
            t = y / h
            px[x, y] = lerp(hexc("#0b0820"), hexc("#2a1550"), t)
    for _ in range(220):
        x, y = rng.randint(0, w - 1), rng.randint(0, h - 1)
        b = rng.randint(140, 255)
        px[x, y] = (b, b, min(255, b + 20), 255)
    d = ImageDraw.Draw(img)
    # Meteor streak.
    for i in range(60):
        t = i / 60
        x = int(60 + t * 120)
        y = int(20 + t * 70)
        r = int(2 + t * 8)
        col = lerp((255, 240, 200, 255), (255, 120, 40, 255), 1 - t)
        d.ellipse((x - r, y - r, x + r, y + r), fill=col)
    try:
        font = ImageFont.load_default(size=56)
        small = ImageFont.load_default(size=18)
    except TypeError:
        font = small = ImageFont.load_default()
    d.text((w // 2 + 2, 112 + 2), "STARFORGED", font=font, fill=(40, 10, 60, 255), anchor="mm")
    d.text((w // 2, 112), "STARFORGED", font=font, fill=(255, 214, 120, 255), anchor="mm")
    d.text((w // 2, 160), "when the stars fall, legends are forged", font=small, fill=(200, 180, 255, 255), anchor="mm")
    out = os.path.join(ROOT, "src/main/resources/starforged_logo.png")
    img.save(out)
    icon = img.crop((100, 0, 300, 200)).resize((64, 64))
    icon.save(os.path.join(ROOT, "src/main/resources/assets/starforged/icon.png"))


if __name__ == "__main__":
    draw_items()
    draw_blocks()
    draw_particles()
    draw_effects()
    draw_armor()
    draw_logo()
    print("textures written")
