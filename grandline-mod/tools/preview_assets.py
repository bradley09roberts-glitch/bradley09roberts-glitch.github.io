#!/usr/bin/env python3
"""Dev aid: build contact sheets of the generated textures (needs Pillow) into build/asset-previews/.

    python3 -I tools/preview_assets.py [items|blocks|npc|projectile|misc|all]
"""
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, 'src/main/resources/assets/grandline/textures')
OUT = os.path.join(ROOT, 'build/asset-previews')


def checker(w, h, a=(58, 62, 70), b=(68, 72, 80), cell=8):
    im = Image.new('RGBA', (w, h), a + (255,))
    d = ImageDraw.Draw(im)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=b + (255,))
    return im


def sheet(files, scale, cols, name, label=True, bg=None):
    if not files:
        return None
    ims = [Image.open(f).convert('RGBA') for f in files]
    cw = max(i.width for i in ims) * scale + 12
    ch = max(i.height for i in ims) * scale + (22 if label else 12)
    rows = (len(ims) + cols - 1) // cols
    out = Image.new('RGBA', (cw * cols, ch * rows), (30, 32, 38, 255))
    d = ImageDraw.Draw(out)
    for n, (f, im) in enumerate(zip(files, ims)):
        x, y = (n % cols) * cw + 6, (n // cols) * ch + 6
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        base = checker(big.width, big.height) if bg is None else Image.new('RGBA', big.size, bg)
        base.alpha_composite(big)
        out.paste(base, (x, y))
        if label:
            d.text((x, y + big.height + 2), os.path.splitext(os.path.basename(f))[0][:22], fill=(230, 230, 230, 255))
    os.makedirs(OUT, exist_ok=True)
    p = os.path.join(OUT, name + '.png')
    out.save(p)
    print(p)
    return p


def listdir(sub, filt=None):
    d = os.path.join(TEX, sub)
    if not os.path.isdir(d):
        return []
    fs = sorted(os.path.join(d, f) for f in os.listdir(d) if f.endswith('.png'))
    return [f for f in fs if filt is None or filt(f)]


def main():
    what = sys.argv[1] if len(sys.argv) > 1 else 'all'
    only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
    keep = (lambda f: os.path.splitext(os.path.basename(f))[0] in only) if only else None
    if what in ('items', 'all'):
        sheet(listdir('item', keep), 8, 8, 'items')
    if what in ('blocks', 'all'):
        sheet(listdir('block', keep), 8, 6, 'blocks')
    if what in ('npc', 'all'):
        sheet(listdir('entity/npc', keep), 5, 6, 'npc')
    if what in ('projectile', 'all'):
        sheet(listdir('entity/projectile', keep), 5, 8, 'projectiles')
    if what in ('misc', 'all'):
        sheet([os.path.join(TEX, 'entity/race_parts.png')] + listdir('mob_effect'), 8, 3, 'misc')


if __name__ == '__main__':
    main()
