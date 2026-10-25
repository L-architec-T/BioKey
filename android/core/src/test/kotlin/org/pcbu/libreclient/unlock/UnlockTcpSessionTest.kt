package org.pcbu.libreclient.unlock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pcbu.libreclient.protocol.model.UnlockErrors
import org.pcbu.libreclient.protocol.model.UnlockRequestData

class UnlockTcpSessionTest {

    private val encKey = "unlock-test-enc-key"
    private val deviceId = "device-id-123"

    @Test
    fun `reads the decrypted challenge and the PC receives the deviceId first, unencrypted`() {
        FakePcUnlockServer(encKey).use { server ->
            server.acceptAndChallenge(UnlockRequestData(user = "larchitect", program = "gdm-password", unlockToken = "t".repeat(64)))

            UnlockTcpSession.connect("127.0.0.1", server.port, deviceId, encKey).use { session ->
                val challenge = session.readChallenge()
                assertEquals("larchitect", challenge.user)
                assertEquals("gdm-password", challenge.program)
                assertEquals("t".repeat(64), challenge.unlockToken)

                session.respondSuccess(challenge.unlockToken, "recovered-password-key")
            }
            server.awaitCompletion()

            assertEquals(deviceId, server.receivedDeviceId)
            val response = requireNotNull(server.receivedResponse)
            assertEquals("", response.error)
        }
    }

    @Test
    fun `success response decrypts back to the echoed token and passwordKey`() {
        FakePcUnlockServer(encKey).use { server ->
            server.acceptAndChallenge(UnlockRequestData(user = "u", program = "sudo", unlockToken = "abc123"))

            UnlockTcpSession.connect("127.0.0.1", server.port, deviceId, encKey).use { session ->
                val challenge = session.readChallenge()
                session.respondSuccess(challenge.unlockToken, "pk-value")
            }
            server.awaitCompletion()

            val response = requireNotNull(server.receivedResponse)
            val decrypted = org.pcbu.libreclient.crypto.AesGcmPacketCodec.decryptPacket(
                org.pcbu.libreclient.crypto.HexCodec.decode(response.encData),
                encKey,
            )
            val data = org.pcbu.libreclient.protocol.model.PcbuJson.decodeFromString(
                org.pcbu.libreclient.protocol.model.UnlockResponseData.serializer(),
                String(decrypted, Charsets.UTF_8),
            )
            assertEquals("abc123", data.unlockToken)
            assertEquals("pk-value", data.passwordKey)
        }
    }

    @Test
    fun `respondError sends the error code with no encData, for a cancelled biometric prompt`() {
        FakePcUnlockServer(encKey).use { server ->
            server.acceptAndChallenge(UnlockRequestData(user = "u", program = "polkit", unlockToken = "tok"))

            UnlockTcpSession.connect("127.0.0.1", server.port, deviceId, encKey).use { session ->
                session.readChallenge()
                session.respondError(UnlockErrors.CANCEL)
            }
            server.awaitCompletion()

            val response = requireNotNull(server.receivedResponse)
            assertEquals(UnlockErrors.CANCEL, response.error)
            assertEquals("", response.encData)
        }
    }
}
