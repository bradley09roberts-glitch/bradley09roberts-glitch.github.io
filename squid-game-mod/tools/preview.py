#!/usr/bin/env python3
"""Renders a .sqbuf block-buffer dump (from `./gradlew dumpArena`) to PNG images so structures can
be inspected without launching Minecraft.

Views:  top    - top-down map, height shaded, regions/markers overlaid
        front  - orthographic view looking north (-z) from the south, x horizontal
        side   - orthographic view looking west (-x) from the east, z horizontal
        iso    - 2:1 dimetric 3D view from the south-east (surface voxels, 3 face shades)
        slice  - horizontal slice at --slice-y (floor plan of one storey)

Examples:
  python3 tools/preview.py tools/out/red_light.sqbuf --views top,iso --out tools/out/preview
  python3 tools/preview.py tools/out/hub.sqbuf --views iso --clip=-40,40,0,40,-35,35 --scale 3
  python3 tools/preview.py tools/out/hub.sqbuf --views slice --slice-y 70 --clip=-40,40,0,0,-35,35

--clip=x0,x1,y0,y1,z0,z1 (use the = form: values may be negative) is in the arena's LOCAL coordinates (the
same numbers you pass to BuildContext) when the file is named after an arena (red_light.sqbuf ...); add --world to
use world coordinates. y0=y1=0 means "all y".
Marker names are printed in a legend; unknown block ids are listed on stderr.
"""
import argparse
import colorsys
import hashlib
import json
import os
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "squidgame", "textures", "block")


# --------------------------------------------------------------------------- reading

class Reader:
    def __init__(self, data):
        self.d = data
        self.p = 0

    def i32(self):
        v = struct.unpack_from(">i", self.d, self.p)[0]
        self.p += 4
        return v

    def utf(self):
        n = struct.unpack_from(">H", self.d, self.p)[0]
        self.p += 2
        s = self.d[self.p:self.p + n].decode("utf-8", "replace")
        self.p += n
        return s

    def shorts(self, n):
        a = np.frombuffer(self.d, dtype=">i2", count=n, offset=self.p).astype(np.int16)
        self.p += 2 * n
        return a


def load(path):
    r = Reader(open(path, "rb").read())
    assert r.i32() == 0x53514246, "not a .sqbuf file"
    r.i32()
    palette = [r.utf() for _ in range(r.i32())]
    secs = []
    for _ in range(r.i32()):
        sx, sy, sz = r.i32(), r.i32(), r.i32()
        secs.append((sx, sy, sz, r.shorts(4096)))
    meta = [r.utf() for _ in range(r.i32())]
    return palette, secs, meta


# --------------------------------------------------------------------------- colours

def block_colors(palette):
    vanilla = json.load(open(os.path.join(HERE, "block_colors.json")))
    cols = np.zeros((len(palette), 4), dtype=np.float32)  # r,g,b,alpha(opacity)
    flags = np.zeros(len(palette), dtype=np.uint8)  # 1 = air, 2 = debug (invisible helper)
    unknown = set()
    for i, st in enumerate(palette):
        if i == 0 or st == "":
            flags[i] = 1
            continue
        bid = st.split("[")[0]
        if bid in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void"):
            flags[i] = 1
            continue
        if bid in ("minecraft:light", "minecraft:barrier", "squidgame:invisible_wall"):
            cols[i] = (1.0, 0.0, 1.0, 0.25)
            flags[i] = 2
            continue
        c = None
        if bid in vanilla:
            h = vanilla[bid]["c"].lstrip("#")
            c = (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), vanilla[bid].get("a", 1.0))
        elif bid.startswith("squidgame:"):
            name = bid.split(":")[1]
            base = name
            for suf in ("_stairs", "_slab"):
                if base.endswith(suf):
                    base = base[: -len(suf)]
            for cand in (base, base + "_top", base + "_side", base + "_screen", base + "_case"):
                p = os.path.join(ASSETS, cand + ".png")
                if os.path.exists(p):
                    im = np.asarray(Image.open(p).convert("RGBA"), dtype=np.float64)
                    if im.shape[0] > im.shape[1]:
                        im = im[: im.shape[1]]
                    m = im[..., 3] > 40
                    if m.any():
                        rgb = im[..., :3][m].mean(axis=0)
                        c = (rgb[0], rgb[1], rgb[2], float(im[..., 3][m].mean() / 255.0 * m.mean()))
                    break
        if c is None:
            unknown.add(bid)
            hsh = hashlib.md5(bid.encode()).digest()
            r, g, b = colorsys.hsv_to_rgb(hsh[0] / 255.0, 0.5, 0.9)
            c = (r * 255, g * 255, b * 255, 1.0)
        cols[i] = (c[0], c[1], c[2], 1.0 if c[3] > 0.6 else max(0.25, c[3]))
    if unknown:
        print("unknown blocks (hash colours):", ", ".join(sorted(unknown)), file=sys.stderr)
    return cols, flags


