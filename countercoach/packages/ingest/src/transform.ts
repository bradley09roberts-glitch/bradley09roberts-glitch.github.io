import type {
  Ability,
  AbilityCostSchedule,
  AbilityTier,
  Activation,
  Hero,
  Item,
  ItemProperty,
  LevelStep,
  PopularItemStat,
  SlotType,
} from "@countercoach/engine";
import { parseNumber, plainText } from "@countercoach/engine";
import type { RawAbilityOrderStat, RawBuild, RawHero, RawItem } from "./rawSchemas.js";

export interface TransformWarnings {
  warnings: string[];
}

function str(v: unknown): string | null {
  return typeof v === "string" && v.length ? v : null;
}

function toProperties(raw: Record<string, unknown> | null | undefined): Record<string, ItemProperty> {
  const out: Record<string, ItemProperty> = {};
  if (!raw) return out;
  for (const [name, v] of Object.entries(raw)) {
    if (!v || typeof v !== "object") continue;
    const p = v as Record<string, unknown>;
    const n = parseNumber(p.value);
    if (n == null || n === 0) continue;
    const flags = Array.isArray(p.usage_flags) ? (p.usage_flags as string[]) : [];
    out[name] = {
      value: n,
      providedType: str(p.provided_property_type) ?? undefined,
      label: str(p.label) ?? undefined,
      postfix: str(p.postfix) ?? undefined,
      conditional: flags.includes("ConditionallyApplied") || undefined,
    };
    // Drop undefined keys so canonical hashing is stable.
    for (const k of Object.keys(out[name]!) as (keyof ItemProperty)[]) {
      if (out[name]![k] === undefined) delete out[name]![k];
    }
  }
  return out;
}

function descriptionText(desc: unknown, sections: unknown[] | null | undefined): string {
  const parts: string[] = [];
  if (typeof desc === "string") parts.push(plainText(desc));
  else if (desc && typeof desc === "object") {
    const d = desc as Record<string, unknown>;
    for (const k of ["desc", "desc2", "passive", "active"]) {
      const t = plainText(str(d[k]));
      if (t) parts.push(t);
    }
  }
  for (const s of sections ?? []) {
    const attrs = (s as { section_attributes?: { loc_string?: string }[] })?.section_attributes ?? [];
    for (const a of attrs) {
      const t = plainText(a.loc_string ?? null);
      if (t && !parts.includes(t)) parts.push(t);
    }
  }
  return parts.join(" ").trim();
}

function activationOf(v: string | null | undefined): Activation {
  switch (v) {
    case "passive":
    case "press":
    case "instant_cast":
    case "instant_cast_toggle":
      return v;
    default:
      return "other";
  }
}

/** Shoppable upgrade items (tiers 1-5). Mode legality is applied later by the rules module. */
export function transformItems(raw: RawItem[], w: TransformWarnings): Item[] {
  const shop = raw.filter((i) => i.type === "upgrade" && i.shopable === true && i.disabled !== true);
  const known = new Set(shop.map((i) => i.class_name));
  const items: Item[] = [];
  for (const i of shop) {
    const slot = i.item_slot_type as SlotType | null | undefined;
    if (slot !== "weapon" && slot !== "vitality" && slot !== "spirit") {
      w.warnings.push(`item ${i.class_name} has unknown slot ${String(slot)}; skipped`);
      continue;
    }
    if (typeof i.item_tier !== "number" || typeof i.cost !== "number" || !i.name) {
      w.warnings.push(`item ${i.class_name} missing tier/cost/name; skipped`);
      continue;
    }
    const components = (i.component_items ?? []).filter((c) => {
      if (known.has(c)) return true;
      w.warnings.push(`item ${i.class_name}: component ${c} is not a shoppable item; ignored`);
      return false;
    });
    items.push({
      id: i.id,
      className: i.class_name,
      name: i.name,
      slot,
      tier: i.item_tier,
      cost: i.cost,
      activation: activationOf(i.activation),
      isActive: i.is_active_item === true,
      components,
      heroRestriction: i.heroes ?? [],
      imbue: i.imbue ?? null,
      image: str(i.shop_image) ?? str(i.image),
      shopFilters: i.shop_filters ?? [],
      properties: toProperties(i.properties as Record<string, unknown> | null | undefined),
      text: descriptionText(i.description, i.tooltip_sections),
      hasCorruptedVariant: i.corrupted_info != null,
    });
  }
  return items.sort((a, b) => a.className.localeCompare(b.className));
}

