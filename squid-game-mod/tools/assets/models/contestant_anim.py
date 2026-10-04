"""Contestant animations (animation.contestant.<name>) - assembly module.

Authoring conventions (see lib/humanoid.py): poses are built with semantic helpers (HEAD/BODY/ARM/LEG...),
arms are placed with IK where a hand has to touch something, feet with IK for gait cycles.  Everything is
converted to the GeckoLib file convention by the helpers and re-verified by the preview/validator.
"""
from __future__ import annotations

import math
from typing import Dict, Optional

from common import *  # noqa: F401,F403
from lib import jsonfmt
from lib.anim import ONCE, LOOP, HOLD, Animation, AnimationSet, B, Pose
from lib.humanoid import ARMS, Humanoid
from lib.posing import LimbCfg
from lib.rig import Rig

HAND_ANCHOR = {"left": "item_left", "right": "item_right"}
HAND_ANCHOR_PT = {"left": (6.0, 11.7, 0.0), "right": (-6.0, 11.7, 0.0)}

# Idle (relaxed standing) arms: every standing animation starts from / is compatible with this pose.
IDLE0 = ARMS(swing=2, out=5, elbow=9)


class Ctx:
    def __init__(self, mb):
        self.mb = mb
        self.rig = Rig(mb.geo.to_json())
        self.cfg = LimbCfg(HAND_ANCHOR, HAND_ANCHOR_PT)
        self.H = Humanoid(self.rig, self.cfg)
        self.A = AnimationSet("animation.contestant")
        self.info: Dict[str, dict] = {}
        self.refs: Dict[str, str] = {}      # one-shot -> animation whose t=0 pose it must match

    def new(self, name: str, length: float, loop: str) -> Animation:
        self.H.reset_warm()
        return self.A.new(name, length, loop)


def finish(a: Animation, one_shot: bool = False, fill_idle: bool = True, start: Optional[Pose] = None) -> Animation:
    """Idle arms for bones an animation does not touch; one-shots get a t=0 key on the start pose."""
    if fill_idle:
        for bone, b in IDLE0.items():
            if bone not in a.bones:
                a.key(0.0, Pose({bone: b}))
    if one_shot:
        a.start_from(start if start is not None else IDLE0)
    return a


# ----------------------------------------------------------------------------------------------
# key helpers shared by all families
# ----------------------------------------------------------------------------------------------
PREF_DOWN = (8.0, 10.0, 0.0, 35.0)
PREF_FRONT = (35.0, 10.0, 0.0, 70.0)
PREF_UP = (120.0, 40.0, 0.0, 100.0)
PREF_HIPS = (-8.0, 50.0, 0.0, 95.0)       # hands on hips: elbows out
PREF_FACE = (95.0, 15.0, 0.0, 120.0)
PREF_GUARD = (55.0, 8.0, 0.0, 105.0)      # fists up


def plant(ctx: Ctx, base: Pose, dy: float = 0.0, left=(0.0, 0.0), right=(0.0, 0.0), pitch=(0.0, 0.0),
          x_spread=(0.0, 0.0), hip_dz: float = 0.0) -> Pose:
    """Lower the waist by ``dy`` (negative = down) and solve both legs with the ankles planted on the floor.

    left/right = (forward offset of the ankle from its hip, extra lift of the ankle) in px;
    pitch = foot pitch (deg, + toes up); x_spread = extra outward foot offset."""
    from lib.gait import ankle_height_for_pitch
    pose = Pose(base)
    wp = pose.get("waist", B())
    pos = wp.pos or (0.0, 0.0, 0.0)
    pose["waist"] = B(rot=wp.rot, pos=(pos[0], pos[1] + dy, pos[2]))
    out = Pose({"waist": pose["waist"]})
    for i, side in enumerate(("left", "right")):
        f, lift = (left, right)[i]
        hip = ctx.H.hip_world(pose, side)
        sg = 1.0 if side == "right" else -1.0
        y = ankle_height_for_pitch(pitch[i]) + lift
        tgt = (hip[0] + sg * x_spread[i], y, hip[2] - f)
        lp, err = ctx.H.foot(pose, side, tgt, pitch=pitch[i])
        out = out + lp
    return out


def K(ctx: Ctx, a: Animation, t: float, base: Pose = None, ease: str = "easeInOutSine", L=None, R=None,
      frame: str = "body", prefL=PREF_DOWN, prefR=PREF_DOWN, extra: Pose = None, warm: bool = True) -> Pose:
    """Add a keyframe. ``L``/``R``: None -> idle arm, Pose -> use as is (FK), tuple -> IK target (file coords in
    ``frame``).  ``base`` should already contain waist/body/head/legs for this instant."""
    base = Pose(base or {})
    out = Pose(base)
    for side, tgt, pref in (("left", L, prefL), ("right", R, prefR)):
        if tgt is None:
            out = out + Pose({k: v for k, v in IDLE0.items() if k.startswith(side)})
        elif isinstance(tgt, Pose):
            out = out + tgt
        else:
            hand, err = ctx.H.hand(base, side, tgt, frame=frame, pref=pref, warm=warm)
            out = out + hand
            if err > 0.6:
                print(f"  [warn] {a.name} t={t} {side} hand IK error {err:.2f}px")
    if extra:
        out = out + extra
    a.key(t, out, ease)
    return out


def build_all(mb) -> Ctx:
    import ca_loco
    ctx = Ctx(mb)
    ca_loco.build(ctx)
    for modname in ("ca_react", "ca_game", "ca_combat"):
        try:
            mod = __import__(modname)
        except ImportError:
            continue
        mod.build(ctx)
    from lib.postproc import ground_lock, lag_tracks
    from ca_react import OPTIONAL
    lag_set = {"stop_skid", "turn_left", "turn_right", "jump_leap", "land", "stumble", "lose_balance", "shocked",
               "relieved", "interact", "knocked_back", "punch_left", "punch_right", "shove", "dodge_left", "dodge_right",
               "pull_heave", "pull_slip", "bridge_step", "marble_throw_release", "marble_guess", "dalgona_crack",
               "eliminated_forward", "eliminated_backward", "knocked_down"}
    no_lock = {"jump_leap", "fall_loop", "celebrate", "celebrate_fist", "eliminated_forward", "eliminated_backward",
               "knocked_down", "run", "sprint_attack"}
    for a in ctx.A.anims.values():
        short = a.name.split(".")[-1]
        if short in lag_set:
            lag_tracks(a, {"head": 0.04, "left_forearm": 0.03, "right_forearm": 0.03,
                           "left_hand_skin": 0.04, "right_hand_skin": 0.04})
        if short not in no_lock:
            lift = ground_lock(ctx.rig, a, OPTIONAL, threshold=0.3)
            if lift > 0:
                ctx.info.setdefault("ground_lift_px", {})[short] = round(lift, 2)
        # every channel that starts after t=0 starts from the neutral pose; loops end where they started
        a.start_from(Pose())
        if a.loop == LOOP:
            a.close_loop()
    return ctx


def build_and_write(mb) -> None:
    ctx = build_all(mb)
    ctx.A.write(str(ANIM_DIR / "contestant.animation.json"))
    jsonfmt.write(str(HERE / "contestant_anim_meta.json"), {"refs": ctx.refs, "info": ctx.info})
    print(f"contestant: {len(ctx.A.anims)} animations")
