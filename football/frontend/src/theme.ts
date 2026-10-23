// The colour theme: Auto, Light or Dark, chosen by the reader (ThemeSwitch.tsx) and kept per
// browser in localStorage. Auto, the default, follows the host's theme when the page is shown
// inside an app that sets data-theme="light" or "dark" on <html> (the artifact host of the
// phone copy does), and the device's setting otherwise. Light or Dark wins over both.
//
// The choice is applied as data-app-theme on <html> (absent for Auto); styles.css picks the
// tokens from it ("Colour: which theme shows"), so nothing here paints anything. initTheme()
// runs from theme-init.ts, which the bundle evaluates before React DOM and the pages, so
// React's first render is already in the right theme. A frame painted before the bundle runs
// at all (a blank page in the Auto colours) can't be avoided: the snapshot's page has no other
// script. The browser chrome (meta theme-color, meta color-scheme) is kept in step.

import { useSyncExternalStore } from "react";

export type ThemeChoice = "auto" | "light" | "dark";
export type Theme = "light" | "dark";

export const THEME_CHOICES: readonly ThemeChoice[] = ["auto", "light", "dark"];

export const THEME_LABELS: Record<ThemeChoice, string> = { auto: "Auto", light: "Light", dark: "Dark" };

const STORAGE_KEY = "footy-theme";
/** Set on <html> by this module: the reader's explicit choice. */
const APP_ATTR = "data-app-theme";
/** Set on <html> by an artifact host, never by this module. */
const HOST_ATTR = "data-theme";
/** Set on <html> for one frame while the theme changes, so no colour transition runs. */
const SWITCHING_ATTR = "data-theme-switching";
const DARK_QUERY = "(prefers-color-scheme: dark)";
/** The app bar's colour in each theme (--appbar-bg in styles.css), used for the browser chrome
 * if the stylesheet can't be read. */
const CHROME: Record<Theme, string> = { light: "#0b3d24", dark: "#0c1510" };

function isChoice(v: unknown): v is ThemeChoice {
  return v === "auto" || v === "light" || v === "dark";
}

/** The saved choice; Auto when nothing is saved or storage can't be read (a private window,
 * blocked site data, a preview). */
function readStored(): ThemeChoice {
  try {
    const v = window.localStorage.getItem(STORAGE_KEY);
    return isChoice(v) ? v : "auto";
  } catch {
    return "auto";
  }
}

/** Saves the choice if storage allows; the page works the same either way. */
function store(choice: ThemeChoice): void {
  try {
    if (choice === "auto") window.localStorage.removeItem(STORAGE_KEY);
    else window.localStorage.setItem(STORAGE_KEY, choice);
  } catch {
    // Storage refused (private window, quota, blocked): the choice lasts until the page closes.
  }
}

let choice: ThemeChoice = "auto";
let resolved: Theme = "light";
let started = false;
const listeners = new Set<() => void>();

function root(): HTMLElement {
  return document.documentElement;
}

/** The host's theme, when the page is shown inside an app that sets one. */
function hostTheme(): Theme | null {
  const v = root().getAttribute(HOST_ATTR);
  return v === "light" || v === "dark" ? v : null;
}

function deviceTheme(): Theme {
  return typeof window.matchMedia === "function" && window.matchMedia(DARK_QUERY).matches ? "dark" : "light";
}

/** The theme on screen: the choice, else the host's, else the device's (as styles.css does). */
function resolve(): Theme {
  return choice !== "auto" ? choice : (hostTheme() ?? deviceTheme());
}

/** meta theme-color (the browser's toolbar on a phone) takes the app bar's colour, and meta
 * color-scheme the theme in force, so the browser's own surfaces match the page. */
function syncChrome(): void {
  let colour = "";
  try {
    colour = getComputedStyle(root()).getPropertyValue("--appbar-bg").trim();
  } catch {
    colour = "";
  }
  if (!colour) colour = CHROME[resolved];
  for (const meta of document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]')) {
    if (meta.content !== colour) meta.content = colour;
  }
  const scheme = document.querySelector<HTMLMetaElement>('meta[name="color-scheme"]');
  const wanted = choice === "auto" && hostTheme() === null ? "light dark" : resolved;
  if (scheme && scheme.content !== wanted) scheme.content = wanted;
}

/** Re-reads what the page shows (after the choice, the host or the device changed) and tells
 * the subscribed components when it differs. */
function refresh(notify: boolean): void {
  resolved = resolve();
  syncChrome();
  if (notify) for (const l of listeners) l();
}

function apply(next: ThemeChoice): void {
  choice = next;
  const el = root();
  if (next === "auto") el.removeAttribute(APP_ATTR);
  else el.setAttribute(APP_ATTR, next);
}

/**
 * Applies the saved choice and starts following the host and the device. Called once, by
 * theme-init.ts, before anything else in the bundle. Safe to call again (does nothing).
 */
export function initTheme(): void {
  if (started || typeof document === "undefined") return;
  started = true;
  apply(readStored());
  refresh(false);

  // Auto follows the device as it changes (an evening switch to dark, a control centre toggle).
  if (typeof window.matchMedia === "function") {
    const mq = window.matchMedia(DARK_QUERY);
    const onChange = () => refresh(true);
    if (typeof mq.addEventListener === "function") mq.addEventListener("change", onChange);
    else mq.addListener?.(onChange);
  }
  // ...and the host, which may change data-theme on <html> while the page is open.
  if (typeof MutationObserver === "function") {
    new MutationObserver(() => refresh(true)).observe(root(), { attributes: true, attributeFilter: [HOST_ATTR] });
  }
  // A choice made in another tab of the same site.
  window.addEventListener("storage", (e) => {
    if (e.key !== STORAGE_KEY && e.key !== null) return;
    const next = readStored();
    if (next === choice) return;
    apply(next);
    refresh(true);
  });
}

/** Sets and saves the reader's choice. The new colours land in one frame, without the
 * transitions some controls have (styles.css, [data-theme-switching]). */
export function setThemeChoice(next: ThemeChoice): void {
  store(next);
  if (next === choice) return;
  const el = root();
  el.setAttribute(SWITCHING_ATTR, "");
  apply(next);
  refresh(true);
  // Let the new colours be computed with transitions off, then turn them back on.
  void el.offsetWidth;
  const done = () => el.removeAttribute(SWITCHING_ATTR);
  if (typeof window.requestAnimationFrame === "function") {
    window.requestAnimationFrame(() => window.requestAnimationFrame(done));
  } else {
    window.setTimeout(done, 50);
  }
}

function subscribe(onChange: () => void): () => void {
  listeners.add(onChange);
  return () => listeners.delete(onChange);
}

/** The reader's choice: "auto", "light" or "dark". */
export function useThemeChoice(): ThemeChoice {
  return useSyncExternalStore(
    subscribe,
    () => choice,
    () => "auto",
  );
}

/** The theme on screen now, "light" or "dark", following the choice, the host and the device.
 * For anything drawn in script that needs to know (the stylesheet handles everything else). */
export function useResolvedTheme(): Theme {
  return useSyncExternalStore(
    subscribe,
    () => resolved,
    () => "light",
  );
}

/** What Auto follows at the moment: "host" when the page is shown inside an app that sets its
 * own theme on <html>, "device" otherwise (the menu's hint says which). */
export function useAutoSource(): "host" | "device" {
  return useSyncExternalStore(
    subscribe,
    () => (hostTheme() ? "host" : "device"),
    () => "device",
  );
}
