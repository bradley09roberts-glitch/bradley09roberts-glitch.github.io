// Typed client for the read-only API (src/footy/api/main.py and routes.py).
//
// Every endpoint has a typed URL builder in `endpoints` (for the `useApi` hook) and a fetch
// function in `api` that takes an AbortSignal. Nothing here invents a number: the types only
// describe what the API sends.

import { useCallback, useEffect, useRef, useState } from "react";
import { SNAPSHOT, SnapshotError, snapshotFetch } from "./snapshot";

// ---------------------------------------------------------------------------------------------
// Shared JSON types

export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

// ---------------------------------------------------------------------------------------------
// /api/health

export type Health = {
  status: "ok" | "degraded";
  app_version: string;
  server_time_utc: string;
  display_timezone: string;
  database: "ok" | "error";
  schema_revision: string | null;
  schema_head: string;
  live_provider: string;
  live_provider_configured: boolean;
  milestone: string;
  data_imported: boolean;
  data: {
    matches: number;
    divisions: number;
    latest_match_date: string | null;
    last_refresh: { status: string; started_at: string | null; finished_at: string | null } | null;
  };
};

// ---------------------------------------------------------------------------------------------
// /api/coverage

export type CoverageRow = {
  division: string;
  division_name: string;
  tier: number;
  scope: string;
  layout: string;
  season: string;
  season_label: string;
  matches: number;
  teams: number;
  expected_matches: number;
  first_date: string;
  last_date: string;
  issues_error: number;
  issues_warning: number;
  raw_revision: number | null;
  retrieved_at: string | null;
  /** European leagues only: repeat meetings held back (D-022). */
  held_back?: number;
  /** European leagues only: matches excluded from models. */
  model_excluded?: number;
  /** Percentage of matches with each statistic, e.g. pct_shots. */
  [pct: `pct_${string}`]: number;
  /** Percentage of matches with a complete price set, e.g. "odds::PS close 1X2" (every
   * division since prices were imported for all of them, D-022). */
  [odds: `odds::${string}`]: number | null;
};

export type CoverageResponse = { rows: CoverageRow[] };

// ---------------------------------------------------------------------------------------------
// /api/refresh-history and /api/import-issues

export type JobRun = {
  id: number;
  job: string;
  status: string;
  started_at: string | null;
  finished_at: string | null;
  error: string | null;
  detail: JsonValue | null;
};

export type RefreshHistory = { runs: JobRun[] };

export type ImportIssue = {
  severity: string;
  code: string;
  file: string;
  row: number | null;
  detail: string | null;
};

export type ImportIssues = {
  summary: { severity: string; code: string; count: number }[];
  issues: ImportIssue[];
};

// ---------------------------------------------------------------------------------------------
// Predictions (/api/fixtures, /api/predictions/{id}, /api/past)

export type RecordType = "genuine_prematch" | "retrospective_backtest" | "exploratory" | "unavailable";

/** "early" (no lineups or injuries) or "lineup". */
export type Edition = "early" | "lineup" | (string & {});

export type OverUnder = { O: number; U: number };

/** Every market the saved record holds. Over/under lines are keyed "OU_0.5" … "OU_4.5". */
export type Probabilities = {
  "1X2"?: { H: number; D: number; A: number };
  BTTS?: { Y: number; N: number };
  expected_goals?: { home: number; away: number };
  top_scores?: { score: string; p: number }[];
  [line: `OU_${string}`]: OverUnder | undefined;
};

/** The ordinary pick per market: the most likely outcome. Not a betting selection. */
export type Selections = Record<string, { pick: string; p: number }>;

export type DataFreshness = {
  /** Domestic models: latest result in the training data, per division. */
  latest_result_by_division?: Record<string, string>;
  /** European model. */
  latest_uefa_result?: string;
  latest_domestic_result?: string;
  [key: string]: JsonValue | undefined;
};

export type MarketOutcome = { pick: string; actual: string; correct: boolean; p_actual: number };

export type Settlement = {
  home_goals: number | null;
  away_goals: number | null;
  status: "settled" | "void" | "postponed" | (string & {});
  outcomes: Record<string, MarketOutcome>;
  settled_at_utc: string;
};

/** Whether the record used bookmakers' prices ("model_and_market") or not ("model_only"). */
export type Basis = "model_and_market" | "model_only";

/** Only on records made with real bookmakers' prices. No selection rule is published yet, so
 * `selections` is always empty for now. */
export type ValueSelection = {
  status: "not_published" | (string & {});
  selections: JsonValue[];
  note: string;
};

