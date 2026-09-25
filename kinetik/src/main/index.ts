/**
 * Electron main process: app lifecycle, windows, menus and IPC routing (SPEC §3.1).
 * Heavy work never happens here; FFmpeg jobs move to the media-service utility process in Phase 1a.
 */
import { BrowserWindow, Menu, app, dialog, session, shell } from 'electron'
import { join } from 'node:path'
import { type AppInfo, type DocumentState, IpcChannel, type MenuCommand } from '@shared/ipc'
import { checkBinary, resolveFfmpegPath, resolveFfprobePath } from './ffmpeg'
import { registerIpc } from './ipc'
import { type FileLogger, createFileLogger, log, setLogger } from './logger'
import { buildAppMenu, setProjectMenuState } from './menu'

const isDev = !app.isPackaged && Boolean(process.env.ELECTRON_RENDERER_URL)

// Tests (and power users) can isolate settings, logs and caches in a separate folder.
if (process.env.KINETIK_USER_DATA) {
  app.setPath('userData', process.env.KINETIK_USER_DATA)
}
app.setName('Kinetik')

interface WindowState {
  doc: DocumentState
  /** Set once the user has agreed to close (or saved), so the next close goes through. */
  allowClose: boolean
}

const windowStates = new Map<number, WindowState>()
let appMenu: Menu | null = null
let fileLogger: FileLogger | null = null
let appInfoPromise: Promise<AppInfo> | null = null

function windowTitle(doc: DocumentState): string {
  if (!doc.hasProject) return 'Kinetik'
  return `${doc.dirty ? '• ' : ''}${doc.name ?? 'Untitled'} — Kinetik`
}

function sendMenuCommand(command: MenuCommand): void {
  const win = BrowserWindow.getFocusedWindow() ?? BrowserWindow.getAllWindows()[0]
  win?.webContents.send(IpcChannel.menuCommand, command)
}

function getAppInfo(): Promise<AppInfo> {
  appInfoPromise ??= Promise.all([
    checkBinary(resolveFfmpegPath()),
    checkBinary(resolveFfprobePath()),
  ]).then(([ffmpeg, ffprobe]) => {
    if (!ffmpeg.ok) log.error('ffmpeg check failed', { ...ffmpeg })
    if (!ffprobe.ok) log.error('ffprobe check failed', { ...ffprobe })
    return {
      name: 'Kinetik',
      version: app.getVersion(),
      platform: process.platform,
      arch: process.arch,
      versions: {
        electron: process.versions.electron,
        chrome: process.versions.chrome,
        node: process.versions.node,
      },
      ffmpeg,
      ffprobe,
      logDir: fileLogger?.dir ?? '',
    }
  })
  return appInfoPromise
}

async function showAbout(): Promise<void> {
  const info = await getAppInfo()
  const ffmpegLine = info.ffmpeg.ok ? info.ffmpeg.version : `not working (${info.ffmpeg.error})`
  const ffprobeLine = info.ffprobe.ok ? info.ffprobe.version : `not working (${info.ffprobe.error})`
  await dialog.showMessageBox({
    type: 'info',
    title: 'About Kinetik',
    message: `Kinetik ${info.version}`,
    detail: [
      'Motion graphics for beat-synced football edits.',
      '',
      `Electron ${info.versions.electron} · Chromium ${info.versions.chrome} · Node ${info.versions.node}`,
      `FFmpeg: ${ffmpegLine}`,
      `ffprobe: ${ffprobeLine}`,
      `Platform: ${info.platform} ${info.arch}`,
    ].join('\n'),
  })
}

function createMainWindow(): BrowserWindow {
  const win = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1024,
    minHeight: 640,
    show: false,
    backgroundColor: '#0D0F12',
    title: 'Kinetik',
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
    },
  })

  const state: WindowState = {
    doc: { hasProject: false, name: null, path: null, dirty: false },
    allowClose: false,
  }
  windowStates.set(win.id, state)

  win.once('ready-to-show', () => win.show())

  // Security: this window only ever shows Kinetik's own UI.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//.test(url)) void shell.openExternal(url)
    return { action: 'deny' }
  })
  win.webContents.on('will-navigate', (event) => event.preventDefault())

  win.webContents.on('render-process-gone', (_event, details) => {
    log.error('renderer process gone', { ...details })
  })

  win.on('close', (event) => {
    if (state.allowClose || !state.doc.dirty) return
    event.preventDefault()
    const choice = dialog.showMessageBoxSync(win, {
      type: 'warning',
      buttons: ['Save', "Don't Save", 'Cancel'],
      defaultId: 0,
      cancelId: 2,
      message: `Save changes to "${state.doc.name ?? 'Untitled'}"?`,
      detail: 'Your changes will be lost if you don’t save them.',
    })
    if (choice === 0) {
      // The renderer saves, then calls window.closeNow() if the save succeeded.
      win.webContents.send(IpcChannel.menuCommand, 'save-and-close' satisfies MenuCommand)
    } else if (choice === 1) {
      state.allowClose = true
      win.close()
    }
  })

  win.on('closed', () => windowStates.delete(win.id))

  if (process.env.ELECTRON_RENDERER_URL) {
    void win.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    void win.loadFile(join(__dirname, '../renderer/index.html'))
  }
  return win
}

async function main(): Promise<void> {
  await app.whenReady()

  fileLogger = await createFileLogger({
    dir: join(app.getPath('userData'), 'logs'),
    minLevel: isDev ? 'debug' : 'info',
    echo: isDev,
  })
  setLogger(fileLogger)
  log.info('Kinetik starting', {
    version: app.getVersion(),
    electron: process.versions.electron,
    platform: process.platform,
    arch: process.arch,
    userData: app.getPath('userData'),
  })

  process.on('uncaughtException', (err) => log.error('uncaught exception (main)', { err }))
  process.on('unhandledRejection', (reason) => log.error('unhandled rejection (main)', { reason }))

  // No web permissions are needed yet; deny everything by default.
  session.defaultSession.setPermissionRequestHandler((_wc, _permission, callback) =>
    callback(false),
  )

  const logger = fileLogger
  registerIpc({
    getAppInfo,
    fileLogger: logger,
    defaultProjectDir: () => app.getPath('documents'),
    onDocumentState: (win, doc) => {
      const state = windowStates.get(win.id)
      if (!state) return
      state.doc = doc
      win.setTitle(windowTitle(doc))
      win.setDocumentEdited(doc.dirty)
      if (appMenu) setProjectMenuState(appMenu, doc.hasProject)
    },
    closeWindowNow: (win) => {
      const state = windowStates.get(win.id)
      if (state) state.allowClose = true
      win.close()
    },
  })

  appMenu = buildAppMenu(
    {
      command: sendMenuCommand,
      about: () => void showAbout(),
      revealLogs: () => void shell.openPath(logger.dir),
    },
    isDev,
  )
  Menu.setApplicationMenu(appMenu)

  // Warm up the FFmpeg check so the About box and welcome screen are instant.
  void getAppInfo()

  createMainWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createMainWindow()
  })
}

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', () => {
  log.info('Kinetik quitting')
})

main().catch((err: unknown) => {
  log.error('fatal startup error', { err })
  dialog.showErrorBox('Kinetik could not start', err instanceof Error ? err.message : String(err))
  app.exit(1)
})
