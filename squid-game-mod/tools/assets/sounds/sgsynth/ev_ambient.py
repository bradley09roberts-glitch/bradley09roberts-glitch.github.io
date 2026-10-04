"""Four seamless 20 s ambient loops (dorm, playground, industrial, alley).

Loops are *periodic by construction*: noise beds are synthesised in the frequency domain (exactly
periodic), every filter / reverb / echo is applied circularly, modulators complete a whole number
of cycles per loop and one-shot events (footsteps, birds, drips, barks) are placed with wrap-around
so their tails ring across the seam.  No cross-fade is needed, head and tail match exactly.
"""
from __future__ import annotations

import math

import numpy as np

from .core import (SR, TAU, add_at, colored, curve, normalize, ns, periodic_smooth_noise, phase_cycles,
                   remove_dc, sine, smooth_noise, stack, white)
from .dsp import band, bandpass, echo, highpass, lowpass, reverb, tv_biquad
from .instruments import chirp, footstep, metal_hit, modal, noise_burst, sparks, thump
from .registry import sound
from .voice import aspiration, build_tracks, harmonic_voice, phone

LOOP = 20.0
N = ns(LOOP)          # 882000 samples


def _unit(x):
    return x / (np.sqrt(np.mean(x * x)) + 1e-12)


def _calm_at_seam(mod, n):
    """Pull a 0..1 modulator to its mean around the loop point (flat slope), so head and tail match."""
    w = np.sin(np.pi * np.arange(n) / n) ** 2
    return 0.5 + (mod - 0.5) * w


def _finish_loop(x, peak_dbfs=-6.0):
    """DC-free, peak-normalised, **no fades** (the loop must stay periodic)."""
    return normalize(remove_dc(x), peak_dbfs, None)


# =================================================================================== dorm
@sound("ambient.dorm", "Dormitory ambience", stream=True)
def ambient_dorm(v, rng):
    n = N
    # --- low room tone: brown rumble + soft air-handling band, slowly "breathing"
    room = _unit(lowpass(colored(n, rng, 2.0), 170.0, order=2, circular=True))
    air = _unit(band(colored(n, rng, 1.0), 180.0, 900.0, order=2, circular=True))
    breathe = 1.0 + 0.16 * periodic_smooth_noise(n, rng, 2, 3)
    bed = (room * 0.55 + air * 0.20 * (1.0 + 0.3 * periodic_smooth_noise(n, rng, 3, 3))) * breathe
    # --- fluorescent / ballast hum (integer Hz -> periodic) and a faint high whine
    hum = (0.050 * sine(100.0, n, 0.3) + 0.030 * sine(200.0, n, 1.1) + 0.018 * sine(300.0, n, 2.0)
           + 0.010 * sine(500.0, n, 0.7)) * (1.0 + 0.05 * sine(0.15, n))
    # --- faint PA hiss + tiny speaker pops (no melody, no tones)
    pa = band(colored(n, rng, 1.0), 3500.0, 9500.0, order=2, circular=True)
    pa = _unit(pa) * 0.030 * (1.0 + 0.4 * periodic_smooth_noise(n, rng, 4, 3))
    for tp in (3.4, 8.9, 13.7, 17.2):
        add_at(pa, 0.020 * noise_burst(rng, 0.005, 700, 7000, tau=0.0010), tp, wrap=True)
    # --- distant footsteps (muffled by walls, passing by) + a far door
    steps = np.zeros(n)
    for t0, count, space in ((2.4, 9, 0.585), (11.2, 8, 0.60)):
        for i in range(count):
            amp = math.exp(-0.5 * ((i - count / 2.0) / (count / 2.6)) ** 2)
            f = rng.uniform(72, 104) * (1.08 if i % 2 else 1.0)
            tt = t0 + i * space + rng.uniform(-0.025, 0.025)
            add_at(steps, 0.55 * amp * footstep(rng, f=f * 1.5, dur=0.32, tone=1.0, grit=1.0), tt, wrap=True)
    add_at(steps, 0.5 * stack(thump(130.0, 58.0, 0.25, 0.02, 0.07),
                              0.6 * modal([560.0, 1210.0], [1.0, 0.5], [0.06, 0.04], 0.2)), 16.3, wrap=True)
    steps = lowpass(steps, 700.0, order=2, circular=True)
    steps = reverb(steps, rt60=1.5, wet=0.9, dry=0.35, predelay=0.02, damp=0.7, seed=61, circular=True)
    y = bed + hum + pa + steps * 1.5
    y = highpass(y, 24.0, order=2, circular=True)
    return _finish_loop(y, -6.0)


