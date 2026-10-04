"""Contestant geometry (file space: front = -Z, file +X = entity's LEFT, y up, feet at origin).

Skeleton (names are a hard contract with the Java side; the elbow/knee bones are additions):

root > waist > body > head > head_skin / hair_* / face_* / glasses
                     body > neck_skin, number_chest, number_back
                     body > {left,right}_arm > {left,right}_forearm > {left,right}_hand_skin > item_{left,right}
              waist > {left,right}_leg > {left,right}_shin > {left,right}_shoe
"""
from __future__ import annotations

import math

from common import *  # noqa: F401,F403  (sets sys.path)
from lib.build import ModelBuilder

TEX = 128

HAIR_BONES = ["hair_buzz", "hair_short", "hair_parted", "hair_curly", "hair_long", "hair_ponytail", "hair_bun"]
FACE_BONES = [f"face_{i}" for i in range(6)]
SKIN_BONES = ["head_skin", "neck_skin", "left_hand_skin", "right_hand_skin"]

HEAD_O = (-4, 24, -4)
FACE_Z = -4.06      # face decal plane
GLASSES_Z = -4.14


def build_model() -> ModelBuilder:
    mb = ModelBuilder("geometry.squidgame.contestant", TEX, TEX, bounds=(3.0, 3.0, (0.0, 1.0, 0.0)))

    mb.bone("root", None, (0, 0, 0))
    mb.bone("waist", "root", (0, 12, 0))
    mb.bone("body", "waist", (0, 12, 0))

    # ---- torso ---------------------------------------------------------------------------
    mb.box("body", "jacket", (-4, 12, -2), (8, 12, 4))
    mb.box("body", "hem", (-4, 12, -2), (8, 2, 4), inflate=0.3)
    mb.box("body", "collar", (-4, 22, -2), (8, 2, 4), inflate=0.35)

    # ---- head ----------------------------------------------------------------------------
    mb.bone("head", "body", (0, 24, 0))
    mb.bone("head_skin", "head", (0, 24, 0))
    mb.box("head_skin", "head", HEAD_O, (8, 8, 8))
    mb.box("head_skin", "ear_l", (4, 26, -1), (1, 2, 2))
    mb.box("head_skin", "ear_r", (-5, 26, -1), (1, 2, 2))
    mb.bone("neck_skin", "body", (0, 24, 0))
    mb.box("neck_skin", "neck", (-2, 22.5, -1.5), (4, 3, 3))

    # hair: one bone per style, greyscale, tinted by Java -----------------------------------
    for hb in HAIR_BONES:
        mb.bone(hb, "head", (0, 24, 0))
    mb.box("hair_buzz", "buzz", HEAD_O, (8, 8, 8), inflate=0.25)

    mb.box("hair_short", "short", HEAD_O, (8, 8, 8), inflate=0.55)
    mb.box("hair_short", "short_top", (-3.5, 32.4, -3.5), (7, 1, 7), inflate=0.1)

    mb.box("hair_parted", "parted", HEAD_O, (8, 8, 8), inflate=0.5)
    mb.box("hair_parted", "parted_quiff", (-0.5, 32.4, -4.0), (5, 1, 6), inflate=0.3)
    mb.box("hair_parted", "parted_puff", (1.0, 33.3, -4.0), (3, 1, 3), inflate=0.2)

    mb.box("hair_curly", "curly", HEAD_O, (8, 8, 8), inflate=1.0)
    mb.box("hair_curly", "curly_top", (-3, 32, -3), (6, 2, 6), inflate=0.8)
    mb.box("hair_curly", "curly_back", (-3, 25.5, 3.5), (6, 6, 2), inflate=0.8)
    mb.box("hair_curly", "curly_side_l", (4.5, 26, -3), (2, 5, 6), inflate=0.6)
    mb.box("hair_curly", "curly_side_r", (-6.5, 26, -3), (2, 5, 6), inflate=0.6)

    mb.box("hair_long", "long", HEAD_O, (8, 8, 8), inflate=0.5)
    mb.box("hair_long", "long_curtain", (-4.5, 16.5, 2.45), (9, 8, 2))
    mb.box("hair_long", "long_side_l", (4.2, 17.5, -0.5), (1, 7, 3))
    mb.box("hair_long", "long_side_r", (-5.2, 17.5, -0.5), (1, 7, 3))

    mb.box("hair_ponytail", "pony", HEAD_O, (8, 8, 8), inflate=0.4)
    # ponytail: tie + two angled segments; each segment hangs below its own pivot (the joint)
    # and is rotated about that joint (file +rx swings the lower end backward).
    tie = (0.0, 30.0, 4.6)
    mb.box("hair_ponytail", "pony_tie", (-2, 28.6, 3.2), (4, 2, 3), pivot=tie, rotation=(18, 0, 0))
    j0 = tie
    for k, (L, ang) in enumerate(((5, 38.0), (6, 12.0))):
        mb.box("hair_ponytail", f"pony_seg{k}", (-1.5, j0[1] - L, j0[2] - 1.5), (3, L, 3),
               pivot=j0, rotation=(ang, 0, 0), inflate=(0.0 if k == 0 else -0.1))
        r = math.radians(ang)
        j0 = (0.0, j0[1] - L * math.cos(r), j0[2] + L * math.sin(r))

    mb.box("hair_bun", "bun_cap", HEAD_O, (8, 8, 8), inflate=0.4)
    mb.box("hair_bun", "bun_knot", (-2.5, 32.6, -2.0), (5, 4, 5), inflate=0.15)
    mb.box("hair_bun", "bun_tie", (-2.5, 32.2, -2.0), (5, 1, 5), inflate=0.0)

    # face decals: dark ink on transparent, shown one at a time ----------------------------
    for i, fb in enumerate(FACE_BONES):
        mb.bone(fb, "head", (0, 24, 0))
        mb.decal(fb, f"face{i}", (-4, 24, FACE_Z), (8, 8), (8, 8))
    mb.bone("glasses", "head", (0, 24, 0))
    mb.decal("glasses", "glasses_front", (-4.3, 25.6, GLASSES_Z), (8.6, 3.0), (9, 3))
    mb.box("glasses", "glasses_temple_l", (4, 27, -4), (1, 1, 4), inflate=-0.25)
    mb.box("glasses", "glasses_temple_r", (-5, 27, -4), (1, 1, 4), inflate=-0.25)

    # ---- number anchors (empty) -----------------------------------------------------------
    mb.bone("number_chest", "body", (0, 18.5, -2.05))
    mb.bone("number_back", "body", (0, 18.5, 2.05))

    # ---- arms (shoulder - elbow - wrist) ------------------------------------------------------
    for s, n in ((+1, "left"), (-1, "right")):
        X = (lambda x0, w, s=s: x0 if s > 0 else -(x0 + w))
        mb.bone(f"{n}_arm", "body", (6 * s, 22, 0))
        mb.box(f"{n}_arm", f"{n}_sleeve_upper", (X(4, 4), 17, -2), (4, 7, 4))
        mb.bone(f"{n}_forearm", f"{n}_arm", (6 * s, 17, 0))
        mb.box(f"{n}_forearm", f"{n}_sleeve_lower", (X(4, 4), 13, -2), (4, 6, 4), inflate=-0.1)
        mb.bone(f"{n}_hand_skin", f"{n}_forearm", (6 * s, 13, 0))
        mb.box(f"{n}_hand_skin", f"{n}_hand", (X(4, 4), 10, -2), (4, 4, 4), inflate=-0.3)
        mb.bone(f"item_{n}", f"{n}_hand_skin", (6 * s, 11.7, 0))

    # ---- legs (hip - knee - ankle) ----------------------------------------------------------
    for s, n in ((+1, "left"), (-1, "right")):
        X = (lambda x0, w, s=s: x0 if s > 0 else -(x0 + w))
        mb.bone(f"{n}_leg", "waist", (2 * s, 12, 0))
        mb.box(f"{n}_leg", f"{n}_thigh", (X(0, 4), 6, -2), (4, 7, 4), inflate=-0.05)
        mb.bone(f"{n}_shin", f"{n}_leg", (2 * s, 6, 0))
        mb.box(f"{n}_shin", f"{n}_shin_c", (X(0, 4), 2, -2), (4, 6, 4), inflate=-0.1)
        mb.bone(f"{n}_shoe", f"{n}_shin", (2 * s, 2.5, 0))
        mb.box(f"{n}_shoe", f"{n}_shoe_c", (X(0.15, 4), 0.15, -4), (4, 3, 6), inflate=0.15)

    mb.finalize()
    return mb


if __name__ == "__main__":
    mb = build_model()
    print(len(mb.geo.bones), "bones;", len(mb.parts), "parts; texture used", round(mb.packer.used_fraction(), 3))
