"""Pan-European club model for UEFA competitions (Champions League, Europa League, Conference
League), and its walk-forward backtest.

One time-weighted Dixon-Coles fit over every domestic league we hold plus all UEFA matches:

    attack_i  = s_L(i) + a_i        defence_i = s_L(i) + d_i
    log E[home goals] = c_k + h_k·(not neutral) + attack_home − defence_away
    log E[away goals] = c_k + attack_away − defence_home

where L(i) is the team's main domestic league in the training window (its country for clubs
whose league we don't hold, e.g. Ukraine), and k is the match's competition: each domestic
division and the UEFA matches get their own scoring level c_k and home advantage h_k.
Within a league the offset s_L cancels, so domestic results set each team's place in its
league; UEFA matches (and promotion/relegation movers) set the leagues against each other.
Team deviations a_i, d_i get a ridge penalty; league offsets a weaker one.
"""

from dataclasses import dataclass

import numpy as np
import pandas as pd
from scipy.optimize import minimize
from sqlalchemy import Engine, text

from footy.modeling.base import ALL_MARKETS
from footy.modeling.dixon_coles import _tau_terms
from footy.modeling.grid import MarketProbs, markets_from_grid, poisson_grid
from footy.modeling.metrics import (
    brier_binary,
    brier_multi,
    logloss_binary,
    logloss_multi,
    pick_1x2,
    rps,
)

EURO_COMPS = ("UCL", "UEL", "UECL", "UCLQ", "UELQ", "UECLQ")
EURO_ID_OFFSET = 10_000_000


def load_combined(engine: Engine, include_scheduled: bool = False) -> pd.DataFrame:
    """Domestic matches (every division) and UEFA matches in one frame, oldest first.
    `comp` is the division code or UEFA competition; `kind` is "domestic" or "euro"."""
    with engine.connect() as conn:
        dom = pd.read_sql(
            text(
                "SELECT m.id, m.division AS comp, m.season, m.match_date, m.kickoff_utc, "
                "m.home_team, m.away_team, m.home_goals, m.away_goals, m.model_exclusion, "
                "t.country AS home_country "
                "FROM matches m JOIN teams t ON t.team_key = m.home_team"
            ),
            conn,
        )
        status = "('played', 'scheduled')" if include_scheduled else "('played')"
        eur = pd.read_sql(
            text(
                "SELECT e.id, e.competition AS comp, e.season, e.stage, e.match_date, "
                "e.kickoff_utc, e.home_team, e.away_team, e.home_goals, e.away_goals, "
                "e.neutral, e.status, e.model_exclusion, e.extra_time, e.penalties "
                f"FROM euro_matches e WHERE e.status IN {status}"
            ),
            conn,
        )
    dom["kind"], dom["neutral"], dom["status"] = "domestic", 0, "played"
    eur["kind"] = "euro"
    eur["id"] = eur["id"] + EURO_ID_OFFSET
    df = pd.concat([dom, eur], ignore_index=True, sort=False)
    df["date"] = pd.to_datetime(df.match_date)
    df["kickoff_utc"] = pd.to_datetime(df.kickoff_utc, utc=True, format="mixed")
    df["neutral"] = df.neutral.fillna(0).astype(int)
    played = df.status == "played"
    df.loc[played, "outcome"] = np.where(
        df.home_goals > df.away_goals, 0, np.where(df.home_goals == df.away_goals, 1, 2)
    )[played.to_numpy()]
    return df.sort_values(["date", "id"]).reset_index(drop=True)


def main_league(history: pd.DataFrame, team_country: dict[str, str]) -> dict[str, str]:
    """Each team's main domestic league in the window (most matches), else its country."""
    dom = history[history.kind == "domestic"]
    long = pd.concat(
        [
            dom[["home_team", "comp"]].rename(columns={"home_team": "team"}),
            dom[["away_team", "comp"]].rename(columns={"away_team": "team"}),
        ]
    )
    league = long.groupby("team")["comp"].agg(lambda s: s.value_counts().index[0]).to_dict()
    teams = set(history.home_team) | set(history.away_team)
    return {t: league.get(t, f"country:{team_country.get(t, 'UNK')}") for t in teams}


