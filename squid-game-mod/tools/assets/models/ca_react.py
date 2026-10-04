"""Reaction / action family: stumble, lose_balance, shocked, cower, sob, relieved, celebrate*, wave, point,
nod, shake_head, think, inspect, interact, eliminated_*, knocked_down."""
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

OPTIONAL = ["hair_buzz", "hair_short", "hair_parted", "hair_curly", "hair_long", "hair_ponytail", "hair_bun",
            "face_0", "face_1", "face_2", "face_3", "face_4", "face_5", "glasses"]

# hand targets in the HEAD frame (file coords of the head bind pose)
MOUTH = {"left": (1.8, 25.0, -5.8), "right": (-1.8, 25.0, -5.8)}
EYES = {"left": (2.4, 28.2, -5.6), "right": (-2.4, 28.2, -5.6)}
TOPHEAD = {"left": (3.2, 33.4, 0.5), "right": (-3.2, 33.4, 0.5)}
CHIN_R = (-1.0, 24.4, -5.4)
BROW_R = (-1.6, 30.6, -5.3)
SCRATCH_R = (-5.5, 30.0, 0.2)


def build(ctx: Ctx) -> None:
    H = ctx.H

    # ============================== stumble ========================================
    a = ctx.new("stumble", 0.9, ONCE)
    a.key(0.0, Pose())
    # 0.08 toe catches: right leg up & forward, torso pitching, arms start to fling
    p = (BODY(lean=9) + HEAD(nod=3) + LEG("right", hip=40, knee=26, ankle=-8) + LEG("left", hip=-6, knee=8)
         + ARM("left", swing=22, out=14, elbow=14) + ARM("right", swing=30, out=14, elbow=14))
    a.key(0.09, p, "easeOutQuad")
    # 0.22 lunge: right foot lands far forward, body very forward, left arm windmills up
    base = BODY(lean=27, twist=-6) + HEAD(nod=-12, turn=3) + WAIST(twist=4)
    legs = plant(ctx, base, dy=-1.8, left=(-6.0, 0.0), right=(9.0, 0.0), pitch=(-26.0, 12.0))
    a.key(0.22, base + legs + ARM("left", swing=96, out=48, elbow=16) + ARM("right", swing=-38, out=52, elbow=14),
          "easeOutCubic")
    # 0.40 arms windmill the other way, left foot catches up
    base = BODY(lean=20, twist=6) + HEAD(nod=-8, turn=-3) + WAIST(twist=-4)
    legs = plant(ctx, base, dy=-1.2, left=(5.0, 0.0), right=(-3.0, 0.0), pitch=(10.0, -18.0))
    a.key(0.40, base + legs + ARM("left", swing=-42, out=58, elbow=12) + ARM("right", swing=100, out=44, elbow=14),
          "easeInOutSine")
    # 0.58 wide arms for balance, staggered stance
    base = BODY(lean=10) + HEAD(nod=0)
    legs = plant(ctx, base, dy=-1.6, left=(4.0, 0.0), right=(-2.0, 0.0), pitch=(0.0, 0.0), x_spread=(1.0, 1.0))
    a.key(0.58, base + legs + ARM("left", swing=18, out=72, elbow=12) + ARM("right", swing=22, out=70, elbow=12),
          "easeInOutSine")
    # 0.75 settle
    a.key(0.75, BODY(lean=3.5) + HEAD(nod=1) + ARM("left", swing=6, out=24, elbow=12) + ARM("right", swing=6, out=24, elbow=12),
          "easeOutSine")
    a.key(0.9, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    # ============================== lose_balance ===================================
    a = ctx.new("lose_balance", 1.2, ONCE)
    a.key(0.0, Pose())
    base = BODY(lean=6, tilt=-5) + HEAD(nod=5, tilt=3) + WAIST(tilt=-3)
    a.key(0.16, base + LEG("right", hip=26, knee=10, out=16) + LEG("left", hip=-4, knee=22)
          + ARM("left", swing=12, out=58, elbow=10) + ARM("right", swing=10, out=62, elbow=10), "easeOutQuad")
    base = BODY(lean=14, tilt=8, twist=8) + HEAD(nod=10, tilt=-4) + WAIST(tilt=4)
    a.key(0.40, base + LEG("right", hip=34, knee=14, out=22) + LEG("left", hip=-10, knee=34)
          + ARM("left", swing=-35, out=40, elbow=14) + ARM("right", swing=125, out=82, elbow=12), "easeInOutSine")
    base = BODY(lean=21, tilt=-9, twist=-8) + HEAD(nod=14, tilt=4) + WAIST(tilt=-4)
    a.key(0.64, base + LEG("right", hip=40, knee=18, out=24) + LEG("left", hip=-14, knee=44)
          + ARM("left", swing=128, out=82, elbow=12) + ARM("right", swing=-32, out=40, elbow=14), "easeInOutSine")
    base = BODY(lean=27, tilt=3) + HEAD(nod=18)
    a.key(0.88, base + LEG("right", hip=48, knee=12, out=20) + LEG("left", hip=-16, knee=50)
          + ARM("left", swing=76, out=56, elbow=22) + ARM("right", swing=80, out=54, elbow=22), "easeInOutSine")
    a.key(1.05, BODY(lean=10, tilt=1) + HEAD(nod=6) + LEG("right", hip=14, knee=14) + LEG("left", hip=-4, knee=14)
          + ARM("left", swing=22, out=40, elbow=14) + ARM("right", swing=22, out=40, elbow=14), "easeOutSine")
    a.key(1.2, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    # ============================== shocked ========================================
    a = ctx.new("shocked", 1.0, ONCE)
    a.key(0.0, Pose())
    rec = (BODY(lean=-9) + HEAD(nod=-9) + WAIST(pos=(0, 0, 0.8)) + LEG("left", hip=-8, knee=6) + LEG("right", hip=-6, knee=4)
           + ARM("left", swing=46, out=42, elbow=34) + ARM("right", swing=48, out=42, elbow=34))
    a.key(0.07, rec, "easeOutCubic")
    for t, k in ((0.32, 0.0), (0.46, 1.0), (0.60, -1.0), (0.74, 0.7), (0.82, 0.0)):
        base = BODY(lean=-5 + 0.6 * k) + HEAD(nod=-3 + 0.8 * k, turn=0.7 * k) + WAIST(pos=(0, 0, 0.5)) + LEG("left", hip=-5, knee=5) + LEG("right", hip=-4, knee=4)
        pose = base + H.hands(Pose(base), left=MOUTH["left"], right=MOUTH["right"], frame="head", prefs=dict(left=PREF_FACE, right=PREF_FACE))
        a.key(t, pose, "easeOutBack" if t == 0.32 else "easeInOutSine")
    a.key(1.0, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    # ============================== cower ==========================================
    a = ctx.new("cower", 1.4, LOOP)
    for i in range(0, 13):
        t = 1.4 * i / 12
        w = S(TAU * 3 * i / 12)
        base = BODY(lean=38 + 0.9 * w) + HEAD(nod=20 + 1.0 * w) + WAIST(pos=(0, 0, 1.5))
        base = base + plant(ctx, base, dy=-6.6 + 0.12 * w, left=(3.2, 0.0), right=(3.2, 0.0), x_spread=(1.0, 1.0))
        base = base + ARMS(swing=60, out=30, elbow=90)      # placeholder for IK frame
        hands = H.hands(Pose(base), left=TOPHEAD["left"], right=TOPHEAD["right"], frame="head",
                        prefs=dict(left=(100, 55, 0, 125), right=(100, 55, 0, 125)))
        if i == 0:
            print("   cower hand err", [round(e, 2) for e in hands.errs])
        a.key(t, base + hands, "linear")
    a.close_loop()

    # ============================== sob ============================================
    a = ctx.new("sob", 1.2, LOOP)
    for i in range(0, 25):
        t = 1.2 * i / 24
        w = S(TAU * 4 * i / 24)
        w2 = S(TAU * 4 * i / 24 + 1.0)
        base = BODY(lean=13 + 1.3 * w, pos=(0, 0.18 * w2, 0)) + HEAD(nod=22 + 1.8 * w2, turn=1.2 * w, tilt=2 * w) + WAIST(pos=(0, 0, 0.5))
        base = base + LEG("left", hip=3, knee=5) + LEG("right", hip=3, knee=5)
        hands = H.hands(Pose(base), left=EYES["left"], right=EYES["right"], frame="head",
                        prefs=dict(left=PREF_FACE, right=PREF_FACE))
        a.key(t, base + hands, "linear")
    a.close_loop()

    # ============================== relieved =======================================
    a = ctx.new("relieved", 1.8, ONCE)
    a.key(0.0, Pose())
    a.key(0.28, BODY(lean=-5) + HEAD(nod=-14) + ARMS(swing=8, out=22, elbow=10), "easeOutSine")          # breath in
    base = BODY(lean=44) + HEAD(nod=8) + WAIST(pos=(0, 0, 2.0))
    base = base + plant(ctx, base, dy=-2.0, left=(3.0, 0.0), right=(3.0, 0.0), x_spread=(1.0, 1.0))

    def knees(b):
        l, _ = H.hand_w(b, "left", H.on_thigh(b, "left", 0.35, 2.0, 0.4), pref=(40, 14, 0, 20), warm=False)
        r, _ = H.hand_w(b, "right", H.on_thigh(b, "right", 0.35, 2.0, 0.4), pref=(40, 14, 0, 20), warm=False)
        return l + r
    hands = knees(base)
    a.key(0.72, base + hands, "easeInOutSine")                                                         # exhale, hands on knees
    b2 = base + BODY(lean=47) + HEAD(nod=11)
    a.key(0.98, b2 + knees(b2), "easeInOutSine")
    base = BODY(lean=10, twist=-3) + HEAD(nod=-2, turn=-3, tilt=-3) + WAIST(pos=(0, 0, 0))
    a.key(1.22, base + H.hands(Pose(base), right=BROW_R, frame="head", prefs=dict(right=PREF_FACE)).only(["right_arm", "right_forearm"])
          + Pose({k: v for k, v in IDLE0.items() if k.startswith("left")}), "easeInOutSine")           # hand to the brow
    base = BODY(lean=8, twist=3) + HEAD(nod=-1, turn=4, tilt=2)
    a.key(1.42, base + H.hands(Pose(base), right=(1.8, 30.6, -5.3), frame="head", prefs=dict(right=PREF_FACE)).only(["right_arm", "right_forearm"])
          + Pose({k: v for k, v in IDLE0.items() if k.startswith("left")}), "easeInOutSine")           # wipe across
    a.key(1.8, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    # ============================== celebrate ======================================
    a = ctx.new("celebrate", 1.0, LOOP)
    _b = BODY(lean=14) + HEAD(nod=-6)
    down = (_b + plant(ctx, _b, dy=-3.0, left=(3.0, 0.0), right=(3.0, 0.0), x_spread=(0.8, 0.8))
            + ARM("left", swing=-26, out=22, elbow=18) + ARM("right", swing=-26, out=22, elbow=18))
    push = (ROOT(pos=(0, 1.4, 0)) + WAIST(pos=(0, 0.4, 0)) + BODY(lean=2) + HEAD(nod=-14) + LEG("left", hip=-6, knee=4, ankle=-30)
            + LEG("right", hip=-4, knee=6, ankle=-30)
            + ARM("left", swing=20, out=118, elbow=14) + ARM("right", swing=20, out=118, elbow=14))
    air = (ROOT(pos=(0, 3.4, 0)) + BODY(lean=-4) + HEAD(nod=-16) + LEG("left", hip=22, knee=48, ankle=-18)
           + LEG("right", hip=8, knee=60, ankle=-20) + ARM("left", swing=12, out=148, elbow=6) + ARM("right", swing=12, out=148, elbow=6))
    _b = BODY(lean=8) + HEAD(nod=-8)
    land = (_b + plant(ctx, _b, dy=-1.6, left=(1.8, 0.0), right=(1.8, 0.0), x_spread=(0.6, 0.6))
            + ARM("left", swing=16, out=132, elbow=10) + ARM("right", swing=16, out=132, elbow=10))
    a.key(0.0, down, "linear")
    a.key(0.22, push, "easeOutCubic")
    a.key(0.46, air, "easeOutSine")
    a.key(0.70, land, "easeInQuad")
    a.key(1.0, down, "easeInOutSine")

    # ============================== celebrate_fist =================================
    a = ctx.new("celebrate_fist", 1.0, LOOP)
    for t, up_r, kn in ((0.0, 1.0, 0.0), (0.25, 0.0, 1.0), (0.5, 0.0, 0.0), (0.75, 1.0, 1.0), (1.0, 1.0, 0.0)):
        pass
    def fist_pose(r_up: float, l_up: float, bob: float, lean: float):
        base = BODY(lean=lean, twist=6 * (r_up - l_up)) + HEAD(nod=-8, turn=-4 * (r_up - l_up)) + WAIST(twist=-3 * (r_up - l_up))
        base = base + plant(ctx, base, dy=-1.8 * bob, left=(1.2 * bob, 0.0), right=(1.2 * bob, 0.2 * r_up), x_spread=(1.0, 1.0))
        res = Pose(base)
        for side, up in (("right", r_up), ("left", l_up)):
            tgt_up = (-6.8 if side == "right" else 6.8, 32.2, -1.5)
            tgt_dn = (-6.0 if side == "right" else 6.0, 23.5, -5.0)
            tgt = tuple(tgt_dn[k] + (tgt_up[k] - tgt_dn[k]) * up for k in range(3))
            hand, err = H.hand(base, side, tgt, frame="body", pref=(95, 22, 0, 60) if up > 0.5 else (60, 15, 0, 110))
            res = res + hand
        return res
    a.key(0.0, fist_pose(1.0, 0.0, 0.0, -3), "easeOutBack")
    a.key(0.25, fist_pose(0.0, 0.0, 1.0, 4), "easeInOutSine")
    a.key(0.5, fist_pose(0.0, 1.0, 0.0, -3), "easeOutBack")
    a.key(0.75, fist_pose(0.0, 0.0, 1.0, 4), "easeInOutSine")
    a.key(1.0, fist_pose(1.0, 0.0, 0.0, -3), "easeInOutSine")

    # ============================== wave (overlay) =================================
    a = ctx.new("wave", 1.4, ONCE)
    a.key(0.0, Pose({k: v for k, v in IDLE0.items() if k.startswith("right")}) + HEAD())
    waves = [(0.22, -8.2), (0.40, -11.2), (0.55, -6.6), (0.70, -11.2), (0.85, -6.6), (1.0, -11.0), (1.12, -8.2)]
    for t, x in waves:
        base = HEAD(nod=4, tilt=-5 if t > 0.3 else -2, turn=-6)
        hand, err = H.hand(Pose(base), "right", (x, 30.0, -1.4), frame="body", pref=(20, 85, 0, 105), warm=False)
        a.key(t, base + hand + Pose(right_hand_skin=B(rot=(0, 0, 14 * (x + 8.2) / 3.0))), "easeInOutSine" if t > 0.22 else "easeOutBack")
    a.key(1.4, HEAD() + Pose({k: v for k, v in IDLE0.items() if k.startswith("right")}) + Pose(right_hand_skin=B(rot=(0, 0, 0))), "easeInOutSine")
    a.start_from(Pose({k: v for k, v in IDLE0.items() if k.startswith("right")}))

    # ============================== point (overlay, hold) ==========================
    a = ctx.new("point", 1.0, HOLD)
    a.key(0.0, Pose({k: v for k, v in IDLE0.items() if k.startswith("right")}))
    cock = ARM("right", swing=26, out=14, elbow=78)
    a.key(0.12, cock, "easeOutSine")
    hand, err = H.hand(Pose(), "right", (-5.6, 24.4, -11.0), frame="body", pref=(80, 10, 0, 6), warm=False)
    a.key(0.36, hand, "easeOutBack")
    a.key(1.0, hand, "linear")

    # ============================== nod / shake_head (overlay) =====================
    a = ctx.new("nod", 0.6, ONCE)
    a.key(0.0, HEAD())
    a.key(0.16, HEAD(nod=18, tilt=0), "easeOutSine")
    a.key(0.34, HEAD(nod=-3), "easeInOutSine")
    a.key(0.46, HEAD(nod=7), "easeInOutSine")
    a.key(0.6, HEAD(), "easeInOutSine")
    a = ctx.new("shake_head", 0.8, ONCE)
    a.key(0.0, HEAD())
    for t, ang in ((0.1, 17), (0.25, -17), (0.4, 14), (0.55, -11), (0.68, 5)):
        a.key(t, HEAD(turn=ang, nod=2, tilt=-ang * 0.18), "easeInOutSine")
    a.key(0.8, HEAD(), "easeInOutSine")

    # ============================== think (overlay) ================================
    a = ctx.new("think", 3.0, LOOP)
    def think_pose(hand_pt, head, pref):
        base = head
        hand, err = H.hand(Pose(base), "right", hand_pt, frame="head", pref=pref, warm=False)
        return base + hand
    chin = think_pose(CHIN_R, HEAD(nod=7, tilt=6, turn=-4), PREF_FACE)
    chin2 = think_pose((CHIN_R[0] + 0.5, CHIN_R[1] + 0.3, CHIN_R[2]), HEAD(nod=9, tilt=7, turn=-5), PREF_FACE)
    scr = [think_pose((SCRATCH_R[0], SCRATCH_R[1] + dy, SCRATCH_R[2] + dz), HEAD(nod=3, tilt=-8, turn=6),
                      (100, 70, 0, 118)) for dy, dz in ((0, 0), (1.2, 0.8), (-0.3, -0.6))]
    a.key(0.0, chin, "linear")
    a.key(0.7, chin2, "easeInOutSine")
    a.key(1.1, chin, "easeInOutSine")
    a.key(1.45, scr[0], "easeInOutSine")
    a.key(1.65, scr[1], "easeInOutSine")
    a.key(1.85, scr[2], "easeInOutSine")
    a.key(2.05, scr[1], "easeInOutSine")
    a.key(2.35, scr[0], "easeInOutSine")
    a.key(2.7, chin2, "easeInOutSine")
    a.key(3.0, chin, "easeInOutSine")

    # ============================== inspect ========================================
    a = ctx.new("inspect", 2.5, LOOP)
    for i, (t, tilt, nod, lean) in enumerate(((0.0, 0, 28, 24), (0.625, 9, 30, 25), (1.25, 0, 26, 24), (1.875, -9, 30, 25), (2.5, 0, 28, 24))):
        base = BODY(lean=lean, twist=tilt * 0.4) + HEAD(nod=nod, tilt=tilt, turn=tilt * 0.5) + WAIST(pos=(0, 0, 1.5))
        base = base + plant(ctx, base, dy=-2.2, left=(2.5, 0.0), right=(2.5, 0.0), x_spread=(1.2, 1.2))
        l, el = H.hand_w(base, "left", H.on_thigh(base, "left", 0.3, 2.0, 0.4), pref=(45, 16, 0, 25), warm=False)
        r, er = H.hand_w(base, "right", H.on_thigh(base, "right", 0.3, 2.0, 0.4), pref=(45, 16, 0, 25), warm=False)
        a.key(t, base + l + r, "easeInOutSine")
    a.close_loop()

    # ============================== interact =======================================
    a = ctx.new("interact", 0.9, ONCE)
    a.key(0.0, Pose())
    base = BODY(lean=5) + HEAD(nod=3) + LEG("left", hip=6, knee=4) + LEG("right", hip=-3, knee=4)
    a.key(0.16, base + ARM("right", swing=40, out=18, elbow=70) + ARM("left", swing=2, out=7, elbow=10), "easeInOutSine")
    base = BODY(lean=10) + HEAD(nod=6) + LEG("left", hip=16, knee=8) + LEG("right", hip=-6, knee=10)
    hand, err = H.hand(Pose(base), "right", (-3.4, 22.6, -10.2), frame="body", pref=(70, 10, 0, 20), warm=False)
    a.key(0.34, base + hand + ARM("left", swing=2, out=7, elbow=10), "easeOutBack")
    base = BODY(lean=12) + HEAD(nod=8) + LEG("left", hip=16, knee=8) + LEG("right", hip=-6, knee=10)
    hand, err = H.hand(Pose(base), "right", (-3.4, 22.6, -12.2), frame="body", pref=(70, 10, 0, 12), warm=False)
    a.key(0.46, base + hand + ARM("left", swing=2, out=7, elbow=10), "easeInQuad")
    a.key(0.56, base + hand + ARM("left", swing=2, out=7, elbow=10), "linear")
    a.key(0.9, Pose(), "easeInOutSine")
    finish(a, one_shot=True)

    # ============================== elimination ====================================
    def lying(pose: Pose) -> Pose:
        return H.ground_snap(pose, hidden=OPTIONAL, clearance=0.0)

    # --- forward
    a = ctx.new("eliminated_forward", 1.6, HOLD)
    a.key(0.0, Pose())
    a.key(0.12, BODY(lean=-8) + HEAD(nod=-12) + WAIST(pos=(0, 0, 0.6)) + LEG("left", hip=-5, knee=8) + LEG("right", hip=-4, knee=6)
          + ARM("left", swing=36, out=44, elbow=26) + ARM("right", swing=40, out=44, elbow=26), "easeOutCubic")
    base = BODY(lean=10) + HEAD(nod=12)
    a.key(0.38, base + plant(ctx, base, dy=-3.6, left=(3.5, 0.0), right=(3.5, 0.0), x_spread=(0.8, 0.8))
          + ARM("left", swing=6, out=26, elbow=24) + ARM("right", swing=6, out=26, elbow=24), "easeInOutSine")
    # kneeling
    kneel = (WAIST(pos=(0, -4.2, 1.0)) + BODY(lean=8) + HEAD(nod=26) + LEG("left", hip=4, knee=100, ankle=-10)
             + LEG("right", hip=2, knee=102, ankle=-10) + ARM("left", swing=-6, out=18, elbow=20) + ARM("right", swing=-8, out=20, elbow=22))
    a.key(0.66, H.ground_snap(kneel, hidden=OPTIONAL), "easeInQuad")
    # toppling: root pitches forward, arms reach out, legs trail
    topple = (ROOT(rot=(52, 0, 0)) + WAIST(pos=(0, -3.0, 0)) + BODY(lean=12) + HEAD(nod=-10, turn=40) + LEG("left", hip=2, knee=40, ankle=-30)
              + LEG("right", hip=-2, knee=46, ankle=-30) + ARM("left", swing=125, out=36, elbow=14) + ARM("right", swing=118, out=40, elbow=14))
    a.key(1.0, lying(topple), "easeInCubic")
    prone = (ROOT(rot=(90, 0, 0)) + BODY(lean=-4, twist=-6) + HEAD(nod=-20, turn=72, tilt=-6)
             + LEG("left", hip=-6, knee=14, ankle=-35, out=8) + LEG("right", hip=-14, knee=38, ankle=-35, out=10)
             + ARM("left", swing=158, out=48, elbow=18) + ARM("right", swing=150, out=64, elbow=24))
    a.key(1.18, lying(prone), "easeOutQuad")
    a.key(1.6, lying(prone.merged(ARM("left", swing=156, out=52, elbow=22)).merged(ARM("right", swing=148, out=66, elbow=28))), "easeOutSine")
    a.start_from(IDLE0)

    # --- backward
    a = ctx.new("eliminated_backward", 1.6, HOLD)
    a.key(0.0, Pose())
    a.key(0.10, BODY(lean=-14) + HEAD(nod=-16) + WAIST(pos=(0, 0, 1.2)) + LEG("left", hip=16, knee=10) + LEG("right", hip=8, knee=12)
          + ARM("left", swing=44, out=52, elbow=24) + ARM("right", swing=52, out=48, elbow=24), "easeOutCubic")
    stagger = (ROOT(rot=(-18, 0, 0)) + BODY(lean=-6) + HEAD(nod=-14) + LEG("left", hip=30, knee=18, ankle=10) + LEG("right", hip=18, knee=30)
               + ARM("left", swing=60, out=70, elbow=20) + ARM("right", swing=66, out=64, elbow=22))
    a.key(0.32, H.ground_snap(stagger, hidden=OPTIONAL), "easeInOutSine")
    fall = (ROOT(rot=(-56, 0, 0)) + BODY(lean=-6) + HEAD(nod=-16, tilt=6) + LEG("left", hip=18, knee=44, ankle=8) + LEG("right", hip=30, knee=56, ankle=8)
            + ARM("left", swing=70, out=74, elbow=24) + ARM("right", swing=64, out=70, elbow=26))
    a.key(0.72, H.ground_snap(fall, hidden=OPTIONAL), "easeInQuad")
    supine = (ROOT(rot=(-90, 0, 0)) + BODY(lean=2) + HEAD(nod=-8, turn=-16, tilt=7)
              + LEG("left", hip=10, knee=28, ankle=12, out=12) + LEG("right", hip=4, knee=16, ankle=12, out=16)
              + ARM("left", swing=-8, out=64, elbow=16) + ARM("right", swing=-14, out=58, elbow=22))
    a.key(1.1, H.ground_snap(supine, hidden=OPTIONAL), "easeOutQuad")
    a.key(1.6, H.ground_snap(supine.merged(ARM("left", swing=-10, out=68, elbow=14)).merged(ARM("right", swing=-16, out=62, elbow=20)), hidden=OPTIONAL), "easeOutSine")
    a.start_from(IDLE0)

    # --- knocked down: knees, then flat on the side
    a = ctx.new("knocked_down", 1.2, HOLD)
    a.key(0.0, Pose())
    a.key(0.09, BODY(lean=-16, tilt=6) + HEAD(nod=-10, tilt=8) + WAIST(pos=(0, 0, 1.5)) + LEG("left", hip=8, knee=24) + LEG("right", hip=-6, knee=10)
          + ARM("left", swing=38, out=58, elbow=24) + ARM("right", swing=24, out=40, elbow=30), "easeOutCubic")
    kneel = (WAIST(pos=(0, -4.2, 1.0)) + BODY(lean=-6, tilt=10) + HEAD(nod=8, tilt=10) + LEG("left", hip=4, knee=100, ankle=-10)
             + LEG("right", hip=2, knee=100, ankle=-10) + ARM("left", swing=22, out=70, elbow=18) + ARM("right", swing=30, out=54, elbow=24))
    a.key(0.38, H.ground_snap(kneel, hidden=OPTIONAL), "easeInOutSine")
    side = (ROOT(rot=(0, 0, 90)) + BODY(lean=10, tilt=-4) + HEAD(nod=4, tilt=4, turn=-10)
            + LEG("left", hip=18, knee=60, out=-4) + LEG("right", hip=34, knee=84, out=-6)
            + ARM("left", swing=40, out=-18, elbow=40) + ARM("right", swing=82, out=30, elbow=48))
    a.key(0.8, H.ground_snap(side, hidden=OPTIONAL), "easeInCubic")
    a.key(1.2, H.ground_snap(side, hidden=OPTIONAL), "easeOutSine")
    a.start_from(IDLE0)
