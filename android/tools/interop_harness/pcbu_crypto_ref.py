"""
Independent Python reference implementation of pcbu-desktop's CryptUtils.cpp,
re-derived by reading the upstream C++ (never copied) to serve as a
cross-language oracle for the Kotlin implementation in :core.

Blob layout: [IV 16B][Salt 16B][Ciphertext][GCM Tag 16B].
Key = PBKDF2HMAC-SHA256(shared_key, salt, 65535 iterations, 32 bytes).
Packet mode prefixes the plaintext with an 8-byte big-endian ms timestamp.
"""
from __future__ import annotations

import os
import struct
import time

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives.hashes import SHA256

IV_SIZE = 16
SALT_SIZE = 16
TAG_SIZE = 16
PBKDF2_ITERATIONS = 65535
KEY_BYTES = 32
PACKET_TIMEOUT_MS = 120_000


def _derive_key(shared_key: str, salt: bytes) -> bytes:
    kdf = PBKDF2HMAC(algorithm=SHA256(), length=KEY_BYTES, salt=salt, iterations=PBKDF2_ITERATIONS)
    return kdf.derive(shared_key.encode("utf-8"))


def encrypt_simple(plaintext: bytes, shared_key: str, *, iv: bytes | None = None, salt: bytes | None = None) -> bytes:
    iv = iv if iv is not None else os.urandom(IV_SIZE)
    salt = salt if salt is not None else os.urandom(SALT_SIZE)
    key = _derive_key(shared_key, salt)
    ciphertext_and_tag = AESGCM(key).encrypt(iv, plaintext, None)
    return iv + salt + ciphertext_and_tag


def decrypt_simple(blob: bytes, shared_key: str) -> bytes:
    iv = blob[:IV_SIZE]
    salt = blob[IV_SIZE:IV_SIZE + SALT_SIZE]
    ciphertext_and_tag = blob[IV_SIZE + SALT_SIZE:]
    key = _derive_key(shared_key, salt)
    return AESGCM(key).decrypt(iv, ciphertext_and_tag, None)


def encrypt_packet(plaintext: bytes, shared_key: str, *, timestamp_ms: int | None = None,
                    iv: bytes | None = None, salt: bytes | None = None) -> tuple[bytes, int]:
    ts = timestamp_ms if timestamp_ms is not None else int(time.time() * 1000)
    prefixed = struct.pack(">q", ts) + plaintext
    return encrypt_simple(prefixed, shared_key, iv=iv, salt=salt), ts


def decrypt_packet(blob: bytes, shared_key: str, *, now_ms: int | None = None) -> bytes:
    now = now_ms if now_ms is not None else int(time.time() * 1000)
    decrypted = decrypt_simple(blob, shared_key)
    (ts,) = struct.unpack(">q", decrypted[:8])
    diff = now - ts
    if diff < -PACKET_TIMEOUT_MS or diff > PACKET_TIMEOUT_MS:
        raise ValueError(f"INVALID_TIMESTAMP: diff={diff}ms outside +/-{PACKET_TIMEOUT_MS}ms")
    return decrypted[8:]


def hex_encode(data: bytes) -> str:
    return data.hex().upper()


def hex_decode(text: str) -> bytes:
    return bytes.fromhex(text)
