"""Final-game / combat family + misc (sit_idle, attention, hands_up)."""
from __future__ import annotations

import math

from common import *  # noqa: F401,F403
from contestant_anim import (IDLE0, PREF_DOWN, PREF_FACE, PREF_FRONT, PREF_GUARD, PREF_HIPS, PREF_UP, Ctx, K,
                             finish, plant)
from lib.anim import HOLD, LOOP, ONCE, B, Pose
from lib.gait import Gait, gait_legs
from lib.humanoid import ARM, ARMS, BODY, HEAD, LEG, LEGS, ROOT, WAIST

TAU = 2 * math.pi
S = math.sin
C = math.cos

GUARD_L = (3.4, 27.6, -9.0)     # lead (left) fist
GUARD_R = (-2.2, 26.6, -5.8)    # rear (right) fist


def build(ctx: Ctx) -> None:
    H = ctx.H

    def stance(twist=22.0, lean=6.0, nod=8.0, dy=-1.6, front=6.0, back=-6.0, sway=0.0, hl=GUARD_L, hr=GUARD_R,
               spread=2.0, pref=PREF_GUARD, head_turn_extra=0.0):
        base = (WAIST(pos=(sway, 0, 0.0), twist=twist * 0.35) + BODY(lean=lean, twist=twist * 0.65)
                + HEAD(nod=nod, turn=-twist + head_turn_extra))
        legs = plant(ctx, base, dy=dy, left=(front, 0.0), right=(back, 0.0), x_spread=(spread, spread), pitch=(0.0, -6.0))
        base = base + legs
        hands = H.hands(Pose(base), left=hl, right=hr, frame="body", prefs=dict(left=pref, right=pref))
        if any(e > 0.8 for e in hands.errs):
            print(f"   [warn] stance hand err {[round(e,2) for e in hands.errs]}")
        return base + hands

    # ============================== fight_stance ===================================
    a = ctx.new("fight_stance", 1.0, LOOP)
    for t, dy, sw, tw in ((0.0, -1.2, 0.0, 22), (0.25, -2.0, 0.3, 23), (0.5, -1.2, 0.0, 22), (0.75, -2.0, -0.3, 21), (1.0, -1.2, 0.0, 22)):
        a.key(t, stance(dy=dy, sway=sw, twist=tw, hl=(GUARD_L[0], GUARD_L[1] + (0.5 if dy < -1.5 else 0), GUARD_L[2]),
                        hr=(GUARD_R[0], GUARD_R[1] + (0.4 if dy < -1.5 else 0), GUARD_R[2])), "easeInOutSine")

    # ============================== punches ========================================
    def punch_keys():
        keys = []
        # t, pose, ease
        guard_l = (2.6, 26.2, -5.6)
        base = BODY(lean=2, twist=8) + HEAD(nod=4, turn=-3) + WAIST(twist=2) + LEG("left", hip=4, knee=6) + LEG("right", hip=-3, knee=8)
        h = H.hands(Pose(base), left=guard_l, right=(-2.4, 25.4, -4.4), frame="body", prefs=dict(left=PREF_GUARD, right=PREF_GUARD))
        keys.append((0.07, base + h, "easeOutQuad"))                                             # cock the fist
        base = (BODY(lean=10, twist=-26) + HEAD(nod=4, turn=18) + WAIST(twist=-12) + LEG("left", hip=22, knee=16, ankle=6)
                + LEG("right", hip=-14, knee=16, ankle=-18))
        h = H.hands(Pose(base), left=(2.2, 26.0, -5.4), right=(-2.6, 25.6, -17.8), frame="body",
                    prefs=dict(left=PREF_GUARD, right=(78, 6, 0, 8)))
        keys.append((0.14, base + h, "easeOutBack"))                                              # impact
        keys.append((0.20, base + h, "linear"))
        base = BODY(lean=5, twist=-10) + HEAD(nod=3, turn=8) + WAIST(twist=-5) + LEG("left", hip=10, knee=10) + LEG("right", hip=-6, knee=12)
        h = H.hands(Pose(base), left=(2.8, 24.4, -4.8), right=(-3.4, 22.4, -8.6), frame="body",
                    prefs=dict(left=PREF_GUARD, right=(60, 8, 0, 45)))
        keys.append((0.27, base + h, "easeInOutSine"))
        return keys

    keys = punch_keys()
    a = ctx.new("punch_right", 0.35, ONCE)
    a.key(0.0, Pose())
    for t, p, e in keys:
        a.key(t, p, e)
    a.key(0.35, Pose(), "easeInOutSine")
    finish(a, one_shot=True)
    a = ctx.new("punch_left", 0.35, ONCE)
    a.key(0.0, Pose())
    for t, p, e in keys:
        a.key(t, p.mirrored(), e)
    a.key(0.35, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    # ============================== shove ==========================================
    a = ctx.new("shove", 0.5, ONCE)
    a.key(0.0, Pose())
    base = BODY(lean=-5) + HEAD(nod=0) + LEG("left", hip=-3, knee=8) + LEG("right", hip=-6, knee=14)
    h = H.hands(Pose(base), left=(3.4, 21.0, -4.4), right=(-3.4, 21.0, -4.4), frame="body", prefs=dict(left=PREF_FRONT, right=PREF_FRONT))
    a.key(0.11, base + h, "easeOutQuad")
    base = BODY(lean=19) + HEAD(nod=3) + WAIST(pos=(0, -0.8, 0)) + LEG("left", hip=28, knee=22, ankle=6) + LEG("right", hip=-18, knee=22, ankle=-22)
    h = H.hands(Pose(base), left=(3.4, 22.6, -14.4), right=(-3.4, 22.6, -14.4), frame="body", prefs=dict(left=(70, 8, 0, 10), right=(70, 8, 0, 10)))
    a.key(0.22, base + h, "easeOutBack")
    a.key(0.32, base + h, "linear")
    base = BODY(lean=9) + HEAD(nod=2) + WAIST(pos=(0, -0.3, 0)) + LEG("left", hip=14, knee=14) + LEG("right", hip=-8, knee=12)
    h = H.hands(Pose(base), left=(3.4, 21.0, -9.0), right=(-3.4, 21.0, -9.0), frame="body", prefs=dict(left=PREF_FRONT, right=PREF_FRONT))
    a.key(0.4, base + h, "easeInOutSine")
    a.key(0.5, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    # ============================== block ==========================================
    a = ctx.new("block", 1.0, LOOP)
    for t, k in ((0.0, 0.0), (0.25, 1.0), (0.5, 0.0), (0.75, -1.0), (1.0, 0.0)):
        base = BODY(lean=17 + 1.2 * k, twist=-4) + HEAD(nod=16 + 1.0 * k, turn=3) + WAIST(pos=(0, 0, 0.8))
        base = base + plant(ctx, base, dy=-2.2 + 0.2 * k, left=(3.5, 0.0), right=(-2.5, 0.0), x_spread=(1.8, 1.8), pitch=(0, -6))
        h = H.hands(Pose(base), left=(3.5, 29.6 + 0.3 * k, -7.2), right=(-3.2, 29.0 + 0.3 * k, -7.2), frame="body",
                    prefs=dict(left=(60, 25, 0, 125), right=(60, 25, 0, 125)))
        if any(e > 0.8 for e in h.errs):
            print(f"   [warn] block hand err {[round(e,2) for e in h.errs]}")
        a.key(t, base + h, "easeInOutSine")

    # ============================== dodges =========================================
    def dodge(name, side):
        sg = +1 if side == "left" else -1          # file +x is the entity's left
        a = ctx.new(name, 0.4, ONCE)
        a.key(0.0, Pose())
        p1 = (WAIST(pos=(4.4 * sg, -0.8, 0), tilt=-8 * sg) + BODY(lean=5, tilt=16 * sg, twist=-6 * sg) + HEAD(nod=3, tilt=-14 * sg, turn=8 * sg)
              + LEG(side, hip=-2, knee=26, out=22) + LEG("right" if side == "left" else "left", hip=-4, knee=10, out=-3)
              + ARM(side, swing=-20, out=52, elbow=22) + ARM("right" if side == "left" else "left", swing=34, out=-18, elbow=40))
        a.key(0.09, p1, "easeOutCubic")
        a.key(0.19, p1.scaled(0.9), "easeInOutSine")
        a.key(0.30, p1.scaled(0.35), "easeInOutSine")
        a.key(0.4, Pose(), "easeInOutSine")
        finish(a, one_shot=True)
    dodge("dodge_left", "left")
    dodge("dodge_right", "right")

    # ============================== knocked_back ===================================
    a = ctx.new("knocked_back", 0.6, ONCE)
    a.key(0.0, Pose())
    a.key(0.07, ROOT(pos=(0, 0.6, 2.2)) + BODY(lean=-22) + HEAD(nod=-16) + WAIST(pos=(0, 0, 1.0)) + LEG("left", hip=14, knee=14, ankle=8)
          + LEG("right", hip=20, knee=20, ankle=8) + ARM("left", swing=58, out=44, elbow=26) + ARM("right", swing=64, out=48, elbow=24), "easeOutCubic")
    a.key(0.24, ROOT(pos=(0, 0, 3.4)) + BODY(lean=-14, twist=6) + HEAD(nod=-10) + WAIST(pos=(0, -0.6, 0)) + LEG("left", hip=-16, knee=18)
          + LEG("right", hip=22, knee=12) + ARM("left", swing=30, out=64, elbow=22) + ARM("right", swing=40, out=60, elbow=22), "easeInOutSine")
    a.key(0.42, ROOT(pos=(0, 0, 2.0)) + BODY(lean=-4) + HEAD(nod=-2) + LEG("left", hip=6, knee=14) + LEG("right", hip=-8, knee=18)
          + ARM("left", swing=14, out=36, elbow=18) + ARM("right", swing=14, out=36, elbow=18), "easeInOutSine")
    a.key(0.6, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    # ============================== sprint_attack ==================================
    g = Gait(cycle=0.5, d_front=4.8, d_back=8.4, stance=0.38, lift=5.8, strike_pitch=14.0, toeoff_pitch=-40.0,
             swing_pitch_mid=-28.0, pelvis_mode="bounce", bounce_strike=-1.6, bounce_mid_stance=-3.4,
             bounce_off=-1.2, bounce_flight=0.6)
    from ca_loco import gait_pose
    a = ctx.new("sprint_attack", 0.5, LOOP)
    st = {}
    a.cycle(lambda ph: gait_pose(ctx, g, ph, st, lean=30.0, arm_amp=52, elbow=95, twist=9, shoulder_counter=13, sway=0.5,
                                 arm_out=8, arm_bias=4, roll=1.0, elbow_gain=0.2, head_nod=14.0), 10)
    a.close_loop()

    # ============================== sit_idle =======================================
    a = ctx.new("sit_idle", 4.0, LOOP)
    for i in range(0, 9):
        t = 4.0 * i / 8
        w = S(TAU * i / 8)
        glance = (0, 0, -16, -16, 0, 12, 12, 0, 0)[i]
        base = WAIST(pos=(0, 0, 0.0)) + BODY(lean=7 + 0.7 * w) + HEAD(nod=4 + 1.0 * w, turn=glance, tilt=glance * 0.1)
        base = base + plant(ctx, base, dy=-6.0, left=(6.0, 0.0), right=(6.0, 0.0), x_spread=(0.9, 0.9))
        h = H.hands(Pose(base), left=(3.2, 9.6, -4.6), right=(-3.2, 9.6, -4.6), frame="root",
                    prefs=dict(left=(30, 14, 0, 40), right=(30, 14, 0, 40)))
        if i == 0 and any(e > 0.8 for e in h.errs):
            print(f"   [warn] sit_idle hand err {[round(e,2) for e in h.errs]}")
        a.key(t, base + h, "easeInOutSine")
    a.close_loop()

    # ============================== attention ======================================
    a = ctx.new("attention", 4.0, LOOP)
    for i in range(0, 9):
        t = 4.0 * i / 8
        w = S(TAU * i / 8)
        base = BODY(lean=-1.5 + 0.4 * w, pos=(0, 0.05 * w, 0)) + HEAD(nod=-6 + 0.3 * w) + WAIST()
        base = base + LEGS(hip=0, knee=0, out=1.2)
        arms = ARM("left", swing=0, out=1.8, elbow=3, twist=0) + ARM("right", swing=0, out=1.8, elbow=3, twist=0)
        a.key(t, base + arms, "easeInOutSine")
    a.close_loop()

    # ============================== hands_up =======================================
    a = ctx.new("hands_up", 2.0, LOOP)
    for i in range(0, 9):
        t = 2.0 * i / 8
        w = S(TAU * i / 8)
        w2 = S(TAU * 3 * i / 8)
        base = BODY(lean=-2.0 + 0.5 * w) + HEAD(nod=3 + 0.6 * w, turn=2 * w) + WAIST(pos=(0, -0.4, 0))
        base = base + plant(ctx, base, dy=-0.5, left=(0.8, 0.0), right=(0.4, 0.0), x_spread=(1.2, 1.2))
        h = H.hands(Pose(base), left=(6.8 + 0.3 * w2, 31.4 + 0.4 * w, -2.8), right=(-6.8 - 0.3 * w2, 31.4 + 0.4 * w, -2.8),
                    frame="body", prefs=dict(left=(100, 40, 0, 105), right=(100, 40, 0, 105)))
        if i == 0 and any(e > 0.8 for e in h.errs):
            print(f"   [warn] hands_up hand err {[round(e,2) for e in h.errs]}")
        a.key(t, base + h, "easeInOutSine")
    a.close_loop()
