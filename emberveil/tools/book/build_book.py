#!/usr/bin/env python3
"""Build the Wayfarer's Almanac (Patchouli book) from tools/book/content/*.yaml.

Content markup (converted to Patchouli formatting codes):
  blank line          -> paragraph break  $(br2)
  "- item" lines      -> list items       $(li)
  [[target]] / [[target|label]]  -> link to an entry or category (label defaults to its name)
  **bold**            -> $(l)bold$()
  {Thing}             -> highlighted name $(item)Thing$()
  {!Danger}           -> red warning text
  {+Good}             -> green text
  <key:key.inventory> -> the player's *current* binding for that key ($(k:...))
  $(...)              -> raw Patchouli codes pass through

Page types: text, spotlight, entity, crafting, smelting, image, relations, quest, empty, plus the
generated types `stats` (creature numbers from the runtime registry dump + drops from real loot tables)
and `itemstats` (weapon/armour numbers). Long text is split across pages using Minecraft's actual
glyph widths (tools/book/glyph_widths.json) and Patchouli's page geometry.

Validation (--check, default on): every item, entity, recipe, advancement and link must exist in the
registry dump (data/registry.json, produced by the test kit from the running game) or the book.
"""
import json, os, pathlib, re, sys, textwrap
import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
BOOK_DIR = ROOT / "tools/book"
RES = ROOT / "companion-mod/src/main/resources"
BOOK_ID = "almanac"
NS = "emberveil"
LANG_DIR = RES / f"assets/{NS}/patchouli_books/{BOOK_ID}/en_us"
DATA_BOOK = RES / f"data/{NS}/patchouli_books/{BOOK_ID}/book.json"
REGISTRY = pathlib.Path(os.environ.get("EMBERVEIL_REGISTRY", ROOT / "tools/book/data/registry.json"))
LOOT_DIR = pathlib.Path(os.environ.get("EMBERVEIL_EXTRACTED", ROOT / ".cache/extracted"))

GLYPHS = json.loads((BOOK_DIR / "glyph_widths.json").read_text())
LINE_W = 116          # GuiBook.PAGE_WIDTH
LINE_H = 9            # GuiBook.TEXT_LINE_HEIGHT
PAGE_H = 156          # GuiBook.PAGE_HEIGHT
# Where text starts on each page type (Patchouli getTextHeight()), measured from the page top.
TEXT_TOP = {"text_first": 22, "text_titled": 12, "text": -4, "spotlight": 40, "entity": 115,
            "crafting": 97, "crafting2": 150, "smelting": 55, "image": 120, "quest": 22, "link": 22}
SAFETY_LINES = 1      # keep one spare line so rounding differences never overflow

errors, warnings = [], []

# ---------------------------------------------------------------- registry
REG = json.loads(REGISTRY.read_text()) if REGISTRY.exists() else None
ITEMS = set(REG["items"]) if REG else set()
ENTITIES = set(REG["entities"]) if REG else set()
RECIPES = set(REG["recipes"]) if REG else set()
ADVANCEMENTS = set(REG["advancements"]) if REG else set()

def check_item(i, where):
    base = i.split("{")[0].split("[")[0].split("#")[0]
    if base.startswith("emberveil:") or not REG:
        return
    if base not in ITEMS:
        errors.append(f"{where}: unknown item {i}")

# ---------------------------------------------------------------- text measuring
def visible(s):
    """Strip Patchouli codes and return (text, bold flag per char)."""
    out, bold = [], False
    i = 0
    while i < len(s):
        if s.startswith("$(", i):
            j = s.find(")", i)
            code = s[i + 2:j]
            if code in ("l", "bold"): bold = True
            elif code == "" or code == "r": bold = False
            elif code.startswith("k:"): out.append(("[K]", False))
            elif code in ("br", "br2", "2br", "p", "li"): out.append((f"\n{code}\n", False))
            i = j + 1
            continue
        out.append((s[i], bold)); i += 1
    return out

def char_w(c, bold):
    return GLYPHS.get(c, 6) + (1 if bold else 0)

