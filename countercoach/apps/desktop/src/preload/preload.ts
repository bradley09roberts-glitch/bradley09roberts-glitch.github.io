import { contextBridge, ipcRenderer, type IpcRendererEvent } from "electron";
import { IPC } from "../shared/channels.js";

/**
 * Minimal, explicit bridge. The renderer cannot access Node, the filesystem or arbitrary IPC
 * channels; it can only call these functions, whose inputs the main process validates.
 */
const on = (channel: string, fn: (payload: unknown) => void) => {
  const h = (_e: IpcRendererEvent, payload: unknown) => fn(payload);
  ipcRenderer.on(channel, h);
  return () => ipcRenderer.removeListener(channel, h);
};

contextBridge.exposeInMainWorld("countercoach", {
  getBootstrap: () => ipcRenderer.invoke(IPC.getBootstrap),
  getSettings: () => ipcRenderer.invoke(IPC.getSettings),
  setSettings: (patch: unknown) => ipcRenderer.invoke(IPC.setSettings, patch),
  checkData: () => ipcRenderer.invoke(IPC.checkData),
  refreshData: () => ipcRenderer.invoke(IPC.refreshData),
  overlayUpdate: (model: unknown) => ipcRenderer.send(IPC.overlayUpdate, model),
  setEditMode: (on: boolean) => ipcRenderer.invoke(IPC.overlaySetEdit, on),
  overlayResize: (height: number) => ipcRenderer.send(IPC.overlayResize, height),
  logAppend: (matchId: string, entry: unknown) => ipcRenderer.invoke(IPC.logAppend, { matchId, entry }),
  logList: () => ipcRenderer.invoke(IPC.logList),
  logRead: (id: string) => ipcRenderer.invoke(IPC.logRead, id),
  logDeleteAll: () => ipcRenderer.invoke(IPC.logDeleteAll),
  importReplay: () => ipcRenderer.invoke(IPC.importReplay),
  captureScreen: (delayMs: number) => ipcRenderer.invoke(IPC.screenCapture, delayMs),
  loadImage: () => ipcRenderer.invoke(IPC.screenLoadImage),
  savedCaptures: () => ipcRenderer.invoke(IPC.screenSaved),
  deleteSavedCaptures: () => ipcRenderer.invoke(IPC.screenDeleteSaved),
  openSavedCaptures: () => ipcRenderer.invoke(IPC.screenOpenSaved),
  onScreenCaptured: (fn: (m: unknown) => void) => on(IPC.screenCaptured, fn),
  iconTemplates: () => ipcRenderer.invoke(IPC.screenTemplates),
  onTemplatesProgress: (fn: (m: unknown) => void) => on(IPC.screenTemplatesProgress, fn),
  onOverlayModel: (fn: (m: unknown) => void) => on(IPC.overlayModel, fn),
  onOverlayState: (fn: (m: unknown) => void) => on(IPC.overlayState, fn),
  onDataEvent: (fn: (m: unknown) => void) => on(IPC.dataEvent, fn),
  onSettings: (fn: (m: unknown) => void) => on(IPC.settingsEvent, fn),
});
