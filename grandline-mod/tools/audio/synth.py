"""Small numpy synthesiser for the mod's original sound effects and music (no samples, everything is computed).

Building blocks: oscillators with pitch curves, additive instruments, Karplus-Strong plucked strings, drums, FFT
filters (static and time-varying), a convolution reverb with a synthetic room, and a bus mixer with a master chain.
Only numpy is needed; ffmpeg encodes the result (see gen_sounds.py).
"""
import math

import numpy as np

SR = 44100
TAU = 2 * np.pi


def secs(t):
    return max(1, int(round(t * SR)))


def tt(n):
    return np.arange(n) / SR


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


NOTE_IDX = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}


def note(name):
    """'C4' -> 60, 'F#5' -> 78, 'Bb3' -> 58."""
    i = NOTE_IDX[name[0].upper()]
    k = 1
    while k < len(name) and name[k] in '#b':
        i += 1 if name[k] == '#' else -1
        k += 1
    return i + 12 * (int(name[k:]) + 1)


# ---------------------------------------------------------------------------------------------------------- envelopes
def adsr(n_sustain, a=0.01, d=0.1, s=0.7, r=0.1):
    """Envelope for a note held n_sustain samples, followed by a release tail of r seconds."""
    na, nd, nr = secs(a), secs(d), secs(r)
    held = np.full(max(n_sustain, 1), s, dtype=float)
    held[:min(na, len(held))] = np.linspace(0, 1, na, endpoint=False)[:len(held)]
    if na < len(held):
        seg = held[na:na + nd]
        seg[:] = 1 + (s - 1) * (1 - np.exp(-np.arange(len(seg)) / max(1, nd / 3)))
    last = held[-1]
    tail = last * np.exp(-np.arange(nr) / max(1, nr / 5))
    return np.concatenate([held, tail])


def perc(n, a=0.002, tau=0.3):
    """Percussive envelope: fast attack, exponential decay with time constant tau (seconds)."""
    t = tt(n)
    e = np.exp(-t / tau)
    na = secs(a)
    e[:na] *= np.linspace(0, 1, na)
    return e


def fade(x, fin=0.0, fout=0.01):
    x = x.copy()
    a, b = secs(fin) if fin else 0, secs(fout) if fout else 0
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


# --------------------------------------------------------------------------------------------------------- oscillators
def phase(freq, n):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return TAU * np.cumsum(f) / SR


def sine(freq, n, ph0=0.0):
    return np.sin(phase(freq, n) + ph0)


def additive(freq, n, amps, ph0=None):
    """Sum of harmonics k*freq with amplitudes amps[k-1] (scalars or arrays of length n); harmonics above Nyquist are
    dropped per sample so pitch sweeps never alias."""
    ph = phase(freq, n)
    fv = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    out = np.zeros(n)
    for k, a in enumerate(amps, 1):
        if np.isscalar(a) and a == 0:
            continue
        lim = fv * k < SR * 0.45
        if not lim.any():
            break
        p0 = 0.0 if ph0 is None else ph0[k - 1]
        out += np.where(lim, a * np.sin(k * ph + p0), 0.0)
    return out


def vibrato(freq, n, rate=5.5, depth=0.004, delay=0.15, rng=None):
    """Frequency curve with a delayed vibrato (depth is relative, 0.004 ~ 7 cents)."""
    t = tt(n)
    ramp = np.clip((t - delay) / 0.25, 0, 1)
    ph = rng.uniform(0, TAU) if rng is not None else 0.0
    return freq * (1 + depth * ramp * np.sin(TAU * rate * t + ph))


def noise(n, rng, kind='white'):
    w = rng.standard_normal(n)
    if kind == 'white':
        return w
    spec = np.fft.rfft(w)
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1] if n > 1 else 1
    if kind == 'pink':
        spec /= np.sqrt(f)
    elif kind == 'brown':
        spec /= f
    out = np.fft.irfft(spec, n)
    return out / (np.abs(out).max() + 1e-9)


