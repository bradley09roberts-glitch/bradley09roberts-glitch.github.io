"""Pose evaluation + numeric IK on top of :class:`rig.Rig`.

Poses are :class:`anim.Pose` objects in FILE convention.  They are converted to a rig state exactly
as GeckoLib loads them (rotation ``(-x, -y, +z)`` degrees -> radians, position untouched), so IK
solutions are automatically consistent with what the game will do.

Baked-space coordinates used for targets (pixels): +x = entity right, +y = up, +z = back.
"""
from __future__ import annotations

import math
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import numpy as np

from .anim import B, Pose
from .rig import DEG, Rig


def pose_to_state(pose: Pose) -> dict:
    st = {}
    for bone, b in pose.items():
        d = {}
        if b.rot is not None:
            d["rot"] = (-b.rot[0] * DEG, -b.rot[1] * DEG, b.rot[2] * DEG)
        if b.pos is not None:
            d["pos"] = b.pos
        if d:
            st[bone] = d
    return st


class Skel:
    """Convenience wrapper: FK queries for a pose."""

    def __init__(self, rig: Rig):
        self.rig = rig
        self._cache_key = None
        self._cache = None

    def mats(self, pose: Pose) -> Dict[str, np.ndarray]:
        return self.rig.world_matrices(pose_to_state(pose))

    def pt(self, pose: Pose, bone: str, file_pt: Sequence[float], mats=None) -> np.ndarray:
        m = mats if mats is not None else self.mats(pose)
        return self.rig.point(bone, file_pt, m)

    def local_to_world(self, pose: Pose, frame_bone: str, baked_local: Sequence[float], mats=None) -> np.ndarray:
        """A baked bind-pose point (x already negated) riding ``frame_bone`` -> world (model) space."""
        m = mats if mats is not None else self.mats(pose)
        p = np.array([baked_local[0], baked_local[1], baked_local[2], 1.0])
        return (m[frame_bone] @ p)[:3]

    def world_to_local(self, pose: Pose, frame_bone: str, world_pt: Sequence[float], mats=None) -> np.ndarray:
        m = mats if mats is not None else self.mats(pose)
        inv = np.linalg.inv(m[frame_bone])
        p = np.array([world_pt[0], world_pt[1], world_pt[2], 1.0])
        return (inv @ p)[:3]

    def lowest_y(self, pose: Pose, hidden=()) -> float:
        m = self.mats(pose)
        ymin = 1e9
        for bone, cube, verts in self.rig.vertices_world(m, hidden=hidden):
            ymin = min(ymin, float(verts[..., 1].min()))
        return ymin

    def bounds(self, pose: Pose, hidden=()):
        m = self.mats(pose)
        lo = np.array([1e9] * 3); hi = np.array([-1e9] * 3)
        for bone, cube, verts in self.rig.vertices_world(m, hidden=hidden):
            v = verts.reshape(-1, 3)
            lo = np.minimum(lo, v.min(0)); hi = np.maximum(hi, v.max(0))
        return lo, hi


class Chain:
    """Fast FK for a bone chain under a fixed base pose; ``overrides`` replace single bones' B."""

    def __init__(self, skel: Skel, base: Pose, top: str, tip: str):
        rig = skel.rig
        self.rig = rig
        self.path = rig.path_to(tip, top)
        m0 = rig.world_matrices(pose_to_state(base))
        parent = rig.bones[top].parent
        self.Mp = m0[parent] if parent else np.eye(4)
        st = pose_to_state(base)
        self.fixed = {n: rig.local_matrix(n, st.get(n)) for n in self.path}
        self.tip = tip

    def matrix(self, overrides: Dict[str, B]) -> np.ndarray:
        M = self.Mp
        for n in self.path:
            if n in overrides:
                st = pose_to_state(Pose({n: overrides[n]})).get(n)
                M = M @ self.rig.local_matrix(n, st)
            else:
                M = M @ self.fixed[n]
        return M

    def point(self, overrides: Dict[str, B], file_pt: Sequence[float]) -> np.ndarray:
        M = self.matrix(overrides)
        return (M @ np.array([-file_pt[0], file_pt[1], file_pt[2], 1.0]))[:3]


# ----------------------------------------------------------------------------------
# generic damped least squares
# ----------------------------------------------------------------------------------
def solve_dls(residual, x0: Sequence[float], lo: Sequence[float], hi: Sequence[float],
              iters: int = 80, mu: float = 1e-2, tol: float = 1e-5) -> np.ndarray:
    x = np.array(x0, dtype=float)
    lo = np.array(lo, dtype=float); hi = np.array(hi, dtype=float)
    x = np.clip(x, lo, hi)
    r = residual(x)
    cost = float(r @ r)
    for _ in range(iters):
        J = np.empty((len(r), len(x)))
        for k in range(len(x)):
            h = 0.05
            xp = x.copy(); xp[k] += h
            J[:, k] = (residual(xp) - r) / h
        g = J.T @ r
        A = J.T @ J
        improved = False
        for _try in range(8):
            try:
                dx = -np.linalg.solve(A + mu * np.eye(len(x)), g)
            except np.linalg.LinAlgError:
                mu *= 10
                continue
            xn = np.clip(x + dx, lo, hi)
            rn = residual(xn)
            cn = float(rn @ rn)
            if cn < cost:
                x, r, cost = xn, rn, cn
                mu = max(mu * 0.4, 1e-6)
                improved = True
                break
            mu *= 4
        if not improved or cost < tol:
            break
    return x