function abilityFrom(raw: RawItem, slot: 1 | 2 | 3 | 4, w: TransformWarnings): Ability {
  const props = toProperties(raw.properties as Record<string, unknown> | null | undefined);
  const rawProps = (raw.properties ?? {}) as Record<string, Record<string, unknown>>;
  const desc = (raw.description && typeof raw.description === "object" ? raw.description : {}) as Record<string, unknown>;
  const tiers: AbilityTier[] = [];
  const ups = raw.upgrades ?? [];
  if (ups.length !== 3) w.warnings.push(`ability ${raw.class_name} has ${ups.length} upgrade tiers (expected 3)`);
  for (let t = 1 as 1 | 2 | 3; t <= 3; t = (t + 1) as 1 | 2 | 3) {
    const u = ups[t - 1]?.property_upgrades ?? [];
    tiers.push({
      tier: t,
      text: plainText(str(desc[`t${t}_desc`])),
      upgrades: u.map((pu) => {
        const meta = rawProps[pu.name] ?? {};
        const entry: AbilityTier["upgrades"][number] = { property: pu.name, bonus: typeof pu.bonus === "number" ? pu.bonus : String(pu.bonus) };
        if (str(meta.label)) entry.label = str(meta.label)!;
        if (str(meta.postfix)) entry.postfix = str(meta.postfix)!;
        return entry;
      }),
    });
    if (t === 3) break;
  }
  return {
    id: raw.id,
    className: raw.class_name,
    name: raw.name ?? raw.class_name,
    slot,
    isUltimate: slot === 4,
    image: str(raw.image),
    text: plainText(str(desc.desc)),
    tiers,
    properties: props,
  };
}

export function transformHeroes(
  rawHeroes: RawHero[],
  rawItems: RawItem[],
  orderStats: Map<number, { since: number; orders: RawAbilityOrderStat[] }>,
  w: TransformWarnings,
): Hero[] {
  const abilities = new Map(rawItems.filter((i) => i.type === "ability").map((i) => [i.class_name, i]));
  const itemsById = new Map(rawItems.map((i) => [i.id, i]));
  const heroes: Hero[] = [];
  for (const h of rawHeroes) {
    const playable = h.player_selectable === true && h.disabled !== true;
    const map = h.items ?? {};
    const abs: Ability[] = [];
    for (const s of [1, 2, 3, 4] as const) {
      const cn = map[`signature${s}`];
      if (!cn) continue;
      const a = abilities.get(cn);
      if (!a) {
        if (playable) w.warnings.push(`hero ${h.name}: ability ${cn} not found in items`);
        continue;
      }
      abs.push(abilityFrom(a, s, w));
    }
    const levels: LevelStep[] = Object.entries(h.level_info ?? {})
      .map(([lvl, v]) => {
        const grants: LevelStep["grants"] = [];
        for (const c of v.bonus_currencies ?? []) {
          if (c === "EAbilityUnlocks") grants.push("unlock");
          else if (c === "EAbilityPoints") grants.push("point");
          else w.warnings.push(`hero ${h.name} level ${lvl}: unknown currency ${c}`);
        }
        return { level: Number(lvl), souls: Math.round(v.required_gold), grants };
      })
      .filter((l) => Number.isFinite(l.level))
      .sort((a, b) => a.level - b.level);

    const pop = (arr: { item_id: number; pick_pct: number; winrate_pct: number }[] | undefined): PopularItemStat[] =>
      (arr ?? [])
        .map((p) => {
          const it = itemsById.get(p.item_id);
          return it ? { className: it.class_name, pickPct: round(p.pick_pct), winPct: round(p.winrate_pct) } : null;
        })
        .filter((x): x is PopularItemStat => x !== null)
        .sort((a, b) => b.pickPct - a.pickPct);

    const os = orderStats.get(h.id);
    const images = (h.images ?? {}) as Record<string, unknown>;
    heroes.push({
      id: h.id,
      className: h.class_name,
      name: h.name,
      heroType: h.hero_type ?? null,
      tags: h.tags ?? [],
      complexity: h.complexity ?? null,
      gunTag: h.gun_tag ?? null,
      image: str(images.icon_image_small),
      cardImage: str(images.icon_hero_card),
      playable,
      inDevelopmentFlag: h.in_development === true,
      abilities: abs,
      levels,
      popularItems: {
        timestamp: h.popular_items?.timestamp ?? null,
        early: pop(h.popular_items?.early_game),
        mid: pop(h.popular_items?.mid_game),
        late: pop(h.popular_items?.late_game),
      },
      abilityOrders: {
        retrievedFor: os ? "/v1/analytics/ability-order-stats" : null,
        sampleWindowStart: os?.since ?? null,
        orders: (os?.orders ?? [])
          .slice()
          .sort((a, b) => b.matches - a.matches)
          .slice(0, 5)
          .map((o) => ({ sequence: o.abilities, matches: o.matches, wins: o.wins })),
      },
    });
  }
  return heroes.sort((a, b) => a.id - b.id);
}