# ------------------------------------------------------------------------------------------------------------- filters
def _resp_lp(f, fc, order):
    return 1.0 / np.sqrt(1 + (f / fc) ** (2 * order))


def _resp_hp(f, fc, order):
    f = np.maximum(f, 1e-3)
    return 1.0 / np.sqrt(1 + (fc / f) ** (2 * order))


def ffilter(x, lp=None, hp=None, order=2, peaks=()):
    """Zero-phase FFT filter: optional low-pass, high-pass, and resonant peaks [(freq, gain_db, q)]."""
    n = len(x)
    if n < 4:
        return x
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    g = np.ones_like(f)
    if lp:
        g *= _resp_lp(f, lp, order)
    if hp:
        g *= _resp_hp(f, hp, order)
    for fc, db, q in peaks:
        bw = fc / q
        g *= 1 + (10 ** (db / 20) - 1) * np.exp(-0.5 * ((f - fc) / (bw / 2)) ** 2)
    return np.fft.irfft(spec * g, n)


def bandpass(x, lo, hi, order=2):
    return ffilter(x, lp=hi, hp=lo, order=order)


def stft_filter(x, gain_fn, nfft=1024, hop=256):
    """Time-varying filter: gain_fn(t_seconds, freqs) -> gain per bin, applied frame by frame (overlap-add)."""
    n = len(x)
    win = np.sqrt(np.hanning(nfft + 1)[:-1])
    pad = np.concatenate([np.zeros(nfft), x, np.zeros(nfft)])
    out = np.zeros_like(pad)
    norm = np.zeros_like(pad)
    freqs = np.fft.rfftfreq(nfft, 1 / SR)
    for start in range(0, len(pad) - nfft, hop):
        frame = pad[start:start + nfft] * win
        t = (start + nfft / 2 - nfft) / SR
        spec = np.fft.rfft(frame) * gain_fn(t, freqs)
        out[start:start + nfft] += np.fft.irfft(spec, nfft) * win
        norm[start:start + nfft] += win * win
    out = out / np.maximum(norm, 1e-6)
    return out[nfft:nfft + n]


def sweep_lp(x, fc_curve, order=2):
    """Low-pass whose cutoff follows fc_curve(t) (seconds -> Hz)."""
    return stft_filter(x, lambda t, f: _resp_lp(f, max(20.0, fc_curve(t)), order))


def sweep_bp(x, fc_curve, q=2.0):
    def g(t, f):
        fc = max(30.0, fc_curve(t))
        bw = fc / q
        return np.exp(-0.5 * ((f - fc) / (bw / 2)) ** 2)
    return stft_filter(x, g)


def drive(x, amount=2.0):
    return np.tanh(x * amount) / np.tanh(amount)


def pitch_mod(y, ratio):
    """Read y at a variable speed (ratio array, 1 = unchanged): pitch bends and glides for sampled-style voices."""
    pos = np.cumsum(ratio)
    pos = pos[pos < len(y) - 1]
    return np.interp(pos, np.arange(len(y)), y)


# ------------------------------------------------------------------------------------------------------- Karplus-Strong
def pluck(freq, dur, rng, bright=0.5, t60=1.5, body=0.0):
    """Plucked string: a burst of noise in a lossy delay line. bright 0..1 (pick hardness), t60 = decay time."""
    freq = float(freq)
    p = max(2, int(SR / freq - 0.5))
    f_real = SR / (p + 0.5)
    n_out = secs(dur)
    n_in = int(n_out * f_real / freq) + p * 2 + 4
    init = rng.uniform(-1, 1, p)
    # softer picks: smooth the burst
    soft = 1 - bright
    for _ in range(int(soft * 6)):
        init = 0.5 * (init + np.roll(init, 1))
    init -= init.mean()
    g = 0.001 ** (1.0 / max(0.05, t60 * f_real))
    blocks = [init]
    prev, pp_last = init, 0.0
    total = p
    while total < n_in:
        shifted = np.concatenate(([pp_last], prev[:-1]))
        new = g * 0.5 * (prev + shifted)
        pp_last = prev[-1]
        blocks.append(new)
        prev = new
        total += p
    y = np.concatenate(blocks)
    # pitch correction: the integer delay line rings slightly sharp; resample down to the exact pitch
    pos = np.arange(n_out) * (freq / f_real)
    y = np.interp(pos, np.arange(len(y)), y)
    if body:
        y = y + body * ffilter(y, peaks=((180, 6, 2), (2600, 3, 2)))
    return y


