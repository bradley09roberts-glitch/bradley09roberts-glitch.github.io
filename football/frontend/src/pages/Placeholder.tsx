// Stand-in for a page that hasn't been built yet. It shows no numbers.

import { useLayoutEffect, type ComponentType } from "react";
import { ButtonLink, Empty } from "../components/StateViews";
import { href, navigate } from "../router";
import type { PageProps } from ".";

export function placeholder(title: string): ComponentType<PageProps> {
  function PlaceholderPage() {
    return (
      <>
        <header className="page-head">
          <h1 className="page-title">{title}</h1>
        </header>
        <section className="card notice">
          <h2>Not built yet</h2>
          <p>
            This page is still being written. Nothing is shown here until it comes from saved
            records. Upcoming predictions are on the <a href={href("/fixtures")}>Fixtures</a> page.
          </p>
        </section>
      </>
    );
  }
  PlaceholderPage.displayName = `Placeholder(${title})`;
  return PlaceholderPage;
}

export function NotFound({ path }: { path: string }) {
  return (
    <>
      <header className="page-head">
        <h1 className="page-title">Page not found</h1>
        <p className="lede">The address may be mistyped, or the page may have moved.</p>
      </header>
      <Empty
        title="Nothing at this address"
        actions={
          <>
            <ButtonLink to="/fixtures" primary>
              Fixtures
            </ButtonLink>
            <ButtonLink to="/past">Past matches</ButtonLink>
            <ButtonLink to="/performance">Performance</ButtonLink>
          </>
        }
      >
        <p>
          There's no page at <code>{path}</code>. Upcoming predictions are on Fixtures; settled
          ones and the track record are on Past matches and Performance.
        </p>
      </Empty>
    </>
  );
}

/** A page that has moved: replaces the address with its new one. */
export function redirectTo(to: string): ComponentType<PageProps> {
  function Redirect() {
    useLayoutEffect(() => {
      navigate(to, { replace: true });
    }, []);
    return null;
  }
  Redirect.displayName = `Redirect(${to})`;
  return Redirect;
}
