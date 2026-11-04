"""Fight review, part 2: fighter weak spots and the season-wide checks.

* Rolling pre-fight UFC rates for every fighter (only fights before each date), used to label each opponent's style
  as it stood on fight night (no hindsight).
* Per ledger fighter: how they lose, takedown defence in practice, control conceded, damage absorbed, knockdowns
  suffered, round-by-round fade, what happens after conceding the first takedown, and results against each style.
  Rates are shrunk toward the division average so a three-fight sample can't shout.
* Aggregates: verdict counts, who underrated the winner, the model's and the market's record against each opponent style
  and against the model's own projected grappling gap, and a walk-forward test of whether "vulnerable to takedowns vs a
  wrestler" adds anything beyond the model's and the market's prices.

Inputs: work/ledger.json, work/ufc_fights.json, work/review_core.json, work/stories.json (optional)
Output: work/review_profiles.json
"""
import json, math, os, re, sys
from collections import defaultdict, Counter
import numpy as np

S = sys.argv[1]
L = json.load(open(f"{S}/work/ledger.json"))
U = json.load(open(f"{S}/work/ufc_fights.json"))
RC = json.load(open(f"{S}/work/review_core.json"))
RB, RM = RC["bouts"], RC["round_model"]
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from stories_load import load_stories
ST = load_stories(S)
FZ = L["cards"]["fighters"]

KD, SIGL, SIGA, TOTL, TOTA, TDL, TDA, SUB, REV, CTRL, HEADL, HEADA, BODYL, BODYA, LEGL, LEGA, DISTL, DISTA, CLL, CLA, GRL, GRA = range(22)
ZERO = [0] * 22
W = np.array([RM["weights"][k] for k in ["sig_strikes", "knockdowns", "takedowns", "control_min", "sub_attempts", "other_strikes", "reversals"]])
def feats(rp, ro):
    return [rp[SIGL] - ro[SIGL], rp[KD] - ro[KD], rp[TDL] - ro[TDL], (rp[CTRL] - ro[CTRL]) / 60.0, rp[SUB] - ro[SUB],
            (rp[TOTL] - rp[SIGL]) - (ro[TOTL] - ro[SIGL]), rp[REV] - ro[REV]]
def p_round(rp, ro):
    z = float(np.dot(W, feats(rp, ro))); return 1 / (1 + math.exp(-max(min(z, 30), -30)))

# ---------- ids for (fight, name) from the ledger's fighter logs
by_dn = defaultdict(list)
for fid, f in U.items():
    if f.get("d"): by_dn[(f["d"], frozenset(f["names"]))].append(fid)
idmap = {}
for fid_, c in FZ.items():
    for x in c.get("log", []):
        for fid in by_dn.get((x["d"], frozenset([c["name"], x["o"]])), []):
            idmap[(fid, c["name"])] = fid_
            if x.get("oid"): idmap[(fid, x["o"])] = x["oid"]
# UFCStats names are unique except a handful (two Bruno Silvas, two Jean Silvas...). Unique names map to the ledger's id
# for every fight on record (the ledger's own logs stop at the last ten); duplicated names fall back to name + weight class.
import csv
_names = Counter(r["FIGHTER"].strip() for r in csv.DictReader(open(f"{S}/raw/ufc_fighter_tott.csv", encoding="utf8")))
DUPS = {n for n, c in _names.items() if c > 1}
name2id = {c["name"]: fid_ for fid_, c in FZ.items() if c["name"] not in DUPS}
dup2id = {c["name"]: (fid_, (c.get("div") or "").lower(), bool(c.get("w"))) for fid_, c in FZ.items() if c["name"] in DUPS}
def key_of(fid, name):
    k = idmap.get((fid, name))
    if k: return k
    if name in DUPS:
        wc = (U[fid].get("wc") or "").lower()
        led = dup2id.get(name)
        # the ledger's fighter of that name fights in one division: fights in that division are his
        if led and led[1] and led[1] in wc and (("women" in wc) == led[2]): return led[0]
        return "n:" + name + "|" + U[fid].get("wc", "")
    return name2id.get(name, "n:" + name)

