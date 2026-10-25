package org.pcbu.libreclient.pairing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pcbu.libreclient.protocol.ProtocolVersions
import org.pcbu.libreclient.protocol.model.PairResponse
import org.pcbu.libreclient.protocol.model.PairResponseData
import org.pcbu.libreclient.protocol.model.PairingMethods

class PairingTcpClientTest {

    private val encKey = "test-pairing-enc-key"
    private val thisDevice = ThisDeviceInfo(
        deviceUUID = "phone-uuid-1234",
        deviceName = "Test Phone",
        ipAddress = "192.168.1.99",
        udpPort = 43300,
    )

    @Test
    fun `successful pairing decrypts the response and captures the plaintext deviceId etc`() {
        FakePcPairingServer(encKey).use { server ->
            server.acceptOnceAndRespond(
                PairResponse(
                    errMsg = "",
                    data = PairResponseData(
                        pairingMethod = PairingMethods.UDP,
                        deviceId = "sha256-device-id",
                        deviceName = "debian",
                        deviceOS = "Linux",
                        unlockServerPort = 43296,
                        macAddresses = listOf("aa:bb:cc:dd:ee:ff"),
                        userName = "larchitect",
                        passwordKey = "p".repeat(64),
                    ),
                ),
            )

            val record = PairingTcpClient.pair("127.0.0.1", server.port, encKey, thisDevice)
            server.awaitCompletion()

            assertEquals("sha256-device-id", record.deviceId)
            assertEquals("debian", record.deviceName)
            assertEquals("Linux", record.deviceOS)
            assertEquals("larchitect", record.userName)
            assertEquals(43296, record.unlockServerPort)
            assertEquals(listOf("aa:bb:cc:dd:ee:ff"), record.macAddresses)
            assertEquals(encKey, record.encKey)
            assertEquals("p".repeat(64), record.passwordKey)

            val sentInit = requireNotNull(server.receivedInit)
            assertEquals(ProtocolVersions.PAIRING, sentInit.protoVersion)
            assertEquals(thisDevice.deviceUUID, sentInit.deviceUUID)
            assertEquals(thisDevice.udpPort, sentInit.udpPort)
        }
    }

    @Test
    fun `PC rejection surfaces as PairingError-Rejected with the PC's message`() {
        FakePcPairingServer(encKey).use { server ->
            server.acceptOnceAndRespond(PairResponse(errMsg = "Protocol version mismatch", data = null))

            val ex = assertThrows(PairingError.Rejected::class.java) {
                PairingTcpClient.pair("127.0.0.1", server.port, encKey, thisDevice)
            }
            assertEquals("Protocol version mismatch", ex.errMsg)
        }
    }

    @Test
    fun `a corrupted (tampered) response surfaces as PairingError-Crypto, not silently accepted`() {
        FakePcPairingServer(encKey).use { server ->
            server.acceptOnceAndRespond(
                PairResponse(
                    data = PairResponseData(
                        pairingMethod = PairingMethods.UDP, deviceId = "id", deviceName = "n", deviceOS = "Linux",
                        unlockServerPort = 1, macAddresses = emptyList(), userName = "u", passwordKey = "k",
                    ),
                ),
                corruptResponseBytes = true,
            )
            assertThrows(PairingError.Crypto::class.java) {
                PairingTcpClient.pair("127.0.0.1", server.port, encKey, thisDevice)
            }
        }
    }
}
