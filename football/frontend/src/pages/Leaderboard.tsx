// Performance page: "Model leaderboard (live)", one card per group (league games, UEFA), from
// /api/model-leaderboard. Genuine pre-match records only: every model with settled records,
// ranked by mean RPS, with how often its match-result pick was right (and that rate's 95%
// interval as a range bar), its log losses on over/under 2.5 and both teams to score, and how
// it did against each match's record of record on the matches both have. The headline record
// leads each list. Under 100 settled matches a note says the sample is too small to tell
// models apart; before any result there is an empty state listing what is waiting.
//
// Every figure is the API's; nothing is computed here.

import type { ReactNode } from "react";
import { endpoints, useApi, type LeaderboardModel, type LeaderboardRecord, type ModelLeaderboard } from "../api";
import { BasisBadge } from "../components/Badge";
import { RangeBar, RoleTag, signedDiff } from "../components/ModelBits";
import { ErrorView, Loading, StaleNotice } from "../components/StateViews";
import { formatLondon, formatNumber, formatPct } from "../format";
import "./Leaderboard.css";

const GROUPS = [
  { id: "domestic", name: "League games" },
  { id: "uefa", name: "UEFA competitions" },
] as const;

export function ModelLeaderboards() {
  return (
    <div className="lb-groups">
      {GROUPS.map((g) => (
        <GroupBoard key={g.id} group={g.id} name={g.name} />
      ))}
    </div>
  );
}

function GroupBoard({ group, name }: { group: "domestic" | "uefa"; name: string }) {
  const res = useApi(endpoints.modelLeaderboard(group), { freshMs: 60_000 });
  const id = `lb-${group}`;
  return (
    <section className="card lb" aria-labelledby={id}>
      <div className="lb-head">
        <h2 id={id}>
          Model leaderboard (live)<span className="visually-hidden">: {name}</span>
        </h2>
        <span className="lb-group">{res.data?.group_name ?? name}</span>
      </div>
      {res.data === undefined ? (
        res.error ? (
          <ErrorView
            error={res.error}
            what={`the ${name.toLowerCase()} leaderboard`}
            onRetry={res.reload}
            retrying={res.loading}
            wayOn={{ to: "/performance", label: "Back to performance" }}
          />
        ) : (
          <Loading label={`Loading the ${name.toLowerCase()} leaderboard…`} lines={1} />
        )
      ) : (
        <>
          {res.error && (
            <StaleNotice onRetry={res.reload} retrying={res.loading}>
              Couldn't refresh: {res.error.message}
            </StaleNotice>
          )}
          <Board d={res.data} />
        </>
      )}
    </section>
  );
}

function Board({ d }: { d: ModelLeaderboard }) {
  const settledAll = d.record.settled;
  const fewest = d.models.length ? Math.min(...d.models.map((m) => m.settled)) : 0;
  const small = d.models.some((m) => m.small_sample) || d.record.small_sample;
  return (
    <>
      <p className="lb-lede">
        Genuine pre-match records only: each saved before kickoff and scored once its match was settled, each match once
        per model (its first edition). Ranked by mean RPS; lower is better.
      </p>

      {d.models.length === 0 ? (
        <Waiting d={d} empty />
      ) : (
        <>
          {small && (
            <div className="lb-small" role="note">
              <strong>Too few games to tell models apart.</strong>{" "}
              {settledAll === fewest ? (
                <>
                  {formatNumber(settledAll, 0)} settled {settledAll === 1 ? "match" : "matches"} so far.
                </>
              ) : (
                <>
                  Between {formatNumber(fewest, 0)} and {formatNumber(Math.max(...d.models.map((m) => m.settled)), 0)}{" "}
                  settled matches per model so far.
                </>
              )}{" "}
              Under {formatNumber(d.small_sample_below, 0)}, a few results can reorder this table, and the intervals
              overlap. Treat the order as provisional.
            </div>
          )}

          <ol className="lb-list" aria-label={`${d.group_name}: models ranked by mean RPS, the headline record first`}>
            <li className="lb-row lb-colhead" aria-hidden="true">
              <span className="lb-c-rank">#</span>
              <span className="lb-c-model">Model</span>
              <span className="lb-c-n">Settled</span>
              <span className="lb-c-hit">Results right</span>
              <span className="lb-c-rps">Mean RPS</span>
              <span className="lb-c-ou">O/U 2.5 log loss</span>
              <span className="lb-c-bt">BTTS log loss</span>
              <span className="lb-c-vs">Against the record</span>
            </li>
            <RecordRow r={d.record} />
            {d.models.map((m, i) => (
              <ModelRow key={m.model} m={m} rank={i + 1} roles={d.roles} />
            ))}
          </ol>
          <Waiting d={d} />
        </>
      )}

      {d.last_settled_at_utc && (
        <p className="muted small lb-foot">Last result settled {formatLondon(d.last_settled_at_utc)} (UK time).</p>
      )}
    </>
  );
}

function Figure({ cls, label, children }: { cls: string; label: string; children: ReactNode }) {
  return (
    <span className={`lb-fig ${cls}`}>
      <span className="lb-fig-label">{label}</span>
      {children}
    </span>
  );
}

