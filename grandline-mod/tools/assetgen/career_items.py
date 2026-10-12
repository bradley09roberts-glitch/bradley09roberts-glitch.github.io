"""Career assets: the Marine coat (item sprite and the armour layer worn over the shoulders) and the Warlord's letter
of marque. Ids come from career/CareerRegistry.java (see source.item_ids); gen_assets writes textures()."""
from .canvas import Canvas, C, CLEAR, mix
from .sprite import Fixed, render
from .palette import *
from .items import item, art

COAT_WHITE = ['#2c2e36', '#70747e', '#b4b8c0', '#dfe2e6', '#f2f3f5', '#ffffff']
TRIM_BLUE = ['#0a1430', '#16285a', '#24418a', '#3462b4', '#5486d4', '#90b4ec']


@item('marine_coat')
def _marine_coat():
    # the white "Justice" coat seen from the front: wide shoulders with gold epaulettes, blue collar and cuffs,
    # hanging open over a blue shirt
    g = art([
        '................',
        '....kkk..kkk....',
        '..ggwwkkkkwwgg..',
        '.gwwwwwbbwwwwwg.',
        '.wwwwwwbbwwwwww.',
        '.www.wwbbww.www.',
        '.www.wwbbww.www.',
        '.www.wwbbww.www.',
        '.www.wwbbww.www.',
        '.ccc.wwbbww.ccc.',
        '.....wwbbww.....',
        '.....wwbbww.....',
        '.....wwbbww.....',
        '.....wwbbww.....',
        '.....wwwwww.....',
        '................',
    ])
    return render(g, {'w': M(COAT_WHITE), 'k': M(TRIM_BLUE), 'c': M(TRIM_BLUE), 'b': M(CLOTH_BLUE, outline=False, shading='flat'),
                      'g': M(GOLD)})


@item('letter_of_marque')
def _letter_of_marque():
    # a rolled parchment tied with a ribbon, sealed in red wax with the Government's mark
    g = art([
        '................',
        '..........ee....',
        '.........epp....',
        '........eppp....',
        '.......epppe....',
        '......epppe.....',
        '.....epppe......',
        '....epprre......',
        '...eprrrrr......',
        '..epprrsrr......',
        '..eppprrr.......',
        '..eppppe........',
        '..epppe.........',
        '..eppe..........',
        '...ee...........',
        '................',
    ])
    return render(g, {'p': M(PAPER), 'e': M(['#2e2414', '#5e4a2a', '#8a7650', '#a89468', '#c4b282', '#e0d4ac']),
                      'r': M(['#2a0508', '#55101a', '#8a1a24', '#b42228', '#d8443a', '#f07a68'], shading='sphere'), 's': Fixed('#f0d070')})


def coat_layer():
    """textures/models/armor/marine_coat_layer_1.png (64x32, the player-skin layout): the coat on the body and arms.
    The front hangs open (transparent down the middle: the wearer's own clothes show), the back carries the blue mark of
    justice, the sleeves end in blue cuffs and the shoulders in gold epaulettes."""
    cv = Canvas(64, 32, CLEAR)
    white, shade, edge = C('#f2f3f5'), C('#d8dbe0'), C('#b4b8c0')
    blue, dark_blue, gold, gold_dark = C('#24418a'), C('#16285a'), C('#e8c040'), C('#a87414')

    def fill(x0, y0, x1, y1, col):
        for y in range(y0, y1):
            for x in range(x0, x1):
                cv.set(x, y, col)

    def cloth(x0, y0, x1, y1):
        """White cloth with a soft fold every few pixels and a darker hem."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = white if (x - x0) % 4 != 3 else shade
                if y == y1 - 1:
                    c = edge
                cv.set(x, y, c)

    # ---- body: top (20,16)-(28,20), bottom (28,16)-(36,20), right (16,20)-(20,32), front (20,20)-(28,32),
    #      left (28,20)-(32,32), back (32,20)-(40,32)
    cloth(20, 16, 28, 20)
    fill(20, 16, 28, 17, blue)                      # the collar along the shoulders
    cloth(16, 20, 20, 32)
    cloth(28, 20, 32, 32)
    cloth(32, 20, 40, 32)
    cloth(20, 20, 28, 32)
    for y in range(21, 32):                         # open front: the lapels, then the wearer's own clothes
        for x in range(22, 26):
            cv.set(x, y, CLEAR)
    for y in range(20, 32):
        cv.set(21, y, blue if y < 24 else shade)
        cv.set(26, y, blue if y < 24 else shade)
    fill(20, 20, 28, 21, blue)                      # collar at the throat
    # the mark of justice on the back: two blue strokes over a bar
    for (x, y) in [(34, 23), (35, 23), (37, 23), (38, 23), (34, 24), (38, 24), (35, 25), (36, 25), (37, 25), (36, 26), (34, 27), (35, 27), (36, 27),
                   (37, 27), (38, 27), (36, 28)]:
        cv.set(x, y, dark_blue)

    # ---- arm: top (44,16)-(48,20), bottom (48,16)-(52,20), outer (40,20)-(44,32), front (44,20)-(48,32),
    #      inner (48,20)-(52,32), back (52,20)-(56,32); the model mirrors it for the other arm
    cloth(44, 16, 48, 20)
    fill(44, 16, 48, 18, gold)                      # epaulette
    fill(44, 18, 48, 19, gold_dark)
    cloth(48, 16, 52, 20)
    for x0 in (40, 44, 48, 52):
        cloth(x0, 20, x0 + 4, 32)
        fill(x0, 29, x0 + 4, 31, blue)              # cuffs
        fill(x0, 31, x0 + 4, 32, dark_blue)
    fill(40, 20, 44, 22, gold)                      # epaulette fringe over the shoulder
    for x in range(40, 44, 2):
        cv.set(x, 22, gold_dark)
    return cv


def textures():
    """Extra textures under assets/grandline/: {relative path: Canvas}."""
    return {'textures/models/armor/marine_coat_layer_1.png': coat_layer()}
