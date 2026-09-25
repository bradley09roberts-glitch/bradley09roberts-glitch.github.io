/**
 * Preload: exposes the typed `window.kinetik` API (SPEC §3.6). Runs sandboxed, so at runtime it
 * may only require `electron`; anything imported from @shared is bundled into this file.
 */
import { type IpcRendererEvent, contextBridge, ipcRenderer } from 'electron'
import { IpcChannel, type KinetikApi, type MenuCommand } from '@shared/ipc'

const api: KinetikApi = {
  app: {
    getInfo: () => ipcRenderer.invoke(IpcChannel.appInfo),
    revealLogs: () => ipcRenderer.invoke(IpcChannel.revealLogs),
  },
  project: {
    openDialog: () => ipcRenderer.invoke(IpcChannel.projectOpenDialog),
    openPath: (path) => ipcRenderer.invoke(IpcChannel.projectOpenPath, path),
    save: (request) => ipcRenderer.invoke(IpcChannel.projectSave, request),
  },
  dialog: {
    confirmDiscard: (projectName) => ipcRenderer.invoke(IpcChannel.confirmDiscard, projectName),
  },
  window: {
    setDocumentState: (state) => ipcRenderer.send(IpcChannel.documentState, state),
    closeNow: () => ipcRenderer.send(IpcChannel.closeWindowNow),
  },
  log: (level, msg, ctx) => ipcRenderer.send(IpcChannel.log, { level, msg, ctx }),
  onMenuCommand: (listener) => {
    const handler = (_event: IpcRendererEvent, command: MenuCommand): void => listener(command)
    ipcRenderer.on(IpcChannel.menuCommand, handler)
    return () => {
      ipcRenderer.removeListener(IpcChannel.menuCommand, handler)
    }
  },
}

contextBridge.exposeInMainWorld('kinetik', api)
