"""Walk-forward fight features from the first UFC fight on record.

Every fight, in date order. For each card: snapshot every fighter's state, write one feature row per fight from those
snapshots, and only then update the states with the card's results. Nothing from a fight (or anything later) can reach
that fight's row. Odds never enter.

Row = fighter A (UFCStats names[0]) minus fighter B (names[1]); every feature is antisymmetric, so the models (which
have no intercept) give P(A) = 1 - P(B) and can't learn the UFCStats habit of listing winners first.
Rates are shrunk toward population means of past fights; a debutant's UFCStats rates start at those priors, so a fight
against a debutant still carries the other fighter's record. Layoff, Sherdog and story features stay blank (NaN) when
there is no data, never zero. Missing physicals are filled with running typical values from fighters already seen.
Ratings group: Glicko-1 (rating plus RD that grows with time out) and an Elo, both fed a margin-of-victory score: 30% from
the outcome (finish = full win, decision by the judges' point margin) and 70% from the stats round share; gl_p is the
Glicko win logit.

Inputs (scratch): work/ufc_fights.json, raw/ufc_fighter_tott.csv, sherdog/all_fights.json, stories/*.json, and
ML_DIR/round_model_pre2019.json (the stats round scorer, fitted on 2016-18 Sherdog rounds)
Output: ml/features.npz + ml/features_meta.json
"""
import csv, glob, json, math, os, re, sys, unicodedata
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
# One fighter, two spellings: the fight files and the fighter file sometimes disagree. Exact match first, then a listed
# alias, then a match that ignores accents, punctuation, hyphens and Jr./III suffixes (only when it points to exactly one
# fighter-file name, so Lance Gibson and Lance Gibson Jr. stay apart).
ALIAS = {"Patricio Pitbull": "Patricio Freire", "Rafael Cerquiera": "Rafael Cerqueira", "Zach Reese": "Zachary Reese",
         "Joe Kropschot": "Joseph Kropschot", "Nick Galanti": "Nicholas Galanti", "Mahammadali Osmanli": "Mehemmedeli Osmanli",
         "Ilimbek Akylbek": "Ilimbek Akylbek Uulu", "Cam Nelson": "Cameron Nelson", "YiSak Lee": "Yi Sak Lee",
         "Hector Santiago": "Hector de Sousa Santiago"}
def _norm(s_):
    s_ = unicodedata.normalize("NFKD", s_).encode("ascii", "ignore").decode().lower()
    s_ = re.sub(r"[.'’`]", "", s_); s_ = re.sub(r"[-_]", " ", s_)
    return " ".join(t for t in s_.split() if t not in ("jr", "sr", "ii", "iii", "iv"))
_TNAMES = {r["FIGHTER"].strip() for r in tott}
_TNORM = defaultdict(set)
for _n in _TNAMES: _TNORM[_norm(_n)].add(_n)
def canon(name):
    if name in _TNAMES: return name
    if name in ALIAS and ALIAS[name] in _TNAMES: return ALIAS[name]
    c = _TNORM.get(_norm(name), set())
    return next(iter(c)) if len(c) == 1 else name
CANON = {n: canon(n) for f in U.values() for n in f.get("names", [])}
UNMATCHED = sorted(n for n, c in CANON.items() if c not in _TNAMES)

DIVS = [("light heavyweight", 205), ("strawweight", 115), ("flyweight", 125), ("bantamweight", 135), ("featherweight", 145),
        ("lightweight", 155), ("welterweight", 170), ("middleweight", 185), ("heavyweight", 265)]
def div_lbs(wc):
    s_ = (wc or "").lower()
    for nm, lb in DIVS:
        if nm in s_: return lb
    return None
def lbs(s_):
    m = re.match(r"\s*(\d+)", s_ or ""); return int(m.group(1)) if m else None
def phys_of(r):
    try: dob = datetime.strptime(r["DOB"].strip(), "%b %d, %Y").date()
    except Exception: dob = None
    return {"dob": dob, "h": inches(r["HEIGHT"]), "r": reach(r["REACH"]), "st": (r["STANCE"] or "").strip().lower()}
