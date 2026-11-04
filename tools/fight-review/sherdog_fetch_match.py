"""Fetch the discovered Sherdog play-by-play articles (UFC only), parse them, and match each
article to a ledger event and each Sherdog fight to a ledger bout.
Usage: sherdog_fetch_match.py <scratch>"""
import glob, json, os, re, sys, time, unicodedata, urllib.request
sys.path.insert(0, os.path.dirname(__file__))
from sherdog_parse import parse
S = sys.argv[1]
UA = {"User-Agent": "Mozilla/5.0 (fight-review research script)"}
links = {}
for fn in glob.glob(f"{S}/sherdog/range_*.json") + [f"{S}/sherdog/pbp_links.json"]:
    d = json.load(open(fn))
    links.update(d.get("found", d) if isinstance(d, dict) else {})
ufc = {p: i for p, i in links.items() if re.search(r"/news/news/(UFC|Noche)", p, re.I)}
print("pbp links", len(links), "UFC", len(ufc))
os.makedirs(f"{S}/sherdog/html", exist_ok=True)
for path, i in sorted(ufc.items(), key=lambda x: x[1]):
    out = f"{S}/sherdog/html/{i}.html"
    if os.path.exists(out) and os.path.getsize(out) > 20000: continue
    for attempt in range(4):
        try:
            data = urllib.request.urlopen(urllib.request.Request("https://www.sherdog.com" + path, headers=UA), timeout=40).read()
            open(out, "wb").write(data); break
        except Exception as e:
            time.sleep(2 ** attempt * 2)
    time.sleep(0.6)

def fold(s): return unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
def toks(s): return [t for t in re.findall(r"[a-z]+", fold(s)) if t not in ("jr", "sr", "ii", "iii", "de", "da", "dos", "do", "the")]

L = json.load(open(f"{S}/work/ledger.json"))
events = [e for e in L["events"]]
arts = {}
for fn in sorted(glob.glob(f"{S}/sherdog/html/*.html")):
    aid = os.path.basename(fn)[:-5]
    try: arts[aid] = parse(fn)
    except Exception as e: print("parse fail", aid, e)

def headline_tokens(name):
    h = name.split(":", 1)[-1]
    return set(toks(h)) - {"vs", "ufc", "fight", "night"}

def name_score(a, b):
    ta, tb = toks(a), toks(b)
    if not ta or not tb: return 0
    if fold(a) == fold(b): return 3
    sa, sb = set(ta), set(tb)
    sc = 0
    if ta[-1] == tb[-1] or ta[-1] in sb or tb[-1] in sa: sc += 2
    if ta[0] == tb[0]: sc += 1
    if len(sa & sb) >= 2: sc += 1
    return min(sc, 3)

matched, ev_used = {}, {}
for aid, a in arts.items():
    if not a["date"] or not a["fights"]: continue
    ht = set(toks(re.sub(r"play-?by-?play.*", "", a["title"], flags=re.I)))
    cands = []
    from datetime import date as D
    ad = D.fromisoformat(a["date"])
    for e in events:
        gap = (D.fromisoformat(e["date"]) - ad).days
        if -2 <= gap <= 3:
            ov = len(headline_tokens(e["name"]) & ht)
            # bout-level overlap as tie-breaker
            bo = sum(1 for f in a["fights"] for b in e["bouts"] if max(name_score(f["names"][0], b["pick"]), name_score(f["names"][0], b["opp"])) >= 2)
            cands.append((ov, bo, -abs(gap), e))
    if not cands: continue
    cands.sort(key=lambda x: x[:3], reverse=True)
    ov, bo, _, e = cands[0]
    if bo < 2: continue
    key = e["date"] + "|" + e["name"]
    if key in ev_used and ev_used[key][1] >= bo: continue
    ev_used[key] = (aid, bo)

out = {}
for key, (aid, _) in ev_used.items():
    a = arts[aid]
    e = next(x for x in events if x["date"] + "|" + x["name"] == key)
    for f in a["fights"]:
        bestb, bs = None, 0
        for b in e["bouts"]:
            s1 = name_score(f["names"][0], b["pick"]) + name_score(f["names"][1], b["opp"])
            s2 = name_score(f["names"][0], b["opp"]) + name_score(f["names"][1], b["pick"])
            s = max(s1, s2)
            if s > bs: bestb, bs, flip = b, s, s2 > s1
        if bestb is None or bs < 3: continue
        # orient Sherdog's names to pick/opp
        sp, so = (f["names"][1], f["names"][0]) if flip else (f["names"][0], f["names"][1])
        out[bestb["id"]] = {"article": aid, "title": a["title"], "url_id": aid, "names": {"pick": sp, "opp": so},
                            "odds": f["odds"], "official": f["official"], "rounds": f["rounds"], "event": e["name"], "date": e["date"]}
json.dump(out, open(f"{S}/sherdog/matched.json", "w"), indent=1)
season = [e for e in events if e["date"] >= "2025-01-01"]
cov = {e["name"]: sum(1 for b in e["bouts"] if b["id"] in out) for e in season}
print("articles parsed", len(arts), "events matched", len(ev_used), "bouts matched", len(out))
print("season events with no sherdog:", [(e["date"], e["name"]) for e in season if cov[e["name"]] == 0])
print("season graded bouts", sum(len([b for b in e["bouts"] if b.get("won")]) for e in season), "matched", sum(cov.values()))
