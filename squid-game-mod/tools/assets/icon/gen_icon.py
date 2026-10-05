#!/usr/bin/env python3
"""Generates the mod icon (128x128) shown in the mod list: the three guard symbols on the pink/green palette of the series.
   python3 tools/assets/icon/gen_icon.py   ->  src/main/resources/assets/squidgame/icon.png"""
import math
import os
from PIL import Image, ImageDraw

S = 512  # drawn large, then downsampled for smooth edges
img = Image.new("RGB", (S, S), (20, 74, 62))          # tracksuit green
d = ImageDraw.Draw(img)
# stripe of the tracksuit
d.rectangle([0, 0, S, 38], fill=(238, 238, 232))
d.rectangle([0, S - 38, S, S], fill=(238, 238, 232))
# pink panel
d.rounded_rectangle([48, 96, S - 48, S - 96], radius=34, fill=(236, 62, 138))
cy = S // 2
pink_dark = (120, 20, 70)
cream = (250, 232, 238)
# circle, triangle, square (the guards' masks)
d.ellipse([88, cy - 62, 212, cy + 62], outline=cream, width=16)
tri = [(S // 2, cy - 70), (S // 2 - 66, cy + 58), (S // 2 + 66, cy + 58)]
d.polygon(tri, outline=cream, width=16)
d.line(tri + [tri[0]], fill=cream, width=16, joint="curve")
d.rectangle([S - 212, cy - 62, S - 88, cy + 62], outline=cream, width=16)
# soft shadow line under the symbols
d.rectangle([88, cy + 86, S - 88, cy + 96], fill=pink_dark)
out = img.resize((128, 128), Image.LANCZOS)
dest = os.path.join(os.path.dirname(__file__), "..", "..", "..", "src", "main", "resources", "assets", "squidgame", "icon.png")
out.save(os.path.normpath(dest))
print("wrote", os.path.normpath(dest))
