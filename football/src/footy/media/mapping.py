"""Map each team_key to its Wikidata item (reference/team_wikidata.csv).

Only the Wikidata Query Service is used (see `wdqs`). For each country, two queries fetch
every association football club (Q476028, or an accepted subclass) whose country (P17) is
that country, with its English (or language-neutral "mul") label and English aliases. Names
are matched locally, and a second batch of queries fetches, for the matched items only, the
facts used to reject the wrong kind of team.

- Our names for a team are its `teams.name` and every `team_aliases` alias (the `(ENG)`-style
  country suffix of openfootball and Wikipedia names removed).
- Names are compared in two passes. Strict: case, accents and punctuation ignored ("F.C."
  equals "FC"). Loose: legal-form words such as FC, AC, SV or Calcio ignored too ("Arsenal"
  equals "Arsenal F.C."). The loose pass runs only when the strict one finds nothing.
- Rejected: women's, reserve, B, C and youth teams (by class, by a parent club P831 that is
  itself a football club, by a women's or youth competition class P2094, by a women's
  league P118, or by a label such as "Arsenal W.F.C.", "Sevilla FC C" or "Real Madrid
  Castilla"), beach soccer, defunct (P576) and national teams, disambiguation pages, season
  items, and clubs whose country is not the team's.
- League check (P118): for a team that played in one of our divisions since 2024/25, the
  leagues of those divisions' national pyramids (DIVISION_LEAGUES) must confirm the club.
  Several matches: the one club the league confirms is taken, otherwise the team is
  ambiguous. A single match the league doesn't confirm is not taken ("R.C. Reims" is not
  Stade de Reims). An alias or loose match is also withdrawn when another club those
  leagues confirm carries the same words in its name (see `build_mapping`). Teams known only
  from UEFA matches have no league to check; their notes say so.
- Exactly one club left: mapped, `exact_label` when one of our names matched its label,
  otherwise `alias`. More than one: unmapped as ambiguous, with the candidates in the note.
  None: unmapped (`unresolved`).

Rows with method `manual` are hand-checked and kept as they are on every rebuild.
"""

import csv
import re
import unicodedata
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from footy.media.wdqs import WdqsClient, qid_values, simplify
from footy.models import EuroMatch, Match, Team, TeamAlias
from footy.reference import REFERENCE_DIR

MAPPING_CSV = REFERENCE_DIR / "team_wikidata.csv"
FIELDS = ["team_key", "qid", "wikidata_label", "method", "note"]
METHODS = {"exact_label", "alias", "manual"}
# 2024/25 onwards: league season keys from "2424" (the calendar-year 2024 seasons) sort after
# every earlier key (D-008); UEFA seasons are always split-year.
SINCE_SEASON = "2424"
EURO_SINCE_SEASON = "2425"
LEAGUE_PHASE_SEASON = "2627"
TOP_DIVISIONS = ["E0", "SP1", "I1", "D1", "F1", "E1"]

# Classes a club item may be an instance of (P31): association football club and its
# subclasses, as the query service listed them on 21 Oct 2026. Only the first four are
# accepted; an item that also carries one of the others is rejected.
ACCEPTED_CLASSES = {
    "Q476028": "association football club",
    "Q103231176": "men's association football club",
    "Q103229495": "men's association football team",
    "Q14752149": "amateur football club",
}
REJECTED_CLASSES = {
    "Q94579592": "defunct association football club",
    "Q51481377": "women's association football club",
    "Q116953048": "beach soccer club",
    "Q135408445": "men's national association football team",
    "Q7315281": "Norwegian reserve football team",
    "Q135100233": "Italian reserve football team",
    "Q4167410": "Wikimedia disambiguation page",
}

