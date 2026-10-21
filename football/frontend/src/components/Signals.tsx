// Three separate things, never blended into one score:
// - model confidence: how likely the model thinks its most likely outcome is;
// - data completeness: which inputs the prediction had and lacked;
// - betting edge: whether a price beat the model's probability (needs odds).

import type { JsonValue, PredictionCard } from "../api";
import { formatPct, outcomeLabel } from "../format";
import { pickOf } from "./ProbBar";

const FIELD_NAMES: Record<string, string> = {
  lineups: "lineups",
  injuries: "injuries",
  odds: "odds",
  suspensions: "suspensions",
  xg: "expected-goals data",
};

export function fieldName(f: string): string {
  return FIELD_NAMES[f] ?? f.replace(/_/g, " ");
}

function listJoin(items: string[]): string {
  if (items.length <= 1) return items.join("");
  return `${items.slice(0, -1).join(", ")} and ${items.at(-1)}`;
}

export function confidenceOf(c: PredictionCard): { label: string; p: number } | null {
  const x = c.probabilities["1X2"];
  if (!x) return null;
  const k = pickOf(x);
  const name = k === "H" ? `${c.home_name} win` : k === "A" ? `${c.away_name} win` : "Draw";
  return { label: name, p: x[k] };
}

/** Model inputs that were missing (odds are not a model input; they belong to the edge). */
export function missingInputs(c: PredictionCard): string[] {
  return c.missing_fields.filter((f) => f !== "odds");
}

export function hasOdds(c: PredictionCard): boolean {
  return !c.missing_fields.includes("odds");
}

function num(v: JsonValue | undefined): number | null {
  return typeof v === "number" && Number.isFinite(v) ? v : null;
}

export function Signals({
  card,
  features,
  variant = "compact",
}: {
  card: PredictionCard;
  /** The detail record's feature snapshot, for the full variant. */
  features?: Record<string, JsonValue>;
  variant?: "compact" | "full";
}) {
  const conf = confidenceOf(card);
  const missing = missingInputs(card);
  const odds = hasOdds(card);
  const homeN = num(features?.home_matches_in_window);
  const awayN = num(features?.away_matches_in_window);
  const full = variant === "full";

  return (
    <dl className={`signals signals-${variant}`}>
      <div className="signal">
        <dt>Model confidence</dt>
        <dd>
          {conf ? (
            <>
              <strong>{formatPct(conf.p)}</strong> <span>{conf.label}</span>
            </>
          ) : (
            <span className="muted">No match-result probabilities</span>
          )}
          {full && (
            <p className="signal-note">
              The probability of the single most likely result. It says how sure the model is,
              not whether a bet is worth taking.
            </p>
          )}
        </dd>
      </div>
      <div className="signal">
        <dt>Data completeness</dt>
        <dd>
          {missing.length === 0 ? (
            <strong>All inputs present</strong>
          ) : (
            <span>
              Without <strong>{listJoin(missing.map(fieldName))}</strong>
            </span>
          )}
          {full && homeN !== null && awayN !== null && (
            <p className="signal-note">
              Team ratings use {homeN} recent matches for {card.home_name} and {awayN} for{" "}
              {card.away_name}.
            </p>
          )}
        </dd>
      </div>
      <div className="signal">
        <dt>Betting edge</dt>
        <dd>
          {odds ? (
            // The API has no betting-selection field yet, so nothing can qualify.
            <strong>No qualifying bet</strong>
          ) : (
            <span>
              <strong>No odds available</strong> — no betting selection
            </span>
          )}
          {full && (
            <p className="signal-note">
              {odds
                ? "Betting selections aren't published for this record, so none qualifies."
                : "An edge needs a bookmaker price to compare with. The probabilities still stand on their own."}
            </p>
          )}
        </dd>
      </div>
    </dl>
  );
}

/** The ordinary pick per market, e.g. "Over 2.5 (61%)". Not a betting selection. */
export function pickText(market: string, pick: { pick: string; p: number }): string {
  return `${outcomeLabel(market, pick.pick)} (${formatPct(pick.p)})`;
}
