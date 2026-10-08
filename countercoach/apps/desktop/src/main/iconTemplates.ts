import { nativeImage } from "electron";
import { mkdir, readFile, readdir, rm } from "node:fs/promises";
import path from "node:path";
import {
  buildTemplateFile,
  canonicalJson,
  encodeTemplateImage,
  iconTemplateFileSchema,
  sha256Hex,
  templateRequests,
  unpremultiply,
  type IconTemplateFile,
  type Snapshot,
  type TemplateRequest,
} from "@countercoach/engine";
import { writeJsonAtomic } from "./stores.js";

/**
 * Builds the screen reader's icon templates on the user's machine, from the same asset CDN the
 * app already loads icons from, so no game art is bundled in the installer. Built once per
 * snapshot (cached under userData/vision) and only when the screen reader is first used.
 */

/** Only these hosts are fetched (the snapshot's image URLs all point here). */
const ALLOWED_HOST = "assets-bucket.deadlock-api.com";
const CONCURRENCY = 6;
const TIMEOUT_MS = 15_000;
/** Below this share of item images the build is treated as failed (offline, CDN down). */
const MIN_ITEM_SHARE = 0.9;

export type TemplateProgress = { done: number; total: number };

export class IconTemplateStore {
  private inflight: Promise<IconTemplateFile | { error: string }> | null = null;
  private inflightKey = "";

  constructor(private readonly dir: string) {}

  private key(reqs: TemplateRequest[]): string {
    return sha256Hex(canonicalJson(reqs.map((r) => [r.kind, r.className, r.variant, r.url]))).slice(0, 16);
  }

  /** Cached templates for this snapshot, or null. Never touches the network. */
  async cached(snapshot: Snapshot): Promise<IconTemplateFile | null> {
    try {
      const raw = JSON.parse(await readFile(path.join(this.dir, `icons-${this.key(templateRequests(snapshot))}.json`), "utf8"));
      const p = iconTemplateFileSchema.safeParse(raw);
      return p.success ? p.data : null;
    } catch {
      return null;
    }
  }

  /** Cached templates, or build them now (single flight per snapshot). */
  get(snapshot: Snapshot, onProgress: (p: TemplateProgress) => void): Promise<IconTemplateFile | { error: string }> {
    const reqs = templateRequests(snapshot);
    const key = this.key(reqs);
    if (this.inflight && this.inflightKey === key) return this.inflight;
    this.inflightKey = key;
    this.inflight = (async () => {
      const hit = await this.cached(snapshot);
      if (hit) return hit;
      const r = await this.build(snapshot, reqs, onProgress);
      if (!("error" in r)) {
        await mkdir(this.dir, { recursive: true });
        await writeJsonAtomic(path.join(this.dir, `icons-${key}.json`), r);
        // Keep only the current snapshot's templates.
        for (const f of await readdir(this.dir)) if (/^icons-[0-9a-f]+\.json$/.test(f) && f !== `icons-${key}.json`) await rm(path.join(this.dir, f), { force: true });
      }
      return r;
    })().finally(() => {
      this.inflight = null;
    });
    return this.inflight;
  }

  private async build(snapshot: Snapshot, reqs: TemplateRequest[], onProgress: (p: TemplateProgress) => void): Promise<IconTemplateFile | { error: string }> {
    const entries: { req: TemplateRequest; rgba: string }[] = [];
    let done = 0;
    let next = 0;
    const worker = async () => {
      while (next < reqs.length) {
        const req = reqs[next++]!;
        try {
          const rgba = await this.fetchTemplate(req.url);
          if (rgba) entries.push({ req, rgba });
        } catch {
          /* skipped; counted below */
        }
        done++;
        if (done % 10 === 0 || done === reqs.length) onProgress({ done, total: reqs.length });
      }
    };
    await Promise.all(Array.from({ length: CONCURRENCY }, worker));
    const itemsWanted = reqs.filter((r) => r.kind === "item").length;
    const itemsGot = entries.filter((e) => e.req.kind === "item").length;
    if (itemsGot < itemsWanted * MIN_ITEM_SHARE) {
      return { error: `Could only download ${itemsGot} of ${itemsWanted} item images from ${ALLOWED_HOST}. Check your connection and try again.` };
    }
    // Keep a stable order regardless of download completion order.
    const order = new Map(reqs.map((r, i) => [r, i]));
    entries.sort((a, b) => order.get(a.req)! - order.get(b.req)!);
    return buildTemplateFile(entries, `${ALLOWED_HOST} images listed in snapshot build ${snapshot.meta.clientVersion}; built locally`, new Date().toISOString());
  }

  private async fetchTemplate(url: string): Promise<string | null> {
    const u = new URL(url);
    if (u.protocol !== "https:" || u.hostname !== ALLOWED_HOST) return null;
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
    try {
      const res = await fetch(u, { signal: ctrl.signal });
      if (!res.ok) return null;
      const img = nativeImage.createFromBuffer(Buffer.from(await res.arrayBuffer()));
      if (img.isEmpty()) return null;
      const { width, height } = img.getSize();
      // NativeImage bitmaps are BGRA with premultiplied alpha; templates use straight alpha.
      const data = new Uint8Array(img.toBitmap());
      unpremultiply(data);
      return encodeTemplateImage({ width, height, data, order: "bgra" });
    } finally {
      clearTimeout(timer);
    }
  }
}
