#!/usr/bin/env python3
"""Sunforged sound synthesizer - fire, sunlight and the Sun Warden. Reuses the DSP helpers of gen_sounds.py."""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_sounds import (SR, bandpass, bell, env, expdecay, glide, highpass, lowpass, mixall, noise, osc, reverb, sumx,  # noqa: E402
                        sweep_filter, t_axis, thump, write)

rng = np.random.default_rng(4321)


def crackle(dur, density=0.985, lo=1500, hi=7000, gain=1.0):
    clicks = (rng.random(int(SR * dur)) > density).astype(float) * rng.standard_normal(int(SR * dur))
    return bandpass(clicks, lo, hi) * gain


def whoosh(dur, f0, f1, width=0.6):
    return sweep_filter(noise(dur), f0, f1, width) * env(dur, dur * 0.25, dur * 0.6)


def roar(dur, f0, f1, grit=0.6):
    base = osc(glide(f0, f1, dur, 0.8), dur, "saw") + osc(glide(f0 * 1.01, f1 * 1.01, dur, 0.8), dur, "saw")
    base *= 1 + grit * rng.standard_normal(int(SR * dur)) * 0.5
    return (bandpass(base, 150, 1400) + bandpass(base, 1800, 3200) * 0.3) * env(dur, 0.15, dur * 0.5)


def chirp(dur, f0, f1, vib=30.0):
    t = t_axis(dur)
    f = glide(f0, f1, dur, 0.7) * (1 + 0.03 * np.sin(2 * np.pi * vib * t))
    return osc(f, dur) * env(dur, 0.01, dur * 0.6)


def at(x, delay):
    return np.concatenate([np.zeros(int(SR * delay)), x])


