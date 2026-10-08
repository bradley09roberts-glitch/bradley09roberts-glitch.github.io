import type { Provenance } from "../../types.js";
import type { Archetype, HeroProfile, UpgradeStep } from "./types.js";

/**
 * Curated profiles. Authored for build 6763 from:
 *  - ability tier text in the current snapshot (quoted in `why`),
 *  - the current-window most-played ability order (as a baseline),
 *  - current popular items (to keep routes realistic),
 *  - author judgement for archetypes, breakpoints and route ordering.
 * Status "curated" = every reference checked against data by tests; NOT reviewed by an
 * expert player. Treat route ordering and breakpoints as informed heuristics.
 */

const AUTHOR = "CounterCoach (AI-authored, data-checked; awaiting expert review)";
const BUILD = 6763;

const prov = (interpretation: string, kind: Provenance["kind"] = "heuristic", confidence: Provenance["confidence"] = "medium"): Provenance => ({
  kind,
  source: kind === "statistical" ? "/v1/analytics/ability-order-stats + /v1/assets/heroes popular_items (build 6763 window)" : "Curated profile (packages/engine/src/knowledge/profiles/curated.ts)",
  interpretation,
  checkedBuild: BUILD,
  confidence,
});

const s = (ability: string, tier: 1 | 2 | 3, why = "", breakpoint = false): UpgradeStep => ({ ability, tier, why, ...(breakpoint ? { breakpoint } : {}) });

function deps(p: Omit<HeroProfile, "dependsOn">): HeroProfile {
  const set = new Map<string, { kind: "item" | "ability"; className: string }>();
  for (const a of p.archetypes) {
    for (const c of [...a.fundamentals, ...a.avoid, ...a.routes.stabilise.items, ...a.routes.normal.items, ...a.routes.ambitious.items]) {
      set.set(`item:${c}`, { kind: "item", className: c });
    }
    for (const c of a.abilityPlan.unlockOrder) set.set(`ability:${c}`, { kind: "ability", className: c });
  }
  return { ...p, dependsOn: [...set.values()] };
}

// ---------------------------------------------------------------------------------------------
// Abrams (6) – close-range brawler with sustain and wall-stun charge
// ---------------------------------------------------------------------------------------------
const ABRAMS_HEAL = "citadel_ability_bull_heal";
const ABRAMS_CHARGE = "citadel_ability_bull_charge";
const ABRAMS_BEEFY = "citadel_ability_passive_beefy";
const ABRAMS_LEAP = "citadel_ability_bull_leap";

const abramsBrawler: Archetype = {
  id: "melee-brawler",
  label: "Melee brawler",
  description: "Shotgun + heavy melee frontliner. Wins close fights through lifesteal and Shoulder Charge wall stuns.",
  damage: { weapon: 0.45, spirit: 0.2, melee: 0.35 },
  range: "close",
  fundamentals: ["upgrade_close_range", "upgrade_endurance", "upgrade_lifestrike_gauntlets", "upgrade_melee_charge"],
  routes: {
    stabilise: { items: ["upgrade_healing_booster", "upgrade_weapon_backstabber", "upgrade_boxing_glove", "upgrade_tech_purge", "upgrade_crushing_fists", "upgrade_colossus"], note: "Sustain and resilience first; delay greed until fights are winnable." },
    normal: { items: ["upgrade_weapon_backstabber", "upgrade_boxing_glove", "upgrade_bullet_armor_reduction_aura", "upgrade_crushing_fists", "upgrade_close_quarter_combat", "upgrade_phantom_strike", "upgrade_colossus"], note: "Lifestrike into Crushing Fists core with Hunter's Aura for teamfights." },
    ambitious: { items: ["upgrade_crushing_fists", "upgrade_phantom_strike", "upgrade_berserker", "upgrade_close_quarter_combat", "upgrade_colossus"], note: "Rush the melee spike and engage tools when ahead." },
  },
  avoid: ["upgrade_long_range", "upgrade_sharpshooter"],
  abilityPlan: {
    unlockOrder: [ABRAMS_BEEFY, ABRAMS_CHARGE, ABRAMS_HEAL, ABRAMS_LEAP],
    upgrades: [
      s(ABRAMS_CHARGE, 1, "T1: 'Applies +32.0% Slow for 3s' makes charge follow-ups land."),
      s(ABRAMS_CHARGE, 2, "T2: 'On Wall Hit: +0.8s Stun Duration' – the main lane kill pattern.", true),
      s(ABRAMS_CHARGE, 3, "T3: '-18s Cooldown' roughly doubles charge uptime.", true),
      s(ABRAMS_BEEFY, 1),
      s(ABRAMS_LEAP, 1),
      s(ABRAMS_LEAP, 2),
      s(ABRAMS_LEAP, 3, "T3: 'On cast, become Unstoppable for 5s and +6m Impact Radius' – safe teamfight engage.", true),
      s(ABRAMS_BEEFY, 2),
      s(ABRAMS_BEEFY, 3, "T3: '+20% Debuff Resist and +9% Damage Regenerated'."),
      s(ABRAMS_HEAL, 1),
      s(ABRAMS_HEAL, 2),
      s(ABRAMS_HEAL, 3, "T3: '+2m Radius and +18.0 DPS'."),
    ],
    provenance: prov("Unlock and upgrade order follow the current most-played order (19,895 matches) with breakpoints marked from tier text.", "statistical"),
  },
  abilityBranches: [
    { when: "Enemy has heavy CC/debuffs", prefer: [s(ABRAMS_BEEFY, 2), s(ABRAMS_BEEFY, 3, "Debuff Resist at T3.")] },
    { when: "Losing lane to poke / need sustain", prefer: [s(ABRAMS_HEAL, 1), s(ABRAMS_HEAL, 2)] },
  ],
  provenance: [prov("Archetype and route order are author judgement informed by current popular items (Close Quarters 68%, Melee Charge 56% mid, Crushing Fists 52% late).")],
};

