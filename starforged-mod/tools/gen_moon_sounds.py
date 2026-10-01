#!/usr/bin/env python3
"""Moonforged sound synthesizer - moonlight, tides, glassy crystal and the Pale Matriarch. Reuses gen_sounds.py's DSP helpers."""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_sounds import (SR, bandpass, bell, env, expdecay, glide, highpass, lowpass, mixall, noise, osc, reverb, sumx,  # noqa: E402
                        sweep_filter, t_axis, thump, write)

rng = np.random.default_rng(9876)


def at(x, delay):
    return np.concatenate([np.zeros(int(SR * delay)), x])


def whoosh(dur, f0, f1, width=0.6):
    return sweep_filter(noise(dur), f0, f1, width) * env(dur, dur * 0.25, dur * 0.6)


def chime(freqs, dur=1.6, step=0.1, gain=0.35, bright=0.9):
    return mixall(*[at(bell(f, dur, bright) * gain, i * step) for i, f in enumerate(freqs)])


def wave(dur, gain=1.0):
    """Surf: swelling, filtered noise."""
    t = t_axis(dur)
    swell = np.sin(np.pi * t / dur) ** 1.5
    return (lowpass(noise(dur), 1800) * 0.8 + bandpass(noise(dur), 2000, 7000) * 0.3) * swell * gain


def pad(dur, freqs, gain=0.3):
    return sumx(osc(f, dur) * gain for f in freqs) * env(dur, dur * 0.3, dur * 0.5)


def chirp(dur, f0, f1, vib=20.0):
    t = t_axis(dur)
    f = glide(f0, f1, dur, 0.7) * (1 + 0.03 * np.sin(2 * np.pi * vib * t))
    return osc(f, dur) * env(dur, 0.01, dur * 0.6)


def glass(dur, gain=1.0):
    return mixall(bandpass(noise(dur), 2500, 9000) * expdecay(dur, 9) * (rng.random(int(SR * dur)) > 0.55) * 1.2 * gain,
                  *[at(bell(rng.uniform(1200, 3200), 0.9, 1.0) * 0.18 * gain, rng.random() * dur * 0.4) for _ in range(7)])


