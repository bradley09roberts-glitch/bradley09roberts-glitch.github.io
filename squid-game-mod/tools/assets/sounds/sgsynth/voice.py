"""sgsynth.voice - formant (vowel-filter) singing-voice model.

The voiced source is a glottal-pulse / sawtooth-like harmonic series (amplitude ~ 1/k with a
brightness corner, every harmonic of a time-varying f0, so vibrato / glides are exact).  This is
the spectrum of a band-limited sawtooth pulse train; instead of running it through resonator
filters sample by sample, each harmonic's amplitude *and phase* are taken from the complex transfer
function of a cascade of five formant resonators (time-varying F1..F5 + bandwidths, optional nasal
anti-formant), which is the same filtering done in the frequency domain.  Aspiration / frication noise is shaped by the same formant
magnitudes with the spectral-shaping engine.  Everything is computed at control rate and
interpolated, which keeps rendering fast.
"""
from __future__ import annotations

import numpy as np

from .core import SR, TAU, curve, ns, phase_cycles, smooth_noise, smoothstep, cents
from .dsp import apply_biquads, biquad_coefs, shape_spectrum

# ------------------------------------------------------------------ phoneme targets
# baseline formants (Hz) / bandwidths (Hz) for a light female voice; the doll scales them up.
# v = voicing level (dB re vowel), a = aspiration level (dB re vowel), nas = nasalisation 0..1,
# zf/zd = nasal anti-formant frequency / depth.
PHONES = {
    # vowels --------------------------------------------------------------------------
    "u": dict(F=[340, 820, 2650, 3550, 4650], BW=[75, 120, 200, 260, 320], v=0.0, a=-99, nas=0.0),
    "o": dict(F=[540, 960, 2680, 3550, 4650], BW=[85, 120, 200, 260, 320], v=0.0, a=-99, nas=0.0),
    "a": dict(F=[900, 1400, 2800, 3650, 4700], BW=[110, 140, 210, 270, 330], v=0.0, a=-99, nas=0.0),
    "i": dict(F=[330, 2850, 3400, 3950, 4850], BW=[75, 160, 230, 270, 330], v=0.0, a=-99, nas=0.0),
    "A": dict(F=[700, 1180, 2700, 3550, 4650], BW=[95, 130, 200, 260, 320], v=0.0, a=-99, nas=0.0),  # Korean eo
    "M": dict(F=[350, 1380, 2500, 3550, 4650], BW=[75, 140, 210, 260, 320], v=0.0, a=-99, nas=0.0),  # Korean eu
    # nasal murmurs ----------------------------------------------------------------------
    "m": dict(F=[280, 1250, 2300, 3300, 4400], BW=[70, 320, 380, 420, 450], v=-8.0, a=-99, nas=1.0, zf=1000, zd=0.85),
    "n": dict(F=[270, 1500, 2450, 3300, 4400], BW=[70, 300, 380, 420, 450], v=-8.0, a=-99, nas=1.0, zf=1750, zd=0.85),
    "ng": dict(F=[260, 1100, 2250, 3200, 4300], BW=[70, 300, 400, 420, 450], v=-9.0, a=-99, nas=1.0, zf=2500, zd=0.8),
    # consonant loci (voiced stop closures: formants as they leave the closure) --------------
    "d": dict(F=[300, 1750, 2950, 3700, 4700], BW=[90, 200, 260, 300, 340], v=-7.0, a=-99, nas=0.0),
    "g": dict(F=[300, 1550, 2300, 3500, 4600], BW=[90, 200, 260, 300, 340], v=-7.0, a=-99, nas=0.0),
    "tcl": dict(F=[300, 1700, 2900, 3700, 4700], BW=[90, 200, 260, 300, 340], v=-26.0, a=-99, nas=0.0),  # unreleased t
}


def phone(name: str, **over):
    p = dict(PHONES[name])
    p["F"] = list(p["F"])
    p["BW"] = list(p["BW"])
    p.update(over)
    return p


