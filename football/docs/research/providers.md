# Live data providers: Sportmonks vs API-Football

_Researched 2026-10-19 from official pages fetched that day. Prices are as published on that
date, excluding VAT, and must be re-checked before buying; nothing in code depends on them._

**Headline:** Sportmonks' documentation and pricing were readable and are summarised below.
**API-Football could not be verified from this environment**: every page on api-football.com,
api-sports.io and dashboard.api-football.com returned HTTP 403 (a Cloudflare challenge) to
curl, the web-fetch tool and a real headless Chromium. Its column below is search-engine
summaries of official pages, kept only as leads.

## Comparison

| Need | Sportmonks (verified) | API-Football (UNVERIFIED) |
| --- | --- | --- |
| Plans | Starter €29/month (any 5 leagues), Growth €99 (30), Pro €249 (120), Enterprise custom; yearly about 20% off [1] | Free (100 requests/day), paid tiers about $19–39/month; two official pages disagree on prices [A] |
| Our five leagues | Starter covers them (pick 5). Second tiers need Growth, or extra leagues "starting at €4/month" [1]. We don't need second tiers from the provider (football-data supplies them) | Not verified |
| Odds cost | Standard Odds add-on "€14-€69 depending on your base plan" (the Starter figure isn't stated) [3]. Premium Odds: Growth €129 Lite / €199 Full, Pro €449 / €699; "Starter: Not available" [4] | Reportedly included in all plans [A] |
| Free tier | "3,000 API calls / entity / hour", covers Danish Superliga and Scottish Premiership, no card, no expiry [2] | 100 requests/day, 10/minute [A] |
| Rate limits | Per entity per hour: 2,000 / 2,500 / 3,000 / 5,000 by plan; resets an hour after the first request; 429 on excess; `rate_limit` object and `x-ratelimit-*` headers on every response [1][2] | Per day plus per minute [A] |
| Results, half-time, stats | `scores` include (1ST_HALF, 2ND_HALF); statistic types for shots (42), shots on target (86), corners (34), yellow (84) and red (83) cards [10][11] | Shots, corners, cards [A] |
| xG | Paid add-on; coverage list includes all ten target divisions, with the warning that inclusion "does not guarantee data back to the 2024/2025 season" [5] | No xG found [A] |
| Lineups | `lineups` include, checked "every 5 minutes in the final hour before kickoff" [6] | About 20–40 min before kickoff [A] |
| Injuries, suspensions | `sidelined` include, no extra cost; coaches endpoints [12][13] | `/injuries`, updated every 4 hours [A] |
| History (fixtures, stats) | Three most recent seasons per league by default; older seasons are a one-time add-on (€29 / €99 / €249) [7] | Not verified |
| Odds markets | 1 Fulltime Result, 80 Goals Over/Under (line in `total`), 81 Alternative Total Goals (bet365), 14 Both Teams To Score, all pre-match on Standard [8]. Lines offered per bookmaker aren't listed | Not verified |
| Bookmakers | "50+ bookmakers" [1]; docs examples include bet365 and Pinnacle [16] | Not verified |
| Odds history | **Standard keeps only the latest price**: "Store observations yourself if you need a price timeline" [6]. Premium keeps a price history "for up to 7 days after the match has started" [4] | Reportedly "kept for seven days only", updated every 3 hours [A] |
| Quote timestamps | `latest_bookmaker_update` (UTC) on Standard; `bookmaker_update` on Premium history [14] | `update` field [A] |
| Odds refresh and lead time | Standard pre-match every 1–10 minutes; prices appear about two to three weeks ahead [6] | Reportedly every 3 hours, 1–14 days ahead [A] |
| Storage and display | "distribution, transfer, and storage of data provided by our services is allowed, but reselling the product is forbidden"; data licensed per domain [9] | Reportedly no resale [A] |

## What this means for the build

- **The brief's 7-day claim is confirmed for Sportmonks Premium.** Neither provider can backfill
  past seasons' O/U 1.5 / 3.5 / BTTS prices.
- **Sportmonks Standard is enough for forward capture.** It holds only the latest price, so our
  own append-only snapshots at 48h / 24h / 2h / 15 min become the record (which is what the
  brief designs for). `latest_bookmaker_update` gives each quote's real time, which is stored
  alongside our capture time.
- **The free plan is a zero-cost test bed.** With a free key (Scottish Premiership, Danish
  Superliga) the adapter and capture job can be built and tested against real responses,
  including whether 1.5 and 3.5 lines actually come back, before paying for the five leagues.
- If API-Football's reported 3-hour odds refresh is right, a "15 minutes before kickoff"
  snapshot could hold a quote up to 3 hours old. That alone favours Sportmonks for the
  capture job.

## Not verified

1. Everything in the API-Football column.
2. Sportmonks: the Standard Odds add-on price on Starter; bundle prices (the pricing page and
   docs disagree); per-league coverage flags (the coverage finder is interactive); which
   totals lines each bookmaker offers; which rate-limit bucket odds endpoints use; when
   confirmed XIs are typically published.

## Sources

1. https://www.sportmonks.com/football-api/pricing/
2. https://docs.sportmonks.com/v3/api/rate-limit
3. https://docs.sportmonks.com/v3/endpoints-and-entities/endpoints/standard-odds-feed
4. https://docs.sportmonks.com/v3/endpoints-and-entities/endpoints/premium-odds-feed/premium-pre-match-odds
5. https://docs.sportmonks.com/v3/tutorials-and-guides/tutorials/expected/coverage
6. https://docs.sportmonks.com/v3/api-faq (odds FAQ)
7. https://docs.sportmonks.com/v3/tutorials-and-guides/guides/historical-data-add-on
8. https://docs.sportmonks.com/v3/tutorials-and-guides/tutorials/odds-and-predictions/markets
9. https://www.sportmonks.com/terms-of-service/
10. https://docs.sportmonks.com/v3/tutorials-and-guides/tutorials/includes/scores
11. https://docs.sportmonks.com/v3/definitions/types/statistics/fixture-statistics
12. https://docs.sportmonks.com/v3/tutorials-and-guides/tutorials/includes/sidelined
13. https://docs.sportmonks.com/v3/llms.txt
14. https://docs.sportmonks.com/v3/endpoints-and-entities/entities/odd-and-prediction
15. https://www.sportmonks.com/football-api/premier-league-api/
16. https://docs.sportmonks.com/v3/tutorials-and-guides/tutorials/odds-and-predictions/bookmakers

[A] Not fetchable (HTTP 403): https://www.api-football.com/pricing,
https://www.api-football.com/news/post/how-ratelimit-works,
https://www.api-football.com/news/post/how-to-get-started-with-api-football-the-complete-beginners-guide,
https://api-sports.io/terms
