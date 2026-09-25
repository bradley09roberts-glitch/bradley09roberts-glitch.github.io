/**
 * Renderer logger: forwards to the main-process log file (SPEC §3.7). Use this, not console.*.
 */
import type { LogContext, LogLevel, Logger } from '@shared/log-types'

function send(level: LogLevel, msg: string, ctx?: LogContext): void {
  try {
    window.kinetik.log(level, msg, ctx ? toCloneable(ctx) : undefined)
  } catch {
    // The bridge is unavailable (e.g. a unit test); nothing useful to do.
  }
}

/** IPC uses structured clone, which cannot carry Error instances with their stack. */
function toCloneable(ctx: LogContext): LogContext {
  const out: LogContext = {}
  for (const [key, value] of Object.entries(ctx)) {
    out[key] =
      value instanceof Error
        ? { name: value.name, message: value.message, stack: value.stack }
        : value
  }
  return out
}

export const log: Logger = {
  debug: (msg, ctx) => send('debug', msg, ctx),
  info: (msg, ctx) => send('info', msg, ctx),
  warn: (msg, ctx) => send('warn', msg, ctx),
  error: (msg, ctx) => send('error', msg, ctx),
}

export function installGlobalErrorLogging(): void {
  window.addEventListener('error', (event) => {
    log.error('uncaught error (renderer)', {
      message: event.message,
      source: event.filename,
      line: event.lineno,
      err: event.error instanceof Error ? event.error : undefined,
    })
  })
  window.addEventListener('unhandledrejection', (event) => {
    const reason: unknown = event.reason
    log.error('unhandled rejection (renderer)', {
      reason: reason instanceof Error ? reason : String(reason),
    })
  })
}
