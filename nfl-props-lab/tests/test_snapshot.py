"""The offline snapshot page: its thresholds reach both sports, the NBA extras are trimmed for it
only, the page is written under data/ by default (it embeds US prices) and a page over the
host's size limit is refused. The app's data calls are replaced by tiny stand-ins."""

import argparse
import copy

import pytest

from npl import appdata, cli, config, headshots, snapshot


@pytest.fixture
def tiny(monkeypatch):
    """build() over stand-in payloads; records what _add_nba is given."""
    for name, value in (("data_status", {}), ("editions_index", []), ("slate", {"games": []}),
                        ("backtests", []), ("record", {}), ("defense", {})):
        monkeypatch.setattr(appdata, name, lambda *a, _v=value, **k: _v)
    monkeypatch.setattr(appdata, "props", lambda model=None: {"edition_id": "e", "cards": []})
    monkeypatch.setattr(appdata, "visuals", lambda pids: {"photos": {}})
    monkeypatch.setattr(headshots, "fetch", lambda sport, pids: {})
    monkeypatch.setattr(headshots, "fetch_logos", lambda sport: {})
    seen = {}
    monkeypatch.setattr(snapshot, "_add_nba", lambda data, min_p_play: seen.update(nba=min_p_play))
    return seen


def test_build_passes_the_nba_threshold_and_reports_the_size(tmp_path, tiny):
    out = tmp_path / "new" / "page.html"
    r = snapshot.build(out, min_p_play=0.9, nba_min_p_play=0.7)
    assert tiny["nba"] == 0.7  # no longer the old hidden 0.3
    assert r["bytes"] == out.stat().st_size and r["limit_bytes"] == 16_000_000
    assert (r["min_p_play"], r["nba_min_p_play"]) == (0.9, 0.7)
    snapshot.build(tmp_path / "default.html")
    assert tiny["nba"] == 0.5  # build()'s own NBA default


def test_the_page_restores_the_shared_lines_before_the_app_runs(tmp_path, tiny):
    out = tmp_path / "page.html"
    snapshot.build(out)
    html = out.read_text(encoding="utf-8")
    data, back, app = (html.index("<script>window.NPL_SNAPSHOT = "), html.index(snapshot.REHYDRATE),
                       html.index('<script type="module">'))
    assert data < back < app  # the data, then the factors' tested lines put back, then the app


def test_an_oversize_page_is_refused_and_not_written(tmp_path, tiny, monkeypatch):
    small = snapshot.build(tmp_path / "ok.html")["bytes"]
    monkeypatch.setattr(snapshot, "MAX_BYTES", small - 1)
    out = tmp_path / "big.html"
    with pytest.raises(snapshot.SnapshotTooLarge, match=f"{small:,} bytes, over the"):
        snapshot.build(out)
    assert not out.exists()
    assert snapshot.build(out, allow_oversize=True)["bytes"] == small and out.exists()


def test_cli_defaults_thresholds_and_refusal(tmp_path, monkeypatch, capsys):
    a = cli.build_parser().parse_args(["snapshot"])
    assert a.out == str(config.DATA_HOME / "snapshots" / "nfl-props-lab.html")
    assert (a.min_p_play, a.nba_min_p_play, a.allow_oversize) == (0.9, 0.6, False)
    seen = {}

    def build(out, **kw):
        seen.update(kw, out=out)
        raise snapshot.SnapshotTooLarge("snapshot would be 17,000,000 bytes")

    monkeypatch.setattr(snapshot, "build", build)
    rc = cli.cmd_snapshot(argparse.Namespace(out=str(tmp_path / "x.html"), fragment=False,
                                             min_p_play=0.8, nba_min_p_play=0.7,
                                             allow_oversize=False))
    assert rc == 4 and "REFUSED: snapshot would be 17,000,000 bytes" in capsys.readouterr().out
    assert (seen["min_p_play"], seen["nba_min_p_play"], seen["standalone"]) == (0.8, 0.7, True)


