"""sgsynth.core - basic signal toolkit (numpy only).

Everything here works on mono float64 numpy arrays at 44.1 kHz.  Frequencies may be
scalars or per-sample arrays (for glides / vibrato), all randomness comes from seeded
``numpy.random.Generator`` objects so every render is bit-for-bit repeatable.
"""
from __future__ import annotations

import math
import os
import subprocess
import wave
import zlib

import numpy as np

SR = 44100
TAU = 2.0 * math.pi


# --------------------------------------------------------------------------- utils
def seed_for(name: str, variant: int = 0) -> int:
    """Stable 32-bit seed derived from an event name (never uses Python's salted hash)."""
    return zlib.crc32(f"{name}#{variant}".encode("utf-8")) & 0xFFFFFFFF


def make_rng(name: str, variant: int = 0) -> np.random.Generator:
    return np.random.default_rng(seed_for(name, variant))


def ns(seconds: float) -> int:
    """seconds -> sample count."""
    return int(round(seconds * SR))


def time_axis(n: int) -> np.ndarray:
    return np.arange(n, dtype=np.float64) / SR


def db2lin(db):
    return 10.0 ** (np.asarray(db, dtype=np.float64) / 20.0)


def lin2db(x):
    return 20.0 * np.log10(np.maximum(np.asarray(x, dtype=np.float64), 1e-12))


def mtof(midi):
    """MIDI note number -> Hz (A4 = 69 = 440 Hz)."""
    return 440.0 * 2.0 ** ((np.asarray(midi, dtype=np.float64) - 69.0) / 12.0)


def cents(c):
    """cents -> frequency ratio."""
    return 2.0 ** (np.asarray(c, dtype=np.float64) / 1200.0)


def zeros(seconds: float) -> np.ndarray:
    return np.zeros(ns(seconds))


# ------------------------------------------------------------------------ envelopes
def curve(points, n: int, kind: str = "cos") -> np.ndarray:
    """Piece-wise curve through ``(time_s, value)`` knots, sampled for ``n`` samples.

    kind: ``lin`` straight lines, ``cos`` smooth (zero slope at every knot),
          ``exp`` geometric interpolation (all values must be > 0).
    Outside the knot range the first / last value is held.
    """
    t = np.arange(n, dtype=np.float64) / SR
    ts = np.asarray([p[0] for p in points], dtype=np.float64)
    vs = np.asarray([p[1] for p in points], dtype=np.float64)
    if len(ts) == 1:
        return np.full(n, vs[0])
    if kind == "lin":
        return np.interp(t, ts, vs)
    if kind == "exp":
        return np.exp(np.interp(t, ts, np.log(np.maximum(vs, 1e-9))))
    idx = np.clip(np.searchsorted(ts, t, side="right") - 1, 0, len(ts) - 2)
    t0 = ts[idx]
    t1 = ts[idx + 1]
    u = np.clip((t - t0) / np.maximum(t1 - t0, 1e-12), 0.0, 1.0)
    s = 0.5 - 0.5 * np.cos(np.pi * u)
    return vs[idx] + (vs[idx + 1] - vs[idx]) * s


def adsr(n: int, a: float, d: float, s: float, r: float) -> np.ndarray:
    """Smooth ADSR; the release occupies the last ``r`` seconds of the ``n`` samples.

    Attack / release are limited to 45 % of the length each so the knots always stay in order.
    """
    total = n / SR
    r = min(r, 0.45 * total)
    a = min(a, 0.45 * total)
    d = max(min(d, total - a - r), 1e-4)
    t_rel = max(total - r, a + d + 1e-4)
    pts = [(0.0, 0.0), (a, 1.0), (a + d, s), (t_rel, s), (max(total, t_rel + 1e-4), 0.0)]
    return curve(pts, n, "cos")


def exp_decay(n: int, tau: float, attack: float = 0.0) -> np.ndarray:
    """exp(-t/tau) with an optional raised-cosine attack of ``attack`` seconds."""
    t = np.arange(n, dtype=np.float64) / SR
    e = np.exp(-t / max(tau, 1e-6))
    if attack > 0:
        e *= curve([(0.0, 0.0), (attack, 1.0)], n, "cos")
    return e


