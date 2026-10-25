# Odds data provider documentation audit (NFL player props)

- Retrieval date for every page below: **2026-10-25**
- Method: public pages fetched with `curl` and read as text, plus the machine-readable API spec where one exists. No sign-ups, no trials, no payment details, no API key, no paid or keyed endpoints called.
- Convention: **Stated** = quoted or closely paraphrased from the cited page. **Derived** = my arithmetic or inference from stated facts. Anything not stated is listed in the "Unverified / unclear" section at the end.

---

## 1. The Odds API (https://the-odds-api.com/)

Pages used (all retrieved 2026-10-25):

| Short name | URL |
|---|---|
| Home / pricing | https://the-odds-api.com/ (pricing block anchor `#get-access`) |
| v4 docs | https://the-odds-api.com/liveapi/guides/v4/ |
| API reference (OpenAPI YAML behind the Scalar viewer) | https://the-odds-api.com/api-reference/v4/ and https://the-odds-api.com/api-reference/v4/schema.yml |
| Betting markets | https://the-odds-api.com/sports-odds-data/betting-markets.html |
| Bookmakers | https://the-odds-api.com/sports-odds-data/bookmaker-apis.html |
| Historical odds | https://the-odds-api.com/historical-odds-data/ |
| Update intervals | https://the-odds-api.com/sports-odds-data/update-intervals.html |
| Rate limit guide | https://the-odds-api.com/guide/rate-limit.html |
| FAQs | https://the-odds-api.com/manage/faqs.html |
| NFL page (current, linked from site nav) | https://the-odds-api.com/sports/nfl-odds.html |
| NFL page (older, still served, apparently stale) | https://the-odds-api.com/sports-odds-data/nfl-odds.html |
| FIFA World Cup page (UK-region props example) | https://the-odds-api.com/sports/fifa-world-cup-odds.html |
| Terms and Conditions | https://the-odds-api.com/terms-and-conditions.html |

### 1a. Pricing tiers, free tier, historical access

Source: https://the-odds-api.com/ (pricing block, "Get Access")

| Plan | Price (as listed) | Credits / month | Features listed |
|---|---|---|---|
| Starter | FREE | 500 | All sports, **Most** bookmakers, All betting markets, ~~Historical Odds~~ (struck through in the HTML: `<s>Historical Odds</s>`) |
| 20K | $30 per month, USD | 20,000 | All sports, All bookmakers, All betting markets, Historical Odds |
| 100K | $59 per month, USD | 100,000 | same + Historical Odds |
| 5M | $119 per month, USD | 5M | same + Historical Odds |
| 15M | $249 per month, USD | 15M | same + Historical Odds |

- "Create an account to discover higher usage plans" (home page).
- **GBP price: not stated.** Prices are shown only as "per month, USD". VAT/sales tax for UK buyers is not mentioned.
- **Historical endpoints need a paid plan.** Stated in several places:
  - v4 docs, GET historical odds: "This endpoint is only available on paid usage plans." The same sentence appears for historical events and historical event odds. https://the-odds-api.com/liveapi/guides/v4/
  - Historical page: "Historical data is only available on paid usage plans." https://the-odds-api.com/historical-odds-data/
  - Current NFL page: "Current odds data and scores are available on all usage plans, including the free usage plan. Historical NFL odds data is available on paid usage plans." https://the-odds-api.com/sports/nfl-odds.html
- **Cheapest plan with historical access: "20K", $30/month USD, 20,000 credits.**
- Bookmakers excluded from the free plan (marked "Only available on paid subscriptions"): `williamhill_us` (Caesars), `fanatics`, `courtside`, `rebet`, `bet365_au`, `dabble_au`, `bet99_ca_on`. https://the-odds-api.com/sports-odds-data/bookmaker-apis.html
- Billing and reset: "For paid plans, billing happens straight away. Payment then happens automatically each month thereafter, on the same day of the month that the subscription started." Also: "Usage credits are automatically reset on the first of every month." https://the-odds-api.com/manage/faqs.html
- Upgrades: "The billing cycle resets and a new invoice is issued … Credits become available immediately … The API key stays the same." https://the-odds-api.com/manage/upgrade-downgrade-cancel-a-subscription.html

### 1b. Usage-credit cost formulas

Source: https://the-odds-api.com/liveapi/guides/v4/ (each endpoint's "Usage Quota Costs" section)

