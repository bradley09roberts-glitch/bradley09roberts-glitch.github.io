"""Team to Wikidata mapping, the query client and the committed reference files. The query
service is faked with httpx.MockTransport (no network)."""

import csv
import json
import re
from datetime import date
from urllib.parse import parse_qs

import httpx
import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from footy.europe.wiki_fetch import USER_AGENT
from footy.media import mapping
from footy.media.facts import MEDIA_JSON, read_facts, team_entry
from footy.media.mapping import MappingRow, ScopeTeam, build_mapping, normalise
from footy.media.wdqs import WDQS_URL, WdqsCacheMiss, WdqsClient, WdqsRateLimited
from footy.models import EuroMatch, Match, Team, TeamAlias
from footy.reference import REFERENCE_DIR

ENTITY = "http://www.wikidata.org/entity/"
UK, SPAIN, FRANCE, MONACO, POLAND, NETHERLANDS, UKRAINE, MALTA = (
    "Q145", "Q29", "Q142", "Q235", "Q36", "Q55", "Q212", "Q233"
)  # fmt: skip
CLUB, WOMEN, DISAMBIGUATION, MULTISPORT = "Q476028", "Q51481377", "Q4167410", "Q2385804"
MENS_TEAM = "Q103229495"
PL, LALIGA, LIGUE1, EKSTRAKLASA, I_LIGA, EREDIVISIE, WOMENS_LEAGUE = (
    "Q9448", "Q324867", "Q13394", "Q44763", "Q163046", "Q167541", "Q900"
)  # fmt: skip
LABELS = {
    PL: "Premier League",
    LALIGA: "La Liga",
    LIGUE1: "Ligue 1",
    EKSTRAKLASA: "Ekstraklasa",
    I_LIGA: "I liga",
    EREDIVISIE: "Eredivisie",
    WOMENS_LEAGUE: "Ukrainian Women's League",
}

