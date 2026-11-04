"""Walk-forward fight features from the first UFC fight on record.

Every fight, in date order. For each card: snapshot every fighter's state, write one feature row per fight from those
snapshots, and only then update the states with the card's results. Nothing from a fight (or anything later) can reach
that fight's row. Odds never enter.

Row = fighter A (UFCStats names[0]) minus fighter B (names[1]); every feature is antisymmetric, so the models (which
have no intercept) give P(A) = 1 - P(B) and can't learn the UFCStats habit of listing winners first.
Missing values stay NaN (blank), never zero; linear learners add missing-indicator differences.

Inputs (scratch): work/ufc_fights.json, raw/ufc_fighter_tott.csv, sherdog/all_fights.json, stories/*.json, work/review_core.json
Output: ml/features.npz + ml/features_meta.json
"""
import csv, glob, json, math, os, re, sys
from collections import defaultdict
from datetime import date as D, datetime
import numpy as np

S = sys.argv[1]
MLD = os.environ.get("ML_DIR", f"{S}/ml")   # output folder (a copy can work in its own)
U = json.load(open(f"{S}/work/ufc_fights.json"))
SDA = json.load(open(f"{S}/sherdog/all_fights.json")) if os.path.exists(f"{S}/sherdog/all_fights.json") else {}
RC = json.load(open(f"{MLD}/round_model_pre2019.json"))   # fitted on 2016-2018 Sherdog rounds only
W = np.array([RC["weights"][k] for k in ["sig_strikes", "knockdowns", "takedowns", "control_min", "sub_attempts", "other_strikes", "reversals"]])
sys.path.insert(0, f"{S}/scripts")
from stories_load import load_stories
ST = load_stories(S)

KD, SIGL, SIGA, TOTL, TOTA, TDL, TDA, SUB, REV, CTRL, HEADL, HEADA, BODYL, BODYA, LEGL, LEGA, DISTL, DISTA, CLL, CLA, GRL, GRA = range(22)
ZERO = [0] * 22

# ---------- fighter identity and physicals
tott = list(csv.DictReader(open(f"{S}/raw/ufc_fighter_tott.csv", encoding="utf8")))
cnt = defaultdict(int)
for r in tott: cnt[r["FIGHTER"].strip()] += 1
DUPS = {n for n, c in cnt.items() if c > 1}
def inches(s):
    m = re.match(r"\s*(\d+)'\s*(\d+)", s or ""); return int(m.group(1)) * 12 + int(m.group(2)) if m else None
def reach(s):
    m = re.match(r"\s*(\d+(?:\.\d+)?)\"", s or ""); return float(m.group(1)) if m else None
PHYS = {}
for r in tott:
    n = r["FIGHTER"].strip()
    if n in DUPS: continue
    try: dob = datetime.strptime(r["DOB"].strip(), "%b %d, %Y").date()
    except Exception: dob = None
    PHYS[n] = {"dob": dob, "h": inches(r["HEIGHT"]), "r": reach(r["REACH"]), "st": (r["STANCE"] or "").strip().lower()}
def key_of(fid, name): return name + ("|" + U[fid].get("wc", "") if name in DUPS else "")

# ---------- missing physicals are filled with typical values, never left blank or flagged.
# UFCStats (scraped in 2026) mostly filled in reach, height, stance and (before 2014) DOB for fighters who stayed on the
# roster, so a blank marks a fighter who was later cut: information from after the fight. A filled-in value looks like a
# real one, so the learners can't tell which fighters had blanks. All constants are static and use no results.
_hw = defaultdict(list); _seen = set()
for _fid, _f in U.items():
    for _nm in _f.get("names", []):
        _P = PHYS.get(_nm)
        if _P and _P.get("h") and (_nm, _f.get("wc")) not in _seen:
            _seen.add((_nm, _f.get("wc"))); _hw[_f.get("wc")].append(_P["h"])
