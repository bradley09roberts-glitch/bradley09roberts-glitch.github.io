import type { GameMode } from "../rules/gameRules.js";
import type { Confidence } from "../types.js";

/**
 * Where an observation came from.
 * - manual: typed/clicked by the user
 * - live: a verified live interface (none verified yet; reserved)
 * - screen: user-triggered local screen capture recognition (experimental screen reader)
 * - scenario: development/demo fixture playback
 * - replay: post-match / spectator data. Never accepted by a live store.
 */
export type SourceKind = "manual" | "live" | "screen" | "scenario" | "replay";

export interface Observation<T = unknown> {
  value: T;
  source: SourceKind;
  /** Wall-clock ms when the observation was made. */
  observedAt: number;
  /** Match clock (seconds) the observation reflects, when known. */
  gameTime: number | null;
  confidence: Confidence;
  /** The user explicitly corrected this field. Preserved until deliberately replaced. */
  userCorrection: boolean;
  seq: number;
}

export interface AbilityState {
  /** className of every unlocked signature ability. */
  unlocked: string[];
  /** Upgrade tier (0..3) per unlocked ability className. */
  tiers: Record<string, number>;
}

export interface EnemyItemsValue {
  items: string[];
  /** True only if the user saw the full inventory (e.g. scoreboard). Partial ≠ owns nothing else. */
  complete: boolean;
}

export interface DeathRecap {
  weaponPct: number;
  spiritPct: number;
  meleePct: number;
  killerHeroIds: number[];
}

export type Phase = "lane" | "mid" | "late";
export type Standing = "ahead" | "even" | "behind" | "unknown";

export type ThreatKind =
  | "weapon_damage"
  | "spirit_damage"
  | "melee_damage"
  | "spirit_burst"
  | "weapon_burst"
  | "enemy_healing"
  | "hard_cc"
  | "silence"
  | "slow_kite"
  | "debuffs"
  | "mobility_escape"
  | "stealth"
  | "channel_ultimate"
  | "tanky_targets"
  | "bullet_resist_stacking"
  | "spirit_resist_stacking"
  | "percent_hp_damage";

export interface ThreatReport {
  id: string;
  kind: ThreatKind;
  sourceHeroId: number | null;
  note: string;
  source: SourceKind;
  observedAt: number;
  gameTime: number | null;
  /** user-reported severity; 'major' = repeatedly decisive in fights. */
  severity: "minor" | "major";
}

/** Field keys. Per-hero item fields are `enemyItems:<heroId>` / `allyItems:<heroId>`. */
export type FieldKey =
  | "me.hero"
  | "me.souls"
  | "me.items"
  | "me.abilities"
  | "me.unspentPoints"
  | "me.unspentUnlocks"
  | "me.deathRecap"
  | "match.gameTime"
  | "match.phase"
  | "match.standing"
  | "roster.enemies"
  | "roster.allies"
  | "roster.lane"
  | `enemyItems:${number}`
  | `allyItems:${number}`;

export interface FieldValueMap {
  "me.hero": number;
  "me.souls": number;
  "me.items": string[];
  "me.abilities": AbilityState;
  "me.unspentPoints": number;
  "me.unspentUnlocks": number;
  "me.deathRecap": DeathRecap;
  "match.gameTime": number;
  "match.phase": Phase;
  "match.standing": Standing;
  "roster.enemies": number[];
  "roster.allies": number[];
  "roster.lane": number[];
}

export type ValueOf<K extends FieldKey> = K extends keyof FieldValueMap ? FieldValueMap[K] : EnemyItemsValue;

export interface AmbiguousObservation {
  id: string;
  field: FieldKey;
  candidates: { value: unknown; label: string; confidence: Confidence }[];
  source: SourceKind;
  observedAt: number;
  gameTime: number | null;
}

export type MatchEvent =
  | { type: "match.start"; matchId: string; mode: GameMode; at: number }
  | { type: "match.end"; at: number }
  | { type: "match.pause"; at: number }
  | { type: "match.resume"; at: number }
  | {
      type: "observe";
      field: FieldKey;
      value: unknown;
      source: SourceKind;
      observedAt: number;
      gameTime?: number | null;
      confidence?: Confidence;
      userCorrection?: boolean;
    }
  | {
      type: "observe.ambiguous";
      id: string;
      field: FieldKey;
      candidates: AmbiguousObservation["candidates"];
      source: SourceKind;
      observedAt: number;
      gameTime?: number | null;
    }
  | { type: "resolve.ambiguous"; id: string; choice: number | "reject"; at: number }
  | { type: "threat.report"; report: Omit<ThreatReport, "id"> & { id?: string } }
  | { type: "threat.clear"; id: string; at: number };

export interface StateLogEntry {
  at: number;
  event: MatchEvent["type"];
  field?: FieldKey;
  outcome: "applied" | "dropped" | "pending" | "reset" | "rejected";
  reason: string;
}

export interface MatchState {
  /** 'live' stores accept manual/live/screen/scenario; 'replay' stores accept only replay. */
  storeKind: "live" | "replay";
  matchId: string | null;
  mode: GameMode;
  startedAt: number | null;
  ended: boolean;
  paused: boolean;
  /** Wall-clock pause intervals [start, end|null] (for game-clock extrapolation). */
  pauses: [number, number | null][];
  fields: Partial<Record<FieldKey, Observation>>;
  pending: AmbiguousObservation[];
  threatReports: ThreatReport[];
  log: StateLogEntry[];
  seq: number;
}
