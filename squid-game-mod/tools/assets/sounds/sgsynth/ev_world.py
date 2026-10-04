"""World / game-object sounds: elimination, doors, glass, rope, marbles, needle + dalgona, danger.

Everything is synthesised from noise bursts, modal resonators (struck glass / metal / wood),
pitch-dropping sines (impacts), stick-slip friction and Poisson crackle - no samples.
"""
from __future__ import annotations

import math

import numpy as np

from .core import (SR, TAU, add_at, adsr, cents, colored, curve, db2lin, exp_decay, finish, make_rng, ns,
                   phase_cycles, pulse, saw, sine, smooth_noise, soft_clip, stack, tail_fade, time_axis,
                   white, zeros)
from .dsp import band, bandpass, highpass, lowpass, peaking, reverb, tv_biquad
from .instruments import (chirp, friction, metal_hit, modal, noise_burst, sparks, thump, whoosh,
                          wood_tock)
from .registry import sound


# ============================================================================ elimination
@sound("elimination.crack", "Elimination crack")
def elimination_crack(v, rng):
    n = ns(0.5)
    out = np.zeros(n)
    # bright snap (noise burst) + a fast pitched "zap" so it is clearly stylised, not a gunshot
    add_at(out, 1.6 * noise_burst(rng, 0.06, 1400, 9500, tau=0.010, attack=0.0003), 0.0)
    m = ns(0.05)
    f = np.exp(np.linspace(math.log(5200.0), math.log(700.0), m))
    add_at(out, 0.8 * sine(f, m) * np.exp(-np.arange(m) / SR / 0.012), 0.0)
    # retro "hit" square drop
    m = ns(0.16)
    tt = np.linspace(0.0, 1.0, m)
    fs = 900.0 * (170.0 / 900.0) ** (tt ** 0.5)
    add_at(out, 0.32 * pulse(fs, m, 0.5) * exp_decay(m, 0.055, 0.001), 0.003)
    # punchy low thump + mid body slap
    add_at(out, 0.85 * thump(190.0, 50.0, 0.3, 0.030, 0.085, click=0.3, rng=rng), 0.0)
    add_at(out, 0.6 * noise_burst(rng, 0.12, 250, 1400, tau=0.030, attack=0.001), 0.0)
    add_at(out, 0.09 * metal_hit(rng, 880.0, 0.3, 0.10, 0.6), 0.005)
    out = soft_clip(out * 0.9, 1.4)
    out = lowpass(out, 11000.0, order=2)
    return finish(out, -3.0, -13.0, 0.0003, 0.14)


@sound("elimination.buzzer", "Elimination alarm")
def elimination_buzzer(v, rng):
    dur = 0.9
    n = ns(dur)
    out = np.zeros(n)
    steps = [(0.000, 0.235, 520.0, 500.0), (0.245, 0.480, 390.0, 372.0), (0.490, 0.900, 262.0, 190.0)]
    for a, b, f0, f1 in steps:
        m = ns(b - a)
        f = np.linspace(f0, f1, m)
        y = 0.6 * saw(f, m) + 0.6 * pulse(f * 1.008, m, 0.3) + 0.3 * saw(f * 2.0, m)
        y *= (1.0 + 0.35 * sine(31.0, m)) / 1.35
        y = soft_clip(y * 1.8, 1.0)
        y *= curve([(0, 0), (0.004, 1), ((b - a) - 0.02, 1), ((b - a), 0)], m, "cos")
        add_at(out, y, a)
    out = band(out, 140.0, 3600.0, order=4)
    out = reverb(out, rt60=0.25, wet=0.08, predelay=0.004, damp=0.6, seed=41)
    return finish(out, -3.0, -11.0, 0.002, 0.05)


