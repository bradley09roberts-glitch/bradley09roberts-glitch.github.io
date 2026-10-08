import { describe, expect, it } from "vitest";
import { applyEvent, applyEvents, createMatchState, currentGameTime, readField, type MatchEvent } from "../src/index.js";
import { T0, match, obs } from "./helpers.js";

describe("observation conflict and freshness rules", () => {
  it("drops out-of-order observations by game time", () => {
    let s = match([obs("me.souls", 2000, 1000, 600)]);
    s = applyEvent(s, obs("me.souls", 1500, 2000, 580));
    expect(readField(s, "me.souls", T0 + 3000)!.value).toBe(2000);
    expect(s.log.at(-1)!.outcome).toBe("dropped");
    expect(s.log.at(-1)!.reason).toMatch(/out of order/);
  });

  it("preserves a user correction against a lower-authority or not-clearly-newer observation", () => {
    let s = match([obs("me.items", ["upgrade_health"], 1000, 600, { userCorrection: true })]);
    s = applyEvent(s, obs("me.items", ["upgrade_endurance"], 2000, 605, { source: "screen", confidence: "medium" }));
    expect(readField(s, "me.items", T0 + 3000)!.value).toEqual(["upgrade_health"]);
    s = applyEvent(s, obs("me.items", ["upgrade_endurance"], 2500, 606, { source: "live", confidence: "high" }));
    expect(readField(s, "me.items", T0 + 3000)!.value).toEqual(["upgrade_health"]);
  });

  it("allows a clearly newer, high-confidence verified observation to supersede a correction", () => {
    let s = match([obs("me.items", ["upgrade_health"], 1000, 600, { userCorrection: true })]);
    s = applyEvent(s, obs("me.items", ["upgrade_endurance"], 30_000, 640, { source: "live", confidence: "high" }));
    expect(readField(s, "me.items", T0 + 31_000)!.value).toEqual(["upgrade_endurance"]);
  });

  it("a deliberate new user correction replaces the old one", () => {
    let s = match([obs("me.items", ["upgrade_health"], 1000, 600, { userCorrection: true })]);
    s = applyEvent(s, obs("me.items", ["upgrade_grit"], 2000, 601, { userCorrection: true }));
    expect(readField(s, "me.items", T0 + 3000)!.value).toEqual(["upgrade_grit"]);
  });

  it("rejects replay/spectator data in a live store and live data in a replay store", () => {
    let live = match([]);
    live = applyEvent(live, obs("roster.enemies", [1, 2, 3], 1000, 100, { source: "replay" }));
    expect(readField(live, "roster.enemies", T0 + 2000)).toBeNull();
    expect(live.log.at(-1)!.outcome).toBe("rejected");
    let rep = createMatchState("replay");
    rep = applyEvent(rep, { type: "observe", field: "me.souls", value: 10, source: "manual", observedAt: T0 });
    expect(rep.fields["me.souls"]).toBeUndefined();
  });

  it("reconnecting to the same match keeps state; a hero change clears hero-scoped fields", () => {
    let s = match([obs("me.hero", 13), obs("me.items", ["upgrade_health"]), obs("roster.enemies", [1, 2])]);
    s = applyEvent(s, { type: "match.start", matchId: "m1", mode: "standard", at: T0 + 500 });
    expect(readField(s, "me.items", T0 + 600)!.value).toEqual(["upgrade_health"]);
    s = applyEvent(s, obs("me.hero", 2, 700));
    expect(readField(s, "me.items", T0 + 800)).toBeNull();
    expect(readField(s, "roster.enemies", T0 + 800)!.value).toEqual([1, 2]);
  });

  it("pausing stops the match clock extrapolation", () => {
    let s = match([obs("match.gameTime", 600, 0, 600)]);
    expect(currentGameTime(s, T0 + 10_000)).toBe(610);
    s = applyEvent(s, { type: "match.pause", at: T0 + 10_000 });
    expect(currentGameTime(s, T0 + 70_000)).toBe(610);
    s = applyEvent(s, { type: "match.resume", at: T0 + 70_000 });
    expect(currentGameTime(s, T0 + 80_000)).toBe(620);
  });

  it("marks values aging/stale with field-specific policies", () => {
    const s = match([obs("me.souls", 2000, 0, null), obs("roster.enemies", [1], 0, null)]);
    expect(readField(s, "me.souls", T0 + 10_000)!.freshness).toBe("fresh");
    expect(readField(s, "me.souls", T0 + 120_000)!.freshness).toBe("aging");
    expect(readField(s, "me.souls", T0 + 400_000)!.freshness).toBe("stale");
    expect(readField(s, "roster.enemies", T0 + 3_600_000)!.freshness).toBe("fresh");
  });

  it("changing the enemy roster removes item observations for heroes no longer present", () => {
    let s = match([obs("roster.enemies", [1, 2]), obs("enemyItems:1", { items: ["upgrade_health"], complete: false })]);
    s = applyEvent(s, obs("roster.enemies", [2, 3], 100));
    expect(s.fields["enemyItems:1"]).toBeUndefined();
  });

  it("ignores observations after the match ended", () => {
    let s = match([obs("me.souls", 100)]);
    s = applyEvent(s, { type: "match.end", at: T0 + 10 });
    s = applyEvent(s, obs("me.souls", 999, 20));
    expect(readField(s, "me.souls", T0 + 30)!.value).toBe(100);
  });
});

describe("10. ambiguous (OCR-style) observations require explicit user resolution", () => {
  const amb: MatchEvent = {
    type: "observe.ambiguous",
    id: "icon-1",
    field: "enemyItems:4",
    candidates: [
      { value: { items: ["upgrade_spirit_bubble"], complete: false }, label: "Spirit Shielding", confidence: "medium" },
      { value: { items: ["upgrade_weapon_shielding"], complete: false }, label: "Weapon Shielding", confidence: "medium" },
    ],
    source: "screen",
    observedAt: T0 + 100,
    gameTime: 500,
  };

  it("does not apply any candidate until the user chooses", () => {
    const s = applyEvents(match([obs("roster.enemies", [4])]), [amb]);
    expect(s.fields["enemyItems:4"]).toBeUndefined();
    expect(s.pending).toHaveLength(1);
  });

  it("applies exactly the chosen candidate (not the first) as a user correction", () => {
    const s = applyEvents(match([obs("roster.enemies", [4])]), [amb, { type: "resolve.ambiguous", id: "icon-1", choice: 1, at: T0 + 200 }]);
    const r = readField(s, "enemyItems:4", T0 + 300)!;
    expect(r.value).toEqual({ items: ["upgrade_weapon_shielding"], complete: false });
    expect(r.obs.userCorrection).toBe(true);
    expect(s.pending).toHaveLength(0);
  });

  it("rejecting all candidates leaves state unchanged", () => {
    const s = applyEvents(match([obs("roster.enemies", [4])]), [amb, { type: "resolve.ambiguous", id: "icon-1", choice: "reject", at: T0 + 200 }]);
    expect(s.fields["enemyItems:4"]).toBeUndefined();
    expect(s.pending).toHaveLength(0);
  });
});
