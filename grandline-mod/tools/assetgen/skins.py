"""64x64 NPC skins (classic 4px arms, standard player UV layout) composed from painted layers: body, face, hair,
garments, then outer-layer hats / coats / masks. Every base face is opaque; the outer layer is transparent wherever
nothing is worn."""
from .canvas import Canvas, C, mix, shade, hash01

UV = {
    'head': ((0, 0), (32, 0), (8, 8, 8)),
    'body': ((16, 16), (16, 32), (8, 12, 4)),
    'right_arm': ((40, 16), (40, 32), (4, 12, 4)),
    'left_arm': ((32, 48), (48, 48), (4, 12, 4)),
    'right_leg': ((0, 16), (0, 32), (4, 12, 4)),
    'left_leg': ((16, 48), (0, 48), (4, 12, 4)),
}
FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')
SIDES = ('right', 'front', 'left', 'back')
ARMS = ('right_arm', 'left_arm')
LEGS = ('right_leg', 'left_leg')

SKINS = {}


def npc(name):
    def deco(fn):
        SKINS[name] = fn
        return fn
    return deco


def rects(part, outer=False):
    (u, v), (uo, vo), (w, h, d) = UV[part]
    if outer:
        u, v = uo, vo
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h),
        'back': (u + 2 * d + w, v + d, w, h),
    }


def k(col, amount):
    """Shade step shortcut."""
    return shade(C(col), amount)


class Skin:
    def __init__(self, seed):
        self.c = Canvas(64, 64)
        self.seed = seed

    # ------------------------------------------------------------------------------------------ primitives
    def set(self, part, face, x, y, col, outer=False):
        x0, y0, w, h = rects(part, outer)[face]
        if 0 <= x < w and 0 <= y < h and col is not None:
            self.c.set(x0 + x, y0 + y, C(col))

    def get(self, part, face, x, y, outer=False):
        x0, y0, w, h = rects(part, outer)[face]
        return self.c.get(x0 + x, y0 + y)

    def clear(self, part, face, x, y, outer=True):
        x0, y0, w, h = rects(part, outer)[face]
        if 0 <= x < w and 0 <= y < h:
            self.c.set(x0 + x, y0 + y, (0, 0, 0, 0))

    def paint(self, part, fn, outer=False, faces=FACES):
        """fn(face, x, y, w, h) -> colour or None (leave)."""
        for face in faces:
            x0, y0, w, h = rects(part, outer)[face]
            for y in range(h):
                for x in range(w):
                    col = fn(face, x, y, w, h)
                    if col is not None:
                        self.c.set(x0 + x, y0 + y, C(col))

    def rows(self, part, face, art, pal, ox=0, oy=0, outer=False):
        """Stamp ASCII art onto one face ('.' = leave, ' ' = clear)."""
        for y, row in enumerate(art):
            for x, ch in enumerate(row):
                if ch == '.':
                    continue
                if ch == ' ':
                    self.clear(part, face, ox + x, oy + y, outer)
                else:
                    self.set(part, face, ox + x, oy + y, pal[ch], outer)

    def mirror_arm(self, outer=False):
        """Copy the right arm onto the left arm (mirrored), for symmetric outfits."""
        r, l = rects('right_arm', outer), rects('left_arm', outer)
        mapping = {'top': 'top', 'bottom': 'bottom', 'front': 'front', 'back': 'back', 'right': 'left', 'left': 'right'}
        for fr, fl in mapping.items():
            x0, y0, w, h = r[fr]
            x1, y1, _, _ = l[fl]
            for y in range(h):
                for x in range(w):
                    self.c.set(x1 + (w - 1 - x), y1 + y, self.c.get(x0 + x, y0 + y))

    def mirror_leg(self, outer=False):
        r, l = rects('right_leg', outer), rects('left_leg', outer)
        mapping = {'top': 'top', 'bottom': 'bottom', 'front': 'front', 'back': 'back', 'right': 'left', 'left': 'right'}
        for fr, fl in mapping.items():
            x0, y0, w, h = r[fr]
            x1, y1, _, _ = l[fl]
            for y in range(h):
                for x in range(w):
                    self.c.set(x1 + (w - 1 - x), y1 + y, self.c.get(x0 + x, y0 + y))


# ============================================================================================ palettes
SKIN_TONES = {
    'pale': '#f2d3bc', 'light': '#e8bc98', 'tan': '#c98f62', 'olive': '#b88456', 'brown': '#946040', 'dark': '#6a422c',
    'fish_blue': '#5d8fc4', 'fish_teal': '#4e9e9a', 'fish_grey': '#7d96a8', 'beast': '#8a6440',
}


