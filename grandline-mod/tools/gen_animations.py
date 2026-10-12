#!/usr/bin/env python3
"""
Grand Line Odyssey - humanoid animation library generator.

Writes src/main/resources/assets/grandline/animations/humanoid.animation.json (Bedrock/Blockbench-style
JSON) which client/anim/AnimLibrary loads and client/anim/Animator plays on players and humanoid NPCs.

    python3 -I tools/gen_animations.py                      generate + validate
    python3 -I tools/gen_animations.py --check              validate the existing JSON only
    python3 -I tools/gen_animations.py --preview DIR [NAME ...]
                                                            also render stick-figure contact sheets (PNG)
                                                            of the generated JSON (front / side / top /
                                                            first-person rows, one column per keyframe)

Poses are written in an intuitive, body-relative DSL and solved into the raw channels Animator expects.

  Pose keys (all optional; degrees and model pixels, 16 px = 1 block)
    ra, la    right / left arm: aim(hand_dir, item_dir) with directions (right, up, fwd) in the pelvis frame
              (best for weapons: held items stick out perpendicular to the arm), 'grip' / 'shaft' (left arm
              only: reach for the right hand's hilt, or further up a polearm's shaft), or
              (pitch, yaw_out, roll_out) relative to the torso:
                pitch     + raises the arm forward: 90 = horizontal forward, 180 = straight up, - = swung back
                yaw_out   + sweeps a raised arm horizontally away from the body midline, - across the body
                roll_out  rotation about the forward axis (frontal plane); for a hanging arm + lifts it sideways
                          outward (90 = T-pose).  Applied in ModelPart order: pitch, then yaw, then roll.
    rl, ll    right / left leg (same triple, pelvis frame): pitch + = leg swings forward (kick)
    torso     (lean, twist): lean + bends forward at the hips; twist + turns the chest to the LEFT
                (counter-clockwise seen from above, right shoulder comes forward). Arms ride along.
    head      (pitch, yaw): pitch + looks down, yaw + turns left. Omit to keep the vanilla look direction.
    drop      upper body sinks this many px (crouch compression, like vanilla sneaking; - lifts it)
    neck      extra head lift in px
    rap, lap  shoulder offset (out, up, fwd) px in the torso frame (e.g. fwd + for a reaching punch)
    fwd, up, right    whole-body (root) translation in px
    pitch, yaw, roll  whole-body rotation about the hip-height pivot: pitch + = lean/flip forward,
                      yaw + = turn left, roll + = lean/roll to the right
    plant     (default True) auto-raise/lower the root so the lowest foot touches the ground ('up' adds on top);
              'body' / 'all' plant the torso / any part instead (lying poses), False = free (airborne, flips)
    fp        first-person hand offset: raw (x, y, z, rx, ry, rz) or fps(...)
    hit       marks the impact keyframe (used by the timing validation)
    tween     this key does not key the root rotation (it is interpolated from its neighbours), so spins and
              flips stay one smooth segment while the key still sets lift/contact and limbs

  Bones that a clip never mentions keep vanilla motion (e.g. walking legs during upper-body attacks).
  A bone mentioned in any keyframe is controlled for the whole clip and rests at 0 where it is not given.

Raw channel conventions (what Animator does with the JSON):
  head/body/arms/legs rotation [x,y,z] deg -> ModelPart xRot = -x, yRot = y, zRot = z.
  position [x,y,z] px -> part.x += -x, part.y += -y, part.z += z  (so +x = toward the right side,
  +y = up, +z = BACKWARD for model parts).
  root: position px/16 in a body-yaw-aligned frame (+x = left, +y = up, +z = forward); rotation X (+ = pitch
  forward), Y (+ = turn left), Z (+ = roll right), pivot at half the bounding-box height.
  fp_right: first-person PoseStack translate px/16 (+x right, +y up, +z toward camera) then rotate X/Y/Z
  about the camera (+x = pitch up, +y = swing left, +z = roll counter-clockwise).

Vanilla HumanoidModel.setupAnim does NOT reset head/body x/z positions, leg x positions or head/body zRot,
and Animator adds positions / blends from the previous value, so those channels would drift: the solver
never emits them and validate() rejects them.
"""
import json
import math
import os
import re
import sys
import zlib
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
OUT = os.path.join(MOD, 'src', 'main', 'resources', 'assets', 'grandline', 'animations', 'humanoid.animation.json')
JAVA = os.path.join(MOD, 'src', 'main', 'java', 'com', 'grandline')
KEY_PREFIX = 'animation.grandline.'

REQUIRED = """
aim air_step anchor_throw axe_kick backstab cast_forward cast_ground cast_up chain_spin chain_swing claw_flurry
claw_left claw_right dash dodge_back dodge_forward dodge_left dodge_right dodge_side double_palm dual_cross
dual_dash dual_slash_left dual_slash_right dual_x_cut finger_pistol finger_twist fire_one_hand fire_two_hand
focus gatling gear_second get_up giant_punch grab ground_slam guard guard_break guard_end handstand_spin harden
headbutt heavy_cleave heavy_overhead heavy_punch heavy_swing heavy_swing_back heavy_thrust hit_front hit_left
hit_right hook_right howl iai_dash iai_slash iai_stance iron_body jab_left jab_right kick_heavy kick_high kick_low
kick_mid knocked_down nika palm_strike parry pistol_whip polearm_spin polearm_sweep polearm_sweep_back pounce
punch_extend rapid_fire rifle_butt rising_slash roar rocket slam slash_left slash_right spear_sweep spear_thrust
spin_kick spin_slash split stab staff_swing staff_swing_back staff_thrust staggered stance_enter stance_exit
surrender sway throw throw_grab thrust transform transform_revert triple_cut triple_dash uppercut vault
whip_crack whip_lash whip_sweep wide_slash wide_slash_back
""".split()
EXTRA = ['stance', 'meditate', 'wave', 'cheer', 'sit_ground', 'point', 'bow_greet']
# Animator treats the first four as persistent states; the rest loop until another clip replaces them.
LOOPING = {'guard', 'knocked_down', 'surrender', 'iai_stance', 'stance', 'meditate', 'sit_ground'}
BONES = ('head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg', 'root', 'fp_right')

# =====================================================================================================
# math
# =====================================================================================================
D2R = math.pi / 180.0


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return ((1.0, 0.0, 0.0), (0.0, c, -s), (0.0, s, c))


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return ((c, 0.0, s), (0.0, 1.0, 0.0), (-s, 0.0, c))


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return ((c, -s, 0.0), (s, c, 0.0), (0.0, 0.0, 1.0))


