"""SYNTHETIC test data. Fictional teams and made-up numbers in football-data's column layout.

Used only by tests; never imported into a real database or counted in any metric.
"""

from footy.reference import AliasRow, TeamRow

TEAMS = [
    TeamRow("eng-testtown", "Testtown", "ENG"),
    TeamRow("eng-sampleton", "Sampleton", "ENG"),
    TeamRow("eng-mockford", "Mockford", "ENG"),
]
ALIASES = [AliasRow("football-data", t.name, t.team_key) for t in TEAMS]
REFERENCE = (TEAMS, ALIASES)

HEADER = (
    "Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,HTHG,HTAG,HTR,HS,AS,HST,AST,HC,AC,"
    "B365H,B365D,B365A,PSCH,PSCD,PSCA,B365>2.5,B365<2.5,PC>2.5,PC<2.5"
)
# Saturday 15:00 (BST), Wednesday 19:45 (GMT), Friday 20:00 (BST)
ROWS = [
    "E0,13/09/2025,15:00,Testtown,Sampleton,2,1,H,1,0,H,12,8,5,3,6,2,"
    "2.10,3.40,3.50,2.05,3.50,3.70,1.90,1.95,1.88,2.02",
    "E0,03/12/2025,19:45,Sampleton,Mockford,0,0,D,0,0,D,9,10,2,4,3,5,"
    "2.60,3.20,2.80,2.55,3.25,2.90,2.20,1.70,2.25,1.68",
    "E0,19/09/2025,20:00,Mockford,Testtown,1,3,A,0,2,A,7,15,3,8,4,7,"
    "4.00,3.60,1.90,4.10,3.70,1.92,1.80,2.05,1.83,2.07",
]


def csv_bytes(rows=None, header=HEADER, encoding="utf-8-sig") -> bytes:
    lines = [header, *(ROWS if rows is None else rows)]
    return ("\r\n".join(lines) + "\r\n").encode(encoding)


def replace_field(row: str, column: str, value: str, header: str = HEADER) -> str:
    cols = header.split(",")
    parts = row.split(",")
    parts[cols.index(column)] = value
    return ",".join(parts)
