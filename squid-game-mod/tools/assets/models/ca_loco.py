"""Locomotion family: idle*, walk, run, sneak_walk, turns, skid, freeze*, jump/fall/land."""
from __future__ import annotations

import math

from common import *  # noqa: F401,F403
from contestant_anim import (IDLE0, PREF_DOWN, PREF_FACE, PREF_FRONT, PREF_GUARD, PREF_HIPS, PREF_UP, Ctx, K,
                             finish, plant)
from lib.anim import HOLD, LOOP, ONCE, B, Pose
from lib.gait import Gait, gait_legs, planted_speed as _ps, smoothstep
from lib.humanoid import ARM, ARMS, BODY, HEAD, LEG, LEGS, ROOT, WAIST

TAU = 2 * math.pi
S = math.sin
C = math.cos
PX_TO_BLOCKS = 0.9375 / 16.0


# ----------------------------------------------------------------------------------------------
# gait pose
# ----------------------------------------------------------------------------------------------
def gait_pose(ctx: Ctx, g: Gait, ph: float, st: dict, *, lean: float, arm_amp: float, elbow: float,
              twist: float = 4.0, shoulder_counter: float = 9.0, sway: float = 0.6, reach: float = 9.25,
              hip_cap: float = 12.0, head_nod: float = 0.0, arm_out: float = 5.0, arm_bias: float = 3.0,
              roll: float = 1.2, elbow_gain: float = 0.9, extra: Pose = None) -> Pose:
    c = C(TAU * ph)
    ul = (ph + g.lead) % 1.0
    ur = (ph + g.lead + 0.5) % 1.0
    wL = S(math.pi * min(ul / g.stance, 1.0)) if ul < g.stance else 0.0
    wR = S(math.pi * min(ur / g.stance, 1.0)) if ur < g.stance else 0.0
    side_w = wL - wR                                  # +1: weight on the left foot
    base = (WAIST(twist=twist * c, pos=(sway * side_w, 0, 0))
            + BODY(lean=lean, twist=-shoulder_counter * c, tilt=-roll * side_w)
            + HEAD(nod=head_nod - lean * 0.7, turn=(shoulder_counter - twist) * c * 0.85, tilt=roll * 0.5 * side_w))
    legs, info = gait_legs(ctx.H, g, ph, base, reach=reach, hip_cap=hip_cap, last=st.get("last"))
    st["last"] = {s: (info[s]["hipx"], info[s]["knee"]) for s in ("left", "right")}
    st.setdefault("info", []).append((ph, info))
    arms = Pose()
    for side, sign in (("right", +1), ("left", -1)):
        swing = sign * arm_amp * c + arm_bias
        fwd = 0.5 + 0.5 * sign * C(TAU * (ph - 0.09))
        arms = arms + ARM(side, swing=swing, out=arm_out, elbow=elbow * (0.55 + elbow_gain * fwd))
    out = base + legs + arms
    if extra:
        out = out + extra
    return out