H_WC = {wc: round(float(np.mean(v))) for wc, v in _hw.items() if len(v) >= 10}
H_ALL = round(float(np.mean([P["h"] for P in PHYS.values() if P.get("h")])))
APE = float(np.mean([P["r"] - P["h"] for P in PHYS.values() if P.get("h") and P.get("r")]))
_first = {}
for _fid, _f in U.items():
    for _nm in _f.get("names", []):
        if _f.get("d"): _first[_nm] = min(_first.get(_nm, "9999"), _f["d"])
AGE_DEBUT = float(np.mean([(D.fromisoformat(_first[n]) - P["dob"]).days / 365.25 for n, P in PHYS.items() if P.get("dob") and n in _first]))
print(f"physical fill-ins: ape index {APE:+.2f} in, age at UFC debut {AGE_DEBUT:.1f}", flush=True)

def p_round(rp, ro):
    x = [rp[SIGL] - ro[SIGL], rp[KD] - ro[KD], rp[TDL] - ro[TDL], (rp[CTRL] - ro[CTRL]) / 60.0, rp[SUB] - ro[SUB],
         (rp[TOTL] - rp[SIGL]) - (ro[TOTL] - ro[SIGL]), rp[REV] - ro[REV]]
    z = float(np.dot(W, x)); return 1 / (1 + math.exp(-max(min(z, 30), -30)))

# ---------- state
ACC = ["n", "w", "l", "secs", "sigL", "sigT", "osigL", "osigT", "tdL", "tdT", "otdL", "otdT", "ctrl", "octrl", "kd", "okd",
       "sub", "osub", "obody", "oleg", "ogr", "rounds", "rshare", "ko_w", "sub_w", "dec_w", "ko_l", "sub_l", "dec_l",
       "lost_ahead", "won_behind", "sd_r", "sd_won", "sd_lost", "sd108f", "sd108a", "sd_f", "hurt_g", "hurt_t", "fat",
       "opp_elo", "st_n", "st_td", "st_hurt", "st_one", "st_luckL"]
DEC = ["d_secs", "d_sigL", "d_osigL", "d_tdL", "d_otdL", "d_ctrl", "d_octrl", "d_kd", "d_okd", "d_rounds", "d_rshare", "d_w", "d_n"]
DECAY = 0.7       # weight kept by everything before each new fight (about the last three fights matter most)
class F:
    __slots__ = ("a", "elo", "elo_dom", "first", "last", "res")
    def __init__(self):
        self.a = defaultdict(float); self.elo = 1500.0; self.elo_dom = 1500.0; self.first = None; self.last = None; self.res = []
FS = defaultdict(F)
G = defaultdict(float)   # population totals, for shrinkage priors (past fights only)

def rate(num, den, prior_mean, prior_w):
    return (num + prior_mean * prior_w) / (den + prior_w)

