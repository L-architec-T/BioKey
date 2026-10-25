package org.pcbu.libreclient.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.SequenceInputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PacketFramingTest {

    @Test
    fun `round-trips a packet through write and read`() {
        val out = ByteArrayOutputStream()
        PacketFraming.writePacket(out, PacketIds.UNLOCK_REQUEST, "hello world".toByteArray())

        val packet = PacketFraming.readPacket(ByteArrayInputStream(out.toByteArray()))
        assertEquals(PacketIds.UNLOCK_REQUEST, packet.id)
        assertArrayEquals("hello world".toByteArray(), packet.data)
    }

    @Test
    fun `wire format matches the documented byte layout exactly`() {
        val out = ByteArrayOutputStream()
        PacketFraming.writePacket(out, 0xB0, byteArrayOf(0x01, 0x02, 0x03))
        val bytes = out.toByteArray()

        // 8-byte magic + 2-byte id + 2-byte length + payload
        assertEquals(8 + 2 + 2 + 3, bytes.size)
        val expectedMagic = byteArrayOf(
            0xDB.toByte(), 0x06, 0x5A, 0xC7.toByte(), 0xAF.toByte(), 0xDF.toByte(), 0xA4.toByte(), 0xCC.toByte(),
        )
        assertArrayEquals(expectedMagic, bytes.copyOfRange(0, 8))
        assertArrayEquals(byteArrayOf(0x00, 0xB0.toByte()), bytes.copyOfRange(8, 10))
        assertArrayEquals(byteArrayOf(0x00, 0x03), bytes.copyOfRange(10, 12))
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), bytes.copyOfRange(12, 15))
    }

    @Test
    fun `resynchronizes on the magic header despite leading garbage bytes`() {
        val validPacket = ByteArrayOutputStream().apply {
            PacketFraming.writePacket(this, PacketIds.PAIR_INIT, "payload".toByteArray())
        }.toByteArray()

        val garbage = byteArrayOf(0x11, 0x22, 0xDB.toByte(), 0x06, 0x00, 0x00) // partial false-start on the magic byte
        val stream = SequenceInputStream(ByteArrayInputStream(garbage), ByteArrayInputStream(validPacket))

        val packet = PacketFraming.readPacket(stream)
        assertEquals(PacketIds.PAIR_INIT, packet.id)
        assertArrayEquals("payload".toByteArray(), packet.data)
    }

    @Test
    fun `reading from a closed stream throws ClosedConnection`() {
        assertThrows(PacketReadError.ClosedConnection::class.java) {
            PacketFraming.readPacket(ByteArrayInputStream(ByteArray(0)))
        }
    }

    @Test
    fun `DEVICE_ID packet carries the raw deviceId bytes with no JSON envelope`() {
        // Per BaseUnlockConnection::OnPacketReceived: payload is the raw UTF-8 deviceId, unencrypted.
        val deviceId = "abc123-device-id"
        val out = ByteArrayOutputStream()
        PacketFraming.writePacket(out, PacketIds.DEVICE_ID, deviceId)

        val packet = PacketFraming.readPacket(ByteArrayInputStream(out.toByteArray()))
        assertEquals(PacketIds.DEVICE_ID, packet.id)
        assertEquals(deviceId, String(packet.data, Charsets.UTF_8))
    }
}
