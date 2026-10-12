"""Minimal Minecraft RCON client (stdlib only). Handles responses split across several packets."""
import socket
import struct
import time


class Rcon:
    def __init__(self, host="127.0.0.1", port=25575, password="longnight-test", timeout=180):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.req = 0
        rid = self._write(3, password)
        got, _, _ = self._read()
        if got == -1 or got != rid:
            raise RuntimeError("RCON auth failed")

    def _write(self, kind, body):
        self.req += 1
        data = struct.pack("<ii", self.req, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)
        return self.req

    def _exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("closed")
            buf += chunk
        return buf

    def _read(self):
        (length,) = struct.unpack("<i", self._exact(4))
        data = self._exact(length)
        rid, kind = struct.unpack("<ii", data[:8])
        return rid, kind, data[8:-2].decode("utf-8", "replace")

    def cmd(self, command):
        """Vanilla RCON handles one packet per read and splits long replies into 4096-byte fragments."""
        rid = self._write(2, command)
        parts = []
        got, _, text = self._read()
        parts.append(text)
        while len(text.encode("utf-8")) >= 4096:
            old = self.sock.gettimeout()
            self.sock.settimeout(2.0)
            try:
                got, _, text = self._read()
                parts.append(text)
            except socket.timeout:
                break
            finally:
                self.sock.settimeout(old)
        return "".join(parts)

    def close(self):
        self.sock.close()


def connect(retries=150, **kw):
    for _ in range(retries):
        try:
            return Rcon(**kw)
        except OSError:
            time.sleep(2)
    raise RuntimeError("could not connect to RCON")
