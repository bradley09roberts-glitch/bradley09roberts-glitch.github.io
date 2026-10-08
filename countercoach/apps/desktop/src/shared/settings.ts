import { z } from "zod";

/**
 * Persisted user settings. Validated in the main process on every read and write; unknown or
 * invalid values fall back to defaults field by field.
 */

export const ANCHORS = ["top-left", "top-right", "bottom-left", "bottom-right", "custom"] as const;
export type Anchor = (typeof ANCHORS)[number];

/** Accelerator strings are validated loosely: modifiers + one key, letters/digits/F-keys only. */
export const acceleratorSchema = z
  .string()
  .max(40)
  .regex(/^((Ctrl|Control|Alt|Shift|Super|CommandOrControl)\+){1,3}([A-Z0-9]|F([1-9]|1[0-9]|2[0-4]))$/);

export const settingsSchema = z.object({
  version: z.literal(1),
  overlay: z.object({
    enabled: z.boolean(),
    scale: z.number().min(0.6).max(2),
    opacity: z.number().min(0.3).max(1),
    anchor: z.enum(ANCHORS),
    displayId: z.number().int().nullable(),
    /** Position of the overlay's top-left inside the display work area, as fractions 0..1. */
    offset: z.object({ x: z.number().min(0).max(1), y: z.number().min(0).max(1) }),
    clickThrough: z.boolean(),
    expanded: z.boolean(),
  }),
  hotkeys: z.object({
    toggleOverlay: acceleratorSchema,
    toggleExpanded: acceleratorSchema,
    toggleEditMode: acceleratorSchema,
  }),
  logging: z.object({ enabled: z.boolean() }),
  data: z.object({ autoCheck: z.boolean() }),
  rules: z.object({
    totalSlots: z.number().int().min(4).max(24).nullable(),
    sellFraction: z.number().min(0).max(1).nullable(),
  }),
  coach: z.object({
    difficulty: z.enum(["simple", "standard", "complex"]),
    route: z.enum(["auto", "stabilise", "normal", "ambitious"]),
    archetypeByHero: z.record(z.string().regex(/^\d+$/), z.string().max(64)),
    lockedCoreByHero: z.record(z.string().regex(/^\d+$/), z.array(z.string().max(80)).max(12)),
  }),
});

export type Settings = z.infer<typeof settingsSchema>;

export const DEFAULT_SETTINGS: Settings = {
  version: 1,
  overlay: {
    enabled: true,
    scale: 1,
    opacity: 0.92,
    anchor: "top-right",
    displayId: null,
    offset: { x: 0.78, y: 0.12 },
    clickThrough: true,
    expanded: false,
  },
  hotkeys: {
    toggleOverlay: "Ctrl+Alt+O",
    toggleExpanded: "Ctrl+Alt+E",
    toggleEditMode: "Ctrl+Alt+M",
  },
  logging: { enabled: false },
  data: { autoCheck: true },
  rules: { totalSlots: null, sellFraction: null },
  coach: { difficulty: "standard", route: "auto", archetypeByHero: {}, lockedCoreByHero: {} },
};

/** Merge an untrusted object onto defaults, keeping only valid sections. */
export function sanitizeSettings(input: unknown): Settings {
  const full = settingsSchema.safeParse(input);
  if (full.success) return full.data;
  const out: Settings = JSON.parse(JSON.stringify(DEFAULT_SETTINGS)) as Settings;
  if (!input || typeof input !== "object") return out;
  const obj = input as Record<string, unknown>;
  const shape = settingsSchema.shape;
  for (const key of Object.keys(shape) as (keyof Settings)[]) {
    if (key === "version") continue;
    const section = (shape[key] as z.ZodTypeAny).safeParse(obj[key]);
    if (section.success) (out as Record<string, unknown>)[key] = section.data;
    else if (obj[key] && typeof obj[key] === "object" && (shape[key] as z.ZodTypeAny) instanceof z.ZodObject) {
      // Field-level salvage inside a section.
      const sub = (shape[key] as z.ZodObject<z.ZodRawShape>).shape;
      const src = obj[key] as Record<string, unknown>;
      const dst = (out as unknown as Record<string, Record<string, unknown>>)[key]!;
      for (const f of Object.keys(sub)) {
        const r = (sub[f] as z.ZodTypeAny).safeParse(src[f]);
        if (r.success) dst[f] = r.data;
      }
    }
  }
  return out;
}

/** Apply a partial patch (from the renderer) and validate the result. */
export function patchSettings(current: Settings, patch: unknown): Settings {
  if (!patch || typeof patch !== "object") return current;
  const merged: Record<string, unknown> = JSON.parse(JSON.stringify(current));
  for (const [k, v] of Object.entries(patch as Record<string, unknown>)) {
    if (!(k in merged) || k === "version") continue;
    if (v && typeof v === "object" && !Array.isArray(v) && merged[k] && typeof merged[k] === "object") {
      merged[k] = { ...(merged[k] as object), ...(v as object) };
    } else merged[k] = v;
  }
  return sanitizeSettings(merged);
}
