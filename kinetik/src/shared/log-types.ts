export const LOG_LEVELS = ['debug', 'info', 'warn', 'error'] as const
export type LogLevel = (typeof LOG_LEVELS)[number]

export type LogContext = Record<string, unknown>

export interface LogEntry {
  /** ISO timestamp. */
  t: string
  level: LogLevel
  /** Which process or worker wrote the line: "main", "renderer", "render-worker", ... */
  src: string
  msg: string
  ctx?: LogContext
}

export interface Logger {
  debug(msg: string, ctx?: LogContext): void
  info(msg: string, ctx?: LogContext): void
  warn(msg: string, ctx?: LogContext): void
  error(msg: string, ctx?: LogContext): void
}

export function isLogLevel(value: unknown): value is LogLevel {
  return typeof value === 'string' && (LOG_LEVELS as readonly string[]).includes(value)
}

export function levelRank(level: LogLevel): number {
  return LOG_LEVELS.indexOf(level)
}
