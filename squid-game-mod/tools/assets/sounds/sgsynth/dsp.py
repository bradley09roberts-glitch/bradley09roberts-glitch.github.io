"""sgsynth.dsp - filters, spectral shaping, reverb, delay and dynamics (numpy only).

No scipy: IIR filters are applied by multiplying the signal spectrum with the exact digital
biquad response (zero-padded so the result is the true *causal* response, or ``circular=True``
for perfectly periodic loop material).  Time-varying filters use weighted overlap-add (WOLA)
of locally filtered frames.
"""
from __future__ import annotations

import math

import numpy as np

from .core import SR, TAU, ns, cents  # noqa: F401  (cents re-exported for convenience)

# ------------------------------------------------------------------ fft size helper
_SMOOTH = None


def fast_len(n: int) -> int:
    """Smallest 5-smooth integer >= n (fast pocketfft sizes)."""
    global _SMOOTH
    if _SMOOTH is None:
        lim = 1 << 25
        vals = []
        p2 = 1
        while p2 < lim:
            p3 = p2
            while p3 < lim:
                p5 = p3
                while p5 < lim:
                    vals.append(p5)
                    p5 *= 5
                p3 *= 3
            p2 *= 2
        _SMOOTH = np.array(sorted(vals), dtype=np.int64)
    return int(_SMOOTH[np.searchsorted(_SMOOTH, n)])


# --------------------------------------------------------------------- biquad design
def biquad_coefs(kind: str, f0, q=0.70710678, gain_db=0.0):
    """RBJ cookbook biquads.  ``f0``/``q``/``gain_db`` may be arrays.  Returns (b0,b1,b2,a1,a2)."""
    f0 = np.clip(np.asarray(f0, dtype=np.float64), 5.0, 0.495 * SR)
    q = np.maximum(np.asarray(q, dtype=np.float64), 0.05)
    w0 = TAU * f0 / SR
    cw = np.cos(w0)
    sw = np.sin(w0)
    alpha = sw / (2.0 * q)
    A = 10.0 ** (np.asarray(gain_db, dtype=np.float64) / 40.0)
    if kind == "lp":
        b0 = (1 - cw) / 2
        b1 = 1 - cw
        b2 = b0
        a0 = 1 + alpha
        a1 = -2 * cw
        a2 = 1 - alpha
    elif kind == "hp":
        b0 = (1 + cw) / 2
        b1 = -(1 + cw)
        b2 = b0
        a0 = 1 + alpha
        a1 = -2 * cw
        a2 = 1 - alpha
    elif kind == "bp":  # constant 0 dB peak gain
        b0 = alpha
        b1 = 0.0 * alpha
        b2 = -alpha
        a0 = 1 + alpha
        a1 = -2 * cw
        a2 = 1 - alpha
    elif kind == "notch":
        b0 = 1.0 + 0.0 * alpha
        b1 = -2 * cw
        b2 = b0
        a0 = 1 + alpha
        a1 = -2 * cw
        a2 = 1 - alpha
    elif kind == "peak":
        b0 = 1 + alpha * A
        b1 = -2 * cw
        b2 = 1 - alpha * A
        a0 = 1 + alpha / A
        a1 = -2 * cw
        a2 = 1 - alpha / A
    elif kind == "ap":
        b0 = 1 - alpha
        b1 = -2 * cw
        b2 = 1 + alpha
        a0 = 1 + alpha
        a1 = -2 * cw
        a2 = 1 - alpha
    elif kind in ("ls", "hs"):
        sa = 2.0 * np.sqrt(A) * (sw / 2.0) * math.sqrt(2.0)  # shelf slope S = 1
        if kind == "ls":
            b0 = A * ((A + 1) - (A - 1) * cw + sa)
            b1 = 2 * A * ((A - 1) - (A + 1) * cw)
            b2 = A * ((A + 1) - (A - 1) * cw - sa)
            a0 = (A + 1) + (A - 1) * cw + sa
            a1 = -2 * ((A - 1) + (A + 1) * cw)
            a2 = (A + 1) + (A - 1) * cw - sa
        else:
            b0 = A * ((A + 1) + (A - 1) * cw + sa)
            b1 = -2 * A * ((A - 1) + (A + 1) * cw)
            b2 = A * ((A + 1) + (A - 1) * cw - sa)
            a0 = (A + 1) - (A - 1) * cw + sa
            a1 = 2 * ((A - 1) - (A + 1) * cw)
            a2 = (A + 1) - (A - 1) * cw - sa
    else:
        raise ValueError(f"unknown biquad kind {kind!r}")
    return b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0


