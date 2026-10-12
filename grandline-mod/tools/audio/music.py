"""Original music for Grand Line Odyssey, composed here as code (melodies, chords, drum patterns) and rendered with
synth.py. Every melody is original; nothing is transcribed from the anime or any other score.

Notation: a melody is 'D5:1 E5:.5 r:.5 | ...' (note:beats, 'r' is a rest, '|' separates bars and every bar is checked
against the metre). Chords are names like 'Dm', 'A7', 'F#/E'.
"""
import numpy as np

from synth import (INSTRUMENTS, Mix, drum, master, midi_hz, note, SR, secs, ffilter, tt, TAU)

QUAL = {'': (0, 4, 7), 'm': (0, 3, 7), '7': (0, 4, 7, 10), 'm7': (0, 3, 7, 10), 'maj7': (0, 4, 7, 11),
        'dim': (0, 3, 6), 'sus4': (0, 5, 7), 'sus2': (0, 2, 7), '5': (0, 7), 'm9': (0, 3, 7, 10, 14),
        'add9': (0, 4, 7, 14), '6': (0, 4, 7, 9), 'm6': (0, 3, 7, 9)}
PC = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}


def _pc(s):
    p = PC[s[0]]
    rest = s[1:]
    while rest and rest[0] in '#b':
        p += 1 if rest[0] == '#' else -1
        rest = rest[1:]
    return p % 12, rest


def chord(name):
    """-> (bass pitch class, [pitch classes])"""
    bass = None
    if '/' in name:
        name, b = name.split('/')
        bass, _ = _pc(b)
    root, q = _pc(name)
    pcs = [(root + i) % 12 for i in QUAL[q]]
    return (root if bass is None else bass), pcs, root


def near(pc, center):
    """MIDI note of pitch class pc closest to center."""
    base = center - ((center - pc) % 12)
    return base if center - base <= 6 else base + 12


def voicing(name, center=60, n=4):
    _, pcs, root = chord(name)
    notes = sorted({near(p, center) for p in pcs})
    i = 0
    while len(notes) < n:
        notes.append(notes[i] + 12)
        i += 1
    return sorted(notes)[:max(n, len(pcs))]


