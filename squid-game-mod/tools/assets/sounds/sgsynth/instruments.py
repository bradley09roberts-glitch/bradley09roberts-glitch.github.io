"""sgsynth.instruments - reusable sound sources: resonators, hits, noise textures, tones, strings.

All functions return mono float64 arrays at 44.1 kHz.  ``rng`` is always a seeded Generator.
"""
from __future__ import annotations

import math

import numpy as np

from .core import (SR, TAU, add_at, adsr, cents, curve, exp_decay, ns, phase_cycles, saw, sine,
                   smooth_noise, stack, supersaw, tail_fade, time_axis)
from .dsp import apply_biquads, band, bandpass, biquad_coefs, lowpass, tv_biquad


# ---------------------------------------------------------------------------- modal
def modal(freqs, amps, taus, dur: float, phases=None, detune_beat: float = 0.0) -> np.ndarray:
    """Sum of exponentially decaying sinusoids (struck bars, glass, wood, metal, bells).

    freqs Hz, amps linear, taus = 1/e decay times in seconds.  ``detune_beat`` adds a second
    copy of every partial that many Hz higher (slow beating, shimmer).
    """
    n = ns(dur)
    t = time_axis(n)
    out = np.zeros(n)
    for i, (f, a, tau) in enumerate(zip(freqs, amps, taus)):
        if f >= 0.48 * SR:
            continue
        ph = 0.0 if phases is None else phases[i]
        env = np.exp(-t / tau)
        out += a * env * np.sin(TAU * f * t + ph)
        if detune_beat:
            out += 0.7 * a * env * np.sin(TAU * (f + detune_beat) * t + ph)
    out *= np.minimum(1.0, t / 0.0002)          # 0.2 ms anti-click ramp
    return tail_fade(out, 0.015)


def noise_burst(rng, dur: float, lo: float = 0.0, hi: float = 0.0, tau: float = 0.005,
                attack: float = 0.0005, order: int = 2) -> np.ndarray:
    """Band-limited noise with a fast exponential decay (clicks, snaps, ticks)."""
    n = ns(dur)
    x = rng.standard_normal(n)
    cs = []
    if lo > 0:
        cs += [biquad_coefs("hp", lo, 0.7071)] * order
    if hi > 0:
        cs += [biquad_coefs("lp", hi, 0.7071)] * order
    if cs:
        x = apply_biquads(x, cs)
    x /= (np.sqrt(np.mean(x * x)) + 1e-12)
    return tail_fade(x * exp_decay(n, tau, attack), 0.004)


def thump(f_start: float, f_end: float, dur: float, tau_f: float = 0.03, tau_a: float = 0.08,
          attack: float = 0.001, click: float = 0.0, rng=None) -> np.ndarray:
    """Kick-like sine with exponential pitch drop (body impacts, hearts, drums)."""
    n = ns(dur)
    t = time_axis(n)
    f = f_end + (f_start - f_end) * np.exp(-t / tau_f)
    y = np.sin(TAU * phase_cycles(f, n)) * exp_decay(n, tau_a, attack)
    if click > 0 and rng is not None:
        add_at(y, click * noise_burst(rng, min(dur, 0.02), 200, 3500, tau=0.002), 0.0)
    return tail_fade(y, 0.012)


def bell_fm(freq: float, dur: float, ratio: float = 3.5, index: float = 2.5, tau: float = 0.5,
            index_tau: float = 0.2, attack: float = 0.002, amp_tau=None) -> np.ndarray:
    """FM bell / electric-piano tone: decaying modulation index gives a bright strike that mellows."""
    n = ns(dur)
    t = time_axis(n)
    idx = index * np.exp(-t / index_tau)
    y = fm(freq, ratio, idx, n)
    return tail_fade(y * exp_decay(n, tau if amp_tau is None else amp_tau, attack), 0.02)


def fm(carrier, ratio, index, n: int, phase0: float = 0.0) -> np.ndarray:
    c = np.broadcast_to(np.asarray(carrier, dtype=np.float64), (n,))
    mod = np.sin(TAU * phase_cycles(c * ratio, n))
    return np.sin(TAU * phase_cycles(c, n, phase0) + index * mod)


