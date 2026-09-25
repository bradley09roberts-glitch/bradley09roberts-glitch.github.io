export type ProjectErrorCode =
  /** The text is not valid JSON. */
  | 'parse'
  /** Not a Kinetik project, or a bad header. */
  | 'format'
  /** Saved by a newer version of the app. */
  | 'too-new'
  /** Valid header but the content fails schema validation. */
  | 'invalid'
  /** Reading or writing the file failed. */
  | 'io'

/** A user-presentable project load/save failure. `message` is safe to show in the UI. */
export class ProjectError extends Error {
  readonly code: ProjectErrorCode
  readonly details: readonly string[]

  constructor(code: ProjectErrorCode, message: string, details: readonly string[] = []) {
    super(message)
    this.name = 'ProjectError'
    this.code = code
    this.details = details
  }
}