# ----------------------------------------------------------------------------------------------
def build(ctx: Ctx) -> None:
    A = ctx.A

    # ============================== idle ===========================================
    a = ctx.new("idle", 4.0, LOOP)

    def idle_fn(ph):
        b = S(TAU * ph)
        sw = S(TAU * ph + 0.6) - S(0.6)
        return (BODY(lean=0.7 * b, pos=(0, 0.08 * b, 0), twist=0.8 * b)
                + WAIST(pos=(0.25 * sw, 0, 0), tilt=0.5 * sw)
                + HEAD(nod=-0.6 * b, turn=1.8 * (S(TAU * ph + 1.2) - S(1.2)), tilt=0.7 * sw)
                + ARM("left", swing=2 + 1.4 * b, out=5 + 0.8 * b, elbow=9 + 1.2 * (S(TAU * ph + 0.5) - S(0.5)))
                + ARM("right", swing=2 - 1.0 * b, out=5 + 0.8 * b, elbow=9 + 1.2 * (S(TAU * ph + 2.0) - S(2.0))))
    a.cycle(idle_fn, 16)
    a.close_loop()

    # ============================== walk ===========================================
    # The loop is 1.0 s and contains TWO gait cycles (0.5 s each): with 12 px legs a stance foot can only sweep
    # ~12 px, which plants it at the contract's reference speed of 2.4 blocks/s only at this cadence (4 steps/s).
    g = Gait(cycle=0.5, d_front=5.4, d_back=6.9, stance=0.60, lift=1.9, strike_pitch=11.0, toeoff_pitch=-28.0)
    a = ctx.new("walk", 1.0, LOOP)
    st = {}
    a.cycle(lambda ph: gait_pose(ctx, g, (2 * ph) % 1.0, st, lean=3.0, arm_amp=15, elbow=12, twist=3.5,
                                 shoulder_counter=7, sway=0.5, reach=9.2, roll=1.0), 16)
    a.close_loop()
    ctx.info["walk"] = _ps(ctx, st, g, span=1.0)

    # ============================== run ============================================
    g = Gait(cycle=0.6, d_front=5.2, d_back=8.8, stance=0.24, lift=5.8, strike_pitch=14.0, toeoff_pitch=-38.0,
             swing_pitch_mid=-26.0, pelvis_mode="bounce", bounce_strike=-1.2, bounce_mid_stance=-3.2,
             bounce_off=-1.0, bounce_flight=1.0)
    a = ctx.new("run", 0.6, LOOP)
    st = {}
    a.cycle(lambda ph: gait_pose(ctx, g, ph, st, lean=12.0, arm_amp=42, elbow=85, twist=7, shoulder_counter=12,
                                 sway=0.5, arm_out=7, arm_bias=6, roll=1.0, elbow_gain=0.25), 24)
    a.close_loop()
    ctx.info["run"] = _ps(ctx, st, g, span=0.6)

    # ============================== sneak_walk =====================================
    g = Gait(cycle=1.2, d_front=4.4, d_back=3.8, stance=0.66, lift=1.8, strike_pitch=8.0, toeoff_pitch=-22.0,
             swing_pitch_mid=-4.0)
    a = ctx.new("sneak_walk", 1.2, LOOP)
    st = {}
    a.cycle(lambda ph: gait_pose(ctx, g, ph, st, lean=13.0, arm_amp=9, elbow=50, twist=3, shoulder_counter=5,
                                 sway=0.5, reach=8.2, hip_cap=9.7, head_nod=5.0, arm_out=14, arm_bias=22,
                                 roll=1.6, elbow_gain=0.3), 16)
    a.close_loop()
    ctx.info["sneak_walk"] = _ps(ctx, st, g, span=1.2)


    # ============================== idle_nervous ===================================
    a = ctx.new("idle_nervous", 5.0, LOOP)
    FR_L, FR_R = (1.4, 15.8, -4.6), (-1.4, 15.5, -4.6)      # clasped hands in front of the belly
    prefs = dict(prefL=(25.0, 10.0, 0.0, 85.0), prefR=(25.0, 10.0, 0.0, 85.0))
    seq = [
        # t,   body lean, head(nod,turn,tilt), waist x, legs, hands
        (0.0, 0.0, (0, 0, 0), 0.0, None),
        (0.6, 2.5, (7, 0, 1), 0.0, (FR_L, FR_R)),
        (1.1, 2.0, (4, -26, -2), 0.5, ((2.0, 16.0, -4.4), (-0.6, 15.2, -4.9))),
        (1.8, 3.0, (10, -6, 0), 0.5, ((1.0, 15.6, -4.9), (-2.0, 15.8, -4.4))),
        (2.4, 2.0, (3, 30, 2), -0.8, (FR_L, FR_R)),
        (3.1, 2.5, (6, 8, 0), -0.8, ((2.0, 15.8, -4.4), (-0.8, 15.3, -4.9))),
        (3.7, 5.0, (20, 0, 0), 0.0, ((1.6, 15.0, -4.2), (-1.6, 14.8, -4.2))),
        (4.3, 2.0, (6, -4, 0), 0.0, (FR_L, FR_R)),
        (5.0, 0.0, (0, 0, 0), 0.0, None),
    ]
    for t, lean, (hn, ht, htl), wx, hands in seq:
        base = (BODY(lean=lean) + WAIST(pos=(wx, 0, 0), tilt=-wx * 1.4) + HEAD(nod=hn, turn=ht, tilt=htl))
        shift = wx / 0.8
        base = base + LEG("left", hip=0, knee=max(0.0, -shift) * 7) + LEG("right", hip=0, knee=max(0.0, shift) * 7)
        if hands is None:
            K(ctx, a, t, base, L=IDLE0.only(["left_arm", "left_forearm"]), R=IDLE0.only(["right_arm", "right_forearm"]))
        else:
            K(ctx, a, t, base, L=hands[0], R=hands[1], **prefs)
    a.close_loop()

    # ============================== idle_confident =================================
    a = ctx.new("idle_confident", 4.0, LOOP)
    seq = [
        (0.0, -2.0, (-3, 0, 0), 0.0),
        (1.0, -3.5, (-5, 10, 1), 0.2),
        (2.0, -2.5, (-4, -10, -1), 0.6),
        (3.0, -3.0, (-5, 2, 0), 0.0),
        (4.0, -2.0, (-3, 0, 0), 0.0),
    ]
    for t, lean, (hn, ht, htl), wx in seq:
        base = (BODY(lean=lean, twist=ht * 0.25) + WAIST(pos=(wx, 0, 0), tilt=-wx * 1.2) + HEAD(nod=hn, turn=ht, tilt=htl))
        base = base + plant(ctx, base, dy=0.0, x_spread=(1.6, 1.6), left=(0.0, 0.0), right=(1.0, 0.0))
        K(ctx, a, t, base, L=(5.8, 13.5, 0.4), R=(-5.8, 13.5, 0.4), prefL=PREF_HIPS, prefR=PREF_HIPS)
    a.close_loop()

    # ============================== freeze_balance =================================
    gw = Gait(cycle=1.0, d_front=6.2, d_back=5.2, stance=0.58, lift=2.2, strike_pitch=10.0, toeoff_pitch=-26.0)
    a = ctx.new("freeze_balance", 1.6, LOOP)
    st = {}
    for t, k in ((0.0, 0.0), (0.4, 1.0), (0.8, 0.0), (1.2, -1.0), (1.6, 0.0)):
        # frozen mid-stride (walk phase 0.04): tense, leaning forward, one arm out for balance
        ph = 0.04
        base = (WAIST(twist=-2.0 + 0.6 * k) + BODY(lean=8.0 + 0.7 * k, twist=3.0 + 0.5 * k, tilt=1.0 * k)
                + HEAD(nod=-5 + 0.5 * k, turn=-2.0 - 0.5 * k, tilt=-2.0 + 0.5 * k))
        legs, info = gait_legs(ctx.H, gw, ph, base, reach=9.0)
        arms = (ARM("right", swing=-12 + 2.0 * k, out=62 + 2.5 * k, elbow=14, twist=0)
                + ARM("left", swing=28 + 1.5 * k, out=30 - 1.5 * k, elbow=32))
        a.key(t, base + legs + arms, "easeInOutSine")
    a.close_loop()

    # ============================== freeze_stiff ===================================
    a = ctx.new("freeze_stiff", 2.0, LOOP)
    for i in range(0, 13):
        t = 2.0 * i / 12
        w = S(TAU * 3 * i / 12)
        base = (BODY(lean=0.5 + 0.35 * w, pos=(0, 0.05 * w, 0)) + HEAD(nod=5 + 0.3 * w, turn=0.0, tilt=0.0)
                + WAIST(pos=(0, 0, 0)))
        arms = (ARM("left", swing=0, out=2.0 + 0.6 * w, elbow=4, twist=0) + ARM("right", swing=0, out=2.0 - 0.6 * w, elbow=4))
        a.key(t, base + arms, "linear")
    a.close_loop()

    # ============================== turns ==========================================
    def turn(name, sign):
        a = ctx.new(name, 0.4, ONCE)
        s = sign                          # +1 turn right, -1 turn left
        step = LEG("right" if s < 0 else "left", hip=16, knee=26) + LEG("left" if s < 0 else "right", hip=-4, knee=6)
        a.key(0.0, Pose())
        a.key(0.10, HEAD(turn=s * 30, nod=2, tilt=-s * 3) + BODY(twist=s * 5) + WAIST(twist=s * 2) + step.scaled(0.4)
              + ARM("left", swing=2, out=7, elbow=10) + ARM("right", swing=2, out=7, elbow=10), "easeOutQuad")
        a.key(0.22, HEAD(turn=s * 14, nod=1, tilt=-s * 1.5) + BODY(twist=s * 11, lean=2) + WAIST(twist=s * 6) + step
              + ARM("left", swing=6, out=12 if s > 0 else 4, elbow=12) + ARM("right", swing=6, out=4 if s > 0 else 12, elbow=12), "easeInOutSine")
        a.key(0.32, HEAD(turn=s * 3) + BODY(twist=s * 3) + WAIST(twist=s * 1.5) + step.scaled(0.25), "easeInOutSine")
        a.key(0.4, Pose(), "easeOutSine")
        finish(a, one_shot=True)
        return a
    turn("turn_left", -1)
    turn("turn_right", +1)

    # ============================== stop_skid ======================================
    a = ctx.new("stop_skid", 0.5, ONCE)
    a.key(0.0, Pose())
    skid = (WAIST(pos=(0, -1.2, 0)) + BODY(lean=-11, tilt=0) + HEAD(nod=-6)
            + LEG("left", hip=30, knee=8, ankle=14) + LEG("right", hip=14, knee=34, ankle=6))
    arms = ARM("left", swing=30, out=55, elbow=14) + ARM("right", swing=14, out=62, elbow=10)
    a.key(0.07, skid + arms, "easeOutCubic")
    a.key(0.2, skid.scaled(1.05) + arms.scaled(1.08), "easeInOutSine")
    a.key(0.34, skid.scaled(0.45) + arms.scaled(0.5), "easeInOutSine")
    a.key(0.5, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    # ============================== jump_leap ======================================
    a = ctx.new("jump_leap", 0.6, HOLD)
    a.key(0.0, Pose())
    base = BODY(lean=20) + HEAD(nod=-10)
    a.key(0.10, base + plant(ctx, base, dy=-3.6, left=(3.5, 0.0), right=(3.5, 0.0), x_spread=(0.6, 0.6))
          + ARM("left", swing=-35, out=14, elbow=14) + ARM("right", swing=-35, out=14, elbow=14), "easeInOutSine")
    a.key(0.22, ROOT(pos=(0, 2.4, 0)) + WAIST(pos=(0, 0.3, 0)) + BODY(lean=8) + HEAD(nod=-12) + LEG("left", hip=-14, knee=4, ankle=-35)
          + LEG("right", hip=-8, knee=10, ankle=-30)
          + ARM("left", swing=155, out=24, elbow=12) + ARM("right", swing=150, out=26, elbow=12), "easeOutCubic")
    a.key(0.40, ROOT(pos=(0, 2.0, 0)) + WAIST(pos=(0, 0.0, 0)) + BODY(lean=12) + HEAD(nod=-8) + LEG("left", hip=62, knee=92, ankle=-10)
          + LEG("right", hip=48, knee=78, ankle=-12)
          + ARM("left", swing=75, out=34, elbow=30) + ARM("right", swing=82, out=30, elbow=34), "easeInOutSine")
    a.key(0.6, ROOT(pos=(0, 1.0, 0)) + BODY(lean=14) + HEAD(nod=-6) + LEG("left", hip=44, knee=22, ankle=20) + LEG("right", hip=38, knee=30, ankle=16)
          + ARM("left", swing=52, out=44, elbow=16) + ARM("right", swing=58, out=42, elbow=18), "easeInOutSine")
    finish(a, one_shot=True)

    # ============================== fall_loop ======================================
    a = ctx.new("fall_loop", 0.5, LOOP)
    for i in range(0, 13):
        ph = (i % 12) / 12.0
        t = 0.5 * i / 12
        w = S(TAU * ph)
        w2 = S(TAU * ph + 2.0)
        base = (BODY(lean=-8 + 3 * S(TAU * ph * 2), tilt=5 * w) + HEAD(nod=14 + 5 * w2, turn=6 * w) + WAIST(twist=6 * w))
        legs = (LEG("left", hip=30 * w, knee=40 + 30 * (0.5 - 0.5 * C(TAU * ph)), ankle=-20)
                + LEG("right", hip=-30 * w, knee=40 + 30 * (0.5 + 0.5 * C(TAU * ph)), ankle=-20, out=6))
        arms = (ARM("left", swing=95 * S(TAU * ph + 0.5), out=48, elbow=20 + 15 * w2)
                + ARM("right", swing=95 * S(TAU * ph + 0.5 + math.pi * 0.8), out=48, elbow=20 - 15 * w2))
        a.key(t, base + legs + arms, "linear")
    a.close_loop()

    # ============================== land ===========================================
    a = ctx.new("land", 0.35, ONCE)
    a.key(0.0, Pose())
    base = BODY(lean=26) + HEAD(nod=-12) + WAIST(pos=(0, 0, 0))
    crouch = plant(ctx, base, dy=-4.8, left=(3.0, 0.0), right=(3.0, 0.0), pitch=(0.0, 0.0), x_spread=(0.8, 0.8))
    arms = ARM("left", swing=42, out=34, elbow=24) + ARM("right", swing=42, out=34, elbow=24)
    a.key(0.09, base + crouch + arms, "easeOutCubic")
    a.key(0.2, (BODY(lean=14) + HEAD(nod=-5)) + plant(ctx, BODY(lean=14), dy=-2.4, left=(1.5, 0.0), right=(1.5, 0.0))
          + ARM("left", swing=20, out=18, elbow=16) + ARM("right", swing=20, out=18, elbow=16), "easeInOutSine")
    a.key(0.35, Pose(), "easeInOutSine")
    finish(a, one_shot=True)