# A tiny Wikidata: qid -> facts.
ITEMS = {
    "Q9617": {
        "en": "Arsenal F.C.",
        "aliases": ["The Gunners", "Arsenal Women"],
        "P31": [CLUB],
        "P118": [PL],
    },
    "Q2": {"en": "Arsenal W.F.C.", "P31": [WOMEN, CLUB]},  # women's: rejected
    "Q3": {"en": "Arsenal", "P31": [CLUB, DISAMBIGUATION]},  # rejected
    "Q80": {"en": "2020–21 Arsenal F.C. season", "P31": [CLUB], "P118": [PL]},  # not a club
    "Q18656": {"en": "Manchester United F.C.", "aliases": ["Man United"], "P31": [CLUB]},
    # A football club's reserve side, named only by its parent club (P831).
    "Q4": {"en": "MUFC Development Squad", "aliases": ["Man United"], "P831": ["Q18656"]},
    # The football team of a multi-sport club also names a parent club: that's no reserve.
    "Q7156": {"en": "FC Barcelona", "P31": [CLUB], "P831": ["Q21"], "P17": [SPAIN]},
    "Q21": {"en": "FC Barcelona (sports club)", "P31": [MULTISPORT], "P17": [SPAIN]},
    "Q5": {"en": "Bury F.C.", "P31": [CLUB], "P576": True},  # dissolved: rejected
    "Q6": {"en": "Bury AFC", "P31": [CLUB], "P118": [PL]},
    "Q7": {"en": "Sporting F.C.", "P31": [CLUB], "P118": [PL]},  # two clubs called
    "Q8": {"en": "Sporting A.F.C.", "P31": [CLUB], "P118": [PL]},  # "Sporting"
    "Q30": {"en": "Lincoln City F.C.", "aliases": ["Lincoln"], "P31": [CLUB], "P118": [PL]},
    "Q31": {"en": "Lincoln United F.C.", "aliases": ["Lincoln"], "P31": [CLUB]},
    "Q8682": {"en": "Real Madrid CF", "aliases": ["Real Madrid"], "P17": [SPAIN]},
    "Q9": {"en": "Real Madrid Castilla", "P17": [SPAIN], "P118": [LALIGA]},  # B team
    "Q10": {"en": "Arsenal de Sarandí", "P17": [SPAIN]},
    "Q180305": {"en": "AS Monaco FC", "P17": [MONACO], "P118": [LIGUE1]},
    "Q11": {"mul": "Olympique Lyonnais", "P17": [FRANCE], "P118": [LIGUE1]},  # "mul" only
    "Q40": {"en": "R.C. Reims", "P17": [FRANCE]},  # no league: not Stade de Reims
    "Q50": {"en": "Zagłębie Sosnowiec", "aliases": ["Zagłębie"], "P17": [POLAND]},
    "Q51": {"en": "Zagłębie Lubin", "P17": [POLAND], "P118": [EKSTRAKLASA]},
    "Q60": {"en": "Willem II", "P17": [NETHERLANDS], "P118": [EREDIVISIE]},
    "Q70": {"en": "Zlahoda", "aliases": ["Dnipro-1"], "P17": [UKRAINE], "P118": [WOMENS_LEAGUE]},
    # Malta: each club's item, and an item for its first men's team (part of the club, P361)
    # that carries the bare club name but no sitelinks, so no picture or venue either.
    "Q1289495": {"en": "Sliema Wanderers F.C.", "P17": [MALTA], "sitelinks": 33},
    "Q127269055": {"en": "Sliema Wanderers", "P31": [MENS_TEAM], "P17": [MALTA]},
    "Q343030": {"en": "Ħamrun Spartans F.C.", "P17": [MALTA], "sitelinks": 35},
    "Q127269053": {"en": "Ħamrun Spartans", "P31": [MENS_TEAM], "P17": [MALTA]},
    "Q127269099": {"en": "Gżira United", "P31": [MENS_TEAM], "P17": [MALTA]},  # nothing else
}
ITEMS["Q50"]["P118"] = [I_LIGA]
for item in ITEMS.values():
    item.setdefault("P17", [UK])
    item.setdefault("P31", [CLUB])
    for prop in ("P118", "P831"):
        item.setdefault(prop, [])


def binding(**cells) -> dict:
    out = {}
    for var, value in cells.items():
        if isinstance(value, tuple):  # (text, lang)
            out[var] = {"type": "literal", "value": value[0], "xml:lang": value[1]}
        elif re.fullmatch(r"Q\d+", str(value)):
            out[var] = {"type": "uri", "value": ENTITY + value}
        elif str(value).startswith("http"):
            out[var] = {"type": "uri", "value": value}
        else:
            out[var] = {"type": "literal", "value": str(value)}
    return out


