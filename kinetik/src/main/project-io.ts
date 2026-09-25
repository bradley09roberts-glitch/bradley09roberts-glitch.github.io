/**
 * Project file I/O (SPEC §4, ADR-8).
 *
 * Save is atomic: the new content goes to `<file>.tmp` and is fsynced, the previous file is copied
 * to `<file>.bak`, then the temp file is renamed over the original. At every point there is a
 * complete, valid file on disk, so a crash or power cut mid-save never loses the project.
 */
import { copyFile, open, readFile, rename, rm, stat } from 'node:fs/promises'
import {
  type ParsedProject,
  type Project,
  ProjectError,
  parseProject,
  serializeProject,
} from '@shared/project'

export interface SaveHooks {
  /** Test seam: runs after the temp file is written, before anything is replaced. */
  beforeCommit?: () => Promise<void> | void
}

function describeFsError(err: unknown): string {
  const code = (err as NodeJS.ErrnoException | undefined)?.code
  switch (code) {
    case 'ENOENT':
      return 'The file or folder does not exist.'
    case 'EACCES':
    case 'EPERM':
      return 'Kinetik does not have permission to access this location.'
    case 'ENOSPC':
      return 'The disk is full.'
    case 'EROFS':
      return 'The disk is read-only.'
    case 'EISDIR':
      return 'The path is a folder, not a file.'
    default:
      return err instanceof Error ? err.message : String(err)
  }
}

async function exists(path: string): Promise<boolean> {
  try {
    await stat(path)
    return true
  } catch {
    return false
  }
}

/** Windows can briefly lock files (antivirus, indexer); retry a replace a few times. */
async function renameWithRetry(from: string, to: string): Promise<void> {
  for (let attempt = 0; ; attempt++) {
    try {
      await rename(from, to)
      return
    } catch (err) {
      const code = (err as NodeJS.ErrnoException).code
      if (attempt >= 4 || (code !== 'EPERM' && code !== 'EBUSY' && code !== 'EACCES')) throw err
      await new Promise((resolve) => setTimeout(resolve, 50 * (attempt + 1)))
    }
  }
}

export function tempPathFor(filePath: string): string {
  return `${filePath}.tmp`
}

export function backupPathFor(filePath: string): string {
  return `${filePath}.bak`
}

export async function saveProjectFile(
  filePath: string,
  project: Project,
  hooks: SaveHooks = {},
): Promise<void> {
  // Validation errors surface as ProjectError('invalid') before touching the disk.
  const text = serializeProject(project)
  const tmp = tempPathFor(filePath)
  try {
    const handle = await open(tmp, 'w')
    try {
      await handle.writeFile(text, 'utf8')
      await handle.sync()
    } finally {
      await handle.close()
    }
    await hooks.beforeCommit?.()
    if (await exists(filePath)) {
      await copyFile(filePath, backupPathFor(filePath))
    }
    await renameWithRetry(tmp, filePath)
  } catch (err) {
    await rm(tmp, { force: true }).catch(() => undefined)
    if (err instanceof ProjectError) throw err
    throw new ProjectError('io', `The project could not be saved. ${describeFsError(err)}`, [
      String(err),
    ])
  }
}

export async function loadProjectFile(filePath: string): Promise<ParsedProject> {
  let text: string
  try {
    text = await readFile(filePath, 'utf8')
  } catch (err) {
    throw new ProjectError('io', `The project could not be opened. ${describeFsError(err)}`, [
      String(err),
    ])
  }
  return parseProject(text)
}
