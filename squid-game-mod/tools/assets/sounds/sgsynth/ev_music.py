"""Three original music loops (no known melodies).

* music.lobby   (60 s)  slow 3/4 music-box tune over a soft pad - A minor, a flat tine, a Neapolitan
                        chord and reversed bell swells for the "slightly unsettling" feeling
* music.tension (45 s)  96 BPM pulsing low synth strings, ticking clock, a creeping bass line and a
                        high tremolo cluster that blooms and fades
* music.final   (60 s)  64 BPM slow dramatic build: drone + heartbeat, rising ostinato and bass line,
                        strings, brass, timpani roll, an endlessly rising Shepard glide, climax, release

Every loop is rendered on a circular time base: notes and reverb tails wrap around the end, so the
loop point is seamless (head and tail levels match because the piece returns to its opening texture).
"""
from __future__ import annotations

import math

import numpy as np

from .core import (SR, TAU, add_at, cents, curve, exp_decay, mtof, normalize, ns, remove_dc, sine, saw,
                   soft_clip, stack, supersaw, time_axis, white)
from .dsp import band, bandpass, compress, highpass, lowpass, reverb, tv_biquad
from .instruments import (brass, chime_tone, metal_hit, modal, music_box_note, noise_burst, noise_swell,
                          pad_note, string_swell, thump, timpani, wood_tock)
from .registry import sound


def _master(x, peak_dbfs=-4.0, comp=(-24.0, 2.2), drive=1.0, hp=28.0):
    """Gentle bus compression + saturation, DC/sub clean-up, peak normalise.  No fades (loops!)."""
    x = highpass(x, hp, order=2, circular=True)
    if comp:
        x = compress(x, thresh_db=comp[0], ratio=comp[1], attack=0.03, release=0.35, circular=True)
    if drive > 0:
        x = soft_clip(x, drive)
    return normalize(remove_dc(x), peak_dbfs, None)


def _arp_tones(pcs, lo, hi):
    """All MIDI notes whose pitch class is in ``pcs`` within [lo, hi], ascending."""
    return [m for m in range(lo, hi + 1) if (m % 12) in pcs]


# ======================================================================================= lobby
_CH = {  # pitch classes
    "Am": (9, 0, 4), "F": (5, 9, 0), "Dm": (2, 5, 9), "E": (4, 8, 11), "Bb": (10, 2, 5),
    "C": (0, 4, 7), "G": (7, 11, 2),
}
_PAD = {  # voicing (MIDI) for the soft pad
    "Am": (57, 60, 64, 69), "F": (53, 57, 60, 65), "Dm": (50, 57, 62, 65), "E": (52, 56, 59, 64),
    "Bb": (58, 62, 65, 70), "C": (48, 55, 60, 64), "G": (55, 59, 62, 67),
}
_BASS = {"Am": 33, "F": 29, "Dm": 26, "E": 28, "Bb": 34, "C": 36, "G": 31}

_L_A = ["Am", "Am", "F", "F", "Dm", "Dm", "E", "E"]
_L_A2 = ["Am", "Am", "F", "F", "Dm", "Dm", "Bb", "E"]
_L_B = ["C", "C", "G", "G", "F", "F", "E", "E"]
_L_BARS = _L_A + _L_A2 + _L_B

