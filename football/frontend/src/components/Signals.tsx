// Three separate things, never blended into one score:
// - confidence: how likely the forecast thinks its most likely outcome is (the model's own on a
//   model-only record; bookmakers' consensus blended with the model on a model + market one);
// - data completeness: which inputs the prediction had and lacked;
// - betting edge: whether a price beat the model's probability (needs odds).

import type { JsonValue, PredictionCard } from "../api";
import { formatPct, outcomeLabel } from "../format";
import { href } from "../router";
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

/** Whose probabilities the confidence is: on a model + market record they are the bookmakers'
 * consensus blended with the model, not the model's alone. */
export function confidenceSource(c: Pick<PredictionCard, "basis">): string {
  return c.basis === "model_and_market" ? "bookmakers + model" : "model";
}

/** The same fixture's model + market record, when this record is model-only. */
export type MarketRecordRef = { id: number; model_name: string };

export function Signals({
  card,
  features,
  variant = "compact",
  marketRecord = null,
}: {
  card: PredictionCard;
  /** The detail record's feature snapshot, for the full variant. */
  features?: Record<string, JsonValue>;
  variant?: "compact" | "full";
  /** On a model-only record: the fixture's model + market record, if it has one. */
  marketRecord?: MarketRecordRef | null;
}) {
  const conf = confidenceOf(card);
  const missing = missingInputs(card);
  const odds = hasOdds(card);
  const vs = card.value_selection;
  const homeN = num(features?.home_matches_in_window);
  const awayN = num(features?.away_matches_in_window);
  const full = variant === "full";
  const blended = card.basis === "model_and_market";
  // Prices exist for this match, but in its model + market record rather than this one.
  const pricesElsewhere = !vs && !odds && !blended && marketRecord !== null;

  // Each signal: a label, then one headline in the same style for all three, then (on the
  // match page) a note.
  return (
    <dl className={`signals signals-${variant}`}>
      <div className="signal">
        <dt>
          Confidence
          {/* On the match page the source is the label's second line; on a card it sits under
              the value, so the three values start on one line. */}
          {full && <span className="signal-sub">({confidenceSource(card)})</span>}
        </dt>
        <dd>
          <span className="signal-value">
            {conf ? (
              <>
                <strong>{formatPct(conf.p)}</strong> {conf.label}
              </>
            ) : (
              <span className="muted">No match-result probabilities</span>
            )}
          </span>
          {!full && <span className="signal-sub">({confidenceSource(card)})</span>}
          {full && (
            <p className="signal-note">
              {blended
                ? "The probability of the single most likely result, from the bookmakers' consensus blended with the model. It says how sure this forecast is, not whether a bet is worth taking."
                : "The probability of the single most likely result. It says how sure the model is, not whether a bet is worth taking."}
            </p>
          )}
        </dd>
      </div>
      <div className="signal">
        <dt>{full ? "Data completeness" : "Data"}</dt>
        <dd>
          <span className="signal-value">
            {missing.length === 0 ? (
              <strong>All inputs present</strong>
            ) : (
              <>
                Without <strong>{listJoin(missing.map(fieldName))}</strong>
              </>
            )}
          </span>
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
          <span className="signal-value">
            {vs ? (
              // Real prices were used, but no selection rule is published: nothing is named.
              <strong>No selection published</strong>
            ) : odds ? (
              // The API has no betting-selection field for this record, so nothing can qualify.
              <strong>No qualifying bet</strong>
            ) : pricesElsewhere && marketRecord ? (
              // The match has prices, used by its model + market record: point there, never
              // "no odds available".
              <>
                <strong>
                  See the{" "}
                  {full ? (
                    <a href={href(`/match/${marketRecord.id}`)}>model + market record</a>
                  ) : (
                    "model + market record"
                  )}
                </strong>{" "}
                — this one uses no prices
              </>
            ) : (
              <>
                <strong>No odds available</strong> — no betting selection
              </>
            )}
          </span>
          {full && (
            <p className="signal-note">
              {vs
                ? vs.note
                : odds
                  ? "Betting selections aren't published for this record, so none qualifies."
                  : pricesElsewhere && marketRecord
                    ? `Bookmakers' prices for this match went into the model + market record (${marketRecord.model_name}), not into this one, so any betting edge is shown there. The probabilities here are the model's alone.`
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
