import type { ThreatKind } from "../../state/types.js";
import type { Provenance } from "../../types.js";

/**
 * - curated: hand-authored archetypes, routes and ability breakpoints, with every item and
 *   ability reference checked against the current snapshot. AI-authored; awaiting review by an
 *   experienced player. Shown as "Curated (unreviewed by expert)".
 * - auto: derived only from current data and statistics (popular items, most-played ability
 *   order, ability properties). Shown as "Basic (data-derived)".
 */
export type ProfileStatus = "curated" | "auto";

export type Level3 = "low" | "medium" | "high";

export interface DamageMix {
  weapon: number;
  spirit: number;
  melee: number;
}

export interface UpgradeStep {
  ability: string;
  tier: 1 | 2 | 3;
  /** Why this step matters; empty for statistical-only steps. */
  why: string;
  /** Functional breakpoint worth holding points for. */
  breakpoint?: boolean;
}

export interface AbilityPlan {
  /** Ability classNames in unlock order (all four). */
  unlockOrder: string[];
  /** Ordered upgrade steps. Steps must be legal in order (tier n after tier n-1). */
  upgrades: UpgradeStep[];
  provenance: Provenance;
}

export interface Route {
  /** Ordered item classNames to buy after (or interleaved with) fundamentals. */
  items: string[];
  note: string;
}

export interface Archetype {
  id: string;
  label: string;
  description: string;
  damage: DamageMix;
  range: "close" | "mid" | "long";
  /** Hero fundamentals shared by all routes (build identity). */
  fundamentals: string[];
  routes: { stabilise: Route; normal: Route; ambitious: Route };
  /** Items that conflict with this build's identity. */
  avoid: string[];
  abilityPlan: AbilityPlan;
  /** Situational ability branches (e.g. farming, survival). */
  abilityBranches: { when: string; prefer: UpgradeStep[] }[];
  provenance: Provenance[];
}

/** What this hero does to *you* when they are on the enemy team. */
export interface ThreatSignature {
  damage: DamageMix;
  burst: Level3;
  sustain: Level3;
  threats: ThreatKind[];
  /** Short lane-respect notes: which mechanic to respect (never hidden cooldowns/positions). */
  laneNotes: string[];
  provenance: Provenance;
}

export interface HeroProfile {
  heroId: number;
  heroClass: string;
  status: ProfileStatus;
  author: string;
  /** Client build the references were checked against. */
  checkedBuild: number | null;
  archetypes: Archetype[];
  threat: ThreatSignature;
  /** Entity references this profile depends on (for patch invalidation). */
  dependsOn: { kind: "item" | "ability"; className: string }[];
}