# Our country codes (ISO 3166-1 alpha-3; ENG, SCO, WAL, NIR for the home nations, XKX for
# Kosovo) and the Wikidata countries (P17) a club of that country may carry. Checked on the
# query service on 21 Oct 2026. A club takes its league's country here (D-023), so France
# includes Monaco and Switzerland Liechtenstein.
UK = ["Q145", "Q21", "Q22", "Q25", "Q26"]
COUNTRY_QIDS: dict[str, list[str]] = {
    "ALB": ["Q222"],
    "AND": ["Q228"],
    "ARM": ["Q399"],
    "AUT": ["Q40"],
    "AZE": ["Q227"],
    "BEL": ["Q31"],
    "BGR": ["Q219"],
    "BIH": ["Q225"],
    "BLR": ["Q184"],
    "CHE": ["Q39", "Q347"],
    "CYP": ["Q229"],
    "CZE": ["Q213"],
    "DEU": ["Q183"],
    "DNK": ["Q35", "Q756617"],
    "ENG": UK,
    "ESP": ["Q29"],
    "EST": ["Q191"],
    "FIN": ["Q33"],
    "FRA": ["Q142", "Q235"],
    "FRO": ["Q4628", "Q35", "Q756617"],
    "GEO": ["Q230"],
    "GIB": ["Q1410", "Q145"],
    "GRC": ["Q41"],
    "HRV": ["Q224"],
    "HUN": ["Q28"],
    "IRL": ["Q27"],
    "ISL": ["Q189"],
    "ISR": ["Q801"],
    "ITA": ["Q38"],
    "KAZ": ["Q232"],
    "LTU": ["Q37"],
    "LUX": ["Q32"],
    "LVA": ["Q211"],
    "MDA": ["Q217"],
    "MKD": ["Q221"],
    "MLT": ["Q233"],
    "MNE": ["Q236"],
    "NIR": UK,
    "NLD": ["Q55", "Q29999"],
    "NOR": ["Q20"],
    "POL": ["Q36"],
    "PRT": ["Q45"],
    "ROU": ["Q218"],
    "RUS": ["Q159"],
    "SCO": UK,
    "SMR": ["Q238"],
    "SRB": ["Q403"],
    "SVK": ["Q214"],
    "SVN": ["Q215"],
    "SWE": ["Q34"],
    "TUR": ["Q43"],
    "UKR": ["Q212"],
    "WAL": UK,
    "XKX": ["Q1246"],
}

# The leagues (P118) that confirm a club of each division: its own league and the tiers next
# to it, since a club promoted or relegated since 2024/25 may carry either. Taken from the
# leagues of the clubs the strict pass matched, with their labels checked on 21 Oct 2026.
_ENG = ["Q9448", "Q19510", "Q19565"]  # Premier League, Championship, League One
_ESP = ["Q324867", "Q35615"]  # La Liga, LaLiga 2
_ITA = ["Q15804", "Q194052", "Q607965"]  # Serie A, Serie B, Serie C
_DEU = ["Q82595", "Q152665", "Q154069"]  # Bundesliga, 2. Bundesliga, 3. Liga
_FRA = ["Q13394", "Q217374", "Q864298"]  # Ligue 1, Ligue 2, Ligue 3 (National)
DIVISION_LEAGUES: dict[str, list[str]] = {
    "E0": _ENG,
    "E1": _ENG,
    "SP1": _ESP,
    "SP2": _ESP,
    "I1": _ITA,
    "I2": _ITA,
    "D1": _DEU,
    "D2": _DEU,
    "F1": _FRA,
    "F2": _FRA,
    "SC0": ["Q14377162", "Q14468438"],  # Scottish Premiership, Championship
    "N1": ["Q167541", "Q610823"],  # Eredivisie, Eerste Divisie
    "B1": ["Q216022", "Q23925620"],  # Belgian Pro League, Challenger Pro League
    "P1": ["Q182994", "Q754488"],  # Primeira Liga, Liga Portugal 2
    "T1": ["Q485568", "Q1141692"],  # Süper Lig, TFF First League
    "G1": ["Q235114"],  # Super League Greece
    "AUT": ["Q219592", "Q650236"],  # Austrian Bundesliga, 2. Liga
    "DNK": ["Q204752", "Q162031"],  # Danish Superliga, 1st Division
    "SWZ": ["Q202699", "Q669073"],  # Swiss Super League, Challenge League
    "NOR": ["Q201671", "Q353508"],  # Eliteserien, 1. divisjon
    "SWE": ["Q202243", "Q692009"],  # Allsvenskan, Superettan
    "POL": ["Q44763", "Q163046"],  # Ekstraklasa, I liga
    "ROU": ["Q237753", "Q386384"],  # Liga I, Liga II
    "RUS": ["Q182165", "Q162533"],  # Russian Premier League, First League
    "FIN": ["Q220875", "Q384536"],  # Veikkausliiga, Ykkösliiga
    "IRL": ["Q831202", "Q2474177"],  # League of Ireland Premier, First Division
}

