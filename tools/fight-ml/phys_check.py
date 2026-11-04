import csv, json, re, sys
from collections import defaultdict
S = sys.argv[1]
U = json.load(open(f"{S}/work/ufc_fights.json"))
n = defaultdict(int); first = {}
for fid, f in U.items():
    for nm in f["names"]:
        n[nm] += 1
        if f.get("d"): first[nm] = min(first.get(nm, "9999"), f["d"])
tott = {r["FIGHTER"].strip(): r for r in csv.DictReader(open(f"{S}/raw/ufc_fighter_tott.csv", encoding="utf8"))}
for era in (("1993", "2005"), ("2005", "2014"), ("2014", "2019"), ("2019", "2027")):
    print("debut", era)
    for b, lab in ((lambda k: k == 1, "1"), (lambda k: k == 2, "2"), (lambda k: k == 3, "3"), (lambda k: k >= 4, "4+")):
        ns = [nm for nm in n if era[0] <= first.get(nm, "0") < era[1] and b(n[nm]) and nm in tott]
        if not ns: continue
        miss = lambda col, pat: sum(1 for nm in ns if not re.search(pat, tott[nm][col] or "")) / len(ns)
        print(f"   fights {lab:3s} n={len(ns):5d} noDOB {miss('DOB', r'\d{4}'):.2f} noHeight {miss('HEIGHT', r'\d'):.2f} noReach {miss('REACH', r'\d'):.2f} noStance {miss('STANCE', r'\w'):.2f}")
