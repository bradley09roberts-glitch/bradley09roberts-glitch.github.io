import type { Compatibility, GameData } from "../data/gameData.js";
import { rulesForThreat } from "../knowledge/counterRules.js";
import { itemApplication, itemHas, itemMechanics, mechanicMagnitude, type Application, type Mechanic } from "../knowledge/mechanics.js";
import type { Archetype, ChangeIndex, HeroProfile, ProfileRegistry } from "../knowledge/profiles/index.js";
import { MODE_SUPPORT, isShopLegal, type GameRules } from "../rules/gameRules.js";
import { freeSlots, purchaseEconomics, sellValue, type PurchaseEconomics } from "../rules/inventory.js";
import { currentGameTime, readField } from "../state/matchStore.js";
import type { EnemyItemsValue, MatchState, Phase, SourceKind, Standing, ThreatKind } from "../state/types.js";
import type { Confidence, Hero, Item } from "../types.js";
import { maxActiveItems, type Preferences } from "./preferences.js";
import { THREAT_LABEL, assessThreats, resolvePhase, type AssessedThreat, type ThreatAssessment } from "./threats.js";

export interface EngineDeps {
  data: GameData;
  rules: GameRules;
  profiles: ProfileRegistry;
  changes: ChangeIndex;
  compat: Compatibility;
}

export type RouteName = "stabilise" | "normal" | "ambitious";

export interface ThreatAnswer {
  threat: ThreatKind;
  ruleId: string;
  mechanic: Mechanic;
  contribution: number;
  evidence: AssessedThreat["evidence"];
  urgent: boolean;
  note?: string | undefined;
}

export interface ScoreParts {
  fit: number;
  counter: number;
  path: number;
  phase: number;
  burden: number;
  slot: number;
  /** Negative: souls still missing (reachability). */
  distance: number;
}

export interface ScoredItem {
  item: Item;
  econ: PurchaseEconomics;
  score: number;
  parts: ScoreParts;
  answers: ThreatAnswer[];
  role: "fundamental" | "locked" | "route" | "other-route" | "situational" | "avoid";
  affordable: boolean;
  flags: string[];
  /** Owned item that must be sold to make room (full inventory). */
  requiresSell: { className: string; name: string; sellValue: number; retainScore: number } | null;
}

export interface PurchaseAction {
  className: string;
  name: string;
  image: string | null;
  cost: number;
  remainingCost: number;
  soulsShort: number;
  consumes: string[];
  requiresSell: ScoredItem["requiresSell"];
  score: number;
  reasons: string[];
  answers: ThreatAnswer[];
  flags: string[];
  pinned?: boolean;
  condition?: string;
}

export type InformationState = "manual" | "scenario" | "verified-live" | "last-observed" | "replay" | "no-data";

export interface Recommendation {
  generatedAt: number;
  gameTime: number | null;
  heroId: number | null;
  archetypeId: string | null;
  archetypeLabel: string | null;
  profileStatus: HeroProfile["status"] | null;
  route: RouteName;
  routeReason: string;
  phase: Phase;
  phaseSource: "user" | "clock" | "default";
  decision: "buy" | "save" | "no-legal-purchase" | "unsupported" | "need-info";
  buyNow: PurchaseAction | null;
  saveFor: PurchaseAction | null;
  alternative: PurchaseAction | null;
  summary: string;
  why: string[];
  delays: string | null;
  threats: AssessedThreat[];
  missingInfo: { field: string; why: string }[];
  confidence: Confidence;
  confidenceReasons: string[];
  informationState: InformationState;
  dataStatus: { compat: Compatibility; changedRefs: string[]; rulesUnverified: string[] };
  ranked: ScoredItem[];
  excluded: { className: string; name: string; reason: string }[];
  souls: number | null;
  stability: { held: boolean; reason: string | null };
}

// ---------------------------------------------------------------------------------------------

const WEAPON_STATS: Mechanic[] = ["weapon_damage", "fire_rate", "ammo"];
const SPIRIT_STATS: Mechanic[] = ["spirit_power", "cooldown_reduction", "ability_duration", "ability_range", "ability_charges"];

const PHASE_TIER: Record<Phase, Record<number, number>> = {
  lane: { 1: 0.3, 2: 0.25, 3: 0, 4: -0.3 },
  mid: { 1: -0.1, 2: 0.1, 3: 0.2, 4: 0.1 },
  late: { 1: -0.4, 2: -0.15, 3: 0.15, 4: 0.3 },
};

/**
 * Evidence factor: threats inferred only from enemy hero picks count less than threats seen in
 * enemy items, which count less than what the player reported or saw in a death recap.
 */