def fabric(col, face, x, y, w, h, fold=True):
    """Subtle cloth shading: lit top edge, darker hem and sides."""
    col = C(col)
    if face == 'top':
        return k(col, 0.25)
    if face == 'bottom':
        return k(col, -0.6)
    out = col
    if face in ('right', 'left'):
        out = k(out, -0.2)
    if face == 'back':
        out = k(out, -0.1)
    if y >= h - 2 and fold:
        out = k(out, -0.25)
    if y == 0 and face != 'back':
        out = k(out, 0.2)
    return out


# ============================================================================================ body & face
def base_body(s, tone):
    t = C(SKIN_TONES.get(tone, tone))
    dark, darker, light = k(t, -0.45), k(t, -0.8), k(t, 0.35)

    def head(face, x, y, w, h):
        if face == 'top':
            return t
        if face == 'bottom':
            return dark
        col = t
        if y == 7:
            col = k(t, -0.3)
        if face in ('right', 'left') and y >= 6:
            col = dark
        return col
    s.paint('head', head)

    def limb(face, x, y, w, h):
        if face == 'bottom':
            return darker
        if face == 'top':
            return light
        col = t
        if face in ('left', 'right'):
            col = k(t, -0.15)
        if face == 'back':
            col = k(t, -0.25)
        return col
    for p in ARMS + LEGS + ('body',):
        s.paint(p, limb)
    s.tone = t


def face(s, eyes='#3a2a1a', brows=None, mouth=None, lashes=False, look=0, whites='#f4f4f4', nose=True, blush=False,
         brow_y=3, eye_y=4, wide_mouth=False, frown=False):
    t = s.tone
    brows = brows or k(t, -1.6)
    mouth = mouth or k(t, -1.2)
    ex = [(1, 2), (5, 6)]
    for i, (a, b) in enumerate(ex):
        if look == 0:
            wx, px = (a, b) if i == 0 else (b, a)
        else:
            wx, px = (a, b) if look > 0 else (b, a)
        s.set('head', 'front', wx, eye_y, whites)
        s.set('head', 'front', px, eye_y, eyes)
        if brows:
            s.set('head', 'front', a, brow_y, brows)
            s.set('head', 'front', b, brow_y, brows)
        if lashes:
            s.set('head', 'front', a if i == 0 else b, eye_y - 1, k(eyes, -1))
    if frown:
        s.set('head', 'front', 2, brow_y, brows)
        s.set('head', 'front', 5, brow_y, brows)
        s.set('head', 'front', 1, brow_y - 1, brows)
        s.set('head', 'front', 6, brow_y - 1, brows)
    if nose:
        s.set('head', 'front', 3, eye_y + 1, k(t, -0.35))
        s.set('head', 'front', 4, eye_y + 1, k(t, -0.55))
    my = eye_y + 2
    if wide_mouth:
        for x in range(2, 6):
            s.set('head', 'front', x, my, mouth)
    else:
        s.set('head', 'front', 3, my, mouth)
        s.set('head', 'front', 4, my, mouth)
    if blush:
        s.set('head', 'front', 1, eye_y + 1, mix(t, '#e07a7a', 0.45))
        s.set('head', 'front', 6, eye_y + 1, mix(t, '#e07a7a', 0.45))
    # ears
    for f in ('right', 'left'):
        x = 4 if f == 'right' else 3
        s.set('head', f, x, 4, k(t, -0.3))
        s.set('head', f, x, 5, k(t, -0.45))


