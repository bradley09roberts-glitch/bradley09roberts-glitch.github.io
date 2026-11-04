"""Validate one event's story file against the spec and the UFCStats numbers.
Usage: validate_story.py <packets/index.json> <slug> <story.json>   -> prints OK or a list of problems."""
import json, re, sys, os
idx_path, slug, path = sys.argv[1], sys.argv[2], sys.argv[3]
S = os.path.dirname(os.path.dirname(idx_path))
IDX = {x["slug"]: x for x in json.load(open(idx_path))}
U = json.load(open(f"{S}/work/ufc_fights.json"))
SD = json.load(open(f"{S}/sherdog/matched.json"))
L = json.load(open(f"{S}/work/ledger.json"))
BOUT = {b["id"]: b for e in L["events"] for b in e["bouts"] if b.get("won")}
TDL, KD = 5, 0
WEAK = set("takedown_defence bottom_game clinch_fence got_hurt_standing body_shots leg_kicks faded_late slow_start outworked_at_range walked_onto_counters submission_defence scrambles_back_taken low_output size_strength cut_or_injury fouls one_shot none_clear".split())
EDGE = set("wrestling top_control clinch_work power volume counters body_work leg_kicks submissions scrambles pace_cardio size_strength durability adjustment opportunism".split())
MOM = set("takedown knockdown hurt sub_attempt reversal back_take ground_and_pound clinch_control body_shot leg_kicks comeback cut injury point_deduction foul fatigue finish".split())
LIMITS = {"story": 60, "turning_point": 30, "bf_note": 25, "loser_weak_note": 30, "winner_edge_note": 30, "luck_note": 25}
errs = []
try:
    D = json.load(open(path))
except Exception as e:
    print("PROBLEM: the file is not valid JSON:", e); sys.exit(1)
x = IDX.get(slug)
if not x: print("PROBLEM: unknown slug", slug); sys.exit(1)
B = D.get("bouts") if isinstance(D, dict) else None
if not isinstance(B, dict): print("PROBLEM: top level needs a 'bouts' object"); sys.exit(1)
missing = [b for b in x["bouts"] if b not in B]; extra = [b for b in B if b not in x["bouts"]]
if missing: errs.append(f"missing bouts: {missing}")
if extra: errs.append(f"unknown bout ids: {extra}")
words = lambda s: len(str(s or "").split())
def ngrams(t, n=7):
    w = re.findall(r"[a-z0-9']+", t.lower()); return {" ".join(w[i:i + n]) for i in range(len(w) - n + 1)}
for bid in x["bouts"]:
    st = B.get(bid)
    if not isinstance(st, dict): continue
    b, f = BOUT[bid], U[bid]
    names = {b["pick"], b["opp"]}
    winner, loser = (b["pick"], b["opp"]) if b["ok"] else (b["opp"], b["pick"])
    P = f["rounds"].get(b["pick"], []); O = f["rounds"].get(b["opp"], [])
    nr = max(len(P), len(O)); rowsby = {b["pick"]: P, b["opp"]: O}
    finish = not f["method"].startswith("Decision")
    tag = f"[{bid} {b['pick']} vs {b['opp']}]"
    for k, lim in LIMITS.items():
        if k not in st: errs.append(f"{tag} missing field {k}")
        elif words(st[k]) > lim: errs.append(f"{tag} {k} has {words(st[k])} words (limit {lim})")
    if not st.get("story"): errs.append(f"{tag} empty story")
    rs = st.get("rounds")
    if not isinstance(rs, list) or len(rs) != nr: errs.append(f"{tag} rounds must have one entry per round fought ({nr})")
    else:
        for r in rs:
            if r.get("leader") not in names | {"even"}: errs.append(f"{tag} round {r.get('rd')} leader must be a fighter's exact name or 'even'")
            if words(r.get("note")) > 18: errs.append(f"{tag} round {r.get('rd')} note too long")
    for k in ("bf", "ahead_at_finish"):
        v = st.get(k)
        if finish and v not in ("winner", "loser", "even"): errs.append(f"{tag} {k} must be winner/loser/even for a finish")
        if not finish and v != "n/a": errs.append(f"{tag} {k} must be 'n/a' for a decision")
    ftd = st.get("first_td") or {}
    first = next((i + 1 for i in range(nr) if (P[i][TDL] if i < len(P) else 0) + (O[i][TDL] if i < len(O) else 0) > 0), None)
    if first is None:
        if ftd.get("who") != "none": errs.append(f"{tag} UFCStats shows no takedowns, so first_td.who must be 'none'")
    else:
        w = ftd.get("who")
        if w not in names: errs.append(f"{tag} first_td.who must be one of the two names (UFCStats has a takedown in round {first})")
        else:
            if ftd.get("rd") != first: errs.append(f"{tag} first_td.rd should be {first} (the first round with a takedown on UFCStats)")
            elif (rowsby[w][first - 1][TDL] if first - 1 < len(rowsby[w]) else 0) == 0:
                errs.append(f"{tag} {w} landed no takedown in round {first} on UFCStats; the first takedown was by the other fighter")
    fh = st.get("first_hurt") or {}
    if fh.get("who") not in names | {"none"}: errs.append(f"{tag} first_hurt.who must be a fighter's exact name or 'none'")
    ms = st.get("moments") or []
    if len(ms) > 6: errs.append(f"{tag} at most 6 moments")
    for m in ms:
        if m.get("type") not in MOM: errs.append(f"{tag} unknown moment type {m.get('type')}")
        if m.get("by") not in names: errs.append(f"{tag} moment 'by' must be a fighter's exact name")
        rd = m.get("rd")
        if not isinstance(rd, int) or not 1 <= rd <= nr: errs.append(f"{tag} moment round {rd} outside 1..{nr}"); continue
        if m.get("type") == "knockdown" and m.get("by") in names and (rowsby[m["by"]][rd - 1][KD] if rd - 1 < len(rowsby[m["by"]]) else 0) == 0:
            errs.append(f"{tag} UFCStats has no knockdown by {m['by']} in round {rd}; call it 'hurt' instead")
        if m.get("type") == "takedown" and m.get("by") in names and (rowsby[m["by"]][rd - 1][TDL] if rd - 1 < len(rowsby[m["by"]]) else 0) == 0:
            errs.append(f"{tag} UFCStats has no takedown by {m['by']} in round {rd}")
        if words(m.get("note")) > 15: errs.append(f"{tag} moment note too long")
    lw = st.get("loser_weak") or []
    if not isinstance(lw, list) or len(lw) > 3 or any(t not in WEAK for t in lw): errs.append(f"{tag} loser_weak must be 0-3 tags from the list")
    we = st.get("winner_edge") or []
    if not isinstance(we, list) or not 1 <= len(we) <= 3 or any(t not in EDGE for t in we): errs.append(f"{tag} winner_edge must be 1-3 tags from the list")
    if st.get("luck") not in ("none", "some", "big"): errs.append(f"{tag} luck must be none/some/big")
    src = " ".join(r["text"] for r in SD[bid]["rounds"])
    mine = " ".join(str(st.get(k, "")) for k in ("story", "turning_point", "bf_note", "loser_weak_note", "winner_edge_note", "luck_note")) + " " + \
           " ".join(str(r.get("note", "")) for r in (rs or [])) + " " + " ".join(str(m.get("note", "")) for m in ms)
    cp = ngrams(mine) & ngrams(src)
    if cp: errs.append(f"{tag} copies Sherdog's wording (e.g. '{sorted(cp)[0]}'); rewrite in your own words")
print("OK" if not errs else "PROBLEMS (%d):\n" % len(errs) + "\n".join(errs[:60]))
