"""Citizen BODY layers: full-colour, untinted base skins (base UV regions only).

Each body = race face/ear template + skin-tone palette + undertunic, braies and
shoes (halflings: bare feet). Overlay regions are left transparent.
"""
from __future__ import annotations

from art_pixel import C, mix
from art_skin import Skin, face_rect

# ---------------------------------------------------------------- race definitions
# kind, skin base, shade target, eye iris colour, brow colour
BODIES = {
    'human_1': ('human', '#f3d5c2', '#6a2c34', '#5a7fb0', '#8a6a4a'),
    'human_2': ('human', '#e6bb98', '#6a2c34', '#4f7a4a', '#6b4a32'),
    'human_3': ('human', '#cf9d74', '#5a2630', '#6a4a2a', '#4a3324'),
    'human_4': ('human', '#b07a52', '#4a1f28', '#4a3a26', '#33241a'),
    'human_5': ('human', '#86573a', '#3a1820', '#3a2a1e', '#24180f'),
    'human_6': ('human', '#5e3c2a', '#2a1218', '#2e2018', '#180f0a'),
    'elf_1': ('elf', '#f4e2d4', '#5a3a50', '#3f9a8a', '#b8a080'),
    'elf_2': ('elf', '#dcb27a', '#5a3028', '#3a8a3a', '#8a6a30'),
    'elf_3': ('elf', '#a4abc4', '#2a2848', '#8a6ad0', '#3a3a5a'),
    'dwarf_1': ('dwarf', '#eab196', '#6a2428', '#5a7aa0', '#8a4a2a'),
    'dwarf_2': ('dwarf', '#cc8c6a', '#5a1e22', '#4a6a3a', '#5a3020'),
    'dwarf_3': ('dwarf', '#985f45', '#3e161a', '#3a2a1e', '#2e1a10'),
    'halfling_1': ('halfling', '#f1caa8', '#6a2c34', '#6a8a40', '#7a5232'),
    'halfling_2': ('halfling', '#d6a37a', '#5a2630', '#5a4024', '#5a3a22'),
    'halfling_3': ('halfling', '#a06e4a', '#401a20', '#3a2a1a', '#2e1c10'),
    'orc_1': ('orc', '#7f9468', '#1e2a24', '#d09a30', '#2e3a26'),
    'orc_2': ('orc', '#4f7038', '#14221a', '#c8402a', '#1e2c16'),
    'orc_3': ('orc', '#8e8b84', '#24222a', '#c0a050', '#3a3834'),
    'goblin_1': ('goblin', '#8a8c48', '#262a18', '#e04a2a', '#3a3a1a'),
    'goblin_2': ('goblin', '#b2b65c', '#2e3a1a', '#e0a020', '#4a4e22'),
}

# ---------------------------------------------------------------- face templates
# s base, h light, H highlight, d shade, D deep, b brow, w eye white, W lower white,
# i iris, j iris (lower/darker), y goblin sclera, p pupil, m lip, o mouth dark,
# c cheek blush, t tusk/teeth
FRONT = {
    'human': [
        "ssssssss",
        "sshhhhss",
        "ssssssss",
        "sbbssbbs",
        "swihhiws",
        "dsshdssd",
        "dssmmssd",
        "ddssssdd",
    ],
    'elf': [
        "ssssssss",
        "sshhhhss",
        "ssssssss",
        "bbssssbb",
        "swihhiws",
        "dWjhdjWd",
        "dssmmssd",
        "ddssssdd",
    ],
    'dwarf': [
        "ssssssss",
        "sshhhhss",
        "ssssssss",
        "bbbssbbb",
        "swiHHiws",
        "cshHHhsc",
        "sdDmmDds",
        "ddssssdd",
    ],
    'halfling': [
        "ssssssss",
        "sshhhhss",
        "ssssssss",
        "sbbssbbs",
        "swihhiws",
        "scshdscs",
        "dssmmssd",
        "ddssssdd",
    ],
    'orc': [
        "ssssssss",
        "sshhhhss",
        "sddssdds",
        "dbbDDbbd",
        "sDihhiDs",
        "dsdhhdsd",
        "dstDDtsd",
        "Ddmmmmdd",
    ],
    'goblin': [
        "ssssssss",
        "sshhhhss",
        "bbssssbb",
        "ssbssbss",
        "syphhpys",
        "dssHHssd",
        "dottttod",
        "ddsmmsdd",
    ],
}
# right face of the head (x: back -> front); the left face is its mirror
SIDE = {
    'human': [
        "ssssssss",
        "ssssssss",
        "ssssssss",
        "ssdEEsss",
        "ssdEesss",
        "ssdEEsss",
        "sssddsss",
        "ddddsssd",
    ],
    'elf': [
        "ssssssss",
        "sEssssss",
        "sdEEssss",
        "ssdEEsss",
        "ssdEesss",
        "sssdEsss",
        "ssssssss",
        "ddddsssd",
    ],
    'goblin': [
        "ssssssss",
        "EEssssss",
        "dEEEssss",
        "sdEeEEss",
        "ssdEeEss",
        "sssdEEss",
        "ssssdsss",
        "ddddsssd",
    ],
    'orc': [
        "ssssssss",
        "ssssssss",
        "ssEsssss",
        "ssdEEsss",
        "ssdEesss",
        "ssdEEsss",
        "sssddsss",
        "ddddsssd",
    ],
}
SIDE['dwarf'] = SIDE['human']
SIDE['halfling'] = SIDE['human']

