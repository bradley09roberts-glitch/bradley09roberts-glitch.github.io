"""SYNTHETIC test data. Fictional teams and made-up numbers in football-data's "extra leagues"
layout (new/{code}.csv: Country, League, Season, Date, Time, Home, Away, HG, AG, Res, ...).

Used only by tests; never imported into a real database or counted in any metric. The
"Austria" country value is there only because the parser checks it against the file's code.
"""

from footy.reference import AliasRow, TeamRow

TEAMS = [
    TeamRow("aut-alpendorf", "Alpendorf", "AUT"),
    TeamRow("aut-bergstadt", "Bergstadt", "AUT"),
    TeamRow("aut-seeburg", "Seeburg", "AUT"),
    TeamRow("aut-talheim", "Talheim", "AUT"),
]
# "Alpendorf FC" is a second spelling of Alpendorf, as football-data sometimes switches names.
ALIASES = [AliasRow("football-data", t.name, t.team_key) for t in TEAMS] + [
    AliasRow("football-data", "Alpendorf FC", "aut-alpendorf")
]
REFERENCE = (TEAMS, ALIASES)

HEADER = (
    "Country,League,Season,Date,Time,Home,Away,HG,AG,Res,PSCH,PSCD,PSCA,MaxCH,MaxCD,MaxCA,"
    "AvgCH,AvgCD,AvgCA"
)
ODDS = "2.10,3.40,3.50,2.20,3.50,3.60,2.05,3.30,3.40"


def row(season, day, time, home, away, hg, ag, res=None, country="Austria", league="Bundesliga"):
    res = res or ("H" if hg > ag else "A" if ag > hg else "D")
    return f"{country},{league},{season},{day},{time},{home},{away},{hg},{ag},{res},{ODDS}"


# 2017/18 (before the first season in scope), then 2025/26 with a repeat meeting (split
# league) and a play-off against a lower-division visitor (Talheim).
ROWS = [
    row("2017/2018", "05/08/2017", "16:00", "Alpendorf", "Bergstadt", 1, 0),
    row("2025/2026", "02/08/2025", "16:00", "Alpendorf", "Bergstadt", 2, 1),  # BST
    row("2025/2026", "09/08/2025", "13:30", "Bergstadt", "Seeburg", 0, 0),
    row("2025/2026", "16/08/2025", "18:30", "Seeburg", "Alpendorf", 1, 3),
    row("2025/2026", "06/12/2025", "16:00", "Bergstadt", "Alpendorf", 2, 2),  # GMT
    row("2025/2026", "13/12/2025", "16:00", "Alpendorf", "Seeburg", 4, 0),
    row("2025/2026", "20/12/2025", "16:00", "Seeburg", "Bergstadt", 1, 2),
    row("2025/2026", "07/03/2026", "16:00", "Alpendorf", "Bergstadt", 0, 1),  # repeat meeting
    row("2025/2026", "28/05/2026", "18:30", "Talheim", "Seeburg", 1, 1),  # play-off
    row("2025/2026", "31/05/2026", "16:00", "Seeburg", "Talheim", 2, 0),  # play-off
]
# A calendar-year season, as in Norway, Sweden, Finland and Ireland.
CALENDAR_ROWS = [
    row("2025", "29/03/2025", "16:00", "Alpendorf", "Bergstadt", 1, 1),
    row("2025", "05/04/2025", "16:00", "Bergstadt", "Alpendorf", 0, 2),
]


def csv_bytes(rows=None, header=HEADER, encoding="utf-8-sig") -> bytes:
    lines = [header, *(ROWS if rows is None else rows)]
    return ("\r\n".join(lines) + "\r\n").encode(encoding)
