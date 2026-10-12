"""Zoan full-beast models and island wildlife for gen_creatures.py (see creaturelib for conventions).
Every animation keys the 'head' bone, because the client adds the look direction on top of the keyframed head."""
import math
from creaturelib import *  # noqa: F401,F403

Z = lambda t: (0, 0, 0)  # noqa: E731


def const(v):
    return lambda t: v


# ------------------------------------------------------------------ shared materials & parts

def basic_mats(m, back, belly, pattern=None, eye=0xF0B030, slit=False, side_pow=1.4, size=32, mouth=0x6A2A30, claw=(0x2A2420, 0x8A8070)):
    m.mat('fur', size, size, p_gradient(back, belly, pattern, power=side_pow, seed=3), vfull=True)
    m.mat('top', size, size, p_flat(back, pattern, seed=4))
    m.mat('belly', size, size, p_flat(belly, pattern or pat_fur(), seed=5))
    m.mat('eye', 4, 4, p_eye(eye, slit=slit), fit=True)
    m.mat('nose', 4, 4, p_solid(0x1A1414), fit=True)
    m.mat('tooth', 4, 4, p_teeth(), fit=True)
    m.mat('mouth', 8, 8, p_flat(mouth, pat_mottle(3, 0.2)), fit=True)
    m.mat('claw', 4, 8, p_horn(claw[0], claw[1], rings=2), fit=True)


SKIN = {'side': 'fur', 'up': 'top', 'down': 'belly'}


def leg(m, parent, name, pivot, w, d, length, paw=None, mats=SKIN, paw_mat=None, claws=True):
    x, y, z = pivot
    b = m.bone(name, parent, pivot=pivot)
    m.box(b, (x - w / 2, y - length, z - d / 2), (w, length, d), mats)
    if paw:
        pw, ph, pd = paw
        m.box(b, (x - pw / 2, y - length, z - d / 2 - (pd - d)), (pw, ph, pd), paw_mat or mats)
        if claws:
            for i in (-1, 0, 1):
                m.box(b, (x + i * pw / 3.2 - 0.3, y - length, z - d / 2 - (pd - d) - 0.6), (0.6, 0.6, 0.8), {'all': 'claw'})
    return b


def eyes_front(m, bone, cx, y, z, gap, size=(1.2, 1.0)):
    for sx in (-1, 1):
        m.box(bone, (cx + sx * gap - size[0] / 2, y, z - 0.3), (size[0], size[1], 0.4), {'all': 'eye'})


def eyes_side(m, bone, halfw, y, z, size=(1.0, 1.2)):
    for sx in (-1, 1):
        x0 = halfw if sx > 0 else -halfw - 0.4
        m.box(bone, (x0, y, z), (0.4, size[1], size[0]), {'all': 'eye'})


# ------------------------------------------------------------------ animation archetypes

def quad_anims(name, legs=('leg_fl', 'leg_fr', 'leg_bl', 'leg_br'), tail=('tail',), ears=(), jaw='jaw', swing=30, walk_len=0.8, extra=None):
    a = Anims(name)
    fl, fr, bl, br = legs
    L = 3.0
    idle = {'body': {'position': lambda t: (0, 0.25 * wave(t, L, 1), 0)},
            'head': {'rotation': lambda t: (3 * wave(t, L, 1, 0.2), 6 * wave(t, L, 1), 0)},
            jaw: {'rotation': lambda t: (2 + 2 * wave(t, L, 2), 0, 0)}}
    for i, tb in enumerate(tail):
        idle[tb] = {'rotation': lambda t, i=i: (3 * wave(t, L, 1, 0.1 * i), 12 * wave(t, L, 2, 0.12 * i), 0)}
    for e in ears:
        idle[e] = {'rotation': lambda t, e=e: (0, 0, 6 * max(0, wave(t, L, 1, 0.3)) ** 6 * (1 if e.endswith('l') else -1))}
    for lg in legs:
        idle[lg] = {'rotation': Z}
    a.add('idle', L, idle)
    L = walk_len
    walk = {'body': {'position': lambda t: (0, 0.5 * abs(wave(t, L, 1)), 0), 'rotation': lambda t: (1.5 * wave(t, L, 2), 0, 2 * wave(t, L, 1))},
            'head': {'rotation': lambda t: (4 * wave(t, L, 2, 0.1), 0, 0)},
            jaw: {'rotation': const((3, 0, 0))},
            fl: {'rotation': lambda t: (swing * wave(t, L), 0, 0)},
            br: {'rotation': lambda t: (swing * wave(t, L, 1, 0.05), 0, 0)},
            fr: {'rotation': lambda t: (-swing * wave(t, L), 0, 0)},
            bl: {'rotation': lambda t: (-swing * wave(t, L, 1, 0.05), 0, 0)}}
    for i, tb in enumerate(tail):
        walk[tb] = {'rotation': lambda t, i=i: (6 * wave(t, L, 2, 0.1 * i), 14 * wave(t, L, 1, 0.15 * i), 0)}
    a.add('walk', L, walk)
    L = 0.5

    def lunge(t):
        k = smooth(0, 0.18, t) * (1 - smooth(0.3, 0.5, t))
        return (-12 + 34 * k, 0, 0) if t > 0.0 else (0, 0, 0)
    attack = {'head': {'rotation': lambda t: (-14 * smooth(0, 0.15, t) * (1 - smooth(0.15, 0.25, t)) + 26 * smooth(0.15, 0.25, t) * (1 - smooth(0.3, 0.5, t)), 0, 0),
                       'position': lambda t: (0, 0, -3 * smooth(0.1, 0.22, t) * (1 - smooth(0.3, 0.5, t)))},
              jaw: {'rotation': lambda t: (35 * smooth(0.05, 0.17, t) * (1 - smooth(0.2, 0.28, t)), 0, 0)},
              'body': {'rotation': lambda t: (6 * smooth(0.1, 0.22, t) * (1 - smooth(0.3, 0.5, t)), 0, 0), 'position': lambda t: (0, 0, -1.5 * smooth(0.1, 0.22, t) * (1 - smooth(0.3, 0.5, t)))},
              fl: {'rotation': lambda t: (-55 * smooth(0.0, 0.15, t) * (1 - smooth(0.2, 0.4, t)), 0, 0)},
              fr: {'rotation': lambda t: (-40 * smooth(0.05, 0.2, t) * (1 - smooth(0.25, 0.45, t)), 0, 0)},
              bl: {'rotation': Z}, br: {'rotation': Z}}
    a.add('attack', L, attack)
    L = 0.6
    fly = {'body': {'rotation': lambda t: (-8 + 2 * wave(t, L), 0, 0)},
           'head': {'rotation': const((-6, 0, 0))}, jaw: {'rotation': const((6, 0, 0))},
           fl: {'rotation': lambda t: (-50 + 6 * wave(t, L), 0, 0)}, fr: {'rotation': lambda t: (-45 + 6 * wave(t, L, 1, 0.1), 0, 0)},
           bl: {'rotation': lambda t: (50 + 6 * wave(t, L), 0, 0)}, br: {'rotation': lambda t: (45 + 6 * wave(t, L, 1, 0.1), 0, 0)}}
    for tb in tail:
        fly[tb] = {'rotation': lambda t: (18, 8 * wave(t, L), 0)}
    a.add('fly', L, fly)
    if extra:
        extra(a)
    return a


