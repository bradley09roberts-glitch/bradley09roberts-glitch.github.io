"""NPC skin recipes, one per skin id used in npc/Archetypes.java."""
from .canvas import C, mix
from .skins import *  # noqa: F401,F403  (helpers + SKINS registry)
from .skins import k


# ============================================================================================ civilians
@npc('villager_1')
def _v1():
    s = Skin(11)
    base_body(s, 'light')
    face(s, eyes='#4a3020')
    hair(s, '#6a4424', 'short')
    shirt(s, '#5e8a3a', sleeves='short', neck='v')
    pants(s, '#6a5038')
    skirt(s, '#5e8a3a', rows=3)
    belt(s, '#8a6a3a', buckle=None)
    shoes(s, '#3e2c1e')
    return finish(s)


@npc('villager_2')
def _v2():
    s = Skin(12)
    base_body(s, 'tan')
    face(s, eyes='#2a1c14', lashes=True, blush=True)
    hair(s, '#1e1612', 'long')
    shirt(s, '#3a6aa8', sleeves='short', neck='crew')
    pants(s, '#3a6aa8')
    skirt(s, '#3a6aa8', rows=8, trim='#e8e0c8')
    belt(s, '#e8e0c8', buckle=None)
    shoes(s, '#7a4a2a', rows=1)
    return finish(s)


@npc('villager_3')
def _v3():
    s = Skin(13)
    base_body(s, 'brown')
    face(s, eyes='#1a1008')
    hair(s, '#141010', 'buzz')
    straw_hat(s)
    shirt(s, '#e6dcc0', sleeves='rolled', neck='v', buttons='#a08a60')
    pants(s, '#5a6a7a', cuff='#4a5866')
    belt(s, '#4a3018')
    shoes(s, '#5a3a1e', rows=1)
    return finish(s)


@npc('villager_4')
def _v4():
    s = Skin(14)
    base_body(s, 'pale')
    face(s, eyes='#3a6a3a', lashes=True, blush=True)
    hair(s, '#b8442a', 'bun')
    shirt(s, '#f0e6d6', sleeves='long', stripes='#e07a2a', neck='crew')
    pants(s, '#6a3e22')
    skirt(s, '#6a3e22', rows=10, trim='#4a2a16')
    shoes(s, '#3a2416')
    return finish(s)


@npc('villager_5')
def _v5():
    s = Skin(15)
    base_body(s, 'olive')
    face(s, eyes='#2a2a2a', brows='#d8d8d8')
    hair(s, '#c8c8c8', 'short')
    beard(s, '#d8d8d8', 'full')
    shirt(s, '#e8e4d8', sleeves='long', neck='crew')
    vest(s, '#6a4a2a')
    pants(s, '#3e3a36')
    belt(s, '#2a2018', buckle='#a08a50')
    shoes(s, '#2a2018')
    return finish(s)


@npc('merchant_1')
def _m1():
    s = Skin(21)
    base_body(s, 'light')
    face(s, eyes='#3a2a1a', blush=True, wide_mouth=True)
    hair(s, '#4a3424', 'buzz')
    beard(s, '#4a3424', 'mustache')
    shirt(s, '#f2f0ea', sleeves='rolled', neck='crew', collar='#ffffff')
    s.set('body', 'front', 3, 1, '#b02a2a')
    s.set('body', 'front', 4, 1, '#b02a2a')
    pants(s, '#2e2a28')
    apron(s, '#8a5a32')
    shoes(s, '#1e1814')
    return finish(s)


@npc('merchant_2')
def _m2():
    s = Skin(22)
    base_body(s, 'tan')
    face(s, eyes='#4a2a1a', lashes=True)
    hair(s, '#3a2214', 'ponytail')
    bandana(s, '#7a3a8a', dots='#e8d0f0', knot='back')
    shirt(s, '#6a3a7a', sleeves='long', neck='v')
    pants(s, '#6a3a7a')
    skirt(s, '#6a3a7a', rows=10, trim='#4a2456')
    apron(s, '#f0ece2', strap='#d8d0c0')
    shoes(s, '#4a2a1a')
    return finish(s)


