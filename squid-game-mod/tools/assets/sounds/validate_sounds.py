#!/usr/bin/env python3
"""Validate the generated sound set against section 3 of docs/ASSET_CONTRACT.md.

Checks (exit status 1 if any FAIL):
  * every contract event id exists in sounds.json, with a non-empty ``sounds`` list whose entries
    point to existing ``sounds/<path>.ogg`` files, and carries the ``subtitles.squidgame.<id>`` key
    (also present in tools/assets/lang/sounds.json with non-empty text)
  * the event ids are cross-checked against the table parsed from the contract markdown itself
  * every file is Vorbis, mono, 44.1 kHz; duration (ffprobe) within +-35 % of the contract duration
  * peak level (decoded with ffmpeg) between -12 and -0.5 dBFS
  * loops (ambient.*, music.*): ``stream: true``, matching head/tail loudness and no click at the wrap
    point (jump between last and first sample compared with the signal's own largest sample steps)
  * doll syllables: no leading silence (onset within 8 ms) and loudness matched across the ten files
    (perceptual level = 70/30 blend of K- and A-weighted active levels, see sgsynth/loudness.py)
  * total audio size below 25 MB; sounds.json entries outside the contract / orphan .ogg files -> WARN

usage: python3 validate_sounds.py [--quiet] [--repo PATH]
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import json
import os
import re
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from sgsynth.contract import CONTRACT, ORDER, TOLERANCE  # noqa: E402
from sgsynth.core import SR  # noqa: E402
from sgsynth.loudness import A_WEIGHT_SHARE, loudness_db  # noqa: E402

DEFAULT_REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
PEAK_MIN, PEAK_MAX = -12.0, -0.5
SIZE_LIMIT = 25 * 1024 * 1024
HEADTAIL_TOL_DB = 3.0          # loop head vs tail loudness
JUMP_LIMIT = 2.5               # loop wrap jump / largest typical sample step
ONSET_LIMIT_S = 0.008          # syllables start within 8 ms
SYLLABLE_SPREAD_DB = 1.0       # spread of the perceptual level across the ten syllables
SYLLABLE_PLAIN_K_SPREAD_DB = 3.0    # spread allowed under plain RMS / K-weighting alone
SYLLABLE_A_SPREAD_DB = 5.5          # ... and under A-weighting alone


def db(x: float) -> float:
    return 20.0 * np.log10(max(float(x), 1e-12))


def probe(path: str) -> dict:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries",
         "stream=codec_name,channels,sample_rate:format=duration", "-of", "json", path],
        capture_output=True, text=True, check=True).stdout
    j = json.loads(out)
    st = j["streams"][0]
    return {"codec": st["codec_name"], "channels": int(st["channels"]), "rate": int(st["sample_rate"]),
            "duration": float(j["format"]["duration"])}


def decode(path: str) -> np.ndarray:
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype="<f4").astype(np.float64)


def analyse(path: str) -> dict:
    info = probe(path)
    x = decode(path)
    pk = float(np.max(np.abs(x))) if len(x) else 0.0
    info.update(peak_db=db(pk), rms_db=db(np.sqrt(np.mean(x * x))) if len(x) else -120.0, x=x, size=os.path.getsize(path))
    return info


def loop_metrics(x: np.ndarray, window: float):
    w = int(window * SR)
    head = db(np.sqrt(np.mean(x[:w] ** 2)))
    tail = db(np.sqrt(np.mean(x[-w:] ** 2)))
    d = np.abs(np.diff(x))
    ref = max(float(np.percentile(d, 99)), 1e-4)
    jump = abs(float(x[0] - x[-1])) / ref
    return head, tail, jump


def onset_time(x: np.ndarray) -> float:
    """First time the 2 ms RMS envelope reaches -30 dB re the file peak."""
    k = int(0.002 * SR)
    pk = np.max(np.abs(x))
    env = np.sqrt(np.convolve(x * x, np.ones(k) / k, mode="same"))
    idx = np.argmax(env > pk * 10 ** (-30 / 20))
    return idx / SR


def parse_contract_ids(md_path: str):
    """Event ids from the section-3 table of the contract (ranges / slashes expanded)."""
    if not os.path.isfile(md_path):
        return None
    ids = []
    in3 = False
    for line in open(md_path, encoding="utf-8"):
        if line.startswith("## 3."):
            in3 = True
            continue
        if in3 and line.startswith("## "):
            break
        if not in3 or not line.startswith("|"):
            continue
        first = line.split("|")[1]
        toks = re.findall(r"`([a-z_]+\.[a-z_0-9]+)`", first)
        if "..." in first and len(toks) == 2:
            m1 = re.match(r"(.+_)(\d+)$", toks[0])
            m2 = re.match(r"(.+_)(\d+)$", toks[1])
            if m1 and m2:
                ids += [f"{m1.group(1)}{i}" for i in range(int(m1.group(2)), int(m2.group(2)) + 1)]
                continue
        ids += toks
    return ids


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=DEFAULT_REPO)
    ap.add_argument("--quiet", action="store_true", help="only print problems and the summary")
    args = ap.parse_args()
    repo = args.repo
    res = os.path.join(repo, "src", "main", "resources", "assets", "squidgame")
    sounds_dir = os.path.join(res, "sounds")
    sj_path = os.path.join(res, "sounds.json")
    lang_path = os.path.join(repo, "tools", "assets", "lang", "sounds.json")
    md_path = os.path.join(repo, "docs", "ASSET_CONTRACT.md")

    fails, warns = [], []

    def fail(msg):
        fails.append(msg)

    def warn(msg):
        warns.append(msg)

    if not os.path.isfile(sj_path):
        print(f"FAIL: {sj_path} missing")
        return 1
    sj = json.load(open(sj_path, encoding="utf-8"))
    lang = json.load(open(lang_path, encoding="utf-8")) if os.path.isfile(lang_path) else {}
    if not lang:
        fail(f"{lang_path} missing or empty")

    # contract cross-check against the markdown table
    md_ids = parse_contract_ids(md_path)
    if md_ids is None:
        warn("docs/ASSET_CONTRACT.md not found - skipped the id cross-check")
    else:
        for i in md_ids:
            if i not in CONTRACT:
                fail(f"contract table lists {i} but sgsynth/contract.py does not")
        for i in CONTRACT:
            if i not in md_ids:
                warn(f"{i} is generated but not in the contract markdown table")

    # gather files
    todo = {}  # path -> (event id, relative file)
    referenced = set()
    for eid in ORDER:
        ent = sj.get(eid)
        if ent is None:
            fail(f"{eid}: missing from sounds.json")
            continue
        key = f"subtitles.squidgame.{eid}"
        if ent.get("subtitle") != key:
            fail(f"{eid}: subtitle key is {ent.get('subtitle')!r}, expected {key!r}")
        if not str(lang.get(key, "")).strip():
            fail(f"{eid}: no English text for {key} in tools/assets/lang/sounds.json")
        lst = ent.get("sounds") or []
        if not lst:
            fail(f"{eid}: empty sounds list")
        loop = CONTRACT[eid]["loop"]
        for s in lst:
            name = s["name"] if isinstance(s, dict) else s
            stream = bool(isinstance(s, dict) and s.get("stream"))
            if not name.startswith("squidgame:"):
                fail(f"{eid}: sound name {name!r} is not in the squidgame namespace")
                continue
            rel = name.split(":", 1)[1]
            p = os.path.join(sounds_dir, rel + ".ogg")
            referenced.add(os.path.normpath(p))
            if not os.path.isfile(p):
                fail(f"{eid}: file {rel}.ogg does not exist")
                continue
            if loop and not stream:
                fail(f"{eid}: loop/music event should be marked \"stream\": true")
            todo[p] = (eid, rel)
    for eid in sj:
        if eid not in CONTRACT:
            warn(f"sounds.json event {eid} is not in the contract")

    # analyse files in parallel
    with cf.ThreadPoolExecutor(max_workers=4) as ex:
        results = dict(zip(todo.keys(), ex.map(analyse, todo.keys())))

    total = 0
    syll = {}
    print(f"{'event':24s} {'file':28s} {'dur s':>7s} {'target':>6s} {'ratio':>5s} {'peak':>6s} {'rms':>6s}  notes")
    for eid in ORDER:
        for p, (e, rel) in todo.items():
            if e != eid:
                continue
            a = results[p]
            total += a["size"]
            tgt = CONTRACT[eid]["target"]
            ratio = a["duration"] / tgt
            notes = []
            ok = True
            if a["codec"] != "vorbis" or a["channels"] != 1 or a["rate"] != SR:
                fail(f"{rel}: expected mono 44.1 kHz vorbis, got {a['codec']} {a['channels']}ch {a['rate']} Hz")
                notes.append("format")
                ok = False
            if abs(ratio - 1.0) > TOLERANCE:
                fail(f"{rel}: duration {a['duration']:.3f}s is outside +-{TOLERANCE:.0%} of the contract {tgt}s")
                notes.append("duration")
                ok = False
            if not (PEAK_MIN <= a["peak_db"] <= PEAK_MAX):
                fail(f"{rel}: peak {a['peak_db']:.1f} dBFS outside [{PEAK_MIN}, {PEAK_MAX}]")
                notes.append("peak")
                ok = False
            x = a["x"]
            if CONTRACT[eid]["loop"]:
                win = 1.0 if eid.startswith("music.") else 0.5
                head, tail, jump = loop_metrics(x, win)
                notes.append(f"loop head/tail {head:.1f}/{tail:.1f} dB, wrap jump x{jump:.2f}")
                if abs(head - tail) > HEADTAIL_TOL_DB:
                    fail(f"{rel}: loop head/tail loudness differs by {abs(head - tail):.1f} dB")
                    ok = False
                if jump > JUMP_LIMIT:
                    fail(f"{rel}: click at the loop point (jump x{jump:.2f} of the largest sample steps)")
                    ok = False
            if eid.startswith("doll.syllable_"):
                ot = onset_time(x)
                syll[eid] = loudness_db(x)
                notes.append(f"onset {ot * 1000:.1f} ms, loudness K {syll[eid][1]:.1f} / A {syll[eid][2]:.1f} dB")
                if ot > ONSET_LIMIT_S:
                    fail(f"{rel}: leading silence {ot * 1000:.1f} ms (must start on its onset)")
                    ok = False
            if np.any(~np.isfinite(x)):
                fail(f"{rel}: non-finite samples")
                ok = False
            status = "ok" if ok else "FAIL"
            if not args.quiet or not ok:
                print(f"{eid:24s} {rel:28s} {a['duration']:7.3f} {tgt:6.2f} {ratio:5.2f} {a['peak_db']:6.1f} "
                      f"{a['rms_db']:6.1f}  {status} {'; '.join(notes)}")
    if len(syll) == 10:
        arr = np.array(list(syll.values()))            # columns: plain, K-weighted, A-weighted
        perc = (1.0 - A_WEIGHT_SHARE) * arr[:, 1] + A_WEIGHT_SHARE * arr[:, 2]
        sp = perc.max() - perc.min()
        spk = arr[:, 1].max() - arr[:, 1].min()
        spa = arr[:, 2].max() - arr[:, 2].min()
        spp = arr[:, 0].max() - arr[:, 0].min()
        print(f"\ndoll syllables: perceptual-level spread {sp:.2f} dB  (K-weighted {spk:.1f} dB, A-weighted {spa:.1f} dB, "
              f"plain RMS {spp:.1f} dB)")
        if sp > SYLLABLE_SPREAD_DB:
            fail(f"doll syllables are not loudness matched (perceptual spread {sp:.1f} dB)")
        if max(spk, spp) > SYLLABLE_PLAIN_K_SPREAD_DB or spa > SYLLABLE_A_SPREAD_DB:
            fail(f"doll syllable loudness differs too much (K {spk:.1f}, plain {spp:.1f}, A {spa:.1f} dB)")

    # orphans / total size
    for root, _, files in os.walk(sounds_dir):
        for f in files:
            pth = os.path.normpath(os.path.join(root, f))
            if f.endswith(".ogg") and pth not in referenced:
                warn(f"orphan file not referenced by sounds.json: {os.path.relpath(pth, sounds_dir)}")
    print(f"\n{len(todo)} files for {len(ORDER)} events, total {total / 1048576:.2f} MiB (limit {SIZE_LIMIT / 1048576:.0f} MiB)")
    if total > SIZE_LIMIT:
        fail("total audio size exceeds 25 MB")

    for w in warns:
        print("WARN:", w)
    for f in fails:
        print("FAIL:", f)
    print("\nRESULT:", "PASS" if not fails else f"FAIL ({len(fails)} problem(s))")
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
