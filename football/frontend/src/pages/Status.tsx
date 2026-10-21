// System status from /api/health. Shown on the Data page; SystemStatus can be reused there
// when the full Data and methodology page is built.

import { endpoints, useApi, type Health } from "../api";
import { ApiView } from "../components/StateViews";
import { formatDate, formatLondon } from "../format";
import type { PageProps } from ".";

export function Status(_props: PageProps) {
  return (
    <>
      <h1 className="page-title">Data and methodology</h1>
      <SystemStatus />
      <section className="card">
        <h2>Still to come on this page</h2>
        <p className="muted">
          Sources, coverage by league and season, model versions, excluded rows and the full
          refresh history, failures included.
        </p>
      </section>
    </>
  );
}

export function SystemStatus() {
  const health = useApi(endpoints.health(), { freshMs: 15_000 });
  return (
    <section className="card" aria-labelledby="system-status">
      <h2 id="system-status">System status</h2>
      <ApiView result={health} what="the system status" loadingLines={1}>
        {(h) => <StatusList health={h} />}
      </ApiView>
    </section>
  );
}

function StatusList({ health }: { health: Health }) {
  const schemaOk = health.schema_revision === health.schema_head;
  const refresh = health.data.last_refresh;
  return (
    <dl className="status">
      <dt>API</dt>
      <dd className={health.status === "ok" ? "good" : "bad"}>
        {health.status === "ok" ? "Running" : "Degraded"} (v{health.app_version})
      </dd>
      <dt>Database</dt>
      <dd className={health.database === "ok" && schemaOk ? "good" : "bad"}>
        {health.database === "ok" ? "Connected" : "Error"}, schema{" "}
        {health.schema_revision ?? "not migrated"}
        {!schemaOk && ` (expected ${health.schema_head})`}
      </dd>
      <dt>Match data</dt>
      <dd className={health.data_imported ? undefined : "muted"}>
        {health.data_imported
          ? `${health.data.matches.toLocaleString("en-GB")} matches, ${health.data.divisions} divisions`
          : "None imported"}
      </dd>
      {health.data.latest_match_date && (
        <>
          <dt>Latest match</dt>
          <dd>{formatDate(health.data.latest_match_date)}</dd>
        </>
      )}
      <dt>Last refresh</dt>
      <dd className={refresh?.status === "ok" ? undefined : "bad"}>
        {refresh ? `${formatLondon(refresh.started_at)} (${refresh.status})` : "Never"}
      </dd>
      <dt>Live provider</dt>
      <dd className="muted">
        {health.live_provider === "none"
          ? "Not configured (no key)"
          : `${health.live_provider}${health.live_provider_configured ? "" : " (no key)"}`}
      </dd>
      <dt>Server time</dt>
      <dd>{formatLondon(health.server_time_utc)} (UK)</dd>
    </dl>
  );
}
