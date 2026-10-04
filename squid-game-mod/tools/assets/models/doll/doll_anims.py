"""The doll's animations (contract section 1.3).

Authoring convention (JSON degrees): head rotation = [pitch (+ nod forward), yaw (+ turn to the doll's right),
roll (+ tilt towards the doll's left)].  All rotation values are OFFSETS from the bind pose stored in the geo
(only the arms have a bind rotation: 8 deg outward).  Position is in model px (x = doll's left, y up, z back).

Pose anchors - the animations are built so that transitions never pop (validate_doll.py checks all of them):
  PLAYERS rest = idle_players(0) = turn_to_players(end) = turn_to_tree(start) = lock_on(0) = scan_players(0/end)
  TREE    rest = idle_tree(0)    = turn_to_players(0) = turn_to_tree(end) = wake(end)
  DORMANT rest = dormant(0)      = wake(0)
(the game plays DORMANT -> wake -> idle_tree, so wake ends facing the tree: head lifts, stares at the field for a
beat, then ratchets round to the tree)

The head is authored by hand (stepped servo moves).  Secondary motion (cog ring that follows the head with a
mechanical lag, pigtail inertia, tiny body reaction torque) is SIMULATED from the head track with springs and
baked into keyframes, so it stays in sync whatever playback speed Java picks.
"""
from __future__ import annotations

import math

import numpy as np

import anim as an
from anim import Anim, Track

PI = math.pi
TAU = 2 * PI


def S(t, period, phase=0.0):
    return math.sin(TAU * t / period + phase)


def bob(t, period=0.5):
    """0 -> 1 -> 0 once per `period` (starts at 0)."""
    return 0.5 * (1.0 - math.cos(TAU * t / period))


def v3(a=0.0, b=0.0, c=0.0):
    return np.array([a, b, c], dtype=float)


def _smooth(x):
    x = min(max(x, 0.0), 1.0)
    return x * x * (3 - 2 * x)


def emit(A, bone, ch, fn, t0, t1, dt=1 / 40.0, tol=0.04, loop=False):
    keys = an.sample_fn(fn, t0, t1, dt)
    if loop:
        keys[-1] = (keys[-1][0], keys[0][1], keys[-1][2])
    A.set_track(bone, ch, an.simplify(keys, tol))


def _as_v3(v):
    if isinstance(v, np.ndarray):
        return v
    if isinstance(v, (int, float)):
        return v3(v, v, v)
    return v3(*v)


def keys_from(points):
    """[(t, value, easing)] -> Track keys with vec3 values (a bare number means all three axes)."""
    return [(t, _as_v3(v), e) for t, v, e in points]


# ------------------------------------------------------------------------------------------------
# anchoring: make an animation start / end exactly on another animation's pose
# ------------------------------------------------------------------------------------------------
def anchor(A, start=None, end=None, win=0.1):
    """start / end = (Anim, t) giving the pose to pin the first / last frame to (smoothly blended in)."""
    pairs = set(A.tracks)
    for src in (start, end):
        if src:
            pairs |= set(src[0].tracks)
    for bone, ch in sorted(pairs):
        default = (1.0, 1.0, 1.0) if ch == "scale" else (0.0, 0.0, 0.0)
        want0 = start[0].sampler(bone, ch, default)(start[1]) if start else None
        wantL = end[0].sampler(bone, ch, default)(end[1]) if end else None
        tr = A.tracks.get((bone, ch))
        cur = (lambda t: np.array(default)) if tr is None else tr.sampler()
        keys = [] if tr is None else tr.sorted_keys()
        L = A.length
        v0, vL = cur(0.0), cur(L)
        c0 = None if want0 is None else want0 - v0
        cL = None if wantL is None else wantL - vL
        if (c0 is None or np.max(np.abs(c0)) < 1e-7) and (cL is None or np.max(np.abs(cL)) < 1e-7):
            continue
        if not keys:
            keys = [(0.0, v0, None), (L, vL, None)]
        else:
            if keys[0][0] > 1e-9:
                keys.insert(0, (0.0, v0, None))
            if keys[-1][0] < L - 1e-9:
                keys.append((L, vL, None))
        out = []
        for t, v, e in keys:
            v = np.array(v, dtype=float)
            if c0 is not None:
                v = v + c0 * (1 - _smooth(t / win))
            if cL is not None:
                v = v + cL * _smooth((t - (L - win)) / win)
            out.append((t, v, e))
        A.set_track(bone, ch, out)


