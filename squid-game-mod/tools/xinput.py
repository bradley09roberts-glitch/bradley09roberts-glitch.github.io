#!/usr/bin/env python3
"""Synthetic mouse/keyboard input for the headless test client (XTest on the Xvfb display).

  tools/xinput.py [--display :98] move X Y
  tools/xinput.py click X Y [button=1]
  tools/xinput.py drag X1 Y1 X2 Y2 [steps=30]
  tools/xinput.py path X1,Y1 X2,Y2 ... [--delay 0.02]      press at the first point, move through the others, release
  tools/xinput.py down [button] | up [button]
  tools/xinput.py key Escape|e|space|F3 ...                 press+release (names are X keysyms)
  tools/xinput.py hold KEY SECONDS
Coordinates are screen pixels of the 1280x720 client window. The Minecraft cursor is captured (invisible) while no screen is
open, so these are mostly useful for GUI screens (respawn button, dalgona carving, marble screens ...).
"""
import argparse
import sys
import time

from Xlib import X, XK, display
from Xlib.ext import xtest


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--display", default=":98")
    ap.add_argument("--delay", type=float, default=0.02)
    ap.add_argument("cmd")
    ap.add_argument("args", nargs="*")
    a = ap.parse_args()
    d = display.Display(a.display)

    def move(x, y):
        xtest.fake_input(d, X.MotionNotify, x=int(x), y=int(y))
        d.sync()

    def button(b, down):
        xtest.fake_input(d, X.ButtonPress if down else X.ButtonRelease, int(b))
        d.sync()

    def key(name, down):
        sym = XK.string_to_keysym(name)
        if sym == 0:
            sys.exit("unknown key " + name)
        code = d.keysym_to_keycode(sym)
        xtest.fake_input(d, X.KeyPress if down else X.KeyRelease, code)
        d.sync()

    c, args = a.cmd, a.args
    if c == "move":
        move(args[0], args[1])
    elif c == "click":
        move(args[0], args[1])
        time.sleep(0.15)
        b = int(args[2]) if len(args) > 2 else 1
        button(b, True)
        time.sleep(0.08)
        button(b, False)
    elif c == "drag":
        x1, y1, x2, y2 = (float(v) for v in args[:4])
        steps = int(args[4]) if len(args) > 4 else 30
        move(x1, y1)
        time.sleep(0.1)
        button(1, True)
        for i in range(1, steps + 1):
            move(x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps)
            time.sleep(a.delay)
        button(1, False)
    elif c == "path":
        pts = [tuple(float(v) for v in p.split(",")) for p in args]
        move(*pts[0])
        time.sleep(0.1)
        button(1, True)
        for p in pts[1:]:
            move(*p)
            time.sleep(a.delay)
        button(1, False)
    elif c in ("down", "up"):
        button(int(args[0]) if args else 1, c == "down")
    elif c == "key":
        for k in args:
            key(k, True)
            time.sleep(0.05)
            key(k, False)
            time.sleep(0.05)
    elif c == "hold":
        key(args[0], True)
        time.sleep(float(args[1]))
        key(args[0], False)
    else:
        sys.exit("unknown command " + c)
    d.sync()


if __name__ == "__main__":
    main()
