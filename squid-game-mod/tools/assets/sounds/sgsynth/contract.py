"""sgsynth.contract - the sound-event contract (section 3 of docs/ASSET_CONTRACT.md).

``CONTRACT`` maps every event id Java registers (``squidgame:<id>``) to its target duration in
seconds and whether it is a seamless loop.  gen_sounds.py and validate_sounds.py both use it.
Syllable durations in the contract are a 0.25-0.45 s range (target = 0.35 +-35 %), the last
syllable is the 0.6 s held note.
"""
from __future__ import annotations

# (event id, target seconds, loop?)
_ROWS = [
    *[(f"doll.syllable_{i}", 0.35, False) for i in range(1, 10)],
    ("doll.syllable_10", 0.6, False),
    ("doll.turn_servo", 1.0, False),
    ("doll.lock_on", 0.4, False),
    ("doll.eyes_on", 0.7, False),
    ("doll.eyes_off", 0.5, False),
    ("doll.scan_beep", 0.3, False),
    ("announce.chime", 1.6, False),
    ("announce.chime_alert", 1.2, False),
    ("game.start_horn", 1.5, False),
    ("game.end_buzzer", 1.2, False),
    ("game.win_fanfare", 3.0, False),
    ("game.results_sting", 2.0, False),
    ("countdown.tick", 0.15, False),
    ("countdown.beep", 0.25, False),
    ("countdown.final", 0.6, False),
    ("elimination.crack", 0.5, False),
    ("elimination.buzzer", 0.9, False),
    ("elimination.body_fall", 0.4, False),
    ("door.slide_open", 1.2, False),
    ("door.slide_close", 1.2, False),
    ("door.lock", 0.4, False),
    ("glass.crack", 0.7, False),
    ("glass.shatter", 1.0, False),
    ("rope.creak", 0.8, False),
    ("rope.strain", 1.2, False),
    ("marble.click", 0.2, False),
    ("marble.drop", 0.3, False),
    ("marble.roll", 1.0, False),
    ("needle.scratch", 0.3, False),
    ("dalgona.crack", 0.4, False),
    ("dalgona.snap", 0.6, False),
    ("danger.heartbeat", 1.0, False),
    ("danger.sting", 0.8, False),
    ("ui.select", 0.15, False),
    ("ui.confirm", 0.4, False),
    ("ui.deny", 0.3, False),
    ("ui.number_call", 0.6, False),
    ("ambient.dorm", 20.0, True),
    ("ambient.playground", 20.0, True),
    ("ambient.industrial", 20.0, True),
    ("ambient.alley", 20.0, True),
    ("music.lobby", 60.0, True),
    ("music.tension", 45.0, True),
    ("music.final", 60.0, True),
]

CONTRACT = {eid: {"target": t, "loop": loop} for eid, t, loop in _ROWS}
ORDER = [eid for eid, _, _ in _ROWS]
TOLERANCE = 0.35