const abramsSiphon: Archetype = {
  id: "siphon-frontline",
  label: "Siphon frontline",
  description: "Duration/spirit-leaning durable frontline built around Siphon Life uptime.",
  damage: { weapon: 0.3, spirit: 0.45, melee: 0.25 },
  range: "close",
  fundamentals: ["upgrade_endurance", "upgrade_lifestrike_gauntlets", "upgrade_arcane_extension", "upgrade_acolytes_glove"],
  routes: {
    stabilise: { items: ["upgrade_healing_booster", "upgrade_tech_purge", "upgrade_improved_bullet_armor", "upgrade_imbued_duration_extender", "upgrade_colossus"], note: "Resilience and healing before damage." },
    normal: { items: ["upgrade_imbued_duration_extender", "upgrade_spirit_snatch", "upgrade_healing_booster", "upgrade_tech_purge", "upgrade_colossus", "upgrade_phantom_strike"], note: "Superior Duration core, then durability and engage." },
    ambitious: { items: ["upgrade_imbued_duration_extender", "upgrade_colossus", "upgrade_phantom_strike", "upgrade_escalating_exposure"], note: "Faster big-item spikes when ahead." },
  },
  avoid: ["upgrade_long_range", "upgrade_sharpshooter"],
  abilityPlan: {
    unlockOrder: [ABRAMS_BEEFY, ABRAMS_HEAL, ABRAMS_CHARGE, ABRAMS_LEAP],
    upgrades: [
      s(ABRAMS_HEAL, 1),
      s(ABRAMS_HEAL, 2),
      s(ABRAMS_HEAL, 3, "T3: '+2m Radius and +18.0 DPS with improved Spirit scaling' – the build's damage core.", true),
      s(ABRAMS_CHARGE, 1, "T1: 'Applies +32.0% Slow for 3s'."),
      s(ABRAMS_CHARGE, 2, "T2: wall-hit stun +0.8s."),
      s(ABRAMS_LEAP, 1),
      s(ABRAMS_LEAP, 2),
      s(ABRAMS_LEAP, 3, "T3: Unstoppable on cast.", true),
      s(ABRAMS_BEEFY, 1),
      s(ABRAMS_BEEFY, 2),
      s(ABRAMS_BEEFY, 3),
      s(ABRAMS_CHARGE, 3),
    ],
    provenance: prov("Author judgement: prioritises Siphon Life T3 for a spirit-leaning build; not a most-played order."),
  },
  abilityBranches: [{ when: "Enemy has heavy CC/debuffs", prefer: [s(ABRAMS_BEEFY, 3, "Debuff Resist at T3.")] }],
  provenance: [prov("Second archetype for archetype switching; informed by popular Duration Extender/Superior Duration/Spirit Snatch picks.")],
};

