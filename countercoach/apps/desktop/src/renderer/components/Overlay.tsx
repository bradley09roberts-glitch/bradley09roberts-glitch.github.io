import { useEffect, useRef, useState } from "react";
import type { OverlayModel } from "../../shared/ipc";
import { EVIDENCE_LABEL, INFO_LABEL, fmtSouls } from "../format";
import { getHost, type OverlayStateMsg } from "../host";
import { Icon } from "./common";

/**
 * Overlay card. Collapsed: next purchase, souls remaining, next skill action, one reason and the
 * information state. Expanded: the three purchase choices and threats.
 */
export function OverlayCard({ m, expanded, editMode = false, scale = 1 }: { m: OverlayModel | null; expanded: boolean; editMode?: boolean; scale?: number }) {
  if (!m) {
    return (
      <div className={`ov ${editMode ? "ov-edit" : ""}`} style={{ fontSize: `${13 * scale}px` }}>
        <div className="ov-top">
          <span className="ov-brand">CounterCoach</span>
        </div>
        <div className="ov-muted">Open the CounterCoach window to set up your match.</div>
      </div>
    );
  }
  const p = m.primary;
  return (
    <div className={`ov ${expanded ? "ov-expanded" : ""} ${editMode ? "ov-edit" : ""}`} style={{ fontSize: `${13 * scale}px` }}>
      <div className="ov-top">
        <span className="ov-brand">CounterCoach</span>
        <span className={`ov-state ov-state-${m.infoState}`}>{INFO_LABEL[m.infoState]}</span>
        <span className={`ov-conf ov-conf-${m.confidence}`}>{m.confidence}</span>
      </div>
      {p ? (
        <div className="ov-primary">
          <Icon src={p.image} name={p.name} size={Math.round(40 * scale)} />
          <div className="ov-primary-text">
            <div className="ov-label">{p.label}</div>
            <div className="ov-name">{p.name}</div>
            <div className="ov-souls">{p.soulsShort > 0 ? `${fmtSouls(p.soulsShort)} souls to go` : `${fmtSouls(p.remainingCost)} souls`}</div>
          </div>
        </div>
      ) : (
        <div className="ov-name">{m.decision === "save" ? "Save souls" : "No purchase"}</div>
      )}
      {m.ability && <div className="ov-ability">⬆ {m.ability}</div>}
      <div className="ov-reason">{m.reason}</div>
      {m.screenNote && <div className="ov-screen">◉ {m.screenNote}</div>}
      {expanded && (
        <div className="ov-more">
          {m.saveFor && (
            <div>
              <span className="ov-label">SAVE FOR</span> {m.saveFor.name} {m.saveFor.soulsShort > 0 && <span className="ov-muted">({fmtSouls(m.saveFor.soulsShort)} to go)</span>}
            </div>
          )}
          {m.alternative && (
            <div>
              <span className="ov-label">ALT</span> {m.alternative.name} <div className="ov-muted">{m.alternative.condition}</div>
            </div>
          )}
          {m.threats.length > 0 && (
            <div className="ov-threats">
              {m.threats.map((t) => (
                <span key={t.label} className={`ov-threat ${t.urgent ? "urgent" : ""}`}>
                  {t.label} <span className="ov-muted">({EVIDENCE_LABEL[t.evidence]})</span>
                </span>
              ))}
            </div>
          )}
          {m.dataNote && <div className="ov-warn">{m.dataNote}</div>}
        </div>
      )}
      {editMode && <div className="ov-edit-note">Edit mode: drag to move · Ctrl+Alt+M to lock</div>}
    </div>
  );
}

/** Root for the overlay window. */
export function OverlayApp() {
  const host = getHost();
  const [m, setM] = useState<OverlayModel | null>(null);
  const [st, setSt] = useState<OverlayStateMsg>({ editMode: false, expanded: false, scale: 1, clickThrough: true });
  useEffect(() => host.onOverlayModel(setM), [host]);
  useEffect(() => host.onOverlayState(setSt), [host]);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    document.body.classList.add("overlay-body");
  }, []);
  // Report rendered height so the window hugs its content (no invisible dead area).
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(() => host.overlayResize(el.getBoundingClientRect().height));
    ro.observe(el);
    return () => ro.disconnect();
  }, [host]);
  return (
    <div ref={ref} className={`overlay-root ${st.editMode ? "draggable" : ""}`}>
      <OverlayCard m={m} expanded={st.expanded} editMode={st.editMode} scale={st.scale} />
    </div>
  );
}
