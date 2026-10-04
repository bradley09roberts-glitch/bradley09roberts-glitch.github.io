"""Guard animations (animation.guard.<name>)."""
from __future__ import annotations

import math
from typing import Dict, Optional, Tuple

import numpy as np

from common import *  # noqa: F401,F403
from lib import jsonfmt
from lib.anim import HOLD, LOOP, ONCE, Animation, AnimationSet, B, Pose
from lib.gait import Gait, gait_legs, planted_speed
from lib.humanoid import ARM, ARMS, BODY, HEAD, LEG, LEGS, ROOT, WAIST, Humanoid
from lib.posing import LimbCfg, pose_to_state
from lib.rig import Rig
from guard_model import RIFLE_G

TAU = 2 * math.pi
S = math.sin
C = math.cos

ARM_NAMES = ("{s}_arm", "{s}_forearm", "{s}_hand")
LEG_NAMES = ("{s}_leg", "{s}_shin", "{s}_boot")
HAND_LOC_R = (0.0, -1.1, 1.0)      # palm position on the pistol grip, relative to the rifle pivot
HAND_LOC_L = (0.0, 0.2, -5.5)      # palm position under the handguard

IDLE_ARMS = ARMS(swing=2, out=5, elbow=9)
PREF_PORT = (35.0, 12.0, 0.0, 85.0)


class Ctx:
    def __init__(self, mb):
        self.mb = mb
        self.rig = Rig(mb.geo.to_json())
        cfg = LimbCfg({"left": "item_left", "right": "item_right"},
                      {"left": (6.0, 11.7, 0.0), "right": (-6.0, 11.7, 0.0)},
                      shoe_bone="{s}_boot")
        self.cfg = cfg
        self.H = Humanoid(self.rig, cfg, arm_names=ARM_NAMES, leg_names=LEG_NAMES)
        self.A = AnimationSet("animation.guard")
        self.refs: Dict[str, str] = {}
        self.info: Dict[str, dict] = {}

    def new(self, name, length, loop):
        self.H.reset_warm()
        return self.A.new(name, length, loop)


def A_(side, **kw):
    return ARM(side, names=ARM_NAMES, **kw)


def L_(side, **kw):
    return LEG(side, names=LEG_NAMES, **kw)


def RIFLE(pos=(0.0, 0.0, 0.0), pitch=0.0, yaw=0.0, roll=0.0) -> Pose:
    """Rifle bone pose. ``pos`` = displacement of the grip pivot (file px) from its bind position;
    pitch: muzzle up +, yaw: muzzle to the entity's right +, roll: top toward its left +."""
    return Pose(rifle=B(rot=(-pitch, yaw, roll), pos=pos))


def RIFLE_AT(grip, pitch=0.0, yaw=0.0, roll=0.0) -> Pose:
    """Rifle with its pistol grip at ``grip`` (file coords, body space)."""
    g0 = RIFLE_G
    return RIFLE(pos=(grip[0] - g0[0], grip[1] - g0[1], grip[2] - g0[2]), pitch=pitch, yaw=yaw, roll=roll)


def rifle_points(ctx: Ctx, rifle: Pose) -> Tuple[tuple, tuple]:
    mats = ctx.rig.world_matrices(pose_to_state(rifle))
    M = mats["rifle"]
    g = RIFLE_G

    def tgt(loc):
        p = np.array([-(g[0] + loc[0]), g[1] + loc[1], g[2] + loc[2], 1.0])
        w = M @ p
        return (-w[0], w[1], w[2])
    return tgt(HAND_LOC_R), tgt(HAND_LOC_L)


def holding(ctx: Ctx, base: Pose, rifle: Pose, right=True, left=True, prefR=PREF_PORT, prefL=PREF_PORT,
            right_free: Optional[Pose] = None, left_free: Optional[Pose] = None, tag="") -> Pose:
    """base + rifle + arms (IK onto the rifle grips)."""
    H = ctx.H
    r_pt, l_pt = rifle_points(ctx, rifle)
    pose = base + rifle
    arms = Pose()
    for side, use, pt, pref, free in (("right", right, r_pt, prefR, right_free), ("left", left, l_pt, prefL, left_free)):
        if use:
            hand, err = H.hand(Pose(pose), side, pt, frame="body", pref=pref)
            if err > 0.8:
                print(f"   [warn] {tag} {side} hand err {err:.2f}px")
            arms = arms + hand
        elif free is not None:
            arms = arms + free
        else:
            arms = arms + Pose({k: v for k, v in IDLE_ARMS.items() if k.startswith(side)})
    return pose + arms


