"""Material-map sprite renderer.

Sprites are authored as a grid of material keys (ASCII art or built with the geometry helpers below). Each material
has a six-step hue-shifted ramp; the renderer converts the silhouette boundary into a two-tone outline (lighter on
the top-left, near-black on the bottom-right, like vanilla tools) and shades the interior with a top-left light.

Ramp indices: OD outline-dark, OL outline-light, DK dark, MD mid, LT light, HL highlight.
"""
import math

from .canvas import Canvas, C, ramp as make_ramp

OD, OL, DK, MD, LT, HL = range(6)


class Mat:
    def __init__(self, base, ramp=None, outline=True, shading='edge', pattern=None, group=None, center=None,
                 radius=None, out_dark=None, out_light=None, light=(-0.7, -0.7), thin_mid=MD):
        self.ramp = [C(c) for c in ramp] if ramp else make_ramp(base, out_dark, out_light)
        self.outline = outline          # True: boundary pixels become outline colours; False: shade them too
        self.shading = shading          # 'edge' | 'sphere' | 'flat' | 'none'
        self.pattern = pattern          # fn(x, y, idx) -> idx or colour
        self.group = group if group is not None else id(self)
        self.center = center
        self.radius = radius
        self.light = light
        self.thin_mid = thin_mid        # shade for interior pixels lit from both sides

    def c(self, i):
        return self.ramp[i]


class Fixed:
    """A literal colour that still occupies the silhouette (counts as its own material for edges)."""

    def __init__(self, color, group=None):
        self.color = C(color)
        self.group = group if group is not None else id(self)


class Force:
    """Material pixel with a forced ramp index (or literal colour) that otherwise behaves like `mat`."""

    def __init__(self, mat, idx):
        self.mat, self.idx = mat, idx
        self.group = mat.group


def _spec_mat(s):
    if isinstance(s, Mat):
        return s
    if isinstance(s, Force):
        return s.mat
    return None


