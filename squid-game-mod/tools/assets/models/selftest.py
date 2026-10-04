#!/usr/bin/env python3
"""Numerical self-test of the convention layer (semantic builders vs. the GeckoLib port).

Run:  python3 selftest.py        (needs the generated contestant geo; run gen_contestant.py first)
"""
from __future__ import annotations

import sys

from common import *  # noqa: F401,F403
import numpy as np

from lib.anim import B, Pose
from lib.humanoid import ARM, BODY, HEAD, LEG, ROOT, WAIST, Humanoid
from lib.posing import LimbCfg
from lib.rig import Rig


def contestant_cfg():
    return LimbCfg({"left": "item_left", "right": "item_right"},
                   {"left": (6, 11.7, 0), "right": (-6, 11.7, 0)})


def main() -> int:
    rig = Rig.load(str(GEO_DIR / "contestant.geo.json"))
    hu = Humanoid(rig, contestant_cfg())
    sk = hu.skel
    fails = 0

    def check(name, cond, info=""):
        nonlocal fails
        print(("PASS " if cond else "FAIL ") + name + (("  " + info) if info else ""))
        if not cond:
            fails += 1

    hand = lambda pose, side: sk.pt(pose, f"item_{side}", (6 if side == "left" else -6, 11.7, 0))
    base = {s: hand(Pose(), s) for s in ("left", "right")}

    for side in ("left", "right"):
        h = hand(ARM(side, swing=60), side)
        check(f"ARM({side}, swing=60) hand moves forward (-z) and up", h[2] < base[side][2] - 5 and h[1] > base[side][1] + 3, f"{h - base[side]}")
        h = hand(ARM(side, out=40), side)
        outward = (h[0] - base[side][0]) * (1 if side == "right" else -1)
        check(f"ARM({side}, out=40) hand moves away from the body", outward > 4, f"dx={h[0]-base[side][0]:.2f}")
        h = hand(ARM(side, elbow=90), side)
        check(f"ARM({side}, elbow=90) forearm folds forward", h[2] < base[side][2] - 3 and h[1] > base[side][1] + 3, f"{h - base[side]}")
        h = hand(ARM(side, swing=-40), side)
        check(f"ARM({side}, swing=-40) hand moves backward", h[2] > base[side][2] + 3)

    ank = lambda pose, side: sk.pt(pose, f"{side}_shoe", (2 if side == "left" else -2, 2.5, 0))
    toe = lambda pose, side: sk.pt(pose, f"{side}_shoe", (2 if side == "left" else -2, 1.5, -4))
    for side in ("left", "right"):
        a0 = ank(Pose(), side)
        a = ank(LEG(side, hip=40), side)
        check(f"LEG({side}, hip=40) ankle moves forward", a[2] < a0[2] - 3, f"{a - a0}")
        a = ank(LEG(side, hip=0, knee=90), side)
        check(f"LEG({side}, knee=90) ankle moves backward/up", a[2] > a0[2] + 3 and a[1] > a0[1] + 2, f"{a - a0}")
        t0 = toe(Pose(), side)
        t = toe(LEG(side, ankle=30), side)
        check(f"LEG({side}, ankle=30) toes tilt up", t[1] > t0[1] + 1.5, f"{t - t0}")
        a = ank(LEG(side, out=30), side)
        outward = (a[0] - a0[0]) * (1 if side == "right" else -1)
        check(f"LEG({side}, out=30) foot moves away from the midline", outward > 2, f"dx={a[0]-a0[0]:.2f}")

    nose = lambda pose: sk.pt(pose, "head", (0, 28, -4))
    n0 = nose(Pose())
    check("HEAD(nod=30) front moves down", nose(HEAD(nod=30))[1] < n0[1] - 1.5)
    check("HEAD(turn=40) front turns to the entity's RIGHT (+x baked)", nose(HEAD(turn=40))[0] > n0[0] + 2)
    top = lambda pose: sk.pt(pose, "head", (0, 32, 0))
    check("HEAD(tilt=20) top tilts toward the entity's LEFT (-x baked)", top(HEAD(tilt=20))[0] < top(Pose())[0] - 1.5)
    check("BODY(lean=20) head moves forward (-z)", sk.pt(BODY(lean=20), "head", (0, 28, 0))[2] < -3)
    check("BODY(twist=30) front turns right", sk.pt(BODY(twist=30), "head", (0, 28, -4))[0] > 1.5)
    check("ROOT(pos y+5) lifts", abs(sk.pt(ROOT(pos=(0, 5, 0)), "head", (0, 28, 0))[1] - 33) < 1e-6)
    check("ROOT(pos x+5 file) moves toward the entity's LEFT", sk.pt(ROOT(pos=(5, 0, 0)), "head", (0, 28, 0))[0] < -4.9)
    check("ROOT(pos z+5) moves BACKWARD", sk.pt(ROOT(pos=(0, 0, 5)), "head", (0, 28, 0))[2] > 4.9)
    # mirror consistency
    p = ARM("right", swing=50, out=20, elbow=70, twist=15, fout=10)
    m = p.mirrored()
    q = ARM("left", swing=50, out=20, elbow=70, twist=15, fout=10)
    ok = all(np.allclose(m[k].rot, q[k].rot) for k in q)
    check("Pose.mirrored(ARM right) == ARM(left)", ok)
    h1 = hand(p, "right"); h2 = hand(m, "left")
    check("mirrored pose gives mirrored hand position", np.allclose([-h1[0], h1[1], h1[2]], h2, atol=1e-6), f"{h1} {h2}")
    # waist lowers everything
    check("WAIST(pos y-6) lowers the head by 6", abs(sk.pt(WAIST(pos=(0, -6, 0)), "head", (0, 28, 0))[1] - 22) < 1e-6)
    # IK sanity
    pose = Pose()
    r, err = hu.hand(pose, "right", (-6, 24, -8), frame="body")
    check("IK arm reaches a forward-up target", err < 0.2, f"err={err:.3f}")
    r, err = hu.hand(pose, "left", (6, 14, -3), frame="body")
    check("IK arm reaches hip target", err < 0.2, f"err={err:.3f}")
    print("\nall conventions OK" if fails == 0 else f"\n{fails} FAILED")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
