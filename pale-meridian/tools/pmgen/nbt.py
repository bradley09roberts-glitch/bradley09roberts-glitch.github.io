"""Minimal, dependency-free NBT (Java Edition) reader/writer for structure templates."""
import gzip
import io
import struct

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = range(13)


class Tag:
    __slots__ = ("type", "value")

    def __init__(self, type_: int, value):
        self.type = type_
        self.value = value

    def __repr__(self):
        return f"Tag({self.type},{self.value!r})"


def Byte(v): return Tag(TAG_BYTE, int(v))
def Short(v): return Tag(TAG_SHORT, int(v))
def Int(v): return Tag(TAG_INT, int(v))
def Long(v): return Tag(TAG_LONG, int(v))
def Float(v): return Tag(TAG_FLOAT, float(v))
def Double(v): return Tag(TAG_DOUBLE, float(v))
def String(v): return Tag(TAG_STRING, str(v))
def IntArray(v): return Tag(TAG_INT_ARRAY, [int(x) for x in v])
def LongArray(v): return Tag(TAG_LONG_ARRAY, [int(x) for x in v])


def List(elem_type: int, items):
    return Tag(TAG_LIST, (elem_type, list(items)))


def Compound(d: dict | None = None):
    return Tag(TAG_COMPOUND, dict(d or {}))


def _w_payload(out: io.BytesIO, tag: Tag) -> None:
    t, v = tag.type, tag.value
    if t == TAG_BYTE:
        out.write(struct.pack(">b", v))
    elif t == TAG_SHORT:
        out.write(struct.pack(">h", v))
    elif t == TAG_INT:
        out.write(struct.pack(">i", v))
    elif t == TAG_LONG:
        out.write(struct.pack(">q", v))
    elif t == TAG_FLOAT:
        out.write(struct.pack(">f", v))
    elif t == TAG_DOUBLE:
        out.write(struct.pack(">d", v))
    elif t == TAG_BYTE_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(bytes(v))
    elif t == TAG_STRING:
        b = v.encode("utf-8")
        out.write(struct.pack(">H", len(b)))
        out.write(b)
    elif t == TAG_LIST:
        et, items = v
        if not items:
            et = TAG_END
        out.write(struct.pack(">bi", et, len(items)))
        for it in items:
            if it.type != et:
                raise TypeError(f"list element type {it.type} != {et}")
            _w_payload(out, it)
    elif t == TAG_COMPOUND:
        for k, sub in v.items():
            out.write(struct.pack(">b", sub.type))
            kb = k.encode("utf-8")
            out.write(struct.pack(">H", len(kb)))
            out.write(kb)
            _w_payload(out, sub)
        out.write(b"\x00")
    elif t == TAG_INT_ARRAY:
        out.write(struct.pack(">i", len(v)))
        for x in v:
            out.write(struct.pack(">i", x))
    elif t == TAG_LONG_ARRAY:
        out.write(struct.pack(">i", len(v)))
        for x in v:
            out.write(struct.pack(">q", x))
    else:
        raise ValueError(t)


def write_root(tag: Tag, name: str = "") -> bytes:
    """Serialise a root compound, gzip-compressed (the structure template format), deterministically."""
    out = io.BytesIO()
    out.write(struct.pack(">b", TAG_COMPOUND))
    nb = name.encode()
    out.write(struct.pack(">H", len(nb)))
    out.write(nb)
    _w_payload(out, tag)
    raw = out.getvalue()
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as gz:
        gz.write(raw)
    return buf.getvalue()


def _r_payload(inp: io.BytesIO, t: int):
    if t == TAG_BYTE:
        return struct.unpack(">b", inp.read(1))[0]
    if t == TAG_SHORT:
        return struct.unpack(">h", inp.read(2))[0]
    if t == TAG_INT:
        return struct.unpack(">i", inp.read(4))[0]
    if t == TAG_LONG:
        return struct.unpack(">q", inp.read(8))[0]
    if t == TAG_FLOAT:
        return struct.unpack(">f", inp.read(4))[0]
    if t == TAG_DOUBLE:
        return struct.unpack(">d", inp.read(8))[0]
    if t == TAG_BYTE_ARRAY:
        n = struct.unpack(">i", inp.read(4))[0]
        return list(inp.read(n))
    if t == TAG_STRING:
        n = struct.unpack(">H", inp.read(2))[0]
        return inp.read(n).decode("utf-8")
    if t == TAG_LIST:
        et, n = struct.unpack(">bi", inp.read(5))
        return [_r_payload(inp, et) for _ in range(n)]
    if t == TAG_COMPOUND:
        d = {}
        while True:
            st = struct.unpack(">b", inp.read(1))[0]
            if st == TAG_END:
                return d
            n = struct.unpack(">H", inp.read(2))[0]
            k = inp.read(n).decode("utf-8")
            d[k] = _r_payload(inp, st)
    if t == TAG_INT_ARRAY:
        n = struct.unpack(">i", inp.read(4))[0]
        return list(struct.unpack(f">{n}i", inp.read(4 * n)))
    if t == TAG_LONG_ARRAY:
        n = struct.unpack(">i", inp.read(4))[0]
        return list(struct.unpack(f">{n}q", inp.read(8 * n)))
    raise ValueError(t)


def read_root(data: bytes) -> dict:
    """Read a (possibly gzipped) root compound into plain Python values (for inspection/tests)."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    inp = io.BytesIO(data)
    t = struct.unpack(">b", inp.read(1))[0]
    n = struct.unpack(">H", inp.read(2))[0]
    inp.read(n)
    return _r_payload(inp, t)
