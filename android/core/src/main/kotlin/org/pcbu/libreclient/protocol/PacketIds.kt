package org.pcbu.libreclient.protocol

/**
 * Wire-compatible with pcbu-desktop's `common/src/connection/Packets.h`.
 * Values and framing verified against the upstream C++ source; this file
 * contains no copied code, only re-derived constants needed for interop.
 */
object PacketIds {
    const val PAIR_INIT: Int = 0x50
    const val PAIR_RESPONSE: Int = 0x51

    const val DEVICE_ID: Int = 0xB0
    const val UNLOCK_REQUEST: Int = 0xB1
    const val UNLOCK_RESPONSE: Int = 0xB2

    /** Phone-initiated, sent as a single UDP datagram to pcbu-lockd's dedicated
     * listen port — not part of TCP framing, so this ID is only a documentation/
     * discriminator convention shared with Packets.h, not read off the wire. */
    const val LOCK_REQUEST: Int = 0xB3
}

object ProtocolVersions {
    /** Must match AppInfo::GetPairingProtocolVersion() exactly; the PC rejects on any mismatch. */
    const val PAIRING: String = "4.0.0"

    /** AppInfo::GetUnlockProtocolVersion(); not strictly validated by the PC, informational. */
    const val UNLOCK: String = "3.0.0"
}