def biped_anims(name, tail=('tail1', 'tail2', 'tail3'), arms=('arm_l', 'arm_r'), sail=None):
    a = Anims(name)
    L = 3.0
    idle = {'body': {'position': lambda t: (0, 0.3 * wave(t, L), 0), 'rotation': lambda t: (1.5 * wave(t, L), 0, 0)},
            'neck': {'rotation': lambda t: (2 * wave(t, L, 1, 0.3), 4 * wave(t, L, 1), 0)},
            'head': {'rotation': lambda t: (3 * wave(t, L, 2), 6 * wave(t, L, 1, 0.2), 0)},
            'jaw': {'rotation': lambda t: (3 + 3 * wave(t, L, 1), 0, 0)},
            'thigh_l': {'rotation': Z}, 'thigh_r': {'rotation': Z}, 'shin_l': {'rotation': Z}, 'shin_r': {'rotation': Z}}
    for i, tb in enumerate(tail):
        idle[tb] = {'rotation': lambda t, i=i: (2 * wave(t, L, 1, 0.1 * i), 7 * wave(t, L, 1, 0.12 * i), 0)}
    for ar in arms:
        idle[ar] = {'rotation': lambda t: (6 * wave(t, L, 1), 0, 0)}
    a.add('idle', L, idle)
    L = 1.1
    walk = {'body': {'position': lambda t: (0, -0.8 * abs(wave(t, L)), 0), 'rotation': lambda t: (2 * wave(t, L, 2), 3 * wave(t, L), 2 * wave(t, L))},
            'neck': {'rotation': lambda t: (3 * wave(t, L, 2, 0.15), -3 * wave(t, L), 0)},
            'head': {'rotation': lambda t: (4 * wave(t, L, 2, 0.25), 0, 0)},
            'jaw': {'rotation': const((4, 0, 0))},
            'thigh_l': {'rotation': lambda t: (-28 * wave(t, L), 0, 0)}, 'thigh_r': {'rotation': lambda t: (28 * wave(t, L), 0, 0)},
            'shin_l': {'rotation': lambda t: (18 * max(0, wave(t, L, 1, 0.2)), 0, 0)}, 'shin_r': {'rotation': lambda t: (18 * max(0, wave(t, L, 1, 0.7)), 0, 0)}}
    for i, tb in enumerate(tail):
        walk[tb] = {'rotation': lambda t, i=i: (3 * wave(t, L, 2, 0.1 * i), 10 * wave(t, L, 1, 0.15 + 0.12 * i), 0)}
    for ar in arms:
        walk[ar] = {'rotation': lambda t: (10 * wave(t, L, 2), 0, 0)}
    a.add('walk', L, walk)
    L = 0.6
    attack = {'body': {'rotation': lambda t: (10 * smooth(0.05, 0.2, t) * (1 - smooth(0.3, 0.6, t)), 0, 0)},
              'neck': {'rotation': lambda t: (-10 * smooth(0, 0.12, t) * (1 - smooth(0.12, 0.22, t)) + 22 * smooth(0.12, 0.24, t) * (1 - smooth(0.32, 0.6, t)), 0, 0)},
              'head': {'rotation': lambda t: (-18 * smooth(0, 0.12, t) * (1 - smooth(0.14, 0.24, t)) + 20 * smooth(0.14, 0.26, t) * (1 - smooth(0.32, 0.6, t)), 0, 0)},
              'jaw': {'rotation': lambda t: (42 * smooth(0.02, 0.15, t) * (1 - smooth(0.2, 0.27, t)), 0, 0)},
              'thigh_l': {'rotation': const((-8, 0, 0))}, 'thigh_r': {'rotation': const((10, 0, 0))}, 'shin_l': {'rotation': Z}, 'shin_r': {'rotation': Z}}
    for ar in arms:
        attack[ar] = {'rotation': lambda t: (-50 * smooth(0, 0.2, t) * (1 - smooth(0.3, 0.6, t)), 0, 0)}
    for i, tb in enumerate(tail):
        attack[tb] = {'rotation': lambda t, i=i: (-6 * smooth(0, 0.2, t) * (1 - smooth(0.3, 0.6, t)), 0, 0)}
    a.add('attack', L, attack)
    L = 0.6
    fly = {'body': {'rotation': const((-6, 0, 0))}, 'neck': {'rotation': const((-8, 0, 0))}, 'head': {'rotation': const((-4, 0, 0))},
           'jaw': {'rotation': const((10, 0, 0))},
           'thigh_l': {'rotation': lambda t: (-35 + 4 * wave(t, L), 0, 0)}, 'thigh_r': {'rotation': lambda t: (-20 + 4 * wave(t, L, 1, 0.3), 0, 0)},
           'shin_l': {'rotation': const((40, 0, 0))}, 'shin_r': {'rotation': const((30, 0, 0))}}
    for i, tb in enumerate(tail):
        fly[tb] = {'rotation': lambda t, i=i: (-6, 5 * wave(t, L, 1, 0.1 * i), 0)}
    for ar in arms:
        fly[ar] = {'rotation': const((-40, 0, 0))}
    a.add('fly', L, fly)
    return a


def bird_anims(name, legs=('leg_l', 'leg_r'), tails=('tail',), crest=None, walk_on_wings=False):
    a = Anims(name)
    fold_l, fold_r = (0, -78, 18), (0, 78, -18)
    fold2_l, fold2_r = (0, -150, 0), (0, 150, 0)
    L = 3.0
    idle = {'body': {'position': lambda t: (0, 0.2 * wave(t, L), 0)},
            'head': {'rotation': lambda t: (4 * wave(t, L, 2), 12 * wave(t, L, 1), 0)},
            'wing_l': {'rotation': lambda t: (fold_l[0], fold_l[1], fold_l[2] + 2 * wave(t, L))}, 'wing_r': {'rotation': lambda t: (fold_r[0], fold_r[1], fold_r[2] - 2 * wave(t, L))},
            'wing_l2': {'rotation': const(fold2_l)}, 'wing_r2': {'rotation': const(fold2_r)}}
    for lg in legs:
        idle[lg] = {'rotation': Z}
    for i, tb in enumerate(tails):
        idle[tb] = {'rotation': lambda t, i=i: (4 * wave(t, L, 1, 0.1 * i), 6 * wave(t, L, 1, 0.2 + 0.1 * i), 0)}
    a.add('idle', L, idle)
    L = 0.7
    walk = {'body': {'position': lambda t: (0, 0.5 * abs(wave(t, L)), 0), 'rotation': lambda t: (0, 0, 4 * wave(t, L))},
            'head': {'rotation': lambda t: (6 * wave(t, L, 2), 0, 0), 'position': lambda t: (0, 0, -1.0 * wave(t, L, 2))},
            'wing_l': {'rotation': const(fold_l)}, 'wing_r': {'rotation': const(fold_r)},
            'wing_l2': {'rotation': const(fold2_l)}, 'wing_r2': {'rotation': const(fold2_r)},
            legs[0]: {'rotation': lambda t: (30 * wave(t, L), 0, 0)}, legs[1]: {'rotation': lambda t: (-30 * wave(t, L), 0, 0)}}
    if walk_on_wings:
        walk['wing_l'] = {'rotation': lambda t: (fold_l[0] - 20 * wave(t, L), fold_l[1], fold_l[2] + 30)}
        walk['wing_r'] = {'rotation': lambda t: (fold_r[0] + 20 * wave(t, L), fold_r[1], fold_r[2] - 30)}
    for i, tb in enumerate(tails):
        walk[tb] = {'rotation': lambda t, i=i: (5 * wave(t, L, 2, 0.1 * i), 6 * wave(t, L, 1), 0)}
    a.add('walk', L, walk)
    L = 0.8
    fly = {'body': {'position': lambda t: (0, 1.2 * wave(t, L, 1, 0.25), 0), 'rotation': lambda t: (8 + 3 * wave(t, L, 1, 0.3), 0, 0)},
           'head': {'rotation': lambda t: (-10 + 3 * wave(t, L, 1, 0.4), 0, 0)},
           'wing_l': {'rotation': lambda t: (0, -6, 42 * wave(t, L))}, 'wing_r': {'rotation': lambda t: (0, 6, -42 * wave(t, L))},
           'wing_l2': {'rotation': lambda t: (0, 0, 22 * wave(t, L, 1, -0.12))}, 'wing_r2': {'rotation': lambda t: (0, 0, -22 * wave(t, L, 1, -0.12))}}
    for lg in legs:
        fly[lg] = {'rotation': const((60, 0, 0))}
    for i, tb in enumerate(tails):
        fly[tb] = {'rotation': lambda t, i=i: (-6 + 6 * wave(t, L, 1, 0.3 + 0.1 * i), 4 * wave(t, L, 1, 0.1 * i), 0)}
    a.add('fly', L, fly)
    L = 0.6
    attack = {'body': {'rotation': lambda t: (14 * smooth(0, 0.2, t) * (1 - smooth(0.3, 0.6, t)), 0, 0)},
              'head': {'rotation': lambda t: (-20 * smooth(0, 0.12, t) * (1 - smooth(0.12, 0.22, t)) + 35 * smooth(0.12, 0.24, t) * (1 - smooth(0.32, 0.6, t)), 0, 0)},
              'wing_l': {'rotation': lambda t: (0, -30 + 40 * smooth(0.0, 0.25, t) * (1 - smooth(0.3, 0.6, t)), -40 * smooth(0.0, 0.2, t) * (1 - smooth(0.35, 0.6, t)))},
              'wing_r': {'rotation': lambda t: (0, 30 - 40 * smooth(0.0, 0.25, t) * (1 - smooth(0.3, 0.6, t)), 40 * smooth(0.0, 0.2, t) * (1 - smooth(0.35, 0.6, t)))},
              'wing_l2': {'rotation': Z}, 'wing_r2': {'rotation': Z},
              legs[0]: {'rotation': lambda t: (-40 * smooth(0.1, 0.25, t) * (1 - smooth(0.3, 0.5, t)), 0, 0)}, legs[1]: {'rotation': Z}}
    a.add('attack', L, attack)
    return a