def hair(s, col, style='short', outer_volume=False):
    """Hair styles: short, slick, long, ponytail, bun, topknot, curly, spiky, buzz, bald."""
    h = C(col)
    hl, dk = k(h, 0.6), k(h, -0.6)

    mid_dk = k(h, -0.3)

    def pat(x, y, top=False):
        """Strand texture: sparse highlights on the crown, faint vertical partings below (structured, not noise)."""
        if top:
            if (x + 2 * y + s.seed) % 7 == 0:
                return hl
            if (2 * x + y + s.seed) % 11 == 0:
                return mid_dk
            return h
        if y == 0 and (x + s.seed) % 3 == 0:
            return hl
        if y >= 2 and (x + s.seed) % 3 == 0:
            return mid_dk
        return h

    back_rows = {'short': 6, 'slick': 6, 'spiky': 6, 'curly': 6, 'topknot': 5, 'buzz': 0, 'bald': 0,
                 'long': 8, 'ponytail': 7, 'bun': 7}[style]

    def head(face, x, y, w, hh):
        if style == 'bald' or face == 'bottom':
            return None
        if style == 'buzz':
            if face == 'top':
                return mix(s.tone, h, 0.75 if (x + y) % 3 else 0.55)
            if y == 0 or (y == 1 and face != 'front'):
                return mix(s.tone, h, 0.6)
            return None
        if face == 'top':
            return pat(x, y, top=True)
        if face == 'front':
            if y == 0:
                return pat(x, y)
            if y == 1:
                if style == 'slick':
                    return dk if x in (0, 7) else None
                if style == 'short' and x == 4:
                    return dk
                return pat(x, y)
            if y == 2 and style in ('curly', 'spiky') and x in (0, 1, 6, 7):
                return pat(x, y)
            if style == 'long' and x in (0, 7) and y <= 6:
                return pat(x, y) if y < 5 else dk
            return None
        if face in ('right', 'left'):
            front = (x == w - 1) if face == 'right' else (x == 0)
            ear = (x in (3, 4)) and y in (4, 5)
            if style == 'long':
                return None if ear and False else pat(x, y)
            if y <= 2:
                return pat(x, y)
            if y == 3:
                return pat(x, y) if not (x in (3, 4)) else dk
            if front and y <= 4 and style not in ('slick', 'topknot'):
                return dk  # sideburn
            behind = (x <= 2) if face == 'right' else (x >= 5)
            if behind and y <= (6 if style in ('ponytail', 'bun', 'curly') else 5):
                return pat(x, y)
            return None
        if face == 'back':
            if y < back_rows:
                return pat(x, y)
            if y == back_rows and back_rows < 8:
                return dk
            return None
        return None
    s.paint('head', head)
    if style == 'long':
        # hair falls over the shoulders / upper back
        def back_hair(face, x, y, w, hh):
            if face == 'back' and y <= 4:
                return pat(x, y + 8) if y < 4 else dk
            return None
        s.paint('body', back_hair, outer=True)
    if style == 'ponytail':
        for y in range(6, 8):
            for x in (3, 4):
                s.set('head', 'back', x, y, dk, outer=True)
        def tail(face, x, y, w, hh):
            if face == 'back' and x in (3, 4) and y <= 3:
                return h if y < 3 else dk
            return None
        s.paint('body', tail, outer=True)
    if style == 'bun':
        for x, y in ((3, 0), (4, 0), (2, 1), (3, 1), (4, 1), (5, 1)):
            s.set('head', 'back', x, y, hl if x == 3 else h, outer=True)
        for x in (3, 4):
            for y in (3, 4):
                s.set('head', 'top', x, y, hl, outer=True)
    if style == 'topknot':
        for x in (3, 4):
            for y in (2, 3, 4):
                s.set('head', 'top', x, y, h if (x + y) % 2 else hl, outer=True)
        s.set('head', 'back', 3, 0, h, outer=True)
        s.set('head', 'back', 4, 0, h, outer=True)
    if style in ('spiky', 'curly') or outer_volume:
        def vol(face, x, y, w, hh):
            if face == 'top':
                return pat(x + 3, y) if hash01(x, y, s.seed + 5) < (0.75 if style == 'curly' else 0.55) else None
            if face in ('right', 'left', 'back'):
                lim = 2 if face != 'back' else 4
                if y < lim and hash01(x, y + 9, s.seed + 6) < 0.65:
                    return pat(x, y + 2)
            if face == 'front' and y == 0 and x in (0, 1, 2, 5, 6, 7) and hash01(x, 0, s.seed) < 0.7:
                return pat(x, y)
            return None
        s.paint('head', vol, outer=True)