# ---------- chronological pass: pre-fight snapshots
fights = sorted([(f["d"], fid) for fid, f in U.items() if f.get("d") and f.get("secs") and len(f["names"]) == 2])
acc = defaultdict(lambda: defaultdict(float))
pre = {}   # (fid, key) -> snapshot dict
ACC_KEYS = ["n", "secs", "sigL", "sigA", "tdL", "tdA", "otdL", "otdA", "ctrl", "octrl", "kd", "okd", "sub"]
for d, fid in fights:
    f = U[fid]
    a, b = f["names"]
    ka, kb = key_of(fid, a), key_of(fid, b)
    pre[(fid, ka)] = dict(acc[ka]); pre[(fid, kb)] = dict(acc[kb])
    for me, op, km in ((a, b, ka), (b, a, kb)):
        R, O = f["rounds"].get(me, []), f["rounds"].get(op, [])
        if not R and not O: continue
        t = lambda rows, k: sum(r[k] for r in rows)
        A = acc[km]
        A["n"] += 1; A["secs"] += f["secs"]
        A["sigL"] += t(R, SIGL); A["sigA"] += t(O, SIGL)
        A["tdL"] += t(R, TDL); A["tdA"] += t(R, TDA); A["otdL"] += t(O, TDL); A["otdA"] += t(O, TDA)
        A["ctrl"] += t(R, CTRL); A["octrl"] += t(O, CTRL); A["kd"] += t(R, KD); A["okd"] += t(O, KD); A["sub"] += t(R, SUB)

def rates(s):
    if not s or s.get("n", 0) < 2 or s.get("secs", 0) < 600: return None
    m15 = s["secs"] / 900
    return {"td15": s["tdL"] / m15, "ctl": s["ctrl"] / s["secs"], "slpm": s["sigL"] / (s["secs"] / 60), "kd15": s["kd"] / m15, "sub15": s["sub"] / m15,
            "tk15": s["otdL"] / m15, "n": s["n"]}

# thresholds at roughly the top quarter of fighters on fight night
allr = [r for r in (rates(s) for s in pre.values()) if r]
q = lambda k, p: float(np.percentile([r[k] for r in allr], p))
TH = {"wrestler_td15": round(q("td15", 75), 2), "wrestler_ctl": round(q("ctl", 75), 3), "sub15": round(q("sub15", 80), 2),
      "kd15": round(q("kd15", 80), 2), "slpm": round(q("slpm", 75), 2)}
def archetype(r):
    if not r: return "unknown"
    if r["td15"] >= TH["wrestler_td15"] or r["ctl"] >= TH["wrestler_ctl"]: return "wrestler"
    if r["sub15"] >= TH["sub15"]: return "submission"
    if r["kd15"] >= TH["kd15"]: return "power"
    if r["slpm"] >= TH["slpm"]: return "volume"
    return "balanced"
ARCH_LABEL = {"wrestler": "Wrestlers and grapplers", "submission": "Submission hunters", "power": "Power strikers",
              "volume": "Volume strikers", "balanced": "Balanced fighters", "unknown": "Too new to label"}

# ---------- per ledger fighter: every UFC fight on record
def fight_rows(fid, me):
    f = U[fid]; op = [n for n in f["names"] if n != me]
    if not op: return None
    op = op[0]
    R, O = f["rounds"].get(me, []), f["rounds"].get(op, [])
    n = max(len(R), len(O))
    return op, [((R[i] if i < len(R) else ZERO), (O[i] if i < len(O) else ZERO)) for i in range(n)]

hist = defaultdict(list)
for d, fid in fights:
    f = U[fid]
    for i, me in enumerate(f["names"]):
        k = key_of(fid, me)
        if k not in FZ: continue
        fr = fight_rows(fid, me)
        if not fr: continue
        op, rows = fr
        o = f.get("out") or ["", ""]
        res = {"W": "W", "L": "L", "D": "D", "NC": "NC"}.get(o[i] if i < len(o) else "", o[i] if i < len(o) else "")
        okey = key_of(fid, op)
        hist[k].append({"fid": fid, "d": f["d"], "op": op, "oid": okey if okey in FZ else None, "r": res, "m": f["method"], "rd": f["rd"],
                        "secs": f["secs"], "rows": rows, "oarch": archetype(rates(pre.get((fid, okey))))})
for k in hist: hist[k].sort(key=lambda h: h["d"])

