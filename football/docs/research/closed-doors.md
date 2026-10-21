# COVID-19 closed-doors and limited-crowd periods, 2019/20 and 2020/21

_Researched 2026-10-21. Every source page was fetched that day and stored in the append-only raw
store before it was read (details at the end)._

The data is in [`src/footy/reference/closed_doors.csv`](../../src/footy/reference/closed_doors.csv):
40 rows covering the ten core divisions (E0, E1, SP1, SP2, I1, I2, D1, D2, F1, F2).

## How to read the file

| Column | Meaning |
| --- | --- |
| `division` | football-data code, as in `reference.py` |
| `start_date`, `end_date` | Inclusive local calendar dates. Inside a season, a boundary is the date the rule changed. At a season's edges it is the first or last league match played under the rule |
| `crowd_status` | `none` means every league match in the period was played without spectators (staff, media and a few invited guests aside). `limited` means crowds were restricted for the whole division or for part of it: a cap, or some grounds closed while others were open |
| `cap_note` | The cap, what applied where, and any uncertainty. Notes starting `PARTIAL` or `UNCERTAIN` should be read before use |
| `source_url` | One or more URLs, separated by a single space. Wikipedia links pin the exact revision that was read (`oldid`) |

A date with no row means normal crowds. That holds for every match in the ten divisions up to
and including 7 March 2020, apart from Serie B (see below).

Checked against the `matches` table: every 2020/21 match in the ten divisions falls inside a
row, and so does every 2019/20 match from 10 March 2020. The row counts match the sources'
totals: 83 Bundesliga and 81 2. Bundesliga matches behind closed doors in 2019/20, and 110 La
Liga matches after the restart.

## Summary by league

| League | 2019/20 | 2020/21 |
| --- | --- | --- |
| Premier League | Closed from the restart, 17 Jun–26 Jul | Closed 12 Sep–1 Dec. Limited 2–30 Dec: tiers allowed up to 2,000 (tier 2) or 4,000 (tier 1), and tier 3 clubs stayed closed. Closed 31 Dec–16 May. Limited 17–23 May: up to 10,000 or 25% of capacity |
| Championship | Closed from the restart, 20 Jun–22 Jul | Closed apart from two 1,000-fan pilots on 19 Sep. Limited 2–30 Dec under the tiers. Closed 31 Dec to the end of the regular season (8 May) |
| La Liga | Eibar v Real Sociedad closed on 10 Mar. Closed from the restart, 11 Jun–19 Jul | Closed until 15 May. Limited 16–23 May: phase-1 regions only, 30% of capacity, at most 5,000. Five matches had fans |
| Segunda División | Closed from the restart, 10 Jun–7 Aug | Closed until 14 May. Limited 15–30 May under the same rule; eight matches had fans |
| Serie A | Closed 8–9 Mar. Closed from the restart, 20 Jun–2 Aug | Limited 19 Sep–25 Oct (up to 1,000). Closed 26 Oct to the end of the season |
| Serie B | Northern grounds closed in rounds 26–27 (28 Feb–4 Mar). All closed 5–9 Mar. Closed from the restart, 17 Jun–31 Jul | Uncertain, at most 1,000, 25 Sep–25 Oct. Closed from 26 Oct |
| Bundesliga | Gladbach v Köln closed on 11 Mar. Closed from the restart, 16 May–27 Jun | Limited 18 Sep–1 Nov: up to 20% of capacity, set by each local health authority, and 22 of 54 matches closed. Closed 2 Nov–21 May. Limited on the final day, 22 May (most: 2,000 at Union) |
| 2. Bundesliga | Closed from the restart, 16 May–28 Jun | Limited 18 Sep–1 Nov under the same rule. Closed from 2 Nov to the end of the season (250 fans at Hamburg on the final day) |
| Ligue 1 | Never behind closed doors: round 28 (6–8 Mar) had crowds, then the season was abandoned | Limited 21 Aug–29 Oct (5,000 cap). Closed from 30 Oct to the end of the season |
| Ligue 2 | Lens v Orléans closed on 9 Mar, then abandoned | Limited 22 Aug–29 Oct (5,000 cap). Closed from 30 Oct to the end of the season |

## Sources

Primary or official sources were used for the rules wherever they could be fetched. Wikipedia
supplied dates and per-match attendances:

- **England:** GOV.UK *COVID-19 Winter Plan* (tier caps from 2 Dec 2020); GOV.UK *COVID-19
  Response – Spring 2021 (Summary)* (Step 3 caps); the en.wikipedia 2019–20 and 2020–21
  Premier League and EFL Championship articles. The 2020–21 Championship article gives the
  dates, club by club, that grounds closed in December.
