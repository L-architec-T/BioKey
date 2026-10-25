package org.pcbu.libreclient.unlock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pcbu.libreclient.crypto.HexCodec

/** Vectors computed independently with Python's hmac/hashlib, matching pcbu-lockd's BlePresenceWatcher. */
class BlePresenceBeaconTest {

    @Test
    fun `presence beacon matches reference`() {
        val payload = BlePresenceBeacon.payload("E730B2AA", "test-key", slot = 176139000, timeoutSeconds = 60, disarm = false)
        assertEquals("01001644463A0615F273BD3C19636A", HexCodec.encode(payload))
    }

    @Test
    fun `disarm beacon matches reference`() {
        val payload = BlePresenceBeacon.payload("E730B2AA", "test-key", slot = 176139000, timeoutSeconds = 60, disarm = true)
        assertEquals("01011644463A06A7441F533884097B", HexCodec.encode(payload))
    }

    @Test
    fun `slot is ten-second unix time`() {
        assertEquals(176139000L, BlePresenceBeacon.currentSlot(1_761_390_005_000L))
    }
}
