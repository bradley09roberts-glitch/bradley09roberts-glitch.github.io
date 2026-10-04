"""Semantic pose builders for the humanoid rigs (contestant + guard share the skeleton layout).

All builders return :class:`anim.Pose` in FILE convention but are parameterised in human terms:

    HEAD(nod, turn, tilt)        nod: down +, turn: to the entity's right +, tilt: toward its left +
    BODY / WAIST(lean, twist, tilt, pos)   lean: forward +, twist: right +, tilt: left +
    ARM(side, swing, out, elbow, ...)      swing: forward +, out: away from the body +, elbow: flexion +
    LEG(side, hip, knee, out, ankle)       hip: forward +, knee: flexion (heel to buttock) +, ankle: toes up +

Derived from the GeckoLib conventions documented in ``lib/anim.py``/``rig.py`` and verified numerically in
``selftest.py`` (e.g. ``ARM('right', swing=60)`` moves the hand forward, ``LEG('left', knee=90)`` folds the shin back).
"""
from __future__ import annotations

import math
from typing import Dict, Optional, Sequence, Tuple

import numpy as np

from .anim import B, Pose
from .posing import LimbCfg, Skel, arm_vars, ik_arm, ik_leg
from .rig import Rig


def sgn(side: str) -> int:
    """+1 for the right side, -1 for the left (sign that makes 'outward' positive for rz/ry)."""
    return 1 if side == "right" else -1


def HEAD(nod=0.0, turn=0.0, tilt=0.0) -> Pose:
    return Pose(head=B(rot=(nod, turn, tilt)))


def BODY(lean=0.0, twist=0.0, tilt=0.0, pos=None) -> Pose:
    return Pose(body=B(rot=(lean, twist, tilt), pos=pos))


def WAIST(lean=0.0, twist=0.0, tilt=0.0, pos=None) -> Pose:
    return Pose(waist=B(rot=(lean, twist, tilt), pos=pos))


def ROOT(rot=None, pos=None) -> Pose:
    return Pose(root=B(rot=rot, pos=pos))


def ARM(side: str, swing=0.0, out=0.0, elbow=0.0, twist=0.0, fout=0.0, ftwist=0.0, wrist=None,
        names=("{s}_arm", "{s}_forearm", "{s}_hand_skin")) -> Pose:
    s = sgn(side)
    a, f, h = (n.format(s=side) for n in names)
    p = Pose({a: B(rot=(-swing, s * twist, s * out)),
              f: B(rot=(-elbow, s * ftwist, s * fout))})
    if wrist is not None:
        p[h] = B(rot=wrist)
    return p


def LEG(side: str, hip=0.0, knee=0.0, out=0.0, ankle=0.0, twist=0.0,
        names=("{s}_leg", "{s}_shin", "{s}_shoe")) -> Pose:
    s = sgn(side)
    l, k, f = (n.format(s=side) for n in names)
    return Pose({l: B(rot=(-hip, s * twist, s * out)),
                 k: B(rot=(knee, 0, 0)),
                 f: B(rot=(-ankle, 0, 0))})


def both(fn, **kw) -> Pose:
    return fn("left", **kw) + fn("right", **kw)


def ARMS(swing=0.0, out=0.0, elbow=0.0, twist=0.0, **kw) -> Pose:
    return ARM("left", swing, out, elbow, twist, **kw) + ARM("right", swing, out, elbow, twist, **kw)


def LEGS(hip=0.0, knee=0.0, out=0.0, ankle=0.0) -> Pose:
    return LEG("left", hip, knee, out, ankle) + LEG("right", hip, knee, out, ankle)


