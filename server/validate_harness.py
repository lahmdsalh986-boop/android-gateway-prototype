#!/usr/bin/env python3
"""Deterministic local verification of the AGP server harness protocol."""
from __future__ import annotations

import hashlib
import socket

HOST = "127.0.0.1"
DATA_PORT = 9000
CONTROL_PORT = 9001
PAYLOAD_PORT = 10080
SIZE = 10 * 1024 * 1024
BUFFER = 64 * 1024


def line(sock: socket.socket) -> str:
    result = bytearray()
    while True:
        value = sock.recv(1)
        if not value:
            raise RuntimeError("unexpected EOF")
        if value == b"\n":
            return result.rstrip(b"\r").decode("ascii")
        result += value


def deterministic(size: int):
    left, counter = size, 0
    while left:
        block = hashlib.sha256(f"AGP-PAYLOAD-{counter}".encode()).digest()
        part = block[:min(left, len(block))]
        yield part
        left -= len(part)
        counter += 1


def tunnel() -> socket.socket:
    sock = socket.create_connection((HOST, DATA_PORT), timeout=10)
    sock.sendall(f"AGP/1 CONNECT 127.0.0.1 {PAYLOAD_PORT} local-validation\n".encode())
    assert line(sock) == "AGP/1 OK local-validation"
    return sock


def download() -> tuple[str, str]:
    with tunnel() as sock:
        sock.sendall(f"DOWNLOAD {SIZE}\n".encode())
        kind, length, expected = line(sock).split(" ")
        assert kind == "DOWNLOAD" and int(length) == SIZE
        digest, remaining = hashlib.sha256(), SIZE
        while remaining:
            part = sock.recv(min(BUFFER, remaining))
            if not part:
                raise RuntimeError("truncated download")
            digest.update(part); remaining -= len(part)
        return expected, digest.hexdigest()


def upload() -> tuple[str, str]:
    with tunnel() as sock:
        sock.sendall(f"UPLOAD {SIZE}\n".encode())
        digest = hashlib.sha256()
        for chunk in deterministic(SIZE):
            sock.sendall(chunk); digest.update(chunk)
        kind, remote = line(sock).split(" ")
        assert kind == "UPLOAD-OK"
        return digest.hexdigest(), remote


def control() -> str:
    with socket.create_connection((HOST, CONTROL_PORT), timeout=10) as sock:
        sock.sendall(b"AGP/1 CONTROL\n")
        assert line(sock) == "AGP/1 CONTROL-OK"
        sock.sendall(b"PING 1\n")
        return line(sock)


if __name__ == "__main__":
    incoming, local = download()
    outgoing, remote = upload()
    ack = control()
    print(f"SERVER_TO_CLIENT expected={incoming} received={local} result={'PASS' if incoming == local else 'FAIL'}")
    print(f"CLIENT_TO_SERVER sent={outgoing} server={remote} result={'PASS' if outgoing == remote else 'FAIL'}")
    print(f"CONTROL {ack}")
    if incoming != local or outgoing != remote or ack != "ACK 1":
        raise SystemExit(1)