# ======================================================================================== ZOAN FORMS

def wolf():
    m = Model('wolf', 128, 64)
    basic_mats(m, 0x5C5E66, 0xD6D6DA, pat_fur(4, 0.25), eye=0xF0B030)
    m.mat('muzzle', 16, 16, p_flat(0xC8C8CC, pat_fur(3, 0.15)))
    m.mat('ear', 8, 8, p_gradient(0x4A4C52, 0xC08A8A, power=1.5))
    m.mat('dark', 16, 16, p_flat(0x3A3C42, pat_fur(3, 0.2)))
    body = m.bone('body', pivot=(0, 11, 0))
    m.box(body, (-4, 7, -8), (8, 7, 16), SKIN)
    m.box(body, (-4.75, 6.4, -9.8), (9.5, 8.6, 7.5), SKIN)                    # ruff
    m.box(body, (-1.5, 13.6, -6), (3, 1, 13), {'all': 'dark'})                 # dark saddle stripe
    head = m.bone('head', body, pivot=(0, 12.5, -9))
    m.box(head, (-3.5, 10, -15), (7, 6, 6), SKIN)
    m.box(head, (-2, 10, -19), (4, 3, 4), {'side': 'muzzle', 'up': 'top', 'down': 'muzzle'})
    m.box(head, (-0.75, 11.9, -19.25), (1.5, 1, 0.3), {'all': 'nose'})
    eyes_front(m, head, 0, 13.6, -15, 1.8)
    for sx in (-1, 1):
        m.box(head, (sx * 2.2 - 1, 16, -12.8), (2, 3, 1), {'north': 'ear', 'all': 'top'}, rot=(0, 0, -sx * 10), pivot=(sx * 2.2, 16, -12.3))
    jaw = m.bone('jaw', head, pivot=(0, 10.5, -14))
    m.box(jaw, (-1.75, 9, -18.6), (3.5, 1.5, 4.2), {'all': 'muzzle', 'up': 'mouth'})
    for sx in (-1, 1):
        m.box(head, (sx * 1.2 - 0.3, 9.4, -18.6), (0.6, 0.8, 0.6), {'all': 'tooth'})
    tail = m.bone('tail', body, pivot=(0, 13, 8), rot=(-38, 0, 0))
    m.box(tail, (-1.25, 11.75, 8), (2.5, 2.5, 9), SKIN, inflate=0.2)
    m.box(tail, (-1, 12, 15), (2, 2, 2.5), {'all': 'belly'}, inflate=0.3)
    for name, x, z in (('leg_fl', 2.4, -6), ('leg_fr', -2.4, -6), ('leg_bl', 2.4, 5.5), ('leg_br', -2.4, 5.5)):
        leg(m, None, name, (x, 8, z), 2.7, 2.8, 8, paw=(3.0, 1.2, 3.4))
    return m, quad_anims('wolf', ears=())


def leopard():
    m = Model('leopard', 128, 64)
    base = p_gradient(0xD9A040, 0xF6E9C8, pat_fur(3, 0.12), power=1.8)
    ros = p_spots(base, 0xB06A1A, 6, 0.32, ring=0x2A1A0E, seed=41)
    m.mat('fur', 32, 32, ros, vfull=True)
    m.mat('top', 32, 32, p_spots(p_flat(0xD49A38, pat_fur(3, 0.12)), 0xB06A1A, 6, 0.34, ring=0x2A1A0E, seed=42))
    m.mat('belly', 32, 32, p_spots(p_flat(0xF6EAD0, pat_fur(3, 0.08)), 0x2A1A0E, 8, 0.2, seed=43))
    m.mat('eye', 4, 4, p_eye(0x9ADA3A, slit=True), fit=True)
    m.mat('nose', 4, 4, p_solid(0x6A3A3A), fit=True)
    m.mat('tooth', 4, 4, p_teeth(), fit=True)
    m.mat('mouth', 8, 8, p_flat(0x7A2A34), fit=True)
    m.mat('claw', 4, 8, p_horn(0x2A2420, 0xC8C0B0), fit=True)
    m.mat('muzzle', 16, 16, p_flat(0xF2E4C4, pat_fur(3, 0.1)))
    m.mat('tailtip', 8, 8, p_flat(0x221810), fit=True)
    body = m.bone('body', pivot=(0, 10, 0))
    m.box(body, (-3.5, 7, -9), (7, 6, 18), SKIN)
    m.box(body, (-3.8, 6.6, -10), (7.6, 6.8, 6), SKIN)
    head = m.bone('head', body, pivot=(0, 11.5, -10))
    m.box(head, (-3, 9.5, -15), (6, 5, 5.2), SKIN)
    m.box(head, (-1.75, 9.5, -17.4), (3.5, 2.6, 2.6), {'side': 'muzzle', 'up': 'top', 'down': 'muzzle'})
    m.box(head, (-0.6, 11.6, -17.6), (1.2, 0.6, 0.4), {'all': 'nose'})
    eyes_front(m, head, 0, 12.6, -15, 1.5, (1.1, 0.9))
    for sx in (-1, 1):
        m.box(head, (sx * 2.1 - 1, 14.5, -12.5), (2, 1.6, 1), {'all': 'top'})
    jaw = m.bone('jaw', head, pivot=(0, 10, -14))
    m.box(jaw, (-1.5, 8.6, -17.2), (3, 1, 3), {'all': 'muzzle', 'up': 'mouth'})
    for sx in (-1, 1):
        m.box(head, (sx * 1 - 0.25, 8.8, -17.2), (0.5, 0.9, 0.5), {'all': 'tooth'})
    tail = m.bone('tail', body, pivot=(0, 12, 9), rot=(-25, 0, 0))
    m.box(tail, (-0.9, 11.1, 9), (1.8, 1.8, 9), SKIN)
    t2 = m.bone('tail2', tail, pivot=(0, 12, 18), rot=(30, 0, 0))
    m.box(t2, (-0.8, 11.2, 18), (1.6, 1.6, 7), SKIN)
    m.box(t2, (-0.85, 11.15, 24), (1.7, 1.7, 1.5), {'all': 'tailtip'})
    for name, x, z in (('leg_fl', 2.1, -6.5), ('leg_fr', -2.1, -6.5), ('leg_bl', 2.1, 6.5), ('leg_br', -2.1, 6.5)):
        leg(m, None, name, (x, 7.5, z), 2.0, 2.2, 7.5, paw=(2.4, 1.0, 2.8))
    return m, quad_anims('leopard', tail=('tail', 'tail2'), swing=34, walk_len=0.7)