export type PredictionCard = {
  id: number;
  /** The fixture's record of record: the first model in the group's list that has a genuine
   * pre-match record for it. */
  is_primary: boolean;
  basis: Basis;
  fixture_ref: string;
  competition: string;
  competition_name: string;
  season: string;
  match_date: string;
  kickoff_utc: string | null;
  home_team: string;
  away_team: string;
  home_name: string;
  away_name: string;
  /** Each side's crest and badge colours (fixtures and past cards; absent on the detail,
   * which has them in `teams`). */
  home_media?: MediaSummary;
  away_media?: MediaSummary;
  edition: Edition;
  record_type: RecordType;
  model_name: string;
  model_version: string;
  created_at_utc: string;
  training_cutoff_utc: string;
  probabilities: Probabilities;
  selections: Selections;
  data_freshness: DataFreshness;
  missing_fields: string[];
  notes: string | null;
  settlement: Settlement | null;
  value_selection?: ValueSelection;
};

export type EditionRef = {
  id: number;
  edition: Edition;
  model_name: string;
  basis: Basis;
  is_primary: boolean;
  created_at_utc: string;
  record_type: RecordType;
  reason: string | null;
};

export type RecentMatch = {
  date: string;
  competition: string;
  venue: "H" | "A";
  opponent: string;
  /** The opponent's team key (sent since the team pages; absent in older copies). */
  opponent_team?: string;
  opponent_media?: MediaSummary;
  score: string;
  result: "W" | "D" | "L";
};

/** What a model + market record took from the bookmakers: de-vigged consensus probabilities
 * and where the prices came from. Never the prices themselves. */
export type MarketUsed = {
  probabilities: { "1X2"?: { H: number; D: number; A: number }; "OU_2.5"?: OverUnder };
  books: string[];
  /** The prices were quoted no later than this (UTC). For a live record it is when the price
   * file was downloaded, not when the bookmakers' prices were collected. */
  quote_time_utc: string | null;
  provenance: { source?: string; raw_sha256?: string; raw_file_id?: number; retrieved_at?: string };
  devig: string | null;
  phase: string | null;
  /** Weight given to the prices, per market (1 = the prices alone). */
  weights: Partial<Record<"1X2" | "OU_2.5", number>>;
  base_model: string | null;
};

/** The same fixture's record with the other basis (pure model ↔ model + market). */
export type Counterpart = Pick<
  PredictionCard,
  | "id"
  | "is_primary"
  | "basis"
  | "edition"
  | "record_type"
  | "model_name"
  | "model_version"
  | "created_at_utc"
  | "probabilities"
  | "selections"
>;

export type PredictionDetail = PredictionCard & {
  /** The feature snapshot saved with the prediction (window sizes, model parameters, …). */
  features: Record<string, JsonValue>;
  /** Model + market records only. */
  market: MarketUsed | null;
  counterpart: Counterpart | null;
  /** Every saved edition for this fixture, oldest first. */
  editions: EditionRef[];
  /** Each side's last results before the training cutoff (no hindsight). */
  recent: { home: RecentMatch[]; away: RecentMatch[] };
  /** Both sides' form and averages before the match, their meetings and, once played, the
   * match's statistics (footy.teams.match_page). Absent in copies made before team pages. */
  teams?: MatchTeams;
  /** Plain-English summary built only from the saved record's numbers. */
  explanation: string;
};

/** One side in a match page's `teams` block. */
export type MatchTeamSide = {
  team: string;
  name: string;
  media?: MediaSummary;
  /** Form strings, oldest first: overall and by competition type (types it has played). */
  form: { last_10: string; by_type: Partial<Record<CompetitionType, string>> };
  /** Per-match averages over the last 10 counted matches, overall ("all") and by type. */
  per_match: Record<string, { matches: number; averages: Record<string, StatAverage> }>;
};

export type HeadToHeadMeeting = {
  ref: string;
  date: string;
  competition: string;
  competition_name: string;
  competition_type: CompetitionType;
  season: string;
  stage: string | null;
  home_team: string;
  home_name: string;
  away_team: string;
  away_name: string;
  home_goals: number | null;
  away_goals: number | null;
  neutral: boolean;
  extra_time: boolean | null;
  penalties: boolean | null;
  /** W, D or L for this fixture's home side. */
  result_for_home_side: Result | null;
  excluded: string | null;
};

/** One side's figures in a played match: goals, half-time goals and every statistic. */
export type MatchSideStats = Record<string, number | null>;

