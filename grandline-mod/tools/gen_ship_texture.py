#!/usr/bin/env python3
"""Ship texture atlas (64x64, 4x4 tiles of 16px) used by ShipRenderer/ShipMesh tile indices."""
import os, random, sys
sys.path.insert(0, os.path.dirname(__file__))
from pngw import write_png
random.seed(7)
W = H = 64
img = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]

def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        img[y][x] = c

def tile(t, fn):
    tx, ty = (t % 4) * 16, (t // 4) * 16
    for y in range(16):
        for x in range(16):
            put(tx + x, ty + y, fn(x, y))

def clamp(v): return max(0, min(255, int(v)))
def shade(c, f): return (clamp(c[0]*f), clamp(c[1]*f), clamp(c[2]*f), 255)

# 0 hull: light grey wood (tinted by hull colour) - horizontal strakes
def hull(x, y):
    base = 215 if (y // 4) % 2 == 0 else 200
    if y % 4 == 3: base = 150
    if (x + (y // 4) * 5) % 16 == 0: base = 160
    n = random.randint(-8, 8)
    return (clamp(base + n), clamp(base + n), clamp(base + n), 255)
tile(0, hull)
# 1 deck: natural oak planks along the length
def deck(x, y):
    c = (176, 132, 82) if (x // 4) % 2 == 0 else (162, 120, 74)
    if x % 4 == 3: c = (110, 80, 48)
    if (y + (x // 4) * 7) % 16 == 0: c = (120, 88, 52)
    return shade(c, 1 + random.uniform(-0.05, 0.05))
tile(1, deck)
# 2 sail: canvas (tinted)
def sail(x, y):
    v = 240 - (10 if (x % 8 == 0 or y % 8 == 0) else 0) + random.randint(-6, 6)
    return (clamp(v), clamp(v), clamp(v), 255)
tile(2, sail)
# 3 iron
def iron(x, y):
    v = 50 + random.randint(-6, 6) + (25 if (x == 1 or y == 1) else 0)
    return (v, v, clamp(v + 6), 255)
tile(3, iron)
# 4 rail / mast dark wood
def rail(x, y):
    c = (96, 66, 38) if x % 3 else (80, 54, 30)
    return shade(c, 1 + random.uniform(-0.06, 0.06))
tile(4, rail)
# 5 cabin wall with window
def window(x, y):
    if 4 <= x <= 11 and 3 <= y <= 10:
        if x in (4, 11) or y in (3, 10) or x == 7 or y == 6: return (60, 40, 20, 255)
        return (150, 200, 230, 255) if (x + y) % 5 else (230, 245, 255, 255)
    return deck(x, y)
tile(5, window)
# 6 jolly roger: black flag, white skull and crossbones
def roger(x, y):
    c = (20, 20, 20, 255)
    cx, cy = 8, 6
    if (x - cx) ** 2 + (y - cy) ** 2 <= 10: c = (235, 235, 235, 255)
    if y in (5, 6) and x in (6, 10): c = (20, 20, 20, 255)
    if 7 <= y <= 9 and 6 <= x <= 10 and y == 9 and x % 2 == 0: c = (20, 20, 20, 255)
    if (x - y == -3 or x + y == 19) and 10 <= y <= 14: c = (235, 235, 235, 255)
    return c
tile(6, roger)
# 7 gold trim
def gold(x, y):
    v = 1 + random.uniform(-0.1, 0.1) + (0.25 if (x + y) % 6 == 0 else 0)
    return shade((210, 170, 60), v)
tile(7, gold)
# 8 rope
def rope(x, y):
    return shade((170, 140, 90), 0.8 + 0.3 * (((x + y) % 4) / 3))
tile(8, rope)
# 9 marine flag: white with blue gull mark
def marine(x, y):
    c = (240, 240, 240, 255)
    if y in (7, 8) and 3 <= x <= 12: c = (40, 90, 190, 255)
    if (abs(x - 8) == y - 3 or abs(x - 8) == y - 2) and 3 <= y <= 8 and 3 <= x <= 13: c = (40, 90, 190, 255)
    if y in (0, 15): c = (40, 90, 190, 255)
    return c
tile(9, marine)
# ---- naval customisation tiles (light greyscale: the renderer tints them per figurehead / flag colour) ----
# 10 paint: flat light carving paint with a faint grain
def paint(x, y):
    v = 236 + random.randint(-5, 5) - (6 if (x * 3 + y) % 11 == 0 else 0)
    return (clamp(v), clamp(v), clamp(v), 255)
tile(10, paint)
# 11 scales: overlapping scale rows
def scales(x, y):
    row = y // 4
    cx = (x + (2 if row % 2 else 0)) % 4
    d = (cx - 1.5) ** 2 + ((y % 4) - 0.5) ** 2
    v = 228 - int(d * 9) + random.randint(-4, 4)
    if y % 4 == 3: v -= 40
    return (clamp(v), clamp(v), clamp(v), 255)
tile(11, scales)
# 12 mane: wavy fur strands
def mane(x, y):
    v = 214 + int(22 * ((x + (y // 3)) % 3 == 0)) - int(30 * ((x * 2 + y) % 7 == 0)) + random.randint(-6, 6)
    return (clamp(v), clamp(v), clamp(v), 255)
tile(12, mane)
# 13 feathers: layered barbs
def feather(x, y):
    v = 246 - (18 if (x + y // 2) % 5 == 0 else 0) - (10 if y % 4 == 0 else 0) + random.randint(-3, 3)
    return (clamp(v), clamp(v), clamp(v), 255)
tile(13, feather)
# 14 bone: ivory with hairline cracks
def bone(x, y):
    v = 238 + random.randint(-6, 4)
    if (x * 7 + y * 3) % 23 == 0 or (x == 11 and 3 < y < 9): v -= 45
    return (clamp(v), clamp(v - 6), clamp(v - 22), 255)
tile(14, bone)
# 15 dark: eye sockets, nostrils, mouths
def dark(x, y):
    v = 22 + random.randint(-4, 4)
    return (v, v, v, 255)
tile(15, dark)
out =os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/grandline/textures/entity/ship')
os.makedirs(out, exist_ok=True)
write_png(os.path.join(out, 'ship.png'), W, H, img)
print('ship.png written')