class PanEuropeanDC:
    markets = ALL_MARKETS

    def __init__(
        self,
        xi: float = 0.0019,
        reg_team: float = 3.0,
        reg_league: float = 0.3,
        euro_weight: float = 1.5,
        history_days: int = 3 * 365,
        name: str | None = None,
    ):
        self.xi, self.reg_team, self.reg_league = xi, reg_team, reg_league
        self.euro_weight, self.history_days = euro_weight, history_days
        self.name = (
            name or f"pan_euro_dc_xi{xi:g}_ew{euro_weight:g}_rt{reg_team:g}_rl{reg_league:g}"
        )
        self._x: dict[str, float] = {}
        self.team_country: dict[str, str] = {}

    def params(self) -> dict:
        return {
            "xi": self.xi,
            "reg_team": self.reg_team,
            "reg_league": self.reg_league,
            "euro_weight": self.euro_weight,
            "history_days": self.history_days,
        }

    def fit(self, history: pd.DataFrame, cutoff_date: pd.Timestamp) -> None:
        h = history[(history.date >= cutoff_date - pd.Timedelta(days=self.history_days))]
        h = h[h.model_exclusion.isna() & h.home_goals.notna()]
        league_of = main_league(h, self.team_country)
        teams = sorted(league_of)
        leagues = sorted(set(league_of.values()))
        comps = sorted(h.comp.map(lambda c: "EURO" if c in EURO_COMPS else c).unique())
        ti = {t: i for i, t in enumerate(teams)}
        li = {lg: i for i, lg in enumerate(leagues)}
        ki = {k: i for i, k in enumerate(comps)}
        T, L, K = len(teams), len(leagues), len(comps)
        team_league = np.array([li[league_of[t]] for t in teams])

        hi = h.home_team.map(ti).to_numpy()
        ai = h.away_team.map(ti).to_numpy()
        kk = h.comp.map(lambda c: "EURO" if c in EURO_COMPS else c).map(ki).to_numpy()
        home_on = (h.neutral.to_numpy() == 0).astype(float)
        days = (cutoff_date - h.date).dt.days.to_numpy()
        w = np.exp(-self.xi * days) * np.where(h.kind.to_numpy() == "euro", self.euro_weight, 1.0)
        yh, ya = h.home_goals.to_numpy(float), h.away_goals.to_numpy(float)
        gh, ga = yh.astype(int), ya.astype(int)

        names = (
            [f"a:{t}" for t in teams]
            + [f"d:{t}" for t in teams]
            + [f"s:{lg}" for lg in leagues]
            + [f"c:{k}" for k in comps]
            + [f"h:{k}" for k in comps]
            + ["rho"]
        )
        n = len(names)
        x0 = np.array([self._x.get(nm, np.nan) for nm in names])
        fresh = np.isnan(x0)
        x0[fresh] = 0.0
        o_s, o_c, o_h = 2 * T, 2 * T + L, 2 * T + L + K
        for i in ki.values():
            if fresh[o_c + i]:
                x0[o_c + i] = 0.25
            if fresh[o_h + i]:
                x0[o_h + i] = 0.2
        if fresh[-1]:
            x0[-1] = -0.05

        def f(x):
            a, d, s = x[:T], x[T : 2 * T], x[o_s : o_s + L]
            c, hh, rho = x[o_c : o_c + K], x[o_h : o_h + K], x[-1]
            att, dfn = s[team_league] + a, s[team_league] + d
            eta_h = c[kk] + hh[kk] * home_on + att[hi] - dfn[ai]
            eta_a = c[kk] + att[ai] - dfn[hi]
            lh, la = np.exp(eta_h), np.exp(eta_a)
            tau, dt_rho, dt_eh, dt_ea = _tau_terms(gh, ga, lh, la, rho)
            ll = w * (yh * eta_h - lh + ya * eta_a - la + np.log(tau))
            g_h = w * (yh - lh + dt_eh)
            g_a = w * (ya - la + dt_ea)
            g_att = np.bincount(hi, g_h, T) + np.bincount(ai, g_a, T)  # d ll / d attack
            g_def = -(np.bincount(ai, g_h, T) + np.bincount(hi, g_a, T))  # d ll / d defence
            obj = (
                -ll.sum() + 0.5 * self.reg_team * (a @ a + d @ d) + 0.5 * self.reg_league * (s @ s)
            )
            g = np.zeros(n)
            g[:T] = -g_att + self.reg_team * a
            g[T : 2 * T] = -g_def + self.reg_team * d
            g[o_s : o_s + L] = -np.bincount(team_league, g_att + g_def, L) + self.reg_league * s
            g[o_c : o_c + K] = -np.bincount(kk, g_h + g_a, K)
            g[o_h : o_h + K] = -np.bincount(kk, g_h * home_on, K)
            g[-1] = -(w * dt_rho).sum()
            return obj, g

        bounds = [(None, None)] * (n - 1) + [(-0.25, 0.25)]
        res = minimize(f, x0, jac=True, method="L-BFGS-B", bounds=bounds, options={"maxiter": 800})
        x = res.x
        self._x = dict(zip(names, x, strict=True))
        self.ti, self.li, self.ki, self.league_of = ti, li, ki, league_of
        self.T, self.L, self.K, self.x = T, L, K, x
        self.team_league = team_league
        self.converged_ = bool(res.success)

    def strength(self, team: str) -> tuple[float, float] | None:
        if team not in self.ti:
            return None
        i, T = self.ti[team], self.T
        s = self.x[2 * T + self.team_league[i]]
        return float(s + self.x[i]), float(s + self.x[T + i])

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        T, L, K, x = self.T, self.L, self.K, self.x
        o_c, o_h = 2 * T + L, 2 * T + L + K
        lh, la = [], []
        for r in fixtures.itertuples():
            k = self.ki.get("EURO" if r.comp in EURO_COMPS else r.comp)
            c = x[o_c + k] if k is not None else float(np.mean(x[o_c : o_c + K]))
            hh = x[o_h + k] if k is not None else float(np.mean(x[o_h : o_h + K]))
            sh, sa = self.strength(r.home_team), self.strength(r.away_team)
            sh = sh or (0.0, 0.0)
            sa = sa or (0.0, 0.0)
            home_on = 0.0 if getattr(r, "neutral", 0) else 1.0
            lh.append(np.exp(c + hh * home_on + sh[0] - sa[1]))
            la.append(np.exp(c + sa[0] - sh[1]))
        return np.array(lh), np.array(la)

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.rates(fixtures)
        return markets_from_grid(poisson_grid(lh, la, float(self.x[-1])), lh, la)