def bison():
    m = Model('bison', 128, 128)
    basic_mats(m, 0x4A3020, 0x6E4E34, pat_fur(4, 0.22), eye=0x2A1A10)
    m.mat('shag', 32, 32, p_gradient(0x3A2416, 0x2A1A10, pat_combine(pat_fur(5, 0.35, 8), pat_mottle(5, 0.15)), power=1.0), vfull=True)
    m.mat('muzzle', 16, 16, p_flat(0x2A2220, pat_mottle(3, 0.15)))
    m.mat('horn', 8, 16, p_horn(0xE8E0C8, 0x2A2420, rings=3), vfull=True)
    m.mat('hoof', 8, 8, p_flat(0x1E1A18), fit=True)
    shag = {'side': 'shag', 'up': 'shag', 'down': 'shag'}
    body = m.bone('body', pivot=(0, 14, 0))
    m.box(body, (-6, 9, -9), (12, 10, 21), SKIN)
    m.box(body, (-6.6, 12, -12), (13.2, 12, 11), shag, inflate=0.3)            # hump
    m.box(body, (-7, 6, -13), (14, 10, 6), shag, inflate=0.4)                   # chest shag
    head = m.bone('head', body, pivot=(0, 13, -13))
    m.box(head, (-4.5, 6, -20), (9, 9, 8), shag, inflate=0.3)
    m.box(head, (-3.5, 5.5, -22.5), (7, 5, 3), {'all': 'muzzle'})
    m.box(head, (-2, 2.5, -20), (4, 4, 4), shag)                                 # beard
    eyes_front(m, head, 0, 11, -20.4, 2.8, (1.2, 1.0))
    for sx in (-1, 1):
        m.box(head, (sx * 4.5 + (0 if sx > 0 else -3), 12, -17), (3, 2, 2), {'all': 'horn'})
        m.box(head, (sx * 7 - 1, 12.5, -17), (2, 5, 2), {'all': 'horn'}, rot=(0, 0, -sx * 20), pivot=(sx * 7, 12.5, -16))
    jaw = m.bone('jaw', head, pivot=(0, 6, -19))
    m.box(jaw, (-2.8, 4.5, -22), (5.6, 1.5, 3), {'all': 'muzzle', 'up': 'mouth'})
    tail = m.bone('tail', body, pivot=(0, 18, 12))
    m.box(tail, (-0.6, 10, 11.8), (1.2, 8, 1.2), SKIN)
    m.box(tail, (-1, 8, 11.4), (2, 3, 2), shag)
    for name, x, z in (('leg_fl', 4.3, -8), ('leg_fr', -4.3, -8), ('leg_bl', 4.3, 8), ('leg_br', -4.3, 8)):
        b = leg(m, None, name, (x, 9, z), 3.4, 3.6, 9, paw=(3.6, 1.6, 3.8), paw_mat={'all': 'hoof'}, claws=False)
        if 'f' in name[-2:-1]:
            m.box(b, (x - 2.1, 4, z - 2.1), (4.2, 5, 4.2), shag, inflate=0.2)
    return m, quad_anims('bison', swing=24, walk_len=0.95)


def elephant():
    m = Model('elephant', 128, 128)
    skin = p_flat(0x8C8C94, pat_combine(pat_wrinkles(3, 0.2), pat_mottle(6, 0.1)))
    m.mat('fur', 32, 32, p_gradient(0x8C8C94, 0x6E6E76, pat_combine(pat_wrinkles(3, 0.22), pat_mottle(6, 0.1)), power=1.2), vfull=True)
    m.mat('top', 32, 32, skin)
    m.mat('belly', 32, 32, p_flat(0x6A6A72, pat_wrinkles(3, 0.25)))
    m.mat('ear', 32, 32, p_gradient(0xB08C8C, 0x8C7070, pat_combine(pat_wrinkles(4, 0.12), pat_mottle(6, 0.12)), power=1.0))
    m.mat('eye', 4, 4, p_eye(0x5A3A1A, slit=False), fit=True)
    m.mat('tusk', 8, 16, p_horn(0xF4EEDC, 0xD8CCB0, rings=2), vfull=True)
    m.mat('nail', 4, 4, p_flat(0xD8D0C0), fit=True)
    m.mat('mouth', 8, 8, p_flat(0x7A4A50), fit=True)
    m.mat('claw', 4, 8, p_flat(0x3A3A3A), fit=True)
    body = m.bone('body', pivot=(0, 18, 0))
    m.box(body, (-11, 10, -13), (22, 18, 28), SKIN)
    m.box(body, (-10, 26, -12), (20, 3, 22), {'all': 'top'})
    head = m.bone('head', body, pivot=(0, 24, -13))
    m.box(head, (-7, 17, -22), (14, 15, 10), SKIN)
    m.box(head, (-6, 30, -21), (12, 3, 8), {'all': 'top'})
    eyes_side(m, head, 7, 24, -19, (1.6, 1.4))
    for sx, n in ((1, 'ear_l'), (-1, 'ear_r')):
        eb = m.bone(n, head, pivot=(sx * 7, 28, -17))
        m.box(eb, (sx * 7 + (0 if sx > 0 else -1.4), 15, -18), (1.4, 15, 12), {'east': 'ear', 'west': 'ear', 'all': 'fur'}, rot=(0, sx * 20, 0), pivot=(sx * 7, 28, -17))
        m.box(head, (sx * 3.5 - 1, 15.5, -25), (2, 2, 9), {'all': 'tusk'}, rot=(28, sx * -12, 0), pivot=(sx * 3.5, 16.5, -22))
    t1 = m.bone('trunk1', head, pivot=(0, 21, -23))
    m.box(t1, (-2.6, 13, -25.6), (5.2, 9, 5.2), SKIN)
    t2 = m.bone('trunk2', t1, pivot=(0, 13, -23.2), rot=(-8, 0, 0))
    m.box(t2, (-2.1, 7, -25.2), (4.2, 6.5, 4.2), SKIN)
    t3 = m.bone('trunk3', t2, pivot=(0, 7, -23.2), rot=(-22, 0, 0))
    m.box(t3, (-1.6, 2.5, -24.8), (3.2, 5, 3.2), SKIN)
    jaw = m.bone('jaw', head, pivot=(0, 17, -20))
    m.box(jaw, (-3, 15, -22), (6, 2.5, 5), {'all': 'mouth'})
    tail = m.bone('tail', body, pivot=(0, 25, 15))
    m.box(tail, (-0.6, 14, 14.8), (1.2, 11, 1.2), SKIN)
    for name, x, z in (('leg_fl', 6.5, -8.5), ('leg_fr', -6.5, -8.5), ('leg_bl', 6.5, 9), ('leg_br', -6.5, 9)):
        b = leg(m, None, name, (x, 11, z), 6, 6, 11, claws=False)
        for i in (-1, 0, 1):
            m.box(b, (x + i * 1.8 - 0.6, 0, z - 3.2), (1.2, 1.2, 0.4), {'all': 'nail'})

    def extra(a):
        for nm in ('idle', 'walk', 'attack', 'fly'):
            an = a.anims['animation.elephant.' + nm]
            L = an['animation_length']
            spd = {'idle': 1, 'walk': 2, 'attack': 1, 'fly': 1}[nm]
            an['bones']['ear_l'] = {'rotation': frames(lambda t: (0, -12 * max(0, wave(t, L, spd)), 0), L)}
            an['bones']['ear_r'] = {'rotation': frames(lambda t: (0, 12 * max(0, wave(t, L, spd)), 0), L)}
            if nm == 'attack':
                an['bones']['trunk1'] = {'rotation': frames(lambda t: (-80 * smooth(0, 0.2, t) * (1 - smooth(0.35, 0.5, t)), 0, 0), L)}
                an['bones']['trunk2'] = {'rotation': frames(lambda t: (-40 * smooth(0, 0.2, t) * (1 - smooth(0.35, 0.5, t)), 0, 0), L)}
                an['bones']['trunk3'] = {'rotation': frames(lambda t: (-30 * smooth(0, 0.2, t) * (1 - smooth(0.35, 0.5, t)), 0, 0), L)}
            else:
                amp = 10 if nm == 'walk' else 6
                an['bones']['trunk1'] = {'rotation': frames(lambda t: (amp * wave(t, L, spd), 5 * wave(t, L, spd, 0.25), 0), L)}
                an['bones']['trunk2'] = {'rotation': frames(lambda t: (amp * wave(t, L, spd, 0.1), 0, 0), L)}
                an['bones']['trunk3'] = {'rotation': frames(lambda t: (amp * 1.5 * wave(t, L, spd, 0.2), 0, 0), L)}
    return m, quad_anims('elephant', swing=20, walk_len=1.2, extra=extra)