PHYS, TOTT_ROWS = {}, defaultdict(list)
for r in tott:
    n = r["FIGHTER"].strip()
    if n in DUPS: TOTT_ROWS[n].append((lbs(r["WEIGHT"]), phys_of(r)))
    else: PHYS[n] = phys_of(r)
# Two UFCStats fighters share each name in DUPS. Each bout goes to the namesake whose listed weight is nearest the bout's
# division; a fighter who moves up or down one division (within 15 lb, within 3 years of their last bout) keeps their
# identity. Uses only the bout's own division and earlier assignments.
KEY, _last = {}, {}
for _fid, _f in sorted(U.items(), key=lambda kv: kv[1].get("d") or ""):
    for _un in _f.get("names", []):
        _nm = CANON[_un]
        if _nm not in DUPS: continue
        _rows, _lb, _d = TOTT_ROWS[_nm], div_lbs(_f.get("wc")), D.fromisoformat(_f["d"]) if _f.get("d") else None
        _prev = _last.get(_nm)
        dist = lambda j: abs(_rows[j][0] - (_lb or 170)) if _rows[j][0] else 999
        if _prev and (_lb is None or (_rows[_prev[0]][0] and abs(_rows[_prev[0]][0] - _lb) <= 15 and _d and (_d - _prev[1]).days <= 3 * 365)):
            j = _prev[0]
        else:
            j = min(range(len(_rows)), key=dist)
        KEY[(_fid, _un)] = f"{_nm}|{j}"; PHYS[f"{_nm}|{j}"] = _rows[j][1]
        if _d: _last[_nm] = (j, _d)
def key_of(fid, name): return KEY.get((fid, name), CANON.get(name, name))

# ---------- missing physicals are filled with typical values, never left blank or flagged.
# UFCStats (scraped in 2026) mostly filled in reach, height, stance and (before 2014) DOB for fighters who stayed on the
# roster, so a blank marks a fighter who was later cut: information from after the fight. A filled-in value looks like a
# real one, so the learners can't tell which fighters had blanks. Fill-in values are running means of fighters already
# seen (weak fixed priors: 5 pseudo-observations at 70 in, +1.5 in ape index, age 28), updated after each card; no results.
class Running:
    """Mean of values seen so far, starting from a weak prior (FILL_PRIOR pseudo-observations at a fixed typical value)."""
    def __init__(self, prior): self.s = prior * FILL_PRIOR; self.c = FILL_PRIOR
    def add(self, v): self.s += v; self.c += 1
    def mean(self): return self.s / self.c
FILL_PRIOR = 5
FILL_H = defaultdict(lambda: Running(70.0))      # height by division (lb), from fighters already seen in it
FILL_HALL = Running(70.0); FILL_APE = Running(1.5); FILL_AGE = Running(28.0)
SEEN_PHYS, SEEN_DIV = set(), set()
def fill_update(key, wc, day):
    """After a card: add this fighter's real physicals to the running fill-in values (once each)."""
    P = PHYS.get(key, {})
    lb = div_lbs(wc)
    if P.get("h") and (key, lb) not in SEEN_DIV:
        SEEN_DIV.add((key, lb)); FILL_H[lb].add(P["h"])
    if key in SEEN_PHYS: return
    SEEN_PHYS.add(key)
    if P.get("h"): FILL_HALL.add(P["h"])
    if P.get("h") and P.get("r"): FILL_APE.add(P["r"] - P["h"])
    if P.get("dob"): FILL_AGE.add((day - P["dob"]).days / 365.25)   # age at this, their first UFC fight

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
    __slots__ = ("a", "elo", "elo_dom", "first", "last", "res", "gl_r", "gl_rd", "gl_last", "elo_m")
    def __init__(self):
        self.a = defaultdict(float); self.elo = 1500.0; self.elo_dom = 1500.0; self.first = None; self.last = None; self.res = []
        self.gl_r = 1500.0; self.gl_rd = None; self.gl_last = None; self.elo_m = 1500.0
