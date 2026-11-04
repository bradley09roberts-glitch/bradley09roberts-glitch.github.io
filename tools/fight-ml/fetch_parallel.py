"""Fetch every UFC play-by-play article found by the crawlers, six at a time."""
import glob, json, os, re, sys, time, urllib.request
from concurrent.futures import ThreadPoolExecutor
S = sys.argv[1]
UA = {"User-Agent": "Mozilla/5.0 (fight-review research script)"}
links = {}
for fn in glob.glob(f"{S}/sherdog/range_*.json") + [f"{S}/sherdog/pbp_links.json"]:
    d = json.load(open(fn)); links.update(d.get("found", d) if isinstance(d, dict) else {})
todo = [(p, i) for p, i in links.items() if re.search(r"/news/news/(UFC|Noche|The-Ultimate-Fighter|TUF)", p, re.I)
        and not (os.path.exists(f"{S}/sherdog/html/{i}.html") and os.path.getsize(f"{S}/sherdog/html/{i}.html") > 20000)]
print("to fetch", len(todo), flush=True)
def get(pi):
    p, i = pi
    for attempt in range(4):
        try:
            data = urllib.request.urlopen(urllib.request.Request("https://www.sherdog.com" + p, headers=UA), timeout=60).read()
            open(f"{S}/sherdog/html/{i}.html", "wb").write(data); time.sleep(0.5); return 1
        except Exception:
            time.sleep(2 ** attempt * 2)
    return 0
with ThreadPoolExecutor(6) as ex: ok = sum(ex.map(get, todo))
print("fetched", ok, "of", len(todo))
