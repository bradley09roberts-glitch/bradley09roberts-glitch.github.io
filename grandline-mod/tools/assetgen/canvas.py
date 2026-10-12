"""Tiny dependency-free RGBA canvas, colour helpers and PNG reader/writer (zlib + struct only)."""
import colorsys
import math
import struct
import zlib

CLEAR = (0, 0, 0, 0)


# ----------------------------------------------------------------------------------------------- colours
def C(v, a=255):
    """Colour from '#rrggbb', 'rrggbb', '#rrggbbaa', 0xRRGGBB int or an (r,g,b[,a]) tuple."""
    if isinstance(v, tuple):
        return v if len(v) == 4 else (v[0], v[1], v[2], a)
    if isinstance(v, int):
        return ((v >> 16) & 255, (v >> 8) & 255, v & 255, a)
    s = v.lstrip('#')
    if len(s) == 8:
        return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), int(s[6:8], 16))
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def clamp(v, lo=0, hi=255):
    return lo if v < lo else hi if v > hi else v


def mix(c1, c2, t):
    c1, c2 = C(c1), C(c2)
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def with_alpha(c, a):
    c = C(c)
    return (c[0], c[1], c[2], clamp(int(a)))


def scale(c, f):
    c = C(c)
    return (clamp(int(c[0] * f)), clamp(int(c[1] * f)), clamp(int(c[2] * f)), c[3])


def grey(v, a=255):
    v = clamp(int(v))
    return (v, v, v, a)


def hsv(h, s, v, a=255):
    r, g, b = colorsys.hsv_to_rgb((h % 360) / 360.0, max(0.0, min(1.0, s)), max(0.0, min(1.0, v)))
    return (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)), a)


def to_hsv(c):
    c = C(c)
    h, s, v = colorsys.rgb_to_hsv(c[0] / 255.0, c[1] / 255.0, c[2] / 255.0)
    return h * 360.0, s, v


def _hue_toward(h, target, amount):
    d = ((target - h + 180) % 360) - 180
    step = max(-abs(amount), min(abs(amount), d))
    return h + step


def shade(c, k):
    """Hue-shifted lightness step: k>0 lighter + warmer, k<0 darker + cooler (pixel-art style ramps)."""
    h, s, v = to_hsv(c)
    a = C(c)[3]
    if s < 0.06:  # greys: plain value shift, a touch of coolness in the shadows
        if k >= 0:
            return hsv(h, s, v + (1 - v) * min(1.0, 0.33 * k), a)
        return hsv(h, s, v * (1 + 0.27 * k) if k > -3.6 else v * 0.04, a)
    if k >= 0:
        h2 = _hue_toward(h, 55, 7 * k)
        return hsv(h2, s * (1 - 0.13 * k), v + (1 - v) * min(1.0, 0.30 * k) + 0.03 * k, a)
    k = -k
    h2 = _hue_toward(h, 245, 8 * k)
    return hsv(h2, min(1.0, s * (1 + 0.10 * k) + 0.03 * k), v * max(0.05, 1 - 0.25 * k), a)


def ramp(base, out_dark=None, out_light=None):
    """Six-step material ramp: [outline_dark, outline_light, dark, mid, light, highlight]."""
    base = C(base)
    r = [shade(base, -3.0), shade(base, -2.1), shade(base, -1.0), base, shade(base, 1.0), shade(base, 2.1)]
    if out_dark is not None:
        r[0] = C(out_dark)
    if out_light is not None:
        r[1] = C(out_light)
    return r


def lum(c):
    c = C(c)
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