| Endpoint | Stated formula / cost |
|---|---|
| `GET /v4/sports` | "This endpoint does not count against the usage quota." |
| `GET /v4/sports/{sport}/events` | "This endpoint does not count against the usage quota." |
| `GET /v4/sports/{sport}/odds` | "The usage quota cost is 1 per region per market." `cost = [number of markets specified] x [number of regions specified]` |
| `GET /v4/sports/{sport}/events/{eventId}/odds` | `cost = [number of unique markets returned] x [number of regions specified]` |
| `GET /v4/sports/{sport}/events/{eventId}/markets` | "A call to this endpoint costs 1 usage credit." |
| `GET /v4/sports/{sport}/participants` | "A call to this endpoint costs 1 usage credit." |
| `GET /v4/sports/{sport}/scores` | 2 if `daysFrom` is given, otherwise 1 |
| `GET /v4/historical/sports/{sport}/odds` | "The usage quota cost for historical odds is 10 per region per market." `cost = 10 x [number of markets specified] x [number of regions specified]` |
| `GET /v4/historical/sports/{sport}/events` | "This endpoint costs 1 from usage quota. If no events are found, it will not cost." |
| `GET /v4/historical/sports/{sport}/events/{eventId}/odds` | `cost = 10 x [number of unique markets returned] x [number of regions specified]` |
| `GET /v4/historical/sports/{sport}/events/{eventId}/markets` | "A call to this endpoint costs 1 usage credit." |

Modifiers (stated):
- Region equivalence via `bookmakers`: "Every group of 10 bookmakers is the equivalent of 1 region. For example, specifying up to 10 bookmakers counts as 1 region. Specifying between 11 and 20 bookmakers counts as 2 regions." (v4 docs, /odds parameters)
- Event-odds market counting: "When calculating the market component of usage quota costs, a count of unique markets in the API response is used. For example if you specify 5 different markets and 1 region in the API call, and data is only available for 2 markets, the cost will be [2 markets] x [1 region] = 2". The historical version says the same with a ×10 multiplier ("10 x [2 markets] x [1 region] = 20").
- "Responses with empty data do not count towards the usage quota." (event odds, historical odds, historical event odds) and, for /odds, "If no events are returned, the request will not count against the usage quota".
- The NFL example in the docs: `/v4/historical/sports/americanfootball_nfl/events/{id}/odds?markets=player_pass_tds,player_anytime_td,player_rush_longest&regions=us` → "Cost: 30".
- The historical page words it as "10 per region per market per event" for historical event odds. https://the-odds-api.com/historical-odds-data/

Derived budget examples (my arithmetic; worst case, charged only for markets actually returned):
- One historical NFL snapshot with the 11 priority standard keys (table below), 1 region or 10 or fewer named bookmakers: 10 × 11 × 1 = **110 credits per game per snapshot**. Adding the 10 matching `_alternate` keys makes it 21 markets = **210 credits**.
- On the 20K plan: about 181 game-snapshots per month at 110 credits, or about 95 at 210 credits. One snapshot of all 272 regular-season games at 110 credits = 29,920 credits, which needs the 100K plan or two months of 20K.
- Each `historical/events` lookup (to get event IDs at a timestamp) adds 1 credit.

### 1c. NFL player-prop market keys (exact strings)

Source: https://the-odds-api.com/sports-odds-data/betting-markets.html, sections "NFL, NCAAF, CFL Player Props API" and "Alternate NFL Player Props API". Keys were pulled out by script from the page text, so the spelling is exact. The page lists 33 standard keys and 26 alternate keys.