FS = defaultdict(F)
G = defaultdict(float)   # population totals, for shrinkage priors (past fights only)

# ---------- rating systems with uncertainty and margin of victory (parameters tuned on 2014-2018 only)
GL_RD0 = float(os.environ.get("GL_RD0", "300"))     # Glicko RD of a debutant
GL_C = float(os.environ.get("GL_C", "200"))         # RD growth: RD^2 += C^2 * years since the last fight
GL_RDMAX = max(GL_RD0, 350.0)
MOV_GAM = float(os.environ.get("MOV_GAM", "0.5"))   # decision score = 0.5 + GAM * judges' mean point margin per round
MOV_BETA = float(os.environ.get("MOV_BETA", "0.7")) # weight of the stats round share (round model) in the score
EM_K = float(os.environ.get("EM_K", "200"))         # K of the margin-of-victory Elo
GQ = math.log(10) / 400
def gfun(rd): return 1 / math.sqrt(1 + 3 * GQ * GQ * rd * rd / math.pi ** 2)
def gl_cur_rd(f, day):
    if f.gl_rd is None: return GL_RD0
    return min(math.sqrt(f.gl_rd ** 2 + GL_C ** 2 * (day - f.gl_last).days / 365.25), GL_RDMAX)
def mov_score(res0, meth, judges, nrd, pr):
    """Score of fighter 0 in [0, 1]: finish = 1/0, decisions by the judges' point margin, blended with the stats round share."""
    if res0 not in ("W", "L"): s = 0.5
    else:
        sg = 1 if res0 == "W" else -1
        if meth.startswith("Decision"):
            J = [j for j in (judges or []) if len(j) == 3 and isinstance(j[1], (int, float)) and isinstance(j[2], (int, float))]
            if J and nrd:
                s = 0.5 + MOV_GAM * sum(j[1] - j[2] for j in J) / len(J) / nrd
                s = max(s, 0.5) if sg > 0 else min(s, 0.5)
            else: s = 0.5 + sg * 0.25
        else: s = 0.5 + sg * 0.5
    if pr: s = (1 - MOV_BETA) * s + MOV_BETA * (sum(pr) / len(pr))
    return min(max(s, 0.0), 1.0)

DEBUT_PRIORS = os.environ.get("DEBUT_PRIORS", "1") == "1"   # 0 = original: a debutant's record and stats stay blank
RATINGS = os.environ.get("RATINGS", "1") == "1"             # 0 = original: no Glicko / margin-of-victory group
SHRINK = float(os.environ.get("SHRINK", "4"))   # multiplies every prior weight below; tuned on 2014-2018 (validation) only
def rate(num, den, prior_mean, prior_w):
    prior_w *= SHRINK
    return (num + prior_mean * prior_w) / (den + prior_w)