class Song:
    def __init__(self, name, bpm, beats_per_bar, bars, seed, tail=3.0):
        self.name = name
        self.spb = 60.0 / bpm
        self.bpb = beats_per_bar
        self.bars = bars
        self.length = bars * beats_per_bar * self.spb + tail
        self.mix = Mix(self.length, seed)
        self.rng = np.random.default_rng(seed + 7)

    def t(self, beat):
        return beat * self.spb

    def bar(self, b):
        return b * self.bpb

    def play(self, bus, inst, midi, beat, dur, vel=0.8, human=0.006, **kw):
        f = midi_hz(midi)
        v = vel * (1 + self.rng.uniform(-0.07, 0.07))
        fn = INSTRUMENTS[inst]
        y = fn(f, dur * self.spb, v, self.rng, **kw) if kw else fn(f, dur * self.spb, v, self.rng)
        at = max(0.0, self.t(beat) + self.rng.uniform(-human, human))
        self.mix.add(bus, y, at)

    def hit(self, bus, kind, beat, vel=1.0, human=0.004):
        y = drum(kind, vel * (1 + self.rng.uniform(-0.08, 0.08)), self.rng)
        self.mix.add(bus, y, max(0.0, self.t(beat) + self.rng.uniform(-human, human)))

    def parse(self, text):
        bars = [b.strip() for b in text.split('|') if b.strip()]
        out = []
        for i, b in enumerate(bars):
            total = 0.0
            for tok in b.split():
                n, d = tok.split(':')
                d = float(d)
                out.append((None if n == 'r' else note(n), d))
                total += d
            if abs(total - self.bpb) > 1e-6:
                raise ValueError(f'{self.name}: bar {i + 1} of "{text[:40]}..." has {total} beats, not {self.bpb}')
        return out

    def melody(self, bus, inst, text, bar, vel=0.8, shift=0, legato=1.0, scale=None, third=0, **kw):
        """Plays a melody from bar `bar`; shift moves it by semitones; third>0 adds a diatonic harmony `third` scale
        steps below (needs scale, a list of pitch classes)."""
        beat = self.bar(bar)
        for m, d in self.parse(text):
            if m is not None:
                mm = m + shift
                if third:
                    mm = diatonic(mm, -third, scale)
                self.play(bus, inst, mm, beat, d * legato, vel, **kw)
            beat += d

    def pattern(self, bus, kinds, bar, bars=1, steps=16, vel=1.0):
        """kinds: {'kick': 'x...x...', ...}; one char per step: X accent, x hit, o ghost, . rest."""
        for b in range(bars):
            for kind, pat in kinds.items():
                if len(pat) != steps:
                    raise ValueError(f'{self.name}: pattern {kind} has {len(pat)} steps, not {steps}')
                for i, ch in enumerate(pat):
                    if ch == '.':
                        continue
                    v = {'X': 1.15, 'x': 0.9, 'o': 0.45}[ch] * vel
                    self.hit(bus, kind, self.bar(bar + b) + i * self.bpb / steps, v)

    def segs(self, entry):
        """'Gm C7' -> [(chord, start beat in the bar, beats)]: several chords split a bar evenly."""
        parts = entry.split()
        w = self.bpb / len(parts)
        return [(p, i * w, w) for i, p in enumerate(parts)]

    def chord_at(self, entry, beat):
        for c, st, w in self.segs(entry):
            if st <= beat + 1e-6 < st + w:
                return c
        return self.segs(entry)[-1][0]

    def chords_hold(self, bus, inst, prog, bar, center=60, vel=0.5, n=4, beats=None):
        for i, entry in enumerate(prog):
            if entry == '-':
                continue
            sg = self.segs(entry)
            for c, st, w in sg:
                for m in voicing(c, center, n):
                    self.play(bus, inst, m, self.bar(bar + i) + st, beats if beats and len(sg) == 1 else w, vel, human=0.01)

    def arp(self, bus, inst, prog, bar, seq, step, center=60, vel=0.6, dur=None, **kw):
        """seq indexes the chord's voicing (0 = lowest); one entry per step, repeated over the bar."""
        per_bar = int(round(self.bpb / step))
        for i, entry in enumerate(prog):
            if entry == '-':
                continue
            for k in range(per_bar):
                idx = seq[k % len(seq)]
                if idx is None:
                    continue
                v = voicing(self.chord_at(entry, k * step), center, 5)
                m = v[idx % len(v)] + 12 * (idx // len(v))
                self.play(bus, inst, m, self.bar(bar + i) + k * step, dur or step * 1.6, vel * (1.0 if k == 0 else 0.85), **kw)

    def bassline(self, bus, inst, prog, bar, figure, octave=2, vel=0.8):
        """figure: [(beat, 'R'|'5'|'8'|'3'|'-5', dur), ...] per bar."""
        for i, entry in enumerate(prog):
            if entry == '-':
                continue
            for beat, deg, d in figure:
                b, pcs, root = chord(self.chord_at(entry, beat))
                r = b + 12 * (octave + 1)
                third = (pcs[1] - root) % 12
                m = {'R': r, '5': r + 7, '8': r + 12, '3': r + third, '-5': r - 5}[deg]
                self.play(bus, inst, m, self.bar(bar + i) + beat, d, vel)

    def strum(self, bus, inst, prog, bar, figure, center=55, vel=0.55, n=5):
        """figure: [(beat, 'D'|'U', dur)] per bar; down strums go low to high."""
        for i, entry in enumerate(prog):
            if entry == '-':
                continue
            for beat, direction, d in figure:
                v = voicing(self.chord_at(entry, beat), center, n)
                order = v if direction == 'D' else v[::-1]
                vv = vel if direction == 'D' else vel * 0.7
                for k, m in enumerate(order):
                    self.play(bus, inst, m, self.bar(bar + i) + beat + k * 0.012 / self.spb, d, vv, human=0.003)

    def render(self, out_path_wav, rev=2.0, damp=0.5, rev_level=1.0, rms=-20.0):
        st = self.mix.render(rev, damp, rev_level)
        st = master(st, rms)
        n = secs(self.length)
        st = st[:, :n]
        fo = secs(2.0)
        st[:, -fo:] *= np.linspace(1, 0, fo) ** 2
        write_wav(out_path_wav, st)
        return st


def diatonic(m, steps, scale):
    """Move MIDI note m by `steps` scale degrees within scale (pitch classes)."""
    sc = sorted(scale)
    pc = m % 12
    if pc not in sc:  # chromatic note: move by the nearest scale tone
        pc = min(sc, key=lambda p: min((p - pc) % 12, (pc - p) % 12))
        m = m - (m % 12) + pc
    octave, idx = divmod(sc.index(pc) + steps, len(sc))
    return (m - (m % 12)) + 12 * octave + sc[idx]


def scale_of(root, mode='major'):
    steps = {'major': (0, 2, 4, 5, 7, 9, 11), 'minor': (0, 2, 3, 5, 7, 8, 10), 'harm': (0, 2, 3, 5, 7, 8, 11)}[mode]
    r, _ = _pc(root)
    return [(r + s) % 12 for s in steps]


def write_wav(path, st):
    import wave
    data = np.clip(st, -1, 1)
    pcm = (data.T * 32767).astype('<i2').tobytes()
    with wave.open(path, 'wb') as w:
        w.setnchannels(st.shape[0])
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm)


