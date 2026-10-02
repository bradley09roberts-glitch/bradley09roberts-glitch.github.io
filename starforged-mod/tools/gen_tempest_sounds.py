#!/usr/bin/env python3
"""Tempestforged sound synthesizer - thunder, wind, crackling arcs and Veyr. Reuses gen_sounds.py's DSP helpers."""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_sounds import (SR, bandpass, bell, env, expdecay, glide, highpass, lowpass, mixall, noise, osc, reverb, sumx,  # noqa: E402,F401
                        sweep_filter, t_axis, thump, write)

rng = np.random.default_rng(4242)


def at(x, delay):
    return np.concatenate([np.zeros(int(SR * delay)), x])


def whoosh(dur, f0, f1, width=0.6):
    return sweep_filter(noise(dur), f0, f1, width) * env(dur, dur * 0.25, dur * 0.6)


def crackle(dur, density=0.92, gain=1.0, lo=2000, hi=9000):
    """Electric crackle: sparse clicks of bright noise."""
    clicks = (rng.random(int(SR * dur)) > density).astype(float)
    return bandpass(noise(dur), lo, hi) * clicks * 2.4 * gain


def zap(dur=0.25, gain=1.0):
    """A sharp electric snap with a falling buzz."""
    t = t_axis(dur)
    buzz = osc(glide(1800, 300, dur, 0.6), dur, "saw") * expdecay(dur, 14) * 0.35
    return mixall(crackle(dur, 0.8, 1.2) * expdecay(dur, 10), lowpass(buzz, 6000), highpass(noise(dur), 3000) * expdecay(dur, 40) * 0.8) * gain + 0 * t


def thunder(dur=3.0, gain=1.0, crack=True):
    """A thunderclap: a crack, then a long rolling rumble."""
    rumble = lowpass(noise(dur), 220) * env(dur, 0.08, dur * 0.8) * 2.4
    rolls = sumx(at(lowpass(noise(0.8), 160) * env(0.8, 0.1, 0.6) * 1.6, rng.uniform(0.2, dur - 0.9)) for _ in range(4))
    parts = [rumble, rolls]
    if crack:
        parts.append(mixall(highpass(noise(0.25), 1500) * expdecay(0.25, 18) * 2.0, thump(90, 30, 0.6, 6) * 1.2))
    return mixall(*parts) * gain


def wind(dur, f0=300, f1=900, gain=1.0, gusts=0.5):
    t = t_axis(dur)
    swell = 0.6 + 0.4 * np.sin(2 * np.pi * gusts * t / dur * 3 + 1.0)
    return sweep_filter(noise(dur), f0, f1, 0.5) * swell * env(dur, dur * 0.2, dur * 0.4) * gain


def hum(dur, f, gain=0.3):
    return lowpass(osc(f, dur, "saw") + osc(f * 1.007, dur, "saw"), 1200) * gain * env(dur, 0.05, 0.2)


def chirp(dur, f0, f1, vib=20.0):
    t = t_axis(dur)
    f = glide(f0, f1, dur, 0.7) * (1 + 0.03 * np.sin(2 * np.pi * vib * t))
    return osc(f, dur) * env(dur, 0.01, dur * 0.6)


def chime(freqs, dur=1.6, step=0.1, gain=0.35, bright=0.9):
    return mixall(*[at(bell(f, dur, bright) * gain, i * step) for i, f in enumerate(freqs)])


