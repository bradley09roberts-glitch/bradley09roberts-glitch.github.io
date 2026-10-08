import { describe, expect, it } from "vitest";
import {
  COUNTER_RULES,
  applyEvent,
  applyEvents,
  evaluate,
  purchaseEconomics,
  readField,
  recommend,
  type MatchEvent,
  type Mechanic,
  type Recommendation,
} from "../src/index.js";
import { T0, deps, match, obs, playFixture, prefs } from "./helpers.js";

const d = deps();
const responsesFor = (threat: string): Mechanic[] =>
  COUNTER_RULES.filter((r) => r.threat === threat).flatMap((r) => r.responses.map((x) => x.mechanic));
const headAction = (r: Recommendation) => r.buyNow ?? r.saveFor;

/** No recommendation may ever offer an unaffordable BUY NOW. */
function assertBuyNowAffordable(r: Recommendation) {
  if (r.buyNow && r.souls != null) {
    const sell = r.buyNow.requiresSell?.sellValue ?? 0;
    expect(r.buyNow.remainingCost).toBeLessThanOrEqual(r.souls + sell);
  }
}

describe("1. same hero, different defensive advice for observed weapon vs spirit builds", () => {
  const weapon = playFixture("haze-vs-weapon-pressure");
  const spirit = playFixture("haze-vs-spirit-pressure");
  const rw = recommend(d, weapon.state, prefs(), weapon.now);
  const rs = recommend(d, spirit.state, prefs(), spirit.now);

  it("answers weapon damage with weapon-defence mechanics", () => {
    const a = headAction(rw)!.answers[0]!;
    expect(["weapon_damage", "weapon_burst"]).toContain(a.threat);
    expect(responsesFor("weapon_damage").concat(responsesFor("weapon_burst"))).toContain(a.mechanic);
    expect(a.evidence).toBe("reported");
    assertBuyNowAffordable(rw);
  });

  it("answers spirit damage with spirit-defence mechanics", () => {
    const a = headAction(rs)!.answers[0]!;
    expect(["spirit_damage", "spirit_burst"]).toContain(a.threat);
    expect(responsesFor("spirit_damage").concat(responsesFor("spirit_burst"))).toContain(a.mechanic);
    assertBuyNowAffordable(rs);
  });

  it("produces different purchases for the two builds", () => {
    expect(headAction(rw)!.className).not.toBe(headAction(rs)!.className);
  });
});

describe("2. healing reduction: prioritised when relevant and applicable, not redundant", () => {
  it("weapon Haze gets bullet-applied anti-heal against observed + reported healing", () => {
    const { state, now } = playFixture("haze-vs-healing");
    const r = recommend(d, state, prefs(), now);
    expect(r.buyNow).not.toBeNull();
    const item = d.data.item(r.buyNow!.className)!;
    expect(r.buyNow!.answers[0]!.mechanic).toBe("anti_heal");
    expect(item.text.toLowerCase()).toMatch(/bullet|headshot/);
  });

  it("spirit Seven gets spirit-applied anti-heal for the same threat", () => {
    const { state, now } = playFixture("seven-vs-healing");
    const r = recommend(d, state, prefs(), now);
    expect(r.buyNow!.answers[0]!.mechanic).toBe("anti_heal");
    expect(d.data.item(r.buyNow!.className)!.text.toLowerCase()).toContain("spirit damage");
  });

  it("does not recommend a second anti-heal when one is already owned", () => {
    const { state, now } = playFixture("haze-vs-healing");
    const owned = (readField(state, "me.items", now)!.value as string[]).concat("upgrade_toxic_bullets");
    const s2 = applyEvent(state, obs("me.items", owned, now - T0, 1200));
    const r = recommend(d, s2, prefs(), now);
    for (const a of [r.buyNow, r.saveFor]) {
      if (a) expect(a.answers[0]?.mechanic).not.toBe("anti_heal");
    }
  });

  it("an ally's relevant anti-heal reduces (but does not zero) its priority in teamfights", () => {
    const { state, now } = playFixture("haze-vs-healing");
    const base = recommend(d, state, prefs(), now).ranked.find((c) => c.item.className === "upgrade_toxic_bullets")!;
    // Ally Grey Talon (17) has Toxic Bullets.
    const s2 = applyEvent(state, obs("allyItems:17", { items: ["upgrade_toxic_bullets"], complete: false }, now - T0, 1200));
    const withAlly = recommend(d, s2, prefs(), now).ranked.find((c) => c.item.className === "upgrade_toxic_bullets");
    const healB = base.answers.find((a) => a.mechanic === "anti_heal")!.contribution;
    const healA = withAlly?.answers.find((a) => a.mechanic === "anti_heal")?.contribution ?? 0;
    expect(healA).toBeLessThan(healB);
    expect(healA).toBeGreaterThan(0);
  });
});

