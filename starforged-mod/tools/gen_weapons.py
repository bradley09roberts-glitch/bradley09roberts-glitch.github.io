#!/usr/bin/env python3
"""
Starforged weapon & tool sprites (v2). Run after gen_textures.py - it overwrites the weapon/tool item textures.

The basic Starmetal tools follow the exact vanilla tool silhouettes (diamond tier), recoloured to starmetal.
The legendary weapons are drawn here pixel by pixel / with small shape helpers, then outlined.
Needs the vanilla item textures from the client jar: pass the folder with --vanilla or set VANILLA_ITEMS.
"""
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEMS = os.path.join(ROOT, "src/main/resources/assets/starforged/textures/item")
VANILLA = os.environ.get("VANILLA_ITEMS", "")
if "--vanilla" in sys.argv:
    VANILLA = sys.argv[sys.argv.index("--vanilla") + 1]


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def ramp(colors, t):
    """Sample a list of hex colours at t in [0, 1]."""
    t = max(0.0, min(1.0, t)) * (len(colors) - 1)
    i = min(int(t), len(colors) - 2)
    return lerp(hexc(colors[i]), hexc(colors[i + 1]), t - i)


def new():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return img, img.load()


def save(img, name):
    img.save(os.path.join(ITEMS, name + ".png"))


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def outline(img, color="#100c22"):
    """Adds a 1px outline around every opaque pixel (4-neighbourhood), like vanilla item art."""
    px = img.load()
    out = img.copy()
    opx = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny][3] and px[nx, ny] != hexc(color):
                    opx[x, y] = hexc(color)
                    break
    return out


def grid(rows, pal):
    img, px = new()
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row, len(row))
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = hexc(pal[ch])
    return img


def line(px, a, b, color):
    steps = max(abs(b[0] - a[0]), abs(b[1] - a[1]), 1)
    for s in range(steps + 1):
        x = round(a[0] + (b[0] - a[0]) * s / steps)
        y = round(a[1] + (b[1] - a[1]) * s / steps)
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = hexc(color) if isinstance(color, str) else color


# Shared palette ------------------------------------------------------------------------------------------------
STAR_RAMP = ["#0b1026", "#18224a", "#26386e", "#34508f", "#4a6fb0", "#6f9ad6", "#a9d2f0", "#f2fbff"]
GRIP = ["#1f1233", "#3a2459", "#5b3a86", "#7c58ad"]
GOLD = ["#5e3f0e", "#a8761f", "#f0c45a", "#fff0b0"]
GLOW = "#7ff4ff"


# ----------------------------------------------------------------------------------------------------------------
# Starmetal tools: vanilla diamond silhouettes, recoloured.
# ----------------------------------------------------------------------------------------------------------------

def is_wood(c):
    r, g, b = c[:3]
    return r > b + 8 and r >= g and g > b  # the vanilla stick browns


def recolor_tool(src, name, sparkles):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    head = sorted({px[x, y] for y in range(16) for x in range(16) if px[x, y][3] and not is_wood(px[x, y])}, key=lum)
    tool_ramp = ["#0f1530", "#2a3d78", "#3d5d9a", "#4f78b8", "#6590cc", "#86b0e2", "#b4dcf5", "#f2fbff"]
    mapping = {c: hexc(tool_ramp[round(i * (len(tool_ramp) - 1) / max(1, len(head) - 1))]) for i, c in enumerate(head)}
    for y in range(16):
        for x in range(16):
            c = px[x, y]
            if c[3] and c in mapping:
                px[x, y] = mapping[c]
    for (x, y) in sparkles:
        px[x, y] = hexc(GLOW)
    save(im, name)


def starmetal_tools():
    v = lambda n: os.path.join(VANILLA, n + ".png")
    recolor_tool(v("diamond_sword"), "starmetal_sword", [(12, 2), (8, 6)])
    recolor_tool(v("diamond_pickaxe"), "starmetal_pickaxe", [(8, 3)])
    recolor_tool(v("diamond_axe"), "starmetal_axe", [])
    recolor_tool(v("diamond_shovel"), "starmetal_shovel", [])
    recolor_tool(v("diamond_hoe"), "starmetal_hoe", [])


# ----------------------------------------------------------------------------------------------------------------
# Legendary weapons
# ----------------------------------------------------------------------------------------------------------------

def handle(px, a, b, light, dark):
    """A 2px diagonal haft from a (bottom-left) to b (top-right): light on top, dark below."""
    line(px, a, b, light)
    line(px, (a[0] + 1, a[1]), (b[0] + 1, b[1]), dark)