# melody: per bar a list of (step 0-5, MIDI, length in steps)
_L_MEL = [
    [(0, 88, 3), (3, 86, 1), (4, 84, 2)],
    [(0, 81, 3), (3, 83, 1), (4, 84, 2)],
    [(0, 89, 3), (3, 88, 1), (4, 84, 2)],
    [(0, 81, 3), (3, 84, 3)],
    [(0, 86, 3), (3, 89, 1), (4, 86, 2)],
    [(0, 81, 3), (3, 84, 1), (4, 86, 2)],
    [(0, 88, 2), (2, 83, 2), (4, 80, 2)],
    [(0, 83, 3), (3, 88, 3)],
    # second time: same tune, the last bars darken (Neapolitan Bb, falling into E)
    [(0, 88, 3), (3, 86, 1), (4, 84, 2)],
    [(0, 81, 3), (3, 83, 1), (4, 84, 2)],
    [(0, 89, 3), (3, 88, 1), (4, 84, 2)],
    [(0, 81, 3), (3, 84, 3)],
    [(0, 86, 3), (3, 89, 1), (4, 86, 2)],
    [(0, 81, 3), (3, 84, 1), (4, 86, 2)],
    [(0, 86, 3), (3, 89, 1), (4, 86, 2)],
    [(0, 83, 2), (2, 80, 2), (4, 76, 2)],
    # B: sparse and high
    [(0, 91, 4), (4, 88, 2)],
    [(0, 84, 3), (3, 88, 3)],
    [(0, 91, 3), (3, 86, 3)],
    [(0, 83, 3), (3, 91, 1), (4, 86, 2)],
    [(0, 89, 3), (3, 84, 3)],
    [(0, 81, 2), (2, 84, 2), (4, 89, 2)],
    [(0, 88, 3), (3, 83, 3)],
    [(0, 80, 3), (3, 83, 1), (4, 88, 2)],
]


@sound("music.lobby", "Lobby music", stream=True)
def music_lobby(v, rng):
    L = 60.0
    n = ns(L)
    bars = len(_L_BARS)               # 24
    BAR = L / bars                    # 2.5 s  (3/4 at 72 BPM)
    STEP = BAR / 6.0                  # eighth note
    steps_total = bars * 6

    def T(step):
        """Music-box governor: the tempo drifts +-1.4 % and returns exactly at the loop point."""
        return step * STEP + 0.30 * STEP * math.sin(TAU * step / steps_total)

    box = np.zeros(n)
    ghost = np.zeros(n)
    pat = [0, 2, 4, 5, 4, 2]                                   # rocking arpeggio over the chord tones
    for b, ch in enumerate(_L_BARS):
        tones = _arp_tones(_CH[ch], 62, 86)
        sparse = b >= 16                                       # the B section thins out the arpeggio
        for s in range(6):
            if sparse and s % 2 == 1:
                continue
            m = tones[min(pat[s], len(tones) - 1)]
            vel = (0.46 if s == 0 else 0.34) * rng.uniform(0.9, 1.1)
            tt = T(b * 6 + s) + rng.uniform(-0.004, 0.004)
            add_at(box, music_box_note(float(mtof(m)), 2.2, rng, vel), tt, wrap=True)
        for (st, m, _ln) in _L_MEL[b]:
            det = 0.0
            if 8 <= b < 16 and m == 88:
                det = -32.0                                    # one tine slowly going out of tune
            if b >= 16 and m == 91:
                det = +22.0
            tt = T(b * 6 + st) + rng.uniform(-0.005, 0.005)
            note = music_box_note(float(mtof(m)), 2.6, rng, 0.95 * rng.uniform(0.92, 1.05), det)
            add_at(box, note, tt, wrap=True)
            add_at(ghost, music_box_note(float(mtof(m - 12)), 2.4, rng, 0.5, -9.0), tt + 1.5 * STEP, wrap=True)
    # reversed bell swells breathing into the start of each section (and into the loop point)
    swell = np.zeros(n)
    for bar_start in (8, 16, 24):
        d = 2.4
        sw = stack(chime_tone(880.0, d, tau=1.1, bright=0.5), 0.6 * chime_tone(1318.5, d, tau=0.9, bright=0.4))
        sw = sw[::-1] * curve([(0, 0), (d, 1)], len(sw), "cos") ** 1.5
        sw = sw * curve([(0, 1), (d - 0.03, 1), (d, 0)], len(sw), "cos")      # no hard stop at the "strike"
        add_at(swell, sw, bar_start * BAR - d, wrap=True)
    # soft pad: a note per chord *run*, long overlapping attack / release
    pad = np.zeros(n)
    runs = []
    for b, ch in enumerate(_L_BARS):
        if runs and runs[-1][0] == ch and runs[-1][2] == b:
            runs[-1][2] = b + 1
        else:
            runs.append([ch, b, b + 1])
    for ch, b0, b1 in runs:
        d = (b1 - b0) * BAR + 2.2
        for f in _PAD[ch]:
            note = pad_note(float(mtof(f)), d, rng, attack=1.2, release=1.6, cutoff=820.0, voices=3, detune_c=8.0, sub=0.15)
            add_at(pad, note * 0.16, b0 * BAR - 0.5, wrap=True)
        add_at(pad, 0.20 * pad_note(float(mtof(_BASS[ch] + 12)), d, rng, attack=0.9, release=1.4, cutoff=300.0,
                                    voices=2, detune_c=5.0, sub=0.5), b0 * BAR - 0.4, wrap=True)
    # throbbing sub drone: two sines 0.4 Hz apart (whole cycles per loop)
    drone = 0.11 * (sine(55.0, n) + sine(55.4, n, 0.3)) + 0.03 * sine(110.0, n, 0.2)
    box = reverb(box, rt60=2.4, wet=0.42, dry=0.85, predelay=0.02, damp=0.45, seed=71, circular=True)
    ghost = lowpass(reverb(ghost, rt60=3.0, wet=1.0, dry=0.2, predelay=0.04, damp=0.6, seed=72, circular=True),
                    3500.0, order=2, circular=True)
    swell = reverb(swell, rt60=2.0, wet=0.5, dry=0.8, seed=73, circular=True)
    pad = reverb(pad, rt60=3.2, wet=0.40, dry=0.8, predelay=0.03, damp=0.5, seed=74, circular=True)
    y = box * 1.0 + ghost * 0.16 + swell * 0.34 + pad * 1.0 + drone
    return _master(y, -4.0, comp=(-26.0, 2.0), drive=1.0)


