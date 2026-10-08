/**
 * Record the current snapshot as the reviewed baseline for the knowledge base.
 * Run ONLY after checking curated profiles and counter rules against the new data:
 *   npx tsx scripts/stamp-review.ts
 */
import { readFileSync, writeFileSync } from "node:fs";
import { GameData, buildReviewStamps, validateSnapshot } from "../packages/engine/src/index.js";
const m = JSON.parse(readFileSync("data/snapshots/manifest.json", "utf8"));
const v = validateSnapshot(JSON.parse(readFileSync(`data/snapshots/${m.active.file}`, "utf8")));
if (!v.ok) throw new Error(v.errors.join("\n"));
const stamps = buildReviewStamps(new GameData(v.snapshot));
writeFileSync("data/knowledge/review-stamps.json", JSON.stringify(stamps, null, 1) + "\n");
console.log(`stamped ${Object.keys(stamps.entities).length} entities for build ${stamps.build}`);
