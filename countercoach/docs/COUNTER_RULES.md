# Counter rules

Generated from `packages/engine/src/knowledge/counterRules.ts` and snapshot build 6763.

Each rule is **threat → mechanic → valid responses → conditions → exceptions**. Response weights are heuristic judgement. Which items provide a response mechanic is detected from current item data (property names / modifier types / shop filters; `*` = detected from description text). Items are sorted by heuristic magnitude (1.0 ≈ a dedicated item; many items carry small innate stats, e.g. 5% bullet resist, which score low).

## weapon-sustained — weapon_damage

**Mechanic:** Sustained bullet damage from enemy weapons

**Provenance:** heuristic, medium confidence — Resist answers sustained damage directly; debuffs on the shooter are weaker because they need application.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| bullet_resist | 1 | no | Bullet Resilience (T3, magnitude 1.20), Colossus (T4, magnitude 0.84), Battle Vest (T2, magnitude 0.72), Warp Stone (T3, magnitude 0.72), Heroic Aura (T3, magnitude 0.68), Refresher (T4, magnitude 0.60), Cheat Death (T4, magnitude 0.60), Metal Skin (T3, magnitude 0.48), Crushing Fists (T4, magnitude 0.48), Weapon Shielding (T2, magnitude 0.43), Return Fire (T2, magnitude 0.40), Indomitable (T4, magnitude 0.40), Siphon Bullets (T4, magnitude 0.40), Vampiric Burst (T4, magnitude 0.40), Bullet Resist Shredder (T2, magnitude 0.36), Suppressor (T2, magnitude 0.32), Berserker (T3, magnitude 0.32), Superior Duration (T3, magnitude 0.32), Fleetfoot (T2, magnitude 0.24), Melee Charge (T2, magnitude 0.24), Echo Shard (T4, magnitude 0.20), Escalating Resilience (T3, magnitude 0.15) |  |
| bullet_deflect | 0.7 | no | Plated Armor (T4) |  |
| barrier_vs_weapon_burst | 0.6 | no | Weapon Shielding* (T2) |  |
| fire_rate_slow | 0.6 | yes | Rusted Barrel (T1, active_target), Suppressor (T2, spirit_damage), Hunter's Aura (T3, aura), Juggernaut (T4, reactive) | Must be applied to the shooter |
| outgoing_damage_reduction | 0.55 | yes | Inhibitor* (T4, bullets) |  |
| disarm | 0.55 | yes | Disarming Hex* (T3, active_target), Phantom Strike* (T4, active_target) | Short window; requires landing it on the carry |
| bullet_immunity_active | 0.5 | no | Metal Skin* (T3) | Short active; timing-dependent |
| max_health | 0.3 | no | Fortitude (T3, magnitude 0.94), Extra Health (T1, magnitude 0.53), Cheat Death (T4, magnitude 0.50), Leech (T4, magnitude 0.45), Frenzy (T4, magnitude 0.40), Inhibitor (T4, magnitude 0.38), Plated Armor (T4, magnitude 0.33), Lifestrike (T3, magnitude 0.31), Hollow Point (T3, magnitude 0.31), Veil Walker (T3, magnitude 0.31), Crippling Headshot (T4, magnitude 0.31), Magic Carpet (T4, magnitude 0.31), Unstoppable (T4, magnitude 0.31), Hunter's Aura (T3, magnitude 0.25), Fury Trance (T3, magnitude 0.25), Torment Pulse (T3, magnitude 0.25), Scourge (T4, magnitude 0.25), Infuser (T4, magnitude 0.25), Vampiric Burst (T4, magnitude 0.25), Debuff Reducer (T2, magnitude 0.23), Spirit Lifesteal (T2, magnitude 0.23), Bullet Lifesteal (T2, magnitude 0.23), Radiant Regeneration (T3, magnitude 0.23), Spellbreaker (T4, magnitude 0.23), Spiritual Overflow (T4, magnitude 0.23), Rebuttal (T1, magnitude 0.19), Improved Spirit (T2, magnitude 0.19), Point Blank (T3, magnitude 0.19), Disarming Hex (T3, magnitude 0.19), Escalating Resilience (T3, magnitude 0.19), Spirit Rend (T3, magnitude 0.19), Spirit Snatch (T3, magnitude 0.19), Knockdown (T3, magnitude 0.19), Boundless Spirit (T4, magnitude 0.19), Decay (T3, magnitude 0.16), Headshot Booster (T1, magnitude 0.15), Mystic Regeneration (T1, magnitude 0.15), Rusted Barrel (T1, magnitude 0.15), Weakening Headshot (T2, magnitude 0.15), Mystic Slow (T2, magnitude 0.15), Spirit Sap (T2, magnitude 0.15), Stalker (T2, magnitude 0.15), Counterspell (T3, magnitude 0.15), Headhunter (T3, magnitude 0.15), Tankbuster (T3, magnitude 0.15), Cultist Sacrifice (T3, magnitude 0.15), Silence Wave (T3, magnitude 0.15), Colossus (T4, magnitude 0.15), Lightning Scroll (T4, magnitude 0.15) |  |

