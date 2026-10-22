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

// ---------------------------------------------------------------------------------------------
// Competition marks: a small tile with a region code and tier (e.g. "ENG 1"), or the cup's
// initials for UEFA competitions. Plain text on a coloured tile; no competition's logo.

export type CompetitionMarkInfo = {
  /** "ENG", "ESP", … or the cup's initials ("UCL", "UEL", "ECL"). */
  code: string;
  /** League tier (1 for top flights), or null for a cup. */
  tier: number | null;
  /** Region key for the tile colour. */
  region: string;
};

const REGIONS: [RegExp, string][] = [
  [/^E\d$/, "ENG"],
  [/^SP\d$/, "ESP"],
  [/^I\d$/, "ITA"],
  [/^D\d$/, "GER"],
  [/^F\d$/, "FRA"],
  [/^SC\d$/, "SCO"],
  [/^N\d$/, "NED"],
  [/^B\d$/, "BEL"],
  [/^P\d$/, "POR"],
  [/^T\d$/, "TUR"],
  [/^G\d$/, "GRE"],
];

const CUP_MARKS: Record<string, string> = {
  UCL: "UCL",
  UEL: "UEL",
  UECL: "ECL",
  UCLQ: "UCL",
  UELQ: "UEL",
  UECLQ: "ECL",
};

const OTHER_REGIONS: Record<string, string> = { SWZ: "SUI", DNK: "DEN" };

export function competitionMark(code: string): CompetitionMarkInfo {
  const cup = CUP_MARKS[code];
  if (cup) return { code: cup, tier: null, region: "EUR" };
  for (const [re, region] of REGIONS) {
    if (re.test(code)) {
      // football-data numbers England's top flight 0 and every other country's 1.
      const n = Number.parseInt(code.replace(/^\D+/, ""), 10);
      const tier = region === "ENG" ? n + 1 : region === "SCO" ? n + 1 : n;
      return { code: region, tier: Number.isFinite(tier) ? tier : 1, region };
    }
  }
  const region = OTHER_REGIONS[code] ?? code.slice(0, 3).toUpperCase();
  return { code: region, tier: 1, region };
}
