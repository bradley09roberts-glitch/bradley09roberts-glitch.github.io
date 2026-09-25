import type { z } from 'zod'
import { ProjectError } from './errors'
import { type Migration, migrateToCurrent, migrations } from './migrations'
import { type Project, ProjectSchema } from './schema'

function describeIssues(error: z.ZodError): string[] {
  return error.issues.slice(0, 10).map((issue) => {
    const where = issue.path.length > 0 ? issue.path.join('.') : '(root)'
    return `${where}: ${issue.message}`
  })
}

/** Validates, then pretty-prints with a trailing newline (readable diffs). */
export function serializeProject(project: Project): string {
  const result = ProjectSchema.safeParse(project)
  if (!result.success) {
    throw new ProjectError(
      'invalid',
      'The project could not be saved because it contains invalid data.',
      describeIssues(result.error),
    )
  }
  return `${JSON.stringify(result.data, null, 2)}\n`
}

export interface ParsedProject {
  project: Project
  /** The version the file was saved with (lower than current if it was migrated). */
  fromVersion: number
}

export function parseProject(
  text: string,
  table: Readonly<Record<number, Migration>> = migrations,
): ParsedProject {
  let raw: unknown
  try {
    raw = JSON.parse(text)
  } catch (err) {
    throw new ProjectError('parse', 'The project file is damaged (it is not valid JSON).', [
      err instanceof Error ? err.message : String(err),
    ])
  }
  const { doc, fromVersion } = migrateToCurrent(raw, table)
  const result = ProjectSchema.safeParse(doc)
  if (!result.success) {
    throw new ProjectError(
      'invalid',
      'The project file contains invalid data and could not be opened.',
      describeIssues(result.error),
    )
  }
  return { project: result.data, fromVersion }
}
