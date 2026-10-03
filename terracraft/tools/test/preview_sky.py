#!/usr/bin/env python3
"""Renders the TerraCraft Radiance sky + post-processing (composite, composite1, final) offscreen at a few times
of day, to check how the atmosphere, clouds, stars and tone mapping look without running Minecraft.

    DISPLAY=:99 python3 tools/test/preview_sky.py out.png
"""
import math
import os
import sys

import glfw
import numpy as np
from OpenGL import GL
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from check_shaders import ROOT, expand  # noqa: E402

W, H = 640, 360


def compile_program(name):
    def stage(kind, src):
        s = GL.glCreateShader(kind)
        GL.glShaderSource(s, src)
        GL.glCompileShader(s)
        if not GL.glGetShaderiv(s, GL.GL_COMPILE_STATUS):
            raise RuntimeError(GL.glGetShaderInfoLog(s).decode())
        return s
    p = GL.glCreateProgram()
    GL.glAttachShader(p, stage(GL.GL_VERTEX_SHADER, expand(os.path.join(ROOT, name + '.vsh'))))
    GL.glAttachShader(p, stage(GL.GL_FRAGMENT_SHADER, expand(os.path.join(ROOT, name + '.fsh'))))
    GL.glLinkProgram(p)
    return p


def texture(fmt, w, h, data=None, ftype=GL.GL_FLOAT, ifmt=GL.GL_RGBA16F, mip=False):
    t = GL.glGenTextures(1)
    GL.glBindTexture(GL.GL_TEXTURE_2D, t)
    GL.glTexImage2D(GL.GL_TEXTURE_2D, 0, ifmt, w, h, 0, fmt, ftype, data)
    GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR if mip else GL.GL_LINEAR)
    GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
    GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE)
    GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE)
    return t


def perspective(fov, aspect, near, far):
    f = 1.0 / math.tan(math.radians(fov) / 2)
    return np.array([[f / aspect, 0, 0, 0], [0, f, 0, 0], [0, 0, (far + near) / (near - far), 2 * far * near / (near - far)], [0, 0, -1, 0]],
                    dtype=np.float32)


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], dtype=np.float32)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], dtype=np.float32)


def set_uniforms(p, u):
    GL.glUseProgram(p)
    for name, value in u.items():
        loc = GL.glGetUniformLocation(p, name)
        if loc < 0:
            continue
        if isinstance(value, np.ndarray) and value.shape == (4, 4):
            GL.glUniformMatrix4fv(loc, 1, GL.GL_TRUE, value)
        elif isinstance(value, tuple) and len(value) == 3:
            GL.glUniform3f(loc, *value)
        elif isinstance(value, tuple) and len(value) == 2 and isinstance(value[0], int):
            GL.glUniform2i(loc, *value)
        elif isinstance(value, int):
            GL.glUniform1i(loc, value)
        else:
            GL.glUniform1f(loc, value)


def quad():
    GL.glBegin(GL.GL_QUADS)
    for x, y in ((0, 0), (1, 0), (1, 1), (0, 1)):
        GL.glMultiTexCoord2f(GL.GL_TEXTURE0, x, y)
        GL.glVertex2f(x * 2 - 1, y * 2 - 1)
    GL.glEnd()


