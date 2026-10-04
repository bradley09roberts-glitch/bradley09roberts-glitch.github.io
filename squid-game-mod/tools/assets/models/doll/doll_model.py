"""Geometry of the giant Red Light / Green Light doll (bones, cubes, UV mapping).

Units: model pixels (1 px = 1/16 block), y up, origin at the centre of the feet, front = -Z.
JSON +X is the doll's LEFT side (GeckoLib mirrors x when baking), so "right" parts use
negative x.  The doll is ~9 blocks = 144 px tall (top of the bow at ~142 px).

Bone tree (contract names first):
  root > base (plinth, shoes, socks: static)
       > body (pivot at the feet: sways like an inverted pendulum)
            cubes: waist band, bodice, collar plate, neck core
            > dress        bell skirt, 7 octagonal tiers + petticoat lip
            > left_arm / right_arm   puff sleeve, sleeve, cuff, hand, thumb
            > neck_joint   rotating cog ring        > neck_ring  (flange, follows the head)
            > head         face cube, cheeks, nose
                 > hair  (> pigtail_l, pigtail_r, ribbon)
                 > eyes_off (> eye_off_l, eye_off_r)   dark dull eyes
                 > eyes_on  (> eye_on_l,  eye_on_r)    glowing eyes (glow mask)
                 > mouth
"""
from __future__ import annotations

import doll_paint as dp
from mbuilder import Atlas, ModelBuilder

# ---------------------------------------------------------------- key dimensions (px)
PLINTH_H = 3.0
SHOE_Y0, SHOE_Y1 = 3.9, 8.0
SKIRT_Y0 = 19.0
TIER_H = 6.0
# (full width W of the tier, corner chamfer c) from the hem upwards
TIERS = [(44, 4), (42, 4), (40, 4), (36, 4), (32, 3), (28, 3), (24, 2)]
SKIRT_H = int(TIER_H) * len(TIERS)          # 42
WAIST_Y0, WAIST_Y1 = SKIRT_Y0 + SKIRT_H, SKIRT_Y0 + SKIRT_H + 3    # 61 .. 64
BODICE_Y0, BODICE_Y1 = WAIST_Y1, 88.0       # 24 tall
SHOULDER_Y = 85.5
COLLAR_Y0, COLLAR_Y1 = 86.0, 90.0
CORE_Y0, CORE_Y1 = 90.0, 94.0
RING_Y0, RING_Y1 = 94.0, 97.0               # rotating cog ring
FLANGE_Y0, FLANGE_Y1 = 97.0, 99.9
HEAD_Y0, HEAD_H = 100.0, 32.0
HEAD_W, HEAD_D = 34.0, 32.0
HEAD_Y1 = HEAD_Y0 + HEAD_H                  # 132
HAIR_Y0 = 101.0                             # bob hair ends 1 px above the chin plane
EYE_Y0, EYE_H, EYE_W = 106.5, 11.0, 11.0
EYE_CX = 8.0                                # |x| of each eye centre
EYE_Z = -16.6                               # front plane of the eye_off cards (0.6 px proud of the face: no z-fight)
PIVOT_HEAD = (0, 97.0, 0)
ARM_OUT = 8.0                               # bind pose: arms hang 8 deg away from the body (clears the skirt)


def mx(origin, size):
    """Mirror a cube origin across x=0 (right <-> left)."""
    return (-(origin[0] + size[0]), origin[1], origin[2])


def F(region, w, h, ox=0, oy=0, rot=0):
    """Exact-size sub-rectangle of a region (texel offset ox/oy inside the region)."""
    return (region, ox, oy, w, h, rot) if rot else (region, ox, oy, w, h)