# ============================================================================= playground
def _bird(rng, kind: str) -> np.ndarray:
    """Short synthetic bird calls (sine glides + a little FM + a quiet 2nd harmonic)."""
    if kind == "sparrow":
        out = np.zeros(ns(0.6))
        for i in range(rng.integers(3, 5)):
            f0 = rng.uniform(2900, 3400)
            c = chirp(f0, f0 * rng.uniform(1.25, 1.5), 0.055, "exp") * 0.9
            c = c + 0.12 * chirp(2 * f0, 2.8 * f0, 0.055, "exp")
            add_at(out, c, 0.02 + i * 0.088)
        return out
    if kind == "whistle":
        out = np.zeros(ns(0.55))
        a = chirp(2500.0, 3500.0, 0.14, "lin") * 0.9
        b = chirp(3500.0, 2350.0, 0.22, "lin") * 0.9
        for seg, t0 in ((a, 0.02), (b, 0.15)):
            add_at(out, seg + 0.10 * np.roll(seg, 3), t0)
        return out
    if kind == "trill":
        m = ns(0.5)
        t = np.arange(m) / SR
        f = np.linspace(3500.0, 4150.0, m)
        y = np.sin(TAU * phase_cycles(f, m)) * (0.5 + 0.5 * np.sin(TAU * 25.0 * t)) ** 1.5
        return y * np.sin(np.pi * np.linspace(0, 1, m)) ** 0.6
    # distant dove: two low soft "coo" notes
    out = np.zeros(ns(0.9))
    for t0, f0 in ((0.02, 520.0), (0.34, 470.0), (0.60, 450.0)):
        m = ns(0.24)
        t = np.arange(m) / SR
        f = f0 * (1.0 + 0.03 * np.sin(TAU * 6.0 * t))
        y = np.sin(TAU * phase_cycles(f, m)) + 0.25 * np.sin(TAU * phase_cycles(2 * f, m))
        add_at(out, y * np.sin(np.pi * np.linspace(0, 1, m)) ** 1.5, t0)
    return out * 0.7


@sound("ambient.playground", "Playground ambience", stream=True)
def ambient_playground(v, rng):
    n = N
    gust = np.clip(0.5 + 0.5 * periodic_smooth_noise(n, rng, 3, 5), 0.0, 1.0)
    gust = _calm_at_seam(gust, n)
    fc = 480.0 + 320.0 * (2.0 * _calm_at_seam(0.5 + 0.5 * periodic_smooth_noise(n, rng, 2, 4), n) - 1.0)
    wind = _unit(tv_biquad(colored(n, rng, 1.0), "bp", fc, 0.6, circular=True))
    wind *= 0.30 + 0.70 * gust ** 1.4
    low = _unit(lowpass(colored(n, rng, 2.0), 140.0, order=2, circular=True)) * 0.30 * (0.5 + 0.5 * gust)
    leaves = _unit(band(white(n, rng), 3000.0, 8500.0, order=2, circular=True)) * 0.10 * gust ** 2
    crack = _unit(sparks(rng, LOOP, 60.0 * gust, lo=1500, hi=7000, tau=0.0012)) * 0.05 * gust
    # distant birds
    birds = np.zeros(n)
    calls = [(1.1, "sparrow", 0.30), (2.9, "whistle", 0.22), (5.0, "sparrow", 0.20), (6.6, "dove", 0.20),
             (8.2, "trill", 0.24), (10.3, "whistle", 0.30), (12.0, "sparrow", 0.26), (14.1, "trill", 0.18),
             (15.4, "dove", 0.18), (16.8, "sparrow", 0.22), (18.2, "whistle", 0.20), (19.4, "sparrow", 0.12)]
    for tc, kind, g in calls:
        add_at(birds, g * _bird(rng, kind), tc + rng.uniform(-0.15, 0.15), wrap=True)
    birds = lowpass(birds, 6800.0, order=2, circular=True)
    birds = reverb(birds, rt60=0.8, wet=0.55, dry=0.6, predelay=0.02, damp=0.5, seed=62, circular=True)
    y = wind + low + leaves + crack + birds * 1.4
    y = highpass(y, 30.0, order=2, circular=True)
    return _finish_loop(y, -6.0)


