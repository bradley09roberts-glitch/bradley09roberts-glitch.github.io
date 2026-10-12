"""Island wildlife models for gen_creatures.py: Kung-Fu Dugong, Lapahn, Banana-gator and Sea Cat.
Animations: idle, walk, attack (and swim for the water-goers), each keying 'head'."""
from creaturelib import *  # noqa: F401,F403
from gen_beasts import basic_mats, leg, eyes_front, eyes_side, quad_anims, SKIN, Z, const


def dugong():
    m = Model('kungfu_dugong', 128, 64)
    basic_mats(m, 0x9A8A78, 0xE6D8C0, pat_mottle(4, 0.12), eye=0x1A1410)
    m.mat('shell', 32, 32, p_flat(0x4E7A3A, pat_combine(pat_plates(6, 0.35, 'u'), pat_plates(6, 0.35), pat_mottle(4, 0.1))))
    m.mat('shellrim', 16, 16, p_flat(0xC8B070, pat_mottle(3, 0.12)))
    m.mat('band', 8, 8, p_flat(0xC8302A), fit=True)
    m.mat('whisker', 4, 4, p_flat(0x3A3028), fit=True)
    body = m.bone('body', pivot=(0, 6, 0))
    m.box(body, (-4, 1.5, -3), (8, 9, 6), SKIN)                                  # pear-shaped torso, upright
    m.box(body, (-3.5, 0, -2.5), (7, 2, 5), SKIN)
    m.box(body, (-4.3, 3, 1.5), (8.6, 8, 3), {'all': 'shell', 'south': 'shell', 'side': 'shellrim'})   # turtle shell on the back
    head = m.bone('head', body, pivot=(0, 11, 0))
    m.box(head, (-3, 10.5, -3), (6, 5, 5), SKIN)
    m.box(head, (-2, 10.6, -4.4), (4, 2.6, 1.6), {'all': 'belly'})                # muzzle
    m.box(head, (-0.6, 12.5, -4.6), (1.2, 0.6, 0.3), {'all': 'nose'})
    m.box(head, (-3.15, 14, -3.15), (6.3, 0.9, 5.3), {'all': 'band'})            # kung-fu headband
    m.box(head, (2.4, 13.2, 2), (0.6, 2.4, 1.2), {'all': 'band'})
    eyes_front(m, head, 0, 13.0, -3, 1.4, (0.9, 0.9))
    for sx in (-1, 1):
        m.box(head, (sx * 2.2 - 0.9, 11.8, -4.5), (1.8, 0.2, 0.2), {'all': 'whisker'})
    jaw = m.bone('jaw', head, pivot=(0, 10.8, -3))
    m.box(jaw, (-1.5, 10.0, -4.2), (3, 0.8, 1.6), {'all': 'mouth'})
    for sx, n in ((1, 'arm_l'), (-1, 'arm_r')):
        ab = m.bone(n, body, pivot=(sx * 4, 9, 0), rot=(-10, 0, sx * 20))
        m.box(ab, (sx * 4 - 1, 4, -1.2), (2, 5, 2.4), SKIN)
        m.box(ab, (sx * 4 - 1.2, 3, -1.6), (2.4, 1.2, 3), {'all': 'belly'})
    for sx, n in ((1, 'leg_l'), (-1, 'leg_r')):
        lb = m.bone(n, None, pivot=(sx * 2, 1.5, 0))
        m.box(lb, (sx * 2 - 1.5, 0, -3), (3, 1.2, 4), SKIN)
    a = Anims('kungfu_dugong')
    L = 2.0
    a.add('idle', L, {'body': {'rotation': lambda t: (0, 0, 3 * wave(t, L)), 'position': lambda t: (0, 0.2 * wave(t, L, 2), 0)},
                      'head': {'rotation': lambda t: (2 * wave(t, L, 2), 8 * wave(t, L), 0)}, 'jaw': {'rotation': Z},
                      'arm_l': {'rotation': lambda t: (-50 + 6 * wave(t, L), 0, 10)}, 'arm_r': {'rotation': lambda t: (-70 + 6 * wave(t, L, 1, 0.5), 0, -10)},
                      'leg_l': {'rotation': Z}, 'leg_r': {'rotation': Z}})
    L = 0.6
    a.add('walk', L, {'body': {'rotation': lambda t: (0, 0, 8 * wave(t, L)), 'position': lambda t: (0, 0.6 * abs(wave(t, L)), 0)},
                      'head': {'rotation': lambda t: (0, 0, -4 * wave(t, L))}, 'jaw': {'rotation': Z},
                      'arm_l': {'rotation': lambda t: (-30 + 20 * wave(t, L), 0, 0)}, 'arm_r': {'rotation': lambda t: (-30 - 20 * wave(t, L), 0, 0)},
                      'leg_l': {'rotation': lambda t: (25 * wave(t, L), 0, 0)}, 'leg_r': {'rotation': lambda t: (-25 * wave(t, L), 0, 0)}})
    L = 0.5
    a.add('attack', L, {'body': {'rotation': lambda t: (8 * smooth(0, 0.2, t) * (1 - smooth(0.3, 0.5, t)), 25 * wave(t, L), 0)},
                        'head': {'rotation': Z}, 'jaw': {'rotation': lambda t: (20 * smooth(0, 0.15, t) * (1 - smooth(0.3, 0.5, t)), 0, 0)},
                        'arm_l': {'rotation': lambda t: (-90 * smooth(0, 0.15, t) * (1 - smooth(0.2, 0.35, t)) - 40, 0, 0)},
                        'arm_r': {'rotation': lambda t: (-90 * smooth(0.2, 0.32, t) * (1 - smooth(0.38, 0.5, t)) - 40, 0, 0)},
                        'leg_l': {'rotation': Z}, 'leg_r': {'rotation': Z}})
    a.add('swim', 1.0, {'body': {'rotation': lambda t: (70, 0, 6 * wave(t, 1.0))}, 'head': {'rotation': const((-60, 0, 0))}, 'jaw': {'rotation': Z},
                        'arm_l': {'rotation': lambda t: (0, 0, 40 + 20 * wave(t, 1.0))}, 'arm_r': {'rotation': lambda t: (0, 0, -40 - 20 * wave(t, 1.0))},
                        'leg_l': {'rotation': lambda t: (20 * wave(t, 1.0), 0, 0)}, 'leg_r': {'rotation': lambda t: (20 * wave(t, 1.0), 0, 0)}})
    return m, a