# ------------------------------------------------------------------------------------------------
# head-driven secondary motion
# ------------------------------------------------------------------------------------------------
class HeadDyn:
    """Springs driven by the head yaw: cog ring (follower), flange, pigtails (inertia), body reaction."""

    def __init__(self, head_fn, length, loop, h=1 / 400.0):
        self.h, self.L, self.loop = h, length, loop
        self.passes = 4 if loop else 1
        self.n = int(round(length / h))
        ts = np.arange(self.n * self.passes + 1) * h
        self.yaw = np.array([head_fn((t % length) if loop else min(t, length))[1] for t in ts])
        ker = np.ones(3) / 3
        self.vel = np.convolve(np.gradient(self.yaw, h), ker, mode="same")
        self.acc = np.convolve(np.gradient(self.vel, h), ker, mode="same")

    def take(self, arr):
        return np.arange(self.n + 1) * self.h, np.asarray(arr)[-(self.n + 1):]

    def ring(self, ratio, omega, zeta):
        r = an.spring_follow(ratio * self.yaw, omega, zeta, self.h)
        return r, self.yaw - r

    def pigtail(self, x_p, gain, omega=TAU * 2.7, zeta=0.14, L=9.0, limit=26.0):
        """x_p = baked x (px) of the tie (+ = doll's right).  Pendulum hanging from a moving pivot."""
        psi_vel = -np.radians(self.vel)           # baked yaw rate (rad/s)
        psi_acc = -np.radians(self.acc)
        uz = an.spring_drive(gain * x_p * psi_acc, omega, zeta, self.h)        # tangential inertia
        ux = an.spring_drive(gain * (psi_vel ** 2) * x_p, omega, zeta, self.h)  # centripetal fling
        return an.softclip(np.degrees(uz / L), limit), an.softclip(np.degrees(ux / L), limit)

    def body_yaw(self, gain=0.0095, omega=16.0, zeta=0.25, limit=1.2):
        return an.softclip(an.spring_drive(-gain * self.acc, omega, zeta, self.h), limit)


def add_followers(A, head_fn, loop, *, dt=1 / 50.0, ring=True, pigtails=True, body=True, ring_ratio=0.5,
                  ring_omega=48.0, ring_zeta=0.32, pig_gain=0.085, tol=0.05):
    L = A.length
    dyn = HeadDyn(head_fn, L, loop)

    def put(bone, series_by_axis, tol_):
        tt = np.arange(dyn.n + 1) * dyn.h
        cols = []
        for s in series_by_axis:
            _, ss = an.resample(tt, s, 0, L, dt)
            cols.append(ss)
        tr, _ = an.resample(tt, series_by_axis[0], 0, L, dt)
        keys = [(t, v3(*(c[i] for c in cols)), None) for i, t in enumerate(tr)]
        if loop:
            keys[-1] = (keys[-1][0], keys[0][1], None)
        A.set_track(bone, "rotation", an.simplify(keys, tol_))

    zero = np.zeros(dyn.n + 1)
    if ring:
        r, f = dyn.ring(ring_ratio, ring_omega, ring_zeta)
        put("neck_joint", [zero, dyn.take(r)[1], zero], tol)
        put("neck_ring", [zero, dyn.take(f)[1], zero], tol)
    if pigtails:
        for bone, xp in (("pigtail_r", 22.5), ("pigtail_l", -22.5)):
            ax, az = dyn.pigtail(xp, pig_gain)
            put(bone, [dyn.take(ax)[1], zero, dyn.take(az)[1]], tol)
    if body:
        put("body", [zero, dyn.take(dyn.body_yaw())[1], zero], 0.02)
    return dyn


def add_to_track(A, bone, ch, fn, dt=1 / 50.0, tol=0.02, loop=False):
    """Add fn(t) on top of an existing track (sampled at dt)."""
    old = A.sampler(bone, ch, (1, 1, 1) if ch == "scale" else (0, 0, 0))
    L = A.length
    n = int(round(L / dt))
    keys = [(i * L / n, old(i * L / n) + fn(i * L / n), None) for i in range(n + 1)]
    if loop:
        keys[-1] = (keys[-1][0], keys[0][1], None)
    A.set_track(bone, ch, an.simplify(keys, tol))


def eye_scale(A, fn, dt=1 / 40.0, loop=False, tol=0.004):
    for b in ("eye_on_l", "eye_on_r"):
        emit(A, b, "scale", lambda t: v3(fn(t), fn(t), 1.0), 0, A.length, dt, tol, loop)


def head_track(A, yaw_pts, pitch_pts, roll_pts):
    """Author the head from three scalar key lists [(t, value, easing)].  Keys are kept as authored (crisp servo
    steps); the three axes are merged on the union of their key times."""
    sy = Track([(t, v3(0, y, 0), e) for t, y, e in yaw_pts]).sampler()
    sp = Track([(t, v3(p, 0, 0), e) for t, p, e in pitch_pts]).sampler()
    sr = Track([(t, v3(0, 0, r), e) for t, r, e in roll_pts]).sampler()
    head = lambda t: v3(sp(t)[0], sy(t)[1], sr(t)[2])
    easing = {}
    for pts in (yaw_pts, pitch_pts, roll_pts):
        for t, _, e in pts:
            if e and not easing.get(round(t, 4)):
                easing[round(t, 4)] = e
    times = sorted({round(t, 4) for pts in (yaw_pts, pitch_pts, roll_pts) for t, _, _ in pts})
    A.set_track("head", "rotation", [(t, head(t), easing.get(t)) for t in times])
    return head


