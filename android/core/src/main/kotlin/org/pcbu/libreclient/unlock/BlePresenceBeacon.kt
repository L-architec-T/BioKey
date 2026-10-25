package org.pcbu.libreclient.unlock

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Manufacturer data of the Bluetooth proximity-lock beacon, verified by pcbu-lockd's
 * BlePresenceWatcher (keep both in sync):
 *   [0] version (1)  [1] flags (bit 0 = disarm)  [2..5] SHA-256(deviceId)[0..4]
 *   [6] timeout / 10 s  [7..14] HMAC-SHA256(encKey, "BKP1" | slot (u64 BE) | flags | timeout)[0..8]
 * with slot = unix time / [SLOT_SECONDS]. Signed so another device can't keep a PC unlocked
 * by replaying an old beacon or forging one.
 */
object BlePresenceBeacon {
    /** Bluetooth SIG company ID reserved for testing/internal use. */
    const val COMPANY_ID = 0xFFFF
    const val SLOT_SECONDS = 10L
    private const val VERSION: Byte = 1
    private const val FLAG_DISARM = 0x01

    fun currentSlot(nowMillis: Long = System.currentTimeMillis()): Long = nowMillis / 1000 / SLOT_SECONDS

    fun payload(deviceId: String, encKey: String, slot: Long, timeoutSeconds: Int, disarm: Boolean): ByteArray {
        val flags = if (disarm) FLAG_DISARM else 0
        val timeoutByte = (timeoutSeconds / 10).coerceIn(1, 255)
        val tag = MessageDigest.getInstance("SHA-256").digest(deviceId.toByteArray(Charsets.UTF_8)).copyOf(4)
        val message = ByteBuffer.allocate(14)
            .put("BKP1".toByteArray(Charsets.US_ASCII))
            .putLong(slot)
            .put(flags.toByte())
            .put(timeoutByte.toByte())
            .array()
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(encKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }.doFinal(message)
        return byteArrayOf(VERSION, flags.toByte()) + tag + byteArrayOf(timeoutByte.toByte()) + mac.copyOf(8)
    }
}
