import type { GameData } from "../data/gameData.js";
import { itemHas } from "../knowledge/mechanics.js";
import type { ProfileRegistry } from "../knowledge/profiles/index.js";
import { readField } from "../state/matchStore.js";
import type { EnemyItemsValue, MatchState, Phase, ThreatKind } from "../state/types.js";

export type ThreatEvidence = "roster-only" | "observed-items" | "reported";

export interface ThreatContribution {
  source: "kit" | "items" | "report" | "death-recap";
  heroId: number | null;
  amount: number;
  detail: string;
}

export interface AssessedThreat {
  kind: ThreatKind;
  /** 0..1 strength index (combined evidence). NOT a probability. */
  strength: number;
  evidence: ThreatEvidence;
  urgent: boolean;
  contributions: ThreatContribution[];
  /** Hero ids contributing (for applicability and lane plan). */
  heroIds: number[];
  laneRelevant: boolean;
}

export interface ThreatAssessment {
  threats: AssessedThreat[];
  /** Enemies observed with CC immunity / cleanse tools. */
  ccAnswerHeroIds: number[];
  phase: Phase;
  laneOpponents: number[];
  notes: string[];
}

export const THREAT_LABEL: Record<ThreatKind, string> = {
  weapon_damage: "Weapon damage",
  spirit_damage: "Spirit damage",
  melee_damage: "Melee damage",
  spirit_burst: "Spirit burst",
  weapon_burst: "Weapon burst",
  enemy_healing: "Enemy healing",
  hard_cc: "Stuns / sleeps / roots",
  silence: "Silences",
  slow_kite: "Slows",
  debuffs: "Damage-over-time debuffs",
  mobility_escape: "Mobile enemies",
  stealth: "Stealth",
  channel_ultimate: "Channelled ultimate",
  tanky_targets: "Tanky targets",
  bullet_resist_stacking: "Bullet resist stacking",
  spirit_resist_stacking: "Spirit resist stacking",
  percent_hp_damage: "%HP damage",
};

const KIT_BASE: Partial<Record<ThreatKind, number>> = {
  hard_cc: 0.12,
  silence: 0.12,
  slow_kite: 0.08,
  debuffs: 0.1,
  mobility_escape: 0.08,
  stealth: 0.1,
  channel_ultimate: 0.15,
  percent_hp_damage: 0.1,
};

function combine(amounts: number[]): number {
  let p = 1;
  for (const a of amounts) p *= 1 - Math.min(0.9, Math.max(0, a));
  return Math.round((1 - p) * 100) / 100;
}

/** Game-phase from explicit input, else from the match clock (heuristic boundaries). */
export function resolvePhase(state: MatchState, now: number, gameTime: number | null): { phase: Phase; source: "user" | "clock" | "default" } {
  const ph = readField(state, "match.phase", now);
  if (ph) return { phase: ph.value, source: "user" };
  if (gameTime != null) return { phase: gameTime < 540 ? "lane" : gameTime < 1500 ? "mid" : "late", source: "clock" };
  return { phase: "mid", source: "default" };
}

