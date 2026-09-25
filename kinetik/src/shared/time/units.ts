/**
 * Branded integer time units (SPEC §3.4, CLAUDE.md rule 1).
 *
 * - `Frame`: an integer frame index in a composition's frame rate.
 * - `Flicks`: integer media time, 705,600,000 per second. Evenly divides every frame rate and
 *   sample rate Kinetik supports, so conversions at standard rates are exact.
 */
import { type Rational, ceil, div, floor, isInteger, mul, rational, toNumber } from './rational'

export type Frame = number & { readonly __unit: 'Frame' }
export type Flicks = number & { readonly __unit: 'Flicks' }

export const FLICKS_PER_SECOND = 705_600_000

export function frame(n: number): Frame {
  if (!Number.isSafeInteger(n)) throw new RangeError(`frame must be an integer, got ${n}`)
  return n as Frame
}

export function flicks(n: number): Flicks {
  if (!Number.isSafeInteger(n)) throw new RangeError(`flicks must be an integer, got ${n}`)
  return n as Flicks
}

export function assertValidFrameRate(rate: Rational): void {
  if (rate.num <= 0 || rate.den <= 0) throw new RangeError('frame rate must be positive')
}

/** Duration of one frame in flicks, exactly (may be fractional for exotic rates). */
export function flicksPerFrame(rate: Rational): Rational {
  assertValidFrameRate(rate)
  return div(rational(FLICKS_PER_SECOND), rate)
}

/** Start time of `f` in flicks. Throws if the rate does not map to whole flicks. */
export function frameToFlicks(f: Frame, rate: Rational): Flicks {
  const r = mul(rational(f), flicksPerFrame(rate))
  if (!isInteger(r)) {
    throw new RangeError(
      `frame ${f} at ${rate.num}/${rate.den} fps is not a whole number of flicks`,
    )
  }
  return flicks(r.num)
}

/** The frame that is showing at time `t`: floor(t · rate). */
export function flicksToFrameFloor(t: Flicks, rate: Rational): Frame {
  return frame(floor(div(rational(t), flicksPerFrame(rate))))
}

/** Frames needed to cover `seconds` whole seconds (rounded up for fractional rates). */
export function secondsToFramesCeil(seconds: number, rate: Rational): Frame {
  if (!Number.isSafeInteger(seconds) || seconds < 0) {
    throw new RangeError(`seconds must be a non-negative integer, got ${seconds}`)
  }
  assertValidFrameRate(rate)
  return frame(ceil(mul(rational(seconds), rate)))
}

/** Nominal integer rate used for timecode display, e.g. 30000/1001 → 30. */
export function nominalFps(rate: Rational): number {
  assertValidFrameRate(rate)
  return Math.max(1, Math.round(toNumber(rate)))
}

/**
 * Non-drop display timecode: `SS:FF`, or `M:SS:FF` from one minute. Display only.
 * Negative frames get a leading minus sign.
 */
export function formatTimecode(f: Frame, rate: Rational): string {
  const fps = nominalFps(rate)
  const sign = f < 0 ? '-' : ''
  const abs = Math.abs(f)
  const totalSeconds = Math.floor(abs / fps)
  const ff = abs % fps
  const ss = totalSeconds % 60
  const mm = Math.floor(totalSeconds / 60)
  const width = String(fps - 1).length
  const ffStr = String(ff).padStart(Math.max(2, width), '0')
  const ssStr = String(ss).padStart(2, '0')
  return mm > 0 ? `${sign}${mm}:${ssStr}:${ffStr}` : `${sign}${ssStr}:${ffStr}`
}

/** Human label for a rate: "60", "29.97", "23.976". Display only. */
export function formatFrameRate(rate: Rational): string {
  if (rate.den === 1) return String(rate.num)
  return String(Number(toNumber(rate).toFixed(3)))
}
