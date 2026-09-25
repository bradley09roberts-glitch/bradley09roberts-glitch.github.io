/**
 * Forward-only project migrations (SPEC §4, ADR-8).
 *
 * `migrations[v]` converts a raw document at version v into version v + 1. Migrations operate on
 * plain JSON (never on typed Project objects), because older shapes no longer match the types.
 */
import { PROJECT_FORMAT, PROJECT_VERSION } from './schema'
import { ProjectError } from './errors'

export type JsonObject = Record<string, unknown>
export type Migration = (doc: JsonObject) => JsonObject

/** Empty until the first schema change (expected in Phase 1a). */
export const migrations: Readonly<Record<number, Migration>> = {}

export function isJsonObject(value: unknown): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** Reads the format and version header without validating anything else. */
export function readHeader(doc: unknown): { version: number } {
  if (!isJsonObject(doc) || doc.format !== PROJECT_FORMAT) {
    throw new ProjectError('format', 'This file is not a Kinetik project.')
  }
  const { version } = doc
  if (typeof version !== 'number' || !Number.isInteger(version) || version < 1) {
    throw new ProjectError('format', 'This project file has a missing or invalid version number.')
  }
  return { version }
}

export function migrateToCurrent(
  doc: unknown,
  table: Readonly<Record<number, Migration>> = migrations,
  targetVersion: number = PROJECT_VERSION,
): { doc: JsonObject; fromVersion: number } {
  const { version: fromVersion } = readHeader(doc)
  if (fromVersion > targetVersion) {
    throw new ProjectError(
      'too-new',
      `This project was saved by a newer version of Kinetik (file version ${fromVersion}, ` +
        `this app supports up to ${targetVersion}). Update Kinetik to open it.`,
    )
  }
  let current = doc as JsonObject
  for (let v = fromVersion; v < targetVersion; v++) {
    const step = table[v]
    if (!step) {
      throw new ProjectError('format', `No migration from project version ${v} to ${v + 1}.`)
    }
    current = step(current)
    if (current.version !== v + 1) {
      throw new ProjectError(
        'format',
        `Migration from version ${v} did not produce version ${v + 1}.`,
      )
    }
  }
  return { doc: current, fromVersion }
}
