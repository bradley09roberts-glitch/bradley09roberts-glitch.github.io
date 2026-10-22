// A competition's small mark (region code and tier, or a UEFA cup's initials, with "Q" for its
// qualifying rounds) and the chip that pairs it with the competition's name. The name is always
// printed, so the mark is decorative.

import { competitionMark } from "../competitions";

export function CompetitionMark({ code }: { code: string }) {
  const m = competitionMark(code);
  return (
    <span className={`comp-mark comp-mark-${m.region.toLowerCase()}`} aria-hidden="true">
      <span className="comp-mark-code">{m.code}</span>
      {m.tier !== null ? (
        <span className="comp-mark-tier">{m.tier}</span>
      ) : (
        m.qualifying && <span className="comp-mark-tier comp-mark-q">Q</span>
      )}
    </span>
  );
}

export function CompetitionChip({ code, name, detail }: { code: string; name: string; detail?: string | null }) {
  return (
    <span className="comp-chip">
      <CompetitionMark code={code} />
      <span className="comp-chip-name">{name}</span>
      {detail && <span className="comp-chip-detail">{detail}</span>}
    </span>
  );
}