# ----------------------------------------------------------------------------------------------------------- instruments
def inst_accordion(f, dur, vel, rng):
    n = secs(dur)
    env = adsr(n, a=0.03, d=0.15, s=0.85, r=0.07)
    m = len(env)
    out = np.zeros(m)
    for det in (-0.0018, 0.0018):
        fv = np.full(m, f * (1 + det))
        amps = []
        for k in range(1, 28):
            a = (1.0 / k) * (1 if k % 2 else 0.55)
            a *= 1.0 / (1 + (k * f / 2600) ** 2)
            amps.append(a)
        out += additive(fv, m, amps)
    swell = 1 + 0.05 * np.sin(TAU * 0.7 * tt(m) + rng.uniform(0, TAU))
    return out * env * swell * vel * 0.5


def inst_fiddle(f, dur, vel, rng):
    n = secs(dur)
    env = adsr(n, a=0.07, d=0.2, s=0.8, r=0.12)
    m = len(env)
    fv = vibrato(f, m, rate=5.6, depth=0.0045, delay=0.18, rng=rng)
    amps = [(1.0 / k) / (1 + (k * f / 3800) ** 2) for k in range(1, 40)]
    y = additive(fv, m, amps)
    bow = bandpass(rng.standard_normal(m), 1800, 6000) * 0.04
    return (y + bow) * env * vel * 0.6


def inst_flute(f, dur, vel, rng, breath=0.08, scoop=0.0):
    n = secs(dur)
    env = adsr(n, a=0.06, d=0.2, s=0.85, r=0.1)
    m = len(env)
    fv = vibrato(f, m, rate=5.0, depth=0.0035, delay=0.2, rng=rng)
    if scoop:
        fv = fv * (1 - scoop * np.exp(-tt(m) / 0.06))
    y = additive(fv, m, [1, 0.32, 0.1, 0.05, 0.02])
    br = bandpass(rng.standard_normal(m), f * 1.2, f * 5) * breath * (0.5 + 0.5 * np.exp(-tt(m) / 0.15))
    return (y + br) * env * vel * 0.7


def inst_brass(f, dur, vel, rng):
    n = secs(dur)
    env = adsr(n, a=0.05, d=0.25, s=0.75, r=0.15)
    m = len(env)
    t = tt(m)
    bright = 2.5 + 7 * (1 - np.exp(-t / 0.06)) * np.exp(-t / 0.6) + 3.5 * vel
    fv = f * (1 - 0.012 * np.exp(-t / 0.04))
    fv = vibrato(fv, m, rate=5.2, depth=0.002, delay=0.3, rng=rng)
    amps = [np.exp(-(k - 1) / bright) / k ** 0.3 for k in range(1, 30)]
    return additive(fv, m, amps) * env * vel * 0.32


def inst_strings(f, dur, vel, rng, attack=0.22):
    n = secs(dur)
    env = adsr(n, a=attack, d=0.3, s=0.9, r=0.35)
    m = len(env)
    out = np.zeros(m)
    amps = [(1.0 / k) / (1 + (k * f / 3200) ** 2) for k in range(1, 30)]
    for det in (-0.004, 0.0, 0.0045):
        fv = vibrato(f * (1 + det), m, rate=5.0 + rng.uniform(-0.4, 0.4), depth=0.003, delay=0.1, rng=rng)
        out += additive(fv, m, amps)
    return out * env * vel * 0.28