**Conditions:** Enemy damage is mainly bullets (kit, items or death recap)

**Exceptions:** already_owned ×0.6 — Already have this defence; additional copies assumed to give diminishing returns.; ally_covers ×0.5 (fire_rate_slow, outgoing_damage_reduction, disarm) — An ally applies this; their coverage only counts when they reach the same targets.

## weapon-burst — weapon_burst

**Mechanic:** Short windows of very high bullet damage

**Provenance:** heuristic, medium confidence — Burst is best answered by reactive/active protection that covers the burst window.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| bullet_immunity_active | 1 | no | Metal Skin* (T3) | Become immune to bullets (active) |
| barrier_vs_weapon_burst | 0.9 | no | Weapon Shielding* (T2) | Triggers on significant weapon damage |
| bullet_resist | 0.6 | no | Bullet Resilience (T3, magnitude 1.20), Colossus (T4, magnitude 0.84), Battle Vest (T2, magnitude 0.72), Warp Stone (T3, magnitude 0.72), Heroic Aura (T3, magnitude 0.68), Refresher (T4, magnitude 0.60), Cheat Death (T4, magnitude 0.60), Metal Skin (T3, magnitude 0.48), Crushing Fists (T4, magnitude 0.48), Weapon Shielding (T2, magnitude 0.43), Return Fire (T2, magnitude 0.40), Indomitable (T4, magnitude 0.40), Siphon Bullets (T4, magnitude 0.40), Vampiric Burst (T4, magnitude 0.40), Bullet Resist Shredder (T2, magnitude 0.36), Suppressor (T2, magnitude 0.32), Berserker (T3, magnitude 0.32), Superior Duration (T3, magnitude 0.32), Fleetfoot (T2, magnitude 0.24), Melee Charge (T2, magnitude 0.24), Echo Shard (T4, magnitude 0.20), Escalating Resilience (T3, magnitude 0.15) |  |
| disarm | 0.5 | yes | Disarming Hex* (T3, active_target), Phantom Strike* (T4, active_target) |  |

**Conditions:** Deaths happen quickly to bullets (reported or death recap)

**Exceptions:** already_owned ×0.6 — Already have this defence; additional copies assumed to give diminishing returns.

## spirit-sustained — spirit_damage

**Mechanic:** Sustained spirit (ability) damage