def mmul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def mvec(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def zyx(xr, yr, zr):
    """ModelPart rotation (Quaternionf.rotationZYX): v' = Rz * Ry * Rx * v. Radians."""
    return mmul(rot_z(zr), mmul(rot_y(yr), rot_x(xr)))


def root_matrix(pitch, yaw, roll):
    """ClientRenderEvents: mulPose(X) then (Y) then (Z) => Rx * Ry * Rz. Degrees."""
    return mmul(rot_x(pitch * D2R), mmul(rot_y(yaw * D2R), rot_z(roll * D2R)))


def _near(a, ref):
    return a + 2 * math.pi * round((ref - a) / (2 * math.pi))


def euler_zyx_near(m, ref):
    """Decompose m = Rz(c) Ry(b) Rx(a) into (a, b, c), choosing the equivalent solution closest to ref."""
    sb = max(-1.0, min(1.0, -m[2][0]))
    b = math.asin(sb)
    if abs(math.cos(b)) > 1e-7:
        a = math.atan2(m[2][1], m[2][2])
        c = math.atan2(m[1][0], m[0][0])
    else:  # gimbal lock: put everything into a
        a = math.atan2(-m[1][2], m[1][1])
        c = 0.0
    best = None
    for cand in ((a, b, c), (a + math.pi, math.pi - b, c + math.pi)):
        cand = tuple(_near(cand[i], ref[i]) for i in range(3))
        d = sum((cand[i] - ref[i]) ** 2 for i in range(3))
        if best is None or d < best[0]:
            best = (d, cand)
    return best[1]


# =====================================================================================================
# rig (vanilla HumanoidModel, model space: px, +y down, -z forward, -x = the body's right side)
# =====================================================================================================
PIVOT = {'head': (0.0, 0.0, 0.0), 'body': (0.0, 0.0, 0.0), 'right_arm': (-5.0, 2.0, 0.0), 'left_arm': (5.0, 2.0, 0.0),
         'right_leg': (-1.9, 12.0, 0.0), 'left_leg': (1.9, 12.0, 0.0)}
BOX = {'head': ((-4, -8, -4), (8, 8, 8)), 'body': ((-4, 0, -2), (8, 12, 4)), 'right_arm': ((-3, -2, -2), (4, 12, 4)),
       'left_arm': ((-1, -2, -2), (4, 12, 4)), 'right_leg': ((-2, 0, -2), (4, 12, 4)), 'left_leg': ((-2, 0, -2), (4, 12, 4))}
PARTS = ('head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg')
MODEL_TOP = 24.0          # model y = 0 (neck) sits 24 px above the feet
ROOT_PIVOT = (0.0, 14.4, 0.0)  # bbHeight / 2 for a 1.8-block humanoid
# held item (vanilla handheld transforms): grip at the fist, blade perpendicular to the arm (arm-local -z, 10 deg toward the hand)
GRIP = {'right_arm': (-1.0, 10.0, -2.0), 'left_arm': (1.0, 10.0, -2.0)}
BLADE_DIR = (0.0, 0.17, -0.985)
BLADE_LEN = 13.0


def box_corners(bone):
    (x0, y0, z0), (w, h, d) = BOX[bone]
    return [(x0 + w * i, y0 + h * j, z0 + d * k) for i in (0, 1) for j in (0, 1) for k in (0, 1)]


def to_entity(v):
    """model px -> entity frame px (+x left, +y up from the feet, +z forward)."""
    return (v[0], MODEL_TOP - v[1], -v[2])


def part_transform(bone, rot, pos):
    """Returns f(local) -> model-space point for a part with JSON rotation/position (None = vanilla rest)."""
    rot = rot or (0.0, 0.0, 0.0)
    pos = pos or (0.0, 0.0, 0.0)
    m = zyx(-rot[0] * D2R, rot[1] * D2R, rot[2] * D2R)
    piv = PIVOT[bone]
    p = (piv[0] - pos[0], piv[1] - pos[1], piv[2] + pos[2])
    return lambda v: add(p, mvec(m, v))


def root_transform(rot, pos):
    rot = rot or (0.0, 0.0, 0.0)
    pos = pos or (0.0, 0.0, 0.0)
    m = root_matrix(*rot)
    c = ROOT_PIVOT
    return lambda e: add(add(c, pos), mvec(m, sub(e, c)))


def world_points(chan, bones=PARTS, local=None):
    """chan: {bone: (rot, pos)} in JSON units. Returns {bone: [entity-frame points after root]}."""
    rt = root_transform(*chan.get('root', (None, None)))
    out = {}
    for b in bones:
        f = part_transform(b, *chan.get(b, (None, None)))
        pts = local[b] if local else box_corners(b)
        out[b] = [rt(to_entity(f(v))) for v in pts]
    return out


# =====================================================================================================
# pose solver (DSL -> raw channels)
# =====================================================================================================
ARMS = (('right_arm', 'ra', 'rap', -1.0), ('left_arm', 'la', 'lap', 1.0))
LEGS = (('right_leg', 'rl', -1.0), ('left_leg', 'll', 1.0))


class Aim(tuple):
    """Arm spec by directions in the pelvis frame (right, up, fwd): where the arm points and where the held
    item (blade) points. Independent of torso twist/lean (only the shoulder position follows the torso)."""


def aim(hand, blade=(0.0, 1.0, 0.3)):
    return Aim((tuple(float(v) for v in hand), tuple(float(v) for v in blade)))


def _unit(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / n for c in v)


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def frame_matrix(hand_m, blade_m):
    """Rotation taking arm-local +y (shoulder->hand) to hand_m and local -z (item direction) toward blade_m."""
    y = _unit(hand_m)
    bz = sub(blade_m, tuple(_dot(blade_m, y) * c for c in y))
    if math.sqrt(_dot(bz, bz)) < 1e-6:
        bz = (0.0, 0.0, -1.0) if abs(y[2]) < 0.9 else (0.0, -1.0, 0.0)
        bz = sub(bz, tuple(_dot(bz, y) * c for c in y))
    z = tuple(-c for c in _unit(bz))
    x = _cross(y, z)
    return tuple((x[i], y[i], z[i]) for i in range(3))


def _model_dir(v):  # (right, up, fwd) -> model axes
    return (-v[0], -v[1], -v[2])


def solve(pose, ctrl, prev=None):
    """One keyframe. ctrl = set of DSL keys used anywhere in the clip; prev = previous key's Euler per bone
    (radians, for continuity). Returns {bone: (rot, pos)} in JSON units."""
    prev = prev if prev is not None else {}
    lean, twist = pose.get('torso', (0.0, 0.0))
    drop = pose.get('drop', 0.0)
    tb = mmul(rot_y(-twist * D2R), rot_x(lean * D2R))  # body.yRot = -twist, body.xRot = lean
    ch = {'body': ((-lean, -twist, 0.0), (0.0, -drop, 0.0))}
    neck = (0.0, drop, 0.0)
    grip_r = None
    for bone, key, off, sx in ARMS:
        spec = pose.get(key, (0.0, 0.0, 0.0))
        o, u, f = pose.get(off, (0.0, 0.0, 0.0))
        piv0 = PIVOT[bone]
        piv = add(neck, mvec(tb, (piv0[0] + sx * o, piv0[1] - u, piv0[2] - f)))
        if isinstance(spec, Aim):
            m = frame_matrix(_model_dir(spec[0]), _model_dir(spec[1]))
            ref = prev.get(bone, (0.0, 0.0, 0.0))
        elif spec in ('grip', 'shaft'):  # two-handed: off hand on the hilt just below the main hand, or up the shaft
            rm = ch['_rm']
            along = -3.0 if spec == 'grip' else 8.0
            target = add(add(ch['_rpiv'], mvec(rm, GRIP['right_arm'])), mvec(rm, tuple(along * c for c in BLADE_DIR)))
            m = frame_matrix(sub(target, piv), mvec(rm, BLADE_DIR))
            ref = prev.get(bone, (0.0, 0.0, 0.0))
        else:
            p, yo, ro = spec
            loc = (-p * D2R, -sx * yo * D2R, -sx * ro * D2R)
            m = mmul(tb, zyx(*loc))
            ref = (loc[0] + lean * D2R, loc[1] - twist * D2R, loc[2])
        a, b, c = euler_zyx_near(m, ref)
        if bone in prev:  # same rotation, but take the short way from the previous key
            a, b, c = (_near(v, pv) for v, pv in zip((a, b, c), prev[bone]))
        prev[bone] = (a, b, c)
        if bone == 'right_arm':
            ch['_rm'], ch['_rpiv'] = zyx(a, b, c), piv
        d = sub(piv, piv0)
        ch[bone] = ((-a / D2R, b / D2R, c / D2R) if key in ctrl else None, (-d[0], -d[1], d[2]))
    del ch['_rm'], ch['_rpiv']
    hp, hy = pose.get('head', (0.0, 0.0))
    ch['head'] = ((-hp, -hy, 0.0) if 'head' in ctrl else None, (0.0, -drop + pose.get('neck', 0.0), 0.0))
    for bone, key, sx in LEGS:
        p, yo, ro = pose.get(key, (0.0, 0.0, 0.0))
        d = sub(mvec(tb, PIVOT[bone]), PIVOT[bone])  # hips ride with the torso (not with 'drop')
        ch[bone] = ((p, -sx * yo, -sx * ro) if key in ctrl else None, (0.0, -d[1], d[2]))
    # root: keep the hips in place when the torso leans (lean pivots at the neck in the model)
    hd = sub(mvec(tb, (0.0, 12.0, 0.0)), (0.0, 12.0, 0.0))
    rr = (pose.get('pitch', 0.0), pose.get('yaw', 0.0), pose.get('roll', 0.0))
    comp = mvec(root_matrix(*rr), (-hd[0], hd[1], hd[2]))
    rp = [-pose.get('right', 0.0) + comp[0], comp[1], pose.get('fwd', 0.0) + comp[2]]
    plant = pose.get('plant', True)
    if plant:
        tmp = dict(ch)
        tmp['root'] = (rr, tuple(rp))
        on = {True: ('right_leg', 'left_leg'), 'all': PARTS, 'body': ('body',)}[plant]
        low = min(p[1] for pts in world_points(tmp, on).values() for p in pts)
        rp[1] -= low
    rp[1] += pose.get('up', 0.0)
    ch['root'] = (rr, tuple(rp))
    fp = pose.get('fp')
    if fp is not None or 'fp' in ctrl:
        fp = fp or (0, 0, 0, 0, 0, 0)
        ch['fp_right'] = (tuple(fp[3:6]), tuple(fp[0:3]))
    return ch


# first-person helper ---------------------------------------------------------------------------------
FP_ITEM = (0.63, -0.32, -0.65)   # held item centre at rest (blocks, camera space)
FP_HALF = (1.245, 0.70)          # half screen extents at unit depth (70 deg vertical FOV, 16:9)


def fps(sx, sy, tilt=0.0, push=0.0, pitch=0.0, yaw=0.0):
    """First-person keyframe from screen terms: put the held item's centre at normalised screen (sx, sy)
    (-1..1, rest is about (0.78, -0.70)), rolled by `tilt` deg (+ = counter-clockwise), pushed `push` px
    away from the camera, optionally pitched/yawed for perspective."""
    m = mmul(rot_x(pitch * D2R), mmul(rot_y(yaw * D2R), rot_z(tilt * D2R)))
    c = mvec(m, FP_ITEM)
    depth = -c[2] + push / 16.0
    want = (sx * FP_HALF[0] * depth, sy * FP_HALF[1] * depth, -depth)
    d = sub(want, c)
    return (round(d[0] * 16, 2), round(d[1] * 16, 2), round(d[2] * 16, 2), pitch, yaw, tilt)


FP_REST = (0, 0, 0, 0, 0, 0)

# =====================================================================================================
# clip builder
# =====================================================================================================
LIB = {}


def P(*bases, **kw):
    """Pose = merged dicts (later wins)."""
    out = {}
    for b in bases:
        out.update(b)
    out.update(kw)
    return out


def anim(name, length, keys, loop=False):
    assert name not in LIB, name
    LIB[name] = {'length': float(length), 'loop': bool(loop), 'keys': [(float(t), p) for t, p in keys]}


def _mirror_arm(spec):
    if isinstance(spec, Aim):
        (hr, hu, hf), (br, bu, bf) = spec
        return Aim(((-hr, hu, hf), (-br, bu, bf)))
    return spec


def mirror_pose(p):
    q = dict(p)
    for a, b in (('ra', 'la'), ('rl', 'll'), ('rap', 'lap')):
        q.pop(a, None)
        q.pop(b, None)
        if a in p:
            q[b] = _mirror_arm(p[a])
        if b in p:
            q[a] = _mirror_arm(p[b])
    assert not {'grip', 'shaft'} & {q.get('ra'), q.get('la')}, 'two-handed grips cannot be mirrored'
    if 'torso' in p:
        q['torso'] = (p['torso'][0], -p['torso'][1])
    if 'head' in p:
        q['head'] = (p['head'][0], -p['head'][1])
    for k in ('right', 'yaw', 'roll'):
        if k in p:
            q[k] = -p[k]
    if 'fp' in p:
        x, y, z, rx, ry, rz = p['fp']
        q['fp'] = (-x, y, z, rx, -ry, -rz)
    return q


def mirror(src, dst, fp=None):
    """Register dst as the left/right mirror of src. fp: replacement first-person keys {time: fp} or None to keep."""
    s = LIB[src]
    keys = []
    for t, p in s['keys']:
        q = mirror_pose(p)
        if fp is not None:
            q['fp'] = fp.get(t, FP_REST)
        keys.append((t, q))
    anim(dst, s['length'], keys, s['loop'])


def _fmt_t(t):
    s = ('%.4f' % t).rstrip('0')
    return s + '0' if s.endswith('.') else s


def _r(v):
    v = float(round(v, 2))
    return 0.0 if v == 0 else v


EXPLICIT = {'head': 'head', 'body': 'torso', 'right_arm': 'ra', 'left_arm': 'la', 'right_leg': 'rl', 'left_leg': 'll'}


def _unwrap_root(keys):
    """Root rotation is scaled by the blend weight, so a clip that ends on +-360 would spin back during the
    fade-out. Snap full turns back to 0 with a 0.1 ms wrap key right after the turn completes."""
    keys = [(t, list(v)) for t, v in keys]
    for axis in range(3):
        final = keys[-1][1][axis]
        turns = round(final / 360.0)
        if turns == 0 or abs(final - 360.0 * turns) > 1e-6:
            continue
        i = len(keys) - 1
        while i > 0 and abs(keys[i - 1][1][axis] - final) < 1e-6:
            i -= 1
        assert i < len(keys) - 1, 'a full turn must complete before the last key (hold it, then wrap)'
        wrap = (keys[i][0] + 0.0001, list(keys[i][1]))
        keys.insert(i + 1, wrap)
        for j in range(i + 1, len(keys)):
            keys[j][1][axis] -= 360.0 * turns
    return [(t, tuple(v)) for t, v in keys]


def _smooth(keys, t):
    """AnimDef.Track.sample: smoothstep between neighbouring keys."""
    if t <= keys[0][0]:
        return keys[0][1]
    for (ta, va), (tb, vb) in zip(keys, keys[1:]):
        if t <= tb:
            k = (t - ta) / max(1e-4, tb - ta)
            k = k * k * (3 - 2 * k)
            return tuple(va[i] + (vb[i] - va[i]) * k for i in range(3))
    return keys[-1][1]


def build(name, spec):
    keys = spec['keys']
    ctrl = set()
    for _, p in keys:
        ctrl |= set(p)
    # 'tween' keys do not key the root rotation: a spin/flip stays one smooth segment (smoothstep eases at every
    # key), while the tween still keys everything else (e.g. lift so the body clears the ground mid-roll).
    rk = [(t, (p.get('pitch', 0.0), p.get('yaw', 0.0), p.get('roll', 0.0))) for t, p in keys if not p.get('tween')]
    keys = [(t, P(p, **dict(zip(('pitch', 'yaw', 'roll'), _smooth(rk, t)))) if p.get('tween') else p) for t, p in keys]
    prev = {}
    solved = [(t, solve(p, ctrl, prev), p.get('tween')) for t, p in keys]
    bones = {}
    for bone in BONES:
        for ci, cname in ((0, 'rotation'), (1, 'position')):
            seq = []
            for t, ch, tween in solved:
                if bone not in ch or ch[bone][ci] is None:
                    seq = None
                    break
                if tween and bone == 'root' and ci == 0:
                    continue
                seq.append((t, tuple(_r(v) for v in ch[bone][ci])))
            if not seq:
                continue
            if not any(any(v) for _, v in seq):
                # an all-zero channel only matters as a rotation override of vanilla motion (e.g. legs held
                # straight during a cast); zero positions and zero root / first-person offsets change nothing
                if ci == 1 or EXPLICIT.get(bone) not in ctrl:
                    continue
            if bone == 'root' and ci == 0:
                seq = _unwrap_root(seq)
            if all(v == seq[0][1] for _, v in seq):
                val = list(seq[0][1])
            else:
                val = {_fmt_t(t): list(v) for t, v in seq}
            bones.setdefault(bone, {})[cname] = val
    return {'animation_length': spec['length'], 'loop': spec['loop'], 'bones': bones}


def impact_time(name):
    for t, p in LIB[name]['keys']:
        if p.get('hit'):
            return t
    return None


# =====================================================================================================
# library
# =====================================================================================================
# Melee timing follows combat/weapon/WeaponClass.java and combat/style/FightingStyles.java: a Strike lands
# `windup` ticks after the input and the next input opens after windup + recovery, so the clip reaches its
# hit=True pose at ~windup/20 s and ends around (windup + recovery)/20 s. Abilities fire their effect on the
# first tick, so technique clips put their big pose early and then hold / recover.

REST = P()
LEGS0 = P(rl=(0, 0, 0), ll=(0, 0, 0))
READY = P(ra=(30, 0, 8), torso=(4, 0))                                     # one-handed weapon at the ready
DUAL_READY = P(ra=(32, 0, 10), la=(32, 0, 10), torso=(4, 0))
FISTS = P(ra=(78, -28, 0), la=(86, -22, 0), torso=(6, -12), drop=0.5)       # boxing guard (upper body)
STANCE_LEGS = P(rl=(-14, 0, 4), ll=(16, 0, 4))                               # orthodox stance, left foot forward
LUNGE = P(rl=(-24, 0, 5), ll=(32, 0, 5))
DEEP_LUNGE = P(rl=(-38, 0, 5), ll=(44, 0, 5))
WIDE = P(rl=(-8, 0, 11), ll=(10, 0, 11))
GUARD = P(ra=(118, -42, 0), la=(118, -42, 0), torso=(8, 0), drop=1)         # forearms crossed in front of the face
FP_GUARD = fps(0.3, -0.3, 35)
FP_JAB = fps(0.3, -0.35, 8, push=6)
FP_DIP = fps(0.85, -0.95, -8)


def two(hand, blade, *bases, **kw):
    """Two-handed weapon pose: the right hand aims the weapon, the left hand grips the handle just below it
    (left shoulder reaching slightly forward so the arm clears the chest)."""
    return P(*bases, ra=aim(hand, blade), la='grip', **{'lap': (0, 0, 1.5), **kw})


def pole(hand, blade, *bases, **kw):
    """Polearm pose (spear, naginata, staff): the left hand holds the shaft ahead of the right hand."""
    return P(*bases, ra=aim(hand, blade), la='shaft', **{'lap': (0, 0, 1.5), **kw})


TWO_READY = two((0.1, -1, 0.3), (0, 0.35, 1), torso=(4, 0))
POLE_READY = pole((0.1, -1, 0.3), (0, 0.35, 1), torso=(4, 0))
# crouched, right hand on the hilt at the left hip, left hand on the scabbard
IAI = P(ra=aim((-0.5, -0.85, 0.2), (-0.2, 0.25, -1)), la=aim((0.15, -1, 0.15), (0, 0.1, -1)),
        torso=(14, 22), drop=2, rl=(-24, 0, 5), ll=(28, 0, 5))


def tweens(t0, t1, n, pose):
    """n evenly spaced 'tween' keys strictly between t0 and t1: they keep ground contact / lift right during a
    spin, roll or flip without breaking its single smooth rotation segment."""
    return [(round(t0 + (t1 - t0) * (i + 1) / (n + 1), 4), P(pose, tween=True)) for i in range(n)]


def sweep(name, length, tw, th, tf, back=False, h=0.0, power=1.0, yaw=0.0, hold=two):
    """Two-handed horizontal swing. Forehand = right -> left, back = left -> right. h raises (+) / lowers (-)
    the strike line, power scales the body drive, yaw adds a whole-body pivot, hold = two or pole."""
    s = -1.0 if back else 1.0

    def m(v):
        return (s * v[0], v[1], v[2])
    legs_w = P(rl=(-6, 0, 6), ll=(10, 0, 6))
    legs_h = P(rl=(-10 - 6 * power, 0, 6), ll=(14 + 6 * power, 0, 6))
    fpx = (0.95, 0.05, -0.85) if not back else (-0.55, 0.35, 1.05)
    keys = [
        (0.0, P(TWO_READY if hold is two else POLE_READY, LEGS0, fp=FP_REST)),
        (tw, hold(m((0.85, 0.25 + h, -0.3)), m((0.3, 0.2, -1)), legs_w, torso=(2, -42 * s * power), drop=power,
                 yaw=-yaw * s, fp=fps(fpx[0], 0.05 + 0.4 * h, -35 * s))),
        (th, hold(m((0.1, -0.05 + h, 1)), m((-1, 0.05, 0.1)), legs_h, torso=(10, 22 * s * power), drop=1.5 * power,
                 fwd=1.5 * power, yaw=yaw * s * 0.5, fp=fps(fpx[1], -0.25 + 0.3 * h, 25 * s), hit=True)),
        (tf, hold(m((-0.75, -0.25 + h, 0.55)), m((-0.55, 0, -0.85)), legs_h, torso=(12, 45 * s * power), drop=1.5 * power,
                 fwd=1.5 * power, yaw=yaw * s, fp=fps(fpx[2], -0.6, 65 * s))),
    ]
    if length - tf > 0.3:
        keys.append((tf + (length - tf) * 0.55, P(TWO_READY if hold is two else POLE_READY, rl=(-4, 0, 3), ll=(5, 0, 3), fp=fps(0.65, -0.7, 0))))
    keys.append((length, P(LEGS0, fp=FP_REST)))
    anim(name, length, keys)


def swords():
    # ---------------------------------------------------------------- one-handed (katana, cutlass, jitte, ittoryu)
    anim('slash_right', 0.48, [   # forehand: cocked over the right shoulder, cuts across to the low left
        (0.00, P(READY, fp=FP_REST)),
        (0.09, P(ra=aim((0.7, 0.75, -0.1), (0.3, 0.3, -1)), torso=(2, -30), fp=fps(0.85, 0.35, -35))),
        (0.15, P(ra=aim((-0.1, -0.1, 1), (-1, -0.25, 0.2)), torso=(10, 25), drop=0.5, fp=fps(0.0, -0.1, 30), hit=True)),
        (0.27, P(ra=aim((-0.6, -0.7, 0.4), (-0.6, -0.5, -0.6)), torso=(12, 40), drop=0.5, fp=fps(-0.7, -0.75, 75))),
        (0.48, P(READY, fp=FP_REST)),
    ])
    anim('slash_left', 0.50, [    # backhand: from the left shoulder back across to the right
        (0.00, P(READY, fp=FP_REST)),
        (0.09, P(ra=aim((-0.5, 0.6, 0.5), (-0.4, 0.4, -1)), torso=(2, 30), fp=fps(-0.4, 0.3, 40))),
        (0.15, P(ra=aim((0.3, -0.05, 1), (1, -0.2, 0.2)), torso=(10, -25), drop=0.5, fp=fps(0.3, -0.1, -20), hit=True)),
        (0.30, P(ra=aim((0.8, -0.5, 0.3), (0.5, -0.4, -0.7)), torso=(12, -35), drop=0.5, fp=fps(0.95, -0.6, -60))),
        (0.50, P(READY, fp=FP_REST)),
    ])
    anim('thrust', 0.50, [        # rapier chain 2/4 .. katana finisher 4/10: drive the point forward with the shoulder
        (0.00, P(READY, fp=FP_REST)),
        (0.10, P(ra=aim((0.1, -1, -0.5), (0, 0.35, 1)), rap=(0, 0, -2), torso=(-2, -30), fwd=-1, fp=fps(0.9, -0.75, -10, push=-3))),
        (0.17, P(ra=aim((0, -1, 0.35), (0, 0.08, 1)), rap=(0, 0.5, 4), torso=(12, 30), fwd=3, fp=fps(0.35, -0.35, 5, push=8), hit=True)),
        (0.30, P(ra=aim((0, -1, 0.3), (0, 0.08, 1)), rap=(0, 0.5, 3), torso=(10, 25), fwd=2.5, fp=fps(0.4, -0.4, 5, push=6))),
        (0.50, P(READY, fp=FP_REST)),
    ])
    anim('heavy_cleave', 1.2, [   # cutlass heavy 8/16: rising jump, diagonal cleave down to the left
        (0.00, P(READY, LEGS0, fp=FP_REST)),
        (0.22, P(ra=aim((0.5, 1, -0.2), (0.2, 0.1, -1)), la=(60, 30, 40), torso=(-10, -25), rl=(-10, 0, 4), ll=(15, 0, 4), up=1,
                 fp=fps(0.8, 0.5, -30, pitch=25))),
        (0.32, P(ra=aim((0.4, 1, 0.2), (0.3, -0.2, -1)), la=(70, 20, 50), torso=(-12, -30), rl=(-12, 0, 4), ll=(18, 0, 4), up=3,
                 fp=fps(0.75, 0.7, -35, pitch=35))),
        (0.40, P(DEEP_LUNGE, ra=aim((-0.2, 0.25, 1), (-0.6, -1, 0.3)), la=(30, 10, 45), torso=(22, 25), drop=2, fwd=4,
                 fp=fps(0.0, -0.2, 35, pitch=-20), hit=True)),
        (0.58, P(DEEP_LUNGE, ra=aim((-0.5, -0.5, 0.7), (-0.5, -1, -0.3)), la=(25, 10, 40), torso=(28, 35), drop=2.5, fwd=4,
                 fp=fps(-0.6, -0.85, 60, pitch=-30))),
        (0.88, P(READY, rl=(-8, 0, 2), ll=(10, 0, 2), fp=fps(0.7, -0.75, 0))),
        (1.20, P(LEGS0, fp=FP_REST)),
    ])
    anim('spin_slash', 0.70, [    # full turn to the left, blade extended, cut lands facing forward again
        (0.00, P(READY, LEGS0, fp=FP_REST)),
        (0.06, P(WIDE, ra=aim((0.9, 0.1, -0.4), (0.2, 0, 1)), torso=(4, -30), yaw=-25, drop=1, fp=fps(0.95, -0.1, -30))),
        (0.20, P(WIDE, ra=aim((0.3, 0, 1), (-1, 0.05, 0.2)), torso=(8, 15), yaw=360, drop=1.5, fp=fps(-0.1, -0.2, 35), hit=True)),
        (0.34, P(WIDE, ra=aim((-0.6, -0.4, 0.6), (-0.6, -0.2, -0.8)), torso=(10, 35), yaw=360, drop=1.5, fp=fps(-0.75, -0.6, 65))),
        (0.55, P(READY, rl=(-3, 0, 4), ll=(4, 0, 4), yaw=360, fp=fps(0.6, -0.7, 0))),
        (0.70, P(LEGS0, yaw=360, fp=FP_REST)),
    ])
    anim('rising_slash', 0.60, [  # weapon art: crouch low, cut upward into a small jump
        (0.00, P(READY, LEGS0, fp=FP_REST)),
        (0.12, P(LUNGE, ra=aim((0.45, -0.9, -0.3), (0.2, -0.2, 1)), la=(20, 0, 25), torso=(20, -20), drop=3, fp=fps(0.75, -0.95, -15, pitch=-20))),
        (0.22, P(ra=aim((0.05, 0.5, 1), (0, 1, -0.5)), la=(10, 0, 30), torso=(-10, 20), rl=(-12, 0, 3), ll=(30, 0, 3), up=4,
                 fp=fps(0.35, 0.1, 10, pitch=20), hit=True)),
        (0.36, P(ra=aim((0.1, 1, 0.1), (0, 0, -1)), la=(10, 0, 30), torso=(-12, 20), rl=(-8, 0, 3), ll=(20, 0, 3), up=5,
                 fp=fps(0.45, 0.7, 0, pitch=35))),
        (0.60, P(LEGS0, fp=FP_REST)),
    ])
    # ---------------------------------------------------------------- two-handed (nodachi, naginata, staff, hammer...)
    anim('heavy_overhead', 1.20, [  # vertical chop: hands overhead, blade dropped behind the head, chop to the ground
        (0.00, P(TWO_READY, LEGS0, fp=FP_REST)),
        (0.18, two((0.15, 0.8, 0.5), (0, 0.5, -1), torso=(-4, -5), rl=(-4, 0, 3), ll=(6, 0, 3), fp=fps(0.6, 0.2, -5, pitch=20))),
        (0.36, two((0.1, 1, -0.25), (0, -0.2, -1), torso=(-12, -8), rl=(-8, 0, 4), ll=(12, 0, 4), up=1.5, fp=fps(0.5, 0.75, -10, pitch=40))),
        (0.48, two((0, 0.45, 1), (0, -1, 0.45), torso=(18, 0), drop=1.5, fwd=2, rl=(-18, 0, 4), ll=(24, 0, 4),
                   fp=fps(0.25, -0.15, 0, pitch=-25), hit=True)),
        (0.62, two((0, -0.15, 1), (0, -1, 0.15), torso=(28, 0), drop=2.5, fwd=3, rl=(-20, 0, 4), ll=(26, 0, 4),
                   fp=fps(0.3, -0.85, 5, pitch=-40))),
        (0.90, P(TWO_READY, torso=(8, 0), rl=(-6, 0, 2), ll=(8, 0, 2), fp=fps(0.75, -0.75, 0))),
        (1.20, P(LEGS0, fp=FP_REST)),
    ])
    sweep('wide_slash', 0.90, 0.20, 0.30, 0.45, h=0.1, power=1.1)          # nodachi 6/12
    sweep('wide_slash_back', 1.05, 0.24, 0.35, 0.52, back=True, h=0.1, power=1.1)  # nodachi 7/14
    # ---------------------------------------------------------------- iaido
    anim('iai_stance', 2.0, [(0.0, IAI), (1.0, P(IAI, drop=2.4, torso=(15, 23))), (2.0, IAI)], loop=True)
    anim('iai_slash', 0.75, [     # quick draw: left hip -> sweeping out to the right
        (0.00, P(IAI, fp=fps(0.2, -0.85, 50))),
        (0.18, P(IAI, torso=(16, 28), drop=2.6, fp=fps(0.0, -0.9, 60))),
        (0.25, P(LUNGE, ra=aim((0.45, 0.05, 1), (1, 0, -0.45)), la=aim((0.2, -1, -0.2), (0, 0.1, -1)), torso=(12, -25), drop=1.5, fwd=3,
                 fp=fps(0.5, -0.15, -15), hit=True)),
        (0.42, P(LUNGE, ra=aim((1, 0.1, 0.15), (0.15, 0, -1)), la=aim((0.2, -1, -0.2), (0, 0.1, -1)), torso=(10, -35), drop=1.5, fwd=3,
                 fp=fps(1.05, -0.2, -50))),
        (0.75, P(LEGS0, fp=FP_REST)),
    ])
    anim('iai_dash', 0.70, [      # lion strike: blink through the target, blade trailing, then sheathe
        (0.00, P(IAI, fp=fps(0.2, -0.85, 50))),
        (0.08, P(DEEP_LUNGE, ra=aim((0.9, 0, 0.45), (0.45, 0, -0.9)), la=(-25, 0, 30), torso=(25, -20), drop=2, fwd=6,
                 fp=fps(1.0, -0.3, -55), hit=True)),
        (0.36, P(DEEP_LUNGE, ra=aim((0.9, -0.1, 0.4), (0.4, 0, -0.9)), la=(-20, 0, 30), torso=(22, -22), drop=2, fwd=5,
                 fp=fps(1.0, -0.35, -55))),
        (0.54, P(IAI, torso=(10, 15), drop=1, fp=fps(0.3, -0.85, 45))),
        (0.70, P(LEGS0, fp=FP_REST)),
    ])
    # ---------------------------------------------------------------- dual / three swords
    anim('dual_slash_right', 0.35, [
        (0.00, P(DUAL_READY, fp=FP_REST)),
        (0.07, P(ra=aim((0.7, 0.7, -0.1), (0.3, 0.3, -1)), la=(30, 10, 15), torso=(2, -25), fp=fps(0.85, 0.3, -30))),
        (0.12, P(ra=aim((-0.1, -0.1, 1), (-1, -0.25, 0.2)), la=(25, 15, 20), torso=(8, 25), fp=fps(0.0, -0.15, 30), hit=True)),
        (0.22, P(ra=aim((-0.6, -0.6, 0.45), (-0.6, -0.5, -0.6)), la=(25, 15, 20), torso=(10, 35), fp=fps(-0.65, -0.7, 70))),
        (0.35, P(DUAL_READY, fp=FP_REST)),
    ])
    mirror('dual_slash_right', 'dual_slash_left',
           fp={0.07: fps(0.9, -0.62, -6), 0.12: fps(0.98, -0.58, -12, push=-2), 0.22: fps(0.9, -0.62, -8)})
    anim('dual_cross', 0.45, [    # both blades from high and wide, crossing low in front
        (0.00, P(DUAL_READY, fp=FP_REST)),
        (0.08, P(ra=aim((0.75, 0.7, 0), (0.2, 0.2, -1)), la=aim((-0.75, 0.7, 0), (-0.2, 0.2, -1)), torso=(-6, 0), fp=fps(0.85, 0.4, -30))),
        (0.13, P(ra=aim((-0.35, -0.2, 1), (-1, -0.4, 0.1)), la=aim((0.35, -0.25, 1), (1, -0.4, 0.1)), torso=(12, 0), drop=1.5, fwd=2,
                 fp=fps(0.1, -0.3, 40), hit=True)),
        (0.25, P(ra=aim((-0.6, -0.6, 0.5), (-0.6, -0.6, -0.5)), la=aim((0.6, -0.6, 0.5), (0.6, -0.6, -0.5)), torso=(15, 0), drop=2, fwd=2,
                 fp=fps(-0.35, -0.85, 70))),
        (0.45, P(DUAL_READY, fp=FP_REST)),
    ])
    cross = P(ra=aim((-0.6, 0.5, 0.6), (-0.3, 0.5, -1)), la=aim((0.6, 0.55, 0.6), (0.3, 0.5, -1)))   # blades crossed at the chest
    flung = P(ra=aim((0.95, -0.3, 0.25), (0.5, -0.2, -0.8)), la=aim((-0.95, -0.3, 0.25), (-0.5, -0.2, -0.8)))  # cut out wide
    anim('dual_x_cut', 1.10, [    # nitoryu heavy 8/14 (lunge)
        (0.00, P(DUAL_READY, LEGS0, fp=FP_REST)),
        (0.28, P(cross, torso=(12, 0), drop=2, rl=(-12, 0, 4), ll=(18, 0, 4), fp=fps(-0.2, 0.15, 40))),
        (0.40, P(flung, DEEP_LUNGE, torso=(22, 0), drop=2.5, fwd=6, fp=fps(1.0, -0.45, -55), hit=True)),
        (0.65, P(flung, DEEP_LUNGE, torso=(20, 0), drop=2.5, fwd=6, fp=fps(1.0, -0.5, -55))),
        (0.88, P(DUAL_READY, rl=(-6, 0, 3), ll=(8, 0, 3), fp=fps(0.7, -0.7, 0))),
        (1.10, P(LEGS0, fp=FP_REST)),
    ])
    anim('triple_cut', 0.90, [    # santoryu: blades crossed (third in the mouth), dash-through cut
        (0.00, P(DUAL_READY, LEGS0, head=(0, 0), fp=FP_REST)),
        (0.20, P(cross, head=(12, 0), torso=(14, 0), drop=2.5, rl=(-14, 0, 5), ll=(20, 0, 5), fp=fps(-0.2, 0.2, 40))),
        (0.30, P(flung, DEEP_LUNGE, head=(4, 0), torso=(22, 0), drop=2, fwd=7, fp=fps(1.0, -0.45, -55), hit=True)),
        (0.56, P(flung, DEEP_LUNGE, head=(4, 0), torso=(20, 0), drop=2, fwd=6, fp=fps(1.0, -0.5, -55))),
        (0.90, P(LEGS0, fp=FP_REST)),
    ])
    anim('triple_dash', 0.70, [   # oni giri: crossed charge, three blades flung out behind
        (0.00, P(cross, LEGS0, head=(10, 0), torso=(14, 0), drop=2, fp=fps(-0.2, 0.15, 40))),
        (0.10, P(flung, DEEP_LUNGE, head=(4, 0), torso=(26, 0), drop=2.5, fwd=9, fp=fps(1.0, -0.45, -55), hit=True)),
        (0.40, P(flung, DEEP_LUNGE, head=(4, 0), torso=(22, 0), drop=2.5, fwd=8, fp=fps(1.0, -0.5, -55))),
        (0.70, P(LEGS0, fp=FP_REST)),
    ])
    anim('dual_dash', 0.60, [     # rhino charge: blades low like horns, then flung out on the pass
        (0.00, P(DUAL_READY, LEGS0, fp=FP_REST)),
        (0.06, P(ra=aim((0.25, -1, -0.2), (0, 0.4, 1)), la=aim((-0.25, -1, -0.2), (0, 0.4, 1)), torso=(32, 0), drop=1.5, fwd=5,
                 rl=(-35, 0, 3), ll=(40, 0, 3), fp=fps(0.55, -0.55, 0, push=6))),
        (0.20, P(flung, DEEP_LUNGE, torso=(20, 0), drop=2, fwd=6, fp=fps(1.0, -0.4, -50), hit=True)),
        (0.40, P(flung, DEEP_LUNGE, torso=(18, 0), drop=2, fwd=5, fp=fps(1.0, -0.45, -50))),
        (0.60, P(LEGS0, fp=FP_REST)),
    ])
    # ---------------------------------------------------------------- knife
    anim('stab', 0.30, [
        (0.00, P(ra=(22, 0, 8), fp=FP_REST)),
        (0.05, P(ra=aim((0.2, -1, -0.4), (0, 0.35, 1)), rap=(0, 0, -1.5), torso=(0, -20), fp=fps(0.9, -0.8, -8, push=-2))),
        (0.10, P(ra=aim((0, -1, 0.45), (0, 0.15, 1)), rap=(0, 0.5, 3.5), torso=(10, 25), fwd=2, fp=fps(0.4, -0.4, 5, push=7), hit=True)),
        (0.18, P(ra=aim((0, -1, 0.4), (0, 0.15, 1)), rap=(0, 0, 2.5), torso=(8, 18), fwd=1.5, fp=fps(0.45, -0.45, 5, push=5))),
        (0.30, P(fp=FP_REST)),
    ])
    anim('backstab', 0.80, [      # knife heavy 6/10 (lunge): coiled crouch, rising stab
        (0.00, P(LEGS0, ra=(22, 0, 8), fp=FP_REST)),
        (0.18, P(ra=aim((0.3, -0.9, -0.6), (0, 0.3, 1)), rap=(0, 0, -2), la=(50, -20, 10), torso=(20, -35), drop=2.5,
                 rl=(-15, 0, 6), ll=(28, 0, 6), fp=fps(0.95, -0.9, -12, push=-3))),
        (0.30, P(DEEP_LUNGE, ra=aim((-0.05, -0.7, 1), (0, 1, 0.7)), rap=(0, 0.5, 4), la=(20, 0, 30), torso=(28, 35), drop=3, fwd=6,
                 fp=fps(0.3, -0.2, 10, push=9), hit=True)),
        (0.50, P(DEEP_LUNGE, ra=aim((-0.05, -0.7, 1), (0, 1, 0.7)), rap=(0, 0, 3), la=(20, 0, 30), torso=(26, 32), drop=3, fwd=6,
                 fp=fps(0.35, -0.25, 10, push=7))),
        (0.80, P(LEGS0, fp=FP_REST)),
    ])


def polearms_and_heavy():
    # ---------------------------------------------------------------- spear / trident / staff thrusts
    anim('spear_thrust', 0.62, [
        (0.00, P(POLE_READY, LEGS0, fp=FP_REST)),
        (0.12, pole((0.2, -1, -0.3), (0, 0.3, 1), rap=(0, 0, -2), torso=(-3, -30), fwd=-1, rl=(-6, 0, 4), ll=(10, 0, 4),
                   fp=fps(0.95, -0.75, -8, push=-3))),
        (0.20, pole((0, -1, 0.4), (0, 0.12, 1), rap=(0, 0.5, 4), torso=(14, 25), fwd=4, rl=(-22, 0, 5), ll=(30, 0, 5),
                   fp=fps(0.3, -0.3, 4, push=10), hit=True)),
        (0.36, pole((0, -1, 0.35), (0, 0.12, 1), rap=(0, 0, 3), torso=(12, 20), fwd=3.5, rl=(-22, 0, 5), ll=(30, 0, 5),
                   fp=fps(0.35, -0.35, 4, push=8))),
        (0.62, P(POLE_READY, LEGS0, fp=FP_REST)),
    ])
    anim('staff_thrust', 0.95, [  # weather staff heavy 7/12
        (0.00, P(POLE_READY, LEGS0, fp=FP_REST)),
        (0.22, pole((0.2, -1, -0.3), (0, 0.3, 1), rap=(0, 0, -2), torso=(-4, -32), drop=1, fwd=-1, rl=(-8, 0, 4), ll=(12, 0, 4),
                   fp=fps(0.95, -0.8, -10, push=-3))),
        (0.35, pole((0, -1, 0.4), (0, 0.12, 1), LUNGE, rap=(0, 0.5, 4), torso=(16, 26), drop=1.5, fwd=5,
                   fp=fps(0.3, -0.3, 4, push=10), hit=True)),
        (0.58, pole((0, -1, 0.38), (0, 0.12, 1), LUNGE, rap=(0, 0, 3), torso=(14, 22), drop=1.5, fwd=4.5,
                   fp=fps(0.35, -0.35, 4, push=8))),
        (0.80, P(POLE_READY, rl=(-6, 0, 3), ll=(8, 0, 3), fp=fps(0.7, -0.7, 0))),
        (0.95, P(LEGS0, fp=FP_REST)),
    ])
    anim('heavy_thrust', 1.20, [  # rapier / spear / trident heavy 8-10 windup (lunge)
        (0.00, P(POLE_READY, LEGS0, fp=FP_REST)),
        (0.30, pole((0.22, -1, -0.35), (0, 0.35, 1), rap=(0, 0, -2.5), torso=(-6, -38), drop=1.5, fwd=-1.5, rl=(-14, 0, 5), ll=(22, 0, 5),
                   fp=fps(1.0, -0.8, -12, push=-4))),
        (0.45, pole((0, -1, 0.45), (0, 0.15, 1), DEEP_LUNGE, rap=(0, 0.5, 4.5), torso=(22, 30), drop=2, fwd=8,
                   fp=fps(0.25, -0.3, 4, push=12), hit=True)),
        (0.72, pole((0, -1, 0.42), (0, 0.15, 1), DEEP_LUNGE, rap=(0, 0, 3.5), torso=(20, 26), drop=2, fwd=7,
                   fp=fps(0.3, -0.35, 4, push=10))),
        (0.95, P(POLE_READY, rl=(-8, 0, 3), ll=(10, 0, 3), fp=fps(0.7, -0.7, 0))),
        (1.20, P(LEGS0, fp=FP_REST)),
    ])
    # ---------------------------------------------------------------- sweeps (two-handed)
    sweep('polearm_sweep', 0.60, 0.11, 0.18, 0.32, hold=pole)                  # naginata 4/9, storm dance 3/6
    sweep('polearm_sweep_back', 0.60, 0.11, 0.18, 0.32, back=True, hold=pole)
    sweep('staff_swing', 0.45, 0.09, 0.15, 0.26, power=0.8, hold=pole)         # bo staff 3/6
    sweep('staff_swing_back', 0.45, 0.09, 0.15, 0.26, back=True, power=0.8, hold=pole)
    sweep('spear_sweep', 0.90, 0.20, 0.30, 0.46, h=-0.5, power=1.1, yaw=20, hold=pole)   # spear 6/12, 220 deg arc
    sweep('heavy_swing', 0.95, 0.22, 0.32, 0.50, power=1.3, yaw=15)           # kanabo / hammer 6-8 windup
    sweep('heavy_swing_back', 1.25, 0.32, 0.45, 0.65, back=True, power=1.3, yaw=15)
    sweep('chain_swing', 0.95, 0.22, 0.35, 0.55, h=0.2, power=1.2, yaw=10)    # anchor chain 6-8 windup
    anim('polearm_spin', 0.80, [  # full turn with the pole extended (naginata / staff / storm dance finisher)
        (0.00, P(POLE_READY, LEGS0, fp=FP_REST)),
        (0.08, pole((0.9, 0.0, -0.2), (0.2, 0.05, -1), WIDE, torso=(6, -35), drop=1.5, yaw=-25, fp=fps(1.0, -0.3, -30))),
        (0.25, pole((0.95, -0.05, 0.25), (-0.2, 0, 1), WIDE, torso=(10, 15), drop=2, yaw=360, fp=fps(-0.2, -0.3, 35), hit=True)),
        (0.42, pole((-0.6, -0.2, 0.7), (-0.75, 0, -0.6), WIDE, torso=(12, 35), drop=2, yaw=360, fp=fps(-0.85, -0.6, 65))),
        (0.62, P(POLE_READY, rl=(-3, 0, 4), ll=(4, 0, 4), yaw=360, fp=fps(0.6, -0.7, 0))),
        (0.80, P(LEGS0, yaw=360, fp=FP_REST)),
    ])
    anim('ground_slam', 1.30, [   # kanabo / hammer heavy 16/26 and ground-pound techniques
        (0.00, P(TWO_READY, LEGS0, fp=FP_REST)),
        (0.25, two((0.1, 0.8, 0.6), (0, 0.6, -1), torso=(-6, 0), rl=(-4, 0, 6), ll=(6, 0, 6), fp=fps(0.6, 0.3, -5, pitch=20))),
        (0.52, two((0.05, 1, -0.3), (0, -0.3, -1), torso=(-15, 0), up=1.5, rl=(-6, 0, 8), ll=(6, 0, 8), fp=fps(0.5, 0.8, -8, pitch=45))),
        (0.74, two((0, -0.3, 1), (0, -1, -0.3), torso=(40, 0), drop=3, fwd=2, rl=(-22, 0, 12), ll=(26, 0, 12),
                   fp=fps(0.3, -0.8, 0, pitch=-45), hit=True)),
        (0.94, two((0, -0.35, 1), (0, -1, -0.35), torso=(38, 0), drop=3, fwd=2, rl=(-22, 0, 12), ll=(26, 0, 12),
                   fp=fps(0.3, -0.85, 0, pitch=-45))),
        (1.04, two((0.05, -0.6, 1), (0, 0.6, 0.6), torso=(24, 0), drop=2, fwd=1, rl=(-14, 0, 8), ll=(16, 0, 8), fp=fps(0.5, -0.8, 0))),
        (1.16, P(TWO_READY, torso=(8, 0), rl=(-6, 0, 4), ll=(8, 0, 4), fp=fps(0.7, -0.75, 0))),
        (1.30, P(LEGS0, fp=FP_REST)),
    ])
    # ---------------------------------------------------------------- anchor chain / whip
    anim('chain_spin', 1.05, [    # anchor chain 7-9 windup: whirl the anchor around once and release
        (0.00, P(TWO_READY, LEGS0, fp=FP_REST)),
        (0.18, two((0.9, 0.1, -0.3), (0.2, 0.1, -1), WIDE, torso=(5, -40), drop=2, yaw=-30, fp=fps(1.0, -0.2, -35))),
        (0.40, P(WIDE, ra=aim((1, 0.15, 0.1), (0, 0.1, 1)), la=aim((-0.4, -0.3, 0.6), (0, 0.6, 0.6)), torso=(8, 10), drop=2.5, yaw=360,
                 fp=fps(-0.1, -0.25, 35), hit=True)),
        (0.62, P(WIDE, ra=aim((-0.4, -0.2, 0.9), (-0.9, 0, -0.4)), la=(40, 10, 30), torso=(10, 35), drop=2, yaw=360, fp=fps(-0.85, -0.6, 65))),
        (0.85, P(TWO_READY, rl=(-4, 0, 5), ll=(5, 0, 5), yaw=360, fp=fps(0.6, -0.7, 0))),
        (1.05, P(LEGS0, yaw=360, fp=FP_REST)),
    ])
    anim('anchor_throw', 1.30, [  # anchor heavy 12-14/22-24: heave overhead and hurl forward
        (0.00, P(TWO_READY, LEGS0, fp=FP_REST)),
        (0.14, two((0.05, 0.2, 1), (0, 1, -0.2), torso=(-4, -10), rl=(-8, 0, 4), ll=(12, 0, 4), fp=fps(0.6, 0.2, -5, pitch=20))),
        (0.30, two((0.1, 1, -0.45), (0, -0.4, -1), torso=(-15, -20), drop=1,
                 rl=(-14, 0, 5), ll=(20, 0, 5), fp=fps(0.6, 0.85, -10, pitch=45))),
        (0.50, two((0.05, 1, 0.1), (0, 0.1, -1), torso=(-6, -10), drop=1,
                 rl=(-14, 0, 5), ll=(20, 0, 5), fp=fps(0.5, 0.6, 0, pitch=25))),
        (0.62, two((0.02, 0.3, 1), (0, 1, -0.3), LUNGE, torso=(25, 10), drop=2, fwd=4,
                 fp=fps(0.3, 0.0, 0, push=8), hit=True)),
        (0.85, P(LUNGE, ra=(55, -20, 0), la=(50, -25, 0), torso=(30, 15), drop=2, fwd=4, fp=fps(0.2, -0.6, 10, push=4))),
        (1.10, P(TWO_READY, torso=(8, 0), rl=(-8, 0, 3), ll=(10, 0, 3), fp=fps(0.7, -0.75, 0))),
        (1.30, P(LEGS0, fp=FP_REST)),
    ])
    anim('whip_crack', 0.60, [    # overhead crack
        (0.00, P(READY, fp=FP_REST)),
        (0.12, P(ra=aim((0.2, 1, -0.35), (0, -0.3, -1)), torso=(-10, -15), fp=fps(0.75, 0.75, -15, pitch=40))),
        (0.20, P(ra=aim((0.05, 0.1, 1), (0, -1, 0.1)), rap=(0, 0, 2), torso=(15, 15), fwd=1.5, fp=fps(0.35, -0.2, 0, pitch=-30, push=4), hit=True)),
        (0.30, P(ra=aim((0.05, 0.25, 1), (0, 1, -0.2)), rap=(0, 0, 1), torso=(10, 10), fwd=1, fp=fps(0.4, -0.05, 0, pitch=10))),
        (0.60, P(fp=FP_REST)),
    ])
    anim('whip_sweep', 0.60, [    # side-arm sweep right -> left
        (0.00, P(READY, fp=FP_REST)),
        (0.12, P(ra=aim((0.9, -0.1, -0.45), (0.45, 0, -0.9)), torso=(2, -40), drop=1, fp=fps(1.05, -0.35, -30))),
        (0.20, P(ra=aim((0.05, -0.1, 1), (-1, 0.1, 0.05)), torso=(10, 25), drop=1.5, fwd=1, fp=fps(0.0, -0.35, 20), hit=True)),
        (0.32, P(ra=aim((-0.8, -0.25, 0.5), (-0.5, 0.1, -0.85)), torso=(12, 40), drop=1.5, fwd=1, fp=fps(-0.85, -0.55, 50))),
        (0.60, P(fp=FP_REST)),
    ])
    anim('whip_lash', 1.10, [     # whip heavy 8/14: whirl overhead, then a stepping lash
        (0.00, P(READY, LEGS0, fp=FP_REST)),
        (0.10, P(ra=aim((0.6, 1, 0.3), (0.3, -0.3, 1)), torso=(-4, -10), rl=(-4, 0, 4), ll=(6, 0, 4), fp=fps(0.7, 0.6, -20))),
        (0.20, P(ra=aim((-0.3, 1, 0.2), (-1, -0.2, 0)), torso=(-4, 10), rl=(-4, 0, 4), ll=(6, 0, 4), fp=fps(0.2, 0.75, 30))),
        (0.30, P(ra=aim((0.3, 1, -0.4), (0.4, -0.3, -1)), torso=(-12, -25), rl=(-12, 0, 5), ll=(18, 0, 5), fp=fps(0.75, 0.8, -30, pitch=35))),
        (0.40, P(LUNGE, ra=aim((0, 0.15, 1), (0, -1, 0.15)), rap=(0, 0, 3), torso=(22, 20), drop=1.5, fwd=4,
                 fp=fps(0.3, -0.15, 0, pitch=-30, push=6), hit=True)),
        (0.62, P(LUNGE, ra=aim((0, -0.2, 1), (0, -1, -0.2)), rap=(0, 0, 2), torso=(22, 18), drop=1.5, fwd=4,
                 fp=fps(0.35, -0.5, 0, pitch=-35, push=4))),
        (0.90, P(READY, rl=(-6, 0, 3), ll=(8, 0, 3), fp=fps(0.75, -0.75, 0))),
        (1.10, P(LEGS0, fp=FP_REST)),
    ])

def unarmed():
    anim('jab_right', 0.30, [     # brawler / gauntlets / tonfa 2/4
        (0.00, P(FISTS, fp=FP_REST)),
        (0.05, P(FISTS, ra=(70, -18, 4), rap=(0, 0, -1), torso=(6, -16), fp=fps(0.85, -0.75, -5, push=-2))),
        (0.10, P(FISTS, ra=(90, -6, 0), rap=(0, 0.5, 3.5), torso=(9, 18), fp=FP_JAB, hit=True)),
        (0.18, P(FISTS, ra=(84, -14, 0), rap=(0, 0, 1), torso=(7, 6), fp=fps(0.5, -0.5, 4, push=2))),
        (0.30, P(FISTS, fp=FP_REST)),
    ])
    mirror('jab_right', 'jab_left', fp={0.05: fps(0.85, -0.7, -4), 0.10: fps(0.95, -0.62, -10, push=-2), 0.18: fps(0.88, -0.68, -6)})
    anim('hook_right', 0.45, [
        (0.00, P(FISTS, fp=FP_REST)),
        (0.09, P(FISTS, ra=(80, 45, 25), torso=(4, -30), drop=1, fp=fps(1.05, -0.45, -25))),
        (0.15, P(FISTS, ra=(90, -40, 10), rap=(0, 0, 2), torso=(10, 30), drop=1.5, fp=fps(0.1, -0.35, 30), hit=True)),
        (0.27, P(FISTS, ra=(85, -60, 5), torso=(10, 40), drop=1, fp=fps(-0.35, -0.5, 45))),
        (0.45, P(FISTS, fp=FP_REST)),
    ])
    anim('uppercut', 0.62, [
        (0.00, P(FISTS, LEGS0, fp=FP_REST)),
        (0.10, P(FISTS, ra=(15, -5, 15), torso=(15, -25), drop=3, rl=(-12, 0, 6), ll=(16, 0, 6), fp=fps(0.75, -1.0, -10))),
        (0.20, P(FISTS, ra=(160, -15, 0), rap=(0, 2, 1), torso=(-12, 25), up=3, rl=(-5, 0, 4), ll=(5, 0, 4),
                 fp=fps(0.35, 0.25, 15, pitch=20), hit=True)),
        (0.36, P(FISTS, ra=(150, -15, 0), torso=(-8, 20), up=1.5, rl=(-5, 0, 4), ll=(5, 0, 4), fp=fps(0.4, 0.15, 12, pitch=15))),
        (0.62, P(FISTS, LEGS0, fp=FP_REST)),
    ])
    anim('heavy_punch', 1.10, [   # style heavies 7-8/13-15 (lunge) and haymaker techniques
        (0.00, P(FISTS, LEGS0, fp=FP_REST)),
        (0.25, P(FISTS, WIDE, ra=(-35, 15, 25), rap=(0, 0, -2), la=(85, -15, 0), torso=(-4, -40), drop=1.5,
                 fp=fps(1.05, -0.9, -20, push=-4))),
        (0.40, P(DEEP_LUNGE, ra=(92, -6, 0), rap=(0, 0.5, 4.5), la=(35, 10, 25), torso=(18, 35), drop=2, fwd=5,
                 fp=fps(0.25, -0.25, 5, push=10), hit=True)),
        (0.62, P(DEEP_LUNGE, ra=(90, -6, 0), rap=(0, 0.5, 4), la=(35, 10, 25), torso=(16, 32), drop=2, fwd=5,
                 fp=fps(0.3, -0.3, 5, push=8))),
        (0.88, P(FISTS, rl=(-8, 0, 3), ll=(10, 0, 3), fp=fps(0.7, -0.7, 0))),
        (1.10, P(LEGS0, fp=FP_REST)),
    ])
    anim('punch_extend', 0.60, [  # gomu pistol / fire fist / sango / chop cannon (effect fires on the first tick)
        (0.00, P(FISTS, LEGS0, fp=FP_REST)),
        (0.05, P(FISTS, ra=(40, 10, 20), rap=(0, 0, -2), torso=(2, -30), rl=(-8, 0, 4), ll=(12, 0, 4), fp=fps(1.0, -0.85, -15, push=-3))),
        (0.12, P(LUNGE, ra=(92, -5, 0), rap=(0, 0.5, 5), la=(40, 10, 25), torso=(14, 32), drop=1, fwd=3, fp=fps(0.25, -0.25, 5, push=14))),
        (0.40, P(LUNGE, ra=(91, -5, 0), rap=(0, 0.5, 4.5), la=(40, 10, 25), torso=(13, 30), drop=1, fwd=3, fp=fps(0.28, -0.28, 5, push=12))),
        (0.60, P(LEGS0, fp=FP_REST)),
    ])
    anim('palm_strike', 0.45, [
        (0.00, P(FISTS, fp=FP_REST)),
        (0.07, P(FISTS, ra=(25, 5, 18), rap=(0, 0, -2), torso=(4, -22), drop=1, fp=fps(0.95, -0.9, -10, push=-3))),
        (0.15, P(ra=(88, -8, 0), la=(60, -30, 0), rap=(0, 0.5, 3.5), torso=(12, 28), drop=1.5, fwd=2, fp=fps(0.3, -0.3, -10, push=8), hit=True)),
        (0.28, P(ra=(86, -8, 0), la=(60, -30, 0), rap=(0, 0.5, 3), torso=(11, 26), drop=1.5, fwd=2, fp=fps(0.33, -0.33, -10, push=6))),
        (0.45, P(FISTS, fp=FP_REST)),
    ])
    anim('double_palm', 0.80, [   # rokuogan / gomu bazooka / dragon flow burst
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.12, P(ra=(-25, 0, 22), la=(-25, 0, 22), torso=(-6, 0), drop=1.5, rl=(-6, 0, 4), ll=(8, 0, 4), fp=fps(0.95, -0.95, -10, push=-3))),
        (0.22, P(LUNGE, ra=(88, -12, 0), la=(88, -12, 0), rap=(0, 0.5, 3), lap=(0, 0.5, 3), torso=(15, 0), drop=1.5, fwd=3,
                 fp=fps(0.25, -0.25, -8, push=10))),
        (0.52, P(LUNGE, ra=(87, -12, 0), la=(87, -12, 0), rap=(0, 0.5, 2.5), lap=(0, 0.5, 2.5), torso=(14, 0), drop=1.5, fwd=3,
                 fp=fps(0.28, -0.28, -8, push=9))),
        (0.80, P(LEGS0, fp=FP_REST)),
    ])
    anim('finger_pistol', 0.55, [  # shigan
        (0.00, P(FISTS, fp=FP_REST)),
        (0.08, P(FISTS, ra=(45, 10, 12), rap=(0, 0, -2), torso=(4, -28), fp=fps(1.0, -0.85, -12, push=-3))),
        (0.15, P(ra=(93, -8, 0), la=(-15, 0, 15), rap=(0, 0.5, 4.5), torso=(12, 28), drop=1, fwd=2, fp=fps(0.25, -0.2, 0, push=12), hit=True)),
        (0.32, P(ra=(92, -8, 0), la=(-15, 0, 15), rap=(0, 0.5, 4), torso=(11, 26), drop=1, fwd=2, fp=fps(0.28, -0.22, 0, push=10))),
        (0.55, P(FISTS, fp=FP_REST)),
    ])
    keys = [(0.0, P(FISTS, LEGS0, fp=FP_REST)),
            (0.08, P(FISTS, rl=(-18, 0, 8), ll=(22, 0, 8), torso=(12, 0), drop=1.5, fp=fps(0.6, -0.6, 0)))]
    t, right = 0.14, True
    while t < 1.0:                # gomu gatling: alternating pistons
        ext, ret = (92, -6, 0), (50, -12, 18)
        keys.append((round(t, 3), P(rl=(-18, 0, 8), ll=(22, 0, 8), drop=1.5, ra=ext if right else ret, la=ret if right else ext,
                                    rap=(0, 0.5, 4) if right else (0, 0, -1.5), lap=(0, 0, -1.5) if right else (0, 0.5, 4),
                                    torso=(12, 14 if right else -14),
                                    fp=fps(0.3, -0.3, 6, push=8) if right else fps(0.9, -0.7, -8, push=-2))))
        t += 0.09
        right = not right
    keys.append((1.2, P(LEGS0, fp=FP_REST)))
    anim('gatling', 1.2, keys)
    anim('giant_punch', 1.20, [   # gear third: huge wind-up, inflated fist
        (0.00, P(FISTS, LEGS0, fp=FP_REST)),
        (0.35, P(WIDE, ra=(-40, 20, 30), rap=(0, 0, -3), la=(85, -15, 0), torso=(-8, -45), drop=1.5, fp=fps(1.05, -0.9, -20, push=-4))),
        (0.55, P(DEEP_LUNGE, ra=(95, -6, 0), rap=(0, 1, 5), la=(30, 10, 30), torso=(20, 38), drop=2, fwd=6, fp=fps(0.2, -0.1, 5, push=10))),
        (0.85, P(DEEP_LUNGE, ra=(93, -6, 0), rap=(0, 1, 4.5), la=(30, 10, 30), torso=(18, 35), drop=2, fwd=6, fp=fps(0.22, -0.12, 5, push=9))),
        (1.20, P(LEGS0, fp=FP_REST)),
    ])
    anim('grab', 0.45, [          # tide grapple 3/6, undertow / ice time / undertow grip
        (0.00, P(fp=FP_REST)),
        (0.08, P(ra=(80, 15, 10), la=(80, 15, 10), torso=(4, 0), fp=fps(0.95, -0.45, -15))),
        (0.15, P(ra=(92, -12, 0), la=(92, -12, 0), rap=(0, 0, 3), lap=(0, 0, 3), torso=(15, 0), drop=1, fwd=2,
                 fp=fps(0.35, -0.3, 10, push=6), hit=True)),
        (0.30, P(ra=(70, -20, 0), la=(70, -20, 0), rap=(0, 0, -1), lap=(0, 0, -1), torso=(-4, 0), fwd=-1, fp=fps(0.6, -0.55, 5, push=-3))),
        (0.45, P(fp=FP_REST)),
    ])
    anim('throw_grab', 0.90, [    # riptide throw: grab, turn in, over-the-shoulder throw
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.12, P(ra=(92, -12, 0), la=(92, -12, 0), rap=(0, 0, 3), lap=(0, 0, 3), torso=(12, 0), drop=1, fwd=2, rl=(-10, 0, 4), ll=(14, 0, 4),
                 fp=fps(0.35, -0.3, 10, push=5))),
        (0.32, P(ra=(165, 10, 0), la=(160, 25, 0), torso=(25, -35), drop=3, rl=(-14, 0, 10), ll=(14, 0, 10), yaw=-25,
                 fp=fps(0.7, 0.6, -30, pitch=35))),
        (0.50, P(ra=(40, -30, 0), la=(40, -35, 0), torso=(50, 20), drop=3, fwd=2, rl=(-20, 0, 10), ll=(20, 0, 10), yaw=10,
                 fp=fps(0.0, -0.9, 40, pitch=-40))),
        (0.70, P(ra=(30, -20, 10), la=(30, -20, 10), torso=(30, 10), drop=2, rl=(-10, 0, 6), ll=(12, 0, 6), fp=fps(0.5, -0.8, 10))),
        (0.90, P(LEGS0, fp=FP_REST)),
    ])
    anim('slam', 1.25, [          # tide grapple heavy 9/16: grab, lift overhead, slam down
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.15, P(ra=(92, -15, 0), la=(92, -15, 0), rap=(0, 0, 2), lap=(0, 0, 2), torso=(10, 0), drop=1, rl=(-6, 0, 8), ll=(8, 0, 8),
                 fp=fps(0.35, -0.3, 10, push=5))),
        (0.35, P(ra=(172, -10, 0), la=(172, -10, 0), torso=(-18, 0), up=1, rl=(-6, 0, 9), ll=(8, 0, 9), fp=fps(0.5, 0.85, 0, pitch=40))),
        (0.45, P(ra=(45, -15, 0), la=(45, -15, 0), torso=(42, 0), drop=3, fwd=2, rl=(-22, 0, 12), ll=(26, 0, 12),
                 fp=fps(0.3, -0.9, 0, pitch=-45), hit=True)),
        (0.75, P(ra=(45, -15, 0), la=(45, -15, 0), torso=(40, 0), drop=3, fwd=2, rl=(-22, 0, 12), ll=(26, 0, 12),
                 fp=fps(0.3, -0.9, 0, pitch=-45))),
        (1.00, P(ra=(20, 0, 10), la=(20, 0, 10), torso=(12, 0), rl=(-6, 0, 4), ll=(8, 0, 4), fp=fps(0.7, -0.8, 0))),
        (1.25, P(LEGS0, fp=FP_REST)),
    ])
    anim('headbutt', 0.55, [
        (0.00, P(LEGS0, head=(0, 0))),
        (0.15, P(head=(-25, 0), torso=(-20, 0), ra=(40, 0, 30), la=(40, 0, 30), rl=(-6, 0, 4), ll=(10, 0, 4))),
        (0.24, P(head=(35, 0), torso=(30, 0), ra=(20, 0, 25), la=(20, 0, 25), fwd=4, drop=1, rl=(-22, 0, 4), ll=(28, 0, 4), hit=True)),
        (0.36, P(head=(30, 0), torso=(26, 0), ra=(20, 0, 25), la=(20, 0, 25), fwd=4, drop=1, rl=(-22, 0, 4), ll=(28, 0, 4))),
        (0.55, P(LEGS0, head=(0, 0))),
    ])
    # ---------------------------------------------------------------- claws (electro / zoan)
    r_up, r_down = aim((0.6, 0.75, 0.2), (0.2, -0.2, 1)), aim((-0.35, -0.2, 1), (-1, 0.2, -0.3))
    r_follow = aim((-0.6, -0.7, 0.4), (-0.5, 0.5, -0.6))
    anim('claw_right', 0.30, [
        (0.00, P(FISTS, fp=FP_REST)),
        (0.05, P(FISTS, ra=r_up, torso=(0, -20), fp=fps(0.9, 0.3, -30))),
        (0.10, P(FISTS, ra=r_down, torso=(10, 25), drop=1, fp=fps(0.1, -0.35, 30), hit=True)),
        (0.18, P(FISTS, ra=r_follow, torso=(10, 30), drop=1, fp=fps(-0.6, -0.8, 60))),
        (0.30, P(FISTS, fp=FP_REST)),
    ])
    mirror('claw_right', 'claw_left', fp={0.05: fps(0.85, -0.7, -4), 0.10: fps(0.95, -0.62, -10, push=-2), 0.18: fps(0.88, -0.68, -6)})
    low = (40, 5, 15)
    keys = [(0.0, P(FISTS, LEGS0, fp=FP_REST))]
    for i, t in enumerate((0.06, 0.24, 0.42, 0.60)):
        r = i % 2 == 0
        keys.append((t, P(rl=(-10, 0, 6), ll=(14, 0, 6), drop=1, ra=r_up if r else low, la=low if r else _mirror_arm(r_up),
                          torso=(4, -20 if r else 20), fp=fps(0.9, 0.3, -30) if r else fps(0.8, -0.7, -5))))
        keys.append((t + 0.07, P(rl=(-10, 0, 6), ll=(14, 0, 6), drop=1.5, fwd=1, ra=r_down if r else low, la=low if r else _mirror_arm(r_down),
                                 torso=(10, 25 if r else -25), fp=fps(0.1, -0.35, 30) if r else fps(0.9, -0.65, -10))))
    keys.append((0.9, P(LEGS0, fp=FP_REST)))
    anim('claw_flurry', 0.9, keys)
    # ---------------------------------------------------------------- rubber & co
    anim('rocket', 0.80, [        # gomu rocket / bara flying chop: launch forward, arms out ahead
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.12, P(ra=(-40, 0, 20), la=(-40, 0, 20), torso=(20, 0), drop=2, rl=(-15, 0, 4), ll=(25, 0, 4), fp=fps(0.85, -0.95, -10, push=-3))),
        (0.26, P(ra=(150, -6, 0), la=(150, -6, 0), pitch=55, up=6, fwd=6, rl=(-12, 0, 2), ll=(-8, 0, 2), fp=fps(0.35, 0.3, 0, push=12))),
        (0.55, P(ra=(160, -6, 0), la=(160, -6, 0), pitch=60, up=6, fwd=6, rl=(-10, 0, 2), ll=(-6, 0, 2), fp=fps(0.35, 0.3, 0, push=12))),
        (0.80, P(LEGS0, fp=FP_REST)),
    ])
    g2 = P(ra=(30, 0, 15), la=(70, -20, 0), torso=(28, 0), drop=2, rl=(-60, 0, 8), ll=(55, 0, 8), head=(-5, 0))
    anim('gear_second', 1.20, [   # low crouch, knuckles to the ground, pumping
        (0.00, P(LEGS0, head=(0, 0))),
        (0.25, g2), (0.45, P(g2, drop=3)), (0.65, g2), (0.85, P(g2, drop=3)),
        (1.20, P(LEGS0, head=(0, 0))),
    ])
    anim('nika', 1.40, [          # drums of liberation: bouncing dance
        (0.00, P(LEGS0, head=(0, 0))),
        (0.15, P(ra=(20, 0, 150), la=(20, 0, 150), torso=(-8, 0), head=(-15, 10), rl=(40, 0, 10), ll=(0, 0, 4), up=3)),
        (0.40, P(ra=(20, 0, 120), la=(20, 0, 150), torso=(-5, 15), head=(-10, -15), rl=(0, 0, 4), ll=(40, 0, 10))),
        (0.65, P(ra=(20, 0, 150), la=(20, 0, 120), torso=(-8, -15), head=(-15, 15), rl=(40, 0, 10), ll=(0, 0, 4), up=3)),
        (0.90, P(ra=(30, 0, 160), la=(30, 0, 160), torso=(-12, 0), head=(-25, 0), rl=(0, 0, 6), ll=(0, 0, 6), up=4)),
        (1.15, P(ra=(20, 0, 140), la=(20, 0, 140), torso=(-6, 0), head=(-10, 0), rl=(0, 0, 3), ll=(0, 0, 3))),
        (1.40, P(LEGS0, head=(0, 0))),
    ])


