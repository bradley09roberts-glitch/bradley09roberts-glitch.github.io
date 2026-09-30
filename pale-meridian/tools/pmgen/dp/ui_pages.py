"""Journal pages that depend on campaign content (recap, people, the Eleven)."""
from __future__ import annotations

from . import dialog, fid, fn
from .engine import R


def generate(recaps: list[tuple[str, list]], people: list[tuple[str, str, list]], eleven: list[tuple[str, str]]) -> None:
    # recap: the latest recap whose condition matches (list ordered from latest to earliest)
    lines = []
    for i, (cond, paragraphs) in enumerate(recaps):
        did = f"journal/recap/{i}"
        dialog(did, {
            "type": "minecraft:notice",
            "title": {"text": "The story so far", "color": "aqua"},
            "body": [{"type": "minecraft:plain_message", "width": 320, "contents": p if isinstance(p, dict) else {"text": p, "color": "gray"}} for p in paragraphs],
            "action": {"label": {"text": "Back to the journal"}, "action": {"type": "minecraft:run_command", "command": "/trigger pm.ui set 9"}},
        })
        lines.append(f"execute {cond} run return run dialog show @s {fid(did)}".replace("execute  run", "execute run"))
    fn("ui/recap", lines + [f"dialog show @s {fid('journal/free_play')}"])
    # people: one entry per person, shown when known
    body = []
    for cond, name, lines_ in people:
        body.append((cond, name, lines_))
    pl = ["data modify storage palemeridian:tmp people set value []"]
    for i, (cond, name, desc) in enumerate(body):
        dialog(f"journal/people/{i}", {
            "type": "minecraft:notice",
            "title": {"text": name, "color": "aqua"},
            "body": [{"type": "minecraft:plain_message", "width": 320, "contents": {"text": d, "color": "gray"}} for d in desc],
            "action": {"label": {"text": "Back"}, "action": {"type": "minecraft:run_command", "command": "/trigger pm.ui set 3"}},
        })
    # a list dialog of known people is built from static entries gated by conditions
    buttons = []
    for i, (cond, name, desc) in enumerate(body):
        fn(f"ui/people/p{i}", [f"execute {cond} run dialog show @s {fid('journal/people/' + str(i))}".replace("execute  run", "execute run")])
    fn("ui/people", [f"dialog show @s {fid('journal/people_index')}"])
    dialog("journal/people_index", {
        "type": "minecraft:multi_action",
        "title": {"text": "People of the Vale", "color": "aqua"},
        "body": [{"type": "minecraft:plain_message", "contents": {"text": "Only people you have met are described.", "color": "gray"}}],
        "columns": 2,
        "actions": [{"label": {"text": name}, "width": 150, "action": {"type": "minecraft:run_command", "command": f"/trigger pm.ui set {100 + i}"}}
                    for i, (cond, name, desc) in enumerate(body)] or [{"label": {"text": "(nobody yet)"}, "width": 150}],
        "exit_action": {"label": {"text": "Back"}, "action": {"type": "minecraft:run_command", "command": "/trigger pm.ui set 9"}},
        "after_action": "close",
        "pause": False,
    })
    R.functions["ui/people_dispatch"] = [f"execute if score #ui pm.tmp matches {100 + i} run function {fid('ui/people/p' + str(i))}" for i in range(len(body))]
    # the Eleven
    el = []
    for i, (flag, name) in enumerate(eleven):
        el.append(f"execute if score {flag} pm.world matches 1 run data modify storage palemeridian:tmp eleven append value \"{name}\"")
    fn("ui/eleven", ["data modify storage palemeridian:tmp eleven set value []"] + el + [f"function {fid('ui/_eleven_show')}"])
    fn("ui/_eleven_show", [
        "execute store result score #n pm.tmp run data get storage palemeridian:tmp eleven",
        f"execute if score #n pm.tmp matches 0 run return run dialog show @s {fid('journal/eleven_none')}",
        "data modify storage palemeridian:tmp elevenj set value {list:\"\"}",
        f"function {fid('ui/_eleven_join')}",
        f"function {fid('ui/_eleven_dialog')} with storage palemeridian:tmp elevenj",
    ])
    fn("ui/_eleven_join", [
        "execute unless data storage palemeridian:tmp eleven[0] run return 0",
        "data modify storage palemeridian:tmp elevenj.item set from storage palemeridian:tmp eleven[0]",
        f"function {fid('ui/_eleven_cat')} with storage palemeridian:tmp elevenj",
        "data remove storage palemeridian:tmp eleven[0]",
        f"function {fid('ui/_eleven_join')}",
    ])
    fn("ui/_eleven_cat", ["$data modify storage palemeridian:tmp elevenj.list set value \"$(list)$(item)  ·  \""])
    fn("ui/_eleven_dialog", [
        "$dialog show @s {type:\"minecraft:notice\",title:{text:\"Names found\",color:\"aqua\"},body:[{type:\"minecraft:plain_message\",width:300,contents:{text:\"$(list)\",color:\"white\"}}],action:{label:{text:\"Back\"},action:{type:\"minecraft:run_command\",command:\"/trigger pm.ui set 9\"}}}",
    ])
    dialog("journal/eleven_none", {
        "type": "minecraft:notice",
        "title": {"text": "Names found", "color": "aqua"},
        "body": [{"type": "minecraft:plain_message", "contents": {"text": "You have not found any of these names yet.", "color": "gray"}}],
        "action": {"label": {"text": "Back"}, "action": {"type": "minecraft:run_command", "command": "/trigger pm.ui set 9"}},
    })