const abrams = deps({
  heroId: 6,
  heroClass: "hero_atlas",
  status: "curated",
  author: AUTHOR,
  checkedBuild: BUILD,
  archetypes: [abramsBrawler, abramsSiphon],
  threat: {
    damage: { weapon: 0.45, spirit: 0.2, melee: 0.35 },
    burst: "medium",
    sustain: "high",
    threats: ["melee_damage", "weapon_damage", "enemy_healing", "hard_cc"],
    laneNotes: [
      "Respect Shoulder Charge near walls: pushing into a wall stuns (T2 adds +0.8s).",
      "Siphon Life heals him while you stay close; fight at range or bring healing reduction.",
    ],
    provenance: prov("Kit text: Siphon Life heals, Shoulder Charge wall-stun, Seismic Impact stun; damage mix judgement.", "description"),
  },
});

// ---------------------------------------------------------------------------------------------
// Haze (13) – weapon assassin with sleep, stealth and multi-target ultimate
// ---------------------------------------------------------------------------------------------
const HAZE_DAGGER = "ability_sleep_dagger";
const HAZE_SMOKE = "ability_smoke_bomb";
const HAZE_FIX = "ability_stacking_damage";
const HAZE_DANCE = "ability_bullet_flurry";

const haze = deps({
  heroId: 13,
  heroClass: "hero_haze",
  status: "curated",
  author: AUTHOR,
  checkedBuild: BUILD,
  archetypes: [
    {
      id: "gun-carry",
      label: "Gun carry",
      description: "Fire-rate weapon carry: Fixation stacks on a target, Bullet Dance in teamfights.",
      damage: { weapon: 0.85, spirit: 0.1, melee: 0.05 },
      range: "mid",
      fundamentals: ["upgrade_rapid_rounds", "upgrade_blitz_bullets", "upgrade_active_reload", "upgrade_vampire"],
      routes: {
        stabilise: { items: ["upgrade_chonky", "upgrade_regenerating_bullet_shield", "upgrade_burst_fire", "upgrade_tech_purge", "upgrade_surging_power"], note: "Survive dives first; lifesteal and resilience." },
        normal: { items: ["upgrade_magic_storm", "upgrade_burst_fire", "upgrade_bullet_resist_shredder", "upgrade_ricochet", "upgrade_proc_silence", "upgrade_surging_power"], note: "Burst Fire and Ricochet core." },
        ambitious: { items: ["upgrade_burst_fire", "upgrade_ricochet", "upgrade_proc_silence", "upgrade_critshot"], note: "Damage spikes first when ahead." },
      },
      avoid: ["upgrade_escalating_exposure", "upgrade_boundless_spirit", "upgrade_close_range"],
      abilityPlan: {
        unlockOrder: [HAZE_FIX, HAZE_DAGGER, HAZE_SMOKE, HAZE_DANCE],
        upgrades: [
          s(HAZE_FIX, 1, "T1: '40.0 Spirit damage and 12% slow for 2s to target every 20 stacks'."),
          s(HAZE_FIX, 2, "T2: '+40 Max Stacks and +5s Duration'."),
          s(HAZE_DAGGER, 1, "T1: '-10.0% Bullet Resist Reduction for 6.0s on wake-up'."),
          s(HAZE_DAGGER, 2, "T2: '+1s Sleep Duration'."),
          s(HAZE_DAGGER, 3, "T3: '-17.0s Cooldown' – far more frequent picks.", true),
          s(HAZE_SMOKE, 1),
          s(HAZE_SMOKE, 2, "T2: 'Enable 2 Ability Charges'."),
          s(HAZE_SMOKE, 3, "T3: 'Dispels non-ult debuffs, +50% Bullet Lifesteal for 5s' – survival breakpoint.", true),
          s(HAZE_FIX, 3, "T3: '+0.11/ per Stack Scales with Weapon Damage'."),
          s(HAZE_DANCE, 1),
          s(HAZE_DANCE, 2, "T2: '+10.0% Fire Rate +3m Movespeed'."),
          s(HAZE_DANCE, 3, "T3: '+40.0% Evasion -65s Cooldown'.", true),
        ],
        provenance: prov("Order follows the current most-played order (24,792 matches); breakpoints from tier text.", "statistical"),
      },
      abilityBranches: [
        { when: "Getting caught by debuffs/CC", prefer: [s(HAZE_SMOKE, 2), s(HAZE_SMOKE, 3, "Dispel at T3.")] },
        { when: "Teamfight phase with ultimate up", prefer: [s(HAZE_DANCE, 1), s(HAZE_DANCE, 2)] },
      ],
      provenance: [prov("Routes informed by popular items (Rapid Rounds 72%, Swift Striker 62%, Bullet Lifesteal 48% mid, Ricochet 46% late).")],
    },
  ],
  threat: {
    damage: { weapon: 0.85, spirit: 0.1, melee: 0.05 },
    burst: "high",
    sustain: "medium",
    threats: ["weapon_damage", "weapon_burst", "stealth", "hard_cc"],
    laneNotes: [
      "Sleep Dagger: taking damage wakes you shortly after – an ally's hit can break it.",
      "Bullet Dance fires at all nearby targets; bullet resist or bullet immunity answer it.",
    ],
    provenance: prov("Kit text: Sleep Dagger sleeps, Smoke Bomb invisibility, Bullet Dance multi-target bullets.", "description"),
  },
});