def beard(s, col, style='full'):
    h = C(col)
    dk = k(h, -0.6)
    if style in ('full', 'big'):
        for x in range(8):
            s.set('head', 'front', x, 7, h if x % 3 else dk)
            if x in (0, 1, 6, 7):
                s.set('head', 'front', x, 6, h)
                s.set('head', 'front', x, 5, h if style == 'big' or x in (0, 7) else None)
        s.set('head', 'front', 2, 6, h)
        s.set('head', 'front', 5, 6, h)
        for f in ('right', 'left'):
            for y in range(5, 8):
                for x in range(4):
                    xx = x + (4 if f == 'right' else 0)
                    if (f == 'right' and xx >= 5) or (f == 'left' and xx <= 2):
                        s.set('head', f, xx, y, h if (x + y) % 3 else dk)
        s.set('head', 'front', 3, 6, k(s.tone, -1.4))
        s.set('head', 'front', 4, 6, k(s.tone, -1.4))
        if style == 'big':
            for x in range(1, 7):
                s.set('head', 'front', x, 7, h, outer=True)
            for x in range(8):
                s.set('head', 'bottom', x, 0, h)
            # beard spills onto the chest
            for x in range(2, 6):
                for y in range(0, 3 if x in (3, 4) else 2):
                    s.set('body', 'front', x, y, h if (x + y) % 2 else dk)
    elif style == 'stubble':
        st = mix(s.tone, h, 0.45)
        for x in range(1, 7):
            s.set('head', 'front', x, 7, st)
        s.set('head', 'front', 1, 6, st)
        s.set('head', 'front', 6, 6, st)
    elif style == 'mustache':
        for x in (2, 3, 4, 5):
            s.set('head', 'front', x, 5, h if x in (3, 4) else dk)
    elif style == 'goatee':
        s.set('head', 'front', 3, 7, h)
        s.set('head', 'front', 4, 7, h)
        s.set('head', 'front', 2, 5, dk)
        s.set('head', 'front', 5, 5, dk)
        s.set('head', 'front', 3, 5, h)
        s.set('head', 'front', 4, 5, h)


# ============================================================================================ garments
def shirt(s, col, sleeves='long', collar=None, neck='crew', stripes=None, buttons=None, length=10, chest_open=False):
    """Shirt on body + arms (base layer). sleeves: long | short | none. length: body rows covered."""
    col = C(col)

    def body(face, x, y, w, h):
        if face == 'bottom' or y >= length:
            return None
        c = fabric(col, face, x, y, w, h)
        if stripes and face != 'top' and (y + 1) % 2 == 0:
            c = fabric(stripes, face, x, y, w, h)
        if face == 'front':
            if neck == 'v' and ((y == 0 and x in (3, 4)) or (y == 1 and x in (3, 4))):
                return s.tone if y == 0 or x in (3, 4) else c
            if neck == 'deepv' and ((y <= 2 and x in (3, 4)) or (y == 0 and x in (2, 5))):
                return s.tone
            if neck == 'crew' and y == 0 and x in (2, 3, 4, 5):
                return k(col, -0.5)
            if chest_open and 2 <= x <= 5 and y < length - 2:
                return s.tone if not (x in (2, 5) and y > 4) else c
            if buttons and x == 4 and y in (2, 4, 6, 8) and y < length:
                return buttons
        return c
    s.paint('body', body, faces=('top', 'right', 'front', 'left', 'back'))
    rows = {'long': 10, 'short': 4, 'none': 0, 'rolled': 6}[sleeves]

    def arm(face, x, y, w, h):
        if face == 'bottom':
            return None
        if y < rows or (face == 'top' and rows > 0):
            c = fabric(col, face, x, y, w, h, fold=False)
            if stripes and face != 'top' and (y + 1) % 2 == 0:
                c = fabric(stripes, face, x, y, w, h, fold=False)
            if y == rows - 1 and sleeves in ('short', 'rolled'):
                c = k(col, -0.4)
            if sleeves == 'long' and y == 5 and face in ('front', 'back') and x in (1, 2):
                c = k(c, -0.25)
            return c
        return None
    for p in ARMS:
        s.paint(p, arm)
    if collar:
        for x in (1, 2, 5, 6):
            s.set('body', 'front', x, 0, collar)
        s.set('body', 'front', 2, 1, collar)
        s.set('body', 'front', 5, 1, collar)


def gloves(s, col, rows=2):
    col = C(col)
    for p in ARMS:
        s.paint(p, lambda f, x, y, w, h: (k(col, -0.4) if f == 'bottom' else fabric(col, f, x, y, w, h, fold=False)) if (y >= 12 - rows or f == 'bottom') else None,
                faces=('right', 'front', 'left', 'back', 'bottom'))


def pants(s, col, rows_body=2, cuff=None, stripe=None, shorts=False, knee_patch=None):
    col = C(col)

    def body(face, x, y, w, h):
        if face == 'bottom':
            return k(col, -0.5)
        if face == 'top':
            return None
        if y >= h - rows_body:
            return fabric(col, face, x, y, w, h)
        return None
    s.paint('body', body)
    leg_rows = 5 if shorts else 12

    def leg(face, x, y, w, h):
        if face == 'top':
            return k(col, 0.2)
        if face == 'bottom':
            return None
        if y >= leg_rows:
            return None
        c = fabric(col, face, x, y, w, h, fold=False)
        if face == 'front' and y in (6, 7) and x in (1, 2):
            c = k(c, -0.18)
        if knee_patch and face == 'front' and y in (6, 7) and x in (1, 2):
            c = knee_patch
        if stripe and face in ('right', 'left') and x == 1:
            c = stripe
        if cuff and y == leg_rows - 1:
            c = cuff
        return c
    for p in LEGS:
        s.paint(p, leg)