def count_lines(s):
    """Greedy word wrap, the way Patchouli's TextLayouter breaks lines."""
    parts = visible(s)
    lines, x = 1, 0
    word_w = 0
    pending_space = 0
    for token, bold in parts:
        if token.startswith("\n"):
            code = token.strip()
            if code in ("br2", "2br", "p"): lines += 2
            elif code == "br": lines += 1
            elif code == "li": lines += 1; x = 10
            if code != "li": x = 0
            word_w = 0; pending_space = 0
            continue
        if token == "[K]":
            token = "Key"
        for c in token:
            if c == " ":
                x += word_w
                word_w = 0
                pending_space = char_w(" ", bold)
                x += pending_space
                continue
            w = char_w(c, bold)
            if x + word_w + w > LINE_W and x > 0:
                lines += 1
                x = 0
            word_w += w
    return lines

def capacity(kind):
    return (PAGE_H - TEXT_TOP[kind]) // LINE_H - SAFETY_LINES

# ---------------------------------------------------------------- markup
NAMES = {}  # entry/category id -> display name, filled before conversion

def convert(md, where):
    md = textwrap.dedent(md).strip("\n")
    paras = re.split(r"\n\s*\n", md)
    out = []
    for p in paras:
        lines = p.split("\n")
        chunk = []
        for ln in lines:
            ln = ln.rstrip()
            if re.match(r"^\s*[-*] ", ln):
                chunk.append("$(li)" + re.sub(r"^\s*[-*] ", "", ln))
            elif chunk and chunk[-1].startswith("$(li)") and ln.startswith("  "):
                chunk[-1] += " " + ln.strip()
            else:
                if chunk and not chunk[-1].startswith("$(li)"):
                    chunk[-1] += " " + ln.strip()
                else:
                    chunk.append(ln.strip())
        out.append("".join(chunk) if all(c.startswith("$(li)") for c in chunk[1:]) else "$(br)".join(chunk))
    s = "$(br2)".join(out)

    def link(m):
        target, _, label = m.group(1).partition("|")
        target = target.strip()
        if target.startswith("http"):
            return f"$(l:{target}){label or target}$(/l)"
        key = target.split("#")[0]
        if key not in NAMES:
            errors.append(f"{where}: broken link [[{target}]]")
        return f"$(l:{target}){label or NAMES.get(key, key)}$(/l)"
    s = re.sub(r"\[\[([^\]]+)\]\]", link, s)
    s = re.sub(r"\*\*(.+?)\*\*", r"$(l)\1$()", s)
    s = re.sub(r"\{!(.+?)\}", r"$(#a3231a)\1$()", s)
    s = re.sub(r"\{\+(.+?)\}", r"$(#2e6b2a)\1$()", s)
    s = re.sub(r"\{([^{}$]+?)\}", r"$(item)\1$()", s)
    s = re.sub(r"<key:([A-Za-z0-9_.]+)>", r"$(k:\1)", s)
    return s

def split_text(s, first_cap, rest_cap):
    """Split converted text into page-sized chunks at paragraph/sentence boundaries."""
    pieces = re.split(r"(\$\(br2\))", s)
    units = []
    for p in pieces:
        if p == "$(br2)":
            if units: units[-1] += p
            continue
        if count_lines(p) > rest_cap:
            # break long paragraphs at sentence ends
            sents = re.split(r"(?<=[.!?:])\s+", p)
            units.extend(x + " " for x in sents[:-1]); units.append(sents[-1])
        else:
            units.append(p)
    pages, cur, cap = [], "", first_cap
    for u in units:
        cand = cur + u
        if cur and count_lines(cand.rstrip()) > cap:
            pages.append(cur.rstrip().removesuffix("$(br2)").rstrip())
            cur, cap = u, rest_cap
        else:
            cur = cand
    if cur.strip():
        pages.append(cur.rstrip().removesuffix("$(br2)").rstrip())
    return pages

# ---------------------------------------------------------------- generated stat pages
def hearts(hp):
    return f"{hp:g} ({hp / 2:g} hearts)"

