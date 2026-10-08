import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import {
  ManualInput,
  applyEvent,
  createDeps,
  createMatchState,
  currentGameTime,
  defaultPreferences,
  evaluate,
  logEntry,
  readField,
  type CoachOutput,
  type EngineDeps,
  type ItemDecision,
  type MatchEvent,
  type MatchState,
  type Preferences,
} from "@countercoach/engine";
import type { DataStatusEvent, OverlayModel } from "../shared/ipc";
import type { Settings } from "../shared/settings";
import { getHost, type Bootstrap } from "./host";
import { THREAT_SHORT, abilityLine } from "./format";

const MATCH_KEY = "cc.currentMatch";
const DECISIONS_KEY = "cc.itemDecisions";

function loadMatch(): MatchState {
  try {
    const raw = localStorage.getItem(MATCH_KEY);
    if (raw) {
      const s = JSON.parse(raw) as MatchState;
      if (s && s.storeKind === "live" && s.fields && Array.isArray(s.log)) return { ...s, pauses: s.pauses ?? [] };
    }
  } catch {
    /* ignore */
  }
  return createMatchState("live");
}

function saveMatch(s: MatchState): void {
  try {
    localStorage.setItem(MATCH_KEY, JSON.stringify({ ...s, log: s.log.slice(-60) }));
  } catch {
    /* storage unavailable */
  }
}

interface Decisions {
  pinned: ItemDecision[];
  rejected: ItemDecision[];
  deferred: ItemDecision[];
}

export function useCoach(boot: Bootstrap) {
  const host = getHost();
  const [settings, setSettingsState] = useState<Settings>(boot.settings);
  const [dataBoot, setDataBoot] = useState({ snapshot: boot.snapshot, stamps: boot.stamps, source: boot.source, latest: boot.latest });
  const [dataEvent, setDataEvent] = useState<DataStatusEvent | null>(null);
  const [state, dispatch] = useReducer((s: MatchState, e: MatchEvent | { type: "replace"; state: MatchState }) => (e.type === "replace" ? e.state : applyEvent(s, e)), undefined, loadMatch);
  const [now, setNow] = useState(() => Date.now());
  const [decisions, setDecisions] = useState<Decisions>(() => {
    try {
      return (JSON.parse(localStorage.getItem(DECISIONS_KEY) ?? "null") as Decisions) ?? { pinned: [], rejected: [], deferred: [] };
    } catch {
      return { pinned: [], rejected: [], deferred: [] };
    }
  });
  const previous = useRef<CoachOutput["rec"] | null>(null);
  const lastLogged = useRef<string>("");

  useEffect(() => host.onSettings((s) => setSettingsState(s)), [host]);
  useEffect(() => host.onDataEvent((e) => setDataEvent(e)), [host]);
  useEffect(() => saveMatch(state), [state]);
  useEffect(() => {
    try {
      localStorage.setItem(DECISIONS_KEY, JSON.stringify(decisions));
    } catch {
      /* ignore */
    }
  }, [decisions]);
  // Freshness labels need a slow clock; recommendations themselves are event-driven.
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 5000);
    return () => clearInterval(t);
  }, []);

  const deps: EngineDeps = useMemo(
    () =>
      createDeps(dataBoot.snapshot, dataBoot.stamps, {
        mode: state.mode,
        overrides: { totalSlots: settings.rules.totalSlots ?? undefined, sellFraction: settings.rules.sellFraction ?? undefined },
        latest: dataEvent?.latestBuild != null ? { clientVersion: dataEvent.latestBuild, checkedAt: dataEvent.at } : dataBoot.latest,
      }),
    [dataBoot, settings.rules.totalSlots, settings.rules.sellFraction, dataEvent, state.mode],
  );

  const heroId = (readField(state, "me.hero", now)?.value as number | undefined) ?? null;
  const prefs: Preferences = useMemo(() => {
    const p = defaultPreferences();
    p.difficulty = settings.coach.difficulty;
    p.route = settings.coach.route;
    p.archetypeId = heroId != null ? (settings.coach.archetypeByHero[String(heroId)] ?? null) : null;
    p.lockedCore = heroId != null ? (settings.coach.lockedCoreByHero[String(heroId)] ?? []) : [];
    p.pinned = decisions.pinned;
    p.rejected = decisions.rejected;
    p.deferred = decisions.deferred;
    return p;
  }, [settings.coach, heroId, decisions]);

  const output: CoachOutput = useMemo(() => {
    const out = evaluate(deps, state, prefs, now, previous.current);
    return out;
  }, [deps, state, prefs, now]);
  useEffect(() => {
    previous.current = output.rec;
  }, [output]);

  // Overlay model (compact) pushed to the overlay window.
  const overlayModel: OverlayModel = useMemo(() => toOverlayModel(output, deps), [output, deps]);
  useEffect(() => host.overlayUpdate(overlayModel), [host, overlayModel]);

  // Opt-in decision logging on meaningful advice changes only.
  useEffect(() => {
    if (!settings.logging.enabled || !state.matchId || output.rec.heroId == null) return;
    const key = `${output.rec.buyNow?.className}|${output.rec.saveFor?.className}|${output.ability?.ability?.className}:${output.ability?.tier}|${output.rec.souls}`;
    if (key === lastLogged.current) return;
    lastLogged.current = key;
    void host.logAppend(state.matchId, logEntry(output.rec, state, now, output.ability));
  }, [output, settings.logging.enabled, state, now, host]);

  const gameTime = currentGameTime(state, now);
  const input = useMemo(() => new ManualInput(() => Date.now(), () => currentGameTime(state, Date.now())), [state]);
  const send = useCallback((e: MatchEvent) => dispatch(e), []);
  const replaceState = useCallback((s: MatchState) => dispatch({ type: "replace", state: s }), []);

  const updateSettings = useCallback(async (patch: unknown) => setSettingsState(await host.setSettings(patch)), [host]);

  const decide = useCallback(
    (kind: keyof Decisions, className: string, reason: string, untilGameTime?: number | null) => {
      setDecisions((d) => {
        const without = (arr: ItemDecision[]) => arr.filter((x) => x.className !== className);
        const entry: ItemDecision = { className, reason, at: Date.now(), untilGameTime: untilGameTime ?? null };
        const next: Decisions = { pinned: without(d.pinned), rejected: without(d.rejected), deferred: without(d.deferred) };
        next[kind] = [...next[kind], entry];
        return next;
      });
    },
    [],
  );
  const clearDecision = useCallback((className: string) => {
    setDecisions((d) => ({
      pinned: d.pinned.filter((x) => x.className !== className),
      rejected: d.rejected.filter((x) => x.className !== className),
      deferred: d.deferred.filter((x) => x.className !== className),
    }));
  }, []);

  const newMatch = useCallback(() => {
    const id = `m-${new Date().toISOString().replace(/[:.]/g, "-")}`;
    dispatch({ type: "match.start", matchId: id, mode: "standard", at: Date.now() });
    setDecisions({ pinned: [], rejected: [], deferred: [] });
    previous.current = null;
  }, []);

  const applyRefreshedData = useCallback((b: { snapshot: Bootstrap["snapshot"]; stamps: Bootstrap["stamps"]; source: string; latest: Bootstrap["latest"] }) => setDataBoot(b), []);

  return {
    host,
    deps,
    state,
    prefs,
    output,
    settings,
    dataBoot,
    dataEvent,
    setDataEvent,
    applyRefreshedData,
    now,
    gameTime,
    input,
    send,
    replaceState,
    updateSettings,
    decide,
    clearDecision,
    decisions,
    newMatch,
    overlayModel,
  };
}

