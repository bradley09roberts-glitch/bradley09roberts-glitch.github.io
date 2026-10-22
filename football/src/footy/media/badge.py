"""Our own generated badge: a plain roundel in the club's colours with a short name.

It is shown wherever no freely licensed crest is stored, which is often: many clubs' crests
are not free and are not on Wikimedia Commons (Manchester United and Manchester City have no
logo image, P154, on Wikidata). It is deliberately unlike a crest (a circle, one diagonal
band, three letters) and says what it is in its title and accessible name.

Deterministic: the same name and colours always give byte-identical SVG.
"""

import colorsys
import hashlib
import re
import unicodedata
from collections.abc import Iterable, Mapping
from itertools import combinations
from xml.sax.saxutils import escape, quoteattr

# Used when Wikidata has no official colours (P6364 with P465) for the club: muted
# (background, band) pairs, one picked by the team key so neighbouring badges differ.
NEUTRAL_PALETTES = [
    ("#3B4252", "#8892A6"),
    ("#2F4858", "#86A1B3"),
    ("#4A4453", "#A39BB0"),
    ("#3D4A3F", "#97A898"),
    ("#4D4339", "#B0A08D"),
]
# Words skipped when abbreviating ("FC Barcelona" -> "BAR").
SKIP_WORDS = {
    "a", "ac", "afc", "as", "bk", "c", "cd", "cf", "club", "de", "del", "f", "fc", "fk", "if",
    "ik", "nk", "of", "rc", "sc", "sk", "ss", "ssc", "sv", "the", "ud", "us",
}  # fmt: skip
VOWELS = set("AEIOUY")

# The shade each of Wikidata's generic named colours is drawn in, by colour item, with the hex
# Wikidata gives that item (P465): the pure screen colour (red #FF0000, blue #0000FF, green
# #00FF00), which no club wears. The kit shade keeps the colour's name and reads as a shirt. A
# colour with a hex of its own (bordeaux #800032, or any other hex on the item) is drawn as
# Wikidata gives it. "mauve" is Anderlecht's ("mauve et blanc": purple in French), so it takes
# the purple shade.
KIT_SHADES = {
    "Q3142": ("#FF0000", "#C8102E"),  # red
    "Q1088": ("#0000FF", "#1D4F9C"),  # blue
    "Q3133": ("#00FF00", "#00843D"),  # green
    "Q943": ("#FFFF00", "#FCD116"),  # yellow
    "Q373160": ("#007FFF", "#6CABDD"),  # sky blue
    "Q373058": ("#007FFF", "#2E86C8"),  # azure
    "Q5975887": ("#000080", "#14275A"),  # navy blue
    "Q953243": ("#002366", "#1D4FA0"),  # royal blue
    "Q39338": ("#FFA500", "#F26A21"),  # orange
    "Q3257809": ("#800080", "#4F2D7F"),  # purple
    "Q604079": ("#E0B0FF", "#4F2D7F"),  # mauve
    "Q428124": ("#7F00FF", "#5E3A9A"),  # violet
    "Q208045": ("#FFD700", "#F9B21B"),  # gold
    "Q679355": ("#FFBF00", "#F7B500"),  # amber
    "Q25393814": ("#800000", "#7A1F2B"),  # maroon
    "Q244822": ("#B30033", "#A50044"),  # garnet
    "Q303826": ("#DC143C", "#C41E3A"),  # crimson
    "Q429220": ("#FFC0DB", "#F2A1C3"),  # pink
    "Q180778": ("#00FFFF", "#19A7CE"),  # cyan
}


def kit_shade(colour: Mapping) -> str | None:
    """The hex a club colour ({qid, name, hex} from reference/team_media.json) is drawn in:
    the kit shade of a generic named colour, else Wikidata's own hex."""
    given = valid_hex(colour.get("hex"))
    generic, shade = KIT_SHADES.get(colour.get("qid") or "", (None, None))
    return shade if given is not None and given == generic else given


# Characters XML 1.0 can't hold at all, even escaped (control characters, U+FFFE, U+FFFF).
XML_INVALID = re.compile("[\x00-\x08\x0b\x0c\x0e-\x1f\ufffe\uffff]")


