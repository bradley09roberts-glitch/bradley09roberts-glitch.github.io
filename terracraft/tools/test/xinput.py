"""Synthetic X input for the headless client: python3 xin.py key r | click left 640 360 | hold left 2.0 | move 640 360 | scroll up 3"""
import sys, time
from Xlib import X, XK, display
from Xlib.ext import xtest
d = display.Display(':99')
def key(name, dur=0.08):
    code = d.keysym_to_keycode(XK.string_to_keysym(name))
    xtest.fake_input(d, X.KeyPress, code); d.sync(); time.sleep(dur)
    xtest.fake_input(d, X.KeyRelease, code); d.sync()
def move(x, y):
    xtest.fake_input(d, X.MotionNotify, x=int(x), y=int(y)); d.sync()
def button(b, dur=0.08):
    n = {'left': 1, 'middle': 2, 'right': 3, 'up': 4, 'down': 5}[b]
    xtest.fake_input(d, X.ButtonPress, n); d.sync(); time.sleep(dur)
    xtest.fake_input(d, X.ButtonRelease, n); d.sync()
args = sys.argv[1:]
i = 0
while i < len(args):
    a = args[i]
    if a == 'key': key(args[i+1]); i += 2
    elif a == 'keyhold': key(args[i+1], float(args[i+2])); i += 3
    elif a == 'click': move(args[i+2], args[i+3]); time.sleep(0.05); button(args[i+1]); i += 4
    elif a == 'hold': button(args[i+1], float(args[i+2])); i += 3
    elif a == 'move': move(args[i+1], args[i+2]); i += 3
    elif a == 'scroll':
        for _ in range(int(args[i+2])): button(args[i+1])
        i += 3
    elif a == 'shiftclick':
        sh = d.keysym_to_keycode(XK.string_to_keysym('Shift_L'))
        move(args[i+1], args[i+2]); time.sleep(0.05)
        xtest.fake_input(d, X.KeyPress, sh); d.sync(); time.sleep(0.05)
        button('left'); time.sleep(0.05)
        xtest.fake_input(d, X.KeyRelease, sh); d.sync(); i += 3
    elif a == 'sleep': time.sleep(float(args[i+1])); i += 2
    else: raise SystemExit('unknown ' + a)