// ---------------------------------------------------------------------------------------------
// Seven (2) – spirit caster with stun and channelled ultimate
// ---------------------------------------------------------------------------------------------
const SEVEN_BALL = "citadel_ability_lightning_ball";
const SEVEN_STATIC = "citadel_ability_static_charge";
const SEVEN_SURGE = "ability_power_surge";
const SEVEN_CLOUD = "citadel_ability_storm_cloud";

const seven = deps({
  heroId: 2,
  heroClass: "hero_gigawatt",
  status: "curated",
  author: AUTHOR,
  checkedBuild: BUILD,
  archetypes: [
    {
      id: "storm-caster",
      label: "Storm caster",
      description: "Spirit damage build that maximises Storm Cloud duration, radius and uptime.",
      damage: { weapon: 0.25, spirit: 0.75, melee: 0 },
      range: "mid",
      fundamentals: ["upgrade_non_player_bonus", "upgrade_improved_spirit", "upgrade_endurance", "upgrade_extra_charge"],
      routes: {
        stabilise: { items: ["upgrade_health_stealing_magic", "upgrade_magic_shield", "upgrade_arcane_extension", "upgrade_tech_purge", "upgrade_imbued_duration_extender"], note: "Lifesteal and resilience before greed." },
        normal: { items: ["upgrade_magic_vulnerability", "upgrade_arcane_extension", "upgrade_health_stealing_magic", "upgrade_soaring_spirit", "upgrade_imbued_duration_extender", "upgrade_escalating_exposure", "upgrade_tech_range", "upgrade_boundless_spirit"], note: "Mystic Vulnerability into Escalating Exposure core." },
        ambitious: { items: ["upgrade_escalating_exposure", "upgrade_imbued_duration_extender", "upgrade_boundless_spirit", "upgrade_tech_range"], note: "Big spirit spikes when ahead." },
      },
      avoid: ["upgrade_close_range", "upgrade_melee_charge"],
      abilityPlan: {
        unlockOrder: [SEVEN_SURGE, SEVEN_BALL, SEVEN_STATIC, SEVEN_CLOUD],
        upgrades: [
          s(SEVEN_SURGE, 1),
          s(SEVEN_SURGE, 2, "T2: '+8 Damage and increased scaling'."),
          s(SEVEN_SURGE, 3, "T3: '+10s Duration, applies -15% Spirit Resist'.", true),
          s(SEVEN_BALL, 1),
          s(SEVEN_CLOUD, 1, "T1: '+55% Bullet Resist while channeling Storm Cloud' – survive while channelling.", true),
          s(SEVEN_STATIC, 1),
          s(SEVEN_CLOUD, 2, "T2: '+7s Channel Time, +10m Final Radius'.", true),
          s(SEVEN_BALL, 2, "T2: '-28% move speed on hit targets'."),
          s(SEVEN_CLOUD, 3, "T3: '+65.0 DPS'."),
          s(SEVEN_BALL, 3),
          s(SEVEN_STATIC, 2),
          s(SEVEN_STATIC, 3, "T3: '+0.9s Stun Duration +160 Damage'.", true),
        ],
        provenance: prov("Order follows the current most-played order (7,547 matches); breakpoints from tier text.", "statistical"),
      },
      abilityBranches: [{ when: "Enemy dives you in lane", prefer: [s(SEVEN_STATIC, 1), s(SEVEN_STATIC, 2)] }],
      provenance: [prov("Routes informed by popular items (Mystic Vulnerability 53% mid, Escalating Exposure 65% late).")],
    },
  ],
  threat: {
    damage: { weapon: 0.25, spirit: 0.75, melee: 0 },
    burst: "high",
    sustain: "low",
    threats: ["spirit_damage", "spirit_burst", "hard_cc", "channel_ultimate"],
    laneNotes: [
      "Static Charge stuns everything near the marked target after a short delay – step away from allies.",
      "Storm Cloud is channelled and needs line of sight – break LoS, or stun to interrupt.",
    ],
    provenance: prov("Kit text: Static Charge delayed stun, Storm Cloud channel (AbilityChannelTime) and line-of-sight note.", "description"),
  },
});