def finish(a: Animation, one_shot: bool = False, start: Optional[Pose] = None):
    if one_shot:
        a.start_from(start if start is not None else Pose())
    return a



def plant(ctx: Ctx, base: Pose, dy=0.0, left=(0.0, 0.0), right=(0.0, 0.0), pitch=(0.0, 0.0), x_spread=(0.0, 0.0)) -> Pose:
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
        lp, err = ctx.H.foot(pose, side, (hip[0] + sg * x_spread[i], y, hip[2] - f), pitch=pitch[i])
        out = out + lp
    return out


# ----------------------------------------------------------------------------------------------
# rifle carry poses (body space)
# ----------------------------------------------------------------------------------------------
PORT = lambda k=0.0: RIFLE_AT((-4.2, 15.6 + k, -4.6), pitch=52, yaw=-28)          # across the chest, muzzle up-left
LOW = lambda k=0.0: RIFLE_AT((-4.6, 15.2 + k, -5.8), pitch=-14, yaw=-12)          # low ready
AIM = lambda k=0.0: RIFLE_AT((-5.6, 21.2 + k, -6.8), pitch=0, yaw=-34)            # shouldered (body twisted right)
SLING = lambda: RIFLE_AT((4.2, 18.0, 5.2), pitch=64, yaw=24, roll=10)             # slung on the back
SIDE_L = lambda: RIFLE_AT((7.4, 15.4, -1.2), pitch=90, yaw=0)                     # held vertically at the left side


