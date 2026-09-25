import { describe, expect, it } from 'vitest'
import { rational } from './rational'
import {
  FLICKS_PER_SECOND,
  flicks,
  flicksPerFrame,
  flicksToFrameFloor,
  formatFrameRate,
  formatTimecode,
  frame,
  frameToFlicks,
  secondsToFramesCeil,
} from './units'

const STANDARD_RATES = [
  rational(24),
  rational(25),
  rational(30),
  rational(48),
  rational(50),
  rational(60),
  rational(90),
  rational(120),
  rational(24000, 1001),
  rational(30000, 1001),
  rational(60000, 1001),
]

describe('frame and flicks units', () => {
  it('rejects non-integer frames and flicks', () => {
    expect(() => frame(1.5)).toThrow(RangeError)
    expect(() => flicks(0.1)).toThrow(RangeError)
    expect(frame(-3)).toBe(-3)
  })

  it('every standard frame rate is a whole number of flicks per frame', () => {
    for (const rate of STANDARD_RATES) {
      expect(flicksPerFrame(rate).den, `${rate.num}/${rate.den}`).toBe(1)
    }
  })

  it('44.1k and 48k samples are whole numbers of flicks', () => {
    expect(FLICKS_PER_SECOND % 44_100).toBe(0)
    expect(FLICKS_PER_SECOND % 48_000).toBe(0)
  })

  it('round-trips frame → flicks → frame at every standard rate', () => {
    for (const rate of STANDARD_RATES) {
      for (const f of [0, 1, 59, 60, 1799, 1800, 123_456, -7]) {
        const t = frameToFlicks(frame(f), rate)
        expect(flicksToFrameFloor(t, rate)).toBe(f)
        // One flick before the frame boundary still belongs to the previous frame.
        expect(flicksToFrameFloor(flicks(t - 1), rate)).toBe(f - 1)
      }
    }
  })

  it('computes known values exactly', () => {
    expect(frameToFlicks(frame(60), rational(60))).toBe(FLICKS_PER_SECOND)
    expect(frameToFlicks(frame(1), rational(30000, 1001))).toBe(23_543_520)
    expect(frameToFlicks(frame(30000), rational(30000, 1001))).toBe(1001 * FLICKS_PER_SECOND)
  })

  it('throws for rates that do not land on whole flicks', () => {
    expect(() => frameToFlicks(frame(1), rational(7))).not.toThrow() // 100,800,000
    expect(() => frameToFlicks(frame(1), rational(1_000_000_007))).toThrow(RangeError)
  })

  it('converts whole seconds to frames, rounding up at fractional rates', () => {
    expect(secondsToFramesCeil(30, rational(60))).toBe(1800)
    expect(secondsToFramesCeil(30, rational(25))).toBe(750)
    expect(secondsToFramesCeil(30, rational(30000, 1001))).toBe(900) // 899.1 → 900
    expect(() => secondsToFramesCeil(1.5, rational(60))).toThrow(RangeError)
  })

  it('formats timecode for display', () => {
    const r60 = rational(60)
    expect(formatTimecode(frame(0), r60)).toBe('00:00')
    expect(formatTimecode(frame(59), r60)).toBe('00:59')
    expect(formatTimecode(frame(61), r60)).toBe('01:01')
    expect(formatTimecode(frame(1799), r60)).toBe('29:59')
    expect(formatTimecode(frame(3600 + 5), r60)).toBe('1:00:05')
    expect(formatTimecode(frame(-30), r60)).toBe('-00:30')
    expect(formatTimecode(frame(26), rational(25))).toBe('01:01')
    expect(formatTimecode(frame(30), rational(30000, 1001))).toBe('01:00')
    expect(formatTimecode(frame(120), rational(120))).toBe('01:000')
  })

  it('formats frame rates for display', () => {
    expect(formatFrameRate(rational(60))).toBe('60')
    expect(formatFrameRate(rational(30000, 1001))).toBe('29.97')
    expect(formatFrameRate(rational(24000, 1001))).toBe('23.976')
  })
})