export const EVIDENCE_FACTOR: Record<AssessedThreat["evidence"], number> = {
  "roster-only": 0.6,
  "observed-items": 0.9,
  reported: 1.0,
};

/** How much a threat matters to *this* build (author judgement). */
function selfRelevance(t: ThreatKind, arch: Archetype): number {
  switch (t) {
    case "silence":
      return 0.4 + 0.6 * arch.damage.spirit;
    case "bullet_resist_stacking":
      return arch.damage.weapon + 0.5 * arch.damage.melee;
    case "spirit_resist_stacking":
      return arch.damage.spirit;
    default:
      return 1;
  }
}

export function applicationReliability(apps: Application[], arch: Archetype): number {
  let best = 0;
  for (const a of apps) {
    let r = 0;
    switch (a) {
      case "bullets": r = 0.2 + 0.8 * arch.damage.weapon; break;
      case "headshots": r = 0.1 + 0.6 * arch.damage.weapon; break;
      case "spirit_damage": r = 0.2 + 0.8 * arch.damage.spirit; break;
      case "ultimate_damage": r = 0.3 + 0.4 * arch.damage.spirit; break;
      case "melee": r = 0.2 + 0.8 * arch.damage.melee; break;
      case "parry": r = 0.5; break;
      case "active_target": r = 0.75; break;
      case "active_area": r = 0.7; break;
      case "aura": r = arch.range === "close" ? 0.9 : arch.range === "mid" ? 0.7 : 0.45; break;
      case "reactive": r = 0.9; break;
      case "active_self": r = 0.6; break;
      case "passive_self": r = 0.1; break;
    }
    best = Math.max(best, r);
  }
  return Math.min(1, best);
}

interface Ctx {
  deps: EngineDeps;
  hero: Hero;
  profile: HeroProfile;
  arch: Archetype;
  route: RouteName;
  phase: Phase;
  owned: string[];
  souls: number | null;
  assessment: ThreatAssessment;
  allies: { heroId: number; items: string[]; arch: Archetype | null }[];
  prefs: Preferences;
  activesOwned: number;
  free: number;
}

function routeItems(arch: Archetype, route: RouteName): string[] {
  return arch.routes[route].items;
}

function roleOf(cn: string, ctx: Ctx): ScoredItem["role"] {
  if (ctx.prefs.lockedCore.includes(cn)) return "locked";
  if (ctx.arch.fundamentals.includes(cn)) return "fundamental";
  if (ctx.arch.avoid.includes(cn)) return "avoid";
  if (routeItems(ctx.arch, ctx.route).includes(cn)) return "route";
  if ((["stabilise", "normal", "ambitious"] as RouteName[]).some((r) => ctx.arch.routes[r].items.includes(cn))) return "other-route";
  return "situational";
}

function fitScore(item: Item, ctx: Ctx, role: ScoredItem["role"]): { v: number; reasons: string[] } {
  const reasons: string[] = [];
  let v = 0;
  const posBonus = (list: string[]) => {
    const i = list.indexOf(item.className);
    return i < 0 ? 0 : 1 - i / Math.max(1, list.length);
  };
  switch (role) {
    case "locked":
      v = 1.5;
      reasons.push("Locked core item");
      break;
    case "fundamental":
      v = 1.0 + 0.4 * posBonus(ctx.arch.fundamentals);
      reasons.push(`Build fundamental for ${ctx.arch.label}`);
      break;
    case "route":
      v = 0.75 + 0.3 * posBonus(routeItems(ctx.arch, ctx.route));
      reasons.push(`Next on the ${ctx.route} route`);
      break;
    case "other-route":
      v = 0.45;
      break;
    case "avoid":
      v = -1.2;
      reasons.push(`Conflicts with ${ctx.arch.label} identity`);
      break;
    default:
      v = 0;
  }
  const d = ctx.arch.damage;
  let align = 0;
  if (WEAPON_STATS.some((m) => itemHas(item, m))) align = Math.max(align, d.weapon);
  if (SPIRIT_STATS.some((m) => itemHas(item, m))) align = Math.max(align, d.spirit);
  if (itemHas(item, "melee_damage")) align = Math.max(align, d.melee);
  v += 0.3 * align;
  const list = ctx.phase === "lane" ? ctx.hero.popularItems.early : ctx.phase === "mid" ? ctx.hero.popularItems.mid : ctx.hero.popularItems.late;
  const pop = list.find((p) => p.className === item.className);
  if (pop) v += 0.25 * (pop.pickPct / 100);
  return { v, reasons };
}