BACK = [
    "ssssssss",
    "ssssssss",
    "ssssssss",
    "ssssssss",
    "ssssssss",
    "ssssssss",
    "dssssssd",
    "dddddddd",
]
TOP = [
    "ssssssss",
    "sshhhhss",
    "shhHHhhs",
    "shHHHHhs",
    "shHHHHhs",
    "shhHHhhs",
    "sshhhhss",
    "ssssssss",
]

# torso undertunic (L linen, l shade, k dark, K light, d/s neck skin)
TORSO_FRONT = [
    "LLddddLL",
    "LKLddLKL",
    "LKLLLLKL",
    "LLLLLLLL",
    "lLLLLLLl",
    "lLLlLLLl",
    "lLLlLLLl",
    "lLLLLlLl",
    "lLLLLlLl",
    "lLLLLLLl",
    "llLLLLll",
    "kkkkkkkk",
]
TORSO_BACK = [
    "LLLLLLLL",
    "LKKLLKKL",
    "LLLLLLLL",
    "LLLLLLLL",
    "lLLLLLLl",
    "lLLlLLLl",
    "lLLlLLLl",
    "lLLLLLLl",
    "lLLLLlLl",
    "lLLLLlLl",
    "llLLLLll",
    "kkkkkkkk",
]
TORSO_SIDE = [
    "llll",
    "lLLl",
    "LLLL",
    "LLLL",
    "LLLL",
    "LLlL",
    "LLlL",
    "LLLL",
    "LLLL",
    "LLLL",
    "lLLl",
    "kkkk",
]
TORSO_TOP = [
    "KKKKKKKK",
    "KKssssKK",
    "KKddddKK",
    "KKKKKKKK",
]

# right arm strip: outer(4) front(4) inner(4) back(4)
ARM = [
    "dhhs" "shhs" "dddd" "dssd",
    "dhss" "shss" "dddd" "dssd",
    "dsss" "shss" "dddd" "dssd",
    "dsss" "ssss" "dddd" "dssd",
    "dsss" "ssss" "dddd" "dssd",
    "dsss" "ssss" "dddd" "dssd",
    "dsss" "ssss" "dddd" "ddds",
    "dsss" "ssss" "dddd" "dssd",
    "dsss" "ssss" "dddd" "dssd",
    "dsss" "sssd" "dddd" "dssd",
    "dsss" "shsd" "dddd" "dssd",
    "dsds" "dsds" "dDdD" "dsds",
]
# right leg strip: outer front inner back.  L braies, P/Q/O shoes
LEG = [
    "lLLL" "LLLL" "llll" "lLLl",
    "lLLL" "LKLL" "llll" "lLLl",
    "lLLL" "LLLL" "llll" "lLLl",
    "kkkk" "kkkk" "kkkk" "kkkk",
    "dsss" "ssss" "dddd" "dssd",
    "dsss" "shhs" "dddd" "dssd",
    "dsss" "ssss" "dddd" "dhhd",
    "dsss" "ssss" "dddd" "dssd",
    "dsss" "ssss" "dddd" "dssd",
    "dsss" "ssss" "dddd" "dssd",
    "PPPP" "PQQP" "OPPO" "PPPP",
    "OOOO" "OQOO" "OOOO" "OOOO",
]
LEG_BAREFOOT = [
    "dfss" "sffs" "dddd" "dssd",
    "dsds" "hdhd" "dDdD" "dssd",
]


