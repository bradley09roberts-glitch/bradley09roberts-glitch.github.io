/**
 * Convert in-data description markup into plain text. Output is always rendered as text by
 * the UI (never as HTML), so this only needs to be readable, not safe for HTML injection.
 */
const ENTITIES: Record<string, string> = {
  "&amp;": "&",
  "&lt;": "<",
  "&gt;": ">",
  "&quot;": '"',
  "&#39;": "'",
  "&nbsp;": " ",
};

export function plainText(markup: string | null | undefined): string {
  if (!markup) return "";
  let s = markup.replace(/<svg[\s\S]*?<\/svg>/gi, "");
  s = s.replace(/<br\s*\/?>/gi, " ");
  s = s.replace(/<[^>]*>/g, "");
  s = s.replace(/&(amp|lt|gt|quot|#39|nbsp);/g, (m) => ENTITIES[m] ?? m);
  s = s.replace(/\{[sgi]:[^}]*\}/g, "");
  return s.replace(/\s+/g, " ").trim();
}

export function parseNumber(v: unknown): number | null {
  if (typeof v === "number") return Number.isFinite(v) ? v : null;
  if (typeof v !== "string") return null;
  const m = v.trim().match(/^[-+]?\d*\.?\d+(?:e[-+]?\d+)?/i);
  if (!m) return null;
  const n = Number(m[0]);
  return Number.isFinite(n) ? n : null;
}
