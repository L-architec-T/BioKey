package org.pcbu.libreclient.pairing

/**
 * Orchestrates a full pairing attempt from a scanned QR payload down to a
 * persistable [PairedDeviceRecord]. Pure network/crypto logic — actual
 * secure storage (Keystore/Room) is the Android app's responsibility.
 */
object PairingClient {

    fun pairFromQr(rawQr: String, thisDevice: ThisDeviceInfo, discoveryTimeoutMs: Int = 20_000): PairedDeviceRecord {
        val target = PairingQrParser.parse(rawQr)
        return when (target) {
            is PairingTarget.Legacy ->
                PairingTcpClient.pair(target.ip, target.port, target.encKey, thisDevice)

            is PairingTarget.Modern -> {
                val server = PairingDiscoveryClient.discover(
                    discoveryPort = target.discoveryPort,
                    serverId = target.serverId,
                    encKey = target.encKey,
                    timeoutMs = discoveryTimeoutMs,
                )
                PairingTcpClient.pair(server.ip, server.port, target.encKey, thisDevice)
            }
        }
    }
}
