package org.pcbu.libreclient.pairing

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pcbu.libreclient.crypto.AesGcmPacketCodec
import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.protocol.model.UdpPairBeacon

class PairingDiscoveryClientTest {

    private val encKey = "beacon-enc-key"

    private fun freeUdpPort(): Int = DatagramSocket(0).use { it.localPort }

    private fun sendBeacon(port: Int, beacon: UdpPairBeacon, key: String = encKey) {
        val json = PcbuJson.encodeToString(UdpPairBeacon.serializer(), beacon)
        val encrypted = AesGcmPacketCodec.encryptPacket(json.toByteArray(Charsets.UTF_8), key)
        DatagramSocket().use { socket ->
            socket.send(DatagramPacket(encrypted, encrypted.size, InetAddress.getByName("127.0.0.1"), port))
        }
    }

    @Test
    fun `discovers the PC from a matching encrypted beacon, ignoring noise first`() {
        val port = freeUdpPort()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<DiscoveredServer> {
                PairingDiscoveryClient.discover(port, serverId = "srv-target", encKey = encKey, timeoutMs = 8_000)
            }

            Thread.sleep(200) // let discover() bind before we start sending
            sendBeacon(port, UdpPairBeacon(serverId = "srv-other", ip = "10.0.0.1", port = 1))
            sendBeacon(port, UdpPairBeacon(serverId = "srv-target", ip = "192.168.1.50", port = 43295))

            val result = future.get()
            assertEquals("192.168.1.50", result.ip)
            assertEquals(43295, result.port)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `ignores a beacon encrypted with the wrong key and times out`() {
        val port = freeUdpPort()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<Throwable> {
                try {
                    PairingDiscoveryClient.discover(port, serverId = "srv-target", encKey = encKey, timeoutMs = 1_500)
                    null
                } catch (e: Throwable) {
                    e
                }
            }
            Thread.sleep(200)
            sendBeacon(port, UdpPairBeacon(serverId = "srv-target", ip = "1.2.3.4", port = 1), key = "totally-different-key")

            val thrown = future.get()
            assertEquals(PairingError.Network::class.java, thrown?.javaClass)
        } finally {
            executor.shutdownNow()
        }
    }
}