# ===================================================================================== tension
_T_ROOT = [73.42] * 4 + [58.27] * 2 + [73.42] * 2 + [77.78] * 2 + [73.42] * 2 + [49.0] * 2 + [55.0] * 4
_T_PAD = [  # (first bar, bars, chord tones in Hz)
    (0, 2, (146.83, 174.61, 220.0)), (2, 2, (146.83, 174.61, 220.0)), (4, 2, (116.54, 146.83, 174.61)),
    (6, 2, (146.83, 174.61, 220.0)), (8, 2, (155.56, 196.0, 233.08)), (10, 2, (146.83, 174.61, 220.0)),
    (12, 2, (98.0, 116.54, 146.83)), (14, 2, (110.0, 138.59, 164.81, 196.0)), (16, 2, (110.0, 138.59, 164.81)),
]


@sound("music.tension", "Tense music", stream=True)
def music_tension(v, rng):
    L = 45.0
    n = ns(L)
    bars = 18
    BAR = L / bars                    # 2.5 s : 96 BPM, 4/4
    BEAT = BAR / 4.0
    EIGHTH = BEAT / 2.0
    # --- pulsing low strings (3 + 3 + 2 accent grouping)
    acc = [1.0, 0.55, 0.55, 0.92, 0.55, 0.55, 0.86, 0.55]
    pulses = np.zeros(n)
    for b in range(bars):
        f = _T_ROOT[b]
        for k in range(8):
            d = 0.34
            m = ns(d)
            y = supersaw(f, m, 3, 9.0, rng) + 0.55 * supersaw(2 * f, m, 3, 9.0, rng) + 0.3 * sine(f, m)
            y = lowpass(y, 780.0 + 520.0 * acc[k], order=2)
            y *= curve([(0, 0), (0.014, 1.0), (0.11, 0.5), (0.22, 0.32), (d, 0.0)], m, "cos")
            add_at(pulses, y * acc[k] * 0.55, b * BAR + k * EIGHTH, wrap=True)
    # --- sustained string chords that creep along underneath
    pad = np.zeros(n)
    for b0, nb, fs in _T_PAD:
        d = nb * BAR + 1.6
        for f in fs:
            add_at(pad, 0.20 * string_swell(f, d, rng, attack=0.9, release=1.2, cutoff=1250.0, vib_cents=4.0),
                   b0 * BAR - 0.4, wrap=True)
    # --- ticking clock (accent on the bar, soft off-beat tick)
    tick = np.zeros(n)
    for b in range(bars):
        for k in range(4):
            tt = b * BAR + k * BEAT
            if k == 0:
                add_at(tick, 0.62 * wood_tock(rng, f=1020.0, dur=0.16, snap=0.5), tt, wrap=True)
            else:
                add_at(tick, 0.42 * wood_tock(rng, f=1480.0, dur=0.12, snap=0.55), tt, wrap=True)
            add_at(tick, 0.10 * wood_tock(rng, f=2300.0, dur=0.08, snap=0.7), tt + EIGHTH, wrap=True)
    # --- high tremolo cluster: blooms across bars 8-15 and has gone again by the loop point
    hi = np.zeros(n)
    for f in (880.0, 1174.66, 1244.51):
        d = 20.5
        y = string_swell(f, d, rng, attack=9.0, release=6.0, cutoff=3000.0, vib_cents=10.0, vib_hz=5.6)
        t = time_axis(len(y))
        y = y * (0.72 + 0.28 * np.sin(TAU * 8.4 * t + rng.random() * 6.0))
        y = bandpass(y, 2200.0, 0.6) * 1.0 + 0.4 * y
        add_at(hi, 0.14 * y, 7.8 * BAR, wrap=True)
    # --- low impacts every four bars + a riser into the last section
    hits = np.zeros(n)
    for b in (4, 8, 12, 16):
        add_at(hits, 0.55 * thump(72.0, 34.0, 1.0, 0.05, 0.28, click=0.25, rng=rng), b * BAR, wrap=True)
        add_at(hits, 0.30 * timpani(73.42, 1.2, rng), b * BAR, wrap=True)
    add_at(hits, 0.16 * noise_swell(rng, 5.0, 700.0, 9000.0, peak_at=0.94, power=2.4), 15 * BAR - 1.25 + 0.0, wrap=True)
    pulses = reverb(pulses, rt60=1.2, wet=0.30, dry=0.9, predelay=0.012, damp=0.6, seed=75, circular=True)
    pad = reverb(pad, rt60=2.8, wet=0.40, dry=0.8, predelay=0.02, damp=0.5, seed=76, circular=True)
    hi = reverb(hi, rt60=2.2, wet=0.5, dry=0.7, seed=77, circular=True)
    hits = reverb(hits, rt60=1.8, wet=0.4, dry=0.9, seed=78, circular=True)
    tick = reverb(tick, rt60=0.5, wet=0.15, dry=0.95, seed=79, circular=True)
    y = pulses * 1.0 + pad * 0.9 + tick * 0.55 + hi * 0.9 + hits * 0.8
    return _master(y, -4.0, comp=(-24.0, 2.4), drive=1.1)


