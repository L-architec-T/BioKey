package org.pcbu.libreclient.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WakeOnLanTest {

    @Test
    fun `magic packet is 6 padding bytes plus 16 repeats of the MAC`() {
        val packet = WakeOnLan.buildMagicPacket("00:11:22:33:44:55")

        assertEquals(102, packet.size)
        assertEquals("ffffffffffff", packet.copyOfRange(0, 6).toHexString())
        for (i in 0 until 16) {
            val repeat = packet.copyOfRange(6 + i * 6, 6 + i * 6 + 6)
            assertEquals("001122334455", repeat.toHexString())
        }
    }

    @Test
    fun `accepts hyphen-separated MACs`() {
        val packet = WakeOnLan.buildMagicPacket("aa-bb-cc-dd-ee-ff")
        assertEquals("aabbccddeeff", packet.copyOfRange(6, 12).toHexString())
    }

    @Test
    fun `rejects malformed MAC`() {
        assertFailsWith<IllegalArgumentException> { WakeOnLan.buildMagicPacket("not-a-mac") }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
