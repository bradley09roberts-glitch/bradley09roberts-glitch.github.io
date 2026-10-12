#!/usr/bin/env python3
"""Geometry review galleries for the structure audit (offline renders, not game screenshots).

    python3 -I tools/make_galleries.py <before dump dir> <after dump dir> <out dir>

Writes into <out dir>:
  settlement_<style>.png      every settlement building type of one style (AFTER), camera from the south-east
  before_after_<style>.png    the same types BEFORE | AFTER side by side for a few styles
  landmark_<id>.png           each landmark: AFTER views 0 and 2 plus floor cut-aways (BEFORE thumbnail inset)
Renders come from tools/render_structure.py (real block models and textures, Minecraft face shading).
"""
import json
import os
import sys

from PIL import Image, ImageDraw


def save(img, path):
    """256-colour PNG: these are review sheets, a third of the size of full-colour ones."""
    img.convert('RGB').quantize(colors=256, method=Image.Quantize.MEDIANCUT).save(path, optimize=True)


sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_structure as R  # noqa: E402

TYPES = ['house_small', 'house', 'house_large', 'mansion', 'tavern', 'shop', 'blacksmith', 'inn', 'church', 'marine_post',
         'watchtower', 'lighthouse', 'warehouse', 'market_stall', 'well', 'plaza', 'farm', 'ruined_house', 'bounty_board',
         'dojo', 'shipwright', 'hut']
STYLES = ['village', 'stone_town', 'desert', 'snow', 'jungle', 'wano', 'venetian', 'candy', 'marine', 'gothic', 'future',
          'giant', 'palace', 'cloud', 'coral']


def iso(path, view=0, scale=8):
    d = R.load_dump(path)
    R.add_ground(d)
    return R.render_iso(d, view, scale)


def fit(im, w, h):
    im = im.copy()
    im.thumbnail((w, h))
    return im


def sheet(cells, cols, cw, ch, title=None):
    rows = (len(cells) + cols - 1) // cols
    top = 22 if title else 0
    out = Image.new('RGB', (cols * cw, rows * (ch + 16) + top), (36, 40, 48))
    dr = ImageDraw.Draw(out)
    if title:
        dr.text((6, 4), title, fill=(255, 230, 150))
    for k, (label, im) in enumerate(cells):
        x, y = (k % cols) * cw, top + (k // cols) * (ch + 16)
        dr.text((x + 4, y + 2), label, fill=(230, 230, 230))
        if im is not None:
            t = fit(im, cw - 6, ch)
            out.paste(t, (x + (cw - t.width) // 2, y + 16 + (ch - t.height) // 2), t)
    return out


def main():
    before, after, out = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    for st in STYLES:
        cells = []
        for t in TYPES:
            p = os.path.join(after, f'{t}__{st}.json')
            cells.append((t, iso(p) if os.path.exists(p) else None))
        save(sheet(cells, 6, 300, 250, f'Settlement buildings, style {st} (AFTER; offline render)'), os.path.join(out, f'settlement_{st}.png'))
        print('settlement', st)
    for st in ('village', 'desert', 'wano', 'snow', 'venetian', 'marine'):
        cells = []
        for t in TYPES:
            pb, pa = os.path.join(before, f'{t}__{st}.json'), os.path.join(after, f'{t}__{st}.json')
            cells.append((f'{t}  before', iso(pb) if os.path.exists(pb) else None))
            cells.append((f'{t}  after', iso(pa) if os.path.exists(pa) else None))
        save(sheet(cells, 6, 260, 220, f'Before | after, style {st} (offline renders of the structure dumps)'), os.path.join(out, f'before_after_{st}.png'))
        print('before/after', st)
    for f in sorted(os.listdir(after)):
        if not f.endswith('.json'):
            continue
        j = json.load(open(os.path.join(after, f)))
        if j['kind'] != 'landmark':
            continue
        lid = j['id']
        d = R.load_dump(os.path.join(after, f))
        img = R.sheet(d, [0, 2], 8 if max(j['w'], j['d']) < 45 else 5)
        pb = os.path.join(before, f)
        if img is not None and os.path.exists(pb):
            b = iso(pb, 0, 4)
            if b is not None:
                b = fit(b, 260, 200)
                canvas = Image.new('RGB', (img.width, img.height + 0), (40, 44, 52))
                canvas.paste(img, (0, 0))
                dr = ImageDraw.Draw(canvas)
                bx, by = canvas.width - b.width - 8, canvas.height - b.height - 8
                dr.rectangle([bx - 4, by - 18, bx + b.width + 4, by + b.height + 4], fill=(28, 30, 36))
                dr.text((bx, by - 16), 'before', fill=(255, 180, 120))
                canvas.paste(b, (bx, by), b)
                img = canvas
        if img is not None:
            save(img, os.path.join(out, f'landmark_{lid}.png'))
            print('landmark', lid)


if __name__ == '__main__':
    main()
