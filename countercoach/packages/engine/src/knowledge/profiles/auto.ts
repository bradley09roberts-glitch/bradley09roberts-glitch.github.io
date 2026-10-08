import type { GameData } from "../../data/gameData.js";
import type { ThreatKind } from "../../state/types.js";
import type { Hero, Item } from "../../types.js";
import { abilityMechanics, itemHas } from "../mechanics.js";
import type { AbilityPlan, Archetype, DamageMix, HeroProfile, Level3, ThreatSignature, UpgradeStep } from "./types.js";

/**
 * Basic, data-derived profile for any playable hero. Uses only:
 *  - popular items per game phase (pick rate; statistical, not causal),
 *  - the most-played ability order in the current window (statistical),
 *  - ability property names (data) for the threat signature.
 */

const WEAPON_STATS = ["weapon_damage", "fire_rate", "ammo"] as const;
const SPIRIT_STATS = ["spirit_power", "cooldown_reduction", "ability_duration", "ability_range", "ability_charges"] as const;

export function damageMixFromPopular(hero: Hero, data: GameData): { mix: DamageMix; sample: number } {
  let w = 0, s = 0, m = 0, total = 0;
  const lists = [...hero.popularItems.mid, ...hero.popularItems.late];
  for (const p of lists) {
    const it = data.item(p.className);
    if (!it) continue;
    const pw = p.pickPct;
    total += pw;
    if (itemHas(it, "melee_damage")) m += pw;
    if (WEAPON_STATS.some((x) => itemHas(it, x)) || it.slot === "weapon") w += pw;
    if (SPIRIT_STATS.some((x) => itemHas(it, x)) || it.slot === "spirit") s += pw;
  }
  const sum = w + s + m || 1;
  return {
    mix: { weapon: round2(w / sum), spirit: round2(s / sum), melee: round2(m / sum) },
    sample: lists.length,
  };
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

/** Decode the API ability-order sequence: first occurrence = unlock, later = tier ups. */
export function decodeOrder(hero: Hero, sequence: number[]): { unlockOrder: string[]; upgrades: UpgradeStep[] } {
  const byId = new Map(hero.abilities.map((a) => [a.id, a]));
  const tiers = new Map<string, number>();
  const unlockOrder: string[] = [];
  const upgrades: UpgradeStep[] = [];
  for (const id of sequence) {
    const a = byId.get(id);
    if (!a) continue;
    if (!tiers.has(a.className)) {
      tiers.set(a.className, 0);
      unlockOrder.push(a.className);
      continue;
    }
    const t = (tiers.get(a.className) ?? 0) + 1;
    if (t > 3) continue;
    tiers.set(a.className, t);
    upgrades.push({ ability: a.className, tier: t as 1 | 2 | 3, why: "" });
  }
  for (const a of hero.abilities) if (!unlockOrder.includes(a.className)) unlockOrder.push(a.className);
  // Complete the plan with any missing tiers in ability-slot order so it always reaches max.
  for (const a of hero.abilities) {
    for (let t = (tiers.get(a.className) ?? 0) + 1; t <= 3; t++) upgrades.push({ ability: a.className, tier: t as 1 | 2 | 3, why: "" });
  }
  return { unlockOrder, upgrades };
}

export function statisticalAbilityPlan(hero: Hero): AbilityPlan {
  const top = hero.abilityOrders.orders[0];
  if (!top) {
    const unlockOrder = hero.abilities.map((a) => a.className);
    const upgrades: UpgradeStep[] = [];
    for (const t of [1, 2, 3] as const) for (const a of hero.abilities) upgrades.push({ ability: a.className, tier: t, why: "" });
    return {
      unlockOrder,
      upgrades,
      provenance: {
        kind: "heuristic",
        source: "fallback: ability slot order",
        interpretation: "No ability-order statistics available; even spread in slot order. Low confidence.",
        confidence: "low",
      },
    };
  }
  const d = decodeOrder(hero, top.sequence);
  return {
    ...d,
    provenance: {
      kind: "statistical",
      source: "GET /v1/analytics/ability-order-stats (most-played order in window)",
      interpretation: `Most-played order: ${top.matches} matches, ${((100 * top.wins) / Math.max(1, top.matches)).toFixed(1)}% wins. Popularity, not proof of optimality.`,
      confidence: top.matches >= 2000 ? "medium" : "low",
    },
  };
}

function legalForProfile(it: Item | undefined): it is Item {
  return !!it && it.tier <= 4;
}

export function threatSignatureFromData(hero: Hero, data: GameData): ThreatSignature {
  const threats = new Set<ThreatKind>();
  let heals = 0;
  let slows = 0;
  for (const a of hero.abilities) {
    for (const m of abilityMechanics(a)) {
      if (m.mechanic === "stun" || m.mechanic === "sleep" || m.mechanic === "immobilize") threats.add("hard_cc");
      if (m.mechanic === "silence") threats.add("silence");
      if (m.mechanic === "slow") slows++;
      if (m.mechanic === "heal_self") heals++;
      if (m.mechanic === "channel" && a.isUltimate) threats.add("channel_ultimate");
      if (m.mechanic === "invis") threats.add("stealth");
      if (m.mechanic === "dash" || m.mechanic === "flight") threats.add("mobility_escape");
      if (m.mechanic === "percent_hp_damage") threats.add("percent_hp_damage");
    }
  }
  if (slows >= 2) threats.add("slow_kite");
  if (heals >= 1) threats.add("enemy_healing");
  const { mix } = damageMixFromPopular(hero, data);
  if (mix.weapon >= 0.45) threats.add("weapon_damage");
  if (mix.spirit >= 0.45) threats.add("spirit_damage");
  if (mix.melee >= 0.2) threats.add("melee_damage");
  const sustain: Level3 = heals >= 2 ? "high" : heals === 1 ? "medium" : "low";
  return {
    damage: mix,
    burst: "medium",
    sustain,
    threats: [...threats],
    laneNotes: [],
    provenance: {
      kind: "derived",
      source: "ability properties (/v1/assets/items) + popular items (/v1/assets/heroes popular_items)",
      interpretation: "Threats inferred from ability property names; damage mix from the weapon/spirit share of popular mid/late items. Burst unknown (medium assumed).",
      confidence: "low",
    },
  };
}

export function autoProfile(hero: Hero, data: GameData): HeroProfile {
  const { mix, sample } = damageMixFromPopular(hero, data);
  const pick = (arr: { className: string; pickPct: number }[], n: number, minPick: number) =>
    arr.filter((p) => p.pickPct >= minPick && legalForProfile(data.item(p.className))).slice(0, n).map((p) => p.className);
  const early = pick(hero.popularItems.early, 4, 20);
  const mid = pick(hero.popularItems.mid, 6, 15).filter((c) => !early.includes(c));
  const late = pick(hero.popularItems.late, 6, 15).filter((c) => !early.includes(c) && !mid.includes(c));
  const cheapLate = late.filter((c) => (data.item(c)?.tier ?? 9) <= 3);
  const plan = statisticalAbilityPlan(hero);
  const lean = mix.weapon >= mix.spirit ? "weapon" : "spirit";
  const statProv = {
    kind: "statistical" as const,
    source: "GET /v1/assets/heroes popular_items (pick %, per phase)",
    interpretation: `Fundamentals/routes are the most-picked items per phase (pick rate ≥15–20%). Pick rate reflects popularity and affordability, not causal benefit. Sample lists: ${sample}.`,
    confidence: "low" as const,
  };
  const archetype: Archetype = {
    id: "popular",
    label: `Popular ${lean} build`,
    description: "Data-derived: most-picked items for this hero in the current data. Not reviewed.",
    damage: mix,
    range: "mid",
    fundamentals: early,
    routes: {
      stabilise: { items: [...mid.filter((c) => (data.item(c)?.slot ?? "") === "vitality"), ...mid.filter((c) => (data.item(c)?.slot ?? "") !== "vitality")], note: "Durability-first ordering of popular mid-game items." },
      normal: { items: [...mid, ...late], note: "Popular mid then late items." },
      ambitious: { items: [...late, ...mid], note: "Skips toward popular late items when ahead." },
    },
    avoid: [],
    abilityPlan: plan,
    abilityBranches: [],
    provenance: [statProv, plan.provenance],
  };
  if (!cheapLate.length && !mid.length && !early.length) archetype.description += " Insufficient popularity data.";
  const deps = new Set<string>([...early, ...mid, ...late]);
  return {
    heroId: hero.id,
    heroClass: hero.className,
    status: "auto",
    author: "auto (data-derived)",
    checkedBuild: data.build,
    archetypes: [archetype],
    threat: threatSignatureFromData(hero, data),
    dependsOn: [...deps].map((className) => ({ kind: "item" as const, className })),
  };
}
