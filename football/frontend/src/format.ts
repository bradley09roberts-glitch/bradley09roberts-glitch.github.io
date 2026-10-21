// Display formatting. Every timestamp is stored in UTC (D-005) and shown in UK time.

export const UK_TIME_ZONE = "Europe/London";

/** Parse an API timestamp. A string without an offset is UTC (D-005), never local time. */
export function parseUtc(iso: string): Date {
  // Python's isoformat() can send microseconds; keep milliseconds, which every browser parses.
  let s = iso.trim().replace(" ", "T").replace(/(\.\d{3})\d+/, "$1");
  if (/T\d{2}:\d{2}/.test(s) && !/(Z|[+-]\d{2}:?\d{2})$/.test(s)) s += "Z";
  return new Date(s);
}

/** A calendar date ("2026-10-21") as a Date at midday UTC, so it is the same day in the UK. */
function parseDay(isoDate: string): Date {
  return new Date(`${isoDate.slice(0, 10)}T12:00:00Z`);
}

const dateTime = new Intl.DateTimeFormat("en-GB", {
  timeZone: UK_TIME_ZONE,
  dateStyle: "medium",
  timeStyle: "short",
});
const dateOnly = new Intl.DateTimeFormat("en-GB", { timeZone: UK_TIME_ZONE, dateStyle: "medium" });
const shortDay = new Intl.DateTimeFormat("en-GB", {
  timeZone: UK_TIME_ZONE,
  weekday: "short",
  day: "numeric",
  month: "short",
});
const longDay = new Intl.DateTimeFormat("en-GB", {
  timeZone: UK_TIME_ZONE,
  weekday: "long",
  day: "numeric",
  month: "long",
});
const longDayYear = new Intl.DateTimeFormat("en-GB", {
  timeZone: UK_TIME_ZONE,
  weekday: "long",
  day: "numeric",
  month: "long",
  year: "numeric",
});
const timeOnly = new Intl.DateTimeFormat("en-GB", {
  timeZone: UK_TIME_ZONE,
  hour: "2-digit",
  minute: "2-digit",
});
const dayKey = new Intl.DateTimeFormat("en-CA", {
  timeZone: UK_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

function valid(d: Date): boolean {
  return !Number.isNaN(d.getTime());
}

/** "21 Oct 2026, 17:45" in UK time. */
export function formatLondon(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = parseUtc(iso);
  return valid(d) ? dateTime.format(d) : iso;
}

/** "21 Oct 2026" for a calendar date. */
export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return "—";
  const d = parseDay(isoDate);
  return valid(d) ? dateOnly.format(d) : isoDate;
}

/** "Tue 21 Oct" for a calendar date. */
export function formatShortDay(isoDate: string): string {
  const d = parseDay(isoDate);
  return valid(d) ? shortDay.format(d) : isoDate;
}

/** "17:45" UK time. */
export function formatTime(iso: string): string {
  const d = parseUtc(iso);
  return valid(d) ? timeOnly.format(d) : "—";
}

/** The UK calendar day of a kickoff (or the match date when the time isn't known). */
export function londonDayKey(kickoffUtc: string | null, matchDate: string): string {
  if (!kickoffUtc) return matchDate.slice(0, 10);
  const d = parseUtc(kickoffUtc);
  return valid(d) ? dayKey.format(d) : matchDate.slice(0, 10);
}

/** "Tuesday 21 October". */
export function formatDayHeading(isoDate: string): string {
  const d = parseDay(isoDate);
  return valid(d) ? longDay.format(d) : isoDate;
}

/** "Tuesday 21 October 2026", for lists that can span seasons. */
export function formatDayHeadingWithYear(isoDate: string): string {
  const d = parseDay(isoDate);
  return valid(d) ? longDayYear.format(d) : isoDate;
}

/** Display order for market keys: match result, goal lines from lowest, both teams to score,
 * then anything else. */
export function marketOrder(market: string): number {
  if (market === "1X2") return 0;
  if (market.startsWith("OU_")) return 10 + totalLine(market);
  if (market === "BTTS") return 100;
  return 200;
}

/** "Tue 21 Oct, 17:45", or "Tue 21 Oct, time not yet known". */
export function formatKickoff(kickoffUtc: string | null, matchDate: string): string {
  if (!kickoffUtc) return `${formatShortDay(matchDate)}, time not yet known`;
  const d = parseUtc(kickoffUtc);
  if (!valid(d)) return formatShortDay(matchDate);
  return `${shortDay.format(d)}, ${timeOnly.format(d)}`;
}

/** 0.523 -> "52%". Values under 1% show as "<1%" so a real chance never reads as zero. */
export function formatPct(p: number | null | undefined, digits = 0): string {
  if (p === null || p === undefined || !Number.isFinite(p)) return "—";
  const pct = p * 100;
  if (digits === 0 && pct > 0 && pct < 0.5) return "<1%";
  if (digits === 0 && pct < 100 && pct > 99.5) return ">99%";
  return `${pct.toFixed(digits)}%`;
}

export function formatNumber(n: number | null | undefined, digits?: number): string {
  if (n === null || n === undefined || !Number.isFinite(n)) return "—";
  return n.toLocaleString("en-GB", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits ?? 3,
  });
}

