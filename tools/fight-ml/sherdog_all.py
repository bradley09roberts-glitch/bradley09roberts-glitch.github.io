"""Sherdog play-by-play for every UFC fight it covers: fetch, parse, match to UFCStats fights (any fight, not only the
ledger's), and turn each round into features oriented to the UFCStats name order (index 0 = names[0]).

Per round: Sherdog's scorers (who won, 10-8s), who was hurt (from the notes), who was tiring.
The odds Sherdog prints in some articles are never read (a closing-line proxy must not become a feature).
Usage: sherdog_all.py <scratch>   ->  sherdog/all_fights.json
"""
import glob, json, os, re, sys, time, unicodedata, urllib.request
from datetime import date as D
S = sys.argv[1]
sys.path.insert(0, f"{S}/scripts")
from sherdog_parse import parse

UA = {"User-Agent": "Mozilla/5.0 (fight-review research script)"}
links = {}
for fn in glob.glob(f"{S}/sherdog/range_*.json") + [f"{S}/sherdog/pbp_links.json"]:
    d = json.load(open(fn))
    links.update(d.get("found", d) if isinstance(d, dict) else {})
ufc = {p: i for p, i in links.items() if re.search(r"/news/news/(UFC|Noche|The-Ultimate-Fighter|TUF)", p, re.I)}
print("play-by-play links", len(links), "UFC", len(ufc), flush=True)
os.makedirs(f"{S}/sherdog/html", exist_ok=True)
for path, i in sorted(ufc.items(), key=lambda x: -x[1]):
    out = f"{S}/sherdog/html/{i}.html"
    if os.path.exists(out) and os.path.getsize(out) > 20000: continue
    for attempt in range(4):
        try:
            data = urllib.request.urlopen(urllib.request.Request("https://www.sherdog.com" + path, headers=UA), timeout=40).read()
            open(out, "wb").write(data); break
        except Exception:
            time.sleep(2 ** attempt * 2)
    time.sleep(0.4)

def fold(s): return unicodedata.normalize("NFKD", re.sub(r"['’`]", "", s or "")).encode("ascii", "ignore").decode().lower()
def toks(s): return [t for t in re.findall(r"[a-z]+", fold(s)) if t not in ("jr", "sr", "ii", "iii")]
def near(a, b):
    if a == b: return True
    if min(len(a), len(b)) < 5 or abs(len(a) - len(b)) > 1: return False
    if len(a) == len(b): return sum(x != y for x, y in zip(a, b)) <= 1
    if len(a) > len(b): a, b = b, a
    return any(b[:i] + b[i + 1:] == a for i in range(len(b)))
def name_score(a, b):
    ta, tb = toks(a), toks(b)
    if not ta or not tb: return 0
    if fold(a) == fold(b): return 3
    sc = 0
    if any(near(x, tb[-1]) for x in ta) or any(near(x, ta[-1]) for x in tb): sc += 2
    if near(ta[0], tb[0]): sc += 1
    return min(sc, 3)

U = json.load(open(f"{S}/work/ufc_fights.json"))
by_date = {}
for fid, f in U.items():
    if f.get("d"): by_date.setdefault(f["d"], []).append(fid)

arts = {}
for fn in glob.glob(f"{S}/sherdog/html/*.html"):
    aid = os.path.basename(fn)[:-5]
    try:
        a = parse(fn)
        if a["date"] and a["fights"]: arts[aid] = a
    except Exception as e:
        print("parse fail", aid, e)

# ---------- text features from a round's notes
HURT_PASSIVE = r"(?:is|looks|appears|seems|was|gets|got|is clearly|is badly|has been)\s+(?:badly\s+|visibly\s+|momentarily\s+|clearly\s+|briefly\s+|now\s+)?(?:hurt|rocked|wobbled|stunned|stung|dazed|staggered|buzzed|shaken|dropped|floored|knocked (?:down|out|unconscious|silly)|down|out|in trouble|on wobbly legs|on rubbery legs|on (?:baby deer|jelly|spaghetti) legs)"
HURT_INTRANS = r"(?:staggers|wobbles|stumbles|reels|wilts|crumbles|crumples|collapses|slumps|goes down|goes limp|falls(?! back| into| for| short)|hits the (?:deck|canvas|mat|floor|ground)|takes a knee|drops to (?:the canvas|the mat|a knee|his knees|her knees|all fours|the floor)|falls to (?:the canvas|the mat|the floor)|is sent (?:down|reeling|crashing))"
HURT_TRANS = r"(?:rocks|wobbles|hurts|stuns|stings|staggers|drops|floors|buckles|dazes|crumples|fells|rattles|shakes|sits|puts|sets|sends|knocks|flattens|levels|folds|clips)"
FATIGUE = r"\b(?:tired|tiring|fatigue|fatigued|fading|fades|slowing|slows|gassed|exhausted|winded|breathing heavily|sucking wind|labou?ring)\b"

def mentions(sent, tokset):
    s = fold(sent)
    pos = [m.start() for t in tokset for m in re.finditer(r"\b" + re.escape(t) + r"\b", s)]
    return min(pos) if pos else None

