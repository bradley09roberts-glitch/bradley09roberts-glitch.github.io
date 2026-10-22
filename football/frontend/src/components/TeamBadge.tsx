// A team's badge, shown beside its name wherever a team is named.
//
// With a crest URL (the teams media API will supply one per team) it shows that image. With the
// club's colours (from the same API) it draws a shield in those colours. Without either it
// draws a neutral monogram: one shield in the site's own colours with the team's two- or
// three-letter abbreviation, the same for every club, so no club is ever shown in another
// club's colours. No club's real crest is imitated.
//
// Letters sit on a solid field (never over a pattern) and are drawn only from 30 px up; a
// smaller badge shows a centre-circle mark instead, as letters would be too small to read.
//
// The badge is decorative: the team's name is always printed beside it, so it is hidden from
// screen readers and carries no meaning of its own.

import { useState } from "react";

export type TeamBadgeProps = {
  /** The API's team key, e.g. "eng-arsenal" (for the crest, once the API supplies one). */
  teamKey: string;
  /** The display name; the abbreviation is made from it. */
  name: string;
  crestUrl?: string | null;
  /** Club colours, primary first, as CSS hex colours. */
  colours?: string[];
  /** Rendered size in CSS pixels (square). */
  size?: number;
  className?: string;
};

/** Below this size the badge shows no letters. */
const MIN_LETTERS = 30;

/** The widest the letters may be (in the 48-unit box) to stay inside the shield's inner line. */
const MAX_TEXT = 27;

/** A rough width of the letters in em of a condensed bold face: wide letters (M, W) count more. */
function emWidth(letters: string): number {
  let w = 0;
  for (const ch of letters) w += /[MW]/.test(ch) ? 0.72 : /[I1]/.test(ch) ? 0.26 : 0.5;
  return w;
}

function plain(s: string): string {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/['’`]/g, "");
}

/** A key for a team known only by name (e.g. a recent opponent): "Man United" -> "man-united". */
export function teamKeyFromName(name: string): string {
  return plain(name)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

const DROP = new Set(["fc", "afc", "cf", "sc", "ac", "club", "de", "d", "the", "cd", "sd", "ud", "sv", "vfb", "vfl"]);

/** "Arsenal" -> "ARS", "Man United" -> "MUN", "Paris SG" -> "PSG", "RB Leipzig" -> "RBL",
 * "Red Star Belgrade" -> "RSB", "Schalke 04" -> "S04". */
export function teamAbbr(name: string): string {
  const tokens = plain(name)
    .split(/[\s\-/.]+/)
    .filter((t) => t.length > 0 && !DROP.has(t.toLowerCase()) && !/^\d{4}$/.test(t));
  const words = tokens.length > 0 ? tokens : [plain(name).replace(/\s+/g, "") || "?"];
  const isAcronym = (t: string) => t.length >= 2 && t.length <= 4 && t === t.toUpperCase() && /[A-Z]/.test(t);
  const [a = "", b = "", c = ""] = words;
  let out: string;
  if (words.length === 1) out = isAcronym(a) ? a : a.slice(0, 3);
  else if (words.length === 2) {
    if (isAcronym(a)) out = a + b.charAt(0);
    else if (isAcronym(b) || /^\d+$/.test(b)) out = a.charAt(0) + b;
    else out = a.charAt(0) + b.slice(0, 2);
  } else out = a.charAt(0) + b.charAt(0) + c.charAt(0);
  return out.slice(0, 3).toUpperCase();
}

function isHex(c: unknown): c is string {
  return typeof c === "string" && /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i.test(c);
}

function luminance(hex: string): number {
  let h = hex.slice(1);
  if (h.length === 3) h = h.replace(/./g, (x) => x + x);
  const [r, g, b] = [0, 2, 4].map((i) => {
    const v = Number.parseInt(h.slice(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0);
}

/** The club's own colours, when the API gives them: field, band and an ink that reads on the
 * field. Null means the neutral monogram (its colours come from the stylesheet). */
function clubColours(colours?: string[]): { field: string; band: string; ink: string } | null {
  const given = (colours ?? []).filter(isHex);
  const field = given[0];
  if (!field) return null;
  const light = luminance(field) > 0.4;
  return { field, band: given[1] ?? (light ? "#161616" : "#ffffff"), ink: light ? "#161616" : "#ffffff" };
}

/** A heater shield in a 48 x 48 box, and the same shape inset for its inner line. */
const SHIELD = "M24 2.5C29.5 5.4 35.6 6.4 42 6.4V22C42 33.6 34.4 41.6 24 46C13.6 41.6 6 33.6 6 22V6.4C12.4 6.4 18.5 5.4 24 2.5Z";
const INSET = "translate(24 24) scale(0.82) translate(-24 -24.6)";
/** The top band (the chief): above the letters, so nothing crosses them. */
const CHIEF = "M6 6.4C12.4 6.4 18.5 5.4 24 2.5C29.5 5.4 35.6 6.4 42 6.4V12.5H6Z";

export function TeamBadge({ name, crestUrl, colours, size = 32, className }: TeamBadgeProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const cls = `team-badge${className ? ` ${className}` : ""}`;

  if (crestUrl && failedUrl !== crestUrl) {
    return (
      <img
        className={`${cls} team-crest`}
        src={crestUrl}
        alt=""
        aria-hidden="true"
        width={size}
        height={size}
        loading="lazy"
        decoding="async"
        onError={() => setFailedUrl(crestUrl)}
      />
    );
  }

  const club = clubColours(colours);
  const letters = size >= MIN_LETTERS ? teamAbbr(name) : "";
  const fontSize = letters.length >= 3 ? 16.5 : letters.length === 2 ? 19 : 22;
  // Wide combinations ("WWA", "MUN") are drawn a little narrower so they never touch the line.
  const squeeze = emWidth(letters) * fontSize > MAX_TEXT;
  return (
    <svg
      className={`${cls}${club ? " team-badge-club" : " team-badge-neutral"}`}
      width={size}
      height={size}
      viewBox="0 0 48 48"
      aria-hidden="true"
      focusable="false"
    >
      <path className="team-badge-field" d={SHIELD} style={club ? { fill: club.field } : undefined} />
      <path className="team-badge-chief" d={CHIEF} style={club ? { fill: club.band, opacity: 1 } : undefined} />
      <path
        className="team-badge-line"
        d={SHIELD}
        transform={INSET}
        style={club ? { stroke: club.ink, opacity: 0.35 } : undefined}
      />
      {letters ? (
        <text
          className="team-badge-abbr"
          x="24"
          y={letters.length >= 3 ? 30.2 : 31}
          textAnchor="middle"
          fontSize={fontSize}
          textLength={squeeze ? MAX_TEXT : undefined}
          lengthAdjust={squeeze ? "spacingAndGlyphs" : undefined}
          style={club ? { fill: club.ink } : undefined}
        >
          {letters}
        </text>
      ) : (
        <g className="team-badge-mark" style={club ? { stroke: club.ink, fill: club.ink } : undefined}>
          <circle cx="24" cy="25.5" r="7" fill="none" strokeWidth="2.6" />
          <circle cx="24" cy="25.5" r="1.9" stroke="none" />
        </g>
      )}
    </svg>
  );
}
