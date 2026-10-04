#!/usr/bin/env python3
"""Validate the generated doll assets against docs/ASSET_CONTRACT.md section 1.3 and GeckoLib's loader rules.

    python3 tools/assets/models/doll/validate_doll.py        (exit code 0 = pass)

Checks: JSON parses / bakes with the GeckoLib port, required bones + hierarchy, required animations + loop
types + lengths, UVs inside the texture, no NaN / inf, animation bones exist, easing names known, keyframe
times ascending, loops close (value at 0 == value at length), pose anchors between animations match (no pops),
the turn animations are real servo ratchets (3-4 pauses + overshoot, ring visibly rotates), the glow mask only
covers the eye_on cubes, the texture is 0/255 alpha, size / bounds, and near-coplanar overlapping faces that
would z-fight at distance.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True
sys.path.insert(0, str(HERE))

import numpy as np
from PIL import Image

import gl_model as gl

ASSETS = HERE.parents[3] / "src" / "main" / "resources" / "assets" / "squidgame"
GEO = ASSETS / "geo" / "entity" / "doll.geo.json"
ANIM = ASSETS / "animations" / "entity" / "doll.animation.json"
TEX = ASSETS / "textures" / "entity" / "doll.png"
GLOW = ASSETS / "textures" / "entity" / "doll_glowmask.png"

REQUIRED_PARENTS = {
    "root": None, "base": "root", "body": "base", "head": "body", "hair": "head", "eyes_off": "head", "eyes_on": "head",
    "mouth": "head", "left_arm": "body", "right_arm": "body", "dress": "body", "neck_joint": "body",
}
REQUIRED_ANIMS = {   # name: (loop, length_seconds)
    "dormant": (True, None), "wake": (False, 2.0), "idle_tree": (True, 4.0), "turn_to_players": (False, 1.0),
    "idle_players": (True, 3.0), "scan_players": (True, 2.0), "lock_on": (False, 0.4), "turn_to_tree": (False, 1.0),
}
PRE = "animation.doll."

errors: list[str] = []
warnings: list[str] = []
info: list[str] = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def finite(x) -> bool:
    return isinstance(x, (int, float)) and math.isfinite(x)


# ---------------------------------------------------------------------------------------------
def check_geo(doc, tex_size):
    if doc.get("format_version") != "1.12.0":
        err("geo format_version must be 1.12.0")
    geos = doc.get("minecraft:geometry")
    if not geos or len(geos) != 1:
        err("expected exactly one minecraft:geometry entry")
        return None
    g = geos[0]
    d = g["description"]
    if d.get("identifier") != "geometry.squidgame.doll":
        err("identifier must be geometry.squidgame.doll, got %r" % d.get("identifier"))
    tw, th = d.get("texture_width"), d.get("texture_height")
    if (tw, th) != (256, 256):
        err("texture size must be 256x256, got %sx%s" % (tw, th))
    if tex_size != (256, 256):
        err("doll.png must be 256x256, is %s" % (tex_size,))
    bones = g["bones"]
    names = [b["name"] for b in bones]
    if len(set(names)) != len(names):
        err("duplicate bone names")
    by = {b["name"]: b for b in bones}
    for n, parent in REQUIRED_PARENTS.items():
        if n not in by:
            err("required bone missing: " + n)
        elif by[n].get("parent") != parent:
            err("bone %s must have parent %r (has %r)" % (n, parent, by[n].get("parent")))
    for b in bones:
        p = b.get("parent")
        if p is not None and p not in by:
            err("bone %s has unknown parent %s" % (b["name"], p))
    # UV / numeric checks
    ncubes = 0
    for b in bones:
        for key in ("pivot", "rotation"):
            if key in b and not all(finite(v) for v in b[key]):
                err("bone %s has non-finite %s" % (b["name"], key))
        for c in b.get("cubes", []):
            ncubes += 1
            for key in ("origin", "size", "pivot", "rotation"):
                if key in c and (len(c[key]) != 3 or not all(finite(v) for v in c[key])):
                    err("bone %s cube has bad %s %r" % (b["name"], key, c[key]))
            if any(s < 0 for s in c["size"]):
                err("bone %s cube has negative size" % b["name"])
            if "rotation" in c and "pivot" not in c:
                err("bone %s: rotated cube without explicit pivot (GeckoLib would rotate about the model origin)" % b["name"])
            uv = c.get("uv")
            if not isinstance(uv, dict):
                err("bone %s: cube must use per-face uv" % b["name"])
                continue
            if not uv:
                err("bone %s: cube without any face" % b["name"])
            for face, f in uv.items():
                if face not in ("north", "south", "east", "west", "up", "down"):
                    err("bone %s: bad face name %s" % (b["name"], face))
                u, v = f["uv"]
                w, h = f["uv_size"]
                if not all(finite(x) for x in (u, v, w, h)):
                    err("bone %s face %s non-finite uv" % (b["name"], face))
                if u < 0 or v < 0 or w <= 0 or h <= 0 or u + w > tw + 1e-6 or v + h > th + 1e-6:
                    err("bone %s face %s uv rect (%s,%s,%s,%s) outside the %dx%d texture" % (b["name"], face, u, v, w, h, tw, th))
    info.append("geo: %d bones, %d cubes" % (len(bones), ncubes))
    return g


def check_face_coverage(geo, tex):
    """Every face must map onto at least some opaque texels (a fully transparent face is a UV mistake)."""
    a = tex[..., 3]
    bad = 0
    for b in geo["bones"]:
        for c in b.get("cubes", []):
            for face, f in c["uv"].items():
                u, v = f["uv"]
                w, h = f["uv_size"]
                sl = a[int(math.floor(v)):int(math.ceil(v + h)), int(math.floor(u)):int(math.ceil(u + w))]
                if sl.size == 0 or not (sl > 0).any():
                    bad += 1
                    err("bone %s face %s maps onto fully transparent texels" % (b["name"], face))
    return bad


# ---------------------------------------------------------------------------------------------
def check_anims(doc, geo_bones, model):
    anims = doc.get("animations")
    if not isinstance(anims, dict):
        err("animation file needs an 'animations' object")
        return {}
    for name, (loop, length) in REQUIRED_ANIMS.items():
        key = PRE + name
        if key not in anims:
            err("required animation missing: " + key)
            continue
        a = anims[key]
        lp = a.get("loop", False)
        if loop and lp is not True:
            err("%s must loop (loop: true)" % key)
        if not loop and lp not in (False, None, "hold_on_last_frame"):
            err("%s must be a one-shot" % key)
        if length is not None and abs(a.get("animation_length", -1) - length) > 1e-6:
            err("%s animation_length must be %.2f (is %s)" % (key, length, a.get("animation_length")))
    parsed = {}
    try:
        parsed = gl.parse_animations(doc)
    except Exception as e:      # the real loader would log + drop the animation
        err("animation file does not parse with the GeckoLib port: %r" % (e,))
        return {}
    for key, a in anims.items():
        L = a.get("animation_length")
        if not finite(L) or L <= 0:
            err("%s: bad animation_length" % key)
            continue
        for bname, b in a.get("bones", {}).items():
            if bname not in geo_bones:
                err("%s animates unknown bone %s" % (key, bname))
            for ch in b:
                if ch not in ("rotation", "position", "scale"):
                    err("%s/%s: unknown channel %s" % (key, bname, ch))
            for ch, val in b.items():
                _check_channel(key, bname, ch, val, L, a.get("loop") is True)
    return parsed


def _check_channel(key, bone, ch, val, L, is_loop):
    where = "%s/%s/%s" % (key, bone, ch)
    if isinstance(val, list):
        if len(val) != 3 or not all(finite(x) for x in val):
            err(where + ": bad constant vector")
        return
    if not isinstance(val, dict) or not val:
        err(where + ": expected keyframe object")
        return
    times, vecs = [], []
    for k, v in val.items():
        try:
            t = float(k)
        except ValueError:
            err(where + ": bad time key %r" % k)
            continue
        if "e" in k.lower():
            err(where + ": scientific notation in time key %r" % k)
        times.append(t)
        if isinstance(v, dict):
            vec = v.get("vector")
            easing = v.get("easing")
            if easing is not None and str(easing).lower() not in gl.EASINGS:
                err(where + ": unknown easing %r (GeckoLib would silently fall back to linear)" % easing)
            if set(v) - {"vector", "easing", "easingArgs"}:
                err(where + ": unexpected keyframe fields %s" % sorted(set(v) - {"vector", "easing", "easingArgs"}))
        else:
            vec = v
        if not isinstance(vec, list) or len(vec) != 3 or not all(finite(x) for x in vec):
            err(where + ": bad vector at %s" % k)
            continue
        vecs.append(vec)
        if ch == "scale" and any(x <= 0 for x in vec):
            err(where + ": non-positive scale at %s" % k)
    if times != sorted(times):
        err(where + ": keyframe times not ascending")
    if len(set(times)) != len(times):
        err(where + ": duplicate keyframe times")
    if times and times[0] != 0.0:
        err(where + ": first keyframe must be at t=0 (is %s)" % times[0])
    if times and times[-1] > L + 1e-6:
        err(where + ": keyframe beyond animation_length (%.3f > %.3f)" % (times[-1], L))
    if is_loop and vecs:
        if abs(times[-1] - L) > 1e-6:
            err(where + ": loop must end with a keyframe at t=length (last at %.3f)" % times[-1])
        if max(abs(a - b) for a, b in zip(vecs[0], vecs[-1])) > 1e-3:
            err(where + ": loop does not close (first %s vs last %s)" % (vecs[0], vecs[-1]))


# ---------------------------------------------------------------------------------------------
def sample_bone(model, anim, bone, t):
    """Authoring-space (degrees, as in the JSON) rotation of a bone at time t, using the GeckoLib sampler."""
    ba = anim.bones.get(bone)
    if ba is None:
        return np.zeros(3), np.zeros(3), np.ones(3)
    tick = t * 20.0
    r = gl.sample_stack(ba.rot, tick)
    p = gl.sample_stack(ba.pos, tick)
    s = gl.sample_stack(ba.scale, tick)
    rot = np.zeros(3) if r is None else np.array([-math.degrees(r[0]), -math.degrees(r[1]), math.degrees(r[2])])
    return rot, (np.zeros(3) if p is None else p), (np.ones(3) if s is None else s)


def pose_vec(model, anim, t):
    out = {}
    for b in model.bones:
        out[b] = sample_bone(model, anim, b, t)
    return out


def check_anchors(model, parsed):
    def P(name, t):
        return pose_vec(model, parsed[PRE + name], t)

    def cmp(a, b, label, tol_rot=0.06, tol_pos=0.03, tol_scale=0.01):
        for bone in model.bones:
            ra, pa, sa = a[bone]
            rb, pb, sb = b[bone]
            if np.max(np.abs(ra - rb)) > tol_rot:
                err("%s: bone %s rotation %s vs %s" % (label, bone, np.round(ra, 3), np.round(rb, 3)))
            if np.max(np.abs(pa - pb)) > tol_pos:
                err("%s: bone %s position %s vs %s" % (label, bone, np.round(pa, 3), np.round(pb, 3)))
            if np.max(np.abs(sa - sb)) > tol_scale:
                err("%s: bone %s scale %s vs %s" % (label, bone, np.round(sa, 3), np.round(sb, 3)))

    try:
        cmp(P("idle_tree", 0), P("turn_to_players", 0), "idle_tree(0) == turn_to_players(0)")
        cmp(P("turn_to_players", 1.0), P("idle_players", 0), "turn_to_players(end) == idle_players(0)")
        cmp(P("idle_players", 0), P("turn_to_tree", 0), "idle_players(0) == turn_to_tree(0)")
        cmp(P("turn_to_tree", 1.0), P("idle_tree", 0), "turn_to_tree(end) == idle_tree(0)")
        cmp(P("wake", 2.0), P("idle_tree", 0), "wake(end) == idle_tree(0)")
        cmp(P("dormant", 0), P("wake", 0), "dormant(0) == wake(0)")
        cmp(P("idle_players", 0), P("lock_on", 0), "idle_players(0) == lock_on(0)")
        cmp(P("idle_players", 0), P("scan_players", 0), "idle_players(0) == scan_players(0)", tol_rot=0.8)
    except KeyError as e:
        err("anchor check skipped, missing animation %s" % e)


def head_yaw_series(model, anim, dt=1 / 400.0):
    L = anim.length_ticks / 20.0
    ts = np.arange(0, L + 1e-9, dt)
    ys = np.array([sample_bone(model, anim, "head", t)[0][1] for t in ts])
    ring = np.array([sample_bone(model, anim, "neck_joint", t)[0][1] for t in ts])
    return ts, ys, ring


def count_pauses(ts, ys, lo, hi, thresh_deg_per_s=25.0, min_len=0.05):
    """Number of plateaus (|velocity| < thresh for >= min_len) with value strictly between lo and hi."""
    v = np.abs(np.gradient(ys, ts[1] - ts[0]))
    slow = v < thresh_deg_per_s
    pauses = []
    i = 0
    n = len(ts)
    while i < n:
        if slow[i]:
            j = i
            while j < n and slow[j]:
                j += 1
            if ts[min(j, n - 1)] - ts[i] >= min_len:
                mid = float(np.mean(ys[i:j]))
                if lo < mid < hi:
                    pauses.append((float(ts[i]), float(ts[min(j, n - 1)]), mid))
            i = j
        else:
            i += 1
    return pauses


def check_turns(model, parsed):
    for name, start, end in (("turn_to_players", 180.0, 0.0), ("turn_to_tree", 0.0, 180.0)):
        a = parsed.get(PRE + name)
        if a is None:
            continue
        ts, ys, ring = head_yaw_series(model, a)
        if abs(ys[0] - start) > 0.05:
            err("%s must start at head yaw %.0f (is %.2f)" % (name, start, ys[0]))
        if abs(ys[-1] - end) > 0.05:
            err("%s must end at head yaw %.0f (is %.2f)" % (name, end, ys[-1]))
        lo, hi = min(start, end) + 15, max(start, end) - 15
        pauses = count_pauses(ts, ys, lo, hi)
        if not 3 <= len(pauses) <= 4:
            err("%s: expected 3-4 ratchet pauses between the end poses, found %d %s" % (name, len(pauses), [(round(p[0], 2), round(p[1], 2), round(p[2])) for p in pauses]))
        over = (ys.max() - end) if end > start else (end - ys.min())      # how far the head runs past the final pose
        if not 2.0 <= over <= 14.0:
            err("%s: overshoot should be slight (2..14 deg), is %.1f" % (name, over))
        ring_travel = float(ring.max() - ring.min())
        if ring_travel < 60:
            err("%s: cog ring must visibly rotate (travel %.0f deg)" % (name, ring_travel))
        # monotonic apart from wind-up / overshoot settle: no more than 8 deg of backwards motion before the end
        info.append("%s: pauses at %s, overshoot %.1f deg, ring travel %.0f deg" % (
            name, [(round(p[0], 2), round(p[2])) for p in pauses], over, ring_travel))
    a = parsed.get(PRE + "scan_players")
    if a is not None:
        ts, ys, _ = head_yaw_series(model, a)
        if not (24.0 <= ys.max() <= 27.5 and -27.5 <= ys.min() <= -24.0):
            err("scan_players: head sweep should reach about +-25 deg, got %.1f..%.1f" % (ys.min(), ys.max()))
    a = parsed.get(PRE + "idle_tree")
    if a is not None:
        ts, ys, _ = head_yaw_series(model, a)
        if not (170 <= ys.min() and ys.max() <= 190 and abs(np.mean(ys) - 180) < 3):
            err("idle_tree: head should stay rotated ~180 deg (range %.1f..%.1f)" % (ys.min(), ys.max()))
    a = parsed.get(PRE + "idle_players")
    if a is not None:
        ts, ys, _ = head_yaw_series(model, a)
        if abs(ys).max() > 3.0:
            err("idle_players: head must face the field rigidly (max |yaw| %.1f)" % abs(ys).max())


# ---------------------------------------------------------------------------------------------
def check_textures(geo, tex, glow):
    if tex.shape[2] != 4:
        err("doll.png must be RGBA")
    if glow.shape != tex.shape:
        err("glowmask size %s != texture size %s" % (glow.shape, tex.shape))
        return
    a = tex[..., 3]
    if not np.all((a == 0) | (a == 255)):
        err("doll.png has semi-transparent texels (cutout rendering expects 0/255 alpha)")
    if np.any((a == 0) & (tex[..., :3].sum(axis=2) != 0)):
        warn("doll.png has invisible texels with non-zero RGB")
    ga = glow[..., 3]
    marked = (glow.astype(np.int32).sum(axis=2) != 0)
    if not marked.any():
        err("glow mask is empty (GeckoLib throws on an empty glow layer)")
    if np.any(marked & (ga == 0)):
        err("glow mask has texels with RGB but alpha 0 (GeckoLib would treat them as glowing at full alpha)")
    if np.any(marked & (a == 0)):
        err("glow mask marks fully transparent base texels")
    # footprint of every eye_on face
    foot = np.zeros(a.shape, dtype=bool)
    off = np.zeros(a.shape, dtype=bool)
    eyes_on_bones = {"eyes_on", "eye_on_l", "eye_on_r"}
    eyes_off_bones = {"eyes_off", "eye_off_l", "eye_off_r"}
    other = np.zeros(a.shape, dtype=bool)
    for b in geo["bones"]:
        for c in b.get("cubes", []):
            for f in c["uv"].values():
                u, v = f["uv"]
                w, h = f["uv_size"]
                sl = (slice(int(math.floor(v)), int(math.ceil(v + h))), slice(int(math.floor(u)), int(math.ceil(u + w))))
                if b["name"] in eyes_on_bones:
                    foot[sl] = True
                elif b["name"] in eyes_off_bones:
                    off[sl] = True
                else:
                    other[sl] = True
    if np.any(marked & ~foot):
        n = int((marked & ~foot).sum())
        err("%d glow texels outside the eye_on cubes' UV faces" % n)
    if np.any(marked & other):
        err("glow texels overlap UV regions used by non-eye cubes")
    if np.any(foot & other):
        err("eye_on UV regions are shared with other cubes (their texels would glow / vanish)")
    if np.any(foot & off):
        warn("eye_on and eye_off share texels")
    covered = foot & (a > 0)
    frac = float((marked & foot).sum()) / max(1, int(covered.sum()))
    info.append("glow: %d texels (%.0f%% of the opaque eye_on footprint)" % (int(marked.sum()), 100 * frac))
    # unused eye regions must contain no marked pixels, regular regions must be untouched
    if np.any(marked & (a == 0)):
        err("glow texel on transparent base")


# ---------------------------------------------------------------------------------------------
def _poly_area_clip(subject, clip):
    """Sutherland-Hodgman intersection area of two convex polygons (lists of (x, y))."""
    def inside(p, a, b):
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) >= -1e-12

    def inter(p1, p2, a, b):
        x1, y1, x2, y2 = p1[0], p1[1], p2[0], p2[1]
        x3, y3, x4, y4 = a[0], a[1], b[0], b[1]
        den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if abs(den) < 1e-14:
            return p2
        t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
        return (x1 + t * (x2 - x1), y1 + t * (y2 - y1))

    def orient(poly):
        s = sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1] for i in range(len(poly)))
        return poly if s >= 0 else poly[::-1]

    out = orient(subject)
    c = orient(clip)
    for i in range(len(c)):
        a, b = c[i], c[(i + 1) % len(c)]
        inp, out = out, []
        if not inp:
            break
        s = inp[-1]
        for e in inp:
            if inside(e, a, b):
                if not inside(s, a, b):
                    out.append(inter(s, e, a, b))
                out.append(e)
            elif inside(s, a, b):
                out.append(inter(s, e, a, b))
            s = e
    if len(out) < 3:
        return 0.0, []
    area = abs(sum(out[i][0] * out[(i + 1) % len(out)][1] - out[(i + 1) % len(out)][0] * out[i][1] for i in range(len(out)))) / 2
    return area, out


def check_zfight(model, tex, tol_px=0.55, min_area=0.2, eps_px=0.06):
    """Near-coplanar overlapping quads (|dist| < tol_px) whose overlap is EXPOSED (not buried inside / pressed
    against another cube) and whose texels differ would flicker at distance (24-bit depth buffer: ~0.2 px
    resolution at 100 blocks, ~0.8 px at 200)."""
    pose = gl.Pose(model)
    cubes = gl.world_quads_ex(model, pose)
    H, W = tex.shape[:2]
    texf = tex.astype(float)
    inv = [np.linalg.inv(c["m"]) for c in cubes]
    eps = eps_px / 16.0

    def exposed(ci, p, n):
        """p, n in world blocks; covered if p + eps*n lies inside another cube."""
        q = np.append(p + n * eps, 1.0)
        for cj, c in enumerate(cubes):
            if cj == ci:
                continue
            pl = (inv[cj] @ q)[:3]
            if np.all(pl >= c["cube"].lo - 1e-9) and np.all(pl <= c["cube"].hi + 1e-9):
                return False
        return True

    items = []
    for c in cubes:
        for (v, uv, n, q) in c["quads"]:
            v16 = v * 16.0
            items.append((c["bone"], v16, uv, n, q, v16[1] - v16[0], v16[3] - v16[0], c["cid"], v))
    found = 0
    flagged = []
    for i in range(len(items)):
        bi, vi, uvi, ni, qi, e1i, e3i, ci_, v_i = items[i]
        ui = e1i / (np.linalg.norm(e1i) + 1e-12)
        wi = np.cross(ni, ui)
        lo_i = vi.min(axis=0) - tol_px
        hi_i = vi.max(axis=0) + tol_px
        for j in range(i + 1, len(items)):
            bj, vj, uvj, nj, qj, e1j, e3j, cj_, v_j = items[j]
            if cj_ == ci_:
                continue
            if abs(float(ni @ nj)) < 0.9995:
                continue
            if np.any(vj.max(axis=0) < lo_i) or np.any(vj.min(axis=0) > hi_i):
                continue
            d = (vj - vi[0]) @ ni
            if np.max(np.abs(d)) > tol_px:
                continue
            si = [(float((p - vi[0]) @ ui), float((p - vi[0]) @ wi)) for p in vi]
            sj = [(float((p - vi[0]) @ ui), float((p - vi[0]) @ wi)) for p in vj]
            area, poly = _poly_area_clip(si, sj)
            if area < min_area:
                continue
            cx = sum(p[0] for p in poly) / len(poly)
            cy = sum(p[1] for p in poly) / len(poly)
            pts = [(cx, cy)] + [((p[0] + cx) / 2, (p[1] + cy) / 2) for p in poly]
            real = False
            same = True
            for (px, py) in pts:
                p3 = vi[0] + ui * px + wi * py
                p3j = p3 - float((p3 - vj[0]) @ nj) * nj           # the same spot on the other quad's plane
                if not (exposed(ci_, p3 / 16.0, ni) and exposed(cj_, p3j / 16.0, nj)):
                    continue
                real = True
                a_ = _texel(items[i][:7], p3, texf, W, H)
                b_ = _texel(items[j][:7], p3, texf, W, H)
                if a_ is None or b_ is None:
                    continue
                if np.max(np.abs(a_ - b_)) > 6:
                    same = False
                    break
            if not real:
                continue
            found += 1
            if not same:
                flagged.append((bi, bj, round(area, 1), round(float(np.max(np.abs(d))), 3), _desc(items[i][:7]), _desc(items[j][:7])))
    info.append("z-fight scan: %d exposed near-coplanar overlapping quad pairs, %d with differing texels" % (found, len(flagged)))
    for bi, bj, area, dist, di, dj in flagged[:40]:
        err("z-fight risk: %s / %s overlap %.1f px^2 at distance %.2f px with different texels  [%s | %s]" % (bi, bj, area, dist, di, dj))


def _desc(item):
    bone, v, uv, n, q, e1, e3 = item
    lo, hi = v.min(axis=0), v.max(axis=0)
    return "%s %s x%.1f..%.1f y%.1f..%.1f z%.1f..%.1f" % (bone, q.direction, lo[0], hi[0], lo[1], hi[1], lo[2], hi[2])


def _texel(item, p3, texf, W, H):
    bone, v, uv, n, q, e1, e3 = item
    d = p3 - v[0]
    s = float(d @ e1) / float(e1 @ e1)
    t = float(d @ e3) / float(e3 @ e3)
    if s < -1e-6 or s > 1 + 1e-6 or t < -1e-6 or t > 1 + 1e-6:
        return None
    u = uv[0][0] + s * (uv[1][0] - uv[0][0]) + t * (uv[3][0] - uv[0][0])
    vv = uv[0][1] + s * (uv[1][1] - uv[0][1]) + t * (uv[3][1] - uv[0][1])
    x = min(max(int(u * W), 0), W - 1)
    y = min(max(int(vv * H), 0), H - 1)
    return texf[y, x]


# ---------------------------------------------------------------------------------------------
def check_clearance(model, parsed, margin_px=0.6, samples=33):
    """Moving parts must not poke into parts they are not attached to (arms into the skirt, pigtails into the
    shoulders / sleeves, head into the collar).  Corner-in-volume test with a small margin, over every animation."""
    groups = {
        "arm": ("left_arm", "right_arm"),
        "skirt": ("dress",),
        "torso": ("body",),
        "pigtail": ("pigtail_l", "pigtail_r"),
        "head": ("head", "hair", "ribbon", "mouth", "eyes_off", "eyes_on", "eye_off_l", "eye_off_r", "eye_on_l", "eye_on_r"),
        "feet": ("base",),
    }
    bone_group = {b: g for g, bs in groups.items() for b in bs}
    pairs = [("arm", "skirt"), ("arm", "feet"), ("pigtail", "torso"), ("pigtail", "arm"), ("head", "torso")]
    # torso cubes that the head / pigtails may legitimately touch are none; the neck core is inside 'body' too, so only
    # test the head against the collar plate / bodice (y < 93) by restricting torso cubes to those below the neck.
    worst = {}
    margin = margin_px / 16.0
    for key, a in parsed.items():
        L = a.length_ticks / 20.0
        for t in np.linspace(0, L, samples):
            pose = gl.pose_at(model, a, min(t, L), loop_wrap=False)
            cubes = gl.world_quads_ex(model, pose)
            inv = [np.linalg.inv(c["m"]) for c in cubes]
            for ga, gb in pairs:
                for ca in cubes:
                    if bone_group.get(ca["bone"]) != ga:
                        continue
                    lo, hi = ca["cube"].lo, ca["cube"].hi
                    corners = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
                    wc = (np.c_[corners, np.ones(8)] @ ca["m"].T)[:, :3]
                    for cb in cubes:
                        if bone_group.get(cb["bone"]) != gb or cb["cid"] == ca["cid"]:
                            continue
                        if gb == "torso" and cb["cube"].lo[1] * 16 > 93:      # neck core: ignore, head sits on it
                            continue
                        pl = (np.c_[wc, np.ones(8)] @ inv[cb["cid"]].T)[:, :3]
                        inside = np.all(pl > cb["cube"].lo + margin, axis=1) & np.all(pl < cb["cube"].hi - margin, axis=1)
                        if inside.any():
                            depth = float(np.min(np.minimum(pl - cb["cube"].lo, cb["cube"].hi - pl)[inside].min(axis=1))) * 16
                            k = (ca["bone"], cb["bone"], key)
                            if k not in worst or depth > worst[k][0]:
                                worst[k] = (depth, float(t))
    base = {k[:2] for k in worst if k[2] == PRE + "dormant"}
    for (ba, bb, key), (depth, t) in sorted(worst.items(), key=lambda kv: -kv[1][0])[:12]:
        warn("clearance: %s pokes into %s by %.1f px in %s at t=%.2fs" % (ba, bb, depth, key, t))
    if not worst:
        info.append("clearance: no limb / hair interpenetration in any animation")


# ---------------------------------------------------------------------------------------------
def check_bounds(model):
    pose = gl.Pose(model)
    quads = gl.world_quads(model, pose)
    pts = np.concatenate([q[1] for q in quads]) * 16.0
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    info.append("bounds (px): x %.1f..%.1f  y %.1f..%.1f  z %.1f..%.1f  (height %.1f px = %.2f blocks)" % (
        lo[0], hi[0], lo[1], hi[1], lo[2], hi[2], hi[1] - lo[1], (hi[1] - lo[1]) / 16))
    if abs(lo[1]) > 0.01:
        err("feet must stand on y=0 (lowest vertex %.2f)" % lo[1])
    if not 140.0 <= hi[1] <= 147.0:
        err("doll should be ~9 blocks (144 px) tall, top at %.1f px" % hi[1])
    if max(abs(lo[0]), abs(hi[0])) > 32 or max(abs(lo[2]), abs(hi[2])) > 32:
        err("model extends beyond +-2 blocks horizontally")
    # animated extents (bounding box over all animations) for the culling hint
    return lo, hi


# ---------------------------------------------------------------------------------------------
def main(argv=None):
    errors.clear()
    warnings.clear()
    info.clear()
    for p in (GEO, ANIM, TEX, GLOW):
        if not p.exists():
            print("MISSING:", p)
            return 2
    try:
        geo_doc = json.loads(GEO.read_text())
        anim_doc = json.loads(ANIM.read_text())
    except json.JSONDecodeError as e:
        print("JSON error:", e)
        return 2
    tex_img = Image.open(TEX).convert("RGBA")
    glow_img = Image.open(GLOW).convert("RGBA")
    tex = np.array(tex_img)
    glow = np.array(glow_img)
    g = check_geo(geo_doc, tex_img.size)
    if g is None:
        return _report()
    try:
        model = gl.bake(geo_doc)
    except Exception as e:
        err("geo does not bake: %r" % (e,))
        return _report()
    parsed = check_anims(anim_doc, {b["name"] for b in g["bones"]}, model)
    if parsed:
        check_anchors(model, parsed)
        check_turns(model, parsed)
        # sample every animation: no NaN
        for key, a in parsed.items():
            L = a.length_ticks / 20.0
            for t in np.linspace(0, L, 41):
                pose = gl.pose_at(model, a, t, loop_wrap=False)
                for arr in list(pose.rot.values()) + list(pose.pos.values()) + list(pose.scale.values()):
                    if not np.all(np.isfinite(arr)):
                        err("%s: NaN/inf at t=%.3f" % (key, t))
                        break
    check_textures(g, tex, glow)
    check_face_coverage(g, tex)
    check_bounds(model)
    if parsed:
        check_clearance(model, parsed)
    check_zfight(model, tex)
    return _report()


def _report():
    for i in info:
        print("  info:", i)
    for w in warnings:
        print("  WARN:", w)
    for e in errors:
        print("  FAIL:", e)
    if errors:
        print("validate_doll: %d error(s)" % len(errors))
        return 1
    print("validate_doll: OK (%d warning(s))" % len(warnings))
    return 0


if __name__ == "__main__":
    sys.exit(main())
