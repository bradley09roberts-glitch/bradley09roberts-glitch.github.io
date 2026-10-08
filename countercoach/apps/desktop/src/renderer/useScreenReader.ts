import { useCallback, useEffect, useRef, useState } from "react";
import {
  canAutoApply,
  isShopLegal,
  loadTemplates,
  proposeTargets,
  readField,
  readScreen,
  sameTarget,
  screenReadEvents,
  type RgbaImage,
  type RowTarget,
  type ScreenItemRead,
  type ScreenLayout,
  type ScreenRead,
  type TemplateSet,
} from "@countercoach/engine";
import type { CaptureResult, CapturePayload } from "../shared/capture";
import type { Coach } from "./useCoach";

/**
 * Screen reader state for the renderer: turns a capture into a read, proposes who each row
 * belongs to, applies confident reads automatically when allowed, and otherwise waits for the
 * user to confirm. Captures live only in memory here.
 */

export interface ItemEdit {
  read: ScreenItemRead;
  /** Chosen item className, or null to drop this icon. */
  choice: string | null;
}

export interface RowEdit {
  index: number;
  yFrac: number;
  target: RowTarget | null;
  unknownHeroId: number | null;
  heroGuess: number | null;
  reason: string;
  items: ItemEdit[];
}

export type ReaderStatus = "idle" | "capturing" | "reading" | "calibrate" | "review" | "applied" | "error";

export interface ReaderSession {
  capture: CapturePayload;
  /** JPEG data URL for display. */
  preview: string;
  read: ScreenRead | null;
  rows: RowEdit[];
  appliedAt: number | null;
}

const REMEMBER_KEY = "cc.screenRows";
const NOTE_MS = 45_000;

function loadRemembered(matchId: string | null): { yFrac: number; target: RowTarget }[] {
  if (!matchId) return [];
  try {
    const all = JSON.parse(localStorage.getItem(REMEMBER_KEY) ?? "{}") as Record<string, { yFrac: number; target: RowTarget }[]>;
    return Array.isArray(all[matchId]) ? all[matchId]! : [];
  } catch {
    return [];
  }
}

function saveRemembered(matchId: string | null, rows: { yFrac: number; target: RowTarget }[]): void {
  if (!matchId) return;
  try {
    // Only the current match is kept: row positions mean nothing in another match.
    localStorage.setItem(REMEMBER_KEY, JSON.stringify({ [matchId]: rows.slice(0, 24) }));
  } catch {
    /* storage unavailable */
  }
}

export function toImage(c: CapturePayload): RgbaImage {
  return { width: c.width, height: c.height, data: c.data, order: c.order };
}

/** Render a capture to a JPEG data URL for display (the read itself uses the raw pixels). */
export function previewUrl(c: CapturePayload, maxWidth = 1600): string {
  const canvas = document.createElement("canvas");
  canvas.width = c.width;
  canvas.height = c.height;
  const ctx = canvas.getContext("2d");
  if (!ctx) return "";
  const rgba = new Uint8ClampedArray(c.width * c.height * 4);
  if (c.order === "bgra") {
    for (let i = 0; i < rgba.length; i += 4) {
      rgba[i] = c.data[i + 2]!;
      rgba[i + 1] = c.data[i + 1]!;
      rgba[i + 2] = c.data[i]!;
      rgba[i + 3] = 255;
    }
  } else rgba.set(c.data);
  ctx.putImageData(new ImageData(rgba, c.width, c.height), 0, 0);
  if (c.width <= maxWidth) return canvas.toDataURL("image/jpeg", 0.85);
  const small = document.createElement("canvas");
  small.width = maxWidth;
  small.height = Math.round((c.height * maxWidth) / c.width);
  small.getContext("2d")?.drawImage(canvas, 0, 0, small.width, small.height);
  return small.toDataURL("image/jpeg", 0.85);
}

function describe(t: TemplateSet): string {
  return `${t.items.length} items, ${new Set(t.heroes.map((h) => h.heroId)).size} heroes`;
}