**Provenance:** heuristic, medium confidence — Spirit resist answers sustained ability damage directly.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| spirit_resist | 1 | no | Fury Trance (T3, magnitude 1.60), Spirit Resilience (T3, magnitude 1.20), Scourge (T4, magnitude 0.96), Witchmail (T4, magnitude 0.88), Colossus (T4, magnitude 0.84), Enchanter's Emblem (T2, magnitude 0.72), Ethereal Shift (T4, magnitude 0.72), Spellbreaker (T4, magnitude 0.72), Escalating Exposure (T4, magnitude 0.68), Refresher (T4, magnitude 0.56), Silencer (T4, magnitude 0.48), Spirit Shielding (T2, magnitude 0.43), Dispel Magic (T3, magnitude 0.40), Greater Expansion (T3, magnitude 0.40), Arctic Blast (T4, magnitude 0.40), Indomitable (T4, magnitude 0.40), Healing Tempo (T4, magnitude 0.40), Infuser (T4, magnitude 0.40), Mystic Vulnerability (T2, magnitude 0.32), Restorative Locket (T2, magnitude 0.32), Blood Tribute (T3, magnitude 0.32), Spirit Snatch (T3, magnitude 0.29), Cold Front (T2, magnitude 0.24), Echo Shard (T4, magnitude 0.20) |  |
| barrier_vs_spirit_burst | 0.55 | no | Spirit Shielding* (T2) |  |
| spirit_burst_reduction | 0.5 | no | Spellbreaker* (T4) |  |
| silence | 0.45 | yes | Silencer (T4, bullets) | Prevents casting while active; does not undo damage already applied |
| spell_parry | 0.35 | no | Counterspell* (T3) | Requires a timed parry |
| max_health | 0.3 | no | Fortitude (T3, magnitude 0.94), Extra Health (T1, magnitude 0.53), Cheat Death (T4, magnitude 0.50), Leech (T4, magnitude 0.45), Frenzy (T4, magnitude 0.40), Inhibitor (T4, magnitude 0.38), Plated Armor (T4, magnitude 0.33), Lifestrike (T3, magnitude 0.31), Hollow Point (T3, magnitude 0.31), Veil Walker (T3, magnitude 0.31), Crippling Headshot (T4, magnitude 0.31), Magic Carpet (T4, magnitude 0.31), Unstoppable (T4, magnitude 0.31), Hunter's Aura (T3, magnitude 0.25), Fury Trance (T3, magnitude 0.25), Torment Pulse (T3, magnitude 0.25), Scourge (T4, magnitude 0.25), Infuser (T4, magnitude 0.25), Vampiric Burst (T4, magnitude 0.25), Debuff Reducer (T2, magnitude 0.23), Spirit Lifesteal (T2, magnitude 0.23), Bullet Lifesteal (T2, magnitude 0.23), Radiant Regeneration (T3, magnitude 0.23), Spellbreaker (T4, magnitude 0.23), Spiritual Overflow (T4, magnitude 0.23), Rebuttal (T1, magnitude 0.19), Improved Spirit (T2, magnitude 0.19), Point Blank (T3, magnitude 0.19), Disarming Hex (T3, magnitude 0.19), Escalating Resilience (T3, magnitude 0.19), Spirit Rend (T3, magnitude 0.19), Spirit Snatch (T3, magnitude 0.19), Knockdown (T3, magnitude 0.19), Boundless Spirit (T4, magnitude 0.19), Decay (T3, magnitude 0.16), Headshot Booster (T1, magnitude 0.15), Mystic Regeneration (T1, magnitude 0.15), Rusted Barrel (T1, magnitude 0.15), Weakening Headshot (T2, magnitude 0.15), Mystic Slow (T2, magnitude 0.15), Spirit Sap (T2, magnitude 0.15), Stalker (T2, magnitude 0.15), Counterspell (T3, magnitude 0.15), Headhunter (T3, magnitude 0.15), Tankbuster (T3, magnitude 0.15), Cultist Sacrifice (T3, magnitude 0.15), Silence Wave (T3, magnitude 0.15), Colossus (T4, magnitude 0.15), Lightning Scroll (T4, magnitude 0.15) |  |

**Conditions:** Enemy damage is mainly spirit (kit, items or death recap)

**Exceptions:** already_owned ×0.6 — Already have this defence; additional copies assumed to give diminishing returns.; ally_covers ×0.5 (silence) — An ally applies this; their coverage only counts when they reach the same targets.

## spirit-burst — spirit_burst

**Mechanic:** Large single instances / combos of spirit damage