def build_all(mb) -> Ctx:
    ctx = Ctx(mb)
    H = ctx.H
    A = ctx.A

    def stand(lean=0.0, nod=0.0, turn=0.0, tilt=0.0, twist=0.0, dy=0.0, front=0.0, back=0.0, spread=0.6, sway=0.0,
              pitch=(0.0, 0.0), back_z=0.0):
        base = (WAIST(pos=(sway, 0, back_z), twist=twist * 0.35) + BODY(lean=lean, twist=twist * 0.65)
                + HEAD(nod=nod, turn=turn - twist * 0.0, tilt=tilt))
        return base + plant(ctx, base, dy=dy, left=(front, 0.0), right=(back, 0.0), x_spread=(spread, spread), pitch=pitch)

    # the exact pose of `idle` at t=0: every standing one-shot starts here
    idle0 = holding(ctx, stand(lean=0.0, nod=-1.0, turn=0.0, sway=0.0, spread=1.2, front=0.5, back=-0.5), PORT(0.0), tag="idle0")

    # ============================== idle ===========================================
    a = ctx.new("idle", 4.0, LOOP)
    for i in range(0, 9):
        t = 4.0 * i / 8
        w = S(TAU * i / 8)
        base = stand(lean=0.5 * w, nod=-1 + 0.5 * w, turn=2.5 * S(TAU * i / 8 + 1.0) - 2.5 * S(1.0), sway=0.3 * w, spread=1.2, front=0.5, back=-0.5)
        a.key(t, holding(ctx, base, PORT(0.15 * w), tag="idle"), "easeInOutSine")
    a.close_loop()

    # ============================== idle_alert =====================================
    a = ctx.new("idle_alert", 6.0, LOOP)
    for t, turn, tw in ((0.0, 0, 0), (1.3, -34, -10), (2.2, -34, -10), (3.6, 0, 0), (4.6, 34, 10), (5.2, 34, 10), (6.0, 0, 0)):
        base = stand(lean=3, nod=-1, turn=turn, twist=tw, spread=2.2, front=1.8, back=-1.8, dy=-0.4)
        a.key(t, holding(ctx, base, LOW(), tag="idle_alert", prefR=(30, 14, 0, 70), prefL=(30, 14, 0, 70)), "easeInOutSine")
    a.close_loop()

    # ============================== idle_rigid (parade rest) =======================
    a = ctx.new("idle_rigid", 4.0, LOOP)
    for i in range(0, 9):
        t = 4.0 * i / 8
        w = S(TAU * i / 8)
        base = stand(lean=-2.0 + 0.3 * w, nod=-3.5 + 0.2 * w, spread=3.4, dy=0.0)
        rifle = SLING()
        pose = base + rifle
        hl, _ = H.hand(Pose(pose), "left", (1.4, 14.8, 3.4), frame="body", pref=(-35, 10, 0, 85))
        hr, _ = H.hand(Pose(pose), "right", (-1.6, 14.2, 3.0), frame="body", pref=(-35, 10, 0, 85))
        a.key(t, pose + hl + hr, "easeInOutSine")
    a.close_loop()

    # ============================== walk / run =====================================
    g = Gait(cycle=1.0, d_front=6.0, d_back=5.0, stance=0.58, lift=2.0, strike_pitch=9.0, toeoff_pitch=-24.0)
    a = ctx.new("walk", 1.0, LOOP)
    st = {}
    for i in range(0, 17):
        ph = (i % 16) / 16.0
        c = C(TAU * ph)
        base = WAIST(twist=3 * c, pos=(0.0, 0, 0)) + BODY(lean=2.0, twist=-5 * c) + HEAD(nod=-2, turn=3 * c)
        legs, info = gait_legs(ctx.H, g, ph, base, reach=9.2, last=st.get("last"))
        st["last"] = {s: (info[s]["hipx"], info[s]["knee"]) for s in ("left", "right")}
        st.setdefault("info", []).append((ph, info))
        # the rifle bobs with the pelvis a little
        pose = base + legs
        a.key(1.0 * i / 16, holding(ctx, pose, PORT(0.0), tag="walk"), "linear")
    a.close_loop()
    ctx.info["walk"] = planted_speed(ctx, st, g)

    g = Gait(cycle=0.6, d_front=4.8, d_back=8.0, stance=0.40, lift=5.5, strike_pitch=14.0, toeoff_pitch=-38.0,
             swing_pitch_mid=-25.0, pelvis_mode="bounce", bounce_strike=-1.2, bounce_mid_stance=-3.0,
             bounce_off=-1.0, bounce_flight=0.8)
    a = ctx.new("run", 0.6, LOOP)
    st = {}
    for i in range(0, 13):
        ph = (i % 12) / 12.0
        c = C(TAU * ph)
        base = WAIST(twist=6 * c) + BODY(lean=11.0, twist=-9 * c) + HEAD(nod=-7, turn=5 * c)
        legs, info = gait_legs(ctx.H, g, ph, base, reach=9.2, last=st.get("last"))
        st["last"] = {s: (info[s]["hipx"], info[s]["knee"]) for s in ("left", "right")}
        st.setdefault("info", []).append((ph, info))
        pose = base + legs
        a.key(0.6 * i / 12, holding(ctx, pose, PORT(0.0), tag="run"), "linear")
    a.close_loop()
    ctx.info["run"] = planted_speed(ctx, st, g)

    # ============================== aim / fire / lower =============================
    def aim_pose(k=0.0, recoil=0.0):
        base = stand(lean=3.5 - recoil * 2.5, nod=-3 + recoil * 3, turn=-28, twist=32, spread=2.8, front=4.5, back=-4.0, dy=-0.8)
        rifle = RIFLE_AT((-5.6 + 0.0, 21.0 + k + 0.35 * recoil, -6.4 + recoil * 1.9), pitch=0.3 + recoil * 5.0, yaw=-33)
        return holding(ctx, base, rifle, tag="aim", prefR=(70, 10, 0, 60), prefL=(60, 20, 0, 40))

    a = ctx.new("aim", 2.0, LOOP)
    for i in range(0, 9):
        t = 2.0 * i / 8
        w = S(TAU * i / 8)
        a.key(t, aim_pose(0.12 * w), "easeInOutSine")
    a.close_loop()
    ctx.refs["fire"] = "aim"
    ctx.refs["lower"] = "aim"

    a = ctx.new("fire", 0.3, ONCE)
    a.key(0.0, aim_pose(), "linear")
    a.key(0.04, aim_pose(0.0, 1.0), "easeOutCubic")
    a.key(0.12, aim_pose(0.0, 0.35), "easeOutQuad")
    a.key(0.3, aim_pose(), "easeInOutSine")

    a = ctx.new("lower", 0.5, ONCE)
    a.key(0.0, aim_pose(), "linear")
    mid = stand(lean=3, nod=-1, turn=-14, twist=16, spread=2.4, front=3.0, back=-2.5, dy=-0.5)
    a.key(0.2, holding(ctx, mid, RIFLE_AT((-4.8, 17.0, -6.2), pitch=-10, yaw=-20), tag="lower"), "easeInOutSine")
    base = stand(lean=0.0, nod=-1, spread=1.2, front=0.5, back=-0.5)
    a.key(0.5, holding(ctx, base, PORT(0.0), tag="lower"), "easeInOutSine")
    ctx.refs["lower"] = "aim"

    # ============================== turns ==========================================
    def turn(name, sign):
        a = ctx.new(name, 0.4, ONCE)
        step = (L_("right" if sign < 0 else "left", hip=15, knee=24) + L_("left" if sign < 0 else "right", hip=-4, knee=6))
        def k(t, head, body, waist, legs_k, ease):
            base = stand(lean=1.0, nod=-1.0, spread=1.2, front=0.5, back=-0.5) + HEAD(turn=sign * head, nod=-1) + BODY(twist=sign * body, lean=1) + WAIST(twist=sign * waist)
            if legs_k > 0:
                base = base + step.scaled(legs_k)
            a.key(t, holding(ctx, base, PORT(0.0), tag=name), ease)
        a.key(0.0, idle0, "linear")
        k(0.10, 30, 5, 2, 0.4, "easeOutQuad")
        k(0.22, 13, 10, 6, 1.0, "easeInOutSine")
        k(0.32, 3, 3, 1.5, 0.25, "easeInOutSine")
        k(0.4, 0, 0, 0, 0.0, "easeOutSine")
        return a
    turn("turn_left", -1)
    turn("turn_right", +1)

    # ============================== pointing / gestures (rifle held low at the left side) ======
    def carry_left(nod=0.0, lean=0.0, turn=0.0, **kw):
        base = stand(lean=lean, nod=nod, turn=turn, spread=1.4, front=0.6, back=-0.6, **kw)
        return base

    def side_hold(base):
        return holding(ctx, base, SIDE_L(), right=False, left=True, prefL=(10, 8, 0, 30), tag="side_hold")

    a = ctx.new("point_forward", 1.0, HOLD)
    a.key(0.0, idle0, "linear")
    base = carry_left(nod=-1, lean=1)
    cock = side_hold(base) + A_("right", swing=25, out=14, elbow=80)
    a.key(0.2, cock, "easeInOutSine")
    base = carry_left(nod=-3, lean=2, turn=-4)
    pose = side_hold(base)
    pt, err = H.hand(Pose(pose), "right", (-5.0, 24.3, -11.4), frame="body", pref=(80, 8, 0, 6), warm=False)
    a.key(0.5, pose + pt, "easeOutBack")
    a.key(1.0, pose + pt, "linear")

    a = ctx.new("point_down", 0.8, ONCE)
    a.key(0.0, idle0, "linear")
    base = carry_left(nod=16, lean=12)
    pose = side_hold(base)
    pt, err = H.hand(Pose(pose), "right", (-3.4, 6.0, -8.6), frame="body", pref=(60, 14, 0, 20), warm=False)
    a.key(0.26, pose + pt, "easeOutBack")
    a.key(0.55, pose + pt, "linear")
    a.key(0.8, idle0, "easeInOutSine")

    # ============================== salute =========================================
    a = ctx.new("salute", 1.2, ONCE)
    a.key(0.0, idle0, "linear")
    base = stand(lean=-1, nod=-3, spread=2.0, front=0.0, back=0.0)
    pose = side_hold(base)
    pt, err = H.hand(Pose(pose), "right", (-3.6, 30.4, -5.2), frame="head" if False else "body", pref=(100, 55, 0, 125), warm=False)
    a.key(0.30, pose + pt, "easeOutBack")
    a.key(0.85, pose + pt, "linear")
    a.key(1.2, idle0, "easeInOutSine")

    # ============================== open_door ======================================
    a = ctx.new("open_door", 1.2, ONCE)
    a.key(0.0, idle0, "linear")
    base = stand(lean=3, nod=4, spread=1.4, front=1.0, back=-0.5)
    pose = side_hold(base)
    pt, err = H.hand(Pose(pose), "right", (-3.6, 22.4, -9.0), frame="body", pref=(70, 12, 0, 40), warm=False)
    a.key(0.30, pose + pt, "easeInOutSine")
    pt2, err = H.hand(Pose(pose), "right", (-3.6, 22.4, -11.0), frame="body", pref=(75, 12, 0, 25), warm=False)
    a.key(0.46, pose + pt2, "easeInQuad")
    a.key(0.58, pose + pt, "easeOutQuad")
    a.key(0.70, pose + pt2, "easeInQuad")
    a.key(0.82, pose + pt, "easeOutQuad")
    a.key(1.2, idle0, "easeInOutSine")

    # ============================== inspect ========================================
    a = ctx.new("inspect", 2.0, ONCE)
    a.key(0.0, idle0, "linear")
    for t, lean, nod, hx in ((0.55, 36, 30, -3.4), (1.0, 40, 34, -4.2), (1.45, 36, 30, -3.0)):
        base = stand(lean=lean, nod=nod, turn=-8 if t > 0.9 else 6, back_z=1.5, dy=-2.4, front=3.0, back=-1.5, spread=2.0)
        pose = base + SLING()
        hr, e = H.hand(Pose(pose), "right", (hx, 6.4, -9.0), frame="root", pref=(55, 12, 0, 30), warm=False)
        hl, e2 = H.hand(Pose(pose), "left", (5.0, 9.0, -3.0), frame="root", pref=(35, 14, 0, 30), warm=False)
        a.key(t, pose + hr + hl, "easeInOutSine")
    a.key(2.0, idle0, "easeInOutSine")

    # ============================== carry_pose =====================================
    a = ctx.new("carry_pose", 2.0, LOOP)
    for i in range(0, 9):
        t = 2.0 * i / 8
        w = S(TAU * i / 8)
        base = stand(lean=-6 + 0.5 * w, nod=6, spread=2.2, dy=-1.2 + 0.1 * w, front=1.5, back=-1.0, back_z=1.2)
        pose = base + SLING()
        hl, _ = H.hand(Pose(pose), "left", (4.6, 14.4, -9.0), frame="body", pref=(50, 14, 0, 60), warm=False)
        hr, _ = H.hand(Pose(pose), "right", (-4.6, 14.4, -9.0), frame="body", pref=(50, 14, 0, 60), warm=False)
        a.key(t, pose + hl + hr, "easeInOutSine")
    a.close_loop()

    # ============================== clap ===========================================
    a = ctx.new("clap", 1.0, ONCE)
    a.key(0.0, idle0, "linear")
    base = stand(lean=-1, nod=-2, spread=1.4, front=0.6, back=-0.6)
    pose = base + SLING()
    for t, x, ease in ((0.16, 7.4, "easeOutQuad"), (0.26, 0.9, "easeInQuad"), (0.36, 6.6, "easeOutQuad"), (0.46, 0.9, "easeInQuad"),
                       (0.56, 6.6, "easeOutQuad"), (0.66, 0.9, "easeInQuad"), (0.78, 4.0, "easeOutQuad")):
        hl, _ = H.hand(Pose(pose), "left", (x, 21.6, -5.8), frame="body", pref=(60, 14, 0, 85), warm=False)
        hr, _ = H.hand(Pose(pose), "right", (-x, 21.6, -5.8), frame="body", pref=(60, 14, 0, 85), warm=False)
        k = HEAD(nod=-2 + (2 if x < 2 else 0))
        a.key(t, pose + k + hl + hr, ease)
    a.key(1.0, idle0, "easeInOutSine")

    # ============================== wave_on (beckoning) ============================
    a = ctx.new("wave_on", 1.0, ONCE)
    a.key(0.0, idle0, "linear")
    base = stand(lean=1, nod=2, spread=1.4, front=0.5, back=-0.5, turn=-3)
    pose = side_hold(base)
    pts = [(0.2, (-4.2, 22.4, -10.8)), (0.34, (-3.4, 24.6, -6.6)), (0.48, (-4.2, 22.4, -10.8)), (0.62, (-3.4, 24.6, -6.6)), (0.76, (-4.0, 22.6, -9.2))]
    for t, p in pts:
        hr, _ = H.hand(Pose(pose), "right", p, frame="body", pref=(70, 14, 0, 55), warm=False)
        a.key(t, pose + hr + Pose(right_hand=B(rot=(-24 if p[2] > -8 else 14, 0, 0))), "easeInOutSine")
    a.key(1.0, idle0, "easeInOutSine")

    for a_ in ctx.A.anims.values():
        a_.start_from(idle0 if a_.loop != LOOP else Pose())
        if a_.loop == LOOP:
            a_.close_loop()
    return ctx


def build_and_write(mb) -> None:
    ctx = build_all(mb)
    ctx.A.write(str(ANIM_DIR / "guard.animation.json"))
    jsonfmt.write(str(HERE / "guard_anim_meta.json"), {"refs": ctx.refs, "info": ctx.info})
    print(f"guard: {len(ctx.A.anims)} animations")
