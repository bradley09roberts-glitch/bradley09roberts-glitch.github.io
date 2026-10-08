import { planAbility, type AbilityAction } from "../abilities/planner.js";
import { rulesForThreat } from "../knowledge/counterRules.js";
import { itemApplication, itemHas, itemMechanics, type Mechanic } from "../knowledge/mechanics.js";
import type { Archetype } from "../knowledge/profiles/types.js";
import { sellValue } from "../rules/inventory.js";
import { applyEvent, readField } from "../state/matchStore.js";
import type { DecisionLog, DecisionLogEntry } from "../state/adapters.js";
import type { EnemyItemsValue, FieldKey, MatchEvent, MatchState, ThreatKind } from "../state/types.js";
import type { Item } from "../types.js";
import type { Preferences } from "../engine/preferences.js";
import { applicationReliability, recommend, type EngineDeps, type Recommendation } from "../engine/recommend.js";
import { THREAT_LABEL, type AssessedThreat } from "../engine/threats.js";

// ---------------------------------------------------------------------------------------------
// Threat panel
// ---------------------------------------------------------------------------------------------

export interface ThreatPanelEntry {
  kind: ThreatKind;
  label: string;
  strength: number;
  evidence: AssessedThreat["evidence"];
  urgent: boolean;
  context: "lane" | "teamfight";
  topSource: string;
  answeredBy: string | null;
}

/** The 2–3 observed problems most relevant to me, split by lane vs teamfight. */
export function threatPanel(rec: Recommendation, deps: EngineDeps, owned: string[]): ThreatPanelEntry[] {
  const ownedItems = owned.map((c) => deps.data.item(c)).filter((x): x is Item => !!x);
  const out: ThreatPanelEntry[] = [];
  for (const t of rec.threats) {
    const context: ThreatPanelEntry["context"] = rec.phase === "lane" && t.laneRelevant ? "lane" : "teamfight";
    const answers = rulesForThreat(t.kind).flatMap((r) => r.responses.map((x) => x.mechanic));
    const have = ownedItems.find((it) => answers.some((m) => itemHas(it, m)));
    out.push({
      kind: t.kind,
      label: THREAT_LABEL[t.kind],
      strength: t.strength,
      evidence: t.evidence,
      urgent: t.urgent,
      context,
      topSource: t.contributions[0]?.detail ?? "",
      answeredBy: have?.name ?? null,
    });
  }
  // Prefer evidence over roster-only inference, then strength.
  const rank = (e: ThreatPanelEntry) => (e.evidence === "reported" ? 2 : e.evidence === "observed-items" ? 1 : 0) * 0.15 + e.strength;
  return out.sort((a, b) => rank(b) - rank(a)).slice(0, 3);
}

// ---------------------------------------------------------------------------------------------
// Power spike tracker
// ---------------------------------------------------------------------------------------------

export interface PowerSpike {
  item: { name: string; className: string; soulsRemaining: number } | null;
  ability: { name: string; tier: number; pointsRemaining: number; why: string } | null;
  eta: null;
  etaNote: string;
}

export function powerSpike(rec: Recommendation, ability: AbilityAction | null): PowerSpike {
  const target = rec.saveFor ?? rec.buyNow;
  const nextBreak = ability?.upcoming.find((u) => u.breakpoint);
  return {
    item: target ? { name: target.name, className: target.className, soulsRemaining: target.soulsShort } : null,
    ability:
      nextBreak && ability
        ? { name: nextBreak.name, tier: nextBreak.tier, pointsRemaining: Math.max(0, nextBreak.cost - (ability.pointsAvailable ?? 0)), why: ability.kind === "hold" ? ability.reason : "" }
        : null,
    eta: null,
    etaNote: "No reliable income estimate from manual inputs; showing souls/points remaining instead of a time.",
  };
}

// ---------------------------------------------------------------------------------------------
// Build repair
// ---------------------------------------------------------------------------------------------

export interface BuildRepair {
  offRoute: { className: string; name: string; covers: Mechanic[] }[];
  dropped: { className: string; name: string; reason: string }[];
  remaining: { className: string; name: string }[];
  note: string;
}

const REPAIR_MECHANICS: Mechanic[] = ["bullet_resist", "spirit_resist", "anti_heal", "cc_immunity", "debuff_duration_reduction", "cleanse", "spirit_burst_reduction", "bullet_immunity_active", "lifesteal_bullet", "lifesteal_spirit"];