# ----------------------------------------------------------------------------- tracks
def build_tracks(segments, n: int, child: float = 1.10, nf: int = 5):
    """Turn [(phone_dict, t0, t1, transition_seconds_in), ...] into per-sample tracks.

    The first segment has no transition; every later segment glides in over ``tr`` seconds
    centred on its start time (cosine-smoothed), formants hold flat on the plateaus.
    """
    knots_t = []
    per = {k: [] for k in ("v", "a", "nas", "zf", "zd")}
    Fk = [[] for _ in range(nf)]
    Bk = [[] for _ in range(nf)]
    for i, seg in enumerate(segments):
        p, t0, t1 = seg[0], seg[1], seg[2]
        tr = seg[3] if len(seg) > 3 else 0.03
        lo = t0 + (tr / 2.0 if i > 0 else 0.0)
        nxt_tr = (segments[i + 1][3] if len(segments[i + 1]) > 3 else 0.03) if i + 1 < len(segments) else 0.0
        hi = t1 - nxt_tr / 2.0
        hi = max(hi, lo + 1e-4)
        for tk in (lo, hi):
            knots_t.append(tk)
            per["v"].append(p["v"])
            per["a"].append(p["a"])
            per["nas"].append(p["nas"])
            per["zf"].append(p.get("zf", 1500.0))
            per["zd"].append(p.get("zd", 0.0))
            for j in range(nf):
                Fk[j].append(p["F"][j] * child)
                Bk[j].append(p["BW"][j])
    ts = np.asarray(knots_t)

    def ev(vals, kind="cos"):
        return curve(list(zip(ts, vals)), n, kind)

    def lin(db):  # <= -59 dB means "off"; amplitudes cross-fade linearly (no level dip at hand-offs)
        return 0.0 if db <= -59.0 else 10.0 ** (db / 20.0)

    tracks = {
        "v_db": ev(per["v"]),
        "a_db": ev(per["a"]),
        "v_lin": ev([lin(x) for x in per["v"]]),
        "a_lin": ev([lin(x) for x in per["a"]]),
        "nas": ev(per["nas"]),
        "zf": ev(per["zf"]),
        "zd": ev(per["zd"]),
        "F": np.stack([ev(Fk[j]) for j in range(nf)]),
        "BW": np.stack([ev(Bk[j]) for j in range(nf)]),
    }
    return tracks


def pitch_track(n: int, note_hz: float, rng, *, vib_rate=5.6, vib_cents=14.0, vib_delay=0.09,
                vib_ramp=0.14, scoop_cents=-45.0, scoop_tau=0.035, tremor_cents=3.0,
                jitter_cents=5.0, vib_phase=0.0, glide=None, vib_rate_end=None, vib_cents_end=None):
    """f0(t) in Hz: note + scoop + onset-delayed vibrato + tremor + slow random drift (+ glide)."""
    t = np.arange(n) / SR
    rate = np.full(n, vib_rate) if vib_rate_end is None else np.linspace(vib_rate, vib_rate_end, n)
    depth = np.full(n, vib_cents) if vib_cents_end is None else np.linspace(vib_cents, vib_cents_end, n)
    vphase = TAU * np.cumsum(rate) / SR + vib_phase
    vib = depth * smoothstep((t - vib_delay) / vib_ramp) * np.sin(vphase)
    trem = tremor_cents * np.sin(TAU * 8.4 * t + vib_phase * 1.7 + 0.8)
    drift = jitter_cents * smooth_noise(n, rng, 7.0)
    scoop = scoop_cents * np.exp(-t / scoop_tau)
    total = vib + trem + drift + scoop
    if glide is not None:
        total = total + curve(glide, n, "cos")
    return note_hz * cents(total)


# ---------------------------------------------------------------- voiced harmonic synth
def formant_cascade(fk, F, BW):
    """Complex cascade response at frequencies fk (K, nc) for formant tracks F/BW (nf, nc)."""
    H = np.ones(fk.shape, dtype=np.complex128)
    for i in range(F.shape[0]):
        r = fk / F[i][None, :]
        Q = F[i][None, :] / BW[i][None, :]
        H = H / (1.0 - r * r + 1j * r / Q)
    return H