@sound("elimination.body_fall", "Body falls", variants=2)
def elimination_body_fall(v, rng):
    n = ns(0.4)
    out = np.zeros(n)
    f0 = (118.0, 102.0)[v]
    add_at(out, 1.2 * thump(f0, 46.0, 0.25, 0.03, 0.075, click=0.0), 0.0)
    add_at(out, 0.8 * noise_burst(rng, 0.2, 90, 520, tau=0.05, attack=0.002), 0.0)           # body mass
    cloth = band(rng.standard_normal(ns(0.3)), 700, 3600, order=2)
    cloth *= curve([(0, 0), (0.035, 1.0), (0.12, 0.45), (0.3, 0)], len(cloth), "cos")
    add_at(out, 0.20 * cloth / (np.sqrt(np.mean(cloth ** 2)) + 1e-9), 0.0)
    add_at(out, 0.45 * thump(f0 * 1.3, 62.0, 0.12, 0.02, 0.04), 0.12 + 0.012 * v)           # limbs landing
    add_at(out, 0.07 * noise_burst(rng, 0.12, 1500, 5000, tau=0.03, attack=0.01), 0.17)       # settling cloth
    out = lowpass(out, 4200.0, order=4)
    out = reverb(out, rt60=0.3, wet=0.08, predelay=0.004, damp=0.7, seed=42 + v)
    return finish(out, -3.0, -14.0, 0.0005, 0.07)


# ================================================================================== doors
def _hiss(rng, d, level, peak_at=0.12, lo=2200.0, hi=9500.0, power=1.4):
    m = ns(d)
    x = band(rng.standard_normal(m), lo, hi, order=2)
    x /= (np.sqrt(np.mean(x * x)) + 1e-9)
    env = curve([(0, 0), (d * peak_at, 1.0), (d, 0.0)], m, "cos") ** power
    return level * x * env


def _servo(n, f_curve, level=1.0, rough=0.0, rng=None):
    f = f_curve
    y = 0.50 * sine(f, n) + 0.26 * sine(f * 2.0, n) + 0.14 * sine(f * 3.02, n) + 0.20 * pulse(f * 0.5, n, 0.3)
    if rough and rng is not None:
        y += rough * band(rng.standard_normal(n), 400, 2800, order=2) * 0.4
    return level * y


def _door(rng, opening: bool):
    dur = 1.2
    n = ns(dur)
    out = np.zeros(n)
    t = time_axis(n)
    if opening:
        f_curve = curve([(0.0, 260), (0.05, 260), (0.40, 720), (0.95, 690), (1.12, 240), (1.2, 240)], n, "cos")
        env = curve([(0, 0), (0.05, 0), (0.14, 1), (0.98, 1), (1.12, 0), (1.2, 0)], n, "cos")
    else:
        f_curve = curve([(0.0, 250), (0.04, 250), (0.30, 640), (0.85, 560), (1.02, 300), (1.2, 300)], n, "cos")
        env = curve([(0, 0), (0.04, 0), (0.12, 1), (0.88, 1), (1.02, 0), (1.2, 0)], n, "cos")
    out += lowpass(_servo(n, f_curve, 0.34, rough=0.5, rng=rng), 3200.0, order=4) * env
    # sliding carriage: rumble + rolling ticks
    rumble = lowpass(colored(n, rng, 2.0), 520.0, order=2)
    renv = curve([(0, 0), (0.06, 0.0), (0.16, 1.0), (1.00, 0.9), (1.08, 0.0), (1.2, 0)], n, "cos")
    out += 0.55 * rumble * renv
    out += 0.20 * sparks(rng, dur, 85.0, lo=700, hi=3200, tau=0.002) * renv
    if opening:
        add_at(out, stack(modal([1800, 3600], [0.6, 0.3], [0.007, 0.004], 0.04),
                          0.7 * thump(160.0, 90.0, 0.06, 0.012, 0.02)), 0.0)          # latch release
        add_at(out, _hiss(rng, 0.45, 1.05, 0.10), 0.01)                                # pneumatic "psssht"
        add_at(out, _hiss(rng, 0.22, 0.30, 0.45, 1800.0, 7000.0), 0.97)               # air bleed at the stop
        add_at(out, stack(0.9 * thump(105.0, 52.0, 0.16, 0.025, 0.055),
                          0.35 * noise_burst(rng, 0.05, 300, 1600, tau=0.012)), 1.02)  # end stop
    else:
        add_at(out, _hiss(rng, 0.14, 0.45, 0.2), 0.0)
        add_at(out, _hiss(rng, 0.30, 0.95, 0.12), 0.95)                                # pneumatic seal
        add_at(out, stack(modal([1500, 3100], [0.7, 0.35], [0.008, 0.005], 0.05),
                          1.1 * thump(130.0, 55.0, 0.14, 0.02, 0.05),
                          modal([620, 1500], [0.6, 0.3], [0.05, 0.03], 0.12) * 0.5), 1.00)  # latch engages
    out = lowpass(out, 9500.0, order=2)
    out = reverb(out, rt60=0.45, wet=0.10, predelay=0.006, damp=0.65, seed=43 + int(opening))
    return finish(out, -3.0, -14.0, 0.004, 0.06)


