"""Joint game simulation (model family J).

One draw = one simulated game. Shared game-level noise drives both teams' play counts and
dropback rates through a residual covariance estimated from training history (opponents'
play counts and pass rates are negatively correlated). Within a team:

  plays N -> dropbacks D (pass rate) and runs N-D -> kneels K and designed runs R
  D -> sacks S -> scrambles Sc -> pass attempts A = D - S - Sc  (official attempts)
  A -> targets per receiver + an untargeted bucket (Dirichlet-multinomial on shares of the
       players who are available in that draw) -> receptions ~ Binomial(targets, catch)
  receiving yards = sum of per-catch yards drawn from the player's own recent catches mixed
       with the position pool (so negative plays and long tails are kept)
  QB completions/yards = sum over receivers of the catches he threw; a starter who leaves
       the game early hands the rest to the backup by binomial/hypergeometric thinning
  R -> carries per rusher (QB designed runs included); QB carries = designed runs +
       scrambles + kneels, matching nflverse/official conventions (see docs/METHODOLOGY.md)

By construction completions <= attempts, receptions <= targets, team targets <= team
attempts, and every player's volume comes out of the team's simulated volume.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from .features import HP, GameIn, Params, PlayerIn, TeamIn

STATS = ("attempts", "completions", "passing_yards", "carries", "rushing_yards", "targets",
         "receptions", "receiving_yards")


@dataclass
class PlayerSim:
    player: PlayerIn
    played: np.ndarray  # bool (n,)
    stats: dict  # stat -> int array (n,)


def _sum_draws(rng, counts: np.ndarray, own: np.ndarray, pool: np.ndarray, w_own: float,
               factor: float, cap_first: np.ndarray | None = None):
    """Sum `counts[i]` iid per-play yards per draw. Returns (total, total_of_first_k) where
    the second sums only the first cap_first[i] plays (used for QB thinning)."""
    n = counts.shape[0]
    kmax = int(counts.max()) if n else 0
    if kmax == 0:
        z = np.zeros(n, dtype=np.int64)
        return z, z.copy()
    use_own = (rng.random((n, kmax)) < w_own) if len(own) else np.zeros((n, kmax), bool)
    src_pool = pool if len(pool) else np.array([0.0])
    v = src_pool[rng.integers(0, len(src_pool), size=(n, kmax))]
    if len(own):
        v_own = own[rng.integers(0, len(own), size=(n, kmax))]
        v = np.where(use_own, v_own, v)
    v = np.rint(v * factor)
    j = np.arange(kmax)[None, :]
    total = (v * (j < counts[:, None])).sum(1).astype(np.int64)
    if cap_first is None:
        return total, total
    first = (v * (j < cap_first[:, None])).sum(1).astype(np.int64)
    return total, first


def _dirichlet(rng, alpha_vec: np.ndarray) -> np.ndarray:
    g = rng.gamma(np.clip(alpha_vec, 1e-6, None))
    s = g.sum(1, keepdims=True)
    s[s == 0] = 1
    return g / s


def simulate_team(rng, t: TeamIn, P: Params, N: np.ndarray, dbr: np.ndarray, z_dbr: np.ndarray,
                  n: int) -> list[PlayerSim]:
    qbs = [p for p in t.players if p.role == "QB"]
    others = [p for p in t.players if p.role != "QB"]
    D = rng.binomial(N, np.clip(dbr, 0.2, 0.92))
    runs = N - D
    sd = max(np.sqrt(P.resid_cov[2, 2]), 1e-3)
    lam_k = P.kneel_mean * np.exp(-0.8 * z_dbr / sd - 0.32)  # leading teams kneel more
    K = np.minimum(rng.poisson(lam_k), runs)
    R = runs - K
    S = rng.binomial(D, t.sack_rate)

    # --- quarterbacks: starter by start probability; early exit hands over to a backup
    if qbs:
        ps = np.array([q.p_start for q in qbs])
        ps = ps / ps.sum()
        starter = rng.choice(len(qbs), size=n, p=ps)
        exit_ = rng.random(n) < P.q_qb_exit
        f = np.where(exit_, rng.uniform(0.05, 0.95, n), 1.0)
        # backup = most likely other QB
        order = np.argsort(-ps)
        backup = np.where(starter == order[0], order[1] if len(qbs) > 1 else -1, order[0])
        scr_rate = np.array([q.scr_rate for q in qbs])[starter]
        q_dshare = np.array([max(_qb_dshare(q), 0.0) for q in qbs])[starter]
    else:
        starter = np.full(n, -1)
        backup = np.full(n, -1)
        f = np.ones(n)
        exit_ = np.zeros(n, bool)
        scr_rate = np.full(n, P.league["scr_rate"])
        q_dshare = np.full(n, 0.05)
    Sc = rng.binomial(D - S, scr_rate)
    A = D - S - Sc

    # --- availability of non-QB players in this draw
    m = len(others)
    avail = rng.random((n, m)) < np.array([p.p_play for p in others])[None, :] if m else \
        np.zeros((n, 0), bool)

    # --- targets: untargeted bucket + receivers
    ts = np.array([p.tshare for p in others]) if m else np.zeros(0)
    w = np.column_stack([np.full(n, t.notarget), avail * ts[None, :]])
    w = w / w.sum(1, keepdims=True)
    p_t = _dirichlet(rng, P.alpha_target * w)
    tgt = rng.multinomial(A, p_t)
    sims: list[PlayerSim] = []
    qb_att1 = rng.binomial(tgt[:, 0], f)
    qb_cmp1 = np.zeros(n, np.int64)
    qb_yds1 = np.zeros(n, np.int64)
    tot_cmp = np.zeros(n, np.int64)
    tot_yds = np.zeros(n, np.int64)
    rec_stats = []
    for i, p in enumerate(others):
        ti = tgt[:, i + 1]
        cr = float(np.clip(p.catch * t.catch_factor, 0.05, 0.98))
        ri = rng.binomial(ti, cr)
        t1 = rng.binomial(ti, f)
        r1 = _hypergeo(rng, ri, ti - ri, t1)
        n_own = len(p.rec_pool)
        w_own = n_own / (n_own + HP["pool_prior_plays"])
        yds, yds1 = _sum_draws(rng, ri, p.rec_pool, P.pools.get(("rec", p.role), np.array([])),
                               w_own, t.rec_factor, r1)
        qb_att1 += t1
        qb_cmp1 += r1
        qb_yds1 += yds1
        tot_cmp += ri
        tot_yds += yds
        rec_stats.append((ti, ri, yds))

    # --- designed runs: QB slot + non-QB rushers
    ds = np.array([p.dshare for p in others]) if m else np.zeros(0)
    wr = np.column_stack([q_dshare, avail * ds[None, :]])
    wr_sum = wr.sum(1, keepdims=True)
    wr = np.where(wr_sum > 0, wr / np.where(wr_sum == 0, 1, wr_sum), 1.0 / wr.shape[1])
    p_r = _dirichlet(rng, P.alpha_carry * wr)
    car = rng.multinomial(R, p_r)
    for i, p in enumerate(others):
        ci = car[:, i + 1]
        n_own = len(p.rush_pool)
        w_own = n_own / (n_own + HP["pool_prior_plays"])
        ry, _ = _sum_draws(rng, ci, p.rush_pool, P.pools.get(("rush", p.role), np.array([])),
                           w_own, t.rush_factor)
        ti, ri, yds = rec_stats[i]
        sims.append(PlayerSim(p, avail[:, i], {
            "targets": ti, "receptions": ri, "receiving_yards": yds, "carries": ci,
            "rushing_yards": ry, "attempts": np.zeros(n, np.int64),
            "completions": np.zeros(n, np.int64), "passing_yards": np.zeros(n, np.int64)}))

    # --- QB stats: split between the starter (fraction f of plays) and the backup
    if qbs:
        q_runs = car[:, 0]
        qr1 = rng.binomial(q_runs, f)
        sc1 = rng.binomial(Sc, f)
        k_to_starter = ~exit_  # kneels go to whoever finishes the game
        for j, q in enumerate(qbs):
            is_start = starter == j
            is_back = (backup == j) & exit_
            # cameo: on the field (holder, kneel, gadget play) without a passing share;
            # simplified to zero stats (documented limitation)
            cameo = ~is_start & ~is_back & (rng.random(n) < P.p_qb_cameo)
            played = is_start | is_back | cameo
            att = np.where(is_start, qb_att1, np.where(is_back, A - qb_att1, 0))
            cmp_ = np.where(is_start, qb_cmp1, np.where(is_back, tot_cmp - qb_cmp1, 0))
            pyd = np.where(is_start, qb_yds1, np.where(is_back, tot_yds - qb_yds1, 0))
            dr = np.where(is_start, qr1, np.where(is_back, q_runs - qr1, 0))
            scr = np.where(is_start, sc1, np.where(is_back, Sc - sc1, 0))
            kn = np.where(is_start & k_to_starter, K, np.where(is_back, K, 0))
            n_own = len(q.rush_pool)
            dry, _ = _sum_draws(rng, dr, q.rush_pool, P.pools.get(("rush", "QB"), np.array([])),
                                n_own / (n_own + HP["pool_prior_plays"]), t.rush_factor)
            so = q.scr_pool if q.scr_pool is not None else np.array([])
            sy, _ = _sum_draws(rng, scr, so, P.pools.get(("scr", "QB"), np.array([])),
                               len(so) / (len(so) + HP["pool_prior_plays"]), 1.0)
            ky, _ = _sum_draws(rng, kn, np.array([]), P.pools.get(("kneel", "QB"),
                                                                   np.array([-1.0])), 0.0, 1.0)
            sims.append(PlayerSim(q, played, {
                "attempts": att, "completions": cmp_, "passing_yards": pyd,
                "carries": dr + scr + kn, "rushing_yards": dry + sy + ky,
                "targets": np.zeros(n, np.int64), "receptions": np.zeros(n, np.int64),
                "receiving_yards": np.zeros(n, np.int64)}))
    team_tot = {"plays": N, "dropbacks": D, "attempts": A, "sacks": S, "scrambles": Sc,
                "kneels": K, "designed_runs": R, "completions": tot_cmp, "passing_yards": tot_yds}
    return sims, team_tot


def _qb_dshare(q: PlayerIn) -> float:
    return q.dshare if q.dshare > 0 else 0.03


def _hypergeo(rng, ngood, nbad, nsample):
    out = np.zeros_like(ngood)
    ok = (ngood + nbad) > 0
    if ok.any():
        out[ok] = rng.hypergeometric(ngood[ok], nbad[ok], np.minimum(nsample[ok],
                                                                      ngood[ok] + nbad[ok]))
    return out


def simulate_game(g: GameIn, P: Params, n: int = 10_000, seed: int = 0):
    rng = np.random.default_rng(seed)
    z = rng.multivariate_normal(np.zeros(4), P.resid_cov, size=n)
    out = {}
    for side, t, ip, idb in (("home", g.home, 0, 2), ("away", g.away, 1, 3)):
        N = np.maximum(30, np.rint(t.mu_plays + z[:, ip])).astype(np.int64)
        dbr = t.dbr + z[:, idb]
        out[side] = simulate_team(rng, t, P, N, dbr, z[:, idb], n)
    return out