def loot_drops(entity_id):
    ns, path = entity_id.split(":")
    f = LOOT_DIR / f"{ns}.json"
    if not f.exists():
        # namespaces whose mod id differs from the jar key
        for g in LOOT_DIR.glob("*.json"):
            d = json.loads(g.read_text())
            if f"{ns}:entities/{path}" in d.get("loot_tables", {}):
                f = g; break
    if not f.exists():
        return None
    tables = json.loads(f.read_text()).get("loot_tables", {})
    t = tables.get(f"{ns}:entities/{path}")
    if not t:
        return None
    drops = []
    def walk(entries, pool_cond):
        for e in entries:
            if e.get("type") in ("minecraft:item",):
                name = e.get("name")
                cnt = None
                chance = None
                for fn in e.get("functions", []):
                    if fn.get("function") == "minecraft:set_count":
                        c = fn.get("count")
                        if isinstance(c, dict) and "min" in c: cnt = f"{c['min']:g}-{c['max']:g}"
                        elif isinstance(c, (int, float)): cnt = f"{c:g}"
                for c in e.get("conditions", []) + pool_cond:
                    if c.get("condition") in ("minecraft:random_chance", "minecraft:random_chance_with_enchanted_bonus", "minecraft:random_chance_with_looting"):
                        ch = c.get("chance")
                        if isinstance(ch, dict): ch = ch.get("value", ch.get("unenchanted_chance"))
                        if ch is None: ch = c.get("unenchanted_chance")
                        if isinstance(ch, (int, float)): chance = ch
                drops.append((name, cnt, chance))
            elif e.get("type") == "minecraft:alternatives" or "children" in e:
                walk(e.get("children", []), pool_cond)
    for pool in t.get("pools", []):
        walk(pool.get("entries", []), pool.get("conditions", []))
    return drops

def item_name(i):
    if REG and i in REG["items"]:
        return REG["items"][i]["name"]
    return i.split(":")[1].replace("_", " ").title()

def stats_text(entity_id, extra=None):
    e = REG["entities"].get(entity_id) if REG else None
    if not e:
        errors.append(f"stats: unknown entity {entity_id}"); return ""
    parts = []
    if "max_health" in e: parts.append(f"$(l)Health$() {hearts(e['max_health'])}")
    if e.get("armor"): parts.append(f"$(l)Armour$() {e['armor']:g}")
    if e.get("attack_damage"): parts.append(f"$(l)Base attack$() {e['attack_damage']:g}")
    if e.get("knockback_resistance"): parts.append(f"$(l)Knockback resist$() {int(e['knockback_resistance']*100)}%")
    if e.get("fire_immune"): parts.append("Immune to fire")
    s = "$(br)".join(parts)
    drops = loot_drops(entity_id)
    if drops:
        seen = []
        for name, cnt, chance in drops:
            if not name or name in [d[0] for d in seen]: continue
            seen.append((name, cnt, chance))
        dl = []
        for name, cnt, chance in seen[:8]:
            label = item_name(name)
            bits = []
            if cnt and cnt not in ("1", "1-1"): bits.append(cnt)
            if chance is not None and chance < 1: bits.append(f"{chance*100:g}%")
            dl.append(f"$(li){label}" + (f" ({', '.join(bits)})" if bits else ""))
        s += "$(br2)$(l)Drops$()" + "".join(dl)
    if extra:
        s += "$(br2)" + extra
    s += "$(br2)$(o)Base values from the game data; difficulty and armour change what you feel.$()"
    return s

def item_stats_text(item_id):
    it = REG["items"].get(item_id) if REG else None
    if not it:
        errors.append(f"itemstats: unknown item {item_id}"); return ""
    parts = [f"$(l){it['name']}$()"]
    dmg = spd = None
    armor = tough = None
    for m in it.get("modifiers", []):
        attr, op, amt, slot = m.split(" ")
        amt = float(amt)
        if "attack_damage" in attr and "MAINHAND" in slot and op == "ADD_VALUE": dmg = (dmg or 0) + amt
        if "attack_speed" in attr and "MAINHAND" in slot and op == "ADD_VALUE": spd = (spd or 0) + amt
        if attr.endswith(".armor") and op == "ADD_VALUE": armor = (armor or 0) + amt
        if "armor_toughness" in attr and op == "ADD_VALUE": tough = (tough or 0) + amt
    if dmg is not None: parts.append(f"Damage {dmg + 1:g}")
    if spd is not None: parts.append(f"Speed {4 + spd:.2g}/s")
    if armor: parts.append(f"Armour {armor:g}")
    if tough: parts.append(f"Toughness {tough:g}")
    if it.get("durability"): parts.append(f"Durability {it['durability']}")
    return ", ".join(parts)