def fade(x: np.ndarray, fin: float = 0.003, fout: float = 0.010) -> np.ndarray:
    """Raised-cosine fade in / out (seconds) - kills clicks at the file edges."""
    y = np.array(x, dtype=np.float64, copy=True)
    n = len(y)
    a = min(ns(fin), n // 2)
    b = min(ns(fout), n // 2)
    if a > 1:
        y[:a] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(a) / a)
    if b > 1:
        y[n - b:] *= 0.5 + 0.5 * np.cos(np.pi * (np.arange(b) + 1) / b)
    return y


def tail_fade(x: np.ndarray, seconds: float = 0.012) -> np.ndarray:
    """Raised-cosine fade over the last ``seconds`` (never more than 40 % of the signal)."""
    y = np.array(x, dtype=np.float64, copy=True)
    b = min(ns(seconds), int(len(y) * 0.4))
    if b > 1:
        y[len(y) - b:] *= 0.5 + 0.5 * np.cos(np.pi * (np.arange(b) + 1) / b)
    return y


def smoothstep(u):
    u = np.clip(u, 0.0, 1.0)
    return u * u * (3.0 - 2.0 * u)


# ---------------------------------------------------------------------- oscillators
def phase_cycles(freq, n: int, phase0: float = 0.0) -> np.ndarray:
    """Phase in cycles for a (possibly time-varying) frequency in Hz."""
    f = np.broadcast_to(np.asarray(freq, dtype=np.float64), (n,))
    acc = np.empty(n, dtype=np.float64)
    acc[0] = 0.0
    if n > 1:
        np.cumsum(f[:-1], out=acc[1:])
    return phase0 + acc / SR


def sine(freq, n: int, phase0: float = 0.0) -> np.ndarray:
    return np.sin(TAU * phase_cycles(freq, n, phase0))


def _blep(t: np.ndarray, dt: np.ndarray) -> np.ndarray:
    out = np.zeros_like(t)
    m = t < dt
    if np.any(m):
        x = t[m] / dt[m]
        out[m] = x + x - x * x - 1.0
    m = t > 1.0 - dt
    if np.any(m):
        x = (t[m] - 1.0) / dt[m]
        out[m] = x * x + x + x + 1.0
    return out


def saw(freq, n: int, phase0: float = 0.0) -> np.ndarray:
    """Band-limited (PolyBLEP) rising sawtooth, +-1."""
    f = np.broadcast_to(np.asarray(freq, dtype=np.float64), (n,))
    ph = phase_cycles(f, n, phase0) % 1.0
    dt = np.clip(f / SR, 1e-6, 0.45)
    return 2.0 * ph - 1.0 - _blep(ph, dt)


def pulse(freq, n: int, width: float = 0.5, phase0: float = 0.0) -> np.ndarray:
    """Band-limited pulse / square wave (zero mean)."""
    f = np.broadcast_to(np.asarray(freq, dtype=np.float64), (n,))
    dt = np.clip(f / SR, 1e-6, 0.45)
    ph = phase_cycles(f, n, phase0) % 1.0
    ph2 = (ph + width) % 1.0
    s1 = 2.0 * ph - 1.0 - _blep(ph, dt)
    s2 = 2.0 * ph2 - 1.0 - _blep(ph2, dt)
    return s1 - s2


def tri(freq, n: int, phase0: float = 0.0) -> np.ndarray:
    ph = phase_cycles(freq, n, phase0) % 1.0
    return 4.0 * np.abs(ph - 0.5) - 1.0


def fm(carrier, ratio, index, n: int, phase0: float = 0.0) -> np.ndarray:
    """Two-operator FM; ``carrier`` Hz, modulator = carrier*ratio, ``index`` may be an array."""
    c = np.broadcast_to(np.asarray(carrier, dtype=np.float64), (n,))
    mod = np.sin(TAU * phase_cycles(c * ratio, n))
    return np.sin(TAU * phase_cycles(c, n, phase0) + np.asarray(index) * mod)


def supersaw(freq, n: int, voices: int = 5, detune_cents: float = 12.0, rng=None) -> np.ndarray:
    """Stack of detuned band-limited saws, unit-ish level."""
    rng = rng or np.random.default_rng(1)
    out = np.zeros(n)
    spread = np.linspace(-1.0, 1.0, voices) if voices > 1 else np.zeros(1)
    for s in spread:
        out += saw(np.asarray(freq) * cents(s * detune_cents), n, phase0=rng.random())
    return out / math.sqrt(voices)


# ------------------------------------------------------------------------------ noise
def white(n: int, rng) -> np.ndarray:
    return rng.standard_normal(n)


def colored(n: int, rng, power: float = 1.0, f_lo: float = 0.0, f_hi: float = 0.0) -> np.ndarray:
    """FFT-synthesised noise with power spectrum ~ f^-power (0 white, 1 pink, 2 brown).

    Because it is built in the frequency domain it is exactly periodic over ``n`` samples,
    which makes it ideal for seamless loops.  Unit RMS.
    """
    X = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1.0 / SR)
    f[0] = f[1] if len(f) > 1 else 1.0
    X *= f ** (-power / 2.0)
    X[0] = 0.0
    if f_lo > 0:
        X *= 1.0 / (1.0 + (f_lo / f) ** 4)
    if f_hi > 0:
        X *= 1.0 / (1.0 + (f / f_hi) ** 4)
    y = np.fft.irfft(X, n)
    return y / (np.sqrt(np.mean(y * y)) + 1e-12)


