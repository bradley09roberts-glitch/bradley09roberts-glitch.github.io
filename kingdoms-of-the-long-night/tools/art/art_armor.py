"""Optional ARMOUR layers for soldiers: full colour, OVERLAY regions only
(hat / jacket / sleeves / pants), drawn over body+clothes+hair."""
from __future__ import annotations

from art_pixel import C, mix
from art_skin import SIDES, Skin

RA, LA, RL, LL = 'right_arm', 'left_arm', 'right_leg', 'left_leg'

MAIL = {'h': C('#b4bac2'), 'l': C('#878e97'), 'm': C('#5c626a'), 'd': C('#2f3238')}
LEATHER = {'L': C('#6a4b33'), 'l': C('#4a3324'), 'K': C('#2e2018'), 'b': C('#c9a14c'), 'B': C('#8a6a2c')}
STEEL = {'0': C('#24272c'), '1': C('#454a52'), '2': C('#6a717a'), '3': C('#939ba4'),
         '4': C('#c0c7ce'), '5': C('#e6ebf0')}


def mail_px(x, y, dark=0):
    """Interlocking ring texel; ``dark`` darkens for shadowed faces."""
    ch = ("lhll", "mdmd", "lllh", "mdmd")[y % 4][x % 4]
    if dark:
        ch = {'h': 'l', 'l': 'm', 'm': 'd', 'd': 'd'}[ch] if dark == 1 else {'h': 'm', 'l': 'd', 'm': 'd', 'd': 'd'}[ch]
    return MAIL[ch]


def mail_face(sk, part, face, rows, cols=None, dark=0, ov=True, xoff=0):
    from art_skin import face_rect
    x0, y0, x1, y1 = face_rect(part, face, ov)
    w = x1 - x0
    for y in rows:
        for x in (cols if cols is not None else range(w)):
            sk.fp(part, face, x, y, mail_px(x + xoff, y, dark), ov)


def make_mail():
    sk = Skin()
    # coif -----------------------------------------------------------------
    for f in ('top', 'back', 'right', 'left'):
        mail_face(sk, 'head', f, range(8), dark=1 if f == 'back' else 0)
    mail_face(sk, 'head', 'front', (0, 1, 7))
    mail_face(sk, 'head', 'front', range(2, 7), cols=(0, 7))
    for x in range(1, 7):                       # shadowed rim of the face opening
        sk.fp('head', 'front', x, 1, MAIL['d'], True)
    for y in range(2, 7):
        sk.fp('head', 'front', 0, y, MAIL['m'], True)
        sk.fp('head', 'front', 7, y, MAIL['m'], True)
    for x in (1, 6):
        sk.fp('head', 'front', x, 7, MAIL['m'], True)
    mail_face(sk, 'head', 'bottom', range(8))
    # hauberk ----------------------------------------------------------------
    for f in SIDES + ('top',):
        mail_face(sk, 'body', f, range(12) if f != 'top' else range(4), dark=1 if f in ('right', 'left') else 0)
    for x in (3, 4):                            # neck shadow under the coif
        sk.fp('body', 'front', x, 0, MAIL['d'], True)
    # belt
    for f, w in (('front', 8), ('back', 8), ('right', 4), ('left', 4)):
        for x in range(w):
            sk.fp('body', f, x, 8, LEATHER['l'], True)
            sk.fp('body', f, x, 9, mail_px(x, 9, 1), True)
    sk.fp('body', 'front', 3, 8, LEATHER['b'], True)
    sk.fp('body', 'front', 4, 8, LEATHER['B'], True)
    for f in ('front', 'back'):                 # leather-bound hem
        for x in range(8):
            sk.fp('body', f, x, 11, mail_px(x, 11, 1), True)
    # sleeves to the elbow with a leather edge
    for f in SIDES:
        mail_face(sk, RA, f, range(7), dark=2 if f == 'left' else (1 if f == 'back' else 0))
        for x in range(4):
            sk.fp(RA, f, x, 6, LEATHER['l'], True)
    mail_face(sk, RA, 'top', range(4))
    sk.mirror_limb(RA, LA, ov=True)
    # skirt of the hauberk over the thighs
    for f in SIDES:
        mail_face(sk, RL, f, range(5), dark=2 if f == 'left' else (1 if f == 'back' else 0))
        for x in range(4):
            sk.fp(RL, f, x, 4, LEATHER['l'], True)
    sk.mirror_limb(RL, LL, ov=True)
    return sk


