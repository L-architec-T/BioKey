package org.pcbu.libreclient.unlock

import java.net.ServerSocket
import org.pcbu.libreclient.crypto.AesGcmPacketCodec
import org.pcbu.libreclient.crypto.HexCodec
import org.pcbu.libreclient.protocol.PacketFraming
import org.pcbu.libreclient.protocol.PacketIds
import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.protocol.model.UnlockRequest
import org.pcbu.libreclient.protocol.model.UnlockRequestData
import org.pcbu.libreclient.protocol.model.UnlockResponse

/** In-process stand-in for pcbu-desktop's TCPUnlockServer, for exercising UnlockTcpSession. */
class FakePcUnlockServer(private val encKey: String) : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    val port: Int get() = serverSocket.localPort

    @Volatile var receivedDeviceId: String? = null
        private set

    @Volatile var receivedResponse: UnlockResponse? = null
        private set

    private var thread: Thread? = null

    /** Sends [requestData] after receiving DEVICE_ID, then captures whatever UNLOCK_RESPONSE comes back. */
    fun acceptAndChallenge(requestData: UnlockRequestData, protoVersion: String = "3.0.0") {
        thread = Thread {
            serverSocket.accept().use { client ->
                val deviceIdPacket = PacketFraming.readPacket(client.getInputStream())
                check(deviceIdPacket.id == PacketIds.DEVICE_ID) { "Expected DEVICE_ID, got 0x${deviceIdPacket.id.toString(16)}" }
                receivedDeviceId = String(deviceIdPacket.data, Charsets.UTF_8)

                val dataJson = PcbuJson.encodeToString(UnlockRequestData.serializer(), requestData)
                val encrypted = AesGcmPacketCodec.encryptPacket(dataJson.toByteArray(Charsets.UTF_8), encKey)
                val request = UnlockRequest(protoVersion = protoVersion, deviceId = receivedDeviceId!!, encData = HexCodec.encode(encrypted))
                PacketFraming.writePacket(client.getOutputStream(), PacketIds.UNLOCK_REQUEST, PcbuJson.encodeToString(UnlockRequest.serializer(), request))

                val responsePacket = PacketFraming.readPacket(client.getInputStream())
                check(responsePacket.id == PacketIds.UNLOCK_RESPONSE) { "Expected UNLOCK_RESPONSE, got 0x${responsePacket.id.toString(16)}" }
                receivedResponse = PcbuJson.decodeFromString(UnlockResponse.serializer(), String(responsePacket.data, Charsets.UTF_8))
            }
        }.apply { isDaemon = true; start() }
    }

    fun awaitCompletion(timeoutMs: Long = 5_000) {
        thread?.join(timeoutMs)
    }

    override fun close() {
        serverSocket.close()
    }
}
