"""Software preview renderer (orthographic, z-buffered, nearest-neighbour texturing).

Renders a baked :class:`rig.Rig` with a texture, optionally posed by a :class:`rig.Clip`,
into PNG contact sheets so poses can be inspected without launching Minecraft.

It reproduces the in-game behaviour that matters for look-dev: cutout alpha (<0.1 discarded),
no back-face culling, Minecraft's two-light diffuse shading, per-bone tint / hidden bones
(exactly what the Java renderer does for skin/hair/face/mask bones).
"""
from __future__ import annotations

import math
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from .rig import Clip, Rig


class Camera:
    def __init__(self, azimuth: float = 0.0, elevation: float = 0.0, scale: float = 5.0,
                 center: Sequence[float] = (0.0, 17.0, 0.0), size: Tuple[int, int] = (200, 200)):
        """azimuth 0 = looking at the entity's front, 90 = from its right side, 180 = from behind."""
        a = math.radians(azimuth)
        b = math.radians(elevation)
        c = np.array([math.cos(b) * math.sin(a), math.sin(b), -math.cos(b) * math.cos(a)])
        f = -c
        up = np.array([0.0, 1.0, 0.0])
        r = np.cross(f, up)
        if np.linalg.norm(r) < 1e-6:  # looking straight down/up
            r = np.array([math.cos(a), 0.0, math.sin(a)])
        r = r / np.linalg.norm(r)
        u = np.cross(r, f)
        self.right, self.up, self.fwd = r, u, f
        self.scale = scale
        self.center = np.array(center, dtype=float)
        self.size = size

    def project(self, pts: np.ndarray) -> np.ndarray:
        """pts (...,3) -> (...,3) screen x, y (pixels, y down) and depth (smaller = closer)."""
        d = pts - self.center
        sx = d @ self.right * self.scale + self.size[0] / 2.0
        sy = self.size[1] / 2.0 - d @ self.up * self.scale
        dep = d @ self.fwd
        return np.stack([sx, sy, dep], axis=-1)


def _mc_shade(n_world: np.ndarray, cam: Camera) -> float:
    """Minecraft entity diffuse: two fixed view-space lights + 0.4 ambient."""
    n = np.array([n_world @ cam.right, n_world @ cam.up, -(n_world @ cam.fwd)])
    if n[2] < 0:  # no culling: face the camera
        n = -n
    l0 = np.array([0.2, 1.0, -0.7]); l0 /= np.linalg.norm(l0)
    l1 = np.array([-0.2, 1.0, 0.7]); l1 /= np.linalg.norm(l1)
    acc = max(0.0, float(n @ l0)) + max(0.0, float(n @ l1))
    return min(1.0, 0.4 + acc * 0.6)


class Frame:
    """Render target with z-buffer."""

    def __init__(self, w: int, h: int, bg=(0.93, 0.93, 0.94)):
        self.w, self.h = w, h
        self.img = np.zeros((h, w, 3), dtype=np.float32)
        self.img[:] = bg
        self.z = np.full((h, w), np.inf, dtype=np.float32)


def raster_quad(fr: Frame, sv: np.ndarray, uvs: np.ndarray, tex: np.ndarray, shade: float,
                tint: Optional[Sequence[float]] = None) -> None:
    """Rasterise one parallelogram quad (vertices in GeckoLib order: v0->v1 and v0->v3 are its edges)."""
    p0, p1, p3 = sv[0], sv[1], sv[3]
    e1 = p1[:2] - p0[:2]
    e3 = p3[:2] - p0[:2]
    det = e1[0] * e3[1] - e1[1] * e3[0]
    if abs(det) < 1e-4:
        return
    xs = sv[:, 0]
    ys = sv[:, 1]
    x0 = max(int(math.floor(xs.min())), 0)
    x1 = min(int(math.ceil(xs.max())), fr.w - 1)
    y0 = max(int(math.floor(ys.min())), 0)
    y1 = min(int(math.ceil(ys.max())), fr.h - 1)
    if x1 < x0 or y1 < y0:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    dx = gx - p0[0]
    dy = gy - p0[1]
    s = (dx * e3[1] - dy * e3[0]) / det
    t = (e1[0] * dy - e1[1] * dx) / det
    eps = 1e-6
    inside = (s >= -eps) & (s <= 1 + eps) & (t >= -eps) & (t <= 1 + eps)
    if not inside.any():
        return
    s = np.clip(s, 1e-4, 1 - 1e-4)
    t = np.clip(t, 1e-4, 1 - 1e-4)
    dep = p0[2] + s * (p1[2] - p0[2]) + t * (sv[3][2] - p0[2])
    zsub = fr.z[y0:y1 + 1, x0:x1 + 1]
    ok = inside & (dep < zsub)
    if not ok.any():
        return
    u = uvs[0, 0] + s * (uvs[1, 0] - uvs[0, 0]) + t * (uvs[3, 0] - uvs[0, 0])
    v = uvs[0, 1] + s * (uvs[1, 1] - uvs[0, 1]) + t * (uvs[3, 1] - uvs[0, 1])
    th, tw = tex.shape[0], tex.shape[1]
    ix = np.clip(np.floor(u * tw).astype(np.int32), 0, tw - 1)
    iy = np.clip(np.floor(v * th).astype(np.int32), 0, th - 1)
    texel = tex[iy, ix]  # (h,w,4) float 0..1
    ok &= texel[..., 3] >= 0.1
    if not ok.any():
        return
    col = texel[..., :3] * shade
    if tint is not None:
        col = col * np.array(tint, dtype=np.float32)
    isub = fr.img[y0:y1 + 1, x0:x1 + 1]
    isub[ok] = col[ok]
    zsub[ok] = dep[ok]


