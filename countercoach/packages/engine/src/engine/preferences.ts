/**
 * User preferences that shape recommendations. Stored locally by the app.
 */
export type RouteChoice = "auto" | "stabilise" | "normal" | "ambitious";
export type Difficulty = "simple" | "standard" | "complex";

export interface Weights {
  /** Build identity / archetype fit. */
  fit: number;
  /** Answering observed threats. */
  counter: number;
  /** Progress toward planned upgrades (component paths). */
  path: number;
  /** Value of the item tier in the current phase. */
  phase: number;
  /** Multiplier applied to counter value for urgent threats. */
  urgentMultiplier: number;
  /** BUY NOW must reach this fraction of the best target's score unless it is a component of it or urgent. */
  buyThreshold: number;
  /** Relative score margin a new top choice needs to replace the previous one. */
  stabilityMargin: number;
  /** Penalty per 1,600 souls still missing (favours reachable targets), capped at 4 steps. */
  distancePer1600: number;
}

export const DEFAULT_WEIGHTS: Weights = {
  fit: 1.0,
  counter: 1.1,
  path: 0.6,
  phase: 0.5,
  urgentMultiplier: 1.7,
  buyThreshold: 0.8,
  stabilityMargin: 0.12,
  distancePer1600: 0.12,
};

export interface ItemDecision {
  className: string;
  reason: string;
  at: number;
  /** Deferred items return after this game time (seconds). */
  untilGameTime?: number | null;
}

export interface Preferences {
  archetypeId: string | null;
  route: RouteChoice;
  difficulty: Difficulty;
  /** Core items the user locked; they keep top fit and are never displaced by routes. */
  lockedCore: string[];
  pinned: ItemDecision[];
  rejected: ItemDecision[];
  deferred: ItemDecision[];
  weights: Weights;
}

export function defaultPreferences(): Preferences {
  return {
    archetypeId: null,
    route: "auto",
    difficulty: "standard",
    lockedCore: [],
    pinned: [],
    rejected: [],
    deferred: [],
    weights: { ...DEFAULT_WEIGHTS },
  };
}

export function maxActiveItems(d: Difficulty): number {
  return d === "simple" ? 2 : d === "standard" ? 4 : 6;
}