class FakeWdqs:
    def __init__(self, items=ITEMS, status=200, headers=None):
        self.items = items
        self.status = status
        self.headers = headers or {}
        self.requests: list[httpx.Request] = []
        self.queries: list[str] = []

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        if self.status != 200:
            return httpx.Response(self.status, headers=self.headers)
        q = parse_qs(request.content.decode())["query"][0]
        self.queries.append(q)
        return httpx.Response(200, json={"results": {"bindings": self.answer(q)}})

    def answer(self, q: str) -> list[dict]:
        rows = []
        accepted = set(re.findall(r"wd:(Q\d+)", q.split("}")[0])) if "VALUES ?cls" in q else set()
        country = re.search(r"wdt:P17 wd:(Q\d+)", q)
        if country:  # stage 1: names of one country's clubs
            for qid, it in self.items.items():
                if country.group(1) not in it["P17"] or not accepted & set(it["P31"]):
                    continue
                if "rdfs:label" in q:
                    for lang in ("en", "mul"):
                        if it.get(lang):
                            rows.append(binding(item=qid, name=(it[lang], lang)))
                else:
                    rows += [binding(item=qid, name=(a, "en")) for a in it.get("aliases", [])]
        elif "VALUES ?p" in q:  # stage 2: facts
            for qid in re.findall(r"wd:(Q\d+)", q):
                it = self.items[qid]
                for prop in ("P31", "P17", "P118", "P2094", "P831"):
                    for v in it.get(prop, []):
                        p = f"http://www.wikidata.org/prop/direct/{prop}"
                        rows.append(binding(item=qid, p=p, value=v))
                if it.get("P576"):
                    p = "http://www.wikidata.org/prop/direct/P576"
                    rows.append(binding(item=qid, p=p, value="2019-08-27T00:00:00Z"))
                if it.get("sitelinks"):
                    p = "http://wikiba.se/ontology#sitelinks"
                    rows.append(binding(item=qid, p=p, value=str(it["sitelinks"])))
        elif "?label" in q:  # labels of leagues and competition classes
            for qid in re.findall(r"wd:(Q\d+)", q):
                if qid in LABELS:
                    rows.append(binding(item=qid, label=(LABELS[qid], "en")))
        return rows

    def client(self, tmp_path, **kw) -> WdqsClient:
        http = httpx.Client(
            transport=httpx.MockTransport(self.handler), headers={"User-Agent": USER_AGENT}
        )
        self.sleeps: list[float] = []
        return WdqsClient(tmp_path / "cache", client=http, sleep=self.sleeps.append, **kw)


DIVISION = {"ENG": "E0", "ESP": "SP1", "FRA": "F1", "POL": "POL", "NLD": "N1"}


def team(key, name, country="ENG", *aliases, uefa_only=False) -> ScopeTeam:
    divisions = set() if uefa_only else {DIVISION[country]}
    return ScopeTeam(key, name, country, [name, *aliases], divisions)


def test_normalise():
    assert normalise("A.F.C. Bournemouth") == normalise("AFC Bournemouth") == "bournemouth"
    assert normalise("Real Madrid CF") == "real madrid"
    assert normalise("Bayern München") == normalise("Bayern Munchen") == "bayern munchen"
    assert normalise("Nott'm Forest") == "nottm forest"
    assert normalise("Brighton & Hove Albion") == "brighton and hove albion"
    assert normalise("FC") == "fc"  # nothing but legal-form words: kept
    assert mapping.strict("F.C. Köln") == "fc koln"
    # Letters NFKD leaves alone are spelt out, as English names write them.
    assert mapping.strict("Ħamrun Spartans") == "hamrun spartans"
    assert mapping.strict("Breiðablik") == "breidablik"
    assert mapping.strict("Þróttur") == "throttur"


