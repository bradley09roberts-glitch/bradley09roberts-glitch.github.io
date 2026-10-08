#!/usr/bin/env python3
"""Turns the in-game test's time-lapse frames into a captioned video.

The camp client test, run with -Dhardcorefriends.timelapseTicks=N, saves a screenshot every N ticks
(build/run/clientGameTest/screenshots/tl-NNNN.png) and a JSON line per frame describing the clock, weather and what
every friend is doing, feeling and saying (build/run/clientGameTest/test-reports/timelapse.jsonl). This script draws
those captions onto each frame and encodes an MP4 with ffmpeg.

Usage: python3 tools/make_timelapse.py [--fps 10] [--out release/timelapse.mp4] [--stills N]
"""
import argparse
import json
import pathlib
import shutil
import subprocess
import tempfile

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
RUN = ROOT / "build" / "run" / "clientGameTest"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
COLOURS = {
    "Fern": 0x6BBF59, "Oak": 0xC08A4E, "Flint": 0x9AA3AD, "Scout": 0x3FB0AC, "Spark": 0xE0533D,
    "Aegis": 0x6F95D6, "Sage": 0xB39DDB, "Terra": 0xC27BA0, "Rowan": 0x8DB255,
}
MOOD_COLOURS = {"great": (120, 230, 120), "good": (190, 230, 140), "okay": (230, 220, 150),
                "low": (240, 170, 110), "miserable": (240, 110, 110)}
SPEECH_FRAMES = 4  # how many frames a spoken line stays on screen


def rgb(colour):
    return (colour >> 16) & 255, (colour >> 8) & 255, colour & 255


def fit(draw, text, font, width):
    """Shortens text with an ellipsis until it fits the width."""
    if draw.textlength(text, font=font) <= width:
        return text
    while text and draw.textlength(text + "…", font=font) > width:
        text = text[:-1]
    return text + "…"


def caption(frame_path, info, recent, out_path):
    img = Image.open(frame_path).convert("RGB")
    w, h = img.size
    scale = w / 1280
    small = ImageFont.truetype(FONT, int(15 * scale))
    bold = ImageFont.truetype(FONT_BOLD, int(15 * scale))
    big = ImageFont.truetype(FONT_BOLD, int(22 * scale))
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)

    # Top bar: clock, weather, camp.
    top = f"Day {info['day']}  {info['clock']}  ·  {info['weather']}  ·  camp: {info['stage']}  ·  Unity {info['unity']}"
    d.rectangle([0, 0, w, int(36 * scale)], fill=(0, 0, 0, 150))
    d.text((int(12 * scale), int(6 * scale)), top, font=big, fill=(255, 255, 255))

    # Bottom panel: the nine friends in a 3x3 grid, then the latest lines of speech.
    rows = 3
    line_h = int(22 * scale)
    speech_lines = recent[-3:]
    panel_h = rows * line_h + len(speech_lines) * line_h + int(16 * scale)
    y0 = h - panel_h
    d.rectangle([0, y0, w, h], fill=(0, 0, 0, 165))
    col_w = w // 3
    for i, f in enumerate(info["friends"]):
        x = (i % 3) * col_w + int(10 * scale)
        y = y0 + int(6 * scale) + (i // 3) * line_h
        name = f["name"]
        d.text((x, y), name, font=bold, fill=rgb(COLOURS.get(name, 0xFFFFFF)))
        nx = x + d.textlength(name + " ", font=bold)
        mood = f.get("mood")
        status = f.get("activity", "")
        if mood:
            mood_text = f"[{mood}] "
            d.text((nx, y), mood_text, font=small, fill=MOOD_COLOURS.get(mood, (220, 220, 220)))
            nx += d.textlength(mood_text, font=small)
        d.text((nx, y), fit(d, status, small, col_w - (nx - x) - int(14 * scale)), font=small, fill=(235, 235, 235))
    y = y0 + int(10 * scale) + rows * line_h
    for line in speech_lines:
        who, _, said = line.partition(": ")
        d.text((int(12 * scale), y), who + ":", font=bold, fill=rgb(COLOURS.get(who, 0xFFFFFF)))
        sx = int(12 * scale) + d.textlength(who + ": ", font=bold)
        d.text((sx, y), fit(d, said, small, w - sx - int(12 * scale)), font=small, fill=(255, 255, 230))
        y += line_h

    Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB").save(out_path, quality=92)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fps", type=int, default=10)
    ap.add_argument("--out", default=str(ROOT / "release" / "timelapse.mp4"))
    ap.add_argument("--stills", type=int, default=0, help="also save every Nth captioned frame as a still")
    ap.add_argument("--run", default=str(RUN), help="the client test run folder")
    args = ap.parse_args()

    run = pathlib.Path(args.run)
    infos = [json.loads(l) for l in (run / "test-reports" / "timelapse.jsonl").read_text().splitlines() if l.strip()]
    shots = run / "screenshots"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    stills_dir = out.parent / "timelapse-stills"
    if args.stills:
        stills_dir.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        recent = []
        stay = 0
        n = 0
        for info in infos:
            src = shots / f"tl-{info['frame']:04d}.png"
            if not src.exists():
                continue
            # Spoken lines stay on screen for a few frames, then fade.
            if info.get("said"):
                recent = (recent + info["said"])[-3:]
                stay = SPEECH_FRAMES
            elif stay > 0:
                stay -= 1
            else:
                recent = []
            n += 1
            dst = pathlib.Path(tmp) / f"f{n:05d}.jpg"
            caption(src, info, recent, dst)
            if args.stills and n % args.stills == 0:
                shutil.copy(dst, stills_dir / f"timelapse-day{info['day']}-{info['clock'].replace(':', '')}.jpg")
        if n == 0:
            raise SystemExit("no time-lapse frames found")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(args.fps), "-i", f"{tmp}/f%05d.jpg",
                        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "23", "-movflags", "+faststart", str(out)],
                       check=True)
    print(f"{out}: {n} frames at {args.fps} fps ({n / args.fps:.0f} s)")


if __name__ == "__main__":
    main()