/** Adapt the remaining route after off-plan purchases, preserving what is still useful. */
export function buildRepair(deps: EngineDeps, arch: Archetype, route: "stabilise" | "normal" | "ambitious", owned: string[]): BuildRepair {
  const planned = [...arch.fundamentals, ...arch.routes[route].items];
  const offRoute = owned
    .filter((c) => !planned.includes(c) && !planned.some((p) => deps.data.item(p)?.components.includes(c)))
    .map((c) => deps.data.item(c))
    .filter((x): x is Item => !!x)
    .map((it) => ({ className: it.className, name: it.name, covers: REPAIR_MECHANICS.filter((m) => itemHas(it, m)) }));
  const coveredByOff = new Set(offRoute.flatMap((o) => o.covers));
  const dropped: BuildRepair["dropped"] = [];
  const remaining: BuildRepair["remaining"] = [];
  for (const c of planned) {
    if (owned.includes(c)) continue;
    const it = deps.data.item(c);
    if (!it) continue;
    if (owned.some((o) => deps.data.item(o)?.components.includes(c))) continue;
    const overlap = REPAIR_MECHANICS.filter((m) => itemHas(it, m) && coveredByOff.has(m) && !WEAPON_OR_SPIRIT_CORE(it));
    if (overlap.length && !arch.fundamentals.includes(c)) {
      dropped.push({ className: c, name: it.name, reason: `Overlaps ${overlap.join(", ")} already covered by ${offRoute.filter((o) => o.covers.some((m) => overlap.includes(m))).map((o) => o.name).join(", ")}` });
      continue;
    }
    remaining.push({ className: c, name: it.name });
  }
  const note = offRoute.length
    ? `You own ${offRoute.length} off-route item${offRoute.length > 1 ? "s" : ""}; they are kept and the route skips overlapping defensive items. Fundamentals are never dropped.`
    : "Inventory matches the plan.";
  return { offRoute, dropped, remaining, note };
}

function WEAPON_OR_SPIRIT_CORE(it: Item): boolean {
  return itemHas(it, "weapon_damage") || itemHas(it, "spirit_power");
}

// ---------------------------------------------------------------------------------------------
// Team utility check
// ---------------------------------------------------------------------------------------------

export interface TeamUtilityEntry {
  threat: ThreatKind;
  tool: Mechanic;
  label: string;
  coveredBy: string[];
  youAreSensibleBuyer: boolean;
  explanation: string;
}

const TEAM_TOOLS: { threat: ThreatKind; tool: Mechanic; label: string }[] = [
  { threat: "enemy_healing", tool: "anti_heal", label: "Healing reduction" },
  { threat: "channel_ultimate", tool: "stun", label: "Interrupt (stun)" },
  { threat: "bullet_resist_stacking", tool: "bullet_resist_shred", label: "Bullet resist reduction" },
  { threat: "spirit_resist_stacking", tool: "spirit_resist_shred", label: "Spirit resist reduction" },
  { threat: "mobility_escape", tool: "movement_silence", label: "Movement lockdown" },
  { threat: "stealth", tool: "reveal", label: "Reveal" },
];

export function teamUtility(deps: EngineDeps, state: MatchState, rec: Recommendation, myArch: Archetype, now: number): TeamUtilityEntry[] {
  const allies = (readField(state, "roster.allies", now)?.value ?? []).map((id) => ({
    id,
    name: deps.data.hero(id)?.name ?? `Hero ${id}`,
    items: ((readField(state, `allyItems:${id}`, now)?.value as EnemyItemsValue | undefined)?.items ?? []).map((c) => deps.data.item(c)).filter((x): x is Item => !!x),
    arch: deps.profiles.archetype(id, null),
  }));
  const owned = (readField(state, "me.items", now)?.value ?? []).map((c) => deps.data.item(c)).filter((x): x is Item => !!x);
  const out: TeamUtilityEntry[] = [];
  for (const tt of TEAM_TOOLS) {
    const t = rec.threats.find((x) => x.kind === tt.threat);
    if (!t || t.strength < 0.3) continue;
    const coveredBy = [
      ...owned.filter((it) => itemHas(it, tt.tool)).map((it) => `You (${it.name})`),
      ...allies.flatMap((a) => a.items.filter((it) => itemHas(it, tt.tool)).map((it) => `${a.name} (${it.name})`)),
    ];
    // Who applies the cheapest relevant item most reliably?
    const options = deps.data.items().filter((it) => it.tier <= 4 && itemHas(it, tt.tool));
    const myBest = Math.max(0, ...options.map((it) => applicationReliability(itemApplication(it), myArch)));
    const allyBest = Math.max(0, ...allies.filter((a) => a.arch).map((a) => Math.max(0, ...options.map((it) => applicationReliability(itemApplication(it), a.arch!)))));
    const sensible = myBest >= allyBest - 0.1;
    out.push({
      threat: tt.threat,
      tool: tt.tool,
      label: tt.label,
      coveredBy,
      youAreSensibleBuyer: sensible,
      explanation: coveredBy.length
        ? `Covered by ${coveredBy.join(", ")}; extra copies are usually redundant unless they fight different targets.`
        : sensible
          ? `Nobody has ${tt.label.toLowerCase()} against ${THREAT_LABEL[tt.threat].toLowerCase()}; you can apply it reliably.`
          : `Nobody has ${tt.label.toLowerCase()}; an ally applies it more reliably than your build (${Math.round(myBest * 100)}% vs ${Math.round(allyBest * 100)}% reliability index).`,
    });
  }
  return out;
}