const gbp = new Intl.NumberFormat("en-GB", { style: "currency", currency: "GBP" });

/** Money is always pounds sterling. */
export function formatGBP(n: number | null | undefined): string {
  if (n === null || n === undefined || !Number.isFinite(n)) return "—";
  return gbp.format(n);
}

/** Stored season key to display: "2627" -> "2026/27"; a calendar season "2424" -> "2024". */
export function seasonLabel(key: string): string {
  if (!/^\d{4}$/.test(key)) return key;
  const a = key.slice(0, 2);
  const b = key.slice(2, 4);
  if (a === b) return `20${a}`;
  return `20${a}/${b}`;
}

/** Whole days from a to b (b later is positive). */
export function daysBetween(a: Date, b: Date): number {
  return Math.floor((b.getTime() - a.getTime()) / 86_400_000);
}

const relative = new Intl.RelativeTimeFormat("en-GB", { numeric: "auto" });

/** "3 hours ago", "yesterday", "in 2 days". */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const d = parseUtc(iso);
  if (!valid(d)) return iso;
  const seconds = (d.getTime() - now.getTime()) / 1000;
  const abs = Math.abs(seconds);
  if (abs < 60) return "just now";
  if (abs < 3600) return relative.format(Math.round(seconds / 60), "minute");
  if (abs < 86_400) return relative.format(Math.round(seconds / 3600), "hour");
  return relative.format(Math.round(seconds / 86_400), "day");
}

const OUTCOME_LABELS: Record<string, Record<string, string>> = {
  "1X2": { H: "Home win", D: "Draw", A: "Away win" },
  BTTS: { Y: "Both teams score", N: "Not both to score" },
};

/** Market key to label: "1X2" -> "Match result", "OU_2.5" -> "Over/under 2.5", "BTTS". */
export function marketLabel(market: string): string {
  if (market === "1X2") return "Match result";
  if (market === "BTTS") return "Both teams to score";
  if (market.startsWith("OU_")) return `Over/under ${market.slice(3)} goals`;
  return market;
}

/** Outcome code to label within a market: ("OU_2.5", "O") -> "Over 2.5". */
export function outcomeLabel(market: string, code: string): string {
  if (market.startsWith("OU_")) return `${code === "O" ? "Over" : "Under"} ${market.slice(3)}`;
  return OUTCOME_LABELS[market]?.[code] ?? code;
}

/** Sort key for "OU_2.5"-style market names by their line. */
export function totalLine(market: string): number {
  return Number.parseFloat(market.slice(3));
}