def make_plate():
    sk = Skin()
    S = dict(STEEL)
    S.update({'b': LEATHER['b'], 'B': LEATHER['B'], 'L': LEATHER['L'], 'l': LEATHER['l'],
              'h': MAIL['h'], 'm': MAIL['m'], 'd': MAIL['d'], 'k': MAIL['l']})
    # nasal helm with a mail aventail ------------------------------------------
    sk.face_ascii('head', 'top', ["12222221",
                                  "23333332",
                                  "23444332",
                                  "23454432",
                                  "23445432",
                                  "23344332",
                                  "22333322",
                                  "12222221"], S, ov=True)
    sk.face_ascii('head', 'front', ["34455443",
                                    "23444332",
                                    "1b1221b1",
                                    "...43...",
                                    "...32...",
                                    "...21...",
                                    "........",
                                    "........"], S, ov=True)
    sk.face_ascii('head', 'right', ["23344332",
                                    "22333322",
                                    "1b1111b1",
                                    "12222221",
                                    "11222211",
                                    "hmhmhmh.",
                                    "dkdkdk..",
                                    "mhmhm...",], S, ov=True)
    sk.mirror_head_lr(ov=True)
    sk.face_ascii('head', 'back', ["23344332",
                                   "22333322",
                                   "1b1111b1",
                                   "12222221",
                                   "11222211",
                                   "hmhmhmhm",
                                   "dkdkdkdk",
                                   "mhmhmhmh"], S, ov=True)
    # breastplate + backplate -----------------------------------------------------
    sk.face_ascii('body', 'front', ["12....21",
                                    "23444332",
                                    "23455432",
                                    "23455432",
                                    "23445432",
                                    "12344321",
                                    "12344321",
                                    "11233211",
                                    "lbllllbl",
                                    "23344332",
                                    "01111110",
                                    "23344332"], S, ov=True)
    sk.face_ascii('body', 'back', ["11111111",
                                   "12333321",
                                   "12344321",
                                   "12333321",
                                   "12333321",
                                   "11233211",
                                   "11222211",
                                   "11122111",
                                   "llllllll",
                                   "22333322",
                                   "01111110",
                                   "22333322"], S, ov=True)
    side = ["1221",
            "LllL",
            "1221",
            "1221",
            "1221",
            "1221",
            "LllL",
            "1111",
            "llll",
            "2332",
            "0110",
            "2332"]
    sk.face_ascii('body', 'right', side, S, ov=True)
    sk.face_ascii('body', 'left', side, S, ov=True)
    sk.face_ascii('body', 'top', ["11222211",
                                  "23....32",
                                  "23....32",
                                  "12222221"], S, ov=True)
    # pauldrons ------------------------------------------------------------------------
    sk.face_ascii(RA, 'top', ["2332", "3443", "3443", "2332"], S, ov=True)
    for f, rows in (('right', ["3443", "1111", "2332", "0110"]),
                    ('front', ["3443", "1111", "2332", "0110"]),
                    ('back', ["2332", "1111", "1221", "0110"]),
                    ('left', ["2222", "....", "....", "...."])):
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                if ch != '.':
                    sk.fp(RA, f, x, y, S[ch], True)
    for x in (1,):
        sk.fp(RA, 'right', x, 0, S['b'], True)
    sk.mirror_limb(RA, LA, ov=True)
    # tassets over the thighs (front + outer) -----------------------------------------
    for f, rows in (('front', ["3443", "1221", "2332", "0110"]),
                    ('right', ["2332", "1221", "2332", "0110"])):
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                sk.fp(RL, f, x, y, S[ch], True)
    sk.fp(RL, 'front', 1, 0, S['b'], True)
    sk.mirror_limb(RL, LL, ov=True)
    return sk


ARMOR = {'mail': make_mail, 'plate': make_plate}


def make_armor(aid):
    return ARMOR[aid]()
