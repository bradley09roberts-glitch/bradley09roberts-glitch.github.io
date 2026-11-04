"""Walk Sherdog's news list pages back to a cut-off article id and record every
play-by-play article link. Polite: one request at a time with a pause."""
import json, re, sys, time, urllib.request
OUT = sys.argv[1]
STOP_ID = int(sys.argv[2]) if len(sys.argv) > 2 else 195000
UA = {"User-Agent": "Mozilla/5.0 (fight-review research script)"}
found, page, low = {}, 1, 10**9
while low > STOP_ID and page < 1500:
    url = f"https://www.sherdog.com/news/news/list/{page}"
    for attempt in range(4):
        try:
            html = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read().decode("utf8", "replace")
            break
        except Exception as e:
            time.sleep(2 ** attempt * 2)
    else:
        print("give up page", page, flush=True); page += 1; continue
    links = set(re.findall(r'href="(/news/news/[^"#?]*-(\d{6}))"', html))
    ids = [int(i) for _, i in links]
    if ids: low = min(low, min(ids))
    for path, i in links:
        if re.search(r"play-?by-?play", path, re.I):
            found[path] = int(i)
    if page % 25 == 0:
        print(f"page {page} low id {low} pbp found {len(found)}", flush=True)
        json.dump(found, open(OUT, "w"), indent=1)
    page += 1
    time.sleep(0.6)
json.dump(found, open(OUT, "w"), indent=1)
print("done pages", page, "low", low, "pbp", len(found))
