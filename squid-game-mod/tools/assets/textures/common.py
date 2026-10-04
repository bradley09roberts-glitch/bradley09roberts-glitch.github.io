"""Shared helpers for the Squid Game texture / model generators.

Everything here is deterministic: all randomness goes through ``rng(seed)`` and PNGs are
written without any metadata, so re-running the generators reproduces identical bytes.

Conventions
-----------
* Pixel arrays are ``numpy.uint8`` with shape ``(H, W, 4)`` (RGBA).
* Colours are ``(r, g, b)`` or ``(r, g, b, a)`` tuples of 0..255 ints, or ``'#rrggbb'``.
* ``Canvas`` is a thin wrapper around such an array with a few drawing primitives that
  sample at pixel centres (so shapes drawn about an integer centre are perfectly symmetric
  and there is never any anti-aliasing).
"""
from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Sequence

import numpy as np
from PIL import Image

# --------------------------------------------------------------------------- paths
TOOLS_DIR = Path(__file__).resolve().parent                 # tools/assets/textures
REPO_ROOT = TOOLS_DIR.parents[2]                            # squid-game-mod
DEFAULT_RES = REPO_ROOT / "src" / "main" / "resources" / "assets" / "squidgame"
LANG_DIR = TOOLS_DIR.parent / "lang"
PREVIEW_DIR = TOOLS_DIR / "preview"
CONTRACT = REPO_ROOT / "docs" / "ASSET_CONTRACT.md"

# Vanilla assets used only for previews / validation of vanilla parent models.
_VANILLA_CANDIDATES = [
    Path("/tmp/claude-0/-home-user-bradley09roberts-glitch-github-io/"
         "4a6d7ca0-18e4-5def-acf4-50061f55720d/scratchpad/mcassets/assets/minecraft"),
]


def vanilla_root() -> Path | None:
    for p in _VANILLA_CANDIDATES:
        if p.is_dir():
            return p
    return None


# --------------------------------------------------------------------------- ids
PASTELS = ["pink", "mint", "yellow", "sky", "lilac", "peach", "cream"]
TILES = ["pink", "white", "black"]
PANEL_LIGHTS = ["white", "warm", "pink"]
SYMBOLS = ["circle", "triangle", "square"]

BLOCK_IDS: list[str] = (
    ["bridge_glass", "cash_block", "monitor"]
    + [f"panel_light_{c}" for c in PANEL_LIGHTS]
    + ["playground_ground", "registration_terminal", "dalgona_station", "invisible_wall"]
    + [f"tile_{c}" for c in TILES]
    + [f"pastel_{c}" for c in PASTELS]
    + [f"pastel_{c}_stairs" for c in PASTELS]
    + [f"pastel_{c}_slab" for c in PASTELS]
    + [f"symbol_{s}" for s in SYMBOLS]
)
ITEM_IDS: list[str] = ["marble", "recruiter_card"]
# registered by ModItems but not part of the contract: they only need a vanilla-parent item model + a display name
SPAWN_EGGS: list[str] = ["contestant_spawn_egg", "guard_spawn_egg", "doll_spawn_egg"]


# --------------------------------------------------------------------------- colours
def rgb(c) -> tuple[int, int, int]:
    """'#rrggbb' / (r,g,b) / (r,g,b,a) -> (r,g,b)."""
    if isinstance(c, str):
        c = c.lstrip("#")
        return int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)
    return int(c[0]), int(c[1]), int(c[2])


def rgba(c, a: int | None = None) -> tuple[int, int, int, int]:
    if isinstance(c, str):
        r, g, b = rgb(c)
        return r, g, b, 255 if a is None else a
    if len(c) == 4:
        return int(c[0]), int(c[1]), int(c[2]), int(c[3] if a is None else a)
    return int(c[0]), int(c[1]), int(c[2]), 255 if a is None else a


def mix(a, b, t: float):
    """Linear mix of two colours (rgb tuples / hex)."""
    a, b = rgb(a), rgb(b)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f: float):
    """Multiply a colour by f (clamped)."""
    c = rgb(c)
    return tuple(int(max(0, min(255, round(v * f)))) for v in c)


def lighten(c, t: float):
    return mix(c, (255, 255, 255), t)


def darken(c, t: float):
    return mix(c, (0, 0, 0), t)


def _srgb_to_lin(v):
    v = v / 255.0
    return np.where(v <= 0.04045, v / 12.92, ((v + 0.055) / 1.055) ** 2.4)


def _lin_to_srgb(v):
    v = np.clip(v, 0.0, 1.0)
    return np.where(v <= 0.0031308, v * 12.92, 1.055 * np.power(v, 1 / 2.4) - 0.055) * 255.0


