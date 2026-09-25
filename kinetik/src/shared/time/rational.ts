/**
 * Exact rational numbers for frame rates, speeds and time conversions (SPEC §3.4).
 *
 * Values are always normalised: den > 0 and gcd(|num|, den) === 1. Every operation checks that
 * intermediate products stay within Number.MAX_SAFE_INTEGER and throws RangeError otherwise, so a
 * silent float rounding error can never leak into timing maths.
 */
export interface Rational {
  readonly num: number
  readonly den: number
}

function assertSafeInteger(n: number, what: string): void {
  if (!Number.isSafeInteger(n)) {
    throw new RangeError(`${what} must be a safe integer, got ${n}`)
  }
}

export function gcd(a: number, b: number): number {
  a = Math.abs(a)
  b = Math.abs(b)
  while (b !== 0) {
    const t = a % b
    a = b
    b = t
  }
  return a
}

/** Multiply two safe integers, throwing if the exact result is not a safe integer. */
export function safeMul(a: number, b: number): number {
  const r = a * b
  assertSafeInteger(r, `product ${a} * ${b}`)
  return r
}

function safeAdd(a: number, b: number): number {
  const r = a + b
  assertSafeInteger(r, `sum ${a} + ${b}`)
  return r
}

export function rational(num: number, den = 1): Rational {
  assertSafeInteger(num, 'numerator')
  assertSafeInteger(den, 'denominator')
  if (den === 0) throw new RangeError('denominator must not be zero')
  const sign = den < 0 ? -1 : 1
  const g = gcd(num, den) || 1
  // `+ 0` turns -0 into 0 so equal values are also structurally equal.
  return { num: (sign * num) / g + 0, den: (sign * den) / g }
}

export function isRational(value: unknown): value is Rational {
  if (typeof value !== 'object' || value === null) return false
  const { num, den } = value as Record<string, unknown>
  return (
    typeof num === 'number' &&
    typeof den === 'number' &&
    Number.isSafeInteger(num) &&
    Number.isSafeInteger(den) &&
    den > 0 &&
    gcd(num, den) === 1
  )
}

export function mul(a: Rational, b: Rational): Rational {
  // Cross-reduce first to keep intermediates small.
  const g1 = gcd(a.num, b.den) || 1
  const g2 = gcd(b.num, a.den) || 1
  return rational(safeMul(a.num / g1, b.num / g2), safeMul(a.den / g2, b.den / g1))
}

export function div(a: Rational, b: Rational): Rational {
  if (b.num === 0) throw new RangeError('division by zero')
  return mul(a, rational(b.den, b.num))
}

export function add(a: Rational, b: Rational): Rational {
  const g = gcd(a.den, b.den)
  const aScale = b.den / g
  const bScale = a.den / g
  return rational(safeAdd(safeMul(a.num, aScale), safeMul(b.num, bScale)), safeMul(a.den, aScale))
}

export function sub(a: Rational, b: Rational): Rational {
  return add(a, rational(-b.num, b.den))
}

/** Returns -1, 0 or 1. */
export function compare(a: Rational, b: Rational): -1 | 0 | 1 {
  const d = sub(a, b).num
  return d < 0 ? -1 : d > 0 ? 1 : 0
}

export function equals(a: Rational, b: Rational): boolean {
  return a.num === b.num && a.den === b.den
}

/** Exact floor using integer remainder, never float division. */
export function floor(r: Rational): number {
  const m = ((r.num % r.den) + r.den) % r.den
  return (r.num - m) / r.den
}

export function ceil(r: Rational): number {
  const f = floor(r)
  return r.num % r.den === 0 ? f : f + 1
}

/** Round to nearest integer, with exact halves going toward +∞ (consistent for negative frames). */
export function roundHalfUp(r: Rational): number {
  return floor(add(r, rational(1, 2)))
}

export function isInteger(r: Rational): boolean {
  return r.den === 1
}

/** For display and non-timing maths only. Never store the result as a time value. */
export function toNumber(r: Rational): number {
  return r.num / r.den
}

export function toString(r: Rational): string {
  return r.den === 1 ? `${r.num}` : `${r.num}/${r.den}`
}
