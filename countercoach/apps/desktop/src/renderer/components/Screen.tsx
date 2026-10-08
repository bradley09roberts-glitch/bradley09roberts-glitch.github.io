import { useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import {
  buildSlotGrid,
  calibrationBadges,
  layoutFromCalibration,
  mergeSlotGrids,
  readField,
  readScreen,
  toPixels,
  type Rect,
  type RowTarget,
  type ScreenLayout,
} from "@countercoach/engine";
import type { SavedCaptures, TabWatcherStatus } from "../../shared/capture";
import type { Coach } from "../useCoach";
import { toImage, type ReaderSession, type ScreenReader } from "../useScreenReader";
import { Badge, Icon, Section } from "./common";

/**
 * Screen tab: read item icons from a capture of a screen the player can already see (the Tab
 * scoreboard), after a one-time calibration on the user's own screenshot.
 */

const SLOT_TONE = { weapon: "weapon", vitality: "vitality", spirit: "spirit" } as const;

type DrawnBox = { rect: Rect; label: string; tone: "area" | "icon" | "portrait" | "good" | "warn" | "slot" };

/** Calibrated slots drawn faintly over a capture, so you can see what the reader checks. */
function slotBoxes(layout: ScreenLayout | null, img: { width: number; height: number }): DrawnBox[] {
  return (layout?.slots?.cards ?? []).flatMap((c) => c.slots.map((s) => ({ rect: toPixels(s, img), label: "", tone: "slot" as const })));
}

/** An image with rectangles; optionally lets the user drag out a new one (image pixels). */
function ImageBoxes({ src, width, height, boxes, onDraw }: { src: string; width: number; height: number; boxes: DrawnBox[]; onDraw?: (r: Rect) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const [drag, setDrag] = useState<{ x0: number; y0: number; x1: number; y1: number } | null>(null);
  const toImg = (e: ReactPointerEvent) => {
    const b = ref.current!.getBoundingClientRect();
    return { x: ((e.clientX - b.left) / b.width) * width, y: ((e.clientY - b.top) / b.height) * height };
  };
  const pct = (r: Rect) => ({ left: `${(r.x / width) * 100}%`, top: `${(r.y / height) * 100}%`, width: `${(r.w / width) * 100}%`, height: `${(r.h / height) * 100}%` });
  const live = drag ? { x: Math.min(drag.x0, drag.x1), y: Math.min(drag.y0, drag.y1), w: Math.abs(drag.x1 - drag.x0), h: Math.abs(drag.y1 - drag.y0) } : null;
  return (
    <div
      ref={ref}
      className={`shot ${onDraw ? "drawing" : ""}`}
      onPointerDown={(e) => {
        if (!onDraw) return;
        (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
        const p = toImg(e);
        setDrag({ x0: p.x, y0: p.y, x1: p.x, y1: p.y });
      }}
      onPointerMove={(e) => {
        if (!drag) return;
        const p = toImg(e);
        setDrag({ ...drag, x1: p.x, y1: p.y });
      }}
      onPointerUp={() => {
        if (live && live.w > 4 && live.h > 4) onDraw?.(live);
        setDrag(null);
      }}
    >
      <img src={src} alt="Captured screen" draggable={false} />
      {boxes.map((b, i) => (
        <div key={i} className={`shot-box box-${b.tone}`} style={pct(b.rect)}>
          {b.label && <span className="shot-label">{b.label}</span>}
        </div>
      ))}
      {live && <div className="shot-box box-live" style={pct(live)} />}
    </div>
  );
}

const STEPS = [
  {
    key: "area",
    title: "1. Item area",
    help: "Drag a box around the item icons only. In Deadlock's Tab view that is the strip of small icons under the player cards. Leave out portraits, names, stats, ability circles and the game world.",
  },
  { key: "icon", title: "2. One icon", help: "Drag a tight box around one item icon, edge to edge (its tier badge included)." },
  {
    key: "portrait",
    title: "3. Player card portrait (optional)",
    help: "Drag a box around the portrait of the same player (above the icon in the Tab view). Portraits only suggest who is who: skins can make them look different.",
  },
] as const;

/** Advice when the drawn item area is far bigger than the item icons need. */
function areaWarning(area: Rect | null, icon: Rect | null, width: number, height: number): string | null {
  if (!area || !icon) return null;
  const side = (icon.w + icon.h) / 2;
  if (area.h > side * 8 || (area.w * area.h) / (width * height) > 0.25) {
    return "This item area is much bigger than the item icons need. Big areas include scenery and HUD parts that can be mistaken for items; draw it tighter if you can.";
  }
  return null;
}

function Calibrate({ c, r, session, onDone }: { c: Coach; r: ScreenReader; session: ReaderSession; onDone: (layout: ScreenLayout | null) => void }) {
  const { width, height } = session.capture;
  const [area, setArea] = useState<Rect | null>(null);
  const [icon, setIcon] = useState<Rect | null>(null);
  const [portrait, setPortrait] = useState<Rect | null>(null);
  const step = !area ? 0 : !icon ? 1 : 2;
  const warning = areaWarning(area, icon, width, height);
  const save = async (withPortrait: boolean) => {
    if (!area || !icon) return;
    // Learn from the marked icon whether this screen draws tier badges, and their colour.
    const templates = await r.ensureTemplates();
    const img = toImage(session.capture);
    let layout = layoutFromCalibration(
      { width, height },
      area,
      icon,
      withPortrait && portrait ? { box: portrait } : null,
      new Date().toISOString(),
      templates ? calibrationBadges(img, icon, templates) : undefined,
    );
    // Learn every item slot from this capture; later reads check only those slots.
    if (templates) {
      const grid = buildSlotGrid(readScreen(img, layout, templates), layout);
      if (grid) layout = { ...layout, slots: grid };
    }
    await c.updateSettings({ screen: { layout } });
    onDone(layout);
  };
  const boxes: DrawnBox[] = [
    ...(area ? [{ rect: area, label: "items", tone: "area" as const }] : []),
    ...(icon ? [{ rect: icon, label: "icon", tone: "icon" as const }] : []),
    ...(portrait ? [{ rect: portrait, label: "portrait", tone: "portrait" as const }] : []),
  ];
  return (
    <Section title="Calibrate (once per resolution)" className="span2">
      <ol className="steps">
        {STEPS.map((s, i) => (
          <li key={s.key} className={i === step ? "on" : i < step ? "done" : ""}>
            <b>{s.title}</b> <span className="muted small">{s.help}</span>
          </li>
        ))}
      </ol>
      <ImageBoxes
        src={session.preview}
        width={width}
        height={height}
        boxes={boxes}
        onDraw={(rect) => (step === 0 ? setArea(rect) : step === 1 ? setIcon(rect) : setPortrait(rect))}
      />
      {warning && <p className="flag small">{warning}</p>}
      <div className="row gap">
        <button type="button" className="btn ghost small" onClick={() => (portrait ? setPortrait(null) : icon ? setIcon(null) : setArea(null))} disabled={!area}>
          Undo last box
        </button>
        {step === 2 && (
          <>
            <button type="button" className="btn small" onClick={() => void save(true)} disabled={!portrait}>
              Save calibration
            </button>
            <button type="button" className="btn ghost small" onClick={() => void save(false)}>
              Save without portraits
            </button>
          </>
        )}
        <button type="button" className="btn ghost small" onClick={() => onDone(null)}>
          Cancel
        </button>
      </div>
      <p className="muted small">
        Calibrated on a {width}×{height} capture. Positions are stored as fractions of the screen, so the same calibration works at any resolution with the same aspect ratio.
      </p>
    </Section>
  );
}

function targetValue(t: RowTarget | null): string {
  if (!t) return "";
  if (t.kind === "me" || t.kind === "ignore") return t.kind;
  return `${t.kind}:${t.heroId}`;
}

function parseTarget(v: string): RowTarget | null {
  if (v === "me" || v === "ignore") return { kind: v };
  const m = /^(enemy|ally):(\d+)$/.exec(v);
  return m ? { kind: m[1] as "enemy" | "ally", heroId: Number(m[2]) } : null;
}

function Review({ c, r }: { c: Coach; r: ScreenReader }) {
  const s = r.session!;
  const read = s.read!;
  const data = c.deps.data;
  const now = Date.now();
  const me = (readField(c.state, "me.hero", now)?.value as number | undefined) ?? null;
  const enemies = (readField(c.state, "roster.enemies", now)?.value as number[] | undefined) ?? [];
  const allies = (readField(c.state, "roster.allies", now)?.value as number[] | undefined) ?? [];
  const name = (id: number) => data.hero(id)?.name ?? `#${id}`;
  const boxes: DrawnBox[] = [
    ...slotBoxes(c.settings.screen.layout, s.capture),
    ...read.rows.flatMap((row) => [
    ...row.items.map((it) => ({ rect: it.box, label: "", tone: it.status === "confident" ? ("good" as const) : ("warn" as const) })),
    ...(row.hero?.status === "confident" ? [{ rect: row.hero.box, label: name(row.hero.candidates[0]!.heroId!), tone: "portrait" as const }] : []),
    ]),
  ];
  const applied = s.appliedAt != null;
  const inRoster = new Set([...(me != null ? [me] : []), ...enemies, ...allies]);
  const others = data
    .playableHeroes()
    .filter((h) => !inRoster.has(h.id))
    .sort((a, b) => a.name.localeCompare(b.name));
  /** Assign a row; picking a hero that is not in the roster yet adds them to it. */
  const choose = (row: number, value: string) => {
    const add = /^add-(enemy|ally):(\d+)$/.exec(value);
    if (add) {
      const id = Number(add[2]);
      if (add[1] === "enemy") c.send(c.input.enemies([...enemies, id]));
      else c.send(c.input.allies([...allies, id]));
      r.setRowTarget(row, { kind: add[1] as "enemy" | "ally", heroId: id });
      return;
    }
    r.setRowTarget(row, parseTarget(value));
  };
  const unit = read.orientation === "columns" ? "Player" : "Row";
  const unassigned = s.rows.filter((x) => !x.target).length;
  return (
    <>
      <Section
        title={`Read: ${read.rows.length} ${read.orientation === "columns" ? "players" : "rows"}`}
        right={<span className="muted small">{s.capture.source}</span>}
        className="span2"
      >
        {read.warnings.map((w) => (
          <p key={w} className="flag small">
            {w}
          </p>
        ))}
        <ImageBoxes src={s.preview} width={s.capture.width} height={s.capture.height} boxes={boxes} />
      </Section>
      <Section title="Who is who" className="span2">
        {s.rows.length === 0 && <p className="muted">No items were found in the calibrated area.</p>}
        <div className="screen-rows">
          {s.rows.map((row) => {
            const hero = row.heroGuess != null ? data.hero(row.heroGuess) : null;
            return (
              <div key={row.index} className={`screen-row ${row.target ? "" : "unassigned"}`}>
                <div className="screen-row-who">
                  {hero ? <Icon src={hero.image} name={hero.name} size={30} tone="hero" round /> : <span className="icon icon-fallback">?</span>}
                  <select
                    className="input"
                    value={targetValue(row.target)}
                    disabled={applied}
                    aria-label={`${unit} ${row.index + 1} belongs to`}
                    onChange={(e) => choose(row.index, e.target.value)}
                  >
                    <option value="">
                      {unit} {row.index + 1}: choose…
                    </option>
                    {me != null && <option value="me">Me ({name(me)})</option>}
                    {enemies.map((id) => (
                      <option key={`e${id}`} value={`enemy:${id}`}>
                        Enemy: {name(id)}
                      </option>
                    ))}
                    {allies.map((id) => (
                      <option key={`a${id}`} value={`ally:${id}`}>
                        Ally: {name(id)}
                      </option>
                    ))}
                    {others.length > 0 && enemies.length < 6 && (
                      <optgroup label="Add an enemy">
                        {others.map((h) => (
                          <option key={`ae${h.id}`} value={`add-enemy:${h.id}`}>
                            Enemy: {h.name}
                          </option>
                        ))}
                      </optgroup>
                    )}
                    {others.length > 0 && allies.length < 5 && (
                      <optgroup label="Add an ally">
                        {others.map((h) => (
                          <option key={`aa${h.id}`} value={`add-ally:${h.id}`}>
                            Ally: {h.name}
                          </option>
                        ))}
                      </optgroup>
                    )}
                    <option value="ignore">Ignore (not a player)</option>
                  </select>
                  {row.unknownHeroId != null && !applied && (
                    <span className="row gap-s">
                      <span className="muted small">{name(row.unknownHeroId)} isn't in your roster:</span>
                      <button
                        type="button"
                        className="btn tiny"
                        onClick={() => {
                          c.send(c.input.enemies([...enemies, row.unknownHeroId!]));
                          r.setRowTarget(row.index, { kind: "enemy", heroId: row.unknownHeroId! });
                        }}
                      >
                        add as enemy
                      </button>
                      <button
                        type="button"
                        className="btn tiny ghost"
                        onClick={() => {
                          c.send(c.input.allies([...allies, row.unknownHeroId!]));
                          r.setRowTarget(row.index, { kind: "ally", heroId: row.unknownHeroId! });
                        }}
                      >
                        add as ally
                      </button>
                    </span>
                  )}
                  <span className="muted tiny">
                    {row.reason}
                    {!row.target && row.heroHint != null ? ` · portrait looks a bit like ${name(row.heroHint)}` : ""}
                  </span>
                </div>
                <div className="screen-row-items">
                  {row.items.map((it, i) => {
                    const chosen = it.choice ? data.item(it.choice) : null;
                    const unsure = it.read.status !== "confident";
                    return (
                      <div key={i} className={`screen-item ${unsure ? "unsure" : ""} ${it.choice ? "" : "dropped"}`}>
                        <Icon src={chosen?.image} name={chosen?.name ?? "?"} size={28} tone={chosen ? SLOT_TONE[chosen.slot] : "neutral"} />
                        <span className="screen-item-name">{chosen?.name ?? (unsure ? "Which item?" : "dropped")}</span>
                        {unsure && <Badge tone="warn">{Math.round(it.read.candidates[0]!.score * 100)}%</Badge>}
                        {!applied && (
                          <span className="screen-item-alts">
                            {(unsure || !it.choice) &&
                              it.read.candidates.map((cand) => (
                                <button
                                  key={cand.className}
                                  type="button"
                                  className={`btn tiny ${it.choice === cand.className ? "" : "ghost"}`}
                                  onClick={() => r.setItemChoice(row.index, i, cand.className)}
                                  title={`match ${Math.round(cand.score * 100)}%`}
                                >
                                  {data.item(cand.className)?.name ?? cand.className}
                                </button>
                              ))}
                            {it.choice && (
                              <button type="button" className="btn tiny ghost" onClick={() => r.setItemChoice(row.index, i, null)} aria-label="Not an item">
                                ×
                              </button>
                            )}
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
        {!applied && s.rows.length > 0 && (
          <div className="row gap screen-actions">
            <label className="check">
              <input type="checkbox" checked={r.fullInventories} onChange={(e) => r.setFullInventories(e.target.checked)} /> This screen shows full inventories (scoreboard)
            </label>
            <button type="button" className="btn" onClick={r.confirm} disabled={s.rows.every((x) => !x.target)}>
              Apply{unassigned ? ` (${unassigned} row${unassigned > 1 ? "s" : ""} skipped)` : ""}
            </button>
            <button type="button" className="btn ghost" onClick={r.discard}>
              Discard
            </button>
          </div>
        )}
        {applied && (
          <p className="good small">
            Applied. Items now show as <b>seen on screen</b> in the Match tab; change anything there if it is wrong.
          </p>
        )}
      </Section>
    </>
  );
}

function TemplateStatus({ r }: { r: ScreenReader }) {
  const t = r.templates;
  if (t.status === "ready") return <p className="muted small">Icon templates ready ({t.detail}), built on this PC from the item art.</p>;
  if (t.status === "loading")
    return (
      <p className="small">
        Preparing icon templates: downloading the item art once from assets-bucket.deadlock-api.com{t.progress ? ` (${t.progress.done}/${t.progress.total})` : ""}…
      </p>
    );
  if (t.status === "error")
    return (
      <p className="flag small">
        Icon templates could not be prepared: {t.detail}{" "}
        <button type="button" className="btn tiny" onClick={() => void r.ensureTemplates()}>
          Try again
        </button>
      </p>
    );
  return <p className="muted small">Icon templates are built on first use (a one-time download of the item art).</p>;
}

export function ScreenPanel({ c, r }: { c: Coach; r: ScreenReader }) {
  const layout: ScreenLayout | null = c.settings.screen.layout;
  const [calibrating, setCalibrating] = useState(false);
  const [saved, setSaved] = useState<SavedCaptures | null>(null);
  const isElectron = c.host.kind === "electron";
  useEffect(() => {
    if (isElectron) void c.host.savedCaptures().then(setSaved);
  }, [c.host, isElectron, r.session]);
  useEffect(() => {
    if (r.status === "calibrate") setCalibrating(true);
  }, [r.status]);
  // Opening this tab is the cue to prepare the icon templates (a one-time download).
  useEffect(() => {
    if (r.templates.status === "idle") void r.ensureTemplates();
  }, [r.templates.status, r.ensureTemplates]);
  const [tab, setTab] = useState<TabWatcherStatus | null>(null);
  useEffect(() => {
    if (!isElectron) return;
    void c.host.tabStatus().then(setTab);
    return c.host.onTabStatus(setTab);
  }, [c.host, isElectron]);
  const [slotNote, setSlotNote] = useState("");
  const s = r.session;
  const busy = r.status === "capturing" || r.status === "reading";
  const layoutBoxes = useMemo(() => {
    if (!layout || !s) return [];
    const img = { width: s.capture.width, height: s.capture.height };
    return [{ rect: toPixels(layout.itemArea, img), label: "items", tone: "area" as const }, ...slotBoxes(layout, img)];
  }, [layout, s]);
  const slotCount = layout?.slots ? layout.slots.cards.reduce((n, card) => n + card.slots.length, 0) : 0;
  /** Add the slots visible on this capture to the calibrated grid (e.g. after players bought more items). */
  const learnSlots = async () => {
    if (!layout || !s) return;
    const templates = await r.ensureTemplates();
    if (!templates) return;
    const fresh = readScreen(toImage(s.capture), { ...layout, slots: undefined }, templates);
    const grid = buildSlotGrid(fresh, layout);
    if (!grid) {
      setSlotNote("No items were found on this capture, so the slots were not changed.");
      return;
    }
    const slots = layout.slots ? mergeSlotGrids(layout.slots, grid) : grid;
    const next = { ...layout, slots };
    await c.updateSettings({ screen: { layout: next } });
    setSlotNote(`Slots: ${slots.cards.length} players, ${slots.cards.reduce((n, card) => n + card.slots.length, 0)} item slots.`);
    r.reread(next);
  };

  return (
    <div className="grid2 screen-page">
      <Section title="Read the scoreboard" className="span2">
        <p>
          In Deadlock, press <b>{c.settings.hotkeys.readScreen}</b> and hold <b>Tab</b> so the scoreboard is showing
          {c.settings.screen.captureDelayMs > 0 ? ` (the capture happens ${(c.settings.screen.captureDelayMs / 1000).toFixed(1)} s after the hotkey)` : ""}. CounterCoach
          captures the screen under your mouse, reads the item icons and portraits locally, and fills in your items and the enemies' items. Nothing is uploaded, and the
          game is not touched: this only looks at pixels you can already see.
        </p>
        <p className="small">
          Easiest way: calibrate once on a capture where everyone has lots of items, then turn on <b>Read automatically every time I hold Tab</b> below. After that, each
          time you hold Tab in a match the scoreboard is read again and the advice updates.
        </p>
        <div className="row gap">
          {isElectron && (
            <button type="button" className="btn" disabled={busy} onClick={() => void r.captureNow(5000)}>
              Capture in 5 s
            </button>
          )}
          <button type="button" className="btn ghost" disabled={busy} onClick={() => void r.loadFile()}>
            Load screenshot…
          </button>
          {s && layout && (
            <button type="button" className="btn ghost" disabled={busy} onClick={() => r.reread()}>
              Read again
            </button>
          )}
          {s && (
            <button type="button" className="btn ghost" disabled={busy} onClick={() => setCalibrating(true)}>
              {layout ? "Recalibrate on this capture" : "Calibrate on this capture"}
            </button>
          )}
          {s && layout && (
            <button type="button" className="btn ghost" disabled={busy} onClick={() => void learnSlots()}>
              Learn more slots from this capture
            </button>
          )}
        </div>
        {slotNote && <p className="small muted">{slotNote}</p>}
        {r.message && <p className={`small screen-msg ${r.status === "error" ? "flag" : r.status === "applied" ? "good" : "muted"}`}>{r.message}</p>}
        <TemplateStatus r={r} />
        <p className="muted small">
          Status: <b>experimental</b>. Item recognition was checked on crops of a real Deadlock screenshot and on synthetic scoreboards built from the game's item art
          (see docs/TEST_RESULTS.md); a full 12-player match scoreboard has not been tested yet. Uncertain icons are always shown for you to confirm. Enemy items appear
          only if the game shows them on that screen.
        </p>
      </Section>

      {calibrating && s && (
        <Calibrate
          c={c}
          r={r}
          session={s}
          onDone={(newLayout) => {
            setCalibrating(false);
            if (newLayout) {
              const g = newLayout.slots;
              setSlotNote(
                g
                  ? `Calibrated: learned ${g.cards.length} players and ${g.cards.reduce((n, card) => n + card.slots.length, 0)} item slots. Later reads check only these slots.`
                  : "Calibrated, but no item slots were found on this capture; reads will scan the whole area.",
              );
              r.reread(newLayout);
            }
          }}
        />
      )}
      {!calibrating && s?.read && <Review c={c} r={r} />}
      {!calibrating && s && !s.read && r.status !== "calibrate" && (
        <Section title="Capture" className="span2">
          <ImageBoxes src={s.preview} width={s.capture.width} height={s.capture.height} boxes={layoutBoxes} />
        </Section>
      )}

      <Section title="Screen reader settings">
        <p className="small">
          Calibration:{" "}
          {layout ? (
            <>
              <Badge tone="good">set</Badge>{" "}
              <span className="muted">
                aspect {layout.aspect.toFixed(2)}, icon {(layout.iconSize * 100).toFixed(1)}% of screen height, portraits {layout.portrait ? "on" : "off"}
                {layout.slots ? `, ${layout.slots.cards.length} players, ${slotCount} item slots learned` : ", no item slots learned yet"}
              </span>{" "}
              <button type="button" className="btn tiny ghost" onClick={() => void c.updateSettings({ screen: { layout: null } })}>
                clear
              </button>
            </>
          ) : (
            <Badge tone="warn">not set — capture the scoreboard once and calibrate</Badge>
          )}
        </p>
        <label className="check">
          <input type="checkbox" checked={c.settings.screen.autoApply} onChange={(e) => void c.updateSettings({ screen: { autoApply: e.target.checked } })} /> Apply automatically when every icon and row is confident
        </label>
        <label className="check">
          <input type="checkbox" checked={c.settings.screen.fullInventories} onChange={(e) => void c.updateSettings({ screen: { fullInventories: e.target.checked } })} /> The calibrated screen shows full inventories
        </label>
        <label className="field inline">
          <span>Capture delay after the hotkey</span>
          <select className="input" value={c.settings.screen.captureDelayMs} onChange={(e) => void c.updateSettings({ screen: { captureDelayMs: Number(e.target.value) } })}>
            {[0, 500, 1000, 1500, 2000, 3000].map((ms) => (
              <option key={ms} value={ms}>
                {ms === 0 ? "none (hold Tab first)" : `${ms / 1000} s`}
              </option>
            ))}
          </select>
        </label>
        <p className="muted small">Hotkey: {c.settings.hotkeys.readScreen} (change it in Settings → Hotkeys; a plain F-key such as F8 also works).</p>
        {isElectron && (
          <>
            <label className="check">
              <input
                type="checkbox"
                checked={c.settings.screen.captureOnTab}
                onChange={(e) => void c.updateSettings({ screen: { captureOnTab: e.target.checked } })}
              />{" "}
              Read automatically every time I hold Tab in the game
            </label>
            <label className="field inline">
              <span>Hold Tab for</span>
              <select
                className="input"
                value={c.settings.screen.tabDelayMs}
                disabled={!c.settings.screen.captureOnTab}
                onChange={(e) => void c.updateSettings({ screen: { tabDelayMs: Number(e.target.value) } })}
              >
                {[250, 450, 700, 1000, 1500].map((ms) => (
                  <option key={ms} value={ms}>
                    {ms / 1000} s before capturing
                  </option>
                ))}
              </select>
            </label>
            <p className="small">
              {c.settings.screen.captureOnTab ? (
                tab?.running ? (
                  <>
                    <Badge tone="good">listening for Tab</Badge>{" "}
                    <span className="muted">
                      {layout ? "" : "Calibrate first. "}A tap is ignored; holding Tab captures once. Reads that find no scoreboard change nothing.
                    </span>
                  </>
                ) : (
                  <Badge tone="warn">{tab?.error ? `Tab listener could not start: ${tab.error}` : "starting…"}</Badge>
                )
              ) : (
                <span className="muted">Off. When on, CounterCoach watches only the Tab key (it never blocks or sends keys) while this app is running.</span>
              )}
            </p>
          </>
        )}
      </Section>
      <Section title="Privacy">
        <p className="small">Captures are processed in memory and discarded. Nothing leaves your computer.</p>
        <p className="muted small">
          Read on Tab is off unless you turn it on. It uses a passive keyboard listener that reacts only to Tab: it never blocks or sends keys, and no keys are logged or
          stored.
        </p>
        {isElectron && (
          <>
            <label className="check">
              <input type="checkbox" checked={c.settings.screen.saveCaptures} onChange={(e) => void c.updateSettings({ screen: { saveCaptures: e.target.checked } })} /> Keep copies of
              captures on disk (for troubleshooting; last 20)
            </label>
            <p className="muted small">
              Saved: {saved?.count ?? 0} file{saved?.count === 1 ? "" : "s"} ({((saved?.bytes ?? 0) / 1e6).toFixed(1)} MB)
            </p>
            <div className="row gap">
              <button type="button" className="btn small ghost" onClick={() => void c.host.openSavedCaptures()}>
                Open folder
              </button>
              <button type="button" className="btn small ghost" disabled={!saved?.count} onClick={() => void c.host.deleteSavedCaptures().then(setSaved)}>
                Delete saved captures
              </button>
            </div>
          </>
        )}
      </Section>
    </div>
  );
}
