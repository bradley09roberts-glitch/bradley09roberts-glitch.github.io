"""Original sound effects for Grand Line Odyssey, synthesised from oscillators and noise (synth.py).

Each entry of SFX is name -> (function(rng) -> mono array, number of variants). Names become sound events
grandline:<name> and files assets/grandline/sounds/<name with . as />_<n>.ogg.
"""
import numpy as np

from synth import (SR, TAU, adsr, bandpass, drive, ffilter, inst_bell, inst_brass, inst_marimba, inst_harp, midi_hz,
                   note, noise, perc, phase, secs, sine, stft_filter, sweep_bp, sweep_lp, tt, drum, make_ir, convolve)


def place(total, *parts):
    """Mix (offset_seconds, signal) parts into one buffer of `total` seconds."""
    out = np.zeros(secs(total))
    for at, y in parts:
        i = secs(at) if at else 0
        y = y[:len(out) - i]
        out[i:i + len(y)] += y
    return out


def room(y, rng, seconds=0.8, mix=0.25, damp=0.6):
    ir = make_ir(rng, seconds, damp)[0]
    wet = convolve(y, ir)[:len(y)]
    return y * (1 - mix) + wet * mix * 3


def crackle(n, rng, density=60, hp=2500):
    """Sparse electric crackles: random impulses through a bright filter."""
    y = np.zeros(n)
    k = int(density * n / SR)
    idx = rng.integers(0, n, k)
    y[idx] = rng.uniform(-1, 1, k)
    return ffilter(y, hp=hp)


# ------------------------------------------------------------------------------------------------------------- Haki
def haki_conqueror(rng):
    n = secs(2.2)
    t = tt(n)
    sub = np.sin(phase(32 + 30 * np.exp(-t / 0.25), n)) * np.exp(-t / 0.9) * np.clip(t / 0.01, 0, 1)
    wave = sweep_lp(noise(n, rng, 'brown'), lambda s: 2200 * np.exp(-s / 0.35) + 120) * np.exp(-t / 0.6) * 1.4
    crack = crackle(n, rng, 90) * np.exp(-t / 0.5) * 0.9
    swell_n = secs(0.35)
    pre = sweep_lp(noise(swell_n, rng, 'pink'), lambda s: 200 + 2400 * s / 0.35) * np.linspace(0, 1, swell_n) ** 2 * 0.5
    y = place(2.55, (0, pre), (0.35, drive(sub * 1.2 + wave, 1.6) + crack))
    return room(y, rng, 1.6, 0.3)


def haki_armament(rng):
    n = secs(0.9)
    t = tt(n)
    f0 = 380
    y = np.zeros(n)
    for r, a, tau in ((1, 1, 0.35), (2.76, 0.6, 0.22), (5.40, 0.35, 0.12), (8.93, 0.2, 0.07)):
        y += a * np.sin(TAU * f0 * r * t + rng.uniform(0, TAU)) * np.exp(-t / tau)
    y *= np.clip(t / 0.002, 0, 1)
    thunk = np.sin(phase(90 + 60 * np.exp(-t / 0.02), n)) * np.exp(-t / 0.06)
    hiss = bandpass(rng.standard_normal(n), 3000, 9000) * np.exp(-t / 0.03) * 0.4
    return room(y * 0.6 + thunk * 0.8 + hiss, rng, 0.6, 0.2)


def haki_observation(rng):
    n = secs(1.2)
    t = tt(n)
    env = np.sin(np.pi * np.clip(t / 1.1, 0, 1)) ** 2
    y = np.zeros(n)
    for f in (1318.5, 1975.5, 2637.0, 3135.9):
        y += np.sin(phase(f * (1 + 0.004 * np.sin(TAU * 7 * t + rng.uniform(0, TAU))), n)) * rng.uniform(0.5, 1)
    air = sweep_bp(rng.standard_normal(n), lambda s: 1200 + 5000 * s / 1.2, q=3) * 0.5
    return room((y * 0.25 + air) * env, rng, 1.2, 0.35)


# -------------------------------------------------------------------------------------------------------- Devil Fruits
def fruit_rubber(rng):
    n = secs(0.55)
    t = tt(n)
    up = rng.uniform(0.9, 1.15)
    f = (170 + 380 * (1 - np.exp(-t / 0.12))) * up
    f = f * (1 + 0.08 * np.sin(TAU * 24 * t) * np.exp(-t / 0.2))
    y = np.sin(phase(f, n)) + 0.4 * np.sin(2 * phase(f, n)) + 0.15 * np.sin(3 * phase(f, n))
    y *= np.exp(-t / 0.22) * np.clip(t / 0.005, 0, 1)
    squeak = sweep_bp(rng.standard_normal(n), lambda s: 900 + 2600 * s / 0.55, q=8) * np.exp(-t / 0.15) * 0.5
    return y * 0.7 + squeak


