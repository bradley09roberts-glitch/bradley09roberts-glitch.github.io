"""The two-stat NBA combo markets (pr, pa, ra; 4 Nov 2026): the five original markets' pmfs are
bit for bit what the committed code gave before the combos existed (tests/fixtures, written with
that code), the combo pmfs are the PRA construction with two parts (checked against a brute-force
convolution), combos settle as sums of their parts, the two frozen paper rules never see them,
they have no real-line calibration or anchored chance, and the offline copy rebuilds slimmed
combo cards exactly. Synthetic data only: no database, edition or network is touched."""

import json
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

from npl import books, picks, snapshot, track
from npl.dist import Dist
from npl.nba import appdata as A
from npl.nba import calib, factors, live, settle
from npl.nba import model as M
from npl.nba import simple as S

sys.path.insert(0, str(Path(__file__).parent))
from nba_pmf_fixture import FIVE, REF_VERSIONS, frame  # noqa: E402

REF = Path(__file__).parent / "fixtures" / "nba_pmf_reference.npz"
NODE = shutil.which("node")


def _mean(pm: np.ndarray) -> np.ndarray:
    return (pm * np.arange(pm.shape[1])).sum(1)


# ------------------------------------------------------------------ the five markets unchanged
def test_market_lists():
    assert M.ORIGINAL_MARKETS == FIVE and M.COMBOS == ("pr", "pa", "ra")
    assert M.MARKETS == FIVE + M.COMBOS  # the original order first: edition rows keep theirs
    assert {M.COMBO_PARTS[c] for c in M.COMBOS} == {("pts", "reb"), ("pts", "ast"),
                                                    ("reb", "ast")}
    assert M.COMBO_PARTS["pra"] == ("pts", "reb", "ast")
    assert (M.MARKET_LABELS["pr"], M.MARKET_LABELS["pa"], M.MARKET_LABELS["ra"]) == (
        "Pts + Reb", "Pts + Ast", "Reb + Ast")
    assert (M.MARKET_SHORT["pr"], M.MARKET_SHORT["pa"], M.MARKET_SHORT["ra"]) == (
        "P+R", "P+A", "R+A")
    # the single stat standing in for a summed market's opponent factor
    assert M.OPP_STAT == {"pra": "pts", "pr": "pts", "pa": "pts", "ra": "reb"}
    assert live.LIVE_PARAMS == "nba-0.3"


@pytest.mark.parametrize("version", REF_VERSIONS)
def test_original_markets_are_bit_identical_to_the_pre_combo_code(version):
    """The reference was written by the committed code before the combos (git HEAD 28a8e20):
    N1 for nba-0.3 (live) and nba-0.1, baselines A and C for nba-0.3."""
    ref = np.load(REF)
    x, lg = frame()
    p = M.load_params(version)
    for pm in (M.n1_pmfs(x, p, lg), M.n1_pmfs(x, p, lg, only=FIVE)):
        for k in FIVE:
            assert np.array_equal(pm[k], ref[f"N1|{version}|{k}"]), k
    if version == "nba-0.3":
        xc = x.assign(**{f"{c}_{w}": x[f"{a}_{w}"] + x[f"{b}_{w}"]
                         for c, (a, b) in ((c, M.COMBO_PARTS[c]) for c in M.COMBOS)
                         for w in ("gm", "l10")})
        for kind in ("A", "C"):
            for b in (M.baseline_pmfs(x, p, kind, only=FIVE), M.baseline_pmfs(xc, p, kind)):
                for k in FIVE:
                    assert np.array_equal(b[k], ref[f"{kind}|{version}|{k}"]), (kind, k)


