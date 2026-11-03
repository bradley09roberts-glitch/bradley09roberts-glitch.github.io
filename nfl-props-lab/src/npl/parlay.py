"""Combine prop legs at their margin-free chances (D-047). Research arithmetic only: nothing
here is a bet, and a fair price is not a promise of anything.

fair_combo(legs, price) multiplies the legs' chances as if the legs were independent and gives
the combined chance, its fair decimal odds (1 / chance) and, at an offered decimal price, the
expected value per unit staked (chance x price - 1, as everywhere in the project) and the
chance the price needs (1 / price).

A leg's chance is the margin-free market chance of the side taken (an NBA card's
market_line.fair_over or fair_under: the bookmakers' margin removed, averaged over books).
Legs from the same game are flagged: their outcomes move together (pace, minutes, the score),
so the product is not their real combined chance, and the fair odds and EV built on it are not
reliable either. Legs of the same player are flagged as well, and legs priced by fewer than 3
books (a thin market, as the cards' lean rule). Bookmakers' own parlay prices carry far bigger
margins than single bets (the audit found holds of about 17-31%).
"""

from __future__ import annotations

import math
from itertools import combinations

SAME_GAME = "same game, chances not independent"
SAME_PLAYER = "same player, chances not independent"
THIN = "fewer than 3 books, fair chance less reliable"
MIN_BOOKS = 3


def _groups(legs: list[dict], key: str) -> list[list[int]]:
    """Leg numbers (0-based) sharing a value of `key`, groups of two or more, in leg order."""
    seen: dict = {}
    for i, leg in enumerate(legs):
        v = leg.get(key)
        if v is not None:
            seen.setdefault(v, []).append(i)
    return [g for g in seen.values() if len(g) > 1]


def _pairs(groups: list[list[int]]) -> list[tuple[int, int]]:
    return sorted(p for g in groups for p in combinations(g, 2))


def fair_combo(legs, price: float | None = None) -> dict:
    """Combined margin-free chance of `legs`, assuming independence, with flags.

    legs: dicts with `chance` (the margin-free chance of the side taken, strictly between 0
    and 1) and, for the flags, `game_id`, `player_id` and optionally `books` (books behind the
    fair chance); other keys (market, side, line, label) are ignored. price: an offered decimal
    price for the whole combination (above 1), or None.

    Returns legs (count), chance, fair_odds, price, ev and breakeven (None without a price),
    independent (no two legs from one game), same_game and same_player (groups of leg numbers,
    0-based), thin (leg numbers) and warnings (one line each, legs numbered from 1)."""
    legs = list(legs)
    if not legs:
        raise ValueError("no legs")
    chances = []
    for i, leg in enumerate(legs, 1):
        p = leg.get("chance")
        if p is None or isinstance(p, bool) or not 0 < float(p) < 1:
            raise ValueError(f"leg {i}: chance must be strictly between 0 and 1, got {p!r}")
        chances.append(float(p))
    if price is not None and not float(price) > 1:
        raise ValueError(f"price must be a decimal price above 1, got {price!r}")
    chance = math.prod(chances)
    games, players = _groups(legs, "game_id"), _groups(legs, "player_id")
    thin = [i for i, leg in enumerate(legs)
            if leg.get("books") is not None and leg["books"] < MIN_BOOKS]
    warnings = [f"Legs {a + 1} and {b + 1}: {SAME_GAME}" for a, b in _pairs(games)]
    warnings += [f"Legs {a + 1} and {b + 1}: {SAME_PLAYER}" for a, b in _pairs(players)]
    warnings += [f"Leg {i + 1}: {THIN}" for i in thin]
    out = {"legs": len(legs), "chance": chance, "fair_odds": 1 / chance, "price": None,
           "ev": None, "breakeven": None, "independent": not games, "same_game": games,
           "same_player": players, "thin": thin, "warnings": warnings}
    if price is not None:
        price = float(price)
        out |= {"price": price, "ev": chance * price - 1, "breakeven": 1 / price}
    return out
