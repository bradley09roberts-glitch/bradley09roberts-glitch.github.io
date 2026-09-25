import { mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createFileLogger, logFileName, pruneOldLogs } from './logger'

let dir: string

beforeEach(async () => {
  dir = await mkdtemp(join(tmpdir(), 'kinetik-log-'))
})

afterEach(async () => {
  await rm(dir, { recursive: true, force: true })
})

describe('file logger', () => {
  it('writes JSON lines at or above the minimum level', async () => {
    const now = new Date('2026-09-25T12:00:00.000Z')
    const logger = await createFileLogger({ dir, minLevel: 'info', now: () => now })
    logger.debug('hidden')
    logger.info('hello', { a: 1 })
    logger.error('boom', { err: new Error('bad') })
    logger.write({ t: now.toISOString(), level: 'warn', src: 'renderer', msg: 'from ui' })
    await logger.flush()

    const lines = (await readFile(join(dir, logFileName(now)), 'utf8')).trim().split('\n')
    expect(lines).toHaveLength(3)
    const parsed = lines.map((l) => JSON.parse(l) as Record<string, unknown>)
    expect(parsed[0]).toMatchObject({ level: 'info', src: 'main', msg: 'hello', ctx: { a: 1 } })
    expect(parsed[1]).toMatchObject({ level: 'error', ctx: { err: { message: 'bad' } } })
    expect(parsed[2]).toMatchObject({ level: 'warn', src: 'renderer' })
  })

  it('survives unserialisable context', async () => {
    const logger = await createFileLogger({ dir, minLevel: 'debug' })
    const circular: Record<string, unknown> = {}
    circular.self = circular
    logger.info('circular', { circular })
    await logger.flush()
    const [name] = await readdir(dir)
    const line = JSON.parse((await readFile(join(dir, name!), 'utf8')).trim()) as {
      ctx: unknown
    }
    expect(line.ctx).toEqual({ unserializable: true })
  })

  it('prunes log files older than the retention period', async () => {
    const now = new Date('2026-09-25T12:00:00.000Z')
    for (const day of ['2026-09-10', '2026-09-17', '2026-09-18', '2026-09-24']) {
      await writeFile(join(dir, `kinetik-${day}.log`), '{}\n')
    }
    await writeFile(join(dir, 'unrelated.txt'), 'keep me')
    const removed = await pruneOldLogs(dir, 7, now)
    expect(removed.sort()).toEqual(['kinetik-2026-09-10.log', 'kinetik-2026-09-17.log'])
    expect((await readdir(dir)).sort()).toEqual([
      'kinetik-2026-09-18.log',
      'kinetik-2026-09-24.log',
      'unrelated.txt',
    ])
  })
})
