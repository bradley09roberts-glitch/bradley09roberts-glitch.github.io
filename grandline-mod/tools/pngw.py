"""Tiny dependency-free PNG writer (RGBA)."""
import struct, zlib

def write_png(path, w, h, px):
    """px: list of rows, each a list of (r,g,b,a) tuples."""
    raw = b''.join(b'\x00' + bytes([c for p in row for c in p]) for row in px)
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)
