package org.pcbu.libreclient.pairing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PairingQrParserTest {

    @Test
    fun `parses the modern QR format (serverId + discovery port)`() {
        val target = PairingQrParser.parse("""{"serverId":"srv-1","port":43297,"method":"UDP","encKey":"key1"}""")
        val modern = target as PairingTarget.Modern
        assertEquals("srv-1", modern.serverId)
        assertEquals(43297, modern.discoveryPort)
        assertEquals("UDP", modern.method)
        assertEquals("key1", modern.encKey)
    }

    @Test
    fun `parses the legacy QR format (direct ip + port)`() {
        val target = PairingQrParser.parse("""{"ip":"192.168.1.50","port":43295,"method":"UDP","encKey":"key1"}""")
        val legacy = target as PairingTarget.Legacy
        assertEquals("192.168.1.50", legacy.ip)
        assertEquals(43295, legacy.port)
    }

    @Test
    fun `rejects garbage input`() {
        assertThrows(MalformedPairingQrException::class.java) { PairingQrParser.parse("not json at all") }
        assertThrows(MalformedPairingQrException::class.java) { PairingQrParser.parse("""{"foo":"bar"}""") }
    }
}
