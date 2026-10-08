import type { Settings } from "../shared/settings.js";

/** Minimal display description (matches Electron's Display fields we use). */
export interface DisplayInfo {
  id: number;
  scaleFactor: number;
  workArea: { x: number; y: number; width: number; height: number };
}

export interface Bounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Base overlay sizes in device-independent pixels, before the user scale. */
export const OVERLAY_BASE = {
  collapsed: { width: 340, height: 168 },
  expanded: { width: 400, height: 540 },
};
const MARGIN = 16;

export function pickDisplay(displays: DisplayInfo[], displayId: number | null, primaryId: number): DisplayInfo {
  return displays.find((d) => d.id === displayId) ?? displays.find((d) => d.id === primaryId) ?? displays[0]!;
}

/**
 * Overlay bounds in DIPs. Electron positions windows in DIPs, so display scaling is handled by
 * the OS; we only clamp inside the chosen display's work area (taskbar excluded). Works for
 * 16:9 and ultrawide work areas and for displays with negative origins (left/above primary).
 */
export function computeOverlayBounds(s: Settings["overlay"], displays: DisplayInfo[], primaryId: number, contentHeight?: number | null): Bounds & { displayId: number } {
  const d = pickDisplay(displays, s.displayId, primaryId);
  const base = s.expanded ? OVERLAY_BASE.expanded : OVERLAY_BASE.collapsed;
  const width = Math.round(base.width * s.scale);
  // The window hugs its rendered content (reported by the overlay renderer), within limits.
  const height = contentHeight != null ? Math.round(Math.min(Math.max(contentHeight, 60), base.height * s.scale * 1.6)) : Math.round(base.height * s.scale);
  const wa = d.workArea;
  let x: number;
  let y: number;
  switch (s.anchor) {
    case "top-left":
      x = wa.x + MARGIN;
      y = wa.y + MARGIN;
      break;
    case "top-right":
      x = wa.x + wa.width - width - MARGIN;
      y = wa.y + MARGIN;
      break;
    case "bottom-left":
      x = wa.x + MARGIN;
      y = wa.y + wa.height - height - MARGIN;
      break;
    case "bottom-right":
      x = wa.x + wa.width - width - MARGIN;
      y = wa.y + wa.height - height - MARGIN;
      break;
    default:
      x = wa.x + Math.round(s.offset.x * wa.width);
      y = wa.y + Math.round(s.offset.y * wa.height);
  }
  x = Math.min(Math.max(x, wa.x), wa.x + Math.max(0, wa.width - width));
  y = Math.min(Math.max(y, wa.y), wa.y + Math.max(0, wa.height - height));
  return { x, y, width: Math.min(width, wa.width), height: Math.min(height, wa.height), displayId: d.id };
}

/** Convert a dragged window position back to a resolution-independent offset. */
export function offsetFromBounds(b: Bounds, displays: DisplayInfo[], primaryId: number): { displayId: number; offset: { x: number; y: number } } {
  const cx = b.x + b.width / 2;
  const cy = b.y + b.height / 2;
  const d =
    displays.find((x) => cx >= x.workArea.x && cx < x.workArea.x + x.workArea.width && cy >= x.workArea.y && cy < x.workArea.y + x.workArea.height) ??
    pickDisplay(displays, null, primaryId);
  const wa = d.workArea;
  const clamp = (v: number) => Math.min(1, Math.max(0, v));
  return { displayId: d.id, offset: { x: clamp((b.x - wa.x) / wa.width), y: clamp((b.y - wa.y) / wa.height) } };
}
