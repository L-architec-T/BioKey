package org.pcbu.libreclient.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** 0xDB065AC7AFDFA4CC as a signed 64-bit value, matching BaseConnection.cpp's PACKET_HEADER. */
const val PACKET_MAGIC_HEADER: Long = -0x24F9A538_50205B34L

data class Packet(val id: Int, val data: ByteArray)

sealed class PacketReadError : Exception() {
    object ClosedConnection : PacketReadError()
    object Malformed : PacketReadError()
}

/**
 * Reads/writes packets framed as pcbu-desktop expects:
 * [8 bytes magic BE][2 bytes packet id BE][2 bytes length BE][payload].
 *
 * DataInputStream/DataOutputStream are big-endian natively, matching the
 * network byte order (htons/htonll) used on the C++ side.
 */
object PacketFraming {

    fun writePacket(out: OutputStream, packetId: Int, payload: ByteArray) {
        require(payload.size <= 0xFFFF) { "Payload too large for uint16 length field: ${payload.size}" }
        val dos = if (out is DataOutputStream) out else DataOutputStream(out)
        dos.writeLong(PACKET_MAGIC_HEADER)
        dos.writeShort(packetId)
        dos.writeShort(payload.size)
        dos.write(payload)
        dos.flush()
    }

    fun writePacket(out: OutputStream, packetId: Int, payload: String) =
        writePacket(out, packetId, payload.toByteArray(StandardCharsets.UTF_8))

    /**
     * Resynchronizes on the magic header byte-by-byte, mirroring
     * BaseConnection::ReadPacket's resilience to stray/partial bytes.
     */
    fun readPacket(input: InputStream): Packet {
        val dis = if (input is DataInputStream) input else DataInputStream(input)
        val headerBytes = ByteArray(8).also {
            java.nio.ByteBuffer.wrap(it).putLong(PACKET_MAGIC_HEADER)
        }

        var matched = 0
        while (matched < headerBytes.size) {
            val b = dis.read()
            if (b < 0) throw PacketReadError.ClosedConnection
            if (b.toByte() == headerBytes[matched]) matched++ else matched = 0
        }

        val packetId = dis.readUnsignedShort()
        val length = dis.readUnsignedShort()
        if (length == 0) throw PacketReadError.Malformed

        val payload = ByteArray(length)
        dis.readFully(payload)
        return Packet(packetId, payload)
    }
}