# ================================================================================================================ songs
def east_blue(path):
    """'Wind in the Sails' - a jig-time sea shanty in D major (the Blues at sea)."""
    s = Song('east_blue', bpm=216, beats_per_bar=6, bars=36, seed=11)  # beat = an eighth note, 6/8
    s.mix.bus('lead', pan=-0.15, rev=0.18, gain=0.9, peaks=((1800, 2, 1.5),))
    s.mix.bus('fiddle', pan=0.25, rev=0.22, gain=0.8, peaks=((450, 3, 2), (2600, 3, 2)))
    s.mix.bus('guitar', pan=0.35, rev=0.12, gain=0.9, hp=90)
    s.mix.bus('bass', pan=0.0, rev=0.05, gain=0.62, lp=900)
    s.mix.bus('perc', pan=-0.05, rev=0.1, gain=0.7)
    s.mix.bus('tamb', pan=0.4, rev=0.12, gain=0.5)
    s.mix.bus('whistle', pan=-0.35, rev=0.3, gain=0.45)
    D = scale_of('D')
    A = ('A4:2 D5:1 F#5:2 A5:1 | B5:2 A5:1 G5:2 B5:1 | A5:2 F#5:1 D5:2 F#5:1 | E5:2 C#5:1 A4:3 |'
         'A4:2 D5:1 F#5:2 A5:1 | B5:2 D6:1 B5:2 G5:1 | A5:2 G5:1 E5:2 C#5:1 | D5:5 r:1')
    B = ('B4:2 D5:1 F#5:3 | G5:2 F#5:1 E5:2 D5:1 | F#5:2 E5:1 D5:2 A4:1 | E5:3 r:1 C#5:1 E5:1 |'
         'F#5:2 A5:1 B5:3 | G5:2 A5:1 B5:2 D6:1 | E6:2 D6:1 B5:2 G5:1 | A5:2 G5:1 E5:2 C#5:1')
    progA = ['D', 'G', 'D', 'A', 'D', 'G', 'A', 'D']
    progB = ['Bm', 'G', 'D', 'A', 'Bm', 'G', 'Em', 'A7']
    form = [(0, ['D', 'A']), (2, progA), (10, progA), (18, progB), (26, progA), (34, ['D', 'D'])]
    vamp = [(0, 'D', 2), (2, 'U', 1), (3, 'D', 2), (5, 'U', 1)]
    bodhran = {'bodhran': 'X.o.x.X.o.xo'}
    for bar, prog in form:
        s.strum('guitar', 'guitar', prog, bar, vamp, center=57, vel=0.4)
        s.bassline('bass', 'bass', prog, bar, [(0, 'R', 2.8), (3, '5', 2.8)], octave=2, vel=0.75)
        s.pattern('perc', bodhran, bar, len(prog), steps=12, vel=0.8)
        if bar >= 10:
            s.pattern('tamb', {'tamb': '......x.....'}, bar, len(prog), steps=12, vel=0.6)
    s.melody('lead', 'accordion', A, 2, vel=0.75)
    s.melody('fiddle', 'fiddle', A, 10, vel=0.75)
    s.melody('lead', 'accordion', A, 10, vel=0.5, third=2, scale=D)
    s.melody('lead', 'accordion', B, 18, vel=0.7)
    s.melody('fiddle', 'fiddle', B, 18, vel=0.55, shift=-12)
    s.melody('fiddle', 'fiddle', A, 26, vel=0.8)
    s.melody('lead', 'accordion', A, 26, vel=0.6)
    s.melody('whistle', 'whistle', A, 26, vel=0.35, shift=12, third=2, scale=D)
    s.pattern('perc', {'shaker': 'x.o.o.x.o.o.'}, 26, 8, steps=12, vel=0.6)
    for m in voicing('D', 62, 4):
        s.play('lead', 'accordion', m, s.bar(34), 9, 0.55)
    s.play('fiddle', 'fiddle', note('D5'), s.bar(34), 9, 0.5)
    s.hit('perc', 'bodhran', s.bar(35) + 3, 1.0)
    return s.render(path, rev=1.6, damp=0.6, rms=-21)


def grand_line(path):
    """'Bound for the Grand Line' - an adventurous march in A minor that ends in A major."""
    s = Song('grand_line', bpm=132, beats_per_bar=4, bars=34, seed=12)
    s.mix.bus('brass', pan=-0.2, rev=0.3, gain=1.0)
    s.mix.bus('strings', pan=0.25, rev=0.3, gain=0.7, width=0.6)
    s.mix.bus('ost', pan=-0.3, rev=0.2, gain=0.55)
    s.mix.bus('flute', pan=0.4, rev=0.35, gain=0.7)
    s.mix.bus('bass', rev=0.1, gain=0.5, lp=700)
    s.mix.bus('drums', rev=0.15, gain=0.7)
    s.mix.bus('timp', pan=0.1, rev=0.3, gain=0.8)
    s.mix.bus('choir', pan=0.0, rev=0.45, gain=0.45, width=0.8)
    A = ('A4:1.5 E4:.5 A4:1 B4:1 | C5:1.5 A4:.5 F4:1 C5:1 | E5:2 D5:1 C5:1 | D5:3 r:1 |'
         'A4:1.5 E4:.5 A4:1 C5:1 | F5:1.5 E5:.5 C5:1 A4:1 | B4:1 C5:1 D5:1 B4:1 | G#4:3 r:1')
    B = ('A5:1 G5:.5 F5:.5 C5:2 | D5:1 E5:.5 F5:.5 G5:2 | E5:1 D5:.5 C5:.5 G4:1 C5:1 | E5:2 A5:2 |'
         'A5:1.5 G5:.5 F5:1 E5:1 | D5:1.5 E5:.5 F5:1 G5:1 | E5:1 C5:1 A4:1 C5:1 | B4:2 D5:1 G#4:1')
    C = 'A4:2 C5:2 | B4:2 D5:2 | G#4:2 B4:2 | C#5:4 | C#5:4 | r:4'
    progA = ['Am', 'F', 'C', 'G', 'Am', 'F', 'G', 'E']
    progB = ['F', 'G', 'C', 'Am', 'F', 'G', 'Am', 'E7']
    progC = ['F', 'G', 'E', 'A', 'A', '-']
    ost = [0, 2, 1, 2, 0, 2, 1, 2]
    march = {'snare': 'x..ox.oox..ox.oo', 'kick': 'x.......x.......'}
    full = {'snare': '....x.......x..o', 'kick': 'x.....x.x.......', 'hat': 'x.x.x.x.x.x.x.x.'}
    # intro: ostinato and timpani
    s.arp('ost', 'strings_short', ['Am'] * 4, 0, ost, 0.5, center=57, vel=0.55, dur=0.45)
    for b in range(4):
        s.hit('timp', 'timpani', s.bar(b), 0.6 + 0.12 * b)
    s.pattern('drums', {'snare': '..........o.oxox'}, 3, 1, vel=0.7)
    for bar, prog in ((4, progA), (12, progB), (20, progA), (28, progC)):
        s.arp('ost', 'strings_short', prog, bar, ost, 0.5, center=57, vel=0.5, dur=0.45)
        s.bassline('bass', 'bass', prog, bar, [(0, 'R', 1.5), (1.5, 'R', .5), (2, '5', 1), (3, '8', 1)], vel=0.75)
        s.pattern('drums', march if bar < 20 else full, bar, len(prog), vel=0.75)
        for i in range(len(prog)):
            if i % 4 == 0:
                s.hit('timp', 'timpani', s.bar(bar + i), 0.7)
    s.hit('drums', 'crash', s.bar(4), 0.8)
    s.melody('brass', 'brass', A, 4, vel=0.8)
    s.chords_hold('strings', 'strings', progA, 4, center=60, vel=0.3)
    s.melody('flute', 'flute', B, 12, vel=0.7)
    s.melody('strings', 'strings', B, 12, vel=0.45, shift=-12)
    s.chords_hold('strings', 'strings', progB, 12, center=55, vel=0.25)
    s.hit('drums', 'crash', s.bar(20), 0.9)
    s.melody('brass', 'brass', A, 20, vel=0.9)
    s.melody('strings', 'strings', A, 20, vel=0.5, shift=12)
    s.chords_hold('choir', 'choir', progA, 20, center=60, vel=0.35)
    s.melody('brass', 'brass', C, 28, vel=0.95)
    s.chords_hold('choir', 'choir', progC, 28, center=62, vel=0.4)
    s.chords_hold('strings', 'strings', progC, 28, center=55, vel=0.35)
    s.hit('drums', 'crash', s.bar(31), 1.0)
    s.hit('timp', 'timpani', s.bar(31), 1.0)
    return s.render(path, rev=2.2, damp=0.5, rms=-20)


