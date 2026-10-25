package org.pcbu.libreclient.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Re-derived (clean room) from pcbu-desktop's CryptUtils.cpp: blob layout is
 * [IV 16B][Salt 16B][Ciphertext][GCM Tag 16B]. Key = PBKDF2WithHmacSHA256(
 * sharedKey, salt, 65535 iterations, 256-bit). A fresh random salt (and IV)
 * is generated on every encryption, so the derived key differs per message
 * even for the same shared key.
 */
object CryptoConstants {
    const val IV_SIZE = 16
    const val SALT_SIZE = 16
    const val TAG_SIZE = 16
    const val TAG_BITS = TAG_SIZE * 8
    const val PBKDF2_ITERATIONS = 65535
    const val KEY_BITS = 256

    /** CRYPT_PACKET_TIMEOUT in CryptUtils.h: anti-replay window, +/- 2 minutes. */
    const val PACKET_TIMEOUT_MS = 120_000L

    /** CRYPT_BUFFER_SIZE in CryptUtils.cpp: max plaintext+overhead handled by the PC. */
    const val MAX_BLOB_SIZE = 2048
}

sealed class CryptoException(message: String) : Exception(message) {
    class BlobTooLarge(size: Int) : CryptoException("Blob exceeds CRYPT_BUFFER_SIZE (${CryptoConstants.MAX_BLOB_SIZE}): $size")
    class BlobTooShort(size: Int) : CryptoException("Blob shorter than IV+Salt+Tag overhead: $size")
    object InvalidTimestamp : CryptoException("Decrypted packet timestamp outside the anti-replay window")
    class DecryptFailed(cause: Throwable) : CryptoException("AEAD decryption failed (bad key or tampered data): ${cause.message}")
}

private fun deriveKey(sharedKey: String, salt: ByteArray): SecretKeySpec {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(sharedKey.toCharArray(), salt, CryptoConstants.PBKDF2_ITERATIONS, CryptoConstants.KEY_BITS)
    val keyBytes = factory.generateSecret(spec).encoded
    return SecretKeySpec(keyBytes, "AES")
}

/** Low-level [IV][Salt][Ciphertext+Tag] codec, no timestamp/anti-replay framing. */
object AesGcmSimpleCodec {

    fun encrypt(plaintext: ByteArray, sharedKey: String): ByteArray {
        if (plaintext.size > CryptoConstants.MAX_BLOB_SIZE) throw CryptoException.BlobTooLarge(plaintext.size)

        val random = SecureRandom()
        val iv = ByteArray(CryptoConstants.IV_SIZE).also(random::nextBytes)
        val salt = ByteArray(CryptoConstants.SALT_SIZE).also(random::nextBytes)
        val key = deriveKey(sharedKey, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(CryptoConstants.TAG_BITS, iv))
        val ciphertextAndTag = cipher.doFinal(plaintext)

        return iv + salt + ciphertextAndTag
    }

    fun decrypt(blob: ByteArray, sharedKey: String): ByteArray {
        val overhead = CryptoConstants.IV_SIZE + CryptoConstants.SALT_SIZE + CryptoConstants.TAG_SIZE
        if (blob.size > CryptoConstants.MAX_BLOB_SIZE) throw CryptoException.BlobTooLarge(blob.size)
        if (blob.size < overhead) throw CryptoException.BlobTooShort(blob.size)

        val iv = blob.copyOfRange(0, CryptoConstants.IV_SIZE)
        val salt = blob.copyOfRange(CryptoConstants.IV_SIZE, CryptoConstants.IV_SIZE + CryptoConstants.SALT_SIZE)
        val ciphertextAndTag = blob.copyOfRange(CryptoConstants.IV_SIZE + CryptoConstants.SALT_SIZE, blob.size)
        val key = deriveKey(sharedKey, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoConstants.TAG_BITS, iv))
        return try {
            cipher.doFinal(ciphertextAndTag)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw CryptoException.DecryptFailed(e)
        }
    }
}

/**
 * Packet-level codec used for all PCBU protocol messages: prefixes the
 * plaintext with an 8-byte big-endian millisecond timestamp before
 * encrypting (via [AesGcmSimpleCodec]), and enforces the anti-replay
 * window on decrypt. The clock parameters default to the real clock but
 * are injectable for deterministic testing against fixed golden vectors.
 */
object AesGcmPacketCodec {

    fun encryptPacket(
        plaintext: ByteArray,
        sharedKey: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): ByteArray {
        val ts = ByteBuffer.allocate(8).putLong(timestampMs).array()
        return AesGcmSimpleCodec.encrypt(ts + plaintext, sharedKey)
    }

    fun decryptPacket(
        blob: ByteArray,
        sharedKey: String,
        nowMs: Long = System.currentTimeMillis(),
    ): ByteArray {
        val decrypted = AesGcmSimpleCodec.decrypt(blob, sharedKey)
        if (decrypted.size < 8) throw CryptoException.BlobTooShort(decrypted.size)

        val timestampMs = ByteBuffer.wrap(decrypted, 0, 8).long
        val diff = nowMs - timestampMs
        if (diff < -CryptoConstants.PACKET_TIMEOUT_MS || diff > CryptoConstants.PACKET_TIMEOUT_MS) {
            throw CryptoException.InvalidTimestamp
        }
        return decrypted.copyOfRange(8, decrypted.size)
    }
}
