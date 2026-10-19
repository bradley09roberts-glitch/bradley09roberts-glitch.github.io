# Historical data: football-data.co.uk, odds history and xG

_Researched 2026-10-19 from pages fetched that day. Quotes are verbatim, including the
sources' own spelling._

## football-data.co.uk

**Terms of use (the most important finding).** The data page says:

> "it's all FREE , however its use is intended for private individuals only, NOT commerical
> or data training products using automated bots/scrapers/AI."
> (https://www.football-data.co.uk/data.php)

There is no separate licence page; notes.txt says nothing about use or attribution. Whether an
automated importer, built by an AI agent and feeding a website, fits "private individuals
only" is the owner's call (see DECISIONS.md D-013). Until then the importer is designed to
read files from a local drop folder, with downloading switched off by default.

**Odds collection times (the backtest decision time).**

> "Please note that the odds are collected for the downloadable weekend fixtures on Fridays
> afternoons generally not later than 17:00 British Standard Time. Odds for midweek fixtures
> are collected Tuesdays not later than 13:00 British Standard Time."
> (https://www.football-data.co.uk/matches.php)

> "Since 2019/20 I have collected two sets of odds. The first set is collected after market
> opening at times specified on my fixtures page. The second set are the closing odds ('C'
> included the data column headings)." (data.php)

**Column key** (https://football-data.co.uk/notes.txt): pre-closing columns as listed;
closing columns add "C" after the bookmaker, Max or Avg (e.g. `B365CH`). Markets are 1X2,
O/U **2.5 only**, and Asian handicap. Current 1X2 books include B365, BFD, BMGM, BV, BW, CL,
IW, LB, PP, PS (Pinnacle), SK, WH, 1XB, plus Max, Avg and BFE (Betfair Exchange).

**Pinnacle is unreliable from 23 July 2025.**

> "Since 23/07/2025 Pinnacle's public API for odds delivery has become unreliable meaning
> their odds are systematically out of date relative to odds for other bookmakers, including
> both the pre-closing and closing odds. Consequently they should be used with caution when
> undertaking any betting analyses, and are no longer being included for the calculation of
> market average and maximum odds." (data.php)

**Premier League header check** (column names only, 2026-10-19):

| Season | Columns | Pinnacle columns | Betfair Exchange columns | xG columns |
| --- | --- | --- | --- | --- |
| 2023/24 | 106 | 10 | 0 | 0 |
| 2025/26 | 132 | 10 | 14 | 0 |
| 2026/27 | 114 | 0 | 14 | 2 (`HxG`, `AxG`, source not documented) |

2024/25 and the other leagues are left to the M1 coverage report.

## Historical prices for O/U 1.5, 3.5 and BTTS

No free, licence-clean, verified source was found for 2023/24–2025/26. Options for the owner:

| Source | What it offers | Catch |
| --- | --- | --- |
| **The Odds API** (paid) | Historical snapshots from 6 Jun 2020 (10-min, 5-min from Sep 2022); `btts` and `alternate_totals` exist as market keys; "Historical data for additional markets … are available after 2023-05-03" [1]. UK region has 21 books incl. Betfair Exchange; EU region has Pinnacle [2]. Terms permit "Storing our data and retaining it indefinitely" and display on a website [3] | "Additional markets are currently limited to US sports and selected bookmakers" [4]: soccer BTTS / alternate totals history is **unverified**. Paid plans only for history; 10 credits per market per region per event snapshot, so three seasons of two markets would be roughly 100k credits per snapshot time (own estimate). Bet365 not listed. One paid test query would settle it |
| **Betfair Exchange historical data** | BASIC tier is free: "1 minute intervals", "last traded price", "no volume", data "since 2016", "nearly all markets offered on the Exchange" [5][6] | Needs the owner's Betfair account; the site is geo-blocked from here, so soccer market types, licence and commission weren't checked. Last traded price isn't a guaranteed fillable price; commission must be deducted. Would be labelled as exchange-price ROI, never bookmaker ROI |
| OddsPapi | Claims a free timestamped `/historical-odds` endpoint [7] | Archive start undefined; no resale, storage terms not stated [8] |
| TheStatsAPI (football-data's partner) | Lists BTTS, O/U and "Historical odds" incl. Bet365, Pinnacle; $50–379/month [9] | Lines, start date and timestamps not stated |
| Sportmonks Premium | Timestamped price history | Only "until 7 days after the match has started" [10] |
| OddsPortal | Not usable | Terms forbid "embedding, aggregating, scraping or recreating" content [11] |

## xG

- **FBref: confirmed removed.** Sports Reference, 20 Jan 2026: "the provider of our advanced
  soccer data sent us a letter terminating our access to their data feeds … The advanced data
  has been deleted and cannot be provided." [12]
- **Understat: not usable.** robots.txt is `User-agent: * Disallow: /`; no terms page found [13].
- StatsBomb open data covers only scattered big-five seasons [14]; FotMob forbids robots [15].
- football-data's new 2026/27 `HxG`/`AxG` columns are undocumented and don't cover the backtest
  seasons.
- **Conclusion:** the backtested model uses shots and shots on target, as the brief allows.

## Sources

1. https://the-odds-api.com/liveapi/guides/v4/
2. https://the-odds-api.com/sports-odds-data/bookmaker-apis.html
3. https://the-odds-api.com/terms-and-conditions.html
4. https://the-odds-api.com/sports-odds-data/betting-markets.html
5. https://betfair-datascientists.github.io/data/usingHistoricDataSite/
6. https://github.com/betfair/historic-data-workbook
7. https://oddspapi.io/blog/?p=2824
8. https://oddspapi.io/en/legal/terms
9. https://www.thestatsapi.com/odds-api/btts
10. https://docs.sportmonks.com/v3/endpoints-and-entities/endpoints/premium-odds-feed/premium-pre-match-odds/get-all-historical-odds
11. https://www.oddsportal.com/terms/
12. https://www.sports-reference.com/blog/category/fbref/
13. https://understat.com/robots.txt
14. https://github.com/statsbomb/open-data
15. https://www.fotmob.com (footer terms)
