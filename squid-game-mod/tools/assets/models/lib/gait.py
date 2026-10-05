"""Foot-trajectory driven leg animation (IK) for walking / running style cycles.

All distances are model pixels; "forward" offsets are measured from the hip along -Z.
The stance foot moves backwards relative to the body in a straight line (so it stays planted when the
entity moves at ``planted_speed``), the swing foot follows a lifted arc.  The pelvis height follows the
reach of the legs (walk) or a prescribed bounce (run).
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Callable, Dict, Optional, Tuple

import numpy as np

from .anim import B, Pose
from .humanoid import Humanoid

# shoe geometry relative to the ankle pivot (px): from the contestant / guard footwear cubes
ANKLE_Y = 2.5
TOE = 4.15      # toe tip, forward of the ankle
HEEL = 2.15     # heel, behind the ankle


def smoothstep(x: float) -> float:
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def smootherstep(x: float) -> float:
    x = max(0.0, min(1.0, x))
    return x * x * x * (x * (x * 6 - 15) + 10)


def ankle_height_for_pitch(p_deg: float) -> float:
    """Ankle pivot height for which the lowest point of the shoe touches the ground at foot pitch p
    (positive = toes up -> heel contact, negative = toes down -> toe contact)."""
    p = math.radians(p_deg)
    if p >= 0:
        return ANKLE_Y * math.cos(p) + HEEL * math.sin(p)
    return ANKLE_Y * math.cos(p) - TOE * math.sin(p)


@dataclass
class Gait:
    cycle: float = 1.0
    d_front: float = 6.5          # ankle forward of the hip at foot strike
    d_back: float = 5.5           # ankle behind the hip at toe-off
    stance: float = 0.56          # fraction of the cycle a foot is on the ground
    lift: float = 2.4             # peak ankle lift during swing (px, above ground ankle height)
    strike_pitch: float = 10.0    # toes up at strike (deg)
    toeoff_pitch: float = -28.0   # toes down at toe-off (deg)
    swing_pitch_mid: float = -6.0
    leg_len_factor: float = 0.975  # pelvis keeps the leg this fraction of fully straight
    pelvis_mode: str = "reach"    # "reach" (walk) | "bounce" (run)
    bounce_strike: float = -1.0       # run: pelvis dy at foot strike
    bounce_mid_stance: float = -2.8   # run: pelvis dy at mid-stance
    bounce_off: float = -0.8          # run: pelvis dy at toe-off
    bounce_flight: float = 0.6        # run: pelvis dy at mid-flight
    swing_forward_ease: float = 1.0
    foot_x: float = 0.0           # extra lateral foot spread (px, per side, positive = wider)
    lead: float = 0.0             # leg phase offset of the left foot (cycle fraction)

    def foot_state(self, u: float) -> Tuple[float, float, float]:
        """Leg phase u in [0,1) -> (ankle forward offset, ankle height, foot pitch deg)."""
        u = u % 1.0
        s = self.stance
        if u < s:
            k = u / s
            f = self.d_front - (self.d_front + self.d_back) * k
            # pitch: heel strike -> flat -> heel-off
            if k < 0.25:
                pitch = self.strike_pitch * (1 - smoothstep(k / 0.25))
            elif k < 0.6:
                pitch = 0.0
            else:
                pitch = self.toeoff_pitch * smoothstep((k - 0.6) / 0.4)
            y = ankle_height_for_pitch(pitch)
            return f, y, pitch
        w = (u - s) / (1 - s)
        e = smootherstep(w) if self.swing_forward_ease >= 1 else w
        f = -self.d_back + (self.d_front + self.d_back) * e
        # lift arc (peaks slightly before the middle of the swing)
        arc = math.sin(math.pi * min(1.0, w ** 0.85)) ** 1.2
        y0 = ankle_height_for_pitch(self.toeoff_pitch) * (1 - smoothstep(w * 2)) + ANKLE_Y * smoothstep(w * 2)
        y = y0 + self.lift * arc
        # pitch: toe-off -> mid swing -> strike
        if w < 0.5:
            pitch = self.toeoff_pitch + (self.swing_pitch_mid - self.toeoff_pitch) * smoothstep(w / 0.5)
        else:
            pitch = self.swing_pitch_mid + (self.strike_pitch - self.swing_pitch_mid) * smoothstep((w - 0.5) / 0.5)
        return f, y, pitch


def leg_reach(L1: float, L2: float, factor: float) -> float:
    return (L1 + L2) * factor


def _bounce(g: Gait, q: float) -> float:
    """Pelvis dy over one half cycle q in [0,1) (stance of one foot then a short flight)."""
    st = min(g.stance * 2.0, 0.98)                     # stance as a fraction of the half cycle
    pts = [(0.0, g.bounce_strike), (st / 2, g.bounce_mid_stance), (st, g.bounce_off),
           ((st + 1.0) / 2, g.bounce_flight), (1.0, g.bounce_strike)]
    for (q0, v0), (q1, v1) in zip(pts, pts[1:]):
        if q <= q1:
            k = 0.0 if q1 == q0 else (q - q0) / (q1 - q0)
            return v0 + (v1 - v0) * (0.5 - 0.5 * math.cos(math.pi * k))
    return pts[-1][1]


def gait_legs(H: Humanoid, g: Gait, phase: float, base: Pose, reach: float = 9.25,
              hip_cap: float = 12.0, last: Optional[Dict[str, Tuple[float, float]]] = None
              ) -> Tuple[Pose, Dict[str, dict]]:
    """Return (pose with waist/leg/shin/shoe set, info) for cycle phase in [0,1)."""
    info: Dict[str, dict] = {}
    st = {}
    for side, off in (("left", g.lead), ("right", g.lead + 0.5)):
        u = (phase + off) % 1.0
        st[side] = g.foot_state(u)
        info[side] = dict(u=u, f=st[side][0], y=st[side][1], pitch=st[side][2],
                          stance=(u < g.stance))
    # pelvis height
    if g.pelvis_mode == "reach":
        hs = []
        for side in ("left", "right"):
            f, y, _ = st[side]
            hs.append(y + math.sqrt(max(reach ** 2 - f ** 2, 1.0)))
        py = min(min(hs), hip_cap)
        dy = py - 12.0
    else:
        dy = _bounce(g, ((phase + g.lead) % 0.5) / 0.5)
    pose = Pose(base)
    wp = pose.get("waist", B())
    pos = wp.pos or (0.0, 0.0, 0.0)
    pose["waist"] = B(rot=wp.rot, pos=(pos[0], pos[1] + dy, pos[2]))
    out = Pose({"waist": pose["waist"]})
    for side in ("left", "right"):
        f, y, pitch = st[side]
        hip = H.hip_world(pose, side)
        xs = 1.0 if side == "right" else -1.0
        target = (hip[0] + xs * g.foot_x, y, hip[2] - f)
        lp, err = H.foot(pose, side, target, pitch=pitch,
                         x0=(last or {}).get(side, (-f * 4.0, 25.0)))
        out = out + lp
        info[side]["err"] = err
        info[side]["knee"] = lp[H.leg_names[1].format(s=side)].rot[0]
        info[side]["hipx"] = lp[H.leg_names[0].format(s=side)].rot[0]
        info[side]["ankle_world"] = target
    info["dy"] = dy
    return out, info


PX_TO_BLOCKS = 0.9375 / 16.0     # model px -> blocks (renderer scale 0.9375)


def planted_speed(ctx, st: dict, g: Gait, span: float = None) -> dict:
    """Mean backward speed of the stance ankles relative to the body = ground speed at which they stay planted.

    ``span`` = seconds covered by the sampled infos (default one gait cycle)."""
    infos = st["info"]
    n = len(infos) - 1
    dt = (span if span is not None else g.cycle) / n
    sp = []
    for side in ("left", "right"):
        zs = []
        for i in range(n):
            ph, inf = infos[i]
            ph2, inf2 = infos[i + 1]
            if inf[side]["stance"] and inf2[side]["stance"]:
                dz = inf2[side]["ankle_world"][2] - inf[side]["ankle_world"][2]
                zs.append(dz / dt)
        if zs:
            sp.append(sum(zs) / len(zs))
    px_s = sum(sp) / len(sp) if sp else 0.0
    return dict(cycle_s=(span if span is not None else g.cycle), gait_cycle_s=g.cycle,
                planted_px_s=round(px_s, 2), planted_blocks_s=round(px_s * PX_TO_BLOCKS, 2))