// ---------------------------------------------------------------------------------------------
// Replacement advice
// ---------------------------------------------------------------------------------------------

export interface ReplacementAdvice {
  weakest: { className: string; name: string; sellValue: number } | null;
  replaceWith: { className: string; name: string } | null;
  timing: string;
  opportunityCost: string;
  ruleNote: string;
}

export function replacementAdvice(deps: EngineDeps, rec: Recommendation, owned: string[], prefs: Preferences): ReplacementAdvice {
  const total = deps.rules.inventory.value.totalSlots;
  const ruleNote = `Sell value uses an unverified community rule (${Math.round(deps.rules.sellBack.value.fraction * 100)}%). Inventory size ${total} is a community-reported rule.`;
  const fromRec = (rec.buyNow ?? rec.saveFor)?.requiresSell ?? null;
  const target = rec.buyNow ?? rec.saveFor;
  if (fromRec && target) {
    return {
      weakest: { className: fromRec.className, name: fromRec.name, sellValue: fromRec.sellValue },
      replaceWith: { className: target.className, name: target.name },
      timing: `Replace when you can afford ${target.name} (${target.remainingCost} souls, ≈${fromRec.sellValue} back from selling).`,
      opportunityCost: `${fromRec.name} contributes least to your current build and threats.`,
      ruleNote,
    };
  }
  const items = owned.map((c) => deps.data.item(c)).filter((x): x is Item => !!x && !prefs.lockedCore.includes(x.className));
  if (owned.length < total - 1 || !items.length) {
    return { weakest: null, replaceWith: null, timing: `${total - owned.length} slots free; no replacement needed yet.`, opportunityCost: "", ruleNote };
  }
  const weakest = items.sort((a, b) => a.tier - b.tier || a.cost - b.cost)[0]!;
  return {
    weakest: { className: weakest.className, name: weakest.name, sellValue: sellValue(weakest, deps.rules) },
    replaceWith: target ? { className: target.className, name: target.name } : null,
    timing: rec.phase === "lane" ? "Keep it through laning." : "Replace once the next big item is affordable.",
    opportunityCost: `${weakest.name} is your lowest-tier slot (T${weakest.tier}).`,
    ruleNote,
  };
}

// ---------------------------------------------------------------------------------------------
// Active-item reminder
// ---------------------------------------------------------------------------------------------

export interface ActiveHint {
  className: string;
  name: string;
  hint: string;
}

/** Short use-case hint for owned actives. Never asserts enemy cooldowns. */
export function activeHints(deps: EngineDeps, owned: string[]): ActiveHint[] {
  const out: ActiveHint[] = [];
  for (const c of owned) {
    const it = deps.data.item(c);
    if (!it || !it.isActive) continue;
    const first = it.text.split(/(?<=\.)\s/)[0] ?? it.text;
    const notes: string[] = [];
    if (/cannot be used while stunned or slept/i.test(it.text)) notes.push("Activate before the CC lands – it cannot be used while stunned or slept.");
    if (/non-stun debuffs/i.test(it.text)) notes.push("Does not remove stuns.");
    if (itemHas(it, "bullet_immunity_active")) notes.push("Use when focused by guns, not by spirit damage.");
    if (itemHas(it, "teleport_self") || itemHas(it, "mobility_self")) notes.push("Escape or re-position tool.");
    out.push({ className: c, name: it.name, hint: [first, ...notes].join(" ").trim() });
  }
  return out;
}

// ---------------------------------------------------------------------------------------------
// Lane plan
// ---------------------------------------------------------------------------------------------