# Words that only name the legal form or the sport; ignored in the loose pass.
LEGAL_FORMS = {
    "a", "ac", "acf", "afc", "as", "asd", "bk", "c", "calcio", "cd", "cf", "cfc", "club",
    "f", "fc", "fk", "football", "fsv", "futbol", "fussball", "fussballclub", "gfk", "gnk",
    "hnk", "ik", "if", "jk", "kv", "mfk", "nk", "ofk", "pfc", "pfk", "rc", "rcd", "s", "sc",
    "sd", "sk", "spa", "ss", "ssc", "ssd", "ssv", "sv", "the", "tsg", "u", "uc", "ud", "us",
    "usd", "vfb", "vfl",
}  # fmt: skip

# A name (strictly normalised) that names a team which is not the first team: any of these
# words, or a last word "b", "c", "ii" or "iii" ("Sevilla FC C", "Bayern Munich II").
NOT_FIRST_TEAM = re.compile(
    r"\b(women|womens|ladies|feminin|feminine|femenino|femeni|femminile|frauen|dames|damer|"
    r"kvinner|dam|w|wfc|lfc|reserves?|youth|academy|juniors?|primavera|castilla|jong|"
    r"next gen|u\d\d|under \d\d|futsal|beach)\b|\s(b|c|ii|iii)$"
)
# An item for one season of a club ("2020–21 Unione Sportiva Catanzaro season").
SEASON_ITEM = re.compile(r"^\d{4}[–-]\d{2,4}\b")
COUNTRY_SUFFIX = re.compile(r"\s*\([A-Z]{3}\)$")


@dataclass
class ScopeTeam:
    team_key: str
    name: str
    country: str
    names: list[str] = field(default_factory=list)  # name first, then aliases
    divisions: set[str] = field(default_factory=set)  # league divisions since 2024/25
    competitions: set[str] = field(default_factory=set)  # UEFA competitions since 2024/25
    league_phase_2627: set[str] = field(default_factory=set)  # UCL | UEL | UECL

    @property
    def leagues(self) -> set[str]:
        """The leagues (P118) that confirm a club for this team (empty: none to check)."""
        return {q for d in self.divisions for q in DIVISION_LEAGUES.get(d, [])}


@dataclass
class Club:
    """A Wikidata club item: its names (stage 1) and the facts that can reject it (stage 2)."""

    qid: str
    label: str | None = None  # English label
    mul_label: str | None = None  # the language-neutral ("mul") label, when there is one
    aliases: list[str] = field(default_factory=list)  # English aliases
    classes: set[str] = field(default_factory=set)  # every P31 value
    countries: set[str] = field(default_factory=set)  # P17
    leagues: set[str] = field(default_factory=set)  # P118
    competition_classes: set[str] = field(default_factory=set)  # P2094
    parent_clubs: set[str] = field(default_factory=set)  # P831
    dissolved: bool = False  # has P576
    sitelinks: int = 0

    @property
    def display_label(self) -> str:
        return self.label or self.mul_label or self.qid


