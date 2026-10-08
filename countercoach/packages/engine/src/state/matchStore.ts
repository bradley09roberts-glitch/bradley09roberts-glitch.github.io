import type { Confidence } from "../types.js";
import type {
  FieldKey,
  MatchEvent,
  MatchState,
  Observation,
  SourceKind,
  StateLogEntry,
  ValueOf,
} from "./types.js";

/**
 * Pure, deterministic match-state reducer. All observation conflict, expiry and reset rules
 * live here so every adapter (manual, scenario, future live/screen) behaves identically.
 */

const LOG_LIMIT = 200;

/** Higher number = more authoritative for the same moment in time. */
const AUTHORITY: Record<SourceKind, number> = {
  scenario: 1,
  manual: 2,
  screen: 3,
  live: 4,
  replay: 0,
};

/** Minimum game seconds a new authoritative observation must post-date a user correction. */
const CORRECTION_OVERRIDE_MARGIN_S = 10;

export interface FieldPolicy {
  /** Age (seconds) after which the value is "aging" (shown with an age label). */
  agingAfterS: number;
  /** Age (seconds) after which the value is "stale" (advice labelled last-observed). */
  staleAfterS: number;
}

/** Field-specific freshness policy (author judgement; documented in docs/STATE.md). */
export function fieldPolicy(field: FieldKey, source: SourceKind): FieldPolicy {
  const live = source === "live" || source === "screen";
  if (field === "me.souls") return live ? { agingAfterS: 10, staleAfterS: 30 } : { agingAfterS: 60, staleAfterS: 180 };
  if (field === "me.unspentPoints" || field === "me.unspentUnlocks") return { agingAfterS: 120, staleAfterS: 300 };
  if (field === "me.items" || field === "me.abilities") return { agingAfterS: 300, staleAfterS: 900 };
  if (field.startsWith("enemyItems:") || field.startsWith("allyItems:")) return { agingAfterS: 240, staleAfterS: 600 };
  if (field === "me.deathRecap") return { agingAfterS: 120, staleAfterS: 360 };
  if (field === "match.gameTime") return { agingAfterS: 120, staleAfterS: 600 };
  // Rosters, hero, phase, standing do not go stale within a match.
  return { agingAfterS: Number.POSITIVE_INFINITY, staleAfterS: Number.POSITIVE_INFINITY };
}

export function createMatchState(storeKind: "live" | "replay" = "live"): MatchState {
  return {
    storeKind,
    matchId: null,
    mode: "standard",
    startedAt: null,
    ended: false,
    paused: false,
    pauses: [],
    fields: {},
    pending: [],
    threatReports: [],
    log: [],
    seq: 0,
  };
}

function withLog(s: MatchState, e: StateLogEntry): MatchState {
  const log = s.log.length >= LOG_LIMIT ? s.log.slice(s.log.length - LOG_LIMIT + 1) : s.log.slice();
  log.push(e);
  return { ...s, log };
}

function sourceAllowed(s: MatchState, src: SourceKind): boolean {
  return s.storeKind === "replay" ? src === "replay" : src !== "replay";
}

/**
 * Decide whether `next` should replace `cur` for a field. Returns a reason when it should not.
 */
export function conflictDecision(cur: Observation | undefined, next: Omit<Observation, "seq">): { accept: boolean; reason: string } {
  if (!cur) return { accept: true, reason: "first observation" };
  // Out-of-order: an observation of an earlier game moment never replaces a later one.
  if (next.gameTime != null && cur.gameTime != null && next.gameTime < cur.gameTime) {
    return { accept: false, reason: `out of order (game time ${next.gameTime}s < ${cur.gameTime}s)` };
  }
  if (next.gameTime == null && cur.gameTime == null && next.observedAt < cur.observedAt) {
    return { accept: false, reason: "out of order (older wall-clock observation)" };
  }
  if (cur.userCorrection && !next.userCorrection) {
    const clearlyNewer =
      next.gameTime != null && cur.gameTime != null
        ? next.gameTime - cur.gameTime >= CORRECTION_OVERRIDE_MARGIN_S
        : next.observedAt - cur.observedAt >= CORRECTION_OVERRIDE_MARGIN_S * 1000;
    const moreAuthoritative = AUTHORITY[next.source] > AUTHORITY.manual && next.confidence === "high";
    if (!(clearlyNewer && moreAuthoritative)) {
      return { accept: false, reason: "user correction preserved (new observation not clearly newer and more authoritative)" };
    }
    return { accept: true, reason: "newer high-confidence verified observation supersedes correction" };
  }
  const sameMoment =
    next.gameTime != null && cur.gameTime != null ? next.gameTime === cur.gameTime : next.observedAt === cur.observedAt;
  if (sameMoment && AUTHORITY[next.source] < AUTHORITY[cur.source]) {
    return { accept: false, reason: `same moment, lower authority (${next.source} < ${cur.source})` };
  }
  return { accept: true, reason: "newer observation" };
}