export interface LanePlan {
  opponents: { heroId: number; name: string; respect: string[] }[];
  yourTools: string[];
  note: string;
}

export function lanePlan(deps: EngineDeps, state: MatchState, now: number): LanePlan {
  const lane = readField(state, "roster.lane", now)?.value ?? [];
  const meId = readField(state, "me.hero", now)?.value;
  const owned = (readField(state, "me.items", now)?.value ?? []).map((c) => deps.data.item(c)).filter((x): x is Item => !!x);
  const abil = readField(state, "me.abilities", now)?.value;
  const opponents = lane.map((id) => {
    const p = deps.profiles.get(id);
    const respect = p?.threat.laneNotes.length ? p.threat.laneNotes : (p?.threat.threats ?? []).slice(0, 2).map((t) => `Respect ${THREAT_LABEL[t].toLowerCase()} (from kit data)`);
    return { heroId: id, name: deps.data.hero(id)?.name ?? `Hero ${id}`, respect };
  });
  const tools: string[] = [];
  const me = meId != null ? deps.data.hero(meId) : undefined;
  if (me && abil) {
    for (const a of me.abilities) {
      if (!abil.unlocked.includes(a.className)) continue;
      const mech = itemMechanicsOfAbility(a.properties);
      if (mech.length) tools.push(`${a.name}: ${mech.join(", ")}`);
    }
  }
  for (const it of owned) {
    const m = itemMechanics(it).map((h) => h.mechanic).filter((x) => ["anti_heal", "stun", "silence", "slow", "spirit_resist", "bullet_resist", "cc_immunity", "cleanse"].includes(x));
    if (m.length) tools.push(`${it.name}: ${m.join(", ")}`);
  }
  return {
    opponents,
    yourTools: tools,
    note: "Based on kit data and your inputs only. Positions and cooldowns are not known.",
  };
}

function itemMechanicsOfAbility(props: Item["properties"]): string[] {
  const out: string[] = [];
  if (props.StunDuration) out.push("stun");
  if (props.SilenceDuration) out.push("silence");
  if (props.SlowPercent) out.push("slow");
  if (props.SleepDuration) out.push("sleep");
  return out;
}

// ---------------------------------------------------------------------------------------------
// What-if
// ---------------------------------------------------------------------------------------------

export type WhatIfChange =
  | { kind: "enemyItem"; heroId: number; className: string; action: "add" | "remove" }
  | { kind: "souls"; value: number }
  | { kind: "archetype"; archetypeId: string }
  | { kind: "route"; route: "stabilise" | "normal" | "ambitious" };

export interface WhatIfResult {
  before: Recommendation;
  after: Recommendation;
  changed: boolean;
  explanation: string[];
}

export function whatIf(deps: EngineDeps, state: MatchState, prefs: Preferences, now: number, change: WhatIfChange): WhatIfResult {
  const before = recommend(deps, state, prefs, now);
  let s = state;
  let p = prefs;
  const ev = (field: FieldKey, value: unknown): MatchEvent => ({
    type: "observe",
    field,
    value,
    source: "manual",
    observedAt: now,
    gameTime: before.gameTime,
    userCorrection: true,
  });
  switch (change.kind) {
    case "enemyItem": {
      const cur = (readField(state, `enemyItems:${change.heroId}`, now)?.value as EnemyItemsValue | undefined) ?? { items: [], complete: false };
      const items = change.action === "add" ? [...new Set([...cur.items, change.className])] : cur.items.filter((c) => c !== change.className);
      s = applyEvent(state, ev(`enemyItems:${change.heroId}`, { items, complete: cur.complete }));
      break;
    }
    case "souls":
      s = applyEvent(state, ev("me.souls", change.value));
      break;
    case "archetype":
      p = { ...prefs, archetypeId: change.archetypeId };
      break;
    case "route":
      p = { ...prefs, route: change.route };
      break;
  }
  const after = recommend(deps, s, p, now);
  const explanation: string[] = [];
  const name = (x: Recommendation["buyNow"]) => x?.name ?? "nothing";
  if (before.buyNow?.className !== after.buyNow?.className) explanation.push(`BUY NOW: ${name(before.buyNow)} → ${name(after.buyNow)}`);
  if (before.saveFor?.className !== after.saveFor?.className) explanation.push(`SAVE FOR: ${name(before.saveFor)} → ${name(after.saveFor)}`);
  const bt = new Map(before.threats.map((t) => [t.kind, t.strength]));
  for (const t of after.threats) {
    const d = t.strength - (bt.get(t.kind) ?? 0);
    if (Math.abs(d) >= 0.1) explanation.push(`${THREAT_LABEL[t.kind]} ${d > 0 ? "rises" : "falls"} (${(bt.get(t.kind) ?? 0).toFixed(2)} → ${t.strength.toFixed(2)})`);
  }
  const top = after.buyNow ?? after.saveFor;
  if (top?.reasons[0]) explanation.push(`Now: ${top.reasons[0]}`);
  return { before, after, changed: explanation.length > 0, explanation: explanation.length ? explanation : ["No change in advice."] };
}

