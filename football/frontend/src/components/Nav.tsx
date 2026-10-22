// Main navigation, built from the page registry (src/pages/index.ts). On a phone it is a
// tab bar along the bottom of the screen (clear of the home indicator, via the safe-area
// inset) with five items at most: a page marked `tabBar: false` is linked from the app bar
// instead (AppBarLinks). From 720 px wide every item sits in the top bar beside the wordmark.

import { Component, type ErrorInfo, type MouseEvent, type ReactNode } from "react";
import type { PageDef } from "../pages";
import { goBack, href } from "../router";
import { Icon } from "./Svg";

type NavPage = PageDef & { nav: NonNullable<PageDef["nav"]> };

function navItems(pages: readonly PageDef[]): NavPage[] {
  return pages.filter((p): p is NavPage => p.nav !== undefined).sort((a, b) => a.nav.order - b.nav.order);
}

export function Nav({ pages, active }: { pages: readonly PageDef[]; active: string | null }) {
  const items = navItems(pages);
  return (
    <nav className="nav" aria-label="Main">
      <ul>
        {items.map((p) => {
          const current = p.path === active;
          return (
            <li key={p.path} className={p.nav.tabBar === false ? "nav-off-tabbar" : undefined}>
              <a href={href(p.path)} aria-current={current ? "page" : undefined} aria-label={p.nav.label}>
                <span className="nav-icon">
                  <Icon name={p.nav.icon ?? "dot"} />
                </span>
                <span className="nav-long" aria-hidden="true">
                  {p.nav.label}
                </span>
                <span className="nav-short" aria-hidden="true">
                  {p.nav.short ?? p.nav.label}
                </span>
              </a>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/** Phone only (hidden from 720 px by styles.css): the pages kept off the tab bar, as small
 * links at the end of the app bar. */
export function AppBarLinks({ pages, active }: { pages: readonly PageDef[]; active: string | null }) {
  const items = navItems(pages).filter((p) => p.nav.tabBar === false);
  if (items.length === 0) return null;
  return (
    <nav className="appbar-links" aria-label="More">
      {items.map((p) => (
        <a key={p.path} href={href(p.path)} aria-current={p.path === active ? "page" : undefined} aria-label={p.nav.label}>
          <Icon name={p.nav.icon ?? "dot"} size={18} />
          <span aria-hidden="true">{p.nav.short ?? p.nav.label}</span>
        </a>
      ))}
    </nav>
  );
}

/** "← Fixtures": goes Back when the previous page was in the app, otherwise to `to`. */
export function BackLink({ to, children }: { to: string; children: ReactNode }) {
  const onClick = (e: MouseEvent<HTMLAnchorElement>) => {
    if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
    e.preventDefault();
    goBack(to);
  };
  return (
    <a className="back-link" href={href(to)} onClick={onClick}>
      <span aria-hidden="true">← </span>
      {children}
    </a>
  );
}

/** Keeps one page's rendering error from blanking the whole app. */
export class PageErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state: { error: Error | null } = { error: null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Page failed to render", error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <section className="card state state-error" role="alert">
          <span className="state-icon" aria-hidden="true">
            !
          </span>
          <h2>This page couldn't be shown</h2>
          <p>
            Something in the data didn't match what the page expects: {this.state.error.message}
          </p>
          <button type="button" className="button-primary" onClick={() => this.setState({ error: null })}>
            Try again
          </button>
        </section>
      );
    }
    return this.props.children;
  }
}
