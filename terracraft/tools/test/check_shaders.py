#!/usr/bin/env python3
"""Compiles and links every program of the TerraCraft Radiance shader pack on the local OpenGL driver.

    DISPLAY=:99 python3 tools/test/check_shaders.py [shaderpack/shaders]

Includes are resolved the way Iris does (paths starting with / are relative to the shaders folder). Every option
combination listed in TOGGLES is compiled as well, so disabled features cannot hide errors. Needs PyOpenGL, glfw
and a display (Xvfb works; Mesa's llvmpipe provides a 4.5 compatibility context).
"""
import os
import re
import sys

import glfw
from OpenGL import GL

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../shaderpack/shaders')
TOGGLES = ['VOLUMETRIC_LIGHT', 'WATER_REFLECTIONS', 'WAVING_PLANTS', 'CLOUDS']


def expand(path, seen=()):
    out = []
    for line in open(path).read().splitlines():
        m = re.match(r'\s*#include\s+"([^"]+)"', line)
        if m:
            inc = m.group(1)
            full = os.path.join(ROOT, inc.lstrip('/')) if inc.startswith('/') else os.path.join(os.path.dirname(path), inc)
            out.append(expand(full, seen + (path,)))
        else:
            out.append(line)
    return '\n'.join(out)


def disable(source, toggle):
    return re.sub(rf'^#define {toggle}\s*$', f'// #define {toggle}', source, flags=re.M)


def compile_stage(kind, source, label):
    shader = GL.glCreateShader(kind)
    GL.glShaderSource(shader, source)
    GL.glCompileShader(shader)
    if not GL.glGetShaderiv(shader, GL.GL_COMPILE_STATUS):
        log = GL.glGetShaderInfoLog(shader).decode()
        raise RuntimeError(f'{label}:\n{log}')
    return shader


def main():
    global ROOT
    if len(sys.argv) > 1:
        ROOT = sys.argv[1]
    if not glfw.init():
        sys.exit('glfw init failed (is DISPLAY set?)')
    glfw.window_hint(glfw.VISIBLE, glfw.FALSE)
    glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR, 4)
    glfw.window_hint(glfw.CONTEXT_VERSION_MINOR, 5)
    glfw.window_hint(glfw.OPENGL_PROFILE, glfw.OPENGL_COMPAT_PROFILE)
    window = glfw.create_window(64, 64, 'check', None, None)
    if not window:
        sys.exit('could not create a GL 4.5 compatibility context')
    glfw.make_context_current(window)
    print('GL', GL.glGetString(GL.GL_VERSION).decode(), '-', GL.glGetString(GL.GL_RENDERER).decode())

    programs = sorted({f[:-4] for f in os.listdir(ROOT) if f.endswith('.vsh')})
    failures = 0
    variants = [()] + [(t,) for t in TOGGLES]
    for name in programs:
        vsh = expand(os.path.join(ROOT, name + '.vsh'))
        fsh = expand(os.path.join(ROOT, name + '.fsh'))
        for off in variants:
            v, f = vsh, fsh
            for t in off:
                v, f = disable(v, t), disable(f, t)
            label = name + (f' (without {off[0]})' if off else '')
            try:
                vs = compile_stage(GL.GL_VERTEX_SHADER, v, label + '.vsh')
                fs = compile_stage(GL.GL_FRAGMENT_SHADER, f, label + '.fsh')
                prog = GL.glCreateProgram()
                GL.glAttachShader(prog, vs)
                GL.glAttachShader(prog, fs)
                GL.glLinkProgram(prog)
                if not GL.glGetProgramiv(prog, GL.GL_LINK_STATUS):
                    raise RuntimeError(f'{label} link:\n{GL.glGetProgramInfoLog(prog).decode()}')
            except RuntimeError as e:
                failures += 1
                print('FAIL', e)
        print('ok  ', name)
    glfw.terminate()
    print(f'{len(programs)} programs, {failures} failures')
    sys.exit(1 if failures else 0)


if __name__ == '__main__':
    main()