def shoes(s, col, rows=2, boots=False, sole=None):
    col = C(col)
    sole = C(sole) if sole else k(col, -1.0)
    top = 12 - (5 if boots else rows)

    def leg(face, x, y, w, h):
        if face == 'bottom':
            return sole
        if face == 'top':
            return None
        if y < top:
            return None
        c = fabric(col, face, x, y, w, h, fold=False)
        if y == 11:
            c = sole
        if boots and y == top:
            c = k(col, 0.35)
        if face == 'front' and y == 10:
            c = k(col, 0.3)
        return c
    for p in LEGS:
        s.paint(p, leg)


def belt(s, col, y=9, buckle='#d8b44a', sash=False):
    col = C(col)
    for f in SIDES:
        x0, y0, w, h = rects('body')[f]
        for x in range(w):
            s.set('body', f, x, y, col if not sash else (col if (x + y) % 3 else k(col, -0.3)))
            if sash:
                s.set('body', f, x, y + 1, k(col, -0.3))
    if buckle:
        s.set('body', 'front', 3, y, buckle)
        s.set('body', 'front', 4, y, k(buckle, -0.6))
    if sash:
        # knot and hanging ends on the right hip
        s.set('body', 'front', 1, y + 1, col)
        s.set('body', 'front', 1, y + 2, k(col, -0.4))
        s.set('body', 'front', 0, y + 2, col)


def apron(s, col, top=2, bib=True, strap='#5a4430'):
    col = C(col)

    def body(face, x, y, w, h):
        if face == 'front':
            if y >= top and (bib or y >= 7) and 1 <= x <= 6:
                c = fabric(col, face, x, y, w, h)
                if y == 7 and x in (2, 3, 4, 5):
                    c = k(col, -0.35)  # pocket
                return c
            if bib and y < top and x in (1, 6):
                return strap
        if face in ('right', 'left') and y == 7:
            return strap
        if face == 'back' and y == 7:
            return strap
        return None
    s.paint('body', body, outer=True)

    def leg(face, x, y, w, h):
        if face == 'front' and y < 6:
            return fabric(col, face, x, y, w, h)
        if face in ('right', 'left') and y < 6:
            inner = (face == 'left') if True else False
            return None
        return None
    for p in LEGS:
        s.paint(p, leg, outer=True)


