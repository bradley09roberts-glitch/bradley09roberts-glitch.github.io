import { mkdtemp, readFile, readdir, rm, writeFile, mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { type Project, ProjectError, createEmptyProject } from '@shared/project'
import { backupPathFor, loadProjectFile, saveProjectFile, tempPathFor } from './project-io'

let dir: string

beforeEach(async () => {
  dir = await mkdtemp(join(tmpdir(), 'kinetik-io-'))
})

afterEach(async () => {
  await rm(dir, { recursive: true, force: true })
})

function makeProject(name = 'Derby Edit'): Project {
  return createEmptyProject({
    name,
    appVersion: '0.1.0',
    id: '6f1d2c1e-8a8b-4c1f-9d2e-3b4a5c6d7e8f',
    compId: 'comp-1',
    now: new Date('2026-09-25T18:00:00.000Z'),
  })
}

async function expectRejectsWith(p: Promise<unknown>, code: ProjectError['code']): Promise<void> {
  await expect(p).rejects.toBeInstanceOf(ProjectError)
  await expect(p).rejects.toMatchObject({ code })
}

describe('project file I/O', () => {
  it('saves and reopens a project unchanged', async () => {
    const file = join(dir, 'test.kinetik')
    const project = makeProject()
    await saveProjectFile(file, project)
    const { project: loaded } = await loadProjectFile(file)
    expect(loaded).toEqual(project)
    // No temp or backup file is left behind on a first save.
    expect(await readdir(dir)).toEqual(['test.kinetik'])
  })

  it('keeps the previous save as .bak', async () => {
    const file = join(dir, 'test.kinetik')
    await saveProjectFile(file, makeProject('First'))
    await saveProjectFile(file, makeProject('Second'))
    expect((await loadProjectFile(file)).project.name).toBe('Second')
    const backup = JSON.parse(await readFile(backupPathFor(file), 'utf8')) as Project
    expect(backup.name).toBe('First')
  })

  it('leaves the original intact if a save is interrupted before commit', async () => {
    const file = join(dir, 'test.kinetik')
    await saveProjectFile(file, makeProject('Original'))
    const before = await readFile(file, 'utf8')

    const crash = saveProjectFile(file, makeProject('Doomed'), {
      beforeCommit: () => {
        throw new Error('simulated crash')
      },
    })
    await expectRejectsWith(crash, 'io')

    expect(await readFile(file, 'utf8')).toBe(before)
    // The temp file is cleaned up.
    expect(await readdir(dir)).not.toContain('test.kinetik.tmp')
  })

  it('a stale temp file from an earlier crash does not affect loading or saving', async () => {
    const file = join(dir, 'test.kinetik')
    await saveProjectFile(file, makeProject('Good'))
    await writeFile(tempPathFor(file), '{"half-written":', 'utf8')
    expect((await loadProjectFile(file)).project.name).toBe('Good')
    await saveProjectFile(file, makeProject('Newer'))
    expect((await loadProjectFile(file)).project.name).toBe('Newer')
  })

  it('refuses to write invalid projects and does not touch the disk', async () => {
    const file = join(dir, 'test.kinetik')
    const bad = { ...makeProject(), name: '' }
    await expectRejectsWith(saveProjectFile(file, bad), 'invalid')
    expect(await readdir(dir)).toEqual([])
  })

  it('reports friendly I/O errors', async () => {
    await expectRejectsWith(loadProjectFile(join(dir, 'missing.kinetik')), 'io')
    await mkdir(join(dir, 'folder.kinetik'))
    await expectRejectsWith(loadProjectFile(join(dir, 'folder.kinetik')), 'io')
    await expectRejectsWith(
      saveProjectFile(join(dir, 'no', 'such', 'dir', 'x.kinetik'), makeProject()),
      'io',
    )
  })

  it('refuses a newer-version file and never modifies it', async () => {
    const file = join(dir, 'future.kinetik')
    const text = JSON.stringify({ ...makeProject(), version: 42 })
    await writeFile(file, text, 'utf8')
    await expectRejectsWith(loadProjectFile(file), 'too-new')
    expect(await readFile(file, 'utf8')).toBe(text)
  })

  it('rejects damaged and foreign files', async () => {
    const damaged = join(dir, 'damaged.kinetik')
    await writeFile(damaged, '{"format":"kinetik.project","vers', 'utf8')
    await expectRejectsWith(loadProjectFile(damaged), 'parse')
    const foreign = join(dir, 'foreign.kinetik')
    await writeFile(foreign, '{"some":"other app"}', 'utf8')
    await expectRejectsWith(loadProjectFile(foreign), 'format')
  })
})
