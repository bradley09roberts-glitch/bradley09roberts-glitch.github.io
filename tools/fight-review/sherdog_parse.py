"""Parse Sherdog play-by-play articles into fights -> rounds (text + Sherdog scorers' round scores).
Usage: sherdog_parse.py <html_dir> <out.json>"""
import html as H, json, os, re, sys
from datetime import datetime

def text(s):
    s = re.sub(r"<script.*?</script>|<style.*?</style>|<div id=\"ad[^\"]*\".*?</div>\s*</div>|<div class='viaititle'>.*?</div>", " ", s, flags=re.S | re.I)
    s = re.sub(r"\s*\n\s*", " ", s)  # Sherdog hard-wraps its HTML; real breaks are <br>
    s = re.sub(r"<br\s*/?>|</p>|</h[1-6]>", "\n", s, flags=re.I)
    s = re.sub(r"<[^>]+>", " ", s)
    s = H.unescape(s)
    s = re.sub(r"[ \t\r\f\v]+", " ", s)
    s = re.sub(r"\s*\n\s*", "\n", s)
    return s.strip()

def parse(path):
    t = open(path, encoding="utf8", errors="replace").read()
    title = H.unescape(re.sub(r"\s+", " ", (re.search(r"<h1>(.*?)</h1>", t, re.S) or [None, ""])[1])).strip()
    dm = re.search(r"<span>([A-Z][a-z]{2} \d{1,2}, \d{4})</span>", t)
    date = datetime.strptime(dm.group(1), "%b %d, %Y").strftime("%Y-%m-%d") if dm else None
    ev = re.search(r'href="(/events/[^"]+)"', t)
    body = t[t.find('class="content body_content"'):] if 'class="content body_content"' in t else t
    end = body.find('<div class="article-tags')
    if end > 0: body = body[:end]
    chunks = re.split(r'<div class="event">', body)[1:]
    if not chunks:  # older layout: fights separated by <h2> headers
        chunks = re.split(r"(?=<h2>)", body)[1:]
    fights = []
    for c in chunks:
        h2 = re.search(r"<h2>(.*?)</h2>", c, re.S)
        if not h2: continue
        hh = h2.group(1)
        links = re.findall(r'<a href=\s*"(/fighter/[^"]+)"\s*>(.*?)</a>', hh, re.S)
        if len(links) >= 2:
            names = [re.sub(r"\s+", " ", H.unescape(re.sub(r"<[^>]+>", "", n))).strip() for _, n in links[:2]]
            sids = [u.split("/")[-1] for u, _ in links[:2]]
        else:
            parts = re.split(r"\bvs\.?\b", text(hh), maxsplit=1)
            names = [re.sub(r"\(.*?\)|\*", "", p).strip() for p in parts] if len(parts) == 2 else []
            sids = [None, None]
        if len(names) != 2: continue
        rest = c[h2.end():]
        odds = re.search(r"ODDS:\s*([^<\n]+)", text(rest))
        res_i = re.search(r"<h[34]>\s*(The )?Official Result\s*</h[34]>", rest, re.I)
        official = None
        if res_i:
            official = text(rest[res_i.end():res_i.end() + 600]).split("\n")[0].strip()
            rest_r = rest[:res_i.start()]
        else:
            rest_r = rest
        rounds = []
        parts = re.split(r"<h3>\s*Round\s*(\d+)\s*</h3>", rest_r)
        for i in range(1, len(parts), 2):
            n, seg = int(parts[i]), parts[i + 1]
            sc_i = re.search(r"<h4>\s*Sherdog Scores\s*</h4>", seg, re.I)
            narr = text(seg[:sc_i.start()] if sc_i else seg)
            scores = []
            if sc_i:
                for line in text(seg[sc_i.end():]).split("\n"):
                    m = re.match(r"\s*(.+?) scores the round:?\s*(\d+)\s*-\s*(\d+)\s*(.*)$", line)
                    if not m: continue
                    name, tot = m.group(4).strip(), None
                    tm = re.search(r"\((\d+)\s*-\s*(\d+)\s*([^)]*)\)?", name)
                    if tm: tot, name = [int(tm.group(1)), int(tm.group(2)), tm.group(3).strip()], name[:tm.start()].strip()
                    scores.append([m.group(1).strip(), int(m.group(2)), int(m.group(3)), name, tot])
            rounds.append({"n": n, "text": narr, "scores": scores})
        fights.append({"names": names, "sherdog_ids": sids, "odds": odds.group(1).strip() if odds else None,
                       "rounds": rounds, "official": official})
    return {"title": title, "date": date, "event_link": ev.group(1) if ev else None, "fights": fights}

if __name__ == "__main__":
    src, out = sys.argv[1], sys.argv[2]
    res = {}
    for fn in sorted(os.listdir(src)):
        if fn.endswith(".html"):
            res[fn[:-5]] = parse(os.path.join(src, fn))
    json.dump(res, open(out, "w"), indent=1)
    for k, v in res.items():
        print(k, v["date"], v["title"][:60], "fights", len(v["fights"]), "rounds", sum(len(f["rounds"]) for f in v["fights"]),
              "scored", sum(1 for f in v["fights"] for r in f["rounds"] if r["scores"]), "official", sum(1 for f in v["fights"] if f["official"]))