function Common({ r }: { r: LeaderboardRecord }) {
  const h = r.results_right;
  return (
    <>
      <Figure cls="lb-c-n" label="Settled">
        <span className="lb-value">{formatNumber(r.settled, 0)}</span>
        <span className="lb-sub">of {formatNumber(r.published, 0)} published</span>
      </Figure>
      <Figure cls="lb-c-hit" label="Results right">
        <span className="lb-value">{h.n > 0 ? formatPct(h.hit_rate) : "—"}</span>
        <span className="lb-sub">
          {formatNumber(h.correct, 0)} of {formatNumber(h.n, 0)}
          {h.ci95 && (
            <>
              {" "}
              · <span className="nowrap">95%: {formatPct(h.ci95[0])}–{formatPct(h.ci95[1])}</span>
            </>
          )}
        </span>
        {h.n > 0 && <RangeBar value={h.hit_rate} ci={h.ci95} />}
      </Figure>
      <Figure cls="lb-c-rps" label="Mean RPS">
        <span className="lb-value">{r.mean_rps !== null ? formatNumber(r.mean_rps, 4) : "—"}</span>
      </Figure>
      <Figure cls="lb-c-ou" label="O/U 2.5 log loss">
        <span className="lb-value lb-value-sm">{fmt(r.mean_log_loss["OU_2.5"])}</span>
      </Figure>
      <Figure cls="lb-c-bt" label="BTTS log loss">
        <span className="lb-value lb-value-sm">{fmt(r.mean_log_loss.BTTS)}</span>
      </Figure>
    </>
  );
}

function fmt(v: number | null | undefined): string {
  return v === null || v === undefined ? "—" : formatNumber(v, 4);
}

function RecordRow({ r }: { r: ModelLeaderboard["record"] }) {
  return (
    <li className="lb-row lb-record">
      <span className="lb-c-rank lb-rank" aria-hidden="true">
        ★
      </span>
      <span className="lb-c-model lb-model">
        <span className="lb-name">Headline record</span>
        <span className="lb-tags">
          <RoleTag role="model_of_record" />
        </span>
        <span className="lb-desc">Each match's record of record, as the totals above count it.</span>
      </span>
      <Common r={r} />
      <Figure cls="lb-c-vs" label="Against the record">
        <span className="lb-sub">—</span>
      </Figure>
    </li>
  );
}

function ModelRow({ m, rank, roles }: { m: LeaderboardModel; rank: number; roles: Record<string, string> }) {
  return (
    <li className="lb-row">
      <span className="lb-c-rank lb-rank">
        <span className="visually-hidden">Rank </span>
        {rank}
      </span>
      <span className="lb-c-model lb-model">
        <span className="lb-name" title={m.description ?? undefined}>
          {m.label}
        </span>
        <span className="lb-tags">
          <RoleTag role={m.role} roles={roles} short />
          {m.basis === "model_and_market" && <BasisBadge basis={m.basis} />}
        </span>
        {m.pending > 0 && <span className="lb-desc">{formatNumber(m.pending, 0)} awaiting a result</span>}
      </span>
      <Common r={m} />
      <Figure cls="lb-c-vs" label="Against the record">
        <VsRecord m={m} />
      </Figure>
    </li>
  );
}

function VsRecord({ m }: { m: LeaderboardModel }) {
  const v = m.vs_record;
  if (!v || v.n === 0 || v.mean_rps_difference === null) {
    return (
      <span className="lb-sub">
        {m.records_of_record > 0 && m.records_of_record >= m.settled
          ? "Record of record on all its matches"
          : "No shared matches yet"}
      </span>
    );
  }
  const d = v.mean_rps_difference;
  const word = Math.abs(d) < 5e-5 ? "level" : d < 0 ? "better" : "worse";
  return (
    <>
      <span className={`lb-value lb-value-sm lb-vs-${word}`}>
        {signedDiff(d, 4)} <span className="lb-vs-word">{word}</span>
      </span>
      <span className="lb-sub">
        on {formatNumber(v.n, 0)} {v.n === 1 ? "match" : "matches"}: {v.better} better, {v.worse} worse
        {v.level > 0 ? `, ${v.level} level` : ""}
      </span>
    </>
  );
}

function Waiting({ d, empty = false }: { d: ModelLeaderboard; empty?: boolean }) {
  const total = d.waiting.reduce((n, w) => n + w.published, 0);
  if (!empty && d.waiting.length === 0) return null;
  return (
    <div className={empty ? "lb-empty" : "lb-waiting"}>
      {empty && (
        <p className="lb-empty-title">
          <strong>No settled records yet.</strong>
        </p>
      )}
      {d.waiting.length > 0 ? (
        <>
          <p className="small">
            {empty ? "The leaderboard fills in as matches are settled. " : "Waiting for a first result: "}
            {empty && (
              <>
                {formatNumber(total, 0)} {total === 1 ? "record" : "records"} from {d.waiting.length}{" "}
                {d.waiting.length === 1 ? "model is" : "models are"} waiting:
              </>
            )}
          </p>
          <ul className="lb-waiting-list">
            {d.waiting.map((w) => (
              <li key={w.model}>
                <span className="lb-name-sm" title={w.description ?? undefined}>
                  {w.label}
                </span>{" "}
                <RoleTag role={w.role} roles={d.roles} short />{" "}
                <span className="muted small">
                  {formatNumber(w.published, 0)} {w.published === 1 ? "record" : "records"}
                </span>
              </li>
            ))}
          </ul>
        </>
      ) : (
        <p className="small muted">No genuine pre-match records have been published for this group yet.</p>
      )}
    </div>
  );
}