**Provenance:** heuristic, medium confidence — Burst protection reduces the first big hit; resist alone is slower to scale.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| spirit_burst_reduction | 1 | no | Spellbreaker* (T4) | Next instance of high spirit damage is significantly reduced |
| barrier_vs_spirit_burst | 0.9 | no | Spirit Shielding* (T2) |  |
| spirit_resist | 0.6 | no | Fury Trance (T3, magnitude 1.60), Spirit Resilience (T3, magnitude 1.20), Scourge (T4, magnitude 0.96), Witchmail (T4, magnitude 0.88), Colossus (T4, magnitude 0.84), Enchanter's Emblem (T2, magnitude 0.72), Ethereal Shift (T4, magnitude 0.72), Spellbreaker (T4, magnitude 0.72), Escalating Exposure (T4, magnitude 0.68), Refresher (T4, magnitude 0.56), Silencer (T4, magnitude 0.48), Spirit Shielding (T2, magnitude 0.43), Dispel Magic (T3, magnitude 0.40), Greater Expansion (T3, magnitude 0.40), Arctic Blast (T4, magnitude 0.40), Indomitable (T4, magnitude 0.40), Healing Tempo (T4, magnitude 0.40), Infuser (T4, magnitude 0.40), Mystic Vulnerability (T2, magnitude 0.32), Restorative Locket (T2, magnitude 0.32), Blood Tribute (T3, magnitude 0.32), Spirit Snatch (T3, magnitude 0.29), Cold Front (T2, magnitude 0.24), Echo Shard (T4, magnitude 0.20) |  |
| spell_parry | 0.45 | no | Counterspell* (T3) |  |
| invulnerability_active | 0.4 | no | Cheat Death (T4), Ethereal Shift (T4) | Timing-dependent active |

**Conditions:** Deaths happen quickly to spirit damage (reported or death recap)

**Exceptions:** already_owned ×0.6 — Already have this defence; additional copies assumed to give diminishing returns.

## melee — melee_damage

**Mechanic:** Heavy melee / melee-build damage

**Provenance:** heuristic, medium confidence — Melee resist is the direct answer; parrying is a skill, not an item.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| melee_resist | 1 | no | Point Blank (T3, magnitude 1.20), Juggernaut (T4), Close Quarters (T1, magnitude 0.80), Rebuttal (T1, magnitude 0.72), Torment Pulse (T3, magnitude 0.72) |  |
| slow | 0.35 | yes | Cold Front (T2, active_area), Slowing Hex (T2, active_target), Mystic Slow (T2, spirit_damage), Slowing Bullets (T2, bullets), Lifestrike (T3, passive_self), Point Blank (T3, bullets), Majestic Leap (T3, active_self), Weighted Shots (T3, bullets), Vortex Web (T4, active_target), Arctic Blast (T4, active_area), Capacitor (T4, bullets/active_self), Colossus (T4, aura/active_area), Glass Cannon (T4, bullets), Mystic Reverb (T4, passive_self), Phantom Strike (T4, active_target), Lightning Scroll (T4, ultimate_damage) | Kiting a melee threat |
| max_health | 0.3 | no | Fortitude (T3, magnitude 0.94), Extra Health (T1, magnitude 0.53), Cheat Death (T4, magnitude 0.50), Leech (T4, magnitude 0.45), Frenzy (T4, magnitude 0.40), Inhibitor (T4, magnitude 0.38), Plated Armor (T4, magnitude 0.33), Lifestrike (T3, magnitude 0.31), Hollow Point (T3, magnitude 0.31), Veil Walker (T3, magnitude 0.31), Crippling Headshot (T4, magnitude 0.31), Magic Carpet (T4, magnitude 0.31), Unstoppable (T4, magnitude 0.31), Hunter's Aura (T3, magnitude 0.25), Fury Trance (T3, magnitude 0.25), Torment Pulse (T3, magnitude 0.25), Scourge (T4, magnitude 0.25), Infuser (T4, magnitude 0.25), Vampiric Burst (T4, magnitude 0.25), Debuff Reducer (T2, magnitude 0.23), Spirit Lifesteal (T2, magnitude 0.23), Bullet Lifesteal (T2, magnitude 0.23), Radiant Regeneration (T3, magnitude 0.23), Spellbreaker (T4, magnitude 0.23), Spiritual Overflow (T4, magnitude 0.23), Rebuttal (T1, magnitude 0.19), Improved Spirit (T2, magnitude 0.19), Point Blank (T3, magnitude 0.19), Disarming Hex (T3, magnitude 0.19), Escalating Resilience (T3, magnitude 0.19), Spirit Rend (T3, magnitude 0.19), Spirit Snatch (T3, magnitude 0.19), Knockdown (T3, magnitude 0.19), Boundless Spirit (T4, magnitude 0.19), Decay (T3, magnitude 0.16), Headshot Booster (T1, magnitude 0.15), Mystic Regeneration (T1, magnitude 0.15), Rusted Barrel (T1, magnitude 0.15), Weakening Headshot (T2, magnitude 0.15), Mystic Slow (T2, magnitude 0.15), Spirit Sap (T2, magnitude 0.15), Stalker (T2, magnitude 0.15), Counterspell (T3, magnitude 0.15), Headhunter (T3, magnitude 0.15), Tankbuster (T3, magnitude 0.15), Cultist Sacrifice (T3, magnitude 0.15), Silence Wave (T3, magnitude 0.15), Colossus (T4, magnitude 0.15), Lightning Scroll (T4, magnitude 0.15) |  |

