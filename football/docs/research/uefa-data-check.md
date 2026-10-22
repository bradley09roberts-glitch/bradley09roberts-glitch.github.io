# Adversarial check of `euro_matches`: scores, fixtures and integrity

_Checked 21 October 2026, 01:55–02:50 UTC (02:55–03:50 BST), against `data/footy.sqlite3` as
loaded by `footy euro-update` earlier that night (4,537 rows). Every page used as evidence was
fetched that night and stored in the append-only raw store before it was read; raw file ids
are given below. Nothing in `euro_matches` was changed._

## Verdict

- **No parser bug and no wrong row found.** Nothing needed fixing, so the load was not re-run.
- **15 random played matches** (5 with extra time, 2 of them with penalties): date, home and
  away team, and regulation-time score confirmed by at least two sources other than the one
  the row came from, for all 15. Seven targeted matches (the five rows corrected from Wikipedia,
  one late kick-off and one abandoned match) were also confirmed.
- **The 9 Champions League fixtures of Wednesday 21 October 2026**: home, away and kick-off
  confirmed by German and Spanish Wikipedia, both fetched tonight. English Wikipedia, which the
  rows came from, has not changed since the load.
- **Integrity checks on the whole table** (new module `footy.europe.euro_integrity`), covering
  duplicates, impossible values, teams playing twice on one day (UEFA or domestic), tie logic,
  and league-phase and group shapes: **0 findings**.
- **Season-wide second source.** German Wikipedia was compared with all 22
  competition-seasons, 4,512 of the 4,537 rows. Every compared score and every home/away
  orientation agrees. The only differences are 5 ties where German Wikipedia leaves out the
  extra-time marker; Spanish Wikipedia or RSSSF confirms the extra time in each. The 2026/27
  league phases and the 2025/26 Europa League and Conference League phases were also compared
  with Spanish Wikipedia, date and kick-off included.
