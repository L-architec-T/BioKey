package org.pcbu.libreclient.protocol.model

import kotlinx.serialization.Serializable

/**
 * JSON payloads wire-compatible with pcbu-desktop's Packets.h / PairingStructs.h.
 * Field names/casing are load-bearing (must match exactly) — verified against
 * the upstream C++ struct definitions, not copied from them.
 */

@Serializable
data class PairInit(
    val protoVersion: String,
    val deviceUUID: String,
    val deviceName: String,
    val ipAddress: String,
    val tcpPort: Int,
    val udpPort: Int,
    val udpManualPort: Int,
    val cloudToken: String,
)

@Serializable
data class PairResponseData(
    val pairingMethod: String,
    val deviceId: String,
    val deviceName: String,
    val deviceOS: String,
    val unlockServerPort: Int,
    val macAddresses: List<String> = emptyList(),
    val userName: String,
    val passwordKey: String,
)

@Serializable
data class PairResponse(
    val errMsg: String = "",
    val data: PairResponseData? = null,
)

@Serializable
data class UnlockRequest(
    val protoVersion: String,
    val deviceId: String,
    val encData: String,
)

@Serializable
data class UnlockRequestData(
    val user: String,
    val program: String,
    val unlockToken: String,
)

@Serializable
data class UnlockResponse(
    val error: String = "",
    val encData: String = "",
)

@Serializable
data class UnlockResponseData(
    val unlockToken: String,
    val passwordKey: String,
)

@Serializable
data class UdpBroadcast(
    val deviceId: String,
    val pcbuIP: String,
    val pcbuPort: Int,
    val isManual: Boolean,
    // Identifies one logical wake attempt on the PC (one UnlockHandler::GetResult() call);
    // identical across every UDP retransmission and the mirrored FCM push for that same
    // attempt. Lets UnlockForegroundService dedup the two channels by exact identity
    // instead of a time window. Defaulted for wire-compatibility with any packet that
    // predates this field.
    val wakeId: String = "",
)

/** Wire-compatible with pcbu-desktop's PacketLockRequest (Packets.h) / pcbu-lockd.
 * Sent as a single UDP datagram to the PC's LAN broadcast address on
 * [org.pcbu.libreclient.unlock.LockBroadcastSender.LOCK_LISTEN_PORT] — no reply
 * expected, so unlike [UdpBroadcast] this needs no pcbuIP/pcbuPort of its own.
 * `cmd` selects which of pcbu-lockd's remote commands this is ("lock",
 * "selectUser", "suspend", or "setMacro" — see [MacroDto]); it defaults to
 * "lock" to stay wire-compatible with pcbu-lockd builds from before
 * "selectUser" existed. For "setMacro", `encData` decrypts to the macro's
 * JSON (see [MacroDto]) instead of the small fixed marker word the other
 * commands use — pcbu-lockd parses it after decryption rather than treating
 * it as auth-only content. */
@Serializable
data class LockRequest(
    val deviceId: String,
    val encData: String,
    val cmd: String = "lock",
)

@Serializable
data class UdpPairBeacon(
    val serverId: String,
    val ip: String,
    val port: Int,
)

@Serializable
data class PairingQrData(
    val serverId: String,
    val port: Int,
    val method: String,
    val encKey: String,
)

@Serializable
data class LegacyPairingQrData(
    val ip: String,
    val port: Int,
    val method: String,
    val encKey: String,
)

/** Errors PACKET_ID_UNLOCK_RESPONSE.error may carry, per BaseUnlockConnection::OnResponseReceived. */
object UnlockErrors {
    const val CANCEL = "CANCEL"
    const val NOT_PAIRED = "NOT_PAIRED"
    const val APP_ERROR = "APP_ERROR"
    const val TIME_ERROR = "TIME_ERROR"
    const val DATA_ERROR = "DATA_ERROR"
    const val PROTOCOL_ERROR = "PROTOCOL_ERROR"
}

/** PairingMethod enum, PairingMethodUtils::ToString/FromString. */
object PairingMethods {
    const val TCP = "TCP"
    const val BLUETOOTH = "BLUETOOTH"
    const val CLOUD_TCP = "CLOUD_TCP"
    const val CLOUD_BT = "CLOUD_BT"
    const val UDP = "UDP"
    const val MANUAL_UDP = "MANUAL_UDP"
}