# ======================================================================================== final
_F_CH = ["Dm", "Dm", "Dm", "Dm", "Bb", "Bb", "Gm", "A", "Dm", "Dm", "F", "Gm", "A", "A", "Dm", "Dm"]
_F_STR = {  # string pad voicings (MIDI)
    "Dm": (50, 57, 62, 65), "Bb": (46, 53, 58, 62), "Gm": (43, 50, 58, 62), "A": (45, 52, 57, 61), "F": (41, 48, 57, 60),
}
_F_ARP = {  # arpeggio ladders (MIDI), ascending
    "Dm": (50, 53, 57, 62, 65, 69, 74), "Bb": (46, 50, 53, 58, 62, 65, 70), "Gm": (43, 46, 50, 55, 58, 62, 67),
    "A": (45, 49, 52, 57, 61, 64, 69), "F": (41, 45, 48, 53, 57, 60, 65),
}
_F_BASS = [38, 38, 38, 38, 34, 34, 43, 45, 38, 40, 41, 43, 45, 45, 38, 38]


def _shepard(n, period, rng, f_lo=27.5, octaves=9, center=520.0, sigma_oct=1.25):
    """Shepard-Risset glide: endlessly rising pitch, phase-exact, periodic over any multiple of ``period``."""
    t = time_axis(n)
    out = np.zeros(n)
    off = float(rng.uniform(0, TAU))
    for i in range(octaves + 1):
        base = f_lo * 2.0 ** i
        grow = 2.0 ** (t / period)
        ph = TAU * base * period / math.log(2.0) * (grow - 1.0) + off
        f = base * grow
        amp = np.exp(-0.5 * (np.log2(f / center) / sigma_oct) ** 2)
        out += amp * np.sin(ph)
        off += TAU * base * period / math.log(2.0)       # partial i+1 starts where partial i ends
    return out / 3.0


