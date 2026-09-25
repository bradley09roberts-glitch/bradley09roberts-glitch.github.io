/**
 * Locates the FFmpeg/ffprobe binaries and checks they run.
 *
 * Resolution order: KINETIK_FFMPEG / KINETIK_FFPROBE environment variables, then the binaries
 * bundled by ffmpeg-static / ffprobe-static. A "use system FFmpeg" preference arrives with the
 * Preferences window (SPEC §15).
 */
import { execFile } from 'node:child_process'
import ffmpegStatic from 'ffmpeg-static'
import ffprobeStatic from 'ffprobe-static'
import type { FfmpegStatus } from '@shared/ipc'

/** Inside a packaged app, binaries live in app.asar.unpacked, not the (unexecutable) asar. */
function unpacked(path: string): string {
  return path.replace(/app\.asar([\\/])/, 'app.asar.unpacked$1')
}

export function resolveFfmpegPath(): string | null {
  const override = process.env.KINETIK_FFMPEG
  if (override) return override
  return ffmpegStatic ? unpacked(ffmpegStatic) : null
}

export function resolveFfprobePath(): string | null {
  const override = process.env.KINETIK_FFPROBE
  if (override) return override
  return ffprobeStatic.path ? unpacked(ffprobeStatic.path) : null
}

export function checkBinary(path: string | null, timeoutMs = 10_000): Promise<FfmpegStatus> {
  if (!path) {
    return Promise.resolve({
      ok: false,
      version: null,
      path: null,
      error: 'No binary for this platform.',
    })
  }
  return new Promise((resolve) => {
    execFile(path, ['-version'], { timeout: timeoutMs, windowsHide: true }, (err, stdout) => {
      if (err) {
        resolve({ ok: false, version: null, path, error: err.message })
        return
      }
      // "ffmpeg version 7.0.2-static https://… Copyright …" → "ffmpeg version 7.0.2-static"
      const firstLine = stdout.split(/\r?\n/, 1)[0]?.trim() ?? ''
      const version = /^\S+ version \S+/.exec(firstLine)?.[0] ?? (firstLine || null)
      resolve({ ok: true, version, path, error: null })
    })
  })
}
