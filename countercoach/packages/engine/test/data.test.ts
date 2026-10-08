import { createHash } from "node:crypto";
import { describe, expect, it } from "vitest";
import {
  GameData,
  canonicalJson,
  changeIndex,
  diffSnapshots,
  evaluateCompatibility,
  recommend,
  sha256Hex,
  snapshotContentHash,
  validateProfile,
  ProfileRegistry,
  CURATED_PROFILES,
  validateSnapshot,
  type Snapshot,
} from "../src/index.js";
import { deps, loadSnapshot, loadStamps, playFixture, prefs } from "./helpers.js";

const snap = loadSnapshot();
const clone = (): Snapshot => JSON.parse(JSON.stringify(snap)) as Snapshot;

describe("versioned snapshot", () => {
  it("validates and carries provenance", () => {
    expect(snap.meta.apiBase).toBe("https://api.deadlock-api.com");
    expect(snap.meta.clientVersion).toBeGreaterThan(0);
    expect(snap.meta.endpoints).toContain("/v1/assets/items");
    expect(snap.abilityCosts.provenance.kind).toBe("derived");
  });

  it("content hash matches an independent SHA-256 and the stored value", () => {
    const text = canonicalJson({ heroes: snap.heroes, items: snap.items, abilityCosts: snap.abilityCosts });
    expect(sha256Hex(text)).toBe(createHash("sha256").update(text, "utf8").digest("hex"));
    expect(snapshotContentHash(snap)).toBe(snap.meta.contentHash);
    expect(sha256Hex("héllo ✓ 𝄞")).toBe(createHash("sha256").update("héllo ✓ 𝄞", "utf8").digest("hex"));
  });

  it("counts come from data, not a remembered roster size", () => {
    const playable = snap.heroes.filter((h) => h.playable);
    expect(snap.meta.counts.playableHeroes).toBe(playable.length);
    for (const h of playable) expect(h.abilities).toHaveLength(4);
  });

  it("rejects malformed data instead of replacing the active snapshot", () => {
    const bad = clone();
    (bad.items[0] as { cost: unknown }).cost = "free";
    expect(validateSnapshot(bad).ok).toBe(false);
    const bad2 = clone();
    bad2.items.find((i) => i.components.length)!.components.push("upgrade_does_not_exist");
    const v = validateSnapshot(bad2);
    expect(v.ok).toBe(false);
    if (!v.ok) expect(v.errors.join(" ")).toMatch(/unknown component/);
  });
});

describe("9. patch changes invalidate affected interactions", () => {
  it("diff detects changed costs/effects and removed items", () => {
    const next = clone();
    const tb = next.items.find((i) => i.className === "upgrade_toxic_bullets")!;
    tb.cost += 400;
    const prop = Object.keys(tb.properties).find((k) => /HealAmp/.test(k))!;
    tb.properties[prop]!.value = -10;
    next.items = next.items.filter((i) => i.className !== "upgrade_spellbreaker");
    const d = diffSnapshots(snap, next);
    const t = d.items.find((x) => x.ref.className === "upgrade_toxic_bullets")!;
    expect(t.change).toBe("modified");
    expect(t.fields.map((f) => f.field)).toEqual(expect.arrayContaining(["cost", `properties.${prop}`]));
    expect(d.items.find((x) => x.ref.className === "upgrade_spellbreaker")!.change).toBe("removed");
  });

  it("a changed item is flagged and its counter value is reduced until re-reviewed", () => {
    const next = clone();
    const tb = next.items.find((i) => i.className === "upgrade_toxic_bullets")!;
    const prop = Object.keys(tb.properties).find((k) => /HealAmpReceive/.test(k))!;
    tb.properties[prop]!.value = tb.properties[prop]!.value - 5;
    const { state, now } = playFixture("haze-vs-healing");
    const before = recommend(deps(snap), state, prefs(), now).ranked.find((c) => c.item.className === "upgrade_toxic_bullets")!;
    const after = recommend(deps(next), state, prefs(), now).ranked.find((c) => c.item.className === "upgrade_toxic_bullets")!;
    expect(after.flags.join(" ")).toMatch(/changed since rules were reviewed/);
    expect(after.parts.counter).toBeLessThan(before.parts.counter);
  });

  it("a curated profile depending on changed data is marked stale and lowers confidence", () => {
    const next = clone();
    next.items.find((i) => i.className === "upgrade_boxing_glove")!.cost += 100;
    const d2 = deps(next);
    const p = d2.profiles.get(6)!;
    expect(p.status).toBe("curated");
    expect(d2.profiles.level(p)).toBe("curated-stale");
    const { state, now } = playFixture("abrams-midgame-demo");
    const r = recommend(d2, state, prefs(), now);
    expect(r.confidenceReasons.join(" ")).toMatch(/changed since review/);
  });

  it("a curated profile referencing a removed item falls back to the data-derived profile", () => {
    const next = clone();
    next.items = next.items.filter((i) => i.className !== "upgrade_boxing_glove");
    const data = new GameData(next);
    expect(validateProfile(CURATED_PROFILES.find((p) => p.heroId === 6)!, data).some((i) => i.severity === "error")).toBe(true);
    const reg = new ProfileRegistry(data, changeIndex(data, loadStamps()));
    expect(reg.get(6)!.status).toBe("auto");
  });

  it("an outdated client build lowers confidence to low and explains why", () => {
    const d = deps();
    d.compat = evaluateCompatibility(snap, { clientVersion: (snap.meta.clientVersion ?? 0) + 5, checkedAt: "2026-10-09T00:00:00Z" });
    expect(d.compat.status).toBe("outdated");
    const { state, now } = playFixture("haze-minor-roster-threat");
    const r = recommend(d, state, prefs(), now);
    expect(r.confidence).toBe("low");
    expect(r.confidenceReasons.join(" ")).toMatch(/newer than data build/);
  });

  it("offline use is labelled unknown, not current", () => {
    expect(evaluateCompatibility(snap, null).status).toBe("unknown");
  });
});
