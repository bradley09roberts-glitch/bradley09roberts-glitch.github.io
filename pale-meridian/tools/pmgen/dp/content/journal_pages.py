"""Journal recap and people pages. Recaps are listed latest-first; the first matching condition wins."""
from __future__ import annotations


def recaps() -> list[tuple[str, list]]:
    return [
        ("if score #ending pm.world matches 1", [
            "You wrote the eleven names into the Long Chart. The Deepcut is on the map again, and the Pall has nowhere left to hide.",
            "The survey is complete. Odile saw her first sunrise in forty years. Hesper tends a small garden at the mine mouth.",
            "The Vale is yours to explore."]),
        ("if score #ending pm.world matches 2", [
            "You left the Deepcut blank. The rest of Vell cleared; the mine keeps its fog, its eleven, and now its Keeper.",
            "The survey is complete. A faint haze never quite lifts, but every lamp is lit.",
            "The Vale is yours to explore."]),
        ("if score c4.unlooked pm.q matches 2", [
            "The Unlooked is gone and the Great Lens burns over Vellmere.",
            "In the Chart Room the Long Chart glows, except one white space under the north cliffs. Everyone is waiting for your decision."]),
        ("if score c4.lens pm.q matches 2", [
            "You set the Lens Heart in the Great Lens. Something gathered itself out of the fog on the Lens Gallery.",
            "Your friends have come across the lake to the Meridian."]),
        ("if score c4.keeper pm.q matches 2", [
            "Hesper Vane, the Keeper, admitted it: she painted the Deepcut out of the Long Chart and put out the Lens, because her son Tobin died there.",
            "She will not stop you lighting the Lens. She only asked you to think about what a blank is for."]),
        ("if score c3.surge pm.q matches 2", [
            "The Glassworks remembers, but the Deepcut stays in the Pall: it is not on the Chart.",
            "Kiln Three forged a Lens Heart. The way to the Meridian, the Keeper's island in the lake, is open."]),
        ("if score c3.memorial pm.q matches 2", [
            "In the Last Gallery you found eleven pale figures and a wall of blank plaques.",
            "You heard the Keeper strike the Deepcut from the Chart, and her son Tobin promise that the roof would hold."]),
        ("if score c3.remind pm.q matches 2", [
            "Tamsin remembered herself. The fog is attention, she says: the valley stays real because it is looked at.",
            "Forty years ago the Keeper struck the Deepcut from the Long Chart, and the forgetting spread from there."]),
        ("if score c3.arrive pm.q matches 2", [
            "You reached the Glassworks under the north cliffs. The mine behind it, the Deepcut, is where Tamsin went."]),
        ("if score c2.lamp pm.q matches 2", [
            "Aldercross remembers. Bees are back in Brannoc's hives and the windmill lamp burns over the orchard.",
            "Brannoc told you what the fog made everyone forget: forty years ago the Deepcut mine collapsed and eleven miners died, his brother Col among them.",
            "The Keeper of the Meridian, Hesper Vane, had sent them past the safe seam for her great Lens.",
            "Tamsin went north, to the Glassworks under the cliffs."]),
        ("if score c2.hearts pm.q matches 2", [
            "With the Heartwood's three hearts broken, the pale roots let go of the windmill.",
            "Brannoc has his brother's lamp for the windmill cap."]),
        ("if score c2.memories pm.q matches 2", [
            "Aldercross remembered two brothers: Brannoc and Col, and their initials in the old oak.",
            "In the cider-press loft you found Tamsin's note: the fog is worst where the valley refuses to look.",
            "The Heartwood's roots are strangling the windmill that should carry the orchard's lamp."]),
        ("if score c2.arrive pm.q matches 2", [
            "Following Tamsin east, you reached the orchards of Aldercross, grown over with pale oak.",
            "Brannoc, the orchard keeper, wants nothing to do with you, or with his own trees."]),
        ("if score c1.surge pm.q matches 2", [
            "Hollin remembers. The fog rolled back from the village and its people woke from a forty-year evening.",
            "Odile Marsh, the lamplighter, told you the fog came the night the Deepcut mine fell, and the Keeper's great light on the Meridian went dark.",
            "Tamsin passed through Hollin and went east, to the orchards of Aldercross."]),
        ("if score c1.round pm.q matches 2", [
            "You rang the Round, and the latch to the bell tower's lamp cradle fell open.",
            "Hollin's Wakelamp waits to be rebuilt at the top of the tower."]),
        ("if score c1.names pm.q matches 2", [
            "Hollin's four places told you their names: Dunn's bakery, Pell's well, Marsh's hall and Tarn's ferry.",
            "Each family had a bell. The Round, painted in the Hall, says in what order they were rung."]),
        ("if score c1.odile pm.q matches 1..", [
            "You reached Hollin, a village in the fog where people have forgotten their own names.",
            "A lamplighter keeps one lamp lit at the gate and doesn't know why."]),
        ("if score p.road pm.q matches 1..", [
            "You relit the Landing lamp and the fog drew back from the south rim.",
            "The lamp road runs north into the Pall, toward the village of Hollin.",
            "On the road you glimpsed a figure with a lantern. It vanished."]),
        ("if score p.letter pm.q matches 2", [
            "Tamsin Reed, your old mentor, wrote to you from inside the fog of Vell.",
            "She asked you to come yourself, bring light, and relight the lamp at the Landing."]),
        ("", [
            "You have just arrived at the Landing, on the south rim of the Vale of Vell.",
            "A letter waits for you on the noticeboard."]),
    ]


def people() -> list[tuple[str, str, list]]:
    return [
        ("if score p.letter pm.q matches 2", "Tamsin Reed", [
            "Your former mentor at the Chartered Survey. Clever, stubborn, funny when it's least appropriate.",
            "She went into the Pall a year ago and stopped writing. Then her letter came."]),
        ("if score c1.odile pm.q matches 2", "Odile", [
            "Hollin's lamplighter. She has lit one lamp at the gate every evening for longer than she can remember.",
            "Since Hollin woke she knows her full name again: Odile Marsh, who taught the Round in the hall."]),
        ("if score c1.surge pm.q matches 2", "Mirelle Dunn", [
            "Hollin's baker. Brisk and generous; there is a loaf for you every morning."]),
        ("if score c1.surge pm.q matches 2", "Jory Pell", [
            "Hollin's bell-ringer, deaf in one ear and proud of his bells. His sister Agnes went up to the Deepcut and never came back."]),
        ("if score c2.brannoc pm.q matches 2", "Brannoc Hale", [
            "The orchard keeper and beekeeper of Aldercross. Gruff, blunt and fiercely loyal to his trees.",
            "His younger brother Col died in the Deepcut."]),
        ("if score c2.lamp pm.q matches 2 unless score c4.keeper pm.q matches 2", "Hesper Vane", [
            "The Keeper of the Meridian, the island observatory in the lake. Brannoc says she sent the miners past the safe seam.",
            "Nobody you have met has seen her in forty years."]),
        ("if score c4.keeper pm.q matches 2", "Hesper Vane", [
            "The Keeper of the Meridian. Brilliant, proud and hollowed out by guilt.",
            "She sent the Deepcut crew past the safe seam for her Great Lens. Her son Tobin led them. When the mine fell she struck it from the Chart."]),
        ("if score c3.memorial pm.q matches 2", "Tobin Vane", [
            "The crew lead of the Deepcut, and the Keeper's son. He promised the roof would hold."]),
        ("if score c3.log pm.q matches 2", "Silas Crane", [
            "Foreman of the Vell Glassworks. He told Tobin what he thought of the roof, and wrote it down."]),
    ]
