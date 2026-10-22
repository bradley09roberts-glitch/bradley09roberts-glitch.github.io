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


def badge_svg(team_key: str, name: str, hexes: list[str] | None = None) -> str:
    """The badge as an SVG document (128 x 128)."""
    name = re.sub(r"\s+", " ", XML_INVALID.sub("", name)).strip()  # escaping can't fix these
    background, band = pick_colours(hexes or [], team_key)
    letters = short_name(name)
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
