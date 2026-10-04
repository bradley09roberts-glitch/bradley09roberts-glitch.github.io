"""GeckoLib / Bedrock animation JSON builder with a small pose DSL.

Everything here is expressed in FILE convention (the numbers that end up in the JSON):

* rotation ``[x, y, z]`` in degrees.  GeckoLib loads it as radians ``(-x, -y, +z)`` and
  applies ``Rz * Ry * Rx`` in its baked (X-mirrored) space.  Net effect on the entity:

    +x  : head/torso bend FORWARD (nod down), hanging limbs swing BACKWARD
    +y  : turn to the entity's RIGHT
    +z  : roll toward the entity's LEFT (right arm: splays outward; left arm: swings inward)

* position ``[x, y, z]`` in pixels.  GeckoLib renders ``translate(-x, y, z)``, so
    +x : toward the entity's LEFT,  +y : up,  +z : BACKWARD.

* a keyframe's ``easing`` applies to the segment that ARRIVES at that keyframe
  (GeckoLib's ``Keyframe`` stores start=previous value, end=this value, easing=this key's).
"""
from __future__ import annotations

import json
import math
from typing import Callable, Dict, Iterable, List, Optional, Sequence, Tuple

from . import jsonfmt

Vec = Tuple[float, float, float]

# names registered in software.bernie.geckolib.animation.EasingType (lower-cased on load)
EASINGS = {
    "linear", "step",
    "easeInSine", "easeOutSine", "easeInOutSine",
    "easeInQuad", "easeOutQuad", "easeInOutQuad",
    "easeInCubic", "easeOutCubic", "easeInOutCubic",
    "easeInQuart", "easeOutQuart", "easeInOutQuart",
    "easeInQuint", "easeOutQuint", "easeInOutQuint",
    "easeInExpo", "easeOutExpo", "easeInOutExpo",
    "easeInCirc", "easeOutCirc", "easeInOutCirc",
    "easeInBack", "easeOutBack", "easeInOutBack",
    "easeInElastic", "easeOutElastic", "easeInOutElastic",
    "easeInBounce", "easeOutBounce", "easeInOutBounce",
    "catmullrom",
}
_EASINGS_LOWER = {e.lower() for e in EASINGS}


def _num(v: float):
    r = round(float(v), 3)
    if r == 0:
        return 0
    if r == int(r):
        return int(r)
    return r


def _tkey(t: float) -> str:
    r = round(float(t), 4)
    if r == int(r):
        return str(int(r))
    return repr(r)


# ----------------------------------------------------------------------------------
# Pose DSL
# ----------------------------------------------------------------------------------
class B:
    """Per-bone pose: optional rotation (deg) and position (px) in file convention."""
    __slots__ = ("rot", "pos")

    def __init__(self, rot: Optional[Sequence[float]] = None, pos: Optional[Sequence[float]] = None):
        self.rot = None if rot is None else tuple(float(x) for x in rot)
        self.pos = None if pos is None else tuple(float(x) for x in pos)

    def __repr__(self):
        return f"B(rot={self.rot}, pos={self.pos})"

    def mirrored(self) -> "B":
        return B(None if self.rot is None else (self.rot[0], -self.rot[1], -self.rot[2]),
                 None if self.pos is None else (-self.pos[0], self.pos[1], self.pos[2]))

    def scaled(self, k: float) -> "B":
        return B(None if self.rot is None else tuple(x * k for x in self.rot),
                 None if self.pos is None else tuple(x * k for x in self.pos))

    def merged(self, other: "B") -> "B":
        return B(other.rot if other.rot is not None else self.rot,
                 other.pos if other.pos is not None else self.pos)

    def added(self, other: "B") -> "B":
        def add(a, b):
            if a is None:
                return b
            if b is None:
                return a
            return tuple(x + y for x, y in zip(a, b))
        return B(add(self.rot, other.rot), add(self.pos, other.pos))


def _as_b(v) -> B:
    if isinstance(v, B):
        return v
    if v is None:
        return B()
    return B(rot=v)  # bare 3-tuple = rotation


