"""sgsynth.viz - waveform + spectrogram PNGs with numpy and Pillow (matplotlib is not installed)."""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from .core import SR

# inferno-like colour ramp (value 0..1 -> RGB)
_STOPS = np.array([
    [0.00, 0, 0, 4],
    [0.15, 31, 12, 72],
    [0.30, 85, 15, 109],
    [0.45, 136, 34, 106],
    [0.60, 186, 54, 85],
    [0.75, 227, 89, 51],
    [0.88, 249, 140, 10],
    [1.00, 252, 255, 164],
])


def _cmap(v: np.ndarray) -> np.ndarray:
    v = np.clip(v, 0.0, 1.0)
    out = np.empty(v.shape + (3,), dtype=np.float64)
    for c in range(3):
        out[..., c] = np.interp(v, _STOPS[:, 0], _STOPS[:, c + 1])
    return out.astype(np.uint8)


def stft_db(x, nfft: int = 1024, hop: int = 128):
    x = np.asarray(x, dtype=np.float64)
    if len(x) < nfft:
        x = np.concatenate([x, np.zeros(nfft - len(x))])
    win = np.hanning(nfft)
    nfr = 1 + (len(x) - nfft) // hop
    idx = np.arange(nfft)[None, :] + hop * np.arange(nfr)[:, None]
    S = np.abs(np.fft.rfft(x[idx] * win[None, :], axis=1)) / (nfft * 0.25)
    return 20 * np.log10(S + 1e-9), hop / SR


def _font(size: int):
    try:
        return ImageFont.load_default(size=size)
    except Exception:  # pragma: no cover - very old Pillow
        return ImageFont.load_default()


def render(x, path: str, title: str = "", nfft: int = 1024, hop: int = 128, fmax: float = 12000.0,
           db_range: float = 80.0, width: int = 1100, wave_h: int = 120, spec_h: int = 380,
           log_freq: bool = False, fmin: float = 40.0, markers=None) -> None:
    """Write a PNG: waveform (top) + spectrogram (bottom) with time / frequency axes."""
    x = np.asarray(x, dtype=np.float64)
    dur = len(x) / SR
    S, _ = stft_db(x, nfft, hop)
    ref = max(S.max(), -20.0)
    S = np.clip((S - (ref - db_range)) / db_range, 0, 1)
    freqs = np.arange(nfft // 2 + 1) * SR / nfft
    sel = freqs <= fmax
    Sf = S[:, sel].T[::-1]  # (freq, time) with high freq on top
    fsel = freqs[sel]
    pw, ph = width, spec_h
    ml, mt, mr, mb = 62, 28, 14, 30
    W = pw + ml + mr
    H = mt + wave_h + 10 + ph + mb
    img = Image.new("RGB", (W, H), (18, 18, 22))
    d = ImageDraw.Draw(img)
    fnt = _font(13)
    fsm = _font(11)

    # spectrogram image
    if log_freq:
        ys = np.exp(np.linspace(np.log(fmax), np.log(fmin), ph))
        rows = np.clip(np.searchsorted(fsel, ys), 0, len(fsel) - 1)
        rows = len(fsel) - 1 - rows
        rows = np.clip(rows, 0, Sf.shape[0] - 1)
        sp = Sf[rows]
    else:
        sp = Sf
    rgb = _cmap(sp)
    sp_img = Image.fromarray(rgb).resize((pw, ph), Image.BILINEAR)
    img.paste(sp_img, (ml, mt + wave_h + 10))

    # waveform
    cx = mt + wave_h // 2
    d.rectangle([ml, mt, ml + pw, mt + wave_h], outline=(60, 60, 70))
    cols = np.linspace(0, len(x), pw + 1).astype(int)
    for i in range(pw):
        seg = x[cols[i]:max(cols[i + 1], cols[i] + 1)]
        if len(seg):
            lo, hi = seg.min(), seg.max()
            d.line([(ml + i, cx - int(hi * (wave_h / 2 - 2))), (ml + i, cx - int(lo * (wave_h / 2 - 2)))],
                   fill=(120, 200, 255))
    d.text((ml, 6), title, fill=(235, 235, 240), font=fnt)
    pk = np.max(np.abs(x)) if len(x) else 0
    d.text((ml + pw - 260, 6), f"{dur:.3f}s  peak {20*np.log10(pk+1e-9):.1f} dBFS", fill=(170, 170, 180), font=fsm)

    # axes: time
    step = 0.05
    for cand in (0.02, 0.05, 0.1, 0.2, 0.25, 0.5, 1, 2, 5, 10, 20):
        if dur / cand <= 14:
            step = cand
            break
    t = 0.0
    while t <= dur + 1e-9:
        xx = ml + int(t / dur * pw)
        d.line([(xx, mt + wave_h + 10 + ph), (xx, mt + wave_h + 10 + ph + 4)], fill=(200, 200, 200))
        d.text((xx - 10, mt + wave_h + 10 + ph + 8), f"{t:g}", fill=(200, 200, 200), font=fsm)
        t += step
    # axes: frequency
    if log_freq:
        ticks = [f for f in (50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000) if fmin <= f <= fmax]
        for f in ticks:
            yy = mt + wave_h + 10 + int(ph * (np.log(fmax) - np.log(f)) / (np.log(fmax) - np.log(fmin)))
            d.line([(ml - 4, yy), (ml, yy)], fill=(200, 200, 200))
            d.text((4, yy - 7), f"{f/1000:g}k" if f >= 1000 else f"{f}", fill=(200, 200, 200), font=fsm)
    else:
        fstep = 1000 if fmax <= 10000 else 2000
        if fmax <= 5000:
            fstep = 500
        f = 0
        while f <= fmax:
            yy = mt + wave_h + 10 + int(ph * (1 - f / fmax))
            d.line([(ml - 4, yy), (ml, yy)], fill=(200, 200, 200))
            d.text((4, yy - 7), f"{f/1000:g}k" if f else "0", fill=(200, 200, 200), font=fsm)
            f += fstep
    if markers:
        for tm, label in markers:
            xx = ml + int(tm / dur * pw)
            d.line([(xx, mt + wave_h + 10), (xx, mt + wave_h + 10 + ph)], fill=(80, 255, 120))
            d.text((xx + 2, mt + wave_h + 12), label, fill=(80, 255, 120), font=fsm)
    img.save(path)


def render_grid(items, path: str, cols: int = 2, **kw) -> None:
    """Several (title, samples) renders tiled in one PNG."""
    import os
    import tempfile
    tiles = []
    with tempfile.TemporaryDirectory() as td:
        for i, (title, x) in enumerate(items):
            p = os.path.join(td, f"{i}.png")
            render(x, p, title=title, **kw)
            tiles.append(Image.open(p).convert("RGB"))
    w = max(t.width for t in tiles)
    h = max(t.height for t in tiles)
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (w * cols, h * rows), (18, 18, 22))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * w, (i // cols) * h))
    sheet.save(path)