@sound("door.slide_open", "Door slides open")
def door_slide_open(v, rng):
    return _door(rng, True)


@sound("door.slide_close", "Door slides shut")
def door_slide_close(v, rng):
    return _door(rng, False)


@sound("door.lock", "Door locks")
def door_lock(v, rng):
    n = ns(0.4)
    out = np.zeros(n)
    add_at(out, stack(modal([2100, 4300], [0.7, 0.4], [0.006, 0.004], 0.04),
                      0.5 * noise_burst(rng, 0.02, 3000, 10000, tau=0.0015)), 0.0)          # "ka"
    add_at(out, stack(1.25 * thump(135.0, 42.0, 0.24, 0.02, 0.07, click=0.3, rng=rng),
                      modal([470, 1180, 2350, 3900], [0.8, 0.6, 0.35, 0.2], [0.14, 0.09, 0.05, 0.03], 0.3),
                      0.5 * noise_burst(rng, 0.06, 400, 5000, tau=0.012)), 0.07)            # heavy "CHUNK"
    add_at(out, 0.22 * modal([1800, 3100], [0.6, 0.4], [0.01, 0.006], 0.05), 0.22)           # settling rattle
    add_at(out, 0.13 * modal([1500, 2700], [0.6, 0.4], [0.01, 0.006], 0.05), 0.27)
    out = reverb(out, rt60=0.4, wet=0.14, predelay=0.004, damp=0.6, seed=45)
    return finish(out, -3.0, -14.0, 0.0004, 0.08)


# =================================================================================== glass
def _glass_tick(rng, a=1.0, bright=1.0):
    r = rng
    f1 = r.uniform(2300, 3700)
    f2 = r.uniform(4400, 6400)
    f3 = r.uniform(7000, 9800)
    taus = [r.uniform(0.006, 0.022), r.uniform(0.004, 0.014), r.uniform(0.002, 0.008)]
    y = modal([f1, f2, f3], [1.0, 0.6 * bright, 0.35 * bright], taus, 0.08)
    return a * stack(y, 0.5 * bright * noise_burst(rng, 0.01, 4500, 14000, tau=0.0008))


@sound("glass.crack", "Glass cracks", variants=3)
def glass_crack(v, rng):
    dur = 0.7
    n = ns(dur)
    out = np.zeros(n)
    # accelerating crackle of hairline fractures (inhomogeneous Poisson process)
    t = 0.0
    rate0, rate1 = (11.0, 9.0, 8.0)[v], (90.0, 75.0, 105.0)[v]
    while t < dur - 0.08:
        u = t / dur
        rate = rate0 + (rate1 - rate0) * u ** 2
        t += rng.exponential(1.0 / rate)
        if t < dur - 0.08:
            add_at(out, _glass_tick(rng, a=rng.uniform(0.15, 0.55) * (0.5 + u)), t)
    # a bigger crack as the pane gives
    tc = (0.50, 0.44, 0.55)[v]
    add_at(out, _glass_tick(rng, a=1.1, bright=1.2), tc)
    add_at(out, 0.5 * thump(240.0, 120.0, 0.08, 0.015, 0.03), tc)
    # strained-glass creak (stick-slip squeal) underneath
    sq = curve([(0, 1700), (0.35, 2100), (dur, 2500)], n, "lin") * (1.0 + 0.04 * smooth_noise(n, rng, 14.0))
    squeal = sine(sq, n) * (0.55 + 0.45 * sine(23.0 + 5 * v, n)) * curve([(0, 0), (0.1, 0.5), (0.5, 1.0), (dur, 0)], n, "cos")
    out += 0.10 * squeal
    out = highpass(out, 350.0, order=2)
    out = reverb(out, rt60=0.3, wet=0.10, predelay=0.003, damp=0.3, seed=46 + v)
    return finish(out, -3.0, -14.0, 0.002, 0.08)