def load_texture(path: str) -> np.ndarray:
    im = Image.open(path).convert("RGBA")
    return np.asarray(im, dtype=np.float32) / 255.0


def draw_ground(fr: Frame, cam: Camera, extent: float = 40.0) -> None:
    """Floor plane y=0: a darker band below the feet + grid ticks (for side/front views)."""
    if abs(cam.up[1]) < 0.5:
        return  # top-down handled by background grid only
    gy = cam.project(np.array([[0.0, 0.0, 0.0]]))[0, 1]
    y = int(round(gy))
    if y < fr.h:
        fr.img[max(y, 0):, :, :] = fr.img[max(y, 0):, :, :] * 0.86
        if 0 <= y < fr.h:
            fr.img[y, :, :] = (0.30, 0.30, 0.34)
    # vertical scale ticks every 8 px of model height on the left edge
    for k in range(0, 5):
        yy = int(round(cam.project(np.array([[0.0, 8.0 * k, 0.0]]))[0, 1]))
        if 0 <= yy < fr.h:
            fr.img[yy, :6, :] = (0.45, 0.45, 0.5)


_SOLID = np.ones((1, 1, 4), dtype=np.float32)
_BOX_FACES = [(0, 1, 2, 3), (4, 5, 6, 7), (0, 1, 5, 4), (3, 2, 6, 7), (0, 3, 7, 4), (1, 2, 6, 5)]


def draw_props(fr: Frame, cam: Camera, props) -> None:
    """props: list of (lo, hi, colour) axis-aligned boxes in baked model space (flat colour, same lighting)."""
    for lo, hi, col in props:
        x0, y0, z0 = lo
        x1, y1, z1 = hi
        v = np.array([[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0],
                      [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]], dtype=float)
        sv = cam.project(v)
        tex = _SOLID * np.array([col[0], col[1], col[2], 1.0], dtype=np.float32)
        uv = np.full((4, 2), 0.5)
        for f in _BOX_FACES:
            quad = sv[list(f)]
            q3 = v[list(f)]
            n = np.cross(q3[1] - q3[0], q3[3] - q3[0])
            ln = np.linalg.norm(n)
            if ln < 1e-9:
                continue
            raster_quad(fr, quad, uv, tex, _mc_shade(n / ln, cam))


def _render(rig: Rig, tex: np.ndarray, state: Optional[dict], cam: Camera,
            tint: Optional[Dict[str, Sequence[float]]], hidden: Iterable[str],
            bg, ground: bool, props=None) -> np.ndarray:
    W, H = cam.size
    fr = Frame(W, H, bg)
    if ground:
        draw_ground(fr, cam)
    if props:
        draw_props(fr, cam, props)
    mats = rig.world_matrices(state)
    tint = tint or {}
    for bone, cube, verts in rig.vertices_world(mats, hidden=hidden):
        tn = tint.get(bone)
        sv_all = cam.project(verts)  # (Q,4,3)
        for qi, q in enumerate(cube.quads):
            v = verts[qi]
            n = np.cross(v[1] - v[0], v[3] - v[0])
            ln = np.linalg.norm(n)
            if ln < 1e-9:
                # zero-area in 3D (should not happen) - use stored normal
                nw = np.zeros(3)
            else:
                nw = n / ln
            shade = _mc_shade(nw, cam) if ln >= 1e-9 else 0.8
            raster_quad(fr, sv_all[qi], q.uvs, tex, shade, tn)
    return fr.img