**Conditions:** Enemy deals a large share of damage in melee

**Exceptions:** already_owned ×0.6 — Already have this defence; additional copies assumed to give diminishing returns.

## anti-heal — enemy_healing

**Mechanic:** Enemy lifesteal, regeneration or healing abilities

**Provenance:** heuristic, medium confidence — Healing reduction only matters if healing is substantial and you can keep it applied; reductions are assumed not to stack.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| anti_heal | 1 | yes | Healbane (T2, spirit_damage), Decay (T3, active_target), Toxic Bullets (T3, bullets), Crippling Headshot (T4, headshots/bullets), Cheat Death (T4, passive_self), Inhibitor (T4, bullets), Spirit Burn (T4, spirit_damage) | Reduces incoming healing on the target |
| percent_hp_damage | 0.25 | yes | Decay (T3, active_target), Toxic Bullets (T3, bullets), Mystic Reverb (T4, passive_self) |  |

**Conditions:** Healing is significant: observed lifesteal/heal items, a sustain-heavy kit, or reported; Your hero can apply the item's trigger (bullets, spirit damage, active) to the healer

**Exceptions:** already_owned ×0.15 (anti_heal) — Already applying this effect; a second source is assumed not to stack meaningfully.; ally_covers ×0.5 (anti_heal) — An ally already applies healing reduction; only counts when they fight the same healer.

## hard-cc — hard_cc

**Mechanic:** Stuns, sleeps and immobilises

**Provenance:** heuristic, medium confidence — Pre-emptive immunity and duration reduction help against hard CC; cleanses listed as 'non-stun' do not remove stuns.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| cc_immunity | 0.9 | no | Reactive Barrier (T2), Counterspell (T3), Indomitable (T4), Ethereal Shift (T4), Unstoppable (T4) | Unstoppable: 'Cannot be used while Stunned or Slept' – use before the CC lands |
| debuff_duration_reduction | 0.6 | no | Debuff Reducer (T2), Blood Tribute (T3), Dispel Magic (T3), Weighted Shots (T3), Cheat Death (T4), Scourge (T4), Divine Barrier (T4), Frenzy (T4), Spellbreaker (T4), Unstoppable (T4) |  |
| cc_reactive_barrier | 0.45 | no | Reactive Barrier* (T2) |  |
| cleanse | 0.25 | no | Cheat Death* (T4), Divine Barrier* (T4) | Divine Barrier removes non-stun debuffs only |

**Conditions:** Enemy kit or items include hard CC that decides fights

**Exceptions:** already_owned ×0.6 — Already have this defence; additional copies assumed to give diminishing returns.

## silence — silence

**Mechanic:** Silences that shut down your abilities

