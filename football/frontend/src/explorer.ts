// Links into the lab's backtest explorer (pages/LabExplorer.tsx) from other pages.
//
// The explorer always loads one division and season at a time (the static snapshot holds
// exactly those requests, docs/snapshot.md) and filters by team itself. A link from a team page
// therefore names a division and season the team played in; the explorer handles one it has no
// backtest for by saying so.

import type { TeamProfile } from "./api";
import { href } from "./router";

/** The explorer's own query parameters (cleared when the lab moves to or from its tab). */
export const EXPLORER_KEYS = ["division", "season", "team", "models", "page", "order"] as const;

export function explorerHref(q: { division?: string; season?: string; team?: string } = {}): string {
  return href("/lab", { tab: "explorer", division: q.division, season: q.season, team: q.team });
}

/** What the saved backtests cover (/api/backtest-models `coverage`, 22 October 2026): the five
 * leagues and the three UEFA competitions in 2023/24 (validation), 2024/25 and 2025/26 (test),
 * the UEFA qualifying rounds in the two test seasons. Only used to choose links. */
const LEAGUES = ["E0", "SP1", "I1", "D1", "F1"];
const UEFA = ["UCL", "UEL", "UECL"];
const UEFA_QUALIFYING = ["UCLQ", "UELQ", "UECLQ"];
const SEASONS = ["2526", "2425", "2324"];

function covered(division: string, season: string): boolean {
  if (LEAGUES.includes(division) || UEFA.includes(division)) return SEASONS.includes(season);
  if (UEFA_QUALIFYING.includes(division)) return season === "2526" || season === "2425";
  return false;
}

export type ExplorerTarget = { division: string; name: string; season: string };

/** Every division and season with backtest predictions the team played in: newest season
 * first, its league before European competitions. */
export function teamExplorerTargets(p: TeamProfile): ExplorerTarget[] {
  const out: (ExplorerTarget & { rank: number })[] = [];
  for (const c of p.by_competition) {
    const rank = LEAGUES.includes(c.competition) ? 0 : UEFA.includes(c.competition) ? 1 : 2;
    for (const season of c.seasons) {
      if (covered(c.competition, season)) out.push({ division: c.competition, name: c.name, season, rank });
    }
  }
  out.sort((a, b) => b.season.localeCompare(a.season) || a.rank - b.rank || a.division.localeCompare(b.division));
  return out.map(({ division, name, season }) => ({ division, name, season }));
}