@dataclass
class MappingRow:
    team_key: str
    qid: str = ""
    wikidata_label: str = ""
    method: str = ""
    note: str = ""

    @property
    def mapped(self) -> bool:
        return bool(self.qid)

    @property
    def status(self) -> str:
        """mapped | ambiguous | unresolved"""
        if self.qid:
            return "mapped"
        return "ambiguous" if self.note.startswith("ambiguous") else "unresolved"


def strict(name: str) -> str:
    """Lower case, no accents or punctuation: "F.C. Köln" -> "fc koln"."""
    text = unicodedata.normalize("NFKD", name)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.casefold().replace("&", " and ").replace("ß", "ss").replace("ø", "o")
    text = text.replace("æ", "ae").replace("ł", "l").replace("đ", "d").replace("ı", "i")
    text = re.sub(r"[.'’`]", "", text)  # "F.C." -> "fc", "Nott'm" -> "nottm"
    return " ".join(re.findall(r"[a-z0-9]+", text))


def normalise(name: str) -> str:
    """`strict`, with legal-form words dropped too: "A.F.C. Bournemouth" and
    "AFC Bournemouth" both become "bournemouth"."""
    tokens = strict(name).split()
    kept = [t for t in tokens if t not in LEGAL_FORMS]
    return " ".join(kept or tokens)


PASSES = {"strict": strict, "loose": normalise}


def strip_country_suffix(alias: str) -> str:
    return COUNTRY_SUFFIX.sub("", alias).strip()


def teams_in_scope(engine: Engine) -> dict[str, ScopeTeam]:
    """Every team with a league or UEFA match (played or scheduled) since 2024/25."""
    teams: dict[str, ScopeTeam] = {}
    with Session(engine) as s:
        info = {t.team_key: t for t in s.scalars(select(Team))}

        def team(key: str) -> ScopeTeam:
            if key not in teams:
                t = info[key]
                teams[key] = ScopeTeam(key, t.name, t.country, [t.name])
            return teams[key]

        for div, home, away in s.execute(
            select(Match.division, Match.home_team, Match.away_team)
            .where(Match.season >= SINCE_SEASON)
            .distinct()
        ):
            team(home).divisions.add(div)
            team(away).divisions.add(div)
        for comp, season, stage, home, away in s.execute(
            select(
                EuroMatch.competition,
                EuroMatch.season,
                EuroMatch.stage,
                EuroMatch.home_team,
                EuroMatch.away_team,
            )
            .where(EuroMatch.season >= EURO_SINCE_SEASON)
            .distinct()
        ):
            for key in (home, away):
                team(key).competitions.add(comp)
                if season == LEAGUE_PHASE_SEASON and stage.startswith("League"):
                    team(key).league_phase_2627.add(comp)
        for alias, key in s.execute(
            select(TeamAlias.alias, TeamAlias.team_key)
            .where(TeamAlias.team_key.in_(list(teams)))
            .order_by(TeamAlias.source, TeamAlias.alias)
        ):
            name = strip_country_suffix(alias)
            if name and name not in teams[key].names:
                teams[key].names.append(name)
    return teams


def names_query(country_qid: str, kind: str) -> str:
    """Stage 1: the labels ("en" and "mul"; kind "label") or the English aliases (kind
    "alias") of every item of an accepted club class whose country (P17) is `country_qid`.
    One country and one kind per query keeps each query to a few seconds; a combined form
    (VALUES of countries, or a UNION of labels and aliases) ran for minutes."""
    if kind == "label":
        name = 'rdfs:label ?name . FILTER(LANG(?name) IN ("en", "mul"))'
    else:
        name = 'skos:altLabel ?name . FILTER(LANG(?name) = "en")'
    return f"""SELECT DISTINCT ?item ?name WHERE {{
  VALUES ?cls {{ {qid_values(ACCEPTED_CLASSES)} }}
  ?item wdt:P31 ?cls ; wdt:P17 wd:{country_qid} ; {name}
}}"""