function allyCoverageFactor(m: Mechanic, ctx: Ctx): { factor: number; by: string | null } {
  if (ctx.phase === "lane") return { factor: 1, by: null };
  let best = 0;
  let by: string | null = null;
  for (const a of ctx.allies) {
    if (!a.arch) continue;
    for (const cn of a.items) {
      const it = ctx.deps.data.item(cn);
      if (!it || !itemHas(it, m)) continue;
      const rel = applicationReliability(itemApplication(it), a.arch);
      if (rel > best) {
        best = rel;
        by = `${ctx.deps.data.hero(a.heroId)?.name ?? "Ally"} (${it.name})`;
      }
    }
  }
  return { factor: 1 - 0.5 * best, by };
}

function counterScore(item: Item, ctx: Ctx, forRetention: boolean): { v: number; answers: ThreatAnswer[]; flags: string[] } {
  const answers: ThreatAnswer[] = [];
  const flags: string[] = [];
  const changed = ctx.deps.changes.changed.has(`item:${item.className}`);
  let total = 0;
  const ownedOthers = ctx.owned.filter((o) => o !== item.className).map((o) => ctx.deps.data.item(o)).filter((x): x is Item => !!x);
  for (const t of ctx.assessment.threats) {
    let best = 0;
    let bestAns: ThreatAnswer | null = null;
    for (const rule of rulesForThreat(t.kind)) {
      for (const resp of rule.responses) {
        if (!itemHas(item, resp.mechanic)) continue;
        const mag = Math.min(1.2, mechanicMagnitude(item, resp.mechanic));
        const rel = resp.offensive ? applicationReliability(itemApplication(item), ctx.arch) : item.isActive ? 0.85 : 1;
        let exc = 1;
        const notes: string[] = [];
        for (const ex of rule.exceptions) {
          if (ex.appliesTo && !ex.appliesTo.includes(resp.mechanic)) continue;
          if (ex.kind === "already_owned" && ownedOthers.some((o) => itemHas(o, resp.mechanic))) {
            exc *= ex.factor;
            notes.push(ex.note);
          }
          if (ex.kind === "ally_covers" && resp.offensive) {
            const c = allyCoverageFactor(resp.mechanic, ctx);
            if (c.by) {
              exc *= c.factor;
              notes.push(`${c.by} already applies this; counted as partial coverage.`);
            }
          }
          if (ex.kind === "target_has_cc_answer" && t.heroIds.some((h) => ctx.assessment.ccAnswerHeroIds.includes(h))) {
            exc *= ex.factor;
            notes.push(ex.note);
          }
        }
        const v = resp.weight * mag * rel * exc;
        if (v > best) {
          best = v;
          bestAns = {
            threat: t.kind,
            ruleId: rule.id,
            mechanic: resp.mechanic,
            contribution: 0,
            evidence: t.evidence,
            urgent: t.urgent,
            note: [resp.note, rel < 0.5 ? `Your build applies this unreliably (${itemApplication(item).join("/")})` : undefined, ...notes].filter(Boolean).join(" ") || undefined,
          };
        }
      }
    }
    if (!bestAns || best <= 0) continue;
    let c = t.strength * EVIDENCE_FACTOR[t.evidence] * selfRelevance(t.kind, ctx.arch) * best * (t.urgent && !forRetention ? ctx.prefs.weights.urgentMultiplier : 1);
    if (changed) c *= 0.5;
    bestAns.contribution = Math.round(c * 1000) / 1000;
    total += c;
    answers.push(bestAns);
  }
  if (changed && answers.length) flags.push("Item changed since rules were reviewed; counter value halved until re-checked");
  answers.sort((a, b) => b.contribution - a.contribution);
  return { v: Math.min(2.2, total), answers, flags };
}

function pathScore(item: Item, ctx: Ctx, econ: PurchaseEconomics): number {
  const planned = new Set([...ctx.arch.fundamentals, ...routeItems(ctx.arch, ctx.route), ...ctx.prefs.lockedCore]);
  let v = 0;
  for (const up of ctx.deps.data.upgradesFrom(item.className)) if (planned.has(up.className) && !ctx.owned.includes(up.className)) v = Math.max(v, 0.4);
  if (econ.consumes.length) v += 0.2;
  return v;
}

