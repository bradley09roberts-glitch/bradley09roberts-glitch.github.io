import type { ReactNode } from "react";
import type { Basis, Edition, RecordType } from "../api";

export type Tone = "neutral" | "info" | "good" | "warn" | "bad" | "market";

export function Badge({
  tone = "neutral",
  title,
  children,
}: {
  tone?: Tone;
  title?: string;
  children: ReactNode;
}) {
  return (
    <span className={`badge badge-${tone}`} title={title}>
      {children}
    </span>
  );
}

const RECORD_TYPES: Record<RecordType, { label: string; tone: Tone; title: string }> = {
  genuine_prematch: {
    label: "Genuine pre-match",
    tone: "good",
    title: "Saved before kickoff and never changed",
  },
  retrospective_backtest: {
    label: "Retrospective",
    tone: "warn",
    title: "Made after the event by replaying history; not a pre-match record",
  },
  exploratory: {
    label: "Exploratory",
    tone: "neutral",
    title: "A research run; not part of any record",
  },
  unavailable: {
    label: "Unavailable",
    tone: "bad",
    title: "This edition could not be produced",
  },
};

export function recordTypeLabel(t: RecordType | string): string {
  return RECORD_TYPES[t as RecordType]?.label ?? t;
}

export function RecordTypeBadge({ type }: { type: RecordType | string }) {
  const info = RECORD_TYPES[type as RecordType];
  return (
    <Badge tone={info?.tone ?? "neutral"} title={info?.title}>
      {info?.label ?? type}
    </Badge>
  );
}

const EDITIONS: Record<string, { label: string; title: string }> = {
  early: { label: "Early edition", title: "Made without lineups or injury news" },
  lineup: { label: "Lineup edition", title: "Made after the lineups were announced" },
};

export function editionLabel(e: Edition): string {
  return EDITIONS[e]?.label ?? `${e} edition`;
}

export function EditionBadge({ edition }: { edition: Edition }) {
  const info = EDITIONS[edition];
  return (
    <Badge tone="info" title={info?.title}>
      {info?.label ?? `${edition} edition`}
    </Badge>
  );
}

const BASES: Record<Basis, { label: string; tone: Tone; title: string }> = {
  model_and_market: {
    label: "Model + market",
    tone: "market",
    title:
      "Bookmakers' prices, collected before the prediction was saved, were used: where they were given all the weight, the figures are the bookmakers' consensus",
  },
  model_only: {
    label: "Model only",
    tone: "neutral",
    title: "Made from past results alone, without bookmakers' prices",
  },
};

export function basisLabel(b: Basis | string): string {
  return BASES[b as Basis]?.label ?? b;
}

/** A model + market record whose match-result figures are the bookmakers' consensus itself
 * (the prices given all the weight): nothing of the model is in them. */
const CONSENSUS = {
  label: "Bookmakers' consensus",
  tone: "market" as Tone,
  title:
    "The match result and over/under 2.5 goals are the bookmakers' prices with their margins removed; the model only shapes the other goal lines, both teams to score and the scores",
};

/** True when a model + market record's match-result figures are the bookmakers' consensus
 * (the API's `consensus_markets`). */
export function isConsensus(c: { basis: Basis; consensus_markets?: string[] }): boolean {
  return c.basis === "model_and_market" && (c.consensus_markets ?? []).includes("1X2");
}

/** "Model + market" or "Model only": whether the record used bookmakers' prices; "Bookmakers'
 * consensus" when `consensus` is set (a model + market record whose match-result figures are
 * the prices with the margins removed, `isConsensus`). */
export function BasisBadge({ basis, consensus = false }: { basis: Basis; consensus?: boolean }) {
  const info = consensus && basis === "model_and_market" ? CONSENSUS : (BASES[basis] ?? BASES.model_only);
  return (
    <Badge tone={info.tone} title={info.title}>
      {info.label}
    </Badge>
  );
}