def new_world(path):
    """'Into the New World' - dark and broad, D minor, taiko and choir."""
    s = Song('new_world', bpm=90, beats_per_bar=4, bars=24, seed=13)
    s.mix.bus('brass', pan=-0.2, rev=0.35, gain=1.0)
    s.mix.bus('strings', pan=0.2, rev=0.4, gain=0.7, width=0.7)
    s.mix.bus('low', rev=0.25, gain=0.55, lp=1200)
    s.mix.bus('choir', rev=0.5, gain=0.5, width=0.8)
    s.mix.bus('harp', pan=0.45, rev=0.4, gain=1.4)
    s.mix.bus('taiko', rev=0.25, gain=0.45)
    A = ('D4:1.5 E4:.5 F4:1 A4:1 | Bb4:2 A4:1 F4:1 | G4:1.5 A4:.5 Bb4:1 D5:1 | A4:2 C#5:2 |'
         'D5:1.5 C5:.5 A4:1 F4:1 | D5:2 F5:1 D5:1 | E5:1.5 D5:.5 C5:1 E5:1 | A4:4')
    B = ('F5:2 D5:1 F5:1 | G5:2 E5:1 G5:1 | A5:3 G5:.5 F5:.5 | F5:2 E5:1 D5:1 |'
         'Bb5:2 A5:1 G5:1 | A5:2 E5:1 C#5:1 | D5:1 F5:1 A5:1 D6:1 | C#6:3 r:1')
    progA = ['Dm', 'Bb', 'Gm', 'A', 'Dm', 'Bb', 'C', 'A']
    progB = ['Bb', 'C', 'Dm', 'Dm', 'Gm', 'A', 'Dm', 'A']
    taiko_a = {'taiko': 'X.....x.x.......'}
    taiko_b = {'taiko': 'X.....x.x...x.o.', 'tom_lo': '..........o....o'}
    # intro: low D pedal, choir, taiko
    for b in range(4):
        s.play('low', 'strings', note('D2'), s.bar(b), 4, 0.6)
        s.play('low', 'strings', note('D3'), s.bar(b), 4, 0.4)
    s.chords_hold('choir', 'choir_oo', ['Dm', 'Dm', 'Bb', 'A'], 0, center=60, vel=0.35)
    s.pattern('taiko', taiko_a, 0, 4, vel=0.8)
    for bar, prog in ((4, progA), (12, progB)):
        s.bassline('low', 'strings', prog, bar, [(0, 'R', 4)], octave=2, vel=0.6)
        s.bassline('low', 'bass', prog, bar, [(0, 'R', 1.5), (1.5, 'R', .5), (2, 'R', 2)], octave=1, vel=0.7)
        s.arp('harp', 'harp', prog, bar, [0, 1, 2, 3, 4, 3, 2, 1], 0.5, center=62, vel=0.4)
        s.pattern('taiko', taiko_a if bar == 4 else taiko_b, bar, len(prog), vel=0.85)
    s.melody('brass', 'brass', A, 4, vel=0.8)
    s.chords_hold('choir', 'choir_oo', progA, 4, center=57, vel=0.3)
    s.melody('strings', 'strings', B, 12, vel=0.6)
    s.melody('brass', 'brass', B, 12, vel=0.45, shift=-12)
    s.chords_hold('choir', 'choir', progB, 12, center=60, vel=0.35)
    s.hit('taiko', 'crash', s.bar(12), 0.7)
    outro = ['Dm', 'Bb', 'A', 'Dm']
    s.chords_hold('choir', 'choir', outro, 20, center=60, vel=0.4)
    s.chords_hold('brass', 'brass', outro, 20, center=55, vel=0.45, n=3)
    s.bassline('low', 'strings', outro, 20, [(0, 'R', 4)], octave=2, vel=0.6)
    s.pattern('taiko', taiko_b, 20, 3, vel=0.9)
    s.hit('taiko', 'taiko', s.bar(23), 1.2)
    s.hit('taiko', 'timpani', s.bar(23), 1.0)
    return s.render(path, rev=2.8, damp=0.55, rms=-21)