def meteor_hammer():
    """A proper war hammer: a big squared meteorite head with molten cracks and banded starmetal caps."""
    img, px = new()
    handle(px, (1, 14), (8, 7), GRIP[2], GRIP[0])
    px[1, 14] = hexc(GOLD[2])
    px[2, 14] = hexc(GOLD[1])
    px[4, 11] = hexc(GOLD[2])
    px[5, 11] = hexc(GOLD[1])
    cx, cy = 10.0, 5.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - (cx + 0.5), y + 0.5 - (cy + 0.5)
            along = (dx + dy) / math.sqrt(2)  # long axis of the head (perpendicular to the haft)
            across = (dx - dy) / math.sqrt(2)  # + is the striking face side (up-right)
            if abs(along) <= 5.4 and abs(across) <= 3.0:
                top = 1 - (across + 3.0) / 6.0  # 1 at the back, 0 at the face
                if abs(along) > 4.2:
                    c = ramp(STAR_RAMP[3:], 0.35 + (1 - top) * 0.65)  # starmetal caps
                elif abs(along) > 3.4:
                    c = ramp(GOLD, 0.45 + (1 - top) * 0.5)  # gold bands
                else:
                    c = ramp(["#2a2230", "#4a4152", "#6e6379", "#9a8ea6"], 0.25 + (1 - top) * 0.7)
                px[x, y] = c
    # Molten cracks running across the rock.
    for (x, y), c in {(8, 4): "#ff7a2a", (9, 5): "#ffd27a", (10, 5): "#ff7a2a", (10, 6): "#ffb347", (11, 7): "#ff7a2a",
                      (9, 3): "#ffb347", (11, 4): "#ff7a2a", (12, 5): "#ffd27a", (8, 6): "#ff5a1a"}.items():
        px[x, y] = hexc(c)
    save(outline(img), "meteor_hammer")


def void_scythe():
    """A broad, sweeping void blade with a bright cutting edge, a gem at the collar and a wrapped haft."""
    img, px = new()
    handle(px, (5, 15), (11, 3), GRIP[2], GRIP[0])
    for y in (8, 11):
        x = 5 + (15 - y) * 6 // 12
        px[x, y] = hexc(GRIP[3])
    px[5, 15] = hexc(GLOW)
    cx, cy = 11.5, 8.5
    for y in range(16):
        for x in range(16):
            r = math.hypot((x + 0.5 - cx) / 10.0, (y + 0.5 - cy) / 8.0)
            inner = math.hypot((x + 0.5 - cx - 0.4) / 9.2, (y + 0.5 - cy - 1.4) / 5.6)
            if r <= 1.0 and inner > 1.0 and y <= 7 and x <= 12:
                edge = 1.0 - min(1.0, (inner - 1.0) / 0.35)  # 1 right at the cutting edge (inner curve)
                spine = 1.0 - min(1.0, (1.0 - r) / 0.12)  # 1 along the back of the blade
                c = ramp(["#1a0838", "#3b1670", "#6a2fc4", "#9a5cf0"], 0.35 + 0.4 * (1 - spine))
                if edge > 0.55:
                    c = ramp(["#b47cff", "#f3e2ff"], edge)
                px[x, y] = c
    px[11, 2] = hexc("#ff5ae0")
    px[12, 3] = hexc("#b45cff")
    save(outline(img, "#0c0618"), "void_scythe")


BOW_TIPS = ((14.0, 2.0), (2.0, 14.0))


def bow_points():
    """Points along the bow arc (quadratic Bezier bulging to the top-left) with the outward normal."""
    (x0, y0), (x2, y2) = BOW_TIPS
    x1, y1 = 2.6, 2.6
    pts = []
    for i in range(121):
        t = i / 120
        x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * x1 + t * t * x2
        y = (1 - t) ** 2 * y0 + 2 * (1 - t) * t * y1 + t * t * y2
        tx = 2 * (1 - t) * (x1 - x0) + 2 * t * (x2 - x1)
        ty = 2 * (1 - t) * (y1 - y0) + 2 * t * (y2 - y1)
        n = math.hypot(tx, ty)
        nx, ny = ty / n, -tx / n  # outward (top-left) normal
        if nx > 0:
            nx, ny = -nx, -ny
        pts.append((t, x, y, nx, ny))
    return pts


