import { z } from "zod";
import type { GameMode } from "../rules/gameRules.js";
import { applyEvent, createMatchState } from "./matchStore.js";
import type { AbilityState, DeathRecap, FieldKey, MatchEvent, MatchState, Phase, SourceKind, Standing, ThreatKind } from "./types.js";

/**
 * State adapters translate an input channel into MatchEvents. Every adapter declares whether it
 * is verified against the real game. Manual, scenario and replay are verified; the screen reader
 * ships as experimental (verified on synthetic scoreboards only); no live game-state interface
 * exists (see docs/CAPABILITIES.md).
 */
export interface AdapterInfo {
  id: SourceKind;
  label: string;
  verified: boolean;
  note: string;
}

export const ADAPTERS: AdapterInfo[] = [
  { id: "manual", label: "Manual", verified: true, note: "Your own inputs. Always available." },
  { id: "scenario", label: "Scenario playback", verified: true, note: "Timestamped fixtures for demos and testing." },
  { id: "replay", label: "Post-match replay", verified: true, note: "Imported decision logs or replay files. Kept separate from live state." },
  { id: "live", label: "Live game interface", verified: false, note: "Not available: no documented Deadlock game-state interface was found." },
  {
    id: "screen",
    label: "Screen reader (experimental)",
    verified: false,
    note: "User-triggered capture of a screen you can see (e.g. the Tab scoreboard), read locally after a one-time calibration. Tested on synthetic scoreboards only; uncertain icons always need your confirmation.",
  },
];

// ---------------------------------------------------------------------------------------------
// Manual adapter: small helpers that produce correctly-labelled events.
// ---------------------------------------------------------------------------------------------

export class ManualInput {
  constructor(private readonly clock: () => number, private readonly gameTime: () => number | null) {}

  private ev(field: FieldKey, value: unknown, correction = false): MatchEvent {
    return { type: "observe", field, value, source: "manual", observedAt: this.clock(), gameTime: this.gameTime(), confidence: "high", userCorrection: correction };
  }

  startMatch(matchId: string, mode: GameMode = "standard"): MatchEvent {
    return { type: "match.start", matchId, mode, at: this.clock() };
  }
  hero(heroId: number): MatchEvent {
    return this.ev("me.hero", heroId);
  }
  souls(n: number): MatchEvent {
    return this.ev("me.souls", Math.max(0, Math.round(n)));
  }
  items(classNames: string[], correction = false): MatchEvent {
    return this.ev("me.items", [...new Set(classNames)], correction);
  }
  abilities(st: AbilityState): MatchEvent {
    return this.ev("me.abilities", st);
  }
  unspentPoints(n: number): MatchEvent {
    return this.ev("me.unspentPoints", Math.max(0, Math.round(n)));
  }
  unspentUnlocks(n: number): MatchEvent {
    return this.ev("me.unspentUnlocks", Math.max(0, Math.round(n)));
  }
  enemies(ids: number[]): MatchEvent {
    return this.ev("roster.enemies", ids);
  }
  allies(ids: number[]): MatchEvent {
    return this.ev("roster.allies", ids);
  }
  lane(ids: number[]): MatchEvent {
    return this.ev("roster.lane", ids);
  }
  enemyItems(heroId: number, items: string[], complete: boolean): MatchEvent {
    return this.ev(`enemyItems:${heroId}`, { items, complete });
  }
  allyItems(heroId: number, items: string[], complete: boolean): MatchEvent {
    return this.ev(`allyItems:${heroId}`, { items, complete });
  }
  clock_(seconds: number): MatchEvent {
    return { type: "observe", field: "match.gameTime", value: Math.max(0, Math.round(seconds)), source: "manual", observedAt: this.clock(), gameTime: Math.max(0, Math.round(seconds)), confidence: "high" };
  }
  phase(p: Phase): MatchEvent {
    return this.ev("match.phase", p);
  }
  standing(s: Standing): MatchEvent {
    return this.ev("match.standing", s);
  }
  deathRecap(r: DeathRecap): MatchEvent {
    return this.ev("me.deathRecap", r);
  }
  threat(kind: ThreatKind, severity: "minor" | "major", sourceHeroId: number | null, note = ""): MatchEvent {
    return { type: "threat.report", report: { kind, severity, sourceHeroId, note, source: "manual", observedAt: this.clock(), gameTime: this.gameTime() } };
  }
}

// ---------------------------------------------------------------------------------------------
// Scenario fixtures
// ---------------------------------------------------------------------------------------------

const observeSchema = z.object({
  type: z.literal("observe"),
  field: z.string(),
  value: z.unknown(),
  source: z.enum(["manual", "live", "screen", "scenario", "replay"]).optional(),
  gameTime: z.number().nullable().optional(),
  confidence: z.enum(["high", "medium", "low"]).optional(),
  userCorrection: z.boolean().optional(),
});

const scenarioEvent = z.object({
  /** Offset in ms from scenario start. */
  t: z.number().nonnegative(),
  event: z.union([
    observeSchema,
    z.object({ type: z.literal("match.start"), matchId: z.string(), mode: z.enum(["standard", "ranked", "street_brawl"]) }),
    z.object({ type: z.literal("match.end") }),
    z.object({ type: z.literal("match.pause") }),
    z.object({ type: z.literal("match.resume") }),
    z.object({
      type: z.literal("threat.report"),
      kind: z.string(),
      severity: z.enum(["minor", "major"]),
      sourceHeroId: z.number().nullable(),
      note: z.string().default(""),
      gameTime: z.number().nullable().optional(),
    }),
    z.object({
      type: z.literal("observe.ambiguous"),
      id: z.string(),
      field: z.string(),
      candidates: z.array(z.object({ value: z.unknown(), label: z.string(), confidence: z.enum(["high", "medium", "low"]) })),
      gameTime: z.number().nullable().optional(),
    }),
  ]),
});

