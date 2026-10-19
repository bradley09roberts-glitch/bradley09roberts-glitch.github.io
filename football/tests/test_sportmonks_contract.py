"""Contract test against REAL recorded Sportmonks responses (see tests/recorded/sportmonks)."""

import json
from pathlib import Path

import pytest

from footy.providers.sportmonks import LEAGUE_IDS, MARKETS, parse_fixture, parse_odd

RECORDED = sorted((Path(__file__).parent / "recorded" / "sportmonks").glob("*.json"))
pytestmark = pytest.mark.skipif(
    not RECORDED, reason="no recorded Sportmonks responses yet (needs the owner's key, D-010)"
)


@pytest.mark.parametrize("path", RECORDED, ids=lambda p: p.name)
def test_recorded_response_parses(path):
    body = json.loads(path.read_text())
    assert "data" in body
    items = body["data"]
    assert items, "recorded response is empty"
    if path.name.startswith("fixtures"):
        codes = {v: k for k, v in LEAGUE_IDS.items()} | {501: "SC0", 271: "DK1"}  # free plan
        parsed = [parse_fixture(i, codes) for i in items]
        assert any(fx for fx, _ in parsed), [reason for _, reason in parsed]
    else:
        parsed = [parse_odd(i) for i in items if i.get("market_id") in MARKETS]
        reasons = {r for q, r in parsed if q is None}
        assert not any(r and r.startswith("unknown_label") for r in reasons), reasons
        assert any(q for q, _ in parsed)
