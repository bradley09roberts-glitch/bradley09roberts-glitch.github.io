/** Kinetik monogram: a slanted "k" with a motion streak. Original artwork (SPEC §14). */
export function LogoMark({ size = 28 }: { size?: number }): React.JSX.Element {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" className="logo-mark">
      <rect width="32" height="32" rx="8" fill="var(--surface-3)" />
      <path d="M12.5 6.5 9 25.5" stroke="var(--accent)" strokeWidth="3.4" strokeLinecap="round" />
      <path
        d="M24 8.5 12.8 17l6.4 8.5"
        fill="none"
        stroke="var(--accent)"
        strokeWidth="3.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M4 20.5h3.2" stroke="var(--beat)" strokeWidth="2" strokeLinecap="round" />
      <path
        d="M3 24.5h4"
        stroke="var(--beat)"
        strokeWidth="2"
        strokeLinecap="round"
        opacity="0.6"
      />
    </svg>
  )
}

export function Wordmark(): React.JSX.Element {
  return (
    <span className="wordmark">
      <LogoMark />
      <span className="wordmark-text">kinetik</span>
    </span>
  )
}