def kicks():
    anim('kick_low', 0.40, [      # sweep across the shins
        (0.00, P(FISTS, LEGS0, fp=FP_REST)),
        (0.08, P(FISTS, rl=(-22, 25, 12), ll=(0, 0, 2), torso=(0, -20), drop=1, fp=FP_DIP)),
        (0.15, P(FISTS, rl=(38, -38, 10), ll=(-4, 0, 2), torso=(10, 28), drop=2, fp=fps(0.8, -0.85, 5), hit=True)),
        (0.26, P(FISTS, rl=(28, -48, 8), ll=(-4, 0, 2), torso=(10, 32), drop=1.5, fp=FP_DIP)),
        (0.40, P(FISTS, LEGS0, fp=FP_REST)),
    ])
    anim('kick_mid', 0.40, [      # roundhouse to the body, pivoting on the left foot
        (0.00, P(FISTS, LEGS0, fp=FP_REST)),
        (0.08, P(FISTS, rl=(55, 60, 0), ll=(0, 0, 0), torso=(-10, -15), yaw=-10, fp=FP_DIP)),
        (0.15, P(FISTS, rl=(88, -12, 0), ll=(0, 0, 0), torso=(-18, 25), yaw=22, fp=fps(0.7, -0.85, 10), hit=True)),
        (0.26, P(FISTS, rl=(70, -30, 0), ll=(0, 0, 0), torso=(-12, 30), yaw=28, fp=FP_DIP)),
        (0.40, P(FISTS, LEGS0, fp=FP_REST)),
    ])
    anim('kick_high', 0.45, [     # high front kick (collier shoot, swan kick, rankyaku)
        (0.00, P(FISTS, LEGS0, fp=FP_REST)),
        (0.08, P(FISTS, rl=(60, 0, 0), ll=(0, 0, 0), torso=(-8, 0), fp=FP_DIP)),
        (0.15, P(ra=(60, -20, 25), la=(40, 0, 45), rl=(138, -6, 0), ll=(-4, 0, 0), torso=(-24, 10), fp=fps(0.75, -0.9, 10), hit=True)),
        (0.28, P(ra=(60, -20, 25), la=(40, 0, 45), rl=(115, 0, 0), ll=(-4, 0, 0), torso=(-18, 6), fp=FP_DIP)),
        (0.45, P(FISTS, LEGS0, fp=FP_REST)),
    ])
    anim('kick_heavy', 1.10, [    # black leg / okama heavy 8/14-15: chambered knee, lunging front kick
        (0.00, P(FISTS, LEGS0, fp=FP_REST)),
        (0.25, P(FISTS, rl=(75, 0, 0), ll=(-4, 0, 0), torso=(-4, -10), drop=1, fp=FP_DIP)),
        (0.40, P(ra=(35, 0, 50), la=(35, 0, 55), rl=(100, -4, 0), ll=(-10, 0, 0), torso=(-26, 5), fwd=4, fp=fps(0.7, -0.95, 12), hit=True)),
        (0.62, P(ra=(35, 0, 50), la=(35, 0, 55), rl=(96, -4, 0), ll=(-10, 0, 0), torso=(-22, 5), fwd=4, fp=fps(0.72, -0.92, 10))),
        (0.85, P(FISTS, rl=(15, 0, 0), ll=(0, 0, 0), torso=(4, 0), fp=FP_DIP)),
        (1.10, P(LEGS0, fp=FP_REST)),
    ])
    anim('spin_kick', 0.70, [     # spinning roundhouse (full turn left), also diable jambe / pirouette
        (0.00, P(FISTS, LEGS0, fp=FP_REST)),
        (0.06, P(FISTS, rl=(10, 30, 0), ll=(0, 0, 0), torso=(0, -20), yaw=-20, drop=1, fp=FP_DIP)),
        (0.15, P(ra=(40, 0, 60), la=(40, 0, 60), rl=(70, 70, 0), ll=(-4, 0, 0), torso=(-12, 0), tween=True, fp=fps(0.6, -1.0, 15))),
        (0.22, P(ra=(40, 0, 55), la=(40, 0, 55), rl=(88, 5, 0), ll=(-4, 0, 0), torso=(-16, 15), yaw=360, fp=fps(0.7, -0.9, 10), hit=True)),
        (0.36, P(FISTS, rl=(55, -30, 0), ll=(-2, 0, 0), torso=(-8, 20), yaw=360, fp=FP_DIP)),
        (0.70, P(FISTS, LEGS0, yaw=360, fp=FP_REST)),
    ])
    anim('axe_kick', 1.00, [      # concasse: the server hops you up and lands the hit 14 ticks later
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.08, P(ra=(-25, 0, 20), la=(-25, 0, 20), torso=(10, 0), drop=1.5, rl=(8, 0, 3), ll=(8, 0, 3), fp=FP_DIP)),
        (0.22, P(ra=(60, 0, 60), la=(60, 0, 60), torso=(-8, 0), rl=(95, 0, 0), ll=(-15, 0, 0), up=1, fp=fps(0.7, -0.6, 10))),
        (0.45, P(ra=(40, 0, 75), la=(40, 0, 75), torso=(-12, 0), rl=(168, 0, 0), ll=(-20, 0, 0), pitch=-8, up=1, fp=fps(0.75, -0.5, 10, pitch=15))),
        (0.68, P(ra=(30, 0, 40), la=(30, 0, 40), torso=(25, 0), rl=(25, 0, 0), ll=(-25, 0, 0), pitch=10, drop=2,
                 fp=fps(0.6, -0.95, 5, pitch=-25), hit=True)),
        (0.82, P(ra=(30, 0, 35), la=(30, 0, 35), torso=(20, 0), rl=(20, 0, 2), ll=(-22, 0, 2), drop=2, fp=fps(0.65, -0.9, 0))),
        (1.00, P(LEGS0, fp=FP_REST)),
    ])
    hs = P(ra=(180, 0, 12), la=(180, 0, 12), rl=(0, 0, 55), ll=(0, 0, 55), pitch=180, plant='all', fp=FP_DIP)
    dive = P(ra=(170, 0, 10), la=(170, 0, 10), rl=(-10, 0, 0), ll=(10, 0, 0), torso=(20, 0), plant='all', fp=FP_DIP)
    over = P(ra=(120, 0, 15), la=(120, 0, 15), rl=(40, 0, 20), ll=(20, 0, 20), torso=(10, 0), plant='all', fp=FP_DIP)
    anim('handstand_spin', 1.20, [  # party table kick course: dive to a handstand, legs split, two turns, walk-over
        (0.00, P(LEGS0, ra=(0, 0, 0), la=(0, 0, 0), fp=FP_REST)),
        *tweens(0.0, 0.26, 4, dive),
        (0.26, P(hs, yaw=0)),
        (0.96, P(hs, yaw=720)),
        *tweens(0.96, 1.10, 4, over),
        (1.10, P(ra=(60, 0, 20), la=(60, 0, 20), rl=(10, 0, 4), ll=(-10, 0, 4), torso=(15, 0), pitch=360, yaw=720, fp=FP_DIP)),
        (1.20, P(LEGS0, pitch=360, yaw=720, fp=FP_REST)),
    ])


