"""The report's two hand-assembled tables: improvements tried (from the firewalled development runs) and the leakage checklist.

Usage: report_tables.py <final_run_dir> <out_dir> <leaky_learn_json> <dev_base> <dev> <dev_s1>
  leaky_learn_json  learn_all.json from the run before the audit fix (cov_phys still in)
  dev_base, dev, dev_s1   the folders devtools/dev_runs.sh writes (original features; final features; SHRINK=1)
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
    ("1. Chronological validation only; no tuning on the test period", "Pass, with a caveat",
     "Every setting (learning rates, trees, ratings, shrinkage, which improvements to keep) was chosen on 2014–18, in copies where every 2019+ result was hidden from the code. Caveat: an earlier version, before the leakage audit, had already been scored on 2019–26 several times, including a with-and-without-Sherdog test. No choice since used those scores, and the final model was then scored on 2019–26."),
    ("2. Every prediction uses only data from before the fight", "Pass",
     "Each card's rows are written before its results update any rating or stat, and the values used to fill missing physicals are running averages of fighters already seen. Independent audits rebuilt the features with every later fight deleted and that day's results reversed: all earlier rows came out identical. The stats round scorer was fitted on 2016–18 Sherdog rounds only."),
    ("3. Closing odds never a feature", "Pass",
     "No model sees any odds. The odds Sherdog prints are never read. The one test that combines a learner with the close is labelled a diagnostic and benchmarked against the close alone."),
    ("4. Any odds input is a labelled market prior, with a model-only version", "Pass (none used)",
     "There is no market prior; every model shown is model-only."),
    ("5. Every prediction reported", "Pass", "All 8,974 UFC fights since 1994 are in the predictions file, with every chance each learner gave. The yearly logistic and boosted trees start in 2006 (blank before), when they first have 500 earlier fights to fit on."),
    ("6. Missing values blank, never zero", "Pass (after a fix)",
     f"A debutant's UFCStats rates start at the population averages (never zero); layoff, Sherdog and story features stay blank when there is no data. The audit found that blank reach, height, stance or birth date on UFCStats marks fighters who were later cut (in 2014–18 the fighter with the blank won {F['2014_18']['filled_in_fighter_won']:.0%}), so the flag for it was removed and blanks are filled with running typical values the models can't tell apart (see the check below)."),
    ("Close benchmark de-vig", "Fixed",
     "The ledger stores a proportional de-vig. The gate uses Shin, which for a two-way market is additive; rebuilt with the ledger's 4.57% margin, it reproduces the ledger's own gate (close log loss 0.5926 on its 1,111 locked bouts)."),
    ("Like-for-like comparisons", "Fixed", "The close prices only some bouts, so every comparison with it, the calibration chart included, uses the same priced bouts."),
    ("One fighter, two spellings", "Fixed", "Some fighters are spelled differently in the fight files and the fighter file (Jr./III, hyphens, nicknames, typos). A short alias list and a suffix-blind match now join them, so their records don't split and their real physicals are used; fighters with no row in the fighter file at all are filled in."),
    ("Fighters who share a name", "Fixed", "Two UFCStats fighters share each of 9 names. Each bout now goes to the namesake whose listed weight is nearest the bout's division, so a fighter's record no longer splits when they fight for a title or move one division, and they keep their own physicals."),
    ("Fights without round stats", "Fixed", "Some 1990s fights have a time but no round stats; they no longer add minutes with zero strikes to the per-minute rates."),
    ("One RL agent per name", "Fixed", "'RL agent' is the exploring agent rewarded +1 / -1 (its chances averaged over its runs); every RL figure on the page is that agent's. The agent trained without exploration is listed separately."),
    ("Stale results", "Fixed", "One driver runs features, learners and evaluation in order; the evaluation refuses predictions made from a different features file."),
    ("Boosting fits", "Fixed", "Early stopping is off, so the tuned settings are what run in every year (it would otherwise hold out rows whose mirror images stay in training)."),
]
with open(f"{OUT}/checklist.csv", "w", newline="") as fh:
    w = csv.writer(fh); w.writerow(["item", "result", "detail"]); [w.writerow(r) for r in chk]