def pink(n: int, rng) -> np.ndarray:
    return colored(n, rng, 1.0)


def brown(n: int, rng) -> np.ndarray:
    return colored(n, rng, 2.0)


def smooth_noise(n: int, rng, rate_hz: float) -> np.ndarray:
    """Slowly wandering random curve in about [-1, 1] (band-limited around ``rate_hz``)."""
    m = max(int(n / SR * rate_hz * 4) + 4, 4)
    pts = rng.standard_normal(m)
    pts /= max(np.abs(pts).max(), 1e-9)
    x = np.linspace(0, m - 1, n)
    i0 = np.floor(x).astype(int)
    u = x - i0
    i1 = np.minimum(i0 + 1, m - 1)
    s = 0.5 - 0.5 * np.cos(np.pi * u)
    return pts[i0] * (1 - s) + pts[i1] * s


def periodic_smooth_noise(n: int, rng, cycles: int, harmonics: int = 6) -> np.ndarray:
    """Slow random curve that is exactly periodic over n samples (for loops).

    ``cycles`` = lowest frequency in cycles per loop; ``harmonics`` how many partials.
    """
    t = np.arange(n) / n
    y = np.zeros(n)
    for k in range(1, harmonics + 1):
        y += rng.uniform(0.3, 1.0) / k * np.sin(TAU * (cycles * k * t + rng.random()))
    return y / max(np.abs(y).max(), 1e-9)


# -------------------------------------------------------------------- mixing / levels
def add_at(dst: np.ndarray, src: np.ndarray, t: float, gain: float = 1.0, wrap: bool = False) -> None:
    """Mix ``src`` into ``dst`` at time ``t`` seconds (clipped, or wrapped round the end when ``wrap``)."""
    i = ns(t)
    n = len(dst)
    m = len(src)
    if wrap:
        i %= n
        done = 0
        while done < m:
            k = min(m - done, n - i)
            dst[i:i + k] += src[done:done + k] * gain
            done += k
            i = 0
        return
    a = max(i, 0)
    b = min(i + m, n)
    if b > a:
        dst[a:b] += src[a - i:b - i] * gain


def stack(*parts, n: int | None = None) -> np.ndarray:
    """Sum arrays of different lengths (zero padded to the longest, or to ``n``)."""
    m = max(len(p) for p in parts) if n is None else n
    out = np.zeros(m)
    for p in parts:
        k = min(len(p), m)
        out[:k] += p[:k]
    return out


def peak(x) -> float:
    return float(np.max(np.abs(x))) if len(x) else 0.0


def rms(x) -> float:
    return float(np.sqrt(np.mean(np.square(x)))) if len(x) else 0.0