- **Not data errors, but worth knowing** (details in [Conventions](#conventions-found)):
  - matches that were suspended and finished the next day are dated by the day they started;
  - a few kick-offs are the actual, delayed time rather than the scheduled one.
- **Terms problem, for the owner:** UEFA.com's terms forbid automated collection
  ([below](#uefacom-terms-owner-decision)). An earlier session stored 146 UEFA.com pages, and
  this check fetched one UEFA PDF before reading the terms.

## Sources

| Source | Used for | Access and terms |
| --- | --- | --- |
| English Wikipedia (CC BY-SA) | The row source for the 2025/26 UEL/UECL main tournaments and for 2026/27. An independent source for the openfootball seasons | MediaWiki API, already stored (raw source `wikipedia`); the 2026/27 UCL league-phase page was re-fetched tonight: unchanged (rev 1381234244) |
| German Wikipedia (CC BY-SA) | Independent: every season page from 2018/19 to 2026/27 | MediaWiki API `action=parse`, one request every 6 s, `Retry-After` honoured; raw source `wikipedia-de`, raw ids 483, 487–501, 525–530 |
| Spanish Wikipedia (CC BY-SA) | Independent: the 2026/27 and 2025/26 league-phase result tables (with each match's UEFA id, date and local kick-off), and the round annexes (match boxes with dates, goal minutes and "(half time, 90 minutes)" scores) | As above; raw source `wikipedia-es`, raw ids 506–512, 514–518 and 520–523 |
| RSSSF, European club results 2020/21–2024/25 | Independent: dates, half-time and full-time scores, ties | No robots.txt (HTTP 404). The pages allow copying with acknowledgement: "You are free to copy this document in whole or part provided that proper acknowledgement is given to the author" (James M. Ross and RSSSF). Raw source `rsssf`, raw ids 484, 485, 502–505 |
| Sportmonks (owner's key) | Tried for the 21 Oct fixtures | The key is on the Football Free Plan. The UCL request returned "No result(s) found … or you don't have access to it via your current subscription" (raw id 482) |

Not used, and why:

| Site | Reason |
| --- | --- |
| uefa.com | Terms §6.2 forbid automated collection (see the owner decision at the end) |
| BBC Sport | robots.txt: "No scraping … No … agentic AI or grounding using BBC content" (stored, raw id 167) |
| ESPN | robots.txt disallows `anthropic-ai`, `ChatGPT-User` and other AI agents. Not used, to respect its intent |
| The Guardian | robots.txt disallows `Claude-User`; its licence note excludes AI uses |
| worldfootball.net | robots.txt disallows `Claude-User` and sets `ai-input` use to `reference` only |
| kicker.de | robots.txt: "The use of robots or other automated means … without the express permission is strictly prohibited" |
| fbref.com | Cloudflare challenge even for robots.txt |
| clubelo.com | API deactivated (standing rule) |

## 1. Random sample of 15 played matches

**How they were chosen.** The sample used `random.Random(20261021)` over all 4,234 `played`
rows:

- 5 matches drawn from the 190 with extra time or penalties, each from a different
  competition-season;
- then one match from each of 10 other competition-seasons, drawn at random.

The result covers 7 of the 9 seasons and all six competitions. 11 rows come from openfootball
and 4 from English Wikipedia.

Kick-off times are UTC. Every source's local time matches the row's UTC time with the right
offset for the date: CEST (UTC+2) until the last Sunday of October, CET (UTC+1) after.

| # | Row (`euro_matches`) | Source | Independent confirmation | Result |
| ---: | --- | --- | --- | --- |
| 1 | UCLQ 2026/27 Q1 leg 2, 14 Jul 2026 15:00, KuPS 0–2 Vardar, ET | en wiki | **es** (rev 174415537): KuPS 2:3 Vardar "(0:0, 0:2)", extra time, 14 Jul 2026 17:00. **de** (rev 271496866): leg 1 Vardar 0:2 KuPS, leg 2 "3:2 n. V." from Vardar's side; second legs 14 and 15 Jul | Confirmed |
| 2 | UCLQ 2025/26 Q1 leg 2, 15 Jul 2025 18:00, Shkëndija 1–1 The New Saints, ET | openfootball | **en** (rev 1368376156): 2–1 aet, goals 18' and 116' o.g. against 38', so 1–1 after 90. **es** (rev 174260526): 2:1 "(1:1, 1:1)", 15 Jul 2025 20:00. **de** (rev 269007023): leg 2 "1:2 n. V." from TNS's side | Confirmed |
| 3 | UCL 2020/21 Round of 16 leg 2, 9 Mar 2021 20:00, Juventus 2–1 Porto, ET | openfootball | **en** (rev 1371414085): 3–2 aet, regulation 2–1 from the goal minutes. **es** (rev 175113944): 3:2 "(2:1, 0:1)", extra time, 9 Mar 2021 21:00 CET. **de** (rev 267839667): Porto–Juventus 2:1, "2:3 n. V.", Juventus out on away goals. **RSSSF**: Porto v Juventus 2-1, 2-3, 4-4 (Feb 17 and Mar 9) | Confirmed |
| 4 | UECLQ 2025/26 Q1 leg 2, 16 Jul 2025 16:00, Auda 2–2 Larne, ET and penalties | openfootball | **en** (rev 1376711297): 2–2 aet, 2–4 on penalties, goals 28', 53' and 50', 66'. **es** (rev 174300851): 2:2 "(2:2, 1:0)", extra time, 16 Jul 2025 18:00. **de** (rev 270349034): Larne 0:0, 2:2, "(4:2 i. E.)" | Confirmed |
| 5 | UECLQ 2024/25 play-off leg 2, 29 Aug 2024 19:00, Puskás Akadémia 1–1 Fiorentina, ET and penalties | openfootball | **en** (rev 1359365040): 1–1 aet, 4–5 on penalties, goals 90+7' pen and 59'. **es** (rev 169208648): 1:1 "(1:1, 0:0)", extra time, 29 Aug 2024 21:00. **de** (rev 269070223): Fiorentina 3:3, "1:1 n. V.", "(5:4 i. E.)". **RSSSF**: Fiorentina v Puskás 3-3, 1-1, 4-4, 5-4p | Confirmed |
| 6 | UECL 2023/24 knockout play-off leg 2, 22 Feb 2024 20:00, Legia 0–3 Molde | openfootball | **en** (rev 1304428946): 0–3, 21:00 CET. **de** (rev 269070268): Molde 3:2, 3:0. **RSSSF**: Molde v Legia 3-2, 3-0 (Feb 15 and 22) | Confirmed |
| 7 | UECL 2026/27 MD1, 15 Oct 2026 19:00, Atalanta 3–1 Pafos | en wiki | **de** (rev 271396601): 15 Oct 2026 21:00, Atalanta 3:1 (3:0) Paphos. **es** (rev 175889288): Atalanta 3 – 1 Pafos, 15 Oct, 21:00 | Confirmed |
| 8 | UEL 2020/21 Group B, 3 Dec 2020 20:00, Molde 3–1 Dundalk | openfootball | **en** (rev 1371415875): 3–1, 21:00 CET. **de** (rev 271383302): 3 Dec 2020, Molde 3:1 Dundalk. **RSSSF**: "Dec 3: Molde FK (2) 3 Dundalk (0) 1" | Confirmed |
| 9 | UECL 2021/22 Round of 16 leg 1, 10 Mar 2022 20:00, PSV 4–4 Copenhagen | openfootball | **en** (rev 1359364947): 4–4, 21:00 CET. **de** (rev 269070331): PSV 4:4, 4:0, first legs on 10 Mar. **RSSSF**: PSV v København 4-4, 4-0, 8-4 | Confirmed |
| 10 | UEL 2026/27 MD1, 17 Sep 2026 19:00, Lillestrøm 1–2 Torreense | en wiki | **de** (rev 271428890): 17 Sep 2026 21:00, Lillestrøm 1:2 (0:1) Torreense. **es** (rev 175925741): 1 – 2, 17 Sep, 21:00 | Confirmed |
| 11 | UCL 2023/24 Group MD6, 12 Dec 2023 20:00, Napoli 2–0 Braga | openfootball | **en** (rev 1371428685): 2–0, 21:00 CET. **de** (rev 267418031): Neapel 2:0 (2:0) Braga. **RSSSF**: "Dec 12: SSC Napoli (2) 2 SC Braga (0) 0" | Confirmed |
| 12 | UEL 2024/25 league phase, 12 Dec 2024 20:00, Rangers 1–1 Tottenham | openfootball | **en** (rev 1379822505): 1–1, 21:00 CET (20:00 UTC). **de** (rev 271383188): matchday 6, 1:1 (0:0). **RSSSF**: "Dec 12: Rangers (0) 1 Tottenham Hotspur (0) 1" | Confirmed |
| 13 | UELQ 2026/27 Q3 Champions Path leg 2, 13 Aug 2026 17:30, Víkingur Reykjavík 3–2 Thun | en wiki | **es** (rev 174927427): 3:2 "(2:2)", 13 Aug 2026 19:30 CEST. **de** (rev 271428890): Thun 3:0 at home, "2:3" away, second legs on 13 Aug | Confirmed |
| 14 | UEL 2022/23 Round of 16 leg 1, 9 Mar 2023 17:45, Union Berlin 3–3 Union Saint-Gilloise | openfootball | **en** (rev 1303953154): 3–3, 18:45 CET. **de** (rev 271383216): 3:3, 0:3. **RSSSF**: 3-3, 0-3, 3-6 (Mar 9 and 16) | Confirmed |
| 15 | UCL 2021/22 Group G, 20 Oct 2021 19:00, Lille 0–0 Sevilla | openfootball | **en** (rev 1376279952): 0–0, 21:00 CEST. **de** (rev 268510193): Lille 0:0 Sevilla. **RSSSF**: "Oct 20: Lille OSC (0) 0 Sevilla FC (0) 0" | Confirmed |

Spanish Wikipedia's ET boxes give the 90-minute score explicitly, so they confirm the
regulation score directly in rows 1–5.

German Wikipedia and RSSSF give only the score after extra time. For those two sources the
ET rows are confirmed through the tie arithmetic: the tie was level after 90 minutes, and
neither score drops below the regulation score.

### Targeted matches

| Match | Row | Evidence | Result |
| --- | --- | --- | --- |
| PSV v Juventus, UCL knockout play-off leg 2, 19 Feb 2025 (corrected from Wikipedia) | 2–1, ET | **es** (rev 173783190): 3:1, regulation "2:1", extra time. **de**: "1:3 n. V." from Juventus's side. RSSSF has 2-1, 1-3, 3-4 without "aet" (see the limits) | Correction confirmed |
| Inter v Barcelona, UCL semi-final leg 2, 6 May 2025 (corrected) | 3–3, ET | **es** (rev 173783254): 4:3, "3:3" after 90, extra time. **de**: "3:4 n. V.". **RSSSF**: 3-3, 3-4, 6-7 aet | Correction confirmed |
| Dinamo City v Atlètic Escaldes, UECLQ Q2 leg 2, 31 Jul 2025 (corrected: no ET) | 1–1, no ET | **de** (rev 270349034): legs 1:2 and 1:1, no "n. V.", 3:2 on aggregate, so extra time was impossible. Spanish Wikipedia has no box for this leg | Correction confirmed |
| Villarreal v Maccabi Tel Aviv, UEL group I, 5 Nov 2020 | 4–0, kick-off 21:10 UTC | en: "originally scheduled at 21:00, but was delayed due to heavy rain", so 22:10 CET. **de** and **RSSSF**: 5 Nov, 4:0 | Confirmed (the actual kick-off, see the conventions) |
| PSG v İstanbul Başakşehir, UCL group H, 8 Dec 2020 | 5–1, 8 Dec, 20:00 UTC | Suspended in the 14th minute on 8 Dec and completed on 9 Dec (en and de footnotes). RSSSF and openfootball date it 9 Dec | Confirmed; dated by its start |
| Slovácko v Köln, UECL group D, 27 Oct 2022 | 0–1, 27 Oct, 16:45 UTC | Suspended after six minutes (weather) and resumed on 28 Oct (en). RSSSF: "Oct 28"; **de**: 0:1 | Confirmed; dated by its start |
| Rijeka v Sparta Prague, UECL MD2, 23 Oct 2025 | 1–0, 23 Oct, 16:45 UTC | Abandoned at half-time; the second half was played on 24 Oct at 16:00 CET (en). **es** (rev 174852619) dates it 24 Oct, 16:00; **de**: 1:0 | Confirmed; dated by its start |

## 2. Champions League fixtures, Wednesday 21 October 2026 (matchday 3)

Sources:

- **en**: English Wikipedia, league-phase article rev 1381234244. It is the rows' source; it
  was re-fetched at 02:20 UTC and is unchanged.
- **de**: German Wikipedia, "UEFA Champions League 2026/27" rev 271496866, raw id 483.
- **es**: Spanish Wikipedia, "Liga de Campeones de la UEFA 2026-27" rev 175925656, raw id 506.

Neither de nor es states its time zone. Their 18:45 and 21:00 are UEFA's standard CEST slots,
and German and Spanish local time is also CEST on 21 October, so they are read as CEST.

| Kick-off (UTC) | UK (BST) | Home | Away | en | de | es |
| --- | --- | --- | --- | --- | --- | --- |
| 16:45 | 17:45 | Como | Manchester United | ✓ | ✓ 18:45 | ✓ 18:45 |
| 16:45 | 17:45 | Lille | Galatasaray | ✓ | ✓ 18:45 | ✓ 18:45 |
| 19:00 | 20:00 | Aston Villa | Viking | ✓ | ✓ 21:00 | ✓ 21:00 |
| 19:00 | 20:00 | Bayern Munich | Arsenal | ✓ | ✓ 21:00 | ✓ 21:00 |
| 19:00 | 20:00 | Club Brugge | Lens | ✓ | ✓ 21:00 | ✓ 21:00 |
| 19:00 | 20:00 | Inter | Shakhtar Donetsk | ✓ | ✓ 21:00 | ✓ 21:00 |
| 19:00 | 20:00 | Real Betis | Feyenoord | ✓ | ✓ 21:00 | ✓ 21:00 |
| 19:00 | 20:00 | Real Madrid | RB Leipzig | ✓ | ✓ 21:00 | ✓ 21:00 |
| 19:00 | 20:00 | Sporting CP | LASK | ✓ | ✓ 21:00 | ✓ 21:00 |

All 9 agree on date, home team, away team and kick-off. All 9 are `scheduled`, not `neutral`,
and carry no score. Real Betis play at La Cartuja and Sporting CP at the Alvalade, both in
their own cities.

Tuesday's 9 matchday-3 results (Liverpool 4–3 Villarreal, PSG 4–2 Barcelona and the rest) also
agree with both sources.

Sportmonks could not serve as a source: the free plan has no Champions League. The UEFA.com
comparison from the earlier session agreed as well, but it is not counted here (see the terms
section).

The same comparison covers every 2026/27 league-phase fixture (section 4). It includes
Thursday 22 October:

- Europa League matchday 3: 18 matches, at 16:45 and 19:00 UTC.
- Conference League matchday 2: 18 matches, at 16:45 and 19:00 UTC, plus Kairat v Panathinaikos
  at 14:30 UTC.
- Union Saint-Gilloise v Hapoel Be'er Sheva is played in Düsseldorf and is marked neutral.
  The earlier build took that from UEFA.com; German Wikipedia says the same ("Merkur
  Spiel-Arena (in Düsseldorf)").

## 3. Integrity checks on the whole table

`uv run python -m footy.europe.euro_integrity [--markdown PATH]` (module
`src/footy/europe/euro_integrity.py`, tests `tests/test_euro_integrity.py`) reads every row and
reports. It changes nothing, and it exits non-zero if any check fails with an error.

| Check | What it looks for | Findings |
| --- | --- | ---: |
| `duplicate_key` | Same competition, season, date and teams twice | 0 |
| `duplicate_pairing_same_day` | The same two teams twice on one date, in any competitions | 0 |
| `repeat_pairing_near_date` | The same two teams again within 3 days (a rescheduled copy) | 0 |
| `repeat_pairing_in_stage` | A pairing twice in one league phase, or the same home and away twice in one group or round | 0 |
| `same_team` | Home team equals away team | 0 |
| `score_missing`, `score_on_unplayed`, `negative_score`, `unknown_status` | Missing or impossible values | 0 |
| `implausible_score` | 10 or more goals for one side (largest present: Bayern 9–2 Dinamo Zagreb, Barcelona 2–8 Bayern) | 0 |
| `played_in_future`, `scheduled_in_past` | Status contradicted by the clock | 0 |
| `team_twice_same_day` | A team in two matches on one date, against all 59,878 domestic `matches` as well | 0 |
| `team_on_consecutive_days` | The same, one day apart | 0 |
| `kickoff_date_mismatch`, `kickoff_hour_unusual` | Kick-off UTC date ≠ match date; kick-off outside 10:00–21:59 UTC or off a 5-minute mark | 0 |
| `extra_time_outside_knockout`, `penalties_without_extra_time` | Flags on league or group matches, first legs, or penalties without extra time | 0 |
| `extra_time_not_level`, `level_without_extra_time` | 1,012 two-legged ties (away goals counted until 2020/21) and 25 single matches. Extra time if and only if level after 90 minutes | 0 |
| `legs_not_reversed`, `legs_out_of_order`, `incomplete_tie` | Leg structure of all 1,014 two-legged ties | 0 |
| `league_phase_teams`, `league_phase_matches`, `league_phase_home_away`, `matchday_repeat` | The 9 league phases: 36 teams, 8 (UCL, UEL) or 6 (UECL) matches each, half at home, once per matchday | 0 |
| `group_shape` | The 100 lettered groups: 4 teams and 12 matches each | 0 |

The checks are known to fire: each one has a test on synthetic rows. The shortest rest found is
two days: Crystal Palace, UECL on Thursday 18 Dec 2025, then at Leeds on Saturday 20 Dec 2025.

## 4. Season-wide comparison with German and Spanish Wikipedia

This went beyond the brief. A comparison script (kept out of the repository) joined German
Wikipedia's `{{OneLegResult}}` and `{{TwoLegResult}}` rows to the table:

- **Single matches** (groups, league phases): matched one-to-one by team names.
- **Two-legged ties**: matched to the leg-1 row by team names.
- **Scores**: leg scores were compared as written. For an ET leg, German Wikipedia's score
  (after extra time) must not be below the row's regulation score.
- **Flags**: extra time and penalties were compared with German Wikipedia's "n. V." and
  "i. E." markers.

| Rows | Compared | Agree |
| --- | ---: | --- |
| Group and league-phase matches | 2,484 of 2,484 (5 joined by hand, e.g. "OGC Nizza") | All scores and orientations. The two awarded matches agree too ("kampflos 3:0", "0:3 (Wertung)") |
| Two-legged ties (2,028 legs) | 1,014 of 1,014 (10 joined by hand) | All leg scores and orientations of the 1,012 played ties (the awarded Dnipro-1 v Puskás Akadémia tie and the cancelled RB Leipzig v Spartak Moscow tie were not compared). Extra-time flags: all but 6 (below) |
| Single knockout matches (19 finals; the 2019/20 Lisbon quarter-finals and semi-finals) | 0 of 25 | Not compared: the script joins only group, league-phase and two-legged rows. The openfootball ones were paired with English Wikipedia in the earlier cross-check |

Ties where German Wikipedia and the table disagree on extra time:

| Tie (leg 2) | Table | German Wikipedia | Settled by | Who is right |
| --- | --- | --- | --- | --- |
| CFR Cluj v Lugano, UELQ Q2, 31 Jul 2025 | 0–0, ET | "0:1", no "n. V." | es (rev 168839458): 1:0 "(0:0)", extra time; en: 96' goal | Table |
| Braga v Levski Sofia, UELQ Q2, 31 Jul 2025 | 0–0, ET | "0:1", no "n. V." | es: 1:0 "(0:0, 0:0)", extra time; en: 104' | Table |
| Hibernian v Midtjylland, UELQ Q2, 31 Jul 2025 | 0–0, ET | "2:1", no "n. V." | es: 1:2 "(0:0, 0:0)", extra time; en: 105+2', 94', 119' | Table |
| AEK Athens v Aris Limassol, UECLQ Q3, 14 Aug 2025 | 1–1, ET | "1:3", no "n. V." | es (rev 173815130): 3:1 "(1:1, 0:0)", extra time | Table |
| Legia v Molde, UECL Round of 16, 13 Mar 2025 | 1–0, ET | "0:2", no "n. V." | RSSSF: 3-2, 0-2, 3-4 aet | Table |
| Fiorentina v Viktoria Plzeň, UECL quarter-final, 18 Apr 2024 | 0–0, ET | "0:2 n&nbsp;V." | Typo in the marker, which the script missed | Same |

Kick-offs were compared wherever the script could read a source's times: league phases only.

- **German Wikipedia:** the 2024/25 Champions League, and all three competitions in 2025/26
  and 2026/27. 932 of 936 agree. Its 2024/25 Europa League and Conference League tables use
  another layout and were not read.
- **Spanish Wikipedia:** all 396 of 2026/27 agree (joined by UEFA match id), and 250 of 251
  for the 2025/26 Europa League and Conference League. Rijeka v Sparta is dated 24 Oct there,
  so it doesn't join.
- **Differ (German Wikipedia shows the original time; the table has the time actually used):**
  - Bayern v Benfica, 6 Nov 2024: delayed to 21:15 CET for fans held up by transport;
  - Feyenoord v Panathinaikos, 23 Oct 2025: moved to 21:00 the day before (weather);
  - AZ v Slovan Bratislava, 23 Oct 2025: moved to 18:45 (weather);
  - Utrecht v Genk, 22 Jan 2026: delayed to 21:50 for safety reasons. Spanish Wikipedia also
    shows 21:00 here.

## Conventions found

These are not errors, but anyone using the table should know them:

1. **Suspended matches are dated by the day they started.** There are three:
   - PSG v İstanbul Başakşehir, 8 Dec 2020, finished on 9 Dec;
   - Slovácko v Köln, 27 Oct 2022, finished on 28 Oct;
   - Rijeka v Sparta Prague, 23 Oct 2025, finished on 24 Oct.

   RSSSF and openfootball (and Spanish Wikipedia for Rijeka) date them by the day they
   finished. `kickoff_utc` is the original kick-off.
2. **Some `kickoff_utc` values are the actual, late kick-off.** Where the source shows the
   delayed time, the row has it: Villarreal v Maccabi Tel Aviv (22:10 CET, rain), Bayern v
   Benfica (21:15) and Utrecht v Genk (21:50).
   - Fixtures moved forward the day before are new scheduled times, not delays: Feyenoord v
     Panathinaikos and AZ v Slovan Bratislava.
   - Other openfootball rows keep the scheduled time where Wikipedia differs by 5 minutes to
     4 hours (27 rows). For example, Manchester United v Valencia (2 Oct 2018) started 5
     minutes late.
   - So [uefa-matches.md](../europe/uefa-matches.md) ("kick-off times are the scheduled ones")
     holds for most rows, not all. It matters only if a kick-off is used as a decision time
     for prices, which no UEFA row is.
3. **Extra-time markers on secondary sources aren't reliable.**
   - German Wikipedia left out "n. V." on 5 of the 1,014 ties.
   - RSSSF's season pages often omit "aet": PSV v Juventus 2025 and Juventus v Porto 2021,
     for example.
   - Neither should be the only source for a flag.

## Limits

- **Regulation scores of extra-time matches.** Of the 190 ET matches, only the 11 in this
  report had their regulation score checked directly, against Spanish Wikipedia's 90-minute
  score.
  - For the other openfootball ET matches it rests on openfootball plus English Wikipedia's
    goal minutes, which agreed in the earlier cross-check.
  - For the 2025/26 Europa League and Conference League and for 2026/27 it rests on English
    Wikipedia's goal minutes alone. German Wikipedia only confirms that the tie arithmetic holds.
- **Dates of 2026/27 qualifying matches** were checked only in the sample: German Wikipedia
  gives round dates, not match dates.
- **No source has been fully independent of UEFA.** All of them transcribe UEFA's own data.
  Agreement shows the transcription is right, not that UEFA's record is.

## UEFA.com terms (owner decision)

UEFA's General Terms and Conditions (last updated 18 March 2025; read on 21 Oct 2026 with
the WebFetch reader), §6.2, say:

- "you are prohibited from using automated tools, such as robots, spiders, or scripts, to
  scrape or collect Content";
- "You are not allowed to systematically collect, compile, or gather the Content in any way";
- "you must not use the Content to develop or train any software, model, algorithm, or AI
  tool".

robots.txt allows `/*/match/`, which is what the earlier session relied on.

**What is already stored:**

- The 2026/27 build fetched 146 UEFA.com files (raw source `uefa`): 144 match pages and two
  robots.txt responses.
- This check fetched one UEFA match-report PDF (raw id 480, PSV v Juventus) before reading the
  terms. It was not used as evidence, and nothing else was fetched from UEFA.

The raw store is append-only, so nothing was deleted.

**Update, 22 Oct 2026 (audit M16):** since then nothing reads the stored UEFA.com pages. The
build no longer writes the UEFA cross-check, and the neutral flag below comes from German
Wikipedia (`src/footy/europe/venue_overrides.csv`, citing oldid 271428890). What follows
describes the build before that change.

The stored pages still feed the 2026/27 build in two ways:

- the kick-off cross-check in `uefa_2627_crosscheck.csv`;
- one neutral flag: Union Saint-Gilloise v Hapoel Be'er Sheva, in Düsseldorf. German
  Wikipedia gives the same venue.

**Recommendation:** don't use `footy euro-update --fetch-uefa` again. It is off by default.
Then decide whether to keep or purge the stored UEFA files. If they are purged:

- take the neutral flag for that one match from German Wikipedia;
- drop the UEFA column from the 2026/27 cross-check. German and Spanish Wikipedia now cover
  the same ground.

## Reproducing

```bash
cd football
uv run python -m footy.europe.euro_integrity            # section 3
uv run pytest -q tests/test_euro_integrity.py
```

The evidence pages are in the raw store: `wikipedia-de` ids 483, 487–501 and 525–530,
`wikipedia-es` ids 506–512, 514–518 and 520–523, `rsssf` ids 484, 485 and 502–505, and
Sportmonks id 482. Each is listed in `fetch_log`.

The sample comes from `random.Random(20261021)`:

1. Shuffle the 190 extra-time or penalty rows (`rng.sample`) and keep the first 5 from
   distinct competition-seasons.
2. Shuffle the remaining competition-seasons (`rng.shuffle`).
3. Draw one row (`rng.choice`) from each of the first 10.
