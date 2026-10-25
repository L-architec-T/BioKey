"""
Single-shot fake pcbu-desktop pairing server, independent of the Kotlin
implementation, used as a cross-language interop test fixture: the Kotlin
:core test suite launches this as a subprocess and pairs against it over a
real TCP socket, so both the framing and the AES-GCM packet crypto are
exercised end-to-end by two independently-written implementations.

Protocol re-derived from pcbu-desktop's BaseConnection.cpp / PairingServer.cpp
(clean room, no copied code): PAIR_INIT/PAIR_RESPONSE payloads are the raw
AES-GCM "packet"-mode blob (no hex, no JSON envelope around the ciphertext).

Usage:
  pcbu_fake_pc_server.py --enc-key KEY (--accept RESPONSE_JSON | --reject MSG)
Prints exactly one line to stdout once bound: "PORT <n>"
Then, after handling the single connection, prints: "RECEIVED_INIT <json>"
Exit code 0 on success, non-zero on any protocol/crypto error.
"""
from __future__ import annotations

import argparse
import json
import socket
import struct
import sys

from pcbu_crypto_ref import decrypt_packet, encrypt_packet

MAGIC_HEADER = bytes.fromhex("DB065AC7AFDFA4CC")
PAIR_INIT = 0x50
PAIR_RESPONSE = 0x51


def read_packet(sock: socket.socket) -> tuple[int, bytes]:
    def read_exact(n: int) -> bytes:
        buf = b""
        while len(buf) < n:
            chunk = sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("peer closed connection")
            buf += chunk
        return buf

    magic = read_exact(8)
    if magic != MAGIC_HEADER:
        raise ValueError(f"bad magic header: {magic.hex()}")
    packet_id = struct.unpack(">H", read_exact(2))[0]
    length = struct.unpack(">H", read_exact(2))[0]
    payload = read_exact(length) if length else b""
    return packet_id, payload


def write_packet(sock: socket.socket, packet_id: int, payload: bytes) -> None:
    sock.sendall(MAGIC_HEADER + struct.pack(">HH", packet_id, len(payload)) + payload)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--enc-key", required=True)
    parser.add_argument("--port", type=int, default=0)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--accept", help="JSON for PairResponseData to send back on success")
    group.add_argument("--reject", help="errMsg to send back instead of accepting")
    args = parser.parse_args()

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", args.port))
    server.listen(1)
    print(f"PORT {server.getsockname()[1]}", flush=True)

    conn, _ = server.accept()
    try:
        conn.settimeout(10)
        packet_id, payload = read_packet(conn)
        if packet_id != PAIR_INIT:
            print(f"ERROR unexpected packet id 0x{packet_id:X}", file=sys.stderr)
            return 1

        init_json = decrypt_packet(payload, args.enc_key).decode("utf-8")
        print(f"RECEIVED_INIT {init_json}", flush=True)

        if args.reject is not None:
            response = {"errMsg": args.reject, "data": None}
        else:
            response = {"errMsg": "", "data": json.loads(args.accept)}

        response_json = json.dumps(response)
        encrypted, _ = encrypt_packet(response_json.encode("utf-8"), args.enc_key)
        write_packet(conn, PAIR_RESPONSE, encrypted)
        return 0
    finally:
        conn.close()
        server.close()


if __name__ == "__main__":
    sys.exit(main())
