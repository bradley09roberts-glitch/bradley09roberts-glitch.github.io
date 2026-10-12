"""Minecraft player-skin UV layout (64x64, classic 4px arms), a painter that
addresses faces in local coordinates, layer compositing/tinting, flattened
front/back views and a small z-buffered 3D renderer for previews.

UV conventions (character faces +z, viewer in front; "right" = character's right):
  head 8x8x8  base (0,0)   overlay/hat    (32,0)
  body 8x12x4 base (16,16) overlay/jacket (16,32)
  r.arm 4x12x4 base (40,16) overlay/sleeve (40,32)
  l.arm 4x12x4 base (32,48) overlay/sleeve (48,48)
  r.leg 4x12x4 base (0,16)  overlay/pants  (0,32)
  l.leg 4x12x4 base (16,48) overlay/pants  (0,48)
For a part at (u,v) with size (w,h,d):
  top (u+d,v,w,d)  bottom (u+d+w,v,w,d)
  right (u,v+d,d,h)  front (u+d,v+d,w,h)  left (u+d+w,v+d,d,h)  back (u+2d+w,v+d,w,h)
The four side faces form one contiguous strip [right|front|left|back].
Local x on each face runs left->right as seen in the texture:
  right face: back -> front     left face: front -> back
  front face: char-right -> char-left   back face: char-left -> char-right
  top face rows: back -> front  bottom face rows: front -> back
"""
from __future__ import annotations

from math import cos, radians, sin

import numpy as np
from PIL import Image

from art_pixel import Canvas, C, alpha_over, multiply

PARTS = {
    'head': ((0, 0), (32, 0), (8, 8, 8)),
    'body': ((16, 16), (16, 32), (8, 12, 4)),
    'right_arm': ((40, 16), (40, 32), (4, 12, 4)),
    'left_arm': ((32, 48), (48, 48), (4, 12, 4)),
    'right_leg': ((0, 16), (0, 32), (4, 12, 4)),
    'left_leg': ((16, 48), (0, 48), (4, 12, 4)),
}
FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')
SIDES = ('right', 'front', 'left', 'back')
LIMBS = ('right_arm', 'left_arm', 'right_leg', 'left_leg')


def face_rect(part, face, ov=False):
    """(x0, y0, x1, y1) with exclusive x1/y1."""
    (bu, bv), (ou, ovv), (w, h, d) = PARTS[part]
    u, v = (ou, ovv) if ov else (bu, bv)
    return {
        'top': (u + d, v, u + d + w, v + d),
        'bottom': (u + d + w, v, u + d + 2 * w, v + d),
        'right': (u, v + d, u + d, v + d + h),
        'front': (u + d, v + d, u + d + w, v + d + h),
        'left': (u + d + w, v + d, u + 2 * d + w, v + d + h),
        'back': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }[face]


def face_size(part, face):
    x0, y0, x1, y1 = face_rect(part, face)
    return x1 - x0, y1 - y0


def used_mask(base=True, overlay=True):
    """Boolean 64x64 mask of every UV texel that belongs to the player model."""
    m = np.zeros((64, 64), dtype=bool)
    for part in PARTS:
        for face in FACES:
            for ov, on in ((False, base), (True, overlay)):
                if on:
                    x0, y0, x1, y1 = face_rect(part, face, ov)
                    m[y0:y1, x0:x1] = True
    return m


def outer_face(part):
    return 'right' if part.startswith('right') else 'left'


def inner_face(part):
    return 'left' if part.startswith('right') else 'right'