def harmonic_voice(f0, tracks, n: int, *, brightness_hz=2100.0, tilt=1.0, formant_scale=1.0,
                   hop: int = 32, phase0: float = 0.0, hp_shelf_db=2.0, rand_phase=None,
                   bw_scale=1.25, tune_f1=0.5, flutter=0.0, flutter_rng=None):
    """Unit-RMS voiced signal with formant-shaped harmonics (additive synthesis).

    ``rand_phase``: optional RNG - gives every harmonic a random constant phase offset (except
    the fundamental) so a doubled voice does not interleave glottal pulses with the first one.
    ``flutter``/``flutter_rng``: slow random wobble of all formant frequencies (fraction, ~9 Hz),
    micro-prosody that keeps held vowels alive.
    """
    idx = np.arange(0, n + hop, hop)
    idx = np.minimum(idx, n - 1)
    nc = len(idx)
    f0c = f0[idx]
    K = int(0.47 * SR / max(float(np.min(f0)), 50.0))
    k = np.arange(1, K + 1)[:, None]
    fk = k * f0c[None, :]
    F = tracks["F"][:, idx] * formant_scale
    if flutter > 0 and flutter_rng is not None:
        F = F * (1.0 + flutter * smooth_noise(n, flutter_rng, 9.0)[idx])[None, :]
    BW = tracks["BW"][:, idx].copy()
    BW[1:] *= bw_scale  # slightly broader upper formants: sweeter, less piercing
    if tune_f1 > 0:  # soprano-style vowel tuning: F1 climbs towards high fundamentals
        F[0] = F[0] + tune_f1 * np.maximum(0.0, 0.85 * f0c - F[0])
    H = formant_cascade(fk, F, BW)

    # nasal anti-formant (a dip around zf) and a stronger low-pass character
    nas = tracks["nas"][idx][None, :]
    zf = tracks["zf"][idx][None, :]
    zd = tracks["zd"][idx][None, :]
    dip = 1.0 - zd * np.exp(-0.5 * ((fk - zf) / (0.22 * zf + 40.0)) ** 2)
    nas_tilt = 1.0 / (1.0 + (fk / 1100.0) ** 2) ** (0.5 * nas)

    # glottal source tilt: ~ -6 dB/oct up to the brightness corner, steeper beyond
    src = 1.0 / (k ** tilt) / np.sqrt(1.0 + (fk / brightness_hz) ** 2)
    # a little presence boost for the "sweet but thin" child-doll sparkle
    shelf = 1.0 + (10 ** (hp_shelf_db / 20.0) - 1.0) / (1.0 + (2800.0 / np.maximum(fk, 1.0)) ** 3)
    A = np.abs(H) * src * dip * nas_tilt * shelf
    A *= np.clip((0.47 * SR - fk) / (0.06 * SR), 0.0, 1.0)
    ph = np.unwrap(np.angle(H), axis=1)
    # unit RMS at every control point -> vowels are loudness matched, nasals scaled later
    norm = np.sqrt(np.sum(A * A, axis=0) * 0.5) + 1e-12
    A = A / norm[None, :]

    pos = np.arange(n) / hop
    i0 = np.minimum(pos.astype(int), nc - 2)
    u = (pos - i0)[None, :]
    Af = A[:, i0] * (1.0 - u) + A[:, i0 + 1] * u
    Pf = ph[:, i0] * (1.0 - u) + ph[:, i0 + 1] * u
    phi = TAU * phase_cycles(f0, n, phase0)
    off = np.zeros((K, 1))
    if rand_phase is not None:
        off[1:, 0] = rand_phase.uniform(0.0, TAU, K - 1)
    out = np.zeros(n)
    # accumulate in chunks of harmonics to bound memory
    for a in range(0, K, 24):
        kk = k[a:a + 24]
        out += np.sum(Af[a:a + 24] * np.sin(kk * phi[None, :] + Pf[a:a + 24] + off[a:a + 24]), axis=0)
    return out


# --------------------------------------------------------------------- shaped noise
def formant_mag(freqs, F, BW):
    """|cascade| for frames: F, BW (J, nf) -> (J, bins)."""
    mag = np.ones((F.shape[0], len(freqs)))
    for i in range(F.shape[1]):
        r = freqs[None, :] / F[:, i:i + 1]
        Q = F[:, i:i + 1] / BW[:, i:i + 1]
        mag = mag / np.sqrt((1.0 - r * r) ** 2 + (r / Q) ** 2)
    return mag


def aspiration(n: int, rng, tracks, *, formant_scale=1.0, tilt_hz=4500.0):
    """Unit-RMS noise filtered by the current formants (breath / aspiration)."""
    x = rng.standard_normal(n)
    F = tracks["F"] * formant_scale
    BW = tracks["BW"] * 2.0  # breath resonances are broader than voiced ones

    def mag(centers, freqs):
        m = formant_mag(freqs, F[:, centers].T, BW[:, centers].T)
        m = m / np.sqrt(1.0 + (freqs[None, :] / tilt_hz) ** 2)
        m[:, freqs < 150.0] *= 0.1
        return m / (np.sqrt(np.mean(m * m, axis=1, keepdims=True)) + 1e-12)

    y = shape_spectrum(x, mag, size=512)
    return y / (np.sqrt(np.mean(y * y)) + 1e-12)


def noise_band(rng, dur: float, f_lo: float, f_hi: float, env=None, order: int = 2):
    """Band-limited noise burst with an amplitude envelope given as ``curve`` knots (or None)."""
    n = ns(dur)
    x = rng.standard_normal(n)
    if f_lo > 0 and f_hi > 0:
        cs = [biquad_coefs("hp", f_lo, 0.7071)] * order + [biquad_coefs("lp", f_hi, 0.7071)] * order
    elif f_lo > 0:
        cs = [biquad_coefs("hp", f_lo, 0.7071)] * order
    else:
        cs = [biquad_coefs("lp", f_hi, 0.7071)] * order
    y = apply_biquads(x, cs)
    y /= (np.sqrt(np.mean(y * y)) + 1e-12)
    if env is not None:
        y = y * curve(env, n, "lin")
    return y
