import { readFileSync } from "node:fs";
import { validateSnapshot, GameData } from "../packages/engine/src/index.js";
import { itemMechanics, itemApplication } from "../packages/engine/src/knowledge/mechanics.js";
const m = JSON.parse(readFileSync("data/snapshots/manifest.json", "utf8"));
const v = validateSnapshot(JSON.parse(readFileSync(`data/snapshots/${m.active.file}`, "utf8")));
if (!v.ok) throw new Error(v.errors.join("\n"));
const d = new GameData(v.snapshot);
const names = process.argv.slice(2);
for (const it of d.items().filter((i) => i.tier <= 4 && (!names.length || names.includes(i.name)))) {
  console.log(`${it.name.padEnd(22)} T${it.tier} ${it.slot.padEnd(8)} ${itemApplication(it).join(",").padEnd(22)} ${itemMechanics(it).map((h) => h.mechanic + (h.via === "text" ? "*" : "")).join(" ")}`);
}
