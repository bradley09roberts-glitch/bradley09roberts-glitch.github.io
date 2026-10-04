"""The doll: ten sung syllables (the showpiece) + servo / eyes / scanner effects.

Voice model (see voice.py): glottal-pulse harmonic source -> cascade of time-varying formants,
aspiration + consonant noise bursts, vibrato, and a slightly detuned doubled voice with a
second formant scale for a sweet-creepy child-doll timbre.  Melody (original, D major
pentatonic):

    mu   gung hwa  kko  chi | pi   eot  seum ni   da
    D4   E4   F#4  A4   B4  | E5   D5   B4   A4   A3 (octave drop, held)

i.e. rising across the first five syllables, a little fourth-skip up on "pi", falling steps
and then an octave drop to the low held "da".
"""
from __future__ import annotations

import math

import numpy as np

from .core import (SR, TAU, add_at, cents, curve, db2lin, fade, finish, make_rng, mtof, ns, peak,
                   phase_cycles, pulse, sine, stack)
from .dsp import band, highpass, lowpass, reverb
from .instruments import chirp, fm, modal, noise_burst, sparks, thump
from .loudness import perceptual_level_db
from .registry import sound, sound_group
from .voice import aspiration, build_tracks, harmonic_voice, noise_band, phone, pitch_track

# ------------------------------------------------------------------ voice character
CHILD = 1.14          # formant scale (small vocal tract)
# (detune cents, vibrato Hz, vibrato phase, extra formant scale, level)  - the second voice is
# a quieter, slightly detuned double with its own vibrato and a 4 % larger formant scale
VOICES = [
    (+3.0, 5.5, 0.0, 1.000, 1.00),
    (-3.0, 5.9, 2.2, 1.040, 0.60),
]
BREATH_FLOOR_DB = -24.0   # constant airiness under the voiced signal
FORMANT_FLUTTER = 0.012   # slow random formant wobble (fraction)
GLASS_DB = -23.0          # soft sine an octave up ("celesta" sparkle on the doll voice)

# ------------------------------------------------------------------ syllable scores
# Each syllable: note (MIDI), duration, phone segments (phone, t0, t1, glide-in seconds),
# release window and noise "extras" (kind, params).  Times in seconds.
M = 12  # octave


