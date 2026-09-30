"""Story items given by functions (written books with clickable journal links, keepsakes, key items)."""
from __future__ import annotations

from . import fn, snbt


def book_give(title: str, author: str, pages: list, item_id: str, lore: list | None = None, resolved: bool = True) -> str:
    comps = {
        "minecraft:written_book_content": {
            "title": {"raw": title},
            "author": author,
            "pages": [{"raw": p} for p in pages],
            "resolved": resolved,
        },
        "minecraft:custom_data": {"pm": {"item": item_id}},
    }
    if lore:
        comps["minecraft:lore"] = [{"text": l, "italic": False, "color": "gray"} for l in lore]
    inner = ",".join(f"{k}={snbt(v)}" for k, v in comps.items())
    return f"give @s minecraft:written_book[{inner}] 1"


def link(label: str, ui: int, color: str = "dark_aqua") -> dict:
    return {"text": label, "color": color, "underlined": True,
            "click_event": {"action": "run_command", "command": f"/trigger pm.ui set {ui}"}}


def generate() -> None:
    field_book_pages = [
        [{"text": "FIELD BOOK\n", "bold": True},
         {"text": "Chartered Survey — Vell\n\n", "color": "dark_gray", "italic": True},
         link("» Open the journal\n\n", 9),
         link("» Current objective\n\n", 1),
         link("» The story so far\n\n", 2),
         link("» Help & controls\n\n", 5),
         link("» Comfort settings", 6)],
        [{"text": "Surveyor's rules\n\n", "bold": True},
         {"text": "1. Carry light in the fog.\n\n2. Look at what moves when you don't.\n\n3. Write down every name you find. Somebody has to.", "color": "black"}],
    ]
    fn("items/field_book", [book_give("Field Book", "The Surveyor", field_book_pages, "field_book",
                                      lore=["Your survey notes.", "Also: Quick Actions key or pause menu."])])
    letter_pages = [
        [{"text": "Surveyor ", "color": "black"}, {"selector": "@s", "color": "black"}, {"text": " —\n\nIf this reaches you, then I'm sorry, and also: I was right.\n\nThe Vale of Vell is not gone. It is here, under the fog they call the Pall.", "color": "black"}],
        [{"text": "There are people in it who have forgotten their own names.\n\nDon't send the Survey. Come yourself. Bring light. The fog is cold without it.", "color": "black"}],
        [{"text": "The lamp at the Landing has gone dark. Light it and the road will remember you.\n\nMy camp is up the road. Take what you need.\n\n— T.R.", "color": "black"}],
        [{"text": "P.S. If you see me in the fog, don't follow.\n\nIt won't be me.\n\nNot yet.", "color": "dark_gray", "italic": True}],
    ]
    fn("items/letter", [
        "execute if items entity @s container.* *[minecraft:custom_data~{pm:{item:\"letter\"}}] run return fail",
        book_give("Tamsin's Letter", "T. Reed", letter_pages, "letter", lore=["Water-stained. Addressed to you."], resolved=False),
    ])