@npc('guard_1')
def _guard():
    s = Skin(23)
    base_body(s, 'light')
    face(s, eyes='#2a2a3a', frown=True)
    hair(s, '#5a3a20', 'short')
    beard(s, '#5a3a20', 'stubble')
    helmet(s, '#8a8e96', rows=3, nasal=True)
    shirt(s, '#7a7e86', sleeves='long', neck='crew')
    # mail texture
    for p in ('body',) + ARMS:
        s.paint(p, lambda f, x, y, w, h: k(s.get(p, f, x, y), -0.25) if f in SIDES and (x + y) % 2 == 0 and s.get(p, f, x, y)[3] else None,
                faces=SIDES)
    coat(s, '#2a4a8a', length=4, sleeves=False, open_front=False, trim='#d8b44a')
    s.rows('body', 'front', ['.##.', '#yy#', '.##.'], {'#': '#d8b44a', 'y': '#f0e6a0'}, 2, 3, outer=True)
    gloves(s, '#5a3a20')
    pants(s, '#3a3a40')
    shoes(s, '#3a2a1e', boots=True)
    belt(s, '#3a2a1e')
    return finish(s)


@npc('master_1')
def _master():
    s = Skin(24)
    base_body(s, 'olive')
    face(s, eyes='#1a1a1a', brows='#2a2a2a', frown=True)
    hair(s, '#1a1a1e', 'topknot')
    beard(s, '#2a2a2e', 'goatee')
    shirt(s, '#ece6d6', sleeves='long', neck='deepv')
    # crossed gi lapels
    for y in range(0, 7):
        s.set('body', 'front', min(7, 1 + y // 2 + 2), y, '#cfc6b0')
    for y in range(0, 4):
        s.set('body', 'front', 5 - y // 2, y, '#cfc6b0')
    pants(s, '#1e2438', rows_body=3)
    skirt(s, '#1e2438', rows=11, trim='#141828')
    belt(s, '#141414', y=8, buckle=None, sash=True)
    shoes(s, '#f0f0f0', rows=1, sole='#5a4a3a')
    return finish(s)


# ============================================================================================ marines
def marine_base(s, tone, hair_col, style, eyes):
    base_body(s, tone)
    face(s, eyes=eyes)
    hair(s, hair_col, style)
    shirt(s, '#f4f4f2', sleeves='short', neck='v')
    sailor_collar(s, '#2a4e9a')
    neckerchief(s, '#3a6ad0')
    pants(s, '#eeeeec', rows_body=2)
    belt(s, '#2a2a2e', buckle='#d0d0d4')
    shoes(s, '#1a1a1e')
    hat_cap(s, '#f6f6f4', band='#2a4e9a', peak=False)
    for x in (2, 3, 4, 5):
        s.set('head', 'front', x, 2, '#e8eef8' if x % 2 else '#2a4e9a', outer=True)


@npc('marine_1')
def _mar1():
    s = Skin(31)
    marine_base(s, 'light', '#6a4424', 'short', '#2a3a5a')
    return finish(s)


@npc('marine_2')
def _mar2():
    s = Skin(32)
    marine_base(s, 'tan', '#1a1412', 'short', '#1a1412')
    beard(s, '#1a1412', 'stubble')
    return finish(s)


@npc('marine_3')
def _mar3():
    s = Skin(33)
    marine_base(s, 'dark', '#0e0c0c', 'buzz', '#120c08')
    return finish(s)


def suit(s, jacket, shirt_col='#f2f2f2', tie_col='#1a1a1e', pin=None):
    shirt(s, jacket, sleeves='long', neck='crew')
    for y in range(0, 6):
        for x in (2, 3, 4, 5):
            if (x in (3, 4)) or y < 2:
                s.set('body', 'front', x, y, shirt_col)
    tie(s, tie_col, rows=6)
    for y in range(0, 7):
        s.set('body', 'front', 2, y, k(C(jacket), -0.5) if y >= 2 else shirt_col)
        s.set('body', 'front', 5, y, k(C(jacket), -0.5) if y >= 2 else shirt_col)
    if pin:
        for y in range(12):
            for f in SIDES:
                for x in range(8 if f in ('front', 'back') else 4):
                    if x % 3 == 0 and f in ('front', 'back') and not (f == 'front' and 2 <= x <= 5 and y < 7):
                        s.set('body', f, x, y, pin)
    pants(s, jacket, rows_body=2)


@npc('marine_officer_1')
def _officer():
    s = Skin(34)
    base_body(s, 'light')
    face(s, eyes='#2a2a3a', frown=True)
    hair(s, '#2a2018', 'slick')
    suit(s, '#22305a', tie_col='#3a6ad0')
    shoes(s, '#121214')
    belt(s, '#121214', buckle='#d0d0d4')
    coat(s, '#f4f4f2', lining='#2a2a3a', length=5, sleeves=True, open_front=True, sleeve_rows=6)
    justice(s)
    hat_cap(s, '#f6f6f4', band='#22305a', emblem='#d8b44a')
    return finish(s)


@npc('marine_captain_1')
def _captain():
    s = Skin(35)
    base_body(s, 'pale')
    face(s, eyes='#3a5a8a', frown=True)
    hair(s, '#e0c060', 'slick')
    suit(s, '#5a1e2a', tie_col='#1a1a1e')
    shoes(s, '#121214')
    coat(s, '#f6f6f4', lining='#1a1a2a', length=9, sleeves=True, open_front=True)
    justice(s)
    return finish(s)


@npc('vice_admiral_1')
def _vice():
    s = Skin(36)
    base_body(s, 'light')
    face(s, eyes='#2a2a2a', brows='#d0d0d0', frown=True)
    hair(s, '#bcbcbc', 'short')
    beard(s, '#d0d0d0', 'mustache')
    suit(s, '#3a3e48', tie_col='#7a1a1a', pin='#5a606c')
    shoes(s, '#121214')
    coat(s, '#f6f6f4', lining='#14203a', length=10, sleeves=True, open_front=True, trim='#c8a040')
    justice(s)
    return finish(s)


@npc('admiral_1')
def _admiral():
    s = Skin(37)
    base_body(s, 'tan')
    face(s, eyes='#1a1a1a')
    hair(s, '#141216', 'slick')
    beard(s, '#141216', 'goatee')
    sunglasses(s)
    suit(s, '#7a1e2a', shirt_col='#f0f0f0', tie_col='#141414')
    shoes(s, '#0e0e10')
    coat(s, '#ffffff', lining='#101820', length=10, sleeves=True, open_front=True, trim='#d8b44a')
    for p in ARMS:  # gold epaulettes
        for x in range(4):
            s.set(p, 'top', x, 1, '#e8c050', outer=True)
            s.set(p, 'top', x, 2, '#e8c050', outer=True)
        for f in SIDES:
            s.set(p, f, 1, 0, '#f0d070', outer=True)
            s.set(p, f, 2, 0, '#c89a30', outer=True)
    justice(s)
    return finish(s)


# ============================================================================================ world government
@npc('warden_1')
def _warden():
    s = Skin(41)
    base_body(s, 'olive')
    face(s, eyes='#5a0a0a', frown=True, brows='#1a1010')
    hair(s, '#1a1010', 'short')
    shirt(s, '#1c1a22', sleeves='long', neck='crew', buttons='#c02a2a')
    pants(s, '#1c1a22', stripe='#8a1a1a')
    gloves(s, '#0e0e10', rows=3)
    shoes(s, '#0e0e10', boots=True)
    belt(s, '#5a1414', buckle='#d8b44a')
    coat(s, '#2a1e30', lining='#7a1a1a', length=7, sleeves=True, open_front=True, trim='#8a1a1a')
    helmet(s, '#2a1e30', rows=3, horns='#e8dcc8')
    return finish(s)


@npc('jailer_1')
def _jailer():
    s = Skin(42)
    base_body(s, 'light')
    face(s, eyes='#2a2a2a', frown=True)
    hair(s, '#3a3028', 'short')
    beard(s, '#3a3028', 'stubble')
    shirt(s, '#5a6878', sleeves='long', neck='crew', buttons='#c8c8d0')
    pants(s, '#3a4452')
    shoes(s, '#1a1a1e', boots=True)
    belt(s, '#2a1e16', buckle='#c8a040')
    for x in (0, 1):  # key ring on the hip
        s.set('body', 'front', x, 10, '#d8b44a')
    s.set('body', 'front', 1, 11, '#b08a2a')
    hat_cap(s, '#2a3240', band='#1a1e26', emblem='#c8c8d0')
    return finish(s)


@npc('beast_1')
def _beast():
    s = Skin(43)
    s.tone = C('#7a5434')
    fur(s, '#7a5434', belly='#b08a60')
    # face: muzzle, eyes, fangs
    s.rows('head', 'front', [
        '........',
        '........',
        '.dd..dd.',
        '.ye..ey.',
        '..mmmm..',
        '.mmnnmm.',
        '.mwkkwm.',
        '..mmmm..',
    ], {'d': '#3a2414', 'y': '#f0c830', 'e': '#1a0e06', 'm': '#a8845e', 'n': '#1a1210', 'w': '#f4f0e0', 'k': '#5a1e1e'})
    for f, x in (('front', 0), ('front', 1), ('front', 6), ('front', 7)):
        s.set('head', f, x, 0, '#5a3a20', outer=True)
        s.set('head', f, x, 1, '#7a5434', outer=True)
    for x in range(8):  # spiked collar
        s.set('body', 'front', x, 0, '#2a2a2e')
        s.set('body', 'back', x, 0, '#2a2a2e')
        if x % 2 == 0:
            s.set('body', 'front', x, 0, '#c8c8d0', outer=True)
    for f in ('right', 'left'):
        for x in range(4):
            s.set('body', f, x, 0, '#2a2a2e')
    for p in ARMS + LEGS:  # claws
        for x in (0, 2):
            s.set(p, 'front', x, 11, '#f0e8d0')
    pants(s, '#3a2a1e', rows_body=1, shorts=True)
    return finish(s)


@npc('cp_agent_1')
def _cp():
    s = Skin(44)
    base_body(s, 'pale')
    face(s, eyes='#1a1a1a', frown=True)
    hair(s, '#121214', 'slick')
    suit(s, '#141416', tie_col='#141416')
    shoes(s, '#0a0a0c')
    gloves(s, '#1a1a1e', rows=1)
    return finish(s)


@npc('cp_leader_1')
def _cp_leader():
    s = Skin(45)
    base_body(s, 'pale')
    face(s, eyes='#1a1a1a')
    hair(s, '#e8e8ec', 'short')
    suit(s, '#141416', tie_col='#7a1a1a')
    shoes(s, '#0a0a0c')
    gloves(s, '#f0f0f0', rows=2)
    coat(s, '#1a1a1e', lining='#e8e8ec', length=9, sleeves=True, open_front=True)
    mask_full(s)
    return finish(s)


# ============================================================================================ outlaws
@npc('bandit_1')
def _bandit1():
    s = Skin(51)
    base_body(s, 'tan')
    face(s, eyes='#2a1a0a', frown=True)
    hair(s, '#2a1a10', 'spiky')
    mask_lower(s, '#8a2a24')
    headband(s, '#3a2a1e')
    shirt(s, '#7a6044', sleeves='short', neck='v')
    # patches and tears
    s.rows('body', 'front', ['pp', 'pp'], {'p': '#5a6a3a'}, 5, 6)
    s.rows('body', 'back', ['pp.', 'ppp'], {'p': '#4a3a2a'}, 1, 3)
    for x in (0, 2, 5, 7):
        s.set('body', 'front', x, 9, s.tone)
    pants(s, '#4a4036', knee_patch='#6a5a40')
    belt(s, '#2a1e14', buckle='#8a8a8a')
    shoes(s, '#2a1e14', boots=True)
    return finish(s)


@npc('bandit_2')
def _bandit2():
    s = Skin(52)
    base_body(s, 'light')
    face(s, eyes='#3a4a2a', frown=True)
    hair(s, '#4a3a2a', 'short')
    beard(s, '#4a3a2a', 'stubble')
    hood(s, '#3a4630')
    mask_lower(s, '#2a2a24')
    shirt(s, '#5a5a4a', sleeves='long', neck='crew')
    coat(s, '#3a4630', lining='#222a1c', length=6, sleeves=False, open_front=True)
    for x in (1, 4, 6):  # ragged hem
        s.clear('right_leg', 'back', x % 4, 5)
        s.clear('left_leg', 'front', (x + 1) % 4, 5)
    pants(s, '#3a3228')
    belt(s, '#1e1610', buckle=None)
    shoes(s, '#1e1610', boots=True)
    return finish(s)


@npc('pirate_1')
def _pirate1():
    s = Skin(61)
    base_body(s, 'light')
    face(s, eyes='#2a3a5a', wide_mouth=True)
    hair(s, '#5a3a1e', 'short')
    beard(s, '#5a3a1e', 'stubble')
    bandana(s, '#c42a2a', dots='#f4f0e8')
    shirt(s, '#f0f0ec', sleeves='short', stripes='#2a4a9a', neck='v')
    pants(s, '#6a4a2a')
    belt(s, '#b02a2a', y=9, buckle=None, sash=True)
    shoes(s, '#2a1a10', boots=True)
    return finish(s)


@npc('pirate_2')
def _pirate2():
    s = Skin(62)
    base_body(s, 'tan')
    face(s, eyes='#2a1a0a', frown=True)
    hair(s, '#2a1a10', 'bald')
    bandana(s, '#3a6a3a', knot=None, low=0)
    eyepatch(s)
    scar(s, 5, 2, 5)
    beard(s, '#2a1a10', 'goatee')
    shirt(s, '#2a2a2a', sleeves='none', neck='crew', length=0)
    vest(s, '#1e1a16', trim='#a08040')
    for y, row in enumerate(['.a.', 'aaa', '.a.']):  # anchor tattoo on the arm
        for x, ch in enumerate(row):
            if ch == 'a':
                s.set('right_arm', 'right', x, 3 + y, '#2a4a7a')
    pants(s, '#1a1a1e')
    belt(s, '#7a1e1e', y=9, buckle=None, sash=True)
    shoes(s, '#3a2414', boots=True)
    return finish(s)


@npc('pirate_3')
def _pirate3():
    s = Skin(63)
    base_body(s, 'pale')
    face(s, eyes='#3a6a2a')
    hair(s, '#e2c25a', 'spiky')
    shirt(s, '#f2eee4', sleeves='long', neck='v')
    vest(s, '#5a2a6a', trim='#d8b44a')
    pants(s, '#22283a', cuff='#1a1e2a')
    belt(s, '#3a2414', buckle='#d8b44a')
    shoes(s, '#5a3418', boots=True)
    for x in range(4):  # bandana tied on the arm
        s.set('left_arm', 'front', x, 2, '#c42a2a', outer=True)
        s.set('left_arm', 'back', x, 2, '#c42a2a', outer=True)
        s.set('left_arm', 'left', x, 2, '#c42a2a', outer=True)
        s.set('left_arm', 'right', x, 2, '#c42a2a', outer=True)
    return finish(s)


@npc('pirate_captain_1')
def _pcap():
    s = Skin(64)
    base_body(s, 'light')
    face(s, eyes='#1a1a1a', frown=True)
    hair(s, '#141012', 'curly')
    beard(s, '#141012', 'big')
    shirt(s, '#f4f0e6', sleeves='long', neck='deepv')
    for y in (2, 3, 4):  # ruffles
        s.set('body', 'front', 3, y, '#e0d8c4')
    pants(s, '#1a1a1e')
    shoes(s, '#141012', boots=True)
    belt(s, '#2a1a10', buckle='#e8c050')
    coat(s, '#a81e24', lining='#3a0a0e', length=9, sleeves=True, open_front=True, trim='#e0b030')
    tricorn(s, '#18161a', '#e0b030', plume='#f0f0f0')
    return finish(s)


@npc('guardian_1')
def _guardian():
    s = Skin(65)
    base_body(s, 'brown')
    face(s, eyes='#f0d040', frown=True)
    hair(s, '#141010', 'long')
    for x, y in ((0, 5), (1, 5), (6, 5), (7, 5), (1, 6), (6, 6)):  # face paint
        s.set('head', 'front', x, y, '#f2ece0')
    s.set('head', 'front', 3, 2, '#c42a2a')
    s.set('head', 'front', 4, 2, '#c42a2a')
    headband(s, '#c8a040', y=1, tails=False)
    for x, col in ((1, '#c42a2a'), (3, '#2a8a4a'), (4, '#2a6ad0'), (6, '#e0b030')):  # feathers
        s.set('head', 'front', x, 0, col, outer=True)
        s.set('head', 'top', x, 7, col, outer=True)
        s.set('head', 'top', x, 6, k(C(col), 0.4), outer=True)
    # body paint + leather straps
    for y in range(12):
        s.set('body', 'front', (y // 2) % 8 if y < 8 else 3, y, '#5a3a20' if y < 8 else s.tone)
    for x in (1, 6):
        s.set('body', 'front', x, 3, '#f2ece0')
        s.set('body', 'front', x, 4, '#f2ece0')
    pants(s, '#6a4a2a', rows_body=2, shorts=True)
    skirt(s, '#7a5430', rows=5, trim='#c8a040')
    for p in ARMS:
        for f in SIDES:
            for x in range(4):
                s.set(p, f, x, 3, '#c8a040')
                s.set(p, f, x, 9, '#5a3a20')
    for p in LEGS:
        for f in SIDES:
            for x in range(4):
                s.set(p, f, x, 10, '#5a3a20')
                s.set(p, f, x, 11, '#3a2414')
    return finish(s)


# ============================================================================================ fish-men
@npc('fishman_1')
def _fish1():
    s = Skin(71)
    base_body(s, 'fish_teal')
    face(s, eyes='#f0d040', whites='#f8f4d0', mouth='#1e3a3a', wide_mouth=True)
    gills(s)
    hair(s, '#141820', 'spiky')
    shirt(s, '#e8742a', sleeves='short', neck='crew', chest_open=True)
    for x, y in ((1, 2), (6, 4), (0, 7), (7, 8), (1, 9)):  # hibiscus print
        s.set('body', 'front', x, y, '#f4e070')
    for p in ARMS:
        s.set(p, 'front', 1, 1, '#f4e070')
    pants(s, '#1e2a3a', shorts=True)
    shoes(s, '#6a4a2a', rows=1)
    return finish(s)


@npc('fishman_2')
def _fish2():
    s = Skin(72)
    base_body(s, 'fish_blue')
    face(s, eyes='#141414', whites='#e8f4f4', mouth='#1a2a4a', frown=True)
    gills(s)
    hair(s, '#1a2a4a', 'bald')
    for x in (2, 3, 4, 5):
        s.set('head', 'top', x, 3, k(s.tone, 0.35))
        s.set('head', 'top', x, 4, k(s.tone, 0.35))
    shirt(s, '#f2f0ea', sleeves='rolled', neck='deepv')
    for y in range(0, 7):
        s.set('body', 'front', min(7, 2 + y // 2 + 1), y, '#d4d0c4')
    pants(s, '#f2f0ea', rows_body=3)
    belt(s, '#141414', y=8, buckle=None, sash=True)
    return finish(s)


@npc('fishman_boss_1')
def _fishboss():
    s = Skin(73)
    base_body(s, '#4a6a94')
    face(s, eyes='#f0e040', whites='#ffffff', frown=True, nose=False)
    gills(s)
    hair(s, '#0e1218', 'spiky')
    # long saw nose + toothy grin
    s.rows('head', 'front', [
        '...nn...',
        '...nn...',
        '..nNNn..',
        '.twtwtw.',
    ], {'n': '#8aa0b8', 'N': '#b4c6d8', 't': '#f8f8f0', 'w': '#2a1a1a'}, 0, 3)
    s.rows('head', 'front', ['nn', 'NN'], {'n': '#8aa0b8', 'N': '#d0dce8'}, 3, 4, outer=True)
    shirt(s, '#f4f4f0', sleeves='short', neck='v')
    for y in range(12):  # wave print
        for x in range(8):
            if (x + y * 2) % 7 == 0:
                s.set('body', 'front', x, y, '#3a6ad0')
                s.set('body', 'back', x, y, '#3a6ad0')
    for x in (2, 3, 4, 5):
        s.set('body', 'front', x, 1, '#e8c050')
    s.set('body', 'front', 3, 2, '#e8c050')
    pants(s, '#1a1c22')
    belt(s, '#0e0e10', buckle='#e8c050')
    shoes(s, '#2a1a10', rows=1)
    return finish(s)


# ============================================================================================ giants, revolutionaries, kuja
@npc('giant_1')
def _giant():
    s = Skin(81)
    base_body(s, 'light')
    face(s, eyes='#2a4a6a', brows='#a83a1a', frown=True)
    hair(s, '#b04a1e', 'long')
    beard(s, '#b04a1e', 'big')
    helmet(s, '#7a7e86', rows=3, horns='#ece2c8')
    shirt(s, '#6a4a2a', sleeves='short', neck='crew')
    coat(s, '#8a6a44', lining='#5a4028', length=0, sleeves=False, open_front=True, cape=True)
    for f in SIDES:  # fur mantle on the shoulders
        for x in range(8 if f in ('front', 'back') else 4):
            s.set('body', f, x, 0, '#c8b090' if (x % 2) else '#a89070', outer=True)
    pants(s, '#4a3a2a')
    belt(s, '#2a1a10', buckle='#c8c8d0')
    shoes(s, '#8a6a44', boots=True)
    for p in ARMS:
        for f in SIDES:
            for x in range(4):
                s.set(p, f, x, 8, '#5a5e66')
                s.set(p, f, x, 9, '#7a7e86')
    return finish(s)


@npc('revolutionary_1')
def _rev():
    s = Skin(82)
    base_body(s, 'olive')
    face(s, eyes='#2a2a2a', frown=True)
    hair(s, '#1a1a1a', 'short')
    for y in (3, 4, 5, 6):  # tattoo down the left side of the face
        s.set('head', 'front', 6 + (y % 2), y, '#a01e1e')
    shirt(s, '#2a2e2a', sleeves='long', neck='crew')
    pants(s, '#22261e')
    belt(s, '#141410', buckle='#8a8a8a')
    shoes(s, '#141410', boots=True)
    hood(s, '#1e2a20', rim='#121812')
    coat(s, '#1e2a20', lining='#3a1414', length=10, sleeves=True, open_front=True)
    return finish(s)


@npc('kuja_1')
def _kuja():
    s = Skin(83)
    base_body(s, 'tan')
    face(s, eyes='#4a2a6a', lashes=True)
    hair(s, '#141016', 'long')
    shirt(s, '#5a2a7a', sleeves='none', neck='deepv', length=7)
    for x in range(8):
        s.set('body', 'front', x, 6, '#e0b030')
        s.set('body', 'back', x, 6, '#e0b030')
    pants(s, '#5a2a7a', rows_body=3, shorts=True)
    skirt(s, '#5a2a7a', rows=7, trim='#e0b030', slit=True)
    belt(s, '#e04a6a', y=8, buckle=None, sash=True)
    for p in ARMS:  # golden armbands
        for f in SIDES:
            for x in range(4):
                s.set(p, f, x, 2, '#e0b030')
                s.set(p, f, x, 9, '#e0b030')
    shoes(s, '#5a3a1a', rows=3)
    s.set('head', 'right', 4, 6, '#e0b030')  # earrings
    s.set('head', 'left', 3, 6, '#e0b030')
    return finish(s)


# ============================================================================================ the living sea
@npc('trader_1')
def _trader():
    """Sea merchant captain: tricorn, long blue coat with gold trim over a waistcoat."""
    s = Skin(91)
    base_body(s, 'tan')
    face(s, eyes='#3a2a1a', brows='#5a3a20')
    hair(s, '#5a3a20', 'short')
    beard(s, '#5a3a20', 'stubble')
    shirt(s, '#f0ece2', sleeves='long', neck='crew', buttons='#c8b070')
    vest(s, '#7a4a2a', trim='#d8b44a')
    pants(s, '#4a3a2e')
    belt(s, '#3a2416', buckle='#d8b44a')
    shoes(s, '#2a1a10', boots=True)
    coat(s, '#23417e', lining='#162a52', length=9, sleeves=True, open_front=True, trim='#d8b44a')
    tricorn(s, '#1c1a1e', trim='#d8b44a')
    return finish(s)


@npc('sailor_1')
def _sailor1():
    """Merchant deckhand: striped jersey, knit cap, rolled trousers."""
    s = Skin(92)
    base_body(s, 'light')
    face(s, eyes='#2a3a5a')
    hair(s, '#8a5a2a', 'short')
    beard(s, '#8a5a2a', 'stubble')
    shirt(s, '#f2f0ea', sleeves='long', stripes='#2a5a9a', neck='crew')
    neckerchief(s, '#c42a2a')
    pants(s, '#5a6a7a', cuff='#4a5866')
    belt(s, '#3a2a1e', buckle=None)
    shoes(s, '#3a2a1e')
    hat_cap(s, '#3a4a6a', peak=False)
    return finish(s)


@npc('sailor_2')
def _sailor2():
    s = Skin(93)
    base_body(s, 'brown')
    face(s, eyes='#1a1008')
    hair(s, '#141010', 'buzz')
    bandana(s, '#2e6e46', knot='back')
    shirt(s, '#d8ccb0', sleeves='rolled', neck='v')
    vest(s, '#4a3a2a')
    pants(s, '#3a4a5a', cuff='#2e3a48')
    belt(s, '#2a1e14', buckle='#8a8a8a')
    shoes(s, '#2a1e14', boots=True)
    return finish(s)


@npc('bounty_hunter_1')
def _hunter1():
    """Bounty hunter: dark duster, red scarf, battered tricorn."""
    s = Skin(94)
    base_body(s, 'olive')
    face(s, eyes='#2a2a2a', frown=True)
    hair(s, '#1a1410', 'short')
    scar(s, 2, 2, 5)
    shirt(s, '#3a3430', sleeves='long', neck='crew')
    neckerchief(s, '#9e2222')
    pants(s, '#2a2622')
    belt(s, '#1a1410', buckle='#b0b0b0')
    shoes(s, '#1a1410', boots=True)
    coat(s, '#4a3a2a', lining='#2a1e14', length=10, sleeves=True, open_front=True)
    tricorn(s, '#2a2018', trim='#9e2222')
    return finish(s)


@npc('bounty_hunter_2')
def _hunter2():
    s = Skin(95)
    base_body(s, 'pale')
    face(s, eyes='#6a2a2a', lashes=True)
    hair(s, '#c8c8c8', 'ponytail')
    sunglasses(s)
    shirt(s, '#1e1e24', sleeves='long', neck='crew')
    pants(s, '#1e1e24')
    belt(s, '#9e2222', y=9, buckle=None, sash=True)
    shoes(s, '#141418', boots=True)
    coat(s, '#5a1e1e', lining='#2a0e0e', length=8, sleeves=True, open_front=True, trim='#d8b44a')
    return finish(s)