# ------------------------------------------------------------------ combo pmfs
def test_combo_pmfs_are_distributions_consistent_with_their_parts():
    x, lg = frame()
    p = M.load_params("nba-0.3")
    pm = M.n1_pmfs(x, p, lg)
    for c in M.COMBOS:
        assert pm[c].shape == (len(x), M.MAX_K[c] + 1)
        assert np.allclose(pm[c].sum(1), 1, atol=1e-12) and (pm[c] >= 0).all()
        assert pm[c][:, -1].max() < 1e-6  # the cap cuts nothing that matters
        a, b = M.COMBO_PARTS[c]
        # the summed markets use every other minute of the grid (as PRA always has), the single
        # stats every minute: the means agree closely, not exactly
        assert np.allclose(_mean(pm[c]), _mean(pm[a]) + _mean(pm[b]), rtol=0.02, atol=0.05), c
    # exact on the summed markets' own grid: P+R + P+A + R+A = 2 x PRA (truncation aside)
    assert np.allclose(_mean(pm["pr"]) + _mean(pm["pa"]) + _mean(pm["ra"]),
                       2 * _mean(pm["pra"]), atol=1e-6)
    only = M.n1_pmfs(x, p, lg, only=("ra",))
    assert set(only) == {"ra"} and np.array_equal(only["ra"], pm["ra"])


def test_combo_pmfs_match_a_brute_force_convolution():
    """Rates equal to the position priors (no history, neutral opponent and pace), so the
    conditional pmfs are easy to rebuild; then np.convolve per row and minutes value."""
    p = M.load_params("nba-0.3")
    lg = {"pos_rate": {"G": {"pts": .5, "reb": .15, "ast": .15, "fg3m": .05},
                       "F": {"pts": .45, "reb": .2, "ast": .1, "fg3m": .04},
                       "C": {"pts": .48, "reb": .32, "ast": .07, "fg3m": .01}}}
    x = pd.DataFrame({"min_mu": [31.3, 12.0, 24.6], "min_sd": 5.0, "pos": ["G", "F", "C"],
                      "opp_pace": 100.0, "lg_pace": 100.0, "opp_n": 20, "min_sum": np.nan})
    for s in M.BASE_STATS:
        x[f"{s}_sum"], x[f"def_{s}"], x[f"lg_{s}"] = np.nan, 1.0, 1.0
    pm = M.n1_pmfs(x, p, lg)
    W = M.minutes_weights_empirical(np.clip(x.min_mu.to_numpy(), 1, 48), p.min_resid,
                                    frac=p.min_frac)
    for c in ("pra", *M.COMBOS):
        parts = M.COMBO_PARTS[c]
        want = np.zeros((len(x), M.MAX_K[c] + 1))
        for i, pos in enumerate(x.pos):
            for j, mins in enumerate(M.MIN_GRID[::2]):
                w = W[i, 2 * j] + (W[i, 2 * j + 1] if 2 * j + 1 < W.shape[1] else 0)
                conv = np.array([1.0])
                for s in parts:
                    lam = np.array([lg["pos_rate"][pos][s] * (mins + 0.5)])
                    conv = np.convolve(conv, M.count_pmf(s, lam, p)[0])
                want[i] += w * conv[:M.MAX_K[c] + 1]
        want /= want.sum(1, keepdims=True)
        assert np.allclose(pm[c], want, atol=1e-12, rtol=0), c


def test_combo_baselines_use_the_summed_averages():
    x, _ = frame()
    for c in M.COMBOS:
        a, b = M.COMBO_PARTS[c]
        x[f"{c}_gm"], x[f"{c}_l10"] = x[f"{a}_gm"] + x[f"{b}_gm"], x[f"{a}_l10"] + x[f"{b}_l10"]
    p = M.load_params("nba-0.3")
    for kind, col in (("A", "gm"), ("C", "l10")):
        bp = M.baseline_pmfs(x, p, kind)
        for c in M.COMBOS:
            assert np.allclose(bp[c].sum(1), 1)
            ok = x[f"{c}_{col}"].to_numpy() < 0.5 * M.MAX_K[c]
            assert np.allclose(_mean(bp[c])[ok], x[f"{c}_{col}"].to_numpy()[ok], rtol=0.02)


def test_player_states_sum_the_parts():
    pg = pd.DataFrame({"player_id": ["a"] * 3, "game_id": ["g1", "g2", "g3"],
                       "tip_utc": pd.date_range("2026-11-01", periods=3, tz="UTC"),
                       "pts": [10, 20, 30], "reb": [5, 6, 7], "ast": [1, 2, 3], "fg3m": [1, 2, 3]})
    v = M.with_sums(pg)
    assert v.pr.tolist() == [15, 26, 37] and v.pa.tolist() == [11, 22, 33]
    assert v.ra.tolist() == [6, 8, 10] and v.pra.tolist() == [16, 28, 40]
    y = pd.DataFrame({"pts_y": [10.0], "reb_y": [4.0], "ast_y": [3.0]})
    assert [M.actual(y, c)[0] for c in ("pr", "pa", "ra", "pra")] == [14, 13, 7, 17]