describe("3. urgency: urgent threat may delay core, uncertain minor threat does not", () => {
  it("roster-only threats keep the core route", () => {
    const { state, now } = playFixture("haze-minor-roster-threat");
    const r = recommend(d, state, prefs(), now);
    const top = r.ranked.find((c) => c.item.className === r.buyNow!.className)!;
    expect(["fundamental", "route", "locked"]).toContain(top.role);
    expect(r.threats.every((t) => !t.urgent)).toBe(true);
  });

  it("an urgent reported spirit burst promotes a spirit answer over core", () => {
    const { state, now } = playFixture("haze-urgent-spirit-burst");
    const r = recommend(d, state, prefs(), now);
    const a = r.buyNow!.answers[0]!;
    expect(["spirit_damage", "spirit_burst"]).toContain(a.threat);
    expect(a.urgent).toBe(true);
    assertBuyNowAffordable(r);
  });
});

describe("4. unaffordable target becomes SAVE FOR", () => {
  it("returns SAVE with souls remaining and no BUY NOW", () => {
    const { state, now } = playFixture("haze-save-for");
    const r = recommend(d, state, prefs(), now);
    expect(r.decision).toBe("save");
    expect(r.buyNow).toBeNull();
    expect(r.saveFor!.soulsShort).toBe(r.saveFor!.remainingCost - 500);
    expect(r.saveFor!.soulsShort).toBeGreaterThan(0);
  });

  it("never offers an unaffordable BUY NOW across all fixtures", () => {
    for (const f of ["haze-vs-weapon-pressure", "haze-vs-spirit-pressure", "haze-vs-healing", "seven-vs-healing", "haze-minor-roster-threat", "haze-urgent-spirit-burst", "haze-save-for", "abrams-midgame-demo", "infernus-lane-demo"]) {
      const { state, now } = playFixture(f);
      assertBuyNowAffordable(recommend(d, state, prefs(), now));
    }
  });
});

describe("5. owned components reduce the remaining cost; upgrades are legal", () => {
  it("every multi-component item deducts each owned component's cost and reuses its slot", () => {
    let checked = 0;
    for (const it of d.data.items().filter((i) => i.components.length && i.tier <= 4)) {
      const owned = [...it.components];
      const e = purchaseEconomics(it, owned, d.data, d.rules);
      const sum = it.components.reduce((n, c) => n + d.data.item(c)!.cost, 0);
      expect(e.remainingCost).toBe(Math.max(0, it.cost - sum));
      expect(e.consumes.sort()).toEqual([...it.components].sort());
      expect(e.slotDelta).toBe(0);
      checked++;
    }
    expect(checked).toBeGreaterThan(10);
  });

  it("recommendation shows the reduced price when you own a component", () => {
    const st = match([
      obs("me.hero", 2), obs("match.gameTime", 1200, 0, 1200), obs("me.souls", 800, 0, 1200),
      obs("me.items", ["upgrade_improved_spirit"], 0, 1200), obs("roster.enemies", [6, 7, 13, 4, 1, 11], 0, 1200),
    ]);
    const r = recommend(d, st, prefs(), T0 + 1000);
    const imp = r.ranked.find((c) => c.item.className === "upgrade_soaring_spirit")!;
    expect(imp.econ.remainingCost).toBe(d.data.item("upgrade_soaring_spirit")!.cost - d.data.item("upgrade_improved_spirit")!.cost);
    expect(imp.affordable).toBe(true);
  });

  it("never recommends a component you already upgraded", () => {
    const st = match([obs("me.hero", 13), obs("me.souls", 5000), obs("me.items", ["upgrade_blitz_bullets"])]);
    const r = recommend(d, st, prefs(), T0 + 1000);
    expect(r.ranked.map((c) => c.item.className)).not.toContain("upgrade_rapid_rounds");
  });

  it("flags an impossible inventory (component together with its upgrade)", () => {
    const st = match([obs("me.hero", 13), obs("me.souls", 5000), obs("me.items", ["upgrade_rapid_rounds", "upgrade_blitz_bullets"])]);
    const r = recommend(d, st, prefs(), T0 + 1000);
    expect(r.missingInfo.some((m) => m.field === "me.items" && /replaced/.test(m.why))).toBe(true);
  });
});