def fighter_feats(f, day, name, wc=None):
    a = f.a; n = a["n"]
    gm = lambda k, d: (G[k] / G[d]) if G[d] else None
    mins, m15 = a["secs"] / 60, a["secs"] / 900
    P = PHYS.get(name, {})
    pm = {k: gm(*v) for k, v in {"slpm": ("sigL", "secs60"), "sapm": ("osigL", "secs60"), "acc": ("sigL", "sigT"), "def": ("osigL", "osigT"),
          "td15": ("tdL", "secs900"), "tdacc": ("tdL", "tdT"), "tdsucc": ("otdL", "otdT"), "ctrl": ("ctrl", "secs"),
          "kd15": ("kd", "secs900"), "sub15": ("sub", "secs900"), "body": ("obody", "secs60"), "leg": ("oleg", "secs60"),
          "gr": ("ogr", "secs60")}.items()}
    if any(v is None for v in pm.values()): pm = {k: 0.0 for k in pm}
    x = {}
    x["elo"] = f.elo; x["elo_dom"] = f.elo_dom
    x["gl_r"] = f.gl_r; x["gl_rd"] = gl_cur_rd(f, day); x["elo_mov"] = f.elo_m
    x["exp"] = math.log1p(n)
    x["ufc_years"] = ((day - f.first).days / 365.25) if f.first else 0.0
    x["debut"] = 1.0 if n == 0 else 0.0
    # a debutant gets the shrinkage priors themselves (population means of past fights), so a fight against a debutant
    # still carries the other fighter's record instead of going blank; Sherdog and story notes stay gated on coverage
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
    x["opp_elo"] = a["opp_elo"] / n if n else 1500.0
    x["streak"] = float(sum(1 if r == "W" else -1 for r in f.res[-3:]))
    x["layoff"] = math.log1p((day - f.last).days) if f.last else float("nan")
    if n == 0 and not DEBUT_PRIORS:
        for k_ in REC_STATS: x.pop(k_, None)
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
    else: x["age"] = FILL_AGE.mean() + (((day - f.first).days / 365.25) if f.first else 0.0); imp = 1
    h = P.get("h")
    if not h:
        lb = div_lbs(wc); fh = FILL_H[lb] if (lb in FILL_H and FILL_H[lb].c - FILL_PRIOR >= 10) else FILL_HALL
        h = round(fh.mean()); imp = 1
    x["height"] = float(h)
    if P.get("r"): x["reach"] = float(P["r"])
    else: x["reach"] = float(round(h + FILL_APE.mean())); imp = 1
    st = P.get("st") or ""
    if not st: st = "orthodox"; imp = 1
    x["southpaw"] = 1.0 if st == "southpaw" else 0.0; x["switch"] = 1.0 if st == "switch" else 0.0
    x["_imp"] = imp                       # diagnostics only (not a feature)
    return x