def test_build_mapping_rules(tmp_path):
    fake = FakeWdqs()
    teams = {
        t.team_key: t
        for t in [
            # label match; the women's team, disambiguation page and season item rejected
            team("eng-arsenal", "Arsenal"),
            team("eng-man-united", "Man United"),  # alias match; the reserve side rejected
            team("esp-barcelona", "FC Barcelona", "ESP"),  # parent is a multi-sport club
            team("eng-bury", "Bury"),  # dissolved Bury F.C. rejected, Bury AFC kept
            team("eng-sporting", "Sporting"),  # two clubs, both in the league: ambiguous
            team("eng-lincoln", "Lincoln"),  # two clubs, the league picks Lincoln City
            team("esp-real-madrid", "Real Madrid", "ESP"),  # Castilla rejected
            team("fra-monaco", "Monaco", "FRA"),  # France includes Monaco
            team("fra-lyon", "Lyon", "FRA", "Olympique Lyonnais"),  # "mul" label
            team("fra-reims", "Reims", "FRA"),  # loose match the league doesn't confirm
            team("pol-zaglebie", "Zaglebie", "POL"),  # another league club holds the name
            team("nld-willem-ii", "Willem II", "NLD"),  # "II" is part of the name
            team("ukr-dnipro-1", "Dnipro-1", "UKR", uefa_only=True),  # women's league
            team("eng-nowhere", "Nowhere Town"),
            team("eng-keep", "Kept By Hand"),
        ]
    }
    manual = {"eng-keep": MappingRow("eng-keep", "Q999", "Hand FC", "manual", "checked")}
    rows = build_mapping(teams, fake.client(tmp_path), manual)

    def got(key):
        return rows[key].qid, rows[key].method

    assert got("eng-arsenal") == ("Q9617", "exact_label")
    assert rows["eng-arsenal"].wikidata_label == "Arsenal F.C."
    assert "Q10" not in rows["eng-arsenal"].note  # Arsenal de Sarandí: wrong country
    assert got("eng-man-united") == ("", "")  # Q18656 has no league to confirm it
    assert "not confirmed by league" in rows["eng-man-united"].note
    assert got("esp-barcelona") == ("", "")  # the same, but not rejected as a reserve
    assert "Q7156" in rows["esp-barcelona"].note
    assert got("eng-bury") == ("Q6", "exact_label")
    assert rows["eng-sporting"].status == "ambiguous"
    assert "Q7" in rows["eng-sporting"].note and "Q8" in rows["eng-sporting"].note
    assert got("eng-lincoln") == ("Q30", "alias")
    assert "league (P118) confirms it, of 2 name matches" in rows["eng-lincoln"].note
    assert rows["esp-real-madrid"].status == "unresolved"  # Castilla rejected, Q8682 no league
    assert got("fra-monaco") == ("Q180305", "exact_label")
    assert got("fra-lyon") == ("Q11", "exact_label")
    assert rows["fra-reims"].note.startswith("unresolved: loose match Q40")
    assert rows["pol-zaglebie"].status == "ambiguous"
    assert "Q51 Zagłębie Lubin" in rows["pol-zaglebie"].note
    assert got("nld-willem-ii") == ("Q60", "exact_label")
    assert "women's league" in rows["ukr-dnipro-1"].note
    assert rows["eng-nowhere"].status == "unresolved"
    assert rows["eng-keep"] == manual["eng-keep"]  # manual rows are never rebuilt
    # Every query carries the identifying User-Agent and was paced at 2 s.
    assert all(r.headers["User-Agent"] == USER_AGENT for r in fake.requests)
    assert all(str(r.url) == WDQS_URL for r in fake.requests)
    assert all(0 < s <= 2.0 for s in fake.sleeps)
    assert len(fake.sleeps) >= len(fake.requests) - 1


def test_uefa_only_teams_need_no_league(tmp_path):
    teams = {
        "eng-man-united": team("eng-man-united", "Man United", uefa_only=True),
        "esp-barcelona": team("esp-barcelona", "FC Barcelona", "ESP", uefa_only=True),
    }
    rows = build_mapping(teams, FakeWdqs().client(tmp_path))
    assert rows["eng-man-united"].qid == "Q18656"  # the reserve side (P831) rejected
    assert rows["esp-barcelona"].qid == "Q7156"  # its parent is a multi-sport club
    assert "no league to check" in rows["esp-barcelona"].note


def test_an_item_without_sitelinks_gives_way_to_a_name_match_with_some(tmp_path):
    teams = {
        t.team_key: t
        for t in [
            team("mlt-sliema-wanderers", "Sliema Wanderers", "MLT", uefa_only=True),
            team("mlt-hamrun-spartans", "Hamrun Spartans", "MLT", uefa_only=True),
            team("mlt-gzira-united", "Gzira United", "MLT", uefa_only=True),
        ]
    }
    rows = build_mapping(teams, FakeWdqs().client(tmp_path))
    # The first team's item matches strictly, the club's only with "F.C." ignored; the club,
    # which some Wikipedia describes, is taken.
    assert rows["mlt-sliema-wanderers"].qid == "Q1289495"
    assert rows["mlt-hamrun-spartans"].qid == "Q343030"
    assert "ignoring legal-form words" in rows["mlt-hamrun-spartans"].note
    # With nothing better, an item without sitelinks is still taken.
    assert rows["mlt-gzira-united"].qid == "Q127269099"


