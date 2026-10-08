import { useEffect, useMemo, useState } from "react";
import {
  ADAPTERS,
  applyEvents,
  createMatchState,
  parseScenario,
  postMatchReview,
  readField,
  scenarioEvents,
  whatIf,
  type DecisionLog,
  type EnemyItemsValue,
  type ScenarioFixture,
  type WhatIfChange,
} from "@countercoach/engine";
import type { Coach } from "../useCoach";
import { acceleratorSchema, ANCHORS, type Anchor } from "../../shared/settings";
import { fmtTime } from "../format";
import { Badge, SearchPicker, Section } from "./common";
import { itemOptions } from "./Setup";

// ---------------------------------------------------------------------------------------------
// What-if
// ---------------------------------------------------------------------------------------------
export function WhatIfPanel({ c }: { c: Coach }) {
  const enemies = (readField(c.state, "roster.enemies", c.now)?.value as number[] | undefined) ?? [];
  const [enemy, setEnemy] = useState<number | null>(enemies[0] ?? null);
  const [souls, setSouls] = useState("");
  const [change, setChange] = useState<WhatIfChange | null>(null);
  const heroId = c.output.rec.heroId;
  const profile = heroId != null ? c.deps.profiles.get(heroId) : null;
  const result = useMemo(() => (change ? whatIf(c.deps, c.state, c.prefs, c.now, change) : null), [change, c.deps, c.state, c.prefs, c.now]);
  const options = useMemo(() => itemOptions(c.deps.data.items()), [c.deps]);
  if (heroId == null) return <p className="muted">Pick your hero first.</p>;
  const current = enemy != null ? ((readField(c.state, `enemyItems:${enemy}`, c.now)?.value as EnemyItemsValue | undefined)?.items ?? []) : [];
  return (
    <div className="grid2">
      <Section title="Change one thing">
        <div className="field">
          <span>Enemy item</span>
          <div className="row gap">
            <select className="input" value={enemy ?? ""} onChange={(e) => setEnemy(e.target.value ? Number(e.target.value) : null)}>
              {enemies.map((id) => (
                <option key={id} value={id}>
                  {c.deps.data.hero(id)?.name}
                </option>
              ))}
            </select>
          </div>
          {enemy != null && (
            <SearchPicker
              options={options}
              placeholder="What if they had…"
              max={10}
              onPick={(cn) => setChange({ kind: "enemyItem", heroId: enemy, className: cn, action: current.includes(cn) ? "remove" : "add" })}
            />
          )}
        </div>
        <div className="field">
          <span>My souls</span>
          <div className="row gap">
            <input className="input" value={souls} inputMode="numeric" onChange={(e) => setSouls(e.target.value.replace(/\D/g, ""))} placeholder="e.g. 6400" />
            <button type="button" className="btn small" disabled={!souls} onClick={() => setChange({ kind: "souls", value: Number(souls) })}>
              Try
            </button>
          </div>
        </div>
        {profile && profile.archetypes.length > 1 && (
          <div className="field">
            <span>Build</span>
            <div className="row gap wrap">
              {profile.archetypes.map((a) => (
                <button key={a.id} type="button" className="btn ghost small" onClick={() => setChange({ kind: "archetype", archetypeId: a.id })}>
                  {a.label}
                </button>
              ))}
            </div>
          </div>
        )}
        <div className="field">
          <span>Route</span>
          <div className="row gap">
            {(["stabilise", "normal", "ambitious"] as const).map((r) => (
              <button key={r} type="button" className="btn ghost small" onClick={() => setChange({ kind: "route", route: r })}>
                {r}
              </button>
            ))}
          </div>
        </div>
        <p className="muted small">What-if never changes your real match state.</p>
      </Section>
      <Section title="Result">
        {!result && <p className="muted">Choose a change on the left.</p>}
        {result && (
          <div>
            <div className="row gap wrap">
              <div className="card">
                <div className="action-label">Now</div>
                <div>{result.before.summary}</div>
              </div>
              <div className="card">
                <div className="action-label">What-if</div>
                <div>{result.after.summary}</div>
              </div>
            </div>
            <ul className="small">
              {result.explanation.map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </ul>
          </div>
        )}
      </Section>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Post-match review
// ---------------------------------------------------------------------------------------------
export function ReviewPanel({ c }: { c: Coach }) {
  const [logs, setLogs] = useState<{ matchId: string; entries: number; heroId: number | null }[]>([]);
  const [log, setLog] = useState<DecisionLog | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const refresh = () => void c.host.logList().then(setLogs);
  useEffect(refresh, [c.host]);
  const review = useMemo(() => (log ? postMatchReview(log) : null), [log]);
  return (
    <div className="grid2">
      <Section
        title="Decision logs"
        right={
          <span className="row gap">
            <Badge tone={c.settings.logging.enabled ? "good" : "neutral"}>{c.settings.logging.enabled ? "logging on" : "logging off"}</Badge>
          </span>
        }
      >
        <p className="small muted">Logs are opt-in, stay on this computer, and record only what was known when advice was given.</p>
        <div className="row gap wrap">
          <button type="button" className="btn small" onClick={() => void c.updateSettings({ logging: { enabled: !c.settings.logging.enabled } })}>
            {c.settings.logging.enabled ? "Turn logging off" : "Turn logging on"}
          </button>
          <button
            type="button"
            className="btn ghost small"
            onClick={async () => {
              const r = await c.host.importReplay();
              if (r?.log) setLog(r.log);
              else if (r?.error) setMsg(r.error);
            }}
          >
            Import log…
          </button>
          <button
            type="button"
            className="btn danger small"
            onClick={async () => {
              await c.host.logDeleteAll();
              setLog(null);
              refresh();
            }}
          >
            Delete all logs
          </button>
        </div>
        {msg && <p className="flag small">{msg}</p>}
        <ul className="loglist">
          {logs.map((l) => (
            <li key={l.matchId}>
              <button type="button" className="linkish" onClick={() => void c.host.logRead(l.matchId).then(setLog)}>
                {l.matchId}
              </button>{" "}
              <span className="muted small">
                {l.heroId != null ? c.deps.data.hero(l.heroId)?.name : "?"} · {l.entries} decisions
              </span>
            </li>
          ))}
          {!logs.length && <li className="muted small">No logs yet.</li>}
        </ul>
      </Section>
      <Section title="Review (max 3 lessons)">
        {!review && <p className="muted">Open a log to review it.</p>}
        {review && (
          <>
            {!review.lessons.length && <p className="muted">No clear lessons from this log.</p>}
            <ol>
              {review.lessons.map((l, i) => (
                <li key={i}>
                  <Badge>{l.category}</Badge> {l.text}
                  <div className="muted small">{l.evidence}</div>
                </li>
              ))}
            </ol>
            <p className="muted small">{review.caveat}</p>
            <details className="small">
              <summary>Decisions in this log</summary>
              <ul>
                {log!.entries.slice(-30).map((e, i) => (
                  <li key={i}>
                    {fmtTime(e.gameTime)} · buy {e.advice.buyNow ? (c.deps.data.item(e.advice.buyNow)?.name ?? e.advice.buyNow) : "—"} · save {e.advice.saveFor ? (c.deps.data.item(e.advice.saveFor)?.name ?? e.advice.saveFor) : "—"} · {e.informationState}
                  </li>
                ))}
              </ul>
            </details>
          </>
        )}
      </Section>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Scenario / demo playback
// ---------------------------------------------------------------------------------------------
export function ScenarioPanel({ c, fixtures }: { c: Coach; fixtures: unknown[] }) {
  const parsed = useMemo(() => fixtures.map((f) => parseScenario(f)).filter((p): p is { ok: true; scenario: ScenarioFixture } => p.ok).map((p) => p.scenario), [fixtures]);
  const [sel, setSel] = useState<ScenarioFixture | null>(null);
  const [pos, setPos] = useState(0);
  const load = (s: ScenarioFixture, upto: number) => {
    const base = Date.now() - (s.events[Math.max(0, upto - 1)]?.t ?? 0) - 500;
    const evs = scenarioEvents(s, base).slice(0, upto);
    c.replaceState(applyEvents(createMatchState("live"), evs));
    setPos(upto);
  };
  return (
    <div className="grid2">
      <Section title="Scenario fixtures">
        <p className="small muted">Scenarios replace your current match with recorded, timestamped inputs. Advice is labelled “Scenario”.</p>
        <ul className="scenario-list">
          {parsed.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                className={`linkish ${sel?.id === s.id ? "strong" : ""}`}
                onClick={() => {
                  setSel(s);
                  load(s, s.events.length);
                }}
              >
                {s.title}
              </button>
              <div className="muted small">{s.description}</div>
            </li>
          ))}
        </ul>
      </Section>
      <Section title="Playback">
        {!sel && <p className="muted">Choose a scenario.</p>}
        {sel && (
          <>
            <div className="row gap">
              <button type="button" className="btn small" onClick={() => load(sel, 1)}>
                ⏮ Start
              </button>
              <button type="button" className="btn small" disabled={pos >= sel.events.length} onClick={() => load(sel, pos + 1)}>
                Step ▶
              </button>
              <button type="button" className="btn small" onClick={() => load(sel, sel.events.length)}>
                End ⏭
              </button>
              <span className="muted small">
                event {pos}/{sel.events.length}
              </span>
            </div>
            <ul className="small">
              {sel.expectations.map((e, i) => (
                <li key={i}>Expected: {e}</li>
              ))}
            </ul>
          </>
        )}
      </Section>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Settings + data status
// ---------------------------------------------------------------------------------------------
export function SettingsPanel({ c }: { c: Coach }) {
  const s = c.settings;
  const [hk, setHk] = useState(s.hotkeys);
  const [hkError, setHkError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const isElectron = c.host.kind === "electron";
  const snap = c.dataBoot.snapshot.meta;
  const r = c.deps.rules;
  return (
    <div className="grid2">
      <Section title="Overlay">
        {!isElectron && <p className="muted small">Web preview: the overlay runs as a separate always-on-top window in the desktop app.</p>}
        <label className="check">
          <input type="checkbox" checked={s.overlay.enabled} onChange={(e) => void c.updateSettings({ overlay: { enabled: e.target.checked } })} /> Show overlay
        </label>
        <label className="check">
          <input type="checkbox" checked={s.overlay.expanded} onChange={(e) => void c.updateSettings({ overlay: { expanded: e.target.checked } })} /> Expanded
        </label>
        <label className="check">
          <input type="checkbox" checked={s.overlay.clickThrough} onChange={(e) => void c.updateSettings({ overlay: { clickThrough: e.target.checked } })} /> Click-through while playing
        </label>
        <label className="field">
          <span>Scale {s.overlay.scale.toFixed(2)}×</span>
          <input type="range" min={0.6} max={2} step={0.05} value={s.overlay.scale} onChange={(e) => void c.updateSettings({ overlay: { scale: Number(e.target.value) } })} />
        </label>
        <label className="field">
          <span>Opacity {Math.round(s.overlay.opacity * 100)}%</span>
          <input type="range" min={0.3} max={1} step={0.02} value={s.overlay.opacity} onChange={(e) => void c.updateSettings({ overlay: { opacity: Number(e.target.value) } })} />
        </label>
        <label className="field">
          <span>Position</span>
          <select className="input" value={s.overlay.anchor} onChange={(e) => void c.updateSettings({ overlay: { anchor: e.target.value as Anchor } })}>
            {ANCHORS.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </select>
        </label>
        {isElectron && (
          <div className="row gap">
            <button type="button" className="btn small" onClick={() => void c.host.setEditMode(true)}>
              Move overlay (edit mode)
            </button>
            <button type="button" className="btn ghost small" onClick={() => void c.host.setEditMode(false)}>
              Lock
            </button>
          </div>
        )}
        <p className="muted small">Use Borderless/Windowed display mode in Deadlock. Overlays cannot draw over exclusive fullscreen. Not yet tested with the real game.</p>
      </Section>
      <Section title="Hotkeys">
        {(Object.keys(hk) as (keyof typeof hk)[]).map((k) => (
          <label key={k} className="field">
            <span>{k.replace(/([A-Z])/g, " $1").toLowerCase()}</span>
            <input className="input" value={hk[k]} onChange={(e) => setHk({ ...hk, [k]: e.target.value })} />
          </label>
        ))}
        <button
          type="button"
          className="btn small"
          onClick={() => {
            const bad = Object.values(hk).find((v) => !acceleratorSchema.safeParse(v).success);
            if (bad) return setHkError(`"${bad}" is not a valid shortcut (e.g. Ctrl+Alt+O)`);
            setHkError(null);
            void c.updateSettings({ hotkeys: hk });
          }}
        >
          Save hotkeys
        </button>
        {hkError && <p className="flag small">{hkError}</p>}
        <p className="muted small">Global shortcuts do not take keyboard focus from the game.</p>
      </Section>
      <Section title="Coach preferences">
        <label className="field">
          <span>Item complexity</span>
          <select className="input" value={s.coach.difficulty} onChange={(e) => void c.updateSettings({ coach: { difficulty: e.target.value } })}>
            <option value="simple">Simple (≤2 actives)</option>
            <option value="standard">Standard (≤4 actives)</option>
            <option value="complex">Complex (≤6 actives)</option>
          </select>
        </label>
        <label className="field">
          <span>Route</span>
          <select className="input" value={s.coach.route} onChange={(e) => void c.updateSettings({ coach: { route: e.target.value } })}>
            <option value="auto">Auto (from ahead/behind)</option>
            <option value="stabilise">Stabilise (cheap, durable)</option>
            <option value="normal">Normal</option>
            <option value="ambitious">Ambitious</option>
          </select>
        </label>
        {c.output.rec.heroId != null && <LockedCore c={c} />}
      </Section>
      <Section title="Game rules (unverified values)">
        <p className="small muted">These rules are not in the game data. Change them if your in-game shop shows otherwise.</p>
        <label className="field">
          <span>Item slots ({r.inventory.provenance.kind})</span>
          <input className="input narrow" inputMode="numeric" defaultValue={s.rules.totalSlots ?? r.inventory.value.totalSlots} onBlur={(e) => void c.updateSettings({ rules: { totalSlots: Number(e.target.value) || null } })} />
        </label>
        <label className="field">
          <span>Sell-back fraction ({r.sellBack.provenance.kind})</span>
          <input className="input narrow" inputMode="decimal" defaultValue={s.rules.sellFraction ?? r.sellBack.value.fraction} onBlur={(e) => void c.updateSettings({ rules: { sellFraction: e.target.value === "" ? null : Number(e.target.value) } })} />
        </label>
        <p className="tiny muted">{r.inventory.provenance.interpretation}</p>
      </Section>
      <Section title="Game data">
        <ul className="small">
          <li>
            Client build <b>{snap.clientVersion}</b> ({snap.versionDate}) · retrieved {snap.retrievedAt.slice(0, 16).replace("T", " ")}
          </li>
          <li>
            Source: {c.dataBoot.source} · <code>{snap.contentHash.slice(0, 12)}</code>
          </li>
          <li>
            {snap.counts.playableHeroes} playable heroes, {snap.counts.shopItems} shop items · tier costs {c.dataBoot.snapshot.abilityCosts.tierCosts.join("/")} (derived, {c.dataBoot.snapshot.abilityCosts.provenance.confidence})
          </li>
          <li>Latest patch note: {snap.latestPatch?.title ?? "?"}</li>
          <li>
            Compatibility: <Badge tone={c.deps.compat.status === "current" ? "good" : c.deps.compat.status === "outdated" ? "bad" : "neutral"}>{c.deps.compat.status}</Badge> {c.deps.compat.message}
          </li>
        </ul>
        {c.dataEvent && <p className="small">{c.dataEvent.message}</p>}
        <div className="row gap">
          <button
            type="button"
            className="btn small"
            disabled={busy || !isElectron}
            onClick={async () => {
              setBusy(true);
              c.setDataEvent(await c.host.checkData());
              setBusy(false);
            }}
          >
            Check for new build
          </button>
          <button
            type="button"
            className="btn small"
            disabled={busy || !isElectron}
            onClick={async () => {
              setBusy(true);
              const r2 = await c.host.refreshData();
              c.setDataEvent(r2.event);
              if (r2.bootstrap) c.applyRefreshedData(r2.bootstrap);
              setBusy(false);
            }}
          >
            Download &amp; validate data
          </button>
        </div>
        <p className="tiny muted">Data: deadlock-api.com community API (not Valve). Downloads are validated and quality-checked; the previous snapshot is kept.</p>
      </Section>
      <Section title="Inputs available">
        <ul className="small">
          {ADAPTERS.map((a) => (
            <li key={a.id}>
              <b>{a.label}</b> <Badge tone={a.verified ? "good" : "neutral"}>{a.verified ? "available" : "not available"}</Badge> — {a.note}
            </li>
          ))}
        </ul>
      </Section>
    </div>
  );
}

function LockedCore({ c }: { c: Coach }) {
  const heroId = String(c.output.rec.heroId);
  const locked = c.settings.coach.lockedCoreByHero[heroId] ?? [];
  const options = useMemo(() => itemOptions(c.deps.data.items()), [c.deps]);
  const set = (list: string[]) => void c.updateSettings({ coach: { lockedCoreByHero: { ...c.settings.coach.lockedCoreByHero, [heroId]: list } } });
  return (
    <div className="field">
      <span>Locked core items (never displaced)</span>
      <div className="chips">
        {locked.map((cn) => (
          <span key={cn} className="chip">
            {c.deps.data.item(cn)?.name ?? cn}{" "}
            <button type="button" className="chip-x" onClick={() => set(locked.filter((x) => x !== cn))} aria-label="Remove">
              ×
            </button>
          </span>
        ))}
      </div>
      {locked.length < 12 && <SearchPicker options={options.filter((o) => !locked.includes(o.id))} placeholder="Lock a core item" max={8} onPick={(cn) => set([...locked, cn])} />}
    </div>
  );
}