FACT_PROPERTIES = {
    "wdt:P31": "P31",
    "wdt:P17": "P17",
    "wdt:P118": "P118",
    "wdt:P2094": "P2094",
    "wdt:P831": "P831",
    "wdt:P576": "P576",
    "wikibase:sitelinks": "sitelinks",
}


def facts_query(qids) -> str:
    """Stage 2: the facts `rejection` checks, for the candidate items only."""
    return f"""SELECT ?item ?p ?value WHERE {{
  VALUES ?item {{ {qid_values(qids)} }}
  VALUES ?p {{ {" ".join(FACT_PROPERTIES)} }}
  ?item ?p ?value .
}}"""


def fetch_country_names(client: WdqsClient, country_qid: str) -> dict[str, Club]:
    """Every club item of one Wikidata country, with its names only."""
    out: dict[str, Club] = {}
    for r in client.raw_query(names_query(country_qid, "label"))["results"]["bindings"]:
        qid = simplify(r)["item"]
        club = out.setdefault(qid, Club(qid))
        if r["name"].get("xml:lang") == "mul":
            club.mul_label = r["name"]["value"]
        else:
            club.label = r["name"]["value"]
    for r in client.query(names_query(country_qid, "alias")):
        club = out.setdefault(r["item"], Club(r["item"]))
        if r["name"] not in club.aliases:
            club.aliases.append(r["name"])
    for club in out.values():
        club.aliases.sort()
    return out


def fetch_facts(client: WdqsClient, clubs: dict[str, Club], batch: int = 200) -> None:
    """Fill in stage 2 for `clubs`, in batches of `batch` items per query."""
    qids = sorted(clubs, key=lambda q: int(q[1:]))
    for i in range(0, len(qids), batch):
        for r in client.query(facts_query(qids[i : i + batch])):
            prop = FACT_PROPERTIES[_prefixed(r["p"])]
            club, value = clubs[r["item"]], r["value"]
            if prop == "P31":
                club.classes.add(value)
            elif prop == "P17":
                club.countries.add(value)
            elif prop == "P118":
                club.leagues.add(value)
            elif prop == "P2094":
                club.competition_classes.add(value)
            elif prop == "P831":
                club.parent_clubs.add(value)
            elif prop == "P576":
                club.dissolved = True
            elif prop == "sitelinks":
                club.sitelinks = int(value)


def _prefixed(uri: str) -> str:
    """ "http://www.wikidata.org/prop/direct/P31" -> "wdt:P31"."""
    for prefix, short in (
        ("http://www.wikidata.org/prop/direct/", "wdt:"),
        ("http://wikiba.se/ontology#", "wikibase:"),
    ):
        if uri.startswith(prefix):
            return short + uri[len(prefix) :]
    return uri


def labels_query(qids) -> str:
    return f"""SELECT ?item ?label WHERE {{
  VALUES ?item {{ {qid_values(qids)} }}
  ?item rdfs:label ?label FILTER(LANG(?label) = "en")
}}"""


def fetch_labels(client: WdqsClient, qids) -> dict[str, str]:
    qids = sorted(set(qids), key=lambda q: int(q[1:]))
    out: dict[str, str] = {}
    for i in range(0, len(qids), 400):
        for r in client.query(labels_query(qids[i : i + 400])):
            out[r["item"]] = r["label"]
    return out


@dataclass
class Context:
    """What `rejection` needs beyond the club itself."""

    class_labels: dict[str, str] = field(default_factory=dict)  # P2094 value -> label
    football_parents: set[str] = field(default_factory=set)  # P831 values that are clubs
    league_labels: dict[str, str] = field(default_factory=dict)  # P118 value -> label


