"""GUI textures: ledger parchment background (256x256, 248x180 used) and the 128x128 logo."""
from __future__ import annotations

import math
import random

import numpy as np
from PIL import Image

from art_pixel import Canvas, C, mix

# ---------------------------------------------------------------- ledger background
PARCHMENT = [C(c) for c in ('#efe3c2', '#e7d9b4', '#ddcca2', '#cfbb8e', '#bba277', '#9e845c')]
INK = C('#4e3c2a')
INK_SOFT = C('#7a644a')
EDGE = C('#5e4a32')


def _value_noise(rng, w, h, cells_x, cells_y):
    grid = np.array([[rng.random() for _ in range(cells_x)] for _ in range(cells_y)], dtype=np.float32)
    img = Image.fromarray((grid * 255).astype(np.uint8), 'L').resize((w, h), Image.Resampling.BICUBIC)
    return np.asarray(img, dtype=np.float32) / 255.0


def ledger_background():
    W, H = 248, 180
    rng = random.Random(9001)
    noise = 0.65 * _value_noise(rng, W, H, 8, 6) + 0.35 * _value_noise(rng, W, H, 24, 18)
    yy, xx = np.mgrid[0:H, 0:W]
    edge = np.minimum(np.minimum(xx, yy), np.minimum(W - 1 - xx, H - 1 - yy)).astype(np.float32)
    age = noise * 0.75 + np.clip((7 - edge) / 7, 0, 1) * 1.7 + np.clip((3 - edge) / 3, 0, 1) * 0.8
    # quantise into a few flat parchment tones (hand-painted look, no gradients);
    # the interior only ever uses the two lightest tones so dark text stays readable
    thresholds = [0.50, 0.92, 1.30, 1.75, 2.2]
    idx = np.zeros((H, W), dtype=np.int32)
    for t in thresholds:
        idx += (age > t)
    cv = Canvas(256, 256)
    pal = np.array(PARCHMENT, dtype=np.uint8)
    cv.a[:H, :W] = pal[idx]
    # sparse fibres and specks (kept away from the writing area's centre)
    for _ in range(40):
        x, y = rng.randint(3, W - 4), rng.randint(3, H - 4)
        horiz = rng.random() < 0.7
        ln = rng.randint(1, 3)
        col = tuple(int(v) for v in cv.a[y, x])
        dark = mix(col, '#6e5634', 0.08)
        for k in range(ln):
            cv.set(x + (k if horiz else 0), y + (0 if horiz else k), dark)
    # a couple of faint water stains (ring shaped, very low contrast)
    for cx, cy, r in ((206, 146, 9), (30, 34, 6)):
        for y in range(cy - r - 1, cy + r + 2):
            for x in range(cx - r - 1, cx + r + 2):
                d = math.hypot(x - cx, y - cy)
                if r - 0.6 <= d <= r + 0.4 and cv.opaque(x, y):
                    cv.set(x, y, mix(cv.get(x, y), '#8a6e44', 0.10))
    # outer edge + clipped corners
    cv.frame(0, 0, W - 1, H - 1, EDGE)
    for x, y in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)):
        cv.clear(x, y)
    for x, y in ((1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)):
        cv.set(x, y, EDGE)
    # thin double ink frame with corner diamonds
    cv.frame(6, 6, W - 7, H - 7, INK)
    cv.frame(8, 8, W - 9, H - 9, INK_SOFT)
    for cx, cy in ((6, 6), (W - 7, 6), (6, H - 7), (W - 7, H - 7)):
        for dx, dy in ((0, -2), (-1, -1), (1, -1), (-2, 0), (2, 0), (-1, 1), (1, 1), (0, 2), (0, 0)):
            cv.set(cx + dx, cy + dy, INK)
        cv.set(cx, cy, PARCHMENT[2])
    # small header ornament on the top rule
    mid = W // 2
    for dx in range(-7, 8):
        cv.set(mid + dx, 6, PARCHMENT[1] if abs(dx) < 6 else INK)
    for dx, dy in ((0, -2), (-1, -1), (1, -1), (-2, 0), (2, 0), (-1, 1), (1, 1), (0, 2),
                   (-4, 0), (4, 0), (-5, 0), (5, 0)):
        cv.set(mid + dx, 6 + dy, INK)
    return cv


# ---------------------------------------------------------------- logo
SKY = [C(c) for c in ('#080b1c', '#0c1128', '#111936', '#172244', '#1e2c54', '#283864')]
EMB = [C(c) for c in ('#4a160c', '#7e2810', '#b44418', '#e0701e', '#ffa63a', '#ffd88a')]
RIM = [C(c) for c in ('#2a160c', '#5a2e14', '#8a4a1e', '#b8702c', '#e0a050')]
STONE = C('#05060c')
STONE_LIT = C('#141826')