def render(sun_angle_deg, yaw_deg, pitch_deg, rain=0.0, time=200.0):
    comp, comp1, final = compile_program('composite'), compile_program('composite1'), compile_program('final')
    zeros = np.zeros((H, W, 4), dtype=np.float32)
    scene = texture(GL.GL_RGBA, W, H, zeros)
    material = texture(GL.GL_RGBA, W, H, zeros)
    depth = texture(GL.GL_RED, W, H, np.ones((H, W), dtype=np.float32), ifmt=GL.GL_R32F)
    shadow = texture(GL.GL_RED, 4, 4, np.ones((4, 4), dtype=np.float32), ifmt=GL.GL_R32F)
    white = texture(GL.GL_RGBA, 4, 4, np.ones((4, 4, 4), dtype=np.float32))
    lit = texture(GL.GL_RGBA, W, H, None)
    bloom = texture(GL.GL_RGBA, W, H, None, mip=True)
    out = texture(GL.GL_RGBA, W, H, None, ftype=GL.GL_UNSIGNED_BYTE, ifmt=GL.GL_RGBA8)
    fbo = GL.glGenFramebuffers(1)

    view = rot_x(math.radians(pitch_deg)) @ rot_y(math.radians(yaw_deg))
    proj = perspective(70, W / H, 0.05, 512.0)
    a = math.radians(sun_angle_deg)
    tilt = math.radians(-25.0)
    sun_world = np.array([math.cos(a), math.sin(a) * math.cos(tilt), math.sin(a) * math.sin(tilt), 0.0])
    sun_view = (view @ sun_world)[:3] * 100.0
    light_view = sun_view if sun_world[1] > 0 else -sun_view
    u = {'gbufferModelView': view, 'gbufferModelViewInverse': np.linalg.inv(view), 'gbufferProjection': proj,
         'gbufferProjectionInverse': np.linalg.inv(proj), 'shadowModelView': np.eye(4, dtype=np.float32),
         'shadowProjection': np.eye(4, dtype=np.float32) * 0.01, 'cameraPosition': (0.0, 70.0, 0.0), 'sunPosition': tuple(sun_view),
         'shadowLightPosition': tuple(light_view), 'frameTimeCounter': time, 'rainStrength': rain, 'viewWidth': float(W),
         'viewHeight': float(H), 'near': 0.05, 'far': 256.0, 'isEyeInWater': 0, 'eyeBrightnessSmooth': (240, 240),
         'colortex0': 0, 'colortex1': 1, 'depthtex0': 2, 'depthtex1': 2, 'shadowtex0': 3, 'shadowtex1': 3, 'shadowcolor0': 4,
         'colortex2': 5}
    GL.glViewport(0, 0, W, H)
    for prog, inputs, target in ((comp, {0: scene, 1: material, 2: depth, 3: shadow, 4: white}, lit),
                                 (comp1, {0: lit}, bloom),
                                 (final, {0: lit, 5: bloom}, out)):
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo)
        GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, target, 0)
        for unit, tex in inputs.items():
            GL.glActiveTexture(GL.GL_TEXTURE0 + unit)
            GL.glBindTexture(GL.GL_TEXTURE_2D, tex)
        set_uniforms(prog, u)
        quad()
        if target == bloom:
            GL.glBindTexture(GL.GL_TEXTURE_2D, bloom)
            GL.glGenerateMipmap(GL.GL_TEXTURE_2D)
    GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo)
    pixels = GL.glReadPixels(0, 0, W, H, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE)
    img = Image.frombytes('RGBA', (W, H), pixels).transpose(Image.Transpose.FLIP_TOP_BOTTOM).convert('RGB')
    return img


def main():
    glfw.init()
    glfw.window_hint(glfw.VISIBLE, glfw.FALSE)
    glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR, 4)
    glfw.window_hint(glfw.CONTEXT_VERSION_MINOR, 5)
    glfw.window_hint(glfw.OPENGL_PROFILE, glfw.OPENGL_COMPAT_PROFILE)
    window = glfw.create_window(W, H, 'preview', None, None)
    glfw.make_context_current(window)
    shots = [('Noon', 70, 200, -25), ('Afternoon', 30, 260, -8), ('Sunset', 4, 270, -4), ('Dusk', -6, 270, -6),
             ('Night', -60, 180, -30), ('Rain', 40, 230, -10)]
    tiles = []
    for label, sun, yaw, pitch in shots:
        img = render(sun, yaw, pitch, rain=0.8 if label == 'Rain' else 0.0)
        tiles.append((label, img))
    sheet = Image.new('RGB', (W * 2, H * 3))
    from PIL import ImageDraw
    for i, (label, img) in enumerate(tiles):
        ImageDraw.Draw(img).text((10, 10), label, fill=(255, 255, 255))
        sheet.paste(img, ((i % 2) * W, (i // 2) * H))
    sheet.save(sys.argv[1] if len(sys.argv) > 1 else 'sky_preview.png')
    glfw.terminate()


if __name__ == '__main__':
    main()
