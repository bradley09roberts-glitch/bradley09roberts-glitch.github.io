/**
 * Generate docs that must stay in sync with code and data:
 *   docs/COVERAGE.md       hero profile coverage (curated vs data-derived)
 *   docs/COUNTER_RULES.md  counter rules and the current items providing each response mechanic
 * Usage: npx tsx scripts/generate-docs.ts
 */
import { readFileSync, writeFileSync } from "node:fs";
import {
  COUNTER_RULES,
  GameData,
  ProfileRegistry,
  changeIndex,
  itemApplication,
  itemMechanics,
  mechanicMagnitude,
  validateSnapshot,
  type ReviewStamps,
} from "../packages/engine/src/index.js";

const m = JSON.parse(readFileSync("data/snapshots/manifest.json", "utf8"));
const v = validateSnapshot(JSON.parse(readFileSync(`data/snapshots/${m.active.file}`, "utf8")));
if (!v.ok) throw new Error(v.errors.join("\n"));
const data = new GameData(v.snapshot);
const stamps = JSON.parse(readFileSync("data/knowledge/review-stamps.json", "utf8")) as ReviewStamps;
const reg = new ProfileRegistry(data, changeIndex(data, stamps));
const meta = v.snapshot.meta;

// ---------------- coverage ----------------
const rows = reg.coverage();
const by = (l: string) => rows.filter((r) => r.level === l).length;
let cov = `# Hero profile coverage\n\nGenerated from snapshot \`${m.active.file}\` (client build ${meta.clientVersion}, retrieved ${meta.retrievedAt}).\n\n`;
cov += `| Level | Heroes | Meaning |\n|---|---|---|\n`;
cov += `| curated | ${by("curated")} | Hand-authored archetypes, routes and ability breakpoints; every reference checked against current data by tests. **AI-authored, awaiting review by an experienced player.** |\n`;
cov += `| curated-stale | ${by("curated-stale")} | Curated, but referenced data changed since review; shown with lowered confidence. |\n`;
cov += `| auto | ${by("auto")} | Basic, data-derived: popular items per phase (pick rate) and the most-played ability order in the current window. Not reviewed. |\n`;
cov += `| auto-limited | ${by("auto-limited")} | Data-derived with thin popularity/ability-order data; low confidence. |\n\n`;
cov += `Playable heroes in data: **${rows.length}** (\`player_selectable && !disabled\`).\n\n`;
cov += `| Hero | Level | Archetypes | Ability plan source | Popular-item lists | Notes |\n|---|---|---|---|---|---|\n`;
for (const r of rows.sort((a, b) => (a.level === b.level ? a.name.localeCompare(b.name) : a.level.localeCompare(b.level)))) {
  const p = reg.get(r.heroId)!;
  const orders = data.hero(r.heroId)!.abilityOrders.orders[0];
  const notes = [r.inDevelopmentFlag ? "data still flags in-development (new release)" : "", orders ? `top order ${orders.matches} matches` : "no ability-order stats", ...r.issues].filter(Boolean).join("; ");
  cov += `| ${r.name} | ${r.level} | ${p.archetypes.map((a) => a.label).join(", ")} | ${r.abilityPlanSource} | ${r.popularDataLists}/3 | ${notes} |\n`;
}
cov += `\nUnsupported mechanics: hero-specific item interactions and alternate modes (Street Brawl) are not modelled. Threat signatures for data-derived profiles come from ability property names and the weapon/spirit share of popular items, so burst level is unknown (assumed medium).\n`;
writeFileSync("docs/COVERAGE.md", cov);

// ---------------- counter rules ----------------
const shop = data.items().filter((i) => i.tier <= 4);
let cr = `# Counter rules\n\nGenerated from \`packages/engine/src/knowledge/counterRules.ts\` and snapshot build ${meta.clientVersion}.\n\n`;
cr += `Each rule is **threat → mechanic → valid responses → conditions → exceptions**. Response weights are heuristic judgement. Which items provide a response mechanic is detected from current item data (property names / modifier types / shop filters; \`*\` = detected from description text). Items are sorted by heuristic magnitude (1.0 ≈ a dedicated item; many items carry small innate stats, e.g. 5% bullet resist, which score low).\n\n`;
for (const r of COUNTER_RULES) {
  cr += `## ${r.id} — ${r.threat}\n\n**Mechanic:** ${r.mechanic}\n\n**Provenance:** ${r.provenance.kind}, ${r.provenance.confidence} confidence — ${r.provenance.interpretation}\n\n`;
  cr += `| Response | Weight | Applied to enemy | Current items (tier, application) | Note |\n|---|---|---|---|---|\n`;
  for (const resp of r.responses) {
    const items = shop
      .filter((i) => itemMechanics(i).some((h) => h.mechanic === resp.mechanic))
      .map((i) => ({ i, mag: mechanicMagnitude(i, resp.mechanic) }))
      .sort((a, b) => b.mag - a.mag || a.i.tier - b.i.tier)
      .map(({ i, mag }) => `${i.name}${itemMechanics(i).find((h) => h.mechanic === resp.mechanic)!.via === "text" ? "*" : ""} (T${i.tier}${resp.offensive ? `, ${itemApplication(i).join("/")}` : ""}${mag !== 1 ? `, magnitude ${mag.toFixed(2)}` : ""})`);
    cr += `| ${resp.mechanic} | ${resp.weight} | ${resp.offensive ? "yes" : "no"} | ${items.join(", ") || "—"} | ${resp.note ?? ""} |\n`;
  }
  cr += `\n**Conditions:** ${r.conditions.join("; ")}\n\n**Exceptions:** ${r.exceptions.map((e) => `${e.kind} ×${e.factor}${e.appliesTo ? ` (${e.appliesTo.join(", ")})` : ""} — ${e.note}`).join("; ")}\n\n`;
}
writeFileSync("docs/COUNTER_RULES.md", cr);
console.log(`coverage: ${rows.length} heroes; counter rules: ${COUNTER_RULES.length}`);
