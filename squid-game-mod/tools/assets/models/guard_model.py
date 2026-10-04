"""Guard geometry (file space: front = -Z, file +X = entity's LEFT, y up, feet at origin).

root > waist > body > head > hood / mask_circle / mask_triangle / mask_square
                     body > collar_black, rifle, number-less
                     body > {left,right}_arm > {left,right}_forearm > {left,right}_hand > item_{left,right}
              waist > {left,right}_leg > {left,right}_shin > {left,right}_boot
(`*_forearm` and `*_shin` are additions for elbows/knees.)
"""
from __future__ import annotations

from common import *  # noqa: F401,F403
from lib.build import ModelBuilder

TEX = 128
MASK_BONES = ["mask_circle", "mask_triangle", "mask_square"]

# rifle: pivot at the right-hand pistol grip; local offsets below are relative to it
RIFLE_G = (-3.0, 13.5, -3.5)     # bind-pose grip position in body space (file coords)


def build_model() -> ModelBuilder:
    mb = ModelBuilder("geometry.squidgame.guard", TEX, TEX, bounds=(3.0, 3.0, (0.0, 1.0, 0.0)))
    mb.bone("root", None, (0, 0, 0))
    mb.bone("waist", "root", (0, 12, 0))
    mb.bone("body", "waist", (0, 12, 0))

    # ---- torso ---------------------------------------------------------------------------
    mb.box("body", "torso", (-4, 12, -2), (8, 12, 4))
    mb.box("body", "belt", (-4, 12.6, -2), (8, 2, 4), inflate=0.3)
    mb.box("body", "buckle", (-1, 12.8, -2.6), (2, 2, 1), inflate=-0.05)
    mb.box("body", "pouch_l", (1.6, 12.8, -2.9), (2, 2, 1), inflate=0.0)
    mb.box("body", "pouch_r", (-3.6, 12.8, -2.9), (2, 2, 1), inflate=0.0)

    mb.bone("collar_black", "body", (0, 24, 0))
    mb.box("collar_black", "collar", (-4, 21.8, -2), (8, 3, 4), inflate=0.5)

    # ---- head: hood + masks ---------------------------------------------------------------------
    mb.bone("head", "body", (0, 24, 0))
    mb.bone("hood", "head", (0, 24, 0))
    mb.box("hood", "hood_shell", (-4, 24, -4), (8, 8, 8), inflate=0.75)
    mb.box("hood", "hood_cowl", (-4, 21.6, -2), (8, 3, 4), inflate=0.85)
    mb.box("hood", "hood_tail", (-3, 22.5, 2.9), (6, 5, 2), inflate=0.3)
    mb.box("hood", "hood_peak", (-3, 32.2, -3), (6, 1, 6), inflate=0.45)
    for m in MASK_BONES:
        mb.bone(m, "head", (0, 24, 0))
        mb.box(m, m, (-3.5, 25.2, -5.9), (7, 7, 1), inflate=0.0)

    # ---- arms -------------------------------------------------------------------------------------
    for s, n in ((+1, "left"), (-1, "right")):
        X = (lambda x0, w, s=s: x0 if s > 0 else -(x0 + w))
        mb.bone(f"{n}_arm", "body", (6 * s, 22, 0))
        mb.box(f"{n}_arm", f"{n}_sleeve_upper", (X(4, 4), 17, -2), (4, 7, 4))
        mb.bone(f"{n}_forearm", f"{n}_arm", (6 * s, 17, 0))
        mb.box(f"{n}_forearm", f"{n}_sleeve_lower", (X(4, 4), 13, -2), (4, 6, 4), inflate=-0.1)
        mb.bone(f"{n}_hand", f"{n}_forearm", (6 * s, 13, 0))
        mb.box(f"{n}_hand", f"{n}_glove", (X(4, 4), 10, -2), (4, 4, 4), inflate=-0.3)
        mb.bone(f"item_{n}", f"{n}_hand", (6 * s, 11.7, 0))

    # ---- legs -------------------------------------------------------------------------------------
    for s, n in ((+1, "left"), (-1, "right")):
        X = (lambda x0, w, s=s: x0 if s > 0 else -(x0 + w))
        mb.bone(f"{n}_leg", "waist", (2 * s, 12, 0))
        mb.box(f"{n}_leg", f"{n}_thigh", (X(0, 4), 6, -2), (4, 7, 4), inflate=-0.05)
        mb.bone(f"{n}_shin", f"{n}_leg", (2 * s, 6, 0))
        mb.box(f"{n}_shin", f"{n}_shin_c", (X(0, 4), 2, -2), (4, 6, 4), inflate=-0.1)
        mb.bone(f"{n}_boot", f"{n}_shin", (2 * s, 2.5, 0))
        mb.box(f"{n}_boot", f"{n}_boot_c", (X(0.15, 4), 0.15, -4), (4, 4, 6), inflate=0.15)

    # ---- rifle (prop) -----------------------------------------------------------------------------
    gx, gy, gz = RIFLE_G
    mb.bone("rifle", "body", RIFLE_G)

    def rb(name, rel, size, **kw):
        mb.box("rifle", name, (gx + rel[0], gy + rel[1], gz + rel[2]), size, **kw)

    rb("r_grip", (-1.0, -2.6, 0.0), (2, 3, 2), inflate=-0.3)
    rb("r_receiver", (-1.0, 0.3, -3.0), (2, 3, 7), inflate=0.1)
    rb("r_stock", (-1.0, 0.2, 4.0), (2, 3, 4), inflate=-0.1)
    rb("r_buttpad", (-1.0, 0.0, 7.9), (2, 3, 1), inflate=0.0)
    rb("r_guard", (-1.0, 0.2, -8.6), (2, 3, 6), inflate=0.2)
    rb("r_barrel", (-0.5, 0.9, -14.0), (1, 1, 6), inflate=-0.15)
    rb("r_muzzle", (-0.5, 0.9, -14.6), (1, 1, 2), inflate=0.15)
    rb("r_rail", (-0.5, 3.2, -5.0), (1, 1, 9), inflate=-0.2)
    rb("r_fsight", (-0.5, 3.0, -11.6), (1, 2, 1), inflate=-0.2)
    rb("r_mag", (-1.0, -4.5, -2.6), (2, 5, 2), inflate=-0.2)

    mb.finalize()
    return mb


if __name__ == "__main__":
    mb = build_model()
    print(len(mb.geo.bones), "bones;", len(mb.parts), "parts; texture used", round(mb.packer.used_fraction(), 3))
