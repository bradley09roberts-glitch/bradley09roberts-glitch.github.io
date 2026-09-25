import { useEffect, useRef, useState } from 'react'

interface Props {
  onCreate(name: string): void
  onCancel(): void
}

export function NewProjectDialog({ onCreate, onCancel }: Props): React.JSX.Element {
  const [name, setName] = useState('Untitled Edit')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    inputRef.current?.select()
  }, [])

  const submit = (): void => {
    if (name.trim()) onCreate(name.trim())
  }

  return (
    <div className="modal-backdrop" onMouseDown={onCancel}>
      <form
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="new-project-title"
        onMouseDown={(e) => e.stopPropagation()}
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') onCancel()
        }}
      >
        <h2 id="new-project-title">New project</h2>
        <label className="field">
          <span>Project name</span>
          <input
            ref={inputRef}
            data-testid="new-project-name"
            value={name}
            maxLength={200}
            onChange={(e) => setName(e.target.value)}
            autoFocus
          />
        </label>
        <p className="hint">Starts with a 1080×1920 composition at 60 fps, 30 seconds long.</p>
        <div className="modal-actions">
          <button type="button" className="btn" onClick={onCancel}>
            Cancel
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            data-testid="new-project-create"
            disabled={!name.trim()}
          >
            Create
          </button>
        </div>
      </form>
    </div>
  )
}