describe("6. full inventory", () => {
  const twelve = [
    "upgrade_health", "upgrade_endurance", "upgrade_improved_stamina", "upgrade_sprint_booster", "upgrade_clip_size", "upgrade_close_range",
    "upgrade_headshot_booster", "upgrade_high_velocity_mag", "upgrade_extra_charge", "upgrade_magic_burst", "upgrade_blitz_bullets", "upgrade_vampire",
  ];
  it("proposes a valid replacement (sell the weakest item) late game", () => {
    const st = match([obs("me.hero", 13), obs("match.gameTime", 2100, 0, 2100), obs("me.souls", 7000, 0, 2100), obs("me.items", twelve, 0, 2100), obs("roster.enemies", [6, 7, 2, 4, 1, 11], 0, 2100)]);
    const r = recommend(d, st, prefs(), T0 + 1000);
    const a = r.buyNow ?? r.saveFor;
    expect(a).not.toBeNull();
    if (a!.consumes.length === 0) {
      expect(a!.requiresSell).not.toBeNull();
      expect(twelve).toContain(a!.requiresSell!.className);
    }
    assertBuyNowAffordable(r);
  });

  it("explains no-purchase when every slot is locked core and nothing upgrades in place", () => {
    const t4 = ["upgrade_colossus", "upgrade_unstoppable", "upgrade_inhibitor", "upgrade_crushing_fists", "upgrade_phantom_strike", "upgrade_berserker", "upgrade_juggernaut", "upgrade_leech_placeholder"].filter((c) => d.data.item(c));
    const full = [...t4, "upgrade_ricochet", "upgrade_spellbreaker", "upgrade_boundless_spirit", "upgrade_transcendent_cooldown", "upgrade_escalating_exposure"].slice(0, 12);
    expect(full.length).toBe(12);
    const p = { ...prefs(), lockedCore: full };
    const st = match([obs("me.hero", 6), obs("match.gameTime", 2400, 0, 2400), obs("me.souls", 9000, 0, 2400), obs("me.items", full, 0, 2400)]);
    const r = recommend(d, st, p, T0 + 1000);
    expect(r.decision).toBe("no-legal-purchase");
    expect(r.summary).toMatch(/full/i);
  });
});