def island(path):
    """'Harbour Town' - an easy calypso in F major for towns and villages."""
    s = Song('island', bpm=108, beats_per_bar=4, bars=36, seed=14)
    s.mix.bus('marimba', pan=-0.25, rev=0.22, gain=1.0)
    s.mix.bus('whistle', pan=0.2, rev=0.3, gain=0.65)
    s.mix.bus('uke', pan=0.35, rev=0.15, gain=1.1, hp=120)
    s.mix.bus('bass', rev=0.06, gain=0.5, lp=800)
    s.mix.bus('perc', pan=-0.1, rev=0.12, gain=0.55)
    F = scale_of('F')
    A = ('C5:.5 F5:.5 r:.5 F5:.5 A5:1 G5:.5 F5:.5 | D5:1 F5:.5 A5:1 G5:.5 F5:1 | D5:.5 F5:.5 r:.5 Bb5:.5 A5:1 G5:1 |'
         'E5:1.5 C5:.5 G5:2 | C5:.5 F5:.5 r:.5 F5:.5 A5:1 C6:1 | D6:1 C6:.5 A5:1 F5:.5 D5:1 | G5:1 Bb5:1 E5:1 G5:1 |'
         'F5:3 r:1')
    B = ('D5:1 F5:1 Bb5:2 | C6:1.5 Bb5:.5 A5:1 G5:1 | A5:1 C6:1 E5:2 | F5:1.5 E5:.5 D5:2 |'
         'G5:1 Bb5:1 D6:2 | C6:1 Bb5:.5 A5:.5 G5:2 | A5:1 G5:.5 F5:.5 C5:2 | E5:1 G5:1 Bb5:1 C6:1')
    progA = ['F', 'Dm', 'Bb', 'C7', 'F', 'Dm', 'Gm C7', 'F']
    progB = ['Bb', 'C', 'Am', 'Dm', 'Gm', 'C', 'F', 'C7']
    strum = [(0, 'D', 1), (1, 'D', .5), (1.5, 'U', .5), (2.5, 'U', .5), (3, 'D', .5), (3.5, 'U', .5)]
    calypso = [(0, 'R', 1.4), (1.5, 'R', .5), (2, '5', 1), (3, '8', .9)]
    groove = {'shaker': 'x.o.x.o.x.o.x.oo', 'brush': '....x.......x...', 'kick': 'x.....x.........'}
    s.strum('uke', 'ukulele', ['F', 'C7'], 0, strum, center=67, vel=0.45, n=4)
    for bar, prog in ((2, progA), (10, progA), (18, progB), (26, progA), (34, ['F', '-'])):
        s.strum('uke', 'ukulele', prog, bar, strum, center=67, vel=0.42, n=4)
        s.bassline('bass', 'bass', prog, bar, calypso, octave=2, vel=0.75)
        s.pattern('perc', groove, bar, len(prog), vel=0.7)
    s.melody('marimba', 'marimba', A, 2, vel=0.8)
    s.melody('whistle', 'whistle', A, 10, vel=0.6)
    s.melody('marimba', 'marimba', A, 10, vel=0.5, third=2, scale=F)
    s.melody('whistle', 'whistle', B, 18, vel=0.6)
    s.melody('marimba', 'marimba', B, 18, vel=0.45, shift=-12)
    s.melody('marimba', 'marimba', A, 26, vel=0.8)
    s.melody('whistle', 'whistle', A, 26, vel=0.45, shift=12, third=2, scale=F)
    s.play('marimba', 'marimba', note('F5'), s.bar(34), 4, 0.7)
    s.play('marimba', 'marimba', note('A5'), s.bar(34), 4, 0.5)
    s.play('whistle', 'whistle', note('C6'), s.bar(34), 4, 0.5)
    return s.render(path, rev=1.4, damp=0.6, rms=-21)


def battle(path):
    """'Clash on the Waves' - a fast fight in E minor."""
    s = Song('battle', bpm=152, beats_per_bar=4, bars=40, seed=15)
    s.mix.bus('brass', pan=-0.15, rev=0.22, gain=0.85)
    s.mix.bus('fiddle', pan=0.3, rev=0.22, gain=0.6, peaks=((450, 3, 2), (2600, 3, 2)))
    s.mix.bus('ost', pan=-0.35, rev=0.15, gain=0.55)
    s.mix.bus('bass', rev=0.04, gain=0.6, lp=900)
    s.mix.bus('drums', rev=0.12, gain=0.6)
    s.mix.bus('choir', rev=0.45, gain=0.4, width=0.8)
    A = ('E5:.5 E5:.5 B4:.5 E5:.5 G5:1 F#5:.5 E5:.5 | G5:.5 G5:.5 E5:.5 G5:.5 C6:1 B5:.5 A5:.5 |'
         'A5:1 F#5:.5 D5:.5 A5:1 B5:.5 A5:.5 | F#5:2 D#5:1 B4:1 | E5:.5 E5:.5 B4:.5 E5:.5 G5:1 A5:.5 B5:.5 |'
         'C6:1 B5:.5 A5:.5 G5:1 E5:1 | A5:1 C6:.5 B5:.5 A5:1 E5:1 | D#5:1.5 F#5:.5 B5:2')
    B = 'E5:2 G5:2 | F#5:2 A5:2 | B5:3 A5:.5 G5:.5 | E5:4 | C6:2 B5:1 A5:1 | A5:2 F#5:1 D5:1 | D#5:2 F#5:2 | B5:4'
    progA = ['Em', 'C', 'D', 'B', 'Em', 'C', 'Am', 'B']
    progB = ['C', 'D', 'Em', 'Em', 'C', 'D', 'B', 'B']
    ost = [0, 4, 2, 4, 0, 4, 2, 4, 1, 4, 2, 4, 1, 4, 2, 4]
    rock = {'kick': 'x.....x.x.......', 'snare': '....x.......x...', 'hat': 'x.x.x.x.x.x.x.x.'}
    half = {'kick': 'x.......x.....x.', 'snare': '........x.......', 'hat': 'x.x.x.x.x.x.x.x.'}
    s.arp('ost', 'strings_short', ['Em'] * 4, 0, ost, 0.25, center=52, vel=0.5, dur=0.3)
    s.pattern('drums', {'tom': 'x.x.x.x.x.x.x.x.'}, 2, 1, vel=0.6)
    s.pattern('drums', {'tom_lo': 'x.x.x.x.........', 'snare': '........xoxoxXxX'}, 3, 1, vel=0.75)
    for bar, prog, beat in ((4, progA, rock), (12, progA, rock), (20, progB, half), (28, progA, rock),
                            (36, ['Em', 'C', 'D', 'B'], rock)):
        s.arp('ost', 'strings_short', prog, bar, ost, 0.25, center=52, vel=0.45, dur=0.3)
        s.bassline('bass', 'synth_bass', prog, bar, [(k * .5, 'R', .45) for k in range(8)], octave=1, vel=0.65)
        s.pattern('drums', beat, bar, len(prog), vel=0.8)
        s.hit('drums', 'crash', s.bar(bar), 0.8)
    s.melody('brass', 'brass', A, 4, vel=0.85)
    s.melody('fiddle', 'fiddle', A, 12, vel=0.75)
    for i, c in enumerate(progA):  # brass stabs on the off-beats
        for bt in (1.5, 3.5):
            for m in voicing(c, 60, 3):
                s.play('brass', 'brass', m, s.bar(12 + i) + bt, 0.35, 0.5)
    s.melody('brass', 'brass', B, 20, vel=0.85)
    s.chords_hold('choir', 'choir', progB, 20, center=60, vel=0.35)
    s.melody('brass', 'brass', A, 28, vel=0.9)
    s.melody('fiddle', 'fiddle', A, 28, vel=0.6, shift=12)
    s.chords_hold('brass', 'brass', ['Em', 'C', 'D', 'B'], 36, center=58, vel=0.5, n=3)
    s.play('brass', 'brass', note('E5'), s.bar(39) + 3, 1.5, 1.0)
    s.hit('drums', 'crash', s.bar(39) + 3, 1.0)
    s.hit('drums', 'kick', s.bar(39) + 3, 1.0)
    return s.render(path, rev=1.6, damp=0.55, rms=-19.5)