# ================================================================================================
# 1. dormant  (loop 6 s): eyes off, head bowed, slumped, tiny sway
# ================================================================================================
DORM_HEAD = v3(13.0, 0.0, -3.5)
DORM_BODY_X = 1.8


def build_dormant(ctx):
    L = 6.0
    A = Anim("dormant", L, True)
    emit(A, "body", "rotation", lambda t: v3(DORM_BODY_X + 0.3 * S(t, 3), 0.0, 0.45 * S(t, 6)), 0, L, loop=True)
    emit(A, "body", "position", lambda t: v3(0, -0.12 * S(t, 3), 0), 0, L, loop=True)
    head = lambda t: v3(DORM_HEAD[0] + 0.8 * S(t, 3), 1.0 * S(t, 6), DORM_HEAD[2] + 0.6 * S(t, 6))
    emit(A, "head", "rotation", head, 0, L, loop=True)
    emit(A, "neck_joint", "rotation", lambda t: v3(0, 0.5 * head(t)[1], 0), 0, L, loop=True)
    emit(A, "neck_ring", "rotation", lambda t: v3(0, 0.5 * head(t)[1], 0), 0, L, loop=True)
    emit(A, "right_arm", "rotation", lambda t: v3(-5 + 0.9 * S(t, 3), 0, -2 + 0.5 * S(t, 6)), 0, L, loop=True)
    emit(A, "left_arm", "rotation", lambda t: v3(-5 - 0.9 * S(t, 3), 0, 2 - 0.5 * S(t, 6)), 0, L, loop=True)
    emit(A, "dress", "rotation", lambda t: v3(0, 0, -0.3 * S(t, 6)), 0, L, loop=True)
    emit(A, "pigtail_r", "rotation", lambda t: v3(1.6 * S(t, 3), 0, 1.0 * S(t, 6)), 0, L, loop=True)
    emit(A, "pigtail_l", "rotation", lambda t: v3(1.6 * S(t, 3), 0, -1.0 * S(t, 6)), 0, L, loop=True)
    return A


# ================================================================================================
# 3. idle_tree (loop 4 s): head turned 180 deg towards the tree, gentle "singing" bob (one nod per syllable)
# ================================================================================================
TREE_YAW = 180.0


def build_idle_tree(ctx):
    L = 4.0
    A = Anim("idle_tree", L, True)
    yaw = lambda t: TREE_YAW + 2.4 * S(t, 2)
    emit(A, "head", "rotation", lambda t: v3(2.0 + 1.3 * S(t, 4) + 2.4 * bob(t), yaw(t), -3.6 * S(t, 2)), 0, L, loop=True)
    emit(A, "neck_joint", "rotation", lambda t: v3(0, 0.5 * yaw(t), 0), 0, L, loop=True)
    emit(A, "neck_ring", "rotation", lambda t: v3(0, 0.5 * yaw(t), 0), 0, L, loop=True)
    emit(A, "body", "rotation", lambda t: v3(0.0, 0.0, 1.0 * S(t, 2)), 0, L, loop=True)
    emit(A, "body", "position", lambda t: v3(0.0, 0.7 * bob(t), 0.0), 0, L, loop=True)
    emit(A, "right_arm", "rotation", lambda t: v3(2.6 * S(t, 2), 0.0, -1.0 * S(t, 2)), 0, L, loop=True)
    emit(A, "left_arm", "rotation", lambda t: v3(-2.6 * S(t, 2), 0.0, 1.0 * S(t, 2)), 0, L, loop=True)
    emit(A, "dress", "rotation", lambda t: v3(0.0, 0.0, -0.7 * S(t, 2)), 0, L, loop=True)
    emit(A, "mouth", "scale", lambda t: v3(1.0 - 0.14 * bob(t), 1.0 + 1.05 * bob(t) ** 1.4, 1.0), 0, L, loop=True, tol=0.01)
    emit(A, "ribbon", "rotation", lambda t: v3(0.0, 0.0, 2.6 * S(t, 0.5)), 0, L, loop=True)
    emit(A, "pigtail_r", "rotation", lambda t: v3(3.2 * S(t, 0.5), 0, 1.8 * S(t, 2)), 0, L, loop=True)
    emit(A, "pigtail_l", "rotation", lambda t: v3(3.2 * S(t, 0.5), 0, -1.8 * S(t, 2)), 0, L, loop=True)
    return A


