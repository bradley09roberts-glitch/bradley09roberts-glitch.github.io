#!/usr/bin/env python3
"""
Starforged sound synthesizer. Every sound effect in the mod is generated here from scratch
(oscillators, filtered noise, envelopes, convolution reverb) and encoded to mono Ogg Vorbis with ffmpeg.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np
from scipy import signal

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/starforged/sounds")
rng = np.random.default_rng(1234)


def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def env(dur, attack=0.01, release=0.3, curve=2.0):
    t = t_axis(dur)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    r = np.clip((dur - t) / max(release, 1e-4), 0, 1) ** curve
    return a * r


def expdecay(dur, rate):
    return np.exp(-t_axis(dur) * rate)


def osc(freq, dur, kind="sine"):
    """freq may be a constant or an array (pitch glide)."""
    n = int(SR * dur)
    f = np.full(n, freq, dtype=float) if np.isscalar(freq) else np.asarray(freq, dtype=float)[:n]
    phase = 2 * np.pi * np.cumsum(f) / SR
    if kind == "sine":
        return np.sin(phase)
    if kind == "saw":
        return 2 * ((phase / (2 * np.pi)) % 1.0) - 1
    if kind == "square":
        return np.sign(np.sin(phase))
    if kind == "tri":
        return 2 * np.abs(2 * ((phase / (2 * np.pi)) % 1.0) - 1) - 1
    raise ValueError(kind)


def glide(f0, f1, dur, shape=1.0):
    t = np.linspace(0, 1, int(SR * dur)) ** shape
    return f0 * (f1 / f0) ** t


def noise(dur):
    return rng.standard_normal(int(SR * dur))


def lowpass(x, cutoff, order=4):
    b, a = signal.butter(order, min(cutoff, SR / 2 - 100) / (SR / 2), "low")
    return signal.lfilter(b, a, x)


def highpass(x, cutoff, order=4):
    b, a = signal.butter(order, cutoff / (SR / 2), "high")
    return signal.lfilter(b, a, x)


def bandpass(x, lo, hi, order=3):
    b, a = signal.butter(order, [lo / (SR / 2), min(hi, SR / 2 - 100) / (SR / 2)], "band")
    return signal.lfilter(b, a, x)


def sweep_filter(x, f0, f1, q_width=0.5, chunks=60):
    """Band-pass whose centre glides from f0 to f1 (chunked, cross-faded)."""
    out = np.zeros_like(x)
    n = len(x)
    size = n // chunks + 1
    for i in range(chunks):
        s, e = i * size, min(n, (i + 1) * size + 256)
        if s >= n:
            break
        fc = f0 * (f1 / f0) ** (i / max(1, chunks - 1))
        lo, hi = max(30, fc * (1 - q_width)), fc * (1 + q_width)
        seg = bandpass(x[max(0, s - 2048):e], lo, hi)[min(s, 2048):]
        seg = seg[:e - s]
        win = np.ones(len(seg))
        out[s:s + len(seg)] += seg * win
    return out


def reverb(x, seconds=1.2, mix=0.3, tone=4000):
    ir_len = int(SR * seconds)
    ir = rng.standard_normal(ir_len) * np.exp(-np.linspace(0, 6, ir_len))
    ir = lowpass(ir, tone)
    ir /= np.max(np.abs(ir)) + 1e-9
    wet = signal.fftconvolve(x, ir)[:len(x) + ir_len // 2]
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    wet /= np.max(np.abs(wet)) + 1e-9
    return dry * (1 - mix) + wet * mix * np.max(np.abs(x))


def pad(x, dur):
    n = int(SR * dur)
    return np.concatenate([x, np.zeros(max(0, n - len(x)))])[:max(n, len(x))]


def mixall(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def sumx(parts):
    return mixall(*list(parts))


def bell(freq, dur, bright=1.0):
    t = t_axis(dur)
    mod = np.sin(2 * np.pi * freq * 3.5 * t) * 2.0 * bright * np.exp(-t * 6)
    return np.sin(2 * np.pi * freq * t + mod) * np.exp(-t * (3.0 / dur))


def thump(freq0, freq1, dur, decay=8):
    return osc(glide(freq0, freq1, dur, 0.5), dur) * expdecay(dur, decay)


def write(name, x, gain=0.9):
    x = np.asarray(x, dtype=float)
    x = x - np.mean(x)
    fade = min(len(x), int(SR * 0.01))
    x[-fade:] *= np.linspace(1, 0, fade)
    peak = np.max(np.abs(x)) + 1e-9
    x = np.tanh(x / peak * 1.2) / np.tanh(1.2) * gain
    pcm = (x * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "5",
                        os.path.join(OUT, name + ".ogg")], check=True)
        os.unlink(tmp.name)


# ----------------------------------------------------------------------------------------------------------------
# Sound designs
# ----------------------------------------------------------------------------------------------------------------

def sounds():
    S = {}

    # --- Events ------------------------------------------------------------------------------------------------
    notes = [523.25, 659.25, 783.99, 987.77, 1174.66, 1567.98]
    arp = mixall(*[np.concatenate([np.zeros(int(SR * i * 0.16)), bell(f, 2.4, 0.6) * 0.5]) for i, f in enumerate(notes)])
    shimmer = highpass(noise(3.2), 6000) * env(3.2, 0.8, 2.0) * 0.08
    drone = (osc(130.8, 3.2, "saw") * 0.3 + osc(196.0, 3.2, "saw") * 0.2)
    drone = lowpass(drone, 900) * env(3.2, 0.6, 1.6)
    S["starfall_begin"] = reverb(mixall(arp, shimmer, drone * 0.5), 2.0, 0.45)

    whoosh = sweep_filter(noise(2.6), 4000, 250, 0.4) * env(2.6, 0.9, 0.8, 1.2)
    rumble = lowpass(noise(2.6), 150) * env(2.6, 1.2, 0.6) * 2.0
    crackle = highpass(noise(2.6), 3000) * (rng.random(int(SR * 2.6)) > 0.995) * 3.0
    S["meteor_whoosh"] = mixall(whoosh, rumble, crackle)

    boom = thump(90, 32, 2.8, 2.2) * 1.6
    blast = lowpass(noise(2.8), 2500) * expdecay(2.8, 3.5) * 1.2
    debris = bandpass(noise(2.8), 800, 4000) * expdecay(2.8, 1.5) * (rng.random(int(SR * 2.8)) > 0.97) * 3
    S["meteor_impact"] = reverb(mixall(boom, blast, debris), 1.5, 0.3, 2500)

    rise = sumx(bell(f, 2.0, 0.4) * 0.3 for f in (440, 554.4, 659.3, 880))
    sweep = osc(glide(200, 1200, 2.0, 2), 2.0, "tri") * env(2.0, 1.2, 0.6) * 0.25
    S["vault_open"] = reverb(mixall(rise, sweep, lowpass(noise(2.0), 300) * env(2.0, 0.5, 1.0) * 0.6), 1.5, 0.4)

    zap = osc(glide(900, 120, 0.6, 0.7), 0.6, "square") * expdecay(0.6, 8)
    S["rune_trigger"] = mixall(lowpass(zap, 3000) * 0.6, thump(140, 50, 0.6, 9))

    swell = (osc(55, 4.5, "saw") + osc(55.6, 4.5, "saw") + osc(82.4, 4.5, "saw")) * 0.3
    swell = lowpass(swell, 600) * env(4.5, 2.5, 1.5, 1.0)
    choir = sumx(osc(f * (1 + 0.004 * np.sin(2 * np.pi * 5 * t_axis(4.5))), 4.5, "saw") for f in (220, 277.2, 329.6, 440)) * 0.12
    choir = bandpass(choir, 300, 2400) * env(4.5, 3.0, 1.2)
    S["altar_activate"] = reverb(mixall(swell, choir, highpass(noise(4.5), 5000) * env(4.5, 3, 1) * 0.05), 2.5, 0.5)

    S["crystal_chime"] = reverb(mixall(bell(1760, 1.4, 0.8) * 0.6, bell(2637, 1.2, 0.5) * 0.4), 1.2, 0.4)

    # --- Items ----------------------------------------------------------------------------------------------
    sparkles = sumx(np.concatenate([np.zeros(int(SR * i * 0.05)), bell(1200 + i * 220, 0.6, 0.3)]) * 0.2 for i in range(8))
    S["staff_cast"] = reverb(mixall(sparkles, sweep_filter(noise(1.0), 400, 5000, 0.5) * env(1.0, 0.3, 0.5) * 0.7), 1.0, 0.35)

    S["hammer_leap"] = sweep_filter(noise(0.7), 300, 2500, 0.5) * env(0.7, 0.15, 0.4)
    S["hammer_slam"] = reverb(mixall(thump(110, 35, 1.6, 4) * 1.8, lowpass(noise(1.6), 1800) * expdecay(1.6, 6) * 1.2,
                                     bandpass(noise(1.6), 1000, 5000) * expdecay(1.6, 3) * (rng.random(int(SR * 1.6)) > 0.96) * 2), 0.8, 0.25, 2000)

    growl = lowpass(osc(glide(90, 60, 1.0), 1.0, "saw"), 500) * env(1.0, 0.1, 0.6) * 0.6
    S["scythe_reap"] = reverb(mixall(sweep_filter(noise(1.0), 3000, 200, 0.5) * env(1.0, 0.05, 0.7) * 1.2, growl), 0.9, 0.3)

    S["bow_starshot"] = reverb(mixall(osc(glide(2200, 900, 0.5), 0.5, "tri") * expdecay(0.5, 9) * 0.5, bell(1567, 0.6, 0.6) * 0.4,
                                      highpass(noise(0.5), 4000) * expdecay(0.5, 12) * 0.4), 0.8, 0.3)

    S["eclipse_blade_wave"] = reverb(mixall(sweep_filter(noise(0.9), 6000, 600, 0.35) * env(0.9, 0.02, 0.7) * 1.3,
                                            osc(glide(600, 300, 0.9), 0.9, "saw") * expdecay(0.9, 5) * 0.15), 0.9, 0.3)

    rev = (lowpass(noise(1.4), 700) * np.linspace(0, 1, int(SR * 1.4)) ** 3)
    boom2 = np.concatenate([np.zeros(int(SR * 1.35)), thump(70, 25, 1.8, 2.5) * 1.6 + lowpass(noise(1.8), 1200) * expdecay(1.8, 4)])
    S["eclipse_blade_total"] = reverb(mixall(rev, boom2, np.concatenate([np.zeros(int(SR * 1.35)), sumx(bell(f, 1.8, 0.3) for f in (311, 466, 622)) * 0.3])), 2.0, 0.4)

    lfo = 0.6 + 0.4 * np.sin(2 * np.pi * 3 * t_axis(2.2))
    S["singularity_hum"] = reverb(mixall(osc(48, 2.2, "saw") * 0.5, osc(48.7, 2.2, "saw") * 0.5) * lfo * env(2.2, 0.3, 0.6), 1.0, 0.3)
    S["singularity_hum"] = lowpass(S["singularity_hum"], 700)

    implode = sweep_filter(noise(1.0), 200, 6000, 0.4) * np.linspace(0, 1, int(SR * 1.0)) ** 2
    S["singularity_collapse"] = reverb(mixall(implode, np.concatenate([np.zeros(int(SR * 0.95)), thump(100, 30, 1.5, 3) * 2,
                                                                       np.zeros(1)])), 1.6, 0.35)

    warp = osc(glide(180, 1400, 0.8, 0.6), 0.8, "sine") * env(0.8, 0.05, 0.4)
    flang = osc(glide(184, 1430, 0.8, 0.6), 0.8, "sine") * env(0.8, 0.05, 0.4)
    S["rift_teleport"] = reverb(mixall(warp * 0.5, flang * 0.5, highpass(noise(0.8), 3000) * env(0.8, 0.02, 0.3) * 0.3), 1.0, 0.4)

    S["gauntlet_grab"] = reverb(osc(glide(80, 320, 0.6), 0.6, "saw") * env(0.6, 0.05, 0.3) * 0.5, 0.6, 0.3)
    S["gauntlet_grab"] = lowpass(S["gauntlet_grab"], 1800)
    S["gauntlet_throw"] = mixall(sweep_filter(noise(0.6), 800, 4500, 0.5) * env(0.6, 0.03, 0.4), thump(160, 60, 0.6, 10) * 0.6)

    ping = bell(1318.5, 1.5, 0.2) + np.concatenate([np.zeros(int(SR * 0.3)), bell(1318.5, 1.2, 0.2) * 0.3])
    S["compass_ping"] = reverb(ping, 1.5, 0.5)

    S["comet_jump"] = mixall(lowpass(noise(0.45), 1500) * expdecay(0.45, 14), bell(2093, 0.45, 0.4) * 0.3, bell(2637, 0.4, 0.3) * 0.2)
    crack = bandpass(noise(0.2), 1500, 6000) * expdecay(0.2, 30)
    S["egg_hatch"] = reverb(mixall(crack, np.concatenate([np.zeros(int(SR * 0.15)), sumx(bell(f, 1.2, 0.3) for f in (1046, 1318, 1568)) * 0.3])), 1.2, 0.4)
    S["starburst"] = reverb(mixall(sweep_filter(noise(0.7), 6000, 800, 0.5) * expdecay(0.7, 6), bell(1760, 0.7, 0.7) * 0.3), 0.8, 0.3)

    # --- Creatures ----------------------------------------------------------------------------------------------
    clicks = np.zeros(int(SR * 0.6))
    for i in range(9):
        p = int(SR * (0.02 + i * 0.06 + rng.random() * 0.02))
        c = bandpass(noise(0.03), 2000, 7000) * expdecay(0.03, 120)
        clicks[p:p + len(c)] += c
    S["mite_chitter"] = mixall(clicks, osc(glide(3000, 2400, 0.6), 0.6, "tri") * (np.sin(2 * np.pi * 30 * t_axis(0.6)) > 0.6) * 0.08)
    S["mite_death"] = reverb(mixall(osc(glide(2400, 600, 0.4), 0.4, "square") * expdecay(0.4, 8) * 0.2, bell(2637, 0.6, 0.6) * 0.3), 0.8, 0.3)

    whisper = bandpass(noise(2.2), 1500, 5000) * (0.5 + 0.5 * np.sin(2 * np.pi * 2.3 * t_axis(2.2)) ** 2)
    formant = bandpass(noise(2.2), 600, 900) * 0.6 * env(2.2, 0.4, 0.8)
    S["void_stalker_ambient"] = reverb(mixall(whisper * env(2.2, 0.5, 0.8) * 0.6, formant), 1.5, 0.45)
    S["void_stalker_teleport"] = reverb((lowpass(noise(0.6), 3000) * np.linspace(0, 1, int(SR * 0.6)) ** 2)[::-1] * 0.4 +
                                        osc(glide(800, 100, 0.6), 0.6) * expdecay(0.6, 6) * 0.3, 0.8, 0.4)
    S["void_stalker_hurt"] = mixall(bandpass(noise(0.45), 1500, 6000) * env(0.45, 0.01, 0.3), osc(glide(700, 400, 0.45), 0.45, "saw") * expdecay(0.45, 6) * 0.3)
    S["void_stalker_death"] = reverb(mixall(osc(glide(900, 90, 1.3, 0.7), 1.3, "saw") * env(1.3, 0.02, 0.8) * 0.4,
                                            bandpass(noise(1.3), 1000, 5000) * env(1.3, 0.02, 1.0) * 0.5), 1.4, 0.45)

    S["golem_slam"] = reverb(mixall(thump(80, 28, 1.6, 3.5) * 1.8, lowpass(noise(1.6), 900) * expdecay(1.6, 5) * 1.4,
                                    bandpass(noise(1.6), 600, 3000) * expdecay(1.6, 2) * (rng.random(int(SR * 1.6)) > 0.97) * 2), 1.0, 0.3, 1800)
    S["golem_hurt"] = mixall(bandpass(noise(0.5), 300, 2000) * expdecay(0.5, 10) * 1.5, thump(120, 70, 0.5, 12), bell(1760, 0.4, 1.0) * 0.15)
    crumble = bandpass(noise(2.2), 200, 2500) * (rng.random(int(SR * 2.2)) > 0.9) * env(2.2, 0.1, 1.5) * 2
    S["golem_death"] = reverb(mixall(crumble, lowpass(noise(2.2), 200) * env(2.2, 0.2, 1.5) * 2, sumx(bell(f, 2.0, 0.5) for f in (880, 1108, 1318)) * 0.15), 1.5, 0.35)

    clack = bandpass(noise(0.12), 400, 3000) * expdecay(0.12, 40)
    S["mimic_chomp"] = mixall(clack * 1.5, np.concatenate([np.zeros(int(SR * 0.12)), clack * 1.2]), thump(180, 90, 0.35, 15) * 0.5)
    creak = bandpass(osc(glide(220, 160, 0.8) * (1 + 0.08 * np.sign(np.sin(2 * np.pi * 35 * t_axis(0.8)))), 0.8, "saw"), 300, 2500) * env(0.8, 0.05, 0.3)
    snarl = lowpass(osc(glide(110, 80, 0.6), 0.6, "saw") * (1 + 0.5 * rng.standard_normal(int(SR * 0.6))), 1200) * env(0.6, 0.02, 0.4)
    S["mimic_reveal"] = mixall(creak * 0.6, np.concatenate([np.zeros(int(SR * 0.6)), snarl * 0.8]))

    vib = 1 + 0.03 * np.sin(2 * np.pi * 5 * t_axis(2.4))
    moan = osc(glide(330, 260, 2.4) * vib, 2.4, "sine") * env(2.4, 0.6, 1.0)
    S["astral_wraith_ambient"] = reverb(mixall(moan * 0.5, osc(glide(495, 390, 2.4) * vib, 2.4) * env(2.4, 0.6, 1.0) * 0.2,
                                               bandpass(noise(2.4), 800, 2000) * env(2.4, 0.6, 1.0) * 0.15), 2.0, 0.55)
    S["astral_wraith_cast"] = reverb(mixall(sumx(bell(f, 0.8, 0.5) for f in (659, 830, 988)) * 0.3, sweep_filter(noise(0.8), 500, 4000, 0.4) * env(0.8, 0.2, 0.4) * 0.5), 1.2, 0.4)
    S["astral_wraith_death"] = reverb(osc(glide(600, 150, 1.6), 1.6) * env(1.6, 0.05, 1.2) * 0.6, 2.0, 0.6)

    chirp = np.zeros(int(SR * 0.6))
    for i, (f0, f1) in enumerate([(2400, 3600), (2800, 4200), (2600, 3300)]):
        c = osc(glide(f0, f1, 0.08), 0.08) * env(0.08, 0.005, 0.05)
        p = int(SR * i * 0.13)
        chirp[p:p + len(c)] += c
    S["starling_ambient"] = reverb(chirp, 0.6, 0.3)

    song = osc(glide(180, 420, 1.6, 1.4), 1.6) * env(1.6, 0.4, 0.5)
    song2 = osc(glide(420, 260, 1.8, 0.7), 1.8) * env(1.8, 0.2, 0.8)
    whale = np.concatenate([song, song2])
    whale = whale + 0.3 * np.concatenate([osc(glide(360, 840, 1.6, 1.4), 1.6), osc(glide(840, 520, 1.8, 0.7), 1.8)]) * np.concatenate([env(1.6, 0.4, 0.5), env(1.8, 0.2, 0.8)])
    S["nebula_ray_ambient"] = reverb(whale * 0.6, 2.5, 0.6, 3000)

    # --- The Sovereign ------------------------------------------------------------------------------------------
    def roar(dur, f0, f1, grit=0.6):
        base = osc(glide(f0, f1, dur, 0.8), dur, "saw") + osc(glide(f0 * 1.01, f1 * 1.01, dur, 0.8), dur, "saw")
        base *= 1 + grit * rng.standard_normal(int(SR * dur)) * 0.5
        voiced = bandpass(base, 150, 1400) + bandpass(base, 1800, 3200) * 0.3
        return voiced * env(dur, 0.15, dur * 0.5)
    S["boss_roar"] = reverb(mixall(roar(3.0, 70, 45) * 1.2, lowpass(noise(3.0), 200) * env(3.0, 0.3, 1.5) * 1.5), 2.0, 0.4, 2500)
    S["boss_hurt"] = reverb(roar(0.8, 110, 70, 0.8), 1.0, 0.3)
    charge = osc(glide(120, 1800, 1.9, 2.2), 1.9, "saw") * np.linspace(0.1, 1, int(SR * 1.9))
    S["boss_beam_charge"] = reverb(mixall(lowpass(charge, 3000) * 0.4, lowpass(noise(1.9), 250) * env(1.9, 1.0, 0.3) * 1.2), 1.0, 0.3)
    fm = np.sin(2 * np.pi * 220 * t_axis(1.6) + 4 * np.sin(2 * np.pi * 330 * t_axis(1.6)))
    buzz = (osc(110, 1.6, "saw") + osc(111.5, 1.6, "saw") + osc(165, 1.6, "saw")) * 0.3
    S["boss_beam_fire"] = reverb(mixall(fm * 0.4, lowpass(buzz, 2500), highpass(noise(1.6), 3000) * 0.15) * env(1.6, 0.03, 0.4), 0.9, 0.25)
    S["boss_slam"] = reverb(mixall(thump(70, 22, 2.2, 2.0) * 2.0, lowpass(noise(2.2), 1400) * expdecay(2.2, 3.0) * 1.5,
                                   bandpass(noise(2.2), 800, 4000) * expdecay(2.2, 1.5) * (rng.random(int(SR * 2.2)) > 0.96) * 2.5), 1.8, 0.35, 1500)
    death_roar = roar(3.0, 90, 30, 0.9)
    chord = sumx(np.concatenate([np.zeros(int(SR * 2.4)), bell(f, 3.4, 0.4) * 0.35]) for f in (261.6, 329.6, 392, 523.3, 659.3))
    S["boss_death"] = reverb(mixall(death_roar * 1.3, np.concatenate([np.zeros(int(SR * 2.3)), thump(60, 20, 2.5, 1.5) * 2]), chord,
                                    np.concatenate([np.zeros(int(SR * 2.4)), highpass(noise(3.4), 6000) * env(3.4, 0.1, 3) * 0.1])), 3.0, 0.45)
    portal = sweep_filter(noise(1.6), 150, 1500, 0.5) * env(1.6, 0.4, 0.8)
    S["boss_summon"] = reverb(mixall(portal * 1.2, lowpass(osc(55, 1.6, "saw"), 300) * env(1.6, 0.3, 0.8) * 0.8), 1.8, 0.45)
    shatter = mixall(bandpass(noise(1.2), 2000, 9000) * expdecay(1.2, 8) * (rng.random(int(SR * 1.2)) > 0.6) * 1.5,
                     *[np.concatenate([np.zeros(int(SR * rng.random() * 0.3)), bell(rng.uniform(2000, 4500), 0.8, 0.8) * 0.15]) for _ in range(8)])
    S["crystal_shatter"] = reverb(shatter, 1.2, 0.35)
    return S


def main():
    for name, data in sounds().items():
        write(name, data)
        print("wrote", name, round(len(data) / SR, 2), "s")


if __name__ == "__main__":
    main()
