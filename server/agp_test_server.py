#!/usr/bin/env python3
"""Android Gateway Prototype test server.

Data port (default 9000): accepts AGP/1 CONNECT target_host target_port session_id,
then proxies raw bytes to the requested *loopback* payload service.
Control port (default 9001): accepts AGP/1 CONTROL and serves PING n -> ACK n.
Payload port (default 10080): deterministic 10 MiB payload and SHA-256 verifier.

This server deliberately permits only 127.0.0.1/localhost payload targets. It is a
laboratory harness, not a general relay or an Internet proxy.
"""
from __future__ import annotations

import argparse
import hashlib
import logging
import socket
import socketserver
import threading
from contextlib import closing

BUFFER = 64 * 1024
MAX_LINE = 1024


def read_line(sock: socket.socket) -> str:
    data = bytearray()
    while len(data) < MAX_LINE:
        chunk = sock.recv(1)
        if not chunk:
            raise ConnectionError("peer closed")
        if chunk == b"\n":
            return data.rstrip(b"\r").decode("ascii", "strict")
        data += chunk
    raise ValueError("line too long")


def write_line(sock: socket.socket, line: str) -> None:
    sock.sendall(line.encode("ascii") + b"\n")


def relay(source: socket.socket, destination: socket.socket, label: str) -> None:
    try:
        while True:
            payload = source.recv(BUFFER)
            if not payload:
                try:
                    destination.shutdown(socket.SHUT_WR)
                except OSError:
                    pass
                return
            destination.sendall(payload)
    except OSError as exc:
        logging.debug("%s ended: %s", label, exc)
    finally:
        try:
            destination.shutdown(socket.SHUT_WR)
        except OSError:
            pass


class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


class DataTunnelHandler(socketserver.BaseRequestHandler):
    def handle(self) -> None:
        client: socket.socket = self.request
        client.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        try:
            fields = read_line(client).split(" ")
            if len(fields) != 5 or fields[:2] != ["AGP/1", "CONNECT"]:
                raise ValueError("invalid data-tunnel preface")
            _, _, host, port_text, session_id = fields
            port = int(port_text)
            if host not in {"127.0.0.1", "localhost", "::1"} or not (1 <= port <= 65535):
                raise ValueError("harness permits loopback payload targets only")
            upstream = socket.create_connection((host, port), timeout=10)
            upstream.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            write_line(client, f"AGP/1 OK {session_id}")
            logging.info("data tunnel accepted session=%s target=%s:%d peer=%s", session_id, host, port, self.client_address[0])
            a = threading.Thread(target=relay, args=(client, upstream, f"client→payload {session_id}"), daemon=True)
            b = threading.Thread(target=relay, args=(upstream, client, f"payload→client {session_id}"), daemon=True)
            a.start(); b.start(); a.join(); b.join()
            upstream.close()
            logging.info("data tunnel closed session=%s", session_id)
        except Exception as exc:
            logging.warning("data-tunnel reject peer=%s: %s", self.client_address, exc)
            try:
                write_line(client, f"AGP/1 ERROR {str(exc)[:120]}")
            except OSError:
                pass


class ControlHandler(socketserver.BaseRequestHandler):
    def handle(self) -> None:
        sock: socket.socket = self.request
        try:
            if read_line(sock) != "AGP/1 CONTROL":
                raise ValueError("invalid control handshake")
            write_line(sock, "AGP/1 CONTROL-OK")
            logging.info("control channel connected peer=%s", self.client_address[0])
            while True:
                frame = read_line(sock).split(" ")
                if len(frame) != 2 or frame[0] != "PING":
                    raise ValueError("expected PING sequence")
                write_line(sock, f"ACK {frame[1]}")
        except Exception as exc:
            logging.info("control channel closed peer=%s reason=%s", self.client_address[0], exc)


def deterministic_bytes(size: int):
    counter = 0
    left = size
    while left:
        block = hashlib.sha256(f"AGP-PAYLOAD-{counter}".encode("ascii")).digest()
        chosen = block[:min(left, len(block))]
        yield chosen
        left -= len(chosen)
        counter += 1


def expected_sha256(size: int) -> str:
    digest = hashlib.sha256()
    for chunk in deterministic_bytes(size):
        digest.update(chunk)
    return digest.hexdigest()


class PayloadHandler(socketserver.BaseRequestHandler):
    def handle(self) -> None:
        sock: socket.socket = self.request
        try:
            command = read_line(sock).split(" ")
            if len(command) != 2 or command[0] not in {"DOWNLOAD", "UPLOAD"}:
                raise ValueError("expected DOWNLOAD bytes or UPLOAD bytes")
            amount = int(command[1])
            if amount <= 0 or amount > 64 * 1024 * 1024:
                raise ValueError("payload size must be 1..64 MiB")
            if command[0] == "DOWNLOAD":
                digest = expected_sha256(amount)
                write_line(sock, f"DOWNLOAD {amount} {digest}")
                for chunk in deterministic_bytes(amount):
                    sock.sendall(chunk)
                logging.info("server→client payload bytes=%d sha256=%s", amount, digest)
            else:
                digest = hashlib.sha256()
                remaining = amount
                while remaining:
                    part = sock.recv(min(BUFFER, remaining))
                    if not part:
                        raise ConnectionError(f"upload truncated with {remaining} bytes outstanding")
                    digest.update(part); remaining -= len(part)
                value = digest.hexdigest()
                write_line(sock, f"UPLOAD-OK {value}")
                logging.info("client→server payload bytes=%d sha256=%s", amount, value)
        except Exception as exc:
            logging.warning("payload error peer=%s: %s", self.client_address, exc)


def run_server(port: int, handler, name: str) -> ThreadedTCPServer:
    server = ThreadedTCPServer(("0.0.0.0", port), handler)
    threading.Thread(target=server.serve_forever, name=name, daemon=True).start()
    logging.info("%s listening on 0.0.0.0:%d", name, port)
    return server


def main() -> None:
    parser = argparse.ArgumentParser(description="Android Gateway Prototype test harness")
    parser.add_argument("--data-port", type=int, default=9000)
    parser.add_argument("--control-port", type=int, default=9001)
    parser.add_argument("--payload-port", type=int, default=10080)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    servers = [
        run_server(args.data_port, DataTunnelHandler, "agp-data"),
        run_server(args.control_port, ControlHandler, "agp-control"),
        run_server(args.payload_port, PayloadHandler, "agp-payload"),
    ]
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        pass
    finally:
        for server in servers:
            server.shutdown(); server.server_close()


if __name__ == "__main__":
    main()