def test_a_club_matched_by_two_teams_is_given_to_neither(tmp_path):
    teams = {
        "eng-arsenal": team("eng-arsenal", "Arsenal"),
        "eng-gunners": team("eng-gunners", "The Gunners"),
    }
    rows = build_mapping(teams, FakeWdqs().client(tmp_path))
    assert rows["eng-arsenal"].qid == rows["eng-gunners"].qid == ""
    assert "also matched eng-gunners" in rows["eng-arsenal"].note


def test_mapping_csv_round_trip(tmp_path):
    rows = {
        "b": MappingRow("b", "Q2", 'B, "quoted"', "alias", "alias 'B' = 'B'"),
        "a": MappingRow("a", note="unresolved: no club matched ('A')"),
    }
    path = tmp_path / "m.csv"
    mapping.write_mapping(rows, path)
    assert path.read_text().splitlines()[0] == "team_key,qid,wikidata_label,method,note"
    assert mapping.read_mapping(path) == rows


def test_wdqs_cache_429_and_offline(tmp_path):
    fake = FakeWdqs()
    client = fake.client(tmp_path)
    q = mapping.names_query(UK, "label")
    first = client.query(q)
    assert client.query(q) == first  # second answer from the disk cache
    assert len(fake.requests) == 1 and client.cache_hits == 1
    cached = json.loads(client.cache_path(q).read_text())
    assert cached["query"] == q and cached["endpoint"] == WDQS_URL

    offline = WdqsClient(tmp_path / "cache", offline=True)
    assert offline.query(q) == first
    with pytest.raises(WdqsCacheMiss):
        offline.query(mapping.names_query(UK, "alias"))

    limited = FakeWdqs(status=429, headers={"Retry-After": "120"})
    with pytest.raises(WdqsRateLimited) as exc:
        limited.client(tmp_path / "other").query(q)
    assert exc.value.retry_after == 120
    assert len(limited.requests) == 1  # never retried


def test_wdqs_retries_a_5xx_once_then_raises(tmp_path):
    fake = FakeWdqs(status=503)
    client = fake.client(tmp_path, retry_pause=0.0)
    with pytest.raises(Exception, match="HTTP 503"):
        client.query(mapping.names_query(UK, "label"))
    assert len(fake.requests) == 2


def test_teams_in_scope(migrated_engine):
    with Session(migrated_engine) as s:
        for key, name, country in [
            ("eng-old", "Old", "ENG"),
            ("eng-new", "New", "ENG"),
            ("eng-cup", "Cup", "ENG"),
            ("ita-euro", "Euro", "ITA"),
        ]:
            s.merge(Team(team_key=key, name=name, country=country))
        s.merge(TeamAlias(source="openfootball", alias="Euro Club (ITA)", team_key="ita-euro"))
        s.commit()
    with migrated_engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-01', 'p')"
            )
        )
        conn.execute(text("INSERT OR IGNORE INTO divisions VALUES ('E0', 'PL', 'ENG', 1)"))
    with Session(migrated_engine) as s:

        def league(season, home, away):
            return Match(
                division="E0",
                season=season,
                match_date=date(2025, 1, 1),
                home_team=home,
                away_team=away,
                home_goals=1,
                away_goals=0,
                result="H",
                source_row=1,
                first_raw_file_id=1,
                last_raw_file_id=1,
            )

        s.add(league("2324", "eng-old", "eng-new"))  # before 2024/25: not in scope by itself
        s.add(league("2425", "eng-new", "eng-cup"))
        for season, stage in [("2627", "League MD1"), ("2526", "Round of 16 leg 1")]:
            s.add(
                EuroMatch(
                    competition="UCL",
                    season=season,
                    stage=stage,
                    match_date=date(2026, 9, 16),
                    home_team="ita-euro",
                    away_team="eng-new",
                    status="scheduled",
                    source="wikipedia",
                )
            )
        s.commit()
    teams = mapping.teams_in_scope(migrated_engine)
    assert set(teams) == {"eng-new", "eng-cup", "ita-euro"}
    assert teams["eng-new"].divisions == {"E0"}
    assert teams["eng-new"].league_phase_2627 == {"UCL"}
    assert teams["ita-euro"].names == ["Euro", "Euro Club"]
    assert teams["ita-euro"].divisions == set()
    cov = mapping.coverage({k: MappingRow(k, "Q1" if k == "eng-new" else "") for k in teams}, teams)
    assert cov["E0"] == {"teams": 2, "mapped": 1, "ambiguous": 0, "unresolved": 1}
    assert cov["UCL 2026/27 league phase"]["teams"] == 2


