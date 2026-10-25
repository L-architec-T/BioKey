"""
Single-shot fake pcbu-desktop TCPUnlockServer, for interop-testing
UnlockTcpSession end-to-end against a real Android app: waits for the
phone's DEVICE_ID (raw, unencrypted), sends an encrypted UNLOCK_REQUEST
challenge, and reports whatever UNLOCK_RESPONSE comes back.

Usage:
  pcbu_fake_pc_unlock_server.py --enc-key KEY --user USER --program PROGRAM [--port N]
Prints "PORT <n>" once bound, then "DEVICE_ID <id>" and finally
"RESPONSE <json>" (decrypted if error=="").
"""
from __future__ import annotations

import argparse
import json
import socket
import struct
import sys
import uuid

from pcbu_crypto_ref import decrypt_packet, encrypt_packet

MAGIC_HEADER = bytes.fromhex("DB065AC7AFDFA4CC")
DEVICE_ID = 0xB0
UNLOCK_REQUEST = 0xB1
UNLOCK_RESPONSE = 0xB2


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
    parser.add_argument("--user", required=True)
    parser.add_argument("--program", required=True)
    parser.add_argument("--port", type=int, default=0)
    args = parser.parse_args()

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", args.port))
    server.listen(1)
    print(f"PORT {server.getsockname()[1]}", flush=True)

    conn, _ = server.accept()
    try:
        conn.settimeout(120)
        packet_id, payload = read_packet(conn)
        if packet_id != DEVICE_ID:
            print(f"ERROR expected DEVICE_ID, got 0x{packet_id:X}", file=sys.stderr)
            return 1
        device_id = payload.decode("utf-8")
        print(f"DEVICE_ID {device_id}", flush=True)

        unlock_token = uuid.uuid4().hex + uuid.uuid4().hex  # 64 hex chars, matches StringUtils::RandomString(64)-ish
        request_data = {"user": args.user, "program": args.program, "unlockToken": unlock_token}
        enc_data, _ = encrypt_packet(json.dumps(request_data).encode("utf-8"), args.enc_key)
        request = {"protoVersion": "3.0.0", "deviceId": device_id, "encData": enc_data.hex().upper()}
        write_packet(conn, UNLOCK_REQUEST, json.dumps(request).encode("utf-8"))

        packet_id, payload = read_packet(conn)
        if packet_id != UNLOCK_RESPONSE:
            print(f"ERROR expected UNLOCK_RESPONSE, got 0x{packet_id:X}", file=sys.stderr)
            return 1
        response = json.loads(payload.decode("utf-8"))

        if response.get("error"):
            print(f"RESPONSE {json.dumps(response)}", flush=True)
            return 0

        dec = decrypt_packet(bytes.fromhex(response["encData"]), args.enc_key)
        data = json.loads(dec.decode("utf-8"))
        token_matches = data.get("unlockToken") == unlock_token
        print(f"RESPONSE {json.dumps({'error': '', 'data': data, 'tokenMatches': token_matches})}", flush=True)
        return 0
    finally:
        conn.close()
        server.close()


if __name__ == "__main__":
    sys.exit(main())
