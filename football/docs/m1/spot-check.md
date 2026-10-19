# M1 spot-check: imported matches vs source rows

_Run 2026-10-19 16:09 UK time by `uv run footy spot-check -n 20 --seed 20261019`. Sample: 3 matches per top division and 1 per second tier, seeded._

Each row was re-read from the stored raw CSV revision with Python's `csv` module,
independently of the importer, and every stored field and price compared with it.

| # | Division | Season | Date | Kick-off (UK) | Match | Score | Values compared | Of which prices | Mismatches |
| ---: | --- | --- | --- | --- | --- | --- | ---: | ---: | ---: |
| 1 | D1 | 2019/20 | 2020-05-31 | 17:00 | Paderborn v Dortmund | 1–6 | 52 | 30 | 0 |
| 2 | SP1 | 2019/20 | 2019-11-02 | 20:00 | Real Madrid v Betis | 0–0 | 52 | 30 | 0 |
| 3 | F1 | 2021/22 | 2021-12-05 | 14:00 | Montpellier v Clermont | 1–0 | 52 | 30 | 0 |
| 4 | D1 | 2018/19 | 2018-12-01 | – | Werder Bremen v Bayern Munich | 1–2 | 34 | 14 | 0 |
| 5 | SP1 | 2020/21 | 2020-12-06 | 13:00 | Granada v Huesca | 3–3 | 52 | 30 | 0 |
| 6 | F1 | 2024/25 | 2024-12-14 | 18:00 | Auxerre v Lens | 2–2 | 62 | 40 | 0 |
| 7 | I2 | 2021/22 | 2021-08-22 | 19:30 | Pisa v Spal | 1–0 | 22 | 0 | 0 |
| 8 | D1 | 2022/23 | 2023-03-04 | 17:30 | Stuttgart v Bayern Munich | 1–2 | 52 | 30 | 0 |
| 9 | SP2 | 2023/24 | 2024-03-09 | 20:00 | Cartagena v Ferrol | 2–1 | 22 | 0 | 0 |
| 10 | E0 | 2024/25 | 2024-12-04 | 19:30 | Everton v Wolves | 4–0 | 62 | 40 | 0 |
| 11 | SP1 | 2023/24 | 2023-08-27 | 16:30 | Villarreal v Barcelona | 3–4 | 52 | 30 | 0 |
| 12 | I1 | 2023/24 | 2024-04-08 | 19:45 | Udinese v Inter | 1–2 | 52 | 30 | 0 |
| 13 | D2 | 2025/26 | 2025-12-13 | 12:00 | Bielefeld v Kaiserslautern | 0–0 | 22 | 0 | 0 |
| 14 | E0 | 2021/22 | 2021-12-29 | 20:15 | Brentford v Man City | 0–1 | 52 | 30 | 0 |
| 15 | E1 | 2024/25 | 2025-01-01 | 17:30 | Hull v Middlesbrough | 0–1 | 22 | 0 | 0 |
| 16 | I1 | 2021/22 | 2021-09-26 | 19:45 | Napoli v Cagliari | 2–0 | 52 | 30 | 0 |
| 17 | I1 | 2020/21 | 2021-05-02 | 11:30 | Lazio v Genoa | 4–3 | 52 | 30 | 0 |
| 18 | F1 | 2018/19 | 2018-08-25 | – | Montpellier v St Etienne | 0–0 | 34 | 14 | 0 |
| 19 | F2 | 2021/22 | 2022-02-26 | 18:00 | Auxerre v Quevilly Rouen | 1–0 | 22 | 0 | 0 |
| 20 | E0 | 2025/26 | 2026-03-21 | 12:30 | Brighton v Liverpool | 2–1 | 52 | 30 | 0 |

**Result: 874 values compared across 20 matches, 0 mismatches.**