def ranged():
    aim2 = P(ra=(90, -18, 0), la=(84, -40, 0), torso=(0, -12))   # two-handed aim down the sights
    anim('aim', 0.70, [           # aimed shot: raise, fire (the projectile leaves on the first tick), hold, lower
        (0.00, P(fp=FP_REST)),
        (0.08, P(aim2, fp=fps(0.35, -0.3, 0, push=2))),
        (0.13, P(aim2, ra=(106, -18, 0), la=(98, -40, 0), torso=(-6, -12), fwd=-1, fp=fps(0.4, -0.1, 0, pitch=12, push=-2))),
        (0.28, P(aim2, fp=fps(0.35, -0.3, 0, push=2))),
        (0.52, P(aim2, fp=fps(0.35, -0.3, 0, push=2))),
        (0.70, P(fp=FP_REST)),
    ])
    anim('fire_one_hand', 0.35, [
        (0.00, P(ra=(90, -14, 0), torso=(0, -8), fp=fps(0.45, -0.35, 0, push=2))),
        (0.06, P(ra=(116, -14, 0), rap=(0, 0, -1.5), torso=(-5, -10), fp=fps(0.5, -0.1, 0, pitch=15, push=-3))),
        (0.16, P(ra=(96, -14, 0), torso=(0, -8), fp=fps(0.45, -0.3, 0, push=1))),
        (0.35, P(fp=FP_REST)),
    ])
    anim('fire_two_hand', 0.50, [
        (0.00, P(aim2, fp=fps(0.35, -0.3, 0, push=2))),
        (0.07, P(aim2, ra=(104, -18, 0), la=(96, -40, 0), torso=(-8, -12), fwd=-1.5, fp=fps(0.4, -0.05, 0, pitch=15, push=-4))),
        (0.22, P(aim2, fp=fps(0.35, -0.3, 0, push=1))),
        (0.50, P(fp=FP_REST)),
    ])
    aim1 = P(ra=(90, -14, 0), la=(80, -48, 0), torso=(0, -8))   # hip-fire, off hand fanning the hammer
    keys = []
    for i in range(5):            # fan the hammer: a shot every 3 ticks
        t = 0.15 * i
        keys.append((t, P(aim1, la=(80, -55, 0) if i % 2 else (84, -40, 0), fp=fps(0.45, -0.35, 0, push=2))))
        keys.append((t + 0.05, P(aim1, ra=(110, -14, 0), rap=(0, 0, -1.5), torso=(-4, -9), la=(86, -35, 0) if i % 2 else (78, -58, 0),
                                 fp=fps(0.5, -0.12, 0, pitch=14, push=-3))))
    keys.append((0.82, P(fp=FP_REST)))
    anim('rapid_fire', 0.82, keys)
    anim('pistol_whip', 0.48, [   # flintlock melee 3-4/6-8: backhand smash with the gun
        (0.00, P(ra=(40, 0, 8), fp=FP_REST)),
        (0.09, P(ra=aim((0.7, 0.7, 0), (0.3, 0.2, -1)), torso=(2, -28), fp=fps(0.85, 0.35, -30))),
        (0.15, P(ra=aim((-0.15, 0, 1), (-1, -0.3, 0.1)), torso=(12, 28), drop=1, fwd=1.5, fp=fps(0.0, -0.15, 30), hit=True)),
        (0.27, P(ra=aim((-0.55, -0.55, 0.6), (-0.6, -0.5, -0.6)), torso=(12, 38), drop=1, fwd=1.5, fp=fps(-0.6, -0.7, 65))),
        (0.48, P(fp=FP_REST)),
    ])
    anim('rifle_butt', 0.60, [    # rifle melee 4/8: drive the stock forward
        (0.00, P(aim2, fp=FP_REST)),
        (0.10, P(ra=(40, 30, 20), la=(55, 0, 10), torso=(-4, -25), fwd=-1, fp=fps(0.95, -0.6, -20, push=-3))),
        (0.20, P(ra=(100, -20, 0), la=(92, -40, 0), rap=(0, 0.5, 2.5), lap=(0, 0.5, 2.5), torso=(14, 25), fwd=2.5, drop=1,
                 fp=fps(0.25, -0.05, 15, push=8), hit=True)),
        (0.34, P(ra=(96, -20, 0), la=(90, -40, 0), rap=(0, 0, 1.5), lap=(0, 0, 1.5), torso=(12, 20), fwd=2, drop=1, fp=fps(0.3, -0.1, 12, push=6))),
        (0.60, P(fp=FP_REST)),
    ])
    anim('throw', 0.55, [         # overhand throw (javelin, ice saber, water shot, smoke/green star)
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.12, P(ra=(195, 10, 15), la=(90, -10, 0), torso=(-8, -30), rl=(-12, 0, 4), ll=(18, 0, 4), fp=fps(0.85, 0.5, -20, pitch=35))),
        (0.22, P(LUNGE, ra=(80, -15, 0), la=(20, 0, 20), rap=(0, 0, 2), torso=(15, 30), fwd=2, fp=fps(0.25, -0.2, 10, push=6))),
        (0.35, P(LUNGE, ra=(40, -35, 0), la=(15, 0, 20), torso=(15, 35), fwd=2, fp=fps(-0.1, -0.8, 30))),
        (0.55, P(LEGS0, fp=FP_REST)),
    ])


