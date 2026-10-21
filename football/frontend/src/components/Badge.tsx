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
    title: "Bookmakers' prices, collected before the prediction was saved, were blended in",
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

/** "Model + market" or "Model only": whether the record used bookmakers' prices. */
export function BasisBadge({ basis }: { basis: Basis }) {
  const info = BASES[basis] ?? BASES.model_only;
  return (
    <Badge tone={info.tone} title={info.title}>
      {info.label}
    </Badge>
  );
}