export type MatchTeams = {
  /** Everything here is from matches before this date. */
  as_of: string;
  note: string;
  home: MatchTeamSide;
  away: MatchTeamSide;
  head_to_head: {
    meetings: HeadToHeadMeeting[];
    home_side_won: number;
    drawn: number;
    away_side_won: number;
  };
  /** Once played, with statistics (league matches only); null otherwise. */
  match_stats: {
    ref: string;
    source: string;
    date: string;
    referee: string | null;
    excluded: string | null;
    home: MatchSideStats;
    away: MatchSideStats;
  } | null;
};

export type FixturesResponse = {
  generated_at_utc: string;
  competitions: { code: string; name: string; fixtures: PredictionCard[] }[];
};

export type PastResponse = { record_type: RecordType; matches: PredictionCard[] };

// ---------------------------------------------------------------------------------------------
// /api/performance

export type PerformanceMarket = {
  n: number;
  correct: number;
  accuracy: number | null;
  mean_p_actual: number | null;
};

export type PerformanceRecord = {
  published: number;
  settled: number;
  pending: number;
  last_settled_at_utc: string | null;
  markets: Record<string, PerformanceMarket>;
};

export type Performance = PerformanceRecord & {
  primary_only: boolean;
  /** Ordered preference per competition group ("domestic", "uefa"). */
  primary_models: Record<string, string[]>;
  /** Model + market and model-only records, each fixture once per basis. */
  by_basis: Record<Basis, PerformanceRecord & { models: string[] }>;
  /** Both bases on the fixtures that have both. */
  paired: { fixtures: number } & Record<Basis, PerformanceRecord>;
};

// ---------------------------------------------------------------------------------------------
// Backtests

export type MarketMetrics = {
  /** 1X2 only. */
  rps?: number;
  log_loss: number;
  brier: number;
  accuracy: number;
  n: number;
  /** Block-bootstrap 95% interval for the market's primary metric (summary endpoint only). */
  primary_ci95?: [number, number];
};

export type Headline = {
  fixtures_total: number;
  predicted: number;
  excluded: number;
  ineligible: number;
  /** Per market: "1X2", "BTTS", "OU_0.5" … "OU_4.5" (only those the model predicts). */
  [market: string]: number | MarketMetrics;
};

/** A market's metrics from a headline, or undefined when the run has none for it. */
export function headlineMarket(h: Headline | null | undefined, market: string): MarketMetrics | undefined {
  const m = h?.[market];
  return typeof m === "object" && m !== null ? m : undefined;
}

export type BacktestRun = {
  run_id: string;
  kind: "backtests" | "backtests_euro" | (string & {});
  model: string | null;
  split: string | null;
  seasons: string[] | null;
  leagues: string[] | null;
  created_at_utc: string | null;
  headline: Headline | null;
};

export type BacktestsResponse = { runs: BacktestRun[] };

export type CalibrationBin = {
  bin_low: number;
  bin_high: number;
  n: number;
  mean_predicted: number;
  observed: number;
};

export type BacktestSummary = {
  run_id: string;
  headline: Headline;
  /** Keyed "1X2_home", "1X2_draw", "1X2_away", "OU_2.5", "BTTS", … */
  calibration: Record<string, CalibrationBin[]>;
};

/** Shape of footy.modeling.benchmark.benchmark(). A saved report may wrap or extend it, so
 * read it defensively. */
export type BenchmarkResult = {
  fixtures_predicted?: number;
  shin?: Record<string, BenchmarkComparison>;
  power?: Record<string, BenchmarkComparison>;
  gate?: {
    primary: string;
    model_beats_market: boolean;
    significant: boolean;
    label: string | null;
  };
};

export type BenchmarkComparison = {
  fixtures: number;
  coverage_of_predicted: number;
  model_rps?: number;
  market_rps?: number;
  model_log_loss?: number;
  market_log_loss?: number;
  difference: number;
  difference_ci95: [number, number];
  sources?: Record<string, number>;
};

// ---------------------------------------------------------------------------------------------
// Hit rates (footy.modeling.hit_rates): how often the pick was right

/** A count of picks and how many were right, with a 95% Wilson interval (null when n is 0). */
export type HitRate = {
  n: number;
  correct: number;
  hit_rate: number | null;
  ci95: [number, number] | null;
};

/** A model's hit rate and the frequency baseline's on the same fixtures. */
export type HitRateCell = HitRate & { baseline: HitRate | null };

/** Games where the favourite (match result) or the likelier side (two-way markets) had at
 * least `threshold`; `share` is games ÷ of_games. */
export type ConfidenceBucket = {
  threshold: number;
  games: number;
  share: number;
  of_games: number;
  correct: number;
  hit_rate: number | null;
  ci95: [number, number] | null;
};

