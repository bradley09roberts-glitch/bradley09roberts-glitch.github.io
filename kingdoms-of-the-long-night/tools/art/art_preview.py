"""Human-facing preview sheets (nearest-neighbour upscales + composited citizens)."""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from art_skin import compose, flat_view, render3d, tinted, used_mask

BG = (38, 42, 50, 255)
PANEL = (58, 63, 74, 255)
TEXT = (226, 222, 205)
SUB = (150, 158, 172)

# Example citizens: (body, clothes, clothes tint, hair, hair tint, armour or None)
CITIZENS = [
    ('human_1', 'noble', '#8a2a2e', 'short', '#e2c27e', None),
    ('human_2', 'tunic', '#6a8a4a', 'beard_short', '#6e4a2e', None),
    ('human_3', 'smock', '#d8cba8', 'hood_short', '#4a3020', None),
    ('human_4', 'apron', '#8a5a3a', 'bald', '#2a2420', None),
    ('human_5', 'robe', '#5a3a7a', 'long', '#2a2220', None),
    ('human_6', 'dress', '#3a5a9a', 'bun', '#201818', None),
    ('elf_1', 'dress', '#5e8a5a', 'long', '#ece2c4', None),
    ('elf_2', 'traveller', '#4e5e3a', 'braided', '#a04a26', None),
    ('elf_3', 'robe', '#2e3e70', 'topknot', '#dcdce6', None),
    ('dwarf_1', 'apron', '#7a4a2a', 'beard_long', '#b8542a', None),
    ('dwarf_2', 'gambeson', '#8a3a30', 'beard_long', '#a6a4a0', 'mail'),
    ('dwarf_3', 'fur', '#6a5040', 'beard_short', '#2a2420', None),
    ('halfling_1', 'smock', '#c8a860', 'wild', '#8a5a30', None),
    ('halfling_2', 'tunic', '#a07040', 'short', '#5a3a20', None),
    ('halfling_3', 'dress', '#b05a40', 'bun', '#3a2418', None),
    ('orc_1', 'fur', '#5a4a3a', 'wild', '#2a2a22', None),
    ('orc_2', 'gambeson', '#5a5a4a', 'topknot', '#1e1e1a', 'plate'),
    ('orc_3', 'rags', '#7a7060', 'bald', '#2a2420', None),
    ('goblin_1', 'rags', '#6a6040', 'wild', '#3a3a20', None),
    ('goblin_2', 'traveller', '#5a4a6a', 'hood_short', '#2a2a20', None),
    ('human_2', 'gambeson', '#6a3030', 'hood_short', '#3a2a1a', 'mail'),
    ('human_4', 'noble', '#34467a', 'beard_short', '#3a2a1a', 'plate'),
]


def _font(size, bold=False):
    name = 'DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf'
    try:
        return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/' + name, size)
    except OSError:
        return ImageFont.load_default()


def assemble(layers, combo):
    body, clothes, ct, hair, ht, armor = combo
    stack = [layers['body'][body], tinted(layers['clothes'][clothes], ct), tinted(layers['hair'][hair], ht)]
    if armor:
        stack.append(layers['armor'][armor])
    return compose(stack)


def _label(draw, x, y, lines, w):
    for i, (txt, font, col) in enumerate(lines):
        tw = draw.textlength(txt, font=font)
        draw.text((x + (w - tw) / 2, y + i * 14), txt, font=font, fill=col)