class Skin(Canvas):
    """64x64 skin canvas addressed by (part, face, local x, local y)."""

    def __init__(self):
        super().__init__(64, 64)

    # face-local pixel access --------------------------------------------
    def fp(self, part, face, x, y, c, ov=False):
        x0, y0, x1, y1 = face_rect(part, face, ov)
        if 0 <= x < x1 - x0 and 0 <= y < y1 - y0:
            self.set(x0 + x, y0 + y, c)

    def fg(self, part, face, x, y, ov=False):
        x0, y0, x1, y1 = face_rect(part, face, ov)
        return self.get(x0 + x, y0 + y)

    def fclear(self, part, face, x, y, ov=False):
        x0, y0, x1, y1 = face_rect(part, face, ov)
        if 0 <= x < x1 - x0 and 0 <= y < y1 - y0:
            self.clear(x0 + x, y0 + y)

    def fill(self, part, face, c, ov=False):
        x0, y0, x1, y1 = face_rect(part, face, ov)
        self.a[y0:y1, x0:x1] = C(c)

    def fill_part(self, part, c, ov=False, faces=FACES):
        for f in faces:
            self.fill(part, f, c, ov)

    def face_ascii(self, part, face, rows, pal, ov=False):
        x0, y0, x1, y1 = face_rect(part, face, ov)
        if len(rows) != y1 - y0 or any(len(r) != x1 - x0 for r in rows):
            raise ValueError(f'{part}/{face} expects {y1 - y0} rows of {x1 - x0}')
        self.ascii(rows, pal, x0, y0)

    # side strip -----------------------------------------------------------
    def strip_rect(self, part, ov=False):
        x0, y0, _, y1 = face_rect(part, 'right', ov)
        _, _, x1, _ = face_rect(part, 'back', ov)
        return x0, y0, x1, y1

    def strip_width(self, part):
        x0, _, x1, _ = self.strip_rect(part)
        return x1 - x0

    def sp(self, part, i, y, c, ov=False):
        """Pixel on the wrap-around side strip (i is taken modulo the strip width)."""
        x0, y0, x1, y1 = self.strip_rect(part, ov)
        if 0 <= y < y1 - y0:
            self.set(x0 + i % (x1 - x0), y0 + y, c)

    def sclear(self, part, i, y, ov=False):
        x0, y0, x1, y1 = self.strip_rect(part, ov)
        if 0 <= y < y1 - y0:
            self.clear(x0 + i % (x1 - x0), y0 + y)

    def band(self, part, y0, y1, c, ov=False):
        """Fill strip rows y0..y1 inclusive (all four side faces)."""
        x0, sy0, x1, sy1 = self.strip_rect(part, ov)
        y0, y1 = max(0, y0), min(sy1 - sy0 - 1, y1)
        if y0 <= y1:
            self.a[sy0 + y0:sy0 + y1 + 1, x0:x1] = C(c)

    def sget(self, part, i, y, ov=False):
        x0, y0, x1, y1 = self.strip_rect(part, ov)
        return self.get(x0 + i % (x1 - x0), y0 + y)

    # limb mirroring ------------------------------------------------------
    def mirror_limb(self, src, dst, ov=False):
        """Copy a limb onto its opposite as a mirror image (outer->outer, inner->inner)."""
        pairs = {'front': 'front', 'back': 'back', 'top': 'top', 'bottom': 'bottom',
                 'right': 'left', 'left': 'right'}
        tmp = {}
        for sf, df in pairs.items():
            x0, y0, x1, y1 = face_rect(src, sf, ov)
            tmp[df] = self.a[y0:y1, x0:x1][:, ::-1].copy()
        for df, block in tmp.items():
            x0, y0, x1, y1 = face_rect(dst, df, ov)
            self.a[y0:y1, x0:x1] = block

    def mirror_head_lr(self, ov=False):
        """Make the head symmetric: left face := mirror(right face)."""
        x0, y0, x1, y1 = face_rect('head', 'right', ov)
        blk = self.a[y0:y1, x0:x1][:, ::-1].copy()
        x0, y0, x1, y1 = face_rect('head', 'left', ov)
        self.a[y0:y1, x0:x1] = blk


# --------------------------------------------------------------------------- compositing
def compose(layers):
    """Composite same-geometry layers in UV space (later layers win), like the
    game re-rendering the model with each texture in order."""
    out = np.zeros((64, 64, 4), dtype=np.uint8)
    for arr in layers:
        out = alpha_over(out, arr)
    return out


def tinted(arr, tint):
    return multiply(arr, tint) if tint is not None else arr


def _blit(dst, src, x, y):
    h, w = src.shape[:2]
    m = src[..., 3] > 0
    region = dst[y:y + h, x:x + w]
    region[m] = src[m]