export type HitRateRow = {
  /** "ALL", or a league or competition code. */
  code: string;
  name: string;
  games: number;
  /** "1X2", "OU_1.5", "OU_2.5", "OU_3.5", "BTTS". */
  markets: Record<string, HitRateCell>;
  confidence: Record<string, ConfidenceBucket[]>;
};

export type HitRateTable = {
  family: "domestic" | "uefa" | (string & {});
  family_name: string;
  model: string;
  basis: Basis;
  run_id: string;
  split: string | null;
  run_created_at_utc: string | null;
  baseline_model: string;
  baseline_run_id: string;
  baseline_run_created_at_utc: string | null;
  seasons: string[];
  games: number;
  rows: HitRateRow[];
};

export type PrematchHitRow = { code: string; name: string; games: number; markets: Record<string, HitRate> };

export type PrematchHitTable = {
  family: string;
  family_name: string;
  games: number;
  /** Settled fixtures per model of record. */
  models: Record<string, number>;
  bases: Record<string, number>;
  rows: PrematchHitRow[];
};

export type HitRates = {
  generated_at_utc: string;
  markets: { key: string; label: string }[];
  confidence_thresholds: Record<string, number[]>;
  backtest: {
    tables: HitRateTable[];
    missing: { family: string; model: string; reason: string; runs: string[] }[];
  };
  prematch: {
    published: number;
    settled: number;
    last_settled_at_utc: string | null;
    tables: PrematchHitTable[];
  };
};

// ---------------------------------------------------------------------------------------------
// Teams (src/footy/api/teams_routes.py, docs/teams.md) and their pictures (media_routes.py)

/** A team's crest (only a downloaded, freely licensed one) and the site badge's colours and
 * letters, sent wherever a team is named. */
export type MediaSummary = {
  crest_url: string | null;
  /** `colours`: the club's official colours (Wikidata), badge background first; [] if none. */
  badge: { colours: string[]; short: string };
};

export type CompetitionType = "league" | "europe" | "cup";
export type Result = "W" | "D" | "L";
export type Venue = "H" | "A" | "N";

export type TeamCompetition = {
  code: string;
  name: string;
  type: CompetitionType | (string & {});
  season: string;
  season_label: string;
};

export type TeamDivision = {
  code: string;
  name: string;
  tier: number | null;
  season: string;
  season_label: string;
  /** False when that league has since moved on without the club. */
  current: boolean;
};

export type TeamListEntry = {
  key: string;
  name: string;
  country: string | null;
  division: TeamDivision | null;
  competitions_this_season: TeamCompetition[];
  /** Oldest first. */
  form_last_5: string;
  matches: number;
  matches_by_type: Partial<Record<CompetitionType, number>>;
  last_match_date: string | null;
  /** The descriptive strength rating (not a model of record) and its rank. */
  elo: number | null;
  elo_rank: number | null;
};

export type TeamsResponse = {
  generated_at_utc: string;
  latest_match_held: string | null;
  filters: { competition: string[]; q: string | null };
  count: number;
  teams: TeamListEntry[];
  /** Team key -> media summary, for every listed team. */
  media: Record<string, MediaSummary>;
};

/** A per-match average for and against, and how many matches it covers. */
export type StatAverage = { for: number | null; against: number | null; matches: number };

/** The counts every summary has. */
export type ShortSummary = {
  played: number;
  won: number;
  drawn: number;
  lost: number;
  goals_for: number;
  goals_against: number;
  goal_difference: number;
  /** Flagged matches left out of this summary. */
  left_out: number;
  league_matches: number;
  league_points: number | null;
  points_per_game: number | null;
};

export type Rate = { matches: number; rate: number | null };

export type FullSummary = ShortSummary & {
  /** goals, half_time_goals, shots, shots_on_target, corners, fouls, yellow_cards, red_cards,
   * fd_xg (football-data's figure, not ours). */
  per_match: Record<string, StatAverage>;
  /** clean_sheets, failed_to_score, both_teams_scored, over_2_5_goals. */
  rates: Record<string, Rate>;
  half_time: {
    matches_with_half_time_score: number;
    behind_at_half_time: { matches: number; won: number; drawn: number; lost: number; points_won: number };
    ahead_at_half_time: { matches: number; won: number; drawn: number; lost: number; points_dropped: number };
  };
};

export type TypeSummary = FullSummary & { by_venue: Partial<Record<Venue, ShortSummary>> };

export type Streak = { matches: number; since: string | null };

/** wins, unbeaten, without_a_win, losses, scored, clean_sheets, failed_to_score. */
export type Streaks = Record<string, Streak>;