def rejection(club: Club, team: ScopeTeam, ctx: Context) -> str | None:
    """Why `club` can't be the first team of `team`'s club, or None."""
    bad = club.classes & set(REJECTED_CLASSES)
    if bad:
        return REJECTED_CLASSES[sorted(bad)[0]]
    if not club.classes & set(ACCEPTED_CLASSES):
        return "not an association football club"
    if SEASON_ITEM.match(club.display_label):
        return "a season, not a club"
    if club.dissolved:
        return "dissolved (P576)"
    # The football team of a multi-sport club names that club as its parent (FC Barcelona's
    # team item does); only a football club as parent makes it a reserve, B or youth team.
    if club.parent_clubs & ctx.football_parents:
        return "its parent club (P831) is a football club: a reserve, B or youth team"
    # A multi-sport club lists every section's class ("women's volleyball"): only a women's
    # or youth football class rejects it.
    for cc in club.competition_classes:
        label = strict(ctx.class_labels.get(cc, ""))
        if "football" in label and (
            NOT_FIRST_TEAM.search(label) or re.search(r"\b(youth|under|u\d\d)\b", label)
        ):
            return f"competition class {ctx.class_labels.get(cc, cc)!r}"
    for league in club.leagues:
        if re.search(r"\bwomen", strict(ctx.league_labels.get(league, ""))):
            return f"plays in a women's league ({ctx.league_labels[league]})"
    if not club.countries & set(COUNTRY_QIDS[team.country]):
        return "country"
    # "Willem II" is a first team: the label rule applies only when none of our names for
    # the team would trip it too.
    if not any(NOT_FIRST_TEAM.search(strict(n)) for n in team.names):
        for text in (club.label, club.mul_label):
            if text and NOT_FIRST_TEAM.search(strict(text)):
                return f"label {text!r} names a women's, reserve, B, C or youth team"
    return None


NameIndex = dict[str, list[tuple[str, str, str]]]  # normalised -> [(qid, kind, text)]


def name_index(pool: dict[str, Club], key=strict) -> NameIndex:
    """Every club name in `pool`, under `key`. Aliases that name a women's, reserve, B or
    youth team (e.g. "Arsenal Women" on a first team's item) are left out."""
    index: NameIndex = defaultdict(list)
    for club in pool.values():
        for kind, text in [("en", club.label), ("mul", club.mul_label)]:
            if text:
                index[key(text)].append((club.qid, kind, text))
        for text in club.aliases:
            if not NOT_FIRST_TEAM.search(strict(text)):
                index[key(text)].append((club.qid, "alias", text))
    return index


Hits = dict[str, tuple[str, str, str]]  # qid -> (method, our name, their name)


def candidates(team: ScopeTeam, index: NameIndex, key=strict) -> Hits:
    """Every club one of our names matches under `key`; a label match wins over an alias
    match on the same club."""
    hits: Hits = {}
    for name in team.names:
        for qid, kind, text in index.get(key(name), []):
            method = "alias" if kind == "alias" else "exact_label"
            if qid not in hits or (method == "exact_label" and hits[qid][0] == "alias"):
                hits[qid] = (method, name, text)
    return hits


def _sorted(qids) -> list[str]:
    return sorted(qids, key=lambda q: int(q[1:]))