_M_RGB2XYZ = np.array([[0.4124564, 0.3575761, 0.1804375],
                       [0.2126729, 0.7151522, 0.0721750],
                       [0.0193339, 0.1191920, 0.9503041]])
_WHITE = np.array([0.95047, 1.0, 1.08883])


def rgb_to_lch(c) -> tuple[float, float, float]:
    """sRGB (0..255) -> CIE LCH(ab) with D65."""
    lin = _srgb_to_lin(np.array(rgb(c), dtype=float))
    xyz = _M_RGB2XYZ @ lin / _WHITE
    f = np.where(xyz > 216 / 24389, np.cbrt(xyz), (24389 / 27 * xyz + 16) / 116)
    L = 116 * f[1] - 16
    a = 500 * (f[0] - f[1])
    b = 200 * (f[1] - f[2])
    return float(L), float(math.hypot(a, b)), float(math.degrees(math.atan2(b, a)) % 360)


def lch_to_rgb(L: float, C: float, h: float) -> tuple[int, int, int]:
    a = C * math.cos(math.radians(h))
    b = C * math.sin(math.radians(h))
    fy = (L + 16) / 116
    fx = fy + a / 500
    fz = fy - b / 200

    def finv(t):
        return t ** 3 if t ** 3 > 216 / 24389 else (116 * t - 16) / (24389 / 27)

    xyz = np.array([finv(fx), finv(fy), finv(fz)]) * _WHITE
    lin = np.linalg.inv(_M_RGB2XYZ) @ xyz
    srgb = _lin_to_srgb(lin)
    return tuple(int(round(float(v))) for v in srgb)


# --------------------------------------------------------------------------- randomness
class Rng:
    """Seeded random source with the small API the generators use.

    Backed by NumPy's legacy ``RandomState`` whose stream is *frozen* across NumPy releases (NEP 19), so the generated
    textures stay byte-identical on any machine / NumPy version (``Generator`` makes no such promise).
    """

    def __init__(self, seed: int):
        self._rs = np.random.RandomState(seed)

    def random(self, size=None):
        return self._rs.random_sample(size)

    def integers(self, low, high=None, size=None):
        return self._rs.randint(low, high, size)

    def choice(self, a, size=None, p=None):
        return self._rs.choice(a, size=size, p=p)


def rng(seed: int | str) -> Rng:
    if isinstance(seed, str):
        seed = sum((i + 1) * ord(ch) for i, ch in enumerate(seed)) & 0xFFFFFFFF
    return Rng(seed)