def draw_bow(pull):
    """Constellation Bow: thick starmetal recurve limbs, gold tips and fins, a star gem grip, starlight string."""
    img, px = new()
    for (t, x, y, nx, ny) in bow_points():
        width = 1.0 + 1.6 * math.sin(t * math.pi)  # thicker towards the grip
        tip = t < 0.1 or t > 0.9
        for k in range(0, int(width * 4) + 1):
            o = k / 4
            X, Y = int(x + nx * o), int(y + ny * o)
            if 0 <= X < 16 and 0 <= Y < 16:
                if tip:
                    c = ramp(GOLD, 0.9 - o / 3)
                else:
                    c = ramp(STAR_RAMP[2:], 0.95 - o / max(width, 1) * 0.75)
                px[X, Y] = c
    # Fins on the back of each limb.
    for (t, x, y, nx, ny) in bow_points():
        if abs(t - 0.28) < 0.004 or abs(t - 0.72) < 0.004:
            X, Y = int(x + nx * 3.2), int(y + ny * 3.2)
            px[X, Y] = hexc(GOLD[2])
    px[15, 1] = hexc(GOLD[3])
    px[1, 15] = hexc(GOLD[3])
    # Star gem in the grip.
    for (x, y), c in {(4, 4): "#ffffff", (5, 4): GLOW, (4, 5): GLOW, (5, 5): "#2a8fb0"}.items():
        px[x, y] = hexc(c)
    img = outline(img)
    px = img.load()
    # String: straight between the tips, or pulled back to a point with a starbolt nocked (drawn after the outline).
    a, b = (13, 3), (3, 13)
    if pull == 0:
        line(px, a, b, "#f5e6a8")
    else:
        back = {1: (10, 10), 2: (11, 11), 3: (12, 12)}[pull]
        line(px, a, back, "#f5e6a8")
        line(px, back, b, "#f5e6a8")
        # Starbolt arrow from the string towards the grip.
        tipd = {1: 6, 2: 6, 3: 6}[pull]
        line(px, back, (tipd, tipd), "#6f9ad6")
        px[tipd, tipd] = hexc("#ffffff")
        px[tipd + 1, tipd + 1] = hexc(GLOW)
        px[back[0], back[1]] = hexc(GLOW)
    return img


def constellation_bow():
    save(draw_bow(0), "constellation_bow")
    for i in range(3):
        save(draw_bow(i + 1), f"constellation_bow_pulling_{i}")


def eclipse_blade():
    """A straight black-core sword with glowing violet edges and an eclipse orb in the crossguard."""
    img, px = new()
    edge = ["#2a0f4a", "#6a2fc4", "#c58bff", "#ffe9ff"]
    # Blade along the diagonal x + y = 15, tip at the top right.
    for y in range(0, 10):
        for x in range(16):
            d = x + y - 15
            if y == 0 and x != 15:
                continue
            if 6 <= x <= 15 and -1 <= d <= 1:
                if d == -1:
                    c = hexc(edge[3]) if y < 4 else hexc(edge[2])
                elif d == 0:
                    c = hexc("#120820") if (x + y * 3) % 7 else hexc("#ff9ef5")  # black core with star flecks
                else:
                    c = hexc(edge[1])
                px[x, y] = c
    px[15, 0] = hexc("#ffffff")
    # Crossguard: gold bar perpendicular to the blade with an eclipse orb in the middle.
    for (x, y) in ((3, 7), (4, 8), (5, 9), (8, 12), (9, 13), (3, 8), (8, 13)):
        px[x, y] = hexc(GOLD[2])
    for (x, y) in ((4, 9), (9, 12)):
        px[x, y] = hexc(GOLD[1])
    for (x, y) in ((5, 10), (6, 10), (6, 11), (7, 11), (5, 11), (7, 12), (6, 12)):
        px[x, y] = hexc("#0a0414")
    px[6, 9] = hexc("#ff5ae0")
    px[8, 11] = hexc("#ff5ae0")
    px[7, 10] = hexc("#ffb3f3")
    # Grip and pommel.
    for (x, y) in ((4, 12), (3, 13)):
        px[x, y] = hexc(GRIP[2])
    for (x, y) in ((5, 12), (4, 13)):
        px[x, y] = hexc(GRIP[0])
    px[2, 14] = hexc("#ff5ae0")
    px[1, 15] = hexc(GOLD[2])
    save(outline(img, "#0a0414"), "eclipse_blade")


def starcaller_staff():
    """Haft with gold bands, crowned by a gold crescent cradling a blazing star crystal."""
    img, px = new()
    handle(px, (1, 14), (8, 7), GRIP[2], GRIP[0])
    for (x, y) in ((3, 12), (6, 9)):
        px[x, y] = hexc(GOLD[2])
        px[x + 1, y] = hexc(GOLD[1])
    px[1, 14] = hexc(GLOW)
    cx, cy = 11.0, 4.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - (cx + 0.5), y + 0.5 - (cy + 0.5)
            r = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(-dy, dx)) % 360  # 0 = right, 90 = up
            if 3.2 <= r <= 4.4 and not (10 <= ang <= 80):
                px[x, y] = ramp(GOLD[1:], 0.3 + (1 - (dy + 4.4) / 8.8) * 0.7)
    star = {(11, 4): "#ffffff", (11, 3): "#e8fdff", (11, 5): "#e8fdff", (10, 4): "#e8fdff", (12, 4): "#e8fdff",
            (11, 2): GLOW, (11, 6): GLOW, (9, 4): GLOW, (13, 4): GLOW, (10, 3): "#4fd8f0", (12, 5): "#4fd8f0",
            (12, 3): "#4fd8f0", (10, 5): "#4fd8f0"}
    for (x, y), c in star.items():
        px[x, y] = hexc(c)
    save(outline(img), "starcaller_staff")