function scoreItem(item: Item, ctx: Ctx, forRetention = false): ScoredItem {
  const econ = purchaseEconomics(item, ctx.owned, ctx.deps.data, ctx.deps.rules);
  const role = roleOf(item.className, ctx);
  const fit = fitScore(item, ctx, role);
  const counter = counterScore(item, ctx, forRetention);
  const path = forRetention ? 0 : pathScore(item, ctx, econ);
  const phase = PHASE_TIER[ctx.phase][item.tier] ?? 0;
  const W = ctx.prefs.weights;
  const flags = [...counter.flags];
  let burden = 0;
  if (!forRetention && item.isActive) {
    const max = maxActiveItems(ctx.prefs.difficulty);
    if (ctx.activesOwned >= max) {
      burden = -0.35 * (ctx.activesOwned - max + 1);
      flags.push(`Active item: you already have ${ctx.activesOwned} actives (preference: ${ctx.prefs.difficulty})`);
    } else flags.push("Active item: needs manual use");
  }
  let slot = 0;
  if (!forRetention && econ.slotDelta === 1) {
    if (ctx.free === 0) slot = -0.25;
    else if (ctx.free <= 2 && item.tier <= 2 && ctx.phase !== "lane") slot = -0.2;
  }
  const short = ctx.souls == null || forRetention ? 0 : Math.max(0, econ.remainingCost - ctx.souls);
  const distance = -W.distancePer1600 * Math.min(4, short / 1600);
  const parts: ScoreParts = {
    fit: fit.v,
    counter: counter.v,
    path,
    phase,
    burden,
    slot,
    distance,
  };
  const score = W.fit * parts.fit + W.counter * parts.counter + W.path * parts.path + W.phase * parts.phase + parts.burden + parts.slot + parts.distance;
  return {
    item,
    econ,
    score: Math.round(score * 1000) / 1000,
    parts,
    answers: counter.answers,
    role,
    affordable: ctx.souls != null && econ.remainingCost <= ctx.souls,
    flags: [...fit.reasons.length ? [] : [], ...flags],
    requiresSell: null,
  };
}

function reasonsFor(s: ScoredItem, ctx: Ctx): string[] {
  const r: string[] = [];
  const top = s.answers[0];
  if (top && top.contribution >= 0.08) {
    const t = ctx.assessment.threats.find((x) => x.kind === top.threat);
    r.push(
      `Answers ${THREAT_LABEL[top.threat].toLowerCase()} (${evidenceText(top.evidence)}${top.urgent ? ", urgent" : ""})` +
        (t?.contributions[0] ? ` – ${t.contributions[0].detail}` : ""),
    );
  }
  if (s.role === "fundamental") r.push(`Core of ${ctx.arch.label}`);
  if (s.role === "locked") r.push("You locked this as core");
  if (s.role === "route") r.push(`Next on the ${ctx.route} route`);
  if (s.econ.consumes.length) {
    r.push(`Upgrades your ${s.econ.consumes.map((c) => ctx.deps.data.item(c)?.name ?? c).join(" + ")} (pays only the difference)`);
  }
  if (s.parts.path > 0.3) r.push("Builds toward a planned item");
  if (top?.note && top.contribution >= 0.08) r.push(top.note);
  return r;
}

function evidenceText(e: AssessedThreat["evidence"]): string {
  return e === "reported" ? "from your report or death recap" : e === "observed-items" ? "seen in enemy items" : "inferred from enemy heroes only";
}

function toAction(s: ScoredItem, ctx: Ctx, extra: Partial<PurchaseAction> = {}): PurchaseAction {
  return {
    className: s.item.className,
    name: s.item.name,
    image: s.item.image,
    cost: s.item.cost,
    remainingCost: s.econ.remainingCost,
    soulsShort: ctx.souls == null ? s.econ.remainingCost : Math.max(0, s.econ.remainingCost - ctx.souls),
    consumes: s.econ.consumes,
    requiresSell: s.requiresSell,
    score: s.score,
    reasons: reasonsFor(s, ctx),
    answers: s.answers.slice(0, 3),
    flags: s.flags,
    ...extra,
  };
}

function informationState(state: MatchState, now: number): InformationState {
  if (state.storeKind === "replay") return "replay";
  const keys = ["me.souls", "me.items", "roster.enemies"] as const;
  const reads = keys.map((k) => readField(state, k, now)).filter((x) => x != null);
  if (!reads.length) return "no-data";
  const sources = new Set<SourceKind>(reads.map((r) => r!.obs.source));
  if (sources.has("live") && reads.every((r) => r!.freshness !== "stale")) return "verified-live";
  if (reads.some((r) => r!.freshness === "stale" && (r!.obs.source === "live" || r!.obs.source === "screen"))) return "last-observed";
  if (sources.has("scenario")) return "scenario";
  return "manual";
}

function chooseRoute(prefs: Preferences, standing: Standing): { route: RouteName; reason: string } {
  if (prefs.route !== "auto") return { route: prefs.route, reason: `You chose the ${prefs.route} route` };
  if (standing === "behind") return { route: "stabilise", reason: "You marked yourself behind: cheaper stabilising route" };
  if (standing === "ahead") return { route: "ambitious", reason: "You marked yourself ahead: ambitious route" };
  return { route: "normal", reason: standing === "even" ? "Even game: normal route" : "Ahead/behind unknown: normal route" };
}