def _syllables():
    P = phone
    S = []
    # 1 "mu"  D4 - nasal murmur opens into /u/
    S.append(dict(
        text="mu", midi=62, dur=0.34,
        segs=[(P("m"), 0.000, 0.075), (P("u"), 0.075, 0.340, 0.040)],
        rel=(0.255, 0.340), scoop=-40,
        extras=[("burst", 0.071, 0.010, 200, 900, -24, 1.5, 3.0)],      # soft lip release thump
    ))
    # 2 "gung"  E4 - soft velar g, /u/, nasal ng
    S.append(dict(
        text="gung", midi=64, dur=0.40,
        segs=[(P("g"), 0.000, 0.022), (P("u"), 0.022, 0.250, 0.025), (P("ng"), 0.250, 0.400, 0.055)],
        rel=(0.320, 0.400), scoop=-30,
        extras=[("burst", 0.000, 0.020, 800, 2800, -16, 0.5, 5.0)],
    ))
    # 3 "hwa"  F#4 - breathy h through a rounded vowel, w-glide into /a/
    S.append(dict(
        text="hwa", midi=66, dur=0.36,
        segs=[(P("u", v=-60, a=-8), 0.000, 0.045), (P("u"), 0.045, 0.090, 0.022), (P("a"), 0.090, 0.360, 0.070)],
        rel=(0.285, 0.360), scoop=-35,
        extras=[],
    ))
    # 4 "kko"  A4 - tense velar: hard burst, short VOT, pressed onset (pitch starts sharp)
    S.append(dict(
        text="kko", midi=69, dur=0.34,
        segs=[(P("o", v=-60, a=-5), 0.000, 0.026), (P("o"), 0.026, 0.340, 0.014)],
        rel=(0.262, 0.340), scoop=+55, scoop_tau=0.03,
        extras=[("burst", 0.000, 0.026, 1000, 3200, -2, 0.4, 7.0),
                ("burst", 0.000, 0.004, 1800, 7000, -8, 0.2, 1.6)],
    ))
    # 5 "chi"  B4 - palatal affricate: burst + sibilant friction, aspirated into /i/
    S.append(dict(
        text="chi", midi=71, dur=0.36,
        segs=[(P("i", v=-60, a=-9), 0.000, 0.058), (P("i"), 0.058, 0.360, 0.022)],
        rel=(0.290, 0.360), scoop=-30,
        extras=[("fric", 0.000, 0.085, 2900, 8200, -4, 0.004, 0.030),
                ("burst", 0.000, 0.012, 2500, 9000, -5, 0.3, 2.5)],
    ))
    # 6 "pi"  E5 - the little skip up: soft bilabial p, bright /i/
    S.append(dict(
        text="pi", midi=76, dur=0.30,
        segs=[(P("i", v=-60, a=-9), 0.000, 0.022), (P("i"), 0.022, 0.300, 0.012)],
        rel=(0.228, 0.300), scoop=-38, vib=(6.0, 17.0),
        extras=[("burst", 0.000, 0.012, 250, 4200, -8, 0.3, 3.0)],
    ))
    # 7 "eot"  D5 - open vowel then an unreleased t (voice dies into the closure)
    S.append(dict(
        text="eot", midi=74, dur=0.27,
        segs=[(P("A"), 0.000, 0.170), (P("tcl"), 0.170, 0.270, 0.050)],
        rel=(0.205, 0.265), scoop=-25, attack=0.002,
        extras=[("burst", 0.222, 0.012, 2800, 7500, -22, 0.2, 3.0)],
        glide=[(0.0, 0.0), (0.17, 0.0), (0.26, -55.0)],
    ))
    # 8 "seum"  B4 - sibilant s, /eu/, closing m
    S.append(dict(
        text="seum", midi=71, dur=0.42,
        segs=[(P("M", v=-60), 0.000, 0.070), (P("M"), 0.070, 0.300, 0.040), (P("m"), 0.300, 0.420, 0.050)],
        rel=(0.350, 0.420), scoop=-30,
        extras=[("fric", 0.000, 0.100, 5200, 11500, -5, 0.006, 0.040)],
    ))
    # 9 "ni"  A4 - quick n, bright /i/
    S.append(dict(
        text="ni", midi=69, dur=0.30,
        segs=[(P("n"), 0.000, 0.055), (P("i"), 0.055, 0.300, 0.030)],
        rel=(0.228, 0.300), scoop=-32,
        extras=[("burst", 0.052, 0.010, 400, 3000, -20, 0.5, 2.5)],
    ))
    # 10 "da"  A3 - octave drop, low held /a/ with slowing, deepening vibrato and a sag
    S.append(dict(
        text="da", midi=57, dur=0.60,
        segs=[(P("d"), 0.000, 0.022), (P("a"), 0.022, 0.600, 0.030)],
        rel=(0.455, 0.600), scoop=-25, vib=(5.6, 13.0), vib_end=(4.7, 22.0), vib_delay=0.11,
        glide=[(0.0, 0.0), (0.25, 0.0), (0.60, -42.0)],
        amp=[(0.0, 0.85), (0.07, 1.0), (0.30, 0.95), (0.60, 0.80)],
        extras=[("burst", 0.000, 0.010, 3000, 9500, -12, 0.2, 2.5)],
    ))
    return S


def _render_noise_extras(rng, n, extras, ref):
    out = np.zeros(n)
    for ex in extras:
        kind = ex[0]
        if kind == "burst":
            _, t0, dur, lo, hi, db, att_ms, dec_ms = ex
            x = noise_band(rng, max(dur, 0.002) + 0.03, lo, hi)
            t = np.arange(len(x)) / SR
            att = att_ms * 1e-3
            env = np.where(t < att, t / max(att, 1e-6), np.exp(-(t - att) / (dec_ms * 1e-3)))
            add_at(out, x * env * db2lin(db) * ref, t0)
        elif kind == "fric":
            _, t0, t1, lo, hi, db, att, rel = ex
            d = t1 - t0
            x = noise_band(rng, d + rel + 0.01, lo, hi)
            env = curve([(0.0, 0.0), (att, 1.0), (max(d - rel, att + 1e-3), 0.8), (d, 0.0), (d + rel + 0.01, 0.0)],
                        len(x), "lin")
            add_at(out, x * env * db2lin(db) * ref, t0)
    return out


