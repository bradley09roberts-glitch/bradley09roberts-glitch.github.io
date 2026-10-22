import xml.etree.ElementTree as ET

from footy.media.badge import (
    NEUTRAL_PALETTES,
    badge_svg,
    contrast,
    kit_shade,
    pick_colours,
    resolve_shorts,
    short_candidates,
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


def test_generic_named_colours_are_drawn_in_kit_shades():
    # Wikidata's own hex for "red" is the pure screen red; a club's red reads as a shirt.
    assert kit_shade({"qid": "Q3142", "name": "red", "hex": "#FF0000"}) == "#C8102E"
    assert kit_shade({"qid": "Q3133", "name": "green", "hex": "00FF00"}) == "#00843D"
    # A hex of the colour's own (not the generic one) is drawn as given; unknown colours too.
    assert kit_shade({"qid": "Q3142", "name": "red", "hex": "#EF0107"}) == "#EF0107"
    assert kit_shade({"qid": "Q10859033", "name": "bordeaux", "hex": "#800032"}) == "#800032"
    assert kit_shade({"qid": "Q219660", "name": "ultramarine", "hex": None}) is None
    # Each shade carries white or near-black letters at 4.5:1 or better.
    from footy.media.badge import KIT_SHADES

    for _, shade in KIT_SHADES.values():
        assert max(contrast(shade, "#FFFFFF"), contrast(shade, "#161616")) >= 4.5, shade


def test_short_candidates_start_with_the_short_name():
    assert short_candidates("Celta") == ["CEL", "CET", "CEA", "CLT", "CLA", "CTA"]
    assert short_candidates("Sheffield United")[0] == "SUN"
    assert len(set(short_candidates("Dundee United"))) == len(short_candidates("Dundee United"))


def test_teams_that_meet_never_share_letters():
    names = {
        "sco-celtic": "Celtic",
        "esp-celta": "Celta",
        "svn-celje": "Celje",
        "eng-sheffield-united": "Sheffield United",
        "eng-sunderland": "Sunderland",
        "eng-arsenal": "Arsenal",
    }
    groups = [
        {"sco-celtic", "esp-celta", "svn-celje"},  # one competition's season
        {"eng-sheffield-united", "eng-sunderland", "eng-arsenal"},
    ]
    played = {"sco-celtic": 400, "esp-celta": 300, "svn-celje": 50, "eng-sunderland": 200}
    out = resolve_shorts(names, groups, priority=played)
    assert out["sco-celtic"] == "CEL"  # the most matches keeps its letters
    assert len({out[k] for k in groups[0]}) == 3 and len({out[k] for k in groups[1]}) == 3
    assert out["eng-sunderland"] == "SUN" and out["eng-arsenal"] == "ARS"
    # Letters reviewed by hand are kept, and the others work round them.
    fixed = resolve_shorts(names, groups, fixed={"eng-sheffield-united": "SHU"}, priority=played)
    assert fixed["eng-sheffield-united"] == "SHU" and fixed["eng-sunderland"] == "SUN"
    # Teams that never meet may share letters.
    apart = resolve_shorts(names, [{"sco-celtic"}, {"esp-celta"}])
    assert apart["sco-celtic"] == apart["esp-celta"] == "CEL"


def test_badge_draws_colours_in_the_order_given():
    svg = badge_svg("x-y", "Real Madrid", ["#FFFFFF", "#4F2D7F"], "RMA", in_order=True)
    assert 'r="58" fill="#FFFFFF"' in svg and 'fill="#4F2D7F"' in svg and ">RMA<" in svg