def flat_view(tex, side='front'):
    """Flatten a skin into a 16x32 front (or back) silhouette, base then overlay."""
    out = np.zeros((32, 16, 4), dtype=np.uint8)
    if side == 'front':
        place = [('head', 4, 0), ('body', 4, 8), ('right_arm', 0, 8), ('left_arm', 12, 8),
                 ('right_leg', 4, 20), ('left_leg', 8, 20)]
    else:
        place = [('head', 4, 0), ('body', 4, 8), ('left_arm', 0, 8), ('right_arm', 12, 8),
                 ('left_leg', 4, 20), ('right_leg', 8, 20)]
    for ov in (False, True):
        for part, x, y in place:
            x0, y0, x1, y1 = face_rect(part, side, ov)
            _blit(out, tex[y0:y1, x0:x1], x, y)
    return out


# --------------------------------------------------------------------------- 3D renderer
_BOXES = {  # model-space boxes (pixels), y up, character faces +z
    'head': ((-4, 24, -4), (4, 32, 4)),
    'body': ((-4, 12, -2), (4, 24, 2)),
    'right_arm': ((-8, 12, -2), (-4, 24, 2)),
    'left_arm': ((4, 12, -2), (8, 24, 2)),
    'right_leg': ((-4, 0, -2), (0, 12, 2)),
    'left_leg': ((0, 0, -2), (4, 12, 2)),
}


def _face_geometry(lo, hi):
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    return {
        'front': ((x0, y1, z1), (w, 0, 0), (0, -h, 0), (0, 0, 1)),
        'back': ((x1, y1, z0), (-w, 0, 0), (0, -h, 0), (0, 0, -1)),
        'right': ((x0, y1, z0), (0, 0, d), (0, -h, 0), (-1, 0, 0)),
        'left': ((x1, y1, z1), (0, 0, -d), (0, -h, 0), (1, 0, 0)),
        'top': ((x0, y1, z0), (w, 0, 0), (0, 0, d), (0, 1, 0)),
        'bottom': ((x0, y0, z1), (w, 0, 0), (0, 0, -d), (0, -1, 0)),
    }


def _rot(p, yaw, pitch):
    x, y, z = p
    cy, sy = cos(yaw), sin(yaw)
    x, z = x * cy + z * sy, -x * sy + z * cy
    cp, sp = cos(pitch), sin(pitch)
    y, z = y * cp - z * sp, y * sp + z * cp
    return np.array((x, y, z), dtype=np.float64)