**Provenance:** heuristic, medium confidence — Silence matters most to ability-reliant builds.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| cleanse | 0.65 | no | Cheat Death* (T4), Divine Barrier* (T4) | Non-stun debuff removal |
| debuff_duration_reduction | 0.6 | no | Debuff Reducer (T2), Blood Tribute (T3), Dispel Magic (T3), Weighted Shots (T3), Cheat Death (T4), Scourge (T4), Divine Barrier (T4), Frenzy (T4), Spellbreaker (T4), Unstoppable (T4) |  |
| cc_immunity | 0.6 | no | Reactive Barrier (T2), Counterspell (T3), Indomitable (T4), Ethereal Shift (T4), Unstoppable (T4) |  |
| cc_reactive_barrier | 0.35 | no | Reactive Barrier* (T2) |  |

**Conditions:** Your build relies on abilities; Enemy kit or items silence

**Exceptions:** already_owned ×0.6 — Already have this defence; additional copies assumed to give diminishing returns.

## slows — slow_kite

**Mechanic:** Heavy slows that let enemies kite or catch you

**Provenance:** heuristic, low confidence — Slow duration reduction and mobility reduce kiting; low confidence on exact value.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| debuff_duration_reduction | 0.5 | no | Debuff Reducer (T2), Blood Tribute (T3), Dispel Magic (T3), Weighted Shots (T3), Cheat Death (T4), Scourge (T4), Divine Barrier (T4), Frenzy (T4), Spellbreaker (T4), Unstoppable (T4) |  |
| cleanse | 0.45 | no | Cheat Death* (T4), Divine Barrier* (T4) |  |
| mobility_self | 0.4 | no | Arcane Surge (T2), Kinetic Dash (T2), Majestic Leap (T3) |  |
| teleport_self | 0.35 | no | Warp Stone (T3), Phantom Strike (T4) |  |
| move_speed | 0.15 | no | Golden Goose Egg (T1), Healing Rite (T1), Sprint Boots (T1), Rusted Barrel (T1), Active Reload (T2), Swift Striker (T2), Enduring Speed (T2), Slowing Hex (T2), Fleetfoot (T2), Guardian Ward (T2), Long Range (T2), Mystic Slow (T2), Improved Spirit (T2), Trophy Collector (T2), Stalker (T2), Blood Tribute (T3), Hunter's Aura (T3), Burst Fire (T3), Fortitude (T3), Shadow Weave (T3), Counterspell (T3), Heroic Aura (T3), Fury Trance (T3), Disarming Hex (T3), Headhunter (T3), Surge of Power (T3), Dispel Magic (T3), Rescue Beam (T3), Radiant Regeneration (T3), Sharpshooter (T3), Veil Walker (T3), Vortex Web (T4), Divine Barrier (T4), Frenzy (T4), Healing Tempo (T4), Juggernaut (T4), Magic Carpet (T4), Ethereal Shift (T4), Lightning Scroll (T4) |  |

**Conditions:** Enemy applies repeated slows

**Exceptions:** already_owned ×0.6 — Already have this defence; additional copies assumed to give diminishing returns.

## debuffs — debuffs

**Mechanic:** Damage-over-time and stacked debuffs

**Provenance:** heuristic, medium confidence — Shorter or removed debuffs reduce their total value.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| debuff_duration_reduction | 0.7 | no | Debuff Reducer (T2), Blood Tribute (T3), Dispel Magic (T3), Weighted Shots (T3), Cheat Death (T4), Scourge (T4), Divine Barrier (T4), Frenzy (T4), Spellbreaker (T4), Unstoppable (T4) |  |
| cleanse | 0.65 | no | Cheat Death* (T4), Divine Barrier* (T4) |  |

**Conditions:** Enemy relies on lingering debuffs

**Exceptions:** already_owned ×0.6 — Already have this defence; additional copies assumed to give diminishing returns.

## mobility — mobility_escape

**Mechanic:** Highly mobile enemies escaping or diving