# ================================================================================================
# 5. idle_players (loop 3 s): rigid, facing the field, tiny sudden micro-movements; eyes pulse
# ================================================================================================
def build_idle_players(ctx):
    L = 3.0
    A = Anim("idle_players", L, True)
    yaw = Track([(0.0, v3(0), None), (0.60, v3(0), None), (0.64, v3(0, 0.9, 0), "easeOutQuad"), (1.05, v3(0, 0.9, 0), None),
                 (1.11, v3(0, -0.15, 0), "easeInOutSine"), (1.20, v3(0), "easeInOutSine"), (1.70, v3(0), None),
                 (1.73, v3(0, -0.8, 0), "easeOutQuad"), (2.10, v3(0, -0.8, 0), None), (2.18, v3(0), "easeInOutSine"),
                 (3.0, v3(0), None)]).sampler()
    pit = Track([(0.0, v3(0), None), (2.40, v3(0), None), (2.43, v3(0.75, 0, 0), "easeOutQuad"), (2.60, v3(0.75, 0, 0), None),
                 (2.68, v3(0), "easeInOutSine"), (3.0, v3(0), None)]).sampler()
    rol = Track([(0.0, v3(0), None), (1.30, v3(0), None), (1.33, v3(0, 0, 0.6), "easeOutQuad"), (1.50, v3(0, 0, 0.6), None),
                 (1.56, v3(0), "easeInOutSine"), (3.0, v3(0), None)]).sampler()
    head = lambda t: v3(pit(t)[0], yaw(t)[1], rol(t)[2])
    emit(A, "head", "rotation", head, 0, L, 1 / 60.0, 0.01, loop=True)
    add_followers(A, head, True, dt=1 / 60.0, body=False, pig_gain=0.5, ring_zeta=0.5)
    eye_scale(A, lambda t: 1.0 + 0.055 * bob(t, 1.5), 1 / 40.0, True)
    emit(A, "body", "position", lambda t: v3(0, 0.07 * S(t, 1.5), 0), 0, L, 1 / 30.0, 0.01, loop=True)
    return A