def theropod(name, back, belly, stripe, eye, snout_len, sail=None, arm_len=7):
    m = Model(name, 128, 128)
    base = p_gradient(back, belly, pat_scales(4, 3, 0.25), power=1.6)
    m.mat('fur', 32, 32, p_stripes(base, stripe, 3, 0.22, seed=7, wobble=3), vfull=True)
    m.mat('top', 32, 32, p_stripes(p_flat(back, pat_scales(4, 3, 0.25)), stripe, 3, 0.3, seed=8, wobble=3))
    m.mat('belly', 32, 32, p_flat(belly, pat_plates(3, 0.2)))
    m.mat('eye', 4, 4, p_eye(eye, slit=True), fit=True)
    m.mat('tooth', 4, 4, p_teeth(), fit=True)
    m.mat('mouth', 16, 16, p_flat(0x7A2A30, pat_mottle(3, 0.2)))
    m.mat('claw', 4, 8, p_horn(0x2A2420, 0xB0A890), fit=True)
    m.mat('horn', 8, 8, p_horn(0xB86A3A, 0xE8B070), fit=True)
    if sail:
        m.mat('sail', 32, 32, p_stripes(p_gradient(sail[0], sail[1], pat_mottle(5, 0.12), power=0.8), 0x3A1410, 7, 0.08, seed=9, wobble=0.5))
    body = m.bone('body', pivot=(0, 22, 2), rot=(6, 0, 0))
    m.box(body, (-5.5, 17, -10), (11, 13, 22), SKIN)
    m.box(body, (-4.5, 16, -12), (9, 11, 6), SKIN)
    neck = m.bone('neck', body, pivot=(0, 26, -10), rot=(-14, 0, 0))
    m.box(neck, (-3.5, 23, -18), (7, 9, 10), SKIN)
    head = m.bone('head', neck, pivot=(0, 29, -16), rot=(12, 0, 0))
    m.box(head, (-4, 26.5, -22), (8, 7, 8), SKIN)
    sl = snout_len
    m.box(head, (-3.2 if sail else -3.6, 27, -22 - sl), (6.4 if sail else 7.2, 5 if sail else 6, sl), dict(SKIN, down='mouth'))
    for sx in (-1, 1):
        m.box(head, (sx * 4.02 - 0.4 * (sx < 0), 30.5, -21), (0.4, 1.2, 1.6), {'all': 'eye'})
        if not sail:
            m.box(head, (sx * 2.6 - 1, 33.3, -22), (2, 1.6, 3), {'all': 'horn'})
        for z in range(int(-21 - sl), -21, 2):
            m.box(head, (sx * (2.7 if sail else 3.1) - 0.4, 25.8, z), (0.8, 1.4, 0.8), {'all': 'tooth'})
    jaw = m.bone('jaw', head, pivot=(0, 27, -19))
    m.box(jaw, (-3 if sail else -3.3, 24, -21 - sl), (6 if sail else 6.6, 3, sl + 3), dict(SKIN, up='mouth'))
    for sx in (-1, 1):
        for z in range(int(-20 - sl), -20, 2):
            m.box(jaw, (sx * (2.4 if sail else 2.8) - 0.35, 27, z), (0.7, 1.2, 0.7), {'all': 'tooth'})
    if sail:
        sb = m.bone('sail', body, pivot=(0, 30, 0))
        for z0, z1, h in ((-9, -3, 11), (-3, 3, 16), (3, 9, 18), (9, 14, 12)):
            m.box(sb, (-0.7, 29, z0), (1.4, h, z1 - z0), {'all': 'sail'})
    for sx, n in ((1, 'arm_l'), (-1, 'arm_r')):
        ab = m.bone(n, body, pivot=(sx * 4.5, 22, -9), rot=(-30, 0, 0))
        m.box(ab, (sx * 4.5 - 1, 22 - arm_len, -10), (2, arm_len, 2), SKIN)
        for i in (-1, 1):
            m.box(ab, (sx * 4.5 + i * 0.5 - 0.3, 21.2 - arm_len, -10.6), (0.6, 1.4, 0.6), {'all': 'claw'})
    for sx, n in ((1, 'l'), (-1, 'r')):
        th = m.bone('thigh_' + n, None, pivot=(sx * 5, 21, 2))
        m.box(th, (sx * 5 - 2.6, 11, -2), (5.2, 11, 7.5), SKIN)
        sh = m.bone('shin_' + n, th, pivot=(sx * 5, 12, 2.5), rot=(10, 0, 0))
        m.box(sh, (sx * 5 - 1.6, 3, 1), (3.2, 9.5, 3.5), SKIN)
        ft = m.bone('foot_' + n, sh, pivot=(sx * 5, 3, 2.5), rot=(-10, 0, 0))
        m.box(ft, (sx * 5 - 2.2, 0, -3), (4.4, 3, 7), SKIN)
        for i in (-1, 0, 1):
            m.box(ft, (sx * 5 + i * 1.4 - 0.4, 0, -4.2), (0.8, 1.2, 1.4), {'all': 'claw'})
    t1 = m.bone('tail1', body, pivot=(0, 24, 12), rot=(-6, 0, 0))
    m.box(t1, (-4, 19.5, 11), (8, 8.5, 13), SKIN)
    t2 = m.bone('tail2', t1, pivot=(0, 24, 24))
    m.box(t2, (-2.8, 20.8, 23), (5.6, 6, 13), SKIN)
    t3 = m.bone('tail3', t2, pivot=(0, 24, 36), rot=(4, 0, 0))
    m.box(t3, (-1.6, 22, 35), (3.2, 3.6, 15), SKIN)
    return m, biped_anims(name)


def allosaurus():
    return theropod('allosaurus', 0x5E6C3C, 0xCFC290, 0x38441E, 0xF0C020, 10)


def spinosaurus():
    return theropod('spinosaurus', 0x3E5C5A, 0xCFC6A2, 0x24383A, 0xF07A20, 15, sail=(0xE8622A, 0x8A1E14), arm_len=9)


def pteranodon():
    m = Model('pteranodon', 128, 64)
    basic_mats(m, 0x7A5638, 0xDCCDB0, pat_fur(3, 0.1), eye=0xE0A020, slit=False)
    m.mat('wing', 32, 32, p_membrane(0xB08050, 0x5A3A22))
    m.mat('crest', 16, 16, p_gradient(0xE0602A, 0x8A2A18, pat_mottle(4, 0.1), power=1.0), vfull=True)
    m.mat('beak', 16, 16, p_gradient(0xE2C070, 0x9A7A3A, power=1.0), vfull=True)
    body = m.bone('body', pivot=(0, 11, 0), rot=(-10, 0, 0))
    m.box(body, (-3, 8, -5), (6, 5, 11), SKIN)
    m.box(body, (-2.4, 9, -9), (4.8, 4, 4.5), SKIN)
    head = m.bone('head', body, pivot=(0, 12.5, -9))
    m.box(head, (-2, 11, -13.5), (4, 4, 5), SKIN)
    m.box(head, (-1.3, 11.4, -24), (2.6, 2.6, 10.5), {'all': 'beak'})
    m.box(head, (-0.8, 11.6, -29), (1.6, 1.6, 5), {'all': 'beak'})
    m.box(head, (-0.6, 13.5, -10.5), (1.2, 5, 10), {'all': 'crest'}, rot=(-38, 0, 0), pivot=(0, 14, -11))
    for sx in (-1, 1):
        m.box(head, (sx * 2.02 - 0.4 * (sx < 0), 13, -12.5), (0.4, 1, 1.2), {'all': 'eye'})
    jaw = m.bone('jaw', head, pivot=(0, 11.5, -12))
    m.box(jaw, (-1.1, 10.4, -27), (2.2, 1.1, 14.5), {'all': 'beak'})
    for sx, n in ((1, 'l'), (-1, 'r')):
        w1 = m.bone('wing_' + n, body, pivot=(sx * 3, 12, -3))
        m.box(w1, (sx * 3 + (0 if sx > 0 else -14), 11.6, -7), (14, 0.8, 10), {'all': 'wing'})
        m.box(w1, (sx * 3 + (0 if sx > 0 else -14), 11.4, -7.5), (14, 1.2, 1.4), SKIN)
        w2 = m.bone('wing_' + n + '2', w1, pivot=(sx * 17, 12, -3))
        m.box(w2, (sx * 17 + (0 if sx > 0 else -17), 11.65, -6.5), (17, 0.7, 7), {'all': 'wing'})
        m.box(w2, (sx * 17 + (0 if sx > 0 else -17), 11.4, -7), (17, 1.1, 1.1), SKIN)
        lg = m.bone('leg_' + n, None, pivot=(sx * 1.6, 9, 4))
        m.box(lg, (sx * 1.6 - 0.6, 0.6, 3.4), (1.2, 8.6, 1.2), SKIN)
        m.box(lg, (sx * 1.6 - 1, 0, 2.2), (2, 0.6, 2.6), {'all': 'claw'})
    tail = m.bone('tail', body, pivot=(0, 10.5, 6))
    m.box(tail, (-0.5, 10, 6), (1, 1, 4), SKIN)
    return m, bird_anims('pteranodon', legs=('leg_l', 'leg_r'), walk_on_wings=True)