function resetForNewMatch(prev: MatchState, matchId: string, mode: MatchState["mode"], at: number): MatchState {
  const fresh = createMatchState(prev.storeKind);
  return { ...fresh, matchId, mode, startedAt: at, seq: prev.seq, log: prev.log };
}

/** Fields that belong to "my hero" and are invalid after a hero change. */
const HERO_SCOPED: FieldKey[] = ["me.items", "me.abilities", "me.unspentPoints", "me.unspentUnlocks", "me.souls", "me.deathRecap"];

export function applyEvent(state: MatchState, ev: MatchEvent): MatchState {
  switch (ev.type) {
    case "match.start": {
      if (state.matchId === ev.matchId && !state.ended) {
        return withLog(state, { at: ev.at, event: ev.type, outcome: "applied", reason: "reconnect to same match; state kept" });
      }
      const s = resetForNewMatch(state, ev.matchId, ev.mode, ev.at);
      return withLog(s, { at: ev.at, event: ev.type, outcome: "reset", reason: `new match ${ev.matchId}; previous roster, items and threats cleared` });
    }
    case "match.end":
      return withLog({ ...state, ended: true }, { at: ev.at, event: ev.type, outcome: "applied", reason: "match ended" });
    case "match.pause":
      if (state.paused) return state;
      return withLog({ ...state, paused: true, pauses: [...state.pauses, [ev.at, null]] }, { at: ev.at, event: ev.type, outcome: "applied", reason: "paused" });
    case "match.resume": {
      if (!state.paused) return state;
      const pauses = state.pauses.map((p, i) => (i === state.pauses.length - 1 && p[1] == null ? ([p[0], ev.at] as [number, number]) : p));
      return withLog({ ...state, paused: false, pauses }, { at: ev.at, event: ev.type, outcome: "applied", reason: "resumed" });
    }
    case "observe": {
      if (!sourceAllowed(state, ev.source)) {
        return withLog(state, {
          at: ev.observedAt,
          event: ev.type,
          field: ev.field,
          outcome: "rejected",
          reason: `${ev.source} observations are not accepted by a ${state.storeKind} store (spectator/replay data is kept separate)`,
        });
      }
      if (state.ended && ev.source !== "replay") {
        return withLog(state, { at: ev.observedAt, event: ev.type, field: ev.field, outcome: "dropped", reason: "match has ended; start a new match" });
      }
      const next: Omit<Observation, "seq"> = {
        value: ev.value,
        source: ev.source,
        observedAt: ev.observedAt,
        gameTime: ev.gameTime ?? null,
        confidence: ev.confidence ?? defaultConfidence(ev.source),
        userCorrection: ev.userCorrection === true,
      };
      const cur = state.fields[ev.field];
      const d = conflictDecision(cur, next);
      if (!d.accept) return withLog(state, { at: ev.observedAt, event: ev.type, field: ev.field, outcome: "dropped", reason: d.reason });
      let fields: MatchState["fields"] = { ...state.fields, [ev.field]: { ...next, seq: state.seq + 1 } };
      let reason = d.reason;
      if (ev.field === "me.hero" && cur && cur.value !== ev.value) {
        for (const k of HERO_SCOPED) delete fields[k];
        reason += "; hero changed, hero-scoped fields cleared";
      }
      if (ev.field === "roster.enemies") fields = pruneHeroFields(fields, "enemyItems:", ev.value as number[]);
      if (ev.field === "roster.allies") fields = pruneHeroFields(fields, "allyItems:", ev.value as number[]);
      return withLog({ ...state, fields, seq: state.seq + 1 }, { at: ev.observedAt, event: ev.type, field: ev.field, outcome: "applied", reason });
    }
    case "observe.ambiguous": {
      if (!sourceAllowed(state, ev.source)) {
        return withLog(state, { at: ev.observedAt, event: ev.type, field: ev.field, outcome: "rejected", reason: "source not accepted by this store" });
      }
      const pending = state.pending.filter((p) => p.id !== ev.id);
      pending.push({ id: ev.id, field: ev.field, candidates: ev.candidates, source: ev.source, observedAt: ev.observedAt, gameTime: ev.gameTime ?? null });
      return withLog({ ...state, pending }, {
        at: ev.observedAt,
        event: ev.type,
        field: ev.field,
        outcome: "pending",
        reason: `${ev.candidates.length} candidates; waiting for user confirmation (never auto-selected)`,
      });
    }
    case "resolve.ambiguous": {
      const p = state.pending.find((x) => x.id === ev.id);
      if (!p) return state;
      const pending = state.pending.filter((x) => x.id !== ev.id);
      if (ev.choice === "reject") {
        return withLog({ ...state, pending }, { at: ev.at, event: ev.type, field: p.field, outcome: "dropped", reason: "user rejected all candidates" });
      }
      const c = p.candidates[ev.choice];
      if (!c) return withLog({ ...state, pending }, { at: ev.at, event: ev.type, field: p.field, outcome: "dropped", reason: "invalid choice" });
      const applied = applyEvent(
        { ...state, pending },
        { type: "observe", field: p.field, value: c.value, source: p.source, observedAt: ev.at, gameTime: p.gameTime, confidence: "high", userCorrection: true },
      );
      return applied;
    }
    case "threat.report": {
      if (!sourceAllowed(state, ev.report.source)) {
        return withLog(state, { at: ev.report.observedAt, event: ev.type, outcome: "rejected", reason: "source not accepted by this store" });
      }
      const id = ev.report.id ?? `t${state.seq + 1}`;
      const threatReports = state.threatReports.filter((t) => t.id !== id).concat({ ...ev.report, id });
      return withLog({ ...state, threatReports, seq: state.seq + 1 }, { at: ev.report.observedAt, event: ev.type, outcome: "applied", reason: `threat ${ev.report.kind} reported` });
    }
    case "threat.clear":
      return withLog({ ...state, threatReports: state.threatReports.filter((t) => t.id !== ev.id) }, { at: ev.at, event: ev.type, outcome: "applied", reason: `threat ${ev.id} cleared` });
  }
}

