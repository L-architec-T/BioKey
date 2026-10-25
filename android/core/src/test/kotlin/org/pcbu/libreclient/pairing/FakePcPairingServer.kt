package org.pcbu.libreclient.pairing

import java.net.ServerSocket
import org.pcbu.libreclient.crypto.AesGcmPacketCodec
import org.pcbu.libreclient.protocol.PacketFraming
import org.pcbu.libreclient.protocol.PacketIds
import org.pcbu.libreclient.protocol.model.PairInit
import org.pcbu.libreclient.protocol.model.PairResponse
import org.pcbu.libreclient.protocol.model.PcbuJson

/**
 * A minimal, single-shot in-process stand-in for pcbu-desktop's
 * PairingServer, used to exercise PairingTcpClient's real socket I/O
 * without needing an actual PC. Captures the decrypted PairInit it
 * received so tests can assert on what the client sent.
 */
class FakePcPairingServer(private val encKey: String) : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    val port: Int get() = serverSocket.localPort

    @Volatile var receivedInit: PairInit? = null
        private set

    fun acceptOnceAndRespond(response: PairResponse, corruptResponseBytes: Boolean = false) {
        val thread = Thread {
            serverSocket.accept().use { client ->
                val packet = PacketFraming.readPacket(client.getInputStream())
                check(packet.id == PacketIds.PAIR_INIT) { "Expected PAIR_INIT, got 0x${packet.id.toString(16)}" }
                val json = String(AesGcmPacketCodec.decryptPacket(packet.data, encKey), Charsets.UTF_8)
                receivedInit = PcbuJson.decodeFromString(PairInit.serializer(), json)

                val responseJson = PcbuJson.encodeToString(PairResponse.serializer(), response)
                val encrypted = AesGcmPacketCodec.encryptPacket(responseJson.toByteArray(Charsets.UTF_8), encKey)
                if (corruptResponseBytes) {
                    // Flip the last byte (part of the GCM tag) so the client's decrypt fails,
                    // exercising PairingTcpClient's own Crypto error path on a valid connection.
                    encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()
                }
                PacketFraming.writePacket(client.getOutputStream(), PacketIds.PAIR_RESPONSE, encrypted)
            }
        }
        thread.isDaemon = true
        thread.start()
        this.acceptThread = thread
    }

    private var acceptThread: Thread? = null

    fun awaitCompletion(timeoutMs: Long = 5_000) {
        acceptThread?.join(timeoutMs)
    }

    override fun close() {
        serverSocket.close()
    }
}