def short_name(name: str) -> str:
    """Three letters for the badge: "Arsenal" -> "ARS", "Aston Villa" -> "AVL",
    "Man United" -> "MUN", "Paris SG" -> "PSG", "Nott'm Forest" -> "NFR".

    The one abbreviation of a team: the generated badge.svg draws it and the API sends it as
    `badge.short` for the website's own badges. A leading word written in two to four capitals
    is kept as the club writes it ("RB Leipzig" -> "RBL", "PSV Eindhoven" -> "PSV"), and a
    four-digit year is skipped ("Iberia 1999" -> "IBE")."""
    text = unicodedata.normalize("NFKD", name)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = re.sub(r"\b[DdLl]['’]", " ", text)  # "d'Escaldes" -> "Escaldes"
    raw = re.findall(r"[A-Za-z0-9]+", re.sub(r"['’]", "", text))
    raw = [w for w in raw if w.lower() not in SKIP_WORDS] or raw
    kept = [w.upper() for w in raw if not re.fullmatch(r"\d{4}", w)] or [w.upper() for w in raw]
    if not kept:
        return "?"
    if re.fullmatch(r"[A-Z]{2,4}", raw[0]) and len(kept) <= 2:  # an acronym leads
        return (kept[0] + (kept[1] if len(kept) > 1 else ""))[:3]
    if len(kept) == 1:
        return kept[0][:3]
    if len(kept) == 2:
        first, second = kept
        if len(second) <= 2:  # "Paris SG" -> "PSG"
            return (first[0] + second)[:3]
        consonant = next((ch for ch in second[1:] if ch not in VOWELS), second[1])
        return first[0] + second[0] + consonant
    return "".join(w[0] for w in kept[:3])


def short_candidates(name: str) -> list[str]:
    """Three-letter abbreviations for a name, best first: `short_name`'s, then the letters of
    the leading words in order (a two-word name's first letter with two of the second word's,
    and so on), for when two teams that meet would otherwise share one."""
    base = short_name(name)
    text = unicodedata.normalize("NFKD", name)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = re.sub(r"\b[DdLl]['’]", " ", text)
    raw = re.findall(r"[A-Za-z0-9]+", re.sub(r"['’]", "", text))
    words = [w.upper() for w in raw if w.lower() not in SKIP_WORDS] or [w.upper() for w in raw]
    out = [base]
    pools = []
    if len(words) >= 2:  # a letter from the first word, then two from the rest
        pools.append((words[0][0], "".join(words[1:])))
    if words:  # the first word's own letters, its first letter always first
        pools.append((words[0][0], words[0][1:] + "".join(words[1:])))
    for first, rest in pools:
        for pair in combinations(rest, 2):
            cand = first + "".join(pair)
            if cand not in out:
                out.append(cand)
    return out


def resolve_shorts(
    names: Mapping[str, str],
    groups: Iterable[Iterable[str]],
    *,
    fixed: Mapping[str, str] | None = None,
    priority: Mapping[str, int] | None = None,
) -> dict[str, str]:
    """team key -> three letters, different for every two teams in the same group (teams that
    meet: a competition's season). `fixed` letters are kept as they are (reviewed by hand);
    otherwise the team with more matches (`priority`) keeps `short_name`'s letters and the
    other takes its next candidate (`short_candidates`) free in all of its groups."""
    fixed = dict(fixed or {})
    priority = priority or {}
    rivals: dict[str, set[str]] = {k: set() for k in names}
    for group in groups:
        members = [k for k in group if k in rivals]
        for k in members:
            rivals[k].update(m for m in members if m != k)
    out = {k: v for k, v in fixed.items() if k in names}
    for key in sorted(names, key=lambda k: (k not in fixed, -priority.get(k, 0), k)):
        if key in out:
            continue
        taken = {out[r] for r in rivals[key] if r in out}
        cands = short_candidates(names[key])
        out[key] = next((c for c in cands if c not in taken), cands[0])
    return out