def phoenix():
    m = Model('phoenix', 128, 128)
    flame = (0xFFF09A, 0x7ADFFF, 0x2A8AFF, 0x1A4ACC)
    m.mat('fur', 32, 32, p_gradient(0x2A7AF0, 0x9AE8FF, pat_feathers(5, 4, 0.3), power=1.0), vfull=True, glow=True)
    m.mat('top', 32, 32, p_flat(0x2A6AE0, pat_feathers(5, 4, 0.3)), glow=True)
    m.mat('belly', 32, 32, p_flat(0xFFE48A, pat_feathers(4, 3, 0.2)), glow=True)
    m.mat('wing', 32, 32, p_flame(flame), glow=True)
    m.mat('plume', 16, 32, p_flame((0xFFF6B0, 0xFFD040, 0x5AD0FF, 0x2A70F0)), vfull=True, glow=True)
    m.mat('gold', 8, 8, p_gradient(0xFFE070, 0xC08A20, power=1.0), vfull=True)
    m.mat('eye', 4, 4, p_eye(0x101020, sclera=0xFFE070), fit=True)
    m.mat('claw', 4, 8, p_horn(0xC08A20, 0x4A3010), fit=True)
    m.mat('mouth', 8, 8, p_flat(0x8A5A10), fit=True)
    body = m.bone('body', pivot=(0, 10, 0), rot=(-15, 0, 0))
    m.box(body, (-3.5, 6, -5), (7, 7, 11), SKIN)
    m.box(body, (-3, 7, -7.5), (6, 6, 3), {'all': 'belly'})
    neck = m.bone('neck', body, pivot=(0, 12, -6))
    m.box(neck, (-1.6, 11, -8), (3.2, 6, 3.2), SKIN, rot=(-12, 0, 0), pivot=(0, 12, -6.5))
    head = m.bone('head', neck, pivot=(0, 16.5, -7))
    m.box(head, (-2, 15, -10.5), (4, 4, 5), SKIN)
    m.box(head, (-0.8, 15.6, -13.5), (1.6, 1.8, 3), {'all': 'gold'})
    for sx in (-1, 1):
        m.box(head, (sx * 2.02 - 0.4 * (sx < 0), 17, -9.4), (0.4, 1, 1), {'all': 'eye'})
    for i, (x, rx) in enumerate(((0, -30), (-1, -45), (1, -45))):
        m.box(head, (x - 0.5, 18.5, -8), (1, 6, 1), {'all': 'plume'}, rot=(rx, x * 20, 0), pivot=(x, 18.5, -7.5))
    jaw = m.bone('jaw', head, pivot=(0, 15.6, -10.5))
    m.box(jaw, (-0.6, 14.8, -13), (1.2, 0.8, 2.6), {'all': 'gold'})
    for sx, n in ((1, 'l'), (-1, 'r')):
        w1 = m.bone('wing_' + n, body, pivot=(sx * 3.5, 12, -3))
        m.box(w1, (sx * 3.5 + (0 if sx > 0 else -12), 11.5, -6), (12, 1, 10), {'all': 'wing'})
        w2 = m.bone('wing_' + n + '2', w1, pivot=(sx * 15.5, 12, -3))
        m.box(w2, (sx * 15.5 + (0 if sx > 0 else -15), 11.6, -5.5), (15, 0.8, 9), {'all': 'wing'})
        m.box(w2, (sx * 30.5 + (0 if sx > 0 else -5), 11.7, -4.5), (5, 0.6, 6), {'all': 'plume'})
        lg = m.bone('leg_' + n, None, pivot=(sx * 1.6, 7, 0.5))
        m.box(lg, (sx * 1.6 - 0.5, 0.6, 0), (1, 6.5, 1), {'all': 'gold'})
        for i in (-1, 0, 1):
            m.box(lg, (sx * 1.6 + i * 0.7 - 0.25, 0, -1.6), (0.5, 0.6, 2), {'all': 'claw'})
    for i, ry in enumerate((0, -16, 16)):
        tb = m.bone(['tail', 'tail_l', 'tail_r'][i], body, pivot=(0, 9, 5), rot=(-14 + 6 * (i > 0), ry, 0))
        m.box(tb, (-0.8, 8.6, 5), (1.6, 0.8, 19 - 3 * (i > 0)), {'all': 'plume'})
        m.box(tb, (-1.6, 8.5, 20 - 3 * (i > 0)), (3.2, 1, 4), {'all': 'wing'})
    return m, bird_anims('phoenix', tails=('tail', 'tail_l', 'tail_r'))