def coat(s, col, lining=None, length=8, sleeves=True, open_front=True, trim=None, cape=False, collar_up=False,
         sleeve_rows=12, hood=False):
    """Outer-layer coat: body + arms (+ leg tails of `length` rows)."""
    col = C(col)
    lining = C(lining) if lining else k(col, -1.2)
    trim = C(trim) if trim else None

    def body(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'top':
            return fabric(col, face, x, y, w, h) if not cape or True else None
        c = fabric(col, face, x, y, w, h, fold=False)
        if face == 'front':
            if open_front and 2 <= x <= 5:
                return None
            if open_front and x in (1, 6):
                c = lining if y > 1 else c
                if trim:
                    c = trim
            if cape and y > 2 and 1 <= x <= 6:
                return None
        if trim and face != 'front' and y == h - 1:
            c = trim
        if collar_up and y == 0:
            c = k(col, 0.4)
        return c
    s.paint('body', body, outer=True)
    if sleeves:
        def arm(face, x, y, w, h):
            if face == 'bottom':
                return None
            if y >= sleeve_rows:
                return None
            c = fabric(col, face, x, y, w, h, fold=False)
            if y == sleeve_rows - 1 and trim:
                c = trim
            elif y == sleeve_rows - 1:
                c = lining
            return c
        for p in ARMS:
            s.paint(p, arm, outer=True)
    if length > 0:
        def leg(face, x, y, w, h):
            if y >= length or face in ('top', 'bottom'):
                return None
            inner = (face == 'left') if True else False
            c = fabric(col, face, x, y, w, h, fold=False)
            if y == length - 1:
                c = trim or lining
            return c
        for p in LEGS:
            s.paint(p, leg, outer=True)
        # front opening: inner faces of the legs stay open at the front
        for p, f in (('right_leg', 'front'), ('left_leg', 'front')):
            for y in range(length):
                for x in range(4):
                    edge = (x == 3) if p == 'right_leg' else (x == 0)
                    if open_front and edge:
                        s.clear(p, f, x, y)
                    elif open_front and ((x == 2 and p == 'right_leg') or (x == 1 and p == 'left_leg')):
                        s.set(p, f, x, y, trim or lining, outer=True)
        for p, f in (('right_leg', 'left'), ('left_leg', 'right')):
            for y in range(length):
                for x in range(4):
                    s.clear(p, f, x, y)


def justice(s, col='#1a1a22'):
    """Marine 'Justice' kanji on the back of the outer coat."""
    sei = ['#####', '..#..', '#.##.', '#.#..', '#####']
    gi = ['.#.#.', '#####', '..#..', '#####', '.###.', '#.#.#']
    for y, row in enumerate(sei):
        for x, ch in enumerate(row):
            if ch == '#':
                s.set('body', 'back', 2 + x, y, col, outer=True)
    for y, row in enumerate(gi):
        for x, ch in enumerate(row):
            if ch == '#':
                s.set('body', 'back', 2 + x, 6 + y, col, outer=True)


def neckerchief(s, col):
    col = C(col)
    s.set('body', 'front', 3, 1, col)
    s.set('body', 'front', 4, 1, col)
    s.set('body', 'front', 3, 2, col)
    s.set('body', 'front', 4, 2, k(col, -0.4))
    s.set('body', 'front', 3, 3, k(col, -0.3))
    s.set('body', 'front', 4, 3, col)
    s.set('body', 'front', 3, 4, col)


def tie(s, col, rows=6):
    col = C(col)
    for y in range(1, rows):
        s.set('body', 'front', 3, y, col)
        s.set('body', 'front', 4, y, k(col, -0.35) if y > 1 else col)
    s.set('body', 'front', 3, rows, k(col, -0.35))


def sailor_collar(s, col, stripe='#ffffff'):
    """Big square sailor collar: back panel + over the shoulders, V at the front."""
    col = C(col)
    for x in range(8):
        for y in range(4):
            s.set('body', 'back', x, y, col)
        s.set('body', 'back', x, 4, k(col, -0.5))
    for x in range(1, 7):
        s.set('body', 'back', x, 3, stripe)
    for x in range(8):
        s.set('body', 'top', x, 0, col)
        s.set('body', 'top', x, 1, col)
    for f in ('right', 'left'):
        for x in range(4):
            s.set('body', f, x, 0, col)
    for x, y in ((0, 0), (1, 0), (6, 0), (7, 0), (1, 1), (6, 1), (2, 2), (5, 2), (0, 1), (7, 1)):
        s.set('body', 'front', x, y, col)
    for p in ARMS:
        for x in range(4):
            s.set(p, 'top', x, 0, col)


def hat_cap(s, col, band=None, brim=None, peak=True, emblem=None):
    """Peaked cap on the outer head layer."""
    col, band = C(col), C(band) if band else None
    brim = C(brim) if brim else k(col, -0.6)

    def head(face, x, y, w, h):
        if face == 'top':
            return fabric(col, 'front', x, y, w, h)
        if face == 'bottom':
            return None
        if y <= 1:
            c = fabric(col, 'front', x, y, w, h, fold=False)
            if y == 0:
                c = k(col, 0.25)
            return c
        if y == 2:
            return band or k(col, -0.25)
        if y == 3 and face == 'front' and peak:
            return brim
        return None
    s.paint('head', head, outer=True)
    if emblem:
        s.set('head', 'front', 3, 1, emblem, outer=True)
        s.set('head', 'front', 4, 1, emblem, outer=True)


def bandana(s, col, dots=None, knot='back', low=1):
    col = C(col)

    def head(face, x, y, w, h):
        if face == 'top':
            c = fabric(col, 'front', x, y, w, h, fold=False)
            if dots and (x + 2 * y) % 4 == 0:
                c = dots
            return c
        if face == 'bottom':
            return None
        if y <= low:
            c = fabric(col, 'front', x, y, w, h, fold=False)
            if dots and (x + 2 * y) % 4 == 1:
                c = dots
            if y == low:
                c = k(c, -0.3)
            return c
        return None
    s.paint('head', head, outer=True)
    if knot == 'back':
        for x, y in ((3, 2), (4, 2), (3, 3), (5, 3), (2, 4), (5, 4)):
            s.set('head', 'back', x, y, col if y < 4 else k(col, -0.4), outer=True)


def headband(s, col, y=1, tails=True):
    col = C(col)
    for f in SIDES:
        for x in range(8):
            s.set('head', f, x, y, col, outer=True)
    if tails:
        s.set('head', 'back', 5, y + 1, col, outer=True)
        s.set('head', 'back', 6, y + 2, k(col, -0.3), outer=True)
        s.set('head', 'back', 4, y + 2, k(col, -0.3), outer=True)


def eyepatch(s, col='#141414'):
    """Patch over the character's right eye with a strap running diagonally round the head."""
    col = C(col)
    for x, y in ((1, 3), (2, 3), (1, 4), (2, 4), (2, 5)):
        s.set('head', 'front', x, y, col)
    s.set('head', 'front', 1, 3, k(col, 1.0))
    for x, y in ((0, 3), (3, 2), (4, 1), (5, 1), (6, 0), (7, 0)):
        s.set('head', 'front', x, y, col)
    for x in range(8):
        s.set('head', 'right', x, 3, col)
        s.set('head', 'back', x, 2 if x < 4 else 1, col)
        s.set('head', 'left', x, 0 if x < 4 else 1, col)


def mask_lower(s, col):
    col = C(col)
    for x in range(8):
        for y in (5, 6, 7):
            s.set('head', 'front', x, y, fabric(col, 'front', x, y, 8, 8, fold=False), outer=True)
    for f in ('right', 'left'):
        for x in range(8):
            for y in (5, 6, 7):
                s.set('head', f, x, y, k(col, -0.2), outer=True)
    for x in range(8):
        s.set('head', 'back', x, 5, k(col, -0.4), outer=True)
    s.set('head', 'back', 3, 6, col, outer=True)
    s.set('head', 'back', 4, 6, col, outer=True)


def sunglasses(s, col='#141418', lens='#2a3040'):
    for x in range(1, 7):
        s.set('head', 'front', x, 4, lens if x not in (3, 4) else col)
    s.set('head', 'front', 1, 4, col)
    s.set('head', 'front', 6, 4, col)
    s.set('head', 'front', 2, 4, '#5a6a80')
    s.set('head', 'front', 0, 4, col)
    s.set('head', 'front', 7, 4, col)


def scar(s, x, y0, y1, col=None):
    col = col or k(s.tone, -0.9)
    for y in range(y0, y1 + 1):
        s.set('head', 'front', x + (y - y0) % 2, y, col)


def skirt(s, col, rows=6, trim=None, slit=False):
    """Skirt / tunic tail on the outer leg layer."""
    col = C(col)

    def leg(face, x, y, w, h):
        if face in ('top', 'bottom') or y >= rows:
            return None
        if slit and face in ('right', 'left') and y >= 2:
            return None
        c = fabric(col, face, x, y, w, h, fold=False)
        if y == rows - 1:
            c = C(trim) if trim else k(col, -0.4)
        return c
    for p in LEGS:
        s.paint(p, leg, outer=True)


def vest(s, col, trim=None, closed=False):
    coat(s, col, lining=k(col, -0.9), length=0, sleeves=False, open_front=not closed, trim=trim)


def hood(s, col, rim=None):
    col = C(col)
    rim = C(rim) if rim else k(col, -0.6)

    def head(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'front':
            if y == 0:
                return k(col, 0.2)
            if y == 1:
                return rim
            if x in (0, 7):
                return rim
            return None
        c = fabric(col, 'front', x, y, w, h, fold=False)
        if face == 'back' and y >= 6:
            c = k(col, -0.3)
        return c
    s.paint('head', head, outer=True)


def helmet(s, col, rows=3, nasal=False, horns=None, crest=None):
    col = C(col)

    def head(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'top':
            c = k(col, 0.3) if (x + y) % 5 else k(col, 0.6)
            return c
        if y < rows:
            c = col if y else k(col, 0.4)
            if y == rows - 1:
                c = k(col, -0.5)
            return c
        if nasal and face == 'front' and x in (3, 4) and y < rows + 2:
            return k(col, -0.2)
        return None
    s.paint('head', head, outer=True)
    if horns:
        hc = C(horns)
        dk = k(hc, -0.8)
        # curved horns painted on both sides of the helmet, rising onto the crown
        for f, xs in (('right', (3, 4, 5)), ('left', (2, 3, 4))):
            for i, x in enumerate(xs):
                s.set('head', f, x, 2, dk, outer=True)
                s.set('head', f, x, 1, hc, outer=True)
                if i != 0:
                    s.set('head', f, x, 0, k(hc, 0.5), outer=True)
        for x, y in ((0, 3), (0, 4), (1, 4), (7, 3), (7, 4), (6, 4)):
            s.set('head', 'top', x, y, hc, outer=True)
        for x in (0, 7):
            s.set('head', 'front', x, 0, hc, outer=True)
            s.set('head', 'front', x, 1, dk, outer=True)
    if crest:
        for y in range(8):
            s.set('head', 'top', 3, y, crest, outer=True)
            s.set('head', 'top', 4, y, k(C(crest), -0.3), outer=True)


def tricorn(s, col='#1c1a1e', trim='#d8b44a', plume=None):
    col, trim = C(col), C(trim)

    def head(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'top':
            return col if (x + y) % 4 else k(col, 0.4)
        if face == 'front':
            if y == 0 and x in (0, 1, 6, 7):
                return trim
            if y <= 1:
                return col
            if y == 2:
                return trim
            return None
        if y <= 1:
            return col
        if y == 2:
            return trim
        return None
    s.paint('head', head, outer=True)
    if plume:
        for x, y in ((1, 0), (2, 1), (2, 0), (3, 1)):
            s.set('head', 'top', x, y, plume, outer=True)
    # turned-up brim: lighter rim on the front corners of the base hat
    for x in range(8):
        s.set('head', 'front', x, 0, col)
        s.set('head', 'top', x, 7, col)
    for f in ('right', 'left', 'back'):
        for x in range(8):
            s.set('head', f, x, 0, col)
    for x in range(8):
        for y in range(8):
            s.set('head', 'top', x, y, col if (x + y) % 5 else k(col, 0.3))


def straw_hat(s, band='#c42a2a'):
    straw, dk, lt = C('#e8c45a'), C('#b8902e'), C('#f6dc8a')

    def head(face, x, y, w, h):
        if face == 'bottom':
            return None
        if face == 'top':
            return lt if (x + y) % 3 == 0 else straw if (x * 2 + y) % 5 else dk
        if y <= 1:
            return straw if (x + y) % 2 else lt
        if y == 2:
            return band
        if y == 3:
            return dk if face != 'front' else straw
        return None
    s.paint('head', head, outer=True)


def mask_full(s, col='#f2f0ea', mark='#b02a2a', eyes_open=True):
    col = C(col)

    def head(face, x, y, w, h):
        if face != 'front':
            return None
        if eyes_open and y == 4 and x in (1, 2, 5, 6):
            return None
        c = col if y < 7 else k(col, -0.3)
        if y in (5, 6) and x in (3, 4):
            c = k(col, -0.25) if y == 5 else c
        return c
    s.paint('head', head, outer=True)
    if mark:
        s.set('head', 'front', 1, 2, mark, outer=True)
        s.set('head', 'front', 6, 2, mark, outer=True)
        s.set('head', 'front', 3, 6, k(col, -0.6), outer=True)
        s.set('head', 'front', 4, 6, k(col, -0.6), outer=True)
    # mask strap
    for f in ('right', 'left'):
        for x in range(8):
            s.set('head', f, x, 3, '#2a2a2e', outer=True)
    for x in range(8):
        s.set('head', 'back', x, 3, '#2a2a2e', outer=True)
    # eyes behind the mask are dark
    for x in (1, 2, 5, 6):
        s.set('head', 'front', x, 4, '#101014')


def fur(s, col, belly=None, stripes=False):
    col = C(col)
    dk, lt = k(col, -0.55), k(col, 0.45)

    def f(face, x, y, w, h):
        r = (x * 7 + y * 13 + s.seed) % 11
        c = col
        if r == 0:
            c = lt
        elif r in (3, 7):
            c = dk
        if face == 'bottom':
            c = dk
        return c
    for p in ('head', 'body') + ARMS + LEGS:
        s.paint(p, f)
    if belly:
        for x in range(2, 6):
            for y in range(1, 10):
                s.set('body', 'front', x, y, C(belly) if (x + y) % 4 else k(C(belly), -0.3))
    s.tone = col


def gills(s, col=None):
    col = col or k(s.tone, -1.0)
    for f in ('right', 'left'):
        xs = (5, 6) if f == 'right' else (1, 2)
        for x in xs:
            for y in (5, 6, 7):
                if (x + y) % 2 == 0:
                    s.set('head', f, x, y, col)
    for x in (0, 7):
        s.set('head', 'front', x, 6, col)


def emblem(s, part, face, art, pal, ox, oy, outer=False):
    s.rows(part, face, art, pal, ox, oy, outer)


def finish(s):
    """Guarantee every base face is opaque (fill any gaps with the skin tone)."""
    for p in UV:
        for f, (x0, y0, w, h) in rects(p).items():
            for y in range(h):
                for x in range(w):
                    if s.c.get(x0 + x, y0 + y)[3] != 255:
                        s.c.set(x0 + x, y0 + y, getattr(s, 'tone', C('#c89a7a')))
    return s.c
