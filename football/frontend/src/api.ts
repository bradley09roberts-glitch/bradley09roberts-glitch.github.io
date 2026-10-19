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
    last_refresh: { status: string; started_at: string; finished_at: string | null } | null;
  };
};

export async function fetchHealth(signal: AbortSignal): Promise<Health> {
  const res = await fetch("/api/health", { signal });
  if (!res.ok) throw new Error(`API returned ${res.status}`);
  return (await res.json()) as Health;
}

const londonTime = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Europe/London",
  dateStyle: "medium",
  timeStyle: "short",
});

export function formatLondon(iso: string): string {
  return londonTime.format(new Date(iso));
}

const londonDate = new Intl.DateTimeFormat("en-GB", { timeZone: "Europe/London", dateStyle: "medium" });

export function formatDate(isoDate: string): string {
  return londonDate.format(new Date(`${isoDate}T12:00:00Z`));
}