def fighter_feats(f, day, name, wc=None):
    a = f.a; n = a["n"]
    gm = lambda k, d: (G[k] / G[d]) if G[d] else None
    mins, m15 = a["secs"] / 60, a["secs"] / 900
    P = PHYS.get(name.split("|")[0], {})
    pm = {k: gm(*v) for k, v in {"slpm": ("sigL", "secs60"), "sapm": ("osigL", "secs60"), "acc": ("sigL", "sigT"), "def": ("osigL", "osigT"),
          "td15": ("tdL", "secs900"), "tdacc": ("tdL", "tdT"), "tdsucc": ("otdL", "otdT"), "ctrl": ("ctrl", "secs"),
          "kd15": ("kd", "secs900"), "sub15": ("sub", "secs900"), "body": ("obody", "secs60"), "leg": ("oleg", "secs60"),
          "gr": ("ogr", "secs60")}.items()}
    if any(v is None for v in pm.values()): pm = {k: 0.0 for k in pm}
    x = {}
    x["elo"] = f.elo; x["elo_dom"] = f.elo_dom
    x["exp"] = math.log1p(n)
    x["ufc_years"] = ((day - f.first).days / 365.25) if f.first else 0.0
    x["debut"] = 1.0 if n == 0 else 0.0
    if n > 0:
        x["winrate"] = rate(a["w"], a["w"] + a["l"], .5, 4)
        x["fin_w"] = rate(a["ko_w"] + a["sub_w"], n, .3, 4)
        x["ko_l"] = rate(a["ko_l"], n, .12, 4); x["sub_l"] = rate(a["sub_l"], n, .08, 4)
        x["slpm"] = rate(a["sigL"], mins, pm["slpm"], 15); x["sapm"] = rate(a["osigL"], mins, pm["sapm"], 15)
        x["acc"] = rate(a["sigL"], a["sigT"], pm["acc"], 40); x["sdef"] = 1 - rate(a["osigL"], a["osigT"], pm["def"], 40)
        x["td15"] = rate(a["tdL"], m15, pm["td15"], 1); x["tdacc"] = rate(a["tdL"], a["tdT"], pm["tdacc"], 6)
        x["tddef"] = 1 - rate(a["otdL"], a["otdT"], pm["tdsucc"], 6)
        x["ctrl"] = rate(a["ctrl"], a["secs"], pm["ctrl"], 900); x["octrl"] = rate(a["octrl"], a["secs"], pm["ctrl"], 900)
        x["kd15"] = rate(a["kd"], m15, pm["kd15"], 1.5); x["okd15"] = rate(a["okd"], m15, pm["kd15"], 1.5)
        x["sub15"] = rate(a["sub"], m15, pm["sub15"], 1.5); x["osub15"] = rate(a["osub"], m15, pm["sub15"], 1.5)
        x["body_abs"] = rate(a["obody"], mins, pm["body"], 15); x["leg_abs"] = rate(a["oleg"], mins, pm["leg"], 15)
        x["gr_abs"] = rate(a["ogr"], mins, pm["gr"], 15)
        x["rshare"] = rate(a["rshare"], a["rounds"], .5, 3)
        dm = a["d_secs"] / 60
        x["d_slpm"] = rate(a["d_sigL"], dm, pm["slpm"], 10); x["d_sapm"] = rate(a["d_osigL"], dm, pm["sapm"], 10)
        x["d_td15"] = rate(a["d_tdL"], a["d_secs"] / 900, pm["td15"], .7); x["d_ctrl"] = rate(a["d_ctrl"], a["d_secs"], pm["ctrl"], 600)
        x["d_kd15"] = rate(a["d_kd"], a["d_secs"] / 900, pm["kd15"], 1); x["d_okd15"] = rate(a["d_okd"], a["d_secs"] / 900, pm["kd15"], 1)
        x["d_rshare"] = rate(a["d_rshare"], a["d_rounds"], .5, 2); x["d_win"] = rate(a["d_w"], a["d_n"], .5, 1.5)
        x["luck_adj"] = rate(a["w"] - a["won_behind"] + a["lost_ahead"], a["w"] + a["l"], .5, 4)
        x["opp_elo"] = a["opp_elo"] / n
        x["streak"] = float(sum(1 if r == "W" else -1 for r in f.res[-3:]))
        x["layoff"] = math.log1p((day - f.last).days) if f.last else float("nan")
        # Sherdog: round scores and notes from earlier fights
        if a["sd_f"] > 0:
            x["sd_share"] = rate(a["sd_won"], a["sd_won"] + a["sd_lost"], .5, 2)
            x["sd_108"] = (a["sd108f"] - a["sd108a"]) / (a["sd_r"] + 2)
            x["hurt_t"] = rate(a["hurt_t"], a["sd_f"], .6, 2); x["hurt_g"] = rate(a["hurt_g"], a["sd_f"], .6, 2)
            x["fat"] = rate(a["fat"], a["sd_f"], .3, 2)
        if a["st_n"] > 0:
            x["st_td"] = rate(a["st_td"], a["st_n"], .2, 2); x["st_hurt"] = rate(a["st_hurt"], a["st_n"], .25, 2)
            x["st_one"] = rate(a["st_one"], a["st_n"], .1, 2); x["st_luckL"] = rate(a["st_luckL"], a["st_n"], .1, 2)
    x["cov_sd"] = 1.0 if a["sd_f"] > 0 else 0.0
    x["cov_st"] = 1.0 if a["st_n"] > 0 else 0.0
    imp = 0
    if P.get("dob"): x["age"] = (day - P["dob"]).days / 365.25
    else: x["age"] = AGE_DEBUT + (((day - f.first).days / 365.25) if f.first else 0.0); imp = 1
    h = P.get("h")
    if not h: h = H_WC.get(wc, H_ALL); imp = 1
    x["height"] = float(h)
    if P.get("r"): x["reach"] = float(P["r"])
    else: x["reach"] = float(round(h + APE)); imp = 1
    st = P.get("st") or ""
    if not st: st = "orthodox"; imp = 1
    x["southpaw"] = 1.0 if st == "southpaw" else 0.0; x["switch"] = 1.0 if st == "switch" else 0.0
    x["_imp"] = imp
    return x