def chirp(f0: float, f1: float, dur: float, curve_kind: str = "exp", shape=None, tau=None) -> np.ndarray:
    """Sine glide f0 -> f1 (exp or lin) with a smooth envelope."""
    n = ns(dur)
    t = np.linspace(0.0, 1.0, n)
    f = f0 * (f1 / f0) ** t if curve_kind == "exp" else f0 + (f1 - f0) * t
    y = np.sin(TAU * phase_cycles(f, n))
    env = np.sin(np.pi * np.clip(t, 0, 1)) ** 0.7 if shape is None else shape
    if tau is not None:
        env = env * np.exp(-np.arange(n) / SR / tau)
    return y * env


def sparks(rng, dur: float, rate, lo: float = 1500.0, hi: float = 7000.0, tau: float = 0.0015,
           amp_jitter: float = 0.7) -> np.ndarray:
    """Poisson crackle: random impulses (rate in 1/s, scalar or array), band-limited."""
    n = ns(dur)
    r = np.broadcast_to(np.asarray(rate, dtype=np.float64), (n,)) / SR
    hit = rng.random(n) < r
    x = np.zeros(n)
    amp = rng.uniform(1.0 - amp_jitter, 1.0, n) * rng.choice([-1.0, 1.0], n)
    x[hit] = amp[hit]
    # impulse -> short damped burst, then band-limit
    k = ns(tau * 8) + 2
    kern = np.exp(-np.arange(k) / SR / tau)
    y = np.convolve(x, kern)[:n]
    y = apply_biquads(y, [biquad_coefs("hp", lo, 0.7071), biquad_coefs("lp", hi, 0.7071)])
    return y


def poisson_times(rng, rate_fn, t_max: float, rate_max: float) -> list:
    """Event times of an inhomogeneous Poisson process (thinning), ``rate_fn(t)`` in events/s."""
    out = []
    t = 0.0
    while True:
        t += rng.exponential(1.0 / rate_max)
        if t >= t_max:
            return out
        if rng.random() < rate_fn(t) / rate_max:
            out.append(t)


def friction(rng, dur: float, f_pulse, resonances, q: float = 12.0, rough: float = 0.5,
             jitter: float = 0.25, pulse_tau: float = 0.0006) -> np.ndarray:
    """Stick-slip friction (creaks, squeaks, rope, wood): a jittery pulse train exciting resonators.

    ``f_pulse``: stick-slip rate in Hz (scalar or array).  ``resonances``: list of
    (centre_hz, gain) or (centre_array, gain) for time-varying body resonances.
    """
    n = ns(dur)
    fp = np.broadcast_to(np.asarray(f_pulse, dtype=np.float64), (n,))
    fp = fp * (1.0 + jitter * smooth_noise(n, rng, 25.0))
    ph = phase_cycles(fp, n) % 1.0
    # sawtooth-ish slip: ramps up then snaps back, amplitude jitters per cycle
    cyc = np.floor(phase_cycles(fp, n)).astype(int)
    amps = rng.uniform(0.5, 1.0, cyc.max() + 2)
    exc = (ph ** 3) * amps[cyc]
    exc = exc - np.mean(exc)
    exc = exc + rough * rng.standard_normal(n) * 0.3 * (ph ** 2)
    out = np.zeros(n)
    for fc, g in resonances:
        if np.ndim(fc) == 0:
            out += g * bandpass(exc, fc, q)
        else:
            out += g * tv_biquad(exc, "bp", fc, q)
    return out


# ------------------------------------------------------------------------- foley bits
def wood_tock(rng, f: float = 1200.0, dur: float = 0.15, snap: float = 0.5, tilt: float = 1.0):
    """Hollow wooden tick (clock / wood block)."""
    parts = modal([f, f * 2.31, f * 3.87, f * 5.6], [1.0, 0.5, 0.22 * tilt, 0.1 * tilt],
                  [0.016, 0.010, 0.006, 0.004], dur)
    c = noise_burst(rng, dur, 1800, 7500, tau=0.0012) * snap
    return parts + c