def citizens_sheet(layers, out_front, out_back, scale=6):
    cols = 11
    cw, ch = 16 * scale + 30, 32 * scale + 62
    rows = (len(CITIZENS) + cols - 1) // cols
    for side, out in (('front', out_front), ('back', out_back)):
        sheet = Image.new('RGBA', (cols * cw + 20, rows * ch + 60), BG)
        d = ImageDraw.Draw(sheet)
        d.text((14, 12), f'Assembled citizens ({side} view, {scale}x): body + tinted clothes + tinted hair [+ armour]',
               font=_font(15, True), fill=TEXT)
        for i, combo in enumerate(CITIZENS):
            x, y = 10 + (i % cols) * cw, 44 + (i // cols) * ch
            d.rectangle((x, y, x + cw - 6, y + ch - 6), fill=PANEL)
            tex = assemble(layers, combo)
            fv = Image.fromarray(flat_view(tex, side)).resize((16 * scale, 32 * scale), Image.Resampling.NEAREST)
            sheet.alpha_composite(fv, (x + 8, y + 6))
            body, clothes, ct, hair, ht, armor = combo
            _label(d, x, y + 32 * scale + 10, [(body, _font(11, True), TEXT),
                                                (f'{clothes}', _font(10), SUB),
                                                (f'{hair}' + (f' + {armor}' if armor else ''), _font(10), SUB)],
                   cw - 6)
        sheet.save(out)


def citizens_3d(layers, out, picks=(0, 2, 5, 6, 7, 9, 10, 12, 15, 16, 18, 21), scale=7):
    """Top row: front three-quarter views; bottom row: the same citizens from behind."""
    cw, ch = 24 * scale, 40 * scale
    sheet = Image.new('RGBA', (len(picks) * cw + 20, 2 * ch + 100), BG)
    d = ImageDraw.Draw(sheet)
    d.text((14, 12), '3D preview on the player model (z-buffered, nearest-neighbour): front and back three-quarter',
           font=_font(15, True), fill=TEXT)
    for i, idx in enumerate(picks):
        tex = assemble(layers, CITIZENS[idx])
        x = 10 + i * cw
        sheet.alpha_composite(render3d(tex, scale=scale, yaw=28, pitch=10), (x, 40))
        sheet.alpha_composite(render3d(tex, scale=scale, yaw=208, pitch=10), (x, 60 + ch))
        body, clothes, _, hair, _, armor = CITIZENS[idx]
        _label(d, x, 40 + ch - 18, [(f'{body} {clothes}', _font(11, True), TEXT)], cw)
    sheet.save(out)


def _checker(w, h, s=4, light=False):
    yy, xx = np.mgrid[0:h, 0:w]
    c = ((xx // s + yy // s) % 2).astype(np.uint8)
    a = np.zeros((h, w, 4), dtype=np.uint8)
    a[c == 0] = (168, 176, 186, 255) if light else (70, 74, 82, 255)
    a[c == 1] = (186, 194, 204, 255) if light else (84, 88, 96, 255)
    return Image.fromarray(a, 'RGBA')


def uv_sheet(layers, out, scale=3):
    """Every layer texture in raw UV form; faint outline marks the model's UV boxes."""
    items = [(f'{group}/{k}', v) for group in ('body', 'clothes', 'hair', 'armor') for k, v in layers[group].items()]
    cols = 9
    cw, ch = 64 * scale + 12, 64 * scale + 26
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * cw + 12, rows * ch + 40), BG)
    d = ImageDraw.Draw(sheet)
    d.text((10, 10), 'Raw UV layouts (64x64, classic arms). Clothes/hair are greyscale; game tints them by RGB multiply.',
           font=_font(13, True), fill=TEXT)
    mask = used_mask()
    guide = np.zeros((64, 64, 4), dtype=np.uint8)
    guide[mask] = (120, 128, 140, 70)
    gimg = Image.fromarray(guide, 'RGBA').resize((64 * scale, 64 * scale), Image.Resampling.NEAREST)
    for i, (name, arr) in enumerate(items):
        x, y = 8 + (i % cols) * cw, 34 + (i // cols) * ch
        sheet.alpha_composite(_checker(64 * scale, 64 * scale), (x, y))
        sheet.alpha_composite(gimg, (x, y))
        sheet.alpha_composite(Image.fromarray(arr, 'RGBA').resize((64 * scale, 64 * scale), Image.Resampling.NEAREST), (x, y))
        d.text((x, y + 64 * scale + 4), name, font=_font(11), fill=SUB)
    sheet.save(out)


def contact_sheet(tiles, out, scale=8):
    """tiles: list of (label, PIL image). Animated strips are split into frames."""
    expanded = []
    for label, im in tiles:
        if im.height > im.width:
            n = im.height // im.width
            for f in range(n):
                expanded.append((f'{label} [{f}]', im.crop((0, f * im.width, im.width, (f + 1) * im.width))))
        else:
            expanded.append((label, im))
    cols = 8
    tw = 16 * scale
    cw, ch = tw + 34, tw + 30
    rows = (len(expanded) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * cw + 16, rows * ch + 44), BG)
    d = ImageDraw.Draw(sheet)
    d.text((12, 12), f'Blocks and items at {scale}x (nearest neighbour)', font=_font(15, True), fill=TEXT)
    for i, (label, im) in enumerate(expanded):
        x, y = 10 + (i % cols) * cw, 40 + (i // cols) * ch
        sheet.alpha_composite(_checker(tw, tw, 8, light=True), (x, y))
        sheet.alpha_composite(im.resize((tw, tw), Image.Resampling.NEAREST), (x, y))
        f = _font(10)
        txt = label.split('/', 1)[1]
        d.text((x, y + tw + 4), txt, font=f, fill=SUB)
    sheet.save(out)


def gui_sheet(ledger: Image.Image, logo: Image.Image, out):
    w = 248 * 2 + 128 * 3 + 50
    sheet = Image.new('RGBA', (w, 180 * 2 + 70), BG)
    d = ImageDraw.Draw(sheet)
    d.text((12, 10), 'GUI: ledger_background (used 248x180 area, 2x) and logo (3x)', font=_font(15, True), fill=TEXT)
    led = ledger.crop((0, 0, 248, 180)).resize((496, 360), Image.Resampling.NEAREST)
    sheet.alpha_composite(led, (12, 40))
    dd = ImageDraw.Draw(sheet)
    f = _font(16)
    for i, line in enumerate(('Settlement Ledger: Oakmere', 'Citizens 23   Grain 140   Coin 57',
                              'Hearth kindled, wards holding.')):
        dd.text((50, 80 + i * 26), line, font=f, fill=(52, 40, 30))
    sheet.alpha_composite(logo.resize((384, 384), Image.Resampling.NEAREST), (12 + 496 + 30, 40 - 10))
    sheet.save(out)


def ensure_dir(p):
    Path(p).mkdir(parents=True, exist_ok=True)
