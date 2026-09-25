/**
 * JSON-lines file logger (SPEC §3.7). One file per day under <userData>/logs, kept for 7 days.
 *
 * Writes are queued as async appends so logging never blocks the main process. Call `flush()` to
 * wait for queued lines (tests, shutdown).
 */
import { appendFile, mkdir, readdir, rm } from 'node:fs/promises'
import { join } from 'node:path'
import {
  type LogContext,
  type LogEntry,
  type LogLevel,
  type Logger,
  levelRank,
} from '@shared/log-types'

const FILE_PATTERN = /^kinetik-(\d{4}-\d{2}-\d{2})\.log$/
const DAY_MS = 24 * 60 * 60 * 1000

export interface FileLoggerOptions {
  dir: string
  minLevel: LogLevel
  retentionDays?: number
  /** Also print lines to stdout/stderr (development). */
  echo?: boolean
  now?: () => Date
}

export interface FileLogger extends Logger {
  write(entry: LogEntry): void
  flush(): Promise<void>
  readonly dir: string
}

export function logFileName(date: Date): string {
  return `kinetik-${date.toISOString().slice(0, 10)}.log`
}

/** Turns Error objects (which JSON.stringify drops) into plain data. */
function toSerializable(ctx: LogContext): LogContext {
  const out: LogContext = {}
  for (const [key, value] of Object.entries(ctx)) {
    out[key] =
      value instanceof Error
        ? { name: value.name, message: value.message, stack: value.stack }
        : value
  }
  return out
}

function formatLine(entry: LogEntry): string {
  try {
    return JSON.stringify(entry.ctx ? { ...entry, ctx: toSerializable(entry.ctx) } : entry)
  } catch {
    // Circular or otherwise unserialisable context: keep the message, drop the context.
    return JSON.stringify({ ...entry, ctx: { unserializable: true } })
  }
}

export async function pruneOldLogs(
  dir: string,
  retentionDays: number,
  now: Date,
): Promise<string[]> {
  const cutoff = now.getTime() - retentionDays * DAY_MS
  const removed: string[] = []
  let names: string[]
  try {
    names = await readdir(dir)
  } catch {
    return removed
  }
  for (const name of names) {
    const match = FILE_PATTERN.exec(name)
    if (!match) continue
    const day = Date.parse(`${match[1]}T00:00:00.000Z`)
    if (Number.isFinite(day) && day + DAY_MS <= cutoff) {
      await rm(join(dir, name), { force: true })
      removed.push(name)
    }
  }
  return removed
}

export async function createFileLogger(opts: FileLoggerOptions): Promise<FileLogger> {
  const now = opts.now ?? (() => new Date())
  await mkdir(opts.dir, { recursive: true })
  await pruneOldLogs(opts.dir, opts.retentionDays ?? 7, now())

  const minRank = levelRank(opts.minLevel)
  let queue: Promise<void> = Promise.resolve()

  const write = (entry: LogEntry): void => {
    if (levelRank(entry.level) < minRank) return
    const line = `${formatLine(entry)}\n`
    if (opts.echo) {
      const stream = entry.level === 'error' || entry.level === 'warn' ? console.error : console.log
      stream(`[${entry.src}] ${entry.level}: ${entry.msg}`, entry.ctx ?? '')
    }
    const file = join(opts.dir, logFileName(new Date(entry.t)))
    queue = queue.then(() =>
      appendFile(file, line, 'utf8').catch((err: unknown) => {
        console.error('kinetik logger: failed to write log line', err)
      }),
    )
  }

  const at =
    (level: LogLevel) =>
    (msg: string, ctx?: LogContext): void =>
      write({ t: now().toISOString(), level, src: 'main', msg, ...(ctx ? { ctx } : {}) })

  return {
    dir: opts.dir,
    write,
    flush: () => queue,
    debug: at('debug'),
    info: at('info'),
    warn: at('warn'),
    error: at('error'),
  }
}

/**
 * Process-wide logger used by main-process modules. Starts as a console logger and is swapped for
 * the file logger once `app` is ready and the userData path is known.
 */
let current: Logger = {
  debug: (msg, ctx) => console.debug(`[main] debug: ${msg}`, ctx ?? ''),
  info: (msg, ctx) => console.info(`[main] info: ${msg}`, ctx ?? ''),
  warn: (msg, ctx) => console.warn(`[main] warn: ${msg}`, ctx ?? ''),
  error: (msg, ctx) => console.error(`[main] error: ${msg}`, ctx ?? ''),
}

export function setLogger(logger: Logger): void {
  current = logger
}

export const log: Logger = {
  debug: (msg, ctx) => current.debug(msg, ctx),
  info: (msg, ctx) => current.info(msg, ctx),
  warn: (msg, ctx) => current.warn(msg, ctx),
  error: (msg, ctx) => current.error(msg, ctx),
}