@sound("glass.shatter", "Glass shatters")
def glass_shatter(v, rng):
    dur = 1.0
    n = ns(dur)
    out = np.zeros(n)
    # impact: sharp smash + pane thud + low flex
    add_at(out, 1.1 * noise_burst(rng, 0.08, 900, 12000, tau=0.008, attack=0.0002), 0.0)
    add_at(out, 0.5 * thump(250.0, 85.0, 0.14, 0.025, 0.05), 0.0)
    add_at(out, 0.3 * modal([430.0, 790.0, 1210.0], [1.0, 0.6, 0.4], [0.09, 0.07, 0.05], 0.3), 0.0)
    # cascade of shards: Poisson with exponentially falling rate, log-uniform pitch / decay
    t = 0.0
    while t < 0.9:
        rate = 1100.0 * math.exp(-t / 0.17) + 40.0
        t += rng.exponential(1.0 / rate)
        f = math.exp(rng.uniform(math.log(1800), math.log(10500)))
        tau = rng.uniform(0.012, 0.075) * (3500.0 / f) ** 0.5
        a = float(np.exp(rng.normal(-0.9, 0.6))) * (0.6 + 0.4 * math.exp(-t / 0.4))
        sh = modal([f, f * 2.76], [1.0, 0.25], [tau, tau * 0.5], min(0.3, tau * 6))
        add_at(out, a * sh, t)
    # bigger pieces tinkling down later
    for tt in np.sort(rng.uniform(0.25, 0.85, 9)):
        f = rng.uniform(1300, 3000)
        add_at(out, rng.uniform(0.25, 0.6) * modal([f, f * 2.4], [1.0, 0.4], [0.09, 0.05], 0.4), tt)
    out = highpass(out, 300.0, order=2)
    out = reverb(out, rt60=0.55, wet=0.16, predelay=0.005, damp=0.2, seed=47)
    return finish(out, -3.0, -13.0, 0.0003, 0.12)


# =================================================================================== rope
@sound("rope.creak", "Rope creaks", variants=2)
def rope_creak(v, rng):
    dur = 0.8
    n = ns(dur)
    fp = curve([(0, 52 + 8 * v), (0.28, 82 + 6 * v), (0.6, 70), (dur, 48)], n, "cos")
    r1 = curve([(0, 820), (0.35, 1280 + 120 * v), (dur, 980)], n, "cos")
    r2 = curve([(0, 1900), (0.4, 2300), (dur, 1800)], n, "cos")
    y = friction(rng, dur, fp, [(r1, 1.0), (r2, 0.45), (3300.0, 0.18)], q=9.0, rough=0.6, jitter=0.28)
    y += 0.25 * band(rng.standard_normal(n), 600, 3200, order=2) * (0.3 + 0.7 * np.abs(sine(11.0 + 3 * v, n)))
    y *= curve([(0, 0), (0.07, 1.0), (0.55, 0.85), (dur, 0)], n, "cos")
    y = lowpass(y, 5200.0, order=2)
    y = reverb(y, rt60=0.25, wet=0.07, predelay=0.003, damp=0.6, seed=48 + v)
    return finish(y, -3.0, -15.0, 0.005, 0.08)