def value_noise(w: int, h: int, cell: int, r: Rng, wrap: bool = True) -> np.ndarray:
    """Smooth value noise in [0,1], shape (h, w). With ``wrap`` it tiles seamlessly.

    ``cell`` is the grid spacing in pixels (must divide w and h when wrap=True).
    """
    gw, gh = max(1, w // cell), max(1, h // cell)
    grid = r.random((gh + 1, gw + 1))
    if wrap:
        grid[gh, :] = grid[0, :]
        grid[:, gw] = grid[:, 0]
    ys = (np.arange(h) + 0.5) / cell
    xs = (np.arange(w) + 0.5) / cell
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = ys - y0
    fx = xs - x0
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    y1 = y0 + 1
    x1 = x0 + 1
    g00 = grid[np.ix_(y0, x0)]
    g01 = grid[np.ix_(y0, x1)]
    g10 = grid[np.ix_(y1, x0)]
    g11 = grid[np.ix_(y1, x1)]
    top = g00 * (1 - fx)[None, :] + g01 * fx[None, :]
    bot = g10 * (1 - fx)[None, :] + g11 * fx[None, :]
    return top * (1 - fy)[:, None] + bot * fy[:, None]


def fbm(w: int, h: int, r: Rng, octaves: Sequence[tuple[int, float]], wrap: bool = True) -> np.ndarray:
    """Sum of value-noise octaves ``[(cell, weight), ...]`` normalised to [0,1]."""
    acc = np.zeros((h, w))
    tot = 0.0
    for cell, wt in octaves:
        acc += wt * value_noise(w, h, cell, r, wrap)
        tot += wt
    return acc / tot


BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16.0


def bayer(w: int, h: int) -> np.ndarray:
    return np.tile(BAYER4, (h // 4 + 1, w // 4 + 1))[:h, :w]


def ramp(values: np.ndarray, palette: Sequence, dither: float = 0.0) -> np.ndarray:
    """Map values in [0,1] to a palette (list of colours, dark -> light) producing RGB uint8.

    Hard banded (pixel-art style). ``dither`` in 0..1 jitters the band thresholds with a Bayer matrix.
    """
    h, w = values.shape
    n = len(palette)
    v = values
    if dither:
        v = v + (bayer(w, h) - 0.5) * dither / n
    idx = np.clip(np.floor(v * n).astype(int), 0, n - 1)
    pal = np.array([rgb(c) for c in palette], dtype=np.uint8)
    return pal[idx]


# --------------------------------------------------------------------------- canvas
class Canvas:
    """RGBA pixel canvas with simple, alias-free drawing primitives."""

    def __init__(self, w: int, h: int | None = None, fill=(0, 0, 0, 0)):
        h = w if h is None else h
        self.a = np.zeros((h, w, 4), dtype=np.uint8)
        self.a[:, :] = rgba(fill)

    # -- basics
    @property
    def w(self) -> int:
        return self.a.shape[1]

    @property
    def h(self) -> int:
        return self.a.shape[0]

    def copy(self) -> "Canvas":
        c = Canvas(self.w, self.h)
        c.a = self.a.copy()
        return c

    def px(self, x: int, y: int, c) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = rgba(c)

    def get(self, x: int, y: int):
        return tuple(int(v) for v in self.a[y, x])

    def rect(self, x0: int, y0: int, x1: int, y1: int, c) -> None:
        """Fill [x0,x1) x [y0,y1)."""
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(self.w, x1), min(self.h, y1)
        if x1 > x0 and y1 > y0:
            self.a[y0:y1, x0:x1] = rgba(c)

    def outline(self, x0: int, y0: int, x1: int, y1: int, c) -> None:
        self.rect(x0, y0, x1, y0 + 1, c)
        self.rect(x0, y1 - 1, x1, y1, c)
        self.rect(x0, y0, x0 + 1, y1, c)
        self.rect(x1 - 1, y0, x1, y1, c)

    def mask(self, m: np.ndarray, c) -> None:
        """Paint colour ``c`` wherever boolean mask ``m`` is set. ``c`` may also be an (H,W,3|4) array."""
        if isinstance(c, np.ndarray):
            if c.shape[2] == 3:
                self.a[m, :3] = c[m]
                self.a[m, 3] = 255
            else:
                self.a[m] = c[m]
        else:
            self.a[m] = rgba(c)

    def fill_rgb(self, arr: np.ndarray) -> None:
        self.a[..., :3] = arr
        self.a[..., 3] = 255

    def blit(self, other: "Canvas", x: int, y: int, over: bool = True) -> None:
        """Paste another canvas at (x,y); transparent source pixels leave destination untouched."""
        for yy in range(other.h):
            for xx in range(other.w):
                px = other.a[yy, xx]
                if px[3] == 0:
                    continue
                X, Y = x + xx, y + yy
                if 0 <= X < self.w and 0 <= Y < self.h:
                    if px[3] == 255 or not over:
                        self.a[Y, X] = px
                    else:
                        self.a[Y, X] = over_px(self.a[Y, X], px)

    # -- shape masks (pixel-centre sampling) ------------------------------------------------
    def grid(self):
        ys, xs = np.mgrid[0:self.h, 0:self.w]
        return xs + 0.5, ys + 0.5

    def disc_mask(self, cx: float, cy: float, r: float) -> np.ndarray:
        xs, ys = self.grid()
        return (xs - cx) ** 2 + (ys - cy) ** 2 <= r * r

    def ellipse_mask(self, cx: float, cy: float, rx: float, ry: float) -> np.ndarray:
        xs, ys = self.grid()
        return ((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2 <= 1.0

    def poly_mask(self, pts: Sequence[tuple[float, float]]) -> np.ndarray:
        xs, ys = self.grid()
        return poly_inside(xs, ys, pts)

    def line_mask(self, x0: float, y0: float, x1: float, y1: float, thick: float = 1.0) -> np.ndarray:
        xs, ys = self.grid()
        return seg_dist(xs, ys, x0, y0, x1, y1) <= thick / 2.0

    def line(self, x0, y0, x1, y1, c, thick: float = 1.0):
        self.mask(self.line_mask(x0, y0, x1, y1, thick), c)


def over_px(dst: np.ndarray, src: np.ndarray) -> np.ndarray:
    """Alpha-composite a single RGBA pixel ``src`` over ``dst`` (uint8 arrays)."""
    sa = src[3] / 255.0
    da = dst[3] / 255.0
    oa = sa + da * (1 - sa)
    if oa <= 0:
        return np.zeros(4, dtype=np.uint8)
    out = np.zeros(4, dtype=np.uint8)
    for i in range(3):
        out[i] = int(round((src[i] * sa + dst[i] * da * (1 - sa)) / oa))
    out[3] = int(round(oa * 255))
    return out


def seg_dist(px, py, x0, y0, x1, y1):
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    if L2 == 0:
        return np.hypot(px - x0, py - y0)
    t = np.clip(((px - x0) * dx + (py - y0) * dy) / L2, 0, 1)
    return np.hypot(px - (x0 + t * dx), py - (y0 + t * dy))


def poly_inside(px, py, pts) -> np.ndarray:
    """Even-odd point-in-polygon for arrays of points."""
    inside = np.zeros(px.shape, dtype=bool)
    n = len(pts)
    j = n - 1
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[j]
        cond = ((yi > py) != (yj > py))
        with np.errstate(divide="ignore", invalid="ignore"):
            xint = (xj - xi) * (py - yi) / (yj - yi + 1e-12) + xi
        inside ^= cond & (px < xint)
        j = i
    return inside


def dilate(mask: np.ndarray, n: int = 1, diag: bool = True) -> np.ndarray:
    out = mask.copy()
    for _ in range(n):
        m = out.copy()
        m[1:, :] |= out[:-1, :]
        m[:-1, :] |= out[1:, :]
        m[:, 1:] |= out[:, :-1]
        m[:, :-1] |= out[:, 1:]
        if diag:
            m[1:, 1:] |= out[:-1, :-1]
            m[1:, :-1] |= out[:-1, 1:]
            m[:-1, 1:] |= out[1:, :-1]
            m[:-1, :-1] |= out[1:, 1:]
        out = m
    return out


def erode(mask: np.ndarray, n: int = 1) -> np.ndarray:
    return ~dilate(~mask, n)


def art(rows: Sequence[str], legend: dict, w: int | None = None) -> Canvas:
    """Build a canvas from ASCII rows; '.' / ' ' are transparent, others looked up in ``legend``."""
    h = len(rows)
    w = w or max(len(r) for r in rows)
    c = Canvas(w, h)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in ". ":
                continue
            if ch not in legend:
                raise KeyError(f"art(): no legend entry for {ch!r}")
            c.px(x, y, legend[ch])
    return c


# --------------------------------------------------------------------------- json
def _inline(obj) -> str | None:
    """One-line JSON for scalars, flat lists and flat dicts of scalars / flat lists; else None."""
    if isinstance(obj, (str, int, float, bool)) or obj is None:
        return json.dumps(obj, ensure_ascii=False)
    if isinstance(obj, list) and all(not isinstance(x, (dict, list)) for x in obj):
        return "[" + ", ".join(json.dumps(x, ensure_ascii=False) for x in obj) + "]"
    if isinstance(obj, dict):
        parts = []
        for k, v in obj.items():
            iv = _inline(v) if not isinstance(v, dict) else None
            if iv is None:
                return None
            parts.append(f"{json.dumps(k, ensure_ascii=False)}: {iv}")
        return "{ " + ", ".join(parts) + " }"
    return None


def format_json(obj, level: int = 0, width: int = 118) -> str:
    """Readable JSON: nested structure indented by 2, flat lists / small flat dicts kept on one line."""
    ind = "  " * level
    inl = _inline(obj)
    if inl is not None and len(inl) + len(ind) <= width:
        return inl
    if isinstance(obj, dict):
        if not obj:
            return "{}"
        items = [f"{ind}  {json.dumps(k, ensure_ascii=False)}: {format_json(v, level + 1, width)}" for k, v in obj.items()]
        return "{\n" + ",\n".join(items) + f"\n{ind}}}"
    if isinstance(obj, list):
        items = [f"{ind}  {format_json(x, level + 1, width)}" for x in obj]
        return "[\n" + ",\n".join(items) + f"\n{ind}]"
    return json.dumps(obj, ensure_ascii=False)


# --------------------------------------------------------------------------- output
class Out:
    """Output root + bookkeeping of written files."""

    def __init__(self, root: Path | str | None = None, lang_dir: Path | str | None = None):
        self.root = Path(root) if root else DEFAULT_RES
        self.lang_dir = Path(lang_dir) if lang_dir else LANG_DIR
        self.written: list[Path] = []

    def path(self, rel: str) -> Path:
        p = self.root / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        return p

    def png(self, rel: str, img) -> Path:
        if isinstance(img, Canvas):
            arr = img.a
        elif isinstance(img, Image.Image):
            arr = np.array(img.convert("RGBA"))
        else:
            arr = np.asarray(img)
            if arr.ndim == 3 and arr.shape[2] == 3:
                arr = np.concatenate([arr, np.full(arr.shape[:2] + (1,), 255, np.uint8)], axis=2)
        arr = np.ascontiguousarray(arr.astype(np.uint8))
        p = self.path(rel)
        Image.fromarray(arr).save(p, format="PNG", compress_level=9)
        self.written.append(p)
        return p

    def json(self, rel: str, data, *, root: Path | None = None) -> Path:
        p = (root or self.root) / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(format_json(data) + "\n", encoding="utf-8")
        self.written.append(p)
        return p


def load_png(path: Path | str) -> np.ndarray:
    return np.array(Image.open(path).convert("RGBA"))
