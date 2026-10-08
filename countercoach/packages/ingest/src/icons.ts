import { buildTemplateFile, encodeTemplateImage, templateRequests, type IconTemplateFile, type Snapshot } from "@countercoach/engine";
import { API } from "./core.js";
import { fetchBinary, type FetchOptions } from "./http.js";
import { decodePng } from "./png.js";

/**
 * Build screen-recognition templates for a snapshot: each shop item's art (`shop_image`, unique
 * per item, unlike the older HUD glyph `image` that many items share) and four portrait styles
 * per hero. Used for the test fixture `fixtures/vision/icons.json`; the desktop app builds its
 * own copy on the user's machine with the same engine helpers.
 */
export async function buildIconTemplates(snapshot: Snapshot, fo: FetchOptions, log: (m: string) => void): Promise<{ file: IconTemplateFile; problems: string[] }> {
  const problems: string[] = [];
  const entries: Parameters<typeof buildTemplateFile>[0] = [];
  for (const req of templateRequests(snapshot)) {
    try {
      if (!/\.png($|\?)/i.test(req.url)) throw new Error("not a PNG");
      entries.push({ req, rgba: encodeTemplateImage(decodePng((await fetchBinary(req.url, fo)).data)) });
    } catch (e) {
      problems.push(`${req.kind} ${req.className}${req.variant ? `/${req.variant}` : ""}: ${String(e)}`);
    }
  }
  const file = buildTemplateFile(entries, `${API} asset images (community Deadlock API, not Valve); snapshot build ${snapshot.meta.clientVersion}`, new Date().toISOString());
  log(`items: ${file.items.length}/${snapshot.items.length}; portraits: ${file.heroes.length}`);
  return { file, problems };
}
