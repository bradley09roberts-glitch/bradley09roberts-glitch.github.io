import type { GameData } from "../data/gameData.js";
import type { Archetype, HeroProfile, UpgradeStep } from "../knowledge/profiles/types.js";
import type { AbilityState, ThreatKind } from "../state/types.js";
import type { Ability, AbilityCostSchedule, Hero, Provenance } from "../types.js";

export interface AbilityAction {
  kind: "unlock" | "upgrade" | "hold" | "none" | "need-info" | "invalid-state";
  ability: { className: string; name: string; slot: number; image: string | null } | null;
  tier: number | null;
  pointsRequired: number;
  pointsAvailable: number | null;
  unlocksAvailable: number | null;
  /** What the tier changes, from current data. */
  whatChanges: string;
  reason: string;
  alternative: { className: string; name: string; tier: number; cost: number; note: string } | null;
  issues: string[];
  /** Souls until the next level grants a point/unlock (requires total souls). */
  nextGrant: { souls: number; grant: "unlock" | "point"; level: number } | null;
  breakpoint: boolean;
  provenance: Provenance;
  planSource: "curated" | "statistical" | "fallback";
  /** Remaining plan steps (for display). */
  upcoming: { className: string; name: string; tier: number; cost: number; breakpoint: boolean }[];
}

export interface PlannerInput {
  hero: Hero;
  profile: HeroProfile;
  archetype: Archetype;
  costs: AbilityCostSchedule;
  abilities: AbilityState | null;
  unspentPoints: number | null;
  unspentUnlocks: number | null;
  /** Total souls collected (net worth), if known, to estimate the next level grant. */
  totalSouls?: number | null;
  /** Active threats to select situational branches. */
  threats?: ThreatKind[];
}

export function tierText(a: Ability, tier: number): string {
  const t = a.tiers[tier - 1];
  if (!t) return "";
  if (t.text) return t.text;
  return t.upgrades
    .map((u) => {
      const n = typeof u.bonus === "number" ? u.bonus : Number(u.bonus);
      const v = Number.isFinite(n) ? `${n > 0 ? "+" : ""}${n}` : String(u.bonus);
      return `${v}${u.postfix ?? ""} ${u.label ?? u.property}`.trim();
    })
    .join(", ");
}

/** Validate the reported ability state against the hero and rules. */
export function validateAbilityState(hero: Hero, st: AbilityState): string[] {
  const issues: string[] = [];
  const names = new Map(hero.abilities.map((a) => [a.className, a.name]));
  for (const u of st.unlocked) if (!names.has(u)) issues.push(`${u} is not one of ${hero.name}'s abilities`);
  for (const [cn, t] of Object.entries(st.tiers)) {
    if (!names.has(cn)) issues.push(`${cn} is not one of ${hero.name}'s abilities`);
    if (!Number.isInteger(t) || t < 0 || t > 3) issues.push(`${names.get(cn) ?? cn}: tier ${t} is outside 0–3`);
    if (t > 0 && !st.unlocked.includes(cn)) issues.push(`${names.get(cn) ?? cn} has tier ${t} but is not unlocked`);
  }
  return issues;
}

function nextGrant(hero: Hero, totalSouls: number | null | undefined): AbilityAction["nextGrant"] {
  if (totalSouls == null) return null;
  const next = hero.levels.find((l) => l.souls > totalSouls && l.grants.length);
  if (!next) return null;
  return { souls: next.souls - totalSouls, grant: next.grants[0]!, level: next.level };
}

