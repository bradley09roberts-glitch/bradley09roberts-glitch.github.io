import { useState } from 'react'
import type { Composition, Project } from '@shared/project'
import { formatFrameRate } from '@shared/time/units'
import { Wordmark } from './Logo'

interface Props {
  project: Project
  path: string | null
  dirty: boolean
  onRename(name: string): void
}

function fileName(path: string): string {
  return path.split(/[\\/]/).pop() ?? path
}

function compSummary(comp: Composition): string {
  const seconds = (comp.durationFrames * comp.frameRate.den) / comp.frameRate.num
  const secondsLabel = Number.isInteger(seconds) ? String(seconds) : seconds.toFixed(2)
  return `${comp.width}×${comp.height} · ${formatFrameRate(comp.frameRate)} fps · ${secondsLabel} s (${comp.durationFrames} frames)`
}

function ProjectName({
  name,
  onRename,
}: {
  name: string
  onRename(name: string): void
}): React.JSX.Element {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(name)

  if (!editing) {
    return (
      <button
        className="project-name"
        data-testid="project-name"
        title="Rename project"
        onClick={() => {
          setDraft(name)
          setEditing(true)
        }}
      >
        {name}
      </button>
    )
  }
  const commit = (): void => {
    onRename(draft)
    setEditing(false)
  }
  return (
    <input
      className="project-name-input"
      data-testid="project-name-input"
      value={draft}
      maxLength={200}
      autoFocus
      onFocus={(e) => e.target.select()}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === 'Enter') commit()
        if (e.key === 'Escape') setEditing(false)
      }}
    />
  )
}

function Panel({
  title,
  phase,
  className,
  children,
}: {
  title: string
  phase: string
  className: string
  children?: React.ReactNode
}): React.JSX.Element {
  return (
    <section className={`panel ${className}`}>
      <header className="panel-header">{title}</header>
      <div className="panel-body">
        {children ?? <p className="panel-placeholder">Arrives in Phase {phase}</p>}
      </div>
    </section>
  )
}

export function EditorShell({ project, path, dirty, onRename }: Props): React.JSX.Element {
  const comp =
    project.compositions.find((c) => c.id === project.settings.defaultCompId) ??
    project.compositions[0]

  return (
    <div className="editor" data-testid="editor">
      <header className="topbar">
        <Wordmark />
        <div className="topbar-doc">
          <ProjectName name={project.name} onRename={onRename} />
          <span className="doc-status" data-testid="doc-status">
            {dirty ? '● Unsaved changes' : path ? `Saved · ${fileName(path)}` : 'Not saved'}
          </span>
        </div>
      </header>
      <div className="workspace">
        <Panel title="Media" phase="1a" className="area-media" />
        <Panel title="Viewer" phase="1b" className="area-viewer">
          {comp && (
            <div className="viewer-stage">
              <div
                className="viewer-frame"
                style={{ aspectRatio: `${comp.width} / ${comp.height}` }}
                data-testid="viewer-frame"
              >
                <span className="viewer-comp-name">{comp.name}</span>
              </div>
              <p className="viewer-caption" data-testid="comp-summary">
                {compSummary(comp)}
              </p>
            </div>
          )}
        </Panel>
        <Panel title="Inspector" phase="3" className="area-inspector" />
        <Panel title="Timeline" phase="2" className="area-timeline" />
      </div>
    </div>
  )
}