// ---------------------------------------------------------------------------------------------
// Infernus (1) – hybrid damage-over-time with lifesteal and uninterruptible bomb
// ---------------------------------------------------------------------------------------------
const INF_NAPALM = "ability_incendiary_projectile";
const INF_DASH = "ability_flame_dash";
const INF_BURN = "ability_afterburn";
const INF_BOMB = "ability_fire_bomb";

const infernus = deps({
  heroId: 1,
  heroClass: "hero_inferno",
  status: "curated",
  author: AUTHOR,
  checkedBuild: BUILD,
  archetypes: [
    {
      id: "afterburn-hybrid",
      label: "Afterburn hybrid",
      description: "Weapon hits apply Afterburn; spirit and fire-rate items both scale it.",
      damage: { weapon: 0.5, spirit: 0.5, melee: 0 },
      range: "mid",
      fundamentals: ["upgrade_improved_spirit", "upgrade_rapid_rounds", "upgrade_soaring_spirit", "upgrade_clip_size"],
      routes: {
        stabilise: { items: ["upgrade_health_stealing_magic", "upgrade_cardio_calibrator", "upgrade_arcane_extension", "upgrade_tech_purge", "upgrade_imbued_duration_extender"], note: "Lifesteal and mobility first." },
        normal: { items: ["upgrade_health_stealing_magic", "upgrade_arcane_extension", "upgrade_magic_vulnerability", "upgrade_titan_round", "upgrade_toxic_bullets", "upgrade_imbued_duration_extender", "upgrade_escalating_exposure", "upgrade_tech_overflow"], note: "Spirit Lifesteal, duration, then Escalating Exposure." },
        ambitious: { items: ["upgrade_escalating_exposure", "upgrade_tech_overflow", "upgrade_boundless_spirit", "upgrade_ricochet"], note: "Late damage items when ahead." },
      },
      avoid: ["upgrade_close_range", "upgrade_melee_charge"],
      abilityPlan: {
        unlockOrder: [INF_BURN, INF_DASH, INF_NAPALM, INF_BOMB],
        upgrades: [
          s(INF_BURN, 1),
          s(INF_DASH, 1),
          s(INF_BURN, 2),
          s(INF_BURN, 3, "T3: '+3.0s Max Burn Duration'.", true),
          s(INF_NAPALM, 1),
          s(INF_DASH, 2, "T2: '+1.0s Trail Duration and +20.0 DPS'."),
          s(INF_NAPALM, 2, "T2: 'Napalm Effect: +15% Lifesteal'."),
          s(INF_NAPALM, 3, "T3: 'Napalm Effect: +17% Damage Taken and -33% Healing' – built-in healing reduction.", true),
          s(INF_DASH, 3, "T3: 'Enable 2 Ability Charges'."),
          s(INF_BOMB, 1),
          s(INF_BOMB, 2, "T2: '-65.0s Cooldown and +100.0% Explosion Lifesteal'.", true),
          s(INF_BOMB, 3, "T3: '+0.9s Stun Duration and +10m Radius'."),
        ],
        provenance: prov("Order follows the current most-played order (24,229 matches); breakpoints from tier text.", "statistical"),
      },
      abilityBranches: [{ when: "Enemy has heavy healing", prefer: [s(INF_NAPALM, 2), s(INF_NAPALM, 3, "Napalm T3 reduces healing by 33%.")] }],
      provenance: [prov("Routes informed by popular items (Spirit Lifesteal 64% mid, Escalating Exposure 61% late).")],
    },
  ],
  threat: {
    damage: { weapon: 0.5, spirit: 0.5, melee: 0 },
    burst: "medium",
    sustain: "medium",
    threats: ["spirit_damage", "weapon_damage", "debuffs", "hard_cc", "mobility_escape", "enemy_healing"],
    laneNotes: [
      "Afterburn builds from his weapon hits; debuff duration reduction shortens burns.",
      "Concussive Combustion 'cannot be interrupted' once cast – leave the radius instead of trying to stun him.",
    ],
    provenance: prov("Kit text: Afterburn DoT, Napalm/Combustion lifesteal tiers, Combustion stun 'cannot be interrupted'.", "description"),
  },
});