class EuroFrequency:
    """Baseline: home/draw/away, goal-line and BTTS rates over UEFA matches in the window."""

    markets = ALL_MARKETS

    def __init__(self, days: int = 3 * 365):
        self.days = days
        self.name = "euro_frequency"
        self.team_country: dict[str, str] = {}

    def params(self) -> dict:
        return {"days": self.days}

    def fit(self, history: pd.DataFrame, cutoff_date: pd.Timestamp) -> None:
        e = history[
            (history.kind == "euro") & (history.date >= cutoff_date - pd.Timedelta(days=self.days))
        ]
        e = e[e.home_goals.notna() & (e.neutral == 0)]
        tot = e.home_goals + e.away_goals
        self.p1x2 = np.bincount(e.outcome.astype(int), minlength=3) / len(e)
        self.over = {k: float((tot > k).mean()) for k in (0.5, 1.5, 2.5, 3.5, 4.5)}
        self.btts = float(((e.home_goals > 0) & (e.away_goals > 0)).mean())

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        n = len(fixtures)
        return MarketProbs(
            p1x2=np.tile(self.p1x2, (n, 1)),
            p_over={k: np.full(n, v) for k, v in self.over.items()},
            p_btts=np.full(n, self.btts),
        )


@dataclass
class EuroSpec:
    model_factory: object
    seasons: list[str]
    comps: tuple[str, ...] = ("UCL",)
    min_history: int = 5


def run_euro_backtest(
    df: pd.DataFrame, spec: EuroSpec, team_country: dict[str, str]
) -> pd.DataFrame:
    """Walk-forward over UEFA matches. Decision time: only results dated at least two days
    before the match are used (00:00 UK on the day before kickoff)."""
    targets = df[(df.kind == "euro") & df.comp.isin(spec.comps) & df.season.isin(spec.seasons)]
    targets = targets[targets.status == "played"]
    model = spec.model_factory()
    model.team_country = team_country
    rows = []
    for day, batch in targets.groupby("date", sort=True):
        cutoff_date = day - pd.Timedelta(days=1)
        hist = df[(df.date < cutoff_date) & (df.status == "played")]
        if not hist.empty and hist.date.max() >= cutoff_date:
            raise AssertionError("leakage: history reaches the cutoff")
        counts = pd.concat([hist.home_team, hist.away_team]).value_counts()
        live = batch[batch.model_exclusion.isna()]
        ok = (live.home_team.map(counts).fillna(0) >= spec.min_history) & (
            live.away_team.map(counts).fillna(0) >= spec.min_history
        )
        for r in batch[batch.model_exclusion.notna()].itertuples():
            rows.append(_row(r, cutoff_date) | {"status": "excluded", "reason": r.model_exclusion})
        for r in live[~ok].itertuples():
            rows.append(_row(r, cutoff_date) | {"status": "ineligible", "reason": "history"})
        fx = live[ok]
        if fx.empty:
            continue
        model.fit(hist, cutoff_date)
        mp = model.predict(fx.drop(columns=["home_goals", "away_goals", "outcome"]))
        rows.extend(_scored(fx, mp, cutoff_date))
    led = pd.DataFrame(rows)
    led.insert(0, "model", model.name)
    return led