def prof_raw(H):
    s = defaultdict(float)
    fade = defaultdict(lambda: [0.0, 0])   # round index -> [sum of sig diff per minute, n rounds]
    pair = [0, 0.0, 0.0]                   # fights with a full round 3: n, sum of round-1 and round-3 sig diff per minute
    ftd = {"conceded": [0, 0], "landed": [0, 0], "none": [0, 0]}
    fin = Counter()
    arch = defaultdict(lambda: [0, 0, 0, 0.0])  # n, W, L, sum of stat round share
    for h in H:
        rows, secs = h["rows"], h["secs"]
        if not rows or not secs: continue
        s["n"] += 1; s["secs"] += secs
        t = lambda side, k: sum((r[0] if side == 0 else r[1])[k] for r in rows)
        s["otdL"] += t(1, TDL); s["otdA"] += t(1, TDA); s["octrl"] += t(1, CTRL); s["sigA"] += t(1, SIGL); s["sigL"] += t(0, SIGL)
        s["okd"] += t(1, KD); s["obody"] += t(1, BODYL); s["oleg"] += t(1, LEGL); s["ohead"] += t(1, HEADL)
        s["tdL"] += t(0, TDL)
        res = h["r"]
        meth = (h["m"] or "").split(" - ")[0]
        if res == "L": fin["L_" + ("KO" if "KO" in meth or "Doctor" in (h["m"] or "") else "SUB" if "Sub" in meth else "DEC" if "Decision" in meth else "OTHER")] += 1
        if res == "W": fin["W"] += 1
        for i, (rp, ro) in enumerate(rows):
            if i < len(rows) - 1 or secs >= 300 * (i + 1):   # full rounds only
                fade[i + 1][0] += (rp[SIGL] - ro[SIGL]) / 5.0; fade[i + 1][1] += 1
        if len(rows) >= 3 and secs >= 900:
            pair[0] += 1; pair[1] += (rows[0][0][SIGL] - rows[0][1][SIGL]) / 5.0; pair[2] += (rows[2][0][SIGL] - rows[2][1][SIGL]) / 5.0
        first = next(((rp[TDL], ro[TDL]) for rp, ro in rows if rp[TDL] or ro[TDL]), None)
        k = "none" if not first else ("conceded" if first[1] > 0 and first[0] == 0 else "landed" if first[0] > 0 and first[1] == 0 else "none")
        if res in ("W", "L"): ftd[k][0 if res == "W" else 1] += 1
        share = sum(p_round(rp, ro) for rp, ro in rows) / len(rows)
        a = arch[h["oarch"]]; a[0] += 1; a[1] += res == "W"; a[2] += res == "L"; a[3] += share
    s["pair"] = pair
    return s, fade, ftd, fin, arch

raw = {fid: prof_raw(H) for fid, H in hist.items()}
# how often a fighter loses after conceding the fight's first takedown, across every ledger fighter: the baseline
_c = [r[2]["conceded"] for r in raw.values()]
BASE_FTD = sum(x[1] for x in _c) / max(1, sum(x[0] + x[1] for x in _c))
def binom_tail(k, n, p):
    return sum(math.comb(n, i) * p ** i * (1 - p) ** (n - i) for i in range(k, n + 1))

# division means for shrinkage (fighters with at least 3 UFC fights)
def divk(c): return ("w:" if c.get("w") else "m:") + (c.get("div") or "unknown")
DM = defaultdict(lambda: defaultdict(list))
for fid, (s, *_rest) in raw.items():
    if s["n"] < 3: continue
    dk = divk(FZ[fid])
    m15 = s["secs"] / 900
    DM[dk]["tdsucc"].append(s["otdL"] / s["otdA"] if s["otdA"] else None)
    DM[dk]["tk15"].append(s["otdL"] / m15); DM[dk]["cc"].append(s["octrl"] / s["secs"]); DM[dk]["ab"].append(s["sigA"] / (s["secs"] / 60))
    DM[dk]["kd15"].append(s["okd"] / m15)
def dmean(dk, k):
    xs = [x for x in DM[dk][k] if x is not None]
    if len(xs) < 8:
        xs = [x for d in DM for x in DM[d][k] if x is not None]
    return float(np.mean(xs))