def fruit_flame(rng):
    n = secs(1.1)
    t = tt(n)
    env = np.clip(t / 0.06, 0, 1) * np.exp(-np.maximum(0, t - 0.15) / 0.35)
    roar = sweep_lp(noise(n, rng, 'pink'), lambda s: 700 + 2500 * np.exp(-((s - 0.12) / 0.25) ** 2)) * env
    flick = ffilter(crackle(n, rng, 140, hp=1500), lp=6000) * env * 0.5
    low = ffilter(noise(n, rng, 'brown'), lp=180) * env * 0.8
    return roar + flick + low


def fruit_thunder(rng):
    n = secs(0.9)
    t = tt(n)
    crack = ffilter(rng.standard_normal(n), hp=800) * np.exp(-t / 0.012)
    jit = np.cumsum(rng.standard_normal(n)) * 0.4
    buzz_f = 95 * (1 + 0.15 * np.tanh(jit / 200))
    ph = phase(buzz_f, n)
    saw = 2 * ((ph / TAU) % 1) - 1
    buzz = bandpass(saw, 400, 5000) * np.exp(-t / 0.18) * 0.6
    tail = crackle(n, rng, 260, hp=1800) * np.exp(-t / 0.3) * 0.8
    boom = ffilter(noise(n, rng, 'brown'), lp=250) * np.exp(-t / 0.35) * 0.8
    return drive(crack * 1.3 + buzz + tail + boom, 1.5)


def fruit_ice(rng):
    n = secs(0.9)
    t = tt(n)
    y = np.zeros(n)
    for _ in range(26):
        at = rng.uniform(0, 0.45)
        f = rng.uniform(2200, 7000)
        i = secs(at)
        m = n - i
        tl = tt(m)
        y[i:] += np.sin(TAU * f * tl) * np.exp(-tl / rng.uniform(0.02, 0.08)) * rng.uniform(0.2, 0.6)
    crack = ffilter(rng.standard_normal(n), hp=1500) * np.exp(-t / 0.02)
    creak = sweep_bp(rng.standard_normal(n), lambda s: 600 + 300 * np.sin(s * 30), q=10) * np.exp(-t / 0.25) * 0.6
    return room(y * 0.6 + crack + creak, rng, 0.9, 0.3)


def fruit_dark(rng):
    n = secs(1.4)
    t = tt(n)
    swell = np.clip(t / 1.0, 0, 1) ** 3 * (t < 1.0) + (t >= 1.0) * np.exp(-(t - 1.0) / 0.05)
    suck = sweep_lp(noise(n, rng, 'pink'), lambda s: 3000 * (1 - s / 1.4) + 150) * swell
    sub = np.sin(phase(70 - 40 * np.clip(t / 1.0, 0, 1), n)) * swell * 0.8
    groan = np.sin(phase(55 * (1 + 0.02 * np.sin(TAU * 3 * t)), n)) * 0.3 * swell
    return drive(suck + sub + groan, 1.4)


def fruit_quake(rng):
    n = secs(1.6)
    t = tt(n)
    crack = ffilter(rng.standard_normal(n), hp=1200) * np.exp(-t / 0.03)
    ring = sum(np.sin(TAU * f * t) * np.exp(-t / 0.15) * 0.2 for f in (1630, 2410, 3370, 4120))
    thump = np.sin(phase(42 + 50 * np.exp(-t / 0.04), n)) * np.exp(-t / 0.5)
    rumble = ffilter(noise(n, rng, 'brown'), lp=140) * np.exp(-t / 0.7) * 1.2
    return drive(crack + ring + thump + rumble, 1.5)


def fruit_light(rng):
    n = secs(0.75)
    t = tt(n)
    f = 2400 + 1800 * np.exp(-t / 0.08)
    y = np.zeros(n)
    for det in (0.995, 1.0, 1.006, 1.5):
        y += np.sin(phase(f * det, n)) * (0.5 if det == 1.5 else 1)
    y *= np.exp(-t / 0.25) * np.clip(t / 0.003, 0, 1)
    sizzle = ffilter(rng.standard_normal(n), hp=5000) * np.exp(-t / 0.15) * 0.4
    return room(y * 0.35 + sizzle, rng, 0.8, 0.3)