// ---------------------------------------------------------------------------------------------
// Dynamo (11) – utility initiator with team heal and channelled pull-stun ultimate
// ---------------------------------------------------------------------------------------------
const DYN_PULSE = "citadel_ability_stomp";
const DYN_QE = "citadel_ability_void_sphere";
const DYN_AURORA = "citadel_ability_nikuman";
const DYN_SING = "citadel_ability_self_vacuum";

const dynamo = deps({
  heroId: 11,
  heroClass: "hero_dynamo",
  status: "curated",
  author: AUTHOR,
  checkedBuild: BUILD,
  archetypes: [
    {
      id: "utility-initiator",
      label: "Utility initiator",
      description: "Cooldown and range build that maximises Singularity engages and Rejuvenating Aurora uptime.",
      damage: { weapon: 0.3, spirit: 0.7, melee: 0 },
      range: "mid",
      fundamentals: ["upgrade_extra_charge", "upgrade_endurance", "upgrade_magic_reach", "upgrade_sprint_booster"],
      routes: {
        stabilise: { items: ["upgrade_healing_booster", "upgrade_magic_tempo", "upgrade_tech_purge", "upgrade_warp_stone", "upgrade_cooldown_reduction"], note: "Survive and keep heal available." },
        normal: { items: ["upgrade_rapid_recharge", "upgrade_magic_tempo", "upgrade_arcane_extension", "upgrade_tech_range", "upgrade_cooldown_reduction", "upgrade_imbued_duration_extender", "upgrade_transcendent_cooldown", "upgrade_ability_refresher"], note: "Range + cooldown core, Refresher late." },
        ambitious: { items: ["upgrade_tech_range", "upgrade_cooldown_reduction", "upgrade_ability_refresher", "upgrade_transcendent_cooldown"], note: "Engage spikes when ahead." },
      },
      avoid: ["upgrade_close_range", "upgrade_melee_charge"],
      abilityPlan: {
        unlockOrder: [DYN_PULSE, DYN_AURORA, DYN_QE, DYN_SING],
        upgrades: [
          s(DYN_PULSE, 1, "T1: '+1 Charge'."),
          s(DYN_PULSE, 2, "T2: 'On Hit: -15% Bullet Resist and -24% Move Speed for 4s'."),
          s(DYN_PULSE, 3, "T3: '+135.0 Damage and +20m Cast Range'.", true),
          s(DYN_SING, 1),
          s(DYN_SING, 2),
          s(DYN_AURORA, 1, "T1: '+4m Move Speed during channel'."),
          s(DYN_AURORA, 2, "T2: '-20.0s Cooldown and +1.0s Duration'."),
          s(DYN_AURORA, 3, "T3: 'Full move and ability use' while healing.", true),
          s(DYN_QE, 1),
          s(DYN_QE, 2),
          s(DYN_SING, 3),
          s(DYN_QE, 3, "T3: 'Reduces non-ult debuffs by 50%'."),
        ],
        provenance: prov("Order follows the current most-played order (7,611 matches); breakpoints from tier text.", "statistical"),
      },
      abilityBranches: [{ when: "Team needs sustain in long fights", prefer: [s(DYN_AURORA, 2), s(DYN_AURORA, 3)] }],
      provenance: [prov("Routes informed by popular items (Extra Charge 72% early, Rapid Recharge 51% mid, Greater Expansion 54% late).")],
    },
  ],
  threat: {
    damage: { weapon: 0.3, spirit: 0.7, melee: 0 },
    burst: "medium",
    sustain: "high",
    threats: ["hard_cc", "channel_ultimate", "enemy_healing", "spirit_damage"],
    laneNotes: [
      "Kinetic Pulse knocks up along the ground – dodge sideways.",
      "Singularity is a channelled pull-and-stun ultimate; a stun is the usual interrupt (not verified for this ability).",
    ],
    provenance: prov("Kit text: Kinetic Pulse knockup, Rejuvenating Aurora channel heal, Singularity channel stun-pull.", "description"),
  },
});