# --------------------------------------------------------------------------- grid

def build_grid(secs, palette, clip):
    air_ids = {i for i, s in enumerate(palette) if i == 0 or s.split("[")[0] in
               ("minecraft:air", "minecraft:cave_air", "minecraft:void_air")}
    xs0 = min(s[0] for s in secs) * 16
    ys0 = min(s[1] for s in secs) * 16
    zs0 = min(s[2] for s in secs) * 16
    xs1 = (max(s[0] for s in secs) + 1) * 16
    ys1 = (max(s[1] for s in secs) + 1) * 16
    zs1 = (max(s[2] for s in secs) + 1) * 16
    if clip:
        x0, x1, y0, y1, z0, z1 = clip
        X0, X1 = max(xs0, x0), min(xs1, x1 + 1)
        Z0, Z1 = max(zs0, z0), min(zs1, z1 + 1)
        if y0 == 0 and y1 == 0:
            Y0, Y1 = ys0, ys1
        else:
            Y0, Y1 = max(ys0, y0), min(ys1, y1 + 1)
    else:
        X0, X1, Y0, Y1, Z0, Z1 = xs0, xs1, ys0, ys1, zs0, zs1
    if (X1 - X0) * (Y1 - Y0) * (Z1 - Z0) > 220_000_000:
        sys.exit("region too large (%d x %d x %d); use --clip" % (X1 - X0, Y1 - Y0, Z1 - Z0))
    grid = np.zeros((X1 - X0, Y1 - Y0, Z1 - Z0), dtype=np.int16)
    for sx, sy, sz, arr in secs:
        bx, by, bz = sx * 16, sy * 16, sz * 16
        if bx + 16 <= X0 or bx >= X1 or by + 16 <= Y0 or by >= Y1 or bz + 16 <= Z0 or bz >= Z1:
            continue
        a = arr.reshape(16, 16, 16)  # [y][z][x]
        a = np.transpose(a, (2, 0, 1))  # [x][y][z]
        lx0, lx1 = max(bx, X0), min(bx + 16, X1)
        ly0, ly1 = max(by, Y0), min(by + 16, Y1)
        lz0, lz1 = max(bz, Z0), min(bz + 16, Z1)
        grid[lx0 - X0:lx1 - X0, ly0 - Y0:ly1 - Y0, lz0 - Z0:lz1 - Z0] = a[lx0 - bx:lx1 - bx, ly0 - by:ly1 - by, lz0 - bz:lz1 - bz]
    return grid, (X0, Y0, Z0), air_ids


def parse_meta(meta):
    markers, regions, ents = [], [], []
    for line in meta:
        p = line.split("\t")
        if p[0] == "M":
            markers.append((p[1], float(p[2]), float(p[3]), float(p[4])))
        elif p[0] == "R":
            regions.append((p[1],) + tuple(int(v) for v in p[2:8]))
        elif p[0] == "E":
            ents.append((p[1], float(p[2]), float(p[3]), float(p[4])))
    return markers, regions, ents


def name_color(name):
    h = hashlib.md5(name.encode()).digest()
    r, g, b = colorsys.hsv_to_rgb(h[0] / 255.0, 0.9, 1.0)
    return int(r * 255), int(g * 255), int(b * 255)


# --------------------------------------------------------------------------- views

