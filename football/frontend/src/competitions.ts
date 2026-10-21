// Display order for competitions: European cups, then the big five, then second tiers, then
// everything else in the order the API sent it.

export const COMPETITION_PRIORITY = [
  "UCL",
  "UEL",
  "UECL",
  "E0",
  "SP1",
  "I1",
  "D1",
  "F1",
  "E1",
  "SP2",
  "I2",
  "D2",
  "F2",
] as const;

export function competitionRank(code: string): number {
  const i = (COMPETITION_PRIORITY as readonly string[]).indexOf(code);
  return i < 0 ? COMPETITION_PRIORITY.length : i;
}

/** A stable sort by priority; unknown codes keep their incoming order after the known ones. */
export function sortByCompetition<T>(items: readonly T[], code: (item: T) => string): T[] {
  return items
    .map((item, index) => ({ item, index, rank: competitionRank(code(item)) }))
    .sort((a, b) => a.rank - b.rank || a.index - b.index)
    .map((x) => x.item);
}

export const EUROPEAN_CUPS = new Set(["UCL", "UEL", "UECL"]);