# ----------------------------------------------------------------------------------
# limb IK
# ----------------------------------------------------------------------------------
class LimbCfg:
    """Bone names of one humanoid rig (contestant / guard use different hand names)."""

    def __init__(self, hand_anchor: Dict[str, str], hand_anchor_pt: Dict[str, Tuple[float, float, float]],
                 arm="{s}_arm", forearm="{s}_forearm", leg="{s}_leg", shin="{s}_shin", shoe_bone="{s}_shoe"):
        self.hand_anchor = hand_anchor
        self.hand_anchor_pt = hand_anchor_pt
        self.arm, self.forearm, self.leg, self.shin, self.shoe = arm, forearm, leg, shin, shoe_bone


def ik_arm(skel: Skel, cfg: LimbCfg, pose: Pose, side: str, target_world: Sequence[float],
           pref: Sequence[float] = (10.0, 8.0, 0.0, 40.0), weight: float = 0.0004,
           x0: Optional[Sequence[float]] = None,
           lims=((-80, 200), (-80, 80), (-110, 110), (0, 150))) -> Tuple[Pose, float]:
    """Solve shoulder (swing, out, twist) + elbow flexion so the palm anchor reaches ``target_world``.

    Variables are SEMANTIC and side-symmetric: ``(swing fwd+, out outward+, twist outward+, elbow flex+)``
    in degrees; arm rot = (-swing, s*twist, s*out), forearm rot = (-elbow, 0, 0) with s=+1 right / -1 left.
    ``pref`` pulls the solution toward a nominal pose (this picks the elbow swivel).
    Returns (pose with the arm/forearm bones set, error px)."""
    arm = cfg.arm.format(s=side)
    fore = cfg.forearm.format(s=side)
    anchor = cfg.hand_anchor[side]
    apt = cfg.hand_anchor_pt[side]
    sg = 1.0 if side == "right" else -1.0
    target = np.array(target_world, dtype=float)
    base = Pose(pose)

    def make(x):
        p = Pose(base)
        p[arm] = B(rot=(-x[0], sg * x[2], sg * x[1]))
        p[fore] = B(rot=(-x[3], 0, 0))
        return p

    pref_a = np.array(pref, dtype=float)
    chain = Chain(skel, base, arm, anchor)

    def residual(x):
        ov = {arm: B(rot=(-x[0], sg * x[2], sg * x[1])), fore: B(rot=(-x[3], 0, 0))}
        hand = chain.point(ov, apt)
        e = hand - target
        reg = np.sqrt(weight) * (x - pref_a)
        return np.concatenate([e, reg])

    x0 = np.array(x0 if x0 is not None else pref, dtype=float)
    lo = [l[0] for l in lims]; hi = [l[1] for l in lims]
    x = solve_dls(residual, x0, lo, hi)
    p = make(x)
    err = float(np.linalg.norm(residual(x)[:3]))
    return p, err


def arm_vars(pose: Pose, side: str, cfg: LimbCfg) -> np.ndarray:
    """Inverse of the mapping in :func:`ik_arm` (for warm starts)."""
    sg = 1.0 if side == "right" else -1.0
    a = pose[cfg.arm.format(s=side)].rot
    f = pose[cfg.forearm.format(s=side)].rot
    return np.array([-a[0], sg * a[2], sg * a[1], -f[0]])


def ik_leg(skel: Skel, cfg: LimbCfg, pose: Pose, side: str, ankle_target_world: Sequence[float],
           foot_pitch: float = 0.0, x0=(0.0, 20.0), hip_roll: float = 0.0) -> Tuple[Pose, float]:
    """Solve hip rx + knee rx (+ shoe pitch) so the ANKLE pivot reaches ``ankle_target_world``.

    ``foot_pitch`` = world pitch of the foot in degrees, positive = toes UP (0 = sole flat).
    Returns (pose with leg/shin/shoe set, error px)."""
    leg = cfg.leg.format(s=side)
    shin = cfg.shin.format(s=side)
    shoe = cfg.shoe.format(s=side)
    ankle_pt = skel.rig.bones[shoe].pivot * np.array([-1, 1, 1])      # file coords of the ankle pivot
    base = Pose(pose)
    target = np.array(ankle_target_world, dtype=float)
    chain = Chain(skel, base, leg, shoe)

    def make(x):
        p = Pose(base)
        p[leg] = B(rot=(x[0], 0, hip_roll))
        p[shin] = B(rot=(x[1], 0, 0))
        return p

    def residual(x):
        ov = {leg: B(rot=(x[0], 0, hip_roll)), shin: B(rot=(x[1], 0, 0)), shoe: B(rot=(0, 0, 0))}
        return chain.point(ov, ankle_pt) - target

    x = solve_dls(residual, x0, (-100, 0), (100, 150), iters=60)
    p = make(x)
    err = float(np.linalg.norm(residual(x)))
    # foot pitch: total baked pitch of the chain must be compensated by the shoe bone
    p[shoe] = B(rot=(0, 0, 0))
    m = skel.mats(p)
    # forward direction of the shoe bone (its local -z) in world, pitch = elevation of that vector
    fwd = m[shoe][:3, :3] @ np.array([0.0, 0.0, -1.0])
    pitch_now = math.degrees(math.asin(max(-1.0, min(1.0, fwd[1]))))
    # baked Rx(theta): front goes up for theta>0  ->  file rx = -theta
    p[shoe] = B(rot=(-(foot_pitch - pitch_now), 0, 0))
    return p, err
