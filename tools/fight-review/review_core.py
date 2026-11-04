"""Fight review, part 1: per-bout round stats, a round-scoring model, verdicts, who underrated the winner,
and projected-versus-actual numbers for every graded bout in the Octagon Season Ledger.

Inputs (in <scratch>):  work/ledger.json, work/ufc_fights.json, sherdog/matched.json (optional), work/stories.json (optional)
Output: work/review_core.json  {bouts: {id: {...}}, round_model: {...}}

Orientation: everything is from the model's pick ("p") and its opponent ("o"), as the ledger records them.
Round row from UFCStats: [kd, sigL, sigA, totL, totA, tdL, tdA, sub, rev, ctrl_s, headL, headA, bodyL, bodyA, legL, legA,
                          distL, distA, clinchL, clinchA, groundL, groundA]
"""
import json, math, os, re, sys, unicodedata
import numpy as np

S = sys.argv[1]
L = json.load(open(f"{S}/work/ledger.json"))
U = json.load(open(f"{S}/work/ufc_fights.json"))
SD = json.load(open(f"{S}/sherdog/matched.json")) if os.path.exists(f"{S}/sherdog/matched.json") else {}
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from stories_load import load_stories
ST = load_stories(S)

KD, SIGL, SIGA, TOTL, TOTA, TDL, TDA, SUB, REV, CTRL, HEADL, HEADA, BODYL, BODYA, LEGL, LEGA, DISTL, DISTA, CLL, CLA, GRL, GRA = range(22)
ZERO = [0] * 22

def fold(s): return unicodedata.normalize("NFKD", re.sub(r"['’`]", "", s or "")).encode("ascii", "ignore").decode().lower()
def toks(s): return [t for t in re.findall(r"[a-z]+", fold(s)) if t not in ("jr", "sr", "ii", "iii")]

def round_lengths(f):
    """Seconds fought in each round."""
    rd, secs = f.get("rd") or 0, f.get("secs")
    if not rd or secs is None: return []
    out = [300] * (rd - 1) + [max(secs - 300 * (rd - 1), 0)]
    return out

def feats(rp, ro):
    """Round features, pick minus opponent. Strikes landed, knockdowns, takedowns, control minutes,
    submission attempts, non-significant strikes (mostly ground punches), reversals."""
    return [rp[SIGL] - ro[SIGL], rp[KD] - ro[KD], rp[TDL] - ro[TDL], (rp[CTRL] - ro[CTRL]) / 60.0,
            rp[SUB] - ro[SUB], (rp[TOTL] - rp[SIGL]) - (ro[TOTL] - ro[SIGL]), rp[REV] - ro[REV]]
FEAT_NAMES = ["sig_strikes", "knockdowns", "takedowns", "control_min", "sub_attempts", "other_strikes", "reversals"]

# ---------- Sherdog round labels (majority of the three scorers), oriented to pick/opp
def _near(a, b):
    """Two name tokens match: equal, or one edit apart for longer names (Sherdog typos such as Vokov/Volkov)."""
    if a == b: return True
    if min(len(a), len(b)) < 5 or abs(len(a) - len(b)) > 1: return False
    if len(a) == len(b): return sum(x != y for x, y in zip(a, b)) <= 1
    if len(a) > len(b): a, b = b, a
    return any(b[:i] + b[i + 1:] == a for i in range(len(b)))
def name_side(sd, name):
    """Which fighter a scorer's name refers to: 'p', 'o' or None. Surname first, then any token, with typo tolerance;
    when only one fighter's Sherdog name is known, a name that isn't him is the other fighter."""
    t = toks(name)
    if not t: return None
    P, O = toks(sd["names"]["pick"]), toks(sd["names"]["opp"])
    def score(ft):
        if not ft: return 0
        if any(_near(x, ft[-1]) for x in t): return 2
        return 1 if any(_near(x, y) for x in t for y in ft) else 0
    sp, so = score(P), score(O)
    if sp > so: return "p"
    if so > sp: return "o"
    if not P and so == 0: return "p"
    if not O and sp == 0: return "o"
    return None