def lapahn():
    m = Model('lapahn', 128, 128)
    basic_mats(m, 0xF4F4F8, 0xD8DCE6, pat_fur(4, 0.18), eye=0xD8202A)
    m.mat('ear', 16, 16, p_gradient(0xF0F0F4, 0xE8A8B0, power=2.0), vfull=True)
    m.mat('fang', 4, 8, p_teeth(), fit=True)
    m.mat('pad', 8, 8, p_flat(0xC89098), fit=True)
    body = m.bone('body', pivot=(0, 18, 0))
    m.box(body, (-8, 10, -6), (16, 18, 13), SKIN, inflate=0.2)                   # huge furry torso
    m.box(body, (-6.5, 9, -7), (13, 12, 3), {'all': 'belly'})
    head = m.bone('head', body, pivot=(0, 28, -2))
    m.box(head, (-6, 27, -8), (12, 10, 10), SKIN, inflate=0.2)
    m.box(head, (-3.5, 27.5, -10), (7, 4, 2.4), {'all': 'belly'})
    m.box(head, (-0.9, 30.2, -10.4), (1.8, 1, 0.5), {'all': 'pad'})
    eyes_front(m, head, 0, 32.2, -8.2, 3, (1.6, 1.4))
    for sx in (-1, 1):
        m.box(head, (sx * 1.6 - 0.5, 25.6, -10.1), (1, 2.6, 0.8), {'all': 'fang'})
        eb = m.bone('ear_' + ('l' if sx > 0 else 'r'), head, pivot=(sx * 3.5, 37, -3), rot=(-12, 0, -sx * 12))
        m.box(eb, (sx * 3.5 - 1.6, 37, -4), (3.2, 12, 2), {'north': 'ear', 'all': 'fur'})
    jaw = m.bone('jaw', head, pivot=(0, 27.5, -6))
    m.box(jaw, (-3, 26, -9.8), (6, 1.6, 4), {'all': 'mouth'})
    for sx, n in ((1, 'arm_l'), (-1, 'arm_r')):
        ab = m.bone(n, body, pivot=(sx * 8, 25, -1), rot=(-15, 0, sx * 10))
        m.box(ab, (sx * 8 + (0 if sx > 0 else -4), 13, -3), (4, 12, 4.5), SKIN, inflate=0.2)
        for i in (-1, 0, 1):
            m.box(ab, (sx * 8 + (2 if sx > 0 else -2) + i * 1.2 - 0.3, 12, -3.6), (0.6, 1.2, 1.2), {'all': 'claw'})
    for sx, n in ((1, 'leg_l'), (-1, 'leg_r')):
        lb = m.bone(n, None, pivot=(sx * 4.5, 11, 1))
        m.box(lb, (sx * 4.5 - 2.6, 0, -3), (5.2, 11, 6), SKIN, inflate=0.2)
        m.box(lb, (sx * 4.5 - 2.8, 0, -5.5), (5.6, 2, 3), SKIN)
    tail = m.bone('tail', body, pivot=(0, 12, 7))
    m.box(tail, (-2, 10.5, 6.5), (4, 4, 3.5), {'all': 'belly'}, inflate=0.4)
    a = Anims('lapahn')
    L = 3.0
    a.add('idle', L, {'body': {'position': lambda t: (0, 0.3 * wave(t, L), 0)}, 'head': {'rotation': lambda t: (3 * wave(t, L, 2), 10 * wave(t, L), 0)},
                      'jaw': {'rotation': lambda t: (3 + 3 * wave(t, L, 2), 0, 0)},
                      'ear_l': {'rotation': lambda t: (4 * wave(t, L, 1, 0.2), 0, 0)}, 'ear_r': {'rotation': lambda t: (4 * wave(t, L, 1, 0.7), 0, 0)},
                      'arm_l': {'rotation': lambda t: (4 * wave(t, L), 0, 0)}, 'arm_r': {'rotation': lambda t: (-4 * wave(t, L), 0, 0)},
                      'leg_l': {'rotation': Z}, 'leg_r': {'rotation': Z}, 'tail': {'rotation': Z}})
    L = 0.7
    a.add('walk', L, {'body': {'position': lambda t: (0, 1.4 * abs(wave(t, L)), 0), 'rotation': lambda t: (6 + 3 * wave(t, L, 2), 0, 3 * wave(t, L))},
                      'head': {'rotation': lambda t: (-4 * wave(t, L, 2), 0, 0)}, 'jaw': {'rotation': const((6, 0, 0))},
                      'ear_l': {'rotation': lambda t: (-14 * abs(wave(t, L)), 0, 0)}, 'ear_r': {'rotation': lambda t: (-14 * abs(wave(t, L, 1, 0.1)), 0, 0)},
                      'arm_l': {'rotation': lambda t: (-30 * wave(t, L), 0, 0)}, 'arm_r': {'rotation': lambda t: (30 * wave(t, L), 0, 0)},
                      'leg_l': {'rotation': lambda t: (30 * wave(t, L), 0, 0)}, 'leg_r': {'rotation': lambda t: (-30 * wave(t, L), 0, 0)},
                      'tail': {'rotation': lambda t: (0, 10 * wave(t, L), 0)}})
    L = 0.6
    a.add('attack', L, {'body': {'rotation': lambda t: (16 * smooth(0, 0.2, t) * (1 - smooth(0.3, 0.6, t)), 0, 0)},
                        'head': {'rotation': lambda t: (-10 * smooth(0, 0.15, t) * (1 - smooth(0.25, 0.5, t)), 0, 0)},
                        'jaw': {'rotation': lambda t: (35 * smooth(0, 0.15, t) * (1 - smooth(0.3, 0.45, t)), 0, 0)},
                        'ear_l': {'rotation': const((-25, 0, 0))}, 'ear_r': {'rotation': const((-25, 0, 0))},
                        'arm_l': {'rotation': lambda t: (-130 * smooth(0, 0.2, t) * (1 - smooth(0.25, 0.45, t)), 0, 0)},
                        'arm_r': {'rotation': lambda t: (-130 * smooth(0.1, 0.3, t) * (1 - smooth(0.35, 0.55, t)), 0, 0)},
                        'leg_l': {'rotation': Z}, 'leg_r': {'rotation': Z}, 'tail': {'rotation': Z}})
    return m, a