GROUPS = {
    "elo": ["elo", "elo_dom"],
    "record": ["exp", "ufc_years", "debut", "winrate", "fin_w", "ko_l", "sub_l", "luck_adj", "opp_elo", "streak", "layoff", "d_win"],
    "stats": ["slpm", "sapm", "acc", "sdef", "td15", "tdacc", "tddef", "ctrl", "octrl", "kd15", "okd15", "sub15", "osub15",
              "body_abs", "leg_abs", "gr_abs", "rshare", "d_slpm", "d_sapm", "d_td15", "d_ctrl", "d_kd15", "d_okd15", "d_rshare"],
    "physical": ["age", "height", "reach", "southpaw", "switch"],
    "sherdog": ["sd_share", "sd_108", "hurt_t", "hurt_g", "fat", "cov_sd"],
    "stories": ["st_td", "st_hurt", "st_one", "st_luckL", "cov_st"],
    "style": ["x_grap", "x_ctrl", "x_strike", "x_power", "x_sub", "x_hurt"],
}
NAMES = [k for g in GROUPS.values() for k in g]

def row(xa, xb):
    v = {}
    for k in NAMES:
        if k.startswith("x_"): continue
        a, b = xa.get(k), xb.get(k)
        v[k] = (a - b) if (a is not None and b is not None and not math.isnan(a) and not math.isnan(b)) else float("nan")
    def g(x, k): return x.get(k, float("nan"))
    def inter(f):
        try: r = f(xa, xb) - f(xb, xa)
        except TypeError: r = float("nan")
        return r
    v["x_grap"] = inter(lambda p, q: g(p, "td15") * (1 - g(q, "tddef")))
    v["x_ctrl"] = inter(lambda p, q: g(p, "ctrl") * g(q, "octrl"))
    v["x_strike"] = inter(lambda p, q: g(p, "slpm") * g(q, "sapm"))
    v["x_power"] = inter(lambda p, q: g(p, "kd15") * g(q, "okd15"))
    v["x_sub"] = inter(lambda p, q: g(p, "sub15") * g(q, "sub_l"))
    v["x_hurt"] = inter(lambda p, q: g(p, "hurt_g") * g(q, "hurt_t"))
    return [v[k] for k in NAMES]

ELO_K = float(os.environ.get("ELO_K", "64"))   # tuned on 2014-2018 (validation) only
def elo_update(fa, fb, sa, k):
    ea = 1 / (1 + 10 ** ((fb - fa) / 400)); return k * (sa - ea)