def logo():
    S = 128
    cx, cy, R = 64, 64, 61
    rng = random.Random(4242)
    cv = Canvas(S, S)
    # night sky in flat bands, warming toward the horizon
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= R:
                band = min(5, max(0, (y - 6) // 15))
                col = SKY[band]
                if y > 82:
                    col = mix(SKY[5], EMB[1], min(1.0, (y - 82) / 20) * 0.55)
                    col = mix(col, EMB[1], 0.0)
                cv.set(x, y, col)
    # horizon glow bands (stepped)
    for y, t in ((86, 0.18), (90, 0.3), (94, 0.42), (98, 0.55)):
        for x in range(S):
            if cv.opaque(x, y) and math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= R:
                for yy in range(y, y + 4):
                    if cv.opaque(x, yy):
                        cv.set(x, yy, mix(SKY[4], EMB[2], t))
    # stars
    for _ in range(46):
        x, y = rng.randint(10, 118), rng.randint(8, 72)
        if math.hypot(x - cx, y - 50) < 30:
            continue
        if math.hypot(x + 0.5 - cx, y + 0.5 - cy) < R - 4:
            cv.set(x, y, C('#c8d4f4') if rng.random() < 0.7 else C('#ffe9b0'))
    for x, y in ((28, 24), (100, 30), (92, 14)):
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            cv.set(x + dx, y + dy, C('#e6ecff') if (dx, dy) == (0, 0) else C('#7a8ac0'))
    # the dark sun: black disc, ember corona, a thin bright crescent on its lower-left
    sx, sy, sr = 64, 38, 17
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - sx, y + 0.5 - sy)
            ang = math.atan2(y + 0.5 - sy, x + 0.5 - sx)
            ray = 2.2 + 1.6 * (0.5 + 0.5 * math.cos(ang * 9)) + 1.2 * (0.5 + 0.5 * math.cos(ang * 4 + 1.3))
            if sr < d <= sr + ray:
                f = (d - sr) / ray
                cv.set(x, y, EMB[4] if f < 0.3 else EMB[3] if f < 0.6 else EMB[2])
            elif sr + ray < d <= sr + ray + 2:
                if cv.opaque(x, y):
                    cv.set(x, y, mix(cv.get(x, y), EMB[1], 0.45))
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - sx, y + 0.5 - sy)
            if d <= sr:
                cv.set(x, y, C('#030308'))
                d2 = math.hypot(x + 0.5 - (sx + 3), y + 0.5 - (sy - 3))
                if d2 > sr and d > sr - 2.2:
                    cv.set(x, y, EMB[5] if d > sr - 1.2 else EMB[4])
    # castle silhouette
    castle = Canvas(S, S)
    def block(x0, y0, x1, y1, merlons=True):
        castle.rect(x0, y0, x1, y1, STONE)
        if merlons:
            for x in range(x0, x1 + 1, 4):
                castle.rect(x, y0 - 3, min(x + 1, x1), y0 - 1, STONE)
    block(22, 92, 106, 128)                  # curtain wall
    block(30, 76, 42, 128)                   # left tower
    block(86, 76, 98, 128)                   # right tower
    block(52, 72, 76, 128)                   # keep
    block(59, 63, 69, 74, merlons=False)     # keep spire base
    for i in range(10):                      # spire roof, piercing the corona
        castle.hline(59 + i // 2, 69 - i // 2, 62 - i, STONE)
    castle.vline(92, 64, 74, STONE)          # banner pole on the right tower
    for y, w in ((65, 5), (66, 4), (67, 5), (68, 3)):
        castle.hline(93, 92 + w, y, EMB[2] if y != 65 else EMB[3])
    for x0, x1, y in ((12, 22, 104), (106, 116, 106)):   # low walls / hills
        castle.rect(x0, y, x1, 128, STONE)
    castle.rect(0, 110, 128, 128, STONE)
    # lit windows and gate
    windows = [(35, 84), (36, 84), (91, 84), (92, 84), (59, 80), (60, 80), (67, 80), (68, 80),
               (63, 67), (64, 67), (35, 98), (91, 98), (47, 100), (80, 100)]
    for x, y in windows:
        castle.set(x, y, EMB[4])
        castle.set(x, y + 1, EMB[3])
    for y in range(113, 122):                # gate glow
        w = 3 if y > 114 else 2
        castle.hline(64 - w, 63 + w, y, EMB[1] if y > 117 else EMB[2])
    # rim light on the castle edges facing the sun
    for y in range(S):
        for x in range(S):
            if castle.get(x, y) == STONE and (y == 0 or castle.get(x, y - 1)[3] == 0):
                castle.set(x, y, STONE_LIT)
    for y in range(S):
        for x in range(S):
            if castle.opaque(x, y) and math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= R:
                cv.set(x, y, castle.get(x, y))
    # embers drifting up from the town
    for x, y in ((44, 86), (79, 82), (50, 74), (84, 70), (40, 66), (74, 94)):
        cv.set(x, y, EMB[4])
    # bronze rim
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if R - 3 < d <= R + 2:
                lit = (x - cx) + (y - cy) < 0
                ring = d - (R - 3)
                if ring < 1:
                    col = RIM[0]
                elif ring < 2.5:
                    col = RIM[3] if lit else RIM[2]
                elif ring < 4:
                    col = RIM[4] if lit else RIM[3]
                else:
                    col = RIM[1]
                cv.set(x, y, col)
    for k in range(8):                       # rivets on the rim
        a = k * math.pi / 4 + math.pi / 8
        x, y = int(cx + math.cos(a) * (R - 0.5)), int(cy + math.sin(a) * (R - 0.5))
        cv.set(x, y, RIM[4])
        cv.set(x + 1, y + 1, RIM[0])
    return cv


GUI = {'ledger_background': ledger_background, 'logo': logo}
