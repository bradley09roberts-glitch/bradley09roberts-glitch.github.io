"""Game-specific families: tug of war, dalgona, marbles, glass bridge."""
from __future__ import annotations

import math

from common import *  # noqa: F401,F403
from contestant_anim import (IDLE0, PREF_DOWN, PREF_FACE, PREF_FRONT, PREF_GUARD, PREF_HIPS, PREF_UP, Ctx, K,
                             finish, plant)
from lib.anim import HOLD, LOOP, ONCE, B, Pose
from lib.humanoid import ARM, ARMS, BODY, HEAD, LEG, LEGS, ROOT, WAIST

TAU = 2 * math.pi
S = math.sin
C = math.cos


def build(ctx: Ctx) -> None:
    H = ctx.H

    # =====================================================================================
    # TUG OF WAR  (hands stay on a rope that runs forward at chest-waist height; targets in ROOT space)
    # =====================================================================================
    ROPE_L = (0.8, 17.2, -1.4)      # left hand
    ROPE_R = (-1.2, 16.8, -3.6)     # right hand (further forward on the rope)

    def pull_pose(lean, dz=0.0, dy=-2.6, head=-8.0, front=7.0, back=-6.0, sway=0.0, pull=0.0, twist=-14.0, rope_dy=0.0,
                  shake=0.0, ease_hands=True):
        """Braced tug-of-war stance.  ``pull`` moves both hands back along the rope (px)."""
        base = (WAIST(pos=(sway, 0, 3.2 + dz), twist=twist * 0.5) + BODY(lean=lean, twist=twist * 0.5 + shake)
                + HEAD(nod=head - lean * 0.5 + 6, turn=-twist * 0.6))
        legs = plant(ctx, base, dy=dy, left=(front, 0.0), right=(back, 0.0), pitch=(2.0, -10.0), x_spread=(2.2, 2.2))
        base = base + legs
        hl = (ROPE_L[0], ROPE_L[1] + rope_dy, ROPE_L[2] + pull)
        hr = (ROPE_R[0], ROPE_R[1] + rope_dy, ROPE_R[2] + pull)
        hands = H.hands(Pose(base), left=hl, right=hr, frame="root",
                        prefs=dict(left=(45, 14, 0, 28), right=(45, 14, 0, 28)))
        if any(e > 0.8 for e in hands.errs):
            print(f"   [warn] pull_pose hand err {[round(e,2) for e in hands.errs]} lean={lean}")
        return base + hands

    a = ctx.new("pull_idle", 1.6, LOOP)
    for t, lean, sw, pl in ((0.0, -22, 0.0, 0.0), (0.4, -24, 0.3, 0.6), (0.8, -22.5, 0.0, 0.0), (1.2, -24.5, -0.3, 0.6), (1.6, -22, 0.0, 0.0)):
        a.key(t, pull_pose(lean, sway=sw, pull=pl), "easeInOutSine")
    ctx.refs["pull_heave"] = "pull_idle"
    ctx.refs["pull_slip"] = "pull_idle"

    a = ctx.new("pull_heave", 0.5, ONCE)
    a.key(0.0, pull_pose(-22), "linear")
    a.key(0.10, pull_pose(-12, pull=-1.0, dy=-3.4, head=-2, front=8.0, back=-7.0), "easeOutQuad")            # wind up forward
    a.key(0.22, pull_pose(-40, pull=4.5, dy=-3.0, head=-14, front=7.0, back=-5.0, dz=2.0), "easeOutCubic")  # heave back
    a.key(0.36, pull_pose(-33, pull=3.0, dy=-2.8, head=-10, dz=1.0), "easeInOutSine")
    a.key(0.5, pull_pose(-22), "easeInOutSine")

    a = ctx.new("pull_strain", 0.8, LOOP)
    for i in range(0, 17):
        t = 0.8 * i / 16
        w = S(TAU * 2 * i / 16)
        w2 = S(TAU * 2 * i / 16 + 1.3)
        a.key(t, pull_pose(-30 + 1.2 * w, dy=-3.6 + 0.2 * w2, head=-22 + 2.0 * w2, pull=1.4 + 0.5 * w2, sway=0.4 * w,
                           shake=1.4 * w2, front=6.5, back=-5.5), "linear")
    a.close_loop()

    a = ctx.new("pull_slip", 0.8, ONCE)
    a.key(0.0, pull_pose(-22), "linear")
    a.key(0.12, pull_pose(-30, front=9.0, back=-4.0, dy=-3.0, dz=1.5, pull=0.5, head=-14), "easeOutQuad")     # feet start sliding
    a.key(0.26, pull_pose(-44, front=12.5, back=-2.0, dy=-4.0, dz=3.5, pull=-3.0, head=-20), "easeOutCubic")  # grip lost, arms stretch
    a.key(0.42, pull_pose(-36, front=11.0, back=-3.0, dy=-3.6, dz=2.5, pull=-1.0, head=-16), "easeInOutSine")
    a.key(0.62, pull_pose(-26, front=8.0, back=-5.5, dy=-2.8, dz=0.8, head=-10), "easeInOutSine")
    a.key(0.8, pull_pose(-22), "easeInOutSine")

    # =====================================================================================
    # DALGONA  (seated: waist -6, thighs horizontal, shins down; hands over the table in front)
    # =====================================================================================
    def seated(lean, nod, tilt=0.0, twist=0.0, back=0.0, dy=-6.0):
        base = WAIST(pos=(0, 0, back)) + BODY(lean=lean, twist=twist) + HEAD(nod=nod, tilt=tilt, turn=-twist * 0.5)
        legs = plant(ctx, base, dy=dy, left=(6.0, 0.0), right=(6.0, 0.0), x_spread=(0.9, 0.9))
        return base + legs

    TIN = (2.6, 9.6, -8.6)         # left hand: holds the tin on the table
    NEEDLE = (-1.8, 10.2, -9.2)    # right hand: needle

    def dal_pose(lean=18, nod=24, tilt=0.0, tin=TIN, needle=NEEDLE, twist=0.0, back=0.0, extra=None):
        base = seated(lean, nod, tilt, twist, back)
        hands = H.hands(Pose(base), left=tin, right=needle, frame="root",
                        prefs=dict(left=(40, 14, 0, 55), right=(40, 14, 0, 55)))
        if any(e > 0.8 for e in hands.errs):
            print(f"   [warn] dal_pose hand err {[round(e,2) for e in hands.errs]} lean={lean}")
        out = base + hands
        return out + extra if extra else out

    a = ctx.new("dalgona_sit", 3.0, LOOP)
    for i in range(0, 13):
        t = 3.0 * i / 12
        w = S(TAU * i / 12)
        w3 = S(TAU * 3 * i / 12 + 0.7) - S(0.7)
        w5 = S(TAU * 4 * i / 12)
        a.key(t, dal_pose(lean=18 + 0.7 * w, nod=24 + 1.4 * w + 0.6 * w3, tilt=1.2 * w,
                          needle=(NEEDLE[0] + 0.35 * w3, NEEDLE[1] + 0.15 * w5, NEEDLE[2] + 0.35 * w5)), "linear")
    a.close_loop()
    ctx.refs.update({k: "dalgona_sit" for k in ("dalgona_lick", "dalgona_crack", "dalgona_success", "dalgona_fail")})

    a = ctx.new("dalgona_carve", 2.0, LOOP)
    for i in range(0, 17):
        t = 2.0 * i / 16
        ang = TAU * i / 16
        a.key(t, dal_pose(lean=21 + 0.4 * S(ang), nod=32 + 0.7 * S(ang * 2), tilt=5 + 1.0 * S(ang),
                          tin=(TIN[0] + 0.15 * S(ang), TIN[1], TIN[2]),
                          needle=(NEEDLE[0] + 0.7 * C(ang), NEEDLE[1] + 0.2 * S(ang * 2), NEEDLE[2] + 0.7 * S(ang))), "linear")
    a.close_loop()

    # lick: lift the tin to the mouth
    a = ctx.new("dalgona_lick", 1.8, ONCE)
    a.key(0.0, dal_pose(), "linear")
    for t, k, ease in ((0.42, 0.0, "easeInOutSine"), (0.72, 1.0, "easeInOutSine"), (0.96, -1.0, "easeInOutSine"),
                       (1.18, 1.0, "easeInOutSine"), (1.36, 0.0, "easeInOutSine")):
        base = seated(12, 14 + 4 * k, tilt=-4 + 1.5 * k) + HEAD(nod=14 + 4 * k, tilt=-4 + 1.5 * k)
        base = base + Pose(body=B(rot=(12, 0, 0)))
        lift = H.hand(Pose(base), "left", (1.4, 24.6 + 0.3 * k, -7.6 - 0.5 * k), frame="head", pref=PREF_FACE, warm=False)[0]
        right = H.hand(Pose(base), "right", NEEDLE, frame="root", pref=(40, 14, 0, 55), warm=False)[0]
        a.key(t, base + lift + right, ease)
    a.key(1.8, dal_pose(), "easeInOutSine")

    # crack: flinch
    a = ctx.new("dalgona_crack", 0.7, ONCE)
    a.key(0.0, dal_pose(), "linear")
    a.key(0.07, dal_pose(lean=8, nod=10, back=1.2, tin=(TIN[0] + 0.6, TIN[1] + 3.0, TIN[2] + 1.5),
                         needle=(NEEDLE[0] - 1.0, NEEDLE[1] + 3.6, NEEDLE[2] + 1.5)), "easeOutCubic")
    a.key(0.2, dal_pose(lean=10, nod=14, back=0.8, tin=(TIN[0] + 0.4, TIN[1] + 1.8, TIN[2] + 1.0),
                        needle=(NEEDLE[0] - 0.6, NEEDLE[1] + 2.0, NEEDLE[2] + 1.0)), "easeInOutSine")
    a.key(0.42, dal_pose(lean=14, nod=20, tin=(TIN[0], TIN[1] + 0.6, TIN[2]), needle=(NEEDLE[0], NEEDLE[1] + 0.6, NEEDLE[2])), "easeInOutSine")
    a.key(0.7, dal_pose(), "easeInOutSine")

    # success: relief
    a = ctx.new("dalgona_success", 2.0, ONCE)
    a.key(0.0, dal_pose(), "linear")
    a.key(0.55, dal_pose(lean=8, nod=10, tin=(1.6, 16.5, -7.0), needle=(NEEDLE[0], NEEDLE[1] + 0.8, NEEDLE[2] + 0.8)), "easeInOutSine")  # lifts the cookie
    a.key(1.1, dal_pose(lean=-2, nod=-6, tin=(1.8, 15.0, -6.0), needle=(NEEDLE[0] - 0.3, NEEDLE[1] - 0.6, NEEDLE[2] + 1.8)), "easeInOutSine")  # sigh
    a.key(1.55, dal_pose(lean=6, nod=6, tin=(1.9, 13.6, -7.0), needle=(NEEDLE[0] - 0.2, NEEDLE[1] - 0.8, NEEDLE[2] + 1.0)), "easeInOutSine")
    a.key(2.0, dal_pose(lean=8, nod=8, tin=(2.0, 12.6, -7.4), needle=(NEEDLE[0], NEEDLE[1] - 1.0, NEEDLE[2] + 0.2)), "easeOutSine")

    # fail: slump
    a = ctx.new("dalgona_fail", 2.0, HOLD)
    a.key(0.0, dal_pose(), "linear")
    a.key(0.45, dal_pose(lean=20, nod=34, tin=(TIN[0], TIN[1] + 0.2, TIN[2] + 0.3)), "easeInOutSine")      # stares
    a.key(1.05, dal_pose(lean=30, nod=42, tin=(TIN[0] - 0.3, TIN[1] - 0.6, TIN[2] + 0.9),
                         needle=(NEEDLE[0] + 0.5, NEEDLE[1] - 2.0, NEEDLE[2] + 3.0)), "easeInOutSine")       # slumps
    a.key(1.55, dal_pose(lean=33, nod=44, tin=(TIN[0] - 0.4, TIN[1] - 0.9, TIN[2] + 1.2),
                         needle=(NEEDLE[0] + 0.7, NEEDLE[1] - 3.0, NEEDLE[2] + 4.0)), "easeOutSine")
    a.key(2.0, dal_pose(lean=33, nod=44, tin=(TIN[0] - 0.4, TIN[1] - 0.9, TIN[2] + 1.2),
                        needle=(NEEDLE[0] + 0.7, NEEDLE[1] - 3.0, NEEDLE[2] + 4.0)), "linear")

    # =====================================================================================
    # MARBLES
    # =====================================================================================
    def standing_base(lean=0.0, nod=0.0, turn=0.0, tilt=0.0, dy=0.0, front=0.0, back=0.0, twist=0.0, spread=0.6, pitch=(0, 0)):
        base = WAIST(twist=twist * 0.4) + BODY(lean=lean, twist=twist * 0.6) + HEAD(nod=nod, turn=turn - twist, tilt=tilt)
        return base + plant(ctx, base, dy=dy, left=(front, 0.0), right=(back, 0.0), x_spread=(spread, spread), pitch=pitch)

    a = ctx.new("marble_hold", 2.0, LOOP)
    for i in range(0, 9):
        t = 2.0 * i / 8
        w = S(TAU * i / 8)
        w2 = S(TAU * 3 * i / 8)
        base = standing_base(lean=5 + 0.6 * w, nod=9 + 0.8 * w, turn=-3, twist=-8, front=2.0, back=-1.5)
        hands = H.hands(Pose(base), left=(2.0, 19.4 + 0.15 * w2, -9.2), right=(-1.4, 20.2 + 0.2 * w2, -10.6 - 0.2 * w),
                        frame="body", prefs=dict(left=(50, 12, 0, 80), right=(55, 8, 0, 88)))
        a.key(t, base + hands, "linear")
    a.close_loop()

    a = ctx.new("marble_guess", 1.2, ONCE)
    a.key(0.0, Pose())
    base = HEAD(nod=7, tilt=7, turn=-5) + BODY(lean=2)
    ch = H.hand(Pose(base), "right", CHIN_R if False else (-1.0, 24.4, -5.4), frame="head", pref=PREF_FACE, warm=False)[0]
    a.key(0.22, base + ch + ARM("left", swing=2, out=7, elbow=10), "easeOutSine")
    for t, ang in ((0.40, 14), (0.54, -14), (0.68, 10)):
        base = HEAD(nod=4, turn=ang, tilt=-ang * 0.3) + BODY(lean=2)
        ch = H.hand(Pose(base), "right", (-1.0, 24.4, -5.4), frame="head", pref=PREF_FACE, warm=False)[0]
        a.key(t, base + ch + ARM("left", swing=2, out=7, elbow=10), "easeInOutSine")
    base = HEAD(nod=3, turn=2) + BODY(lean=7) + LEG("left", hip=8, knee=6)
    pt = H.hand(Pose(base), "right", (-4.2, 19.6, -11.6), frame="body", pref=(70, 10, 0, 12), warm=False)[0]
    a.key(0.92, base + pt + ARM("left", swing=2, out=7, elbow=10), "easeOutBack")
    a.key(1.2, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    a = ctx.new("marble_reveal", 1.0, ONCE)
    a.key(0.0, Pose())
    base = BODY(lean=6, twist=-6) + HEAD(nod=7, turn=3)
    fist = H.hand(Pose(base), "right", (-2.6, 20.4, -10.2), frame="body", pref=(55, 8, 0, 85), warm=False)[0]
    a.key(0.30, base + fist + ARM("left", swing=4, out=9, elbow=12), "easeOutSine")
    a.key(0.46, base + fist + ARM("left", swing=4, out=9, elbow=12), "linear")     # hold the fist
    opened = fist + Pose({"right_forearm": B(rot=(fist["right_forearm"].rot[0], 0, 72)), "right_hand_skin": B(rot=(-18, 0, 0))})
    a.key(0.78, base + opened + ARM("left", swing=4, out=9, elbow=12), "easeInOutSine")                  # palm opens up
    a.key(1.0, base + opened + ARM("left", swing=4, out=9, elbow=12), "linear")
    # blend back is handled by the controller; end on the open-hand pose
    finish(a, one_shot=True)

    a = ctx.new("marble_throw_windup", 0.5, HOLD)
    a.key(0.0, Pose())

    def windup_pose(k=1.0):
        base = standing_base(lean=26 * k, nod=-14 * k, dy=-4.0 * k, front=5.5 * k, back=-3.0 * k, twist=-6 * k, spread=1.0)
        arms = ARM("right", swing=-52 * k + 2 * (1 - k), out=9, elbow=26 * k + 9 * (1 - k)) + ARM("left", swing=52 * k + 2 * (1 - k), out=14, elbow=14)
        return base + arms
    a.key(0.22, windup_pose(), "easeOutCubic")
    a.key(0.5, windup_pose(), "easeInOutSine")
    finish(a, one_shot=True)
    ctx.refs["marble_throw_release"] = "marble_throw_windup@end"

    a = ctx.new("marble_throw_release", 0.5, ONCE)
    a.key(0.0, windup_pose(), "linear")
    base = standing_base(lean=18, nod=-8, dy=-3.2, front=6.0, back=-3.0, twist=0.0, spread=1.0)
    a.key(0.12, base + ARM("right", swing=4, out=8, elbow=16) + ARM("left", swing=40, out=22, elbow=14), "easeInQuad")
    base = standing_base(lean=10, nod=-4, dy=-1.8, front=6.0, back=-3.0, twist=5, spread=1.0)
    a.key(0.24, base + ARM("right", swing=64, out=8, elbow=8) + ARM("left", swing=20, out=30, elbow=14), "easeOutCubic")
    base = standing_base(lean=5, nod=0, dy=-0.8, front=5.0, back=-2.0, twist=6)
    a.key(0.38, base + ARM("right", swing=88, out=10, elbow=14) + ARM("left", swing=10, out=26, elbow=12), "easeOutSine")
    a.key(0.5, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    # =====================================================================================
    # GLASS BRIDGE
    # =====================================================================================
    a = ctx.new("bridge_step", 0.9, ONCE)
    a.key(0.0, Pose())
    arms = ARM("left", swing=8, out=44, elbow=12) + ARM("right", swing=8, out=44, elbow=12)
    a.key(0.14, WAIST(pos=(0.6, -0.3, 0)) + BODY(lean=4, tilt=-2) + HEAD(nod=14) + LEG("left", hip=0, knee=10) + LEG("right", hip=22, knee=34, ankle=-14) + arms, "easeOutSine")
    a.key(0.32, WAIST(pos=(0.8, -0.5, 0)) + BODY(lean=6, tilt=-3) + HEAD(nod=18) + LEG("left", hip=-4, knee=14) + LEG("right", hip=40, knee=14, ankle=-38) + arms, "easeInOutSine")   # toe tap
    a.key(0.44, WAIST(pos=(0.8, -0.4, 0)) + BODY(lean=5, tilt=-3) + HEAD(nod=17) + LEG("left", hip=-4, knee=14) + LEG("right", hip=36, knee=26, ankle=-30) + arms, "easeOutSine")  # lift
    a.key(0.58, WAIST(pos=(0.8, -0.5, 0)) + BODY(lean=6, tilt=-3) + HEAD(nod=18) + LEG("left", hip=-4, knee=14) + LEG("right", hip=40, knee=14, ankle=-38) + arms, "easeInQuad")   # 2nd tap
    a.key(0.72, WAIST(pos=(0.3, -0.2, 0)) + BODY(lean=4, tilt=-1) + HEAD(nod=12) + LEG("left", hip=4, knee=12) + LEG("right", hip=30, knee=10, ankle=-14) + arms.scaled(0.8), "easeInOutSine")
    a.key(0.9, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    a = ctx.new("bridge_hesitate", 2.5, LOOP)
    for i in range(0, 11):
        t = 2.5 * i / 10
        w = S(TAU * i / 10)
        w2 = S(TAU * 4 * i / 10 + 0.8)
        base = (BODY(lean=36 + 1.0 * w, tilt=3 * w2 * 0.3) + HEAD(nod=30 + 2.0 * w, turn=2.0 * w, tilt=2.0 * w) + WAIST(pos=(0.4 * w, 0, 2.0)))
        base = base + plant(ctx, base, dy=-4.8 + 0.1 * w2, left=(4.5, 0.0), right=(3.5, 0.0), x_spread=(1.4, 1.4))
        hand_r = H.hand(Pose(base), "right", (-3.0, 7.4 + 0.3 * w, -10.0), frame="root", pref=(45, 12, 0, 40), warm=False)[0]
        arm_l = ARM("left", swing=18 + 3 * w, out=62 + 2 * w2, elbow=14)
        a.key(t, base + hand_r + arm_l, "linear")
    a.close_loop()

    a = ctx.new("bridge_balance", 1.2, LOOP)
    for i in range(0, 13):
        t = 1.2 * i / 12
        w = S(TAU * i / 12)
        c = C(TAU * i / 12)
        lift_l = max(0.0, c) * 0.9
        lift_r = max(0.0, -c) * 0.9
        base = (BODY(lean=3.0, tilt=3.2 * w) + HEAD(nod=14, tilt=-2.4 * w) + WAIST(pos=(0.9 * w, -0.6, 0), tilt=-1.4 * w))
        legs = plant(ctx, base, dy=-0.9, left=(0.8 + 0.8 * w, lift_l), right=(0.8 - 0.8 * w, lift_r), x_spread=(0.6, 0.6),
                     pitch=(0.0, 0.0))
        arms = (ARM("left", swing=6 - 3 * w, out=76 + 6 * w, elbow=8 + 4 * c) + ARM("right", swing=6 + 3 * w, out=76 - 6 * w, elbow=8 - 4 * c))
        a.key(t, base + legs + arms, "linear")
    a.close_loop()
