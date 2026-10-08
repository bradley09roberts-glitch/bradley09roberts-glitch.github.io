import type { GameData } from "../../data/gameData.js";
import { abilityFingerprint, itemFingerprint } from "../../data/gameData.js";
import { autoProfile } from "./auto.js";
import { CURATED_PROFILES } from "./curated.js";
import type { Archetype, HeroProfile } from "./types.js";

export * from "./types.js";
export { autoProfile, decodeOrder, statisticalAbilityPlan, damageMixFromPopular, threatSignatureFromData } from "./auto.js";
export { CURATED_PROFILES } from "./curated.js";

/**
 * Review stamps: fingerprints of every item/ability as they were when the knowledge base was
 * last checked. Entities whose current fingerprint differs are "changed since review" and the
 * advice that depends on them is invalidated or labelled until re-checked.
 */
export interface ReviewStamps {
  build: number | null;
  contentHash: string;
  generatedAt: string;
  entities: Record<string, string>;
}

export function buildReviewStamps(data: GameData): ReviewStamps {
  const entities: Record<string, string> = {};
  for (const it of data.items()) entities[`item:${it.className}`] = itemFingerprint(it);
  for (const h of data.snapshot.heroes.filter((x) => x.playable)) {
    for (const a of h.abilities) entities[`ability:${a.className}`] = abilityFingerprint(a);
  }
  return { build: data.build, contentHash: data.snapshot.meta.contentHash, generatedAt: new Date().toISOString(), entities };
}

export interface ChangeIndex {
  /** item/ability keys whose data changed (or disappeared) since review. */
  changed: Set<string>;
  /** keys not present at review time (new content). */
  unreviewed: Set<string>;
  reviewBuild: number | null;
}

export function changeIndex(data: GameData, stamps: ReviewStamps | null): ChangeIndex {
  const changed = new Set<string>();
  const unreviewed = new Set<string>();
  if (!stamps) {
    for (const it of data.items()) unreviewed.add(`item:${it.className}`);
    return { changed, unreviewed, reviewBuild: null };
  }
  const current = buildReviewStamps(data).entities;
  for (const [k, fp] of Object.entries(stamps.entities)) {
    if (current[k] == null) changed.add(k);
    else if (current[k] !== fp) changed.add(k);
  }
  for (const k of Object.keys(current)) if (!(k in stamps.entities)) unreviewed.add(k);
  return { changed, unreviewed, reviewBuild: stamps.build };
}

export interface ProfileIssue {
  heroId: number;
  severity: "error" | "warning";
  message: string;
}

/** Validate a profile's references against current data. */
export function validateProfile(p: HeroProfile, data: GameData): ProfileIssue[] {
  const issues: ProfileIssue[] = [];
  const hero = data.hero(p.heroId);
  if (!hero) return [{ heroId: p.heroId, severity: "error", message: "hero not in data" }];
  if (hero.className !== p.heroClass) issues.push({ heroId: p.heroId, severity: "error", message: `class mismatch ${p.heroClass} vs ${hero.className}` });
  const heroAbilities = new Set(hero.abilities.map((a) => a.className));
  for (const a of p.archetypes) {
    const all = [...a.fundamentals, ...a.avoid, ...a.routes.stabilise.items, ...a.routes.normal.items, ...a.routes.ambitious.items];
    for (const c of all) {
      const it = data.item(c);
      if (!it) issues.push({ heroId: p.heroId, severity: "error", message: `${a.id}: item ${c} not in current data` });
      else if (it.tier > 4) issues.push({ heroId: p.heroId, severity: "error", message: `${a.id}: item ${c} is tier ${it.tier} (not sold)` });
    }
    const unlock = a.abilityPlan.unlockOrder;
    if (unlock.length !== hero.abilities.length || new Set(unlock).size !== unlock.length) {
      issues.push({ heroId: p.heroId, severity: "error", message: `${a.id}: unlock order must list each ability once` });
    }
    for (const c of unlock) if (!heroAbilities.has(c)) issues.push({ heroId: p.heroId, severity: "error", message: `${a.id}: ability ${c} not on hero` });
    const tiers = new Map<string, number>();
    for (const st of a.abilityPlan.upgrades) {
      if (!heroAbilities.has(st.ability)) issues.push({ heroId: p.heroId, severity: "error", message: `${a.id}: upgrade for unknown ability ${st.ability}` });
      const prev = tiers.get(st.ability) ?? 0;
      if (st.tier !== prev + 1) issues.push({ heroId: p.heroId, severity: "error", message: `${a.id}: ${st.ability} T${st.tier} listed after T${prev}` });
      tiers.set(st.ability, st.tier);
    }
    const dmg = a.damage.weapon + a.damage.spirit + a.damage.melee;
    if (Math.abs(dmg - 1) > 0.05) issues.push({ heroId: p.heroId, severity: "warning", message: `${a.id}: damage mix sums to ${dmg.toFixed(2)}` });
  }
  return issues;
}

