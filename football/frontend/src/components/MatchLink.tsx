// A link to a saved record's match page (#/match/<id>).
//
// A static snapshot keeps every model's pages only for recent fixtures and, before that, each
// fixture's record of record (docs/snapshot.md, "Which match pages"). A page the copy doesn't
// hold is printed as plain text, so the copy has no links that lead to "Not in this snapshot".

import { useCallback, useSyncExternalStore, type ReactNode } from "react";
import { endpoints } from "../api";
import { href } from "../router";
import { SNAPSHOT, snapshotHolds, snapshotStatus, subscribeSnapshotStatus } from "../snapshot";

const noSubscription = () => () => {};
const noStatus = () => null;

/** A test for "this record has a page here": always true in the normal build; in a snapshot,
 * false for a record the copy is known not to hold. Re-renders when snapshot.json loads. */
export function useMatchPageCheck(): (id: number | null | undefined) => boolean {
  const status = useSyncExternalStore(
    SNAPSHOT ? subscribeSnapshotStatus : noSubscription,
    SNAPSHOT ? snapshotStatus : noStatus,
  );
  return useCallback(
    (id: number | null | undefined) =>
      id !== null && id !== undefined && (!SNAPSHOT || snapshotHolds(endpoints.prediction(id)) !== false),
    // The status changes once snapshot.json has loaded; the test then gives final answers.
    [status],
  );
}

export function MatchLink({
  id,
  children,
  className,
  label,
  query,
  title,
}: {
  id: number;
  children: ReactNode;
  className?: string;
  /** Accessible name when the visible text alone would be unclear. */
  label?: string;
  query?: Record<string, string | null>;
  title?: string;
}) {
  const linked = useMatchPageCheck()(id);
  if (!linked) return <span className={className}>{children}</span>;
  return (
    <a className={className} href={href(`/match/${id}`, query)} aria-label={label} title={title}>
      {children}
    </a>
  );
}