**Provenance:** heuristic, medium confidence — Movement control answers mobility; requires landing it.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| immobilize | 0.8 | yes | Arctic Blast (T4, active_area) |  |
| movement_silence | 0.75 | yes | Slowing Hex* (T2, active_target), Capacitor* (T4, bullets/active_self) | Silences movement-based items and abilities |
| pull_to_ground | 0.6 | yes | Slowing Hex (T2, active_target), Knockdown (T3, active_target), Phantom Strike (T4, active_target) |  |
| slow | 0.55 | yes | Cold Front (T2, active_area), Slowing Hex (T2, active_target), Mystic Slow (T2, spirit_damage), Slowing Bullets (T2, bullets), Lifestrike (T3, passive_self), Point Blank (T3, bullets), Majestic Leap (T3, active_self), Weighted Shots (T3, bullets), Vortex Web (T4, active_target), Arctic Blast (T4, active_area), Capacitor (T4, bullets/active_self), Colossus (T4, aura/active_area), Glass Cannon (T4, bullets), Mystic Reverb (T4, passive_self), Phantom Strike (T4, active_target), Lightning Scroll (T4, ultimate_damage) |  |
| stun | 0.5 | yes | Knockdown (T3, active_target), Crushing Fists (T4, bullets), Lightning Scroll (T4, ultimate_damage) |  |

**Conditions:** Enemy repeatedly escapes or dives with dashes/flight

**Exceptions:** ally_covers ×0.5 (immobilize, movement_silence, slow) — An ally applies this; their coverage only counts when they reach the same targets.; target_has_cc_answer ×0.7 — Target was seen with CC immunity or cleanse.

## stealth — stealth

**Mechanic:** Stealth / invisibility

**Provenance:** heuristic, low confidence — Few item answers exist in current data; low confidence.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| reveal | 0.6 | yes | Stalker* (T2, bullets) | Wounded enemies are revealed through walls |

**Conditions:** Enemy uses stealth to engage or escape

**Exceptions:** ally_covers ×0.5 — An ally applies this; their coverage only counts when they reach the same targets.

## channel-ult — channel_ultimate

**Mechanic:** Channelled ultimates that decide teamfights

**Provenance:** heuristic, low confidence — Only stun is weighted strongly as an interrupt; slows, disarms and displacement are not assumed to interrupt.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| stun | 0.8 | yes | Knockdown (T3, active_target), Crushing Fists (T4, bullets), Lightning Scroll (T4, ultimate_damage) | Stun is treated as an interrupt (community understanding, unverified per ability) |
| sleep | 0.5 | yes | — |  |
| silence | 0.35 | yes | Silencer (T4, bullets) | Whether silence cancels an ongoing channel is not verified |

**Conditions:** An enemy ultimate is channelled (ability data includes channel time)

**Exceptions:** ally_covers ×0.5 — An ally applies this; their coverage only counts when they reach the same targets.; target_has_cc_answer ×0.6 — Target was seen with CC immunity.

## bullet-resist-stacking — bullet_resist_stacking

**Mechanic:** Enemies stacking bullet resist

**Provenance:** heuristic, medium confidence — Resist reduction recovers damage lost to stacked resist; partial stacking assumed.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| bullet_resist_shred | 1 | yes | Rusted Barrel (T1, active_target), Bullet Resist Shredder (T2, spirit_damage), Weakening Headshot (T2, headshots/bullets), Stalker (T2, bullets), Hunter's Aura (T3, aura), Disarming Hex (T3, active_target), Hollow Point (T3, bullets), Alchemical Fire (T3, active_target), Crippling Headshot (T4, headshots/bullets), Crushing Fists (T4, bullets) |  |

**Conditions:** You deal mainly weapon damage; Enemies were seen buying bullet resist

**Exceptions:** already_owned ×0.35 — Already applying this effect; a second source is assumed not to stack meaningfully.; ally_covers ×0.5 — An ally applies this; their coverage only counts when they reach the same targets.

## spirit-resist-stacking — spirit_resist_stacking

**Mechanic:** Enemies stacking spirit resist

**Provenance:** heuristic, medium confidence — Resist reduction recovers damage lost to stacked resist; partial stacking assumed.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| spirit_resist_shred | 1 | yes | Spirit Strike (T1, passive_self), Mystic Vulnerability (T2, passive_self), Spirit Sap (T2, active_target), Spirit Shredder (T2, bullets), Spirit Rend (T3, headshots/bullets), Spirit Snatch (T3, passive_self), Crippling Headshot (T4, headshots/bullets), Escalating Exposure (T4, spirit_damage), Focus Lens (T4, active_target) |  |
| spirit_amp | 0.5 | yes | Escalating Exposure* (T4, spirit_damage) |  |