def render3d(tex, scale=8, yaw=28, pitch=10, bg=None, size=None):
    """Render the composited 64x64 texture on the standard player model.

    Orthographic, z-buffered, nearest-neighbour texels, simple directional light.
    Returns a PIL RGBA image.
    """
    yaw_r, pitch_r = radians(yaw), radians(pitch)
    W, H = size or (int(24 * scale), int(40 * scale))
    img = np.zeros((H, W, 4), dtype=np.uint8)
    if bg is not None:
        img[:, :] = C(bg)
    zbuf = np.full((H, W), -1e9)
    cx, cy = W / 2, H - 4 * scale
    light = np.array((-0.45, 0.75, 0.55))
    light /= np.linalg.norm(light)
    quads = []
    for part, (lo, hi) in _BOXES.items():
        for ov in (False, True):
            inf = (0.5 if part == 'head' else 0.25) if ov else 0.0
            lo2 = tuple(c - inf for c in lo)
            hi2 = tuple(c + inf for c in hi)
            for face, (p0, U, V, n) in _face_geometry(lo2, hi2).items():
                quads.append((part, face, ov, p0, U, V, n))
    for part, face, ov, p0, U, V, n in quads:
        nr = _rot(n, yaw_r, pitch_r)
        if nr[2] <= 1e-6:
            continue
        x0, y0, x1, y1 = face_rect(part, face, ov)
        sub = tex[y0:y1, x0:x1]
        if not np.any(sub[..., 3]):
            continue
        A = _rot(p0, yaw_r, pitch_r)
        Ue = _rot(U, yaw_r, pitch_r)
        Ve = _rot(V, yaw_r, pitch_r)
        a2 = np.array((cx + A[0] * scale, cy - A[1] * scale))
        u2 = np.array((Ue[0] * scale, -Ue[1] * scale))
        v2 = np.array((Ve[0] * scale, -Ve[1] * scale))
        corners = np.array([a2, a2 + u2, a2 + v2, a2 + u2 + v2])
        bx0, by0 = np.floor(corners.min(0)).astype(int)
        bx1, by1 = np.ceil(corners.max(0)).astype(int)
        bx0, by0 = max(bx0, 0), max(by0, 0)
        bx1, by1 = min(bx1, W), min(by1, H)
        if bx0 >= bx1 or by0 >= by1:
            continue
        M = np.array([[u2[0], v2[0]], [u2[1], v2[1]]])
        if abs(np.linalg.det(M)) < 1e-9:
            continue
        Mi = np.linalg.inv(M)
        yy, xx = np.mgrid[by0:by1, bx0:bx1]
        px = xx + 0.5 - a2[0]
        py = yy + 0.5 - a2[1]
        s = Mi[0, 0] * px + Mi[0, 1] * py
        t = Mi[1, 0] * px + Mi[1, 1] * py
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        fw, fh = x1 - x0, y1 - y0
        tu = np.clip((s * fw).astype(int), 0, fw - 1)
        tv = np.clip((t * fh).astype(int), 0, fh - 1)
        samp = sub[tv, tu]
        z = A[2] + s * Ue[2] + t * Ve[2] + (0.001 if ov else 0)
        zb = zbuf[by0:by1, bx0:bx1]
        keep = inside & (samp[..., 3] > 0) & (z > zb)
        f = 0.62 + 0.42 * max(0.0, float(nr @ light))
        col = samp.astype(np.float64)
        col[..., :3] = np.clip(col[..., :3] * f, 0, 255)
        region = img[by0:by1, bx0:bx1]
        region[keep] = col.astype(np.uint8)[keep]
        zb[keep] = z[keep]
    return Image.fromarray(img, 'RGBA')


# --------------------------------------------------------------------------- greyscale level maps
# Tinted layers (clothes, hair) are authored as integer "value levels" so that
# shading can be nudged up/down without caring about the final grey numbers.
LEVELS = (92, 108, 126, 146, 166, 188, 210, 232, 255)   # 0.36 .. 1.00
VERY_DARK = 46                                        # level -1: reads ~black after tint
TOP_LEVEL = len(LEVELS) - 1
CLEAR_LV = -9


