// Copy the active, validated snapshot, review stamps and scenario fixtures into public/data so
// that they ship with the app (and the web preview). The snapshot is the last-known-good fallback.
import { copyFile, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../../..");
const out = path.resolve(here, "../public/data");
await rm(out, { recursive: true, force: true });
await mkdir(path.join(out, "fixtures"), { recursive: true });
const manifest = JSON.parse(await readFile(path.join(repo, "data/snapshots/manifest.json"), "utf8"));
await copyFile(path.join(repo, "data/snapshots", manifest.active.file), path.join(out, "snapshot.json"));
await copyFile(path.join(repo, "data/knowledge/review-stamps.json"), path.join(out, "review-stamps.json"));
// The desktop app builds its own icon templates on the user's machine (no game art is bundled).
// Only the browser preview, used for docs and tests, gets the development fixture.
if (process.argv.includes("--web")) await copyFile(path.join(repo, "fixtures/vision/icons.json"), path.join(out, "icons.json"));
const fixtures = (await readdir(path.join(repo, "fixtures/scenarios"))).filter((f) => f.endsWith(".json")).sort();
for (const f of fixtures) await copyFile(path.join(repo, "fixtures/scenarios", f), path.join(out, "fixtures", f));
await writeFile(path.join(out, "fixtures", "index.json"), JSON.stringify(fixtures));
console.log(`copied snapshot ${manifest.active.file}, review stamps${process.argv.includes("--web") ? ", icon template fixture" : ""} and ${fixtures.length} fixtures`);