# ------------------------------------------------------------------ settlement
def test_settle_actual_value_sums_the_parts():
    r = pd.Series({"pts": 21.0, "reb": 8.0, "ast": 6.0, "fg3m": 2.0})
    assert [settle.actual_value(r, m) for m in ("pts", "fg3m", "pra", "pr", "pa", "ra")] == [
        21, 2, 35, 29, 27, 14]


def test_paper_record_settles_combo_props_from_box_scores():
    pg = pd.DataFrame({"game_id": ["g1", "g1"], "player_id": ["p1", "p2"],
                       "participation": ["played", "dnp"], "pts": [20.0, 0], "reb": [7.0, 0],
                       "ast": [5.0, 0]})
    t = pd.DataFrame({"game_id": ["g1"] * 5, "player_id": ["p1"] * 4 + ["p2"],
                      "market": ["pr", "pa", "ra", "pra", "pr"],
                      "line": [26.5, 25.0, 12.5, 31.5, 10.5],
                      "side": ["over", "under", "under", "over", "over"]})
    out = track.settle(t, pg)
    assert out.actual.tolist()[:4] == [27, 25, 12, 32]
    assert out.status.tolist() == ["win", "push", "win", "win", "void"]


# ------------------------------------------------------------------ the frozen paper rules
def _card(market: str, **kw) -> dict:
    pm = np.exp(-((np.arange(60) - 30.0) ** 2) / 50.0)
    c = {"name": "A", "player_id": "1", "team": "X", "opponent": "Y", "game_id": "g",
         "market": market, "p_play": 0.99, "injury": None, "line_known": True,
         "tip_utc": (pd.Timestamp.now(tz="UTC") + pd.Timedelta(hours=2)).isoformat(),
         "tip_london": "", "dist": Dist.from_pmf(0, pm / pm.sum()).to_json(), "simple": 40.0,
         "market_line": {"line": 24.5, "fair_over": 0.5, "books": 5, "mgm_over": 2.3,
                         "mgm_under": 1.6, "mgm_line": 24.5, "arb": False,
                         "observed_at": "2026-11-03T01:00:00Z", "best_over": 2.3,
                         "best_over_book": "betmgm", "best_under": 1.6,
                         "best_under_book": "betmgm"}}
    return c | kw


def test_frozen_rules_keep_their_markets_and_ids():
    assert picks.TRACKED_MARKETS == M.ORIGINAL_MARKETS
    assert picks.TRACK_RULE == "nba-anchored-ev2-mgm@D044"
    assert picks.SIMPLE_RULE == "nba-simple-gap1@D038"


def test_combos_never_enter_the_frozen_rules_and_do_not_crash_them(monkeypatch):
    # a combo card that would pass every other condition of both rules (calib.anchored would
    # refuse it with a KeyError if it were reached)
    cards = [_card(c, player_id=f"c{c}") for c in M.COMBOS]
    P = {"edition_id": "e", "model_version": picks.TRACK_MODEL, "cards": cards}
    monkeypatch.setattr(A, "props", lambda: P)
    r = picks.nba()
    assert r["tracking"] == [] and r["simple_gap"] == []
    # the same card in an original market is taken by both, so the exclusion is what stops it
    P["cards"] = cards + [_card("pts")]
    r = picks.nba()
    assert [x["market"] for x in r["tracking"]] == ["pts"]
    assert [x["market"] for x in picks.simple_gap(P["cards"])] == ["pts"]


# ------------------------------------------------------------------ calibration
@pytest.fixture
def cal_file(tmp_path, monkeypatch):
    """A calibration.json copy that the test may extend; the lru cache is cleared around it."""
    d = json.loads(calib.PATH.read_text())
    path = tmp_path / "calibration.json"

    def write(extra: dict | None = None):
        dd = json.loads(json.dumps(d))
        if extra is not None:
            dd["by_model"]["nba-0.3"]["calibration_combo_proxy"] = extra
        path.write_text(json.dumps(dd))
        calib._coef.cache_clear()

    monkeypatch.setattr(calib, "PATH", path)
    write()
    yield write
    calib._coef.cache_clear()


