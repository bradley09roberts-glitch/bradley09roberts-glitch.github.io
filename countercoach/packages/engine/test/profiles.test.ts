import { describe, expect, it } from "vitest";
import { CURATED_PROFILES, validateProfile } from "../src/index.js";
import { deps } from "./helpers.js";

const d = deps();

describe("hero profiles", () => {
  it("every curated profile resolves against current data with no errors", () => {
    for (const p of CURATED_PROFILES) {
      const errs = validateProfile(p, d.data).filter((i) => i.severity === "error");
      expect(errs, `${p.heroClass}: ${errs.map((e) => e.message).join("; ")}`).toEqual([]);
    }
  });

  it("covers every currently playable hero, distinguishing curated from data-derived", () => {
    const cov = d.profiles.coverage();
    expect(cov).toHaveLength(d.data.playableHeroes().length);
    const curated = cov.filter((c) => c.level === "curated");
    expect(curated.length).toBe(CURATED_PROFILES.length);
    for (const c of cov) expect(["curated", "curated-stale", "auto", "auto-limited"]).toContain(c.level);
    for (const c of cov.filter((x) => x.level.startsWith("auto"))) expect(d.profiles.get(c.heroId)!.author).toMatch(/auto/);
  });

  it("curated breakpoints quote text that exists in the current tier data", () => {
    for (const p of CURATED_PROFILES) {
      const hero = d.data.hero(p.heroId)!;
      for (const a of p.archetypes) {
        for (const s of a.abilityPlan.upgrades.filter((x) => x.why.includes("'"))) {
          const quote = s.why.match(/'([^']+)'/)![1]!;
          const tier = hero.abilities.find((x) => x.className === s.ability)!.tiers[s.tier - 1]!;
          expect(tier.text.replace(/\s+/g, " "), `${hero.name} ${s.ability} T${s.tier}`).toContain(quote.split(",")[0]!.replace(/\s+/g, " ").trim());
        }
      }
    }
  });

  it("data-derived profiles never invent items: every fundamental exists and is purchasable", () => {
    for (const h of d.data.playableHeroes()) {
      const p = d.profiles.get(h.id)!;
      for (const a of p.archetypes) for (const c of a.fundamentals) expect(d.data.item(c)?.tier ?? 99).toBeLessThanOrEqual(4);
    }
  });
});
