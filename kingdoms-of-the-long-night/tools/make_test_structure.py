#!/usr/bin/env python3
"""Writes data/longnight/structure/empty.nbt: a 24x12x24 GameTest platform (stone floor, air above)."""
import gzip, os, struct

def tag_string(s):
    b = s.encode("utf-8"); return struct.pack(">H", len(b)) + b
def named(tid, name, payload):
    return bytes([tid]) + tag_string(name) + payload
def compound(items):  # items: list of (tid, name, payload)
    return b"".join(named(t, n, p) for t, n, p in items) + b"\x00"
def int_(v): return struct.pack(">i", v)
def list_(tid, payloads): return bytes([tid]) + struct.pack(">i", len(payloads)) + b"".join(payloads)

W, H, D = 24, 12, 24
palette = [compound([(8, "Name", tag_string("minecraft:stone"))]), compound([(8, "Name", tag_string("minecraft:air"))])]
blocks = []
for x in range(W):
    for y in range(H):
        for z in range(D):
            state = 0 if y == 0 else 1
            blocks.append(compound([(9, "pos", list_(3, [int_(x), int_(y), int_(z)])), (3, "state", int_(state))]))
root = compound([
    (3, "DataVersion", int_(3955)),
    (9, "size", list_(3, [int_(W), int_(H), int_(D)])),
    (9, "palette", list_(10, palette)),
    (9, "blocks", list_(10, blocks)),
    (9, "entities", list_(10, [])),
])
data = b"\x0a" + tag_string("") + root
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mod", "src", "main", "resources", "data", "longnight", "structure", "empty.nbt")
with gzip.open(out, "wb") as f:
    f.write(data)
print("wrote", os.path.normpath(out), len(data), "bytes")