def fruit_gas(rng):
    n = secs(1.0)
    t = tt(n)
    env = np.clip(t / 0.08, 0, 1) * np.exp(-np.maximum(0, t - 0.2) / 0.3)
    return sweep_bp(noise(n, rng, 'pink'), lambda s: 600 + 3400 * np.exp(-s / 0.4), q=1.2) * env * 1.5


def fruit_room(rng):
    n = secs(1.3)
    t = tt(n)
    env = np.clip(t / 0.5, 0, 1) ** 2 * np.exp(-np.maximum(0, t - 0.6) / 0.3)
    y = np.zeros(n)
    for m in (57, 64, 69, 76):
        f = midi_hz(m)
        y += np.sin(phase(f * (1 + 0.003 * np.sin(TAU * 4 * t)), n))
    whoosh = sweep_bp(rng.standard_normal(n), lambda s: 300 + 2000 * s / 1.3, q=2) * env * 0.6
    return room(y * env * 0.25 + whoosh, rng, 1.0, 0.35)


def fruit_shambles(rng):
    n = secs(0.35)
    t = tt(n)
    fwip = sweep_bp(rng.standard_normal(n), lambda s: 500 + 6000 * s / 0.2, q=3) * np.exp(-t / 0.1)
    click = ffilter(rng.standard_normal(n), hp=3000) * np.exp(-t / 0.004)
    tone = np.sin(phase(880 + 880 * t / 0.35, n)) * np.exp(-t / 0.08) * 0.3
    return fwip + click + tone


def fruit_transform(rng):
    n = secs(1.2)
    t = tt(n)
    env = np.clip(t / 0.15, 0, 1) * np.exp(-np.maximum(0, t - 0.4) / 0.3)
    f = 75 * (1 + 0.3 * np.clip(t / 0.4, 0, 1)) * (1 + 0.05 * np.sin(TAU * 31 * t))
    ph = phase(f, n)
    growl = (2 * ((ph / TAU) % 1) - 1)
    growl = ffilter(growl, peaks=((600, 9, 3), (1100, 6, 3)), lp=2500)
    whoosh = sweep_bp(rng.standard_normal(n), lambda s: 400 + 1500 * s, q=1.5) * env * 0.6
    return drive(growl * env * 0.8 + whoosh, 1.5)


def fruit_drums(rng):
    """The heartbeat drums of a liberated warrior: four double beats."""
    parts = []
    for k in range(4):
        base = k * 0.62
        parts.append((base, drum('taiko', 1.0, rng)))
        parts.append((base + 0.17, drum('taiko', 0.75, rng)))
    y = place(3.2, *parts)
    return room(y, rng, 1.2, 0.25)


# ------------------------------------------------------------------------------------------------------------- bosses
def boss_roar(rng):
    n = secs(2.2)
    t = tt(n)
    env = np.clip(t / 0.2, 0, 1) * np.exp(-np.maximum(0, t - 0.8) / 0.45)
    y = np.zeros(n)
    for f0 in (62, 93, 124):
        f = f0 * (1 + 0.06 * np.sin(TAU * 0.8 * t)) * (1 + 0.03 * np.cumsum(rng.standard_normal(n)) / np.sqrt(n))
        ph = phase(f, n)
        y += 2 * ((ph / TAU) % 1) - 1
    y = ffilter(y, peaks=((700, 10, 2.5), (1200, 7, 3), (2500, 4, 3)), lp=3500)
    breath = bandpass(rng.standard_normal(n), 400, 3000) * 0.5
    return room(drive((y * 0.5 + breath) * env, 2.2), rng, 1.4, 0.3)


def boss_phase(rng):
    """Orchestral stinger: low brass chord, timpani, cymbal."""
    parts = [(0, drum('timpani', 1.2, rng)), (0, drum('crash', 0.7, rng))]
    for m in (48, 55, 60, 63, 67):
        parts.append((0.02, inst_brass(midi_hz(m), 1.3, 0.9, rng)))
    y = place(2.6, *parts)
    return room(y, rng, 1.8, 0.3)


