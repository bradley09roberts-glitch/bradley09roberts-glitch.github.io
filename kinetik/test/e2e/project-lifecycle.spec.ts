/**
 * Phase 0 acceptance: the app launches, and an empty project can be created, saved and reopened.
 * Native dialogs are replaced with stubs in the main process so the test runs unattended.
 */
import {
  type ElectronApplication,
  type Page,
  _electron as electron,
  expect,
  test,
} from '@playwright/test'
import { existsSync } from 'node:fs'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const appDir = resolve(__dirname, '../..')

let app: ElectronApplication
let page: Page
let workDir: string

async function stubSaveDialog(filePath: string): Promise<void> {
  await app.evaluate(({ dialog }, target) => {
    dialog.showSaveDialog = (async () => ({
      canceled: false,
      filePath: target,
    })) as typeof dialog.showSaveDialog
  }, filePath)
}

async function stubOpenDialog(filePath: string): Promise<void> {
  await app.evaluate(({ dialog }, target) => {
    dialog.showOpenDialog = (async () => ({
      canceled: false,
      filePaths: [target],
    })) as typeof dialog.showOpenDialog
  }, filePath)
}

/** 0 = Save, 1 = Don't Save, 2 = Cancel (see confirmDiscard in src/main/ipc.ts). */
async function stubMessageBox(response: number): Promise<void> {
  await app.evaluate(({ dialog }, r) => {
    dialog.showMessageBox = (async () => ({
      response: r,
      checkboxChecked: false,
    })) as typeof dialog.showMessageBox
  }, response)
}

async function clickMenu(id: string): Promise<void> {
  await app.evaluate(({ Menu }, itemId) => {
    const item = Menu.getApplicationMenu()?.getMenuItemById(itemId)
    if (!item) throw new Error(`menu item ${itemId} not found`)
    if (!item.enabled) throw new Error(`menu item ${itemId} is disabled`)
    item.click()
  }, id)
}

async function windowTitle(): Promise<string> {
  return app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0]?.getTitle() ?? '')
}

test.beforeEach(async () => {
  workDir = await mkdtemp(join(tmpdir(), 'kinetik-e2e-'))
  const args = [appDir]
  // Chromium refuses to run as root without this (CI containers only).
  if (process.getuid?.() === 0) args.unshift('--no-sandbox')
  app = await electron.launch({
    args,
    env: { ...process.env, KINETIK_USER_DATA: join(workDir, 'userData') },
  })
  page = await app.firstWindow()
  await page.waitForLoadState('domcontentloaded')
})

test.afterEach(async () => {
  await app?.close()
  await rm(workDir, { recursive: true, force: true })
})

test('launches, creates, saves and reopens an empty project', async () => {
  await expect(page.getByTestId('welcome')).toBeVisible()
  await expect(page.getByTestId('ffmpeg-status')).toContainText('ffmpeg version')

  // Create
  await page.getByTestId('welcome-new').click()
  await page.getByTestId('new-project-name').fill('Derby Edit')
  await page.getByTestId('new-project-create').click()
  await expect(page.getByTestId('editor')).toBeVisible()
  await expect(page.getByTestId('project-name')).toHaveText('Derby Edit')
  await expect(page.getByTestId('comp-summary')).toHaveText(
    '1080×1920 · 60 fps · 30 s (1800 frames)',
  )
  await expect(page.getByTestId('doc-status')).toContainText('Unsaved')
  await expect.poll(windowTitle).toBe('• Derby Edit — Kinetik')

  // Save (the dialog path has no extension; Kinetik must add it)
  const file = join(workDir, 'derby.kinetik')
  await stubSaveDialog(join(workDir, 'derby'))
  await clickMenu('file.save')
  await expect(page.getByTestId('doc-status')).toHaveText('Saved · derby.kinetik')
  await expect.poll(windowTitle).toBe('Derby Edit — Kinetik')
  const saved = JSON.parse(await readFile(file, 'utf8')) as Record<string, unknown>
  expect(saved).toMatchObject({ format: 'kinetik.project', version: 1, name: 'Derby Edit' })

  // Close
  await clickMenu('file.close')
  await expect(page.getByTestId('welcome')).toBeVisible()

  // Reopen
  await stubOpenDialog(file)
  await page.getByTestId('welcome-open').click()
  await expect(page.getByTestId('editor')).toBeVisible()
  await expect(page.getByTestId('project-name')).toHaveText('Derby Edit')
  await expect(page.getByTestId('comp-summary')).toHaveText(
    '1080×1920 · 60 fps · 30 s (1800 frames)',
  )
  await expect(page.getByTestId('doc-status')).toHaveText('Saved · derby.kinetik')

  // Rename and save again: the previous save becomes the .bak
  await page.getByTestId('project-name').click()
  await page.getByTestId('project-name-input').fill('Derby Edit v2')
  await page.getByTestId('project-name-input').press('Enter')
  await expect(page.getByTestId('doc-status')).toContainText('Unsaved')
  await clickMenu('file.save')
  await expect(page.getByTestId('doc-status')).toHaveText('Saved · derby.kinetik')
  const resaved = JSON.parse(await readFile(file, 'utf8')) as Record<string, unknown>
  const backup = JSON.parse(await readFile(`${file}.bak`, 'utf8')) as Record<string, unknown>
  expect(resaved.name).toBe('Derby Edit v2')
  expect(backup.name).toBe('Derby Edit')
  expect(resaved.id).toBe(saved.id)

  // Logs were written
  expect(existsSync(join(workDir, 'userData', 'logs'))).toBe(true)
})

test('asks before discarding unsaved changes', async () => {
  await page.getByTestId('welcome-new').click()
  await page.getByTestId('new-project-create').click()
  await expect(page.getByTestId('editor')).toBeVisible()

  await stubMessageBox(2) // Cancel
  await clickMenu('file.close')
  await expect(page.getByTestId('editor')).toBeVisible()

  await stubMessageBox(1) // Don't Save
  await clickMenu('file.close')
  await expect(page.getByTestId('welcome')).toBeVisible()
})

test('shows a clear error for damaged and newer-version files', async () => {
  const damaged = join(workDir, 'damaged.kinetik')
  await writeFile(damaged, '{"format":"kinetik.project",', 'utf8')
  await stubOpenDialog(damaged)
  await page.getByTestId('welcome-open').click()
  await expect(page.getByTestId('error-banner')).toContainText('not valid JSON')
  await expect(page.getByTestId('welcome')).toBeVisible()

  const future = join(workDir, 'future.kinetik')
  await writeFile(future, JSON.stringify({ format: 'kinetik.project', version: 99 }), 'utf8')
  await stubOpenDialog(future)
  await page.getByTestId('welcome-open').click()
  await expect(page.getByTestId('error-banner')).toContainText('newer version of Kinetik')
})
