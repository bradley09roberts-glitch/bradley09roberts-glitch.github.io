import type { Ability, Item, ItemProperty } from "../types.js";

/**
 * Mechanic tags derived from current item/ability data. Detection is data-driven (property
 * names, provided modifier types, shop filters) with a small number of description-text
 * detections, each labelled by how it was detected. Nothing here hard-codes item names, so a
 * patch that removes or changes a property changes the tags automatically.
 */

export type Mechanic =
  // defensive
  | "bullet_resist"
  | "spirit_resist"
  | "melee_resist"
  | "max_health"
  | "barrier"
  | "barrier_vs_weapon_burst"
  | "barrier_vs_spirit_burst"
  | "bullet_immunity_active"
  | "spirit_burst_reduction"
  | "invulnerability_active"
  | "bullet_deflect"
  | "debuff_duration_reduction"
  | "cleanse"
  | "cc_immunity"
  | "cc_reactive_barrier"
  | "spell_parry"
  | "regen"
  | "heal_amp_self"
  | "lifesteal_bullet"
  | "lifesteal_spirit"
  | "heal_ally"
  // offensive utility / control
  | "anti_heal"
  | "stun"
  | "silence"
  | "movement_silence"
  | "disarm"
  | "sleep"
  | "slow"
  | "immobilize"
  | "pull_to_ground"
  | "fire_rate_slow"
  | "outgoing_damage_reduction"
  | "bullet_resist_shred"
  | "spirit_resist_shred"
  | "spirit_amp"
  | "percent_hp_damage"
  | "reveal"
  // self power / mobility
  | "stealth_self"
  | "teleport_self"
  | "mobility_self"
  | "weapon_damage"
  | "spirit_power"
  | "fire_rate"
  | "ammo"
  | "cooldown_reduction"
  | "ability_range"
  | "ability_duration"
  | "ability_charges"
  | "melee_damage"
  | "move_speed"
  | "stamina";

export type Detection = "property" | "filter" | "text";

export interface MechanicHit {
  mechanic: Mechanic;
  via: Detection;
  /** Property name / filter / text fragment that triggered the detection. */
  detail: string;
}

/** How an item's effect reaches its target. Determines whether a hero can apply it reliably. */
export type Application =
  | "bullets"
  | "headshots"
  | "spirit_damage"
  | "ultimate_damage"
  | "melee"
  | "parry"
  | "active_target"
  | "active_self"
  | "active_area"
  | "aura"
  /** Applied automatically to whoever attacks you. */
  | "reactive"
  | "passive_self";

interface PropRule {
  mechanic: Mechanic;
  /** Property name regex. */
  name?: RegExp;
  /** Provided modifier type regex. */
  type?: RegExp;
  sign: "pos" | "neg" | "any";
}