def shrunk(fid, s):
    """Each rate pulled toward the division mean by a fixed prior: takedown success (10 attempts), takedowns conceded
    (30 minutes), control conceded (30 minutes), strikes absorbed (30 minutes), knockdowns suffered (45 minutes)."""
    dk = divk(FZ[fid]); m15, mins = s["secs"] / 900, s["secs"] / 60
    return {"tdsucc": (s["otdL"] + 10 * dmean(dk, "tdsucc")) / (s["otdA"] + 10),
            "tk15": (s["otdL"] + 2 * dmean(dk, "tk15")) / (m15 + 2),
            "cc": (s["octrl"] + 1800 * dmean(dk, "cc")) / (s["secs"] + 1800),
            "ab": (s["sigA"] + 30 * dmean(dk, "ab")) / (mins + 30),
            "kd15": (s["okd"] + 3 * dmean(dk, "kd15")) / (m15 + 3)}
# peers are ranked on the same shrunk scale as the fighter (fighters with 3+ UFC fights)
DMS = defaultdict(lambda: defaultdict(list))
for fid, (s, *_rest) in raw.items():
    if s["n"] < 3: continue
    for k, v in shrunk(fid, s).items(): DMS[divk(FZ[fid])][k].append(v)
def pctile(dk, k, v, higher_bad=True):
    xs = DMS[dk][k]
    if len(xs) < 8: xs = [x for d in DMS for x in DMS[d][k]]
    below = sum(1 for x in xs if x < v)
    return round(below / len(xs), 2)