def boss_telegraph(rng):
    n = secs(1.1)
    t = tt(n)
    f = 220 * 2 ** (t / 1.1 * 1.5)
    tone = np.sin(phase(f, n)) + 0.3 * np.sin(2 * phase(f, n))
    trem = 0.6 + 0.4 * np.sin(TAU * (6 + 14 * t / 1.1) * t)
    swell = sweep_lp(noise(n, rng, 'pink'), lambda s: 400 + 4000 * s / 1.1) * 0.5
    env = np.clip(t / 1.0, 0, 1) ** 1.5 * np.where(t < 1.05, 1, np.exp(-(t - 1.05) / 0.01))
    return (tone * trem * 0.5 + swell) * env


def boss_enrage(rng):
    beat = []
    for k in range(3):
        beat.append((0.9 + k * 0.42, drum('kick', 1.0, rng) * 1.2))
        beat.append((0.9 + k * 0.42 + 0.14, drum('kick', 0.6, rng)))
    return place(2.6, (0, boss_roar(rng)[:secs(2.2)] * 0.8), *beat)


# ----------------------------------------------------------------------------------------------------------- the sea
def ship_cannon(rng):
    n = secs(1.8)
    t = tt(n)
    blast = ffilter(rng.standard_normal(n), lp=4000) * np.exp(-t / 0.03) * 1.5
    thump = np.sin(phase(38 + 70 * np.exp(-t / 0.03), n)) * np.exp(-t / 0.25) * 1.3
    rumble = ffilter(noise(n, rng, 'brown'), lp=300) * np.exp(-t / 0.6) * 1.2
    y = drive(blast + thump + rumble, 2.0)
    echo = np.concatenate([np.zeros(secs(0.28)), ffilter(y, lp=1500)[:n - secs(0.28)]]) * 0.25
    return y + echo


def ship_bell(rng):
    n = secs(3.0)
    t = tt(n)
    f = 740.0
    y = np.zeros(n)
    for r, a, tau in ((0.5, 0.5, 2.2), (1.0, 1.0, 1.6), (1.19, 0.6, 1.2), (1.5, 0.5, 1.0), (2.0, 0.35, 0.8),
                      (2.51, 0.25, 0.6), (3.01, 0.15, 0.4)):
        y += a * np.sin(TAU * f * r * t + rng.uniform(0, TAU)) * np.exp(-t / tau)
    strike = ffilter(rng.standard_normal(n), hp=2000) * np.exp(-t / 0.005) * 0.5
    return room(y * np.clip(t / 0.001, 0, 1) * 0.5 + strike, rng, 1.2, 0.2)


def ship_horn(rng):
    n = secs(3.2)
    t = tt(n)
    env = np.clip(t / 0.25, 0, 1) * np.where(t < 2.5, 1, np.exp(-(t - 2.5) / 0.2))
    f = 98 * (1 + 0.02 * np.clip(t / 0.3, 0, 1))
    ph = phase(f, n)
    y = sum(np.sin(k * ph) / k ** 0.7 for k in range(1, 14))
    y = ffilter(y, lp=1400, peaks=((350, 6, 2),))
    return room(drive(y * env * 0.5, 1.3), rng, 2.0, 0.3)


def sea_king_call(rng):
    n = secs(3.4)
    t = tt(n)
    f = np.interp(t, [0, 1.2, 2.6, 3.4], [140, 85, 115, 70])
    ph = phase(f, n)
    y = np.sin(ph) + 0.45 * np.sin(2 * ph) + 0.2 * np.sin(3 * ph)
    y = ffilter(y, lp=900, peaks=((250, 6, 2),))
    env = np.sin(np.pi * np.clip(t / 3.3, 0, 1)) ** 1.5
    rumble = ffilter(noise(n, rng, 'brown'), lp=120) * 0.6
    return room((y * 0.6 + rumble) * env, rng, 2.5, 0.45, damp=0.85)


# ----------------------------------------------------------------------------------------------------- Den Den Mushi
def den_den_ring(rng):
    """'Puru puru puru': a fast warbling trill in two bursts."""
    n = secs(1.9)
    t = tt(n)
    trill = 0.5 + 0.5 * np.sign(np.sin(TAU * 17 * t))
    f = 980 + 260 * ffilter(trill, lp=120)
    y = np.sin(phase(f, n)) + 0.3 * np.sin(2 * phase(f, n))
    gate = ((t > 0.02) & (t < 0.75)) | ((t > 1.05) & (t < 1.78))
    gate = ffilter(gate.astype(float), lp=60)
    return y * gate * 0.6


