import { useCallback, useEffect, useState } from 'react'
import type { AppInfo, MenuCommand } from '@shared/ipc'
import { log } from './lib/log'
import { useDocument } from './state/document-store'
import { EditorShell } from './ui/EditorShell'
import { NewProjectDialog } from './ui/NewProjectDialog'
import { Welcome } from './ui/Welcome'

function ErrorBanner(): React.JSX.Element | null {
  const error = useDocument((s) => s.error)
  const dismiss = useDocument((s) => s.dismissError)
  if (!error) return null
  return (
    <div className="banner banner-error" role="alert" data-testid="error-banner">
      <div>
        <strong>{error.title}.</strong> {error.message}
        {error.details.length > 0 && (
          <details>
            <summary>Details</summary>
            <ul>
              {error.details.map((d, i) => (
                <li key={i}>{d}</li>
              ))}
            </ul>
          </details>
        )}
      </div>
      <button className="btn btn-ghost" onClick={dismiss} aria-label="Dismiss">
        ✕
      </button>
    </div>
  )
}

function Notice(): React.JSX.Element | null {
  const notice = useDocument((s) => s.notice)
  const clear = useDocument((s) => s.clearNotice)
  useEffect(() => {
    if (!notice) return
    const id = window.setTimeout(clear, 2500)
    return () => window.clearTimeout(id)
  }, [notice, clear])
  if (!notice) return null
  return (
    <div className="toast" role="status" data-testid="notice">
      {notice}
    </div>
  )
}

export function App(): React.JSX.Element {
  const project = useDocument((s) => s.project)
  const path = useDocument((s) => s.path)
  const dirty = useDocument((s) => s.dirty)
  const busy = useDocument((s) => s.busy)
  const [info, setInfo] = useState<AppInfo | null>(null)
  const [showNew, setShowNew] = useState(false)

  useEffect(() => {
    window.kinetik.app
      .getInfo()
      .then((i) => {
        setInfo(i)
        useDocument.getState().setAppVersion(i.version)
      })
      .catch((err: unknown) => log.error('failed to get app info', { err: String(err) }))
  }, [])

  // Keep the main process informed (window title, dirty marker, menu enablement, close prompt).
  useEffect(() => {
    window.kinetik.window.setDocumentState({
      hasProject: project !== null,
      name: project?.name ?? null,
      path,
      dirty,
    })
  }, [project, path, dirty])

  const handleCommand = useCallback(async (command: MenuCommand) => {
    const doc = useDocument.getState()
    switch (command) {
      case 'new':
        if (await doc.confirmDiscard()) setShowNew(true)
        break
      case 'open':
        if (await doc.confirmDiscard()) await doc.open()
        break
      case 'save':
        await doc.save(false)
        break
      case 'save-as':
        await doc.save(true)
        break
      case 'close':
        if (await doc.confirmDiscard()) doc.close()
        break
      case 'save-and-close':
        if (await doc.save(false)) window.kinetik.window.closeNow()
        break
    }
  }, [])

  useEffect(
    () =>
      window.kinetik.onMenuCommand((command) => {
        handleCommand(command).catch((err: unknown) =>
          log.error('menu command failed', { command, err: String(err) }),
        )
      }),
    [handleCommand],
  )

  return (
    <div className="app">
      <ErrorBanner />
      {project ? (
        <EditorShell
          project={project}
          path={path}
          dirty={dirty}
          onRename={(name) => useDocument.getState().renameProject(name)}
        />
      ) : (
        <Welcome
          info={info}
          busy={busy}
          onNew={() => void handleCommand('new')}
          onOpen={() => void handleCommand('open')}
        />
      )}
      {showNew && (
        <NewProjectDialog
          onCancel={() => setShowNew(false)}
          onCreate={(name) => {
            useDocument.getState().createProject(name)
            setShowNew(false)
          }}
        />
      )}
      <Notice />
    </div>
  )
}
