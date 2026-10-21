// Stand-in for a page that hasn't been built yet. It shows no numbers.

import type { ComponentType } from "react";
import { href } from "../router";
import type { PageProps } from ".";

export function placeholder(title: string): ComponentType<PageProps> {
  function PlaceholderPage() {
    return (
      <>
        <h1 className="page-title">{title}</h1>
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
      <h1 className="page-title">Page not found</h1>
      <section className="card">
        <p>
          There's no page at <code>{path}</code>. Try the <a href={href("/fixtures")}>fixtures</a>.
        </p>
      </section>
    </>
  );
}