/** Our pre-match record of record for a fixture, from the team's side. Never prices. */
export type TeamPrediction = {
  id: number;
  model_name: string;
  basis: Basis;
  edition: Edition;
  created_at_utc: string;
  probabilities: Probabilities;
  team: { win: number; draw: number; loss: number } | null;
  pick: string | null;
  pick_for_team: Result | null;
  settled: boolean;
  /** Per market, once settled. */
  correct: Record<string, boolean> | null;
};

export type UpcomingFixture = {
  ref: string;
  source: string;
  date: string;
  kickoff_utc: string | null;
  competition: string;
  competition_name: string;
  competition_type: CompetitionType;
  season: string;
  season_label: string;
  stage: string | null;
  opponent: string;
  opponent_name: string;
  venue: Venue;
  status: string;
  awaiting_result: boolean;
  prediction: TeamPrediction | null;
  opponent_media?: MediaSummary;
};

export type StrengthPoint = { date: string; competition: string; rating: number };

export type CompetitionSummary = {
  competition: string;
  name: string;
  type: CompetitionType;
  seasons: string[];
  summary: FullSummary;
};

export type SeasonSummary = {
  season: string;
  label: string;
  competitions: string[];
  summary: ShortSummary;
  by_type: Partial<Record<CompetitionType, ShortSummary>>;
};

export type TeamProfile = {
  generated_at_utc: string;
  team: {
    key: string;
    name: string;
    media?: MediaSummary;
    country: string | null;
    division: TeamDivision | null;
    competitions_this_season: TeamCompetition[];
  };
  data: { latest_match_held: string | null };
  notes: Record<string, string>;
  matches: { logged: number; counted: number; left_out: number; first_date: string | null; last_date: string | null };
  summary: FullSummary;
  by_type: Partial<Record<CompetitionType, TypeSummary>>;
  by_competition: CompetitionSummary[];
  by_season: SeasonSummary[];
  by_venue: Partial<Record<Venue, FullSummary>>;
  form: {
    last_5: string;
    last_10: string;
    by_type: Partial<Record<CompetitionType, { last_5: string; last_10: string }>>;
  };
  streaks: { all: Streaks; by_type: Partial<Record<CompetitionType, Streaks>> };
  upcoming: UpcomingFixture[];
  strength: {
    description: string;
    current: number | null;
    rank: number | null;
    ranked_teams: number;
    rank_basis: string;
    peak: { rating: number; date: string } | null;
    series: StrengthPoint[];
    series_points?: { counted_matches: number; shown: number; every_match_from: string | null; rule: string };
  };
};

/** One row of a team's match log, from the team's side. Statistics the source lacks are null. */
export type TeamMatch = {
  ref: string;
  source: string;
  date: string;
  kickoff_utc: string | null;
  competition: string;
  competition_name: string;
  competition_type: CompetitionType;
  season: string;
  season_label: string;
  stage: string | null;
  meeting: number | null;
  opponent: string;
  opponent_name: string;
  venue: Venue;
  listed_home: boolean;
  status: string;
  goals_for: number | null;
  goals_against: number | null;
  result: Result | null;
  ht_goals_for: number | null;
  ht_goals_against: number | null;
  ht_result: Result | null;
  extra_time: boolean | null;
  penalties: boolean | null;
  neutral: boolean;
  referee: string | null;
  excluded: string | null;
  elo: { before: number; after: number; opponent_before: number | null } | null;
  prediction: TeamPrediction | null;
  /** shots_for, shots_against, …, fd_xg_for, fd_xg_against. */
  [stat: `${string}_for` | `${string}_against`]: number | null;
};

export type TeamMatchesResponse = {
  team: string;
  filters: { type: string | null; competition: string | null; season: string | null };
  total: number;
  offset: number;
  limit: number;
  notes: Record<string, string>;
  matches: TeamMatch[];
  /** Team key -> media summary for the team and every opponent on the page. */
  media: Record<string, MediaSummary>;
};

/** A picture with its licence and credit. `url` is null until a freely licensed file has been
 * downloaded. */
export type MediaPicture = {
  url: string | null;
  licence: string | null;
  licence_url: string | null;
  author: string | null;
  attribution: string | null;
  source_url: string | null;
};

export type TeamMedia = {
  team_key: string;
  wikidata_url: string | null;
  crest: MediaPicture & { kind: "crest" | "generated" | (string & {}) };
  venue: (MediaPicture & { name: string | null }) | null;
  colours: { name: string; hex: string | null }[];
};

