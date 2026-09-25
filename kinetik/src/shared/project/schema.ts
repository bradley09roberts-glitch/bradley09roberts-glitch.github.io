/**
 * Project file schema, version 1 (SPEC §4).
 *
 * Any change to this schema bumps PROJECT_VERSION and adds a migration in ./migrations.ts, even if
 * the change is only additive. This keeps every saved file readable by every later version.
 *
 * In version 1 the media, layer and marker lists must be empty: those structures arrive with the
 * phases that implement them (1a, 2) together with a migration.
 */
import { z } from 'zod'
import { gcd } from '../time/rational'

export const PROJECT_FORMAT = 'kinetik.project'
export const PROJECT_VERSION = 1
export const PROJECT_FILE_EXTENSION = 'kinetik'

const MAX_DIMENSION = 8192
const MAX_FPS = 240

export const RationalSchema = z
  .strictObject({ num: z.int(), den: z.int().positive() })
  .refine((r) => gcd(r.num, r.den) === 1, { message: 'rational must be in lowest terms' })

export const FrameRateSchema = RationalSchema.refine((r) => r.num > 0 && r.num / r.den <= MAX_FPS, {
  message: `frame rate must be between 0 and ${MAX_FPS} fps`,
})

/** Even dimensions only: 4:2:0 video encoding needs them. */
const DimensionSchema = z
  .int()
  .min(16)
  .max(MAX_DIMENSION)
  .refine((n) => n % 2 === 0, { message: 'must be an even number' })

const UnitInterval = z.number().min(0).max(1)

export const CompositionSchema = z
  .strictObject({
    id: z.string().min(1),
    name: z.string().min(1).max(200),
    width: DimensionSchema,
    height: DimensionSchema,
    frameRate: FrameRateSchema,
    durationFrames: z
      .int()
      .min(1)
      .max(MAX_FPS * 60 * 60),
    background: z.tuple([UnitInterval, UnitInterval, UnitInterval, UnitInterval]),
    workArea: z.strictObject({ inFrame: z.int().min(0), outFrame: z.int().min(1) }),
    markers: z.array(z.never()),
    layers: z.array(z.never()),
  })
  .refine((c) => c.workArea.inFrame < c.workArea.outFrame, {
    message: 'work area in must be before out',
    path: ['workArea'],
  })
  .refine((c) => c.workArea.outFrame <= c.durationFrames, {
    message: 'work area must end within the composition',
    path: ['workArea', 'outFrame'],
  })

export const ProjectSchema = z
  .strictObject({
    format: z.literal(PROJECT_FORMAT),
    version: z.literal(PROJECT_VERSION),
    id: z.uuid(),
    name: z.string().min(1).max(200),
    createdAt: z.iso.datetime(),
    modifiedAt: z.iso.datetime(),
    appVersion: z.string().min(1),
    settings: z.strictObject({
      defaultCompId: z.string().min(1),
      snapping: z.boolean(),
    }),
    media: z.array(z.never()),
    compositions: z.array(CompositionSchema).min(1),
    presets: z.strictObject({ effects: z.array(z.never()), text: z.array(z.never()) }),
  })
  .superRefine((p, ctx) => {
    const ids = new Set<string>()
    p.compositions.forEach((c, i) => {
      if (ids.has(c.id)) {
        ctx.addIssue({
          code: 'custom',
          message: `duplicate composition id "${c.id}"`,
          path: ['compositions', i, 'id'],
        })
      }
      ids.add(c.id)
    })
    if (!ids.has(p.settings.defaultCompId)) {
      ctx.addIssue({
        code: 'custom',
        message: 'default composition does not exist',
        path: ['settings', 'defaultCompId'],
      })
    }
  })

export type Project = z.infer<typeof ProjectSchema>
export type Composition = z.infer<typeof CompositionSchema>