def inst_pad(f, dur, vel, rng):
    n = secs(dur)
    env = adsr(n, a=0.7, d=0.5, s=0.9, r=1.2)
    m = len(env)
    out = np.zeros(m)
    for det in (-0.006, 0.0, 0.006):
        fv = f * (1 + det) * (1 + 0.002 * np.sin(TAU * 0.3 * tt(m) + rng.uniform(0, TAU)))
        out += additive(fv, m, [1, 0.45, 0.2, 0.1, 0.05])
    return out * env * vel * 0.3


def inst_choir(f, dur, vel, rng, vowel='ah'):
    n = secs(dur)
    env = adsr(n, a=0.35, d=0.3, s=0.9, r=0.5)
    m = len(env)
    form = {'ah': ((800, 80), (1150, 90), (2900, 120)), 'oh': ((450, 70), (800, 80), (2830, 100)),
            'oo': ((325, 50), (700, 60), (2530, 100))}[vowel]
    out = np.zeros(m)
    for det in (-0.005, 0.0, 0.004, 0.009):
        amps = []
        for k in range(1, 40):
            fk = k * f
            a = sum(np.exp(-0.5 * ((fk - fc) / (bw * 2.5)) ** 2) * w for (fc, bw), w in zip(form, (1, 0.6, 0.25)))
            amps.append(a / k ** 0.5 + 0.02 / k)
        fv = vibrato(f * (1 + det), m, rate=5.2 + rng.uniform(-0.5, 0.5), depth=0.005, delay=0.2, rng=rng)
        out += additive(fv, m, amps)
    return out * env * vel * 0.3


def inst_bass(f, dur, vel, rng):
    n = secs(dur)
    m = n + secs(0.12)
    t = tt(m)
    env = np.exp(-t / 0.9) * np.clip(t / 0.006, 0, 1)
    env[n:] *= np.exp(-(t[n:] - t[n]) / 0.03)
    amps = [1, 0.55 * np.exp(-t / 0.4), 0.3 * np.exp(-t / 0.25), 0.15 * np.exp(-t / 0.15), 0.08 * np.exp(-t / 0.1)]
    y = additive(f, m, amps)
    thump = bandpass(rng.standard_normal(m), 60, 400) * np.exp(-t / 0.01) * 0.3
    return (y + thump) * env * vel * 0.8


def inst_synth_bass(f, dur, vel, rng):
    n = secs(dur)
    env = adsr(n, a=0.005, d=0.15, s=0.7, r=0.06)
    m = len(env)
    t = tt(m)
    bright = 3 + 8 * np.exp(-t / 0.08)
    amps = [np.exp(-(k - 1) / bright) / k for k in range(1, 20)]
    return additive(f, m, amps) * env * vel * 0.7


def inst_pluck(f, dur, vel, rng, bright=0.55, t60=1.6, body=0.6):
    y = pluck(f, dur + 0.4, rng, bright=bright, t60=t60, body=body)
    n = secs(dur)
    y[n:] *= np.exp(-np.arange(len(y) - n) / secs(0.08))
    return y * vel * 0.8


def inst_harp(f, dur, vel, rng):
    return pluck(f, max(dur, 1.2) + 0.8, rng, bright=0.35, t60=2.8, body=0.2) * vel * 0.8


def inst_koto(f, dur, vel, rng, bend=0.0):
    y = pluck(f, max(dur, 0.8) + 0.6, rng, bright=0.8, t60=1.9, body=0.4)
    if bend:
        m = len(y)
        t = tt(m)
        ratio = 1 + bend * np.clip((t - 0.12) / 0.1, 0, 1) + 0.004 * np.sin(TAU * 6 * t) * np.clip((t - 0.3) / 0.2, 0, 1)
        y = pitch_mod(y, ratio)
    return y * vel * 0.8


def inst_shamisen(f, dur, vel, rng):
    y = pluck(f, dur + 0.3, rng, bright=0.95, t60=0.7, body=0.0)
    y = y + 0.3 * drive(ffilter(y, hp=900), 3)  # the buzzing sawari
    return y * vel * 0.6


