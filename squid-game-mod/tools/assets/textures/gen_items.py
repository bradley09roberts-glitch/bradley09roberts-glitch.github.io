"""Flat 16x16 item textures: marble, recruiter card, invisible-wall icon."""
from __future__ import annotations

import numpy as np

from common import Canvas, Out, art, dilate, mix, rng
import paint

# --------------------------------------------------------------------------- marble
MARBLE_BODY = [(26, 58, 120), (40, 92, 164), (70, 138, 206), (122, 190, 240), (196, 232, 252)]
MARBLE_SWIRL = [(168, 70, 12), (226, 116, 22), (250, 172, 44), (255, 214, 100), (255, 240, 176)]


def marble() -> Canvas:
    """Cat's-eye glass bead: blue glass body with an orange swirl inside, bright highlight."""
    c = paint.glass_marble(16, 8.0, 8.4, 5.4, MARBLE_BODY, MARBLE_SWIRL, "item_marble", outline=(22, 36, 70),
                           swirl_turns=0.55)
    # specular highlight + small secondary glint
    for (x, y) in [(5, 5), (6, 4), (5, 6)]:
        c.px(x, y, (255, 255, 255))
    c.px(6, 5, (236, 248, 255))
    c.px(10, 11, (210, 238, 255))
    return c


# --------------------------------------------------------------------------- recruiter card
def recruiter_card() -> Canvas:
    paper = (244, 242, 236)
    paper_shade = (186, 182, 174)
    ink = (44, 40, 62)
    ink2 = (60, 56, 84)
    pink = (250, 104, 160)
    red = (226, 56, 84)
    c = Canvas(16)
    # card body 16x10 at y 3..12, white border, dark navy face
    c.rect(0, 3, 16, 13, paper)
    c.rect(1, 4, 15, 12, ink)
    c.rect(1, 4, 15, 5, ink2)
    # drop edge under the card for depth
    c.rect(1, 12, 15, 13, paper_shade)
    # circle, triangle, square in a row (4x4 each, one-pixel gaps)
    circle = ["XX..", "XXXX", "XXXX", ".XX."]
    circle = [".XX.", "XXXX", "XXXX", ".XX."]
    triangle = [".XX.", ".XX.", "XXXX", "XXXX"]
    square = ["XXXX", "XXXX", "XXXX", "XXXX"]
    for x0, shp, col in ((2, circle, pink), (6, triangle, red), (10, square, pink)):
        for dy, row in enumerate(shp):
            for dx, ch in enumerate(row):
                if ch == "X":
                    c.px(x0 + dx, 5 + dy, col)
    # tiny phone-number dashes
    for x in range(3, 13, 2):
        c.px(x, 10, (200, 196, 214))
    return c


# --------------------------------------------------------------------------- invisible wall icon
def invisible_wall_icon() -> Canvas:
    """Dashed pale-cyan frame crossed by a slash: an admin-only 'barrier' marker."""
    c = Canvas(16)
    col = (196, 238, 248)
    for i in range(1, 15):
        if i % 3 != 0:
            c.px(i, 1, col)
            c.px(i, 14, col)
            c.px(1, i, col)
            c.px(14, i, col)
    for i in range(3, 13):
        c.px(i, i, (255, 120, 170))
        c.px(i + 1, i, (230, 84, 140))
    for (x, y) in [(5, 4), (4, 5), (11, 10), (10, 11)]:
        c.px(x, y, (232, 250, 255))
    return c


def generate(out: Out) -> None:
    out.png("textures/item/marble.png", marble())
    out.png("textures/item/recruiter_card.png", recruiter_card())
    out.png("textures/item/invisible_wall.png", invisible_wall_icon())


if __name__ == "__main__":
    o = Out()
    generate(o)
    print(f"wrote {len(o.written)} files")