def biquad_H(coefs, w):
    """Complex frequency response at radian frequencies ``w`` (broadcasts against coefs)."""
    b0, b1, b2, a1, a2 = coefs
    z1 = np.exp(-1j * w)
    z2 = z1 * z1
    return (b0 + b1 * z1 + b2 * z2) / (1.0 + a1 * z1 + a2 * z2)


def _tail_samples(coefs) -> int:
    """Samples for the impulse response of a biquad (or array of biquads) to fall below ~-100 dB."""
    a1 = np.asarray(coefs[3], dtype=np.float64).ravel()
    a2 = np.asarray(coefs[4], dtype=np.float64).ravel()
    disc = a1 * a1 - 4.0 * a2
    r = np.where(disc < 0, np.sqrt(np.abs(a2)), (np.abs(a1) + np.sqrt(np.maximum(disc, 0.0))) / 2.0)
    r = float(np.max(r))
    if r >= 0.99999:
        return int(2.0 * SR)
    return int(np.clip(math.log(1e-5) / math.log(r), 64, 6 * SR))


def apply_biquads(x: np.ndarray, coefs_list, circular: bool = False) -> np.ndarray:
    """Apply a cascade of static biquads in one FFT pass (causal, or exactly circular)."""
    x = np.asarray(x, dtype=np.float64)
    n = len(x)
    if n == 0:
        return x
    tail = 0 if circular else sum(_tail_samples(c) for c in coefs_list)
    M = n if circular else fast_len(n + tail)
    X = np.fft.rfft(x, M)
    w = TAU * np.arange(M // 2 + 1) / M
    H = np.ones(M // 2 + 1, dtype=np.complex128)
    for c in coefs_list:
        H = H * biquad_H(c, w)
    return np.fft.irfft(X * H, M)[:n]


def _butter_qs(order: int):
    return [1.0 / (2.0 * math.cos(math.pi * (2 * k + 1) / (2 * order))) for k in range(order // 2)]


def lowpass(x, fc, order: int = 2, q=None, circular: bool = False):
    """Butterworth low-pass (even ``order``); pass ``q`` for a single resonant biquad."""
    if q is not None:
        return apply_biquads(x, [biquad_coefs("lp", fc, q)], circular)
    return apply_biquads(x, [biquad_coefs("lp", fc, qq) for qq in _butter_qs(order)], circular)


def highpass(x, fc, order: int = 2, q=None, circular: bool = False):
    if q is not None:
        return apply_biquads(x, [biquad_coefs("hp", fc, q)], circular)
    return apply_biquads(x, [biquad_coefs("hp", fc, qq) for qq in _butter_qs(order)], circular)


def bandpass(x, f0, q=2.0, circular: bool = False, order: int = 1):
    """Resonant band-pass (0 dB at the centre).  ``order`` cascades identical sections."""
    return apply_biquads(x, [biquad_coefs("bp", f0, q)] * order, circular)


def band(x, f_lo, f_hi, order: int = 2, circular: bool = False):
    """Flat-ish pass band between two corner frequencies."""
    cs = [biquad_coefs("hp", f_lo, qq) for qq in _butter_qs(order)]
    cs += [biquad_coefs("lp", f_hi, qq) for qq in _butter_qs(order)]
    return apply_biquads(x, cs, circular)


def notch(x, f0, q=4.0, circular: bool = False):
    return apply_biquads(x, [biquad_coefs("notch", f0, q)], circular)


def peaking(x, f0, q, gain_db, circular: bool = False):
    return apply_biquads(x, [biquad_coefs("peak", f0, q, gain_db)], circular)


def shelf(x, kind: str, f0, gain_db, circular: bool = False):
    return apply_biquads(x, [biquad_coefs("ls" if kind == "low" else "hs", f0, 0.7071, gain_db)], circular)


def resonator(x, f0, q, circular: bool = False):
    """Peak-normalised resonance (modal filter) - good for excitation/resonator models."""
    return apply_biquads(x, [biquad_coefs("bp", f0, q)], circular)


def onepole_lp(x, fc, circular: bool = False):
    """First-order low-pass (6 dB/oct) via its exact response."""
    x = np.asarray(x, dtype=np.float64)
    n = len(x)
    a = math.exp(-TAU * fc / SR)
    tail = 0 if circular else int(min(math.log(1e-5) / math.log(a), 6 * SR))
    M = n if circular else fast_len(n + tail)
    w = TAU * np.arange(M // 2 + 1) / M
    H = (1 - a) / (1 - a * np.exp(-1j * w))
    return np.fft.irfft(np.fft.rfft(x, M) * H, M)[:n]


def dc_block(x, fc: float = 20.0):
    return highpass(x, fc, order=2)


# --------------------------------------------------------- time-varying (WOLA) engine
def _wola(x, size, make_H, fft_len, circular=False, chunk=384):
    """Weighted overlap-add filtering.  ``make_H(centers, bins_w)`` -> (J, bins) complex."""
    x = np.asarray(x, dtype=np.float64)
    n = len(x)
    hop = size // 4
    left = size - hop
    nframes = (left + n - 1) // hop + 1
    plen = (nframes - 1) * hop + size
    if circular:
        idx = (np.arange(plen) - left) % n
        xp = x[idx]
    else:
        xp = np.zeros(plen)
        xp[left:left + n] = x
    win = 0.5 - 0.5 * np.cos(TAU * np.arange(size) / size)
    M = fft_len
    w = TAU * np.arange(M // 2 + 1) / M
    out = np.zeros((nframes - 1) * hop + M)
    for j0 in range(0, nframes, chunk):
        js = np.arange(j0, min(j0 + chunk, nframes))
        starts = js * hop
        frames = xp[starts[:, None] + np.arange(size)[None, :]] * win[None, :]
        F = np.fft.rfft(frames, M, axis=1)
        centers = np.clip(starts + size // 2 - left, 0, n - 1)
        H = make_H(centers, w)
        y = np.fft.irfft(F * H, M, axis=1)
        for k, s in enumerate(starts):
            out[s:s + M] += y[k]
    out *= 0.5  # periodic Hann at 75 % overlap sums to 2
    res = out[left:left + n].copy()
    if circular:
        over = out[left + n:]
        m = min(len(over), n)
        res[:m] += over[:m]
        under = out[:left]
        m = min(len(under), n)
        res[n - m:] += under[len(under) - m:]
    return res


def tv_biquad(x, kind: str, f, q=0.70710678, gain_db=0.0, size: int = 512, circular: bool = False,
              tail: int | None = None):
    """Time-varying biquad.  ``f``/``q``/``gain_db`` are scalars or per-sample arrays."""
    n = len(x)
    fa = np.broadcast_to(np.asarray(f, dtype=np.float64), (n,))
    qa = np.broadcast_to(np.asarray(q, dtype=np.float64), (n,))
    ga = np.broadcast_to(np.asarray(gain_db, dtype=np.float64), (n,))
    if tail is None:
        # worst-case ring time over the whole trajectory
        tail = min(_tail_samples(biquad_coefs(kind, fa, qa, ga)), 3 * size)
    M = fast_len(size + tail)

    def make_H(centers, w):
        c = biquad_coefs(kind, fa[centers][:, None], qa[centers][:, None], ga[centers][:, None])
        return biquad_H(c, w[None, :])

    return _wola(x, size, make_H, M, circular)


def shape_spectrum(x, mag_fn, size: int = 512, circular: bool = False):
    """Zero-phase time-varying spectral shaping.

    ``mag_fn(centers, freqs)`` -> (J, bins) real magnitudes for the frames centred at sample
    indices ``centers``; ``freqs`` are the bin frequencies in Hz.
    """
    freqs = np.arange(size // 2 + 1) * SR / size

    def make_H(centers, w):
        return mag_fn(centers, freqs)

    return _wola(x, size, make_H, size, circular)


# ------------------------------------------------------------------- convolution / fx
def fft_convolve(x, h, n_out: int | None = None, circular: bool = False):
    """Linear (or circular, length of ``x``) convolution via FFT."""
    x = np.asarray(x, dtype=np.float64)
    n = len(x)
    if circular:
        hh = np.zeros(n)
        m = len(h)
        for k in range(0, m, n):  # fold a longer IR
            seg = h[k:k + n]
            hh[:len(seg)] += seg
        return np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(hh), n)
    L = n + len(h) - 1
    M = fast_len(L)
    y = np.fft.irfft(np.fft.rfft(x, M) * np.fft.rfft(h, M), M)[:L]
    n_out = n if n_out is None else n_out
    if n_out <= L:
        return y[:n_out]
    return np.concatenate([y, np.zeros(n_out - L)])


_IR_CACHE: dict = {}


def make_ir(rt60: float = 1.5, predelay: float = 0.012, damp: float = 0.5, early: float = 0.6,
            seed: int = 7, lowcut: float = 80.0, bass_mult: float = 1.15) -> np.ndarray:
    """Synthetic room impulse response: sparse early reflections + band-wise decaying noise."""
    key = (round(rt60, 3), round(predelay, 4), round(damp, 3), round(early, 3), seed, lowcut, bass_mult)
    if key in _IR_CACHE:
        return _IR_CACHE[key]
    r = np.random.default_rng(seed)
    n = ns(rt60 * 1.1)
    t = np.arange(n) / SR
    noise = r.standard_normal(n)
    M = fast_len(n)
    X = np.fft.rfft(noise, M)
    f = np.fft.rfftfreq(M, 1.0 / SR)
    fsafe = np.maximum(f, 1.0)
    lo = X / (1 + (fsafe / 450.0) ** 4)
    hi = X / (1 + (2500.0 / fsafe) ** 4)
    mid = X - lo - hi
    rt_lo = rt60 * bass_mult
    rt_mid = rt60
    rt_hi = rt60 * (1.0 - 0.8 * damp)
    late = np.zeros(n)
    for band_x, rt in ((lo, rt_lo), (mid, rt_mid), (hi, rt_hi)):
        b = np.fft.irfft(band_x, M)[:n]
        late += b * np.exp(-6.9078 * t / max(rt, 0.05))
    late *= np.clip(t / 0.012, 0.0, 1.0) ** 1.5  # soft onset
    # early reflections
    er = np.zeros(n)
    k = 14
    times = np.sort(r.uniform(0.004, 0.075, k))
    for i, tt in enumerate(times):
        j = int(tt * SR)
        if j < n:
            er[j] += r.choice([-1, 1]) * r.uniform(0.5, 1.0) * (1.0 - tt / 0.1)
    er = np.fft.irfft(np.fft.rfft(er, M) / (1 + (np.maximum(f, 1) / 6500.0) ** 2), M)[:n]
    late_rms = np.sqrt(np.mean(late ** 2)) + 1e-12
    ir = late / late_rms * 0.02 + er * early * 0.35
    # high-pass the IR a little (no rumble in the tail)
    if lowcut > 0:
        Hir = np.fft.rfft(ir, M)
        Hir *= 1 / (1 + (lowcut / fsafe) ** 4)
        ir = np.fft.irfft(Hir, M)[:n]
    pd = ns(predelay)
    ir = np.concatenate([np.zeros(pd), ir])
    ir /= math.sqrt(float(np.sum(ir ** 2)))
    _IR_CACHE[key] = ir
    return ir


def reverb(x, rt60: float = 1.5, wet: float = 0.25, dry: float = 1.0, predelay: float = 0.012,
           damp: float = 0.5, early: float = 0.6, seed: int = 7, circular: bool = False,
           lowcut: float = 80.0):
    """Convolution reverb with a synthetic IR.  ``wet`` is relative to an energy-normalised IR."""
    ir = make_ir(rt60, predelay, damp, early, seed, lowcut)
    w = fft_convolve(x, ir, circular=circular)
    return dry * np.asarray(x) + wet * w


def echo(x, delay: float, feedback: float = 0.4, wet: float = 0.5, repeats: int = 10,
         lp: float | None = None, hp: float | None = None, circular: bool = False):
    """Feed-back style delay built from explicit taps (each repeat progressively filtered)."""
    x = np.asarray(x, dtype=np.float64)
    d = ns(delay)
    out = x.copy()
    cur = x
    for _ in range(repeats):
        cur = np.roll(cur, d) if circular else np.concatenate([np.zeros(d), cur[:-d]])
        cur = cur * feedback
        if lp:
            cur = lowpass(cur, lp, order=2, circular=circular)
        if hp:
            cur = highpass(cur, hp, order=2, circular=circular)
        out = out + wet * cur
        if np.max(np.abs(cur)) < 1e-4:
            break
    return out


# ------------------------------------------------------------------------- dynamics
def follow(x, block: int = 256):
    """Block RMS envelope (returned at block rate with block-centre sample indices)."""
    n = len(x)
    nb = max(n // block, 1)
    xx = np.asarray(x[:nb * block]).reshape(nb, block)
    env = np.sqrt(np.mean(xx * xx, axis=1) + 1e-18)
    centers = (np.arange(nb) + 0.5) * block
    return env, centers


def compress(x, thresh_db: float = -18.0, ratio: float = 3.0, attack: float = 0.01,
             release: float = 0.15, makeup_db: float = 0.0, block: int = 128):
    """Simple block-based feed-forward compressor (good enough for music buses)."""
    x = np.asarray(x, dtype=np.float64)
    env, centers = follow(x, block)
    lev = 20 * np.log10(env + 1e-9)
    over = np.maximum(lev - thresh_db, 0.0)
    gr = -over * (1.0 - 1.0 / ratio)  # desired gain reduction in dB
    ab = math.exp(-block / SR / max(attack, 1e-4))
    rb = math.exp(-block / SR / max(release, 1e-3))
    g = np.zeros_like(gr)
    cur = 0.0
    for i in range(len(gr)):
        tgt = gr[i]
        cur = tgt + (cur - tgt) * (ab if tgt < cur else rb)
        g[i] = cur
    gain = 10 ** ((g + makeup_db) / 20.0)
    full = np.interp(np.arange(len(x)), centers, gain)
    return x * full


def limit(x, ceiling: float = 0.7, lookahead: float = 0.004, release: float = 0.08, block: int = 64):
    """Look-ahead peak limiter (block based, smooth)."""
    x = np.asarray(x, dtype=np.float64)
    n = len(x)
    nb = int(math.ceil(n / block))
    pad = np.zeros(nb * block)
    pad[:n] = np.abs(x)
    pk = pad.reshape(nb, block).max(axis=1)
    need = np.minimum(1.0, ceiling / np.maximum(pk, 1e-9))
    la = max(int(lookahead * SR / block), 1)
    # minimum over the look-ahead window (gain dips before the peak arrives)
    m = need.copy()
    for k in range(1, la + 1):
        m[:-k] = np.minimum(m[:-k], need[k:])
    rel = math.exp(-block / SR / release)
    g = np.empty(nb)
    cur = 1.0
    for i in range(nb):
        if m[i] < cur:
            cur = m[i]
        else:
            cur = m[i] + (cur - m[i]) * rel
        g[i] = cur
    # smooth the gain with a short moving average so steps do not click
    k = max(la, 2)
    g = np.convolve(np.pad(g, (k, k), mode="edge"), np.ones(2 * k + 1) / (2 * k + 1), mode="valid")
    g = np.minimum(g, np.pad(need, (0, 0)))  # never exceed the instantaneous requirement
    gain = np.interp(np.arange(n), (np.arange(nb) + 0.5) * block, g)
    return x * gain


def envelope_follow_abs(x, tau: float = 0.01):
    """Smoothed |x| (one-pole, via FFT filter)."""
    return np.maximum(onepole_lp(np.abs(x), 1.0 / (TAU * tau)), 0.0)