export function assessThreats(
  state: MatchState,
  data: GameData,
  profiles: ProfileRegistry,
  now: number,
  phase: Phase,
): ThreatAssessment {
  const contrib = new Map<ThreatKind, ThreatContribution[]>();
  const add = (k: ThreatKind, c: ThreatContribution) => {
    if (c.amount <= 0) return;
    const arr = contrib.get(k) ?? [];
    arr.push(c);
    contrib.set(k, arr);
  };
  const enemies = readField(state, "roster.enemies", now)?.value ?? [];
  const lane = readField(state, "roster.lane", now)?.value ?? [];
  const notes: string[] = [];
  const relevance = (heroId: number): number => {
    if (phase !== "lane") return 1;
    if (!lane.length) return 0.6;
    return lane.includes(heroId) ? 1 : 0.3;
  };
  const ccAnswer = new Set<number>();

  for (const id of enemies) {
    const hero = data.hero(id);
    const prof = profiles.get(id);
    if (!hero || !prof) continue;
    const r = relevance(id);
    const sig = prof.threat;
    const dmgScale = 0.3 * r;
    add("weapon_damage", { source: "kit", heroId: id, amount: dmgScale * sig.damage.weapon, detail: `${hero.name} kit/build: ${Math.round(sig.damage.weapon * 100)}% weapon` });
    add("spirit_damage", { source: "kit", heroId: id, amount: dmgScale * sig.damage.spirit, detail: `${hero.name} kit/build: ${Math.round(sig.damage.spirit * 100)}% spirit` });
    add("melee_damage", { source: "kit", heroId: id, amount: dmgScale * sig.damage.melee, detail: `${hero.name} kit/build: ${Math.round(sig.damage.melee * 100)}% melee` });
    if (sig.burst === "high") {
      if (sig.damage.weapon >= 0.5) add("weapon_burst", { source: "kit", heroId: id, amount: 0.15 * r, detail: `${hero.name}: high weapon burst kit` });
      if (sig.damage.spirit >= 0.5) add("spirit_burst", { source: "kit", heroId: id, amount: 0.15 * r, detail: `${hero.name}: high spirit burst kit` });
    }
    const sus = sig.sustain === "high" ? 0.25 : sig.sustain === "medium" ? 0.12 : 0.04;
    add("enemy_healing", { source: "kit", heroId: id, amount: sus * r, detail: `${hero.name}: ${sig.sustain} sustain kit` });
    for (const t of sig.threats) {
      const base = KIT_BASE[t];
      if (base) add(t, { source: "kit", heroId: id, amount: base * r, detail: `${hero.name} kit` });
    }

    // Observed items (visible on scoreboard / in fights). Unobserved ≠ owns nothing.
    const obs = readField(state, `enemyItems:${id}`, now);
    if (obs) {
      const v = obs.value as EnemyItemsValue;
      const fresh = obs.freshness === "stale" ? 0.6 : 1;
      let heal = 0;
      for (const cn of v.items) {
        const it = data.item(cn);
        if (!it) continue;
        const f = fresh * r;
        if (itemHas(it, "lifesteal_bullet") || itemHas(it, "lifesteal_spirit") || itemHas(it, "heal_amp_self")) heal += 0.22;
        else if (itemHas(it, "regen") && it.tier >= 3) heal += 0.1;
        if (itemHas(it, "spirit_power") && it.slot === "spirit") add("spirit_damage", { source: "items", heroId: id, amount: 0.07 * f, detail: `${hero.name} owns ${it.name}` });
        if ((itemHas(it, "weapon_damage") || itemHas(it, "fire_rate")) && it.slot === "weapon") add("weapon_damage", { source: "items", heroId: id, amount: 0.07 * f, detail: `${hero.name} owns ${it.name}` });
        if (itemHas(it, "bullet_resist") && it.slot === "vitality") add("bullet_resist_stacking", { source: "items", heroId: id, amount: 0.15 * f, detail: `${hero.name} owns ${it.name}` });
        if (itemHas(it, "spirit_resist") && it.slot === "vitality") add("spirit_resist_stacking", { source: "items", heroId: id, amount: 0.15 * f, detail: `${hero.name} owns ${it.name}` });
        if (itemHas(it, "max_health") && it.tier >= 3 && it.slot === "vitality") add("tanky_targets", { source: "items", heroId: id, amount: 0.08 * f, detail: `${hero.name} owns ${it.name}` });
        if ((itemHas(it, "stun") || itemHas(it, "immobilize")) && it.isActive) add("hard_cc", { source: "items", heroId: id, amount: 0.12 * f, detail: `${hero.name} owns ${it.name}` });
        if (itemHas(it, "silence")) add("silence", { source: "items", heroId: id, amount: 0.15 * f, detail: `${hero.name} owns ${it.name}` });
        if (itemHas(it, "percent_hp_damage")) add("percent_hp_damage", { source: "items", heroId: id, amount: 0.12 * f, detail: `${hero.name} owns ${it.name}` });
        if (itemHas(it, "cc_immunity") || itemHas(it, "cleanse")) ccAnswer.add(id);
      }
      if (heal > 0) add("enemy_healing", { source: "items", heroId: id, amount: Math.min(0.5, heal) * fresh * r, detail: `${hero.name} owns healing items` });
    }
  }

  // User reports (decay with a 5-minute half-life of game/wall time).
  const gtNow = readField(state, "match.gameTime", now);
  for (const rep of state.threatReports) {
    const ageS = rep.gameTime != null && gtNow ? Math.max(0, (gtNow.value as number) + gtNow.ageS - rep.gameTime) : Math.max(0, (now - rep.observedAt) / 1000);
    const decay = Math.pow(0.5, ageS / 300);
    const amt = (rep.severity === "major" ? 0.6 : 0.3) * decay;
    add(rep.kind, { source: "report", heroId: rep.sourceHeroId, amount: amt, detail: `You reported: ${rep.note || THREAT_LABEL[rep.kind]}${decay < 0.6 ? " (older report)" : ""}` });
  }

  // Death recap (player-visible), decays with a 4-minute half-life.
  const recap = readField(state, "me.deathRecap", now);
  if (recap) {
    const decay = Math.pow(0.5, recap.ageS / 240);
    const r = recap.value;
    const tot = Math.max(1, r.weaponPct + r.spiritPct + r.meleePct);
    const share = { w: r.weaponPct / tot, s: r.spiritPct / tot, m: r.meleePct / tot };
    const ids = r.killerHeroIds;
    add("weapon_damage", { source: "death-recap", heroId: ids[0] ?? null, amount: 0.55 * share.w * decay, detail: `Death recap: ${Math.round(share.w * 100)}% weapon` });
    add("spirit_damage", { source: "death-recap", heroId: ids[0] ?? null, amount: 0.55 * share.s * decay, detail: `Death recap: ${Math.round(share.s * 100)}% spirit` });
    add("melee_damage", { source: "death-recap", heroId: ids[0] ?? null, amount: 0.55 * share.m * decay, detail: `Death recap: ${Math.round(share.m * 100)}% melee` });
    if (share.w >= 0.7) add("weapon_burst", { source: "death-recap", heroId: ids[0] ?? null, amount: 0.3 * decay, detail: "Death recap mostly weapon damage" });
    if (share.s >= 0.7) add("spirit_burst", { source: "death-recap", heroId: ids[0] ?? null, amount: 0.3 * decay, detail: "Death recap mostly spirit damage" });
  }

  const threats: AssessedThreat[] = [];
  for (const [kind, cs] of contrib) {
    const strength = combine(cs.map((c) => c.amount));
    if (strength < 0.05) continue;
    // Evidence comes only from contributions large enough to matter (a 5% death-recap share
    // must not upgrade a threat to "reported").
    const material = cs.filter((c) => c.amount >= 0.08);
    const evidence: ThreatEvidence = material.some((c) => c.source === "report" || c.source === "death-recap")
      ? "reported"
      : material.some((c) => c.source === "items")
        ? "observed-items"
        : "roster-only";
    const heroIds = [...new Set(cs.map((c) => c.heroId).filter((x): x is number => x != null))];
    threats.push({
      kind,
      strength,
      evidence,
      urgent: strength >= 0.55 && evidence !== "roster-only",
      contributions: cs.sort((a, b) => b.amount - a.amount),
      heroIds,
      laneRelevant: lane.length ? heroIds.some((h) => lane.includes(h)) : phase === "lane",
    });
  }
  threats.sort((a, b) => b.strength - a.strength);
  if (!enemies.length) notes.push("No enemy roster entered: threat-based advice is off.");
  if (phase === "lane" && !lane.length && enemies.length) notes.push("Lane opponents not set: all enemies weighted equally in lane.");
  return { threats, ccAnswerHeroIds: [...ccAnswer], phase, laneOpponents: lane, notes };
}