def boss(path):
    """'The Emperor's Wrath' - C minor with a Phrygian edge, choir and timpani."""
    s = Song('boss', bpm=138, beats_per_bar=4, bars=40, seed=16)
    s.mix.bus('brass', pan=-0.2, rev=0.3, gain=1.0)
    s.mix.bus('strings', pan=0.25, rev=0.3, gain=0.8, width=0.6)
    s.mix.bus('low', rev=0.1, gain=0.55, lp=700)
    s.mix.bus('choir', rev=0.5, gain=0.5, width=0.9)
    s.mix.bus('drums', rev=0.18, gain=0.5)
    s.mix.bus('timp', pan=0.15, rev=0.3, gain=0.6)
    A = ('C5:1.5 D5:.5 Eb5:1 G5:1 | Ab5:2 G5:1 Eb5:1 | F5:1.5 G5:.5 Ab5:1 C6:1 | B5:2 G5:1 D5:1 |'
         'C5:1.5 D5:.5 Eb5:1 G5:1 | C6:1.5 Bb5:.5 Ab5:1 Eb5:1 | Db6:2 C6:1 Ab5:1 | B5:3 r:1')
    C = 'G4:2 C5:2 | Bb4:4 | Ab4:2 C5:2 | B4:4 | C5:2 Eb5:2 | D5:2 F5:2 | Eb5:2 G5:2 | D5:2 B4:2'
    progI = ['Cm', 'Cm', 'Db', 'Db', 'Cm', 'Ab', 'Bb', 'G']
    progA = ['Cm', 'Ab', 'Fm', 'G', 'Cm', 'Ab', 'Db', 'G']
    progC = ['Cm', 'Eb', 'Fm', 'G', 'Ab', 'Bb', 'Cm', 'G']
    ost_notes = ['C2', 'C2', 'Eb2', 'C2', 'Db2', 'C2', 'G1', 'C2']
    drive_ = {'kick': 'x..x..x.x..x..x.', 'snare': '....x.......x..x', 'hat': 'x.xxx.xxx.xxx.xx'}
    # intro: tremolo strings, timpani roll, choir swell
    for b in range(4):
        for k in range(16):
            s.play('strings', 'strings_short', note('C3'), s.bar(b) + k * 0.25, 0.3, 0.25 + 0.05 * b)
        for k in range(8):
            s.hit('timp', 'timpani', s.bar(b) + k * 0.5, 0.25 + 0.08 * b + 0.01 * k)
    s.chords_hold('choir', 'choir', ['Cm', 'Cm', 'Db', 'G'], 0, center=60, vel=0.3)
    s.hit('drums', 'cymbal_swell', s.bar(3) + 4 - 2.4 / s.spb, 0.9)

    def ostinato(bar, bars):
        for b in range(bars):
            for k, n_ in enumerate(ost_notes):
                s.play('low', 'synth_bass', note(n_), s.bar(bar + b) + k * 0.5, 0.45, 0.75)
                s.play('low', 'strings_short', note(n_) + 12, s.bar(bar + b) + k * 0.5, 0.4, 0.35)
    for bar, prog in ((4, progI), (12, progA), (20, progC), (28, progA)):
        ostinato(bar, 8)
        s.pattern('drums', drive_, bar, 8, vel=0.8)
        s.hit('drums', 'crash', s.bar(bar), 0.9)
        for i in range(0, 8, 2):
            s.hit('timp', 'timpani', s.bar(bar + i), 0.8)
    s.chords_hold('choir', 'choir', progI, 4, center=60, vel=0.35)
    s.chords_hold('brass', 'brass', progI, 4, center=55, vel=0.35, n=3)
    s.melody('brass', 'brass', A, 12, vel=0.85)
    s.chords_hold('strings', 'strings', progA, 12, center=60, vel=0.3)
    s.melody('choir', 'choir', C, 20, vel=0.6)
    s.melody('brass', 'brass', C, 20, vel=0.5, shift=-12)
    for b in range(8):
        for k in range(16):
            m = voicing(progC[b], 72, 3)[k % 3]
            s.play('strings', 'strings_short', m, s.bar(20 + b) + k * 0.25, 0.25, 0.3)
    s.melody('brass', 'brass', A, 28, vel=0.95)
    s.melody('strings', 'strings', A, 28, vel=0.5, shift=12)
    s.chords_hold('choir', 'choir', progA, 28, center=60, vel=0.4)
    outro = ['Ab', 'Bb', 'G', 'Cm']
    s.chords_hold('brass', 'brass', outro, 36, center=58, vel=0.6, n=4)
    s.chords_hold('choir', 'choir', outro, 36, center=62, vel=0.45)
    ostinato(36, 3)
    s.pattern('drums', drive_, 36, 3, vel=0.85)
    s.hit('timp', 'timpani', s.bar(39), 1.2)
    s.hit('drums', 'crash', s.bar(39), 1.1)
    s.hit('drums', 'taiko', s.bar(39), 1.2)
    return s.render(path, rev=2.0, damp=0.5, rms=-19.5)