// ---------------------------------------------------------------------------------------------
// Machine learning (D-028): /api/ml/summary and the two saved reports

export type MlComparison = {
  fixtures: number;
  composite: number;
  target_composite: number;
  difference: number;
  difference_ci95: [number, number];
};

export type MlCandidate = {
  model: string;
  what: string;
  fixtures_with_prices: boolean;
  target: string;
  validation: MlComparison & { beat_target: boolean };
  test: { run: false } | (MlComparison & { run: true; at_least_as_good: boolean });
  outcome: string;
};

export type MlSummary = {
  decision: string;
  status: string;
  verdict: string;
  switch_to: string[];
  models_of_record: Record<string, string>;
  rule: string;
  lower_is_better: boolean;
  candidates: MlCandidate[];
  why: string[];
  report_generated_utc: string | null;
  sources: Record<string, string>;
};

export type MlCurveSplit = {
  months: string[];
  fixtures: number[];
  models: Record<string, { monthly: (number | null)[]; cumulative: (number | null)[] }>;
  tested: string[];
};

export type MlLearningCurve = {
  generated_utc: string;
  rule: string;
  note: string;
  panels: { candidate: string; target: string; splits: Record<string, MlCurveSplit> }[];
};

export type MlSelectionRow = {
  model: string;
  fixtures: number;
  composite: number;
  rps?: number;
  acc_1x2?: number;
  [metric: string]: number | string | undefined;
};

export type MlSelectionTable = { table: MlSelectionRow[] };

/** Read defensively: a saved report's shape can grow. */
export type MlSelection = {
  generated_utc?: string;
  rule?: string;
  validation_2324?: MlSelectionTable;
  test_2425_2526?: MlSelectionTable;
};

// ---------------------------------------------------------------------------------------------
// Endpoints: typed URLs. `Endpoint<T>` is a plain string that remembers its response type.

declare const responseType: unique symbol;
export type Endpoint<T> = string & { readonly [responseType]?: T };

type QueryValue = string | number | boolean | null | undefined;

function endpoint<T>(path: string, query?: Record<string, QueryValue>): Endpoint<T> {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query ?? {})) {
    if (v !== null && v !== undefined && v !== "") params.set(k, String(v));
  }
  const qs = params.toString();
  return (qs ? `${path}?${qs}` : path) as Endpoint<T>;
}

const seg = (s: string | number) => encodeURIComponent(String(s));

export type PastQuery = { competition?: string; record_type?: RecordType; limit?: number };
export type SummaryQuery = { league?: string; season?: string };

export const endpoints = {
  health: () => endpoint<Health>("/api/health"),
  coverage: () => endpoint<CoverageResponse>("/api/coverage"),
  refreshHistory: (limit?: number) => endpoint<RefreshHistory>("/api/refresh-history", { limit }),
  importIssues: () => endpoint<ImportIssues>("/api/import-issues"),
  fixtures: (days?: number) => endpoint<FixturesResponse>("/api/fixtures", { days }),
  prediction: (id: number | string) => endpoint<PredictionDetail>(`/api/predictions/${seg(id)}`),
  past: (q: PastQuery = {}) => endpoint<PastResponse>("/api/past", q),
  performance: () => endpoint<Performance>("/api/performance"),
  backtests: () => endpoint<BacktestsResponse>("/api/backtests"),
  backtestSummary: (runId: string, q: SummaryQuery = {}) =>
    endpoint<BacktestSummary>(`/api/backtests/${seg(runId)}/summary`, q),
  hitRates: () => endpoint<HitRates>("/api/hit-rates"),
  /** Saved JSON reports, e.g. "benchmark". The shape depends on the report. */
  report: <T = JsonValue>(name: string) => endpoint<T>(`/api/reports/${seg(name)}`),
  mlSummary: () => endpoint<MlSummary>("/api/ml/summary"),
  // The team pages ask for these four exactly as written, with no parameters, so a static
  // snapshot (which holds only those) can answer them. Only the match log's older pages
  // (`offset`) go beyond that, against the live API.
  teams: () => endpoint<TeamsResponse>("/api/teams"),
  team: (key: string) => endpoint<TeamProfile>(`/api/teams/${seg(key)}`),
  teamMatches: (key: string, offset?: number) =>
    endpoint<TeamMatchesResponse>(`/api/teams/${seg(key)}/matches`, { offset: offset || undefined }),
  teamMedia: (key: string) => endpoint<TeamMedia>(`/api/teams/${seg(key)}/media`),
};

/** File downloads: plain links, not fetched by the app. */
export const downloads = {
  ledger: (runId: string, format: "csv" | "json") => `/api/backtests/${seg(runId)}/ledger.${format}`,
};