Things to watch:
- Receiving yards is **`player_reception_yds`**, not `player_receiving_yds`.
- Rush + rec yards is **`player_rush_reception_yds`**.
- Interceptions thrown is **`player_pass_interceptions`**. Defensive interceptions are a separate key, `player_defensive_interceptions`, with no alternate listed.
- `player_anytime_td`, `player_1st_td`, `player_last_td`, `player_tds`, `player_tds_over` and `player_pass_yds_q1` have no `_alternate` key listed.
- "Milestone (X+) markets are captured using `_alternate` player market keys, for example `player_pass_yds_alternate`" (https://the-odds-api.com/sports/nfl-odds.html).
- DFS sites put non-default multipliers in `_alternate` markets (Pick6, Underdog), and PrizePicks puts "Demons and goblins" there (https://the-odds-api.com/sports-odds-data/bookmaker-apis.html).
- Player props must be requested one event at a time: "Player props can be accessed one event at a time using the /events/{eventId}/odds endpoint." (betting markets page)

| Standard key | Market name (as listed) | Alternate key (as listed) | Priority for this project |
|---|---|---|---|
| `player_assists` | Assists (Over/Under) | `player_assists_alternate` |  |
| `player_defensive_interceptions` | Defensive Interceptions (Over/Under) | (none listed) |  |
| `player_field_goals` | Field Goals (Over/Under) | `player_field_goals_alternate` |  |
| `player_kicking_points` | Kicking Points (Over/Under) | `player_kicking_points_alternate` |  |
| `player_pass_attempts` | Pass Attempts (Over/Under) | `player_pass_attempts_alternate` | yes |
| `player_pass_completions` | Pass Completions (Over/Under) | `player_pass_completions_alternate` | yes |
| `player_pass_interceptions` | Pass Intercepts (Over/Under) | `player_pass_interceptions_alternate` | yes |
| `player_pass_longest_completion` | Longest Pass Completion (Over/Under) | `player_pass_longest_completion_alternate` |  |
| `player_pass_rush_yds` | Pass + Rush Yards (Over/Under) | `player_pass_rush_yds_alternate` |  |
| `player_pass_rush_reception_tds` | Pass + Rush + Reception Touchdowns (Over/Under) | `player_pass_rush_reception_tds_alternate` |  |
| `player_pass_rush_reception_yds` | Pass + Rush + Reception Yards (Over/Under) | `player_pass_rush_reception_yds_alternate` |  |
| `player_pass_tds` | Pass Touchdowns (Over/Under) | `player_pass_tds_alternate` | yes |
| `player_pass_yds` | Pass Yards (Over/Under) | `player_pass_yds_alternate` | yes |
| `player_pass_yds_q1` | 1st Quarter Pass Yards (Over/Under) | (none listed) |  |
| `player_pats` | Points After Touchdown (Over/Under) | `player_pats_alternate` |  |
| `player_receptions` | Receptions (Over/Under) | `player_receptions_alternate` | yes |
| `player_reception_longest` | Longest Reception (Over/Under) | `player_reception_longest_alternate` |  |
| `player_reception_tds` | Reception Touchdowns (Over/Under) | `player_reception_tds_alternate` |  |
| `player_reception_yds` | Reception Yards (Over/Under) | `player_reception_yds_alternate` | yes |
| `player_rush_attempts` | Rush Attempts (Over/Under) | `player_rush_attempts_alternate` | yes |
| `player_rush_longest` | Longest Rush (Over/Under) | `player_rush_longest_alternate` |  |
| `player_rush_reception_tds` | Rush + Reception Touchdowns (Over/Under) | `player_rush_reception_tds_alternate` |  |
| `player_rush_reception_yds` | Rush + Reception Yards (Over/Under) | `player_rush_reception_yds_alternate` | yes |
| `player_rush_tds` | Rush Touchdowns (Over/Under) | `player_rush_tds_alternate` |  |
| `player_rush_yds` | Rush Yards (Over/Under) | `player_rush_yds_alternate` | yes |
| `player_sacks` | Sacks (Over/Under) | `player_sacks_alternate` |  |
| `player_solo_tackles` | Solo Tackles (Over/Under) | `player_solo_tackles_alternate` |  |
| `player_tackles_assists` | Tackles + Assists (Over/Under) | `player_tackles_assists_alternate` |  |
| `player_tds_over` | Touchdowns (Over only) | (none listed) |  |
| `player_tds` | Touchdowns (Over/Under) | (none listed) |  |
| `player_1st_td` | 1st Touchdown Scorer (Yes/No) | (none listed) |  |
| `player_anytime_td` | Anytime Touchdown Scorer (Yes/No) | (none listed) | yes |
| `player_last_td` | Last Touchdown Scorer (Yes/No) | (none listed) |  |

Quick map from the requested stats to keys:

| Stat | Key | Alternate |
|---|---|---|
| Passing yards | `player_pass_yds` | `player_pass_yds_alternate` |
| Pass attempts | `player_pass_attempts` | `player_pass_attempts_alternate` |
| Pass completions | `player_pass_completions` | `player_pass_completions_alternate` |
| Rushing yards | `player_rush_yds` | `player_rush_yds_alternate` |
| Rush attempts | `player_rush_attempts` | `player_rush_attempts_alternate` |
| Receptions | `player_receptions` | `player_receptions_alternate` |
| Receiving yards | `player_reception_yds` | `player_reception_yds_alternate` |
| Pass TDs | `player_pass_tds` | `player_pass_tds_alternate` |
| Interceptions thrown | `player_pass_interceptions` | `player_pass_interceptions_alternate` |
| Anytime TD | `player_anytime_td` | none listed |
| Rush + rec yards | `player_rush_reception_yds` | `player_rush_reception_yds_alternate` |

### 1d. Regions and bookmakers that return player props; UK specifics

Region keys accepted (API reference enum for `regions`): `uk, us, us2, us_dfs, us_ex, eu, au, ca, fr, se, fi`. https://the-odds-api.com/api-reference/v4/schema.yml
(The prose v4 docs only give `us`, `us2`, `uk`, `au`, `eu` as examples.)

What the docs say about where player props come from:
- Betting markets page: "Coverage of player props is mainly limited to US sports and US bookmakers at this time." Also: "Additional markets are currently limited to US sports and selected bookmakers, however coverage is expanding." https://the-odds-api.com/sports-odds-data/betting-markets.html
- Current NFL page: "Additional markets including player props, half and quarter time markets, and more are covered for most US bookmakers." https://the-odds-api.com/sports/nfl-odds.html
- Older NFL page (still served; mentions FoxBet, which is no longer in the bookmaker list, so probably stale): "NFL player props (US and AU bookmakers only)". https://the-odds-api.com/sports-odds-data/nfl-odds.html
- v4 docs, event odds: "Coverage of non-featured markets is currently limited to selected bookmakers and sports, and expanding over time."
- US DFS (`us_dfs`): "The API covers player props from the following US DFS sites. Odds on DFS sites can vary based on user selections, therefore odds are indicative only." https://the-odds-api.com/sports-odds-data/bookmaker-apis.html
- AFL and NRL props: "currently available from select AU bookmakers, including Sportsbet, Ladbrokes, TAB, Pointsbet and Betr". Soccer props: "Coverage is currently limited to US bookmakers." (betting markets page)
- Counter-example for UK: the FIFA World Cup page shows `regions=uk` with `markets=btts,alternate_totals,player_goal_scorer_anytime`, and the sample response contains bookmaker `skybet` ("Sky Bet") returning `player_goal_scorer_anytime` outcomes. https://the-odds-api.com/sports/fifa-world-cup-odds.html

**Conclusion on UK-region NFL player props: no current page says NFL player props are available from UK-region bookmakers.** The wording points to US books ("mainly US bookmakers", "most US bookmakers"; legacy page: "US and AU bookmakers only"). UK books do show props for soccer in at least one doc example, so UK prop coverage is not zero, but nothing documents it for NFL. `us_ex` is not described as covering props.

UK-region bookmakers listed (region key `uk`, key → name), from https://the-odds-api.com/sports-odds-data/bookmaker-apis.html:

| Key | Name |
|---|---|
| `sport888` | 888sport |
| `betano_uk` | Betano (UK) |
| `betfair_ex_uk` | Betfair Exchange |
| `betfair_sb_uk` | Betfair Sportsbook |
| `betfred_uk` | Betfred (UK) |
| `betvictor` | Bet Victor |
| `betway` | Betway |
| `boylesports` | BoyleSports |
| `casumo` | Casumo |
| `coral` | Coral |
| `grosvenor` | Grosvenor |
| `ladbrokes_uk` | Ladbrokes |
| `leovegas` | LeoVegas |
| `livescorebet` | LiveScore Bet |
| `matchbook` | Matchbook |
| `paddypower` | Paddy Power |
| `skybet` | Sky Bet |
| `smarkets` | Smarkets |
| `unibet_uk` | Unibet |
| `virginbet` | Virgin Bet |
| `williamhill` | William Hill (UK) |

**BetMGM UK is not listed.** BetMGM appears only as `betmgm` (region `us`, linked to sports.nj.betmgm.com), `betmgm_ca_on` (region `ca`, "BetMGM (CA - ON)") and `betmgm_se` (region `se`, "BetMGM (SE)"). There is no `uk`-region BetMGM key. Similarly `williamhill_us` is Caesars (US), separate from `williamhill` (William Hill UK, also in `eu`).

### 1e. Historical data: snapshots, start dates, props support

- Featured markets: "Historical odds data is available from June 6th 2020, with snapshots taken at 10 minute intervals. From September 2022, historical odds snapshots are available at 5 minute intervals." (v4 docs, GET historical odds; same wording on https://the-odds-api.com/historical-odds-data/)
- Additional markets (including player props), exact wording:
  - v4 docs, GET historical event odds: "Historical data for additional markets (player props, alternate lines, period markets) are available after **2023-05-03T05:30:00Z**." In the `date` parameter text: "Historical data for additional markets are available after 2023-05-03T05:30:00Z. Snapshots are available at 5 minute intervals." https://the-odds-api.com/liveapi/guides/v4/
  - Historical page: "Historical odds data for additional markets, including player props, period markets and more, is available from **May 3rd, 2023**. Snapshots are available at 5 minute intervals." https://the-odds-api.com/historical-odds-data/
  - API reference tag text: "Snapshots of historical odds for non-featured markets (player props, period markets, alternates, etc.) are available from May 2023 at 5 minute intervals." https://the-odds-api.com/api-reference/v4/schema.yml
  - Current NFL page: "Historical odds for other markets are available from May 2023." https://the-odds-api.com/sports/nfl-odds.html
  - So the belief "additional markets archive begins May 2023" is **confirmed**; the precise timestamp is 2023-05-03T05:30:00Z.
- **The historical event odds endpoint supports player props.** It "Accepts any available betting markets using the markets parameter", and the docs' own NFL cost examples use `player_pass_tds,player_anytime_td,player_rush_longest`. The plain historical `/odds` endpoint "will only return featured markets … using the market keys `h2h`, `spreads`, `totals`" (API reference), so it cannot be used for props.
- The NFL earliest featured-market timestamp is `2020-06-06T10:05:00Z` (table "Earliest Historical Timestamps", historical page).
- "Bookmakers, sports and markets will only be available in the historical odds API from the time that they were added to the current odds API." (v4 docs)
- Event expiry: "If the event had expired at the specified timestamp (not receiving updates due to completion or cancellation), a HTTP 404 status code will be returned." (API reference, historical event odds `eventId`)
- Snapshot navigation: responses are wrapped with `timestamp`, `previous_timestamp` and `next_timestamp`. The API returns "the closest snapshot equal to or earlier than the provided date parameter".
- Data quality notes: "Prior to Septemer 18th 2022, only decimal odds were caputred in historical snapshots" (typos as on page), and "Data errors … can still be present in historical odds snapshots." (v4 docs)
- Live update cadence (current API, not the archive): additional markets (player props, alternates, period markets) update every 60 s pre-match and 60 s in-play. Featured markets update every 60 s pre-match and 40 s in-play. "Six hours before an event's start time, the update interval begins decreasing from the pre-match interval". https://the-odds-api.com/sports-odds-data/update-intervals.html

### 1f. Response fields

Sources: v4 docs examples, and the API reference `Outcome` schema in https://the-odds-api.com/api-reference/v4/schema.yml

- **`last_update`**:
  - Market level: "The market-level last_update field shows the last time our system saw odds for that market from the bookmaker. When a market is suspended or closed, its last_update stops advancing, and the market is removed from the API response after about 15 minutes."
  - Bookmaker level: "The bookmaker-level last_update field is deprecated." (v4 docs, /odds "More info")
  - Event odds: "The last_update field is only available on the market level in the response and not on the bookmaker level." However, the historical event-odds example on the same page still shows a bookmaker-level `last_update`.
- **`description`**: "This field is only relevant for certain markets. It contains more information about the outcome (for example, for player prop markets, it includes the player's name)". Example: `{"name":"Over","description":"Joe Burrow","price":-235,"point":1.5}` (https://the-odds-api.com/sports/nfl-odds.html)
- **`point`**: "The handicap or points of the outcome". The NFL prop examples carry the line here (for example `"point": 76.5` for `player_rush_yds`).
- **`name`**: "For totals markets, this will be 'Over' or 'Under'". NFL prop examples use `"Over"` and `"Under"`. Yes/No markets: a soccer UK example shows `{"name":"Yes","description":"Kim Tae-Hyeon","price":17}` for `player_goal_scorer_anytime` (World Cup page). No NFL `player_anytime_td` sample was found.
- **`link`** (needs `includeLinks=true`): exists at event, market and outcome level ("link to the bookmaker's website and populate the betslip"), "if available".
- **`sid`** (needs `includeSids=true`): "The bookmaker's id for the bet selection, if available", and also present at event and market level.
- Other optional fields: `bet_limit` (`includeBetLimits=true`, "mainly populated for betting exchanges"), `multiplier` (`includeMultipliers=true`, "Applicable to US DFS sites"), and rotation numbers (`includeRotationNumbers=true`, "Available in historical data from November 2025").
- **Player IDs: none documented.** The player shows up only as a name string in `description`. The participants endpoint "does not return players on a team" (v4 docs and API reference). `sid` is the bookmaker's own selection id, not a cross-book player id. Joining to nflverse or other stats data will need name matching (derived).
- Event `id` is "A unique 32 character identifier for the event."

### 1g. Rate limits, 429, quota headers

- Headers on every call: `x-requests-remaining` ("The usage credits remaining until the quota resets"), `x-requests-used` ("The usage credits used since the last quota reset"), `x-requests-last` ("The usage cost of the last API call"). https://the-odds-api.com/liveapi/guides/v4/
- 429: "Requests are rate limited in order to protect our systems from sudden bursts in traffic. If you enounter the rate limit, the API will respond with a status code of 429, in which case try spacing out requests over several seconds." (v4 docs)
- "The current rate limit is 30 requests per second on paid usage plans." It also says 429s can happen below the limit during scale-up, and advises: "retry the request after a couple of seconds", combine markets and regions into one request, and cache `/sports` and `/events` (examples: "cache the /sports response for an hour, and the /events endpoint for 10 minutes"). https://the-odds-api.com/guide/rate-limit.html
- Running out of credits gives **401**, not 429: "Unauthenticated or unauthorized. The API key might be missing or invalid (unauthenticated), or it might at its usage limit (unauthorized)." 429 is described as "The request was throttled because requests are being sent too frequently". (API reference) This fits the observed 401 on an unauthenticated `/v4/sports`.

### 1h. Terms: storage, display, redistribution

Source: https://the-odds-api.com/terms-and-conditions.html ("Last updated: 31 August 2026"), section "Restrictions":

> "Do not resell, repackage, or redistribute our data as a standalone data product. This includes, but is not limited to, offering our data through your own API, data feed, downloadable files, or any other format intended to serve as a source of raw data for others."

> "Permitted uses include, but are not limited to: Storing our data and retaining it indefinitely; Displaying our data in a UI, website, or mobile app, including for commercial use; Using our data in research papers and analytical dashboards; Calculating and displaying values you derive from our data; Using our data to train statistical and machine learning models"

> "Attribution to The Odds API is not required, but is always appreciated."

Other relevant points: the provider is "The Odds API Pty Ltd, an Australian registered company", governed by the laws of New South Wales. Users "are encouraged to display appropriate responsible gambling messaging (e.g., "Gamble Responsibly. 18+")" if the service is used to promote bookmakers.

Derived note for this project (it is a GitHub Pages site): storing data, showing odds and derived forecasts, and training models are explicitly permitted. Publishing raw odds as downloadable JSON/CSV in a public repo could fall under "downloadable files … intended to serve as a source of raw data for others". Keep raw pulls private or show only derived values.

### 1i. Trial, sample or scrambled data

- **No mention of scrambled or trial data** on any Odds API page reviewed. The free "Starter" plan (500 credits/month) is the only no-cost tier. It covers current data for "All betting markets" and "Most bookmakers", but not historical data.
- Public sample files: the historical page links static sample responses, for example `https://public-odds-api-sample-data.s3.amazonaws.com/historical-nfl.json` (NFL, featured markets h2h/spreads/totals, US books, snapshot 2021-11-25T12:00:00Z). The file is publicly reachable (HTTP 200, S3 Last-Modified 2022-10-21). No player-prop sample file is linked. https://the-odds-api.com/historical-odds-data/

---

## 2. SportsDataIO (brief)

Pages (retrieved 2026-10-25):
- Developer hub / access methods: https://sportsdata.io/developers
- Scrambled data article: https://sportsdata.io/help/scrambled-data
- FAQ: https://sportsdata.io/help/faq (https://sportsdata.io/developers/faq redirects here)
- NFL API docs: https://sportsdata.io/developers/api-documentation/nfl
- NFL data dictionary: https://sportsdata.io/developers/data-dictionary/nfl
- Discovery Lab: https://discoverylab.sportsdata.io/

Findings:
- **Free trial data is scrambled.** Developer hub: "Scrambled but structurally identical data … Player names, scores, and stats are scrambled — realistic but not real … Data is not real — cannot be used for analysis or display … Small daily call limit". Scrambled-data article: values are "randomly adjusted up or down between 5% and 20% from the actual value", and scrambling "allows us to give away our Free Trial without ever expiring". FAQ: "If you are seeing scrambled data … you are most likely using a free trial key. Complete and accurate data is reserved for paid subscriptions." The data dictionary has a "Scrambled" column per field (for example `PlayerID` on BettingMarket is marked Scrambled = Yes).
- The "Dev Key" (contact sales) uses the "Same scrambled data as Free Trial".
- **Replay** is "Real data from past games replayed on the live feed schedule" (self-serve, select sports).
- NFL odds and prop endpoints (base `https://api.sportsdata.io/v3/nfl/odds/json/`), from the NFL API docs:
  - Props: `BettingEventsByDate/{date}`, `BettingEvents/{season}`, `BettingMarkets/{eventId}`, `BettingMarketsByEvent/{eventId}/{sportsbookgroup}`, `BettingMarketsByScoreID/{scoreid}`, `BettingMarketsByGameID/{gameid}/{sportsbookgroup}`, `BettingMarketsByMarketType/{eventId}/{marketTypeID}[/{sportsbookgroup}]`, `BettingPlayerPropsByScoreID/{scoreid}[/{sportsbookgroup}]` ("Betting Player Props - by Game").
  - Props Plus: `BettingMarket/{marketId}[/{sportsbookgroup}]` ("Returns full line movement … intended for historical data purposes").
  - Others: `BettingMarketResults/{marketId}`, `BettingResultsByMarket/{marketId}/{sportsbookgroup}`, `BettingFuturesBySeason/{season}`.
  - Game lines: `GameOddsByWeek/{season}/{week}`, `PreGameOddsByWeek/...`, `GameOddsLineMovement/{scoreid}`, `PreGameOddsLineMovement[WithResulting]/...`, `LiveGameOddsByWeek/...`, `InGameOddsByWeek/...`, `AlternateMarketGameOddsByWeek/...`.
  - Splits and trends: `BettingSplitsByMarketId/{marketId}`, `BettingSplitsByScoreId/{scoreId}`, `TeamTrends/{team}`, `MatchupTrends/{team}/{opponent}`, plus `BettingMetadata` and `ActiveSportsbooks`.
  - Betting outcomes carry a SportsDataIO `PlayerID` ("this ID will stay with the player throughout their entire career"), unlike The Odds API.
- **Pricing visibility:**
  - Leagues API, Dev Key, Global Sports API and Vault (historical) are "Contact sales".
  - Discovery Lab (self-serve, real but "Next-day delayed", "100–1,000 API calls per day depending on tier", "Not licensed for commercial redistribution") is public: Fantasy $99/mo or $599/yr; Odds $99/mo or $599/yr; Fantasy + Odds $149/mo or $899/yr; Free tier "provides access to last season's data".
  - https://sportsdata.io/pricing returned HTTP 404.
- Historical props: "Props & Futures for Big Six sports (NFL, …): Stored in a separate data warehouse with separate endpoints and a separate API key … moving data older than 30 days to this archive" (Vault, contact sales).

---

## 3. Sportradar (brief)

Pages (retrieved 2026-10-25):
- Your Account (trial terms): https://developer.sportradar.com/getting-started/docs/your-account (markdown at `.md`)
- Make Your First Call: https://developer.sportradar.com/getting-started/docs/make-your-first-call
- Simulations: https://developer.sportradar.com/getting-started/docs/simulations
- Odds docs index: https://developer.sportradar.com/odds/llms.txt
- OC Player Props overview: https://developer.sportradar.com/odds/reference/oc-player-props-overview
- OC Player Props FAQ: https://developer.sportradar.com/odds/reference/oc-player-props-faq
- NFL API v7 overview: https://developer.sportradar.com/football/reference/nfl-overview

Findings:
- **Trial keys exist and are self-serve** via the Sportradar Marketplace ("Click the **Add Trial** button"). Defaults: "30-day trial period", "1,000 requests (quota) per rolling 30 days", "1 QPS".
- Not all trials can be self-issued. The non-self-serve list includes "Live Odds API", "Probabilities API" and "Push Feeds". Odds Comparison Player Props is not on that list (and its OpenAPI path uses `/oddscomparison-player-props/trial/v2/`), so it appears self-serve, but I did not confirm this inside the Marketplace.
- **Trial data is real:** "Trial access provides the same real-world data as production, with some rare exceptions. Trials will have lower rate limits but the data itself is not throttled or updated at a lesser frequency". (Your Account)
- Simulations ("replay real games") need no API key.
- Product names: Odds APIs are "Odds Comparison Prematch", "Odds Comparison Player Props" (v2), "Odds Comparison Futures", "Odds Comparison Live Odds", "Odds Comparison Core", plus "Probabilities" and "Mapping API". The sports data product is "NFL API v7".
- Player Props API: "Top US bookmakers are available, and return odds for NFL, NBA, MLB, NHL, NCAA Football and top soccer leagues."
  - FAQ bookmaker list: "DraftKings, William Hill, MGM, PointsBet, FanDuel" (no UK books named).
  - NFL markets use IDs such as `sr:market:914` (passing yards), `915` (completions), `916` (passing TDs), `917` (carries), `918` (rushing yards), `919` (receiving yards), `920` (receptions), `6000` (passing interceptions), `6001` (passing attempts), `6005` (rushing + receiving yards), `6016` (anytime TD scorer).
  - Players have `sr:player:` IDs with mapping feeds.
  - Closing lines: after kickoff, removed pre-match markets keep the last odds with `removed="true"`, "which can be interpreted as the closing line".
- Pricing: none published on the developer pages reviewed. Extended trials and production go through sales@sportradar.com.
- Historical prop archive depth: not stated on the pages reviewed.

---

## Unverified / unclear

1. **UK-region NFL player props (The Odds API).** No page states that UK-region bookmakers return NFL player props.
   - Current wording: "mainly limited to US sports and US bookmakers"; NFL page: "covered for most US bookmakers".
   - The older NFL page says "US and AU bookmakers only" and looks stale (it lists FoxBet).
   - A World Cup example shows Sky Bet (`uk`) returning a soccer player prop, so UK props exist at least for soccer. This conflicts with the betting-markets page's "Coverage is currently limited to US bookmakers" for soccer props.
   - To verify cheaply with a key: `GET /v4/sports/americanfootball_nfl/events/{eventId}/markets?regions=uk` costs 1 credit and works on the free plan; the historical version is also 1 credit but needs a paid plan.
2. **AU-region NFL props.** Only the stale legacy page mentions AU. Not confirmed on current pages.
3. **GBP pricing / VAT.** Not stated; prices are listed only in USD.
4. **Free-plan rate limit.** Only "30 requests per second on paid usage plans" is stated.
5. **Credit reset vs billing date.** The FAQ says credits reset "on the first of every month", while billing recurs "on the same day of the month that the subscription started". How these interact for a mid-month subscription is not explained.
6. **First NFL prop snapshot in practice.** The archive starts 2023-05-03T05:30:00Z, which is the NFL offseason. When NFL props first actually appear (2023 preseason or regular season?) and the per-bookmaker or per-market start dates for props are not published. The docs only say items appear "from the time that they were added to the current odds API". Whether every `_alternate` key existed from May 2023 is not stated.
7. **Cost wording.** The v4 docs say historical event odds cost `10 x [unique markets returned] x [regions]`; the historical page says "10 per region per market per event". These are consistent for one event per call, but the docs do not say how a returned market with zero matching bookmakers is counted.
8. **`includeLinks` on historical endpoints.** The v4 prose says historical parameters are "the same as for the /odds endpoint", but the OpenAPI spec lists `includeSids` and `includeBetLimits` for the historical endpoints and **not** `includeLinks`. Whether historical responses can include `link` is unclear.
9. **Yes/No outcome format for NFL TD scorer markets.** Inferred from the soccer example (`name: "Yes"`, `description: <player>`). No NFL `player_anytime_td` sample was found.
10. **Bookmaker-level `last_update` in event odds.** The docs say it is absent and deprecated, but the historical event-odds example still shows it. Treat market-level `last_update` as authoritative.
11. **Player identity.** The Odds API documents no player IDs; `sid` is the bookmaker's selection id "if available". Whether `sid` is stable across snapshots is not stated.
12. **Terms interpretation.** Whether a public GitHub repo containing raw odds CSV/JSON counts as "downloadable files … intended to serve as a source of raw data for others" is my reading, not a ruling. The terms say "If you are unsure whether your use case is permitted, please contact us."
13. **SportsDataIO scrambling scope.** The developer hub says "Player names, scores, and stats are scrambled", but the scrambled-data article says "schedules, teams, and players are not scrambled". Whether odds fields are scrambled is per field in the data dictionary; I did not check every betting field. Whether Discovery Lab "Odds" includes player props is not stated explicitly.
14. **Sportradar.** Self-serve trial availability for Odds Comparison Player Props was inferred, not confirmed in the Marketplace. Pricing and historical prop depth are not published on the pages reviewed.