@sound("music.final", "Dramatic music", stream=True)
def music_final(v, rng):
    L = 60.0
    n = ns(L)
    bars = 16
    BAR = L / bars                    # 3.75 s : 64 BPM, 4/4
    BEAT = BAR / 4.0
    EIGHTH = BEAT / 2.0
    t = time_axis(n)
    # overall build: 0 at the loop point -> 1 at the climax (bar 14) -> back to 0
    build = curve([(0.0, 0.0), (7.5, 0.10), (15.0, 0.25), (30.0, 0.55), (45.0, 0.95), (52.5, 1.0), (57.0, 0.22), (60.0, 0.0)],
                  n, "cos")
    # --- drone (D1 + D2) with a slowly opening filter, and a heartbeat that gains weight
    drone = 0.55 * saw(36.71, n) + 0.55 * saw(73.42 * cents(4.0), n) + 0.4 * sine(36.71, n)
    cut = 180.0 + 220.0 * (0.5 + 0.5 * np.sin(TAU * t / L - 1.57)) + 380.0 * build
    drone = tv_biquad(drone, "lp", cut, 0.8, circular=True) * 0.30
    heart = np.zeros(n)
    for k in range(int(L / BEAT)):
        lvl = 0.10 + 0.42 * float(build[min(int(k * BEAT * SR), n - 1)])
        add_at(heart, lvl * thump(62.0, 36.0, 0.5, 0.04, 0.16, click=0.2, rng=rng), k * BEAT, wrap=True)
        if k % 2 == 1:
            add_at(heart, 0.45 * lvl * thump(70.0, 40.0, 0.4, 0.035, 0.12), k * BEAT + EIGHTH, wrap=True)
    # --- rising ostinato: soft felt-piano / celesta pluck that climbs an octave in the last third
    ost = np.zeros(n)
    pat = [0, 2, 1, 3, 2, 4, 3, 5]
    for b in range(4, 14):
        ch = _F_CH[b]
        key = "Dm" if ch == "Dm" else ch
        ladder = _F_ARP[key]
        lift = 12 if b >= 10 else 0
        for k in range(8):
            m = ladder[pat[k]] + lift
            vel = (0.14 + 0.80 * float(build[min(int((b * BAR + k * EIGHTH) * SR), n - 1)])) * (1.0 if k % 4 == 0 else 0.78)
            f = float(mtof(m))
            y = modal([f, 2 * f, 3 * f, 4.01 * f, 5.02 * f], [1.0, 0.5, 0.3, 0.16, 0.09],
                      [0.55, 0.32, 0.22, 0.14, 0.10], 1.1)
            add_at(y, 0.12 * noise_burst(rng, 0.02, 1500, 6000, tau=0.002), 0.0)
            add_at(ost, vel * 0.55 * y, b * BAR + k * EIGHTH, wrap=True)
    # --- strings: bar-long chords, brightening and thickening with the build
    strings = np.zeros(n)
    for b in range(2, 16):
        ch = _F_CH[b]
        g = 0.07 + 0.60 * float(build[min(int((b + 0.5) * BAR * SR), n - 1)])
        for m in _F_STR[ch]:
            add_at(strings, g * 0.5 * string_swell(float(mtof(m)), BAR + 2.4, rng, attack=1.5, release=1.8,
                                                    cutoff=1100.0 + 1800.0 * float(build[min(int(b * BAR * SR), n - 1)])),
                   b * BAR - 0.6, wrap=True)
        if b >= 8:
            add_at(strings, g * 0.26 * string_swell(float(mtof(_F_STR[ch][-1] + 12)), BAR + 2.4, rng, attack=1.2,
                                                    release=1.8, cutoff=3200.0, vib_cents=9.0), b * BAR - 0.5, wrap=True)
    # singing high line climbing a step every two bars (the "slow rise")
    line = np.zeros(n)
    for (b0, nb, m) in ((8, 2, 81), (10, 1, 82), (11, 1, 84), (12, 2, 86)):
        add_at(line, 0.19 * string_swell(float(mtof(m)), nb * BAR + 1.8, rng, attack=1.0, release=1.4,
                                         cutoff=4800.0, vib_cents=14.0, vib_hz=5.4, voices=4), b0 * BAR - 0.3, wrap=True)
    # --- low brass swell into the climax, timpani roll and cymbal wash
    brs = np.zeros(n)
    for m, g in ((38, 0.5), (45, 0.4), (50, 0.4), (57, 0.25)):
        add_at(brs, g * brass(float(mtof(m)), 13.0, rng, attack=7.0, release=3.2, bright=0.8, f_open=2100.0, detune=6.0),
               12 * BAR - 0.5, wrap=True)
    perc = np.zeros(n)
    t0 = 11 * BAR
    k = 0
    while t0 + k * 0.125 < 14 * BAR:
        u = (k * 0.125) / (3 * BAR)
        add_at(perc, (0.10 + 0.55 * u ** 1.6) * timpani(73.42 if k % 2 == 0 else 55.0, 0.5, rng), t0 + k * 0.125, wrap=True)
        k += 1
    for b in (12, 13, 14):
        add_at(perc, (0.7 if b == 14 else 0.45) * timpani(73.42, 2.0, rng), b * BAR, wrap=True)
    add_at(perc, 0.8 * thump(66.0, 30.0, 2.5, 0.06, 0.65, click=0.3, rng=rng), 14 * BAR, wrap=True)
    cym = noise_swell(rng, 7.0, 4500.0, 13000.0, peak_at=0.97, power=2.0)
    add_at(perc, 0.09 * cym, 12.1 * BAR, wrap=True)
    add_at(perc, 0.14 * exp_decay(ns(5.5), 1.5, 0.004) * band(white(ns(5.5), rng), 3500.0, 12500.0, order=2),
           14 * BAR, wrap=True)
    # --- tolling low bell (twice) and the endlessly rising Shepard glide
    bell = np.zeros(n)
    for tb in (1 * BAR, 9 * BAR):
        add_at(bell, 0.16 * metal_hit(rng, 146.83, 5.0, 2.0, 0.5), tb, wrap=True)
    shep = _shepard(n, 20.0, rng) * (0.03 + 0.22 * build)
    ost = reverb(ost, rt60=2.6, wet=0.45, dry=0.8, predelay=0.02, damp=0.5, seed=81, circular=True)
    strings = reverb(strings, rt60=3.4, wet=0.40, dry=0.8, predelay=0.03, damp=0.5, seed=82, circular=True)
    line = reverb(line, rt60=3.0, wet=0.5, dry=0.7, seed=83, circular=True)
    brs = reverb(brs, rt60=2.8, wet=0.35, dry=0.85, seed=84, circular=True)
    perc = reverb(perc, rt60=3.2, wet=0.45, dry=0.85, predelay=0.02, seed=85, circular=True)
    bell = reverb(bell, rt60=3.5, wet=0.6, dry=0.7, seed=86, circular=True)
    y = drone * 1.0 + heart * 1.0 + ost * 1.0 + strings * 1.0 + line * 1.1 + brs * 1.2 + perc * 1.1 + bell + shep * 0.9
    return _master(y, -4.0, comp=(-16.0, 1.6), drive=1.0)