// ---------------------------------------------------------------------------------------------
// Fetching

export class ApiError extends Error {
  /** HTTP status, or null when the API couldn't be reached at all. */
  readonly status: number | null;
  readonly url: string;

  constructor(message: string, status: number | null, url: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.url = url;
  }

  get notFound(): boolean {
    return this.status === 404;
  }
}

function isAbort(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError";
}

async function requestJson<T>(url: string, signal: AbortSignal): Promise<T> {
  let res: Response;
  try {
    // A snapshot build (src/snapshot.ts) answers from its saved responses instead.
    res = await (SNAPSHOT
      ? snapshotFetch(url, signal)
      : fetch(url, { signal, headers: { Accept: "application/json" } }));
  } catch (err) {
    if (isAbort(err)) throw err;
    if (SNAPSHOT && err instanceof SnapshotError) throw new ApiError(err.message, null, url);
    throw new ApiError("Can't reach the API. Check your connection and try again.", null, url);
  }
  const type = res.headers.get("content-type") ?? "";
  if (!res.ok) {
    let detail = "";
    if (type.includes("json")) {
      try {
        const body = (await res.json()) as { detail?: unknown };
        if (typeof body.detail === "string") detail = body.detail;
      } catch {
        // keep the status-only message
      }
    }
    const message =
      res.status === 404
        ? `Not found${detail ? `: ${detail}` : ""}.`
        : res.status >= 500
          ? `The API had a problem (error ${res.status}).`
          : `The API returned ${res.status}${detail ? `: ${detail}` : ""}.`;
    throw new ApiError(message, res.status, url);
  }
  if (!type.includes("json")) {
    throw new ApiError("The API sent something other than JSON. Is it running?", res.status, url);
  }
  return (await res.json()) as T;
}

// Requests for the same URL in flight at the same time share one network request. It is
// aborted only when every caller has aborted (deferred a tick, so React's StrictMode
// mount-unmount-mount doesn't fire a second request).
type Inflight = { promise: Promise<unknown>; controller: AbortController; refs: number; timer?: number };
const inflight = new Map<string, Inflight>();

function sharedRequest<T>(url: string, signal?: AbortSignal): Promise<T> {
  let entry = inflight.get(url);
  if (!entry) {
    const controller = new AbortController();
    const created: Inflight = { controller, refs: 0, promise: Promise.resolve() };
    created.promise = requestJson<T>(url, controller.signal).finally(() => {
      if (inflight.get(url) === created) inflight.delete(url);
    });
    inflight.set(url, created);
    entry = created;
  }
  const e = entry;
  if (e.timer !== undefined) {
    window.clearTimeout(e.timer);
    e.timer = undefined;
  }
  e.refs += 1;
  return new Promise<T>((resolve, reject) => {
    let done = false;
    const onAbort = () => {
      if (done) return;
      done = true;
      e.refs -= 1;
      if (e.refs === 0) {
        e.timer = window.setTimeout(() => {
          if (e.refs === 0) {
            e.controller.abort();
            if (inflight.get(url) === e) inflight.delete(url);
          }
        }, 0);
      }
      reject(new DOMException("Aborted", "AbortError"));
    };
    if (signal?.aborted) {
      onAbort();
      return;
    }
    signal?.addEventListener("abort", onAbort, { once: true });
    e.promise.then(
      (v) => {
        if (done) return;
        done = true;
        signal?.removeEventListener("abort", onAbort);
        resolve(v as T);
      },
      (err: unknown) => {
        if (done) return;
        done = true;
        signal?.removeEventListener("abort", onAbort);
        reject(err);
      },
    );
  });
}

/** Fetch a typed endpoint. Rejects with ApiError, or an AbortError when `signal` aborts. */
export function getJson<T>(url: Endpoint<T>, signal?: AbortSignal): Promise<T> {
  return sharedRequest<T>(url, signal);
}