def swap_side(name: str) -> str:
    if name.startswith("left_"):
        return "right_" + name[5:]
    if name.startswith("right_"):
        return "left_" + name[6:]
    if name.endswith("_left"):
        return name[:-5] + "_right"
    if name.endswith("_right"):
        return name[:-6] + "_left"
    return name


class Pose(dict):
    """dict bone -> B.  Missing bone = untouched."""

    def __init__(self, *args, **kw):
        super().__init__()
        src = dict(*args, **kw)
        for k, v in src.items():
            self[k] = _as_b(v)

    def mirrored(self) -> "Pose":
        return Pose({swap_side(k): v.mirrored() for k, v in self.items()})

    def __add__(self, other: "Pose") -> "Pose":
        """Merge (right-hand side wins per bone/channel)."""
        return self.merged(other)

    def merged(self, other: "Pose") -> "Pose":
        out = Pose(self)
        for k, v in other.items():
            out[k] = out[k].merged(v) if k in out else v
        return out

    def added(self, other: "Pose") -> "Pose":
        out = Pose(self)
        for k, v in other.items():
            out[k] = out[k].added(v) if k in out else v
        return out

    def scaled(self, k: float) -> "Pose":
        return Pose({n: b.scaled(k) for n, b in self.items()})

    def only(self, bones: Iterable[str]) -> "Pose":
        bones = set(bones)
        return Pose({k: v for k, v in self.items() if k in bones})

    def without(self, bones: Iterable[str]) -> "Pose":
        bones = set(bones)
        return Pose({k: v for k, v in self.items() if k not in bones})


def P(**kw) -> Pose:
    return Pose(kw)


def lerp_pose(a: Pose, b: Pose, t: float) -> Pose:
    out = Pose()
    for k in set(a) | set(b):
        ba = a.get(k, B())
        bb = b.get(k, B())
        rot = None
        pos = None
        if ba.rot is not None or bb.rot is not None:
            ra = ba.rot or (0, 0, 0)
            rb = bb.rot or (0, 0, 0)
            rot = tuple(x + (y - x) * t for x, y in zip(ra, rb))
        if ba.pos is not None or bb.pos is not None:
            pa = ba.pos or (0, 0, 0)
            pb = bb.pos or (0, 0, 0)
            pos = tuple(x + (y - x) * t for x, y in zip(pa, pb))
        out[k] = B(rot, pos)
    return out


# ----------------------------------------------------------------------------------
# Animation container
# ----------------------------------------------------------------------------------
LOOP = "loop"
ONCE = "once"
HOLD = "hold"


class Track:
    def __init__(self):
        self.keys: List[Tuple[float, Vec, str]] = []

    def add(self, t: float, v: Sequence[float], ease: str = "linear"):
        for i, (tt, _, _) in enumerate(self.keys):
            if abs(tt - t) < 1e-6:
                self.keys[i] = (tt, tuple(float(x) for x in v), ease)
                return
        self.keys.append((float(t), tuple(float(x) for x in v), ease))
        self.keys.sort(key=lambda k: k[0])

    def value_at(self, t: float) -> Vec:
        """Same interpolation the game does (linear/eased) - used to fill loop ends."""
        ks = self.keys
        if t <= ks[0][0]:
            return ks[0][1]
        for (t0, v0, _), (t1, v1, e1) in zip(ks, ks[1:]):
            if t <= t1:
                f = (t - t0) / (t1 - t0)
                return tuple(a + (b - a) * f for a, b in zip(v0, v1))
        return ks[-1][1]


class BoneTracks:
    def __init__(self):
        self.rot = Track()
        self.pos = Track()
        self.scale = Track()


