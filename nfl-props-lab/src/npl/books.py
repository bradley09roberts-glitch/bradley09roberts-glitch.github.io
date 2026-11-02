"""Which US bookmakers' prices count as takeable in research results (audit, D-044).

The provider's "us" region mixes state-regulated sportsbooks with offshore and sweepstakes
books. Offshore books' margin-free prices are informative (BetOnline's are among the most
accurate), so they stay in the consensus, but a "best price" is only taken from a regulated
book: offshore quotes, ladder lines and price pairs that imply an arbitrage inflated earlier
research returns (audit 2026-11). None of these books is open to UK customers.
"""

from __future__ import annotations

OFFSHORE = frozenset({"betonlineag", "betus", "bovada", "lowvig", "mybookieag", "betanysports",
                      "fliff", "rebet"})


def regulated(book: str) -> bool:
    return book not in OFFSHORE