/** One function per endpoint. */
export const api = {
  health: (signal?: AbortSignal) => getJson(endpoints.health(), signal),
  coverage: (signal?: AbortSignal) => getJson(endpoints.coverage(), signal),
  refreshHistory: (limit?: number, signal?: AbortSignal) =>
    getJson(endpoints.refreshHistory(limit), signal),
  importIssues: (signal?: AbortSignal) => getJson(endpoints.importIssues(), signal),
  fixtures: (days?: number, signal?: AbortSignal) => getJson(endpoints.fixtures(days), signal),
  prediction: (id: number | string, signal?: AbortSignal) =>
    getJson(endpoints.prediction(id), signal),
  past: (q?: PastQuery, signal?: AbortSignal) => getJson(endpoints.past(q), signal),
  performance: (signal?: AbortSignal) => getJson(endpoints.performance(), signal),
  backtests: (signal?: AbortSignal) => getJson(endpoints.backtests(), signal),
  hitRates: (signal?: AbortSignal) => getJson(endpoints.hitRates(), signal),
  backtestSummary: (runId: string, q?: SummaryQuery, signal?: AbortSignal) =>
    getJson(endpoints.backtestSummary(runId, q), signal),
  report: <T = JsonValue>(name: string, signal?: AbortSignal) =>
    getJson(endpoints.report<T>(name), signal),
};

// ---------------------------------------------------------------------------------------------
// useApi: fetch on mount and whenever the URL changes.
//
// - The previous request is aborted when the URL changes or the component unmounts.
// - A response that arrives after a newer request was started is ignored.
// - Successful responses are kept for the session. A copy younger than `freshMs` is used
//   without a request (so going back is instant); an older copy is shown at once while a new
//   request runs. If that request fails, the old copy stays, with `error` set, so the page
//   can say it is showing saved data.

type CacheEntry = { data: unknown; at: number };
const cache = new Map<string, CacheEntry>();
const CACHE_LIMIT = 60;

function remember(url: string, data: unknown): CacheEntry {
  const entry = { data, at: Date.now() };
  cache.delete(url);
  cache.set(url, entry);
  while (cache.size > CACHE_LIMIT) {
    const oldest = cache.keys().next().value;
    if (oldest === undefined) break;
    cache.delete(oldest);
  }
  return entry;
}

export type ApiResult<T> = {
  /** The latest data for this URL (possibly an earlier copy; see `error` and `loading`). */
  data: T | undefined;
  /** Set when the latest request failed. With `data` also set, the data is a saved copy. */
  error: ApiError | undefined;
  /** A request for this URL is in flight. */
  loading: boolean;
  /** When `data` was received (ms since epoch). */
  receivedAt: number | undefined;
  /** Fetch again, ignoring any saved copy. */
  reload: () => void;
};

type Snapshot = {
  url: string | null;
  data: unknown;
  error: ApiError | undefined;
  loading: boolean;
  receivedAt: number | undefined;
};

function snapshotFor(url: string | null, freshMs: number): Snapshot {
  const hit = url === null ? undefined : cache.get(url);
  return {
    url,
    data: hit?.data,
    error: undefined,
    loading: url !== null && (hit === undefined || Date.now() - hit.at >= freshMs),
    receivedAt: hit?.at,
  };
}

export function useApi<T>(url: Endpoint<T> | null, options: { freshMs?: number } = {}): ApiResult<T> {
  const freshMs = options.freshMs ?? 30_000;
  const [snap, setSnap] = useState<Snapshot>(() => snapshotFor(url, freshMs));
  const [token, setToken] = useState(0);
  const force = useRef(false);
  const seq = useRef(0);

  // A new URL: show what we already hold for it (or nothing) straight away, in this render.
  let current = snap;
  if (snap.url !== url) {
    current = snapshotFor(url, freshMs);
    setSnap(current);
  }

  useEffect(() => {
    if (url === null) return;
    const hit = cache.get(url);
    if (!force.current && hit && Date.now() - hit.at < freshMs) {
      setSnap((s) => (s.url === url && s.loading ? { ...s, loading: false } : s));
      return;
    }
    force.current = false;
    const id = ++seq.current;
    const controller = new AbortController();
    setSnap((s) => (s.url === url && !s.loading ? { ...s, loading: true } : s));
    sharedRequest<T>(url, controller.signal).then(
      (data) => {
        if (id !== seq.current || controller.signal.aborted) return;
        const entry = remember(url, data);
        setSnap({ url, data, error: undefined, loading: false, receivedAt: entry.at });
      },
      (err: unknown) => {
        if (id !== seq.current || controller.signal.aborted || isAbort(err)) return;
        const error =
          err instanceof ApiError
            ? err
            : new ApiError(err instanceof Error ? err.message : String(err), null, url);
        setSnap((s) => (s.url === url ? { ...s, error, loading: false } : s));
      },
    );
    return () => controller.abort();
  }, [url, token, freshMs]);

  const reload = useCallback(() => {
    force.current = true;
    setToken((n) => n + 1);
  }, []);

  return {
    data: current.data as T | undefined,
    error: current.error,
    loading: current.loading,
    receivedAt: current.receivedAt,
    reload,
  };
}