# ---------------------------------------------------------------- building
def page_json(p, entry_where, first_page):
    """Return a list of Patchouli page dicts (a text page may become several)."""
    if isinstance(p, str):
        p = {"text": p}
    out = []
    kind = next(k for k in ("text", "spotlight", "entity", "crafting", "smelting", "blasting", "smithing",
                            "stonecutting", "campfire", "image", "relations", "quest", "empty", "stats",
                            "itemstats", "link", "multiblock") if k in p)
    where = f"{entry_where} page {kind}"
    title = p.get("title")
    if kind == "stats":
        txt = stats_text(p["stats"], convert(p["text"], where) if p.get("text") else None)
        chunks = split_text(txt, capacity("text_titled"), capacity("text"))
        for i, c in enumerate(chunks):
            d = {"type": "patchouli:text", "text": c}
            if i == 0: d["title"] = p.get("title", "Field Notes")
            out.append(d)
        return out
    if kind == "text":
        txt = convert(p["text"], where)
        first_cap = capacity("text_first" if first_page else ("text_titled" if title else "text"))
        chunks = split_text(txt, first_cap, capacity("text"))
        for i, c in enumerate(chunks):
            d = {"type": "patchouli:text", "text": c}
            if i == 0 and title and not first_page: d["title"] = title
            out.append(d)
        return out
    d = {"type": f"patchouli:{kind}"}
    overflow = None
    if kind == "spotlight":
        items = p["spotlight"] if isinstance(p["spotlight"], list) else [p["spotlight"]]
        for i in items: check_item(i, where)
        d["item"] = ",".join(items)
        if title: d["title"] = title
        if p.get("link_recipe"): d["link_recipe"] = True
        cap_kind = "spotlight"
    elif kind == "entity":
        eid = p["entity"].split("{")[0]
        if REG and eid not in ENTITIES:
            errors.append(f"{where}: unknown entity {eid}")
        d["entity"] = p["entity"]
        for k in ("scale", "offset", "rotate", "default_rotation", "name"):
            if k in p: d[k] = p[k]
        cap_kind = "entity"
    elif kind in ("crafting", "smelting", "blasting", "smithing", "stonecutting", "campfire"):
        rid = p[kind]
        if REG and rid not in RECIPES and not rid.startswith("emberveil:"):
            errors.append(f"{where}: unknown recipe {rid}")
        d["recipe"] = rid
        if p.get("recipe2"):
            if REG and p["recipe2"] not in RECIPES: errors.append(f"{where}: unknown recipe {p['recipe2']}")
            d["recipe2"] = p["recipe2"]
        if title: d["title"] = title
        cap_kind = "crafting2" if p.get("recipe2") else ("crafting" if kind in ("crafting", "smithing") else "smelting")
    elif kind == "image":
        d["images"] = p["image"] if isinstance(p["image"], list) else [p["image"]]
        d["border"] = p.get("border", True)
        if title: d["title"] = title
        cap_kind = "image"
    elif kind == "relations":
        ents = p["relations"]
        for e in ents:
            if e not in NAMES: errors.append(f"{where}: relations target {e} missing")
        d["entries"] = [f"{NS}:{e}" if ":" not in e else e for e in ents]
        d["title"] = title or "See Also"
        cap_kind = None
        if p.get("text"): d["text"] = convert(p["text"], where)
    elif kind == "quest":
        adv = p["quest"]
        if REG and adv not in ADVANCEMENTS and not adv.startswith("emberveil:"):
            errors.append(f"{where}: unknown advancement {adv}")
        d["trigger"] = adv
        if title: d["title"] = title
        cap_kind = "quest"
    elif kind == "itemstats":
        lines = [item_stats_text(i) for i in p["itemstats"]]
        d = {"type": "patchouli:text", "title": title or "Numbers", "text": "$(br2)".join(lines) + "$(br2)$(o)Damage/speed as shown on the tooltip in the main hand.$()"}
        return [d]
    elif kind == "empty":
        return [{"type": "patchouli:empty", "draw_filler": True}]
    elif kind == "link":
        d["url"] = p["link"]; d["link_text"] = p.get("link_text", "Open")
        cap_kind = "link"
    else:
        raise ValueError(kind)
    if p.get("text") and kind not in ("relations",):
        txt = convert(p["text"], where)
        cap = capacity(cap_kind) if cap_kind else 99
        if count_lines(txt) > cap:
            chunks = split_text(txt, cap, capacity("text"))
            d["text"] = chunks[0]
            overflow = chunks[1:]
        else:
            d["text"] = txt
    out.append(d)
    for c in overflow or []:
        out.append({"type": "patchouli:text", "text": c})
    return out

