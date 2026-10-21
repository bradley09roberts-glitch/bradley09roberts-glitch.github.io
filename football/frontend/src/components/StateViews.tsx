// Loading, empty, error and stale states, and ApiView, which picks between them.

import type { ReactNode } from "react";
import type { ApiError, ApiResult } from "../api";
import { formatLondon } from "../format";

export function Loading({ label = "Loading…", lines = 3 }: { label?: string; lines?: number }) {
  return (
    <div className="state state-loading" role="status" aria-live="polite">
      <span className="visually-hidden">{label}</span>
      <div className="skeleton-group" aria-hidden="true">
        {Array.from({ length: lines }, (_, i) => (
          <div key={i} className="skeleton card" />
        ))}
      </div>
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <section className="card state state-empty">
      <h2>{title}</h2>
      {children && <div className="state-body">{children}</div>}
    </section>
  );
}

export function ErrorView({
  error,
  what,
  onRetry,
  retrying = false,
}: {
  error: ApiError | Error;
  /** What failed to load, e.g. "fixtures". */
  what: string;
  onRetry?: () => void;
  retrying?: boolean;
}) {
  return (
    <section className="card state state-error" role="alert">
      <h2>Couldn't load {what}</h2>
      <p>{error.message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} disabled={retrying}>
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
}: {
  result: ApiResult<T>;
  what: string;
  children: (data: T) => ReactNode;
  isEmpty?: (data: T) => boolean;
  empty?: ReactNode;
  loadingLines?: number;
}) {
  const { data, error, loading, receivedAt, reload } = result;
  if (data === undefined) {
    if (error) return <ErrorView error={error} what={what} onRetry={reload} retrying={loading} />;
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