@sound("rope.strain", "Rope strains")
def rope_strain(v, rng):
    dur = 1.2
    n = ns(dur)
    fp = curve([(0, 21), (0.5, 38), (dur, 25)], n, "cos")
    r1 = curve([(0, 240), (0.6, 330), (dur, 250)], n, "cos")
    y = friction(rng, dur, fp, [(r1, 1.0), (540.0, 0.8), (1080.0, 0.5), (2100.0, 0.2)], q=6.0, rough=0.8, jitter=0.35)
    # deep groan of the fibres under load + crackling strands
    grn = 62.0 + 9.0 * smooth_noise(n, rng, 3.0)
    y += 0.35 * (sine(grn, n) + 0.5 * sine(grn * 2.0, n)) * (0.6 + 0.4 * sine(5.0, n))
    cr = sparks(rng, dur, 30.0 + 20.0 * np.sin(TAU * 1.7 * time_axis(n)) ** 2, lo=900, hi=5200, tau=0.002) * 0.9
    y = y / (np.sqrt(np.mean(y * y)) + 1e-9) * 0.6 + cr * 0.45
    y *= curve([(0, 0), (0.18, 0.7), (0.62, 1.0), (dur, 0)], n, "cos")
    y = lowpass(y, 4600.0, order=2)
    y = reverb(y, rt60=0.3, wet=0.08, predelay=0.004, damp=0.65, seed=50)
    return finish(y, -3.0, -14.0, 0.01, 0.12)


# ================================================================================ marbles
@sound("marble.click", "Marbles click", variants=3)
def marble_click(v, rng):
    n = ns(0.2)
    out = np.zeros(n)
    f = (2900.0, 3250.0, 2650.0)[v] * rng.uniform(0.97, 1.03)
    for t0, g, fs in ((0.0, 1.0, 1.0), (0.011 + 0.003 * v, 0.55, 1.07)):
        y = modal([f * fs, f * fs * 2.41, f * fs * 4.13, f * fs * 0.55], [1.0, 0.55, 0.3, 0.35],
                  [0.03, 0.02, 0.012, 0.045], 0.19)
        y = stack(y, 0.5 * noise_burst(rng, 0.01, 4500, 14000, tau=0.0008))
        add_at(out, g * y, t0)
    out = reverb(out, rt60=0.2, wet=0.06, predelay=0.002, damp=0.2, seed=51 + v)
    return finish(out, -3.0, -15.0, 0.0003, 0.05)


@sound("marble.drop", "Marble drops", variants=2)
def marble_drop(v, rng):
    n = ns(0.3)
    out = np.zeros(n)
    times = [(0.0, 1.0), (0.082, 0.56), (0.140, 0.31), (0.181, 0.17), (0.206, 0.09)]
    if v == 1:
        times = [(0.0, 1.0), (0.095, 0.5), (0.155, 0.27), (0.195, 0.13), (0.215, 0.07)]
    f0 = (2100.0, 2500.0)[v]
    for i, (t0, a) in enumerate(times):
        fk = f0 * (1.0 + 0.05 * i)
        y = modal([fk, fk * 2.3, 760.0], [1.0, 0.4, 0.6], [0.014, 0.008, 0.02], 0.1)
        y = stack(y, 0.7 * noise_burst(rng, 0.01, 2500, 11000, tau=0.0012))
        add_at(out, a * y, t0)
    out = reverb(out, rt60=0.22, wet=0.07, predelay=0.002, damp=0.5, seed=53 + v)
    return finish(out, -3.0, -15.0, 0.0003, 0.06)


