/**
 * The open document: project, file path and dirty flag, plus the New/Open/Save/Close flows.
 *
 * Phase 0 note: edits here (rename) change the project directly. From Phase 2 every project
 * mutation goes through the command history (SPEC §3.5) instead.
 */
import { create } from 'zustand'
import type { ProjectFailure } from '@shared/ipc'
import { type Project, createEmptyProject } from '@shared/project'
import { log } from '../lib/log'

export interface DocumentError {
  title: string
  message: string
  details: readonly string[]
}

interface DocumentStore {
  project: Project | null
  path: string | null
  dirty: boolean
  busy: boolean
  error: DocumentError | null
  /** Short-lived confirmation, e.g. "Saved". */
  notice: string | null
  appVersion: string

  setAppVersion(version: string): void
  createProject(name: string): void
  renameProject(name: string): void
  /** Returns false if the user cancelled or the save failed. */
  save(saveAs: boolean): Promise<boolean>
  open(): Promise<void>
  close(): void
  /** Asks to save unsaved changes. Returns true if it is OK to discard the current document. */
  confirmDiscard(): Promise<boolean>
  dismissError(): void
  clearNotice(): void
}

function failureToError(title: string, failure: ProjectFailure): DocumentError {
  return { title, message: failure.message, details: failure.details }
}

export const useDocument = create<DocumentStore>((set, get) => ({
  project: null,
  path: null,
  dirty: false,
  busy: false,
  error: null,
  notice: null,
  appVersion: '0.0.0',

  setAppVersion: (appVersion) => set({ appVersion }),

  createProject: (name) => {
    const project = createEmptyProject({
      name,
      appVersion: get().appVersion,
      id: crypto.randomUUID(),
      compId: crypto.randomUUID(),
      now: new Date(),
    })
    log.info('project created', { id: project.id })
    // A brand-new project counts as unsaved so closing it prompts to save.
    set({ project, path: null, dirty: true, error: null, notice: null })
  },

  renameProject: (name) => {
    const { project } = get()
    const trimmed = name.trim()
    if (!project || !trimmed || trimmed === project.name) return
    set({ project: { ...project, name: trimmed.slice(0, 200) }, dirty: true })
  },

  save: async (saveAs) => {
    const { project, path, busy } = get()
    if (!project || busy) return false
    set({ busy: true })
    try {
      const result = await window.kinetik.project.save({
        project: { ...project, appVersion: get().appVersion },
        path,
        saveAs,
      })
      if (result.status === 'cancelled') return false
      if (result.status === 'error') {
        set({ error: failureToError('Could not save the project', result) })
        return false
      }
      // Keep edits made while the save was in flight: only clear dirty if nothing changed.
      const unchanged = get().project === project
      set({
        project: unchanged
          ? result.project
          : { ...get().project!, modifiedAt: result.project.modifiedAt },
        path: result.path,
        dirty: !unchanged,
        notice: 'Saved',
      })
      return true
    } finally {
      set({ busy: false })
    }
  },

  open: async () => {
    if (get().busy) return
    set({ busy: true })
    try {
      const result = await window.kinetik.project.openDialog()
      if (result.status === 'cancelled') return
      if (result.status === 'error') {
        set({ error: failureToError('Could not open the project', result) })
        return
      }
      set({
        project: result.project,
        path: result.path,
        dirty: false,
        error: null,
        notice: result.migratedFrom
          ? `Upgraded from project version ${result.migratedFrom}. Saving will use the new format.`
          : null,
      })
    } finally {
      set({ busy: false })
    }
  },

  close: () => set({ project: null, path: null, dirty: false, notice: null }),

  confirmDiscard: async () => {
    const { project, dirty } = get()
    if (!project || !dirty) return true
    const choice = await window.kinetik.dialog.confirmDiscard(project.name)
    if (choice === 'cancel') return false
    if (choice === 'discard') return true
    return get().save(false)
  },

  dismissError: () => set({ error: null }),
  clearNotice: () => set({ notice: null }),
}))
