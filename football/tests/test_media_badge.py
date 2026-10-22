import xml.etree.ElementTree as ET

from footy.media.badge import (
    NEUTRAL_PALETTES,
    badge_svg,
    contrast,
    pick_colours,
    short_name,
    valid_hex,
)

SVG = "{http://www.w3.org/2000/svg}"


def test_short_names():
    assert short_name("Arsenal") == "ARS"
    assert short_name("Aston Villa") == "AVL"
    assert short_name("Man United") == "MUN"
    assert short_name("Paris SG") == "PSG"
    assert short_name("Nott'm Forest") == "NFR"
    assert short_name("FC Barcelona") == "BAR"
    assert short_name("Atlètic Club d'Escaldes") == "AES"  # "Club" and "d'" skipped
    assert short_name("Real Club Deportivo Mallorca") == "RDM"  # three words: initials
    assert short_name("Bodø/Glimt") == "BGL"


def test_badge_is_deterministic_valid_svg_and_says_it_is_generated():
    a = badge_svg("eng-aston-villa", "Aston Villa", ["#670E36", "#95BFE5"])
    assert a == badge_svg("eng-aston-villa", "Aston Villa", ["#670E36", "#95BFE5"])
    root = ET.fromstring(a)
    assert root.tag == f"{SVG}svg"
    assert root.get("data-generated") == "footy"
    assert "not the club's crest" in root.get("aria-label")
    assert "generated badge" in root.find(f"{SVG}title").text
    assert root.find(f"{SVG}text").text == "AVL"
    fills = {e.get("fill") for e in root.iter() if e.get("fill")}
    assert {"#670E36", "#95BFE5"} <= fills


def test_badge_escapes_names():
    svg = badge_svg("x-odd", 'A & B <"C">', [])
    root = ET.fromstring(svg)  # parses, so the name was escaped
    assert 'A & B <"C">' in root.find(f"{SVG}title").text


def test_badge_drops_characters_xml_cannot_hold():
    svg = badge_svg("x-odd", "Odd\x00 Town\x1b FC\ufffe", [])
    root = ET.fromstring(svg)  # parses: no control characters left
    assert root.find(f"{SVG}title").text.startswith("Odd Town FC:")
    assert "Odd Town FC" in root.get("aria-label")


def test_neutral_palette_without_colours_is_stable_per_team():
    assert pick_colours([], "eng-arsenal") in NEUTRAL_PALETTES
    assert pick_colours([], "eng-arsenal") == pick_colours([], "eng-arsenal")
    assert pick_colours(["not a colour"], "eng-arsenal") in NEUTRAL_PALETTES


def test_colour_choice_background_most_saturated_band_most_contrasting():
    background, band = pick_colours(["FFFFFF", "DA291C", "000000"], "eng-man-united")
    assert background == "#DA291C"
    assert band == max(["#FFFFFF", "#000000"], key=lambda h: contrast("#DA291C", h))
    # One colour: a white or near-black band, whichever contrasts more.
    background, band = pick_colours(["#FDB913"], "eng-wolves")
    assert (background, band) == ("#FDB913", "#111111")
    assert contrast("#FFFFFF", "#000000") > 20


def test_valid_hex():
    assert valid_hex("ef0107") == "#EF0107"
    assert valid_hex("#EF0107") == "#EF0107"
    assert valid_hex("red") is None
    assert valid_hex(None) is None
