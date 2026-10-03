"""Detailed 64x64 skins for humanoid creatures (zombies, town NPCs, goblins, casters).

Painted in the style of good hand-made Minecraft skins: every face has a light top and darker bottom, the edges
of each box are shaded so limbs read as round, hair is drawn as strands with an uneven fringe, faces have eye
whites, irises, brows, a nose shadow and lips, and clothes have folds, seams, collars, buttons, belts with
buckles, rolled sleeves, scuffed knees and laced boots. Zombies get torn clothes, sickly blotches and blood.

Layout is the vanilla skin layout (head 0,0; hat 32,0; body 16,16; right arm 40,16; right leg 0,16); goblin
ears use the free area at 56,16. Original art only.
"""
import zlib
import random

from pixelart import hexc, shade


def box_faces(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


class Painter:
    def __init__(self, canvas, seed):
        self.c = canvas
        self.rnd = random.Random(seed)

    def shaded(self, rect, color, top=1.1, bottom=0.82, edge=0.9, noise=0.04, round_edges=True):
        """Fills a face with a vertical light gradient, darker side edges and fine noise."""
        x0, y0, w, h = rect
        for x in range(w):
            for y in range(h):
                t = y / max(1, h - 1)
                f = top + (bottom - top) * t
                if round_edges and (x == 0 or x == w - 1):
                    f *= edge
                f *= 1 + self.rnd.uniform(-noise, noise)
                self.c.set(x0 + x, y0 + y, shade(color, f))

    def box(self, u, v, w, h, d, color, faces=('top', 'bottom', 'right', 'front', 'left', 'back'), **kw):
        for name, rect in box_faces(u, v, w, h, d).items():
            if name in faces:
                if name == 'top':
                    self.shaded(rect, color, 1.15, 1.05, 1.0, round_edges=False)
                elif name == 'bottom':
                    self.shaded(rect, color, 0.75, 0.7, 1.0, round_edges=False)
                else:
                    self.shaded(rect, color, **kw)

    def px(self, x, y, color):
        self.c.set(x, y, color)

    def get(self, x, y):
        return self.c.get(x, y)


def copy_rect(P, src, dst, test=lambda x, y: True):
    (sx, sy, w, h), (dx, dy) = src, dst
    for x in range(w):
        for y in range(h):
            if test(x, y):
                P.px(dx + x, dy + y, P.get(sx + x, sy + y))


def overlay(P, hair_style, hat, jacket, robe, straps, suspenders, short_sleeves, torn, hair_px):
    """Paints the outer layer (hat 32,0; jacket 16,32; right sleeve 40,32; right trousers 0,32) from the base layer:
    only the parts that should stand out from the body are copied, the rest stays transparent."""
    rnd = P.rnd
    head = box_faces(0, 0, 8, 8, 8)
    hat_layer = box_faces(32, 0, 8, 8, 8)
    if hair_px and not hat:
        for name in ('top', 'right', 'left', 'back', 'front'):
            x0, y0, w, h = hat_layer[name]
            bx, by = head[name][0], head[name][1]
            for x in range(w):
                depth = {'top': h, 'front': 1 + (x % 3 == 0), 'back': 5 + rnd.choice((0, 1)), 'right': 3, 'left': 3}[name]
                for y in range(min(h, depth)):
                    if name == 'top' or rnd.random() < 0.9:
                        P.px(x0 + x, y0 + y, P.get(bx + x, by + y))
    body = box_faces(16, 16, 8, 12, 4)
    jack = box_faces(16, 32, 8, 12, 4)
    for name in ('front', 'back', 'right', 'left'):
        src, dst = body[name], jack[name]
        w = src[2]
        def keep(x, y, name=name, w=w):
            if y >= 10:
                return True                                    # belt and buckle
            if name == 'front' and y == 0 and 2 <= x <= 5:
                return True                                    # collar
            if jacket:
                return not (name == 'front' and 3 <= x <= 4)  # the coat, open at the front
            if robe and y >= 8:
                return True                                    # robe hem
            if (straps or suspenders) and name in ('front', 'back'):
                return False
            return False
        copy_rect(P, src, dst[:2], keep)
    if straps or suspenders:
        # the straps' own colour is copied wherever it was painted on the base
        for name in ('front', 'back'):
            src, dst = body[name], jack[name]
            ref = None
            for x in range(src[2]):
                for y in range(src[3] - 2):
                    c = P.get(src[0] + x, src[1] + y)
                    col = hexc(straps or suspenders)
                    if c[:3] == col[:3]:
                        P.px(dst[0] + x, dst[1] + y, c)
    if torn:
        for name in ('front', 'back'):
            src, dst = body[name], jack[name]
            for _ in range(6):                                 # loose flaps of the torn shirt
                x, y = rnd.randrange(src[2]), rnd.randrange(4, src[3])
                P.px(dst[0] + x, dst[1] + y, P.get(src[0] + x, src[1] + y))
    arm = box_faces(40, 16, 4, 12, 4)
    sleeve = box_faces(40, 32, 4, 12, 4)
    cut = 5 if short_sleeves else 10
    for name in ('front', 'back', 'right', 'left'):
        copy_rect(P, arm[name], sleeve[name][:2], lambda x, y: y == cut - 1 or (jacket and y < cut))
    leg = box_faces(0, 16, 4, 12, 4)
    pants = box_faces(0, 32, 4, 12, 4)
    for name in ('front', 'back', 'right', 'left'):
        copy_rect(P, leg[name], pants[name][:2], lambda x, y, name=name: y in (9, 10) or (name == 'front' and y in (6, 7)))


def skin_texture(skin, hair, shirt, pants, shoes='#3A2A20', eyes=(30, 30, 40, 255), beard=None, hat=None, hat_band=None,
                 torn=False, bony=False, short_sleeves=True, cross=False, lamp=False, jacket=None, straps=None, suspenders=None,
                 goggles=None, robe=False, hair_style='short', ears=False, belt='#3A2A1E', buckle='#C8A040', iris=None, blood=False,
                 apron=None, collar=None):
    """Returns a painter for a 64x64 humanoid skin. Colours are hex strings; eyes is the iris RGBA."""
    def paint(c):
        P = Painter(c, zlib.crc32(repr((skin, hair, shirt, pants)).encode()) & 0xFFFF)
        sk, hr, sh, pa, sho = hexc(skin), hexc(hair), hexc(shirt), hexc(pants), hexc(shoes)
        rnd = P.rnd
        eye_col = iris if iris is not None else eyes

        # ---- head: skin with shading, then hair
        P.box(0, 0, 8, 8, 8, sk, top=1.08, bottom=0.88, edge=0.92)
        faces = box_faces(0, 0, 8, 8, 8)
        fx, fy = faces['front'][0], faces['front'][1]
        hair_ramp = [shade(hr, 0.7), shade(hr, 0.85), hr, shade(hr, 1.15), shade(hr, 1.3)]

        def hair_px(x, y, sx):
            # vertical strands: each column keeps its shade, with a highlight near the top
            k = (x * 7 + sx + (y // 3)) % 5
            if y == 1 and k in (1, 2):
                return hair_ramp[4]
            return hair_ramp[1] if k == 0 else hair_ramp[3] if k == 1 else hair_ramp[2] if k < 4 else hair_ramp[0]

        if hair_style != 'bald':
            tx, ty, tw, td = faces['top']
            for x in range(tw):
                for y in range(td):
                    P.px(tx + x, ty + y, hair_px(x, y, 1))
            long_hair = hair_style == 'long'
            for name in ('right', 'left', 'back'):
                x0, y0, w, h = faces[name]
                for x in range(w):
                    depth = (8 if long_hair else 5) if name == 'back' else (6 if long_hair else 3) + (1 if x in (w - 1, 0) and name != 'back' else 0)
                    depth += rnd.choice((0, 0, 1))
                    for y in range(min(h, depth)):
                        P.px(x0 + x, y0 + y, hair_px(x, y, 2))
            # fringe with an uneven edge
            for x in range(8):
                length = 1 + ((x * 5 + 3) % 3 == 0) + (x in (0, 7))
                if long_hair and x in (0, 7):
                    length = 6
                for y in range(length):
                    P.px(fx + x, fy + y, hair_px(x, y, 3))
        # ---- face
        brow = shade(hr, 0.8) if hair_style != 'bald' else shade(sk, 0.7)
        white = (240, 240, 236, 255)
        if robe and hair_style == 'hood':
            pass
        for ex, inner in ((1, 2), (5, 5)):           # eyes: white + iris on the inner side
            outer = ex if ex == 1 else 6
            P.px(fx + outer, fy + 4, white)
            P.px(fx + inner, fy + 4, eye_col)
            P.px(fx + outer, fy + 3, brow)
            P.px(fx + inner, fy + 3, brow)
        P.px(fx + 3, fy + 5, shade(sk, 0.82)); P.px(fx + 4, fy + 5, shade(sk, 0.78))     # nose shadow
        P.px(fx + 3, fy + 6, shade(sk, 0.62)); P.px(fx + 4, fy + 6, shade(sk, 0.66))     # mouth
        P.px(fx + 2, fy + 6, shade(sk, 0.8)); P.px(fx + 5, fy + 6, shade(sk, 0.8))
        for y in range(2, 8):                                                              # cheek shading
            P.px(fx, fy + y, shade(P.get(fx, fy + y), 0.92)); P.px(fx + 7, fy + y, shade(P.get(fx + 7, fy + y), 0.92))
        if torn and not ears:   # zombie: sunken dark eye sockets, glowing iris, a torn lip
            for ex in (1, 2, 5, 6):
                P.px(fx + ex, fy + 4, shade(sk, 0.45))
            P.px(fx + 2, fy + 4, eye_col); P.px(fx + 5, fy + 4, eye_col)
            P.px(fx + 4, fy + 7, shade(sk, 0.55)); P.px(fx + 2, fy + 6, (90, 20, 20, 255))
            for _ in range(5):
                P.px(fx + rnd.randint(0, 7), fy + rnd.randint(2, 7), shade(sk, 0.75))
        if beard:
            bd = hexc(beard)
            br = [shade(bd, 0.8), bd, shade(bd, 1.12)]
            for x in range(8):
                for y in range(5, 8):
                    if not (y == 5 and x in (3, 4)):
                        P.px(fx + x, fy + y, br[(x + y * 2) % 3])
            P.px(fx + 3, fy + 6, shade(sk, 0.55)); P.px(fx + 4, fy + 6, shade(sk, 0.55))
            for name in ('right', 'left'):
                x0, y0, w, h = faces[name]
                for x in range(w):
                    for y in range(5, 8):
                        P.px(x0 + x, y0 + y, br[(x + y) % 3])
        if goggles:
            g = hexc(goggles)
            for x in range(8):
                P.px(fx + x, fy + 3, shade(g, 0.6))
            for ex in (1, 4):
                for dx in range(3):
                    P.px(fx + ex + dx, fy + 4, g if dx != 1 else shade(g, 1.4))
            for name in ('right', 'left', 'back'):
                x0, y0, w, h = faces[name]
                for x in range(w):
                    P.px(x0 + x, y0 + 4, shade(g, 0.5))
        # ---- hat layer
        if hat:
            ht = hexc(hat)
            for name, rect in box_faces(32, 0, 8, 8, 8).items():
                x0, y0, w, h = rect
                if name == 'top':
                    P.shaded(rect, ht, 1.15, 1.0, 1.0, round_edges=False)
                elif name != 'bottom':
                    P.shaded((x0, y0, w, 3), ht, 1.1, 0.9)
                    if hat_band:
                        for x in range(w):
                            P.px(x0 + x, y0 + 2, hexc(hat_band))
            if cross:
                for x, y in ((43, 9), (44, 9), (43, 8), (43, 10), (42, 9)):
                    P.px(x, y, hexc('#E03030'))
            if lamp:
                for x, y in ((43, 9), (44, 9), (43, 10), (44, 10)):
                    P.px(x, y, hexc('#FFF8C0'))

        # ---- body: shirt with folds, collar, buttons, optional jacket/robe/apron, belt
        body = box_faces(16, 16, 8, 12, 4)
        P.box(16, 16, 8, 12, 4, sh, top=1.1, bottom=0.85, edge=0.86)
        bx, by = body['front'][0], body['front'][1]
        for x in range(8):
            for y in range(12):
                if (x - y) % 6 == 0 and 2 < y < 10 and x not in (0, 7):
                    P.px(bx + x, by + y, shade(P.get(bx + x, by + y), 0.86))       # fabric folds
        collar_col = hexc(collar) if collar else shade(sh, 1.2)
        for x in (2, 3, 4, 5):
            P.px(bx + x, by, collar_col)
        P.px(bx + 3, by + 1, shade(sk, 0.9)); P.px(bx + 4, by + 1, shade(sk, 0.9))        # neck
        if jacket:
            jk = hexc(jacket)
            for name in ('front', 'back', 'right', 'left'):
                x0, y0, w, h = body[name]
                for x in range(w):
                    for y in range(h):
                        if name == 'front' and 3 <= x <= 4 and y < 10:
                            continue           # the open front shows the shirt
                        f = 1.1 - 0.25 * y / 11
                        if x in (0, w - 1):
                            f *= 0.86
                        P.px(x0 + x, y0 + y, shade(jk, f))
                if name == 'front':
                    for y in range(10):
                        P.px(x0 + 2, y0 + y, shade(jk, 1.25))   # lapels
                        P.px(x0 + 5, y0 + y, shade(jk, 1.25))
        else:
            for y in (3, 6):
                P.px(bx + 4, by + y, shade(sh, 0.65))           # buttons
        if robe:
            rb = hexc(robe) if isinstance(robe, str) else sh
            for x in range(8):
                P.px(bx + x, by + 11, shade(rb, 0.7))
        if apron:
            ap = hexc(apron)
            for x in range(1, 7):
                for y in range(5, 12):
                    P.px(bx + x, by + y, shade(ap, 1.05 - 0.15 * y / 11))
            P.px(bx + 1, by + 5, shade(ap, 0.7)); P.px(bx + 6, by + 5, shade(ap, 0.7))
        if straps:   # a bandolier across the chest
            st = hexc(straps)
            for i in range(10):
                P.px(bx + 7 - i * 7 // 9, by + 1 + i, st)
                P.px(body['back'][0] + i * 7 // 9, body['back'][1] + 1 + i, st)
        if suspenders:
            sp = hexc(suspenders)
            for y in range(10):
                P.px(bx + 1, by + y, sp); P.px(bx + 6, by + y, sp)
                P.px(body['back'][0] + 1, body['back'][1] + y, sp); P.px(body['back'][0] + 6, body['back'][1] + y, sp)
        if cross:
            for x, y in ((3, 3), (4, 3), (3, 2), (4, 2), (3, 4), (4, 4), (2, 3), (5, 3)):
                P.px(bx + x, by + y, hexc('#E03030'))
        bl, bk = hexc(belt), hexc(buckle)
        for name in ('front', 'back', 'right', 'left'):
            x0, y0, w, h = body[name]
            for x in range(w):
                P.px(x0 + x, y0 + 10, shade(bl, 1.05)); P.px(x0 + x, y0 + 11, shade(bl, 0.8))
        P.px(bx + 3, by + 10, bk); P.px(bx + 4, by + 10, bk); P.px(bx + 3, by + 11, shade(bk, 0.75)); P.px(bx + 4, by + 11, shade(bk, 0.75))

        # ---- arms: sleeves (rolled on short sleeves), skin forearms, hands
        sleeve = hexc(jacket) if jacket else sh
        arm_faces = box_faces(40, 16, 4, 12, 4)
        P.box(40, 16, 4, 12, 4, sleeve, top=1.1, bottom=0.85, edge=0.84)
        cut = 5 if short_sleeves else 10
        for name in ('front', 'back', 'right', 'left', 'bottom'):
            x0, y0, w, h = arm_faces[name]
            if name == 'bottom':
                P.shaded((x0, y0, w, h), sk, 0.85, 0.8, 1.0, round_edges=False)
                continue
            for x in range(w):
                for y in range(cut, h):
                    f = 1.02 - 0.18 * (y - cut) / max(1, h - cut)
                    if x in (0, w - 1):
                        f *= 0.88
                    P.px(x0 + x, y0 + y, shade(sk, f))
                P.px(x0 + x, y0 + cut - 1, shade(sleeve, 1.25))          # rolled cuff
                P.px(x0 + x, y0 + h - 1, shade(sk, 0.78))                # knuckles
        # ---- legs: trousers with seams and scuffed knees, laced boots
        leg_faces = box_faces(0, 16, 4, 12, 4)
        P.box(0, 16, 4, 12, 4, pa, top=1.0, bottom=0.82, edge=0.84)
        for name in ('front', 'back', 'right', 'left'):
            x0, y0, w, h = leg_faces[name]
            if name == 'front':
                P.px(x0 + 1, y0 + 6, shade(pa, 1.15)); P.px(x0 + 2, y0 + 6, shade(pa, 1.15))   # knee wear
                P.px(x0 + 1, y0 + 7, shade(pa, 0.85))
            for y in range(h):
                P.px(x0 + w - 1, y0 + y, shade(P.get(x0 + w - 1, y0 + y), 0.92))              # seam
            boot_top = 9
            for x in range(w):
                for y in range(boot_top, h):
                    f = 1.12 if y == boot_top else 1.0 if y < h - 1 else 0.6
                    P.px(x0 + x, y0 + y, shade(sho, f))
            if name == 'front':
                P.px(x0 + 1, y0 + boot_top + 1, shade(sho, 1.35)); P.px(x0 + 2, y0 + boot_top + 1, shade(sho, 1.35))  # laces
        P.shaded(leg_faces['bottom'][:4], sho, 0.6, 0.55, 1.0, round_edges=False)

        # ---- zombies: torn clothes showing skin, blood stains
        if torn:
            for rect in (body['front'], body['back'], arm_faces['front'], leg_faces['front']):
                x0, y0, w, h = rect
                for _ in range(max(3, w * h // 12)):
                    x, y = x0 + rnd.randrange(w), y0 + rnd.randrange(h)
                    P.px(x, y, shade(sk, rnd.uniform(0.8, 1.0)))
                for x in range(w):           # ragged hem
                    if rnd.random() < 0.5:
                        P.px(x0 + x, y0 + h - 2, shade(sk, 0.9))
        if blood or (torn and not ears):
            red = (130, 20, 20, 255) if not blood else (170, 25, 25, 255)
            for rect in (faces['front'], body['front'], arm_faces['front']):
                x0, y0, w, h = rect
                for _ in range(3 if blood else 1):
                    x, y = x0 + rnd.randrange(w), y0 + rnd.randrange(2, h)
                    P.px(x, y, red); P.px(x, min(y0 + h - 1, y + 1), shade(red, 0.8))
        # ---- the second (3D) layer, like player skins: hair volume, coat, belt, cuffs and boot tops stand off the body
        overlay(P, hair_style, hat, jacket, robe, straps, suspenders, short_sleeves, torn, hair_px if hair_style != 'bald' else None)
        # left arm/leg have their own texture in the player layout: copy the right ones
        for (sx, sy), (dx, dy) in (((40, 16), (32, 48)), ((0, 16), (16, 48)), ((40, 32), (48, 48)), ((0, 32), (0, 48))):
            for x in range(16):
                for y in range(16):
                    P.px(dx + x, dy + y, P.get(sx + x, sy + y))
        # ---- goblin ears (cube texture at 56,16: 3x2x1)
        if ears:
            for v in (16, 20):
                for name, rect in box_faces(56, v, 3, 2, 1).items():
                    P.shaded(rect, sk, 1.05, 0.85, 0.9)
                    x0, y0, w, h = rect
                    if name in ('front', 'back') and w >= 3:
                        P.px(x0 + 1, y0 + 1, shade(sk, 0.7))
    return paint
