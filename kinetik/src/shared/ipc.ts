/**
 * Typed IPC contract between main, preload and renderer (SPEC §3.6).
 *
 * To add a channel: add its name to `IpcChannel`, add the method to `KinetikApi`, implement it in
 * src/preload/index.ts, and register a handler (with payload validation) in src/main/ipc.ts.
 */
import type { LogContext, LogLevel } from './log-types'
import type { Project, ProjectErrorCode } from './project'

export const IpcChannel = {
  appInfo: 'app:info',
  revealLogs: 'app:reveal-logs',
  projectOpenDialog: 'project:open-dialog',
  projectOpenPath: 'project:open-path',
  projectSave: 'project:save',
  confirmDiscard: 'dialog:confirm-discard',
  documentState: 'window:document-state',
  closeWindowNow: 'window:close-now',
  log: 'log:write',
  menuCommand: 'menu:command',
} as const

export type MenuCommand = 'new' | 'open' | 'save' | 'save-as' | 'close' | 'save-and-close'

export interface FfmpegStatus {
  ok: boolean
  /** First line of `ffmpeg -version`, e.g. "ffmpeg version 7.0.2-static". */
  version: string | null
  path: string | null
  error: string | null
}

export interface AppInfo {
  name: string
  version: string
  platform: string
  arch: string
  versions: { electron: string; chrome: string; node: string }
  ffmpeg: FfmpegStatus
  ffprobe: FfmpegStatus
  logDir: string
}

export interface ProjectFailure {
  status: 'error'
  code: ProjectErrorCode
  message: string
  details: readonly string[]
}

export type OpenResult =
  | { status: 'opened'; path: string; project: Project; migratedFrom: number | null }
  | { status: 'cancelled' }
  | ProjectFailure

export interface SaveRequest {
  project: Project
  /** Current file path, or null if the project has never been saved. */
  path: string | null
  /** Always show the save dialog (Save As…). */
  saveAs: boolean
}

export type SaveResult =
  { status: 'saved'; path: string; project: Project } | { status: 'cancelled' } | ProjectFailure

export type DiscardChoice = 'save' | 'discard' | 'cancel'

export interface DocumentState {
  hasProject: boolean
  name: string | null
  path: string | null
  dirty: boolean
}

/** Exposed to the renderer as `window.kinetik`. */
export interface KinetikApi {
  app: {
    getInfo(): Promise<AppInfo>
    revealLogs(): Promise<void>
  }
  project: {
    openDialog(): Promise<OpenResult>
    openPath(path: string): Promise<OpenResult>
    save(request: SaveRequest): Promise<SaveResult>
  }
  dialog: {
    confirmDiscard(projectName: string): Promise<DiscardChoice>
  }
  window: {
    setDocumentState(state: DocumentState): void
    closeNow(): void
  }
  log(level: LogLevel, msg: string, ctx?: LogContext): void
  onMenuCommand(listener: (command: MenuCommand) => void): () => void
}