def shade(rgb, f):
    return np.clip(rgb * f, 0, 255)


def view_top(grid, origin, cols, flags, scale, markers, regions, args):
    X, Y, Z = grid.shape
    solid = (flags[grid] == 0)
    any_solid = solid.any(axis=1)
    top = np.where(any_solid, Y - 1 - np.argmax(solid[:, ::-1, :], axis=1), -1)  # [x][z]
    idx = np.take_along_axis(grid, np.clip(top, 0, None)[:, None, :], axis=1)[:, 0, :]
    rgb = cols[idx][..., :3].copy()
    hfrac = np.where(top >= 0, top / max(1, Y - 1), 0.0)
    f = 0.62 + 0.45 * hfrac
    # simple relief shading: darker when the north/west neighbour is higher
    t = np.where(top >= 0, top, 0).astype(np.int32)
    dn = np.zeros_like(t)
    dn[1:, :] = np.maximum(0, t[:-1, :] - t[1:, :])
    dw = np.zeros_like(t)
    dw[:, 1:] = np.maximum(0, t[:, :-1] - t[:, 1:])
    f = f - 0.04 * np.minimum(dn + dw, 6)
    rgb = rgb * f[..., None]
    rgb[top < 0] = (12, 12, 18)
    img = np.transpose(np.clip(rgb, 0, 255).astype(np.uint8), (1, 0, 2))  # rows = z, cols = x
    im = Image.fromarray(img, "RGB").resize((X * scale, Z * scale), Image.NEAREST)
    d = ImageDraw.Draw(im)
    ox, oy, oz = origin
    for (n, x1, y1, z1, x2, y2, z2) in regions:
        if args.regions:
            d.rectangle([(x1 - ox) * scale, (z1 - oz) * scale, (x2 + 1 - ox) * scale, (z2 + 1 - oz) * scale],
                        outline=name_color(n) + (255,))
    for (n, x, y, z) in markers:
        px, pz = (x - ox) * scale, (z - oz) * scale
        if 0 <= px < im.width and 0 <= pz < im.height:
            c = name_color(n)
            r = max(2, scale // 2)
            d.ellipse([px - r, pz - r, px + r, pz + r], fill=c, outline=(0, 0, 0))
    return im


def view_ortho(grid, origin, cols, flags, scale, axis):
    X, Y, Z = grid.shape
    solid = (flags[grid] == 0)
    if axis == "front":  # look along -z from the south: nearest = largest z
        any_s = solid.any(axis=2)
        depth = np.where(any_s, Z - 1 - np.argmax(solid[:, :, ::-1], axis=2), -1)  # [x][y]
        idx = np.take_along_axis(grid, np.clip(depth, 0, None)[:, :, None], axis=2)[:, :, 0]
        w, h = X, Y
        fdepth = depth / max(1, Z - 1)
    else:  # side: look along -x from the east
        any_s = solid.any(axis=0)
        depth = np.where(any_s, X - 1 - np.argmax(solid[::-1, :, :], axis=0), -1)  # [y][z]
        idx = np.take_along_axis(grid, np.clip(depth, 0, None)[None, :, :], axis=0)[0]
        idx = idx.T  # -> [z][y]
        depth = depth.T
        any_s = any_s.T
        w, h = Z, Y
        fdepth = depth / max(1, X - 1)
    rgb = cols[idx][..., :3] * (0.6 + 0.4 * np.clip(fdepth, 0, 1))[..., None]
    rgb[~any_s] = (14, 14, 22)
    img = np.transpose(np.clip(rgb, 0, 255).astype(np.uint8), (1, 0, 2))[::-1]  # rows = y (flip so up is up)
    return Image.fromarray(img, "RGB").resize((w * scale, h * scale), Image.NEAREST)


def view_slice(grid, origin, cols, flags, scale, y_world, markers):
    X, Y, Z = grid.shape
    ox, oy, oz = origin
    yy = y_world - oy
    if not (0 <= yy < Y):
        sys.exit("slice-y outside the clipped region")
    layer = grid[:, yy, :]
    below = grid[:, max(0, yy - 1), :]
    rgb = cols[layer][..., :3].copy()
    air = flags[layer] == 1
    rgb[air] = cols[below][air][..., :3] * 0.45
    rgb[air & (flags[below] == 1)] = (10, 10, 16)
    img = np.transpose(np.clip(rgb, 0, 255).astype(np.uint8), (1, 0, 2))
    im = Image.fromarray(img, "RGB").resize((X * scale, Z * scale), Image.NEAREST)
    d = ImageDraw.Draw(im)
    for (n, x, y, z) in markers:
        if abs(y - y_world) <= 3:
            px, pz = (x - ox) * scale, (z - oz) * scale
            r = max(2, scale // 2)
            d.ellipse([px - r, pz - r, px + r, pz + r], fill=name_color(n), outline=(0, 0, 0))
    return im


def make_sprite(k):
    W, H = 2 * k, k
    V = max(2, int(round(2.2 * k)))
    bw, bh = 2 * W, 2 * H + V
    face = np.zeros((bh, bw), dtype=np.int8)  # 0 none 1 top 2 left 3 right
    for py in range(bh):
        for px in range(bw):
            x = px - W + 0.5
            y = py - H + 0.5  # relative to top-rhombus centre
            if abs(x) / W + abs(y) / H <= 1.0:
                face[py, px] = 1
            elif -W <= x < 0:
                yt = H * (x + W) / W
                if yt <= y < yt + V:
                    face[py, px] = 2
            elif 0 <= x < W:
                yt = H * (1 - x / W)
                if yt <= y < yt + V:
                    face[py, px] = 3
    return face, W, H, V


def view_iso(grid, origin, cols, flags, scale, markers, args):
    X, Y, Z = grid.shape
    opaque = (flags[grid] == 0) & (cols[grid][..., 3] > 0.6)
    solid = (flags[grid] != 1)
    # surface voxels: any face toward the camera (+x, +y, +z) not covered by an opaque neighbour
    ex = np.ones_like(solid)
    ex[:-1, :, :] = ~opaque[1:, :, :]
    ey = np.ones_like(solid)
    ey[:, :-1, :] = ~opaque[:, 1:, :]
    ez = np.ones_like(solid)
    ez[:, :, :-1] = ~opaque[:, :, 1:]
    vis = solid & (ex | ey | ez)
    xs, ys, zs = np.nonzero(vis)
    n = len(xs)
    if n == 0:
        return None
    k = scale
    face, W, H, V = make_sprite(k)
    bh, bw = face.shape
    sx = (xs - zs).astype(np.int64) * 2 * k
    sy = ((xs + zs) * k - ys * int(round(2.2 * k))).astype(np.int64)
    minx, miny = sx.min() - W, sy.min() - H
    width = int(sx.max() + W - minx + 2)
    height = int(sy.max() + H + V - miny + 2)
    if width * height > 140_000_000:
        sys.exit("iso image too large (%dx%d); use --clip or a smaller --scale" % (width, height))
    canvas = np.zeros((height, width, 3), dtype=np.float32)
    canvas[:] = (14, 14, 22)
    ids = grid[xs, ys, zs]
    base = cols[ids][..., :3]
    alpha = cols[ids][..., 3]
    d = xs + ys + zs
    order = np.argsort(d, kind="stable")
    xs, ys, zs, sx, sy, base, alpha, d = (a[order] for a in (xs, ys, zs, sx, sy, base, alpha, d))
    # group boundaries per diagonal slice
    bounds = np.flatnonzero(np.diff(d)) + 1
    starts = np.concatenate(([0], bounds))
    ends = np.concatenate((bounds, [len(d)]))
    py_idx, px_idx = np.nonzero(face)
    fvals = face[py_idx, px_idx]
    mult = np.array([0.0, 1.08, 0.82, 0.62], dtype=np.float32)[fvals]
    for s, e in zip(starts, ends):
        cx = (sx[s:e] - minx - W).astype(np.int64)
        cy = (sy[s:e] - miny - H).astype(np.int64)
        col = base[s:e]
        al = alpha[s:e]
        for j in range(len(py_idx)):
            yy = cy + py_idx[j]
            xx = cx + px_idx[j]
            c = col * mult[j]
            if (al < 0.99).any():
                old = canvas[yy, xx]
                c = np.where((al < 0.99)[:, None], old * (1 - al[:, None]) + c * al[:, None], c)
            canvas[yy, xx] = c
    im = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8), "RGB")
    ox, oy, oz = origin
    dr = ImageDraw.Draw(im)
    if args.markers:
        for (nme, x, y, z) in markers:
            mx, my, mz = x - ox, y - oy, z - oz
            px = (mx - mz) * 2 * k - minx
            py = (mx + mz) * k - my * int(round(2.2 * k)) - miny
            r = max(2, k)
            if 0 <= px < width and 0 <= py < height:
                dr.ellipse([px - r, py - r, px + r, py + r], fill=name_color(nme), outline=(0, 0, 0))
    return im


