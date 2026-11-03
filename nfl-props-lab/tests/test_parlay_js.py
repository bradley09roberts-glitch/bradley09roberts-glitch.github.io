"""The app's parlay maths (web/app.js, between the ">>> parlay maths" and "<<< parlay maths"
markers) run with node and set against npl.parlay.fair_combo on the same legs: the same flags
and warnings, the same chance, fair odds, expected value and break-even (the same arithmetic in
the same order, so the same numbers), and the same legs and prices refused. The odds reader
(parseOdds) has no Python twin and is checked against fixed answers. Skipped without node. Every
chance, id and price below is made up."""

import json
import random
import shutil
import subprocess

import pytest

from npl import parlay as P
from npl.config import PROJECT_ROOT

NODE = shutil.which("node")
pytestmark = pytest.mark.skipif(NODE is None, reason="node is not installed")

APP = PROJECT_ROOT / "web" / "app.js"
START, END = "// >>> parlay maths", "// <<< parlay maths"

# reads {code, combos: [[legs, price], ...], odds: [text, ...]} on stdin; runs the app's block in
# a fresh context and writes each result ({ok} or {error}) and each odds reading (NaN as "NaN")
RUNNER = r"""
const vm = require("node:vm");
let buf = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (d) => { buf += d; });
process.stdin.on("end", () => {
  const { code, combos, odds } = JSON.parse(buf);
  const ctx = vm.createContext({});
  vm.runInContext(code + "\n;globalThis.__pc = { fairCombo, parseOdds };", ctx);
  const { fairCombo, parseOdds } = ctx.__pc;
  const run = ([legs, price]) => { try { return { ok: fairCombo(legs, price) }; }
    catch (e) { return { error: String(e && e.message) }; } };
  const read = (s) => { const v = parseOdds(s); return v === null ? null : Number.isNaN(v) ? "NaN" : v; };
  process.stdout.write(JSON.stringify({ combos: combos.map(run), odds: odds.map(read) }));
});
"""


def _block() -> str:
    src = APP.read_text(encoding="utf-8")
    a, b = src.find(START), src.find(END)
    assert 0 <= a < b, "the parlay maths markers are missing from web/app.js"
    return src[a:b]


def _node(combos: list, odds: list) -> dict:
    r = subprocess.run([NODE, "-e", RUNNER], capture_output=True, text=True, timeout=120,
                       input=json.dumps({"code": _block(), "combos": combos, "odds": odds}))
    assert r.returncode == 0, r.stderr
    return json.loads(r.stdout)


def _python(legs, price) -> dict:
    try:
        return {"ok": P.fair_combo(legs, price)}
    except ValueError as err:
        return {"error": str(err)}


def _leg(chance, game="g1", player="p1", **kw):
    return {"chance": chance, "game_id": game, "player_id": player, **kw}


