import { readFile } from "node:fs/promises";
import path from "node:path";
import { validateSnapshot, type ReviewStamps, type Snapshot } from "@countercoach/engine";
import { buildSnapshot, compareQuality, fetchLatestBuild } from "@countercoach/ingest/core";
import type { DataStatusEvent } from "../shared/ipc.js";
import { writeJsonAtomic } from "./stores.js";

interface Manifest {
  active: { file: string; build: number | null; hash: string; at: string } | null;
  previous: { file: string; build: number | null; hash: string; at: string } | null;
}

/**
 * Snapshot management for the app: a validated bundled snapshot ships with the app; refreshed
 * snapshots are fetched in the background into userData, validated, quality-checked against
 * the active one and only then activated. The previous snapshot is kept for rollback.
 */
export class DataStore {
  private active: Snapshot | null = null;
  private stamps: ReviewStamps | null = null;
  private source: "bundled" | "downloaded" = "bundled";
  private latestBuild: { build: number | null; at: string } | null = null;

  constructor(
    private readonly bundledDir: string,
    private readonly userDir: string,
    private readonly emit: (e: DataStatusEvent) => void,
  ) {}

  private get manifestFile(): string {
    return path.join(this.userDir, "manifest.json");
  }

  async load(): Promise<void> {
    const bundled = validateSnapshot(JSON.parse(await readFile(path.join(this.bundledDir, "snapshot.json"), "utf8")));
    if (!bundled.ok) throw new Error(`bundled snapshot invalid: ${bundled.errors.slice(0, 3).join("; ")}`);
    this.active = bundled.snapshot;
    this.stamps = JSON.parse(await readFile(path.join(this.bundledDir, "review-stamps.json"), "utf8")) as ReviewStamps;
    try {
      const m = JSON.parse(await readFile(this.manifestFile, "utf8")) as Manifest;
      if (m.active) {
        const dl = validateSnapshot(JSON.parse(await readFile(path.join(this.userDir, m.active.file), "utf8")));
        if (dl.ok && (dl.snapshot.meta.clientVersion ?? 0) >= (bundled.snapshot.meta.clientVersion ?? 0)) {
          this.active = dl.snapshot;
          this.source = "downloaded";
        }
      }
    } catch {
      /* no downloaded data: bundled snapshot stays active */
    }
  }

  bootstrap(): { snapshot: Snapshot; stamps: ReviewStamps | null; source: string; latest: { clientVersion: number | null; checkedAt: string } | null } {
    if (!this.active) throw new Error("data not loaded");
    return {
      snapshot: this.active,
      stamps: this.stamps,
      source: this.source,
      latest: this.latestBuild ? { clientVersion: this.latestBuild.build, checkedAt: this.latestBuild.at } : null,
    };
  }

  /** Cheap check: is the live client build newer than our data? */
  async check(): Promise<DataStatusEvent> {
    const snapshotBuild = this.active?.meta.clientVersion ?? null;
    const at = new Date().toISOString();
    try {
      const latest = await fetchLatestBuild(this.fetchOpts());
      this.latestBuild = { build: latest, at };
      const outdated = latest != null && snapshotBuild != null && latest > snapshotBuild;
      const ev: DataStatusEvent = {
        kind: outdated ? "outdated" : "current",
        message: outdated ? `Client build ${latest} is newer than data build ${snapshotBuild}.` : `Data matches client build ${snapshotBuild}.`,
        latestBuild: latest,
        snapshotBuild,
        at,
      };
      this.emit(ev);
      return ev;
    } catch (e) {
      const ev: DataStatusEvent = { kind: "offline", message: `Could not reach the data API (${String(e).slice(0, 80)}); using cached data.`, latestBuild: null, snapshotBuild, at };
      this.emit(ev);
      return ev;
    }
  }

  private fetchOpts() {
    return { cacheDir: path.join(this.userDir, "cache"), maxAgeMs: 6 * 3600_000, offline: false, retries: 3, timeoutMs: 30_000, log: () => {} };
  }

  /** Full refresh in the background. Never blocks the UI; never activates degraded data. */
  async refresh(): Promise<DataStatusEvent> {
    const snapshotBuild = this.active?.meta.clientVersion ?? null;
    const at = () => new Date().toISOString();
    this.emit({ kind: "checking", message: "Downloading and validating game data…", latestBuild: null, snapshotBuild, at: at() });
    const r = await buildSnapshot({ fetch: this.fetchOpts(), statsDays: 14, buildHeroes: 6, log: () => {} });
    if (!r.ok) {
      const ev: DataStatusEvent = { kind: "error", message: `Refresh failed validation; keeping current data. ${r.errors.slice(0, 2).join("; ")}`, latestBuild: null, snapshotBuild, at: at() };
      this.emit(ev);
      return ev;
    }
    const q = compareQuality(this.active, r.snapshot);
    if (!q.acceptable) {
      const ev: DataStatusEvent = { kind: "rejected", message: `New data looked degraded; keeping last-known-good. ${q.problems.join("; ")}`, latestBuild: r.snapshot.meta.clientVersion, snapshotBuild, at: at() };
      this.emit(ev);
      return ev;
    }
    if (r.snapshot.meta.contentHash === this.active?.meta.contentHash) {
      const ev: DataStatusEvent = { kind: "current", message: "Data unchanged.", latestBuild: r.snapshot.meta.clientVersion, snapshotBuild, at: at() };
      this.emit(ev);
      return ev;
    }
    const file = `snapshot-${r.snapshot.meta.clientVersion ?? "x"}-${r.snapshot.meta.contentHash.slice(0, 12)}.json`;
    await writeJsonAtomic(path.join(this.userDir, file), r.snapshot);
    let prev: Manifest["active"] = null;
    try {
      prev = (JSON.parse(await readFile(this.manifestFile, "utf8")) as Manifest).active;
    } catch {
      prev = null;
    }
    const m: Manifest = { active: { file, build: r.snapshot.meta.clientVersion, hash: r.snapshot.meta.contentHash, at: at() }, previous: prev };
    await writeJsonAtomic(this.manifestFile, m);
    this.active = r.snapshot;
    this.source = "downloaded";
    const ev: DataStatusEvent = {
      kind: "updated",
      message: `Updated to data build ${r.snapshot.meta.clientVersion}. Items changed since the reviewed build are labelled until re-checked.`,
      latestBuild: r.snapshot.meta.clientVersion,
      snapshotBuild: r.snapshot.meta.clientVersion,
      at: at(),
    };
    this.emit(ev);
    return ev;
  }
}