def test_combos_have_no_real_line_calibration_or_anchored_chance(cal_file):
    for c in M.COMBOS:
        with pytest.raises(KeyError):
            calib.calibrated(c, 0.6, "nba-0.3")
        with pytest.raises(KeyError):
            calib.anchored(c, 0.5, 0.6, "nba-0.3")
        assert calib.combo_calibrated(c, 0.6, "nba-0.3") is None  # no proxy block
    assert calib.combo_proxy("nba-0.3") is None and calib.combo_slopes("nba-0.3") is None
    assert calib.combo_proxy("nba-9.9") is None  # an unknown version: none, no error
    with pytest.raises(KeyError):
        calib.combo_calibrated("pra", 0.6, "nba-0.3")  # PRA keeps its own V4 coefficients
    assert 0 < calib.calibrated("pra", 0.6, "nba-0.3") < 1
    assert set(calib.v4_slopes("nba-0.3")) == set(M.ORIGINAL_MARKETS)


def test_combo_proxy_block_is_used_for_its_own_markets_only(cal_file):
    cal_file({"form": "logistic on logit(p_over)", "logit_clip": 1e-4,
              "coef": {"pr": {"a": 0.1, "b": 0.5}}, "fitted_on": "synthetic",
              "validation": {"note": "test"}})
    z = np.log(0.6 / 0.4)
    assert calib.combo_calibrated("pr", 0.6, "nba-0.3") == pytest.approx(
        1 / (1 + np.exp(-(0.1 + 0.5 * z))))
    assert calib.combo_calibrated("pa", 0.6, "nba-0.3") is None  # not in the block
    assert calib.combo_slopes("nba-0.3") == {"pr": 0.5}
    info = A.market_info("nba-0.3")
    assert info["pr"]["calibration"] == "combo_proxy" and info["pa"]["calibration"] is None
    assert info["pts"]["calibration"] == "calibration_v4"
    cal = A.calibration("nba-0.3")
    assert cal["combo_proxy"]["slope"] == {"pr": 0.5} and "pr" not in cal["slope"]


def test_cards_p_cal_for_combos(cal_file):
    pm = np.exp(-((np.arange(60) - 25.0) ** 2) / 40.0)
    f = {"market": "pr", "dist": Dist.from_pmf(0, pm / pm.sum()).to_json()}
    m = {"line": 24.5}
    assert A._p_cal(f, m, "nba-0.3") is None and A._cal_basis("pr", "nba-0.3") is None
    assert A._p_cal({**f, "market": "pts"}, m, "nba-0.3") is not None
    cal_file({"logit_clip": 1e-4, "coef": {"pr": {"a": 0.0, "b": 1.0}}})
    raw = Dist.from_json(f["dist"]).line(24.5)["over"]
    assert A._p_cal(f, m, "nba-0.3") == pytest.approx(round(raw, 4), abs=1e-4)
    assert A._cal_basis("pr", "nba-0.3") == "combo_proxy"


def test_market_info_for_the_front_end(cal_file):
    info = A.market_info("nba-0.3")
    assert list(info) == list(M.MARKETS)
    assert info["ra"] == {"label": "Reb + Ast", "short": "R+A", "parts": ["reb", "ast"],
                          "opp_stat": "reb", "tested": False, "calibration": None}
    assert info["pra"]["parts"] == ["pts", "reb", "ast"] and info["pra"]["tested"] is True
    assert info["pts"]["parts"] is None and info["pts"]["opp_stat"] == "pts"


# ------------------------------------------------------------------ factors, consensus, S
STORED_REST = {
    "pts": {0: 1.0518332239848525, 1: 1.0126608241069641, 2: 0.9990147904691993,
            3: 0.9843060279972834},
    "reb": {0: 1.0225063822107348, 1: 1.0023318341270944, 2: 0.9946380964925214,
            3: 0.9924342327505044},
    "ast": {0: 1.0589655166233987, 1: 1.0250159338655562, 2: 1.012157809031313,
            3: 0.9711730843620698},
    "fg3m": {0: 1.0534249461898093, 1: 1.0178691776797624, 2: 0.997900210745312,
             3: 0.9852661363011378},
    "pra": {0: 1.0454211756571743, 1: 1.0117392238723444, 2: 0.999692264522367,
            3: 0.9845072658196736}}