# ---------- walk forward
fights = sorted([(f["d"], fid) for fid, f in U.items() if f.get("d") and len(f["names"]) == 2 and f.get("out")])
by_day = defaultdict(list)
for d, fid in fights: by_day[d].append(fid)
X, Y, META = [], [], []
for day_s in sorted(by_day):
    day = D.fromisoformat(day_s)
    # 1) snapshot and write rows
    for fid in by_day[day_s]:
        f = U[fid]; n0, n1 = f["names"]; k0, k1 = key_of(fid, n0), key_of(fid, n1)
        o = f["out"]
        y = 1 if o[0] == "W" else 0 if o[0] == "L" else None
        xa, xb = fighter_feats(FS[k0], day, k0, f.get("wc")), fighter_feats(FS[k1], day, k1, f.get("wc"))
        X.append(row(xa, xb)); Y.append(-1 if y is None else y)
        META.append([fid, day_s, n0, n1, int(FS[k0].a["n"]), int(FS[k1].a["n"]), int(FS[k0].a["sd_f"] > 0), int(FS[k1].a["sd_f"] > 0)])
    # 2) update with the card's results
    for fid in by_day[day_s]:
        f = U[fid]; n0, n1 = f["names"]; k0, k1 = key_of(fid, n0), key_of(fid, n1)
        o = f["out"]; res0 = o[0]
        R0, R1 = f["rounds"].get(n0, []), f["rounds"].get(n1, [])
        nr = max(len(R0), len(R1))
        rows = [((R0[i] if i < len(R0) else ZERO), (R1[i] if i < len(R1) else ZERO)) for i in range(nr)]
        secs = f.get("secs") or 0
        meth = f.get("method", "")
        finish = not meth.startswith("Decision")
        pr = [p_round(a, b) for a, b in rows]
        sd = SDA.get(fid)
        st = ST.get(fid)
        fa, fb = FS[k0], FS[k1]
        ea0, ea1, ed0, ed1 = fa.elo, fb.elo, fa.elo_dom, fb.elo_dom
        s0 = 1.0 if res0 == "W" else 0.0 if res0 == "L" else 0.5
        share0 = (sum(pr) / len(pr)) if pr else .5
        dom0 = s0 if finish and res0 in ("W", "L") else (0.5 + (share0 - 0.5)) if res0 not in ("W", "L") else (0.6 + 0.4 * share0 if s0 == 1 else 0.4 * share0)
        kf0 = kf1 = ELO_K
        if res0 in ("W", "L", "D"):
            fa.elo += elo_update(ea0, ea1, s0, kf0); fb.elo += elo_update(ea1, ea0, 1 - s0, kf1)
            fa.elo_dom += elo_update(ed0, ed1, dom0, kf0); fb.elo_dom += elo_update(ed1, ed0, 1 - dom0, kf1)
        for i, (me, op, k, opp_elo) in enumerate(((fa, fb, k0, ea1), (fb, fa, k1, ea0))):
            a = me.a
            mine = [r[i] for r in rows]; theirs = [r[1 - i] for r in rows]
            t = lambda rr, idx: sum(x[idx] for x in rr)
            res = res0 if i == 0 else {"W": "L", "L": "W"}.get(res0, res0)
            for kk in DEC: a[kk] *= DECAY
            a["n"] += 1; a["d_n"] += 1
            if res == "W": a["w"] += 1; a["d_w"] += 1
            if res == "L": a["l"] += 1
            for kk, v in (("secs", secs), ("sigL", t(mine, SIGL)), ("sigT", t(mine, SIGA)), ("osigL", t(theirs, SIGL)), ("osigT", t(theirs, SIGA)),
                          ("tdL", t(mine, TDL)), ("tdT", t(mine, TDA)), ("otdL", t(theirs, TDL)), ("otdT", t(theirs, TDA)),
                          ("ctrl", t(mine, CTRL)), ("octrl", t(theirs, CTRL)), ("kd", t(mine, KD)), ("okd", t(theirs, KD)),
                          ("sub", t(mine, SUB)), ("osub", t(theirs, SUB)), ("obody", t(theirs, BODYL)), ("oleg", t(theirs, LEGL)), ("ogr", t(theirs, GRL))):
                a[kk] += v
            for kk, v in (("d_secs", secs), ("d_sigL", t(mine, SIGL)), ("d_osigL", t(theirs, SIGL)), ("d_tdL", t(mine, TDL)), ("d_otdL", t(theirs, TDL)),
                          ("d_ctrl", t(mine, CTRL)), ("d_octrl", t(theirs, CTRL)), ("d_kd", t(mine, KD)), ("d_okd", t(theirs, KD))):
                a[kk] += v
            mysh = [(p if i == 0 else 1 - p) for p in pr]
            a["rounds"] += len(mysh); a["rshare"] += sum(mysh); a["d_rounds"] += len(mysh); a["d_rshare"] += sum(mysh)
            mk = "ko" if ("KO" in meth or "Doctor" in meth) else "sub" if "Submission" in meth else "dec"
            if res == "W": a[mk + "_w"] += 1
            if res == "L": a[mk + "_l"] += 1
            # luck: finished while ahead on banked rounds (stats), or finishing while behind
            if finish and len(mysh) > 1 and res in ("W", "L"):
                banked = sum(1 if p >= .6 else -1 if p <= .4 else 0 for p in mysh[:-1])
                if res == "L" and banked > 0: a["lost_ahead"] += 1
                if res == "W" and banked < 0: a["won_behind"] += 1
            a["opp_elo"] += opp_elo
            if sd:
                a["sd_f"] += 1
                for r in sd["rounds"]:
                    if r["own"] in ("0", "1"):
                        a["sd_r"] += 1
                        if int(r["own"]) == i: a["sd_won"] += 1
                        else: a["sd_lost"] += 1
                    a["sd108f"] += r["s108"][i]; a["sd108a"] += r["s108"][1 - i]
                    a["hurt_t"] += min(r["hurt"][i], 2); a["hurt_g"] += min(r["hurt"][1 - i], 2); a["fat"] += min(r["fat"][i], 2)
            if st and res == "L":
                a["st_n"] += 1
                lw = st.get("loser_weak", [])
                a["st_td"] += any(t_ in lw for t_ in ("takedown_defence", "bottom_game"))
                a["st_hurt"] += "got_hurt_standing" in lw
                a["st_one"] += "one_shot" in lw
                a["st_luckL"] += st.get("luck") == "big"
            elif st:
                a["st_n"] += 1
            me.res.append(res)
            if me.first is None: me.first = day
            me.last = day
        # population totals
        for kk, v in (("sigL", sum(r[0][SIGL] + r[1][SIGL] for r in rows)), ("sigT", sum(r[0][SIGA] + r[1][SIGA] for r in rows)),
                      ("tdL", sum(r[0][TDL] + r[1][TDL] for r in rows)), ("tdT", sum(r[0][TDA] + r[1][TDA] for r in rows)),
                      ("ctrl", sum(r[0][CTRL] + r[1][CTRL] for r in rows)), ("kd", sum(r[0][KD] + r[1][KD] for r in rows)),
                      ("sub", sum(r[0][SUB] + r[1][SUB] for r in rows)), ("obody", sum(r[0][BODYL] + r[1][BODYL] for r in rows)),
                      ("oleg", sum(r[0][LEGL] + r[1][LEGL] for r in rows)), ("ogr", sum(r[0][GRL] + r[1][GRL] for r in rows)),
                      ("secs", 2 * secs), ("secs60", 2 * secs / 60), ("secs900", 2 * secs / 900)):
            G[kk] += v
        G["osigL"] = G["sigL"]; G["osigT"] = G["sigT"]; G["otdL"] = G["tdL"]; G["otdT"] = G["tdT"]

X = np.array(X, dtype=np.float64); Y = np.array(Y)
_dates = np.array([m[1] for m in META]); Y[_dates >= "2019-01-01"] = -1   # firewall (development only)
os.makedirs(MLD, exist_ok=True)
np.savez_compressed(f"{MLD}/features.npz", X=X, Y=Y)
json.dump({"names": NAMES, "groups": GROUPS, "meta": META, "elo_k": ELO_K}, open(f"{MLD}/features_meta.json", "w"))
lab = Y >= 0
print("rows", len(Y), "labelled", int(lab.sum()), "features", X.shape[1], "from", META[0][1], "to", META[-1][1])
print("share of labelled rows where names[0] won:", round(float(Y[lab].mean()), 3), "(the models can't use this: no intercept, antisymmetric features)")
for g, ks in GROUPS.items():
    idx = [NAMES.index(k) for k in ks]
    cov = np.mean(~np.isnan(X[np.ix_(np.where(lab)[0], idx)]), axis=0)
    print(f"  {g:9s} coverage {np.round(cov.mean(), 3)}")
