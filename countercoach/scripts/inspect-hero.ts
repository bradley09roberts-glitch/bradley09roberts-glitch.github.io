import { readFileSync } from "node:fs";
import { validateSnapshot, GameData } from "../packages/engine/src/index.js";
import { abilityMechanics } from "../packages/engine/src/knowledge/mechanics.js";
const m = JSON.parse(readFileSync("data/snapshots/manifest.json", "utf8"));
const v = validateSnapshot(JSON.parse(readFileSync(`data/snapshots/${m.active.file}`, "utf8")));
if (!v.ok) throw new Error(v.errors.join("\n"));
const d = new GameData(v.snapshot);
for (const name of process.argv.slice(2)) {
  const h = d.heroByName(name)!;
  console.log(`\n### ${h.name} (${h.id}) type=${h.heroType} tags=${h.tags.join("/")} gun=${h.gunTag}`);
  for (const a of h.abilities) {
    console.log(` [${a.slot}] ${a.name} (${a.className}) mech=${abilityMechanics(a).map((x) => x.mechanic + (x.fromTier ? "@T" + x.fromTier : "")).join(",")}`);
    console.log(`     ${a.text.slice(0, 160)}`);
    for (const t of a.tiers) console.log(`     T${t.tier}: ${t.text}`);
  }
  const top = h.abilityOrders.orders[0];
  if (top) {
    const nm = (id: number) => h.abilities.find((a) => a.id === id)?.slot ?? "?";
    console.log(` top order (${top.matches} matches, ${(100 * top.wins / top.matches).toFixed(1)}% wr): ${top.sequence.map(nm).join(" ")}`);
  }
  const fmt = (arr: { className: string; pickPct: number }[]) => arr.slice(0, 14).map((p) => `${d.item(p.className)?.name ?? p.className}(${Math.round(p.pickPct)})`).join(", ");
  console.log(` early: ${fmt(h.popularItems.early)}`);
  console.log(` mid:   ${fmt(h.popularItems.mid)}`);
  console.log(` late:  ${fmt(h.popularItems.late)}`);
}
