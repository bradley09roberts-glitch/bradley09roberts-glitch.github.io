import { mkdir, readFile, readdir, rename, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { decisionLogSchema, type DecisionLog } from "@countercoach/engine";
import { DEFAULT_SETTINGS, sanitizeSettings, type Settings } from "../shared/settings.js";

/** Atomic JSON write: write a temp file then rename over the target. */
export async function writeJsonAtomic(file: string, value: unknown): Promise<void> {
  await mkdir(path.dirname(file), { recursive: true });
  const tmp = `${file}.${process.pid}.${Date.now()}.tmp`;
  await writeFile(tmp, JSON.stringify(value, null, 1));
  await rename(tmp, file);
}

export class SettingsStore {
  private current: Settings = JSON.parse(JSON.stringify(DEFAULT_SETTINGS)) as Settings;
  constructor(private readonly file: string) {}

  async load(): Promise<Settings> {
    try {
      this.current = sanitizeSettings(JSON.parse(await readFile(this.file, "utf8")));
    } catch {
      this.current = JSON.parse(JSON.stringify(DEFAULT_SETTINGS)) as Settings;
    }
    return this.current;
  }

  get(): Settings {
    return this.current;
  }

  async save(next: Settings): Promise<Settings> {
    this.current = sanitizeSettings(next);
    await writeJsonAtomic(this.file, this.current);
    return this.current;
  }
}

/**
 * Opt-in local decision log. One JSON file per match under userData/logs. Nothing is uploaded.
 */
export class LogStore {
  constructor(private readonly dir: string) {}

  private file(matchId: string): string {
    const safe = matchId.replace(/[^\w.-]/g, "_");
    return path.join(this.dir, `${safe}.json`);
  }

  async append(matchId: string, heroId: number | null, build: number | null, entry: unknown): Promise<void> {
    const f = this.file(matchId);
    let log: DecisionLog;
    try {
      const parsed = decisionLogSchema.safeParse(JSON.parse(await readFile(f, "utf8")));
      log = parsed.success ? parsed.data : { version: 1, matchId, heroId, build, entries: [] };
    } catch {
      log = { version: 1, matchId, heroId, build, entries: [] };
    }
    const candidate = { ...log, heroId: heroId ?? log.heroId, entries: [...log.entries, entry].slice(-500) };
    const ok = decisionLogSchema.safeParse(candidate);
    if (!ok.success) throw new Error("invalid decision log entry");
    await writeJsonAtomic(f, ok.data);
  }

  async list(): Promise<{ matchId: string; entries: number; heroId: number | null }[]> {
    try {
      const files = (await readdir(this.dir)).filter((f) => f.endsWith(".json"));
      const out: { matchId: string; entries: number; heroId: number | null }[] = [];
      for (const f of files) {
        try {
          const p = decisionLogSchema.safeParse(JSON.parse(await readFile(path.join(this.dir, f), "utf8")));
          if (p.success) out.push({ matchId: p.data.matchId, entries: p.data.entries.length, heroId: p.data.heroId });
        } catch {
          /* skip unreadable */
        }
      }
      return out;
    } catch {
      return [];
    }
  }

  async read(matchId: string): Promise<DecisionLog | null> {
    try {
      const p = decisionLogSchema.safeParse(JSON.parse(await readFile(this.file(matchId), "utf8")));
      return p.success ? p.data : null;
    } catch {
      return null;
    }
  }

  async deleteAll(): Promise<void> {
    await rm(this.dir, { recursive: true, force: true });
  }
}