def render_syllable(spec, idx: int) -> np.ndarray:
    """Render one dry syllable (unit voiced level ~1; loudness matching happens afterwards)."""
    rng = make_rng("doll.syllable", idx)
    D = spec["dur"]
    n = ns(D)
    tracks = build_tracks(spec["segs"], n, child=CHILD)

    # ---- global amplitude: onset ramp, optional contour, release window
    att = spec.get("attack", 0.004)
    env = curve([(0.0, 0.0), (att, 1.0)], n, "cos")
    r0, r1 = spec["rel"]
    env *= curve([(0.0, 1.0), (r0, 1.0), (r1, 0.0), (D, 0.0)], n, "cos")
    if "amp" in spec:
        env *= curve(spec["amp"], n, "cos")

    # ---- voiced doubled voice
    note = float(mtof(spec["midi"]))
    vib_rate, vib_cents = spec.get("vib", (5.6, 14.0))
    voiced = np.zeros(n)
    f0_main = None
    drng = make_rng("doll.syllable.phase", idx)
    for vi, (det, vrate, vph, fsc, lvl) in enumerate(VOICES):
        kw = dict(vib_rate=vrate * vib_rate / 5.6, vib_cents=vib_cents, vib_phase=vph,
                  scoop_cents=spec.get("scoop", -40), scoop_tau=spec.get("scoop_tau", 0.035),
                  vib_delay=spec.get("vib_delay", 0.09), glide=spec.get("glide"))
        if "vib_end" in spec:
            kw["vib_rate_end"] = spec["vib_end"][0] * vrate / 5.6
            kw["vib_cents_end"] = spec["vib_end"][1]
        f0 = pitch_track(n, note * float(cents(det)), rng, **kw)
        if f0_main is None:
            f0_main = f0
        # fundamentals of the two voices are in phase in the middle of the syllable (no null)
        ph0 = 0.0 if vi == 0 else float(-(note * (cents(det) - cents(VOICES[0][0]))) * D / 2.0) % 1.0
        voiced += lvl * harmonic_voice(f0, tracks, n, formant_scale=fsc, phase0=ph0,
                                       rand_phase=None if vi == 0 else drng,
                                       flutter=FORMANT_FLUTTER, flutter_rng=rng)
    voiced /= np.sqrt(sum(v[4] ** 2 for v in VOICES))  # keep the doubled sum near unit RMS
    v_amp = tracks["v_lin"]
    y = voiced * v_amp * env

    # ---- octave-up glass sine (fades in with the voiced level)
    glass_mask = np.clip(tracks["v_lin"] / 0.35, 0.0, 1.0)
    y += sine(2.0 * f0_main, n) * db2lin(GLASS_DB) * v_amp * env * glass_mask

    # ---- aspiration: breath floor + phone-specific h / aspirated release
    asp = aspiration(n, rng, tracks, formant_scale=1.0)
    a_amp = tracks["a_lin"] + db2lin(BREATH_FLOOR_DB) * v_amp
    y += asp * a_amp * env

    # ---- consonant noise
    ref = 1.0
    y += _render_noise_extras(rng, n, spec["extras"], ref)

    # dry cleanup: remove sub-bass, tiny edge fades
    y = highpass(y, 140.0, order=2)
    return fade(y, 0.0015, 0.004)


@sound_group("doll.syllables", [
    (f"doll.syllable_{i}", "Doll sings", f"doll/syllable_{i:02d}") for i in range(1, 11)
])
def doll_syllables():
    specs = _syllables()
    raw = [render_syllable(s, i + 1) for i, s in enumerate(specs)]
    # loudness-match on the perceptual level (70/30 K-/A-weighted active level, see loudness.py),
    # then a gentle common soft-limit and peak normalisation
    target_db = -16.0
    out = []
    for x in raw:
        y = x * 10.0 ** ((target_db - perceptual_level_db(x)) / 20.0)
        out.append(y)
    # tame the loudest consonant bursts (keeps vowels untouched, bursts rounded)
    lim = 0.60
    out = [lim * np.tanh(y / lim) for y in out]
    gpk = max(peak(y) for y in out)
    g = db2lin(-3.0) / gpk
    return [y * g for y in out]