def sherdog_round_owner(sd, rnd):
    votes = []
    for sc in rnd.get("scores", []):
        _, a, b, name, _tot = sc
        if a == b: votes.append("e"); continue
        side = name_side(sd, name)
        if side is None: return None, votes       # an unattributable vote: don't let the rest form a false majority
        votes.append(side if a > b else ("o" if side == "p" else "p"))
    if not votes: return None, votes
    for k in ("p", "o", "e"):
        if votes.count(k) * 2 > len(votes): return k, votes
    return "e", votes

def sherdog_total(sd):
    """Sherdog scorers' fight totals after the last scored round, oriented [pick, opp] per scorer."""
    last = [r for r in sd.get("rounds", []) if r.get("scores")]
    if not last: return []
    out = []
    for sc in last[-1]["scores"]:
        tot = sc[4]
        if not tot: continue
        a, b, name = tot
        if a == b: out.append([a, b]); continue
        side = name_side(sd, name)
        if side is None: return []
        out.append([max(a, b), min(a, b)] if side == "p" else [min(a, b), max(a, b)])
    return out

# ---------- collect graded bouts
bouts = []
for e in L["events"]:
    for b in e["bouts"]:
        if b.get("won") is None or b.get("ok") is None: continue
        f = U.get(b["id"])
        if not f: continue
        P = f["rounds"].get(b["pick"], []); O = f["rounds"].get(b["opp"], [])
        n = max(len(P), len(O))
        rows = [((P[i] if i < len(P) else ZERO), (O[i] if i < len(O) else ZERO)) for i in range(n)]
        bouts.append({"e": e, "b": b, "f": f, "rows": rows})

# ---------- round model: logistic regression (no intercept, symmetric) fitted on Sherdog's completed, scored rounds
X, y = [], []
for it in bouts:
    sd = SD.get(it["b"]["id"])
    if not sd: continue
    lens = round_lengths(it["f"])
    for r in sd["rounds"]:
        i = r["n"] - 1
        if i >= len(it["rows"]) or i >= len(lens) or lens[i] < 300: continue   # full rounds only
        own, votes = sherdog_round_owner(sd, r)
        if own in ("p", "o"):
            rp, ro = it["rows"][i]
            X.append(feats(rp, ro)); y.append(1 if own == "p" else 0)
X, y = np.array(X, float), np.array(y, float)

def fit_logit(X, y, l2=0.5, iters=50):
    w = np.zeros(X.shape[1])
    for _ in range(iters):
        z = X @ w; p = 1 / (1 + np.exp(-z))
        g = X.T @ (y - p) - l2 * w
        Hm = -(X.T * (p * (1 - p))) @ X - l2 * np.eye(len(w))
        step = np.linalg.solve(Hm, g); w = w - step
        if np.abs(step).max() < 1e-8: break
    return w

DEFAULT_W = np.array([0.12, 1.5, 0.6, 0.35, 0.5, 0.03, 0.3])
if len(y) >= 300:
    W = fit_logit(X, y)
    # fit quality: in-sample accuracy and 5-fold cross-validated accuracy / log loss
    idx = np.arange(len(y)); folds = idx % 5
    acc, ll = [], []
    for k in range(5):
        tr, te = folds != k, folds == k
        wk = fit_logit(X[tr], y[tr]); pk = 1 / (1 + np.exp(-(X[te] @ wk)))
        acc.append(((pk > .5) == (y[te] == 1)).mean()); ll.append(-np.mean(y[te] * np.log(pk + 1e-12) + (1 - y[te]) * np.log(1 - pk + 1e-12)))
    RM = {"source": "sherdog", "n_rounds": int(len(y)), "weights": dict(zip(FEAT_NAMES, map(float, W))),
          "cv_accuracy": float(np.mean(acc)), "cv_logloss": float(np.mean(ll))}
