"""Crawl a range of Sherdog news list pages [start, end) and record play-by-play article links."""
import json, re, sys, time, urllib.request
OUT, START, END = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
UA = {"User-Agent": "Mozilla/5.0 (fight-review research script)"}
found, lows = {}, {}
for page in range(START, END):
    url = f"https://www.sherdog.com/news/news/list/{page}"
    html = None
    for attempt in range(4):
        try:
            html = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read().decode("utf8", "replace"); break
        except Exception:
            time.sleep(2 ** attempt * 2)
    if html is None: print("give up", page, flush=True); continue
    links = set(re.findall(r'href="(/news/news/[^"#?]*-(\d{6}))"', html))
    if links: lows[page] = min(int(i) for _, i in links)
    for path, i in links:
        if re.search(r"play-?by-?play", path, re.I): found[path] = int(i)
    if page % 20 == 0:
        print(f"page {page} low {lows.get(page)} pbp {len(found)}", flush=True)
        json.dump({"found": found, "lows": lows}, open(OUT, "w"))
    time.sleep(0.5)
json.dump({"found": found, "lows": lows}, open(OUT, "w"))
print("done", START, END, len(found), flush=True)
