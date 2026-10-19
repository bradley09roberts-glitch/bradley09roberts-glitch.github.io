"""Provider-neutral types. Market keys and selections match `fd_odds` and `odds_snapshots`."""

from dataclasses import dataclass, field
from datetime import date, datetime
from typing import Protocol

# Half-goal lines only (brief: 0.5 to 4.5); integer and quarter lines are out of scope.
TOTAL_LINES = (0.5, 1.5, 2.5, 3.5, 4.5)


@dataclass(frozen=True)
class RawPage:
    """One API response as received, for the append-only raw store. Never holds the key."""

    logical_key: str
    url: str
    content: bytes


@dataclass(frozen=True)
class ProviderFixture:
    provider_fixture_id: str
    league_code: str  # football-data division code
    kickoff_utc: datetime
    home_name: str
    away_name: str
    status: str


@dataclass(frozen=True)
class ProviderQuote:
    bookmaker: str
    market_key: str  # 1X2 | OU_1.5 | OU_2.5 | ... | BTTS
    line: float | None
    selection: str  # H D A | O U | Y N
    price: float
    quote_time_utc: datetime | None  # the provider's own "last confirmed" time
    page: int = 0  # index into FetchedOdds.pages of the response this quote came from


@dataclass
class FetchedFixtures:
    fixtures: list[ProviderFixture]
    pages: list[RawPage]
    skipped: dict[str, int] = field(default_factory=dict)  # reason -> count


@dataclass
class FetchedOdds:
    quotes: list[ProviderQuote]
    pages: list[RawPage]
    skipped: dict[str, int] = field(default_factory=dict)


class LiveProvider(Protocol):
    name: str

    def upcoming_fixtures(self, start: date, end: date) -> FetchedFixtures: ...

    def prematch_odds(self, provider_fixture_id: str) -> FetchedOdds: ...