def sky(path):
    """'Above the Clouds' - a Lydian waltz in E for the sky islands."""
    s = Song('sky', bpm=88, beats_per_bar=3, bars=30, seed=17)
    s.mix.bus('flute', pan=-0.15, rev=0.45, gain=0.7)
    s.mix.bus('harp', pan=0.3, rev=0.45, gain=1.5)
    s.mix.bus('pad', rev=0.6, gain=0.5, width=0.9)
    s.mix.bus('bells', pan=-0.35, rev=0.55, gain=0.8)
    s.mix.bus('bass', rev=0.2, gain=0.55, lp=500)
    A = 'B5:2 G#5:1 | A#5:2 F#5:1 | G#5:1 B5:1 E6:1 | C#6:3 | E6:2 C#6:1 | B5:1 A5:1 C#6:1 | D#6:2 B5:1 | F#5:3'
    B = 'C#6:2 E6:1 | D#6:2 F#6:1 | E6:1 D#6:1 B5:1 | C#6:3 | A5:1 C#6:1 E6:1 | A#5:2 C#6:1 | D#6:2 C#6:1 | B5:3'
    progA = ['E', 'F#/E', 'E', 'F#/E', 'C#m', 'A', 'B', 'B']
    progB = ['A', 'B', 'G#m', 'C#m', 'A', 'F#', 'B', 'E']
    harp_seq = [0, 2, 3, 4, 3, 2]
    for bar, prog in ((0, ['E', 'F#/E']), (2, progA), (10, progB), (18, progA), (26, ['E', 'F#/E', 'E', 'E'])):
        s.arp('harp', 'harp', prog, bar, harp_seq, 0.5, center=60, vel=0.45)
        s.chords_hold('pad', 'pad', prog, bar, center=60, vel=0.3)
        s.bassline('bass', 'bass', prog, bar, [(0, 'R', 3)], octave=2, vel=0.55)
        for i in range(len(prog)):
            if i % 2 == 0:
                s.play('bells', 'bell', voicing(prog[i], 84, 3)[0], s.bar(bar + i), 2, 0.35)
    s.melody('flute', 'flute', A, 2, vel=0.7)
    s.melody('flute', 'flute', B, 10, vel=0.7)
    s.melody('bells', 'celesta', B, 10, vel=0.4, shift=-12)
    s.melody('bells', 'celesta', A, 18, vel=0.55)
    s.melody('flute', 'flute', A, 18, vel=0.5, shift=-12)
    s.chords_hold('pad', 'choir_oo', progA, 18, center=64, vel=0.25)
    s.play('bells', 'bell', note('E6'), s.bar(28), 3, 0.5)
    s.play('bells', 'bell', note('B6'), s.bar(29), 3, 0.35)
    return s.render(path, rev=3.2, damp=0.35, rms=-22)


def deep_sea(path):
    """'Beneath the Waves' - slow, cold and wide, for the deep sea and Fish-Man Island."""
    s = Song('deep_sea', bpm=64, beats_per_bar=4, bars=18, seed=18)
    s.mix.bus('pad', rev=0.55, gain=0.45, width=0.9, lp=3000)
    s.mix.bus('celesta', pan=-0.3, rev=0.6, gain=1.6)
    s.mix.bus('marimba', pan=0.3, rev=0.55, gain=1.0, lp=2500)
    s.mix.bus('sub', rev=0.1, gain=0.7, lp=200)
    s.mix.bus('whale', pan=0.2, rev=0.7, gain=0.35, lp=900)
    s.mix.bus('bubbles', pan=-0.2, rev=0.5, gain=0.18)
    M = 'E5:1.5 C5:.5 A4:2 | A4:1 C5:1 E5:2 | B4:1.5 C5:.5 E5:2 | G5:3 r:1 | F5:1.5 E5:.5 D5:2 | C5:2 A4:2 | E5:3 r:1 | G#4:4'
    prog = ['Am9', 'Fmaj7', 'Am9', 'Em7', 'Dm', 'Fmaj7', 'Am', 'E']
    s.chords_hold('pad', 'pad', ['Am9', 'Fmaj7'], 0, center=57, vel=0.4)
    for bar in (2, 10):
        s.chords_hold('pad', 'pad', prog, bar, center=57, vel=0.4)
        s.bassline('sub', 'bass', prog, bar, [(0, 'R', 3.5)], octave=1, vel=0.6)
    s.melody('celesta', 'celesta', M, 2, vel=0.5)
    s.melody('marimba', 'marimba', M, 10, vel=0.6, shift=-12)
    s.melody('celesta', 'celesta', M, 10, vel=0.25, shift=12)
    # whale calls: slow sine glides
    for bar, (f0, f1, f2) in ((3, (140, 95, 120)), (8, (120, 160, 100)), (13, (150, 90, 110))):
        n = secs(5.0)
        t = tt(n)
        fc = np.interp(t, [0, 2.0, 5.0], [f0, f1, f2])
        y = np.sin(TAU * np.cumsum(fc) / SR) * np.sin(np.pi * t / 5.0) ** 2
        y += 0.3 * np.sin(2 * TAU * np.cumsum(fc) / SR) * np.sin(np.pi * t / 5.0) ** 3
        s.mix.add('whale', y * 0.6, s.t(s.bar(bar)))
    # bubbles: short rising blips
    for _ in range(40):
        at = s.rng.uniform(1, s.length - 4)
        n = secs(0.06)
        t = tt(n)
        f = s.rng.uniform(600, 1400) * (1 + 3 * t / 0.06)
        y = np.sin(TAU * np.cumsum(f) / SR) * np.sin(np.pi * t / 0.06)
        s.mix.add('bubbles', y * s.rng.uniform(0.3, 0.8), at)
    s.chords_hold('pad', 'pad', ['Am9', 'Am9'], 16, center=57, vel=0.35)
    return s.render(path, rev=4.0, damp=0.7, rms=-23)