def techniques():
    anim('cast_forward', 0.80, [  # both palms thrust out (ice wall, cyclone, tailwind, mirage tempo)
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.12, P(ra=(-15, 0, 25), la=(-15, 0, 25), torso=(-6, 0), drop=1, rl=(-4, 0, 4), ll=(6, 0, 4), fp=fps(0.95, -0.9, -10, push=-3))),
        (0.22, P(LUNGE, ra=(90, -12, 0), la=(90, -12, 0), rap=(0, 0.5, 2), lap=(0, 0.5, 2), torso=(14, 0), fwd=2, drop=1,
                 fp=fps(0.3, -0.25, 15, push=8))),
        (0.55, P(LUNGE, ra=(88, -12, 0), la=(88, -12, 0), rap=(0, 0.5, 1.5), lap=(0, 0.5, 1.5), torso=(12, 0), fwd=2, drop=1,
                 fp=fps(0.33, -0.28, 15, push=7))),
        (0.80, P(LEGS0, fp=FP_REST)),
    ])
    anim('cast_ground', 0.90, [   # raise both hands, slam the palms into the ground (electro burst, flame ring, vari, ice age)
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.15, P(ra=(150, 0, 15), la=(150, 0, 15), torso=(-10, 0), up=1, rl=(0, 0, 6), ll=(0, 0, 6), fp=fps(0.6, 0.6, 0, pitch=30))),
        (0.30, P(WIDE, ra=(55, -10, 5), la=(55, -10, 5), torso=(48, 0), drop=3, fp=fps(0.3, -0.95, 0, pitch=-40))),
        (0.62, P(WIDE, ra=(52, -10, 5), la=(52, -10, 5), torso=(46, 0), drop=3, fp=fps(0.3, -0.95, 0, pitch=-40))),
        (0.90, P(LEGS0, fp=FP_REST)),
    ])
    anim('cast_up', 1.00, [       # gather low, then reach for the sky (room, el thor, raigo, flame emperor, updraft)
        (0.00, P(LEGS0, head=(0, 0), fp=FP_REST)),
        (0.15, P(ra=(40, -35, 0), la=(40, -35, 0), torso=(8, 0), drop=1.5, rl=(0, 0, 5), ll=(0, 0, 5), head=(10, 0), fp=fps(0.5, -0.85, 20))),
        (0.35, P(ra=(178, -5, 0), la=(0, 0, 115), torso=(-12, 0), head=(-28, 0), up=1, rl=(0, 0, 6), ll=(0, 0, 6),
                 fp=fps(0.55, 0.6, 0, pitch=35))),
        (0.75, P(ra=(176, -5, 0), la=(0, 0, 112), torso=(-11, 0), head=(-26, 0), up=0.5, rl=(0, 0, 6), ll=(0, 0, 6),
                 fp=fps(0.55, 0.55, 0, pitch=32))),
        (1.00, P(LEGS0, head=(0, 0), fp=FP_REST)),
    ])
    anim('finger_twist', 0.70, [  # shambles: raise the hand and twist it sharply
        (0.00, P(fp=FP_REST)),
        (0.10, P(ra=aim((0, 0.35, 1), (0, 1, -0.35)), torso=(4, 10), fp=fps(0.4, -0.1, 0))),
        (0.30, P(ra=aim((0, 0.35, 1), (1, 0, 0)), torso=(4, 12), fp=fps(0.4, -0.1, -70))),
        (0.42, P(ra=aim((0, 0.35, 1), (-1, 0.3, 0)), torso=(4, 8), fp=fps(0.4, -0.1, 60))),
        (0.70, P(fp=FP_REST)),
    ])
    anim('focus', 1.00, [         # haki / life return: palms together, head bowed
        (0.00, P(head=(0, 0), fp=FP_REST)),
        (0.25, P(ra=(60, -42, 0), la=(60, -42, 0), head=(18, 0), drop=0.5, fp=fps(0.15, -0.6, 25))),
        (0.75, P(ra=(60, -42, 0), la=(60, -42, 0), head=(20, 0), drop=0.8, fp=fps(0.15, -0.62, 25))),
        (1.00, P(head=(0, 0), fp=FP_REST)),
    ])
    anim('harden', 0.80, [        # armament: clench, cross, flex down and out
        (0.00, P(LEGS0, head=(0, 0), fp=FP_REST)),
        (0.15, P(ra=(95, -40, 0), la=(95, -40, 0), drop=1, rl=(0, 0, 4), ll=(0, 0, 4), head=(5, 0), fp=fps(0.2, -0.4, 30))),
        (0.35, P(WIDE, ra=(20, 0, 38), la=(20, 0, 38), torso=(8, 0), drop=2, head=(12, 0), fp=fps(0.95, -0.95, -25))),
        (0.60, P(WIDE, ra=(22, 0, 36), la=(22, 0, 36), torso=(8, 0), drop=2.2, head=(12, 0), fp=fps(0.95, -0.95, -25))),
        (0.80, P(LEGS0, head=(0, 0), fp=FP_REST)),
    ])
    anim('iron_body', 0.80, [     # tekkai / iron hair: brace
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.15, P(WIDE, ra=(25, 0, 32), la=(25, 0, 32), torso=(5, 0), drop=2, fp=fps(0.95, -0.95, -20))),
        (0.35, P(WIDE, ra=(27, 0, 30), la=(27, 0, 30), torso=(6, 0), drop=2.3, fp=fps(0.95, -0.95, -20))),
        (0.60, P(WIDE, ra=(25, 0, 32), la=(25, 0, 32), torso=(5, 0), drop=2, fp=fps(0.95, -0.95, -20))),
        (0.80, P(LEGS0, fp=FP_REST)),
    ])
    anim('roar', 1.10, [          # sulong / asura: gather, then chest out, arms wide, shaking
        (0.00, P(LEGS0, head=(0, 0), fp=FP_REST)),
        (0.15, P(ra=(40, -30, 0), la=(40, -30, 0), torso=(20, 0), drop=2, head=(20, 0), rl=(0, 0, 4), ll=(0, 0, 4), fp=fps(0.4, -0.9, 20))),
        (0.35, P(WIDE, ra=(25, 0, 60), la=(25, 0, 60), torso=(-14, 0), head=(-35, 0), fp=fps(1.0, -0.6, -30))),
        (0.50, P(WIDE, ra=(28, 0, 57), la=(22, 0, 62), torso=(-12, 0), head=(-32, 0), drop=0.5, fp=fps(1.0, -0.62, -28))),
        (0.65, P(WIDE, ra=(23, 0, 62), la=(27, 0, 58), torso=(-14, 0), head=(-35, 0), fp=fps(1.0, -0.6, -30))),
        (0.85, P(WIDE, ra=(25, 0, 58), la=(25, 0, 58), torso=(-10, 0), head=(-28, 0), fp=fps(0.95, -0.65, -25))),
        (1.10, P(LEGS0, head=(0, 0), fp=FP_REST)),
    ])
    anim('howl', 1.20, [          # wolf: head thrown back, arms back
        (0.00, P(LEGS0, head=(0, 0))),
        (0.20, P(ra=(-30, 0, 30), la=(-30, 0, 30), torso=(-16, 0), head=(-50, 0), rl=(0, 0, 5), ll=(0, 0, 5))),
        (0.70, P(ra=(-28, 0, 32), la=(-28, 0, 32), torso=(-18, 0), head=(-55, 0), rl=(0, 0, 5), ll=(0, 0, 5))),
        (0.95, P(ra=(-20, 0, 25), la=(-20, 0, 25), torso=(-10, 0), head=(-35, 0), rl=(0, 0, 4), ll=(0, 0, 4))),
        (1.20, P(LEGS0, head=(0, 0))),
    ])
    anim('transform', 1.00, [     # zoan: hunch and curl, then burst open
        (0.00, P(LEGS0, head=(0, 0))),
        (0.20, P(ra=(40, -40, 0), la=(40, -40, 0), torso=(30, 0), drop=3, head=(20, 0), rl=(-10, 0, 6), ll=(12, 0, 6))),
        (0.45, P(ra=(60, 30, 70), la=(60, 30, 70), torso=(-15, 0), head=(-25, 0), up=2, rl=(0, 0, 10), ll=(0, 0, 10))),
        (0.75, P(ra=(55, 25, 65), la=(55, 25, 65), torso=(-12, 0), head=(-20, 0), rl=(0, 0, 9), ll=(0, 0, 9))),
        (1.00, P(LEGS0, head=(0, 0))),
    ])
    anim('transform_revert', 0.80, [  # slump, shake it off
        (0.00, P(head=(0, 0))),
        (0.20, P(ra=(20, 0, 5), la=(20, 0, 5), torso=(20, 0), head=(15, 0), drop=1.5)),
        (0.40, P(ra=(15, 0, 8), la=(15, 0, 8), torso=(12, 0), head=(10, 18), drop=1)),
        (0.55, P(ra=(12, 0, 8), la=(12, 0, 8), torso=(8, 0), head=(8, -18), drop=0.5)),
        (0.80, P(head=(0, 0))),
    ])
    anim('split', 0.80, [         # bara bara: the body comes apart (torso lifts off the hips, arms float off)
        (0.00, P(LEGS0, head=(0, 0))),
        (0.12, P(ra=(30, 0, 85), la=(30, 0, 85), rap=(3, 0, 0), lap=(3, 0, 0), rl=(0, 0, 30), ll=(0, 0, 30), head=(-15, 0),
                 drop=-3, neck=2)),
        (0.50, P(ra=(30, 0, 80), la=(30, 0, 80), rap=(3.5, 0, 0), lap=(3.5, 0, 0), rl=(0, 0, 28), ll=(0, 0, 28), head=(-12, 0),
                 drop=-3.5, neck=2.5)),
        (0.80, P(LEGS0, head=(0, 0))),
    ])
    anim('sway', 0.60, [          # kami-e: paper-like sway left then right
        (0.00, P(LEGS0)),
        (0.12, P(ra=(20, 0, 30), la=(15, 0, 25), torso=(0, 12), right=-4, roll=-16, rl=(0, 0, 3), ll=(0, 0, 8))),
        (0.30, P(ra=(15, 0, 25), la=(20, 0, 30), torso=(0, -12), right=4, roll=16, rl=(0, 0, 8), ll=(0, 0, 3))),
        (0.45, P(ra=(10, 0, 15), la=(10, 0, 15), torso=(0, 3), right=-1, roll=-4, rl=(0, 0, 3), ll=(0, 0, 3))),
        (0.60, P(LEGS0)),
    ])