def inst_marimba(f, dur, vel, rng):
    m = secs(max(dur, 0.5) + 0.6)
    t = tt(m)
    tau = 0.25 + 0.9 * np.exp(-f / 300)
    y = np.sin(TAU * f * t) * np.exp(-t / tau)
    y += 0.25 * np.sin(TAU * f * 3.93 * t) * np.exp(-t / (tau * 0.25))
    y += 0.08 * np.sin(TAU * f * 9.2 * t) * np.exp(-t / (tau * 0.1))
    click = bandpass(rng.standard_normal(m), 800, 5000) * np.exp(-t / 0.004) * 0.15
    y = (y + click) * np.clip(t / 0.002, 0, 1)
    return y * vel * 0.8


def inst_bell(f, dur, vel, rng, ratio=3.5, index=2.5, tau=1.6):
    m = secs(max(dur, tau * 1.5) + 0.5)
    t = tt(m)
    mod = index * np.exp(-t / (tau * 0.35)) * np.sin(TAU * f * ratio * t)
    y = np.sin(TAU * f * t + mod) * np.exp(-t / tau)
    y += 0.3 * np.sin(TAU * f * 2.01 * t) * np.exp(-t / (tau * 0.5))
    return y * np.clip(t / 0.002, 0, 1) * vel * 0.5


def inst_celesta(f, dur, vel, rng):
    return inst_bell(f, dur, vel, rng, ratio=4.0, index=1.2, tau=0.9)


def inst_whistle(f, dur, vel, rng):
    return inst_flute(f, dur, vel, rng, breath=0.03)


def inst_shakuhachi(f, dur, vel, rng):
    return inst_flute(f, dur, vel, rng, breath=0.22, scoop=0.03)


INSTRUMENTS = {
    'accordion': inst_accordion, 'fiddle': inst_fiddle, 'flute': inst_flute, 'brass': inst_brass,
    'strings': inst_strings, 'pad': inst_pad, 'choir': inst_choir, 'bass': inst_bass, 'synth_bass': inst_synth_bass,
    'guitar': inst_pluck, 'harp': inst_harp, 'koto': inst_koto, 'shamisen': inst_shamisen, 'marimba': inst_marimba,
    'bell': inst_bell, 'celesta': inst_celesta, 'whistle': inst_whistle, 'shakuhachi': inst_shakuhachi,
    'ukulele': lambda f, d, v, r: inst_pluck(f, d, v, r, bright=0.75, t60=0.9, body=0.3),
    'strings_short': lambda f, d, v, r: inst_strings(f, d, v, r, attack=0.02),
    'choir_oo': lambda f, d, v, r: inst_choir(f, d, v, r, vowel='oo'),
}


