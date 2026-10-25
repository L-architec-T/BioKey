package org.pcbu.libreclient.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class AesGcmCodecTest {

    // --- Cross-language interop: Kotlin must decrypt what the Python reference (CryptUtils.cpp
    // clean-room re-derivation) produced, and vice versa is exercised by round-trip tests below. ---

    @ParameterizedTest
    @MethodSource("simpleVectors")
    fun `decrypts golden simple-mode vectors from the Python reference`(vector: SimpleVector) {
        val plaintext = AesGcmSimpleCodec.decrypt(HexCodec.decode(vector.blobHex), vector.sharedKey)
        assertArrayEquals(HexCodec.decode(vector.plaintextHex), plaintext)
    }

    @ParameterizedTest
    @MethodSource("packetVectors")
    fun `decrypts golden packet-mode vectors within the anti-replay window`(vector: PacketVector) {
        val plaintext = AesGcmPacketCodec.decryptPacket(
            HexCodec.decode(vector.blobHex),
            vector.sharedKey,
            nowMs = vector.timestampMs + 1_000,
        )
        assertArrayEquals(HexCodec.decode(vector.plaintextHex), plaintext)
    }

    @Test
    fun `packet decrypt rejects a timestamp just past the 120s window`() {
        val vector = GoldenVectorsLoader.load().packet.first()
        assertThrows(CryptoException.InvalidTimestamp::class.java) {
            AesGcmPacketCodec.decryptPacket(
                HexCodec.decode(vector.blobHex),
                vector.sharedKey,
                nowMs = vector.timestampMs + CryptoConstants.PACKET_TIMEOUT_MS + 1,
            )
        }
        assertThrows(CryptoException.InvalidTimestamp::class.java) {
            AesGcmPacketCodec.decryptPacket(
                HexCodec.decode(vector.blobHex),
                vector.sharedKey,
                nowMs = vector.timestampMs - CryptoConstants.PACKET_TIMEOUT_MS - 1,
            )
        }
    }

    @Test
    fun `packet decrypt accepts the exact boundary of the anti-replay window`() {
        val vector = GoldenVectorsLoader.load().packet.first()
        val plaintext = AesGcmPacketCodec.decryptPacket(
            HexCodec.decode(vector.blobHex),
            vector.sharedKey,
            nowMs = vector.timestampMs + CryptoConstants.PACKET_TIMEOUT_MS,
        )
        assertArrayEquals(HexCodec.decode(vector.plaintextHex), plaintext)
    }

    @Test
    fun `simple codec round-trips arbitrary plaintext with a fresh random salt each time`() {
        val plaintext = "unlockToken-payload".toByteArray()
        val blobA = AesGcmSimpleCodec.encrypt(plaintext, "shared-secret")
        val blobB = AesGcmSimpleCodec.encrypt(plaintext, "shared-secret")

        assertArrayEquals(plaintext, AesGcmSimpleCodec.decrypt(blobA, "shared-secret"))
        assertArrayEquals(plaintext, AesGcmSimpleCodec.decrypt(blobB, "shared-secret"))
        // Different random IV/salt each call -> different ciphertext for identical input.
        org.junit.jupiter.api.Assertions.assertFalse(blobA.contentEquals(blobB))
    }

    @Test
    fun `decrypt fails with the wrong shared key`() {
        val blob = AesGcmSimpleCodec.encrypt("secret".toByteArray(), "correct-key")
        assertThrows(CryptoException.DecryptFailed::class.java) {
            AesGcmSimpleCodec.decrypt(blob, "wrong-key")
        }
    }

    @Test
    fun `decrypt fails on a tampered ciphertext`() {
        val blob = AesGcmSimpleCodec.encrypt("secret".toByteArray(), "k").copyOf()
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertThrows(CryptoException.DecryptFailed::class.java) {
            AesGcmSimpleCodec.decrypt(blob, "k")
        }
    }

    @Test
    fun `hex codec round-trips and decodes both cases`() {
        val bytes = byteArrayOf(0x00, 0x0F, -0x80, -0x01)
        val hex = HexCodec.encode(bytes)
        assertArrayEquals(bytes, HexCodec.decode(hex))
        assertArrayEquals(bytes, HexCodec.decode(hex.lowercase()))
        assertEquals(hex, hex.uppercase())
    }

    companion object {
        @JvmStatic
        fun simpleVectors() = GoldenVectorsLoader.load().simple

        @JvmStatic
        fun packetVectors() = GoldenVectorsLoader.load().packet
    }
}
