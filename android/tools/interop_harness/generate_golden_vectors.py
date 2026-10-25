"""
Generates deterministic cross-language test vectors for :core's Kotlin
crypto/protocol tests, using pcbu_crypto_ref.py as the oracle.

Packet-mode vectors bake in a fixed timestamp_ms; the Kotlin test injects
`nowMs` relative to that value (never the real wall clock), so the vectors
stay valid forever instead of expiring.

Run: python3 generate_golden_vectors.py > ../../core/src/test/resources/golden_vectors.json
"""
import json
import sys

from pcbu_crypto_ref import encrypt_packet, encrypt_simple, hex_encode

FIXED_TIMESTAMP_MS = 1_700_000_000_000  # arbitrary fixed instant, not "now"


def simple_vector(shared_key: str, plaintext: bytes, iv: bytes, salt: bytes) -> dict:
    blob = encrypt_simple(plaintext, shared_key, iv=iv, salt=salt)
    return {
        "sharedKey": shared_key,
        "plaintextHex": hex_encode(plaintext),
        "blobHex": hex_encode(blob),
    }


def packet_vector(shared_key: str, plaintext: bytes, iv: bytes, salt: bytes, timestamp_ms: int) -> dict:
    blob, ts = encrypt_packet(plaintext, shared_key, timestamp_ms=timestamp_ms, iv=iv, salt=salt)
    return {
        "sharedKey": shared_key,
        "plaintextHex": hex_encode(plaintext),
        "timestampMs": ts,
        "blobHex": hex_encode(blob),
    }


def main() -> None:
    iv_a = bytes(range(16))
    salt_a = bytes(range(200, 216))
    iv_b = bytes(reversed(range(16)))
    salt_b = bytes(reversed(range(200, 216)))

    vectors = {
        "simple": [
            simple_vector("test-shared-key-1", b"hello pcbu", iv_a, salt_a),
            simple_vector("un-mot-de-passe-ascii-only", b"", iv_b, salt_b),
            simple_vector(
                "clef-partagee-longue-" + "x" * 60,
                json.dumps({"user": "larchitect", "program": "gdm-password", "unlockToken": "t" * 64}).encode("utf-8"),
                iv_a,
                salt_b,
            ),
        ],
        "packet": [
            packet_vector("pairing-enc-key-abc123", b"pair init payload", iv_a, salt_a, FIXED_TIMESTAMP_MS),
            packet_vector(
                "pairing-enc-key-abc123",
                json.dumps({"user": "larchitect", "program": "sudo", "unlockToken": "u" * 64}).encode("utf-8"),
                iv_b,
                salt_b,
                FIXED_TIMESTAMP_MS,
            ),
        ],
    }

    json.dump(vectors, sys.stdout, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