def gravity_gauntlet():
    rows = [
        "................",
        "...a.a.a.a......",
        "..#a#a#a#a#.....",
        "..#b#b#b#b#.....",
        "..#b#b#b#b#.##..",
        "..#cbbcbbbc#ab#.",
        "..#bbbbbbbbbbc#.",
        "..#bbcgggcbbc#..",
        "..#bcgwwgcbc#...",
        "..#cbcgggcbc#...",
        "...#cbbcbbc#....",
        "...#yYyyYyy#....",
        "...#hHhhHhh#....",
        "...#hhHhhHh#....",
        "....#######.....",
        "................",
    ]
    pal = {"#": "#141a33", "a": "#f2fbff", "b": "#a9d2f0", "c": "#5c87bf", "g": "#b47cff", "w": "#ffffff",
           "y": GOLD[2], "Y": GOLD[1], "h": GRIP[2], "H": GRIP[1]}
    save(outline(grid(rows, pal), "#141a33"), "gravity_gauntlet")


def singularity_grenade():
    """A starmetal-caged glass orb with a swirling black hole inside."""
    img, px = new()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 9
            r = math.hypot(dx, dy)
            if r <= 5.6:
                ang = math.atan2(dy, dx) + r * 0.9
                swirl = 0.5 + 0.5 * math.sin(ang * 2)
                if r < 1.8:
                    c = hexc("#05020a")
                elif r < 4.6:
                    c = lerp(hexc("#1a0838"), hexc("#b47cff"), swirl * (r - 1.8) / 2.8)
                else:
                    c = hexc("#cbb8ff") if dx < 0 and dy < 0 else hexc("#5a3aa0")
                px[x, y] = c
    # Starmetal cap and pin.
    for x in range(6, 10):
        px[x, 3] = hexc(STAR_RAMP[6] if x < 8 else STAR_RAMP[4])
    px[7, 2] = hexc(STAR_RAMP[5])
    px[8, 2] = hexc(STAR_RAMP[3])
    px[10, 1] = hexc(GOLD[2])
    px[9, 2] = hexc(GOLD[1])
    px[6, 6] = hexc("#ffffff")
    save(outline(img, "#0c0618"), "singularity_grenade")


def astral_compass():
    """Gold-rimmed starry dial with a glowing needle at 32 angles (frames astral_compass_00..31)."""
    rng_stars = [(5, 6), (10, 5), (11, 10), (5, 11), (8, 4), (3, 8)]
    for i in range(32):
        img, px = new()
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - 8, y + 0.5 - 8
                r = math.hypot(dx, dy)
                if r <= 7.2:
                    if r > 5.9:
                        c = ramp(GOLD[1:], 0.25 + (1 - (dy + 7) / 14) * 0.75)
                    else:
                        c = lerp(hexc("#2a1f5e"), hexc("#0c0a26"), r / 6.0)
                    px[x, y] = c
        for (x, y) in rng_stars:
            px[x, y] = hexc("#cfe9ff")
        # Needle: angle i/32 of a full turn, 0 = pointing up (north on the dial).
        a = i / 32.0 * 2 * math.pi
        tipx, tipy = 8 + math.sin(a) * 4.6, 8 - math.cos(a) * 4.6
        tailx, taily = 8 - math.sin(a) * 3.0, 8 + math.cos(a) * 3.0
        line(px, (int(tailx), int(taily)), (int(8), int(8)), "#6f6a8f")
        line(px, (int(8), int(8)), (int(tipx), int(tipy)), "#ff5ae0")
        px[int(tipx), int(tipy)] = hexc(GLOW)
        px[8, 8] = hexc("#ffffff")
        save(outline(img, "#141a33"), f"astral_compass_{i:02d}")


def main():
    if not VANILLA or not os.path.isdir(VANILLA):
        sys.exit("Pass the vanilla item texture folder: gen_weapons.py --vanilla <.../assets/minecraft/textures/item>")
    starmetal_tools()
    meteor_hammer()
    void_scythe()
    constellation_bow()
    eclipse_blade()
    starcaller_staff()
    gravity_gauntlet()
    singularity_grenade()
    astral_compass()
    print("weapon sprites written")


if __name__ == "__main__":
    main()