def test_facts_choose_current_statements():
    preferred = "http://wikiba.se/ontology#PreferredRank"
    normal = "http://wikiba.se/ontology#NormalRank"
    logos = [
        {"value": "Old crest.svg", "rank": normal, "end": "2002-01-01T00:00:00Z"},
        {"value": "B crest.svg", "rank": normal},
        {"value": "A crest.svg", "rank": normal, "start": "2020-01-01T00:00:00Z"},
    ]
    venues = [
        {"value": "Q1", "rank": normal, "name": "Old Ground", "end": "2006-05-07T00:00:00Z"},
        {"value": "Q2", "rank": preferred, "name": "New Ground", "image": "b.jpg"},
        {"value": "Q2", "rank": preferred, "name": "New Ground", "image": "a.jpg"},
    ]
    colours = [
        {"value": "Q3142", "rank": normal, "name": "red", "hex": "ef0107"},
        {"value": "Q23444", "rank": normal, "name": "white", "hex": "FFFFFF"},
        {"value": "Q9", "rank": normal, "name": "gold"},  # no hex
    ]
    entry = team_entry("Q9617", logos, venues, colours)
    assert entry["crest_file"] == "A crest.svg"  # latest start first
    assert entry["venue"] == {"qid": "Q2", "name": "New Ground", "image_file": "a.jpg"}
    assert [c["hex"] for c in entry["colours"]] == [None, "#EF0107", "#FFFFFF"]
    assert team_entry("Q1", [logos[0]], [venues[0]], [])["crest_file"] is None


# The committed reference files ------------------------------------------------------------


def committed_mapping() -> dict[str, dict]:
    with (REFERENCE_DIR / "team_wikidata.csv").open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        assert reader.fieldnames == mapping.FIELDS
        rows = list(reader)
    keys = [r["team_key"] for r in rows]
    assert len(keys) == len(set(keys))
    return {r["team_key"]: r for r in rows}


def test_committed_mapping_is_consistent():
    rows = committed_mapping()
    with (REFERENCE_DIR / "teams.csv").open(encoding="utf-8", newline="") as f:
        known = {r["team_key"] for r in csv.DictReader(f)}
    assert set(rows) <= known
    qids = [r["qid"] for r in rows.values() if r["qid"]]
    assert len(qids) == len(set(qids))  # no club given to two teams
    for r in rows.values():
        if r["qid"]:
            assert re.fullmatch(r"Q\d+", r["qid"])
            assert r["method"] in mapping.METHODS
            assert r["wikidata_label"]
        else:
            assert r["method"] == ""
            assert r["note"].startswith(("ambiguous", "unresolved"))


def test_committed_facts_match_the_mapping():
    rows = committed_mapping()
    facts = read_facts(MEDIA_JSON)
    assert facts
    for key, f in facts.items():
        assert rows[key]["qid"] == f["qid"]
        for c in f["colours"]:
            assert c["hex"] is None or re.fullmatch(r"#[0-9A-F]{6}", c["hex"])
    mapped = {k for k, r in rows.items() if r["qid"]}
    assert set(facts) == mapped