# ===================================================================== doll mechanics
def _motor_run(rng, dur, f_start, f_end, level=1.0, grind=0.5):
    """One servo run: accelerating motor tone + gear whine + grinding noise."""
    n = ns(dur)
    t = np.arange(n) / n
    f = f_start + (f_end - f_start) * np.clip(t / 0.55, 0, 1) ** 0.8
    f = f * (1.0 - 0.45 * np.clip((t - 0.88) / 0.12, 0, 1))          # decelerates into the click
    body = 0.4 * pulse(f, n, 0.35) + 0.6 * sine(f * 2, n) + 0.3 * sine(f * 3.01, n)
    whine = 0.22 * sine(f * 6.7, n) + 0.12 * sine(f * 10.3, n)
    gr = grind * 0.45 * band(rng.standard_normal(n), 300, 2400, order=2)
    gr *= 0.6 + 0.4 * sine(f * 0.5, n)
    y = body + whine + gr
    env = curve([(0.0, 0.0), (0.012, 1.0), (dur - 0.018, 0.9), (dur, 0.0)], n, "cos")
    return level * y * env


@sound("doll.turn_servo", "Doll's head turns")
def doll_turn_servo(v, rng):
    dur = 1.0
    n = ns(dur)
    out = np.zeros(n)
    runs = [(0.000, 0.228), (0.256, 0.498), (0.528, 0.742), (0.778, 0.985)]
    clicks = [0.240, 0.512, 0.760]
    f_sets = [(110, 300), (125, 320), (140, 340), (150, 300)]
    for (a, b), (f0, f1) in zip(runs, f_sets):
        add_at(out, _motor_run(rng, b - a, f0, f1), a)
    for k, c in enumerate(clicks):
        # ratchet pawl: heavy clack = low thunk + two metal resonances + tick
        clack = stack(0.9 * thump(170 - 12 * k, 62, 0.07, 0.012, 0.022),
                      modal([1350 + 90 * k, 3050 + 120 * k], [0.7, 0.35], [0.011, 0.006], 0.06),
                      0.6 * noise_burst(rng, 0.03, 2500, 9000, tau=0.0018))
        add_at(out, clack * 1.5, c)
    out = lowpass(out, 4800, order=4)
    out = reverb(out, rt60=0.35, wet=0.12, predelay=0.004, damp=0.6, seed=11)
    return finish(out, -3.0, -14.0, 0.003, 0.02)


@sound("doll.lock_on", "Doll locks on")
def doll_lock_on(v, rng):
    n = ns(0.4)
    out = np.zeros(n)
    # fast servo zip (pitch falls as the head snaps to target)
    zip_n = ns(0.075)
    f = np.exp(np.linspace(math.log(1500), math.log(420), zip_n))
    zp = (0.7 * pulse(f, zip_n, 0.3) + 0.4 * sine(f * 2, zip_n)) * curve([(0, 0), (0.008, 1), (0.075, 0.5)], zip_n, "cos")
    zp += 0.35 * band(rng.standard_normal(zip_n), 800, 5000) * np.hanning(zip_n)
    add_at(out, zp, 0.0)
    # sharp snap
    snap = stack(thump(210, 70, 0.09, 0.012, 0.025) * 1.1,
                 modal([950, 2150, 3700], [0.8, 0.5, 0.3], [0.014, 0.009, 0.006], 0.09),
                 0.8 * noise_burst(rng, 0.03, 2000, 9500, tau=0.002))
    add_at(out, snap, 0.072)
    # tiny chirp + faint echo of it
    ch = chirp(2300, 3700, 0.055, "exp") * 0.55
    add_at(out, ch, 0.15)
    add_at(out, ch * 0.28, 0.215)
    out = reverb(out, rt60=0.3, wet=0.10, predelay=0.003, damp=0.6, seed=12)
    return finish(out, -3.0, -14.0, 0.002, 0.03)


