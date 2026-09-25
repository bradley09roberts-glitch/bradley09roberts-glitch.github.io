import { rational } from '../time/rational'
import { secondsToFramesCeil } from '../time/units'
import { type Composition, type Project, PROJECT_FORMAT, PROJECT_VERSION } from './schema'

/** SPEC §2: default composition is 1080×1920, 60 fps, 30 s. */
export const DEFAULT_COMP = {
  width: 1080,
  height: 1920,
  frameRate: rational(60),
  durationSeconds: 30,
} as const

export interface CreateProjectOptions {
  name: string
  appVersion: string
  /** Injected so tests are deterministic. */
  id: string
  compId: string
  now: Date
}

export function createDefaultComposition(id: string, name = 'Main'): Composition {
  const durationFrames = secondsToFramesCeil(DEFAULT_COMP.durationSeconds, DEFAULT_COMP.frameRate)
  return {
    id,
    name,
    width: DEFAULT_COMP.width,
    height: DEFAULT_COMP.height,
    frameRate: { ...DEFAULT_COMP.frameRate },
    durationFrames,
    background: [0, 0, 0, 1],
    workArea: { inFrame: 0, outFrame: durationFrames },
    markers: [],
    layers: [],
  }
}

export function createEmptyProject(opts: CreateProjectOptions): Project {
  const timestamp = opts.now.toISOString()
  return {
    format: PROJECT_FORMAT,
    version: PROJECT_VERSION,
    id: opts.id,
    name: opts.name.trim() || 'Untitled',
    createdAt: timestamp,
    modifiedAt: timestamp,
    appVersion: opts.appVersion,
    settings: { defaultCompId: opts.compId, snapping: true },
    media: [],
    compositions: [createDefaultComposition(opts.compId)],
    presets: { effects: [], text: [] },
  }
}
