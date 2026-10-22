// Loading, empty, error and stale states, and ApiView, which picks between them.

import type { ReactNode } from "react";
import type { ApiError, ApiResult } from "../api";
import { formatLondon } from "../format";
import { href } from "../router";
import { NOT_IN_SNAPSHOT, SNAPSHOT } from "../snapshot";

export function Loading({ label = "Loading…", lines = 3 }: { label?: string; lines?: number }) {
  return (
    <div className="state state-loading" role="status" aria-live="polite">
      <span className="visually-hidden">{label}</span>
      <div className="skeleton-group" aria-hidden="true">
        {Array.from({ length: lines }, (_, i) => (
          <div key={i} className="skeleton card">
            <span className="sk sk-chip" />
            <span className="sk sk-title" />
            <span className="sk sk-line" />
            <span className="sk sk-bar" />
            <span className="sk sk-line sk-short" />
          </div>
        ))}
      </div>
    </div>
  );
}

/** A simple line drawing for empty states: a goal frame with its net. */
function EmptyArt() {
  return (
    <svg className="state-art" width="72" height="48" viewBox="0 0 72 48" aria-hidden="true" focusable="false">
      <path className="state-art-net" d="M14 10h44M14 18h44M14 26h44M14 34h44M22 6v34M30 6v34M38 6v34M46 6v34M54 6v34" />
      <path className="state-art-frame" d="M10 42V6h52v36" />
      <path className="state-art-line" d="M2 42h68" />
      <circle className="state-art-ball" cx="36" cy="42" r="3" />
    </svg>
  );
}

export function Empty({
  title,
  children,
  actions,
}: {
  title: ReactNode;
  children?: ReactNode;
  /** Buttons or links to go on from here, in a row under the text. */
  actions?: ReactNode;
}) {
  return (
    <section className="card state state-empty">
      <EmptyArt />
      <h2>{title}</h2>
      {children && <div className="state-body">{children}</div>}
      {actions && <div className="state-actions">{actions}</div>}
    </section>
  );
}

/** A link styled as a button, to another page of the app. */
export function ButtonLink({ to, primary = false, children }: { to: string; primary?: boolean; children: ReactNode }) {
  return (
    <a className={`button${primary ? " button-primary" : ""}`} href={href(to)}>
      {children}
    </a>
  );
}

/** True for a snapshot request the snapshot doesn't hold: trying again can never help. */
export function notInSnapshot(error: ApiError | Error): boolean {
  return SNAPSHOT && error.message === NOT_IN_SNAPSHOT;
}

/** Where a page sends you when the snapshot doesn't hold what it asked for. */
export type WayOn = { to: string; label: string };

const TO_FIXTURES: WayOn = { to: "/fixtures", label: "Go to fixtures" };

export function ErrorView({
  error,
  what,
  onRetry,
  retrying = false,
  wayOn = TO_FIXTURES,
}: {
  error: ApiError | Error;
  /** What failed to load, e.g. "fixtures". */
  what: string;
  onRetry?: () => void;
  retrying?: boolean;
  /** The way on from "Not in this snapshot" (the fixtures by default). */
  wayOn?: WayOn;
}) {
  // In a snapshot, a page it doesn't hold is not a failure to retry: say so, and offer a way on.
  if (notInSnapshot(error)) {
    return (
      <Empty
        title="Not in this snapshot"
        actions={
          <>
            <ButtonLink to={wayOn.to} primary>
              {wayOn.label}
            </ButtonLink>
            {wayOn.to !== TO_FIXTURES.to && <ButtonLink to={TO_FIXTURES.to}>{TO_FIXTURES.label}</ButtonLink>}
          </>
        }
      >
        <p>
          {what.charAt(0).toUpperCase() + what.slice(1)} {what.endsWith("s") ? "aren't" : "isn't"} in
          this read-only copy, which holds only what the site could show when it was taken.
        </p>
      </Empty>
    );
  }
  return (
    <section className="card state state-error" role="alert">
      <span className="state-icon" aria-hidden="true">
        !
      </span>
      <h2>Couldn't load {what}</h2>
      <p>{error.message}</p>
      {onRetry && (
        <button type="button" className="button-primary" onClick={onRetry} disabled={retrying}>
          {retrying ? "Trying again…" : "Try again"}
        </button>
      )}
    </section>
  );
}

/** Shown above data that may be out of date. */
export function StaleNotice({
  children,
  onRetry,
  retrying = false,
}: {
  children: ReactNode;
  onRetry?: () => void;
  retrying?: boolean;
}) {
  return (
    <div className="notice-stale" role="status">
      <span className="notice-icon" aria-hidden="true">
        !
      </span>
      <div className="notice-text">{children}</div>
      {onRetry && (
        <button type="button" onClick={onRetry} disabled={retrying}>
          {retrying ? "Refreshing…" : "Refresh"}
        </button>
      )}
    </div>
  );
}

/**
 * Renders the right state for an API result:
 * - nothing yet and loading: skeleton;
 * - nothing and an error: the error with Try again;
 * - data and a failed refresh: the data under a "saved copy" notice;
 * - data: `children(data)`, dimmed slightly while a refresh is running.
 * `isEmpty` + `empty` show the empty state for data that has nothing in it.
 */
export function ApiView<T>({
  result,
  what,
  children,
  isEmpty,
  empty,
  loadingLines,
  wayOn,
}: {
  result: ApiResult<T>;
  what: string;
  children: (data: T) => ReactNode;
  isEmpty?: (data: T) => boolean;
  empty?: ReactNode;
  loadingLines?: number;
  /** See ErrorView. */
  wayOn?: WayOn;
}) {
  const { data, error, loading, receivedAt, reload } = result;
  if (data === undefined) {
    if (error) return <ErrorView error={error} what={what} onRetry={reload} retrying={loading} wayOn={wayOn} />;
    return <Loading label={`Loading ${what}…`} lines={loadingLines} />;
  }
  return (
    <>
      {error && (
        <StaleNotice onRetry={reload} retrying={loading}>
          Showing the copy loaded at{" "}
          {receivedAt ? formatLondon(new Date(receivedAt).toISOString()) : "an earlier time"}.
          Refreshing failed: {error.message}
        </StaleNotice>
      )}
      <div className={`api-view${loading ? " refreshing" : ""}`} aria-busy={loading || undefined}>
        {isEmpty?.(data) ? empty : children(data)}
      </div>
    </>
  );
}