@sound("marble.roll", "Marble rolls", variants=2)
def marble_roll(v, rng):
    dur = 1.0
    n = ns(dur)
    rate = curve([(0, 24 + 4 * v), (0.7, 16), (dur, 7)], n, "lin")      # rolling slows as it settles
    base = band(rng.standard_normal(n), 140, 1700, order=2)
    ph = phase_cycles(rate, n)
    mod = (0.55 + 0.45 * np.sin(TAU * ph)) * (0.7 + 0.3 * smooth_noise(n, rng, 40.0))
    grit_rate = curve([(0, 560), (dur, 150)], n, "lin")
    grit = sparks(rng, dur, grit_rate, lo=900, hi=6500, tau=0.0008)
    y = base / (np.sqrt(np.mean(base ** 2)) + 1e-9) * mod + 0.55 * grit / (np.sqrt(np.mean(grit ** 2)) + 1e-9)
    # small dirt pebbles knocking now and then
    for tt in np.sort(rng.uniform(0.05, 0.8, 5)):
        add_at(y, rng.uniform(0.4, 0.9) * modal([rng.uniform(900, 1700)], [1.0], [0.012], 0.06), tt)
    y *= curve([(0, 0), (0.05, 1.0), (0.72, 0.9), (dur, 0)], n, "cos")
    y = lowpass(y, 7500.0, order=2)
    return finish(y, -3.0, -17.0, 0.01, 0.10)


# ===================================================================== needle + dalgona
@sound("needle.scratch", "Needle scratches", variants=3)
def needle_scratch(v, rng):
    dur = 0.3
    n = ns(dur)
    out = np.zeros(n)
    strokes = [[(0.010, 0.135), (0.150, 0.285)], [(0.015, 0.11), (0.12, 0.2), (0.215, 0.29)],
               [(0.005, 0.15), (0.17, 0.27)]][v]
    for a, b in strokes:
        m = ns(b - a)
        x = band(rng.standard_normal(m), 3300, 10500, order=2)
        x /= (np.sqrt(np.mean(x * x)) + 1e-9)
        gr_rate = np.linspace(90.0, 135.0, m) * rng.uniform(0.9, 1.1)
        grain = (0.5 + 0.5 * np.sin(TAU * phase_cycles(gr_rate, m) + rng.random() * 6.0)) ** 2
        env = np.sin(np.pi * np.linspace(0, 1, m)) ** 0.8
        add_at(out, 0.8 * x * grain * env, a)
        add_at(out, 0.18 * bandpass(x * env, 5200.0, 14.0) * 3.0, a)   # thin sugar-sheet resonance
        for _ in range(rng.integers(2, 5)):                              # brittle crumbs
            add_at(out, rng.uniform(0.3, 0.8) * noise_burst(rng, 0.01, 2500, 12000, tau=0.0012),
                   rng.uniform(a, b - 0.01))
    out = highpass(out, 1800.0, order=2)
    return finish(out, -3.0, -17.0, 0.004, 0.03)


@sound("dalgona.crack", "Candy cracks", variants=2)
def dalgona_crack(v, rng):
    n = ns(0.4)
    out = np.zeros(n)
    f = (3300.0, 3700.0)[v]
    snap = stack(1.0 * noise_burst(rng, 0.05, 1800, 13000, tau=0.006, attack=0.0002),
                 modal([f, f * 1.55, f * 2.3], [0.7, 0.5, 0.3], [0.025, 0.018, 0.01], 0.12))
    add_at(out, snap, 0.0)
    ts = np.sort(rng.uniform(0.012, 0.16, 7))
    for i, tt in enumerate(ts):                                          # micro-fractures running through the sugar
        add_at(out, (0.6 - 0.05 * i) * noise_burst(rng, 0.012, 3000, 13000, tau=0.0012), tt)
    add_at(out, 0.14 * modal([4500.0 + 300 * v, 6100.0], [1.0, 0.5], [0.09, 0.05], 0.3), 0.0)   # sugar-glass ring
    add_at(out, 0.35 * thump(420.0, 190.0, 0.04, 0.008, 0.015), 0.0)
    out = highpass(out, 600.0, order=2)
    out = reverb(out, rt60=0.22, wet=0.07, predelay=0.002, damp=0.4, seed=55 + v)
    return finish(out, -3.0, -16.0, 0.0003, 0.08)


