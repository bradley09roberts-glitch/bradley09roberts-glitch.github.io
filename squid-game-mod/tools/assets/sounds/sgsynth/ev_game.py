"""Game flow sounds: start horn, end buzzer, win fanfare, results sting."""
from __future__ import annotations

import numpy as np

from .core import add_at, curve, finish, ns, pulse, saw, sine, soft_clip
from .dsp import band, highpass, lowpass, peaking, reverb
from .instruments import (brass, chime_tone, metal_hit, noise_burst, noise_swell, string_swell, thump,
                          timpani)
from .registry import sound


@sound("game.start_horn", "Start horn blares")
def game_start_horn(v, rng):
    dur = 1.5
    n = ns(dur)
    out = np.zeros(n)
    # G2 + D3 + G3 (+ a quiet D4): a big, low, open-fifth brass chord with a lip scoop
    for f, g, det in ((98.0, 1.0, 5.0), (146.83, 0.85, 6.0), (196.0, 0.75, 7.0), (293.66, 0.22, 8.0)):
        out += g * brass(f, dur, rng, attack=0.075, release=0.30, f_open=2300.0, scoop=45.0, detune=det)
    out = peaking(out, 480.0, 1.3, 6.0)       # bell resonances of the horn
    out = peaking(out, 1250.0, 1.6, 4.0)
    add_at(out, 0.10 * noise_burst(rng, 0.3, 500, 3500, tau=0.08, attack=0.02), 0.0)   # breath
    out = highpass(out, 45.0, order=2)
    out = soft_clip(out * 1.15, 1.0)
    out = reverb(out, rt60=0.9, wet=0.14, predelay=0.010, damp=0.5, seed=31)
    return finish(out, -3.0, -11.0, 0.004, 0.07)


@sound("game.end_buzzer", "Buzzer sounds")
def game_end_buzzer(v, rng):
    dur = 1.2
    n = ns(dur)
    f = 205.0
    y = (0.6 * saw(f, n) + 0.6 * pulse(f * 1.012, n, 0.3) + 0.35 * saw(f * 2.0, n)
         + 0.25 * pulse(f * 3.02, n, 0.5))
    y *= (1.0 + 0.45 * sine(98.0, n)) / 1.45          # rough arena-buzzer amplitude flutter
    y = soft_clip(y * 1.8, 1.0)
    y = band(y, 130.0, 4000.0, order=4)
    y *= curve([(0.0, 0.0), (0.006, 1.0), (1.10, 1.0), (1.2, 0.0)], n, "cos")
    return finish(y, -3.0, -11.0, 0.0, 0.0)


# ----------------------------------------------------------------------- fanfare
def _bnote(out, f, t0, d, g=1.0, rng=None, bright=0.85, attack=0.03, release=0.10):
    add_at(out, g * brass(f, d, rng, attack=attack, release=release, bright=bright, f_open=2300.0, detune=6.0), t0)


