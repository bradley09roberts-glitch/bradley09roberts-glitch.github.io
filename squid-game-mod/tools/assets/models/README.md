# Contestant & guard models (GeckoLib 4.9.3) - generator toolchain

Everything in `src/main/resources/assets/squidgame/{geo,animations,textures}/entity/{contestant,guard}*` is
**generated** by the Python scripts in this directory (Python 3.11 + Pillow + numpy, no GPU, deterministic - there
is no RNG state, all noise is an integer hash).

```
python3 tools/assets/models/gen_all.py              # regenerate geo + animations + 128x128 textures, then validate
python3 tools/assets/models/gen_all.py --preview    # + contact sheets for every animation in preview/ (git-ignored)
python3 tools/assets/models/gen_contestant.py       # only the contestant
python3 tools/assets/models/gen_guard.py            # only the guard
python3 tools/assets/models/validate_models.py      # contract validator (exit code 0 = OK)
python3 tools/assets/models/qa_anims.py contestant  # floor penetration / limb-in-torso QA over all animations
python3 tools/assets/models/selftest.py             # numeric proof of the sign conventions below
python3 tools/assets/models/preview.py contestant --static --anim walk run   # PNG sheets -> preview/contestant/
```

| file | role |
|------|------|
| `lib/geom.py` | Bedrock `format_version 1.12.0` geometry builder (box-UV + per-face-UV cubes, shelf UV packer) |
| `lib/anim.py` | animation JSON builder: eased keyframes, pose DSL (`HEAD/BODY/ARM/LEG`), loop closing |
| `lib/paint.py` | pixel-art helpers (canvas, faces of a box-UV cube, noise ramps, ASCII stamps) |
| `lib/rig.py` | **faithful port of GeckoLib's bake / keyframe / easing / transform code** (used by preview, IK, QA, validator) |
| `lib/render.py` | software rasteriser (ortho, z-buffer, cutout alpha, Minecraft diffuse light, per-bone tint/hide) |
| `lib/posing.py`, `lib/humanoid.py`, `lib/gait.py` | FK/IK on the *same* rig code, foot-trajectory gait generator |
| `contestant_model.py` / `contestant_tex.py` / `contestant_anim.py` + `ca_*.py` | contestant geometry, texture, 62 animations |
| `guard_model.py` / `guard_tex.py` / `guard_anim.py` | guard geometry (+ rifle prop), texture, 18 animations |
| `contestant_bones.json` | bone roles (skin / hair / face / accessory / anchor ...) for the Java constants test |
| `gait_info.json` | planted ground speed of every locomotion cycle (see "Foot planting") |
| `preview.py`, `qa_anims.py`, `selftest.py`, `validate_models.py` | look-dev, QA and contract checks |

## Conventions derived from the GeckoLib 4.9.3 sources

*(read in `loading/object/BakedModelFactory`, `loading/json/typeadapter/BakedAnimationsAdapter`,
`animation/AnimationController`, `animation/EasingType`, `renderer/GeoEntityRenderer`, `util/RenderUtil`,
`cache/object/GeoBone`; ported 1:1 in `lib/rig.py`)*

**Geometry file space.** px units, y up, feet at the origin, model front = **-Z**. File **+X is the entity's LEFT**
(Bedrock convention: the right arm sits at x < 0). GeckoLib bakes `x -> -x` (pivot `(-x,y,z)`, cube origin
`-(ox+sx)`), bone/cube rotation `(-rx,-ry,+rz)` degrees -> radians; baked space is therefore
`+x = entity right, +y = up, +z = back`. Per-bone matrix in the renderer:
`T(-posX,posY,posZ) * T(pivot) * Rz*Ry*Rx * S * T(-pivot)` (X applied first, extrinsic), per cube
`T(cpivot) * Rz*Ry*Rx * T(-cpivot)`. Box-UV sizes are **floored** by GeckoLib, so every box-UV cube has integral
`size` and sub-pixel adjustments use `inflate`.

**Box-UV orientation** (vanilla skin layout; verified by `selftest`/preview): the six regions of a cube at `(u,v)`:
`east` = entity's right side `(u, v+sz)`, `north` = front `(u+sz, v+sz)`, `west` = left side `(u+sz+sx, v+sz)`,
`south` = back `(u+2sz+sx, v+sz)`, `up` `(u+sz, v)`, `down` `(u+sz+sx, v)`.
Viewed from outside, `north` has the entity's **right** at its left edge; `south` has the entity's left at its left
edge; `east` runs back -> front, `west` front -> back; `up`/`down` have the **back at the top edge** and the entity's
right at the left edge. Face decals are zero-thickness cubes with a single per-face `north` UV.