GROUPS = {
    "elo": ["elo", "elo_dom"],
    "ratings": ["gl_r", "gl_rd", "gl_p", "elo_mov"],
    "record": ["exp", "ufc_years", "debut", "winrate", "fin_w", "ko_l", "sub_l", "luck_adj", "opp_elo", "streak", "layoff", "d_win"],
    "stats": ["slpm", "sapm", "acc", "sdef", "td15", "tdacc", "tddef", "ctrl", "octrl", "kd15", "okd15", "sub15", "osub15",
              "body_abs", "leg_abs", "gr_abs", "rshare", "d_slpm", "d_sapm", "d_td15", "d_ctrl", "d_kd15", "d_okd15", "d_rshare"],
    "physical": ["age", "height", "reach", "southpaw", "switch"],
    "sherdog": ["sd_share", "sd_108", "hurt_t", "hurt_g", "fat", "cov_sd"],
    "stories": ["st_td", "st_hurt", "st_one", "st_luckL", "cov_st"],
    "style": ["x_grap", "x_ctrl", "x_strike", "x_power", "x_sub", "x_hurt"],
}
if not RATINGS: del GROUPS["ratings"]
NAMES = [k for g in GROUPS.values() for k in g]
REC_STATS = ["winrate", "fin_w", "ko_l", "sub_l", "luck_adj", "opp_elo", "streak", "layoff", "d_win"] + GROUPS["stats"]

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
    da, db = xa["gl_rd"], xb["gl_rd"]
    v["gl_p"] = gfun(math.sqrt(da * da + db * db)) * (xa["gl_r"] - xb["gl_r"]) * GQ   # Glicko win logit: rating gap shrunk by both RDs
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
X, Y, META, IMP = [], [], [], []
for day_s in sorted(by_day):
    day = D.fromisoformat(day_s)
    # 1) snapshot and write rows
    for fid in by_day[day_s]:
        f = U[fid]; n0, n1 = f["names"]; k0, k1 = key_of(fid, n0), key_of(fid, n1)
        o = f["out"]
        y = 1 if o[0] == "W" else 0 if o[0] == "L" else None
        xa, xb = fighter_feats(FS[k0], day, k0, f.get("wc")), fighter_feats(FS[k1], day, k1, f.get("wc"))
        IMP.append([xa["_imp"], xb["_imp"]])
        X.append(row(xa, xb)); Y.append(-1 if y is None else y)
        META.append([fid, day_s, n0, n1, int(FS[k0].a["n"]), int(FS[k1].a["n"]), int(FS[k0].a["sd_f"] > 0), int(FS[k1].a["sd_f"] > 0)])
    # 2) update with the card's results
    for fid in by_day[day_s]:
        f = U[fid]; n0, n1 = f["names"]; k0, k1 = key_of(fid, n0), key_of(fid, n1)
        o = f["out"]; res0 = o[0]
        R0, R1 = f["rounds"].get(n0, []), f["rounds"].get(n1, [])
        nr = max(len(R0), len(R1))
        rows = [((R0[i] if i < len(R0) else ZERO), (R1[i] if i < len(R1) else ZERO)) for i in range(nr)]
        secs = (f.get("secs") or 0) if nr > 0 else 0      # no round stats (some 1990s fights): no minutes either
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
        if res0 in ("W", "L", "D"):
            sm = mov_score(res0, meth, f.get("judges"), f.get("rd"), pr)
            # margin-of-victory Elo
            em0, em1 = fa.elo_m, fb.elo_m
            fa.elo_m += elo_update(em0, em1, sm, EM_K); fb.elo_m += elo_update(em1, em0, 1 - sm, EM_K)
            # Glicko-1, one fight per rating period, RD grown for the time out
            da, db = gl_cur_rd(fa, day), gl_cur_rd(fb, day); ra, rb = fa.gl_r, fb.gl_r
            new = []
            for me, r, d, ro, do, sc in ((fa, ra, da, rb, db, sm), (fb, rb, db, ra, da, 1 - sm)):
                g = gfun(do); E = 1 / (1 + 10 ** (-g * (r - ro) / 400))
                inv = 1 / d ** 2 + GQ * GQ * g * g * E * (1 - E)
                new.append((me, r + GQ / inv * g * (sc - E), math.sqrt(1 / inv)))
            for me, r, d in new: me.gl_r, me.gl_rd, me.gl_last = r, d, day
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
            fill_update(k, f.get("wc"), day)
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
if os.environ.get("FIREWALL") == "1":
    # development runs: results from 2019 on are hidden (unlabelled), so no choice can be tuned on the test period
    _dates = np.array([m[1] for m in META]); Y[_dates >= "2019-01-01"] = -1
os.makedirs(MLD, exist_ok=True)
np.savez_compressed(f"{MLD}/features.npz", X=X, Y=Y, IMP=np.array(IMP))
json.dump({"names": NAMES, "groups": GROUPS, "meta": META, "elo_k": ELO_K,
           "ratings": {"gl_rd0": GL_RD0, "gl_c": GL_C, "mov_gam": MOV_GAM, "mov_beta": MOV_BETA, "em_k": EM_K},
           "shrink": SHRINK, "debut_priors": DEBUT_PRIORS, "ratings_group": RATINGS, "firewall": os.environ.get("FIREWALL") == "1",
           "names_without_fighter_file_row": UNMATCHED,
           "fill_ins_final": {"age_at_debut": round(FILL_AGE.mean(), 2), "ape": round(FILL_APE.mean(), 2), "height_all": round(FILL_HALL.mean(), 1)}}, open(f"{MLD}/features_meta.json", "w"))
lab = Y >= 0
print("rows", len(Y), "labelled", int(lab.sum()), "features", X.shape[1], "from", META[0][1], "to", META[-1][1])
print("share of labelled rows where names[0] won:", round(float(Y[lab].mean()), 3), "(the models can't use this: no intercept, antisymmetric features)")
for g, ks in GROUPS.items():
    idx = [NAMES.index(k) for k in ks]
    cov = np.mean(~np.isnan(X[np.ix_(np.where(lab)[0], idx)]), axis=0)
    print(f"  {g:9s} coverage {np.round(cov.mean(), 3)}")