export type Coach = ReturnType<typeof useCoach>;

export function toOverlayModel(out: CoachOutput, deps: EngineDeps): OverlayModel {
  const r = out.rec;
  const primaryAction = r.buyNow ?? r.saveFor;
  const hero = r.heroId != null ? deps.data.hero(r.heroId) : undefined;
  const reason = primaryAction?.reasons[0] ?? r.why[0] ?? (r.decision === "need-info" ? r.missingInfo[0]?.why ?? "Set up your match" : r.summary);
  return {
    infoState: r.informationState,
    heroName: hero?.name ?? null,
    decision: r.decision,
    primary: primaryAction
      ? {
          label: r.buyNow ? "BUY NOW" : "SAVE FOR",
          name: primaryAction.name,
          image: primaryAction.image,
          soulsShort: Math.max(0, Math.round(primaryAction.soulsShort)),
          remainingCost: Math.max(0, Math.round(primaryAction.remainingCost)),
        }
      : null,
    saveFor: r.buyNow && r.saveFor ? { name: r.saveFor.name, soulsShort: Math.max(0, Math.round(r.saveFor.soulsShort)) } : null,
    alternative: r.alternative ? { name: r.alternative.name, condition: (r.alternative.condition ?? "").slice(0, 240) } : null,
    reason: reason.slice(0, 240),
    ability: out.ability ? abilityLine(out.ability).slice(0, 200) : null,
    threats: out.threatPanel.slice(0, 3).map((t) => ({ label: THREAT_SHORT[t.kind] ?? t.label, evidence: t.evidence, urgent: t.urgent })),
    confidence: r.confidence,
    dataNote: r.dataStatus.compat.status === "outdated" ? r.dataStatus.compat.message.slice(0, 200) : null,
    updatedAt: r.generatedAt,
  };
}