def _hum(n, f_curve, rng, buzz=0.7, whine=None):
    """Mains-style buzz: harmonic series on a sweeping fundamental + optional whine."""
    ph = phase_cycles(f_curve, n)
    y = np.zeros(n)
    for k in range(1, 22):
        y += (1.0 / k ** buzz) * np.sin(TAU * k * ph + 0.7 * k)
    if whine is not None:
        y += 0.5 * np.sin(TAU * phase_cycles(whine, n))
    return y


@sound("doll.eyes_on", "Doll's eyes glow")
def doll_eyes_on(v, rng):
    dur = 0.7
    n = ns(dur)
    fcur = np.exp(np.linspace(math.log(52), math.log(190), n))
    whine = np.exp(np.linspace(math.log(500), math.log(2600), n))
    y = _hum(n, fcur, rng, 0.85, whine)
    y = lowpass(y, 3600, order=4)
    amp = curve([(0, 0.0), (0.12, 0.25), (0.55, 1.0), (0.64, 0.9), (0.70, 0.0)], n, "cos")
    y = y * amp
    # crackle gets denser as the circuit charges, a final bright glint
    sp = sparks(rng, dur, np.linspace(25, 260, n), lo=1800, hi=8000, tau=0.0012) * 1.8
    sp *= curve([(0, 0.2), (0.5, 1.0), (0.62, 0.6), (0.7, 0)], n, "lin")
    y = y / (np.sqrt(np.mean(y * y)) + 1e-9) * 0.35 + sp
    glint = modal([1760, 2637, 3520], [0.9, 0.35, 0.2], [0.07, 0.05, 0.03], 0.2)
    add_at(y, glint * 0.22, 0.52)
    y = reverb(y, rt60=0.4, wet=0.08, predelay=0.004, damp=0.5, seed=13)
    return finish(y, -3.0, -13.0, 0.004, 0.02)


@sound("doll.eyes_off", "Doll's eyes dim")
def doll_eyes_off(v, rng):
    dur = 0.5
    n = ns(dur)
    fcur = np.exp(np.linspace(math.log(185), math.log(34), n)) * (1.0 - 0.1 * np.sin(TAU * 11 * np.arange(n) / SR))
    whine = np.exp(np.linspace(math.log(2100), math.log(260), n))
    y = _hum(n, fcur, rng, 0.9, whine)
    y = lowpass(y, 3200, order=4)
    amp = curve([(0, 0.0), (0.008, 1.0), (0.16, 0.8), (0.4, 0.2), (0.5, 0.0)], n, "cos")
    y = y / (np.sqrt(np.mean(y * y)) + 1e-9) * 0.35 * amp
    sp = sparks(rng, dur, np.linspace(160, 5, n), lo=1500, hi=7000, tau=0.0013) * 1.4
    sp *= curve([(0, 1.0), (0.2, 0.6), (0.4, 0.1), (0.5, 0)], n, "lin")
    y += sp
    add_at(y, thump(120, 42, 0.12, 0.03, 0.05) * 0.5, 0.36)       # little power-down thunk
    y = reverb(y, rt60=0.35, wet=0.08, predelay=0.004, damp=0.5, seed=14)
    return finish(y, -3.0, -13.0, 0.002, 0.04)


@sound("doll.scan_beep", "Scanner beeps", variants=2)
def doll_scan_beep(v, rng):
    n = ns(0.3)
    out = np.zeros(n)
    m = ns(0.115)
    t = np.linspace(0, 1, m)
    lo, hi = ((820.0, 2650.0), (900.0, 2900.0))[v]
    f = lo * (hi / lo) ** (t ** 0.8)
    blip = fm(f, 2.0, 0.8 * (1 - t) + 0.15, m)
    blip += 0.3 * sine(f * 3, m)
    blip *= curve([(0, 0), (0.006, 1.0), (0.07, 0.7), (0.115, 0.0)], m, "cos") * (1.0 + 0.25 * sine(38.0, m))
    add_at(out, blip, 0.0)
    # radar-ish echoes (darker each time)
    add_at(out, lowpass(blip, 2600, order=2) * 0.34, 0.10)
    add_at(out, lowpass(blip, 1500, order=2) * 0.15, 0.20)
    return finish(out, -3.0, -13.0, 0.003, 0.03)