describe("8. weak or stale observations lower certainty instead of inventing state", () => {
  it("souls entered long ago lower confidence and are labelled", () => {
    const { state } = playFixture("haze-minor-roster-threat");
    const fresh = recommend(d, state, prefs(), T0 + 2000);
    const stale = recommend(d, state, prefs(), T0 + 15 * 60_000);
    const rank = { high: 3, medium: 2, low: 1 } as const;
    expect(rank[stale.confidence]).toBeLessThan(rank[fresh.confidence]);
    expect(stale.confidenceReasons.join(" ")).toMatch(/Souls entered/);
  });

  it("missing souls is not treated as zero", () => {
    const st = match([obs("me.hero", 13), obs("roster.enemies", [6, 7, 2, 4, 1, 11])]);
    const r = recommend(d, st, prefs(), T0 + 1000);
    expect(r.souls).toBeNull();
    expect(r.buyNow).toBeNull();
    expect(r.decision).toBe("save");
    expect(r.missingInfo.map((m) => m.field)).toContain("me.souls");
  });

  it("unobserved enemy items do not count as evidence", () => {
    const { state, now } = playFixture("haze-minor-roster-threat");
    const r = recommend(d, state, prefs(), now);
    expect(r.threats.every((t) => t.evidence === "roster-only")).toBe(true);
  });

  it("stale enemy-item observations contribute less than fresh ones", () => {
    const { state, now } = playFixture("haze-vs-healing");
    const fresh = recommend(d, state, prefs(), now).threats.find((t) => t.kind === "enemy_healing")!;
    const itemsOnly = (r: Recommendation) => r.threats.find((t) => t.kind === "enemy_healing")!.contributions.filter((c) => c.source === "items").reduce((n, c) => n + c.amount, 0);
    const later = recommend(d, state, prefs(), now + 20 * 60_000);
    expect(itemsOnly(later)).toBeLessThan(itemsOnly(recommend(d, state, prefs(), now)));
    expect(fresh.strength).toBeGreaterThan(0);
  });
});

describe("11. recommendation stability", () => {
  it("keeps the previous BUY NOW when the new top is within the stability margin", () => {
    const fx = playFixture("haze-minor-roster-threat");
    const now = fx.now;
    const state = applyEvent(fx.state, obs("me.souls", 6400, now - T0, 1200));
    const p = { ...prefs(), weights: { ...prefs().weights, stabilityMargin: 0.2 } };
    const first = recommend(d, state, p, now);
    const top = first.ranked.find((c) => c.item.className === first.buyNow!.className)!;
    const runnerUp = first.ranked.find((c) => c.affordable && c !== top && c.score > top.score / 1.2 && !c.answers.some((a) => a.urgent))!;
    expect(runnerUp).toBeDefined();
    const previous: Recommendation = { ...first, buyNow: { ...first.buyNow!, className: runnerUp.item.className, name: runnerUp.item.name } };
    const second = recommend(d, state, p, now + 1000, previous);
    expect(second.buyNow!.className).toBe(runnerUp.item.className);
    expect(second.stability.held).toBe(true);
    // With the default 12% margin a larger gap is allowed to change the advice.
    const third = recommend(d, state, prefs(), now + 1000, previous);
    if (top.score > runnerUp.score * 1.12) expect(third.buyNow!.className).toBe(top.item.className);
  });

  it("allows an urgent, well-supported change", () => {
    const { state, now } = playFixture("haze-minor-roster-threat");
    const first = recommend(d, state, prefs(), now);
    const urgent: MatchEvent[] = [
      obs("me.deathRecap", { weaponPct: 5, spiritPct: 95, meleePct: 0, killerHeroIds: [4] }, now - T0 + 10, 1210),
      { type: "threat.report", report: { kind: "spirit_burst", severity: "major", sourceHeroId: 4, note: "", source: "manual", observedAt: now + 10, gameTime: 1210 } },
    ];
    const s2 = applyEvents(state, urgent);
    const second = recommend(d, s2, prefs(), now + 1000, first);
    expect(second.buyNow!.className).not.toBe(first.buyNow!.className);
    expect(second.stability.held).toBe(false);
    expect(["spirit_damage", "spirit_burst"]).toContain(second.buyNow!.answers[0]!.threat);
  });
});

describe("12. a new match cannot inherit the previous match", () => {
  it("clears roster, items and threats on a new match id", () => {
    const { state, now } = playFixture("new-match-reset");
    expect(state.matchId).toBe("fx-reset-2");
    expect(readField(state, "roster.enemies", now)).toBeNull();
    expect(readField(state, "me.items", now)).toBeNull();
    expect(readField(state, "me.souls", now)).toBeNull();
    expect(readField(state, "me.hero", now)!.value).toBe(2);
    const out = evaluate(d, state, prefs(), now);
    expect(out.rec.threats).toEqual([]);
  });
});