def seiryu():
    m = Model('seiryu', 128, 128)
    m.mat('fur', 32, 32, p_gradient(0x1E46B0, 0x6A9AF0, pat_scales(5, 4, 0.3), power=1.2), vfull=True)
    m.mat('top', 32, 32, p_flat(0x1A3A9A, pat_scales(5, 4, 0.3)))
    m.mat('belly', 32, 32, p_flat(0xF0E4B8, pat_plates(4, 0.3, 'u')))
    m.mat('mane', 16, 16, p_flat(0xE4F2FF, pat_fur(3, 0.3, 4)))
    m.mat('horn', 8, 16, p_horn(0xF2E2A8, 0xB08A40), vfull=True)
    m.mat('eye', 4, 4, p_eye(0xFF3A1A, slit=True), fit=True, glow=True)
    m.mat('tooth', 4, 4, p_teeth(), fit=True)
    m.mat('mouth', 16, 16, p_flat(0xA02A3A, pat_mottle(3, 0.2)))
    m.mat('claw', 4, 8, p_horn(0xF2E2A8, 0x8A6A30), fit=True)
    m.mat('whisker', 8, 8, p_flat(0xE8F0FF), fit=True)
    head = m.bone('head', None, pivot=(0, 14, -8))
    m.box(head, (-5, 9, -18), (10, 9, 10), SKIN)
    m.box(head, (-3.6, 9.5, -28), (7.2, 5.5, 10.5), dict(SKIN, down='mouth'))
    m.box(head, (-2.6, 14.2, -28.5), (5.2, 2, 4), {'all': 'top'})
    m.box(head, (-6.5, 7, -12), (13, 12, 6), {'all': 'mane'}, inflate=0.4)
    for sx in (-1, 1):
        m.box(head, (sx * 5.02 - 0.4 * (sx < 0), 14, -16.5), (0.4, 1.6, 2.4), {'all': 'eye'})
        m.box(head, (sx * 5 - 2, 15.5, -17.5), (4, 1.4, 5), {'all': 'top'})
        hb = m.bone('horn_' + ('l' if sx > 0 else 'r'), head, pivot=(sx * 3, 17.5, -12))
        m.box(hb, (sx * 3 - 1, 17, -12), (2, 2, 11), {'all': 'horn'}, rot=(-35, sx * -15, 0), pivot=(sx * 3, 18, -12))
        m.box(hb, (sx * 4 - 0.75, 19, -8), (1.5, 6, 1.5), {'all': 'horn'}, rot=(-20, 0, -sx * 15), pivot=(sx * 4, 19, -8))
        wb = m.bone('whisker_' + ('l' if sx > 0 else 'r'), head, pivot=(sx * 3.5, 11.5, -26))
        m.box(wb, (sx * 3.5 - 0.4, 11.1, -26), (0.8, 0.8, 16), {'all': 'whisker'}, rot=(18, sx * -38, 0), pivot=(sx * 3.5, 11.5, -26))
        for z in range(-27, -19, 2):
            m.box(head, (sx * 3 - 0.4, 8.6, z), (0.8, 1.2, 0.8), {'all': 'tooth'})
    jaw = m.bone('jaw', head, pivot=(0, 10, -17))
    m.box(jaw, (-3.2, 6.5, -27.5), (6.4, 3, 11), dict(SKIN, up='mouth'))
    for sx in (-1, 1):
        m.box(jaw, (sx * 2.6 - 0.4, 9.5, -27), (0.8, 1.4, 0.8), {'all': 'tooth'})
    parent, z = None, -9
    widths = [9, 9.5, 9, 8.5, 7.5, 6.5, 5.5, 4.5, 3.5]
    for k, w in enumerate(widths):
        sb = m.bone(f'seg{k}', parent, pivot=(0, 13.5, z))
        m.box(sb, (-w / 2, 13.5 - w / 2, z), (w, w, 12.5), SKIN)
        m.box(sb, (-0.6, 13.5 + w / 2 - 0.3, z + 2), (1.2, max(1.5, w * 0.35), 8), {'all': 'mane'})
        if k in (1, 5):
            for sx in (-1, 1):
                lb = m.bone(f'leg{k}_' + ('l' if sx > 0 else 'r'), sb, pivot=(sx * w / 2, 11.5, z + 5), rot=(25, 0, -sx * 20))
                m.box(lb, (sx * w / 2 - 1.1, 5.5, z + 4), (2.2, 6.5, 2.2), SKIN)
                for i in (-1, 0, 1):
                    m.box(lb, (sx * w / 2 + i * 0.8 - 0.3, 5, z + 2.8), (0.6, 0.8, 1.6), {'all': 'claw'})
        if k == len(widths) - 1:
            m.box(sb, (-2.5, 10.5, z + 11), (5, 6, 7), {'all': 'mane'}, inflate=0.4)
        parent = sb
        z += 12.5
    a = Anims('seiryu')
    n = len(widths)

    def body_wave(an, L, amp_y, amp_x, cycles, speed):
        bones = {}
        for k in range(n):
            bones[f'seg{k}'] = {'rotation': (lambda t, k=k: (amp_x * wave(t, L, cycles, -0.11 * k), amp_y * wave(t, L, cycles, -0.13 * k), 0))}
        for k in (1, 5):
            for sx, s in (('l', 1), ('r', -1)):
                bones[f'leg{k}_{sx}'] = {'rotation': (lambda t, k=k, s=s: (20 * wave(t, L, cycles * speed, 0.2 * k + (0 if s > 0 else 0.5)), 0, 0))}
        bones['whisker_l'] = {'rotation': lambda t: (8 * wave(t, L, cycles, 0.2), 6 * wave(t, L, cycles), 0)}
        bones['whisker_r'] = {'rotation': lambda t: (8 * wave(t, L, cycles, 0.7), -6 * wave(t, L, cycles), 0)}
        return bones
    L = 3.0
    b = body_wave(a, L, 10, 3, 1, 1)
    b['head'] = {'rotation': lambda t: (3 * wave(t, L, 1, 0.1), 5 * wave(t, L, 1, 0.05), 0), 'position': lambda t: (0, 0.6 * wave(t, L, 1), 0)}
    b['jaw'] = {'rotation': lambda t: (4 + 3 * wave(t, L, 2), 0, 0)}
    a.add('idle', L, b)
    L = 1.4
    b = body_wave(a, L, 16, 4, 1, 2)
    b['head'] = {'rotation': lambda t: (2 * wave(t, L, 2), 8 * wave(t, L, 1, 0.05), 0)}
    b['jaw'] = {'rotation': const((5, 0, 0))}
    a.add('walk', L, b)
    L = 1.0
    b = body_wave(a, L, 14, 7, 1, 1)
    b['head'] = {'rotation': lambda t: (-6 + 3 * wave(t, L, 1, 0.1), 6 * wave(t, L, 1, 0.05), 0), 'position': lambda t: (0, 1.0 * wave(t, L, 1), 0)}
    b['jaw'] = {'rotation': const((8, 0, 0))}
    a.add('fly', L, b)
    L = 0.6
    b = body_wave(a, L, 6, 2, 1, 1)
    b['head'] = {'rotation': lambda t: (-14 * smooth(0, 0.15, t) * (1 - smooth(0.15, 0.25, t)) + 18 * smooth(0.15, 0.27, t) * (1 - smooth(0.35, 0.6, t)), 0, 0),
                 'position': lambda t: (0, 0, -4 * smooth(0.1, 0.25, t) * (1 - smooth(0.35, 0.6, t)))}
    b['jaw'] = {'rotation': lambda t: (48 * smooth(0.02, 0.16, t) * (1 - smooth(0.24, 0.34, t)), 0, 0)}
    a.add('attack', L, b)
    return m, a


def orochi():
    m = Model('orochi', 128, 128)
    m.mat('fur', 32, 32, p_gradient(0x3A1E4E, 0xB898C8, pat_scales(4, 3, 0.3), power=1.3), vfull=True)
    m.mat('top', 32, 32, p_stripes(p_flat(0x34184A, pat_scales(4, 3, 0.3)), 0x1E0A2A, 3, 0.25, seed=5, vertical=False))
    m.mat('belly', 32, 32, p_flat(0xD8C0DC, pat_plates(3, 0.3)))
    m.mat('eye', 4, 4, p_eye(0xF8D020, slit=True), fit=True)
    m.mat('tongue', 8, 8, p_flat(0xD02040), fit=True)
    m.mat('horn', 8, 8, p_horn(0xE0D0B0, 0x6A5A40), fit=True)
    body = m.bone('body', pivot=(0, 8, 0))
    m.box(body, (-12, 0, -11), (24, 9, 22), SKIN)
    m.box(body, (-9.5, 8.5, -8), (19, 7.5, 16), SKIN)
    t1 = m.bone('tail', body, pivot=(0, 4, 11))
    m.box(t1, (-4, 0, 10), (8, 7, 13), SKIN)
    t2 = m.bone('tail2', t1, pivot=(0, 4, 22))
    m.box(t2, (-2.5, 0.5, 22), (5, 5, 13), SKIN)
    spots = [(-6, -4.5), (-2, -5.5), (2, -5.5), (6, -4.5), (-7, 2.5), (-2.5, 3.5), (2.5, 3.5), (7, 2.5)]
    for i, (x, z) in enumerate(spots):
        lean_z = -x * 5.5
        lean_x = 30 if z < 0 else -22
        nb = m.bone(f'neck{i}', body, pivot=(x, 15, z), rot=(lean_x, 0, lean_z))
        m.box(nb, (x - 1.8, 15, z - 1.8), (3.6, 11, 3.6), SKIN)
        nb2 = m.bone(f'neckb{i}', nb, pivot=(x, 25.5, z), rot=(-lean_x * 0.6 - 6, 0, -lean_z * 0.3))
        m.box(nb2, (x - 1.5, 25, z - 1.5), (3, 8, 3), SKIN)
        hb = m.bone(f'head{i}' if i else 'head', nb2, pivot=(x, 33, z))
        m.box(hb, (x - 2.3, 32, z - 5.5), (4.6, 3.8, 6.5), SKIN)
        m.box(hb, (x - 1.6, 32.2, z - 8), (3.2, 2.4, 3), SKIN)
        m.box(hb, (x - 0.3, 32.4, z - 10), (0.6, 0.3, 2.2), {'all': 'tongue'})
        for sx in (-1, 1):
            m.box(hb, (x + sx * 2.32 - 0.4 * (sx < 0), 34.4, z - 4.5), (0.4, 0.9, 1.1), {'all': 'eye'})
            m.box(hb, (x + sx * 1.4 - 0.4, 35.6, z - 3), (0.8, 1.6, 0.8), {'all': 'horn'}, rot=(-30, 0, 0), pivot=(x + sx * 1.4, 35.6, z - 2.6))
    a = Anims('orochi')

    def necks(L, amp, cycles, strike=None):
        bones = {}
        for i in range(8):
            ph = i * 0.13
            if strike:
                bones[f'neck{i}'] = {'rotation': (lambda t, i=i, ph=ph: (strike(t - ph * 0.15), 4 * wave(t, L, 1, ph), 0))}
                bones[f'neckb{i}'] = {'rotation': (lambda t, ph=ph: (strike(t - ph * 0.15) * 0.6, 0, 0))}
            else:
                bones[f'neck{i}'] = {'rotation': (lambda t, ph=ph: (amp * wave(t, L, cycles, ph), amp * 0.8 * wave(t, L, cycles, ph + 0.25), amp * 0.5 * wave(t, L, cycles, ph + 0.5)))}
                bones[f'neckb{i}'] = {'rotation': (lambda t, ph=ph: (amp * 0.7 * wave(t, L, cycles, ph + 0.15), 0, 0))}
            hn = f'head{i}' if i else 'head'
            bones[hn] = {'rotation': (lambda t, ph=ph: (3 * wave(t, L, cycles, ph + 0.3), 6 * wave(t, L, cycles, ph), 0))}
        return bones
    L = 3.0
    b = necks(L, 7, 1)
    b['body'] = {'position': lambda t: (0, 0.2 * wave(t, L), 0)}
    b['tail'] = {'rotation': lambda t: (0, 10 * wave(t, L), 0)}
    b['tail2'] = {'rotation': lambda t: (0, 14 * wave(t, L, 1, 0.15), 0)}
    a.add('idle', L, b)
    L = 1.2
    b = necks(L, 9, 1)
    b['body'] = {'rotation': lambda t: (0, 6 * wave(t, L), 3 * wave(t, L, 1, 0.25)), 'position': lambda t: (0.8 * wave(t, L), 0, 0)}
    b['tail'] = {'rotation': lambda t: (0, 22 * wave(t, L, 1, 0.2), 0)}
    b['tail2'] = {'rotation': lambda t: (0, 28 * wave(t, L, 1, 0.35), 0)}
    a.add('walk', L, b)
    L = 0.8
    b = necks(L, 0, 1, strike=lambda t: -12 * smooth(0, 0.15, t) * (1 - smooth(0.15, 0.28, t)) + 38 * smooth(0.15, 0.3, t) * (1 - smooth(0.4, 0.75, t)))
    b['body'] = {'rotation': lambda t: (5 * smooth(0.1, 0.3, t) * (1 - smooth(0.4, 0.8, t)), 0, 0)}
    b['tail'] = {'rotation': Z}
    b['tail2'] = {'rotation': Z}
    a.add('attack', L, b)
    L = 0.6
    b = necks(L, 4, 1)
    b['body'] = {'rotation': const((-6, 0, 0))}
    b['tail'] = {'rotation': const((14, 0, 0))}
    b['tail2'] = {'rotation': const((10, 0, 0))}
    a.add('fly', L, b)
    return m, a


