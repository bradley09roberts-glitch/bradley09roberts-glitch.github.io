import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { Nav, PageErrorBoundary } from "./components/Nav";
import { SITE_NAME } from "./hooks";
import { pages } from "./pages";
import { NotFound } from "./pages/Placeholder";
import { href, resolve, savedScrollPosition, useLocation } from "./router";

export function App() {
  const location = useLocation();
  const match = resolve(pages, location.path);
  const page = match?.route;
  // A page outside the navigation (a match) highlights the section the reader came from, e.g.
  // Past matches; opened directly, it highlights its own `section`.
  const [lastSection, setLastSection] = useState<string | null>(null);
  const navPath = page?.nav ? page.path : null;
  useEffect(() => {
    if (navPath) setLastSection(navPath);
  }, [navPath]);
  const active = page ? (navPath ?? lastSection ?? page.section ?? null) : null;
  const main = useRef<HTMLElement>(null);
  const shownPath = useRef(location.path);

  // Each history entry keeps its own scroll position; a new page starts at the top.
  useLayoutEffect(() => {
    window.scrollTo(0, savedScrollPosition());
  }, [location.hash]);

  // Move focus to the page on navigation, so keyboard and screen-reader users start there.
  useEffect(() => {
    if (shownPath.current === location.path) return;
    shownPath.current = location.path;
    main.current?.focus({ preventScroll: true });
  }, [location.path]);

  // A layout effect runs before the page's own useDocumentTitle, which can then refine it.
  useLayoutEffect(() => {
    document.title = page ? `${page.title} · ${SITE_NAME}` : `Not found · ${SITE_NAME}`;
  }, [page]);

  const Page = page?.component;

  return (
    <>
      <header className="bar">
        <a className="brand" href={href("/fixtures")}>
          <span className="brand-mark" aria-hidden="true" />
          {SITE_NAME}
        </a>
        <Nav pages={pages} active={active} />
      </header>
      <main className="page" ref={main} tabIndex={-1}>
        <PageErrorBoundary key={location.path}>
          {Page && match ? (
            <Page key={location.path} params={match.params} query={location.query} />
          ) : (
            <NotFound path={location.path} />
          )}
        </PageErrorBoundary>
      </main>
      <footer className="site-foot muted small">
        Probabilities are model estimates, not advice. Times are UK time (Europe/London).
      </footer>
    </>
  );
}