def sounds():
    S = {}
    S["moon_gateway_form"] = reverb(mixall(thump(80, 30, 1.6, 2.0) * 1.2, whoosh(2.4, 200, 4500) * 1.0, chime((392, 523, 659, 784, 1047), 2.6, 0.15)), 2.4, 0.5)
    S["moon_gateway_travel"] = reverb(mixall(sweep_filter(noise(1.6), 600, 5000, 0.4) * env(1.6, 0.1, 1.0), chime((1047, 1319, 1568), 1.2, 0.1, 0.25)), 1.6, 0.5)
    S["moon_tide_high"] = reverb(mixall(wave(3.0, 1.6), pad(3.0, (130.8, 196, 261.6)), at(chime((523, 659, 784), 2.0, 0.3, 0.25), 0.6)), 2.5, 0.5)
    S["moon_tide_low"] = reverb(mixall(wave(2.6, 1.0), pad(2.6, (110, 164.8, 220)), at(chime((784, 659, 523), 2.0, 0.3, 0.2), 0.5)), 2.5, 0.5)
    S["moon_clam_open"] = reverb(mixall(bandpass(noise(0.4), 300, 1500) * expdecay(0.4, 10) * 1.2, at(bell(1568, 1.0, 1.0) * 0.4, 0.1)), 0.8, 0.3)
    S["moon_gravity_plate"] = reverb(mixall(chirp(0.7, 200, 1400, 6) * 0.6, whoosh(0.7, 300, 4000) * 1.2), 0.8, 0.35)
    S["moon_ring_turn"] = reverb(mixall(bandpass(noise(0.3), 600, 3000) * expdecay(0.3, 10) * (rng.random(int(SR * 0.3)) > 0.8) * 2.0,
                                        bell(880, 0.6, 1.0) * 0.3), 0.6, 0.3)
    S["moon_orrery_align"] = reverb(mixall(chime((262, 330, 392, 523, 659, 784, 1047), 3.0, 0.18, 0.35), pad(3.0, (130.8, 196)), whoosh(3.0, 200, 3000) * 0.5), 3.0, 0.55)
    S["moon_orrery_fail"] = reverb(mixall(chime((440, 415, 370, 311), 1.6, 0.12, 0.35, 0.6), lowpass(osc(55, 1.4, "saw"), 300) * env(1.4, 0.05, 1.0) * 0.6,
                                          chirp(1.2, 1200, 150, 4) * 0.4), 1.6, 0.45)
    S["moon_seal_open"] = reverb(mixall(glass(2.4, 1.2), chime((523, 659, 784, 1047, 1319), 2.2, 0.16, 0.35), whoosh(2.4, 300, 5000) * 0.8), 2.2, 0.5)
    S["moon_altar_activate"] = reverb(mixall(chime((196, 247, 294, 392, 494, 587, 784), 3.2, 0.22, 0.45, 0.5), pad(3.4, (98, 147)) * 1.4,
                                             wave(3.4, 0.8)), 3.0, 0.55)

    S["moon_glaive_throw"] = reverb(whoosh(1.0, 400, 3500) * (0.6 + 0.4 * np.sin(2 * np.pi * 10 * t_axis(1.0))) * 1.6, 0.7, 0.3)
    S["moon_staff_fire"] = reverb(mixall(chirp(0.4, 1800, 900, 15) * 0.5, bell(1319, 0.6, 1.0) * 0.3, whoosh(0.4, 800, 5000)), 0.7, 0.35)
    S["moon_staff_moonfall"] = reverb(mixall(chime((262, 392, 523, 784), 1.6, 0.12, 0.35), at(lowpass(noise(2.0), 400) * env(2.0, 0.6, 1.0) * 1.4, 0.3),
                                             at(thump(70, 25, 1.6, 2.0) * 1.2, 1.4)), 2.0, 0.45)
    S["moon_dagger_phase"] = reverb(mixall(whoosh(0.35, 1000, 8000) * 1.8, chirp(0.3, 2500, 1200, 20) * 0.3), 0.4, 0.25)
    S["moon_dagger_detonate"] = reverb(mixall(glass(0.6, 1.0), bandpass(noise(0.2), 2000, 9000) * expdecay(0.2, 20) * 1.4), 0.6, 0.3)
    S["moon_bolt_ricochet"] = reverb(mixall(bell(2093, 0.4, 1.2) * 0.4, bandpass(noise(0.1), 3000, 9000) * expdecay(0.1, 30) * 1.4), 0.4, 0.25)
    S["moon_bell_ring"] = reverb(mixall(*[bell(f, 4.0, 0.6) * g for f, g in ((523, 0.5), (1047, 0.3), (1319, 0.2), (2093, 0.12))],
                                        sweep_filter(noise(4.0), 3000, 300, 0.3) * env(4.0, 0.01, 3.5) * 0.2), 3.0, 0.6)
    S["moon_hook_fire"] = reverb(mixall(whoosh(0.4, 500, 4000) * 1.2, bandpass(noise(0.4), 1500, 5000) * (rng.random(int(SR * 0.4)) > 0.9) * 1.5), 0.4, 0.2)
    S["moon_tide_wave"] = reverb(mixall(wave(1.8, 2.0), whoosh(1.8, 200, 2500) * 0.8, chime((659, 784), 1.2, 0.2, 0.2)), 1.4, 0.4)
    S["moon_ward_break"] = reverb(mixall(glass(1.0, 1.4), bell(784, 1.2, 1.0) * 0.4), 1.0, 0.4)

    S["skimmer_ambient"] = reverb(mixall(lowpass(noise(1.2), 500) * env(1.2, 0.3, 0.6) * 1.2, bandpass(noise(1.2), 2000, 6000) * env(1.2, 0.3, 0.6) * 0.2), 0.6, 0.2)
    S["skimmer_erupt"] = reverb(mixall(thump(110, 50, 0.8, 4) * 1.5, lowpass(noise(1.0), 1200) * expdecay(1.0, 4) * 1.6,
                                       chirp(0.5, 300, 900, 8) * 0.4), 0.8, 0.3)
    S["skimmer_death"] = reverb(mixall(chirp(0.9, 500, 120, 5) * 0.6, lowpass(noise(1.2), 600) * expdecay(1.2, 3) * 1.2), 0.9, 0.3)
    flutter = bandpass(noise(1.0), 200, 1200) * (0.5 + 0.5 * np.sin(2 * np.pi * 22 * t_axis(1.0)))
    S["moth_ambient"] = reverb(mixall(flutter * env(1.0, 0.2, 0.5) * 1.2, chirp(0.5, 2600, 3000, 30) * 0.08), 0.5, 0.25)
    S["moth_eat"] = reverb(mixall(chirp(0.6, 600, 2400, 10) * 0.3, sweep_filter(noise(0.6), 4000, 400, 0.3) * env(0.6, 0.05, 0.5) * 1.2), 0.6, 0.3)
    S["moth_death"] = reverb(mixall(flutter * expdecay(1.0, 3), chirp(0.7, 2400, 600, 25) * 0.3), 0.7, 0.3)
    S["sentinel_ambient"] = reverb(mixall(pad(1.4, (220, 330, 440), 0.25), glass(1.4, 0.4)), 1.2, 0.4)
    S["sentinel_reflect"] = reverb(mixall(bell(1568, 0.8, 1.3) * 0.5, bell(2349, 0.6, 1.3) * 0.3, bandpass(noise(0.1), 3000, 9000) * expdecay(0.1, 30)), 0.7, 0.35)
    S["sentinel_death"] = reverb(mixall(glass(1.8, 1.8), lowpass(noise(1.8), 400) * env(1.8, 0.05, 1.2) * 0.8), 1.4, 0.4)
    S["lurker_ambient"] = reverb(mixall(lowpass(noise(2.0), 300) * env(2.0, 0.8, 0.8) * 0.8, chirp(2.0, 110, 98, 2) * 0.25), 1.8, 0.5)
    S["lurker_lunge"] = reverb(mixall(whoosh(0.4, 3000, 300) * 1.4, chirp(0.4, 900, 400, 50) * 0.3), 0.6, 0.35)
    S["lurker_death"] = reverb(mixall(sweep_filter(noise(1.6), 3000, 100, 0.3) * env(1.6, 0.05, 1.3) * 1.3, chirp(1.4, 300, 60, 3) * 0.4), 1.6, 0.5)
    S["moonkit_ambient"] = reverb(mixall(chirp(0.18, 1400, 1900, 30) * 0.6, at(chirp(0.14, 1700, 1300, 30) * 0.5, 0.2)), 0.4, 0.2)
    sniff = sumx(at(bandpass(noise(0.08), 2000, 7000) * env(0.08, 0.01, 0.06), i * 0.12) for i in range(4))
    S["moonkit_sniff"] = reverb(mixall(sniff * 1.2, at(bell(1568, 0.6, 1.0) * 0.25, 0.5)), 0.5, 0.25)
    S["leaper_ambient"] = reverb(mixall(bandpass(noise(0.3), 400, 2000) * env(0.3, 0.05, 0.2), chirp(0.25, 900, 700, 20) * 0.2), 0.4, 0.2)
    S["leaper_leap"] = reverb(mixall(whoosh(0.9, 200, 3500) * 1.6, chirp(0.8, 300, 1200, 4) * 0.3, thump(120, 60, 0.4, 8) * 0.8), 0.8, 0.3)

    S["matriarch_roar"] = reverb(mixall(chirp(3.0, 330, 220, 3) * 0.5, chirp(3.0, 495, 330, 3) * 0.3, pad(3.0, (82.4, 123.5), 0.4),
                                        wave(3.0, 1.2), whoosh(3.0, 200, 2500) * 0.5), 3.0, 0.55, 3000)
    S["matriarch_hurt"] = reverb(mixall(chirp(0.7, 440, 300, 6) * 0.6, glass(0.7, 0.8)), 0.9, 0.35)
    choir = mixall(*[at(chirp(3.2, f, f * 0.5, 2) * 0.25, 0.1 * i) for i, f in enumerate((523, 659, 784, 1047))])
    S["matriarch_death"] = reverb(mixall(choir, glass(3.0, 1.5), at(thump(60, 20, 2.4, 1.4) * 1.6, 2.2), at(whoosh(3.0, 100, 6000), 2.0)), 3.5, 0.6)
    S["matriarch_wave"] = reverb(mixall(wave(1.8, 2.2), thump(90, 40, 0.8, 4) * 0.8), 1.4, 0.4)
    beam = (osc(220, 2.0, "saw") + osc(221.7, 2.0, "saw") + osc(330, 2.0, "saw")) * 0.25
    S["matriarch_lance"] = reverb(mixall(lowpass(beam, 2500) * env(2.0, 0.4, 0.6), chirp(2.0, 900, 300, 4) * 0.3, whoosh(2.0, 3000, 300) * 0.5), 1.4, 0.4)
    S["matriarch_teleport"] = reverb(mixall(sweep_filter(noise(0.6), 6000, 400, 0.3) * env(0.6, 0.02, 0.5) * 1.4, chime((1319, 1047), 0.8, 0.05, 0.25)), 0.9, 0.45)
    S["matriarch_inversion"] = reverb(mixall(chirp(2.6, 60, 900, 2) * 0.4, pad(2.6, (130.8, 155.6, 196), 0.3), whoosh(2.6, 4000, 200) * 0.8,
                                             chime((1568, 1319, 1047, 784), 2.0, 0.25, 0.25)), 2.6, 0.55)
    charge = osc(glide(40, 300, 3.0, 2.0), 3.0, "saw") * np.linspace(0.1, 1, int(SR * 3.0))
    S["matriarch_moonfall"] = reverb(mixall(lowpass(charge, 900) * 0.6, lowpass(noise(3.0), 250) * env(3.0, 1.0, 1.5) * 1.6, chime((196, 233, 294), 2.4, 0.4, 0.3)),
                                     3.0, 0.55, 2000)
    S["anchor_shatter"] = reverb(glass(1.4, 2.0), 1.3, 0.4)
    return S


def main():
    for name, data in sounds().items():
        write(name, data)
        print("wrote", name, round(len(data) / SR, 2), "s")


if __name__ == "__main__":
    main()
