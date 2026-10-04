"""Post-processing passes applied to finished animations (operate on the in-memory Animation objects)."""
from __future__ import annotations

from typing import Iterable, Optional, Sequence

import numpy as np

from .anim import Animation, B, Pose, LOOP
from .rig import Clip, Rig


def key_times(a: Animation) -> list:
    ts = set()
    for bt in a.bones.values():
        for tr in (bt.rot, bt.pos):
            for (t, _, _) in tr.keys:
                ts.add(round(t, 5))
    return sorted(ts)


def lowest_y(rig: Rig, state: dict, hidden: Sequence[str]) -> float:
    m = rig.world_matrices(state)
    y = 1e9
    for bone, cube, verts in rig.vertices_world(m, hidden=hidden):
        y = min(y, float(verts[..., 1].min()))
    return y


def ground_lock(rig: Rig, a: Animation, hidden: Sequence[str], threshold: float = 0.3, step: float = 0.04) -> float:
    """Lift the root wherever the lowest model point would sink more than ``threshold`` px into the floor.

    The animation is sampled densely (every ``step`` s) so the lift also covers motion between keys.
    Returns the largest lift applied."""
    clip = Clip(a.name, a.to_json())
    base_times = key_times(a)
    times = set(base_times)
    for t0, t1 in zip(base_times, base_times[1:]):
        n = int((t1 - t0) / step)
        for k in range(1, n + 1):
            times.add(round(t0 + (t1 - t0) * k / (n + 1), 5))
    times = sorted(times)
    lifts = []
    for t in times:
        low = lowest_y(rig, clip.sample(t, loop=False), hidden)
        lifts.append(-low if low < -threshold else 0.0)
    if not any(l > 0 for l in lifts):
        return 0.0
    bt = a._bt("root")
    sel = set()
    for i, l in enumerate(lifts):
        if l > 0:
            sel.update({max(i - 1, 0), i, min(i + 1, len(times) - 1)})
    plan = []
    for i in sorted(sel):
        t = times[i]
        cur = bt.pos.value_at(t) if bt.pos.keys else (0.0, 0.0, 0.0)        # ORIGINAL root position at t
        ease = "linear"
        for (tt, _, e) in bt.pos.keys:
            if abs(tt - t) < 1e-6:
                ease = e
        plan.append((t, cur, ease, lifts[i]))
    for t, cur, ease, lift in plan:
        bt.pos.add(t, (cur[0], cur[1] + lift, cur[2]), ease)
    return max(lifts)


def lag_tracks(a: Animation, lags: dict, min_gap_frac: float = 0.45) -> None:
    """Delay the interior keys of selected bones (follow-through): head / forearms / hands trail their parents.

    ``lags``: bone -> seconds.  The first and last keys of every track stay where they are."""
    for bone, lag in lags.items():
        if bone not in a.bones:
            continue
        bt = a.bones[bone]
        for tr in (bt.rot, bt.pos):
            ks = tr.keys
            if len(ks) < 3:
                continue
            new = [ks[0]]
            for i in range(1, len(ks) - 1):
                t, v, e = ks[i]
                gap_prev = ks[i][0] - ks[i - 1][0]
                gap_next = ks[i + 1][0] - ks[i][0]
                shift = min(lag, gap_next * min_gap_frac) if gap_next > 0 else 0.0
                new.append((t + shift, v, e))
            new.append(ks[-1])
            tr.keys = new