/**
 * Candidate generation, legality checks and scoring. Illegal, owned, covered-by-upgrade,
 * rejected and deferred items are removed before scoring.
 */
function generateCandidates(ctx: Ctx, gameTime: number, excluded: Recommendation["excluded"] | null): ScoredItem[] {
  const { deps, owned, prefs, hero, souls } = ctx;
  const candidates: ScoredItem[] = [];
  for (const item of deps.data.items()) {
    const legal = isShopLegal(item, hero.id, deps.rules);
    if (!legal.legal) continue;
    if (owned.includes(item.className)) continue;
    const econ = purchaseEconomics(item, owned, deps.data, deps.rules);
    if (econ.coveredByOwnedUpgrade) continue;
    const rej = prefs.rejected.find((r) => r.className === item.className);
    if (rej) {
      excluded?.push({ className: item.className, name: item.name, reason: `Rejected by you: ${rej.reason}` });
      continue;
    }
    const def = prefs.deferred.find((r) => r.className === item.className);
    if (def && (def.untilGameTime == null || gameTime < def.untilGameTime)) {
      excluded?.push({ className: item.className, name: item.name, reason: `Deferred by you: ${def.reason}` });
      continue;
    }
    candidates.push(scoreItem(item, ctx));
  }

  // Full inventory: candidates that need a new slot must replace the weakest retained item.
  if (ctx.free === 0) {
    const retained = owned
      .map((c) => deps.data.item(c))
      .filter((x): x is Item => !!x && !prefs.lockedCore.includes(x.className))
      .map((it) => ({ it, s: scoreItem(it, ctx, true).score }))
      .sort((a, b) => a.s - b.s);
    const weakest = retained[0];
    for (const c of candidates) {
      if (c.econ.slotDelta === 0) continue;
      if (!weakest) {
        c.score = -99;
        c.flags.push("Inventory full and every item is locked");
        continue;
      }
      const sv = sellValue(weakest.it, deps.rules);
      c.requiresSell = { className: weakest.it.className, name: weakest.it.name, sellValue: sv, retainScore: weakest.s };
      // Net value: candidate must beat what is lost; resale (unverified rule) offsets cost.
      c.score = Math.round((c.score - Math.max(0, weakest.s)) * 1000) / 1000;
      c.affordable = souls != null && c.econ.remainingCost <= souls + sv;
      c.flags.push(`Inventory full: sell ${weakest.it.name} (≈${sv} souls, resale rule unverified)`);
    }
  }
  return candidates.sort((a, b) => b.score - a.score);
}

/** Context after hypothetically buying `s` (used to choose the next SAVE FOR target). */
function afterPurchase(ctx: Ctx, s: ScoredItem): Ctx {
  const owned = ctx.owned.filter((o) => !s.econ.consumes.includes(o) && o !== s.requiresSell?.className).concat(s.item.className);
  const spent = s.econ.remainingCost - (s.requiresSell?.sellValue ?? 0);
  return {
    ...ctx,
    owned,
    souls: ctx.souls == null ? null : Math.max(0, ctx.souls - spent),
    activesOwned: ctx.activesOwned + (s.item.isActive ? 1 : 0),
    free: freeSlots(owned, ctx.deps.rules),
  };
}

/** Tie-break bonuses used only to pick BUY NOW among affordable items (documented in SCORING.md). */
const URGENT_BUY_BONUS = 0.15;
const COMPONENT_BUY_BONUS = 0.25;

// ---------------------------------------------------------------------------------------------

