import type { Ability, Hero, Item, Snapshot } from "../types.js";
import { canonicalJson, sha256Hex } from "./hash.js";

/**
 * Indexed, read-only view over a validated snapshot.
 */
export class GameData {
  readonly snapshot: Snapshot;
  private readonly itemsByClass = new Map<string, Item>();
  private readonly itemsById = new Map<number, Item>();
  private readonly heroesById = new Map<number, Hero>();
  private readonly abilitiesByClass = new Map<string, { hero: Hero; ability: Ability }>();
  private readonly abilitiesById = new Map<number, { hero: Hero; ability: Ability }>();
  /** className -> items that list it as a component. */
  private readonly upgradesOf = new Map<string, Item[]>();

  constructor(snapshot: Snapshot) {
    this.snapshot = snapshot;
    for (const it of snapshot.items) {
      this.itemsByClass.set(it.className, it);
      this.itemsById.set(it.id, it);
    }
    for (const it of snapshot.items) {
      for (const c of it.components) {
        const arr = this.upgradesOf.get(c) ?? [];
        arr.push(it);
        this.upgradesOf.set(c, arr);
      }
    }
    for (const h of snapshot.heroes) {
      this.heroesById.set(h.id, h);
      for (const a of h.abilities) {
        this.abilitiesByClass.set(a.className, { hero: h, ability: a });
        this.abilitiesById.set(a.id, { hero: h, ability: a });
      }
    }
  }

  get build(): number | null {
    return this.snapshot.meta.clientVersion;
  }

  item(className: string): Item | undefined {
    return this.itemsByClass.get(className);
  }

  itemById(id: number): Item | undefined {
    return this.itemsById.get(id);
  }

  hero(id: number): Hero | undefined {
    return this.heroesById.get(id);
  }

  heroByName(name: string): Hero | undefined {
    const n = name.trim().toLowerCase();
    return this.snapshot.heroes.find((h) => h.name.toLowerCase() === n || h.className.toLowerCase() === n);
  }

  ability(className: string): { hero: Hero; ability: Ability } | undefined {
    return this.abilitiesByClass.get(className);
  }

  abilityById(id: number): { hero: Hero; ability: Ability } | undefined {
    return this.abilitiesById.get(id);
  }

  playableHeroes(): Hero[] {
    return this.snapshot.heroes.filter((h) => h.playable).sort((a, b) => a.name.localeCompare(b.name));
  }

  /** Items that upgrade from the given component. */
  upgradesFrom(className: string): Item[] {
    return this.upgradesOf.get(className) ?? [];
  }

  /** All items. Mode legality is applied separately by the rules module. */
  items(): Item[] {
    return this.snapshot.items;
  }
}

/** Gameplay fields of an item that a reviewed rule may depend on. */
export function itemFingerprint(it: Item): string {
  return sha256Hex(
    canonicalJson({
      cost: it.cost,
      tier: it.tier,
      slot: it.slot,
      activation: it.activation,
      components: it.components,
      properties: it.properties,
      text: it.text,
    }),
  ).slice(0, 16);
}

export function abilityFingerprint(a: Ability): string {
  return sha256Hex(canonicalJson({ tiers: a.tiers, properties: a.properties, text: a.text, slot: a.slot })).slice(0, 16);
}

export type EntityRef = { kind: "item"; className: string } | { kind: "ability"; className: string };

export function entityKey(ref: EntityRef): string {
  return `${ref.kind}:${ref.className}`;
}

export function currentFingerprint(data: GameData, ref: EntityRef): string | null {
  if (ref.kind === "item") {
    const it = data.item(ref.className);
    return it ? itemFingerprint(it) : null;
  }
  const a = data.ability(ref.className);
  return a ? abilityFingerprint(a.ability) : null;
}

// ---------------------------------------------------------------------------------------------
// Snapshot diffing (patch change detection)
// ---------------------------------------------------------------------------------------------

export interface FieldChange {
  field: string;
  before: unknown;
  after: unknown;
}

export interface EntityChange {
  ref: EntityRef;
  name: string;
  change: "added" | "removed" | "modified";
  fields: FieldChange[];
}

export interface SnapshotDiff {
  fromBuild: number | null;
  toBuild: number | null;
  fromHash: string;
  toHash: string;
  items: EntityChange[];
  abilities: EntityChange[];
  heroes: { id: number; name: string; change: "added" | "removed" | "playability"; detail: string }[];
}

function diffRecords(a: Record<string, unknown>, b: Record<string, unknown>, prefix: string): FieldChange[] {
  const out: FieldChange[] = [];
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of [...keys].sort()) {
    const va = canonicalJson(a[k] ?? null);
    const vb = canonicalJson(b[k] ?? null);
    if (va !== vb) out.push({ field: `${prefix}${k}`, before: a[k] ?? null, after: b[k] ?? null });
  }
  return out;
}

