import { describe, expect, it } from 'vitest'
import { checkBinary, resolveFfmpegPath, resolveFfprobePath } from './ffmpeg'

describe('ffmpeg binaries', () => {
  it('finds and runs the bundled ffmpeg and ffprobe', async () => {
    const ffmpeg = await checkBinary(resolveFfmpegPath())
    expect(ffmpeg.ok, ffmpeg.error ?? '').toBe(true)
    expect(ffmpeg.version).toMatch(/^ffmpeg version /)

    const ffprobe = await checkBinary(resolveFfprobePath())
    expect(ffprobe.ok, ffprobe.error ?? '').toBe(true)
    expect(ffprobe.version).toMatch(/^ffprobe version /)
  })

  it('reports a missing binary without throwing', async () => {
    const status = await checkBinary('/definitely/not/here/ffmpeg')
    expect(status.ok).toBe(false)
    expect(status.error).toBeTruthy()
    expect((await checkBinary(null)).ok).toBe(false)
  })
})
