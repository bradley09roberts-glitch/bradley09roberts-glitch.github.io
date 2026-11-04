"""Pack the fight review into one compact JSON block for the ledger page (<script id="review-data">).
Inputs (in <scratch>/work): review_core.json, review_profiles.json, ledger.json; stories/*.json (optional)
Output: work/review-data.json
Names inside stories are mapped to "p" (the model's pick) and "o" (its opponent)."""
import glob, json, os, sys
S = sys.argv[1]
RC = json.load(open(f"{S}/work/review_core.json"))
RP = json.load(open(f"{S}/work/review_profiles.json"))
L = json.load(open(f"{S}/work/ledger.json"))
BOUT = {b["id"]: b for e in L["events"] for b in e["bouts"] if b.get("won")}

stories = {}
for fn in sorted(glob.glob(f"{S}/stories/*.json")):
    try: d = json.load(open(fn))
    except Exception: continue
    for bid, st in (d.get("bouts") or {}).items():
        stories[bid] = st
json.dump(stories, open(f"{S}/work/stories.json", "w"))

def side(b, name):
    return "p" if name == b["pick"] else "o" if name == b["opp"] else None

def wl(b, v):   # winner/loser -> p/o
    win = "p" if b["ok"] else "o"
    return {"winner": win, "loser": "o" if win == "p" else "p", "even": "e"}.get(v)

def sd_code(sc, names):
    """One Sherdog scorer's round score -> 'p' / 'o' / 'e', with '8' appended for a 10-8."""
    _, a, b_, name = sc[:4]
    if a == b_: return "e"
    import re, unicodedata
    fold = lambda s: set(re.findall(r"[a-z]+", unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()))
    sp, so = len(fold(name) & fold(names["pick"])), len(fold(name) & fold(names["opp"]))
    hi = "p" if sp > so else "o" if so > sp else None
    if not hi: return "?"
    who = hi if a > b_ else ("o" if hi == "p" else "p")
    return who + ("8" if abs(a - b_) >= 2 else "")

out_b = {}
for bid, r in RC["bouts"].items():
    b = BOUT[bid]
    x = {"v": r["verdict"], "w": r["why"], "ub": r["ub"], "rd": r["rd"], "tm": r["time"], "sec": r["secs"], "sch": r["sched"],
         "how": r["method"], "r": r["rows"], "pr": r["pr"], "own": "".join(r["own"]), "exp": r["exp"]}
    if r.get("dk"): x["dk"] = r["dk"]
    if r.get("judges"): x["j"] = r["judges"]
    if r.get("detail"): x["det"] = r["detail"]
    if r.get("ref"): x["ref"] = r["ref"]
    if r.get("pa") and r["pa"]["flips"]: x["fl"] = r["pa"]["flips"]
    pre = RP["pre_arch"].get(bid)
    if pre: x["sty"] = pre
    sd = r.get("sd")
    if sd:
        x["sd"] = {"a": sd["a"], "t": sd["tot"],
                   "sc": [[n, [sd_code(s, sd["names"]) for s in scs]] for n, scs in sd["scores"]]}
        x["so"] = "".join(o or "-" for o in r["sd_own"])
    st = stories.get(bid)
    if st:
        m = lambda n: side(b, n)
        x["st"] = {
            "s": st.get("story", ""), "tp": st.get("turning_point", ""),
            "rn": [[(m(rr.get("leader")) or "e"), rr.get("note", "")] for rr in st.get("rounds", [])],
            "bf": wl(b, st.get("bf")), "bfn": st.get("bf_note", ""), "af": wl(b, st.get("ahead_at_finish")),
            "td": [m((st.get("first_td") or {}).get("who")), (st.get("first_td") or {}).get("rd"), (st.get("first_td") or {}).get("effect", "")],
            "hu": [m((st.get("first_hurt") or {}).get("who")), (st.get("first_hurt") or {}).get("rd"), (st.get("first_hurt") or {}).get("by", "")],
            "m": [[mm.get("rd"), mm.get("type"), m(mm.get("by")), mm.get("note", "")] for mm in st.get("moments", [])],
            "lw": st.get("loser_weak", []), "lwn": st.get("loser_weak_note", ""),
            "we": st.get("winner_edge", []), "wen": st.get("winner_edge_note", ""),
            "lk": st.get("luck"), "lkn": st.get("luck_note", ""),
        }
    out_b[bid] = x

# aggregates: verdicts, underrated-by, story tags (season and all)
from collections import Counter
agg = RP["agg"]
for win, since in (("season", "2025-01-01"), ("all", "0000")):
    xs = [(bid, RC["bouts"][bid]) for bid in RC["bouts"] if RC["bouts"][bid]["date"] >= since]
    st_ = [(bid, r) for bid, r in xs if bid in stories]
    agg[win]["stories"] = len(st_)
    agg[win]["loser_weak"] = Counter(t for bid, r in st_ for t in stories[bid].get("loser_weak", []))
    agg[win]["winner_edge"] = Counter(t for bid, r in st_ for t in stories[bid].get("winner_edge", []))
    agg[win]["luck_miss"] = Counter(stories[bid].get("luck") for bid, r in st_ if not r["ok"])
    agg[win]["luck_hit"] = Counter(stories[bid].get("luck") for bid, r in st_ if r["ok"])

data = {
    "v": 1, "built": sys.argv[2] if len(sys.argv) > 2 else None,
    "rm": RC["round_model"], "th": RP["thresholds"], "al": RP["arch_label"], "agg": agg, "tdt": RP["td_test"],
    "b": out_b, "f": RP["profiles"],
}
s = json.dumps(data, separators=(",", ":"), ensure_ascii=False)
open(f"{S}/work/review-data.json", "w").write(s)
print("bouts", len(out_b), "with stories", sum(1 for x in out_b.values() if "st" in x), "fighters", len(RP["profiles"]), "bytes", len(s.encode()))
print("  bouts part", len(json.dumps(out_b, separators=(",", ":"), ensure_ascii=False).encode()), "fighters part", len(json.dumps(RP["profiles"], separators=(",", ":"), ensure_ascii=False).encode()))