export function useScreenReader(c: Coach, iconsRaw: unknown) {
  const boot0 = useRef(c.dataBoot.snapshot.meta.contentHash);
  const [status, setStatus] = useState<ReaderStatus>("idle");
  const [message, setMessage] = useState<string>("");
  const [session, setSession] = useState<ReaderSession | null>(null);
  const [fullInventories, setFullInventories] = useState(c.settings.screen.fullInventories);
  const noteTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Hotkey captures and post-calibration re-reads must see the latest settings and match state,
  // not the values captured when a callback was created.
  const latest = useRef(c);
  latest.current = c;

  // Icon templates are built on this machine on first use (one download of the item art), then
  // cached by the main process; a new data snapshot means new templates.
  const templatesRef = useRef<TemplateSet | null>(null);
  const [templates, setTemplates] = useState<{ status: "idle" | "loading" | "ready" | "error"; detail: string; progress: { done: number; total: number } | null }>({
    status: "idle",
    detail: "",
    progress: null,
  });
  const snapshotHash = c.dataBoot.snapshot.meta.contentHash;
  useEffect(() => {
    templatesRef.current = null;
    try {
      if (iconsRaw && snapshotHash === boot0.current) templatesRef.current = loadTemplates(iconsRaw);
    } catch {
      templatesRef.current = null;
    }
    setTemplates(templatesRef.current ? { status: "ready", detail: describe(templatesRef.current), progress: null } : { status: "idle", detail: "", progress: null });
  }, [iconsRaw, snapshotHash]);
  useEffect(() => c.host.onTemplatesProgress((p) => setTemplates((t) => ({ ...t, progress: p }))), [c.host]);

  const ensureTemplates = useCallback(async (): Promise<TemplateSet | null> => {
    if (templatesRef.current) return templatesRef.current;
    setTemplates({ status: "loading", detail: "", progress: null });
    const raw = await c.host.iconTemplates();
    if (raw && typeof raw === "object" && "error" in raw) {
      setTemplates({ status: "error", detail: String((raw as { error: unknown }).error), progress: null });
      return null;
    }
    try {
      templatesRef.current = loadTemplates(raw);
      setTemplates({ status: "ready", detail: describe(templatesRef.current), progress: null });
      return templatesRef.current;
    } catch {
      setTemplates({ status: "error", detail: "The icon templates were invalid.", progress: null });
      return null;
    }
  }, [c.host]);

  const note = useCallback(
    (text: string | null) => {
      if (noteTimer.current) clearTimeout(noteTimer.current);
      c.setScreenNote(text);
      if (text) noteTimer.current = setTimeout(() => c.setScreenNote(null), NOTE_MS);
    },
    [c.setScreenNote],
  );

  const roster = useCallback(() => {
    const now = Date.now();
    const st = latest.current.state;
    return {
      meHero: (readField(st, "me.hero", now)?.value as number | undefined) ?? null,
      enemies: (readField(st, "roster.enemies", now)?.value as number[] | undefined) ?? [],
      allies: (readField(st, "roster.allies", now)?.value as number[] | undefined) ?? [],
    };
  }, []);

  const apply = useCallback(
    (rows: RowEdit[], full: boolean, auto: boolean) => {
      const confirmed = rows
        .filter((r) => r.target && r.target.kind !== "ignore")
        .map((r) => ({ target: r.target!, items: r.items.map((i) => i.choice).filter((x): x is string => !!x) }));
      const events = screenReadEvents(confirmed, {
        observedAt: Date.now(),
        gameTime: null,
        complete: full,
        // Machine-only reads are medium confidence (they never override your own corrections);
        // reads you reviewed count as high.
        confidence: auto ? "medium" : "high",
      });
      for (const e of events) latest.current.send(e);
      saveRemembered(
        latest.current.state.matchId,
        rows.filter((r) => r.target).map((r) => ({ yFrac: r.yFrac, target: r.target! })),
      );
      const players = confirmed.length;
      const items = confirmed.reduce((n, r) => n + r.items.length, 0);
      return { players, items };
    },
    [],
  );

  const read = useCallback(
    (cap: CapturePayload, templates: TemplateSet | null, layoutOverride?: ScreenLayout) => {
      const c = latest.current;
      const preview = previewUrl(cap);
      const layout = layoutOverride ?? c.settings.screen.layout;
      if (!templates) {
        setSession({ capture: cap, preview, read: null, rows: [], appliedAt: null });
        setStatus("error");
        setMessage("Icon templates could not be prepared, so the screen cannot be read yet. See Screen reader settings.");
        return;
      }
      if (!layout) {
        setSession({ capture: cap, preview, read: null, rows: [], appliedAt: null });
        setStatus("calibrate");
        setMessage("Calibrate once: mark where the item icons are on this capture.");
        note("Screen captured — calibrate it in CounterCoach (Screen tab)");
        return;
      }
      const ctx = roster();
      const heroId = ctx.meHero;
      const items = c.deps.data.items().filter((i) => isShopLegal(i, heroId, c.deps.rules).legal);
      const allowed = new Set(items.map((i) => i.className));
      const result = readScreen(toImage(cap), layout, templates, {
        allowItem: (cn) => allowed.has(cn),
        allowHero: (id) => !!c.deps.data.hero(id)?.playable,
      });
      const props = proposeTargets(result, ctx, loadRemembered(c.state.matchId));
      const rows: RowEdit[] = result.rows.map((r, i) => ({
        index: r.index,
        yFrac: r.yFrac,
        target: props[i]!.target,
        unknownHeroId: props[i]!.unknownHeroId,
        heroGuess: r.hero && r.hero.status !== "none" ? (r.hero.candidates[0]?.heroId ?? null) : null,
        reason: props[i]!.reason,
        items: r.items.map((it) => ({ read: it, choice: it.status === "confident" ? it.candidates[0]!.className : null })),
      }));
      const full = c.settings.screen.fullInventories;
      setFullInventories(full);
      if (c.settings.screen.autoApply && canAutoApply(result, props)) {
        const res = apply(rows, full, true);
        setSession({ capture: cap, preview, read: result, rows, appliedAt: Date.now() });
        setStatus("applied");
        setMessage(`Applied automatically: ${res.players} players, ${res.items} items (${result.ms} ms).`);
        note(`Read ${res.players} players · ${res.items} items`);
      } else {
        setSession({ capture: cap, preview, read: result, rows, appliedAt: null });
        setStatus("review");
        const unsure = rows.reduce((n, r) => n + r.items.filter((i) => i.read.status !== "confident").length, 0);
        setMessage(
          result.rows.length === 0
            ? (result.warnings[0] ?? "No item icons found.")
            : `Found ${result.rows.length} rows (${result.ms} ms). Check ${unsure ? `${unsure} uncertain icon${unsure > 1 ? "s" : ""} and ` : ""}who each row belongs to, then apply.`,
        );
        note(result.rows.length ? "Screen read needs a quick check (Screen tab)" : "Screen read found no items");
      }
    },
    [roster, apply, note],
  );

  const ingest = useCallback(
    (cap: CaptureResult | null, layoutOverride?: ScreenLayout) => {
      if (!cap) {
        setStatus((s) => (s === "capturing" || s === "reading" ? "idle" : s));
        return;
      }
      if ("error" in cap) {
        setStatus("error");
        setMessage(cap.error);
        note(`Screen read failed: ${cap.error}`.slice(0, 120));
        return;
      }
      setStatus("reading");
      setMessage(templatesRef.current ? "Reading…" : "Preparing icon templates (one-time download of the item art)…");
      void ensureTemplates().then((templates) => {
        // Let React paint the "Reading…" state before the synchronous read.
        setTimeout(() => read(cap, templates, layoutOverride), 30);
      });
    },
    [ensureTemplates, read, note],
  );

  // Hotkey captures arrive from the main process.
  useEffect(() => c.host.onScreenCaptured((r) => ingest(r)), [c.host, ingest]);

  const captureNow = useCallback(
    async (delayMs: number) => {
      setStatus("capturing");
      setMessage(delayMs ? `Capturing in ${Math.round(delayMs / 1000)} s — switch to the game now.` : "Capturing…");
      ingest(await c.host.captureScreen(delayMs));
    },
    [c.host, ingest],
  );

  const loadFile = useCallback(async () => ingest(await c.host.loadImage()), [c.host, ingest]);

  /** Re-run the read on the current capture (e.g. right after calibrating, with the new layout). */
  const reread = useCallback(
    (layout?: ScreenLayout) => {
      if (session) ingest(session.capture, layout);
    },
    [session, ingest],
  );

  const setRowTarget = useCallback((row: number, target: RowTarget | null) => {
    setSession((s) => {
      if (!s) return s;
      const rows = s.rows.map((r) => {
        if (r.index === row) return { ...r, target };
        // One player per row: move the assignment instead of duplicating it.
        if (target && target.kind !== "ignore" && sameTarget(r.target, target)) return { ...r, target: null };
        return r;
      });
      return { ...s, rows };
    });
  }, []);

  const setItemChoice = useCallback((row: number, item: number, choice: string | null) => {
    setSession((s) => (s ? { ...s, rows: s.rows.map((r) => (r.index === row ? { ...r, items: r.items.map((it, i) => (i === item ? { ...it, choice } : it)) } : r)) } : s));
  }, []);

  const confirm = useCallback(() => {
    if (!session) return;
    const res = apply(session.rows, fullInventories, false);
    setSession({ ...session, appliedAt: Date.now() });
    setStatus("applied");
    setMessage(`Applied: ${res.players} players, ${res.items} items.`);
    note(`Read ${res.players} players · ${res.items} items`);
  }, [session, apply, fullInventories, note]);

  const discard = useCallback(() => {
    setSession(null);
    setStatus("idle");
    setMessage("");
    note(null);
  }, [note]);

  return {
    status,
    message,
    session,
    templates,
    ensureTemplates,
    fullInventories,
    setFullInventories,
    captureNow,
    loadFile,
    reread,
    setRowTarget,
    setItemChoice,
    confirm,
    discard,
  };
}

export type ScreenReader = ReturnType<typeof useScreenReader>;
