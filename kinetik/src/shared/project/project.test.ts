import { describe, expect, it } from 'vitest'
import {
  type JsonObject,
  type Project,
  ProjectError,
  createEmptyProject,
  migrateToCurrent,
  parseProject,
  serializeProject,
} from './index'

const fixedOpts = {
  name: 'Derby Edit',
  appVersion: '0.1.0',
  id: '6f1d2c1e-8a8b-4c1f-9d2e-3b4a5c6d7e8f',
  compId: 'comp-1',
  now: new Date('2026-09-25T18:00:00.000Z'),
}

function makeProject(): Project {
  return createEmptyProject(fixedOpts)
}

function expectProjectError(fn: () => unknown, code: ProjectError['code']): ProjectError {
  try {
    fn()
  } catch (err) {
    expect(err).toBeInstanceOf(ProjectError)
    expect((err as ProjectError).code).toBe(code)
    return err as ProjectError
  }
  throw new Error(`expected ProjectError(${code}) to be thrown`)
}

describe('createEmptyProject', () => {
  it('uses the SPEC default composition: 1080×1920, 60 fps, 30 s', () => {
    const p = makeProject()
    expect(p.version).toBe(1)
    expect(p.format).toBe('kinetik.project')
    const comp = p.compositions[0]!
    expect(comp.width).toBe(1080)
    expect(comp.height).toBe(1920)
    expect(comp.frameRate).toEqual({ num: 60, den: 1 })
    expect(comp.durationFrames).toBe(1800)
    expect(comp.workArea).toEqual({ inFrame: 0, outFrame: 1800 })
    expect(p.settings.defaultCompId).toBe(comp.id)
  })

  it('falls back to "Untitled" for a blank name', () => {
    expect(createEmptyProject({ ...fixedOpts, name: '   ' }).name).toBe('Untitled')
  })
})

describe('serialize / parse', () => {
  it('round-trips a project exactly', () => {
    const p = makeProject()
    const text = serializeProject(p)
    expect(text.endsWith('\n')).toBe(true)
    const { project, fromVersion } = parseProject(text)
    expect(project).toEqual(p)
    expect(fromVersion).toBe(1)
    // Stable output: serialising the loaded project gives identical text.
    expect(serializeProject(project)).toBe(text)
  })

  it('refuses to serialise an invalid project', () => {
    const p = makeProject()
    const bad = { ...p, compositions: [{ ...p.compositions[0]!, width: 1081 }] }
    expectProjectError(() => serializeProject(bad), 'invalid')
  })

  it('rejects text that is not JSON', () => {
    expectProjectError(() => parseProject('{"format": "kinetik.project",'), 'parse')
  })

  it('rejects JSON that is not a Kinetik project', () => {
    expectProjectError(() => parseProject('{"hello": "world"}'), 'format')
    expectProjectError(() => parseProject('[]'), 'format')
    expectProjectError(() => parseProject('null'), 'format')
    expectProjectError(() => parseProject('{"format":"kinetik.project","version":"1"}'), 'format')
    expectProjectError(() => parseProject('{"format":"kinetik.project","version":0}'), 'format')
  })

  it('refuses a file from a newer app version with a clear message', () => {
    const doc = { ...makeProject(), version: 99 }
    const err = expectProjectError(() => parseProject(JSON.stringify(doc)), 'too-new')
    expect(err.message).toMatch(/newer version of Kinetik/)
  })

  it('rejects invalid content with readable details', () => {
    const cases: Array<(d: JsonObject) => void> = [
      (d) => {
        d.id = 'not-a-uuid'
      },
      (d) => {
        d.name = ''
      },
      (d) => {
        d.extra = true // unknown keys are rejected: they signal corruption or a newer writer
      },
      (d) => {
        ;(d.settings as JsonObject).defaultCompId = 'missing'
      },
      (d) => {
        d.compositions = []
      },
      (d) => {
        const c = (d.compositions as JsonObject[])[0]!
        c.frameRate = { num: 120, den: 2 } // not normalised
      },
      (d) => {
        const c = (d.compositions as JsonObject[])[0]!
        c.frameRate = { num: 0, den: 1 }
      },
      (d) => {
        const c = (d.compositions as JsonObject[])[0]!
        c.durationFrames = 1.5
      },
      (d) => {
        const c = (d.compositions as JsonObject[])[0]!
        c.workArea = { inFrame: 100, outFrame: 50 }
      },
      (d) => {
        const c = (d.compositions as JsonObject[])[0]!
        c.workArea = { inFrame: 0, outFrame: 5000 }
      },
      (d) => {
        const comps = d.compositions as JsonObject[]
        comps.push({ ...comps[0]! })
      },
    ]
    for (const mutate of cases) {
      const doc = JSON.parse(serializeProject(makeProject())) as JsonObject
      mutate(doc)
      const err = expectProjectError(() => parseProject(JSON.stringify(doc)), 'invalid')
      expect(err.details.length).toBeGreaterThan(0)
    }
  })
})

describe('migrations', () => {
  it('runs each step in order up to the target version', () => {
    // Simulates future schema changes: v1 → v2 renames a field, v2 → v3 adds one.
    const table = {
      1: (d: JsonObject) => ({ ...d, version: 2, title: d.name, name: undefined }),
      2: (d: JsonObject) => ({ ...d, version: 3, tags: [] }),
    }
    const { doc, fromVersion } = migrateToCurrent(
      { format: 'kinetik.project', version: 1, name: 'x' },
      table,
      3,
    )
    expect(fromVersion).toBe(1)
    expect(doc).toMatchObject({ version: 3, title: 'x', tags: [] })
  })

  it('fails clearly when a step is missing or misbehaves', () => {
    expectProjectError(
      () => migrateToCurrent({ format: 'kinetik.project', version: 1 }, {}, 2),
      'format',
    )
    expectProjectError(
      () =>
        migrateToCurrent(
          { format: 'kinetik.project', version: 1 },
          { 1: (d: JsonObject) => ({ ...d }) },
          2,
        ),
      'format',
    )
  })

  it('is a no-op for current-version files', () => {
    const doc = JSON.parse(serializeProject(makeProject())) as JsonObject
    expect(migrateToCurrent(doc).doc).toBe(doc)
  })
})