@sound("dalgona.snap", "Cookie snaps", variants=2)
def dalgona_snap(v, rng):
    n = ns(0.6)
    out = np.zeros(n)
    add_at(out, stack(1.0 * noise_burst(rng, 0.09, 800, 7000, tau=0.012, attack=0.0003),
                      0.7 * thump(540.0, 210.0, 0.07, 0.010, 0.022)), 0.0)                # the snap itself
    # crumbling: a spray of small ticks thinning out
    t = 0.02
    while t < 0.36:
        t += rng.exponential(1.0 / (160.0 * math.exp(-(t - 0.02) / 0.15) + 25.0))
        a = rng.uniform(0.15, 0.45) * math.exp(-(t - 0.02) / 0.25)
        add_at(out, a * noise_burst(rng, 0.02, 1200, 7500, tau=0.003), t)
    # two pieces falling to the table
    for tt, f0, a in ((0.37 + 0.02 * v, 1450.0, 0.7), (0.49 + 0.02 * v, 1750.0, 0.5), (0.57, 2100.0, 0.3)):
        add_at(out, a * stack(modal([f0, f0 * 2.05], [1.0, 0.4], [0.02, 0.01], 0.07),
                              0.8 * thump(330.0, 150.0, 0.05, 0.01, 0.02)), tt)
    out = lowpass(out, 9000.0, order=2)
    out = reverb(out, rt60=0.25, wet=0.07, predelay=0.003, damp=0.55, seed=57 + v)
    return finish(out, -3.0, -15.0, 0.0004, 0.09)


# ==================================================================================== danger
@sound("danger.heartbeat", "Heart beats")
def danger_heartbeat(v, rng):
    n = ns(1.0)
    out = np.zeros(n)
    # lub
    add_at(out, stack(1.0 * thump(88.0, 44.0, 0.3, 0.035, 0.085),
                      0.5 * lowpass(noise_burst(rng, 0.12, 40, 260, tau=0.03, attack=0.003), 220.0, order=2),
                      0.18 * noise_burst(rng, 0.03, 300, 1400, tau=0.006)), 0.0)
    # dub (a little softer and higher)
    add_at(out, stack(0.72 * thump(104.0, 52.0, 0.26, 0.03, 0.07),
                      0.35 * lowpass(noise_burst(rng, 0.1, 50, 300, tau=0.025, attack=0.003), 260.0, order=2),
                      0.12 * noise_burst(rng, 0.03, 300, 1400, tau=0.006)), 0.285)
    out = lowpass(out, 900.0, order=4)
    out = reverb(out, rt60=0.35, wet=0.10, predelay=0.004, damp=0.8, seed=58)
    return finish(out, -3.0, -17.0, 0.003, 0.06)


@sound("danger.sting", "Ominous sting")
def danger_sting(v, rng):
    dur = 0.8
    n = ns(dur)
    out = np.zeros(n)
    cluster = [130.81, 138.59, 185.0, 196.0, 261.63, 277.18, 370.0]   # C3 C#3 F#3 G3 C4 C#4 F#4 : minor-2nd / tritone stack
    t = time_axis(n)
    for i, f in enumerate(cluster):
        y = 0.45 * saw(f * cents(rng.uniform(-6, 6)), n) + 0.45 * saw(f * cents(rng.uniform(-6, 6)), n)
        cut = curve([(0, 6000), (0.05, 3800), (0.25, 1400), (dur, 600)], n, "exp")
        y = tv_biquad(y, "lp", cut, 1.0)
        out += y * curve([(0, 0), (0.006, 1.0), (0.12, 0.7), (dur, 0.0)], n, "cos") * 0.5
    add_at(out, 0.9 * thump(95.0, 38.0, 0.6, 0.06, 0.22, click=0.4, rng=rng), 0.0)
    add_at(out, 0.35 * metal_hit(rng, 520.0, 0.7, 0.28, 1.2), 0.0)
    add_at(out, 0.28 * whoosh(rng, 0.7, 7000.0, 600.0, q=0.9, peak_at=0.12), 0.0)    # reversed-cymbal-ish air
    out = highpass(out, 40.0, order=2)
    out = reverb(out, rt60=1.0, wet=0.24, predelay=0.010, damp=0.55, seed=59)
    return finish(out, -3.0, -12.0, 0.002, 0.10)