def wano(path):
    """'Land of Wano' - koto, shakuhachi, shamisen and taiko on the E miyako-bushi scale."""
    s = Song('wano', bpm=96, beats_per_bar=4, bars=28, seed=19)
    s.mix.bus('shaku', pan=-0.2, rev=0.45, gain=0.75)
    s.mix.bus('koto', pan=0.3, rev=0.35, gain=1.6)
    s.mix.bus('shamisen', pan=-0.35, rev=0.25, gain=1.8)
    s.mix.bus('taiko', rev=0.3, gain=0.45)
    s.mix.bus('pad', rev=0.5, gain=0.35, lp=1800)
    A = 'E5:2 F5:1 A5:1 | B5:3 A5:1 | C6:1.5 B5:.5 A5:1 F5:1 | E5:4 | A5:2 B5:1 C6:1 | D6:2 C6:1 B5:1 | A5:1 F5:1 E5:1 F5:1 | E5:4'
    B = ('E5:.5 E5:.5 F5:.5 A5:.5 B5:1 A5:1 | C6:.5 B5:.5 A5:.5 F5:.5 E5:2 | A5:.5 A5:.5 B5:.5 C6:.5 D6:1 C6:1 |'
         'B5:.5 A5:.5 F5:.5 E5:.5 F5:2 | E5:.5 E5:.5 F5:.5 A5:.5 B5:1 C6:1 | D6:.5 C6:.5 B5:.5 A5:.5 B5:2 |'
         'C6:.5 B5:.5 A5:.5 F5:.5 E5:1 F5:1 | E5:4')
    ost1 = ['E4', 'B4', 'E5', 'F5', 'E5', 'B4', 'A4', 'B4']
    ost2 = ['A3', 'E4', 'A4', 'B4', 'C5', 'B4', 'A4', 'E4']
    drums_a = {'taiko': 'X.....x.x.......', 'kotsuzumi': '............x...'}
    drums_b = {'taiko': 'X.....x.x...x.x.', 'kotsuzumi': '....x.......x..x', 'clave': 'x...x...x...x...'}

    def ostinato(bar, bars, notes, vel=0.45):
        for b in range(bars):
            for k, n_ in enumerate(notes):
                s.play('koto', 'koto', note(n_), s.bar(bar + b) + k * 0.5, 0.5, vel * (1.0 if k == 0 else 0.8))
    ostinato(0, 4, ost1)
    s.play('shaku', 'shakuhachi', note('E5'), s.bar(1), 7, 0.6)
    s.pattern('taiko', {'taiko': 'X...............'}, 0, 4, vel=0.8)
    ostinato(4, 8, ost1)
    s.pattern('taiko', drums_a, 4, 8, vel=0.8)
    s.melody('shaku', 'shakuhachi', A, 4, vel=0.75)
    s.play('pad', 'strings', note('E3'), s.bar(4), 16, 0.4)
    s.play('pad', 'strings', note('A3'), s.bar(8), 16, 0.4)
    ostinato(12, 8, ost2, vel=0.4)
    s.pattern('taiko', drums_b, 12, 8, vel=0.9)
    s.melody('shamisen', 'shamisen', B, 12, vel=0.8)
    s.melody('shaku', 'shakuhachi', A, 20, vel=0.75)
    s.melody('koto', 'koto', A, 20, vel=0.45, shift=-12)
    ostinato(20, 7, ost1, vel=0.35)
    s.pattern('taiko', drums_a, 20, 7, vel=0.85)
    s.play('pad', 'strings', note('E3'), s.bar(20), 28, 0.35)
    s.play('koto', 'koto', note('E4'), s.bar(27), 4, 0.7)
    s.play('koto', 'koto', note('B4'), s.bar(27) + 0.05, 4, 0.6)
    s.play('koto', 'koto', note('E5'), s.bar(27) + 0.1, 4, 0.6)
    s.hit('taiko', 'taiko', s.bar(27), 1.2)
    return s.render(path, rev=2.4, damp=0.5, rms=-21)


SONGS = {'east_blue': east_blue, 'grand_line': grand_line, 'new_world': new_world, 'island': island,
         'battle': battle, 'boss': boss, 'sky': sky, 'deep_sea': deep_sea, 'wano': wano}
