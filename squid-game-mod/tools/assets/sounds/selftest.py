#!/usr/bin/env python3
"""Quick numerical self-test of the sgsynth toolkit (a few seconds, writes no files).

  python3 selftest.py

Covers the DSP building blocks the generators rely on: filter accuracy and causality, the
time-varying filter engine, circular (loop-safe) processing, equal-power loop cross-fading,
loop wrap folding, wrap-around mixing, normalisation, reverb decay, formant-voice vowels.
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from sgsynth import dsp, voice  # noqa: E402
from sgsynth.core import (SR, add_at, colored, loop_wrap, loop_xfade, make_rng, normalize, ns, peak_db,  # noqa: E402
                          rms, saw, white)

FAILS = []


def check(name: str, ok: bool, detail: str = "") -> None:
    print(("ok    " if ok else "FAIL  ") + name + (f"  ({detail})" if detail else ""))
    if not ok:
        FAILS.append(name)


def gain_db(x, y, f0, frac=0.1):
    X = np.abs(np.fft.rfft(x))
    Y = np.abs(np.fft.rfft(y))
    f = np.fft.rfftfreq(len(x), 1 / SR)
    m = (f > f0 * (1 - frac)) & (f < f0 * (1 + frac))
    return 10 * math.log10(np.mean(Y[m] ** 2) / np.mean(X[m] ** 2))


def main() -> int:
    r = make_rng("selftest")
    x = white(SR * 2, r)

    for kind, fn in (("lowpass", dsp.lowpass), ("highpass", dsp.highpass)):
        g = gain_db(x, fn(x, 1000.0, order=2), 1000.0)
        check(f"{kind} is -3 dB at the cut-off", abs(g + 3.0) < 0.5, f"{g:.2f} dB")
    g4 = gain_db(x, dsp.lowpass(x, 1000.0, order=4), 4000.0)
    check("4th-order low-pass slope (~-48 dB at 2 octaves)", -52 < g4 < -42, f"{g4:.1f} dB")

    imp = np.zeros(8000)
    imp[4000] = 1.0
    y = dsp.bandpass(imp, 2000.0, 20.0)
    check("resonant filter is causal (no pre-ringing)", np.sum(y[:4000] ** 2) < 1e-12 and np.sum(y[4000:] ** 2) > 1e-4)

    ys = dsp.lowpass(x[:SR], 800.0, order=2)
    yt = dsp.tv_biquad(x[:SR], "lp", 800.0, 0.70710678)
    check("time-varying engine == static filter for constant parameters", np.max(np.abs(ys - yt)) < 1e-6,
          f"max diff {np.max(np.abs(ys - yt)):.1e}")

    n = ns(4.0)
    nz = colored(n, r, 1.0)
    cf = dsp.lowpass(nz, 300.0, order=2, circular=True)
    step = abs(cf[0] - cf[-1]) / np.percentile(np.abs(np.diff(cf)), 99)
    check("circular filtering keeps a loop seamless", step < 2.0, f"seam step x{step:.2f} of the largest sample step")
    rv = dsp.reverb(nz, rt60=1.5, wet=1.0, dry=0.0, circular=True)
    step = abs(rv[0] - rv[-1]) / np.percentile(np.abs(np.diff(rv)), 99)
    check("circular reverb keeps a loop seamless", step < 2.0, f"seam step x{step:.2f}")

    L, X = 2.0, 0.5
    src = white(ns(L + X), r)
    lp = loop_xfade(src, L, X)
    seg = ns(X)
    mid = rms(lp[: seg]) / rms(lp[seg: 2 * seg])
    check("loop_xfade is equal-power (no level bump in the cross-fade)", 0.9 < mid < 1.1, f"x{mid:.2f}")
    check("loop_xfade last sample flows into the first", abs(lp[0] - src[ns(L)]) < 1e-9 and len(lp) == ns(L))
    wr = loop_wrap(np.ones(ns(2.5)), 1.0)
    check("loop_wrap folds the tail onto the head (sum preserved)", abs(wr.sum() - ns(2.5)) < 1e-6 and len(wr) == ns(1.0))

    buf = np.zeros(1000)
    add_at(buf, np.ones(300), 900 / SR, wrap=True)
    check("add_at(wrap=True) wraps and conserves energy", abs(buf.sum() - 300) < 1e-9 and buf[:200].sum() == 200)

    z = normalize(saw(220.0, SR) * 0.123, -3.0)
    check("normalize hits the requested peak", abs(peak_db(z) + 3.0) < 0.01, f"{peak_db(z):.2f} dBFS")

    ir = dsp.reverb(np.concatenate([[1.0], np.zeros(SR * 2 - 1)]), rt60=1.0, wet=1.0, dry=0.0)
    e1 = rms(ir[int(SR * 0.05): int(SR * 0.15)])
    e2 = rms(ir[int(SR * 0.85): int(SR * 0.95)])
    drop = 20 * math.log10(e1 / e2)
    check("reverb decays ~60 dB per rt60 (rt60 = 1 s: >40 dB between 0.1 s and 0.9 s)", drop > 40, f"{drop:.0f} dB")

    # formant voice: LPC formants of a synthesised /a/ and /i/ at f0 = 140 Hz
    import analyze_voice as av
    ok = True
    for name, f1, f2 in (("a", 1026, 1596), ("i", 376, 3249), ("u", 388, 935)):
        p = voice.phone(name)
        tr = voice.build_tracks([(p, 0.0, 0.5)], ns(0.5), child=1.14)
        yv = voice.harmonic_voice(np.full(ns(0.5), 140.0), tr, ns(0.5))[ns(0.1): ns(0.14)]
        fm = [f for f, _ in av.lpc_formants(yv, order=16)]
        ok &= len(fm) >= 2 and abs(fm[0] - f1) < 0.12 * f1 + 25 and abs(fm[1] - f2) < 0.12 * f2 + 25
    check("formant voice reproduces the F1/F2 of /a/ /i/ /u/", ok)

    print("\nSELFTEST", "PASS" if not FAILS else f"FAIL ({len(FAILS)})")
    return 0 if not FAILS else 1


if __name__ == "__main__":
    sys.exit(main())