def _row(i, line=None, diff=None):
    return {"d": f"2026-01-{i + 1:02d}", "s": 2026, "t": "R", "tm": "PHX", "h": 1, "min": 30,
            "v": [20, 5, 4, 2, 29], "line": line, "diff": diff}


def test_snapshot_trims_the_grid_against_tonights_opponent():
    lined = [18.5, None, None, None, 27.5]
    grid = {"opp": "GS", "tonight": [17.5, None, None, None, 26.5], "n": 8,
            "games": [_row(0, lined, [2.5, None, None, None, 2.5]), _row(1)]
            + [_row(i, [None] * 5, [None] * 5) for i in range(2, 8)]}
    ex = {"players": {"p": {"volume": {}, "script": {}, "vs_opp": grid}}, "defence": {},
          "pos_v_opp": {}, "game_lines": {}}
    before = copy.deepcopy(ex)
    cut = snapshot._nba_extras(ex, [{"player_id": "p", "opponent": "GS", "team": "PHX",
                                     "position": "G", "game_id": "g"}])
    g = cut["players"]["p"]["vs_opp"]
    assert len(g["games"]) == snapshot.SNAPSHOT_GRID == 5 and g["n"] == 8  # n still counts all
    assert g["games"][0]["line"] == lined and "diff" in g["games"][0]
    assert all("line" not in r and "diff" not in r for r in g["games"][1:])  # no value: dropped
    assert ex == before  # the cached props block is not changed


def test_nba_copy_records_its_threshold_pages_and_grid_cap(monkeypatch):
    """The copy's props payload says which play probability its cards needed, the data lists the
    players with a page in it, and the grid's note states the cap used; the slate (with its
    injury counts for every player) and the cached props block are left as they are."""
    from npl.nba import appdata as nba
    from npl.nba import extras as X

    def card(pid, p_play):
        return {"player_id": pid, "p_play": p_play, "game_id": "g1", "team": "DET",
                "opponent": "PHI", "position": "G", "log": {"values": list(range(30))}}

    slate = {"edition_id": "e", "games": [{"game_id": "g1", "home": "DET", "away": "PHI",
                                           "tip_utc": "2099-01-01T00:00:00Z",
                                           "injured": {"DET": {"out": 3, "questionable": 1},
                                                       "PHI": {"out": 0, "questionable": 0}}}]}
    cached = {"edition_id": "e", "cards": [card("a", 0.9), card("b", 0.3), card("c", 0.95)],
              "extras": {"players": {}, "notes": dict(X.NOTES)}}
    before = copy.deepcopy(cached)
    for name, fn in (("slate", lambda: slate), ("props", lambda: cached),
                     ("backtests", lambda: []), ("game", lambda gid: {"game": {}}),
                     ("matchups", lambda: {}), ("visuals", lambda pids: {}),
                     ("shot_profile", lambda pid: {"error": "none"}),
                     ("player", lambda pid: {"error": "unknown player"} if pid == "c" else
                      {"player_id": pid, "game_log": [], "forecasts": [], "extras": {}})):
        monkeypatch.setattr(nba, name, fn)
    monkeypatch.setattr(headshots, "fetch", lambda sport, pids: {})
    monkeypatch.setattr(headshots, "fetch_logos", lambda sport: {})
    data = {}
    snapshot._add_nba(data, min_p_play=0.6)
    props = data["/api/nba/props"]
    assert props["snapshot_min_p_play"] == 0.6
    assert [c["player_id"] for c in props["cards"]] == ["a", "c"]
    assert data["snapshot_player_pages"] == ["a"]  # c's page could not be built
    assert "/api/nba/player/a" in data and "/api/nba/player/c" not in data
    assert "(up to 10; 5 in the offline copy)" in props["extras"]["notes"]["vs_opp"]
    assert "(up to 10)." in X.NOTES["vs_opp"]  # the server keeps its own cap
    assert data["/api/nba/slate"]["games"][0]["injured"]["DET"] == {"out": 3, "questionable": 1}
    assert cached == before  # the cached payload is not trimmed