def banana_gator():
    m = Model('banana_gator', 128, 128)
    base = p_gradient(0x4E6A2A, 0xD8CC8A, pat_scales(4, 3, 0.35), power=1.5)
    m.mat('fur', 32, 32, base, vfull=True)
    m.mat('top', 32, 32, p_flat(0x3E5A22, pat_combine(pat_plates(4, 0.3, 'u'), pat_plates(4, 0.3), pat_mottle(5, 0.12))))
    m.mat('belly', 32, 32, p_flat(0xE0D49A, pat_plates(3, 0.25)))
    m.mat('eye', 4, 4, p_eye(0xF0D020, slit=True), fit=True)
    m.mat('tooth', 4, 4, p_teeth(), fit=True)
    m.mat('mouth', 16, 16, p_flat(0xC8505A, pat_mottle(3, 0.15)))
    m.mat('claw', 4, 8, p_horn(0x2A2420, 0xB0A890), fit=True)
    m.mat('banana', 16, 32, p_gradient(0xFFE24A, 0xE0A820, pat_mottle(4, 0.08), power=1.0), vfull=True)
    m.mat('stem', 4, 4, p_flat(0x5A3A1A), fit=True)
    body = m.bone('body', pivot=(0, 6, 0))
    m.box(body, (-6, 2.5, -10), (12, 7.5, 22), SKIN)
    for z in range(-9, 11, 4):
        m.box(body, (-4, 10, z), (8, 1, 3), {'all': 'top'})
    head = m.bone('head', body, pivot=(0, 7, -10))
    m.box(head, (-4.5, 3.5, -16), (9, 6.5, 7), SKIN)
    m.box(head, (-3, 4, -30), (6, 4, 14.5), dict(SKIN, down='mouth'))
    for sx in (-1, 1):
        m.box(head, (sx * 3 - 1.2, 9.5, -14.5), (2.4, 1.6, 2.4), SKIN)
        m.box(head, (sx * 4.02 - 0.4 * (sx < 0), 9.7, -14), (0.4, 1.0, 1.4), {'all': 'eye'})
        for z in range(-29, -16, 2):
            m.box(head, (sx * 2.6 - 0.35, 2.9, z), (0.7, 1.2, 0.7), {'all': 'tooth'})
    # the banana-shaped horn that gives the beast its name
    bn = m.bone('banana', head, pivot=(0, 10, -12), rot=(-35, 0, 0))
    m.box(bn, (-1.6, 10, -13.5), (3.2, 7, 3.2), {'all': 'banana'})
    m.box(bn, (-1.4, 16.5, -12.5), (2.8, 4, 2.8), {'all': 'banana'}, rot=(-35, 0, 0), pivot=(0, 16.5, -11))
    m.box(bn, (-0.6, 20, -10.5), (1.2, 1.5, 1.2), {'all': 'stem'}, rot=(-50, 0, 0), pivot=(0, 20, -10))
    jaw = m.bone('jaw', head, pivot=(0, 4, -15))
    m.box(jaw, (-2.8, 1.6, -29.5), (5.6, 2.4, 14), dict(SKIN, up='mouth'))
    for name, x, z in (('leg_fl', 6, -7), ('leg_fr', -6, -7), ('leg_bl', 6, 8), ('leg_br', -6, 8)):
        b = m.bone(name, None, pivot=(x, 5, z), rot=(0, 0, -15 if x > 0 else 15))
        m.box(b, (x - 1.6, 0, z - 1.6), (3.2, 5, 3.2), SKIN)
        m.box(b, (x - 2, 0, z - 3.6), (4, 1.2, 3), SKIN)
    t1 = m.bone('tail', body, pivot=(0, 6, 12))
    m.box(t1, (-4, 3, 11.5), (8, 6, 12), SKIN)
    t2 = m.bone('tail2', t1, pivot=(0, 6, 23))
    m.box(t2, (-2.5, 3.5, 23), (5, 4.5, 13), SKIN)
    m.box(t2, (-0.5, 8, 24), (1, 2, 11), {'all': 'top'})
    a = quad_anims('banana_gator', tail=('tail', 'tail2'), swing=24, walk_len=1.0)
    a.add('swim', 1.2, {'body': {'rotation': lambda t: (0, 6 * wave(t, 1.2), 0)}, 'head': {'rotation': lambda t: (-4, -5 * wave(t, 1.2), 0)},
                        'jaw': {'rotation': Z}, 'tail': {'rotation': lambda t: (0, 25 * wave(t, 1.2, 1, 0.2), 0)},
                        'tail2': {'rotation': lambda t: (0, 35 * wave(t, 1.2, 1, 0.35), 0)},
                        'leg_fl': {'rotation': const((60, 0, 0))}, 'leg_fr': {'rotation': const((60, 0, 0))},
                        'leg_bl': {'rotation': const((60, 0, 0))}, 'leg_br': {'rotation': const((60, 0, 0))}})
    return m, a


