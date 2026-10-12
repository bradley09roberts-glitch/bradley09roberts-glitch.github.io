"""Citizen CLOTHES layers: greyscale (tinted at runtime by RGB multiply).

Values stay between ~0.36 and 1.0 (see art_skin.LEVELS); level -1 ("x") is a
near-black accent for buckles/holes that stays dark whatever the tint.
Face and hands are always left transparent.

Strip indices (side faces as one wrap):
  body : right 0-3 (back->front) | front 4-11 | left 12-15 (front->back) | back 16-23
  right limb: outer 0-3 (back->front) | front 4-7 (outer->inner) | inner 8-11 | back 12-15 (inner->outer)
Limbs are painted on the right side and mirrored to the left unless noted.
"""
from __future__ import annotations

from art_skin import LevelSkin, SIDES

RA, LA, RL, LL = 'right_arm', 'left_arm', 'right_leg', 'left_leg'


# ======================================================================= building blocks
def torso(g, b, neck='round'):
    g.fill_part('body', b, faces=SIDES)
    g.fill('body', 'top', b + 1)
    g.fill('body', 'bottom', b - 2)
    for y in range(2, 12):                       # roundness of the chest/back
        for f in ('front', 'back'):
            g.shade('body', f, 0, y, -1)
            g.shade('body', f, 7, y, -1)
    for x in (1, 2, 5, 6):                       # chest / shoulder-blade light
        g.shade('body', 'front', x, 1, +1)
        g.shade('body', 'back', x, 1, +1)
    for f in ('right', 'left'):                  # armpit shadow
        for x in range(4):
            g.shade('body', f, x, 0, -2)
            g.shade('body', f, x, 1, -1)
    neckline(g, neck)


def neckline(g, kind):
    F = 'front'
    if kind == 'round':
        for x in range(2, 6):
            g.clear('body', F, x, 0)
        for x in (1, 6):
            g.shade('body', F, x, 0, -1)
        for x in (2, 5):
            g.shade('body', F, x, 1, -1)
    elif kind == 'v':
        for x in range(2, 6):
            g.clear('body', F, x, 0)
        for x in (3, 4):
            g.clear('body', F, x, 1)
            g.clear('body', F, x, 2)
        for x, y in ((1, 0), (2, 1), (5, 1), (6, 0), (2, 2), (5, 2), (3, 3), (4, 3)):
            g.shade('body', F, x, y, -1)
    elif kind == 'square':
        for y in (0, 1):
            for x in range(2, 6):
                g.clear('body', F, x, y)
        for x in range(2, 6):
            g.shade('body', F, x, 2, +1)
        for y in (0, 1):
            g.shade('body', F, 1, y, -1)
            g.shade('body', F, 6, y, -1)
    elif kind == 'high':
        g.band_shade('body', 0, +1)
    if kind != 'high':
        for y in range(1, 3):
            for x in range(2, 6):
                g.clear('body', 'top', x, y)
    for x in (3, 4):
        g.shade('body', 'back', x, 0, -1)