def _row(r, cutoff_date) -> dict:
    return {
        "match_id": int(r.id),
        "comp": r.comp,
        "season": r.season,
        "stage": getattr(r, "stage", None),
        "date": r.date.date().isoformat(),
        "home_team": r.home_team,
        "away_team": r.away_team,
        "neutral": int(r.neutral),
        "batch_cutoff_utc": cutoff_date.isoformat(),
        "home_goals": None if pd.isna(r.home_goals) else int(r.home_goals),
        "away_goals": None if pd.isna(r.away_goals) else int(r.away_goals),
    }


def _scored(fx, mp: MarketProbs, cutoff_date) -> list[dict]:
    hg, ag = fx.home_goals.to_numpy(int), fx.away_goals.to_numpy(int)
    outcome = fx.outcome.to_numpy(int)
    tot = hg + ag
    out = []
    p = mp.p1x2
    r_ = rps(p, outcome)
    ll = logloss_multi(p, outcome)
    br = brier_multi(p, outcome)
    pick = pick_1x2(p)
    btts = ((hg > 0) & (ag > 0)).astype(int)
    for j, r in enumerate(fx.itertuples()):
        row = _row(r, cutoff_date) | {
            "status": "predicted",
            "reason": None,
            "p_H": p[j, 0],
            "p_D": p[j, 1],
            "p_A": p[j, 2],
            "rps": r_[j],
            "ll_1x2": ll[j],
            "brier_1x2": br[j],
            "pick_1x2": "HDA"[pick[j]],
            "correct_1x2": int(pick[j] == outcome[j]),
            "p_btts": mp.p_btts[j],
            "ll_btts": float(logloss_binary(mp.p_btts[j : j + 1], btts[j : j + 1])[0]),
            "brier_btts": float(brier_binary(mp.p_btts[j : j + 1], btts[j : j + 1])[0]),
            "correct_btts": int((mp.p_btts[j] >= 0.5) == (btts[j] == 1)),
        }
        for k, v in mp.p_over.items():
            y = int(tot[j] > k)
            row[f"p_over_{k:g}"] = v[j]
            row[f"ll_ou_{k:g}"] = float(logloss_binary(v[j : j + 1], np.array([y]))[0])
            row[f"brier_ou_{k:g}"] = float(brier_binary(v[j : j + 1], np.array([y]))[0])
            row[f"correct_ou_{k:g}"] = int((v[j] >= 0.5) == (y == 1))
        if mp.lam_h is not None:
            row["lam_h"], row["lam_a"] = mp.lam_h[j], mp.lam_a[j]
        out.append(row)
    return out


