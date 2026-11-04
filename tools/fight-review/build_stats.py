"""Turn the UFCStats scrape (Greco1899/scrape_ufc_stats CSVs) into one JSON of every UFC fight
with round-by-round stats per fighter, keyed by the UFCStats fight id (the ledger's bout id).

Round row layout (per fighter, per round):
  [kd, sigL, sigA, totL, totA, tdL, tdA, sub, rev, ctrl_s, headL, headA, bodyL, bodyA, legL, legA,
   distL, distA, clinchL, clinchA, groundL, groundA]
"""
import csv, json, re, sys
from datetime import datetime
RAW, OUT = sys.argv[1], sys.argv[2]
def rows(name): return list(csv.DictReader(open(f"{RAW}/{name}", encoding="utf8")))
def norm(s): return re.sub(r"\s+", " ", (s or "").strip())
def of(s):
    m = re.match(r"\s*(\d+)\s+of\s+(\d+)", s or "")
    return (int(m.group(1)), int(m.group(2))) if m else (0, 0)
def num(s):
    try: return int(float(s))
    except Exception: return 0
def mmss(s):
    m = re.match(r"\s*(\d+):(\d+)", s or "")
    return int(m.group(1)) * 60 + int(m.group(2)) if m else 0

dates = {}
for r in rows("ufc_event_details.csv"):
    try: dates[norm(r["EVENT"])] = datetime.strptime(norm(r["DATE"]), "%B %d, %Y").strftime("%Y-%m-%d")
    except Exception: pass

# event names sometimes differ between the CSVs ("UFC Fight Night: Lopes vs. Silva" vs "Noche UFC: Lopes vs. Silva"):
# fall back to the part after the colon
dates_h = {}
for k, v in dates.items(): dates_h.setdefault(k.split(":", 1)[-1].strip(), v)
fights, key2id = {}, {}
for r in rows("ufc_fight_results.csv"):
    fid = r["URL"].rstrip("/").split("/")[-1]
    ev, bout = norm(r["EVENT"]), norm(r["BOUT"])
    names = [norm(x) for x in re.split(r"\s+vs\.\s+", bout, maxsplit=1)]
    fmt = norm(r["TIME FORMAT"])
    rd, tm = num(r["ROUND"]), mmss(r["TIME"])
    m = re.match(r"(\d+) Rnd \(([\d-]+)\)", fmt)
    lens = [int(x) * 60 for x in m.group(2).split("-")] if m else [300] * 5
    secs = sum(lens[: max(rd - 1, 0)]) + tm if rd else None
    det = norm(r["DETAILS"])
    o = norm(r["OUTCOME"]).split("/")
    judges = []
    if norm(r["METHOD"]).startswith("Decision"):
        # UFCStats lists each card as "loser score - winner score" (lower first), not in the bout's name order.
        # Re-orient to the bout's name order: [judge, score of names[0], score of names[1]].
        for j, a, b in re.findall(r"([A-Za-z'.\- ]+?)\s*(\d+)\s*-\s*(\d+)\.?", det):
            j = re.sub(r"^[\s.]+", "", j).strip()
            j = re.split(r"(?<=[a-z])(?=[A-Z][a-z]+ [A-Z])", j)[-1].strip()   # drop a glued prefix ("...by SongDerek Cleary")
            a, b = int(a), int(b)
            if o[0] == "W": judges.append([j, b, a])      # names[0] won: his score is the second number
            elif o[0] == "L": judges.append([j, a, b])    # names[0] lost: his score is the first number
            else: judges.append([j, a, b])                # draw or no contest: order unknown, keep as listed
    clean_det = re.sub(r"^to(?:\s+(?:At Distance|On Ground|In Clinch|From [A-Z][a-z]+(?: [A-Z][a-z]+)*))?\s*", "", det).strip()
    clean_det = clean_det[:1].upper() + clean_det[1:]
    fights[fid] = {"ev": ev, "d": dates.get(ev) or dates_h.get(ev.split(":", 1)[-1].strip()), "names": names, "out": o, "method": norm(r["METHOD"]),
                   "detail": clean_det if not judges else "", "judges": judges, "rd": rd, "time": norm(r["TIME"]),
                   "fmt": fmt, "sched": len(lens) if m else None, "secs": secs, "wc": norm(r["WEIGHTCLASS"]),
                   "ref": norm(r["REFEREE"]), "rounds": {}}
    key2id[(ev, bout)] = fid

missing = 0
for r in rows("ufc_fight_stats.csv"):
    fid = key2id.get((norm(r["EVENT"]), norm(r["BOUT"])))
    if not fid: missing += 1; continue
    rn = re.match(r"Round (\d+)", r["ROUND"] or "")
    if not rn: continue
    rn = int(rn.group(1))
    row = [num(r["KD"]), *of(r["SIG.STR."]), *of(r["TOTAL STR."]), *of(r["TD"]), num(r["SUB.ATT"]), num(r["REV."]),
           mmss(r["CTRL"]), *of(r["HEAD"]), *of(r["BODY"]), *of(r["LEG"]), *of(r["DISTANCE"]), *of(r["CLINCH"]), *of(r["GROUND"])]
    per = fights[fid]["rounds"].setdefault(norm(r["FIGHTER"]), {})
    per[rn] = row
for f in fights.values():
    f["rounds"] = {n: [per[k] for k in sorted(per)] for n, per in f["rounds"].items()}
json.dump(fights, open(OUT, "w"), separators=(",", ":"))
print("fights", len(fights), "with rounds", sum(1 for f in fights.values() if f["rounds"]), "unmatched stat rows", missing)
