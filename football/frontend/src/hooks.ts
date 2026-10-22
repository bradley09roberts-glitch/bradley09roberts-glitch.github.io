import { useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore, type RefObject } from "react";

export const SITE_NAME = "Football model";

/** Sets the browser tab title while the calling page is shown. */
export function useDocumentTitle(title: string | null | undefined): void {
  useEffect(() => {
    if (!title) return;
    document.title = `${title} · ${SITE_NAME}`;
  }, [title]);
}

// ---------------------------------------------------------------------------------------------
// Reveal on first view: a chart (e.g. a probability bar) starts empty and fills once, the first
// time it scrolls into view. Without IntersectionObserver, or when the reader prefers reduced
// motion, it is simply shown full: the "pending" state is only ever set by this hook.

export type RevealState = "shown" | "pending";

const watchers = new WeakMap<Element, () => void>();
let observer: IntersectionObserver | null = null;

function watch(el: Element, onView: () => void): () => void {
  if (!observer) {
    observer = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (!e.isIntersecting) continue;
          const cb = watchers.get(e.target);
          watchers.delete(e.target);
          observer?.unobserve(e.target);
          cb?.();
        }
      },
      { threshold: 0.25 },
    );
  }
  watchers.set(el, onView);
  observer.observe(el);
  return () => {
    watchers.delete(el);
    observer?.unobserve(el);
  };
}

function prefersReducedMotion(): boolean {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function useReveal<T extends Element>(): [RefObject<T | null>, RevealState] {
  const ref = useRef<T>(null);
  const [state, setState] = useState<RevealState>("shown");
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el || typeof IntersectionObserver === "undefined" || prefersReducedMotion()) return;
    setState("pending");
    const stop = watch(el, () => setState("shown"));
    return () => {
      stop();
      setState("shown");
    };
  }, []);
  return [ref, state];
}

// ---------------------------------------------------------------------------------------------
// Media queries

/** True while the media query matches; follows changes (rotation, resizing). */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      if (typeof window.matchMedia !== "function") return () => {};
      const mq = window.matchMedia(query);
      mq.addEventListener("change", onChange);
      return () => mq.removeEventListener("change", onChange);
    },
    () => typeof window.matchMedia === "function" && window.matchMedia(query).matches,
    () => false,
  );
}

// ---------------------------------------------------------------------------------------------
// Element width

/** True while the element is narrower than `px` (its border box), following resizes; false
 * until it has been measured. For layouts decided by a container query on a box the element
 * fills, so the script and the stylesheet agree on the same width. */
export function useNarrowerThan<T extends Element>(ref: RefObject<T | null>, px: number): boolean {
  const [narrow, setNarrow] = useState(false);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = (width: number) => setNarrow(width > 0 && width < px);
    measure(el.getBoundingClientRect().width);
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver((entries) => {
      const entry = entries[entries.length - 1];
      const box = entry?.borderBoxSize?.[0];
      measure(box ? box.inlineSize : el.getBoundingClientRect().width);
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [ref, px]);
  return narrow;
}

// ---------------------------------------------------------------------------------------------
// The app bar on a phone: it slides away while the reader scrolls down and comes back as soon
// as they scroll up, reach the top, move keyboard focus into it, or change page. Only the bar
// itself hides: the strip under the iPhone status bar stays (styles.css, html[data-appbar]).

const PHONE = "(max-width: 719px)";
/** Scrolling less than this (px) in one direction changes nothing. */
const HIDE_AFTER = 12;
/** Near the top of the page the bar is always shown. */
const ALWAYS_NEAR_TOP = 96;

export function useAutoHideAppBar(header: RefObject<HTMLElement | null>): void {
  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const mq = window.matchMedia(PHONE);
    const root = document.documentElement;
    let hidden = false;
    let anchor = window.scrollY;
    let frame = 0;
    const set = (h: boolean) => {
      if (h === hidden) return;
      hidden = h;
      if (h) root.dataset.appbar = "hidden";
      else delete root.dataset.appbar;
    };
    const update = () => {
      frame = 0;
      const y = Math.max(0, window.scrollY);
      if (!mq.matches || y < ALWAYS_NEAR_TOP) {
        set(false);
        anchor = y;
        return;
      }
      const dy = y - anchor;
      if (dy > HIDE_AFTER) {
        set(true);
        anchor = y;
      } else if (dy < -HIDE_AFTER) {
        set(false);
        anchor = y;
      } else if ((hidden && dy > 0) || (!hidden && dy < 0)) {
        // Still moving the same way: follow, so a reversal is measured from here.
        anchor = y;
      }
    };
    const onScroll = () => {
      if (!frame) frame = window.requestAnimationFrame(update);
    };
    const show = () => {
      set(false);
      anchor = Math.max(0, window.scrollY);
    };
    const el = header.current;
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("hashchange", show);
    mq.addEventListener("change", show);
    el?.addEventListener("focusin", show);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("hashchange", show);
      mq.removeEventListener("change", show);
      el?.removeEventListener("focusin", show);
      if (frame) window.cancelAnimationFrame(frame);
      delete root.dataset.appbar;
    };
  }, [header]);
}