def movement():
    run = P(ra=(-35, 0, 12), la=(-35, 0, 12), rl=(-35, 0, 3), ll=(40, 0, 3), torso=(35, 0), drop=1.5)
    anim('dash', 0.50, [          # shoulder charge / soru / flame dash / lightning step / ballet dash
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.06, P(run, fwd=4, fp=FP_DIP)),
        (0.30, P(run, fwd=3, torso=(30, 0), fp=FP_DIP)),
        (0.50, P(LEGS0, fp=FP_REST)),
    ])
    anim('dodge_back', 0.45, [    # hop back (the server impulse moves the body; this adds the hop)
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.05, P(ra=(30, 0, 15), la=(30, 0, 15), rl=(10, 0, 3), ll=(10, 0, 3), torso=(12, 0), drop=1.5, fp=FP_DIP)),
        (0.15, P(ra=(70, -10, 20), la=(70, -10, 20), rl=(30, 0, 5), ll=(15, 0, 5), torso=(-5, 0), pitch=-14, fwd=-4, up=5,
                 fp=fps(0.75, -0.55, 0))),
        (0.30, P(ra=(45, -10, 15), la=(45, -10, 15), rl=(-10, 0, 5), ll=(20, 0, 5), torso=(15, 0), drop=2, fwd=-2, fp=FP_DIP)),
        (0.45, P(LEGS0, fp=FP_REST)),
    ])
    tuck = P(ra=(100, -25, 0), la=(100, -25, 0), rl=(95, 0, 3), ll=(105, 0, 3), torso=(40, 0), drop=2)
    anim('dodge_forward', 0.50, [  # forward roll (one smooth turn; tweens keep the body on the ground)
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.05, P(ra=(60, -20, 0), la=(60, -20, 0), rl=(25, 0, 3), ll=(35, 0, 3), torso=(30, 0), drop=2, fp=FP_DIP)),
        *tweens(0.05, 0.38, 16, P(tuck, plant='all', fp=fps(0.8, -1.1, -10))),
        (0.38, P(ra=(60, -20, 0), la=(60, -20, 0), rl=(-15, 0, 3), ll=(35, 0, 3), torso=(25, 0), drop=2, pitch=360, fp=FP_DIP)),
        (0.50, P(LEGS0, pitch=360, fp=FP_REST)),
    ])
    side_tuck = P(ra=(70, -30, 0), la=(70, -30, 0), rl=(70, 0, 0), ll=(80, 0, 0), torso=(25, 0), drop=2)
    anim('dodge_right', 0.45, [   # sideways barrel roll to the right
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.05, P(ra=(45, -15, 0), la=(45, -15, 0), rl=(15, 0, 4), ll=(20, 0, 4), torso=(18, 0), drop=1.5, right=1, fp=FP_DIP)),
        *tweens(0.05, 0.33, 8, P(side_tuck, right=3.5, plant='all', fp=fps(1.0, -0.9, -25))),
        (0.33, P(ra=(45, -15, 0), la=(45, -15, 0), rl=(15, 0, 6), ll=(20, 0, 6), torso=(18, 0), drop=1.5, right=3, roll=360, fp=FP_DIP)),
        (0.45, P(LEGS0, roll=360, fp=FP_REST)),
    ])
    mirror('dodge_right', 'dodge_left')
    anim('dodge_side', 0.40, [    # observation haki sidestep: quick lean out of the line
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.10, P(ra=(25, 0, 25), la=(20, 0, 20), torso=(0, 15), right=-5, roll=-14, rl=(0, 0, 18), ll=(0, 0, 4), fp=fps(0.6, -0.75, 10))),
        (0.25, P(ra=(22, 0, 22), la=(18, 0, 18), torso=(0, 12), right=-4.5, roll=-12, rl=(0, 0, 16), ll=(0, 0, 4), fp=fps(0.62, -0.74, 8))),
        (0.40, P(LEGS0, fp=FP_REST)),
    ])
    anim('air_step', 0.50, [      # geppo / sky walk: kick off the air
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.06, P(ra=(-25, 0, 15), la=(-25, 0, 15), rl=(8, 0, 0), ll=(8, 0, 0), torso=(8, 0), drop=1.5, fp=FP_DIP)),
        (0.16, P(ra=(70, 0, 20), la=(40, 0, 20), rl=(-35, 0, 0), ll=(60, 0, 0), torso=(-6, 0), up=4, fp=fps(0.75, -0.5, 5))),
        (0.32, P(ra=(40, 0, 30), la=(40, 0, 30), rl=(40, 0, 0), ll=(55, 0, 0), torso=(8, 0), up=5, fp=fps(0.75, -0.6, 0))),
        (0.50, P(LEGS0, fp=FP_REST)),
    ])
    anim('vault', 0.90, [         # pole vault: plant the pole, swing over, land
        (0.00, P(POLE_READY, LEGS0, fp=FP_REST)),
        (0.12, pole((0, -0.3, 1), (0, -1, -0.3), torso=(20, 0), drop=2, rl=(-10, 0, 0), ll=(15, 0, 0), fp=fps(0.4, -0.95, 0, pitch=-40))),
        (0.35, pole((0, 1, 0.15), (0, -0.15, 1), torso=(-10, 0), pitch=25, up=9, fwd=3, rl=(60, 0, 5), ll=(75, 0, 5), fp=fps(0.6, 0.5, 0))),
        (0.58, pole((0.1, -0.8, 0.5), (0, 0.5, 0.8), torso=(15, 0), drop=2, rl=(-15, 0, 4), ll=(20, 0, 4), fp=FP_DIP)),
        (0.90, P(LEGS0, fp=FP_REST)),
    ])
    anim('pounce', 0.70, [        # wolf: crouch, leap claws first, land
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.14, P(ra=(20, -10, 10), la=(20, -10, 10), torso=(35, 0), drop=2.5, rl=(-30, 0, 4), ll=(35, 0, 4), fp=FP_DIP)),
        (0.30, P(ra=(150, -5, 15), la=(150, -5, 15), torso=(10, 0), pitch=25, up=7, fwd=6, rl=(-40, 0, 3), ll=(-25, 0, 3),
                 fp=fps(0.5, 0.2, 20, push=6))),
        (0.50, P(ra=(60, -10, 10), la=(60, -10, 10), torso=(30, 0), drop=2.5, fwd=2, rl=(-25, 0, 5), ll=(30, 0, 5), fp=fps(0.4, -0.7, 20))),
        (0.70, P(LEGS0, fp=FP_REST)),
    ])