# ================================================================================================
# 2. wake (once 2.0 s): power-up shudder, head lifts in servo steps, a stare, then the head ratchets round to the
#    tree.  The game plays DORMANT -> wake -> idle_tree, so wake ENDS on idle_tree's first frame (no pop / whip).
# ================================================================================================
def build_wake(ctx):
    L = 2.0
    A = Anim("wake", L, False)
    rng = np.random.RandomState(1021)
    env = lambda t: math.sin(PI * min(max((t - 0.20) / 0.42, 0.0), 1.0)) ** 1.1      # shudder envelope 0.20 .. 0.62
    n_t = int(L / 0.04) + 2
    nz = {k: rng.uniform(-1, 1, size=n_t) for k in ("hx", "hy", "hz", "bx", "bz", "ax", "ay", "px", "pz", "rg")}
    nn = lambda key, t: nz[key][int(min(max(round(t / 0.04), 0), n_t - 1))]
    d = DORM_HEAD
    tree = ctx["idle_tree"].sampler("head", "rotation")(0.0)          # (2, 180, 0)

    yaw = Track(keys_from([
        (0.0, 0, None), (1.28, 0, None), (1.33, -3.0, "easeInOutSine"),
        (1.38, 45.0, "easeOutQuad"), (1.40, 43.6, "easeOutQuad"), (1.49, 43.6, None),
        (1.54, 88.5, "easeOutQuad"), (1.56, 87.4, "easeOutQuad"), (1.65, 87.4, None),
        (1.70, 132.5, "easeOutQuad"), (1.72, 131.4, "easeOutQuad"), (1.80, 131.4, None),
        (1.86, 186.0, "easeOutQuad"), (1.91, 178.5, "easeInOutSine"), (1.955, 181.0, "easeInOutSine"), (2.0, tree[1], "easeInOutSine")])).sampler()
    pitch = Track(keys_from([
        (0.0, d[0], None), (0.20, d[0], None), (0.62, d[0] - 0.5, "easeInOutSine"),
        (0.68, 9.0, "easeOutQuad"), (0.73, 9.0, None), (0.79, 5.0, "easeOutQuad"), (0.84, 5.0, None),
        (0.91, -4.5, "easeOutQuad"), (0.97, -4.5, None), (1.12, tree[0], "easeInOutSine"), (1.28, tree[0], None),
        (1.33, 1.0, "easeInOutSine"), (1.38, 3.8, "easeOutQuad"), (1.49, 2.0, "easeInOutSine"), (1.54, 3.6, "easeOutQuad"),
        (1.65, 1.8, "easeInOutSine"), (1.70, 3.8, "easeOutQuad"), (1.80, 1.6, "easeInOutSine"), (1.86, 6.4, "easeOutQuad"),
        (1.91, -1.0, "easeInOutSine"), (1.955, 2.8, "easeInOutSine"), (2.0, tree[0], "easeInOutSine")])).sampler()
    roll = Track(keys_from([
        (0.0, d[2], None), (0.62, d[2], None), (0.97, 0.0, "easeInOutSine"), (1.28, 0.0, None), (1.33, -0.6, "easeInOutSine"),
        (1.38, 2.5, "easeOutQuad"), (1.49, 0.9, "easeInOutSine"), (1.54, -2.2, "easeOutQuad"), (1.65, -0.7, "easeInOutSine"),
        (1.70, 2.0, "easeOutQuad"), (1.80, 0.5, "easeInOutSine"), (1.86, -3.0, "easeOutQuad"), (1.91, 1.2, "easeInOutSine"),
        (1.955, -0.5, "easeInOutSine"), (2.0, tree[2], "easeInOutSine")])).sampler()
    head = lambda t: v3(pitch(t)[0] + 2.0 * env(t) * nn("hx", t), yaw(t)[1] + 2.2 * env(t) * nn("hy", t),
                        roll(t)[2] + 1.7 * env(t) * nn("hz", t))
    emit(A, "head", "rotation", head, 0, L, 1 / 100.0, 0.03)
    lean = Track(keys_from([(0.0, DORM_BODY_X, None), (0.62, DORM_BODY_X, None), (1.0, -0.7, "easeInOutSine"),
                            (1.2, 0.5, "easeInOutSine"), (1.5, 0.0, "easeInOutSine"), (2.0, 0.0, None)])).sampler()
    emit(A, "body", "rotation", lambda t: v3(lean(t)[0] + 0.7 * env(t) * nn("bx", t), 0.0,
                                              1.35 * env(t) * ((-1) ** int(t / 0.04)) * (0.7 + 0.3 * abs(nn("bz", t)))), 0, L, 1 / 50.0, 0.03)
    emit(A, "body", "position", lambda t: v3(0.5 * env(t) * nn("px", t), 0.35 * env(t) * abs(nn("bx", t)), 0), 0, L, 1 / 50.0, 0.02)
    # cog ring + pigtails follow the head (turning 0 -> 180 at the end)
    add_followers(A, head, False, dt=1 / 60.0, ring_zeta=0.3, body=False, pig_gain=0.07)
    add_to_track(A, "neck_joint", "rotation", lambda t: v3(0, 3.0 * env(t) * nn("rg", t), 0), 1 / 50.0, 0.04)

    def arm(sgn):
        base = Track(keys_from([(0.0, (-5.0, 0, -2.0 * sgn), None), (0.80, (-5.0, 0, -2.0 * sgn), "easeInOutSine"),
                                (1.00, (-10.0, 0, 4.5 * sgn), "easeOutQuad"), (1.20, (2.0, 0, -0.8 * sgn), "easeInOutSine"),
                                (1.40, (-1.0, 0, 0.4 * sgn), "easeInOutSine"), (1.65, (0.3, 0, 0), "easeInOutSine"),
                                (2.0, 0, "easeInOutSine")])).sampler()
        return lambda t: base(t) + v3(5.5 * env(t) * nn("ax", t + (0.02 if sgn > 0 else 0.0)), 0, sgn * 2.2 * env(t) * abs(nn("ay", t + 0.08 * sgn)))   # flutter only outwards

    emit(A, "right_arm", "rotation", arm(1), 0, L, 1 / 50.0, 0.04)
    emit(A, "left_arm", "rotation", arm(-1), 0, L, 1 / 50.0, 0.04)
    emit(A, "dress", "rotation", lambda t: v3(0, 0, 0.9 * env(t) * nn("bz", t + 0.04)), 0, L, 1 / 50.0, 0.03)
    emit(A, "ribbon", "rotation", lambda t: v3(0, 0, 5.0 * env(t) * nn("pz", t)), 0, L, 1 / 50.0, 0.04)
    anchor(A, (ctx["dormant"], 0.0), (ctx["idle_tree"], 0.0))
    return A


