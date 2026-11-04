"""Load every event's story file into one {bout id: story} dict."""
import glob, json
def load_stories(S):
    out = {}
    for fn in sorted(glob.glob(f"{S}/stories/*.json")):
        try: d = json.load(open(fn))
        except Exception: continue
        for bid, st in (d.get("bouts") or {}).items():
            if isinstance(st, dict): out[bid] = st
    return out
