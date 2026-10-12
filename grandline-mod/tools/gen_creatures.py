#!/usr/bin/env python3
"""Generates the GeckoLib assets for Grand Line Odyssey creatures:
  * Sea Kings (3 variants)       geo/sea_king, animations/sea_king, textures/entity/sea_king
  * Zoan full-beast forms (11)   geo/zoan,     animations/zoan,     textures/entity/zoan
  * island wildlife              geo/beast,    animations/beast,    textures/entity/beast
Run from the grandline-mod directory:  python3 -I tools/gen_creatures.py
Animation files use the .anim.json suffix so the humanoid animation loader (client/anim, *.animation.json) ignores them."""
import math, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from creaturelib import *  # noqa: E402,F401,F403

A = 'src/main/resources/assets/grandline'


def out(model, kind, name, anims=None):
    model.write(f'{A}/geo/{kind}/{name}.geo.json', f'{A}/textures/entity/{kind}/{name}.png')
    if anims is not None:
        anims.write(f'{A}/animations/{kind}/{name}.anim.json')


# ======================================================================================== SEA KINGS
# Must match SeaKing.java: SEGMENTS, NECK, SPACING, HEAD_H and diameter(k).
SEGMENTS, NECK, SPACING, HEAD_H = 14, 1.35, 1.48, 2.0


def sk_diameter(k):
    t = k / (SEGMENTS - 1)
    if k < 3:
        return 1.95 * (0.88 + 0.04 * k)
    return 1.95 * (1 - 0.64 * ((k - 3) / (SEGMENTS - 4)) ** 1.25) * (0.95 if t > 0.95 else 1)


SK_PALETTES = {
    'serpent': dict(back=0x1D5752, side=0x2E8A78, belly=0xEAD9A0, spike=(0xC8401E, 0xF4A43C), fin=0xD8603A, fin_vein=0x8A2A16,
                    eye=0xFFD23A, mouth=0x7A2234, horn=(0xE8DEC0, 0x7A6A50), spots=None),
    'maw': dict(back=0x4E4226, side=0x7A6A3E, belly=0xF0E0BC, spike=(0x5A6E80, 0x9AB4C8), fin=0x6E8CA4, fin_vein=0x34485A,
                eye=0xE8502A, mouth=0x5E1626, horn=(0x8A6A44, 0x4A3420), spots=0x2E2614),
    'horned': dict(back=0x34183A, side=0x642A50, belly=0xD8C2D6, spike=(0x161216, 0x5A5060), fin=0x7A2A48, fin_vein=0x3A0E22,
                   eye=0x9CFF5A, mouth=0x4E0818, horn=(0xDCCBA0, 0x4E3E2C), spots=None),
}