def sounds():
    S = {}
    fire_bed = lambda d: lowpass(noise(d), 900) * 0.6 + crackle(d, 0.99, 1500, 6000, 3.0)

    S["sun_brazier_ignite"] = reverb(mixall(whoosh(1.2, 200, 2500) * 1.5, fire_bed(1.6) * env(1.6, 0.2, 1.0), thump(110, 50, 0.6, 6)), 0.9, 0.3)
    S["sun_seal_open"] = reverb(mixall(whoosh(2.5, 300, 4000) * 1.2, *[at(bell(f, 2.2, 0.5) * 0.4, i * 0.18) for i, f in enumerate((392, 523, 659, 784, 1047))],
                                       crackle(2.8, 0.99, 2000, 8000, 2.0) * env(2.8, 0.3, 1.5)), 2.0, 0.45)
    S["sun_vent_erupt"] = reverb(mixall(whoosh(1.0, 120, 1800) * 2.0, lowpass(noise(1.0), 300) * expdecay(1.0, 3) * 1.5,
                                        crackle(1.0, 0.98, 1500, 7000, 2.5)), 0.7, 0.25)
    S["sun_altar_activate"] = reverb(mixall(*[at(bell(f, 3.0, 0.5) * 0.5, i * 0.25) for i, f in enumerate((220, 277, 330, 440, 554, 659))],
                                            lowpass(osc(55, 3.0, "saw"), 260) * env(3.0, 0.8, 1.5) * 0.7, whoosh(3.0, 100, 3000) * 0.6), 2.5, 0.5)
    S["sun_gateway_travel"] = reverb(mixall(sweep_filter(noise(1.6), 400, 4000, 0.4) * env(1.6, 0.1, 1.0), chirp(1.4, 300, 1200) * 0.4,
                                            *[at(bell(f, 1.2, 0.8) * 0.25, 0.1 * i) for i, f in enumerate((880, 1109, 1319))]), 1.4, 0.45)
    S["sun_gateway_form"] = reverb(mixall(thump(90, 30, 1.6, 2.0) * 1.6, whoosh(2.4, 100, 3500) * 1.3,
                                          *[at(bell(f, 2.6, 0.6) * 0.35, 0.4 + 0.15 * i) for i, f in enumerate((330, 440, 554, 659))]), 2.0, 0.45)

    S["sun_lance_dash"] = reverb(mixall(whoosh(0.55, 300, 5000) * 2.0, crackle(0.6, 0.97, 2000, 8000, 2.0) * env(0.6, 0.02, 0.4)), 0.5, 0.2)
    S["sun_greatsword_flare"] = reverb(mixall(whoosh(0.9, 150, 3000) * 1.6, thump(140, 50, 0.7, 5) * 1.2, fire_bed(1.0) * env(1.0, 0.05, 0.7)), 0.8, 0.3)
    S["sun_bow_shoot"] = reverb(mixall(whoosh(0.5, 600, 6000) * 1.4, chirp(0.45, 1800, 700) * 0.5, crackle(0.5, 0.98, 3000, 9000, 1.5)), 0.6, 0.25)
    S["sun_scepter_summon"] = reverb(mixall(chirp(1.2, 300, 1500) * 0.5, *[at(bell(f, 1.6, 0.7) * 0.35, i * 0.08) for i, f in enumerate((523, 659, 784, 1047))],
                                            whoosh(1.2, 200, 3000)), 1.4, 0.4)
    spin = whoosh(0.9, 400, 2500) * (0.6 + 0.4 * np.sin(2 * np.pi * 14 * t_axis(0.9)))
    S["sun_chakram_throw"] = reverb(mixall(spin * 1.5, crackle(0.9, 0.98, 2000, 8000, 1.5)), 0.6, 0.25)
    S["sun_flask_burst"] = reverb(mixall(bandpass(noise(0.3), 2000, 9000) * expdecay(0.3, 12) * 1.5, whoosh(1.4, 100, 5000) * 1.5,
                                         thump(160, 40, 0.9, 4) * 1.4, highpass(noise(1.6), 5000) * env(1.6, 0.02, 1.4) * 0.3), 1.2, 0.35)
    S["sun_rebirth"] = reverb(mixall(thump(80, 25, 2.0, 1.8) * 1.5, whoosh(2.4, 100, 5000) * 1.4,
                                     *[at(bell(f, 2.6, 0.5) * 0.4, 0.3 + 0.12 * i) for i, f in enumerate((262, 330, 392, 523, 659, 784))],
                                     at(chirp(1.4, 900, 1700, 8) * 0.4, 0.5)), 2.2, 0.45)
    S["sun_egg_hatch"] = reverb(mixall(bandpass(noise(0.25), 1500, 6000) * expdecay(0.25, 18) * 1.2, at(whoosh(1.2, 200, 3000), 0.15),
                                       at(chirp(0.9, 900, 1600, 10) * 0.5, 0.4), at(chirp(0.6, 1200, 2000, 12) * 0.4, 0.9)), 1.2, 0.35)

    cackle = sumx(at(chirp(0.09, 1500 + i * 120, 900, 50) * 0.7, i * 0.11) for i in range(6))
    S["imp_ambient"] = reverb(mixall(cackle, crackle(0.8, 0.99, 2000, 7000, 1.0)), 0.5, 0.2)
    S["imp_hurt"] = reverb(chirp(0.3, 2200, 900, 40) * 1.0, 0.4, 0.2)
    S["imp_death"] = reverb(mixall(chirp(0.6, 1800, 300, 25), whoosh(0.8, 2000, 300) * 0.8, crackle(0.8, 0.97, 2000, 8000, 2.0) * expdecay(0.8, 4)), 0.6, 0.3)
    hiss = bandpass(noise(1.0), 2500, 8000) * env(1.0, 0.1, 0.6)
    S["crawler_ambient"] = reverb(mixall(hiss * 0.8, lowpass(noise(1.0), 200) * env(1.0, 0.2, 0.5) * 1.2, crackle(1.0, 0.99, 800, 3000, 1.5)), 0.6, 0.25)
    S["crawler_death"] = reverb(mixall(roar(0.9, 160, 70, 0.9) * 0.8, lowpass(noise(1.2), 600) * expdecay(1.2, 3) * 1.4, crackle(1.2, 0.98, 800, 4000, 2.0)), 0.9, 0.3)
    pant = sumx(at(bandpass(noise(0.18), 600, 3000) * env(0.18, 0.04, 0.1), i * 0.28) for i in range(4))
    S["hound_ambient"] = reverb(mixall(pant, crackle(1.2, 0.99, 1500, 6000, 1.0)), 0.4, 0.2)
    S["hound_growl"] = reverb(mixall(roar(1.0, 95, 80, 1.2) * 0.9, crackle(1.0, 0.985, 1500, 6000, 1.5)), 0.5, 0.2)
    S["hound_death"] = reverb(mixall(chirp(0.7, 700, 250, 6) * 0.6, roar(0.6, 140, 70) * 0.6, lowpass(noise(1.0), 900) * expdecay(1.0, 4)), 0.7, 0.3)
    S["knight_ambient"] = reverb(mixall(bandpass(noise(1.2), 200, 1200) * env(1.2, 0.3, 0.7) * 1.2, lowpass(osc(70, 1.2, "saw"), 300) * env(1.2, 0.3, 0.7) * 0.5), 1.0, 0.35)
    clang = sumx(bell(f, 0.8, 1.5) * 0.4 for f in (420, 1130, 1870, 2640))
    S["knight_block"] = reverb(mixall(clang, bandpass(noise(0.15), 2000, 8000) * expdecay(0.15, 25) * 1.5), 0.6, 0.25)
    rubble = mixall(*[at(bandpass(noise(0.12), 300, 2500) * expdecay(0.12, 25), rng.random() * 1.2) for _ in range(14)])
    S["knight_death"] = reverb(mixall(rubble * 1.5, clang * 0.6, lowpass(noise(1.6), 300) * env(1.6, 0.05, 1.2)), 1.2, 0.35)
    S["phoenix_ambient"] = reverb(mixall(chirp(0.5, 1300, 1700, 7), at(chirp(0.6, 1500, 1100, 7), 0.45), at(chirp(0.8, 1100, 1900, 6), 0.95),
                                         crackle(1.8, 0.99, 2000, 7000, 0.8)), 1.4, 0.4)
    S["phoenix_cry"] = reverb(mixall(chirp(1.1, 1900, 900, 12) * 1.0, chirp(1.1, 2850, 1350, 12) * 0.3, whoosh(1.1, 400, 2400) * 0.5), 1.4, 0.4)

    S["warden_roar"] = reverb(mixall(roar(3.2, 65, 40) * 1.3, lowpass(noise(3.2), 220) * env(3.2, 0.3, 1.6) * 1.4,
                                     whoosh(3.2, 100, 1600) * 0.6), 2.2, 0.45, 2500)
    S["warden_hurt"] = reverb(mixall(roar(0.8, 100, 65, 0.8), clang * 0.5), 1.0, 0.3)
    big = sumx(np.concatenate([np.zeros(int(SR * 2.2)), bell(f, 3.6, 0.4) * 0.35]) for f in (196, 247, 294, 392, 494, 587))
    S["warden_death"] = reverb(mixall(roar(3.0, 80, 28, 0.9) * 1.3, at(thump(60, 18, 2.6, 1.4) * 2.0, 2.1), big,
                                      at(whoosh(3.0, 100, 6000) * 1.2, 2.0)), 3.2, 0.5)
    S["warden_flare"] = reverb(mixall(whoosh(1.4, 120, 3500) * 1.6, *[at(thump(110, 45, 0.6, 7) * 0.8, 0.2 * i) for i in range(4)],
                                      fire_bed(1.6) * env(1.6, 0.1, 1.0)), 1.4, 0.4)
    beam = (osc(130, 2.0, "saw") + osc(131.3, 2.0, "saw") + osc(196, 2.0, "saw")) * 0.3
    S["warden_beam"] = reverb(mixall(lowpass(beam, 2800) * env(2.0, 0.4, 0.6), chirp(2.0, 300, 900, 4) * 0.3,
                                     highpass(noise(2.0), 4000) * env(2.0, 0.4, 0.6) * 0.15), 1.2, 0.3)
    S["warden_pillar"] = reverb(mixall(lowpass(noise(1.8), 300) * env(1.8, 0.5, 0.6) * 1.6, at(whoosh(1.0, 100, 3000) * 1.6, 0.8),
                                       at(crackle(1.0, 0.97, 1500, 7000, 2.5), 0.8)), 1.4, 0.35)
    S["warden_slam"] = reverb(mixall(thump(65, 20, 2.4, 1.8) * 2.2, lowpass(noise(2.4), 1300) * expdecay(2.4, 2.8) * 1.6,
                                     bandpass(noise(2.4), 800, 4000) * expdecay(2.4, 1.5) * (rng.random(int(SR * 2.4)) > 0.96) * 2.5,
                                     whoosh(2.0, 100, 2000) * 0.8), 2.0, 0.4, 1500)
    charge = osc(glide(60, 1400, 2.6, 2.4), 2.6, "saw") * np.linspace(0.05, 1, int(SR * 2.6))
    S["warden_supernova"] = reverb(mixall(lowpass(charge, 3000) * 0.5, at(thump(80, 20, 2.6, 1.6) * 2.2, 2.4), at(whoosh(2.6, 80, 7000) * 1.8, 2.4),
                                          at(highpass(noise(2.6), 5000) * env(2.6, 0.02, 2.4) * 0.3, 2.4)), 2.6, 0.5)
    shards = mixall(bandpass(noise(1.2), 1500, 8000) * expdecay(1.2, 7) * (rng.random(int(SR * 1.2)) > 0.6) * 1.4,
                    *[at(bell(rng.uniform(900, 2400), 0.9, 0.9) * 0.18, rng.random() * 0.3) for _ in range(8)], whoosh(1.2, 2000, 300) * 0.8)
    S["pylon_shatter"] = reverb(shards, 1.2, 0.35)
    return S


def main():
    for name, data in sounds().items():
        write(name, data)
        print("wrote", name, round(len(data) / SR, 2), "s")


if __name__ == "__main__":
    main()
