"""Naval combat item sprites (grappling hook, chain shot, grape shot) and their recipes.

Ids come from ship/NavalItems.java (see source.item_ids); gen_assets merges recipes() into the recipe set."""
from .sprite import Grid, Mat, Fixed, Force, render, OD, OL, DK, MD, LT, HL
from .palette import *
from .items import item
from .data import shaped, NS

SHOT = ['#050506', '#141518', '#24262b', '#3a3d44', '#565a63', '#7c818c']
BURLAP = ['#2a1d0e', '#584126', '#8a6c44', '#ae905e', '#cbb07a', '#e6d4a0']


@item('grappling_hook')
def _grappling_hook():
    g = Grid()
    # coiled line in the upper left, running down to the eye of the hook
    g.ellipse(4.4, 4.2, 4.1, 3.7, 'r', ring=0.2)
    g.ellipse(4.4, 4.2, 2.0, 1.6, None)
    g.seg(6.5, 6.5, 7.6, 7.6, 0.8, 'r')
    # eye, shank and three claws at the lower right
    g.seg(7.6, 7.6, 11.6, 11.6, 1.05, 'i')
    g.seg(11.6, 11.6, 14.4, 9.8, 0.85, 'i')
    g.seg(14.4, 9.8, 14.6, 7.2, 0.75, 'i')
    g.seg(11.6, 11.6, 9.8, 14.4, 0.85, 'i')
    g.seg(9.8, 14.4, 7.2, 14.6, 0.75, 'i')
    g.seg(11.6, 11.6, 14.4, 14.4, 0.8, 'i')
    iron = M(STEEL)
    return render(g, {'r': M(ROPE), 'i': iron})


@item('chain_shot')
def _chain_shot():
    g = Grid()
    g.disc(4.4, 11.6, 3.5, 'a')
    g.disc(11.6, 4.4, 3.5, 'b')
    for i, (x, y) in enumerate(((6, 9), (7, 8), (8, 7), (9, 6))):
        g.put(x, y, 'c' if i % 2 == 0 else 'C')
    g.put(3, 10, 's')
    g.put(10, 3, 's')
    ball = M(SHOT, shading='sphere')
    return render(g, {'a': ball, 'b': M(SHOT, shading='sphere'), 'c': M(IRON, outline=False, shading='flat'),
                      'C': Fixed('#9aa0a8'), 's': Force(ball, HL)})


@item('grape_shot')
def _grape_shot():
    g = Grid()
    g.ellipse(8.0, 10.0, 6.4, 5.6, 'b')
    g.rect(6, 3, 9, 4, 't')
    g.rect(5, 1, 6, 2, 't')
    g.rect(9, 1, 10, 2, 't')
    g.put(7, 2, 't')
    # iron balls bulging through the canvas
    for (x, y) in ((4, 8), (8, 7), (11, 9), (6, 11), (3, 12), (9, 12), (11, 13), (6, 14), (9, 10)):
        g.put(x, y, 'p')
        g.put(x + 1, y, 'P')
    sack = M(BURLAP, pattern=lambda x, y, i: DK if i == MD and (x + 2 * y) % 5 == 0 else i)
    return render(g, {'b': sack, 't': M(ROPE), 'p': Fixed('#3a3d44'), 'P': Fixed('#7c818c')})


def recipes():
    I = 'iron_ingot'
    return {
        'grappling_hook': shaped(['I I', ' I ', ' L '], {'I': I, 'L': 'lead'}, 'grappling_hook', 1),
        'chain_shot': shaped(['CNC'], {'C': f'{NS}:cannonball', 'N': 'iron_nugget'}, 'chain_shot', 2),
        'grape_shot': shaped([' S ', 'NGN', 'NNN'], {'S': 'string', 'N': 'iron_nugget', 'G': 'gunpowder'}, 'grape_shot', 2),
    }
