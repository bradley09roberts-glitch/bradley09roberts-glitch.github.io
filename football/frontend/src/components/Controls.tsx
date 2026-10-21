// Form controls shared by the Lab and Data pages: tabs, a labelled select and a segmented
// button group. Plain HTML controls; styles in controls.css use the variables in styles.css.

import { useRef, type KeyboardEvent, type ReactNode } from "react";
import "./controls.css";

export type TabDef<T extends string> = { id: T; label: string; hint?: string };

/**
 * An ARIA tab list. Arrow keys, Home and End move between tabs (and select them), as the
 * WAI-ARIA tabs pattern describes. The panel is rendered by the caller with
 * `id={tabPanelId(idBase)}` and `aria-labelledby={tabId(idBase, active)}`.
 */
export function Tabs<T extends string>({
  tabs,
  active,
  onChange,
  idBase,
  label,
}: {
  tabs: readonly TabDef<T>[];
  active: T;
  onChange: (id: T) => void;
  idBase: string;
  label: string;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>, index: number) => {
    let next = -1;
    if (e.key === "ArrowRight") next = (index + 1) % tabs.length;
    else if (e.key === "ArrowLeft") next = (index - 1 + tabs.length) % tabs.length;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = tabs.length - 1;
    if (next < 0) return;
    e.preventDefault();
    const tab = tabs[next];
    if (!tab) return;
    refs.current[next]?.focus();
    onChange(tab.id);
  };
  return (
    <div className="ctl-tabs" role="tablist" aria-label={label}>
      {tabs.map((t, i) => {
        const selected = t.id === active;
        return (
          <button
            key={t.id}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="tab"
            id={tabId(idBase, t.id)}
            aria-selected={selected}
            aria-controls={tabPanelId(idBase)}
            tabIndex={selected ? 0 : -1}
            className="ctl-tab"
            onClick={() => onChange(t.id)}
            onKeyDown={(e) => onKeyDown(e, i)}
          >
            <span className="ctl-tab-label">{t.label}</span>
            {t.hint && <span className="ctl-tab-hint">{t.hint}</span>}
          </button>
        );
      })}
    </div>
  );
}

export function tabId(idBase: string, id: string): string {
  return `${idBase}-tab-${id}`;
}

export function tabPanelId(idBase: string): string {
  return `${idBase}-panel`;
}

export type SelectOption = { value: string; label: string; disabled?: boolean };
export type SelectGroup = { label: string; options: SelectOption[] };

/** A labelled <select>. Options may be grouped (rendered as <optgroup>). */
export function SelectField({
  id,
  label,
  value,
  onChange,
  options,
  groups,
  disabled,
  wide = false,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  options?: SelectOption[];
  groups?: SelectGroup[];
  disabled?: boolean;
  /** Take the whole row (used for long option labels). */
  wide?: boolean;
}) {
  return (
    <div className={`ctl-field${wide ? " ctl-wide" : ""}`}>
      <label htmlFor={id} className="ctl-label">
        {label}
      </label>
      <select
        id={id}
        className="ctl-select"
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
      >
        {options?.map((o) => (
          <option key={o.value} value={o.value} disabled={o.disabled}>
            {o.label}
          </option>
        ))}
        {groups?.map((g) =>
          g.options.length ? (
            <optgroup key={g.label} label={g.label}>
              {g.options.map((o) => (
                <option key={o.value} value={o.value} disabled={o.disabled}>
                  {o.label}
                </option>
              ))}
            </optgroup>
          ) : null,
        )}
      </select>
    </div>
  );
}

/** A row of toggle buttons (one pressed). Uses the existing .segmented style. */
export function Segmented<T extends string | number>({
  label,
  value,
  options,
  onChange,
  showLabel = true,
}: {
  label: string;
  value: T;
  options: readonly { value: T; label: ReactNode }[];
  onChange: (value: T) => void;
  showLabel?: boolean;
}) {
  return (
    <div className="ctl-field ctl-seg" role="group" aria-label={label}>
      {showLabel && (
        <span className="ctl-label" aria-hidden="true">
          {label}
        </span>
      )}
      <div className="segmented segmented-scroll">
        {options.map((o) => (
          <button
            key={String(o.value)}
            type="button"
            aria-pressed={o.value === value}
            onClick={() => onChange(o.value)}
          >
            {o.label}
          </button>
        ))}
      </div>
    </div>
  );
}

/** A table that scrolls sideways inside its own box on narrow screens, never the page. */
export function TableScroll({
  label,
  children,
  className,
}: {
  /** Accessible name for the scrolling region. */
  label: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    // A focusable region so keyboard users can scroll it (axe: scrollable-region-focusable).
    <div className={`table-scroll${className ? ` ${className}` : ""}`} role="region" aria-label={label} tabIndex={0}>
      {children}
    </div>
  );
}
