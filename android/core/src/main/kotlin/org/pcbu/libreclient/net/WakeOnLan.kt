package org.pcbu.libreclient.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends a standard Wake-on-LAN magic packet (6x 0xFF followed by the target
 * MAC repeated 16 times) as a UDP broadcast. Not part of the PCBU protocol
 * itself — the desktop app's "Wake" button does the same thing from the
 * macAddresses collected at pairing, since a sleeping/off PC obviously can't
 * run the unlock listener.
 */
object WakeOnLan {

    fun buildMagicPacket(macAddress: String): ByteArray {
        val macBytes = parseMac(macAddress)
        val packet = ByteArray(6 + 16 * macBytes.size)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packet, 6 + i * macBytes.size, macBytes.size)
        }
        return packet
    }

    fun send(macAddress: String, broadcastAddress: String = "255.255.255.255", port: Int = 9) {
        val packet = buildMagicPacket(macAddress)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val address = InetAddress.getByName(broadcastAddress)
            socket.send(DatagramPacket(packet, packet.size, address, port))
        }
    }

    private fun parseMac(macAddress: String): ByteArray {
        val parts = macAddress.split(":", "-")
        require(parts.size == 6) { "Not a MAC address: $macAddress" }
        return ByteArray(6) { i -> parts[i].toInt(16).toByte() }
    }
}