# --------------------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("file")
    ap.add_argument("--views", default="top,iso")
    ap.add_argument("--out", default=None)
    ap.add_argument("--scale", type=int, default=0)
    ap.add_argument("--clip", default=None)
    ap.add_argument("--slice-y", type=int, default=None, help="world Y for the slice view")
    ap.add_argument("--world", action="store_true", help="interpret --clip / --slice-y in world coordinates")
    ap.add_argument("--no-markers", dest="markers", action="store_false")
    ap.add_argument("--regions", action="store_true", help="outline regions in the top view")
    args = ap.parse_args()

    palette, secs, meta = load(args.file)
    markers, regions, ents = parse_meta(meta)
    clip = tuple(int(v) for v in args.clip.split(",")) if args.clip else None
    origins = {"hub": (0, 64, 0), "red_light": (1000, 64, 0), "dalgona": (2000, 64, 0), "tug_of_war": (3000, 64, 0),
               "marbles": (4000, 64, 0), "glass_bridge": (5000, 64, 0), "final": (6000, 64, 0)}
    stem = os.path.splitext(os.path.basename(args.file))[0]
    org = None if args.world else origins.get(stem)
    if org and clip:
        ox, oy, oz = org
        y0, y1 = clip[2], clip[3]
        clip = (clip[0] + ox, clip[1] + ox, (y0 + oy) if not (y0 == 0 and y1 == 0) else 0,
                (y1 + oy) if not (y0 == 0 and y1 == 0) else 0, clip[4] + oz, clip[5] + oz)
    if org and args.slice_y is not None:
        args.slice_y += org[1]
    cols, flags = block_colors(palette)
    grid, origin, _ = build_grid(secs, palette, clip)
    X, Y, Z = grid.shape
    out = args.out or os.path.splitext(args.file)[0] + "_preview"
    os.makedirs(out, exist_ok=True)
    base = os.path.splitext(os.path.basename(args.file))[0]
    print("grid", grid.shape, "origin", origin, "markers", len(markers), "regions", len(regions))
    if markers:
        names = {}
        for m in markers:
            names[m[0]] = names.get(m[0], 0) + 1
        print("markers:", ", ".join("%s x%d" % (k, v) for k, v in sorted(names.items())))
    for v in args.views.split(","):
        v = v.strip()
        sc = args.scale or max(1, min(8, int(1600 / max(X, Z))))
        if v == "top":
            im = view_top(grid, origin, cols, flags, sc, markers, regions, args)
        elif v in ("front", "side"):
            sc2 = args.scale or max(1, min(8, int(1600 / max(X if v == "front" else Z, Y))))
            im = view_ortho(grid, origin, cols, flags, sc2, v)
        elif v == "slice":
            if args.slice_y is None:
                sys.exit("--slice-y required")
            im = view_slice(grid, origin, cols, flags, sc, args.slice_y, markers)
        elif v == "iso":
            k = args.scale or max(1, min(6, int(2400 / ((X + Z) * 4))))
            im = view_iso(grid, origin, cols, flags, k, markers, args)
        else:
            print("unknown view", v)
            continue
        if im is None:
            print("nothing to draw for", v)
            continue
        path = os.path.join(out, "%s_%s.png" % (base, v))
        im.save(path)
        print("wrote", path, im.size)


if __name__ == "__main__":
    main()