class LevelSkin:
    """64x64 map of grey levels addressed like :class:`Skin`; -9 = transparent."""

    def __init__(self):
        self.m = np.full((64, 64), CLEAR_LV, dtype=np.int16)

    # access --------------------------------------------------------------
    def set(self, part, face, x, y, lv, ov=False):
        x0, y0, x1, y1 = face_rect(part, face, ov)
        if 0 <= x < x1 - x0 and 0 <= y < y1 - y0:
            self.m[y0 + y, x0 + x] = lv

    def get(self, part, face, x, y, ov=False):
        x0, y0, x1, y1 = face_rect(part, face, ov)
        if 0 <= x < x1 - x0 and 0 <= y < y1 - y0:
            return int(self.m[y0 + y, x0 + x])
        return CLEAR_LV

    def clear(self, part, face, x, y, ov=False):
        self.set(part, face, x, y, CLEAR_LV, ov)

    def shade(self, part, face, x, y, d, ov=False):
        v = self.get(part, face, x, y, ov)
        if v >= 0:
            self.set(part, face, x, y, max(0, min(TOP_LEVEL, v + d)), ov)

    def fill(self, part, face, lv, ov=False, rows=None, cols=None):
        x0, y0, x1, y1 = face_rect(part, face, ov)
        r0, r1 = rows if rows else (0, y1 - y0 - 1)
        c0, c1 = cols if cols else (0, x1 - x0 - 1)
        self.m[y0 + r0:y0 + r1 + 1, x0 + c0:x0 + c1 + 1] = lv

    def fill_part(self, part, lv, ov=False, faces=FACES):
        for f in faces:
            self.fill(part, f, lv, ov)

    def size(self, part, face):
        x0, y0, x1, y1 = face_rect(part, face)
        return x1 - x0, y1 - y0

    # strip (wrap-around side faces) ----------------------------------------
    def _strip(self, part, ov):
        x0, y0, _, y1 = face_rect(part, 'right', ov)
        _, _, x1, _ = face_rect(part, 'back', ov)
        return x0, y0, x1, y1

    def swidth(self, part):
        x0, _, x1, _ = self._strip(part, False)
        return x1 - x0

    def sp(self, part, i, y, lv, ov=False):
        x0, y0, x1, y1 = self._strip(part, ov)
        if 0 <= y < y1 - y0:
            self.m[y0 + y, x0 + i % (x1 - x0)] = lv

    def sg(self, part, i, y, ov=False):
        x0, y0, x1, y1 = self._strip(part, ov)
        if 0 <= y < y1 - y0:
            return int(self.m[y0 + y, x0 + i % (x1 - x0)])
        return CLEAR_LV

    def sshade(self, part, i, y, d, ov=False):
        v = self.sg(part, i, y, ov)
        if v >= 0:
            self.sp(part, i, y, max(0, min(TOP_LEVEL, v + d)), ov)

    def band(self, part, y0, y1, lv, ov=False):
        x0, sy0, x1, sy1 = self._strip(part, ov)
        y0, y1 = max(0, y0), min(sy1 - sy0 - 1, y1)
        if y0 <= y1:
            self.m[sy0 + y0:sy0 + y1 + 1, x0:x1] = lv

    def band_shade(self, part, y, d, ov=False):
        for i in range(self.swidth(part)):
            self.sshade(part, i, y, d, ov)

    # faces by role for limbs -------------------------------------------------
    def strip_index(self, part, face, x):
        """Strip index of local column x on a side face."""
        order = {'right': 0, 'front': 1, 'left': 2, 'back': 3}
        _, _, (w, h, d) = PARTS[part]
        widths = {'right': d, 'front': w, 'left': d, 'back': w}
        off = 0
        for f in SIDES:
            if f == face:
                return off + x
            off += widths[f]
        raise ValueError(face)

    # mirroring ---------------------------------------------------------------
    def mirror_limb(self, src, dst, ov=False):
        pairs = {'front': 'front', 'back': 'back', 'top': 'top', 'bottom': 'bottom',
                 'right': 'left', 'left': 'right'}
        tmp = {}
        for sf, df in pairs.items():
            x0, y0, x1, y1 = face_rect(src, sf, ov)
            tmp[df] = self.m[y0:y1, x0:x1][:, ::-1].copy()
        for df, blk in tmp.items():
            x0, y0, x1, y1 = face_rect(dst, df, ov)
            self.m[y0:y1, x0:x1] = blk

    def mirror_head(self, ov=False):
        x0, y0, x1, y1 = face_rect('head', 'right', ov)
        blk = self.m[y0:y1, x0:x1][:, ::-1].copy()
        x0, y0, x1, y1 = face_rect('head', 'left', ov)
        self.m[y0:y1, x0:x1] = blk

    def face_ascii(self, part, face, rows, ov=False, pal=None):
        """Rows of digits 0-8, 'x' very dark, '.' transparent, ' ' keep."""
        x0, y0, x1, y1 = face_rect(part, face, ov)
        if len(rows) != y1 - y0 or any(len(r) != x1 - x0 for r in rows):
            raise ValueError(f'{part}/{face} expects {y1 - y0} rows of {x1 - x0}')
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == ' ':
                    continue
                if pal and ch in pal:
                    lv = pal[ch]
                elif ch == '.':
                    lv = CLEAR_LV
                elif ch == 'x':
                    lv = -1
                else:
                    lv = int(ch)
                self.m[y0 + y, x0 + x] = lv

    def rgba(self):
        out = np.zeros((64, 64, 4), dtype=np.uint8)
        for lv, g in enumerate(LEVELS):
            sel = self.m == lv
            out[sel] = (g, g, g, 255)
        sel = self.m == -1
        out[sel] = (VERY_DARK, VERY_DARK, VERY_DARK, 255)
        return out