def footstep(rng, f: float = 90.0, dur: float = 0.3, tone: float = 1.0, grit: float = 0.5):
    th = thump(f * 1.8, f, dur, tau_f=0.02, tau_a=0.05) * tone
    sc = noise_burst(rng, dur, 150, 1500, tau=0.025, attack=0.003) * grit * 0.5
    return th + sc


def metal_hit(rng, f: float = 600.0, dur: float = 0.8, tau: float = 0.35, brightness: float = 1.0):
    ratios = [1.0, 1.51, 2.14, 2.78, 3.35, 4.21, 5.43]
    amps = [1.0, 0.7, 0.55, 0.4, 0.3, 0.2 * brightness, 0.12 * brightness]
    taus = [tau, tau * 0.8, tau * 0.65, tau * 0.55, tau * 0.45, tau * 0.35, tau * 0.25]
    fr = [f * r * (1.0 + 0.004 * rng.standard_normal()) for r in ratios]
    return stack(modal(fr, amps, taus, dur), 0.4 * noise_burst(rng, min(dur, 0.05), 1500, 9000, tau=0.004))


def whoosh(rng, dur: float, f_start: float, f_end: float, q: float = 1.2, attack=None, peak_at=0.5):
    """Band-passed noise swept from f_start to f_end with a swell envelope."""
    n = ns(dur)
    x = rng.standard_normal(n)
    f = np.exp(np.linspace(math.log(f_start), math.log(f_end), n))
    y = tv_biquad(x, "bp", f, q)
    env = curve([(0.0, 0.0), (dur * peak_at, 1.0), (dur, 0.0)], n, "cos")
    return y * env


# --------------------------------------------------------------------------- tones
def brass(freq, dur: float, rng=None, attack: float = 0.06, release: float = 0.12, bright: float = 1.0,
          detune: float = 7.0, f_open: float = 2600.0, f_closed: float = 500.0, scoop: float = 0.0):
    """Brassy synth: detuned saws through a filter that opens with the swell (+ lip-buzz scoop)."""
    n = ns(dur)
    t = time_axis(n)
    f = np.broadcast_to(np.asarray(freq, dtype=np.float64), (n,)).copy()
    if scoop:
        f = f * cents(-scoop * np.exp(-t / 0.05))
    y = np.zeros(n)
    for d, g in ((-detune, 0.8), (0.0, 1.0), (detune, 0.8)):
        y += g * saw(f * cents(d), n, phase0=0.17 + d * 0.01)
    y /= 2.6
    k1 = min(attack * 1.5, dur * 0.35)
    k2 = min(max(attack * 4.0 + 0.05, k1 + 0.05), dur * 0.8)
    cut = curve([(0.0, f_closed), (k1, f_open * 0.6 * bright), (k2, f_open * bright),
                 (dur, f_open * 0.7 * bright)], n, "cos")
    y = tv_biquad(y, "lp", cut, 0.9)
    return y * adsr(n, attack, 0.08, 0.92, release)


def pad_note(freq: float, dur: float, rng, attack: float = 0.8, release: float = 1.2, cutoff: float = 900.0,
             voices: int = 4, detune_c: float = 9.0, sub: float = 0.4):
    """Soft detuned-saw pad voice (called per chord tone, filtered per note)."""
    n = ns(dur)
    y = supersaw(freq, n, voices, detune_c, rng)
    y += sub * sine(freq * 0.5, n)
    y = lowpass(y, cutoff, order=2)
    return y * adsr(n, attack, 0.2, 0.9, release)


def string_swell(freq: float, dur: float, rng, attack: float = 0.5, release: float = 0.6, cutoff: float = 1400.0,
                 vib_hz: float = 5.2, vib_cents: float = 6.0, voices: int = 5, detune_c: float = 11.0):
    """Bowed-string-like ensemble: detuned saws, slow attack, gentle vibrato, formant-ish body."""
    n = ns(dur)
    t = time_axis(n)
    vib = vib_cents * np.sin(TAU * vib_hz * t + rng.random() * 6.28) * curve([(0, 0), (attack, 1.0)], n, "cos")
    y = supersaw(freq * cents(vib), n, voices, detune_c, rng)
    y = lowpass(y, cutoff, order=2)
    y = bandpass_mix(y, 450.0, 0.8, 0.35)
    return y * adsr(n, attack, 0.15, 0.95, release)