def sounds():
    S = {}
    # World & blocks.
    S["tempest_gate_form"] = reverb(mixall(thunder(2.6, 1.0), whoosh(2.6, 200, 5000) * 0.8, at(chime((392, 587, 784, 1175), 2.0, 0.12), 0.3)), 2.4, 0.5)
    S["tempest_gate_travel"] = reverb(mixall(whoosh(1.6, 500, 6000) * 1.2, crackle(1.6, 0.97, 0.6) * env(1.6, 0.1, 1.0)), 1.6, 0.5)
    S["stormreach_wind"] = reverb(mixall(wind(6.0, 150, 700, 1.2), wind(6.0, 600, 2200, 0.35, 1.3)), 2.0, 0.4)
    S["stormreach_gust"] = reverb(mixall(whoosh(2.2, 200, 3000) * 2.0, wind(2.2, 500, 1500, 0.8)), 1.4, 0.4)
    S["tempest_supercell"] = reverb(mixall(thunder(4.5, 1.4), at(thunder(3.0, 1.0, False), 1.2), wind(4.5, 200, 1200, 1.0),
                                   hum(4.5, 55, 0.25)), 3.0, 0.55, 2500)
    S["tempest_aetherium_charge"] = reverb(mixall(chirp(0.8, 300, 2400, 12) * 0.3, crackle(0.8, 0.9, 0.8) * env(0.8, 0.3, 0.4), bell(1568, 0.8, 1.2) * 0.3), 0.8, 0.35)
    S["tempest_wind_vent"] = reverb(whoosh(1.0, 300, 2500) * 1.4, 0.6, 0.25)
    S["tempest_shock_plate"] = reverb(zap(0.3, 1.2), 0.4, 0.2)
    S["tempest_wind_chime"] = reverb(chime([float(f) for f in rng.choice((784, 880, 988, 1175, 1319, 1568), 4, replace=False)], 2.0, 0.15, 0.25, 1.1), 1.6, 0.5)
    S["tempest_conductor_turn"] = reverb(mixall(bandpass(noise(0.25), 800, 3000) * expdecay(0.25, 12) * (rng.random(int(SR * 0.25)) > 0.75) * 2.0,
                                        bell(1175, 0.4, 1.2) * 0.2), 0.5, 0.25)
    S["tempest_network_pulse"] = reverb(mixall(zap(0.2, 0.7), chirp(0.2, 900, 1800, 30) * 0.15), 0.4, 0.25)
    S["tempest_core_online"] = reverb(mixall(hum(1.6, 110, 0.3) * np.linspace(0.2, 1, int(SR * 1.6)), chime((523, 784, 1047), 1.6, 0.12, 0.3),
                                     crackle(1.6, 0.96, 0.5) * env(1.6, 0.1, 1.0)), 1.6, 0.45)
    S["tempest_overload"] = reverb(mixall(thump(70, 30, 0.9, 4) * 1.6, zap(0.6, 1.6), lowpass(noise(1.2), 900) * expdecay(1.2, 3) * 1.4,
                                  chirp(0.8, 1400, 200, 6) * 0.3), 1.2, 0.4)
    S["tempest_seal_open"] = reverb(mixall(crackle(2.0, 0.85, 1.0) * expdecay(2.0, 1.5), chime((523, 659, 784, 1047, 1319), 2.2, 0.16, 0.3), whoosh(2.2, 300, 5000) * 0.8),
                            2.2, 0.5)
    S["tempest_altar_activate"] = reverb(mixall(chime((196, 294, 392, 587, 784), 3.0, 0.22, 0.4, 0.6), thunder(3.2, 0.8), hum(3.2, 49, 0.3)), 3.0, 0.55)

    # Weapons & items.
    S["tempest_stormstep_dash"] = reverb(mixall(whoosh(0.35, 1200, 7000) * 1.8, zap(0.2, 0.5)), 0.4, 0.25)
    S["tempest_halberd_launch"] = reverb(mixall(whoosh(0.8, 200, 4000) * 1.8, chirp(0.6, 300, 1100, 6) * 0.3), 0.6, 0.3)
    S["tempest_halberd_slam"] = reverb(mixall(thunder(2.0, 1.4), thump(60, 25, 1.2, 3) * 1.6), 1.6, 0.45)
    S["tempest_javelin_throw"] = reverb(mixall(whoosh(0.6, 600, 6000) * 1.6, crackle(0.6, 0.93, 0.6) * expdecay(0.6, 4)), 0.6, 0.3)
    S["tempest_javelin_recall"] = reverb(mixall(chirp(0.7, 400, 2000, 14) * 0.35, whoosh(0.7, 3000, 500) * 1.2, crackle(0.7, 0.9, 0.5)), 0.6, 0.3)
    S["tempest_blades_cyclone"] = reverb(mixall(wind(1.2, 400, 3000, 1.8) * (0.6 + 0.4 * np.sin(2 * np.pi * 14 * t_axis(1.2))), whoosh(1.2, 300, 4000) * 0.6), 0.8, 0.3)
    S["tempest_hook_fire"] = reverb(mixall(whoosh(0.4, 500, 4000) * 1.2, zap(0.3, 0.6)), 0.4, 0.2)
    S["tempest_live_wire"] = reverb(mixall(hum(0.8, 120, 0.4), crackle(0.8, 0.85, 1.0) * env(0.8, 0.02, 0.6)), 0.6, 0.25)
    charge = osc(glide(80, 900, 1.6, 1.5), 1.6, "saw") * np.linspace(0.1, 1, int(SR * 1.6))
    S["tempest_cannon_charge"] = reverb(mixall(lowpass(charge, 3000) * 0.4, crackle(1.6, 0.94, 0.7) * np.linspace(0.2, 1, int(SR * 1.6))), 0.8, 0.3)
    S["tempest_cannon_fire"] = reverb(mixall(zap(0.6, 1.8), thump(110, 40, 0.6, 6) * 1.2, highpass(noise(0.8), 2000) * expdecay(0.8, 6) * 0.8), 0.9, 0.35)
    S["tempest_judgement"] = reverb(mixall(thunder(3.5, 1.6), at(thunder(2.5, 1.0), 0.6), chime((262, 392, 523, 784), 2.6, 0.2, 0.3, 0.5)), 3.0, 0.55, 3000)

    # Creatures.
    S["tempest_wisp_ambient"] = reverb(mixall(hum(1.0, 220, 0.15), crackle(1.0, 0.97, 0.5) * env(1.0, 0.2, 0.5)), 0.6, 0.3)
    S["tempest_wisp_arc"] = reverb(zap(0.35, 1.2), 0.5, 0.3)
    S["tempest_wisp_death"] = reverb(mixall(chirp(0.8, 1600, 200, 20) * 0.3, crackle(0.8, 0.8, 1.2) * expdecay(0.8, 4)), 0.7, 0.3)
    S["tempest_shardwing_ambient"] = reverb(mixall(chirp(0.4, 1800, 2600, 25) * 0.3, at(chirp(0.3, 2400, 1600, 25) * 0.25, 0.3), bell(2349, 0.5, 1.2) * 0.1), 0.5, 0.3)
    S["tempest_shardwing_dive"] = reverb(mixall(whoosh(1.0, 4000, 400) * 1.6, chirp(1.0, 2600, 900, 30) * 0.3), 0.7, 0.3)
    S["tempest_shardwing_death"] = reverb(mixall(chirp(0.8, 2400, 500, 20) * 0.35, bandpass(noise(0.6), 2500, 9000) * expdecay(0.6, 8) * 1.2), 0.8, 0.3)
    S["tempest_stormbound_ambient"] = reverb(mixall(hum(1.2, 82, 0.3), bandpass(noise(1.2), 300, 1200) * env(1.2, 0.3, 0.6) * 0.4), 0.8, 0.3)
    S["tempest_stormbound_step"] = reverb(mixall(zap(0.3, 1.2), whoosh(0.3, 6000, 800) * 1.0), 0.5, 0.3)
    S["tempest_stormbound_death"] = reverb(mixall(bandpass(noise(1.0), 600, 3000) * expdecay(1.0, 4) * (rng.random(int(SR * 1.0)) > 0.7) * 1.8,
                                          chirp(1.0, 200, 60, 3) * 0.4, crackle(1.0, 0.9, 0.6)), 1.0, 0.35)
    growl = lowpass(osc(glide(70, 55, 1.6, 1), 1.6, "saw") * (0.7 + 0.3 * np.sin(2 * np.pi * 9 * t_axis(1.6))), 700) * env(1.6, 0.2, 0.8)
    S["tempest_thunderjaw_ambient"] = reverb(growl * 0.7, 0.8, 0.3)
    S["tempest_thunderjaw_stamp"] = reverb(mixall(thump(55, 25, 1.4, 3) * 2.0, lowpass(noise(1.2), 400) * expdecay(1.2, 3) * 1.4), 1.2, 0.4)
    S["tempest_thunderjaw_death"] = reverb(mixall(lowpass(osc(glide(80, 30, 2.0, 1), 2.0, "saw"), 600) * env(2.0, 0.1, 1.5) * 0.7, crackle(2.0, 0.95, 0.5)), 1.4, 0.4)
    S["tempest_sprite_ambient"] = reverb(mixall(chirp(0.25, 1800, 2400, 30) * 0.4, at(chirp(0.2, 2200, 2800, 30) * 0.3, 0.18), whoosh(0.5, 2000, 5000) * 0.3), 0.5, 0.3)
    S["tempest_sprite_guard"] = reverb(mixall(whoosh(0.8, 300, 4000) * 1.4, chime((1319, 1760), 0.8, 0.08, 0.2)), 0.6, 0.35)
    S["tempest_roc_ambient"] = reverb(mixall(chirp(0.7, 700, 500, 8) * 0.4, chirp(0.7, 1050, 750, 8) * 0.2), 0.7, 0.3)
    S["tempest_roc_screech"] = reverb(mixall(chirp(1.2, 1600, 900, 9) * 0.5, chirp(1.2, 2400, 1350, 9) * 0.25, highpass(noise(1.2), 2500) * env(1.2, 0.05, 0.8) * 0.3),
                              1.0, 0.4)
    S["tempest_alpha_roar"] = reverb(mixall(lowpass(osc(glide(90, 45, 2.6, 1), 2.6, "saw") * (0.7 + 0.3 * np.sin(2 * np.pi * 7 * t_axis(2.6))), 900) * env(2.6, 0.1, 1.6),
                                    thunder(2.6, 0.8)), 2.0, 0.5)

    # Veyr.
    S["tempest_veyr_roar"] = reverb(mixall(thunder(3.2, 1.2), chirp(3.0, 165, 110, 3) * 0.5, chirp(3.0, 247, 165, 3) * 0.3, hum(3.2, 41, 0.3)), 3.0, 0.55, 3000)
    S["tempest_veyr_hurt"] = reverb(mixall(zap(0.5, 1.0), bandpass(noise(0.5), 400, 2000) * expdecay(0.5, 8) * (rng.random(int(SR * 0.5)) > 0.7) * 2.0), 0.7, 0.35)
    choir = mixall(*[at(chirp(3.4, f, f * 0.5, 2) * 0.22, 0.1 * i) for i, f in enumerate((392, 494, 587, 784))])
    S["tempest_veyr_death"] = reverb(mixall(choir, thunder(4.0, 1.6), at(thunder(3.0, 1.4), 1.8), crackle(4.0, 0.9, 0.8) * env(4.0, 0.2, 2.0)), 3.5, 0.6)
    S["tempest_veyr_step"] = reverb(mixall(thunder(1.6, 1.0), zap(0.4, 1.4)), 1.4, 0.4)
    S["tempest_veyr_tornado"] = reverb(mixall(wind(3.0, 200, 2500, 2.0), whoosh(3.0, 200, 3000) * 1.0, hum(3.0, 55, 0.2)), 2.0, 0.45)
    S["tempest_veyr_shatter"] = reverb(mixall(thunder(3.0, 1.6), bandpass(noise(2.0), 300, 2500) * expdecay(2.0, 2) * (rng.random(int(SR * 2.0)) > 0.6) * 2.4,
                                      thump(45, 20, 2.0, 2) * 1.8), 2.4, 0.5)
    S["tempest_veyr_last_thunder"] = reverb(mixall(hum(4.0, 41, 0.4) * np.linspace(0.2, 1, int(SR * 4.0)), at(thunder(3.0, 1.8), 1.0),
                                           chime((147, 175, 220, 294), 3.4, 0.4, 0.35, 0.4)), 3.5, 0.6, 2500)
    S["tempest_conductor_redirect"] = reverb(mixall(zap(0.7, 1.6), thunder(1.6, 0.8), bell(1568, 1.0, 1.2) * 0.3), 1.2, 0.4)
    return S


def main():
    for name, data in sounds().items():
        write(name, data)
        print("wrote", name, round(len(data) / SR, 2), "s")


if __name__ == "__main__":
    main()
