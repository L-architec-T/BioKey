package org.pcbu.libreclient.unlock

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.protocol.model.UdpBroadcast

class UnlockUdpListenerTest {

    private fun send(port: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("127.0.0.1"), port)) }
    }

    @Test
    fun `receives and parses a plaintext broadcast, matching UDPUnlockBroadcaster's format`() {
        UnlockUdpListener(0).use { listener ->
            val port = listenerPort(listener)
            val broadcast = UdpBroadcast(deviceId = "dev-1", pcbuIP = "192.168.1.50", pcbuPort = 43296, isManual = false)
            send(port, PcbuJson.encodeToString(UdpBroadcast.serializer(), broadcast))

            val received = listener.receiveOnce(timeoutMs = 3_000)
            assertEquals("dev-1", received?.deviceId)
            assertEquals("192.168.1.50", received?.pcbuIP)
            assertEquals(43296, received?.pcbuPort)
            assertEquals(false, received?.isManual)
        }
    }

    @Test
    fun `ignores malformed datagrams instead of throwing`() {
        UnlockUdpListener(0).use { listener ->
            val port = listenerPort(listener)
            send(port, "not json")
            val received = listener.receiveOnce(timeoutMs = 1_000)
            assertNull(received)
        }
    }

    @Test
    fun `times out and returns null when nothing arrives`() {
        UnlockUdpListener(0).use { listener ->
            assertNull(listener.receiveOnce(timeoutMs = 500))
        }
    }

    @Test
    fun `close() unblocking a concurrent receiveOnce() returns null instead of throwing`() {
        val listener = UnlockUdpListener(0)
        val result = java.util.concurrent.CompletableFuture<UdpBroadcast?>()
        val thread = Thread {
            result.complete(listener.receiveOnce(timeoutMs = 0))
        }
        thread.start()
        Thread.sleep(200) // let the thread actually enter the blocking receive() call
        listener.close()
        assertNull(result.get(3, java.util.concurrent.TimeUnit.SECONDS))
        thread.join(3_000)
    }

    /** Reflects the ephemeral port actually bound when constructed with port 0. */
    private fun listenerPort(listener: UnlockUdpListener): Int {
        val field = UnlockUdpListener::class.java.getDeclaredField("socket")
        field.isAccessible = true
        return (field.get(listener) as java.net.DatagramSocket).localPort
    }
}