def kraken():
    m = Model('kraken', 128, 128)
    m.mat('fur', 32, 32, p_gradient(0x9A2440, 0xE88A9A, pat_combine(pat_mottle(5, 0.2, 7), pat_mottle(2, 0.08, 9)), power=1.4), vfull=True)
    m.mat('top', 32, 32, p_spots(p_flat(0x8A1E38, pat_mottle(5, 0.2)), 0x5A0E22, 7, 0.3, seed=44))
    m.mat('belly', 32, 32, p_suckers(0xF0B4BC, 0xF8D6DC, 4))
    m.mat('eye', 8, 8, p_eye(0xF0C020, slit=False, sclera=0xF8F0D0), fit=True)
    m.mat('beak', 4, 4, p_flat(0x2A2018), fit=True)
    mantle = m.bone('head', None, pivot=(0, 18, 0))
    m.box(mantle, (-8, 11, -8), (16, 15, 16), SKIN)
    m.box(mantle, (-7, 25.5, -6.5), (14, 6, 14), {'all': 'top'})
    m.box(mantle, (-5, 31, -4.5), (10, 3, 10), {'all': 'top'})
    m.box(mantle, (-7.2, 8.5, -7.2), (14.4, 3, 14.4), {'all': 'belly', 'side': 'fur'})
    for sx in (-1, 1):
        m.box(mantle, (sx * 8 - (0 if sx > 0 else 2.5), 15, -5), (2.5, 4.5, 4.5), {'all': 'fur', 'east': 'eye', 'west': 'eye'})
    m.box(mantle, (-1.5, 8, -2), (3, 1, 3), {'all': 'beak'})
    tents = []
    for i in range(8):
        a_deg = i * 45 + 22.5
        a_rad = math.radians(a_deg)
        x, z = 6 * math.sin(a_rad), -6 * math.cos(a_rad)
        b1 = m.bone(f't{i}a', mantle, pivot=(x, 10, z), rot=(28, -a_deg, 0))
        mats = {'side': 'fur', 'up': 'fur', 'down': 'belly'}
        m.box(b1, (x - 2.2, 8, z - 11), (4.4, 4, 11), mats)
        b2 = m.bone(f't{i}b', b1, pivot=(x, 10, z - 11), rot=(-6, 0, 0))
        m.box(b2, (x - 1.7, 8.5, z - 21), (3.4, 3.2, 10.2), mats)
        b3 = m.bone(f't{i}c', b2, pivot=(x, 10, z - 21), rot=(-24, 0, 0))
        m.box(b3, (x - 1.2, 9, z - 30), (2.4, 2.2, 9.2), mats)
        tents.append(i)
    a = Anims('kraken')

    def tentacles(L, amp, cycles, front_strike=None):
        bones = {}
        for i in tents:
            ph = i / 8
            if front_strike and i in (0, 7):
                bones[f't{i}a'] = {'rotation': (lambda t: (front_strike(t), 0, 0))}
                bones[f't{i}b'] = {'rotation': (lambda t: (front_strike(t) * 0.4, 0, 0))}
                bones[f't{i}c'] = {'rotation': (lambda t: (front_strike(t) * 0.3, 0, 0))}
                continue
            bones[f't{i}a'] = {'rotation': (lambda t, ph=ph: (amp * wave(t, L, cycles, ph), amp * 0.4 * wave(t, L, cycles, ph + 0.25), 0))}
            bones[f't{i}b'] = {'rotation': (lambda t, ph=ph: (amp * 1.2 * wave(t, L, cycles, ph + 0.12), 0, 0))}
            bones[f't{i}c'] = {'rotation': (lambda t, ph=ph: (amp * 1.6 * wave(t, L, cycles, ph + 0.24), 0, 0))}
        return bones
    L = 3.0
    b = tentacles(L, 6, 1)
    b['head'] = {'position': lambda t: (0, 0.5 * wave(t, L), 0), 'rotation': lambda t: (2 * wave(t, L, 1, 0.25), 0, 0)}
    a.add('idle', L, b)
    L = 1.2
    b = tentacles(L, 14, 1)
    b['head'] = {'position': lambda t: (0, 0.8 * abs(wave(t, L, 1)), 0), 'rotation': lambda t: (5 + 2 * wave(t, L, 2), 0, 0)}
    a.add('walk', L, b)
    L = 0.7
    b = tentacles(L, 4, 1, front_strike=lambda t: -60 * smooth(0, 0.25, t) * (1 - smooth(0.25, 0.4, t)) + 30 * smooth(0.25, 0.4, t) * (1 - smooth(0.45, 0.7, t)))
    b['head'] = {'rotation': lambda t: (10 * smooth(0.1, 0.3, t) * (1 - smooth(0.4, 0.7, t)), 0, 0)}
    a.add('attack', L, b)
    L = 0.8
    b = tentacles(L, 10, 1)
    for i in tents:
        b[f't{i}a']['rotation'] = (lambda t, ph=i / 8: (-30 + 8 * wave(t, L, 1, ph), 0, 0))
    b['head'] = {'rotation': const((-10, 0, 0))}
    a.add('fly', L, b)
    return m, a


ZOANS = {'wolf': wolf, 'leopard': leopard, 'bison': bison, 'phoenix': phoenix, 'elephant': elephant, 'allosaurus': allosaurus,
         'pteranodon': pteranodon, 'spinosaurus': spinosaurus, 'seiryu': seiryu, 'orochi': orochi, 'kraken': kraken}


def gen_zoans(out):
    for name, fn in ZOANS.items():
        m, a = fn()
        out(m, 'zoan', name, a)


def gen_wildlife(out):
    try:
        import gen_wildlife as gw
    except ImportError:
        return
    gw.gen(out)