@sound("game.win_fanfare", "Victory fanfare")
def game_win_fanfare(v, rng):
    dur = 3.0
    n = ns(dur)
    out = np.zeros(n)
    # --- lead (original: rising C-major triad, a held G, a falling answer, a high held C)
    lead = [
        (392.00, 0.00, 0.20), (523.25, 0.17, 0.20), (659.26, 0.34, 0.20),
        (783.99, 0.51, 0.66),
        (880.00, 1.13, 0.20), (783.99, 1.31, 0.20), (698.46, 1.49, 0.20),
        (659.26, 1.67, 0.17), (587.33, 1.82, 0.22),
        (1046.50, 2.02, 0.98),
    ]
    for f, t0, d in lead:
        _bnote(out, f, t0, d, 1.0, rng, release=min(0.12, d * 0.5))
    for f, t0, d in lead[:3]:            # opening triad doubled an octave lower for weight
        _bnote(out, f * 0.5, t0, d, 0.75, rng, bright=0.6, attack=0.04, release=min(0.12, d * 0.5))
    # --- harmony brass: thirds / chords under the long notes
    chords = [
        (0.51, 0.64, [261.63, 329.63, 392.00], 0.55),          # C
        (1.13, 0.54, [174.61, 220.00, 261.63], 0.50),          # F
        (1.67, 0.35, [196.00, 246.94, 293.66], 0.55),          # G
        (2.02, 0.98, [261.63, 329.63, 392.00, 523.25], 0.62),  # C (tutti)
    ]
    for t0, d, fs, g in chords:
        for f in fs:
            _bnote(out, f, t0, d, g, rng, bright=0.7, attack=0.045, release=min(0.2, d * 0.5))
    # --- bass + timpani
    for f, t0, d in ((130.81, 0.51, 0.64), (87.31, 1.13, 0.54), (98.0, 1.67, 0.35), (130.81, 2.02, 0.98)):
        add_at(out, 0.55 * brass(f, d, rng, attack=0.04, release=min(0.2, d * 0.5), bright=0.5, f_open=1400.0), t0)
    add_at(out, 0.9 * timpani(65.41, 1.0, rng), 0.51)
    add_at(out, 0.9 * timpani(65.41, 1.0, rng), 2.02)
    add_at(out, 0.6 * timpani(98.0, 0.5, rng), 1.67)
    # --- warm string pad under the second half
    for f in (130.81, 196.0, 261.63, 329.63, 392.0):
        add_at(out, 0.28 * string_swell(f, 3.0, rng, attack=0.55, release=0.5, cutoff=1900.0), 0.0)
    # --- cymbal-like shimmer + bell sparkle on the final chord
    add_at(out, 0.07 * noise_swell(rng, 1.0, 5500, 12000, peak_at=0.12, power=1.0), 2.0)
    for i, f in enumerate((1046.5, 1318.5, 1568.0, 2093.0)):
        add_at(out, 0.20 * chime_tone(f, 0.85, tau=0.35, bright=0.8, rng=rng), 2.08 + 0.085 * i)
    out = lowpass(out, 6200.0, order=2)
    out = reverb(out, rt60=1.7, wet=0.22, predelay=0.014, damp=0.5, seed=32)
    return finish(out, -3.0, -11.0, 0.003, 0.30)


# ----------------------------------------------------------------- results sting
@sound("game.results_sting", "Results sting")
def game_results_sting(v, rng):
    dur = 2.0
    n = ns(dur)
    out = np.zeros(n)
    tension = [73.42, 110.0, 146.83, 174.61, 207.65, 277.18]    # D2 A2 D3 F3 G#3 C#4 : a bitter D-minor cluster
    # swell: bowed-string-like ensemble that blooms towards the hit
    for i, f in enumerate(tension):
        add_at(out, 0.5 * string_swell(f, 1.45, rng, attack=1.15, release=0.06, cutoff=1300.0 + 180 * i,
                                       vib_cents=8.0), 0.0)
    add_at(out, 0.16 * noise_swell(rng, 1.4, 500, 6500, peak_at=0.97, power=2.8), 0.0)
    # sting: stab on the same chord (strings + brass), sub boom, metal hit
    for i, f in enumerate(tension[1:]):
        add_at(out, 0.42 * brass(f * 2.0, 0.6, rng, attack=0.012, release=0.32, f_open=3600.0, bright=1.0), 1.40)
        add_at(out, 0.40 * string_swell(f * 2.0, 0.6, rng, attack=0.015, release=0.35, cutoff=3200.0), 1.40)
    add_at(out, 1.1 * thump(78.0, 36.0, 0.6, 0.06, 0.28, click=0.4, rng=rng), 1.40)
    add_at(out, 0.7 * timpani(73.42, 0.6, rng), 1.40)
    add_at(out, 0.30 * metal_hit(rng, 410.0, 0.6, 0.30, 1.2), 1.40)
    out = reverb(out, rt60=1.5, wet=0.30, predelay=0.014, damp=0.55, seed=33)
    return finish(out, -3.0, -12.0, 0.01, 0.06)
