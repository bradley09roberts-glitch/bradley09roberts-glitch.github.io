import { Menu, type MenuItemConstructorOptions, app } from 'electron'
import type { MenuCommand } from '@shared/ipc'

export const MenuId = {
  new: 'file.new',
  open: 'file.open',
  save: 'file.save',
  saveAs: 'file.save-as',
  close: 'file.close',
} as const

export interface MenuActions {
  command(command: MenuCommand): void
  about(): void
  revealLogs(): void
}

export function buildAppMenu(actions: MenuActions, isDev: boolean): Menu {
  const isMac = process.platform === 'darwin'

  const template: MenuItemConstructorOptions[] = [
    ...(isMac
      ? [
          {
            label: app.name,
            submenu: [
              { label: 'About Kinetik', click: actions.about },
              { type: 'separator' },
              { role: 'hide' },
              { role: 'hideOthers' },
              { role: 'unhide' },
              { type: 'separator' },
              { role: 'quit' },
            ],
          } satisfies MenuItemConstructorOptions,
        ]
      : []),
    {
      label: '&File',
      submenu: [
        {
          id: MenuId.new,
          label: 'New Project',
          accelerator: 'CmdOrCtrl+N',
          click: () => actions.command('new'),
        },
        {
          id: MenuId.open,
          label: 'Open Project…',
          accelerator: 'CmdOrCtrl+O',
          click: () => actions.command('open'),
        },
        { type: 'separator' },
        {
          id: MenuId.save,
          label: 'Save',
          accelerator: 'CmdOrCtrl+S',
          enabled: false,
          click: () => actions.command('save'),
        },
        {
          id: MenuId.saveAs,
          label: 'Save As…',
          accelerator: 'CmdOrCtrl+Shift+S',
          enabled: false,
          click: () => actions.command('save-as'),
        },
        { type: 'separator' },
        {
          id: MenuId.close,
          label: 'Close Project',
          accelerator: 'CmdOrCtrl+W',
          enabled: false,
          click: () => actions.command('close'),
        },
        ...(isMac ? [] : [{ type: 'separator' as const }, { role: 'quit' as const }]),
      ],
    },
    {
      // Text-field editing only. Project undo/redo (command history) replaces these in Phase 2.
      label: '&Edit',
      submenu: [
        { role: 'undo' },
        { role: 'redo' },
        { type: 'separator' },
        { role: 'cut' },
        { role: 'copy' },
        { role: 'paste' },
        { role: 'selectAll' },
      ],
    },
    {
      label: '&View',
      submenu: [
        ...(isDev
          ? [
              { role: 'reload' as const },
              { role: 'forceReload' as const },
              { role: 'toggleDevTools' as const },
              { type: 'separator' as const },
            ]
          : []),
        { role: 'togglefullscreen' },
      ],
    },
    {
      label: '&Help',
      submenu: [
        ...(isMac ? [] : [{ label: 'About Kinetik', click: actions.about }]),
        { label: 'Reveal Logs', click: actions.revealLogs },
      ],
    },
  ]

  return Menu.buildFromTemplate(template)
}

/** Enables the project-dependent File items. */
export function setProjectMenuState(menu: Menu, hasProject: boolean): void {
  for (const id of [MenuId.save, MenuId.saveAs, MenuId.close]) {
    const item = menu.getMenuItemById(id)
    if (item) item.enabled = hasProject
  }
}
