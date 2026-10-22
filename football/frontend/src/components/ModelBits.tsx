// Small pieces shared by the every-model views (the match page's "Every model", the live
// leaderboard on Performance and the lab's backtest explorer): a model's role tag, a compact
// home/draw/away split bar with its percentages printed, a right/wrong mark and a 0–100% range
// bar for an interval. Colour never carries a value alone: every number is printed beside its
// mark, and the marks say "right" or "wrong" to screen readers.

import type { ReactNode } from "react";
import type { ModelRole } from "../api";
import { formatPct } from "../format";
import { pickOf, type OneXTwo } from "./ProbBar";
import "./models.css";

// ---------------------------------------------------------------------------------------------
// Roles

/** Display names for when a response's `roles` map lacks one (it never should). */
const ROLE_NAMES: Record<string, string> = {
  model_of_record: "Model of record",
  fallback_of_record: "Model of record (fallback)",
  challenger: "Challenger",
  machine_learning: "Machine learning",
  lab_best: "Best of a model-lab family",
  earlier_record: "Earlier model of record",
  baseline: "Baseline",
  other: "Other",
};

/** Plural headings for a list grouped by role. */
export const ROLE_GROUPS: Record<string, string> = {
  model_of_record: "Models of record",
  fallback_of_record: "Models of record",
  machine_learning: "Machine learning",
  challenger: "Challengers",
  lab_best: "Best of each model-lab family",
  earlier_record: "Earlier model of record",
  baseline: "Baselines",
  other: "Other models",
};

/** Display order: the model of record first, then machine-learning learners, challengers,
 * the lab's best, an earlier model of record and the baselines. */
const ROLE_RANK: Record<string, number> = {
  model_of_record: 0,
  fallback_of_record: 1,
  machine_learning: 2,
  challenger: 3,
  lab_best: 4,
  earlier_record: 5,
  baseline: 6,
  other: 7,
};

export function roleRank(role: ModelRole): number {
  return ROLE_RANK[role] ?? 8;
}

export function roleName(role: ModelRole, roles?: Record<string, string>): string {
  return roles?.[role] ?? ROLE_NAMES[role] ?? role.replace(/_/g, " ");
}

/** Shorter tag text for narrow rows. */
const ROLE_SHORT: Record<string, string> = {
  model_of_record: "Model of record",
  fallback_of_record: "Fallback of record",
  machine_learning: "Machine learning",
  challenger: "Challenger",
  lab_best: "Lab best",
  earlier_record: "Earlier record",
  baseline: "Baseline",
  other: "Other",
};

export function RoleTag({
  role,
  roles,
  short = false,
}: {
  role: ModelRole;
  roles?: Record<string, string>;
  short?: boolean;
}) {
  const full = roleName(role, roles);
  const text = short ? (ROLE_SHORT[role] ?? full) : full;
  const known = role in ROLE_RANK ? role : "other";
  return (
    <span className={`badge role-tag role-${known}`} title={text !== full ? full : undefined}>
      {text}
    </span>
  );
}

// ---------------------------------------------------------------------------------------------
// Right or wrong

export function Mark({ ok, label }: { ok: boolean; label?: string }) {
  return (
    <span className={`mark ${ok ? "mark-ok" : "mark-no"}`}>
      <span aria-hidden="true">{ok ? "✓" : "✗"}</span>
      <span className="visually-hidden">{label ?? (ok ? " right" : " wrong")}</span>
    </span>
  );
}

// ---------------------------------------------------------------------------------------------
// Home / draw / away

const OUTCOME_WORD = { H: "home win", D: "draw", A: "away win" } as const;

/** Decimal places for three percentages: one when two different values would print the same
 * whole number (a near tie, where the bold pick would otherwise look arbitrary), else none. */
export function pctDigits(values: number[]): 0 | 1 {
  for (let i = 0; i < values.length; i++) {
    for (let j = i + 1; j < values.length; j++) {
      const a = values[i] ?? 0;
      const b = values[j] ?? 0;
      if (a !== b && formatPct(a) === formatPct(b)) return 1;
    }
  }
  return 0;
}

/**
 * A compact match-result bar: one track split home (blue), draw (grey), away (orange), and the
 * three percentages printed under it (home at the left, draw in the middle, away at the right).
 * The pick (the most likely outcome) is printed in bold and, once the result is known, marked
 * right or wrong; a near tie prints one decimal place. One accessible name says it all.
 */
