package org.pcbu.libreclient.protocol.model

import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards against silent field drift: if a Kotlin property gets renamed, this
 * fails loudly instead of quietly breaking interop with pcbu-desktop, whose
 * exact JSON keys are hard-coded here from Packets.h / PairingStructs.h.
 */
class ModelsTest {

    private fun keysOf(json: String) = PcbuJson.parseToJsonElement(json).jsonObject.keys

    @Test
    fun `PairInit serializes exactly the keys the PC expects`() {
        val payload = PairInit(
            protoVersion = ProtocolVersionsStub.PAIRING,
            deviceUUID = "u", deviceName = "n", ipAddress = "1.2.3.4",
            tcpPort = 0, udpPort = 43300, udpManualPort = 0, cloudToken = "",
        )
        val json = PcbuJson.encodeToString(PairInit.serializer(), payload)
        assertEquals(
            setOf("protoVersion", "deviceUUID", "deviceName", "ipAddress", "tcpPort", "udpPort", "udpManualPort", "cloudToken"),
            keysOf(json),
        )
    }

    @Test
    fun `PairResponse round-trips including nested data and empty errMsg`() {
        val json = """{"errMsg":"","data":{"pairingMethod":"UDP","deviceId":"id1","deviceName":"debian",
            "deviceOS":"Linux","unlockServerPort":43296,"macAddresses":["aa:bb:cc:dd:ee:ff"],
            "userName":"larchitect","passwordKey":"k"}}""".trimIndent()
        val parsed = PcbuJson.decodeFromString(PairResponse.serializer(), json)
        assertEquals("UDP", parsed.data?.pairingMethod)
        assertEquals(43296, parsed.data?.unlockServerPort)
        assertEquals(listOf("aa:bb:cc:dd:ee:ff"), parsed.data?.macAddresses)
    }

    @Test
    fun `UnlockRequestData serializes exactly the keys the phone must encrypt`() {
        val payload = UnlockRequestData(user = "larchitect", program = "gdm-password", unlockToken = "t".repeat(64))
        val json = PcbuJson.encodeToString(UnlockRequestData.serializer(), payload)
        assertEquals(setOf("user", "program", "unlockToken"), keysOf(json))
    }

    @Test
    fun `UnlockResponseData serializes exactly unlockToken and passwordKey`() {
        val payload = UnlockResponseData(unlockToken = "t".repeat(64), passwordKey = "p".repeat(64))
        val json = PcbuJson.encodeToString(UnlockResponseData.serializer(), payload)
        assertEquals(setOf("unlockToken", "passwordKey"), keysOf(json))
    }

    @Test
    fun `UdpBroadcast is parsed from an unencrypted plain JSON datagram`() {
        val json = """{"deviceId":"id1","pcbuIP":"192.168.1.50","pcbuPort":43296,"isManual":false}"""
        val parsed = PcbuJson.decodeFromString(UdpBroadcast.serializer(), json)
        assertEquals("id1", parsed.deviceId)
        assertEquals(43296, parsed.pcbuPort)
        assertEquals(false, parsed.isManual)
    }

    @Test
    fun `modern pairing QR discriminates from legacy by the serverId vs ip field`() {
        val modern = """{"serverId":"srv1","port":43297,"method":"UDP","encKey":"k"}"""
        val legacy = """{"ip":"192.168.1.10","port":43295,"method":"UDP","encKey":"k"}"""
        assertEquals(true, "serverId" in keysOf(modern))
        assertEquals(true, "ip" in keysOf(legacy))
        assertEquals(false, "ip" in keysOf(modern))
        assertEquals(false, "serverId" in keysOf(legacy))
    }

    /** Avoids importing ProtocolVersions just for a literal in the test above. */
    private object ProtocolVersionsStub {
        const val PAIRING = "4.0.0"
    }
}
