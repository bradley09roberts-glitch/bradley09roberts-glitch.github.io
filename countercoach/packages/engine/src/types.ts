/**
 * Core domain types for CounterCoach. These describe the normalised, versioned game-data
 * snapshot produced by the ingest package. Nothing here depends on a UI or on Node.
 */

export type SlotType = "weapon" | "vitality" | "spirit";
export type Activation = "passive" | "press" | "instant_cast" | "instant_cast_toggle" | "other";

/** Evidence strength for any fact or rule used by the engine. */
export type EvidenceKind =
  /** Read directly from structured game data (API asset fields). */
  | "data"
  /** Derived deterministically from structured data (e.g. tier costs from build currency deltas). */
  | "derived"
  /** Interpreted from an official/in-data description string. */
  | "description"
  /** Community documentation or reports, not verified on the current build. */
  | "community"
  /** Aggregated match statistics (correlational, never causal). */
  | "statistical"
  /** Author judgement. */
  | "heuristic";

export type Confidence = "high" | "medium" | "low";

export interface Provenance {
  kind: EvidenceKind;
  /** Where the fact came from: URL, data field path, or document. */
  source: string;
  /** How the source was interpreted, in plain words. */
  interpretation: string;
  /** Client build the fact was checked against, if any. */
  checkedBuild?: number | null;
  confidence: Confidence;
}

export interface ItemProperty {
  /** Raw numeric value as a number when parseable. */
  value: number;
  /** Engine-level modifier type, e.g. MODIFIER_VALUE_TECH_RESIST. */
  providedType?: string | undefined;
  label?: string | undefined;
  postfix?: string | undefined;
  /** True when the game marks the property as conditionally applied. */
  conditional?: boolean | undefined;
}

export interface Item {
  id: number;
  className: string;
  name: string;
  slot: SlotType;
  tier: number;
  cost: number;
  activation: Activation;
  isActive: boolean;
  /** className list of component items this item upgrades from. */
  components: string[];
  /** Hero IDs this item is restricted to (empty = any hero). */
  heroRestriction: number[];
  imbue: string | null;
  image: string | null;
  shopFilters: string[];
  /** Numeric, non-zero properties keyed by property name. */
  properties: Record<string, ItemProperty>;
  /** Plain-text description (markup stripped; never HTML). */
  text: string;
  /** True when a corrupted (Broker) variant definition exists for this item. */
  hasCorruptedVariant: boolean;
}

export interface AbilityTier {
  tier: 1 | 2 | 3;
  /** Plain-text tier description from data (t1_desc etc.). */
  text: string;
  upgrades: { property: string; bonus: number | string; label?: string | undefined; postfix?: string | undefined }[];
}

export interface Ability {
  id: number;
  className: string;
  name: string;
  /** Signature slot 1..4 from the hero's item map (signature4 is the ultimate). */
  slot: 1 | 2 | 3 | 4;
  isUltimate: boolean;
  image: string | null;
  text: string;
  tiers: AbilityTier[];
  /** Selected base numeric properties (non-zero), keyed by name. */
  properties: Record<string, ItemProperty>;
}

export interface LevelStep {
  level: number;
  /** Total souls (net worth) required to reach this level. */
  souls: number;
  grants: ("unlock" | "point")[];
}

export interface PopularItemStat {
  className: string;
  pickPct: number;
  winPct: number;
}

export interface AbilityOrderStat {
  /** Sequence of ability IDs in order of unlock/upgrade events. */
  sequence: number[];
  matches: number;
  wins: number;
}

export interface Hero {
  id: number;
  className: string;
  name: string;
  heroType: string | null;
  tags: string[];
  complexity: number | null;
  gunTag: string | null;
  image: string | null;
  cardImage: string | null;
  /** True when player_selectable && !disabled. */
  playable: boolean;
  /** Data still flags the hero as in development (e.g. just released). */
  inDevelopmentFlag: boolean;
  abilities: Ability[];
  levels: LevelStep[];
  popularItems: {
    timestamp: number | null;
    early: PopularItemStat[];
    mid: PopularItemStat[];
    late: PopularItemStat[];
  };
  abilityOrders: {
    retrievedFor: string | null;
    sampleWindowStart: number | null;
    orders: AbilityOrderStat[];
  };
}

export interface AbilityCostSchedule {
  unlockCost: number;
  /** Points required for tier 1, 2, 3 (index 0 = tier 1). */
  tierCosts: [number, number, number];
  provenance: Provenance;
  sampleBuilds: number;
  /** Total points granted by level_info compared with 4 × sum(tierCosts). */
  crossCheck: { pointsGranted: number; pointsToMaxAll: number; consistent: boolean };
}

export interface SnapshotMeta {
  schemaVersion: number;
  apiBase: string;
  endpoints: string[];
  retrievedAt: string;
  clientVersion: number | null;
  serverVersion: number | null;
  versionDate: string | null;
  latestPatch: { title: string; date: string; link: string } | null;
  /** SHA-256 of the canonical JSON of heroes+items+schedule (hex). */
  contentHash: string;
  counts: { heroes: number; playableHeroes: number; items: number; shopItems: number };
}

export interface Snapshot {
  meta: SnapshotMeta;
  heroes: Hero[];
  items: Item[];
  abilityCosts: AbilityCostSchedule;
  /** Price per tier as published by generic data (index = tier). */
  itemPricePerTier: number[];
  /** Map of game modes found in data, used for explicit support labelling. */
  modesInData: string[];
}