export function SplitBar({
  probs,
  correct,
  pick: givenPick,
  homeName,
  awayName,
  className,
}: {
  probs: OneXTwo;
  /** Whether the pick happened; undefined or null before the result. */
  correct?: boolean | null;
  /** The pick that `correct` was scored on (the record's or ledger's own), when known: on a
   * tie between the printed figures the most likely outcome alone can't say which it was. */
  pick?: "H" | "D" | "A" | null;
  homeName?: string;
  awayName?: string;
  className?: string;
}) {
  const pick = givenPick ?? pickOf(probs);
  const parts = [
    { code: "H" as const, cls: "home", p: probs.H },
    { code: "D" as const, cls: "draw", p: probs.D },
    { code: "A" as const, cls: "away", p: probs.A },
  ];
  const pickWord = pick === "H" ? `${homeName ?? "home"} win` : pick === "A" ? `${awayName ?? "away"} win` : "draw";
  const digits = pctDigits([probs.H, probs.D, probs.A]);
  const pct = (v: number) => formatPct(v, digits);
  const label =
    `${homeName ? `${homeName} win` : "Home win"} ${pct(probs.H)}, draw ${pct(probs.D)}, ` +
    `${awayName ? `${awayName} win` : "away win"} ${pct(probs.A)}. Pick: ${pickWord}` +
    (correct === true ? ", right." : correct === false ? ", wrong." : ".");
  return (
    <div className={`split${className ? ` ${className}` : ""}`} role="img" aria-label={label}>
      <span className="split-track" aria-hidden="true">
        {parts.map((x) => (
          <span
            key={x.code}
            className={`split-seg seg-${x.cls}${x.code === pick ? " is-pick" : ""}`}
            style={{ flexGrow: Math.max(x.p, 0.0001) }}
          />
        ))}
      </span>
      <span className="split-nums" aria-hidden="true">
        {parts.map((x) => (
          <span key={x.code} className={`split-num split-${x.cls}${x.code === pick ? " is-pick" : ""}`}>
            {pct(x.p)}
            {x.code === pick && correct !== undefined && correct !== null && (
              <span className={`mark ${correct ? "mark-ok" : "mark-no"}`}>{correct ? "✓" : "✗"}</span>
            )}
          </span>
        ))}
      </span>
    </div>
  );
}

/** "Home win", "Draw", "Away win" for a final score. */
export function resultOf(home: number, away: number): "H" | "D" | "A" {
  return home > away ? "H" : home < away ? "A" : "D";
}

/** The pick a scored forecast was marked on, from the outcome and whether the pick was right:
 * the outcome itself if right, else the likelier of the other two. Where rounding ties the
 * printed figures (a backtest cell's three decimals), the most likely of them can't tell. */
export function scoredPick(probs: OneXTwo, actual: "H" | "D" | "A", right: boolean): "H" | "D" | "A" {
  if (right) return actual;
  const [a, b] = (["H", "D", "A"] as const).filter((k) => k !== actual) as ["H" | "D" | "A", "H" | "D" | "A"];
  return probs[b] > probs[a] ? b : a;
}

export function outcomeWord(code: "H" | "D" | "A"): string {
  return OUTCOME_WORD[code];
}

/** The swatch key for split bars: "■ Home ■ Draw ■ Away", or with team names. */
export function SplitKey({
  home = "Home",
  away = "Away",
  children,
}: {
  home?: string;
  away?: string;
  children?: ReactNode;
}) {
  return (
    <p className="split-key small">
      <span>
        <span className="swatch swatch-home" aria-hidden="true" /> {home}
      </span>
      <span>
        <span className="swatch swatch-draw" aria-hidden="true" /> Draw
      </span>
      <span>
        <span className="swatch swatch-away" aria-hidden="true" /> {away}
      </span>
      {children}
    </p>
  );
}

// ---------------------------------------------------------------------------------------------
// Intervals

/** A 95% Wilson interval for k right out of n (footy.modeling.hit_rates.wilson); null for n = 0. */
export function wilson(k: number, n: number): [number, number] | null {
  if (n <= 0) return null;
  const z = 1.959963984540054;
  const z2 = z * z;
  const p = k / n;
  const denom = 1 + z2 / n;
  const centre = (p + z2 / (2 * n)) / denom;
  const half = (z * Math.sqrt((p * (1 - p)) / n + z2 / (4 * n * n))) / denom;
  return [Math.max(0, centre - half), Math.min(1, centre + half)];
}

/** A small 0–100% bar: the interval as a band, the estimate as a dot. Decorative: the caller
 * prints the values. */
export function RangeBar({ value, ci }: { value: number | null; ci: [number, number] | null }) {
  const pct = (v: number) => `${Math.max(0, Math.min(1, v)) * 100}%`;
  return (
    <span className="rangebar" aria-hidden="true">
      {ci && (
        <span className="rangebar-band" style={{ left: pct(ci[0]), width: `calc(${pct(ci[1])} - ${pct(ci[0])})` }} />
      )}
      {value !== null && Number.isFinite(value) && <span className="rangebar-dot" style={{ left: pct(value) }} />}
    </span>
  );
}

/** "+0.0123", "−0.0985", "0.0000" (no sign when it rounds to zero). */
export function signedDiff(v: number, digits = 4): string {
  const s = Math.abs(v).toFixed(digits);
  if (Number(s) === 0) return (0).toFixed(digits);
  return v > 0 ? `+${s}` : `−${s}`;
}
