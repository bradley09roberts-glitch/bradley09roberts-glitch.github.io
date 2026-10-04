"""Tiny software rasteriser used to preview GeckoLib models.

Mimics what the game does for a GeoEntityRenderer:
  * RenderType.entityCutoutNoCull  -> no back-face culling, alpha < 0.1 discarded
  * GL_NEAREST texture sampling
  * vanilla entity diffuse lighting (two fixed lights in WORLD space; the entity is
    rendered rotated by 180 - bodyYaw, so at yaw 0 the model's front (-Z) faces world +Z)
  * AutoGlowingGeoLayer: texels marked in <tex>_glowmask.png are drawn full-bright

World / camera conventions of the preview (baked model space, y up, front = -Z):
  azimuth 0   = camera in front of the doll (sees its front; its right side on screen LEFT)
  azimuth +90 = camera on the doll's RIGHT side
  azimuth 180 = camera behind the doll
  elevation   = degrees above the horizon
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import gl_model as gl

_L0 = np.array([0.2, 1.0, -0.7])
_L0 = _L0 / np.linalg.norm(_L0)
_L1 = np.array([-0.2, 1.0, 0.7])
_L1 = _L1 / np.linalg.norm(_L1)


def entity_light(normal_model: np.ndarray) -> float:
    """minecraft_mix_light with the entity rendered at body yaw 0 (rotated 180 deg about Y)."""
    n = np.array([-normal_model[0], normal_model[1], -normal_model[2]])
    a = max(0.0, float(n @ _L0))
    b = max(0.0, float(n @ _L1))
    return min(1.0, (a + b) * 0.6 + 0.4)


class Camera:
    def __init__(self, azimuth=0.0, elevation=6.0, center=(0, 72, 0), scale=3.0, mode="ortho",
                 distance=900.0, fov=None):
        self.az = math.radians(azimuth)
        self.el = math.radians(elevation)
        self.center = np.array(center, dtype=float) / 16.0   # blocks
        self.scale = scale          # px per model px (ortho)
        self.mode = mode
        self.distance = distance / 16.0  # blocks
        self.fov = fov
        o = np.array([math.sin(self.az) * math.cos(self.el), math.sin(self.el), -math.cos(self.az) * math.cos(self.el)])
        self.pos = self.center + o * self.distance
        f = -o
        up = np.array([0.0, 1.0, 0.0])
        if abs(self.el) > math.radians(80):
            # top / bottom views: screen-up = horizontal direction towards the camera's azimuth side
            up = np.array([math.sin(self.az), 0.0, -math.cos(self.az)]) * (1.0 if self.el > 0 else -1.0)
        r = np.cross(f, up)
        r /= np.linalg.norm(r)
        u = np.cross(r, f)
        self.f, self.r, self.u = f, r, u

    def project(self, pts, w, h):
        """pts (N,3) blocks -> (N,3) screen x, y, depth(z along view dir)"""
        d = pts - self.pos
        xc = d @ self.r
        yc = d @ self.u
        zc = d @ self.f
        if self.mode == "ortho":
            s = self.scale * 16.0
            return np.c_[w / 2 + xc * s, h / 2 - yc * s, zc], zc
        # perspective: scale = pixels per model px at the target distance
        s = self.scale * 16.0 * self.distance
        zz = np.maximum(zc, 1e-3)
        return np.c_[w / 2 + s * xc / zz, h / 2 - s * yc / zz, zc], zc


def _tri(buf, p, uv, zinv, persp, sample, shade, rect):
    """Rasterise one triangle into buf (dict of arrays)."""
    H, W = buf["z"].shape
    x0, y0 = p[0][0], p[0][1]
    x1, y1 = p[1][0], p[1][1]
    x2, y2 = p[2][0], p[2][1]
    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return
    minx = max(int(math.floor(min(x0, x1, x2))), 0)
    maxx = min(int(math.ceil(max(x0, x1, x2))), W - 1)
    miny = max(int(math.floor(min(y0, y1, y2))), 0)
    maxy = min(int(math.ceil(max(y0, y1, y2))), H - 1)
    if minx > maxx or miny > maxy:
        return
    xs = np.arange(minx, maxx + 1) + 0.5
    ys = np.arange(miny, maxy + 1) + 0.5
    X, Y = np.meshgrid(xs, ys)
    w0 = ((x1 - X) * (y2 - Y) - (x2 - X) * (y1 - Y)) / area
    w1 = ((x2 - X) * (y0 - Y) - (x0 - X) * (y2 - Y)) / area
    w2 = 1.0 - w0 - w1
    eps = -1e-6
    inside = (w0 >= eps) & (w1 >= eps) & (w2 >= eps)
    if not inside.any():
        return
    z = w0 * p[0][2] + w1 * p[1][2] + w2 * p[2][2]
    zb = buf["z"][miny:maxy + 1, minx:maxx + 1]
    ok = inside & (z < zb)
    if not ok.any():
        return
    if persp:
        iw = w0 * zinv[0] + w1 * zinv[1] + w2 * zinv[2]
        u = (w0 * uv[0][0] * zinv[0] + w1 * uv[1][0] * zinv[1] + w2 * uv[2][0] * zinv[2]) / iw
        v = (w0 * uv[0][1] * zinv[0] + w1 * uv[1][1] * zinv[1] + w2 * uv[2][1] * zinv[2]) / iw
    else:
        u = w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0]
        v = w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1]
    rgb, a, emissive = sample(u, v, rect)
    ok &= a >= 0.1
    if not ok.any():
        return
    light = np.where(emissive, 1.0, shade)
    col = rgb * light[..., None]
    sub = buf["c"][miny:maxy + 1, minx:maxx + 1]
    sub[ok] = col[ok]
    em = buf["e"][miny:maxy + 1, minx:maxx + 1]
    em[ok] = emissive[ok]
    zb[ok] = z[ok]


def render(model, tex, glow, pose, cam: Camera, size=(300, 480), ssaa=2, night=False, bg=None,
           bloom=True, ambient=None):
    """Render one pose.  tex/glow are uint8 RGBA arrays (H, W, 4); glow may be None."""
    W, H = size[0] * ssaa, size[1] * ssaa
    th, tw = tex.shape[0], tex.shape[1]
    texf = tex.astype(np.float64) / 255.0
    glowa = None if glow is None else glow[..., 3].astype(np.float64) / 255.0

    quads = gl.world_quads(model, pose)
    buf = {
        "z": np.full((H, W), np.inf),
        "c": np.zeros((H, W, 3)),
        "e": np.zeros((H, W), dtype=bool),
    }
    persp = cam.mode != "ortho"
    amb = ambient if ambient is not None else (0.30 if night else 1.0)
    cam_scaled = Camera.__new__(Camera)
    cam_scaled.__dict__.update(cam.__dict__)
    cam_scaled.scale = cam.scale * ssaa

    for bname, verts, uvs, normal, q in quads:
        pts, zc = cam_scaled.project(verts, W, H)
        if persp and (zc <= 1e-3).any():
            continue
        shade = entity_light(normal) * amb
        u0, v0, u1, v1 = q.uv_rect

        def sample(u, v, rect, u0=u0, v0=v0, u1=u1, v1=v1):
            U = np.clip(u * tw, u0, max(u1 - 1e-4, u0))
            V = np.clip(v * th, v0, max(v1 - 1e-4, v0))
            iu = np.clip(U.astype(int), 0, tw - 1)
            iv = np.clip(V.astype(int), 0, th - 1)
            t = texf[iv, iu]
            rgb = t[..., :3]
            a = t[..., 3]
            if glowa is None:
                return rgb, a, np.zeros(a.shape, dtype=bool)
            ga = glowa[iv, iu]
            em = ga > 0
            a = np.where(em, ga, a)
            return rgb, a, em

        zinv = 1.0 / np.maximum(zc, 1e-3)
        for tri in ((0, 1, 2), (0, 2, 3)):
            _tri(buf, [pts[i] for i in tri], [uvs[i] for i in tri], [zinv[i] for i in tri], persp, sample, shade, None)

    img = buf["c"]
    covered = np.isfinite(buf["z"])
    if bg is None:
        bg = _default_bg(W, H, night)
    out = np.where(covered[..., None], img, bg)
    if bloom and night:
        em = np.where((buf["e"] & covered)[..., None], img, 0.0)
        pil = Image.fromarray((np.clip(em, 0, 1) * 255).astype(np.uint8))
        b1 = np.asarray(pil.filter(ImageFilter.GaussianBlur(radius=2.2 * ssaa)), dtype=np.float64) / 255.0
        b2 = np.asarray(pil.filter(ImageFilter.GaussianBlur(radius=7 * ssaa)), dtype=np.float64) / 255.0
        out = np.clip(out + 1.2 * b1 + 1.0 * b2, 0, 1)
    res = Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8))
    if ssaa > 1:
        res = res.resize(size, Image.LANCZOS)
    return res


def _default_bg(W, H, night):
    y = np.linspace(0, 1, H)[:, None, None]
    if night:
        top = np.array([0.03, 0.04, 0.09])
        bot = np.array([0.09, 0.09, 0.14])
    else:
        top = np.array([0.52, 0.66, 0.80])
        bot = np.array([0.74, 0.80, 0.85])
    return top * (1 - y) + bot * y + np.zeros((H, W, 3))


# --------------------------------------------------------------------------------------
# layout helpers
# --------------------------------------------------------------------------------------


def _font(size=14):
    for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/freefont/FreeSans.ttf"):
        try:
            return ImageFont.truetype(p, size)
        except Exception:
            continue
    return ImageFont.load_default()


def contact_sheet(frames, labels=None, cols=6, pad=6, bg=(24, 26, 32), label_h=18, title=None):
    """frames: list of PIL images (same size)."""
    if not frames:
        return None
    w, h = frames[0].size
    rows = (len(frames) + cols - 1) // cols
    th = 26 if title else 0
    sheet = Image.new("RGB", (cols * w + (cols + 1) * pad, th + rows * (h + label_h) + (rows + 1) * pad), bg)
    d = ImageDraw.Draw(sheet)
    f = _font(13)
    if title:
        d.text((pad, 4), title, fill=(235, 235, 235), font=_font(16))
    for i, im in enumerate(frames):
        r, c = divmod(i, cols)
        x = pad + c * (w + pad)
        y = th + pad + r * (h + label_h + pad)
        sheet.paste(im, (x, y + label_h))
        if labels:
            d.text((x + 2, y + 1), str(labels[i]), fill=(210, 215, 225), font=f)
    return sheet


def load_tex(path):
    return np.array(Image.open(path).convert("RGBA"))