profiles = {}
for fid, (s, fade, ftd, fin, arch) in raw.items():
    c = FZ[fid]; dk = divk(c)
    if s["n"] == 0: continue
    m15, mins = s["secs"] / 900, s["secs"] / 60
    mu_succ = dmean(dk, "tdsucc"); k_td = 10
    td_succ_sh = (s["otdL"] + k_td * mu_succ) / (s["otdA"] + k_td)
    mu_tk = dmean(dk, "tk15"); tk_sh = (s["otdL"] + 2 * mu_tk) / (m15 + 2)
    mu_cc = dmean(dk, "cc"); cc_sh = (s["octrl"] + 1800 * mu_cc) / (s["secs"] + 1800)
    mu_ab = dmean(dk, "ab"); ab_sh = (s["sigA"] + 30 * mu_ab) / (mins + 30)
    mu_kd = dmean(dk, "kd15"); kd_sh = (s["okd"] + 3 * mu_kd) / (m15 + 3)
    P = {
        "n": int(s["n"]), "mins": round(mins, 1),
        "tdd": [int(s["otdL"]), int(s["otdA"]), round(1 - td_succ_sh, 3), round(1 - mu_succ, 3), pctile(dk, "tdsucc", td_succ_sh)],
        "tk": [round(s["otdL"] / m15, 2), round(tk_sh, 2), round(mu_tk, 2), pctile(dk, "tk15", tk_sh)],
        "cc": [round(s["octrl"] / s["secs"], 3), round(cc_sh, 3), round(mu_cc, 3), pctile(dk, "cc", cc_sh)],
        "ab": [round(s["sigA"] / mins, 2), round(ab_sh, 2), round(mu_ab, 2), pctile(dk, "ab", ab_sh)],
        "kd": [int(s["okd"]), round(s["okd"] / m15, 2), round(kd_sh, 2), round(mu_kd, 2), pctile(dk, "kd15", kd_sh)],
        "tgt": [int(s["ohead"]), int(s["obody"]), int(s["oleg"])],
        "fin": [int(fin["W"]), int(fin["L_KO"]), int(fin["L_SUB"]), int(fin["L_DEC"]), int(fin["L_OTHER"])],
        "fade": [[r, round(fade[r][0] / fade[r][1], 2) if fade[r][1] else None, fade[r][1]] for r in sorted(fade) if r <= 5],
        "ftd": ftd,
        "arch": {k: [v[0], v[1], v[2], round(v[3] / v[0], 3)] for k, v in arch.items() if v[0]},
    }
    flags, strengths = [], []
    if s["otdA"] >= 6 and P["tdd"][4] >= .8:
        flags.append(["td_def", f"Opponents land {round(100 * (1 - P['tdd'][2]))}% of their takedowns after allowing for sample size ({int(s['otdL'])} of {int(s['otdA'])} on record), among the weakest takedown defence in the division."])
    if s["n"] >= 3 and P["cc"][3] >= .8 and P["cc"][1] >= .15:
        flags.append(["controlled", f"Spends {round(100 * P['cc'][1])}% of fight time controlled by opponents (division average {round(100 * P['cc'][2])}%)."])
    if s["n"] >= 3 and P["ab"][3] >= .8:
        flags.append(["absorbs", f"Absorbs {P['ab'][1]} significant strikes a minute (division average {P['ab'][2]})."])
    if s["n"] >= 3 and P["kd"][4] >= .85 and s["okd"] >= 2:
        flags.append(["chin", f"Knocked down {int(s['okd'])} times in {round(mins)} UFC minutes, {P['kd'][2]} per 15 minutes after allowing for sample size (division {P['kd'][3]})."])
    if fin["L_KO"] >= 2 and fin["L_KO"] >= 0.6 * max(1, fin["L_KO"] + fin["L_SUB"] + fin["L_DEC"] + fin["L_OTHER"]):
        flags.append(["ko_losses", f"{fin['L_KO']} of {fin['L_KO'] + fin['L_SUB'] + fin['L_DEC'] + fin['L_OTHER']} UFC losses came by KO/TKO."])
    if fin["L_SUB"] >= 2 and fin["L_SUB"] >= 0.5 * max(1, fin["L_KO"] + fin["L_SUB"] + fin["L_DEC"] + fin["L_OTHER"]):
        flags.append(["sub_losses", f"{fin['L_SUB']} of {fin['L_KO'] + fin['L_SUB'] + fin['L_DEC'] + fin['L_OTHER']} UFC losses came by submission."])
    pn, p1, p3 = s["pair"]
    if pn >= 4:
        r1, r3 = p1 / pn, p3 / pn
        P["fade_pair"] = [int(pn), round(r1, 2), round(r3, 2)]
        drop = (r1 - r3) * pn / (pn + 6)   # within the same fights, shrunk toward no change
        if drop >= 1.0:
            flags.append(["fade", f"In the {int(pn)} fights that reached a full third round, strike differential fell from {r1:+.1f} a minute in round 1 to {r3:+.1f} in round 3."])
        if drop <= -1.0:
            strengths.append(["late", f"Gets better as fights go on: in {int(pn)} fights that went three full rounds, {r1:+.1f} strikes a minute in round 1, {r3:+.1f} in round 3."])
    cf = ftd["conceded"]; ncf = cf[0] + cf[1]
    # flag only when it's clearly worse than the norm: most fighters lose after conceding the first takedown
    if ncf >= 4 and cf[1] / ncf > BASE_FTD and binom_tail(cf[1], ncf, BASE_FTD) < .1:
        flags.append(["first_td", f"Lost {cf[1]} of {ncf} UFC fights in which the opponent landed the first takedown; a typical fighter loses about {round(100 * BASE_FTD)}% of those."])
    P["ftd_base"] = round(BASE_FTD, 3)
    for k, v in P["arch"].items():
        if k in ("unknown", "balanced") or v[0] < 3: continue
        shr = (v[1] + 1) / (v[0] + 2)
        if (v[0] >= 4 and v[2] >= 2 and shr <= .4 and v[3] <= .45) or (v[0] == 3 and v[1] == 0 and v[3] <= .4):
            flags.append(["vs_" + k, f"{v[1]}–{v[2]} against {ARCH_LABEL[k].lower()}, winning {round(100 * v[3])}% of rounds on the stats."])
        if v[1] >= 3 and shr >= .65 and v[3] >= .58:
            strengths.append(["vs_" + k, f"{v[1]}–{v[2]} against {ARCH_LABEL[k].lower()}."])
    if s["otdA"] >= 8 and P["tdd"][4] <= .2:
        strengths.append(["td_def", f"Stops {round(100 * P['tdd'][2])}% of takedowns after allowing for sample size ({int(s['otdA'] - s['otdL'])} of {int(s['otdA'])} stopped)."])
    # story tags from this season's fight reviews
    tags = Counter()
    for bid, st in ST.items():
        rb = RB.get(bid)
        if not rb or not rb.get("ids") or fid not in rb["ids"]: continue
        me_name = c["name"]
        loser = rb["opp"] if rb["ok"] else rb["pick"]
        if loser == me_name:
            for t in st.get("loser_weak", []): tags[t] += 1
    P["tags"] = dict(tags)
    rn = rates(dict(acc[fid])) if fid in acc else None
    P["arch_now"] = archetype(rn)
    if rn: P["now"] = {k: round(rn[k], 2 if k != "ctl" else 3) for k in ("td15", "ctl", "slpm", "kd15", "sub15")}
    P["flags"], P["strengths"] = flags, strengths
    profiles[fid] = P