def match_team(
    team: ScopeTeam, hits: dict[str, Hits], clubs: dict[str, Club], ctx: Context
) -> MappingRow:
    """The mapping row for one team from its strict and loose name matches."""
    rejected: dict[str, str] = {}
    leagues = team.leagues
    for pass_name in PASSES:
        kept = {}
        for q, h in hits[pass_name].items():
            why = rejection(clubs[q], team, ctx)
            if why is None:
                kept[q] = h
            else:
                rejected[q] = why
        if not kept:
            continue
        confirmed = [q for q in kept if clubs[q].leagues & leagues]
        if len(kept) > 1:
            if len(confirmed) != 1:
                cands = "; ".join(
                    f"{q} {clubs[q].display_label} ({kept[q][0]} {kept[q][2]!r})"
                    for q in _sorted(kept)
                )
                return MappingRow(team.team_key, note=f"ambiguous: {cands}")
            qid = confirmed[0]
            check = f"league (P118) confirms it, of {len(kept)} name matches"
        else:
            qid = next(iter(kept))
            if not leagues:
                check = "no league to check (UEFA matches only)"
            elif qid in confirmed:
                check = "league (P118) confirms it"
            else:
                label = clubs[qid].display_label
                return MappingRow(
                    team.team_key,
                    note=f"unresolved: {pass_name} match {qid} {label} not confirmed by league "
                    "(P118)",
                )
        method, our_name, their_name = kept[qid]
        what = "label" if method == "exact_label" else "alias"
        how = "" if pass_name == "strict" else ", ignoring legal-form words"
        note = f"{what} {their_name!r} = {our_name!r}{how}; {check}"
        return MappingRow(team.team_key, qid, clubs[qid].display_label, method, note)
    note = "unresolved: no club matched " + _names(team)
    if rejected:
        note += "; rejected " + "; ".join(
            f"{q} {clubs[q].display_label}: {rejected[q]}" for q in _sorted(rejected)
        )
    return MappingRow(team.team_key, note=note)


def _names(team: ScopeTeam) -> str:
    return "(" + ", ".join(repr(n) for n in team.names) + ")"


def read_mapping(path: Path = MAPPING_CSV) -> dict[str, MappingRow]:
    if not path.exists():
        return {}
    with path.open(encoding="utf-8", newline="") as f:
        return {
            r["team_key"]: MappingRow(**{k: r[k] or "" for k in FIELDS}) for r in csv.DictReader(f)
        }


def write_mapping(rows: dict[str, MappingRow], path: Path = MAPPING_CSV) -> None:
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, lineterminator="\n")
        w.writeheader()
        for key in sorted(rows):
            r = rows[key]
            w.writerow({k: getattr(r, k) for k in FIELDS})


def word_index(pool: dict[str, Club]) -> dict[str, set[str]]:
    """word -> the clubs with that word in a label or alias (after `normalise`)."""
    index: dict[str, set[str]] = defaultdict(set)
    for club in pool.values():
        for text in [club.label, club.mul_label, *club.aliases]:
            if text and not NOT_FIRST_TEAM.search(strict(text)):
                for w in normalise(text).split():
                    index[w].add(club.qid)
    return index


def name_rivals(hits: dict[str, Hits], words: dict[str, set[str]]) -> set[str]:
    """Clubs other than the name matches whose names hold every word of one of our names
    that matched: the candidates for the uniqueness check in `build_mapping`."""
    out: set[str] = set()
    for name in {h[1] for found in hits.values() for h in found.values()}:
        sets = [words.get(w, set()) for w in normalise(name).split()]
        if sets:
            out |= set.intersection(*sets)
    return out - {q for found in hits.values() for q in found}


