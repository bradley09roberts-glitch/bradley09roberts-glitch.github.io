// The note above every page in a snapshot build (src/snapshot.ts): when the copy was taken,
// and that it doesn't update by itself. Never rendered in the normal app (its styles in
// styles.css are unused there).

import { useEffect, useSyncExternalStore } from "react";
import { formatLondon } from "../format";
import { snapshotStatus, startSnapshotLoad, subscribeSnapshotStatus } from "../snapshot";

export function SnapshotBanner() {
  // Follows every load attempt: after a failed load, a page's "Try again" that succeeds
  // replaces the failure note with the time the snapshot was taken.
  const status = useSyncExternalStore(subscribeSnapshotStatus, snapshotStatus);

  useEffect(() => {
    startSnapshotLoad();
  }, []);

  return (
    <div className="snapshot-strip">
      <div className="snapshot-strip-inner" role="note">
        <span className="snapshot-dot" aria-hidden="true" />
        <strong>
          {status.state === "loaded"
            ? `Snapshot taken ${formatLondon(status.takenAt)} UK time.`
            : status.state === "failed"
              ? "Snapshot: its data couldn't be loaded."
              : "Snapshot."}
        </strong>{" "}
        <span>Read-only copy; predictions and results update when the snapshot is refreshed.</span>
      </div>
    </div>
  );
}