def main():
    check = "--no-check" not in sys.argv
    cfg = yaml.safe_load((BOOK_DIR / "book.yaml").read_text())
    files = sorted((BOOK_DIR / "content").glob("*.yaml"))
    cats, entries = [], []
    for f in files:
        doc = yaml.safe_load(f.read_text())
        for c in doc.get("categories", []):
            cats.append(c)
        for e in doc.get("entries", []):
            e["_file"] = f.name
            entries.append(e)
    for c in cats: NAMES[c["id"]] = c["name"]
    for e in entries:
        eid = f"{e['category']}/{e['id']}"
        if eid in NAMES: errors.append(f"duplicate entry {eid}")
        NAMES[eid] = e["name"]
    cat_ids = {c["id"] for c in cats}

    # clean output
    if LANG_DIR.exists():
        for p in sorted(LANG_DIR.rglob("*.json"), reverse=True): p.unlink()
    (LANG_DIR / "categories").mkdir(parents=True, exist_ok=True)
    (LANG_DIR / "entries").mkdir(parents=True, exist_ok=True)

    for c in cats:
        check_item(c["icon"], f"category {c['id']}") if ":" in c["icon"] and not c["icon"].endswith(".png") else None
        d = {"name": c["name"], "description": convert(c["description"], f"category {c['id']}"),
             "icon": c["icon"], "sortnum": c.get("sortnum", 0)}
        if c.get("parent"):
            if c["parent"] not in cat_ids: errors.append(f"category {c['id']}: unknown parent {c['parent']}")
            d["parent"] = f"{NS}:{c['parent']}"
        out = LANG_DIR / "categories" / f"{c['id']}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(d, indent=1, ensure_ascii=False) + "\n")

    total_pages = 0
    for e in entries:
        where = f"{e['_file']}:{e['category']}/{e['id']}"
        if e["category"] not in cat_ids: errors.append(f"{where}: unknown category")
        check_item(e["icon"], where) if ":" in e["icon"] and not e["icon"].endswith(".png") else None
        pages = []
        for i, p in enumerate(e["pages"]):
            pages.extend(page_json(p, where, first_page=(len(pages) == 0)))
        if pages and pages[0]["type"] != "patchouli:text":
            # Patchouli draws the entry title only on a text first page; others carry their own header.
            pass
        rel = e.get("related")
        if rel:
            for r in rel:
                if r not in NAMES: errors.append(f"{where}: related target {r} missing")
            pages.append({"type": "patchouli:relations", "title": "See Also",
                          "entries": [f"{NS}:{r}" for r in rel]})
        d = {"name": e["name"], "icon": e["icon"], "category": f"{NS}:{e['category']}", "pages": pages}
        for k in ("sortnum", "priority", "read_by_default", "advancement", "secret", "entry_color"):
            if k in e: d[k] = e[k]
        out = LANG_DIR / "entries" / e["category"] / f"{e['id']}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(d, indent=1, ensure_ascii=False) + "\n")
        total_pages += len(pages)

    # landing text: a clickable, labelled list of the top-level categories
    top = sorted([c for c in cats if not c.get("parent")], key=lambda c: c.get("sortnum", 0))
    rows = []
    for row in cfg["landing_rows"]:
        cells = []
        for cid, label in zip(row[0::2], row[1::2]):
            if cid not in cat_ids: errors.append(f"landing: unknown category {cid}")
            cells.append(f"$(l:{cid}){label}$(/l)")
        rows.append(" $(#7A2E0E)\u00b7$() ".join(cells))
    landing = convert(cfg["landing_intro"], "landing") + "$(br2)" + "$(br)".join(rows)
    book = dict(cfg["book"])
    book["landing_text"] = landing
    DATA_BOOK.parent.mkdir(parents=True, exist_ok=True)
    DATA_BOOK.write_text(json.dumps(book, indent=1, ensure_ascii=False) + "\n")

    print(f"{len(cats)} categories, {len(entries)} entries, {total_pages} pages")
    for w in warnings: print("WARN", w)
    if errors:
        for er in errors: print("ERROR", er)
        if check: sys.exit(1)

if __name__ == "__main__":
    main()
