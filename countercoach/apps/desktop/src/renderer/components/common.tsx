import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { initials } from "../format";

/**
 * Game icons are loaded at runtime from the community asset CDN (not bundled, since Valve
 * assets are not ours to redistribute). If an image fails or is offline, initials are shown.
 */
export function Icon({ src, name, size = 36, tone = "neutral", round = false }: { src: string | null | undefined; name: string; size?: number; tone?: "weapon" | "vitality" | "spirit" | "neutral" | "hero"; round?: boolean }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  const style = { width: size, height: size };
  if (!src || failed) {
    return (
      <span className={`icon icon-fallback tone-${tone} ${round ? "round" : ""}`} style={style} aria-label={name} role="img">
        {initials(name)}
      </span>
    );
  }
  return <img className={`icon tone-${tone} ${round ? "round" : ""}`} src={src} alt={name} style={style} loading="lazy" referrerPolicy="no-referrer" onError={() => setFailed(true)} draggable={false} />;
}

export function Badge({ children, tone = "neutral", title }: { children: ReactNode; tone?: "neutral" | "good" | "warn" | "bad" | "accent" | "spirit" | "weapon"; title?: string }) {
  return (
    <span className={`badge badge-${tone}`} title={title}>
      {children}
    </span>
  );
}

export function Section({ title, children, right, className = "" }: { title: string; children: ReactNode; right?: ReactNode; className?: string }) {
  return (
    <section className={`section ${className}`} aria-label={title}>
      <header className="section-head">
        <h2>{title}</h2>
        {right}
      </header>
      <div className="section-body">{children}</div>
    </section>
  );
}

export interface PickOption {
  id: string;
  label: string;
  sub?: string;
  image?: string | null;
  tone?: "weapon" | "vitality" | "spirit" | "neutral" | "hero";
  disabled?: boolean;
}

/**
 * Searchable picker with keyboard navigation: type to filter, ↑/↓ to move, Enter to pick,
 * Esc to clear. Recent picks are shown first when the query is empty.
 */
export function SearchPicker({
  options,
  onPick,
  placeholder,
  recent = [],
  inputId,
  grid = false,
  max = 40,
}: {
  options: PickOption[];
  onPick: (id: string) => void;
  placeholder: string;
  recent?: string[];
  inputId?: string;
  grid?: boolean;
  max?: number;
}) {
  const [q, setQ] = useState("");
  const [cursor, setCursor] = useState(0);
  const listRef = useRef<HTMLUListElement>(null);
  const filtered = useMemo(() => {
    const n = q.trim().toLowerCase();
    let list = options.filter((o) => !o.disabled);
    if (!n) {
      const rec = recent.map((r) => list.find((o) => o.id === r)).filter((x): x is PickOption => !!x);
      list = [...rec, ...list.filter((o) => !recent.includes(o.id))];
    } else {
      list = list
        .map((o) => ({ o, i: o.label.toLowerCase().indexOf(n) }))
        .filter((x) => x.i >= 0 || (x.o.sub ?? "").toLowerCase().includes(n))
        .sort((a, b) => (a.i < 0 ? 99 : a.i) - (b.i < 0 ? 99 : b.i) || a.o.label.localeCompare(b.o.label))
        .map((x) => x.o);
    }
    return list.slice(0, max);
  }, [q, options, recent, max]);
  useEffect(() => setCursor(0), [q]);
  const pick = (id: string) => {
    onPick(id);
    setQ("");
  };
  return (
    <div className="picker">
      <input
        id={inputId}
        className="input"
        value={q}
        placeholder={placeholder}
        aria-label={placeholder}
        onChange={(e) => setQ(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") {
            e.preventDefault();
            setCursor((c) => Math.min(filtered.length - 1, c + 1));
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setCursor((c) => Math.max(0, c - 1));
          } else if (e.key === "Enter") {
            e.preventDefault();
            const o = filtered[cursor];
            if (o) pick(o.id);
          } else if (e.key === "Escape") setQ("");
        }}
      />
      <ul ref={listRef} className={grid ? "pick-grid" : "pick-list"} role="listbox">
        {filtered.map((o, i) => (
          <li key={o.id} role="option" aria-selected={i === cursor}>
            <button type="button" className={`pick ${i === cursor ? "active" : ""}`} onClick={() => pick(o.id)} title={o.sub ? `${o.label} — ${o.sub}` : o.label}>
              <Icon src={o.image} name={o.label} size={grid ? 40 : 26} tone={o.tone ?? "neutral"} round={o.tone === "hero"} />
              <span className="pick-label">{o.label}</span>
              {o.sub && !grid && <span className="pick-sub">{o.sub}</span>}
            </button>
          </li>
        ))}
        {!filtered.length && <li className="muted small">No matches</li>}
      </ul>
    </div>
  );
}

export function Chip({ label, image, onRemove, tone = "neutral", round = false, extra }: { label: string; image?: string | null; onRemove?: () => void; tone?: "weapon" | "vitality" | "spirit" | "neutral" | "hero"; round?: boolean; extra?: ReactNode }) {
  return (
    <span className="chip">
      <Icon src={image} name={label} size={22} tone={tone} round={round} />
      <span>{label}</span>
      {extra}
      {onRemove && (
        <button type="button" className="chip-x" onClick={onRemove} aria-label={`Remove ${label}`}>
          ×
        </button>
      )}
    </span>
  );
}
