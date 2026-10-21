// A tiny hash router: "#/fixtures", "#/match/123", "#/past?competition=E0".
//
// Routes are not listed here: pages register themselves in src/pages/index.ts and the app
// resolves the current path against that list with `resolve`. Every navigation is a real
// browser history entry, so Back and Forward work, and each entry remembers its scroll
// position.

import { useMemo, useSyncExternalStore } from "react";

export type Params = Readonly<Record<string, string>>;

export type RouteLocation = {
  /** The whole hash, e.g. "#/past?competition=E0". */
  hash: string;
  /** The path part, e.g. "/past". */
  path: string;
  /** The query part. Treat as read-only; change it with setQuery. */
  query: URLSearchParams;
};

export type QueryUpdate = Record<string, string | number | null | undefined>;

let defaultPath = "/";

function normalisePath(path: string): string {
  const p = `/${path.replace(/^\/+/, "")}`;
  return p.length > 1 ? p.replace(/\/+$/, "") : p;
}

/** Split "#/past?competition=E0" into its path and query. */
export function parseHash(hash: string): { path: string; search: string } {
  const h = hash.startsWith("#") ? hash.slice(1) : hash;
  const q = h.indexOf("?");
  const path = normalisePath(q >= 0 ? h.slice(0, q) : h);
  return { path, search: q >= 0 ? h.slice(q + 1) : "" };
}

/** Match "/match/:id" against "/match/123" -> { id: "123" }, or null. */
export function matchPath(pattern: string, path: string): Params | null {
  const want = pattern.split("/").filter(Boolean);
  const got = path.split("/").filter(Boolean);
  if (want.length !== got.length) return null;
  const params: Record<string, string> = {};
  for (let i = 0; i < want.length; i++) {
    const w = want[i];
    const g = got[i];
    if (w === undefined || g === undefined) return null;
    if (w.startsWith(":")) {
      try {
        params[w.slice(1)] = decodeURIComponent(g);
      } catch {
        return null;
      }
    } else if (w !== g) {
      return null;
    }
  }
  return params;
}

/** The first route whose pattern matches `path`, with its parameters. */
export function resolve<R extends { path: string }>(
  routes: readonly R[],
  path: string,
): { route: R; params: Params } | null {
  for (const route of routes) {
    const params = matchPath(route.path, path);
    if (params) return { route, params };
  }
  return null;
}

/** A link target: href("/match/12") -> "#/match/12"; query values that are empty are dropped. */
export function href(path: string, query?: QueryUpdate): string {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query ?? {})) {
    if (v !== null && v !== undefined && v !== "") params.set(k, String(v));
  }
  const qs = params.toString();
  return `#${normalisePath(path)}${qs ? `?${qs}` : ""}`;
}

/** Go to a path ("/fixtures" or "#/fixtures"). `replace` swaps the current history entry. */
export function navigate(to: string, options: { replace?: boolean } = {}): void {
  const target = to.startsWith("#") ? to : `#${normalisePath(to)}`;
  if (target === window.location.hash) return;
  if (options.replace) {
    const oldURL = window.location.href;
    window.history.replaceState(window.history.state, "", target);
    window.dispatchEvent(new HashChangeEvent("hashchange", { oldURL, newURL: window.location.href }));
  } else {
    window.location.hash = target;
  }
}

/** Change query parameters on the current page (null or "" removes one). Filters use this;
 * by default it replaces the history entry so a run of filter changes is one Back step. */
export function setQuery(update: QueryUpdate, options: { replace?: boolean } = {}): void {
  const { path, search } = parseHash(window.location.hash);
  const params = new URLSearchParams(search);
  for (const [k, v] of Object.entries(update)) {
    if (v === null || v === undefined || v === "") params.delete(k);
    else params.set(k, String(v));
  }
  const qs = params.toString();
  navigate(`#${path}${qs ? `?${qs}` : ""}`, { replace: options.replace ?? true });
}

// ---------------------------------------------------------------------------------------------
// History entries: each gets a key (to remember its scroll position) and a depth (to know
// whether Back stays inside the app).

type EntryState = { routerKey: string; routerDepth: number };

function entryState(): Partial<EntryState> {
  const s: unknown = window.history.state;
  return typeof s === "object" && s !== null ? (s as Partial<EntryState>) : {};
}

let currentKey = "";
let currentDepth = 0;
/** The hash of the entry `currentKey` belongs to. */
let currentHash = "";
const scrollByEntry = new Map<string, number>();

function adoptEntry(depthIfNew: number): void {
  currentHash = window.location.hash;
  const s = entryState();
  if (s.routerKey) {
    currentKey = s.routerKey;
    currentDepth = s.routerDepth ?? 0;
    return;
  }
  currentKey = Math.random().toString(36).slice(2);
  currentDepth = depthIfNew;
  const base: unknown = window.history.state;
  window.history.replaceState(
    { ...(typeof base === "object" && base !== null ? base : {}), routerKey: currentKey, routerDepth: currentDepth },
    "",
  );
}

let installed = false;

/** Call once before rendering. `fallback` is where an empty or bare "#/" hash goes. */
export function installRouter(fallback: string): void {
  defaultPath = normalisePath(fallback);
  if (installed) return;
  installed = true;
  if ("scrollRestoration" in window.history) window.history.scrollRestoration = "manual";
  if (parseHash(window.location.hash).path === "/") {
    window.history.replaceState(window.history.state, "", `#${defaultPath}`);
  }
  adoptEntry(0);
  // Registered before React subscribes, so the old page's scroll is saved before it re-renders.
  window.addEventListener("hashchange", () => {
    scrollByEntry.set(currentKey, window.scrollY);
    const previousDepth = currentDepth;
    if (parseHash(window.location.hash).path === "/") {
      window.history.replaceState(window.history.state, "", `#${defaultPath}`);
    }
    adoptEntry(previousDepth + 1);
  });
  // Loading the current address again (Enter in the address bar on the same URL) replaces the
  // entry with an empty state and fires no hashchange. Put this entry's key back, or its scroll
  // position would be lost when the reader later comes Back to it.
  window.addEventListener("popstate", () => {
    if (window.location.hash !== currentHash || entryState().routerKey) return;
    const base: unknown = window.history.state;
    window.history.replaceState(
      { ...(typeof base === "object" && base !== null ? base : {}), routerKey: currentKey, routerDepth: currentDepth },
      "",
    );
  });
}

/** Where the current history entry was scrolled to when the reader left it (0 if new). */
export function savedScrollPosition(): number {
  return scrollByEntry.get(currentKey) ?? 0;
}

/** True when Back would stay inside this app. */
export function canGoBack(): boolean {
  return currentDepth > 0;
}

/** Back within the app if possible, otherwise go to `fallback`. */
export function goBack(fallback: string): void {
  if (canGoBack()) window.history.back();
  else navigate(fallback);
}

// ---------------------------------------------------------------------------------------------
// React binding

function subscribe(onChange: () => void): () => void {
  window.addEventListener("hashchange", onChange);
  return () => window.removeEventListener("hashchange", onChange);
}

function readHash(): string {
  return window.location.hash || `#${defaultPath}`;
}

/** The current location; re-renders on every hash change. */
export function useLocation(): RouteLocation {
  const hash = useSyncExternalStore(subscribe, readHash, () => `#${defaultPath}`);
  return useMemo(() => {
    const { path, search } = parseHash(hash);
    return { hash, path, query: new URLSearchParams(search) };
  }, [hash]);
}
