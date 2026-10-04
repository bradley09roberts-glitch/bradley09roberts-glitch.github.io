#!/usr/bin/env python3
"""Minimal RCON client used by the verification scripts.

Usage: rcon.py [--port 25598] [--password squidtest] <command...>
Prints the server response. Exit code 2 on connection failure.
"""
import argparse
import socket
import struct
import sys


def _pack(req_id: int, kind: int, body: str) -> bytes:
    payload = struct.pack("<ii", req_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def _read(sock: socket.socket):
    raw = b""
    while len(raw) < 4:
        chunk = sock.recv(4 - len(raw))
        if not chunk:
            raise ConnectionError("rcon closed")
        raw += chunk
    (length,) = struct.unpack("<i", raw)
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise ConnectionError("rcon closed")
        data += chunk
    req_id, kind = struct.unpack("<ii", data[:8])
    return req_id, kind, data[8:-2].decode("utf-8", "replace")


def rcon(command: str, host="127.0.0.1", port=25598, password="squidtest", timeout=60.0) -> str:
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.sendall(_pack(1, 3, password))
        req_id, _, _ = _read(sock)
        if req_id == -1:
            raise PermissionError("rcon auth failed")
        sock.sendall(_pack(2, 2, command))
        _, _, body = _read(sock)
        return body


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=25598)
    ap.add_argument("--password", default="squidtest")
    ap.add_argument("command", nargs="+")
    a = ap.parse_args()
    try:
        print(rcon(" ".join(a.command), a.host, a.port, a.password))
    except (OSError, ConnectionError) as exc:
        print(f"rcon error: {exc}", file=sys.stderr)
        sys.exit(2)