def sea_king(variant):
    pal = SK_PALETTES[variant]
    m = Model('sea_king_' + variant, 256, 128, density=1.0)
    scales = pat_scales(8, 6, 0.3)
    side = p_gradient(mix(hexc(pal['back']), hexc(pal['side']), 0.3), pal['belly'], pat_combine(scales, pat_mottle(10, 0.12, 21)), power=1.35, seed=3)
    top = p_flat(pal['back'], pat_combine(scales, pat_mottle(12, 0.15, 5)), seed=4)
    if pal['spots']:
        side = p_spots(side, pal['spots'], 12, 0.32, seed=31)
        top = p_spots(top, pal['spots'], 12, 0.36, seed=32)
    if variant == 'serpent':
        side = p_stripes(side, 0x153F3B, 3, 0.18, seed=12, wobble=4)
    m.mat('side', 64, 64, side, vfull=True)
    m.mat('top', 64, 64, top)
    belly_plate = pat_plates(5, 0.32)
    m.mat('belly', 48, 40, p_flat(pal['belly'], pat_combine(belly_plate, pat_mottle(8, 0.06, 9)), seed=6))
    m.mat('spike', 16, 32, p_gradient(pal['spike'][1], pal['spike'][0], power=0.8, noise=0.1, rim=0.0), vfull=True)
    m.mat('fin', 48, 48, p_membrane(pal['fin'], pal['fin_vein']))
    m.mat('eye', 8, 8, p_eye(pal['eye'], slit=variant != 'maw'), fit=True, glow=True)
    m.mat('tooth', 8, 8, p_teeth(), fit=True)
    m.mat('mouth', 32, 32, p_flat(pal['mouth'], pat_mottle(6, 0.2, 3), seed=8))
    m.mat('dark', 8, 8, p_solid(0x141010), fit=True)
    m.mat('horn', 16, 32, p_horn(*pal['horn']), vfull=True)
    m.mat('plate', 32, 32, p_flat(mix(hexc(pal['back']), (0, 0, 0), 0.3), pat_combine(pat_plates(6, 0.4, 'u'), pat_plates(6, 0.3)), seed=13))
    skin = {'side': 'side', 'up': 'top', 'down': 'belly'}

    # ---------------- body segments (positioned in code along the swimming path)
    seglen = SPACING * 16 * 1.5
    for k in range(SEGMENTS):
        d = sk_diameter(k) * 16
        b = m.bone(f'seg{k}', pivot=(0, 0, 0))
        r = m.bone(f'seg{k}_roll', b, pivot=(0, 0, 0))
        hh = d * 0.92
        m.cbox(r, (0, 0, 0), (d, hh, seglen), skin)
        m.cbox(r, (0, -hh / 2 - d * 0.04, 0), (d * 0.66, d * 0.1, seglen * 0.94), {'all': 'belly'})
        # ridge along the back
        if variant == 'maw':
            m.cbox(r, (0, hh / 2 + d * 0.18, 2), (1.2, d * 0.38, seglen * 0.95), {'all': 'fin'})
        elif variant == 'horned':
            for sx in (-1, 1):
                m.box(r, (sx * d * 0.18 - 1.5, hh / 2 - 1, -4), (3, d * 0.28, 7), {'all': 'spike'}, rot=(-28, 0, 0), pivot=(sx * d * 0.18, hh / 2, -1))
            m.cbox(r, (0, hh / 2 + 0.3, 0), (d * 0.5, 1.2, seglen * 0.8), {'all': 'plate'})
        else:
            hs = d * (0.42 if 2 <= k <= 9 else 0.3)
            m.box(r, (-1.2, hh / 2 - 1, -6), (2.4, hs, 9), {'all': 'spike'}, rot=(-30, 0, 0), pivot=(0, hh / 2, -2))
        if k == 1:
            span = {'serpent': 16, 'maw': 28, 'horned': 13}[variant]
            for sx, n in ((1, 'pec_l'), (-1, 'pec_r')):
                fb = m.bone(n, r, pivot=(sx * d * 0.45, -d * 0.18, -4))
                m.box(fb, (sx * d * 0.45 if sx > 0 else -d * 0.45 - span, -d * 0.18 - 0.6, -6), (span, 1.2, 20), {'all': 'fin'}, rot=(0, 0, sx * 18), pivot=(sx * d * 0.45, -d * 0.18, -4))
        if k == 8:
            span = {'serpent': 9, 'maw': 13, 'horned': 8}[variant]
            for sx, n in ((1, 'mid_l'), (-1, 'mid_r')):
                fb = m.bone(n, r, pivot=(sx * d * 0.45, -d * 0.2, 0))
                m.box(fb, (sx * d * 0.45 if sx > 0 else -d * 0.45 - span, -d * 0.2 - 0.5, -3), (span, 1.0, 12), {'all': 'fin'}, rot=(0, 0, sx * 24), pivot=(sx * d * 0.45, -d * 0.2, 0))
        if k == SEGMENTS - 1:
            tf = m.bone('tailfin', r, pivot=(0, 0, seglen / 2 - 2))
            if variant == 'maw':
                for sx in (-1, 1):  # whale-like flukes
                    m.box(tf, (0 if sx > 0 else -22, -0.8, seglen / 2 - 4), (22, 1.6, 16), {'all': 'fin'}, rot=(0, sx * -28, 0), pivot=(0, 0, seglen / 2 - 4))
            elif variant == 'horned':
                m.cbox(tf, (0, 0, seglen / 2 + 3), (d * 0.9, d * 0.9, 8), skin)
                for a in range(6):
                    ang = a * 60
                    m.box(tf, (-1.5, d * 0.4, seglen / 2), (3, 7, 3), {'all': 'spike'}, rot=(0, 0, ang), pivot=(0, 0, seglen / 2 + 1.5))
            else:
                m.box(tf, (-0.8, -16, seglen / 2 - 6), (1.6, 32, 24), {'all': 'fin'})
                m.box(tf, (-1.2, -3, seglen / 2 - 6), (2.4, 6, 30), {'all': 'spike'})

    # ---------------- head
    head = m.bone('head', pivot=(0, 16, 0))
    hr = m.bone('head_roll', head, pivot=(0, 16, 0))
    d0 = sk_diameter(0) * 16
    m.cbox(hr, (0, 16, 15), (d0 * 0.98, d0 * 0.9, 18), skin)                          # neck stub into the first segment
    if variant == 'serpent':
        m.box(hr, (-15, 6, -20), (30, 22, 32), skin)                               # skull
        m.box(hr, (-15.5, 24, -21), (31, 5, 13), {'all': 'top'})                    # brows
        m.box(hr, (-11, 13, -42), (22, 12, 22), dict(skin, down='mouth'))          # upper snout
        m.box(hr, (-12, 22, -40), (24, 4, 18), {'all': 'top'})                      # snout ridge
        for sx in (-1, 1):
            m.box(hr, (sx * 5 - 1.5, 25.5, -41), (3, 1, 3), {'all': 'dark'})         # nostrils
            m.box(hr, (sx * 15.05 - 0.4 * (sx < 0), 19, -17), (0.4, 5, 6), {'east': 'eye', 'west': 'eye', 'all': 'dark'})
            for z in range(-41, -20, 4):
                m.box(hr, (sx * 9.5 - 1.2, 9.5, z), (2.4, 3.6, 2.4), {'all': 'tooth'})
            hb = m.bone('horn_' + ('l' if sx > 0 else 'r'), hr, pivot=(sx * 9, 27, -6))
            m.box(hb, (sx * 9 - 2, 26, -6), (4, 4, 14), {'all': 'horn', 'north': 'horn', 'up': 'horn'}, rot=(25, sx * -12, 0), pivot=(sx * 9, 28, -6))
            fb = m.bone('fin_' + ('l' if sx > 0 else 'r'), hr, pivot=(sx * 14.5, 20, -2))
            m.box(fb, (sx * 14.5 - 0.6, 10, -2), (1.2, 22, 20), {'all': 'fin'}, rot=(0, sx * 28, 0), pivot=(sx * 14.5, 20, -2))
        jaw = m.bone('jaw', hr, pivot=(0, 11, -12))
        m.box(jaw, (-10.5, 5, -41), (21, 6, 30), dict(skin, up='mouth'))
        for sx in (-1, 1):
            for z in range(-39, -20, 4):
                m.box(jaw, (sx * 8.5 - 1.1, 11, z), (2.2, 3.2, 2.2), {'all': 'tooth'})
    elif variant == 'maw':
        m.box(hr, (-17, 6, -24), (34, 24, 36), skin)                               # great skull
        m.box(hr, (-13, 29, -22), (26, 4, 26), {'all': 'top'})                      # brow hump
        m.box(hr, (-15, 12, -36), (30, 12, 12), dict(skin, down='mouth'))          # upper lip
        for sx in (-1, 1):
            m.box(hr, (sx * 17.05 - 0.4 * (sx < 0), 22, -20), (0.4, 4, 4), {'east': 'eye', 'west': 'eye', 'all': 'dark'})
            for gz in (2, 6, 10):
                m.box(hr, (sx * 17.05 - 0.4 * (sx < 0), 9, gz), (0.4, 16, 1.4), {'all': 'dark'})  # gill slits
            for z in range(-35, -24, 4):
                m.box(hr, (sx * 13 - 1, 9.5, z), (2, 3, 2), {'all': 'tooth'})
            bb = m.bone('barbel_' + ('l' if sx > 0 else 'r'), hr, pivot=(sx * 14, 14, -33))
            m.box(bb, (sx * 14 - 1, 13, -33), (2, 2, 26), {'all': 'horn'}, rot=(48, sx * -18, 0), pivot=(sx * 14, 14, -33))
            fb = m.bone('fin_' + ('l' if sx > 0 else 'r'), hr, pivot=(sx * 16, 16, 6))
            m.box(fb, (sx * 16 - 0.6, 8, 6), (1.2, 16, 12), {'all': 'fin'}, rot=(0, sx * 35, 0), pivot=(sx * 16, 16, 6))
        jaw = m.bone('jaw', hr, pivot=(0, 12, -8))
        m.box(jaw, (-16, 0, -40), (32, 12, 34), dict(skin, up='mouth'))
        for sx in (-1, 1):
            for x, z, hgt in ((13, -38, 8), (7, -39, 6), (13, -30, 6)):
                m.box(jaw, (sx * x - 1.6, 12, z), (3.2, hgt, 3.2), {'all': 'tooth'})
    else:  # horned
        m.box(hr, (-14, 6, -16), (28, 20, 28), skin)                               # skull
        m.box(hr, (-8, 10, -47), (16, 9, 31), dict(skin, down='mouth'))            # long snout
        m.box(hr, (-6, 18, -47), (12, 3, 8), {'all': 'top'})                        # nose bump
        for i, z in enumerate(range(-12, 10, 6)):
            m.box(hr, (-4, 25.5, z), (8, 2, 5), {'all': 'plate'})                   # head scutes
        for sx in (-1, 1):
            m.box(hr, (sx * 12 - 3, 23, -15), (6, 5, 7), {'all': 'top'})           # eye bumps
            m.box(hr, (sx * 15.05 - 0.4 * (sx < 0), 23.5, -14), (0.4, 3.5, 5), {'east': 'eye', 'west': 'eye', 'all': 'dark'})
            for z in range(-46, -16, 3):
                m.box(hr, (sx * 7.3 - 0.9, 7.5, z), (1.8, 2.8, 1.8), {'all': 'tooth'})
            n = 'l' if sx > 0 else 'r'
            h1 = m.bone('horn_' + n, hr, pivot=(sx * 11, 24, 2))
            m.box(h1, (sx * 11 - 3.5, 24, -2), (7, 9, 8), {'all': 'horn'}, rot=(-35, 0, sx * -30), pivot=(sx * 11, 24, 2))
            h2 = m.bone('horn2_' + n, h1, pivot=(sx * 16, 31, 6))
            m.box(h2, (sx * 16 - 2.75, 29, 4), (5.5, 5.5, 10), {'all': 'horn'}, rot=(-10, sx * 20, sx * -20), pivot=(sx * 16, 31, 6))
            h3 = m.bone('horn3_' + n, h2, pivot=(sx * 19, 31, 14))
            m.box(h3, (sx * 19 - 2, 27, 12), (4, 4, 9), {'all': 'horn'}, rot=(40, sx * 25, 0), pivot=(sx * 19, 31, 14))
        jaw = m.bone('jaw', hr, pivot=(0, 10, -12))
        m.box(jaw, (-8, 4, -47), (16, 6, 36), dict(skin, up='mouth'))
        for sx in (-1, 1):
            for z in range(-45, -15, 3):
                m.box(jaw, (sx * 6.6 - 0.8, 10, z + 1.5), (1.6, 2.6, 1.6), {'all': 'tooth'})
    return m


