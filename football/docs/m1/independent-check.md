# M1 spot-check, part 2: the same 20 matches against independent sources

_Checked 2026-10-19 against public sources other than football-data.co.uk. Local kick-off
times were converted to UK time. Companion to [spot-check.md](spot-check.md), which compares
the database with the raw source rows._

**Result: 20/20 dates and 20/20 scores confirmed; 18/18 recorded kick-off times match the
scheduled UK time.** The two 2018/19 matches have no time in the source.

| # | Match | Date | Score | Kick-off (UK) | Source(s) | Note |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | Paderborn v Dortmund | ✓ | ✓ | ✓ | [maxifoot](https://www.maxifoot.fr/match/paderborn-borussia-dortmund-20200531.htm), [Sky Sports](https://www.skysports.com/football/news/11899/11998121/paderborn-1-6-borussia-dortmund-jadon-sancho-nets-first-career-hat-trick) | 18:00 CEST |
| 2 | Real Madrid v Betis | ✓ | ✓ | ✓ | [ESPN](https://www.espn.com/soccer/match/_/gameId/550501) | |
| 3 | Montpellier v Clermont | ✓ | ✓ | ✓ | [maxifoot](https://www.maxifoot.fr/match/montpellier-clermont-20211205.htm) | 15:00 CET |
| 4 | Werder Bremen v Bayern Munich | ✓ | ✓ | not in source | [FC Bayern](https://fcbayern.com/en/news/matchreports/2018/12/match-report-bundesliga-md13-werder-bremen---fc-bayern-011218) | |
| 5 | Granada v Huesca | ✓ | ✓ | ✓ | [maxifoot](https://www.maxifoot.fr/match/grenade-huesca-20201206.htm), [ESPN](https://www.espn.in/football/match/_/gameId/582091) | |
| 6 | Auxerre v Lens | ✓ | ✓ | ✓ | [maxifoot](https://www.maxifoot.fr/match/auxerre-lens-20241214.htm) | |
| 7 | Pisa v Spal | ✓ | ✓ | ✓ | [Wikipedia](https://en.wikipedia.org/wiki/2021%E2%80%9322_Pisa_SC_season), [Corriere dello Sport](https://www.corrieredellosport.it/live/partita/pisa-spal-2236727) | |
| 8 | Stuttgart v Bayern Munich | ✓ | ✓ | ✓ | [Goal](https://www.goal.com/en/news/stuttgart-vs-bayern-munich-live-stream-tv-where-to-watch/blt1ca1bad74f0daa0c), [ESPN](https://africa.espn.com/football/match/_/gameId/638641) | |
| 9 | Cartagena v Ferrol | ✓ | ✓ | ✓ | [LaLiga](https://www.laliga.com/en-GB/match/temporada-2023-2024-laliga-hypermotion-fc-cartagena-racing-club-ferrol-30) | |
| 10 | Everton v Wolves | ✓ | ✓ | ✓ | [evertonia](https://evertonia.com/seasons/2024-25/matches/Wolves(H).php) | |
| 11 | Villarreal v Barcelona | ✓ | ✓ | ✓ | [LaLiga](https://www.laliga.com/en-US/match/temporada-2023-2024-laliga-easports-villarreal-cf-fc-barcelona-3), [ESPN](https://global.espn.com/football/match/_/gameId/674039) | |
| 12 | Udinese v Inter | ✓ | ✓ | ✓ | [Goal](https://www.goal.com/en-gb/news/udinese-vs-inter-live-stream-tv-channel-kick-off-time-where-to-watch/blt7e16c9450ae1e92e), [Sky Sport](https://sport.sky.it/calcio/serie-a/partite/2023/giornata-31/udinese-inter) | |
| 13 | Bielefeld v Kaiserslautern | ✓ | ✓ | ✓ | [bundesliga.com](https://www.bundesliga.com/en/2bundesliga/matchday/2025-2026/16/dsc-arminia-bielefeld-vs-1-fc-kaiserslautern) | |
| 14 | Brentford v Man City | ✓ | ✓ | ✓ | [Brentford FC](https://www.brentfordfc.com/news/2021/december/next-up-manchester-city) | |
| 15 | Hull v Middlesbrough | ✓ | ✓ | ✓ | [Goal](https://www.goal.com/en-gb/news/watch-hull-city-vs-middlesbrough-championship-game-live-stream-tv-channel-and-start-time/blt98e2e6ca2315e650), [ESPN](https://www.espn.com/soccer/match/_/gameId/707913) | |
| 16 | Napoli v Cagliari | ✓ | ✓ | ✓ | [Sky Sport](https://sport.sky.it/calcio/serie-a/partite/2021/giornata-6/napoli-cagliari/risultato-gol) | |
| 17 | Lazio v Genoa | ✓ | ✓ | ✓ | [Goal](https://www.goal.com/it/notizie/lazio-genoa-dove-vederla-sky-o-dazn-canale-tv-diretta-streaming-formazioni-della-partita/1m78bxf808yzi10vlh4mnyt507) | |
| 18 | Montpellier v St Etienne | ✓ | ✓ | not in source | [maxifoot](https://www.maxifoot.fr/match/montpellier-etienne-20180825.htm) | |
| 19 | Auxerre v Quevilly Rouen | ✓ | ✓ | ✓ | [Wikipedia](https://en.wikipedia.org/wiki/2021%E2%80%9322_AJ_Auxerre_season) | |
| 20 | Brighton v Liverpool | ✓ | ✓ | scheduled ✓ | [Goal](https://www.goal.com/en-gb/lists/explained-why-liverpool-s-premier-league-match-with-brighton-has-been-delayed/blt69b11a91b01cf3d0), [ESPN](https://www.espn.com/soccer/match/_/gameId/740898) | Scheduled 12:30; actually kicked off at 12:45 after a road accident |

**What this establishes:** football-data's `Time` column is the **scheduled** kick-off in UK
local time (D-005), not the actual start.

**Awarded result confirmed (D-018).** Union Berlin v Bochum, 14 Dec 2024, finished 1–1 on the
pitch, after a lighter thrown from the crowd hit Bochum's goalkeeper. The DFB sports court
ruled it a 0–2 win for Bochum on 9 Jan 2025; the ruling was upheld on appeal and by
arbitration in April 2025
([bundesliga.com](https://www.bundesliga.com/de/bundesliga/news/vfl-bochum-union-berlin-schiedsgericht-urteil-drei-punkte-feuerzeug-wurf-31928)).
The importer's `result_without_stats` rule flagged exactly this match.