# ---------- aggregates
def ci_resid(ys, ps):
    ys, ps = np.array(ys, float), np.array(ps, float)
    n = len(ys)
    if n == 0: return None
    r = ys.mean() - ps.mean(); se = math.sqrt(float((ps * (1 - ps)).sum())) / n
    return {"n": n, "won": round(float(ys.mean()), 3), "said": round(float(ps.mean()), 3), "resid": round(r, 3), "lo": round(r - 1.96 * se, 3), "hi": round(r + 1.96 * se, 3)}

agg = {}
for win, since in (("season", "2025-01-01"), ("all", "0000")):
    xs = [(bid, r) for bid, r in RB.items() if r["date"] >= since]
    A = {"n": len(xs), "hits": sum(1 for _, r in xs if r["ok"])}
    A["miss_verdicts"] = Counter(r["verdict"] for _, r in xs if not r["ok"])
    A["hit_verdicts"] = Counter(r["verdict"] for _, r in xs if r["ok"])
    A["miss_ub"] = Counter(r["ub"] for _, r in xs if not r["ok"])
    A["miss_ub_verdict"] = Counter(f"{r['ub']}|{r['verdict']}" for _, r in xs if not r["ok"])
    A["flips_miss"] = Counter(fl for _, r in xs if not r["ok"] and r.get("pa") for fl in r["pa"]["flips"])
    A["flips_hit"] = Counter(fl for _, r in xs if r["ok"] and r.get("pa") for fl in r["pa"]["flips"])
    A["n_pa_miss"] = sum(1 for _, r in xs if not r["ok"] and r.get("pa")); A["n_pa_hit"] = sum(1 for _, r in xs if r["ok"] and r.get("pa"))
    # by opponent's style on fight night
    by_arch = defaultdict(lambda: ([], [], [], []))
    by_gap = defaultdict(lambda: ([], [], [], []))
    for bid, r in xs:
        ids = r.get("ids") or [None, None]
        okey = ids[1] if ids[1] else "n:" + r["opp"]
        pkey = ids[0] if ids[0] else "n:" + r["pick"]
        oa = archetype(rates(pre.get((bid, okey)))); pa_ = archetype(rates(pre.get((bid, pkey))))
        y = 1 if r["ok"] else 0
        for key, store in ((oa, by_arch), (f"{pa_}>{oa}", by_arch)):
            store[key][0].append(y); store[key][1].append(r["p"])
            if r.get("mk") is not None: store[key][2].append(y); store[key][3].append(r["mk"])
        b = next(bb for e in L["events"] if e["date"] == r["date"] for bb in e["bouts"] if bb["id"] == bid)
        if b.get("proj"):
            gap = b["proj"]["opp"]["ctl"] - b["proj"]["pick"]["ctl"]
            g = "opp_much_more" if gap >= .2 else "opp_more" if gap >= .07 else "even" if gap > -.07 else "pick_more" if gap > -.2 else "pick_much_more"
            by_gap[g][0].append(y); by_gap[g][1].append(r["p"])
            if r.get("mk") is not None: by_gap[g][2].append(y); by_gap[g][3].append(r["mk"])
    ally = [1 if r["ok"] else 0 for _, r in xs]
    A["overall"] = {"model": ci_resid(ally, [r["p"] for _, r in xs]),
                    "market": ci_resid([1 if r["ok"] else 0 for _, r in xs if r.get("mk") is not None], [r["mk"] for _, r in xs if r.get("mk") is not None])}
    A["by_opp_style"] = {k: {"model": ci_resid(v[0], v[1]), "market": ci_resid(v[2], v[3]) if v[2] else None} for k, v in by_arch.items() if ">" not in k}
    A["by_style_pair"] = {k: {"model": ci_resid(v[0], v[1]), "market": ci_resid(v[2], v[3]) if v[2] else None} for k, v in by_arch.items() if ">" in k and len(v[0]) >= 25}
    A["by_proj_control_gap"] = {k: {"model": ci_resid(v[0], v[1]), "market": ci_resid(v[2], v[3]) if v[2] else None} for k, v in by_gap.items()}
    agg[win] = A