# ================================================================================================
# 4./8. turns: head yaw in four servo moves (3 pauses), a wind-up twitch, over-shoot and settle
# ================================================================================================
TURN_TO_PLAYERS = dict(
    yaw=[(0.0, 180.0, None), (0.06, 183.5, "easeInOutSine"),
         (0.13, 135.0, "easeOutQuad"), (0.165, 133.2, "easeOutQuad"), (0.285, 133.2, None),
         (0.355, 90.0, "easeOutQuad"), (0.385, 88.6, "easeOutQuad"), (0.50, 88.6, None),
         (0.57, 45.5, "easeOutQuad"), (0.60, 44.0, "easeOutQuad"), (0.715, 44.0, None),
         (0.785, -7.2, "easeOutQuad"), (0.855, 2.6, "easeInOutSine"), (0.925, -0.9, "easeInOutSine"), (1.0, 0.0, "easeInOutSine")],
    pitch=[(0.0, 2.0, None), (0.06, 0.8, "easeInOutSine"), (0.13, 4.2, "easeOutQuad"), (0.285, 2.4, "easeInOutSine"),
           (0.355, 4.0, "easeOutQuad"), (0.50, 2.2, "easeInOutSine"), (0.57, 4.2, "easeOutQuad"), (0.715, 2.0, "easeInOutSine"),
           (0.785, 7.0, "easeOutQuad"), (0.855, -1.8, "easeInOutSine"), (0.925, 0.8, "easeInOutSine"), (1.0, 0.0, "easeInOutSine")],
    roll=[(0.0, 0.0, None), (0.06, 0.8, "easeInOutSine"), (0.13, -2.6, "easeOutQuad"), (0.285, -1.0, "easeInOutSine"),
          (0.355, 2.3, "easeOutQuad"), (0.50, 0.8, "easeInOutSine"), (0.57, -2.1, "easeOutQuad"), (0.715, -0.4, "easeInOutSine"),
          (0.785, 3.2, "easeOutQuad"), (0.855, -1.3, "easeInOutSine"), (0.925, 0.5, "easeInOutSine"), (1.0, 0.0, "easeInOutSine")],
)
TURN_TO_TREE = dict(
    yaw=[(0.0, 0.0, None), (0.05, -3.2, "easeInOutSine"),
         (0.12, 45.0, "easeOutQuad"), (0.145, 43.4, "easeOutQuad"), (0.27, 43.4, None),
         (0.335, 89.0, "easeOutQuad"), (0.365, 87.6, "easeOutQuad"), (0.485, 87.6, None),
         (0.55, 133.2, "easeOutQuad"), (0.58, 131.7, "easeOutQuad"), (0.70, 131.7, None),
         (0.775, 187.2, "easeOutQuad"), (0.845, 177.4, "easeInOutSine"), (0.92, 181.2, "easeInOutSine"), (1.0, 180.0, "easeInOutSine")],
    pitch=[(0.0, 0.0, None), (0.05, 1.0, "easeInOutSine"), (0.12, 3.8, "easeOutQuad"), (0.27, 2.0, "easeInOutSine"), (0.335, 3.6, "easeOutQuad"),
           (0.485, 1.8, "easeInOutSine"), (0.55, 3.8, "easeOutQuad"), (0.70, 1.6, "easeInOutSine"), (0.775, 6.4, "easeOutQuad"),
           (0.845, -1.0, "easeInOutSine"), (0.92, 2.8, "easeInOutSine"), (1.0, 2.0, "easeInOutSine")],
    roll=[(0.0, 0.0, None), (0.05, -0.6, "easeInOutSine"), (0.12, 2.5, "easeOutQuad"), (0.27, 0.9, "easeInOutSine"), (0.335, -2.2, "easeOutQuad"),
          (0.485, -0.7, "easeInOutSine"), (0.55, 2.0, "easeOutQuad"), (0.70, 0.5, "easeInOutSine"), (0.775, -3.0, "easeOutQuad"),
          (0.845, 1.2, "easeInOutSine"), (0.92, -0.5, "easeInOutSine"), (1.0, 0.0, "easeInOutSine")],
)

# the move instants (end of each fast move) of the two turns, used to time the body / arm / dress reactions
TTP_HITS = (0.13, 0.355, 0.57, 0.785)
TTT_HITS = (0.12, 0.335, 0.55, 0.775)


def _reactions(A, hits, sgn_dir):
    """Body lean, arm flicks, dress swing and ribbon flap on each servo stop (sgn_dir flips sway for the return turn)."""
    t1, t2, t3, t4 = hits
    A.set_track("body", "position", keys_from([(0.0, 0, None), (t1 + 0.02, (0, 0, 0.3), "easeOutQuad"), (t1 + 0.27, 0, "easeInOutSine"),
                                               (t4 + 0.0, (0, 0, 0.5), "easeOutQuad"), (1.0, 0, "easeInOutSine")]))
    for bone, sgn in (("right_arm", 1), ("left_arm", -1)):
        A.set_track(bone, "rotation", keys_from([
            (0.0, 0, None), (t1, (0.3 * sgn_dir, 0, 1.6 * sgn), "easeOutQuad"), (t1 + 0.155, (0, 0, 0.3 * sgn), "easeInOutSine"),
            (t2, (0, 0, 1.4 * sgn), "easeOutQuad"), (t2 + 0.145, (0, 0, 0.2 * sgn), "easeInOutSine"),
            (t3, (0, 0, 1.4 * sgn), "easeOutQuad"), (t3 + 0.145, (0, 0, 0.2 * sgn), "easeInOutSine"),
            (t4, (1.0 * sgn_dir, 0, 2.6 * sgn), "easeOutQuad"), (t4 + 0.115, (-0.8 * sgn_dir, 0, -0.7 * sgn), "easeInOutSine"),
            (1.0, 0, "easeInOutSine")]))
    A.set_track("dress", "rotation", keys_from([
        (0.0, 0, None), (t1 + 0.01, (0, 0, 0.6 * sgn_dir), "easeOutQuad"), (t2 + 0.01, (0, 0, -0.5 * sgn_dir), "easeInOutSine"),
        (t3 + 0.01, (0, 0, 0.6 * sgn_dir), "easeInOutSine"), (t4 + 0.005, (0, 0, -1.2 * sgn_dir), "easeOutQuad"),
        (t4 + 0.115, (0, 0, 0.6 * sgn_dir), "easeInOutSine"), (1.0, 0, "easeInOutSine")]))
    A.set_track("ribbon", "rotation", keys_from([
        (0.0, 0, None), (t1 + 0.01, (0, 0, 4.0 * sgn_dir), "easeOutQuad"), (t2 + 0.01, (0, 0, -3.5 * sgn_dir), "easeInOutSine"),
        (t3 + 0.01, (0, 0, 3.8 * sgn_dir), "easeInOutSine"), (t4 + 0.005, (0, 0, -7.0 * sgn_dir), "easeOutQuad"),
        (t4 + 0.1, (0, 0, 3.0 * sgn_dir), "easeInOutSine"), (1.0, 0, "easeInOutSine")]))
    A.set_track("mouth", "scale", keys_from([(0.0, (1, 1, 1), None), (1.0, (1, 1, 1), None)]))


