package org.pcbu.libreclient.pairing

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import org.pcbu.libreclient.crypto.AesGcmPacketCodec
import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.protocol.model.UdpPairBeacon

data class DiscoveredServer(val ip: String, val port: Int)

/**
 * Listens for the PC's encrypted UDP pairing beacon (modern QR flow).
 * Verified against UDPPairingBroadcaster::SendToTarget: the datagram
 * payload is the raw AES-GCM "packet"-mode blob (no hex, no JSON
 * envelope) of a PacketUDPPairBeacon{serverId, ip, port} — same shape as
 * PAIR_INIT/PAIR_RESPONSE, unlike the plaintext unlock UDP broadcast.
 */
object PairingDiscoveryClient {

    /**
     * Binds to [discoveryPort] and waits (up to [timeoutMs] total) for a
     * beacon whose serverId matches and whose decrypted contents are within
     * the anti-replay window. Malformed or mismatched datagrams (e.g. from
     * another in-progress pairing on the same LAN) are ignored, not fatal.
     */
    fun discover(discoveryPort: Int, serverId: String, encKey: String, timeoutMs: Int = 20_000): DiscoveredServer {
        DatagramSocket(discoveryPort).use { socket ->
            socket.soTimeout = timeoutMs
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(4096)

            while (true) {
                val remaining = (deadline - System.currentTimeMillis()).toInt()
                if (remaining <= 0) throw PairingError.Network(SocketTimeoutException("No matching beacon within ${timeoutMs}ms"))
                socket.soTimeout = remaining

                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    throw PairingError.Network(e)
                }

                val payload = packet.data.copyOfRange(0, packet.length)
                val beacon = tryDecodeBeacon(payload, encKey) ?: continue
                if (beacon.serverId == serverId) {
                    return DiscoveredServer(beacon.ip, beacon.port)
                }
            }
        }
    }

    private fun tryDecodeBeacon(payload: ByteArray, encKey: String): UdpPairBeacon? {
        return try {
            val json = String(AesGcmPacketCodec.decryptPacket(payload, encKey), Charsets.UTF_8)
            PcbuJson.decodeFromString(UdpPairBeacon.serializer(), json)
        } catch (e: Exception) {
            null
        }
    }
}
