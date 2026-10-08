import type { AbilityAction, ThreatKind } from "@countercoach/engine";

export function fmtTime(s: number | null | undefined): string {
  if (s == null || !Number.isFinite(s)) return "--:--";
  const m = Math.floor(s / 60);
  return `${m}:${String(Math.floor(s % 60)).padStart(2, "0")}`;
}

export function parseTime(v: string): number | null {
  const m = v.trim().match(/^(\d{1,3})(?::(\d{1,2}))?$/);
  if (!m) return null;
  return Number(m[1]) * 60 + Number(m[2] ?? 0);
}

export function fmtSouls(n: number | null | undefined): string {
  if (n == null) return "—";
  return n >= 10000 ? `${(n / 1000).toFixed(1)}k` : n.toLocaleString("en-US");
}

export const THREAT_SHORT: Partial<Record<ThreatKind, string>> = {
  weapon_damage: "Gun damage",
  spirit_damage: "Spirit damage",
  melee_damage: "Melee",
  spirit_burst: "Spirit burst",
  weapon_burst: "Gun burst",
  enemy_healing: "Enemy healing",
  hard_cc: "Stuns/sleeps",
  silence: "Silence",
  slow_kite: "Slows",
  debuffs: "DoT debuffs",
  mobility_escape: "Mobility",
  stealth: "Stealth",
  channel_ultimate: "Channel ult",
  tanky_targets: "Tanky targets",
  bullet_resist_stacking: "Bullet resist",
  spirit_resist_stacking: "Spirit resist",
  percent_hp_damage: "%HP damage",
};

export const EVIDENCE_LABEL: Record<string, string> = {
  "roster-only": "heroes only",
  "observed-items": "seen items",
  reported: "your report/recap",
};

export const INFO_LABEL: Record<string, string> = {
  manual: "Manual",
  scenario: "Scenario",
  "verified-live": "Verified live",
  "last-observed": "Last observed",
  replay: "Replay",
  "no-data": "No data",
};

export function abilityLine(a: AbilityAction): string {
  switch (a.kind) {
    case "unlock":
      return `Unlock ${a.ability?.name}`;
    case "upgrade":
      return `${a.ability?.name} → T${a.tier} (${a.pointsRequired} pt${a.pointsRequired === 1 ? "" : "s"})`;
    case "hold":
      return `Hold points: ${a.ability?.name} T${a.tier} needs ${a.pointsRequired} (have ${a.pointsAvailable ?? 0})`;
    case "need-info":
      return "Enter unspent ability points";
    case "invalid-state":
      return "Check your ability tiers";
    default:
      return a.reason || "No ability action";
  }
}

export function initials(name: string): string {
  return name
    .split(/[\s&-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join("");
}
