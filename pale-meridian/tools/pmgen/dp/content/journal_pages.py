"""Journal recap and people pages. Recaps are listed latest-first; the first matching condition wins."""
from __future__ import annotations


def recaps() -> list[tuple[str, list]]:
    return [
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
        ("if score c2.lamp pm.q matches 2", "Hesper Vane", [
            "The Keeper of the Meridian, the island observatory in the lake. Brannoc says she sent the miners past the safe seam.",
            "Nobody you have met has seen her in forty years."]),
    ]