def _build_turn(name, spec, hits, sgn_dir, ctx, start, end):
    L = 1.0
    A = Anim(name, L, False)
    head = head_track(A, spec["yaw"], spec["pitch"], spec["roll"])
    add_followers(A, head, False, dt=1 / 60.0, ring_zeta=0.3, pig_gain=0.085)
    _reactions(A, hits, sgn_dir)
    # the body also takes a very slightly delayed yaw reaction torque from the simulation
    anchor(A, (ctx[start], 0.0), (ctx[end], 0.0))
    return A


def build_turn_to_players(ctx):
    return _build_turn("turn_to_players", TURN_TO_PLAYERS, TTP_HITS, 1.0, ctx, "idle_tree", "idle_players")


def build_turn_to_tree(ctx):
    return _build_turn("turn_to_tree", TURN_TO_TREE, TTT_HITS, -1.0, ctx, "idle_players", "idle_tree")


# ================================================================================================
# 6. scan_players (loop 2 s): +-25 deg sweeps with jittery servo steps
# ================================================================================================
def _scan_keys():
    """Right half: 0 -> +25 in 3 servo steps, dwell, back in 3 steps; the left half mirrors it."""
    keys = [(0.0, 0.0, None)]
    t = 0.08
    keys.append((t, 0.0, None))

    def sweep(t, a0, a1, n=3, move=0.048, hold=0.066, over=0.9):
        step = (a1 - a0) / n
        cur = a0
        for i in range(n):
            nxt = cur + step
            kick = (over if i == n - 1 else 0.35) * (1 if step > 0 else -1)
            keys.append((t + move, nxt + kick, "easeOutQuad"))
            keys.append((t + move + 0.018, nxt, "easeInOutSine"))
            t += move + hold
            keys.append((t, nxt, None))
            cur = nxt
        return t

    t = sweep(t, 0.0, 25.0)
    t += 0.12
    keys.append((t, 25.0, None))
    t = sweep(t, 25.0, 0.0)
    t += 0.03
    keys.append((t, 0.0, None))
    return keys, t


def build_scan_players(ctx):
    L = 2.0
    A = Anim("scan_players", L, True)
    right, t_end = _scan_keys()
    left = [(t_end + k[0], -k[1], k[2]) for k in right[1:]]
    keys = right + left
    scale = L / keys[-1][0]
    keys = [(k[0] * scale, k[1] + 0.0, k[2]) for k in keys]
    keys[-1] = (L, 0.0, keys[-1][2])
    ysamp = Track([(t, v3(0, y, 0), e) for t, y, e in keys]).sampler()
    rng = np.random.RandomState(77)
    jit = rng.uniform(-1, 1, size=(int(L * 25) + 3, 3))

    def head(t):
        y = ysamp(t)[1]
        i = int(t * 25)
        # gaze dips as the head swings away from centre, servo hunting adds tiny pitch / roll twitches
        p = 2.4 * min(1.0, abs(y) / 16.0) + 0.35 * jit[i, 0] * min(1.0, abs(y) / 6.0)
        r = -0.05 * y + 0.4 * jit[(i + 7) % len(jit), 1] * min(1.0, abs(y) / 6.0)
        return v3(p, y, r)

    emit(A, "head", "rotation", head, 0, L, 1 / 60.0, 0.02, loop=True)
    add_followers(A, head, True, dt=1 / 60.0, pig_gain=0.07, ring_zeta=0.32)
    # the eyes flare a little with every servo step (lens hunting), on top of a slow scanner pulse
    h = 1 / 200.0
    n = int(round(L / h))
    ts = np.arange(n * 3 + 1) * h                                        # 3 passes -> periodic steady state
    speed = np.abs(np.gradient(np.array([ysamp((t % L))[1] for t in ts]), h))
    flare, s = np.zeros(len(ts)), 0.0
    for i, sp in enumerate(speed):
        target = 0.085 * min(1.0, sp / 250.0)
        s += (target - s) * (h / (0.03 if target > s else 0.09))          # fast attack, slower release
        flare[i] = s
    flare_at = lambda t: float(np.interp((t % L) + L, ts, flare))
    eye_scale(A, lambda t: 1.0 + 0.04 * bob(t, 1.0) + flare_at(t), 1 / 50.0, True, tol=0.003)
    emit(A, "body", "position", lambda t: v3(0, 0.06 * S(t, 1.0), 0), 0, L, 1 / 30.0, 0.01, loop=True)
    anchor(A, (ctx["idle_players"], 0.0), (ctx["idle_players"], 0.0))
    return A