const PROP_RULES: PropRule[] = [
  { mechanic: "bullet_resist", type: /^MODIFIER_VALUE_BULLET_ARMOR_DAMAGE_RESIST$/, sign: "pos" },
  { mechanic: "spirit_resist", type: /^MODIFIER_VALUE_TECH_RESIST$/, sign: "pos" },
  { mechanic: "spirit_resist", name: /^TechArmorDamageReduction$/, sign: "pos" },
  { mechanic: "melee_resist", type: /^MODIFIER_VALUE_MELEE_RESIST$/, sign: "pos" },
  { mechanic: "melee_resist", name: /^MeleeResistPercent$/, sign: "pos" },
  { mechanic: "max_health", type: /^MODIFIER_VALUE_(HEALTH_MAX|HEALTH_MAX_PERCENT|BASE_HEALTH_PERCENT)$/, sign: "pos" },
  { mechanic: "barrier", name: /Barrier$|^BarrierHealth|CombatBarrier/, sign: "pos" },
  { mechanic: "barrier", type: /^MODIFIER_VALUE_BARRIER_HEALTH$/, sign: "pos" },
  { mechanic: "spirit_burst_reduction", name: /^(SpellbreakerDamageReduction|HighSpiritDamageReduction)/, sign: "any" },
  { mechanic: "debuff_duration_reduction", type: /^MODIFIER_VALUE_STATUS_RESISTANCE$/, sign: "pos" },
  { mechanic: "regen", type: /^MODIFIER_VALUE_(HEALTH_REGEN_PER_SECOND|OUT_OF_COMBAT_HEALTH_REGEN)$/, sign: "pos" },
  { mechanic: "regen", name: /^BonusHealthRegen$/, sign: "pos" },
  { mechanic: "heal_amp_self", type: /^MODIFIER_VALUE_HEAL_AMP_(CAST|REGEN)_PERCENT$/, sign: "pos" },
  { mechanic: "heal_amp_self", name: /^HealAmp(Cast|Regen)Percent$/, sign: "pos" },
  { mechanic: "lifesteal_bullet", type: /^MODIFIER_VALUE_BULLET_LIFESTEAL$/, sign: "pos" },
  { mechanic: "lifesteal_bullet", name: /^BulletLifestealPercent$|^LifestealHeal/, sign: "pos" },
  { mechanic: "lifesteal_spirit", type: /^MODIFIER_VALUE_TECH_LIFESTEAL$/, sign: "pos" },
  { mechanic: "lifesteal_spirit", name: /^AbilityLifestealPercentHero$/, sign: "pos" },
  { mechanic: "anti_heal", name: /^HealAmpReceivePenaltyPercent$|^HealAmpRegenPenaltyPercent$/, sign: "neg" },
  { mechanic: "anti_heal", type: /^MODIFIER_VALUE_HEAL_AMP_RECEIVE_PERCENT$/, sign: "neg" },
  { mechanic: "stun", name: /^StunDuration$|^StompStunDuration$/, sign: "pos" },
  { mechanic: "silence", name: /^SilenceDuration$/, sign: "pos" },
  { mechanic: "disarm", name: /^DisarmDuration$/, sign: "pos" },
  { mechanic: "sleep", name: /^SleepDuration$/, sign: "pos" },
  { mechanic: "slow", name: /^(SlowPercent|MovementSpeedSlow|MaxSlowPercent|MoveSlowPercent|EnemySlowPct)$/, sign: "pos" },
  { mechanic: "slow", type: /^MODIFIER_VALUE_MOVEMENT_SPEED_SLOW_PERCENT$/, sign: "pos" },
  { mechanic: "immobilize", name: /^(ImmobilizeDuration|RootDuration)$/, sign: "pos" },
  { mechanic: "fire_rate_slow", name: /^FireRateSlow$/, sign: "pos" },
  { mechanic: "fire_rate_slow", type: /^MODIFIER_VALUE_FIRE_RATE_SLOW$/, sign: "pos" },
  { mechanic: "bullet_resist_shred", type: /^MODIFIER_VALUE_BULLET_AND_MELEE_RESIST_REDUCTION$/, sign: "any" },
  { mechanic: "spirit_resist_shred", type: /^MODIFIER_VALUE_TECH_RESIST_REDUCTION$/, sign: "any" },
  { mechanic: "percent_hp_damage", name: /^(DotHealthPercent|CurrentHealthDamagePercentage|MaxHealthDamage|BleedDamagePercent)/, sign: "pos" },
  { mechanic: "weapon_damage", type: /^MODIFIER_VALUE_(WEAPON_DAMAGE_INCREASE|CLOSE_RANGE_WEAPON_DAMAGE_INCREASE|LONG_RANGE_BULLET_DAMAGE_INCREASE)$/, sign: "pos" },
  { mechanic: "spirit_power", type: /^MODIFIER_VALUE_(TECH_POWER|TECH_POWER_PERCENT)$/, sign: "pos" },
  { mechanic: "fire_rate", type: /^MODIFIER_VALUE_FIRE_RATE$/, sign: "pos" },
  { mechanic: "ammo", type: /^MODIFIER_VALUE_AMMO_CLIP_SIZE(_PERCENT)?$/, sign: "pos" },
  { mechanic: "cooldown_reduction", type: /^MODIFIER_VALUE_(COOLDOWN_REDUCTION_PERCENTAGE|ULTIMATE_COOLDOWN_REDUCTION_PERCENTAGE|ITEM_COOLDOWN_REDUCTION_PERCENTAGE)$/, sign: "pos" },
  { mechanic: "ability_range", type: /^MODIFIER_VALUE_TECH_(RANGE|RADIUS)_PERCENT$/, sign: "pos" },
  { mechanic: "ability_duration", type: /^MODIFIER_VALUE_BONUS_ABILITY_DURATION_PERCENTAGE$/, sign: "pos" },
  { mechanic: "ability_charges", type: /^MODIFIER_VALUE_BONUS_ABILITY_CHARGES$/, sign: "pos" },
  { mechanic: "melee_damage", type: /^MODIFIER_VALUE_MELEE_DAMAGE_INCREASE$/, sign: "pos" },
  { mechanic: "move_speed", type: /^MODIFIER_VALUE_(MOVEMENT_SPEED_MAX|SPRINT_SPEED_BONUS)$/, sign: "pos" },
  { mechanic: "stamina", type: /^MODIFIER_VALUE_STAMINA(_REGEN_PER_SECOND_PERCENTAGE)?$/, sign: "pos" },
];

