#!/usr/bin/env python3
"""Renders the mod's original sound effects and music (tools/audio) to OGG Vorbis and writes sounds.json.

    python3 tools/gen_sounds.py            # everything
    python3 tools/gen_sounds.py sfx        # only the sound effects
    python3 tools/gen_sounds.py music wano # only the named songs
    python3 tools/gen_sounds.py discs      # only the mono music-disc copies of the rendered tracks

Needs numpy and ffmpeg (with libvorbis). Output: src/main/resources/assets/grandline/sounds/** and sounds.json.
Sound effects are mono (Minecraft only attenuates mono sounds with distance); music is stereo and streamed.
"""
import json
import os
import subprocess
import sys
import tempfile
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'audio'))
import music  # noqa: E402
import sfx  # noqa: E402
from synth import SR, drive, ffilter  # noqa: E402

# quieter by design (relative to the loud effects, which are peak-normalised)
LEVEL = {'haki.observation': 0.6, 'den_den.pickup': 0.55, 'ui.quest': 0.7, 'ui.discover': 0.7,
         'combat.soru': 0.7, 'combat.geppo': 0.7, 'fruit.shambles': 0.75, 'fruit.room': 0.8, 'player.knockdown': 0.8,
         'boss.telegraph': 0.7, 'den_den.ring': 0.35, 'ship.horn': 0.6, 'fruit.transform': 0.7,
         'fruit.quake': 0.66, 'fruit.thunder': 0.66}
# impacts whose energy sits in a few transients: saturate them so they are as loud as vanilla explosions
DENSE = {'ship.cannon': 3.0, 'fruit.quake': 4.0, 'fruit.thunder': 5.0, 'haki.conqueror': 2.0, 'fruit.flame': 1.6}

# tracks that are also music discs: a mono copy (a jukebox is a positional source and only mono sounds attenuate)
DISCS = ['east_blue', 'grand_line', 'wano', 'boss']

ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'grandline')
SOUNDS = os.path.join(ASSETS, 'sounds')


def encode(wav, ogg, quality):
    os.makedirs(os.path.dirname(ogg), exist_ok=True)
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a',
                    str(quality), '-map_metadata', '-1', ogg], check=True)


def trim(y, floor_db=-60):
    a = np.abs(y)
    thr = a.max() * 10 ** (floor_db / 20)
    idx = np.nonzero(a > thr)[0]
    end = min(len(y), (idx[-1] if len(idx) else len(y)) + int(0.02 * SR))
    y = y[:end].copy()
    f = min(len(y), int(0.012 * SR))
    y[-f:] *= np.linspace(1, 0, f)
    return y


def render_sfx(defs):
    out = {}
    tmp = tempfile.mkdtemp()
    for name, (fn, variants) in sfx.SFX.items():
        files = []
        for v in range(1, variants + 1):
            rng = np.random.default_rng(zlib.crc32(f'{name}#{v}'.encode()))
            y = fn(rng)
            if not np.all(np.isfinite(y)):
                raise SystemExit(f'{name}: non-finite samples')
            y = ffilter(y, hp=40, order=2)  # nothing useful lives below 40 Hz; it only eats headroom
            y = trim(y)
            if name in DENSE:
                y = drive(y / max(1e-9, np.abs(y).max()), DENSE[name])
            y = y * (0.8 * LEVEL.get(name, 1.0) / max(1e-9, np.abs(y).max()))  # headroom for Vorbis overshoot
            wav = os.path.join(tmp, 'x.wav')
            music.write_wav(wav, y[None, :])
            rel = name.replace('.', '/') + f'_{v}'
            encode(wav, os.path.join(SOUNDS, rel + '.ogg'), 4)
            files.append('grandline:' + rel)
            print(f'  sfx {rel}: {len(y) / SR:.2f} s')
        out[name] = {'subtitle': 'subtitles.grandline.' + name, 'sounds': files}
    defs.update(out)


def disc(src, name, defs):
    os.makedirs(os.path.join(SOUNDS, 'music', 'disc'), exist_ok=True)
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', src, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '2',
                    '-map_metadata', '-1', os.path.join(SOUNDS, 'music', 'disc', name + '.ogg')], check=True)
    defs['disc.' + name] = {'sounds': [{'name': 'grandline:music/disc/' + name, 'stream': True}]}


def render_discs(defs):
    """Mono disc copies from the already rendered stereo tracks."""
    for name in DISCS:
        disc(os.path.join(SOUNDS, 'music', name + '.ogg'), name, defs)
        print('  disc', name)


def render_music(defs, only):
    tmp = tempfile.mkdtemp()
    for name, fn in music.SONGS.items():
        if only and name not in only:
            defs['music.' + name] = {'sounds': [{'name': 'grandline:music/' + name, 'stream': True}]}
            continue
        wav = os.path.join(tmp, name + '.wav')
        st = fn(wav)
        encode(wav, os.path.join(SOUNDS, 'music', name + '.ogg'), 2)
        if name in DISCS:
            disc(wav, name, defs)
        rms = 10 * np.log10(np.mean(st.mean(axis=0) ** 2) + 1e-12)
        print(f'  music {name}: {st.shape[1] / SR:.1f} s, rms {rms:.1f} dB, peak {np.abs(st).max():.2f}')
        defs['music.' + name] = {'sounds': [{'name': 'grandline:music/' + name, 'stream': True}]}


def main():
    args = sys.argv[1:]
    path = os.path.join(ASSETS, 'sounds.json')
    defs = {}
    if os.path.exists(path):
        with open(path, encoding='utf-8') as f:
            defs = json.load(f)
    if not args or args[0] == 'sfx':
        render_sfx(defs)
    if not args or args[0] == 'music':
        render_music(defs, set(args[1:]))
    if args and args[0] == 'discs':
        render_discs(defs)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(dict(sorted(defs.items())), f, indent=2)
        f.write('\n')
    print('wrote', os.path.relpath(path, ROOT), len(defs), 'sound events')


if __name__ == '__main__':
    main()
