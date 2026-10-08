import { z } from "zod";
import type { Snapshot } from "../types.js";

/** Bump when the normalised snapshot layout changes incompatibly. */
export const SNAPSHOT_SCHEMA_VERSION = 1;

const confidence = z.enum(["high", "medium", "low"]);
const provenance = z.object({
  kind: z.enum(["data", "derived", "description", "community", "statistical", "heuristic"]),
  source: z.string().min(1),
  interpretation: z.string(),
  checkedBuild: z.number().int().nullable().optional(),
  confidence,
});

const itemProperty = z.object({
  value: z.number().finite(),
  providedType: z.string().optional(),
  label: z.string().optional(),
  postfix: z.string().optional(),
  conditional: z.boolean().optional(),
});

const item = z.object({
  id: z.number().int().nonnegative(),
  className: z.string().min(1),
  name: z.string().min(1),
  slot: z.enum(["weapon", "vitality", "spirit"]),
  tier: z.number().int().min(1).max(5),
  cost: z.number().int().nonnegative(),
  activation: z.enum(["passive", "press", "instant_cast", "instant_cast_toggle", "other"]),
  isActive: z.boolean(),
  components: z.array(z.string()),
  heroRestriction: z.array(z.number().int()),
  imbue: z.string().nullable(),
  image: z.string().url().nullable(),
  shopFilters: z.array(z.string()),
  properties: z.record(z.string(), itemProperty),
  text: z.string(),
  hasCorruptedVariant: z.boolean(),
});

const abilityTier = z.object({
  tier: z.union([z.literal(1), z.literal(2), z.literal(3)]),
  text: z.string(),
  upgrades: z.array(
    z.object({
      property: z.string(),
      bonus: z.union([z.number(), z.string()]),
      label: z.string().optional(),
      postfix: z.string().optional(),
    }),
  ),
});

const ability = z.object({
  id: z.number().int().nonnegative(),
  className: z.string().min(1),
  name: z.string().min(1),
  slot: z.union([z.literal(1), z.literal(2), z.literal(3), z.literal(4)]),
  isUltimate: z.boolean(),
  image: z.string().url().nullable(),
  text: z.string(),
  tiers: z.array(abilityTier).length(3),
  properties: z.record(z.string(), itemProperty),
});

const popular = z.object({ className: z.string(), pickPct: z.number(), winPct: z.number() });

const hero = z.object({
  id: z.number().int().nonnegative(),
  className: z.string().min(1),
  name: z.string().min(1),
  heroType: z.string().nullable(),
  tags: z.array(z.string()),
  complexity: z.number().nullable(),
  gunTag: z.string().nullable(),
  image: z.string().url().nullable(),
  cardImage: z.string().url().nullable(),
  playable: z.boolean(),
  inDevelopmentFlag: z.boolean(),
  abilities: z.array(ability),
  levels: z.array(
    z.object({
      level: z.number().int().positive(),
      souls: z.number().int().nonnegative(),
      grants: z.array(z.enum(["unlock", "point"])),
    }),
  ),
  popularItems: z.object({
    timestamp: z.number().nullable(),
    early: z.array(popular),
    mid: z.array(popular),
    late: z.array(popular),
  }),
  abilityOrders: z.object({
    retrievedFor: z.string().nullable(),
    sampleWindowStart: z.number().nullable(),
    orders: z.array(
      z.object({
        sequence: z.array(z.number().int()),
        matches: z.number().int().nonnegative(),
        wins: z.number().int().nonnegative(),
      }),
    ),
  }),
});

export const snapshotSchema = z
  .object({
    meta: z.object({
      schemaVersion: z.literal(SNAPSHOT_SCHEMA_VERSION),
      apiBase: z.string().url(),
      endpoints: z.array(z.string()),
      retrievedAt: z.string().datetime(),
      clientVersion: z.number().int().nullable(),
      serverVersion: z.number().int().nullable(),
      versionDate: z.string().nullable(),
      latestPatch: z.object({ title: z.string(), date: z.string(), link: z.string() }).nullable(),
      contentHash: z.string().regex(/^[0-9a-f]{64}$/),
      counts: z.object({
        heroes: z.number().int(),
        playableHeroes: z.number().int(),
        items: z.number().int(),
        shopItems: z.number().int(),
      }),
    }),
    heroes: z.array(hero).min(1),
    items: z.array(item).min(1),
    abilityCosts: z.object({
      unlockCost: z.number().int().positive(),
      tierCosts: z.tuple([z.number().int().positive(), z.number().int().positive(), z.number().int().positive()]),
      provenance,
      sampleBuilds: z.number().int().nonnegative(),
      crossCheck: z.object({
        pointsGranted: z.number().int(),
        pointsToMaxAll: z.number().int(),
        consistent: z.boolean(),
      }),
    }),
    itemPricePerTier: z.array(z.number()),
    modesInData: z.array(z.string()),
  })
  .superRefine((s, ctx) => {
    const ids = new Set<number>();
    const classNames = new Set<string>();
    for (const it of s.items) {
      if (ids.has(it.id)) ctx.addIssue({ code: "custom", message: `duplicate item id ${it.id}` });
      if (classNames.has(it.className)) ctx.addIssue({ code: "custom", message: `duplicate item ${it.className}` });
      ids.add(it.id);
      classNames.add(it.className);
    }
    for (const it of s.items) {
      for (const c of it.components) {
        if (!classNames.has(c)) ctx.addIssue({ code: "custom", message: `${it.className} references unknown component ${c}` });
      }
    }
    for (const h of s.heroes.filter((x) => x.playable)) {
      if (h.abilities.length !== 4) {
        ctx.addIssue({ code: "custom", message: `playable hero ${h.name} has ${h.abilities.length} signature abilities, expected 4` });
      }
      if (h.levels.length === 0) ctx.addIssue({ code: "custom", message: `playable hero ${h.name} has no level schedule` });
    }
    if (s.meta.counts.items !== s.items.length) ctx.addIssue({ code: "custom", message: "meta.counts.items mismatch" });
    if (s.meta.counts.heroes !== s.heroes.length) ctx.addIssue({ code: "custom", message: "meta.counts.heroes mismatch" });
  });

export type SnapshotValidation =
  | { ok: true; snapshot: Snapshot }
  | { ok: false; errors: string[] };

/** Validate an untrusted parsed JSON value as a snapshot. Never throws. */
export function validateSnapshot(value: unknown): SnapshotValidation {
  const r = snapshotSchema.safeParse(value);
  if (r.success) return { ok: true, snapshot: r.data as Snapshot };
  return {
    ok: false,
    errors: r.error.issues.slice(0, 50).map((i) => `${i.path.join(".") || "(root)"}: ${i.message}`),
  };
}