# ============================================================================== industrial
@sound("ambient.industrial", "Machinery hums", stream=True)
def ambient_industrial(v, rng):
    n = N
    # --- two slightly detuned motors (50.00 / 49.95 Hz: whole cycles per loop) beating once per loop
    def motor(f, g, ph):
        return g * sum(a * sine(f * k, n, ph * k) for k, a in ((1, 1.0), (2, 0.72), (3, 0.36), (4, 0.26), (6, 0.12)))
    hum = motor(50.0, 0.34, 0.1) + motor(49.95, 0.30, 0.6)
    hum = lowpass(hum, 900.0, order=2, circular=True) * (1.0 + 0.10 * sine(0.25, n))
    rumble = _unit(band(colored(n, rng, 2.0), 25.0, 160.0, order=2, circular=True)) * 0.8
    rumble *= 1.0 + 0.18 * sine(0.25, n, 0.4) + 0.08 * sine(0.5, n, 1.0)
    whirr = _unit(bandpass(white(n, rng), 520.0, 3.0, circular=True)) * 0.16 * (1.0 + 0.4 * sine(0.35, n, 2.0))
    fan = 0.003 * sine(2400.0 * (1.0 + 0.004 * sine(0.2, n)), n)
    steam = np.zeros(n)
    add_at(steam, 0.10 * _unit(band(white(int(1.4 * SR), rng), 2500.0, 8500.0, order=2)) *
           np.sin(np.pi * np.linspace(0, 1, int(1.4 * SR))) ** 2, 9.6, wrap=True)
    # --- water drips into a big echoing hall (+ two far-off metallic clanks)
    drips = np.zeros(n)
    for tc, g in ((1.8, 0.8), (4.9, 0.55), (7.6, 0.7), (11.9, 0.6), (14.2, 0.85), (18.0, 0.5)):
        m = ns(0.12)
        tt = np.arange(m) / SR
        f = 700.0 * (2.3 ** np.minimum(tt / 0.025, 1.0)) * rng.uniform(0.9, 1.15)
        d = np.sin(TAU * phase_cycles(f, m)) * np.exp(-tt / 0.028)
        add_at(drips, g * d, tc, wrap=True)
        add_at(drips, g * 0.25 * noise_burst(rng, 0.01, 1500, 9000, tau=0.0015), tc, wrap=True)
    drips = echo(drips, 0.37, feedback=0.5, wet=0.6, repeats=7, lp=3200.0, circular=True)
    clanks = np.zeros(n)
    add_at(clanks, 1.3 * metal_hit(rng, 310.0, 1.4, 0.6, 0.7), 6.3, wrap=True)
    add_at(clanks, 0.9 * metal_hit(rng, 430.0, 1.2, 0.5, 0.7), 15.6, wrap=True)
    far = lowpass(drips + clanks, 5200.0, order=2, circular=True)
    far = reverb(far, rt60=3.2, wet=0.8, dry=0.45, predelay=0.03, damp=0.55, seed=63, circular=True)
    y = hum + rumble + whirr + fan + steam + far * 1.1
    y = highpass(y, 22.0, order=2, circular=True)
    return _finish_loop(y, -6.0)


