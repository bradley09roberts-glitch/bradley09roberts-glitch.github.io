#!/usr/bin/env python3
"""Minimal RCON client for driving a local TerraCraft dev server: python3 tools/test/rcon.py "cmd1" "cmd2" ..."""
import socket, struct, sys, time
def pkt(s, i, t, body):
    b = body.encode()
    s.sendall(struct.pack('<iii', len(b)+10, i, t) + b + b'\x00\x00')
def rd(s):
    data = b''
    while len(data) < 4: data += s.recv(4-len(data))
    n = struct.unpack('<i', data)[0]; body = b''
    while len(body) < n: body += s.recv(n-len(body))
    return body[8:-2].decode(errors='replace')
def connect():
    s = socket.create_connection(('127.0.0.1', 25575), timeout=8)
    pkt(s, 1, 3, 'terracraft'); rd(s)
    return s
s = connect()
for cmd in sys.argv[1:]:
    print('>', cmd)
    try:
        pkt(s, 2, 2, cmd); print(rd(s))
    except socket.timeout:
        print('(no response)'); s.close(); s = connect()
    time.sleep(0.2)
