import type { GameData } from "../data/gameData.js";
import type { Item } from "../types.js";
import type { GameRules } from "./gameRules.js";

export interface PurchaseEconomics {
  item: Item;
  /** Souls needed now, after owned-component deductions. */
  remainingCost: number;
  /** Owned components that would be consumed (replaced) by this purchase. */
  consumes: string[];
  /** Change in occupied slots after the purchase (0 when a component is replaced). */
  slotDelta: 0 | 1;
  /** True when an owned item already upgrades from this one (buying it is redundant). */
  coveredByOwnedUpgrade: string | null;
}

/**
 * Remaining cost and slot effect of buying `item` given the owned inventory. Uses the
 * component rule from GameRules; when the rule is disabled the full cost applies.
 */
export function purchaseEconomics(item: Item, owned: readonly string[], data: GameData, rules: GameRules): PurchaseEconomics {
  const ownedSet = new Set(owned);
  const consumes = item.components.filter((c) => ownedSet.has(c));
  let remaining = item.cost;
  if (rules.componentUpgrade.value.deductComponentCost) {
    for (const c of consumes) remaining -= data.item(c)?.cost ?? 0;
  }
  remaining = Math.max(0, remaining);
  const consumesSlot = rules.componentUpgrade.value.consumesComponent && consumes.length > 0;
  const coveredBy = owned.find((o) => data.item(o)?.components.includes(item.className)) ?? null;
  return {
    item,
    remainingCost: remaining,
    consumes: rules.componentUpgrade.value.consumesComponent ? consumes : [],
    slotDelta: consumesSlot ? 0 : 1,
    coveredByOwnedUpgrade: coveredBy,
  };
}

export function slotsUsed(owned: readonly string[]): number {
  return owned.length;
}

export function freeSlots(owned: readonly string[], rules: GameRules): number {
  return Math.max(0, rules.inventory.value.totalSlots - slotsUsed(owned));
}

/** Souls returned for selling an owned item under the configured (unverified) rule. */
export function sellValue(item: Item, rules: GameRules): number {
  return Math.floor(item.cost * rules.sellBack.value.fraction);
}

/** Total souls invested in owned items (component costs included in the upgrade's cost). */
export function inventoryValue(owned: readonly string[], data: GameData): number {
  return owned.reduce((n, cn) => n + (data.item(cn)?.cost ?? 0), 0);
}