# ===================================================================================== alley
def _dog_bark(rng, dur: float = 0.22) -> np.ndarray:
    n = ns(dur)
    seg = phone("a", F=[680, 1250, 2500, 3500, 4600], BW=[170, 230, 320, 380, 420])
    seg2 = phone("o", F=[560, 1050, 2400, 3500, 4600], BW=[170, 230, 320, 380, 420])
    tracks = build_tracks([(seg, 0.0, 0.07), (seg2, 0.07, dur, 0.1)], n, child=1.0)
    f0 = curve([(0.0, 360.0), (0.04, 420.0), (dur, 250.0)], n, "cos")
    f0 = f0 * (1.0 + 0.05 * smooth_noise(n, rng, 55.0))                       # rough, growly
    y = harmonic_voice(f0, tracks, n, brightness_hz=3800.0, tilt=0.8, bw_scale=1.0, tune_f1=0.0)
    y = y + 0.55 * aspiration(n, rng, tracks)
    env = curve([(0.0, 0.0), (0.006, 1.0), (0.09, 0.7), (dur, 0.0)], n, "cos")
    return y * env


@sound("ambient.alley", "Night ambience", stream=True)
def ambient_alley(v, rng):
    n = N
    # --- crickets: carriers 4.1-5.2 kHz, every chirp train has a period of 20/k seconds (periodic loop)
    crickets = np.zeros(n)
    pulse = np.hanning(ns(0.013))
    specs = [(4400.0, 31, 0.10), (4750.0, 37, 0.08), (5050.0, 43, 0.06), (4150.0, 29, 0.07),
             (4900.0, 41, 0.05), (4600.0, 34, 0.06)]
    for fc, k, g in specs:
        period = LOOP / k
        phase0 = rng.uniform(0, period)
        pulses = int(rng.integers(3, 5))
        tone = np.sin(TAU * fc * np.arange(len(pulse)) / SR) * pulse
        for j in range(k):
            for p in range(pulses):
                add_at(crickets, g * (0.8 + 0.2 * rng.random()) * tone, phase0 + j * period + p * 0.034, wrap=True)
    crickets = lowpass(crickets, 7200.0, order=2, circular=True)
    crickets *= 1.0 + 0.25 * periodic_smooth_noise(n, rng, 2, 3)
    # --- faint wind in the alley + far-off city rumble
    gust = _calm_at_seam(np.clip(0.5 + 0.5 * periodic_smooth_noise(n, rng, 2, 4), 0.0, 1.0), n)
    wind = _unit(band(colored(n, rng, 1.0), 110.0, 800.0, order=2, circular=True)) * (0.10 + 0.22 * gust ** 1.5)
    city = _unit(lowpass(colored(n, rng, 2.0), 110.0, order=2, circular=True)) * 0.20
    # --- distant dog (two barks, a long pause, one bark) carried by the alley walls
    dog = np.zeros(n)
    for tb, g in ((5.8, 0.9), (6.25, 0.8), (14.6, 0.7)):
        add_at(dog, g * _dog_bark(rng), tb + rng.uniform(-0.02, 0.02), wrap=True)
    dog = lowpass(dog, 2300.0, order=2, circular=True)
    dog = reverb(dog, rt60=1.9, wet=0.8, dry=0.40, predelay=0.05, damp=0.6, seed=64, circular=True)
    # a distant tin can / bottle tick once in a while
    tick = np.zeros(n)
    add_at(tick, 0.5 * metal_hit(rng, 760.0, 0.5, 0.15, 0.5), 10.4, wrap=True)
    tick = reverb(lowpass(tick, 4500.0, order=2, circular=True), rt60=1.4, wet=0.9, dry=0.3, seed=65, circular=True)
    y = crickets * 1.0 + wind + city + dog * 0.9 + tick * 0.10
    y = highpass(y, 28.0, order=2, circular=True)
    return _finish_loop(y, -6.0)