def bandpass_mix(x, f0, q, mix):
    """Add a band-passed copy (a cheap body resonance)."""
    return x + mix * bandpass(x, f0, q)


def music_box_note(freq: float, dur: float, rng, vel: float = 1.0, detune_cents: float = 0.0):
    """Struck steel comb tine: bright partials, slow shimmer, short pin click, box body resonance."""
    f = freq * float(cents(detune_cents))
    # decays shorten with pitch
    base = float(np.clip(2.2 - 0.0012 * f, 0.5, 2.0))
    ratios = [1.0, 2.0, 3.0, 4.07, 6.27, 9.3]
    amps = [1.0, 0.34, 0.20, 0.10, 0.12, 0.05]
    taus = [base, base * 0.55, base * 0.42, base * 0.3, base * 0.1, base * 0.05]
    y = modal([f * r for r in ratios], amps, taus, dur, detune_beat=0.6 + 0.0005 * f)
    add_at(y, 0.10 * noise_burst(rng, 0.03, 3000, 9000, tau=0.002), 0.0)   # pin click
    y += 0.12 * modal([420.0, 760.0], [1.0, 0.5], [0.05, 0.04], dur)     # comb-box body
    return vel * y


def glock_note(freq: float, dur: float, vel: float = 1.0):
    """Soft glockenspiel / celesta bell (partials 1, 2.76, 5.4)."""
    y = modal([freq, freq * 2.76, freq * 5.40], [1.0, 0.3, 0.1], [0.9, 0.35, 0.15], dur)
    return vel * y


def chime_tone(freq: float, dur: float, tau: float = 0.5, bright: float = 1.0, attack: float = 0.003,
               rng=None) -> np.ndarray:
    """Soft struck-bell tone: fundamental + warm octave + inharmonic bar partials (1, 2.76, 4.07, 5.4)."""
    ratios = [1.0, 2.0, 2.76, 4.07, 5.40]
    amps = [1.0, 0.30, 0.20 * bright, 0.10 * bright, 0.06 * bright]
    taus = [tau, tau * 0.6, tau * 0.4, tau * 0.25, tau * 0.15]
    y = modal([freq * r for r in ratios], amps, taus, dur, detune_beat=0.9)
    y = y * curve([(0.0, 0.0), (attack, 1.0)], len(y), "cos")
    if rng is not None:
        add_at(y, 0.08 * bright * noise_burst(rng, 0.02, 2500, 9000, tau=0.002), 0.0)
    return y


def timpani(freq: float, dur: float, rng, vel: float = 1.0):
    """Tuned drum: pitch-dropping body + membrane partials + stick noise."""
    n = ns(dur)
    t = time_axis(n)
    f = freq * (1.0 + 0.25 * np.exp(-t / 0.04))
    y = np.sin(TAU * phase_cycles(f, n)) * np.exp(-t / 0.55)
    y += 0.4 * np.sin(TAU * phase_cycles(f * 1.5, n)) * np.exp(-t / 0.3)
    y += 0.25 * np.sin(TAU * phase_cycles(f * 2.0, n)) * np.exp(-t / 0.22)
    add_at(y, 0.5 * noise_burst(rng, min(dur, 0.1), 200, 2500, tau=0.012), 0.0)
    return vel * y


def noise_swell(rng, dur: float, lo: float, hi: float, peak_at: float = 0.8, power: float = 2.0):
    n = ns(dur)
    x = rng.standard_normal(n)
    x = band(x, lo, hi, order=2)
    x /= (np.sqrt(np.mean(x * x)) + 1e-12)
    t = np.arange(n) / n
    env = np.where(t < peak_at, (t / peak_at) ** power, np.cos(0.5 * np.pi * (t - peak_at) / (1 - peak_at + 1e-9)) ** 2)
    return x * env