// ---------------------------------------------------------------------------------------------
// Lady Geist (4) – spirit burst with heavy sustain and health swap
// ---------------------------------------------------------------------------------------------
const LG_BOMB = "ability_blood_bomb";
const LG_DRAIN = "ability_life_drain";
const LG_MALICE = "ability_blood_shards";
const LG_SWAP = "ability_health_swap";

const ladyGeist = deps({
  heroId: 4,
  heroClass: "hero_ghost",
  status: "curated",
  author: AUTHOR,
  checkedBuild: BUILD,
  archetypes: [
    {
      id: "blood-burst",
      label: "Spirit burst + sustain",
      description: "Essence Bomb burst with Life Drain sustain; trades health for damage.",
      damage: { weapon: 0.15, spirit: 0.85, melee: 0 },
      range: "mid",
      fundamentals: ["upgrade_mystic_regeneration", "upgrade_magic_burst", "upgrade_improved_spirit", "upgrade_magic_reach"],
      routes: {
        stabilise: { items: ["upgrade_resonant_healing", "upgrade_health_stealing_magic", "upgrade_tech_purge", "upgrade_magic_tempo", "upgrade_cooldown_reduction"], note: "Sustain first – her abilities cost health." },
        normal: { items: ["upgrade_resonant_healing", "upgrade_health_stealing_magic", "upgrade_magic_shock", "upgrade_magic_tempo", "upgrade_escalating_exposure", "upgrade_tech_range", "upgrade_damage_recycler"], note: "Radiant Regeneration and Tankbuster, then Escalating Exposure." },
        ambitious: { items: ["upgrade_escalating_exposure", "upgrade_boundless_spirit", "upgrade_spirit_burn", "upgrade_damage_recycler"], note: "Late damage items when ahead." },
      },
      avoid: ["upgrade_close_range", "upgrade_melee_charge"],
      abilityPlan: {
        unlockOrder: [LG_BOMB, LG_DRAIN, LG_MALICE, LG_SWAP],
        upgrades: [
          s(LG_BOMB, 1),
          s(LG_BOMB, 2, "T2: '+2m Radius and +50.0 Damage'."),
          s(LG_DRAIN, 1),
          s(LG_DRAIN, 2),
          s(LG_BOMB, 3, "T3: toxic ground for 30% damage per second over 6s.", true),
          s(LG_SWAP, 1),
          s(LG_MALICE, 1),
          s(LG_MALICE, 2, "T2: '+25.199999 Damage and +4 Blood Shards'."),
          s(LG_DRAIN, 3, "T3: 'Enables and Grants +2 Charges. Increases spirit scaling'.", true),
          s(LG_SWAP, 2, "T2: 'Silence enemies within 25m for 3s'.", true),
          s(LG_SWAP, 3),
          s(LG_MALICE, 3, "T3: '+8% Damage Amp'."),
        ],
        provenance: prov("Order follows the current most-played order (7,362 matches); breakpoints from tier text.", "statistical"),
      },
      abilityBranches: [{ when: "Teamfights against ability-reliant heroes", prefer: [s(LG_SWAP, 1), s(LG_SWAP, 2, "Soul Exchange T2 silences in 25m.")] }],
      provenance: [prov("Routes informed by popular items (Mystic Regeneration 77% early, Radiant Regeneration 70% mid).")],
    },
  ],
  threat: {
    damage: { weapon: 0.15, spirit: 0.85, melee: 0 },
    burst: "high",
    sustain: "high",
    threats: ["spirit_damage", "spirit_burst", "enemy_healing", "silence", "slow_kite"],
    laneNotes: [
      "Life Drain heals her while tethered in line of sight – break line of sight or bring healing reduction.",
      "Soul Exchange swaps health levels; T2 silences nearby enemies for 3s.",
    ],
    provenance: prov("Kit text: Life Drain heal tether, Malice slows, Soul Exchange health swap + T2 silence.", "description"),
  },
});

export const CURATED_PROFILES: HeroProfile[] = [abrams, haze, seven, infernus, dynamo, ladyGeist];