# ---------------------------------------------------------------------------------------------------------------- drums
def drum(kind, vel, rng):
    if kind == 'kick':
        m = secs(0.45)
        t = tt(m)
        f = 45 + 85 * np.exp(-t / 0.03)
        y = np.sin(phase(f, m)) * np.exp(-t / 0.18)
        y += bandpass(rng.standard_normal(m), 1500, 6000) * np.exp(-t / 0.003) * 0.3
        return drive(y, 1.5) * vel
    if kind == 'snare':
        m = secs(0.3)
        t = tt(m)
        tone = (np.sin(TAU * 185 * t) + 0.5 * np.sin(TAU * 330 * t)) * np.exp(-t / 0.06)
        nz = bandpass(rng.standard_normal(m), 1200, 9000) * np.exp(-t / 0.11)
        return (0.5 * tone + 0.8 * nz) * vel * 0.8
    if kind == 'brush':
        m = secs(0.25)
        t = tt(m)
        nz = bandpass(rng.standard_normal(m), 2000, 9000) * np.exp(-t / 0.08) * np.clip(t / 0.015, 0, 1)
        return nz * vel * 0.5
    if kind in ('hat', 'ohat'):
        m = secs(0.3 if kind == 'ohat' else 0.08)
        t = tt(m)
        nz = ffilter(rng.standard_normal(m), hp=7000, order=3)
        return nz * np.exp(-t / (0.12 if kind == 'ohat' else 0.022)) * vel * 0.35
    if kind == 'shaker':
        m = secs(0.12)
        t = tt(m)
        nz = bandpass(rng.standard_normal(m), 4500, 11000)
        return nz * np.clip(t / 0.02, 0, 1) * np.exp(-t / 0.035) * vel * 0.35
    if kind == 'tamb':
        m = secs(0.25)
        t = tt(m)
        y = ffilter(rng.standard_normal(m), hp=6000) * np.exp(-t / 0.05) * 0.4
        for fr in (5400, 6900, 8300, 9100):
            y += 0.08 * np.sin(TAU * fr * t + rng.uniform(0, TAU)) * np.exp(-t / 0.12)
        return y * vel * 0.5
    if kind in ('crash', 'cymbal_swell'):
        m = secs(2.4)
        t = tt(m)
        y = ffilter(rng.standard_normal(m), hp=3500, order=2) * 0.6
        for fr in rng.uniform(3000, 9000, 14):
            y += 0.05 * np.sin(TAU * fr * t + rng.uniform(0, TAU))
        if kind == 'crash':
            y *= np.exp(-t / 0.8) * np.clip(t / 0.001, 0, 1)
        else:
            y *= np.clip(t / 2.2, 0, 1) ** 2.5
        return y * vel * 0.45
    if kind in ('tom', 'tom_lo'):
        m = secs(0.6)
        t = tt(m)
        base = 120 if kind == 'tom' else 85
        f = base * (1 + 0.4 * np.exp(-t / 0.04))
        y = np.sin(phase(f, m)) * np.exp(-t / 0.25)
        y += bandpass(rng.standard_normal(m), 200, 2000) * np.exp(-t / 0.02) * 0.2
        return y * vel * 0.9
    if kind == 'timpani':
        m = secs(2.2)
        t = tt(m)
        f = 73.4 * (1 + 0.03 * np.exp(-t / 0.05))
        y = np.zeros(m)
        for r, a, tau in ((1, 1, 0.9), (1.5, 0.5, 0.6), (1.98, 0.35, 0.45), (2.44, 0.2, 0.3)):
            y += a * np.sin(phase(f * r, m)) * np.exp(-t / tau)
        y += ffilter(rng.standard_normal(m), lp=600) * np.exp(-t / 0.03) * 0.6
        return y * vel * 0.8
    if kind == 'taiko':
        m = secs(1.1)
        t = tt(m)
        f = 52 + 28 * np.exp(-t / 0.05)
        y = np.sin(phase(f, m)) * np.exp(-t / 0.35)
        y += ffilter(rng.standard_normal(m), lp=900) * np.exp(-t / 0.04) * 0.5
        return drive(y, 1.8) * vel
    if kind == 'kotsuzumi':  # the small shoulder drum's "pon"
        m = secs(0.4)
        t = tt(m)
        f = 420 * (1 - 0.25 * (1 - np.exp(-t / 0.05)))
        y = np.sin(phase(f, m)) * np.exp(-t / 0.12)
        return y * vel * 0.5
    if kind == 'bodhran':
        m = secs(0.4)
        t = tt(m)
        f = 70 + 30 * np.exp(-t / 0.03)
        y = np.sin(phase(f, m)) * np.exp(-t / 0.16)
        y += ffilter(rng.standard_normal(m), lp=1200, hp=150) * np.exp(-t / 0.025) * 0.35
        return y * vel * 0.9
    if kind == 'clave':
        m = secs(0.15)
        t = tt(m)
        return np.sin(TAU * 2500 * t) * np.exp(-t / 0.025) * vel * 0.3
    raise ValueError(kind)


