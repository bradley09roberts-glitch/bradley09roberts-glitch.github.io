#!/usr/bin/env python3
"""Synthesise every sound of the Squid Game mod (original, procedural, numpy only) and write

  src/main/resources/assets/squidgame/sounds/<group>/<name>.ogg   (mono, 44.1 kHz, Vorbis -q:a 4)
  src/main/resources/assets/squidgame/sounds.json                  (event id -> files, subtitle keys)
  tools/assets/lang/sounds.json                                    (subtitles.squidgame.<id> -> English)

Event ids come from section 3 of docs/ASSET_CONTRACT.md (see sgsynth/contract.py).  Every event
is one registered function in sgsynth/ev_*.py; each variant has its own fixed seed so repeated
runs give byte-identical files (ffmpeg is called with bitexact flags).

usage:
  python3 gen_sounds.py                      # render everything (parallel)
  python3 gen_sounds.py --only 'doll.*'      # glob on event ids (sounds.json is always complete)
  python3 gen_sounds.py --preview DIR        # also write waveform+spectrogram PNGs
  python3 gen_sounds.py --jobs 1 --list
Requires: python3.11, numpy, Pillow (only for --preview), ffmpeg with libvorbis.
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import fnmatch
import importlib
import json
import os
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

from sgsynth import registry  # noqa: E402
from sgsynth.contract import CONTRACT, ORDER  # noqa: E402
from sgsynth.core import SR, encode_ogg, make_rng, peak_db, write_wav  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
RES = os.path.join(REPO, "src", "main", "resources", "assets", "squidgame")
OUT_SOUNDS = os.path.join(RES, "sounds")
OUT_JSON = os.path.join(RES, "sounds.json")
OUT_LANG = os.path.join(REPO, "tools", "assets", "lang", "sounds.json")

MODULES = ["ev_doll", "ev_ui", "ev_game", "ev_world", "ev_ambient", "ev_music"]


def load_modules():
    for m in MODULES:
        try:
            importlib.import_module(f"sgsynth.{m}")
        except ModuleNotFoundError as e:  # a module that has not been written yet
            if e.name != f"sgsynth.{m}":
                raise
            print(f"[warn] module {m} not found - skipped", file=sys.stderr)


def unit_files(unit):
    return [(ev, f) for ev in unit.events for f in ev.files]


def render_unit(name: str, tmpdir: str, preview: str | None):
    """Render one unit (all its files).  Runs inside worker processes."""
    load_modules()
    unit = next(u for u in registry.UNITS if u.name == name)
    t0 = time.time()
    if unit.grouped:
        arrays = unit.fn()
    else:
        ev = unit.events[0]
        arrays = [unit.fn(vi, make_rng(ev.id, vi)) for vi in range(unit.variants)]
    files = [f for _, f in unit_files(unit)]
    assert len(arrays) == len(files), f"{name}: {len(arrays)} arrays for {len(files)} files"
    report = []
    for f, x in zip(files, arrays):
        x = np.asarray(x, dtype=np.float64)
        if x.ndim != 1 or not np.all(np.isfinite(x)):
            raise ValueError(f"{f}: bad samples")
        if np.max(np.abs(x)) > 1.0:
            raise ValueError(f"{f}: clipping before encode ({np.max(np.abs(x)):.3f})")
        wav = os.path.join(tmpdir, f.replace("/", "__") + ".wav")
        ogg = os.path.join(OUT_SOUNDS, f + ".ogg")
        write_wav(wav, x)
        encode_ogg(wav, ogg, 4)
        os.remove(wav)
        if preview:
            from sgsynth import viz
            os.makedirs(preview, exist_ok=True)
            viz.render(x, os.path.join(preview, f.replace("/", "__") + ".png"), title=f,
                       fmax=16000 if len(x) / SR < 3 else 12000, width=1000 if len(x) / SR < 3 else 1400,
                       nfft=1024 if len(x) / SR < 3 else 2048, hop=128 if len(x) / SR < 3 else 1024)
        report.append((f, len(x) / SR, peak_db(x), os.path.getsize(ogg)))
    return name, time.time() - t0, report


def build_sounds_json(units):
    ev_by_id = {}
    for u in units:
        for ev in u.events:
            ev_by_id[ev.id] = ev
    out = {}
    lang = {}
    for eid in ORDER:
        ev = ev_by_id.get(eid)
        if ev is None:
            print(f"[warn] no generator registered for contract event {eid}", file=sys.stderr)
            continue
        key = f"subtitles.squidgame.{eid}"
        entries = []
        for f in ev.files:
            if ev.stream:
                entries.append({"name": f"squidgame:{f}", "stream": True})
            else:
                entries.append(f"squidgame:{f}")
        out[eid] = {"subtitle": key, "sounds": entries}
        lang[key] = ev.subtitle
    return out, lang


def dump_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--only", action="append", help="glob(s) on event ids to (re)render")
    ap.add_argument("--jobs", type=int, default=min(4, os.cpu_count() or 1))
    ap.add_argument("--preview", default=None, help="directory for waveform+spectrogram PNGs")
    ap.add_argument("--list", action="store_true", help="list registered events and exit")
    args = ap.parse_args()

    load_modules()
    units = list(registry.UNITS)
    missing = [e for e in ORDER if not any(e == ev.id for u in units for ev in u.events)]
    if args.list:
        for u in units:
            for ev in u.events:
                print(f"{ev.id:24s} -> {', '.join(ev.files)}{'  [stream]' if ev.stream else ''}")
        return 0
    if missing:
        print("[warn] contract events without a generator:", ", ".join(missing), file=sys.stderr)

    todo = units
    if args.only:
        todo = [u for u in units if any(fnmatch.fnmatch(ev.id, g) for g in args.only for ev in u.events)]
    names = [u.name for u in todo]
    print(f"rendering {len(names)} unit(s) with {args.jobs} job(s) ...")
    t0 = time.time()
    total = 0
    with tempfile.TemporaryDirectory(prefix="sgsounds_") as tmp:
        if args.jobs <= 1:
            results = [render_unit(n, tmp, args.preview) for n in names]
        else:
            with cf.ProcessPoolExecutor(max_workers=args.jobs) as ex:
                futs = [ex.submit(render_unit, n, tmp, args.preview) for n in names]
                results = [f.result() for f in futs]
    for name, secs, report in results:
        for f, dur, pk, size in report:
            total += size
            print(f"  {f:34s} {dur:7.3f}s  peak {pk:6.1f} dBFS  {size / 1024:7.1f} KiB")
    print(f"rendered in {time.time() - t0:.1f}s; encoded {total / 1048576:.2f} MiB")

    sounds, lang = build_sounds_json(units)
    dump_json(OUT_JSON, sounds)
    dump_json(OUT_LANG, lang)
    print(f"wrote {os.path.relpath(OUT_JSON, REPO)} ({len(sounds)} events) and {os.path.relpath(OUT_LANG, REPO)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
