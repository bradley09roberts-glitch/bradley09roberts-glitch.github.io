import { z } from "zod";

export { IPC } from "./channels.js";

/** Compact model rendered by the overlay window (no engine needed there). */
export const overlayModelSchema = z.object({
  infoState: z.enum(["manual", "scenario", "verified-live", "last-observed", "replay", "no-data"]),
  heroName: z.string().max(80).nullable(),
  decision: z.string().max(40),
  primary: z
    .object({
      label: z.enum(["BUY NOW", "SAVE FOR"]),
      name: z.string().max(80),
      image: z.string().max(400).nullable(),
      soulsShort: z.number().int().nonnegative(),
      remainingCost: z.number().int().nonnegative(),
    })
    .nullable(),
  saveFor: z.object({ name: z.string().max(80), soulsShort: z.number().int().nonnegative() }).nullable(),
  alternative: z.object({ name: z.string().max(80), condition: z.string().max(240) }).nullable(),
  reason: z.string().max(240),
  ability: z.string().max(200).nullable(),
  threats: z.array(z.object({ label: z.string().max(60), evidence: z.string().max(40), urgent: z.boolean() })).max(3),
  confidence: z.enum(["high", "medium", "low"]),
  dataNote: z.string().max(200).nullable(),
  updatedAt: z.number(),
});
export type OverlayModel = z.infer<typeof overlayModelSchema>;

export const logEntrySchema = z.object({
  matchId: z.string().min(1).max(80).regex(/^[\w.:-]+$/),
  entry: z.record(z.string(), z.unknown()),
});

export const logIdSchema = z.string().min(1).max(80).regex(/^[\w.:-]+$/);

export interface DataStatusEvent {
  kind: "checking" | "current" | "outdated" | "updated" | "rejected" | "offline" | "error";
  message: string;
  latestBuild: number | null;
  snapshotBuild: number | null;
  at: string;
}
