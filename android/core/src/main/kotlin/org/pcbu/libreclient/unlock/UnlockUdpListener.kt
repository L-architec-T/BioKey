package org.pcbu.libreclient.unlock

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.protocol.model.UdpBroadcast

/**
 * Binds a UDP socket for the "automatic" unlock trigger. Unlike the pairing
 * beacon, this datagram is verified plaintext JSON (UDPUnlockBroadcaster
 * sends the raw PacketUDPBroadcast.ToJson().dump() bytes with no
 * encryption) — the wake-up itself carries no secret, only "someone paired
 * with deviceId X wants to talk, at this address".
 *
 * One instance binds once and is read from repeatedly; the caller decides
 * per-datagram whether deviceId is a known paired device before acting on
 * it (unknown ids are the caller's concern to ignore, not this class's).
 */
class UnlockUdpListener(port: Int) : AutoCloseable {
    private val socket = DatagramSocket(port)
    private val buffer = ByteArray(4096)

    /** True once this instance is done for good — either [close] was called on it, or (see
     * [receiveOnce]) the OS invalidated the underlying fd out from under it (observed after a
     * Wi-Fi network transition, surfacing as EBADF). Either way this instance will only ever
     * keep throwing; the caller must construct a fresh one to keep listening. Real devices
     * confirmed the latter case (2026-08-22): without it, the listen loop silently spun on the
     * dead socket forever, deaf to every broadcast until the whole service was restarted. */
    val isDead: Boolean get() = socket.isClosed

    /** Blocks until a well-formed broadcast arrives, [timeoutMs] elapses (returns null), or the socket is closed. */
    fun receiveOnce(timeoutMs: Int = 0): UdpBroadcast? {
        socket.soTimeout = timeoutMs
        val packet = DatagramPacket(buffer, buffer.size)
        try {
            socket.receive(packet)
        } catch (e: SocketTimeoutException) {
            return null
        } catch (e: SocketException) {
            // close() unblocking a concurrently-running receive() (the normal way to interrupt
            // this call from onDestroy() while listenLoop() is parked in it) surfaces here as
            // "recvfrom failed: EBADF", not as a timeout — same "nothing to report" outcome as
            // one, not an error: the caller's scope.isActive check is what decides whether to
            // keep listening. Left uncaught, this used to crash the whole app process instead
            // of just ending the loop.
            //
            // The OS can also invalidate the fd itself (same EBADF) without anyone calling
            // close() — observed on a real device right after a Wi-Fi network transition. Mark
            // the socket closed ourselves so isDead picks it up either way; otherwise this
            // instance keeps rejecting every future receive() and the caller has no signal that
            // it's the socket, not just an empty network, that's broken.
            if (!socket.isClosed) socket.close()
            return null
        }

        val json = String(packet.data, 0, packet.length, Charsets.UTF_8)
        return try {
            PcbuJson.decodeFromString(UdpBroadcast.serializer(), json)
        } catch (e: Exception) {
            null // malformed/foreign datagram on the same port; not fatal, keep listening
        }
    }

    override fun close() {
        socket.close()
    }
}