const FILTER_RULES: Record<string, Mechanic[]> = {
  anti_cc: ["cc_immunity"],
  invulnerability: ["invulnerability_active"],
  debuff_resist: ["debuff_duration_reduction"],
  stealth: ["stealth_self"],
  teleport: ["teleport_self"],
  jump_and_dash: ["mobility_self"],
  status_immobilize: ["immobilize"],
  status_stun: ["stun"],
  status_grounded: ["pull_to_ground"],
  bullet_vuln: ["bullet_resist_shred"],
  spirit_vuln: ["spirit_resist_shred"],
  physical_resist: ["bullet_deflect"],
};

/** Description phrases. Each is a direct quote fragment from current item text. */
const TEXT_RULES: { re: RegExp; mechanic: Mechanic }[] = [
  { re: /become immune to bullets/i, mechanic: "bullet_immunity_active" },
  { re: /next instance of high spirit damage you take is significantly reduced/i, mechanic: "spirit_burst_reduction" },
  { re: /barrier whenever you take significant weapon damage/i, mechanic: "barrier_vs_weapon_burst" },
  { re: /barrier whenever you take significant spirit damage/i, mechanic: "barrier_vs_spirit_burst" },
  { re: /become immune to stun, silence, sleep, root, and disarm/i, mechanic: "cc_immunity" },
  { re: /remove all non-stun debuffs/i, mechanic: "cleanse" },
  { re: /gain a barrier when you are stunned, chained, immobilized, slept or silenced/i, mechanic: "cc_reactive_barrier" },
  { re: /parry protects you from the damage and effects of enemy abilities/i, mechanic: "spell_parry" },
  { re: /reduce the target's outgoing damage/i, mechanic: "outgoing_damage_reduction" },
  { re: /disarms enemy target|speed reduction and disarm/i, mechanic: "disarm" },
  { re: /\bsilences? their movement-based items and abilities/i, mechanic: "movement_silence" },
  { re: /deflect incoming bullets/i, mechanic: "bullet_deflect" },
  { re: /revealed through walls/i, mechanic: "reveal" },
  { re: /\bheals? a target allied hero/i, mechanic: "heal_ally" },
  { re: /spirit amp/i, mechanic: "spirit_amp" },
  { re: /lose a percentage of their max health/i, mechanic: "percent_hp_damage" },
];

function signOk(p: ItemProperty, sign: PropRule["sign"]): boolean {
  if (sign === "any") return p.value !== 0;
  return sign === "pos" ? p.value > 0 : p.value < 0;
}

export function detectMechanics(props: Record<string, ItemProperty>, filters: string[], text: string): MechanicHit[] {
  const hits: MechanicHit[] = [];
  const seen = new Set<string>();
  const add = (h: MechanicHit) => {
    const k = `${h.mechanic}`;
    if (seen.has(k)) return;
    seen.add(k);
    hits.push(h);
  };
  for (const [name, p] of Object.entries(props)) {
    for (const r of PROP_RULES) {
      const nameOk = r.name ? r.name.test(name) : false;
      const typeOk = r.type && p.providedType ? r.type.test(p.providedType) : false;
      if ((nameOk || typeOk) && signOk(p, r.sign)) add({ mechanic: r.mechanic, via: "property", detail: name });
    }
  }
  for (const f of filters) for (const m of FILTER_RULES[f] ?? []) add({ mechanic: m, via: "filter", detail: f });
  for (const r of TEXT_RULES) {
    const m = text.match(r.re);
    if (m) add({ mechanic: r.mechanic, via: "text", detail: m[0] });
  }
  return hits;
}

const itemCache = new WeakMap<Item, MechanicHit[]>();
export function itemMechanics(item: Item): MechanicHit[] {
  let v = itemCache.get(item);
  if (!v) {
    v = detectMechanics(item.properties, item.shopFilters, item.text);
    itemCache.set(item, v);
  }
  return v;
}

export function itemHas(item: Item, m: Mechanic): boolean {
  return itemMechanics(item).some((h) => h.mechanic === m);
}

/**
 * How an item's offensive effect is applied, from activation type and description wording.
 * Used to judge whether a hero can apply it reliably.
 */
export function itemApplication(item: Item): Application[] {
  const t = item.text.toLowerCase();
  const out = new Set<Application>();
  if (/headshot/.test(t)) out.add("headshots");
  if (/enemies that shoot you|towards any attacker/.test(t)) out.add("reactive");
  else if (/your bullets|bullets build up|weapon damage at close range|landing a headshot/.test(t)) out.add("bullets");
  if (/spirit damage (reduces|applies)|when (the target|you) (takes?|deal) spirit damage|dealing (significant )?spirit damage/.test(t)) out.add("spirit_damage");
  if (/your ultimate/.test(t)) out.add("ultimate_damage");
  if (/\bparry\b/.test(t)) out.add("parry");
  if (/nearby enemies/.test(t)) out.add("aura");
  if (item.activation === "press") out.add("active_target");
  if (item.activation === "instant_cast" || item.activation === "instant_cast_toggle") {
    out.add(/release an expanding|nearby enemies|enemies it hits|targets it hits/.test(t) ? "active_area" : "active_self");
  }
  if (!out.size) out.add(item.slot === "weapon" ? "bullets" : "passive_self");
  return [...out];
}

// ---------------------------------------------------------------------------------------------
// Ability mechanics (used for auto-derived hero threat signatures)
// ---------------------------------------------------------------------------------------------

const ABILITY_RULES: { re: RegExp; mechanic: Mechanic | "heal_self" | "channel" | "displacement" | "dash" | "flight" | "invis" }[] = [
  { re: /^(StunDuration|StompStunDuration)$/, mechanic: "stun" },
  { re: /^(SilenceDuration|SilenceDebuff)$/, mechanic: "silence" },
  { re: /^SleepDuration$/, mechanic: "sleep" },
  { re: /^(ImmobilizeDuration|TetherDuration|PullDuration)$/, mechanic: "immobilize" },
  { re: /^(SlowPercent|MoveSlowPercent|EnemySlowPct|MovementSlowPct|AuraSlowAmount|SpinSlowPercent|ClubSlowPercent|PunchRollSlow|MoveSpeedSlowPct)$/, mechanic: "slow" },
  { re: /^(FireRateSlow|MaxFireRateSlowPercent)$/, mechanic: "fire_rate_slow" },
  { re: /^HealAmpReceivePenaltyPercent$/, mechanic: "anti_heal" },
  { re: /^(HealAmount|HealingPerSecond|HealMaxHealthPercent|HealFixedHealth|HealMaxHealth|MissingHealthPercentHeal|MissingHPHeal|HeartHeal|HealingFactor|HealingPerGlub|BonusHealthRegen|MaxHealthRegen|ExternalBonusHealthRegen|HealPctVsHeroes|DamageHealMult|HealthStealPctHero)$/, mechanic: "heal_self" },
  { re: /^(LifeStealPercentOnHit|LifestealPercentHero|BulletLifestealPercent|BulletLifesteal|TechLifestealPercent|BeamLifesteal|MeleeLifesteal|AbilityLifestealPercentHero|LifeDrainHealthMult)$/, mechanic: "heal_self" },
  { re: /^AbilityChannelTime$/, mechanic: "channel" },
  { re: /^(PushForce|KnockForce|PushBackForce|LiftHeight|LiftDuration|LightningStrikeKnockBackForce|PullDistance)$/, mechanic: "displacement" },
  { re: /^(DashSpeed|DashRange|DashAirSpeed)$/, mechanic: "dash" },
  { re: /^(FlightControlEnabled|MaxFlyHeight)$/, mechanic: "flight" },
  { re: /^(InvisFadeToDuration|FullInvisDistance)$/, mechanic: "invis" },
  { re: /^(CombatBarrier|BarrierDuration)$/, mechanic: "barrier" },
  { re: /^(DotHealthPercent|CurrentHealthDamagePercentage|MaxHealthDamage|DPSPercentHealth|FullRageCurrentHealthDamagePct)$/, mechanic: "percent_hp_damage" },
  { re: /^PurgeOnCast$|^PurgeDebuffs$/, mechanic: "cleanse" },
];

export type AbilityMechanic = (typeof ABILITY_RULES)[number]["mechanic"];

export function abilityMechanics(a: Ability): { mechanic: AbilityMechanic; property: string; fromTier: 0 | 1 | 2 | 3 }[] {
  const out: { mechanic: AbilityMechanic; property: string; fromTier: 0 | 1 | 2 | 3 }[] = [];
  const seen = new Set<string>();
  const consider = (name: string, tier: 0 | 1 | 2 | 3) => {
    for (const r of ABILITY_RULES) {
      if (r.re.test(name) && !seen.has(r.mechanic)) {
        seen.add(r.mechanic);
        out.push({ mechanic: r.mechanic, property: name, fromTier: tier });
      }
    }
  };
  for (const name of Object.keys(a.properties)) consider(name, 0);
  for (const t of a.tiers) for (const u of t.upgrades) consider(u.property, t.tier);
  return out;
}

/**
 * Heuristic magnitude of a mechanic on an item, normalised so 1.0 is a typical dedicated item.
 * Reference values are author judgement (documented in docs/SCORING.md); conditional
 * properties (e.g. "below 50% health") are discounted. Binary mechanics return 1.
 */
const MAGNITUDE_REFERENCE: Partial<Record<Mechanic, number>> = {
  bullet_resist: 25,
  spirit_resist: 25,
  melee_resist: 25,
  max_health: 400,
  barrier: 400,
  regen: 4,
};

export function mechanicMagnitude(item: Item, m: Mechanic): number {
  const hit = itemMechanics(item).find((h) => h.mechanic === m);
  if (!hit) return 0;
  const ref = MAGNITUDE_REFERENCE[m];
  if (!ref || hit.via !== "property") return 1;
  let best = 0;
  for (const [name, p] of Object.entries(item.properties)) {
    for (const h of detectMechanics({ [name]: p }, [], "")) {
      if (h.mechanic !== m) continue;
      const v = (Math.abs(p.value) / ref) * (p.conditional ? 0.6 : 1);
      if (v > best) best = v;
    }
  }
  return Math.min(1.6, Math.max(0.15, best));
}