**Animation file semantics** (what the JSON numbers mean):

| channel | +value does |
|---------|-------------|
| `rotation x` | head/torso/limb bends **forward** (nod down); hanging limbs swing **backward** |
| `rotation y` | turn to the entity's **right** |
| `rotation z` | top rolls toward the entity's **left** (right arm: splays out, left arm: swings in) |
| `position x` | toward the entity's **left** |
| `position y` / `z` | up / **backward** |

The easing named on a keyframe applies to the segment that **arrives** at that keyframe (GeckoLib `Keyframe`
start=previous, end=this). Rotations of different keyframes are interpolated per Euler axis. Keyframes are written
in increasing time order, only one key per timestamp, loops carry a key at `animation_length` equal to the key at 0
(GeckoLib restarts at `adjustedTick >= length`, so the last pose is never shown), holds use
`"loop": "hold_on_last_frame"`, one-shots `"loop": false`.

`lib/humanoid.py` hides the sign soup behind semantic builders (`HEAD(nod,turn,tilt)`, `BODY/WAIST(lean,twist,tilt)`,
`ARM(side, swing, out, elbow)`, `LEG(side, hip, knee, ankle)` - "swing/hip" = forward +, "out" = away from the body +,
"elbow/knee" = flexion +); `selftest.py` asserts each of them numerically against the ported GeckoLib transform
(e.g. `ARM('right', swing=60)` moves the palm forward and up, `HEAD(turn=40)` turns toward the entity's right).

## Rigs

Contestant (`geometry.squidgame.contestant`, 128x128):

```
root > waist > body > head > head_skin | hair_buzz hair_short hair_parted hair_curly hair_long hair_ponytail hair_bun
                                | face_0..face_5 | glasses
                   > neck_skin   > number_chest (0,18.5,-2.05)   > number_back (0,18.5,+2.05)     (empty anchors)
                   > {left,right}_arm > {left,right}_forearm > {left,right}_hand_skin > item_{left,right}
              waist > {left,right}_leg > {left,right}_shin > {left,right}_shoe
```

Guard (`geometry.squidgame.guard`, 128x128):

```
root > waist > body > head > hood | mask_circle mask_triangle mask_square        > collar_black   > rifle (pivot = pistol grip)
                   > {left,right}_arm > {left,right}_forearm > {left,right}_hand > item_{left,right}
              waist > {left,right}_leg > {left,right}_shin > {left,right}_boot
```

*Additions to the contract* (it allows extra bones): `*_forearm` (elbow) and `*_shin` (knee). They make bent-arm poses
(hands on hips / to the face / on the tin / rifle grips) and the seated dalgona pose (thighs horizontal, shins down)
possible. As a consequence `*_hand_skin`/`*_hand`, `item_*` are descendants of the arm (not direct children) and
`*_shoe`/`*_boot` descendants of the leg. Everything the Java side addresses by name keeps its name. The elbow/knee
cubes are inset by 0.1 px and extended 2 px over the joint, so a bent joint has no gap and a straight one no z-fighting.

Proportions are vanilla-player (32 px: legs 12, torso 12, head 8; arms/legs 4x12x4, body 8x12x4); the renderer scale
0.9375 gives the 1.8 block hitbox.

### Tint / visibility notes for the Java renderer (contestant)

* **Tint** (greyscale, near-white skin / mid-grey hair): `head_skin`, `neck_skin`, `left_hand_skin`, `right_hand_skin`
  by skin tone; every `hair_*` bone by hair colour. Show exactly one `hair_*` (none = bald), exactly one `face_*`.
* `face_*` and `glasses` are flat decal quads (z = -4.06 / -4.14, in front of the head front face) with opaque ink on
  transparent texels - **do not tint** them. Hair shells are inflated 0.25-1.0 px so fringes cover the decal rows 0-2
  only; the brows/eyes start at row 3.
* `number_chest` / `number_back` pivots sit exactly on the jacket surface (z = -2.05 / +2.05), centred, at y = 18.5; the
  jacket texture is plain green behind them (rows y = 16..21 of the front and back) so the Java bib reads cleanly.
* Guard: show exactly one mask bone (circle = workers, triangle = soldiers, square = managers); `collar_black` only
  for managers; hide `rifle` for unarmed ranks. The animations always move the rifle together with the hands.

## Animations

* Contestant: 62 (`animation.contestant.<name>`), guard: 18 (`animation.guard.<name>`) - every name, loop type and
  length of docs/ASSET_CONTRACT.md section 1.1/1.2 exists (checked by `validate_models.py`).
* Overlay clips (`wave point nod shake_head think`) only touch the right arm chain and/or the head.
* One-shots start from the `idle` pose (relaxed arms: swing 2, out 5, elbow 9; all other bones neutral) **and end at it**
  (empty-pose keys in the authoring scripts mean "back to idle here", resolved by `Animation.settle`; the validator warns
  about any plain `once` clip that ends elsewhere). Documented exceptions: `pull_heave/pull_slip` start and end at
  `pull_idle`, `dalgona_lick/crack/success` at `dalgona_sit`, `marble_throw_release` starts at the end of
  `marble_throw_windup`, `marble_reveal` ends on the open hand, guard `fire` starts and ends at `aim`, `lower` starts at
  `aim` (`*_anim_meta.json` -> `refs`; the validator uses them). `hold_on_last_frame` clips keep their final pose.
* Hand contacts (hands on hips, on the tin, on the rope, on the rifle) are solved with IK against the ported GeckoLib
  FK, feet of every standing pose are planted with IK, lying poses are ground-snapped, and a final ground-lock pass
  (`lib/postproc.ground_lock`) lifts the root wherever any pose would sink more than 0.1 px into the floor: the root
  position track is then re-written densely (0.02 s, linear) from the exact GeckoLib-sampled authored root motion plus the
  lift (`qa_anims.py` reports what is left; floating during run/jump flight phases is intended).
* One-shots have a 0.03-0.04 s follow-through lag on head / forearms / hands.

### Foot planting (`gait_info.json`)

With 12 px legs a stance foot sweeps only ~12-15 px, so the cycles are built to plant the feet at the contract's
reference speeds when played at **1.0x**:

| clip | loop | content | feet planted at | contract reference |
|------|------|---------|-----------------|--------------------|
| `walk` (contestant, guard) | 1.0 s | **two** gait cycles (0.5 s each, 4 steps/s, left/right half a cycle apart) | 2.46 / 2.44 blocks/s | 2.4 |
| `run` (contestant, guard) | 0.6 s | one cycle, short ground contact (24%), flight phases | 5.78 / 5.77 blocks/s | 6.0 |
| `sneak_walk` | 1.2 s | one cycle | 0.62 blocks/s | - |

For other ground speeds play the clip at `animationSpeed = actualSpeed / planted_blocks_s` (clamp to ~0.5-1.5) so the
feet keep sticking to the floor; `gait_info.json` holds the exact numbers (`speed_multiplier_at_reference` ~ 1).

## Preview / QA tools

* `preview.py` renders the **generated files** (not the in-memory data): orthographic views (front, side, 3/4) of
  8 poses per animation + a side film strip, texture sheet, hair-style and face sheets, all through
  `lib/rig.py`/`lib/render.py` (same bake + transform + easing code as GeckoLib, nearest texturing, cutout alpha,
  no culling, Minecraft light). Props (bench, table, rope, glass panel) are drawn for the matching clips.
* `qa_anims.py`: lowest point over time (floor penetration / floating) and hand/elbow inside torso/head tests.
* `validate_models.py`: see the docstring - bones / animations / loop types / lengths from the contract, UV bounds and
  overlap, greyscale tint regions, clean decal alpha, easing names, finite numbers, loop closure, one-shot start and end
  poses, overlay bone sets, no scale channels, `contestant_bones.json` consistency.

## What could not be verified without an in-game render

* The preview is a port of the GeckoLib code, not GeckoLib itself: a global sign/axis error in the port would be
  invisible to it (the conventions were derived twice - from the Java and numerically in `selftest.py` - and agree).
* Exact in-game lighting / colour response of the greyscale tinted regions (very dark hair colours lose strand detail).
* Z-fighting of coplanar decals at long distances (decals sit 0.06 px off the head; glasses 0.14 px).
* Whether Java overrides head/arm rotations after animation (it would flatten head-driven acting).