def palette(kind, skin, shadow, iris, brow):
    s = C(skin)
    pal = {
        'D': mix(s, shadow, 0.40), 'd': mix(s, shadow, 0.20), 's': s,
        'h': mix(s, '#fff3e0', 0.13), 'H': mix(s, '#fff3e0', 0.26),
        'b': C(brow), 'w': C('#f1ede4'), 'W': C('#d8d2c8'),
        'i': C(iris), 'j': mix(iris, '#101018', 0.35),
        'y': C('#e8d878'), 'p': C('#2a1a12'),
        'm': mix(s, '#7a2a30', 0.38), 'o': mix(s, '#1a0a0a', 0.70),
        'c': mix(s, '#e05a5a', 0.28), 't': C('#ece4c8'),
        'E': mix(s, '#c0505a', 0.10), 'e': mix(s, shadow, 0.45),
        'f': mix(brow, '#000000', 0.0),
    }
    if kind in ('orc', 'goblin'):
        cloth = C('#7a634a')
        pal.update({'P': C('#4e3a2a'), 'Q': C('#6a5038'), 'O': C('#33251c')})
        pal['t'] = C('#e6dcb8')
    else:
        cloth = C('#cdbc98')
        pal.update({'P': C('#5c4030'), 'Q': C('#7a5840'), 'O': C('#3a281e')})
    pal.update({'L': cloth, 'l': mix(cloth, '#3a2a2a', 0.18), 'k': mix(cloth, '#2a1a1a', 0.38),
                'K': mix(cloth, '#fff8e8', 0.25)})
    pal['f'] = mix(brow, s, 0.25)
    return pal


def _strip(sk, part, rows, pal):
    x0, y0, x1, y1 = sk.strip_rect(part)
    sk.ascii(rows, pal, x0, y0)


def make_body(body_id):
    kind, skin, shadow, iris, brow = BODIES[body_id]
    pal = palette(kind, skin, shadow, iris, brow)
    sk = Skin()
    # head ---------------------------------------------------------------
    sk.face_ascii('head', 'front', FRONT[kind], pal)
    sk.face_ascii('head', 'right', SIDE[kind], pal)
    sk.mirror_head_lr()
    sk.face_ascii('head', 'back', BACK, pal)
    sk.face_ascii('head', 'top', TOP, pal)
    sk.fill('head', 'bottom', pal['d'])
    # torso --------------------------------------------------------------
    sk.face_ascii('body', 'front', TORSO_FRONT, pal)
    sk.face_ascii('body', 'back', TORSO_BACK, pal)
    sk.face_ascii('body', 'right', TORSO_SIDE, pal)
    sk.face_ascii('body', 'left', [r[::-1] for r in TORSO_SIDE], pal)
    sk.face_ascii('body', 'top', TORSO_TOP, pal)
    sk.fill('body', 'bottom', pal['l'])
    # arms ----------------------------------------------------------------
    _strip(sk, 'right_arm', ARM, pal)
    sk.fill('right_arm', 'top', pal['h'])
    sk.fill('right_arm', 'bottom', pal['d'])
    sk.mirror_limb('right_arm', 'left_arm')
    # legs ----------------------------------------------------------------
    rows = list(LEG)
    if kind == 'halfling':
        rows[10:12] = LEG_BAREFOOT
    _strip(sk, 'right_leg', rows, pal)
    sk.fill('right_leg', 'top', pal['L'])
    sk.fill('right_leg', 'bottom', pal['D'] if kind == 'halfling' else pal['O'])
    sk.mirror_limb('right_leg', 'left_leg')
    return sk