# Knocked-down players get Pose.SWIMMING forced (player/Knockdown.java), so vanilla PlayerRenderer already lays
# the body face-down (crawl) and stands it back up over ~11 ticks via swimAmount when the pose clears. These two
# clips therefore pose limbs only (relative to the body) and must not add a root rotation (it would double up).
KD = P(ra=(165, -10, 15), la=(15, 0, 30), rl=(0, 0, 9), ll=(2, 0, 5), head=(0, 75), plant=False)  # sprawled, cheek down
SURRENDER = P(rl=(-65, 0, 6), ll=(-65, 0, 6), torso=(4, 0), ra=(15, 0, 158), la=(15, 0, 158), head=(18, 0))  # kneeling, hands up


def reactions_and_states():
    anim('hit_front', 0.35, [
        (0.00, P(head=(0, 0), fp=FP_REST)),
        (0.07, P(ra=(25, 0, 20), la=(25, 0, 20), torso=(-14, 0), head=(-15, 0), fwd=-2, fp=fps(0.85, -0.85, -8, push=-3))),
        (0.18, P(ra=(12, 0, 10), la=(12, 0, 10), torso=(-6, 0), head=(-5, 0), fwd=-1, fp=fps(0.82, -0.78, -4))),
        (0.35, P(head=(0, 0), fp=FP_REST)),
    ])
    anim('hit_left', 0.35, [      # Hits.hitReaction: attacker on the victim's right -> knocked toward its left
        (0.00, P(head=(0, 0), fp=FP_REST)),
        (0.07, P(ra=(30, 0, 30), la=(15, 0, 20), torso=(-6, 22), head=(0, 25), right=-2.5, roll=-8, fp=fps(0.6, -0.8, 12))),
        (0.18, P(ra=(15, 0, 15), la=(8, 0, 10), torso=(-3, 10), head=(0, 10), right=-1.5, roll=-3, fp=fps(0.72, -0.74, 5))),
        (0.35, P(head=(0, 0), fp=FP_REST)),
    ])
    mirror('hit_left', 'hit_right')
    anim('staggered', 0.80, [     # poise broken / parried: reel back, then hunch
        (0.00, P(LEGS0, head=(0, 0), fp=FP_REST)),
        (0.10, P(ra=(60, 20, 45), la=(35, 15, 60), torso=(-16, -10), head=(-15, -10), rl=(-10, 0, 8), ll=(15, 0, 4), fwd=-3, roll=6,
                 fp=fps(0.95, -0.6, -20, push=-3))),
        (0.35, P(ra=(20, -5, 10), la=(25, -5, 10), torso=(18, 5), head=(20, 5), rl=(-6, 0, 6), ll=(10, 0, 6), drop=2, fwd=-2, roll=-4,
                 fp=fps(0.7, -0.95, 10))),
        (0.55, P(ra=(10, 0, 8), la=(12, 0, 8), torso=(10, 0), head=(10, 0), rl=(-3, 0, 4), ll=(5, 0, 4), drop=1, fp=fps(0.75, -0.85, 5))),
        (0.80, P(LEGS0, head=(0, 0), fp=FP_REST)),
    ])
    anim('guard', 1.6, [(0.0, P(GUARD, fp=FP_GUARD)), (0.8, P(GUARD, drop=1.5, torso=(9, 0), fp=FP_GUARD)), (1.6, P(GUARD, fp=FP_GUARD))],
         loop=True)
    anim('guard_end', 0.25, [(0.0, P(GUARD, fp=FP_GUARD)), (0.25, P(fp=FP_REST))])
    anim('guard_break', 0.70, [   # guard crushed: arms flung up, stumble back
        (0.00, P(GUARD, LEGS0, head=(0, 0), fp=FP_GUARD)),
        (0.08, P(ra=(150, 25, -30), la=(150, 25, -30), torso=(-16, 0), head=(-22, 0), fwd=-3, rl=(-12, 0, 6), ll=(10, 0, 6),
                 fp=fps(1.0, 0.5, -35, pitch=25))),
        (0.30, P(ra=(95, 30, 30), la=(95, 30, 30), torso=(-8, 0), head=(-8, 0), fwd=-2, rl=(-10, 0, 6), ll=(8, 0, 6), fp=fps(0.95, 0.0, -20))),
        (0.70, P(LEGS0, head=(0, 0), fp=FP_REST)),
    ])
    anim('parry', 0.40, [         # perfect block: snap the weapon out to deflect
        (0.00, P(GUARD, fp=FP_GUARD)),
        (0.08, P(ra=aim((0.75, 0.55, 0.5), (-0.45, 0.6, 0.65)), la=(60, 10, 20), torso=(-4, -18), fwd=-1, fp=fps(0.95, 0.2, -30))),
        (0.22, P(ra=aim((0.7, 0.45, 0.55), (-0.4, 0.6, 0.65)), la=(55, 10, 20), torso=(-2, -14), fp=fps(0.9, 0.1, -25))),
        (0.40, P(fp=FP_REST)),
    ])
    anim('knocked_down', 3.0, [(0.0, KD), (1.5, P(KD, ra=(162, -10, 17), head=(-4, 72))), (3.0, KD)], loop=True)
    anim('get_up', 1.00, [        # vanilla rotates the body upright (swimAmount) while the limbs push up, kneel, stand
        (0.00, KD),
        (0.20, P(ra=(95, -5, 12), la=(95, -5, 12), rl=(0, 0, 6), ll=(0, 0, 6), head=(-35, 0), plant=False)),
        (0.45, P(ra=(40, 0, 15), la=(50, -10, 10), rl=(-45, 0, 4), ll=(50, 0, 6), torso=(25, 0), head=(-10, 0), drop=2)),
        (0.70, P(ra=(10, 0, 10), la=(10, 0, 10), rl=(-15, 0, 3), ll=(15, 0, 3), torso=(10, 0), head=(0, 0), drop=1)),
        (1.00, P(LEGS0, head=(0, 0))),
    ])
    anim('surrender', 2.0, [(0.0, SURRENDER), (1.0, P(SURRENDER, ra=(15, 0, 154), la=(15, 0, 154), head=(22, 0), drop=0.4)),
                            (2.0, SURRENDER)], loop=True)
    stance = P(FISTS, STANCE_LEGS)
    anim('stance', 1.6, [(0.0, stance), (0.8, P(stance, drop=1.4, torso=(8, -12))), (1.6, stance)], loop=True)
    anim('stance_enter', 0.60, [  # combat mode on: fists up, knuckles together, settle
        (0.00, P(LEGS0, fp=FP_REST)),
        (0.14, P(stance, drop=1.5, fp=fps(0.55, -0.45, 15))),
        (0.26, P(STANCE_LEGS, ra=(88, -48, 0), la=(88, -48, 0), torso=(6, 0), drop=1, fp=fps(0.2, -0.4, 30))),
        (0.42, P(stance, fp=fps(0.55, -0.45, 15))),
        (0.60, P(LEGS0, fp=FP_REST)),
    ])
    anim('stance_exit', 0.40, [(0.0, P(stance, fp=fps(0.55, -0.45, 15))), (0.40, P(LEGS0, fp=FP_REST))])


def emotes():
    anim('meditate', 3.0, [       # cross-legged on the ground
        (0.0, P(rl=(90, 32, 0), ll=(90, 32, 0), ra=(40, 22, 10), la=(40, 22, 10), head=(12, 0))),
        (1.5, P(rl=(90, 32, 0), ll=(90, 32, 0), ra=(41, 22, 10), la=(41, 22, 10), head=(14, 0), torso=(2, 0), drop=0.4)),
        (3.0, P(rl=(90, 32, 0), ll=(90, 32, 0), ra=(40, 22, 10), la=(40, 22, 10), head=(12, 0))),
    ], loop=True)
    sit = P(rl=(85, 8, 0), ll=(85, 8, 0), ra=(-35, 0, 15), la=(-35, 0, 15), torso=(-12, 0))
    anim('sit_ground', 4.0, [(0.0, sit), (2.0, P(sit, torso=(-10, 0))), (4.0, sit)], loop=True)
    anim('wave', 1.40, [
        (0.00, P()),
        (0.20, P(ra=(0, 0, 150), torso=(0, -6))),
        (0.40, P(ra=(0, 0, 125), torso=(0, -6))),
        (0.60, P(ra=(0, 0, 150), torso=(0, -6))),
        (0.80, P(ra=(0, 0, 125), torso=(0, -6))),
        (1.00, P(ra=(0, 0, 150), torso=(0, -6))),
        (1.40, P()),
    ])
    anim('cheer', 1.20, [
        (0.00, P(LEGS0)),
        (0.15, P(ra=(10, 0, 150), la=(10, 0, 150), rl=(0, 0, 3), ll=(0, 0, 3), drop=1)),
        (0.30, P(ra=(10, 0, 160), la=(10, 0, 160), rl=(0, 0, 3), ll=(0, 0, 3), up=3)),
        (0.48, P(ra=(10, 0, 150), la=(10, 0, 150), rl=(0, 0, 3), ll=(0, 0, 3), drop=1)),
        (0.66, P(ra=(10, 0, 160), la=(10, 0, 160), rl=(0, 0, 3), ll=(0, 0, 3), up=3)),
        (0.85, P(ra=(10, 0, 150), la=(10, 0, 150), rl=(0, 0, 3), ll=(0, 0, 3))),
        (1.20, P(LEGS0)),
    ])
    anim('point', 1.20, [(0.0, P()), (0.2, P(ra=(92, -6, 0), torso=(0, 4))), (0.9, P(ra=(91, -6, 0), torso=(0, 4))), (1.2, P())])
    anim('bow_greet', 1.40, [
        (0.00, P(head=(0, 0))),
        (0.40, P(torso=(38, 0), head=(18, 0), ra=(0, 0, 0), la=(0, 0, 0))),
        (0.90, P(torso=(38, 0), head=(18, 0))),
        (1.40, P(head=(0, 0))),
    ])