def test_combo_factor_tabs_carry_no_test_figures():
    b = factors.breakdown({
        "form": factors.form([30, 31, 32, 33], 25.5),
        "matchup": factors.factor("matchup", factors.NEUTRAL, "h", {}),
        "game_script": factors.factor("game_script", factors.OVER, "h", {}),
        "role": factors.factor("role", factors.OVER, "h", {})})
    u = factors.untested(b)
    for f in factors.FACTORS:
        assert u[f]["tested"] is None and u[f]["reliability"] == factors.UNTESTED
        assert u[f]["lean"] == b[f]["lean"] and u[f]["headline"] == b[f]["headline"]
    assert u["overall"]["untested"] is True and u["overall"]["agree"] == "over"
    assert u["overall"]["text"].endswith(factors.UNTESTED_OVERALL)
    assert "pr" not in factors.TESTED_MARKETS
    assert factors.tests_payload()["untested"]["markets"] == list(M.COMBOS)


def test_consensus_works_unchanged_for_combo_markets():
    rows = []
    for book, line, o, u in (("fanduel", 30.5, 1.9, 1.9), ("draftkings", 30.5, 1.87, 1.95),
                             ("betmgm", 30.5, 1.95, 1.87)):
        for sel, pr in (("over", o), ("under", u)):
            for mk in ("pr", "pts"):
                rows.append({"player_id": "p1", "market": mk, "bookmaker": book, "line": line,
                             "selection": sel, "decimal_price": pr})
    out = books.records(books.consensus_frame(books.pairs(pd.DataFrame(rows))))
    assert out[("p1", "pr")] == out[("p1", "pts")] and out[("p1", "pr")]["books"] == 3


def test_simple_baseline_has_combos_and_keeps_the_stored_factors():
    rest = S.load_rest()
    assert set(rest) == set(S.MARKETS) and set(S.MARKETS) == set(M.MARKETS)
    for c in M.COMBOS:
        assert set(rest[c]) == {0, 1, 2, 3} and all(0.9 < v < 1.1 for v in rest[c].values())
    # the five original markets' factors (fitted 1 Nov 2026, n08), exactly as committed then:
    # the frozen simple-gap rule depends on them
    assert rest == {**rest, **STORED_REST}
    f = pd.DataFrame({"rest": [0, 3]} | {f"avg_{s}": [10.0, 10.0] for s in S.MARKETS}
                     | {f"opp_factor_{s}": [1.0, 1.0] for s in S.MARKETS} | {"min_trend": 1.0})
    out = S.project(f, rest)
    assert list(out.columns) == list(S.MARKETS)
    assert out.pr.tolist() == pytest.approx([10 * rest["pr"][0], 10 * rest["pr"][3]])
    assert S.project(f, {k: v for k, v in rest.items() if k not in M.COMBOS}).columns.tolist() \
        == list(M.ORIGINAL_MARKETS)


# ------------------------------------------------------------------ editions
def test_edition_rows_cover_every_market_in_order():
    x = pd.DataFrame({"game_id": ["g"], "player_id": ["p"], "name": ["N"], "team": ["T"],
                      "opp": ["O"], "pos": ["G"], "p_play": [0.9], "fantasy": [np.nan],
                      "status": [None], "detail": [None], "as_of_date": [None],
                      "injury_source": [None], "min_mu": [30.0], "min_mu_base": [30.0],
                      "has_line": [True]})
    pm = {mk: np.full((1, M.MAX_K[mk] + 1), 1.0 / (M.MAX_K[mk] + 1)) for mk in M.MARKETS}
    rows = live.forecast_rows(x, {"N1": pm})
    assert [r["market"] for r in rows] == list(M.MARKETS)
    assert all(r["line_known"] is True for r in rows)  # per player, as before


def test_posted_lines_ignore_combo_quotes(monkeypatch):
    from npl.sources import odds_api

    x = pd.DataFrame({"game_id": ["g1", "g1"], "player_id": ["p1", "p2"]})
    q = pd.DataFrame({"game_id": ["g1", "g1"], "player_id": ["p1", "p2"],
                      "market": ["pts", "pr"], "observed_at": ["2026-11-04T10:00:00Z"] * 2})
    monkeypatch.setattr(odds_api, "quotes_frame", lambda con: q)
    has, _ = live.posted_lines(None, x)
    assert has.tolist() == [True, False]  # a combo-only line moves no market's numbers


