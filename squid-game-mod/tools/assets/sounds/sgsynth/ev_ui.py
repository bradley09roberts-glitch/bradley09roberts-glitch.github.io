"""UI blips, countdown beeps / ticks and the PA announcement chimes."""
from __future__ import annotations

import math

import numpy as np

from .core import (SR, TAU, add_at, adsr, curve, exp_decay, finish, ns, saw, pulse, sine, soft_clip,
                   stack, time_axis)
from .dsp import band, bandpass, highpass, lowpass, reverb
from .instruments import bell_fm, chime_tone, modal, noise_burst, wood_tock, chirp
from .registry import sound


# --------------------------------------------------------------------------- UI
@sound("ui.select", "Click", variants=2)
def ui_select(v, rng):
    f = (1760.0, 1480.0)[v]
    n = ns(0.15)
    body = 0.7 * sine(f, n) * exp_decay(n, 0.016, 0.0008)
    body += 0.15 * sine(f * 2.0, n) * exp_decay(n, 0.008, 0.0008)
    tick = 0.5 * noise_burst(rng, 0.02, 3000, 9000, tau=0.0015)
    knock = 0.5 * modal([640.0 + 60 * v, 1290.0], [0.6, 0.2], [0.018, 0.009], 0.15)
    y = stack(body, tick, knock, n=n)
    return finish(y, -3.0, -14.0, 0.0008, 0.03)


@sound("ui.confirm", "Confirmation chime")
def ui_confirm(v, rng):
    n = ns(0.4)
    out = np.zeros(n)
    add_at(out, bell_fm(880.0, 0.3, ratio=2.0, index=1.3, tau=0.16, index_tau=0.07, attack=0.003), 0.0)
    add_at(out, bell_fm(1318.5, 0.28, ratio=2.0, index=1.5, tau=0.14, index_tau=0.07, attack=0.003), 0.115, 1.0)
    out = reverb(out, rt60=0.35, wet=0.12, predelay=0.006, damp=0.5, seed=21)
    return finish(out, -3.0, -13.0, 0.002, 0.07)


@sound("ui.deny", "Denied buzz")
def ui_deny(v, rng):
    n = ns(0.3)
    out = np.zeros(n)
    for t0, f in ((0.0, 168.0), (0.15, 132.0)):
        m = ns(0.115)
        y = 0.7 * pulse(f, m, 0.4) + 0.5 * saw(f * 1.01, m)
        y = lowpass(y, 1700, order=4) * (1.0 + 0.4 * sine(70.0, m))
        y *= curve([(0, 0), (0.005, 1), (0.085, 0.9), (0.115, 0)], m, "cos")
        add_at(out, y, t0)
    return finish(out, -3.0, -13.0, 0.002, 0.03)


@sound("ui.number_call", "Number called")
def ui_number_call(v, rng):
    dur = 0.6
    f = 1568.0
    y = modal([f, f * 2.0, f * 3.01, f * 4.2], [1.0, 0.33, 0.14, 0.06], [0.42, 0.22, 0.12, 0.06], dur,
              detune_beat=1.7)
    add_at(y, 0.12 * noise_burst(rng, 0.02, 4000, 12000, tau=0.002), 0.0)
    y += 0.5 * sine(f * 0.5, ns(dur)) * exp_decay(ns(dur), 0.22, 0.002)
    y = reverb(y, rt60=0.6, wet=0.18, predelay=0.008, damp=0.4, seed=22)
    return finish(y, -3.0, -14.0, 0.001, 0.09)


# -------------------------------------------------------------------- countdown
@sound("countdown.tick", "Clock ticks", variants=3)
def countdown_tick(v, rng):
    f = (1150.0, 1260.0, 1370.0)[v]
    y = wood_tock(rng, f=f, dur=0.15, snap=0.45 + 0.05 * v)
    y = reverb(y, rt60=0.18, wet=0.06, predelay=0.002, damp=0.7, seed=23 + v)
    return finish(y, -3.0, -14.0, 0.0005, 0.03)


@sound("countdown.beep", "Countdown beep")
def countdown_beep(v, rng):
    n = ns(0.25)
    y = sine(880.0, n) + 0.025 * sine(1760.0, n)
    y *= curve([(0, 0), (0.004, 1), (0.215, 1), (0.25, 0)], n, "cos")
    return finish(y, -3.0, -12.0, 0.0, 0.0)


@sound("countdown.final", "Final countdown beep")
def countdown_final(v, rng):
    n = ns(0.6)
    y = sine(1320.0, n) + 0.03 * sine(2640.0, n) + 0.015 * sine(3960.0, n)
    y *= curve([(0, 0), (0.005, 1), (0.50, 0.96), (0.6, 0)], n, "cos")
    return finish(y, -3.0, -12.0, 0.0, 0.0)


# ------------------------------------------------------------------ PA chimes
def _pa(x, hf=6800.0, drive=1.0):
    """Public-address colouring: band-limited (speaker horn), optional mild saturation."""
    x = band(x, 260.0, hf, order=2)
    if drive > 1.0:
        x = soft_clip(x * drive, 1.0)
    return x


@sound("announce.chime", "Announcement chime")
def announce_chime(v, rng):
    n = ns(1.6)
    out = np.zeros(n)
    notes = [(784.0, 0.00, 0.45), (659.26, 0.34, 0.50), (523.25, 0.68, 0.80)]  # G5 E5 C5 (descending C major)
    for f, t0, tau in notes:
        add_at(out, chime_tone(f, 1.6 - t0, tau=tau, bright=0.9, attack=0.006, rng=rng), t0)
    out = _pa(out)
    out = reverb(out, rt60=1.5, wet=0.28, predelay=0.012, damp=0.55, seed=24)
    return finish(out, -3.0, -14.0, 0.002, 0.18)


@sound("announce.chime_alert", "Urgent announcement chime")
def announce_chime_alert(v, rng):
    n = ns(1.2)
    out = np.zeros(n)
    for f, t0, tau in ((1318.5, 0.0, 0.30), (987.77, 0.30, 0.55)):   # E6 -> B5, falling fourth
        add_at(out, chime_tone(f, 1.2 - t0, tau=tau, bright=1.6, attack=0.002, rng=rng), t0)
    out = _pa(out, 7800.0)
    out = reverb(out, rt60=1.0, wet=0.22, predelay=0.010, damp=0.5, seed=25)
    return finish(out, -3.0, -13.0, 0.001, 0.15)