export function recommend(deps: EngineDeps, state: MatchState, prefs: Preferences, now: number, previous?: Recommendation | null): Recommendation {
  const gameTime = currentGameTime(state, now);
  const { phase, source: phaseSource } = resolvePhase(state, now, gameTime);
  const missingInfo: Recommendation["missingInfo"] = [];
  const base = (partial: Partial<Recommendation>): Recommendation => ({
    generatedAt: now,
    gameTime,
    heroId: null,
    archetypeId: null,
    archetypeLabel: null,
    profileStatus: null,
    route: "normal",
    routeReason: "",
    phase,
    phaseSource,
    decision: "need-info",
    buyNow: null,
    saveFor: null,
    alternative: null,
    summary: "",
    why: [],
    delays: null,
    threats: [],
    missingInfo,
    confidence: "low",
    confidenceReasons: [],
    informationState: informationState(state, now),
    dataStatus: { compat: deps.compat, changedRefs: [], rulesUnverified: [] },
    ranked: [],
    excluded: [],
    souls: null,
    stability: { held: false, reason: null },
    ...partial,
  });

  const mode = MODE_SUPPORT[state.mode];
  if (!mode.supported) {
    return base({ decision: "unsupported", summary: `${mode.label} is not supported`, why: [mode.note] });
  }
  const heroRead = readField(state, "me.hero", now);
  const hero = heroRead ? deps.data.hero(heroRead.value) : undefined;
  if (!hero) {
    missingInfo.push({ field: "me.hero", why: "Pick your hero to get any advice." });
    return base({ summary: "Pick your hero", why: ["No hero selected."] });
  }
  const profile = deps.profiles.get(hero.id)!;
  const arch = deps.profiles.archetype(hero.id, prefs.archetypeId)!;
  const standing = readField(state, "match.standing", now)?.value ?? "unknown";
  const { route, reason: routeReason } = chooseRoute(prefs, standing);
  const soulsRead = readField(state, "me.souls", now);
  const souls = soulsRead?.value ?? null;
  const owned = readField(state, "me.items", now)?.value ?? [];
  const assessment = assessThreats(state, deps.data, deps.profiles, now, phase);
  const allies = (readField(state, "roster.allies", now)?.value ?? []).map((id) => {
    const v = readField(state, `allyItems:${id}`, now)?.value as EnemyItemsValue | undefined;
    return { heroId: id, items: v?.items ?? [], arch: deps.profiles.archetype(id, null) };
  });

  const ctx: Ctx = {
    deps,
    hero,
    profile,
    arch,
    route,
    phase,
    owned,
    souls,
    assessment,
    allies,
    prefs,
    activesOwned: owned.filter((c) => deps.data.item(c)?.isActive).length,
    free: freeSlots(owned, deps.rules),
  };

  // --- candidate generation + legality -------------------------------------------------------
  const excluded: Recommendation["excluded"] = [];
  const candidates = generateCandidates(ctx, gameTime ?? 0, excluded);
  // Input consistency: owning an item together with its listed component is not possible when
  // upgrades consume components. Flag it instead of silently trusting either.
  for (const o of owned) {
    const it = deps.data.item(o);
    for (const c of it?.components ?? []) {
      if (owned.includes(c) && deps.rules.componentUpgrade.value.consumesComponent) {
        missingInfo.push({ field: "me.items", why: `Check your items: ${deps.data.item(c)?.name ?? c} is normally replaced when you buy ${it!.name}.` });
      }
    }
  }
  if (souls == null) missingInfo.push({ field: "me.souls", why: "Enter your souls to choose between buying now and saving." });
  if (!readField(state, "roster.enemies", now)) missingInfo.push({ field: "roster.enemies", why: "Add enemy heroes so advice reacts to threats." });
  else if (!Object.keys(state.fields).some((k) => k.startsWith("enemyItems:")) && phase !== "lane") {
    missingInfo.push({ field: "enemyItems", why: "Add visible items of the enemy causing the most trouble (scoreboard)." });
  }
  if (phase === "lane" && !readField(state, "roster.lane", now)) missingInfo.push({ field: "roster.lane", why: "Set your lane opponents to focus lane advice." });
  if (standing === "unknown") missingInfo.push({ field: "match.standing", why: "Mark ahead/even/behind to pick a stabilising or ambitious route." });
  if (phaseSource === "default") missingInfo.push({ field: "match.gameTime", why: "Enter the match time (or phase) so item tiers fit the game stage." });

  const positive = candidates.filter((c) => c.score > 0);
  const pinned = prefs.pinned.map((p) => candidates.find((c) => c.item.className === p.className)).filter((x): x is ScoredItem => !!x);

  let buyNow: PurchaseAction | null = null;
  let saveFor: PurchaseAction | null = null;
  let alternative: PurchaseAction | null = null;
  let decision: Recommendation["decision"] = "no-legal-purchase";
  let delays: string | null = null;
  const why: string[] = [];

  const best = pinned[0] ?? positive[0];
  if (!best) {
    const fullMsg = ctx.free === 0 ? "Inventory is full and no candidate beats your weakest item; keep your current items." : "No legal purchase improves your build right now.";
    return base({
      heroId: hero.id,
      archetypeId: arch.id,
      archetypeLabel: arch.label,
      profileStatus: profile.status,
      route,
      routeReason,
      decision: "no-legal-purchase",
      summary: fullMsg,
      why: [fullMsg],
      threats: assessment.threats,
      ranked: candidates.slice(0, 12),
      excluded,
      souls,
      confidence: "medium",
      confidenceReasons: ["No positive-value purchase found."],
    });
  }

  const threshold = route === "ambitious" ? Math.min(0.95, prefs.weights.buyThreshold + 0.1) : route === "stabilise" ? prefs.weights.buyThreshold - 0.15 : prefs.weights.buyThreshold;
  const affordable = positive.filter((c) => c.affordable);
  const isPinned = (s: ScoredItem) => prefs.pinned.some((p) => p.className === s.item.className);

  const isComp = (c: ScoredItem) => best.item.components.includes(c.item.className);
  const urgentAnswer = (c: ScoredItem) => c.answers.some((a) => a.urgent && a.contribution >= 0.25);
  const buyRank = (c: ScoredItem) =>
    c.score + (urgentAnswer(c) ? URGENT_BUY_BONUS : 0) + (!best.affordable && isComp(c) ? COMPONENT_BUY_BONUS : 0) + (isPinned(c) ? 1 : 0);
  /** Next target after a hypothetical purchase, rescored so redundancy is accounted for. */
  const nextTarget = (bought: ScoredItem): PurchaseAction | null => {
    const ctx2 = afterPurchase(ctx, bought);
    const next = generateCandidates(ctx2, gameTime ?? 0, null).find((c) => c.score > 0 && c.item.className !== bought.item.className);
    if (!next) return null;
    return toAction(next, ctx2);
  };

  if (souls == null) {
    decision = "save";
    saveFor = toAction(best, ctx, { pinned: isPinned(best) });
    why.push("Souls unknown: showing the next target only.");
  } else {
    const pick = affordable.slice().sort((a, b) => buyRank(b) - buyRank(a))[0];
    if (best.affordable && pick) {
      // Best overall is affordable: buy the top-ranked affordable item (urgency may promote a
      // close counter over the core item).
      decision = "buy";
      buyNow = toAction(pick, ctx, { pinned: isPinned(pick) });
      if (pick !== best && urgentAnswer(pick)) buyNow.reasons.unshift("Urgent, well-supported threat");
      saveFor = nextTarget(pick);
    } else if (pick && (isComp(pick) || isPinned(pick) || pick.score >= threshold * best.score || urgentAnswer(pick))) {
      // Best is unaffordable: a component is progress without waste; otherwise the affordable
      // item must be close in value or answer an urgent, well-supported threat.
      decision = "buy";
      buyNow = toAction(pick, ctx, { pinned: isPinned(pick) });
      if (isComp(pick)) {
        buyNow.reasons.unshift(`Component of ${best.item.name}: its cost counts toward the upgrade`);
        saveFor = toAction(best, ctx, { pinned: isPinned(best), soulsShort: Math.max(0, best.econ.remainingCost - souls) });
      } else {
        if (urgentAnswer(pick) && pick.score < threshold * best.score) buyNow.reasons.unshift("Urgent, well-supported threat: worth delaying the bigger item");
        const after = nextTarget(pick);
        saveFor = after ?? toAction(best, ctx, { pinned: isPinned(best) });
        if (after?.className === best.item.className || !after) delays = `Delays ${best.item.name} by ${pick.econ.remainingCost} souls.`;
        else delays = `${best.item.name} drops in priority once ${pick.item.name} is bought.`;
      }
    } else {
      decision = "save";
      saveFor = toAction(best, ctx, { pinned: isPinned(best) });
      if (pick) delays = `Buying ${pick.item.name} now would delay ${best.item.name} by ${pick.econ.remainingCost} souls for less value.`;
    }
  }

  // A pinned target the user chose stays the SAVE FOR target until bought, unpinned or affordable.
  const pinnedTarget = pinned.find((p) => p.item.className !== buyNow?.className);
  if (pinnedTarget && saveFor?.className !== pinnedTarget.item.className && souls != null) {
    const after = buyNow ? souls - buyNow.remainingCost : souls;
    saveFor = toAction(pinnedTarget, ctx, { pinned: true, soulsShort: Math.max(0, pinnedTarget.econ.remainingCost - after) });
    saveFor.reasons.unshift("Pinned by you");
  }

  // Situational alternative: different primary threat/mechanic or different role.
  const chosen = new Set([buyNow?.className, saveFor?.className].filter(Boolean) as string[]);
  const primaryThreat = (buyNow ?? saveFor)?.answers[0]?.threat;
  const altCand = positive.find((c) => !chosen.has(c.item.className) && c.answers[0] && c.answers[0].threat !== primaryThreat && c.answers[0].contribution >= 0.05)
    ?? positive.find((c) => !chosen.has(c.item.className) && c.role !== (buyNow ? positive.find((p) => p.item.className === buyNow!.className)?.role : best.role));
  if (altCand) {
    const a0 = altCand.answers[0];
    const condition = a0
      ? `Better if ${THREAT_LABEL[a0.threat].toLowerCase()} becomes your main problem${a0.evidence === "roster-only" ? " (currently only inferred from heroes)" : ""}.`
      : altCand.role === "other-route"
        ? "Better if the game state changes (another route lists it)."
        : "A different direction for your build.";
    alternative = toAction(altCand, ctx, { condition });
  }

  // --- stability ------------------------------------------------------------------------------
  let stability: Recommendation["stability"] = { held: false, reason: null };
  if (previous && previous.heroId === hero.id && previous.buyNow && buyNow && previous.buyNow.className !== buyNow.className) {
    const prevNow = candidates.find((c) => c.item.className === previous.buyNow!.className);
    const newTop = candidates.find((c) => c.item.className === buyNow!.className)!;
    const urgentChange = newTop.answers.some((a) => a.urgent && a.contribution >= 0.25) && !(prevNow?.answers.some((a) => a.urgent));
    if (prevNow && prevNow.affordable && prevNow.score > 0 && newTop.score < prevNow.score * (1 + prefs.weights.stabilityMargin) && !urgentChange) {
      buyNow = toAction(prevNow, ctx);
      stability = { held: true, reason: `Kept ${prevNow.item.name}: ${newTop.item.name} scores only marginally higher.` };
    } else if (urgentChange) {
      stability = { held: false, reason: `Changed to ${newTop.item.name}: urgent, well-supported threat.` };
    }
  }

  // --- explanation + confidence ----------------------------------------------------------------
  const head = buyNow ?? saveFor;
  if (head) why.push(...head.reasons);
  if (decision === "save" && saveFor) why.unshift(`Save: ${saveFor.name} needs ${saveFor.soulsShort} more souls.`);
  if (alternative?.condition) why.push(`Alternative: ${alternative.name} – ${alternative.condition}`);

  const confidenceReasons: string[] = [];
  let level = 3;
  if (profile.status === "auto") {
    level--;
    confidenceReasons.push("Basic data-derived hero profile (not curated)");
  }
  if (!soulsRead) {
    level--;
    confidenceReasons.push("Souls not entered");
  } else if (soulsRead.freshness !== "fresh") {
    level--;
    confidenceReasons.push(`Souls entered ${Math.round(soulsRead.ageS / 60)} min ago`);
  }
  if (!readField(state, "roster.enemies", now)) {
    level--;
    confidenceReasons.push("No enemy roster");
  }
  if (deps.compat.status === "outdated") {
    level = Math.min(level, 1);
    confidenceReasons.push(deps.compat.message);
  }
  const changedRefs = deps.profiles.staleRefs(profile);
  if (changedRefs.length) {
    level--;
    confidenceReasons.push(`${changedRefs.length} profile references changed since review`);
  }
  const headScored = head ? candidates.find((c) => c.item.className === head.className) : undefined;
  if (headScored?.answers[0]?.evidence === "roster-only" && headScored.parts.counter > headScored.parts.fit) {
    level--;
    confidenceReasons.push("Top choice is driven by threats inferred from heroes only");
  }
  const rulesUnverified: string[] = [];
  if (head?.consumes.length) rulesUnverified.push("Component upgrade pricing (community rule)");
  if (head?.requiresSell) rulesUnverified.push("Sell-back value (community rule)");
  if (ctx.free <= 1) rulesUnverified.push(`Inventory size ${deps.rules.inventory.value.totalSlots} (community rule)`);
  const confidence: Confidence = level >= 3 ? "high" : level === 2 ? "medium" : "low";
  if (!confidenceReasons.length) confidenceReasons.push("Curated profile, fresh inputs, current data");

  const summary =
    decision === "buy" && buyNow
      ? `Buy ${buyNow.name}${saveFor ? `, then save for ${saveFor.name}` : ""}`
      : decision === "save" && saveFor
        ? `Save for ${saveFor.name} (${saveFor.soulsShort} more souls)`
        : "No purchase";

  return base({
    heroId: hero.id,
    archetypeId: arch.id,
    archetypeLabel: arch.label,
    profileStatus: profile.status,
    route,
    routeReason,
    decision,
    buyNow,
    saveFor,
    alternative,
    summary,
    why,
    delays,
    threats: assessment.threats,
    confidence,
    confidenceReasons,
    dataStatus: { compat: deps.compat, changedRefs, rulesUnverified },
    ranked: candidates.slice(0, 15),
    excluded,
    souls,
    stability,
  });
}

/** Mechanic summary for display. */
export function describeItemMechanics(item: Item): string[] {
  return itemMechanics(item).map((h) => `${h.mechanic}${h.via === "text" ? " (from description)" : ""}`);
}

export type { ThreatAssessment };