def den_den_pickup(rng):
    """'Gacha': the receiver lifts - two damped clicks."""
    def click(f):
        m = secs(0.08)
        tl = tt(m)
        return (ffilter(rng.standard_normal(m), hp=800, peaks=((f, 12, 4),)) * np.exp(-tl / 0.012))
    return place(0.3, (0, click(1800)), (0.07, click(1300) * 0.8))


# ---------------------------------------------------------------------------------------------------------------- UI
def ui_bounty(rng):
    """A short brass fanfare and a cymbal: your bounty went up."""
    parts = [(0.0, inst_brass(midi_hz(60), 0.18, 0.9, rng)), (0.16, inst_brass(midi_hz(64), 0.18, 0.9, rng)),
             (0.32, inst_brass(midi_hz(67), 0.18, 0.9, rng))]
    for m in (60, 64, 67, 72):
        parts.append((0.48, inst_brass(midi_hz(m), 0.9, 1.0, rng)))
    parts.append((0.48, drum('crash', 0.6, rng)))
    parts.append((0.48, drum('timpani', 0.8, rng)))
    return room(place(2.0, *parts), rng, 1.2, 0.25)


def ui_quest(rng):
    parts = [(i * 0.1, inst_marimba(midi_hz(m), 0.4, 0.9, rng)) for i, m in enumerate((67, 71, 74, 79))]
    parts.append((0.3, inst_bell(midi_hz(91), 0.8, 0.4, rng, tau=0.8)))
    return room(place(1.4, *parts), rng, 1.0, 0.3)


def ui_discover(rng):
    parts = [(i * 0.06, inst_harp(midi_hz(m), 1.2, 0.7, rng)) for i, m in enumerate((62, 66, 69, 74, 78, 81, 86))]
    parts.append((0.45, inst_bell(midi_hz(86), 1.4, 0.5, rng, tau=1.2)))
    return room(place(2.4, *parts), rng, 1.8, 0.35)


def player_knockdown(rng):
    def beat(v):
        m = secs(0.25)
        tl = tt(m)
        return np.sin(phase(55 + 30 * np.exp(-tl / 0.02), m)) * np.exp(-tl / 0.07) * v
    y = place(1.8, (0, beat(1)), (0.16, beat(0.7)), (0.9, beat(0.85)), (1.06, beat(0.6)))
    return ffilter(y, lp=400)


def combat_soru(rng):
    n = secs(0.35)
    t = tt(n)
    w = sweep_bp(rng.standard_normal(n), lambda s: 5000 * np.exp(-s / 0.08) + 400, q=1.5)
    return w * np.clip(t / 0.01, 0, 1) * np.exp(-t / 0.08) * 1.2


def combat_geppo(rng):
    n = secs(0.3)
    t = tt(n)
    thump = np.sin(phase(120 + 200 * np.exp(-t / 0.01), n)) * np.exp(-t / 0.05)
    puff = bandpass(rng.standard_normal(n), 300, 2500) * np.exp(-t / 0.04) * 0.7
    return thump + puff


SFX = {
    'haki.conqueror': (haki_conqueror, 2), 'haki.armament': (haki_armament, 2), 'haki.observation': (haki_observation, 1),
    'fruit.rubber': (fruit_rubber, 3), 'fruit.flame': (fruit_flame, 3), 'fruit.thunder': (fruit_thunder, 3),
    'fruit.ice': (fruit_ice, 2), 'fruit.dark': (fruit_dark, 2), 'fruit.quake': (fruit_quake, 2),
    'fruit.light': (fruit_light, 2), 'fruit.gas': (fruit_gas, 2), 'fruit.room': (fruit_room, 1),
    'fruit.shambles': (fruit_shambles, 2), 'fruit.transform': (fruit_transform, 2), 'fruit.drums': (fruit_drums, 1),
    'boss.roar': (boss_roar, 2), 'boss.phase': (boss_phase, 1), 'boss.telegraph': (boss_telegraph, 1),
    'boss.enrage': (boss_enrage, 1), 'ship.cannon': (ship_cannon, 3), 'ship.bell': (ship_bell, 1),
    'ship.horn': (ship_horn, 1), 'sea_king.call': (sea_king_call, 2), 'den_den.ring': (den_den_ring, 1),
    'den_den.pickup': (den_den_pickup, 1), 'ui.bounty': (ui_bounty, 1), 'ui.quest': (ui_quest, 1),
    'ui.discover': (ui_discover, 1), 'player.knockdown': (player_knockdown, 1), 'combat.soru': (combat_soru, 2),
    'combat.geppo': (combat_geppo, 2),
}


