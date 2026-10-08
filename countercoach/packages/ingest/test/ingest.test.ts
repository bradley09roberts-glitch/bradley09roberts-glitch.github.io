import { createServer } from "node:http";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { fetchJson } from "../src/http.js";
import { deriveAbilityCosts, transformItems } from "../src/transform.js";
import type { RawBuild, RawItem } from "../src/rawSchemas.js";

describe("ingest", () => {
  it("falls back to the last cached response when the API fails (outage)", async () => {
    let fail = false;
    const server = createServer((req, res) => {
      if (fail) {
        res.statusCode = 503;
        res.end("down");
        return;
      }
      res.setHeader("content-type", "application/json");
      res.end(JSON.stringify({ ok: true, path: req.url }));
    });
    await new Promise<void>((r) => server.listen(0, "127.0.0.1", () => r()));
    const port = (server.address() as { port: number }).port;
    const cacheDir = mkdtempSync(path.join(tmpdir(), "cc-cache-"));
    const opts = { cacheDir, maxAgeMs: 0, offline: false, retries: 1, timeoutMs: 2000, log: () => {} };
    const url = `http://127.0.0.1:${port}/v1/assets/items`;
    const first = await fetchJson<{ ok: boolean }>(url, opts);
    expect(first.fromCache).toBe(false);
    fail = true;
    const second = await fetchJson<{ ok: boolean }>(url, opts);
    expect(second.fromCache).toBe(true);
    expect(second.data.ok).toBe(true);
    const offline = await fetchJson<{ ok: boolean }>(url, { ...opts, offline: true });
    expect(offline.fromCache).toBe(true);
    server.close();
  });

  it("derives tier costs from build currency deltas and cross-checks the level schedule", () => {
    const mk = (seq: [number, number, number][]): RawBuild => ({ hero_build: { hero_id: 1, details: { ability_order: { currency_changes: seq.map(([ability_id, currency_type, delta]) => ({ ability_id, currency_type, delta })) } } } }) as RawBuild;
    const one = mk([[10, 2, -1], [10, 1, -1], [10, 1, -2], [10, 1, -5], [11, 2, -1], [11, 1, -1]]);
    const levels = Array.from({ length: 36 }, (_, i) => ({ level: i + 1, souls: i * 100, grants: [[1, 3, 5, 8].includes(i + 1) ? "unlock" : "point"] as ("unlock" | "point")[] }));
    const hero = { playable: true, levels } as never;
    const w = { warnings: [] as string[] };
    const c = deriveAbilityCosts([one, one, one], [hero], w);
    expect(c.tierCosts).toEqual([1, 2, 5]);
    expect(c.crossCheck).toEqual({ pointsGranted: 32, pointsToMaxAll: 32, consistent: true });
    expect(w.warnings).toEqual([]);
  });

  it("skips malformed items with warnings instead of inventing values", () => {
    const raw = [
      { id: 1, class_name: "upgrade_a", name: "A", type: "upgrade", shopable: true, item_slot_type: "weapon", item_tier: 1, cost: 800, properties: { X: { value: "5" } } },
      { id: 2, class_name: "upgrade_b", name: "B", type: "upgrade", shopable: true, item_slot_type: "weapon", item_tier: null, cost: 800 },
      { id: 3, class_name: "upgrade_c", name: "C", type: "upgrade", shopable: true, item_slot_type: "weapon", item_tier: 2, cost: 1600, component_items: ["upgrade_missing"] },
    ] as RawItem[];
    const w = { warnings: [] as string[] };
    const items = transformItems(raw, w);
    expect(items.map((i) => i.className)).toEqual(["upgrade_a", "upgrade_c"]);
    expect(items[1]!.components).toEqual([]);
    expect(w.warnings.join(" ")).toMatch(/missing tier/);
    expect(w.warnings.join(" ")).toMatch(/not a shoppable item/);
  });
});