else:
    W = DEFAULT_W
    RM = {"source": "default", "n_rounds": int(len(y)), "weights": dict(zip(FEAT_NAMES, map(float, W)))}

def p_round(rp, ro):
    z = float(np.dot(W, feats(rp, ro)))
    return 1 / (1 + math.exp(-max(min(z, 30), -30)))

# ---------- per bout
def compact(r):
    return [r[KD], r[SIGL], r[SIGA], r[TDL], r[TDA], r[SUB], r[REV], r[CTRL], r[TOTL], r[HEADL], r[BODYL], r[LEGL], r[DISTL], r[CLL], r[GRL]]

def dec_kind(method):
    m = method.lower()
    return "S" if "split" in m else "M" if "majority" in m else "U" if "unanimous" in m else None

out = {}
for it in bouts:
    b, f, rows, e = it["b"], it["f"], it["rows"], it["e"]
    bid = b["id"]; ok = bool(b["ok"])
    lens = round_lengths(f)
    secs = f.get("secs") or sum(lens)
    names = f["names"]
    pick_first = names[0] == b["pick"]
    judges = [[j[0], j[1], j[2]] if pick_first else [j[0], j[2], j[1]] for j in f.get("judges", [])]
    how = b["how"]; method = f.get("method", "")
    sd = SD.get(bid); story = ST.get(bid)

    # round ownership
    pr = [p_round(rp, ro) for rp, ro in rows]
    owners, sd_owners = [], []
    for i, (rp, ro) in enumerate(rows):
        own = None
        if sd:
            rr = next((r for r in sd["rounds"] if r["n"] == i + 1), None)
            if rr: own, _ = sherdog_round_owner(sd, rr)
        sd_owners.append(own)
        st = "p" if pr[i] >= .6 else "o" if pr[i] <= .4 else "e"
        owners.append(own if own else st)
    exp_p = sum(pr); exp_o = len(pr) - exp_p

    winner, loser = ("p", "o") if ok else ("o", "p")
    reasons = []; verdict = None; read = None
    finish = how != "Decision"
    if not finish:
        dk = dec_kind(method)
        margins = [(j[1] - j[2]) if winner == "p" else (j[2] - j[1]) for j in judges]  # winner minus loser
        sdt = sherdog_total(sd) if sd else []
        sd_for_loser = sum(1 for t in sdt if (t[1] > t[0] if winner == "p" else t[0] > t[1]))
        sd_for_winner = sum(1 for t in sdt if (t[0] > t[1] if winner == "p" else t[1] > t[0]))
        stat_gap = (exp_p - exp_o) if winner == "p" else (exp_o - exp_p)   # rounds, winner minus loser, by the stats model
        reasons_split_clear = False
        unlucky = []
        sd_backs_winner = bool(sdt) and sd_for_winner == len(sdt)
        if dk in ("S", "M"):
            # a split card is a coin flip unless Sherdog's scorers all had the winner and the stats clearly agree
            if sd_backs_winner and stat_gap >= 1: reasons_split_clear = True
            else: unlucky.append("split" if dk == "S" else "majority")
        if sdt and sd_for_loser * 2 > len(sdt): unlucky.append("sherdog_loser")
        # the stats model only overrules the cards when Sherdog's scorers don't back the winner too
        if stat_gap <= -0.5 and not (sdt and sd_for_winner * 2 > len(sdt)): unlucky.append("stats_loser")
        if unlucky:
            verdict = "loser_robbed"; reasons = unlucky
        elif margins and min(margins) >= 2:
            verdict = "clear"; reasons = ["wide_cards"]
        elif margins and sum(margins) / len(margins) >= 2:
            verdict = "clear"; reasons = ["mostly_wide_cards"]
        else:
            verdict = "close"
            reasons = ["one_round_cards"] if margins and max(margins) <= 1 else ["mostly_one_round"] if margins else ["no_cards"]
            if dk in ("S", "M"): reasons = ["split_but_clear"]
            elif stat_gap >= 1.5 and (not sdt or sd_for_winner * 2 > len(sdt)): verdict = "clear"; reasons = reasons + ["stats_dominant"]
    else:
        rf = f.get("rd") or len(rows)
        full_last = secs is not None and lens and lens[-1] >= 300    # stopped between rounds: the last round was fought in full
        done = owners[: rf] if full_last else owners[: rf - 1]
        bank_w = sum(1 for x in done if x == winner); bank_l = sum(1 for x in done if x == loser)
        lean = None if full_last else (story.get("bf") if story else None)    # the final round's lean before the finish
        early = secs is not None and secs < 60
        stats_lean = False
        if lean is None and not full_last and rows and rf - 1 < len(pr):
            # No written read: the final round's numbers include the winner's finishing burst, so they lean his way.
            # If the loser still led them clearly over at least 90 seconds, he was ahead in that round.
            p_loser = pr[rf - 1] if loser == "p" else 1 - pr[rf - 1]
            final_secs = (secs or 0) - 300 * (rf - 1)
            if p_loser >= .65 and final_secs >= 90: lean, stats_lean = "loser", True
        dq = how == "DQ"
        # Score the fight at the moment it ended: banked rounds plus the round in progress.
        net = bank_w - bank_l + (1 if lean == "winner" else -1 if lean == "loser" else 0)
        lean_reason = (["final_round_loser_stats"] if stats_lean else ["final_round_loser"]) if lean == "loser" else \
                      ["final_round_winner"] if lean == "winner" else ["final_round_even"] if lean == "even" else []
        af = story.get("ahead_at_finish") if story else None
        if dq:
            verdict = "variance"; reasons = ["disqualification"]
        elif af in ("winner", "loser", "even"):
            # the checked written review's own call on who was ahead when it ended (it reads the round in progress)
            verdict = {"winner": "loser_behind", "loser": "loser_ahead", "even": "level"}[af]
            reasons = [f"rounds_{bank_l}_{bank_w}"] + lean_reason + ["story_" + af]
        elif net < 0:
            verdict = "loser_ahead"; reasons = [f"rounds_{bank_l}_{bank_w}"] + lean_reason
        elif net > 0:
            verdict = "loser_behind"; reasons = [f"rounds_{bank_l}_{bank_w}"] + lean_reason
        elif rf == 1 and lean is None and not full_last:
            verdict = "quick"; reasons = ["first_round_no_read"]
        else:
            verdict = "level"; reasons = [f"rounds_{bank_l}_{bank_w}"] + lean_reason
        if full_last: reasons.append("between_rounds")
        if early and verdict in ("loser_behind", "level"): verdict = "quick"; reasons = ["under_a_minute"]
        detail = (f.get("detail") or "")
        if re.search(r"doctor|injur|could not continue", detail + " " + method, re.I): reasons.append("doctor_or_injury")
        if re.search(r"injur", detail, re.I) and not re.search(r"cut", detail, re.I) and verdict != "loser_behind":
            verdict = "variance"; reasons.append("injury")

    # map to the pick's perspective
    if ok:
        v = {"loser_robbed": "fortunate", "clear": "earned", "close": "close", "loser_ahead": "fortunate",
             "loser_behind": "earned", "level": "close", "quick": "quick", "variance": "fortunate"}[verdict]
    else:
        v = {"loser_robbed": "unlucky", "clear": "outclassed", "close": "close", "loser_ahead": "unlucky",
             "loser_behind": "outclassed", "level": "close", "quick": "quick", "variance": "unlucky"}[verdict]

    # who underrated the winner
    mk = b.get("mk")
    priced = mk is not None and 0 < mk < 1 and mk != 0.5
    if not ok:
        ub = ("both" if mk > .5 else "model") if priced else "model_unpriced"
    else:
        ub = "market" if (priced and mk < .5) else ("neither" if priced else None)

    # projected vs actual (per 15 minutes)
    tot = lambda side, k: sum((r[0] if side == "p" else r[1])[k] for r in rows)
    pa = None
    if b.get("proj") and secs:
        m15 = secs / 900.0
        act = {s: {"str": tot(s, SIGL) / m15, "td": tot(s, TDL) / m15, "ctl": tot(s, CTRL) / secs, "ko": tot(s, KD) / m15, "sub": tot(s, SUB) / m15} for s in ("p", "o")}
        pj = {"p": b["proj"]["pick"], "o": b["proj"]["opp"]}
        flips = []
        sp_proj = pj["p"]["str"] / max(pj["p"]["str"] + pj["o"]["str"], 1e-9)
        sl = tot("p", SIGL) + tot("o", SIGL)
        sp_act = tot("p", SIGL) / sl if sl else None
        if sp_act is not None and (sp_proj - .5) * (sp_act - .5) < 0 and abs(sp_act - .5) >= .1 and secs >= 240: flips.append("strikes")
        cl_proj = pj["p"]["ctl"] - pj["o"]["ctl"]
        cl_act = (tot("p", CTRL) - tot("o", CTRL)) / secs
        if cl_proj * cl_act < 0 and abs(cl_act) >= .15 and secs >= 240: flips.append("control")
        if abs(cl_proj) < .1 and abs(cl_act) >= .3 and secs >= 240: flips.append("control_surprise")
        pa = {"sp_proj": round(sp_proj, 3), "sp_act": None if sp_act is None else round(sp_act, 3),
              "cl_proj": round(cl_proj, 3), "cl_act": round(cl_act, 3), "flips": flips}

    rec = {
        "ok": ok, "how": how, "method": method, "rd": f.get("rd"), "time": f.get("time"), "secs": secs, "sched": f.get("sched"),
        "detail": f.get("detail") or None, "ref": f.get("ref"),
        "dk": dec_kind(method) if not finish else None, "judges": judges,
        "rows": [[compact(rp), compact(ro)] for rp, ro in rows],
        "pr": [round(x, 3) for x in pr], "own": owners, "sd_own": sd_owners,
        "exp": [round(exp_p, 2), round(exp_o, 2)],
        "verdict": v, "why": reasons, "raw_verdict": verdict, "ub": ub, "mk": mk if priced else None, "p": b["p"], "pa": pa,
        "date": e["date"], "event": e["name"], "pick": b["pick"], "opp": b["opp"], "ids": b.get("ids"),
    }
    if sd:
        rec["sd"] = {"a": sd["article"], "title": sd["title"], "names": sd["names"], "official": sd.get("official"), "odds": sd.get("odds"),
                     "scores": [[r["n"], [[s[0], s[1], s[2], s[3]] for s in r["scores"]]] for r in sd["rounds"] if r.get("scores")],
                     "tot": sherdog_total(sd)}
    out[bid] = rec

json.dump({"bouts": out, "round_model": RM}, open(f"{S}/work/review_core.json", "w"))
from collections import Counter
print("round model", json.dumps(RM))
for win in (("all", "0000"), ("season", "2025-01-01")):
    xs = [r for r in out.values() if r["date"] >= win[1]]
    print(win[0], "bouts", len(xs), "misses", Counter(r["verdict"] for r in xs if not r["ok"]).most_common(),
          "hits", Counter(r["verdict"] for r in xs if r["ok"]).most_common())
    print("  underrated (misses)", Counter(r["ub"] for r in xs if not r["ok"]).most_common())
# QA: the code's finish verdict against the written review's own "ahead at the finish" call
agree = tot_ = 0
for bid, r in out.items():
    st = ST.get(bid)
    if not st or r["how"] == "Decision" or st.get("ahead_at_finish") not in ("winner", "loser", "even"): continue
    want = {"winner": "loser_behind", "loser": "loser_ahead", "even": "level"}[st["ahead_at_finish"]]
    tot_ += 1; agree += (r["raw_verdict"] == want) or (r["raw_verdict"] == "quick" and want == "level")
print("finish verdicts agreeing with the written review's ahead-at-finish call:", agree, "of", tot_)
