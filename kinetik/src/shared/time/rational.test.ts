import { describe, expect, it } from 'vitest'
import {
  add,
  ceil,
  compare,
  div,
  equals,
  floor,
  isRational,
  mul,
  rational,
  roundHalfUp,
  sub,
  toString,
} from './rational'

describe('rational', () => {
  it('normalises sign and gcd', () => {
    expect(rational(4, 8)).toEqual({ num: 1, den: 2 })
    expect(rational(3, -6)).toEqual({ num: -1, den: 2 })
    expect(rational(-3, -6)).toEqual({ num: 1, den: 2 })
    expect(rational(0, 5)).toEqual({ num: 0, den: 1 })
    expect(rational(0, -5)).toEqual({ num: 0, den: 1 })
  })

  it('rejects zero denominators and non-integers', () => {
    expect(() => rational(1, 0)).toThrow(RangeError)
    expect(() => rational(1.5, 2)).toThrow(RangeError)
    expect(() => rational(Number.MAX_SAFE_INTEGER + 1)).toThrow(RangeError)
  })

  it('does exact arithmetic', () => {
    expect(add(rational(1, 3), rational(1, 6))).toEqual(rational(1, 2))
    expect(sub(rational(1, 3), rational(1, 2))).toEqual(rational(-1, 6))
    expect(mul(rational(30000, 1001), rational(1001, 30000))).toEqual(rational(1))
    expect(div(rational(60), rational(30000, 1001))).toEqual(rational(2002, 1000))
  })

  it('throws instead of losing precision', () => {
    const big = rational(Number.MAX_SAFE_INTEGER)
    expect(() => mul(big, rational(3))).toThrow(RangeError)
    expect(() => add(big, rational(1))).toThrow(RangeError)
    expect(() => div(rational(1), rational(0))).toThrow(RangeError)
  })

  it('floors, ceils and rounds exactly, including negatives', () => {
    expect(floor(rational(7, 2))).toBe(3)
    expect(floor(rational(-7, 2))).toBe(-4)
    expect(floor(rational(6, 2))).toBe(3)
    expect(ceil(rational(7, 2))).toBe(4)
    expect(ceil(rational(-7, 2))).toBe(-3)
    expect(ceil(rational(-6, 2))).toBe(-3)
    expect(roundHalfUp(rational(5, 2))).toBe(3)
    expect(roundHalfUp(rational(-5, 2))).toBe(-2)
    expect(roundHalfUp(rational(7, 3))).toBe(2)
  })

  it('floors large values exactly', () => {
    const n = Number.MAX_SAFE_INTEGER - 1
    const f = floor(rational(n, 7))
    expect(f * 7 <= n && (f + 1) * 7 > n).toBe(true)
  })

  it('compares and checks equality', () => {
    expect(compare(rational(1, 3), rational(1, 2))).toBe(-1)
    expect(compare(rational(2, 4), rational(1, 2))).toBe(0)
    expect(compare(rational(3, 4), rational(1, 2))).toBe(1)
    expect(equals(rational(2, 4), rational(1, 2))).toBe(true)
  })

  it('validates shapes and prints', () => {
    expect(isRational({ num: 60, den: 1 })).toBe(true)
    expect(isRational({ num: 2, den: 4 })).toBe(false)
    expect(isRational({ num: 1, den: 0 })).toBe(false)
    expect(isRational(null)).toBe(false)
    expect(toString(rational(60))).toBe('60')
    expect(toString(rational(30000, 1001))).toBe('30000/1001')
  })
})