def build():
    atlas = Atlas(256, 256)
    for name, (w, h) in dp.REGIONS.items():
        atlas.add(name, w, h)
    atlas.pack()
    mb = ModelBuilder("geometry.squidgame.doll", atlas, visible=(4.0, 10.0, (0, 4.5, 0)))
    eye_cy = EYE_Y0 + EYE_H / 2

    # ------------------------------------------------------------------ bones
    mb.bone("root", None, (0, 0, 0))
    mb.bone("base", "root", (0, 0, 0))
    mb.bone("body", "base", (0, 6, 0))
    mb.bone("dress", "body", (0, 64, 0))
    mb.bone("left_arm", "body", (14.5, SHOULDER_Y, 0), rotation=(0, 0, -ARM_OUT))
    mb.bone("right_arm", "body", (-14.5, SHOULDER_Y, 0), rotation=(0, 0, ARM_OUT))
    mb.bone("neck_joint", "body", (0, 95.5, 0))
    mb.bone("neck_ring", "neck_joint", (0, 98.5, 0))
    mb.bone("head", "body", PIVOT_HEAD)
    mb.bone("hair", "head", (0, 118, 0))
    mb.bone("pigtail_l", "hair", (22.5, 112.5, 0))
    mb.bone("pigtail_r", "hair", (-22.5, 112.5, 0))
    mb.bone("ribbon", "hair", (0, 139.6, -9))
    mb.bone("eyes_off", "head", (0, eye_cy, EYE_Z))
    mb.bone("eye_off_l", "eyes_off", (EYE_CX, eye_cy, EYE_Z))
    mb.bone("eye_off_r", "eyes_off", (-EYE_CX, eye_cy, EYE_Z))
    mb.bone("eyes_on", "head", (0, eye_cy, EYE_Z - 0.9))
    mb.bone("eye_on_l", "eyes_on", (EYE_CX, eye_cy, EYE_Z - 0.9))
    mb.bone("eye_on_r", "eyes_on", (-EYE_CX, eye_cy, EYE_Z - 0.9))
    mb.bone("mouth", "head", (0, 103.5, -17))

    # ------------------------------------------------------------------ base: plinth (2 steps), shoes, socks
    mb.box("base", (-27, 0, -23), (54, PLINTH_H, 46),
           front=F("plinth_side", 54, 3), back=F("plinth_side", 54, 3),
           left=F("plinth_side", 46, 3, 4), right=F("plinth_side", 46, 3, 4),
           top=F("plinth_top", 54, 46, 0, 4), bottom="solid_dark")
    mb.box("base", (-23, 0, -27), (46, PLINTH_H + 0.05, 54),
           front=F("plinth_side", 46, 3, 4), back=F("plinth_side", 46, 3, 4),
           left=F("plinth_side", 54, 3), right=F("plinth_side", 54, 3),
           top=F("plinth_top", 46, 54, 4, 0), bottom="solid_dark")
    mb.box("base", (-21, 3.0, -17), (42, 1.0, 34),
           front=F("step_side", 42, 1), back=F("step_side", 42, 1), left=F("step_side", 34, 1), right=F("step_side", 34, 1),
           top=F("step_top", 42, 34, 0, 4), bottom=False)
    mb.box("base", (-17, 3.0, -21), (34, 1.05, 42),
           front=F("step_side", 34, 1), back=F("step_side", 34, 1), left=F("step_side", 42, 1), right=F("step_side", 42, 1),
           top=F("step_top", 34, 42, 4, 0), bottom=False)
    shoe = ((-12.5, SHOE_Y0, -13.0), (9, SHOE_Y1 - SHOE_Y0, 15))
    sock = ((-11.5, SHOE_Y1, -5.0), (7, 12, 7))
    for o in (shoe[0], mx(*shoe)):
        mb.box("base", o, shoe[1], front=F("shoe_front", 9, 4), back=F("shoe_front", 9, 4),
               right="shoe_side_r", left="shoe_side_l", top="shoe_top", bottom=False)
    for o in (sock[0], mx(*sock)):
        mb.box("base", o, sock[1], front=F("sock", 7, 12), back=F("sock", 7, 12), left=F("sock", 7, 12),
               right=F("sock", 7, 12), top=False, bottom=False)

    # ------------------------------------------------------------------ dress: 7 octagonal tiers (child of body)
    y = SKIRT_Y0
    for i, (W, c) in enumerate(TIERS):
        row0 = SKIRT_H - (i + 1) * int(TIER_H)       # rows from the waist (top) of the wall texture
        h = TIER_H

        def wall(w, _row0=row0):
            return F("skirt_wall", w, int(h), (44 - w) // 2, _row0)

        bottom = False                                  # covered by the petticoat lip / the tier below
        # box A: full width in x, shallower in z      | box B: narrower in x, full depth in z
        wa, da = W, W - 2 * c
        mb.box("dress", (-wa / 2, y, -da / 2), (wa, h, da), front=wall(wa), back=wall(wa), left=wall(da), right=wall(da),
               top="skirt_top", bottom=bottom)
        wb, db = W - 2 * c, W
        mb.box("dress", (-wb / 2, y, -db / 2), (wb, h + 0.05, db), front=wall(wb), back=wall(wb), left=wall(db), right=wall(db),
               top="skirt_top", bottom=bottom)
        y += TIER_H
    # petticoat lip under the hem (octagonal like the tiers: wide box A + deep box B)
    mb.box("dress", (-23, SKIRT_Y0 - 1.0, -19), (46, 1.2, 38), front=F("petticoat", 46, 2), back=F("petticoat", 46, 2),
           left=F("petticoat", 38, 2, 4, 0), right=F("petticoat", 38, 2, 4, 0), top="solid_white", bottom="solid_dark")
    mb.box("dress", (-19, SKIRT_Y0 - 1.0, -23), (38, 1.25, 46), front=F("petticoat", 38, 2, 4, 0), back=F("petticoat", 38, 2, 4, 0),
           left=F("petticoat", 46, 2), right=F("petticoat", 46, 2), top="solid_white", bottom="solid_dark")

    # ------------------------------------------------------------------ body: waist band, bodice, collar, neck core
    mb.box("body", (-12, WAIST_Y0, -8), (24, WAIST_Y1 - WAIST_Y0, 16),
           front=F("waistband", 24, 3), back=F("waistband", 24, 3), left=F("waistband", 16, 3), right=F("waistband", 16, 3),
           top=False, bottom=False)
    mb.box("body", (-11, BODICE_Y0, -7.5), (22, BODICE_Y1 - BODICE_Y0, 15), front="bodice_front", back="bodice_back",
           left="bodice_side", right="bodice_side", top=False, bottom=False)
    mb.box("body", (-15.5, COLLAR_Y0, -11), (31, COLLAR_Y1 - COLLAR_Y0, 22),
           front=F("collar_edge", 31, 4), back=F("collar_edge", 31, 4), left=F("collar_edge", 22, 4), right=F("collar_edge", 22, 4),
           top="collar_top", bottom="solid_dark")
    mb.box("body", (-10, 79.0, -8.7), (20, 8, 1.2), front="bib", back=False, left="solid_yellow_d", right="solid_yellow_d",
           top=False, bottom="solid_yellow_d")
    mb.box("body", (-6, CORE_Y0, -6), (12, CORE_Y1 - CORE_Y0, 12), sides="neck_core", top=False, bottom=False)

    # ------------------------------------------------------------------ neck joint: octagram cog ring + flange
    ring = (-8.5, RING_Y0, -8.5)
    mb.box("neck_joint", ring, (17, 3, 17), front="ring_a", back="ring_b", left="ring_c", right="ring_d", top=False,
           bottom="solid_dark")
    mb.box("neck_joint", (ring[0], ring[1] + 0.3, ring[2]), (17, 2.2, 17), front="ring_c", back="ring_d", left="ring_a",
           right="ring_b", top=False, bottom=False, rotation=(0, 45, 0), pivot=(0, 95.5, 0))
    mb.box("neck_ring", (-9.5, FLANGE_Y0, -9.5), (19, FLANGE_Y1 - FLANGE_Y0, 19), front=F("flange", 19, 3), back=F("flange", 19, 3),
           left=F("flange", 19, 3), right=F("flange", 19, 3), top=False, bottom=False)

    # ------------------------------------------------------------------ arms (right = -x)
    for side, bone in (("r", "right_arm"), ("l", "left_arm")):
        def O(o, s, _side=side):
            return o if _side == "r" else mx(o, s)

        puff = ((-20.5, 76.5, -5.5), (10, 10, 11))
        mb.box(bone, O(*puff), puff[1], front=F("puff", 10, 10), back=F("puff", 10, 10), left=F("puff", 11, 10),
               right=F("puff", 11, 10), top="puff_top", bottom=False)
        slv = ((-19.5, 61.5, -4.5), (8, 15, 9))
        mb.box(bone, O(*slv), slv[1], front=F("sleeve", 8, 15), back=F("sleeve", 8, 15), left=F("sleeve", 9, 15),
               right=F("sleeve", 9, 15), top=False, bottom=False)
        cuff = ((-19.8, 58.5, -4.8), (9, 3, 10))
        mb.box(bone, O(*cuff), cuff[1], front=F("cuff", 9, 3), back=F("cuff", 9, 3), left="cuff", right="cuff", top=False,
               bottom="skin_dark")
        hand = ((-19.3, 51.5, -3.8), (7, 7, 7))
        mb.box(bone, O(*hand), hand[1], sides=F("hand", 7, 7), top=False, bottom="skin_dark")
        thumb = ((-12.6, 53.5, -4.6), (2, 4, 2))
        mb.box(bone, O(*thumb), thumb[1], front="thumb", back="thumb", left="thumb" if side == "r" else False,
               right=False if side == "r" else "thumb", top="skin_dark", bottom="skin_dark")

    # ------------------------------------------------------------------ head, cheeks, nose
    hw = HEAD_W / 2.0
    # The players stand on the ground and see her from BEHIND and BELOW most of the time (the model front looks at the
    # tree), so the head's underside / back / side strips below the hair (y 100..101) must not show bare skin or the
    # inside of the face: they are dark like the hair above (the part under the chin reads as the chin's shadow).
    mb.box("head", (-hw, HEAD_Y0, -HEAD_D / 2), (HEAD_W, HEAD_H, HEAD_D), front="face", bottom="hair_dark",
           back="hair_dark", left="hair_dark", right="hair_dark", top=False)
    cheek = ((-13.0, 102.5, -16.9), (5, 4, 0.9))
    mb.box("head", cheek[0], cheek[1], front="cheek", left="skin_dark", right=False, top=False, bottom="skin_dark", back=False)
    mb.box("head", mx(*cheek), cheek[1], front="cheek", left=False, right="skin_dark", top=False, bottom="skin_dark", back=False)
    mb.box("head", (-1.5, 105.0, -17.7), (3, 3, 1.7), front="nose", left="skin_dark", right="skin_dark", top="skin_dark",
           bottom=False, back=False)

    # ------------------------------------------------------------------ hair: cap, dome, back, sides, locks, fringe
    cw = HEAD_W + 3.0                                  # cap width (37)
    mb.box("hair", (-cw / 2, 123.0, -18.0), (cw, 10, 36), front=F("hair_front", 37, 10), back=F("hair_back", 37, 10),
           right=F("hair_side_r", 36, 10), left=F("hair_side_l", 36, 10), top="hair_top", bottom=False)
    mb.box("hair", (-17.5, 133.0, -16.0), (35, 1.6, 32), front="hair_dark", back="hair_dark", left="hair_dark",
           right="hair_dark", top=F("hair_top", 35, 32, 1, 2), bottom=False)
    mb.box("hair", (-14.5, 134.6, -13.0), (29, 1.4, 26), front="hair_dark", back="hair_dark", left="hair_dark",
           right="hair_dark", top=F("hair_top", 29, 26, 4, 5), bottom=False)
    mb.box("hair", (-cw / 2, HAIR_Y0, 12.0), (cw, 123.0 - HAIR_Y0, 6.0), back=F("hair_back", 37, 22, 0, 10), left="hair_dark",
           right="hair_dark", front=False, top=False, bottom="hair_dark")
    for sgn in (-1, 1):
        # side panel of the bob, its lowest 2 px inset by 1 px on the outside (rounded bob ends)
        panel = ((-cw / 2 - 3.0, HAIR_Y0 + 2.0, -16.0), (3.0, 123.0 - HAIR_Y0 - 2.0, 34))
        mb.box("hair", panel[0] if sgn < 0 else mx(*panel), panel[1],
               right=F("hair_side_r", 34, 20, 0, 10) if sgn < 0 else False,
               left=F("hair_side_l", 34, 20, 2, 10) if sgn > 0 else False,
               front="hair_dark", back="hair_dark", top="hair_dark", bottom="hair_dark")
        low = ((-cw / 2 - 2.0, HAIR_Y0, -15.0), (2.0, 2.0, 32))
        mb.box("hair", low[0] if sgn < 0 else mx(*low), low[1],
               right=F("hair_side_r", 32, 2, 1, 30) if sgn < 0 else False,
               left=F("hair_side_l", 32, 2, 3, 30) if sgn > 0 else False,
               front="hair_dark", back="hair_dark", top=False, bottom="hair_dark")
        lock = ((-hw + 0.0 - 0.5, 104.0, -17.6), (4.0, 19, 1.7))
        mb.box("hair", lock[0] if sgn < 0 else mx(*lock), lock[1], front="lock_front", back=False, left="hair_dark",
               right="hair_dark", top="hair_dark", bottom="hair_dark")
    # fringe: narrower than the cap, 0.8 px in front of it, top 0.5 px under the cap top (nothing coplanar)
    mb.box("hair", (-17.5, 121.5, -18.8), (35, 11.0, 2.8), front=F("fringe", 35, 11, 1, 0), back=False, left="hair_dark",
           right="hair_dark", top="hair_dark", bottom="hair_dark")
    for (x0, w, hgt) in ((-12.5, 4, 2.0), (-5.5, 3, 1.5), (-1.5, 3, 2.5), (5.0, 3, 1.5), (9.5, 4, 2.0)):
        mb.box("hair", (x0, 121.5 - hgt, -18.8), (w, hgt, 2.8), front="strand", back=False, left="hair_dark",
               right="hair_dark", top=False, bottom="hair_dark")

    # pigtails (the two bob tails) with yellow ties
    for sgn, bone in ((-1, "pigtail_r"), (1, "pigtail_l")):
        def O2(o, s, _sgn=sgn):
            return o if _sgn < 0 else mx(o, s)

        tie = ((-25.5, 111.0, -3.0), (6, 3, 6))
        mb.box(bone, O2(*tie), tie[1], sides=F("tie", 6, 3), top="solid_yellow", bottom="solid_yellow")
        tail = ((-26.0, 94.0, -3.5), (7, 17, 7))
        mb.box(bone, O2(*tail), tail[1], sides="pigtail", top="hair_dark", bottom="hair_dark")
        tip = ((-25.0, 90.5, -3.0), (5, 4, 6))
        mb.box(bone, O2(*tip), tip[1], front=F("pigtail_tip", 5, 4), back=F("pigtail_tip", 5, 4), left="pigtail_tip",
               right="pigtail_tip", top=False, bottom="hair_dark")

    # ribbon / bow on the crown: knot + two wings, each wing = 4 stepped boxes (bow-tie silhouette with real thickness)
    bow_y = 139.6
    mb.box("ribbon", (-2.5, bow_y - 2.5, -10.8), (5, 5, 4), sides="ribbon_knot", top="ribbon_knot", bottom="ribbon_knot")
    segs = ((-2.5, 3, 3), (-5.5, 3, 5), (-8.5, 3, 7), (-11.5, 2, 8))        # (inner edge x, width, height) going outwards
    for sgn, tilt in ((1, 8), (-1, -8)):                                    # sgn 1 = right wing (JSON -x)
        for (xin, w, hgt) in segs:
            x_lo, x_hi = xin - w, xin
            ox = x_lo if sgn > 0 else -x_hi
            row = (9 - hgt) // 2
            mb.box("ribbon", (ox, bow_y - hgt / 2.0, -9.9), (w, hgt, 2.6), front=F("ribbon_seg", w, hgt, 0, row),
                   back=F("ribbon_seg_b", w, hgt, 0, row), left="solid_yellow_d", right="solid_yellow_d", top="solid_yellow",
                   bottom="solid_yellow_d", rotation=(0, 0, tilt), pivot=(-2.5 * sgn, bow_y, -8.6))

    # mouth
    mb.box("mouth", (-3.5, 102.0, -17.5), (7, 3, 1.5), front="mouth", back=False, left="solid_lip_d", right="solid_lip_d",
           top="solid_lip_d", bottom="solid_lip_d")

    # eyes: off = dull dark, on = glowing (glow mask covers every texel of the eye_on regions)
    for sgn, nm in ((-1, "r"), (1, "l")):
        ox = -EYE_CX - EYE_W / 2 if sgn < 0 else EYE_CX - EYE_W / 2
        # only the front faces: the round eye shape comes from the texture alpha (transparent corners); flat side
        # faces would show up as straight rectangular strips around a round eye (and, for eye_on, as glowing lines)
        mb.box("eye_off_" + nm, (ox, EYE_Y0, EYE_Z), (EYE_W, EYE_H, 0.6), front="eye_off", back=False,
               left=False, right=False, top=False, bottom=False)
        mb.box("eye_on_" + nm, (ox - 0.4, EYE_Y0 - 0.4, EYE_Z - 0.9), (EYE_W + 0.8, EYE_H + 0.8, 1.5), front="eye_on", back=False,
               left=False, right=False, top=False, bottom=False)

    dp.paint_all(atlas)
    return mb, atlas