class PanEuropeanElo:
    """World Football Elo over every domestic and UEFA match, then a Poisson mapping from the
    rating gap to each side's expected goals, fitted on UEFA matches in the window.

    Ratings are updated incrementally as the cutoff advances (history only ever grows in a
    walk-forward), and rebuilt from scratch if the cutoff moves backwards. New clubs start at
    a level set by where they are first seen (core top flight, other top flights, second
    tiers, or UEFA-only clubs from leagues we don't hold).
    """

    markets = ALL_MARKETS
    START = {"core_top": 1500.0, "europe_top": 1450.0, "tier2": 1400.0, "uefa_only": 1400.0}

    def __init__(
        self,
        k_domestic: float = 20.0,
        k_euro: float = 30.0,
        home: float = 65.0,
        map_days: int = 4 * 365,
        name: str | None = None,
    ):
        self.k_domestic, self.k_euro, self.home, self.map_days = k_domestic, k_euro, home, map_days
        self.name = name or f"pan_euro_elo_kd{k_domestic:g}_ke{k_euro:g}_h{home:g}"
        self.team_country: dict[str, str] = {}
        self._reset()

    def _reset(self):
        self.r: dict[str, float] = {}
        self._done_until = pd.Timestamp.min
        self._seen_ids: set[int] = set()
        self._pre: list[tuple] = []  # (date, kind, neutral, dr, hg, ag) for the goal mapping

    def params(self) -> dict:
        return {
            "k_domestic": self.k_domestic,
            "k_euro": self.k_euro,
            "home": self.home,
            "map_days": self.map_days,
        }

    def _start(self, comp: str) -> float:
        from footy.reference import DIVISIONS

        d = DIVISIONS.get(comp)
        if d is None:
            return self.START["uefa_only"]
        if d.tier == 2:
            return self.START["tier2"]
        return self.START["core_top"] if d.scope == "core" else self.START["europe_top"]

    def fit(self, history: pd.DataFrame, cutoff_date: pd.Timestamp) -> None:
        if cutoff_date < self._done_until:
            self._reset()
        new = history[(history.date >= self._done_until) & ~history.id.isin(self._seen_ids)]
        new = new[new.home_goals.notna() & new.model_exclusion.isna()].sort_values(["date", "id"])
        for m in new.itertuples():
            rh = self.r.setdefault(m.home_team, self._start(m.comp))
            ra = self.r.setdefault(m.away_team, self._start(m.comp))
            neutral = bool(m.neutral)
            dr = rh - ra + (0.0 if neutral else self.home)
            we = 1 / (10 ** (-dr / 400) + 1)
            gd = abs(int(m.home_goals) - int(m.away_goals))
            g = 1.0 if gd <= 1 else 1.5 if gd == 2 else (11 + gd) / 8
            w = 1.0 if m.home_goals > m.away_goals else 0.5 if m.home_goals == m.away_goals else 0.0
            k = self.k_euro if m.kind == "euro" else self.k_domestic
            delta = k * g * (w - we)
            self.r[m.home_team], self.r[m.away_team] = rh + delta, ra - delta
            self._pre.append((m.date, m.kind, neutral, dr, int(m.home_goals), int(m.away_goals)))
            self._seen_ids.add(int(m.id))
        self._done_until = cutoff_date
        self._fit_mapping(cutoff_date)

    def _fit_mapping(self, cutoff_date):
        pre = [
            p
            for p in self._pre
            if p[1] == "euro" and p[0] >= cutoff_date - pd.Timedelta(days=self.map_days)
        ]
        if len(pre) < 50:  # too few UEFA matches yet: fall back to all matches
            pre = [p for p in self._pre if p[0] >= cutoff_date - pd.Timedelta(days=self.map_days)]
        dr = np.array([p[3] for p in pre]) / 400.0
        hg = np.array([p[4] for p in pre], float)
        ag = np.array([p[5] for p in pre], float)

        def nll(t):
            a, b = t
            lh, la = np.exp(a + b * dr), np.exp(a - b * dr)
            return -(hg * np.log(lh) - lh + ag * np.log(la) - la).sum()

        res = minimize(nll, x0=np.array([0.2, 0.5]), method="L-BFGS-B")
        self.a, self.b = float(res.x[0]), float(res.x[1])
        lh, la = np.exp(self.a + self.b * dr), np.exp(self.a - self.b * dr)

        def nll_rho(rho):
            tau = _tau_terms(hg.astype(int), ag.astype(int), lh, la, rho[0])[0]
            return -np.log(tau).sum()

        self.rho_ = float(minimize(nll_rho, x0=np.array([-0.05]), bounds=[(-0.2, 0.2)]).x[0])

    def rating(self, team: str) -> float | None:
        return self.r.get(team)

    def strength(self, team: str):
        r = self.r.get(team)
        return None if r is None else (round(r, 1), round(r, 1))

    @property
    def league_of(self) -> dict:
        return {}

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = [], []
        for f in fixtures.itertuples():
            rh = self.r.get(f.home_team, self.START["uefa_only"])
            ra = self.r.get(f.away_team, self.START["uefa_only"])
            dr = (rh - ra + (0.0 if getattr(f, "neutral", 0) else self.home)) / 400.0
            lh.append(np.exp(self.a + self.b * dr))
            la.append(np.exp(self.a - self.b * dr))
        lh, la = np.array(lh), np.array(la)
        return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)