def peak_db(x) -> float:
    return float(lin2db(peak(x)))


def rms_db(x) -> float:
    return float(lin2db(rms(x)))


def normalize(x: np.ndarray, peak_dbfs: float = -3.0, rms_cap_dbfs: float | None = None) -> np.ndarray:
    """Scale so the peak sits at ``peak_dbfs``; optionally cap the overall RMS (loudness)."""
    p = peak(x)
    if p < 1e-9:
        return x
    g = 10.0 ** (peak_dbfs / 20.0) / p
    if rms_cap_dbfs is not None:
        r = rms(x)
        if r > 1e-9:
            g = min(g, 10.0 ** (rms_cap_dbfs / 20.0) / r)
    return x * g


def remove_dc(x: np.ndarray) -> np.ndarray:
    return x - np.mean(x)


def finish(x: np.ndarray, peak_dbfs: float = -3.0, rms_cap_dbfs: float | None = -12.0,
           fin: float = 0.002, fout: float = 0.012) -> np.ndarray:
    """Standard one-shot finish: de-click fades, DC removal, peak (+loudness cap) normalisation."""
    y = fade(remove_dc(np.asarray(x, dtype=np.float64)), fin, fout)
    return normalize(y, peak_dbfs, rms_cap_dbfs)


def soft_clip(x: np.ndarray, drive: float = 1.0) -> np.ndarray:
    """tanh saturation, gain-compensated so small signals keep their level."""
    return np.tanh(drive * x) / max(drive, 1e-6)


# ------------------------------------------------------------------------------ loops
def loop_xfade(x: np.ndarray, loop_seconds: float, xfade_seconds: float) -> np.ndarray:
    """Equal-power cross-fade of the tail (beyond ``loop_seconds``) into the head.

    ``x`` must be at least ``loop_seconds + xfade_seconds`` long.  The returned loop has
    exactly ``loop_seconds`` and its last sample flows into its first one.
    """
    L = ns(loop_seconds)
    X = ns(xfade_seconds)
    if len(x) < L + X:
        raise ValueError("need loop + xfade samples of source material")
    u = np.arange(X) / X
    fi = np.sin(0.5 * np.pi * u)
    fo = np.cos(0.5 * np.pi * u)
    out = np.array(x[:L], dtype=np.float64, copy=True)
    out[:X] = x[:X] * fi + x[L:L + X] * fo
    return out


def loop_wrap(x: np.ndarray, loop_seconds: float) -> np.ndarray:
    """Fold everything beyond ``loop_seconds`` back onto the start (tails ring across the seam)."""
    L = ns(loop_seconds)
    out = np.array(x[:L], dtype=np.float64, copy=True)
    k = L
    while k < len(x):
        seg = x[k:k + L]
        out[:len(seg)] += seg
        k += L
    return out


# ---------------------------------------------------------------------------- file io
def write_wav(path: str, x: np.ndarray, sr: int = SR) -> None:
    pcm = (np.clip(x, -1.0, 1.0) * 32767.0).round().astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())


def encode_ogg(wav_path: str, ogg_path: str, quality: int = 4) -> None:
    """Mono 44.1 kHz Vorbis (libvorbis, -q:a 4).  bitexact flags make the bytes repeatable."""
    os.makedirs(os.path.dirname(ogg_path), exist_ok=True)
    cmd = [
        "ffmpeg", "-y", "-loglevel", "error", "-i", wav_path,
        "-map_metadata", "-1", "-ac", "1", "-ar", str(SR),
        "-c:a", "libvorbis", "-q:a", str(quality),
        "-fflags", "+bitexact", "-flags:a", "+bitexact", ogg_path,
    ]
    subprocess.run(cmd, check=True)


def ffprobe_duration(path: str) -> float:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", path],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    return float(out)


def decode_ogg(path: str) -> np.ndarray:
    """Decode any audio file to mono float32 at 44.1 kHz with ffmpeg."""
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
        capture_output=True, check=True,
    ).stdout
    return np.frombuffer(raw, dtype="<f4").astype(np.float64)