def sea_king_anims():
    a = Anims('sea_king')
    L = 2.0
    a.add('swim', L, {
        'jaw': {'rotation': lambda t: (3 + 3 * wave(t, L), 0, 0)},
        'fin_l': {'rotation': lambda t: (0, 10 * wave(t, L, 2), 0)},
        'fin_r': {'rotation': lambda t: (0, -10 * wave(t, L, 2), 0)},
        'pec_l': {'rotation': lambda t: (0, 0, 16 * wave(t, L, 1, 0.1))},
        'pec_r': {'rotation': lambda t: (0, 0, -16 * wave(t, L, 1, 0.1))},
        'mid_l': {'rotation': lambda t: (0, 0, 12 * wave(t, L, 1, 0.4))},
        'mid_r': {'rotation': lambda t: (0, 0, -12 * wave(t, L, 1, 0.4))},
        'tailfin': {'rotation': lambda t: (0, 18 * wave(t, L, 1, 0.25), 0)},
        'barbel_l': {'rotation': lambda t: (8 * wave(t, L, 1), 0, 4 * wave(t, L, 1, 0.3))},
        'barbel_r': {'rotation': lambda t: (8 * wave(t, L, 1, 0.5), 0, -4 * wave(t, L, 1, 0.3))},
    })
    L = 1.2
    a.add('roar', L, {
        'jaw': {'rotation': lambda t: (36 + 5 * wave(t, L, 3), 0, 0)},
        'fin_l': {'rotation': lambda t: (0, 26 + 4 * wave(t, L, 6), 0)},
        'fin_r': {'rotation': lambda t: (0, -26 - 4 * wave(t, L, 6), 0)},
        'pec_l': {'rotation': lambda t: (0, 0, -18)},
        'pec_r': {'rotation': lambda t: (0, 0, 18)},
        'tailfin': {'rotation': lambda t: (0, 24 * wave(t, L, 2), 0)},
        'barbel_l': {'rotation': lambda t: (-20, 0, 10 * wave(t, L, 3))},
        'barbel_r': {'rotation': lambda t: (-20, 0, -10 * wave(t, L, 3))},
    })
    L = 0.6

    def bite(t):
        if t < 0.3:
            return (52 * smooth(0, 0.3, t), 0, 0)
        if t < 0.4:
            return (52 * (1 - smooth(0.3, 0.4, t)), 0, 0)
        return (0, 0, 0)
    a.add('bite', L, {'jaw': {'rotation': bite}, 'fin_l': {'rotation': lambda t: (0, 30, 0)}, 'fin_r': {'rotation': lambda t: (0, -30, 0)}})
    a.add('faint', 1.0, {
        'jaw': {'rotation': lambda t: (24, 0, 0)},
        'fin_l': {'rotation': lambda t: (0, -10, 0)}, 'fin_r': {'rotation': lambda t: (0, 10, 0)},
        'pec_l': {'rotation': lambda t: (0, 0, 30)}, 'pec_r': {'rotation': lambda t: (0, 0, -30)},
        'barbel_l': {'rotation': lambda t: (60, 0, 0)}, 'barbel_r': {'rotation': lambda t: (60, 0, 0)},
    })
    return a


def gen_sea_kings():
    for v in SK_PALETTES:
        out(sea_king(v), 'sea_king', v)
    sea_king_anims().write(f'{A}/animations/sea_king/sea_king.anim.json')


if __name__ == '__main__':
    which = sys.argv[1:] or ['sea_king', 'zoan', 'beast']
    if 'sea_king' in which:
        gen_sea_kings()
        print('sea kings done')
    if 'zoan' in which:
        import gen_beasts
        gen_beasts.gen_zoans(out)
        print('zoan forms done')
    if 'beast' in which:
        import gen_beasts
        gen_beasts.gen_wildlife(out)
        print('wildlife done')
