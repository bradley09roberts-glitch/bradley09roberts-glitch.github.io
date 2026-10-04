"""Animation authoring DSL -> GeckoLib ``*.animation.json``.

Authoring values are in the JSON/Blockbench convention (degrees; rotation x = nod forward, y = turn to
the doll's right, z = tilt towards the doll's left; position x = doll's left, y = up, z = back).
Tracks are sampled with the *same* keyframe code GeckoLib uses (gl_model), so what the spring simulations
see is exactly what the game will play.
"""
from __future__ import annotations

import numpy as np

import gl_model as gl

CHANNELS = ("rotation", "position", "scale")


def fmt_time(t: float) -> str:
    s = ("%.4f" % round(t, 4)).rstrip("0")
    if s.endswith("."):
        s += "0"
    return s


def _vec(v):
    if isinstance(v, (int, float)):
        return (float(v),) * 3
    return tuple(float(x) for x in v)


class Track:
    """One channel of one bone: ordered keyframes (t, (x, y, z), easing|None).

    GeckoLib semantics: the easing stored on keyframe N shapes the segment N-1 -> N."""

    def __init__(self, keys=None):
        self.keys = []
        for k in keys or []:
            self.add(*k)

    def add(self, t, v, easing=None):
        self.keys.append((round(float(t), 4), _vec(v), easing))
        return self

    def sorted_keys(self):
        ks = sorted(self.keys, key=lambda k: k[0])
        out = []
        for k in ks:
            if out and abs(k[0] - out[-1][0]) < 1e-6:
                out[-1] = k           # later key at the same time wins
            else:
                out.append(k)
        return out

    def to_json(self):
        ks = self.sorted_keys()
        if len(ks) == 1 and ks[0][0] == 0:
            return [round(x, 4) for x in ks[0][1]]
        d = {}
        for t, v, e in ks:
            vec = [round(x, 4) + 0.0 for x in v]
            if e and e != "linear":
                d[fmt_time(t)] = {"vector": vec, "easing": e}
            else:
                d[fmt_time(t)] = vec
        return d

    def _stack(self):
        entries = []
        for t, v, e in self.sorted_keys():
            entries.append((t, {"vector": list(v), "easing": e} if e else list(v)))
        return gl._build_stack(entries, False)

    def sampler(self):
        stack = self._stack()
        return lambda t: np.array([gl._sample_axis(stack[i], t * 20.0) for i in range(3)])


class Anim:
    def __init__(self, name, length, loop=False):
        self.name = name
        self.length = float(length)
        self.loop = loop          # True | False | "hold_on_last_frame"
        self.tracks = {}          # (bone, channel) -> Track

    def track(self, bone, channel) -> Track:
        return self.tracks.setdefault((bone, channel), Track())

    def key(self, bone, channel, t, v, easing=None):
        self.track(bone, channel).add(t, v, easing)

    def set_track(self, bone, channel, keys):
        self.tracks[(bone, channel)] = Track(keys)

    def sampler(self, bone, channel, default=(0, 0, 0)):
        tr = self.tracks.get((bone, channel))
        if tr is None:
            d = np.array(_vec(default))
            return lambda t: d
        return tr.sampler()

    def to_json(self):
        bones = {}
        for (bone, ch), tr in sorted(self.tracks.items(), key=lambda kv: (kv[0][0], CHANNELS.index(kv[0][1]))):
            bones.setdefault(bone, {})[ch] = tr.to_json()
        out = {}
        if self.loop is True:
            out["loop"] = True
        elif self.loop:
            out["loop"] = self.loop
        out["animation_length"] = round(self.length, 4)
        out["bones"] = bones
        return out


# --------------------------------------------------------------------------------------
# keyframe generators
# --------------------------------------------------------------------------------------


def sample_fn(fn, t0, t1, dt, easing=None):
    """Sample fn(t)->vec3 on [t0, t1] (inclusive of both ends) every ~dt."""
    n = max(1, int(round((t1 - t0) / dt)))
    return [(t0 + (t1 - t0) * i / n, fn(t0 + (t1 - t0) * i / n), easing) for i in range(n + 1)]


def simplify(keys, tol_deg=0.03):
    """Drop keyframes that a linear interpolation between neighbours reproduces within tol.
    Keys with an explicit easing are always kept (they shape their own segment)."""
    ks = list(keys)
    if len(ks) <= 2:
        return ks

    def dev(i0, i1, i):
        t0, v0, _ = ks[i0]
        t1, v1, _ = ks[i1]
        t, v, _ = ks[i]
        a = (t - t0) / (t1 - t0) if t1 > t0 else 0.0
        return max(abs(v[c] - (v0[c] + (v1[c] - v0[c]) * a)) for c in range(3))

    keep = [False] * len(ks)
    keep[0] = keep[-1] = True
    for i, k in enumerate(ks):
        if k[2] and k[2] != "linear":
            keep[i] = True

    def rec(i0, i1):
        if i1 <= i0 + 1:
            return
        worst, wi = -1.0, -1
        for i in range(i0 + 1, i1):
            d = dev(i0, i1, i)
            if d > worst:
                worst, wi = d, i
        if worst > tol_deg:
            keep[wi] = True
            rec(i0, wi)
            rec(wi, i1)

    idx = [i for i, kk in enumerate(keep) if kk]
    for a, b in zip(idx[:-1], idx[1:]):
        rec(a, b)
    return [k for k, kp in zip(ks, keep) if kp]


# --------------------------------------------------------------------------------------
# dynamics (baked into keyframes)
# --------------------------------------------------------------------------------------


def deriv2(fn, t0, t1, h=1.0 / 400):
    """Return arrays (ts, x, v, a) of a scalar function sampled with finite differences."""
    ts = np.arange(t0 - 2 * h, t1 + 2 * h + 1e-9, h)
    xs = np.array([fn(t) for t in ts])
    v = np.gradient(xs, h)
    a = np.gradient(v, h)
    m = (ts >= t0 - 1e-9) & (ts <= t1 + 1e-9)
    return ts[m], xs[m], v[m], a[m]


def spring_drive(drive, omega, zeta, h=1.0 / 400, x0=0.0, v0=0.0):
    """x'' = -omega^2 x - 2 zeta omega x' + drive[i]   -> x[i]  (semi-implicit Euler)."""
    x, v = x0, v0
    out = np.zeros(len(drive))
    for i, d in enumerate(drive):
        acc = -omega * omega * x - 2 * zeta * omega * v + d
        v += acc * h
        x += v * h
        out[i] = x
    return out


def spring_follow(target, omega, zeta, h=1.0 / 400):
    """x'' = omega^2 (target - x) - 2 zeta omega x'   (starts in steady state at target[0])."""
    x, v = float(target[0]), 0.0
    out = np.zeros(len(target))
    for i, tg in enumerate(target):
        acc = omega * omega * (tg - x) - 2 * zeta * omega * v
        v += acc * h
        x += v * h
        out[i] = x
    return out


def softclip(x, limit):
    return limit * np.tanh(np.asarray(x) / limit)


def resample(ts, ys, t0, t1, dt):
    n = max(1, int(round((t1 - t0) / dt)))
    tt = np.array([t0 + (t1 - t0) * i / n for i in range(n + 1)])
    return tt, np.interp(tt, ts, ys)
