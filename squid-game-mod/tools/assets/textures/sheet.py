"""Contact-sheet helper: lays out PNGs (nearest-neighbour upscaled) with labels on a backdrop."""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


def _font(size: int = 11):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:  # very old Pillow
        return ImageFont.load_default()


def checker(w: int, h: int, a=(46, 50, 60), b=(58, 63, 75), cell: int = 8) -> Image.Image:
    ys, xs = np.mgrid[0:h, 0:w]
    m = ((xs // cell + ys // cell) % 2).astype(bool)
    arr = np.zeros((h, w, 3), np.uint8)
    arr[:] = a
    arr[m] = b
    return Image.fromarray(arr, "RGB").convert("RGBA")


def sheet(items, scale: int = 8, cols: int = 6, pad: int = 10, label_h: int = 16,
          bg=(36, 39, 48, 255), backdrop: str = "checker", max_cell: tuple[int, int] | None = None) -> Image.Image:
    """items: list of (label, PIL image or ndarray or path)."""
    imgs = []
    for label, src in items:
        if isinstance(src, (str, Path)):
            im = Image.open(src).convert("RGBA")
        elif isinstance(src, np.ndarray):
            im = Image.fromarray(src, "RGBA")
        else:
            im = src.convert("RGBA")
        imgs.append((label, im))
    cw = max(im.width * (scale if im.width <= 64 else 1) for _, im in imgs)
    ch = max(im.height * (scale if im.height <= 64 else 1) for _, im in imgs)
    if max_cell:
        cw, ch = min(cw, max_cell[0]), min(ch, max_cell[1])
    rows = (len(imgs) + cols - 1) // cols
    W = pad + cols * (cw + pad)
    H = pad + rows * (ch + label_h + pad)
    out = Image.new("RGBA", (W, H), bg)
    d = ImageDraw.Draw(out)
    f = _font(11)
    for i, (label, im) in enumerate(imgs):
        k = scale if im.width <= 64 and im.height <= 64 else 1
        if im.width * k > cw or im.height * k > ch:
            k = max(1, min(cw // im.width, ch // im.height))
        big = im.resize((im.width * k, im.height * k), Image.NEAREST) if k != 1 else im
        cx = pad + (i % cols) * (cw + pad)
        cy = pad + (i // cols) * (ch + label_h + pad)
        if backdrop == "checker":
            out.paste(checker(big.width, big.height), (cx, cy))
        else:
            out.paste(Image.new("RGBA", big.size, (8, 9, 12, 255)), (cx, cy))
        out.alpha_composite(big, (cx, cy))
        d.text((cx, cy + big.height + 2), label, fill=(225, 228, 235, 255), font=f)
    return out
