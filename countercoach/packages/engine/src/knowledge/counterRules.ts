import type { ThreatKind } from "../state/types.js";
import type { Provenance } from "../types.js";
import type { Mechanic } from "./mechanics.js";

/**
 * Explicit counter relationships: threat -> mechanic -> valid responses -> conditions ->
 * exceptions. Response *weights* are heuristic judgement (how directly a mechanic answers the
 * threat). Whether an item *has* a mechanic is decided from current data (mechanics.ts), so a
 * patch that removes a property automatically removes the item from the response set.
 *
 * Deliberately NOT assumed:
 *  - that every control effect interrupts every ability (only stun is weighted as an
 *    interrupt, and silence only at low confidence);
 *  - that every debuff is cleansable (Divine Barrier's text says "non-stun debuffs");
 *  - that reductions of the same kind stack additively (duplicates are discounted);
 *  - that one ally owning an item fully covers a threat.
 */

export interface CounterResponse {
  mechanic: Mechanic;
  /** 0..1: how directly this mechanic answers the threat (heuristic). */
  weight: number;
  /** True when the effect must be applied to the enemy (uses application reliability). */
  offensive: boolean;
  note?: string;
}

export type ExceptionKind =
  /** I already own an item providing this response mechanic. */
  | "already_owned"
  /** An ally owns a response that reaches the same targets. */
  | "ally_covers"
  /** The threatening enemy was observed with CC immunity / cleanse tools. */
  | "target_has_cc_answer";

export interface CounterRule {
  id: string;
  threat: ThreatKind;
  /** Plain description of the threatening mechanic. */
  mechanic: string;
  responses: CounterResponse[];
  conditions: string[];
  exceptions: { kind: ExceptionKind; factor: number; note: string; appliesTo?: Mechanic[] }[];
  provenance: Provenance;
}

const H = (interpretation: string, confidence: Provenance["confidence"] = "medium"): Provenance => ({
  kind: "heuristic",
  source: "CounterCoach rules (docs/COUNTER_RULES.md); item mechanics from current item data",
  interpretation,
  checkedBuild: 6763,
  confidence,
});

/** Discount when I already own the same response (non-additive assumption). */
const OWNED_OFFENSIVE = { kind: "already_owned" as const, factor: 0.15, note: "Already applying this effect; a second source is assumed not to stack meaningfully." };
const OWNED_DEFENSIVE = { kind: "already_owned" as const, factor: 0.6, note: "Already have this defence; additional copies assumed to give diminishing returns." };
const ALLY = { kind: "ally_covers" as const, factor: 0.5, note: "An ally applies this; their coverage only counts when they reach the same targets." };