def sea_cat():
    m = Model('sea_cat', 128, 128)
    base = p_gradient(0xE0882E, 0xF6E2C4, pat_fur(4, 0.15), power=1.6)
    m.mat('fur', 32, 32, p_stripes(base, 0x9A4A14, 4, 0.22, seed=12, wobble=3), vfull=True)
    m.mat('top', 32, 32, p_stripes(p_flat(0xD87A24, pat_fur(4, 0.15)), 0x8A3E10, 4, 0.3, seed=13, wobble=3))
    m.mat('belly', 32, 32, p_flat(0xF6E6CC, pat_fur(4, 0.1)))
    m.mat('eye', 4, 4, p_eye(0x3AD0E0, slit=True), fit=True)
    m.mat('nose', 4, 4, p_solid(0xD87A88), fit=True)
    m.mat('tooth', 4, 4, p_teeth(), fit=True)
    m.mat('mouth', 8, 8, p_flat(0x8A2A3A), fit=True)
    m.mat('claw', 4, 8, p_horn(0x2A2420, 0xD8D0C0), fit=True)
    m.mat('ear', 8, 8, p_gradient(0xD87A24, 0xF0A8A8, power=1.5), vfull=True)
    m.mat('fin', 32, 32, p_membrane(0x5AA8C8, 0x2A5A78))
    m.mat('whisker', 4, 4, p_flat(0xF8F8F8), fit=True)
    body = m.bone('body', pivot=(0, 18, 0))
    m.box(body, (-8, 11, -13), (16, 14, 28), SKIN)
    m.box(body, (-0.7, 25, -6), (1.4, 6, 16), {'all': 'fin'})                    # a fish-like dorsal fin: it is a cat of the sea
    head = m.bone('head', body, pivot=(0, 22, -13))
    m.box(head, (-7, 16, -24), (14, 12, 11), SKIN)
    m.box(head, (-4, 16, -26.5), (8, 5, 3), {'all': 'belly'})
    m.box(head, (-1, 20.2, -26.8), (2, 1.2, 0.4), {'all': 'nose'})
    eyes_front(m, head, 0, 23, -24.1, 3.3, (2.6, 2.0))
    for sx in (-1, 1):
        m.box(head, (sx * 4.5 - 2, 28, -20), (4, 5, 2), {'north': 'ear', 'all': 'top'}, rot=(0, 0, -sx * 12), pivot=(sx * 4.5, 28, -19))
        for i, ry in enumerate((10, -5)):
            m.box(head, (sx * 4 + (0 if sx > 0 else -8), 18.5 + i, -26.4), (8, 0.25, 0.25), {'all': 'whisker'}, rot=(0, sx * ry, 0), pivot=(sx * 4, 18.6, -26.3))
    jaw = m.bone('jaw', head, pivot=(0, 16.5, -22))
    m.box(jaw, (-3.2, 14.6, -26), (6.4, 2, 4), {'all': 'belly', 'up': 'mouth'})
    for sx in (-1, 1):
        m.box(head, (sx * 2 - 0.4, 14.4, -26.2), (0.8, 1.8, 0.8), {'all': 'tooth'})
    for name, x, z in (('leg_fl', 5.5, -9), ('leg_fr', -5.5, -9), ('leg_bl', 5.5, 10), ('leg_br', -5.5, 10)):
        leg(m, None, name, (x, 12, z), 5, 5, 12, paw=(5.6, 2, 6.4))
    t1 = m.bone('tail', body, pivot=(0, 22, 15), rot=(-30, 0, 0))
    m.box(t1, (-1.8, 20.2, 15), (3.6, 3.6, 14), SKIN)
    t2 = m.bone('tail2', t1, pivot=(0, 22, 28), rot=(40, 0, 0))
    m.box(t2, (-0.6, 18, 27), (1.2, 10, 10), {'all': 'fin'})                    # fish-tail tip
    a = quad_anims('sea_cat', tail=('tail', 'tail2'), swing=26, walk_len=1.0)
    a.add('swim', 1.2, {'body': {'rotation': lambda t: (-6, 0, 3 * wave(t, 1.2))}, 'head': {'rotation': const((-14, 0, 0))}, 'jaw': {'rotation': Z},
                        'tail': {'rotation': lambda t: (0, 25 * wave(t, 1.2), 0)}, 'tail2': {'rotation': lambda t: (0, 35 * wave(t, 1.2, 1, 0.2), 0)},
                        'leg_fl': {'rotation': lambda t: (40 * wave(t, 1.2), 0, 0)}, 'leg_fr': {'rotation': lambda t: (-40 * wave(t, 1.2), 0, 0)},
                        'leg_bl': {'rotation': lambda t: (-40 * wave(t, 1.2), 0, 0)}, 'leg_br': {'rotation': lambda t: (40 * wave(t, 1.2), 0, 0)}})
    return m, a


WILDLIFE = {'kungfu_dugong': dugong, 'lapahn': lapahn, 'banana_gator': banana_gator, 'sea_cat': sea_cat}


def gen(out):
    for name, fn in WILDLIFE.items():
        m, a = fn()
        out(m, 'beast', name, a)
