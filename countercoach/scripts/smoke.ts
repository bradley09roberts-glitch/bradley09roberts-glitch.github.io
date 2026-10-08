import { deps, match, obs, prefs, T0 } from "../packages/engine/test/helpers.js";
import { recommend, planAbility } from "../packages/engine/src/index.js";
const d = deps();
const id = (n: string) => d.data.heroByName(n)!.id;
const st = match([
  obs("me.hero", id("Abrams")),
  obs("match.gameTime", 900, 0, 900),
  obs("me.souls", 2400, 0, 900),
  obs("me.items", ["upgrade_close_range", "upgrade_endurance", "upgrade_lifestrike_gauntlets", "upgrade_melee_charge"], 0, 900),
  obs("roster.enemies", [id("Haze"), id("Seven"), id("Lady Geist"), id("Infernus"), id("Dynamo"), id("Wraith")], 0, 900),
  obs("enemyItems:" + id("Lady Geist") as any, { items: ["upgrade_health_stealing_magic", "upgrade_resonant_healing"], complete: false }, 0, 880),
  obs("me.abilities", { unlocked: ["citadel_ability_passive_beefy", "citadel_ability_bull_charge", "citadel_ability_bull_heal"], tiers: { citadel_ability_bull_charge: 2 } }, 0, 900),
  obs("me.unspentPoints", 3, 0, 900),
  obs("me.unspentUnlocks", 0, 0, 900),
]);
const t0 = performance.now();
const r = recommend(d, st, prefs(), T0 + 1000);
const t1 = performance.now();
console.log("decision:", r.decision, "|", r.summary, `| ${(t1 - t0).toFixed(1)} ms`);
console.log("BUY:", r.buyNow?.name, r.buyNow?.remainingCost, r.buyNow?.reasons);
console.log("SAVE:", r.saveFor?.name, r.saveFor?.soulsShort, r.saveFor?.reasons);
console.log("ALT:", r.alternative?.name, r.alternative?.condition);
console.log("delays:", r.delays, "| confidence:", r.confidence, r.confidenceReasons, "| info:", r.informationState);
console.log("threats:", r.threats.slice(0, 6).map((t) => `${t.kind}:${t.strength}:${t.evidence}${t.urgent ? "!" : ""}`).join(" "));
console.log("ranked:", r.ranked.slice(0, 8).map((s) => `${s.item.name}=${s.score} [fit ${s.parts.fit.toFixed(2)} ctr ${s.parts.counter.toFixed(2)} ph ${s.parts.phase}]`).join("\n  "));
console.log("missing:", r.missingInfo.map((m) => m.field).join(", "));
const prof = d.profiles.get(id("Abrams"))!;
const a = planAbility({ hero: d.data.hero(id("Abrams"))!, profile: prof, archetype: prof.archetypes[0]!, costs: d.data.snapshot.abilityCosts, abilities: { unlocked: ["citadel_ability_passive_beefy", "citadel_ability_bull_charge", "citadel_ability_bull_heal"], tiers: { citadel_ability_bull_charge: 2 } }, unspentPoints: 3, unspentUnlocks: 0 });
console.log("ABILITY:", a.kind, a.ability?.name, "T" + a.tier, a.pointsRequired, "|", a.whatChanges, "|", a.reason, "| alt:", a.alternative?.note);