# --------------------------------------------------------------------------------------------------------------- reverb
def make_ir(rng, seconds=2.0, damp=0.5, predelay=0.015):
    n = secs(seconds)
    t = tt(n)
    chans = []
    for _ in range(2):
        w = rng.standard_normal(n)
        lo = ffilter(w, lp=500)
        mid = ffilter(w, hp=500, lp=4000)
        hi = ffilter(w, hp=4000)
        ir = (lo * np.exp(-6.9 * t / seconds) + mid * np.exp(-6.9 * t / (seconds * (1 - 0.35 * damp)))
              + hi * np.exp(-6.9 * t / (seconds * (1 - 0.75 * damp))) * 0.6)
        ir *= np.clip(t / 0.02, 0, 1)
        er = np.zeros(n)
        for d in rng.uniform(0.004, 0.06, 9):
            er[secs(d)] += rng.uniform(-0.6, 0.6)
        ir = np.concatenate([np.zeros(secs(predelay)), ir + er * 3])
        chans.append(ir / np.sqrt(np.sum(ir ** 2)))
    return chans


def convolve(x, ir):
    n = len(x) + len(ir) - 1
    size = 1 << (n - 1).bit_length()
    y = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:n]
    return y


# ------------------------------------------------------------------------------------------------------------- the mix
class Mix:
    """Buses of mono audio placed in time; rendered to stereo with pan, EQ, reverb sends and a master chain."""

    def __init__(self, length, seed=1):
        self.n = secs(length)
        self.buses = {}
        self.rng = np.random.default_rng(seed)

    def bus(self, name, pan=0.0, rev=0.2, gain=1.0, lp=None, hp=None, peaks=(), width=0.0):
        self.buses[name] = dict(buf=np.zeros(self.n + secs(4)), pan=pan, rev=rev, gain=gain, lp=lp, hp=hp, peaks=peaks,
                                width=width)

    def add(self, name, y, at):
        b = self.buses[name]['buf']
        i = int(round(at * SR))
        if i >= len(b):
            return
        y = y[:len(b) - i]
        b[i:i + len(y)] += y

    def render(self, rev_seconds=2.0, rev_damp=0.5, rev_level=1.0):
        n = self.n + secs(4)
        dry_l, dry_r = np.zeros(n), np.zeros(n)
        send = np.zeros(n)
        for name, b in self.buses.items():
            y = b['buf'] * b['gain']
            if not np.any(y):
                continue
            if b['lp'] or b['hp'] or b['peaks']:
                y = ffilter(y, lp=b['lp'], hp=b['hp'], peaks=b['peaks'])
            ang = (b['pan'] + 1) * np.pi / 4
            l, r = np.cos(ang) * y, np.sin(ang) * y
            if b['width']:
                d = secs(0.011)
                r = np.concatenate([np.zeros(d), r[:-d]]) * (1 - b['width'] * 0.3) + r * b['width'] * 0.3
            dry_l += l
            dry_r += r
            send += y * b['rev']
        irl, irr = make_ir(self.rng, rev_seconds, rev_damp)
        wl = convolve(send, irl)[:n] * rev_level
        wr = convolve(send, irr)[:n] * rev_level
        return np.stack([dry_l + wl, dry_r + wr])


def master(st, target_rms_db=-20.0, ceiling=0.93):
    st = np.stack([ffilter(c, hp=28, order=2) for c in st])
    mono = st.mean(axis=0)
    # slow glue compression: 2:1 above the running loudness
    win = secs(0.08)
    pw = np.convolve(mono ** 2, np.ones(win) / win, mode='same')
    lvl = 10 * np.log10(pw + 1e-12)
    rms_db = 10 * np.log10(np.mean(mono ** 2) + 1e-12)
    over = np.maximum(0, lvl - (rms_db + 4))
    gr = 10 ** (-over * 0.5 / 20)
    sm = secs(0.05)
    gr = np.convolve(gr, np.ones(sm) / sm, mode='same')
    st = st * gr
    rms = 10 * np.log10(np.mean(st.mean(axis=0) ** 2) + 1e-12)
    st = st * 10 ** ((target_rms_db - rms) / 20)
    peak = np.abs(st).max()
    if peak > ceiling:  # soft limit only the peaks above the ceiling
        k = ceiling * 0.8
        a = np.abs(st)
        st = np.where(a > k, np.sign(st) * (k + (ceiling - k) * np.tanh((a - k) / (ceiling - k))), st)
    return st


def normalize(y, peak=0.89):
    m = np.abs(y).max()
    return y * (peak / m) if m > 0 else y