class Humanoid:
    """Rig + IK helpers bound to one model."""

    def __init__(self, rig: Rig, cfg: LimbCfg, arm_names=("{s}_arm", "{s}_forearm", "{s}_hand_skin"),
                 leg_names=("{s}_leg", "{s}_shin", "{s}_shoe")):
        self.rig = rig
        self.skel = Skel(rig)
        self.cfg = cfg
        self.arm_names = arm_names
        self.leg_names = leg_names
        self._warm: Dict[str, np.ndarray] = {}

    # --- arms ---------------------------------------------------------------------------
    def arm_bones(self, side: str):
        return [n.format(s=side) for n in self.arm_names[:2]]

    def hand(self, base: Pose, side: str, file_pt: Sequence[float], frame: str = "body",
             pref: Sequence[float] = (10.0, 8.0, 0.0, 40.0), weight=(0.0004, 0.0012, 0.0004, 0.0004),
             lims=((-80, 200), (-90, 90), (-120, 120), (0, 150)), warm=True) -> Tuple[Pose, float]:
        """IK: palm anchor of ``side`` to a point given in FILE coordinates riding bone ``frame``
        (bind pose of that bone).  ``base`` supplies root/waist/body/head etc. at this instant."""
        baked = (-file_pt[0], file_pt[1], file_pt[2])
        target = self.skel.local_to_world(base, frame, baked)
        x0 = self._warm.get(side) if warm and side in self._warm else pref
        pose, err = ik_arm(self.skel, self.cfg, base, side, target, pref=pref, weight=weight, x0=x0, lims=lims)
        a, f = self.arm_bones(side)
        self._warm[side] = arm_vars(pose, side, self.cfg)
        return Pose({a: pose[a], f: pose[f]}), err

    def hand_w(self, base: Pose, side: str, world_pt: Sequence[float],
               pref: Sequence[float] = (10.0, 8.0, 0.0, 40.0), weight=(0.0004, 0.0012, 0.0004, 0.0004),
               lims=((-80, 200), (-90, 90), (-120, 120), (0, 150)), warm=True) -> Tuple[Pose, float]:
        """IK to a WORLD (model-space, baked: +x right, +z back) target."""
        x0 = self._warm.get(side) if warm and side in self._warm else pref
        pose, err = ik_arm(self.skel, self.cfg, base, side, world_pt, pref=pref, weight=weight, x0=x0, lims=lims)
        a, f = self.arm_bones(side)
        self._warm[side] = arm_vars(pose, side, self.cfg)
        return Pose({a: pose[a], f: pose[f]}), err

    def on_thigh(self, base: Pose, side: str, along: float = 0.0, front: float = 2.4, out: float = 0.0) -> np.ndarray:
        """World point on the front of the thigh: ``along`` 0 = knee .. 1 = hip, ``front`` px off the leg axis
        (toward the leg's forward face), ``out`` px away from the body centre line."""
        leg = self.leg_names[0].format(s=side)
        shin = self.leg_names[1].format(s=side)
        sx = 1 if side == "left" else -1
        mats = self.skel.mats(base)
        hip = self.rig.point(leg, (2 * sx, 12, 0), mats)
        knee = self.rig.point(shin, (2 * sx, 6, 0), mats)
        axis = (hip - knee) / np.linalg.norm(hip - knee)
        fwd = mats[leg][:3, :3] @ np.array([0.0, 0.0, -1.0])
        n = fwd - axis * float(fwd @ axis)
        n = n / np.linalg.norm(n)
        p = knee + (hip - knee) * along + n * front
        p[0] += (-1.0 if side == "left" else 1.0) * out
        return p

    def reset_warm(self):
        self._warm.clear()

    def hands(self, base: Pose, left=None, right=None, frame: str = "body", prefs=None, **kw) -> Pose:
        """Convenience: solve both hands; ``left``/``right`` are file points (or None to skip)."""
        out = Pose()
        prefs = prefs or {}
        errs = []
        for side, pt in (("left", left), ("right", right)):
            if pt is None:
                continue
            kw2 = dict(kw)
            if side in prefs:
                kw2["pref"] = prefs[side]
            p, e = self.hand(base, side, pt, frame=frame, **kw2)
            out = out + p
            errs.append(e)
        out.errs = errs  # type: ignore[attr-defined]
        return out

    # --- legs ----------------------------------------------------------------------------
    def foot(self, base: Pose, side: str, ankle_world: Sequence[float], pitch: float = 0.0, x0=(0.0, 20.0)):
        pose, err = ik_leg(self.skel, self.cfg, base, side, ankle_world, foot_pitch=pitch, x0=x0)
        l, k, f = (n.format(s=side) for n in self.leg_names)
        return Pose({l: pose[l], k: pose[k], f: pose[f]}), err

    def hip_world(self, base: Pose, side: str) -> np.ndarray:
        leg = self.leg_names[0].format(s=side)
        piv = self.rig.bones[leg].pivot * np.array([-1, 1, 1])
        return self.skel.pt(base, leg, piv)

    def ground_snap(self, pose: Pose, hidden=(), clearance: float = 0.0, root: str = "root") -> Pose:
        """Shift ``root`` vertically so the lowest model point of the pose sits on y=clearance."""
        y = self.skel.lowest_y(pose, hidden)
        cur = pose.get(root, B()).pos or (0.0, 0.0, 0.0)
        p = Pose(pose)
        p[root] = B(rot=pose.get(root, B()).rot, pos=(cur[0], cur[1] - y + clearance, cur[2]))
        return p
