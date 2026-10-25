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
    fun `parses the pairing code typed from the PC screen (base32 of the QR JSON)`() {
        // StringUtils::ToBase32String + WithSeperators("-", 5) of the modern QR above.
        val code = "PMRHG-ZLSOZ-SXESL-EEI5C-E43SO-YWTCI-RMEJY-G64TU-EI5DI-MZSHE-3SYIT-NMV2G-Q33EE-I5CEV-KEKAR-CYITF-NZRUW-ZLZEI-5CE23-FPEYS-E7I"
        val modern = PairingQrParser.parse(code) as PairingTarget.Modern
        assertEquals("srv-1", modern.serverId)
        assertEquals(43297, modern.discoveryPort)
        assertEquals("key1", modern.encKey)
        // Typed by hand: lower case, spaces instead of dashes.
        val typed = code.lowercase().replace("-", " ")
        assertEquals(modern, PairingQrParser.parse(typed))
    }

    @Test
    fun `rejects garbage input`() {
        assertThrows(MalformedPairingQrException::class.java) { PairingQrParser.parse("not json at all") }
        assertThrows(MalformedPairingQrException::class.java) { PairingQrParser.parse("""{"foo":"bar"}""") }
    }
}