function branchPlan(arch: Archetype, threats: ThreatKind[] | undefined): { steps: UpgradeStep[]; branch: string | null } {
  const base = arch.abilityPlan.upgrades;
  if (!threats?.length) return { steps: base, branch: null };
  const match = arch.abilityBranches.find((b) => {
    const w = b.when.toLowerCase();
    return (
      (threats.includes("hard_cc") && /cc|debuff/.test(w)) ||
      (threats.includes("enemy_healing") && /heal/.test(w)) ||
      ((threats.includes("weapon_burst") || threats.includes("spirit_burst")) && /survival|caught|dive/.test(w))
    );
  });
  if (!match) return { steps: base, branch: null };
  // Move the branch's preferred steps forward, keeping tier order legal.
  const preferred = match.prefer.map((p) => `${p.ability}:${p.tier}`);
  const front: UpgradeStep[] = [];
  const rest: UpgradeStep[] = [];
  for (const st of base) (preferred.includes(`${st.ability}:${st.tier}`) ? front : rest).push(st);
  // Prerequisite tiers of preferred steps must come first.
  const ordered: UpgradeStep[] = [];
  const pushWithPrereqs = (st: UpgradeStep) => {
    for (let t = 1; t < st.tier; t++) {
      const pre = base.find((b) => b.ability === st.ability && b.tier === t);
      if (pre && !ordered.includes(pre)) ordered.push(pre);
    }
    if (!ordered.includes(st)) ordered.push(st);
  };
  front.forEach(pushWithPrereqs);
  rest.forEach((st) => { if (!ordered.includes(st)) ordered.push(st); });
  return { steps: ordered, branch: match.when };
}

