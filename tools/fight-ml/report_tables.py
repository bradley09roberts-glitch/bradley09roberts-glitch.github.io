"""The report's two hand-assembled tables: improvements tried (from the firewalled development runs) and the leakage checklist.

Usage: report_tables.py <final_run_dir> <out_dir> <leaky_learn_json> <dev_base> <dev> <dev_s1>
  leaky_learn_json  learn_all.json from the run before the audit fix (cov_phys still in)
  dev_base          FIREWALL=1 run of the original features with the fix, learners old (base_old) and new (base_new)
  dev               FIREWALL=1 run of the final features: final_old, final_new, norat_new (no ratings group)
  dev_s1            FIREWALL=1 run of the final features with SHRINK=1: s1_new
"""
import csv, json, sys
RUN, OUT, LEAKY, DB, DV, DS = sys.argv[1:7]
v = lambda d, t: json.load(open(f"{d}/learn_{t}.json"))["res"]["blend_val"]["ll"]
imp = [
    ("Removed the hindsight flag for physical data on file (audit fix; it had flattered 2014–18)", json.load(open(LEAKY))["res"]["blend_val"]["ll"], v(DB, "base_old"), "Fixed (required)"),
    ("Debutants start from average stats instead of blanks, with 4× stronger shrinkage", v(DB, "base_new"), v(DV, "norat_new"), "Yes"),
    ("Glicko and margin-of-victory ratings", v(DV, "norat_new"), v(DV, "final_new"), "Yes"),
    ("Stronger shrinkage on its own (4× against 1×)", v(DS, "s1_new"), v(DV, "final_new"), "Yes (no real difference)"),
    ("Learner changes: yearly logistic, decaying-step online learner, three-way blend", v(DV, "final_old"), v(DV, "final_new"), "Yes"),
    ("Other ratings: Glicko-2, TrueSkill, rating trend and peak, opponent-adjusted striking and grappling", "", "", "No (within noise)"),
    ("New stats: opponent-adjusted rates, win quality, activity, age decline, ape index, weight-class moves", "", "", "No (within noise)"),
    ("Wider boosting grid, more frequent refits, stacking, temperature calibration", "", "", "No (within noise or worse)"),
]
with open(f"{OUT}/improvements.csv", "w", newline="") as fh:
    w = csv.writer(fh); w.writerow(["change", "val_ll_without", "val_ll_with", "kept"]); [w.writerow(r) for r in imp]
F = json.load(open(f"{RUN}/eval.json"))["fill_in_check"]
chk = [
    ("1. Chronological validation only; no tuning on the test period", "Pass",
     "Every setting (learning rates, trees, ratings, shrinkage, which improvements to keep) was chosen on 2014–18, in copies where every 2019+ result was hidden from the code. 2019–26 was scored once, at the end."),
    ("2. Every prediction uses only data from before the fight", "Pass",
     "Each card's rows are written before its results update any rating or stat. An independent audit rebuilt the features with every later fight deleted and that day's results reversed: all rows came out identical. The stats round scorer was fitted on 2016–18 Sherdog rounds only."),
    ("3. Closing odds never a feature", "Pass",
     "No model sees any odds. The odds Sherdog prints are never read. The one test that combines a learner with the close is labelled a diagnostic and benchmarked against the close alone."),
    ("4. Any odds input is a labelled market prior, with a model-only version", "Pass (none used)",
     "There is no market prior; every model shown is model-only."),
    ("5. Every prediction reported", "Pass", "All 8,974 UFC fights since 1994, every learner's chance, are in the predictions file."),
    ("6. Missing values blank, never zero", "Pass (after a fix)",
     f"Stats with no data stay blank. The audit found that blank reach, height, stance or birth date on UFCStats marks fighters who were later cut (in 2014–18 the fighter with the blank won {F['2014_18']['filled_in_fighter_won']:.0%}), so the flag for it was removed and blanks are filled with typical values the models can't tell apart (see the check below)."),
    ("Close benchmark de-vig", "Fixed",
     "The ledger stores a proportional de-vig. The gate uses Shin, which for a two-way market is additive; rebuilt with the ledger's 4.57% margin, it reproduces the ledger's own gate (close log loss 0.5926 on its 1,111 locked bouts)."),
    ("Like-for-like comparisons", "Fixed", "The close prices only some bouts, so every comparison with it uses the same priced bouts."),
    ("Stale results", "Fixed", "One driver runs features, learners and evaluation in order; the evaluation refuses predictions made from a different features file."),
    ("Boosting fits", "Fixed", "Early stopping is off, so the tuned settings are what run in every year (it would otherwise hold out rows whose mirror images stay in training)."),
]
with open(f"{OUT}/checklist.csv", "w", newline="") as fh:
    w = csv.writer(fh); w.writerow(["item", "result", "detail"]); [w.writerow(r) for r in chk]