export const scenarioSchema = z.object({
  id: z.string().min(1),
  title: z.string(),
  description: z.string(),
  build: z.number().int().nullable(),
  /** What the scenario is meant to demonstrate (human-readable expectations). */
  expectations: z.array(z.string()).default([]),
  events: z.array(scenarioEvent).min(1),
});

export type ScenarioFixture = z.infer<typeof scenarioSchema>;

export function parseScenario(json: unknown): { ok: true; scenario: ScenarioFixture } | { ok: false; errors: string[] } {
  const r = scenarioSchema.safeParse(json);
  if (r.success) return { ok: true, scenario: r.data };
  return { ok: false, errors: r.error.issues.slice(0, 20).map((i) => `${i.path.join(".")}: ${i.message}`) };
}

/** Convert fixture entries to MatchEvents at a base wall-clock time. Source defaults to 'scenario'. */
export function scenarioEvents(s: ScenarioFixture, base: number, source: SourceKind = "scenario"): MatchEvent[] {
  return s.events.map(({ t, event }) => {
    const at = base + t;
    switch (event.type) {
      case "observe":
        return {
          type: "observe",
          field: event.field as FieldKey,
          value: event.value,
          source: event.source ?? source,
          observedAt: at,
          gameTime: event.gameTime ?? null,
          ...(event.confidence ? { confidence: event.confidence } : {}),
          ...(event.userCorrection ? { userCorrection: true } : {}),
        };
      case "match.start":
        return { type: "match.start", matchId: event.matchId, mode: event.mode, at };
      case "match.end":
      case "match.pause":
      case "match.resume":
        return { type: event.type, at };
      case "threat.report":
        return {
          type: "threat.report",
          report: { kind: event.kind as ThreatKind, severity: event.severity, sourceHeroId: event.sourceHeroId, note: event.note, source, observedAt: at, gameTime: event.gameTime ?? null },
        };
      case "observe.ambiguous":
        return { type: "observe.ambiguous", id: event.id, field: event.field as FieldKey, candidates: event.candidates, source, observedAt: at, gameTime: event.gameTime ?? null };
    }
  });
}

/** Step-wise scenario player (for the app's demo mode). */
export class ScenarioPlayer {
  private idx = 0;
  readonly events: MatchEvent[];
  constructor(readonly scenario: ScenarioFixture, readonly base: number) {
    this.events = scenarioEvents(scenario, base);
  }
  get done(): boolean {
    return this.idx >= this.events.length;
  }
  get position(): number {
    return this.idx;
  }
  /** Apply events up to (and including) wall time `until`. */
  advanceTo(state: MatchState, until: number): MatchState {
    let s = state;
    while (this.idx < this.events.length) {
      const e = this.events[this.idx]!;
      const at = "observedAt" in e ? e.observedAt : "at" in e ? e.at : e.type === "threat.report" ? e.report.observedAt : this.base;
      if (at > until) break;
      s = applyEvent(s, e);
      this.idx++;
    }
    return s;
  }
  step(state: MatchState): MatchState {
    if (this.done) return state;
    return applyEvent(state, this.events[this.idx++]!);
  }
}

// ---------------------------------------------------------------------------------------------
// Decision log (opt-in, local) and replay adapter
// ---------------------------------------------------------------------------------------------

export interface DecisionLogEntry {
  at: number;
  gameTime: number | null;
  heroId: number | null;
  souls: number | null;
  owned: string[];
  enemies: number[];
  topThreats: { kind: ThreatKind; strength: number; evidence: string }[];
  advice: { buyNow: string | null; saveFor: string | null; alternative: string | null; ability: string | null; confidence: string };
  /** What the player actually did next, if recorded (purchase observed in later state). */
  outcome?: { bought: string[]; at: number } | undefined;
  informationState: string;
}

export const decisionLogSchema = z.object({
  version: z.literal(1),
  matchId: z.string(),
  heroId: z.number().nullable(),
  build: z.number().nullable(),
  entries: z.array(
    z.object({
      at: z.number(),
      gameTime: z.number().nullable(),
      heroId: z.number().nullable(),
      souls: z.number().nullable(),
      owned: z.array(z.string()),
      enemies: z.array(z.number()),
      topThreats: z.array(z.object({ kind: z.string(), strength: z.number(), evidence: z.string() })),
      advice: z.object({ buyNow: z.string().nullable(), saveFor: z.string().nullable(), alternative: z.string().nullable(), ability: z.string().nullable(), confidence: z.string() }),
      outcome: z.object({ bought: z.array(z.string()), at: z.number() }).optional(),
      informationState: z.string(),
    }),
  ),
});
export type DecisionLog = z.infer<typeof decisionLogSchema>;

/** Load a decision log as a replay-only store (never merged into live state). */
export function replayStoreFromLog(log: DecisionLog): MatchState {
  let s = createMatchState("replay");
  s = applyEvent(s, { type: "match.start", matchId: `replay:${log.matchId}`, mode: "standard", at: log.entries[0]?.at ?? 0 });
  for (const e of log.entries) {
    const base = { source: "replay" as const, observedAt: e.at, gameTime: e.gameTime };
    if (e.heroId != null) s = applyEvent(s, { type: "observe", field: "me.hero", value: e.heroId, ...base });
    if (e.souls != null) s = applyEvent(s, { type: "observe", field: "me.souls", value: e.souls, ...base });
    s = applyEvent(s, { type: "observe", field: "me.items", value: e.owned, ...base });
    s = applyEvent(s, { type: "observe", field: "roster.enemies", value: e.enemies, ...base });
  }
  return s;
}