function round(n: number): number {
  return Math.round(n * 100) / 100;
}

/**
 * Derive ability point costs from in-game build ability orders. Builds record currency
 * changes per ability; the currency whose deltas are only ever -1 and that is spent first on
 * each ability is the unlock currency, the other is ability points. The k-th negative point
 * delta on an ability is the cost of tier k. Cross-checked against the level schedule.
 */
export function deriveAbilityCosts(builds: RawBuild[], heroes: Hero[], w: TransformWarnings): AbilityCostSchedule {
  const byType = new Map<number, Set<number>>();
  const perTier: Map<number, number>[] = [new Map(), new Map(), new Map()];
  let sample = 0;
  // First pass: identify the unlock currency (first event per ability).
  const firstTypeCounts = new Map<number, number>();
  for (const b of builds) {
    const cc = b.hero_build.details.ability_order?.currency_changes ?? [];
    const seen = new Set<number>();
    for (const c of cc) {
      if (!byType.has(c.currency_type)) byType.set(c.currency_type, new Set());
      byType.get(c.currency_type)!.add(c.delta);
      if (!seen.has(c.ability_id)) {
        seen.add(c.ability_id);
        firstTypeCounts.set(c.currency_type, (firstTypeCounts.get(c.currency_type) ?? 0) + 1);
      }
    }
  }
  const unlockType = [...firstTypeCounts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0];
  const pointType = [...byType.keys()].find((t) => t !== unlockType);
  if (unlockType == null || pointType == null) throw new Error("could not identify ability currencies from builds");
  for (const b of builds) {
    const cc = b.hero_build.details.ability_order?.currency_changes ?? [];
    if (!cc.length) continue;
    sample++;
    const k = new Map<number, number>();
    for (const c of cc) {
      if (c.currency_type !== pointType || c.delta >= 0) continue;
      const n = (k.get(c.ability_id) ?? 0) + 1;
      k.set(c.ability_id, n);
      if (n <= 3) perTier[n - 1]!.set(-c.delta, (perTier[n - 1]!.get(-c.delta) ?? 0) + 1);
    }
  }
  const mode = (m: Map<number, number>) => [...m.entries()].sort((a, b) => b[1] - a[1])[0]?.[0];
  const costs = perTier.map(mode);
  if (costs.some((c) => c == null)) throw new Error("insufficient build data to derive tier costs");
  const tierCosts = costs as [number, number, number];
  const agreement = perTier.map((m, i) => (m.get(tierCosts[i]!) ?? 0) / Math.max(1, [...m.values()].reduce((a, b) => a + b, 0)));
  const ref = heroes.find((h) => h.playable && h.levels.length);
  const pointsGranted = ref ? ref.levels.reduce((n, l) => n + l.grants.filter((g) => g === "point").length, 0) : 0;
  const unlocksGranted = ref ? ref.levels.reduce((n, l) => n + l.grants.filter((g) => g === "unlock").length, 0) : 0;
  const pointsToMaxAll = 4 * (tierCosts[0] + tierCosts[1] + tierCosts[2]);
  const consistent = pointsGranted === pointsToMaxAll && unlocksGranted === 4;
  if (!consistent) {
    w.warnings.push(`ability cost cross-check failed: granted ${pointsGranted} points/${unlocksGranted} unlocks, max-all needs ${pointsToMaxAll}`);
  }
  const minAgree = Math.min(...agreement);
  return {
    unlockCost: 1,
    tierCosts,
    provenance: {
      kind: "derived",
      source: "GET /v1/builds (hero_build.details.ability_order.currency_changes) + /v1/assets/heroes level_info",
      interpretation: `Unlock currency type ${unlockType}, point currency type ${pointType}. Modal point delta per tier across ${sample} builds (agreement ${(minAgree * 100).toFixed(0)}%+). Cross-check: level schedule grants ${pointsGranted} points and ${unlocksGranted} unlocks.`,
      confidence: consistent && minAgree > 0.9 ? "high" : "medium",
    },
    sampleBuilds: sample,
    crossCheck: { pointsGranted, pointsToMaxAll, consistent },
  };
}