// ---------------------------------------------------------------------------------------------
// Post-match review
// ---------------------------------------------------------------------------------------------

export interface Lesson {
  category: "information" | "decision-timing" | "advice-vs-action";
  text: string;
  evidence: string;
}

/**
 * At most three lessons from decision-time information only. Never claims a purchase would
 * have won a fight or match; distinguishes advice quality from execution and outcome.
 */
export function postMatchReview(log: DecisionLog): { lessons: Lesson[]; caveat: string } {
  const lessons: Lesson[] = [];
  const e = log.entries;
  const caveat =
    "Lessons use only what was known when each piece of advice was given. Match outcome depends on execution and teammates; these are not claims that a different purchase would have won.";
  if (!e.length) return { lessons, caveat };

  const rosterOnly = e.filter((x) => x.topThreats.length && x.topThreats.every((t) => t.evidence === "roster-only")).length;
  if (rosterOnly / e.length >= 0.6) {
    lessons.push({
      category: "information",
      text: "Most advice relied on enemy hero picks only. Checking the scoreboard for the main threat's items earlier would make the advice more specific.",
      evidence: `${rosterOnly} of ${e.length} decisions had no observed enemy items or reports.`,
    });
  }

  let held = 0;
  let firstHeld: DecisionLog["entries"][number] | null = null;
  for (let i = 1; i < e.length; i++) {
    const prev = e[i - 1]!;
    const cur = e[i]!;
    if (prev.advice.buyNow && cur.advice.buyNow === prev.advice.buyNow && (cur.souls ?? 0) >= (prev.souls ?? 0) && !cur.owned.includes(prev.advice.buyNow)) {
      held++;
      firstHeld ??= prev;
    }
  }
  if (held >= 2 && firstHeld) {
    lessons.push({
      category: "decision-timing",
      text: `A recommended purchase stayed unbought across ${held + 1} checks while souls kept rising. Buying on shop visits keeps power spikes on time.`,
      evidence: `First at game time ${fmtTime(firstHeld.gameTime)} with ${firstHeld.souls ?? "?"} souls.`,
    });
  }

  let followed = 0;
  let diverged = 0;
  for (const x of e) {
    if (!x.outcome?.bought.length || !x.advice.buyNow) continue;
    if (x.outcome.bought.includes(x.advice.buyNow)) followed++;
    else diverged++;
  }
  if (followed + diverged >= 2) {
    lessons.push({
      category: "advice-vs-action",
      text:
        diverged > followed
          ? "You often bought something other than the BUY NOW item. If the advice missed something you saw, use Reject with a reason so the coach learns your context next time."
          : "Purchases mostly matched the advice; review fights rather than item choices for further gains.",
      evidence: `${followed} followed, ${diverged} different purchases.`,
    });
  }
  return { lessons: lessons.slice(0, 3), caveat };
}

function fmtTime(s: number | null): string {
  if (s == null) return "?";
  return `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;
}

/** Create a decision-log entry from a recommendation (opt-in logging). */
export function logEntry(rec: Recommendation, state: MatchState, now: number, ability: AbilityAction | null): DecisionLogEntry {
  return {
    at: now,
    gameTime: rec.gameTime,
    heroId: rec.heroId,
    souls: rec.souls,
    owned: readField(state, "me.items", now)?.value ?? [],
    enemies: readField(state, "roster.enemies", now)?.value ?? [],
    topThreats: rec.threats.slice(0, 3).map((t) => ({ kind: t.kind, strength: t.strength, evidence: t.evidence })),
    advice: {
      buyNow: rec.buyNow?.className ?? null,
      saveFor: rec.saveFor?.className ?? null,
      alternative: rec.alternative?.className ?? null,
      ability: ability?.ability ? `${ability.kind}:${ability.ability.className}:${ability.tier ?? 0}` : null,
      confidence: rec.confidence,
    },
    informationState: rec.informationState,
  };
}

export { planAbility };
