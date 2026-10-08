import { describe, expect, it } from "vitest";
import {
  activeHints,
  applyEvent,
  buildRepair,
  evaluate,
  postMatchReview,
  recommend,
  replayStoreFromLog,
  logEntry,
  whatIf,
  type DecisionLog,
} from "../src/index.js";
import { T0, deps, obs, playFixture, prefs } from "./helpers.js";

const d = deps();

describe("coach features", () => {
  it("threat panel shows at most three threats and prefers observed evidence", () => {
    const { state, now } = playFixture("haze-vs-healing");
    const out = evaluate(d, state, prefs(), now);
    expect(out.threatPanel.length).toBeLessThanOrEqual(3);
    expect(out.threatPanel[0]!.kind).toBe("enemy_healing");
    expect(out.threatPanel[0]!.evidence).toBe("reported");
  });

  it("lane threats come from lane opponents during laning", () => {
    const { state, now } = playFixture("infernus-lane-demo");
    const out = evaluate(d, state, prefs(), now);
    expect(out.rec.phase).toBe("lane");
    expect(out.lanePlan.opponents.map((o) => o.name).sort()).toEqual(["Abrams", "Haze"]);
    expect(out.lanePlan.opponents.every((o) => o.respect.length > 0)).toBe(true);
    expect(out.ability!.kind).toBe("unlock");
  });

  it("power spike shows souls/points remaining and no invented ETA", () => {
    const { state, now } = playFixture("abrams-midgame-demo");
    const out = evaluate(d, state, prefs(), now);
    expect(out.powerSpike.eta).toBeNull();
    expect(out.powerSpike.etaNote).toMatch(/No reliable income estimate/);
    expect(out.powerSpike.ability?.name).toBe("Shoulder Charge");
    expect(out.powerSpike.ability?.pointsRemaining).toBe(2);
  });

  it("build repair keeps off-route purchases and drops overlapping route items but never fundamentals", () => {
    const arch = d.profiles.archetype(13, null)!;
    const owned = ["upgrade_blitz_bullets", "upgrade_tech_purge"]; // Spirit Resilience is off the normal route for Haze
    const r = buildRepair(d, arch, "normal", owned);
    expect(r.offRoute.map((o) => o.className)).toContain("upgrade_tech_purge");
    expect(r.remaining.map((x) => x.className)).not.toContain("upgrade_tech_purge");
    for (const f of arch.fundamentals) expect(r.dropped.map((x) => x.className)).not.toContain(f);
  });

  it("team utility explains coverage and who should buy", () => {
    const { state, now } = playFixture("haze-vs-healing");
    const out = evaluate(d, state, prefs(), now);
    const anti = out.teamUtility.find((t) => t.tool === "anti_heal")!;
    expect(anti).toBeDefined();
    expect(anti.coveredBy).toEqual([]);
    expect(typeof anti.youAreSensibleBuyer).toBe("boolean");
  });

  it("active hints quote item behaviour and never claim enemy cooldowns", () => {
    const hints = activeHints(d, ["upgrade_unstoppable", "upgrade_divine_barrier", "upgrade_metal_skin", "upgrade_health"]);
    expect(hints.map((h) => h.className)).toEqual(["upgrade_unstoppable", "upgrade_divine_barrier", "upgrade_metal_skin"]);
    expect(hints[0]!.hint).toMatch(/before the CC lands/);
    expect(hints[1]!.hint).toMatch(/Does not remove stuns/);
    for (const h of hints) expect(h.hint).not.toMatch(/enemy.*cooldown/i);
  });

  it("what-if: adding an enemy healing item changes the advice and explains why", () => {
    const { state, now } = playFixture("haze-minor-roster-threat");
    const r = whatIf(d, applyEvent(state, obs("match.gameTime", 1200, now - T0, 1200)), prefs(), now, { kind: "enemyItem", heroId: 4, className: "upgrade_health_stealing_magic", action: "add" });
    const heal = (x: typeof r.after) => x.threats.find((t) => t.kind === "enemy_healing")!.strength;
    expect(heal(r.after)).toBeGreaterThan(heal(r.before));
    expect(r.explanation.length).toBeGreaterThan(0);
  });

  it("what-if: changing souls or archetype is reflected", () => {
    const { state, now } = playFixture("abrams-midgame-demo");
    const souls = whatIf(d, state, prefs(), now, { kind: "souls", value: 200 });
    expect(souls.after.buyNow).toBeNull();
    const arch = whatIf(d, state, prefs(), now, { kind: "archetype", archetypeId: "siphon-frontline" });
    expect(arch.after.archetypeId).toBe("siphon-frontline");
  });

  it("post-match review: at most three lessons, decision-time only, with a caveat", () => {
    const { state, now } = playFixture("abrams-midgame-demo");
    const entries = [0, 60_000, 120_000, 180_000].map((dt, i) => {
      const s = applyEvent(state, obs("me.souls", 3300 + i * 500, now - T0 + dt, 1010 + i * 60));
      const rec = recommend(d, s, prefs(), now + dt);
      return { ...logEntry(rec, s, now + dt, null), outcome: { bought: ["upgrade_health"], at: now + dt } };
    });
    const log: DecisionLog = { version: 1, matchId: "m", heroId: 6, build: 6763, entries };
    const review = postMatchReview(log);
    expect(review.lessons.length).toBeLessThanOrEqual(3);
    expect(review.lessons.length).toBeGreaterThan(0);
    expect(review.caveat).toMatch(/not claims that a different purchase would have won/);
    for (const l of review.lessons) expect(l.text).not.toMatch(/would have won|guarantee/i);
    // Replay data loads into a replay-only store.
    const rs = replayStoreFromLog(log);
    expect(rs.storeKind).toBe("replay");
    expect(recommend(d, rs, prefs(), now + 200_000).informationState).toBe("replay");
  });

  it("unsupported modes produce no purchase advice", () => {
    const { state, now } = playFixture("haze-minor-roster-threat");
    const r = recommend(d, { ...state, mode: "street_brawl" }, prefs(), now);
    expect(r.decision).toBe("unsupported");
    expect(r.buyNow).toBeNull();
  });

  it("pinned, rejected and deferred items are respected with reasons", () => {
    const { state, now } = playFixture("haze-minor-roster-threat");
    const base = recommend(d, state, prefs(), now);
    const top = base.buyNow!.className;
    const rejected = recommend(d, state, { ...prefs(), rejected: [{ className: top, reason: "enemy has none", at: now }] }, now);
    expect(rejected.buyNow?.className).not.toBe(top);
    expect(rejected.excluded.find((e) => e.className === top)!.reason).toMatch(/enemy has none/);
    const deferred = recommend(d, state, { ...prefs(), deferred: [{ className: top, reason: "later", at: now, untilGameTime: 99999 }] }, now);
    expect(deferred.buyNow?.className).not.toBe(top);
    const pinned = recommend(d, state, { ...prefs(), pinned: [{ className: "upgrade_ricochet", reason: "my plan", at: now }] }, now);
    expect([pinned.buyNow?.className, pinned.saveFor?.className]).toContain("upgrade_ricochet");
  });
});

describe("performance (local scoring cost)", () => {
  it("full evaluate() stays well under 100 ms after warm-up", () => {
    const { state, now } = playFixture("abrams-midgame-demo");
    evaluate(d, state, prefs(), now);
    const times: number[] = [];
    for (let i = 0; i < 30; i++) {
      const t0 = performance.now();
      evaluate(d, state, prefs(), now + i);
      times.push(performance.now() - t0);
    }
    times.sort((a, b) => a - b);
    const p50 = times[15]!;
    const p95 = times[28]!;
    console.log(`[perf] evaluate() p50=${p50.toFixed(1)}ms p95=${p95.toFixed(1)}ms (${process.platform}, Node ${process.version})`);
    expect(p95).toBeLessThan(100);
  });
});
