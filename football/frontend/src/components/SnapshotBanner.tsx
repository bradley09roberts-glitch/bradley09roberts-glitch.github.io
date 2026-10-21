// The note above every page in a snapshot build (src/snapshot.ts): when the copy was taken,
// and that it doesn't update by itself. Never rendered in the normal app. Styled inline with
// the site's own tokens, so the normal build's stylesheet is unchanged.

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
    <div style={{ maxWidth: 760, margin: "0 auto", padding: "12px 16px 0" }}>
      <div className="notice-info small" role="note">
        <strong>
          {status.state === "loaded"
            ? `Snapshot taken ${formatLondon(status.takenAt)} UK time.`
            : status.state === "failed"
              ? "Snapshot: its data couldn't be loaded."
              : "Snapshot."}
        </strong>{" "}
        Read-only copy; predictions and results update when the snapshot is refreshed.
      </div>
    </div>
  );
}
