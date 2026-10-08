import { planAbility, type AbilityAction } from "../abilities/planner.js";
import { GameData, evaluateCompatibility, type Compatibility } from "../data/gameData.js";
import { validateSnapshot } from "../data/schema.js";
import {
  activeHints,
  buildRepair,
  lanePlan,
  powerSpike,
  replacementAdvice,
  teamUtility,
  threatPanel,
  type ActiveHint,
  type BuildRepair,
  type LanePlan,
  type PowerSpike,
  type ReplacementAdvice,
  type TeamUtilityEntry,
  type ThreatPanelEntry,
} from "../features/features.js";
import { ProfileRegistry, changeIndex, type ReviewStamps } from "../knowledge/profiles/index.js";
import { applyOverrides, defaultRules, type GameMode, type RuleOverrides } from "../rules/gameRules.js";
import { readField } from "../state/matchStore.js";
import type { MatchState } from "../state/types.js";
import type { Snapshot } from "../types.js";
import type { Preferences } from "./preferences.js";
import { recommend, type EngineDeps, type Recommendation } from "./recommend.js";

export interface CoachOutput {
  rec: Recommendation;
  ability: AbilityAction | null;
  threatPanel: ThreatPanelEntry[];
  powerSpike: PowerSpike;
  buildRepair: BuildRepair | null;
  teamUtility: TeamUtilityEntry[];
  replacement: ReplacementAdvice | null;
  activeHints: ActiveHint[];
  lanePlan: LanePlan;
  /** Local compute time for this evaluation (ms). */
  computeMs: number;
}

export function createDeps(
  snapshot: Snapshot,
  stamps: ReviewStamps | null,
  opts: { mode?: GameMode; overrides?: RuleOverrides; latest?: { clientVersion: number | null; checkedAt: string } | null } = {},
): EngineDeps {
  const v = validateSnapshot(snapshot);
  if (!v.ok) throw new Error(`invalid snapshot: ${v.errors.slice(0, 3).join("; ")}`);
  const data = new GameData(v.snapshot);
  const changes = changeIndex(data, stamps);
  const compat: Compatibility = evaluateCompatibility(v.snapshot, opts.latest ?? null);
  return {
    data,
    rules: applyOverrides(defaultRules(opts.mode ?? "standard"), opts.overrides ?? {}),
    profiles: new ProfileRegistry(data, changes),
    changes,
    compat,
  };
}

const perf = (globalThis as { performance?: { now(): number } }).performance;
const clock = (): number => (perf ? perf.now() : Date.now());

export function evaluate(deps: EngineDeps, state: MatchState, prefs: Preferences, now: number, previous?: Recommendation | null): CoachOutput {
  const t0 = clock();
  const depsForMode = state.mode === deps.rules.mode ? deps : { ...deps, rules: { ...deps.rules, mode: state.mode } };
  const rec = recommend(depsForMode, state, prefs, now, previous);
  const heroId = rec.heroId;
  let ability: AbilityAction | null = null;
  let repair: BuildRepair | null = null;
  const owned = readField(state, "me.items", now)?.value ?? [];
  if (heroId != null && rec.decision !== "unsupported") {
    const hero = deps.data.hero(heroId)!;
    const profile = deps.profiles.get(heroId)!;
    const arch = deps.profiles.archetype(heroId, prefs.archetypeId)!;
    ability = planAbility({
      hero,
      profile,
      archetype: arch,
      costs: deps.data.snapshot.abilityCosts,
      abilities: readField(state, "me.abilities", now)?.value ?? null,
      unspentPoints: readField(state, "me.unspentPoints", now)?.value ?? null,
      unspentUnlocks: readField(state, "me.unspentUnlocks", now)?.value ?? null,
      threats: rec.threats.filter((t) => t.strength >= 0.4 && t.evidence !== "roster-only").map((t) => t.kind),
    });
    repair = buildRepair(deps, arch, rec.route, owned);
  }
  const arch = heroId != null ? deps.profiles.archetype(heroId, prefs.archetypeId) : null;
  const out: CoachOutput = {
    rec,
    ability,
    threatPanel: threatPanel(rec, deps, owned),
    powerSpike: powerSpike(rec, ability),
    buildRepair: repair,
    teamUtility: arch ? teamUtility(deps, state, rec, arch, now) : [],
    replacement: heroId != null ? replacementAdvice(deps, rec, owned, prefs) : null,
    activeHints: activeHints(deps, owned),
    lanePlan: lanePlan(deps, state, now),
    computeMs: 0,
  };
  const t1 = clock();
  out.computeMs = Math.round((t1 - t0) * 100) / 100;
  return out;
}
