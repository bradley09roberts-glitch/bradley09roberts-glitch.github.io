import { deps, match, obs, prefs, T0 } from "../packages/engine/test/helpers.js";
import { recommend, type MatchEvent } from "../packages/engine/src/index.js";
const d = deps();
const id = (n: string) => d.data.heroByName(n)!.id;
const show = (label: string, evs: MatchEvent[], heroName = "Haze") => {
  const st = match([obs("me.hero", id(heroName)), ...evs]);
  const r = recommend(d, st, prefs(), T0 + 2000);
  console.log(`\n== ${label}: ${r.decision} | ${r.summary} | conf ${r.confidence}`);
  console.log(`  BUY ${r.buyNow?.name ?? "-"} | SAVE ${r.saveFor?.name ?? "-"} (${r.saveFor?.soulsShort}) | ALT ${r.alternative?.name ?? "-"}`);
  console.log(`  threats: ${r.threats.slice(0, 5).map((t) => `${t.kind}:${t.strength}:${t.evidence}${t.urgent ? "!" : ""}`).join(" ")}`);
  console.log("  " + r.ranked.slice(0, 6).map((s) => `${s.item.name}(${s.econ.remainingCost})=${s.score} f${s.parts.fit.toFixed(2)} c${s.parts.counter.toFixed(2)}${s.answers[0] ? " " + s.answers[0].mechanic : ""}`).join("\n  "));
};
const hazeOwned = ["upgrade_blitz_bullets", "upgrade_active_reload", "upgrade_vampire", "upgrade_improved_spirit"];
const base = (souls: number) => [obs("match.gameTime", 1200, 0, 1200), obs("me.souls", souls, 0, 1200), obs("me.items", hazeOwned, 0, 1200), obs("match.standing", "even", 0, 1200)];
const enemies = [id("Wraith"), id("Vindicta"), id("Seven"), id("Lady Geist"), id("Abrams"), id("Dynamo")];
// A1 weapon pressure
show("A1 weapon", [...base(3200), obs("roster.enemies", enemies, 0, 1200),
  obs(`enemyItems:${id("Wraith")}` as any, { items: ["upgrade_burst_fire", "upgrade_blitz_bullets", "upgrade_titan_round"], complete: false }, 0, 1190),
  obs("me.deathRecap", { weaponPct: 85, spiritPct: 10, meleePct: 5, killerHeroIds: [id("Wraith")] }, 0, 1180)]);
show("A2 spirit", [...base(3200), obs("roster.enemies", enemies, 0, 1200),
  obs(`enemyItems:${id("Seven")}` as any, { items: ["upgrade_magic_vulnerability", "upgrade_soaring_spirit", "upgrade_arcane_extension"], complete: false }, 0, 1190),
  obs("me.deathRecap", { weaponPct: 10, spiritPct: 85, meleePct: 5, killerHeroIds: [id("Seven")] }, 0, 1180)]);
show("B1 heal Haze", [...base(3200), obs("roster.enemies", enemies, 0, 1200),
  obs(`enemyItems:${id("Lady Geist")}` as any, { items: ["upgrade_health_stealing_magic", "upgrade_resonant_healing"], complete: false }, 0, 1190),
  { type: "threat.report", report: { kind: "enemy_healing", severity: "major", sourceHeroId: id("Lady Geist"), note: "Geist heals through everything", source: "manual", observedAt: T0, gameTime: 1190 } }]);
show("B2 heal Seven", [obs("match.gameTime", 1200, 0, 1200), obs("me.souls", 3200, 0, 1200), obs("me.items", ["upgrade_non_player_bonus", "upgrade_improved_spirit", "upgrade_endurance", "upgrade_extra_charge"], 0, 1200), obs("match.standing", "even", 0, 1200), obs("roster.enemies", enemies, 0, 1200),
  obs(`enemyItems:${id("Lady Geist")}` as any, { items: ["upgrade_health_stealing_magic", "upgrade_resonant_healing"], complete: false }, 0, 1190),
  { type: "threat.report", report: { kind: "enemy_healing", severity: "major", sourceHeroId: id("Lady Geist"), note: "", source: "manual", observedAt: T0, gameTime: 1190 } }], "Seven");
show("C1 minor roster-only", [...base(1600), obs("roster.enemies", enemies, 0, 1200)]);
show("C2 urgent spirit", [...base(1600), obs("roster.enemies", enemies, 0, 1200),
  obs(`enemyItems:${id("Lady Geist")}` as any, { items: ["upgrade_magic_burst", "upgrade_soaring_spirit"], complete: false }, 0, 1190),
  obs("me.deathRecap", { weaponPct: 5, spiritPct: 95, meleePct: 0, killerHeroIds: [id("Lady Geist")] }, 0, 1195),
  { type: "threat.report", report: { kind: "spirit_burst", severity: "major", sourceHeroId: id("Lady Geist"), note: "", source: "manual", observedAt: T0, gameTime: 1195 } }]);
show("D save", [...base(500), obs("roster.enemies", enemies, 0, 1200)]);
