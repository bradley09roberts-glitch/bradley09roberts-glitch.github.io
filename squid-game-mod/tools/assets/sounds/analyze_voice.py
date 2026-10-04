#!/usr/bin/env python3
"""Formant sanity check for the doll voice.

1. Vowel diagnostic: every vowel of the model is synthesised at a fixed low f0 (no vibrato) and the
   spectral-envelope peaks are measured - they must sit on the model's F1/F2/F3 and differ per vowel.
2. Syllable check: for each rendered syllable (decoded from the committed .ogg files, or freshly
   rendered with --fresh) the LPC formants of the steady vowel part are listed next to the design
   values (high-pitched voices bias LPC towards harmonics, so this is a rough cross-check).

usage: python3 analyze_voice.py [--fresh] [--png OUTDIR]
"""
import argparse
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from sgsynth import voice as V                      # noqa: E402
from sgsynth.core import SR, ns, decode_ogg   # noqa: E402
from sgsynth import ev_doll                         # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
SOUNDS = os.path.join(REPO, "src", "main", "resources", "assets", "squidgame", "sounds")


def lpc_formants(x, order=14, fs=SR):
    x = x - np.mean(x)
    x = np.append(x[0], x[1:] - 0.97 * x[:-1]) * np.hanning(len(x))
    r = np.correlate(x, x, "full")[len(x) - 1:len(x) + order]
    R = np.array([[r[abs(i - j)] for j in range(order)] for i in range(order)])
    a = np.linalg.solve(R + 1e-9 * np.eye(order), r[1:order + 1])
    roots = np.roots(np.concatenate([[1.0], -a]))
    roots = roots[np.imag(roots) > 0.01]
    ang = np.angle(roots)
    fr = ang * fs / (2 * np.pi)
    bw = -np.log(np.abs(roots)) * fs / np.pi
    out = sorted((float(f), float(b)) for f, b in zip(fr, bw) if 150 < f < 5000 and b < 700)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fresh", action="store_true", help="re-render syllables instead of decoding the .ogg files")
    ap.add_argument("--png", default=None)
    args = ap.parse_args()

    print("== 1. vowel diagnostic at f0 = 140 Hz, LPC order 16 (model formants x CHILD=%.2f) ==" % ev_doll.CHILD)
    print("%-4s %-22s %-22s %s" % ("vow", "design F1/F2/F3", "measured F1/F2/F3", ""))
    f0 = 140.0
    n = ns(0.5)
    ok = True
    meas_all = {}
    for name in ("u", "o", "a", "i", "A", "M"):
        p = V.phone(name)
        tracks = V.build_tracks([(p, 0.0, 0.5)], n, child=ev_doll.CHILD)
        y = V.harmonic_voice(np.full(n, f0), tracks, n, formant_scale=1.0)[ns(0.1):ns(0.14)]
        fm = [f for f, b in lpc_formants(y, order=16)]
        des = [p["F"][i] * ev_doll.CHILD for i in range(3)]
        good = len(fm) >= 3 and all(abs(fm[i] - des[i]) < 0.10 * des[i] + 25 for i in range(2)) \
            and abs(fm[2] - des[2]) < 0.12 * des[2]
        ok &= good
        meas_all[name] = fm[:3]
        print("%-4s %-22s %-22s %s" % (name, "/".join("%4.0f" % d for d in des),
                                      "/".join("%4.0f" % m for m in fm[:3]), "ok" if good else "CHECK"))
    # vowels must be pairwise distinct in (F1, F2)
    names = list(meas_all)
    dmin = min(np.hypot(meas_all[a][0] - meas_all[b][0], 0.4 * (meas_all[a][1] - meas_all[b][1]))
               for i, a in enumerate(names) for b in names[i + 1:])
    print("minimum pairwise (F1, 0.4*F2) distance between vowels: %.0f Hz -> %s" % (dmin, "distinct" if dmin > 100 else "TOO CLOSE"))
    ok &= dmin > 100

    print("\n== 2. syllables (steady vowel, LPC order 14) ==")
    specs = ev_doll._syllables()
    if args.fresh:
        arrays = ev_doll.doll_syllables()
    else:
        arrays = [decode_ogg(os.path.join(SOUNDS, "doll", "syllable_%02d.ogg" % (i + 1))) for i in range(10)]
    centre_of = {"mu": 0.19, "gung": 0.13, "hwa": 0.22, "kko": 0.17, "chi": 0.20, "pi": 0.14, "eot": 0.08,
                 "seum": 0.19, "ni": 0.14, "da": 0.30}
    vow_of = {"mu": "u", "gung": "u", "hwa": "a", "kko": "o", "chi": "i", "pi": "i", "eot": "A", "seum": "M",
              "ni": "i", "da": "a"}
    print("%-5s %-3s %-5s %-24s %s" % ("syl", "vow", "f0", "design F1/F2/F3 (x%.2f)" % ev_doll.CHILD, "LPC formants (Hz)"))
    for s, x in zip(specs, arrays):
        t = s["text"]
        c = ns(centre_of[t])
        seg = x[max(c - ns(0.02), 0):c + ns(0.02)]
        p = V.phone(vow_of[t])
        des = [p["F"][i] * ev_doll.CHILD for i in range(3)]
        fm = lpc_formants(seg)
        print("%-5s %-3s %-5.0f %-24s %s" % (t, vow_of[t], 440.0 * 2 ** ((s["midi"] - 69) / 12),
                                            "/".join("%4.0f" % d for d in des),
                                            " ".join("%4.0f" % f for f, b in fm[:5])))

    if args.png:
        from sgsynth import viz
        os.makedirs(args.png, exist_ok=True)
        # long-term vowel spectra on one image: overlay by tiling
        items = []
        for name in ("u", "o", "a", "i", "A", "M"):
            p = V.phone(name)
            tracks = V.build_tracks([(p, 0.0, 0.5)], n, child=ev_doll.CHILD)
            y = V.harmonic_voice(np.full(n, f0), tracks, n)
            items.append(("vowel /%s/ at 140 Hz" % name, y / np.max(np.abs(y)) * 0.8))
        viz.render_grid(items, os.path.join(args.png, "vowels.png"), cols=3, fmax=6000, width=420,
                        spec_h=240, wave_h=40, nfft=2048, hop=256)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
