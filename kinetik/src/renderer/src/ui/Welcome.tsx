import type { AppInfo } from '@shared/ipc'
import { LogoMark } from './Logo'

interface Props {
  info: AppInfo | null
  busy: boolean
  onNew(): void
  onOpen(): void
}

export function Welcome({ info, busy, onNew, onOpen }: Props): React.JSX.Element {
  return (
    <main className="welcome" data-testid="welcome">
      <div className="welcome-card">
        <LogoMark size={64} />
        <h1 className="welcome-title">kinetik</h1>
        <p className="welcome-tagline">Beat-synced motion edits, frame-perfect.</p>
        <div className="welcome-actions">
          <button
            className="btn btn-primary btn-lg"
            data-testid="welcome-new"
            onClick={onNew}
            disabled={busy}
          >
            New Project
          </button>
          <button
            className="btn btn-lg"
            data-testid="welcome-open"
            onClick={onOpen}
            disabled={busy}
          >
            Open Project…
          </button>
        </div>
        <p className="welcome-shortcuts">
          <kbd>Ctrl/⌘ N</kbd> new · <kbd>Ctrl/⌘ O</kbd> open
        </p>
      </div>
      <footer className="welcome-footer">
        {info ? (
          <>
            <span>v{info.version}</span>
            <span
              className={info.ffmpeg.ok ? 'status-ok' : 'status-bad'}
              data-testid="ffmpeg-status"
            >
              {info.ffmpeg.ok
                ? `● ${info.ffmpeg.version}`
                : `● FFmpeg not found: ${info.ffmpeg.error}`}
            </span>
          </>
        ) : (
          <span>Starting…</span>
        )}
      </footer>
    </main>
  )
}
