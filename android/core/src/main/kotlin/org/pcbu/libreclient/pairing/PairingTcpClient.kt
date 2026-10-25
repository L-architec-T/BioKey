package org.pcbu.libreclient.pairing

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import org.pcbu.libreclient.crypto.AesGcmPacketCodec
import org.pcbu.libreclient.crypto.CryptoException
import org.pcbu.libreclient.protocol.Packet
import org.pcbu.libreclient.protocol.PacketFraming
import org.pcbu.libreclient.protocol.PacketIds
import org.pcbu.libreclient.protocol.PacketReadError
import org.pcbu.libreclient.protocol.ProtocolVersions
import org.pcbu.libreclient.protocol.model.PairInit
import org.pcbu.libreclient.protocol.model.PairResponse
import org.pcbu.libreclient.protocol.model.PairResponseData
import org.pcbu.libreclient.protocol.model.PcbuJson

/** Everything that must be persisted (securely) after a successful pairing. */
data class PairedDeviceRecord(
    val deviceId: String,
    val deviceName: String,
    val deviceOS: String,
    val userName: String,
    val unlockServerPort: Int,
    val macAddresses: List<String>,
    val pairingMethod: String,
    /** Shared secret for all future protocol messages with this PC. */
    val encKey: String,
    /** Lets the PC decrypt its locally-stored OS password; must never be sent back to it. */
    val passwordKey: String,
)

sealed class PairingError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Rejected(val errMsg: String) : PairingError("PC rejected pairing: $errMsg")
    class Network(cause: Throwable) : PairingError("Network error during pairing: ${cause.message}", cause)
    class Crypto(cause: Throwable) : PairingError("Crypto error during pairing: ${cause.message}", cause)
    class Malformed(detail: String) : PairingError("Malformed pairing response: $detail")
}

/** What this device presents to the PC during pairing. */
data class ThisDeviceInfo(
    val deviceUUID: String,
    val deviceName: String,
    val ipAddress: String,
    val udpPort: Int,
    val tcpPort: Int = 0,
    val udpManualPort: Int = 0,
    /** FCM registration token, so the PC can push a wake instead of relying only on UDP. */
    val cloudToken: String = "",
)

object PairingTcpClient {

    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 15_000

    fun pair(ip: String, port: Int, encKey: String, thisDevice: ThisDeviceInfo): PairedDeviceRecord {
        val socket = try {
            Socket().apply {
                connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                soTimeout = READ_TIMEOUT_MS
            }
        } catch (e: Exception) {
            throw PairingError.Network(e)
        }
        return socket.use { pair(it.getInputStream(), it.getOutputStream(), encKey, thisDevice) }
    }

    /** Overload taking raw streams directly, for testing against an in-process fake server. */
    fun pair(input: InputStream, output: OutputStream, encKey: String, thisDevice: ThisDeviceInfo): PairedDeviceRecord {
        val initPacket = PairInit(
            protoVersion = ProtocolVersions.PAIRING,
            deviceUUID = thisDevice.deviceUUID,
            deviceName = thisDevice.deviceName,
            ipAddress = thisDevice.ipAddress,
            tcpPort = thisDevice.tcpPort,
            udpPort = thisDevice.udpPort,
            udpManualPort = thisDevice.udpManualPort,
            cloudToken = thisDevice.cloudToken,
        )
        val initJson = PcbuJson.encodeToString(PairInit.serializer(), initPacket)

        writeEncryptedPacket(output, PacketIds.PAIR_INIT, initJson, encKey)

        val responsePacket = try {
            PacketFraming.readPacket(input)
        } catch (e: PacketReadError) {
            throw PairingError.Network(e)
        }
        if (responsePacket.id != PacketIds.PAIR_RESPONSE) {
            throw PairingError.Malformed("Expected PAIR_RESPONSE (0x51), got 0x${responsePacket.id.toString(16)}")
        }

        val responseJson = readEncryptedPacket(responsePacket, encKey)
        val response = try {
            PcbuJson.decodeFromString(PairResponse.serializer(), responseJson)
        } catch (e: Exception) {
            throw PairingError.Malformed("Invalid PAIR_RESPONSE JSON: ${e.message}")
        }

        if (response.errMsg.isNotEmpty()) throw PairingError.Rejected(response.errMsg)
        val data: PairResponseData = response.data ?: throw PairingError.Malformed("PAIR_RESPONSE has neither errMsg nor data")

        return PairedDeviceRecord(
            deviceId = data.deviceId,
            deviceName = data.deviceName,
            deviceOS = data.deviceOS,
            userName = data.userName,
            unlockServerPort = data.unlockServerPort,
            macAddresses = data.macAddresses,
            pairingMethod = data.pairingMethod,
            encKey = encKey,
            passwordKey = data.passwordKey,
        )
    }

    /**
     * PAIR_INIT/PAIR_RESPONSE differ from the unlock packets: the ENTIRE
     * framed payload is the raw AES-GCM blob (no hex, no outer JSON
     * envelope) — verified against PairingServer::WriteEncryptedPacket,
     * which hands EncryptAESPacket's raw output straight to WritePacket.
     */
    private fun writeEncryptedPacket(output: OutputStream, packetId: Int, json: String, encKey: String) {
        val encrypted = try {
            AesGcmPacketCodec.encryptPacket(json.toByteArray(Charsets.UTF_8), encKey)
        } catch (e: CryptoException) {
            throw PairingError.Crypto(e)
        }
        PacketFraming.writePacket(output, packetId, encrypted)
    }

    private fun readEncryptedPacket(packet: Packet, encKey: String): String {
        val decrypted = try {
            AesGcmPacketCodec.decryptPacket(packet.data, encKey)
        } catch (e: CryptoException) {
            throw PairingError.Crypto(e)
        }
        return String(decrypted, Charsets.UTF_8)
    }
}