def round_text_features(text, nm0, nm1):
    t0, t1 = set(toks(nm0)), set(toks(nm1))
    d0, d1 = (t0 - t1) or t0, (t1 - t0) or t1           # tokens that tell the two apart
    d0 = {t for t in d0 if len(t) > 2}; d1 = {t for t in d1 if len(t) > 2}
    hurt, fat = [0, 0], [0, 0]
    for sent in re.split(r"(?<=[.!?])\s+", text or ""):
        s = fold(sent)
        p0, p1 = mentions(sent, d0), mentions(sent, d1)
        victim = None
        # transitive first ("Gomes wobbles her"), then passive or intransitive ("Yan is rocked", "Edgar falls")
        m = re.search(r"\b" + HURT_TRANS + r"\s+(him|her|" + "|".join(map(re.escape, d0 | d1)) + r")\b", s) if (d0 | d1) else None
        if not m:
            for i, dset in ((0, d0), (1, d1)):
                for t in dset:
                    if re.search(r"\b" + re.escape(t) + r"(?:s)?\s+" + HURT_PASSIVE, s) or re.search(r"\b" + re.escape(t) + r"\s+" + HURT_INTRANS + r"(?!\s+(?:him|her)\b)", s) \
                            or re.search(r"\b" + re.escape(t) + r"s?\s+(?:legs?|knees?)\s+(?:buckle|wobble|give)", s):
                        victim = i
        if victim is None and m:
            if m:
                obj = m.group(1)
                if obj in d0: victim = 0
                elif obj in d1: victim = 1
                else:
                    before = s[:m.start()]
                    b0, b1 = mentions(before, d0), mentions(before, d1)
                    if b0 is not None and b1 is None: victim = 1
                    elif b1 is not None and b0 is None: victim = 0
                    elif b0 is not None and b1 is not None: victim = 1 if b0 < b1 else 0
        if victim is not None: hurt[victim] += 1
        if re.search(FATIGUE, s):
            if p0 is not None and p1 is None: fat[0] += 1
            elif p1 is not None and p0 is None: fat[1] += 1
            elif p0 is not None and p1 is not None: fat[0 if p0 < p1 else 1] += 1
    return hurt, fat

def scorer_side(name, nm0, nm1):
    t = toks(name)
    if not t: return None
    a, b = toks(nm0), toks(nm1)
    def sc(ft):
        if not ft: return 0
        if any(near(x, ft[-1]) for x in t): return 2
        return 1 if any(near(x, y) for x in t for y in ft) else 0
    s0, s1 = sc(a), sc(b)
    return 0 if s0 > s1 else 1 if s1 > s0 else None

out, used = {}, {}
for aid, a in sorted(arts.items()):
    ad = D.fromisoformat(a["date"])
    cands = [fid for k in range(-2, 4) for fid in by_date.get(str(D.fromordinal(ad.toordinal() + k)), [])]
    if not cands: continue
    hits = []
    for f in a["fights"]:
        best, bs, flip = None, 0, False
        for fid in cands:
            n0, n1 = U[fid]["names"]
            s1 = name_score(f["names"][0], n0) + name_score(f["names"][1], n1)
            s2 = name_score(f["names"][0], n1) + name_score(f["names"][1], n0)
            s = max(s1, s2)
            if s > bs: best, bs, flip = fid, s, s2 > s1
        if best and bs >= 4: hits.append((f, best, flip))
    for f, fid, flip in hits:
        if fid in used and used[fid] >= len(hits): continue
        used[fid] = len(hits)
        nm0, nm1 = (f["names"][1], f["names"][0]) if flip else (f["names"][0], f["names"][1])
        rounds = []
        for r in f["rounds"]:
            codes, n108 = [], [0, 0]
            for sc in r.get("scores", []):
                _, x, y, nm, _tot = sc
                if x == y: codes.append("e"); continue
                side = scorer_side(nm, nm0, nm1)
                if side is None: codes = None; break
                win = side if x > y else 1 - side
                codes.append(str(win))
                if abs(x - y) >= 2: n108[win] += 1
            own = None
            if codes:
                for k in ("0", "1", "e"):
                    if codes.count(k) * 2 > len(codes): own = k
                if own is None: own = "e"
            hurt, fat = round_text_features(r.get("text", ""), nm0, nm1)
            rounds.append({"n": r["n"], "own": own, "s108": n108, "hurt": hurt, "fat": fat})
        out[fid] = {"a": aid, "date": a["date"], "names": [nm0, nm1], "rounds": rounds}

json.dump(out, open(f"{S}/sherdog/all_fights.json", "w"))
yrs = {}
for fid, x in out.items(): yrs[x["date"][:4]] = yrs.get(x["date"][:4], 0) + 1
print("articles", len(arts), "UFC fights matched", len(out), "by year", dict(sorted(yrs.items())))

# ---------- check the hurt reading against UFCStats knockdowns
KD = 0
agree = tot = fp = 0
for fid, x in out.items():
    f = U[fid]; R0 = f["rounds"].get(f["names"][0], []); R1 = f["rounds"].get(f["names"][1], [])
    for r in x["rounds"]:
        i = r["n"] - 1
        if i >= len(R0) or i >= len(R1): continue
        k0, k1 = R1[i][KD], R0[i][KD]       # knockdowns suffered by fighter 0 / fighter 1
        if (k0 > 0) != (k1 > 0):
            tot += 1; v = 0 if k0 > 0 else 1
            agree += r["hurt"][v] > 0 and r["hurt"][1 - v] == 0
            fp += r["hurt"][1 - v] > 0
print(f"rounds with a one-sided knockdown: {tot}; hurt reading names the right fighter only: {agree} ({agree / max(tot, 1):.0%}); names the other fighter too or instead: {fp}")