def library():
    LIB.clear()
    swords()
    polearms_and_heavy()
    unarmed()
    kicks()
    ranged()
    techniques()
    movement()
    reactions_and_states()
    emotes()


# =====================================================================================================
# validation
# =====================================================================================================
def java_refs():
    """Animation names referenced by the Java sources and melee strike timings (windup, recovery ticks)."""
    names, strikes = set(), {}
    if not os.path.isdir(JAVA):
        return names, strikes
    for root, _, files in os.walk(JAVA):
        for f in files:
            if not f.endswith('.java'):
                continue
            src = open(os.path.join(root, f), encoding='utf-8').read()
            names |= set(re.findall(r'\.anim\("([a-z_]+)"\)', src))
            for line in src.splitlines():  # GLNetwork.anim(e, cond ? "a" : "b"), String anim = ..., hitReaction returns
                if 'GLNetwork.anim(' in line or re.search(r'\bString anim\s*=', line) or re.search(r'return .*"hit_', line):
                    names |= set(re.findall(r'"([a-z_]+)"', re.sub(r'\.equals\("[^"]*"\)', '', line)))
            for m in re.finditer(r'Strike\.of\(([^()]*)\)', src):
                a = [x.strip() for x in m.group(1).split(',')]
                if len(a) == 7:
                    strikes.setdefault(a[6].strip('"'), []).append((int(a[4]), int(a[5])))
    return names, strikes


def validate(data, verbose=True):
    errors = []
    anims = data.get('animations', {})
    if data.get('format_version') != '1.8.0':
        errors.append('format_version must be 1.8.0')
    names = {}
    for key, a in anims.items():
        if not key.startswith(KEY_PREFIX):
            errors.append(f'{key}: key must start with {KEY_PREFIX}')
        names[key.rsplit('.', 1)[-1]] = a
    for n in REQUIRED + EXTRA:
        if n not in names:
            errors.append(f'missing animation: {n}')
    jnames, strikes = java_refs()
    for n in sorted(jnames - set(names)):
        errors.append(f'referenced from Java but missing: {n}')
    for n, a in sorted(names.items()):
        length = a.get('animation_length')
        if not isinstance(length, (int, float)) or not (0 < length <= 5):
            errors.append(f'{n}: bad animation_length {length}')
            continue
        if not isinstance(a.get('loop'), bool):
            errors.append(f'{n}: loop must be a boolean')
        if bool(a.get('loop')) != (n in LOOPING):
            errors.append(f'{n}: loop should be {n in LOOPING}')
        for bone, tr in a.get('bones', {}).items():
            if bone not in BONES:
                errors.append(f'{n}: unknown bone {bone}')
            for cname, val in tr.items():
                if cname not in ('rotation', 'position'):
                    errors.append(f'{n}.{bone}: unknown channel {cname}')
                    continue
                keys = [('0.0', val)] if isinstance(val, list) else list(val.items())
                times = []
                for ts, v in keys:
                    try:
                        t = float(ts)
                    except ValueError:
                        errors.append(f'{n}.{bone}.{cname}: bad time {ts!r}')
                        continue
                    times.append(t)
                    if not (len(v) == 3 and all(isinstance(x, (int, float)) and math.isfinite(x) for x in v)):
                        errors.append(f'{n}.{bone}.{cname}@{ts}: bad value {v}')
                        continue
                    lim = 1080 if cname == 'rotation' else 64
                    if any(abs(x) > lim for x in v):
                        errors.append(f'{n}.{bone}.{cname}@{ts}: out of range {v}')
                    # channels vanilla setupAnim never resets (they would accumulate / stick)
                    if bone in ('head', 'body') and cname == 'rotation' and abs(v[2]) > 1e-9:
                        errors.append(f'{n}.{bone}: z rotation is not reset by vanilla')
                    if bone in ('head', 'body') and cname == 'position' and (abs(v[0]) > 1e-9 or abs(v[2]) > 1e-9):
                        errors.append(f'{n}.{bone}: x/z position is not reset by vanilla')
                    if bone in ('right_leg', 'left_leg') and cname == 'position' and abs(v[0]) > 1e-9:
                        errors.append(f'{n}.{bone}: x position is not reset by vanilla')
                if times and abs(times[0]) > 1e-9:
                    errors.append(f'{n}.{bone}.{cname}: first key must be at 0')
                if any(b <= a2 for a2, b in zip(times, times[1:])):
                    errors.append(f'{n}.{bone}.{cname}: times not strictly ascending')
                if times and (times[-1] > length + 1e-6 or times[0] < 0):
                    errors.append(f'{n}.{bone}.{cname}: key outside 0..{length}')
                if a.get('loop') and isinstance(val, dict):
                    if abs(times[-1] - length) > 1e-6 or keys[0][1] != keys[-1][1]:
                        errors.append(f'{n}.{bone}.{cname}: loop must end at the length with the first key value')
                if bone == 'root' and cname == 'rotation' and n in ('knocked_down', 'get_up'):
                    errors.append(f'{n}: no root rotation (the forced crawl pose already lays the player down)')
                if bone == 'root' and cname == 'rotation' and not a.get('loop'):
                    last = keys[-1][1]
                    if any(abs(x) > 1e-6 for x in last):
                        errors.append(f'{n}: root rotation must end at 0 (blend-out scales it)')
    # melee timing vs server strikes
    report = []
    for n, lst in sorted(strikes.items()):
        if n not in names or n not in LIB:
            continue
        hit = impact_time(n)
        length = names[n]['animation_length']
        wind = sorted(w for w, _ in lst)
        tot = sorted(w + r for w, r in lst)
        report.append(f'  {n:20s} impact {hit if hit is not None else "-":>5}s (server {wind[0] / 20:.2f}-{wind[-1] / 20:.2f}s)'
                      f'  length {length:.2f}s (server {tot[0] / 20:.2f}-{tot[-1] / 20:.2f}s)')
        # one clip serves several weapons/styles: the impact pose must fall inside the range of their windups
        if hit is None:
            errors.append(f'{n}: melee clip needs a hit=True keyframe')
        elif not (wind[0] / 20.0 - 0.061 <= hit <= wind[-1] / 20.0 + 0.061):
            errors.append(f'{n}: impact {hit}s outside the server windups {wind[0] / 20.0}-{wind[-1] / 20.0}s')
        if length > tot[-1] / 20.0 + 0.2:
            errors.append(f'{n}: length {length}s exceeds the longest server strike {tot[-1] / 20.0}s')
    if verbose and report:
        print('melee timing (clip vs server Strike windup / windup+recovery):')
        print('\n'.join(report))
    return errors


# =====================================================================================================
# preview (stick-figure contact sheets)
# =====================================================================================================
def _sample(val, t):
    if isinstance(val, list):
        return tuple(val)
    ks = sorted((float(k), v) for k, v in val.items())
    if t <= ks[0][0]:
        return tuple(ks[0][1])
    for (ta, va), (tb, vb) in zip(ks, ks[1:]):
        if t <= tb:
            k = (t - ta) / max(1e-4, tb - ta)
            k = k * k * (3 - 2 * k)
            return tuple(va[i] + (vb[i] - va[i]) * k for i in range(3))
    return tuple(ks[-1][1])


def sample_clip(a, t):
    ch = {}
    for bone, tr in a['bones'].items():
        ch[bone] = (_sample(tr['rotation'], t) if 'rotation' in tr else None,
                    _sample(tr['position'], t) if 'position' in tr else None)
    return ch


class Canvas:
    FONT = {'0': '75557', '1': '26222', '2': '71747', '3': '71717', '4': '55711', '5': '74717', '6': '74757',
            '7': '71111', '8': '75757', '9': '75717', '.': '00002', '-': '00700', 's': '03632'}

    def __init__(self, w, h, bg=(24, 24, 30)):
        self.w, self.h = w, h
        self.px = bytearray(bytes(bg) * (w * h))

    def dot(self, x, y, c):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < self.w and 0 <= y < self.h:
            i = (y * self.w + x) * 3
            self.px[i:i + 3] = bytes(c)

    def line(self, a, b, c, thick=1):
        n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))) + 1
        for i in range(n + 1):
            x = a[0] + (b[0] - a[0]) * i / n
            y = a[1] + (b[1] - a[1]) * i / n
            self.dot(x, y, c)
            if thick > 1:
                self.dot(x + 1, y, c)
                self.dot(x, y + 1, c)

    def text(self, x, y, s, c=(200, 200, 200)):
        for ch in s:
            g = self.FONT.get(ch)
            if g:
                for row, bits in enumerate(g):
                    v = int(bits, 8) if bits.isdigit() else 0
                    for col in range(3):
                        if v & (4 >> col):
                            self.dot(x + col, y + row, c)
            x += 4

    def save(self, path):
        raw = b''.join(b'\x00' + bytes(self.px[y * self.w * 3:(y + 1) * self.w * 3]) for y in range(self.h))

        def chunk(tag, data):
            return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
        with open(path, 'wb') as f:
            f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 2, 0, 0, 0))
                    + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


COLORS = {'head': (235, 205, 70), 'body': (175, 175, 175), 'right_arm': (235, 75, 75), 'left_arm': (85, 125, 245),
          'right_leg': (245, 150, 55), 'left_leg': (60, 205, 220)}
EDGES = [(a, b) for a in range(8) for b in range(a + 1, 8) if bin(a ^ b).count('1') == 1]


def _geometry(a, t):
    ch = sample_clip(a, t)
    pts = world_points(ch)
    rt = root_transform(*ch.get('root', (None, None)))
    for arm in ('right_arm', 'left_arm'):
        f = part_transform(arm, *ch.get(arm, (None, None)))
        g = GRIP[arm]
        tip = add(g, tuple(BLADE_LEN * c for c in BLADE_DIR))
        pts['blade_' + arm] = (rt(to_entity(f(g))), rt(to_entity(f(tip))))
    rot, pos = ch.get('fp_right', (None, None))
    rot, pos = rot or (0, 0, 0), pos or (0, 0, 0)
    m = mmul(rot_x(rot[0] * D2R), mmul(rot_y(rot[1] * D2R), rot_z(rot[2] * D2R)))
    bd = (0.0, 0.94, 0.34)
    fp = [add(mvec(m, v), tuple(c / 16.0 for c in pos)) for v in
          (sub(FP_ITEM, tuple(0.25 * c for c in bd)), add(FP_ITEM, tuple(0.35 * c for c in bd)))]
    pts['fp'] = fp
    return pts


def render_clip(name, a, path):
    times = sorted({float(k) for tr in a['bones'].values() for v in tr.values() if isinstance(v, dict) for k in v})
    times = [t for i, t in enumerate(times) if i == 0 or t - times[i - 1] > 0.002] or [0.0]
    if len(times) > 12:
        step = len(times) / 12.0
        times = [times[int(i * step)] for i in range(12)]
    cw, rows = 120, (150, 150, 110, 80)
    cv = Canvas(cw * (len(times) + 1), sum(rows) + 12)
    cv.text(2, 2, '%.2f' % a['animation_length'] + 's', (255, 255, 255))
    sc = 2.3
    dual = name.startswith(('dual', 'triple'))
    views = (
        (lambda p: (p[0], p[1]), 12, 125),    # front: viewer faces the figure (its left on screen right)
        (lambda p: (p[2], p[1]), 162, 125),   # side: seen from its right, forward = screen right
        (lambda p: (-p[0], p[2]), 302, 55),   # top: forward = screen up
    )
    fpy = sum(rows[:3]) + 12

    def S(p, vi, x0):
        proj, oy, gy = views[vi]
        u, v = proj(p)
        return (x0 + cw / 2 + u * sc, oy + gy - v * sc)

    def F(v, x0):
        z = max(0.05, -v[2])
        return (x0 + cw / 2 + v[0] / z / FP_HALF[0] * (cw / 2 - 4), fpy + rows[3] / 2 - 2 - v[1] / z / FP_HALF[1] * (rows[3] / 2 - 4))

    def frame(x0):
        for vi in range(2):
            gy = views[vi][1] + views[vi][2]
            cv.line((x0 + 4, gy), (x0 + cw - 4, gy), (80, 80, 80))
        cv.line((x0 + 2, fpy), (x0 + cw - 2, fpy), (60, 60, 60))
        cv.line((x0 + 2, fpy + rows[3] - 4), (x0 + cw - 2, fpy + rows[3] - 4), (60, 60, 60))

    for ci, t in enumerate(times):
        x0 = ci * cw
        cv.text(x0 + 40, 2, '%.3f' % t)
        frame(x0)
        g = _geometry(a, t)
        for vi in range(3):
            order = PARTS if vi != 1 else ('left_leg', 'left_arm', 'body', 'head', 'right_leg', 'right_arm')
            for b in order:
                ps = [S(p, vi, x0) for p in g[b]]
                for e0, e1 in EDGES:
                    cv.line(ps[e0], ps[e1], COLORS[b])
            for arm in (('right_arm', 'left_arm') if dual else ('right_arm',)):
                g0, tip = g['blade_' + arm]
                cv.line(S(g0, vi, x0), S(tip, vi, x0), (250, 250, 250), 2)
        cv.line(F(g['fp'][0], x0), F(g['fp'][1], x0), (250, 250, 250), 2)
    # trail column: hand (yellow) and blade tip (white -> red over time) for the whole clip
    x0 = len(times) * cw
    cv.text(x0 + 40, 2, 'trail')
    frame(x0)
    n = 48
    last = None
    for i in range(n + 1):
        t = a['animation_length'] * i / n
        g = _geometry(a, t)
        k = i / n
        col = (250, int(250 * (1 - k)), int(250 * (1 - k)))
        if last is not None:
            for vi in range(3):
                cv.line(S(last['blade_right_arm'][1], vi, x0), S(g['blade_right_arm'][1], vi, x0), col)
                cv.line(S(last['blade_right_arm'][0], vi, x0), S(g['blade_right_arm'][0], vi, x0), (235, 205, 70))
                if dual:
                    cv.line(S(last['blade_left_arm'][1], vi, x0), S(g['blade_left_arm'][1], vi, x0), (90, 140, 255))
            cv.line(F(last['fp'][1], x0), F(g['fp'][1], x0), col)
            cv.line(F(last['fp'][0], x0), F(g['fp'][0], x0), (235, 205, 70))
        last = g
    cv.save(path)


# =====================================================================================================
# main
# =====================================================================================================
def generate():
    library()
    data = {'format_version': '1.8.0', 'animations': {}}
    for name in sorted(LIB):
        data['animations'][KEY_PREFIX + name] = build(name, LIB[name])
    return data


def main(argv):
    if '--check' not in argv:
        data = generate()
        text = json.dumps(data, indent=1)
        # keep every [x, y, z] on one line: one keyframe per line
        text = re.sub(r'\[\s+([^\[\]]+?)\s+\]', lambda m: '[' + ', '.join(v.strip() for v in m.group(1).split(',')) + ']', text)
        os.makedirs(os.path.dirname(OUT), exist_ok=True)
        with open(OUT, 'w', encoding='utf-8') as f:
            f.write(text + '\n')
        print(f'wrote {len(data["animations"])} animations -> {os.path.relpath(OUT, MOD)}')
    else:
        library()
    data = json.load(open(OUT, encoding='utf-8'))
    errors = validate(data)
    if '--preview' in argv:
        i = argv.index('--preview')
        outdir = argv[i + 1]
        only = set(argv[i + 2:])
        os.makedirs(outdir, exist_ok=True)
        for key, a in data['animations'].items():
            n = key.rsplit('.', 1)[-1]
            if not only or n in only:
                render_clip(n, a, os.path.join(outdir, n + '.png'))
        print(f'previews -> {outdir}')
    if errors:
        print('\n'.join('ERROR: ' + e for e in errors))
        return 1
    print('validation OK')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