# ----------------------------------------------------------------------------------------------- canvas
class Canvas:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h = w, h
        self.px = [C(fill)] * (w * h)

    def copy(self):
        c = Canvas(self.w, self.h)
        c.px = list(self.px)
        return c

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def get(self, x, y):
        if not self.inside(x, y):
            return CLEAR
        return self.px[y * self.w + x]

    def set(self, x, y, c):
        x, y = int(x), int(y)
        if self.inside(x, y):
            self.px[y * self.w + x] = C(c)

    def blend(self, x, y, c):
        """Alpha-over composite of c onto the pixel."""
        x, y = int(x), int(y)
        if not self.inside(x, y):
            return
        c = C(c)
        if c[3] == 0:
            return
        if c[3] == 255:
            self.px[y * self.w + x] = c
            return
        d = self.px[y * self.w + x]
        sa = c[3] / 255.0
        da = d[3] / 255.0
        oa = sa + da * (1 - sa)
        if oa <= 0:
            return
        out = tuple(int(round((c[i] * sa + d[i] * da * (1 - sa)) / oa)) for i in range(3))
        self.px[y * self.w + x] = (out[0], out[1], out[2], int(round(oa * 255)))

    def add(self, x, y, c, k=1.0):
        """Additive light (keeps alpha = max)."""
        x, y = int(x), int(y)
        if not self.inside(x, y):
            return
        c = C(c)
        d = self.px[y * self.w + x]
        f = k * c[3] / 255.0
        self.px[y * self.w + x] = (clamp(int(d[0] + c[0] * f)), clamp(int(d[1] + c[1] * f)), clamp(int(d[2] + c[2] * f)),
                                   max(d[3], clamp(int(c[3] * k))))

    def rect(self, x0, y0, x1, y1, c):
        """Fill [x0,x1) x [y0,y1)."""
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.px[y * self.w + x] = C(c)

    def line(self, x0, y0, x1, y1, c, blend=False):
        x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            (self.blend if blend else self.set)(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def paste(self, other, ox, oy, blend=True):
        for y in range(other.h):
            for x in range(other.w):
                c = other.px[y * other.w + x]
                if blend:
                    self.blend(ox + x, oy + y, c)
                elif c[3]:
                    self.set(ox + x, oy + y, c)

    def crop(self, x0, y0, x1, y1):
        out = Canvas(x1 - x0, y1 - y0)
        for y in range(y0, y1):
            for x in range(x0, x1):
                out.set(x - x0, y - y0, self.get(x, y))
        return out

    def flip_h(self):
        out = Canvas(self.w, self.h)
        for y in range(self.h):
            for x in range(self.w):
                out.px[y * self.w + (self.w - 1 - x)] = self.px[y * self.w + x]
        return out

    def map(self, fn):
        for i, c in enumerate(self.px):
            self.px[i] = fn(c)
        return self

    def scaled(self, k):
        out = Canvas(self.w * k, self.h * k)
        for y in range(out.h):
            for x in range(out.w):
                out.px[y * out.w + x] = self.px[(y // k) * self.w + (x // k)]
        return out

    # ------------------------------------------------------------------------------------------- PNG io
    def png_bytes(self):
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)
            for x in range(self.w):
                raw.extend(self.px[y * self.w + x])

        def chunk(tag, data):
            body = tag + data
            return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xFFFFFFFF)

        return (b'\x89PNG\r\n\x1a\n'
                + chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(bytes(raw), 9))
                + chunk(b'IEND', b''))

    def save(self, path):
        with open(path, 'wb') as f:
            f.write(self.png_bytes())


def load_png(path):
    """Minimal PNG reader (8-bit greyscale/RGB/RGBA/palette, non-interlaced) -> Canvas."""
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    pos, idat, plte, trns = 8, b'', None, None
    w = h = depth = ctype = 0
    while pos < len(data):
        n, tag = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if tag == b'IHDR':
            w, h, depth, ctype, _, _, interlace = struct.unpack('>IIBBBBB', body)
            assert depth == 8 and interlace == 0, 'unsupported PNG'
        elif tag == b'PLTE':
            plte = body
        elif tag == b'tRNS':
            trns = body
        elif tag == b'IDAT':
            idat += body
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * bpp
    prev = bytearray(stride)
    out = Canvas(w, h)
    i = 0
    for y in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + ((a + b) >> 1)) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        for x in range(w):
            s = line[x * bpp:(x + 1) * bpp]
            if ctype == 6:
                px = tuple(s)
            elif ctype == 2:
                px = (s[0], s[1], s[2], 255)
            elif ctype == 0:
                px = (s[0], s[0], s[0], 255)
            elif ctype == 4:
                px = (s[0], s[0], s[0], s[1])
            else:
                k = s[0]
                al = trns[k] if trns is not None and k < len(trns) else 255
                px = (plte[k * 3], plte[k * 3 + 1], plte[k * 3 + 2], al)
            out.px[y * w + x] = px
        prev = line
    return out


# ----------------------------------------------------------------------------------------------- misc
class Rand:
    """Deterministic xorshift RNG so assets are reproducible byte-for-byte."""

    def __init__(self, seed):
        self.s = (zlib.crc32(str(seed).encode()) or 1) & 0xFFFFFFFF

    def u32(self):
        s = self.s
        s ^= (s << 13) & 0xFFFFFFFF
        s ^= s >> 17
        s ^= (s << 5) & 0xFFFFFFFF
        self.s = s & 0xFFFFFFFF
        return self.s

    def random(self):
        return self.u32() / 4294967296.0

    def randint(self, a, b):
        return a + self.u32() % (b - a + 1)

    def choice(self, seq):
        return seq[self.u32() % len(seq)]

    def chance(self, p):
        return self.random() < p


def hash01(x, y, seed=0):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFFFF) / float(0x1000000)


def value_noise(x, y, seed=0, period=None):
    """Smooth tileable value noise in [0,1)."""
    x0, y0 = math.floor(x), math.floor(y)
    fx, fy = x - x0, y - y0

    def h(ix, iy):
        if period:
            ix %= period
            iy %= period
        return hash01(ix, iy, seed)

    sx = fx * fx * (3 - 2 * fx)
    sy = fy * fy * (3 - 2 * fy)
    a = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * sx
    b = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * sx
    return a + (b - a) * sy


def fbm(x, y, seed=0, octaves=3, period=None):
    tot, amp, norm, f = 0.0, 1.0, 0.0, 1
    for o in range(octaves):
        tot += value_noise(x * f, y * f, seed + o * 101, period * f if period else None) * amp
        norm += amp
        amp *= 0.5
        f *= 2
    return tot / norm
