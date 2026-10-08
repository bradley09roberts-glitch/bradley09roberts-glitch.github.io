import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  GameData,
  ProfileRegistry,
  applyEvents,
  changeIndex,
  createMatchState,
  defaultPreferences,
  defaultRules,
  evaluateCompatibility,
  validateSnapshot,
  type EngineDeps,
  type FieldKey,
  type MatchEvent,
  type ReviewStamps,
  type Snapshot,
} from "../src/index.js";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

export function loadSnapshot(): Snapshot {
  const m = JSON.parse(readFileSync(path.join(root, "data/snapshots/manifest.json"), "utf8"));
  const v = validateSnapshot(JSON.parse(readFileSync(path.join(root, "data/snapshots", m.active.file), "utf8")));
  if (!v.ok) throw new Error(v.errors.join("\n"));
  return v.snapshot;
}

export function loadStamps(): ReviewStamps {
  return JSON.parse(readFileSync(path.join(root, "data/knowledge/review-stamps.json"), "utf8")) as ReviewStamps;
}

let cached: Snapshot | null = null;
export function deps(snapshot?: Snapshot, stamps?: ReviewStamps | null): EngineDeps {
  const snap = snapshot ?? (cached ??= loadSnapshot());
  const data = new GameData(snap);
  const changes = changeIndex(data, stamps === undefined ? loadStamps() : stamps);
  return {
    data,
    rules: defaultRules("standard"),
    profiles: new ProfileRegistry(data, changes),
    changes,
    compat: evaluateCompatibility(snap, { clientVersion: snap.meta.clientVersion, checkedAt: "2026-10-08T00:00:00Z" }),
  };
}

export const T0 = 1_790_000_000_000;

/** Build a manual match quickly. Times are wall ms offsets from T0. */
export function match(events: MatchEvent[], matchId = "m1") {
  return applyEvents(createMatchState("live"), [{ type: "match.start", matchId, mode: "standard", at: T0 }, ...events]);
}

export function obs(field: FieldKey, value: unknown, t = 0, gameTime: number | null = null, extra: Partial<Extract<MatchEvent, { type: "observe" }>> = {}): MatchEvent {
  return { type: "observe", field, value, source: "manual", observedAt: T0 + t, gameTime, ...extra };
}

export const prefs = defaultPreferences;

import { parseScenario, scenarioEvents, type ScenarioFixture } from "../src/index.js";

export function loadFixture(name: string): ScenarioFixture {
  const p = parseScenario(JSON.parse(readFileSync(path.join(root, "fixtures/scenarios", `${name}.json`), "utf8")));
  if (!p.ok) throw new Error(p.errors.join("\n"));
  return p.scenario;
}

/** Play a fixture into a fresh live store; returns state and a "now" just after the last event. */
export function playFixture(name: string) {
  const s = loadFixture(name);
  const events = scenarioEvents(s, T0);
  const state = applyEvents(createMatchState("live"), events);
  const last = Math.max(...s.events.map((e) => e.t));
  return { state, now: T0 + last + 1000, scenario: s };
}
