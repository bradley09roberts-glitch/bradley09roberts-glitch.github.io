# UEFA club competitions in `euro_matches`: sources, rules, team identity and checks

_Loaded 21 October 2026 (about 02:50 UK time, BST) with `uv run footy euro-update`. Decision
record: [DECISIONS.md](../../DECISIONS.md) D-023._

`euro_matches` holds every Champions League, Europa League and Conference League match from
2018/19 that a source gave us, qualifying rounds included where available, plus this season's
fixtures. Scores are regulation time (90 minutes plus stoppage time). Extra time and penalties
are flags only.

## Sources

| Source | Seasons | What | Raw store |
| --- | --- | --- | --- |
| [openfootball/champions-league](https://github.com/openfootball/champions-league), CC0 1.0 (public domain), commit `abfaedd` (2 July 2026, the upstream head on 21 Oct 2026) | 2018/19 to 2025/26 | 23 football.txt files: `cl.txt` every season; `el.txt` from 2020/21; `conf.txt` 2021/22 to 2024/25; `clq.txt`, `elq.txt`, `confq.txt` for 2024/25 and 2025/26 | source `openfootball`, key `<season>/<file>` (e.g. `1819/cl.txt`); URL points at the file at that commit |
| English Wikipedia, CC BY-SA 4.0 (MediaWiki API, `action=parse`) | 2025/26 UEL and UECL main tournaments (openfootball has none) | League-phase, knockout-phase and final articles | source `wikipedia`, keys `uel/2526/league-phase`, `uel/2526/knockout-phase`, `uel/2526/final`, and the same for `uecl` |
| English Wikipedia, via `data/europe/uefa_2627.csv` | 2026/27 | Played matches and scheduled fixtures, built by `footy.europe.build_2627` ([uefa-2627.md](uefa-2627.md)) | the stored 2026/27 pages; every row links to the page revision it came from |
| English Wikipedia, as a check only | 2018/19 to 2025/26 | 63 season, knockout, qualifying and final articles | source `wikipedia`, keys `<comp>/<season>/<page>` |

Fetching: one request at a time, at least 2 s apart, User-Agent
`footy-personal-model/0.1 (personal non-commercial research)`, backing off on HTTP 429 with
`Retry-After`. Every response is stored in the raw store before it is parsed. 65 Wikipedia
pages were fetched on 21 Oct 2026 (46 check pages, 17 finals, 2 gap finals), all HTTP 200.

## What is in the table

| Season | Competition | Source | Matches | Played | Scheduled | Awarded / cancelled | Extra time | Penalties | Neutral | Excluded |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2018/19 | UCL | openfootball | 125 | 125 | 0 |  | 1 | 0 | 1 | 0 |
| 2019/20 | UCL | openfootball | 119 | 119 | 0 |  | 1 | 0 | 7 | 0 |
| 2020/21 | UCL | openfootball | 125 | 125 | 0 |  | 1 | 0 | 8 | 0 |
| 2020/21 | UEL | openfootball | 205 | 204 | 0 | 1 | 2 | 1 | 10 | 1 |
| 2021/22 | UCL | openfootball | 125 | 125 | 0 |  | 2 | 0 | 1 | 0 |
| 2021/22 | UEL | openfootball | 141 | 139 | 0 | 2 | 5 | 2 | 1 | 2 |
| 2021/22 | UECL | openfootball | 141 | 140 | 0 | 1 | 2 | 1 | 1 | 1 |
| 2022/23 | UCL | openfootball | 125 | 125 | 0 |  | 0 | 0 | 4 | 0 |
| 2022/23 | UEL | openfootball | 141 | 141 | 0 |  | 6 | 4 | 6 | 0 |
| 2022/23 | UECL | openfootball | 141 | 141 | 0 |  | 6 | 4 | 5 | 0 |
| 2023/24 | UCL | openfootball | 125 | 125 | 0 |  | 3 | 3 | 4 | 0 |
| 2023/24 | UEL | openfootball | 141 | 141 | 0 |  | 4 | 2 | 4 | 0 |
| 2023/24 | UECL | openfootball | 141 | 141 | 0 |  | 7 | 3 | 10 | 0 |
| 2024/25 | UCL | openfootball | 189 | 189 | 0 |  | 4 | 2 | 5 | 0 |
| 2024/25 | UEL | openfootball | 189 | 189 | 0 |  | 5 | 2 | 10 | 0 |
| 2024/25 | UECL | openfootball | 153 | 153 | 0 |  | 7 | 2 | 5 | 0 |
| 2024/25 | UCLQ | openfootball | 90 | 90 | 0 |  | 6 | 3 | 6 | 0 |
| 2024/25 | UELQ | openfootball | 80 | 80 | 0 |  | 8 | 5 | 6 | 0 |
| 2024/25 | UECLQ | openfootball | 256 | 254 | 0 | 2 | 24 | 15 | 11 | 2 |
| 2025/26 | UCL | openfootball | 189 | 189 | 0 |  | 3 | 1 | 1 | 0 |
| 2025/26 | UEL | wikipedia | 189 | 189 | 0 |  | 5 | 2 | 5 | 0 |
| 2025/26 | UECL | wikipedia | 153 | 153 | 0 |  | 2 | 0 | 10 | 0 |
| 2025/26 | UCLQ | openfootball | 92 | 92 | 0 |  | 6 | 3 | 4 | 0 |
| 2025/26 | UELQ | openfootball | 82 | 82 | 0 |  | 11 | 5 | 8 | 0 |
| 2025/26 | UECLQ | openfootball | 256 | 256 | 0 |  | 25 | 6 | 18 | 0 |
| 2026/27 | UCL | wikipedia | 144 | 45 | 99 |  | 0 | 0 | 4 | 0 |
| 2026/27 | UEL | wikipedia | 144 | 36 | 108 |  | 0 | 0 | 5 | 0 |
| 2026/27 | UECL | wikipedia | 108 | 18 | 90 |  | 0 | 0 | 0 | 0 |
| 2026/27 | UCLQ | wikipedia | 90 | 90 | 0 |  | 10 | 3 | 4 | 0 |
| 2026/27 | UELQ | wikipedia | 80 | 80 | 0 |  | 6 | 2 | 4 | 0 |
| 2026/27 | UECLQ | wikipedia | 258 | 258 | 0 |  | 28 | 16 | 16 | 0 |
| **Total** | | | **4,537** | **4,234** | **297** | **6** | **190** | **87** | **184** | **6** |

383 clubs appear. Every row has a `raw_file_id`.

**Not available** (no source in hand, nothing invented):

- Europa League 2018/19 and 2019/20 (openfootball starts at 2020/21).
- Qualifying rounds before 2024/25, for all three competitions.
- Kick-off times are the scheduled ones (a few matches started late, see the checks).

## Parsing rules (openfootball, `footy.europe.openfootball`)

- **Layout.** `▪ <stage>` starts a stage, a date line (`Tue Sep 18 2018`) a match day, and a
  match line is `[HH:MM]  Home (CC)  v  Away (CC)  <score>`. A time carries on to the following
  lines of the same date. The file title must match the file and season, and the
  `# Matches` and `# Date` headers are checked against the matches.
- **Dates.** A date without a year takes the season year whose weekday matches. An explicit
  year is checked against the weekday.
- **Times.** UEFA's Central European time (CET/CEST), converted with `Europe/Paris`. The three
  2025/26 qualifying files are the exception: they give UK time (see the checks).
- **Scores.**

  | Form | Meaning |
  | --- | --- |
  | `3-1 (2-0)` | full time 3-1, half time 2-0 |
  | `0-0` | full time, no half-time score |
  | `3-1 a.e.t. (2-1, 1-1)` | 3-1 after extra time; regulation 2-1; half time 1-1 |
  | `2-0 a.e.t. (0-0)` | one pair after `a.e.t.` is the regulation score |
  | `1-4 pen. 0-1 a.e.t. (0-1, 0-1)` | penalties 1-4, after extra time 0-1, regulation 0-1, half time 0-1 |
  | `3-0 [awarded]` | status `awarded`, `model_exclusion = "awarded"` |
  | `[cancelled]` | status `cancelled`, no score, `model_exclusion = "cancelled"` |

- **Stages.** `Group A` (including the German `Gruppe G`), `Group MD3` and `League MD3`
  (matchday headings), `League` (2024/25 UEL and UECL league phase, no matchday),
  `Knockout play-off` (`Playoffs`, `Playoffs, Matchday 1/2`), `Round of 32`
  (`Sechzehntelfinale`), `Round of 16`, `Quarter-final`, `Semi-final`, `Final`. Qualifying:
  `Q1` to `Q3` (`Round 1`, `1. Round`) and `Play-off`. A pairing met twice in a round becomes
  `<stage> leg 1` / `leg 2`, by date.
- **Neutral.** Finals, and the single-match rounds of the 2019/20 Champions League final
  tournament in Lisbon (quarter-finals and semi-finals at the Estádio da Luz and Estádio José
  Alvalade; confirmed by the stored 2019/20 knockout-phase page). Everything else comes from
  the Wikipedia check (below), since openfootball has no venues.
- **Ties.** A tie must go to extra time exactly when it is level after 90 minutes (away goals
  count before 2021/22) and to penalties exactly when it is still level after extra time.
  A tie that contradicts this keeps its matches with `model_exclusion = "tie_inconsistent"`;
  after the Wikipedia corrections no tie does.

## Team identity

Every club name resolves through `src/footy/reference/team_aliases.csv` to one `team_key` in
`teams.csv`. There is no fuzzy matching at load time: a name missing from the file is
reported (`unmapped_team` in `import_issues`), its match is left out and the job is marked
`partial`.

- **Aliases** are the names exactly as each source writes them, with their country code:
  openfootball `FC Bayern München (GER)` (its FIFA codes, MCO for Monaco); Wikipedia
  `Bayern Munich (DEU)` (the ISO code of the flag shown). 376 openfootball and 257 Wikipedia
  aliases.
- **Country codes** follow `teams.csv`: ISO 3166-1 alpha-3, the four UK associations as ENG,
  SCO, WAL, NIR, and Kosovo as XKX. openfootball's GER, POR, NED, SUI, CRO, DEN, BUL, GRE and
  KOS become DEU, PRT, NLD, CHE, HRV, DNK, BGR, GRC and XKX. A club takes the country of the
  league it plays in (D-022): Monaco is `fra-monaco` and Vaduz (Liechtenstein) `che-vaduz`.
- **Clubs already in `teams.csv`** keep their key (`FC Bayern München (GER)` and
  `Bayern Munich (DEU)` are `deu-bayern-munich`). They were matched by a reviewed list: a
  name-similarity suggestion within the country, then every pair checked by hand. The
  suggestion was wrong, and corrected, for Paris Saint-Germain (suggested Paris FC), Lazio
  Roma (Roma), Inter Milan (Milan), Wisła Kraków (Wieczysta Kraków), İstanbul Başakşehir
  (İstanbulspor), Athletic Club, Sporting CP, Olympique Lyonnais, AGF (Aarhus), NEC (Nijmegen)
  and others.
- **200 new clubs**, all from countries without a football-data league (Croatia, Serbia,
  Czechia, Cyprus, Ukraine, Hungary, Israel, Kazakhstan, Azerbaijan and 24 more), keyed
  `<country>-<slug>` with a plain name (`hrv-dinamo-zagreb`, `srb-red-star-belgrade`,
  `ukr-dynamo-kyiv`). One exception: `prt-torreense`, a Portuguese second-division club in the
  2026/27 Europa League, which has no football-data alias.
- **Spellings of one club merged**: Crvena Zvezda / FK Crvena Zvezda / Red Star Belgrade;
  Dinamo Kiev / Dynamo Kyiv; Pafos FC / Paphos FC / Pafos; Drita Gjilan / KF Drita / Drita;
  Qarabağ FK / Qarabağ Ağdam FK; SL Benfica / Sport Lisboa e Benfica; Olympiakos Piraeus /
  PAE Olympiakos SFP / Olympiacos; and the "FC"/"SK" variants.
- **Similar names kept apart** (tested): Sporting CP, Sporting Braga and Sporting Gijón; Red
  Star Belgrade and Red Bull Salzburg; Dinamo Zagreb, Dinamo Bucureşti and Dynamo Kyiv; AEK
  Athens and AEK Larnaca; Olympiacos and Olimpija Ljubljana; Inter and Milan; Lazio and Roma;
  CSKA Sofia and CSKA 1948; Žalgiris (Vilnius) and Kauno Žalgiris; Hibernian (Scotland) and
  Hibernians (Malta); Partizan (Serbia) and Partizani (Albania); Víkingur Gøta (Faroe Islands)
  and Víkingur Reykjavík; FC Santa Coloma and UE Santa Coloma; Universitatea Cluj and CFR Cluj.

Tests (`tests/test_euro_mapping.py`): aliases unique per source; every alias points at a team;
no team has aliases from two countries (each alias's code, through the league rule, must equal
its team's country); every name in the stored openfootball files and in the 2026/27 CSV
resolves; the pairs above.

## Second-source check against Wikipedia (`uv run footy euro-crosscheck`)

Each stored Wikipedia page is read box by box and every box is paired with the openfootball
match of the same date between clubs of the same two countries (names decide between
candidates). Output: `data/europe/openfootball_crosscheck.csv`.

| Check | Result |
| --- | --- |
| openfootball matches paired with a Wikipedia box | **3,371 of 3,371** |
| Regulation score | 3,363 agree, **2 disagree**, 6 not comparable |
| Final score (after extra time) | 3,365 agree, 0 disagree, 6 not comparable |
| Extra-time and penalty flags | 3,368 agree, **3 disagree** |
| Kick-off | 3,321 agree, 29 disagree, 21 not comparable (17 finals, whose own articles give local time, and 4 awarded or cancelled matches without a time) |

**Time basis, verified.** With CET/CEST, openfootball and Wikipedia agree on 3,321 kick-offs,
and Wikipedia's venue-local times agree with them. Five known matches:

| Match | openfootball | UTC | Local time (Wikipedia) |
| --- | --- | --- | --- |
| Liverpool v Paris Saint-Germain, 18 Sep 2018 | 21:00 | 19:00 | 20:00 BST |
| Tottenham v Borussia Dortmund, 13 Feb 2019 | 21:00 | 20:00 | 20:00 GMT |
| Lokomotiv Moscow v Schalke, 3 Oct 2018 | 18:55 | 16:55 | 19:55 MSK |
| Víkingur Reykjavík v Shamrock Rovers, 9 Jul 2024 | 20:45 | 18:45 | 18:45 GMT |
| Kairat v Celtic, 26 Aug 2025 (a UK-time file) | 17:45 | 16:45 | 21:45 Almaty |

So a 21:00 CET slot is 20:00 in the UK, as expected. The exception: in the 2025/26 qualifying
files (`clq.txt`, `elq.txt`, `confq.txt`) 425 of 430 matches were exactly one hour earlier
than Wikipedia's CEST time, whose venue-local times agree with it. Those three files list UK
time, and are read as `Europe/London` (`FILE_TIMEZONES`). No other file shows an offset.

Of the 29 kick-offs that still differ, 2 are the dates corrected below. The other 27 differ
by 5 minutes to 4 hours: 22 qualifying matches and 5 group-stage matches, among them
Manchester United v Valencia (2 Oct 2018) and Marseille v Sporting CP (4 Oct 2022), which
Wikipedia shows starting 5 and 20 minutes late. They keep openfootball's (scheduled) time and
are listed in the CSV.

**Corrections taken from Wikipedia.** Where the two disagree on the regulation score, the
flags or the date, and Wikipedia's own box adds up (its goal list matches its score), the row
takes Wikipedia's values. Each change is an `import_issues` warning
(`corrected_from_wikipedia`) on the openfootball raw file, and the row's `source_ref` says so.

| Match | openfootball | Wikipedia (stored) |
| --- | --- | --- |
| PSV v Juventus, UCL knockout play-off 2nd leg, 19 Feb 2025 | 3-1 | 2-1 after 90, 3-1 after extra time |
| Inter v Barcelona, UCL semi-final 2nd leg, 6 May 2025 | 4-3 | 3-3 after 90, 4-3 after extra time |
| Dinamo City v Atlètic Escaldes, UECL Q2 2nd leg, 31 Jul 2025 | 1-1 after extra time | 1-1, no extra time (Dinamo City through 3-2 on aggregate) |
| Paris Saint-Germain v İstanbul Başakşehir, 2020/21 | 9 Dec 2020 | 8 Dec 2020 (suspended after 14 minutes, completed the next day) |
| Slovácko v Köln, 2022/23 UECL group D | 28 Oct 2022, 13:00 | 27 Oct 2022, 18:45 |

The Dinamo City tie was the only one the tie check flagged; with Wikipedia's score it is
consistent. The two Champions League corrections matter for models: openfootball had left out
the extra time, so its "regulation" scores were the scores after extra time.

**Neutral venues from Wikipedia.** openfootball has no venues. 113 matches become neutral
because the stadium line names another country than the home club's: Ukrainian, Belarusian
and Israeli clubs' home matches since 2022 and 2023 (92, all of them), the COVID-era moves of
2020/21 (for example RB Leipzig v Liverpool and Borussia Mönchengladbach v Manchester City in
Budapest, Atlético Madrid v Chelsea in Bucharest), Qarabağ in Istanbul in 2020, Gibraltar's
Lincoln Red Imps in Faro, Ballkani in Tirana and Víkingur Reykjavík in Helsinki. Welsh clubs
at grounds just over the border (The New Saints' own Park Hall in Oswestry, New Meadow in
Shrewsbury) are not neutral.

## The 2025/26 Europa League and Conference League (`footy.europe.wikipedia_seasons`)

openfootball has these competitions' qualifying rounds but not their main tournaments, so
they come from Wikipedia, under the same rules as 2026/27: CET/CEST kick-offs as each page
states; regulation score from the goal minutes after extra time (all 7 extra-time matches
rebuilt); neutral when the stadium line names another country, and for finals.

| Check | Result |
| --- | --- |
| League phase: 36 teams, 8 matches each (UEL) or 6 (UECL), one per team per matchday, 144 / 108 matches | Passed, both |
| Knockout ties: two legs each, extra time and penalties exactly when level | Passed: 22 ties per competition (8 play-off, 8 round of 16, 4 quarter-final, 2 semi-final) and the final |
| Teams | 20 new Wikipedia aliases, all clubs already in `teams.csv` |

Two semi-final boxes used `{{flagicon}}` instead of `{{fbaicon}}`; both are read.

## Running it

```bash
cd football
uv run footy euro-update                     # store openfootball files, load all three sources
uv run footy euro-update --fetch-wikipedia   # first re-fetch the 2026/27 pages (after a matchday)
uv run footy euro-crosscheck                 # rerun the comparison (report and CSV only)
```

`euro-update` is one logged job (`job_runs.job = "euro_update"`) and is idempotent: a re-run
over the same raw revisions inserts, updates and deletes nothing (checked on 21 Oct 2026:
3,371 + 342 + 824 rows unchanged). It deletes a stored row only when its source no longer
produces it (a rescheduled fixture comes back under a new date) and never after a parse error.
A changed result in a newer raw revision is written to `data_corrections`
(entity `euro_match`). Without the openfootball clone (`--openfootball-dir`,
`$FOOTY_OPENFOOTBALL_DIR`, default `../../openfootball/champions-league`), the stored revisions
are used.