# ---------- walk-forward test: takedown vulnerability (pick, before the fight) x opponent's wrestling (before the fight)
def logit(p): p = min(max(p, 1e-4), 1 - 1e-4); return math.log(p / (1 - p))
def fit_offset(Xm, y, off, iters=60):
    w = np.zeros(Xm.shape[1])
    for _ in range(iters):
        z = off + Xm @ w; p = 1 / (1 + np.exp(-z))
        g = Xm.T @ (y - p); Hm = -(Xm.T * (p * (1 - p))) @ Xm
        step = np.linalg.solve(Hm, g); w -= step
        if np.abs(step).max() < 1e-9: break
    cov = np.linalg.inv(-Hm)
    return w, np.sqrt(np.diag(cov))
pop_tk = float(np.mean([r["tk15"] for r in allr]))
rows_t = []
for bid, r in RB.items():
    ids = r.get("ids") or [None, None]
    sp = pre.get((bid, ids[0] or "n:" + r["pick"])); so = pre.get((bid, ids[1] or "n:" + r["opp"]))
    if not sp or not so or sp.get("n", 0) < 2 or so.get("n", 0) < 2: continue
    vp = (sp["otdL"] + 2 * pop_tk) / (sp["secs"] / 900 + 2)          # pick: takedowns conceded per 15, shrunk
    vo = (so["otdL"] + 2 * pop_tk) / (so["secs"] / 900 + 2)          # opponent: same
    wp = (sp["tdL"] + 2 * pop_tk) / (sp["secs"] / 900 + 2)           # pick: takedowns landed per 15, shrunk
    wo = (so["tdL"] + 2 * pop_tk) / (so["secs"] / 900 + 2)           # opponent: same
    rows_t.append((1 if r["ok"] else 0, r["p"], r.get("mk"), vp * wo - vo * wp, r["date"]))
test = {}
if len(rows_t) > 200:
    y = np.array([t[0] for t in rows_t], float); x = np.array([t[3] for t in rows_t], float)
    xs_ = (x - x.mean()) / x.std()
    for nm, sel, offs in (("model", np.ones(len(rows_t), bool), np.array([logit(t[1]) for t in rows_t])),
                          ("market", np.array([t[2] is not None for t in rows_t]), np.array([logit(t[2]) if t[2] is not None else 0 for t in rows_t]))):
        w, se = fit_offset(xs_[sel].reshape(-1, 1), y[sel], offs[sel])
        test[nm] = {"n": int(sel.sum()), "coef_per_sd": round(float(w[0]), 3), "se": round(float(se[0]), 3), "z": round(float(w[0] / se[0]), 2)}
    # simple bins: the pick's grappling mismatch (positive = the pick is the one likely to be taken down)
    qs = np.quantile(x, [.2, .8])
    for nm, lo_, hi_ in (("pick_exposed", qs[1], 1e9), ("middle", qs[0], qs[1]), ("pick_favoured", -1e9, qs[0])):
        sel = (x >= lo_) & (x < hi_) if nm != "pick_exposed" else (x >= lo_)
        selm = sel & np.array([t[2] is not None for t in rows_t])
        test["bin_" + nm] = {"model": ci_resid(y[sel], [rows_t[i][1] for i in np.where(sel)[0]]),
                             "market": ci_resid(y[selm], [rows_t[i][2] for i in np.where(selm)[0]])}
json.dump({"profiles": profiles, "agg": agg, "thresholds": TH, "arch_label": ARCH_LABEL, "td_test": test,
           "pre_arch": {f"{bid}": [archetype(rates(pre.get((bid, (r.get('ids') or [None, None])[0] or 'n:' + r['pick'])))),
                                   archetype(rates(pre.get((bid, (r.get('ids') or [None, None])[1] or 'n:' + r['opp']))))] for bid, r in RB.items()}},
          open(f"{S}/work/review_profiles.json", "w"))
print("thresholds", TH)
print("profiles", len(profiles), "with flags", sum(1 for p in profiles.values() if p["flags"]))
for w in agg: print(w, json.dumps({k: agg[w][k] for k in ("by_opp_style", "by_proj_control_gap")}, indent=None)[:3000])
print("td test", json.dumps(test))