# ------------------------------------------------------------------ the offline copy
def _snap_cards():
    def card(market, values, **kw):
        return {"game_id": "g", "player_id": "p", "name": "N", "team": "T", "opponent": "O",
                "position": "G", "market": market, "p_play": 0.9, "minutes_expected": 30.0,
                "mean": 1.0, "median": 1, "q10": 0, "q90": 2, "dist": {"lo": 0, "counts": [1]},
                "injury": None, "minutes_lineup_shift": 0.0, "line_known": True,
                "simple": 2.0, "market_label": M.MARKET_LABELS[market], "model": "N1",
                "tip_utc": "t", "tip_london": "l", "is_home": True, "baselines": {"A": 1.0},
                "log": {"values": values, "opp": ["X", "Y"], "date": ["a", "b"],
                        "season": ["2027", "2027"], "current_season": "2027"},
                "opp_factor": {"factor": 1.0, "rank": 3}, "opp_pace": {"value": 99, "rank": 1},
                "market_line": None, "p_cal_market": None, "teammates_out": [],
                "wowy": [{"name": "M", "with": values[:1], "without": values[1:],
                          "min_with": 30.0, "min_without": 31.0, "seasons": [2027]}],
                "factors": None, **kw}
    cards = [card("pts", [20, 25]), card("reb", [5, 7]), card("ast", [3, 1])]
    for c in M.COMBOS:
        a, b = M.COMBO_PARTS[c]
        va = next(x for x in cards if x["market"] == a)["log"]["values"]
        vb = next(x for x in cards if x["market"] == b)["log"]["values"]
        s = [i + j for i, j in zip(va, vb, strict=True)]
        cards.append(card(c, s, untested=True, p_cal_basis=None,
                          opp_factor={"factor": 1.0, "rank": 3} if c != "ra" else
                          {"factor": 0.9, "rank": 9}))
    return cards


def test_slim_combo_cards_leave_out_only_what_can_be_rebuilt():
    cards = _snap_cards()
    info = A.market_info("nba-0.3")
    slim = snapshot._slim_combos(cards, info)
    assert slim[:3] == cards[:3]
    pr, ra = slim[3], slim[5]
    assert pr["slim"] == 1 and "log" not in pr and "wowy" not in pr and "name" not in pr
    assert "market_label" not in pr and "untested" not in pr and "market_line" not in pr
    assert {"dist", "mean", "simple", "baselines", "market", "player_id"} <= set(pr)
    assert ra["opp_factor"] == {"factor": 0.9, "rank": 9}  # differs from rebounds': kept
    # a log that is not the sum of its parts is kept as it is
    bad = [dict(c) for c in cards]
    bad[3] = {**bad[3], "log": {**bad[3]["log"], "values": [0, 0]}}
    assert snapshot._slim_combos(bad, info)[3]["log"]["values"] == [0, 0]
    # without its parts' cards a combo card is left whole
    assert snapshot._slim_combos(cards[3:], info) == cards[3:]
    # combo cards of players without any line are left out of the copy
    kept = snapshot._combos_kept([*cards, {**cards[3], "player_id": "q", "line_known": False}])
    assert len(kept) == len(cards)


@pytest.mark.skipif(NODE is None, reason="node is not installed")
def test_the_page_rebuilds_slimmed_combo_cards_exactly(tmp_path):
    cards = _snap_cards()
    info = A.market_info("nba-0.3")
    props = {"cards": snapshot._slim_combos(cards, info), "market_info": info,
             "factor_tests": factors.tests_payload()}
    js = ("var window = {NPL_SNAPSHOT: {data: {'/api/nba/props': " + json.dumps(props) + "}}};\n"
          + snapshot.REHYDRATE.replace("<script>", "").replace("</script>", "")
          + "\nprocess.stdout.write(JSON.stringify("
            "window.NPL_SNAPSHOT.data['/api/nba/props'].cards));")
    f = tmp_path / "r.js"
    f.write_text(js)
    got = json.loads(subprocess.run([NODE, str(f)], capture_output=True, text=True,
                                    check=True).stdout)
    assert got == json.loads(json.dumps(cards))