export function planAbility(input: PlannerInput): AbilityAction {
  const { hero, archetype, costs } = input;
  const plan = archetype.abilityPlan;
  const planSource: AbilityAction["planSource"] = input.profile.status === "curated" ? "curated" : plan.provenance.kind === "statistical" ? "statistical" : "fallback";
  const byClass = new Map(hero.abilities.map((a) => [a.className, a]));
  const info = (a: Ability) => ({ className: a.className, name: a.name, slot: a.slot, image: a.image });
  const empty: AbilityAction = {
    kind: "none",
    ability: null,
    tier: null,
    pointsRequired: 0,
    pointsAvailable: input.unspentPoints,
    unlocksAvailable: input.unspentUnlocks,
    whatChanges: "",
    reason: "",
    alternative: null,
    issues: [],
    nextGrant: nextGrant(hero, input.totalSouls),
    breakpoint: false,
    provenance: plan.provenance,
    planSource,
    upcoming: [],
  };
  const st: AbilityState = input.abilities ?? { unlocked: [], tiers: {} };
  const issues = validateAbilityState(hero, st);
  if (issues.length) {
    return { ...empty, kind: "invalid-state", issues, reason: "Your ability state is inconsistent; correct it before advice is given." };
  }
  const tierOf = (cn: string) => (st.unlocked.includes(cn) ? st.tiers[cn] ?? 0 : -1);
  const { steps, branch } = branchPlan(archetype, input.threats);
  const remainingSteps = steps.filter((s) => tierOf(s.ability) < s.tier);
  const upcoming = remainingSteps.slice(0, 6).map((s) => ({
    className: s.ability,
    name: byClass.get(s.ability)?.name ?? s.ability,
    tier: s.tier,
    cost: costs.tierCosts[s.tier - 1]!,
    breakpoint: !!s.breakpoint,
  }));

  // 1) Unlocks are a separate currency: use them first when available.
  const locked = plan.unlockOrder.filter((cn) => !st.unlocked.includes(cn));
  if (locked.length && (input.unspentUnlocks ?? 0) > 0) {
    const a = byClass.get(locked[0]!)!;
    return {
      ...empty,
      kind: "unlock",
      ability: info(a),
      tier: 0,
      pointsRequired: 0,
      whatChanges: a.text,
      reason: `Unlock ${a.name} next (${st.unlocked.length + 1}${ordinal(st.unlocked.length + 1)} unlock in the ${planSource} plan). Unlocks use a separate currency from ability points.`,
      upcoming,
    };
  }
  if (locked.length && input.unspentUnlocks == null) {
    // We cannot know if an unlock is waiting; ask, but still compute the point action below.
  }

  if (input.unspentPoints == null) {
    return {
      ...empty,
      kind: "need-info",
      reason: "Enter your unspent ability points (shown on the HUD) to get the exact next upgrade.",
      issues: locked.length && input.unspentUnlocks == null ? ["Unspent unlocks unknown"] : [],
      upcoming,
    };
  }

  const points = input.unspentPoints;
  // 2) Next legal planned step: ability unlocked, previous tier owned.
  const legal = remainingSteps.filter((s) => tierOf(s.ability) === s.tier - 1);
  const next = legal[0];
  if (!next) {
    const allMax = hero.abilities.every((a) => tierOf(a.className) === 3);
    return {
      ...empty,
      kind: "none",
      reason: allMax ? "All abilities are fully upgraded." : locked.length ? "Unlock another ability before spending more points (no legal upgrade on unlocked abilities)." : "No remaining planned upgrades.",
      upcoming,
    };
  }
  const a = byClass.get(next.ability)!;
  const cost = costs.tierCosts[next.tier - 1]!;
  const changes = tierText(a, next.tier);
  const why = next.why || `Next in the ${planSource} plan${branch ? ` (branch: ${branch})` : ""}.`;
  if (points >= cost) {
    return {
      ...empty,
      kind: "upgrade",
      ability: info(a),
      tier: next.tier,
      pointsRequired: cost,
      whatChanges: changes,
      reason: `${why}${branch && next.why ? ` Branch: ${branch}.` : ""}`,
      breakpoint: !!next.breakpoint,
      upcoming,
    };
  }
  // 3) Not enough points for the next step: hold for a breakpoint, or spend on a cheaper legal step.
  const cheaper = legal.slice(1).find((s) => costs.tierCosts[s.tier - 1]! <= points && !s.breakpoint);
  const holdReason = `Hold: ${a.name} T${next.tier} costs ${cost} points (you have ${points}). ${next.why || ""}`.trim();
  if (next.breakpoint || !cheaper) {
    const alt = cheaper
      ? (() => {
          const ca = byClass.get(cheaper.ability)!;
          const cc = costs.tierCosts[cheaper.tier - 1]!;
          return { className: ca.className, name: ca.name, tier: cheaper.tier, cost: cc, note: `Spending ${cc} now delays ${a.name} T${next.tier} by ${cc} point${cc > 1 ? "s" : ""}.` };
        })()
      : null;
    return {
      ...empty,
      kind: "hold",
      ability: info(a),
      tier: next.tier,
      pointsRequired: cost,
      whatChanges: changes,
      reason: next.breakpoint ? `${holdReason} This is a key breakpoint, so saving is preferred.` : `${holdReason} No cheaper planned upgrade is available.`,
      alternative: alt,
      breakpoint: !!next.breakpoint,
      upcoming,
    };
  }
  const ca = byClass.get(cheaper.ability)!;
  const cc = costs.tierCosts[cheaper.tier - 1]!;
  return {
    ...empty,
    kind: "upgrade",
    ability: info(ca),
    tier: cheaper.tier,
    pointsRequired: cc,
    whatChanges: tierText(ca, cheaper.tier),
    reason: `${a.name} T${next.tier} needs ${cost} points; it is not a key breakpoint, so spend ${cc} on ${ca.name} T${cheaper.tier} now. ${cheaper.why}`.trim(),
    alternative: { className: a.className, name: a.name, tier: next.tier, cost, note: `Or hold for ${a.name} T${next.tier}.` },
    breakpoint: false,
    upcoming,
  };
}

function ordinal(n: number): string {
  return n === 1 ? "st" : n === 2 ? "nd" : n === 3 ? "rd" : "th";
}

/** Points granted by levels reached with a given total souls amount. */
export function pointsEarned(hero: Hero, totalSouls: number): { points: number; unlocks: number; level: number } {
  let points = 0;
  let unlocks = 0;
  let level = 0;
  for (const l of hero.levels) {
    if (l.souls > totalSouls) break;
    level = l.level;
    for (const g of l.grants) g === "point" ? points++ : unlocks++;
  }
  return { points, unlocks, level };
}

export function heroOf(data: GameData, id: number): Hero | undefined {
  return data.hero(id);
}
