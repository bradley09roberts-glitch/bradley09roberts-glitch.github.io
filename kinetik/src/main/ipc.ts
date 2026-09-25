/**
 * IPC handlers (SPEC §3.6). Every payload from the renderer is validated before use.
 */
import { BrowserWindow, dialog, ipcMain, shell } from 'electron'
import { basename, extname, join } from 'node:path'
import { z } from 'zod'
import {
  type AppInfo,
  type DiscardChoice,
  type DocumentState,
  IpcChannel,
  type OpenResult,
  type ProjectFailure,
  type SaveResult,
} from '@shared/ipc'
import { LOG_LEVELS } from '@shared/log-types'
import { PROJECT_FILE_EXTENSION, ProjectError, type Project } from '@shared/project'
import { type FileLogger, log } from './logger'
import { loadProjectFile, saveProjectFile } from './project-io'

const projectFilters = [{ name: 'Kinetik Project', extensions: [PROJECT_FILE_EXTENSION] }]

const SaveRequestSchema = z.object({
  // The project itself is fully validated by serializeProject during save.
  project: z.record(z.string(), z.unknown()),
  path: z.string().min(1).nullable(),
  saveAs: z.boolean(),
})

const LogPayloadSchema = z.object({
  level: z.enum(LOG_LEVELS),
  msg: z.string().max(10_000),
  ctx: z.record(z.string(), z.unknown()).optional(),
})

export const DocumentStateSchema = z.object({
  hasProject: z.boolean(),
  name: z.string().nullable(),
  path: z.string().nullable(),
  dirty: z.boolean(),
})

export interface IpcDeps {
  getAppInfo(): Promise<AppInfo>
  fileLogger: FileLogger
  onDocumentState(win: BrowserWindow, state: DocumentState): void
  closeWindowNow(win: BrowserWindow): void
  defaultProjectDir(): string
}

function toFailure(err: unknown): ProjectFailure {
  if (err instanceof ProjectError) {
    return { status: 'error', code: err.code, message: err.message, details: err.details }
  }
  log.error('unexpected project error', { err })
  return {
    status: 'error',
    code: 'io',
    message: 'Something went wrong. See the log for details.',
    details: [err instanceof Error ? err.message : String(err)],
  }
}

function withExtension(path: string): string {
  return extname(path).toLowerCase() === `.${PROJECT_FILE_EXTENSION}`
    ? path
    : `${path}.${PROJECT_FILE_EXTENSION}`
}

function safeFileStem(name: string): string {
  // Control characters are invalid in file names on every platform.
  // eslint-disable-next-line no-control-regex
  return name.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').trim() || 'Untitled'
}

async function openPath(path: string): Promise<OpenResult> {
  try {
    const { project, fromVersion } = await loadProjectFile(path)
    log.info('project opened', { path, fromVersion })
    return {
      status: 'opened',
      path,
      project,
      migratedFrom: fromVersion === project.version ? null : fromVersion,
    }
  } catch (err) {
    log.warn('project open failed', { path, err })
    return toFailure(err)
  }
}

export function registerIpc(deps: IpcDeps): void {
  const senderWindow = (event: Electron.IpcMainEvent | Electron.IpcMainInvokeEvent) =>
    BrowserWindow.fromWebContents(event.sender)

  ipcMain.handle(IpcChannel.appInfo, () => deps.getAppInfo())

  ipcMain.handle(IpcChannel.revealLogs, async () => {
    await shell.openPath(deps.fileLogger.dir)
  })

  ipcMain.handle(IpcChannel.projectOpenDialog, async (event): Promise<OpenResult> => {
    const win = senderWindow(event)
    const options: Electron.OpenDialogOptions = {
      title: 'Open Kinetik Project',
      filters: projectFilters,
      properties: ['openFile'],
    }
    const result = win
      ? await dialog.showOpenDialog(win, options)
      : await dialog.showOpenDialog(options)
    const path = result.filePaths[0]
    if (result.canceled || !path) return { status: 'cancelled' }
    return openPath(path)
  })

  ipcMain.handle(
    IpcChannel.projectOpenPath,
    async (_event, rawPath: unknown): Promise<OpenResult> => {
      const path = z.string().min(1).safeParse(rawPath)
      if (!path.success) return toFailure(new ProjectError('io', 'Invalid file path.'))
      return openPath(path.data)
    },
  )

  ipcMain.handle(
    IpcChannel.projectSave,
    async (event, rawRequest: unknown): Promise<SaveResult> => {
      const parsed = SaveRequestSchema.safeParse(rawRequest)
      if (!parsed.success) {
        return toFailure(new ProjectError('invalid', 'Invalid save request.'))
      }
      const request = parsed.data
      let path = request.path
      if (request.saveAs || !path) {
        const win = senderWindow(event)
        const name = typeof request.project.name === 'string' ? request.project.name : 'Untitled'
        const options: Electron.SaveDialogOptions = {
          title: 'Save Kinetik Project',
          defaultPath:
            path ??
            join(deps.defaultProjectDir(), `${safeFileStem(name)}.${PROJECT_FILE_EXTENSION}`),
          filters: projectFilters,
        }
        const result = win
          ? await dialog.showSaveDialog(win, options)
          : await dialog.showSaveDialog(options)
        if (result.canceled || !result.filePath) return { status: 'cancelled' }
        path = withExtension(result.filePath)
      }
      const project = {
        ...request.project,
        modifiedAt: new Date().toISOString(),
      } as unknown as Project
      try {
        await saveProjectFile(path, project)
        log.info('project saved', { path, file: basename(path) })
        return { status: 'saved', path, project }
      } catch (err) {
        log.warn('project save failed', { path, err })
        return toFailure(err)
      }
    },
  )

  ipcMain.handle(
    IpcChannel.confirmDiscard,
    async (event, rawName: unknown): Promise<DiscardChoice> => {
      const name = typeof rawName === 'string' && rawName ? rawName : 'Untitled'
      const win = senderWindow(event)
      const options: Electron.MessageBoxOptions = {
        type: 'warning',
        buttons: ['Save', "Don't Save", 'Cancel'],
        defaultId: 0,
        cancelId: 2,
        message: `Save changes to "${name}"?`,
        detail: 'Your changes will be lost if you don’t save them.',
      }
      const { response } = win
        ? await dialog.showMessageBox(win, options)
        : await dialog.showMessageBox(options)
      return response === 0 ? 'save' : response === 1 ? 'discard' : 'cancel'
    },
  )

  ipcMain.on(IpcChannel.documentState, (event, rawState: unknown) => {
    const win = senderWindow(event)
    const state = DocumentStateSchema.safeParse(rawState)
    if (win && state.success) deps.onDocumentState(win, state.data)
  })

  ipcMain.on(IpcChannel.closeWindowNow, (event) => {
    const win = senderWindow(event)
    if (win) deps.closeWindowNow(win)
  })

  ipcMain.on(IpcChannel.log, (_event, rawPayload: unknown) => {
    const payload = LogPayloadSchema.safeParse(rawPayload)
    if (!payload.success) return
    const { level, msg, ctx } = payload.data
    deps.fileLogger.write({
      t: new Date().toISOString(),
      level,
      src: 'renderer',
      msg,
      ...(ctx ? { ctx } : {}),
    })
  })
}
