import { useEffect, useState } from "react";
import { fetchHealth, formatDate, formatLondon, type Health } from "./api";

type State =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ready"; health: Health };

export function App() {
  const [state, setState] = useState<State>({ kind: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setState({ kind: "loading" });
    fetchHealth(controller.signal)
      .then((health) => setState({ kind: "ready", health }))
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: "error", message: err instanceof Error ? err.message : String(err) });
      });
    return () => controller.abort();
  }, [attempt]);

  return (
    <>
      <header className="bar">
        <strong>Football model</strong>
        <span className="tag">Data import</span>
      </header>
      <main className="page">
        <section className="card notice">
          <h1>No predictions yet</h1>
          <p>
            Match data is being imported, but no model has been fitted, so there are no
            predictions, accuracy figures or profit numbers on this site. They appear only once
            they come from real data and reproducible backtests.
          </p>
        </section>

        <section className="card" aria-live="polite">
          <h2>System status</h2>
          {state.kind === "loading" && <p className="muted">Checking the API…</p>}
          {state.kind === "error" && (
            <div className="error">
              <p>Can't reach the API: {state.message}</p>
              <button type="button" onClick={() => setAttempt((n) => n + 1)}>
                Try again
              </button>
            </div>
          )}
          {state.kind === "ready" && <StatusList health={state.health} />}
        </section>
      </main>
    </>
  );
}

function StatusList({ health }: { health: Health }) {
  const schemaOk = health.schema_revision === health.schema_head;
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
      <dd className={health.data.last_refresh?.status === "ok" ? undefined : "bad"}>
        {health.data.last_refresh
          ? `${formatLondon(health.data.last_refresh.started_at)} (${health.data.last_refresh.status})`
          : "Never"}
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