def _cases() -> list:
    """Hand-made cases (those of tests/test_parlay.py and edge cases) and 400 random ones."""
    fixed = [
        ([_leg(0.5, "g1", "a"), _leg(0.6, "g2", "b"), _leg(0.55, "g3", "c")], None),
        ([_leg(0.516, "g1", "a"), _leg(0.48, "g2", "b")], 4.2),
        ([_leg(0.516, "g1", "a"), _leg(0.48, "g2", "b")], 3.5),
        ([_leg(0.516, "g1", "a"), _leg(0.48, "g2", "b")], 1 / (0.516 * 0.48)),
        ([_leg(0.25)], 4.0),
        ([_leg(0.5, "g1", "a"), _leg(0.5, "g1", "b"), _leg(0.5, "g2", "c"), _leg(0.5, "g1", "d")],
         None),
        ([_leg(0.55, "g1", "jg", market="pra", side="under"),
          _leg(0.5, "g1", "jg", market="pts", side="under"), _leg(0.6, "g2", "sc")], 3.5),
        ([_leg(0.5, "g1", "a", books=2), _leg(0.5, "g2", "b", books=5), _leg(0.5, "g3", "c")],
         None),
        ([_leg(0.5, "g1", "a", books=0), _leg(0.5, "g2", "b", books=None)], 2.5),
        ([{"chance": 0.5}, {"chance": 0.5}], None),
        ([{"chance": 0.5, "game_id": None, "player_id": None}] * 3, None),
        ([_leg(0.5, f"g{i}", f"p{i}") for i in range(10)], None),
        ([_leg("0.5", "g1", "a"), _leg(0.4, "g2", "b")], "4.5"),  # numbers given as text
        ([_leg(0.9999, "g1", "a"), _leg(0.0001, "g2", "b")], 1.01),
        # refused, by both
        ([], None), ([_leg(0.0)], None), ([_leg(1.0)], None), ([_leg(None)], None),
        ([_leg(1.2)], None), ([_leg(-0.3)], None), ([_leg(True)], None), ([_leg(False)], None),
        ([{"game_id": "g1"}], None), ([_leg("abc")], None), ([_leg(0.5), _leg(1.5)], None),
        ([_leg(0.5)], 1.0), ([_leg(0.5)], 0.5), ([_leg(0.5)], -2.0), ([_leg(0.5)], 0.0),
        ([_leg(0.5)], True),
    ]
    rnd = random.Random(20261103)
    cases = []
    for _ in range(400):
        legs = []
        for _ in range(rnd.randint(1, 10)):
            leg = {"chance": round(0.02 + rnd.random() * 0.96, 4),
                   "game_id": f"g{rnd.randrange(4)}", "player_id": f"p{rnd.randrange(6)}"}
            u = rnd.random()
            if u < 0.2:
                leg["books"] = rnd.randrange(3)  # thin
            elif u < 0.9:
                leg["books"] = 3 + rnd.randrange(6)
            legs.append(leg)  # else no books at all
        price = round(1.05 + rnd.random() * 60, 2) if rnd.random() < 0.7 else None
        cases.append((legs, price))
    return fixed + cases


def test_the_apps_parlay_maths_match_npl_parlay():
    cases = _cases()
    js = _node([list(c) for c in cases], [])["combos"]
    assert len(js) == len(cases)
    refused = 0
    for k, ((legs, price), j) in enumerate(zip(cases, js, strict=True)):
        p = _python(legs, price)
        assert ("error" in p) == ("error" in j), (k, p, j)
        if "error" in p:
            refused += 1
            continue
        a, b = p["ok"], j["ok"]
        assert set(a) == set(b), k
        for key in ("legs", "independent", "same_game", "same_player", "thin", "warnings"):
            assert a[key] == b[key], (k, key, a[key], b[key])
        for key in ("chance", "fair_odds", "price", "ev", "breakeven"):
            assert a[key] == b[key], (k, key, a[key], b[key])  # the same doubles
    assert refused == 16  # every refusal case above, and no random one


def test_both_refuse_bad_legs_in_the_same_words():
    cases = [([], None), ([_leg(0.5), _leg(1.2)], None), ([_leg(0.5)], 1.0)]
    js = _node([list(c) for c in cases], [])["combos"]
    for (legs, price), j in zip(cases, js, strict=True):
        p = _python(legs, price)
        # the leading words agree; Python writes the bad value as Python does (1.2, 1.0)
        assert p["error"].split(", got")[0] == j["error"].split(", got")[0]
    assert js[0]["error"] == "no legs" and js[1]["error"].startswith("leg 2: chance must be")


def test_the_odds_reader_turns_what_people_type_into_a_decimal_price():
    good = {"4.5": 4.5, "4,5": 4.5, "+350": 4.5, "-118": 1 + 100 / 118, "−120": 1 + 100 / 120,
            "–150": 1 + 100 / 150, "7/2": 4.5, "1/1": 2.0, "evens": 2.0, "EVS": 2.0,
            " 3.75 ": 3.75, "100": 100.0, "+100": 2.0, "-100": 2.0, "11/10": 2.1}
    empty = ["", "   "]
    bad = ["1", "1.0", "0.5", "abc", "+99", "-50", "0/2", "2/0", "4.5.1", "+", "--200", "1/0",
           "x7/2"]
    got = _node([], [*good, *empty, *bad])["odds"]
    n = len(good)
    assert got[:n] == list(good.values())  # the same doubles as the arithmetic here
    assert got[n:n + len(empty)] == [None] * len(empty)  # nothing typed yet
    assert got[n + len(empty):] == ["NaN"] * len(bad)  # cannot be read, or not above 1
