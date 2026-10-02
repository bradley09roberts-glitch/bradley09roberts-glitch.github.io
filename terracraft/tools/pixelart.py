"""Tiny pixel-art toolkit used to procedurally draw TerraCraft's original textures.

All TerraCraft art is generated from these primitives (no Terraria or Minecraft assets are used),
so textures are reproducible, consistent in style, and easy to recolour per material.
"""
import math
import random
from PIL import Image


def hexc(value, alpha=255):
    value = value.lstrip('#')
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def shade(color, factor):
    r, g, b, a = color
    if factor >= 1:
        return (min(255, int(r + (255 - r) * (factor - 1))), min(255, int(g + (255 - g) * (factor - 1))),
                min(255, int(b + (255 - b) * (factor - 1))), a)
    return (int(r * factor), int(g * factor), int(b * factor), a)


def palette(base):
    """5-step ramp: outline, dark, mid, light, highlight."""
    c = hexc(base) if isinstance(base, str) else base
    return [shade(c, 0.35), shade(c, 0.65), c, shade(c, 1.25), shade(c, 1.55)]


class Canvas:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.px = self.img.load()

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.px[x, y] = c

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[x, y]
        return (0, 0, 0, 0)

    def opaque(self, x, y):
        return self.get(x, y)[3] > 0

    def line(self, x0, y0, x1, y1, c, width=1):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            t = i / steps
            x = round(x0 + (x1 - x0) * t)
            y = round(y0 + (y1 - y0) * t)
            for dx in range(width):
                for dy in range(width):
                    self.set(x + dx, y + dy, c)

    def rect(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                self.set(x, y, c)

    def circle(self, cx, cy, r, c):
        for x in range(self.w):
            for y in range(self.h):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + r * 0.3:
                    self.set(x, y, c)

    def ring(self, cx, cy, r, c, thickness=1.0):
        for x in range(self.w):
            for y in range(self.h):
                d = math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
                if r - thickness <= d <= r + 0.5:
                    self.set(x, y, c)

    def polygon(self, points, c):
        xs = [p[0] for p in points]
        ys = [p[1] for p in points]
        for y in range(int(min(ys)), int(max(ys)) + 1):
            for x in range(int(min(xs)), int(max(xs)) + 1):
                if _inside(points, x + 0.5, y + 0.5):
                    self.set(x, y, c)

    def outline(self, color=None, dark=0.35):
        """Adds a 1px outline around opaque pixels (Terraria-style crisp sprites)."""
        add = []
        for x in range(self.w):
            for y in range(self.h):
                if self.opaque(x, y):
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if self.opaque(x + dx, y + dy):
                        neighbour = self.get(x + dx, y + dy)
                        add.append((x, y, color or shade(neighbour, dark)))
                        break
        for x, y, c in add:
            self.set(x, y, c)

    def light(self, amount=1.25, dark=0.8):
        """Highlights top-left edges and darkens bottom-right edges of opaque regions."""
        src = self.img.copy().load()
        for x in range(self.w):
            for y in range(self.h):
                c = src[x, y]
                if c[3] == 0:
                    continue
                tl = src[x - 1, y][3] == 0 if x > 0 else True
                up = src[x, y - 1][3] == 0 if y > 0 else True
                br = src[x + 1, y][3] == 0 if x < self.w - 1 else True
                dn = src[x, y + 1][3] == 0 if y < self.h - 1 else True
                if tl or up:
                    self.px[x, y] = shade(c, amount)
                elif br or dn:
                    self.px[x, y] = shade(c, dark)

    def noise(self, colors, seed, density=1.0):
        rnd = random.Random(seed)
        for x in range(self.w):
            for y in range(self.h):
                if rnd.random() <= density:
                    self.set(x, y, rnd.choice(colors))

    def save(self, path):
        import os
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)


def _inside(points, x, y):
    inside = False
    n = len(points)
    for i in range(n):
        x1, y1 = points[i]
        x2, y2 = points[(i + 1) % n]
        if (y1 > y) != (y2 > y):
            xi = x1 + (y - y1) * (x2 - x1) / (y2 - y1)
            if x < xi:
                inside = not inside
    return inside