export function diffSnapshots(prev: Snapshot, next: Snapshot): SnapshotDiff {
  const items: EntityChange[] = [];
  const prevItems = new Map(prev.items.map((i) => [i.className, i]));
  const nextItems = new Map(next.items.map((i) => [i.className, i]));
  for (const [cn, a] of prevItems) {
    const b = nextItems.get(cn);
    if (!b) {
      items.push({ ref: { kind: "item", className: cn }, name: a.name, change: "removed", fields: [] });
      continue;
    }
    const fields: FieldChange[] = [];
    for (const f of ["cost", "tier", "slot", "activation", "text"] as const) {
      if (a[f] !== b[f]) fields.push({ field: f, before: a[f], after: b[f] });
    }
    if (canonicalJson(a.components) !== canonicalJson(b.components)) {
      fields.push({ field: "components", before: a.components, after: b.components });
    }
    const pa = Object.fromEntries(Object.entries(a.properties).map(([k, v]) => [k, v.value]));
    const pb = Object.fromEntries(Object.entries(b.properties).map(([k, v]) => [k, v.value]));
    fields.push(...diffRecords(pa, pb, "properties."));
    if (fields.length) items.push({ ref: { kind: "item", className: cn }, name: b.name, change: "modified", fields });
  }
  for (const [cn, b] of nextItems) {
    if (!prevItems.has(cn)) items.push({ ref: { kind: "item", className: cn }, name: b.name, change: "added", fields: [] });
  }

  const abilities: EntityChange[] = [];
  const pa = new Map(prev.heroes.flatMap((h) => h.abilities.map((x) => [x.className, x] as const)));
  const na = new Map(next.heroes.flatMap((h) => h.abilities.map((x) => [x.className, x] as const)));
  for (const [cn, a] of pa) {
    const b = na.get(cn);
    if (!b) {
      abilities.push({ ref: { kind: "ability", className: cn }, name: a.name, change: "removed", fields: [] });
      continue;
    }
    const fields: FieldChange[] = [];
    a.tiers.forEach((t, i) => {
      const tb = b.tiers[i];
      if (!tb || canonicalJson(t.upgrades) !== canonicalJson(tb.upgrades)) {
        fields.push({ field: `tier${t.tier}`, before: t.upgrades, after: tb?.upgrades ?? null });
      }
    });
    const ppa = Object.fromEntries(Object.entries(a.properties).map(([k, v]) => [k, v.value]));
    const ppb = Object.fromEntries(Object.entries(b.properties).map(([k, v]) => [k, v.value]));
    fields.push(...diffRecords(ppa, ppb, "properties."));
    if (fields.length) abilities.push({ ref: { kind: "ability", className: cn }, name: b.name, change: "modified", fields });
  }
  for (const [cn, b] of na) {
    if (!pa.has(cn)) abilities.push({ ref: { kind: "ability", className: cn }, name: b.name, change: "added", fields: [] });
  }

  const heroes: SnapshotDiff["heroes"] = [];
  const ph = new Map(prev.heroes.map((h) => [h.id, h]));
  const nh = new Map(next.heroes.map((h) => [h.id, h]));
  for (const [id, h] of ph) {
    const b = nh.get(id);
    if (!b) heroes.push({ id, name: h.name, change: "removed", detail: "hero no longer in data" });
    else if (b.playable !== h.playable) {
      heroes.push({ id, name: b.name, change: "playability", detail: `playable ${h.playable} -> ${b.playable}` });
    }
  }
  for (const [id, h] of nh) if (!ph.has(id)) heroes.push({ id, name: h.name, change: "added", detail: `playable=${h.playable}` });

  return {
    fromBuild: prev.meta.clientVersion,
    toBuild: next.meta.clientVersion,
    fromHash: prev.meta.contentHash,
    toHash: next.meta.contentHash,
    items,
    abilities,
    heroes,
  };
}

// ---------------------------------------------------------------------------------------------
// Compatibility status
// ---------------------------------------------------------------------------------------------

export type CompatibilityStatus =
  /** Snapshot build equals the latest client build seen by a live version check. */
  | "current"
  /** A newer client build has been seen; data may not match the live game. */
  | "outdated"
  /** No live version check available (offline). */
  | "unknown";

export interface Compatibility {
  status: CompatibilityStatus;
  snapshotBuild: number | null;
  latestKnownBuild: number | null;
  checkedAt: string | null;
  message: string;
}

export function evaluateCompatibility(
  snapshot: Snapshot,
  latest: { clientVersion: number | null; checkedAt: string } | null,
): Compatibility {
  const snapshotBuild = snapshot.meta.clientVersion;
  if (!latest || latest.clientVersion == null) {
    return {
      status: "unknown",
      snapshotBuild,
      latestKnownBuild: null,
      checkedAt: null,
      message: `Offline: using snapshot for build ${snapshotBuild ?? "?"} retrieved ${snapshot.meta.retrievedAt}. Live build not checked.`,
    };
  }
  if (snapshotBuild != null && latest.clientVersion <= snapshotBuild) {
    return {
      status: "current",
      snapshotBuild,
      latestKnownBuild: latest.clientVersion,
      checkedAt: latest.checkedAt,
      message: `Data matches client build ${snapshotBuild}.`,
    };
  }
  return {
    status: "outdated",
    snapshotBuild,
    latestKnownBuild: latest.clientVersion,
    checkedAt: latest.checkedAt,
    message: `Client build ${latest.clientVersion} is newer than data build ${snapshotBuild ?? "?"}. Costs and effects may have changed; refresh data.`,
  };
}