class Grid:
    """A W x H grid of material keys with drawing helpers. (0,0) is the top-left pixel."""

    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.g = [[None] * w for _ in range(h)]

    # -------------------------------------------------------------------------------------------- basics
    def put(self, x, y, k):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.g[y][x] = k

    def at(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.g[y][x]
        return None

    def ascii(self, rows, ox=0, oy=0, keep='.'):
        """Overlay ASCII rows; characters in `keep` leave the grid untouched; ' ' clears."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in keep:
                    continue
                self.put(ox + x, oy + y, None if ch == ' ' else ch)
        return self

    def replace(self, a, b, region=None):
        for y in range(self.h):
            for x in range(self.w):
                if self.g[y][x] == a and (region is None or region(x, y)):
                    self.g[y][x] = b
        return self

    def where(self, fn, k):
        for y in range(self.h):
            for x in range(self.w):
                if fn(x, y):
                    self.g[y][x] = k
        return self

    def where_if(self, fn, k, only=None):
        """Like where() but only over pixels already holding one of `only` (None = any filled pixel)."""
        for y in range(self.h):
            for x in range(self.w):
                cur = self.g[y][x]
                if cur is None:
                    continue
                if only is not None and cur not in only:
                    continue
                if fn(x, y):
                    self.g[y][x] = k
        return self

    # -------------------------------------------------------------------------------------------- shapes
    def disc(self, cx, cy, r, k):
        for y in range(self.h):
            for x in range(self.w):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.g[y][x] = k
        return self

    def ellipse(self, cx, cy, rx, ry, k, ring=0.0):
        for y in range(self.h):
            for x in range(self.w):
                v = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
                if v <= 1.0 and (ring <= 0 or v >= ring):
                    self.g[y][x] = k
        return self

    def rect(self, x0, y0, x1, y1, k):
        """Inclusive rectangle."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, k)
        return self

    def seg(self, ax, ay, bx, by, hw, k, cap=True):
        """Thick line segment between pixel-centre points A and B with half-width hw (pixel units)."""
        dx, dy = bx - ax, by - ay
        L2 = dx * dx + dy * dy or 1e-9
        for y in range(self.h):
            for x in range(self.w):
                t = ((x - ax) * dx + (y - ay) * dy) / L2
                if not cap and (t < 0 or t > 1):
                    continue
                t = max(0.0, min(1.0, t))
                px, py = ax + dx * t, ay + dy * t
                if (x - px) ** 2 + (y - py) ** 2 <= hw * hw:
                    self.g[y][x] = k
        return self

    def line(self, x0, y0, x1, y1, k):
        x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.put(x0, y0, k)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy
        return self

    # ------------------------------------------------------------------- 45-degree "tool" axis helpers
    # Axis runs from the bottom-left corner (p=0) to the top-right corner (p=N). For pixel (x,y):
    #   p = (x - y + N) / 2   (position along the axis, half-integer steps)
    #   d = x + y - N         (perpendicular offset; +d toward the bottom-right)
    def pd(self, x, y):
        n = self.w - 1
        return (x - y + n) / 2.0, x + y - n

    def band(self, p0, p1, dlo, dhi, k, only=None):
        """Fill pixels with p in [p0,p1] and d in [dlo(p), dhi(p)] (dlo/dhi may be numbers or callables)."""
        for y in range(self.h):
            for x in range(self.w):
                p, d = self.pd(x, y)
                if p < p0 or p > p1:
                    continue
                lo = dlo(p) if callable(dlo) else dlo
                hi = dhi(p) if callable(dhi) else dhi
                if lo <= d <= hi and (only is None or self.g[y][x] in only):
                    self.g[y][x] = k
        return self

    def axis_xy(self, p, d=0):
        n = self.w - 1
        return p + d / 2.0, n - p + d / 2.0

    def axis_ellipse(self, pc, dc, a, b, k, only=None):
        """Ellipse in axis space: ((p-pc)/a)^2 + ((d-dc)/b)^2 <= 1."""
        for y in range(self.h):
            for x in range(self.w):
                p, d = self.pd(x, y)
                if ((p - pc) / a) ** 2 + ((d - dc) / b) ** 2 <= 1.0 and (only is None or self.g[y][x] in only):
                    self.g[y][x] = k
        return self

    def axis_disc(self, p, d, r, k):
        x, y = self.axis_xy(p, d)
        return self.disc(x + 0.5, y + 0.5, r, k)

    def copy(self):
        g = Grid(self.w, self.h)
        g.g = [list(r) for r in self.g]
        return g

    def flip_h(self):
        g = Grid(self.w, self.h)
        g.g = [list(reversed(r)) for r in self.g]
        return g

    def flip_v(self):
        g = Grid(self.w, self.h)
        g.g = [list(r) for r in reversed(self.g)]
        return g

    def shift(self, dx, dy):
        g = Grid(self.w, self.h)
        for y in range(self.h):
            for x in range(self.w):
                g.put(x + dx, y + dy, self.g[y][x])
        return g

    def overlay(self, other):
        for y in range(self.h):
            for x in range(self.w):
                if other.g[y][x] is not None:
                    self.g[y][x] = other.g[y][x]
        return self

    def render(self, mats):
        return render(self, mats)


def grid_from(rows, w=None, h=None):
    h = h or len(rows)
    w = w or max(len(r) for r in rows)
    g = Grid(w, h)
    g.ascii(rows, keep='. ')
    return g


def render(grid, mats):
    """Render a Grid (or list of ASCII rows) to a Canvas using the material table."""
    if not isinstance(grid, Grid):
        grid = grid_from(grid)
    W, H = grid.w, grid.h
    spec = [[mats[k] if k is not None else None for k in row] for row in grid.g]

    def group(x, y):
        if 0 <= x < W and 0 <= y < H and spec[y][x] is not None:
            return spec[y][x].group
        return None

    # pass 1: outline pixels
    outline = [[None] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            m = _spec_mat(spec[y][x])
            if m is None or not m.outline or isinstance(spec[y][x], Force):
                continue
            up, lf = group(x, y - 1) is None, group(x - 1, y) is None
            dn, rt = group(x, y + 1) is None, group(x + 1, y) is None
            if dn or rt:
                outline[y][x] = OD
            elif up or lf:
                outline[y][x] = OL

    # sphere centres per material group
    centres = {}
    for y in range(H):
        for x in range(W):
            m = _spec_mat(spec[y][x])
            if m is not None and m.shading == 'sphere':
                s = centres.setdefault(m.group, [0.0, 0.0, 0, []])
                s[0] += x + 0.5
                s[1] += y + 0.5
                s[2] += 1
                s[3].append((x + 0.5, y + 0.5))
    for gk, s in centres.items():
        cx, cy = s[0] / s[2], s[1] / s[2]
        r = max(math.hypot(px - cx, py - cy) for px, py in s[3]) or 1.0
        centres[gk] = (cx, cy, r)

    out = Canvas(W, H)
    for y in range(H):
        for x in range(W):
            s = spec[y][x]
            if s is None:
                continue
            if isinstance(s, Fixed):
                out.set(x, y, s.color)
                continue
            if isinstance(s, tuple) or isinstance(s, str):
                out.set(x, y, C(s))
                continue
            m = _spec_mat(s)
            g = m.group

            def edge(nx, ny):
                if group(nx, ny) != g:
                    return True
                return outline[ny][nx] is not None

            if isinstance(s, Force):
                idx = s.idx
            elif outline[y][x] is not None:
                idx = outline[y][x]
            elif m.shading == 'flat':
                idx = MD
            elif m.shading == 'sphere':
                cx, cy, r = (m.center[0], m.center[1], m.radius) if m.center else centres[g]
                if m.radius:
                    r = m.radius
                nx, ny = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
                lx, ly = m.light
                l = (nx * lx + ny * ly) - 0.25 * (nx * nx + ny * ny)
                idx = HL if l > 0.42 else LT if l > 0.12 else MD if l > -0.38 else DK
                if (edge(x + 1, y) or edge(x, y + 1)) and idx > DK and l < 0.1:
                    idx = DK
            else:
                tl = edge(x, y - 1) or edge(x - 1, y)
                br = edge(x, y + 1) or edge(x + 1, y)
                if tl and not br:
                    idx = HL if (edge(x, y - 1) and edge(x - 1, y)) else LT
                elif br and not tl:
                    idx = DK
                elif tl and br:
                    idx = m.thin_mid
                else:
                    idx = MD
            if m.pattern is not None and not isinstance(s, Force):
                r = m.pattern(x, y, idx)
                if isinstance(r, tuple) or isinstance(r, str):
                    out.set(x, y, C(r))
                    continue
                idx = r
            out.set(x, y, m.ramp[idx])
    return out
