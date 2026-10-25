package org.pcbu.libreclient.net

import org.junit.jupiter.api.Test

class LocalNetworkInfoTest {
    @Test
    fun `does not throw and returns either null or a plausible dotted-quad address`() {
        val address = LocalNetworkInfo.findLocalIPv4Address()
        if (address != null) {
            assert(address.split(".").size == 4) { "Unexpected address format: $address" }
        }
    }
}