def _rgb(hex_colour: str) -> tuple[float, float, float]:
    h = hex_colour.lstrip("#")
    return tuple(int(h[i : i + 2], 16) / 255 for i in (0, 2, 4))  # type: ignore[return-value]


def _luminance(hex_colour: str) -> float:
    """WCAG 2 relative luminance."""

    def lin(c: float) -> float:
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = (lin(c) for c in _rgb(hex_colour))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a: str, b: str) -> float:
    la, lb = sorted((_luminance(a), _luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def _saturation(hex_colour: str) -> float:
    return colorsys.rgb_to_hls(*_rgb(hex_colour))[2]


def valid_hex(value: str | None) -> str | None:
    """ "#RRGGBB" from Wikidata's P465 ("FF0000"), or None when it isn't a colour."""
    if value and re.fullmatch(r"#?[0-9A-Fa-f]{6}", value.strip()):
        return "#" + value.strip().lstrip("#").upper()
    return None


def pick_colours(hexes: list[str], team_key: str) -> tuple[str, str]:
    """(background, band): the most saturated colour as the background, and the colour that
    contrasts with it most as the band; a neutral pair when there are no colours."""
    colours = list(dict.fromkeys(h for h in (valid_hex(x) for x in hexes) if h))
    if not colours:
        digest = hashlib.sha256(team_key.encode()).digest()
        return NEUTRAL_PALETTES[digest[0] % len(NEUTRAL_PALETTES)]
    # Ties (e.g. black and white, both unsaturated) go to the darker colour, then the hex.
    background = max(colours, key=lambda h: (_saturation(h), -_luminance(h), h))
    others = [h for h in colours if h != background]
    if not others:
        others = ["#FFFFFF", "#111111"]
    band = max(others, key=lambda h: (contrast(background, h), h))
    return background, band


def badge_svg(
    team_key: str,
    name: str,
    hexes: list[str] | None = None,
    letters: str | None = None,
    in_order: bool = False,
) -> str:
    """The badge as an SVG document (128 x 128). With `in_order`, `hexes` are already in the
    order to draw them (the background, then the band: footy.media.service.ordered_colours);
    otherwise they are picked here. `letters`: the team's resolved short name (else
    `short_name`'s)."""
    name = re.sub(r"\s+", " ", XML_INVALID.sub("", name)).strip()  # escaping can't fix these
    ordered = [h for h in (valid_hex(x) for x in hexes or []) if h]
    if in_order and ordered:
        background = ordered[0]
        band = ordered[1] if len(ordered) > 1 else pick_colours([background], team_key)[1]
    else:
        background, band = pick_colours(hexes or [], team_key)
    letters = letters or short_name(name)
    text = max(["#FFFFFF", "#111111"], key=lambda h: contrast(background, h))
    outline = "#111111" if text == "#FFFFFF" else "#FFFFFF"
    label = f"{letters}: generated badge for {name}, not the club's crest"
    clip = f"clip-{re.sub(r'[^a-z0-9-]', '', team_key.lower()) or 'team'}"
    size = 44 if len(letters) <= 2 else 38
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" width="128" '
        f'height="128" role="img" aria-label={quoteattr(label)} data-generated="footy">\n'
        f"  <title>{escape(name)}: generated badge (not the club's crest)</title>\n"
        f'  <defs><clipPath id="{clip}"><circle cx="64" cy="64" r="58"/></clipPath></defs>\n'
        f'  <circle cx="64" cy="64" r="58" fill="{background}"/>\n'
        f'  <path d="M-8 112 L136 56 L136 80 L-8 136 Z" fill="{band}" '
        f'clip-path="url(#{clip})"/>\n'
        f'  <circle cx="64" cy="64" r="58" fill="none" stroke="{outline}" '
        'stroke-opacity="0.35" stroke-width="4"/>\n'
        f'  <text x="64" y="64" dy="0.35em" text-anchor="middle" font-size="{size}" '
        'font-weight="800" letter-spacing="1" '
        "font-family=\"system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif\" "
        f'fill="{text}" stroke="{outline}" stroke-opacity="0.45" stroke-width="3" '
        f'paint-order="stroke">{escape(letters)}</text>\n'
        "</svg>\n"
    )