# ================================================================================================
# 7. lock_on (once 0.4 s): sharp snap of the head, eyes flare, body recoils
# ================================================================================================
def build_lock_on(ctx):
    L = 0.4
    A = Anim("lock_on", L, False)
    head = head_track(
        A,
        [(0.0, 0.0, None), (0.055, 15.5, "easeOutQuad"), (0.09, 10.2, "easeInOutSine"), (0.14, 12.4, "easeInOutSine"),
         (0.22, 11.4, "easeInOutSine"), (0.4, 11.0, "easeInOutSine")],
        [(0.0, 0.0, None), (0.055, 8.5, "easeOutQuad"), (0.10, 5.0, "easeInOutSine"), (0.16, 6.0, "easeInOutSine"), (0.4, 5.6, "easeInOutSine")],
        [(0.0, 0.0, None), (0.055, -3.6, "easeOutQuad"), (0.12, -1.6, "easeInOutSine"), (0.4, -2.0, "easeInOutSine")])
    add_followers(A, head, False, dt=1 / 80.0, ring_zeta=0.3, pig_gain=0.1)
    A.set_track("body", "position", keys_from([(0.0, 0, None), (0.06, (0, 0, 0.9), "easeOutQuad"), (0.2, (0, 0, 0.35), "easeInOutSine"),
                                               (0.4, (0, 0, 0.3), "easeInOutSine")]))
    extra = Track(keys_from([(0.0, 0, None), (0.06, (-0.9, 0, 0.3), "easeOutQuad"), (0.2, (-0.35, 0, 0.1), "easeInOutSine"),
                             (0.4, (-0.3, 0, 0), "easeInOutSine")])).sampler()
    add_to_track(A, "body", "rotation", extra, 1 / 80.0, 0.02)
    for bone, sgn in (("right_arm", 1), ("left_arm", -1)):
        A.set_track(bone, "rotation", keys_from([(0.0, 0, None), (0.06, (-1.5, 0, 2.2 * sgn), "easeOutQuad"), (0.2, (-0.6, 0, 1.0 * sgn), "easeInOutSine"),
                                                 (0.4, (-0.5, 0, 0.8 * sgn), "easeInOutSine")]))
    A.set_track("dress", "rotation", keys_from([(0.0, 0, None), (0.07, (0, 0, 1.1), "easeOutQuad"), (0.17, (0, 0, -0.5), "easeInOutSine"),
                                                (0.4, (0, 0, 0.1), "easeInOutSine")]))
    for b in ("eye_on_l", "eye_on_r"):
        A.set_track(b, "scale", keys_from([(0.0, (1, 1, 1), None), (0.05, (1.34, 1.34, 1), "easeOutQuad"), (0.14, (1.08, 1.08, 1), "easeInOutSine"),
                                           (0.4, (1.12, 1.12, 1), "easeInOutSine")]))
    A.set_track("ribbon", "rotation", keys_from([(0.0, 0, None), (0.06, (0, 0, 8.0), "easeOutQuad"), (0.16, (0, 0, -3.0), "easeInOutSine"),
                                                 (0.4, 0, "easeInOutSine")]))
    anchor(A, (ctx["idle_players"], 0.0), None)
    return A


# ================================================================================================
BUILD_ORDER = [("dormant", build_dormant), ("idle_tree", build_idle_tree), ("idle_players", build_idle_players),
               ("wake", build_wake), ("turn_to_players", build_turn_to_players), ("scan_players", build_scan_players),
               ("lock_on", build_lock_on), ("turn_to_tree", build_turn_to_tree)]
# order of appearance in the exported file (contract order)
EXPORT_ORDER = ["dormant", "wake", "idle_tree", "turn_to_players", "idle_players", "scan_players", "lock_on", "turn_to_tree"]


def build_all():
    ctx = {}
    for name, fn in BUILD_ORDER:
        ctx[name] = fn(ctx)
    return [ctx[n] for n in EXPORT_ORDER]