function pruneHeroFields(fields: MatchState["fields"], prefix: string, keep: number[]): MatchState["fields"] {
  const out: MatchState["fields"] = {};
  for (const [k, v] of Object.entries(fields) as [FieldKey, Observation][]) {
    if (k.startsWith(prefix) && !keep.includes(Number(k.slice(prefix.length)))) continue;
    out[k] = v;
  }
  return out;
}

function defaultConfidence(src: SourceKind): Confidence {
  return src === "live" ? "high" : src === "screen" ? "medium" : src === "manual" ? "high" : "medium";
}

export function applyEvents(state: MatchState, events: MatchEvent[]): MatchState {
  return events.reduce(applyEvent, state);
}

// ---------------------------------------------------------------------------------------------
// Reading fields with freshness
// ---------------------------------------------------------------------------------------------

export type Freshness = "fresh" | "aging" | "stale";

export interface FieldRead<T> {
  value: T;
  obs: Observation<T>;
  ageS: number;
  freshness: Freshness;
}

/** Current match clock estimate (seconds), extrapolating from the last clock observation. */
export function currentGameTime(state: MatchState, now: number): number | null {
  const o = state.fields["match.gameTime"];
  if (!o) {
    // Fall back to the latest game time carried by any observation.
    let best: number | null = null;
    for (const v of Object.values(state.fields)) if (v?.gameTime != null && (best == null || v.gameTime > best)) best = v.gameTime;
    return best;
  }
  const base = o.value as number;
  if (state.ended) return base;
  // Subtract paused wall time that overlaps the window since the clock was observed.
  let paused = 0;
  for (const [a, b] of state.pauses) {
    const lo = Math.max(a, o.observedAt);
    const hi = Math.min(b ?? now, now);
    if (hi > lo) paused += hi - lo;
  }
  const elapsed = Math.max(0, now - o.observedAt - paused) / 1000;
  // The clock is never earlier than the latest game time any observation reported.
  let latest = base + elapsed;
  for (const v of Object.values(state.fields)) if (v?.gameTime != null && v.gameTime > latest) latest = v.gameTime;
  return Math.round(latest);
}

export function readField<K extends FieldKey>(state: MatchState, field: K, now: number): FieldRead<ValueOf<K>> | null {
  const obs = state.fields[field] as Observation<ValueOf<K>> | undefined;
  if (!obs) return null;
  const gt = currentGameTime(state, now);
  const ageS = obs.gameTime != null && gt != null ? Math.max(0, gt - obs.gameTime) : Math.max(0, (now - obs.observedAt) / 1000);
  const p = fieldPolicy(field, obs.source);
  const freshness: Freshness = ageS >= p.staleAfterS ? "stale" : ageS >= p.agingAfterS ? "aging" : "fresh";
  return { value: obs.value, obs, ageS: Math.round(ageS), freshness };
}

export function enemyIds(state: MatchState): number[] {
  return (state.fields["roster.enemies"]?.value as number[] | undefined) ?? [];
}

export function allyIds(state: MatchState): number[] {
  return (state.fields["roster.allies"]?.value as number[] | undefined) ?? [];
}
