"""Build the report page's store documents (dataset records and the page file) from the current data and page.

Usage: make_docs.py <data_dir> <index.html> <blobs.json> <out_dir>
  blobs.json  {"<dataset id>": "<asset id>", ...}: the asset id each CSV got when uploaded to the dashboard
Writes out_dir/<id>.json for every dataset, out_dir/index.json and out_dir/writes.json (the batch of store writes).
Descriptions name no thresholds; the page draws those from the 'method' dataset.
"""
import datetime, json, os, sys
DATA, PAGE, BLOBS, OUT = sys.argv[1:5]
os.makedirs(OUT, exist_ok=True)
now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
edit = {"via": "claude", "at": now}; updated = {"at": now, "by": "Claude (fight ML pipeline run)"}
D = {
    "results": ("Results by model and period", "Share of winners picked and log loss for every model on the ledger's graded bouts, per period, on all bouts and on the bouts with a closing price."),
    "gate": ("Gate against the close", "Each model's log loss against the de-vigged closing line on the same priced bouts, with a bootstrap interval."),
    "curve": ("Rolling accuracy", "Each learner's share of winners picked over a rolling window of earlier UFC fights, every UFC fight since 1994."),
    "yearly": ("Accuracy by year", "Each learner's share of winners picked and log loss per calendar year, plus the RL agent's own exploring picks."),
    "rl_reward": ("RL agent's running reward", "Cumulative reward of the RL agent (+1 per right pick, -1 per wrong one), averaged over its runs, by date."),
    "rl_summary": ("RL agent summary", "The RL agent's own hit rates, total reward, fights rewarded and share of near-certain chances."),
    "calibration": ("Calibration by chance band", "The favourite's stated chance against how often it won, by band, on the ledger's 2019-26 bouts with a closing price."),
    "adds": ("Adds to v4 or the close?", "Each learner combined with v4 (against v4 alone) or with the close (against the close recalibrated the same way), weights fitted on earlier bouts only."),
    "divisions": ("Results by division", "Learners and v4 against the close by division, priced 2019-26 bouts, divisions with at least a minimum number of bouts."),
    "ablation": ("Without each data source", "Learners re-trained and re-tuned without each data source: 2014-18 validation and every UFC fight from 2019."),
    "fill_in": ("Hindsight-leak check", "Fights where only one fighter's physicals were filled in: how often that fighter won, and what the ML blend said."),
    "checklist": ("Leakage checklist", "The model gate's leakage checklist and the audits' fixes, with how each was checked."),
    "improvements": ("Improvements tried", "Changes tried to the model, each judged by ML blend log loss on 2014-18. Every score comes from a run with 2019+ results hidden, except the first row's Without (the leaky pre-fix score, still 2014-18 only)."),
    "method": ("Method settings", "The settings the evaluation used: rolling window, RL runs, near-certain cut-offs, interval, bootstrap draws, smallest division shown and the margin used to de-vig the close."),
    "predictions": ("Every prediction", "Every learner's chance for the first-listed fighter in all UFC fights since 1994, with the result (blank for draws and no contests)."),
}
B = json.load(open(BLOBS))
writes = []
for k, (title, desc) in D.items():
    assert os.path.exists(f"{DATA}/{k}.csv"), k
    doc = {"title": title, "description": desc, "source": {"kind": "file", "url": f"/_blob/{B[k]}", "name": f"{k}.csv"},
           "updated": updated, "fixed": True, "edit": edit}
    json.dump(doc, open(f"{OUT}/{k}.json", "w"))
    writes.append({"op": "set", "collection": "datasets", "doc_id": k, "file_path": os.path.abspath(f"{OUT}/{k}.json")})
json.dump({"text": open(PAGE).read(), "edit": edit}, open(f"{OUT}/index.json", "w"))
writes.append({"op": "set", "collection": "files", "doc_id": "index.html", "file_path": os.path.abspath(f"{OUT}/index.json")})
json.dump(writes, open(f"{OUT}/writes.json", "w"), indent=0)
print(len(writes), "documents", now)
