// A team's name as a link to its page (#/team/<key>), wherever a team is named.
//
// In a static snapshot only some teams have a page (docs/snapshot.md, "Which teams have
// pages"): a team the copy doesn't hold is printed as plain text, so the copy has no links
// that lead to "Not in this snapshot".

import { useCallback, useSyncExternalStore, type CSSProperties, type ReactNode } from "react";
import { endpoints } from "../api";
import { href } from "../router";
import { SNAPSHOT, snapshotHolds, snapshotStatus, subscribeSnapshotStatus } from "../snapshot";

export function teamPath(key: string): string {
  return `/team/${encodeURIComponent(key)}`;
}

export function teamHref(key: string, query?: Record<string, string | null>): string {
  return href(teamPath(key), query);
}

const noSubscription = () => () => {};
const noStatus = () => null;

/** A test for "this team has a page here": always true in the normal build; in a snapshot,
 * false for a team the copy is known not to hold. Re-renders when snapshot.json loads. */
export function useTeamPageCheck(): (key: string | null | undefined) => boolean {
  const status = useSyncExternalStore(
    SNAPSHOT ? subscribeSnapshotStatus : noSubscription,
    SNAPSHOT ? snapshotStatus : noStatus,
  );
  return useCallback(
    (key: string | null | undefined) => !!key && (!SNAPSHOT || snapshotHolds(endpoints.team(key)) !== false),
    // The status changes once snapshot.json has loaded; the test then gives final answers.
    [status],
  );
}

/** False only when this is a snapshot that is known not to hold the team's page. */
export function useTeamHasPage(key: string | null | undefined): boolean {
  return useTeamPageCheck()(key);
}

export function TeamLink({
  teamKey,
  children,
  className,
  label,
  style,
}: {
  teamKey: string | null | undefined;
  children: ReactNode;
  className?: string;
  /** Accessible name when the visible text alone would be unclear. */
  label?: string;
  style?: CSSProperties;
}) {
  const linked = useTeamHasPage(teamKey);
  if (!teamKey || !linked) {
    return (
      <span className={className} style={style}>
        {children}
      </span>
    );
  }
  return (
    <a className={`team-link${className ? ` ${className}` : ""}`} href={teamHref(teamKey)} aria-label={label} style={style}>
      {children}
    </a>
  );
}