**Conditions:** You deal mainly spirit damage; Enemies were seen buying spirit resist

**Exceptions:** already_owned ×0.35 — Already applying this effect; a second source is assumed not to stack meaningfully.; ally_covers ×0.5 — An ally applies this; their coverage only counts when they reach the same targets.

## tanky — tanky_targets

**Mechanic:** Very high health targets

**Provenance:** heuristic, medium confidence — Percentage-health damage scales with target health.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| percent_hp_damage | 0.8 | yes | Decay (T3, active_target), Toxic Bullets (T3, bullets), Mystic Reverb (T4, passive_self) |  |
| bullet_resist_shred | 0.35 | yes | Rusted Barrel (T1, active_target), Bullet Resist Shredder (T2, spirit_damage), Weakening Headshot (T2, headshots/bullets), Stalker (T2, bullets), Hunter's Aura (T3, aura), Disarming Hex (T3, active_target), Hollow Point (T3, bullets), Alchemical Fire (T3, active_target), Crippling Headshot (T4, headshots/bullets), Crushing Fists (T4, bullets) |  |
| spirit_resist_shred | 0.35 | yes | Spirit Strike (T1, passive_self), Mystic Vulnerability (T2, passive_self), Spirit Sap (T2, active_target), Spirit Shredder (T2, bullets), Spirit Rend (T3, headshots/bullets), Spirit Snatch (T3, passive_self), Crippling Headshot (T4, headshots/bullets), Escalating Exposure (T4, spirit_damage), Focus Lens (T4, active_target) |  |

**Conditions:** Enemies built large health pools

**Exceptions:** already_owned ×0.15 — Already applying this effect; a second source is assumed not to stack meaningfully.

## percent-hp — percent_hp_damage

**Mechanic:** Enemy percentage-health damage

**Provenance:** heuristic, low confidence — Extra health is a weak answer to %HP damage; shortening the debuff helps.

| Response | Weight | Applied to enemy | Current items (tier, application) | Note |
|---|---|---|---|---|
| debuff_duration_reduction | 0.45 | no | Debuff Reducer (T2), Blood Tribute (T3), Dispel Magic (T3), Weighted Shots (T3), Cheat Death (T4), Scourge (T4), Divine Barrier (T4), Frenzy (T4), Spellbreaker (T4), Unstoppable (T4) |  |
| cleanse | 0.4 | no | Cheat Death* (T4), Divine Barrier* (T4) |  |
| spirit_resist | 0.4 | no | Fury Trance (T3, magnitude 1.60), Spirit Resilience (T3, magnitude 1.20), Scourge (T4, magnitude 0.96), Witchmail (T4, magnitude 0.88), Colossus (T4, magnitude 0.84), Enchanter's Emblem (T2, magnitude 0.72), Ethereal Shift (T4, magnitude 0.72), Spellbreaker (T4, magnitude 0.72), Escalating Exposure (T4, magnitude 0.68), Refresher (T4, magnitude 0.56), Silencer (T4, magnitude 0.48), Spirit Shielding (T2, magnitude 0.43), Dispel Magic (T3, magnitude 0.40), Greater Expansion (T3, magnitude 0.40), Arctic Blast (T4, magnitude 0.40), Indomitable (T4, magnitude 0.40), Healing Tempo (T4, magnitude 0.40), Infuser (T4, magnitude 0.40), Mystic Vulnerability (T2, magnitude 0.32), Restorative Locket (T2, magnitude 0.32), Blood Tribute (T3, magnitude 0.32), Spirit Snatch (T3, magnitude 0.29), Cold Front (T2, magnitude 0.24), Echo Shard (T4, magnitude 0.20) |  |

**Conditions:** Enemy applies %HP damage over time

**Exceptions:** already_owned ×0.6 — Already have this defence; additional copies assumed to give diminishing returns.

