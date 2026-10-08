import { describe, expect, it } from "vitest";
import { planAbility, pointsEarned, type AbilityState } from "../src/index.js";
import { deps } from "./helpers.js";

const d = deps();
const costs = d.data.snapshot.abilityCosts;
const abrams = d.data.hero(6)!;
const profile = d.profiles.get(6)!;
const arch = profile.archetypes[0]!;
const plan = (abilities: AbilityState | null, unspentPoints: number | null, unspentUnlocks: number | null = 0) =>
  planAbility({ hero: abrams, profile, archetype: arch, costs, abilities, unspentPoints, unspentUnlocks });

describe("7. ability planner", () => {
  it("uses tier costs derived from data (unlock is a separate currency)", () => {
    expect(costs.tierCosts).toEqual([1, 2, 5]);
    expect(costs.crossCheck.consistent).toBe(true);
  });

  it("spends an available unlock before points, in plan order", () => {
    const a = plan({ unlocked: [], tiers: {} }, 1, 1);
    expect(a.kind).toBe("unlock");
    expect(a.ability!.className).toBe(arch.abilityPlan.unlockOrder[0]);
  });

  it("recommends the next legal tier with exact cost and data text", () => {
    const a = plan({ unlocked: ["citadel_ability_passive_beefy", "citadel_ability_bull_charge"], tiers: {} }, 1, 0);
    expect(a.kind).toBe("upgrade");
    expect(a.ability!.className).toBe("citadel_ability_bull_charge");
    expect(a.tier).toBe(1);
    expect(a.pointsRequired).toBe(1);
    expect(a.whatChanges).toMatch(/Slow/);
  });

  it("never proposes a tier before its prerequisite or on a locked ability", () => {
    for (let pts = 0; pts <= 6; pts++) {
      const st = { unlocked: ["citadel_ability_bull_charge"], tiers: { citadel_ability_bull_charge: 1 } };
      const a = plan(st, pts, 0);
      if (a.kind === "upgrade") {
        expect(st.unlocked).toContain(a.ability!.className);
        const cur = (st.tiers as Record<string, number>)[a.ability!.className] ?? 0;
        expect(a.tier).toBe(cur + 1);
        expect(a.pointsRequired).toBeLessThanOrEqual(pts);
      }
    }
  });

  it("holds points for a key breakpoint instead of spending them elsewhere", () => {
    const st = { unlocked: ["citadel_ability_passive_beefy", "citadel_ability_bull_charge", "citadel_ability_bull_heal"], tiers: { citadel_ability_bull_charge: 2 } };
    const a = plan(st, 3, 0);
    expect(a.kind).toBe("hold");
    expect(a.ability!.className).toBe("citadel_ability_bull_charge");
    expect(a.tier).toBe(3);
    expect(a.pointsRequired).toBe(5);
    expect(a.breakpoint).toBe(true);
    expect(a.alternative).not.toBeNull();
  });

  it("starts from the real state even if it diverges from the plan (no refunds assumed)", () => {
    const st = { unlocked: ["citadel_ability_passive_beefy", "citadel_ability_bull_heal"], tiers: { citadel_ability_bull_heal: 3 } };
    const a = plan(st, 1, 0);
    expect(a.kind).toBe("upgrade");
    expect(a.ability!.className).not.toBe("citadel_ability_bull_heal");
    expect(a.upcoming.every((u) => !(u.className === "citadel_ability_bull_heal"))).toBe(true);
  });

  it("flags inconsistent state instead of guessing", () => {
    const a = plan({ unlocked: [], tiers: { citadel_ability_bull_charge: 2 } }, 3, 0);
    expect(a.kind).toBe("invalid-state");
    expect(a.issues.join(" ")).toMatch(/not unlocked/);
    const b = plan({ unlocked: ["citadel_ability_bull_charge"], tiers: { citadel_ability_bull_charge: 4 } }, 3, 0);
    expect(b.kind).toBe("invalid-state");
  });

  it("asks for unspent points instead of assuming one point per level", () => {
    const a = plan({ unlocked: ["citadel_ability_bull_charge"], tiers: {} }, null, 0);
    expect(a.kind).toBe("need-info");
  });

  it("reports nothing to do when everything is maxed", () => {
    const all = abrams.abilities.map((x) => x.className);
    const a = plan({ unlocked: all, tiers: Object.fromEntries(all.map((c) => [c, 3])) }, 5, 0);
    expect(a.kind).toBe("none");
  });

  it("level schedule from data grants 4 unlocks and 32 points at max", () => {
    const max = abrams.levels.at(-1)!.souls;
    expect(pointsEarned(abrams, max)).toMatchObject({ points: 32, unlocks: 4 });
    expect(pointsEarned(abrams, 0)).toMatchObject({ unlocks: 1, points: 0 });
  });

  it("every playable hero gets a legal plan that can reach max with the granted points", () => {
    for (const h of d.data.playableHeroes()) {
      const p = d.profiles.get(h.id)!;
      for (const a of p.archetypes) {
        expect(new Set(a.abilityPlan.unlockOrder).size).toBe(h.abilities.length);
        const tiers = new Map<string, number>();
        let pts = 0;
        for (const s of a.abilityPlan.upgrades) {
          expect((tiers.get(s.ability) ?? 0) + 1).toBe(s.tier);
          tiers.set(s.ability, s.tier);
          pts += costs.tierCosts[s.tier - 1]!;
        }
        expect(pts).toBeLessThanOrEqual(costs.crossCheck.pointsGranted);
      }
    }
  });
});