export const COUNTER_RULES: CounterRule[] = [
  {
    id: "weapon-sustained",
    threat: "weapon_damage",
    mechanic: "Sustained bullet damage from enemy weapons",
    responses: [
      { mechanic: "bullet_resist", weight: 1.0, offensive: false },
      { mechanic: "bullet_deflect", weight: 0.7, offensive: false },
      { mechanic: "barrier_vs_weapon_burst", weight: 0.6, offensive: false },
      { mechanic: "fire_rate_slow", weight: 0.6, offensive: true, note: "Must be applied to the shooter" },
      { mechanic: "outgoing_damage_reduction", weight: 0.55, offensive: true },
      { mechanic: "disarm", weight: 0.55, offensive: true, note: "Short window; requires landing it on the carry" },
      { mechanic: "bullet_immunity_active", weight: 0.5, offensive: false, note: "Short active; timing-dependent" },
      { mechanic: "max_health", weight: 0.3, offensive: false },
    ],
    conditions: ["Enemy damage is mainly bullets (kit, items or death recap)"],
    exceptions: [OWNED_DEFENSIVE, { ...ALLY, appliesTo: ["fire_rate_slow", "outgoing_damage_reduction", "disarm"] }],
    provenance: H("Resist answers sustained damage directly; debuffs on the shooter are weaker because they need application."),
  },
  {
    id: "weapon-burst",
    threat: "weapon_burst",
    mechanic: "Short windows of very high bullet damage",
    responses: [
      { mechanic: "bullet_immunity_active", weight: 1.0, offensive: false, note: "Become immune to bullets (active)" },
      { mechanic: "barrier_vs_weapon_burst", weight: 0.9, offensive: false, note: "Triggers on significant weapon damage" },
      { mechanic: "bullet_resist", weight: 0.6, offensive: false },
      { mechanic: "disarm", weight: 0.5, offensive: true },
    ],
    conditions: ["Deaths happen quickly to bullets (reported or death recap)"],
    exceptions: [OWNED_DEFENSIVE],
    provenance: H("Burst is best answered by reactive/active protection that covers the burst window."),
  },
  {
    id: "spirit-sustained",
    threat: "spirit_damage",
    mechanic: "Sustained spirit (ability) damage",
    responses: [
      { mechanic: "spirit_resist", weight: 1.0, offensive: false },
      { mechanic: "barrier_vs_spirit_burst", weight: 0.55, offensive: false },
      { mechanic: "spirit_burst_reduction", weight: 0.5, offensive: false },
      { mechanic: "silence", weight: 0.45, offensive: true, note: "Prevents casting while active; does not undo damage already applied" },
      { mechanic: "spell_parry", weight: 0.35, offensive: false, note: "Requires a timed parry" },
      { mechanic: "max_health", weight: 0.3, offensive: false },
    ],
    conditions: ["Enemy damage is mainly spirit (kit, items or death recap)"],
    exceptions: [OWNED_DEFENSIVE, { ...ALLY, appliesTo: ["silence"] }],
    provenance: H("Spirit resist answers sustained ability damage directly."),
  },
  {
    id: "spirit-burst",
    threat: "spirit_burst",
    mechanic: "Large single instances / combos of spirit damage",
    responses: [
      { mechanic: "spirit_burst_reduction", weight: 1.0, offensive: false, note: "Next instance of high spirit damage is significantly reduced" },
      { mechanic: "barrier_vs_spirit_burst", weight: 0.9, offensive: false },
      { mechanic: "spirit_resist", weight: 0.6, offensive: false },
      { mechanic: "spell_parry", weight: 0.45, offensive: false },
      { mechanic: "invulnerability_active", weight: 0.4, offensive: false, note: "Timing-dependent active" },
    ],
    conditions: ["Deaths happen quickly to spirit damage (reported or death recap)"],
    exceptions: [OWNED_DEFENSIVE],
    provenance: H("Burst protection reduces the first big hit; resist alone is slower to scale."),
  },
  {
    id: "melee",
    threat: "melee_damage",
    mechanic: "Heavy melee / melee-build damage",
    responses: [
      { mechanic: "melee_resist", weight: 1.0, offensive: false },
      { mechanic: "slow", weight: 0.35, offensive: true, note: "Kiting a melee threat" },
      { mechanic: "max_health", weight: 0.3, offensive: false },
    ],
    conditions: ["Enemy deals a large share of damage in melee"],
    exceptions: [OWNED_DEFENSIVE],
    provenance: H("Melee resist is the direct answer; parrying is a skill, not an item."),
  },
  {
    id: "anti-heal",
    threat: "enemy_healing",
    mechanic: "Enemy lifesteal, regeneration or healing abilities",
    responses: [
      { mechanic: "anti_heal", weight: 1.0, offensive: true, note: "Reduces incoming healing on the target" },
      { mechanic: "percent_hp_damage", weight: 0.25, offensive: true },
    ],
    conditions: [
      "Healing is significant: observed lifesteal/heal items, a sustain-heavy kit, or reported",
      "Your hero can apply the item's trigger (bullets, spirit damage, active) to the healer",
    ],
    exceptions: [
      { ...OWNED_OFFENSIVE, appliesTo: ["anti_heal"] },
      { ...ALLY, appliesTo: ["anti_heal"], note: "An ally already applies healing reduction; only counts when they fight the same healer." },
    ],
    provenance: H("Healing reduction only matters if healing is substantial and you can keep it applied; reductions are assumed not to stack."),
  },
  {
    id: "hard-cc",
    threat: "hard_cc",
    mechanic: "Stuns, sleeps and immobilises",
    responses: [
      { mechanic: "cc_immunity", weight: 0.9, offensive: false, note: "Unstoppable: 'Cannot be used while Stunned or Slept' – use before the CC lands" },
      { mechanic: "debuff_duration_reduction", weight: 0.6, offensive: false },
      { mechanic: "cc_reactive_barrier", weight: 0.45, offensive: false },
      { mechanic: "cleanse", weight: 0.25, offensive: false, note: "Divine Barrier removes non-stun debuffs only" },
    ],
    conditions: ["Enemy kit or items include hard CC that decides fights"],
    exceptions: [OWNED_DEFENSIVE],
    provenance: H("Pre-emptive immunity and duration reduction help against hard CC; cleanses listed as 'non-stun' do not remove stuns."),
  },
  {
    id: "silence",
    threat: "silence",
    mechanic: "Silences that shut down your abilities",
    responses: [
      { mechanic: "cleanse", weight: 0.65, offensive: false, note: "Non-stun debuff removal" },
      { mechanic: "debuff_duration_reduction", weight: 0.6, offensive: false },
      { mechanic: "cc_immunity", weight: 0.6, offensive: false },
      { mechanic: "cc_reactive_barrier", weight: 0.35, offensive: false },
    ],
    conditions: ["Your build relies on abilities", "Enemy kit or items silence"],
    exceptions: [OWNED_DEFENSIVE],
    provenance: H("Silence matters most to ability-reliant builds."),
  },
  {
    id: "slows",
    threat: "slow_kite",
    mechanic: "Heavy slows that let enemies kite or catch you",
    responses: [
      { mechanic: "debuff_duration_reduction", weight: 0.5, offensive: false },
      { mechanic: "cleanse", weight: 0.45, offensive: false },
      { mechanic: "mobility_self", weight: 0.4, offensive: false },
      { mechanic: "teleport_self", weight: 0.35, offensive: false },
      { mechanic: "move_speed", weight: 0.15, offensive: false },
    ],
    conditions: ["Enemy applies repeated slows"],
    exceptions: [OWNED_DEFENSIVE],
    provenance: H("Slow duration reduction and mobility reduce kiting; low confidence on exact value.", "low"),
  },
  {
    id: "debuffs",
    threat: "debuffs",
    mechanic: "Damage-over-time and stacked debuffs",
    responses: [
      { mechanic: "debuff_duration_reduction", weight: 0.7, offensive: false },
      { mechanic: "cleanse", weight: 0.65, offensive: false },
    ],
    conditions: ["Enemy relies on lingering debuffs"],
    exceptions: [OWNED_DEFENSIVE],
    provenance: H("Shorter or removed debuffs reduce their total value."),
  },
  {
    id: "mobility",
    threat: "mobility_escape",
    mechanic: "Highly mobile enemies escaping or diving",
    responses: [
      { mechanic: "immobilize", weight: 0.8, offensive: true },
      { mechanic: "movement_silence", weight: 0.75, offensive: true, note: "Silences movement-based items and abilities" },
      { mechanic: "pull_to_ground", weight: 0.6, offensive: true },
      { mechanic: "slow", weight: 0.55, offensive: true },
      { mechanic: "stun", weight: 0.5, offensive: true },
    ],
    conditions: ["Enemy repeatedly escapes or dives with dashes/flight"],
    exceptions: [{ ...ALLY, appliesTo: ["immobilize", "movement_silence", "slow"] }, { kind: "target_has_cc_answer", factor: 0.7, note: "Target was seen with CC immunity or cleanse." }],
    provenance: H("Movement control answers mobility; requires landing it."),
  },
  {
    id: "stealth",
    threat: "stealth",
    mechanic: "Stealth / invisibility",
    responses: [{ mechanic: "reveal", weight: 0.6, offensive: true, note: "Wounded enemies are revealed through walls" }],
    conditions: ["Enemy uses stealth to engage or escape"],
    exceptions: [ALLY],
    provenance: H("Few item answers exist in current data; low confidence.", "low"),
  },
  {
    id: "channel-ult",
    threat: "channel_ultimate",
    mechanic: "Channelled ultimates that decide teamfights",
    responses: [
      { mechanic: "stun", weight: 0.8, offensive: true, note: "Stun is treated as an interrupt (community understanding, unverified per ability)" },
      { mechanic: "sleep", weight: 0.5, offensive: true },
      { mechanic: "silence", weight: 0.35, offensive: true, note: "Whether silence cancels an ongoing channel is not verified" },
    ],
    conditions: ["An enemy ultimate is channelled (ability data includes channel time)"],
    exceptions: [ALLY, { kind: "target_has_cc_answer", factor: 0.6, note: "Target was seen with CC immunity." }],
    provenance: H("Only stun is weighted strongly as an interrupt; slows, disarms and displacement are not assumed to interrupt.", "low"),
  },
  {
    id: "bullet-resist-stacking",
    threat: "bullet_resist_stacking",
    mechanic: "Enemies stacking bullet resist",
    responses: [{ mechanic: "bullet_resist_shred", weight: 1.0, offensive: true }],
    conditions: ["You deal mainly weapon damage", "Enemies were seen buying bullet resist"],
    exceptions: [{ ...OWNED_OFFENSIVE, factor: 0.35 }, ALLY],
    provenance: H("Resist reduction recovers damage lost to stacked resist; partial stacking assumed."),
  },
  {
    id: "spirit-resist-stacking",
    threat: "spirit_resist_stacking",
    mechanic: "Enemies stacking spirit resist",
    responses: [
      { mechanic: "spirit_resist_shred", weight: 1.0, offensive: true },
      { mechanic: "spirit_amp", weight: 0.5, offensive: true },
    ],
    conditions: ["You deal mainly spirit damage", "Enemies were seen buying spirit resist"],
    exceptions: [{ ...OWNED_OFFENSIVE, factor: 0.35 }, ALLY],
    provenance: H("Resist reduction recovers damage lost to stacked resist; partial stacking assumed."),
  },
  {
    id: "tanky",
    threat: "tanky_targets",
    mechanic: "Very high health targets",
    responses: [
      { mechanic: "percent_hp_damage", weight: 0.8, offensive: true },
      { mechanic: "bullet_resist_shred", weight: 0.35, offensive: true },
      { mechanic: "spirit_resist_shred", weight: 0.35, offensive: true },
    ],
    conditions: ["Enemies built large health pools"],
    exceptions: [OWNED_OFFENSIVE],
    provenance: H("Percentage-health damage scales with target health."),
  },
  {
    id: "percent-hp",
    threat: "percent_hp_damage",
    mechanic: "Enemy percentage-health damage",
    responses: [
      { mechanic: "debuff_duration_reduction", weight: 0.45, offensive: false },
      { mechanic: "cleanse", weight: 0.4, offensive: false },
      { mechanic: "spirit_resist", weight: 0.4, offensive: false },
    ],
    conditions: ["Enemy applies %HP damage over time"],
    exceptions: [OWNED_DEFENSIVE],
    provenance: H("Extra health is a weak answer to %HP damage; shortening the debuff helps.", "low"),
  },
];

export function rulesForThreat(t: ThreatKind): CounterRule[] {
  return COUNTER_RULES.filter((r) => r.threat === t);
}
