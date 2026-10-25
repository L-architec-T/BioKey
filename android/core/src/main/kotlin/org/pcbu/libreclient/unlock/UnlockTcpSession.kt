package org.pcbu.libreclient.unlock

import java.net.InetSocketAddress
import java.net.Socket
import org.pcbu.libreclient.crypto.AesGcmPacketCodec
import org.pcbu.libreclient.crypto.CryptoException
import org.pcbu.libreclient.crypto.HexCodec
import org.pcbu.libreclient.protocol.PacketFraming
import org.pcbu.libreclient.protocol.PacketIds
import org.pcbu.libreclient.protocol.PacketReadError
import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.protocol.model.UnlockRequest
import org.pcbu.libreclient.protocol.model.UnlockRequestData
import org.pcbu.libreclient.protocol.model.UnlockResponse
import org.pcbu.libreclient.protocol.model.UnlockResponseData

data class UnlockChallenge(
    val user: String,
    val program: String,
    val unlockToken: String,
    val pcProtoVersion: String,
)

sealed class UnlockSessionError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : UnlockSessionError("Network error during unlock: ${cause.message}", cause)
    class Crypto(cause: Throwable) : UnlockSessionError("Crypto error during unlock: ${cause.message}", cause)
    class Malformed(detail: String) : UnlockSessionError("Malformed unlock message: $detail")
}

/**
 * One PC-initiated unlock attempt. Per BaseUnlockConnection::PerformAuthFlow
 * (needsDeviceID=true for the UDP-triggered TCPUnlockServer path), the very
 * first thing sent on the connection MUST be the raw, unencrypted deviceId
 * (packet 0xB0) — the PC doesn't know which paired phone connected until
 * then, even though the UDP broadcast that triggered this already named it.
 *
 * The socket is kept open across the async biometric prompt: callers must
 * read the challenge, show the prompt, then call exactly one of
 * respondSuccess/respondError before closing.
 */
class UnlockTcpSession private constructor(
    private val socket: Socket,
    private val encKey: String,
) : AutoCloseable {

    fun readChallenge(): UnlockChallenge {
        val packet = try {
            PacketFraming.readPacket(socket.getInputStream())
        } catch (e: PacketReadError) {
            throw UnlockSessionError.Network(e)
        }
        if (packet.id != PacketIds.UNLOCK_REQUEST) {
            throw UnlockSessionError.Malformed("Expected UNLOCK_REQUEST (0xB1), got 0x${packet.id.toString(16)}")
        }

        val envelope = try {
            PcbuJson.decodeFromString(UnlockRequest.serializer(), String(packet.data, Charsets.UTF_8))
        } catch (e: Exception) {
            throw UnlockSessionError.Malformed("Invalid UNLOCK_REQUEST JSON: ${e.message}")
        }

        val inner = try {
            AesGcmPacketCodec.decryptPacket(HexCodec.decode(envelope.encData), encKey)
        } catch (e: CryptoException) {
            throw UnlockSessionError.Crypto(e)
        }

        val data = try {
            PcbuJson.decodeFromString(UnlockRequestData.serializer(), String(inner, Charsets.UTF_8))
        } catch (e: Exception) {
            throw UnlockSessionError.Malformed("Invalid decrypted unlock request data: ${e.message}")
        }

        return UnlockChallenge(
            user = data.user,
            program = data.program,
            unlockToken = data.unlockToken,
            pcProtoVersion = envelope.protoVersion,
        )
    }

    fun respondSuccess(unlockToken: String, passwordKey: String) {
        val data = UnlockResponseData(unlockToken = unlockToken, passwordKey = passwordKey)
        val dataJson = PcbuJson.encodeToString(UnlockResponseData.serializer(), data)
        val encrypted = try {
            AesGcmPacketCodec.encryptPacket(dataJson.toByteArray(Charsets.UTF_8), encKey)
        } catch (e: CryptoException) {
            throw UnlockSessionError.Crypto(e)
        }
        writeResponse(UnlockResponse(error = "", encData = HexCodec.encode(encrypted)))
    }

    fun respondError(errorCode: String) {
        writeResponse(UnlockResponse(error = errorCode, encData = ""))
    }

    private fun writeResponse(response: UnlockResponse) {
        val json = PcbuJson.encodeToString(UnlockResponse.serializer(), response)
        try {
            PacketFraming.writePacket(socket.getOutputStream(), PacketIds.UNLOCK_RESPONSE, json)
        } catch (e: Exception) {
            throw UnlockSessionError.Network(e)
        }
    }

    /** TEMP diagnostic (2026-08-22): unread bytes still sitting in the receive buffer at
     * close() time make Linux send an RST instead of a clean FIN — which can drop not-yet-
     * acked outgoing data (e.g. a response [close] wrote moments earlier) along with it. */
    fun pendingBytesAvailable(): Int = socket.getInputStream().available()

    override fun close() {
        socket.close()
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        /** Comfortably longer than the 120s crypto anti-replay window, to outlast a slow biometric prompt. */
        const val READ_TIMEOUT_MS = 130_000

        fun connect(ip: String, port: Int, deviceId: String, encKey: String): UnlockTcpSession {
            val socket = try {
                Socket().apply {
                    connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                    soTimeout = READ_TIMEOUT_MS
                    // pcbu-desktop's own TCPUnlockServer sets this on its side (TCP_NODELAY);
                    // without it here too, Nagle's algorithm can hold back this session's final
                    // small write (the encrypted UNLOCK_RESPONSE) waiting to coalesce with more
                    // data that never comes, right as close() tears the connection down —
                    // exactly the shape of "respondSuccess() returns with no exception, but the
                    // PC never sees a single byte of it" seen on real devices (2026-08-22).
                    tcpNoDelay = true
                }
            } catch (e: Exception) {
                throw UnlockSessionError.Network(e)
            }
            return try {
                PacketFraming.writePacket(socket.getOutputStream(), PacketIds.DEVICE_ID, deviceId)
                UnlockTcpSession(socket, encKey)
            } catch (e: Exception) {
                socket.close()
                throw UnlockSessionError.Network(e)
            }
        }
    }
}
