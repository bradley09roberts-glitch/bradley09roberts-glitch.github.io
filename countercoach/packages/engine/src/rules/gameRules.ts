import type { Item, Provenance } from "../types.js";

/**
 * Game rules that are NOT present as structured API data. Every rule carries its evidence so
 * the UI can label advice that depends on an unverified rule. Users can override the
 * configurable values in settings when they observe the in-game behaviour directly.
 */

export type GameMode = "standard" | "ranked" | "street_brawl";

export interface ModeSupport {
  mode: GameMode;
  supported: boolean;
  label: string;
  note: string;
}

export const MODE_SUPPORT: Record<GameMode, ModeSupport> = {
  standard: {
    mode: "standard",
    supported: true,
    label: "Standard",
    note: "Primary supported mode.",
  },
  ranked: {
    mode: "ranked",
    supported: true,
    label: "Ranked",
    note: "Assumed to use Standard in-match shop and ability rules (unverified). Ranked changes hero selection, not documented shop rules.",
  },
  street_brawl: {
    mode: "street_brawl",
    supported: false,
    label: "Street Brawl",
    note: "Unsupported: round-based item draft, per-round souls and corrupted items are not modelled. No recommendations are produced.",
  },
};

export interface RuleValue<T> {
  value: T;
  provenance: Provenance;
  /** True when the user changed the value from the default. */
  userOverridden?: boolean;
}

export interface GameRules {
  mode: GameMode;
  /** Total item slots and whether they accept any category. */
  inventory: RuleValue<{ totalSlots: number; universal: boolean }>;
  /** Owning a component reduces the upgrade price by the component's cost and consumes it. */
  componentUpgrade: RuleValue<{ deductComponentCost: boolean; consumesComponent: boolean }>;
  /** Fraction of the purchase cost returned when selling (outside the refund window). */
  sellBack: RuleValue<{ fraction: number }>;
  /** Highest tier purchasable in the normal shop. */
  maxShopTier: RuleValue<number>;
  /** Corrupted (Broker) variants are excluded. */
  corruptedItems: RuleValue<"excluded">;
}

export function defaultRules(mode: GameMode = "standard"): GameRules {
  return {
    mode,
    inventory: {
      value: { totalSlots: 12, universal: true },
      provenance: {
        kind: "community",
        source: "https://liquipedia.net/deadlock/Patch/Playtest/2025-05-08 (summary: 'Item slots are now all universal… Total slot count reduced from 16 to 12')",
        interpretation: "12 universal item slots. Not re-verified on build 6763; adjust in Settings if your inventory differs.",
        checkedBuild: null,
        confidence: "medium",
      },
    },
    componentUpgrade: {
      value: { deductComponentCost: true, consumesComponent: true },
      provenance: {
        kind: "community",
        source: "Long-standing Deadlock shop behaviour (community documentation); item component links from /v1/assets/items component_items",
        interpretation: "Upgrade price = item cost − cost of owned listed components; the component is replaced by the upgrade (slot reused). Component links are data; the price rule is not verified on build 6763.",
        checkedBuild: null,
        confidence: "medium",
      },
    },
    sellBack: {
      value: { fraction: 0.5 },
      provenance: {
        kind: "community",
        source: "deadlock.wiki 'The Curiosity Shop' revision (2024), pre-dates the 2025 shop rework",
        interpretation: "Selling returns half the souls spent (full refund before leaving the shop area). Unverified on current build; used only for replacement advice and always labelled.",
        checkedBuild: null,
        confidence: "low",
      },
    },
    maxShopTier: {
      value: 4,
      provenance: {
        kind: "data",
        source: "/v1/assets/items: tier-5 items carry a 9999 placeholder cost; reported as Sandbox-only ('Legendary')",
        interpretation: "Tier 5 items are excluded from shop advice until an acquisition rule is verified.",
        checkedBuild: 6763,
        confidence: "medium",
      },
    },
    corruptedItems: {
      value: "excluded",
      provenance: {
        kind: "community",
        source: "Steam 'City Never Sleeps' coverage; deadlock-live-events README (Broker events)",
        interpretation: "Broker corrupted variants have per-match random penalties and limited availability; not modelled.",
        checkedBuild: null,
        confidence: "medium",
      },
    },
  };
}

export interface RuleOverrides {
  totalSlots?: number;
  sellFraction?: number;
}

export function applyOverrides(rules: GameRules, o: RuleOverrides): GameRules {
  const r = JSON.parse(JSON.stringify(rules)) as GameRules;
  if (o.totalSlots != null && o.totalSlots !== r.inventory.value.totalSlots) {
    r.inventory = { ...r.inventory, value: { ...r.inventory.value, totalSlots: o.totalSlots }, userOverridden: true };
  }
  if (o.sellFraction != null && o.sellFraction !== r.sellBack.value.fraction) {
    r.sellBack = { ...r.sellBack, value: { fraction: o.sellFraction }, userOverridden: true };
  }
  return r;
}

/** Whether an item may be offered by the normal shop for this hero in this mode. */
export function isShopLegal(item: Item, heroId: number | null, rules: GameRules): { legal: boolean; reason?: string } {
  if (!MODE_SUPPORT[rules.mode].supported) return { legal: false, reason: `${MODE_SUPPORT[rules.mode].label} is not supported` };
  if (item.tier > rules.maxShopTier.value) return { legal: false, reason: `tier ${item.tier} not sold in the normal shop` };
  if (item.heroRestriction.length && (heroId == null || !item.heroRestriction.includes(heroId))) {
    return { legal: false, reason: "restricted to other heroes" };
  }
  return { legal: true };
}