def build_mapping(
    teams: dict[str, ScopeTeam],
    client: WdqsClient,
    existing: dict[str, MappingRow] | None = None,
) -> dict[str, MappingRow]:
    """Map every team in `teams`; `manual` rows in `existing` are kept as they are."""
    existing = existing or {}
    countries = {q for t in teams.values() for q in COUNTRY_QIDS.get(t.country, [])}
    pool: dict[str, Club] = {}
    for country_qid in _sorted(countries):
        for qid, club in fetch_country_names(client, country_qid).items():
            pool.setdefault(qid, club)
    indexes = {p: name_index(pool, key) for p, key in PASSES.items()}
    hits = {k: {p: candidates(t, indexes[p], PASSES[p]) for p in PASSES} for k, t in teams.items()}
    clubs = {qid: pool[qid] for h in hits.values() for found in h.values() for qid in found}
    # Clubs whose names contain one of our names (for the uniqueness check below).
    words = word_index(pool)
    rivals = {k: name_rivals(hits[k], words) for k, t in teams.items() if t.leagues}
    for found in rivals.values():
        for q in found:
            clubs.setdefault(q, pool[q])
    fetch_facts(client, {q: c for q, c in clubs.items() if not c.classes})
    parents = {q: Club(q) for c in clubs.values() for q in c.parent_clubs}
    if parents:
        fetch_facts(client, parents)
    class_ids = {cc for c in clubs.values() for cc in c.competition_classes}
    league_ids = {lg for c in clubs.values() for lg in c.leagues}
    ctx = Context(
        class_labels=fetch_labels(client, class_ids) if class_ids else {},
        football_parents={q for q, c in parents.items() if c.classes & set(ACCEPTED_CLASSES)},
        league_labels=fetch_labels(client, league_ids) if league_ids else {},
    )
    rows: dict[str, MappingRow] = {}
    for key, t in teams.items():
        old = existing.get(key)
        if old is not None and old.method == "manual":
            rows[key] = old
        elif t.country not in COUNTRY_QIDS:
            rows[key] = MappingRow(key, note=f"unresolved: no Wikidata country for {t.country}")
        else:
            rows[key] = match_team(t, hits[key], clubs, ctx)
    # A short name must single out one club: an alias or loose match is withdrawn when
    # another club the league confirms also carries our name in its own ("Zaglebie" matched
    # Zagłębie Sosnowiec by alias, but Zagłębie Lubin is the Ekstraklasa club). Clubs that are
    # another team's match don't count ("Milan" is not ambiguous because of Inter Milan).
    taken = {r.qid for r in rows.values() if r.qid}
    for key, found in rivals.items():
        r = rows[key]
        if not r.qid or r.method == "manual" or key not in hits:
            continue
        strict_hit = hits[key]["strict"].get(r.qid)
        if strict_hit and strict_hit[0] == "exact_label":
            continue
        others = [
            q
            for q in found
            if q != r.qid
            and q not in taken
            and rejection(clubs[q], teams[key], ctx) is None
            and clubs[q].leagues & teams[key].leagues
        ]
        if others:
            names = "; ".join(f"{q} {clubs[q].display_label}" for q in _sorted(others))
            note = f"ambiguous: {r.qid} {r.wikidata_label} ({r.note}), but also named: {names}"
            rows[key] = MappingRow(key, note=note)
    # A club can't be two teams: a QID matched by two team_keys is withdrawn from both
    # (manual rows excepted), and the clash is noted.
    owners: dict[str, list[str]] = defaultdict(list)
    for key, r in rows.items():
        if r.qid:
            owners[r.qid].append(key)
    for qid, keys in owners.items():
        if len(keys) > 1:
            for key in keys:
                if rows[key].method != "manual":
                    others = ", ".join(k for k in keys if k != key)
                    label = rows[key].wikidata_label
                    note = f"ambiguous: {qid} {label} also matched {others}"
                    rows[key] = MappingRow(key, note=note)
    for key, old in existing.items():  # manual rows for teams outside the scope stay too
        if key not in rows and old.method == "manual":
            rows[key] = old
    return rows


def coverage(rows: dict[str, MappingRow], teams: dict[str, ScopeTeam]) -> dict:
    """Mapped, ambiguous and unresolved counts by division and 2026/27 league phase."""
    groups: dict[str, list[str]] = defaultdict(list)
    for t in teams.values():
        for d in t.divisions:
            groups[d].append(t.team_key)
        for comp in t.league_phase_2627:
            groups[f"{comp} 2026/27 league phase"].append(t.team_key)
        if t.competitions and not t.divisions:
            groups["UEFA only (no league in the database)"].append(t.team_key)
    groups["all"] = list(teams)

    def count(keys):
        out = {"teams": len(keys), "mapped": 0, "ambiguous": 0, "unresolved": 0}
        for k in keys:
            out[rows[k].status] += 1
        return out

    order = [*TOP_DIVISIONS]
    rest = sorted(g for g in groups if g not in order and g != "all")
    return {g: count(groups[g]) for g in [*order, *rest, "all"] if g in groups}