def sleeve(g, b, length=10, cuff=-1, part=RA, mirror=True):
    """Sleeve of ``length`` rows (row 0 = shoulder). cuff = shade added to last row."""
    for y in range(length):
        for i in range(16):
            face_off = (0, 0, -2, -1)[i // 4]
            g.sp(part, i, y, b + face_off)
    g.fill(part, 'top', b + 1)
    for i in (1, 2, 3, 4, 5):                    # shoulder highlight
        g.sshade(part, i, 0, +1)
    if length > 6:                               # elbow creases
        g.sshade(part, 6, 5, -1)
        g.sshade(part, 13, 6, -1)
        g.sshade(part, 14, 6, -1)
    if cuff:
        g.band_shade(part, length - 1, cuff)
    if length >= 12:
        g.fill(part, 'bottom', b - 3)
    if mirror:
        g.mirror_limb(part, LA if part == RA else RA)


def rolled_cuff(g, part, row):
    """Two-row roll of fabric: bright roll over a shadow."""
    for i in range(16):
        v = g.sg(part, i, row)
        if v >= 0:
            g.sp(part, i, row, min(8, v + 2))
    g.band_shade(part, row - 1, -1)


def trousers(g, b, y0=0, y1=9, part=RL, mirror=True):
    for y in range(y0, y1 + 1):
        for i in range(16):
            g.sp(part, i, y, b + (-1, 0, -2, -1)[i // 4])
    for y in (5, 6):                             # knee
        if y0 <= y <= y1:
            g.sshade(part, 5, y, +1)
            g.sshade(part, 6, y, +1)
    for y in range(max(y0, 7), y1 + 1):          # crease
        if y % 2:
            g.sshade(part, 5, y, +1)
    if y0 == 0:
        g.fill(part, 'top', b - 1)
    if y1 == 11:
        g.fill(part, 'bottom', b - 3)
    g.band_shade(part, y1, -1)
    if mirror:
        g.mirror_limb(part, LL if part == RL else RL)


def skirt(g, b, rows, pleats=(0, 1, 0, -1), part=RL, mirror=True, ov=False, start=0):
    """Fabric hanging over the legs (tunic skirt, robe, dress)."""
    for y in range(start, rows):
        for i in range(16):
            face = i // 4
            off = (0, 0, -3, -1)[face] + (pleats[i % 4] if face != 2 else 0)
            g.sp(part, i, y, max(0, b + off), ov)
    if not ov:
        g.fill(part, 'top', b - 2)
    g.band_shade(part, rows - 1, -2, ov)
    if rows == 12:
        g.fill(part, 'bottom', b - 3, ov)
    if mirror:
        g.mirror_limb(part, LL if part == RL else RL, ov)


def belt(g, row, lv=1, buckle=True):
    g.band('body', row, row, lv)
    g.band_shade('body', row + 1, -1)
    if buckle:
        g.set('body', 'front', 3, row, 8)
        g.set('body', 'front', 4, row, 7)


def boots(g, top, lv=2, cuff=True, part=RL, mirror=True):
    for y in range(top, 12):
        for i in range(16):
            g.sp(part, i, y, lv + (0, 0, -1, 0)[i // 4])
    g.sshade(part, 5, 10, +2)
    g.sshade(part, 6, 10, +2)
    g.sshade(part, 5, 9, +1)
    g.band_shade(part, 11, -1)
    if cuff:
        g.band_shade(part, top, +2)
    g.fill(part, 'bottom', 0)
    if mirror:
        g.mirror_limb(part, LL if part == RL else RL)


def pouch(g, face='left', x=1, y=9, lv=2):
    g.set('body', face, x, y, lv + 2)
    g.set('body', face, x + 1, y, lv + 2)
    g.set('body', face, x, y + 1, lv)
    g.set('body', face, x + 1, y + 1, lv)
    g.set('body', face, x, y + 2, lv - 1)
    g.set('body', face, x + 1, y + 2, lv - 1)


# ======================================================================= garments
def tunic():
    g = LevelSkin()
    torso(g, 6, 'v')
    sleeve(g, 6, 10, cuff=-2)
    skirt(g, 6, 4)
    trousers(g, 4, 4, 9)
    belt(g, 8, 1)
    for x in (1, 6):                       # blousing above the belt
        g.shade('body', 'front', x, 7, -1)
    for x in (2, 5):                       # gathered folds below
        g.shade('body', 'front', x, 10, -1)
        g.shade('body', 'front', x, 11, -1)
    for x in (2, 5):
        g.shade('body', 'back', x, 10, -1)
    pouch(g, 'left', 1, 9, 2)
    return g


def smock():
    g = LevelSkin()
    torso(g, 7, 'round')
    sleeve(g, 7, 6, cuff=0)
    for p in (RA, LA):
        rolled_cuff(g, p, 5)
    # smocked yoke: honeycomb stitches across the chest
    for y, xs in ((1, (1, 3, 5)), (2, (2, 4, 6)), (3, (1, 3, 5))):
        for x in xs:
            g.set('body', 'front', x, y, 5)
    g.band_shade('body', 4, -1)
    for y in range(5, 12):                 # loose vertical folds
        g.shade('body', 'front', 2, y, -1)
        g.shade('body', 'front', 5, y, -1)
        g.shade('body', 'back', 3, y, -1)
    g.set('body', 'front', 3, 1, -1)       # neck drawstring
    g.set('body', 'front', 4, 1, -1)
    g.set('body', 'front', 4, 2, 4)
    skirt(g, 7, 5, pleats=(0, 0, -1, 0))
    trousers(g, 4, 5, 7)
    rolled_cuff(g, RL, 7)
    rolled_cuff(g, LL, 7)
    return g


def apron():
    g = LevelSkin()
    torso(g, 7, 'round')
    sleeve(g, 7, 7, cuff=0)
    for p in (RA, LA):
        rolled_cuff(g, p, 6)
    trousers(g, 4, 0, 9)
    A = 2
    # bib and straps
    for y in range(2, 12):
        for x in range(1, 7):
            g.set('body', 'front', x, y, A)
    for y in (0, 1):
        g.set('body', 'front', 1, y, A)
        g.set('body', 'front', 6, y, A)
    for y in range(4):
        g.set('body', 'top', 1, y, A)
        g.set('body', 'top', 6, y, A)
    for y in range(8):                      # crossed straps on the back
        g.set('body', 'back', 1 + (y * 5) // 7, y, A)
        g.set('body', 'back', 6 - (y * 5) // 7, y, A)
    g.band('body', 8, 8, A)                  # waist tie
    g.set('body', 'back', 3, 9, A + 1)
    g.set('body', 'back', 4, 9, A + 1)
    g.set('body', 'back', 3, 10, A)
    g.set('body', 'back', 5, 10, A)
    for y in range(2, 12):                  # stitched edges
        if y % 2 == 0:
            g.set('body', 'front', 1, y, A + 2)
            g.set('body', 'front', 6, y, A + 2)
    for x in range(1, 7):
        g.set('body', 'front', x, 2, A + 2)
    # pocket with a tool handle
    for x in range(2, 6):
        g.set('body', 'front', x, 6, A + 3)
    for y in (7, 8, 9):
        g.set('body', 'front', 2, y, A - 1)
        g.set('body', 'front', 5, y, A - 1)
    g.set('body', 'front', 4, 5, 6)
    g.set('body', 'front', 4, 4, 7)
    g.set('body', 'front', 3, 10, A - 1)    # scorch marks
    g.set('body', 'front', 5, 3, A - 1)
    # apron skirt over the legs (front only)
    for y in range(0, 9):
        for x in range(1, 4):
            g.set(RL, 'front', x, y, A)
        g.set(RL, 'front', 1, y, A + 2 if y % 2 == 0 else A)
    g.set(RL, 'front', 2, 8, A - 1)
    g.set(RL, 'front', 3, 8, A - 1)
    g.set(RL, 'front', 1, 8, A - 1)
    g.set(RL, 'front', 2, 4, A - 1)
    g.mirror_limb(RL, LL)
    return g


def robe():
    g = LevelSkin()
    b = 5
    torso(g, b, 'v')
    sleeve(g, b, 10, cuff=0)
    # bell sleeves flaring in the sleeve overlay
    for p in (RA,):
        for y in range(6, 11):
            for i in range(16):
                g.sp(p, i, y, b + (0, 0, -2, -1)[i // 4], ov=True)
        g.band(p, 10, 10, 7, ov=True)
        g.band_shade(p, 6, -1, ov=True)
        g.sshade(p, 5, 8, +1, ov=True)
        g.mirror_limb(RA, LA, ov=True)
    # long skirt to the ankles
    skirt(g, b, 11, pleats=(0, 1, 0, -1))
    for p in (RL, LL):
        g.band(p, 10, 10, b - 2)
        g.band(p, 9, 9, 7)
    # orphrey (decorated front band)
    for y in range(3, 12):
        g.set('body', 'front', 3, y, 7)
        g.set('body', 'front', 4, y, 6)
    for y in range(0, 9):
        g.set(RL, 'front', 3, y, 7)
        g.set(LL, 'front', 0, y, 6)
    for y in (4, 7, 10):
        g.set('body', 'front', 3, y, 8)
    # rope belt with hanging cord
    for i in range(24):
        g.sp('body', i, 7, 8 if i % 2 else 7)
    g.set('body', 'front', 2, 8, 7)
    g.set('body', 'front', 2, 9, 8)
    g.set('body', 'front', 2, 10, 7)
    g.set('body', 'front', 2, 11, 8)
    g.set(RL, 'front', 2, 0, 7)
    g.set(RL, 'front', 2, 1, 8)
    g.set(RL, 'front', 2, 2, 6)
    # cowl resting on the shoulders (jacket overlay)
    g.fill('body', 'top', b, ov=True)
    for x in range(2, 6):
        g.set('body', 'top', x, 3, b - 1, ov=True)
    for x in (0, 1, 6, 7):
        g.set('body', 'front', x, 0, b - 1, ov=True)
    for f in ('right', 'left'):
        for x in range(4):
            g.set('body', f, x, 0, b, ov=True)
    for y in range(3):
        for x in range(8):
            g.set('body', 'back', x, y, b if y < 2 else b - 1, ov=True)
    for x in (2, 5):
        g.set('body', 'back', x, 1, b - 1, ov=True)
    for x in (3, 4):
        g.set('body', 'back', x, 3, b - 1, ov=True)
    return g


def dress():
    g = LevelSkin()
    b = 6
    torso(g, b, 'square')
    sleeve(g, b, 10, cuff=0)
    for p in (RA, LA):
        g.band(p, 9, 9, 8)
        g.band_shade(p, 8, -1)
    for y in range(2, 7):                    # bodice lacing
        g.set('body', 'front', 3, y, 8 if y % 2 == 0 else 3)
        g.set('body', 'front', 4, y, 3 if y % 2 == 0 else 8)
    for y in range(2, 8):                    # fitted bodice seams
        g.shade('body', 'front', 1, y, -1)
        g.shade('body', 'front', 6, y, -1)
    belt(g, 8, 2, buckle=False)
    g.set('body', 'front', 4, 8, 8)
    g.set('body', 'front', 5, 8, 7)
    for y in (9, 10, 11):                    # girdle end hanging down
        g.set('body', 'front', 5, y, 2)
    for y in range(9, 12):
        for x in (1, 2, 3, 4, 6):
            g.shade('body', 'front', x, y, (0, 1, 0, -1, 0, 0, 1)[x])
    skirt(g, b, 12)
    for y in (0, 1, 2):
        g.set(LL, 'front', 1, y, 2)
    g.set(LL, 'front', 1, 3, 8)
    # flared lower skirt in the pants overlay
    skirt(g, b, 12, pleats=(1, 0, -1, 0), ov=True, start=6)
    for p in (RL, LL):
        g.band_shade(p, 6, -1, ov=True)
        g.band(p, 11, 11, b - 2, ov=True)
        g.band(p, 10, 10, b + 1, ov=True)
    return g


def gambeson():
    g = LevelSkin()
    b = 6
    torso(g, b, 'high')
    # quilted vertical channels on the torso
    for y in range(1, 12):
        for i in range(24):
            if y % 4 == 0:
                if i % 2 == 1:
                    g.sshade('body', i, y, -1)     # stitch dashes across the channels
            elif i % 2 == 1:
                g.sshade('body', i, y, -1)         # channel seams
            elif y % 4 == 1:
                g.sshade('body', i, y, +1)         # padded puff catching the light
    for y in range(1, 12):                   # front opening with toggles
        g.set('body', 'front', 3, y, b - 3)
    for y in (2, 5, 8):
        g.set('body', 'front', 4, y, 8)
    g.band('body', 0, 0, b + 1)
    # standing collar (jacket overlay)
    for f, w in (('front', 8), ('back', 8), ('right', 4), ('left', 4)):
        for x in range(w):
            g.set('body', f, x, 0, b + 1, ov=True)
    for x in (2, 3, 4, 5):
        g.clear('body', 'front', x, 0, ov=True)
    g.set('body', 'front', 1, 0, b, ov=True)
    g.set('body', 'front', 6, 0, b, ov=True)
    # quilted sleeves (horizontal channels)
    sleeve(g, b, 10, cuff=-2, mirror=False)
    for y in range(1, 9):
        for i in range(16):
            if y % 3 == 0:
                g.sshade(RA, i, y, -1)
            elif y % 3 == 1 and i % 4 in (1, 2):
                g.sshade(RA, i, y, +1)
    g.mirror_limb(RA, LA)
    skirt(g, b, 4, pleats=(0, -1, 0, -1))
    for p in (RL, LL):
        for i in range(16):
            g.sshade(p, i, 1, +1)
        g.band(p, 3, 3, b - 3)
    belt(g, 9, 1)
    trousers(g, 3, 4, 9)
    boots(g, 8, 2)
    return g


def noble():
    g = LevelSkin()
    b = 5
    torso(g, b, 'high')
    g.band('body', 0, 0, 8)                  # trimmed collar
    for y in range(1, 9):                    # buttoned front
        g.set('body', 'front', 3, y, b - 2)
        g.set('body', 'front', 4, y, 8 if y % 2 else b)
    for y in range(2, 7):                    # slashed chest panels
        for x in (1, 6):
            g.set('body', 'front', x, y, 7 if y % 2 == 0 else b - 1)
    belt(g, 8, 1)
    g.set('body', 'front', 3, 8, 8)
    g.set('body', 'front', 4, 8, 8)
    for y in (9, 10):                        # peplum
        for i in range(24):
            g.sshade('body', i, y, +1 if i % 3 == 0 else 0)
    g.band('body', 11, 11, 8)
    # puffed & slashed upper sleeves, fitted forearm, lace cuff
    sleeve(g, b, 10, cuff=0, mirror=False)
    for y in range(0, 4):
        for i in range(16):
            g.sp(RA, i, y, 7 if i % 2 == 0 else b - 1)
            if i // 4 == 2:
                g.sshade(RA, i, y, -2)
    g.band(RA, 4, 4, 8)
    g.band(RA, 9, 9, 8)
    g.mirror_limb(RA, LA)
    # trunk hose + hose
    trousers(g, 2, 0, 9)
    for y in range(0, 4):
        for i in range(16):
            g.sp(RL, i, y, 6 if i % 2 == 0 else 3)
            if i // 4 == 2:
                g.sshade(RL, i, y, -2)
    g.band(RL, 4, 4, 8)
    g.band(RL, 3, 3, 2)
    g.mirror_limb(RL, LL)
    # short cape (jacket overlay) over the back and shoulders
    c = 3
    g.fill('body', 'top', c, ov=True)
    for x in range(2, 6):
        g.clear('body', 'top', x, 2, ov=True)
        g.clear('body', 'top', x, 3, ov=True)
    for y in range(0, 10):
        for x in range(8):
            g.set('body', 'back', x, y, c + (1 if x in (2, 5) else 0) - (1 if x in (0, 7) else 0), ov=True)
    for x in range(8):
        g.set('body', 'back', x, 9, 8, ov=True)
    for f in ('right', 'left'):
        for x in range(4):
            g.set('body', f, x, 0, c, ov=True)
        back_cols = range(0, 2) if f == 'right' else range(2, 4)
        for y in range(1, 9):
            for x in back_cols:
                g.set('body', f, x, y, c - 1, ov=True)
        for x in back_cols:
            g.set('body', f, x, 9, 8, ov=True)
    for x in (0, 7):
        g.set('body', 'front', x, 0, c, ov=True)
    g.set('body', 'front', 1, 0, 8, ov=True)     # clasps + chain
    g.set('body', 'front', 6, 0, 8, ov=True)
    for p in (RA, LA):                           # cape over the shoulder tops
        g.fill(p, 'top', c, ov=True)
    return g


def traveller():
    g = LevelSkin()
    torso(g, 5, 'round')
    sleeve(g, 5, 10, cuff=-1)
    skirt(g, 5, 3)
    belt(g, 8, 1)
    trousers(g, 4, 3, 9)
    boots(g, 6, 2)
    # cloak (jacket overlay): open at the front
    c = 3
    g.fill('body', 'top', c + 1, ov=True)
    for y in range(12):
        for x in range(8):
            v = c + (1 if x in (2, 5) else 0) - (1 if x in (0, 4, 7) else 0)
            g.set('body', 'back', x, y, v, ov=True)
        for x in range(4):
            g.set('body', 'right', x, y, c - (1 if x == 3 else 0), ov=True)
            g.set('body', 'left', x, y, c - (1 if x == 0 else 0), ov=True)
        for x in (0, 7):
            g.set('body', 'front', x, y, c, ov=True)
    for y in range(12):                          # lit inner edge of the opening
        g.set('body', 'front', 1, y, c + 2 if y < 2 else c + 1, ov=True)
        g.set('body', 'front', 6, y, c + 2 if y < 2 else c + 1, ov=True)
    g.set('body', 'front', 2, 0, 8, ov=True)     # brooch clasp
    g.set('body', 'front', 2, 1, c + 1, ov=True)
    g.band_shade('body', 11, -1, ov=True)
    # cloak falls over the shoulders onto the arms
    for p in (RA,):
        g.fill(p, 'top', c + 1, ov=True)
        for y in range(0, 10):
            for x in range(4):
                g.set(p, 'right', x, y, c + (1 if x == 2 else 0), ov=True)
            g.set(p, 'front', 0, y, c + 1, ov=True)
            g.set(p, 'back', 3, y, c - 1, ov=True)
        for x in range(4):
            g.set(p, 'right', x, 9, c - 1, ov=True)
        g.mirror_limb(RA, LA, ov=True)
    # cloak continues behind the legs (pants overlay)
    for y in range(0, 8):
        for x in range(4):
            g.set(RL, 'back', x, y, c + (1 if x == 2 else 0) - (1 if x == 0 else 0), ov=True)
        g.set(RL, 'right', 0, y, c, ov=True)
    for x in range(4):
        g.set(RL, 'back', x, 7, c - 1, ov=True)
    g.mirror_limb(RL, LL, ov=True)
    # hood (hat overlay): solid fabric with an opening for the face
    h = 4
    g.fill('head', 'top', h + 1, ov=True)
    for x in range(8):
        g.set('head', 'top', x, 7, h, ov=True)
    for y in range(8):
        for x in range(8):
            g.set('head', 'back', x, y, h - (1 if y >= 6 else 0) + (1 if x in (3, 4) and y < 3 else 0), ov=True)
            g.set('head', 'right', x, y, h + (1 if y < 2 else 0) - (1 if x == 7 else 0), ov=True)
    g.set('head', 'back', 3, 1, h - 1, ov=True)  # seam
    g.set('head', 'back', 4, 2, h - 1, ov=True)
    for x in range(8):
        g.set('head', 'front', x, 0, h + 1, ov=True)
        g.set('head', 'front', x, 1, h, ov=True)
    for y in range(2, 8):
        g.set('head', 'front', 0, y, h, ov=True)
        g.set('head', 'front', 7, y, h, ov=True)
    for x in range(1, 7):
        g.set('head', 'front', x, 1, h - 2, ov=True)
    g.mirror_head(ov=True)
    return g


def rags():
    g = LevelSkin()
    b = 5
    torso(g, b, 'round')
    # torn, uneven sleeves
    sleeve(g, b, 7, cuff=0, mirror=False)
    for i, cut in enumerate((6, 7, 5, 6, 7, 6, 5, 6, 5, 6, 7, 6, 5, 6, 7, 6)):
        for y in range(cut, 7):
            g.sp(RA, i, y, -9)
        g.sshade(RA, i, cut - 1, -1)
    g.mirror_limb(RA, LA)
    for i, cut in enumerate((4, 3, 4, 5, 4, 3, 4, 4, 5, 4, 3, 4, 4, 5, 4, 3)):
        for y in range(cut, 7):
            g.sp(LA, i, y, -9)
        g.sshade(LA, i, cut - 1, -1)
    # ragged tunic skirt with uneven hem
    skirt(g, b, 5, pleats=(0, -1, 0, 1))
    for p, cuts in ((RL, (3, 4, 5, 4, 3, 5, 4, 3, 4, 2, 3, 4, 5, 3, 4, 3)),
                    (LL, (4, 3, 4, 5, 3, 4, 2, 4, 3, 4, 5, 3, 4, 3, 5, 4))):
        for i, cut in enumerate(cuts):
            for y in range(cut, 5):
                g.sp(p, i, y, -9)
            g.sshade(p, i, cut - 1, -1)
    # trousers torn at different lengths
    for p, cuts in ((RL, (8, 9, 8, 7, 8, 9, 7, 8, 9, 8, 7, 8, 9, 8, 7, 8)),
                    (LL, (9, 8, 9, 9, 8, 7, 8, 9, 9, 8, 9, 9, 8, 9, 9, 8))):
        for y in range(0, 10):
            for i in range(16):
                if g.sg(p, i, y) < 0 and y < cuts[i]:
                    g.sp(p, i, y, 3 + (-1, 0, -2, -1)[i // 4])
        for i, cut in enumerate(cuts):
            g.sshade(p, i, cut - 1, -1)
    g.fill(RL, 'top', 3)
    g.fill(LL, 'top', 3)
    # rope belt
    for i in range(24):
        g.sp('body', i, 8, 7 if i % 2 else 6)
    g.set('body', 'front', 5, 9, 7)
    g.set('body', 'front', 5, 10, 6)
    # patches with stitches
    for x in range(1, 4):
        for y in range(3, 6):
            g.set('body', 'front', x, y, 8 if (x + y) % 2 else 7)
    g.set('body', 'front', 1, 3, 4)
    g.set('body', 'front', 3, 5, 4)
    for x in range(5, 8):
        for y in range(2, 5):
            g.set('body', 'back', x, y, 3)
    g.set('body', 'back', 5, 2, 7)
    g.set('body', 'back', 7, 4, 7)
    for y in (5, 6):
        for x in (1, 2):
            g.set(RL, 'front', x, y, 7)
    g.set(RL, 'front', 1, 5, 5)
    # holes and grime
    g.clear('body', 'front', 6, 6)
    g.set('body', 'front', 6, 7, b - 2)
    g.clear('body', 'back', 2, 8)
    g.clear('body', 'right', 2, 4)
    for x, y in ((2, 10), (6, 2), (4, 7)):
        g.shade('body', 'front', x, y, -2)
    g.set(LL, 'front', 2, 6, -9)
    g.set(LL, 'front', 2, 7, 1)
    return g


FUR_ROWS = ("8786", "7878", "6787", "8676")


def fur_patch(g, part, face, x, y, ov=False, k=0):
    """Fluffy fur texel: light with darker tufts on a 4x4 repeat."""
    v = int(FUR_ROWS[(y + k) % 4][(x + y // 2) % 4])
    g.set(part, face, x, y, v, ov)


def fur():
    g = LevelSkin()
    b = 3
    torso(g, b, 'round')
    for y in range(2, 12):                       # stitched leather panels
        if y % 2:
            g.shade('body', 'front', 2, y, -1)
            g.shade('body', 'front', 5, y, -1)
    for y in range(0, 12):                       # bandolier strap
        x = 1 + (y * 6) // 11
        g.set('body', 'front', x, y, 1)
    g.set('body', 'front', 4, 6, 7)
    belt(g, 9, 1, buckle=False)
    for x, y in ((3, 9), (4, 9), (3, 10), (4, 10)):
        g.set('body', 'front', x, y, 8 if (x, y) != (4, 10) else 6)
    # short leather sleeves, bracers on the forearms
    sleeve(g, b, 3, cuff=0, mirror=False)
    for y in range(6, 10):
        for i in range(16):
            g.sp(RA, i, y, 3 + (0, 0, -2, -1)[i // 4])
    for y in (6, 9):
        g.band_shade(RA, y, -1)
    for y in (7, 8):
        g.sshade(RA, 5, y, +4)
        g.sshade(RA, 6, y, +2)
    g.mirror_limb(RA, LA)
    # jerkin skirt with fur hem, trousers, fur-wrapped boots
    skirt(g, b, 3, pleats=(0, 0, -1, 0))
    for i in range(16):
        for p in (RL,):
            g.sp(p, i, 2, int(FUR_ROWS[0][i % 4]))
    trousers(g, 2, 3, 7)
    for y in range(8, 12):
        for x in range(4):
            for f in SIDES:
                fur_patch(g, RL, f, x, y, k=1)
    for f in ('front', 'right', 'back'):
        g.set(RL, f, 1, 9, 1)
        g.set(RL, f, 2, 10, 1)
    g.fill(RL, 'bottom', 0)
    g.mirror_limb(RL, LL)
    # fur mantle (jacket overlay) with ragged lower edge
    for y in range(4):
        for x in range(8):
            fur_patch(g, 'body', 'top', x, y, ov=True)
    edges = {'front': (2, 3, 2, 1, 1, 2, 3, 2), 'back': (4, 3, 4, 5, 4, 3, 4, 4),
             'right': (3, 3, 2, 3), 'left': (3, 2, 3, 3)}
    for f, depth in edges.items():
        for x, d in enumerate(depth):
            for y in range(d):
                fur_patch(g, 'body', f, x, y, ov=True)
            g.set('body', f, x, d - 1, 5, ov=True)
    for x in (2, 3, 4, 5):                        # keep the throat open
        g.clear('body', 'front', x, 0, ov=True)
    for x in (3, 4):
        g.clear('body', 'front', x, 1, ov=True)
    # fur pauldrons (sleeve overlay)
    for x in range(4):
        for y in range(4):
            fur_patch(g, RA, 'top', x, y, ov=True)
    for f, depth in (('right', (3, 2, 3, 3)), ('front', (3, 3, 2, 2)), ('back', (2, 2, 3, 3)), ('left', (1, 1, 1, 1))):
        for x, d in enumerate(depth):
            for y in range(d):
                fur_patch(g, RA, f, x, y, ov=True, k=2)
            g.set(RA, f, x, d - 1, 5, ov=True)
    g.mirror_limb(RA, LA, ov=True)
    # fluffy boot tops (pants overlay)
    for f in SIDES:
        for x in range(4):
            fur_patch(g, RL, f, x, 8, ov=True, k=3)
            if (x + len(f)) % 2:
                fur_patch(g, RL, f, x, 9, ov=True, k=3)
    g.mirror_limb(RL, LL, ov=True)
    return g


CLOTHES = {
    'tunic': tunic, 'smock': smock, 'apron': apron, 'robe': robe, 'dress': dress,
    'gambeson': gambeson, 'noble': noble, 'traveller': traveller, 'rags': rags, 'fur': fur,
}


def make_clothes(cid):
    return CLOTHES[cid]()