- **Spain:** the LaLiga announcement of 14 May 2021 (the 30% / 5,000 rule, the phase-1 regions
  and the first matches); es.wikipedia season articles, whose matchday tables give every
  match's attendance. The tables were parsed in full: all 380, 462, 380 and 462 matches.
- **Italy:** FIGC news of 4 March 2020 (Serie A behind closed doors); open.online, Italpress
  and LaPresse of 19–20 September 2020 (1,000 spectators, Serie A only); Calcio e Finanza of
  25 October 2020 (the text of the decree banning the public); it.wikipedia *Gestione della
  pandemia di COVID-19 in Italia* (the sequence of decrees and the 1 June 2021 reopening);
  it.wikipedia and en.wikipedia season articles.
- **Germany:** t-online/dpa of 28 October 2020 (the federal–state decision that November would
  be without spectators); de.wikipedia and en.wikipedia season articles (the 20% concept of
  15 September 2020, closed-match counts, the final-day exceptions).
- **France:** fr.wikipedia season articles (the 5,000 cap to 30 October 2020, attendance per
  round, the 2019/20 timeline); Daily Mercato's list of closed-doors matches for Lens v Orléans.

## Uncertainties and known gaps

1. **England, December 2020.** Fans were allowed club by club, depending on each ground's tier
   and the tier changes of 16, 19 and 23 December. The file records the division as `limited`
   and does not list each club. The end date (30 Dec) follows the Championship article's
   statement that tier 2 was removed on 30 December. The only Premier League match that day
   was at a tier 3 ground.
2. **England, May 2021.** en.wikipedia says "10,000 spectators or 25% capacity, whichever was
   higher". The GOV.UK roadmap says whichever is **lower**, and the file follows GOV.UK.
3. **Bundesliga matchday 6 (30 Oct–2 Nov 2020).** de.wikipedia contradicts itself: the season
   text puts the general exclusion from matchday 7, and the stadium table says from
   matchday 6. The boundary is 2 Nov, when the November measures began; the decision was
   announced on 28 Oct, although de.wikipedia says 29 Oct. Hoffenheim v Union on 2 Nov falls
   in the `none` row.
4. **Serie A, 19 September 2020.** The national 1,000 allowance started on 20 Sep. On 19 Sep,
   Verona v Roma was invitation-only, and Fiorentina v Torino was not checked. Both are in the
   `limited` row.
5. **Serie B.** In rounds 26–27 of 2019/20 only grounds in northern Italy were closed, so the
   row is marked `PARTIAL`. The affected matches were not listed one by one. For 25 Sep–25 Oct
   2020, the national 1,000 allowance covered Serie A only. Regional orders and the decree in
   force from 13 Oct may have let some Serie B grounds admit up to 1,000, so the row is
   marked `UNCERTAIN`.
6. **Ligue 1 and Ligue 2, autumn 2020.** The 5,000 cap was national. Prefects set lower local
   caps in some areas, which were not checked one by one; round totals show crowds shrinking.
   fr.wikipedia says the first closed-doors Ligue 2 match was on 8 March 2020 without naming
   it. The only Ligue 2 match after 7 March was Lens v Orléans on 9 March, which Daily Mercato
   lists as closed.
7. **Welsh clubs** in the Championship (Cardiff, Swansea) followed Welsh rules, which were not
   checked.
8. **Not covered:**
   - play-offs, relegation play-offs and cups (not league matches in football-data);
   - the 16 European leagues of D-022;
   - the 2021/22 restrictions, such as Germany's caps and closed doors from December 2021 and
     Italy's and France's caps in January 2022. These are a known gap if 2021/22 matches are
     ever treated the same way.

## Raw store record

All responses are in `data/raw` and in `raw_files`:

| Source | Raw store `source` | Logical keys |
| --- | --- | --- |
| Wikipedia, MediaWiki `action=parse` JSON | `wikipedia` | `research/closed-doors/...`: 11 en.wikipedia pages under the bare prefix; further en, de, es, it and fr pages under `research/closed-doors/<lang>/...` |
| GOV.UK, LaLiga, FIGC, open.online, Italpress, LaPresse, Calcio e Finanza, t-online, Daily Mercato | `web` | `research/closed-doors/<name>` |

Fetching was polite throughout:

- one request every 2 s, with the project's User-Agent;
- robots.txt was checked for every non-Wikipedia host, and each allows the page.

One fetch failed: the West Ham club page on the 5 Dec 2020 return of fans (HTTP 500). It is not
cited. Web searches were used only to find pages; nothing in the file rests on a search
snippet.
