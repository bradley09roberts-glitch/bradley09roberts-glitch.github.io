"""Post-processing passes applied to finished animations (operate on the in-memory Animation objects)."""
from __future__ import annotations

import math
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


def ground_lock(rig: Rig, a: Animation, hidden: Sequence[str], slack: float = 0.1, step: float = 0.02) -> float:
    """Keep the model on top of the floor: wherever the lowest model point would sink more than ``slack`` px
    below y=0, the root is lifted by the excess.

    The root position track is re-written as a dense (``step`` s) linear track holding the *exact* GeckoLib-sampled
    original root position plus the lift, so the authored root motion is preserved.  Does nothing (returns 0.0)
    when the animation never sinks.  Returns the largest lift applied."""
    clip = Clip(a.name, a.to_json())
    times = sorted(set(key_times(a)) | {0.0, round(a.length, 5)})
    dense = set(times)
    for t0, t1 in zip(times, times[1:]):
        n = max(1, int(math.ceil((t1 - t0) / step - 1e-9)))
        for k in range(1, n):
            dense.add(round(t0 + (t1 - t0) * k / n, 5))
    dense = sorted(dense)
    lifts, roots = [], []
    for t in dense:
        st = clip.sample(t, loop=False)
        low = lowest_y(rig, st, hidden)
        lifts.append(max(0.0, -low - slack))
        roots.append(st.get("root", {}).get("pos", (0.0, 0.0, 0.0)))
    top = max(lifts)
    if top < 0.12:                                    # negligible sink: leave the authored animation untouched
        return 0.0
    bt = a._bt("root")
    bt.pos.keys = [(t, (r[0], r[1] + l, r[2]), "linear") for t, r, l in zip(dense, roots, lifts)]
    return top


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