def render(rig: Rig, tex: np.ndarray, state: Optional[dict], azimuth: float = 0.0, elevation: float = 0.0,
           scale: float = 5.0, center: Sequence[float] = (0.0, 17.0, 0.0), size: Tuple[int, int] = (200, 200),
           tint: Optional[Dict[str, Sequence[float]]] = None, hidden: Iterable[str] = (),
           ss: int = 1, bg=(0.93, 0.93, 0.94), ground: bool = True, props=None) -> Image.Image:
    cam = Camera(azimuth, elevation, scale * ss, center, (size[0] * ss, size[1] * ss))
    arr = _render(rig, tex, state, cam, tint, hidden, bg, ground, props)
    im = Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")
    if ss > 1:
        im = im.resize(size, Image.LANCZOS)
    return im


# ----------------------------------------------------------------------------------
# Sheets
# ----------------------------------------------------------------------------------
_FONT = None


def _font():
    global _FONT
    if _FONT is None:
        try:
            _FONT = ImageFont.load_default(size=11)
        except TypeError:
            _FONT = ImageFont.load_default()
    return _FONT


DEFAULT_VIEWS = [
    dict(name="front", azimuth=0, elevation=0),
    dict(name="side", azimuth=90, elevation=0),
    dict(name="3/4", azimuth=-38, elevation=12),
]


def label(im: Image.Image, text: str, xy=(3, 2)) -> None:
    d = ImageDraw.Draw(im)
    d.text((xy[0] + 1, xy[1] + 1), text, fill=(255, 255, 255), font=_font())
    d.text(xy, text, fill=(20, 20, 30), font=_font())


def frame_times(clip: Clip, n: int = 8) -> List[float]:
    L = clip.length
    if clip.loop == "loop":
        return [L * i / n for i in range(n)]
    return [L * i / (n - 1) for i in range(n)]


def sheet(rig: Rig, tex: np.ndarray, clip: Optional[Clip], times: Sequence[float],
          views: Sequence[dict] = tuple(DEFAULT_VIEWS), cell: Tuple[int, int] = (176, 190),
          scale: float = 4.4, center=(0.0, 17.5, 0.0), tint=None, hidden=(), ss: int = 1,
          title: str = "", props=None) -> Image.Image:
    cols = len(views)
    rows = len(times)
    head = 16 if title else 0
    out = Image.new("RGB", (cols * cell[0], rows * cell[1] + head), (255, 255, 255))
    if title:
        label(out, title, (4, 1))
    for r, t in enumerate(times):
        state = clip.sample(t) if clip is not None else None
        for c, v in enumerate(views):
            im = render(rig, tex, state, azimuth=v.get("azimuth", 0), elevation=v.get("elevation", 0),
                        scale=v.get("scale", scale), center=v.get("center", center), size=cell,
                        tint=tint, hidden=hidden, ss=ss, props=props)
            label(im, f"{v.get('name', '')} t={t:.2f}")
            out.paste(im, (c * cell[0], head + r * cell[1]))
    return out


def filmstrip(rig: Rig, tex: np.ndarray, clip: Clip, n: int = 10, azimuth: float = 90,
              cell=(120, 150), scale: float = 3.4, center=(0.0, 17.0, 0.0), tint=None, hidden=(),
              ss: int = 1, props=None) -> Image.Image:
    times = frame_times(clip, n)
    out = Image.new("RGB", (cell[0] * n, cell[1]), (255, 255, 255))
    for i, t in enumerate(times):
        im = render(rig, tex, clip.sample(t), azimuth=azimuth, scale=scale, center=center, size=cell,
                    tint=tint, hidden=hidden, ss=ss, props=props)
        out.paste(im, (i * cell[0], 0))
    return out


def texture_sheet(tex_path: str, regions: Optional[Dict[str, Tuple[int, int, int, int]]] = None,
                  zoom: int = 6, bg_check: bool = True) -> Image.Image:
    im = Image.open(tex_path).convert("RGBA")
    w, h = im.size
    big = im.resize((w * zoom, h * zoom), Image.NEAREST)
    base = Image.new("RGBA", big.size, (255, 255, 255, 255))
    if bg_check:
        d = ImageDraw.Draw(base)
        cs = zoom * 4
        for y in range(0, big.size[1], cs):
            for x in range(0, big.size[0], cs):
                if ((x // cs) + (y // cs)) % 2 == 0:
                    d.rectangle([x, y, x + cs - 1, y + cs - 1], fill=(70, 78, 104, 255))
                else:
                    d.rectangle([x, y, x + cs - 1, y + cs - 1], fill=(82, 90, 118, 255))
    base.alpha_composite(big)
    if regions:
        d = ImageDraw.Draw(base)
        for name, (x, y, rw, rh) in regions.items():
            d.rectangle([x * zoom, y * zoom, (x + rw) * zoom - 1, (y + rh) * zoom - 1], outline=(255, 0, 80, 255))
    return base.convert("RGB")