export type CoverageLevel = "curated" | "curated-stale" | "auto" | "auto-limited";

export interface ProfileCoverageRow {
  heroId: number;
  name: string;
  level: CoverageLevel;
  archetypes: number;
  issues: string[];
  abilityPlanSource: string;
  popularDataLists: number;
  changedSinceReview: string[];
  inDevelopmentFlag: boolean;
}

export class ProfileRegistry {
  private readonly curated = new Map<number, HeroProfile>();
  private readonly autoCache = new Map<number, HeroProfile>();

  constructor(
    private readonly data: GameData,
    private readonly changes: ChangeIndex,
    curated: HeroProfile[] = CURATED_PROFILES,
  ) {
    for (const p of curated) {
      const errors = validateProfile(p, data).filter((i) => i.severity === "error");
      // A curated profile with broken references is not used; the auto profile takes over.
      if (!errors.length) this.curated.set(p.heroId, p);
    }
  }

  get(heroId: number): HeroProfile | null {
    const c = this.curated.get(heroId);
    if (c) return c;
    const cached = this.autoCache.get(heroId);
    if (cached) return cached;
    const hero = this.data.hero(heroId);
    if (!hero) return null;
    const p = autoProfile(hero, this.data);
    this.autoCache.set(heroId, p);
    return p;
  }

  /** Entities this profile depends on that changed since review. */
  staleRefs(p: HeroProfile): string[] {
    return p.dependsOn.map((d) => `${d.kind}:${d.className}`).filter((k) => this.changes.changed.has(k));
  }

  level(p: HeroProfile): CoverageLevel {
    if (p.status === "curated") return this.staleRefs(p).length ? "curated-stale" : "curated";
    const a = p.archetypes[0];
    const hero = this.data.hero(p.heroId);
    const thin = !a || a.fundamentals.length < 2 || !hero?.abilityOrders.orders.length;
    return thin ? "auto-limited" : "auto";
  }

  archetype(heroId: number, archetypeId: string | null): Archetype | null {
    const p = this.get(heroId);
    if (!p) return null;
    return p.archetypes.find((a) => a.id === archetypeId) ?? p.archetypes[0] ?? null;
  }

  coverage(): ProfileCoverageRow[] {
    return this.data.playableHeroes().map((h) => {
      const p = this.get(h.id)!;
      const curatedErrors = CURATED_PROFILES.filter((c) => c.heroId === h.id).flatMap((c) => validateProfile(c, this.data).map((i) => `${i.severity}: ${i.message}`));
      return {
        heroId: h.id,
        name: h.name,
        level: this.level(p),
        archetypes: p.archetypes.length,
        issues: curatedErrors,
        abilityPlanSource: p.archetypes[0]?.abilityPlan.provenance.kind ?? "none",
        popularDataLists: [h.popularItems.early, h.popularItems.mid, h.popularItems.late].filter((x) => x.length).length,
        changedSinceReview: this.staleRefs(p),
        inDevelopmentFlag: h.inDevelopmentFlag,
      };
    });
  }
}