# ------------------------------------------------------------------------------------------------- ambience (client)
def _loop(st, xfade):
    """Make a stereo buffer loop seamlessly: crossfade its last `xfade` seconds into its start."""
    k = secs(xfade)
    head, body, tail = st[:, :k], st[:, k:-k], st[:, -k:]
    w = np.linspace(0, 1, k)
    return np.concatenate([tail * (1 - w) + head * w, body], axis=1)


def ambient_sea(rng):
    """Open sea: slow swells of surf over a low rumble, wide stereo (20 s loop)."""
    total = 22.0
    n = secs(total)
    t = tt(n)
    chans = []
    for c in range(2):
        base = ffilter(noise(n, rng, 'pink'), lp=900, hp=60)
        swell = np.zeros(n)
        at = rng.uniform(0, 2)
        while at < total:  # waves every 4-8 s, each a rise and a long wash
            period = rng.uniform(4, 8)
            env = np.exp(-0.5 * ((t - at - 1.2) / 0.9) ** 2) + 0.5 * np.exp(-np.maximum(0, t - at - 1.2) / 2.5) * (t > at + 1.2)
            swell += env * rng.uniform(0.6, 1.0)
            at += period
        wash = ffilter(rng.standard_normal(n), hp=1500, lp=7000) * swell * 0.25
        rumble = ffilter(noise(n, rng, 'brown'), lp=120) * 0.8
        chans.append(base * (0.35 + 0.65 * swell) + wash + rumble)
    st = np.stack(chans)
    return _loop(st, 2.0)


def ambient_wind(rng):
    """Storm wind: gusting, whistling, wide stereo (20 s loop)."""
    total = 22.0
    n = secs(total)
    t = tt(n)
    gust = 0.55 + 0.45 * np.sin(TAU * 0.11 * t + rng.uniform(0, TAU)) * np.sin(TAU * 0.037 * t + rng.uniform(0, TAU))
    chans = []
    for c in range(2):
        w = sweep_bp(noise(n, rng, 'pink'), lambda s: 500 + 700 * (0.5 + 0.5 * np.sin(TAU * 0.09 * s + c)), q=1.2)
        whistle = sweep_bp(rng.standard_normal(n), lambda s: 1400 + 500 * np.sin(TAU * 0.13 * s + 2 * c), q=14) * 0.35
        chans.append((w + whistle) * gust + ffilter(noise(n, rng, 'brown'), lp=200) * 0.5)
    return _loop(np.stack(chans), 2.0)


def ambient_gull(rng):
    """A gull's call: two or three falling squawks."""
    parts = []
    at = 0.0
    for k in range(int(rng.integers(2, 4))):
        n = secs(rng.uniform(0.18, 0.3))
        t = tt(n)
        f0 = rng.uniform(1500, 2000)
        f = f0 * (1 + 0.25 * np.sin(np.pi * t / t[-1])) * (1 - 0.3 * t / t[-1])
        ph = phase(f, n)
        y = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.3 * np.sin(3 * ph) + 0.15 * np.sin(4 * ph)
        y += bandpass(rng.standard_normal(n), 2000, 6000) * 0.3
        y *= np.sin(np.pi * t / t[-1]) ** 0.7
        parts.append((at, y * (1 - 0.15 * k)))
        at += len(y) / SR + rng.uniform(0.05, 0.12)
    return room(place(at + 0.4, *parts), rng, 1.2, 0.25)


def ambient_creak(rng):
    """Ship timbers: a slow, rough creak (stick-slip friction on a resonant body)."""
    n = secs(rng.uniform(0.6, 1.1))
    t = tt(n)
    rate = rng.uniform(25, 60) * (1 + 0.4 * np.sin(np.pi * t / t[-1]))
    clicks = np.zeros(n)
    pos = 0.0
    while pos < t[-1]:
        i = int(pos * SR)
        clicks[i] = rng.uniform(0.5, 1.0)
        pos += 1.0 / np.interp(pos, t, rate) * rng.uniform(0.85, 1.15)
    body = ffilter(clicks, peaks=((rng.uniform(220, 320), 18, 6), (rng.uniform(600, 900), 12, 6), (1500, 6, 4)), lp=2500, hp=120)
    env = np.sin(np.pi * t / t[-1]) ** 0.6
    return body * env


AMBIENT = {'ambient.sea': ambient_sea, 'ambient.wind': ambient_wind}  # stereo loops
SFX.update({'ambient.gull': (ambient_gull, 3), 'ambient.creak': (ambient_creak, 4)})