class Animation:
    def __init__(self, name: str, length: float, loop: str = LOOP):
        if loop not in (LOOP, ONCE, HOLD):
            raise ValueError(loop)
        self.name = name
        self.length = float(length)
        self.loop = loop
        self.bones: Dict[str, BoneTracks] = {}

    # -- authoring ---------------------------------------------------------------
    def _bt(self, bone: str) -> BoneTracks:
        if bone not in self.bones:
            self.bones[bone] = BoneTracks()
        return self.bones[bone]

    def key(self, t: float, pose: Pose, ease: str = "linear") -> "Animation":
        """Add keyframes for the bones in ``pose`` at time ``t``.

        ``ease`` is the easing of the segment arriving at this key."""
        if ease not in EASINGS:
            raise ValueError(f"unknown easing {ease!r}")
        if t < -1e-9 or t > self.length + 1e-6:
            raise ValueError(f"{self.name}: key time {t} outside 0..{self.length}")
        for bone, b in pose.items():
            bt = self._bt(bone)
            if b.rot is not None:
                bt.rot.add(t, b.rot, ease)
            if b.pos is not None:
                bt.pos.add(t, b.pos, ease)
        return self

    def keys(self, items: Iterable[Tuple], default_ease: str = "linear") -> "Animation":
        """items: (t, pose) or (t, pose, ease)."""
        for it in items:
            if len(it) == 2:
                self.key(it[0], it[1], default_ease)
            else:
                self.key(it[0], it[1], it[2])
        return self

    def cycle(self, fn: Callable[[float], Pose], samples: int, ease: str = "linear") -> "Animation":
        """Sample a periodic pose function ``fn(phase in [0,1))`` into ``samples`` segments."""
        for i in range(samples + 1):
            ph = (i % samples) / float(samples)
            self.key(self.length * i / samples, fn(ph), ease)
        return self

    def close_loop(self) -> "Animation":
        """Make the last key of every track equal its first key (seamless loop)."""
        for bt in self.bones.values():
            for tr in (bt.rot, bt.pos, bt.scale):
                if not tr.keys:
                    continue
                first = tr.keys[0]
                last = tr.keys[-1]
                if abs(last[0] - self.length) > 1e-6:
                    tr.add(self.length, first[1], "linear")
                else:
                    tr.keys[-1] = (last[0], first[1], last[2])
        return self

    def start_from(self, pose: Pose, ease: str = "linear") -> "Animation":
        """Ensure every touched channel has a key at t=0 (value from ``pose``, or 0)."""
        for bone, bt in self.bones.items():
            b = pose.get(bone, B())
            for tr, val in ((bt.rot, b.rot), (bt.pos, b.pos)):
                if tr.keys and tr.keys[0][0] > 1e-6:
                    tr.add(0.0, val if val is not None else (0, 0, 0), ease)
        return self

    def hold_end(self) -> "Animation":
        """Extend every track with a final key at ``length`` equal to its last value."""
        for bt in self.bones.values():
            for tr in (bt.rot, bt.pos, bt.scale):
                if tr.keys and tr.keys[-1][0] < self.length - 1e-6:
                    tr.add(self.length, tr.keys[-1][1], "linear")
        return self

    # -- emission ----------------------------------------------------------------
    def _track_json(self, tr: Track):
        out = {}
        for (t, v, e) in tr.keys:
            vec = [_num(x) for x in v]
            if e == "linear":
                out[_tkey(t)] = vec
            else:
                out[_tkey(t)] = {"vector": vec, "easing": e}
        return out

    def to_json(self) -> dict:
        bones = {}
        for name, bt in self.bones.items():
            d = {}
            if bt.rot.keys:
                d["rotation"] = self._track_json(bt.rot)
            if bt.pos.keys:
                d["position"] = self._track_json(bt.pos)
            if bt.scale.keys:
                d["scale"] = self._track_json(bt.scale)
            if d:
                bones[name] = d
        loop = {LOOP: True, ONCE: False, HOLD: "hold_on_last_frame"}[self.loop]
        return {"loop": loop, "animation_length": _num(self.length), "bones": bones}


class AnimationSet:
    def __init__(self, prefix: str):
        self.prefix = prefix  # e.g. "animation.contestant"
        self.anims: Dict[str, Animation] = {}

    def new(self, name: str, length: float, loop: str = LOOP) -> Animation:
        full = f"{self.prefix}.{name}"
        if full in self.anims:
            raise ValueError(f"duplicate animation {full}")
        a = Animation(full, length, loop)
        self.anims[full] = a
        return a

    def get(self, name: str) -> Animation:
        return self.anims[f"{self.prefix}.{name}"]

    def to_json(self) -> dict:
        return {
            "format_version": "1.8.0",
            "animations": {k: v.to_json() for k, v in self.anims.items()},
        }

    def write(self, path: str) -> None:
        jsonfmt.write(path, self.to_json())
