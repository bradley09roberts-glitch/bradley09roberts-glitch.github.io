// The note at the top of every page in a snapshot build (src/snapshot.ts): when the copy was
// taken, and that it doesn't update by itself. Never rendered in the normal app.

import { useEffect, useState } from "react";
import { formatLondon } from "../format";
import { snapshotTakenAt } from "../snapshot";

export function SnapshotBanner() {
  const [takenAt, setTakenAt] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let live = true;
    snapshotTakenAt().then(
      (t) => {
        if (live) setTakenAt(t);
      },
      () => {
        if (live) setFailed(true);
      },
    );
    return () => {
      live = false;
    };
  }, []);

  return (
    <div className="notice-info small" role="note">
      <strong>
        {takenAt
          ? `Snapshot taken ${formatLondon(takenAt)} UK time.`
          : failed
            ? "Snapshot: its data couldn't be loaded."
            : "Snapshot."}
      </strong>{" "}
      Read-only copy; predictions and results update when the snapshot is refreshed.
    </div>
  );
}
